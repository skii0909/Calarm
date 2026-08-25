# Dynamic Alarm Screen, Optional Snooze, and Compatibility Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a lock-screen-first animated alarm screen, per-alarm optional snooze, and local Android alarm diagnostics without adding a server or paid API.

**Architecture:** Extend both regular and shift alarm models with a persisted snooze-enabled flag, carry it through the existing alarm delivery path, and keep the ringing service authoritative for dismiss/snooze actions. Split gesture policy from Compose rendering for unit tests, and add a local diagnostic source that combines Android permission state, battery-optimization state, and a locally recorded schedule audit for the settings screen.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Room 5→6 migration, AlarmManager, foreground service/full-screen notification, Hilt, JUnit4, AndroidX Compose UI tests, Room migration tests.

**Spec:** `docs/superpowers/specs/2026-08-25-dynamic-alarm-screen-snooze-compatibility-design.md`

## Global Constraints

- Keep `minSdk 26`, `compileSdk 36`, and `targetSdk 36`.
- Keep snooze minutes within the inclusive range 5–30.
- Existing and newly created alarms default `snoozeEnabled` to `true`.
- Apply identical snooze rules to regular alarms and shift-type alarms.
- Use public Android APIs and public settings intents; do not hardcode manufacturer-private components.
- Do not add a runtime server, external API, paid service, graphics engine, or manufacturer SDK.
- Preserve existing vacation/calendar skip, public-holiday skip, shift-group matching, and 22:00 change notification behavior.
- Do not claim universal device compatibility; verify the release AAB with Play Device Catalog and pre-launch reporting.

---

## File Structure

- `data/local/*Entity.kt`, `*Mapper.kt`, `WorkAlarmDatabase.kt`, `di/DatabaseModule.kt`: persist and migrate `snooze_enabled`.
- `domain/model/Alarm.kt`, `ShiftTypeAlarm.kt`: canonical snooze-enabled property.
- `presentation/alarmedit/*`, `presentation/shift/*`: edit, restore, save, and summarize optional snooze.
- `alarm/AlarmRinger*.kt`, `AlarmNotificationFactory.kt`, `AlarmRingingService.kt`, `AlarmActivity.kt`: deliver and enforce snooze state.
- `alarm/AlarmGesturePolicy.kt`: pure title-size, drag-threshold, and hold-duration policy.
- `alarm/AlarmRingingScreen.kt`: focused Compose UI for title, hold-to-snooze, and drag-to-dismiss.
- `alarm/AlarmScheduleAuditStore.kt`: device-local record of the most recent scheduled/cancelled alarm operation.
- `permissions/AlarmDiagnostics.kt`: pure diagnostic status model plus Android-backed diagnostic source.
- `presentation/settings/*`: diagnostic card and public settings actions.

---

### Task 1: Persist Optional Snooze for Regular and Shift Alarms

**Files:**
- Modify: `app/src/main/java/com/smartworkalarm/domain/model/Alarm.kt`
- Modify: `app/src/main/java/com/smartworkalarm/domain/model/ShiftTypeAlarm.kt`
- Modify: `app/src/main/java/com/smartworkalarm/data/local/AlarmEntity.kt`
- Modify: `app/src/main/java/com/smartworkalarm/data/local/ShiftTypeAlarmEntity.kt`
- Modify: `app/src/main/java/com/smartworkalarm/data/local/AlarmEntityMapper.kt`
- Modify: `app/src/main/java/com/smartworkalarm/data/local/ShiftAlarmMapper.kt`
- Modify: `app/src/main/java/com/smartworkalarm/data/local/WorkAlarmDatabase.kt`
- Modify: `app/src/main/java/com/smartworkalarm/di/DatabaseModule.kt`
- Modify: all production/test `Alarm(...)` and `ShiftTypeAlarm(...)` construction sites reported by the compiler
- Test: `app/src/test/java/com/smartworkalarm/data/local/AlarmEntityMapperTest.kt`
- Test: `app/src/test/java/com/smartworkalarm/data/local/ShiftAlarmMapperTest.kt`
- Test: `app/src/androidTest/java/com/smartworkalarm/data/local/AlarmMigrationTest.kt`
- Generate: `app/schemas/com.smartworkalarm.data.local.WorkAlarmDatabase/6.json`

**Interfaces:**
- Produces: `Alarm.snoozeEnabled: Boolean`, `ShiftTypeAlarm.snoozeEnabled: Boolean`, and `MIGRATION_5_6: Migration`.
- Produces: Room column `snooze_enabled INTEGER NOT NULL DEFAULT 1` in `alarms` and `shift_type_alarms`.

