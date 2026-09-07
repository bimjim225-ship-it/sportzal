# Sportzal MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the personal offline Android Sportzal MVP that imports an AI-authored workout program, logs sets in 3–5 seconds, survives process death, and exports one AI-ready JSON through the Android Share Sheet.

**Architecture:** One native Android app module. Imported program documents are validated and stored immutably as canonical JSON; Room stores only the current equipment catalog, a lightweight planned-workout index, runtime workouts, set facts, skipped slots and drafts. UI is Jetpack Compose; all training interpretation stays outside the app.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3 primitives with Sportzal theme tokens, Room, Kotlin Serialization, coroutines/Flow, Android Storage Access Framework, FileProvider, Android Sharesheet, JUnit, Room tests, Compose UI tests, instrumentation tests.

**Spec:** `docs/superpowers/specs/2026-09-06-sportzal-mvp-design.md`  
**Frozen decisions:** `docs/decisions/0001-development-readiness-freeze.md`  
**Behavior:** `UX-CONTRACT.md`  
**External data contract:** `docs/data-contracts.md`; JSON schemas are in `docs/schemas/`.

## Global Constraints

- One user, Android only, Russian UI, light theme only in MVP.
- No Firebase, backend, web client, login, network dependency or embedded LLM.
- App responsibilities are only DISPLAY → INPUT → TIMESTAMP → STORE → EXPORT.
- Ordinary set logging target: median ≤5 seconds in the real-device acceptance test.
- Minimum touch target: 48 dp; system font scaling remains enabled.
- A started workout is bound permanently to the program/equipment snapshot captured at start.
- `rest_target_sec` follows ADR-0001: target until the next set of the same `exercise_instance_id`; never infer readiness.
- Empty accidental workout cancellation follows ADR-0001.
- Stable `exercise_id` and canonical Room model follow ADR-0001.
- Raw set timestamps are facts; do not invent `actual_rest_sec`.
- Persist every committed set immediately; no “save whole workout at the end”.
- Use TDD for domain/data behavior and regression-sensitive UI behavior.
- Work in VS Code or Android Studio in the repository root. Run Gradle commands from the integrated terminal opened at the repository root. On Windows use `gradlew.bat`; on macOS/Linux use `./gradlew`.

---

## File Structure to Create

```text
sportzal/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/ru/sportzal/app/
│       │   │   ├── SportzalApplication.kt
│       │   │   ├── MainActivity.kt
│       │   │   ├── AppContainer.kt
│       │   │   ├── data/
│       │   │   │   ├── db/
│       │   │   │   │   ├── SportzalDatabase.kt
│       │   │   │   │   ├── Entities.kt
│       │   │   │   │   └── SportzalDao.kt
│       │   │   │   ├── repository/SportzalRepository.kt
│       │   │   │   └── files/
│       │   │   │       ├── ProgramImporter.kt
│       │   │   │       ├── ProgramValidator.kt
│       │   │   │       ├── SnapshotExporter.kt
│       │   │   │       └── SnapshotShareCoordinator.kt
│       │   │   ├── model/
│       │   │   │   ├── ExternalContracts.kt
│       │   │   │   ├── DomainModels.kt
│       │   │   │   └── TimeModels.kt
│       │   │   ├── domain/
│       │   │   │   ├── TodaySelector.kt
│       │   │   │   ├── WorkoutService.kt
│       │   │   │   ├── RotationSorter.kt
│       │   │   │   └── PrefillResolver.kt
│       │   │   ├── ui/
│       │   │   │   ├── theme/SportzalTheme.kt
│       │   │   │   ├── navigation/SportzalNav.kt
│       │   │   │   ├── components/
│       │   │   │   │   ├── SportzalButton.kt
│       │   │   │   │   ├── NumericField.kt
│       │   │   │   │   ├── RirSelector.kt
│       │   │   │   │   ├── SetResultRow.kt
│       │   │   │   │   └── ExerciseCard.kt
│       │   │   │   ├── today/
│       │   │   │   ├── workout/
│       │   │   │   ├── history/
│       │   │   │   └── equipment/
│       │   │   └── platform/
│       │   │       ├── ClockProvider.kt
│       │   │       └── FileIntentHandler.kt
│       │   └── res/xml/file_paths.xml
│       ├── test/java/ru/sportzal/app/
│       └── androidTest/java/ru/sportzal/app/
├── build.gradle.kts
├── settings.gradle.kts
└── gradle/libs.versions.toml
```

