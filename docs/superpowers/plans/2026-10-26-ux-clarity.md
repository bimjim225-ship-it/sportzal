# UX clarity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Закрыть замечания финальной UX-проверки в отдельном PR без слияния.

**Architecture:** Общий ScreenWithAction отделяет длинное содержимое от главного действия. Состояние первого запуска выводится из реального каталога; общие RIR и схема переиспользуются во всех затронутых сценариях.

**Tech Stack:** Kotlin 2.1.20, Compose Material3, Android API26+, Room3.

**Spec:** docs/superpowers/specs/2026-10-26-ux-clarity-design.md

## Global Constraints

- Android Kotlin + Compose; русский UI; touch targets ≥48dp.
- Никаких новых зависимостей, миграций БД, изменений схемы JSON, сетевого ИИ или генерации схем из фото.
- AppSafeArea остаётся единственным владельцем системных/IME inset.
- Новый PR от main; не мержить.

## Review Focus

- 320dp/200%: нижнее действие не перекрывает конец списка или заметку.
- Разные назначения: компактная карточка не подменяет различающиеся веса/паузы общим значением.
- Сбой записи заметки: отчёт не отправляется со старым комментарием, ошибка доступна у действия.
- После замены: быстрый просмотр схемы использует фактический тренажёр и движение.
- Первый запуск с готовым файлом: пошаговая помощь не блокирует немедленный импорт.

---

### Task 1: Регрессии замечаний

**Files:** Create `app/src/androidTest/java/ru/sportzal/app/ui/UxClarityTest.kt`.

**Interfaces:** существующие TodayScreen, PlannedWorkoutScreen, ProgramImportScreen, FinishScreen, EquipmentScreen, RirSelector и ExerciseMediaPanel; API не меняется для тестов воспроизведения.

- [x] Написать проверки: первый CTA ведёт к оборудованию/помощи; импорт доступен; старт/принятие/отчёт видны до и после прокрутки длинного содержимого; добавление доступно в длинном каталоге; справка не меняет RIR; легенда содержит детали; разные назначения раскрываются полностью.
- [x] Собрать app/test APK; выполнить тесты на исходном приложении в CI. Expected: реальные assertion failures новых UX-регрессий, без ошибок компиляции.
- [x] Зафиксировать RED evidence.

### Task 2: Доступные действия и компактные планы

**Files:** Create `ui/components/ScreenWithAction.kt`, `ui/today/FirstPlanCard.kt`, `ui/today/PlanPrescriptionSummary.kt`; modify TodayScreen, ProgramScreens, FinishScreen, EquipmentScreen, AiHelpScreen, SportzalApp.

**Interfaces:** ScreenWithAction(bottomAction: (@Composable () -> Unit)?, content: @Composable () -> Unit); FirstPlanCard принимает каталог и реальные callbacks; compactPrescriptionLines(ExerciseDocument): List<String>.

- [x] Добавить нижнюю панель через общий компонент, вынести добавление в шапку каталога.
- [x] Реализовать последовательный первый запуск и прямой переход из помощи в каталог.
- [x] Сгруппировать только одинаковые назначения; сохранить полные строки под раскрытием.
- [x] Проверить отсутствие перекрытия на 320dp/200%, конец списка, комментарий перед отправкой и отказ onNote. Expected: native UX-регрессии зелёные.
- [x] Commit.

### Task 3: Объяснения схем и оценки

**Files:** Modify RirSelector, ExerciseCard, SetResultRow, ExerciseMediaPanel, EquipmentScreen, WorkoutScreen; create `ui/media/EquipmentGuide.kt`.

**Interfaces:** RirSelector сохраняет сигнатуру и владеет заголовком/подсказкой; EquipmentGuide(VisualAsset?, Modifier) общий для каталога/тренировки; быстрый просмотр получает actualContext.

- [x] Добавить справку к шкале, убрать повторные заголовки в потребителях.
- [x] Добавить буквенные выноски/легенды к 5 типовым схемам и указатель перехода между кадрами.
- [x] Добавить «Схема» в шапку упражнения, без изменения быстрого ввода и оценки.
- [x] Проверить новую справку, все схемы, отсутствие выдуманных подписей без привязки, фактическое оборудование после замены. Expected: новые и прежние native тесты зелёные.
- [x] Commit.

### Task 4: Проверка и передача

**Files:** DESIGN.md, UX-CONTRACT.md, README.md, app/build.gradle.kts, VisualAcceptanceTest.kt, `docs/reviews/2026-10-26-ux-clarity.md`, `docs/reviews/screens/ux-clarity/`.

**Interfaces:** version 0.2.2/code4; прежний нативный сценарий дополнен новыми снимками; публичный JSON и Room без изменений.

- [x] Обновить дизайн-контракт и версию; расширить нативный сценарий проверкой справки/быстрой схемы.
- [x] Выполнить JVM, assemble, lint, validate_docs.py, validate_graphics.py, strict UI audit и полный native API30/API35. Expected: все проверки проходят, схемы Room неизменны.
- [x] Получить свежие нативные снимки и визуально проверить читаемость/геометрию.
- [x] Провести одно независимое финальное ревью; существенные замечания исправить с регрессиями.
- [x] Сохранить evidence и заключение, опубликовать в PR; оставить его открытым без слияния.
