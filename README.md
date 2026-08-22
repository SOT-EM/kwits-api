# kwits-api

Spring Boot backend for [kwits-mobile](../kwits-mobile). Replaces Supabase as the
app's backend: the mobile client talks only to this service, and only this service
talks to Supabase.

```
kwits-mobile  ->  kwits-api  ->  Supabase (Auth + Postgres)
```

Spring Boot 4.1.1 on Java 17, built with Maven via the bundled wrapper (`./mvnw`), so
no global Maven install is required.

## Layout

```
src/main/java/com/kwits/sotm/api/
  auth/      POST /auth/login, POST /auth/signup -- proxied to Supabase Auth
  plans/     GET /plans (protected) -- reads travel_plans over JPA
  config/    SecurityConfig (JWT validation), SupabaseProperties
  common/    ApiError, ApiException, GlobalExceptionHandler
supabase/    Supabase CLI project: config.toml and migrations
```

## Tokens

kwits-api does **not** issue its own JWTs. `/auth/login` forwards the credentials to
Supabase and passes Supabase's `access_token` straight back to the client. Protected
routes then validate that same token. One token system, one expiry policy, one place
to rotate keys.

The signing algorithm is decided in exactly one place: the `jwtDecoder()` method in
`config/SecurityConfig.java`. It is built for symmetric HS256, which is what local
Supabase uses (`supabase/config.toml` leaves `signing_keys_path` commented out, so the
stack falls back to the shared secret). A hosted project on the newer
publishable/secret key system may sign asymmetrically instead; that method's comment
has the two-line JWKS replacement.

## Database

The schema is owned by `supabase/migrations/`, never by Hibernate:
`spring.jpa.hibernate.ddl-auto` is `validate`, so an entity that disagrees with the
table fails at startup rather than quietly altering the database.

RLS is enabled on both tables with **no policies**, which denies Supabase's
auto-generated PostgREST API to the `anon` and `authenticated` roles. That makes
"mobile must go through kwits-api" a database-level guarantee rather than a
convention. kwits-api connects as the privileged role and bypasses RLS, so
authorization is entirely this service's responsibility.

## Local dev contract with kwits-mobile

Both repos are cloned as siblings:

```
kwits/
  kwits-mobile/
  kwits-api/      # this repo
```

| Repo | Variable | Default |
| --- | --- | --- |
| kwits-api | `SERVER_PORT` | `8080` |
| kwits-mobile | `API_BASE_URL` | `http://10.0.2.2:8080` |

`10.0.2.2` is the Android emulator's alias for the host machine; `localhost` inside
the emulator refers to the emulator itself. A physical device needs the host's real
LAN IP. `API_BASE_URL` is read in mobile's `app.config.ts` and reaches the app as
`Constants.expoConfig.extra.apiBaseUrl`.

A React Native client does not enforce CORS, so no CORS config exists here. Add it
only if you start serving the app through Expo web.

## Documentation

| Doc | What it covers |
| --- | --- |
| [docs/HOW_TO_SETUP.md](./docs/HOW_TO_SETUP.md) | Fresh machine to a runnable backend: Java 17, Docker, Supabase CLI, `.env`, migrations |
| [docs/HOW_TO_RUN.md](./docs/HOW_TO_RUN.md) | Day-to-day loop, which port is which, the API surface, the full round-trip walkthrough |
| [docs/HOW_TO_DEBUG.md](./docs/HOW_TO_DEBUG.md) | Troubleshooting runbook: startup failures, token validation, schema mismatches |

Mobile client setup and running lives in [kwits-mobile/docs](../kwits-mobile/docs).

## Quick start

Assuming setup is done:

```bash
supabase start            # Postgres on 54322, Auth/API on 54321
./mvnw spring-boot:run    # this API on 8080
```

```bash
curl -i http://localhost:8080/plans
```

A `401` is the healthy answer: the route exists and is protected. There is no public
health endpoint, so this is the probe. The full round-trip walkthrough, including
creating a test user and proving the database query actually works, is in
[docs/HOW_TO_RUN.md](./docs/HOW_TO_RUN.md#4-verifying-the-full-round-trip).

## Tests

```bash
./mvnw test
```

Three web-layer tests with Supabase and Postgres mocked out, so they need no Docker
and no credentials. They do not cover the real Supabase call, the JPA query, or token
validation — a green run does not mean the round trip works. Verify that per the doc
above.
