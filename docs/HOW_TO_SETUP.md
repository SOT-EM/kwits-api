# How to set up kwits-api

This guide takes you from a fresh machine to a runnable backend. It assumes zero prior context about the project.

**Scope:** this repo is the backend. It is the only thing in the kwits project that holds Supabase credentials or talks to Postgres. The mobile client has its own setup guide at [kwits-mobile/docs/HOW_TO_SETUP.md](../../kwits-mobile/docs/HOW_TO_SETUP.md); you do not need to set up the app to work on the API.

## 1. Prerequisites

### Java 17

The project targets **Java 17** (`java.version` in `pom.xml`). Spring Boot 4 requires 17 as a minimum, so a newer JDK also works, but 17 is what this is built and verified against.

```bash
java -version
```

If you need to install it, [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=17) is a reasonable default. On Windows, note that Android Studio ships its own bundled JBR — that one is for Gradle builds in the mobile repo and is not what you want on `PATH` here.

### Maven — you do not need to install it

The repo includes the **Maven Wrapper**, so there is nothing to install. Use `./mvnw` (or `mvnw.cmd` on Windows PowerShell/CMD) everywhere you would normally type `mvn`:

```bash
./mvnw -v
```

The first run downloads the pinned Maven version into `~/.m2`. That is expected and only happens once. `.mvn/wrapper/maven-wrapper.jar` is gitignored for the same reason — it is fetched on demand.

### Docker Desktop

The local Supabase stack (Postgres + Auth) runs in Docker. The daemon must actually be **running**, not merely installed:

```bash
docker --version
docker ps        # must not error
```

### Supabase CLI

Used to start the local stack and apply migrations.

