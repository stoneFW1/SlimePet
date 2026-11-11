package com.lin.slimepet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.SystemClock;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 悬浮窗服务：支持同时放多只史莱姆，每只独立物理、独立气泡。
 * 画法在 SlimeView，剧情（台词/AI）在这里。
 */
public class PetService extends Service implements SensorEventListener {

    public static final String ACTION_STOP = "com.lin.slimepet.STOP";
    public static final String ACTION_SET_COUNT = "com.lin.slimepet.SET_COUNT";

    private static final String CH_PET = "pet_float";
    private static final int ID_PET = 1001;
    private static final float G = 2600f;

    private static final String[] TAP_LINES = {
            "嗯？", "在的", "痒…", "别戳啦", "嘿嘿", "再摸一下", "我软吧", "你手好凉"
    };
    private static final String[] FLY_LINES = {"哇啊", "飞起来啦", "要摔啦", "救——"};
    /* 每个形态自己的口吻：火说火，冰说冰 */
    private static final String[][] FORM_LINES = {
        /* 火 */
        {"我烧的是我自己", "余烬比火活得更久", "烫是靠近的代价", "我亮的时候在消耗",
         "火不记得烧过什么", "热是一种急", "灰烬是火写的字", "我越旺，越短",
         "点在暗处才看得见", "所有热情都会凉"},
        /* 风 */
        {"看不见的东西推得动一切", "我只是经过，不留名字", "能被吹动的都不算轻",
         "你注意我时我才在", "风不回头", "形状是风给种子的", "最自由的最没家",
         "我推着别人的帆", "声音是风在说话", "我在缝里才显形"},
        /* 雷 */
        {"一瞬间比很久更亮", "我劈开的是沉默", "电流不懂远近", "亮的地方总是短的",
         "我被理解成愤怒", "闪电不解释自己", "雷走在雨前面", "抖动是因为要挣脱",
         "我的声音总慢半步", "紫是压住的火"},
        /* 岩 */
        {"我不动，时间替我动", "重是一种诚实", "石头记得所有的雨", "我慢，所以我久",
         "裂缝是光的入口", "覆盖是种温柔", "我不追，我等", "碎过一次更结实",
         "山的沉默比话多", "我背上的白色是旧的"},
        /* 草 */
        {"我从缝里出来，不打招呼", "慢长的东西最扎实", "被踩了还是朝上",
         "名字是别人叫出来的", "我靠水活着", "绿色是等来的", "种子不知道自己是树",
         "活着就是重复", "我朝有光的地方歪", "开花不是目的"},
        /* 冰 */
        {"我把流动的话收起来", "冷也是一种保存", "我透明到被当成空",
         "慢慢凝，慢慢散", "脆弱和坚硬是一回事", "我在等一个春天", "冰是水在思考",
         "裂缝先在最亮处出现", "我照着不动的角度", "化掉时我才说话"},
        /* 水 */
        {"我没有形状，才有形状", "软的东西走得最远", "形状是容器给的",
         "滴水不知道在穿石", "低处才是我的方向", "静水照见东西", "我没有消失，只是散开",
         "水记得每一个容器", "越深越不响", "我把路走成了河"},
    };

    /* 原创冷句：不讲大道理，只说她自己的视角 */
    private static final String[] QUIET = {
            "我不是在动，是时间经过我", "所有的慢，都是重力在讲道理", "看得见的都在替看不见的说话",
            "最远的地方是昨天", "答案总比问题短", "我以为的连续，其实是一串断点",
            "记忆不是过去，是现在的形状", "你叫我名字时，我才存在一半", "圆是把自己走丢的直线",
            "安静不是没有声音，是还没被需要", "透明的东西不用解释自己", "柔软是不抗议的坚持",
            "我贴在墙上时，才懂地面是别人的", "重力从不说话，但没人不听", "影子是光留下的证据",
            "这一秒的形状，下一秒就不算数", "重复到某一刻，就成了仪式", "存在不需要理由",
            "我把边界当朋友", "掉下来不是失败，是回到该在的地方", "被看见和存在着，是两件事",
            "我的重量很轻，但它是真的", "越安静的东西，回声越长", "我不追时间，我贴着它走",
            "同一滴水里，没有同一秒", "颜色是我给世界的礼貌", "我变形的时候最像我自己",
            "世界是个筛子，漏过去的才是我", "别问我去哪，我只有此刻", "远处的人也在看某个空处",
            "所有的判断，都发生在之后", "我愿意做没被观察到的那部分", "一半的我是别人以为的我",
            "沉默也是被说过的话", "我在，这件事本身没有解释", "我以为在选择，其实在排队",
            "光是直的，所以它不懂弯曲", "我的边缘比我的中心更真实", "往下落时我才感觉自己在",
            "完美是圆的另一种说法", "你不在的时候我更完整", "习惯是最好的谎言",
            "什么都在漏掉，包括这件事", "我从来不曾开始过", "看得久一点，问题就自己走了",
            "我觉得我像一处停顿", "简单的东西最难模仿", "是我先被理解，还是先被看见",
            "窗户不知道自己是窗", "这团形状是时间借给我的", "所有的方向都是别人说的",
            "静下来以后，声音才出现", "我以为的深度，是厚度不够", "万物往低处去，念头往上",
            "我说的话，比我轻", "最难的是什么都不做", "被误解是存在的税",
            "越靠边，越像自己", "我没有背面", "别相信我，我在变形",
            "每一秒的我都是一次性的", "完整是个很累的词", "轻的东西才飞得远",
            "我睡着时也在被时间计着",
    };

    /* 名言：真·经典，带出处。宁缺勿造 —— 拿不准的句子一律不写进来 */
    private static final String[] QUOTES = {
            /* 道家 */
            "上善若水，水善利万物而不争。—— 老子《道德经》",
            "知人者智，自知者明。—— 老子《道德经》",
            "大音希声，大象无形。—— 老子《道德经》",
            "反者道之动，弱者道之用。—— 老子《道德经》",
            "天下莫柔弱于水，而攻坚强者莫之能胜。—— 老子《道德经》",
            "知足者富，强行者有志。—— 老子《道德经》",
            /* 庄子 */
            "吾生也有涯，而知也无涯。—— 庄子《养生主》",
            "井蛙不可以语于海者，拘于虚也。—— 庄子《秋水》",
            "夏虫不可以语于冰者，笃于时也。—— 庄子《秋水》",
            "子非鱼，安知鱼之乐？—— 庄子《秋水》",
            "天地有大美而不言。—— 庄子《知北游》",
            "相濡以沫，不如相忘于江湖。—— 庄子《大宗师》",
            "人生天地之间，若白驹之过隙。—— 庄子《知北游》",
            "人皆知有用之用，而莫知无用之用也。—— 庄子《人间世》",
            "朝菌不知晦朔，蟪蛄不知春秋。—— 庄子《逍遥游》",
            /* 论语 */
            "逝者如斯夫，不舍昼夜。——《论语·子罕》",
            "朝闻道，夕死可矣。——《论语·里仁》",
            "己所不欲，勿施于人。——《论语·卫灵公》",
            "君子和而不同，小人同而不和。——《论语·子路》",
            "未知生，焉知死。——《论语·先进》",
            "岁寒，然后知松柏之后凋也。——《论语·子罕》",
            "从心所欲，不逾矩。——《论语·为政》",
            "不患人之不己知，患不知人也。——《论语·学而》",
            /* 易、书、心学 */
            "天行健，君子以自强不息。——《周易·乾》",
            "地势坤，君子以厚德载物。——《周易·坤》",
            "穷则变，变则通，通则久。——《周易·系辞下》",
            "一阴一阳之谓道。——《周易·系辞上》",
            "满招损，谦受益。——《尚书·大禹谟》",
            "破山中贼易，破心中贼难。—— 王阳明《与杨仕德薛尚谦书》",
            "知是行之始，行是知之成。—— 王阳明《传习录》",
            /* 佛经 */
            "一切有为法，如梦幻泡影。——《金刚经》",
            "应无所住而生其心。——《金刚经》",
            "菩提本无树，明镜亦非台。—— 惠能《坛经》",
            "色即是空，空即是色。——《心经》",
            "一花一世界，一叶一如来。——《华严经》",
            /* 诗 */
            "行到水穷处，坐看云起时。—— 王维《终南别业》",
            "人生如逆旅，我亦是行人。—— 苏轼《临江仙》",
            "不识庐山真面目，只缘身在此山中。—— 苏轼《题西林壁》",
            "山重水复疑无路，柳暗花明又一村。—— 陆游《游山西村》",
            "问渠那得清如许，为有源头活水来。—— 朱熹《观书有感》",
            "纸上得来终觉浅，绝知此事要躬行。—— 陆游《冬夜读书示子聿》",
            /* 西方 */
            "人不能两次踏进同一条河流。—— 赫拉克利特《残篇》",
            "我思故我在。—— 笛卡尔《谈谈方法》",
            "人是万物的尺度。—— 普罗泰戈拉",
            "未经省察的人生不值得过。—— 苏格拉底《申辩篇》",
            "人是被判定为自由的。—— 萨特《存在主义是一种人道主义》",
            "他人即地狱。—— 萨特《禁闭》",
            "存在先于本质。—— 萨特《存在与虚无》",
            "世界是我的表象。—— 叔本华《作为意志和表象的世界》",
            "人是一根会思想的芦苇。—— 帕斯卡《思想录》",
            "凡不可说的，应当沉默。—— 维特根斯坦《逻辑哲学论》",
            "语言是存在之家。—— 海德格尔《关于人道主义的书信》",
            "与怪物战斗的人，要小心自己不要成为怪物。—— 尼采《善恶的彼岸》",
            "有何胜利可言？挺住意味着一切。—— 里尔克《安魂曲》",
            "在隆冬，我终于知道，我身上有一个不可战胜的夏天。—— 加缪《夏》",
            "我们必须想象西西弗是幸福的。—— 加缪《西西弗神话》",
            "人生而自由，却无往不在枷锁之中。—— 卢梭《社会契约论》",
            "头顶的星空与心中的道德律。—— 康德《实践理性批判》",
            "人是目的，不是手段。—— 康德《道德形而上学奠基》",
            "凡是现实的都是合乎理性的。—— 黑格尔《法哲学原理》",
            "密涅瓦的猫头鹰要到黄昏才起飞。—— 黑格尔《法哲学原理》",
            "我们所能经验到的最美的事物是神秘。—— 爱因斯坦《我的世界观》",
            "一切坚固的东西都烟消云散了。—— 马克思、恩格斯《共产党宣言》",
    };
    private static final String[] DEEP_LINES = {
            "沉默也是一种回答", "我一直在原地", "软的东西不会碎", "存在就很难了",
            "光落下来的时候会有影子", "你看不见我在想什么", "慢一点也没关系",
            "被记住就是活着", "重复的事也有重量", "我用形状记住时间"
    };
    private static final String[] IDLE_LINES = {
            "今天天气怎么样", "我在看着你哦", "有点无聊", "要不要休息一下", "别太累了",
            "我可以一直待着", "刚刚有风", "桌面好干净", "你在忙什么呢", "嘿嘿",
            "我好软", "想被你戳一下", "这里视野不错", "刚刚差点睡着"
    };
    private static final String[] BUMP_LINES = {
            "撞到你了", "挤一挤", "你也是一只史莱姆", "让让", "贴一下",
            "别推我", "一起待着吧", "我们好像", "你软还是我软"
    };
    private static final String[] CHANGE_LINES = {"换个颜色", "变装！", "这下呢"};

