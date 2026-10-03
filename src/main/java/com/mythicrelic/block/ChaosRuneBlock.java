package com.mythicrelic.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;

/**
 * 封印符文方块——祭坛广场上那一圈圈「上古符咒」。
 *
 * <p>小说里那句「祭坛上的封印符文亮了起来」，在这里是一个三档的方块状态
 * {@link #GLOW}：</p>
 * <ul>
 *   <li><b>0 熄灭</b>：凿在黑石上的暗纹，亮度 {@value #LIGHT_OFF}。
 *       玩家自己合出来的符文砖就是这个状态。</li>
 *   <li><b>1 微光</b>：亮度 {@value #LIGHT_DIM}。暗金的纹路，在黑暗里零星地冒一点火星
 *       ——封印之地的符文<b>从一开始就是这样</b>，远远看着像有什么东西在极缓慢地呼吸。</li>
 *   <li><b>2 盛光</b>：亮度 {@value #LIGHT_BLAZING}。纹路烧成炽亮的金色，火星不断往上冒，
 *       整座广场被照亮——对应「祭坛上的封印符文，亮了起来」。</li>
 * </ul>
 *
 * <p>三档之间只换贴图与发光等级，不换模型，所以广场上那上百块砖可以整片一起变。
 * 贴图由 {@code tools/gen_chaos_rune_textures.py} 程序化生成。</p>
 */
public class ChaosRuneBlock extends Block
{
    /** 符文亮度档位：0=熄灭，1=微光（一开始就在微弱闪烁），2=盛光（仪式开始后的强大金光）。 */
    public static final IntegerProperty GLOW = IntegerProperty.create("glow", 0, 2);

    public static final int LIGHT_OFF = 0;
    public static final int LIGHT_DIM = 5;
    public static final int LIGHT_BLAZING = 15;

    public ChaosRuneBlock()
    {
        super(Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(4.0F, 1200.0F)
                .lightLevel(state -> lightOf(state.getValue(GLOW))));
        this.registerDefaultState(this.stateDefinition.any().setValue(GLOW, 0));
    }

    private static int lightOf(int glow)
    {
        return switch (glow)
        {
            case 1 -> LIGHT_DIM;
            case 2 -> LIGHT_BLAZING;
            default -> LIGHT_OFF;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(GLOW);
    }

    /**
     * 符文砖自己的那点「闪」。
     *
     * <p>原版每个区块段每 tick 只随机挑一个位置调 {@code animateTick}，所以同一片符文砖
     * 是零散地、互不同步地冒火星——正好就是「闪烁」该有的样子，不必真的去改方块状态
     * （那会牵动上百次方块更新和光照重算）。这里跑在客户端，服务端零开销。</p>
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        int glow = state.getValue(GLOW);
        if (glow == LIGHT_OFF)
        {
            return;
        }

        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 1.0D;
        double z = pos.getZ() + 0.5D;

        if (glow == 1)
        {
            // 微光：偶尔冒起一点暗金，明灭不定。
            if (random.nextInt(16) == 0)
            {
                level.addParticle(ParticleTypes.END_ROD,
                        x + (random.nextDouble() - 0.5D) * 0.8D, y,
                        z + (random.nextDouble() - 0.5D) * 0.8D,
                        0.0D, 0.012D, 0.0D);
            }
            return;
        }

        // 盛光：纹路在烧，火舌与金星不断往上冒。
        if (random.nextInt(3) == 0)
        {
            level.addParticle(ParticleTypes.FLAME,
                    x + (random.nextDouble() - 0.5D) * 0.6D, y - 0.2D,
                    z + (random.nextDouble() - 0.5D) * 0.6D,
                    0.0D, 0.012D, 0.0D);
        }
        if (random.nextInt(6) == 0)
        {
            level.addParticle(ParticleTypes.FIREWORK,
                    x + (random.nextDouble() - 0.5D) * 0.9D, y + random.nextDouble() * 0.5D,
                    z + (random.nextDouble() - 0.5D) * 0.9D,
                    0.0D, 0.02D, 0.0D);
        }
    }
}
