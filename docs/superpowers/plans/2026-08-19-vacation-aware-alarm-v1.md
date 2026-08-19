# Work Alarm V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an installable Android MVP where user-selected repeating alarms are skipped on Google Calendar vacation dates and resume automatically afterward.

**Architecture:** A single-module MVVM app stores permanent alarm settings in Room, schedules one next occurrence per enabled alarm with AlarmManager, and makes a fail-open calendar decision when a scheduled occurrence fires. Pure Kotlin date and decision logic is isolated behind `AlarmRepository`, `AlarmScheduler`, and `CalendarEventSource`, while WorkManager restores schedules after system changes.

**Tech Stack:** Kotlin 2.3.21, Android Gradle Plugin 8.13.2, Gradle 8.13, compile/target SDK 36, min SDK 26, Jetpack Compose BOM 2026.06.01, Room 2.8.4, WorkManager 2.11.2, Hilt/Dagger with AndroidX Hilt 1.3.0, Coroutines, Flow, AlarmManager, CalendarContract.

**Spec:** `docs/superpowers/specs/2026-08-19-vacation-aware-alarm-v1-design.md`

## Global Constraints

- The repository starts with only the committed V1 design document.
- The app namespace and application ID are `com.smartworkalarm`.
- Minimum SDK is 26; compile SDK and target SDK are 36 because API 36 is installed locally.
- Use only stable dependency releases compatible with compile SDK 36; do not adopt libraries requiring API 37.
- V1 supports Google-backed CalendarContract calendars; Outlook is visible only as `준비 중`.
- Do not implement profiles, user-defined rules, calendar caching, shifts, remote services, or system clock-app control.
- A vacation skip never mutates the alarm's user-controlled `enabled` field.
- Calendar denial or query failure must fail open and ring the alarm.
- New production behavior follows RED → GREEN → REFACTOR; each RED command must be observed failing for the intended reason.
- Never log calendar titles or other private calendar content.

## File Structure

```text
SmartWorkAlarm/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── gradle/wrapper/gradle-wrapper.properties
├── gradlew
├── gradlew.bat
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/smartworkalarm/
        │   │   ├── WorkAlarmApplication.kt
        │   │   ├── MainActivity.kt
        │   │   ├── alarm/
        │   │   ├── data/calendar/
        │   │   ├── data/local/
        │   │   ├── data/repository/
        │   │   ├── di/
        │   │   ├── domain/model/
        │   │   ├── domain/repository/
        │   │   ├── domain/usecase/
        │   │   ├── presentation/alarmedit/
        │   │   ├── presentation/alarmlist/
        │   │   ├── presentation/navigation/
        │   │   ├── presentation/onboarding/
        │   │   ├── presentation/theme/
        │   │   └── worker/
        │   └── res/values/{strings.xml,themes.xml}
        ├── test/java/com/smartworkalarm/
        └── androidTest/java/com/smartworkalarm/
```

The domain files own deterministic rules, data files own Android persistence and calendar access, alarm files own OS scheduling/ringing, and presentation files own screen state. No file may combine two of those responsibilities.

---

