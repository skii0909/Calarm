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
