package com.mythicrelic.client;

import net.minecraft.client.Minecraft;

/**
 * 客户端手里的「神棱偏转镜」状态副本。
 *
 * <p>和 {@link DeathHeartClientData} 一个路子：服务端只把两个<b>结束时刻</b>
 * （绝对世界 tick）发过来，客户端自己拿 {@code level.getGameTime()} 倒着算——
 * 所以 tooltip 里的剩余时间能一直跳，而服务端不必每 tick 发包。</p>
 *
 * <p>两个时刻的含义：{@code activeEndTick} 是「偏转窗口」结束的时刻（0 = 没在偏转），
 * {@code cooldownEndTick} 是「自动触发冷却」结束的时刻（0 = 没有冷却）。</p>
 *
 * <p><b>这些字段是客户端全局的</b>，不跟着玩家实体走，所以断开连接时必须
 * {@link #reset()}，否则同一次客户端换存档/换服务器后会先显示上一个角色的冷却。</p>
 */
public final class PrismMirrorClientData
{
    /** 偏转窗口结束的世界 tick；0 表示当前没有偏转。 */
    private static long activeEndTick;
    /** 自动触发冷却结束的世界 tick；0 表示没有冷却。 */
    private static long cooldownEndTick;

    private PrismMirrorClientData() {}

    /** 收到服务端的同步包。 */
    public static void set(long activeEndTick, long cooldownEndTick)
    {
        PrismMirrorClientData.activeEndTick = activeEndTick;
        PrismMirrorClientData.cooldownEndTick = cooldownEndTick;
    }

    /** 断开连接时清空（见 {@code ClientSessionReset}）。 */
    public static void reset()
    {
        activeEndTick = 0L;
        cooldownEndTick = 0L;
    }

    /** 偏转窗口还剩多少 tick（0 = 没在偏转）。 */
    public static int activeLeft()
    {
        return left(activeEndTick);
    }

    /** 自动触发冷却还剩多少 tick（0 = 冷却已过）。 */
    public static int cooldownLeft()
    {
        return left(cooldownEndTick);
    }

    private static int left(long endTick)
    {
        if (endTick <= 0L)
        {
            return 0;
        }
        return (int) Math.max(0L, endTick - clientTick());
    }

    private static long clientTick()
    {
        try
        {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null)
            {
                return minecraft.level.getGameTime();
            }
        }
        catch (Throwable ignored) { }
        return 0L;
    }
}
