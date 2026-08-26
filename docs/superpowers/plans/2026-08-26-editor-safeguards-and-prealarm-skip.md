# Editor Safeguards and Pre-Alarm Skip Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Protect unsaved alarm edits, keep save actions visible, and let users skip one specific regular or shift-alarm occurrence from a system notification ten minutes before it rings.

**Architecture:** Editor ViewModels expose snapshot-based dirty state and Compose routes share a guarded-back dialog plus a scaffold with a persistent bottom save action. Existing scheduler implementations additionally schedule a pre-alarm broadcast for every non-snooze occurrence; a receiver posts actionable notifications, while a SharedPreferences occurrence store lets both execution coordinators consume a one-time skip before ringing.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Hilt, AlarmManager, BroadcastReceiver, NotificationCompat, SharedPreferences, JUnit 4, Robolectric/Android Compose tests, Gradle

**Spec:** `docs/superpowers/specs/2026-08-26-unsaved-edits-sticky-save-and-prealarm-skip-design.md`

## Global Constraints

- Android only; distribution remains Google Play oriented.
- Use no server, external API, or network-backed storage.
- `오늘만 끄기` must skip only one identified occurrence and must not disable its repeating alarm.
- `유지` and no response must preserve the scheduled alarm.
- Pre-alarm notification failure must never prevent the real alarm from being scheduled or rung.
- Final deliverable is version 1.10 (`versionCode 13`) debug APK.

---

### Task 1: Occurrence identity and persistent one-time skip store

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/prealarm/AlarmOccurrenceKey.kt`
- Create: `app/src/main/java/com/smartworkalarm/prealarm/OccurrenceSkipStore.kt`
- Create: `app/src/main/java/com/smartworkalarm/prealarm/SharedPreferencesOccurrenceSkipStore.kt`
- Create: `app/src/test/java/com/smartworkalarm/prealarm/AlarmOccurrenceKeyTest.kt`
- Create: `app/src/test/java/com/smartworkalarm/prealarm/SharedPreferencesOccurrenceSkipStoreTest.kt`
- Modify: `app/src/main/java/com/smartworkalarm/di/AlarmModule.kt`

**Interfaces:**
- Produces: `data class AlarmOccurrenceKey(val kind: AlarmKind, val id: Long, val scheduledAt: Instant)` with stable `encode()` and `decode(String)`.
- Produces: `enum class AlarmKind { REGULAR, SHIFT }`.
- Produces: `interface OccurrenceSkipStore { fun mark(key: AlarmOccurrenceKey); fun consume(key: AlarmOccurrenceKey): Boolean; fun prune(now: Instant) }`.

- [ ] **Step 1: Write failing identity tests**

Test that regular and shift keys with the same numeric ID/time encode differently, round-trip exactly, and different occurrence dates remain distinct.

- [ ] **Step 2: Run the identity test and verify RED**

Run: `./gradlew testDebugUnitTest --tests "com.smartworkalarm.prealarm.AlarmOccurrenceKeyTest"`

Expected: compilation failure because `AlarmOccurrenceKey` and `AlarmKind` do not exist.

- [ ] **Step 3: Implement the minimal immutable occurrence key**

Use a versioned colon-separated representation containing kind, ID, and epoch milliseconds. Parsing returns `null` for malformed input rather than throwing.

- [ ] **Step 4: Run the identity test and verify GREEN**

Run the command from Step 2 and expect all tests to pass.

- [ ] **Step 5: Write failing store tests**

Use an in-memory Android SharedPreferences test context to verify mark/consume once, duplicate mark idempotence, process-style store recreation persistence, unrelated occurrence preservation, and pruning records older than 24 hours.

- [ ] **Step 6: Run store tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests "com.smartworkalarm.prealarm.SharedPreferencesOccurrenceSkipStoreTest"`

Expected: compilation failure because the store implementation does not exist.

- [ ] **Step 7: Implement and bind the store**

