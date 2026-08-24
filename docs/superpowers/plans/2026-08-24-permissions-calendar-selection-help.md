# 필수 권한·캘린더 선택·도움말 개편 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 잠금화면 알람을 최우선으로 유지하면서 필수 권한을 자동 점검하고, 계정별로 펼친 Google 캘린더 중 선택한 캘린더만 알람 판단에 사용하며, 설정/도움말을 Night Pulse UI로 개편한다.

**Architecture:** OS 권한 판정은 순수 도메인 모델과 Android 어댑터로 분리하고 앱 루트에서 한 번에 조정한다. 캘린더 목록과 선택 상태는 별도 저장소가 관리하며 `CalendarContractEventSource`는 선택 저장소가 제공한 ID만 조회한다. 설정 ViewModel이 권한·캘린더·도움말 화면 상태를 결합한다.

**Tech Stack:** Kotlin, Android CalendarContract, SharedPreferences, Jetpack Compose Material 3, Hilt, JUnit, Compose UI Test

**Spec:** `docs/superpowers/specs/2026-08-24-permissions-calendar-selection-help-design.md`

## Global Constraints

- Android Calendar Provider에 동기화된 `ACCOUNT_TYPE = com.google`, `VISIBLE = 1`, `SYNC_EVENTS = 1` 캘린더만 지원한다.
- Google API, OAuth, 외부 서버, 유료 API를 추가하지 않는다.
- 최초에는 발견한 Google 캘린더를 모두 선택하고 이후 사용자 선택을 로컬에 유지한다.
- 일반 알람 조회 실패 시 알람을 울리고, 교대 알람 조회 실패 시 비상 기본 근무 알람만 울리는 기존 안전 정책을 유지한다.
- 잠금화면 전체화면 알람 UI와 해제/다시 울림 동작을 회귀시키지 않는다.

---

### Task 1: 필수 권한 통합 점검

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/permissions/RequiredPermission.kt`
- Create: `app/src/main/java/com/smartworkalarm/permissions/RequiredPermissionsController.kt`
- Create: `app/src/main/java/com/smartworkalarm/presentation/permissions/RequiredPermissionsPrompt.kt`
- Modify: `app/src/main/java/com/smartworkalarm/MainActivity.kt`
- Test: `app/src/test/java/com/smartworkalarm/permissions/RequiredPermissionTest.kt`

**Interfaces:**
- Produces: `enum class RequiredPermission { CALENDAR, NOTIFICATIONS, EXACT_ALARM, FULL_SCREEN_ALARM }`
- Produces: `RequiredPermissionsController.missing(): List<RequiredPermission>` and `settingsIntent(permission): Intent?`
- Produces: `RequiredPermissionsPrompt(missing, onRequestRuntime, onOpenSettings, onLater)`

- [ ] **Step 1: Write failing permission-order tests**

```kotlin
@Test fun `Android 14 누락 권한을 실행 가능한 순서로 반환한다`() {
    val missing = RequiredPermission.missingFor(
        sdkInt = 34,
        calendarGranted = false,
        notificationsGranted = false,
        exactAlarmGranted = false,
        fullScreenAlarmGranted = false,
    )
    assertEquals(
        listOf(CALENDAR, NOTIFICATIONS, EXACT_ALARM, FULL_SCREEN_ALARM),
        missing,
    )
}

@Test fun `낮은 Android 버전에는 존재하지 않는 특수 권한을 제외한다`() {
    assertEquals(
        listOf(CALENDAR),
        RequiredPermission.missingFor(30, false, false, false, false),
    )
}
```

- [ ] **Step 2: Run RED**

Run: `./gradlew testDebugUnitTest --tests com.smartworkalarm.permissions.RequiredPermissionTest`

Expected: FAIL because `RequiredPermission` does not exist.

- [ ] **Step 3: Implement permission model and Android controller**

```kotlin
enum class RequiredPermission {
    CALENDAR, NOTIFICATIONS, EXACT_ALARM, FULL_SCREEN_ALARM;

