package com.mythicrelic.accessory;

import com.mythicrelic.MythicRelic;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** 饰品槽：只收本模组的饰品，绑定类饰品放进去后就取不下来了。 */
public class AccessorySlot extends Slot
{
    public AccessorySlot(Container container, int slot, int x, int y)
    {
        super(container, slot, x, y);
    }

    /**
     * 只允许<b>本模组</b>的饰品进入。
     *
     * <p>两道判据缺一不可：{@link AccessoryItem} 是「这是个饰品」的语义标记，
     * 而命名空间检查把它钉死在本模组——即使别的模组也实现（或反射实现）了这个接口，
     * 它的物品照样进不来。普通材料、武器更是直接被挡在门外。</p>
     */
    @Override
    public boolean mayPlace(ItemStack stack)
    {
        if (!(stack.getItem() instanceof AccessoryItem))
        {
            return false;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id != null && MythicRelic.MODID.equals(id.getNamespace());
    }

    @Override
    public boolean mayPickup(Player player)
    {
        ItemStack stack = this.getItem();
        if (stack.getItem() instanceof BoundAccessory bound)
        {
            return !bound.isBound();
        }
        return true;
    }

    @Override
    public int getMaxStackSize()
    {
        return 1;
    }
}