- [ ] **Step 1: Add failing mapper tests**

```kotlin
@Test fun `alarm mapper preserves disabled snooze`() {
    val alarm = sampleAlarm().copy(snoozeEnabled = false, snoozeMinutes = 17)
    assertFalse(alarm.toEntity().toDomain().snoozeEnabled)
    assertEquals(17, alarm.toEntity().toDomain().snoozeMinutes)
}

@Test fun `shift mapper preserves disabled snooze`() {
    val shift = sampleShiftType().copy(snoozeEnabled = false, snoozeMinutes = 23)
    assertFalse(shift.toEntity().toDomain().snoozeEnabled)
    assertEquals(23, shift.toEntity().toDomain().snoozeMinutes)
}
```

- [ ] **Step 2: Run mapper tests and verify they fail because `snoozeEnabled` does not exist**

Run: `./gradlew.bat testDebugUnitTest --tests "*AlarmEntityMapperTest" --tests "*ShiftAlarmMapperTest"`

Expected: compilation failure referencing unresolved `snoozeEnabled`.

- [ ] **Step 3: Add the model, entity, and mapper fields**

```kotlin
// Alarm and AlarmEntity
val snoozeEnabled: Boolean = true,
val snoozeMinutes: Int = 5,

// ShiftTypeAlarm and ShiftTypeAlarmEntity
val snoozeEnabled: Boolean = true,
val snoozeMinutes: Int,

// Entity annotations
@ColumnInfo(name = "snooze_enabled", defaultValue = "1")
val snoozeEnabled: Boolean = true,
```

Pass `snoozeEnabled = snoozeEnabled` in both directions in `AlarmEntityMapper.kt` and `ShiftAlarmMapper.kt`. Update every constructor explicitly so compiler errors cannot silently select an incorrect positional argument.

- [ ] **Step 4: Add a failing 5→6 migration test**

```kotlin
@Test fun migrate5To6_defaultsBothSnoozeFlagsToEnabled() {
    helper.createDatabase(TEST_DB, 5).apply {
        execSQL("INSERT INTO alarms (id,name,hour,minute,repeat_days_mask,enabled,skip_on_calendar_event,calendar_keywords,ringtone_uri,skip_on_public_holiday,gradual_volume,snooze_minutes) VALUES (1,'아침',7,0,31,1,1,'휴가',NULL,0,0,9)")
        execSQL("INSERT INTO shift_alarm_groups (id,name,enabled,emergency_shift_type_id,is_draft) VALUES (1,'교대',1,NULL,0)")
        execSQL("INSERT INTO shift_type_alarms (id,group_id,name,exact_titles,hour,minute,repeat_days_mask,enabled,ringtone_uri,gradual_volume,snooze_minutes,skip_on_public_holiday) VALUES (1,1,'야간','야간',20,0,127,1,NULL,0,11,0)")
        close()
    }
    helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6).use { db ->
        db.query("SELECT snooze_enabled FROM alarms WHERE id=1").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
        db.query("SELECT snooze_enabled FROM shift_type_alarms WHERE id=1").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
    }
}
```

- [ ] **Step 5: Implement and register the non-destructive migration**

```kotlin
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE alarms ADD COLUMN snooze_enabled INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE shift_type_alarms ADD COLUMN snooze_enabled INTEGER NOT NULL DEFAULT 1")
    }
}
```

Set `WorkAlarmDatabase.version = 6` and append `MIGRATION_5_6` to `addMigrations(...)`.

- [ ] **Step 6: Run unit tests, migration test, and schema generation**

Run: `./gradlew.bat testDebugUnitTest --tests "*AlarmEntityMapperTest" --tests "*ShiftAlarmMapperTest"`

Expected: PASS.

Run with an emulator/device: `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.smartworkalarm.data.local.AlarmMigrationTest`

Expected: PASS and schema `6.json` generated/validated.

- [ ] **Step 7: Commit the persistence slice**

```powershell
git add app/src/main/java/com/smartworkalarm/domain app/src/main/java/com/smartworkalarm/data/local app/src/main/java/com/smartworkalarm/di/DatabaseModule.kt app/src/test app/src/androidTest app/schemas
git commit -m "feat: persist optional snooze settings"
```

---

### Task 2: Edit and Summarize Optional Snooze in Both Alarm Types

