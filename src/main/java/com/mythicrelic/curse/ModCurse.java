package com.mythicrelic.curse;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

/**
 * 「诅咒」相关的玩家数据 —— 目前只有赫格尼之剑的点击进度。
 *
 * <p>数据类与能力入口放在同一个文件里：它只有两个字段，拆成
 * {@code PlayerCurse} + {@code ModCurse} 两个文件反而更难找。</p>
 *
 * <p>进度是<b>按玩家</b>存的，挂在 Player 实体上，多人服务器里不会互串；
 * 死亡时清零（{@link HegniCurseEvents} 之外由 {@code CursedAltarEvents} 处理），
 * 因为「在死亡之前点满一百下」是这把剑的规矩。</p>
 */
public final class ModCurse
{
    public static final Capability<Data> CURSE = CapabilityManager.get(new CapabilityToken<>() {});

    private ModCurse() {}

    @Nullable
    public static Data get(@Nullable Player player)
    {
        return player == null ? null : player.getCapability(CURSE).resolve().orElse(null);
    }

    /** 玩家身上的诅咒数据。 */
    public static class Data implements INBTSerializable<CompoundTag>
    {
        private static final String TAG_HEGNI_PROGRESS = "HegniProgress";
        private static final String TAG_HEGNI_CLAIMED = "HegniClaimed";

        /** 已经对着赫格尼之剑点了几下。 */
        private int hegniProgress;

        /**
         * 是否已经拔出过赫格尼之剑。
         *
         * <p>这个标记是<b>永久</b>的，和「此刻有没有拿着剑」是两回事：
         * </p>
         * <ul>
         *   <li>凋零免疫挂在它上面——拔出来那一刻就「获得了剑的认可」，此后一辈子免疫，
         *       不用一直攥在手里；</li>
         *   <li>同时用它挡住重复获取：已经拔过的人再去点别的祭坛，剑不会再认他第二回。</li>
         * </ul>
         */
        private boolean hegniClaimed;

        public int hegniProgress()
        {
            return this.hegniProgress;
        }

        public boolean hegniClaimed()
        {
            return this.hegniClaimed;
        }

        public void setHegniClaimed(boolean value)
        {
            this.hegniClaimed = value;
        }

        public void setHegniProgress(int value)
        {
            this.hegniProgress = Math.max(0, value);
        }

        public void addHegniProgress(int amount)
        {
            setHegniProgress(this.hegniProgress + amount);
        }

        @Override
        public CompoundTag serializeNBT()
        {
            CompoundTag tag = new CompoundTag();
            tag.putInt(TAG_HEGNI_PROGRESS, this.hegniProgress);
            tag.putBoolean(TAG_HEGNI_CLAIMED, this.hegniClaimed);
            return tag;
        }

        @Override
        public void deserializeNBT(CompoundTag nbt)
        {
            this.hegniProgress = Math.max(0, nbt.getInt(TAG_HEGNI_PROGRESS));
            this.hegniClaimed = nbt.getBoolean(TAG_HEGNI_CLAIMED);
        }
    }

    /** 挂在玩家实体上的能力提供者。 */
    public static class Provider implements ICapabilitySerializable<CompoundTag>
    {
        private final Data data = new Data();
        private final LazyOptional<Data> optional = LazyOptional.of(() -> this.data);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side)
        {
            return CURSE.orEmpty(capability, this.optional);
        }

        @Override
        public CompoundTag serializeNBT()
        {
            return this.data.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt)
        {
            this.data.deserializeNBT(nbt);
        }

        public void invalidate()
        {
            this.optional.invalidate();
        }
    }
}
