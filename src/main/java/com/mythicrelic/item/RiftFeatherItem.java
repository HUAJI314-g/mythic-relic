package com.mythicrelic.item;

import com.mythicrelic.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 「破界之羽」——从 riftfeather 模组搬过来的瞬移道具。
 *
 * <p>两种用法：</p>
 * <ul>
 *   <li><b>手持右键</b>：朝视线方向瞬移，最远 {@code riftFeather.lookRange} 格。
 *       视线撞到方块就停在方块前面一点。</li>
 *   <li><b>潜行右键</b>：打开 {@code CoordinateScreen}，手填 X/Y/Z（支持 {@code ~} 相对坐标），
 *       最远 {@code riftFeather.longRange} 格。</li>
 * </ul>
 *
 * <p>落点不是随便选的：会在目标点周围扫一圈，找一个「脚下有实心方块、身体两格不卡、不碰岩浆／
 * 细雪／仙人掌／甜浆果丛」的位置；实在没有就退而求其次选一个悬空点，
 * 并按配置给一段缓降免得摔死。</p>
 */
public class RiftFeatherItem extends Item
{
    public RiftFeatherItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide())
        {
            return InteractionResultHolder.success(stack);
        }
        // 潜行时交给客户端事件去开坐标界面，这里什么都不做。
        if (player.isShiftKeyDown())
        {
            return InteractionResultHolder.success(stack);
        }
        if (player.getCooldowns().isOnCooldown(stack.getItem()))
        {
            return InteractionResultHolder.success(stack);
        }
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel)
        {
            teleportAlongSight(serverPlayer, serverLevel, stack);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> lines, TooltipFlag flag)
    {
        lines.add(Component.translatable("tooltip.mythic_relic.rift_feather.1", Config.riftLookRange));
        lines.add(Component.translatable("tooltip.mythic_relic.rift_feather.2", Config.riftLongRange));
        lines.add(Component.translatable("tooltip.mythic_relic.rift_feather.3", Config.riftCooldownTicks / 20));
        super.appendHoverText(stack, level, lines, flag);
    }

    // —————————————————————— 视线瞬移 ——————————————————————

    private static void teleportAlongSight(ServerPlayer player, ServerLevel level, ItemStack stack)
    {
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle().normalize();
        Vec3 raw = eye.add(dir.scale(Config.riftLookRange));

        BlockHitResult hit = level.clip(new ClipContext(eye, raw,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.BLOCK)
        {
            raw = hit.getLocation().add(dir.scale(-0.75D));
        }
        else
        {
            // 视线没打到任何方块（对着天空、或者朝地平线望出去）。
            //
            // 这时候 raw 是「眼位 + 视线 × 1000 格」——朝上望就是一个远在建造上限之上的点，
            // 经 clampToWorld 压下来正好落在最高层，于是玩家被送上天空。所以这里改成
            // **只取水平方向，纵向压到那个水平位置的地表**：
            // 朝地平线看 = 往前飞一大段落到地上；正对天空看 = 原地不动。
            int tx = Mth.floor(raw.x);
            int tz = Mth.floor(raw.z);

            // ⚠️ 必须先把这个区块加载出来，再问高度。
            // Level#getHeight 对**没加载的区块**会直接返回 getMinBuildHeight()（主世界 -64），
            // 而不是去生成它。朝天空看时水平方向仍有余量，目标点可能落在几十个区块之外，
            // 那个区块没加载 → 读到 -64 → 玩家被塞进地底。这就是「小概率传送到地底下」的根因。
            level.getChunk(tx >> 4, tz >> 4);

            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz);
            if (surface <= level.getMinBuildHeight() + 1)
            {
                // 兜底：还是拿到建造下限，说明那个位置问不出有效地表，别硬送
                player.displayClientMessage(
                        Component.translatable("message.mythic_relic.rift_feather.blocked"), true);
                return;
            }
            raw = new Vec3(raw.x, surface, raw.z);
        }

        BlockPos center = clampToWorld(level, raw);
        ensureAreaLoaded(level, center, 3);

        BlockPos dest = findLanding(level, center, 3);
        if (dest == null)
        {
            player.displayClientMessage(Component.translatable("message.mythic_relic.rift_feather.blocked"), true);
            return;
        }
        finishTeleport(player, level, stack, dest);
    }

    // —————————————————————— 坐标瞬移 ——————————————————————

    /** 由 {@code RiftTeleportPacket} 调用（坐标界面填完点「破界前往」）。 */
    public static void teleportToCoordinates(ServerPlayer player, int rawX, int rawY, int rawZ)
    {
        ItemStack stack = findHeldFeather(player);
        if (stack.isEmpty())
        {
            player.displayClientMessage(Component.translatable("message.mythic_relic.rift_feather.need_item"), true);
            return;
        }
        if (player.getCooldowns().isOnCooldown(stack.getItem()))
        {
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        BlockPos requested = new BlockPos(rawX, rawY, rawZ);
        BlockPos center = clampToWorld(level, Vec3.atBottomCenterOf(requested));

        int limit = Config.riftLongRange;
        if (player.position().distanceToSqr(Vec3.atBottomCenterOf(center)) > (double) limit * limit)
        {
            int distance = (int) Math.round(Math.sqrt(player.position().distanceToSqr(Vec3.atBottomCenterOf(center))));
            player.displayClientMessage(Component.translatable("message.mythic_relic.rift_feather.too_far", distance, limit), true);
            return;
        }

        ensureAreaLoaded(level, center, 4);
        BlockPos dest = findLanding(level, center, 4);
        if (dest == null)
        {
            player.displayClientMessage(Component.translatable("message.mythic_relic.rift_feather.blocked"), true);
            return;
        }
        finishTeleport(player, level, stack, dest);
    }

    private static ItemStack findHeldFeather(Player player)
    {
        for (InteractionHand hand : InteractionHand.values())
        {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof RiftFeatherItem)
            {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    // —————————————————————— 落地 ——————————————————————

    private static void finishTeleport(ServerPlayer player, ServerLevel level, ItemStack stack, BlockPos dest)
    {
        double oldX = player.getX();
        double oldY = player.getY() + player.getEyeHeight() * 0.5D;
        double oldZ = player.getZ();
        double newX = dest.getX() + 0.5D;
        double newY = dest.getY();
        double newZ = dest.getZ() + 0.5D;

        int distance = (int) Math.round(Math.sqrt(player.distanceToSqr(newX, newY, newZ)));

        if (level.getWorldBorder().isWithinBounds(dest))
        {
            ensureAreaLoaded(level, dest, 1);
        }
        player.teleportTo(newX, newY, newZ);
        player.resetFallDistance();
        player.setDeltaMovement(Vec3.ZERO);

        if (!hasGround(level, dest) && Config.riftSlowFalling)
        {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 120, 0));
        }

        level.sendParticles(ParticleTypes.PORTAL, oldX, oldY + 0.5D, oldZ, 48, 0.4D, 0.8D, 0.4D, 0.15D);
        level.sendParticles(ParticleTypes.PORTAL, newX, newY + 1.0D, newZ, 48, 0.4D, 0.8D, 0.4D, 0.15D);
        level.playSound(null, oldX, oldY, oldZ, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
        level.playSound(null, newX, newY, newZ, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);

        if (Config.riftCooldownTicks > 0)
        {
            player.getCooldowns().addCooldown(stack.getItem(), Config.riftCooldownTicks);
        }
        player.displayClientMessage(Component.translatable("message.mythic_relic.rift_feather.teleported", distance), true);
    }

    private static BlockPos clampToWorld(Level level, Vec3 raw)
    {
        WorldBorder border = level.getWorldBorder();
        double x = Math.max(border.getMinX() + 1.0D, Math.min(border.getMaxX() - 1.0D, raw.x));
        double z = Math.max(border.getMinZ() + 1.0D, Math.min(border.getMaxZ() - 1.0D, raw.z));
        double y = Math.max(level.getMinBuildHeight() + 1, Math.min(level.getMaxBuildHeight() - 3, raw.y));
        return new BlockPos((int) x, (int) y, (int) z);
    }

    /** 先把目标周围的区块加载出来，不然读到的全是空气，找出来的落点会不可靠。 */
    private static void ensureAreaLoaded(ServerLevel level, BlockPos center, int radius)
    {
        int minCx = (center.getX() - radius) >> 4;
        int maxCx = (center.getX() + radius) >> 4;
        int minCz = (center.getZ() - radius) >> 4;
        int maxCz = (center.getZ() + radius) >> 4;
        for (int cx = minCx; cx <= maxCx; cx++)
        {
            for (int cz = minCz; cz <= maxCz; cz++)
            {
                level.getChunk(cx, cz);
            }
        }
    }

    /**
     * 在目标点附近找一个能站的位置。
     *
     * <p>按到中心的距离由近到远扫，优先选「脚下有实心方块」的；一个都没有就退而求其次，
     * 记住第一个「悬空但能站」的点。扫描量封顶 1200 个候选，免得半径一大就卡主线程。</p>
     */
    private static BlockPos findLanding(Level level, BlockPos center, int radius)
    {
        int minY = level.getMinBuildHeight() + 1;
        int maxY = level.getMaxBuildHeight() - 3;

        List<BlockPos> candidates = new ArrayList<>();
        for (int dy = -6; dy <= 12; dy++)
        {
            int y = center.getY() + dy;
            if (y < minY || y > maxY)
            {
                continue;
            }
            for (int dx = -radius; dx <= radius; dx++)
            {
                for (int dz = -radius; dz <= radius; dz++)
                {
                    candidates.add(new BlockPos(center.getX() + dx, y, center.getZ() + dz));
                }
            }
        }

        final int cx = center.getX();
        final int cy = center.getY();
        final int cz = center.getZ();
        candidates.sort(Comparator.comparingDouble(pos ->
        {
            int dx = pos.getX() - cx;
            int dy = pos.getY() - cy;
            int dz = pos.getZ() - cz;
            return dx * dx + dy * dy + dz * dz;
        }));

        BlockPos floating = null;
        int scanned = 0;
        for (BlockPos pos : candidates)
        {
            if (++scanned > 1200)
            {
                break;
            }
            if (!canStand(level, pos))
            {
                continue;
            }
            if (hasGround(level, pos))
            {
                return pos;
            }
            if (floating == null)
            {
                floating = pos;
            }
        }
        return floating;
    }

    /** 脚下和头顶两格都得能待人。 */
    private static boolean canStand(Level level, BlockPos pos)
    {
        return isBreathable(level, pos) && isBreathable(level, pos.above());
    }

    private static boolean isBreathable(Level level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        if (state.getFluidState().is(FluidTags.LAVA))
        {
            return false;
        }
        return isSurvivable(state) && state.getCollisionShape(level, pos).isEmpty();
    }

    private static boolean hasGround(Level level, BlockPos pos)
    {
        BlockPos below = pos.below();
        BlockState state = level.getBlockState(below);
        if (state.getFluidState().is(FluidTags.LAVA) || !isSurvivable(state))
        {
            return false;
        }
        return !state.getCollisionShape(level, below).isEmpty();
    }

    /** 这些方块踩上去会掉血，不能当成落点。 */
    private static boolean isSurvivable(BlockState state)
    {
        return state.getBlock() != Blocks.POWDER_SNOW
                && state.getBlock() != Blocks.FIRE
                && state.getBlock() != Blocks.SOUL_FIRE
                && state.getBlock() != Blocks.CACTUS
                && state.getBlock() != Blocks.WITHER_ROSE
                && state.getBlock() != Blocks.SWEET_BERRY_BUSH;
    }
}
