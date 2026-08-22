# Work Alarm V1.1 알람 설정 개선 설계

## 목표

기존 V1의 캘린더 기반 알람 건너뛰기 흐름을 유지하면서 시간 입력 오류를 제거하고, 사용자가 알람별 캘린더 키워드와 알람음을 선택할 수 있게 한다.

이 문서는 `2026-08-19-vacation-aware-alarm-v1-design.md`를 확장하며, 충돌하는 항목은 이 문서가 우선한다.

## 성공 기준

- 알람 시간은 숫자 텍스트 입력이 아니라 Material Time Picker로 선택한다.
- 사용자는 각 알람마다 캘린더 자동 건너뛰기를 켜거나 끌 수 있다.
- 사용자는 각 알람마다 쉼표로 구분한 하나 이상의 일정 키워드를 저장할 수 있다.
- 키워드는 캘린더 일정 제목의 어느 위치에 있어도 부분 일치한다.
- 키워드 앞뒤 공백과 영문 대소문자는 매칭 결과에 영향을 주지 않는다.
- 여러 키워드 중 하나라도 일정 제목에 포함되면 해당 알람만 그 날짜에 건너뛴다.
- 사용자는 Android 시스템 알람음 선택창에서 알람별 소리를 선택할 수 있다.
- 선택한 알람음이 사라졌거나 재생할 수 없으면 시스템 기본 알람음을 사용한다.
- 기존 V1 데이터는 손실 없이 마이그레이션되며 기본 키워드 `휴가`와 시스템 기본 알람음을 사용한다.

## 범위

### 포함

- Material 3 Time Picker
- 알람별 일정 키워드
- 캘린더 일정 제목 부분 일치
- Android 시스템 Ringtone Picker
- 알람별 ringtone URI 저장 및 재생
- Room 1→2 마이그레이션

### 제외

- 일정 description 검색
- 정규식 키워드
- 키워드별 서로 다른 동작
- 알람음 파일 복사 또는 앱 자체 음원
- 알람음 편집과 볼륨 조절
- Outlook Calendar

## 데이터 모델

기존 `Alarm.skipOnVacation` 필드는 의미를 일반화해 `skipOnCalendarMatch`로 이름을 바꾼다.

```kotlin
data class Alarm(
    val id: Long,
    val name: String,
    val hour: Int,
    val minute: Int,
    val repeatDaysMask: Int,
    val enabled: Boolean,
    val skipOnCalendarMatch: Boolean,
    val calendarKeywords: List<String>,
    val ringtoneUri: String?,
)
```

Room에는 목록을 별도 테이블로 만들지 않고 정규화된 키워드를 쉼표로 연결한 문자열로 저장한다. V1.1에 키워드별 속성이 없으므로 별도 관계 테이블은 불필요하다.

```text
alarms
- 기존 컬럼
- skip_on_calendar_match INTEGER NOT NULL DEFAULT 1
- calendar_keywords TEXT NOT NULL DEFAULT '휴가'
- ringtone_uri TEXT NULL
```

### Room 1→2 마이그레이션

SQLite는 기존 `skip_on_vacation` 컬럼을 그대로 유지할 수 있으므로 물리 컬럼 이름은 호환성을 위해 바꾸지 않는다. Kotlin Entity 속성만 `skipOnCalendarMatch`로 변경하고 `@ColumnInfo(name = "skip_on_vacation")`를 유지한다.

다음 컬럼만 추가한다.

```sql
ALTER TABLE alarms ADD COLUMN calendar_keywords TEXT NOT NULL DEFAULT '휴가';
ALTER TABLE alarms ADD COLUMN ringtone_uri TEXT DEFAULT NULL;
```

Database version을 2로 올리고 `MIGRATION_1_2`를 명시적으로 등록한다. destructive migration은 사용하지 않는다.

## 키워드 규칙

사용자 입력은 다음 순서로 정규화한다.

1. 쉼표로 분리한다.
2. 각 항목의 앞뒤 공백을 제거한다.
3. 빈 항목을 제거한다.
4. Locale 기반 소문자 변환을 적용한다.
5. 입력 순서를 유지하면서 중복을 제거한다.

예:

```text
입력: " 휴가, 연차,휴가, 병가 "
저장 의미: ["휴가", "연차", "병가"]
```

일정 제목도 앞뒤 공백 제거와 소문자 변환 후 각 키워드의 `contains`로 검사한다.

```text
키워드 "휴가"
- "여름휴가" → 일치
- "휴가 - 제주도" → 일치
- "8월 휴가 일정" → 일치
- "연차" → 불일치
```

