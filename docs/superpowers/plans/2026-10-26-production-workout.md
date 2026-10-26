# Production Workout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Реализовать согласованный основной сценарий Sportzal и подключить графику как нативные ресурсы.

**Architecture:** Текущие Room, repository, StrictJson и доменные вычисления сохраняются. Отдельные экраны обзора/упражнения используют общие компоненты; медиасопоставления локальны и не меняют JSON v1.

**Tech Stack:** Kotlin 2.1.20, Compose Material3, Room 2.7.1, Android 26+.

**Spec:** `docs/superpowers/specs/2026-10-26-production-workout-design.md`.

## Global Constraints

Внешние JSON v1 без изменений. Room только с миграцией. Светлая палитра DESIGN.md, русский UI, 48 dp. План и факт отдельно. Никаких фиктивных AI-вызовов. Генерация по фото — дальнейший этап.

## Review Focus

1. Старый draft с null RIR не превращается в явное «Не оценил»; новый явный ответ восстанавливается.
2. Дополнительные/разминочные подходы и пропуски не завышают выполненный план.
3. Back и восстановление не создают новую попытку и не теряют текущий ввод.
4. Смена оборудования не показывает технику от прошлого упражнения и не переносит несовместимый вес.
5. Импорт при активной тренировке меняет только будущий план; ошибка сохраняет текущие данные.

### Task 1: Надёжное восстановление draft

**Files:** `data/db/Entities.kt`, `SportzalDatabase.kt`, `ui/workout/WorkoutViewModel.kt`, unit/Room migration tests, `app/schemas`.

**Interfaces:** DraftEntity.rirAnswered:Boolean (default from existing numeric RIR); MIGRATION_1_2. WorkoutViewModel.updateDraft/open/updateActualContext preserve explicit null answer.

- [x] Экспортировать schema v1 исходной сборкой; запустить baseline suite.
- [x] Добавить регрессию explicit null → repository → новый VM, false и 0..4; увидеть падение.
- [x] Добавить колонку default 0, SQL backfill для числового RIR, оба writer/read, обработку ошибок draft.
- [x] Экспортировать schema v2, проверить migration и unit suite; commit.

### Task 2: Графика и каталог

**Files:** `ui/media/*`, `data/db/MediaEntities.kt`, DAO/repository, `ui/equipment/*`, `app/src/main/res`, schemas/migration tests.

**Interfaces:** explicit equipment illustration key and exercise guide key, nullable fallback; SaveEquipmentCommand carries editable weight metadata. Media assets remain bundled and offline.

- [x] Тесты неизвестного ключа/fallback, ручного выбора, сохранения веса/настроек.
- [x] Добавить локальные media tables с migration 2→3 и 25 WebP/24 vectors из подготовленного пакета.
- [x] Реализовать поиск, просмотр, отдельный editor с выбором схемы, шагом и доступными весами.
- [x] Проверить unit/Room/ресурсы, commit.

### Task 3: Обзор и выполнение

**Files:** `ui/workout/*`, `ui/components/ExerciseCard.kt`, `SetResultRow.kt`, `ui/navigation/*`, MainActivity.

**Interfaces:** focused exercise ID, overview/back callbacks, WorkoutProgress pure computation, state restore via rememberSaveable and Room draft.

- [x] Тест прогресса с warmup/extra/skips и UI тест обзор→упражнение→Back; увидеть падение.
- [x] Реализовать обзор активной тренировки, компактные строки статуса, выбранное упражнение и панель Настройка/Техника.
- [x] Уточнить target/rest labels, сброс ответов между слотами, убрать null из дополнительных строк; явная отмена пустой попытки.
- [x] Проверить unit и Compose регрессии видимых значений/rotation/corrections; commit.

### Task 4: Сегодня и новый план

**Files:** `ui/today/*`, `ui/importing/*`, MainActivity/shared export coordinator.

**Interfaces:** TodayUiState planned detail/equipment/loading/busy; dismissPreview, showOverview before start, explicit resume; program preview consumes existing ImportPreview.Valid.

- [x] Тест просмотра без старта, отмены preview и ошибки confirm; увидеть падение.
- [x] Реализовать содержательный Today, обзор ещё не начатого плана, полноэкранный preview состава и инструкции ИИ, loading/error/retry.
- [x] Объединить отправку/сохранение отчёта, сохранить focus и активный snapshot; commit после проверок.

### Task 5: Нативное оформление, итог и история

**Files:** `ui/theme/*`, `ui/components/*`, `ui/workout/FinishScreen.kt`, `ui/history/*`, DESIGN.md/UX-CONTRACT.md/PRODUCT.md.

**Interfaces:** one Compose theme mapping, shared headers/buttons/statuses, finish counters from committed facts; two-stage scheme/technique panel without baked text.

- [x] Тесты summary warmup/extra/skip и no-ID/locale labels.
- [x] Применить типографику/формы/иконки/фон, отделить основные/вторичные действия, оформить итог и историю.
- [x] Обновить канонические владельцы и описание схем вместо фото; unit/Compose/build/lint/docs; commit.

### Task 6: Проверка и поставка

**Files:** `docs/reviews/2026-10-26-production-workout.md`, CI diagnostics/acceptance docs, README.

- [x] Статический premium audit и поиск дрейфа, ресурсный контроль; unit/build/lint/instrumentation на доступном runtime.
- [x] Независимый review всей ветки; исправить существенные замечания с регрессиями.
- [x] Сохранить реальные результаты и ограничения; открыть draft PR с APK artifact, main не менять.

Полный переносимый backup/restore, установка внешних медиапакетов и генерация по фото сохраняются отдельными этапами исходного roadmap после основного рабочего цикла; JSON snapshot не называется backup.
