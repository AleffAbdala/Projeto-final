create policy "Allow anon uploads to chat-media"
on storage.objects
for insert
to anon
with check (bucket_id = 'chat-media');

create policy "Allow anon updates to chat-media"
on storage.objects
for update
to anon
using (bucket_id = 'chat-media')
with check (bucket_id = 'chat-media');

create policy "Allow anon deletes to chat-media"
on storage.objects
for delete
to anon
using (bucket_id = 'chat-media');
