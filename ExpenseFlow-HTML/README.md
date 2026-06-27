# ExpenseFlow (HTML/CSS Edition) — Personal Expense Tracker

A Java desktop app whose **entire frontend is built from real, separate
`.html` and `.css` files** — not Java-drawn UI. Java only supplies the data
and the logic; the look of every screen lives on disk and can be edited
without touching a single line of Java.

No Spring Boot. No Hibernate. No REST API. No external libraries.

---

## How the HTML/Java split actually works

```
   web/*.html  +  web/css/style.css        ←  pure markup & styling, on disk
        │
        ▼
  HtmlTemplate.java   loads the .html file, swaps UPPER_CASE
                      placeholders for live data from the DAO
        │
        ▼
  WebPanel.java       a JEditorPane that renders the final HTML/CSS
                      and turns clicks on <a href="app://..."> links
                      into real Java method calls
        │
        ▼
  MainFrame.java      the controller: routes link-clicks to actions
                      (navigate, edit a field, save, delete, set a
                      budget), updates app state, then re-renders
                      the current page's .html file with fresh data
```

Java's built-in HTML renderer (`JEditorPane` / `HTMLEditorKit`) doesn't run
JavaScript and only understands **CSS 2.1** — no `border-radius`, no
flexbox/grid, no shadows, no gradients. So the templates here are written as
old-school HTML tables for layout, and the stylesheet only uses properties
that actually render: colors, fonts, padding, borders, widths/heights. A
donut chart, for example, is technically impossible to draw with CSS alone in
this renderer, so the category breakdown is shown as a 100%-stacked
horizontal bar instead — same data, same colors, something the renderer can
actually draw.

Because there's no JavaScript engine, "interactivity" (typing into a field,
picking from a dropdown) is done by clicking a styled link that opens a
native input dialog, which then feeds the typed value back into the HTML on
the next render. It's not a real `<input>`/`<select>`, but it's a faithful,
honest way to let real HTML/CSS drive the UI without silently degrading to
something that looks broken.

## Features

- **Dashboard** — monthly total, today's spend, daily average, largest
  expense, a 6-month bar chart (table-based), category breakdown bars, and a
  recent-transactions feed.
- **Add / Edit Expense** — title, amount, date, category, payment method,
  optional note — each field is a clickable link that opens an input dialog.
  Validates input and shows inline errors in the page itself.
- **History** — live search, category filter, results grouped by month,
  inline Edit/Delete links per row with a confirmation dialog before delete.
- **Reports & Budgets** — category split as a stacked bar + percentage
  legend, and per-category monthly budgets with progress bars that turn red
  when you go over, plus an over-budget warning line.
- **Persistence** — plain pipe-delimited text files in
  `~/.expense_tracker_html/`. No database, no drivers, fully human-readable.

## Project Structure

```
ExpenseTracker2/
├── web/                           ← the actual frontend
│   ├── css/style.css              ← one shared external stylesheet
│   ├── sidebar.html                ← nav partial, included on every page
│   ├── dashboard.html
│   ├── add_expense.html
│   ├── history.html
│   ├── reports.html
│   └── set_budget.html            ← small dialog template
├── src/main/java/com/expense/
│   ├── App.java                   → main() entry point
│   ├── model/
│   │   ├── Expense.java
│   │   └── Budget.java
│   ├── dao/
│   │   └── ExpenseDAO.java        → all CRUD + analytics, file-backed
│   └── ui/
│       ├── MainFrame.java         → controller: renders pages, handles app:// links
│       ├── WebPanel.java          → JEditorPane wrapper + link interception
│       └── HtmlTemplate.java      → loads a .html file, fills in placeholders
├── run.sh / run.bat               → compile + run
├── package-jar.sh                 → builds ExpenseFlow.jar
└── README.md
```

## How to Run

### Requirements
Just a **JDK (11 or newer)**. Nothing else.
```bash
java -version
javac -version
```

### Option 1 — One-step script
```bash
chmod +x run.sh
./run.sh
```
Windows: double-click `run.bat`, or run it from a terminal.

### Option 2 — Manual
```bash
javac -d build -encoding UTF-8 $(find src -name "*.java")
java -cp build com.expense.App
```
Run this **from the project root** — `web/` is located relative to the
working directory at startup.

### Option 3 — Standalone JAR
```bash
chmod +x package-jar.sh
./package-jar.sh
java -jar ExpenseFlow.jar
```
**Important:** the `web/` folder must stay next to `ExpenseFlow.jar`. The
HTML/CSS is deliberately *not* bundled inside the jar — the whole point of
this build is that you can open `web/dashboard.html` or `web/css/style.css`
in any text editor, change a color or a layout, save, and relaunch the app
to see your change. Nothing to recompile, nothing to repackage.

## Customizing the look

Want to change the accent color, fonts, or spacing? Open
`web/css/style.css` — it's a normal stylesheet with comments explaining
which CSS properties the renderer actually supports. Want to rearrange a
page? Open the matching `web/*.html` file — the `UPPER_SNAKE_CASE` tokens
(like `STAT_THIS_MONTH` or `RECENT_EXPENSES_PLACEHOLDER`) are the only parts
Java touches; everything else is yours to rearrange freely.

## Where is my data stored?

```
~/.expense_tracker_html/expenses.dat
~/.expense_tracker_html/budgets.dat
```
(`%USERPROFILE%\.expense_tracker_html\` on Windows)

Plain pipe-delimited text, safe to open, inspect, or back up manually.
Deleting these files resets the app to a blank state.

## Honest limitations

This is real separate HTML/CSS, but it's rendered by Java's built-in,
decades-old HTML 3.2 / CSS 2.1 engine — not a browser. That means:
- No rounded corners, shadows, or gradients (flat rectangles only) — but
  `border-style: groove / ridge / double / outset` all render with real
  3D bevel effects, which is what gives cards and buttons their depth here
- No real `<input>`/`<select>` elements — fields are clickable links that
  open a native dialog instead
- No JavaScript — all interactivity is wired through Java via the
  custom `app://action?...` link scheme
- Layout uses HTML tables, the only reliable layout mechanism this
  renderer fully honors
- **`letter-spacing` and `text-transform` are silently ignored** — they
  parse without error but never change anything on screen. Section labels
  that look like `SPENDING BY CATEGORY` are typed in actual capital letters
  in the HTML/Java source, not styled into caps with CSS.
- **`<h2>` ignores some CSS properties that plain `<div>` honors** — that's
  why section headers use `<div class="sectionhead">` instead of `<h2>`.
- **Bold text at 10-12px renders parentheses as curly braces** in the
  default SansSerif bold fallback (e.g. "(YYYY-MM-DD)" can visually show as
  "{YYYY-MM-DD}"). It's a font-substitution quirk, not a CSS bug. Field
  labels avoid parentheses for this reason; this only matters for text you
  control, since rows showing user-entered titles/notes use 13px+ where the
  glyph renders correctly.

If you need a modern browser-grade look with real CSS3 and JS, that
requires JavaFX's `WebView` (a Chromium-based engine) or an Electron-style
embedded browser — both need extra runtime components beyond the plain JDK.
