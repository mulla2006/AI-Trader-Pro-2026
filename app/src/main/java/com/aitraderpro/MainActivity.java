
package com.aitraderpro;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setPadding(25, 25, 25, 25);
        layout.setBackgroundColor(Color.rgb(12, 18, 30));

        TextView title = new TextView(this);
        title.setText("AI TRADER PRO");
        title.setTextSize(30);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);

        TextView status = new TextView(this);
        status.setText(
            "\nBINANCE TRADING\n\n" +
            "Mode: DEMO\n" +
            "Market: SPOT + FUTURES\n" +
            "Leverage: 2x\n" +
            "Maximum Risk: 0.5%\n\n" +
            "Trading engine: Not connected\n" +
            "Account: Demo mode"
        );
        status.setTextSize(18);
        status.setTextColor(Color.rgb(0, 220, 160));
        status.setGravity(Gravity.CENTER);

        layout.addView(title);
        layout.addView(status);

        setContentView(layout);
    }
}
