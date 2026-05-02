truncate table tracked_link_filters, tracked_link_tags, tracked_links, chats restart identity cascade;

insert into chats (chat_id)
select gs
from generate_series(1, 1000) as gs;

insert into tracked_links (chat_id, url, last_checked_at, last_updated_at)
select
    c.chat_id,
    format('https://github.com/load/chat-%s/repo-%s', c.chat_id, link_no),
    now(),
    now()
from chats c
cross join generate_series(1, 100) as link_no;
