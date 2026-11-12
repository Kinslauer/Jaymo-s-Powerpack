package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Fleet;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.seas.Offshore;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

/**
 * Fishing smacks (Andy's ocean pick-list, "More ships, and ships that sail themselves": a fishing boat). A realm with a
 * harbour ({@link Navies}) sends out a smack now and then, crewed by its fishermen: out to a fishing ground well off the
 * coast (where the fish are now, {@code SeaLife}), a few minutes hove to there with her lines over the side (the splash
 * of the lines, a fish now and then), then home, where her catch goes into the realm's stores. Pirates take her if they
 * can; she runs from them as a merchantman does. A player can hail her as they hail a merchantman ({@link SeaTrade}) and
 * buy from her catch.
 */
final class Fishers {
	/** How often harbours are seen to (ticks). */
	static final int CHECK_TICKS = 400;
	/** One smack out from a harbour at a time, at most every this long (ticks: half a day); given up for lost after this long out. */
	static final long GAP = 12000L, FORGET = 24000L * 2;
	static final float CHANCE = 0.4F;
	/** Her grounds lie this far out from the harbour (blocks), and at least this far out to sea ({@link Offshore#far}). */
	static final int GROUNDS_LEAST = 96, GROUNDS_MOST = 200;
	static final float GROUNDS_FAR = 0.15F;
	/** How long she fishes there (ticks), how near counts as there (blocks), and her sail on the way. */
	static final long FISHING = 20L * 60L * 4L;
	static final double ARRIVE = 14.0;
	static final double SAIL = 0.6;
	/** A splash of her lines every this many thinks while she fishes. */
	static final int SPLASH_EVERY = 4;
	/** Her catch, landed into the realm's stores: cod and salmon (at least, at most), and what it earns the realm. */
	static final int COD_LEAST = 12, COD_MOST = 32, SALMON_LEAST = 4, SALMON_MOST = 16;
	static final long CATCH_PROFIT = 10L;
	/** Her crew besides her skipper. */
	static final int HANDS = 2;
	/** Ships aren't launched with a player this near their mooring. */
	static final double LAUNCH_HIDE = 28.0;

