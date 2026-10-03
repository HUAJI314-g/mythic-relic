package com.mythicrelic.event;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.registry.ModItems;
import com.mythicrelic.ritual.ChaosScheduler;
import com.mythicrelic.ritual.SealedLand;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 「混沌之钥」的获取与使用：
 * <ol>
 *   <li>击败末影龙 → 钥匙悬浮在末地祭坛（返回传送门）上方；</li>
 *   <li>手持钥匙右键末影折跃门 → 踏入封印之地。</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID)
public final class ChaosKeyEvents
{
    /** 末影龙的死亡动画约 200 tick，祭坛要到那时才生成，所以钥匙延后放置。 */
    private static final int KEY_DROP_DELAY = 240;

    /** 钥匙悬浮在祭坛顶面之上的高度。 */
    private static final double ALTAR_HOVER = 2.0D;

    private ChaosKeyEvents() {}

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event)
    {
        if (!(event.getEntity() instanceof EnderDragon dragon))
        {
            return;
        }
        if (!(dragon.level() instanceof ServerLevel endLevel) || !endLevel.dimension().equals(Level.END))
        {
            return;
        }
        ChaosScheduler.delay(endLevel, KEY_DROP_DELAY, () -> spawnChaosKey(endLevel));
    }

    private static void spawnChaosKey(ServerLevel endLevel)
    {
        // 末地祭坛就在末地主岛原点，取地表高度后往上抬，让它悬在祭坛正上方。
        int surface = endLevel.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        ItemEntity key = new ItemEntity(endLevel, 0.5D, surface + ALTAR_HOVER, 0.5D,
                new ItemStack(ModItems.CHAOS_KEY.get()));
        key.setNoGravity(true);
        key.setDeltaMovement(Vec3.ZERO);
        key.setPickUpDelay(40);
        key.setUnlimitedLifetime();
        endLevel.addFreshEntity(key);

        for (ServerPlayer player : endLevel.players())
        {
            player.displayClientMessage(Component.translatable("key.mythic_relic.appeared")
                    .withStyle(ChatFormatting.DARK_PURPLE), false);
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        if (!event.getLevel().getBlockState(event.getPos()).is(Blocks.END_GATEWAY))
        {
            return;
        }

        ItemStack held = event.getItemStack();
        if (!held.is(ModItems.CHAOS_KEY.get()))
        {
            player.displayClientMessage(Component.translatable("key.mythic_relic.need_key")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }

        if (!SealedLand.enter(player))
        {
            player.displayClientMessage(Component.translatable("key.mythic_relic.failed")
                    .withStyle(ChatFormatting.RED), false);
            return;
        }

        if (!player.getAbilities().instabuild)
        {
            held.shrink(1);
        }

        event.setCanceled(true);
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }
}
