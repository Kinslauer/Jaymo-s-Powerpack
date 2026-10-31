package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * (0.7.426) Havens patch 2: <b>bog iron</b>, ore held by a king. Lumps of it lie in the mud and clay under swamp water
 * (a few in each new swamp chunk). While no Ogre King has ever fallen it crumbles to rust when mined; once one falls
 * ({@link #fell}), it mines for everyone, giving raw bog iron.
 */
public final class BogIron {
	// ================================================================================================================
	// Tuning
	/** In each new swamp chunk: tries at a lump, its chance, and how many blocks a lump has at most. */
	static final int TRIES = 4, LUMP = 4;
	static final float CHANCE = 0.5F;
	/** How deep under the swamp floor a lump may lie. */
	static final int DEPTH = 4;
	// ================================================================================================================

	public static Block ORE;
	public static Item ORE_ITEM, RAW;
	/** On the Overworld: an Ogre King has fallen (bog iron mines). Saved. */
	static final AttachmentType<Boolean> FELLED = AttachmentRegistry.create(JaymosKingdoms.id("ogre_king_felled"), builder -> builder.persistent(Codec.BOOL));

	private BogIron() {
	}

	static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("bog_iron_ore"));
		ORE = Registry.register(BuiltInRegistries.BLOCK, key, new Block(BlockBehaviour.Properties.of().setId(key).mapColor(MapColor.TERRACOTTA_BROWN)
			.strength(2.0F, 3.0F).requiresCorrectToolForDrops().sound(SoundType.MUD_BRICKS)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("bog_iron_ore"));
		ORE_ITEM = Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(ORE, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));
		ResourceKey<Item> rawKey = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("raw_bog_iron"));
		RAW = Registry.register(BuiltInRegistries.ITEM, rawKey, new Item(new Item.Properties().setId(rawKey)));
		ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> {
			if (generated && level.dimension() == Level.OVERWORLD) {
				seed(level, chunk);
			}
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (!state.is(ORE) || !(level instanceof ServerLevel server) || felled(server.getServer()) || player.isCreative()) {
				return true;
			}
			// (held by a king: it crumbles to rust)
			server.destroyBlock(pos, false);
			server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.RED_SAND.defaultBlockState()), pos.getX() + 0.5, pos.getY() + 0.5,
				pos.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.05);
			server.playSound(null, pos, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS, 1.0F, 0.6F);
			if (player instanceof ServerPlayer sp) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bog_iron.crumbles"));
			}
			return false;
		});
	}

	/** An Ogre King has fallen somewhere: bog iron mines. */
	public static boolean felled(MinecraftServer server) {
		return Boolean.TRUE.equals(Factions.level(server).getAttached(FELLED));
	}

	/** (From {@link OgreHolds}) an Ogre King fell. True if he's the first. */
	static boolean fell(MinecraftServer server) {
		boolean first = !felled(server);
		Factions.level(server).setAttached(FELLED, true);
		return first;
	}

	/** A few lumps of bog iron in the mud and clay under a new swamp chunk's water. */
	private static void seed(ServerLevel level, LevelChunk chunk) {
		int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
		Holder<Biome> biome = level.getBiome(new BlockPos(x0 + 8, level.getSeaLevel(), z0 + 8));
		if (!biome.is(Biomes.SWAMP) && !biome.is(Biomes.MANGROVE_SWAMP)) {
			return;
		}
		RandomSource random = RandomSource.create(level.getSeed() ^ chunk.getPos().pack() * 0x5DEECE66DL);
		for (int t = 0; t < TRIES; t++) {
			if (random.nextFloat() >= CHANCE) {
				continue;
			}
			int x = x0 + random.nextInt(16), z = z0 + random.nextInt(16);
			int floor = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x & 15, z & 15);
			BlockPos at = new BlockPos(x, floor - random.nextInt(DEPTH), z);
			for (int n = 0; n < LUMP; n++) {
				BlockPos p = at.offset(random.nextInt(3) - 1, -random.nextInt(2), random.nextInt(3) - 1);
				if (p.getX() >> 4 == chunk.getPos().x() && p.getZ() >> 4 == chunk.getPos().z() && host(chunk.getBlockState(p))) {
					chunk.setBlockState(p, ORE.defaultBlockState(), 0);
				}
			}
		}
	}

	/** What bog iron may lie in: mud, clay, dirt, gravel. */
	private static boolean host(BlockState state) {
		return state.is(Blocks.MUD) || state.is(Blocks.CLAY) || state.is(Blocks.DIRT) || state.is(Blocks.GRAVEL) || state.is(Blocks.COARSE_DIRT)
			|| state.is(Blocks.MUDDY_MANGROVE_ROOTS);
	}
}
