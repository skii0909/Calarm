# Calarm (SmartWorkAlarm)

> **스마트 캘린더 연동 휴가 및 교대근무 알람 Android 애플리케이션**

Calarm은 Google Calendar와 Microsoft Outlook 일정을 자동으로 분석하여, 휴가·연차·공휴일에는 알람을 스스로 건너뛰고(Skip), 교대근무 일정에 맞춰 알람을 유연하게 울려주는 지능형 알람 앱입니다.

---

## 주요 기능

1. **지능형 휴가/공휴일 감지 & 알람 자동 건너뛰기**
   - 캘린더의 '휴가', '연차', '반차' 등의 일정 및 대한민국 법정 공휴일을 감지하여 알람을 울리지 않고 스킵합니다.
   - 휴가가 끝나면 별도의 복구 조작 없이 자동으로 기존 스케줄대로 알람이 복구됩니다.
2. **사전 알림(Pre-alarm) 및 1회 스킵 (30분 전 알림)**
   - 알람 발생 30분 전 시스템 알림(`[알람명] 알람이 [HH:mm]에 울립니다`)을 발송합니다.
   - 이미 일어난 경우 `오늘만 끄기` 버튼을 눌러 당일 1회만 스킵할 수 있습니다.
3. **Microsoft Outlook Direct Login & Google Calendar 동기화**
   - 시스템 내장 Google Calendar뿐만 아니라, MSAL을 통해 Microsoft 계정으로 로그인하여 회사 Outlook 캘린더를 직접 동기화합니다.
   - 네트워크 연결이 끊어져도 6시간 유효한 로컬 스냅샷을 통해 100% 신뢰성 있는 알람을 보장합니다.
4. **교대근무 알람 그룹**
   - 주간/야간/당직/비번 등 교대 패턴별 키워드 매칭 알람을 지원합니다.
5. **Modern Jetpack Compose UI**
   - Material 3 기반 세련된 다크 테마, 나이트 펄스(Night Pulse) 비주얼, 하단 고정 저장 바 및 미저장 변경사항 보호 지원.

---

## 빠른 시작 (Getting Started)

### 요구사항
- **JDK 17** 이상
- **Android Studio** Ladybug (2024.2) 이상
- **Android SDK API 36**

### 빌드 및 테스트
```bash
# 단위 테스트 실행
./gradlew testDebugUnitTest

# 디버그 APK 빌드
./gradlew assembleDebug
```

### 상세 인수인계 문서
다른 PC에서의 개발 환경 구성 및 상세 아키텍처 설명은 [`HANDOVER.md`](./HANDOVER.md)를 참고하세요.
