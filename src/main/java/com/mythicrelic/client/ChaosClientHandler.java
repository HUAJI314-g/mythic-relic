package com.mythicrelic.client;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.chaos.ChaosAbilities;
import com.mythicrelic.chaos.ChaosTier;
import com.mythicrelic.network.ChaosActionPacket;
import com.mythicrelic.network.ModNetwork;
import com.mythicrelic.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端侧的能力驱动：滑翔、动力、悬停切换、暗影龙刃蓄力、混沌领域按键。
 *
 * <h2>为什么滑翔要在客户端做</h2>
 * <p>原版的鞘翅物理就写在 {@code LivingEntity.travel()} 里，判定条件是
 * {@code isFallFlying()}（第 7 位共享标志）。Forge 虽然给了
 * {@code IForgeItem#canElytraFly} 这个钩子，但它只作用于<b>胸甲槽</b>的物品，
 * 而尼德霍格之印待在饰品栏里，够不着。</p>
 *
 * <p>所以这里的做法是：滑翔时由客户端把那位标志立起来，原版的移动与姿势就全都白送了。
 * <b>只在客户端立</b>——服务端的 {@code updateFallFlying()} 每 tick 都会把它抹掉
 * （因为胸甲槽没有鞘翅），两边一起写反而会每 tick 来回翻转、把同步包刷爆。
 * 服务端只需要知道「你在滑翔」，用来免摔伤，这件事由 {@link ChaosActionPacket} 通知。</p>
 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID, value = Dist.CLIENT)
public final class ChaosClientHandler
{
    /** 共享标志里的「滑翔中」那一位。 */
    private static final int FLAG_FALL_FLYING = 7;

    /**
     * 双击空格的连按窗口（tick）。
     *
     * <p>取 7 是和原版 {@code LocalPlayer.jumpTriggerTime} 保持一致——那边也是按下第一次
     * 记 7，窗口内再按一次才认作连击。原版用这个手势切换创造飞行，我们借它来起飞。</p>
     */
    private static final int DOUBLE_TAP_TICKS = 7;

    /** 蓄满力需要按住多久（tick）。 */
    private static final int FULL_CHARGE_TICKS = 25;
    /** 至少要按这么久才承认是一次蓄力，避免和普通右键误触。 */
    private static final int MIN_CHARGE_TICKS = 4;
    /** 蓄力时每多少 tick 响一次闷响。 */
    private static final int CHARGE_SOUND_INTERVAL = 10;
    /** 蓄满那一刻向外炸开的粒子数（每一圈都撒一对紫+黑，实际粒子数是它的两倍）。 */
    private static final int FULL_CHARGE_BURST = 36;
    /**
     * 松手之后隔多少 tick 才真正把剑气打出去。
     *
     * <p>玩家的挥击动画是 6 tick（原版 {@code LivingEntity.getCurrentSwingDuration()} 的默认值，
     * 那个方法是私有的、没法直接问，急迫效果会把它压到最短 2 tick），取一半就是 3——
     * 手正好挥到最前那一下，剑气再离手。</p>
     *
     * <p>不隔这一下的话，同一 tick 里既甩手又发射，剑气会抢在手前面飞出去，
     * 看着就是「手还没落，刀已经飞了」。</p>
     */
    private static final int SWING_FIRE_DELAY = 3;
    /** 动力滑翔时每 tick 追加的加速度。 */
    private static final double THRUST_ACCELERATION = 0.06D;
    /** 动力滑翔的速度上限（格/tick）。 */
    private static final double THRUST_MAX_SPEED = 2.2D;

    private static boolean gliding;
    private static boolean charging;
    private static int chargeTicks;
    private static boolean lastJumpDown;
    /** 双击空格的倒计时，>0 表示正处在连按窗口里。 */
    private static int jumpTapTimer;
    /** 已经松手、正等着挥击动画播到一半的那一发（>0 表示还没打出去）。 */
    private static int pendingBladeTicks;
    /** 上面那一发要用的蓄力比例（chargeTicks 在松手时就清零了，所以得另存）。 */
    private static float pendingBladeCharge;