The exact number of Kotlin files may grow only when a class becomes difficult to reason about. Do not introduce feature modules, Hilt/Koin, networking, analytics or a generic “clean architecture” layer in MVP.

---

### Task 1: Android project shell and design-token adapter

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/ru/sportzal/app/SportzalApplication.kt`
- Create: `app/src/main/java/ru/sportzal/app/MainActivity.kt`
- Create: `app/src/main/java/ru/sportzal/app/ui/theme/SportzalTheme.kt`
- Create: `app/src/androidTest/java/ru/sportzal/app/ui/theme/SportzalThemeTest.kt`

**Interfaces:**
- Produces: `SportzalTheme(content: @Composable () -> Unit)` and a runnable empty app shell.
- Consumes: exact color/type/shape decisions from `DESIGN.md`.

- [ ] **Step 1: Generate the empty Android project in the repository root**

Use Android Studio, **File → New → New Project → Empty Activity**, package `ru.sportzal.app`, Kotlin, Compose enabled, minimum SDK 26. Keep one `app` module. Do not generate sample navigation/business logic.

- [ ] **Step 2: Add only MVP dependencies**

In `app/build.gradle.kts`, include Compose Material 3, Navigation Compose, Room runtime/ktx/compiler, Kotlin Serialization JSON and coroutine support. Use the stable mutually compatible versions offered by the current stable Android Studio/AGP toolchain at implementation time; dependency versions are build tooling, not a product contract.

Do **not** add Retrofit/Ktor, Firebase, Hilt, Koin, WorkManager, analytics or image-network libraries.

- [ ] **Step 3: Write a theme instrumentation test first**

Create `app/src/androidTest/java/ru/sportzal/app/ui/theme/SportzalThemeTest.kt` and assert a button under `SportzalTheme` is discoverable and has a touch target of at least 48 dp.

- [ ] **Step 4: Run the test and verify it initially fails or cannot compile**

Windows PowerShell in repository root:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Expected before implementation: compilation/test failure because `SportzalTheme` is incomplete.

- [ ] **Step 5: Implement the theme adapter**

In `SportzalTheme.kt`, map the exact DESIGN.md tokens into one Compose theme source. Keep light theme only. Use Roboto/system font, tabular numeric feature settings in the shared numeric text style, and 48 dp minimum controls.

- [ ] **Step 6: Run verification**

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest assembleDebug
```

Expected: all pass; debug APK produced.

- [ ] **Step 7: Commit**

```powershell
git add .
git commit -m "build: scaffold Sportzal Android app"
```

---

### Task 2: External JSON models and strict program validation

**Files:**
- Create: `app/src/main/java/ru/sportzal/app/model/ExternalContracts.kt`
- Create: `app/src/main/java/ru/sportzal/app/data/files/ProgramValidator.kt`
- Create: `app/src/test/java/ru/sportzal/app/data/files/ProgramValidatorTest.kt`
- Copy test fixtures from: `docs/examples/program.json`, `docs/examples/ai-snapshot.json`

**Interfaces:**
- Produces:
  - `ProgramDocument`
  - `AiSnapshotDocument`
  - `ProgramValidator.validate(json: String, knownEquipmentIds: Set<String>): ValidationResult<ProgramDocument>`
- Consumes: `docs/data-contracts.md` and schema v1 field names exactly.

- [ ] **Step 1: Write failing decoder/validator tests**

Cover at minimum:

```kotlin
@Test fun validProgramFixtureIsAccepted()
@Test fun unknownFieldIsRejected()
@Test fun unsupportedSchemaVersionIsRejected()
@Test fun oneExerciseRotationIsRejected()
@Test fun duplicateExerciseInstanceIdIsRejected()
@Test fun unorderedPlannedSetNumbersAreRejected()
@Test fun unknownEquipmentReferenceIsRejected()
@Test fun bodyweightWithNonZeroTargetIsRejected()
```

Use strict Kotlin Serialization models with `ignoreUnknownKeys = false`.

