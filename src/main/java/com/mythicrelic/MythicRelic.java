package com.mythicrelic;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.PrimaryLevelData;
import com.mythicrelic.registry.ModBlockEntities;
import com.mythicrelic.registry.ModBlocks;
import com.mythicrelic.registry.ModItems;
import com.mythicrelic.registry.ModMenuTypes;
import com.mythicrelic.registry.ModMobEffects;
import com.mythicrelic.registry.ModRecipes;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * 「神话之遗 / Mythic Relics」——一个以混沌与封印为核心的暗黑奇幻扩展。
 *
 * <p>内容涵盖：混沌能量体系（痕迹 / 碎片 / 结晶）、死亡能量体系（痕迹 / 碎片 / 结晶 / 死亡之心）、
 * 元素提取器、封印符文与混沌祭坛、封印之地维度、尼德霍格之印与混沌阶位、
 * 诅咒之剑「赫格尼」与诅咒祭坛、破界之羽，以及尼德霍格的龙语旁白。</p>
 *
 * <p>mod id 为 {@code mythic_relic}，与 {@code META-INF/mods.toml} 中的 {@code modId} 一致。</p>
 */
@Mod(MythicRelic.MODID)
public class MythicRelic
{
    /** 全局唯一的 mod id，所有 DeferredRegister 与资源命名空间都以它为准。 */
    public static final String MODID = "mythic_relic";
    /** slf4j 日志器。 */
    private static final Logger LOGGER = LogUtils.getLogger();
    /** 方块注册表（命名空间 mythic_relic）。 */
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    /** 物品注册表（命名空间 mythic_relic）。 */
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    /** 创造模式物品栏标签页注册表。 */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // 本模组的物品栏标签页。图标用本模组自己的徽记（MOD_ICON：由封面缩下来的金环 + ᛉ），
    // 标题走 lang 文件，免得显示成原始翻译键。
    public static final RegistryObject<CreativeModeTab> MAIN_TAB = CREATIVE_MODE_TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mythic_relic.main"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> ModItems.MOD_ICON.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.ELEMENT_EXTRACTOR_ITEM.get());
                output.accept(ModItems.CHAOS_TRACE.get());
                output.accept(ModItems.CHAOS_FRAGMENT.get());
                output.accept(ModItems.CHAOS_CRYSTAL.get());
                output.accept(ModItems.DEATH_TRACE.get());
                output.accept(ModItems.DEATH_FRAGMENT.get());
                output.accept(ModItems.DEATH_CRYSTAL.get());
                output.accept(ModItems.DEATH_HEART.get());
                output.accept(ModItems.SPACE_TRACE.get());
                output.accept(ModItems.SPACE_FRAGMENT.get());
                output.accept(ModItems.SPACE_CRYSTAL.get());
                output.accept(ModItems.PRISM_TRACE.get());
                output.accept(ModItems.PRISM_FRAGMENT.get());
                output.accept(ModItems.PRISM_CRYSTAL.get());
                output.accept(ModItems.PRISM_MIRROR.get());
                output.accept(ModItems.TOXIC_TRACE.get());
                output.accept(ModItems.TOXIC_FRAGMENT.get());
                output.accept(ModItems.TOXIC_CRYSTAL.get());
                output.accept(ModItems.POISON_RING.get());
                output.accept(ModItems.MYTHIC_BOOK.get());
                output.accept(ModItems.CHAOS_ALTAR_ITEM.get());
                output.accept(ModItems.CHAOS_RUNE_ITEM.get());
                output.accept(ModItems.NIDHOGG_MARK.get());
                output.accept(ModItems.CHAOS_KEY.get());
                output.accept(ModItems.RIFT_FEATHER.get());
                output.accept(ModItems.HEGNI_SWORD.get());
            }).build());

    public MythicRelic(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);

        // Element extractor registrations
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModMenuTypes.MENUS.register(modEventBus);
        ModRecipes.RECIPE_TYPES.register(modEventBus);
        ModRecipes.RECIPE_SERIALIZERS.register(modEventBus);
        ModMobEffects.MOB_EFFECTS.register(modEventBus);
        com.mythicrelic.registry.ModEntities.ENTITY_TYPES.register(modEventBus);
        // 「龙语」用的音效事件。音频是原版的，但事件必须挂在自己名下——
        // 否则字幕会蹦出「潜影贝打开了」，衰减距离也只有 16 格。详见 ModSounds。
        com.mythicrelic.registry.ModSounds.SOUNDS.register(modEventBus);
        // 诅咒祭坛的世界生成结构：类型 + 片段两个注册表都要挂，
        // 否则 /locate structure mythic_relic:hegni_altar 找不到它
        com.mythicrelic.registry.ModStructures.STRUCTURE_TYPES.register(modEventBus);
        com.mythicrelic.registry.ModStructures.PIECE_TYPES.register(modEventBus);
        // 蓄力用的自定义粒子（原版粒子改不了寿命，详见 ModParticles）
        com.mythicrelic.registry.ModParticles.PARTICLES.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        // Register our mod's ForgeConfigSpec so that Forge can create and load the config file for us
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event)
    {
        // 注册网络通道（饰品栏界面请求等）
        event.enqueueWork(com.mythicrelic.network.ModNetwork::register);
    }

    /**
     * 把「使用实验性设置的世界不受支持」那个弹窗直接确认掉。
     *
     * <p>这不是本模组的 bug，而是 Forge 对<b>任何通过数据包添加维度的模组</b>都会有的行为。
     * 链路是这样的：</p>
     * <ol>
     *   <li>vanilla 的 {@code RegistryDataLoader} 注册动态注册表条目时按来源分档——
     *       {@code resource.isBuiltin() ? Lifecycle.stable() : dataResult.lifecycle()}。
     *       内置资源（原版 jar）给 stable，数据包（含模组）给解码结果的生命周期；</li>
     *   <li>本模组用 {@code data/mythic_relic/dimension/sealed_land.json} 加了一个维度，
     *       于是 {@code minecraft:dimension} 注册表变成 {@code Experimental}——
     *       实测这是唯一一个非 stable 的注册表，其余全是 Stable；</li>
     *   <li>{@code WorldOpenFlows} 把 {@code datapackWorldgen().allRegistriesLifecycle()}
     *       交给 {@code getDataTag}，非 stable 就判定这个世界「使用了实验性设置」，
     *       每次<b>重新进入</b>世界都弹一次。</li>
     * </ol>
     *
     * <p>Forge 自己留了后门：{@code PrimaryLevelData.withConfirmedWarning(true)} 会就地写下
     * {@code confirmedExperimentalSettings}，{@code WorldOpenFlows} 见到它就跳过弹窗
     * （见 Forge 的 {@code WorldOpenFlows} 补丁：{@code skipConfirmation}）。
     * 这里在服务端启动时置上，随存档一并保存——玩家此后就不会再看到它了。</p>
     *
     * <p>注意：标记只在「注册表生命周期非 stable」时才被 {@code PrimaryLevelData} 采纳，
     * 而本模组的世界必然满足这一点，所以这里无条件设置是安全的。</p>
     */
    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event)
    {
        if (event.getServer().getWorldData() instanceof PrimaryLevelData levelData)
        {
            levelData.withConfirmedWarning(true);
        }
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }
    }
}
