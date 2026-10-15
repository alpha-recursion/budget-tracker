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
- **Priorities after Phase 0:** SMS scanning first, then lending tracker, then goals &
  gamification (subscriptions and vehicle tracking follow later)
- **SMS samples received:** Kotak UPI debit and BOI UPI debit (credit samples still needed)
- **SMS scan rules:** only Kotak & BOI messages; credits included but flagged and unticked
  until confirmed; self transfers can be marked and are excluded from totals; categories
  are chosen once per payee and remembered

## Working agreement

- Plan before building; look for flaws in the plan and fix them first. Only proceed when
  90%+ confident the step is right.
- After every step, verify the goal was met, check for drift, and look for new and existing
  bugs.
- Never assume: read the code, or ask the owner (multiple-choice questions).
- Commits and PRs carry no AI co-author/attribution lines.

## Progress

| Phase | Status |
|---|---|
| 0. Foundation | **Built** — ₹ currency, demo data removed, safe DB migrations (v2), custom categories, editable budgets, JSON backup / restore / erase. Needs on-device check. |
| 2. Auto-capture | **SMS history scan built** — permission, 7/14/30-day or since-date scan, grouped review, de-duplication, payee memory, self transfers (DB v3). **Live capture built** (1.5): known payees added automatically, the rest wait for review. Verified credit formats (1.8) pending. Needs on-device check. |
| 3. Money features | **Lending tracker built** (2.1–2.3): people, lent / got back / borrowed / paid back, balances, due dates and overdue, settle up; SMS and existing transactions can be marked as lending (DB v5). Needs on-device check. |
| 4. Gamification | **Savings goals built** (3.1): money set aside (not spending), 25/50/75/100% milestones with a confetti celebration, optional date with weekly plan and on-track / behind (DB v6). Needs on-device check. |
| 1 | Credit SMS formats (1.8) on hold until samples arrive |
| 5 | Recurring payments built (5.1); bike costs (5.2) not started |

## To-do list

### 1. SMS scanning (in progress)
- [x] 1.1 Permission screen: why SMS access is needed, grant button, help for Android's
      "Allow restricted settings"
- [x] 1.2 History scan: last 7 / 14 / 30 days or since a chosen date, with preview summary
- [x] 1.3 Review preview: grouped by payee, tick/untick items, import selected
- [x] 1.4 De-duplication: never import the same SMS twice (bank + direction + UPI ref, or
      message fingerprint); flag likely manual duplicates (same amount, same day)
- [x] 1.5 Live capture: Settings switch "Auto-detect bank SMS"; payments to payees with a
      remembered category are added at once, everything else waits for review; one combined
      notification; Home card "N bank payments to review"
- [x] 1.6 Payee memory: category (or "self transfer") remembered per payee
- [x] 1.7 Database v3: `sms_imports` and `merchant_rules` tables (+ backup/restore/erase)
- [ ] 1.8 Exact credit formats once Kotak/BOI "money received" samples arrive
- [x] 1.9 Scan details: what was read / recognised, unrecognised examples, and
      "Scan since <date>" when the latest bank SMS is older than the period
- [x] 1.10 Teach by example: save a new SMS format from one sample (DB v4)
- [x] 1.11 Similar vendors: truncated payee names grouped in the SMS preview; changing a
      category offers to update the same vendor's other transactions; remembered per vendor

### 1b. Category & vendor UX (done)
- [x] Category grid picker (4 per row, most-used first) in form, budget editor and SMS preview
- [x] Auto-category while typing a known vendor in the Add form
- [x] Top vendors card in Analytics (this month, similar names combined)

### 1c. Premium UI redesign (done)
- [x] Design foundations: reference palette (ink #021720, blue #0B51D9, green #51D200 on a
      light grey canvas), Plus Jakarta Sans (has ₹ and tabular figures), one type scale,
      contrast-checked text colours, soft ink-tinted shadows
- [x] Shared components (cards, chips, segmented control, buttons, fields, list rows)
- [x] App shell: large page titles, bottom bar with centre + and Settings as a tab
- [x] Home: dark balance hero, budget gauge, smooth weekly spending chart, rounded donut
- [x] Transactions grouped by day; full-screen Add/Edit page; Insights and Settings restyled

