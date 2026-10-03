package com.mythicrelic.worldgen;

import com.mythicrelic.registry.ModStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * 「诅咒祭坛」——一个真正的世界生成结构。
 *
 * <p>做成 worldgen 结构（而不是运行时随机搭）的关键好处是<b>它进得了
 * {@code Registries.STRUCTURE}</b>：{@code /locate structure mythic_relic:hegni_altar}
 * 才找得到，原版那套 {@code structure_set} 的间距/分布也才管用。</p>
 *
 * <p>生成点很简单：每个被选中的区块，取区块中心正上方的地表，在那里放一个
 * {@link HegniAltarPiece}。「每个区块选不选」不在这里管——那是
 * {@code worldgen/structure_set/hegni_altar.json} 里 {@code random_spread} 的事。</p>
 */
public class HegniAltarStructure extends Structure
{
    // 用 RecordCodecBuilder.create（返回 Codec）而不是 mapCodec（返回 MapCodec）——
    // StructureType.codec() 要的是 Codec。
    public static final Codec<HegniAltarStructure> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(settingsCodec(instance))
                    .apply(instance, HegniAltarStructure::new));

    public HegniAltarStructure(StructureSettings settings)
    {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context)
    {
        // onTopOfChunkCenter：把生成点定在区块中心的地表高度上
        return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG, builder ->
                builder.addPiece(new HegniAltarPiece(context.random(),
                        context.chunkPos().getMinBlockX(), context.chunkPos().getMinBlockZ())));
    }

    @Override
    public StructureType<?> type()
    {
        return ModStructures.HEGNI_ALTAR.get();
    }
}
