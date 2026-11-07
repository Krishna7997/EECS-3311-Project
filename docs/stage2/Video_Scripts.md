# MapleCFO: feature demonstration video scripts (Stage 2)

Two recordings cover every feature: **Video A (GUI)** and **Video B (CLI)**. Each feature is a labelled segment; write
down the start time of each segment while you record and put the timestamped links in the report (Section 7).

## Before recording (once)

1. Use the project outside iCloud: `cd ~/Projects/MapleCFO && mvn -q -DskipTests package`
2. Put your real key in `.env` (never show this file, your terminal history with the key, or the Anthropic Console on screen).
3. Load the demo data: `sample-data/demo/load-demo.sh --fresh`
4. Empty the demo inbox: `rm -f ~/MapleCFO-demo-inbox/*`
5. Close other windows and notifications. Record at 1080p. Speak slowly; 30-60 seconds per feature is enough.
6. Upload both videos as **Unlisted** on YouTube (or a public Google Drive / OneDrive link) and open them in a private
   window to check that no access request appears.
7. Timestamped YouTube link: `https://youtu.be/<id>?t=<seconds>`.

Each segment should show: the feature ID and name (say it), how you start it and what you type, the result, what the
feature does and where the AI is involved, and an error or alternative case where one exists.

---

## Video A: GUI (about 14 minutes)

Start: `MAPLECFO_DB=~/.maplecfo/demo.db java -jar target/maplecfo.jar gui`

| # | Feature | What to do | What to say |
|---|---|---|---|
| A0 | Intro | Show the window and the sidebar. | "MapleCFO is a personal-finance agent for young Canadians. This video shows all 19 features in the GUI, using fake demo data for 'Alex'." |
| A1 | **F13 Auto-sync + monthly check-in** | Home: point at the November check-in card. In Finder copy `sample-data/demo/inbox/chequing_nov2026.csv` and `visa_nov2026.csv` into `~/MapleCFO-demo-inbox`. Wait for the alert. Settings: show the import folder. | "Deterministic. MapleCFO watches a folder; a dropped bank CSV is imported automatically, the format is detected and the account is taken from the file name. The chequing balance updates from the file." Alternative: Settings → change folder. |
| A2 | **F14 Detected contribution** | Home → "Needs your attention": the TFSA $300 and FHSA $500 contributions from the November file. Confirm the TFSA one; show the TFSA account balance went up. Undo, Redo. | "Hybrid rules: transfers that name TFSA/RRSP/FHSA are detected and proposed; nothing changes until I confirm, and it can be undone." |
| A3 | **F01 Import + duplicates + bad rows** | Transactions → Import bank CSV → `sample-data/demo/errors/chequing_overlap_oct25-nov6.csv` → "Detected: TD" → Import. Then `errors/visa_bad_rows.csv`. | "Deterministic. The overlap file only adds new rows: duplicates are skipped by fingerprint. The bad file imports 3 rows and reports the 3 rows it could not read and why." |
| A4 | **F16 AI reads an unknown bank format** | Import `errors/unknown_bank_format.csv` → "MapleCFO could not recognise these columns" → **Let the AI read this file** → preview → name "Tangerine" → Save format and import. Import it again to show it is detected automatically. | "AI + checks: Claude reads the first lines and proposes the columns; Java test-reads the whole file and shows a preview. The format is saved only when I accept it." |
| A5 | **F02 Categorization (rules + AI) + learning** | Transactions → search "HAKKA": with a key, shops the rules don't know (Hakka Legend, Cineplex, Kensington Bakery) were categorised by the AI during import (Source AI), so "Needs a category" is empty. Change one category with the drop-down; Undo. | "Hybrid: about 120 Canadian merchant rules first, the AI only for unknown shops, and only one of our category names is accepted. Corrections teach a new rule and are undoable." |
| A6 | **F18 Ask your transactions** | Type "Uber Eats over $30 in October" → Find. Show the filled-in filters and totals. Change one filter by hand. Clear filters. | "AI + checks: the AI turns my sentence into the filters below; Java checks each field and does the search, so I can see and change what it understood." |
| A7 | **F04 Budgets + alerts** | Budgets: October, Dining is red (291%). Set a new budget for Groceries; show the 80%/100% alert in Home or Alerts. Undo. | "Deterministic, Observer: imports and edits publish events; budgets recompute and warn at 80% and 100%." Error: enter a negative amount → message. |
| A8 | **F05 Subscriptions & bills** | Subscriptions: Netflix, Rogers, Spotify and rent "Price went up"; Hydro "Amount varies"; Presto weekly; Disney+ not shown (cancelled). | "Deterministic detection of monthly, weekly and yearly charges, price steps and cancelled subscriptions." |
| A9 | **F03 Accounts & net worth** | Accounts: groups (cash, registered, debts), net worth. Add an account, update a balance, open Activity for TFSA. | "Composite pattern: accounts form a tree and the net worth is the sum. Card and loan balances count as debts." |
| A10 | **F09 Savings goals** | Goals: Japan trip "Behind". Add a goal; open the forecast. | "Deterministic forecast from my average monthly surplus, shared between goals." |
| A11 | **F11 Can I afford it?** | Afford: "New laptop", 1,500, by March → result. Try 15,000 → NOT YET with the shortfall. | "Deterministic: cash, what is left each month after TFSA/RRSP/FHSA transfers, goals and a one-month buffer." |
| A12 | **F19 Extra money** | Same page: Have extra money? 3000 → Show options → the split, then the AI explanation appears. | "Hybrid: Java splits the money (expensive debt, emergency fund, FHSA, TFSA, RRSP); the AI only explains it. Education, not advice." |
| A13 | **F06 TFSA / RRSP / FHSA room** | TFSA·RRSP·FHSA: room cards. Add a contribution: type 25000 for TFSA → live "over by" warning and 1%/month note. Change to 500 → "Fits". Show an HBP withdrawal kind and the repayment banner. History. | "Deterministic CRA rules: TFSA room since 18, withdrawals come back next January, RRSP $2,000 allowance, FHSA $8,000/year, HBP and LLP repayments." |
| A14 | **F14 RRSP estimate** | Same page: "Don't know? Estimate it from my pay" → explanation (12 months of 2025 pay). Pension adjustment field. | "Estimate from pay deposits, clearly marked as low because pay is after tax." |
| A15 | **F08 Debt payoff** | Debt payoff: avalanche vs snowball, extra $200/month, interest saved. | "Strategy pattern: avalanche and snowball are interchangeable strategies." |
| A16 | **F07 FIRE** | Retirement: Use my numbers → plan. Open "Target, retirement age and assumptions": own target 900,000, retire at 50, return 4%. Show the banner, Coast FIRE, what-if table, chart. Error: retire-at age below my age. | "Deterministic projection; I can change the target and assumptions and see what changes." |
| A17 | **F10 AI CFO chat** | Ask MapleCFO: "How much did I spend on dining last month?", then "Can I afford a $2,000 trip by March?", then "Set my dining budget to 300" → Confirm. | "AI agent: Claude decides which tools to call (shown under the answer); every dollar amount is checked against the tool results; changes need my confirmation." |
| A18 | **F12 Monthly report** | Monthly report: October 2026 → Create report → figures, pie, AI summary. | "Hybrid: figures from Java, a short summary written by the AI and checked against the figures (template if the AI fails)." |
| A19 | **F17 AI monthly review** | Same page: "AI review & next month's budgets" → review text + proposed budgets → Confirm one, Dismiss one → Budgets shows the new November limit. | "Agentic: the AI plans several steps on its own (spending, budgets, subscriptions, goals, room, suggestions), proposes up to five budgets and writes a to-do list. Nothing changes until I confirm." |
| A20 | **F15 Self-updating limits** | Settings → contribution limits: source and last check, press "Check for new limits". Briefly show `.github/workflows/limits-update.yml` on GitHub. | "Deterministic: limits are downloaded at most weekly, validated and cached; a weekly bot reads the CRA pages and opens a pull request or an issue." |

