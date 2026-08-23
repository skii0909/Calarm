# Work Alarm V1.3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 알람별 공휴일 건너뛰기, 점증 볼륨, 5~30분 다시 울림, 앱 전용 추천 사운드와 Night Pulse UI를 서버 없이 제공한다.

**Architecture:** Room의 알람 설정을 확장하고 순수 Kotlin 공휴일·볼륨·스누즈 규칙을 use case로 격리한다. 기존 알람 실행과 22시 preview 흐름은 공통 `AlarmDayEvaluator`의 사유 목록을 사용하며, 추천 음원은 APK raw resource와 정적 catalog로 제공한다.

**Tech Stack:** Kotlin, Room, AlarmManager, CalendarContract, MediaPlayer, Jetpack Compose Material 3, Hilt, JUnit4

**Spec:** `docs/superpowers/specs/2026-08-23-alarm-controls-holidays-and-night-pulse-design.md`

## Global Constraints

- 서버, 외부 API, 원격 설정, 음원 다운로드를 추가하지 않는다.
- 대한민국 공휴일 내장 범위는 2026-01-01~2031-12-31이다.
- 추천 사운드는 외부 음원을 샘플링하지 않은 앱 전용 원본만 포함한다.
- V1.2의 캘린더 키워드, 22시 한 번 알림, 실제 알람 시각 최종 확인과 fail-open을 유지한다.
- 알람의 `enabled` 값은 공휴일·캘린더 결과로 변경하지 않는다.
- 기존 추천 사운드 resource 이름은 이후 버전에서 삭제하지 않는다.
- 모든 동작 변경은 실패하는 테스트를 먼저 확인한다.

---

### Task 1: 알람 설정 모델과 Room 3→4

**Files:**
- Modify: `app/src/main/java/com/smartworkalarm/domain/model/Alarm.kt`
- Modify: `app/src/main/java/com/smartworkalarm/data/local/AlarmEntity.kt`
- Modify: `app/src/main/java/com/smartworkalarm/data/local/AlarmEntityMapper.kt`
- Modify: `app/src/main/java/com/smartworkalarm/data/local/WorkAlarmDatabase.kt`
- Modify: `app/src/main/java/com/smartworkalarm/di/DatabaseModule.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/alarmedit/AlarmEditViewModel.kt`
- Test: `app/src/test/java/com/smartworkalarm/data/local/AlarmEntityMapperTest.kt`
- Test: `app/src/test/java/com/smartworkalarm/presentation/alarmedit/AlarmEditViewModelTest.kt`
- Test: `app/src/androidTest/java/com/smartworkalarm/data/local/AlarmMigrationTest.kt`

**Interfaces:**
- Produces: `Alarm.skipOnPublicHoliday: Boolean`, `gradualVolume: Boolean`, `snoozeMinutes: Int`
- Produces: `MIGRATION_3_4`

- [ ] **Step 1: mapper와 편집 상태 실패 테스트 작성**

```kotlin
val alarm = Alarm(1, "출근", 7, 0, 31, true, true,
    skipOnPublicHoliday = true, gradualVolume = true, snoozeMinutes = 12)
assertEquals(alarm, alarm.toEntity().toDomain())
viewModel.onSnoozeMinutesChanged(30)
assertEquals(30, viewModel.state.value.snoozeMinutes)
```

- [ ] **Step 2: RED 확인**

Run: `./gradlew testDebugUnitTest --tests "*AlarmEntityMapperTest" --tests "*AlarmEditViewModelTest"`
Expected: 신규 필드와 handler 미정의로 실패.

- [ ] **Step 3: 모델, mapper, validation, migration 구현**

Room version을 4로 올리고 세 컬럼을 설계 SQL 그대로 추가한다. ViewModel은 `onSkipOnPublicHolidayChanged`, `onGradualVolumeChanged`, `onSnoozeMinutesChanged`를 제공하고 snooze는 `coerceIn(5, 30)`으로 정규화한다.

- [ ] **Step 4: GREEN과 migration 컴파일 확인**

Run: `./gradlew testDebugUnitTest compileDebugAndroidTestKotlin`
Expected: 단위 테스트 통과, 3→4 migration 테스트 컴파일.

- [ ] **Step 5: 커밋**

```bash
git add app/src app/schemas
git commit -m "feat: store holiday volume and snooze settings"
```

