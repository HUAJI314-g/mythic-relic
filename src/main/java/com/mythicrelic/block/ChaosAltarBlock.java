package com.mythicrelic.block;

import com.mythicrelic.ritual.ChaosRitualManager;
import com.mythicrelic.ritual.SealedLand;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 混沌祭坛：小说中那座「上古符咒密布、镇压着恐怖存在」的黑色祭坛。
 *
 * <h2>它不负责启动仪式</h2>
 * <p>仪式是<b>玩家踏进封印之地的那一刻自动开演</b>的，入口在
 * {@link SealedLand#enter(ServerPlayer)} 里——那里搭完结构就直接
 * {@code ChaosRitualManager.start()}。祭坛在这里只是布景的一部分。</p>
 *
 * <p>历史上这里曾经挂着「手持混沌能量结晶右键开启仪式」的一条路径，那是第一轮的写法；
 * 第二轮把流程改成「进来即开演」之后它就被架空了，一直留着没删。<b>已经删掉</b>——
 * 留着的话，玩家在自家门口摆一个自制祭坛右键就能把整场仪式开起来，甚至再拿一枚印记。</p>
 *
 * <h2>它现在唯一的作用：出口</h2>
 * <p>右键把玩家送回自己的重生点。这是给「仪式没能跑完」兜底的——比如服务器重启会把
 * {@code ChaosRitualManager} 里进行中的仪式丢掉，玩家就困在只有基岩的维度里了。</p>
 *
 * <p>{@link #LIT} 状态用于在仪式进行时切换成发光的符文材质。</p>
 */
public class ChaosAltarBlock extends Block
{
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public ChaosAltarBlock()
    {
        super(Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(5.0F, 1200.0F)
                // 点亮时照亮半径 14 —— 封印之地里没有天光，祭坛就是唯一能看见的东西。
                .lightLevel(state -> state.getValue(LIT) ? 14 : 0));
        this.registerDefaultState(this.stateDefinition.any().setValue(LIT, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(LIT);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit)
    {
        if (level.isClientSide())
        {
            return InteractionResult.SUCCESS;
        }
        if (!(player instanceof ServerPlayer serverPlayer))
        {
            return InteractionResult.PASS;
        }

        // 仪式进行中祭坛不响应。玩家走到祭坛跟前时很容易顺手右键一下，
        // 不拦的话这一下就把自己送出封印之地、仪式当场中断了。
        if (ChaosRitualManager.isRunning(serverPlayer.getUUID()))
        {
            serverPlayer.displayClientMessage(
                    Component.translatable("ritual.mythic_relic.already_running").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }

        // 唯一的作用：出口。只在封印之地有意义，站在别处右键不做任何事。
        if (level.dimension().equals(SealedLand.DIMENSION) && SealedLand.sendBack(serverPlayer))
        {
            serverPlayer.displayClientMessage(
                    Component.translatable("ritual.mythic_relic.leave").withStyle(ChatFormatting.GRAY), false);
        }
        return InteractionResult.SUCCESS;
    }
}