- [ ] **Step 2: Run only validator tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*ProgramValidatorTest"
```

Expected: FAIL before implementation.

- [ ] **Step 3: Implement DTOs matching schema v1 exactly**

Do not rename JSON fields internally in a way that changes serialization. Model `rir`/`target_rir` as nullable integer 0..4, `rest_target_sec` as non-negative integer and all IDs as strings.

- [ ] **Step 4: Implement semantic invariants**

`ProgramValidator` must check all semantic rules listed under `docs/data-contracts.md § Семантические проверки сверх Schema` that apply to `sportzal.program`.

Also enforce ADR-0001 stable-identity rule only as an **AI authoring contract**, not by guessing equivalence in app code. The app validates uniqueness/references; it does not decide whether an ID should have been reused.

- [ ] **Step 5: Re-run tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "*ProgramValidatorTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main app/src/test
git commit -m "feat: add strict Sportzal program contract"
```

---

### Task 3: Canonical Room database and repository invariants

**Files:**
- Create: `app/src/main/java/ru/sportzal/app/data/db/Entities.kt`
- Create: `app/src/main/java/ru/sportzal/app/data/db/SportzalDao.kt`
- Create: `app/src/main/java/ru/sportzal/app/data/db/SportzalDatabase.kt`
- Create: `app/src/main/java/ru/sportzal/app/data/repository/SportzalRepository.kt`
- Create: `app/src/androidTest/java/ru/sportzal/app/data/db/SportzalDatabaseTest.kt`

**Interfaces:**
- Produces `SportzalRepository` with transactional methods used by every later task.
- Consumes the exact logical table design from ADR-0001.

Use these repository signatures as the stable boundary:

```kotlin
interface SportzalRepository {
    suspend fun importProgram(validated: ProgramDocument, canonicalJson: String, canonicalHash: String): ImportResult
    fun observeTodayState(): Flow<TodayData>
    suspend fun startWorkout(programId: String, programVersion: Int, workoutInstanceId: String): String
    suspend fun cancelEmptyWorkout(workoutId: String): CancelEmptyResult
    suspend fun saveSet(command: SaveSetCommand): SaveSetResult
    suspend fun editSet(command: EditSetCommand): Unit
    suspend fun deleteSet(setResultId: String): Unit
    suspend fun skipSet(command: SkipSetCommand): Unit
    suspend fun restoreSkippedSet(workoutId: String, exerciseInstanceId: String, plannedSetNo: Int): Unit
    suspend fun finishWorkout(workoutId: String): CompletionStatus
    fun observeActiveWorkout(): Flow<WorkoutRuntime?>
    suspend fun snapshotSource(focusWorkoutId: String?): SnapshotSource
}
```

- [ ] **Step 1: Write database tests first**

Tests must prove:

```text
same program key + same hash => no-op
same program key + different hash => conflict
historical program JSON cannot be overwritten
only one active workout can exist
(workout, sequence_no) is unique
one planned slot cannot receive two set facts
cancel empty workout removes runtime + drafts
cancel after first set is rejected
new program import does not mutate active plan snapshot
draft is not exported as fact
```

- [ ] **Step 2: Run Room tests and observe failure**

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=ru.sportzal.app.data.db.SportzalDatabaseTest
```

- [ ] **Step 3: Implement exactly the ADR-0001 tables**

Create only:

```text
app_state
equipment
programs
program_workout_index
workouts
set_results
skipped_sets
drafts
```

Do not create `exercise_result` or deeply normalized plan tables.

- [ ] **Step 4: Implement transactions**

The following are one transaction each:

- import program + equipment upserts + workout index + active pointer;
- start workout + immutable plan/equipment snapshots;
- save set + sequence allocation + planned-slot exclusivity + draft removal;
- skip set + planned-slot exclusivity;
- delete set + derived state recalculation;
- cancel empty workout + draft removal;
- finish workout status update.

- [ ] **Step 5: Re-run Room tests**

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main app/src/androidTest
git commit -m "feat: add canonical local persistence"
```

---

### Task 4: Program import, immutable activation and Today selection

**Files:**
- Create: `app/src/main/java/ru/sportzal/app/data/files/ProgramImporter.kt`
- Create: `app/src/main/java/ru/sportzal/app/domain/TodaySelector.kt`
- Create: `app/src/main/java/ru/sportzal/app/ui/today/TodayViewModel.kt`
- Create: `app/src/main/java/ru/sportzal/app/ui/today/TodayScreen.kt`
- Create: `app/src/test/java/ru/sportzal/app/domain/TodaySelectorTest.kt`
- Create: `app/src/androidTest/java/ru/sportzal/app/ui/today/TodayScreenTest.kt`

