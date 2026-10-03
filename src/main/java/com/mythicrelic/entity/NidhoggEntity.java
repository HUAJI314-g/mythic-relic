package com.mythicrelic.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * 上古混沌神龙·尼德霍格——封印之地那场演出的主角。
 *
 * <p>她不是一个能被打的 BOSS，而是一段「活着」的演出：没有 AI、无敌、无重力、沉默，
 * 悬在祭坛上方，始终面向玩家。真正驱动她的是两个同步字段：</p>
 * <ul>
 *   <li>{@code emerge}（0→1）：从黑暗里凝聚成形的进度，渲染器据此把她从一点放大到全尺寸；</li>
 *   <li>{@code vanish}（0→1）：化作紫光涌入玩家体内时的收缩进度。</li>
 * </ul>
 */
public class NidhoggEntity extends PathfinderMob
{
    private static final EntityDataAccessor<Float> DATA_EMERGE =
            SynchedEntityData.defineId(NidhoggEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_VANISH =
            SynchedEntityData.defineId(NidhoggEntity.class, EntityDataSerializers.FLOAT);

    /** 悬停时脚底相对祭坛顶面的高度（格）。 */
    public static final double HOVER_HEIGHT = 1.5D;

    private float emergeO;
    private float emerge;
    private float vanishO;
    private float vanish;

    public NidhoggEntity(EntityType<? extends NidhoggEntity> type, Level level)
    {
        super(type, level);
        this.noCulling = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setSilent(true);
        this.setNoAi(true);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1024.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_EMERGE, 0.0F);
        this.entityData.define(DATA_VANISH, 0.0F);
    }

    @Override
    protected void registerGoals()
    {
        // 不需要任何 AI：她只是一段演出。
    }

    // —————————————————————— 演出进度（同步） ——————————————————————

    public float getEmerge(float partialTicks)
    {
        return Mth.lerp(partialTicks, this.emergeO, this.emerge);
    }

    public void setEmerge(float value)
    {
        this.entityData.set(DATA_EMERGE, Mth.clamp(value, 0.0F, 1.0F));
    }

    public float getVanish(float partialTicks)
    {
        return Mth.lerp(partialTicks, this.vanishO, this.vanish);
    }

    public void setVanish(float value)
    {
        this.entityData.set(DATA_VANISH, Mth.clamp(value, 0.0F, 1.0F));
    }

    @Override
    public void tick()
    {
        this.emergeO = this.emerge;
        this.vanishO = this.vanish;
        super.tick();
        this.emerge = this.entityData.get(DATA_EMERGE);
        this.vanish = this.entityData.get(DATA_VANISH);
    }

    /** 永远面向最近的玩家。 */
    @Override
    public void aiStep()
    {
        super.aiStep();
        if (this.level().isClientSide())
        {
            return;
        }
        Player player = this.level().getNearestPlayer(this, 64.0D);
        if (player != null && player.isAlive())
        {
            double dx = player.getX() - this.getX();
            double dz = player.getZ() - this.getZ();
            double dy = (player.getY() + player.getEyeHeight()) - (this.getY() + 1.7D);
            float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
            float pitch = (float) (-(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * (180.0D / Math.PI)));
            this.setYRot(yaw);
            this.setYHeadRot(yaw);
            this.yBodyRot = yaw;
            this.yHeadRotO = yaw;
            this.setXRot(Mth.clamp(pitch, -20.0F, 20.0F));
        }
    }

    // —————————————————————— 不参与物理与交互 ——————————————————————

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    public boolean canBeCollidedWith()
    {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source)
    {
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer)
    {
        return false;
    }

    @Override
    public void push(Entity entity)
    {
        // 不接受任何推挤
    }

    @Override
    protected void pushEntities()
    {
        // 不推挤任何实体
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Emerge", this.emerge);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.setEmerge(tag.getFloat("Emerge"));
    }
}
