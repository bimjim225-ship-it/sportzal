# Sportzal MVP Device Acceptance

This record intentionally separates automated activity recreation from a real Linux process restart. Blank
measurement fields have not been executed and are not evidence of a pass.

## Build

- Commit: to be recorded from the final PR7 HEAD
- APK: `app/build/outputs/apk/debug/app-debug.apk` (not installed on a real phone yet)
- Device: NOT YET VERIFIED
- Android: NOT YET VERIFIED
- Date: NOT YET VERIFIED

## Automated verification

- Activity recreation: covered by `ProcessDeathRecoveryTest`; local connected-device result to be recorded after execution.
- 320dp / 200% font and accessibility semantics: covered by `AccessibilitySmokeTest`; local connected-device result to be recorded after execution.
- Real process restart: not claimed by the automated suite.

## Install / launch

- Result: NOT YET VERIFIED

## Process restart

- Result: NOT YET VERIFIED
- Notes: On the same installed APK, start a workout, enter a draft, save one set, leave another slot unresolved,
  force-stop/kill the application process, and reopen it. Verify the workout ID, saved fact, draft, immutable program
  version, timer continuity, and absence of duplicate facts.

## Logging KPI

Each trial starts with the workout already open on a representative prefilled exercise card. Stop timing only when
the saved row or next planned slot is visible.

| Trial | Time, sec | Correct exercise | Input preserved | Duplicate |
|---|---:|---|---|---|
| 1 | | | | |
| 2 | | | | |
| 3 | | | | |
| 4 | | | | |
| 5 | | | | |
| 6 | | | | |
| 7 | | | | |
| 8 | | | | |
| 9 | | | | |
| 10 | | | | |

- Median: NOT YET VERIFIED
- Wrong-exercise saves: NOT YET VERIFIED
- Lost inputs: NOT YET VERIFIED
- Accidental duplicate saves: NOT YET VERIFIED
- Result: NOT YET VERIFIED

Acceptance requires an unrounded median of at most 5.0 seconds and zero wrong-exercise saves, lost inputs, and
accidental duplicate saves.

## 320dp / 200% font

- Result: NOT YET VERIFIED on a real device
- Notes: Verify the workout flow remains scrollable; the long exercise title and plan are readable; weight, reps,
  every RIR choice, and Save remain reachable while the keyboard is open.

## TalkBack

- Result: NOT YET VERIFIED
- TalkBack device: NOT YET VERIFIED
- Android version: NOT YET VERIFIED
- App SHA: NOT YET VERIFIED
- Date: NOT YET VERIFIED

- [ ] можно найти название упражнения
- [ ] weight/reps fields понятно подписаны
- [ ] RIR понятно подписан
- [ ] Save понятно подписан
- [ ] focus не прыгает самопроизвольно
- [ ] timer не объявляется каждую секунду
- [ ] icon-only actions имеют смысловое имя
- [ ] после Save feedback не заспамлен

Automated semantics tests do not constitute this manual TalkBack pass.

## JSON round-trip

- Share target: NOT YET VERIFIED
- Desktop received actual `.json` attachment: NOT YET VERIFIED
- AI/opening step: NOT YET VERIFIED
- Returned `sportzal.program` opened by Sportzal: NOT YET VERIFIED
- Preview correct: NOT YET VERIFIED
- No database write before Confirm: NOT YET VERIFIED on the real round-trip
- Result: NOT YET VERIFIED

## Final

**NOT YET VERIFIED — MVP READY = false.**

Real-device install, process restart, ten measured logging trials, 200% font inspection, TalkBack smoke, and the
external JSON attachment round-trip remain for the user to execute on the exact final APK. Do not merge this PR on
the basis of automated recreation tests alone.
