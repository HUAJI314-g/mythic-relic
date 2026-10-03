package com.mythicrelic.ritual;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 「封印之地」——小说里那片吞噬光亮的漆黑空间。
 *
 * <p>它是一个真正独立的维度（见 {@code data/mythic_relic/dimension/sealed_land.json}）：
 * 无天光、环境光为 0。玩家进来时，{@link SealedLandStructure} 会在这里搭起一座真实的
 * 祭坛广场，玩家落在广场外的黑暗里，得自己顺着引道走过去。</p>
 *
 * <p>离开时会被直接送回自己的重生点（没有设过床／锚的话则回到世界出生点），
 * 就像原版的末地祭坛一样。</p>
 */
public final class SealedLand
{
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            new ResourceLocation(MythicRelic.MODID, "sealed_land"));

    /**
     * 祭坛本体的位置。
     *
     * <p>维度是平坦生成器：y=0 基岩、y=1 黑曜石，地板表面在 y=2。祭坛广场铺在 y=2
     * （比地板高一格，引道尽头用半砖接上），中央两级台阶抬到 y=3 / y=4，祭坛落在 y=4。</p>
     */
    public static final BlockPos ALTAR_POS = new BlockPos(0, 4, 0);

    /** 玩家刚被送进来时的落点——在祭坛广场之外的黑暗里，正对广场。 */
    public static final double ENTRY_X = 0.5D;
    public static final double ENTRY_Y = 2.0D;
    public static final double ENTRY_Z = 20.5D;
    /** 进入时的朝向：正对广场中央的祭坛（-Z 方向）。 */
    public static final float ENTRY_YAW = 180.0F;

    /**
     * 走到这个水平距离以内，就算「抵达祭坛」。
     *
     * <p>广场半径是 11，取 9 意味着玩家必须真的踏上广场、走进光圈里，仪式才会继续。</p>
     */
    public static final double ARRIVAL_RADIUS = 9.0D;

    private SealedLand() {}

    @Nullable
    public static ServerLevel level(ServerPlayer player)
    {
        return player.getServer() == null ? null : player.getServer().getLevel(DIMENSION);
    }

    /**
     * 把玩家送进封印之地、搭好祭坛、并开启仪式。
     *
     * <p>注意这里只把玩家放在广场外的黑暗里，绝不把他直接挪到祭坛前——
     * 那一段路要他自己走。</p>
     *
     * @return 维度不可用时返回 false，调用方应据此提示玩家
     */
    public static boolean enter(ServerPlayer player)
    {
        ServerLevel sealed = level(player);
        if (sealed == null)
        {
            return false;
        }

        // 先搭结构，再把祭坛本体放进中央预留的位置。
        SealedLandStructure.build(sealed, ALTAR_POS);
        sealed.setBlockAndUpdate(ALTAR_POS, ModBlocks.CHAOS_ALTAR.get().defaultBlockState());

        player.teleportTo(sealed, ENTRY_X, ENTRY_Y, ENTRY_Z, ENTRY_YAW, 0.0F);
        ChaosRitualManager.start(player, ALTAR_POS);
        return true;
    }

    /** 玩家是不是已经走进广场、站到祭坛跟前了。 */
    public static boolean isNearAltar(ServerPlayer player)
    {
        if (!player.serverLevel().dimension().equals(DIMENSION))
        {
            return false;
        }
        double dx = player.getX() - (ALTAR_POS.getX() + 0.5D);
        double dz = player.getZ() - (ALTAR_POS.getZ() + 0.5D);
        return dx * dx + dz * dz <= ARRIVAL_RADIUS * ARRIVAL_RADIUS;
    }

    /**
     * 把玩家送出封印之地。
     *
     * <p>与原版末地祭坛一致：优先送回玩家自己的重生点（床 / 重生锚），没有则退回世界出生点。</p>
     *
     * @return 无法确定落点（或玩家已掉线／死亡）时返回 false
     */
    public static boolean sendBack(ServerPlayer player)
    {
        if (player.hasDisconnected() || !player.isAlive())
        {
            return false;
        }
        MinecraftServer server = player.getServer();
        if (server == null)
        {
            return false;
        }

        // 1) 玩家自己的重生点
        BlockPos respawn = player.getRespawnPosition();
        ServerLevel respawnLevel = server.getLevel(player.getRespawnDimension());
        if (respawn != null && respawnLevel != null)
        {
            player.teleportTo(respawnLevel, respawn.getX() + 0.5D, respawn.getY(), respawn.getZ() + 0.5D,
                    player.getYRot(), player.getXRot());
            return true;
        }

        // 2) 兜底：世界出生点
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                overworld.getSharedSpawnAngle(), player.getXRot());
        return true;
    }
}
