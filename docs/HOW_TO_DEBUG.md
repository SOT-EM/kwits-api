# How to debug kwits-api

A troubleshooting runbook for the backend. Each entry is a failure this specific stack can actually produce, with the diagnostic that distinguishes it from the ones that look identical.

**How to use this:** Ctrl+F the error text, jump to that section, apply the fix. Verified against this repo's config as of this revision — if something has since changed, trust the repo over this doc.

Client-side failures — the Android build, the emulator not reaching the API — are in [kwits-mobile/docs/HOW_TO_DEBUG.md](../../kwits-mobile/docs/HOW_TO_DEBUG.md).

**Start here.** Nearly every problem below is one of three things, in this order of likelihood:

1. Docker isn't running, so there is no Supabase and no database.
2. `.env` is incomplete or holds a stale value from a previous `supabase start`.
3. The migration hasn't been applied to the database you're actually pointed at.

---

## Problem 1: Startup fails with "Property 'supabase.anon-key' must not be blank"

**Symptom:** the app exits during startup, before Tomcat binds a port:

```
Binding to target ... failed:
    Property: supabase.anon-key
    Value: ""
    Reason: must not be blank
```

**Cause:** working as designed. `SupabaseProperties` marks all three `supabase.*` values `@NotBlank`, so a missing one stops the boot rather than letting the app run and fail confusingly on the first login attempt.

**Fix:** fill the named property in `.env` from `supabase status`. The mapping is not always obvious from the property name:

| Property | `supabase status` line |
| --- | --- |
| `supabase.url` | `API URL` |
| `supabase.anon-key` | `anon key` |
| `supabase.jwt-secret` | `JWT secret` |

Two things that silently produce a "blank" value: `.env` not being at the repo root (`spring-dotenv` looks there), and a value you pasted with surrounding quotes.

## Problem 2: Startup fails with "Schema-validation: missing table [travel_plans]"

**Symptom:**

```
org.hibernate.tool.schema.spi.SchemaManagementException:
  Schema-validation: missing table [travel_plans]
```

or a variant naming a missing column.

**Cause:** `spring.jpa.hibernate.ddl-auto` is `validate`, so Hibernate compares each entity against the real database and refuses to start on a mismatch. It will not create or alter anything. The database you are connected to does not have the schema.

**Fix:** apply the migration:

```bash
supabase db reset
```

If that already ran, you are connected to a different database than you think. Check `SPRING_DATASOURCE_URL` really points at port **54322** (Postgres), not 54321 (the Supabase API), and confirm the table exists:

```bash
psql postgresql://postgres:postgres@127.0.0.1:54322/postgres -c '\dt public.*'
```

**Do not "fix" this by switching `ddl-auto` to `update`.** That would let Hibernate mutate a schema that `supabase/migrations/` owns, and the two would drift apart silently. The failure is the feature.

## Problem 3: `/v1/auth/login` returns a token, but `/v1/plans` returns 401

**Symptom:** login succeeds, and the very next call with that token is rejected. In the mobile app this shows up as being signed out immediately after signing in.

**Cause:** the token was issued by Supabase (login only proxies, it does not validate) but rejected by kwits-api's decoder. Almost always `SUPABASE_JWT_SECRET` does not match the secret Supabase actually signed with. `supabase start` can regenerate it, so a value copied a while ago goes stale.

**Fix:** reprint the current secret and compare it to `.env` character for character:

```bash
supabase status
```

Then restart the API — the secret is read once at startup, so editing `.env` alone changes nothing.

**Confirm it's the secret and not something else** by decoding the token's header. It needs no tooling beyond base64:

```bash
echo "$TOKEN" | cut -d. -f1 | base64 -d
```

- `{"alg":"HS256",...}` — symmetric, and `SUPABASE_JWT_SECRET` is the right mechanism. A mismatch is the cause.
- `{"alg":"ES256"...}` or `{"alg":"RS256"...}` with a `kid` — asymmetric. `SUPABASE_JWT_SECRET` cannot ever validate this. Go to Problem 4.

