# Sportzal UX Contract

## Product context

- **Audience:** один владелец Android-смартфона.
- **Primary jobs:** увидеть план; быстро записать подход; увидеть время с предыдущего подхода; продолжить тренировку после сворачивания/закрытия; отправить один JSON после тренировки.
- **Target market:** персональный продукт, без требований массового рынка.
- **Active locale:** `ru-RU`.
- **Language/content register:** короткий утилитарный русский; `RIR` и `kg` допустимы как технические обозначения.
- **Timezone/calendar policy:** локальное время устройства; `planned_date` трактуется как локальная календарная дата без timezone-конвертации.
- **Accessibility target:** WCAG 2.2 AA как ориентир плюс Android accessibility semantics; touch-targets не меньше 48 dp.

## Business-context sources

| Domain / scope | Authoritative source | Source type | Reviewed date |
|---|---|---|---|
| Product boundary / AI outside | `PRODUCT.md` | Product contract | 2026-09-06 |
| JSON lifecycle | `docs/data-contracts.md` | Data contract | 2026-09-06 |
| Visual behavior | `DESIGN.md` | Design contract | 2026-09-06 |

В MVP нет permissions, billing, auth, legal или multi-user lifecycle.

## Visual contract

- **Project `DESIGN.md`:** `DESIGN.md`.
- **Token ownership model:** до реализации `DESIGN.md` нормативен; после появления Compose theme точное сопоставление фиксируется в том же changeset.
- **Supported themes:** light only в MVP.
- **Design review policy:** новые экраны не должны добавлять отдельную визуальную грамматику без обновления `DESIGN.md`.

## Canonical UI Map

| Capability | Canonical owner | Source of truth | Allowed variants | Verification |
|---|---|---|---|---|
| Primary button | shared Compose `SportzalButton` | `DESIGN.md` | primary / secondary / danger | component test + screenshot |
| Numeric input | shared `NumericField` | `DESIGN.md` | weight / reps | keyboard + state test |
| RIR input | shared `RirSelector` | `PRODUCT.md` | 0..4+ | state test |
| Workout card | shared `ExerciseCard` | this contract | straight / rotation / completed | UI test |
| Set row | shared `SetResultRow` | this contract | normal / edited / warmup | UI test |
| Overflow deviations | shared app-owned menu/bottom sheet | this contract | exercise / set | keyboard/touch test |
| File import | `ProgramImportCoordinator` | `docs/data-contracts.md` | picker / Open with | integration test |
| JSON share | `SnapshotShareCoordinator` | `docs/data-contracts.md` | Android Share Sheet | device test |

## Navigation

### Inactive state

Primary destinations:

1. `Сегодня`;
2. `История`;
3. `Тренажёры`.

### Active workout state

После начала тренировки открывается отдельный full-screen workout flow. Bottom navigation скрыта.

Back:

- не завершает тренировку;
- возвращает на `Сегодня` только после app-owned подтверждения ухода с active workout screen, если пользователь явно нажимает системный Back;
- сама тренировка остаётся активной и полностью сохранённой.

На `Сегодня` при наличии активной тренировки главное действие — `Продолжить тренировку`.

## Home / Сегодня

Порядок приоритетов:

1. активная незавершённая тренировка;
2. тренировка с `planned_date == today`;
3. ближайшая будущая тренировка;
4. если программы нет — `Импортировать программу`.

Экран не делает вывод, надо ли пользователю тренироваться сегодня. Он только отображает состояние программы.

## Start workout

`Начать тренировку`:

- создаёт локальный `workout_id`;
- фиксирует ссылку на `program_id`, `program_version`, `workout_instance_id`, `template_id`;
- копирует необходимый план в immutable snapshot тренировки;
- записывает `started_at`;
- открывает первый блок.

После создания активной тренировки последующий импорт программы не изменяет уже начатую тренировку.

## Workout screen

### Общая структура

```text
← Тренировка A                       31:42

Блок 1 · грудь / спина
[ карточки активного блока ]

Блок 2 · ноги
[ свернутый preview ]

...

Завершить тренировку
```

Header показывает elapsed session time как арифметику `now - started_at`. Никакой периодический background-job для таймера не является источником истины.

### Straight block

- содержит ровно одно упражнение;
- карточка остаётся наверху до выполнения всех запланированных подходов или явного завершения/пропуска;
- таймер после первого сохранённого подхода показывает `now - completed_at(previous set)`.

