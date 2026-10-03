package com.mythicrelic.client;

import com.mythicrelic.MythicRelic;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** 客户端的按键绑定：V 打开饰品栏，R 释放混沌领域。 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ModKeyMappings
{
    public static final KeyMapping OPEN_ACCESSORIES = new KeyMapping(
            "key.mythic_relic.open_accessories",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.mythic_relic");

    /** 终阶的混沌领域。 */
    public static final KeyMapping CHAOS_DOMAIN = new KeyMapping(
            "key.mythic_relic.chaos_domain",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.mythic_relic");

    private ModKeyMappings() {}

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event)
    {
        event.register(OPEN_ACCESSORIES);
        event.register(CHAOS_DOMAIN);
    }
}