    companion object {
        fun missingFor(
            sdkInt: Int,
            calendarGranted: Boolean,
            notificationsGranted: Boolean,
            exactAlarmGranted: Boolean,
            fullScreenAlarmGranted: Boolean,
        ): List<RequiredPermission> = buildList {
            if (!calendarGranted) add(CALENDAR)
            if (sdkInt >= 33 && !notificationsGranted) add(NOTIFICATIONS)
            if (sdkInt >= 31 && !exactAlarmGranted) add(EXACT_ALARM)
            if (sdkInt >= 34 && !fullScreenAlarmGranted) add(FULL_SCREEN_ALARM)
        }
    }
}
```

The controller must use `ContextCompat.checkSelfPermission`, `AlarmManager.canScheduleExactAlarms()`, and `NotificationManager.canUseFullScreenIntent()`. Special permission settings intents must fall back to `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` when they cannot be resolved.

- [ ] **Step 4: Replace the single full-screen dialog with one unified prompt**

Use Activity Result permission launchers for `READ_CALENDAR` and `POST_NOTIFICATIONS`. For exact-alarm and full-screen permission, open the controller-provided settings Intent. Re-run `missing()` in `onResume`; show the prompt again on the next app launch while any item remains.

- [ ] **Step 5: Run GREEN and commit**

Run: `./gradlew testDebugUnitTest --tests com.smartworkalarm.permissions.RequiredPermissionTest compileDebugKotlin`

Commit: `feat: check all required alarm permissions`

---

### Task 2: 계정 정보가 포함된 캘린더 목록과 선택 저장

**Files:**
- Modify: `app/src/main/java/com/smartworkalarm/domain/model/CalendarConnectionState.kt`
- Create: `app/src/main/java/com/smartworkalarm/domain/repository/CalendarSelectionRepository.kt`
- Create: `app/src/main/java/com/smartworkalarm/data/calendar/SharedPreferencesCalendarSelectionRepository.kt`
- Modify: `app/src/main/java/com/smartworkalarm/data/calendar/CalendarContractConnectionRepository.kt`
- Modify: `app/src/main/java/com/smartworkalarm/di/AlarmModule.kt`
- Test: `app/src/test/java/com/smartworkalarm/data/calendar/CalendarSelectionRepositoryTest.kt`

**Interfaces:**
- Produces: `ConnectedCalendar(id, displayName, accountName, accountType, selected)`
- Produces: `CalendarSelectionRepository.reconcile(availableIds): Set<Long>`, `setSelected(id, selected)`, `setAccountSelected(ids, selected)`

- [ ] **Step 1: Write failing reconciliation tests**

```kotlin
@Test fun `첫 발견 캘린더는 모두 선택한다`() {
    assertEquals(setOf(1L, 2L), repository.reconcile(setOf(1L, 2L)))
}

@Test fun `사용자가 해제한 캘린더는 다음 조회에도 해제 상태다`() {
    repository.reconcile(setOf(1L, 2L))
    repository.setSelected(2L, false)
    assertEquals(setOf(1L), repository.reconcile(setOf(1L, 2L)))
}

@Test fun `새 캘린더는 선택하고 사라진 ID는 제거한다`() {
    repository.reconcile(setOf(1L, 2L))
    repository.setSelected(2L, false)
    assertEquals(setOf(1L, 3L), repository.reconcile(setOf(1L, 3L)))
}
```

- [ ] **Step 2: Run RED**

Run: `./gradlew testDebugUnitTest --tests com.smartworkalarm.data.calendar.CalendarSelectionRepositoryTest`

Expected: FAIL because the selection repository is missing.

- [ ] **Step 3: Implement local selection and calendar catalog projection**

Persist `known_ids` and `selected_ids` as string sets in a dedicated SharedPreferences file. Query `_ID`, `CALENDAR_DISPLAY_NAME`, `ACCOUNT_NAME`, and `ACCOUNT_TYPE`; reconcile discovered IDs before constructing `ConnectedCalendar` values.

- [ ] **Step 4: Run GREEN and commit**

Run: `./gradlew testDebugUnitTest --tests com.smartworkalarm.data.calendar.CalendarSelectionRepositoryTest`

Commit: `feat: select calendars by Google account`

---

### Task 3: 선택된 캘린더만 모든 알람 판단에 적용

**Files:**
- Modify: `app/src/main/java/com/smartworkalarm/data/calendar/CalendarContractEventSource.kt`
- Modify: `app/src/main/java/com/smartworkalarm/di/AlarmModule.kt`
- Modify: `app/src/test/java/com/smartworkalarm/data/calendar/CalendarContractEventSourceTest.kt`
- Test: `app/src/test/java/com/smartworkalarm/data/calendar/SelectedCalendarEventSourceTest.kt`

**Interfaces:**
- Consumes: `CalendarSelectionRepository.reconcile(availableIds): Set<Long>`
- Produces: event queries whose `CALENDAR_ID IN (...)` arguments contain selected IDs only

- [ ] **Step 1: Write failing selected-ID tests**

```kotlin
@Test fun `선택한 캘린더 ID만 이벤트 조회에 전달한다`() = runTest {
    var queriedIds = emptyList<Long>()
    val source = CalendarContractEventSource(
        calendarIds = { listOf(10L, 20L) },
        selectedCalendarIds = { setOf(20L) },
        eventQuery = { ids, _, _ -> queriedIds = ids; emptyList() },
    )
    source.getEventsOverlapping(start, end).getOrThrow()
    assertEquals(listOf(20L), queriedIds)
}

