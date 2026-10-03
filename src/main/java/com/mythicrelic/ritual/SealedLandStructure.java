package com.mythicrelic.ritual;

import com.mythicrelic.block.ChaosRuneBlock;
import com.mythicrelic.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * 「封印之地」里那座真实存在的祭坛。
 *
 * <p>它不是贴图，而是由方块一砖一瓦搭出来的：一座圆形黑石广场、两圈上古符文环、
 * 八条放射咒文、一条从黑暗里通进来的引道、八根挑着灵魂灯笼的石柱，以及正中央
 * 两级台阶托起的祭坛本体。</p>
 *
 * <p>整体是「幂等」的：重复构建只会得到同样的结果，所以每次进入时重建一遍即可，
 * 不必额外记录状态。</p>
 *
 * <p><b>垂直关系</b>：维度自带的黑曜石地板上面是 y=2，而广场铺在 y=2（也就是比
 * 周围地板高出一格）。所以引道尽头特意放了一排半砖，让玩家不用跳就能从地板走上引道、
 * 再走上广场。</p>
 */
public final class SealedLandStructure
{
    /** 广场半径。 */
    private static final int PLATFORM_RADIUS = 11;
    /** 清场半径（覆盖整条引道，用来抹掉上一次仪式的残留）。 */
    private static final int CLEAR_RADIUS = 20;
    /** 清场高度。 */
    private static final int CLEAR_HEIGHT = 9;

    /**
     * 九根石柱的位置（相对祭坛中心）。
     *
     * <p>+Z 一侧原本正中有一根柱子，正好挡在玩家走进来的路上，这里把它挪成左右两根，
     * 空出一道门，玩家从黑暗里一眼就能顺着引道走进广场。</p>
     */
    private static final int[][] PILLARS = {
            {4, 8}, {-4, 8}, {8, 0}, {-8, 0},
            {6, 6}, {-6, 6}, {6, -6}, {-6, -6}, {0, -8}
    };

    /** 引道：从广场边缘（z=11）一直铺到 z=18。 */
    private static final int PATH_NEAR_Z = 12;
    private static final int PATH_FAR_Z = 18;
    /** 引道尽头那排半砖的位置，负责把 1 格落差拆成两小步。 */
    private static final int PATH_STEP_Z = 19;
    /** 引道半宽：3 格宽，中列是符文，两侧是凿纹黑石。 */
    private static final int PATH_HALF_WIDTH = 1;

    private static final double OUTER_RING_RADIUS = 6.1D;
    private static final double OUTER_RING_THICKNESS = 0.35D;
    private static final double INNER_RING_RADIUS = 3.6D;
    private static final double INNER_RING_THICKNESS = 0.32D;

    private SealedLandStructure() {}

    /**
     * 在 {@code altarPos} 处搭出整套祭坛。
     *
     * @param altarPos 祭坛方块所在的位置
     */
    public static void build(ServerLevel level, BlockPos altarPos)
    {
        int ax = altarPos.getX();
        int ay = altarPos.getY();
        int az = altarPos.getZ();
        int floorY = ay - 2;

        clearArea(level, ax, az, floorY);
        buildPlatform(level, ax, az, floorY);
        buildPath(level, ax, az, floorY);
        buildRunes(level, ax, az, floorY);
        buildDais(level, ax, ay, az);
        buildPillars(level, ax, az, floorY);
        buildLights(level, ax, ay, az);
    }

    /**
     * 所有符文方块的绝对坐标（两圈符环 + 八条咒文 + 引道中列）。
     *
     * <p>坐标完全由几何决定，所以仪式要点亮／熄灭符文时，重新算一遍即可，
     * 不必去世界里扫描方块。</p>
     */
    public static List<BlockPos> runePositions(BlockPos altarPos)
    {
        return runePositions(altarPos.getX(), altarPos.getZ(), altarPos.getY() - 2);
    }

