# Work Alarm V1.2 Next-Day Preview and Help Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 매일 22시에 다음 날 알람 상태 변화를 한 번 안내하고, 캘린더 미연결 안내와 설정/도움말 화면을 제공한다.

**Architecture:** `AlarmManager`가 매일 22시 전용 receiver를 실행하고 receiver는 Hilt `Worker`에 다음 날 판정을 위임한다. 알람별 마지막 캘린더 억제 상태는 Room 테이블에 저장하며, UI는 단일 `CalendarConnectionRepository`를 통해 권한·연결 캘린더 목록을 확인한다.

**Tech Stack:** Kotlin, Android AlarmManager, BroadcastReceiver, WorkManager, Room, CalendarContract, Hilt, Jetpack Compose, JUnit4

**Spec:** `docs/superpowers/specs/2026-08-22-next-day-alarm-preview-and-help-design.md`

## Global Constraints

- V1.2에서 지원 캘린더는 Google Calendar만이다.
- 22시 이후 캘린더 변경에 대한 추가 상단바 알림은 만들지 않는다.
- 실제 알람 시각의 기존 캘린더 최종 확인과 fail-open 정책을 유지한다.
- 조회 가능한 Google 캘린더가 하나라도 있으면 연결 안내 팝업을 표시하지 않는다.
- 앱 시작, 알람 추가, 알람 수정 시 조회 가능한 캘린더가 0개이면 매번 안내한다.
- 알람의 `enabled` 값은 캘린더 결과로 변경하지 않는다.
- 구현은 테스트를 먼저 실패시킨 뒤 최소 코드로 통과시킨다.

---

### Task 1: 캘린더 연결 상태 조회 경계

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/domain/model/CalendarConnectionState.kt`
- Create: `app/src/main/java/com/smartworkalarm/domain/repository/CalendarConnectionRepository.kt`
- Create: `app/src/main/java/com/smartworkalarm/data/calendar/CalendarContractConnectionRepository.kt`
- Modify: `app/src/main/java/com/smartworkalarm/di/CalendarModule.kt`
- Test: `app/src/test/java/com/smartworkalarm/data/calendar/CalendarConnectionEvaluatorTest.kt`

**Interfaces:**
- Produces: `CalendarConnectionState(permissionGranted: Boolean, calendars: List<ConnectedCalendar>)`와 `val isConnected: Boolean`
- Produces: `CalendarConnectionRepository.getState(): CalendarConnectionState`
- Produces: 순수 함수 `CalendarConnectionEvaluator.evaluate(permissionGranted, calendars)`

- [ ] **Step 1: 연결 판정 실패 테스트 작성**

```kotlin
@Test fun `one readable calendar is connected`() {
    assertTrue(CalendarConnectionEvaluator.evaluate(true, listOf(ConnectedCalendar(1, "내 캘린더"))).isConnected)
}
@Test fun `permission missing or empty calendars is disconnected`() {
    assertFalse(CalendarConnectionEvaluator.evaluate(false, emptyList()).isConnected)
    assertFalse(CalendarConnectionEvaluator.evaluate(true, emptyList()).isConnected)
}
```

- [ ] **Step 2: RED 확인**

Run: `./gradlew testDebugUnitTest --tests "com.smartworkalarm.data.calendar.CalendarConnectionEvaluatorTest"`
Expected: `CalendarConnectionEvaluator` unresolved로 실패.

- [ ] **Step 3: 모델과 CalendarContract 구현**

```kotlin
data class ConnectedCalendar(val id: Long, val displayName: String)
data class CalendarConnectionState(val permissionGranted: Boolean, val calendars: List<ConnectedCalendar>) {
    val isConnected get() = permissionGranted && calendars.isNotEmpty()
}
interface CalendarConnectionRepository { fun getState(): CalendarConnectionState }
```

Repository는 `READ_CALENDAR`을 확인한 후 `Calendars.ACCOUNT_TYPE = com.google`, `VISIBLE = 1`, `SYNC_EVENTS = 1`인 `_ID`, `CALENDAR_DISPLAY_NAME`을 조회한다. `SecurityException`과 null cursor는 연결 안 됨 상태로 반환한다.

- [ ] **Step 4: GREEN 및 회귀 확인**

Run: `./gradlew testDebugUnitTest --tests "com.smartworkalarm.data.calendar.CalendarConnectionEvaluatorTest" testDebugUnitTest`
Expected: 연결 판정 테스트와 기존 단위 테스트 통과.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/smartworkalarm/domain app/src/main/java/com/smartworkalarm/data/calendar app/src/main/java/com/smartworkalarm/di/CalendarModule.kt app/src/test/java/com/smartworkalarm/data/calendar
git commit -m "feat: report connected Google calendars"
```

