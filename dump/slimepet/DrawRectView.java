package com.lin.slimepet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** 画活动范围用的遮罩：拖出一个矩形，松手回调 */
public class DrawRectView extends View {

    public interface Done {
        void onRect(Rect r);
        void onCancel();
    }

    private final Paint dim = new Paint();
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF cur = new RectF();
    private float x0, y0;
    private boolean down = false;
    private Done cb;

    public DrawRectView(Context c) {
        super(c);
        dim.setColor(0x88000000);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(3f * c.getResources().getDisplayMetrics().density);
        line.setColor(0xFF7FE3FF);
        fill.setColor(0x227FE3FF);
        text.setColor(Color.WHITE);
        text.setTextSize(14f * c.getResources().getDisplayMetrics().density);
        text.setTextAlign(Paint.Align.CENTER);
    }

    public void setDone(Done d) { cb = d; }

    @Override
    protected void onDraw(Canvas c) {
        c.drawColor(0x00000000);
        c.drawRect(0, 0, getWidth(), getHeight(), dim);
        if (cur.width() > 0 && cur.height() > 0) {
            c.drawRect(cur, fill);
            c.drawRect(cur, line);
        }
        c.drawText("拖出一个框：史莱姆只在这个范围里活动", getWidth() / 2f, 80f, text);
        c.drawText("松手保存 · 按返回取消", getWidth() / 2f, 108f, text);
        if (down) postInvalidateOnAnimation();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                down = true; x0 = x; y0 = y;
                cur.set(x, y, x, y);
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                cur.set(Math.min(x0, x), Math.min(y0, y), Math.max(x0, x), Math.max(y0, y));
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                down = false;
                cur.set(Math.min(x0, x), Math.min(y0, y), Math.max(x0, x), Math.max(y0, y));
                if (cur.width() > 120 && cur.height() > 120 && cb != null) {
                    cb.onRect(new Rect((int) cur.left, (int) cur.top, (int) cur.right, (int) cur.bottom));
                } else if (cb != null) {
                    cb.onCancel();
                }
                return true;
        }
        return true;
    }
}
