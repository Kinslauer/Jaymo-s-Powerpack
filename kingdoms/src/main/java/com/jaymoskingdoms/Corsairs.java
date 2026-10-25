package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Crew;
import com.jaymoskingdoms.sailing.Deck;
import com.jaymoskingdoms.sailing.Fleet;
import com.jaymoskingdoms.sailing.Hull;
import com.jaymoskingdoms.sailing.Sailing;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.sailing.Ships;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.42) Sailing, stage 3: the captains of the ships that sail on their own ({@link Crew}). Each one keeps a lookout,
 * and by his side:
 *
 * <ul>
 * <li>a <b>pirate</b> patrols the coast round his cove, runs down any ship players sail, any merchantman and any
 * warship he sights, comes about to bring his guns to bear and fires broadsides as she crosses them; he sails to the
 * raid when his cove sends a landing party ashore ({@link PirateCoves});</li>
 * <li>a kingdom's <b>warship</b> patrols off its harbour and engages pirates near it, and players its realm counts as
 * enemies ({@link Navies});</li>
 * <li>a <b>merchantman</b> sails for another realm's harbour and runs from pirates.</li>
 * </ul>
 *
 * <p>Her gunners lay their own guns, each on the ship they're fighting, leading her as she sails; they load again
 * while they live (faster with a powder monkey aboard). Kill her captain and she drifts; kill her gunners too and her
 * guns go quiet, and her hold is yours: each player's own share of it, as Lootr shares a chest. A ship that founders
 * takes her crew down with her.
 */
final class Corsairs implements Ship.Captaincy {
	/** How far each side sights a foe (blocks). */
	static final double PIRATE_SIGHT = 80.0, NAVY_SIGHT = 96.0, MERCHANT_SIGHT = 48.0;
	/** Patrolling: how far from home, and how near a waypoint counts as reached; how far a chase may draw her from home. */
	static final double PATROL = 96.0, NAVY_PATROL = 72.0, WAYPOINT_REACH = 12.0, PIRATE_LEASH = 240.0, NAVY_LEASH = 180.0;
	/** She comes about to fight inside this, and sheers off when closer than this (blocks). */
	static final double ENGAGE = 30.0, TOO_CLOSE = 12.0;
	/** Sail set: patrolling, chasing, fighting, running. */
	static final double PATROL_SAIL = 0.5, CHASE_SAIL = 1.0, FIGHT_SAIL = 0.6, RUN_SAIL = 1.0;
	/** Under oars when her sails give less than this (blocks a tick: she's head to wind). */
	static final double ROW_BELOW = 0.12;
	/** Her guns: their reach, the least range, how far off their line they're laid (a cosine), and how long a reload takes (ticks). */
	static final double GUN_RANGE = 52.0, GUN_NEAREST = 5.0, GUN_ARC = 0.86;
	static final int RELOAD = 160, RELOAD_MONKEY = 100, GUN_GAP = 4;
	/** How fast round shot flies and falls (as {@code ShipShot}), and how much they miss by (degrees). */
	static final double SHOT_SPEED = 1.6, SHOT_FALL = 0.015;
	static final float AIM_SPREAD = 1.2F, LOWEST = -6.0F, HIGHEST = 18.0F;
	/** How often (ticks) her captain looks about; how many looks she may lie stuck before she backs off; how long she backs. */
	static final int THINK = 10, STUCK_LOOKS = 12, BACK_LOOKS = 4;
	/** A merchantman's done when she's this near her harbour. */
	static final double ARRIVE = 22.0;
	/** Plundering a realm's ship: reputation lost with it. */
	static final int REP_PLUNDER = -60;
	/** A hold's share: how many slots. */
	static final int SHARE = 27;
	/**
	 * (0.7.179) Her crew about her deck: how fast a hand strolls, a gunner goes to his guns, anyone runs in a fight
	 * (blocks a tick); how far a hand strolls (blocks); how long each idles where they stop (ticks; a hand's is up to
	 * four times this, twice that in a fight); how near his gun a gunner must stand to load it; how often fallen
	 * gunners' guns are taken up (ticks).
	 */
	static final double WALK_PACE = 0.07, GUNNER_PACE = 0.1, RUN_PACE = 0.16, STROLL = 9.0, AT_GUN = 0.6;
	static final int HAND_IDLE = 60, GUNNER_IDLE = 20, MONKEY_IDLE = 20, MAN_CHECK = 20;

	private Corsairs() {
	}

	static void register() {
		Ship.captaincy = new Corsairs();
	}

	// ---- launching ----------------------------------------------------------------------------------------------------

	/** Who sails her: her captain's role, her gunners', the rest, and how many hands besides the gunners and boys. */
	record Complement(int captain, int gunner, int hand, int hands, int boys) {
	}