### Task 2: 다음 날 상태 변화 판정 및 저장

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/domain/model/AlarmPreviewState.kt`
- Create: `app/src/main/java/com/smartworkalarm/data/local/AlarmPreviewEntity.kt`
- Create: `app/src/main/java/com/smartworkalarm/data/local/AlarmPreviewDao.kt`
- Modify: `app/src/main/java/com/smartworkalarm/data/local/WorkAlarmDatabase.kt`
- Modify: `app/src/main/java/com/smartworkalarm/di/DatabaseModule.kt`
- Create: `app/src/main/java/com/smartworkalarm/domain/usecase/CompareAlarmPreviewStateUseCase.kt`
- Test: `app/src/test/java/com/smartworkalarm/domain/usecase/CompareAlarmPreviewStateUseCaseTest.kt`
- Test: `app/src/androidTest/java/com/smartworkalarm/data/local/AlarmPreviewDaoTest.kt`

**Interfaces:**
- Produces: `PreviewDecision.RING`, `SKIP`, `UNKNOWN`
- Produces: `PreviewChange.Skipped(alarm, keyword)`와 `PreviewChange.Resumed(alarm)`
- Produces: DAO `get(alarmId)`, `upsert(entity)`, `delete(alarmId)`

- [ ] **Step 1: 변화 규칙 실패 테스트 작성**

```kotlin
assertNull(compare(previous = null, current = RING))
assertIs<Skipped>(compare(previous = null, current = SKIP))
assertIs<Skipped>(compare(previous = RING, current = SKIP))
assertIs<Resumed>(compare(previous = SKIP, current = RING))
assertNull(compare(previous = SKIP, current = SKIP))
assertNull(compare(previous = SKIP, current = UNKNOWN))
```

- [ ] **Step 2: RED 확인**

Run: `./gradlew testDebugUnitTest --tests "com.smartworkalarm.domain.usecase.CompareAlarmPreviewStateUseCaseTest"`
Expected: preview 타입 미정의로 실패.

- [ ] **Step 3: 최소 판정 코드와 Room v3 마이그레이션 구현**

`alarm_preview_states(alarm_id PRIMARY KEY, decision TEXT, target_date TEXT, updated_at INTEGER)`를 추가하고 DB version을 3으로 올린다. `MIGRATION_2_3`은 `CREATE TABLE IF NOT EXISTS`를 사용하며 destructive migration을 금지한다. `UNKNOWN`은 DAO에 저장하지 않는다.

- [ ] **Step 4: GREEN과 Android 테스트 컴파일 확인**

Run: `./gradlew testDebugUnitTest --tests "com.smartworkalarm.domain.usecase.CompareAlarmPreviewStateUseCaseTest" compileDebugAndroidTestKotlin`
Expected: 단위 테스트 통과, DAO/migration 계측 테스트 컴파일 성공.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/smartworkalarm/domain app/src/main/java/com/smartworkalarm/data/local app/src/main/java/com/smartworkalarm/di/DatabaseModule.kt app/src/test app/src/androidTest
git commit -m "feat: persist next-day alarm preview states"
```

### Task 3: 22시 다음 날 판정 작업과 변화 알림

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/preview/EvaluateNextDayAlarmsUseCase.kt`
- Create: `app/src/main/java/com/smartworkalarm/preview/NextDayPreviewNotificationFactory.kt`
- Create: `app/src/main/java/com/smartworkalarm/preview/NextDayPreviewWorker.kt`
- Test: `app/src/test/java/com/smartworkalarm/preview/EvaluateNextDayAlarmsUseCaseTest.kt`
- Test: `app/src/test/java/com/smartworkalarm/preview/PreviewNotificationTextTest.kt`

**Interfaces:**
- Consumes: `AlarmRepository.getEnabled()`, `CalendarEventSource.getEventsOverlapping(startInclusive, endExclusive)`, preview DAO와 비교 use case
- Produces: `EvaluateNextDayAlarmsUseCase.execute(targetDate, zoneId): List<PreviewChange>`
- Produces: 일반 중요도 채널 `next_day_alarm_changes`

- [ ] **Step 1: 대상 필터와 변화 목록 실패 테스트 작성**

```kotlin
@Test fun `only alarms repeating tomorrow are evaluated`() = runTest { /* 토요일 대상에서 토요일 알람만 조회 */ }
@Test fun `calendar failure returns no changes and preserves state`() = runTest { /* Result.failure */ }
@Test fun `multiple changes produce Korean summary and detail rows`() { /* 2개 변경 */ }
```

- [ ] **Step 2: RED 확인**

Run: `./gradlew testDebugUnitTest --tests "com.smartworkalarm.preview.*"`
Expected: use case와 formatter 미정의로 실패.

- [ ] **Step 3: 판정 및 NotificationCompat 구현**

다음 날 반복 알람을 필터링하고 캘린더 조회가 성공한 경우에만 각 알람의 keyword match를 계산한다. 변화가 비어 있으면 Worker는 notification을 게시하지 않는다. 한 개는 구체 문구, 여러 개는 `BigTextStyle` 목록을 만든다.

- [ ] **Step 4: GREEN 확인**

Run: `./gradlew testDebugUnitTest --tests "com.smartworkalarm.preview.*"`
Expected: 필터, 실패, 변화, 문구 테스트 통과.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/smartworkalarm/preview app/src/test/java/com/smartworkalarm/preview
git commit -m "feat: notify next-day alarm changes"
```

