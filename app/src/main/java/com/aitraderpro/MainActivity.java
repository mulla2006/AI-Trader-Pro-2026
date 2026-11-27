package com.aitraderpro;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final double START_BALANCE = 100.0;
    private static final double MAX_RISK = 0.005;
    private static final double MAX_LEVERAGE = 2.0;
    private SharedPreferences prefs;
    private TextView balanceView, riskView, positionView, resultView;
    private EditText entryInput, stopInput, targetInput, exitInput;
    private boolean hasPosition;
    private boolean isLong;
    private double balance, entry, stop, target, quantity, riskAmount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("paper_trading", MODE_PRIVATE);
        loadState();
        buildScreen();
        refreshScreen();
    }

    private void loadState() {
        balance = prefs.getFloat("balance", (float) START_BALANCE);
        hasPosition = prefs.getBoolean("has_position", false);
        isLong = prefs.getBoolean("is_long", true);
        entry = Double.longBitsToDouble(prefs.getLong("entry", Double.doubleToLongBits(0)));
        stop = Double.longBitsToDouble(prefs.getLong("stop", Double.doubleToLongBits(0)));
        target = Double.longBitsToDouble(prefs.getLong("target", Double.doubleToLongBits(0)));
        quantity = Double.longBitsToDouble(prefs.getLong("quantity", Double.doubleToLongBits(0)));
        riskAmount = Double.longBitsToDouble(prefs.getLong("risk", Double.doubleToLongBits(0)));
    }

    private void saveState() {
        prefs.edit()
            .putFloat("balance", (float) balance)
            .putBoolean("has_position", hasPosition)
            .putBoolean("is_long", isLong)
            .putLong("entry", Double.doubleToLongBits(entry))
            .putLong("stop", Double.doubleToLongBits(stop))
            .putLong("target", Double.doubleToLongBits(target))
            .putLong("quantity", Double.doubleToLongBits(quantity))
            .putLong("risk", Double.doubleToLongBits(riskAmount))
            .apply();
    }

    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        root.setBackgroundColor(Color.rgb(12, 18, 30));
        scroll.addView(root);

        TextView title = text("AI TRADER PRO", 27, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView subtitle = text("PAPER TRADING • SIMULATION ONLY", 13, Color.rgb(0, 220, 160), true);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        balanceView = text("", 23, Color.WHITE, true);
        riskView = text("", 15, Color.LTGRAY, false);
        addGap(root, 18);
        root.addView(balanceView);
        root.addView(riskView);
        addGap(root, 20);

        TextView section = text("OPEN A DEMO POSITION", 17, Color.WHITE, true);
        root.addView(section);
        entryInput = field("Entry price (example: 60000)");
        stopInput = field("Stop-loss price");
        targetInput = field("Take-profit price");
        root.addView(entryInput);
        root.addView(stopInput);
        root.addView(targetInput);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        Button longButton = button("OPEN LONG");
        Button shortButton = button("OPEN SHORT");
        buttons.addView(longButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        buttons.addView(shortButton, new LinearLayout.LayoutParams(0, dp(48), 1));
        root.addView(buttons);
        longButton.setOnClickListener(v -> openPosition(true));
        shortButton.setOnClickListener(v -> openPosition(false));

        addGap(root, 18);
        positionView = text("", 15, Color.WHITE, false);
        root.addView(positionView);
        exitInput = field("Exit price to close position");
        root.addView(exitInput);
        Button closeButton = button("CLOSE PAPER POSITION");
        root.addView(closeButton);
        closeButton.setOnClickListener(v -> closePosition());

        addGap(root, 16);
        resultView = text("", 14, Color.rgb(0, 220, 160), false);
        root.addView(resultView);

        Button resetButton = button("RESET DEMO ACCOUNT");
        root.addView(resetButton);
        resetButton.setOnClickListener(v -> resetAccount());

        addGap(root, 16);
        TextView note = text("Rules: $100 starting balance • maximum planned risk 0.5% per trade • maximum simulated leverage 2x. Enter prices manually. No live market feed, AI signals, exchange connection, fees or slippage are included yet. This screen cannot place real orders.", 12, Color.LTGRAY, false);
        root.addView(note);
        setContentView(scroll);
    }

    private void openPosition(boolean longSide) {
        if (hasPosition) { toast("Close the current paper position first."); return; }
        double e = read(entryInput), s = read(stopInput), t = read(targetInput);
        if (!valid(e) || !valid(s) || !valid(t)) { toast("Enter valid positive prices in all three fields."); return; }
        if (longSide ? !(s < e && t > e) : !(s > e && t < e)) {
            toast("For LONG: stop < entry < target. For SHORT: target < entry < stop.");
            return;
        }
        double distance = Math.abs(e - s);
        riskAmount = balance * MAX_RISK;
        double units = riskAmount / distance;
        double maxUnits = (balance * MAX_LEVERAGE) / e;
        quantity = Math.min(units, maxUnits);
        if (!valid(quantity) || quantity <= 0) { toast("Position size could not be calculated."); return; }
        entry = e; stop = s; target = t; isLong = longSide; hasPosition = true;
        saveState();
        resultView.setText("Paper position opened. Estimated stop risk: " + money(quantity * distance) +
                ". Simulated notional: " + money(quantity * entry) + ".");
        refreshScreen();
    }

    private void closePosition() {
        if (!hasPosition) { toast("There is no open paper position."); return; }
        double exit = read(exitInput);
        if (!valid(exit)) { toast("Enter a valid positive exit price."); return; }
        double pnl = (isLong ? exit - entry : entry - exit) * quantity;
        balance += pnl;
        hasPosition = false;
        saveState();
        resultView.setText("Position closed. Simulated P/L: " + (pnl >= 0 ? "+" : "") + money(pnl) +
                ". New demo balance: " + money(balance) + ". Fees and slippage are not included.");
        refreshScreen();
    }

    private void resetAccount() {
        balance = START_BALANCE; hasPosition = false; entry = stop = target = quantity = riskAmount = 0;
        saveState();
        resultView.setText("Demo account reset to $100. No real funds were used.");
        refreshScreen();
    }

    private void refreshScreen() {
        balanceView.setText("DEMO BALANCE: " + money(balance));
        riskView.setText("Risk cap: 0.5% (" + money(balance * MAX_RISK) + ")  •  Leverage cap: 2x");
        if (hasPosition) {
            positionView.setText("OPEN " + (isLong ? "LONG" : "SHORT") +
                "\nEntry: " + price(entry) + "   Stop: " + price(stop) +
                "\nTarget: " + price(target) + "\nSize: " + String.format(Locale.US, "%.6f", quantity) +
                "\nPlanned stop risk: " + money(quantity * Math.abs(entry - stop)));
        } else {
            positionView.setText("No open paper position.");
        }
    }

    private double read(EditText field) {
        try { return Double.parseDouble(field.getText().toString().trim()); }
        catch (Exception ex) { return Double.NaN; }
    }
    private boolean valid(double n) { return !Double.isNaN(n) && !Double.isInfinite(n) && n > 0; }
    private String money(double n) { return String.format(Locale.US, "$%.2f", n); }
    private String price(double n) { return String.format(Locale.US, "%.6f", n); }
    private int dp(int n) { return (int)(n * getResources().getDisplayMetrics().density + 0.5f); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        if (bold) v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setPadding(0, dp(7), 0, dp(7)); return v;
    }
    private EditText field(String hint) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setTextSize(15);
        e.setHint(hint); e.setHintTextColor(Color.GRAY); e.setTextColor(Color.WHITE);
        e.setInputType(8194); e.setPadding(dp(10), dp(5), dp(10), dp(5));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(52)); p.bottomMargin = dp(8);
        e.setLayoutParams(p); return e;
    }
    private Button button(String label) {
        Button b = new Button(this); b.setText(label); b.setTextSize(12); return b;
    }
    private void addGap(LinearLayout root, int height) {
        View gap = new View(this); root.addView(gap, new LinearLayout.LayoutParams(1, dp(height)));
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