### Task 1: Reproducible Android project scaffold

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/com/smartworkalarm/WorkAlarmApplication.kt`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `.gitignore`
- Test: `app/src/test/java/com/smartworkalarm/ProjectConfigurationTest.kt`

**Interfaces:**
- Consumes: JDK 17 and local Android SDK 36.
- Produces: `com.smartworkalarm.WorkAlarmApplication`, a buildable `:app` module, JUnit 5-compatible local tests, KSP-generated Room/Hilt code, and Gradle Wrapper 8.13.

- [ ] **Step 1: Add a failing project configuration test**

Create the Gradle files and the following test before creating `WorkAlarmApplication`:

```kotlin
package com.smartworkalarm

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProjectConfigurationTest {
    @Test
    fun `application class is available`() {
        assertTrue(Class.forName("com.smartworkalarm.WorkAlarmApplication").isAssignableFrom(WorkAlarmApplication::class.java))
    }
}
```

Use a version catalog with these pinned values:

```toml
[versions]
agp = "8.13.2"
kotlin = "2.3.21"
ksp = "2.3.4"
composeBom = "2026.06.01"
room = "2.8.4"
work = "2.11.2"
androidxHilt = "1.3.0"
daggerHilt = "2.57.1"
activityCompose = "1.13.0"
lifecycle = "2.10.0"
navigation = "2.9.5"
coroutines = "1.10.2"
junit5 = "5.13.4"
```

Configure `compileSdk = 36`, `targetSdk = 36`, `minSdk = 26`, Java/Kotlin toolchain 17, Compose compiler plugin, Room schema export, and `testOptions.unitTests.isIncludeAndroidResources = true`.

- [ ] **Step 2: Generate the wrapper and verify RED**

Download or use a temporary Gradle 8.13 distribution only to run:

```powershell
gradle-8.13\bin\gradle.bat wrapper --gradle-version 8.13
.\gradlew.bat testDebugUnitTest --tests "com.smartworkalarm.ProjectConfigurationTest"
```

Expected: compilation fails because `WorkAlarmApplication` does not exist.

- [ ] **Step 3: Add the minimal application and manifest**

```kotlin
package com.smartworkalarm

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WorkAlarmApplication : Application()
```

Declare `.WorkAlarmApplication` in the manifest. Also declare `READ_CALENDAR`, `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `USE_FULL_SCREEN_INTENT`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, and `FOREGROUND_SERVICE_MEDIA_PLAYBACK`; do not add `WRITE_CALENDAR` or internet permission.

- [ ] **Step 4: Verify GREEN and baseline build**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.smartworkalarm.ProjectConfigurationTest"
.\gradlew.bat assembleDebug
```

Expected: both commands pass and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 5: Commit**

```powershell
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle gradlew gradlew.bat app
git commit -m "build: scaffold Work Alarm Android app"
```

---

### Task 2: Alarm model and next-occurrence calculation

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/domain/model/Alarm.kt`
- Create: `app/src/main/java/com/smartworkalarm/domain/model/RepeatDay.kt`
- Create: `app/src/main/java/com/smartworkalarm/domain/usecase/CalculateNextOccurrenceUseCase.kt`
- Test: `app/src/test/java/com/smartworkalarm/domain/usecase/CalculateNextOccurrenceUseCaseTest.kt`

**Interfaces:**
- Consumes: `java.time.Clock`, `Instant`, `LocalTime`, and `ZoneId`.
- Produces: `Alarm(id: Long, name: String, hour: Int, minute: Int, repeatDaysMask: Int, enabled: Boolean, skipOnVacation: Boolean)` and `CalculateNextOccurrenceUseCase.execute(alarm: Alarm, now: Instant, zoneId: ZoneId): Instant?`.

- [ ] **Step 1: Write failing tests for the next valid weekday**

```kotlin
class CalculateNextOccurrenceUseCaseTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val useCase = CalculateNextOccurrenceUseCase()

    @Test
    fun `Monday alarm later today returns today`() {
        val alarm = alarmAt(7, 0, RepeatDay.MONDAY)
        val now = ZonedDateTime.of(2026, 8, 17, 6, 30, 0, 0, zone).toInstant()
        assertEquals(
            ZonedDateTime.of(2026, 8, 17, 7, 0, 0, 0, zone).toInstant(),
            useCase.execute(alarm, now, zone),
        )
    }

    @Test
    fun `Friday alarm after its time advances to next Friday`() {
        val alarm = alarmAt(7, 0, RepeatDay.FRIDAY)
        val now = ZonedDateTime.of(2026, 8, 21, 7, 1, 0, 0, zone).toInstant()
        assertEquals(
            ZonedDateTime.of(2026, 8, 28, 7, 0, 0, 0, zone).toInstant(),
            useCase.execute(alarm, now, zone),
        )
    }

    @Test
    fun `disabled alarm has no next occurrence`() {
        assertNull(useCase.execute(alarmAt(7, 0, RepeatDay.MONDAY).copy(enabled = false), Instant.EPOCH, zone))
    }
}
```

Add cases for Sunday-to-Monday, all seven days, and an empty repeat mask.