### 2. Lending tracker (done, in-app reminders only)
- [x] 2.1 People + lent/borrowed ledger: "Money lent" screen (Home card → people list →
      person history); kept out of spending, income and budgets (DB v5 `debts` table)
- [x] 2.2 Partial repayments, settle up, due dates with overdue shown in red (no phone
      notification yet: would need WorkManager)
- [x] 2.3 SMS review: Spending / Lending / Self transfer per payee; the person and direction
      are guessed from the payee name and who owes whom. Edit page: "This was money I lent"
      moves an imported payment to Money lent
- [x] 2.4 Due-date phone notifications (WorkManager): 10 AM the day before money is due, switch
      in Settings; tap opens Money lent. Needs on-device check.

### 3. Goals & gamification
- [x] 3.1 Savings goals: Home card + Goals screen; add / take out money (set aside, not
      spending); 25/50/75/100% milestone badges, each celebrated once with confetti; optional
      target date with "save ₹X/week" and on track / behind / date passed
- [x] 3.1b Goal tracking: "At this pace" finish date (last 8 weeks), savings-over-time
      chart, "free to spend" on Home (balance minus set aside), Achieved shelf with
      "I bought it" (records the expense, releases the money), named checkpoints (DB v7)
- [x] 3.1c Round-up jar: spare change (to the next ₹10 / ₹50 / ₹100) from each expense
      after switching on, moved to a goal with one tap
- [x] 3.2a Weekly saving streak with one freeze (earned back after 4 saving weeks)
- [x] 3.2b Daily check-in ("I've logged everything" button on Home) with check-in and no-spend-day
      streaks (a no-spend day needs a check-in and zero expenses), one freeze earned back after 7
      days, XP, 4 new badges, optional 9 PM reminder. Check-in days are kept on the phone, not in
      the JSON backup. Needs on-device check.
- [x] 3.3 XP, 10 levels and 14 badges, all worked out from the owner's data; new badges and
      level-ups are celebrated once; Badges screen (Goals → level card, or Settings → Rewards)
- [x] 3.3b Celebration chime + vibration (silent when the phone is; switch in Settings)
- [x] 3.4 Monthly challenges (3 suggested from last month, pick any; category limit, total
      spend, save into goals, no-spend days; +100 XP and the Challenger badge per win) and a
      graded report card per month (A+ to D), shown on Home in the first week of a month (DB v8)

### 4. Faster manual entry
- [x] 4.1 Quick-add chips on Home: frequent payees (last 60 days) + pinned; tap adds with Undo,
      hold to change the amount, pin or hide
- [x] 4.2 Monthly income and payday: "₹X left · ₹Y a day" for the pay cycle (income minus
      spending and goal deposits since the last payday) and a salary-day card to add it

### 5. Recurring, subscriptions & bike
- [x] 5.1 Recurring payments/subscriptions (reminders only, DB v9): weekly / monthly / yearly,
      "Recurring payments" screen (Settings or the Home card), phone alert 3 days before at
      10 AM with its own switch, "still to pay this month" card on Home. Payments that repeat
      in your spending (including imported bank SMS) are suggested for one-tap adding. Needs
      on-device check.
- [ ] 5.2 Bike costs: fuel, service, insurance, cost per km

### 6. Statement import & polish
- [ ] 6.1 Bank CSV/Excel import, then PhonePe/Paytm PDF
- [ ] 6.2 Widget, insights, dark mode

### 7. Input guardrails (done)
- [x] One shared rule set (`InputGuard`) runs on every keystroke and paste: amounts take digits
      only (one decimal point, 2 decimals, up to 8 digits, no leading zeros; "₹1,250" pastes as
      1250); person names take letters, spaces and . ' - only (no digits); payee, goal,
      checkpoint, category and recurring names take letters, digits and punctuation (no emoji
      or symbols) with length caps (60 / 40 / 30); notes drop control characters and are capped
      at 200; search and the SMS box are capped. No leading spaces or double spaces while
      typing; trailing spaces are removed on save. A name must contain a letter or number.

### 8. Redesign v4 (built, needs on-device check)
Source: Claude Design handoff "Pennywise Redesign v4" (Home, Transactions, New transaction,
Insights, Settings, Badges). Owner decisions (Oct 2026):
- Follow the design exactly on Home: quick-add chips, payday countdown, "bank payments to
  review", salary-day and monthly report cards are removed from Home. They stay reachable
  elsewhere: SMS review via Import SMS and the capture notification, income & payday in
  Settings, report cards via Goals → Challenges.
