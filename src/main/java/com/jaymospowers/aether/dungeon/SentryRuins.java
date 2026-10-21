package com.jaymospowers.aether.dungeon;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/**
 * The Sentry Ruins ({@code jaymospowers:sentry_ruins}): a dungeon buried in an Aether island, with the Slider at its bottom and a
 * small ruin at the surface to mark the way down. The pieces are built in code ({@link RuinPiece}, laid out by
 * {@link RuinLayout}); the data (biome tag, spacing) is in {@code worldgen/structure} and {@code worldgen/structure_set}.
 */
public final class SentryRuins extends Structure {
	public static final MapCodec<SentryRuins> CODEC = simpleCodec(SentryRuins::new);
	public static StructureType<SentryRuins> TYPE;

	public SentryRuins(StructureSettings settings) {
		super(settings);
	}

	static void init() {
		TYPE = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, JaymosPowers.id("sentry_ruins"), () -> CODEC);
		RuinPiece.TYPE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, JaymosPowers.id("sentry_ruins_piece"),
			(StructurePieceType.ContextlessType)RuinPiece::new);
	}

	@Override
	protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
		ChunkPos chunk = context.chunkPos();
		int cx = chunk.getMiddleBlockX(), cz = chunk.getMiddleBlockZ();
		WorldgenRandom random = context.random();
		RuinLayout.Terrain terrain = new RuinLayout.Terrain(context);
		int top = terrain.top(cx, cz);
		for (int attempt = 0; attempt < RuinLayout.ATTEMPTS; attempt++) {
			List<RuinPiece> pieces = RuinLayout.build(terrain, random, cx, cz);
			if (pieces != null) {
				// (the spot the biome is read at: the ground above the boss room)
				return Optional.of(new GenerationStub(new BlockPos(cx, top, cz), builder -> pieces.forEach(builder::addPiece)));
			}
		}
		return Optional.empty();
	}

	@Override
	public StructureType<?> type() {
		return TYPE;
	}
}
