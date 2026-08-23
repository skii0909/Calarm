# Shift Alarm Groups Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 캘린더의 정확한 근무 형태 제목에 따라 교대 그룹에서 알람 하나만 실행하고, 휴가·공휴일 우선순위와 상태 변경 시에만 발생하는 22시 알림을 제공한다.

**Architecture:** 기존 일반 `Alarm` 흐름은 변경하지 않고 Room에 교대 그룹과 근무 형태 알람을 별도 aggregate로 추가한다. 순수 Kotlin `ShiftDayEvaluator`가 캘린더 조회 결과를 `Matched`, `None`, `Conflict`, `ReadFailure`로 판정하고, 실제 실행 coordinator와 22시 preview가 같은 결과 모델을 사용한다.

**Tech Stack:** Kotlin, Room, CalendarContract, AlarmManager, WorkManager, Jetpack Compose Material 3, Hilt, JUnit4

**Spec:** `docs/superpowers/specs/2026-08-23-shift-alarm-groups-design.md`

## Global Constraints

- 일반 알람과 기존 부분 키워드 건너뛰기 동작을 변경하지 않는다.
- 교대 근무 제목은 trim 후 전체 문자열이 일치해야 하며 영문 대소문자만 무시한다.
- 휴가와 알람별 공휴일 건너뛰기는 교대 근무 형태보다 우선한다.
- 교대 그룹의 영구 enabled 값을 날짜 판정 결과로 변경하지 않는다.
- 같은 그룹에서 서로 다른 근무 형태가 둘 이상 발견되면 모든 그룹 알람을 건너뛴다.
- 22시 알림은 상태 또는 충돌 내용이 변경됐을 때만 보낸다.
- 22시 이후 변경은 실제 알람 시각의 최종 캘린더 조회에 반영한다.
- 캘린더 조회 실패 시 지정된 비상 기본 알람만 실행한다.
- 서버, 외부 API, 원격 동기화 코드를 추가하지 않는다.

---

### Task 1: 교대 그룹 모델과 Room 4→5

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/domain/model/ShiftAlarmGroup.kt`
- Create: `app/src/main/java/com/smartworkalarm/domain/model/ShiftTypeAlarm.kt`
- Create: `app/src/main/java/com/smartworkalarm/data/local/ShiftAlarmGroupEntity.kt`
- Create: `app/src/main/java/com/smartworkalarm/data/local/ShiftTypeAlarmEntity.kt`
- Create: `app/src/main/java/com/smartworkalarm/data/local/ShiftAlarmDao.kt`
- Create: `app/src/main/java/com/smartworkalarm/data/local/ShiftAlarmMapper.kt`
- Create: `app/src/main/java/com/smartworkalarm/data/local/ShiftPreviewEntity.kt`
- Create: `app/src/main/java/com/smartworkalarm/data/local/ShiftPreviewDao.kt`
- Modify: `app/src/main/java/com/smartworkalarm/data/local/WorkAlarmDatabase.kt`
- Modify: `app/src/main/java/com/smartworkalarm/di/DatabaseModule.kt`
- Test: `app/src/test/java/com/smartworkalarm/data/local/ShiftAlarmMapperTest.kt`
- Test: `app/src/androidTest/java/com/smartworkalarm/data/local/AlarmMigrationTest.kt`

**Interfaces:**
- Produces: `ShiftAlarmGroup(id, name, enabled, emergencyShiftTypeId)`
- Produces: `ShiftTypeAlarm(id, groupId, name, exactTitles, hour, minute, repeatDaysMask, enabled, ringtoneUri, gradualVolume, snoozeMinutes, skipOnPublicHoliday)`
- Produces: `ShiftAlarmDao.observeGroupsWithTypes()`, `getGroupWithTypes(groupId)`, `upsertGroup`, `upsertShiftType`, `deleteGroup`, `deleteShiftType`
- Produces: `ShiftGroupWithTypes(group, shiftTypes)` Room relation과 `ShiftPreviewDao.get/upsert`
- Produces: `MIGRATION_4_5`

- [ ] **Step 1: mapper와 migration 실패 테스트 작성**

```kotlin
@Test fun `shift type mapper preserves exact title aliases and alarm settings`() {
    val value = ShiftTypeAlarm(2, 1, "야간", listOf("야간", "야간근무"), 18, 0, 127, true, null, true, 12, false)
    assertEquals(value, value.toEntity().toDomain())
}
```

Migration test는 version 4 DB를 열고 migration 후 `shift_alarm_groups`, `shift_type_alarms`, `shift_preview_states`가 존재하며 `emergency_shift_type_id`가 nullable인지 확인한다.

- [ ] **Step 2: RED 확인**

Run: `./gradlew testDebugUnitTest --tests "*ShiftAlarmMapperTest" compileDebugAndroidTestKotlin`
Expected: 신규 모델, mapper, migration 미정의로 실패.

- [ ] **Step 3: 모델, DAO, mapper와 migration 구현**

`exact_titles`는 기존 keyword 저장 방식과 동일하게 JSON 의존성 없이 newline 구분 문자열로 저장하고 mapper에서 trim·빈 값 제거·중복 제거한다. DB version을 5로 올리고 세 테이블과 group index를 만든다.

- [ ] **Step 4: GREEN 확인**

Run: `./gradlew testDebugUnitTest --tests "*ShiftAlarmMapperTest" compileDebugAndroidTestKotlin`
Expected: mapper 통과, migration Android test 컴파일 성공.

- [ ] **Step 5: 커밋**

```bash
git add app/src app/schemas
git commit -m "feat: store shift alarm groups"
```

### Task 2: 정확한 제목 matcher와 날짜 판정 엔진

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/domain/shift/ExactShiftTitleMatcher.kt`
- Create: `app/src/main/java/com/smartworkalarm/domain/shift/ShiftDayEvaluation.kt`
- Create: `app/src/main/java/com/smartworkalarm/domain/shift/ShiftDayEvaluator.kt`
- Test: `app/src/test/java/com/smartworkalarm/domain/shift/ExactShiftTitleMatcherTest.kt`
- Test: `app/src/test/java/com/smartworkalarm/domain/shift/ShiftDayEvaluatorTest.kt`

