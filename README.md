# MapleCFO 🍁

**An AI "CFO" for Canadian students and young professionals.** Import your bank CSVs and get clear answers about budgets, savings, TFSA/RRSP/FHSA room and debt. The AI never invents a number.

EECS3311 Software Design (Fall 2026) course project by Krishna Patel.

## What it does
- 📥 Imports bank CSVs and categorizes transactions, using rules first and AI for the rest
- 📊 Tracks net worth, budgets, subscriptions and savings goals
- 🇨🇦 Calculates TFSA / RRSP / FHSA room and warns before you over-contribute
- 🧮 Plans debt payoff (avalanche or snowball) and projects when you could retire early (FIRE)
- 🤖 **AI CFO chat** answers questions like *"Can I afford a $2,000 trip in December?"* by calling finance tools and explaining the results

## How the AI stays honest
- All numbers come from tested Java code. The AI only plans and explains.
- Answers are checked: every dollar amount must match what the tools returned.
- The AI can only *propose* changes. You confirm them, and you can undo them.
- It gives financial education only, never investment picks.

## Architecture
![Architecture](docs/stage1/diagrams/png/architecture.png)

## Diagrams
| Diagram | Link |
|---|---|
| Use-case diagram | [usecase.png](docs/stage1/diagrams/png/usecase.png) |
| Class diagram: 1 Domain · 2 Import · 3 Services · 4 Events/Commands · 5 AI agent · 6 UI/Facade | [1](docs/stage1/diagrams/png/class_domain.png) · [2](docs/stage1/diagrams/png/class_import.png) · [3](docs/stage1/diagrams/png/class_planning.png) · [4](docs/stage1/diagrams/png/class_events.png) · [5](docs/stage1/diagrams/png/class_agent.png) · [6](docs/stage1/diagrams/png/class_ui.png) |
| Sequence: import · undo · net worth · TFSA room · FIRE/debt | [SD01](docs/stage1/diagrams/png/sd01_import.png) · [SD02](docs/stage1/diagrams/png/sd02_edit_undo.png) · [SD03](docs/stage1/diagrams/png/sd03_networth.png) · [SD04](docs/stage1/diagrams/png/sd04_room.png) · [SD05](docs/stage1/diagrams/png/sd05_planning.png) |
| Sequence: subscriptions · goals · **AI CFO agent** · afford check · monthly summary | [SD06](docs/stage1/diagrams/png/sd06_insights.png) · [SD07](docs/stage1/diagrams/png/sd07_goal.png) · [SD08](docs/stage1/diagrams/png/sd08_agent.png) · [SD09](docs/stage1/diagrams/png/sd09_afford.png) · [SD10](docs/stage1/diagrams/png/sd10_report.png) |

All diagrams are made in **[UMLet](https://www.umlet.com)**. Editable UMLet files are in [`docs/stage1/diagrams/uxf/`](docs/stage1/diagrams/uxf/) and the exported images in [`docs/stage1/diagrams/png/`](docs/stage1/diagrams/png/).

## Tech stack
Java 21 · JavaFX (GUI) · picocli (CLI) · SQLite · Anthropic Claude API · JUnit 5 · KUMA

## Design at a glance
- **12 features** (8 deterministic, 1 AI, 3 hybrid)
- **8 design patterns:** Facade, Adapter, Template Method, Factory Method, Strategy, Observer, Command, Composite

📄 Full design (UML, use cases, sequence diagrams, traceability): [Stage 1 Design Report](docs/stage1/Stage1_Design_Report.md) · [PDF](docs/stage1/Stage1_Design_Report.pdf)

## Project stages
| Stage | Status |
|---|---|
| 1: Design | ✅ Done |
| 2: Build with AI | ⏳ Next |
| 3: Testing (JUnit + KUMA) | ⏳ |

> *Educational project: MapleCFO provides financial information, not professional financial advice.*
