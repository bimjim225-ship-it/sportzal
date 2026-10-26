# Userflow iteration implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox syntax for tracking.

**Goal:** Закрыть все замечания аудита PR42 и показать проверенные нативные экраны.
**Architecture:** Сохраняем слои Room/repository/VM/Compose. Редкие атомарные действия добавляются в repository; публичные JSON остаются v1. Сфокусированный ввод получает собственную компактную компоновку, общий UI сохраняет прежние канонические владельцы.
**Tech Stack:** Kotlin, Compose, Room3, JDK17/SDK35.
**Spec:** docs/superpowers/specs/2026-10-26-userflow-iteration-design.md

## Global Constraints
- Та же ветка codex/production-workout-flow, PR42, без merge.
- JSON v1, minSdk26/target35, офлайн; без новых библиотек и API ИИ.
- Исторические snapshots, completed_at/sequence_no сохраняются. Нет destructive migration.
- Проверки RED→GREEN, затем общий suite; редкие ошибки не прячутся за пределами экрана.

## Review Focus
- Неизвестный результат commit + редактирование перед retry: один правильный факт без дубля (Task1).
- Существующий каталог + equipment_upserts=[]: корректный следующий план (Task2).
- Не входящий в план/новый тренажёр и новое движение: постоянная идентичность и полный экспорт (Task3).
- Много подходов,320dp/200% и клавиатура: текущий ввод и ошибка доступны (Task4).
- Старый план во время активного занятия + save/export из истории: snapshot не меняется (Tasks2/5).

### Task 1: Надёжные записи и восстановление
**Files:** WorkoutViewModel.kt, WorkoutViewModelTest.kt, постоянный UserflowAuditRegressionTest.kt.
**Interfaces:** VM reloadCommittedState():Boolean, WorkoutUiState.reloadRequired:Boolean; сохранение текущих публичных saveSet/updateDraft.
- [ ] Перенести три audit probes; выполнить и увидеть3 ожидаемых падения.
- [ ] Исправить pending/reconciliation и защищённое чтение после всех mutation; покрыть identical/edited retry и commit-then-read-failure. Сначала отсутствие unhandled exception и saving=false, затем explicit reload.
- [ ] Unit suite GREEN; commit.

### Task 2: Каталог при импорте и явная активация
**Files:** ProgramImporter, ProgramScreens, TodayViewModel, FileIntentHandler, SportzalRepository, DomainModels; unit/Room tests.
**Interfaces:** suspend preview(source), ImportPreview.Valid.status/equipment; repository.programImportStatus(id,version,hash), activateProgram(id,version,hash).
- [ ] Регрессии локального ID без upsert, неизвестного ID, сохранённой версии v1/v2 и активного snapshot — RED.
- [ ] Асинхронный предпросмотр с каталогом и явное действие активации; no-op не активирует старую версию, конфликт не активируется.
- [ ] Unit suite/Room tests; commit.

### Task 3: Замены и пропуск остатка
**Files:** ActualContext/DomainModels, repository, VM, ExerciseCard/SetResultRow dialogs, HistoryViewModel; unit/Room/Compose tests.
**Interfaces:** ActualContext.title:String?=null; EditSetCommand optional actual ID/title; repository.skipRemainingSets(workoutId,exerciseId,recordedAt,reason,note); VM.updateActualContext/save contexts consume full catalog separately from immutable snapshot.
- [ ] Тесты каталога B, local equipment в snapshot, замены ID/title и атомарного skip-rest с сохранением фактов — RED.
- [ ] Общие редакторы фактического движения/оборудования, защита prefill, запись локального каталога в транзакции; skip-rest с подтверждением.
- [ ] Unit suite/Room/Compose GREEN; commit.

### Task 4: Быстрый экран и честный таймер
**Files:** WorkoutScreen/ExerciseCard/MediaPanel/Overview, shared controls, timer formatter, DESIGN/UX; Compose tests.
**Interfaces:** focused-input variant; onReload/onSkipRemaining/onActualExercise callbacks; inline error display.
- [ ] Тест видимой записи на360×800, доступности320×640/200%, ошибки у формы, свёрнутых старых строк и ≈ timer — RED.
- [ ] Компактная форма перед иллюстрациями, сведения отдельным раскрытием; общие validation/recovery controls, формат времени.
- [ ] Unit/Compose/lint и визуальный просмотр; commit.

### Task 5: Первый круг ИИ и единый экспорт
**Files:** AI help/prompt generator/schema assets, Today/Finish/History/FileExchangeActions; tests, README/contracts.
**Interfaces:** offline prompt(schema:String):String; общие share/save/preparing callbacks; UI Help on open/close без потери активного маршрута.
- [ ] Тест самодостаточного запроса со схемой, доступных действий, истории focus/сохранения — RED.
- [ ] Помощь/копирование запроса и единые действия; сохранить JSONv1.
- [ ] Unit/Compose/docs GREEN; commit.

### Task 6: Приёмка и скриншоты
**Files:** VisualAcceptanceTest, CI diagnostics script, docs/reviews/screens/iteration2, отчёт.
- [ ] Запустить весь suite, собрать APK/test APK, lint/docs/ресурсы.
- [ ] Один независимый whole-branch review; существенные замечания исправить с регрессиями.
- [ ] CI API30/API35 на итоговой ветке; получить скриншоты из нативного приложения, проверить каждый; обновить PR42.
