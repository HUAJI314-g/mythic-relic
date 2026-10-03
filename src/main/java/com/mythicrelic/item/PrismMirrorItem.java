package com.mythicrelic.item;

import com.mythicrelic.accessory.AccessoryItem;
import com.mythicrelic.client.PrismMirrorClientData;
import com.mythicrelic.mirror.PrismMirrorEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 「神棱偏转镜」——把打来的伤害原样折回去的一面镜子。
 *
 * <h2>两种用法</h2>
 * <ul>
 *   <li><b>拿在手里右键</b>：开启 {@value PrismMirrorEvents#DURATION_TICKS} tick
 *       （60 秒）的「神棱偏转」窗口；</li>
 *   <li><b>放进饰品栏</b>：血量掉到 {@value PrismMirrorEvents#AUTO_TRIGGER_HEALTH}
 *       点以下时自动开启（带一段冷却，详见 {@link PrismMirrorEvents}）。</li>
 * </ul>
 *
 * <p>它实现了 {@link AccessoryItem}（所以能进饰品栏），但把
 * {@link AccessoryItem#equipOnRightClick()} 关掉了——右键是「激活」而不是「戴上」，
 * 否则 {@code AccessoryEvents} 会抢先把右键吃掉、直接把它塞进饰品槽。
 * 想戴它请从饰品界面（V）拖进去。</p>
 */
public class PrismMirrorItem extends Item implements AccessoryItem
{
    public PrismMirrorItem(Properties properties)
    {
        super(properties);
    }

    /** 右键是「激活偏转」，不是「自动佩戴」。 */
    @Override
    public boolean equipOnRightClick()
    {
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer)
        {
            PrismMirrorEvents.activate(serverPlayer, false);
        }
        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("tooltip.mythic_relic.prism_mirror.1",
                PrismMirrorEvents.DURATION_TICKS / 20).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.mythic_relic.prism_mirror.effect")
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.mythic_relic.prism_mirror.reflect")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.mythic_relic.prism_mirror.immune")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.mythic_relic.prism_mirror.auto",
                (int) PrismMirrorEvents.AUTO_TRIGGER_HEALTH).withStyle(ChatFormatting.GREEN));

        // 状态行：和死亡之心同一套做法——把生效/冷却的剩余时间直接写在 tooltip 里，
        // 数据由服务端在开窗、登录、重生时同步过来（见 PrismMirrorSyncPacket）。
        int activeLeft = PrismMirrorClientData.activeLeft();
        int cooldownLeft = PrismMirrorClientData.cooldownLeft();
        if (activeLeft > 0)
        {
            tooltip.add(Component.translatable("tooltip.mythic_relic.prism_mirror.active",
                    formatTime(activeLeft)).withStyle(ChatFormatting.AQUA));
        }
        else if (cooldownLeft > 0)
        {
            tooltip.add(Component.translatable("tooltip.mythic_relic.prism_mirror.cooldown",
                    formatTime(cooldownLeft)).withStyle(ChatFormatting.RED));
        }
        else
        {
            tooltip.add(Component.translatable("tooltip.mythic_relic.prism_mirror.ready")
                    .withStyle(ChatFormatting.GREEN));
        }

        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.mythic_relic.prism_mirror.source")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }

    /** 与死亡之心同一套写法：不到一分钟报秒数，超过一分钟报 {@code M:SS}。 */
    private static String formatTime(int ticks)
    {
        int seconds = ticks / 20;
        int minutes = seconds / 60;
        int rest = seconds % 60;
        return minutes > 0 ? minutes + ":" + String.format("%02d", rest) : String.valueOf(seconds);
    }
}
