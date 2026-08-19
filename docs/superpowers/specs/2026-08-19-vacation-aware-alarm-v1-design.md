# Work Alarm V1 설계

## 1. 목표

Work Alarm V1은 사용자가 휴가 전후에 출근 알람을 직접 끄고 다시 켜야 하는 기억 부담을 없앤다.

사용자가 Google Calendar 일정 제목에 `휴가`가 포함된 일정을 등록하면, 그 일정 기간에는 사용자가 지정한 알람만 울리지 않는다. 휴가가 끝나면 별도의 복구 동작 없이 기존 반복 요일과 시간에 맞춰 다시 울린다.

## 2. V1 성공 기준

- 사용자는 앱 안에서 여러 알람을 생성, 수정, 삭제할 수 있다.
- 각 알람은 이름, 시간, 반복 요일, 활성화 상태를 가진다.
- 사용자는 각 알람마다 `휴가 일정에는 이 알람 건너뛰기`를 선택할 수 있다.
- 활성 알람은 지정한 반복 요일과 시간에 울린다.
- 선택한 Google Calendar의 일정 제목에 `휴가`가 포함되어 있으면, 해당 일정 기간에는 `휴가 일정에는 이 알람 건너뛰기`가 선택된 알람만 울리지 않는다.
- 휴가와 무관한 알람은 휴가 기간에도 정상적으로 울린다.
- 하루짜리 휴가는 당일만 건너뛰고, 여러 날 휴가는 포함된 모든 날짜를 건너뛴다.
- 휴가 종료 후 각 알람은 원래 반복 설정에 따라 자동으로 다시 울린다.
- 캘린더 권한이나 조회에 문제가 생겼을 때는 알람 누락을 피하기 위해 알람을 울린다.
- 기기 재부팅 및 시각·시간대 변경 후 활성 알람이 다시 예약된다.

## 3. 범위

### 포함

- Android 네이티브 앱
- Kotlin, Jetpack Compose, MVVM
- Room 기반 알람 저장
- AlarmManager 기반 정확한 반복 알람
- Google Calendar 연동을 위한 CalendarContract 조회
- AlarmManager 재예약을 보조하는 WorkManager 작업
- Hilt, Coroutines, Flow
- 전체 화면 알람, 해제, 다시 알림
- Google Calendar 연결 온보딩
- Outlook Calendar `준비 중` 안내

### 제외

- Outlook Calendar 실제 로그인 및 일정 조회
- 프로파일 생성과 프로파일 자동 전환
- 사용자 정의 키워드와 규칙 우선순위
- `연차`, `반차`, `출장`, `재택`, 교대근무 처리
- 일정 제목 분석과 자동 규칙 추천
- CalendarCache 테이블
- 삼성 시계 또는 기본 알람 앱 제어
- 서버, 계정 동기화, 원격 백업

제외된 기능은 V1 동작을 확인한 뒤 별도의 설계와 구현 주기로 다룬다.

## 4. 기술 기준

- Minimum SDK: 26
- Target SDK와 Compile SDK: 구현 시점에 설치 가능한 최신 안정 Android SDK
- Language: Kotlin
- UI: Jetpack Compose와 Material 3
- Architecture: 단일 앱 모듈의 MVVM
- Local persistence: Room
- Dependency injection: Hilt
- Async: Kotlin Coroutines와 Flow
- Alarm: AlarmManager
- Background recovery: WorkManager
- Calendar: Android CalendarContract

단일 모듈을 유지하고 도메인 경계를 패키지와 인터페이스로 분리한다. V1에 필요하지 않은 멀티 모듈 구성은 도입하지 않는다.

## 5. 아키텍처

```text
Compose UI
    |
ViewModel
    |
UseCase
    |
Repository / CalendarEventSource / AlarmScheduler
    |                    |                 |
Room                 CalendarContract   AlarmManager
```

### Presentation

Compose 화면이 UI 상태를 표시하고 사용자 입력을 ViewModel에 전달한다. ViewModel은 Flow 기반 상태를 제공하고 UseCase만 호출한다.

