# Work Alarm V1.1 Alarm Customization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace broken numeric time entry with a Time Picker and add per-alarm calendar keywords and system alarm sounds without losing V1 data.

**Architecture:** Extend the existing Alarm model and Room schema with normalized keywords and an optional ringtone URI. Keep calendar matching in pure Kotlin, use Android system picker contracts at the UI boundary, and pass the selected URI through the execution coordinator to a fallback-capable ringing service.

**Tech Stack:** Existing Kotlin/Compose/Room/Hilt/AlarmManager stack, Material 3 TimePicker, Android RingtoneManager.

**Spec:** `docs/superpowers/specs/2026-08-22-alarm-customization-v1-1-design.md`

## Global Constraints

- Preserve all V1 alarm rows through Room migration 1→2.
- Calendar search examines event title only and uses normalized substring matching.
- Empty keywords or a disabled calendar switch must avoid calendar access.
- Every alarm can store a distinct ringtone URI.
- Invalid ringtone URIs fall back to the system alarm URI, then notification URI.
- Follow RED → GREEN → REFACTOR for each production behavior.

---

### Task 1: Extend Alarm and Room with keywords and ringtone URI

**Files:**
- Modify: `app/src/main/java/com/smartworkalarm/domain/model/Alarm.kt`
- Modify: `app/src/main/java/com/smartworkalarm/data/local/AlarmEntity.kt`
- Modify: `app/src/main/java/com/smartworkalarm/data/local/AlarmEntityMapper.kt`
- Modify: `app/src/main/java/com/smartworkalarm/data/local/WorkAlarmDatabase.kt`
- Modify: `app/src/main/java/com/smartworkalarm/di/DatabaseModule.kt`
- Modify: all Alarm constructors in production and tests
- Test: `app/src/test/java/com/smartworkalarm/data/local/AlarmEntityMapperTest.kt`
- Test: `app/src/androidTest/java/com/smartworkalarm/data/local/AlarmMigrationTest.kt`

**Interfaces:**
- Produces: `Alarm.skipOnCalendarMatch: Boolean`, `calendarKeywords: List<String>`, `ringtoneUri: String?`.
- Produces: `MIGRATION_1_2` adding `calendar_keywords` and `ringtone_uri`.

- [ ] Write a failing mapper test asserting keywords and URI survive an entity round trip.
- [ ] Run the focused mapper test and confirm it fails because the fields do not exist.
- [ ] Add model/entity fields and a stable comma-separated Room mapping.
- [ ] Add `MIGRATION_1_2` with exactly these statements:

```sql
ALTER TABLE alarms ADD COLUMN calendar_keywords TEXT NOT NULL DEFAULT '휴가'
ALTER TABLE alarms ADD COLUMN ringtone_uri TEXT DEFAULT NULL
```

- [ ] Register the migration in `Room.databaseBuilder` and raise database version to 2.
- [ ] Add an instrumentation migration test that creates schema version 1, inserts a V1 row, migrates, and asserts preserved values plus `휴가`/null defaults.
- [ ] Run mapper tests, KSP schema generation, and Android-test compilation.
- [ ] Commit with `feat: store alarm calendar rules and sounds`.

### Task 2: Generalize calendar keyword matching

**Files:**
- Replace: `app/src/main/java/com/smartworkalarm/domain/usecase/VacationMatcher.kt` with `CalendarKeywordMatcher.kt`
- Modify: `app/src/main/java/com/smartworkalarm/domain/usecase/ShouldRingAlarmUseCase.kt`
- Test: `app/src/test/java/com/smartworkalarm/domain/usecase/CalendarKeywordMatcherTest.kt`
- Modify: `app/src/test/java/com/smartworkalarm/domain/usecase/ShouldRingAlarmUseCaseTest.kt`

**Interfaces:**
- Produces: `CalendarKeywordMatcher.normalize(input: String): List<String>`.
- Produces: `CalendarKeywordMatcher.matches(title: String?, keywords: List<String>): Boolean`.

- [ ] Write failing literal tests for `" 휴가, 연차,휴가, 병가 "` normalization and title start/middle/end matches.
- [ ] Confirm RED because the generalized matcher does not exist.
- [ ] Implement trim, lowercase with `Locale.ROOT`, empty removal, and order-preserving distinct.
- [ ] Change `ShouldRingAlarmUseCase` to short-circuit when disabled or keywords are empty and otherwise match any keyword against title.
- [ ] Update decision tests for two alarms with different keywords and calendar query avoidance.
- [ ] Run all domain use-case tests and commit `feat: match per-alarm calendar keywords`.

