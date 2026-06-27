package com.expense.model;

public class Budget {
    private String category;
    private double limit;
    private String month; // format: "YYYY-MM"

    public Budget() {}

    public Budget(String category, double limit, String month) {
        this.category = category;
        this.limit = limit;
        this.month = month;
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public double getLimit() { return limit; }
    public void setLimit(double limit) { this.limit = limit; }
    public String getMonth() { return month; }
    public void setMonth(String month) { this.month = month; }

    @Override
    public String toString() {
        return category + "|" + limit + "|" + month;
    }

    public static Budget fromString(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 3) return null;
        Budget b = new Budget();
        b.category = parts[0];
        b.limit = Double.parseDouble(parts[1]);
        b.month = parts[2];
        return b;
    }
}
