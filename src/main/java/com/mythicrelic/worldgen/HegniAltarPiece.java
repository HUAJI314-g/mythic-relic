package com.mythicrelic.worldgen;

import com.mythicrelic.registry.ModBlocks;
import com.mythicrelic.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.ScatteredFeaturePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * 诅咒祭坛的实际搭建：一圈黑曜石台 + 四根哭泣黑曜石柱（顶上挂灵魂灯）+ 中间插着的剑。
 *
 * <p>继承 {@link ScatteredFeaturePiece} 是为了白拿 {@code heightPosition} 和
 * {@link #updateHeightPositionToLowestGroundHeight} —— 它会扫描自己脚下这片区域，
 * 把高度对齐到最低的地面，这样祭坛不会一半悬空、一半埋在山坡里。</p>
 *
 * <h2>⚠️ 为什么不用 {@code StructurePiece.placeBlock()}</h2>
 * <p>那个方法看着正好合用（它会检查目标是否落在写入范围内），但实测<b>会把方块静默丢掉</b>：
 * 传进去的坐标明明通过了 {@code box.isInside()}，写完回读还是空气，
 * 整座祭坛一个方块都落不下去。换成自己控界的 {@link #put} 之后立刻就正常了
 * （回读到 {@code mythic_relic:hegni_altar}）。</p>
 *
 * <p>{@link #put} 做的事和 {@code placeBlock} 的本意一样：只往「当前正在写的这个区块」
 * 里放，跨界的那部分跳过——那部分会由相邻区块自己的 {@code postProcess} 负责，
 * 直接 {@code setBlock} 到界外会破坏别的区块。</p>
 */
public class HegniAltarPiece extends ScatteredFeaturePiece
{
    /** 台子的半径（含中心共 7x7，但按圆裁掉四角）。 */
    private static final int RADIUS = 3;
    private static final int WIDTH = RADIUS * 2 + 1;
    private static final int HEIGHT = 6;
    private static final int DEPTH = RADIUS * 2 + 1;
    /** 台面之上要清空的高度，免得祭坛长在树里。 */
    private static final int CLEAR_HEIGHT = 4;

    /** 生成用：先给个占位高度，真正的高度在 {@link #postProcess} 里对齐。 */
    public HegniAltarPiece(RandomSource random, int x, int z)
    {
        super(ModStructures.HEGNI_ALTAR_PIECE.get(), x, 64, z, WIDTH, HEIGHT, DEPTH, Direction.NORTH);
    }

    /** 读存档用。 */
    public HegniAltarPiece(CompoundTag tag)
    {
        super(ModStructures.HEGNI_ALTAR_PIECE.get(), tag);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag)
    {
        super.addAdditionalSaveData(context, tag);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager manager, ChunkGenerator generator,
                            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pos)
    {
        // 对齐到这片区域最低的地面，免得祭坛一半悬空
        updateHeightPositionToLowestGroundHeight(level, 1);

        int baseY = this.heightPosition;
        int cx = this.boundingBox.minX() + RADIUS;
        int cz = this.boundingBox.minZ() + RADIUS;

        for (int dx = -RADIUS; dx <= RADIUS; dx++)
        {
            for (int dz = -RADIUS; dz <= RADIUS; dz++)
            {
                // 圆形台面：四角裁掉
                if (dx * dx + dz * dz > 10)
                {
                    continue;
                }
                for (int dy = 0; dy < CLEAR_HEIGHT; dy++)
                {
                    put(level, Blocks.AIR.defaultBlockState(), cx + dx, baseY + dy, cz + dz, box);
                }
                put(level, Blocks.OBSIDIAN.defaultBlockState(), cx + dx, baseY - 1, cz + dz, box);
            }
        }

        int[][] corners = {{-RADIUS, -RADIUS}, {RADIUS, -RADIUS}, {-RADIUS, RADIUS}, {RADIUS, RADIUS}};
        for (int[] corner : corners)
        {
            for (int h = 0; h < 3; h++)
            {
                put(level, Blocks.CRYING_OBSIDIAN.defaultBlockState(),
                        cx + corner[0], baseY + h, cz + corner[1], box);
            }
            put(level, Blocks.SOUL_LANTERN.defaultBlockState(),
                    cx + corner[0], baseY + 3, cz + corner[1], box);
        }

        // 中心插着的那把剑
        put(level, ModBlocks.HEGNI_ALTAR.get().defaultBlockState(), cx, baseY, cz, box);
    }

    /** 只往当前正在写的区块里放方块；界外的交给相邻区块自己处理。 */
    private static void put(WorldGenLevel level, BlockState state, int x, int y, int z, BoundingBox box)
    {
        BlockPos pos = new BlockPos(x, y, z);
        if (box.isInside(pos))
        {
            level.setBlock(pos, state, 2);
        }
    }
}