**Files:**
- Modify: `app/src/main/java/com/smartworkalarm/presentation/alarmedit/AlarmEditViewModel.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/alarmedit/AlarmEditScreen.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/alarmlist/AlarmListScreen.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/shift/ShiftGroupEditViewModel.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/shift/ShiftTypeEditScreen.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/shift/ShiftGroupScreen.kt`
- Test: `app/src/test/java/com/smartworkalarm/presentation/alarmedit/AlarmEditViewModelTest.kt`
- Test: `app/src/test/java/com/smartworkalarm/presentation/shift/ShiftGroupEditViewModelTest.kt`
- Test: `app/src/androidTest/java/com/smartworkalarm/presentation/OptionalSnoozeUiTest.kt`

**Interfaces:**
- Consumes: model properties from Task 1.
- Produces: `AlarmEditState.snoozeEnabled`, `ShiftTypeEditState.snoozeEnabled`, `onSnoozeEnabledChanged(Boolean)`, and `onShiftSnoozeEnabledChanged(Boolean)`.

- [ ] **Step 1: Add failing ViewModel tests for default, restore, and preservation**

```kotlin
@Test fun `new alarm defaults snooze to enabled`() = runTest {
    assertTrue(viewModel.state.value.snoozeEnabled)
}

@Test fun `disabling snooze preserves selected minutes on save`() = runTest {
    viewModel.onSnoozeMinutesChanged(19)
    viewModel.onSnoozeEnabledChanged(false)
    viewModel.save()
    assertFalse(savedAlarm.snoozeEnabled)
    assertEquals(19, savedAlarm.snoozeMinutes)
}

@Test fun `shift type restores disabled snooze and minutes`() = runTest {
    viewModel.editShiftType(sampleShiftType().copy(snoozeEnabled = false, snoozeMinutes = 21))
    assertFalse(viewModel.shiftTypeState.value.snoozeEnabled)
    assertEquals(21, viewModel.shiftTypeState.value.snoozeMinutes)
}
```

- [ ] **Step 2: Run the ViewModel tests and verify unresolved state/action failures**

Run: `./gradlew.bat testDebugUnitTest --tests "*AlarmEditViewModelTest" --tests "*ShiftGroupEditViewModelTest"`

Expected: FAIL before the state/action fields are implemented.

- [ ] **Step 3: Implement state, actions, save, and restore paths**

```kotlin
data class AlarmEditState(
    // existing fields
    val snoozeEnabled: Boolean = true,
    val snoozeMinutes: Int = 5,
)

fun onSnoozeEnabledChanged(enabled: Boolean) =
    _state.update { it.copy(snoozeEnabled = enabled) }
```

Repeat the same shape for `ShiftTypeEditState`. Keep the minutes unchanged when the Boolean changes, and include both values in every `Alarm`/`ShiftTypeAlarm` produced by the ViewModels.

- [ ] **Step 4: Add a failing Compose UI test for conditional visibility**

```kotlin
@Test fun snoozeControlsOnlyAppearWhenEnabled() {
    compose.setContent { AlarmEditScreenForTest(snoozeEnabled = false, snoozeMinutes = 17) }
    compose.onNodeWithText("17분").assertDoesNotExist()
    compose.onNodeWithText("다시 울림 사용").assertIsDisplayed()
}
```

Add equivalent assertions for the shift editor and for list pills: disabled alarms must not expose `17분 후`.

- [ ] **Step 5: Implement compact switch-and-detail layouts**

```kotlin
Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
        Text("다시 울림 사용", style = MaterialTheme.typography.titleMedium)
        Text("길게 눌러 설정한 시간 뒤 다시 울립니다.")
    }
    Switch(checked = state.snoozeEnabled, onCheckedChange = viewModel::onSnoozeEnabledChanged)
}
if (state.snoozeEnabled) {
    SnoozeMinutesSelector(value = state.snoozeMinutes, onValueChange = viewModel::onSnoozeMinutesChanged)
}
```

Render `FeaturePill("${alarm.snoozeMinutes}분 후")` and its shift equivalent only when `snoozeEnabled` is true.

- [ ] **Step 6: Run ViewModel and Compose tests**

Run: `./gradlew.bat testDebugUnitTest --tests "*AlarmEditViewModelTest" --tests "*ShiftGroupEditViewModelTest"`

Expected: PASS.

Run with emulator/device: `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.smartworkalarm.presentation.OptionalSnoozeUiTest`

Expected: PASS.

- [ ] **Step 7: Commit the editor slice**

```powershell
git add app/src/main/java/com/smartworkalarm/presentation app/src/test app/src/androidTest
git commit -m "feat: make snooze optional per alarm"
```