### Task 4: 매일 22시 AlarmManager 예약과 시스템 복구

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/preview/NextDayPreviewScheduler.kt`
- Create: `app/src/main/java/com/smartworkalarm/preview/NextDayPreviewSchedulerImpl.kt`
- Create: `app/src/main/java/com/smartworkalarm/preview/NextDayPreviewReceiver.kt`
- Create: `app/src/main/java/com/smartworkalarm/domain/usecase/CalculateNextPreviewTimeUseCase.kt`
- Modify: `app/src/main/java/com/smartworkalarm/alarm/SystemChangeReceiver.kt`
- Modify: `app/src/main/java/com/smartworkalarm/worker/RescheduleAlarmsWorker.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/java/com/smartworkalarm/domain/usecase/CalculateNextPreviewTimeUseCaseTest.kt`

**Interfaces:**
- Produces: `CalculateNextPreviewTimeUseCase.execute(now, zoneId): Instant`
- Produces: `NextDayPreviewScheduler.scheduleNext(now)`와 고정 request code의 교체 가능한 PendingIntent

- [ ] **Step 1: 22시 경계 실패 테스트 작성**

```kotlin
assertEquals(todayAt22, calculate(todayAt21_59))
assertEquals(tomorrowAt22, calculate(todayAt22_00))
assertEquals(expectedAfterDst, calculate(nowInZone))
```

- [ ] **Step 2: RED 확인**

Run: `./gradlew testDebugUnitTest --tests "com.smartworkalarm.domain.usecase.CalculateNextPreviewTimeUseCaseTest"`
Expected: 계산 use case 미정의로 실패.

- [ ] **Step 3: 예약, receiver, 복구 구현**

`setExactAndAllowWhileIdle`을 우선 사용하고 불가능하면 `setAndAllowWhileIdle`로 대체한다. receiver는 다음 예약을 즉시 갱신하고 unique Work를 enqueue한다. `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, `TIMEZONE_CHANGED`, exact permission 변경 복구 경로에 preview 예약을 추가한다.

- [ ] **Step 4: GREEN과 manifest 컴파일 확인**

Run: `./gradlew testDebugUnitTest --tests "com.smartworkalarm.domain.usecase.CalculateNextPreviewTimeUseCaseTest" assembleDebug`
Expected: 시간 경계 테스트와 debug APK 빌드 통과.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/smartworkalarm/preview app/src/main/java/com/smartworkalarm/domain/usecase app/src/main/java/com/smartworkalarm/alarm/SystemChangeReceiver.kt app/src/main/java/com/smartworkalarm/worker/RescheduleAlarmsWorker.kt app/src/main/AndroidManifest.xml app/src/test
git commit -m "feat: schedule daily alarm preview"
```

### Task 5: 반복 캘린더 연결 안내

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/presentation/calendar/CalendarConnectionPrompt.kt`
- Create: `app/src/main/java/com/smartworkalarm/presentation/calendar/CalendarConnectionViewModel.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/navigation/WorkAlarmNavHost.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/alarmlist/AlarmListScreen.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/alarmedit/AlarmEditScreen.kt`
- Test: `app/src/androidTest/java/com/smartworkalarm/presentation/CalendarConnectionPromptTest.kt`

**Interfaces:**
- Consumes: `CalendarConnectionRepository.getState()`
- Produces: `refresh(entryPoint)`가 매 진입마다 상태를 재조회
- Produces: 권한 요청, 앱 권한 설정, 계정 동기화 안내 intents

- [ ] **Step 1: 팝업 조건 UI 테스트 작성**

```kotlin
composeRule.setContent { CalendarConnectionPrompt(connected = false, ...) }
composeRule.onNodeWithText("캘린더 연결이 필요합니다").assertIsDisplayed()
composeRule.setContent { CalendarConnectionPrompt(connected = true, ...) }
composeRule.onNodeWithText("캘린더 연결이 필요합니다").assertDoesNotExist()
```

