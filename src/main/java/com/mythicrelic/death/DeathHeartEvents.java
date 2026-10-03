package com.mythicrelic.death;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.accessory.ModAccessories;
import com.mythicrelic.network.DeathHeartSyncPacket;
import com.mythicrelic.registry.ModItems;
import com.mythicrelic.registry.ModMobEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 死亡之心的「死亡回溯」全部逻辑：
 *
 * <ol>
 *   <li>玩家血量归零时在 {@link LivingDeathEvent} 拦截，把死亡之心饰品栏物品触发</li>
 *   <li>真实血量固定在 0.5f，之后所有伤害都扣到虚拟血量里（可以到负数）</li>
 *   <li>5 分钟回溯期内 {@link TickEvent} 每秒检查：到期时虚拟血量 >= 0 就活着，< 0 就真死亡</li>
 *   <li>回溯期间如果玩家回血让虚拟血量回升到安全线以上，自动结束回溯</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID)
public final class DeathHeartEvents
{
    /** 真实血量下限——回溯期间永远不会低于这个值（不会真正"死"）。 */
    private static final float REAL_HP_FLOOR = 0.5f;
    /** 每秒检查一次。 */
    private static final int CHECK_INTERVAL = 20;

    /** 回溯期间同步血量的间隔（tick）。比 CHECK_INTERVAL 密，HUD 上的血量才不会一顿一顿的。 */
    private static final int HP_SYNC_INTERVAL = 5;
    /** 回溯期间回血超过这个值就可以安全退出。 */
    private static final float VIRTUAL_HEAL_THRESHOLD = 5.0f;

    private DeathHeartEvents() {}

    // —————————————————————— Capability 注册与挂载 ——————————————————————

