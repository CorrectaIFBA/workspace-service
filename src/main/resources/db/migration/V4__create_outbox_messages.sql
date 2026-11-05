-- Messages for RabbitMQ, written in the same transaction as the change they
-- announce and published afterwards by the outbox relay (at least once).

CREATE TABLE IF NOT EXISTS outbox_messages (
    id           VARCHAR(255)                NOT NULL,
    exchange     VARCHAR(255)                NOT NULL,
    routing_key  VARCHAR(255)                NOT NULL,
    payload      TEXT                        NOT NULL,
    created_at   TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    available_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP(6) WITH TIME ZONE,
    attempts     INTEGER                     NOT NULL DEFAULT 0,
    last_error   TEXT,
    CONSTRAINT pk_outbox_messages PRIMARY KEY (id)
);

-- The relay only ever reads unpublished rows.
CREATE INDEX IF NOT EXISTS idx_outbox_messages_pending
    ON outbox_messages (published_at, available_at);