### Task 2: 대한민국 공휴일과 공통 날짜 판정

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/domain/model/AlarmSkipReason.kt`
- Create: `app/src/main/java/com/smartworkalarm/domain/holiday/KoreanPublicHolidayProvider.kt`
- Create: `app/src/main/java/com/smartworkalarm/domain/holiday/CalendarHolidayMatcher.kt`
- Create: `app/src/main/java/com/smartworkalarm/domain/usecase/AlarmDayEvaluator.kt`
- Modify: `app/src/main/java/com/smartworkalarm/domain/usecase/ShouldRingAlarmUseCase.kt`
- Test: `app/src/test/java/com/smartworkalarm/domain/holiday/KoreanPublicHolidayProviderTest.kt`
- Test: `app/src/test/java/com/smartworkalarm/domain/usecase/AlarmDayEvaluatorTest.kt`

**Interfaces:**
- Produces: `PublicHoliday(date, name)`와 `KoreanPublicHolidayProvider.find(date): PublicHoliday?`
- Produces: `AlarmSkipReason.PublicHoliday(name)`, `CalendarKeyword(keyword)`
- Produces: `AlarmDayEvaluation(decision, reasons)`

- [ ] **Step 1: 대표 휴일과 복합 사유 실패 테스트 작성**

```kotlin
assertEquals("광복절", provider.find(LocalDate.of(2026, 8, 15))?.name)
assertEquals("광복절 대체공휴일", provider.find(LocalDate.of(2027, 8, 16))?.name)
assertNull(provider.find(LocalDate.of(2032, 1, 1)))
assertEquals(2, evaluator.evaluate(holidayAlarm, date, events).reasons.size)
```

- [ ] **Step 2: RED 확인**

Run: `./gradlew testDebugUnitTest --tests "*KoreanPublicHolidayProviderTest" --tests "*AlarmDayEvaluatorTest"`
Expected: provider와 evaluator 미정의로 실패.

- [ ] **Step 3: 2026~2031 표와 판정 구현**

양력 고정일, 설날·추석·부처님오신날과 대체공휴일을 확정된 `LocalDate` 표로 저장한다. Calendar matcher는 명시적인 공휴일 이름, `임시공휴일`, `선거일`만 일치시키며 중복 이름을 제거한다. `ShouldRingAlarmUseCase`는 evaluator 결과를 기존 enum으로 변환한다.

- [ ] **Step 4: GREEN 및 기존 캘린더 회귀 확인**

Run: `./gradlew testDebugUnitTest --tests "*Holiday*" --tests "*ShouldRingAlarmUseCaseTest"`
Expected: 공휴일, 복합 사유, 기존 키워드·fail-open 테스트 통과.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/smartworkalarm/domain app/src/test/java/com/smartworkalarm/domain
git commit -m "feat: skip alarms on Korean public holidays"
```

### Task 3: 22시 공휴일 사유 알림

**Files:**
- Modify: `app/src/main/java/com/smartworkalarm/data/local/AlarmPreviewEntity.kt`
- Modify: `app/src/main/java/com/smartworkalarm/data/local/AlarmPreviewDao.kt`
- Modify: `app/src/main/java/com/smartworkalarm/preview/EvaluateNextDayAlarmsUseCase.kt`
- Modify: `app/src/main/java/com/smartworkalarm/preview/PreviewNotificationText.kt`
- Modify: `app/src/main/java/com/smartworkalarm/preview/NextDayPreviewNotificationFactory.kt`
- Test: `app/src/test/java/com/smartworkalarm/preview/PreviewNotificationTextTest.kt`
- Test: `app/src/test/java/com/smartworkalarm/preview/EvaluateNextDayAlarmsUseCaseTest.kt`

**Interfaces:**
- Consumes: `AlarmDayEvaluator`
- Produces: preview 상태의 직렬화된 `reason_summary`
- Produces: 휴일명과 키워드를 포함하는 `AlarmPreviewChange`

- [ ] **Step 1: 휴일 및 복합 문구 실패 테스트 작성**

```kotlin
assertEquals("광복절로 인해 오전 07:00 ‘출근 알람’이 울리지 않습니다.", holidayText)
assertTrue(combinedText.contains("광복절 및 ‘휴가’ 일정"))
```

- [ ] **Step 2: RED 확인**

Run: `./gradlew testDebugUnitTest --tests "com.smartworkalarm.preview.*"`
Expected: 공휴일 reason 처리 누락으로 실패.

- [ ] **Step 3: preview reason 저장과 알림 채널 구현**

현재 decision과 reason summary를 함께 저장한다. 상태 변화가 있을 때만 알림을 만들고 `NotificationChannel` 생성 시 vibration을 활성화하며 default notification sound를 지정한다. 기존 채널 사용자는 Android 채널 설정을 따른다.