    @SubscribeEvent
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event)
    {
        event.register(PlayerDeathHeart.class);
    }

    @SubscribeEvent
    public static void onAttachCapabilities(AttachCapabilitiesEvent<Entity> event)
    {
        if (event.getObject() instanceof Player)
        {
            event.addCapability(ModDeathHeart.ID, new ModDeathHeart.Provider());
        }
    }

    // —————————————————————— 死亡回溯触发 ——————————————————————

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }

        // 已经在回溯中了——不二次触发，等这次回溯到期时再决定真死
        PlayerDeathHeart dh = ModDeathHeart.get(player);
        if (dh == null || dh.isActive())
        {
            return;
        }

        // 注意：必须用世界游戏时间（level.getGameTime()），不能用 player.tickCount——
        // 前者跨维度/重登一致，且与客户端 DeathHeartClientData 使用同一时间基准；
        // 后者会随实体重生而重置，会导致冷却判定和显示都错乱。
        int tick = (int) player.level().getGameTime();
        if (!dh.canTrigger(tick))
        {
            // 冷却没好，正常死亡
            return;
        }

        // 饰品栏里有没有死亡之心
        boolean equipped = player.getCapability(ModAccessories.ACCESSORIES)
                .map(acc -> acc.contains(ModItems.DEATH_HEART.get()))
                .orElse(false);
        if (!equipped)
        {
            return;
        }

        // 拦截死亡
        event.setCanceled(true);
        dh.trigger(tick);
        player.setHealth(REAL_HP_FLOOR);

        // 加效果（右上角药水栏显示倒计时）
        player.addEffect(new MobEffectInstance(ModMobEffects.DEATH_REGRESSION.get(),
                PlayerDeathHeart.DURATION_TICKS, 0, false, false));

        // 这一发带上动画标记：客户端收到就播一次死亡之心的「图腾式」弹出动画。
        DeathHeartSyncPacket.sendActivation(player);

        // 音效 + 消息
        player.sendSystemMessage(Component.translatable("death.mythic_relic.regression.triggered")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE,
                SoundSource.PLAYERS, 1.0F, 1.1F);

        // 白色不死图腾粒子特效（服务端发给附近玩家）
        spawnWhiteTotemParticles(player);
    }

    /**
     * 在玩家附近生成一圈白色粒子，模拟不死图腾触发效果。
     *
     * <p>原版不死图腾用金色的 {@code TOTEM_OF_UNDYING} 粒子，
     * 这里组合 {@code PORTAL}（白灰漩涡）+ {@code EXPLOSION}（白爆）+ {@code CLOUD}（小云）+ {@code ENCHANTED_HIT}（白色打击）
     * 做出一圈向上升腾的白色光环。</p>
     */
    private static void spawnWhiteTotemParticles(ServerPlayer player)
    {
        var level = player.serverLevel();
        double px = player.getX();
        double py = player.getY() + player.getBbHeight() * 0.5;
        double pz = player.getZ();

        // 1) 一圈白灰漩涡（类似原版 totem 的金色漩涡，换成 portal 的白灰色）
        int ringCount = 24;
        for (int i = 0; i < ringCount; i++)
        {
            double angle = (i * Math.PI * 2 / ringCount) + player.tickCount * 0.01;
            double radius = 0.5 + (i % 3) * 0.15;
            double offsetY = Math.random() * player.getBbHeight();
            double ox = Math.cos(angle) * radius;
            double oz = Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.PORTAL,
                    px + ox, py - player.getBbHeight()*0.5 + offsetY, pz + oz,
                    1,
                    0, 0.05 + Math.random()*0.05, 0, 0.02);
        }

        // 2) 中心白色大爆
        level.sendParticles(ParticleTypes.EXPLOSION,
                px, py, pz, 1, 0, 0, 0, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER,
                px, py, pz, 1, 0, 0, 0, 0.0);

        // 3) 上飘的小云（白色）
        for (int i = 0; i < 8; i++)
        {
            double ox = (Math.random() - 0.5) * 1.2;
            double oz = (Math.random() - 0.5) * 1.2;
            level.sendParticles(ParticleTypes.CLOUD,
                    px + ox, py + 0.3 + Math.random()*0.8, pz + oz,
                    1, 0, 0.1, 0, 0.0);
        }

        // 4) 打击闪光（白色魔法光）
        for (int i = 0; i < 6; i++)
        {
            double ox = (Math.random() - 0.5) * 1.5;
            double oy = (Math.random() - 0.5) * player.getBbHeight();
            double oz = (Math.random() - 0.5) * 1.5;
            level.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    px + ox, py + oy, pz + oz,
                    1, 0, 0, 0, 0.0);
        }
    }

    // —————————————————————— 回溯期间：伤害扣虚拟血量 ——————————————————————

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        PlayerDeathHeart dh = ModDeathHeart.get(player);
        if (dh == null || !dh.isActive())
        {
            return;
        }

        float realHp = player.getHealth();
        float damage = event.getAmount();
        float allowed = realHp - REAL_HP_FLOOR;   // real hp 最多能扣到 REAL_HP_FLOOR

        if (damage <= allowed)
        {
            // 这次伤害完全可以从 real hp 扣掉，不用碰 virtual
            return;
        }

        // 部分扣 real hp、部分扣 virtual
        float realPart = Math.max(0, allowed);
        float virtualPart = damage - realPart;
        event.setAmount(realPart);
        dh.subtractVirtual(virtualPart);
        // 注意：如果 realPart 是 0（比如 real hp 已经卡住在 0.5），event.setAmount(0) 会让这一伤害事件相当于被 cancel
        // 但这样玩家不会有红屏——我们希望让玩家感受到"还在受伤但死不了"的体验。
        // 那就让 realPart 至少是 0.01，让 hurt 事件正常传播（击退、红屏都会有）
        if (realPart == 0)
        {
            event.setAmount(0.01f);
        }
    }

    // —————————————————————— Tick：检查回溯到期 / 回血安全退出 ——————————————————————

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        if (!(event.player instanceof ServerPlayer player))
        {
            return;
        }

        PlayerDeathHeart dh = ModDeathHeart.get(player);
        if (dh == null || !dh.isActive())
        {
            return;
        }

        long gameTime = player.level().getGameTime();

        // 回溯期间每 5 tick 推一次同步包：客户端 HUD 要靠它实时显示（可能为负的）真实血量。
        // 完整逻辑仍然每秒才跑一次，见下面的 CHECK_INTERVAL。
        if (gameTime % HP_SYNC_INTERVAL == 0)
        {
            DeathHeartSyncPacket.send(player);
        }

        // 每秒检查一次即可（同样以世界游戏时间为基准）
        if (gameTime % CHECK_INTERVAL != 0)
        {
            return;
        }

        int tick = (int) gameTime;
        float vh = dh.virtualHealth();

        // 先处理回血：玩家自然回血/金苹果之类的效果会先加 real hp
        // 这里把超出 REAL_HP_FLOOR 的那部分当作恢复虚拟血量
        float realHp = player.getHealth();
        float excessHeal = realHp - REAL_HP_FLOOR;
        if (excessHeal > 0)
        {
            dh.addVirtual(excessHeal);
            player.setHealth(REAL_HP_FLOOR);
            vh = dh.virtualHealth();
        }

        // 到期检查
        if (tick >= dh.activeEndTick())
        {
            if (vh >= 0)
            {
                // 活下来了——结束回溯，恢复到 maxHealth/2
                float restoreHp = player.getMaxHealth() * 0.5f;
                player.setHealth(Math.max(realHp, restoreHp));
                dh.end();
                player.removeEffect(ModMobEffects.DEATH_REGRESSION.get());
                player.sendSystemMessage(Component.translatable("death.mythic_relic.regression.survived")
                        .withStyle(ChatFormatting.GREEN));
                player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE,
                        SoundSource.PLAYERS, 1.0F, 0.8F);
            }
            else
            {
                // 回溯到期且仍未恢复 → 真死亡
                dh.end();
                player.removeEffect(ModMobEffects.DEATH_REGRESSION.get());
                player.hurt(player.damageSources().playerAttack(player), 9999f);
                // 上面那招如果触发 LivingDeathEvent，此时 dh.isActive() 已经 false，不会二次拦截
                player.sendSystemMessage(Component.translatable("death.mythic_relic.regression.expired")
                        .withStyle(ChatFormatting.RED));
            }
            DeathHeartSyncPacket.send(player);
            return;
        }

        // 还没到期但已经足够安全了 → 自动结束
        if (vh >= VIRTUAL_HEAL_THRESHOLD)
        {
            float restoreHp = player.getMaxHealth() * 0.5f;
            player.setHealth(Math.max(realHp, restoreHp));
            dh.end();
            player.removeEffect(ModMobEffects.DEATH_REGRESSION.get());
            player.sendSystemMessage(Component.translatable("death.mythic_relic.regression.survived")
                    .withStyle(ChatFormatting.GREEN));
            player.level().playSound(null, player.blockPosition(), SoundEvents.TOTEM_USE,
                    SoundSource.PLAYERS, 1.0F, 0.8F);
            DeathHeartSyncPacket.send(player);
        }
    }

    // —————————————————————— 登录/重生同步 ——————————————————————

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            DeathHeartSyncPacket.send(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            DeathHeartSyncPacket.send(player);
        }
    }

    /** 提供给外部（比如混沌进度触发后）检查的便捷方法。 */
    public static boolean hasDeathHeart(Player player)
    {
        return player.getCapability(ModAccessories.ACCESSORIES)
                .map(acc -> acc.contains(ModItems.DEATH_HEART.get()))
                .orElse(false);
    }
}