---

## Video B: CLI (about 8 minutes)

Use a fresh copy of the demo database so the GUI video is not affected:
run these three lines first, each on its own (zsh only knows an alias from the next line you type):

```
cd ~/Projects/MapleCFO && cp ~/.maplecfo/demo.db ~/.maplecfo/cli.db
export MAPLECFO_DB=~/.maplecfo/cli.db
alias m='java -jar ~/Projects/MapleCFO/target/maplecfo.jar'
```

| # | Feature | Commands | Notes |
|---|---|---|---|
| B0 | Intro | `m --help` | "The same features from the command line; the GUI and CLI share one facade." |
| B1 | F01 | `m import sample-data/demo/errors/visa_bad_rows.csv -a visa` | Detected format, 3 imported, 3 rejected rows with reasons. |
| B2 | F16 | `m learn-format sample-data/demo/errors/unknown_bank_format.csv --name Tangerine` then add `--save -a chequing` | AI suggestion, test read, preview; saved only with --save. |
| B3 | F02 | `m tx search hakka --limit 3` then `m tx recategorize <id> SHOPPING` (undo is shown in B11: one-shot commands do not share an undo history) | Source AI = categorised by the AI at import (the rules don't know the shop). `m tx list --review` is empty with a key. |
| B4 | F18 | `m tx find uber eats over 30 in october` | Shows "Showing ..." and "[understood by the AI]". |
| B5 | F04 | `m budget set DINING 300 -m 2026-11` then `m budget status -m 2026-10` | Error: `m budget set DINING -5` |
| B6 | F05 | `m recurring` | |
| B7 | F03 / F09 / F11 | `m networth`, `m goal list`, `m afford 1500 --by 2027-03-31` | |
| B8 | F19 | `m extra 3000` | Plan + AI explanation. |
| B9 | F06 / F14 | `m room`, `m room check TFSA 25000`, `m room log RRSP 15000 --kind HBP_WITHDRAWAL --date 2026-05-01`, `m room`, `m rrsp-estimate -y 2027` | |
| B10 | F07 / F08 | `m fire --mine --target 900000 --retire-at 50`, `m debt plan --compare --extra 200` | Error: `m fire --mine --retire-at 20` |
| B11 | F10 / F17 (interactive) | `m` (interactive) → `ask How much TFSA room do I have?` → `review -m 2026-10` → `confirm A1` → `undo` → `exit` | Proposals live while the interactive session is open. |
| B12 | F12 | `m report -m 2026-10` | |
| B13 | F13 | `m inbox`, `m checkin`, `m watch --once`, `m reminders` | |
| B14 | F15 | `m limits` and `m limits refresh --force` | |
| B15 | Demo mode | `mv .env .env.off && m ask how much did I spend on dining last month && mv .env.off .env` | Shows that without a key the app still runs, with clearly labelled fixed rules. Do not open `.env`. |

## After recording

- Fill the timestamps into the table in report Section 7 (and Section 2's "Video" column).
- Check every link in a private browser window.
