# Sportzal Data Contracts v1

## 1. Цель

JSON — единственный внешний интерфейс между Sportzal и ИИ в MVP.

Есть два публичных документа:

1. `sportzal.program` — ИИ → приложение;
2. `sportzal.ai_snapshot` — приложение → ИИ.

Канонические JSON Schema:

- `docs/schemas/program.schema.json`;
- `docs/schemas/ai-snapshot.schema.json`.

## 2. Общие правила

- encoding: UTF-8;
- timestamps: ISO-8601 с offset, например `2026-09-08T19:22:37+03:00`;
- planned calendar dates: `YYYY-MM-DD` без timezone;
- weight unit MVP: kg;
- IDs — стабильные строки, созданные ИИ или приложением;
- `schema_version` v1 — integer `1`;
- приложение не изменяет семантику импортированных тренировочных целей;
- timestamps являются первичными фактами времени;
- duration/rest могут вычисляться на стороне ИИ;
- unknown schema version должна отклоняться явно, а не интерпретироваться приблизительно.

## 3. `sportzal.program`

Минимальный пример:

```json
{
  "schema": "sportzal.program",
  "schema_version": 1,
  "program_id": "program-2026-09-08",
  "program_version": 3,
  "generated_at": "2026-09-06T16:30:00+03:00",
  "equipment_upserts": [
    {
      "equipment_id": "eq-chest-press-01",
      "name": "Жим от груди сидя",
      "setup_hint": "Сиденье 4",
      "weight_step_kg": 5,
      "notes": "Основной жимовой тренажёр"
    }
  ],
  "workouts": [
    {
      "workout_instance_id": "2026-09-08-A",
      "template_id": "A",
      "title": "Тренировка A",
      "planned_date": "2026-09-08",
      "blocks": [
        {
          "block_id": "A-01",
          "title": "Грудь / спина",
          "mode": "rotation",
          "exercises": [
            {
              "exercise_id": "chest-press",
              "title": "Жим от груди",
              "equipment_id": "eq-chest-press-01",
              "planned_order": 1,
              "rir_capture": "last_work_set",
              "planned_sets": [
                {
                  "set_no": 1,
                  "set_type": "work",
                  "target_weight_kg": 40,
                  "reps_min": 8,
                  "reps_max": 12,
                  "target_rir": 2,
                  "rest_target_sec": 120
                },
                {
                  "set_no": 2,
                  "set_type": "work",
                  "target_weight_kg": 40,
                  "reps_min": 8,
                  "reps_max": 12,
                  "target_rir": 2,
                  "rest_target_sec": 120
                },
                {
                  "set_no": 3,
                  "set_type": "work",
                  "target_weight_kg": 40,
                  "reps_min": 8,
                  "reps_max": 12,
                  "target_rir": 2,
                  "rest_target_sec": 120
                }
              ]
            }
          ]
        }
      ]
    }
  ]
}
```

### Почему `planned_sets` — массив

Даже если большинство упражнений имеют одинаковый вес/диапазон на всех трёх подходах, массив позволяет ИИ явно задать:

- разминочные подходы;
- разные веса;
- разные диапазоны повторений;
- разные цели RIR;
- разные интервалы отдыха.

Приложение не обязано самостоятельно разворачивать сложную тренировочную логику.

### `rir_capture`

- `none`;
- `last_work_set`;
- `all_work_sets`.

RIR UI не показывается без необходимости.

### `target_rir`

Целевой RIR — плановая информация. Фактический RIR хранится в snapshot отдельно.

### Equipment upsert

`equipment_upserts` делает файл программы самодостаточным: если ИИ впервые добавил тренажёр или уточнил его подпись/setup hint, пользователю не нужен второй JSON.

Локальная фотография не приходит из ИИ и не перезаписывается equipment upsert.

## 4. Local workout fact model

Внутренняя сущность workout сохраняет plan reference и факт.

Ключевые поля:

```json
{
  "workout_id": "local-uuid",
  "program_id": "program-2026-09-08",
  "program_version": 3,
  "workout_instance_id": "2026-09-08-A",
  "template_id": "A",
  "started_at": "2026-09-08T19:03:11+03:00",
  "finished_at": "2026-09-08T20:01:45+03:00",
  "completion_status": "completed"
}
```

`completion_status`:

- `active`;
- `completed`;
- `ended_early`.

## 5. Set result

