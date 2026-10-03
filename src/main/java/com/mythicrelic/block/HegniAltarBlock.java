package com.mythicrelic.block;

import com.mythicrelic.blockentity.HegniAltarBlockEntity;
import com.mythicrelic.chaos.ChaosEvents;
import com.mythicrelic.curse.ModCurse;
import com.mythicrelic.network.CommentaryPacket;
import com.mythicrelic.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 「诅咒祭坛」——赫格尼之剑插在中心的那块石头。
 *
 * <h2>玩法</h2>
 * <p>它不是让你挖的，是让你<b>点</b>的：右键一次算一下，点满
 * {@value #REQUIRED_CLICKS} 下才能把剑拔出来，进度会打在聊天栏里。
 * 所以这个方块设成了<b>挖不动</b>（{@code strength(-1)}）。</p>
 *
 * <p>压力来自 {@link #WITHER_RADIUS} 格内持续挂着的凋零 II——二十秒左右就能把人耗死，
 * 想拔剑就得手快。进度按玩家存在 {@link ModCurse} 里，<b>一死就清零</b>。</p>
 *
 * <h2>那把剑是一个实体，不是方块模型</h2>
 * <p>方块本身<b>不渲染任何东西</b>（模型是 {@code block/air}）——剑由
 * {@link HegniAltarBlockEntity} 生成的一个 {@code ItemDisplay} 实体举着。
 * 原因见那个类的注释：用 {@code block/cross} 画的话比例会拉满一整格，
 * 而且两张交叉平面从不同角度看形状不一样，玩家一转头剑就变样。</p>
 *
 * <p>但方块还是要留一个<b>可点击的碰撞盒</b>：{@code noCollission} 会让射线检测
 * 直接穿过方块，玩家就点不到它了。这里给了一个 6~10 的细柱形
 * （形状和剑的位置对得上，玩家对着剑点就行）。</p>
 */
public class HegniAltarBlock extends Block implements EntityBlock
{
    /** 要点满多少下。 */
    public static final int REQUIRED_CLICKS = 100;
    /** 站多近会被凋零盯上（格）。 */
    public static final double WITHER_RADIUS = 12.0D;

    /** 可点击范围：中心一根 4x16x4 的细柱。 */
    private static final VoxelShape SHAPE = Shapes.box(6.0D / 16.0D, 0.0D, 6.0D / 16.0D,
            10.0D / 16.0D, 1.0D, 10.0D / 16.0D);

    public HegniAltarBlock()
    {
        super(Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                // 挖不动：这把剑只能点出来，不能挖出来
                .strength(-1.0F, 3600000.0F)
                .noCollission()
                .noOcclusion()
                .lightLevel(state -> 3));
    }

    // —————————————————————— 方块实体 ——————————————————————

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state)
    {
        return new HegniAltarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type)
    {
        // 只在服务端跑：实体是服务端生成的，客户端那边会自然同步过去
        return level.isClientSide() ? null : (lvl, pos, st, be) ->
                HegniAltarBlockEntity.serverTick(lvl, pos, st, (HegniAltarBlockEntity) be);
    }

    // —————————————————————— 交互 ——————————————————————

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
        ModCurse.Data data = ModCurse.get(serverPlayer);
        if (data == null)
        {
            return InteractionResult.SUCCESS;
        }

        // 一个人只认一把剑。已经拔过的人再去点别的祭坛，剑不会再认他第二回——
        // 所以连计数都不必，直接给一句提示打发走，免得白点一百下。
        if (data.hegniClaimed())
        {
            serverPlayer.displayClientMessage(
                    Component.translatable("hegni.mythic_relic.already_claimed").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.SUCCESS;
        }

        data.addHegniProgress(1);
        int clicks = data.hegniProgress();

        if (clicks >= REQUIRED_CLICKS)
        {
            claim(serverPlayer, level, pos);
        }
        else
        {
            // 进度走「底部字幕」那条通道（和尼德霍格的评论同一套排版：居中、折行、衬底、
            // 让开聊天栏），不再刷聊天栏——连着点一百下，聊天栏会被刷得没法看。
            CommentaryPacket.sendPrompt(serverPlayer, "hegni.mythic_relic.progress",
                    clicks, REQUIRED_CLICKS);
            // 敲击声随进度越来越尖，给一点「快成了」的反馈
            level.playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS,
                    0.5F, 0.7F + Math.min(1.0F, clicks / (float) REQUIRED_CLICKS) * 0.8F);
        }
        return InteractionResult.SUCCESS;
    }

    /** 点满了：给剑、清进度、把祭坛和上面那把实体剑一起收掉。 */
    private static void claim(ServerPlayer player, Level level, BlockPos pos)
    {
        ItemStack sword = new ItemStack(ModItems.HEGNI_SWORD.get());
        // 先留一份副本再塞背包：Inventory.add() 会把传进去的那个栈**当场消耗掉**
        // （它内部是 addResource 反复 shrink，成功塞满后 count 归零、变成空栈），
        // 直接拿 sword 去播评论的话，speakForItem 会因 stack.isEmpty() 直接 return——
        // 这正是「拔剑时尼德霍格永远不吭声」的根因。
        ItemStack announced = sword.copy();
        if (!player.getInventory().add(sword))
        {
            player.drop(sword, false);
        }
        // 这是「直接发放」，不经过捡起 / 合成事件，得手动喊一声，
        // 否则尼德霍格对这把剑永远没有评论
        com.mythicrelic.chaos.NidhoggCommentary.onGranted(player, announced);
        ModCurse.Data data = ModCurse.get(player);
        if (data != null)
        {
            data.setHegniProgress(0);
            // 记下「拔出过」。凋零免疫与「一个人只认一把剑」都挂在这个标记上，
            // 和「此刻有没有拿着剑」无关。
            data.setHegniClaimed(true);
        }
        // 拔剑那一刻身上多半正挂着凋零（祭坛光环干的），立刻清掉，
        // 让「获得认可」这件事当场就能感觉到。
        player.removeEffect(net.minecraft.world.effect.MobEffects.WITHER);

        // 先把实体收掉再拆方块，否则方块一没、方块实体也没了，那个显示实体会永远留在原地
        if (level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof HegniAltarBlockEntity altar)
        {
            altar.removeSword(serverLevel);
        }
        level.removeBlock(pos, false);

        level.playSound(null, pos, SoundEvents.WITHER_DEATH, SoundSource.BLOCKS, 1.0F, 1.2F);

        // 拔出来之后紧接着就是尼德霍格对这把剑的评论（上面那句 onGranted），
        // 她那句本来就点明了「你居然真把它从石头里拔出来了」，所以不再另插系统提示，
        // 免得把节奏切开。
        // 但她只对**戴着印记的人**开口（ChaosEvents.hasMark），所以没戴印记的玩家
        // 补一条普通字幕——否则点满一百下之后什么字都没有。
        if (!ChaosEvents.hasMark(player))
        {
            CommentaryPacket.sendPrompt(player, "hegni.mythic_relic.claimed");
        }
    }

    // —————————————————————— 形状 ——————————————————————

    /**
     * 给一个细柱形的可点击范围。
     *
     * <p>方块本身 {@code noCollission}（不挡路、也不渲染），但射线检测读的是
     * {@code getShape}——不覆盖的话形状为空，玩家压根点不到它。</p>
     */
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    /** 这个方块没法被正常破坏——它只能在「点满一百下」时自己消失。 */
    @Override
    public float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos)
    {
        return player.getAbilities().instabuild ? super.getDestroyProgress(state, player, level, pos) : 0.0F;
    }
}
