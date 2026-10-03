package com.mythicrelic.ritual;

import com.mythicrelic.MythicRelic;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 驱动 {@link ChaosRitualManager} 的 Forge 事件入口。 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID)
public final class ChaosRitualEvents
{
    private ChaosRitualEvents() {}

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        if (event.level instanceof ServerLevel serverLevel)
        {
            ChaosRitualManager.tick(serverLevel);
            ChaosScheduler.tick(serverLevel);
        }
    }
}
