-- EventPulse 360 : initial schema (MySQL 8)

CREATE TABLE users (
    user_id       BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE events (
    event_id     BIGINT        NOT NULL AUTO_INCREMENT,
    title        VARCHAR(200)  NOT NULL,
    description  VARCHAR(2000) NULL,
    venue        VARCHAR(200)  NOT NULL,
    capacity     INT           NOT NULL,
    starts_at    DATETIME(6)   NOT NULL,
    ends_at      DATETIME(6)   NOT NULL,
    status       VARCHAR(20)   NOT NULL,
    organizer_id BIGINT        NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    PRIMARY KEY (event_id),
    CONSTRAINT fk_events_organizer FOREIGN KEY (organizer_id) REFERENCES users (user_id),
    CONSTRAINT ck_events_capacity CHECK (capacity > 0),
    CONSTRAINT ck_events_dates CHECK (ends_at > starts_at),
    INDEX idx_events_status_starts (status, starts_at),
    INDEX idx_events_organizer (organizer_id)
) ENGINE = InnoDB;

-- A "ticket" product for an event (e.g. General, VIP) with its own price and inventory.
CREATE TABLE ticket_types (
    ticket_id  BIGINT         NOT NULL AUTO_INCREMENT,
    event_id   BIGINT         NOT NULL,
    name       VARCHAR(100)   NOT NULL,
    price      DECIMAL(10, 2) NOT NULL,
    quantity   INT            NOT NULL,
    sold       INT            NOT NULL DEFAULT 0,
    PRIMARY KEY (ticket_id),
    CONSTRAINT fk_ticket_types_event FOREIGN KEY (event_id) REFERENCES events (event_id),
    CONSTRAINT uk_ticket_types_event_name UNIQUE (event_id, name),
    CONSTRAINT ck_ticket_types_price CHECK (price >= 0),
    -- Last line of defence against overbooking: even a buggy service cannot oversell.
    CONSTRAINT ck_ticket_types_inventory CHECK (sold >= 0 AND sold <= quantity)
) ENGINE = InnoDB;

CREATE TABLE attendees (
    attendee_id BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    email       VARCHAR(150) NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (attendee_id),
    CONSTRAINT uk_attendees_email UNIQUE (email)
) ENGINE = InnoDB;

CREATE TABLE registrations (
    registration_id BIGINT         NOT NULL AUTO_INCREMENT,
    attendee_id     BIGINT         NOT NULL,
    event_id        BIGINT         NOT NULL,
    ticket_id       BIGINT         NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    ticket_code     VARCHAR(36)    NOT NULL,
    amount          DECIMAL(10, 2) NOT NULL,
    -- 1 while CONFIRMED, NULL once CANCELLED. MySQL unique indexes allow many NULLs, so this
    -- gives "one live registration per attendee per event" while still permitting re-booking.
    active_flag     TINYINT(1)     NULL,
    registered_at   DATETIME(6)    NOT NULL,
    cancelled_at    DATETIME(6)    NULL,
    PRIMARY KEY (registration_id),
    CONSTRAINT fk_reg_attendee FOREIGN KEY (attendee_id) REFERENCES attendees (attendee_id),
    CONSTRAINT fk_reg_event FOREIGN KEY (event_id) REFERENCES events (event_id),
    CONSTRAINT fk_reg_ticket FOREIGN KEY (ticket_id) REFERENCES ticket_types (ticket_id),
    CONSTRAINT uk_reg_ticket_code UNIQUE (ticket_code),
    CONSTRAINT uk_reg_one_active UNIQUE (attendee_id, event_id, active_flag),
    INDEX idx_reg_event_registered (event_id, registered_at),
    INDEX idx_reg_ticket (ticket_id)
) ENGINE = InnoDB;

CREATE TABLE payments (
    payment_id      BIGINT         NOT NULL AUTO_INCREMENT,
    registration_id BIGINT         NOT NULL,
    amount          DECIMAL(10, 2) NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    method          VARCHAR(30)    NOT NULL,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    PRIMARY KEY (payment_id),
    CONSTRAINT fk_payments_registration FOREIGN KEY (registration_id) REFERENCES registrations (registration_id),
    CONSTRAINT uk_payments_registration UNIQUE (registration_id),
    INDEX idx_payments_status (status)
) ENGINE = InnoDB;
