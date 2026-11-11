package com.lin.slimepet;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** 手绘活动范围：随便画一条闭合线，史莱姆只能待在里面 */
public class DrawRegionView extends View {

    public interface Done {
        void onRegion(List<PointF> pts);
        void onCancel();
    }

    private final Paint dim = new Paint();
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final List<PointF> pts = new ArrayList<PointF>();
    private Done cb;
    private boolean drawing = false;

    public DrawRegionView(Context c) {
        super(c);
        dim.setColor(0x99000000);
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
        c.drawRect(0, 0, getWidth(), getHeight(), dim);
        if (!pts.isEmpty()) {
            path.reset();
            path.moveTo(pts.get(0).x, pts.get(0).y);
            for (int i = 1; i < pts.size(); i++) path.lineTo(pts.get(i).x, pts.get(i).y);
            if (!drawing && pts.size() > 2) path.close();
            if (pts.size() > 2) c.drawPath(path, fill);
            c.drawPath(path, line);
        }
        c.drawText("随手画一条闭合线，圈出她可以活动的范围", getWidth() / 2f, 80f, text);
        c.drawText("松手自动闭合 · 按返回退出", getWidth() / 2f, 108f, text);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                drawing = true;
                pts.clear();
                pts.add(new PointF(x, y));
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE: {
                PointF last = pts.isEmpty() ? null : pts.get(pts.size() - 1);
                if (last == null || Math.hypot(x - last.x, y - last.y) > 4f) {
                    pts.add(new PointF(x, y));
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
                drawing = false;
                if (pts.size() >= 8 && area(pts) > 40000) {
                    if (cb != null) cb.onRegion(new ArrayList<PointF>(pts));
                } else {
                    if (cb != null) cb.onCancel();
                }
                return true;
        }
        return true;
    }

    /** 鞋带公式算面积，太小的圈不要 */
    private static float area(List<PointF> p) {
        float a = 0;
        for (int i = 0; i < p.size(); i++) {
            PointF q = p.get(i), r = p.get((i + 1) % p.size());
            a += q.x * r.y - r.x * q.y;
        }
        return Math.abs(a) / 2f;
    }
}
