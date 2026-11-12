package com.jaymospowers.client.sound;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Spot sounds heard from a direction (Andy's four-mods pick fx_spots, once ImmersiveFX's and now ours, as it was
 * removed). Out in the Overworld, now and then, the land around you makes a sound from where it really is: a
 * woodpecker drumming on a trunk in the woods by day, frogs calling from the water of a swamp or river at night, and a
 * village you can't yet see murmuring from the direction it lies. Each comes from a real place near you (a log, water,
 * the villagers themselves), so you can turn your head and walk to it. Client only; nothing is sent.
 */
public final class SpotSounds {
	/** How often one may sound (ticks, the least and the spread), and how far each looks for its place (blocks). */
	static final int GAP = 160, GAP_SPREAD = 240;
	static final int LOG_REACH = 24, WATER_REACH = 20;
	/** A village is heard from this far, but not once you're nearly among its people. */
	static final double VILLAGE_FAR = 72.0, VILLAGE_NEAR = 18.0;
	/** Tries to find a place before giving up this time. */
	static final int TRIES = 24;

	static final SoundEvent WOODPECKER = SoundEvent.createVariableRangeEvent(JaymosPowers.id("spot.woodpecker"));

	private static int wait = GAP;

	private SpotSounds() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(SpotSounds::tick);
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		Player player = mc.player;
		if (level == null || player == null || mc.isPaused() || level.dimension() != Level.OVERWORLD) {
			return;
		}
		if (--wait > 0) {
			return;
		}
		RandomSource random = level.getRandom();
		wait = GAP + random.nextInt(GAP_SPREAD);
		BlockPos at = player.blockPosition();
		// only out under the open sky: underground and indoors the land is not heard
		if (at.getY() < level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) - 3) {
			return;
		}
		Holder<Biome> biome = level.getBiome(at);
		boolean day = level.isBrightOutside();
		if (village(level, player, random)) {
			return;
		}
		if (day && !level.isRaining() && (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE))) {
			woodpecker(level, at, random);
		} else if (!day && (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) || biome.is(BiomeTags.IS_RIVER))) {
			frogs(level, at, random);
		}
	}

	/** A woodpecker on a trunk near: a log with air on one side, a few blocks up. */
	private static void woodpecker(ClientLevel level, BlockPos at, RandomSource random) {
		for (int i = 0; i < TRIES; i++) {
			int x = at.getX() + random.nextInt(LOG_REACH * 2 + 1) - LOG_REACH;
			int z = at.getZ() + random.nextInt(LOG_REACH * 2 + 1) - LOG_REACH;
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos trunk = new BlockPos(x, top - 1 - random.nextInt(3), z);
			if (level.getBlockState(trunk).is(BlockTags.LOGS) && level.getBlockState(trunk.below()).is(BlockTags.LOGS)) {
				level.playLocalSound(trunk.getX() + 0.5, trunk.getY() + 0.5, trunk.getZ() + 0.5, WOODPECKER, SoundSource.AMBIENT,
					0.7F + random.nextFloat() * 0.3F, 0.9F + random.nextFloat() * 0.2F, true);
				return;
			}
		}
	}

	/** Frogs calling from water near: a few voices at the edge, one after another. */
	private static void frogs(ClientLevel level, BlockPos at, RandomSource random) {
		for (int i = 0; i < TRIES; i++) {
			int x = at.getX() + random.nextInt(WATER_REACH * 2 + 1) - WATER_REACH;
			int z = at.getZ() + random.nextInt(WATER_REACH * 2 + 1) - WATER_REACH;
			int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
			BlockPos water = new BlockPos(x, top - 1, z);
			if (level.getFluidState(water).isSource() && level.getFluidState(water).is(net.minecraft.tags.FluidTags.WATER)) {
				int voices = 1 + random.nextInt(3);
				for (int v = 0; v < voices; v++) {
					level.playLocalSound(water.getX() + random.nextInt(5) - 2 + 0.5, water.getY() + 1.0, water.getZ() + random.nextInt(5) - 2 + 0.5,
						SoundEvents.FROG_AMBIENT, SoundSource.AMBIENT, 0.6F, 0.85F + random.nextFloat() * 0.3F, true);
				}
				return;
			}
		}
	}

	/** A village out of sight: its folk heard faintly from where they stand together. */
	private static boolean village(ClientLevel level, Player player, RandomSource random) {
		var folk = level.getEntitiesOfClass(Villager.class, new AABB(player.blockPosition()).inflate(VILLAGE_FAR), v -> v.isAlive() && !v.isBaby());
		if (folk.size() < 3) {
			return false;
		}
		Vec3 sum = Vec3.ZERO;
		for (Villager v : folk) {
			if (v.distanceToSqr(player) < VILLAGE_NEAR * VILLAGE_NEAR) {
				return false;
			}
			sum = sum.add(v.position());
		}
		Vec3 heart = sum.scale(1.0 / folk.size());
		if (random.nextFloat() >= 0.5F) {
			return false;
		}
		Villager one = folk.get(random.nextInt(folk.size()));
		level.playLocalSound(one.getX(), one.getEyeY(), one.getZ(), SoundEvents.VILLAGER_AMBIENT, SoundSource.AMBIENT, 1.2F,
			0.9F + random.nextFloat() * 0.2F, true);
		if (random.nextFloat() < 0.25F) {
			level.playLocalSound(heart.x, heart.y, heart.z, SoundEvents.WOOD_HIT, SoundSource.AMBIENT, 1.0F, 0.8F, true);
		}
		return true;
	}
}
