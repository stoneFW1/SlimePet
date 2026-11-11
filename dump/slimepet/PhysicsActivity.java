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

/** 物理手感：重力 / 弹性 / 摩擦 / 抛掷 / 顶部反弹 / 多宠碰撞 / 低配模式 */
public class PhysicsActivity extends Activity {

    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        sp = getSharedPreferences("slimepet", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(40), dp(20), dp(24));
        root.setBackgroundColor(0xFF0B0F17);

        TextView title = new TextView(this);
        title.setText("物理 / 行为设置");
        title.setTextSize(24f);
        title.setTextColor(0xFFEAF2FF);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("拖动即时保存，桌宠每 1.2 秒读一次，不用重启");
        hint.setTextSize(12.5f);
        hint.setTextColor(0xFF7D8CA3);
        hint.setPadding(0, dp(6), 0, dp(16));
        root.addView(hint);

        addSection(root, "物理");
        addSlider(root, "重力", "gravity", 0, 3000, 2600, " px/s²", "越小越飘，越大掉得越沉");
        addSlider(root, "碰边弹性", "bounce", 0, 100, 45, " %", "撞到屏幕边或地面后的回弹比例");
        addSlider(root, "地面摩擦", "friction", 0, 100, 40, " %", "落地后水平速度衰减，越大停得越快");
        addSlider(root, "抛掷力度", "throw", 50, 200, 110, " %", "甩出去时的初速度倍率");

        addSection(root, "位置");
        addSlider(root, "离底距离", "bottom_gap", 0, 120, 36, " dp",
                "她站在地上时，身体底边离屏幕底还有多远：看着悬空就调小，看着被导航条压住就调大");

        addSection(root, "行为");
        addSlider(root, "移动速度", "move_speed", 20, 200, 100, " %", "走路、爬墙、天花板横移的整体速度");
        addSlider(root, "说话间隔", "talk_gap", 5, 300, 30, " 秒", "关掉「名言模式」后，她自己那套冷句多久冒一句；名言的节奏在「名言」页调");

        addSection(root, "开关");
        addSwitch(root, "顶部反弹", "top_bounce", true, "关掉后可以把她甩出屏幕顶部再落回来");
        addSwitch(root, "多宠碰撞", "multi_collide", true, "两只以上时按动量守恒互相弹开，且不会重合");
        addSwitch(root, "低配模式", "low_spec", false, "30fps + 关碰撞 + 不画内部纹理，多只时更省电");

        Button back = new Button(this);
        back.setText("返回");
        back.setTextSize(14f);
        back.setAllCaps(false);
        back.setTextColor(0xFFDCE8F8);
        back.setBackgroundColor(0xFF17202E);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(22);
        back.setLayoutParams(lp);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        root.addView(back);

        ScrollView sc = new ScrollView(this);
        sc.addView(root);
        setContentView(sc);
    }

    private void addSection(LinearLayout box, String title) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(13f);
        t.setTextColor(0xFF7FE3FF);
        t.setPadding(0, dp(20), 0, dp(2));
        box.addView(t);
    }

    private void addNote(LinearLayout box, String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(11.5f);
        t.setTextColor(0xFF6E7F96);
        t.setPadding(dp(2), dp(2), 0, 0);
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
        addNote(box, note);
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
