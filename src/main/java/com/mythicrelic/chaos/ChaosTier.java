package com.mythicrelic.chaos;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/**
 * 混沌进度的四个阶段。
 *
 * <p>进度是单调累加的一个整数（{@link PlayerChaos#progress()}），阶段由它落在哪个区间决定。
 * 阈值、名称颜色都集中在这里，想调快调慢只改 {@code threshold} 一个数。</p>
 *
 * <p>各阶段解锁的能力：</p>
 * <ul>
 *   <li>{@link #NOVICE 初阶}——只有贪欲 / 暴食 / 起源三条被动；</li>
 *   <li>{@link #MID 中阶}——空手蓄力发射「暗影龙刃」，背后长出龙翼，可像鞘翅一样滑翔；</li>
 *   <li>{@link #HIGH 高阶}——滑翔时按空格获得动力，双击空格进入悬停，悬停中按 Ctrl 切回动力滑翔；</li>
 *   <li>{@link #FINAL 终阶}——按键释放「混沌领域」，此后混沌进度继续转化成生命上限。</li>
 * </ul>
 */
public enum ChaosTier
{
    NOVICE("novice", ChatFormatting.GRAY, 0),
    MID("mid", ChatFormatting.AQUA, 150),
    HIGH("high", ChatFormatting.LIGHT_PURPLE, 500),
    FINAL("final", ChatFormatting.GOLD, 1200);

    private final String key;
    private final ChatFormatting color;
    private final int threshold;

    ChaosTier(String key, ChatFormatting color, int threshold)
    {
        this.key = key;
        this.color = color;
        this.threshold = threshold;
    }

    /** 累计进度达到多少时进入本阶段。 */
    public int threshold()
    {
        return this.threshold;
    }

    public ChatFormatting color()
    {
        return this.color;
    }

    /** 本地化后的阶段名，例如「中阶」。 */
    public Component display()
    {
        return Component.translatable("chaos.mythic_relic.tier." + this.key).withStyle(this.color);
    }

    /** 下一个阶段；已经是终阶则返回 {@code null}。 */
    public ChaosTier next()
    {
        ChaosTier[] all = values();
        return this.ordinal() + 1 < all.length ? all[this.ordinal() + 1] : null;
    }

    public boolean atLeast(ChaosTier other)
    {
        return this.ordinal() >= other.ordinal();
    }

    /** 根据累计进度求出当前阶段。 */
    public static ChaosTier of(int progress)
    {
        ChaosTier result = NOVICE;
        for (ChaosTier tier : values())
        {
            if (progress >= tier.threshold)
            {
                result = tier;
            }
        }
        return result;
    }
}
