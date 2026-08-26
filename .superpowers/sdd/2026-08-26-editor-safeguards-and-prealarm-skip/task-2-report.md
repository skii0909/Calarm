# Task 2 Report: 10분 전 사전 알림

## 상태

구현 완료. 정규 알람과 교대 알람의 실제 발생 10분 전에 발생 건 단위 선택 알림을 예약하고, `오늘만 끄기` 및 `유지` 동작을 처리한다. 스누즈 예약에는 사전 알림을 만들지 않는다.

## TDD 기록

### RED 1: 시간 정책 및 문구

실행 명령:

```text
.\gradlew.bat testDebugUnitTest --tests "com.smartworkalarm.prealarm.PreAlarmPolicyTest" --tests "com.smartworkalarm.prealarm.PreAlarmNotificationTextTest"
```

예상한 실패를 확인했다.

- `preAlarmTriggerAt` 미구현으로 시간 경계 4개 테스트가 컴파일 실패했다.
- `PreAlarmNotificationText` 미구현으로 정확한 한국어 제목/본문 테스트가 컴파일 실패했다.

최소 정책/문구 구현 후 같은 집중 테스트가 통과했다.

### RED 2: Android 13 이전 알림 권한 정책

자기검토 중 API 32 이하에서도 `POST_NOTIFICATIONS`를 런타임 권한처럼 검사할 위험을 발견했다. `canPostPreAlarmNotification` 테스트를 먼저 추가하고 미구현 심볼로 실패함을 확인한 뒤, API 33 이상에서만 권한 승인을 요구하도록 구현했다.

## 구현 내용

- `preAlarmTriggerAt(now, occurrenceAt)`은 발생이 지났으면 `null`, 아니면 `maxOf(now, occurrenceAt - 600초)`를 반환한다.
- `ACTION_PRE_ALARM`, `ACTION_SKIP_OCCURRENCE`, `ACTION_KEEP_OCCURRENCE`와 kind/ID/발생 시각 extra 계약을 추가했다.
- 정규/교대 실제 알람이 성공적으로 예약된 뒤에만 별도의 immutable 사전 알림 PendingIntent를 예약한다.
- 사전 알림 예약 전체를 `runCatching`으로 격리해 실제 알람 결과를 유지한다.
- 두 스누즈 메서드는 기존 동작을 유지하며 사전 알림을 만들지 않는다.
- 정규/교대 취소 시 대응하는 사전 알림 PendingIntent도 취소한다.
- 수신 시 현재 정규 알람 또는 교대 유형을 다시 읽고, 없거나 비활성이면 알림을 게시하지 않는다.
- 발생 키를 notification tag로 사용해 알림을 발생 건별로 구분한다.
- 알림은 소리/진동, `오늘만 끄기`, `유지` 액션을 제공한다.
- 건너뛰기는 해당 발생 키를 `OccurrenceSkipStore`에 표시하고 그 알림만 취소한다. 유지는 저장소를 변경하지 않고 그 알림만 취소한다.
- non-exported 수신기를 manifest에 등록하고, 앱 시작 시 high-importance `pre_alarm_choice` 채널을 만든다.
- API 33 이상에서 알림 권한이 없으면 안전하게 게시를 생략하며, 게시 예외도 실제 알람 흐름에 전파하지 않는다.

## 자기검토

- 사전 알림과 실제/스누즈/교대 PendingIntent의 action 및 request-code 네임스페이스가 분리되어 있다.
- 사전 알림 취소 시 extras와 무관한 동일 PendingIntent identity를 사용한다.
- 유지, 무동작, 알림 스와이프는 실제 알람을 취소하지 않는다.
- 알람 ID가 같아도 REGULAR/SHIFT 및 발생 시각이 notification tag에서 구분된다.
- 지정된 파일 범위 외 애플리케이션 코드는 변경하지 않았고 버전은 `1.9` 그대로다.

## 우려사항

- PendingIntent request code는 기존 코드와 동일하게 `Long.hashCode()`와 고정 offset을 사용하므로 이론적인 정수 해시 충돌 가능성은 남는다. action/component와 REGULAR/SHIFT 네임스페이스로 일반 충돌은 분리했다.
