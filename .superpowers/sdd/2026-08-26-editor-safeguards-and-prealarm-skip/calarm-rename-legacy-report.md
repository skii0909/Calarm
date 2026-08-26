# Calarm Rename and Legacy Reservation Migration Report

## Delivered scope

- Rebranded user-facing app copy to `Calarm`, with the Korean descriptor `캘람 · 캘린더와 알람의 조합` in onboarding and the ringing-channel description.
- Updated the app label, onboarding, home header, regular ringing label, and the current v1.10 Galaxy S22 Ultra checklist.
- Kept package `com.smartworkalarm`, the `WorkAlarmApplication`, `WorkAlarmDatabase`, `Theme.WorkAlarm`, database filename `work-alarm.db`, version `1.10` / code `13`, and notification-channel IDs unchanged.
- Before each regular or shift schedule/cancel path, the scheduler now cancels the matching v1.9 package-scoped, data-less PendingIntent before using or cancelling the v1.10 URI-backed PendingIntent. Existing v1.10 URI identities and `AlarmClockInfo` scheduling remain unchanged.

## TDD evidence

1. Added legacy regular/shift reservation regression tests, then ran:

   ```powershell
   .\gradlew.bat :app:testDebugUnitTest --tests com.smartworkalarm.alarm.AlarmSchedulerImplTest
   ```

   RED: 3 failures showed that legacy regular snooze, regular cancellation, and shift cancellation identities remained scheduled.

2. Added the app-label regression test, then ran:

   ```powershell
   .\gradlew.bat :app:testDebugUnitTest --tests com.smartworkalarm.ProjectConfigurationTest
   ```

   RED: `application label is Calarm` failed because the resource still contained `Work Alarm`.

3. Implemented the minimal legacy cancellation and user-visible copy changes.

## Verification

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests com.smartworkalarm.ProjectConfigurationTest --tests com.smartworkalarm.alarm.AlarmSchedulerImplTest :app:compileDebugAndroidTestKotlin
```

Result: `BUILD SUCCESSFUL` (focused unit tests pass; Android instrumentation-test Kotlin sources compile).

## Review follow-up: package-replacement non-snooze coverage

- Added focused scheduler tests for the regular `schedule()` and shift `scheduleShift()` non-snooze paths used by package-replacement rescheduling.
- Each test first schedules the exact v1.9 package-scoped, data-less occurrence identity, then asserts rescheduling leaves exactly one action-matching v1.10 URI-backed occurrence with `AlarmClockInfo`.
- Mutation RED: temporarily removing the two schedule-path legacy cancellation calls produced three focused failures: the new regular and shift occurrence tests plus the existing regular snooze test. This proves the tests detect an old reservation remaining beside the new identity.
- GREEN command:

  ```powershell
  .\gradlew.bat :app:testDebugUnitTest --tests com.smartworkalarm.alarm.AlarmSchedulerImplTest :app:compileDebugAndroidTestKotlin
  ```

  Result: `BUILD SUCCESSFUL`.

## Self-review

- Checked the migration builder is implicit, package-scoped, has no data URI, and uses the required regular/shift request-code offsets.
- Confirmed the added tests install legacy identities without data and verify that only the URI-backed v1.10 snooze remains after rescheduling; cancellation tests cover regular and shift occurrence/snooze identities.
- Scanned user-facing source and the current checklist: no remaining `Work Alarm`, `WORK ALARM`, or `워크알람` copy. Historical plans/designs and the older device checklist were intentionally not changed.
- Scanned protected persistence identifiers and version values; all remain at their existing values.

## Deferred work / concern

- The complete release gate and final APK packaging were not run, as directed. The pre-existing untracked `artifacts/WorkAlarm-v1.10-debug.apk` was not renamed because it predates this change and would not contain the Calarm branding. Final packaging must create `artifacts/Calarm-v1.10-debug.apk` and record its byte size and SHA-256 in the checklist.
