package com.mythicrelic.registry;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.entity.NidhoggEntity;
import com.mythicrelic.entity.ShadowDragonBlade;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities
{
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MythicRelic.MODID);

    /** 上古混沌神龙·尼德霍格。 */
    public static final RegistryObject<EntityType<NidhoggEntity>> NIDHOGG =
            ENTITY_TYPES.register("nidhogg",
                    () -> EntityType.Builder.of(NidhoggEntity::new, MobCategory.MISC)
                            .sized(1.2F, 2.6F)
                            .clientTrackingRange(12)
                            .fireImmune()
                            .build("nidhogg"));

    /** 「暗影龙刃」——尼德霍格之印中阶发射的剑气。 */
    public static final RegistryObject<EntityType<ShadowDragonBlade>> SHADOW_DRAGON_BLADE =
            ENTITY_TYPES.register("shadow_dragon_blade",
                    () -> EntityType.Builder.<ShadowDragonBlade>of(ShadowDragonBlade::new, MobCategory.MISC)
                            .sized(0.6F, 0.6F)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build("shadow_dragon_blade"));

    private ModEntities() {}
}
