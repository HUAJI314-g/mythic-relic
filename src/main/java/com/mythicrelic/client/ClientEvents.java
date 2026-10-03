package com.mythicrelic.client;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.client.model.ChaosWingsModel;
import com.mythicrelic.client.model.NidhoggModel;
import com.mythicrelic.client.particle.ChaosMoteParticle;
import com.mythicrelic.client.render.ChaosWingsLayer;
import com.mythicrelic.client.render.NidhoggRenderer;
import com.mythicrelic.client.render.ShadowDragonBladeRenderer;
import com.mythicrelic.registry.ModEntities;
import com.mythicrelic.registry.ModMenuTypes;
import com.mythicrelic.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = MythicRelic.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents
{
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event)
    {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenuTypes.ELEMENT_EXTRACTOR_MENU.get(), ElementExtractorScreen::new);
            MenuScreens.register(ModMenuTypes.ACCESSORIES_MENU.get(), AccessoryScreen::new);
        });
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event)
    {
        event.registerLayerDefinition(NidhoggModel.LAYER_LOCATION, NidhoggModel::createBodyLayer);
        event.registerLayerDefinition(ChaosWingsModel.LAYER_LOCATION, ChaosWingsModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(ModEntities.NIDHOGG.get(), NidhoggRenderer::new);
        event.registerEntityRenderer(ModEntities.SHADOW_DRAGON_BLADE.get(), ShadowDragonBladeRenderer::new);
    }

    /**
     * 挂上自定义粒子的 provider。
     *
     * <p>漏了这一步的症状很有意思：粒子类型注册得好好的、也不报错，但什么都不显示
     * （原版找不到 provider 就只是一个 warn 级的静默失败）。</p>
     *
     * <p>两种微尘共用 {@link ChaosMoteParticle}，差别只在基色。</p>
     */
    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event)
    {
        event.registerSpriteSet(ModParticles.CHAOS_MOTE.get(),
                sprites -> new ChaosMoteParticle.Provider(sprites,
                        ChaosMoteParticle.PURPLE_RED, ChaosMoteParticle.PURPLE_GREEN,
                        ChaosMoteParticle.PURPLE_BLUE));
        event.registerSpriteSet(ModParticles.CHAOS_MOTE_DARK.get(),
                sprites -> new ChaosMoteParticle.Provider(sprites,
                        ChaosMoteParticle.DARK_RED, ChaosMoteParticle.DARK_GREEN,
                        ChaosMoteParticle.DARK_BLUE));
    }

    /** 把混沌龙翼挂到玩家模型上（粗/细两种皮肤都要）。 */
    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event)
    {
        for (String skin : event.getSkins())
        {
            PlayerRenderer renderer = event.getPlayerSkin(skin);
            if (renderer != null)
            {
                renderer.addLayer(new ChaosWingsLayer(renderer, Minecraft.getInstance().getEntityModels()));
            }
        }
    }
}