### Task 3: Replace numeric fields with Material Time Picker

**Files:**
- Modify: `app/src/main/java/com/smartworkalarm/presentation/alarmedit/AlarmEditViewModel.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/alarmedit/AlarmEditScreen.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/alarmlist/AlarmListScreen.kt`
- Test: `app/src/test/java/com/smartworkalarm/presentation/alarmedit/AlarmEditViewModelTest.kt`
- Test: `app/src/androidTest/java/com/smartworkalarm/presentation/AlarmEditScreenTest.kt`

**Interfaces:**
- Consumes: `onTimeChanged(hour: Int, minute: Int)`.
- Produces: a clickable `HH:mm` time row and Material 3 `TimePicker` dialog.

- [ ] Add a failing ViewModel test asserting a 05:00 selection remains 05:00.
- [ ] Add keyword draft state and validation to the ViewModel.
- [ ] Remove hour/minute text fields and show a 24-hour TimePicker initialized from current state.
- [ ] Add a Compose test that selects/accepts a time and observes the formatted value.
- [ ] Show normalized keyword summaries on the alarm list.
- [ ] Run focused unit tests and Android-test compilation; commit `fix: use reliable alarm time picker`.

### Task 4: Select and play a distinct ringtone per alarm

**Files:**
- Modify: `app/src/main/java/com/smartworkalarm/presentation/alarmedit/AlarmEditScreen.kt`
- Create: `app/src/main/java/com/smartworkalarm/alarm/AlarmSoundResolver.kt`
- Modify: `app/src/main/java/com/smartworkalarm/alarm/AlarmRinger.kt`
- Modify: `app/src/main/java/com/smartworkalarm/alarm/AlarmRingerImpl.kt`
- Modify: `app/src/main/java/com/smartworkalarm/alarm/AlarmExecutionCoordinator.kt`
- Modify: `app/src/main/java/com/smartworkalarm/alarm/AlarmRingingService.kt`
- Modify: related tests
- Test: `app/src/test/java/com/smartworkalarm/alarm/AlarmExecutionCoordinatorTest.kt`
- Test: `app/src/test/java/com/smartworkalarm/alarm/AlarmSoundResolverTest.kt`

**Interfaces:**
- Changes: `AlarmRinger.start(alarmId: Long, alarmName: String, ringtoneUri: String?)`.
- Produces: ordered URI candidates selected URI → default alarm → default notification.

- [ ] Write a failing coordinator test proving the selected URI reaches `AlarmRinger`.
- [ ] Write a failing resolver test proving fallback order and duplicate removal.
- [ ] Pass ringtone URI through coordinator, service Intent, and notification full-screen Intent.
- [ ] Add `ACTION_RINGTONE_PICKER` via `StartActivityForResult`, using TYPE_ALARM and the existing URI.
- [ ] Persist the returned URI in ViewModel state and display its Ringtone title.
- [ ] Make MediaPlayer try resolver candidates in order without crashing the service.
- [ ] Run alarm tests and assembleDebug; commit `feat: choose per-alarm ringtone`.

### Task 5: Verify upgrade and produce APK

**Files:**
- Modify: `docs/testing/v1-device-checklist.md`
- Output: `C:/Users/sondh/Documents/SmartWorkAlarm/artifacts/WorkAlarm-v1.1-debug.apk`

**Interfaces:**
- Consumes: all V1.1 changes.
- Produces: one update-installable debug APK.

- [ ] Run `clean lintDebug testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug`.
- [ ] Confirm unit-test XML reports zero failures and errors.
- [ ] Confirm APK exists and record SHA-256.
- [ ] Add manual checks for V1→V1.1 update, 05:00 selection, different keyword sets, and different alarm sounds.
- [ ] Commit acceptance evidence with `test: verify Work Alarm v1.1`.

## Plan Self-Review Record

- The plan covers every V1.1 success criterion and retains the V1 fail-open policy.
- Model names and ringtone interfaces are consistent across persistence, domain, UI, and runtime.
- Description search, regex, Outlook, and bundled audio remain excluded.
- No destructive migration or dynamic dependency is introduced.