- [ ] **Step 4: GREEN 확인**

Run: `./gradlew testDebugUnitTest --tests "com.smartworkalarm.preview.*"`
Expected: 캘린더, 공휴일, 복합, 복귀 문구 테스트 통과.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/smartworkalarm/preview app/src/main/java/com/smartworkalarm/data/local app/src/test/java/com/smartworkalarm/preview app/schemas
git commit -m "feat: explain holiday alarm changes at 22"
```

### Task 4: 알람별 다시 울림과 점증 볼륨

**Files:**
- Create: `app/src/main/java/com/smartworkalarm/alarm/GradualVolume.kt`
- Modify: `app/src/main/java/com/smartworkalarm/alarm/AlarmRinger.kt`
- Modify: `app/src/main/java/com/smartworkalarm/alarm/AlarmRingerImpl.kt`
- Modify: `app/src/main/java/com/smartworkalarm/alarm/AlarmExecutionCoordinator.kt`
- Modify: `app/src/main/java/com/smartworkalarm/alarm/AlarmRingingService.kt`
- Test: `app/src/test/java/com/smartworkalarm/alarm/GradualVolumeTest.kt`
- Test: `app/src/test/java/com/smartworkalarm/alarm/AlarmExecutionCoordinatorTest.kt`

**Interfaces:**
- Produces: `GradualVolume.atSecond(second): Float`
- Changes: `AlarmRinger.start(alarmId, alarmName, ringtoneUri, gradualVolume, snoozeMinutes)`

- [ ] **Step 1: 볼륨 곡선과 전달 실패 테스트 작성**

```kotlin
assertEquals(0.05f, GradualVolume.atSecond(0))
assertEquals(1.0f, GradualVolume.atSecond(60))
assertEquals(12, capturedSnoozeMinutes)
```

- [ ] **Step 2: RED 확인**

Run: `./gradlew testDebugUnitTest --tests "com.smartworkalarm.alarm.*"`
Expected: volume 함수와 확장 signature 미정의로 실패.

- [ ] **Step 3: service 적용 구현**

점증 옵션이면 player 시작 직후 0.05를 설정하고 service coroutine에서 1초마다 60단계로 올린다. dismiss/snooze/onDestroy에서 Job을 취소한다. snooze action은 intent의 5~30분 값을 사용해 `Duration.ofMinutes(value)`로 예약한다.

- [ ] **Step 4: GREEN과 서비스 컴파일 확인**

Run: `./gradlew testDebugUnitTest --tests "com.smartworkalarm.alarm.*" assembleDebug`
Expected: 곡선, 전달, 기존 알람 실행 테스트와 APK 빌드 통과.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/smartworkalarm/alarm app/src/test/java/com/smartworkalarm/alarm
git commit -m "feat: customize alarm volume and snooze"
```

### Task 5: 앱 전용 추천 사운드

**Files:**
- Create: `app/src/main/res/raw/sunrise_pop.wav`
- Create: `app/src/main/res/raw/soft_marimba.wav`
- Create: `app/src/main/res/raw/digital_pulse.wav`
- Create: `app/src/main/res/raw/lofi_bell.wav`
- Create: `app/src/main/res/raw/bright_chime.wav`
- Create: `app/src/main/res/raw/minimal_beat.wav`
- Create: `app/src/main/java/com/smartworkalarm/sound/RecommendedSoundCatalog.kt`
- Create: `app/src/main/java/com/smartworkalarm/presentation/sound/RecommendedSoundDialog.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/alarmedit/AlarmEditScreen.kt`
- Test: `app/src/test/java/com/smartworkalarm/sound/RecommendedSoundCatalogTest.kt`

**Interfaces:**
- Produces: `RecommendedSound(id, title, description, rawResource)`와 stable resource URI
- Produces: dialog callbacks `onPreview(uri)`, `onSelect(uri)`, `onSystemPicker()`

- [ ] **Step 1: catalog URI 실패 테스트 작성**

```kotlin
assertEquals(6, catalog.all.size)
assertEquals("android.resource://com.smartworkalarm/raw/sunrise_pop", catalog.all.first().uri)
assertEquals(catalog.all.map { it.id }.size, catalog.all.map { it.id }.distinct().size)
```

- [ ] **Step 2: RED 확인**

Run: `./gradlew testDebugUnitTest --tests "*RecommendedSoundCatalogTest"`
Expected: catalog 미정의로 실패.

