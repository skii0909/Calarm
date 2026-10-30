# Outlook Direct Login Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Sign in to Microsoft from Calarm and use selected Outlook calendars in the existing alarm rules.

**Architecture:** MSAL owns authentication. A Graph client fetches complete calendar views into a separate Room snapshot; the alarm event source reads only local state. Google selections and existing alarm storage retain their current IDs and data.

**Tech Stack:** Kotlin, Compose, Hilt, Room, WorkManager, MSAL Android 8.5.0, HTTPS Microsoft Graph v1.0.

**Spec:** `docs/superpowers/specs/2026-09-19-outlook-direct-login-design.md`

## Global Constraints

- One Microsoft account; personal and organizational accounts; no client secret.
- Delegated `Calendars.ReadBasic`; no calendar writes; no shared/group calendars.
- Android Calendar permission is Google-only and is not required for Outlook.
- Snapshot window: yesterday through the next 31 days; validity: 6 hours, no future timestamps.
- Alarm delivery must never wait for Microsoft network I/O.
- Preserve the pre-existing uncommitted 1.10.4 changes and Google selection preferences.
- Client ID is externally supplied. Missing configuration is an explicit unavailable state, never a simulated login.

## Review Focus

1. Logout/selection change during slow sync must invalidate the response (Task 2).
2. Partial pagination or HTTP redirects must not publish incomplete data or leak bearer tokens (Task 3).
3. All-day events must keep their original dates across UTC offsets and DST (Task 3).
4. Outlook-only users must not be blocked by Google permission or account absence (Task 5).
5. Expired, revoked or out-of-window data must take the existing calendar-error alarm path (Tasks 1, 2, 5).

## Task 1: Local Outlook model and alarm snapshot policy

**Files:** Create `outlook/OutlookModels.kt`, `outlook/OutlookSnapshotPolicy.kt` under `app/src/main/java/com/smartworkalarm/`; create matching `OutlookSnapshotPolicyTest.kt` under unit tests.

**Interfaces:** `OutlookCalendarKey(accountId: String, calendarId: String)`; `OutlookSnapshotPolicy.events(state: OutlookState, start: Instant, end: Instant, now: Instant, zone: ZoneId): Result<List<CalendarEvent>>`.

- [x] Write tests with fixed timestamps for fresh/expired/missing/revoked snapshots, selected calendar isolation, coverage, clock rollback and all-day local dates.
```kotlin
assertTrue(OutlookSnapshotPolicy.events(state, start, end, synced.plusSeconds(21601), zone).isFailure)
```
- [x] Run `gradlew.bat testDebugUnitTest --tests *OutlookSnapshotPolicyTest`; observe missing implementation.
- [x] Implement immutable models and policy. Return failure instead of an empty list when required data is unavailable.
- [x] Re-run the policy tests; expected: all pass.

## Task 2: Durable store and sync lifecycle

**Files:** Create `outlook/OutlookStore.kt`, `outlook/OutlookDatabase.kt`, `outlook/OutlookSyncCoordinator.kt`, `outlook/OutlookWork.kt`; test `OutlookSyncCoordinatorTest.kt`, `OutlookStoreTest.kt`.

**Interfaces:** `OutlookStore.state: StateFlow<OutlookState>`; suspending `update(transform)` serialized and durably persisted before publication. `OutlookSyncCoordinator.sync(): OutlookSyncResult`; `disconnect()` invalidates session before asynchronous cleanup.

- [x] Test a slow fetch whose result arrives after logout or selection change, failed pages preserving old snapshot, first connection selecting only the default calendar, retry deferral, and store round-trip.
```kotlin
assertNull(store.state.value.account)
assertTrue(store.state.value.events.isEmpty())
```
- [x] Run `gradlew.bat testDebugUnitTest --tests *OutlookSyncCoordinatorTest --tests *OutlookStoreTest`; expected: fail for missing implementation.
- [x] Implement separate Room storage, generation checks, complete snapshot replacement and a connected-network periodic worker. Store metadata/JSON atomically in one singleton record; the original alarm DB is untouched. Workers retry only transient failures with bounded attempts and server retry time.
- [x] Run the two tests; expected: pass including process-recreation persistence.

## Task 3: Graph transport and date conversion

**Files:** Create `outlook/OutlookGraphClient.kt`, `outlook/GraphEventParser.kt`; test `OutlookGraphClientTest.kt` and `GraphEventParserTest.kt`.