Store encoded keys in a private SharedPreferences string set, copy sets before mutation, synchronize compound read/write operations, and prune stale keys by `scheduledAt < now.minus(24, HOURS)`. Bind the interface as a singleton in `AlarmModule`.

- [ ] **Step 8: Run Task 1 tests and commit**

Run both Task 1 test classes, then commit with `feat: persist one-time alarm occurrence skips`.

### Task 2: Schedule and display the ten-minute pre-alarm notification

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/prealarm/PreAlarmContract.kt`
- Create: `app/src/main/java/com/smartworkalarm/prealarm/PreAlarmNotificationFactory.kt`
- Create: `app/src/main/java/com/smartworkalarm/prealarm/PreAlarmReceiver.kt`
- Create: `app/src/test/java/com/smartworkalarm/prealarm/PreAlarmPolicyTest.kt`
- Create: `app/src/test/java/com/smartworkalarm/prealarm/PreAlarmNotificationTextTest.kt`
- Modify: `app/src/main/java/com/smartworkalarm/alarm/AlarmSchedulerImpl.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/smartworkalarm/WorkAlarmApplication.kt`

**Interfaces:**
- Consumes: `AlarmOccurrenceKey`, `OccurrenceSkipStore` from Task 1.
- Produces: `fun preAlarmTriggerAt(now: Instant, occurrenceAt: Instant): Instant?` returning `null` after occurrence, otherwise `maxOf(now, occurrenceAt.minusSeconds(600))`.
- Produces broadcast actions `ACTION_PRE_ALARM`, `ACTION_SKIP_OCCURRENCE`, and `ACTION_KEEP_OCCURRENCE` carrying kind, ID, and scheduled occurrence epoch milliseconds.

- [ ] **Step 1: Write failing timing-policy tests**

Cover more than ten minutes remaining, exactly ten minutes, less than ten minutes (immediate), and occurrence already passed (`null`).

- [ ] **Step 2: Run policy tests and verify RED**

Run: `./gradlew testDebugUnitTest --tests "com.smartworkalarm.prealarm.PreAlarmPolicyTest"`

Expected: unresolved `preAlarmTriggerAt`.

- [ ] **Step 3: Implement the timing policy and scheduler integration**

For `schedule` and `scheduleShift`, schedule a distinct immutable PendingIntent at the policy time after successfully scheduling the real alarm. Do not create pre-alerts for `scheduleSnooze` or `scheduleShiftSnooze`. Extend regular and shift cancellation to cancel their pre-alarm PendingIntents. Keep failures isolated with `runCatching` so the real alarm result remains authoritative.

- [ ] **Step 4: Write and verify failing notification-text tests**

Assert title `알람 이름이 10분 뒤 울립니다` and body `오늘 이 알람을 끌까요?` for regular and shift names.

- [ ] **Step 5: Implement receiver, notification, and channel**

On `ACTION_PRE_ALARM`, load the current alarm or shift type by ID, reject missing/disabled records, and post a unique notification with sound, vibration, `오늘만 끄기`, and `유지` actions. On skip, mark the occurrence and cancel only that notification; on keep, only cancel it. Register the receiver as non-exported and create a high-importance `pre_alarm_choice` channel in application startup.

- [ ] **Step 6: Verify Task 2 and commit**

Run Task 2 tests plus `compileDebugKotlin`, then commit with `feat: notify before scheduled alarms`.

### Task 3: Consume one-time skips without breaking repeat scheduling

**Files:**
- Modify: `app/src/main/java/com/smartworkalarm/alarm/AlarmExecutionCoordinator.kt`
- Modify: `app/src/main/java/com/smartworkalarm/alarm/ShiftAlarmExecutionCoordinator.kt`
- Modify: `app/src/test/java/com/smartworkalarm/alarm/AlarmExecutionCoordinatorTest.kt`
- Modify: `app/src/test/java/com/smartworkalarm/alarm/ShiftAlarmExecutionCoordinatorTest.kt`

**Interfaces:**
- Consumes: `OccurrenceSkipStore.consume(AlarmOccurrenceKey)`.
- Extends: `ExecutionOutcome` with `SkippedByUser`.

- [ ] **Step 1: Write failing regular-alarm coordinator tests**

Assert that a marked non-snooze occurrence returns `SkippedByUser`, never starts the ringer, consumes the record, and still schedules the next repeat. Assert that the following occurrence rings. Assert snooze executions do not consume a normal-occurrence skip.

- [ ] **Step 2: Run regular coordinator tests and verify RED**

Run the focused `AlarmExecutionCoordinatorTest`; expect missing constructor/store or missing outcome failures.

- [ ] **Step 3: Implement regular occurrence consumption**

Preserve the existing next-repeat scheduling before checking the skip store. Check only non-snooze occurrences using the receiver-provided `scheduledOccurrenceAt`; return `SkippedByUser` before calendar evaluation and ringing.

- [ ] **Step 4: Write failing shift coordinator tests**

Mirror the regular assertions for shift keys, including continued next-repeat scheduling and isolation from a regular key with the same ID.

- [ ] **Step 5: Implement shift occurrence consumption**

Inject the store, build a SHIFT key, consume only non-snooze occurrences, and return `SkippedByUser` before calendar evaluation/ringing while retaining existing repeat scheduling.

- [ ] **Step 6: Run both coordinator suites and commit**

Run both focused test classes and commit with `feat: skip a selected alarm occurrence`.

### Task 4: Add reusable dirty-state and guarded back behavior

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/presentation/components/UnsavedChangesDialog.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/alarmedit/AlarmEditViewModel.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/shift/ShiftGroupEditViewModel.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/alarmedit/AlarmEditScreen.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/shift/ShiftGroupScreen.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/shift/ShiftTypeEditScreen.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/navigation/WorkAlarmNavHost.kt`
- Modify: `app/src/test/java/com/smartworkalarm/presentation/alarmedit/AlarmEditViewModelTest.kt`
- Modify: `app/src/test/java/com/smartworkalarm/presentation/shift/ShiftGroupEditViewModelTest.kt`
- Create: `app/src/androidTest/java/com/smartworkalarm/presentation/UnsavedChangesDialogTest.kt`

