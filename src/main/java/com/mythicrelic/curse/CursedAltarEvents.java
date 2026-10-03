package com.mythicrelic.curse;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.block.HegniAltarBlock;
import com.mythicrelic.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 诅咒祭坛的凋零光环，以及拔剑进度的挂载与清零。
 *
 * <p><b>祭坛本身不在这里生成了</b>——它现在是一个真正的世界生成结构
 * （{@code worldgen/HegniAltarStructure} + {@code data/mythic_relic/worldgen/} 下的
 * structure 与 structure_set），所以 {@code /locate structure mythic_relic:hegni_altar}
 * 找得到它，分布也交给原版 {@code random_spread} 那套间距算法。</p>
 *
 * <p>这里只剩两件事：光环、以及玩家数据。</p>
 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID)
public final class CursedAltarEvents
{
    private static final ResourceLocation CAPABILITY_ID =
            new ResourceLocation(MythicRelic.MODID, "curse");

    /** 每多少 tick 扫一次凋零光环。 */
    private static final int AURA_INTERVAL = 20;
    /** 凋零效果每次续多久（tick）。离开范围后大约这么久消退。 */
    private static final int WITHER_DURATION = 60;

    private static int tickCounter;

    private CursedAltarEvents() {}

    // —————————————————————— 能力挂载 ——————————————————————

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event)
    {
        if (event.getObject() instanceof Player)
        {
            event.addCapability(CAPABILITY_ID, new ModCurse.Provider());
        }
    }

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event)
    {
        event.register(ModCurse.Data.class);
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event)
    {
        event.getOriginal().getCapability(ModCurse.CURSE).ifPresent(oldStore ->
                event.getEntity().getCapability(ModCurse.CURSE).ifPresent(newStore ->
                        newStore.deserializeNBT(oldStore.serializeNBT())));
    }

    /** 死了就把拔剑进度清零——「在死亡之前点满一百下」是这把剑的规矩。 */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            ModCurse.Data data = ModCurse.get(player);
            if (data != null)
            {
                data.setHegniProgress(0);
            }
        }
    }

    // —————————————————————— 凋零光环 ——————————————————————

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        if (overworld == null || overworld.players().isEmpty())
        {
            return;
        }
        if (++tickCounter % AURA_INTERVAL == 0)
        {
            applyWitherAura(overworld);
        }
    }

    /**
     * 扫玩家身边一小块找祭坛。
     *
     * <p>之所以不用「记录祭坛坐标表」：结构是随区块生成出来的，没有一份现成的清单。
     * 而玩家能受影响就说明祭坛一定在他身边、区块一定是加载着的，
     * 所以就地扫一下最省事，也不会强制加载任何区块。</p>
     */
    private static void applyWitherAura(ServerLevel level)
    {
        int r = (int) Math.ceil(HegniAltarBlock.WITHER_RADIUS);
        for (ServerPlayer player : level.players())
        {
            BlockPos origin = player.blockPosition();
            BlockPos min = origin.offset(-r, -4, -r);
            BlockPos max = origin.offset(r, 4, r);

            for (BlockPos pos : BlockPos.betweenClosed(min, max))
            {
                if (level.getBlockState(pos).is(ModBlocks.HEGNI_ALTAR.get()))
                {
                    // amplifier = 1 就是「凋零 II」
                    player.addEffect(new MobEffectInstance(MobEffects.WITHER,
                            WITHER_DURATION, 1, false, true, true));
                    break;
                }
            }
        }
    }
}
