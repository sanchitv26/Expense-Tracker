package com.expense.ui;

import com.expense.dao.ExpenseDAO;
import com.expense.model.Budget;
import com.expense.model.Expense;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.List;

public class MainFrame extends JFrame {

    private final ExpenseDAO dao = new ExpenseDAO();
    private final Path webDir;
    private final String cssHref;

    private final WebPanel webPanel;
    private String currentPage = "dashboard";

    // ---- transient state for the Add/Edit form ----
    private String editingId = null;
    private String fTitle = "";
    private String fAmount = "";
    private String fDate = LocalDate.now().toString();
    private String fCategory = ExpenseDAO.CATEGORIES[0];
    private String fPayment = ExpenseDAO.PAYMENT_METHODS[0];
    private String fNote = "";
    private String fError = "";

    // ---- transient state for History filters ----
    private String searchQuery = "";
    private String categoryFilter = "All Categories";

    // ---- transient state for the Set Budget dialog ----
    private String bCategory = ExpenseDAO.CATEGORIES[0];
    private String bLimit = "";
    private String bError = "";
    private JDialog budgetDialog;
    private WebPanel budgetWebPanel;

    public MainFrame() {
        super("ExpenseFlow \u2014 Personal Expense Tracker (HTML/CSS UI)");

        this.webDir = locateWebDir();
        this.cssHref = new File(webDir.toFile(), "css/style.css").toURI().toString();

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1024, 680));
        setSize(1280, 820);
        setLocationRelativeTo(null);
        getContentPane().setBackground(new Color(0x0F1624));

        webPanel = new WebPanel(this::handleAppLink);
        JScrollPane scroll = new JScrollPane(webPanel);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        setContentPane(scroll);

        renderCurrentPage();
    }

    /** Finds the web/ folder whether running from source tree or from a packaged jar's sibling folder. */
    private Path locateWebDir() {
        File candidate = new File("web");
        if (candidate.exists()) return candidate.toPath();
        File fallback = new File(System.getProperty("user.dir"), "web");
        return fallback.toPath();
    }

    private Path webFile(String name) {
        return new File(webDir.toFile(), name).toPath();
    }

    // =========================================================================================
    //  LINK ROUTING — every <a href="app://..."> click in any rendered page arrives here.
    // =========================================================================================
    private void handleAppLink(String href) {
        try {
            URIParts parts = URIParts.parse(href);
            switch (parts.action) {
                case "nav" -> {
                    String to = parts.params.getOrDefault("to", "dashboard");
                    if (to.equals("add")) resetForm();
                    currentPage = to;
                    renderCurrentPage();
                }
                case "edit" -> {
                    String field = parts.params.get("field");
                    promptFieldEdit(field);
                }
                case "save" -> {
                    saveExpenseForm();
                }
                case "editrow" -> {
                    String id = parts.params.get("id");
                    Expense ex = dao.getExpenseById(id);
                    if (ex != null) {
                        loadFormFor(ex);
                        currentPage = "add";
                        renderCurrentPage();
                    }
                }
                case "delrow" -> {
                    String id = parts.params.get("id");
                    confirmAndDelete(id);
                }
                case "setbudget" -> {
                    openBudgetDialog();
                }
                default -> { /* ignore unknown actions */ }
            }
        } catch (Exception ex) {
            fError = "Something went wrong: " + ex.getMessage();
            renderCurrentPage();
        }
    }

    private void promptFieldEdit(String field) {
        if (field == null) return;
        switch (field) {
            case "title" -> {
                String v = askText("Title", "Enter a title for this expense:", fTitle);
                if (v != null) fTitle = v;
            }
            case "amount" -> {
                String v = askText("Amount", "Enter amount (e.g. 250.50):", fAmount);
                if (v != null) fAmount = v;
            }
            case "date" -> {
                String v = askText("Date", "Enter date as YYYY-MM-DD:", fDate);
                if (v != null) fDate = v;
            }
            case "note" -> {
                String v = askText("Note", "Optional note for this expense:", fNote);
                if (v != null) fNote = v;
            }
            case "category" -> {
                String v = askChoice("Category", "Choose a category:", ExpenseDAO.CATEGORIES, fCategory);
                if (v != null) fCategory = v;
            }
            case "payment" -> {
                String v = askChoice("Payment Method", "Choose a payment method:", ExpenseDAO.PAYMENT_METHODS, fPayment);
                if (v != null) fPayment = v;
            }
            case "search" -> {
                String v = askText("Search", "Search by title, category, or note:", searchQuery);
                if (v != null) searchQuery = v;
            }
            case "catfilter" -> {
                String[] options = new String[ExpenseDAO.CATEGORIES.length + 1];
                options[0] = "All Categories";
                System.arraycopy(ExpenseDAO.CATEGORIES, 0, options, 1, ExpenseDAO.CATEGORIES.length);
                String v = askChoice("Category Filter", "Filter by category:", options, categoryFilter);
                if (v != null) categoryFilter = v;
            }
        }
        renderCurrentPage();
    }

    private String askText(String title, String message, String initial) {
        return (String) JOptionPane.showInputDialog(
            this, message, title, JOptionPane.PLAIN_MESSAGE, null, null, initial);
    }

    private String askChoice(String title, String message, String[] options, String initial) {
        Object result = JOptionPane.showInputDialog(
            this, message, title, JOptionPane.PLAIN_MESSAGE, null, options, initial);
        return result != null ? result.toString() : null;
    }

    private void saveExpenseForm() {
        String title = fTitle.trim();
        if (title.isEmpty()) {
            fError = "Please enter a title for this expense.";
            renderCurrentPage();
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(fAmount.trim());
            if (amount <= 0) throw new NumberFormatException();
        } catch (NumberFormatException ex) {
            fError = "Please enter a valid amount greater than zero.";
            renderCurrentPage();
            return;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(fDate.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ex) {
            fError = "Please enter the date as YYYY-MM-DD.";
            renderCurrentPage();
            return;
        }

        if (editingId != null) {
            Expense existing = dao.getExpenseById(editingId);
            if (existing != null) {
                existing.setTitle(title);
                existing.setAmount(amount);
                existing.setCategory(fCategory);
                existing.setDate(date);
                existing.setNote(fNote.trim());
                existing.setPaymentMethod(fPayment);
                dao.updateExpense(existing);
            }
        } else {
            dao.addExpense(new Expense(title, amount, fCategory, date, fNote.trim(), fPayment));
        }

        resetForm();
        currentPage = "dashboard";
        renderCurrentPage();
    }

    private void resetForm() {
        editingId = null;
        fTitle = "";
        fAmount = "";
        fDate = LocalDate.now().toString();
        fCategory = ExpenseDAO.CATEGORIES[0];
        fPayment = ExpenseDAO.PAYMENT_METHODS[0];
        fNote = "";
        fError = "";
    }

    private void loadFormFor(Expense e) {
        editingId = e.getId();
        fTitle = e.getTitle();
        fAmount = String.valueOf(e.getAmount());
        fDate = e.getDate().toString();
        fCategory = e.getCategory();
        fPayment = e.getPaymentMethod();
        fNote = e.getNote() == null ? "" : e.getNote();
        fError = "";
    }

    private void confirmAndDelete(String id) {
        Expense e = dao.getExpenseById(id);
        if (e == null) return;
        int result = JOptionPane.showConfirmDialog(
            this,
            "Delete \"" + e.getTitle() + "\" (\u20B9" + String.format("%.2f", e.getAmount()) + ")?\nThis cannot be undone.",
            "Confirm Delete",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (result == JOptionPane.YES_OPTION) {
            dao.deleteExpense(id);
            renderCurrentPage();
        }
    }

    // =========================================================================================
    //  BUDGET DIALOG (separate small HTML view in a JDialog)
    // =========================================================================================
    private void openBudgetDialog() {
        bCategory = ExpenseDAO.CATEGORIES[0];
        bLimit = "";
        bError = "";

        budgetDialog = new JDialog(this, "Set Monthly Budget", true);
        budgetDialog.setSize(420, 320);
        budgetDialog.setLocationRelativeTo(this);

        budgetWebPanel = new WebPanel(this::handleBudgetLink);
        budgetDialog.add(new JScrollPane(budgetWebPanel));
        renderBudgetDialog();
        budgetDialog.setVisible(true);
    }

    private void handleBudgetLink(String href) {
        URIParts parts = URIParts.parse(href);
        switch (parts.action) {
            case "budgetedit" -> {
                String field = parts.params.get("field");
                if ("category".equals(field)) {
                    String v = askChoice("Category", "Choose a category:", ExpenseDAO.CATEGORIES, bCategory);
                    if (v != null) bCategory = v;
                } else if ("limit".equals(field)) {
                    String v = askText("Monthly Limit", "Enter the monthly limit in Rs.:", bLimit);
                    if (v != null) bLimit = v;
                }
                renderBudgetDialog();
            }
            case "budgetsave" -> {
                try {
                    double limit = Double.parseDouble(bLimit.trim());
                    if (limit <= 0) throw new NumberFormatException();
                    dao.setBudget(new Budget(bCategory, limit, YearMonth.now().toString()));
                    budgetDialog.dispose();
                    renderCurrentPage();
                } catch (NumberFormatException ex) {
                    bError = "Please enter a valid positive number.";
                    renderBudgetDialog();
                }
            }
            case "budgetcancel" -> budgetDialog.dispose();
            default -> { }
        }
    }

    private void renderBudgetDialog() {
        HtmlTemplate tpl = HtmlTemplate.load(webFile("set_budget.html"));
        Map<String, String> values = new LinkedHashMap<>();
        values.put("STYLE_CSS_PATH", cssHref);
        values.put("BUDGET_CATEGORY", HtmlTemplate.esc(bCategory));
        values.put("BUDGET_LIMIT", bLimit.isEmpty() ? "e.g. 5000" : HtmlTemplate.esc(bLimit));
        values.put("BUDGET_ERROR", HtmlTemplate.esc(bError));
        budgetWebPanel.setHtml(tpl.render(values));
    }

    // =========================================================================================
    //  PAGE RENDERING
    // =========================================================================================
    private void renderCurrentPage() {
        String html;
        switch (currentPage) {
            case "add" -> html = renderAddExpense();
            case "history" -> html = renderHistory();
            case "reports" -> html = renderReports();
            default -> html = renderDashboard();
        }
        webPanel.setHtml(html);
        setTitle("ExpenseFlow \u2014 " + capitalize(currentPage));
    }

    private String capitalize(String s) {
        if (s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    private String buildSidebar() {
        HtmlTemplate tpl = HtmlTemplate.load(webFile("sidebar.html"));
        Map<String, String> v = new LinkedHashMap<>();
        v.put("NAV_DASHBOARD_CLASS", currentPage.equals("dashboard") ? "navitem-active" : "navitem");
        v.put("NAV_ADD_CLASS", currentPage.equals("add") ? "navitem-active" : "navitem");
        v.put("NAV_HISTORY_CLASS", currentPage.equals("history") ? "navitem-active" : "navitem");
        v.put("NAV_REPORTS_CLASS", currentPage.equals("reports") ? "navitem-active" : "navitem");
        return tpl.render(v);
    }

    private String dotClass(String category) {
        int idx = Arrays.asList(ExpenseDAO.CATEGORIES).indexOf(category);
        String[] classes = {
            "dot-food", "dot-transport", "dot-shopping", "dot-entertain",
            "dot-health", "dot-housing", "dot-utilities", "dot-education",
            "dot-travel", "dot-personal", "dot-gifts", "dot-other"
        };
        return idx >= 0 ? classes[idx % classes.length] : "dot-other";
    }

    // ---------------------------------------------------------------------------------------
    //  DASHBOARD
    // ---------------------------------------------------------------------------------------
    private String renderDashboard() {
        HtmlTemplate tpl = HtmlTemplate.load(webFile("dashboard.html"));
        YearMonth now = YearMonth.now();

        double thisMonth = dao.getTotalByMonth(now);
        double lastMonth = dao.getTotalByMonth(now.minusMonths(1));
        double todayTotal = dao.getAllExpenses().stream()
            .filter(e -> e.getDate().equals(LocalDate.now()))
            .mapToDouble(Expense::getAmount).sum();
        double dailyAvg = dao.getDailyAverage(now);
        Expense largest = dao.getLargestExpense(now);

        double diff = thisMonth - lastMonth;
        String diffStr = (diff >= 0 ? "\u25B2 " : "\u25BC ") + String.format("\u20B9%.0f vs last month", Math.abs(diff));

        Map<String, String> v = new LinkedHashMap<>();
        v.put("STYLE_CSS_PATH", cssHref);
        v.put("SIDEBAR_PLACEHOLDER", buildSidebar());
        v.put("DATE_TODAY", LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy")));
        v.put("STAT_THIS_MONTH", String.format("\u20B9%.0f", thisMonth));
        v.put("STAT_MONTH_DIFF", diffStr);
        v.put("STAT_TODAY", String.format("\u20B9%.0f", todayTotal));
        v.put("STAT_DAILY_AVG", String.format("\u20B9%.0f", dailyAvg));
        v.put("STAT_LARGEST", largest != null ? String.format("\u20B9%.0f", largest.getAmount()) : "\u20B90");
        v.put("STAT_LARGEST_TITLE", largest != null ? HtmlTemplate.esc(largest.getTitle()) : "No expenses");
        v.put("CHART_TABLE_PLACEHOLDER", buildBarChartTable());
        v.put("CATEGORY_BREAKDOWN_PLACEHOLDER", buildCategoryBreakdown());
        v.put("RECENT_EXPENSES_PLACEHOLDER", buildRecentExpensesTable());

        return tpl.render(v);
    }

    private String buildBarChartTable() {
        Map<String, Double> monthly = dao.getMonthlyTotals(6);
        double max = monthly.values().stream().mapToDouble(d -> d).max().orElse(1);
        if (max == 0) max = 1;

        StringBuilder sb = new StringBuilder();
        sb.append("<table class=\"chart\" width=\"100%\" cellpadding=\"4\" cellspacing=\"0\" border=\"0\"><tr>");
        for (Map.Entry<String, Double> entry : monthly.entrySet()) {
            YearMonth ym = YearMonth.parse(entry.getKey());
            double val = entry.getValue();
            int barHeightPx = (int) Math.round((val / max) * 130);
            if (barHeightPx < 2 && val > 0) barHeightPx = 2;
            int spacerHeight = 134 - barHeightPx;

            sb.append("<td class=\"chart-col\">");
            sb.append("<div class=\"chart-value\">").append(val > 0 ? String.format("\u20B9%.0f", val) : "").append("</div>");
            sb.append("<table width=\"70%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" align=\"center\"><tr><td height=\"").append(Math.max(spacerHeight, 0)).append("\"></td></tr>");
            if (val > 0) {
                sb.append("<tr><td class=\"chart-bar\" height=\"").append(Math.max(barHeightPx, 1)).append("\"></td></tr></table>");
            } else {
                sb.append("<tr><td height=\"1\"></td></tr></table>");
            }
            sb.append("<div class=\"chart-label\">").append(ym.getMonth().name().substring(0, 3)).append("</div>");
            sb.append("</td>");
        }
        sb.append("</tr></table>");
        return sb.toString();
    }

    private String buildCategoryBreakdown() {
        Map<String, Double> cats = dao.getCategoryTotals(YearMonth.now());
        double total = cats.values().stream().mapToDouble(d -> d).sum();

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(cats.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        StringBuilder sb = new StringBuilder();
        sb.append("<table width=\"100%\" cellpadding=\"3\" cellspacing=\"0\" border=\"0\">");
        int shown = 0;
        for (Map.Entry<String, Double> e : sorted) {
            if (e.getValue() <= 0 || shown >= 6) continue;
            double pct = total > 0 ? e.getValue() / total * 100 : 0;
            sb.append("<tr><td class=\"row-meta\">").append(HtmlTemplate.esc(e.getKey())).append("</td>")
              .append("<td align=\"right\" class=\"row-title\">")
              .append(String.format("\u20B9%.0f", e.getValue())).append("</td>")
              .append("</tr>");
            sb.append("<tr><td colspan=\"2\">")
              .append(buildProgressBar(pct, false))
              .append("</td></tr>");
            sb.append("<tr><td colspan=\"2\" height=\"6\"></td></tr>");
            shown++;
        }
        if (shown == 0) {
            sb.append("<tr><td class=\"empty-text\">No expenses this month</td></tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    private String buildProgressBar(double pct, boolean danger) {
        pct = Math.max(0, Math.min(100, pct));
        String fillClass = danger ? "barfilldanger" : "barfill";
        return "<table class=\"barbg\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">"
            + "<tr><td class=\"" + fillClass + "\" width=\"" + (int) pct + "%\"></td>"
            + "<td></td></tr></table>";
    }

    private String buildRecentExpensesTable() {
        List<Expense> recent = dao.getAllExpenses();
        recent.sort(Comparator.comparing(Expense::getDate).reversed());
        recent = recent.subList(0, Math.min(5, recent.size()));
        return buildExpenseRowsTable(recent, false);
    }

    private String buildExpenseRowsTable(List<Expense> list, boolean showActions) {
        if (list.isEmpty()) {
            return "<p class=\"empty-text\">No expenses yet. Add your first one!</p>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<table class=\"rowlist\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">");
        sb.append(buildExpenseRowsBody(list, showActions));
        sb.append("</table>");
        return sb.toString();
    }

    /** Builds just the <tr> rows (no wrapping <table>) so callers can interleave month-header
     *  rows into one single table and keep column widths consistent across the whole list. */
    private String buildExpenseRowsBody(List<Expense> list, boolean showActions) {
        StringBuilder sb = new StringBuilder();
        for (Expense e : list) {
            sb.append("<tr class=\"expense-row\">");
            sb.append("<td width=\"18\" valign=\"top\"><table cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr><td class=\"dot ")
              .append(dotClass(e.getCategory())).append("\" width=\"12\" height=\"12\"></td></tr></table></td>");
            sb.append("<td valign=\"top\">")
              .append("<div class=\"row-title\">").append(HtmlTemplate.esc(e.getTitle())).append("</div>")
              .append("<div class=\"row-meta\">").append(HtmlTemplate.esc(e.getCategory()))
              .append(" &middot; ").append(e.getDate())
              .append(showActions ? (" &middot; " + HtmlTemplate.esc(e.getPaymentMethod())) : "")
              .append("</div>");
            if (showActions && e.getNote() != null && !e.getNote().isEmpty()) {
                sb.append("<div class=\"row-note\">&ldquo;").append(HtmlTemplate.esc(e.getNote())).append("&rdquo;</div>");
            }
            sb.append("</td>");
            sb.append("<td align=\"right\" valign=\"top\" width=\"100\"><span class=\"row-amount\">")
              .append(String.format("\u20B9%.2f", e.getAmount())).append("</span></td>");
            if (showActions) {
                sb.append("<td align=\"right\" valign=\"top\" width=\"130\" nowrap>")
                  .append("<a class=\"btn-info btn-small\" href=\"app://editrow?id=").append(e.getId()).append("\">Edit</a>")
                  .append("&nbsp;")
                  .append("<a class=\"btn-danger btn-small\" href=\"app://delrow?id=").append(e.getId()).append("\">Del</a>")
                  .append("</td>");
            }
            sb.append("</tr>");
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------------------------
    //  ADD / EDIT EXPENSE
    // ---------------------------------------------------------------------------------------
    private String renderAddExpense() {
        HtmlTemplate tpl = HtmlTemplate.load(webFile("add_expense.html"));
        Map<String, String> v = new LinkedHashMap<>();
        v.put("STYLE_CSS_PATH", cssHref);
        v.put("SIDEBAR_PLACEHOLDER", buildSidebar());
        v.put("FORM_TITLE", editingId != null ? "Edit Expense" : "Add Expense");
        v.put("SAVE_BUTTON_LABEL", editingId != null ? "Update Expense" : "Save Expense");
        v.put("FIELD_TITLE", fTitle.isEmpty() ? "Click to enter a title..." : HtmlTemplate.esc(fTitle));
        v.put("FIELD_AMOUNT", fAmount.isEmpty() ? "Click to enter amount..." : HtmlTemplate.esc(fAmount));
        v.put("FIELD_DATE", HtmlTemplate.esc(fDate));
        v.put("FIELD_CATEGORY", HtmlTemplate.esc(fCategory));
        v.put("FIELD_PAYMENT", HtmlTemplate.esc(fPayment));
        v.put("FIELD_NOTE", fNote.isEmpty() ? "Click to add a note..." : HtmlTemplate.esc(fNote));
        v.put("ERROR_MESSAGE", HtmlTemplate.esc(fError));
        return tpl.render(v);
    }

    // ---------------------------------------------------------------------------------------
    //  HISTORY
    // ---------------------------------------------------------------------------------------
    private String renderHistory() {
        HtmlTemplate tpl = HtmlTemplate.load(webFile("history.html"));

        List<Expense> results;
        if (!searchQuery.isEmpty()) {
            results = dao.searchExpenses(searchQuery);
        } else {
            results = dao.getAllExpenses();
            results.sort(Comparator.comparing(Expense::getDate).reversed());
        }
        if (!categoryFilter.equals("All Categories")) {
            results = new ArrayList<>(results);
            results.removeIf(e -> !e.getCategory().equals(categoryFilter));
        }

        double total = results.stream().mapToDouble(Expense::getAmount).sum();
        String summary = results.size() + " transaction" + (results.size() == 1 ? "" : "s")
            + String.format("  &middot;  Total: \u20B9%.2f", total);

        Map<String, String> v = new LinkedHashMap<>();
        v.put("STYLE_CSS_PATH", cssHref);
        v.put("SIDEBAR_PLACEHOLDER", buildSidebar());
        v.put("SEARCH_AND_FILTER_BAR", "Find any transaction in seconds.");
        v.put("SEARCH_QUERY_DISPLAY", searchQuery.isEmpty() ? "Click to search..." : HtmlTemplate.esc(searchQuery));
        v.put("CATEGORY_FILTER_DISPLAY", HtmlTemplate.esc(categoryFilter));
        v.put("RESULTS_SUMMARY", summary);
        v.put("EXPENSE_ROWS_PLACEHOLDER", buildGroupedHistoryRows(results));
        return tpl.render(v);
    }

    private String buildGroupedHistoryRows(List<Expense> results) {
        if (results.isEmpty()) {
            return "<tr><td colspan=\"4\" class=\"empty-text\">No matching transactions found.</td></tr>";
        }
        StringBuilder sb = new StringBuilder();
        String lastMonth = "";
        List<Expense> monthBucket = new ArrayList<>();
        for (Expense e : results) {
            String monthKey = e.getDate().getMonth().name() + " " + e.getDate().getYear();
            if (!monthKey.equals(lastMonth)) {
                if (!monthBucket.isEmpty()) {
                    sb.append(buildExpenseRowsBody(monthBucket, true));
                    monthBucket.clear();
                }
                sb.append("<tr><td colspan=\"4\" class=\"month-header\" style=\"padding-top:14px;padding-bottom:6px;\">")
                  .append(monthKey).append("</td></tr>");
                lastMonth = monthKey;
            }
            monthBucket.add(e);
        }
        if (!monthBucket.isEmpty()) {
            sb.append(buildExpenseRowsBody(monthBucket, true));
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------------------------
    //  REPORTS
    // ---------------------------------------------------------------------------------------
    private String renderReports() {
        HtmlTemplate tpl = HtmlTemplate.load(webFile("reports.html"));
        Map<String, String> v = new LinkedHashMap<>();
        v.put("STYLE_CSS_PATH", cssHref);
        v.put("SIDEBAR_PLACEHOLDER", buildSidebar());
        v.put("CURRENT_MONTH", YearMonth.now().toString());
        v.put("PIE_TABLE_PLACEHOLDER", buildPieAsBars());
        v.put("LEGEND_PLACEHOLDER", buildLegend());
        v.put("BUDGETS_PLACEHOLDER", buildBudgetsList());
        return tpl.render(v);
    }

    /** CSS2 tables can't draw a real pie/donut, so we represent the category split as a
     *  horizontal 100%-stacked bar — same data, same colors, an honest substitute that the
     *  renderer can actually draw. */
    private String buildPieAsBars() {
        Map<String, Double> cats = dao.getCategoryTotals(YearMonth.now());
        double total = cats.values().stream().mapToDouble(d -> d).sum();

        List<Map.Entry<String, Double>> sorted = new ArrayList<>(cats.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"stat-value\" style=\"text-align:center;\">")
          .append(String.format("\u20B9%.0f", total)).append("</div>");
        sb.append("<div class=\"stat-label\" style=\"text-align:center;\">this month</div><br>");

        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>");
        if (total <= 0) {
            sb.append("<td class=\"barbg\" height=\"22\"></td>");
        } else {
            for (Map.Entry<String, Double> e : sorted) {
                if (e.getValue() <= 0) continue;
                int pct = (int) Math.round(e.getValue() / total * 100);
                if (pct <= 0) continue;
                String cls = dotClass(e.getKey());
                sb.append("<td class=\"").append(cls).append("\" width=\"").append(pct).append("%\" height=\"22\"></td>");
            }
        }
        sb.append("</tr></table>");
        return sb.toString();
    }

    private String buildLegend() {
        Map<String, Double> cats = dao.getCategoryTotals(YearMonth.now());
        double total = cats.values().stream().mapToDouble(d -> d).sum();
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(cats.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        StringBuilder sb = new StringBuilder();
        sb.append("<table width=\"100%\" cellpadding=\"4\" cellspacing=\"0\" border=\"0\">");
        boolean any = false;
        for (Map.Entry<String, Double> e : sorted) {
            if (e.getValue() <= 0) continue;
            any = true;
            double pct = total > 0 ? e.getValue() / total * 100 : 0;
            sb.append("<tr><td width=\"14\"><table cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr><td class=\"dot ")
              .append(dotClass(e.getKey())).append("\" width=\"10\" height=\"10\"></td></tr></table></td>");
            sb.append("<td class=\"row-meta\">").append(HtmlTemplate.esc(e.getKey())).append("</td>");
            sb.append("<td align=\"right\" class=\"row-title\">")
              .append(String.format("%.1f%%  (\u20B9%.0f)", pct, e.getValue())).append("</td></tr>");
        }
        if (!any) {
            sb.append("<tr><td class=\"empty-text\">No expenses recorded this month.</td></tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    private String buildBudgetsList() {
        String currentMonth = YearMonth.now().toString();
        List<Budget> budgets = dao.getBudgets(currentMonth);
        Map<String, Double> spent = dao.getCategoryTotals(YearMonth.now());

        if (budgets.isEmpty()) {
            return "<p class=\"empty-text\">No budgets set for this month. Click \"Set Budget\" to start tracking limits per category.</p>";
        }

        budgets.sort(Comparator.comparing(Budget::getCategory));
        StringBuilder sb = new StringBuilder();
        sb.append("<table width=\"100%\" cellpadding=\"4\" cellspacing=\"0\" border=\"0\">");
        for (Budget b : budgets) {
            double used = spent.getOrDefault(b.getCategory(), 0.0);
            double pct = b.getLimit() > 0 ? Math.min(used / b.getLimit() * 100, 100) : 0;
            boolean over = used > b.getLimit();

            sb.append("<tr><td style=\"padding-top:10px;\" class=\"row-title\">").append(HtmlTemplate.esc(b.getCategory())).append("</td>")
              .append("<td align=\"right\" style=\"padding-top:10px;\" class=\"").append(over ? "sub-red" : "row-meta").append("\">")
              .append(String.format("\u20B9%.0f / \u20B9%.0f", used, b.getLimit())).append("</td>")
              .append("</tr>");
            sb.append("<tr><td colspan=\"2\">").append(buildProgressBar(pct, over)).append("</td></tr>");
            if (over) {
                sb.append("<tr><td colspan=\"2\"><span class=\"sub-red\">&#9888; Over budget by ")
                  .append(String.format("\u20B9%.0f", used - b.getLimit())).append("</span></td></tr>");
            }
            sb.append("<tr><td colspan=\"2\"><hr class=\"sep\"></td></tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }

    // =========================================================================================
    //  Tiny app://action?key=value&key2=value2 parser
    // =========================================================================================
    private static class URIParts {
        String action;
        Map<String, String> params = new HashMap<>();

        static URIParts parse(String href) {
            URIParts p = new URIParts();
            String rest = href.substring("app://".length());
            int q = rest.indexOf('?');
            p.action = q >= 0 ? rest.substring(0, q) : rest;
            if (q >= 0) {
                String query = rest.substring(q + 1);
                for (String pair : query.split("&")) {
                    int eq = pair.indexOf('=');
                    if (eq > 0) {
                        String k = pair.substring(0, eq);
                        String val = pair.substring(eq + 1);
                        try {
                            val = URLDecoder.decode(val, StandardCharsets.UTF_8);
                        } catch (Exception ignored) { }
                        p.params.put(k, val);
                    }
                }
            }
            return p;
        }
    }
}
