package com.mythicrelic.item;

import com.mythicrelic.accessory.AccessoryItem;
import com.mythicrelic.accessory.BoundAccessory;
import com.mythicrelic.client.DeathHeartClientData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 「死亡之心」——佩戴在饰品栏上时，将死亡回溯一次。
 *
 * <p>当血量归零时，它会拦截死亡，让血量可以下降至负数，持续 5 分钟。
 * 5 分钟后血量若仍未恢复至正数，则真正死亡。触发后进入 300 秒冷却。</p>
 *
 * <p>它<b>不</b>实现 {@link BoundAccessory}：可以自由佩戴和取下，
 * 但依然实现了 {@link AccessoryItem}——否则放不进饰品栏。</p>
 */
public class DeathHeartItem extends Item implements AccessoryItem
{
    public DeathHeartItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("tooltip.mythic_relic.death_heart.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.mythic_relic.death_heart.2")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.mythic_relic.death_heart.effect")
                .withStyle(ChatFormatting.LIGHT_PURPLE));

        int cooldownLeft = DeathHeartClientData.cooldownLeft();
        if (cooldownLeft > 0)
        {
            int seconds = cooldownLeft / 20;
            int m = seconds / 60;
            int s = seconds % 60;
            String time = m > 0 ? m + ":" + String.format("%02d", s) : String.valueOf(seconds);
            tooltip.add(Component.translatable("tooltip.mythic_relic.death_heart.cooldown", time)
                    .withStyle(ChatFormatting.RED));
        }
        else
        {
            tooltip.add(Component.translatable("tooltip.mythic_relic.death_heart.ready")
                    .withStyle(ChatFormatting.GREEN));
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.mythic_relic.death_heart.source")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