	/** A realm's smack: the one out (if any) and when she put out or was lost. */
	record Boat(Optional<UUID> ship, long at) {
		static final Codec<Boat> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.optionalFieldOf("ship").forGetter(Boat::ship),
				Codec.LONG.optionalFieldOf("at", -1L).forGetter(Boat::at))
			.apply(i, Boat::new));
	}

	/** Realm id → its smack, kept on the Overworld. */
	static final AttachmentType<Map<String, Boat>> BOATS = AttachmentRegistry.create(JaymosKingdoms.id("fishing_boats"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Boat.CODEC)));

	/** Not saved: each smack's fishing done at (game time); one fishing when the server started fishes a fresh spell. */
	private static final Map<UUID, Long> UNTIL = new HashMap<>();

	private Fishers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Fishers::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> UNTIL.clear());
	}

	private static @Nullable Boat boat(MinecraftServer server, int realm) {
		return Factions.level(server).getAttachedOrElse(BOATS, Map.of()).get(Integer.toString(realm));
	}

	private static void put(MinecraftServer server, int realm, @Nullable Boat boat) {
		ServerLevel level = Factions.level(server);
		Map<String, Boat> all = new HashMap<>(level.getAttachedOrElse(BOATS, Map.of()));
		if (boat == null) {
			all.remove(Integer.toString(realm));
		} else {
			all.put(Integer.toString(realm), boat);
		}
		level.setAttached(BOATS, Map.copyOf(all));
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		if (now % CHECK_TICKS != 7) {
			return;
		}
		for (Faction f : Factions.all(server)) {
			if (!f.type().isRealm() || f.fallen()) {
				continue;
			}
			Navies.Harbour h = Navies.harbour(server, f.id());
			if (h == null || !level.isPositionEntityTicking(h.mooring())) {
				continue;
			}
			Boat b = boat(server, f.id());
			if (b != null && b.ship().isPresent()) {
				if (now - b.at() < FORGET) {
					continue;
				}
				put(server, f.id(), b = new Boat(Optional.empty(), now));
			}
			if (b != null && b.at() >= 0 && now - b.at() < GAP || level.getRandom().nextFloat() > CHANCE
				|| Raids.playerNear(level, h.mooring(), LAUNCH_HIDE)) {
				continue;
			}
			launch(level, f, h);
		}
	}

	/** A smack put out from {@code f}'s harbour for a fishing ground off it. */
	static @Nullable Ship launch(ServerLevel level, Faction f, Navies.Harbour h) {
		RandomSource random = level.getRandom();
		BlockPos ground = grounds(level, h, random);
		Crew crew = new Crew(Crew.Side.FISHER, f.id(), Pirates.merchantShip(random), h.mooring());
		crew.bound = ground;
		crew.mode = "outward";
		Fleet.Look look = new Fleet.Look(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_FENCE, Blocks.SPRUCE_LOG, Fleet.block(f.color().getName() + "_wool"), null);
		Ship ship = Navies.launch(level, f, h, Fleet.Rig.SMACK, look, crew,
			new Corsairs.Complement(Pirates.SEA_CAPTAIN, Folk.GUARD, Folk.GUARD, HANDS, 0));
		if (ship != null) {
			put(level.getServer(), f.id(), new Boat(Optional.of(ship.getUUID()), level.getGameTime()));
			JaymosKingdoms.LOGGER.info("{} puts out from {} for the fishing grounds at {}", crew.name, f.name(), ground);
		}
		return ship;
	}

	/** A fishing ground off the harbour: open sea well out (by the land's lie, nothing loaded), or straight out if none is found. */
	private static BlockPos grounds(ServerLevel level, Navies.Harbour h, RandomSource random) {
		int y = level.getSeaLevel() - 1;
		double out = Math.atan2(h.mooring().getZ() - h.shore().getZ(), h.mooring().getX() - h.shore().getX());
		for (int t = 0; t < 12; t++) {
			double angle = out + (random.nextDouble() - 0.5) * Math.PI;
			double r = GROUNDS_LEAST + random.nextDouble() * (GROUNDS_MOST - GROUNDS_LEAST);
			int x = h.mooring().getX() + Mth.floor(Math.cos(angle) * r), z = h.mooring().getZ() + Mth.floor(Math.sin(angle) * r);
			if (Offshore.far(level, x, z) >= GROUNDS_FAR) {
				return new BlockPos(x, y, z);
			}
		}
		return new BlockPos(h.mooring().getX() + Mth.floor(Math.cos(out) * GROUNDS_LEAST), y,
			h.mooring().getZ() + Mth.floor(Math.sin(out) * GROUNDS_LEAST));
	}

	/**
	 * Her skipper's helm ({@link Corsairs}): {heading, sail}, or null when she's done (landed her catch). Outward to her
	 * grounds, hove to fishing there a while, then homeward.
	 */
	static float @Nullable [] think(ServerLevel level, Ship ship, Crew crew) {
		long now = level.getGameTime();
		if ("fishing".equals(crew.mode)) {
			long until = UNTIL.computeIfAbsent(ship.getUUID(), k -> now + FISHING);
			if (now >= until) {
				UNTIL.remove(ship.getUUID());
				crew.mode = "homeward";
				crew.bound = crew.home;
			} else {
				lines(level, ship);
				return new float[] {ship.getYRot(), 0.0F};
			}
		}
		if (crew.bound == null) {
			crew.mode = "homeward";
			crew.bound = crew.home;
		}
		double d = Math.sqrt(Raids.horizontal(ship.blockPosition(), crew.bound));
		if (d < ARRIVE) {
			if ("homeward".equals(crew.mode)) {
				landed(level, ship, crew);
				return null;
			}
			crew.mode = "fishing";
			UNTIL.put(ship.getUUID(), now + FISHING);
			return new float[] {ship.getYRot(), 0.0F};
		}
		if (!"homeward".equals(crew.mode)) {
			crew.mode = "outward";
		}
		crew.goal = crew.bound;
		return new float[] {Fleet.bearing(ship, crew.bound.getX() + 0.5, crew.bound.getZ() + 0.5), (float)(d < 30.0 ? SAIL * 0.5 : SAIL)};
	}

	/** Her lines over the side: a splash now and then, and now and then a fish. */
	private static void lines(ServerLevel level, Ship ship) {
		RandomSource random = level.getRandom();
		if (random.nextInt(SPLASH_EVERY) != 0) {
			return;
		}
		double side = random.nextBoolean() ? 3.5 : -3.5;
		double yaw = ship.getYRot() * Mth.DEG_TO_RAD;
		double x = ship.getX() + Math.cos(yaw) * side + (random.nextDouble() - 0.5) * 6.0;
		double z = ship.getZ() + Math.sin(yaw) * side + (random.nextDouble() - 0.5) * 6.0;
		double y = level.getSeaLevel();
		level.playSound(null, x, y, z, SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.NEUTRAL, 0.5F, 0.9F + random.nextFloat() * 0.2F);
		level.sendParticles(ParticleTypes.FISHING, x, y, z, 6, 0.3, 0.0, 0.3, 0.05);
		if (random.nextInt(3) == 0) {
			level.sendParticles(ParticleTypes.SPLASH, x, y, z, 10, 0.2, 0.1, 0.2, 0.1);
		}
	}

	/** She's home: her catch into the realm's stores, a little for its treasury, and she's warped in (gone). */
	private static void landed(ServerLevel level, Ship ship, Crew crew) {
		MinecraftServer server = level.getServer();
		Faction f = Factions.get(server, crew.faction);
		RandomSource random = level.getRandom();
		if (f != null) {
			if (Storage.loaded(level, f)) {
				Storage.put(level, f, Items.COD, Mth.nextInt(random, COD_LEAST, COD_MOST));
				Storage.put(level, f, Items.SALMON, Mth.nextInt(random, SALMON_LEAST, SALMON_MOST));
			}
			Factions.update(server, f.id(), x -> x.withTreasury(x.treasury() + CATCH_PROFIT));
			Boat b = boat(server, f.id());
			if (b != null && b.ship().map(ship.getUUID()::equals).orElse(false)) {
				put(server, f.id(), new Boat(Optional.empty(), level.getGameTime()));
			}
		}
		UNTIL.remove(ship.getUUID());
		Navies.stow(ship);
	}

	/** She went down: the harbour sends another in a while, and her sinker is hated for it. */
	static void lost(ServerLevel level, Crew crew, @Nullable ServerPlayer player) {
		MinecraftServer server = level.getServer();
		Faction f = Factions.get(server, crew.faction);
		if (f == null) {
			return;
		}
		put(server, f.id(), new Boat(Optional.empty(), level.getGameTime()));
		if (player != null) {
			Reputation.change(player, f, Navies.REP_SINK / 2);
		}
	}
}
