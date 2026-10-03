package com.mythicrelic.death;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.util.INBTSerializable;

/**
 * 玩家身上的「死亡之心」能力数据。
 *
 * <ul>
 *   <li>{@code cooldownEndTick} —— 冷却结束的<b>世界 tick 总数</b>，0 表示无冷却。</li>
 *   <li>{@code activeEndTick}   —— 回溯生效结束的 tick 总数，0 表示当前未生效。</li>
 *   <li>{@code virtualHealth}   —— 虚拟血量，可以是负数。真实 MC 血量在回溯期间固定在 0.5f，
 *       所有进一步的伤害都被扣到这里面。</li>
 * </ul>
 *
 * <p>之所以存「结束 tick 总数」而不是「剩余秒数」，是为了让不同游戏时长、暂停/后台等
 * 情况下保持一致——Forge 的 TickEvent 是游戏逻辑 tick，不受服务器卡顿或时间跳变影响。</p>
 */
public class PlayerDeathHeart implements INBTSerializable<CompoundTag>
{
    /** 5 分钟（回溯持续时间）的 tick 数。 */
    public static final int DURATION_TICKS = 20 * 60 * 5;
    /** 300 秒冷却的 tick 数。 */
    public static final int COOLDOWN_TICKS = 20 * 300;

    private static final String TAG_COOLDOWN = "CooldownEndTick";
    private static final String TAG_ACTIVE_END = "ActiveEndTick";
    private static final String TAG_VIRTUAL_HP = "VirtualHealth";

    private int cooldownEndTick;
    private int activeEndTick;
    private float virtualHealth;

    // —————————————— 访问器 ——————————————

    public int cooldownEndTick()
    {
        return cooldownEndTick;
    }

    public int activeEndTick()
    {
        return activeEndTick;
    }

    public float virtualHealth()
    {
        return virtualHealth;
    }

    public boolean isActive()
    {
        return activeEndTick > 0;
    }

    /** 是否可以触发下一次死亡回溯（冷却过了）。 */
    public boolean canTrigger(int currentTick)
    {
        return currentTick >= cooldownEndTick && activeEndTick == 0;
    }

    // —————————————— 修改器 ——————————————

    /** 触发回溯：从给定的 tick 开始进入持续期 + 冷却期。 */
    public void trigger(int currentTick)
    {
        this.activeEndTick = currentTick + DURATION_TICKS;
        this.cooldownEndTick = currentTick + COOLDOWN_TICKS;
        this.virtualHealth = 0.5f;   // 从死亡线开始
    }

    /** 回溯期间：扣虚拟血量。返回实际能扣的量（不超过当前虚拟血量）。 */
    public float subtractVirtual(float amount)
    {
        float old = this.virtualHealth;
        this.virtualHealth -= amount;
        this.virtualHealth = Math.max(-50.0f, this.virtualHealth);  // 允许的最低虚拟血量
        return old - this.virtualHealth;
    }

    /** 回溯期间：恢复虚拟血量（回血效果）。返回真正加上去的量。 */
    public float addVirtual(float amount)
    {
        float old = this.virtualHealth;
        this.virtualHealth = Math.min(20.0f, this.virtualHealth + amount);  // 上限与常规 maxHealth 对齐
        return this.virtualHealth - old;
    }

    /** 强制结束回溯（成功活下来了）。 */
    public void end()
    {
        this.activeEndTick = 0;
        this.virtualHealth = 0;
    }

    // —————————————— 序列化 ——————————————

    @Override
    public CompoundTag serializeNBT()
    {
        CompoundTag tag = new CompoundTag();
        tag.putInt(TAG_COOLDOWN, cooldownEndTick);
        tag.putInt(TAG_ACTIVE_END, activeEndTick);
        tag.putFloat(TAG_VIRTUAL_HP, virtualHealth);
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt)
    {
        this.cooldownEndTick = nbt.getInt(TAG_COOLDOWN);
        this.activeEndTick = nbt.getInt(TAG_ACTIVE_END);
        this.virtualHealth = nbt.getFloat(TAG_VIRTUAL_HP);
    }
}