### Domain

알람 모델, 다음 실행 시각 계산, 휴가 여부에 따른 실행 결정, 알람 CRUD와 예약 조정을 포함한다. 날짜 계산과 실행 결정은 Android 프레임워크에서 분리된 순수 Kotlin 코드로 작성한다.

### Data

Room이 사용자의 알람 설정을 저장한다. `CalendarEventSource` 구현은 CalendarContract에서 Google 계정의 실제 일정 발생분을 조회한다.

### Alarm runtime

AlarmManager는 활성 알람마다 가장 가까운 실행 한 건만 예약한다. Receiver는 다음 실행을 먼저 예약한 뒤 현재 실행을 계속할지 판단한다. 알람을 울릴 때 고우선순위 알림과 전체 화면 Activity를 사용한다.

### Recovery

기기 재부팅, 앱 업데이트, 시각 또는 시간대 변경 시 활성 알람을 다시 예약한다. 긴 저장소 접근은 WorkManager의 재예약 작업으로 넘긴다.

## 6. 패키지 구조

```text
com.smartworkalarm
├── alarm
│   ├── AlarmBroadcastReceiver
│   ├── AlarmNotificationFactory
│   ├── AlarmRingingService
│   ├── AlarmSchedulerImpl
│   ├── AlarmActivity
│   └── SystemChangeReceiver
├── data
│   ├── calendar
│   │   ├── CalendarContractEventSource
│   │   └── CalendarQueryFailure
│   ├── local
│   │   ├── AlarmDao
│   │   ├── AlarmEntity
│   │   ├── AlarmEntityMapper
│   │   └── WorkAlarmDatabase
│   └── repository
│       └── AlarmRepositoryImpl
├── di
│   ├── AlarmModule
│   ├── CalendarModule
│   └── DatabaseModule
├── domain
│   ├── model
│   │   ├── Alarm
│   │   ├── CalendarEvent
│   │   └── AlarmExecutionDecision
│   ├── repository
│   │   ├── AlarmRepository
│   │   ├── AlarmScheduler
│   │   └── CalendarEventSource
│   └── usecase
│       ├── CalculateNextOccurrenceUseCase
│       ├── DeleteAlarmUseCase
│       ├── GetAlarmsUseCase
│       ├── SaveAlarmUseCase
│       ├── SetAlarmEnabledUseCase
│       └── ShouldRingAlarmUseCase
├── presentation
│   ├── alarmedit
│   ├── alarmlist
│   ├── onboarding
│   ├── navigation
│   └── theme
├── worker
│   └── RescheduleAlarmsWorker
├── MainActivity
└── WorkAlarmApplication
```

## 7. 데이터 모델과 Room

### Alarm 도메인 모델

```kotlin
data class Alarm(
    val id: Long,
    val name: String,
    val hour: Int,
    val minute: Int,
    val repeatDaysMask: Int,
    val enabled: Boolean,
    val skipOnVacation: Boolean,
)
```

### AlarmEntity

```text
Table: alarms

id                 INTEGER PRIMARY KEY AUTOINCREMENT
name               TEXT NOT NULL
hour               INTEGER NOT NULL
minute             INTEGER NOT NULL
repeat_days_mask   INTEGER NOT NULL
enabled            INTEGER NOT NULL
skip_on_vacation   INTEGER NOT NULL
created_at         INTEGER NOT NULL
```

제약 조건은 애플리케이션 계층에서도 검증한다.

- `name`: 공백 제거 후 한 글자 이상
- `hour`: 0~23
- `minute`: 0~59
- `repeatDaysMask`: 월요일부터 일요일 중 한 개 이상 선택

반복 요일은 월요일부터 일요일까지 일곱 비트로 저장한다. 휴가 때문에 `enabled`를 변경하지 않는다. `enabled`는 사용자가 직접 선택한 상태만 표현한다.

### ERD

