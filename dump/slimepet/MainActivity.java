package com.lin.slimepet;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private static final int REQ_NOTI = 21;
    private TextView status;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(44), dp(22), dp(24));
        root.setBackgroundColor(0xFF0B0F17);

        TextView title = new TextView(this);
        title.setText("琳");
        title.setTextSize(36f);
        title.setTextColor(0xFFEAF2FF);
        title.setGravity(Gravity.CENTER);

        TextView sub = new TextView(this);
        sub.setText("会飘在所有 App 上方的史莱姆\n单击换形态 · 双击关页面 · 三击睡 · 长按出菜单");
        sub.setTextSize(13.5f);
        sub.setTextColor(0xFF8FA2BD);
        sub.setGravity(Gravity.CENTER);
        sub.setLineSpacing(0f, 1.45f);
        sub.setPadding(0, dp(14), 0, dp(24));

        Button go = mkBtn("让琳出来");
        go.setBackgroundColor(0xFF1D3B2A);
        go.setTextColor(0xFFB9F5C8);
        go.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startPet(); }
        });

        Button hide = mkBtn("让琳收起来");
        hide.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                stopService(new Intent(MainActivity.this, PetService.class));
                status.setText("琳回去休息了");
            }
        });

        Button shizuku = mkBtn("授权 Shizuku（关机功能要用）");
        shizuku.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (ShizukuShell.canRun()) { status.setText("Shizuku 已授权，菜单里的「关机」可以用"); return; }
                if (!ShizukuShell.needsPermission()) {
                    status.setText("没连上 Shizuku：先装 Shizuku App，并让它启动起来（无线调试或电脑激活一次）");
                    return;
                }
                ShizukuShell.requestPermission();
                status.setText("弹窗里点「允许」，然后回来点刷新");
            }
        });

        Button access = mkBtn("开无障碍（双击关页面要用）");
        access.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (PetAccess.ready()) { status.setText("无障碍已开：双击琳就能替你关掉当前页面"); return; }
                try {
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    status.setText("在列表里找到「琳」，把开关打开，然后回来点「刷新状态」");
                } catch (Exception e) {
                    status.setText("跳不过去，去 系统设置 → 无障碍 里手动打开「琳」");
                }
            }
        });

        /* ---- 数量 ---- */
        EditText countIn = new EditText(this);
        countIn.setHint("宠物数量（比如 3）");
        countIn.setHintTextColor(0xFF5C6C82);
        countIn.setTextColor(0xFFDCE8F8);
        countIn.setTextSize(14f);
        countIn.setSingleLine(true);
        countIn.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        try { countIn.setText(String.valueOf(getSharedPreferences("slimepet", MODE_PRIVATE).getInt("count", 1))); } catch (Exception ignored) {}
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(12);
        countIn.setLayoutParams(clp);

        Button applyCount = mkBtn("应用数量");
        applyCount.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                int n = 1;
                try { n = Integer.parseInt(countIn.getText().toString().trim()); } catch (Exception ignored) {}
                if (n < 1) n = 1;
                if (n > 60) n = 60;
                getSharedPreferences("slimepet", MODE_PRIVATE).edit().putInt("count", n).apply();
                Intent si = new Intent(MainActivity.this, PetService.class);
                si.setAction(PetService.ACTION_SET_COUNT);
                try { startService(si); } catch (Exception ignored) {}
                status.setText("数量设为 " + n + " 只" + (n > 3 ? "（超过 3 只自动进低配）" : ""));
            }
        });

        /* ---- 物理与行为 ---- */
        Button quoteBtn = mkBtn("名言（间隔 / 停留 / 历史）");
        quoteBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, QuoteActivity.class)); }
        });

        Button physBtn = mkBtn("物理 / 行为设置");
        physBtn.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(MainActivity.this, PhysicsActivity.class)); }
        });

        /* ---- AI 接口：服务商可换（DeepSeek / Ollama Cloud / 任何 OpenAI 兼容端点） ---- */
        final android.content.SharedPreferences sp =
                getSharedPreferences("slimepet", MODE_PRIVATE);

        final EditText baseIn = aiField(sp.getString("ai_base", DEFAULT_BASE), "接口地址（例如 https://ollama.com/v1）", 14);
        final EditText modelIn = aiField(sp.getString("ai_model", DEFAULT_MODEL), "模型名（例如 deepseek-v4.1-flash）", 12);
        final EditText keyIn = aiField(sp.getString("api_key", ""), "API Key（Ollama Cloud 在 ollama.com/settings/keys 生成）", 16);

        Button saveKey = mkBtn("保存 AI 设置");
        saveKey.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                sp.edit()
                        .putString("ai_base", baseIn.getText().toString().trim())
                        .putString("ai_model", modelIn.getText().toString().trim())
                        .putString("api_key", keyIn.getText().toString().trim())
                        .apply();
                status.setText("已保存：\n" + chatUrl(baseIn.getText().toString())
                        + "\n模型 " + modelIn.getText().toString().trim());
            }
        });

        Button presetOllama = mkBtn("Ollama Cloud 预设");
        presetOllama.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                baseIn.setText("https://ollama.com/v1");
                modelIn.setText("deepseek-v4.1-flash");
                status.setText("已填入 Ollama Cloud：https://ollama.com/v1\n"
                        + "模型默认 deepseek-v4.1-flash（中文顺、快）。\n"
                        + "别的也能用：gpt-oss:20b / gpt-oss:120b / kimi-k2.6 / glm-5.3-flash …\n"
                        + "点「查可用模型」能看到当前能用的全部名单。");
            }
        });

        Button presetDeepSeek = mkBtn("DeepSeek 预设");
        presetDeepSeek.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                baseIn.setText("https://api.deepseek.com");
                modelIn.setText("deepseek-chat");
                status.setText("已填入 DeepSeek 官方地址和模型");
            }
        });

        Button listModels = mkBtn("查可用模型");
        listModels.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String k = keyIn.getText().toString().trim();
                final String base = baseIn.getText().toString().trim();
                if (k.length() < 10) { status.setText("先在下面填 Key"); return; }
                status.setText("查询中…");
                new Thread(new Runnable() {
                    @Override public void run() {
                        final String res = fetchModels(base, k);
                        runOnUiThread(new Runnable() { @Override public void run() { status.setText(res); } });
                    }
                }).start();
            }
        });

        Button testKey = mkBtn("测试 AI 连接");
        testKey.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String k = keyIn.getText().toString().trim();
                final String base = baseIn.getText().toString().trim();
                final String model = modelIn.getText().toString().trim();
                if (k.length() < 10) { status.setText("先填 Key"); return; }
                if (model.length() == 0) { status.setText("先填模型名"); return; }
                sp.edit().putString("ai_base", base).putString("ai_model", model)
                        .putString("api_key", k).apply();
                status.setText("测试中…\n" + chatUrl(base) + "\n模型 " + model);
                new Thread(new Runnable() {
                    @Override public void run() {
                        final String res = testChat(base, model, k);
                        runOnUiThread(new Runnable() { @Override public void run() { status.setText(res); } });
                    }
                }).start();
            }
        });

        Button refresh = mkBtn("刷新状态");
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { refreshStatus(); }
        });

        status = new TextView(this);
        status.setTextSize(13f);
        status.setTextColor(0xFF9FB2C9);
        status.setGravity(Gravity.CENTER);
        status.setLineSpacing(0f, 1.5f);
        status.setPadding(dp(6), dp(20), dp(6), dp(6));

        root.addView(title);
        root.addView(sub);
        root.addView(go);
        root.addView(hide);
        root.addView(shizuku);
        root.addView(access);
        root.addView(countIn);
        root.addView(applyCount);
        root.addView(quoteBtn);
        root.addView(physBtn);

        TextView aiTitle = new TextView(this);
        aiTitle.setText("AI 说话（可选）");
        aiTitle.setTextSize(13f);
        aiTitle.setTextColor(0xFF7FE3FF);
        aiTitle.setPadding(0, dp(20), 0, 0);
        root.addView(aiTitle);
        root.addView(baseIn);
        root.addView(modelIn);
        root.addView(keyIn);
        root.addView(presetOllama);
        root.addView(presetDeepSeek);
        root.addView(listModels);
        root.addView(saveKey);
        root.addView(testKey);
        root.addView(refresh);
        TextView help = new TextView(this);
        help.setTextSize(12f);
        help.setTextColor(0xFF6E7F96);
        help.setLineSpacing(0f, 1.5f);
        help.setPadding(dp(2), dp(18), dp(2), dp(6));
        help.setText("怎么玩：\n"
                + "· 名言：到点说一条真名言（带出处），说过的不重复；间隔 / 停留 / 字号在「名言」页调\n"
                + "· 单击：换一种元素形态（不说话）\n"
                + "· 双击：关掉当前页面（替你按返回，要开无障碍）\n"
                + "· 三击：让她睡 / 叫醒\n"
                + "· 慢慢连戳三下：她会往旁边躲开（快点三下是睡觉）\n"
                + "· 拖动：随便放，甩出去会飞\n"
                + "· 长按：菜单（换形态、大小、说话、活动范围、多只、关机…）\n"
                + "· 碰到屏幕边会爬上去，爬到天花板再从另一边下来\n"
                + "· 站得太高 / 太低：物理设置里的「离底距离」拖一下\n"
                + "· 多只时会互相碰撞弹开（动量守恒），也可以自己画活动范围");
        root.addView(help);
        root.addView(status);

        ScrollView sc = new ScrollView(this);
        sc.addView(root);
        setContentView(sc);

        askNotification();
        refreshStatus();
    }

    @Override protected void onResume() { super.onResume(); refreshStatus(); }

    private Button mkBtn(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(14f);
        b.setAllCaps(false);
        b.setTextColor(0xFFDCE8F8);
        b.setBackgroundColor(0xFF17202E);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(9);
        b.setLayoutParams(lp);
        return b;
    }

    /* ================= AI 接口（OpenAI 兼容：DeepSeek / Ollama Cloud / 自建） ================= */
    static final String DEFAULT_BASE = "https://api.deepseek.com";
    static final String DEFAULT_MODEL = "deepseek-chat";

    private EditText aiField(String value, String hint, int topDp) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(0xFF5C6C82);
        e.setTextColor(0xFFDCE8F8);
        e.setTextSize(12.5f);
        e.setSingleLine(true);
        e.setText(value == null ? "" : value);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        e.setLayoutParams(lp);
        return e;
    }

    private static final String UA = "Lin/9.3 (Android; slimepet)";

    /** base 补成 /chat/completions 的完整地址（用户直接填完整地址也认） */
    static String chatUrl(String base) {
        String b = base == null ? "" : base.trim();
        if (b.length() == 0) b = DEFAULT_BASE;
        if (!b.startsWith("http://") && !b.startsWith("https://")) b = "https://" + b;
        while (b.endsWith("/")) b = b.substring(0, b.length() - 1);
        /* 只写了 https://ollama.com 的，替她补上 /v1 */
        if (b.equals("https://ollama.com")) b = "https://ollama.com/v1";
        if (b.endsWith("/chat/completions") || b.endsWith("/api/chat")) return b;
        return b + "/chat/completions";
    }

    /** 是不是 Ollama 系的端点（官方云 / 本机 11434）：它的规矩和 OpenAI 不完全一样 */
    static boolean isOllama(String base) {
        String u = chatUrl(base);
        return u.contains("ollama.com") || u.contains(":11434");
    }

    private static String modelsUrl(String base) {
        try {
            URL u = new URL(chatUrl(base));
            String host = u.getHost() == null ? "" : u.getHost();
            String origin = u.getProtocol() + "://" + host + (u.getPort() > 0 ? ":" + u.getPort() : "");
            if (host.contains("ollama.com") || u.getPort() == 11434) return origin + "/api/tags";
            return origin + "/models";
        } catch (Exception e) {
            return "https://api.deepseek.com/models";
        }
    }

    private static String head(String s, int n) {
        if (s == null) return "";
        s = s.replace("\n", " ");
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    private static String jsonEsc(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\\') sb.append("\\\\");
            else if (ch == '"') sb.append("\\\"");
            else if (ch == '\n') sb.append("\\n");
            else if (ch == '\r') sb.append("\\r");
            else if (ch == '\t') sb.append("\\t");
            else if (ch < 0x20) sb.append(' ');
            else sb.append(ch);
        }
        return sb.toString();
    }

    /**
     * 拼一个 chat/completions 的请求体。
     *
     * "Ollama Cloud 接口用不了"的真正原因在这里：ollama.com 上的模型（deepseek-v4.1-flash、
     * glm-5.3、kimi、gpt-oss …）默认先"思考"，思考出来的字也算 token；max_tokens 小的时候
     * 额度全被 reasoning 吃掉，content 回来是空字符串 —— 接口其实 200 成功。
     * 加 reasoning_effort:none 让它直接出正文。
     */
    static String chatBody(String base, String model, int maxTokens, boolean noThink,
                           String system, String user) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(jsonEsc(model)).append("\",")
          .append("\"max_tokens\":").append(maxTokens).append(',')
          .append("\"stream\":false");
        if (noThink) sb.append(",\"reasoning_effort\":\"none\"");
        sb.append(",\"messages\":[");
        if (system != null && system.length() > 0)
            sb.append("{\"role\":\"system\",\"content\":\"").append(jsonEsc(system)).append("\"},");
        sb.append("{\"role\":\"user\",\"content\":\"").append(jsonEsc(user)).append("\"}]}");
        return sb.toString();
    }

    /** 取 json 里 from 之后第一个 "key":"字符串" 的值；值是 null / 数字 / 对象就返回 null */
    static String jsonString(String json, int from, String key) {
        if (json == null) return null;
        String k = "\"" + key + "\"";
        int i = json.indexOf(k, Math.max(0, from));
        if (i < 0) return null;
        int c = json.indexOf(':', i + k.length());
        if (c < 0) return null;
        int p = c + 1;
        while (p < json.length() && Character.isWhitespace(json.charAt(p))) p++;
        if (p >= json.length() || json.charAt(p) != '"') return null;
        StringBuilder sb = new StringBuilder();
        for (int j = p + 1; j < json.length(); j++) {
            char ch = json.charAt(j);
            if (ch == '\\') {
                j++;
                if (j >= json.length()) break;
                char nx = json.charAt(j);
                if (nx == 'n') sb.append('\n');
                else if (nx == 't') sb.append('\t');
                else if (nx == 'r') sb.append(' ');
                else if (nx == 'u' && j + 4 < json.length()) {
                    try { sb.append((char) Integer.parseInt(json.substring(j + 1, j + 5), 16)); j += 4; }
                    catch (Exception ignored) { }
                } else sb.append(nx);
                continue;
            }
            if (ch == '"') break;
            sb.append(ch);
        }
        return sb.toString();
    }

    static String finishReason(String json) { return jsonString(json, 0, "finish_reason"); }

    /** 错误响应里的人话原因：Ollama 用 error，OpenAI 用 message */
    private static String briefError(String json) {
        String e = jsonString(json, 0, "error");
        if (e == null || e.length() == 0) e = jsonString(json, 0, "message");
        if (e == null || e.length() == 0) return "";
        return "（" + head(e, 120) + "）";
    }

    /** 从 OpenAI 兼容响应里取正文：choices[0].message.content（PetService 也用它） */
    static String extractContent(String json) {
        if (json == null) return null;
        int m = json.indexOf("\"message\"");
        int from = m < 0 ? 0 : m;
        String c = jsonString(json, from, "content");
        if (c != null && c.trim().length() > 0) return c.trim();
        /* 思考类模型偶尔把话全说进 reasoning，content 留空 */
        String r = jsonString(json, from, "reasoning");
        if (r != null && r.trim().length() > 0) return r.trim();
        String t = jsonString(json, from, "text");
        if (t != null && t.trim().length() > 0) return t.trim();
        return null;
    }

    private static String[] postJson(String url, String key, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(12000);
        c.setReadTimeout(25000);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", UA);
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setDoOutput(true);
        OutputStream os = c.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.flush();
        os.close();
        int code = c.getResponseCode();
        java.io.InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
        if (in == null) in = new java.io.ByteArrayInputStream(new byte[0]);
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String ln;
        while ((ln = br.readLine()) != null) sb.append(ln);
        br.close();
        return new String[]{String.valueOf(code), sb.toString()};
    }

    private static String testChat(String base, String model, String key) {
        boolean ollama = isOllama(base);
        try {
            String url = chatUrl(base);
            String[] r = postJson(url, key, chatBody(base, model, 200, ollama, null, "说一句话，20字以内"));
            int code = Integer.parseInt(r[0]);
            if ((code == 400 || code == 422) && ollama) {      /* 不认 reasoning_effort 的端点：去掉再来一次 */
                r = postJson(url, key, chatBody(base, model, 200, false, null, "说一句话，20字以内"));
                code = Integer.parseInt(r[0]);
            }
            if (code >= 200 && code < 300) {
                String content = extractContent(r[1]);
                if (content != null) return "✅ 连上了，她回：" + head(content, 60);
                if ("length".equals(finishReason(r[1])))
                    return "⚠️ HTTP 200，但回答被截断了（额度都被思考吃掉了）：\n" + head(r[1], 200);
                return "⚠️ HTTP 200 但没解析出内容：" + head(r[1], 200);
            }
            if (code == 401 || code == 403)
                return "❌ HTTP " + code + "：Key 不对或过期了，去 ollama.com/settings/keys 重新生成";
            if (code == 404) return "❌ HTTP 404：模型名不存在" + briefError(r[1]);
            return "❌ HTTP " + code + briefError(r[1]);
        } catch (Exception e) {
            return "❌ " + e.getClass().getSimpleName() + "：" + e.getMessage();
        }
    }

    private static String fetchModels(String base, String key) {
        try {
            String u = modelsUrl(base);
            HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
            c.setRequestMethod("GET");
            c.setConnectTimeout(12000);
            c.setReadTimeout(20000);
            c.setRequestProperty("Accept", "application/json");
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Authorization", "Bearer " + key);
            int code = c.getResponseCode();
            java.io.InputStream in = (code >= 200 && code < 300) ? c.getInputStream() : c.getErrorStream();
            if (in == null) in = new java.io.ByteArrayInputStream(new byte[0]);
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String ln;
            while ((ln = br.readLine()) != null) sb.append(ln);
            br.close();
            String txt = sb.toString();
            if (code < 200 || code >= 300) return "❌ HTTP " + code + briefError(txt);

            java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<String>();
            for (String marker : new String[]{"\"name\":\"", "\"id\":\""}) {
                int p = 0;
                while (names.size() < 60) {
                    int i = txt.indexOf(marker, p);
                    if (i < 0) break;
                    int s = i + marker.length();
                    int e = txt.indexOf('"', s);
                    if (e < 0) break;
                    String nm = txt.substring(s, e);
                    p = e + 1;
                    int endObj = txt.indexOf('}', e);
                    String chunk = endObj > 0 ? txt.substring(i, endObj) : "";
                    /* 只要会说话的：纯 embedding 的塞进来只会让人白填 */
                    if (chunk.contains("embedding") && !chunk.contains("completion")) continue;
                    names.add(nm);
                }
                if (!names.isEmpty()) break;
            }
            if (names.isEmpty()) return "连上了，但没读出模型名：" + head(txt, 200);
            StringBuilder out = new StringBuilder("可用模型（共 " + names.size() + "，填进上面第二个框）：\n");
            int n = 0;
            for (String s : names) {
                out.append("· ").append(s).append('\n');
                if (++n >= 24) break;
            }
            if (names.size() > 24) out.append("……");
            return out.toString();
        } catch (Exception e) {
            return "❌ " + e.getClass().getSimpleName() + "：" + e.getMessage();
        }
    }

    private void refreshStatus() {
        if (status == null) return;
        boolean overlay = Settings.canDrawOverlays(this);
        boolean acc = PetAccess.ready();
        boolean sz = ShizukuShell.canRun();
        boolean szPend = ShizukuShell.needsPermission();
        StringBuilder sb = new StringBuilder();
        sb.append(overlay ? "✅ 悬浮窗权限已开" : "❌ 悬浮窗权限未开（必须先开）");
        sb.append("\n");
        sb.append(sz ? "✅ Shizuku 已授权（菜单里的关机可用）"
                : (szPend ? "○ Shizuku 已连上，但还没授权" : "○ Shizuku 未连上（不影响日常使用）"));
        sb.append("\n");
        sb.append(acc ? "✅ 无障碍已开（双击替你关页面）" : "○ 无障碍未开（双击关页面要用）");
        String k = "";
        try { k = getSharedPreferences("slimepet", MODE_PRIVATE).getString("api_key", ""); } catch (Exception ignored) {}
        String m = "";
        try { m = getSharedPreferences("slimepet", MODE_PRIVATE).getString("ai_model", DEFAULT_MODEL); } catch (Exception ignored) {}
        sb.append("\n");
        sb.append(k.length() > 10
                ? "✅ AI 已配置（Key " + k.length() + " 位 · 模型 " + m + "）"
                : "○ 还没配 AI（不配也能用本地台词）");
        sb.append("\n");
        String ver = "";
        try { ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; } catch (Exception ignored) {}
        sb.append("版本 ").append(ver.length() > 0 ? ver : "?");
        sb.append("\n手势：单击换形态 · 双击关页面 · 三击睡/醒 · 长按菜单");
        status.setText(sb.toString());
    }

    private void startPet() {
        if (!Settings.canDrawOverlays(this)) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {}
            status.setText("请先打开「显示在其他应用上层」，再回来点一次");
            return;
        }
        Intent s = new Intent(this, PetService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(s);
        else startService(s);
        status.setText("琳出来了：能拖能甩 · 单击换形态 · 双击关页面 · 三击睡/醒 · 长按菜单");
    }

    private void askNotification() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTI);
        }
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
