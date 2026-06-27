package com.expense.dao;

import com.expense.model.Budget;
import com.expense.model.Expense;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

public class ExpenseDAO {
    private static final String DATA_DIR = System.getProperty("user.home") + File.separator + ".expense_tracker_html";
    private static final String EXPENSES_FILE = DATA_DIR + File.separator + "expenses.dat";
    private static final String BUDGETS_FILE = DATA_DIR + File.separator + "budgets.dat";

    private List<Expense> expenses = new ArrayList<>();
    private List<Budget> budgets = new ArrayList<>();

    public static final String[] CATEGORIES = {
        "Food & Dining", "Transport", "Shopping", "Entertainment",
        "Health & Medical", "Housing & Rent", "Utilities", "Education",
        "Travel", "Personal Care", "Gifts & Donations", "Other"
    };

    public static final String[] PAYMENT_METHODS = {
        "Cash", "Credit Card", "Debit Card", "UPI / Net Banking", "Wallet"
    };

    public ExpenseDAO() {
        ensureDataDir();
        loadExpenses();
        loadBudgets();
    }

    private void ensureDataDir() {
        try {
            Files.createDirectories(Paths.get(DATA_DIR));
        } catch (IOException e) {
            System.err.println("Could not create data directory: " + e.getMessage());
        }
    }

    // ─── EXPENSE CRUD ────────────────────────────────────────────────────────

    public void addExpense(Expense expense) {
        expenses.add(expense);
        saveExpenses();
    }

    public void updateExpense(Expense updated) {
        for (int i = 0; i < expenses.size(); i++) {
            if (expenses.get(i).getId().equals(updated.getId())) {
                expenses.set(i, updated);
                break;
            }
        }
        saveExpenses();
    }

    public void deleteExpense(String id) {
        expenses.removeIf(e -> e.getId().equals(id));
        saveExpenses();
    }

    public List<Expense> getAllExpenses() {
        return new ArrayList<>(expenses);
    }

    public Expense getExpenseById(String id) {
        return expenses.stream().filter(e -> e.getId().equals(id)).findFirst().orElse(null);
    }

    public List<Expense> getExpensesByMonth(YearMonth month) {
        return expenses.stream()
            .filter(e -> YearMonth.from(e.getDate()).equals(month))
            .sorted(Comparator.comparing(Expense::getDate).reversed())
            .collect(Collectors.toList());
    }

    public List<Expense> getExpensesByDateRange(LocalDate from, LocalDate to) {
        return expenses.stream()
            .filter(e -> !e.getDate().isBefore(from) && !e.getDate().isAfter(to))
            .sorted(Comparator.comparing(Expense::getDate).reversed())
            .collect(Collectors.toList());
    }

    public List<Expense> getExpensesByCategory(String category) {
        return expenses.stream()
            .filter(e -> e.getCategory().equals(category))
            .sorted(Comparator.comparing(Expense::getDate).reversed())
            .collect(Collectors.toList());
    }

    public List<Expense> searchExpenses(String query) {
        String q = query.toLowerCase();
        return expenses.stream()
            .filter(e -> e.getTitle().toLowerCase().contains(q)
                || e.getCategory().toLowerCase().contains(q)
                || e.getNote().toLowerCase().contains(q))
            .sorted(Comparator.comparing(Expense::getDate).reversed())
            .collect(Collectors.toList());
    }

    // ─── ANALYTICS ───────────────────────────────────────────────────────────

    public double getTotalByMonth(YearMonth month) {
        return getExpensesByMonth(month).stream().mapToDouble(Expense::getAmount).sum();
    }

    public Map<String, Double> getCategoryTotals(YearMonth month) {
        Map<String, Double> map = new LinkedHashMap<>();
        for (String cat : CATEGORIES) map.put(cat, 0.0);
        getExpensesByMonth(month).forEach(e ->
            map.merge(e.getCategory(), e.getAmount(), Double::sum));
        return map;
    }

    public Map<String, Double> getCategoryTotalsAll() {
        Map<String, Double> map = new LinkedHashMap<>();
        for (String cat : CATEGORIES) map.put(cat, 0.0);
        expenses.forEach(e -> map.merge(e.getCategory(), e.getAmount(), Double::sum));
        return map;
    }

    public Map<String, Double> getMonthlyTotals(int numMonths) {
        Map<String, Double> map = new LinkedHashMap<>();
        YearMonth current = YearMonth.now();
        for (int i = numMonths - 1; i >= 0; i--) {
            YearMonth m = current.minusMonths(i);
            map.put(m.toString(), getTotalByMonth(m));
        }
        return map;
    }

    public Expense getLargestExpense(YearMonth month) {
        return getExpensesByMonth(month).stream()
            .max(Comparator.comparing(Expense::getAmount)).orElse(null);
    }

    public double getDailyAverage(YearMonth month) {
        List<Expense> list = getExpensesByMonth(month);
        if (list.isEmpty()) return 0;
        return list.stream().mapToDouble(Expense::getAmount).sum() / month.lengthOfMonth();
    }

    // ─── BUDGET ──────────────────────────────────────────────────────────────

    public void setBudget(Budget budget) {
        budgets.removeIf(b -> b.getCategory().equals(budget.getCategory())
            && b.getMonth().equals(budget.getMonth()));
        budgets.add(budget);
        saveBudgets();
    }

    public List<Budget> getBudgets(String month) {
        return budgets.stream()
            .filter(b -> b.getMonth().equals(month))
            .collect(Collectors.toList());
    }

    public double getBudgetLimit(String category, String month) {
        return budgets.stream()
            .filter(b -> b.getCategory().equals(category) && b.getMonth().equals(month))
            .mapToDouble(Budget::getLimit).findFirst().orElse(0.0);
    }

    // ─── FILE I/O ─────────────────────────────────────────────────────────────

    private void saveExpenses() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(EXPENSES_FILE))) {
            for (Expense e : expenses) {
                pw.println(e.toString());
            }
        } catch (IOException ex) {
            System.err.println("Error saving expenses: " + ex.getMessage());
        }
    }

    private void loadExpenses() {
        File f = new File(EXPENSES_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    Expense e = Expense.fromString(line);
                    if (e != null) expenses.add(e);
                }
            }
        } catch (IOException ex) {
            System.err.println("Error loading expenses: " + ex.getMessage());
        }
    }

    private void saveBudgets() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(BUDGETS_FILE))) {
            for (Budget b : budgets) {
                pw.println(b.toString());
            }
        } catch (IOException ex) {
            System.err.println("Error saving budgets: " + ex.getMessage());
        }
    }

    private void loadBudgets() {
        File f = new File(BUDGETS_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    Budget b = Budget.fromString(line);
                    if (b != null) budgets.add(b);
                }
            }
        } catch (IOException ex) {
            System.err.println("Error loading budgets: " + ex.getMessage());
        }
    }
}
