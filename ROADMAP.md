# Pennywise Roadmap

> **Goal:** know where every rupee goes — without typing every rupee.

Pennywise is a personal Android finance app for someone who pays mostly through UPI
(GPay, PhonePe, Paytm). It should capture spending automatically, show where money goes
(merchants, people, subscriptions, bike), manage monthly income/budgets/goals, track money
lent to others, and use light game mechanics so it gets opened every day.

- **Capture** — UPI/card spends land in the app automatically or via one import
- **Understand** — which merchants, people, subscriptions and the bike take money
- **Control** — monthly income, budgets and savings goals
- **Track people** — who owes money and when they paid it back
- **Habit** — small game mechanics so the app is used daily, not abandoned

---

## Owner decisions (Oct 2026)

- **Banks for SMS parsing:** Bank of India (BOI), Kotak Mahindra Bank
- **Data import:** all four — bank SMS auto-read, bank statement CSV/Excel,
  PhonePe/Paytm PDF, and manual quick-add
- **Priorities after Phase 0:** lending tracker, then goals & gamification
  (subscriptions and vehicle tracking follow later)
- **Next step:** owner shares masked BOI and Kotak SMS samples before Phase 0 starts

---

## 1. Current state vs target

```
 TODAY (v0.1)                      TARGET (v1.0)
 ─────────────                     ─────────────
 [x] Manual add/edit/delete        [ ] Auto-capture from bank SMS
 [x] 11 fixed categories           [ ] Statement import (CSV/Excel)
 [x] Search / filter / sort        [ ] "To review" inbox
 [x] Dashboard + pie chart         [ ] Merchants + auto-category
 [x] Budget bars (hard-coded)      [ ] Editable monthly budgets
 [~] Shows $ instead of ₹          [ ] Income & salary cycle
 [~] Fake demo data                [ ] Recurring + subscriptions
 [!] Data wiped on DB change       [ ] Lend / borrow ledger
                                   [ ] Vehicle (bike) costs
                                   [ ] Goals, streaks, badges
                                   [ ] Reminders + backup

 [x] works   [~] works but wrong   [!] risky   [ ] to build
```

Known gaps in v0.1:

- Budget limits are seeded sample values; there is no screen to edit them
  (`PennywiseRepository.setBudget` exists but is never called).
- Demo transactions are seeded on first launch.
- Currency is formatted as USD (`Formatters` uses `Locale.US`, dialog shows `$`).
- `fallbackToDestructiveMigration` wipes all data on any schema change.
- Firebase AI / Retrofit dependencies are included but unused.

---

## 2. Architecture

```
 ┌──────────────── DATA SOURCES ────────────────┐
 │  Bank SMS     Statement file    Manual entry │
 │  (auto)       (CSV/XLS/PDF)     (quick add)  │
 └─────┬──────────────┬─────────────────┬───────┘
       │              │                 │
       ▼              ▼                 │
 ┌─────────────────────────────┐        │
 │       IMPORT PIPELINE        │        │
 │ 1. Parse (amount, dr/cr,     │        │
 │    merchant/VPA, date, ref)  │        │
 │ 2. De-duplicate (UPI ref)    │        │
 │ 3. Match merchant → category │        │
 │ 4. Detect recurring/lending  │        │
 └──────────────┬──────────────┘        │
                ▼                       │
 ┌─────────────────────────────┐        │
 │   "TO REVIEW" INBOX          │        │
 │  swipe ✓ confirm / ✎ edit    │        │
 └──────────────┬──────────────┘        │
                ▼                       ▼
 ┌──────────────────────────────────────────────┐
 │            LOCAL DATABASE (Room)             │
 │ Transactions · Merchants · Categories ·      │
 │ Budgets · Recurring · Loans · Vehicles ·     │
 │ Goals · Achievements                         │
 └──────┬──────────────┬───────────────┬────────┘
        ▼              ▼               ▼
 ┌────────────┐ ┌─────────────┐ ┌──────────────┐
 │ INSIGHTS   │ │ REMINDERS   │ │ GAME ENGINE  │
 │ budgets,   │ │ (WorkManager│ │ streaks, XP, │
 │ trends,    │ │ bills, dues,│ │ badges,      │
 │ merchants  │ │ lent money) │ │ challenges   │
 └─────┬──────┘ └──────┬──────┘ └──────┬───────┘
       └───────────────┼───────────────┘
                       ▼
                ┌─────────────┐
                │  UI SCREENS │
                └─────────────┘
```

