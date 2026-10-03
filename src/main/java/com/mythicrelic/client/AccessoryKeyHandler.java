package com.mythicrelic.client;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.network.ModNetwork;
import com.mythicrelic.network.OpenAccessoriesPacket;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 客户端每 tick 检查快捷键：按下 V 键就请求服务端打开饰品栏。 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID, value = Dist.CLIENT)
public final class AccessoryKeyHandler
{
    private AccessoryKeyHandler() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null)
        {
            return;
        }
        while (ModKeyMappings.OPEN_ACCESSORIES.consumeClick())
        {
            ModNetwork.CHANNEL.sendToServer(new OpenAccessoriesPacket());
        }
    }
}
