package com.mythicrelic.client;

import com.mythicrelic.chaos.ChaosTier;

/**
 * 客户端手里的混沌进度副本。
 *
 * <p>服务端通过 {@code ChaosSyncPacket} 更新它。这个类刻意不引用任何客户端专属类型，
 * 所以即使被专用服务端加载也不会炸。</p>
 */
public final class ChaosClientData
{
    private static int progress;

    private ChaosClientData() {}

    public static void setProgress(int value)
    {
        progress = Math.max(0, value);
    }

    /**
     * 断开连接时清空。
     *
     * <p>这个字段是<b>客户端全局</b>的，不跟着玩家走。不清的话，同一个客户端换存档或换
     * 服务器之后，新角色的印记悬浮提示会先显示上一个角色的进度，直到服务端把同步包发过来。</p>
     */
    public static void reset()
    {
        progress = 0;
    }

    public static int progress()
    {
        return progress;
    }

    public static ChaosTier tier()
    {
        return ChaosTier.of(progress);
    }

    /** 有没有长龙翼——中阶及以上。 */
    public static boolean hasWings()
    {
        return tier().atLeast(ChaosTier.MID);
    }

    /** 能不能悬停——高阶及以上。 */
    public static boolean canHover()
    {
        return tier().atLeast(ChaosTier.HIGH);
    }

    /** 能不能放混沌领域——终阶。 */
    public static boolean canUnleashDomain()
    {
        return tier() == ChaosTier.FINAL;
    }
}
