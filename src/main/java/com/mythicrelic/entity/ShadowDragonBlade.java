package com.mythicrelic.entity;

import com.mythicrelic.registry.ModEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 「暗影龙刃」——尼德霍格之印中阶能力的投射物。
 *
 * <p>一道贴着视线飞出去的漆黑剑气，命中后消失。伤害随蓄力时间从
 * {@value #MIN_DAMAGE} 涨到 {@value #MAX_DAMAGE}。</p>
 *
 * <p>运动是自己推的：{@link Projectile} 基类只提供了命中判定与 {@code onHit} 的分发，
 * 位移逻辑得由子类补上（原版是 {@code AbstractArrow} / {@code ThrowableProjectile} 在做）。</p>
 */
public class ShadowDragonBlade extends Projectile
{
    public static final float MIN_DAMAGE = 4.0F;
    public static final float MAX_DAMAGE = 16.0F;
    /** 最长存活 tick，飞满就散掉。 */
    private static final int MAX_LIFE = 60;
    /** 每 tick 的速度衰减，让它慢慢变慢。 */
    private static final double DRAG = 0.985D;

    private float damage = MIN_DAMAGE;
    /**
     * 蓄力比例 0~1。
     *
     * <p>单独存一份，不再从 {@link #damage} 反推——手上武器的加成也进了伤害里，
     * 反推的话拿把好剑就会把渲染器撑成巨无霸。</p>
     */
    private float charge;
    /** 手上武器加进来的那部分伤害（只为存档与排查，不参与渲染）。 */
    private float weaponBonus;
    private int life;

    public ShadowDragonBlade(EntityType<? extends ShadowDragonBlade> type, Level level)
    {
        super(type, level);
        this.noCulling = true;
    }

    /**
     * @param charge       蓄力比例 0~1，决定基础伤害与剑气大小
     * @param weaponBonus  手上武器的攻击力（服务端自己读的，空手时为 0），直接加在伤害上
     */
    public ShadowDragonBlade(Level level, LivingEntity owner, float charge, float weaponBonus)
    {
        this(ModEntities.SHADOW_DRAGON_BLADE.get(), level);
        this.setOwner(owner);
        this.charge = Mth.clamp(charge, 0.0F, 1.0F);
        this.weaponBonus = Math.max(0.0F, weaponBonus);
        this.damage = MIN_DAMAGE + (MAX_DAMAGE - MIN_DAMAGE) * this.charge + this.weaponBonus;
        this.setPos(owner.getX(), owner.getEyeY() - 0.2D, owner.getZ());
    }

    /** 蓄力比例，渲染器拿它决定剑气的大小。 */
    public float chargeRatio()
    {
        return this.charge;
    }

    @Override
    protected void defineSynchedData()
    {
    }

    @Override
    public void tick()
    {
        super.tick();

        if (++this.life > MAX_LIFE)
        {
            this.discard();
            return;
        }

        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS && !net.minecraftforge.event.ForgeEventFactory.onProjectileImpact(this, hit))
        {
            this.onHit(hit);
        }
        if (this.isRemoved())
        {
            return;
        }

        Vec3 motion = this.getDeltaMovement();
        this.setPos(this.getX() + motion.x, this.getY() + motion.y, this.getZ() + motion.z);
        this.setDeltaMovement(motion.scale(DRAG));

        if (this.level().isClientSide())
        {
            // 拖尾走「近黑 + 暗紫」：墨色打底，龙息补紫，偶尔一点传送门的幽光。
            // 之前用的灵魂火是青白色的，看着像圣光不像暗影。
            this.level().addParticle(ParticleTypes.SQUID_INK,
                    this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
            if (this.life % 2 == 0)
            {
                this.level().addParticle(ParticleTypes.DRAGON_BREATH,
                        this.getX(), this.getY(), this.getZ(),
                        (this.random.nextDouble() - 0.5D) * 0.25D,
                        (this.random.nextDouble() - 0.5D) * 0.25D,
                        (this.random.nextDouble() - 0.5D) * 0.25D);
            }
            if (this.life % 3 == 0)
            {
                this.level().addParticle(ParticleTypes.PORTAL,
                        this.getX(), this.getY(), this.getZ(),
                        (this.random.nextDouble() - 0.5D) * 0.6D,
                        (this.random.nextDouble() - 0.5D) * 0.6D,
                        (this.random.nextDouble() - 0.5D) * 0.6D);
            }
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result)
    {
        super.onHitEntity(result);
        if (this.level().isClientSide())
        {
            return;
        }
        Entity owner = this.getOwner();
        DamageSource source = owner instanceof Player player
                ? this.damageSources().playerAttack(player)
                : this.damageSources().magic();
        result.getEntity().hurt(source, this.damage);
    }

    @Override
    protected void onHit(HitResult result)
    {
        super.onHit(result);
        if (!this.level().isClientSide())
        {
            this.level().broadcastEntityEvent(this, (byte) 3);
            this.discard();
        }
    }

    @Override
    public boolean isPickable()
    {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.charge = tag.contains("Charge") ? tag.getFloat("Charge") : 0.0F;
        this.weaponBonus = tag.contains("WeaponBonus") ? tag.getFloat("WeaponBonus") : 0.0F;
        this.damage = tag.contains("Damage")
                ? tag.getFloat("Damage")
                : MIN_DAMAGE + (MAX_DAMAGE - MIN_DAMAGE) * this.charge + this.weaponBonus;
        this.life = tag.getInt("Life");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Damage", this.damage);
        tag.putFloat("Charge", this.charge);
        tag.putFloat("WeaponBonus", this.weaponBonus);
        tag.putInt("Life", this.life);
    }
}