**Interfaces:**
- `ProgramImporter.import(uri: Uri): ImportPreview`
- `ProgramImporter.confirm(preview: ImportPreview): ImportResult`
- `TodaySelector.select(activeWorkout, activeProgramWorkouts, consumedKeys): TodaySelection`

- [ ] **Step 1: Write Today selection tests**

Cover:

```text
active workout always wins
earliest unstarted planned_date wins, including past dates
same workout_instance_id consumed in an older program version stays consumed
cancelled empty runtime is not consumed
manual later workout remains selectable
exhausted program yields Plan completed
no program yields Import program
```

- [ ] **Step 2: Write import integration tests**

Cover valid preview, malformed JSON, wrong schema, unsupported version, same-version no-op and same-key conflicting-content rejection.

- [ ] **Step 3: Run tests and observe failure**

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest
```

- [ ] **Step 4: Implement import using the system picker**

Read the provided URI off the UI thread, cap at 10 MiB, validate before any DB write, show a preview, then call the transactional repository import only after explicit `Импортировать`.

- [ ] **Step 5: Implement Today UI**

Inactive navigation has only `Сегодня`, `История`, `Тренажёры`. Today shows facts and planned content, not readiness advice.

- [ ] **Step 6: Re-run tests and build**

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest assembleDebug
```

- [ ] **Step 7: Commit**

```powershell
git add app
git commit -m "feat: import programs and show Today state"
```

---

### Task 5: Clock model, workout lifecycle and accidental-start cancellation

**Files:**
- Create: `app/src/main/java/ru/sportzal/app/platform/ClockProvider.kt`
- Create: `app/src/main/java/ru/sportzal/app/model/TimeModels.kt`
- Create: `app/src/main/java/ru/sportzal/app/domain/WorkoutService.kt`
- Create: `app/src/test/java/ru/sportzal/app/domain/WorkoutServiceTest.kt`
- Create: `app/src/test/java/ru/sportzal/app/model/TimeModelsTest.kt`

**Interfaces:**

```kotlin
interface ClockProvider {
    fun wallNow(): Instant
    fun elapsedRealtimeMs(): Long
    fun bootIdOrNull(): String?
}
```

`WorkoutService` owns start/cancel/finish commands but delegates persistence to the repository.

- [ ] **Step 1: Write time-model tests**

Prove:

- same boot uses monotonic elapsed time;
- different/unknown boot uses non-negative wall-clock fallback and marks it approximate;
- wall clock moving backwards never produces negative displayed elapsed;
- editing a set does not change its clock anchor.

- [ ] **Step 2: Write cancellation tests**

Prove:

- empty active workout can be cancelled;
- drafts are discarded with it;
- navigation/block switching alone does not block cancellation;
- one committed set blocks cancellation;
- one committed skip blocks cancellation;
- blocked cancellation directs flow to normal early finish.

- [ ] **Step 3: Implement and run tests**

```powershell
.\gradlew.bat testDebugUnitTest
```

- [ ] **Step 4: Commit**

```powershell
git add app
git commit -m "feat: add resilient workout lifecycle"
```

---

### Task 6: Prefill, straight block and fast set logging

**Files:**
- Create: `app/src/main/java/ru/sportzal/app/domain/PrefillResolver.kt`
- Create: `app/src/main/java/ru/sportzal/app/ui/components/NumericField.kt`
- Create: `app/src/main/java/ru/sportzal/app/ui/components/RirSelector.kt`
- Create: `app/src/main/java/ru/sportzal/app/ui/components/SetResultRow.kt`
- Create: `app/src/main/java/ru/sportzal/app/ui/components/ExerciseCard.kt`
- Create: `app/src/main/java/ru/sportzal/app/ui/workout/WorkoutViewModel.kt`
- Create: `app/src/main/java/ru/sportzal/app/ui/workout/WorkoutScreen.kt`
- Create: `app/src/test/java/ru/sportzal/app/domain/PrefillResolverTest.kt`
- Create: `app/src/androidTest/java/ru/sportzal/app/ui/workout/FastSetLoggingTest.kt`

**Interfaces:**
- `PrefillResolver.resolve(slot, previousPlannedSlot, previousFact, draft, actualContext): SetDraft`
- `WorkoutViewModel.saveSet(exerciseInstanceId, plannedSetNo)`

