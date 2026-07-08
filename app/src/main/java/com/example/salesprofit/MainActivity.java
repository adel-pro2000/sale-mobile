package com.example.salesprofit;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS = "sales_profit_prefs";
    private static final String SHIFTS_KEY = "shifts";
    private static final double SELLER_PERCENT = 0.60;

    private final Locale ruLocale = new Locale("ru", "RU");
    private final NumberFormat moneyFormat = NumberFormat.getCurrencyInstance(ruLocale);

    private SharedPreferences prefs;
    private JSONArray shifts;
    private JSONObject currentShift;

    private TextView shiftTitle;
    private TextView statusText;
    private TextView totalsText;
    private EditText nameInput;
    private EditText purchaseInput;
    private EditText saleInput;
    private EditText rentInput;
    private LinearLayout salesList;
    private LinearLayout journalList;
    private Button saveRentButton;
    private Button addButton;
    private Button closeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        loadData();
        ensureTodayShift();
        buildUi();
        render();
    }

    private void buildUi() {
        ScrollView scrollView = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        root.setBackgroundColor(Color.rgb(248, 250, 252));
        scrollView.addView(root);

        root.addView(label("Прибыль продаж", 26, true));

        shiftTitle = label("", 18, true);
        shiftTitle.setPadding(0, dp(14), 0, 0);
        root.addView(shiftTitle);

        statusText = label("", 15, false);
        root.addView(statusText);

        LinearLayout rentSection = section();
        rentSection.addView(label("Аренда за смену", 20, true));
        rentInput = input("Ежедневная аренда", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        rentSection.addView(rentInput);
        saveRentButton = button("Сохранить аренду", Color.rgb(37, 99, 235));
        saveRentButton.setOnClickListener(v -> saveRent());
        rentSection.addView(saveRentButton);
        root.addView(rentSection);

        LinearLayout form = section();
        form.addView(label("Новая продажа", 20, true));
        nameInput = input("Наименование", InputType.TYPE_CLASS_TEXT);
        purchaseInput = input("Закуп", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        saleInput = input("Цена продажи", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        form.addView(nameInput);
        form.addView(purchaseInput);
        form.addView(saleInput);

        addButton = button("Добавить продажу", Color.rgb(37, 99, 235));
        addButton.setOnClickListener(v -> addSale());
        form.addView(addButton);
        root.addView(form);

        LinearLayout totals = section();
        totals.addView(label("Итог смены", 20, true));
        totalsText = label("", 16, false);
        totals.addView(totalsText);
        closeButton = button("Закрыть смену", Color.rgb(22, 163, 74));
        closeButton.setOnClickListener(v -> closeShift());
        totals.addView(closeButton);
        root.addView(totals);

        LinearLayout currentSales = section();
        currentSales.addView(label("Продажи за смену", 20, true));
        salesList = new LinearLayout(this);
        salesList.setOrientation(LinearLayout.VERTICAL);
        currentSales.addView(salesList);
        root.addView(currentSales);

        LinearLayout journal = section();
        journal.addView(label("Журнал смен", 20, true));
        journalList = new LinearLayout(this);
        journalList.setOrientation(LinearLayout.VERTICAL);
        journal.addView(journalList);
        root.addView(journal);

        setContentView(scrollView);
    }

    private void saveRent() {
        if (currentShift.optBoolean("closed", false)) {
            show("Смена уже закрыта");
            return;
        }
        if (isRentSet(currentShift)) {
            show("Аренда уже сохранена для этой смены");
            return;
        }

        Double rent = parseMoney(rentInput);
        if (rent == null) {
            show("Введите ежедневную аренду");
            return;
        }

        try {
            currentShift.put("rent", rent);
            currentShift.put("rentSet", true);
            saveData();
            render();
            show("Аренда сохранена");
        } catch (JSONException e) {
            show("Не удалось сохранить аренду");
        }
    }

    private void addSale() {
        if (currentShift.optBoolean("closed", false)) {
            show("Смена уже закрыта");
            return;
        }
        if (!isRentSet(currentShift)) {
            show("Сначала сохраните аренду за смену");
            return;
        }

        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            show("Введите наименование");
            return;
        }

        Double purchase = parseMoney(purchaseInput);
        Double sale = parseMoney(saleInput);
        if (purchase == null || sale == null) {
            show("Введите закуп и цену продажи");
            return;
        }

        double profit = sale - purchase;
        JSONObject item = new JSONObject();
        try {
            item.put("name", name);
            item.put("purchase", purchase);
            item.put("sale", sale);
            item.put("profit", profit);
            item.put("time", new SimpleDateFormat("HH:mm", ruLocale).format(new Date()));
            currentShift.getJSONArray("sales").put(item);
            saveData();
            nameInput.setText("");
            purchaseInput.setText("");
            saleInput.setText("");
            render();
            show("Продажа добавлена");
        } catch (JSONException e) {
            show("Не удалось сохранить продажу");
        }
    }

    private void closeShift() {
        if (currentShift.optBoolean("closed", false)) {
            show("Смена уже закрыта");
            return;
        }
        if (!isRentSet(currentShift)) {
            show("Сначала сохраните аренду за смену");
            return;
        }

        try {
            double rent = currentShift.optDouble("rent", 0);
            Totals totals = calculateTotals(currentShift, rent);
            currentShift.put("grossProfit", totals.grossProfit);
            currentShift.put("netProfit", totals.netProfit);
            currentShift.put("sellerSalary", totals.sellerSalary);
            currentShift.put("closed", true);
            currentShift.put("closedAt", new SimpleDateFormat("dd.MM.yyyy HH:mm", ruLocale).format(new Date()));
            saveData();
            render();
            show("Смена закрыта");
        } catch (JSONException e) {
            show("Не удалось закрыть смену");
        }
    }

    private void render() {
        String date = currentShift.optString("date", today());
        boolean closed = currentShift.optBoolean("closed", false);
        boolean rentSet = isRentSet(currentShift);
        double rent = currentShift.optDouble("rent", 0);
        Totals totals = calculateTotals(currentShift, rent);

        shiftTitle.setText("Смена: " + date);
        statusText.setText(closed ? "Статус: закрыта" : "Статус: открыта");
        rentInput.setText(rentSet ? formatPlain(rent) : "");
        rentInput.setEnabled(!closed && !rentSet);
        saveRentButton.setEnabled(!closed && !rentSet);
        addButton.setEnabled(!closed && rentSet);
        closeButton.setEnabled(!closed && rentSet);

        String totalMessage = "Прибыль продаж: " + money(totals.grossProfit)
                + "\nАренда: " + (rentSet ? money(rent) : "не сохранена")
                + "\nИтог после аренды: " + money(totals.netProfit)
                + "\nЗарплата продавца 60%: " + money(totals.sellerSalary);
        if (closed) {
            totalMessage += "\nЗакрыта: " + currentShift.optString("closedAt", "");
        }
        totalsText.setText(totalMessage);

        renderSales();
        renderJournal();
    }

    private void renderSales() {
        salesList.removeAllViews();
        JSONArray sales = currentShift.optJSONArray("sales");
        if (sales == null || sales.length() == 0) {
            salesList.addView(muted("Продаж пока нет"));
            return;
        }

        for (int i = 0; i < sales.length(); i++) {
            JSONObject sale = sales.optJSONObject(i);
            if (sale == null) continue;
            salesList.addView(row(
                    sale.optString("time", "") + "  " + sale.optString("name", ""),
                    "Продажа: " + money(sale.optDouble("sale"))
                            + " | Закуп: " + money(sale.optDouble("purchase"))
                            + " | Прибыль: " + money(sale.optDouble("profit"))
            ));
        }
    }

    private void renderJournal() {
        journalList.removeAllViews();
        if (shifts.length() == 0) {
            journalList.addView(muted("Журнал пуст"));
            return;
        }

        for (int i = shifts.length() - 1; i >= 0; i--) {
            JSONObject shift = shifts.optJSONObject(i);
            if (shift == null) continue;
            double rent = isRentSet(shift) ? shift.optDouble("rent", 0) : 0;
            Totals totals = calculateTotals(shift, rent);
            String status = shift.optBoolean("closed", false) ? "закрыта" : "открыта";
            JSONArray sales = shift.optJSONArray("sales");
            int salesCount = sales == null ? 0 : sales.length();
            journalList.addView(row(
                    shift.optString("date") + " - " + status,
                    "Продаж: " + salesCount
                            + " | Прибыль: " + money(totals.grossProfit)
                            + " | Аренда: " + (isRentSet(shift) ? money(rent) : "не сохранена")
                            + " | Зарплата: " + money(totals.sellerSalary)
            ));
        }
    }

    private void loadData() {
        String raw = prefs.getString(SHIFTS_KEY, "[]");
        try {
            shifts = new JSONArray(raw);
        } catch (JSONException e) {
            shifts = new JSONArray();
        }
    }

    private void ensureTodayShift() {
        String today = today();
        for (int i = 0; i < shifts.length(); i++) {
            JSONObject shift = shifts.optJSONObject(i);
            if (shift != null && today.equals(shift.optString("date"))) {
                currentShift = shift;
                return;
            }
        }

        currentShift = new JSONObject();
        try {
            currentShift.put("date", today);
            currentShift.put("rent", 0);
            currentShift.put("rentSet", false);
            currentShift.put("closed", false);
            currentShift.put("sales", new JSONArray());
            shifts.put(currentShift);
            saveData();
        } catch (JSONException ignored) {
        }
    }

    private void saveData() {
        prefs.edit().putString(SHIFTS_KEY, shifts.toString()).apply();
    }

    private boolean isRentSet(JSONObject shift) {
        return shift.optBoolean("rentSet", shift.optBoolean("closed", false) || shift.optDouble("rent", 0) > 0);
    }

    private Totals calculateTotals(JSONObject shift, double rent) {
        JSONArray sales = shift.optJSONArray("sales");
        double gross = 0;
        if (sales != null) {
            for (int i = 0; i < sales.length(); i++) {
                JSONObject sale = sales.optJSONObject(i);
                if (sale != null) {
                    gross += sale.optDouble("profit", 0);
                }
            }
        }
        double net = gross - rent;
        return new Totals(gross, net, net * SELLER_PERCENT);
    }

    private Double parseMoney(EditText input) {
        String value = input.getText().toString().trim().replace(',', '.');
        if (value.isEmpty()) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String today() {
        return new SimpleDateFormat("dd.MM.yyyy", ruLocale).format(new Date());
    }

    private String money(double value) {
        return moneyFormat.format(value);
    }

    private String formatPlain(double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return String.valueOf(value);
    }

    private TextView label(String text, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sp);
        view.setTextColor(Color.rgb(15, 23, 42));
        view.setPadding(0, dp(6), 0, dp(6));
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        return view;
    }

    private TextView muted(String text) {
        TextView view = label(text, 15, false);
        view.setTextColor(Color.rgb(100, 116, 139));
        return view;
    }

    private EditText input(String hint, int inputType) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setTextSize(16);
        editText.setSingleLine(true);
        editText.setInputType(inputType);
        editText.setPadding(dp(12), dp(8), dp(12), dp(8));
        editText.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(8), 0, dp(8));
        editText.setLayoutParams(params);
        return editText;
    }

    private Button button(String text, int color) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setBackgroundColor(color);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(10), 0, 0);
        button.setLayoutParams(params);
        return button;
    }

    private LinearLayout section() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(14), dp(12), dp(14), dp(14));
        layout.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dp(14), 0, 0);
        layout.setLayoutParams(params);
        return layout;
    }

    private View row(String title, String subtitle) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(0, dp(8), 0, dp(8));

        TextView titleView = label(title, 16, true);
        TextView subtitleView = label(subtitle, 14, false);
        subtitleView.setTextColor(Color.rgb(71, 85, 105));
        layout.addView(titleView);
        layout.addView(subtitleView);
        return layout;
    }

    private void show(String text) {
        Toast toast = Toast.makeText(this, text, Toast.LENGTH_SHORT);
        toast.setGravity(Gravity.BOTTOM, 0, dp(16));
        toast.show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class Totals {
        final double grossProfit;
        final double netProfit;
        final double sellerSalary;

        Totals(double grossProfit, double netProfit, double sellerSalary) {
            this.grossProfit = grossProfit;
            this.netProfit = netProfit;
            this.sellerSalary = sellerSalary;
        }
    }
}