**Interfaces:**
- Produces: `ExactShiftTitleMatcher.matches(title: String?, aliases: List<String>): Boolean`
- Produces sealed `ShiftDayEvaluation.Matched(shiftTypeId, name)`, `None`, `Conflict(matches)`, `ReadFailure`
- Produces: `ShiftDayEvaluator.evaluate(types, eventsResult): ShiftDayEvaluation`

- [ ] **Step 1: exact match와 네 상태 실패 테스트 작성**

```kotlin
@Test fun `exact matcher ignores surrounding whitespace but rejects partial title`() {
    assertTrue(ExactShiftTitleMatcher.matches("  야간  ", listOf("야간")))
    assertFalse(ExactShiftTitleMatcher.matches("야간 근무 후 회식", listOf("야간")))
}

@Test fun `two different shift types are conflict`() {
    val result = evaluator.evaluate(listOf(day, night), Result.success(events("주간", "야간")))
    assertTrue(result is ShiftDayEvaluation.Conflict)
}
```

- [ ] **Step 2: RED 확인**

Run: `./gradlew testDebugUnitTest --tests "com.smartworkalarm.domain.shift.*"`
Expected: matcher와 evaluator 미정의로 실패.

- [ ] **Step 3: 최소 판정 구현**

이벤트 제목을 각 근무 형태의 aliases와 전체 일치시키고 distinct shift type 기준으로 0개면 `None`, 1개면 `Matched`, 2개 이상이면 `Conflict`, `Result.failure`면 `ReadFailure`를 반환한다. 같은 근무 형태의 별칭 이벤트가 여러 개 있어도 conflict가 아니다.

- [ ] **Step 4: GREEN 확인**

Run: `./gradlew testDebugUnitTest --tests "com.smartworkalarm.domain.shift.*"`
Expected: exact, partial rejection, none, one match, duplicate same type, conflict, failure 모두 통과.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/smartworkalarm/domain/shift app/src/test/java/com/smartworkalarm/domain/shift
git commit -m "feat: evaluate calendar shift titles"
```

### Task 3: 교대 알람 예약과 실제 실행 최종 판정

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/domain/repository/ShiftAlarmRepository.kt`
- Create: `app/src/main/java/com/smartworkalarm/data/repository/ShiftAlarmRepositoryImpl.kt`
- Create: `app/src/main/java/com/smartworkalarm/alarm/ShiftAlarmExecutionCoordinator.kt`
- Modify: `app/src/main/java/com/smartworkalarm/alarm/AlarmBroadcastReceiver.kt`
- Modify: `app/src/main/java/com/smartworkalarm/alarm/AlarmSchedulerImpl.kt`
- Modify: `app/src/main/java/com/smartworkalarm/worker/RescheduleAlarmsUseCase.kt`
- Modify: `app/src/main/java/com/smartworkalarm/di/AlarmModule.kt`
- Test: `app/src/test/java/com/smartworkalarm/alarm/ShiftAlarmExecutionCoordinatorTest.kt`

**Interfaces:**
- Produces: repository CRUD/observe APIs over Task 1 DAO
- Produces: `ShiftAlarmExecutionCoordinator.execute(shiftTypeId, isSnooze, now): ExecutionOutcome`
- Consumes: Task 2 `ShiftDayEvaluator`

