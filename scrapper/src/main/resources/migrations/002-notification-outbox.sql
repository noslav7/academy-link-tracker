--liquibase formatted sql

--changeset link-tracker:002-notification-outbox
create table if not exists notification_outbox (
    id bigserial primary key,
    payload jsonb not null,
    created_at timestamptz not null default now(),
    processed_at timestamptz null
);

create index if not exists idx_notification_outbox_unprocessed
    on notification_outbox(id)
    where processed_at is null;
