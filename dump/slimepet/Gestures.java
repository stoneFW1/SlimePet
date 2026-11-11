package com.lin.slimepet;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

/**
 * 手势判定：单击 / 双击 / 三击 / 长按 / 拖动。
 *
 * 两种渲染方式（合窗、分窗）共用这一套逻辑：以前它长在 SlimeView 里，
 * 现在抽出来，谁接触摸就把自己当 Sink 递进来。
 */
public class Gestures {

    public interface Sink {
        boolean isSleeping();
        void onPress();                       /* 手指刚压下：即时反馈，别等 320ms */
        void onWake();
        void onTap();
        void onDoubleTap();
        void onTripleTap();
        void onLongPress();
        void onDrag(float dx, float dy);
        void onDragEnd(float vx, float vy);
    }

    private static final long TAP_WINDOW = 320L;
    private static final long LONG_PRESS = 600L;

    private final int slop;
    private final Handler h = new Handler(Looper.getMainLooper());

    private float downX, downY, lastX, lastY;
    private boolean down, dragging, longFired, downSleeping;
    private float vx, vy;
    private int pendingTaps;
    private Sink sink;

    private final Runnable longJob = new Runnable() {
        @Override public void run() {
            Sink s = sink;
            if (s == null || !down || dragging) return;
            longFired = true;
            if (downSleeping) { s.onWake(); return; }   /* 睡着时长按 = 叫醒，不弹菜单 */
            s.onLongPress();
        }
    };

    private final Runnable tapResolver = new Runnable() {
        @Override public void run() {
            Sink s = sink;
            int n = pendingTaps;
            pendingTaps = 0;
            if (s == null || n == 0) return;
            if (n == 1) { if (!s.isSleeping()) s.onTap(); }   /* 睡着时单击无效 */
            else if (n == 2) s.onDoubleTap();
            else s.onTripleTap();
        }
    };

    public Gestures(Context c) {
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
    }

    public void reset() {
        down = false; dragging = false; longFired = false;
        pendingTaps = 0;
        h.removeCallbacks(longJob);
        h.removeCallbacks(tapResolver);
    }

    public boolean onTouchEvent(MotionEvent e, Sink s) {
        sink = s;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                down = true; dragging = false; longFired = false;
                downSleeping = s.isSleeping();
                downX = lastX = e.getRawX();
                downY = lastY = e.getRawY();
                vx = vy = 0f;
                s.onPress();
                h.removeCallbacks(longJob);
                h.postDelayed(longJob, LONG_PRESS);
                return true;
            }

            case MotionEvent.ACTION_MOVE: {
                float rx = e.getRawX(), ry = e.getRawY();
                float dx = rx - lastX, dy = ry - lastY;
                lastX = rx; lastY = ry;
                if (!dragging && (Math.abs(rx - downX) + Math.abs(ry - downY)) > slop) {
                    dragging = true;
                    h.removeCallbacks(longJob);
                    pendingTaps = 0;                 /* 拖了就不算点 */
                    h.removeCallbacks(tapResolver);
                    if (downSleeping) s.onWake();    /* 拖她就算叫醒 */
                }
                if (dragging) {
                    vx = vx * 0.6f + dx * 0.4f;
                    vy = vy * 0.6f + dy * 0.4f;
                    s.onDrag(dx, dy);
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                down = false;
                h.removeCallbacks(longJob);
                if (dragging) {
                    dragging = false;
                    s.onDragEnd(vx, vy);
                    return true;
                }
                if (longFired) { pendingTaps = 0; h.removeCallbacks(tapResolver); return true; }
                /* 先记下来，等 TAP_WINDOW 过去再判是单击还是连击 */
                pendingTaps++;
                h.removeCallbacks(tapResolver);
                h.postDelayed(tapResolver, TAP_WINDOW);
                return true;
            }
        }
        return false;
    }
}