Everything stays on the phone: no server, no bank login. Backup is a user-exported file.

---

## 3. Data model

```
 ┌───────────────┐       ┌──────────────────┐
 │   Category    │1     *│   Transaction    │
 │───────────────│◄──────│──────────────────│
 │ id, name      │       │ id, amount, type │
 │ icon, color   │       │ date, note       │
 │ isCustom      │       │ categoryId       │
 └───────┬───────┘       │ merchantId?      │
         │1              │ source (SMS/CSV/ │
         │*              │   MANUAL)        │
 ┌───────┴───────┐       │ upiRef? (dedupe) │
 │    Budget     │       │ status (REVIEW/  │
 │───────────────│       │   CONFIRMED)     │
 │ categoryId    │       │ recurringId?     │
 │ month (YYYYMM)│       │ loanId?          │
 │ limit         │       │ vehicleId?       │
 └───────────────┘       └──┬────┬────┬─────┘
                            │    │    │
        ┌───────────────────┘    │    └──────────────┐
        ▼*                       ▼*                  ▼*
 ┌───────────────┐   ┌──────────────────┐   ┌──────────────┐
 │   Merchant    │   │ RecurringRule    │   │   Vehicle    │
 │───────────────│   │──────────────────│   │──────────────│
 │ name          │   │ name, amount     │   │ name (bike)  │
 │ aliases (VPA) │   │ every N days/    │   │ odometer     │
 │ defaultCatId  │   │   month          │   │ fuel/service │
 │ isFavourite   │   │ nextDue          │   │ /insurance   │
 └───────────────┘   │ kind: BILL/SUB/  │   └──────────────┘
                     │   SIP/PERSON     │
                     └──────────────────┘
 ┌───────────────┐   ┌──────────────────┐   ┌──────────────┐
 │    Person     │1 *│      Loan        │   │     Goal     │
 │───────────────│──►│──────────────────│   │──────────────│
 │ name, phone?  │   │ direction LENT/  │   │ name, target │
 └───────────────┘   │   BORROWED       │   │ saved, due   │
                     │ amount, dueDate  │   └──────────────┘
                     │ status OPEN/     │   ┌──────────────┐
                     │   PARTIAL/PAID   │   │ Achievement  │
                     │ repayments[]     │   │ key, earnedAt│
                     └──────────────────┘   └──────────────┘
```

---

## 4. Key user flows

### 4a. Auto-capture (paying ₹250 on GPay)

```
 You pay ₹250 on GPay
        │
        ▼
 Bank sends SMS: "Rs.250.00 debited ... to VPA
                  swiggy@icici ... UPI Ref 6123..."
        │
        ▼
 Pennywise reads SMS ──► parse ──► ref seen before?
                                       │yes → ignore
                                       │no
                                       ▼
                         merchant "swiggy" known?
                           │yes → category = Food
                           │no  → ask once, remember
                                       ▼
                         Notification: "₹250 Swiggy → Food ✓"
                                       ▼
                         Inbox: tap ✓ (or auto-confirm
                         for trusted merchants)
```

### 4b. Statement import (history / backup)

```
 Net-banking → download CSV/Excel
        │
        ▼
 Pennywise ▸ Import ▸ pick file ▸ map columns once
        │
        ▼
 Preview: "142 rows · 9 duplicates skipped · 23 new merchants"
        │
        ▼
 Bulk-categorise new merchants ▸ Confirm ▸ done
```

### 4c. Money lent