### Rotation block

- содержит 2–N упражнений;
- до первого подхода упражнения без timestamps сохраняют `planned_order` и располагаются выше уже начатых упражнений;
- после того как у упражнения появился `completed_at`, его динамический ключ — elapsed time с последнего подхода;
- среди начатых упражнений большее elapsed time располагается выше;
- пользователь может нажать любую карточку независимо от позиции;
- никакого текста `пора`, `готов`, `слишком рано` приложение не генерирует;
- плановый отдых показывается как справочный параметр рядом с планом.

Сортировка пересчитывается после сохранения подхода и при возвращении приложения в foreground. Визуальный таймер обновляется на экране регулярно, но timestamps остаются единственным источником данных.

## Exercise card

Карточка показывает:

- название;
- локальное фото, если есть;
- setup hint;
- `completed sets / planned sets`;
- текущий план;
- timer;
- список уже выполненных set rows;
- поля следующего подхода;
- RIR selector только при необходимости;
- `Записать подход`;
- `⋯`.

### Prefill

Следующий подход получает:

1. вес и повторы предыдущего фактического подхода этого упражнения в текущей тренировке;
2. если предыдущего подхода нет — `target_weight_kg` и разумное стартовое значение повторов из конкретного planned set;
3. пользователь всегда может изменить значения до сохранения.

Prefill — удобство ввода, а не рекомендация.

## Save set

После `Записать подход` приложение атомарно:

1. валидирует локальные типы данных: вес >= 0, reps >= 0, требуемый RIR выбран;
2. сохраняет set result в Room;
3. фиксирует `completed_at = now`;
4. очищает временные UI-поля следующего подхода и создаёт prefill;
5. обновляет `n / total`;
6. запускает отображаемый timer через разницу timestamps;
7. в rotation-блоке пересортировывает карточки.

Не показывать success-dialog. Допустим короткий haptic feedback и тихая визуальная фиксация сохранённой строки.

Если запись в Room не удалась, карточка сохраняет введённые значения и показывает локальную ошибку с действием `Повторить`.

## Edit set

Тап по сохранённой строке открывает редактирование.

При изменении:

- `completed_at` не меняется;
- записывается/обновляется `edited_at`;
- timer следующего подхода продолжает опираться на исходный `completed_at`;
- можно изменить weight, reps, RIR, фактическое оборудование и deviation flags.

Удаление сохранённого подхода — редкое действие с app-owned confirmation, поскольку оно меняет временную цепочку анализа.

## RIR

Показывать только если planned exercise требует его для текущего рабочего подхода.

- `none`: selector отсутствует;
- `last_work_set`: selector только на последнем planned work set;
- `all_work_sets`: selector на каждом planned work set;
- warmup set не требует RIR, если это отдельно не указано в данных будущей версии схемы.

`4+` экспортируется как `rir: 4`, где контракт определяет 4 как capped bucket `>=4`.

## Deviations / ⋯

В обычном потоке пользователь ничего не отмечает.

Доступные коды MVP:

- `range_shortened`;
- `technique_changed`;
- `discomfort`;
- `setup_changed`;
- `equipment_changed`;
- `other`.

При `equipment_changed` можно выбрать другую запись из локального equipment catalog.

Свободный комментарий необязателен и короткий.

## Skip / incomplete

Упражнение можно пометить как пропущенное через `⋯`.

Причина необязательна; доступные quick reasons:

- `equipment_busy`;
- `time_limit`;
- `fatigue`;
- `discomfort`;
- `other`.

Невыполненные planned sets не удаляются из плана. Snapshot позволяет ИИ видеть разницу plan/fact.

## Block completion

Когда все упражнения блока выполнены или явно пропущены:

- блок сворачивается;
- появляется следующее плановое содержимое;
- приложение не показывает оценку качества блока.

MVP не реализует свободное автоматическое перемешивание блоков. Это сохраняет простой и сравнимый тренировочный поток. Если позже реальные условия зала потребуют ручного переключения блоков, это отдельное изменение контракта.

## Finish workout

`Завершить тренировку`:

- если остались planned sets, показывает app-owned confirmation `Завершить с невыполненными подходами?`;
- после подтверждения пишет `finished_at` и `completion_status`;
- все фактические данные уже находятся в Room, поэтому завершение не является «сохранением всей тренировки»;
- открывает Finish screen.

### Finish screen

Показывает только факты:

