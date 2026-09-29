package com.doxton.questfan;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.InputType;
import android.util.Log;
import org.json.JSONObject;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final int[] speeds = {80, 90, 100};
    private final Button[] gears = new Button[3];
    private Button off, connect;
    private EditText address;
    private TextView status;
    private boolean busy;
    private final int bg = Color.rgb(12, 17, 27);
    private final int text = Color.rgb(239, 244, 250);
    private final int muted = Color.rgb(157, 172, 187);
    private final int normal = Color.rgb(40, 53, 69);
    private final int selected = Color.rgb(16, 159, 115);

    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable shape(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(14));
        return shape;
    }
    private TextView label(String content, int size, int color) {
        TextView v = new TextView(this);
        v.setText(content);
        v.setTextSize(size);
        v.setTextColor(color);
        return v;
    }
    private void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top);
        parent.addView(child, p);
    }
    private Button button(String title) {
        Button b = new Button(this);
        b.setText(title);
        b.setTextColor(text);
        b.setTextSize(20);
        b.setAllCaps(false);
        b.setBackground(shape(normal));
        b.setMinHeight(dp(64));
        b.setElevation(dp(2));
        return b;
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(28), dp(24), dp(28), dp(32));
        scroll.addView(content);
        setContentView(scroll);

        TextView title = label("面罩风扇", 30, text);
        title.setTypeface(null, Typeface.BOLD);
        add(content, title, 0);
        TextView subtitle = label("Quest 3 直连 ESP32 · 三挡控制", 15, muted);
        add(content, subtitle, 4);

        TextView ipLabel = label("风扇板 IPv4 地址", 15, muted);
        add(content, ipLabel, 28);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        add(content, row, 8);
        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextSize(19);
        address.setTextColor(text);
        address.setHintTextColor(muted);
        address.setHint("例如 192.168.10.178");
        address.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        address.setPadding(dp(16), 0, dp(12), 0);
        address.setBackground(shape(normal));
        address.setText(getPreferences(0).getString("fan_ip", "192.168.10.178"));
        row.addView(address, new LinearLayout.LayoutParams(0, dp(56), 1));
        connect = button("连接");
        connect.setTextSize(17);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(92), dp(56));
        cp.leftMargin = dp(8);
        row.addView(connect, cp);
        connect.setOnClickListener(v -> request("/state"));

        status = label("正在读取风扇状态…", 16, muted);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setMinHeight(dp(48));
        add(content, status, 14);
        String[] names = {"低速 · 80%", "中速 · 90%", "高速 · 100%"};
        for (int i = 0; i < gears.length; i++) {
            final int speed = speeds[i];
            gears[i] = button(names[i]);
            add(content, gears[i], i == 0 ? 10 : 12);
            gears[i].setOnClickListener(v -> request(FanProtocol.gearPath(speed)));
        }
        off = button("关闭风扇");
        add(content, off, 12);
        off.setOnClickListener(v -> request("/off"));
        TextView hint = label("头显与风扇板须处于同一局域网。地址变了，在上面修改后点连接。", 13, muted);
        add(content, hint, 24);
        request("/state");
    }

    private void setBusy(boolean active) {
        busy = active;
        connect.setEnabled(!active);
        for (Button b : gears) b.setEnabled(!active);
        off.setEnabled(!active);
    }
    private void paint(boolean on, int speed) {
        for (int i = 0; i < gears.length; i++) gears[i].setBackground(shape(on && speed == speeds[i] ? selected : normal));
        off.setBackground(shape(on ? normal : selected));
    }
    private void clearSelection() {
        for (Button b : gears) b.setBackground(shape(normal));
        off.setBackground(shape(normal));
    }
    private void request(String path) {
        if (busy) return;
        final String host;
        try { host = FanProtocol.normalizedHost(address.getText().toString()); }
        catch (IllegalArgumentException e) {
            clearSelection();
            status.setText("地址错误：" + e.getMessage());
            status.setTextColor(Color.rgb(255, 166, 135));
            return;
        }
        getPreferences(0).edit().putString("fan_ip", host).apply();
        setBusy(true);
        status.setText(path.equals("/state") ? "正在连接风扇板…" : "正在发送指令…");
        status.setTextColor(muted);
        io.execute(() -> {
            String message;
            Boolean on = null;
            int speed = 0;
            try {
                JSONObject state = new JSONObject(FanProtocol.request(host, path));
                if (!state.has("on") || !state.has("speed")) throw new Exception("状态字段缺失");
                on = state.getBoolean("on");
                speed = state.getInt("speed");
                if (speed != 80 && speed != 90 && speed != 100) throw new Exception("挡位无效");
                message = on ? "运行中 · " + speed + "%" : "风扇已关闭 · 预设 " + speed + "%";
                Log.i("QuestFan", "HTTP " + path + " succeeded, on=" + on + ", speed=" + speed);
            } catch (Exception e) {
                on = null;
                message = "连接失败：" + e.getMessage();
                Log.w("QuestFan", "HTTP " + path + " failed: " + e.getMessage());
            }
            final String result = message;
            final Boolean isOn = on;
            final int currentSpeed = speed;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false);
                if (isOn == null) {
                    clearSelection();
                    status.setTextColor(Color.rgb(255, 166, 135));
                } else {
                    paint(isOn, currentSpeed);
                    status.setTextColor(Color.rgb(130, 226, 192));
                }
                status.setText(result);
            });
        });
    }
    @Override protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }
}