```
 Lend ₹2,000 to Rahul ──► Loan(OPEN, due 30 Oct)
                                │
            reminder on due date│
                                ▼
 Rahul repays ₹1,500 ──► PARTIAL (₹500 left)
 Rahul repays ₹500   ──► PAID ✓  (+badge "Settled up")

 SMS shows "credited ₹2,000 from rahul@okaxis"
   └─► app suggests: "Mark Rahul's loan as paid?"
```

### 4d. Monthly cycle

```
  Salary day ──► set/confirm income ──► budgets reset
      │                                     │
      ▼                                     ▼
  Daily: review inbox (30 sec) ──► streak +1
      │
      ▼
  Alerts: 80% of budget · bill due · loan due
      │
      ▼
  Month end ──► Report card (grade, top merchants,
                saved vs goal, badges earned)
```

---

## 5. Screen map

```
 ┌──────────────────── Bottom navigation ────────────────────┐
 │  Home        Activity       Insights        More          │
 └───┬────────────┬─────────────┬───────────────┬────────────┘
     │            │             │               │
     ▼            ▼             ▼               ▼
 ┌─────────┐ ┌──────────┐ ┌───────────┐ ┌─────────────────┐
 │Balance  │ │All txns  │ │Budgets    │ │Lending ledger   │
 │This-    │ │Review    │ │Top        │ │Subscriptions &  │
 │month    │ │inbox (3) │ │merchants  │ │  recurring      │
 │budget   │ │Search /  │ │Trends     │ │Goals            │
 │Streak 🔥│ │filters   │ │Report card│ │Vehicle (bike)   │
 │Due soon │ └──────────┘ └───────────┘ │Import statement │
 │Quick-add│                            │Categories       │
 │chips    │      [ + ] FAB:            │Backup / export  │
 └─────────┘      quick add             │Settings         │
                                        └─────────────────┘
```

Quick add with pre-filled forms:

```
 ┌──────────────────────────────────┐
 │  ₹ [ 40 ]                        │
 │  Recent: [Chai ₹20] [Metro ₹40]  │
 │          [Petrol ₹500] [Swiggy]  │
 │  Merchant: Metro  → Transport ✓  │
 │  Date: Today ▾                   │
 │            [ Save ]              │
 └──────────────────────────────────┘
   Tap a chip → everything pre-filled → Save
```

---

## 6. Gamification

```
 ACTIONS                    REWARDS                 LEVELS
 ───────                    ───────                 ──────
 Review inbox today ──┐                         Lv1 Tracker
 Log a cash expense ──┼──► XP points ──────────►Lv2 Saver
 No-spend day ────────┤                         Lv3 Planner
 Stay under budget ───┼──► Streak 🔥 (daily)     Lv4 Money Pro
 Hit goal milestone ──┤
 Loan settled ────────┴──► Badges 🏅

 MONTHLY CHALLENGES (pick 1–2)
 • "Food under ₹3,000"
 • "3 no-spend days a week"
 • "Cancel 1 unused subscription"
 • "Save ₹5,000 toward Goal: New phone"

 GOAL MILESTONES
 [██████░░░░] 60%  New phone ₹30,000
   25% 🥉   50% 🥈   75% 🥇   100% 🏆
```

Rules: streaks reward *reviewing*, not *spending*; a missed day uses a "freeze" instead
of resetting to 0; no leaderboards and nothing that nudges risky money behaviour.

---

## 7. Phased roadmap

```
 Phase  │ Wk1 Wk2 Wk3 Wk4 Wk5 Wk6 Wk7 Wk8 Wk9 Wk10
 ───────┼─────────────────────────────────────────
 0 Fix  │ ███
 1 Core │     ███████
 2 Auto │             ███████████
 3 Money│                         ███████
 4 Game │                                 ███████
 5 Polish                                     ███
 (rough estimates, part-time; adjust after each phase)
```

