package com.mythicrelic.blockentity;

import com.mythicrelic.registry.ModBlockEntities;
import com.mythicrelic.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 诅咒祭坛上那把剑的「实体化身」。
 *
 * <h2>为什么不用方块模型画</h2>
 * <p>原来是用 {@code minecraft:block/cross} 画的——两张互相垂直的平面。
 * 那个做法有两个毛病：16x16 的剑贴图会被拉满一整格，比例完全不对；
 * 而且两张平面从不同角度看形状不一样，玩家一转头剑就「变样」。</p>
 *
 * <p>所以改成生成一个 {@link Display.ItemDisplay} 实体举着这把剑：它渲染的是
 * <b>物品自己的模型</b>，比例正确、也不随视角变形。</p>
 *
 * <h2>怎么生成的</h2>
 * <p>区块生成阶段（{@code postProcess}）不能生成实体，所以这里让方块实体每 tick 检查一次：
 * 没记着剑、或者记着的那个实体已经没了，就补一个。区块一加载剑就在，
 * 不需要玩家走近。</p>
 *
 * <h2>⚠️ 参数只能靠 NBT 传</h2>
 * <p>{@code Display} 的 {@code setTransformation} / {@code setBillboardConstraints} 都是
 * <b>private</b>，{@code ItemDisplay#setItemStack} 也是包内可见——外部唯一能配它的路
 * 就是 {@link Entity#load(CompoundTag)}。所以这里是拼一段 NBT 再 load。</p>
 */
public class HegniAltarBlockEntity extends BlockEntity
{
    private static final String TAG_DISPLAY_ID = "SwordDisplay";

    /** 剑相对方块中心的偏移。0.5 就是方块正中——剑的下半截埋在石头里、柄露在上面。 */
    private static final double SWORD_Y_OFFSET = 0.5D;
    /** 剑绕竖轴转多少度——斜着插才像「插在地上」而不是正对着某个方向。 */
    private static final float SWORD_YAW = 35.0F;
    /** 放大倍数。默认 1.0 太小，看着像地上掉了把迷你剑。 */
    private static final float SWORD_SCALE = 1.5F;
    /**
     * 剑在自身平面里转多少度。
     *
     * <p>原版剑贴图是**斜着的**：剑尖在右上、柄在左下，对角线正好 45°。
     * 想让剑刃竖直朝下扎进石头里，就得再转 180°（让剑尖朝下）+ 45°（把对角线摆正），
     * 合计 225°。绕 Z 轴转即可——{@code fixed} 姿态下贴图就躺在 XY 平面里。</p>
     */
    private static final float SWORD_SPIN_DEGREES = 225.0F;

    @Nullable
    private UUID displayId;

    public HegniAltarBlockEntity(BlockPos pos, BlockState state)
    {
        super(ModBlockEntities.HEGNI_ALTAR.get(), pos, state);
    }

    /** 由方块注册成 ticker。 */
    public static void serverTick(Level level, BlockPos pos, BlockState state, HegniAltarBlockEntity self)
    {
        if (!(level instanceof ServerLevel server))
        {
            return;
        }
        if (self.displayId != null)
        {
            Entity existing = server.getEntity(self.displayId);
            if (existing != null && existing.isAlive())
            {
                return;
            }
        }
        self.spawnSword(server);
    }

    private void spawnSword(ServerLevel level)
    {
        Display.ItemDisplay display = new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);

        CompoundTag tag = new CompoundTag();
        tag.put("item", new ItemStack(ModItems.HEGNI_SWORD.get()).save(new CompoundTag()));
        // 用 fixed 而不是 ground：ground 是「躺在地上」的那个姿态，剑会横躺着。
        // fixed 不带任何内置旋转，姿态完全由下面的 transformation 说了算。
        tag.putString("item_display", "fixed");
        // 视野倍率：给大一点，免得走远一点剑就看不见了
        tag.putFloat("view_range", 3.0F);
        tag.put("transformation", buildTransformation());
        display.load(tag);

        display.setNoGravity(true);
        display.setPos(this.worldPosition.getX() + 0.5D,
                this.worldPosition.getY() + SWORD_Y_OFFSET,
                this.worldPosition.getZ() + 0.5D);
        display.setYRot(SWORD_YAW);

        level.addFreshEntity(display);
        this.displayId = display.getUUID();
        this.setChanged();
    }

    /**
     * 拼一段 {@code transformation} NBT。
     *
     * <p>格式是 {@code {translation:[x,y,z], scale:[x,y,z], left_rotation:[x,y,z,w], right_rotation:[x,y,z,w]}}，
     * 和原版 {@code Transformation.CODEC} 对得上。之所以拼 NBT 而不是直接调
     * {@code setTransformation()}：那个方法是 private，外部只有 load(NBT) 这一条路。</p>
     */
    private static CompoundTag buildTransformation()
    {
        CompoundTag transform = new CompoundTag();
        transform.put("translation", floatList(0.0F, 0.0F, 0.0F));
        transform.put("scale", floatList(SWORD_SCALE, SWORD_SCALE, SWORD_SCALE));

        // 绕 Z 轴转：四元数是 (0, 0, sin(θ/2), cos(θ/2))
        double half = Math.toRadians(SWORD_SPIN_DEGREES) / 2.0D;
        transform.put("left_rotation", floatList(0.0F, 0.0F,
                (float) Math.sin(half), (float) Math.cos(half)));
        transform.put("right_rotation", floatList(0.0F, 0.0F, 0.0F, 1.0F));
        return transform;
    }

    private static ListTag floatList(float... values)
    {
        ListTag list = new ListTag();
        for (float value : values)
        {
            list.add(FloatTag.valueOf(value));
        }
        return list;
    }

    /** 剑被拔走时把实体一起收掉，免得原地留一把看不见摸不着的幻影剑。 */
    public void removeSword(ServerLevel level)
    {
        if (this.displayId == null)
        {
            return;
        }
        Entity display = level.getEntity(this.displayId);
        if (display != null)
        {
            display.discard();
        }
        this.displayId = null;
        this.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);
        if (this.displayId != null)
        {
            tag.putUUID(TAG_DISPLAY_ID, this.displayId);
        }
    }

    @Override
    public void load(CompoundTag tag)
    {
        super.load(tag);
        this.displayId = tag.hasUUID(TAG_DISPLAY_ID) ? tag.getUUID(TAG_DISPLAY_ID) : null;
    }
}