	/**
	 * Builds a {@code rig} in {@code look} at {@code water} (bow toward {@code bow}) and mans her for {@code faction}: her
	 * captain at the helm, a gunner to each gun or two, the rest about her deck. Null if she couldn't be launched there.
	 */
	static @Nullable Ship launch(ServerLevel level, Fleet.Rig rig, Fleet.Look look, BlockPos water, Direction bow, Crew crew, Faction faction,
		Complement complement) {
		Ship ship = Fleet.launch(level, rig, look, water, bow, crew);
		if (ship == null) {
			return null;
		}
		Fleet.Stations stations = Fleet.stations(ship);
		BlockPos home = crew.home;
		if (stations.helm() != null) {
			Folk captain = hand(level, ship, faction, complement.captain(), stations.helm(), home);
			if (captain != null) {
				crew.captain = captain.getUUID();
			}
		}
		// a gunner to each gun, two guns to a gunner on a big ship's far side
		List<Map.Entry<Long, Vec3>> guns = new ArrayList<>(stations.guns().entrySet());
		int gunners = Math.max(1, (guns.size() + 1) / 2 + (guns.size() >= 6 ? 1 : 0));
		List<Folk> crewed = new ArrayList<>();
		for (int i = 0; i < guns.size(); i++) {
			Map.Entry<Long, Vec3> gun = guns.get(i);
			if (i < gunners) {
				Folk g = hand(level, ship, faction, complement.gunner(), gun.getValue(), home);
				if (g != null) {
					crewed.add(g);
				}
			}
			if (!crewed.isEmpty()) {
				crew.gunners.put(gun.getKey(), crewed.get(i % crewed.size()).getUUID());
			}
		}
		List<Vec3> deck = new ArrayList<>(stations.deck());
		java.util.Collections.shuffle(deck, new java.util.Random(level.getRandom().nextLong()));
		int k = 0;
		for (int i = 0; i < complement.boys() && k < deck.size(); i++) {
			hand(level, ship, faction, Pirates.POWDER_MONKEY, deck.get(k++), home);
		}
		for (int i = 0; i < complement.hands() && k < deck.size(); i++) {
			hand(level, ship, faction, complement.hand(), deck.get(k++), home);
		}
		return ship;
	}

	/** One of her crew, of {@code role}, at {@code seat}. */
	static @Nullable Folk hand(ServerLevel level, Ship ship, Faction faction, int role, Vec3 seat, BlockPos home) {
		Folk folk = JaymosKingdoms.FOLK.create(level, EntitySpawnReason.EVENT);
		if (folk == null) {
			return null;
		}
		Vec3 at = ship.toWorld(seat.x, seat.y, seat.z);
		folk.snapTo(at.x, at.y, at.z, ship.getYRot(), 0.0F);
		folk.setup(role, faction, home, 256);
		folk.setAttached(Ship.SEAT, seat);
		level.addFreshEntity(folk);
		if (!ship.takeAboard(folk, seat)) {
			folk.discard();
			return null;
		}
		return folk;
	}

	// ---- at the helm ------------------------------------------------------------------------------------------------

	@Override
	public @Nullable Input helm(ServerLevel level, Ship ship, Crew crew) {
		long now = level.getGameTime();
		Ship foe = crew.target >= 0 && level.getEntity(crew.target) instanceof Ship s && !s.isRemoved() && !s.foundering() ? s : null;
		if (now % 2 == 0) {
			this.gunnery(level, ship, crew, foe, now);
		}
		this.deckwork(level, ship, crew, foe, now);
		if (crew.side == Crew.Side.WHALER) {
			Whalers.helm(level, ship, crew);
		}
		if (!crew.captained(ship)) {
			return null;
		}
		if ((now + ship.getId()) % THINK == 0) {
			this.think(level, ship, crew);
		}
		return Fleet.steer(ship, crew.wantYaw, crew.wantSail, crew.row, crew.astern);
	}

