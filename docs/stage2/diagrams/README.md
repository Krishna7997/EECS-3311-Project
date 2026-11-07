# Stage 2 diagrams (as implemented)

Made in UMLet 15.1. Editable files are in `uxf/`, exported images in `png/`. Stage 1 diagrams that did not change stay in [`docs/stage1/diagrams`](../../stage1/diagrams).

| Diagram | What changed from Stage 1 |
|---|---|
| [Use cases](png/usecase.png) | UC16-UC19 added for the new AI features |
| [Class 1/8 Domain](png/class_domain.png) | ContributionKind (HBP/LLP, FHSA transfer), pension adjustment, RecurringCharge variable amount |
| [Class 2/8 Import](png/class_import.png) | format detection, ColumnMapping header/balance, F16 AiFormatReader |
| [Class 3/8 Planning](png/class_planning.png) | FIRE own target / target age / Coast FIRE, repayment plans, BudgetPlanner, ExtraMoneyPlanner |
| [Class 4/8 Events and commands](png/class_events.png) | new event types, proposals wait in PendingActionStore |
| [Class 5/8 Agent](png/class_agent.png) | runTask for multi-step tasks, several proposals per answer, new prompts and tools |
| [Class 6/8 UI, Facade, persistence](png/class_ui.png) | 13 GUI pages (AtlantaFX), Nav, Ui helpers, CLI command classes, SettingsRepository, migrations |
| [Class 7/8 Automation](png/class_automation.png) | import folder from Settings, monthly check-in, ConsoleNotifier replaces EmailNotifier |
| [Class 8/8 New AI features](png/class_ai_features.png) | F16-F19 (new) |
| [SD13 Limits bot](png/sd13_limits_bot.png) | pull request or GitHub issue instead of a commit and email |
| [SD15 AI monthly review](png/sd15_monthly_review.png) | new (F17) |
| [SD16 AI format reader](png/sd16_ai_format.png) | new (F16) |
| [SD17 Plain-English search](png/sd17_find_transactions.png) | new (F18) |
| [SD18 Extra money](png/sd18_extra_money.png) | new (F19) |
