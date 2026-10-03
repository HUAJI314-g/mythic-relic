package com.mythicrelic.item;

import com.mythicrelic.chaos.ChaosEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * 「混沌能量残息 / 碎片 / 结晶」——蕴含混沌进度的消耗品。
 *
 * <p>右键即可食用，直接换成一份混沌进度。它们同时也是仪式用的材料，
 * 但祭坛的 {@code use()} 始终返回 SUCCESS，所以拿着结晶右键祭坛不会误食。</p>
 */
public class ChaosEssenceItem extends Item
{
    private final int progress;

    public ChaosEssenceItem(Properties properties, int progress)
    {
        super(properties);
        this.progress = progress;
    }

    /** 食用后获得的混沌进度。 */
    public int progress()
    {
        return this.progress;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);

        if (!ChaosEvents.hasMark(player))
        {
            if (!level.isClientSide())
            {
                player.displayClientMessage(
                        Component.translatable("chaos.mythic_relic.need_mark").withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide())
        {
            ChaosEvents.award(player, this.progress);
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 0.8F, 1.4F);
            if (!player.getAbilities().instabuild)
            {
                stack.shrink(1);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("tooltip.mythic_relic.chaos_essence", this.progress)
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("tooltip.mythic_relic.chaos_essence.use")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
