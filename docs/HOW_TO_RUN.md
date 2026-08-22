# How to run kwits-api

This guide assumes you've completed [HOW_TO_SETUP.md](./HOW_TO_SETUP.md) — Java 17, Docker, the Supabase CLI, `.env`, and the applied migration are all in place.

> **Last reconciled against the repo:** `pom.xml`, `application.yml`, `supabase/config.toml`, and the controller routes were checked against their current file contents as of this revision.

## 1. The two things that must be running

kwits-api on its own does nothing useful — it needs Supabase behind it. Two terminals, both from the repo root:

```bash
supabase start            # terminal 1: Postgres + Auth in Docker
./mvnw spring-boot:run    # terminal 2: the API on port 8080
```

`supabase start` is idempotent and keeps running in the background once started, so in practice you start it in the morning and only restart the API.

Windows PowerShell uses `.\mvnw.cmd` instead of `./mvnw` throughout this doc.

## 2. Which port is which

Four ports are in play and mixing them up is the most common early confusion:

| Port | Service | Who talks to it |
| --- | --- | --- |
| `8080` | **kwits-api** | The mobile app. This is the only port the app knows about. |
| `54321` | Supabase Auth/REST | kwits-api only. The app must never call this. |
| `54322` | Postgres | kwits-api, via JDBC. Also `psql` and DB clients. |
| `54323` | Supabase Studio | You, in a browser. |

If the mobile app is configured to hit `54321`, that is the architecture being bypassed — it should always be `8080`.

## 3. The API surface

Three routes exist. That is deliberate: this phase implements one complete round trip rather than scaffolding every future endpoint.

| Method | Route | Auth | Purpose |
| --- | --- | --- | --- |
| `POST` | `/auth/signup` | Public | Creates a Supabase Auth user, returns a session |
| `POST` | `/auth/login` | Public | Exchanges email/password for a Supabase access token |
| `GET` | `/plans` | **Bearer token** | The signed-in user's travel plans |

Anything not listed is `authenticated` by default — `SecurityConfig` permits only the two `/auth` routes and requires a valid token for everything else. So a typo in a URL returns 401, not 404, which is worth knowing before you spend time debugging the wrong thing.

### Tokens

kwits-api does not issue its own JWTs. `/auth/login` forwards credentials to Supabase and passes Supabase's `access_token` straight back. `/plans` then validates that same token. One token system, one expiry policy, one place to rotate keys.

This means a token obtained from kwits-api is also a valid Supabase token. That is fine — the mobile app never sees Supabase's address, and RLS with no policies means the token grants nothing against Supabase's own REST API.

## 4. Verifying the full round trip

Run these from your host terminal, in order. This is the check that actually proves the chain works.

**Step 1 — the API is up and protecting its routes:**

```bash
curl -i http://localhost:8080/plans
```

Expect **401**. A 401 here is success. Connection refused means the API isn't running.

**Step 2 — create a test user:**

```bash
curl -X POST http://localhost:8080/auth/signup \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@example.com","password":"hunter22","name":"Test User"}'
```

Expect JSON with an `accessToken`. If you get **202** with a message about confirming your email, local Auth has confirmations enabled — open the Inbucket URL from `supabase status` and click through the message that landed there.

**Step 3 — log in and call the protected route:**

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@example.com","password":"hunter22"}' | jq -r .accessToken)

curl -s http://localhost:8080/plans -H "Authorization: Bearer $TOKEN"
```

Expect `[]`. An empty array is the win: the request was authenticated, the `sub` claim was read, JPA queried `travel_plans`, and the user genuinely has no plans yet.

**Step 4 — prove it isn't returning an empty list by accident.** Insert a row and fetch again:

```bash
psql postgresql://postgres:postgres@127.0.0.1:54322/postgres -c \
  "insert into travel_plans (host_id, title, start_date, end_date)
   select id, 'Test Trip', '2026-09-01', '2026-09-05' from profiles limit 1;"

curl -s http://localhost:8080/plans -H "Authorization: Bearer $TOKEN"
```

Now expect one plan. Skipping this step is how an empty array from a silently-broken query gets mistaken for a working round trip.

**Step 5 — the mobile app.** With the API confirmed, follow [kwits-mobile/docs/HOW_TO_RUN.md](../../kwits-mobile/docs/HOW_TO_RUN.md) and log in through the UI. The Dashboard renders the same list you just fetched.

## 5. Tests

```bash
./mvnw test
```

`AuthControllerTest` is a web-layer slice test: Supabase and Postgres are both mocked, so it needs no Docker and no credentials. It covers three things — the token pass-through, rejection of a malformed email before Supabase is ever called, and bad credentials surfacing as 401.

What it does **not** cover: the real Supabase call, the JPA query, and the token validation. Those need a live stack, which is what section 4 is for. A green `./mvnw test` does not mean the round trip works.

```bash
./mvnw verify      # tests + package the jar
./mvnw -q compile  # fastest check that the code still builds
```

## 6. When do you need to restart what?

| You changed | What to do |
| --- | --- |
| Java source | Restart `spring-boot:run`. No Spring DevTools is configured, so there is no hot reload. |
| `.env` or `application.yml` | Restart `spring-boot:run` — config is read at startup. |
| A file in `supabase/migrations/` | `supabase db reset`, then restart the API so `ddl-auto: validate` re-checks. |
| `supabase/config.toml` | `supabase stop && supabase start`. |
| `pom.xml` | Restart; the wrapper resolves new dependencies on the next run. |

## 7. Working with the database

Studio at `http://127.0.0.1:54323` gives you the same interface as the hosted dashboard — browse tables, run SQL, inspect RLS.

```bash
supabase db reset                  # wipe + replay all migrations (destructive)
supabase migration new <name>      # scaffold a new timestamped migration
supabase migration list            # what's applied where
psql postgresql://postgres:postgres@127.0.0.1:54322/postgres
```

Two rules worth internalising:

**The schema is owned by `supabase/migrations/`, never by Hibernate.** `spring.jpa.hibernate.ddl-auto` is `validate`, so Hibernate will refuse to start on a mismatch rather than quietly altering your tables. Adding a column means writing a migration, not editing an entity and hoping.

**`supabase db reset` is destructive.** It is the right tool locally and the wrong tool against anything hosted. For a linked remote project the command is `supabase db push`.

## 8. Stopping

```bash
supabase stop                 # stops containers, preserves data
supabase stop --no-backup     # stops and discards all local data
```

The API itself is just Ctrl+C. If port 8080 is still held afterwards, a previous run didn't exit cleanly — find and kill it rather than switching ports, or the mobile app's `API_BASE_URL` will stop matching.

## 9. CI

There is no CI workflow in this repo yet. The mobile repo has three GitHub Actions (`ci.yml`, `enforce-branch-flow.yml`, `pr-title-check.yml`); nothing equivalent exists here, so `./mvnw verify` locally is currently the only gate. Worth adding.

A change spanning both repos is two PRs with no shared CI. Merge and deploy the API side first: the app calling an endpoint that doesn't exist yet fails at runtime, while an endpoint nothing calls yet harms nothing.