```json
{
  "set_result_id": "local-uuid",
  "exercise_id": "chest-press",
  "planned_set_no": 2,
  "set_type": "work",
  "equipment_id_actual": "eq-chest-press-01",
  "weight_kg": 40,
  "reps": 11,
  "rir": 2,
  "completed_at": "2026-09-08T19:22:37+03:00",
  "edited_at": null,
  "deviations": []
}
```

### RIR

`rir`:

- `null`, если не собирался;
- `0..3` — точное bucket value;
- `4` — означает `4+`.

### Time semantics

`completed_at` — момент завершения физического подхода, зафиксированный нажатием `Записать подход`.

Если пользователь позже исправил вес или повторы:

- `completed_at` не меняется;
- `edited_at` получает время редактирования.

`equipment_id_actual` хранится на уровне конкретного подхода: если оборудование пришлось заменить между подходами, факт не теряется.

ИИ вычисляет отдых как разницу `completed_at` соседних подходов одного `exercise_id` в фактическом порядке.

## 6. Deviations

Допустимые коды v1:

```text
range_shortened
technique_changed
discomfort
setup_changed
equipment_changed
other
```

Код — факт пользовательской отметки, не диагноз и не оценка качества.

## 7. `sportzal.ai_snapshot`

Snapshot — самодостаточный транспортный пакет для анализа ИИ.

Пример верхнего уровня:

```json
{
  "schema": "sportzal.ai_snapshot",
  "schema_version": 1,
  "exported_at": "2026-09-08T20:02:10+03:00",
  "app": {
    "name": "Sportzal",
    "app_version": "0.1.0"
  },
  "equipment": [],
  "programs": [],
  "workouts": []
}
```

Snapshot содержит только сырые данные и структуру плана. Он не содержит поля вроде:

```text
progress_score
fatigue_score
recommended_weight
performance_rating
```

Такие поля запрещены архитектурной границей продукта.

## 8. History scope

Default AI snapshot включает последние 24 тренировки по `started_at`, плюс активную тренировку, если она существует.

Для всех включённых тренировок snapshot также включает все версии программ, на которые они ссылаются, и текущую активную программу.

Таким образом ИИ видит:

- что было запланировано;
- что фактически выполнено;
- точные timestamps;
- изменение программ между тренировками;
- оборудование и setup hints.

## 9. Rest calculation example

```text
set 1 completed_at = 19:17:14
set 2 completed_at = 19:19:31
```

ИИ может получить:

```text
137 секунд между завершениями подходов
```

Это не идеальная физиологическая «чистая пауза» до начала следующего подхода, потому что Sportzal фиксирует момент завершения, а не момент начала каждого подхода. Для практической динамики пользователя такая метрика последовательна и не требует ещё одного касания.

Если позже понадобится точное время начала подхода, это отдельное изменение схемы; в MVP оно намеренно не собирается.

## 10. Program import invariants

Помимо JSON Schema приложение проверяет:

1. `workout_instance_id` уникален внутри файла;
2. `block_id` уникален внутри workout;
3. `exercise_id` уникален внутри одного block;
4. `planned_set_no`/`set_no` возрастают и уникальны внутри exercise;
5. `straight` block содержит ровно одно exercise;
6. `rotation` block содержит минимум два exercise;
7. каждый `equipment_id` либо уже известен локально, либо присутствует в `equipment_upserts`;
8. `reps_min <= reps_max`;
9. `target_weight_kg >= 0`;
10. `rest_target_sec >= 0`;
11. planned dates валидны.

Это структурная валидация, а не тренировочная интерпретация.

## 11. Import version behavior

- `schema_version != 1` → файл не импортируется;
- повторный импорт того же `program_id + program_version` допустим только как явная замена previewed пользователем;
- новая версия программы не переписывает plan snapshot уже начатой тренировки;
- исторические программы, на которые ссылаются workouts, сохраняются.

## 12. Share behavior

Snapshot filename:

```text
sportzal-ai-2026-09-08-2002.json
```

MIME:

```text
application/json
```

Android Share Sheet является транспортом. Sportzal не содержит Telegram/email SDK и не требует знания конкретного канала отправки.

## 13. Privacy

Snapshot содержит тренировочную историю пользователя. Приложение не загружает её автоматически никуда. Передача начинается только по явному действию `Отправить JSON`, после чего выбор получателя контролируется Android Share Sheet.
