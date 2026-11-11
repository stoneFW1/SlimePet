package com.lin.slimepet;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * 通过 Shizuku 以 shell 身份执行命令（返回键 / 杀进程）。
 * 全部走反射，Shizuku 库没打包进来时也不会崩，只是变成不可用。
 */
public final class ShizukuShell {

    private static Class<?> szClass;
    private static boolean looked = false;
    private static boolean requested = false;

    private ShizukuShell() {}

    private static synchronized Class<?> sz() {
        if (!looked) {
            looked = true;
            try { szClass = Class.forName("rikka.shizuku.Shizuku"); } catch (Throwable ignored) { szClass = null; }
        }
        return szClass;
    }

    /** 库在、服务在、权限也有 */
    public static boolean canRun() {
        try {
            Class<?> c = sz();
            if (c == null) return false;
            Method ping = c.getMethod("pingBinder");
            if (!Boolean.TRUE.equals(ping.invoke(null))) return false;
            Method check = c.getMethod("checkSelfPermission");
            Object r = check.invoke(null);
            return r instanceof Integer && ((Integer) r) == 0;
        } catch (Throwable e) { return false; }
    }

    /** 服务在但没授权 */
    public static boolean needsPermission() {
        try {
            Class<?> c = sz();
            if (c == null) return false;
            Method ping = c.getMethod("pingBinder");
            if (!Boolean.TRUE.equals(ping.invoke(null))) return false;
            Method check = c.getMethod("checkSelfPermission");
            Object r = check.invoke(null);
            return r instanceof Integer && ((Integer) r) != 0;
        } catch (Throwable e) { return false; }
    }

    /** 弹 Shizuku 授权框（只弹一次） */
    public static void requestPermission() {
        if (requested) return;
        requested = true;
        try {
            Class<?> c = sz();
            if (c == null) return;
            final Class<?> listenerCls = Class.forName("rikka.shizuku.Shizuku$OnRequestPermissionResultListener");
            Object listener = Proxy.newProxyInstance(ShizukuShell.class.getClassLoader(),
                    new Class<?>[]{listenerCls}, new InvocationHandler() {
                        @Override public Object invoke(Object proxy, Method m, Object[] a) { return null; }
                    });
            Method add = c.getMethod("addRequestPermissionResultListener", listenerCls);
            add.invoke(null, listener);
            Method req = c.getMethod("requestPermission", int.class);
            req.invoke(null, 2048);
        } catch (Throwable ignored) {}
    }

    /** 执行一条 shell 命令，返回输出（读不到就返回空串） */
    public static String exec(String cmd) {
        try {
            Class<?> c = sz();
            if (c == null) return "";
            Method newProcess = c.getMethod("newProcess", String[].class, String[].class, String.class);
            Object obj = newProcess.invoke(null, new String[]{"sh", "-c", cmd}, null, null);
            if (!(obj instanceof Process)) return "";
            Process p = (Process) obj;
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String ln;
            long t0 = System.currentTimeMillis();
            while ((ln = br.readLine()) != null) {
                sb.append(ln).append('\n');
                if (System.currentTimeMillis() - t0 > 2500) break;
            }
            try { p.waitFor(); } catch (Throwable ignored) {}
            return sb.toString();
        } catch (Throwable e) { return ""; }
    }

    /** 当前前台 App 的包名（拿不到返回 null） */
    public static String topPackage() {
        String out = exec("dumpsys window | grep -E 'mCurrentFocus|mFocusedApp' | head -4");
        if (out == null || out.length() == 0) return null;
        for (String row : out.split("\n")) {
            int i = row.indexOf("u0 ");
            if (i < 0) i = row.indexOf(' ');
            int slash = row.indexOf('/');
            if (slash < 0) continue;
            int start = row.lastIndexOf(' ', slash);
            if (start < 0) start = i;
            String pkg = row.substring(start + 1, slash).trim();
            if (pkg.length() > 2 && pkg.indexOf('.') > 0 && pkg.indexOf(' ') < 0) return pkg;
        }
        return null;
    }
}