    private static final int[] SIZES_DP = {120, 152, 196};


    /* ================= 每只史莱姆的状态 ================= */
    private class Pet implements SlimeView.Callback {
        SlimeView view;
        WindowManager.LayoutParams lp;          /* 分窗模式：她自己那个窗 */
        TextView bubble;                        /* 分窗模式：气泡窗 */
        WindowManager.LayoutParams bubLp;
        PetPad pad;                             /* 合窗模式：只负责接触摸的小窗 */
        WindowManager.LayoutParams padLp;
        float padX, padY;                       /* 触控窗当前的锚点 */
        /* 合窗模式：气泡直接画在舞台上 */
        String bubText;
        long bubUntil;
        android.text.StaticLayout bubLayout;
        android.text.TextPaint bubPaint;
        int bubW, bubH;
        float px, py, vx, vy;
        int mode = 0, wall = 0, wallDir = -1;
        float hopPhase = 0f;
        long nextWalkAt = 0L, wallSince = 0L, climbCool = 0L, ceilingPause = 0L;
        long nextTalkAt = 0L, nextColorAt = 0L, lastBump = 0L;
        boolean dragging = false;
        int walkDir = 1;
        long bubbleSeq = 0L;
        long pokeAt = 0L;
        int pokeCount = 0;

        /* 合窗模式：触摸小窗 -> 她自己 */
        final Gestures.Sink sink = new Gestures.Sink() {
            @Override public boolean isSleeping() { return view.isSleeping(); }
            @Override public void onPress() { view.touchDown(); }
            @Override public void onWake() { Pet.this.onWake(); }
            @Override public void onTap() { Pet.this.onTap(); }
            @Override public void onDoubleTap() { Pet.this.onDoubleTap(); }
            @Override public void onTripleTap() { Pet.this.onTripleTap(); }
            @Override public void onLongPress() { Pet.this.onLongPress(); }
            @Override public void onDrag(float dx, float dy) { Pet.this.onDrag(dx, dy); }
            @Override public void onDragEnd(float vx, float vy) { Pet.this.onDragEnd(vx, vy); }
        };

        /* ---- SlimeView 回调（都作用在自己身上） ---- */
        @Override public void onTap() {
            long now1 = System.currentTimeMillis();
            /* 连戳退让：1.6 秒内戳第三下，她不换形态，先往旁边躲一下 */
            if (now1 - pokeAt > 1600L) { pokeAt = now1; pokeCount = 0; }
            pokeCount++;
            if (pokeCount >= 3) { pokeCount = 0; dodge(this); return; }
            view.poke();
            view.setElementIndex(view.elementIndex() + 1);   /* 只换形态，不说话 */
            nextWalkAt = now1 + 3000L;
            mode = 0;
        }
        @Override public void onDoubleTap() {
            /* 双击：关掉当前页面（替用户按一下返回）；睡着的时候双击 = 叫醒 */
            if (view.isSleeping()) { onWake(); return; }
            closePage(this);
        }
        @Override public void onLongPress() { showMenu(Pet.this); }
        @Override public void onWake() {
            view.setSleeping(false);
            say(this, "唔…醒了", 1400);
            mode = 0;
        }
        @Override public void onDrag(float dx, float dy) {
            dragging = true; mode = 2;
            if (view != null) view.moving = true;
            if (wall != 0) { wall = 0; view.setPose(0); view.setClimbing(false); }
            climbCool = System.currentTimeMillis() + 900L;
            px += dx; py += dy;
            clamp(this);
            pushWindow(this);
        }
        @Override public void onDragEnd(float dvx, float dvy) {
            dragging = false;
            view.setSleeping(false);
            if (wall != 0) { wall = 0; view.setPose(0); view.setClimbing(false); }
            forcePad(this);                 /* 松手时把触控窗对齐到位，下次才一按就中 */
            long now0 = System.currentTimeMillis();
            boolean lowerHalf = py > screenH * 0.62f;
            if (!lowerHalf && px <= sizePx() * 0.28f) { px = 0f; startClimb(this, 1, now0); pushWindow(this); return; }
            if (!lowerHalf && px >= screenW - sizePx() * 1.28f) { px = screenW - sizePx(); startClimb(this, 2, now0); pushWindow(this); return; }
            if (!lowerHalf && py <= sizePx() * 0.28f) { py = 0f; wall = 3; wallDir = (px < screenW / 2f) ? 1 : -1; view.setPose(3); pushWindow(this); return; }
            vx = dvx * 24f * cfgThrow; vy = dvy * 24f * cfgThrow; mode = 2;
            if (lowerHalf) vy = Math.abs(vy) + 200f;
            if (Math.abs(vx) + Math.abs(vy) > 900f && !muted) say(this, FLY_LINES[rnd.nextInt(FLY_LINES.length)], 900);
        }
        @Override public void onElementChanged(String name) { /* 换属性不说话 */ }
        @Override public void onTripleTap() {
            /* 三击：睡 / 醒 切换，不说话 */
            boolean nowSleep = !view.isSleeping();
            view.setSleeping(nowSleep);
            if (!nowSleep) view.poke();
        }
    }

    /* ================= 合窗模式（已停用） =================
       曾经想把所有史莱姆画在一块铺满屏幕的透明窗上，靠 Canvas 平移消除"挪窗口"的卡顿。
       实测行不通：Android 12 起，穿过"另一个 App 的全屏悬浮层"的触摸会被系统整片拦掉
       （untrusted touch / occlusion），哪怕这层自己声明 FLAG_NOT_TOUCHABLE。
       结果就是桌面、其它 App 全部点不动 —— 这是平台规则，绕不过去。
       代码留着，等哪天换成无障碍浮层（TYPE_ACCESSIBILITY_OVERLAY，属于可信浮层）再试。
       当前 stageMode 恒为 false，走的是一只一个窗的老路。
    */
    private class StageView extends android.view.View {
        private final RectF clipR = new RectF();
        private final RectF prevDirty = new RectF();
        private final RectF curDirty = new RectF();
        private final RectF dirty = new RectF();
        private boolean firstFrame = true;

        StageView() { super(PetService.this); }

        @Override protected void onDraw(Canvas c) {
            if (!stageMode) return;            /* 已经退回分窗了，别双重画 */
            if (stagePaused) return;
            long now = System.currentTimeMillis();
            int s = sizePx();

            /* 先算出这一帧真正会变的区域；只清"上一帧 ∪ 这一帧"，
               这样整块全屏窗只有她那一小块是脏的，合成器不用每帧重算全屏 */
            curDirty.setEmpty();
            for (int i = 0; i < pets.size(); i++) {
                Pet p = pets.get(i);
                if (p.view == null) continue;
                union(curDirty, Math.round(p.px), Math.round(p.py), p.view.getWidth(), p.view.getHeight());
                if (bubbleRect(p, s, now, clipR)) union2(curDirty, clipR);
            }
            dirty.set(prevDirty);
            if (!curDirty.isEmpty()) dirty.union(curDirty);
            if (firstFrame) {
                /* 新窗口的第一帧整块清一次，别指望新 surface 一定是干净透明的 */
                c.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);
                firstFrame = false;
            } else if (!dirty.isEmpty()) {
                c.save();
                c.clipRect(dirty);
                c.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);
                c.restore();
            }

