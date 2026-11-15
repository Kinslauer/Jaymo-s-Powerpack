package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Fleet;
import com.jaymoskingdoms.sailing.Hull;
import com.jaymoskingdoms.sailing.Sailing;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.sailing.Ships;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Realm air patrols (the medieval picks' vp-patrols; Lore: "Sky pirates"): a rich realm keeps an airship of its own, lent by
 * the nearest steam town for a fee out of its treasury (with no steam town standing, its own carpenters build her, dearer),
 * that circles its lands, spots war parties, raiders and bandits, and calls
 * the alarm. Andy: "little to no chat indicators, fireworks and horns should be heard": nothing is said in chat. Over a war
 * band she fires a rocket out of her hold (real rockets, in the realm's colours) and blows her horn, heard far over the
 * realm's land, and the seat's bell rings and its people run in ({@link WarAlert#sighted}). She engages any sky pirate's
 * airship in reach with her gun. Short of rockets, powder or shot, she comes in over the seat and the realm buys her more out
 * of its treasury. She is the realm's navy in the air ({@link Crew.Side#NAVY}), crewed by {@link Corsairs}; kill
 * her captain and she drifts down ({@link Ship}); lost, the realm pays the steam towns for another in time.
 */
final class AirPatrols {
	// ---- tuning ----
	/**
	 * A realm keeps a patrol while its treasury holds this much; another costs this (paid to the steam town that lends her), or
	 * this built by its own carpenters when no steam town stands.
	 */
	static final long RICH = Wealth.WEALTHY, FEE = 300L, OWN_BUILD = 600L;
	/** Her stores bought from the treasury when she comes in: each rocket, powder and ball costs this. */
	static final long ROCKET_COST = 3L, POWDER_COST = 2L, SHOT_COST = 2L;
	/** She comes in for stores when down to this many rockets, powder or balls; she's in over the seat this near it. */
	static final int LOW = 2;
	static final double IN_OVER = 12.0;
	/** How often patrols are seen to (ticks); she's put out only with a player this near the seat and none this near where she rises. */
	static final int UPKEEP_EVERY = 600;
	static final double PLAYER_NEAR = 192.0, LAUNCH_HIDE = 48.0;
	/** Lost, the steam towns lend another after this long (ticks: a day); she's missing after this long unseen where she was. */
	static final long RELEND_TICKS = 24000L, MISSING_TICKS = 20 * 180;
	/** She rounds the seat this far out, this high over it; her cloth on patrol and shadowing a band. */
	static final double ROUND = 72.0, ROUND_UP = 34.0, PATROL_CLOTH = 0.45, SHADOW_CLOTH = 0.6;
	/** Her lookout's reach; a band is this many hostiles at least, within this of the first; she minds bands this near the seat. */
	static final double SPOT = 72.0, BAND_REACH = 16.0, REALM_REACH = 200.0;
	static final int BAND_LEAST = 2;
	/** Over a band: she circles it this wide and high; fires a rocket at most every this many ticks; blows her horn and rings the alarm every this many. */
	static final double SHADOW_RADIUS = 14.0, SHADOW_UP = 24.0, OVER = 20.0;
	static final long ROCKET_GAP = 20 * 8, HORN_GAP = 20 * 30;
	/** A band unseen this long is let go. */
	static final long LOSE_TICKS = 20 * 20;
	/** Her horn carries this far (blocks), and the mobs about hear it this far. */
	static final double HORN_REACH = 224.0, HORN_HEARD = 64.0;
	/** Her captain thinks every this many ticks. */
	static final int THINK = 20;
	/** What she carries: rockets, powder, shot. */
	static final int ROCKETS = 12, POWDER = 10, SHOT = 10;
	/** Her crew besides her gunner. */
	static final int HANDS = 1;
	/** She fights a sky pirate's airship this near. */
	static final double ENGAGE = 64.0;

	/** A realm's patrol: her airship, where she was last seen, when she was lost. */
	record Patrol(Optional<UUID> ship, Optional<BlockPos> seen, long lostAt) {
		static final Codec<Patrol> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.optionalFieldOf("ship").forGetter(Patrol::ship),
				BlockPos.CODEC.optionalFieldOf("seen").forGetter(Patrol::seen),
				Codec.LONG.optionalFieldOf("lost_at", -1L).forGetter(Patrol::lostAt))
			.apply(i, Patrol::new));
	}

	/** Every realm's patrol (realm id → it), kept on the Overworld. */
	static final AttachmentType<Map<String, Patrol>> PATROLS = AttachmentRegistry.create(JaymosKingdoms.id("air_patrols"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Patrol.CODEC)));

	/** What her captain is about. */
	private static final class Watch {
		@Nullable Vec3 band;
		long seen, lastRocket = Long.MIN_VALUE / 2, lastHorn = Long.MIN_VALUE / 2;
		/** She's coming in over the seat for stores. */
		boolean coming;
	}

	private static final Map<UUID, Watch> WATCHES = new HashMap<>();
	private static final Map<Integer, Long> MISSING = new HashMap<>();
	/** Old patrols come back after their realm took on another: put away next tick. */
	private static final java.util.Set<UUID> STOW = new java.util.HashSet<>();

	private AirPatrols() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("air patrol attachment: {}", PATROLS);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerLevel level = Factions.level(server);
			if (level.getGameTime() % UPKEEP_EVERY == 7) {
				upkeep(level);
			}
			if (!STOW.isEmpty()) {
				for (UUID id : List.copyOf(STOW)) {
					if (level.getEntity(id) instanceof Ship ship) {
						Navies.stow(ship);
					}
				}
				STOW.clear();
			}
		});
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Ship ship && ship.crew != null && SkyShips.PATROL.equals(ship.crew.flag)) {
				Patrol p = patrol(level.getServer(), ship.crew.faction);
				if (p == null || p.ship().isEmpty()) {
					// (given up while she lay unloaded: she's the realm's again, and no other is taken on)
					put(level.getServer(), ship.crew.faction, new Patrol(Optional.of(ship.getUUID()), Optional.of(ship.blockPosition()), -1L));
				} else if (!p.ship().get().equals(ship.getUUID())) {
					// (an old patrol of a realm that has taken on another since: she's put away)
					STOW.add(ship.getUUID());
				}
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (!(entity instanceof Ship ship)) {
				return;
			}
			WATCHES.remove(ship.getUUID());
			if (ship.crew == null || !SkyShips.PATROL.equals(ship.crew.flag)) {
				return;
			}
			Patrol p = patrol(level.getServer(), ship.crew.faction);
			if (p != null && p.ship().isPresent() && p.ship().get().equals(ship.getUUID())) {
				// destroyed (moored, broken up): she's lost; unloaded with her chunk: where she lies is known exactly
				boolean gone = ship.getRemovalReason() != null && ship.getRemovalReason().shouldDestroy();
				put(level.getServer(), ship.crew.faction, gone ? new Patrol(Optional.empty(), Optional.empty(), level.getGameTime())
					: new Patrol(p.ship(), Optional.of(ship.blockPosition()), -1L));
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WATCHES.clear();
			MISSING.clear();
			STOW.clear();
		});
	}

	private static @Nullable Patrol patrol(MinecraftServer server, int realm) {
		return Factions.level(server).getAttachedOrElse(PATROLS, Map.of()).get(Integer.toString(realm));
	}

	private static void put(MinecraftServer server, int realm, Patrol p) {
		ServerLevel level = Factions.level(server);
		Map<String, Patrol> all = new HashMap<>(level.getAttachedOrElse(PATROLS, Map.of()));
		all.put(Integer.toString(realm), p);
		level.setAttached(PATROLS, Map.copyOf(all));
	}

	// ---- keeping one -----------------------------------------------------------------------------------------------

	/** Every rich realm with a player about keeps track of its patrol, and takes on another when it has none (lent, or its own built). */
	private static void upkeep(ServerLevel level) {
		MinecraftServer server = level.getServer();
		List<SteamTowns.Town> towns = SteamTowns.all(level);
		long now = level.getGameTime();
		for (Faction realm : Factions.all(server)) {
			if (!realm.type().isRealm() || realm.fallen() || !level.isPositionEntityTicking(realm.seat())) {
				continue;
			}
			Patrol p = patrol(server, realm.id());
			if (p != null && p.ship().isPresent()) {
				if (level.getEntity(p.ship().get()) instanceof Ship s && !s.isRemoved()) {
					MISSING.remove(realm.id());
					put(server, realm.id(), new Patrol(p.ship(), Optional.of(s.blockPosition()), -1L));
					continue;
				}
				BlockPos seen = p.seen().orElse(realm.seat());
				if (!level.isPositionEntityTicking(seen) || MISSING.merge(realm.id(), (long)UPKEEP_EVERY, Long::sum) < MISSING_TICKS) {
					continue;
				}
				MISSING.remove(realm.id());
				put(server, realm.id(), p = new Patrol(Optional.empty(), Optional.empty(), now));
			}
			long cost = towns.isEmpty() ? OWN_BUILD : FEE;
			if (realm.treasury() < RICH + cost || p != null && p.lostAt() >= 0 && now - p.lostAt() < RELEND_TICKS
				|| !Raids.playerNear(level, realm.seat(), PLAYER_NEAR)) {
				continue;
			}
			// (the steam town nearest lends her, and is paid for her; with none standing, the realm's own carpenters build her)
			SteamTowns.Town lender = null;
			double best = Double.MAX_VALUE;
			for (SteamTowns.Town t : towns) {
				Faction tf = Factions.get(server, t.id());
				double d = tf == null ? Double.MAX_VALUE : Raids.horizontal(tf.seat(), realm.seat());
				if (d < best) {
					best = d;
					lender = t;
				}
			}
			if (launch(level, realm) != null) {
				Factions.update(server, realm.id(), f -> f.withTreasury(Math.max(0L, f.treasury() - cost)));
				if (lender != null && lender.id() != realm.id()) {
					Factions.update(server, lender.id(), f -> f.withTreasury(f.treasury() + cost));
				}
			}
		}
	}

	/** {@code realm}'s patrol airship put out high over its land, out of every player's sight; null if there was no room for her. */
	private static @Nullable Ship launch(ServerLevel level, Faction realm) {
		MinecraftServer server = level.getServer();
		RandomSource random = level.getRandom();
		DyeColor colour = realm.color();
		Crew crew = new Crew(Crew.Side.NAVY, realm.id(), Pirates.navyShip(random), realm.seat());
		crew.flag = SkyShips.PATROL;
		List<SkyShips.Placed> design = SkyShips.design(Blocks.SPRUCE_PLANKS.defaultBlockState(), Blocks.SPRUCE_FENCE.defaultBlockState(),
			Blocks.WOOL.pick(colour).defaultBlockState(), Blocks.WOOL.pick(colour == DyeColor.WHITE ? DyeColor.LIGHT_GRAY : DyeColor.WHITE).defaultBlockState(), 1);
		List<ItemStack> stores = List.of(rockets(colour, ROCKETS), new ItemStack(Items.GUNPOWDER, POWDER), new ItemStack(Sailing.ROUND_SHOT, SHOT));
		for (int tries = 0; tries < 6; tries++) {
			double a = random.nextDouble() * Math.PI * 2.0;
			int x = realm.seat().getX() + (int)Math.round(Math.cos(a) * ROUND), z = realm.seat().getZ() + (int)Math.round(Math.sin(a) * ROUND);
			BlockPos column = new BlockPos(x, 0, z);
			if (!level.isPositionEntityTicking(column)) {
				continue;
			}
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
			BlockPos middle = new BlockPos(x, Math.max(ground + SkyShips.CLEAR + 2, realm.seat().getY() + (int)ROUND_UP), z);
			if (Raids.playerNear(level, middle, LAUNCH_HIDE)) {
				continue;
			}
			Direction bow = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			Ship ship = SkyShips.launch(level, design, middle, bow, crew, realm,
				new Corsairs.Complement(Pirates.SEA_CAPTAIN, Folk.GUARD, Folk.GUARD, HANDS, 0), stores);
			if (ship != null) {
				put(server, realm.id(), new Patrol(Optional.of(ship.getUUID()), Optional.of(ship.blockPosition()), -1L));
				JaymosKingdoms.LOGGER.info("{} took on a patrol airship, {}", realm.name(), crew.name);
				return ship;
			}
		}
		return null;
	}

	/** {@code n} signal rockets in {@code colour}: a large ball, white fading, high. */
	private static ItemStack rockets(DyeColor colour, int n) {
		FireworkExplosion burst = new FireworkExplosion(FireworkExplosion.Shape.LARGE_BALL, IntList.of(colour.getFireworkColor()),
			IntList.of(DyeColor.WHITE.getFireworkColor()), true, false);
		ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET, n);
		rocket.set(DataComponents.FIREWORKS, new Fireworks(2, List.of(burst)));
		return rocket;
	}

	/** {@code crew}'s patrol went down (sunk by {@code player}, or anyone): the steam towns lend another in a while. */
	static void lost(ServerLevel level, Crew crew, @Nullable Ship ship, @Nullable ServerPlayer player) {
		if (ship != null) {
			WATCHES.remove(ship.getUUID());
		}
		put(level.getServer(), crew.faction, new Patrol(Optional.empty(), Optional.empty(), level.getGameTime()));
		Faction f = Factions.get(level.getServer(), crew.faction);
		if (player != null && f != null) {
			Reputation.change(player, f, Navies.REP_SINK);
		}
	}

	// ---- her captain -----------------------------------------------------------------------------------------------

	/** Her helm this tick, from {@link Corsairs#helm} for a patrol airship in the air with her captain aboard. */
	static Input helm(ServerLevel level, Ship ship, Crew crew) {
		long now = level.getGameTime();
		Watch w = WATCHES.computeIfAbsent(ship.getUUID(), k -> new Watch());
		Faction realm = Factions.get(level.getServer(), crew.faction);
		if ((now + ship.getId()) % THINK == 0) {
			think(level, ship, crew, realm, w, now);
		}
		Vec3 seat = Vec3.atCenterOf(realm != null ? realm.seat() : crew.home);
		if (w.coming && w.band == null) {
			// in over the seat for stores
			double d = SkyShips.flat(ship, seat);
			if (d < IN_OVER) {
				restock(level, ship, realm);
				w.coming = false;
			}
			return SkyShips.fly(level, ship, Fleet.bearing(ship, seat.x, seat.z), d < IN_OVER * 2 ? 0.15 : PATROL_CLOTH, seat.y + ROUND_UP);
		}
		if (w.band != null) {
			if (SkyShips.flat(ship, w.band) < OVER) {
				signal(level, ship, crew, realm, w, now);
			}
			return SkyShips.fly(level, ship, SkyShips.circling(ship, w.band, SHADOW_RADIUS), SHADOW_CLOTH, w.band.y + SHADOW_UP);
		}
		Ship foe = crew.target >= 0 && level.getEntity(crew.target) instanceof Ship s && !s.isRemoved() ? s : null;
		if (foe != null) {
			return SkyShips.fly(level, ship, SkyShips.circling(ship, foe.position(), 20.0), SHADOW_CLOTH, foe.getY());
		}
		return SkyShips.fly(level, ship, SkyShips.circling(ship, seat, ROUND), PATROL_CLOTH, seat.y + ROUND_UP);
	}

	/** Her lookout: a war band on or near the realm's land, and any sky pirate in reach. */
	private static void think(ServerLevel level, Ship ship, Crew crew, @Nullable Faction realm, Watch w, long now) {
		crew.shore = null;
		crew.mode = w.band != null ? "shadow" : "patrol";
		if (realm == null || realm.fallen()) {
			w.band = null;
			return;
		}
		MinecraftServer server = level.getServer();
		List<LivingEntity> hostile = level.getEntitiesOfClass(LivingEntity.class, ship.getBoundingBox().inflate(SPOT, 128.0, SPOT),
			e -> e.isAlive() && !(e.getVehicle() instanceof Ship) && hostile(server, realm, e)
				&& Raids.horizontal(e.blockPosition(), realm.seat()) < REALM_REACH * REALM_REACH);
		Vec3 band = null;
		for (LivingEntity first : hostile) {
			List<LivingEntity> with = new ArrayList<>();
			for (LivingEntity e : hostile) {
				if (e.distanceToSqr(first) < BAND_REACH * BAND_REACH) {
					with.add(e);
				}
			}
			if (with.size() >= BAND_LEAST) {
				double x = 0, y = 0, z = 0;
				for (LivingEntity e : with) {
					x += e.getX();
					y += e.getY();
					z += e.getZ();
				}
				band = new Vec3(x / with.size(), y / with.size(), z / with.size());
				break;
			}
		}
		if (band != null) {
			w.band = band;
			w.seen = now;
		} else if (w.band != null && now - w.seen > LOSE_TICKS) {
			w.band = null;
		}
		// a sky pirate in reach
		Ship pirate = null;
		for (Ship s : Ships.in(level)) {
			if (s != ship && !s.isRemoved() && !s.foundering() && s.crew != null && SkyShips.PIRATE.equals(s.crew.flag) && s.distanceTo(ship) < ENGAGE) {
				pirate = s;
				break;
			}
		}
		crew.target = pirate != null ? pirate.getId() : -1;
		if (ship.inHold(s -> s.is(Items.FIREWORK_ROCKET)) <= LOW || ship.inHold(s -> s.is(Items.GUNPOWDER)) <= LOW
			|| ship.inHold(com.jaymoskingdoms.sailing.ShipCannonBlock::isShot) <= LOW) {
			w.coming = true;
		}
	}

	/** In over the seat: her rockets, powder and shot made up out of {@code realm}'s treasury, as much as it can pay for. */
	private static void restock(ServerLevel level, Ship ship, @Nullable Faction realm) {
		if (realm == null || realm.fallen()) {
			return;
		}
		buy(level, ship, realm, rockets(realm.color(), 1), ROCKETS - ship.inHold(s -> s.is(Items.FIREWORK_ROCKET)), ROCKET_COST);
		buy(level, ship, realm, new ItemStack(Items.GUNPOWDER), POWDER - ship.inHold(s -> s.is(Items.GUNPOWDER)), POWDER_COST);
		buy(level, ship, realm, new ItemStack(Sailing.ROUND_SHOT), SHOT - ship.inHold(com.jaymoskingdoms.sailing.ShipCannonBlock::isShot), SHOT_COST);
	}

	private static void buy(ServerLevel level, Ship ship, Faction realm, ItemStack one, int want, long each) {
		Faction now = Factions.get(level.getServer(), realm.id());
		if (now == null || want <= 0) {
			return;
		}
		int n = (int)Math.min(want, now.treasury() / each);
		if (n <= 0) {
			return;
		}
		ItemStack left = ship.intoHold(one.copyWithCount(n));
		long paid = (n - left.getCount()) * each;
		Factions.update(level.getServer(), realm.id(), f -> f.withTreasury(Math.max(0L, f.treasury() - paid)));
	}

	/** A raider, an outlaw or a bandit, or a soldier of a realm at war with {@code realm}. */
	private static boolean hostile(MinecraftServer server, Faction realm, LivingEntity e) {
		if (Raids.isRaider(e) || Outlaws.is(e)) {
			return true;
		}
		return e instanceof Worker w && w.factionId() != realm.id() && WarParties.isSoldier(w) && Wars.enemies(server, realm.id(), w.factionId());
	}

	/** Over the band: a rocket out of her hold, and her horn blown and the seat's alarm rung, now and then. */
	private static void signal(ServerLevel level, Ship ship, Crew crew, @Nullable Faction realm, Watch w, long now) {
		Hull hull = ship.hull();
		if (hull == null) {
			return;
		}
		Vec3 side = ship.toWorld(hull.minX - 1.5, 1.0, (hull.minZ + hull.maxZ) * 0.5);
		if (now - w.lastRocket >= ROCKET_GAP) {
			ItemStack rocket = ship.fromHold(s -> s.is(Items.FIREWORK_ROCKET), 1);
			if (!rocket.isEmpty()) {
				w.lastRocket = now;
				level.addFreshEntity(new FireworkRocketEntity(level, side.x, side.y, side.z, rocket));
			}
		}
		if (now - w.lastHorn >= HORN_GAP) {
			w.lastHorn = now;
			var horn = SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(level.getRandom().nextInt(SoundEvents.GOAT_HORN_SOUND_VARIANTS.size()));
			level.playSound(null, side.x, side.y, side.z, horn, SoundSource.NEUTRAL, (float)(HORN_REACH / 16.0), 1.0F);
			Hearing.noise(level, side, HORN_HEARD, null, Hearing.Kind.ACTION);
			if (realm != null && w.band != null) {
				WarAlert.sighted(level, realm, BlockPos.containing(w.band), now);
			}
		}
	}
}
