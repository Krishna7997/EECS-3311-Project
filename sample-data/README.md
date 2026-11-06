# Sample data (fake)

All files here are **made-up demo data** for testing and demonstrations. They contain no real
transactions or account numbers. Never put real bank exports in this repository.

## Small samples

| File | Bank layout | Mapping profile |
|---|---|---|
| `td_chequing_sample.csv` | TD bank account export (no header row) | `TD bank account` |
| `rbc_visa_sample.csv` | RBC credit card export (includes one bad row on purpose) | `RBC` |
| `savings_generic_sample.csv` | Simple `Date,Description,Amount,Balance` file | `Generic (Date, Description, Amount)` |
| `td_chequing_nov2026_sample.csv` | TD, Oct 21 – Nov 6 2026 | `TD bank account` |

## Demo dataset (`demo/`): 22 months for testing and the feature videos

"Alex", a young professional in Toronto, January 2025 – November 6, 2026. Load it into a separate
database (your own data is not touched):

```
sample-data/demo/load-demo.sh            # creates ~/.maplecfo/demo.db (--fresh to reload)
MAPLECFO_DB=~/.maplecfo/demo.db java -jar target/maplecfo.jar gui
MAPLECFO_DB=~/.maplecfo/demo.db java -jar target/maplecfo.jar        # CLI, interactive
```

The script sets up the profile, 8 accounts, TFSA/RRSP/FHSA history, budgets, 3 goals and 3 debts, and imports
the files below up to October 31, 2026. November is left for the live auto-import demo.

| File | Layout | What it contains |
|---|---|---|
| `td_chequing_2025-2026.csv` | TD | ~1,000 rows: pay every 2 weeks, rent (up from $1,450 to $1,525 in Sep 2026), bills, groceries, coffee, TFSA/FHSA/RRSP contributions, a TFSA withdrawal (Dec 2025), card payments |
| `rbc_visa_2025-2026.csv` | RBC | ~300 rows: gas, Amazon, restaurants, shopping, travel, subscriptions |
| `eq_savings_2025-2026.csv` | Generic | Monthly transfers, interest, one transfer back to chequing |
| `inbox/chequing_nov2026.csv`, `inbox/visa_nov2026.csv` | TD, RBC | Nov 1–6. Copy into the import folder while the app is open: auto-import, balance update, and a TFSA + FHSA contribution to confirm |
| `errors/chequing_overlap_oct25-nov6.csv` | TD | Overlaps the history: shows duplicates being skipped |
| `errors/visa_bad_rows.csv` | RBC | 3 bad rows (bad date, missing amount, text amount) next to 3 good ones |
| `errors/unknown_bank_format.csv` | none | Columns no saved format matches: MapleCFO asks you to choose the format |
| `room_history.txt` | | The contributions in the CSVs, read by `load-demo.sh` |

What each feature should find (checked by `DemoDatasetTest`):

- **F05:** Netflix ($16.99 → $18.99), Rogers ($75 → $82), Spotify ($11.99 → $12.99) and rent show "price went up". Hydro, Enbridge and the card payment show "variable amount". Presto is weekly. Disney+ (cancelled in April 2026) is not listed.
- **F04 / F12:** October 2026 dining is far over its $250 budget (Uber Eats spike).
- **F14:** the RRSP estimate for 2026 uses 12 months of 2025 pay ($54,080 → $9,734.40); for 2027 it scales 10 months of 2026 pay.
- **F02:** shops the rules do not know (Hakka Legend, Kensington Bakery, Quick Mart, LCBO, Cineplex, loan payments, interest) are categorised by the AI with a key, or wait in the review list in demo mode.