- Category picker in the New transaction sheet: wrapping icon + name chips, most-used first.
- Purple accent `#6D28D9` and navy `#0B1A2E` everywhere, income green `#1F7A3D`.
- Full v4 motion, switched off when the phone's "remove animations" setting is on.
- No notification bell or avatar (no inbox or profile exists); greeting and date only.
- Out of scope (design marks "not yet done"): dark mode, custom keypad, category palette.

Slices, each committed separately so it can be built and checked before the next:
- [x] 8.1 Foundation: colours, radii (cards 20, hero 24, sheets 28), two-layer shadows,
      pill buttons and pill segmented control with sliding thumb, v4 type scale, 16dp
      screen gutter, motion tokens that respect "Remove animations"
- [x] 8.2 Shell: floating navy pill nav (Home, Activity, +, Insights, Settings), active tab
      shows its label, raised gradient add button
- [x] 8.3 Home: greeting, period hero (Today / This week / This month, mini bars, Import SMS ·
      Lending · Goals · Recurring), check-in strip, budget ring + top 3, spending-mix donut,
      2-column goal cards, Recent (4)
- [x] 8.4 Transactions: sort menu, pill search, All / Expenses / Income, category chips,
      count and totals line, day groups with totals, empty and no-match states
- [x] 8.5 New transaction as a bottom sheet: big amount, +₹100 / 500 / 1,000 / 2,000 chips,
      payee, date chips, note, wrapping category chips, pill save button
- [x] 8.6 Insights: savings rate, month-to-date trend with "on pace for" projection, last 7
      days bars with tooltip, where it went, budgets, top payees
- [x] 8.7 Settings (grouped rows, icon circles) and Badges (level card, How XP works,
      All / Earned / Locked, 3-column grid with locked state)
- [x] 8.8 Motion: bar growth, count-up, spring thumbs, self-drawing line, donut sweep, row
      cascade, shimmer / shine / pop, toast; all under 700 ms

## SMS import design

```
 [Scan: 7d | 14d | 30d | Since date]
              │
              ▼
  Read SMS inbox (READ_SMS), date ≥ start
  Keep alphanumeric bank senders only (e.g. JD-KOTAKB)
              │
              ▼
  BankSmsParser ──► keep Kotak / BOI results
              │
              ▼
  Dedupe key: ref:<bank>:<type>:<upiRef>  or  sms:<hash(sender|time|body)>
  Already in sms_imports? ──► skip (counted in summary)
              │
              ▼
  Preview grouped by payee (null payee = one row per message)
   • category pre-filled from merchant_rules, else "Other"
   • "Self transfer" toggle per group (auto-suggested when a Kotak debit
     and BOI credit share a UPI ref)
   • unticked by default: unverified credits, likely manual duplicates
              │
              ▼
  Import (one DB transaction):
   transactions  ← ticked, non-self-transfer items
   sms_imports   ← IMPORTED (with transactionId) / SELF_TRANSFER
   merchant_rules← category / self-transfer per payee
```

Unticked items are not recorded, so they appear again on the next scan.

### Live capture (1.5)

```
 Bank SMS arrives ──► BankSmsReceiver (RECEIVE_SMS, switch on in Settings)
              │
              ▼
  Same planner as the scan (parser, templates, dedupe, payee memory)
              │
              ▼
  AutoCapture.decide — added at once only when ALL hold:
   • payee has a remembered category (or self transfer)
   • it is a debit (credit formats are not verified yet)
   • it has a UPI ref (stable dedupe key; the fallback key uses arrival time)
   • not a likely duplicate of a hand-entered transaction
              │                                   │
        added (sms_imports + transactions)   waits for review (key kept in prefs)
              └──────────────┬────────────────────┘
                             ▼
     One updating notification ("2 bank payments to review · Latest: ₹45 · …")
     Tap / Home card ──► SMS review scanning from the oldest waiting SMS
```

No database change: waiting items are re-read from the inbox when reviewed. SMS that arrive
while the number is not registered with the bank cannot be captured or scanned.

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