**Interfaces:**
- Produces in each relevant state: `val hasUnsavedChanges: Boolean` derived from a loaded baseline snapshot rather than manually toggled event flags.
- Produces: shared `UnsavedChangesDialog(onSave, onDiscard, onCancel)` with visible labels `저장`, `저장 안 함`, `취소`.

- [ ] **Step 1: Write failing ViewModel dirty-state tests**

For regular alarm, group, and shift type, assert pristine after load, dirty after a change, pristine after reverting to baseline, and no premature navigation when save validation fails.

- [ ] **Step 2: Run focused ViewModel tests and verify RED**

Run `AlarmEditViewModelTest` and `ShiftGroupEditViewModelTest`; expect unresolved dirty-state assertions.

- [ ] **Step 3: Implement baseline snapshots and discard hooks**

Capture normalized editable fields after load/default initialization. Exclude transient error, saved, deleted, calendar-query, and preview fields. Add a draft-discard operation that invokes the existing group draft cleanup before navigation.

- [ ] **Step 4: Write failing Compose dialog test**

Render the shared dialog and assert the three actions invoke only their matching callback. Add route-level tests that back with no changes exits directly and back with changes opens the dialog.

- [ ] **Step 5: Implement guarded back routes**

Use `BackHandler` in all three editor routes. Save delegates to existing validation; success continues through existing saved-state navigation. Discard invokes cleanup where required and then `onDone`; cancel only closes the dialog. Pass an explicit `onBack` callback from navigation so toolbar/system handling share the same path.

- [ ] **Step 6: Run Task 4 tests and commit**