**Interfaces:** `OutlookGraphGateway.calendars(token): List<OutlookCalendar>` and `events(token, calendarId, start, end): List<OutlookEvent>` are suspending. `GraphHttpTransport.get(url, token): GraphResponse` isolates only external I/O.

- [x] Write real parser/pagination tests using hand-written JSON pages; include hostile nextLink, partial failure, 401, 403, 429, cancelled instances, Korea/Los Angeles all-day boundaries, invalid timezone and precise half-open overlap.
```kotlin
assertEquals(LocalDate.parse("2026-09-19"), parsed.allDayStart)
```
- [x] Run `gradlew.bat testDebugUnitTest --tests *GraphEventParserTest --tests *OutlookGraphClientTest`; expected: fail.
- [x] Implement fixed-host HTTPS with redirects disabled, bounded requests, all-page retrieval and typed failures. Request UTC timed values; recover all-day calendar dates in their original timezone (IANA or ICU Windows mapping). Never log tokens or response bodies.
- [x] Re-run the tests; expected: pass.

## Task 4: MSAL authentication and build configuration

**Files:** Create `outlook/OutlookAuth.kt`, `outlook/MsalOutlookAuth.kt`, `outlook/OutlookController.kt`, `di/OutlookModule.kt`, backup-rule XML; modify Gradle dependencies and `AndroidManifest.xml`.

**Interfaces:** `OutlookAuth.signIn(Activity): OutlookAccount?`, `account(): OutlookAccount?`, `token(forceRefresh: Boolean): String`, `signOut()`; null sign-in means cancellation. Controller coordinates authentication, store sessions, sync and local rescheduling.

- [x] Test missing/invalid configuration, account mismatch, cancelled sign-in preserving old state, interactive-required failure invalidating cached eligibility, and logout callbacks racing with another operation.
```kotlin
assertFalse(configuration.isConfigured)
```
- [x] Run the targeted auth/controller tests; expected: fail.
- [x] Pin MSAL 8.5.0; accept `outlookClientId` and `outlookSignatureHash` Gradle properties, generate MSAL config at runtime in no-backup storage, configure matching manifest redirect path. MSAL handles tokens and its account cache. Exclude Outlook snapshot/auth storage from cloud backup and device transfer.
- [x] Re-run targeted tests and `compileDebugKotlin`; expected: pass without client ID and an explicit setup-required UI state.

## Task 5: Google + Outlook integration and Compose connection UI

**Files:** Create `data/calendar/CombinedCalendarEventSource.kt`, `outlook/OutlookViewModel.kt`, `presentation/settings/OutlookConnectionCard.kt`; modify connection model, DI, settings route/screen, onboarding, permission prompts, MainActivity and application worker initialization.

**Interfaces:** Existing `CalendarEventSource` remains unchanged. Google `Long` IDs remain in existing preferences; Outlook keys are separate typed account/string IDs. The combined connection state contains `outlook` alongside existing Google fields.

- [x] Add tests for Outlook-only without Calendar permission, Google-only unchanged behavior, both providers selected, provider failure versus unselected provider, expired cloud snapshot, independent selections, permission prompts and summary.
```kotlin
assertTrue(CalendarConnectionState(false, emptyList(), outlook = ready).isConnected)
```
- [x] Run the targeted combined-source/connection/permission tests; expected: fail.
- [x] Implement combined local event source and state, Outlook settings card with login/select/refresh/reconnect/disconnect, and onboarding entry into settings. Reconcile alarms after successful sync, connection/selection changes and logout. Filter Calendar out of the automatic alarm permission prompt; Google connection still provides an explicit grant action.
- [x] Run all unit tests and compile Android UI tests; expected: pass.

## Task 6: Full verification and delivery

**Files:** Update registration guide and create `docs/testing/2026-09-19-outlook-integration-results.md`.

- [x] Run `gradlew.bat lintDebug testDebugUnitTest assembleDebug assembleDebugAndroidTest`; inspect test counts and lint findings.
- [x] Review the final change with a fresh reviewer, especially Review Focus conditions. Correct important defects with failing regression tests and rerun affected verification.
- [x] Package APK as `artifacts/Calarm-v1.11.0-outlook-setup-required-debug.apk` when client ID remains absent. Document exact client ID build command, signature setup and remaining live Microsoft login checks. Do not describe this APK as having a verified live connection.
- [x] Preserve original changes and provide a concise result with APK, validation, and the external registration dependency.