- [ ] **Step 1: Write prefill tests from the contract**

Include:

```text
draft wins
new target uses target value
warmup -> work uses work target
identical target may reuse prior user-entered fact
equipment substitution clears weight
RIR is never copied
symptom/deviation flags are never copied
```

- [ ] **Step 2: Write Compose fast-path test**

Starting from a prefilled exercise card:

1. tap reps;
2. replace value;
3. tap required RIR once;
4. tap `Записать подход`;
5. assert saved row appears and next slot is ready.

The UI test is behavioral; the later real-device timing test determines whether the 3–5 second KPI is actually met.

- [ ] **Step 3: Implement shared numeric/RIR controls**

Accept comma and dot for weight entry; JSON/domain numeric value remains decimal number. Reps are integer. Keyboard must not cover `Записать подход`.

- [ ] **Step 4: Implement straight-block flow**

Show target text as:

```text
Ориентир до следующего подхода: 2:00
С записи: 1:34
```

Never generate readiness language from these numbers.

- [ ] **Step 5: Run unit + UI tests**

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest
```

- [ ] **Step 6: Commit**

```powershell
git add app
git commit -m "feat: add fast straight-set logging"
```

---

### Task 7: Rotation sorting without input/focus loss

**Files:**
- Create: `app/src/main/java/ru/sportzal/app/domain/RotationSorter.kt`
- Create: `app/src/test/java/ru/sportzal/app/domain/RotationSorterTest.kt`
- Create: `app/src/androidTest/java/ru/sportzal/app/ui/workout/RotationInteractionTest.kt`
- Modify: `WorkoutViewModel.kt`
- Modify: `WorkoutScreen.kt`

**Interfaces:**

```kotlin
fun sortRotation(
    cards: List<ExerciseRuntimeCard>,
    now: ClockReading
): List<ExerciseRuntimeCard>
```

- [ ] **Step 1: Write sorting tests**

Prove:

- never-started exercises stay above started ones in planned order;
- among started unfinished exercises, longest elapsed since latest set comes first;
- ties use `planned_order`;
- completed/skipped cards fall below active cards;
- user may still open any card manually.

- [ ] **Step 2: Write interaction regression tests**

While a field is focused or keyboard/menu/touch interaction is active, update clock and save another card; assert the edited card does not move until interaction ends and its draft remains attached to `exercise_instance_id`.

- [ ] **Step 3: Implement sort + reorder freeze**

Do not re-sort every second. Update timer text every second, but re-evaluate order only after a committed set/skip/delete, foreground return or explicit interaction release.

- [ ] **Step 4: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest
```

- [ ] **Step 5: Commit**

```powershell
git add app
git commit -m "feat: add stable rotation workflow"
```

---

### Task 8: Edit, delete, skip, deviations and manual block detours

**Files:**
- Create: `app/src/main/java/ru/sportzal/app/ui/workout/SetEditSheet.kt`
- Create: `app/src/main/java/ru/sportzal/app/ui/workout/DeviationSheet.kt`
- Create: `app/src/androidTest/java/ru/sportzal/app/ui/workout/WorkoutCorrectionsTest.kt`
- Modify: repository/domain/UI files from Tasks 3–7

**Interfaces:** existing repository edit/delete/skip/restore methods.

- [ ] **Step 1: Write regression tests**

Cover:

- edit retains `completed_at` and updates `edited_at`;
- delete frees planned slot and recalculates latest timer;
- extra set has `planned_set_no = null` and does not fill a slot;
- skip reason remains optional;
- restore skip reopens active slot;
- equipment change clears weight until manually entered;
- actual setup/equipment values are copied into facts;
- manual block detour does not fabricate skips or reorder plan.

- [ ] **Step 2: Implement secondary sheets**

Keep normal flow clean. `⋯` opens deviations/skip actions. Dangerous delete uses an app-owned confirmation. No success modal.

- [ ] **Step 3: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest
```

- [ ] **Step 4: Commit**

```powershell
git add app
git commit -m "feat: add workout corrections and exceptions"
```

---

### Task 9: Finish screen and AI snapshot generation

**Files:**
- Create: `app/src/main/java/ru/sportzal/app/data/files/SnapshotExporter.kt`
- Create: `app/src/main/java/ru/sportzal/app/ui/workout/FinishScreen.kt`
- Create: `app/src/test/java/ru/sportzal/app/data/files/SnapshotExporterTest.kt`

**Interfaces:**

```kotlin
class SnapshotExporter {
    suspend fun export(focusWorkoutId: String?): ExportedSnapshot
}

