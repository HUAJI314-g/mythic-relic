package com.mythicrelic.item;

import com.mythicrelic.accessory.BoundAccessory;
import com.mythicrelic.chaos.ChaosTier;
import com.mythicrelic.chaos.PlayerChaos;
import com.mythicrelic.client.ChaosClientData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 「尼德霍格之印」——完成封印仪式后，混沌神龙盘踞体内的信物。
 *
 * <p>它是一件 {@link BoundAccessory 绑定饰品}：只能待在专属的饰品栏里，放进去便取不下来，
 * 也不会随着玩家死亡而消失（相当于永久的绑定诅咒）。</p>
 *
 * <p>悬浮提示里带上当前的混沌阶段、进度条，以及三条被动效果和已经解锁的能力——
 * 玩家把鼠标放上去就能看明白这块印记到底给了什么。</p>
 */
public class NidhoggMarkItem extends Item implements BoundAccessory
{
    /** 进度条用几个格子画。 */
    private static final int BAR_LENGTH = 20;
    /** 实心与空心方块。都是原版字体里就有的制表符。 */
    private static final String BAR_FULL = "\u2588";
    private static final String BAR_EMPTY = "\u2591";

    public NidhoggMarkItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("tooltip.mythic_relic.nidhogg_mark.1").withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("tooltip.mythic_relic.nidhogg_mark.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.mythic_relic.nidhogg_mark.bound").withStyle(ChatFormatting.RED));

        ChaosTier tier = ChaosClientData.tier();
        int progress = ChaosClientData.progress();

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.mythic_relic.nidhogg_mark.tier", tier.display())
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(progressBar(tier, progress));

        int overflow = progress - ChaosTier.FINAL.threshold();
        if (overflow > 0)
        {
            tooltip.add(Component.translatable("tooltip.mythic_relic.nidhogg_mark.health_bonus",
                    String.format("%.1f", overflow * PlayerChaos.HEALTH_PER_POINT))
                    .withStyle(ChatFormatting.RED));
        }

        tooltip.add(Component.empty());
        addEffect(tooltip, "greed");
        addEffect(tooltip, "gluttony");
        addEffect(tooltip, "origin");

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.mythic_relic.nidhogg_mark.unlocked")
                .withStyle(ChatFormatting.GOLD));
        if (tier.atLeast(ChaosTier.MID))
        {
            tooltip.add(bullet("blade", ChatFormatting.AQUA));
            tooltip.add(bullet("wings", ChatFormatting.AQUA));
        }
        if (tier.atLeast(ChaosTier.HIGH))
        {
            tooltip.add(bullet("flight", ChatFormatting.AQUA));
        }
        if (tier == ChaosTier.FINAL)
        {
            tooltip.add(bullet("domain", ChatFormatting.AQUA));
        }

        ChaosTier next = tier.next();
        if (next != null)
        {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.mythic_relic.nidhogg_mark.next", next.display())
                    .withStyle(ChatFormatting.DARK_GRAY));
            String ability = next == ChaosTier.MID ? "blade" : next == ChaosTier.HIGH ? "flight" : "domain";
            tooltip.add(bullet(ability, ChatFormatting.DARK_GRAY));
        }

        super.appendHoverText(stack, level, tooltip, flag);
    }

    /** 画一条 [████░░░░] 的进度条；已经到终阶就只报总数。 */
    private static Component progressBar(ChaosTier tier, int progress)
    {
        ChaosTier next = tier.next();
        if (next == null)
        {
            return Component.translatable("tooltip.mythic_relic.nidhogg_mark.maxed", progress)
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
        }

        int span = next.threshold() - tier.threshold();
        int into = Math.max(0, progress - tier.threshold());
        int filled = Math.max(0, Math.min(BAR_LENGTH, (int) Math.round(BAR_LENGTH * (double) into / span)));

        MutableComponent bar = Component.literal("[");
        bar.append(Component.literal(BAR_FULL.repeat(filled)).withStyle(ChatFormatting.LIGHT_PURPLE));
        bar.append(Component.literal(BAR_EMPTY.repeat(BAR_LENGTH - filled)).withStyle(ChatFormatting.DARK_GRAY));
        bar.append(Component.literal("] "));
        bar.append(Component.literal(into + " / " + span).withStyle(ChatFormatting.GRAY));
        return bar;
    }

    private static void addEffect(List<Component> tooltip, String key)
    {
        MutableComponent title = Component.translatable("tooltip.mythic_relic.nidhogg_mark." + key)
                .withStyle(ChatFormatting.LIGHT_PURPLE);
        title.append(Component.literal(" "));
        title.append(Component.translatable("tooltip.mythic_relic.nidhogg_mark." + key + ".flavor")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(title);
        tooltip.add(Component.translatable("tooltip.mythic_relic.nidhogg_mark." + key + ".desc")
                .withStyle(ChatFormatting.GRAY));
    }

    private static Component bullet(String key, ChatFormatting color)
    {
        return Component.translatable("tooltip.mythic_relic.nidhogg_mark.ability." + key).withStyle(color);
    }

    @Override
    public boolean isFoil(ItemStack stack)
    {
        return true;
    }
}