---

### Task 3: Carry and Enforce Snooze State Through the Ringing Pipeline

**Files:**
- Modify: `app/src/main/java/com/smartworkalarm/alarm/AlarmRinger.kt`
- Modify: `app/src/main/java/com/smartworkalarm/alarm/AlarmRingerImpl.kt`
- Modify: `app/src/main/java/com/smartworkalarm/alarm/AlarmExecutionCoordinator.kt`
- Modify: `app/src/main/java/com/smartworkalarm/alarm/ShiftAlarmExecutionCoordinator.kt`
- Modify: `app/src/main/java/com/smartworkalarm/alarm/AlarmNotificationFactory.kt`
- Modify: `app/src/main/java/com/smartworkalarm/alarm/AlarmRingingService.kt`
- Modify: `app/src/main/java/com/smartworkalarm/worker/RescheduleAlarmsUseCase.kt`
- Test: `app/src/test/java/com/smartworkalarm/alarm/AlarmExecutionCoordinatorTest.kt`
- Test: `app/src/test/java/com/smartworkalarm/alarm/ShiftAlarmExecutionCoordinatorTest.kt`
- Create: `app/src/test/java/com/smartworkalarm/alarm/SnoozeRequestPolicyTest.kt`

**Interfaces:**
- Consumes: `snoozeEnabled` from Tasks 1–2.
- Produces: `AlarmRinger.start(..., snoozeEnabled: Boolean, snoozeMinutes: Int)` and `EXTRA_SNOOZE_ENABLED`.
- Produces: pure `canScheduleSnooze(snoozeEnabled: Boolean, requestedAction: String): Boolean` used by the service.

- [ ] **Step 1: Add failing pipeline and policy tests**

```kotlin
@Test fun `coordinator carries disabled snooze to ringer`() = runTest {
    coordinator.execute(sampleAlarm().copy(snoozeEnabled = false))
    assertFalse(fakeRinger.started.single().snoozeEnabled)
}

@Test fun `disabled snooze rejects snooze action`() {
    assertFalse(canScheduleSnooze(false, AlarmRingingService.ACTION_SNOOZE))
    assertTrue(canScheduleSnooze(true, AlarmRingingService.ACTION_SNOOZE))
}
```

- [ ] **Step 2: Run the tests and verify signature/policy failures**

Run: `./gradlew.bat testDebugUnitTest --tests "*AlarmExecutionCoordinatorTest" --tests "*ShiftAlarmExecutionCoordinatorTest" --tests "*SnoozeRequestPolicyTest"`

Expected: FAIL because the ringer payload lacks the Boolean and policy function.

- [ ] **Step 3: Extend ringer and notification payloads**

```kotlin
fun start(
    alarmId: Long,
    alarmName: String,
    ringtoneUri: String?,
    gradualVolume: Boolean,
    snoozeEnabled: Boolean,
    snoozeMinutes: Int,
)
```

Put `EXTRA_SNOOZE_ENABLED` into the service Intent, full-screen Activity Intent, and content/full-screen PendingIntent. Update regular, shift, and reschedule call sites.

- [ ] **Step 4: Make the service authoritative and failure-safe**

```kotlin
internal fun canScheduleSnooze(snoozeEnabled: Boolean, action: String?): Boolean =
    snoozeEnabled && action == AlarmRingingService.ACTION_SNOOZE

if (canScheduleSnooze(snoozeEnabled, intent.action)) {
    runCatching { scheduleSnooze(clock.instant().plus(Duration.ofMinutes(snoozeMinutes.toLong()))) }
        .onSuccess { stopRingingAndSelf() }
        .onFailure { publishActionFailure("다시 울림을 예약하지 못했습니다. 다시 시도하거나 알람을 해제해 주세요.") }
}
```

Do not stop audio, vibration, or the foreground service until scheduling succeeds. Ignore snooze actions when the current service state says disabled.

- [ ] **Step 5: Run pipeline tests and existing alarm coordinator regressions**

Run: `./gradlew.bat testDebugUnitTest --tests "*AlarmExecutionCoordinatorTest" --tests "*ShiftAlarmExecutionCoordinatorTest" --tests "*SnoozeRequestPolicyTest" --tests "*RescheduleAlarmsUseCaseTest"`

Expected: PASS.

- [ ] **Step 6: Commit the ringing pipeline slice**

```powershell
git add app/src/main/java/com/smartworkalarm/alarm app/src/main/java/com/smartworkalarm/worker app/src/test
git commit -m "feat: enforce snooze state while ringing"
```