```text
┌─────────────────────────────┐
│ alarms                      │
├─────────────────────────────┤
│ PK id                       │
│    name                     │
│    hour                     │
│    minute                   │
│    repeat_days_mask         │
│    enabled                  │
│    skip_on_vacation         │
│    created_at               │
└─────────────────────────────┘
```

V1에는 관계 테이블이 없다.

## 8. Calendar Service

### 인터페이스

```kotlin
interface CalendarEventSource {
    suspend fun getEventsOverlapping(
        startInclusive: Instant,
        endExclusive: Instant,
    ): Result<List<CalendarEvent>>
}
```

알람 엔진은 CalendarContract를 직접 참조하지 않는다. 후속 버전에서 Outlook 구현을 추가할 때 같은 의미의 인터페이스 구현을 제공한다.

### Google 일정 선택

- CalendarContract의 캘린더 목록에서 Google 계정에 속하고 표시 및 동기화가 활성화된 캘린더를 대상으로 한다.
- 삼성 캘린더 UI를 사용하더라도 Google 계정에 저장된 일정이면 CalendarContract를 통해 조회할 수 있다.
- 반복 일정의 실제 발생분을 얻기 위해 `CalendarContract.Instances`를 조회한다.
- 조회 열은 occurrence ID, title, begin, end, all-day 여부로 제한한다.

### 휴가 매칭

- 제목이 없는 일정은 휴가가 아니다.
- 제목 앞뒤 공백을 제거한 후 `휴가` 포함 여부를 검사한다.
- `휴가`, `여름휴가`, `가족 휴가`는 매칭된다.
- 설명은 V1에서 검사하지 않는다.
- 알람이 실행되는 로컬 날짜의 시작 시각 이상, 다음 날짜 시작 시각 미만 범위에 겹치는 일정을 조회한다.
- 하루 종일 일정의 종료일이 배타적인 CalendarContract 의미를 그대로 사용한다.
- 시간 지정 일정은 알람 실행 시각이 아니라 일정이 걸친 로컬 날짜 전체를 휴가 날짜로 취급한다.

예를 들어 8월 20일 14:00부터 8월 20일 18:00까지 `오후 휴가`가 있으면 8월 20일의 `skipOnVacation` 알람은 모두 건너뛴다. 반차의 세분화는 V1 범위가 아니다.

## 9. UseCase

### CalculateNextOccurrenceUseCase

현재 시각, 알람 시간, 반복 요일, ZoneId를 받아 현재 시각보다 뒤인 가장 가까운 실행 Instant를 반환한다. 일광절약시간의 존재하지 않거나 중복된 로컬 시각은 `java.time`의 ZoneRules가 정한 유효 Instant로 해석한다.

### GetAlarmsUseCase

Room의 전체 알람을 Flow로 제공한다.

### SaveAlarmUseCase

입력을 검증하고 저장한 다음 기존 PendingIntent를 취소하고, 활성 알람이면 다음 실행을 예약한다.

### DeleteAlarmUseCase

예약을 취소한 다음 Room에서 삭제한다.

### SetAlarmEnabledUseCase

사용자 활성화 상태를 저장한다. 켜면 다음 실행을 예약하고 끄면 예약을 취소한다.

### ShouldRingAlarmUseCase

알람과 현재 날짜를 받아 다음 규칙으로 결정한다.

```text
알람 없음 또는 enabled=false       -> DO_NOT_RING
skipOnVacation=false              -> RING
캘린더 권한 없음                   -> RING_WITH_CALENDAR_WARNING
캘린더 조회 실패                   -> RING_WITH_CALENDAR_WARNING
제목에 "휴가"가 포함된 겹치는 일정 -> SKIP_FOR_VACATION
그 외                              -> RING
```

## 10. Alarm Engine

### 예약

- 알람마다 고유 request code와 안정적인 PendingIntent identity를 사용한다.
- 활성 알람마다 가장 가까운 실행 한 건만 AlarmManager에 등록한다.
- Android 12 이상에서 정확한 알람 권한을 확인한다.
- 권한이 있으면 정확한 알람 API로 등록한다.
- 권한이 없으면 임의의 부정확한 알람으로 조용히 대체하지 않고 UI에 해결 안내를 표시한다.