| Phase | What gets built | Done when |
|---|---|---|
| **0. Foundation** | ₹ currency; remove fake data; Room migrations (no data loss); backup/export to file; editable budgets; custom categories | Trusted with real data for a week |
| **1. Fast manual entry** | Merchants + remembered categories; quick-add chips; recent/favourites; income & salary day; per-category monthly budgets | Logging an expense takes < 5 seconds |
| **2. Auto-capture** | Bank SMS receiver + parser (owner's banks first); review inbox; dedupe by UPI ref; CSV/Excel statement import | ≥ 80% of spends enter without typing |
| **3. Money features** | Recurring & subscriptions with reminders; lend/borrow ledger with partial repayments; vehicle costs (fuel, service, cost/km) | All money questions answered in-app |
| **4. Gamification** | Streaks, XP, badges, monthly challenges, savings goals with milestones, month-end report card | Opened daily without forcing it |
| **5. Polish** | Home-screen widget, insights ("30% more at Swiggy"), optional Gemini for unknown merchants/PDFs, dark mode | Feels finished |

---

## 8. Technical decisions

| Topic | Decision | Why |
|---|---|---|
| Storage | Room with real migrations | Destructive migration deletes data on every schema change |
| Navigation | Navigation Compose | Many more screens (lending, goals, vehicle…) |
| SMS capture | `RECEIVE_SMS` receiver + Indian bank SMS parser | One source covers GPay, PhonePe and Paytm via the bank |
| Distribution | Sideload the APK (no Play Store for now) | Play restricts SMS permissions; not needed for personal use |
| Reminders | WorkManager + notifications | Reliable and battery-friendly |
| Import | System file picker + CSV/XLS parser; PDF later | Bank CSVs are the cleanest source |
| AI (optional) | Gemini (already a dependency) for unknown merchants/PDFs only | Fallback; rules handle most cases |
| Backup | Export JSON/CSV to Drive/Files; import to restore | Protects against phone loss |

Not pursued: RBI Account Aggregator (requires a registered financial-information user,
not practical for a personal app).

---

## 9. Risks and mitigations

```
 RISK                              MITIGATION
 ────                              ──────────
 Bank SMS formats differ      ──►  Start with owner's 1–2 banks;
                                   add formats as they appear
 Android blocks SMS permission──►  May need "Allow restricted
   for sideloaded apps             settings" in App info; CSV import
                                   as fallback
 Duplicates (SMS + statement) ──►  Match on UPI ref, else
                                   amount + date + merchant
 Data loss                    ──►  Migrations + export backup
                                   before Phase 1
 Losing interest              ──►  Ship small phases used daily;
                                   gamification in Phase 4
 Scope creep                  ──►  Each phase has a "done when";
                                   nothing new until met
```

---

## 10. Success metrics

```
 Metric                         Target
 ──────                         ──────
 Txns captured automatically    ≥ 80%
 Time to log manually           < 5 sec
 Days opened per week           ≥ 5
 Uncategorised transactions     < 5%
 Budgets actually set           all major categories
```

---

## 11. Next actions

```
 [Owner]  1. Install the current build from AI Studio on the phone
 [Owner]  2. Share bank name(s) and 2–3 sample debit/credit SMS
            (account numbers masked)
 [Owner]  3. Download one month's bank statement as CSV/Excel
 [Dev]    4. Build Phase 0 on a branch → PR → merge
 [Both]   5. Use it for a week → adjust → start Phase 1
```

---

## Market context (Oct 2026)

- **Axio (ex-Walnut), Moneyview** — automatic tracking from bank SMS on Android, but both
  push loans / pay-later, and the tracking data feeds lending.
- **Manual / privacy-first apps** (Pocket Clear, Vitmora, etc.) — no SMS access, but every
  entry is typed by hand.
- **Lending/IOUs** usually need a separate app (Splitwise); subscriptions another one.
- **UPI statements:** PhonePe emails statements by month/FY; Paytm offers PDF (Excel
  reported); Google Pay has no confirmed official export. Bank CSV covers all UPI apps.
- **Gap Pennywise fills:** UPI-aware capture + lending + subscriptions + vehicle +
  motivation, on-device, with no loan upsell.
