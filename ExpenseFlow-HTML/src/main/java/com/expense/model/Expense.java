package com.expense.model;

import java.time.LocalDate;
import java.util.UUID;

public class Expense {
    private String id;
    private String title;
    private double amount;
    private String category;
    private LocalDate date;
    private String note;
    private String paymentMethod;

    public Expense() {
        this.id = UUID.randomUUID().toString();
    }

    public Expense(String title, double amount, String category, LocalDate date, String note, String paymentMethod) {
        this.id = UUID.randomUUID().toString();
        this.title = title;
        this.amount = amount;
        this.category = category;
        this.date = date;
        this.note = note;
        this.paymentMethod = paymentMethod;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    @Override
    public String toString() {
        return id + "|" + title + "|" + amount + "|" + category + "|" + date.toString() + "|" + note + "|" + paymentMethod;
    }

    public static Expense fromString(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length < 7) return null;
        Expense e = new Expense();
        e.id = parts[0];
        e.title = parts[1];
        e.amount = Double.parseDouble(parts[2]);
        e.category = parts[3];
        e.date = LocalDate.parse(parts[4]);
        e.note = parts[5];
        e.paymentMethod = parts[6];
        return e;
    }
}
