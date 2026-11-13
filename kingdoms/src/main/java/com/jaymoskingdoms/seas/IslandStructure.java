package com.jaymoskingdoms.seas;

import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.codec.RegistryCodecs;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A structure that stands only on an island (Andy's ocean pick-list: "the ones that spawn on islands dont spawn
 * inland"): one of {@code structures} (its own, or one written in place), placed only where {@link Offshore#island}
 * says the land is an island, and, with {@code far}, only that far out to sea. The one picked is the first whose own
 * biomes take the island's biome, else any; it then stands as it would anywhere, held to this structure's biomes.
 * <p>
 * Used for the Tideborn's island villages, the sea hermit's tower, wrecks thrown up on island beaches, the caches
 * on uncharted islands, the villages on the islands of Mohknee's crossing (with {@code only_at}) and (with {@code island} false) the sunken wrecks, which lie only out at sea (data {@code worldgen/structure}, type {@code jaymoskingdoms:island}).
 */
public final class IslandStructure extends Structure {
	public static final MapCodec<IslandStructure> CODEC = RecordCodecBuilder.<IslandStructure>mapCodec(i -> i.group(
			settingsCodec(i),
			RegistryCodecs.holderSet(Registries.STRUCTURE, Structure.DIRECT_CODEC).fieldOf("structures").forGetter(s -> s.structures),
			Codec.floatRange(0.0F, 1.0F).optionalFieldOf("far", 0.0F).forGetter(s -> s.far),
			Codec.BOOL.optionalFieldOf("island", true).forGetter(s -> s.island),
			BlockPos.CODEC.listOf().optionalFieldOf("only_at", List.of()).forGetter(s -> s.onlyAt))
		.apply(i, IslandStructure::new));

	public static StructureType<IslandStructure> TYPE;

	private final HolderSet<Structure> structures;
	/** How far out to sea the island must lie ({@link Offshore#far}, 0 any island). */
	private final float far;
	/** False for one that needs only open sea, not an island (the wrecks on the sea floor, out of sight of land). */
	private final boolean island;
	/**
	 * With any: it stands only in the chunks these blocks are in, one to a place (the islands of Mohknee's crossing,
	 * {@link Mohknee#ISLANDS}; its structure set then offers every chunk, and this turns all the others down at once).
	 */
	private final List<BlockPos> onlyAt;

	public IslandStructure(StructureSettings settings, HolderSet<Structure> structures, float far, boolean island, List<BlockPos> onlyAt) {
		super(settings);
		this.structures = structures;
		this.far = far;
		this.island = island;
		this.onlyAt = List.copyOf(onlyAt);
	}

	public static void register() {
		TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, JaymosKingdoms.id("island"), () -> CODEC);
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		if (!this.onlyAt.isEmpty() && this.onlyAt.stream().noneMatch(p -> p.getX() >> 4 == chunk.x() && p.getZ() >> 4 == chunk.z())) {
			return Optional.empty();
		}
		int x = chunk.getMiddleBlockX(), z = chunk.getMiddleBlockZ();
		Climate.Sampler sampler = context.climateSampler();
		if (this.island) {
			if (!Offshore.island(context.chunkGenerator(), context.heightAccessor(), context.randomState(), x, z)) {
				return Optional.empty();
			}
			if (this.far > 0.0F && Offshore.far(Offshore.ringContinentalness(sampler, x, z)) < this.far) {
				return Optional.empty();
			}
		} else if (Offshore.far(Offshore.continentalness(sampler, x, z)) < this.far) {
			return Optional.empty();
		}
		List<Holder<Structure>> choices = new ArrayList<>();
		for (Holder<Structure> h : this.structures) {
			if (h.isBound() && !(h.value() instanceof IslandStructure)) {
				choices.add(h);
			}
		}
		if (choices.isEmpty()) {
			return Optional.empty();
		}
		WorldgenRandom random = context.random();
		Holder<Structure> pick = choices.get(random.nextInt(choices.size()));
		int y = context.chunkGenerator().getSeaLevel();
		Holder<Biome> here = context.biomeResolver().getNoiseBiome(x >> 2, y >> 2, z >> 2);
		for (Holder<Structure> h : choices) {
			if (h.value().biomes().contains(here)) {
				pick = h;
				break;
			}
		}
		GenerationContext there = new GenerationContext(context.registryAccess(), context.chunkGenerator(), context.biomeSource(),
			sampler, context.randomState(), context.structureTemplateManager(), context.seed(), chunk, context.heightAccessor(),
			this.biomes()::contains);
		if (this.onlyAt.isEmpty()) {
			return pick.value().findValidGenerationPoint(there);
		}
		// the village it stands as is started here as the island's own (MohkneeStructureMixin turns every other away)
		Mohknee.OWN_VILLAGE.set(Boolean.TRUE);
		try {
			return pick.value().findValidGenerationPoint(there);
		} finally {
			Mohknee.OWN_VILLAGE.remove();
		}
	}

	@Override
	public StructureType<?> type() {
		return TYPE;
	}
}