- [ ] **Step 2: Verify RED**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.smartworkalarm.domain.usecase.CalculateNextOccurrenceUseCaseTest"
```

Expected: compilation fails because the model and use case do not exist.

- [ ] **Step 3: Implement the minimal deterministic calculator**

```kotlin
class CalculateNextOccurrenceUseCase {
    fun execute(alarm: Alarm, now: Instant, zoneId: ZoneId): Instant? {
        if (!alarm.enabled || alarm.repeatDaysMask == 0) return null
        val nowZoned = now.atZone(zoneId)
        return (0L..7L)
            .asSequence()
            .map { nowZoned.toLocalDate().plusDays(it) }
            .filter { date -> alarm.repeatsOn(date.dayOfWeek) }
            .map { date -> ZonedDateTime.of(date, LocalTime.of(alarm.hour, alarm.minute), zoneId).toInstant() }
            .firstOrNull { it > now }
    }
}
```

Implement `RepeatDay` using bit positions `MONDAY = 1 shl 0` through `SUNDAY = 1 shl 6`, plus `Alarm.repeatsOn(dayOfWeek)`.

- [ ] **Step 4: Verify GREEN**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.smartworkalarm.domain.usecase.CalculateNextOccurrenceUseCaseTest"
```

Expected: all calculator tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/smartworkalarm/domain app/src/test/java/com/smartworkalarm/domain
git commit -m "feat: calculate repeating alarm occurrences"
```

---

### Task 3: Room persistence and alarm repository

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/data/local/AlarmEntity.kt`
- Create: `app/src/main/java/com/smartworkalarm/data/local/AlarmDao.kt`
- Create: `app/src/main/java/com/smartworkalarm/data/local/AlarmEntityMapper.kt`
- Create: `app/src/main/java/com/smartworkalarm/data/local/WorkAlarmDatabase.kt`
- Create: `app/src/main/java/com/smartworkalarm/domain/repository/AlarmRepository.kt`
- Create: `app/src/main/java/com/smartworkalarm/data/repository/AlarmRepositoryImpl.kt`
- Create: `app/src/main/java/com/smartworkalarm/di/DatabaseModule.kt`
- Test: `app/src/androidTest/java/com/smartworkalarm/data/local/AlarmDaoTest.kt`
- Test: `app/src/test/java/com/smartworkalarm/data/local/AlarmEntityMapperTest.kt`

**Interfaces:**
- Consumes: `Alarm` from Task 2.
- Produces: `AlarmRepository.observeAll(): Flow<List<Alarm>>`, `getById(id: Long): Alarm?`, `upsert(alarm: Alarm): Long`, `delete(id: Long)`, and `setEnabled(id: Long, enabled: Boolean)`.

- [ ] **Step 1: Write the failing mapper test**

```kotlin
@Test
fun `entity round trip preserves user settings`() {
    val alarm = Alarm(4, "출근", 7, 0, 31, true, true)
    val entity = alarm.toEntity(createdAt = 123L)
    assertEquals(alarm, entity.toDomain())
    assertEquals(123L, entity.createdAt)
}
```

- [ ] **Step 2: Verify mapper RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.smartworkalarm.data.local.AlarmEntityMapperTest"
```

Expected: compilation fails because mapper functions do not exist.

- [ ] **Step 3: Implement entity, mapper, DAO, database, and repository**

Use this exact DAO contract:

```kotlin
@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute, id")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Long): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE enabled = 1")
    suspend fun getEnabled(): List<AlarmEntity>

    @Upsert
    suspend fun upsert(entity: AlarmEntity): Long

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun delete(id: Long)
}
```

Set Room database version to 1 and export schemas to `app/schemas`. Do not use destructive migration fallback.

- [ ] **Step 4: Verify mapper GREEN**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.smartworkalarm.data.local.AlarmEntityMapperTest"
```

Expected: mapper tests pass.

- [ ] **Step 5: Add and run the instrumentation DAO test**

Create an in-memory database test that inserts two alarms, verifies sorted Flow output, updates one `enabled` field without changing `skipOnVacation`, and deletes one alarm.