data class ExportedSnapshot(
    val exportId: String,
    val json: String,
    val filename: String
)
```

- [ ] **Step 1: Write snapshot tests against `docs/examples/ai-snapshot.json` semantics**

Prove:

- latest 24 finished workouts included;
- active workout included once;
- selected historical workout included even outside window;
- required program versions included;
- current equipment catalog included;
- each workout includes immutable `equipment_at_start`;
- drafts excluded;
- omitted count is truthful;
- no `actual_rest_sec` exists;
- edited fact replaces previous state with same IDs, not duplicate history.

- [ ] **Step 2: Implement deterministic serialization**

Use schema v1 field names. Reject export above 10 MiB with a user-facing retry/save explanation; never silently trim beyond the documented scope.

- [ ] **Step 3: Implement Finish facts-only UI**

Show duration, completed work-set count, exercises with facts and whether planned work remains. Primary CTA `Отправить JSON`; secondary `Закрыть`.

- [ ] **Step 4: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest
```

- [ ] **Step 5: Commit**

```powershell
git add app
git commit -m "feat: export AI-ready workout snapshots"
```

---

### Task 10: Android Share Sheet, Save file and Open-with import

**Files:**
- Create: `app/src/main/java/ru/sportzal/app/data/files/SnapshotShareCoordinator.kt`
- Create: `app/src/main/java/ru/sportzal/app/platform/FileIntentHandler.kt`
- Create: `app/src/main/res/xml/file_paths.xml`
- Modify: `AndroidManifest.xml`
- Modify: `MainActivity.kt`
- Create: `app/src/androidTest/java/ru/sportzal/app/platform/FileRoundTripTest.kt`

**Interfaces:**

```kotlin
interface SnapshotShareCoordinator {
    suspend fun prepareAndShare(focusWorkoutId: String?)
    suspend fun saveAs(focusWorkoutId: String?)
}
```

- [ ] **Step 1: Write intent/file tests where feasible**

Assert generated URI is `content://`, provider is not broadly exported, MIME is `application/json`, `FLAG_GRANT_READ_URI_PERMISSION` is present and incoming snapshot JSON is rejected by program importer with the correct user-facing classification.

- [ ] **Step 2: Configure narrow FileProvider**

Only expose the app cache export directory. Keep export files at least long enough for receiving apps to open them; cleanup follows data contract (>7 days, best-effort).

- [ ] **Step 3: Implement `ACTION_SEND` and `ACTION_CREATE_DOCUMENT`**

No Telegram/email SDK. Chooser cancellation is not success or failure. Room data remains untouched.

- [ ] **Step 4: Implement incoming `Open with Sportzal`**

Validate file contents, not extension/MIME alone. Route valid program to preview; reject snapshot as “Это файл результатов, а не программа”.

- [ ] **Step 5: Device-check round trip manually**

On the user’s Android phone:

1. finish a synthetic workout;
2. `Отправить JSON`;
3. choose Telegram or email;
4. open/download on desktop;
5. attach to ChatGPT;
6. save a returned `sportzal_program.json`;
7. open it with Sportzal;
8. verify import preview.

Record the device/app used and result in the PR/release notes.

- [ ] **Step 6: Commit**

```powershell
git add app
git commit -m "feat: add Android JSON file round trip"
```

---

### Task 11: History, equipment catalog and local photos

**Files:**
- Create: `app/src/main/java/ru/sportzal/app/ui/history/HistoryScreen.kt`
- Create: `app/src/main/java/ru/sportzal/app/ui/history/HistoryDetailScreen.kt`
- Create: `app/src/main/java/ru/sportzal/app/ui/equipment/EquipmentScreen.kt`
- Create: `app/src/main/java/ru/sportzal/app/ui/equipment/EquipmentEditSheet.kt`
- Create: `app/src/androidTest/java/ru/sportzal/app/ui/history/HistoryEquipmentTest.kt`

**Interfaces:** repository read/edit functions only; no analytics interfaces.

- [ ] **Step 1: Write UI tests**

Cover empty history, completed/ended-early rows, plan-vs-fact detail, edit/delete set from history, equipment text-only fallback and photo replacement without modifying historical facts.

