package com.jaymoskingdoms.deep;

import com.jaymoskingdoms.ChasmTideborn;
import com.jaymoskingdoms.beasts.Beasts;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;

/**
 * The deep beasts' types and their rising (Andy, 2026-11-25, from Rim: giant sea beasts "on a schedule, and every attack is a
 * bit worse than the last"). On every {@link #EVERY}th dusk ({@link #RISING_AT}) the Abyssal Chasm rises: for each player in
 * it, beasts come up out of the dark water {@link #COME_MIN} to {@link #COME_MAX} blocks off. The {@link #tier} is the number
 * of risings so far, capped at {@link #MAX_TIER}: it is read off the day, so nothing is saved and every rising is a little
 * worse: more beasts ({@link #count}) and, from {@link DeepSpecies#fromTier}, bigger kinds. Nothing comes while no one is in
 * the chasm, and leaving it alone costs nothing: the beasts keep to their lairs ({@link DeepBeast}).
 */
public final class Deeps {
	// ---- tuning ----
	/** Days between risings, the dusk it happens at (ticks into the day), and the tier past which it gets no worse. */
	static final int EVERY = 3, MAX_TIER = 8;
	static final long RISING_AT = 12500L;
	/** The most beasts one rising brings round one player, and how far off they come up (blocks). */
	static final int MAX_BEASTS = 4, COME_MIN = 32, COME_MAX = 56, TRIES = 24;

	static final Map<EntityType<?>, DeepSpecies> KINDS = new HashMap<>();
	public static final Map<DeepSpecies, EntityType<DeepBeast>> TYPES = new EnumMap<>(DeepSpecies.class);

	private Deeps() {
	}

	/** Every kind's type, attributes and spawn egg. */
	public static void register() {
		for (DeepSpecies species : DeepSpecies.values()) {
			EntityType<DeepBeast> type = Beasts.type(species.id, EntityType.Builder.<DeepBeast>of(DeepBeast::new, MobCategory.MONSTER)
				.sized(species.width, species.height).clientTrackingRange(16), DeepBeast.createAttributes(species));
			TYPES.put(species, type);
			KINDS.put(type, species);
		}
	}

	/** The rising itself, which needs Alex's Caves' chasm. */
	public static void registerRising() {
		ServerTickEvents.END_SERVER_TICK.register(Deeps::tick);
	}

	static DeepSpecies speciesOf(EntityType<?> type) {
		DeepSpecies species = KINDS.get(type);
		return species == null ? DeepSpecies.LANCER : species;
	}

	/** How many risings have come by {@code day}, as bad as they get at {@link #MAX_TIER}. */
	static int tier(long day) {
		return (int) Math.min(MAX_TIER, day / EVERY);
	}

	/** How many beasts the rising of {@code tier} brings round a player. */
	static int count(int tier) {
		return Math.min(MAX_BEASTS, 1 + tier / 2);
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.getLevel(Level.OVERWORLD);
		if (level == null) {
			return;
		}
		long clock = level.getOverworldClockTime();
		long day = clock / 24000L;
		if (Math.floorMod(clock, 24000L) != RISING_AT || day % EVERY != EVERY - 1) {
			return;
		}
		int tier = tier(day + 1 - EVERY);
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || player.isCreative() || !level.getBiome(player.blockPosition()).is(ChasmTideborn.CHASM)) {
				continue;
			}
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.deep.rising"));
			List<DeepSpecies> kinds = new ArrayList<>();
			for (DeepSpecies species : DeepSpecies.values()) {
				if (species.fromTier <= tier) {
					kinds.add(species);
				}
			}
			for (int i = 0; i < count(tier); i++) {
				rise(level, player, kinds.get(random.nextInt(kinds.size())), random);
			}
		}
	}

	/** One beast comes up out of the dark water some way off. */
	static void rise(ServerLevel level, ServerPlayer player, DeepSpecies species, RandomSource random) {
		for (int i = 0; i < TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int distance = COME_MIN + random.nextInt(COME_MAX - COME_MIN + 1);
			BlockPos at = BlockPos.containing(player.getX() + Math.cos(angle) * distance, player.getY() + random.nextInt(9) - 6,
				player.getZ() + Math.sin(angle) * distance);
			if (!level.getFluidState(at).is(FluidTags.WATER) || !level.getBiome(at).is(ChasmTideborn.CHASM)) {
				continue;
			}
			DeepBeast beast = TYPES.get(species).create(level, EntitySpawnReason.EVENT);
			if (beast == null) {
				return;
			}
			beast.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, random.nextFloat() * 360.0F, 0.0F);
			if (!level.noCollision(beast)) {
				continue;
			}
			beast.setLair(at);
			level.addFreshEntity(beast);
			// it comes up already hungry; and if it has risen on a town's land the town's bell tolls
			com.jaymoskingdoms.AlarmBells.toll(level, beast);
			return;
		}
	}
}