**Windows — install via [Scoop](https://scoop.sh/), not npm.** The npm-distributed build bundles a Bun runtime that segfaults on many Windows machines:

```powershell
scoop bucket add supabase https://github.com/supabase/scoop-bucket.git
scoop install supabase
```

**macOS — Homebrew:**

```bash
brew install supabase/tap/supabase
```

**Linux —** see the [CLI release page](https://github.com/supabase/cli/releases) for the appropriate package.

Verify:

```bash
supabase --version
```

> This guidance moved here from the mobile repo's setup doc. The mobile app no longer needs the Supabase CLI at all — if you find that instruction in an old copy of `kwits-mobile/docs`, it is stale.

## 2. Clone both repos as siblings

kwits is two independent repos with separate histories and remotes — not a monorepo, and there is no git repo at the parent level. Cloning them side by side keeps both open in one editor and makes the relative links in these docs (`../../kwits-mobile`) resolve:

```bash
mkdir kwits && cd kwits
git clone <KWITS_API_REPO_URL>    kwits-api
git clone <KWITS_MOBILE_REPO_URL> kwits-mobile
```

```
kwits/
├── kwits-api/       this repo
└── kwits-mobile/    React Native / Expo client
```

You can work on this repo alone; the sibling layout only matters for doc links and for running the full stack.

## 3. Start the local Supabase stack

Run this from the repo root — the CLI finds its project via `supabase/config.toml`, which lives here:

```bash
cd kwits-api
supabase start
```

The first run pulls a Docker image per service, so it is much slower than every run after. When it finishes it prints the connection details. The ports come from this repo's `supabase/config.toml`:

```
API URL: http://127.0.0.1:54321
DB URL: postgresql://postgres:postgres@127.0.0.1:54322/postgres
Studio URL: http://127.0.0.1:54323
Inbucket URL: http://127.0.0.1:54324
JWT secret: <shown in your terminal>
anon key: <shown in your terminal>
service_role key: <shown in your terminal>
```

| Line | What it's for |
| --- | --- |
| `API URL` | Supabase's own Auth/REST endpoint. **kwits-api** calls this; the mobile app never does. |
| `DB URL` | Direct Postgres connection. This is what `SPRING_DATASOURCE_URL` points at. |
| `Studio URL` | Local web UI for browsing tables and running SQL. |
| `Inbucket URL` | Catches emails local Auth "sends" (signup confirmation, magic links) so you can read them without a mail provider. |
| `anon key` | Becomes `SUPABASE_ANON_KEY`. |
| `JWT secret` | Becomes `SUPABASE_JWT_SECRET`. This is how kwits-api verifies tokens. |
| `service_role key` | Full-access key that bypasses RLS. **Not used by this project** — do not put it in `.env`. |

If you lose the output, reprint it any time with:

```bash
supabase status
```

> These values are generated for your local stack. Copy them from your own terminal; never reuse a value from a doc or a teammate.

## 4. Environment variables

```bash
cp .env.example .env
```

Then fill in the blanks from `supabase status`:

| Variable | Where it comes from | Default in `.env.example` |
| --- | --- | --- |
| `SERVER_PORT` | Must match mobile's `API_BASE_URL` port | `8080` |
| `SUPABASE_URL` | `API URL` from `supabase status` | `http://127.0.0.1:54321` |
| `SUPABASE_ANON_KEY` | `anon key` from `supabase status` | **blank — you must fill this** |
| `SUPABASE_JWT_SECRET` | `JWT secret` from `supabase status` | **blank — you must fill this** |
| `SPRING_DATASOURCE_URL` | From the `DB URL` line | `jdbc:postgresql://127.0.0.1:54322/postgres` |
| `SPRING_DATASOURCE_USERNAME` | From the `DB URL` line | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | From the `DB URL` line | `postgres` |

`spring-dotenv` reads `.env` at startup, so no shell exports are needed — the same developer experience as the mobile repo. `.env` is gitignored; never commit it.

All three `supabase.*` properties are `@NotBlank`, so a missing one **fails startup immediately** with the offending property named. That is deliberate: a clear boot failure beats a confusing 401 on the first login.

Note the `SPRING_DATASOURCE_URL` uses a `jdbc:postgresql://` scheme, not the `postgresql://` scheme the CLI prints. Same host, port, and database — different prefix, because JDBC needs its own.

## 5. Apply the database migration

The schema lives in `supabase/migrations/` and is owned there, not by Hibernate:

```bash
supabase db reset
```

This **wipes the local database** and replays every migration from scratch. That is the point — it proves a migration works from a clean state rather than against a database you have hand-patched in Studio. It is also destructive, so know that any local test data goes with it.

What the current migration creates:

- `profiles` — application user data, keyed to `auth.users`. Mirrors the mobile app's `User` type.
- `travel_plans` — mirrors the mobile app's `TravelPlan` type, snake_cased.
- A trigger on `auth.users` that creates the matching `profiles` row on signup, so a new account is immediately usable.
- **RLS enabled on both tables with no policies.** This is intentional, not an oversight — see the note in the migration file and the README.

Confirm it worked in Studio (`http://127.0.0.1:54323`) or over psql:

```bash
psql postgresql://postgres:postgres@127.0.0.1:54322/postgres -c '\d public.travel_plans'
```

## 6. Verify the setup

```bash
./mvnw test        # 3 tests, no Docker or credentials needed
./mvnw spring-boot:run
```

If the app boots and logs a Tomcat port, you are set. Two failures worth recognising immediately:

- **A missing `supabase.*` property** stops startup before the web server binds.
- **`ddl-auto: validate`** compares each entity against its real table and fails startup naming the mismatch. If you see that, step 5 either didn't run or didn't finish.

Then confirm the API is answering:

```bash
curl -i http://localhost:8080/plans
```

A `401` is the correct answer — the route exists and is protected. There is no public health endpoint, so this is the health probe.

## 7. Hosted Supabase (staging / prod)

Everything above is the local Docker stack, which is how day-to-day work should happen: it is fast, free, and safe to break.

To point at a hosted project instead, replace `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_JWT_SECRET`, and the datasource values with that project's own. Two things to check before you do:

1. **Confirm the token signing algorithm.** Local Supabase uses a symmetric HS256 secret because `supabase/config.toml` leaves `signing_keys_path` commented out. A hosted project on the newer publishable/secret key system may sign asymmetrically instead, in which case `SUPABASE_JWT_SECRET` is not the right mechanism at all. The swap is a two-line change confined to the `jwtDecoder()` method in `config/SecurityConfig.java` — that method exists to contain exactly this decision. See Problem 4 in [HOW_TO_DEBUG.md](./HOW_TO_DEBUG.md).
2. **Never put the `service_role` / secret key in `.env`.** Nothing in this project needs it. It bypasses RLS entirely.

Applying migrations to a linked remote project is `supabase db push`, not `db reset` — `reset` would destroy production data.

---

Setup complete. Continue to [HOW_TO_RUN.md](./HOW_TO_RUN.md) for the day-to-day loop and the full round-trip walkthrough.