Run when an emulator/device is available:

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.smartworkalarm.data.local.AlarmDaoTest
```

Expected: PASS. If no device is connected, record this command for final device verification and still run `assembleDebug` plus mapper unit tests.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/smartworkalarm/data app/src/main/java/com/smartworkalarm/domain/repository app/src/main/java/com/smartworkalarm/di app/src/test app/src/androidTest app/schemas
git commit -m "feat: persist alarm settings with Room"
```

---

### Task 4: Calendar vacation detection and fail-open decision

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/domain/model/CalendarEvent.kt`
- Create: `app/src/main/java/com/smartworkalarm/domain/model/AlarmExecutionDecision.kt`
- Create: `app/src/main/java/com/smartworkalarm/domain/repository/CalendarEventSource.kt`
- Create: `app/src/main/java/com/smartworkalarm/domain/usecase/VacationMatcher.kt`
- Create: `app/src/main/java/com/smartworkalarm/domain/usecase/ShouldRingAlarmUseCase.kt`
- Create: `app/src/main/java/com/smartworkalarm/data/calendar/CalendarContractEventSource.kt`
- Create: `app/src/main/java/com/smartworkalarm/di/CalendarModule.kt`
- Test: `app/src/test/java/com/smartworkalarm/domain/usecase/VacationMatcherTest.kt`
- Test: `app/src/test/java/com/smartworkalarm/domain/usecase/ShouldRingAlarmUseCaseTest.kt`

**Interfaces:**
- Consumes: `Alarm`, `ContentResolver`, `CalendarContract.Calendars`, and `CalendarContract.Instances`.
- Produces: `CalendarEventSource.getEventsOverlapping(startInclusive: Instant, endExclusive: Instant): Result<List<CalendarEvent>>` and `ShouldRingAlarmUseCase.execute(alarm: Alarm, date: LocalDate, zoneId: ZoneId): AlarmExecutionDecision`.

- [ ] **Step 1: Write failing vacation matcher tests**

```kotlin
@ParameterizedTest
@ValueSource(strings = ["휴가", "여름휴가", " 가족 휴가 "])
fun `vacation keyword matches title`(title: String) {
    assertTrue(VacationMatcher.isVacationTitle(title))
}

@ParameterizedTest
@NullAndEmptySource
@ValueSource(strings = ["회의", "연차", "출장"])
fun `non vacation title does not match`(title: String?) {
    assertFalse(VacationMatcher.isVacationTitle(title))
}
```

- [ ] **Step 2: Verify matcher RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.smartworkalarm.domain.usecase.VacationMatcherTest"
```

Expected: compilation fails because `VacationMatcher` does not exist.

- [ ] **Step 3: Implement the matcher and decision use case**

Use these decisions:

```kotlin
enum class AlarmExecutionDecision {
    RING,
    RING_WITH_CALENDAR_WARNING,
    SKIP_FOR_VACATION,
    DO_NOT_RING,
}
```

`ShouldRingAlarmUseCase` must short-circuit disabled alarms and alarms with `skipOnVacation=false`; query `[date.atStartOfDay(zone), date.plusDays(1).atStartOfDay(zone))`; return `SKIP_FOR_VACATION` when any successful result has a matching title; and return `RING_WITH_CALENDAR_WARNING` on every failed result.

- [ ] **Step 4: Add decision tests and verify RED then GREEN**

Use a fake `CalendarEventSource`, not a mock framework, to cover successful vacation, successful non-vacation, failure, disabled, and skip-option-off cases.

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.smartworkalarm.domain.usecase.ShouldRingAlarmUseCaseTest"
```

Expected first run: FAIL because the use case is incomplete. Expected after minimal implementation: PASS.

- [ ] **Step 5: Implement CalendarContractEventSource**

Query visible, synced calendars where `ACCOUNT_TYPE = "com.google"`; then query `CalendarContract.Instances` using the standard begin/end URI path and selected calendar IDs. Read only `_ID`, `TITLE`, `BEGIN`, `END`, and `ALL_DAY`. Wrap `SecurityException`, null Cursor, and provider exceptions in `Result.failure`; never print a title to logs.

The provider adapter is covered by a focused Robolectric test if its shadow supports `Instances`; otherwise isolate row mapping into an internal function and unit test null title, begin/end conversion, and empty cursor behavior.

- [ ] **Step 6: Run Task 4 tests and commit**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.smartworkalarm.domain.usecase.*"
git add app/src/main/java/com/smartworkalarm/domain app/src/main/java/com/smartworkalarm/data/calendar app/src/main/java/com/smartworkalarm/di app/src/test
git commit -m "feat: detect Google Calendar vacation dates"
```

