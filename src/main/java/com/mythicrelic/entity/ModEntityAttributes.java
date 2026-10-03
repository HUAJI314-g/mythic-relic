package com.mythicrelic.entity;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 注册自定义实体的属性。 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntityAttributes
{
    private ModEntityAttributes() {}

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event)
    {
        event.put(ModEntities.NIDHOGG.get(), NidhoggEntity.createAttributes().build());
    }
}
