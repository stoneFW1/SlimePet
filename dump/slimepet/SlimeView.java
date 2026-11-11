package com.lin.slimepet;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.Random;

/**
 * 元素史莱姆：圆顶果冻身体 + 发光眼 + 七种元素（草/风/雷有头顶装饰）。
 * 视觉与网页预览 slime2.html 一致。
 */
public class SlimeView extends View {

    public interface Callback {
        void onTap();
        void onDoubleTap();
        void onLongPress();
        void onDrag(float dx, float dy);
        void onDragEnd(float vx, float vy);
        void onElementChanged(String name);
        void onWake();
        void onTripleTap();
    }

    /* ---------- 元素配色 ---------- */
    private static final int[] DEEP  = {0xFF8A1C07, 0xFF0E6B74, 0xFF4A1580, 0xFF5E4A22, 0xFF2B6A1C, 0xFF1F5F8A, 0xFF123F75};
    private static final int[] MID   = {0xFFF2620F, 0xFF37C6C0, 0xFF9B45E0, 0xFFC9A05A, 0xFF77C13A, 0xFF63C3EA, 0xFF2F8FD6};
    private static final int[] LIGHT = {0xFFFFC24A, 0xFFB9FDF6, 0xFFE6C4FF, 0xFFF4E3B8, 0xFFCDF59A, 0xFFDCF6FF, 0xFFBFEAFF};
    private static final int[] AURA  = {0xFFFF5A1E, 0xFF2FF0D8, 0xFFB56BFF, 0xFFE8B45C, 0xFF8DE83C, 0xFF7FE3FF, 0xFF3FB6FF};
    private static final int[] EYE   = {0xFFFFE08A, 0xFFEAFFFB, 0xFFFDF3FF, 0xFFFFCF7A, 0xFFFFE9A3, 0xFFF4FEFF, 0xFFEAFAFF};
    private static final int[] CORE  = {0xFFFF6A2A, 0xFF8FE8E0, 0xFFC98BFF, 0xFFFF9A3C, 0xFFFFB038, 0xFFA8E8FF, 0xFF66C8FF};
    private static final String[] NAME = {"火", "风", "雷", "岩", "草", "冰", "水"};
    private static final String[] KEY  = {"fire", "anemo", "electro", "geo", "grass", "cryo", "water"};

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path body = new Path();
    private final RectF oval = new RectF();
    private final Random rnd = new Random();

    private int idx = 4;                 /* 默认草 */
    private float squash = 0f, squashV = 0f;
    private float blush = 0f;
    private int clip = -1;
    private long clipStart = 0L;
    private float clipDur = 1f;
    private long nextClip = 0L;
    private float tilt = 0f;
    private boolean climbing = false;
    private long wriggleT0 = 0L;
    private long bubbleUntil = 0L;
    /* ---- 渐变缓存：只有元素或尺寸变了才重建，避免每帧 new ---- */
    private int shIdx = -1, shW = -1, shH = -1;
    private Shader shHalo, shBody, shCore, shHL;
    private Shader gFire, gBlush, gEyeBody, gLeaf, gHorn, gBolt;
    private float shR = 0f;
    private final Path tmpPath = new Path();   /* 每帧复用，省掉 new Path 的垃圾 */

    /* ---- 光晕缓存：外圈那道径向渐变几乎盖满整个窗口，是每帧最贵的一层。
       它只在换元素/改大小时变，所以烘成半分辨率位图，每帧只做一次缩放贴图。 ---- */
    private Bitmap haloBmp;
    private int haloW = -1, haloH = -1;
    private final RectF haloDst = new RectF();
    private final Paint bmpPaint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    /* 低配：隔帧绘制 */
    public boolean lowSpec = false;
    public boolean moving = false;      /* 由服务告诉它"正在动" */
    public boolean paused = false;      /* 锁屏时服务置位：停画、也不再排下一帧 */
    private boolean skipDraw = false;
    private float gazeX = 0f, gazeY = 0f;

    /* ---- 实测帧率 ---- */
    private final float[] fpsWin = new float[30];
    private int fpsIdx = 0, fpsFill = 0;
    private float fps = 0f;

    /* ---- 手势：合窗 / 分窗两种渲染都走 Gestures，这里只是它的一种宿主 ---- */
    private final Gestures gestures;
    private boolean sleeping = false;
    private long sleepAt = 0L, lastAct = 0L;
    public boolean autoSleep = false;         /* 默认不自己睡，避免各种怪状态 */
    public static final long SLEEP_AFTER = 300000L;
    private float blink = 1f;
    private long nextBlink = 0L, blinkUntil = 0L, nextGlance = 0L;

    private long lastFrame = 0L;
    private float time = 0f;
    private int pose = 0;            /* 0 地面 1 左壁 2 右壁 3 天花板 */
    private float wallFlat = 0f;     /* 贴墙时压扁的程度 0~1 */
    private float idlePulse = 0f;
    private float glanceTo = 0f;
    private long glanceUntil = 0L;

    /* 合窗模式下由 StageView 统一驱动，自己不再排下一帧 */
    public boolean selfDriven = true;
    public boolean dragging = false;

    private Callback cb;

    /* 触摸 -> 回调 的适配层（只有分窗模式会用到） */
    private final Gestures.Sink sink = new Gestures.Sink() {
        @Override public boolean isSleeping() { return sleeping; }
        @Override public void onPress() { touchDown(); }
        @Override public void onWake() { waking(); if (cb != null) cb.onWake(); }
        @Override public void onTap() { if (cb != null) cb.onTap(); }
        @Override public void onDoubleTap() { if (cb != null) cb.onDoubleTap(); }
        @Override public void onTripleTap() { if (cb != null) cb.onTripleTap(); }
        @Override public void onLongPress() { if (cb != null) cb.onLongPress(); }
        @Override public void onDrag(float dx, float dy) {
            dragging = true; waking();
            if (cb != null) cb.onDrag(dx, dy);
        }
        @Override public void onDragEnd(float vx, float vy) {
            dragging = false;
            if (cb != null) cb.onDragEnd(vx, vy);
        }
    };

    public SlimeView(Context c) {
        super(c);
        gestures = new Gestures(c);
        try {
            SharedPreferences sp = c.getSharedPreferences("slimepet", Context.MODE_PRIVATE);
            String saved = sp.getString("element_key", "");
            int found = -1;
            for (int i = 0; i < KEY.length; i++) if (KEY[i].equals(saved)) { found = i; break; }
            idx = found >= 0 ? found : rnd.nextInt(KEY.length);
        } catch (Exception ignored) {}
        long now = SystemClock.uptimeMillis();
        lastAct = now; lastFrame = now;
        nextBlink = now + 2600L;
    }

