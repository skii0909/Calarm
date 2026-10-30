# Calarm Microsoft 로그인 앱 등록

작성일: 2026-09-19. 현재 디버그 APK의 패키지와 서명 기준이다.

## 등록 값

| 항목 | 값 |
| --- | --- |
| 앱 이름 | Calarm |
| 지원 계정 | 모든 조직 디렉터리의 계정 및 개인 Microsoft 계정 |
| Android 패키지 이름 | `com.smartworkalarm` |
| Android Signature hash | `aaoXrHFrYrhShsQEbGTUANF+6Ek=` |
| 예상 redirect URI | `msauth://com.smartworkalarm/aaoXrHFrYrhShsQEbGTUANF%2B6Ek%3D` |
| Graph 권한 | 위임된 권한 `Calendars.ReadBasic` |

서명 값은 `gradlew.bat :app:signingReport`에서 확인한 디버그 인증서 SHA-1 바이트를 Base64로 변환했다. SHA-1은 `69:AA:17:AC:71:6B:62:B8:52:86:C4:04:6C:64:D4:00:D1:7E:E8:49`이다. 서명 해시는 공개 식별 정보이며 키 파일이나 비밀번호가 아니다.

## 등록 절차

1. [Microsoft Entra 관리 센터](https://entra.microsoft.com)에 앱을 소유할 계정으로 로그인한다.
2. 앱 등록 권한이 있는 디렉터리를 선택한다. 디렉터리나 앱 등록 메뉴에 접근할 수 없으면 그 상태를 먼저 확인한다. 개인 Outlook 메일 계정이 있다는 사실만으로 앱 등록 권한이 확보되는 것은 아니다.
3. `Entra ID → 앱 등록 → 새 등록`에서 이름 `Calarm`, 지원 계정 `모든 조직 디렉터리의 계정 및 개인 Microsoft 계정`을 선택해 등록한다. 웹 redirect URI는 이 단계에서 추가하지 않는다.
4. `인증 → 플랫폼 추가 → Android`에서 위의 패키지 이름과 Signature hash를 그대로 넣는다.
5. 생성된 MSAL 구성의 redirect URI가 위 값과 같은지 확인하고 저장한다.
6. `API 권한 → 권한 추가 → Microsoft Graph → 위임된 권한`에서 `Calendars.ReadBasic`을 추가한다. Application 권한이나 일정 쓰기 권한은 추가하지 않는다.
7. 개요 화면의 `Application (client) ID`를 구현 담당자에게 전달한다. Android 플랫폼에서 생성된 MSAL 구성도 함께 전달하면 등록을 교차 확인할 수 있다.

client secret은 만들 필요가 없다. 계정 비밀번호, 토큰, 키 파일은 전달하지 않는다. 회사 계정에서 사용자 동의가 제한된 경우 해당 조직의 승인 절차가 별도로 필요할 수 있다.

## 배포 시 변경

현재 Gradle의 release 서명 구성은 없다. Google Play 또는 release APK를 배포할 때 실제 앱 서명 인증서에 해당하는 Android 플랫폼/redirect URI를 추가하고 해당 빌드 설정에 반영해야 한다. Play App Signing 사용 시 업로드 키와 실제 배포 앱 서명 키를 구분한다.

## 현재 상태

- 사용자는 2026-09-19에 Microsoft Entra 앱이 아직 등록되지 않았다고 확인했다.
- 코드에는 MSAL 로그인과 Graph 캘린더 조회가 구현되어 있다. 실제 Microsoft 앱 등록 및 계정 로그인 검증은 아직 수행하지 않았다.

## client ID를 적용한 APK 생성

프로젝트 루트의 PowerShell에서 실행한다. 아래 GUID 자리에 등록한 실제 Application (client) ID를 넣는다.

```powershell
.\gradlew.bat assembleDebug '-PoutlookClientId=12345678-1234-4234-9234-123456789abc'
```

이 값은 공개 앱 식별자이며 secret이 아니다. 지속적으로 사용하려면 개인 Gradle 설정 `%USERPROFILE%\.gradle\gradle.properties`에 `outlookClientId=실제-ID`를 추가할 수 있다. 명령행의 `-PoutlookClientId`가 우선한다. 비밀번호나 client secret은 설정하지 않는다.

현재 컴퓨터의 debug 서명 해시는 빌드의 기본값으로 들어 있다. 다른 서명으로 빌드할 때는 등록된 서명 해시를 추가 지정한다.

```powershell
.\gradlew.bat assembleDebug '-PoutlookClientId=실제-ID' '-PoutlookSignatureHash=등록된-Base64-서명해시'
```

로그인 전에 앱은 실제 설치 APK의 서명이 구성과 일치하는지 검사한다. release에는 debug 서명을 기본값으로 넣지 않으며 release 서명 구성과 실제 배포 인증서 등록이 필요하다.

client ID가 없는 APK는 로그인 버튼을 비활성화하고 Microsoft 로그인 설정이 완료되지 않았다고 안내한다. 이 APK로 정상 로그인이나 서버 일정 조회가 검증됐다고 간주하면 안 된다.

## 연결 후 확인할 항목

1. Calarm 설정의 `Microsoft 로그인`에서 개인 Microsoft 계정으로 로그인한다.
2. 캘린더 읽기에 동의하고 기본 캘린더가 선택되는지 확인한다.
3. 다른 소유 캘린더를 선택한 뒤 동기화 완료와 마지막 갱신 시각을 확인한다.
4. Outlook에서 휴가/교대 일정을 만든 뒤 `지금 새로고침`으로 반영한다.
5. 연결 해제 후 Outlook 선택 정보가 사라지고 Google 연결과 저장된 알람이 유지되는지 확인한다.
6. 조직 테스트 계정이 있으면 Microsoft 365 로그인과 조직의 동의 정책을 별도로 확인한다.

참고: [앱 등록](https://learn.microsoft.com/en-us/entra/identity-platform/quickstart-register-app), [Android 플랫폼 등록](https://learn.microsoft.com/en-us/entra/identity-platform/tutorial-mobile-app-android-prepare-app), [캘린더 조회 최소 권한](https://learn.microsoft.com/en-us/graph/api/calendar-list-calendarview?view=graph-rest-1.0).
