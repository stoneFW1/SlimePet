package com.lin.slimepet;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/**
 * 只做一件事：替用户按下“返回”，用来实现“双击琳关掉当前页面”。
 * 不读取任何屏幕内容（canRetrieveWindowContent=false）。
 */
public class PetAccess extends AccessibilityService {

    private static PetAccess inst;
    /* 最近一次"界面有动静"的时间：用来判断刚才那下返回到底有没有生效 */
    private static long lastEventAt = 0L;
    private static String lastPkg = "";

    public static boolean ready() {
        return inst != null;
    }

    public static long lastEventAt() { return lastEventAt; }
    public static String lastPackage() { return lastPkg; }

    public static boolean pressBack() {
        PetAccess s = inst;
        if (s == null) return false;
        return s.performGlobalAction(GLOBAL_ACTION_BACK);
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        inst = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence p = event.getPackageName();
        if (p != null) lastPkg = p.toString();
        lastEventAt = SystemClock.uptimeMillis();
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        inst = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (inst == this) inst = null;
        super.onDestroy();
    }
}
