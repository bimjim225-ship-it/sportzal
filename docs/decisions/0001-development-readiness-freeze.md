# ADR-0001 — Development Readiness Freeze

**Status:** Accepted  
**Date:** 2026-09-06  
**Scope:** Sportzal MVP  
**Decision owner:** product owner + specification review

## Purpose

This ADR closes the remaining implementation-level ambiguities found after the reviewed documentation was merged to `main`.

It does **not** expand the product. It freezes the decisions required to start implementation without inventing product behavior in code.

### Precedence

For the four decisions below, this ADR has precedence over older wording in `PRODUCT.md`, `UX-CONTRACT.md`, `docs/data-contracts.md` and `docs/superpowers/specs/2026-09-06-sportzal-mvp-design.md` if that wording can be read differently.

All other requirements remain governed by those documents and the JSON Schema files.

---

## Decision 1 — `rest_target_sec` means return to the same exercise

### Problem

The previous text could be read as “pause until the next action”. That is ambiguous in a `rotation` block because the next action is normally another exercise, while the visible timer and the training analysis are tied to returning to the same exercise.

### Decision

`rest_target_sec` is the **planned minimum elapsed time from saving the current set until starting the next planned set of the same `exercise_instance_id`**.

It is an instruction from the external AI, not a measured fact and not an app recommendation.

The app:

- shows the target as reference text, e.g. `Ориентир до следующего подхода: 2:00`;
- shows the independent factual timer `С записи: 2:34`;
- does not generate `готов`, `рано`, `пора`, readiness scores, colors or alerts from the comparison;
- never exports the difference as `actual_rest_sec`;
- continues to preserve raw `completed_at`, `clock` and `sequence_no` so the external AI can reason from the available facts.

In `straight`, this is the usual pause between sets of the same exercise.

In `rotation`, time spent performing the other exercise(s) naturally counts toward the elapsed timer before returning to this exercise.

Example:

```text
Chest set saved          19:10:00
Row set saved            19:11:15
Chest target             02:00
Chest timer at 19:12:00  02:00
```

The app only displays the target and timer. It does not decide whether the next chest set should start.

### Schema impact

None. `rest_target_sec` remains a required non-negative integer in schema v1.

`0` means no minimum wait is specified by the program for that planned set; it does **not** mean the app recommends immediate execution.

---

## Decision 2 — Accidental empty workout can be cancelled

### Problem

Starting a workout immediately creates a runtime workout and marks that planned instance as started. A mistaken tap must not permanently consume the planned session when no training fact exists yet.

### Decision

An active workout exposes `Отменить ошибочный запуск` only while it contains **no factual training event**.

A factual training event is any of:

- a committed `set_result`;
- a committed `skipped_set`.

Draft field values, navigation between blocks and opening menus are not factual training events.

Cancellation:

1. requires a lightweight app-owned confirmation because it discards the runtime draft;
2. deletes the active `workout` and all its drafts in one Room transaction;
3. does not create `ended_early` history;
4. does not export a tombstone;
5. makes `(program_id, workout_instance_id)` eligible to start again;
6. does not alter the imported program or equipment catalog.

Once the first `set_result` or `skipped_set` is committed, accidental cancellation is unavailable. The user can only finish the workout normally or `Завершить с невыполненными подходами`, producing `ended_early` when appropriate.

This is a local correction of an empty runtime session, not a training interpretation.

---

## Decision 3 — Stable semantic exercise identity across program versions

### Problem

Longitudinal AI analysis depends on a stable semantic identity and must not rely on display titles.

### Decision

Within one `program_id`, if an exercise represents the same semantic movement across program versions, the external AI **must preserve `exercise_id`** even when any of these change:

- title wording;
- equipment;
- setup hint;
- target weight;
- rep range;
- RIR target/capture policy;
- block position;
- `exercise_instance_id`.

A new `exercise_id` is created only when the external AI intentionally considers it a different semantic exercise for longitudinal comparison.

`exercise_instance_id` remains the unique position/key of a concrete exercise occurrence inside one planned workout. It is not the longitudinal identity.

The app never tries to infer semantic equality from titles.

---

## Decision 4 — Canonical Room persistence model

### Problem

The architecture document previously allowed either deep normalization of program plans or validated raw JSON plus indexes. Leaving both open would move a product-level architectural choice into implementation and could produce incompatible code paths.