    public void setCallback(Callback c) { cb = c; }
    public void poke() { squashV += 3.0f; blush = 1f; waking(); playClip(0); }

    /** 手指压下去：立刻给一点形变反馈（真正的形态变化等手势裁决） */
    public void touchDown() {
        if (!sleeping) waking();
        else lastAct = SystemClock.uptimeMillis();   /* 睡着被碰：只刷新计时，不叫醒 */
        squashV += 0.9f;
        invalidate();
    }
    public int elementIndex() { return idx; }
    public String elementKey() { return KEY[idx]; }
    public String elementName() { return NAME[idx]; }

    public void setElementIndex(int i) {
        idx = ((i % KEY.length) + KEY.length) % KEY.length;
        squashV += 1.4f;
        try {
            getContext().getSharedPreferences("slimepet", Context.MODE_PRIVATE)
                    .edit().putString("element_key", KEY[idx]).apply();
        } catch (Exception ignored) {}
        if (cb != null) cb.onElementChanged(NAME[idx]);
        invalidate();
    }

    public void setGaze(float nx, float ny) { gazeX = nx; gazeY = ny; }

    /** 陀螺仪倾斜角度（度），拿歪手机她会跟着歪 */
    public void setTilt(float deg) { tilt = Math.max(-18f, Math.min(18f, deg)); }

    /** 在爬墙：开启尺蠖式伸缩 */
    public void setClimbing(boolean c) {
        if (c && !climbing) wriggleT0 = SystemClock.uptimeMillis();
        climbing = c;
    }

    /** 锁屏停画：省掉锁屏期间的全部合成开销 */
    public void setPaused(boolean p) {
        paused = p;
        if (!p && selfDriven) postInvalidateOnAnimation();
    }

    /** 0 地面 / 1 左壁 / 2 右壁 / 3 天花板：换个姿势贴上去 */
    public void setPose(int p) { pose = p; }

    /** 起跳前把身体拉长一点 */
    public void hopStart() { squashV -= 2.4f; }

    /** 落地：压扁 + 泛红 */
    public void land() { squashV += 3.2f; blush = Math.max(blush, 0.55f); }
    public void setSleeping(boolean s) {
        sleeping = s;
        if (s) sleepAt = SystemClock.uptimeMillis();
        else waking();
        invalidate();
    }
    public boolean isSleeping() { return sleeping; }

    /** 给"调试信息"用：一句状态摘要 */
    public String debugLine() {
        long idle = (SystemClock.uptimeMillis() - lastAct) / 1000L;
        return "睡=" + (sleeping ? 1 : 0) + " 自动睡=" + (autoSleep ? 1 : 0)
                + " 静默=" + idle + "s 动作=" + (clip < 0 ? "-" : String.valueOf(clip))
                + " 拖动=" + (dragging ? 1 : 0)
                + " " + Math.round(fps) + "fps" + (lowSpec ? "(低配)" : "");
    }