Isolate the app from the equation first: if the two-curl sequence in [HOW_TO_RUN.md section 4](./HOW_TO_RUN.md#4-verifying-the-full-round-trip) also gives 401, the mobile client is not involved.

## Problem 4: The token is signed asymmetrically (ES256 / RS256), not HS256

**Symptom:** Problem 3's header decode shows `ES256` or `RS256`, and no value of `SUPABASE_JWT_SECRET` will ever work.

**Cause:** Supabase's newer publishable/secret key system can sign with asymmetric keys published at a JWKS endpoint instead of a shared secret. This repo's decoder is built for HS256, which is what **local** Supabase uses — `supabase/config.toml` leaves `signing_keys_path` commented out, so the stack falls back to the symmetric secret. A hosted project may differ.

**Fix:** this is a deliberately contained change. Replace the body of `jwtDecoder()` in `config/SecurityConfig.java`:

```java
@Bean
JwtDecoder jwtDecoder() {
    return NimbusJwtDecoder
            .withJwkSetUri(supabase.url() + "/auth/v1/.well-known/jwks.json")
            .build();
}
```

Then drop `@NotBlank` from `jwtSecret` in `SupabaseProperties` (or remove the field), and remove `SUPABASE_JWT_SECRET` from `.env` and `.env.example`. Nothing else changes — that method exists solely to hold this decision in one place.

Check which one a project uses before writing code:

```bash
curl -s http://127.0.0.1:54321/auth/v1/.well-known/jwks.json
```

A populated `keys` array means asymmetric signing is available. Empty or 404 means symmetric.

## Problem 5: `/v1/auth/login` returns 401 for credentials you're sure are correct

**Symptom:** `{"status":401,"message":"Invalid email or password"}` for a user you just created.

**Cause:** `AuthService` collapses every error from Supabase's token endpoint into one 401 on purpose — distinguishing "no such user" from "wrong password" would tell an attacker which emails are registered. The tradeoff is that several distinct problems look identical here.

**Work through these in order:**

1. **Email not confirmed.** If signup returned 202 rather than a token, the account exists but cannot sign in yet. Open the Inbucket URL from `supabase status` and click the confirmation link.
2. **The user doesn't exist in the database you're pointed at.** `supabase db reset` wipes `auth.users` along with everything else, so any user created before your last reset is gone.
3. **Wrong `SUPABASE_ANON_KEY`.** Supabase rejects the request at the `apikey` header before it ever looks at the credentials.
4. **Password below the minimum.** `config.toml` sets `minimum_password_length = 6`; signup rejects shorter, so the account was never created.

To see what Supabase actually said, rather than the message this API substitutes, call it directly:

```bash
curl -i -X POST "http://127.0.0.1:54321/auth/v1/token?grant_type=password" \
  -H "apikey: $SUPABASE_ANON_KEY" \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@example.com","password":"hunter22"}'
```

That response is unfiltered and usually names the real problem outright.

## Problem 6: `supabase start` fails, or the API can't reach Postgres

**Symptom:** `supabase start` errors immediately, or the API throws `Connection to 127.0.0.1:54322 refused`.

**Cause:** Docker isn't running. Installed is not the same as running — the daemon has to be up.

**Fix:**

```bash
docker ps          # must list containers without erroring
supabase status    # must show services running
```

If Docker is fine but a port is taken, something else is on it (a second Supabase project, or a stray Postgres). `supabase stop` in the other project's directory, or find the holder:

```powershell
netstat -ano | findstr :54322     # Windows
```

```bash
lsof -i :54322                    # macOS/Linux
```

Two Supabase projects cannot share these ports. If you also have a stack running from the old pre-split repo, stop that one — this repo's `supabase/config.toml` claims the same ports, since `project_id` is still `"kwits"`.

## Problem 7: `GET /v1/plans` returns `[]` for a user who should have plans

**Symptom:** 200 with an empty array, no error.

**Cause:** the query ran and matched nothing. `findByHostIdOrderByStartDateAsc` filters on `host_id` against the token's `sub` claim, so a plan owned by a *different* user is correctly invisible.

**Fix:** confirm whose id the token carries and what the table holds:

```bash
echo "$TOKEN" | cut -d. -f2 | base64 -d          # look at "sub"
psql postgresql://postgres:postgres@127.0.0.1:54322/postgres -c \
  'select id, host_id, title from travel_plans;'
```

If `host_id` values don't match `sub`, the rows belong to someone else — that is authorization working, not a bug. Insert a row for the right user per [HOW_TO_RUN.md section 4](./HOW_TO_RUN.md#4-verifying-the-full-round-trip).

## Problem 8: A route you know exists returns 401 instead of 404

**Symptom:** a typo'd or not-yet-built path returns 401 rather than "not found".

**Cause:** `SecurityConfig` permits `/v1/auth/login` and `/v1/auth/signup` and requires authentication for `anyRequest()`. Security runs before routing, so an unauthenticated request to a nonexistent path is rejected for lacking a token before anything discovers there's no handler.

**Fix:** nothing to fix — but don't let it mislead you. Retry with a valid token: a real 404 then surfaces, which tells you the route is genuinely missing rather than protected.

## Problem 9: `./mvnw` fails on the first run

**Symptom:** the wrapper errors while downloading Maven, or `JAVA_HOME` complaints.

**Cause:** the wrapper fetches its pinned Maven into `~/.m2` on first use (`.mvn/wrapper/maven-wrapper.jar` is gitignored deliberately), so the first run needs network access. Separately, it needs a JDK it can find.

**Fix:**

```bash
java -version      # must be 17 or newer
./mvnw -v          # PowerShell: .\mvnw.cmd -v
```

On Windows, use `.\mvnw.cmd` from PowerShell — the extensionless `./mvnw` is a shell script and needs a POSIX shell such as Git Bash.

---

## General lessons

- **A 401 from `/v1/plans` with no token is health, not failure.** There is no public health endpoint, so that is the probe. Learn to read it as "up and protected."
- **An empty array is not proof of a working round trip.** Insert a row and fetch it back; a silently-broken query returns `[]` too.
- **Restart the API after touching `.env`.** Config is read once at startup. Editing the file and re-running curl changes nothing.
- **Startup failures are the good failures.** A missing property or a schema mismatch stops the boot with the cause named. Both were configured to behave that way rather than degrade into a runtime 500 later.
- **Decode the token before theorising.** `cut -d. -f1 | base64 -d` on the header and `-f2` on the payload answers most auth questions in one command, and it needs no tooling.
- **`supabase db reset` wipes users too.** A login that worked yesterday and fails today, right after a reset, is not a regression in the code.
