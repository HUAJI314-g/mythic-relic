package com.mythicrelic.client;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 破界之羽：潜行右键打开坐标界面。
 *
 * <p>为什么要在客户端拦：右键「物品」和右键「方块」是两条不同的路径，两条都得接——
 * 对着空气潜行右键走 {@code RightClickItem}，对着方块潜行右键走 {@code RightClickBlock}。
 * 只在客户端拦是因为界面本来就只存在于客户端；服务端的传送判定另有其人
 * （见 {@code RiftTeleportPacket} 与 {@code RiftFeatherItem}）。</p>
 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID, value = Dist.CLIENT)
public final class RiftFeatherClientEvents
{
    private RiftFeatherClientEvents() {}

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event)
    {
        openSneakScreen(event.getEntity().isShiftKeyDown(), event.getItemStack(), () ->
        {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        });
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        openSneakScreen(event.getEntity().isShiftKeyDown(), event.getItemStack(), () ->
        {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.PASS);
        });
    }

    private static void openSneakScreen(boolean sneaking, ItemStack stack, Runnable cancel)
    {
        if (!sneaking || !stack.is(ModItems.RIFT_FEATHER.get()))
        {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null)
        {
            return;
        }
        cancel.run();
        minecraft.setScreen(new CoordinateScreen());
    }
}
