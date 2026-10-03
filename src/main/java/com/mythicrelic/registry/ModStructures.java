package com.mythicrelic.registry;

import com.mythicrelic.MythicRelic;
import com.mythicrelic.worldgen.HegniAltarPiece;
import com.mythicrelic.worldgen.HegniAltarStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 世界生成结构的注册。
 *
 * <p>两个注册表都要挂：</p>
 * <ul>
 *   <li>{@code STRUCTURE_TYPE} —— 结构本体，让 {@code data/mythic_relic/worldgen/structure/*.json}
 *       里的 {@code "type"} 能找到对应的 {@code Codec}；</li>
 *   <li>{@code STRUCTURE_PIECE} —— 结构片段，读存档时要靠它把 NBT 还原成片段对象。</li>
 * </ul>
 *
 * <p>{@link StructureType} 是函数式接口（只有一个 {@code codec()}），
 * 所以直接写成 lambda；{@link StructurePieceType} 用 {@code ContextlessType}
 * 那条路——我们的片段只需要「从 NBT 还原」这一件事。</p>
 */
public final class ModStructures
{
    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, MythicRelic.MODID);

    public static final DeferredRegister<StructurePieceType> PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, MythicRelic.MODID);

    /** 诅咒祭坛。注册名要和 {@code worldgen/structure/hegni_altar.json} 里的 type 对上。 */
    public static final RegistryObject<StructureType<HegniAltarStructure>> HEGNI_ALTAR =
            STRUCTURE_TYPES.register("hegni_altar", () -> () -> HegniAltarStructure.CODEC);

    public static final RegistryObject<StructurePieceType> HEGNI_ALTAR_PIECE =
            PIECE_TYPES.register("hegni_altar",
                    () -> (StructurePieceType.ContextlessType) HegniAltarPiece::new);

    private ModStructures() {}
}
