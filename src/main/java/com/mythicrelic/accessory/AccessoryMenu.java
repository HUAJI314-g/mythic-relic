package com.mythicrelic.accessory;

import com.mythicrelic.registry.ModMenuTypes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** 饰品栏界面容器：上半部分是饰品槽，下半部分是玩家背包。 */
public class AccessoryMenu extends AbstractContainerMenu
{
    @SuppressWarnings("unused")
    private final PlayerAccessories accessories;

    /** 客户端构造：饰品内容由服务端同步下来。 */
    public AccessoryMenu(int id, Inventory playerInventory)
    {
        this(id, playerInventory, new PlayerAccessories());
    }

    public AccessoryMenu(int id, Inventory playerInventory, PlayerAccessories accessories)
    {
        super(ModMenuTypes.ACCESSORIES_MENU.get(), id);
        this.accessories = accessories;

        for (int i = 0; i < PlayerAccessories.SLOT_COUNT; i++)
        {
            int col = i % 3;
            int row = i / 3;
            this.addSlot(new AccessorySlot(accessories, i, 53 + col * 26, 22 + row * 26));
        }

        for (int row = 0; row < 3; row++)
        {
            for (int col = 0; col < 9; col++)
            {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++)
        {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

    @Override
    public boolean stillValid(Player player)
    {
        return true;
    }

    /**
     * Shift + 点击（左键/右键都行）搬运：
     * <ul>
     *   <li>点饰品槽 → 搬回背包（绑定饰品会被 {@link AccessorySlot#mayPickup} 挡下）；</li>
     *   <li>点背包里的饰品 → 搬进第一个空的饰品槽；</li>
     *   <li>点背包里的普通物品 → 什么也不做（饰品栏只收本模组的饰品）。</li>
     * </ul>
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem())
        {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();

        if (index < PlayerAccessories.SLOT_COUNT)
        {
            if (!slot.mayPickup(player)
                    || !this.moveItemStackTo(stack, PlayerAccessories.SLOT_COUNT, this.slots.size(), true))
            {
                return ItemStack.EMPTY;
            }
        }
        else
        {
            if (!(stack.getItem() instanceof AccessoryItem)
                    || !this.moveItemStackTo(stack, 0, PlayerAccessories.SLOT_COUNT, false))
            {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty())
        {
            slot.setByPlayer(ItemStack.EMPTY);
        }
        else
        {
            slot.setChanged();
        }
        return copy;
    }
}
