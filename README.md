# Sportzal

Персональный Android-логгер: ИИ составляет программу → приложение быстро записывает факт → один JSON отправляется через системное «Поделиться» в Telegram/почту → ИИ анализирует на компьютере.

**Статус:** спецификация MVP заморожена и подготовлена к разработке; Android-приложение ещё не реализовано. Kotlin + Compose + Room, один пользователь, офлайн. Firebase, веб-клиент и встроенный ИИ не нужны.

## С чего начинать разработку

1. Прочитать [Product contract](PRODUCT.md) и [UX contract](UX-CONTRACT.md).
2. Прочитать [ADR-0001: Development Readiness Freeze](docs/decisions/0001-development-readiness-freeze.md) — он закрывает последние неоднозначности и имеет приоритет для перечисленных в нём решений.
3. Использовать [детальный implementation plan](docs/superpowers/plans/2026-09-06-sportzal-mvp-implementation-plan.md) как порядок реализации и тестов.

| Документ | Что определяет |
|---|---|
| [PRODUCT.md](PRODUCT.md) | Назначение и границы MVP |
| [UX-CONTRACT.md](UX-CONTRACT.md) | Быстрый ввод, блоки, ошибки и восстановление |
| [DESIGN.md](DESIGN.md) | Визуальные токены, читабельность и касания |
| [Контракты данных](docs/data-contracts.md) | План/факт, идентичность, версии и экспорт |
| [ADR-0001](docs/decisions/0001-development-readiness-freeze.md) | Финальная семантика отдыха, отмена пустого запуска, стабильный exercise_id и точная Room-модель |
| [Архитектура](docs/superpowers/specs/2026-09-06-sportzal-mvp-design.md) | Границы Kotlin/Compose/Room и будущие проверки |
| [Implementation plan](docs/superpowers/plans/2026-09-06-sportzal-mvp-implementation-plan.md) | Пошаговая TDD-реализация с файлами, интерфейсами и командами проверки |
| [Результаты ревью](docs/reviews/2026-09-06-concept-review.md) | Тренерская и пользовательская оценка, исправления и ограничения |

Формальные схемы: [program](docs/schemas/program.schema.json), [snapshot](docs/schemas/ai-snapshot.schema.json).  
Полные синтетические примеры: [программа](docs/examples/program.json), [результат](docs/examples/ai-snapshot.json).

Документы дополняют друг друга: PRODUCT определяет продукт, UX — поведение, DESIGN — внешний вид, data-contracts и Schema — обмен, ADR-0001 замораживает финальные решения перед кодом. Архитектура и implementation plan реализуют этот набор требований.

Не принимать примерные веса за персональное назначение. Точные интервалы физического отдыха не измеряются; snapshot не является полным восстанавливаемым backup.
