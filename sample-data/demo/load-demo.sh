#!/usr/bin/env bash
# Loads the fake demo dataset (Alex, a young professional in Toronto, Jan 2025 - Oct 2026)
# into a SEPARATE database so your own data is never touched. Used for testing and the feature videos.
#
#   sample-data/demo/load-demo.sh            # creates ~/.maplecfo/demo.db
#   sample-data/demo/load-demo.sh --fresh    # deletes the demo database first and loads it again
#
# Then start the app on the demo data:
#   MAPLECFO_DB=~/.maplecfo/demo.db java -jar target/maplecfo.jar gui
#   MAPLECFO_DB=~/.maplecfo/demo.db java -jar target/maplecfo.jar
set -euo pipefail
cd "$(dirname "$0")/../.."
DEMO=sample-data/demo
JAR=target/maplecfo.jar
export MAPLECFO_DB="$HOME/.maplecfo/demo.db"
unset MAPLECFO_INBOX
INBOX="$HOME/MapleCFO-demo-inbox"

[ -f "$JAR" ] || mvn -q -DskipTests package
if [ -f "$MAPLECFO_DB" ]; then
  if [ "${1:-}" = "--fresh" ]; then rm "$MAPLECFO_DB"; else
    echo "$MAPLECFO_DB already exists. Run with --fresh to load it again."; exit 1; fi
fi
mkdir -p "$(dirname "$MAPLECFO_DB")" "$INBOX"
m() { java -jar "$JAR" "$@"; }
q() { m "$@" > /dev/null; }

echo "1/6 Profile and accounts"
q profile set --birth-year 2001 --resident-since 2001 --province ON --rrsp-limit 9450 --fhsa-opened 2025
q account add "TD Chequing" CHEQUING 3800 --bank TD --id chequing
q account add "EQ Bank Savings" SAVINGS 4800 --bank "EQ Bank" --id savings
q account add "RBC Visa" CREDIT_CARD 1850 --bank RBC --id visa
q account add "Wealthsimple TFSA" TFSA 33450 --bank Wealthsimple --id tfsa
q account add "Wealthsimple RRSP" RRSP 3140 --bank Wealthsimple --id rrsp
q account add "Wealthsimple FHSA" FHSA 9310 --bank Wealthsimple --id fhsa
q account add "Car loan" LOAN 9800 --bank "TD Auto Finance" --id car-loan
q account add "Student loan" LOAN 6200 --bank NSLSC --id student-loan

echo "2/6 TFSA/RRSP/FHSA history (logged before importing, so the importer does not ask about it again)"
for y in 2019:2000 2020:3000 2021:4000 2022:5000 2023:6000 2024:7000; do
  q room log TFSA "${y#*:}" --date "${y%:*}-06-15"
done
q room log TFSA 1500 --withdrawal --date 2024-08-10
while read -r type amount day kind; do
  if [ "$kind" = WITHDRAWAL ]; then q room log "$type" "$amount" --withdrawal --date "$day"
  else q room log "$type" "$amount" --date "$day"; fi
done < "$DEMO/room_history.txt"

echo "3/6 Importing 22 months of bank files (rules first, AI for unknown shops when a key is set)"
m import "$DEMO/td_chequing_2025-2026.csv" -a chequing
m import "$DEMO/rbc_visa_2025-2026.csv" -a visa
m import "$DEMO/eq_savings_2025-2026.csv" -a savings

echo "4/6 Budgets (Sep - Nov 2026)"
for month in 2026-09 2026-10 2026-11; do
  q budget set GROCERIES 450 -m $month
  q budget set DINING 250 -m $month
  q budget set TRANSPORT 120 -m $month
  q budget set SHOPPING 150 -m $month
  q budget set SUBSCRIPTIONS 120 -m $month
done

echo "5/6 Goals and debts"
q goal add "Emergency fund" 12000 --by 2027-12-31 --saved 8620 --account savings
q goal add "Japan trip" 4000 --by 2027-04-30 --saved 900
q goal add "Home down payment" 40000 --by 2030-12-31 --saved 9310 --account fhsa
q debt add "RBC Visa" --balance 1850 --apr 20.99 --min 55
q debt add "Car loan" --balance 9800 --apr 7.49 --min 285
q debt add "Student loan" --balance 6200 --apr 6.2 --min 95

echo "6/6 Auto-import folder: $INBOX"
q inbox "$INBOX"

echo
m networth
echo
echo "Done. Start the app on the demo data with:"
echo "  MAPLECFO_DB=~/.maplecfo/demo.db java -jar target/maplecfo.jar gui"
echo "For the auto-import demo, copy a file from $DEMO/inbox/ into $INBOX while the app is open."