---

### Task 5: Exact alarm scheduling and coordinated CRUD use cases

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/domain/repository/AlarmScheduler.kt`
- Create: `app/src/main/java/com/smartworkalarm/alarm/ExactAlarmPermission.kt`
- Create: `app/src/main/java/com/smartworkalarm/alarm/AlarmSchedulerImpl.kt`
- Create: `app/src/main/java/com/smartworkalarm/di/AlarmModule.kt`
- Create: `app/src/main/java/com/smartworkalarm/domain/usecase/GetAlarmsUseCase.kt`
- Create: `app/src/main/java/com/smartworkalarm/domain/usecase/SaveAlarmUseCase.kt`
- Create: `app/src/main/java/com/smartworkalarm/domain/usecase/DeleteAlarmUseCase.kt`
- Create: `app/src/main/java/com/smartworkalarm/domain/usecase/SetAlarmEnabledUseCase.kt`
- Test: `app/src/test/java/com/smartworkalarm/domain/usecase/SaveAlarmUseCaseTest.kt`
- Test: `app/src/test/java/com/smartworkalarm/domain/usecase/DeleteAlarmUseCaseTest.kt`
- Test: `app/src/test/java/com/smartworkalarm/domain/usecase/SetAlarmEnabledUseCaseTest.kt`

**Interfaces:**
- Consumes: Task 2 calculator and Task 3 repository.
- Produces: `AlarmScheduler.schedule(alarmId: Long, triggerAt: Instant): ScheduleResult`, `cancel(alarmId: Long)`, `scheduleSnooze(alarmId: Long, triggerAt: Instant)`, and CRUD use cases that keep Room and OS scheduling consistent.

- [ ] **Step 1: Write a failing SaveAlarmUseCase test**

```kotlin
@Test
fun `saving enabled alarm persists then schedules returned id`() = runTest {
    val repository = FakeAlarmRepository(savedId = 42)
    val scheduler = FakeAlarmScheduler()
    val useCase = SaveAlarmUseCase(repository, scheduler, calculator, fixedClock, seoulZone)

    val result = useCase.execute(Alarm(0, "출근", 7, 0, 31, true, true))

    assertEquals(42, result.getOrThrow())
    assertEquals(42, scheduler.scheduled.single().alarmId)
}
```

Add tests for blank name, invalid time, no repeat days, disabled save, enable, disable, and delete.

- [ ] **Step 2: Verify use-case RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.smartworkalarm.domain.usecase.SaveAlarmUseCaseTest"
```

Expected: compilation fails because the use case and scheduler do not exist.

- [ ] **Step 3: Implement contracts and CRUD coordination**

Use sealed results:

```kotlin
sealed interface ScheduleResult {
    data object Scheduled : ScheduleResult
    data object ExactAlarmPermissionRequired : ScheduleResult
}
```

Save validates, persists, cancels the old identity, and schedules only if enabled. Disable and delete cancel before returning. Enable persists then schedules the next occurrence. The repository remains the source of permanent truth if OS scheduling fails.

- [ ] **Step 4: Verify CRUD GREEN**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.smartworkalarm.domain.usecase.SaveAlarmUseCaseTest" --tests "com.smartworkalarm.domain.usecase.DeleteAlarmUseCaseTest" --tests "com.smartworkalarm.domain.usecase.SetAlarmEnabledUseCaseTest"
```

- [ ] **Step 5: Implement AlarmSchedulerImpl**

Use immutable/update-current PendingIntents targeting `AlarmBroadcastReceiver`. Normal alarms use request code derived from alarm ID; snooze requests use a separate namespace. On Android 12+, call `alarmManager.canScheduleExactAlarms()` first. Register exact wakeup alarms with `setExactAndAllowWhileIdle`; include only alarm ID and `isSnooze` extras.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/smartworkalarm/alarm app/src/main/java/com/smartworkalarm/domain app/src/main/java/com/smartworkalarm/di app/src/test
git commit -m "feat: schedule and manage exact alarms"
```

