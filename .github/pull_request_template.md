<!--
   Fill every section. Delete a section only if it truly does not apply.
  Keep the PR title in Conventional Commits form, e.g. "feat: add expense split validation".
-->

## Summary

<!-- What does this PR do, and why? 1-3 sentences. -->

## Type of change

<!-- Check all that apply. -->

- [ ] Feature
- [ ] Bug fix
- [ ] Refactor (no behaviour change)
- [ ] Performance
- [ ] Tests
- [ ] Docs
- [ ] Chore / tooling / CI
- [ ] Breaking change

## Changes

## <!-- Bullet the key changes so reviewers know where to look. -->

## Endpoints / API surface affected

## <!-- Which routes changed or were added? Note any request/response shape changes that affect kwits-mobile. Delete if none. -->

## Supabase changes

<!-- New/changed tables, columns, RLS policies, or Storage buckets. Delete if none. -->

- [ ] SQL migration included and run against a dev/staging Supabase project
- [ ] RLS policies added/updated for any new or changed table
- [ ] Migration is safe on existing data (no destructive change without a backfill plan)
- [ ] Entity classes updated to match the schema (`ddl-auto` is `validate`, so drift fails at startup)

## Config / environment

<!-- New or changed env vars, secrets, or dependencies. -->

- [ ] New env vars added to `.env.example` (never commit real values)
- [ ] No secrets or credentials committed
- [ ] `application.yml` updated if a config property was added or changed
- [ ] New dependency added to `pom.xml` — noted below so reviewers know why

## How to test

<!-- Steps for the reviewer: setup, commands, expected result, edge cases. -->

1.

## Checklist

- [ ] Self-reviewed the diff
- [ ] Build passes locally (`./mvnw -B compile`)
- [ ] Tests pass / added or updated where relevant (`./mvnw -B test`)
- [ ] PR title follows Conventional Commits
- [ ] Updated docs / README if behaviour or setup changed

## Notes for reviewer / deploy

<!-- Deploy ordering, migration timing, feature flags, rollback plan, follow-ups, anything risky. -->
