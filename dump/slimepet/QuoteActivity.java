package com.lin.slimepet;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 名言：说多久、隔多久说一次、字多大，以及「说过的话」历史。
 * 名言本身带出处，说过的不重复 —— 一轮说完了才重新开始。
 */
public class QuoteActivity extends Activity {

    private SharedPreferences sp;
    private LinearLayout histBox;
    private TextView histNote;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("slimepet", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(40), dp(20), dp(24));
        root.setBackgroundColor(0xFF0B0F17);

        TextView title = new TextView(this);
        title.setText("名言");
        title.setTextSize(24f);
        title.setTextColor(0xFFEAF2FF);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("她说的都是真名言，带出处；说过的不重复。\n改完不用重启，桌宠每 1.2 秒读一次设置。");
        hint.setTextSize(12.5f);
        hint.setTextColor(0xFF7D8CA3);
        hint.setLineSpacing(0f, 1.4f);
        hint.setPadding(0, dp(6), 0, dp(14));
        root.addView(hint);

        addSection(root, "什么时候说");
        addSwitch(root, "名言模式", "quote_on", true, "开：到点就说一条带出处的名言；关：说她自己那套冷句");
        addSlider(root, "名言间隔", "quote_gap", 5, 600, 45, " 秒",
                "每隔多久冒一条名言（开了 AI 就是多久问一次 AI）");
        addSlider(root, "停留时间", "quote_sec", 2, 30, 4, " 秒",
                "一条名言在气泡里停多久：句子长就调大一点");

        addSection(root, "好看程度");
        addSlider(root, "气泡字号", "bubble_font", 9, 24, 13, " sp",
                "字号越大气泡越宽，长句照样换行，不截断");

        addSection(root, "说过的话（最近的在前，最多 200 条）");
        histNote = new TextView(this);
        histNote.setTextSize(12f);
        histNote.setTextColor(0xFF6E7F96);
        histNote.setPadding(0, dp(6), 0, 0);
        root.addView(histNote);

        histBox = new LinearLayout(this);
        histBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(histBox);

        Button refresh = mkBtn("刷新历史");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { renderHistory(); }
        });
        root.addView(refresh);

        Button clear = mkBtn("清空历史（同时重置「已说清单」）");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                sp.edit().putString("quote_history", "")
                        .putString("quote_used", "")
                        .putString("quiet_used", "")
                        .putString("form0_used", "").putString("form1_used", "")
                        .putString("form2_used", "").putString("form3_used", "")
                        .putString("form4_used", "").putString("form5_used", "")
                        .putString("form6_used", "").apply();
                renderHistory();
            }
        });
        root.addView(clear);

        Button back = mkBtn("返回");
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        root.addView(back);

        ScrollView sc = new ScrollView(this);
        sc.addView(root);
        setContentView(sc);

        renderHistory();
    }

    @Override protected void onResume() { super.onResume(); if (histBox != null) renderHistory(); }

    /** 把历史一条条摆出来：时间 + 句子（出处就在句子里） */
    private void renderHistory() {
        if (histBox == null) return;
        histBox.removeAllViews();
        String h = sp.getString("quote_history", "");
        if (h == null || h.trim().length() == 0) {
            histNote.setText("还没有说过名言（关掉「名言模式」时只说她自己的冷句，不记在这里）");
            return;
        }
        String[] lines = h.split("\n");
        histNote.setText("共 " + lines.length + " 条");
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        int n = 0;
        for (String line : lines) {
            if (line.length() == 0) continue;
            String time = "";
            String text = line;
            int cut = line.indexOf('\u0001');
            if (cut > 0) {
                text = line.substring(cut + 1);
                try { time = fmt.format(new Date(Long.parseLong(line.substring(0, cut)))); }
                catch (Exception ignored) {}
            }
            TextView tv = new TextView(this);
            tv.setText(time.length() > 0 ? (time + "　" + text) : text);
            tv.setTextSize(12.5f);
            tv.setTextColor(0xFFDCE8F8);
            tv.setLineSpacing(0f, 1.35f);
            tv.setPadding(0, dp(8), 0, 0);
            histBox.addView(tv);
            if (++n >= 200) break;
        }
    }

    private Button mkBtn(String text) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextSize(14f);
        btn.setAllCaps(false);
        btn.setTextColor(0xFFDCE8F8);
        btn.setBackgroundColor(0xFF17202E);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        btn.setLayoutParams(lp);
        return btn;
    }

    private void addSection(LinearLayout box, String title) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(13f);
        t.setTextColor(0xFF7FE3FF);
        t.setPadding(0, dp(20), 0, dp(2));
        box.addView(t);
    }

    private void addSlider(LinearLayout box, String label, final String key,
                           final int min, final int max, int def, final String unit, String note) {
        final TextView name = new TextView(this);
        name.setTextSize(13.5f);
        name.setTextColor(0xFFDCE8F8);
        final int cur = sp.getInt(key, def);
        name.setText(label + "　" + cur + unit);
        name.setPadding(0, dp(12), 0, 0);

        SeekBar bar = new SeekBar(this);
        bar.setMax(max - min);
        bar.setProgress(Math.max(0, Math.min(max - min, cur - min)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int val = min + progress;
                name.setText(label + "　" + val + unit);
                if (fromUser) sp.edit().putInt(key, val).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
        box.addView(name);
        box.addView(bar);
        if (note != null && note.length() > 0) {
            TextView t = new TextView(this);
            t.setText(note);
            t.setTextSize(11.5f);
            t.setTextColor(0xFF6E7F96);
            t.setPadding(dp(2), dp(2), 0, 0);
            box.addView(t);
        }
    }

    private void addSwitch(LinearLayout box, String label, final String key, boolean def, String note) {
        Switch sw = new Switch(this);
        sw.setText(label + "（" + note + "）");
        sw.setTextSize(13f);
        sw.setTextColor(0xFFDCE8F8);
        sw.setPadding(0, dp(14), 0, 0);
        sw.setChecked(sp.getBoolean(key, def));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(CompoundButton b, boolean v) {
                sp.edit().putBoolean(key, v).apply();
            }
        });
        box.addView(sw);
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