	/** Her captain looks about him: his foe, what he does about it, and the helm he sets. */
	private void think(ServerLevel level, Ship ship, Crew crew) {
		RandomSource random = level.getRandom();
		double fromHome = Math.sqrt(Raids.horizontal(ship.blockPosition(), crew.home));
		Ship foe = this.lookout(level, ship, crew);
		double leash = crew.side == Crew.Side.NAVY ? NAVY_LEASH : PIRATE_LEASH;
		if (foe != null && crew.side != Crew.Side.MERCHANT && crew.side != Crew.Side.WHALER && fromHome > leash) {
			foe = null;
		}
		crew.target = foe == null ? -1 : foe.getId();
		float want;
		double sail;
		if (foe != null && (crew.side == Crew.Side.MERCHANT || crew.side == Crew.Side.WHALER)) {
			// running: away from her, all sail (a whaler keeps her voyage's place, to take up again)
			if (crew.side == Crew.Side.MERCHANT) {
				crew.mode = "run";
			}
			want = Fleet.bearing(ship, foe.getX(), foe.getZ()) + 180.0F;
			sail = RUN_SAIL;
		} else if (foe != null) {
			double d = ship.distanceTo(foe);
			float to = Fleet.bearing(ship, foe.getX() + foe.drift().x * 20.0, foe.getZ() + foe.drift().z * 20.0);
			if (d > ENGAGE) {
				crew.mode = "chase";
				want = to;
				sail = CHASE_SAIL;
			} else {
				// come about to bring a broadside to bear: her beam to the foe, the side nearer her heading; sheer off when close
				crew.mode = "engage";
				float port = to - 90.0F, starboard = to + 90.0F;
				want = Math.abs(Mth.wrapDegrees(port - ship.getYRot())) < Math.abs(Mth.wrapDegrees(starboard - ship.getYRot())) ? port : starboard;
				if (d < TOO_CLOSE) {
					want += Mth.wrapDegrees(want - to) > 0 ? 25.0F : -25.0F;
				}
				sail = FIGHT_SAIL;
			}
		} else if (crew.side == Crew.Side.WHALER) {
			// (0.7.210) a whaler on her voyage: fitting out, outward, hunting, cutting in, homeward
			float[] plan = Whalers.think(level, ship, crew);
			if (plan == null) {
				return;
			}
			want = plan[0];
			sail = plan[1];
		} else if (crew.bound != null) {
			// bound somewhere: a merchantman to her harbour, a pirate to his cove's raid
			double d = Math.sqrt(Raids.horizontal(ship.blockPosition(), crew.bound));
			if (crew.side == Crew.Side.MERCHANT && d < ARRIVE) {
				Navies.arrived(level, ship, crew);
				return;
			}
			crew.mode = crew.side == Crew.Side.MERCHANT ? "voyage" : "raid";
			want = Fleet.bearing(ship, crew.bound.getX() + 0.5, crew.bound.getZ() + 0.5);
			sail = d < WAYPOINT_REACH ? 0.0 : d < 40.0 ? PATROL_SAIL : CHASE_SAIL;
			if (crew.side == Crew.Side.PIRATE && !PirateCoves.raiding(level, crew)) {
				crew.bound = null;
			}
		} else {
			crew.mode = "patrol";
			double range = crew.side == Crew.Side.NAVY ? NAVY_PATROL : PATROL;
			if (crew.waypoint == null || Math.sqrt(Raids.horizontal(ship.blockPosition(), crew.waypoint)) < WAYPOINT_REACH
				|| Math.sqrt(Raids.horizontal(crew.waypoint, crew.home)) > range * 1.2) {
				crew.waypoint = this.waypoint(level, crew, range, random);
			}
			want = Fleet.bearing(ship, crew.waypoint.getX() + 0.5, crew.waypoint.getZ() + 0.5);
			sail = PATROL_SAIL;
		}
		// open water ahead, or the nearest heading that has it; embayed, she backs off a while and tries another way
		Float clear = Fleet.heading(level, ship, want);
		if (crew.backing > 0) {
			crew.backing--;
			crew.astern = true;
			crew.wantYaw = ship.getYRot() + 40.0F;
			crew.wantSail = 0.0;
			crew.row = false;
			return;
		}
		crew.astern = false;
		boolean moving = Math.abs(ship.way()) > 0.03;
		if (clear == null || !moving && sail > 0.1 && ship.sailsSet() > 0.3) {
			crew.stuck++;
		} else {
			crew.stuck = Math.max(0, crew.stuck - 1);
		}
		if (crew.stuck > STUCK_LOOKS) {
			crew.stuck = 0;
			crew.backing = BACK_LOOKS;
			crew.waypoint = null;
		}
		crew.wantYaw = clear != null ? clear : want;
		crew.wantSail = clear != null ? sail : 0.2;
		crew.row = ship.oarSpeed() > 0.0 && ship.sailSpeed() < ROW_BELOW && sail > 0.0;
	}

	/** A new waypoint at sea round her home (her home itself if none is found). */
	private BlockPos waypoint(ServerLevel level, Crew crew, double range, RandomSource random) {
		BlockPos at = Fleet.openSea(level, crew.home, (int)range, 6, random);
		return at != null ? at : crew.home;
	}

