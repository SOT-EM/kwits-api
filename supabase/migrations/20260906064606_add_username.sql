alter table public.profiles
    add column if not exists username text;

create unique index if not exists profiles_username_unique_idx
    on public.profiles (username);

create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = public as $$
begin
    insert into public.profiles (id, name, email, username)
    values (
        new.id,
        coalesce(new.raw_user_meta_data ->> 'name', ''),
        new.email,
        new.raw_user_meta_data ->> 'username'
    )
    on conflict (id) do nothing;
    return new;
end;
$$;