### 실행

1. Receiver가 알람 ID를 수신한다.
2. `goAsync`와 제한된 CoroutineScope로 후속 작업을 수행한다.
3. Room에서 알람을 조회한다.
4. 삭제되지 않고 활성 상태라면 다음 반복 실행을 먼저 예약한다.
5. `ShouldRingAlarmUseCase`로 현재 실행 결정을 구한다.
6. `SKIP_FOR_VACATION`이면 UI를 열지 않고 종료한다.
7. 그 외 실행 결정이면 AlarmRingingService가 소리, 진동, 고우선순위 알림을 시작한다.
8. Notification의 full-screen intent로 AlarmActivity를 표시한다.

캘린더 조회가 실패하면 알람을 울린다. 알람을 놓치는 것보다 휴가 중 한 번 잘못 울리는 것이 안전한 실패다.

### 해제와 다시 알림

- 해제는 현재 소리와 진동만 중지한다. 반복 설정과 이미 예약된 다음 실행은 유지한다.
- 다시 알림은 V1에서 고정 5분 후 일회성 PendingIntent를 등록한다.
- 다시 알림 실행도 같은 휴가 판정을 적용한다. 사용자가 휴가 중 실수로 다시 알림을 만들더라도 휴가 일정이 유지되면 울리지 않는다.

## 11. WorkManager와 시스템 이벤트

WorkManager는 매일 캘린더를 동기화하거나 사용자 설정을 변경하지 않는다. AlarmManager 등록 복구에만 사용한다.

- `BOOT_COMPLETED`: RescheduleAlarmsWorker를 enqueue한다.
- `MY_PACKAGE_REPLACED`: RescheduleAlarmsWorker를 enqueue한다.
- `TIME_SET`: RescheduleAlarmsWorker를 replace 정책으로 enqueue한다.
- `TIMEZONE_CHANGED`: RescheduleAlarmsWorker를 replace 정책으로 enqueue한다.

Worker는 Room에서 활성 알람을 읽고 각각의 다음 실행을 다시 계산해 등록한다. 동일한 unique work 이름을 사용해 중복 재예약 작업을 방지한다.

## 12. 화면과 Navigation

```text
OnboardingRoute
  └── AlarmListRoute
        ├── AlarmEditRoute/new
        └── AlarmEditRoute/{alarmId}
```

### Onboarding

- 핵심 문구: `휴가를 캘린더에 등록하면 출근 알람을 알아서 건너뜁니다. 휴가가 끝나면 다시 원래대로 울립니다.`
- Google Calendar 선택
- Outlook Calendar 선택 항목과 `준비 중` 안내
- Google 선택 시 READ_CALENDAR 런타임 권한 요청
- 권한을 거절해도 앱 진입은 허용하되 휴가 자동 건너뛰기가 동작하지 않고 알람은 정상 실행된다고 안내

### Alarm List

- 알람 이름, 시간, 반복 요일 요약
- 사용자 활성화 Switch
- 다음 예약 시각
- 휴가 건너뛰기 표시
- 정확한 알람 권한, 알림 권한, 캘린더 권한이 없을 때 해결 가능한 안내
- 알람 추가, 수정, 삭제

상태 문구는 사용자의 영구 설정과 자동 실행 결정을 섞지 않는다. `꺼짐`은 사용자가 비활성화한 경우에만 사용한다. 휴가로 한 번 건너뛴 사실은 `오늘 휴가 일정으로 건너뜀`으로 별도 표시한다.

### Alarm Edit

- 이름 입력
- Material TimePicker
- 월~일 반복 요일 선택
- 활성화 Switch
- `휴가 일정에는 이 알람 건너뛰기` Switch
- 저장과 삭제

## 13. 권한과 Android 정책