    /** 符文亮度档位。和 {@link ChaosRuneBlock#GLOW} 的三个取值一一对应。 */
    public static final int RUNE_OFF = 0;
    /** 一开始就在微弱闪烁的那一档。 */
    public static final int RUNE_DIM = 1;
    /** 仪式开始后「散发着强大的金光」的那一档。 */
    public static final int RUNE_BLAZING = 2;

    /**
     * 把广场上所有符文一起调到某一档亮度。
     *
     * <p>{@link #RUNE_DIM} 对应「一开始就在微弱闪烁」，{@link #RUNE_BLAZING} 对应小说里那句
     * 「祭坛上的封印符文，亮了起来」。</p>
     */
    public static void setRuneGlow(ServerLevel level, BlockPos altarPos, int glow)
    {
        Block rune = ModBlocks.CHAOS_RUNE.get();
        for (BlockPos pos : runePositions(altarPos))
        {
            BlockState state = level.getBlockState(pos);
            if (state.is(rune) && state.getValue(ChaosRuneBlock.GLOW) != glow)
            {
                level.setBlockAndUpdate(pos, state.setValue(ChaosRuneBlock.GLOW, glow));
            }
        }
    }

    // —————————————————————— 搭建 ——————————————————————

    private static List<BlockPos> runePositions(int ax, int az, int floorY)
    {
        List<BlockPos> out = new ArrayList<>();
        collectRing(out, ax, az, floorY, OUTER_RING_RADIUS, OUTER_RING_THICKNESS);
        collectRing(out, ax, az, floorY, INNER_RING_RADIUS, INNER_RING_THICKNESS);

        // 八条放射状咒文
        for (int k = 0; k < 8; k++)
        {
            double angle = Math.toRadians(k * 45.0D);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            for (double t = 4.2D; t <= 5.7D; t += 0.5D)
            {
                out.add(new BlockPos(ax + (int) Math.round(cos * t), floorY,
                        az + (int) Math.round(sin * t)));
            }
        }

        // 引道中列
        for (int z = PATH_NEAR_Z; z <= PATH_FAR_Z; z++)
        {
            out.add(new BlockPos(ax, floorY, az + z));
        }
        return out;
    }