    /* 每个关键帧：进度, 横向缩放, 纵向缩放, 旋转角度, 横向位移(×R), 纵向位移(×R) */
    private static final float[][][] CLIPS = {
        /* 0 弹一下 */
        {{0f,1f,1f,0f,0f,0f},{0.16f,0.86f,1.18f,0f,0f,0f},{0.34f,1.12f,0.88f,0f,0f,0f},
         {0.55f,0.95f,1.06f,0f,0f,0f},{0.78f,1.03f,0.98f,0f,0f,0f},{1f,1f,1f,0f,0f,0f}},
        /* 1 伸个懒腰 */
        {{0f,1f,1f,0f,0f,0f},{0.30f,0.92f,1.22f,0f,0f,-0.06f},{0.55f,0.92f,1.24f,0f,0f,-0.07f},
         {0.75f,1.08f,0.94f,0f,0f,0f},{1f,1f,1f,0f,0f,0f}},
        /* 2 歪头看看左边、再看看右边 */
        {{0f,1f,1f,0f,0f,0f},{0.22f,0.97f,1.03f,-9f,-0.05f,0f},{0.45f,0.97f,1.03f,-9f,-0.05f,0f},
         {0.68f,0.97f,1.03f,9f,0.05f,0f},{0.85f,0.99f,1.01f,4f,0.02f,0f},{1f,1f,1f,0f,0f,0f}},
        /* 4 果冻晃：先猛压再一层层回 */
        {{0f,1f,1f,0f,0f,0f},{0.14f,1.16f,0.80f,0f,0f,0.02f},{0.32f,0.86f,1.16f,0f,0f,0f},
         {0.5f,1.10f,0.92f,0f,0f,0f},{0.68f,0.94f,1.05f,0f,0f,0f},{0.85f,1.03f,0.98f,0f,0f,0f},{1f,1f,1f,0f,0f,0f}},
        /* 5 轻轻跳一下 */
        {{0f,1f,1f,0f,0f,0f},{0.12f,0.9f,1.14f,0f,0f,0.02f},{0.35f,0.95f,1.1f,0f,0f,-0.20f},
         {0.55f,1.02f,1f,0f,0f,-0.26f},{0.75f,1.14f,0.86f,0f,0f,0f},{1f,1f,1f,0f,0f,0f}},
        /* 3b 转一圈（旋转时自动缩小，避免被窗口裁掉） */
        {{0f,1f,1f,0f,0f,0f},{0.12f,0.97f,1.04f,-15f,0f,0f},{0.30f,1f,1f,-120f,0f,-0.02f},
         {0.55f,1f,1f,-240f,0f,-0.02f},{0.78f,1f,1f,-345f,0f,0f},{0.90f,0.95f,1.06f,-360f,0f,0f},{1f,1f,1f,-360f,0f,0f}},
        /* 6 跳舞：左右扭 */
        {{0f,1f,1f,0f,0f,0f},{0.15f,0.95f,1.06f,-8f,-0.08f,0f},{0.32f,1.04f,0.96f,8f,0.08f,0f},
         {0.50f,0.95f,1.06f,-8f,-0.08f,0f},{0.68f,1.04f,0.96f,8f,0.08f,0f},
         {0.84f,0.98f,1.03f,-4f,-0.03f,0f},{1f,1f,1f,0f,0f,0f}},
        /* 7 打哈欠：先扁后长 */
        {{0f,1f,1f,0f,0f,0f},{0.25f,0.90f,1.24f,0f,0f,-0.04f},{0.55f,0.86f,1.30f,0f,0f,-0.05f},
         {0.78f,1.06f,0.96f,0f,0f,0f},{1f,1f,1f,0f,0f,0f}},
        /* 8 抖一抖（快频小抖） */
        {{0f,1f,1f,0f,0f,0f},{0.10f,1.02f,0.99f,-3f,-0.03f,0f},{0.20f,1.02f,0.99f,3f,0.03f,0f},
         {0.30f,1.02f,0.99f,-3f,-0.03f,0f},{0.40f,1.02f,0.99f,3f,0.03f,0f},
         {0.50f,1.02f,0.99f,-2f,-0.02f,0f},{0.60f,1.02f,0.99f,2f,0.02f,0f},
         {0.75f,1.01f,1f,-1f,-0.01f,0f},{1f,1f,1f,0f,0f,0f}},
        /* 9 被吓一跳：猛缩一下再慢慢鼓回来 */
        {{0f,1f,1f,0f,0f,0f},{0.10f,0.72f,1.22f,0f,0f,0.05f},{0.28f,0.80f,1.14f,0f,0f,0.03f},
         {0.50f,1.06f,0.95f,0f,0f,0f},{0.72f,0.97f,1.03f,0f,0f,0f},{1f,1f,1f,0f,0f,0f}},
        /* 10 点头两下 */
        {{0f,1f,1f,0f,0f,0f},{0.16f,1.03f,0.96f,0f,0f,0.05f},{0.30f,0.98f,1.02f,0f,0f,0f},
         {0.46f,1.03f,0.96f,0f,0f,0.05f},{0.62f,0.98f,1.02f,0f,0f,0f},{1f,1f,1f,0f,0f,0f}},
        /* 12 发呆 */
        {{0f,1f,1f,0f,0f,0f},{0.30f,0.97f,1.05f,-2f,0f,0f},{0.62f,0.97f,1.05f,2f,0f,0f},
         {0.82f,1.01f,0.99f,0f,0f,0f},{1f,1f,1f,0f,0f,0f}},
        /* 13 揉眼睛 */
        {{0f,1f,1f,0f,0f,0f},{0.14f,1.02f,0.98f,-4f,-0.02f,0f},{0.28f,1.02f,0.98f,4f,0.02f,0f},
         {0.42f,1.02f,0.98f,-3f,-0.02f,0f},{0.56f,1.02f,0.98f,3f,0.02f,0f},
         {0.74f,0.99f,1.01f,0f,0f,0f},{1f,1f,1f,0f,0f,0f}},
        /* 14 吐泡泡 */
        {{0f,1f,1f,0f,0f,0f},{0.20f,0.95f,1.07f,0f,0f,-0.02f},{0.55f,1.03f,0.99f,0f,0f,0f},
         {0.80f,0.99f,1.02f,0f,0f,0f},{1f,1f,1f,0f,0f,0f}},
        /* 15 左右摇摆招手 */
        {{0f,1f,1f,0f,0f,0f},{0.18f,0.92f,1.09f,-12f,-0.06f,0f},{0.38f,0.92f,1.09f,12f,0.06f,0f},
         {0.58f,0.94f,1.07f,-8f,-0.04f,0f},{0.78f,0.98f,1.02f,4f,0.02f,0f},{1f,1f,1f,0f,0f,0f}},
        /* 16 往旁边倒一下再立起来 */
        {{0f,1f,1f,0f,0f,0f},{0.25f,1.06f,0.94f,-18f,-0.10f,0.02f},{0.50f,1.05f,0.95f,-20f,-0.12f,0.03f},
         {0.72f,0.95f,1.06f,6f,0.04f,0f},{1f,1f,1f,0f,0f,0f}},
        /* 17 缩成一小团再弹开 */
        {{0f,1f,1f,0f,0f,0f},{0.22f,0.72f,0.72f,0f,0f,0.06f},{0.45f,0.74f,0.74f,0f,0f,0.06f},
         {0.68f,1.16f,1.16f,0f,0f,-0.04f},{0.85f,0.96f,1.04f,0f,0f,0f},{1f,1f,1f,0f,0f,0f}},
        /* 18 原地上下颠两下 */
        {{0f,1f,1f,0f,0f,0f},{0.14f,0.90f,1.12f,0f,0f,0.02f},{0.28f,1.04f,0.97f,0f,0f,-0.10f},
         {0.42f,1.02f,0.99f,0f,0f,0f},{0.56f,0.90f,1.12f,0f,0f,0.02f},{0.70f,1.04f,0.97f,0f,0f,-0.08f},
         {0.85f,0.99f,1.01f,0f,0f,0f},{1f,1f,1f,0f,0f,0f}},
        /* 19 慢慢吸一口气再呼出 */
        {{0f,1f,1f,0f,0f,0f},{0.35f,0.94f,1.14f,0f,0f,-0.03f},{0.55f,0.94f,1.15f,0f,0f,-0.03f},
         {0.80f,1.05f,0.96f,0f,0f,0f},{1f,1f,1f,0f,0f,0f}},
        /* 20 横向滑步（往一边挪一下） */
        {{0f,1f,1f,0f,0f,0f},{0.25f,1.05f,0.96f,-5f,-0.16f,0f},{0.55f,1.04f,0.97f,-4f,-0.30f,0f},
         {0.80f,0.97f,1.03f,2f,-0.30f,0f},{1f,1f,1f,0f,0f,0f}},
    };
    private static final float[] CLIP_DUR = {0.9f, 1.6f, 1.0f, 1.4f, 0.9f, 0.9f, 1.4f, 1.5f, 1.0f, 0.9f, 1.1f, 1.8f, 1.2f, 1.3f,
                                             1.3f, 1.5f, 1.2f, 1.2f, 1.9f, 1.4f};

    private void playClip(int i) {
        if (i == CLIPS.length - 1) bubbleUntil = SystemClock.uptimeMillis() + 1300L;
        clip = i;
        clipStart = SystemClock.uptimeMillis();
        clipDur = i < CLIP_DUR.length ? CLIP_DUR[i] : 1f;
    }

    /* 加权表：权重越大的越常演 */
    private static final int[] CLIP_WEIGHT = {3, 4, 2, 1, 4, 2, 2, 2, 1, 1, 1, 2, 2, 2, 2, 2, 2, 3, 2, 2};

    public void playRandomClip() {
        int total = 0;
        for (int w : CLIP_WEIGHT) total += w;
        int pick = rnd.nextInt(total);
        for (int i = 0; i < CLIP_WEIGHT.length && i < CLIPS.length; i++) {
            pick -= CLIP_WEIGHT[i];
            if (pick < 0) { playClip(i); return; }
        }
        playClip(0);
    }