- [ ] **Step 2: RED 또는 테스트 컴파일 실패 확인**

Run: `./gradlew compileDebugAndroidTestKotlin`
Expected: prompt composable 미정의로 실패.

- [ ] **Step 3: 안내 팝업과 세 진입점 연결 구현**

`나중에`는 현재 dialog만 닫고 영구 저장하지 않는다. 앱 목록 진입 및 `alarm/{id}` 진입마다 `refresh`한다. permission launcher 결과 후 재조회하며 영구 거부 시 `ACTION_APPLICATION_DETAILS_SETTINGS`, 권한은 있지만 캘린더가 없으면 `ACTION_SYNC_SETTINGS`로 안내한다.

- [ ] **Step 4: GREEN 컴파일 및 기존 UI 회귀 확인**

Run: `./gradlew compileDebugAndroidTestKotlin testDebugUnitTest`
Expected: UI 테스트 소스와 모든 단위 테스트 컴파일/통과.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/smartworkalarm/presentation app/src/androidTest/java/com/smartworkalarm/presentation
git commit -m "feat: prompt until a calendar is connected"
```

### Task 6: 설정, 연결 상태, 사용법 화면과 키워드 설명

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/presentation/settings/SettingsScreen.kt`
- Create: `app/src/main/java/com/smartworkalarm/presentation/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/navigation/WorkAlarmNavHost.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/alarmlist/AlarmListScreen.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/alarmedit/AlarmEditScreen.kt`
- Test: `app/src/androidTest/java/com/smartworkalarm/presentation/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: `CalendarConnectionState`, notification permission, exact alarm permission
- Produces: route `settings`, 연결 캘린더 목록과 정적 도움말

- [ ] **Step 1: 설정 내용 UI 테스트 작성**

```kotlin
composeRule.onNodeWithText("사용법/도움말").assertIsDisplayed()
composeRule.onNodeWithText("여러 키워드는 쉼표(,)로 구분하세요.").assertIsDisplayed()
composeRule.onNodeWithText("연결된 Google 캘린더").assertIsDisplayed()
composeRule.onNodeWithText("Outlook Calendar — 준비 중").assertIsDisplayed()
```

- [ ] **Step 2: RED 확인**

Run: `./gradlew compileDebugAndroidTestKotlin`
Expected: settings composable 미정의로 실패.

- [ ] **Step 3: 설정 route와 도움말 UI 구현**

메인 화면의 설정 버튼에서 이동하며, 연결 상태를 화면 재진입마다 갱신한다. 알람 편집 도움말을 정확히 `여러 키워드는 쉼표(,)로 구분하세요. 일정 제목에 키워드가 하나라도 포함되면 이 알람을 건너뜁니다.`로 변경한다.

- [ ] **Step 4: GREEN 확인**

Run: `./gradlew compileDebugAndroidTestKotlin testDebugUnitTest assembleDebug`
Expected: 설정 UI 테스트 컴파일, 단위 테스트, APK 빌드 통과.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/smartworkalarm/presentation app/src/androidTest/java/com/smartworkalarm/presentation
git commit -m "feat: add calendar settings and help"
```

### Task 7: 전체 검증과 V1.2 APK

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `docs/testing/v1-device-checklist.md`
- Create artifact: `artifacts/WorkAlarm-v1.2-debug.apk`

**Interfaces:**
- Consumes: Tasks 1–6의 완성된 앱
- Produces: versionCode `3`, versionName `1.2` APK

- [ ] **Step 1: 버전과 실제 기기 체크리스트 갱신**

`versionCode = 3`, `versionName = "1.2"`로 변경하고 22시 변화 안내, 복귀 안내, 미연결 반복 팝업, 연결 후 팝업 중단, 설정 도움말, 22시 이후 최종 알람 판정을 체크리스트에 추가한다.

- [ ] **Step 2: 전체 클린 검증**

Run: `./gradlew --no-daemon clean lintDebug testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug`
Expected: BUILD SUCCESSFUL, unit test failures `0`, lint errors `0`, APK 생성.

- [ ] **Step 3: 산출물 복사와 해시 확인**

```powershell
Copy-Item app/build/outputs/apk/debug/app-debug.apk artifacts/WorkAlarm-v1.2-debug.apk
Get-FileHash -Algorithm SHA256 artifacts/WorkAlarm-v1.2-debug.apk
```

- [ ] **Step 4: 최종 커밋**

```bash
git add app/build.gradle.kts docs/testing/v1-device-checklist.md
git commit -m "test: verify Work Alarm v1.2"
```