- [ ] **Step 1: 실행 우선순위 실패 테스트 작성**

```kotlin
@Test fun `matched night alarm rings and day alarm skips`() = runTest {
    assertEquals(Ringing, coordinator(result = Matched(night.id, "야간")).execute(night.id, false, now))
    assertEquals(SkippedForShift, coordinator(result = Matched(night.id, "야간")).execute(day.id, false, now))
}

@Test fun `read failure rings only emergency shift alarm`() = runTest {
    assertEquals(RingingWithCalendarWarning, coordinator(result = ReadFailure, emergencyId = day.id).execute(day.id, false, now))
    assertEquals(SkippedForShift, coordinator(result = ReadFailure, emergencyId = day.id).execute(night.id, false, now))
}
```

```kotlin
@Test fun `vacation and public holiday skip before matched shift`() = runTest {
    assertEquals(SkippedForVacation, coordinator(result = Matched(night.id, "야간"), vacation = true).execute(night.id, false, now))
    assertEquals(SkippedForVacation, coordinator(result = Matched(night.id, "야간"), publicHoliday = true).execute(night.id, false, holidayNow))
}
```

- [ ] **Step 2: RED 확인**

Run: `./gradlew testDebugUnitTest --tests "*ShiftAlarmExecutionCoordinatorTest"`
Expected: coordinator와 outcome 미정의로 실패.

- [ ] **Step 3: coordinator와 scheduler routing 구현**

모든 enabled shift type alarm을 독립 exact alarm으로 예약하되 수신 시 날짜 전체 캘린더를 재조회한다. 일반 alarm ID와 충돌하지 않도록 PendingIntent action과 request code namespace를 분리한다. 다음 반복 occurrence는 판정 전에 예약한다.

- [ ] **Step 4: GREEN 및 회귀 확인**

Run: `./gradlew testDebugUnitTest --tests "*AlarmExecutionCoordinatorTest" --tests "*ShiftAlarmExecutionCoordinatorTest" assembleDebug`
Expected: 일반 알람 회귀와 교대 matched/none/conflict/failure/휴가/공휴일 테스트 통과.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/smartworkalarm/alarm app/src/main/java/com/smartworkalarm/data app/src/main/java/com/smartworkalarm/domain app/src/main/java/com/smartworkalarm/worker app/src/test
git commit -m "feat: execute calendar matched shift alarms"
```

### Task 4: 22시 교대 상태 변경 preview

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/preview/EvaluateNextDayShiftGroupsUseCase.kt`
- Create: `app/src/main/java/com/smartworkalarm/preview/ShiftPreviewNotificationText.kt`
- Modify: `app/src/main/java/com/smartworkalarm/preview/NextDayPreviewWorker.kt`
- Modify: `app/src/main/java/com/smartworkalarm/preview/NextDayPreviewNotificationFactory.kt`
- Test: `app/src/test/java/com/smartworkalarm/preview/EvaluateNextDayShiftGroupsUseCaseTest.kt`
- Test: `app/src/test/java/com/smartworkalarm/preview/ShiftPreviewNotificationTextTest.kt`

**Interfaces:**
- Produces canonical state key: `MATCHED:<shiftTypeId>`, `NONE`, `CONFLICT:<sortedIds>`, `READ_FAILURE`
- Produces: `ShiftPreviewChange(group, previousKey, currentEvaluation)` only when keys differ
- Consumes: Task 2 evaluator and Task 1 repository

- [ ] **Step 1: 상태 비교와 문구 실패 테스트 작성**

```kotlin
@Test fun `same night shift on consecutive days emits no change`() = runTest {
    previews.save(group.id, "MATCHED:2")
    assertTrue(useCase.execute(today, tomorrowEvents("야간")).isEmpty())
}

@Test fun `day and night conflict requests calendar confirmation`() {
    assertEquals(
        "내일 주간과 야간 일정이 함께 등록되어 있습니다. 캘린더 근무 일정을 확인해 주세요.",
        ShiftPreviewNotificationText.row(conflict),
    )
}
```

- [ ] **Step 2: RED 확인**

Run: `./gradlew testDebugUnitTest --tests "*ShiftPreview*" --tests "*EvaluateNextDayShiftGroupsUseCaseTest"`
Expected: preview state와 formatter 미정의로 실패.

- [ ] **Step 3: preview 저장·비교·notification 통합 구현**

오늘과 내일을 같은 evaluator로 평가하고 canonical key가 다를 때만 change를 만든다. `Conflict`는 충돌 ID 집합이 달라진 경우에만 재알림한다. 기존 일반 알람 changes와 교대 changes를 한 번의 22시 시스템 알림에 합친다.

- [ ] **Step 4: GREEN 확인**

