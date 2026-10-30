# Calarm (SmartWorkAlarm) 인수인계 및 개발 가이드 (HANDOVER.md)

> **최종 업데이트**: 2026-10-30  
> **현재 버전**: Calarm v1.12.3 (VersionCode: 18)  
> **패키지명**: `com.smartworkalarm`  
> **대상 플랫폼**: Android (minSdk 26, targetSdk 36, compileSdk 36)

---

## 1. 프로젝트 개요

**Calarm**은 사용자가 휴가나 출장, 교대근무 일정을 캘린더에 등록해 두면, 시스템 알람이 이를 지능적으로 분석하여 **자동으로 알람을 울리거나 건너뛰는(Skip)** 스마트 알람 애플리케이션입니다.

- **핵심 가치**: "휴가 전날 밤 알람을 끄고 복귀 전날 다시 켜야 하는 번거로움과 불안감을 완전히 해소"
- **기술 스택**:
  - **언어/프레임워크**: Kotlin 2.3.21, Jetpack Compose Material 3
  - **아키텍처**: Clean Architecture (Presentation - Domain - Data), MVVM, 단방향 데이터 흐름(UDF)
  - **로컬 DB**: Room SQLite Database (v1 ~ v7 스키마 마이그레이션 지원)
  - **의존성 주입**: Dagger Hilt 2.57.1
  - **비동기 처리**: Coroutines, StateFlow, SharedFlow
  - **백그라운드/스케줄링**: AlarmManager (`setExactAndAllowWhileIdle`), WorkManager (`androidx.work`)
  - **캘린더 연동**:
    - Android 시스템 캘린더 프로바이더 (`CalendarContract`) - Google Calendar 등 로컬 캘린더
    - Microsoft Outlook Direct Login (MSAL Android + Microsoft Graph API v1.0)

---

## 2. 최근 구현 및 주요 기능 현황

1. **사전 알림(Pre-alarm) 및 1회 스킵 기능 (v1.10.x)**
   - 알람 예정 시간 **30분 전**에 고우선순위 노티피케이션 발송 (`PreAlarmNotificationFactory`)
   - 문구: `"[알람명] 알람이 [HH:mm]에 울립니다"` / `"오늘 이 알림을 끌까요?"`
   - 액션: `오늘만 끄기` (해당 일자 1회 발생만 스킵하고 반복 스케줄은 정상 유지), `유지` (알람 유지)
2. **Microsoft Outlook Direct Login (v1.11.x)**
   - MSAL Android를 통한 Microsoft 계정 브라우저 인증 지원
   - Microsoft Graph API를 통해 31일치 일정 뷰를 로컬 Room 스냅샷으로 원자적(Atomic) 동기화
   - 네트워크 연결 없이도 로컬 스냅샷을 기반으로 알람이 안정적으로 작동
3. **알람 실행 신뢰성(Delivery Reliability) 강화 (v1.11.2 ~ v1.12.1)**
   - `AlarmExecutionJournal`: 알람 예약/실행/스킵/취소 이력을 로컬에 저널링하여 누락 및 중복 방지
   - `AlarmPlaybackController` & `AlarmPlayerPreparation`: 미디어 플레이어 준비 실패 시 시스템 기본 알람음 폴백 보장
   - 전체 화면 알람 화면(`AlarmActivity`) 화면 켜짐 유지(KeepScreenOn) 플래그 강화
4. **UI/UX 전면 리뉴얼 (v1.12.0)**
   - 메인 홈 화면, 알람 편집 화면, 전체 화면 알람 울림 화면, 설정 화면 디자인 고도화
   - 부드러운 다크 테마 및 나이트 펄스(Night Pulse) 시각 효과 적용
   - 편집 화면 하단 고정 저장 바(`PersistentSaveScaffold`) 및 미저장 변경사항 보호 다이얼로그(`UnsavedChangesDialog`)
5. **실시간 알람 및 캘린더 갱신 (v1.11.3, v1.12.3)**
   - `CalendarRefreshController`: 앱 포그라운드 진입 및 캘린더 변경 시 다가오는 알람 목록 실시간 재계산
   - 키워드 매칭 모드 보강 (완전 일치, 포함 일치 등 유연한 매칭 지원)

---

## 3. 다른 PC에서 이어 작업할 때 환경 설정

