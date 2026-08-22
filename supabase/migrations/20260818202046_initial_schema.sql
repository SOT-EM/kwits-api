-- Initial schema for kwits.
--
-- Scope: only what the first end-to-end round trip needs (login, then GET /plans).
-- Columns mirror kwits-mobile/src/types/user.ts and travelPlan.ts, converted from
-- camelCase to snake_case. Later tables (expenses, payment proofs, ower balances)
-- have TypeScript types already but are deliberately not created yet.
--
-- Access model: kwits-mobile never talks to Supabase directly. Every read and write
-- goes through kwits-api, which connects over JDBC as a privileged role. RLS is
-- therefore enabled with NO policies on purpose -- see the note at the bottom.

-- profiles: application-level user data, keyed to Supabase Auth users.
-- Mirrors the User interface (id, name, email, avatarUrl?).
create table if not exists public.profiles (
    id          uuid        primary key references auth.users (id) on delete cascade,
    name        text        not null default '',
    email       text        not null,
    avatar_url  text,
    created_at  timestamptz not null default now()
);

-- travel_plans: mirrors the TravelPlan interface.
-- estimated_cost is nullable because estimatedCost is optional; numeric(12,2)
-- rather than a float because it holds PHP currency amounts.
create table if not exists public.travel_plans (
    id              uuid          primary key default gen_random_uuid(),
    host_id         uuid          not null references public.profiles (id) on delete cascade,
    title           text          not null,
    start_date      date          not null,
    end_date        date          not null,
    estimated_cost  numeric(12,2),
    created_at      timestamptz   not null default now(),
    constraint travel_plans_dates_ordered check (end_date >= start_date)
);

-- GET /plans filters by host_id, so index it.
create index if not exists travel_plans_host_id_idx on public.travel_plans (host_id);

-- Keep profiles in step with Supabase Auth. Without this, a signup creates an
-- auth.users row with no matching profile, and any later travel_plans insert
-- would fail its foreign key.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.profiles (id, name, email)
    values (
        new.id,
        coalesce(new.raw_user_meta_data ->> 'name', ''),
        new.email
    )
    on conflict (id) do nothing;
    return new;
end;
$$;

drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- Row Level Security: enabled, intentionally with no policies.
--
-- This is not an oversight. Supabase auto-exposes every table in `public` over
-- PostgREST to the `anon` and `authenticated` roles. Enabling RLS without policies
-- denies those roles entirely, which closes that path off and makes the
-- "mobile must go through kwits-api" rule enforced by the database rather than by
-- convention. kwits-api connects as the postgres/service role, which bypasses RLS,
-- so it is unaffected.
--
-- Consequence to be aware of: authorization is now entirely kwits-api's job. If a
-- future service ever connects with the anon or authenticated role, it will read
-- nothing until explicit policies are added here.
alter table public.profiles      enable row level security;
alter table public.travel_plans  enable row level security;
