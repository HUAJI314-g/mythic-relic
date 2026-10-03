package com.mythicrelic.menu;

import com.mythicrelic.blockentity.ElementExtractorBlockEntity;
import com.mythicrelic.registry.ModBlocks;
import com.mythicrelic.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 元素提取器的界面容器。
 *
 * <p>机器那一段是 <b>1 个输入 + 3 个并排输出</b>：
 * 输入在左（x={@value #INPUT_X}），中间是进度箭头，右边三格输出依次排开。
 * 输出槽一律 {@code mayPlace = false}，玩家只能取、不能塞。</p>
 */
public class ElementExtractorMenu extends AbstractContainerMenu
{
    public static final int CONTAINER_SIZE = ElementExtractorBlockEntity.CONTAINER_SIZE;

    /** 机器自身占用的槽位数量（1 输入 + 3 输出）。 */
    private static final int MACHINE_SLOTS = CONTAINER_SIZE;
    /** 玩家背包（27 格）在 slots 里的起始下标。 */
    private static final int PLAYER_INVENTORY_START = MACHINE_SLOTS;
    /** 快捷栏（9 格）的起始下标。 */
    private static final int HOTBAR_START = MACHINE_SLOTS + 27;

    private static final int INPUT_X = 26;
    private static final int SLOT_Y = 35;
    /** 第一个输出槽的 x，后面每格 +18。 */
    private static final int FIRST_OUTPUT_X = 96;

    private final Container container;
    private final ContainerData data;
    @Nullable
    private final Level level;
    private final BlockPos pos;

    /** Client side constructor, opened from the network. */
    public ElementExtractorMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buffer)
    {
        this(containerId, playerInventory, new SimpleContainer(CONTAINER_SIZE),
                new SimpleContainerData(2), null, buffer.readBlockPos());
    }

    /** Server side constructor. */
    public ElementExtractorMenu(int containerId, Inventory playerInventory, Container container,
                                ContainerData data, @Nullable Level level, BlockPos pos)
    {
        super(ModMenuTypes.ELEMENT_EXTRACTOR_MENU.get(), containerId);
        checkContainerSize(container, CONTAINER_SIZE);
        checkContainerDataCount(data, 2);
        this.container = container;
        this.data = data;
        this.level = level;
        this.pos = pos == null ? BlockPos.ZERO : pos;
        this.addDataSlots(data);

        // 输入
        this.addSlot(new Slot(container, ElementExtractorBlockEntity.INPUT_SLOT, INPUT_X, SLOT_Y));
        // 三个并排输出：只能取，不能放
        for (int i = 0; i < ElementExtractorBlockEntity.OUTPUT_SLOT_COUNT; i++)
        {
            this.addSlot(new Slot(container, ElementExtractorBlockEntity.FIRST_OUTPUT_SLOT + i,
                    FIRST_OUTPUT_X + i * 18, SLOT_Y)
            {
                @Override
                public boolean mayPlace(ItemStack stack)
                {
                    return false;
                }
            });
        }

        for (int row = 0; row < 3; ++row)
        {
            for (int column = 0; column < 9; ++column)
            {
                this.addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int index = 0; index < 9; ++index)
        {
            this.addSlot(new Slot(playerInventory, index, 8 + index * 18, 142));
        }
    }

    public int getProgress()
    {
        return this.data.get(0);
    }

    public int getTotalDuration()
    {
        int duration = this.data.get(1);
        return duration <= 0 ? 1 : duration;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        ItemStack original = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem())
        {
            ItemStack inSlot = slot.getItem();
            original = inSlot.copy();

            if (index < MACHINE_SLOTS)
            {
                // 机器里的东西（输入或输出）→ 全部挪回背包
                if (!this.moveItemStackTo(inSlot, PLAYER_INVENTORY_START, this.slots.size(), true))
                {
                    return ItemStack.EMPTY;
                }
            }
            else if (!this.moveItemStackTo(inSlot, ElementExtractorBlockEntity.INPUT_SLOT,
                    ElementExtractorBlockEntity.INPUT_SLOT + 1, false))
            {
                // 输入槽满了 / 放不进 → 只在背包的两段之间对调
                if (index < HOTBAR_START)
                {
                    if (!this.moveItemStackTo(inSlot, HOTBAR_START, this.slots.size(), false))
                    {
                        return ItemStack.EMPTY;
                    }
                }
                else if (!this.moveItemStackTo(inSlot, PLAYER_INVENTORY_START, HOTBAR_START, false))
                {
                    return ItemStack.EMPTY;
                }
            }

            if (inSlot.isEmpty())
            {
                slot.set(ItemStack.EMPTY);
            }
            else
            {
                slot.setChanged();
            }

            if (inSlot.getCount() == original.getCount())
            {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, inSlot);
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player)
    {
        if (this.level == null)
        {
            return true;
        }
        return ContainerLevelAccess.create(this.level, this.pos)
                .evaluate((level, pos) -> level.getBlockState(pos).is(ModBlocks.ELEMENT_EXTRACTOR.get()), true);
    }
}
