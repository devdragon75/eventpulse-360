package com.eventpulse.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end through the real filter chain, controllers, services and (H2) database. */
@SpringBootTest
@AutoConfigureMockMvc
class EventPulseApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    void organizerCanRunAnEventFromDraftToSoldOut() throws Exception {
        String token = signup("Olivia");
        long eventId = createEvent(token, 10);
        long ticketId = addTicketType(token, eventId, "General", "100.00", 1);

        // Drafts are hidden from the public.
        mvc.perform(get("/api/events/{id}", eventId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/events/{id}", eventId).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));

        mvc.perform(post("/api/events/{id}/publish", eventId).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(get("/api/events/{id}", eventId)).andExpect(status().isOk());
        mvc.perform(get("/api/events/{id}/ticket-types", eventId))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].remaining").value(1));

        // Anyone can register; the last seat goes to the first buyer only.
        JsonNode booking = body(register(eventId, ticketId, "ada@" + UUID.randomUUID() + ".com").andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentStatus").value("PAID")));
        register(eventId, ticketId, "bob@" + UUID.randomUUID() + ".com")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("sold out")));

        // Ticket lookup is public; analytics is not.
        mvc.perform(get("/api/registrations/ticket/{code}", booking.get("ticketCode").asText()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/analytics/events/{id}", eventId)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/analytics/events/{id}", eventId).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticketsSold").value(1))
                .andExpect(jsonPath("$.netRevenue").value(100.0))
                .andExpect(jsonPath("$.occupancyPercent").value(10.0));

        // Cancelling refunds the money and frees the seat.
        mvc.perform(post("/api/registrations/{id}/cancel", booking.get("id").asLong()).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));
        mvc.perform(get("/api/analytics/events/{id}", eventId).header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.ticketsSold").value(0))
                .andExpect(jsonPath("$.netRevenue").value(0))
                .andExpect(jsonPath("$.refunded").value(100.0));
    }

    @Test
    void registrationIsRejectedForDraftEvents() throws Exception {
        String token = signup("Dana");
        long eventId = createEvent(token, 10);
        long ticketId = addTicketType(token, eventId, "General", "10.00", 5);

        register(eventId, ticketId, "x@" + UUID.randomUUID() + ".com").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ticketAllocationCannotExceedCapacity() throws Exception {
        String token = signup("Cap");
        long eventId = createEvent(token, 10);
        addTicketType(token, eventId, "General", "10.00", 8);

        mvc.perform(post("/api/events/{id}/ticket-types", eventId).header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "VIP", "price", "99.00", "quantity", 3))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(containsString("exceed the event capacity")));
    }

    @Test
    void organizersCannotTouchEachOthersEvents() throws Exception {
        String owner = signup("Owner");
        String intruder = signup("Intruder");
        long eventId = createEvent(owner, 10);

        mvc.perform(put("/api/events/{id}", eventId).header("Authorization", bearer(intruder))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(eventPayload(10))))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/events/{id}", eventId).header("Authorization", bearer(intruder)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/events/{id}", eventId).header("Authorization", bearer(intruder)))
                .andExpect(status().isNotFound()); // draft: invisible, not just forbidden
    }

    @Test
    void securityRulesAreEnforced() throws Exception {
        String organizer = signup("Sec");

        mvc.perform(get("/api/me/events")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/events").header("Authorization", "Bearer garbage")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/attendees")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(organizer))).andExpect(status().isForbidden());

        String admin = login("admin@test.local", "AdminPass123!");
        mvc.perform(get("/api/admin/users").header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void loginFailsWithoutRevealingWhichPartWasWrong() throws Exception {
        String email = "known-" + UUID.randomUUID() + "@example.com";
        signupWithEmail("Known", email);

        for (Map<String, String> creds : new Map[]{Map.of("email", email, "password", "wrong-password"),
                Map.of("email", "nobody@example.com", "password", "whatever123")}) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(creds)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("Invalid credentials"));
        }
    }

    @Test
    void validationErrorsListOffendingFields() throws Exception {
        String token = signup("Val");

        mvc.perform(post("/api/events").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("title", " ", "venue", "Hall", "capacity", 0,
                                "startsAt", Instant.now().plus(1, ChronoUnit.DAYS).toString(),
                                "endsAt", Instant.now().plus(2, ChronoUnit.DAYS).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists())
                .andExpect(jsonPath("$.fieldErrors.capacity").exists());

        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "A", "email", "not-an-email", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void duplicateSignupIsRejected() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@example.com";
        signupWithEmail("First", email);

        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("name", "Second", "email", email, "password", "password123"))))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownRoutesReturn404NotServerError() throws Exception {
        mvc.perform(get("/api/does-not-exist")).andExpect(status().isUnauthorized()); // not public => auth first
        String token = signup("Nf");
        mvc.perform(get("/api/does-not-exist").header("Authorization", bearer(token))).andExpect(status().isNotFound());
    }

    // ---- helpers ------------------------------------------------------------------------

    private String signup(String name) throws Exception {
        return signupWithEmail(name, name.toLowerCase() + "-" + UUID.randomUUID() + "@example.com");
    }

    private String signupWithEmail(String name, String email) throws Exception {
        return body(mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", name, "email", email, "password", "password123"))))
                .andExpect(status().isCreated())).get("token").asText();
    }

    private String login(String email, String password) throws Exception {
        return body(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())).get("token").asText();
    }

    private long createEvent(String token, int capacity) throws Exception {
        return body(mvc.perform(post("/api/events").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(eventPayload(capacity))))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long addTicketType(String token, long eventId, String name, String price, int quantity) throws Exception {
        return body(mvc.perform(post("/api/events/{id}/ticket-types", eventId).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", name, "price", price, "quantity", quantity))))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions register(long eventId, long ticketId, String email) throws Exception {
        return mvc.perform(post("/api/events/{id}/registrations", eventId).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("ticketTypeId", ticketId, "attendeeName", "Attendee", "attendeeEmail", email))));
    }

    private Map<String, Object> eventPayload(int capacity) {
        return Map.of("title", "Tech Summit " + UUID.randomUUID(), "description", "A conference", "venue", "Main Hall",
                "capacity", capacity,
                "startsAt", Instant.now().plus(10, ChronoUnit.DAYS).toString(),
                "endsAt", Instant.now().plus(11, ChronoUnit.DAYS).toString());
    }

    private JsonNode body(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