    private ChaosClientHandler() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null)
        {
            reset();
            return;
        }

        // 已经松手、正等挥击动画的那一发：放在最前面，免得被下面「开着界面就 return」吞掉
        tickBladeFire();

        while (ModKeyMappings.CHAOS_DOMAIN.consumeClick())
        {
            if (ChaosClientData.canUnleashDomain() && minecraft.screen == null)
            {
                ModNetwork.CHANNEL.sendToServer(new ChaosActionPacket(ChaosActionPacket.Action.UNLEASH_DOMAIN));
            }
        }

        if (minecraft.screen != null)
        {
            lastJumpDown = false;
            jumpTapTimer = 0;
            // 开着界面时蓄力状态是冻结的，这里直接取消，免得关掉界面时莫名其妙射一发
            cancelCharge();
            return;
        }

        boolean jumpDown = minecraft.options.keyJump.isDown();
        boolean jumpPressed = jumpDown && !lastJumpDown;
        boolean ctrlDown = minecraft.options.keySprint.isDown();
        boolean airborne = !player.onGround() && !player.isInWater() && !player.isPassenger();

        if (jumpTapTimer > 0)
        {
            jumpTapTimer--;
        }

        if (player.getAbilities().flying && ChaosClientData.canHover() && ctrlDown)
        {
            // 悬停中按住 Ctrl → 切回动力滑翔
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            beginGlide();
        }
        else if (jumpPressed && airborne && !player.getAbilities().flying && ChaosClientData.hasWings())
        {
            if (jumpTapTimer > 0)
            {
                jumpTapTimer = 0;
                onDoubleTapSpace(player, ctrlDown);
            }
            else
            {
                jumpTapTimer = DOUBLE_TAP_TICKS;
            }
        }

        // 注意要在上面可能改过 abilities.flying 之后再读
        boolean flying = player.getAbilities().flying;
        if (flying || !airborne || !ChaosClientData.hasWings())
        {
            endGlide(player);
        }

        if (gliding)
        {
            // 立起滑翔标志，原版 travel() 就会按鞘翅处理。
            // setSharedFlag 是 protected，对外只能走 startFallFlying()。
            if (!player.isFallFlying())
            {
                player.startFallFlying();
            }
            player.fallDistance = 1.0F;
            if (jumpDown && ChaosClientData.canHover())
            {
                applyThrust(player);
            }
        }

        lastJumpDown = jumpDown;
        updateBladeCharging(minecraft, player);
    }

    // —————————————————————— 滑翔 ——————————————————————

    private static void beginGlide()
    {
        if (!gliding)
        {
            gliding = true;
            ModNetwork.CHANNEL.sendToServer(new ChaosActionPacket(ChaosActionPacket.Action.GLIDE_START));
        }
    }

    /**
     * 收起滑翔。
     *
     * <p>必须连本地那位标志一起清掉——只通知服务端的话，客户端会继续按鞘翅飞，
     * 而服务端以为你已经落地收工、开始正常累计坠落距离，结果就是「明明在滑翔却摔死」。</p>
     */
    private static void endGlide(LocalPlayer player)
    {
        if (gliding)
        {
            gliding = false;
            player.stopFallFlying();
            ModNetwork.CHANNEL.sendToServer(new ChaosActionPacket(ChaosActionPacket.Action.GLIDE_STOP));
        }
    }

    /**
     * 双击空格的处理。
     *
     * <p>原版 {@code LocalPlayer} 里双击空格是切换创造飞行的（用 {@code jumpTriggerTime}
     * 记 7 tick），而高阶会授予玩家 {@code mayfly}，于是这个手势在高阶会被原版抢走。
     * 所以约定成：</p>
     * <ul>
     *   <li><b>中阶</b>：双击空格 = 起飞滑翔；</li>
     *   <li><b>高阶及以上</b>：双击空格交给原版进入悬停；想滑翔得额外按住 Ctrl。</li>
     * </ul>
     *
     * <p>麻烦在于原版那套切换根本不看 Ctrl——只要在窗口内按下第二下就会把 {@code flying}
     * 翻一次。所以按住 Ctrl 起飞时得把这一下补回来，否则玩家会一边悬停一边滑翔。</p>
     */
    private static void onDoubleTapSpace(LocalPlayer player, boolean ctrlDown)
    {
        if (ChaosClientData.canHover())
        {
            if (!ctrlDown)
            {
                // 让原版自己去切创造飞行
                return;
            }
            if (player.getAbilities().flying)
            {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }
        beginGlide();
    }

    private static void reset()
    {
        gliding = false;
        charging = false;
        chargeTicks = 0;
        lastJumpDown = false;
        jumpTapTimer = 0;
        pendingBladeTicks = 0;
        pendingBladeCharge = 0.0F;
    }

    /** 长按空格获得动力——大致相当于原版烟花的推进感。 */
    private static void applyThrust(LocalPlayer player)
    {
        Vec3 look = player.getLookAngle();
        Vec3 motion = player.getDeltaMovement();
        Vec3 boosted = motion.add(look.x * THRUST_ACCELERATION,
                look.y * THRUST_ACCELERATION + THRUST_ACCELERATION * 0.35D,
                look.z * THRUST_ACCELERATION);

        double speed = boosted.length();
        if (speed > THRUST_MAX_SPEED)
        {
            boosted = boosted.scale(THRUST_MAX_SPEED / speed);
        }
        player.setDeltaMovement(boosted);
        player.hasImpulse = true;
    }

    // —————————————————————— 暗影龙刃 ——————————————————————

    /**
     * 空手按住右键蓄力，松手发射。
     *
     * <p><b>起手</b>只排除「准星对着方块」——那是原版的方块交互（开箱子、按按钮），得让位。
     * 对着<b>实体</b>或空处都能起手。早先这里要求射线必须是 {@code MISS}，于是瞄着生物
     * 反而蓄不了力，这一条已经改掉。</p>
     *
     * <p><b>手上拿着的武器也能用来蓄力</b>：只要它的右键没被占用（见
     * {@link ChaosAbilities#isChargeableWeapon})——暗影龙刃会连这把武器的攻击力一起吃进去。</p>
     *
     * <p><b>已经在蓄力时不再重新判定准星</b>：中途把视角拖到方块或实体上不该被打断，
     * 否则刚蓄一半就当场发射，等于白蓄。只有「松开右键」才结算，
     * 「主手换成不能蓄力的东西」则静默取消。</p>
     */
    private static void updateBladeCharging(Minecraft minecraft, LocalPlayer player)
    {
        boolean holding = minecraft.options.keyUse.isDown();
        ItemStack mainHand = player.getMainHandItem();
        boolean chargeableHand = mainHand.isEmpty() || ChaosAbilities.isChargeableWeapon(mainHand);

        if (charging)
        {
            if (!chargeableHand)
            {
                // 中途换了手 / 换成了弓盾食物之类：静默取消，不发射
                cancelCharge();
                return;
            }
            if (holding)
            {
                int before = chargeTicks;
                chargeTicks = Math.min(FULL_CHARGE_TICKS, chargeTicks + 1);
                spawnChargeEffects(player, before);
                return;
            }

            // 松手：先甩手，剑气等挥击动画播到一半再真正打出去
            charging = false;
            if (chargeTicks >= MIN_CHARGE_TICKS)
            {
                pendingBladeCharge = (float) chargeTicks / FULL_CHARGE_TICKS;
                pendingBladeTicks = SWING_FIRE_DELAY;
                player.swing(InteractionHand.MAIN_HAND);
            }
            chargeTicks = 0;
            return;
        }

        boolean aimedAtBlock = minecraft.hitResult != null
                && minecraft.hitResult.getType() == HitResult.Type.BLOCK;
        if (holding && chargeableHand && !aimedAtBlock
                && ChaosClientData.tier().atLeast(ChaosTier.MID))
        {
            charging = true;
            chargeTicks = 1;
            spawnChargeEffects(player, 0);
        }
    }

    /** 静默取消蓄力（不发射）。已经松手排上的那一发不受影响。 */
    private static void cancelCharge()
    {
        charging = false;
        chargeTicks = 0;
    }

    /**
     * 推进「松手之后等挥击动画」的那一发：倒数到 0 就把包发出去。
     *
     * <p>这样剑气离手的那一刻正是手挥到最前的一下，而不是抢在手前面飞出去。</p>
     */
    private static void tickBladeFire()
    {
        if (pendingBladeTicks <= 0)
        {
            return;
        }
        if (--pendingBladeTicks <= 0)
        {
            ModNetwork.CHANNEL.sendToServer(
                    new ChaosActionPacket(ChaosActionPacket.Action.FIRE_BLADE, pendingBladeCharge));
            pendingBladeCharge = 0.0F;
        }
    }

    /** 蓄力时手往身体方向收多少格——粒子跟着手走，{@link ChaosChargeAnimation} 的姿势也用这个数。 */
    static final float CHARGE_PULL_BACK = 0.16F;
    /** 蓄力时手抬高多少格。 */
    static final float CHARGE_LIFT = 0.06F;
    /**
     * 蓄力时手腕绕相机右轴翻的角度（度）。
     *
     * <p>正值 = 手往上抬、往前送（举起来聚气），负值 = 往下压。取正是刻意的：
     * 手往上走会离镜头远一点，不会怼到近裁剪面上。</p>
     */
    static final float CHARGE_TILT = 18.0F;

    /** 第一人称里手心相对于眼睛的位置：往右、往下、往前各多少格。 */
    private static final double HAND_RIGHT = 0.42D;
    private static final double HAND_DOWN = 0.90D;
    private static final double HAND_FORWARD = 0.95D;
    /**
     * 手抬起来之后，粒子再往上补多少格（满蓄力时）。
     *
     * <p>姿势那套变换是拿「手心这一个点」算的，几何上严丝合缝；但真手是条会转的胳膊，
     * 抬起来之后实际比那个点还高一些，所以上机看着是「粒子有些偏下」。
     * 这一项就是补这个差，属于实测校准：觉得还偏下就调大，调成 0 就退回纯按几何算。</p>
     */
    private static final double HAND_RISE = 0.25D;
    /** 蓄力粒子围绕手心的竖向半散布（格）。 */
    private static final double CHARGE_SPREAD = 0.65D;
    /**
     * 蓄满时竖向散布收掉的比例（0~1）。
     *
     * <p>水平半径会从 1.7 收到 0.55，竖向散布要是原地不动，蓄满时整团就是
     * 「1.1 宽、1.3 高」的一根柱子，下半截全垂在手下面——那也是「偏下」的一部分来源。</p>
     */
    private static final double CHARGE_SPREAD_SHRINK = 0.6D;
    /** 黑色微尘相对紫色那颗错开多少格——完全重叠只会压暗颜色，看不出粒子更多。 */
    private static final double DARK_MOTE_JITTER = 0.08D;
    /**
     * 收拢环每 tick 撒几「对」微尘（起手 → 蓄满）。
     *
     * <p>一对 = 一颗紫 + 一颗等量的黑（见 {@link #spawnMotePair}）。
     * 嫌整团稀/浓就调这两个数——这是最直接的浓度旋钮。</p>
     */
    private static final int CHARGE_RING_MIN = 2;
    private static final int CHARGE_RING_MAX = 8;
    /** 手心那团余烬每 tick 撒几对——中心要够实，整团才不显稀。 */
    private static final int CHARGE_EMBER_PAIRS = 2;
    /** 余烬自身散开多少格（半散布），免得几对完全叠在同一个点上。 */
    private static final double CHARGE_EMBER_SPREAD = 0.10D;

    /**
     * 蓄力演出：粒子全部聚在<b>手心</b>上（参考三叉戟那套「低沉闷响 + 粒子往手心收」）。
     *
     * <p>手里是空的，用不了 {@code UseAnim}，所以这个过程由三样东西一起演：
     * 手心的粒子、越蓄越急的闷响、以及 {@link ChaosChargeAnimation} 的手部姿势
     * （第一人称收手 + 发抖，第三人称摆掷矛架势）。蓄得越满，那圈粒子收得越小越密、
     * 闷响越急音调越高，蓄满的一瞬间向外炸开一圈。</p>
     *
     * <p>每一处都走 {@link #spawnMotePair}：一颗暗紫 + 一颗等量的墨黑。</p>
     */
    private static void spawnChargeEffects(LocalPlayer player, int previousTicks)
    {
        float ratio = (float) chargeTicks / FULL_CHARGE_TICKS;
        Vec3 look = player.getLookAngle();
        Vec3 hand = handPosition(player, look, ratio);

        // 一圈向内收拢的暗紫粒子。
        // 竖向散布跟着水平半径一起收：蓄满时半径只剩 0.55，上下要是还散着 ±0.65，
        // 整团就成了一根柱子、下半截垂在手下面，那也是「偏下」的一部分来源。
        // 用的是自己的混沌微尘（寿命 6~12 tick），不是原版 PORTAL——
        // 后者寿命 40~50 tick，松手之后要飘两秒多才散。
        double radius = 1.7D - ratio * 1.15D;
        double spread = CHARGE_SPREAD * (1.0D - CHARGE_SPREAD_SHRINK * ratio);
        int pairs = CHARGE_RING_MIN + (int) (ratio * (CHARGE_RING_MAX - CHARGE_RING_MIN));
        for (int i = 0; i < pairs; i++)
        {
            double angle = player.level().random.nextDouble() * Math.PI * 2.0D;
            double r = radius * (0.65D + player.level().random.nextDouble() * 0.35D);
            double x = hand.x + Math.cos(angle) * r;
            double z = hand.z + Math.sin(angle) * r;
            double y = hand.y + (player.level().random.nextDouble() - 0.5D) * spread * 2.0D;
            spawnMotePair(player, x, y, z,
                    (hand.x - x) * 0.22D, (hand.y - y) * 0.22D, (hand.z - z) * 0.22D);
        }

        // 手心那团余烬：多撒几对、各自错开一点，中心才够实
        for (int i = 0; i < CHARGE_EMBER_PAIRS; i++)
        {
            spawnMotePair(player,
                    hand.x + (player.level().random.nextDouble() - 0.5D) * CHARGE_EMBER_SPREAD * 2.0D,
                    hand.y + (player.level().random.nextDouble() - 0.5D) * CHARGE_EMBER_SPREAD * 2.0D,
                    hand.z + (player.level().random.nextDouble() - 0.5D) * CHARGE_EMBER_SPREAD * 2.0D,
                    0.0D, 0.0D, 0.0D);
        }

        // 蓄满的一瞬间向外炸开
        if (chargeTicks == FULL_CHARGE_TICKS && previousTicks < FULL_CHARGE_TICKS)
        {
            for (int i = 0; i < FULL_CHARGE_BURST; i++)
            {
                double angle = Math.PI * 2.0D * i / FULL_CHARGE_BURST;
                double vx = Math.cos(angle) * 0.35D;
                double vz = Math.sin(angle) * 0.35D;
                spawnMotePair(player, hand.x, hand.y, hand.z, vx, 0.12D, vz);
                spawnMotePair(player, hand.x, hand.y, hand.z, vx * 0.6D, 0.08D, vz * 0.6D);
            }
            player.level().playLocalSound(hand.x, hand.y, hand.z,
                    SoundEvents.TRIDENT_RETURN, SoundSource.PLAYERS, 0.9F, 0.7F, false);
        }

        // 低沉的闷响：越蓄越急、音调越高。
        // 音色沿用龙语那套「威严」音池里的沉重闷响（0.25 秒），
        // 正好卡在 CHARGE_SOUND_INTERVAL(10 tick) 之内不互相糊住。
        // 注：1.20.1 的 SoundEvents 里没有 TRIDENT_RUMBLE（只有 TRIDENT_HIT /
        // HIT_GROUND / RETURN / RIPTIDE_1~3 / THROW / THUNDER），别照抄新版本的常量。
        if (chargeTicks % CHARGE_SOUND_INTERVAL == 0)
        {
            player.level().playLocalSound(hand.x, hand.y, hand.z,
                    SoundEvents.IRON_GOLEM_ATTACK, SoundSource.PLAYERS,
                    0.5F + ratio * 0.5F, 0.75F + ratio * 0.7F, false);
        }
    }

    /**
     * 在同一处撒一颗暗紫微尘 + 一颗<b>等量</b>的墨黑微尘。
     *
     * <p>短寿命粒子同时在场的数量本来就少，只放紫色会显得「淡」。这里保证
     * <b>紫色颗数一颗不少</b>，黑的按等量补上去，整团厚一倍、颜色又不跑偏。</p>
     *
     * <p>黑的那颗故意错开一点点（±{@link #DARK_MOTE_JITTER} 格）：完全重叠只会把颜色
     * 压暗，看不出「粒子变多了」。</p>
     */
    private static void spawnMotePair(LocalPlayer player, double x, double y, double z,
                                      double xSpeed, double ySpeed, double zSpeed)
    {
        player.level().addParticle(ModParticles.CHAOS_MOTE.get(), x, y, z, xSpeed, ySpeed, zSpeed);

        player.level().addParticle(ModParticles.CHAOS_MOTE_DARK.get(),
                x + (player.level().random.nextDouble() - 0.5D) * DARK_MOTE_JITTER,
                y + (player.level().random.nextDouble() - 0.5D) * DARK_MOTE_JITTER,
                z + (player.level().random.nextDouble() - 0.5D) * DARK_MOTE_JITTER,
                xSpeed, ySpeed, zSpeed);
    }

    /**
     * 第一人称里<b>手心</b>的世界坐标。
     *
     * <p>原版第一人称手臂的位移是 {@code applyItemArmTransform} 的
     * {@code (±0.56, -0.52, -0.72)}（空手那条路 {@code renderPlayerArm} 用的是
     * {@code (±0.64, -0.6, -0.72)}）——那是<b>肩关节</b>，手心还得沿手臂再往前下推一点，
     * 所以基准取「右 0.42 / 下 0.90 / 前 0.95」。觉得粒子没贴住手，就调这三个数。</p>
     *
     * <p>姿势动画会抬手腕、收手、发颤，所以这里把同一套变换<b>原样复算一遍</b>
     * （{@link ChaosChargeAnimation} 里就是「平移 + 绕相机右轴转 {@link #CHARGE_TILT}」），
     * 否则手在动、粒子却钉在原地，一眼就穿帮。</p>
     *
     * <p>唯一一处不按几何来的是 {@link #HAND_RISE}：手抬起来以后，真手比「手心那一个点」
     * 还要高一点，上机看着就是粒子偏下，所以额外补一点。要精确复现姿势就把它调成 0。</p>
     */
    private static Vec3 handPosition(LocalPlayer player, Vec3 look, float ratio)
    {
        // 水平的「向右」向量；视线接近垂直时退化，免得 normalize 出 NaN
        Vec3 flat = new Vec3(-look.z, 0.0D, look.x);
        Vec3 right = flat.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : flat.normalize();
        // 相机的「向上」= right × look
        Vec3 up = right.cross(look);

        // 把位移写在（右, 上, 后）这组基里：后方为正，所以「前面 0.95 格」记作 -0.95
        double y = -HAND_DOWN;
        double z = -HAND_FORWARD;

        // 绕右轴转 θ：y' = y·cosθ - z·sinθ，z' = y·sinθ + z·cosθ
        double theta = Math.toRadians(CHARGE_TILT) * ratio;
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        // CHARGE_LIFT 是姿势动画本来就有的平移，HAND_RISE 是补「真手比这个点更高」的校准项
        double finalY = (y * cos - z * sin) + (CHARGE_LIFT + HAND_RISE) * ratio;
        double finalZ = (y * sin + z * cos) + CHARGE_PULL_BACK * ratio;

        // 回到世界坐标：右 = right、上 = up、后 = -look
        return player.getEyePosition()
                .add(right.scale(HAND_RIGHT))
                .add(up.scale(finalY))
                .add(look.scale(-finalZ));
    }

    /** 供渲染器判断要不要画蓄力提示。 */
    public static float chargeRatio()
    {
        return charging ? (float) chargeTicks / FULL_CHARGE_TICKS : 0.0F;
    }
}
