# MapleCFO — AI Collaboration Log (Stage 2)

EECS 3311 · Krishna Patel · Repository: https://github.com/Krishna7997/EECS-3311-Project

This log records how the Stage 2 code was produced with an AI coding tool. Following the course rule, all Java code was written by the AI; I (Krishna) gave the instructions, reviewed every change, made the design decisions, ran the tests and the app on my own Mac, reported bugs, and made every commit.

Lines marked ✎ were drafted from the session record and confirmed by me before submission.

## 1. AI tools used

| Tool | Model | Used for | Tokens / cost |
|---|---|---|---|
| Claude | claude-opus-5-5 (configured) | Writing all Java code and JUnit tests, design-to-code mapping, GUI, README, bug fixing, Stage 2 planning | See report Section 5 |
| Anthropic Claude API (inside MapleCFO) | claude-haiku-5-5 | Runtime LLM for the F10 agent, F02 AI categorizer and F12 summaries (my own key, kept in .env) | See report Section 5 |

## 2. How we worked

1. I described the task or the problem (often after testing the app on my Mac).
2. The AI wrote the code and JUnit tests from the Stage 1 design, ran `mvn test` in its own workspace, and checked GUI screens with screenshots.
3. Each change came as one patch per commit. I applied it, ran `mvn test` and the app myself, and committed and pushed only when it worked.
4. Bugs I found while testing went back to the AI as the next task (see section 4).

**Security rules we kept:** the API key lives only in a local, git-ignored `.env`; it never appears in code, chat, screenshots, videos or commits. Only fake sample CSV files are in the repository; real bank exports and the database are git-ignored.

## 3. Log entries

### 1. Project skeleton and Money value object

- **Date:** 2026-10-22
- **Task:** Set up the Maven project and the Money value object from Stage 1 decision D11.
- **Input/Instruction:** Implement the Stage 1 design feature by feature, starting with the project skeleton and domain model.
- **AI Contribution:** pom.xml (Java 21, JUnit 5), .gitignore rules for .env, real bank CSVs and the database, .env.example, Money (BigDecimal, rounded to cents, immutable) and MoneyTest (8 tests).
- **Human Contribution:** ✎ Reviewed the pom.xml and .gitignore rules, ran mvn test on my Mac (8 tests passed), committed and pushed.
- **Outcome:** ✎ Committed Oct 23 (0ca6048). Tests pass.
- **Commit:** Set up Maven project and Money value object with tests

### 2. Domain model

- **Date:** 2026-10-22
- **Task:** Implement the domain classes from class diagram part 1.
- **Input/Instruction:** Implement the Stage 1 domain model with the validation rules from the feature error cases.
- **AI Contribution:** Account, AccountGroup and AssetComponent (Composite pattern), Transaction with a SHA-256 fingerprint for duplicate detection, Budget, BudgetStatus, SavingsGoal, Debt, RecurringCharge, ContributionRecord, UserProfile and enums. 16 tests.
- **Decisions made while coding:** Liability balances (credit card, loan) are stored as positive amounts owed and getValue() returns them as negative values, so a group total is always a plain sum.
- **Human Contribution:** ✎ Reviewed the classes against class diagram part 1 and the sign rule for liabilities; ran mvn test, committed and pushed.
- **Outcome:** ✎ Committed Oct 23 (a2ff9b2). Tests pass.
- **Commit:** Add domain model: accounts (Composite), transactions with fingerprints, budgets, goals, debts

### 3. F07 FIRE calculator

- **Date:** 2026-10-22
- **Task:** Implement FireCalculator, FireInputs and FireProjection (SD05).
- **Input/Instruction:** Implement F07 as specified in the Stage 1 report.
- **AI Contribution:** Target = spending / withdrawal rate, yearly simulation capped at 70 years, validation ranges from the Stage 1 spec, three savings scenarios. 7 tests.
- **Decisions made while coding:** The spec says "±5% savings-rate scenarios". Implemented as moving 5% of income between saving and spending, which also lowers or raises the target. Confirm this matches what you intended.
- **Human Contribution:** ✎ Checked the ±5% scenario interpretation the AI asked about and accepted it; ran mvn test, committed and pushed.
- **Outcome:** ✎ Committed Oct 23 (5051cb3). Tests pass.
- **Commit:** F07: FIRE calculator with validation and savings scenarios

### 4. F08 Debt payoff planner

