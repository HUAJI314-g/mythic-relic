package com.mythicrelic.chaos;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

/** 混沌进度能力：注册入口 + 玩家身上的提供者。 */
public final class ModChaos
{
    public static final Capability<PlayerChaos> CHAOS =
            CapabilityManager.get(new CapabilityToken<>() {});

    private ModChaos() {}

    /** 便捷取用；能力尚未挂上时返回 {@code null}。 */
    @Nullable
    public static PlayerChaos get(@Nullable Player player)
    {
        if (player == null)
        {
            return null;
        }
        return player.getCapability(CHAOS).resolve().orElse(null);
    }

    /** 挂在玩家实体上的能力提供者。 */
    public static class Provider implements ICapabilitySerializable<CompoundTag>
    {
        private final PlayerChaos chaos = new PlayerChaos();
        private final LazyOptional<PlayerChaos> optional = LazyOptional.of(() -> this.chaos);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side)
        {
            return CHAOS.orEmpty(capability, this.optional);
        }

        @Override
        public CompoundTag serializeNBT()
        {
            return this.chaos.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt)
        {
            this.chaos.deserializeNBT(nbt);
        }

        public void invalidate()
        {
            this.optional.invalidate();
        }
    }
}