---

### Task 4: Build the Three-Zone Animated Ringing Screen

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/alarm/AlarmGesturePolicy.kt`
- Create: `app/src/main/java/com/smartworkalarm/alarm/AlarmRingingScreen.kt`
- Modify: `app/src/main/java/com/smartworkalarm/alarm/AlarmActivity.kt`
- Modify: `app/src/test/java/com/smartworkalarm/alarm/AlarmPresentationStateTest.kt`
- Create: `app/src/test/java/com/smartworkalarm/alarm/AlarmGesturePolicyTest.kt`
- Modify: `app/src/androidTest/java/com/smartworkalarm/alarm/AlarmRingingScreenTest.kt`

**Interfaces:**
- Consumes: Activity payload including `snoozeEnabled` and `snoozeMinutes` from Task 3.
- Produces: `alarmTitleFontSize(titleLength: Int): TextUnit`, `dismissProgress(distancePx: Float, thresholdPx: Float): Float`, `shouldDismiss(distancePx: Float, thresholdPx: Float): Boolean`, and `SNOOZE_HOLD_MILLIS = 3_000L`.
- Produces: `AlarmRingingScreen(presentation, onDismiss, onSnooze)`.

- [ ] **Step 1: Write failing pure gesture/title policy tests**

```kotlin
@Test fun `dismiss is direction independent`() {
    val threshold = 100f
    listOf(Offset(101f, 0f), Offset(-101f, 0f), Offset(0f, 101f), Offset(0f, -101f), Offset(72f, 72f))
        .forEach { assertTrue(shouldDismiss(it.getDistance(), threshold)) }
}

@Test fun `short drag does not dismiss`() = assertFalse(shouldDismiss(99f, 100f))

@Test fun `title size steps down by length`() {
    assertEquals(48.sp, alarmTitleFontSize(8))
    assertEquals(40.sp, alarmTitleFontSize(18))
    assertEquals(34.sp, alarmTitleFontSize(40))
}
```

- [ ] **Step 2: Run policy tests and verify missing symbols**

Run: `./gradlew.bat testDebugUnitTest --tests "*AlarmGesturePolicyTest"`

Expected: FAIL because the policy file does not exist.

- [ ] **Step 3: Implement pure policy functions**

```kotlin
internal const val SNOOZE_HOLD_MILLIS = 3_000L
internal fun shouldDismiss(distancePx: Float, thresholdPx: Float) = distancePx >= thresholdPx
internal fun dismissProgress(distancePx: Float, thresholdPx: Float) =
    (distancePx / thresholdPx).coerceIn(0f, 1f)
internal fun alarmTitleFontSize(titleLength: Int) = when {
    titleLength <= 10 -> 48.sp
    titleLength <= 24 -> 40.sp
    else -> 34.sp
}
```

- [ ] **Step 4: Add failing Compose behavior tests**

```kotlin
@Test fun disabledSnoozeHidesHoldArea() {
    setRingingContent(snoozeEnabled = false)
    compose.onNodeWithText("3초간 길게 누르기").assertDoesNotExist()
}

