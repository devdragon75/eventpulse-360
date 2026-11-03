"""Writes realistic registration events (with deliberate duplicates and bad rows) for the pipeline demo."""
import json
import random
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

random.seed(7)
out = Path(sys.argv[1] if len(sys.argv) > 1 else "sample_data/events.jsonl")
out.parent.mkdir(parents=True, exist_ok=True)

EVENTS = {
    1: ("Annual Tech Summit", {"General": 120.00, "VIP": 350.00}),
    2: ("Data Engineering Workshop", {"Standard": 80.00, "Early Bird": 55.00}),
    3: ("Product Leaders Forum", {"General": 200.00, "Sponsor": 900.00}),
}

lines, reg_id = [], 0
start = datetime.now(timezone.utc) - timedelta(days=21)
for _ in range(400):
    reg_id += 1
    event_id = random.choice(list(EVENTS))
    title, tickets = EVENTS[event_id]
    ticket, price = random.choice(list(tickets.items()))
    when = start + timedelta(days=random.random() * 21, seconds=random.randint(0, 86400))
    base = {
        "registrationId": reg_id, "eventId": event_id, "eventTitle": title, "ticketTypeId": event_id * 10,
        "ticketType": ticket, "attendeeId": reg_id, "attendeeEmail": f"Attendee{reg_id}@Example.com",
        "amount": f"{price:.2f}", "currency": "USD",
    }
    confirmed = {**base, "eventType": "REGISTRATION_CONFIRMED", "occurredAt": when.isoformat()}
    lines.append(confirmed)
    if random.random() < 0.08:  # ~8% cancel a few days later
        lines.append({**base, "eventType": "REGISTRATION_CANCELLED",
                      "occurredAt": (when + timedelta(days=random.randint(1, 4))).isoformat()})
    if random.random() < 0.05:  # at-least-once delivery: same message twice
        lines.append(confirmed)

raw_lines = [json.dumps(r) for r in lines]
raw_lines += ["{not valid json", json.dumps({"eventType": "REGISTRATION_CONFIRMED"}),
              json.dumps({**lines[0], "amount": "-5"})]          # three rows the Silver layer must reject
random.shuffle(raw_lines)
out.write_text("\n".join(raw_lines) + "\n")
print(f"wrote {len(raw_lines)} lines to {out}")
