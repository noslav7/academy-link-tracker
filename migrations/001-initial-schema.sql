--liquibase formatted sql

--changeset link-tracker:001-init-schema
create table if not exists chats (
    chat_id bigint primary key,
    created_at timestamptz not null default now()
);

create table if not exists tracked_links (
    id bigserial primary key,
    chat_id bigint not null references chats(chat_id) on delete cascade,
    url text not null,
    last_checked_at timestamptz not null,
    last_updated_at timestamptz not null,
    created_at timestamptz not null default now(),
    constraint uq_tracked_links_chat_url unique (chat_id, url)
);

create table if not exists tracked_link_tags (
    id bigserial primary key,
    tracked_link_id bigint not null references tracked_links(id) on delete cascade,
    tag text not null,
    constraint uq_tracked_link_tags_unique_pair unique (tracked_link_id, tag)
);

create table if not exists tracked_link_filters (
    id bigserial primary key,
    tracked_link_id bigint not null references tracked_links(id) on delete cascade,
    filter_value text not null,
    constraint uq_tracked_link_filters_unique_pair unique (tracked_link_id, filter_value)
);

create index if not exists idx_tracked_links_chat_id on tracked_links(chat_id);
create index if not exists idx_tracked_links_last_updated_at on tracked_links(last_updated_at);
create index if not exists idx_tracked_links_url on tracked_links(url);
