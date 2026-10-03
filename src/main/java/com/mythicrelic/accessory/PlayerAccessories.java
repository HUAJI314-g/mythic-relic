package com.mythicrelic.accessory;

import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * 玩家身上的「饰品栏」数据。
 *
 * <p>槽位数固定为 {@link #SLOT_COUNT}，目前只用了第 0 格（尼德霍格之印），其余留作后续扩展。
 * 它同时实现 {@link Container}（给 Menu/Slot 用）与 {@link INBTSerializable}（存档、跨死亡保留）。</p>
 */
public class PlayerAccessories implements Container, INBTSerializable<CompoundTag>
{
    /** 饰品槽数量——后面还要往里加东西，所以一次留够。 */
    public static final int SLOT_COUNT = 6;

    private static final String TAG_ITEMS = "Items";
    private static final String TAG_SLOT = "Slot";

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

    @Override
    public int getContainerSize()
    {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty()
    {
        for (ItemStack stack : this.items)
        {
            if (!stack.isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot)
    {
        return this.items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount)
    {
        ItemStack stack = ContainerHelper.removeItem(this.items, slot, amount);
        if (!stack.isEmpty())
        {
            this.setChanged();
        }
        return stack;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot)
    {
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack)
    {
        this.items.set(slot, stack);
        if (stack.getCount() > this.getMaxStackSize())
        {
            stack.setCount(this.getMaxStackSize());
        }
        this.setChanged();
    }

    @Override
    public void setChanged()
    {
        // 数据直接挂在玩家能力上，无需额外脏标记。
    }

    @Override
    public boolean stillValid(Player player)
    {
        return true;
    }

    @Override
    public void clearContent()
    {
        this.items.clear();
    }

    /** 便捷方法：把物品放进第一个能放下的槽位。 */
    public boolean addToFirstFreeSlot(ItemStack stack)
    {
        for (int i = 0; i < SLOT_COUNT; i++)
        {
            if (this.items.get(i).isEmpty())
            {
                this.items.set(i, stack.copy());
                this.setChanged();
                return true;
            }
        }
        return false;
    }

    /** 是否已经拥有同一种物品（避免重复发放）。 */
    public boolean contains(net.minecraft.world.item.Item item)
    {
        for (ItemStack stack : this.items)
        {
            if (stack.is(item))
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public CompoundTag serializeNBT()
    {
        ListTag list = new ListTag();
        for (int i = 0; i < SLOT_COUNT; i++)
        {
            ItemStack stack = this.items.get(i);
            if (!stack.isEmpty())
            {
                CompoundTag entry = new CompoundTag();
                entry.putByte(TAG_SLOT, (byte) i);
                stack.save(entry);
                list.add(entry);
            }
        }
        CompoundTag root = new CompoundTag();
        root.put(TAG_ITEMS, list);
        return root;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt)
    {
        for (int i = 0; i < SLOT_COUNT; i++)
        {
            this.items.set(i, ItemStack.EMPTY);
        }
        ListTag list = nbt.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.getByte(TAG_SLOT) & 255;
            if (slot < SLOT_COUNT)
            {
                this.items.set(slot, ItemStack.of(entry));
            }
        }
    }
}