@Test fun `선택 캘린더가 없으면 읽기 실패로 반환한다`() = runTest {
    val result = sourceWithNoSelection.getEventsOverlapping(start, end)
    assertTrue(result.isFailure)
}
```

- [ ] **Step 2: Run RED**

Run: `./gradlew testDebugUnitTest --tests com.smartworkalarm.data.calendar.CalendarContractEventSourceTest`

Expected: FAIL because the event source has no selected-ID boundary.

- [ ] **Step 3: Filter IDs at the event-source boundary**

Intersect readable provider IDs with selected IDs before querying. Preserve the existing failure result for no readable/selected calendars so ordinary and shift coordinators retain their existing fallback behavior.

- [ ] **Step 4: Run alarm safety regressions and commit**

Run: `./gradlew testDebugUnitTest --tests com.smartworkalarm.data.calendar.CalendarContractEventSourceTest --tests com.smartworkalarm.alarm.AlarmExecutionCoordinatorTest --tests com.smartworkalarm.alarm.ShiftAlarmExecutionCoordinatorTest`

Commit: `feat: filter alarm events by selected calendars`

---

### Task 4: Night Pulse 설정·도움말과 계정별 선택 UI

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/presentation/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/settings/SettingsScreen.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/navigation/WorkAlarmNavHost.kt`
- Test: `app/src/test/java/com/smartworkalarm/presentation/settings/SettingsViewModelTest.kt`
- Test: `app/src/androidTest/java/com/smartworkalarm/presentation/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: permission controller, calendar catalog, selection repository
- Produces: `SettingsUiState(permissionRows, accountGroups, helpSections, message)`
- Produces: account and calendar toggle events persisted immediately

- [ ] **Step 1: Write failing grouping and toggle tests**

```kotlin
@Test fun `같은 계정 캘린더를 하나의 펼침 그룹으로 만든다`() {
    val groups = groupCalendars(listOf(
        calendar(1, "회사 일정", "work@gmail.com", true),
        calendar(2, "근무표", "work@gmail.com", false),
        calendar(3, "개인", "me@gmail.com", true),
    ))
    assertEquals(listOf("me@gmail.com", "work@gmail.com"), groups.map { it.accountName })
    assertEquals(2, groups.first { it.accountName == "work@gmail.com" }.calendars.size)
}
```

- [ ] **Step 2: Run RED**

Run: `./gradlew testDebugUnitTest --tests com.smartworkalarm.presentation.settings.SettingsViewModelTest`

Expected: FAIL because the account-group state is missing.

- [ ] **Step 3: Implement ViewModel and card UI**

Build these `NightPulseSection` cards in order: `연결 및 권한`, `사용 중인 캘린더`, `일반 알람`, `캘린더 자동 건너뛰기`, `교대근무`, `22시 확인`, `공휴일·알람음·다시 울림`. Account headers must show selected count and expand/collapse. Calendar rows must have clear selected/unselected checkbox states.

- [ ] **Step 4: Add Compose assertions**

```kotlin
composeRule.onNodeWithText("사용 중인 캘린더").assertIsDisplayed()
composeRule.onNodeWithText("work@gmail.com").performClick()
composeRule.onNodeWithText("근무표").assertIsDisplayed()
composeRule.onNodeWithText("연결 및 권한").assertIsDisplayed()
```

- [ ] **Step 5: Run GREEN and commit**

Run: `./gradlew testDebugUnitTest --tests com.smartworkalarm.presentation.settings.SettingsViewModelTest compileDebugAndroidTestKotlin`

Commit: `feat: redesign help and calendar settings`

---

### Task 5: 잠금화면 회귀 검증과 APK

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `docs/testing/v1-device-checklist.md`
- Verify: `app/src/main/java/com/smartworkalarm/alarm/AlarmActivity.kt`
- Verify: `app/src/main/java/com/smartworkalarm/alarm/AlarmNotificationFactory.kt`
- Verify: `app/src/androidTest/java/com/smartworkalarm/alarm/AlarmRingingScreenTest.kt`

**Interfaces:**
- Consumes: all prior tasks
- Produces: `artifacts/WorkAlarm-v1.7-debug.apk`

- [ ] **Step 1: Add the real-device checklist**

Record checks for locked screen off/on, unlocked screen, dismiss, configured snooze and repeated snooze screen, Android 14 full-screen permission denied/granted, runtime permissions denied/granted, four Google accounts, partial calendar selection, and 22:00 selected-calendar filtering.

- [ ] **Step 2: Confirm lock-screen configuration**

Verify the manifest contains `USE_FULL_SCREEN_INTENT`, `AlarmActivity` has `showWhenLocked` and `turnScreenOn`, and the ringing notification uses a public maximum-priority alarm category with `setFullScreenIntent(..., true)` and a content intent to the same activity.

- [ ] **Step 3: Run the complete verification**

Run: `./gradlew clean lintDebug testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug`

Expected: `BUILD SUCCESSFUL`, zero unit failures, zero lint errors, and Android test Kotlin compilation success.

- [ ] **Step 4: Copy and hash APK**

```powershell
Copy-Item app\build\outputs\apk\debug\app-debug.apk artifacts\WorkAlarm-v1.7-debug.apk -Force
Get-FileHash -Algorithm SHA256 artifacts\WorkAlarm-v1.7-debug.apk
```

- [ ] **Step 5: Commit release metadata**

Commit: `test: verify permissions and calendar selection release`