Run focused unit tests and `compileDebugAndroidTestKotlin`, then commit with `feat: protect unsaved alarm edits`.

### Task 5: Keep save actions fixed at the bottom of every editor

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/presentation/components/PersistentSaveScaffold.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/alarmedit/AlarmEditScreen.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/shift/ShiftGroupScreen.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/shift/ShiftTypeEditScreen.kt`
- Create: `app/src/androidTest/java/com/smartworkalarm/presentation/PersistentSaveScaffoldTest.kt`

**Interfaces:**
- Produces: `PersistentSaveScaffold(saveLabel: String, onSave: () -> Unit, content: @Composable ColumnScope.() -> Unit)` using a bottom action surface and independently scrollable content.

- [ ] **Step 1: Write failing Compose layout tests**

Render oversized editor content, scroll to top and bottom, and assert the tagged save action remains displayed and clickable in both positions. Verify safe-drawing bottom padding is applied by the shared scaffold.

- [ ] **Step 2: Run the Compose test compile and verify RED**

Run `./gradlew compileDebugAndroidTestKotlin`; expect unresolved `PersistentSaveScaffold`.

- [ ] **Step 3: Implement the shared scaffold**

Use Material 3 `Scaffold(bottomBar = ...)`, a tonal/elevated bottom surface, full-width primary button, navigation-bar insets, and content padding forwarded to a vertical scroll container.

- [ ] **Step 4: Migrate all three editor screens**

Remove their inline save buttons, preserve delete actions in scrollable content, provide bottom spacing through scaffold padding, and retain existing validation scroll/focus behavior.

- [ ] **Step 5: Compile UI tests and commit**

Run `compileDebugAndroidTestKotlin` and commit with `feat: pin editor save actions to bottom`.

### Task 6: Rescheduling coverage, version, and final APK

**Files:**
- Modify: `app/src/test/java/com/smartworkalarm/worker/RescheduleAlarmsUseCaseTest.kt`
- Modify: `app/src/test/java/com/smartworkalarm/domain/usecase/AlarmCrudUseCasesTest.kt`
- Modify: `app/build.gradle.kts`
- Create: `docs/testing/2026-08-26-v1.10-prealarm-checklist.md`
- Create: `artifacts/WorkAlarm-v1.10-debug.apk`

**Interfaces:**
- Consumes the unchanged `AlarmScheduler` and `ShiftAlarmScheduler` contracts; their Android implementation now owns paired pre-alarm scheduling.

- [ ] **Step 1: Add reschedule regression tests**

Assert save, enable, system reschedule, and shift reconciliation still invoke the existing scheduler contracts exactly once per next occurrence; scheduler implementation tests cover paired pre-alert creation/cancellation.

- [ ] **Step 2: Run focused regression tests**

Run `RescheduleAlarmsUseCaseTest`, `AlarmCrudUseCasesTest`, and `ReconcileShiftAlarmsUseCaseTest`; fix only regressions caused by this feature.

- [ ] **Step 3: Bump the app version**

Set `versionCode = 13` and `versionName = "1.10"` in `app/build.gradle.kts`.

- [ ] **Step 4: Run the complete verification once**

Run: `./gradlew lintDebug testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug --rerun-tasks`

Expected: BUILD SUCCESSFUL, zero unit-test failures, lint success, Android test source compilation success, and a generated debug APK.

- [ ] **Step 5: Package and document the APK**

Copy `app/build/outputs/apk/debug/app-debug.apk` to `artifacts/WorkAlarm-v1.10-debug.apk`, record byte size and SHA-256, and add Galaxy S22 Ultra checks for dirty-back choices, fixed save buttons, pre-alert sound/vibration, keep, one-time skip, subsequent repeat, same-time multiple alarms, and reboot recovery.

- [ ] **Step 6: Commit the release artifact and checklist**

Commit source/version/checklist changes with `build: prepare v1.10 alarm skip APK`; include the APK only if repository artifact policy continues to track generated APKs.