    /** 抹掉上一次仪式可能留下的东西，只保留 y<=floorY-1 的基岩／黑曜石地板。 */
    private static void clearArea(ServerLevel level, int ax, int az, int floorY)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -CLEAR_RADIUS; dx <= CLEAR_RADIUS; dx++)
        {
            for (int dz = -CLEAR_RADIUS; dz <= CLEAR_RADIUS; dz++)
            {
                for (int dy = 0; dy < CLEAR_HEIGHT; dy++)
                {
                    pos.set(ax + dx, floorY + dy, az + dz);
                    if (!level.getBlockState(pos).isAir())
                    {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /** 圆形黑石广场：由外向内一圈圈换材质，做出层次。 */
    private static void buildPlatform(ServerLevel level, int ax, int az, int floorY)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++)
        {
            for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++)
            {
                double r = Math.sqrt((double) dx * dx + (double) dz * dz);
                if (r > PLATFORM_RADIUS + 0.4D)
                {
                    continue;
                }

                BlockState state;
                if (r > 10.2D)
                {
                    state = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
                }
                else if (r > 9.4D)
                {
                    state = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
                }
                else if (r > 8.6D)
                {
                    state = Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
                }
                else
                {
                    state = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
                }

                pos.set(ax + dx, floorY, az + dz);
                level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            }
        }
    }

    /**
     * 从广场边缘伸向黑暗的引道。
     *
     * <p>广场比外面的地板高一格，所以引道尽头铺一排半砖（0.5 格），玩家一路走过来
     * 完全不用跳。</p>
     */
    private static void buildPath(ServerLevel level, int ax, int az, int floorY)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState edge = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();

        for (int z = PATH_NEAR_Z; z <= PATH_FAR_Z; z++)
        {
            for (int dx = -PATH_HALF_WIDTH; dx <= PATH_HALF_WIDTH; dx++)
            {
                if (dx == 0)
                {
                    continue; // 中列留给符文方块
                }
                pos.set(ax + dx, floorY, az + z);
                level.setBlock(pos, edge, Block.UPDATE_CLIENTS);
            }
        }

        BlockState step = Blocks.POLISHED_BLACKSTONE_SLAB.defaultBlockState();
        for (int dx = -PATH_HALF_WIDTH; dx <= PATH_HALF_WIDTH; dx++)
        {
            pos.set(ax + dx, floorY, az + PATH_STEP_Z);
            level.setBlock(pos, step, Block.UPDATE_CLIENTS);
        }
    }

    /** 地面符文：两圈圆环 + 八条放射状的「咒文」+ 引道中列。 */
    private static void buildRunes(ServerLevel level, int ax, int az, int floorY)
    {
        // 一上来就是「微光」档：玩家被拉进封印之地的那一刻，这些符文已经在黑暗里微弱地闪了。
        // 注意这里必须用 UPDATE_ALL 而不是 UPDATE_CLIENTS——符文现在会发光，
        // 只发客户端更新的话光照引擎不会跟着重算，广场要等区块重载才会亮。
        BlockState rune = ModBlocks.CHAOS_RUNE.get().defaultBlockState()
                .setValue(ChaosRuneBlock.GLOW, RUNE_DIM);
        for (BlockPos pos : runePositions(ax, az, floorY))
        {
            level.setBlock(pos, rune, Block.UPDATE_ALL);
        }
    }

    /** 在给定半径处收集一圈方块坐标。 */
    private static void collectRing(List<BlockPos> out, int ax, int az, int y,
                                    double radius, double thickness)
    {
        int bound = (int) Math.ceil(radius + thickness) + 1;
        for (int dx = -bound; dx <= bound; dx++)
        {
            for (int dz = -bound; dz <= bound; dz++)
            {
                double r = Math.sqrt((double) dx * dx + (double) dz * dz);
                if (Math.abs(r - radius) <= thickness)
                {
                    out.add(new BlockPos(ax + dx, y, az + dz));
                }
            }
        }
    }

    /** 中央两级台阶：下层 5×5，上层 3×3（正中央留给祭坛本体）。 */
    private static void buildDais(ServerLevel level, int ax, int ay, int az)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // 下层
        BlockState lower = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
        for (int dx = -2; dx <= 2; dx++)
        {
            for (int dz = -2; dz <= 2; dz++)
            {
                pos.set(ax + dx, ay - 1, az + dz);
                level.setBlock(pos, lower, Block.UPDATE_CLIENTS);
            }
        }

        // 上层（四角用凿纹黑石点缀，中央留空给祭坛）
        BlockState upper = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState corner = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                if (dx == 0 && dz == 0)
                {
                    continue;
                }
                pos.set(ax + dx, ay, az + dz);
                boolean isCorner = dx != 0 && dz != 0;
                level.setBlock(pos, isCorner ? corner : upper, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** 九根石柱，顶端各挑一盏灵魂灯笼。 */
    private static void buildPillars(ServerLevel level, int ax, int az, int floorY)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState shaft = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
        BlockState capital = Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();

        for (int[] p : PILLARS)
        {
            int px = ax + p[0];
            int pz = az + p[1];

            for (int dy = 1; dy <= 4; dy++)
            {
                pos.set(px, floorY + dy, pz);
                level.setBlock(pos, shaft, Block.UPDATE_CLIENTS);
            }
            pos.set(px, floorY + 5, pz);
            level.setBlock(pos, capital, Block.UPDATE_CLIENTS);
            pos.set(px, floorY + 6, pz);
            level.setBlock(pos, Blocks.SOUL_LANTERN.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** 祭坛四周漂浮的末地烛——给漆黑的空间一点幽光。 */
    private static void buildLights(ServerLevel level, int ax, int ay, int az)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState rod = Blocks.END_ROD.defaultBlockState();
        int[][] spots = {{3, 3}, {-3, 3}, {3, -3}, {-3, -3}};
        for (int[] s : spots)
        {
            pos.set(ax + s[0], ay + 1, az + s[1]);
            level.setBlock(pos, rod, Block.UPDATE_CLIENTS);
        }
    }
}