- [ ] **Step 2: Implement history**

No charts, progress labels, tonnage or AI conclusions. `Отправить JSON` from detail sets `focus_workout_id` to the selected workout.

- [ ] **Step 3: Implement equipment**

System picker → copy selected photo into app-private storage → persist `photo_path`. Current catalog edits must not rewrite past workout facts or `equipment_at_start`.

- [ ] **Step 4: Run tests**

```powershell
.\gradlew.bat testDebugUnitTest connectedDebugAndroidTest
```

- [ ] **Step 5: Commit**

```powershell
git add app
git commit -m "feat: add local history and equipment catalog"
```

---

### Task 12: Resilience, accessibility and real-gym acceptance gate

**Files:**
- Create: `app/src/androidTest/java/ru/sportzal/app/ProcessDeathRecoveryTest.kt`
- Create: `app/src/androidTest/java/ru/sportzal/app/AccessibilitySmokeTest.kt`
- Create: docs/reviews/mvp-device-acceptance.md during execution with measured results only.
- Modify shared UI only when tests reveal a failure.

**Interfaces:** no new product features.

- [ ] **Step 1: Verify process/activity recreation**

Automated test must show:

- active workout resumes;
- committed sets remain;
- drafts remain;
- planned program version remains unchanged;
- timer derives from persisted anchors.

- [ ] **Step 2: Verify narrow layout and 200% font scale**

On emulator/device at 320 dp width and system font scale 200%:

- exercise name wraps;
- numeric values remain visible;
- RIR buttons stay ≥48 dp and wrap rather than shrink;
- keyboard does not obscure `Записать подход`;
- screen remains scrollable.

- [ ] **Step 3: TalkBack smoke test**

Verify focus does not jump during rotation reorder, timer is not announced every second, icon-only controls have Russian content descriptions, and after save only concise save feedback is announced.

- [ ] **Step 4: Run full automated verification**

Windows PowerShell in repository root:

```powershell
.\gradlew.bat clean testDebugUnitTest connectedDebugAndroidTest assembleDebug
python -m pip install -r requirements-docs.txt
python tools/validate_docs.py
git diff --check
```

Expected before claiming readiness: all commands exit 0.

- [ ] **Step 5: Measure the actual 3–5 second KPI**

On the user’s real phone, use a representative prefilled exercise card. Perform **10 ordinary logging trials**:

1. phone already on active Workout screen;
2. tap reps and change the value;
3. select RIR when requested;
4. tap `Записать подход`;
5. stop timing when saved row/next slot state is visible.

Record all ten times in docs/reviews/mvp-device-acceptance.md.

Acceptance:

```text
median <= 5.0 seconds
0 wrong-exercise saves
0 lost inputs
0 accidental duplicate saves
```

If it fails, fix UX friction before adding features.

- [ ] **Step 6: Verify real file roundtrip**

Record whether Telegram/email produces an actual JSON attachment usable on desktop and whether returned program JSON opens/imports on the phone.

- [ ] **Step 7: Final commit**

```powershell
git add .
git commit -m "test: verify Sportzal MVP on device"
```

---

## Final implementation acceptance checklist

Implementation is not complete until all are true:

```text
[ ] Android app builds from a clean checkout
[ ] docs validator passes
[ ] program import strictness matches v1 contracts
[ ] program versions are immutable
[ ] one active workout maximum
[ ] accidental empty start can be cancelled; factual workout cannot
[ ] set save is immediate, idempotent and slot-safe
[ ] straight workflow works
[ ] rotation workflow sorts by factual elapsed time without stealing input/focus
[ ] target rest is shown only as same-exercise reference, never interpreted
[ ] RIR null/4+ semantics preserved
[ ] edits preserve completed_at
[ ] skips/extra sets/equipment changes preserve plan-vs-fact truth
[ ] process death recovery works
[ ] finish/export snapshot matches schema and history scope
[ ] Android Share Sheet works with a real installed target
[ ] returned JSON imports through picker/Open with
[ ] history/equipment remain non-analytical
[ ] 320 dp, 200% font and TalkBack smoke tests pass
[ ] real-phone logging median <=5.0 s with zero wrong/duplicate saves
```

Only after this gate should work move to optional post-MVP items such as full backup/restore, timed exercises or cloud backup.
