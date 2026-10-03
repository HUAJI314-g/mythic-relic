package com.mythicrelic.book;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.chaos.ModChaos;
import com.mythicrelic.chaos.NidhoggCommentary;
import com.mythicrelic.chaos.PlayerChaos;
import com.mythicrelic.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 《神话之书》的发放：每个玩家<b>开局一次</b>。
 *
 * <p>「发过了没」记在 {@link PlayerChaos#bookGiven()} 上，落盘保存。
 * 不给这个标记的话，玩家把书弄丢之后每次登录都会再被塞一本，那就成骚扰了。
 * 书丢了只能自己再造一本（书 + 黑曜石），这本来就是这本的设定。</p>
 */
@Mod.EventBusSubscriber(modid = MythicRelic.MODID)
public final class ModBookEvents
{
    private ModBookEvents() {}

    @SubscribeEvent
    public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        PlayerChaos chaos = ModChaos.get(player);
        if (chaos == null || chaos.bookGiven())
        {
            return;
        }

        ItemStack book = new ItemStack(ModItems.MYTHIC_BOOK.get());
        // Inventory.add() 会把传进去的栈当场消耗掉（塞满后变空栈），
        // 所以先留一份副本给评论用——不然尼德霍格对这本书也不会吭声。
        ItemStack announced = book.copy();
        if (!player.getInventory().add(book))
        {
            player.drop(book, false);
        }
        chaos.setBookGiven(true);

        // 直接发放，不经过捡起 / 合成事件
        NidhoggCommentary.onGranted(player, announced);
    }
}