@Test fun dragPastThresholdDismisses() {
    setRingingContent(snoozeEnabled = true)
    compose.onNodeWithTag("dismiss_handle").performTouchInput { swipe(center, center + Offset(250f, 0f), 500) }
    assertEquals(1, dismissCount)
}
```

Also test a below-threshold drag, 2.9-second hold cancellation, 3-second hold completion, title max two lines semantics, and TalkBack click dismissal.

- [ ] **Step 5: Implement the three-zone screen as a focused composable**

```kotlin
@Composable
internal fun AlarmRingingScreen(
    presentation: AlarmPresentation,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit,
) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        AlarmIdentityZone(presentation, Modifier.weight(1f))
        if (presentation.snoozeEnabled) HoldToSnoozeZone(presentation.snoozeMinutes, onSnooze, Modifier.weight(1f))
        DragToDismissZone(onDismiss, Modifier.weight(if (presentation.snoozeEnabled) 1.15f else 1.8f))
    }
}
```

Use `rememberInfiniteTransition` for 2–3 expanding/fading rings, `Animatable<Offset>` for handle drag/snap-back, `awaitEachGesture` for single-pointer drag, Euclidean distance for the direction-independent threshold, and `LocalHapticFeedback` only on threshold crossing/completion. Respect `Settings.Global.ANIMATOR_DURATION_SCALE == 0f` by reducing decorative ring movement while keeping progress feedback.

- [ ] **Step 6: Wire Activity actions and idempotence**

Add `snoozeEnabled` to `AlarmPresentation` and parsing. Replace the old buttons/content in `AlarmActivity` with `AlarmRingingScreen`. Protect each callback with an atomic/remembered terminal-action flag so dismiss or snooze sends only one service Intent. Back presses must not finish the ringing Activity.

- [ ] **Step 7: Run screen unit/instrumentation tests**

Run: `./gradlew.bat testDebugUnitTest --tests "*AlarmGesturePolicyTest" --tests "*AlarmPresentationStateTest"`

Expected: PASS.

Run with emulator/device: `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.smartworkalarm.alarm.AlarmRingingScreenTest`

Expected: PASS.

- [ ] **Step 8: Commit the ringing screen slice**

```powershell
git add app/src/main/java/com/smartworkalarm/alarm app/src/test/java/com/smartworkalarm/alarm app/src/androidTest/java/com/smartworkalarm/alarm
git commit -m "feat: add animated drag-to-dismiss alarm screen"
```

---

### Task 5: Record Local Alarm Scheduling Health

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/alarm/AlarmScheduleAuditStore.kt`
- Modify: `app/src/main/java/com/smartworkalarm/alarm/AlarmSchedulerImpl.kt`
- Modify: `app/src/main/java/com/smartworkalarm/preview/NextDayPreviewScheduler.kt` only if shared storage keys could collide; preview schedules must not count as ringing alarms
- Modify: `app/src/main/java/com/smartworkalarm/di/AlarmModule.kt`
- Create: `app/src/test/java/com/smartworkalarm/alarm/AlarmScheduleAuditStoreTest.kt`
- Modify: `app/src/test/java/com/smartworkalarm/worker/RescheduleAlarmsUseCaseTest.kt`

**Interfaces:**
- Produces: `AlarmScheduleAudit(lastSuccessfulScheduleAt: Instant?, nextTriggerAt: Instant?, activeAlarmCount: Int)`.
- Produces: `AlarmScheduleAuditStore.recordScheduled(key: String, triggerAt: Instant)`, `recordCancelled(key: String)`, and `read(): AlarmScheduleAudit`.

- [ ] **Step 1: Write failing audit aggregation tests**

```kotlin
@Test fun `recording and cancelling exposes next active trigger`() {
    store.recordScheduled("regular:1", instant("2026-08-26T22:00:00Z"))
    store.recordScheduled("shift:2", instant("2026-08-26T21:00:00Z"))
    assertEquals(2, store.read().activeAlarmCount)
    assertEquals(instant("2026-08-26T21:00:00Z"), store.read().nextTriggerAt)
    store.recordCancelled("shift:2")
    assertEquals(instant("2026-08-26T22:00:00Z"), store.read().nextTriggerAt)
}
```

- [ ] **Step 2: Run the audit test and verify missing store types**

Run: `./gradlew.bat testDebugUnitTest --tests "*AlarmScheduleAuditStoreTest"`

Expected: FAIL because the audit store does not exist.

- [ ] **Step 3: Implement a SharedPreferences-backed audit store**

```kotlin
data class AlarmScheduleAudit(
    val lastSuccessfulScheduleAt: Instant?,
    val nextTriggerAt: Instant?,
    val activeAlarmCount: Int,
)

interface AlarmScheduleAuditStore {
    fun recordScheduled(key: String, triggerAt: Instant)
    fun recordCancelled(key: String)
    fun read(): AlarmScheduleAudit
}
```

Store a string map of stable keys to epoch milliseconds plus `last_success_epoch_ms`. Synchronize read-modify-write so regular and shift scheduling cannot overwrite one another. Use only app-private SharedPreferences. Never record 22:00 preview notification PendingIntents as ringing alarms.

- [ ] **Step 4: Record only successful schedule/cancel operations**

In `AlarmSchedulerImpl`, call `recordScheduled(key, at)` only after `setExactAndAllowWhileIdle` returns without throwing. Call `recordCancelled(key)` after `AlarmManager.cancel`. Use stable keys `regular:<id>` and `shift:<id>`. Provide the store through Hilt.

- [ ] **Step 5: Run audit and rescheduling tests**

Run: `./gradlew.bat testDebugUnitTest --tests "*AlarmScheduleAuditStoreTest" --tests "*RescheduleAlarmsUseCaseTest"`

Expected: PASS and preview scheduling does not affect audit assertions.

- [ ] **Step 6: Commit the local audit slice**

