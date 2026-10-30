# Calarm 1.11.0 Outlook integration validation

Date: 2026-09-19. Branch: `codex/outlook-direct-login`.

## Delivered behavior

Calarm launches Microsoft browser authentication through MSAL, lists owned calendars, preserves separate Google/Outlook selections, and reads Microsoft Graph calendarView pages into an atomic local snapshot. Ordinary and shift alarms consume local data without network requests during alarm execution. Background sync, manual refresh, reconnect and disconnect are wired into settings and scheduling.

Snapshots expire after six hours. Revoked permissions invalidate the snapshot until a complete successful sync, including when a later retry encounters a network error. Repeated synchronization does not repost an already delivered pre-alarm for the same occurrence. A changed schedule can remove an ineligible pre-alarm.

The existing APK's physical-device validation is accepted per the user's instruction. It is not used as evidence of live Outlook authentication.

## Verification

- Full `testDebugUnitTest`: 372 tests, zero failures, errors or skipped tests.
- `lintDebug`: zero errors, 49 warnings. Warnings include dependency update suggestions, API constant checks, launcher resources and KTX suggestions; see generated lint report for exact findings.
- `assembleDebug` and Android instrumentation APK compilation: successful.
- UI emulator validation: all 11 tests passed (Outlook connection card, onboarding and settings) on Android 17 Small_Phone. An initial run exposed an obsolete pre-existing help-text expectation; the expectation now matches the current one-time/repeating alarm help. Subsequent runs suffered emulator memory pressure. The final successful run used a read-only temporary AVD with Google background services disabled; the original AVD was preserved. This validates UI actions with test state, not live Microsoft authentication. Log: `.superpowers/sdd/2026-09-19-outlook-direct-login/ui-final-isolated.log`.
- Fresh independent code review found and prompted fixes for revoked-cache reactivation and repeated pre-alarm notifications. Regression tests pass in the full unit suite.
- `git diff --check`: no whitespace errors (Git also reports Windows line-ending normalization notices).

## APK

`artifacts/Calarm-v1.11.0-outlook-setup-required-debug.apk`

Package `com.smartworkalarm`, versionName `1.11.0`, versionCode `18`, minSdk `26`.

SHA-256: `375EC54803E767267DA97388D133519D692B707580590A6E5AB2335EB7947EAD`.

## External dependency and remaining live checks

The user has not registered the Microsoft Entra application. This APK intentionally disables Microsoft login and shows setup-required guidance because no client ID is configured. Mocked/unit/UI tests do not prove an actual Microsoft account login, redirect callback, tenant consent policy or live Graph response.

Follow [app registration instructions](../setup/outlook-app-registration.md), provide the public Application (client) ID, rebuild with `-PoutlookClientId=...`, then verify personal/organization login, calendar selection, live event synchronization, reconnect and disconnect. No client secret is required.

Existing uncommitted product changes were preserved in the same checkout. No merge or push was performed.
