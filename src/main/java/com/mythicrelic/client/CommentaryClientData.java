package com.mythicrelic.client;

/**
 * 客户端手里「当前正在说的那句评论」。
 *
 * <p>刻意不引用任何客户端专属类型（{@code Minecraft} / {@code ClientLevel} 一个都不碰），
 * 所以网络包的处理函数可以直接调它，即使被专用服务端加载也不会炸——
 * 真正需要客户端环境的编译、发声和渲染都放在 {@link CommentaryOverlay} 里。</p>
 */
public final class CommentaryClientData
{
    /** 正文的语言键。 */
    private static String key;
    /** 语言键里的整数参数（比如「23 / 100」那两个数）。 */
    private static int[] args = new int[0];
    /** 是不是尼德霍格本人在说话（false = 借字幕显示的系统提示，不报名也不发声）。 */
    private static boolean speaking = true;
    /** 刚收到、还没被 {@link CommentaryOverlay} 接手的那一句。 */
    private static boolean fresh;
    /** 还剩多少 tick 说（含收尾的停顿）。 */
    private static int ticksLeft;
    /** 整句总时长，用来算淡入淡出。 */
    private static int totalTicks;

    private CommentaryClientData() {}

    /** 收到新的一句。 */
    public static void set(String translationKey, int mode, int[] newArgs)
    {
        key = translationKey;
        args = newArgs == null ? new int[0] : newArgs;
        speaking = mode != com.mythicrelic.network.CommentaryPacket.MODE_PLAIN;
        fresh = true;
        ticksLeft = 0;
        totalTicks = 0;
    }

    /**
     * 取出「刚收到」的标记。
     *
     * <p>用一次性标记而不是比对状态跳变——服务端能明确告知就明确告知，
     * 别让客户端去猜（这条教训在死亡之心的弹出动画上已经吃过一次）。</p>
     */
    public static boolean consumeFresh()
    {
        if (!fresh)
        {
            return false;
        }
        fresh = false;
        return true;
    }

    /**
     * 由 {@link CommentaryOverlay} 在编译完那句龙语之后调用，开始计时。
     *
     * <p><b>会保留已经过去的时间</b>：祭坛那种「点一下就推一句」的提示会连着来，
     * 每次都从头淡入的话字幕会一直在半透明状态闪；把 elapsed 接续下来，
     * 文字换了、透明度却是连续的。</p>
     */
    public static void begin(int total)
    {
        int elapsed = Math.max(0, totalTicks - ticksLeft);
        totalTicks = total;
        ticksLeft = Math.max(1, total - Math.min(elapsed, Math.max(0, total - 1)));
    }

    public static void tick()
    {
        if (ticksLeft > 0)
        {
            ticksLeft--;
        }
    }

    public static boolean active()
    {
        return key != null && ticksLeft > 0;
    }

    public static String key()
    {
        return key;
    }

    /** 语言键里的整数参数。 */
    public static int[] args()
    {
        return args;
    }

    /** 是不是尼德霍格本人在说话（false = 借字幕显示的系统提示）。 */
    public static boolean speaking()
    {
        return speaking;
    }

    public static int ticksLeft()
    {
        return ticksLeft;
    }

    public static int totalTicks()
    {
        return totalTicks;
    }

    /**
     * 当前的透明度 0~1：头 4 tick 淡入、尾 10 tick 淡出。
     *
     * <p>淡出时间要够长，否则最后那声龙吼还没落，字就先没了。</p>
     */
    public static float alpha()
    {
        if (totalTicks <= 0)
        {
            return 0.0F;
        }
        int elapsed = totalTicks - ticksLeft;
        float in = Math.min(1.0F, elapsed / 4.0F);
        float out = Math.min(1.0F, ticksLeft / 10.0F);
        return Math.max(0.0F, Math.min(in, out));
    }

    /** 断开连接时清掉——否则换个存档还会顶着上一个角色的字幕。 */
    public static void reset()
    {
        key = null;
        args = new int[0];
        speaking = true;
        fresh = false;
        ticksLeft = 0;
        totalTicks = 0;
    }
}