---

### Task 6: Alarm execution, ringing UI, snooze, and system recovery

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/alarm/AlarmBroadcastReceiver.kt`
- Create: `app/src/main/java/com/smartworkalarm/alarm/AlarmExecutionCoordinator.kt`
- Create: `app/src/main/java/com/smartworkalarm/alarm/AlarmRingingService.kt`
- Create: `app/src/main/java/com/smartworkalarm/alarm/AlarmNotificationFactory.kt`
- Create: `app/src/main/java/com/smartworkalarm/alarm/AlarmActivity.kt`
- Create: `app/src/main/java/com/smartworkalarm/alarm/SystemChangeReceiver.kt`
- Create: `app/src/main/java/com/smartworkalarm/worker/RescheduleAlarmsWorker.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/smartworkalarm/alarm/AlarmExecutionCoordinatorTest.kt`
- Test: `app/src/test/java/com/smartworkalarm/worker/RescheduleAlarmsWorkerTest.kt`

**Interfaces:**
- Consumes: `AlarmRepository`, `AlarmScheduler`, `ShouldRingAlarmUseCase`, WorkManager, MediaPlayer/Ringtone APIs, Vibrator, and NotificationManager.
- Produces: `AlarmExecutionCoordinator.execute(alarmId: Long, isSnooze: Boolean, now: Instant): ExecutionOutcome`, full-screen alarm controls, fixed five-minute snooze, and unique rescheduling work.

- [ ] **Step 1: Write failing coordinator tests**

```kotlin
@Test
fun `next repeating occurrence is scheduled before vacation occurrence is skipped`() = runTest {
    val events = mutableListOf<String>()
    val coordinator = coordinator(
        decision = AlarmExecutionDecision.SKIP_FOR_VACATION,
        onSchedule = { events += "schedule" },
        onRing = { events += "ring" },
    )

    assertEquals(ExecutionOutcome.SkippedForVacation, coordinator.execute(7, false, now))
    assertEquals(listOf("schedule"), events)
}
```

Add tests for missing alarm, disabled alarm, normal ring, warning ring, and snooze not creating a second repeating schedule.

- [ ] **Step 2: Verify coordinator RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.smartworkalarm.alarm.AlarmExecutionCoordinatorTest"
```

Expected: compilation fails because coordinator types do not exist.

- [ ] **Step 3: Implement coordinator and Receiver**

Receiver calls `goAsync`, launches an injected application coroutine, invokes the coordinator, and always finishes the pending result in `finally`. The coordinator schedules the next repeating occurrence before calendar access for non-snooze deliveries. It invokes `AlarmRingingService.start()` for both `RING` decisions and does nothing visible for vacation skips.

- [ ] **Step 4: Verify coordinator GREEN**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.smartworkalarm.alarm.AlarmExecutionCoordinatorTest"
```

- [ ] **Step 5: Implement ringing, notification, dismiss, and snooze**

Create a high-importance notification channel with alarm audio attributes. Start a foreground service immediately, acquire only a time-bounded partial wake lock, play the system alarm ringtone in a loop, and vibrate with an alarm pattern. `AlarmActivity` shows alarm name with two buttons:

```text
다시 알림 (5분) | 해제
```

Dismiss stops service sound/vibration. Snooze schedules an exact one-time alarm for `clock.instant().plus(Duration.ofMinutes(5))`, then stops the service. Both actions finish the Activity. The service releases MediaPlayer, vibrator, and wake lock in every stop path.

- [ ] **Step 6: Implement and test recovery Worker**

`SystemChangeReceiver` enqueues unique work named `reschedule-active-alarms` with `ExistingWorkPolicy.REPLACE` for boot, package replacement, time set, and timezone changes. Worker loads enabled alarms and schedules each valid next occurrence; missing exact-alarm access returns success after leaving the UI-visible permission state unresolved, because retrying cannot grant special access.

Use WorkManager test initialization and fakes to assert only enabled alarms are scheduled.

- [ ] **Step 7: Verify alarm/recovery tests and manifest merge**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.smartworkalarm.alarm.*" --tests "com.smartworkalarm.worker.*"
.\gradlew.bat processDebugMainManifest
```

