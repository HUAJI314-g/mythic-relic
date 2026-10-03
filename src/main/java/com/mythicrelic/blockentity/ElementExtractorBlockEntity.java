package com.mythicrelic.blockentity;

import com.mythicrelic.block.ElementExtractorBlock;
import com.mythicrelic.menu.ElementExtractorMenu;
import com.mythicrelic.recipe.ElementExtractorRecipe;
import com.mythicrelic.registry.ModBlockEntities;
import com.mythicrelic.registry.ModRecipes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * 元素提取器的方块实体。
 *
 * <h2>槽位布局</h2>
 * <p>共 {@value #CONTAINER_SIZE} 格：第 {@value #INPUT_SLOT} 格是输入，
 * 后面 {@value #OUTPUT_SLOT_COUNT} 格是<b>并排的输出</b>。
 * 输出做成三格，是为了让一份原料能同时吐出好几层能量
 * （末影珍珠 = 混沌 + 空间），且互不挤占。</p>
 */
public class ElementExtractorBlockEntity extends BlockEntity implements MenuProvider
{
    public static final int INPUT_SLOT = 0;
    /** 输出槽数量。 */
    public static final int OUTPUT_SLOT_COUNT = 3;
    /** 第一个输出槽的下标。 */
    public static final int FIRST_OUTPUT_SLOT = 1;
    /** 1 个输入 + 3 个输出。 */
    public static final int CONTAINER_SIZE = 1 + OUTPUT_SLOT_COUNT;

    public static final int DATA_PROGRESS = 0;
    public static final int DATA_DURATION = 1;

    private final SimpleContainer container = new SimpleContainer(CONTAINER_SIZE)
    {
        @Override
        public void setChanged()
        {
            ElementExtractorBlockEntity.this.setChanged();
        }
    };

    private final ContainerData data = new ContainerData()
    {
        @Override
        public int get(int index)
        {
            return switch (index)
            {
                case DATA_PROGRESS -> progress;
                case DATA_DURATION -> totalDuration;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value)
        {
            switch (index)
            {
                case DATA_PROGRESS -> progress = value;
                case DATA_DURATION -> totalDuration = value;
                default -> { }
            }
        }

        @Override
        public int getCount()
        {
            return 2;
        }
    };

    private int progress = 0;
    private int totalDuration = ElementExtractorRecipe.DEFAULT_DURATION;

    public ElementExtractorBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.ELEMENT_EXTRACTOR.get(), pos, state);
    }

    public SimpleContainer getContainer()
    {
        return this.container;
    }

    public ContainerData getData()
    {
        return this.data;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ElementExtractorBlockEntity blockEntity)
    {
        if (level.isClientSide())
        {
            return;
        }

        boolean changed = false;
        Optional<ElementExtractorRecipe> recipe = level.getRecipeManager()
                .getRecipeFor(ModRecipes.ELEMENT_EXTRACTOR_TYPE.get(), blockEntity.container, level);

        if (recipe.isPresent() && blockEntity.canAcceptResult(recipe.get()))
        {
            ElementExtractorRecipe found = recipe.get();
            blockEntity.totalDuration = found.getDuration();
            blockEntity.progress++;
            if (blockEntity.progress >= blockEntity.totalDuration)
            {
                blockEntity.progress = 0;
                blockEntity.craftItem(found);
                changed = true;
            }
        }
        else
        {
            blockEntity.progress = 0;
            blockEntity.totalDuration = ElementExtractorRecipe.DEFAULT_DURATION;
        }

        boolean lit = blockEntity.progress > 0;
        if (state.getValue(ElementExtractorBlock.LIT) != lit)
        {
            level.setBlock(pos, state.setValue(ElementExtractorBlock.LIT, lit), Block.UPDATE_ALL);
        }

        if (changed)
        {
            blockEntity.setChanged();
        }
    }

    /** 三个输出槽里放不放得下这一炉的全部产出。 */
    private boolean canAcceptResult(ElementExtractorRecipe recipe)
    {
        ItemStack[] simulated = new ItemStack[OUTPUT_SLOT_COUNT];
        for (int i = 0; i < OUTPUT_SLOT_COUNT; i++)
        {
            simulated[i] = this.container.getItem(FIRST_OUTPUT_SLOT + i).copy();
        }
        for (ItemStack result : recipe.getResults())
        {
            if (!mergeInto(simulated, result))
            {
                return false;
            }
        }
        return true;
    }

    private void craftItem(ElementExtractorRecipe recipe)
    {
        ItemStack[] outputs = new ItemStack[OUTPUT_SLOT_COUNT];
        for (int i = 0; i < OUTPUT_SLOT_COUNT; i++)
        {
            outputs[i] = this.container.getItem(FIRST_OUTPUT_SLOT + i);
        }
        for (ItemStack result : recipe.getResults())
        {
            // canAcceptResult 已经验过放得下，这里必定成功
            mergeInto(outputs, result);
        }
        for (int i = 0; i < OUTPUT_SLOT_COUNT; i++)
        {
            this.container.setItem(FIRST_OUTPUT_SLOT + i, outputs[i]);
        }
        this.container.getItem(INPUT_SLOT).shrink(1);
        this.container.setChanged();
        this.setChanged();
    }

    /**
     * 把一份产出塞进这组输出槽：先叠到同物品上，再占空格；不够就返回 {@code false}。
     *
     * <p>槽位是<b>原地修改</b>的（传进来的是容器里那几个 ItemStack 本身，
     * 或者是一份拷贝），调用方自己决定要不要写回。</p>
     */
    private static boolean mergeInto(ItemStack[] slots, ItemStack result)
    {
        int remaining = result.getCount();

        // ① 先叠到已经有的同种物品上
        for (int i = 0; i < slots.length && remaining > 0; i++)
        {
            if (!slots[i].isEmpty() && ItemStack.isSameItemSameTags(slots[i], result))
            {
                int space = slots[i].getMaxStackSize() - slots[i].getCount();
                int moved = Math.min(space, remaining);
                slots[i].grow(moved);
                remaining -= moved;
            }
        }

        // ② 再找空格
        for (int i = 0; i < slots.length && remaining > 0; i++)
        {
            if (slots[i].isEmpty())
            {
                ItemStack placed = result.copy();
                int moved = Math.min(placed.getMaxStackSize(), remaining);
                placed.setCount(moved);
                slots[i] = placed;
                remaining -= moved;
            }
        }

        return remaining == 0;
    }

    @Override
    public Component getDisplayName()
    {
        return Component.translatable("container.mythic_relic.element_extractor");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player)
    {
        return new ElementExtractorMenu(containerId, playerInventory, this.container, this.data,
                this.level, this.getBlockPos());
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, snapshot());
        tag.putInt("Progress", this.progress);
        tag.putInt("TotalDuration", this.totalDuration);
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        NonNullList<ItemStack> list = NonNullList.withSize(this.container.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list);
        for (int i = 0; i < list.size(); i++)
        {
            this.container.setItem(i, list.get(i));
        }
        this.progress = tag.getInt("Progress");
        this.totalDuration = tag.getInt("TotalDuration");
        if (this.totalDuration <= 0)
        {
            this.totalDuration = ElementExtractorRecipe.DEFAULT_DURATION;
        }
    }

    private NonNullList<ItemStack> snapshot()
    {
        NonNullList<ItemStack> list = NonNullList.withSize(this.container.getContainerSize(), ItemStack.EMPTY);
        for (int i = 0; i < list.size(); i++)
        {
            list.set(i, this.container.getItem(i));
        }
        return list;
    }
}
