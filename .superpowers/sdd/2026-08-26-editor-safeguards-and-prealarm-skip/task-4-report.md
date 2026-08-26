# Task 4 Report

## 상태

완료. 일반 알람, 교대 그룹, 근무 형태 편집기에 baseline 기반 dirty-state와 저장 확인 뒤로가기를 적용했다.

## 구현

- 각 ViewModel state에 원복 가능한 `hasUnsavedChanges`를 노출하고, 실제 편집 가능한 영속 필드만 snapshot으로 비교한다.
- 새 기본값과 로드 직후는 pristine이며, 저장/삭제 성공 뒤 baseline을 갱신해 dirty-state를 해제한다.
- 검증 오류, 저장/삭제 완료 신호, 캘린더 연결·조회·오늘/내일 상태, load 오류 등 transient 필드는 snapshot에서 제외했다.
- 공용 `UnsavedChangesDialog`와 `UnsavedChangesBackHandler`를 추가했다. 액션 라벨은 `저장`, `저장 안 함`, `취소`다.
- 세 편집 route 모두 clean back은 즉시 종료하고 dirty back은 dialog를 연다. 저장은 기존 저장/검증 경로를 사용하며, 검증 실패 시 dialog만 닫히고 화면에 남는다.
- 교대 그룹 draft는 clean back과 discard 모두 `discardGroupChanges()`를 거쳐 기존 `cancelDraft()` 정리를 완료한 뒤 기존 deleted-state navigation으로 종료한다.
- 하단 저장 버튼 배치, 알람 scheduling, 서버/API, 앱 버전은 변경하지 않았다.

## TDD 및 검증

- RED: focused ViewModel 테스트가 `hasUnsavedChanges` 부재로 실패하는 것을 확인했다.
- GREEN: `AlarmEditViewModelTest`, `ShiftGroupEditViewModelTest` focused 실행 통과.
- RED: Compose 테스트가 공용 dialog/back handler 부재로 실패하는 것을 확인했다.
- GREEN: `compileDebugAndroidTestKotlin` 통과.
- `git diff --check`로 whitespace 오류가 없음을 확인했다.

## 우려사항

- Android instrumentation 테스트는 빌드 환경에서 컴파일까지만 검증했으며, 연결된 기기/에뮬레이터 실행은 하지 않았다.

## Fix Round 1 — 비동기 저장 중 추가 입력 보호

- 리뷰 finding: 저장 완료 콜백이 saver/repository에 전달된 payload 대신 최신 UI state를 baseline으로 삼아, 저장 중 입력을 clean 처리하고 자동 이탈할 수 있었다.
- 수정 파일:
  - `app/src/main/java/com/smartworkalarm/presentation/alarmedit/AlarmEditViewModel.kt`
  - `app/src/main/java/com/smartworkalarm/presentation/shift/ShiftGroupEditViewModel.kt`
  - `app/src/test/java/com/smartworkalarm/presentation/alarmedit/AlarmEditViewModelTest.kt`
  - `app/src/test/java/com/smartworkalarm/presentation/shift/ShiftGroupEditViewModelTest.kt`
- 각 저장 시작 시 immutable payload와 영속 필드 snapshot을 캡처한다. 성공 baseline은 해당 payload snapshot으로 갱신하고, 완료 시 최신 UI snapshot이 다르면 최신 입력을 보존한 채 `saved=false`, `hasUnsavedChanges=true`를 유지한다.
- 새 알람은 저장 중 추가 입력이 있어도 반환된 ID를 state에 반영해 후속 저장이 새 insert로 반복되지 않게 했다.
- RED 명령: `./gradlew testDebugUnitTest --tests "com.smartworkalarm.presentation.alarmedit.AlarmEditViewModelTest.editing while alarm save is in flight stays dirty and does not exit" --tests "com.smartworkalarm.presentation.shift.ShiftGroupEditViewModelTest.editing group while save is in flight stays dirty and does not exit" --tests "com.smartworkalarm.presentation.shift.ShiftGroupEditViewModelTest.editing shift type while save is in flight stays dirty and does not exit"`
  - 결과: 3 tests, 3 failed. 세 경로 모두 기존 완료 처리의 `saved=true/clean`에서 실패했다.
- GREEN/회귀 명령: `./gradlew testDebugUnitTest --tests "com.smartworkalarm.presentation.alarmedit.AlarmEditViewModelTest" --tests "com.smartworkalarm.presentation.shift.ShiftGroupEditViewModelTest" compileDebugAndroidTestKotlin`
  - 결과: BUILD SUCCESSFUL. focused unit 31개(알람 11, 교대 20) 실패 0, AndroidTest Kotlin 컴파일 성공.
- `git diff --check` 성공.