- `READ_CALENDAR`: Google Calendar 일정 조회
- `POST_NOTIFICATIONS`: Android 13 이상 알람 알림 표시
- `SCHEDULE_EXACT_ALARM`: Android 12 이상 정확한 알람 특별 접근
- `USE_FULL_SCREEN_INTENT`: 알람 전체 화면 표시
- `RECEIVE_BOOT_COMPLETED`: 재부팅 후 재예약
- Foreground Service 관련 권한: 구현 시 선택한 알람 재생 서비스 유형에 맞게 선언

권한은 사용 맥락에서 요청한다. 캘린더 권한은 온보딩의 Google 연결 단계, 알림 및 정확한 알람 권한은 첫 알람 저장 전 요청한다. 권한을 영구 거절한 경우 앱 설정 또는 특별 접근 설정으로 연결한다.

## 14. 오류 처리

- CalendarContract SecurityException: 휴가 판정을 실패로 기록하고 알람 실행
- Calendar provider 오류 또는 null Cursor: 휴가 판정을 실패로 기록하고 알람 실행
- 선택 가능한 Google 캘린더 없음: 연결 안내를 표시하고 알람 실행
- 정확한 알람 접근 없음: 예약 성공으로 표시하지 않고 목록에 권한 안내 표시
- 저장 데이터가 유효하지 않음: 저장을 거절하고 입력 필드 오류 표시
- 이미 삭제된 알람의 PendingIntent 도착: 아무 동작 없이 종료
- 반복 요일이 없는 손상 데이터: 예약하지 않고 진단 로그 기록

로그에는 일정 제목과 같은 개인 정보를 남기지 않는다.

## 15. 테스트 전략

### 순수 단위 테스트

- `CalculateNextOccurrenceUseCase`
  - 오늘 실행 시각 전과 후
  - 금요일에서 다음 월요일
  - 일요일에서 월요일
  - 한 요일만 선택
  - 모든 요일 선택
  - ZoneId 경계
- 휴가 제목 매칭
  - `휴가`, `여름휴가`, ` 가족 휴가 `
  - null, 빈 제목, `회의`, `연차`
- 일정 겹침
  - 하루 종일 하루
  - 여러 날 하루 종일 일정의 배타적 종료일
  - 시간 지정 일정
  - 자정을 가로지르는 일정
- `ShouldRingAlarmUseCase`
  - skip 옵션 꺼짐
  - 휴가 있음과 없음
  - 권한 없음
  - 조회 실패
  - 비활성 알람

### 저장소와 Android 테스트

- Room 저장, 수정, 삭제, Flow 반영
- 저장·활성화·비활성화·삭제 시 Scheduler 호출 결과
- Receiver가 다음 알람을 먼저 예약하는 흐름
- RescheduleAlarmsWorker가 활성 알람만 재예약
- 동일 알람 PendingIntent의 등록 및 취소 identity

### UI 테스트

- 온보딩 권한 상태별 안내
- 알람 생성과 필수 입력 검증
- 반복 요일 선택
- 휴가 건너뛰기 옵션 저장
- 사용자 비활성화와 휴가 스킵 상태 문구 구분

### 수동 기기 검증

- 화면이 켜진 상태, 꺼진 상태, 잠금 상태에서 알람 실행
- 배터리 절전 및 Doze 상태
- 재부팅 후 알람 복구
- 하루 및 여러 날 Google Calendar 휴가 일정
- 알람 직전에 휴가 일정을 추가하거나 삭제한 경우
- 캘린더 권한을 알람 예약 후 취소한 경우

## 16. 완료 조건

- 클린 체크아웃에서 Gradle 빌드가 성공한다.
- 단위 테스트와 Android 테스트가 통과한다.
- 여러 반복 알람을 만들 수 있다.
- 같은 휴가 날짜에 `skipOnVacation=true`인 알람은 울리지 않고 `false`인 알람은 울린다.
- 휴가 종료 후 사용자 조작 없이 원래 반복 알람이 울린다.
- 캘린더 조회 실패 시 알람이 울린다.
- 재부팅 후 활성 알람이 다시 예약된다.
- V1 제외 기능의 미완성 UI나 동작 경로가 노출되지 않는다.
