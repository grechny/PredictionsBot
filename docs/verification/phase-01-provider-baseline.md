# Phase 1 Provider Baseline Verification

This inventory protects current behavior before the football data provider boundary changes. Remote smoke checks are test system only and must never use production systems or production credentials.

## Automated Coverage

| Workflow | Coverage | Verification |
|----------|----------|--------------|
| fixture refresh | New and existing provider fixture synchronization is covered by `CompetitionServiceFixtureRefreshTest`. | `gradle test --tests '*CompetitionServiceFixtureRefreshTest'` |
| active fixture refresh | Active fixture ID lookup, provider failures, and empty provider responses are covered by `CompetitionServiceFixtureRefreshTest`. | `gradle test --tests '*CompetitionServiceFixtureRefreshTest'` |
| result calculation | Finished and live match scoring by user is covered by `PredictionServiceResultsTest`. | `gradle test --tests '*PredictionServiceResultsTest'` |
| prediction saving | Future-match updates, started-match skip, and exactly one double-up validation are covered by `PredictionServiceSavePredictionsTest`. | `gradle test --tests '*PredictionServiceSavePredictionsTest'` |
| reminders | Today, tomorrow, recheck, no-fixture, and competition-participation reminder behavior is covered by `ReminderSchedulerTest`. | `gradle test --tests '*ReminderSchedulerTest'` |
| league workflows | League create, join, duplicate member, invalid ID, admin update guard, blank name, and league limit behavior is covered by `LeagueServiceTest`. | `gradle test --tests '*LeagueServiceTest'` |
| current results-page refresh | Existing controller/service tests cover results service calculations; page-level refresh remains an inserted-phase candidate if the provider boundary touches rendered results behavior directly. | `gradle test --tests '*PredictionServiceResultsTest'` |

## Local Smoke

Local smoke uses the bundled PostgreSQL service and API-Football mock. The mock returns fixture rounds and empty fixture lists, so it is useful for verifying the current connector path without consuming provider quota.

1. Run the automated baseline:

   ```bash
   gradle test
   ```

2. Start the local stack:

   ```bash
   docker compose -f local/compose.yaml up --build
   ```

3. Trigger the current admin refresh endpoint against the local app:

   ```text
   POST /{secrets.adminKey}/fixtures
   ```

4. Confirm the request reaches the local API-Football mock and returns without real provider credentials.

## Remote Test-System Smoke

Remote smoke must use the test system only. Do not use production hosts, production databases, production Telegram tokens, or production provider credentials.

| Check | Expected Result | Result |
|-------|-----------------|--------|
| Deploy or select current phase branch on the test system | Test system runs the current backend code and test configuration. | MCP environment check passed for the test environment on 2026-05-26. |
| Trigger `POST /{secrets.adminKey}/fixtures` on the test system | The current API-Football refresh path completes without touching production. | Scheduled test-system refresh reached `CompetitionService.refreshFixtures` on 2026-05-26T00:00:00Z, but API-Football returned HTTP 429. Prior successful test-system API-Football refresh was recorded on 2026-05-25T00:00:01Z. |
| Open an existing test-system results page after refresh | Results still render and scores remain consistent with current behavior. | Read-only test DB check: 5169 matches, 5163 with finished/started results; active Premier League 2025 has 380/380 matches with results and active Champions League 2025 has 280/281. |
| Record timestamp and operator | Smoke outcome is traceable before provider abstraction work continues. | Recorded by Codex via predictionsbot MCP on 2026-05-26. |

Remote smoke timestamp: 2026-05-26T00:00:00Z scheduled refresh; recorded on 2026-05-26.
Remote smoke outcome: quota-limited. The test system reached the current provider refresh path and did not use production, but the latest provider call failed with HTTP 429. Previous successful test-system provider refreshes are present in audit history.

## Inserted-Phase Candidates

| Candidate | Reason |
|-----------|--------|
| Results-page rendered refresh flow | Current Phase 1 changes target provider contracts. If later provider extraction changes page-level result rendering, add a focused UI/API inserted phase before frontend extraction. |