### Decision

MVP uses **immutable validated program JSON + small relational runtime/index tables**.

Do not deeply normalize blocks, exercises and planned sets into Room tables in v1.

Do not create a mutable aggregate `exercise_result` table.

### Canonical logical tables

#### `app_state`

Singleton row.

```text
id = 1
active_program_id nullable
active_program_version nullable
```

Purpose: explicit active-program pointer without encoding mutable active state into immutable program versions.

#### `equipment`

```text
equipment_id PK
name
setup_hint nullable
weight_step_kg nullable
available_weights_json nullable
notes nullable
photo_path nullable
updated_at
```

This is the current local catalog only. Historical plan/equipment context is snapshotted elsewhere.

#### `programs`

Composite primary key `(program_id, program_version)`.

```text
program_id
program_version
schema_version
canonical_json
canonical_hash
generated_at
imported_at
```

Rules:

- `canonical_json` is the validated external document stored immutably;
- same key + same `canonical_hash` is import no-op;
- same key + different hash is rejected;
- imported historical rows are never updated in place.

#### `program_workout_index`

Composite primary key `(program_id, program_version, workout_instance_id)`.

```text
program_id
program_version
workout_instance_id
template_id
title
planned_date
planned_order
```

This is a query index derived during successful import. It contains no independent training logic and can always be rebuilt from `programs.canonical_json`.

The Today screen uses this table plus `workouts` to find the earliest unstarted planned workout.

A planned workout is considered consumed when a non-cancelled runtime row exists with the same `(program_id, workout_instance_id)`, regardless of which version originally started it. This preserves the contract that moving a date between versions keeps the same `workout_instance_id`.

#### `workouts`

```text
workout_id PK
program_id
program_version
workout_instance_id
template_id
started_at
finished_at nullable
completion_status = active | completed | ended_early
plan_snapshot_json
equipment_at_start_json
notes nullable
```

Required invariants:

- maximum one `active` workout;
- start is transactional;
- `plan_snapshot_json` is immutable after start;
- `equipment_at_start_json` is immutable after start;
- accidental empty cancellation physically removes this runtime row and its drafts;
- new program imports never modify existing workout snapshots.

#### `set_results`

```text
set_result_id PK
workout_id FK
sequence_no
exercise_instance_id
planned_set_no nullable
set_type
exercise_id_actual
title_actual
equipment_id_actual nullable
equipment_name_actual nullable
setup_actual nullable
load_basis_actual
side_actual
weight_kg
reps
rir nullable
completed_at
boot_id nullable
elapsed_realtime_ms nullable
edited_at nullable
deviations_json
note nullable
```

Required uniqueness:

- `(workout_id, sequence_no)`;
- `(workout_id, exercise_instance_id, planned_set_no)` for non-null `planned_set_no`.

The set row is the source of truth for performed work. No separate mutable exercise aggregate is stored.

#### `skipped_sets`

Composite primary key `(workout_id, exercise_instance_id, planned_set_no)`.

```text
workout_id FK
exercise_instance_id
planned_set_no
recorded_at
reason nullable
note nullable
```

A planned slot cannot simultaneously exist in `set_results` and `skipped_sets`; enforce this transactionally in repository/domain logic and tests.

#### `drafts`

Composite primary key `(workout_id, exercise_instance_id, planned_set_no)`.

```text
workout_id FK
exercise_instance_id
planned_set_no
weight_text
reps_text
rir nullable
actual_context_json nullable
updated_at
```

Drafts are UI recovery state, not training facts and not part of AI snapshot.

They are deleted after successful set commit, explicit discard or accidental empty-workout cancellation.

### Why this model

It minimizes schema surface while preserving the hard invariants:

- exact immutable program versions;
- cheap Today queries;
- exact started-workout snapshots;
- transactional set logging;
- process-death recovery;
- reliable AI export;
- no duplicated mutable aggregates that can drift from set facts.

---

## Development gate

After this ADR, implementation must not invent alternative behavior for the four decisions above.

The implementation plan is `docs/superpowers/plans/2026-09-06-sportzal-mvp-implementation-plan.md`.

A change to these frozen decisions requires a new ADR or an explicit update to this ADR, plus synchronized contract/tests where applicable.