- [ ] **Step 3: 오리지널 WAV와 선택 UI 구현**

외부 샘플 없이 합성 파형으로 6개 짧은 loop-safe WAV를 생성한다. dialog는 단일 MediaPlayer 미리 듣기를 사용하고 새 항목 재생, 닫기, dispose에서 이전 player를 해제한다. 시스템 알람음 버튼은 기존 picker를 실행한다.

- [ ] **Step 4: GREEN과 resource 패키징 확인**

Run: `./gradlew testDebugUnitTest --tests "*RecommendedSoundCatalogTest" assembleDebug`
Expected: 6개 catalog 테스트와 raw resource 패키징 성공.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/res/raw app/src/main/java/com/smartworkalarm/sound app/src/main/java/com/smartworkalarm/presentation app/src/test/java/com/smartworkalarm/sound
git commit -m "feat: add original recommended alarm sounds"
```

### Task 6: Night Pulse 목록과 편집 화면

**Files:**
- Modify: `app/src/main/java/com/smartworkalarm/presentation/theme/Theme.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/alarmlist/AlarmListScreen.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/alarmedit/AlarmEditScreen.kt`
- Modify: `app/src/main/java/com/smartworkalarm/presentation/settings/SettingsScreen.kt`
- Create: `app/src/main/java/com/smartworkalarm/presentation/components/NightPulseComponents.kt`
- Test: `app/src/androidTest/java/com/smartworkalarm/presentation/NightPulseScreenTest.kt`

**Interfaces:**
- Produces: `NightPulseCard`, `SettingSection`, `FeatureChip`
- Consumes: Tasks 1, 4, 5의 편집 상태와 callbacks

- [ ] **Step 1: 화면 핵심 내용 UI 테스트 작성**

```kotlin
composeRule.onNodeWithText("WORK ALARM").assertIsDisplayed()
composeRule.onNodeWithText("공휴일 자동 끄기").assertIsDisplayed()
composeRule.onNodeWithText("다시 울림 5분").assertIsDisplayed()
composeRule.onNodeWithText("점점 크게").assertIsDisplayed()
```

- [ ] **Step 2: RED/컴파일 실패 확인**

Run: `./gradlew compileDebugAndroidTestKotlin`
Expected: 신규 composable/문구 미정의로 실패.

- [ ] **Step 3: Night Pulse UI 구현**

dark navy color scheme과 violet→cyan Brush를 추가한다. 목록은 가장 가까운 활성 알람을 hero card로 표시하고 나머지는 card와 feature chips로 표시한다. 편집 화면은 `verticalScroll`과 5개 setting section을 사용하며 snooze `–/+` 경계를 적용한다. 설정 도움말에 공휴일 지원 기간과 추천 음원 정책을 추가한다.

- [ ] **Step 4: GREEN과 회귀 확인**

Run: `./gradlew compileDebugAndroidTestKotlin testDebugUnitTest assembleDebug`
Expected: UI 소스 컴파일, 전체 단위 테스트, APK 빌드 통과.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/com/smartworkalarm/presentation app/src/androidTest/java/com/smartworkalarm/presentation
git commit -m "feat: apply Night Pulse alarm design"
```

### Task 7: 전체 검증과 V1.3 APK

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `docs/testing/v1-device-checklist.md`
- Create artifact: `artifacts/WorkAlarm-v1.3-debug.apk`

**Interfaces:**
- Produces: versionCode `4`, versionName `1.3` 업데이트 APK

- [ ] **Step 1: 버전과 실제 기기 체크리스트 갱신**

공휴일 전날 22시 소리·진동, 실제 휴일 skip, 점증 볼륨, 5/중간/30분 snooze, 추천 사운드 6개, Night Pulse 스크롤·가독성을 추가한다.

- [ ] **Step 2: 클린 전체 검증**

Run: `./gradlew --no-daemon clean lintDebug testDebugUnitTest compileDebugAndroidTestKotlin assembleDebug`
Expected: BUILD SUCCESSFUL, unit failures 0, lint errors 0, APK 생성.

- [ ] **Step 3: APK 복사와 SHA-256 확인**

```powershell
Copy-Item app/build/outputs/apk/debug/app-debug.apk artifacts/WorkAlarm-v1.3-debug.apk
Get-FileHash -Algorithm SHA256 artifacts/WorkAlarm-v1.3-debug.apk
```

- [ ] **Step 4: 최종 커밋**

```bash
git add app/build.gradle.kts docs/testing/v1-device-checklist.md
git commit -m "test: verify Work Alarm v1.3"
```