	/** The foe her lookout sights nearest (null: none). */
	private @Nullable Ship lookout(ServerLevel level, Ship ship, Crew crew) {
		double sight = crew.side == Crew.Side.NAVY ? NAVY_SIGHT : crew.side == Crew.Side.MERCHANT || crew.side == Crew.Side.WHALER ? MERCHANT_SIGHT : PIRATE_SIGHT;
		Ship best = null;
		double bestD = sight * sight;
		for (Ship other : Ships.in(level)) {
			if (other == ship || other.foundering() || other.hull() == null) {
				continue;
			}
			double d = other.distanceToSqr(ship);
			if (d < bestD && this.hostile(level, crew, other)) {
				best = other;
				bestD = d;
			}
		}
		return best;
	}

	/** Whether {@code other} is a ship {@code crew} fights (or, a merchantman, runs from). */
	private boolean hostile(ServerLevel level, Crew crew, Ship other) {
		Crew them = other.crew;
		switch (crew.side) {
			case PIRATE -> {
				if (them != null) {
					return them.side != Crew.Side.PIRATE;
				}
				// any ship players sail (or stand aboard)
				return other.pilot() != null || !playersAboard(level, other).isEmpty();
			}
			case NAVY -> {
				if (them != null) {
					return them.side == Crew.Side.PIRATE;
				}
				for (ServerPlayer p : playersAboard(level, other)) {
					if (Reputation.get(p, crew.faction) <= Reputation.HOSTILE) {
						return true;
					}
				}
				return false;
			}
			default -> {
				return them != null && them.side == Crew.Side.PIRATE;
			}
		}
	}

