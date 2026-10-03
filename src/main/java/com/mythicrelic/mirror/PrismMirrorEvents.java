package com.mythicrelic.mirror;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.accessory.ModAccessories;
import com.mythicrelic.network.PrismMirrorSyncPacket;
import com.mythicrelic.registry.ModItems;
import com.mythicrelic.registry.ModMobEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 「神棱偏转镜」的判定逻辑。
 *
 * <h2>偏转窗口</h2>
 * <p>用一个 MobEffect（{@link ModMobEffects#PRISM_DEFLECTION}）来表示「正在偏转」，
 * 而不是自己维护一套计时器——药水栏里能直接看到倒计时，死亡 / 换维度也不会漏掉。
 * 开窗口的两条路：</p>
 * <ul>
 *   <li><b>右键</b>（{@code PrismMirrorItem#use}）→ {@link #activate}；</li>
 *   <li><b>自动</b>：戴着镜子且血量掉到 {@value #AUTO_TRIGGER_HEALTH} 以下时，
 *       由 {@link #onPlayerTick} 兜底触发。</li>
 * </ul>
 *
 * <h2>伤害规则（用户口径）</h2>
 * <ul>
 *   <li><b>有源伤害</b>（能追到攻击者，且不是自己）→ <b>全额反弹</b>给攻击者，
 *       自己一点不吃；</li>
 *   <li><b>无源伤害</b>（摔落 / 岩浆 / 虚空 / 溺水…）→ 直接<b>免疫</b>；</li>
 *   <li><b>自己造成的伤害</b>（自己的箭、自爆等，来源就是本人）→ 直接<b>免疫</b>。</li>
 * </ul>
 *
 * <h2>为什么要有 {@link #reflecting} 这个开关</h2>
 * <p>反弹走的是 {@code attacker.hurt(...)}，那会<b>同步</b>触发攻击者那边的
 * {@link LivingHurtEvent}。如果攻击者身上也开着偏转镜，它会把伤害再弹回来、
 * 我们再弹过去……在同一个 tick 里无限递归，直接 StackOverflow。
 * 所以反弹期间把这个开关立起来，链上的二次命中一律只免疫、不再反弹。</p>
 *
 * <h2>自动触发的冷却</h2>
 * <p>不加冷却的话，「血量 &lt; 5 → 开 60 秒 → 到期时人还在 5 点以下 → 立刻再开」
 * 就成了永久无敌。所以自动触发之后要等到窗口结束再隔
 * {@value #AUTO_COOLDOWN_TICKS} tick 才允许下一次。冷却只记在内存里——
 * 它本来就不需要跨存档，重启后重新计时是合理的。</p>
 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID)
public final class PrismMirrorEvents
{
    /** 偏转窗口时长（tick）。60 秒。 */
    public static final int DURATION_TICKS = 1200;

    /** 自动触发阈值：血量低于这个值（不含）就自动开窗。 */
    public static final float AUTO_TRIGGER_HEALTH = 5.0F;

    /** 自动触发后的额外冷却（tick），从窗口结束那一刻起算。60 秒。 */
    public static final int AUTO_COOLDOWN_TICKS = 1200;

    /** 正在反弹链里。挡住「两面镜子互弹」的死循环。 */
    private static boolean reflecting = false;

    /** 每个玩家下一次允许自动触发的时间点（世界时间）。 */
    private static final Map<UUID, Long> AUTO_READY_AT = new HashMap<>();

    private PrismMirrorEvents() {}

    // —————————————————————— 开窗 ——————————————————————

    /**
     * 开启偏转窗口。
     *
     * @param automatic 是否由「血量过低」自动触发（会额外记一段冷却）
     */
    public static void activate(ServerPlayer player, boolean automatic)
    {
        player.addEffect(new MobEffectInstance(ModMobEffects.PRISM_DEFLECTION.get(),
                DURATION_TICKS, 0, false, true, true));

        long now = player.level().getGameTime();
        if (automatic)
        {
            // 窗口结束 + 额外冷却，之后才允许下一次自动触发
            AUTO_READY_AT.put(player.getUUID(), now + DURATION_TICKS + AUTO_COOLDOWN_TICKS);
            player.displayClientMessage(
                    Component.translatable("mirror.mythic_relic.auto").withStyle(ChatFormatting.AQUA), true);
        }
        else
        {
            player.displayClientMessage(
                    Component.translatable("mirror.mythic_relic.activated").withStyle(ChatFormatting.AQUA), true);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, 0.9F, 1.6F);

        // 状态变了就同步一次——客户端拿这两个「结束时刻」自己倒计时，不用每 tick 发包
        sync(player);
    }

    /** 饰品栏里有没有这面镜子。 */
    public static boolean hasMirror(Player player)
    {
        return player.getCapability(ModAccessories.ACCESSORIES)
                .map(accessories -> accessories.contains(ModItems.PRISM_MIRROR.get()))
                .orElse(false);
    }

    // —————————————————————— 同步 ——————————————————————

    /**
     * 把此刻的镜子状态发给本人（tooltip 靠它显示剩余时间）。
     *
     * <p>两个时刻都是<b>绝对世界 tick</b>。自动触发冷却那张表只活在服务端内存里，
     * 所以玩家登录 / 重生时要补发一次，否则 tooltip 会一直显示成「就绪」。</p>
     */
    public static void sync(ServerPlayer player)
    {
        long now = player.level().getGameTime();

        MobEffectInstance effect = player.getEffect(ModMobEffects.PRISM_DEFLECTION.get());
        long activeEnd = effect == null ? 0L : now + effect.getDuration();

        Long ready = AUTO_READY_AT.get(player.getUUID());
        long cooldownEnd = ready == null ? 0L : ready;

        PrismMirrorSyncPacket.send(player, activeEnd, cooldownEnd);
    }

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            sync(player);
        }
    }

    // —————————————————————— 自动触发 ——————————————————————

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player))
        {
            return;
        }
        // 每 5 tick 查一次就够，没必要每 tick 都算
        if (player.tickCount % 5 != 0)
        {
            return;
        }
        if (player.isDeadOrDying() || player.isCreative() || player.isSpectator())
        {
            return;
        }
        if (player.hasEffect(ModMobEffects.PRISM_DEFLECTION.get()))
        {
            return;
        }
        if (player.getHealth() >= AUTO_TRIGGER_HEALTH)
        {
            return;
        }
        if (!hasMirror(player))
        {
            return;
        }
        Long ready = AUTO_READY_AT.get(player.getUUID());
        if (ready != null && player.level().getGameTime() < ready)
        {
            return;
        }
        activate(player, true);
    }

    // —————————————————————— 偏转判定 ——————————————————————

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event)
    {
        if (!(event.getEntity() instanceof Player player))
        {
            return;
        }
        if (!player.hasEffect(ModMobEffects.PRISM_DEFLECTION.get()))
        {
            return;
        }

        // 反弹链上的二次命中：只免疫，不再反弹，避免两面镜子互弹成死循环
        if (reflecting)
        {
            event.setCanceled(true);
            return;
        }

        Entity attacker = event.getSource().getEntity();
        // 无源伤害，或自己打自己 —— 一律直接免疫
        if (attacker == null || attacker == player)
        {
            event.setCanceled(true);
            return;
        }

        // 有源伤害 —— 原样折回去，自己一点不吃
        float amount = event.getAmount();
        event.setCanceled(true);
        reflecting = true;
        try
        {
            attacker.hurt(player.damageSources().thorns(player), amount);
        }
        finally
        {
            reflecting = false;
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK,
                SoundSource.PLAYERS, 0.8F, 1.8F);
    }
}