```powershell
git add app/src/main/java/com/smartworkalarm/alarm app/src/main/java/com/smartworkalarm/di app/src/test
git commit -m "feat: record local alarm scheduling health"
```

---

### Task 6: Add Alarm Operation Diagnostics and Public Settings Actions

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/permissions/AlarmDiagnostics.kt`
- Modify: `app/src/main/java/com/smartworkalarm/permissions/RequiredPermissionsController.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/settings/SettingsRoute.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/settings/SettingsHelpCards.kt`
- Create: `app/src/test/java/com/smartworkalarm/permissions/AlarmDiagnosticsTest.kt`
- Modify: `app/src/test/java/com/smartworkalarm/presentation/settings/SettingsViewModelTest.kt`
- Modify: `app/src/androidTest/java/com/smartworkalarm/presentation/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: `AlarmScheduleAuditStore.read()` from Task 5 and current public permission checks.
- Produces: `DiagnosticStatus { OK, ACTION_REQUIRED, NOT_APPLICABLE, UNKNOWN }`, `AlarmDiagnosticItem`, and `AlarmDiagnosticsSource.read(): List<AlarmDiagnosticItem>`.
- Produces: `RequiredPermissionsController.batteryOptimizationIntent(): Intent` with app-details fallback through existing `launchPermissionSettings`.

- [ ] **Step 1: Write failing diagnostic policy tests**

```kotlin
@Test fun `full screen is not applicable below Android 14`() {
    val items = evaluateAlarmDiagnostics(sdkInt = 33, notifications = true, exact = true, fullScreen = false, batteryOptimized = false, audit = activeAudit())
    assertEquals(DiagnosticStatus.NOT_APPLICABLE, items.single { it.kind == FULL_SCREEN }.status)
}

@Test fun `missing exact alarm is action required`() {
    val items = evaluateAlarmDiagnostics(sdkInt = 36, notifications = true, exact = false, fullScreen = true, batteryOptimized = false, audit = activeAudit())
    assertEquals(DiagnosticStatus.ACTION_REQUIRED, items.single { it.kind == EXACT_ALARM }.status)
}

@Test fun `no trustworthy schedule record is unknown not ok`() {
    val items = evaluateAlarmDiagnostics(36, true, true, true, false, AlarmScheduleAudit(null, null, 0))
    assertEquals(DiagnosticStatus.UNKNOWN, items.single { it.kind == SCHEDULE }.status)
}
```

- [ ] **Step 2: Run diagnostic tests and verify missing policy types**

Run: `./gradlew.bat testDebugUnitTest --tests "*AlarmDiagnosticsTest"`

Expected: FAIL because diagnostic types/functions do not exist.

- [ ] **Step 3: Implement pure evaluation and Android-backed readings**

```kotlin
enum class DiagnosticStatus { OK, ACTION_REQUIRED, NOT_APPLICABLE, UNKNOWN }
enum class AlarmDiagnosticKind { NOTIFICATIONS, EXACT_ALARM, FULL_SCREEN, BATTERY, SCHEDULE }
data class AlarmDiagnosticItem(
    val kind: AlarmDiagnosticKind,
    val status: DiagnosticStatus,
    val summary: String,
)
```

Read notification permission, `AlarmManager.canScheduleExactAlarms()`, `NotificationManager.canUseFullScreenIntent()`, and `PowerManager.isIgnoringBatteryOptimizations(packageName)` with SDK guards. Battery optimization applied is `ACTION_REQUIRED`, exemption is `OK`; do not request or change it automatically. Map a valid audit to its next time/count and an absent/stale record to `UNKNOWN`.

- [ ] **Step 4: Add public settings intents with fallback**

```kotlin
fun batteryOptimizationIntent(): Intent = Intent(
    Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
)
```

Continue using `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` and `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT`. Route all launches through `launchPermissionSettings(primary, appDetailsIntent(), launch)` so unavailable screens fall back to public app details.

- [ ] **Step 5: Extend settings state and add failing screen assertions**

```kotlin
data class SettingsUiState(
    // existing fields
    val diagnostics: List<AlarmDiagnosticItem> = emptyList(),
)
```

Add UI assertions for `알람 작동 진단`, `정상`, `확인 필요`, `해당 없음`, `알 수 없음`, and `설정하러 가기`. Verify Samsung guidance includes `절전 앱`, `초절전 앱`, and that menu names may vary by OS version.

- [ ] **Step 6: Implement the diagnostic card and help copy**

