# Task 1 Report — Required Permission Integration

## RED evidence

Command:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.smartworkalarm.permissions.RequiredPermissionTest --console=plain
```

Result: `:app:compileDebugUnitTestKotlin FAILED` because `RequiredPermission` was an unresolved reference in the new test file. This is the expected failure before the production model existed.

## Files changed

- `app/src/main/java/com/smartworkalarm/permissions/RequiredPermission.kt`
- `app/src/main/java/com/smartworkalarm/permissions/RequiredPermissionsController.kt`
- `app/src/main/java/com/smartworkalarm/presentation/permissions/RequiredPermissionsPrompt.kt`
- `app/src/main/java/com/smartworkalarm/MainActivity.kt`
- `app/src/main/java/com/smartworkalarm/presentation/calendar/CalendarConnectionPrompt.kt`
- `app/src/test/java/com/smartworkalarm/permissions/RequiredPermissionTest.kt`

## Behavior

- Computes missing permissions in the required fixed order: calendar, notifications, exact alarm, full-screen alarm.
- Applies the Android 13, 12, and 14 SDK gates and obtains Android state through `ContextCompat`, `AlarmManager`, and `NotificationManager`.
- Presents one root-level prompt listing all missing items and takes the first unresolved item to a runtime launcher or special-access setting. The special-access intent falls back to app details when unresolved.
- Rechecks on resume and retains a Later dismissal only for the current `MainActivity` session.
- Prevents `CalendarConnectionPrompt` from rendering or directly requesting calendar permission while calendar permission is missing, so it cannot compete with the unified runtime prompt.
- Leaves the alarm full-screen notification and lock-screen activity paths unchanged.

## GREEN evidence

Command:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.smartworkalarm.permissions.RequiredPermissionTest compileDebugKotlin --console=plain
```

Result: `BUILD SUCCESSFUL` in 17 seconds; the focused `RequiredPermissionTest` suite (3 tests) and `compileDebugKotlin` completed successfully.

Additional regression check:

```powershell
.\gradlew.bat testDebugUnitTest --console=plain
```

Result: `BUILD SUCCESSFUL` in 4 seconds.

## Self-review

- Verified the pure API covers SDK 34 all-missing, SDK 30 calendar-only, and all-granted cases with hand-derived expected values.
- Verified the root UI only invokes runtime launchers for runtime permissions and settings intents for special permissions.
- Verified the existing calendar prompt no longer owns a runtime permission launcher, following the ledger ruling.
- Ran `git diff --check`; no whitespace errors were reported.

## Commit

`feat: check all required alarm permissions`

## Concerns

- Special-access setting intent resolution and Android 14 full-screen permission behavior still need physical-device validation because JVM unit tests cannot exercise OEM system settings screens.