Expected: tests and manifest processing pass with exported flags explicitly set for every component.

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/java/com/smartworkalarm/alarm app/src/main/java/com/smartworkalarm/worker app/src/main/AndroidManifest.xml app/src/test
git commit -m "feat: ring, snooze, and restore alarms"
```

---

### Task 7: Onboarding, alarm list, editor, and permission UX

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/MainActivity.kt`
- Create: `app/src/main/java/com/smartworkalarm/presentation/navigation/WorkAlarmNavHost.kt`
- Create: `app/src/main/java/com/smartworkalarm/presentation/onboarding/OnboardingScreen.kt`
- Create: `app/src/main/java/com/smartworkalarm/presentation/onboarding/OnboardingPreferences.kt`
- Create: `app/src/main/java/com/smartworkalarm/presentation/alarmlist/AlarmListViewModel.kt`
- Create: `app/src/main/java/com/smartworkalarm/presentation/alarmlist/AlarmListScreen.kt`
- Create: `app/src/main/java/com/smartworkalarm/presentation/alarmedit/AlarmEditViewModel.kt`
- Create: `app/src/main/java/com/smartworkalarm/presentation/alarmedit/AlarmEditScreen.kt`
- Create: `app/src/main/java/com/smartworkalarm/presentation/theme/Theme.kt`
- Test: `app/src/test/java/com/smartworkalarm/presentation/alarmedit/AlarmEditViewModelTest.kt`
- Test: `app/src/androidTest/java/com/smartworkalarm/presentation/WorkAlarmNavigationTest.kt`

**Interfaces:**
- Consumes: Task 5 CRUD use cases, permission state providers, and Compose Navigation.
- Produces: routes `onboarding`, `alarms`, `alarm/new`, `alarm/{alarmId}` and stable `StateFlow` UI models.

- [ ] **Step 1: Write failing editor ViewModel tests**

```kotlin
@Test
fun `saving requires at least one repeat day`() = runTest {
    val viewModel = viewModelWith(FakeSaveAlarmUseCase())
    viewModel.onNameChanged("출근")
    viewModel.onTimeChanged(7, 0)
    viewModel.save()
    assertEquals("반복 요일을 하나 이상 선택하세요", viewModel.state.value.repeatDaysError)
}

@Test
fun `vacation switch is preserved in saved alarm`() = runTest {
    val saver = FakeSaveAlarmUseCase()
    val viewModel = viewModelWith(saver)
    viewModel.onNameChanged("출근")
    viewModel.onTimeChanged(7, 0)
    viewModel.toggleDay(RepeatDay.MONDAY)
    viewModel.onSkipOnVacationChanged(true)
    viewModel.save()
    assertTrue(saver.saved.single().skipOnVacation)
}
```

- [ ] **Step 2: Verify ViewModel RED**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.smartworkalarm.presentation.alarmedit.AlarmEditViewModelTest"
```

Expected: compilation fails because editor state and ViewModel do not exist.

- [ ] **Step 3: Implement ViewModels and verify GREEN**

Use immutable UI state data classes. Alarm list observes repository Flow and combines it with exact-alarm, notification, and calendar permission states. Editor owns draft values and delegates validation/storage to SaveAlarmUseCase; it maps validation failures to Korean field messages.

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.smartworkalarm.presentation.*"
```

- [ ] **Step 4: Implement onboarding and permissions**

Show this exact primary message:

```text
휴가를 캘린더에 등록하면 출근 알람을 알아서 건너뜁니다.
휴가가 끝나면 다시 원래대로 울립니다.
```

Google selection requests `READ_CALENDAR`. Outlook selection shows `Outlook Calendar는 준비 중입니다.` without an auth flow. Calendar denial is non-blocking and explains that alarms still ring. Before the first enabled alarm is saved, request notification permission on Android 13+ and open `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` when exact access is missing.