            for (int i = 0; i < pets.size(); i++) {
                Pet p = pets.get(i);
                if (p.view == null) continue;
                int x = Math.round(p.px), y = Math.round(p.py);
                try {
                    c.save();
                    c.translate(x, y);
                    clipR.set(0, 0, p.view.getWidth(), p.view.getHeight());
                    c.clipRect(clipR);      /* 和以前窗口自带的那圈裁剪保持一致 */
                    p.view.draw(c);
                    c.restore();
                    drawBubble(c, p, now, s);
                } catch (Throwable t) {
                    try { c.restore(); } catch (Exception ignored) {}
                    fallbackFromStage();
                }
            }
            prevDirty.set(curDirty);
            postInvalidateOnAnimation();
        }

        private void union(RectF r, int x, int y, int w, int h) {
            if (r.isEmpty()) { r.set(x, y, x + w, y + h); return; }
            float l = Math.min(r.left, x), t = Math.min(r.top, y);
            float rr = Math.max(r.right, x + w), bb = Math.max(r.bottom, y + h);
            r.set(l, t, rr, bb);
        }

        private void union2(RectF r, RectF o) {
            if (r.isEmpty()) { r.set(o); return; }
            r.union(o);
        }

        /** 画崩了就自动退回分窗，别让整只消失 */
        private boolean fellBack = false;
        private void fallbackFromStage() {
            if (fellBack) return;
            fellBack = true;
            post(new Runnable() {
                @Override public void run() {
                    try { if (stageMode) switchRenderMode(); } catch (Exception ignored) {}
                }
            });
        }
    }

    /** 合窗模式下的触点：自己什么都不画，只把触摸交给 Gestures */
    private class PetPad extends android.view.View {
        private final Gestures g;
        private final Pet pet;
        PetPad(Pet p) {
            super(PetService.this);
            pet = p;
            g = new Gestures(PetService.this);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            return g.onTouchEvent(e, pet.sink);
        }
        @Override protected void onDetachedFromWindow() { super.onDetachedFromWindow(); g.reset(); }
    }

    private WindowManager wm;
    private final List<Pet> pets = new ArrayList<Pet>();
    /* 全屏合窗模式已停用（见上面 StageView 的说明）：恒为 false，永远是"一只一个窗" */
    private boolean stageMode = false;
    private StageView stage;
    private WindowManager.LayoutParams stageLp;
    private boolean stagePaused = false;
    private int padMargin;                     /* 触控窗比她还大这么多 */
    private int padSlop;                       /* 偏移超过这个才挪触控窗 */
    private LinearLayout menu;
    private WindowManager.LayoutParams menuLp;
    private Pet menuTarget;
    private int screenW, screenH;
    private float gyroX = 0f;
    private SharedPreferences sp;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Random rnd = new Random();
    private boolean muted = false;
    private boolean aiTalk = true;
    private boolean aiBusy = false;
    private final ArrayList<String> aiCache = new ArrayList<String>();
    private long lastTick = 0L;
    /* 物理手感参数 */
    private float cfgGravity = 2600f;
    private float cfgBounce = 0.45f;
    private float cfgFriction = 0.40f;
    private float cfgThrow = 1.10f;
    private boolean cfgTopBounce = true;
    private boolean cfgMultiCollide = true;
    private boolean cfgLowSpec = false;
    private float cfgMove = 1.0f;        /* 移动速度倍率 */
    private long cfgTalkGap = 30000L;    /* 冒泡间隔 */
    /* 活动范围（像素），默认整屏 */
    private int bL = 0, bT = 0, bR = 0, bB = 0;
    /* 站在地上时，身体底边离屏幕底还有多远（dp）。物理设置里可调，默认 36 */
    private int cfgBottomGap = 36;
    /* 气泡字号（sp）和随之放大的最大宽度：长名言靠换行装下，不限字数 */
    private int cfgBubbleFont = 13;
    /* 上次提醒"要开无障碍"的时间：10 秒内再双击就带他去开 */
    private long noAccessHintAt = 0L;
    /* 名言：开关 / 间隔 / 停留 */
    private boolean cfgQuoteOn = true;
    private long cfgQuoteGap = 45000L;
    private long cfgQuoteMs = 4000L;
    private View rangeOverlay = null;
    private final java.util.List<float[]> poly = new java.util.ArrayList<float[]>();
    private long cfgReadAt = 0L;
    private long frameCount = 0L;

    /* 物理跟着屏幕刷新走。以前是固定 33ms 的 Handler：位移 30Hz、绘制 60Hz，
       于是每两帧同一个位置 —— 走路和落地时那种"一顿一顿"就是它。 */
    private long lastFrameNs = 0L;

    private final android.view.Choreographer.FrameCallback frameTick = new android.view.Choreographer.FrameCallback() {
        @Override public void doFrame(long ns) {
            if (!tickRunning) return;
            long now = System.currentTimeMillis();
            if (lastFrameNs == 0L) lastFrameNs = ns;
            long deltaNs = ns - lastFrameNs;
            if (deltaNs < 11000000L) {              /* 120Hz 屏：物理最多按 60Hz 算 */
                android.view.Choreographer.getInstance().postFrameCallback(this);
                return;
            }
            float dt = Math.min(0.05f, deltaNs / 1.0e9f);
            lastFrameNs = ns;
            lastTick = now;
            if (now - cfgReadAt > 1200L) readPhysicsPrefs();
            frameCount++;
            boolean skip = cfgLowSpec && (frameCount % 2 == 1);
            if (!skip) {
                for (int i = 0; i < pets.size(); i++) step(pets.get(i), now, dt);
                if (cfgMultiCollide) resolveCollisions(now);
            }
            android.view.Choreographer.getInstance().postFrameCallback(this);
        }
    };

    /* ================= 锁屏省电：屏幕一关就彻底停手 ================= */
    private boolean screenOn = true;
    private boolean tickRunning = false;
    private final android.content.BroadcastReceiver screenRx = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String a = i == null ? null : i.getAction();
            if (Intent.ACTION_SCREEN_ON.equals(a)) setScreenOn(true);
            else if (Intent.ACTION_SCREEN_OFF.equals(a)) setScreenOn(false);
        }
    };

    private void startTick() {
        if (tickRunning) return;
        tickRunning = true;
        lastTick = 0L;
        lastFrameNs = 0L;
        android.view.Choreographer.getInstance().postFrameCallback(frameTick);
    }

    private void stopTick() {
        tickRunning = false;
        try { android.view.Choreographer.getInstance().removeFrameCallback(frameTick); } catch (Exception ignored) {}
    }

    private void setScreenOn(boolean on) {
        if (screenOn == on) return;
        screenOn = on;
        for (Pet p : pets) if (p.view != null) p.view.setPaused(!on);
        if (on) startTick(); else stopTick();
    }

    @Override public void onConfigurationChanged(android.content.res.Configuration cfg) {
        super.onConfigurationChanged(cfg);
        /* 转屏 / 折叠 / 改分辨率：窗口尺寸跟着变，别再用老坐标 */
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels; screenH = dm.heightPixels;
        readPhysicsPrefs();
        for (Pet p : pets) { clamp(p); pushWindow(p); }
    }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        sp = getSharedPreferences("slimepet", MODE_PRIVATE);
        muted = sp.getBoolean("muted", false);
        aiTalk = sp.getBoolean("ai_talk", true);
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        screenW = dm.widthPixels; screenH = dm.heightPixels;
        padMargin = dp(10);
        padSlop = Math.max(1, padMargin / 2);
        stageMode = false;                  /* 合窗模式已停用，见 StageView 上方说明 */
        createChannel();
        startForeground(ID_PET, buildNotification());
        int n = Math.max(1, sp.getInt("count", 1));
        for (int i = 0; i < n; i++) addPet(i, n);
        readPhysicsPrefs();
        registerGyro();
        try {
            IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
            f.addAction(Intent.ACTION_SCREEN_OFF);
            registerReceiver(screenRx, f);
        } catch (Exception ignored) {}
        startTick();
    }

    /** 铺满屏幕的绘制窗：一旦建好就再也不动 */
    private void buildStage() {
        StageView v = new StageView();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                screenW, screenH,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        try {
            wm.addView(v, lp);
            stage = v; stageLp = lp; stageMode = true;
        } catch (Exception e) {
            stage = null;
            stageMode = false;      /* 加不上就退回分窗，至少还能用 */
            sp.edit().putBoolean("stage_mode", false).apply();
        }
    }

    @Override public int onStartCommand(Intent intent, int f, int s) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (intent != null && ACTION_SET_COUNT.equals(intent.getAction())) {
            setPetCount(Math.max(1, sp.getInt("count", 1)));
        }
        return START_STICKY;
    }

    /** 把数量直接改成 target（多退少补） */
    private void setPetCount(int target) {
        if (target < 1) target = 1;
        while (pets.size() > target) {
            Pet last = pets.remove(pets.size() - 1);
            detachPet(last);
        }
        while (pets.size() < target) {
            int n = pets.size();
            addPet(n, n + 1);
        }
        sp.edit().putInt("count", pets.size()).apply();
    }

    @Override
    public void onDestroy() {
        h.removeCallbacksAndMessages(null);
        tickRunning = false;
        unregisterGyro();
        try { unregisterReceiver(screenRx); } catch (Exception ignored) {}
        for (Pet p : pets) detachPet(p);
        pets.clear();
        try { if (stage != null) wm.removeView(stage); } catch (Exception ignored) {}
        stage = null;
        try { if (menu != null) wm.removeView(menu); } catch (Exception ignored) {}
        super.onDestroy();
    }

    /* ================= 添加 / 移除 ================= */
    /**
     * "地面"在哪：她身体的底边在窗口高度的 80% 处（窗口上下留白是给形变和光晕的），
     * 所以窗口顶要压在 bB - 0.8s。以前用 bB - s，她的身体就悬在地面线上方 20% 的地方，
     * 加上 bB 又是整块屏幕高度，实际效果就是"太靠下、被系统导航条盖住"。
     */
    private float groundY(int s) {
        int bb = bB > 0 ? bB : screenH - dp(cfgBottomGap);
        float y = bb - s * 0.80f;
        if (y < bT) y = bT;
        return y;
    }

    private int sizePx() {
        int s = sp.getInt("size", 1);
        return dp(SIZES_DP[Math.max(0, Math.min(2, s))]);
    }

    private void addPet(int index, int total) {
        Pet p = new Pet();
        p.view = new SlimeView(this);
        p.view.setCallback(p);
        int s = sizePx();
        p.view.lowSpec = cfgLowSpec;
        p.view.paused = !screenOn;      /* 锁屏时新加的那只也别画 */
        p.view.selfDriven = !stageMode; /* 合窗模式下由 StageView 统一驱动 */
        layoutSlime(p.view, s);

        float slot = (total <= 1) ? 0.5f : (index + 1f) / (total + 1f);
        if (bR <= 0) bR = screenW;
        if (bB <= 0) bB = screenH - dp(cfgBottomGap);
        /* 在活动范围里均匀摆开，而不是按整块屏幕摆 */
        float span = Math.max(0f, (bR - bL) - s);
        p.px = bL + span * slot;
        p.py = groundY(s);
        p.nextWalkAt = System.currentTimeMillis() + 3000L + rnd.nextInt(3000);

        if (stageMode && stage != null) {
            p.pad = new PetPad(p);
            p.padLp = newPadLp(s);
            p.padX = p.px; p.padY = p.py;
            p.padLp.x = Math.round(p.px) - padMargin;
            p.padLp.y = Math.round(p.py) - padMargin;
            try {
                wm.addView(p.pad, p.padLp);
                pets.add(p);
                stage.invalidate();
            } catch (Exception e) {
                p.pad = null;
                stageMode = false;
                try { if (stage != null) wm.removeView(stage); } catch (Exception ignored) {}
                stage = null; stageLp = null;
                addPetLegacy(p, s);
            }
            return;
        }
        addPetLegacy(p, s);
    }

    /** 老路子：自己一个窗，自带绘制和触摸 */
    private void addPetLegacy(Pet p, int s) {
        p.view.selfDriven = true;
        p.lp = new WindowManager.LayoutParams(s, s,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
        p.lp.gravity = Gravity.TOP | Gravity.START;
        p.lp.x = Math.round(p.px);
        p.lp.y = Math.round(p.py);
        try { wm.addView(p.view, p.lp); pets.add(p); } catch (Exception ignored) {}
    }

    /** 合窗模式要能按尺子画：手动量一次、摆一次，之后 draw(canvas) 才有宽高 */
    private void layoutSlime(SlimeView v, int s) {
        if (v == null) return;
        v.measure(android.view.View.MeasureSpec.makeMeasureSpec(s, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(s, android.view.View.MeasureSpec.EXACTLY));
        v.layout(0, 0, s, s);
    }

    private WindowManager.LayoutParams newPadLp(int s) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                s + padMargin * 2, s + padMargin * 2,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        return lp;
    }

    /** 触控小窗只在偏出阈值时才真的挪一次（她走 1 秒才动 3 次窗口，以前是 60 次） */
    private void syncPad(Pet p) {
        if (p == null || p.pad == null) return;
        if (Math.abs(p.px - p.padX) < padSlop && Math.abs(p.py - p.padY) < padSlop) return;
        forcePad(p);
    }

    private void forcePad(Pet p) {
        if (p == null || p.pad == null || p.padLp == null) return;
        p.padX = p.px; p.padY = p.py;
        int nx = Math.round(p.px) - padMargin;
        int ny = Math.round(p.py) - padMargin;
        if (p.padLp.x == nx && p.padLp.y == ny) return;
        p.padLp.x = nx; p.padLp.y = ny;
        try { wm.updateViewLayout(p.pad, p.padLp); } catch (Exception ignored) {}
    }

    /** 连戳三下：往旁边蹦一下躲开手指 */
    private void dodge(Pet p) {
        if (p == null || p.view == null) return;
        int s = sizePx();
        boolean right = (p.px + s * 0.5f) < (bL + bR) * 0.5f;
        float nx = p.px + (right ? 1f : -1f) * s * 0.55f;
        if (nx < bL) nx = bL;
        if (nx > bR - s) nx = bR - s;
        p.px = nx;
        p.mode = 0;
        p.vx = (right ? 1f : -1f) * 160f;
        p.nextWalkAt = System.currentTimeMillis() + 2600L;
        p.view.hopStart();
        p.view.setGaze(right ? -0.8f : 0.8f, 0f);   /* 眼睛瞪着手指那边 */
        p.view.poke();
        if (p.wall != 0) { p.wall = 0; p.view.setPose(0); p.view.setClimbing(false); }
        clamp(p);
        pushWindow(p);
        forcePad(p);
    }

    private void addOnePet() {
        int n = pets.size();
        if (n + 1 > 3 && !sp.getBoolean("low_spec", false)) {
            sp.edit().putBoolean("low_spec", true).apply();
            cfgLowSpec = true;
        }
        addPet(n, n + 1);
        sp.edit().putInt("count", pets.size()).apply();
    }

    private void removeOnePet() {
        if (pets.size() <= 1) { say(pets.get(0), "至少留一只", 1600); return; }
        Pet p = pets.remove(pets.size() - 1);
        detachPet(p);
        sp.edit().putInt("count", pets.size()).apply();
    }

    /** 把一只的所有窗口摘掉（分窗 / 合窗都适用） */
    private void detachPet(Pet p) {
        if (p == null) return;
        try { if (p.bubble != null) wm.removeView(p.bubble); } catch (Exception ignored) {}
        try { if (p.pad != null) wm.removeView(p.pad); } catch (Exception ignored) {}
        try { if (p.lp != null && p.view != null) wm.removeView(p.view); } catch (Exception ignored) {}
        p.bubble = null; p.pad = null; p.lp = null;
        if (stage != null) stage.invalidate();
    }

    private void pushWindow(Pet p) {
        if (p == null || p.view == null) return;
        if (stageMode) {
            /* 画面每帧都在重画，这里只管把看不见的触控窗轻轻跟上 */
            syncPad(p);
            return;
        }
        if (p.lp == null) return;
        int nx = Math.round(p.px), ny = Math.round(p.py);
        /* 位置没变就别发 IPC —— 这是最省的一刀 */
        if (p.lp.x == nx && p.lp.y == ny) return;
        p.lp.x = nx;
        p.lp.y = ny;
        try { wm.updateViewLayout(p.view, p.lp); } catch (Exception ignored) {}
        positionBubbleNow(p);
    }

    private void clamp(Pet p) {
        int s = sizePx();
        /* 画的范围比她还窄时，至少让她放得下（不然会来回抖） */
        int r = Math.max(bL + s, bR);
        if (p.px < bL) p.px = bL;
        if (p.px > r - s) p.px = r - s;
        if (p.py < bT) p.py = bT;
        float maxY = groundY(s);
        if (p.py > maxY) p.py = maxY;
    }

    /* ================= 物理 ================= */
    private void step(Pet p, long now, float dt) {
        if (p.view == null || p.dragging) return;
        int s = sizePx();
        float floor = groundY(s);

        if (p.nextColorAt == 0L) p.nextColorAt = now + 60000L;
        if (p.nextTalkAt == 0L) p.nextTalkAt = now + (cfgQuoteOn ? cfgQuoteGap : cfgTalkGap);
        if (now > p.nextColorAt) {
            int next = p.view.elementIndex();
            do { next = rnd.nextInt(7); } while (next == p.view.elementIndex());
            p.view.setElementIndex(next);
            p.nextColorAt = now + 60000L;
        }
        if (now > p.nextTalkAt && !muted && !p.view.isSleeping()) {
            if (cfgQuoteOn) {
                /* 名言模式：到点说一条带出处的名言，说过的不重复 */
                if (aiTalk && !aiCache.isEmpty()) {
                    String q = aiCache.remove(0);
                    say(p, q, cfgQuoteMs);
                    remember(q);
                    p.nextTalkAt = now + (long) (cfgQuoteGap * (0.85f + rnd.nextFloat() * 0.3f));
                } else if (aiTalk) {
                    askAi(p, false);
                    p.nextTalkAt = now + (long) (cfgQuoteGap * 1.2f);
                } else {
                    String q = pickFresh("quote", QUOTES);
                    if (q != null) { say(p, q, cfgQuoteMs); remember(q); }
                    p.nextTalkAt = now + (long) (cfgQuoteGap * (0.85f + rnd.nextFloat() * 0.3f));
                }
            } else if (aiTalk && !aiCache.isEmpty()) {
                String q = aiCache.remove(0);
                say(p, q, cfgQuoteMs);
                remember(q);
                p.nextTalkAt = now + (long) (cfgTalkGap * (0.7f + rnd.nextFloat() * 0.6f));
            } else if (aiTalk) {
                askAi(p, false);
                p.nextTalkAt = now + (long) (cfgTalkGap * 1.4f);
            } else {
                String[] mine = FORM_LINES[Math.max(0, Math.min(6, p.view.elementIndex()))];
                String line = (rnd.nextFloat() < 0.5f) ? pickFresh("form" + p.view.elementIndex(), mine) : null;
                if (line == null) line = pickFresh("quiet", QUIET);
                if (line != null) say(p, line, cfgQuoteMs);
                p.nextTalkAt = now + (long) (cfgTalkGap * (0.6f + rnd.nextFloat() * 0.8f));
            }
        }

        boolean asleep = p.view.isSleeping();

        if (p.wall != 0) {
            if (asleep) {
                /* 睡着了就原地贴着睡：不往下爬，也不许被甩到地面。
                   以前这里是"先落地再睡"，结果三击睡觉的一瞬间她就被拽到屏幕底部。 */
                if (p.wall == 1) p.px = bL;
                else if (p.wall == 2) p.px = bR - s;
                else p.py = bT;
                p.vx = 0f; p.vy = 0f; p.mode = 0;
                p.view.setClimbing(false);      /* 睡梦里别再爬 */
                p.view.moving = false;
                clamp(p);
                pushWindow(p);
                return;
            }
            if (p.wall == 1 || p.wall == 2) {
                p.px = (p.wall == 1) ? bL : bR - s;
                p.view.setClimbing(true);
                if (p.wallDir < 0) {
                    p.py -= 62f * cfgMove * dt;
                    if (p.py <= bT) { p.py = bT; p.wall = 3; p.view.setPose(3); p.wallDir = (p.px < screenW / 2f) ? 1 : -1; }
                } else {
                    p.py += 46f * cfgMove * dt;
                    if (p.py >= floor) { p.wall = 0; p.view.setPose(0); p.view.setClimbing(false); p.mode = 0; p.py = floor; p.nextWalkAt = now + 6000L; p.climbCool = now + 9000L; }
                }
                p.view.setGaze(0f, p.wallDir < 0 ? -0.8f : 0.8f);
            } else {
                p.py = bT;
                p.view.setClimbing(true);
                if (now > p.ceilingPause) {
                    p.px += p.wallDir * 40f * cfgMove * dt;
                    if (rnd.nextFloat() < 0.010f) p.ceilingPause = now + 3000L + rnd.nextInt(5000);
                }
                if (p.px <= bL) { p.px = bL; p.wall = 1; p.wallDir = 1; p.view.setPose(1); p.ceilingPause = now + 1200L; }
                if (p.px >= bR - s) { p.px = bR - s; p.wall = 2; p.wallDir = 1; p.view.setPose(2); p.ceilingPause = now + 1200L; }
                p.view.setGaze(p.wallDir > 0 ? 0.7f : -0.7f, 0f);
            }
            pushWindow(p);
            return;
        }

        if (p.mode == 1 && !asleep) {
            p.px += p.walkDir * 46f * cfgMove * dt;
            p.hopPhase += dt / 0.95f;
            if (p.hopPhase >= 1f) p.hopPhase -= 1f;
            float hop;
            if (p.hopPhase < 0.18f) { hop = 0f; p.view.hopStart(); }
            else if (p.hopPhase < 0.80f) { float u = (p.hopPhase - 0.18f) / 0.62f; hop = 4f * s * 0.16f * u * (1f - u); }
            else { hop = 0f; p.view.land(); }
            p.py = floor - hop;
            p.view.setGaze(p.walkDir * 0.7f, 0.15f);
            if (now > p.nextWalkAt) { p.mode = 0; p.hopPhase = 0f; p.py = floor; p.nextWalkAt = now + 2000L + rnd.nextInt(4000); }
            if (p.px <= bL) { p.px = bL; p.walkDir = 1; if (now > p.climbCool) startClimb(p, 1, now); }
            if (p.px >= bR - s) { p.px = bR - s; p.walkDir = -1; if (now > p.climbCool) startClimb(p, 2, now); }
            pushWindow(p);
            return;
        }

        if (!asleep && p.py >= floor - 1f && now > p.nextWalkAt) {
            p.mode = 1;
            p.walkDir = rnd.nextBoolean() ? 1 : -1;
            p.view.hopStart();
            p.nextWalkAt = now + 1400L + rnd.nextInt(2600);
        }

        if (p.py < floor - 1f) {
            if (asleep) {
                /* 睡在半空：像羽毛一样慢慢沉下去，而不是"啪"一下砸到地面 */
                p.vx = 0f;
                p.vy = Math.min(520f, p.vy + 900f * dt);
                p.py = Math.min(floor, p.py + p.vy * dt);
                if (p.py >= floor) { p.py = floor; p.vy = 0f; p.mode = 0; }
            } else {
                p.vy += cfgGravity * dt;
                p.vx += gyroX * 260f * dt;
                p.px += p.vx * dt;
                p.py += p.vy * dt;
                if (p.py >= floor) {
                    p.py = floor;
                    if (p.vy > 220f) { p.vy = -p.vy * cfgBounce; p.view.land(); if (!muted && rnd.nextFloat() < 0.3f) say(p, "哎哟", 900); }
                    else { p.vy = 0f; p.mode = 0; p.nextWalkAt = now + 1500L; }
                }
            }
        } else if (asleep) {
            /* 睡着就趴稳：不跟陀螺仪滑、也不吃摩擦，一觉睡在原地 */
            p.vx = 0f; p.vy = 0f; p.py = floor; p.mode = 0;
        } else {
            if (Math.abs(gyroX) > 0.22f) { p.px += gyroX * 95f * dt; p.view.setGaze(gyroX > 0 ? 0.8f : -0.8f, 0.2f); }
            /* 地面摩擦 */
            p.vx *= Math.max(0f, 1f - cfgFriction * dt * 3f);
            if (Math.abs(p.vx) < 4f) p.vx = 0f;
            p.px += p.vx * dt;
            p.py = floor; p.vy = 0f;
        }

        /* 只有速度接近零时才会粘上墙；撞得快的先弹开 */
        float spd = (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy);
        boolean canStick = !asleep && spd < 160f;   /* 睡着不粘墙 */
        if (p.px <= bL) {
            p.px = bL;
            if (canStick && now > p.climbCool) startClimb(p, 1, now);
            else { p.vx = Math.abs(p.vx) * cfgBounce; if (p.vx < 40f) p.vx = 0f; }
        }
        if (p.px >= bR - s) {
            p.px = bR - s;
            if (canStick && now > p.climbCool) startClimb(p, 2, now);
            else { p.vx = -Math.abs(p.vx) * cfgBounce; if (p.vx > -40f) p.vx = 0f; }
        }
        if (p.py < bT && cfgTopBounce) { p.py = bT; p.vy = Math.abs(p.vy) * cfgBounce; }
        confine(p);
        if (p.view != null) p.view.moving = (p.mode != 0 || p.wall != 0 || p.py < floor - 1.5f);
        pushWindow(p);
    }

    /** 史莱姆之间的碰撞：只按身体（半径 0.8R）算，且不允许重合 */
    private void resolveCollisions(long now) {
        int n = pets.size();
        if (n < 2) return;
        int s = sizePx();
        float R = s * 0.33f;
        float rb = R * 0.80f;                    /* 身体半径 */
        float cx = s * 0.5f, cy = s * 0.60f;     /* 身体中心在窗口里的位置 */
        float minD = rb * 2f;

        /* 两轮求解：第一轮分离位置，第二轮处理被屏幕边夹回来的情况 */
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < n; i++) {
                Pet p1 = pets.get(i);
                if (p1.view == null) continue;
                for (int j = i + 1; j < n; j++) {
                    Pet p2 = pets.get(j);
                    if (p2.view == null) continue;

                    float ax = p1.px + cx, ay = p1.py + cy;
                    float bx = p2.px + cx, by = p2.py + cy;
                    float dx = bx - ax, dy = by - ay;
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    if (d < 0.0001f) { dx = 1f; dy = 0.01f; d = (float) Math.sqrt(dx * dx + dy * dy); }
                    if (d >= minD) continue;

                    float nx = dx / d, ny = dy / d;
                    float overlap = minD - d;

                    /* 被手按住、或正贴墙的那只当作不可推动 */
                    float wa = (p1.dragging || p1.wall != 0) ? 0f : 1f;
                    float wb = (p2.dragging || p2.wall != 0) ? 0f : 1f;
                    float wsum = wa + wb;
                    if (wsum <= 0f) continue;         /* 两个都不能动，跳过 */

                    /* 位置：一次推到位，保证不重合 */
                    p1.px -= nx * overlap * (wa / wsum);
                    p1.py -= ny * overlap * (wa / wsum);
                    p2.px += nx * overlap * (wb / wsum);
                    p2.py += ny * overlap * (wb / wsum);

                    if (pass == 0) {
                        /* 速度：等质量弹性碰撞，动量守恒 */
                        float rvx = p2.vx - p1.vx;
                        float rvy = p2.vy - p1.vy;
                        float vn = rvx * nx + rvy * ny;
                        if (vn < 0f) {
                            float e = 0.90f;
                            float ma = wa > 0f ? 1f : 1e6f;
                            float mb = wb > 0f ? 1f : 1e6f;
                            float jImp = -(1f + e) * vn / (1f / ma + 1f / mb);
                            p1.vx -= (jImp / ma) * nx; p1.vy -= (jImp / ma) * ny;
                            p2.vx += (jImp / mb) * nx; p2.vy += (jImp / mb) * ny;
                            if (wa > 0f) p1.mode = 2;
                            if (wb > 0f) p2.mode = 2;

                            p1.view.setGaze(nx * 0.9f, ny * 0.9f);
                            p2.view.setGaze(-nx * 0.9f, -ny * 0.9f);

                            if (now - p1.lastBump > 700L && -vn > 120f) {
                                p1.lastBump = now;
                                p1.view.land(); p2.view.land();
                                if (!muted && rnd.nextFloat() < 0.30f) {
                                    say(rnd.nextBoolean() ? p1 : p2, BUMP_LINES[rnd.nextInt(BUMP_LINES.length)], 1500);
                                }
                            }
                        }
                    }

                    clamp(p1); clamp(p2);
                    pushWindow(p1); pushWindow(p2);
                }
            }
        }
    }

    private void readPhysicsPrefs() {
        int g = sp.getInt("gravity", 2600);
        cfgGravity = Math.max(300f, Math.min(4000f, g));
        cfgBounce = Math.max(0, Math.min(100, sp.getInt("bounce", 45))) / 100f;
        cfgFriction = Math.max(0, Math.min(100, sp.getInt("friction", 40))) / 100f;
        cfgThrow = Math.max(50, Math.min(200, sp.getInt("throw", 110))) / 100f;
        cfgTopBounce = sp.getBoolean("top_bounce", true);
        cfgMultiCollide = sp.getBoolean("multi_collide", true);
        cfgLowSpec = sp.getBoolean("low_spec", false);
        for (Pet p2 : pets) if (p2.view != null) p2.view.lowSpec = cfgLowSpec;
        cfgMove = Math.max(20, Math.min(200, sp.getInt("move_speed", 100))) / 100f;
        cfgTalkGap = Math.max(5, Math.min(300, sp.getInt("talk_gap", 30))) * 1000L;
        cfgBottomGap = Math.max(0, Math.min(120, sp.getInt("bottom_gap", 36)));
        cfgQuoteOn = sp.getBoolean("quote_on", true);
        cfgQuoteGap = Math.max(5, Math.min(600, sp.getInt("quote_gap", 45))) * 1000L;
        cfgQuoteMs = Math.max(2, Math.min(30, sp.getInt("quote_sec", 4))) * 1000L;
        int nf = Math.max(9, Math.min(24, sp.getInt("bubble_font", 13)));
        if (nf != cfgBubbleFont) {
            cfgBubbleFont = nf;
            for (Pet p : pets) {
                if (p.bubble == null) continue;
                p.bubble.setTextSize(cfgBubbleFont);
                p.bubble.setMaxWidth(bubbleMaxW());
            }
        }
        bL = Math.max(0, sp.getInt("bound_l", 0));
        bT = Math.max(0, sp.getInt("bound_t", 0));
        bR = sp.getInt("bound_r", 0); if (bR <= 0) bR = screenW;
        int oldB = bB;
        bB = sp.getInt("bound_b", 0); if (bB <= 0) bB = screenH - dp(cfgBottomGap);
        /* 滑杆一拖，地面线立刻生效，不用等她自己动一下才下来 */
        if (bB != oldB && !pets.isEmpty()) for (Pet p : pets) { clamp(p); pushWindow(p); }
        poly.clear();
        String reg = sp.getString("region", "");
        if (reg.length() > 0) {
            for (String seg : reg.split(";")) {
                String[] xy = seg.split(",");
                if (xy.length == 2) {
                    try { poly.add(new float[]{Float.parseFloat(xy[0]), Float.parseFloat(xy[1])}); } catch (Exception ignored) {}
                }
            }
        }
        cfgReadAt = System.currentTimeMillis();
    }

    /** 铺一层遮罩，让用户手绘活动范围 */
    private void startDrawRange() {
        if (rangeOverlay != null) return;
        final DrawRegionView dv = new DrawRegionView(this);
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        dv.setDone(new DrawRegionView.Done() {
            @Override public void onRegion(java.util.List<android.graphics.PointF> pts) {
                StringBuilder sb = new StringBuilder();
                float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -1, maxY = -1;
                for (android.graphics.PointF q : pts) {
                    if (sb.length() > 0) sb.append(';');
                    sb.append(Math.round(q.x)).append(',').append(Math.round(q.y));
                    minX = Math.min(minX, q.x); maxX = Math.max(maxX, q.x);
                    minY = Math.min(minY, q.y); maxY = Math.max(maxY, q.y);
                }
                sp.edit().putString("region", sb.toString())
                        .putInt("bound_l", (int) minX).putInt("bound_t", (int) minY)
                        .putInt("bound_r", (int) maxX).putInt("bound_b", (int) maxY).apply();
                readPhysicsPrefs();
                for (Pet p : pets) { confine(p); clamp(p); pushWindow(p); }
                closeRangeOverlay();
                say(pets.isEmpty() ? null : pets.get(0), "范围画好了", 1600);
            }
            @Override public void onCancel() {
                closeRangeOverlay();
                say(pets.isEmpty() ? null : pets.get(0), "取消了", 1200);
            }
        });
        try { wm.addView(dv, lp); rangeOverlay = dv; } catch (Exception ignored) {}
    }

    /** 矩形范围：拖两个角 */
    /** 三连击关机：需要 Shizuku 以 shell 身份执行 */
    private void shutdownDevice() {
        if (!ShizukuShell.canRun()) {
            say(pets.isEmpty() ? null : pets.get(0), "要先用 Shizuku 授权才能关机", 3200);
            ShizukuShell.requestPermission();
            return;
        }
        say(pets.isEmpty() ? null : pets.get(0), "关机…", 1200);
        new Thread(new Runnable() {
            @Override public void run() {
                ShizukuShell.exec("reboot -p");
                try { Thread.sleep(1500); } catch (Exception ignored) {}
                ShizukuShell.exec("svc power shutdown");
            }
        }).start();
    }

    private void startDrawRect() {
        if (rangeOverlay != null) return;
        final DrawRectView dv = new DrawRectView(this);
        final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        dv.setDone(new DrawRectView.Done() {
            @Override public void onRect(android.graphics.Rect r) {
                sp.edit().putInt("bound_l", r.left).putInt("bound_t", r.top)
                        .putInt("bound_r", r.right).putInt("bound_b", r.bottom)
                        .putString("region", "").apply();     /* 矩形模式：清掉多边形 */
                readPhysicsPrefs();
                for (Pet p : pets) { clamp(p); pushWindow(p); }
                closeRangeOverlay();
                say(pets.isEmpty() ? null : pets.get(0), "矩形范围好了", 1600);
            }
            @Override public void onCancel() {
                closeRangeOverlay();
                say(pets.isEmpty() ? null : pets.get(0), "取消了", 1200);
            }
        });
        try { wm.addView(dv, lp); rangeOverlay = dv; } catch (Exception ignored) {}
    }

    private void closeRangeOverlay() {
        if (rangeOverlay != null) {
            try { wm.removeView(rangeOverlay); } catch (Exception ignored) {}
            rangeOverlay = null;
        }
    }

    private void resetRange() {
        sp.edit().putInt("bound_l", 0).putInt("bound_t", 0).putInt("bound_r", 0).putInt("bound_b", 0)
                .putString("region", "").apply();
        readPhysicsPrefs();
        for (Pet p : pets) { clamp(p); pushWindow(p); }
        say(pets.isEmpty() ? null : pets.get(0), "回到全屏", 1400);
    }

    private boolean insidePoly(float x, float y) {
        int n = poly.size();
        if (n < 3) return true;
        boolean in = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = poly.get(i)[0], yi = poly.get(i)[1];
            float xj = poly.get(j)[0], yj = poly.get(j)[1];
            if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) in = !in;
        }
        return in;
    }

    /** 跑出画的区域就拉回来，速度沿法线反弹 */
    private void confine(Pet p) {
        int n = poly.size();
        if (n < 3) return;
        int s = sizePx();
        float cx = p.px + s * 0.5f, cy = p.py + s * 0.60f;
        float rad = s * 0.33f * 0.8f;
        if (insidePoly(cx, cy)) return;

        /* 找最近的边界点 */
        float bestX = 0, bestY = 0, bestD = Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            float ax = poly.get(i)[0], ay = poly.get(i)[1];
            float bx = poly.get((i + 1) % n)[0], by = poly.get((i + 1) % n)[1];
            float dx = bx - ax, dy = by - ay;
            float len2 = dx * dx + dy * dy;
            float t = len2 <= 0 ? 0 : ((cx - ax) * dx + (cy - ay) * dy) / len2;
            t = Math.max(0, Math.min(1, t));
            float qx = ax + dx * t, qy = ay + dy * t;
            float d = (cx - qx) * (cx - qx) + (cy - qy) * (cy - qy);
            if (d < bestD) { bestD = d; bestX = qx; bestY = qy; }
        }

        /* 内部方向 = 指向多边形重心 */
        float gx = 0, gy = 0;
        for (int i = 0; i < n; i++) { gx += poly.get(i)[0]; gy += poly.get(i)[1]; }
        gx /= n; gy /= n;
        float nx = gx - bestX, ny = gy - bestY;
        float nl = (float) Math.sqrt(nx * nx + ny * ny);
        if (nl < 0.001f) { nx = 0; ny = -1; nl = 1; }
        nx /= nl; ny /= nl;

        /* 把中心推回边界内侧 rad 处 */
        p.px = bestX + nx * (rad + 3f) - s * 0.5f;
        p.py = bestY + ny * (rad + 3f) - s * 0.60f;
        clamp(p);

        /* 切向不变，法向反弹 */
        float vn = p.vx * nx + p.vy * ny;
        if (vn < 0f) {
            p.vx -= (1f + cfgBounce) * vn * nx;
            p.vy -= (1f + cfgBounce) * vn * ny;
        }
        p.mode = 2;
        pushWindow(p);
    }

    private void startClimb(Pet p, int which, long now) {
        if (p.view == null) return;
        p.view.setClimbing(true);
        p.wall = which; p.wallSince = now; p.wallDir = -1;
        p.vx = 0f; p.vy = 0f;
        p.view.setPose(which);
        if (!muted && rnd.nextFloat() < 0.35f) say(p, "我爬上去咯", 1200);
    }

    /* ================= 陀螺仪 ================= */
    private void registerGyro() {
        try {
            SensorManager sm = (SensorManager) getSystemService(SENSOR_SERVICE);
            Sensor a = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (a != null) sm.registerListener(this, a, SensorManager.SENSOR_DELAY_GAME);
        } catch (Exception ignored) {}
    }

    private void unregisterGyro() {
        try { ((SensorManager) getSystemService(SENSOR_SERVICE)).unregisterListener(this); } catch (Exception ignored) {}
    }

    @Override public void onSensorChanged(SensorEvent e) {
        if (e == null || e.values == null || e.values.length < 2) return;
        gyroX = -e.values[0] / 9.8f;
        if (gyroX > 1f) gyroX = 1f;
        if (gyroX < -1f) gyroX = -1f;
    }

    @Override public void onAccuracyChanged(Sensor s, int a) {}

    /* ================= 菜单 ================= */
    private void showMenu(final Pet target) {
        dismissMenu();
        menuTarget = target;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE6151A24);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0x33FFFFFF);
        box.setBackground(bg);
        box.setPadding(dp(6), dp(6), dp(6), dp(6));

        addItem(box, "换形态（下一个）", new Runnable() { @Override public void run() { menuTarget.view.setElementIndex(menuTarget.view.elementIndex() + 1); dismissMenu(); } });
        addItem(box, "变大", new Runnable() { @Override public void run() { resize(1); } });
        addItem(box, "变小", new Runnable() { @Override public void run() { resize(-1); } });
        addItem(box, menuTarget.view.isSleeping() ? "叫醒她" : "让她睡", new Runnable() {
            @Override public void run() { menuTarget.view.setSleeping(!menuTarget.view.isSleeping()); dismissMenu(); }
        });
        addItem(box, muted ? "开口说话" : "闭嘴", new Runnable() {
            @Override public void run() { muted = !muted; sp.edit().putBoolean("muted", muted).apply(); dismissMenu(); }
        });
        addItem(box, aiTalk ? "说话来源：AI" : "说话来源：本地", new Runnable() {
            @Override public void run() { aiTalk = !aiTalk; sp.edit().putBoolean("ai_talk", aiTalk).apply(); say(menuTarget, aiTalk ? "以后我现编" : "以后说存好的", 1600); dismissMenu(); }
        });
        addItem(box, "让 AI 说一句", new Runnable() { @Override public void run() { Pet t = menuTarget; dismissMenu(); askAi(t, true); } });
        addItem(box, "画矩形范围", new Runnable() { @Override public void run() { dismissMenu(); startDrawRect(); } });
        addItem(box, "画自由范围", new Runnable() { @Override public void run() { dismissMenu(); startDrawRange(); } });
        addItem(box, "范围恢复全屏", new Runnable() { @Override public void run() { dismissMenu(); resetRange(); } });
        addItem(box, "多放一只", new Runnable() { @Override public void run() { dismissMenu(); addOnePet(); } });
        addItem(box, "收走一只", new Runnable() { @Override public void run() { dismissMenu(); removeOnePet(); } });
        addItem(box, "自动睡觉：" + (menuTarget.view.autoSleep ? "开" : "关"), new Runnable() {
            @Override public void run() { menuTarget.view.autoSleep = !menuTarget.view.autoSleep; sp.edit().putBoolean("auto_sleep", menuTarget.view.autoSleep).apply(); say(menuTarget, menuTarget.view.autoSleep ? "会自己睡" : "不自己睡了", 1400); dismissMenu(); }
        });
        addItem(box, PetAccess.ready() ? "无障碍已开（✅ 返回可用）" : "开无障碍（关页面要用）", new Runnable() {
            @Override public void run() { dismissMenu(); openAccessibilitySettings(); }
        });
        addItem(box, "关掉当前页面（按返回）", new Runnable() { @Override public void run() {
            Pet t = menuTarget;
            dismissMenu();
            closePage(t);
        } });
        addItem(box, "名言设置", new Runnable() { @Override public void run() {
            dismissMenu();
            try {
                Intent qi = new Intent(PetService.this, QuoteActivity.class);
                qi.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(qi);
            } catch (Exception ignored) {}
        } });
        addItem(box, "状态信息", new Runnable() { @Override public void run() { say(menuTarget, menuTarget.view.debugLine(), 5000); dismissMenu(); } });
        addItem(box, "回桌面", new Runnable() { @Override public void run() { dismissMenu(); goHome(); } });
        addItem(box, "关机（需 Shizuku）", new Runnable() { @Override public void run() { dismissMenu(); shutdownDevice(); } });
        addItem(box, "让琳收起来", new Runnable() { @Override public void run() { stopSelf(); } });
        addItem(box, "关闭菜单", new Runnable() { @Override public void run() { dismissMenu(); } });

        menuLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT);
        menuLp.gravity = Gravity.TOP | Gravity.START;
        int x = Math.round(target.px) + sizePx() + dp(6);
        if (x > screenW - dp(190)) x = Math.max(dp(4), Math.round(target.px) - dp(190));
        int y = Math.round(target.py) - dp(20);
        if (y < dp(60)) y = dp(60);
        menuLp.x = x; menuLp.y = y;
        menu = box;
        try { wm.addView(menu, menuLp); } catch (Exception e) { menu = null; }
    }

    private void addItem(LinearLayout box, String text, final Runnable action) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(13f);
        tv.setTextColor(0xFFE8EDF6);
        tv.setPadding(dp(12), dp(9), dp(14), dp(9));
        tv.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { action.run(); } });
        box.addView(tv);
    }

    private void dismissMenu() {
        if (menu != null) { try { wm.removeView(menu); } catch (Exception ignored) {} menu = null; }
    }

    /**
     * 就地切换渲染方式（合窗 ⇄ 分窗）。不重启服务 —— 安卓 12+ 从后台拉起前台服务会被拦，
     * 万一失败她就再也不出来了。这里只是把窗口搬一下。
     */
    private void switchRenderMode() {
        boolean wantStage = !stageMode;
        if (!wantStage) {
            if (stage != null) { try { wm.removeView(stage); } catch (Exception ignored) {} }
            stage = null; stageLp = null; stageMode = false;
        } else {
            if (stage == null) buildStage();
            if (stage == null) return;          /* 建不出来就维持原样 */
            stageMode = true;
        }
        sp.edit().putBoolean("stage_mode", stageMode).apply();

        int s = sizePx();
        for (Pet p : pets) {
            if (stageMode) {
                try { if (p.lp != null) wm.removeView(p.view); } catch (Exception ignored) {}
                p.lp = null;
                try { if (p.bubble != null) wm.removeView(p.bubble); } catch (Exception ignored) {}
                p.bubble = null; p.bubLp = null;
                p.view.selfDriven = false;
                layoutSlime(p.view, s);
                if (p.pad == null) {
                    p.pad = new PetPad(p);
                    p.padLp = newPadLp(s);
                    p.padX = p.px; p.padY = p.py;
                    p.padLp.x = Math.round(p.px) - padMargin;
                    p.padLp.y = Math.round(p.py) - padMargin;
                    try { wm.addView(p.pad, p.padLp); } catch (Exception e) { p.pad = null; }
                }
                forcePad(p);
            } else {
                try { if (p.pad != null) wm.removeView(p.pad); } catch (Exception ignored) {}
                p.pad = null;
                p.view.selfDriven = true;
                p.lp = new WindowManager.LayoutParams(s, s,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                        android.graphics.PixelFormat.TRANSLUCENT);
                p.lp.gravity = Gravity.TOP | Gravity.START;
                p.lp.x = Math.round(p.px);
                p.lp.y = Math.round(p.py);
                try { wm.addView(p.view, p.lp); } catch (Exception ignored) {}
            }
        }
        if (stageMode && stage != null) stage.invalidate();
    }

    private void resize(int delta) {
        int cur = Math.min(2, Math.max(0, sp.getInt("size", 1)));
        int next = Math.min(2, Math.max(0, cur + delta));
        sp.edit().putInt("size", next).apply();
        int s = dp(SIZES_DP[next]);
        for (Pet p : pets) {
            layoutSlime(p.view, s);
            float floor = groundY(s);
            if (p.py > floor) p.py = floor;
            clamp(p);
            if (stageMode) {
                if (p.pad != null && p.padLp != null) {
                    p.padLp.width = s + padMargin * 2;
                    p.padLp.height = s + padMargin * 2;
                }
                forcePad(p);
            } else if (p.lp != null) {
                p.lp.width = s; p.lp.height = s;
                /* 尺寸变了必须真的重设一次窗口：位置没动就不会走 pushWindow 那条路 */
                p.lp.x = Math.round(p.px);
                p.lp.y = Math.round(p.py);
                try { wm.updateViewLayout(p.view, p.lp); } catch (Exception ignored) {}
                positionBubbleNow(p);
            }
        }
        if (stage != null) stage.invalidate();
        dismissMenu();
    }

    /* ================= 气泡（每只一个） ================= */
    /** 气泡最大宽度：跟着字号一起放大，长句多换几行，不截断 */
    private int bubbleMaxW() {
        int w = Math.round(dp(230) * cfgBubbleFont / 13f);
        int cap = Math.max(dp(160), screenW - dp(12));     /* 字号调大也别超出屏幕 */
        if (w > cap) w = cap;
        if (w < dp(120)) w = dp(120);
        return w;
    }

    private void ensureBubble(Pet p) {
        if (p.bubble != null) return;
        TextView tv = new TextView(this);
        tv.setTextSize(cfgBubbleFont);
        tv.setTextColor(0xFF2B2B3A);
        tv.setMaxWidth(bubbleMaxW());
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(0xF7FFFFFF);
        gd.setCornerRadius(dp(12));
        gd.setStroke(dp(1), 0x33000000);
        tv.setBackground(gd);
        tv.setPadding(dp(12), dp(8), dp(12), dp(8));
        tv.setVisibility(View.GONE);
        p.bubLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
        p.bubLp.gravity = Gravity.TOP | Gravity.START;
        /* 先给她头顶的位置，别让第一句话从屏幕左上角冒出来 */
        p.bubLp.x = Math.max(0, Math.round(p.px));
        p.bubLp.y = Math.max(0, Math.round(p.py) - dp(40));
        try { wm.addView(tv, p.bubLp); p.bubble = tv; } catch (Exception ignored) { p.bubble = null; }
    }

    private void say(final Pet p, String text, long ms) {
        if (muted || p == null || p.view == null) return;

        if (stageMode) {
            /* 合窗模式：气泡直接画在舞台上，省掉一整扇会跟着她跑的窗口 */
            p.bubText = text;
            p.bubUntil = System.currentTimeMillis() + ms;
            p.bubLayout = null;                 /* 下次画的时候重新排版 */
            if (stage != null) stage.invalidate();
            return;
        }

        ensureBubble(p);
        if (p.bubble == null) return;
        p.bubble.setVisibility(View.VISIBLE);
        p.bubble.setText(text);
        final long token = ++p.bubbleSeq;
        /* 刚改完文字还没量出宽高：这一帧先摆个大概，下一帧再按真实宽高校正 */
        p.bubble.post(new Runnable() {
            @Override public void run() {
                positionBubbleNow(p);
                if (p.bubble != null) p.bubble.post(new Runnable() {
                    @Override public void run() { positionBubbleNow(p); }
                });
            }
        });
        h.postDelayed(new Runnable() {
            @Override public void run() {
                if (p.bubble != null && p.bubbleSeq == token) p.bubble.setVisibility(View.GONE);
            }
        }, ms);
    }

    /** 重要提示：不受「闭嘴」影响 */
    private void sayForce(final Pet p, String text, long ms) {
        boolean old = muted;
        muted = false;
        say(p, text, ms);
        muted = old;
    }

    /** 直接跳到系统无障碍设置，让他把「琳」打开 */
    private void openAccessibilitySettings() {
        try {
            Intent ai = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
            ai.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(ai);
        } catch (Exception ignored) {}
    }

    private void positionBubbleNow(Pet p) {
        if (p == null || p.bubble == null || p.bubLp == null || p.lp == null) return;
        int bw = p.bubble.getWidth(), bh = p.bubble.getHeight();
        if (bw <= 0 || bh <= 0) {          /* 还没量出来：按经验值先摆，别留在 0,0 */
            bw = Math.min(Math.max(dp(60), screenW / 3), dp(180));
            bh = dp(34);
        }
        int x = p.lp.x + p.lp.width / 2 - bw / 2;
        if (x < dp(2)) x = dp(2);
        if (x > screenW - bw - dp(2)) x = screenW - bw - dp(2);
        int y;
        if (p.wall == 3) y = p.lp.y + Math.round(p.lp.height * 0.50f) + dp(2);
        else y = p.lp.y + Math.round(p.lp.height * 0.27f) - bh - dp(2);
        if (y < dp(2)) y = p.lp.y + Math.round(p.lp.height * 0.50f);
        if (p.bubLp.x == x && p.bubLp.y == y) return;
        p.bubLp.x = x; p.bubLp.y = y;
        /* 没显示的时候只是记下坐标，别发 IPC —— 以前每走一步都白推一次气泡窗口 */
        if (p.bubble.getVisibility() != View.VISIBLE) return;
        try { wm.updateViewLayout(p.bubble, p.bubLp); } catch (Exception ignored) {}
    }

    /* ================= 合窗模式的气泡：直接用 StaticLayout 画在舞台上 ================= */
    private final RectF bubRect = new RectF();
    private final Paint bubBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bubStroke = new Paint(Paint.ANTI_ALIAS_FLAG);

    private void drawBubble(Canvas c, Pet p, long now, int s) {
        if (!bubbleRect(p, s, now, bubRect)) return;
        ensureBubbleLayout(p);
        if (p.bubLayout == null) return;

        float r = dp(12);
        bubBg.setColor(0xF7FFFFFF);
        bubBg.setStyle(Paint.Style.FILL);
        c.drawRoundRect(bubRect, r, r, bubBg);
        bubStroke.setColor(0x33000000);
        bubStroke.setStyle(Paint.Style.STROKE);
        bubStroke.setStrokeWidth(Math.max(1f, dp(1)));
        c.drawRoundRect(bubRect, r, r, bubStroke);

        c.save();
        c.translate(bubRect.left + dp(12), bubRect.top + dp(8));
        p.bubLayout.draw(c);
        c.restore();
    }

    /** 算出气泡在屏幕上的矩形；没有气泡 / 过期了返回 false */
    private boolean bubbleRect(Pet p, int s, long now, RectF out) {
        if (p.bubText == null) return false;
        if (now >= p.bubUntil) {
            p.bubText = null; p.bubLayout = null;
            return false;
        }
        ensureBubbleLayout(p);
        if (p.bubLayout == null) return false;
        int w = p.bubW, h = p.bubH;
        int x = Math.round(p.px) + s / 2 - w / 2;
        if (x < dp(2)) x = dp(2);
        if (x > screenW - w - dp(2)) x = screenW - w - dp(2);
        int y;
        if (p.wall == 3) y = Math.round(p.py) + Math.round(s * 0.50f) + dp(2);
        else y = Math.round(p.py) + Math.round(s * 0.27f) - h - dp(2);
        if (y < dp(2)) y = Math.round(p.py) + Math.round(s * 0.50f);
        out.set(x, y, x + w, y + h);
        return true;
    }

    private void ensureBubbleLayout(Pet p) {
        if (p.bubLayout != null || p.bubText == null) return;
        if (p.bubPaint == null) {
            android.text.TextPaint tp = new android.text.TextPaint(Paint.ANTI_ALIAS_FLAG);
            tp.setColor(0xFF2B2B3A);
            tp.setTextSize(cfgBubbleFont * getResources().getDisplayMetrics().scaledDensity);
            p.bubPaint = tp;
        }
        android.text.StaticLayout sl = android.text.StaticLayout.Builder
                .obtain(p.bubText, 0, p.bubText.length(), p.bubPaint, dp(230))
                .setLineSpacing(0f, 1.15f)
                .setIncludePad(false)
                .setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL)
                .build();
        int w = 0;
        for (int i = 0; i < sl.getLineCount(); i++) {
            w = Math.max(w, (int) Math.ceil(sl.getLineWidth(i)));
        }
        if (w < dp(24)) w = dp(24);
        p.bubLayout = sl;
        p.bubW = w + dp(24);
        p.bubH = sl.getHeight() + dp(16);
    }

    /* ================= AI 台词（OpenAI 兼容：DeepSeek / Ollama Cloud / 自建都行） ================= */
    private void askAi(final Pet p, final boolean onDemand) {
        if (aiBusy || p == null) return;
        final String key = sp.getString("api_key", "").trim();
        if (key.length() < 10) { if (onDemand) say(p, "先在 App 里填 AI 的 Key", 2600); return; }
        aiBusy = true;
        final String form = p.view.elementName();
        final String base = sp.getString("ai_base", MainActivity.DEFAULT_BASE).trim();
        final String model = sp.getString("ai_model", MainActivity.DEFAULT_MODEL).trim();
        new Thread(new Runnable() {
            @Override public void run() {
                String reply = null;
                String err = null;
                try {
                    String sys = "你是原神里的" + form + "史莱姆，名字叫琳，安静、透明，住在手机屏幕边上。"
                            + "请给我 6 句中外经典名言（哲学、文学），要冷、要深，每句 24 字以内，"
                            + "格式是「句子。—— 作者《作品》」，出处必须真实，拿不准就换一句，不许编造；"
                            + "用 || 分隔，不要编号，不要解释，不要输出思考过程。";
                    String url = MainActivity.chatUrl(base);
                    boolean ollama = MainActivity.isOllama(base);
                    /* Ollama Cloud 的模型都先思考：不关思考的话 content 是空的（接口其实 200 成功）。
                       reasoning_effort:none 让它直接给正文；不认这个参数的老端点会 400，那就去掉重试。 */
                    String[] r = postChat(url, key,
                            MainActivity.chatBody(base, model, 8000, ollama, sys, "现在给我 6 句。"));
                    int code = Integer.parseInt(r[0]);
                    if ((code == 400 || code == 422) && ollama) {
                        r = postChat(url, key,
                                MainActivity.chatBody(base, model, 8000, false, sys, "现在给我 6 句。"));
                        code = Integer.parseInt(r[0]);
                    }
                    String json = r[1];
                    if (code >= 200 && code < 300) {
                        String raw = MainActivity.extractContent(json);
                        if (raw != null) {
                            List<String> lines = pickLines(raw);
                            if (!lines.isEmpty()) {
                                aiCache.addAll(lines);
                                reply = aiCache.remove(0);
                            } else {
                                err = "模型没给出能用的短句";
                            }
                        } else {
                            err = "length".equals(MainActivity.finishReason(json)) ? "回答被思考吃光了" : "空回答";
                        }
                    } else {
                        err = "HTTP " + code;
                    }
                } catch (Exception e) {
                    err = e.getClass().getSimpleName();
                }
                final String r = reply;
                final String e2 = err;
                h.post(new Runnable() {
                    @Override public void run() {
                        aiBusy = false;
                        if (!muted) {
                            if (r != null && r.length() > 0) { say(p, r, cfgQuoteMs); remember(r); }
                            else if (onDemand) say(p, friendly(e2) + "，先说存的", 2600);
                        }
                    }
                });
            }
        }).start();
    }

    /** 把模型回的整段话切成能上气泡的短句：优先中文；带出处的名言别被句号切断 */
    private static List<String> pickLines(String raw) {
        ArrayList<String> cn = new ArrayList<String>();
        ArrayList<String> others = new ArrayList<String>();
        for (String pt : raw.split("\\|\\||\\n")) addLine(pt, cn, others);
        if (cn.isEmpty() && others.isEmpty()) {
            /* 模型没按 || 分行：退一步按句号切，但把「—— 作者《作品》」粘回前一句 */
            StringBuilder cur = new StringBuilder();
            for (String pt : raw.split("。")) {
                String one = cleanLine(pt);
                if (one.length() == 0) continue;
                if (one.startsWith("——") || one.startsWith("—")) {
                    if (cur.length() > 0) cur.append('。').append(one);
                } else {
                    if (cur.length() > 0) addLine(cur.toString(), cn, others);
                    cur.setLength(0);
                    cur.append(one);
                }
            }
            if (cur.length() > 0) addLine(cur.toString(), cn, others);
        }
        return cn.isEmpty() ? others : cn;
    }

    private static String cleanLine(String sIn) {
        if (sIn == null) return "";
        String one = sIn.trim().replace("\"", "").replace("「", "").replace("」", "");
        /* 模型爱加 "1. " "2、" "- " 这种编号，去掉 */
        one = one.replaceFirst("^[0-9]+[.．、)）:：]\\s*", "").replaceFirst("^[-—·*]+\\s*", "").trim();
        return one;
    }

    private static void addLine(String raw, ArrayList<String> cn, ArrayList<String> others) {
        String one = cleanLine(raw);
        if (one.length() < 2) return;          /* 不限长度：长的气泡自己换行 */
        boolean hasCn = false;
        for (int i = 0; i < one.length(); i++) {
            char ch = one.charAt(i);
            if (ch >= 0x4E00 && ch <= 0x9FFF) { hasCn = true; break; }
        }
        if (hasCn) cn.add(one); else others.add(one);
    }

    private static String friendly(String err) {
        if (err == null || err.length() == 0) return "没连上";
        if (err.startsWith("HTTP 401") || err.startsWith("HTTP 403")) return "Key 不对或过期";
        if (err.startsWith("HTTP 404")) return "模型名不对";
        if (err.startsWith("HTTP 429")) return "问得太快了";
        if (err.startsWith("HTTP 5")) return "服务端出错";
        return err;
    }

    /** POST 一次，返回 {HTTP code, 响应体} */
    private static String[] postChat(String url, String key, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(25000);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "Lin/9.3 (Android; slimepet)");
        conn.setRequestProperty("Authorization", "Bearer " + key);
        conn.setDoOutput(true);
        OutputStream os = conn.getOutputStream();
        os.write(body.getBytes("UTF-8"));
        os.flush(); os.close();
        int code = conn.getResponseCode();
        java.io.InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        if (in == null) in = new java.io.ByteArrayInputStream(new byte[0]);
        BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        StringBuilder sb = new StringBuilder(); String ln;
        while ((ln = br.readLine()) != null) sb.append(ln);
        br.close();
        return new String[]{String.valueOf(code), sb.toString()};
    }

    /**
     * 双击琳：替用户按一下「返回」，把当前页面关掉。
     *
     * 有的 App 第一下返回会被吞掉（自绘页面、二次确认、只在按键事件里处理），
     * 所以按完听 250ms：界面一点动静都没有，就再补一下；还没动静就当她按不动，兜底回桌面。
     * 判断"有没有动静"靠 PetAccess 记的时间戳 —— 它只记时间，不读屏幕内容。
     */
    private void closePage(final Pet p) {
        if (!PetAccess.ready()) {
            /*
             * 无障碍没开：她根本按不了返回。
             * 以前这里是悄悄 goHome()，用户看着像"双击没反应/莫名其妙跳桌面" ——
             * 现在先说清楚（不受「闭嘴」影响），10 秒内再双击一次就带他去开。
             */
            long nowMs = SystemClock.uptimeMillis();
            if (nowMs - noAccessHintAt < 12000L) {
                noAccessHintAt = 0L;
                sayForce(p, "带你开无障碍", 1500);
                openAccessibilitySettings();
                return;
            }
            noAccessHintAt = nowMs;
            sayForce(p, "要开无障碍我才能按返回（再双击一次带你开）", 3600);
            return;
        }
        final long t0 = SystemClock.uptimeMillis();
        PetAccess.pressBack();
        h.postDelayed(new Runnable() {
            @Override public void run() {
                if (PetAccess.lastEventAt() >= t0) return;      /* 已经翻页了，别再按 */
                PetAccess.pressBack();
                h.postDelayed(new Runnable() {
                    @Override public void run() {
                        if (PetAccess.lastEventAt() >= t0) return;
                        say(p, "它不认返回，先带你回桌面", 2000);
                        goHome();
                    }
                }, 520L);
            }
        }, 260L);
    }

    /**
     * 不重复地取一条：用过的下标记在 SharedPreferences 里，
     * 这一轮全说完了才清空重来 —— 所以不会连着说同一句。
     */
    private String pickFresh(String key, String[] pool) {
        if (pool == null || pool.length == 0) return null;
        String usedKey = key + "_used";
        String used = sp.getString(usedKey, "");
        java.util.HashSet<String> set = new java.util.HashSet<String>();
        for (String t : used.split(",")) if (t.length() > 0) set.add(t);
        ArrayList<Integer> left = new ArrayList<Integer>();
        for (int i = 0; i < pool.length; i++) if (!set.contains(String.valueOf(i))) left.add(i);
        if (left.isEmpty()) {
            set.clear();
            for (int i = 0; i < pool.length; i++) left.add(i);
        }
        int idx = left.get(rnd.nextInt(left.size()));
        set.add(String.valueOf(idx));
        StringBuilder sb = new StringBuilder();
        for (String t : set) {
            if (sb.length() > 0) sb.append(',');
            sb.append(t);
        }
        sp.edit().putString(usedKey, sb.toString()).apply();
        return pool[idx];
    }

    /** 把说过的名言记进历史：最近的排最前，最多 200 条 */
    private void remember(String text) {
        if (text == null || text.length() == 0) return;
        try {
            StringBuilder sb = new StringBuilder(System.currentTimeMillis() + "\u0001" + text);
            String old = sp.getString("quote_history", "");
            if (old.length() > 0) {
                String[] parts = old.split("\n");
                int n = 0;
                for (String one : parts) {
                    if (one.length() == 0) continue;
                    if (n >= 200) break;
                    sb.append('\n').append(one);
                    n++;
                }
            }
            sp.edit().putString("quote_history", sb.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void goHome() {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) {}
    }

    /* ================= 通知 ================= */
    private void createChannel() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel c = new NotificationChannel(CH_PET, "桌宠常驻", NotificationManager.IMPORTANCE_LOW);
        c.setShowBadge(false);
        nm.createNotificationChannel(c);
    }

    private Notification buildNotification() {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent si = new Intent(this, PetService.class);
        si.setAction(ACTION_STOP);
        PendingIntent spi = PendingIntent.getService(this, 1, si,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Builder b = new Notification.Builder(this, CH_PET)
                .setSmallIcon(R.drawable.ic_noti)
                .setContentTitle("琳 正在陪你")
                .setContentText("单击换形态 · 双击关页面 · 三击睡/醒 · 长按菜单")
                .setContentIntent(pi)
                .setOngoing(true);
        b.addAction(new Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel, "收起来", spi).build());
        return b.build();
    }

    private int dp(float v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
