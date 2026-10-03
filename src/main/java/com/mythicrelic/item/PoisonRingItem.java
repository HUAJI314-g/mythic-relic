package com.mythicrelic.item;

import com.mythicrelic.accessory.AccessoryItem;
import com.mythicrelic.poison.PoisonRingEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 「百毒不侵之戒」——八片剧毒碎片围成的一枚戒指。
 *
 * <p>戴上（放进饰品栏）之后，<b>一切负面效果都近不了身</b>：新的施加不上去，
 * 身上已有的也会被慢慢化掉。判定在 {@link PoisonRingEvents} 里。</p>
 *
 * <p>它用 {@link AccessoryItem} 的默认行为——手持右键直接戴上最省事，
 * 所以不需要像神棱偏转镜那样把 {@code equipOnRightClick()} 关掉。</p>
 */
public class PoisonRingItem extends Item implements AccessoryItem
{
    public PoisonRingItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("tooltip.mythic_relic.poison_ring.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.mythic_relic.poison_ring.effect")
                .withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.mythic_relic.poison_ring.block")
                .withStyle(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.translatable("tooltip.mythic_relic.poison_ring.cleanse")
                .withStyle(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.mythic_relic.poison_ring.source")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