Run: `./gradlew testDebugUnitTest --tests "com.smartworkalarm.preview.*"`
Expected: 야간→야간 무알림, 야간→주간, 시작, 종료, vacation, conflict, conflict 지속, read failure 문구 통과.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/smartworkalarm/preview app/src/test/java/com/smartworkalarm/preview
git commit -m "feat: notify shift changes at 22"
```

### Task 5: 교대 그룹 생성·편집 화면

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/presentation/shift/ShiftGroupListViewModel.kt`
- Create: `app/src/main/java/com/smartworkalarm/presentation/shift/ShiftGroupEditViewModel.kt`
- Create: `app/src/main/java/com/smartworkalarm/presentation/shift/ShiftGroupScreen.kt`
- Create: `app/src/main/java/com/smartworkalarm/presentation/shift/ShiftTypeEditScreen.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/alarmlist/AlarmListScreen.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/navigation/WorkAlarmNavHost.kt`
- Test: `app/src/test/java/com/smartworkalarm/presentation/shift/ShiftGroupEditViewModelTest.kt`
- Test: `app/src/androidTest/java/com/smartworkalarm/presentation/ShiftGroupScreenTest.kt`

**Interfaces:**
- Produces routes `shift-group/{groupId}` and `shift-type/{groupId}/{shiftTypeId}`
- Produces validated group save: nonblank name, at least two shift types, unique normalized aliases, emergency type belonging to group
- Consumes: repository from Task 3

- [ ] **Step 1: validation과 핵심 화면 실패 테스트 작성**

```kotlin
@Test fun `group requires emergency type from its own shift types`() = runTest {
    viewModel.save(group(emergencyShiftTypeId = 99))
    assertEquals("비상 기본 알람을 선택하세요", viewModel.state.value.emergencyError)
}
```

Compose test는 `교대 알람 그룹`, `오늘 근무`, `비상 기본 알람`, `정확히 일치할 캘린더 제목`이 표시되는지 확인한다.

- [ ] **Step 2: RED 확인**

Run: `./gradlew testDebugUnitTest --tests "*ShiftGroupEditViewModelTest" compileDebugAndroidTestKotlin`
Expected: view model과 composable 미정의로 실패.

- [ ] **Step 3: Night Pulse UI와 navigation 구현**

메인에 일반 알람과 분리된 교대 그룹 section을 추가한다. 그룹 상세에는 오늘 상태 card, calendar warning, shift type cards와 추가 button을 둔다. 별칭 입력 설명에 `제목 전체가 일치할 때만 적용됩니다`를 표시하고 비상 기본 알람을 dropdown으로 선택한다.

- [ ] **Step 4: GREEN과 화면 컴파일 확인**

Run: `./gradlew testDebugUnitTest --tests "*Shift*ViewModelTest" compileDebugAndroidTestKotlin assembleDebug`
Expected: validation 테스트, Compose 테스트 컴파일, APK build 성공.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/smartworkalarm/presentation app/src/test/java/com/smartworkalarm/presentation app/src/androidTest/java/com/smartworkalarm/presentation
git commit -m "feat: manage shift alarm groups"
```

### Task 6: 도움말, 복구와 전체 검증

**Files:**
- Modify: `app/src/main/java/com/smartworkalarm/presentation/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/smartworkalarm/worker/RescheduleAlarmsUseCase.kt`
- Modify: `app/build.gradle.kts`
- Modify: `docs/testing/v1-device-checklist.md`
- Create artifact: `artifacts/WorkAlarm-v1.5-debug.apk`

**Interfaces:**
- Produces: versionCode `8`, versionName `1.5`
- Ensures: reboot/timezone/app-update rescheduling includes shift type alarms

- [ ] **Step 1: 도움말과 기기 체크리스트 갱신**

도움말에 exact title 예시, partial rejection, no-shift, conflict, emergency fallback, 22시 change-only 알림을 설명한다. 체크리스트에 주간→야간, 동일 야간 무알림, conflict, read failure fallback, 22시 이후 변경을 추가한다.

- [ ] **Step 2: 전체 검증 실행**

Run: `./gradlew --no-daemon clean lintDebug testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug`
Expected: BUILD SUCCESSFUL, unit failure 0, lint error 0, Android test sources compile.

- [ ] **Step 3: APK와 hash 생성**

```powershell
$target = 'artifacts/WorkAlarm-v1.5-debug.apk'
Copy-Item -LiteralPath 'app/build/outputs/apk/debug/app-debug.apk' -Destination $target -Force
Get-FileHash -Algorithm SHA256 -LiteralPath $target
```

- [ ] **Step 4: 최종 커밋**

```bash
git add app/build.gradle.kts docs/testing/v1-device-checklist.md
git commit -m "test: verify Work Alarm v1.5 shift groups"
```