캘린더 자동 건너뛰기 스위치가 꺼졌거나 정규화 후 키워드가 비어 있으면 캘린더를 조회하지 않고 알람을 울린다. 캘린더 권한 없음과 조회 실패도 기존 fail-open 정책을 유지한다.

## 시간 선택

알람 편집 화면은 시·분 `OutlinedTextField`를 제거한다. 현재 선택 시간을 `HH:mm` 형식의 버튼으로 표시하고 버튼을 누르면 Material 3 `TimePicker` Dialog를 연다.

- 신규 알람 기본값: 07:00
- 기존 알람 수정: 저장된 시각으로 Picker 초기화
- 24시간 형식 사용
- 확인을 누르면 ViewModel의 정수 hour/minute를 함께 갱신
- 취소를 누르면 기존 시간을 유지

키보드 숫자 입력이 없으므로 중간 문자열과 강제 범위 보정 문제도 발생하지 않는다.

## 알람음 선택

알람 편집 화면의 `알람음` 행을 누르면 `RingtoneManager.ACTION_RINGTONE_PICKER` Intent를 실행한다.

- `EXTRA_RINGTONE_TYPE`: `TYPE_ALARM`
- `EXTRA_RINGTONE_SHOW_DEFAULT`: true
- `EXTRA_RINGTONE_SHOW_SILENT`: false
- `EXTRA_RINGTONE_EXISTING_URI`: 현재 알람 URI 또는 시스템 기본 알람 URI

결과 URI 문자열을 Room에 저장하고 `RingtoneManager.getRingtone()`으로 표시 이름을 구한다. URI 권한이 필요한 provider 결과에는 persistable read permission을 시도하되, 실패해도 URI를 저장하고 재생 시 fallback을 적용한다.

알람 실행 경로는 `AlarmRinger.start(alarmId, alarmName, ringtoneUri)`로 URI를 전달한다. `AlarmRingingService`는 다음 순서로 음원을 결정한다.

1. 저장된 URI가 있고 MediaPlayer 준비에 성공하면 해당 URI
2. 시스템 기본 `TYPE_ALARM` URI
3. 기본 알람 URI도 없으면 `TYPE_NOTIFICATION` URI

어느 URI도 재생할 수 없는 경우 진동과 전체 화면 알림은 계속 동작하며 서비스가 종료되지 않는다.

## UI

알람 편집 화면 순서:

1. 알람 이름
2. 시간 선택 버튼
3. 반복 요일
4. 알람 활성화
5. 캘린더 일정에 따라 건너뛰기
6. 건너뛸 일정 키워드 입력
7. 알람음 선택
8. 저장/삭제

키워드 입력 안내:

```text
라벨: 건너뛸 일정 키워드
예시: 휴가, 연차, 병가
도움말: 일정 제목에 키워드가 포함되면 이 알람을 건너뜁니다.
```

스위치가 꺼지면 키워드 입력란은 비활성화하지만 값을 삭제하지 않는다.

알람 목록에는 캘린더 스킵이 켜진 알람에 정규화된 키워드 요약을 표시한다.

## 테스트

### 단위 테스트

- 키워드 분리, 공백 제거, 중복 제거, 영문 대소문자 정규화
- 제목 시작·중간·끝 부분 일치
- 키워드 하나 이상 일치와 전체 불일치
- 스위치 꺼짐과 빈 키워드에서 캘린더 미조회
- 선택한 hour/minute 상태 저장
- 선택한 ringtone URI가 Alarm 모델과 Entity 왕복에서 보존
- AlarmExecutionCoordinator가 ringtone URI를 Ringer에 전달
- 잘못된 URI일 때 fallback 후보 순서

### Room 테스트

- MIGRATION_1_2 후 기존 id, 이름, 시간, 반복 요일, enabled, skip 값 보존
- 신규 키워드가 `휴가`, ringtone URI가 null

### UI 테스트

- 시간 행 클릭 시 Time Picker 표시
- 확인 시 선택 시간이 화면에 반영
- 키워드 입력 저장
- 알람음 행 클릭 시 시스템 Picker Intent 생성

## 완료 조건

- 기존 V1 단위 테스트와 새 테스트가 통과한다.
- lintDebug가 오류 없이 통과한다.
- assembleDebug가 성공한다.
- 기존 DB를 가진 앱 위에 업데이트 설치할 수 있다.
- 05:00을 선택하면 05:00으로 저장되고 23:00으로 변하지 않는다.
- 일정 제목의 어느 위치든 알람별 키워드가 포함되면 해당 알람만 건너뛴다.
- 서로 다른 두 알람이 서로 다른 알람음을 재생할 수 있다.
- 새 설치용 APK와 기존 V1 업데이트용 APK가 동일한 산출물로 제공된다.