	/** The players at her wheel or on her decks. */
	static List<ServerPlayer> playersAboard(ServerLevel level, Ship ship) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level.players()) {
			if (p.getVehicle() == ship || ship.getBoundingBox().inflate(1.0).contains(p.position()) && ship.aboard(ship.toLocal(p.position()), 1.0)) {
				out.add(p);
			}
		}
		return out;
	}

	// ---- her guns ---------------------------------------------------------------------------------------------------

	/** Her gunners load their guns, and fire those that bear on {@code foe} (one at a time down her side, a broadside). */
	private void gunnery(ServerLevel level, Ship ship, Crew crew, @Nullable Ship foe, long now) {
		if (crew.gunners.isEmpty()) {
			return;
		}
		boolean monkey = false;
		Map<UUID, LivingEntity> aboard = new java.util.HashMap<>();
		for (LivingEntity e : ship.crewAboard()) {
			aboard.put(e.getUUID(), e);
			monkey |= e instanceof Folk f && f.role() == Pirates.POWDER_MONKEY;
		}
		for (long gun : ship.guns()) {
			UUID who = crew.gunners.get(gun);
			LivingEntity gunner = who == null ? null : aboard.get(who);
			if (gunner == null) {
				continue;
			}
			if (!ship.gunLoaded(gun)) {
				if (!atGun(ship, crew, gun, gunner)) {
					// (0.7.179) a gun's loaded by her gunner standing at it: he's on his way
					crew.loading.remove(gun);
					continue;
				}
				Long ready = crew.loading.get(gun);
				if (ready == null) {
					crew.loading.put(gun, now + (monkey ? RELOAD_MONKEY : RELOAD) + level.getRandom().nextInt(30));
				} else if (now >= ready) {
					crew.loading.remove(gun);
					ship.loadGun(gun);
				}
				continue;
			}
			if (foe == null || now - crew.lastFired < GUN_GAP) {
				continue;
			}
			Vec3 dir = this.lay(level, ship, gun, foe);
			if (dir != null && ship.fireGunAlong(level, gun, dir, gunner)) {
				crew.lastFired = (int)now;
				if (gunner instanceof Folk f) {
					f.getLookControl().setLookAt(foe.getX(), foe.getY() + 2.0, foe.getZ());
				}
			}
		}
	}

	/** How the gun at {@code gun} is laid on {@code foe} (her hull, led as she sails), or null if it doesn't bear. */
	private @Nullable Vec3 lay(ServerLevel level, Ship ship, long gun, Ship foe) {
		double[] way = ship.gunWay(gun);
		Hull h = foe.hull();
		if (way == null || h == null) {
			return null;
		}
		Vec3 muzzle = ship.gunAt(gun);
		Vec3 aim = new Vec3(foe.getX(), foe.getY() + h.minY + Math.max(1.0, h.draft + 0.6), foe.getZ());
		double d = Math.sqrt((aim.x - muzzle.x) * (aim.x - muzzle.x) + (aim.z - muzzle.z) * (aim.z - muzzle.z));
		if (d < GUN_NEAREST || d > GUN_RANGE) {
			return null;
		}
		// led: where she'll be when the ball gets there
		double flight = d / SHOT_SPEED;
		aim = aim.add(foe.drift().x * flight, 0.0, foe.drift().z * flight);
		double dx = aim.x - muzzle.x, dz = aim.z - muzzle.z;
		d = Math.sqrt(dx * dx + dz * dz);
		double hx = dx / d, hz = dz / d;
		if (hx * way[0] + hz * way[1] < GUN_ARC) {
			return null;
		}
		// laid for the range: the low arc that carries a ball this far, and up or down to her deck
		double reach = Mth.clamp(SHOT_FALL * d / (SHOT_SPEED * SHOT_SPEED), -1.0, 1.0);
		double elevation = 0.5 * Math.toDegrees(Math.asin(reach)) + Math.toDegrees(Math.atan2(aim.y - muzzle.y, d));
		RandomSource random = level.getRandom();
		elevation += (random.nextFloat() - 0.5F) * 2.0F * AIM_SPREAD;
		double turn = Math.toRadians((random.nextFloat() - 0.5F) * 2.0F * AIM_SPREAD);
		double c = Math.cos(turn), s = Math.sin(turn);
		double ax = hx * c - hz * s, az = hx * s + hz * c;
		double up = Math.toRadians(Mth.clamp(elevation, LOWEST, HIGHEST));
		return new Vec3(ax * Math.cos(up), Math.sin(up), az * Math.cos(up)).normalize();
	}

	// ---- (0.7.179) about her deck -----------------------------------------------------------------------------------

	/**
	 * Her crew about her deck ({@link Deck}): her gunners go from gun to gun of theirs, loading each, and a hand takes up
	 * the guns of one who's fallen; in a fight her powder monkeys run between the guns; her hands stroll her deck,
	 * idling a while wherever they stop. Her captain keeps her wheel.
	 */
	private void deckwork(ServerLevel level, Ship ship, Crew crew, @Nullable Ship foe, long now) {
		Deck deck = Deck.of(ship, crew);
		if (deck == null || deck.places.isEmpty()) {
			return;
		}
		List<LivingEntity> aboard = ship.crewAboard();
		if ((now + ship.getId()) % MAN_CHECK == 0) {
			manGuns(ship, crew, deck, aboard);
		}
		RandomSource random = level.getRandom();
		java.util.Set<UUID> here = new java.util.HashSet<>();
		for (LivingEntity hand : aboard) {
			UUID id = hand.getUUID();
			here.add(id);
			Vec3 seat = hand.getAttached(Ship.SEAT);
			if (seat == null || id.equals(crew.captain) || Downed.isDowned(hand)) {
				continue;
			}
			Crew.Walk walk = crew.walks.computeIfAbsent(id, k -> new Crew.Walk());
			if (!walk.way.isEmpty()) {
				step(ship, hand, seat, walk);
				continue;
			}
			if (now < walk.rest) {
				continue;
			}
			boolean monkey = hand instanceof Folk f && f.role() == Pirates.POWDER_MONKEY;
			List<Long> mine = new ArrayList<>();
			crew.gunners.forEach((gun, who) -> {
				if (who.equals(id) && deck.guns.containsKey(gun)) {
					mine.add(gun);
				}
			});
			Vec3 to;
			int idle;
			if (!mine.isEmpty()) {
				// a gunner: to the nearest gun of his that wants loading, else to the nearest of his, and stands to it
				to = null;
				double best = Double.MAX_VALUE;
				for (long gun : mine) {
					Vec3 at = deck.guns.get(gun);
					double d = at.distanceToSqr(seat) + (ship.gunLoaded(gun) ? 10000.0 : 0.0);
					if (d < best) {
						best = d;
						to = at;
					}
				}
				walk.pace = foe != null ? RUN_PACE : GUNNER_PACE;
				idle = GUNNER_IDLE;
			} else if (monkey && foe != null) {
				// a powder monkey in a fight: powder and shot to the guns
				List<Vec3> guns = new ArrayList<>(deck.guns.values());
				to = guns.isEmpty() ? null : guns.get(random.nextInt(guns.size()));
				walk.pace = RUN_PACE;
				idle = MONKEY_IDLE + random.nextInt(MONKEY_IDLE);
			} else {
				// a hand: somewhere a few steps off that nobody else is standing at or making for
				to = this.stroll(crew, deck, aboard, seat, random);
				walk.pace = WALK_PACE;
				idle = (foe != null ? 2 : 1) * (HAND_IDLE + random.nextInt(HAND_IDLE * 3));
			}
			List<Vec3> way = to == null || to.distanceToSqr(seat) < 0.01 ? null : deck.path(seat, to);
			if (way == null || way.isEmpty()) {
				walk.rest = now + idle / 2;
				continue;
			}
			walk.way = new ArrayList<>(way);
			walk.rest = now + (int)(way.size() / walk.pace) + idle;
		}
		crew.walks.keySet().retainAll(here);
	}

	/** Where a hand strolls to: a free place on her deck he can walk to, not too far off; null to stay put. */
	private @Nullable Vec3 stroll(Crew crew, Deck deck, List<LivingEntity> aboard, Vec3 seat, RandomSource random) {
		java.util.Set<Vec3> taken = new java.util.HashSet<>();
		for (LivingEntity other : aboard) {
			Vec3 s = other.getAttached(Ship.SEAT);
			if (s != null) {
				taken.add(deck.nearest(s));
			}
			Crew.Walk w = crew.walks.get(other.getUUID());
			if (w != null && !w.way.isEmpty()) {
				taken.add(w.way.get(w.way.size() - 1));
			}
		}
		taken.addAll(deck.guns.values());
		List<Vec3> free = new ArrayList<>();
		for (Vec3 p : deck.reachable(seat)) {
			if (!taken.contains(p) && p.distanceToSqr(seat) <= STROLL * STROLL) {
				free.add(p);
			}
		}
		return free.isEmpty() ? null : free.get(random.nextInt(free.size()));
	}

	/** One step of {@code hand}'s walk: their station moved along it, and them turned the way they're going. */
	private static void step(Ship ship, LivingEntity hand, Vec3 seat, Crew.Walk walk) {
		Vec3 next = walk.way.get(0);
		Vec3 d = next.subtract(seat);
		double len = d.length();
		Vec3 now;
		if (len <= walk.pace) {
			now = next;
			walk.way.remove(0);
		} else {
			now = seat.add(d.scale(walk.pace / len));
		}
		hand.setAttached(Ship.SEAT, now);
		double flat = d.x * d.x + d.z * d.z;
		if (flat > 1.0E-4) {
			// her frame turned as she lies (as Ship.toWorld)
			float c = Mth.cos(ship.getYRot() * Mth.DEG_TO_RAD), s = Mth.sin(ship.getYRot() * Mth.DEG_TO_RAD);
			double wx = d.x * c - d.z * s, wz = d.x * s + d.z * c;
			float yaw = (float)(Mth.atan2(wz, wx) * Mth.RAD_TO_DEG) - 90.0F;
			hand.setYRot(yaw);
			hand.setYBodyRot(yaw);
			hand.setYHeadRot(yaw);
		}
	}

	/** Whether the gunner at {@code gun} is standing to it (or it has no place to stand: then he serves it from anywhere). */
	private static boolean atGun(Ship ship, Crew crew, long gun, LivingEntity gunner) {
		Deck deck = Deck.of(ship, crew);
		Vec3 at = deck == null ? null : deck.guns.get(gun);
		Vec3 seat = gunner.getAttached(Ship.SEAT);
		return at == null || seat == null || at.distanceToSqr(seat) <= AT_GUN * AT_GUN;
	}

	/** Every gun with no gunner left aboard goes to a hand (the nearest), else to the gunner with fewest guns. */
	private static void manGuns(Ship ship, Crew crew, Deck deck, List<LivingEntity> aboard) {
		Map<UUID, LivingEntity> byId = new java.util.HashMap<>();
		for (LivingEntity e : aboard) {
			byId.put(e.getUUID(), e);
		}
		for (long gun : ship.guns()) {
			UUID who = crew.gunners.get(gun);
			if (who != null && byId.containsKey(who) && !Downed.isDowned(byId.get(who))) {
				continue;
			}
			Vec3 at = deck.guns.get(gun);
			LivingEntity best = null;
			double bestD = Double.MAX_VALUE;
			for (LivingEntity e : aboard) {
				if (e.getUUID().equals(crew.captain) || crew.gunners.containsValue(e.getUUID()) || Downed.isDowned(e)
					|| e instanceof Folk f && f.role() == Pirates.POWDER_MONKEY) {
					continue;
				}
				Vec3 s = e.getAttached(Ship.SEAT);
				double d = at == null || s == null ? 0.0 : at.distanceToSqr(s);
				if (d < bestD) {
					best = e;
					bestD = d;
				}
			}
			if (best == null) {
				// no hand left: the gunner with fewest guns takes it too
				Map<UUID, Integer> load = new java.util.HashMap<>();
				for (UUID g : crew.gunners.values()) {
					if (byId.containsKey(g) && !Downed.isDowned(byId.get(g))) {
						load.merge(g, 1, Integer::sum);
					}
				}
				best = load.entrySet().stream().min(Map.Entry.comparingByValue()).map(e -> byId.get(e.getKey())).orElse(null);
			}
			if (best != null) {
				crew.gunners.put(gun, best.getUUID());
				crew.walks.remove(best.getUUID());
			}
		}
	}

	// ---- going down -------------------------------------------------------------------------------------------------

	@Override
	public void foundering(ServerLevel level, Ship ship, Crew crew, @Nullable Entity by) {
		ServerPlayer player = by instanceof ServerPlayer p ? p : by instanceof net.minecraft.world.entity.projectile.Projectile proj
			&& proj.getOwner() instanceof ServerPlayer p2 ? p2 : null;
		if (player == null) {
			// (holed by a gun a player fired from her own ship: the shot's gunner is the player)
			player = by instanceof Player p ? (ServerPlayer)p : null;
		}
		DamageSource source = player != null ? level.damageSources().playerAttack(player) : level.damageSources().drown();
		for (LivingEntity hand : ship.crewAboard()) {
			hand.setInvulnerableTime(0);
			hand.hurtServer(level, source, Float.MAX_VALUE);
		}
		KingdomEvents.announce(level.getServer(), Component.translatable("message.jaymoskingdoms.ship.sunk." + crew.side.id(), crew.name,
			player == null ? Component.translatable("message.jaymoskingdoms.someone") : player.getDisplayName()).withStyle(ChatFormatting.GOLD));
		switch (crew.side) {
			case PIRATE -> PirateCoves.shipLost(level, crew, player);
			case NAVY, MERCHANT -> Navies.shipLost(level, crew, player);
			case WHALER -> Whalers.lost(level, crew);
		}
	}

	// ---- her hold ---------------------------------------------------------------------------------------------------

	@Override
	public @Nullable Container hold(ServerLevel level, Ship ship, Crew crew, ServerPlayer player) {
		if (crew.captained(ship)) {
			player.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.crew.hold_guarded"));
			return null;
		}
		for (LivingEntity hand : ship.crewAboard()) {
			if (crew.gunners.containsValue(hand.getUUID()) || crew.side != Crew.Side.PIRATE) {
				player.sendOverlayMessage(Component.translatable("ship.jaymoskingdoms.crew.hold_guarded"));
				return null;
			}
		}
		List<ItemStack> share = crew.plunder.get(player.getUUID());
		if (share == null) {
			share = new ArrayList<>(java.util.Collections.nCopies(SHARE, ItemStack.EMPTY));
			stock(level, crew, share, level.getRandom());
			crew.plunder.put(player.getUUID(), share);
			if (crew.side != Crew.Side.PIRATE) {
				// a realm's ship plundered: it hears of it
				Faction f = Factions.get(level.getServer(), crew.faction);
				if (f != null) {
					Reputation.change(player, f, REP_PLUNDER);
				}
			}
		}
		return new Share(share);
	}

	/** What a share of her hold holds, by who she is. */
	static void stock(ServerLevel level, Crew crew, List<ItemStack> share, RandomSource random) {
		List<ItemStack> loot = new ArrayList<>();
		switch (crew.side) {
			case PIRATE -> {
				loot.add(new ItemStack(Items.EMERALD, 4 + random.nextInt(9)));
				loot.add(new ItemStack(Items.GOLD_INGOT, 2 + random.nextInt(5)));
				loot.add(new ItemStack(Items.GOLD_NUGGET, 8 + random.nextInt(17)));
				loot.add(new ItemStack(Items.GUNPOWDER, 4 + random.nextInt(7)));
				loot.add(new ItemStack(Sailing.ROUND_SHOT, 4 + random.nextInt(5)));
				loot.add(new ItemStack(Items.IRON_INGOT, 2 + random.nextInt(5)));
				loot.add(new ItemStack(Items.COOKED_COD, 3 + random.nextInt(6)));
				loot.add(new ItemStack(Items.HONEY_BOTTLE, 1 + random.nextInt(3)));
				if (random.nextFloat() < 0.35F) {
					loot.add(new ItemStack(Items.AMETHYST_SHARD, 2 + random.nextInt(4)));
				}
				if (random.nextFloat() < 0.25F) {
					loot.add(TreasureWrecks.chart(level, crew.home, random));
				}
				if (random.nextFloat() < 0.2F) {
					loot.add(new ItemStack(Items.SPYGLASS));
				}
				if (random.nextFloat() < 0.15F) {
					loot.add(new ItemStack(Items.HEART_OF_THE_SEA));
				}
			}
			case NAVY -> {
				loot.add(new ItemStack(Items.GUNPOWDER, 6 + random.nextInt(8)));
				loot.add(new ItemStack(Sailing.ROUND_SHOT, 6 + random.nextInt(8)));
				loot.add(new ItemStack(Items.IRON_INGOT, 3 + random.nextInt(6)));
				loot.add(new ItemStack(Items.ARROW, 16 + random.nextInt(17)));
				loot.add(new ItemStack(Items.BREAD, 6 + random.nextInt(8)));
				loot.add(new ItemStack(Items.EMERALD, 2 + random.nextInt(5)));
			}
			case WHALER -> {
				loot.add(new ItemStack(com.jaymoskingdoms.whaling.Whaling.WHALE_OIL, 4 + random.nextInt(9)));
				loot.add(new ItemStack(com.jaymoskingdoms.whaling.Whaling.WHALE_BLUBBER, 8 + random.nextInt(17)));
				loot.add(new ItemStack(com.jaymoskingdoms.whaling.Whaling.WHALE_BONE, 2 + random.nextInt(5)));
				loot.add(new ItemStack(com.jaymoskingdoms.whaling.Whaling.HARPOON_ITEM, 2 + random.nextInt(4)));
				loot.add(new ItemStack(Items.EMERALD, 2 + random.nextInt(5)));
			}
			case MERCHANT -> {
				Item[] goods = {Items.WOOL.pick(net.minecraft.world.item.DyeColor.WHITE), Items.WHEAT, Items.SUGAR, Items.COCOA_BEANS, Items.LEATHER, Items.PAPER, Items.GLASS_BOTTLE, Items.HONEYCOMB};
				for (int i = 0; i < 4; i++) {
					loot.add(new ItemStack(goods[random.nextInt(goods.length)], 8 + random.nextInt(25)));
				}
				loot.add(new ItemStack(Items.EMERALD, 6 + random.nextInt(11)));
				loot.add(new ItemStack(Items.IRON_INGOT, 2 + random.nextInt(5)));
			}
		}
		List<Integer> slots = new ArrayList<>();
		for (int i = 0; i < share.size(); i++) {
			slots.add(i);
		}
		java.util.Collections.shuffle(slots, new java.util.Random(random.nextLong()));
		for (int i = 0; i < loot.size() && i < slots.size(); i++) {
			share.set(slots.get(i), loot.get(i));
		}
	}

	/** A player's share of a hold: the list kept in her {@link Crew}, as a chest. */
	private record Share(List<ItemStack> items) implements Container {
		@Override
		public int getContainerSize() {
			return this.items.size();
		}

		@Override
		public boolean isEmpty() {
			return this.items.stream().allMatch(ItemStack::isEmpty);
		}

		@Override
		public ItemStack getItem(int slot) {
			return this.items.get(slot);
		}

		@Override
		public ItemStack removeItem(int slot, int count) {
			ItemStack stack = this.items.get(slot);
			if (stack.isEmpty() || count <= 0) {
				return ItemStack.EMPTY;
			}
			ItemStack taken = stack.split(count);
			if (stack.isEmpty()) {
				this.items.set(slot, ItemStack.EMPTY);
			}
			return taken;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			ItemStack stack = this.items.get(slot);
			this.items.set(slot, ItemStack.EMPTY);
			return stack;
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			this.items.set(slot, stack);
		}

		@Override
		public void setChanged() {
		}

		@Override
		public boolean stillValid(Player player) {
			return true;
		}

		@Override
		public void clearContent() {
			java.util.Collections.fill(this.items, ItemStack.EMPTY);
		}
	}

	// ---- her crew ---------------------------------------------------------------------------------------------------

	/**
	 * One of a ship's crew, aboard at their station (from {@link Folk#customServerAiStep}): with no foe of their own, they
	 * take the nearest hostile soul aboard the ship their captain is fighting, for their pistols and muskets.
	 */
	static void crewTick(ServerLevel level, Folk folk) {
		if ((folk.tickCount + folk.getId()) % 20 != 0 || folk.getTarget() != null && folk.getTarget().isAlive() || !folk.combatant()) {
			return;
		}
		if (!(folk.getVehicle() instanceof Ship ship) || ship.crew == null || ship.crew.target < 0
			|| !(level.getEntity(ship.crew.target) instanceof Ship foe)) {
			return;
		}
		LivingEntity best = null;
		double bestD = Pirates.PISTOL_FURTHEST * Pirates.PISTOL_FURTHEST;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, foe.getBoundingBox().inflate(2.0), e -> e.isAlive() && !e.isSpectator())) {
			boolean hostile = e instanceof ServerPlayer p ? !p.isCreative() && (ship.crew.side == Crew.Side.PIRATE || Reputation.get(p, ship.crew.faction) <= Reputation.HOSTILE)
				: e instanceof Folk f && !folk.sameSide(f) && (Pirates.isPirate(f) != Pirates.isPirate(folk));
			double d = e.distanceToSqr(folk);
			if (hostile && d < bestD) {
				best = e;
				bestD = d;
			}
		}
		if (best != null) {
			folk.setTarget(best);
		}
	}

	/** {@code entity} is one of a ship's crew (not counted on any roster). */
	static boolean isCrew(Entity entity) {
		return entity.hasAttached(Ship.SEAT);
	}
}