- **Date:** 2026-10-22
- **Task:** Implement DebtPayoffPlanner with the Strategy pattern and the Repository / DebtRepository interfaces.
- **Input/Instruction:** Implement F08 as specified in the Stage 1 report.
- **AI Contribution:** Month-by-month simulation (interest, minimums, extra to the first debt in the strategy's order, freed payments rolled forward), warnings when a minimum payment does not cover interest, a Clock for repeatable tests. 9 tests, including "avalanche never costs more interest than snowball".
- **Human Contribution:** ✎ Reviewed the avalanche/snowball results; ran mvn test, committed and pushed.
- **Outcome:** ✎ Committed Oct 23 (a369c1b). Tests pass.
- **Commit:** F08: debt payoff planner with avalanche and snowball strategies

### 5. F05 Recurring charge detector

- **Date:** 2026-10-22
- **Task:** Implement RecurringChargeDetector (SD06).
- **Input/Instruction:** Implement F05 as specified in the Stage 1 report.
- **AI Contribution:** Merchant-name normalisation, weekly/monthly/yearly interval detection within ±3 days, price-increase flag (more than 5% above earlier charges), variable-amount label, low-confidence check for less than 3 months of data. 8 tests.
- **Decisions made while coding:** The price-increase check compares the latest charge with the average of the earlier charges, because including the latest charge in the average would hide part of the increase.
- **Human Contribution:** ✎ Reviewed the detector rules; ran mvn test (48 tests for week 1), committed and pushed.
- **Outcome:** ✎ Committed Oct 23 (db01f84). Tests pass.
- **Commit:** F05: recurring charge and subscription detector

### 6. F06 TFSA / RRSP / FHSA room

- **Date:** 2026-10-23
- **Task:** Implement RegisteredAccountService, ContributionLimits (loaded from JSON) and a first LimitsService with the bundled limits file (SD04).
- **Input/Instruction:** Start week 2 with F06.
- **AI Contribution:** TFSA, FHSA and RRSP room rules, OK / OVER / UNKNOWN checks, contribution_limits.json with CRA limits up to 2026 (checked against a 2026 tax-numbers article: TFSA $7,000, total $109,000 since 2009; RRSP $33,810), LimitsService with ordered fallback between providers. 16 tests.
- **Decisions made while coding:** LimitsService was built early (it belongs to F15) because F06 needs the limits; the remote provider, validator and cache from the F15 design come later. TFSA withdrawals restore room on January 1 of the next year; FHSA carry-forward is capped at $8,000.
- **Human Contribution:** ✎ Checked the 2026 limits in contribution_limits.json against CRA numbers; ran mvn test (64 tests), committed on Oct 28 so it counts for week 2.
- **Outcome:** ✎ Committed Oct 28 (7c29fe5). Tests pass.
- **Commit:** F06: contribution room tracker with CRA limits from JSON (commit on or after Oct 27)

### 7. Observer: EventBus, F04 budgets and alerts

- **Date:** 2026-10-23
- **Task:** EventBus, FinanceEvent, AlertService and BudgetService (Observer pattern).
- **Input/Instruction:** Prepare next week's work.
- **AI Contribution:** EventBus that keeps going if one listener fails, BudgetService that recomputes budgets on import or recategorize and alerts once at 80% and 100%, AlertService that turns events into messages. F06 now publishes CONTRIBUTION_WARNING.
- **Decisions made while coding:** Added a BUDGET_WARNING event type for the 80% alert from the Stage 1 spec (the design only listed BUDGET_EXCEEDED).
- **Human Contribution:** ✎ Reviewed the extra BUDGET_WARNING event; ran mvn test, committed and pushed.
- **Outcome:** ✎ Committed Oct 28 (1904991). Tests pass.
- **Commit:** Week 2, commits 1–2

### 8. Command: undo and redo

- **Date:** 2026-10-23
- **Task:** UndoableCommand, CommandHistory, SetBudgetCommand.
- **Input/Instruction:** Prepare next week's work.
- **AI Contribution:** Undo/redo stacks capped at 100; a failed undo leaves both stacks unchanged (Stage 1 F02 error case). 5 tests.
- **Human Contribution:** ✎ Ran mvn test, committed and pushed.
- **Outcome:** ✎ Committed Oct 28 (4e43342). Tests pass.
- **Commit:** Week 2, commit 3

### 9. F02 rule-based categorization

- **Date:** 2026-10-23
- **Task:** CategorizationStrategy chain, RuleBasedCategorizer with Canadian merchant rules, learning from corrections.
- **Input/Instruction:** Prepare next week's work.
- **AI Contribution:** About 120 built-in keyword rules, whole-word matching, learned rules first and longer keywords win ("uber eats" beats "uber"). A failing strategy (e.g. LLM down) leaves the transaction for review. 6 tests.
- **Decisions made while coding:** The LLM categorizer is plugged in later as the second strategy in the chain.
- **Human Contribution:** ✎ Reviewed the Canadian merchant keyword list; ran mvn test, committed and pushed.
- **Outcome:** ✎ Committed Oct 29 (395b727). Tests pass.
- **Commit:** Week 2, commit 4

### 10. F01 CSV import (Adapter)

- **Date:** 2026-10-23
- **Task:** CsvTransactionAdapter, ColumnMapping profiles, TransactionService, RecategorizeCommand, fake sample data.
- **Input/Instruction:** Prepare next week's work.
- **AI Contribution:** One generic CSV adapter driven by mapping profiles (TD, RBC and generic presets), bad rows reported with reasons, duplicate detection by fingerprint count, three fake sample CSV files. 15 tests.
- **Decisions made while coding:** ColumnMapping gained hasHeader (TD exports have no header row) and balanceColumn (needed later by F13 auto-sync). Duplicates are compared by count so two identical coffees on one day are both kept.
- **Human Contribution:** ✎ Reviewed the TD/RBC mapping profiles; confirmed only fake sample CSVs are in the repo (no real bank data); ran mvn test, committed and pushed.
- **Outcome:** ✎ Committed Nov 1 (7643cbb). Tests pass.
- **Commit:** Week 2, commit 5

### 11. SQLite repositories

- **Date:** 2026-10-23
- **Task:** DatabaseManager and SQLite versions of every repository.
- **Input/Instruction:** Prepare next week's work.
- **AI Contribution:** Tables created on first start in ~/.maplecfo/maplecfo.db, money stored as exact decimal text, learned category rules also saved. 6 tests.
- **Human Contribution:** ✎ Ran mvn test, committed and pushed.
- **Outcome:** ✎ Committed Nov 1 (0c503e2). Tests pass.
- **Commit:** Week 2, commit 6

### 12. Facade and first CLI

- **Date:** 2026-10-23
- **Task:** MapleCfoFacade, AppContext and the picocli CLI.
- **Input/Instruction:** Prepare next week's work.
- **AI Contribution:** Commands: import, profiles, tx list/search/recategorize, undo, redo, budget set/status, room, room check/log, profile, fire, debt add/list/plan, recurring, alerts. Interactive mode keeps undo history. Runnable jar via mvn package. 6 end-to-end CLI tests.
- **Decisions made while coding:** Undo in the CLI works within one interactive session (the history is kept in memory).
- **Human Contribution:** ✎ Built the jar with mvn package and tried the CLI commands; all 113 tests passed; committed and pushed.
- **Outcome:** ✎ Committed Nov 1 (0d2ae51). Tests pass.
- **Commit:** Week 2, commit 7

### 13. API key protection check

- **Date:** 2026-10-28
- **Task:** Set up the Claude API key without exposing it.
- **Input/Instruction:** "Can you check my file if I did it right"
- **AI Contribution:** Checked the local files without printing the key: found the key had gone into .env.example (a committed file) instead of .env, confirmed it was never committed or pushed, restored the placeholder, and verified the new .env is git-ignored.
- **Decisions made while coding:** Treat any key shown in chat as exposed: delete it and create a new one (Stage 2 handout, API key protection).
- **Human Contribution:** ✎ Created a new key, put it only in the git-ignored .env, and deleted the exposed key in the Anthropic Console.
- **Outcome:** ✎ No commit. Exposed key deleted; .env is git-ignored and .env.example only has a placeholder.
- **Commit:** No commit (local configuration only)

### 14. Testing the CLI on the sample data

- **Date:** 2026-11-01
- **Task:** Run the built app (mvn package, java -jar target/maplecfo.jar) and check the results.
- **Input/Instruction:** Krishna ran --help, import, recurring and fire on his Mac and shared the output.
- **AI Contribution:** Reviewed the output and found two F05 defects: a next-expected date in the past (TFSA contribution on 2026-10-25 when today is Nov 1) and a 'price went up' flag on a variable utility bill.
- **Decisions made while coding:** Next date will be rolled forward past today; the price-increase flag will only apply to fixed-amount charges.
- **Human Contribution:** ✎ Ran --help, import, recurring and fire on my Mac with the sample data and shared the output; agreed to the two fixes.
- **Outcome:** ✎ Two F05 bugs logged and fixed in W3C1 (e832174).
- **Commit:** Fixes go into week 3

### 15. F05 fixes + LLM layer

- **Date:** 2026-11-01
- **Task:** Fix recurring next-date and price-increase issues; add the Claude connection.
- **Input/Instruction:** Build week 3 in the agreed order, starting with F05 fixes and the Claude connection.
- **AI Contribution:** RecurringChargeDetector rolls next dates forward and ignores variable bills for price increases. AppConfig (.env loading), ClaudeClientAdapter (Messages API with tools, retry, friendly errors), MockLlmClient demo mode, LlmClientFactory.
- **Decisions made while coding:** Without an API key the app runs in demo mode so every feature can be shown offline.
- **Human Contribution:** Reviewed the patch, ran mvn test (passed), committed and pushed. Checked the real API key is read from .env: no demo-mode note.
- **Outcome:** Committed Nov 3 (e832174). Claude connection works with my key.
- **Commit:** W3C1: F05 fixes + LLM layer (Claude adapter, mock, factory, config)

### 16. F10 AI CFO agent

- **Date:** 2026-11-01
- **Task:** Implement the bounded tool-calling agent from SD08.
- **Input/Instruction:** Implement the agent with grounding checks and confirm-before-change.
- **AI Contribution:** CfoAgent (max 6 steps), ToolRegistry, AbstractFinanceTool (Template Method), 7 tools, ResponseValidator (every $ amount must come from tool results), PendingActionStore, CLI ask/confirm/dismiss/ai.
- **Decisions made while coding:** Rent paid by e-transfer was categorised TRANSFER; TRANSFER rules now run last.
- **Human Contribution:** Ran mvn test (passed), committed. Tested: ask "How much did I spend on dining last month?" gave $162.04 for October, matching my data.
- **Outcome:** Committed Nov 3 (439f701). Answer grounded in tool results.
- **Commit:** W3C2: F10 AI CFO agent

### 17. F02 AI categorizer + F12 report

- **Date:** 2026-11-01
- **Task:** Add the AI fallback categorizer and the monthly summary.
- **Input/Instruction:** Continue with F02 AI and F12.
- **AI Contribution:** LlmCategorizer (only valid category names, cached per merchant), ReportService, MonthlyReport, ReportSummarizer (AI narrative checked against the figures, template otherwise), CLI report.
- **Human Contribution:** Ran mvn test (passed), committed. Tested report -m 2026-09: AI summary written by claude-haiku-5-5. Found that rent imported last week was still categorized TRANSFER, so spending was under-counted ($921.61 instead of $2,371.61).
- **Outcome:** Committed Nov 3 (e494302). Fixed by re-importing on a fresh database; new imports use the corrected rent rule.
- **Commit:** W3C3: F02 AI categorizer + F12 monthly report

### 18. F03 net worth + F09 goals

- **Date:** 2026-11-01
- **Task:** Accounts, net worth history and savings goal forecasts.
- **Input/Instruction:** Continue with F03 and F09.
- **AI Contribution:** AccountService (Composite tree), NetWorthService (daily snapshots), CashFlowForecaster, GoalService/GoalForecast, CreateGoalCommand, net_worth and goal_forecast tools, SQLite tables, CLI account/networth/goal.
- **Decisions made while coding:** A goal's available money is the average surplus minus what the other goals need each month.
- **Human Contribution:** Ran mvn test (passed), committed. Tested account add, networth, goal add and goal list on my data. Duplicate import correctly skipped 136 rows.
- **Outcome:** Committed Nov 3 (c4d5af8).
- **Commit:** W3C4: F03 net worth + F09 savings goals

### 19. F11 affordability

- **Date:** 2026-11-01
- **Task:** Can I afford X by date Y?
- **Input/Instruction:** Continue with F11.
- **AI Contribution:** AffordabilityAnalyzer and AffordabilityResult (YES / TIGHT / NO / INSUFFICIENT_DATA, headroom, shortfall, goals affected), affordability tool, CLI afford, demo date parsing.
- **Decisions made while coding:** Safety buffer = one month of recurring bills; savings transfers such as a monthly TFSA deposit are not counted as bills.
- **Human Contribution:** ✎ Ran mvn test, committed and pushed; tried afford with an example purchase.
- **Outcome:** ✎ Committed Nov 6 (b925d3a). Tests pass.
- **Commit:** W3C5: F11 affordability check

### 20. JavaFX GUI part 1

- **Date:** 2026-11-01
- **Task:** Main window and first four screens.
- **Input/Instruction:** Build the GUI on the same facade as the CLI.
- **AI Contribution:** MapleCfoApp (tabs, Undo/Redo bar, EventBus refresh), Dashboard, Transactions (import, recategorise), Budgets (progress bars), Accounts & Goals (net worth, goals, affordability). Checked with headless screenshots.
- **Decisions made while coding:** Start the GUI from the single jar: java -jar maplecfo.jar gui.
- **Human Contribution:** ✎ Ran the GUI on my Mac (java -jar target/maplecfo.jar gui), committed and pushed.
- **Outcome:** ✎ Committed Nov 6 (06d505b). Tests pass.
- **Commit:** W3C6: JavaFX GUI shell + dashboard, transactions, budgets, accounts & goals

### 21. JavaFX GUI part 2

- **Date:** 2026-11-01
- **Task:** Remaining screens.
- **Input/Instruction:** Finish the GUI.
- **AI Contribution:** Planning (subscriptions, TFSA/RRSP/FHSA, debt payoff, FIRE chart), Ask MapleCFO chat (background thread, Confirm/Dismiss), Monthly Report (pie chart, narrative).
- **Human Contribution:** ✎ Tried the chat, planning and report screens on my Mac; committed and pushed.
- **Outcome:** ✎ Committed Nov 6 (fd9a000). Tests pass.
- **Commit:** W3C7: GUI planning, AI chat and monthly report screens

### 22. F13 + F14 automation

- **Date:** 2026-11-01
- **Task:** Auto-import folder, balance sync, reminders, contribution detection, RRSP estimate.
- **Input/Instruction:** Continue with F13 and F14.
- **AI Contribution:** ImportFolderWatcher (WatchService, waits for file to stop growing, picks the bank format), BalanceSyncService, ReminderService, ContributionDetector + RecordContributionCommand (needs confirmation), RrspRoomEstimator, CLI watch/reminders/rrsp-estimate, GUI 'Waiting for your OK' card.
- **Decisions made while coding:** RRSP estimate scales a partial year of pay to 12 months and says it is low because pay is after tax.
- **Human Contribution:** ✎ Ran mvn test, committed and pushed; tried watch, reminders and rrsp-estimate in the CLI.
- **Outcome:** ✎ Committed Nov 6 (05e7ee7). Tests pass.
- **Commit:** W3C8: F13 auto-import, balance sync, reminders + F14 contribution detection, RRSP estimate

### 23. F15 limits refresh + bot

- **Date:** 2026-11-01
- **Task:** Download, validate and cache new limits; weekly bot.
- **Input/Instruction:** Finish with F15.
- **AI Contribution:** RemoteLimitsProvider, LimitsValidator, LimitsCache, LimitsService.refreshIfStale (UP_TO_DATE / UPDATED / USING_CACHED), CraLimitsScraper, LimitsUpdateJob, GitHubIssueNotifier, GitHub Actions workflow, CLI limits.
- **Decisions made while coding:** Bot opens a pull request (or an issue on problems) instead of sending email.
- **Human Contribution:** ✎ Reviewed the switch from email to GitHub issues for the bot; ran mvn test, committed and pushed.
- **Outcome:** ✎ Committed Nov 6 (fc1ab76). Tests pass.
- **Commit:** W3C9: F15 limits refresh (remote + validator + cache) and weekly CRA bot

### 24. README update

- **Date:** 2026-11-01
- **Task:** Document GUI, CLI, demo mode and automation.
- **Input/Instruction:** Update README.
- **AI Contribution:** Build/run section with GUI launch, a full CLI example for all 15 features (all commands run successfully), settings and the limits bot.
- **Human Contribution:** ✎ Read the README and checked the commands; committed and pushed.
- **Outcome:** ✎ Committed Nov 6 (7efe645). Tests pass.
- **Commit:** W3C10: README for GUI, CLI, demo mode and automation

### 25. More JUnit tests

- **Date:** 2026-11-06
- **Task:** Add tests where coverage was lowest.
- **Input/Instruction:** Make more JUnit tests for the project.
- **AI Contribution:** Measured coverage with JaCoCo (68.8%). Added FinanceToolsTest (16 tests: every agent tool, its numbers and argument validation), MoreCliCommandsTest (8: ask, ai, report, tx, profile, limits, debt, error messages), PlanningExtrasTest (6: goal/affordability wording, commands, CSV format detection). 219 tests, coverage 73.7% overall and 89.8% outside the GUI.
- **Decisions made while coding:** Found a flaky test: the folder-watcher test could finish while an import was still writing. Fixed the test to wait for the import to finish, and ImportFolderWatcher.close() now waits for a running import.
- **Human Contribution:** ✎ Asked for more tests, ran mvn test (219 passed), committed and pushed.
- **Outcome:** ✎ Committed Nov 6 (5f45d8a). Tests pass.
- **Commit:** More unit tests for agent tools, CLI commands and planning rules

### 26. GUI redesign

- **Date:** 2026-11-06
- **Task:** Make the GUI look like a modern finance app.
- **Input/Instruction:** Krishna: the GUI looks plain/old-fashioned; chose the modern finance app style (sidebar, cards, red maple accent).
- **AI Contribution:** Dark sidebar with icons instead of tabs, page titles, KPI tiles on the dashboard, white rounded cards, red primary buttons, status chips (On track / Behind / Done, YES / TIGHT / NO), restyled tables, inputs, progress bars, chat bubbles and charts. Friendlier labels (Credit card, Dining). Checked with screenshots of all 7 pages.
- **Decisions made while coding:** Only the look changed; every screen still calls the same facade methods, so all 219 tests still pass.
- **Human Contribution:** Asked for the redesign and chose the style.
- **Outcome:** ✎ Committed Nov 6 (63a6ed7). Tests pass.
- **Commit:** GUI redesign: sidebar, cards, status chips and new theme

### 27. Automatic bank format detection

- **Date:** 2026-11-06
- **Task:** Stop asking the user to pick the CSV format.
- **Input/Instruction:** Krishna: instead of selecting the CSV type, make it dynamic.
- **AI Contribution:** Import now detects the bank format from the file's columns (the same check the F13 auto-import already used). GUI: 'Detect automatically' is the default in the format list and the status line says which format was found. CLI: -p is optional and prints 'Detected format: …'. A clear message asks the user to pick the format when nothing matches. 2 new tests (221 total). Added a fake Oct 21 – Nov 6 sample CSV.
- **Decisions made while coding:** Manual choice is still possible for banks whose columns look like another bank's.
- **Human Contribution:** Suggested the change while testing the GUI.
- **Outcome:** ✎ Committed Nov 6 (8535411). Tests pass.
- **Commit:** Detect the bank CSV format automatically (GUI and CLI)

### 28. Report chart and layout fixes

- **Date:** 2026-11-06
- **Task:** Fix disappearing pie labels and overlapping text.
- **Input/Instruction:** Krishna: the report part is bugging out, labels disappear and the wording merges.
- **AI Contribution:** Reproduced at a smaller window size. JavaFX hides or overlaps pie labels on small slices, so the pie now has no labels and a list beside it shows each category with the same colour, a bar, the amount and the %. Number tiles now wrap to 2 x 2 on narrow windows instead of cutting numbers off; progress bars stretch; chips never shrink to '...'; alerts wrap instead of scrolling sideways; report details wrap.
- **Decisions made while coding:** Added a small ResponsiveGrid layout used by the dashboard and the report.
- **Human Contribution:** Found the bug while testing the GUI on my MacBook.
- **Outcome:** ✎ Committed together with the UX rework (60d6ae5, Nov 6).
- **Commit:** Fix report chart labels and make the layout fit smaller windows

### 29. GUI UX rework

- **Date:** 2026-11-06
- **Task:** Make the GUI easy to navigate and modern.
- **Input/Instruction:** Krishna: the GUI is not user friendly, hard to navigate and still old-fashioned. Problems: too many fields on screen, don't know where things are, no guidance when empty, dated controls, unclear where to enter what. Chose the full UX rework.
- **AI Contribution:** AtlantaFX (Primer) theme with the maple-red accent; 13 pages, one per feature, in sidebar groups (Money, Plan, Reports); a Home page with a 6-step getting-started checklist, KPI tiles, 'Needs your attention' (confirm proposals, transactions to sort, budgets over) and a quick question box that opens the chat; '+ Add' buttons open short forms with labels and help text, errors shown inside the form; empty states explain what the page is for and give the one next step; categories are changed with a drop-down right in the table; Settings page for AI, auto-import, limits and reminders; demo-mode rule so 'Set my dining budget to $200' proposes a budget.
- **Decisions made while coding:** Includes the report/layout fix (e28). Only the GUI changed; the facade gained canRedo(). 221 tests pass; all 13 pages and a form dialog checked with screenshots, with and without data.
- **Human Contribution:** Reported the usability problems and chose the approach.
- **Outcome:** ✎ Committed Nov 6 (60d6ae5). Tests pass.
- **Commit:** GUI UX rework: AtlantaFX theme, one page per feature, forms in dialogs, getting-started guide

### 30. TFSA money flow, monthly check-in, import folder setting

- **Date:** 2026-11-06
- **Task:** Follow registered contributions through net worth and forecasts; add a monthly reminder; let the user choose the auto-import folder.
- **Input/Instruction:** Krishna asked what happens to TFSA contributions (net worth, other things), then: every month the user should get an alert to update things, and we should be able to change the import folder.
- **AI Contribution:** 1) TFSA/RRSP/FHSA deposits are categorised as transfers. 2) Confirming a detected contribution also adds it to the matching TFSA/RRSP/FHSA account (undoable), so net worth stays right; a tip appears if no such account exists. 3) Forecasts subtract regular registered transfers from the cash left each month (affordability and goals). 4) Monthly check-in: reminder alert + Home card (import last month's CSVs, update balances, budgets, room) until marked done; CLI 'checkin'. 5) Import folder chosen in Settings (folder picker, saved in a new settings table) or CLI 'inbox <folder>'; the watcher restarts. 6 new tests (227).
- **Decisions made while coding:** Only a contribution MapleCFO detected changes an account balance (the chequing side was already synced from the CSV); manual logs only change room.
- **Human Contribution:** Found the TFSA/net-worth gap by asking how contributions flow; asked for monthly reminders and a configurable folder.
- **Outcome:** ✎ Committed Nov 6 (ada881a). Tests pass.
- **Commit:** TFSA/RRSP/FHSA transfers follow the money, monthly check-in, choosable import folder

### 31. Test isolation fix, in-window pop-ups, room checked while adding

- **Date:** 2026-11-06
- **Task:** Fix two tests that failed on Krishna's Mac; make forms open inside the window.
- **Input/Instruction:** Krishna's mvn test failed (leftover MAPLECFO_INBOX in his terminal). Then: 'Check an amount' opens a new window instead of a pop-up; and instead of a separate check button, the room should be checked while adding the contribution.
- **AI Contribution:** MAPLECFO_INBOX is now read through AppConfig and tests run with empty settings, plus a test for the override (228 tests). All forms, confirmations and messages open as an in-window pop-up (AtlantaFX ModalPane) that dims the page; Esc/Cancel closes, Enter submits. The separate 'Check an amount' button was removed: the 'Add a contribution' form checks the room live as you type (green 'Fits, $X left', red 'over by $Y, CRA charges 1%/month', or a note for withdrawals).
- **Decisions made while coding:** On macOS a JavaFX Dialog without an owner opens as a separate window, so dialogs were replaced by overlays.
- **Human Contribution:** Ran the tests on my Mac and found the failure; reported the pop-up opening as a new window; suggested checking the room while adding instead of a separate button.
- **Outcome:** ✎ Committed Nov 6 (921f649). Tests pass.
- **Commit:** Pop-ups open inside the window; room is checked while adding a contribution

### 32. Saved this year, account activity, RRSP allowance

- **Date:** 2026-11-06
- **Task:** Show per-account activity and withdrawals, saved this month and year; handle over-limit contributions properly.
- **Input/Instruction:** Krishna asked whether going over the limit is OK, then: add transactions for all accounts (e.g. how much was withdrawn from the TFSA) and a 'saved this year' indicator next to 'saved this month'.
- **AI Contribution:** PeriodSummary (income, spending, saved, moved to/from TFSA/RRSP/FHSA) for Home tiles 'Saved in October' and 'Saved in 2026'. Accounts → Activity pop-up: the account's transactions and money in/out this year; for TFSA/RRSP/FHSA also contributions and withdrawals. TFSA/RRSP/FHSA cards show 'put in / taken out this year' and a History pop-up. Transactions page has an account filter; the import account list shows names. RRSP $2,000 allowance (new ALLOWANCE status, orange); over the limit the Save button becomes 'Save anyway — it already happened'. The live check no longer raises an alert on every keystroke. 5 new tests (233).
- **Decisions made while coding:** Over-contributions can still be saved because the app records what happened, but only after a clear warning. Checked CRA pages for the 1%/month rule and the RRSP $2,000 allowance.
- **Human Contribution:** Asked about over-limit contributions; asked for per-account activity and saved-this-year.
- **Outcome:** ✎ Committed Nov 6 (6f536e4). Tests pass.
- **Commit:** Saved this month/year, account activity, RRSP allowance, safer over-limit save

### 33. Withdrawals clear an over-contribution

- **Date:** 2026-11-06
- **Task:** Fix TFSA room staying negative after a withdrawal.
- **Input/Instruction:** Krishna: the TFSA shows a negative number even though I withdrew it.
- **AI Contribution:** Room only counted withdrawals from earlier years, so an over-contribution stayed 'over' after the money was taken out. Now a same-year withdrawal removes the excess right away (down to $0) but does not create new room; for a TFSA the withdrawn amount still comes back on January 1. Applied to TFSA, RRSP and FHSA; the TFSA card says when withdrawn room returns. 3 new tests (236).
- **Decisions made while coding:** Matches CRA: withdrawing the excess stops the 1%/month tax; TFSA room from a withdrawal returns the next year.
- **Human Contribution:** Found the bug by over-contributing and then withdrawing in the GUI.
- **Outcome:** ✎ Committed Nov 6 (3ec76b8). Tests pass.
- **Commit:** Withdrawing an over-contribution clears it (TFSA, RRSP, FHSA)

### 34. Real-life registered-account rules + transaction filters

- **Date:** 2026-11-06
- **Task:** Implement the simplified RRSP/FHSA rules and add filters to Transactions.
- **Input/Instruction:** Krishna asked how the limits work in real life, then: implement it, and add filters to the transactions.
- **AI Contribution:** New contribution kinds: Home Buyers' Plan and Lifelong Learning Plan withdrawals and repayments (no room used; repayment schedule 1/15 or 1/10 a year, HBP 2022-2025 deferral, $60,000 HBP and $10,000/$20,000 LLP limits), FHSA transfer to RRSP (no RRSP room used), pension adjustment in the profile (lowers the RRSP estimate; DB column added with a migration that keeps old data). The contribution form asks 'What happened?' and explains each choice; the RRSP card shows what to repay this year. TransactionFilter (date range, account, category, money in/out, amount range, text) used by a filter bar on the Transactions page with totals, and by 'tx list'. 13 new tests (246).
- **Decisions made while coding:** LLP repayment start simplified to the 5th year after the first withdrawal; noted in the report.
- **Human Contribution:** Asked how CRA limits work and asked to implement them; asked for transaction filters.
- **Outcome:** ✎ Committed Nov 6 (a985778). Tests pass.
- **Commit:** HBP/LLP repayments, FHSA-to-RRSP transfer, pension adjustment, transaction filters

### 35. Demo dataset for testing and videos

- **Date:** 2026-11-06
- **Task:** Build a realistic fake dataset to test every feature and record the videos.
- **Input/Instruction:** Krishna: add more CSV files / information so there is a good dataset for testing, and record the videos from it.
- **AI Contribution:** sample-data/demo: 22 months of fake TD chequing, RBC Visa and savings data (~1,300 rows), November files for the auto-import demo, error files (overlap/duplicates, 3 bad rows, unknown format), and load-demo.sh that sets up profile, 8 accounts, TFSA/RRSP/FHSA history, budgets, goals and debts in a separate demo database. DemoDatasetTest (5 tests) checks what each feature should find. 254 tests pass.
- **Decisions made while coding:** The realistic data exposed two F05 gaps: a price change from a few months ago showed as 'amount varies' instead of 'price went up', and cancelled subscriptions kept showing. Fixed with a price-step check, a 12-month window, dropping charges that missed two dates, and using the new price for yearly cost. Report tip no longer suggests a budget that already exists.
- **Human Contribution:** Asked for a richer dataset for testing and videos.
- **Outcome:** ✎ Committed Nov 6 (f7058dd). Tests pass.
- **Commit:** 22-month demo dataset, price-step detection for recurring charges, clearer report tip

### 36. F16 AI reads an unknown bank CSV

- **Date:** 2026-11-06
- **Task:** Import a bank format MapleCFO doesn't know without the user mapping columns by hand.
- **Input/Instruction:** Krishna: introduce more AI features / use AI to drive things. Chose all four proposed features.
- **AI Contribution:** AiFormatReader: the LLM reads the first 8 lines and returns the columns as JSON; Java builds a ColumnMapping (rejects invalid answers), test-reads the whole file and shows a preview. Saved only when the user accepts. GUI button in the import dialog; CLI learn-format. 6 tests.
- **Decisions made while coding:** The AI never imports directly: a wrong date format shows up as rejected rows in the preview and the format is not saved.
- **Human Contribution:** ✎ 
- **Outcome:** ✎ Committed Nov 6 (f76b1dc). Tests pass.
- **Commit:** F16 AI reads an unknown bank CSV format

### 37. F18 find transactions in plain English

- **Date:** 2026-11-06
- **Task:** Search transactions with a sentence instead of setting filters.
- **Input/Instruction:** Same request (more AI features).
- **AI Contribution:** TransactionQueryParser: the LLM fills in the filter fields as JSON; Java checks each one (real dates, known categories/accounts, sensible amounts) and reports what it ignored. The answer fills the visible filter controls. Keyword rules in demo mode. GUI ask box; CLI tx find. 5 tests.
- **Decisions made while coding:** The search itself stays the deterministic TransactionFilter; the AI only translates the question.
- **Human Contribution:** ✎ 
- **Outcome:** ✎ Committed Nov 6 (1b9f20b). Tests pass.
- **Commit:** F18 find transactions by asking in plain English

### 38. F19 where to put extra money

- **Date:** 2026-11-06
- **Task:** Help decide where a refund or bonus could go.
- **Input/Instruction:** Same request (more AI features).
- **AI Contribution:** ExtraMoneyPlanner (Java): high-interest debt, 3-month emergency fund, FHSA, TFSA, RRSP, then low-interest debt, with amounts and reasons; extra_money_plan tool; the agent explains it. GUI card on 'Can I afford it?'; CLI extra. 5 tests.
- **Decisions made while coding:** All amounts are computed in Java; the order is labelled as common education, not advice.
- **Human Contribution:** ✎ 
- **Outcome:** ✎ Committed Nov 6 (5ef8658). Tests pass.
- **Commit:** F19 where to put extra money

### 39. F17 AI monthly review

- **Date:** 2026-11-06
- **Task:** A monthly review that plans its own steps and proposes next month's budgets.
- **Input/Instruction:** Same request (more AI features).
- **AI Contribution:** MonthlyReviewService runs a multi-step agent task (up to 10 steps): spending, budgets, recurring, goals, room, suggest_budgets, then up to 5 propose_action calls and a to-do list. BudgetPlanner/suggest_budgets tool computes 3-month averages rounded up to $10. CfoAgent now tracks several proposals per answer. GUI review pop-up with Confirm/Dismiss per budget; Home check-in step; CLI review. 3 tests (273 total).
- **Decisions made while coding:** Fixed-order fallback in demo mode uses the same tools. Proposals never apply without confirmation.
- **Human Contribution:** ✎ 
- **Outcome:** ✎ Committed Nov 6 (f9070e6). Tests pass.
- **Commit:** F17 AI monthly review with proposed budgets

### 40. F07 FIRE: own target and more options

- **Date:** 2026-11-06
- **Task:** Let the user change the FIRE target and other assumptions.
- **Input/Instruction:** Krishna: in FIRE make it possible to change the target, and anything else you recommend.
- **AI Contribution:** FireInputs gained an optional own target and target age; FireCalculator adds the yearly saving needed for a target age (annuity formula), the Coast FIRE number, what the target lets you spend, and return scenarios 1 point lower/higher. GUI: 'Use my numbers' (investment accounts + 12 x average cash flow), advanced section (target, retire-at age, return %, withdrawal %), target line on the chart, last plan remembered. CLI fire --mine --target --retire-at. Agent tool accepts target_amount/target_age. 6 new tests (279 total).
- **Decisions made while coding:** Return and withdrawal rate stay within the Stage 1 validation ranges; a custom target that pays for less than planned spending shows a warning.
- **Human Contribution:** ✎ 
- **Outcome:** ✎ Committed Nov 6 (4666a5d). Tests pass.
- **Commit:** F07 FIRE: own target, target age, editable return and withdrawal rate, coast FIRE, use my numbers

## 4. Where the AI's work needed human correction

- API key pasted into `.env.example` (a tracked file) instead of `.env` — caught before any commit; key deleted and replaced (entry 13).
- F05 showed a next date in the past and flagged price increases on variable bills — found by me testing the CLI (entry 14).
- Rent paid by e-transfer was categorised as TRANSFER, so the September report under-counted spending — found by me (entries 16–17).
- First GUI looked plain and was hard to navigate; report labels overlapped on my MacBook — redesigned twice (entries 26, 28, 29).
- Registered contributions changed room but not account balances, so net worth dropped — found by me (entry 30).
- Two tests depended on an environment variable in my terminal; dialogs opened as separate windows on macOS (entry 31).
- Over-limit RRSP contributions ignored the CRA $2,000 allowance; TFSA stayed negative after a withdrawal (entries 32–33).
- A flaky folder-watcher test was found by the AI itself while measuring coverage (entry 25).
- iCloud Desktop sync created duplicate 'Name 2.java' files on my Mac that broke compilation; they were never committed, and the project was moved out of iCloud.
- The 22-month demo dataset showed that F05 labelled old price changes as 'amount varies' and kept listing cancelled subscriptions (entry 35).

## 5. Changes from the Stage 1 design

| Date | By | Change | Detail |
|---|---|---|---|
| 2026-11-01 | Claude | CLI tested on Krishna's Mac: 2 F05 bugs found | Import, recurring and FIRE work. Bugs: past 'next' date and 'price went up' on variable bills. Fix planned for week 3. |
| 2026-11-01 | Claude | F15 bot notifies through a GitHub pull request / issue instead of email | Email needs an SMTP account and password in CI; GitHub's built-in token is safer. EmailNotifier replaced by GitHubIssueNotifier + ConsoleNotifier. |
| 2026-11-01 | Claude | Goal forecast shares the surplus between goals | Available per month = average surplus minus other goals' required monthly amounts. |
| 2026-11-01 | Claude | Affordability verdict TIGHT added | Stage 1 had yes/no. TIGHT = affordable only by using part of the one-month safety buffer. |
| 2026-11-01 | Claude | RRSP estimate scales partial-year pay to 12 months | Stage 1 used last year's income directly; with fewer than 12 months imported it is scaled and labelled an estimate. |
| 2026-11-01 | Claude | Auto-import picks the account from the file name | If a file name contains an account id (e.g. visa_oct.csv) it goes to that account, otherwise to the default (chequing). |
| 2026-11-03 | Krishna | Tested week 3 commits 1–4; found stale rent category in old data | API key, ask, report, accounts and goals worked. Rent stored last week was still TRANSFER, so the September report under-counted spending; fixed by re-importing on a fresh database. |
| 2026-11-06 | Claude | 30 more JUnit tests (219 total) and a watcher shutdown fix | Agent tools, CLI commands, planning rules. Coverage 73.7% overall, 89.8% excluding GUI. |
| 2026-11-06 | Krishna | GUI redesigned: sidebar navigation instead of tabs | Krishna found the first GUI plain. New modern finance-app theme: sidebar, KPI tiles, cards, chips. |
| 2026-11-06 | Krishna | F01: bank CSV format is detected automatically | Stage 1 had the user pick a mapping profile on every import. Now it is detected from the columns; picking is optional. Suggested by Krishna during GUI testing. |
| 2026-11-06 | Krishna | GUI bug found: report labels overlapping and text cut off on small windows | Fixed: pie without labels + breakdown list; responsive tiles; wrapping text. |
| 2026-11-06 | Krishna | GUI reorganised into 13 pages with onboarding and dialog forms | Stage 1 sketched tabbed screens. After testing, Krishna found them hard to use; the GUI now has one page per feature, a Home checklist, empty states and short forms. Uses the AtlantaFX library (MIT) for the controls. |
| 2026-11-06 | Krishna | Registered contributions update account balances and forecasts; FHSA deposits are transfers | Found by Krishna: confirmed contributions only changed room, so net worth dropped and forecasts were too optimistic. |
| 2026-11-06 | Krishna | F13: monthly check-in reminder and a user-chosen import folder | Stage 1 had a fixed inbox folder and only the 30-day reminder. New settings table stores the folder and the last finished check-in. |
| 2026-11-06 | Krishna | Bugs found on Mac: tests depended on environment; dialogs opened as separate windows | Both fixed: settings come from config in tests; pop-ups are in-window overlays. |
| 2026-11-06 | Krishna | F06: RRSP $2,000 allowance; F03: account activity; Home: saved this year | Stage 1 treated any amount over the RRSP limit as an over-contribution; CRA allows $2,000. Per-account activity and year-to-date savings added at Krishna's request. |
| 2026-11-06 | Krishna | Bug: TFSA stayed over-contributed after withdrawing the excess | Fixed in the room rules for TFSA, RRSP and FHSA. Also removed another iCloud 'MapleCfoFacade 2.java' copy (not committed). |
| 2026-11-06 | Krishna | F06: HBP/LLP, FHSA-to-RRSP transfer, pension adjustment; F01/F02: transaction filters | Stage 1 only had contribution/withdrawal. Added at Krishna's request after reviewing the real CRA rules. |

## 6. Commits per period

| Period | Commits | List |
|---|---|---|
| Oct 20–26 | 5 | 0ca6048 Maven project and Money; a2ff9b2 Domain model; 5051cb3 F07 FIRE; a369c1b F08 debt planner; db01f84 F05 recurring charges |
| Oct 27–Nov 2 | 7 | 7c29fe5 F06 room + EventBus; 1904991 F04 budgets; 4e43342 Command undo/redo; 395b727 F02 rules; 7643cbb F01 CSV import; 0c503e2 SQLite; 0d2ae51 Facade + CLI |
| Nov 3–9 | 26 | e832174 LLM layer + F05 fixes; 439f701 F10 agent; e494302 F02 AI + F12 report; c4d5af8 F03 + F09; b925d3a F11; 06d505b GUI part 1; fd9a000 GUI part 2; 05e7ee7 F13 + F14; fc1ab76 F15; 7efe645 README; 5f45d8a More tests; 63a6ed7 GUI redesign; 8535411 Format detection; 60d6ae5 GUI UX rework; ada881a Money flow + check-in; 921f649 In-window pop-ups; 6f536e4 Activity + allowance; 3ec76b8 Withdrawals clear excess; a985778 HBP/LLP, filters; f7058dd Demo dataset; f76b1dc F16; 1b9f20b F18; 5ef8658 F19; f9070e6 F17; 4666a5d FIRE options; (Nov 7) Logo / app icon |

Test count grew from 8 (first commit) to 279; the build is `mvn test` / `mvn package`.
