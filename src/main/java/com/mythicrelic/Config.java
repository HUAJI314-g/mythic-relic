package com.mythicrelic;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 「神话之遗」的配置文件。
 *
 * <p>目前只暴露破界之羽的几个旋钮——它们是从 riftfeather 模组搬过来时留下的，
 * key 统一挂在 {@code riftFeather.} 组下。其余内容走数据包 / 代码常量，暂不需要配置项。</p>
 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config
{
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    // —————————————————————— 破界之羽 ——————————————————————

    private static final ForgeConfigSpec.IntValue RIFT_LOOK_RANGE = BUILDER
            .comment("破界之羽：手持右键时，视线能探到多远并把玩家送到那里（格）")
            .defineInRange("riftFeather.lookRange", 1000, 16, 4096);

    private static final ForgeConfigSpec.IntValue RIFT_LONG_RANGE = BUILDER
            .comment("破界之羽：潜行右键打开的坐标界面，允许输入的最大距离（格）")
            .defineInRange("riftFeather.longRange", 1000, 16, 4096);

    private static final ForgeConfigSpec.IntValue RIFT_COOLDOWN = BUILDER
            .comment("破界之羽：两次传送之间的冷却时间（tick，20 tick = 1 秒）")
            .defineInRange("riftFeather.cooldownTicks", 40, 0, 1200);

    private static final ForgeConfigSpec.BooleanValue RIFT_SLOW_FALLING = BUILDER
            .comment("破界之羽：落点悬空时，是否给予短暂的缓降效果以避免摔死")
            .define("riftFeather.grantSlowFallingWhenAirborne", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static int riftLookRange = 1000;
    public static int riftLongRange = 1000;
    public static int riftCooldownTicks = 40;
    public static boolean riftSlowFalling = true;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event)
    {
        riftLookRange = RIFT_LOOK_RANGE.get();
        riftLongRange = RIFT_LONG_RANGE.get();
        riftCooldownTicks = RIFT_COOLDOWN.get();
        riftSlowFalling = RIFT_SLOW_FALLING.get();
    }
}
