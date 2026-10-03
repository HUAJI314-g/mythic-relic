package com.mythicrelic.chaos;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.entity.ShadowDragonBlade;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 尼德霍格之印的主动能力，全部在服务端做最终判定。
 *
 * <ul>
 *   <li><b>中阶</b>：{@link #fireShadowBlade} 发射暗影龙刃；滑翔状态由
 *       {@link #setGliding} 维护，真正的滑翔物理交给客户端的
 *       {@code ChaosClientHandler}——原版的鞘翅物理写在 {@code LivingEntity.travel()} 里，
 *       只要把 {@code isFallFlying} 那位标志立起来，移动与姿势就都是白送的。</li>
 *   <li><b>高阶</b>：{@link #updateFlight} 授予 {@code mayfly}，剩下的「双击空格进入悬停」
 *       由原版自己的创造飞行逻辑处理，Ctrl 切换回动力滑翔也在客户端做。</li>
 *   <li><b>终阶</b>：{@link #unleashDomain} 释放混沌领域——一片跟着你移动的吞噬场。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID)
public final class ChaosAbilities
{
    // —————— 暗影龙刃 ——————
    /** 两次发射之间的最短间隔（tick）。 */
    private static final int BLADE_COOLDOWN = 10;
    /** 飞出去的速度（格/tick）。 */
    private static final float BLADE_SPEED = 1.6F;

    // —————— 混沌领域 ——————
    /** 领域半径（格）。 */
    private static final double DOMAIN_RADIUS = 8.0D;
    /** 领域持续时间（tick）。20 秒——原来是 10 秒，太短了撑不起「领域」的份量。 */
    private static final int DOMAIN_DURATION = 400;
    /** 领域冷却（tick）。 */
    private static final int DOMAIN_COOLDOWN = 1200;
    /** 每秒的基础伤害。 */
    private static final float DOMAIN_BASE_DAMAGE = 10.0F;
    /** 每秒额外造成的、目标最大生命的百分比。 */
    private static final float DOMAIN_MAX_HEALTH_RATIO = 0.05F;

    /**
     * 收起滑翔之后再宽限多少 tick，这段时间里依旧不结算摔落与撞击伤害。
     *
     * <p>落地那一 tick 的时序很微妙：客户端发现着地才发 GLIDE_STOP，而服务端的
     * {@code LivingFallEvent} 可能已经先跑了；再加上滑翔时玩家是躺着的姿势（碰撞箱 0.6 高），
     * 落地后姿势变回站立（1.8 高），原版的 {@code refreshDimensions()} 会把卡进方块的玩家
     * 往上顶一截，于是又凭空多出一段坠落。留一段宽限期把这两种情况都盖住。</p>
     */
    private static final int GLIDE_GRACE_TICKS = 60;

    private static final Map<UUID, Integer> BLADE_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> DOMAIN_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> GLIDE_GRACE = new HashMap<>();
    private static final List<ActiveDomain> DOMAINS = new ArrayList<>();

    private ChaosAbilities() {}

    // —————————————————————— 暗影龙刃 ——————————————————————

    /** 客户端松手时调用。{@code charge} 是蓄力比例 0~1。 */
    public static void fireShadowBlade(ServerPlayer player, float charge)
    {
        if (!ChaosEvents.hasMarkAt(player, ChaosTier.MID))
        {
            return;
        }
        if (BLADE_COOLDOWNS.getOrDefault(player.getUUID(), 0) > player.tickCount)
        {
            return;
        }
        BLADE_COOLDOWNS.put(player.getUUID(), player.tickCount + BLADE_COOLDOWN);

        float clamped = Math.max(0.0F, Math.min(1.0F, charge));
        Vec3 look = player.getLookAngle();
        // 手上那把武器的伤害由服务端自己读——客户端只报蓄力比例，别信它报伤害
        ShadowDragonBlade blade = new ShadowDragonBlade(player.level(), player, clamped, weaponBonus(player));
        blade.shoot(look.x, look.y, look.z, BLADE_SPEED, clamped < 0.5F ? 3.0F : 1.0F);
        player.level().addFreshEntity(blade);

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 0.9F, 1.4F - clamped * 0.4F);
    }

    /**
     * 手上这件东西算不算「可以借来蓄力的武器」。
     *
     * <p>两个条件，缺一不可：</p>
     * <ol>
     *   <li><b>右键没被占用</b>——{@code UseAnim.NONE}。弓、盾、三叉戟、食物、望远镜、
     *       以及模组自定义的 {@code CUSTOM} 动画都被这一条挡在外面，右键该干嘛还干嘛。</li>
     *   <li><b>本身带攻击力</b>——主手槽上有 {@code ATTACK_DAMAGE} 修饰符。
     *       这一条是为了挡住「右键另有用途但没动画」的那批东西：雪球、末影珍珠、水桶、方块……
     *       它们的 {@code UseAnim} 也是 NONE，只看第一条会误伤。</li>
     * </ol>
     */
    public static boolean isChargeableWeapon(ItemStack stack)
    {
        if (stack.isEmpty() || stack.getUseAnimation() != UseAnim.NONE)
        {
            return false;
        }
        return !stack.getAttributeModifiers(EquipmentSlot.MAINHAND).get(Attributes.ATTACK_DAMAGE).isEmpty();
    }

    /** 手上武器的攻击力；空手或不是武器时为 0。 */
    public static float weaponBonus(ServerPlayer player)
    {
        return isChargeableWeapon(player.getMainHandItem())
                ? (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE)
                : 0.0F;
    }

    // —————————————————————— 滑翔 ——————————————————————

    /** 客户端通知服务端「我开始/结束滑翔」。 */
    public static void setGliding(ServerPlayer player, boolean gliding)
    {
        PlayerChaos chaos = ModChaos.get(player);
        if (chaos == null)
        {
            return;
        }
        if (gliding && !ChaosEvents.hasMarkAt(player, ChaosTier.MID))
        {
            return;
        }
        chaos.setGliding(gliding);
        if (gliding)
        {
            GLIDE_GRACE.remove(player.getUUID());
        }
        else
        {
            player.stopFallFlying();
            GLIDE_GRACE.put(player.getUUID(), GLIDE_GRACE_TICKS);
        }
    }

    /**
     * 让烟花火箭在滑翔时也能点火推进。
     *
     * <p>原版 {@code FireworkRocketItem.use()} 的第一道判断就是 {@code player.isFallFlying()}，
     * 而我们的滑翔状态只立在客户端（原因见 {@code ChaosClientHandler} 的类注释），
     * 服务端这边是 false，于是原版直接 pass，烟花根本点不着。</p>
     *
     * <p>所以在这里照原版的样子自己补一支挂在他身上的 {@link FireworkRocketEntity}：
     * 烟花实体的推进逻辑会自己去认 {@code attachedToEntity}，而客户端的玩家是处于滑翔态的，
     * 推力照样吃得到。</p>
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        PlayerChaos chaos = ModChaos.get(player);
        if (chaos == null || !chaos.gliding())
        {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (!stack.is(Items.FIREWORK_ROCKET))
        {
            return;
        }

        FireworkRocketEntity rocket = new FireworkRocketEntity(player.level(), stack, player);
        player.level().addFreshEntity(rocket);
        if (!player.getAbilities().instabuild)
        {
            stack.shrink(1);
        }
        player.level().playSound(null, player.blockPosition(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0F, 1.0F);

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    /** 滑翔中不摔伤——风是托着你的。 */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event)
    {
        if (event.getEntity() instanceof Player player)
        {
            PlayerChaos chaos = ModChaos.get(player);
            if (chaos != null && (chaos.gliding() || inGlideGrace(player)))
            {
                event.setCanceled(true);
            }
        }
    }

    /**
     * 拦下滑翔期间的「撞击伤害」与漏网的摔落伤害。
     *
     * <p>原版鞘翅在撞墙时会结算 {@code flyIntoWall} 伤害，摔落伤害也可能因为时序问题
     * 绕过 {@link LivingFallEvent}。既然滑翔本身是我们接管的状态，这两类伤害就不该由它承担。</p>
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event)
    {
        if (!(event.getEntity() instanceof Player player))
        {
            return;
        }
        PlayerChaos chaos = ModChaos.get(player);
        if (chaos == null || (!chaos.gliding() && !inGlideGrace(player)))
        {
            return;
        }
        DamageSource source = event.getSource();
        if (source.is(DamageTypes.FALL)
                || source.is(DamageTypes.FLY_INTO_WALL)
                || source.is(DamageTypes.IN_WALL))
        {
            event.setCanceled(true);
        }
    }

    private static boolean inGlideGrace(Player player)
    {
        return GLIDE_GRACE.getOrDefault(player.getUUID(), 0) > 0;
    }

    // —————————————————————— 每 tick ——————————————————————

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player))
        {
            return;
        }
        PlayerChaos chaos = ModChaos.get(player);
        if (chaos == null)
        {
            return;
        }

        updateFlight(player);

        // 落地或入水就自动收起滑翔
        if (chaos.gliding() && (player.onGround() || player.isInWater() || player.isPassenger()))
        {
            setGliding(player, false);
        }

        // 滑翔中（以及刚落地的一小段宽限期）由我们接管坠落距离，
        // 服务端这边并不处于滑翔状态，不重置的话会自己攒出一身摔落伤害。
        if (chaos.gliding() || inGlideGrace(player))
        {
            player.resetFallDistance();
            if (!chaos.gliding())
            {
                int left = GLIDE_GRACE.getOrDefault(player.getUUID(), 0) - 1;
                if (left > 0)
                {
                    GLIDE_GRACE.put(player.getUUID(), left);
                }
                else
                {
                    GLIDE_GRACE.remove(player.getUUID());
                }
            }
        }
    }

    /**
     * 高阶之后授予 {@code mayfly}，也就是原版的创造飞行。
     *
     * <p>双击空格进入悬停、以及飞行状态的同步，都是原版 {@code LocalPlayer} 自己的逻辑，
     * 只要 {@code mayfly} 是开的就会自动生效，所以这里只需要管这个开关。</p>
     */
    private static void updateFlight(ServerPlayer player)
    {
        Abilities abilities = player.getAbilities();
        boolean canHover = ChaosEvents.hasMarkAt(player, ChaosTier.HIGH);

        if (canHover)
        {
            if (!abilities.mayfly)
            {
                abilities.mayfly = true;
                player.onUpdateAbilities();
            }
        }
        else if (abilities.mayfly && !player.isCreative() && !player.isSpectator())
        {
            abilities.mayfly = false;
            abilities.flying = false;
            player.onUpdateAbilities();
        }
    }

    // —————————————————————— 混沌领域 ——————————————————————

    /** 终阶的按键能力：以自己为中心撑开一片混沌领域；之后它会一直跟着你走，而不是留在原地。 */
    public static void unleashDomain(ServerPlayer player)
    {
        if (!ChaosEvents.hasMarkAt(player, ChaosTier.FINAL))
        {
            return;
        }
        if (DOMAIN_COOLDOWNS.getOrDefault(player.getUUID(), 0) > player.tickCount)
        {
            int left = (DOMAIN_COOLDOWNS.get(player.getUUID()) - player.tickCount) / 20;
            player.displayClientMessage(
                    Component.translatable("chaos.mythic_relic.domain_cooldown", left).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        DOMAIN_COOLDOWNS.put(player.getUUID(), player.tickCount + DOMAIN_COOLDOWN);
        DOMAINS.add(new ActiveDomain((ServerLevel) player.level(), player.getUUID(),
                player.position(), DOMAIN_DURATION));

        player.level().playSound(null, player.blockPosition(),
                SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 1.4F, 0.6F);
        player.sendSystemMessage(
                Component.translatable("chaos.mythic_relic.domain_cast").withStyle(ChatFormatting.DARK_PURPLE));
    }

    /** 领域每 tick 推进一次，每秒结算一次伤害。 */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || DOMAINS.isEmpty())
        {
            return;
        }
        Iterator<ActiveDomain> iterator = DOMAINS.iterator();
        while (iterator.hasNext())
        {
            ActiveDomain domain = iterator.next();
            if (--domain.ticksLeft <= 0)
            {
                iterator.remove();
                continue;
            }
            // 领域跟着主人走。主人下线 / 死亡 / 换了维度就当场散掉——
            // 留在原地既没了主人，又会白白往外刷粒子。
            if (!domain.followOwner())
            {
                iterator.remove();
                continue;
            }
            domain.spawnParticles();
            if (domain.ticksLeft % 20 == 0)
            {
                domain.strike();
            }
        }
    }

    /** 一片正在生效的混沌领域——中心每 tick 跟着主人走。 */
    private static final class ActiveDomain
    {
        private final ServerLevel level;
        private final UUID ownerId;
        /** 主人。每 tick 重新取一次，顺便当「还在不在」的判据。 */
        private ServerPlayer owner;
        /** 领域中心。施放时是主人当时的位置，之后每 tick 挪到他脚下。 */
        private Vec3 center;
        private int ticksLeft;

        ActiveDomain(ServerLevel level, UUID ownerId, Vec3 center, int ticksLeft)
        {
            this.level = level;
            this.ownerId = ownerId;
            this.center = center;
            this.ticksLeft = ticksLeft;
        }

        /**
         * 把领域中心挪到主人脚下。
         *
         * @return false 表示这一片该散场了：主人下线、死亡，或者跑到别的维度去了
         */
        boolean followOwner()
        {
            this.owner = this.level.getServer().getPlayerList().getPlayer(this.ownerId);
            if (this.owner == null || !this.owner.isAlive() || this.owner.level() != this.level)
            {
                return false;
            }
            this.center = this.owner.position();
            return true;
        }

        /**
         * 每 tick 撒一层紫黑粒子。
         *
         * <p>全部走批量发包（一次 {@code sendParticles} 里把 count 给足），
         * 别一个粒子一个包——那会 20 tick 里刷出上百个包。</p>
         */
        void spawnParticles()
        {
            // 整片领域只用「墨黑 + 暗紫 + 幽紫」三色，和暗影龙刃、龙语演出一套配色。
            // 早先这里掺了 SCULK_SOUL，它是青蓝色的（贴图 sculk_soul_*.png 实测 (5,42,50)），
            // 在一片紫黑里会泛出一层幽绿，所以换掉。

            // 外圈：贴着边界的暗紫龙息，把范围勾勒出来
            this.level.sendParticles(ParticleTypes.DRAGON_BREATH,
                    this.center.x, this.center.y + 0.3D, this.center.z,
                    32, DOMAIN_RADIUS * 0.88D, 0.35D, DOMAIN_RADIUS * 0.88D, 0.02D);

            // 内部：翻涌的墨色——每 tick 都铺一层，这是「浓郁」的底子
            this.level.sendParticles(ParticleTypes.SQUID_INK,
                    this.center.x, this.center.y + 0.2D, this.center.z,
                    24, DOMAIN_RADIUS * 0.62D, 0.25D, DOMAIN_RADIUS * 0.62D, 0.01D);

            // 幽紫的细点，向上飘
            if (this.ticksLeft % 3 == 0)
            {
                this.level.sendParticles(ParticleTypes.PORTAL,
                        this.center.x, this.center.y + 1.2D, this.center.z,
                        36, DOMAIN_RADIUS * 0.62D, 1.1D, DOMAIN_RADIUS * 0.62D, 0.05D);
            }

            // 每半秒从中心腾起一团浓烟，配一圈向上飘的暗紫余烬
            if (this.ticksLeft % 10 == 0)
            {
                this.level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        this.center.x, this.center.y + 0.8D, this.center.z,
                        20, 1.9D, 0.3D, 1.9D, 0.01D);
                this.level.sendParticles(ParticleTypes.DRAGON_BREATH,
                        this.center.x, this.center.y + 0.5D, this.center.z,
                        18, DOMAIN_RADIUS * 0.5D, 0.4D, DOMAIN_RADIUS * 0.5D, 0.01D);
            }
        }

        void strike()
        {
            AABB box = new AABB(this.center, this.center).inflate(DOMAIN_RADIUS);
            List<LivingEntity> targets = this.level.getEntitiesOfClass(LivingEntity.class, box);
            // 主人由 followOwner() 每 tick 刷新，这里直接用；保险起见仍然允许为空
            ServerPlayer owner = this.owner;

            for (LivingEntity target : targets)
            {
                if (target instanceof Player || target.isDeadOrDying() || !target.isAlive())
                {
                    continue;
                }
                float damage = DOMAIN_BASE_DAMAGE + target.getMaxHealth() * DOMAIN_MAX_HEALTH_RATIO;

                DamageSource source = owner != null
                        ? this.level.damageSources().playerAttack(owner)
                        : this.level.damageSources().magic();

                // 血量低于这一击的直接吞噬——不给它留下挣扎的余地
                if (target.getHealth() < damage)
                {
                    if (owner != null)
                    {
                        ChaosEvents.award(owner, Math.max(2, Math.round(target.getMaxHealth() / 4.0F)));
                    }
                    target.hurt(source, Float.MAX_VALUE);
                }
                else
                {
                    target.hurt(source, damage);
                }
            }
        }
    }
}