### 3.1 필수 요구사항
- **JDK**: Java 17 (Eclipse Temurin 또는 Android Studio 내장 jbr-17 권장)
- **Android Studio**: Ladybug (2024.2) 이상 권장
- **Android SDK Build-Tools**: 36.0.0
- **Android SDK Platform**: API Level 36 (Android 16 / Vanilla Ice Cream)

### 3.2 로컬 설정 파일 복구
`local.properties`는 Git 추적에서 제외되어 있으므로, 새 PC의 경로에 맞게 프로젝트 루트에 생성해야 합니다:
```properties
## 예시 (새 PC의 Android SDK 실제 경로 지정)
sdk.dir=C\:\\Users\\사용자명\\AppData\\Local\\Android\\Sdk
```

### 3.3 Outlook 로그인 테스트 시
Microsoft Entra ID(Azure AD) 클라이언트 ID가 필요한 경우, 빌드 시 속성으로 전달할 수 있습니다:
```bash
./gradlew assembleDebug -PoutlookClientId=YOUR_CLIENT_ID
```
(자세한 Azure 앱 등록 절차는 `docs/setup/outlook-app-registration.md` 문서 참고)

---

## 4. 빌드 및 테스트 명령어

```bash
# 1. 단위 테스트 전체 실행 (370+ 테스트 케이스 검증)
./gradlew testDebugUnitTest

# 2. 디버그 APK 빌드
./gradlew assembleDebug

# 3. 린트 정적 검사
./gradlew lintDebug
```

---

## 5. 프로젝트 주요 디렉토리 구조

```
SmartWorkAlarm/
├── app/
│   ├── schemas/                            # Room DB 스키마 이력 (1.json ~ 7.json)
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml         # 권한, 서비스, 리시버, 액티비티 선언
│   │   │   ├── java/com/smartworkalarm/
│   │   │   │   ├── alarm/                  # 알람 스케줄링, 리시버, 오디오 링어, 저널
│   │   │   │   ├── data/                   # Room DB, 캘린더 프로바이더 연동
│   │   │   │   ├── di/                     # Hilt DI 모듈
│   │   │   │   ├── domain/                 # 유스케이스, 모델, 공휴일 계산, 교대근무 로직
│   │   │   │   ├── outlook/                # Outlook MSAL 인증 및 Graph API 동기화
│   │   │   │   ├── permissions/            # 권한 진단 및 안내 컨트롤러
│   │   │   │   ├── prealarm/               # 30분 전 사전 알림 및 1회 스킵 기능
│   │   │   │   ├── presentation/           # Jetpack Compose UI (홈, 편집, 설정, 테마)
│   │   │   │   └── preview/                # 내일 알람 사전 브리핑 기능
│   │   │   └── res/                        # 알람 음원(raw/), 아이콘, 문자열, MSAL 설정
│   │   ├── test/                           # 단위 테스트 (로보렉트릭, JUnit)
│   │   └── androidTest/                    # UI 및 DB 마이그레이션 계측 테스트
├── docs/
│   ├── setup/                              # 환경 설정 가이드 (Outlook 앱 등록 등)
│   ├── superpowers/specs/                  # 기능별 상세 설계 명세서 (Design Specs)
│   ├── superpowers/plans/                  # 단계별 TDD 구현 계획서 (Implementation Plans)
│   └── testing/                            # 기기별 QA 체크리스트
├── artifacts/                              # 제품 정의서(.docx), 스토어 아이콘 등
├── HANDOVER.md                             # 본 인수인계 문서
└── README.md                               # 프로젝트 기본 소개 문서
```

---

## 6. Git 원격 저장소(GitHub 등) 연결 및 푸시 가이드

새 PC에서 `git clone`하여 바로 사용하려면 원격 저장소(Remote)를 등록하고 푸시해야 합니다.

```bash
# 1. GitHub 또는 GitLab에서 새 레포지토리(예: SmartWorkAlarm 또는 Calarm) 생성

# 2. 로컬 저장소에 원격 origin 등록
git remote add origin https://github.com/사용자계정/저장소이름.git

# 3. 브랜치 푸시
git push -u origin master
# 또는 작업 브랜치 푸시:
git push -u origin codex/outlook-direct-login
```

---

## 7. 다음 작업 권장 사항 (Next Steps)
1. **Azure Entra ID 실제 계정 테스트**: Microsoft 앱 등록 완료 후 실계정 로그인 및 캘린더 동기화 라이브 검증
2. **구글 플레이스토어 배포 준비**: 릴리즈 키스토어 설정, 프로가드/R8 룰 검토, 개인정보처리방침 검토
