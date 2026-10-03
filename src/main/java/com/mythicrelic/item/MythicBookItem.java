package com.mythicrelic.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 《神话之书》——尼德霍格自己写的那本记录。
 *
 * <h2>内容从哪来</h2>
 * <p>正文<b>不写在 NBT 上</b>，而是直接走语言文件：第 {@code i} 页就是
 * {@code book.mythic_relic.page.<i>}。好处是</p>
 * <ul>
 *   <li>合成、开局发放、创造模式拿到的都是同一个普通物品，不用给每本书铸一份 NBT；</li>
 *   <li>中英文自动跟着语言走；</li>
 *   <li>以后想加一页，只要 {@link #PAGE_COUNT} 加一、再补一条 lang 就行。</li>
 * </ul>
 *
 * <p>翻开用的是原版那本书的界面（{@code BookViewScreen}）——它只认一个
 * {@code BookAccess}，而那个接口只有 {@code getPageCount()} / {@code getPageRaw()}
 * 两个方法，随手实现一份即可，连网络包都不用发。</p>
 */
public class MythicBookItem extends Item
{
    /**
     * 总页数：前 2 页是目录（page 0 / page 1），正文从 page 2 起。
     * 改这个数字的同时记得在 lang 里补齐对应的 {@code book.mythic_relic.page.N} 与目录键。
     */
    public static final int PAGE_COUNT = 19;

    public MythicBookItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide())
        {
            // 只在客户端加载这个屏幕类；服务端不会碰到它
            com.mythicrelic.client.MythicBookScreen.open();
        }
        player.swing(hand, true);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("tooltip.mythic_relic.mythic_book.1")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("tooltip.mythic_relic.mythic_book.flavor")
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.add(Component.translatable("tooltip.mythic_relic.mythic_book.flavor2")
                .withStyle(ChatFormatting.DARK_GRAY));
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
