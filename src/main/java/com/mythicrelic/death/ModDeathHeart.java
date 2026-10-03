package com.mythicrelic.death;

import com.mythicrelic.MythicRelic;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 「死亡之心」能力的注册入口。
 *
 * <p>能力挂载在玩家身上，由 {@link DeathHeartEvents} 在 AttachCapabilitiesEvent 里触发。</p>
 */
public final class ModDeathHeart
{
    public static final Capability<PlayerDeathHeart> CAPABILITY =
            CapabilityManager.get(new CapabilityToken<>() {});

    public static final ResourceLocation ID =
            new ResourceLocation(MythicRelic.MODID, "death_heart");

    private ModDeathHeart() {}

    /** 便捷 getter，玩家未挂载时返回 null。 */
    @Nullable
    public static PlayerDeathHeart get(@Nullable Player player)
    {
        if (player == null)
        {
            return null;
        }
        return player.getCapability(CAPABILITY).resolve().orElse(null);
    }

    /** 挂在玩家实体上的能力提供者（与 {@code ModChaos.Provider} 相同模式）。 */
    public static class Provider implements ICapabilitySerializable<CompoundTag>
    {
        private final PlayerDeathHeart instance = new PlayerDeathHeart();
        private final LazyOptional<PlayerDeathHeart> optional = LazyOptional.of(() -> this.instance);

        @Override
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability,
                                                  @Nullable Direction side)
        {
            return CAPABILITY.orEmpty(capability, this.optional);
        }

        @Override
        public CompoundTag serializeNBT()
        {
            return this.instance.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt)
        {
            this.instance.deserializeNBT(nbt);
        }
    }
}
