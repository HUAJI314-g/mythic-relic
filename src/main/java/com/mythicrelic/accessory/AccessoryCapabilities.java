package com.mythicrelic.accessory;

import com.mythicrelic.MythicRelic;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 向 Forge 注册「饰品栏」能力。 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class AccessoryCapabilities
{
    private AccessoryCapabilities() {}

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event)
    {
        event.register(PlayerAccessories.class);
    }
}
