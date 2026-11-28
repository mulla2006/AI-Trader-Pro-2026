package com.aitraderpro;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final double START_BALANCE = 100.0;
    private static final double MAX_RISK = 0.005;
    private static final double MAX_LEVERAGE = 2.0;
    private SharedPreferences prefs;
    private TextView balanceView, riskView, positionView, resultView, signalView, livePriceView, marketAnalysisView;
    private final Handler priceHandler = new Handler(Looper.getMainLooper());
    private volatile boolean activityActive = true;
    private static final long PRICE_REFRESH_MS = 60000L;
    private final Runnable priceRefreshTask = new Runnable() { public void run() { if (activityActive) { fetchLiveGoldPrice(); fetchAutomaticMarketAnalysis(); priceHandler.postDelayed(this, PRICE_REFRESH_MS); } } };
    private EditText entryInput, stopInput, targetInput, exitInput;
    private EditText currentPriceInput, h1FastInput, h1SlowInput, m15FastInput, m15SlowInput, rsiInput, atrInput;
    private boolean hasPosition, isLong;
    private double balance, entry, stop, target, quantity, riskAmount;
    private boolean signalReady, signalIsLong;
    private double signalEntry, signalStop, signalTarget, signalQuantity;

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
        prefs.edit().putFloat("balance", (float) balance)
            .putBoolean("has_position", hasPosition).putBoolean("is_long", isLong)
            .putLong("entry", Double.doubleToLongBits(entry))
            .putLong("stop", Double.doubleToLongBits(stop))
            .putLong("target", Double.doubleToLongBits(target))
            .putLong("quantity", Double.doubleToLongBits(quantity))
            .putLong("risk", Double.doubleToLongBits(riskAmount)).apply();
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
        TextView subtitle = text("GOLD SIGNAL ASSISTANT • DEMO ONLY", 13, Color.rgb(0, 220, 160), true);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);

        balanceView = text("", 23, Color.WHITE, true);
        riskView = text("", 15, Color.LTGRAY, false);
        addGap(root, 14);
        root.addView(balanceView);
        root.addView(riskView);

        addGap(root, 20);
        root.addView(text("LIVE XAU/USD SPOT PRICE", 18, Color.WHITE, true));
        livePriceView = text("Connecting to free public price feed…", 16, Color.rgb(0, 220, 160), true);
        root.addView(livePriceView);
        Button refreshPriceButton = button("REFRESH LIVE PRICE");
        root.addView(refreshPriceButton);
        refreshPriceButton.setOnClickListener(v -> fetchLiveGoldPrice());
        root.addView(text("Source: Gold-API public XAU spot feed • refreshes every 60 seconds • indicative spot price, not an Exness quote. Timestamp is checked for freshness.", 12, Color.LTGRAY, false));

        root.addView(text("AUTOMATIC XAUUSD MARKET ANALYSIS", 18, Color.WHITE, true));
        marketAnalysisView = text("Waiting for M15 and H1 candle data…", 14, Color.WHITE, false);
        root.addView(marketAnalysisView);
        Button autoRefreshButton = button("REFRESH MARKET ANALYSIS");
        root.addView(autoRefreshButton);
        autoRefreshButton.setOnClickListener(v -> fetchAutomaticMarketAnalysis());
        root.addView(text("Automatic analysis uses public XAUUSD M15/H1 candles. Indicators are calculated inside the app from closed candles. Feed prices may differ from Exness.", 12, Color.LTGRAY, false));
        root.addView(text("SIGNAL DETAILS", 18, Color.WHITE, true));
        root.addView(text("The app fills these values automatically from the public candle feed. They are reference data, not Exness executable prices.", 12, Color.LTGRAY, false));
        currentPriceInput = field("Current XAUUSD price");
        h1FastInput = field("H1 EMA 20");
        h1SlowInput = field("H1 EMA 50");
        m15FastInput = field("M15 EMA 20");
        m15SlowInput = field("M15 EMA 50");
        rsiInput = field("M15 RSI 14 (0 to 100)");
        atrInput = field("M15 ATR 14 (price units)");
        root.addView(currentPriceInput);
        root.addView(h1FastInput);
        root.addView(h1SlowInput);
        root.addView(m15FastInput);
        root.addView(m15SlowInput);
        root.addView(rsiInput);
        root.addView(atrInput);
        Button analyzeButton = button("ANALYSE XAUUSD");
        root.addView(analyzeButton);
        signalView = text("Waiting for automatic M15/H1 candle analysis.", 14, Color.WHITE, false);
        root.addView(signalView);
        Button loadSignalButton = button("LOAD SIGNAL INTO PAPER TRADE");
        root.addView(loadSignalButton);
        analyzeButton.setOnClickListener(v -> analyzeSignal());
        loadSignalButton.setOnClickListener(v -> loadSignal());

        addGap(root, 22);
        root.addView(text("PAPER TRADING — SIMULATION ONLY", 17, Color.rgb(0, 220, 160), true));
        root.addView(text("Review the signal, then manually open a simulated position below. No MT5 order is sent.", 12, Color.LTGRAY, false));
        entryInput = field("Entry price");
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
        root.addView(text("Safety: demo simulation only • maximum planned risk 0.5% • simulated leverage cap 2x. Public candle and spot feeds are indicative and may differ from Exness. Analysis is an automated indicator-based strategy, not a trained AI model or financial advice. No MT5 connection or broker orders. Position size is generic units, not MT5 lots.", 12, Color.LTGRAY, false));
        setContentView(scroll);
        priceHandler.post(priceRefreshTask);
    }


    private void fetchLiveGoldPrice() {
        if (livePriceView != null) livePriceView.setText("Updating public XAU/USD spot price…");
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL("https://api.gold-api.com/price/XAU");
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(12000);
                connection.setReadTimeout(12000);
                connection.setRequestProperty("Accept", "application/json");
                int code = connection.getResponseCode();
                if (code != 200) throw new Exception("Gold feed HTTP " + code);
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
                reader.close();
                JSONObject json = new JSONObject(body.toString());
                double spot = json.optDouble("price", Double.NaN);
                if (!valid(spot) || !"XAU".equalsIgnoreCase(json.optString("symbol", "")))
                    throw new Exception("Gold feed returned missing or invalid XAU price");
                String timestamp = json.optString("updatedAt", "");
                if (timestamp.isEmpty()) throw new Exception("Gold feed timestamp missing");
                boolean fresh = false;
                try {
                    String stamp = timestamp.length() >= 19 ? timestamp.substring(0, 19) : timestamp;
                    SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                    parser.setTimeZone(TimeZone.getTimeZone("UTC"));
                    Date updated = parser.parse(stamp);
                    long age = updated == null ? Long.MAX_VALUE : System.currentTimeMillis() - updated.getTime();
                    fresh = age >= -60000L && age <= 180000L;
                } catch (Exception ignored) { fresh = false; }
                final double finalSpot = spot;
                final String finalTimestamp = timestamp;
                final boolean finalFresh = fresh;
                runOnUiThread(() -> {
                    if (!activityActive) return;
                    if (finalFresh) currentPriceInput.setText(price(finalSpot));
                    livePriceView.setText("XAU/USD: $" + String.format(Locale.US, "%.2f", finalSpot) +
                        "\nFeed status: " + (finalFresh ? "FRESH" : "STALE — DO NOT USE AS LIVE") +
                        "\nUpdated: " + finalTimestamp +
                        (finalFresh ? "" : "\nPrice field is shown for reference only; it was not copied into the signal entry."));
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    if (activityActive && livePriceView != null)
                        livePriceView.setText("LIVE PRICE UNAVAILABLE — " + ex.getMessage() + "\nDo not use an old price as a live quote.");
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        activityActive = false;
        priceHandler.removeCallbacks(priceRefreshTask);
        super.onDestroy();
    }


    private static class Candle {
        double open, high, low, close;
        Candle(double o, double h, double l, double c) { open=o; high=h; low=l; close=c; }
    }

    private JSONObject getJson(String address) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(12000);
            connection.setReadTimeout(12000);
            connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            if (code != 200) throw new Exception("HTTP " + code + " from market-data feed");
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
            reader.close();
            return new JSONObject(body.toString());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private ArrayList<Candle> getClosedCandles(String timeframe) throws Exception {
        JSONObject json = getJson("https://biquote.io/api/XAUUSD/ohlc?interval=" + timeframe + "&limit=100");
        JSONArray bars = json.optJSONArray("bars");
        if (bars == null) throw new Exception(timeframe + " candle list missing");
        ArrayList<Candle> candles = new ArrayList<>();
        // Provider returns newest first. Insert at the front to restore oldest-first order.
        for (int i=0; i<bars.length(); i++) {
            JSONObject b = bars.optJSONObject(i);
            if (b == null || b.optBoolean("isOpen", false)) continue;
            double o=b.optDouble("open", Double.NaN), h=b.optDouble("high", Double.NaN);
            double l=b.optDouble("low", Double.NaN), cl=b.optDouble("close", Double.NaN);
            if (valid(o) && valid(h) && valid(l) && valid(cl) && h >= l) {
                candles.add(0, new Candle(o,h,l,cl));
            }
        }
        if (candles.size() < 55) throw new Exception("Not enough closed " + timeframe + " candles (" + candles.size() + ")");
        return candles;
    }

    private double ema(ArrayList<Candle> bars, int period) {
        double value=0;
        for (int i=0; i<period; i++) value += bars.get(i).close;
        value /= period;
        double k=2.0/(period+1.0);
        for (int i=period; i<bars.size(); i++) value = bars.get(i).close*k + value*(1.0-k);
        return value;
    }

    private double rsi(ArrayList<Candle> bars, int period) {
        double gains=0, losses=0;
        int start=bars.size()-period;
        for (int i=start; i<bars.size(); i++) {
            double change=bars.get(i).close-bars.get(i-1).close;
            if (change>0) gains+=change; else losses-=change;
        }
        double avgGain=gains/period, avgLoss=losses/period;
        if (avgLoss==0) return avgGain==0 ? 50 : 100;
        double rs=avgGain/avgLoss;
        return 100.0-(100.0/(1.0+rs));
    }

    private double atr(ArrayList<Candle> bars, int period) {
        double sum=0;
        int start=bars.size()-period;
        for (int i=start; i<bars.size(); i++) {
            Candle b=bars.get(i), prev=bars.get(i-1);
            double tr=Math.max(b.high-b.low, Math.max(Math.abs(b.high-prev.close), Math.abs(b.low-prev.close)));
            sum+=tr;
        }
        return sum/period;
    }

    private void fetchAutomaticMarketAnalysis() {
        if (marketAnalysisView != null) marketAnalysisView.setText("Downloading closed M15 and H1 candles…");
        new Thread(() -> {
            try {
                ArrayList<Candle> m15=getClosedCandles("15m");
                ArrayList<Candle> h1=getClosedCandles("1h");
                double price=m15.get(m15.size()-1).close;
                double h1e20=ema(h1,20), h1e50=ema(h1,50);
                double m15e20=ema(m15,20), m15e50=ema(m15,50);
                double rsi14=rsi(m15,14), atr14=atr(m15,14);
                runOnUiThread(() -> {
                    if (!activityActive) return;
                    currentPriceInput.setText(price(price));
                    h1FastInput.setText(price(h1e20)); h1SlowInput.setText(price(h1e50));
                    m15FastInput.setText(price(m15e20)); m15SlowInput.setText(price(m15e50));
                    rsiInput.setText(String.format(Locale.US,"%.2f",rsi14));
                    atrInput.setText(price(atr14));
                    marketAnalysisView.setText("DATA: M15 + H1 CLOSED CANDLES RECEIVED\n" +
                        "Latest M15 close: " + price(price) +
                        "\nH1 EMA 20 / 50: " + price(h1e20) + " / " + price(h1e50) +
                        "\nM15 EMA 20 / 50: " + price(m15e20) + " / " + price(m15e50) +
                        "\nM15 RSI 14: " + String.format(Locale.US,"%.2f",rsi14) +
                        "\nM15 ATR 14: " + price(atr14) +
                        "\nUpdated from latest closed candles. Refreshes every 60 seconds.");
                    analyzeSignal();
                });
            } catch (Exception ex) {
                runOnUiThread(() -> {
                    if (activityActive && marketAnalysisView != null)
                        marketAnalysisView.setText("AUTOMATIC ANALYSIS UNAVAILABLE\n" + ex.getMessage() +
                            "\nNo signal generated. Check connection and try again.");
                });
            }
        }).start();
    }

    private void analyzeSignal() {
        double price = read(currentPriceInput), h1Fast = read(h1FastInput), h1Slow = read(h1SlowInput);
        double m15Fast = read(m15FastInput), m15Slow = read(m15SlowInput);
        double rsi = read(rsiInput), atr = read(atrInput);
        if (!valid(price) || !valid(h1Fast) || !valid(h1Slow) || !valid(m15Fast) ||
            !valid(m15Slow) || Double.isNaN(rsi) || rsi < 0 || rsi > 100 || !valid(atr)) {
            toast("Enter positive price, EMA and ATR values, and an RSI between 0 and 100.");
            return;
        }
        boolean bullish = h1Fast > h1Slow && m15Fast > m15Slow && rsi >= 50 && rsi <= 70 && price > m15Fast;
        boolean bearish = h1Fast < h1Slow && m15Fast < m15Slow && rsi >= 30 && rsi <= 50 && price < m15Fast;
        signalReady = false;
        if (!bullish && !bearish) {
            signalView.setText("SIGNAL: WAIT\nH1 and M15 trend/RSI conditions do not align. Do not force a trade.");
            return;
        }
        signalIsLong = bullish;
        signalEntry = price;
        double distance = atr * 1.5;
        signalStop = bullish ? price - distance : price + distance;
        signalTarget = bullish ? price + (distance * 2.0) : price - (distance * 2.0);
        double riskBudget = balance * MAX_RISK;
        signalQuantity = Math.min(riskBudget / distance, (balance * MAX_LEVERAGE) / price);
        if (!valid(signalQuantity)) {
            signalView.setText("SIGNAL: WAIT\nPosition size could not be calculated safely.");
            return;
        }
        signalReady = true;
        signalView.setText("SIGNAL: " + (bullish ? "BUY / LONG" : "SELL / SHORT") +
            "\nEntry reference: " + price(signalEntry) +
            "\nStop-loss: " + price(signalStop) +
            "\nTake-profit (2R): " + price(signalTarget) +
            "\nEstimated size: " + String.format(Locale.US, "%.6f units (not MT5 lots)", signalQuantity) +
            "\nPlanned stop risk: " + money(signalQuantity * distance) +
            "\n\nManual approval required. Confirm current prices and spread in MT5 before acting.");
    }

    private void loadSignal() {
        if (!signalReady) {
            toast("Analyse a valid BUY or SELL signal first. WAIT cannot be loaded.");
            return;
        }
        entryInput.setText(price(signalEntry));
        stopInput.setText(price(signalStop));
        targetInput.setText(price(signalTarget));
        toast("Signal values loaded. Review them, then tap OPEN " + (signalIsLong ? "LONG" : "SHORT") + " for paper trading.");
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
        quantity = Math.min(riskAmount / distance, (balance * MAX_LEVERAGE) / e);
        if (!valid(quantity)) { toast("Position size could not be calculated."); return; }
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
        signalReady = false;
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