    private void waking() {
        sleeping = false;
        lastAct = SystemClock.uptimeMillis();
    }

    /** 随机往左或右瞄一眼 */
    public void glance() {
        glanceTo = (rnd.nextFloat() < 0.5f ? -1f : 1f);
        glanceUntil = SystemClock.uptimeMillis() + 900L;
    }

    /* ---------------- 触摸（只有分窗模式走这里；合窗模式由 PetPad 接） ---------------- */
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        return gestures.onTouchEvent(e, sink);
    }

    /* ---------------- 绘制 ---------------- */
    @Override
    protected void onDraw(Canvas c) {
        if (paused) return;                 /* 锁屏：一帧都不画 */
        long now = SystemClock.uptimeMillis();
        long deltaMs = now - lastFrame;
        float dt = Math.min(0.05f, deltaMs / 1000f);
        if (dt < 0f) dt = 0f;
        /* 实测帧率（滚动 30 帧），用于「状态信息」里自己看卡不卡 */
        if (deltaMs > 0 && deltaMs < 600) {
            fpsWin[fpsIdx] = 1000f / deltaMs;
            fpsIdx = (fpsIdx + 1) % fpsWin.length;
            if (fpsFill < fpsWin.length) fpsFill++;
            float sum = 0f;
            for (int i = 0; i < fpsFill; i++) sum += fpsWin[i];
            fps = sum / Math.max(1, fpsFill);
        }
        lastFrame = now;
        time += dt;

        /* 弹簧：被戳后先陷后弹 */
        squashV += (-squash * 58f - squashV * 7.2f) * dt;
        squash += squashV * dt;
        if (blush > 0f) blush = Math.max(0f, blush - dt * 0.5f);

        /* 眨眼 */
        if (now > nextBlink) { blinkUntil = now + 110L; nextBlink = now + 2800L + rnd.nextInt(3600); }
        blink = now < blinkUntil ? 0.12f : Math.min(1f, blink + dt * 9f);

        /* 自己犯困：被摸过、在演动作、在走动的时候都不睡 */
        if (autoSleep && !sleeping && clip < 0 && now - lastAct > SLEEP_AFTER) setSleeping(true);

        /* 张望：一段时间就瞄一眼 */
        if (now < glanceUntil) {
            gazeX = glanceTo;
        } else {
            if (gazeX != 0f) gazeX = 0f;
            if (now > nextGlance) { nextGlance = now + 3500L + rnd.nextInt(6000); glance(); }
        }

        float w = getWidth(), h = getHeight();
        float R = Math.min(w, h) * 0.33f;
        float cx = w * 0.5f, cy = h * 0.60f;

        c.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);

        /* 基础呼吸 + 轻微摇摆 */
        float sway = (float) Math.sin(time * 1.15f) * 2.2f;
        float swayX = (float) Math.sin(time * 0.85f) * R * 0.03f;
        float breathe = 1f + (float) Math.sin(time * 1.6f) * 0.016f;
        float sx = breathe, sy = 2f - breathe, rot = sway + tilt, dx = swayX, dy = 0f;

        /* 关键帧动画叠加 */
        if (clip >= 0) {
            long el = now - clipStart;
            float pr = el / (clipDur * 1000f);
            if (pr >= 1f) { clip = -1; }
            else {
                float[][] fr = CLIPS[clip];
                int k = 0;
                while (k < fr.length - 2 && fr[k + 1][0] < pr) k++;
                float t0 = fr[k][0], t1 = fr[k + 1][0];
                float u = t1 <= t0 ? 0f : (pr - t0) / (t1 - t0);
                sx *= lerp(fr[k][1], fr[k + 1][1], u);
                sy *= lerp(fr[k][2], fr[k + 1][2], u);
                rot += lerp(fr[k][3], fr[k + 1][3], u);
                dx += lerp(fr[k][4], fr[k + 1][4], u) * R;
                dy += lerp(fr[k][5], fr[k + 1][5], u) * R;
            }
        } else if (!sleeping && now > nextClip) {
            playRandomClip();
            nextClip = now + 1800L + rnd.nextInt(3200);
        }

        /* 转到大角度时整体缩小，保证不会被窗口边缘切掉 */
        double rad = Math.toRadians(rot);
        float fit = 1f / (float) (Math.abs(Math.cos(rad)) + Math.abs(Math.sin(rad)));
        if (fit < 0.66f) fit = 0.66f;
        if (fit < 1f) { sx *= fit; sy *= fit; }

        c.save();
        c.translate(cx + dx, cy + dy);
        c.rotate(rot);
        c.scale(sx, sy);
        c.translate(-cx, -cy);

        /* 贴墙：把整只画旋转过去，看起来就是趴在墙上 */
        float targetFlat = pose == 0 ? 0f : (pose == 3 ? 0.55f : 0.85f);
        wallFlat += (targetFlat - wallFlat) * Math.min(1f, dt * 6f);
        c.save();
        /* 贴边：按"压扁之后肚子最外沿的位置"实时算，永远正好贴住屏幕边 */
        float flatY = 0.60f * R * (1f - wallFlat * 0.14f);   /* 天花板：y 轴压扁 */
        float flatS = 0.60f * R * (1f - wallFlat * 0.12f);   /* 侧壁：x 轴压扁，转到屏幕 y 上 */
        if (pose == 1) c.translate(-(w * 0.5f - flatS), 0f);
        else if (pose == 2) c.translate(w * 0.5f - flatS, 0f);
        else if (pose == 3) c.translate(0f, -(cy - flatY));

        /* 贴壁：屁股（平底）对着墙，脑袋朝屏幕中间；天花板：倒挂 */
        if (pose == 1) c.rotate(90f, cx, cy);        /* 左壁：底朝左 */
        else if (pose == 2) c.rotate(-90f, cx, cy);  /* 右壁：底朝右 */
        else if (pose == 3) c.rotate(180f, cx, cy);  /* 天花板：底朝上 */

        /* 蛄蛹：沿墙方向一伸一缩（约 0.75 秒一轮） */
        if (climbing) {
            double phase = ((SystemClock.uptimeMillis() - wriggleT0) % 750L) / 750.0 * Math.PI * 2;
            float wr = (float) Math.sin(phase);
            c.scale(1f + 0.08f * wr, 1f - 0.06f * wr, cx, cy);
        }
        if (wallFlat > 0.01f) {
            if (pose == 3) c.scale(1f + wallFlat * 0.08f, 1f - wallFlat * 0.14f, cx, cy);
            else c.scale(1f - wallFlat * 0.12f, 1f + wallFlat * 0.06f, cx, cy);
        }
        drawSlime(c, cx, cy, R, idx, time, dt);
        if (now < bubbleUntil) drawBubbles(c, cx, cy, R);
        c.restore();
        c.restore();

        /* zzz 画在姿态变换之外：贴墙 / 倒挂着睡的时候，Z 才不会跟着一起歪 */
        if (sleeping) {
            if (pose == 1) drawZzz(c, cx + R * 1.05f, cy - R * 0.45f);
            else if (pose == 2) drawZzz(c, cx - R * 1.05f, cy - R * 0.45f);
            else if (pose == 3) drawZzz(c, cx + R * 0.72f, cy + R * 0.95f);
            else drawZzz(c, cx + R * 0.7f, cy - R * 0.9f);
        }

        /* 刷新节奏：默认跟屏幕刷新率（60/120 都行），低配降到 30fps，睡着 15fps。
           合窗模式下不许自己排帧 —— 那会把 runnable 堆进永不执行的队列。 */
        if (!selfDriven) return;
        if (sleeping) postInvalidateDelayed(66L);
        else if (lowSpec) postInvalidateDelayed(33L);
        else postInvalidateOnAnimation();
    }

    private void drawSlime(Canvas c, float cx, float cy, float R, int i, float t, float dt) {
        int deep = DEEP[i], mid = MID[i], light = LIGHT[i], aura = AURA[i];

        /* 1. 外圈光晕（优先用烘好的位图，贴一次就完事） */
        ensureShaders(cx, cy, R, i);
        if (haloBmp != null) {
            haloDst.set(0f, 0f, getWidth(), getHeight());
            c.drawBitmap(haloBmp, null, haloDst, bmpPaint);
        } else {
            fill.setShader(shHalo);
            c.drawCircle(cx, cy + R * 0.06f, R * 1.75f, fill);
            fill.setShader(null);
        }

        /* 2. 身体路径 */
        buildBody(cx, cy, R);
        c.save();
        c.clipPath(body);

        fill.setShader(shBody);
        c.drawRect(cx - R * 2, cy - R * 2, cx + R * 2, cy + R * 2, fill);
        fill.setShader(null);

        /* 内部亮核 */
        fill.setShader(shCore);
        c.drawRect(cx - R * 2, cy - R * 2, cx + R * 2, cy + R * 2, fill);
        fill.setShader(null);

        drawTexture(c, cx, cy, R, i, t);

        /* 左上高光 */
        c.save();
        c.rotate(-35f, cx - R * 0.34f, cy - R * 0.46f);
        oval.set(cx - R * 0.64f, cy - R * 0.56f, cx - R * 0.04f, cy - R * 0.36f);
        fill.setShader(shHL);
        c.drawOval(oval, fill);
        fill.setShader(null);
        c.restore();

        /* 腮红：渐变复用，靠 translate 挪到两颊，浓度用 paint alpha 调 */
        if (blush > 0.01f) {
            fill.setShader(gBlush);
            fill.setAlpha(Math.round(255f * Math.min(1f, blush)));
            for (int s = -1; s <= 1; s += 2) {
                float bx = cx + s * R * 0.56f, by = cy + R * 0.16f;
                c.save();
                c.translate(bx, by);
                c.drawCircle(0, 0, R * 0.26f, fill);
                c.restore();
            }
            fill.setShader(null);
            fill.setAlpha(255);
        }
        c.restore();

        /* 3. 边缘透光 */
        c.save();
        c.clipPath(body);
        line.setStyle(Paint.Style.STROKE);
        line.setShader(null);
        line.setColor(withAlpha(light, 0.32f));
        line.setStrokeWidth(Math.max(3f, R * 0.15f));
        c.drawPath(body, line);
        line.setColor(0x33FFFFFF);
        line.setStrokeWidth(Math.max(1.2f, R * 0.022f));
        c.drawPath(body, line);
        c.restore();

        /* 4. 头顶装饰（只有草 / 风 / 雷） */
        drawCrown(c, cx, cy, R, i, t);

        /* 5. 发光眼 */
        drawEyes(c, cx, cy, R, i, t);
    }

    /** 只在元素/尺寸变化时重建渐变 */
    private void ensureShaders(float cx, float cy, float R, int i) {
        int w = getWidth(), h = getHeight();
        if (shIdx == i && shW == w && shH == h && Math.abs(shR - R) < 0.5f) return;
        shIdx = i; shW = w; shH = h; shR = R;
        int aura = AURA[i], mid = MID[i], light = LIGHT[i];
        shHalo = new RadialGradient(cx, cy + R * 0.06f, R * 1.75f,
                new int[]{withAlpha(aura, 0.26f), withAlpha(aura, 0.08f), withAlpha(aura, 0f)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        shBody = new LinearGradient(cx, cy - R * 0.92f, cx, cy + R * 0.60f,
                new int[]{withAlpha(mid, 0.95f), withAlpha(mid, 0.88f), withAlpha(light, 0.74f), withAlpha(light, 0.62f)},
                new float[]{0f, 0.40f, 0.76f, 1f}, Shader.TileMode.CLAMP);
        shCore = new RadialGradient(cx, cy + R * 0.24f, R * 0.92f,
                new int[]{withAlpha(light, 0.40f), withAlpha(light, 0f)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        shHL = new LinearGradient(0f, cy - R * 0.56f, 0f, cy - R * 0.36f,
                new int[]{0x9EFFFFFF, 0x00FFFFFF}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);

        /* 以下是装饰用的渐变：全部以 (0,0) 为原点建一次，画的时候靠 translate 挪过去，
           这样每帧不用再 new 一堆 Shader。 */
        gFire = new RadialGradient(0f, 0f, R * 0.28f,
                new int[]{0x57FFECA0, 0x33FF7C2A, 0x00B41E00},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        gBlush = new RadialGradient(0f, 0f, R * 0.26f,
                new int[]{withAlpha(0xFFFF6E96, 0.45f), withAlpha(0xFFFF6E96, 0f)},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        float eh = R * 0.168f;
        gEyeBody = new LinearGradient(0, -eh, 0, eh,
                new int[]{0xFFFDFFF7, withAlpha(EYE[i], 0.99f), withAlpha(CORE[i], 0.92f)},
                new float[]{0f, 0.42f, 1f}, Shader.TileMode.CLAMP);
        gLeaf = new LinearGradient(0, -R * 0.50f, 0, R * 0.16f,
                new int[]{0xF5A8E86E, 0xF0387C1C}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);
        gHorn = new LinearGradient(0, -R * 0.50f, 0, R * 0.16f,
                new int[]{0xFAFFFFFF, 0xEBE2FFFC, 0xB396E6E1}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
        gBolt = new LinearGradient(0, -R * 0.60f, 0, -R * 0.12f,
                new int[]{0xFFFFFFFF, 0xEBE2BEFF}, new float[]{0f, 1f}, Shader.TileMode.CLAMP);

        bakeHalo(w, h, cx, cy, R);
    }

    /** 把外圈光晕烘成半分辨率位图（半分辨率放大后肉眼无差，像素量少 4 倍） */
    private void bakeHalo(int w, int h, float cx, float cy, float R) {
        int bw = Math.max(1, w / 2), bh = Math.max(1, h / 2);
        try {
            if (haloBmp == null || haloW != bw || haloH != bh) {
                if (haloBmp != null) haloBmp.recycle();
                haloBmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
                haloW = bw; haloH = bh;
            }
            Canvas bc = new Canvas(haloBmp);
            bc.drawColor(0, android.graphics.PorterDuff.Mode.CLEAR);
            bc.scale(0.5f, 0.5f);
            Paint bp = new Paint(Paint.ANTI_ALIAS_FLAG);
            bp.setShader(shHalo);
            bc.drawCircle(cx, cy + R * 0.06f, R * 1.75f, bp);
        } catch (Throwable t) {
            haloBmp = null;      /* 内存不够就退回每帧画渐变 */
        }
    }

    /* ---- 身体轮廓查表：120 个点的三角函数只在类加载时算一遍，
       每帧只剩乘加（以前每帧 600 次 sin/cos/pow，中端机上就是那 2~3ms 的卡顿） ---- */
    private static final int BODY_N = 120;
    private static final float[] B_COS = new float[BODY_N + 1];
    private static final float[] B_S2 = new float[BODY_N + 1];
    private static final float[] B_C2 = new float[BODY_N + 1];
    private static final float[] B_S3 = new float[BODY_N + 1];
    private static final float[] B_C3 = new float[BODY_N + 1];
    private static final float[] B_POW = new float[BODY_N + 1];   /* |sin|^flatPow */
    private static final float[] B_RX = new float[BODY_N + 1];    /* 0.94*taper*spread */
    private static final float[] B_RY = new float[BODY_N + 1];    /* 0.98 / 0.60 */
    private static final float[] B_SQK = new float[BODY_N + 1];   /* -0.55 / 0.85 */
    private static final float[] B_SGN = new float[BODY_N + 1];

    static {
        for (int k = 0; k <= BODY_N; k++) {
            double th = k * 2.0 * Math.PI / BODY_N;
            double sn = Math.sin(th), cs = Math.cos(th);
            boolean top = sn < 0;
            float a = (float) Math.abs(sn);
            B_COS[k] = (float) cs;
            B_S2[k] = (float) Math.sin(th * 2);
            B_C2[k] = (float) Math.cos(th * 2);
            B_S3[k] = (float) Math.sin(th * 3);
            B_C3[k] = (float) Math.cos(th * 3);
            B_POW[k] = (float) Math.pow(a, top ? 1.0 : 0.80);
            float taper = top ? 1f - 0.10f * (float) Math.pow(a, 2.2) : 1f;
            float spread = top ? 1f : 1f + 0.06f * (float) Math.pow(a, 0.45);
            B_RX[k] = 0.94f * taper * spread;
            B_RY[k] = top ? 0.98f : 0.60f;
            B_SQK[k] = top ? -0.55f : 0.85f;
            B_SGN[k] = sn >= 0 ? 1f : -1f;
        }
    }

    private void buildBody(float cx, float cy, float R) {
        body.reset();
        final float tw = time * 1.5f;
        final float h2 = 0.014f + (float) Math.sin(time * 2.1f) * 0.006f;
        final float sq = squash * 0.10f + (float) Math.sin(time * 1.4f) * 0.03f;
        final float ct = (float) Math.cos(tw), st = (float) Math.sin(tw);
        final float c6 = (float) Math.cos(tw * 0.6f), s6 = (float) Math.sin(tw * 0.6f);
        for (int k = 0; k <= BODY_N; k++) {
            float w1 = h2 * (B_S2[k] * ct + B_C2[k] * st) + 0.006f * (B_S3[k] * c6 - B_C3[k] * s6);
            float squashF = 1f + sq * B_SQK[k];
            float rx = B_RX[k] * (1f + w1);
            float ry = B_RY[k] * squashF * (1f + w1 * 0.5f);
            float x = cx + B_COS[k] * R * rx;
            float y = cy + B_SGN[k] * B_POW[k] * R * ry;
            if (k == 0) body.moveTo(x, y); else body.lineTo(x, y);
        }
        body.close();
    }

    private void drawTexture(Canvas c, float cx, float cy, float R, int i, float t) {
        if (lowSpec) return;   /* 低配跳过纹理 */
        String key = KEY[i];
        if ("fire".equals(key)) {
            /* 四团火苗：渐变只建一次（原点），画的时候 translate 过去 */
            fill.setShader(gFire);
            for (int k = 0; k < 4; k++) {
                float bx = cx + (k - 1.5f) * R * 0.26f + (float) Math.sin(t * 0.5f) * R * 0.06f;
                float by = cy + R * (0.16f + (k % 2) * 0.22f);
                c.save();
                c.translate(bx, by);
                c.drawCircle(0, 0, R * 0.28f, fill);
                c.restore();
            }
            fill.setShader(null);
            return;
        }
        if ("grass".equals(key)) {
            fill.setShader(null);
            fill.setColor(withAlpha(0xFF2B6A1C, 0.22f));
            for (int k = 0; k < 8; k++) {
                double a = k * 2.1;
                float bx = cx + (float) Math.cos(a) * R * 0.46f;
                float by = cy + (float) Math.sin(a) * R * 0.28f + R * 0.08f;
                oval.set(bx - R * 0.055f, by - R * 0.038f, bx + R * 0.055f, by + R * 0.038f);
                c.drawOval(oval, fill);
            }
            fill.setColor(0x8CFFE878);
            for (int k = 0; k < 3; k++) {
                c.drawCircle(cx + (k - 1) * R * 0.30f, cy + R * 0.34f, R * 0.018f, fill);
            }
            return;
        }

        /* 其余元素：笔画先攒进复用的 tmpPath，最后一次画出来 */
        Path p2 = tmpPath;
        p2.reset();
        line.setShader(null);
        line.setStyle(Paint.Style.STROKE);

        if ("electro".equals(key)) {
            line.setColor(0x4DFFFFFF);
            line.setStrokeWidth(R * 0.020f);
            line.setStrokeCap(Paint.Cap.ROUND);
            for (int s = -1; s <= 1; s += 2) {
                p2.moveTo(cx + s * R * 0.40f, cy + R * 0.10f);
                p2.lineTo(cx + s * R * 0.52f, cy + R * 0.26f);
                p2.lineTo(cx + s * R * 0.44f, cy + R * 0.26f);
            }
            c.drawPath(p2, line);
        } else if ("cryo".equals(key)) {
            line.setColor(0x47FFFFFF);
            line.setStrokeWidth(R * 0.015f);
            line.setStrokeCap(Paint.Cap.BUTT);
            for (int k = 0; k < 3; k++) {
                float bx = cx - R * 0.36f + k * R * 0.36f, by = cy + R * 0.30f;
                for (int m = 0; m < 3; m++) {
                    double a = m * Math.PI / 3.0;
                    p2.moveTo(bx - (float) Math.cos(a) * R * 0.06f, by - (float) Math.sin(a) * R * 0.06f);
                    p2.lineTo(bx + (float) Math.cos(a) * R * 0.06f, by + (float) Math.sin(a) * R * 0.06f);
                }
            }
            c.drawPath(p2, line);
        } else if ("anemo".equals(key)) {
            line.setColor(0x47FFFFFF);
            line.setStrokeWidth(R * 0.020f);
            line.setStrokeCap(Paint.Cap.ROUND);
            for (int s = -1; s <= 1; s += 2) {
                oval.set(cx + s * R * 0.30f - R * 0.17f, cy + R * 0.22f - R * 0.17f,
                        cx + s * R * 0.30f + R * 0.17f, cy + R * 0.22f + R * 0.17f);
                p2.addArc(oval, s > 0 ? 86f : -86f, 140f);
            }
            c.drawPath(p2, line);
        } else if ("geo".equals(key)) {
            line.setColor(0x38786034);
            line.setStrokeWidth(R * 0.014f);
            line.setStrokeCap(Paint.Cap.BUTT);
            p2.moveTo(cx - R * 0.44f, cy + R * 0.36f); p2.lineTo(cx - R * 0.26f, cy + R * 0.44f);
            p2.moveTo(cx + R * 0.28f, cy + R * 0.38f); p2.lineTo(cx + R * 0.44f, cy + R * 0.30f);
            p2.moveTo(cx - R * 0.06f, cy + R * 0.44f); p2.lineTo(cx + R * 0.04f, cy + R * 0.36f);
            c.drawPath(p2, line);
        } else {
            line.setColor(0x33FFFFFF);
            line.setStrokeWidth(R * 0.018f);
            line.setStrokeCap(Paint.Cap.ROUND);
            for (int s = -1; s <= 1; s += 2) {
                p2.moveTo(cx + s * R * 0.18f, cy + R * 0.42f);
                p2.quadTo(cx + s * R * 0.42f, cy + R * 0.30f, cx + s * R * 0.60f, cy + R * 0.42f);
            }
            c.drawPath(p2, line);
            line.setStrokeWidth(R * 0.011f);
            line.setColor(0x57FFFFFF);
            for (int k = 0; k < 4; k++) {
                double ph = t * 0.6 + k * 1.9;
                float bx = cx + (float) Math.cos(ph) * R * 0.34f;
                float by = cy + (float) Math.sin(ph * 1.4) * R * 0.20f + R * 0.14f;
                c.drawCircle(bx, by, R * (0.042f + k * 0.008f), line);
            }
        }
    }

    private void drawCrown(Canvas c, float cx, float cy, float R, int i, float t) {
        String key = KEY[i];
        float topY = cy - R * 0.90f;
        float bob = (float) Math.sin(t * 1.3f) * R * 0.012f;

        if ("grass".equals(key)) {
            c.save();
            c.translate(cx + R * 0.02f, topY + R * 0.40f + bob);
            c.rotate(-19f + (float) Math.sin(t) * 3f);
            c.scale(0.82f, 0.82f);
            Path leaf = tmpPath;
            leaf.reset();
            leaf.moveTo(0, R * 0.16f);
            leaf.cubicTo(-R * 0.14f, R * 0.02f, -R * 0.20f, -R * 0.24f, -R * 0.06f, -R * 0.50f);
            leaf.cubicTo(R * 0.16f, -R * 0.36f, R * 0.26f, -R * 0.06f, 0, R * 0.16f);
            fill.setShader(gLeaf);
            c.drawPath(leaf, fill);
            fill.setShader(null);
            line.setShader(null); line.setStyle(Paint.Style.STROKE);
            line.setColor(0x66E8FFC4); line.setStrokeWidth(R * 0.012f);
            c.drawPath(leaf, line);
            c.restore();
            /* 小花 */
            c.save();
            c.translate(cx - R * 0.26f, topY + R * 0.30f + bob);
            for (int k = 0; k < 5; k++) {
                c.save();
                c.rotate(k * 72f);
                fill.setShader(null);
                fill.setColor(0xF5FFFAE0);
                oval.set(-R * 0.046f, -R * 0.106f, R * 0.046f, -R * 0.014f);
                c.drawOval(oval, fill);
                c.restore();
            }
            fill.setColor(0xFFFFD76A);
            c.drawCircle(0, 0, R * 0.040f, fill);
            c.restore();

        } else if ("anemo".equals(key)) {
            for (int s = -1; s <= 1; s += 2) {
                c.save();
                c.translate(cx + s * R * 0.36f, topY + R * 0.36f);
                c.rotate(s * -15f);
                Path horn = tmpPath;
                horn.reset();
                horn.moveTo(0, R * 0.16f);
                horn.cubicTo(s * R * 0.16f, -R * 0.14f, s * R * 0.26f, -R * 0.30f, s * R * 0.28f, -R * 0.50f);
                horn.cubicTo(s * R * 0.44f, -R * 0.26f, s * R * 0.26f, 0, 0, R * 0.16f);
                fill.setShader(gHorn);
                c.drawPath(horn, fill);
                fill.setShader(null);
                c.restore();
            }
            line.setShader(null); line.setStyle(Paint.Style.STROKE);
            line.setColor(0x80FFFFFF); line.setStrokeWidth(R * 0.035f);
            line.setStrokeCap(Paint.Cap.ROUND);
            for (int s = -1; s <= 1; s += 2) {
                RectF r2 = new RectF(cx + s * R * 0.58f - R * 0.13f, cy + R * 0.06f - R * 0.13f,
                        cx + s * R * 0.58f + R * 0.13f, cy + R * 0.06f + R * 0.13f);
                c.drawArc(r2, s > 0 ? 31f : 138f, 110f, false, line);
            }

        } else if ("electro".equals(key)) {
            c.save();
            c.translate(cx, topY + R * 0.32f + bob);
            line.setShader(null); line.setStyle(Paint.Style.STROKE);
            line.setColor(0xE6F8EEFF); line.setStrokeWidth(R * 0.020f);
            c.drawLine(0, 0, 0, -R * 0.22f, line);
            Path bolt = tmpPath;
            bolt.reset();
            bolt.moveTo(-R * 0.02f, -R * 0.60f);
            bolt.lineTo(R * 0.10f, -R * 0.32f);
            bolt.lineTo(R * 0.02f, -R * 0.32f);
            bolt.lineTo(R * 0.07f, -R * 0.12f);
            bolt.lineTo(-R * 0.08f, -R * 0.36f);
            bolt.lineTo(0, -R * 0.36f);
            bolt.close();
            fill.setShader(gBolt);
            c.drawPath(bolt, fill);
            fill.setShader(null);
            fill.setColor(0xF5FFFFFF);
            c.drawCircle(0, -R * 0.18f, R * 0.05f, fill);
            c.restore();
            /* 肩上的雷纹 */
            line.setShader(null); line.setStyle(Paint.Style.STROKE);
            line.setColor(0x66FFFFFF); line.setStrokeWidth(R * 0.030f);
            line.setStrokeCap(Paint.Cap.ROUND);
            Path side = tmpPath;
            side.reset();
            for (int s = -1; s <= 1; s += 2) {
                side.moveTo(cx + s * R * 0.64f, cy - R * 0.50f);
                side.lineTo(cx + s * R * 0.56f, cy - R * 0.30f);
                side.lineTo(cx + s * R * 0.66f, cy - R * 0.26f);
            }
            c.drawPath(side, line);
        }
    }

    private void drawEyes(Canvas c, float cx, float cy, float R, int i, float t) {
        int core = CORE[i];

        /* 睡着：闭眼，画两道向下弯的眼缝 */
        if (sleeping) {
            float exS = R * 0.30f, eyS = cy + R * 0.07f;
            line.setShader(null);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(Math.max(2f, R * 0.030f));
            line.setStrokeCap(Paint.Cap.ROUND);
            line.setColor(0xD93A2E44);
            Path lids = tmpPath;
            lids.reset();
            for (int s2 = -1; s2 <= 1; s2 += 2) {
                float px = cx + s2 * exS;
                lids.moveTo(px - R * 0.095f, eyS);
                lids.quadTo(px, eyS + R * 0.070f, px + R * 0.095f, eyS);
            }
            c.drawPath(lids, line);
            return;
        }
        float ex = R * 0.295f, ey = cy + R * 0.005f;
        float w = R * 0.104f, h = R * 0.168f;
        for (int s = -1; s <= 1; s += 2) {
            float px = cx + s * ex + gazeX * R * 0.05f;
            float py = ey + gazeY * R * 0.05f;
            c.save();
            c.translate(px, py);
            c.rotate(s * 7f);
            c.scale(1f, Math.max(0.10f, blink));

            /* 1. 很淡的外发光，只为了让眼睛亮一点，不糊形状 */
            fill.setShader(null);
            fill.setColor(withAlpha(core, 0.13f));
            oval.set(-w * 1.55f, -h * 1.35f, w * 1.55f, h * 1.35f);
            c.drawOval(oval, fill);

            /* 2. 眼窝：比身体深一点的底色，把形状压出来 */
            fill.setColor(withAlpha(core, 0.55f));
            oval.set(-w, -h, w, h);
            c.drawOval(oval, fill);

            /* 3. 眼球：上白下彩（渐变按元素缓存） */
            fill.setShader(gEyeBody);
            oval.set(-w * 0.82f, -h * 0.86f, w * 0.82f, h * 0.86f);
            c.drawOval(oval, fill);
            fill.setShader(null);

            /* 4. 深色描边：给眼睛一个清楚的边界 */
            line.setShader(null);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(Math.max(1.2f, R * 0.016f));
            line.setColor(withAlpha(core, 0.85f));
            oval.set(-w * 0.82f, -h * 0.86f, w * 0.82f, h * 0.86f);
            c.drawOval(oval, line);

            /* 5. 高光 */
            fill.setShader(null);
            fill.setColor(0xFFFFFFFF);
            oval.set(-w * 0.52f, -h * 0.62f, -w * 0.06f, -h * 0.20f);
            c.drawOval(oval, fill);
            fill.setColor(0xCCFFFFFF);
            oval.set(w * 0.08f, h * 0.18f, w * 0.34f, h * 0.38f);
            c.drawOval(oval, fill);

            c.restore();
        }
    }

    private void drawBubbles(Canvas c, float cx, float cy, float R) {
        float ph = (time * 0.9f) % 1f;
        for (int k = 0; k < 3; k++) {
            float t2 = (ph + k * 0.33f) % 1f;
            float bx = cx + R * (0.30f + 0.10f * k) + (float) Math.sin((time + k) * 2f) * R * 0.05f;
            float by = cy - R * 0.55f - t2 * R * 0.55f;
            float rr = R * (0.055f + 0.02f * k) * (1f - t2 * 0.3f);
            fill.setShader(null);
            fill.setColor(0x55FFFFFF);
            c.drawCircle(bx, by, rr, fill);
            line.setShader(null);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(R * 0.012f);
            line.setColor(0x99FFFFFF);
            c.drawCircle(bx, by, rr, line);
        }
    }

    private void drawZzz(Canvas c, float x, float y) {
        fill.setShader(null);
        fill.setColor(0xCCFFFFFF);
        fill.setTextSize(Math.max(12f, getWidth() * 0.10f));
        fill.setTextAlign(Paint.Align.CENTER);
        float ph = (time * 40f) % 60f;
        c.drawText("z", x + ph * 0.3f, y - ph, fill);
        fill.setTextSize(Math.max(9f, getWidth() * 0.07f));
        fill.setColor(0x99FFFFFF);
        c.drawText("z", x + 12f + ph * 0.25f, y - ph * 0.7f + 8f, fill);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static int withAlpha(int color, float a) {
        int al = Math.round(Color.alpha(color) * a);
        if (al < 0) al = 0; if (al > 255) al = 255;
        return (al << 24) | (color & 0x00FFFFFF);
    }
}