- [ ] **Step 5: Implement alarm list and editor Compose screens**

List rows show name, formatted time, repeat-day summary, enabled switch, next occurrence, and a `휴가 시 건너뜀` label when configured. Permission banners link to the appropriate settings. Editor provides Material 3 time picker, seven day toggles, enabled switch, vacation switch, save, and delete for existing alarms.

- [ ] **Step 6: Add navigation UI test**

Test onboarding → Google permission continuation → empty alarm list → new editor, plus an Outlook click displaying the prepared message. Use injected permission launchers/fakes so the test asserts UI behavior without changing device settings.

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.smartworkalarm.presentation.WorkAlarmNavigationTest
```

Expected: PASS on an emulator/device. If no device is available, retain the test and include the command in final device verification.

- [ ] **Step 7: Build and commit**

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
git add app/src/main app/src/test app/src/androidTest
git commit -m "feat: add vacation-aware alarm UI"
```

---

### Task 8: Integration hardening and V1 acceptance verification

**Files:**
- Modify: only files implicated by failing acceptance tests.
- Create: `docs/testing/v1-device-checklist.md`
- Test: all tests under `app/src/test` and `app/src/androidTest`.

**Interfaces:**
- Consumes: the complete V1 application from Tasks 1–7.
- Produces: a clean debug APK and recorded manual device verification for all OS-controlled behavior.

- [ ] **Step 1: Run static and automated verification**

```powershell
.\gradlew.bat clean lintDebug testDebugUnitTest assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

Expected: all available tasks pass with no lint errors. If no emulator/device is attached, `connectedDebugAndroidTest` may be the only unavailable command; document that fact rather than claiming it passed.

- [ ] **Step 2: Verify APK and dependency policy**

```powershell
Get-Item app\build\outputs\apk\debug\app-debug.apk
```

Expected: the debug APK exists and has a non-zero length. Review `gradle/libs.versions.toml` directly and confirm it contains no dynamic, alpha, beta, RC, or API-37-only dependency.

- [ ] **Step 3: Execute the physical-device checklist**

Create a checklist containing these exact scenarios and record device/API/result/date:

```text
[ ] Mon-Fri 07:00 alarm rings on a normal selected weekday
[ ] Second alarm with skipOnVacation=false still rings during vacation
[ ] One-day all-day "휴가" skips only the opted-in alarm that day
[ ] Multi-day "여름휴가" skips every included day and rings after the end date
[ ] Vacation created shortly before trigger is respected
[ ] Vacation deleted shortly before trigger allows ringing
[ ] Calendar permission revoked causes fail-open ringing
[ ] Screen on, screen off, and lock-screen delivery work
[ ] Dismiss stops sound and vibration
[ ] Snooze rings once after five minutes
[ ] Reboot restores enabled alarms
[ ] Time and timezone changes recalculate next occurrence
```

- [ ] **Step 4: Fix each discovered defect with a regression test**

For every failure, add the smallest failing unit/instrumentation test, observe RED, implement the minimal fix, and rerun the focused test before the full suite. Do not add V2 functionality while hardening V1.

- [ ] **Step 5: Run final verification**

```powershell
.\gradlew.bat clean lintDebug testDebugUnitTest assembleDebug
git status --short
```

Expected: all commands pass, APK exists, and only the intentionally updated device checklist may remain uncommitted before the final commit.

- [ ] **Step 6: Commit acceptance evidence**

```powershell
git add docs/testing/v1-device-checklist.md app
git commit -m "test: verify Work Alarm v1 acceptance flow"
```

## Plan Self-Review Record

- Spec coverage: every V1 success criterion maps to Tasks 2–8; all excluded features remain absent.
- File ownership: deterministic rules, Android adapters, runtime alarm behavior, and Compose UI are separate.
- Type consistency: `Alarm`, `AlarmRepository`, `AlarmScheduler`, `CalendarEventSource`, and decision enum signatures are stable across tasks.
- Failure policy: calendar failures ring; exact-alarm permission failures are surfaced rather than silently degraded.
- Testability: Android provider and scheduler boundaries have fakes; pure business rules run as local tests.
