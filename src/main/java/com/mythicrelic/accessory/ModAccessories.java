package com.mythicrelic.accessory;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.Nullable;

/** 饰品栏能力：注册入口 + 玩家身上的提供者。 */
public final class ModAccessories
{
    public static final Capability<PlayerAccessories> ACCESSORIES =
            CapabilityManager.get(new CapabilityToken<>() {});

    private ModAccessories() {}

    /** 挂在玩家实体上的能力提供者。 */
    public static class Provider implements ICapabilitySerializable<CompoundTag>
    {
        private final PlayerAccessories accessories = new PlayerAccessories();
        private final LazyOptional<PlayerAccessories> optional = LazyOptional.of(() -> this.accessories);

        @Override
        public <T> LazyOptional<T> getCapability(Capability<T> capability, @Nullable Direction side)
        {
            return ACCESSORIES.orEmpty(capability, this.optional);
        }

        @Override
        public CompoundTag serializeNBT()
        {
            return this.accessories.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt)
        {
            this.accessories.deserializeNBT(nbt);
        }

        public void invalidate()
        {
            this.optional.invalidate();
        }
    }
}