Place the diagnostic card after the existing permission card and before calendar selection. Each row displays label, status, summary, and an action only for actionable states. Schedule status displays the locally recorded next trigger/count without implying AlarmManager introspection. Add Galaxy S22 Ultra guidance for notifications, exact alarms, full-screen alarms, battery use, lock screen, and AOD testing.

- [ ] **Step 7: Run diagnostics, settings ViewModel, and Compose tests**

Run: `./gradlew.bat testDebugUnitTest --tests "*AlarmDiagnosticsTest" --tests "*SettingsViewModelTest" --tests "*RequiredPermission*Test"`

Expected: PASS.

Run with emulator/device: `./gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.smartworkalarm.presentation.SettingsScreenTest`

Expected: PASS.

- [ ] **Step 8: Commit the diagnostics slice**

```powershell
git add app/src/main/java/com/smartworkalarm/permissions app/src/main/java/com/smartworkalarm/presentation/settings app/src/test app/src/androidTest
git commit -m "feat: add local alarm operation diagnostics"
```

---

### Task 7: Regression, Device Verification, and Release Artifact

**Files:**
- Modify only files required by failures found in this task; do not add unrelated refactors
- Update: `app/schemas/com.smartworkalarm.data.local.WorkAlarmDatabase/6.json` if Room generation shows an intentional schema mismatch
- Create: `docs/testing/2026-08-25-galaxy-s22-ultra-alarm-checklist.md`
- Output: `artifacts/WorkAlarm-v1.8-debug.apk`

**Interfaces:**
- Consumes: all prior tasks.
- Produces: passing automated suite, a reproducible Galaxy S22 Ultra checklist, and an installable debug APK.

- [ ] **Step 1: Run the complete unit suite**

Run: `./gradlew.bat testDebugUnitTest`

Expected: BUILD SUCCESSFUL with all unit tests passing. Fix only failures caused by this feature and rerun until green.

- [ ] **Step 2: Run lint and assemble**

Run: `./gradlew.bat lintDebug assembleDebug`

Expected: BUILD SUCCESSFUL with no new lint errors.

- [ ] **Step 3: Run the full instrumentation suite on an available emulator/device**

Run: `./gradlew.bat connectedDebugAndroidTest`

Expected: BUILD SUCCESSFUL. If no device is connected, record this explicitly and do not claim instrumentation passed.

- [ ] **Step 4: Write and execute the Galaxy S22 Ultra manual checklist**

```markdown
# Galaxy S22 Ultra Alarm Verification

- [ ] Locked, screen off: full-screen alarm appears at the exact time.
- [ ] Process removed from recents: alarm still appears and rings/vibrates.
- [ ] Drag right, left, up, down, and diagonally past threshold: each dismisses.
- [ ] Short drag: handle returns and ringing continues.
- [ ] Snooze enabled: 3-second hold schedules and reopens the same screen after N minutes.
- [ ] Snooze disabled: no hold area appears and spoofed snooze action is ignored.
- [ ] Notification/exact/full-screen permissions denied individually: diagnostic and settings link are correct.
- [ ] Battery optimization enabled: diagnostic shows 확인 필요 without forcing exemption.
- [ ] Reboot/time/time-zone changes: enabled regular and shift alarms are rescheduled.
- [ ] Vacation, public holiday, shift matching, and 22:00 change notifications still behave as before.
```

Record Android/One UI version and observed results in the document. Unperformed cases remain unchecked.

- [ ] **Step 5: Create and checksum the APK**

```powershell
Copy-Item -LiteralPath 'app\build\outputs\apk\debug\app-debug.apk' -Destination 'artifacts\WorkAlarm-v1.8-debug.apk'
Get-FileHash -Algorithm SHA256 'artifacts\WorkAlarm-v1.8-debug.apk'
```

Expected: APK exists and SHA-256 is printed for handoff.

- [ ] **Step 6: Record Play release verification requirements**

Before production publishing, upload the release candidate AAB to a non-production Play track, inspect Device Catalog exclusions, complete the full-screen-intent alarm-app declaration, and review the pre-launch report. This step changes Play Console state and therefore requires the user's separate release authorization; if not authorized, report it as pending rather than performing it.

- [ ] **Step 7: Review the final diff and commit verification artifacts**

Run: `git diff --check` and `git status --short`.

Expected: no whitespace errors; only intentional source, test, schema, checklist, and APK artifact changes are present. Do not add existing unrelated `.superpowers/` files or older APKs.

```powershell
git add app docs/testing artifacts/WorkAlarm-v1.8-debug.apk
git commit -m "release: verify dynamic alarm experience"
```