- длительность;
- число выполненных рабочих подходов;
- число упражнений с фактическими подходами;
- наличие невыполненных planned sets.

Не показывает «эффективность», «прогресс», калории или motivational score.

Primary action:

> **Отправить JSON**

Secondary:

> **Закрыть**

## Share JSON

`Отправить JSON`:

1. формирует snapshot по `docs/data-contracts.md`;
2. сохраняет временный `.json` в app cache;
3. публикует файл через Android `FileProvider`/content URI;
4. вызывает `ACTION_SEND` с MIME `application/json` и read permission;
5. Android показывает системный Share Sheet;
6. Sportzal не знает и не хранит, выбрал пользователь Telegram, email, Drive или другой клиент.

Filename:

```text
sportzal-ai-YYYY-MM-DD-HHmm.json
```

Та же кнопка `Отправить последний JSON` доступна на экране `Сегодня` после завершённой тренировки, чтобы файл можно было отправить позже без повторной тренировки.

## Import program

Два entry point:

1. `Импортировать программу` → Android system document picker;
2. compatible JSON, переданный Android через `Open with Sportzal`.

Import flow:

1. прочитать файл;
2. проверить JSON syntax;
3. проверить `schema == sportzal.program`;
4. проверить `schema_version`;
5. проверить структурные инварианты по schema/контракту;
6. показать короткий preview: версия программы, ближайшие даты, число тренировок/упражнений;
7. `Импортировать`;
8. в одной транзакции сохранить программу и `equipment_upserts`;
9. сделать новую программу активной для будущих тренировок.

Приложение не оценивает качество программы.

### Import errors

Показывать конкретно:

- `Файл не является JSON`;
- `Неподдерживаемая схема`;
- `Нужна schema_version 1`;
- `Не найдено поле workouts[0].blocks`;
- `Программа ссылается на неизвестный equipment_id`, если это нарушает контракт.

Введённые/существующие данные не изменяются при неуспешном импорте.

## History

История — простой список завершённых и незавершённых тренировок:

```text
08 сен · A · 58 мин
05 сен · B · 61 мин
01 сен · A · 55 мин
```

Detail показывает план против факта и set rows. Графиков и аналитических выводов нет.

## Equipment

Equipment list показывает:

- название;
- локальное фото, если привязано;
- setup hint;
- необязательные заметки.

Из приложения можно привязать/сменить локальную фотографию. AI metadata хранится и экспортируется, но приложение не обязано визуализировать все технические поля.

## Async and resilience

- **Mutation model:** локальные записи pessimistic/transactional; UI считает set сохранённым только после успешной записи Room.
- **Auto-save:** каждый set сохраняется сразу; active workout восстанавливается после process death.
- **Offline:** полная функциональность без сети.
- **Network:** отсутствует как продуктовая зависимость MVP.
- **Timer resilience:** таймер вычисляется из persisted timestamp; приложение не зависит от живущего background timer.
- **Duplicate submit:** кнопка `Записать подход` блокируется на время локальной транзакции.
- **Program import:** атомарная транзакция; partial import запрещён.
- **Share failure:** snapshot остаётся регенерируемым из Room; пользователь может повторить `Отправить последний JSON`.

## Validation

- Kotlin serialization/deserialization + отдельные structural invariants;
- JSON Schema в `docs/schemas` является нормативной документацией интерфейса, но runtime validator может быть компактнее при эквивалентном результате;
- пользовательские числовые поля валидируются непосредственно перед локальным save;
- ошибки не очищают введённые значения;
- программа не импортируется частично.

## Accessibility and touch

- минимум 48 dp для touch targets;
- content descriptions для icon-only controls;
- таймер не зависит только от цвета;
- dynamic sorting не должна похищать accessibility focus;
- при включённом TalkBack после сохранения set объявляется факт сохранения, но не весь пересортированный список;
- системная клавиатура должна закрываться одним действием и не перекрывать primary save control.

## Verification

До релиза MVP обязательны:

- unit tests сортировки rotation-блока;
- unit tests timestamp/rest derivation;
- Room persistence tests;
- import success/failure tests;
- snapshot serialization tests against examples/schema;
- process-death/resume instrumentation test;
- Compose UI test быстрого сохранения set;
- device test Android Share Sheet через `ACTION_SEND`;
- device test `Open with Sportzal` для JSON;
- narrow-phone screenshot review;
- TalkBack smoke test;
- font scale 200% smoke test на ключевых экранах.
