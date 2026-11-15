package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import com.jaymoskingdoms.sailing.Fleet;
import com.jaymoskingdoms.sailing.HelmBlock;
import com.jaymoskingdoms.sailing.Hull;
import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.sailing.Ships;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The steam towns' airships (GT New Horizons picks, patch 9; Andy: "maybe they have airships?"). Each steam town
 * ({@link SteamTowns}) keeps one on its berth at the airfield: a railed deck under a bag of wool, a balloon {@link Ship}
 * of the sailing code's kind. Standing on her deck, a player buys passage from her captain (the town's aeronaut,
 * {@link SteamTrades}) on MCA's Interact page ("Buy Passage"): to another steam town, or home if she lies away. She casts
 * off with her captain at the wheel, climbs, and flies there on the wind with her boiler pushing her along
 * ({@link #STEAM_CRUISE}), going up over high ground ahead of her, then comes down onto that town's visitor berth (or her
 * own) and is blocks again. With no other steam town in the world, her captain shakes his head.
 * <p>
 * <b>The passenger</b> ({@link #PASSENGER}): everyone on her deck when she casts off is kept from harm until they are
 * standing on the ground again: no damage, no fall, and one who goes over the rail is caught and set back on her deck.
 * <p>
 * <b>The way home.</b> Lying at another town, she waits there a day ({@link #AWAY_STAY}) for anyone wanting to fly back.
 * Then, the next time that berth is loaded and nobody is near, she is gone from it, and her own berth at home has her
 * again the next time it is loaded with nobody near ({@link #upkeep}): an empty flight never loads a chunk.
 * <p>
 * <b>Chunks.</b> She flies only where a player is: a passenger aboard loads the way ahead as anyone travelling does, and
 * nothing more is held loaded. If every passenger leaves the game she waits where she is, her course kept on her
 * ({@link #TRIP}), until the chunk is loaded again.
 * <p>
 * She is flown through {@link Ship#skyfaring}, ahead of the sky folk's ferries ({@link SkyFerries}), who fly what isn't hers.
 */
public final class SteamAirships implements Ship.Skyfaring {
	// ---- tuning ----
	/** She cruises this high over the higher of the two berths (blocks), and this high over any ground she sees ahead. */
	static final int CRUISE_UP = 36, CLEAR = 14;
	/** How far ahead she looks for high ground (blocks), and how often (ticks). */
	static final int[] LOOK_AHEAD = {16, 32, 56};
	static final int LOOK_EVERY = 20;
	/** From this far off (blocks) she comes down to {@link #ARRIVE_UP} over her berth; she eases her cloth from {@link #EASE_FROM}. */
	static final double APPROACH = 72.0, EASE_FROM = 32.0, EASE = 0.3;
	static final int ARRIVE_UP = 18;
	/** She is over her berth within this (blocks); she only comes down once within {@link #PLUMB} of it, her head within {@link #SQUARE}. */
	static final double OVER = 2.5, PLUMB = 0.5;
	static final float SQUARE = 4.0F;
	/** Her crew haul her in over the berth this fast (blocks a tick). */
	static final double HAUL = 0.05;
	/** Her boiler's push: she makes at least this way under steam (blocks a tick), the most it adds. */
	static final double STEAM_CRUISE = 0.35, STEAM_MOST = 0.35;
	/** Making no way for this long (ticks), she goes this much higher. */
	static final int STUCK_TICKS = 60, OVER_STUCK = 6;
	/** A flight that has taken this long (ticks) is given up and she is moored where she hangs; coming down that long, the same. */
	static final long LONGEST_FLIGHT = 24000L;
	static final int DOWN_MOST = 1200;
	/** Passage: the captain this near her wheel, the player this near it (on her deck); the fare (emeralds' worth) and how it grows with distance. */
	static final double CAPTAIN_REACH = 32.0, ABOARD_REACH = 9.0;
	static final int FARE_BASE = 3, FARE_PER = 250, FARE_MOST = 20, DESTINATIONS = 3;
	/** How often the berths are seen to (ticks), and how near no player may be for a ship to come or go unseen. */
	static final int UPKEEP_EVERY = 200;
	static final double HIDE_RANGE = 64.0;
	/** She lies at another town this long before she goes home; a flight unheard of this long is lost; a berth found empty is laid again after this. */
	static final long AWAY_STAY = 24000L, LOST_AFTER = 48000L, REBUILD_TICKS = 48000L;
	/** How often (ticks) the passengers are looked after. */
	static final int SAFE_EVERY = 5;

	/** The legs of a flight: up from her berth, across, down onto the berth she is bound for. */
	static final int UP = 0, OUT = 1, DOWN = 2;

	/**
	 * A flight: the town she belongs to (-1: none's), the town she is bound for, where her keel will lie there and how
	 * she will lie, the height she cruises at, the leg she is on, when she cast off.
	 */
	record Trip(int owner, int to, Vec3 target, float yaw, double cruise, int leg, long began) {
		static final Codec<Trip> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("owner").forGetter(Trip::owner),
				Codec.INT.fieldOf("to").forGetter(Trip::to),
				Vec3.CODEC.fieldOf("target").forGetter(Trip::target),
				Codec.FLOAT.fieldOf("yaw").forGetter(Trip::yaw),
				Codec.DOUBLE.fieldOf("cruise").forGetter(Trip::cruise),
				Codec.INT.fieldOf("leg").forGetter(Trip::leg),
				Codec.LONG.fieldOf("began").forGetter(Trip::began))
			.apply(i, Trip::new));

		Trip on(int leg) {
			return new Trip(this.owner, this.to, this.target, this.yaw, this.cruise, leg, this.began);
		}
	}

	/** A passenger: the airship they flew on, and when their care runs out at the latest (they are on the ground by then). */
	record Passage(UUID ship, long until) {
		static final Codec<Passage> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("ship").forGetter(Passage::ship),
				Codec.LONG.fieldOf("until").forGetter(Passage::until))
			.apply(i, Passage::new));
	}

	public static final AttachmentType<Trip> TRIP = AttachmentRegistry.create(JaymosKingdoms.id("steam_airship_trip"), builder -> builder.persistent(Trip.CODEC));
	public static final AttachmentType<Passage> PASSENGER = AttachmentRegistry.create(JaymosKingdoms.id("steam_airship_passenger"),
		builder -> builder.persistent(Passage.CODEC));

	/** One of a town's two berths: where her wheel stands, where her keel lies and how she lies, and every block of her there. */
	record Berth(BlockPos helm, Vec3 keel, float yaw, List<BlockPos> blocks) {
	}

	/** Where a captain will fly: the town, her keel's place and heading there, the fare. */
	record Dest(int town, String name, Vec3 keel, float yaw, int fare) {
	}

	/** A player asked a captain: him, the wheel of the ship he'd fly, the town she belongs to, and where he named. */
	private record Asked(int captain, BlockPos helm, int owner, List<Dest> dests) {
	}

	/** Each flight under way: how long she has made no way (ticks), how much higher she has gone for it, how high the ground ahead wants her, how long she has been coming down. */
	private static final class Flight {
		int stuck, extra, down;
		double ahead = Double.NEGATIVE_INFINITY;
	}

	private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
	private static final Map<UUID, Asked> ASKED = new HashMap<>();
	/** Who flies what isn't ours (the sky folk's ferries). */
	private Ship.@Nullable Skyfaring before;

	private SteamAirships() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("airship attachments: {} {}", TRIP, PASSENGER);
		SteamAirships pilot = new SteamAirships();
		pilot.before = Ship.skyfaring;
		Ship.skyfaring = pilot;
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !(entity instanceof ServerPlayer p && p.hasAttached(PASSENGER)));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerLevel level = Factions.level(server);
			long now = level.getGameTime();
			if (now % SAFE_EVERY == 0) {
				passengers(server, now);
			}
			if (now % UPKEEP_EVERY == 3) {
				upkeep(level, now);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FLIGHTS.clear();
			ASKED.clear();
		});
	}

	// ---- the berths ----------------------------------------------------------------------------------------------------

	/**
	 * {@code town}'s home berth or its visitor's: the airship's blocks as {@link #lay} puts them there, and where her keel
	 * lies and how she lies, worked out as {@link Ship#castOff} works them out of her blocks, so a ship flown onto it and
	 * moored there lies exactly as one laid there would.
	 */
	static Berth berth(SteamTowns.Town town, boolean home) {
		CampBuildings.Placer field = town.placer();
		int[] b = home ? SteamBuildings.HOME_BERTH : SteamBuildings.VISITOR_BERTH;
		List<BlockPos> blocks = new ArrayList<>();
		BlockPos helm = null;
		Direction facing = Direction.NORTH;
		for (Blueprint.Placement p : SteamBuildings.airship(SteamTowns.wool(net.minecraft.world.item.DyeColor.WHITE)).blocks()) {
			BlockPos at = field.at(p.offset().offset(b[0], b[1], b[2]));
			blocks.add(at);
			BlockState state = field.turn(p.state());
			if (state.getBlock() instanceof HelmBlock) {
				helm = at;
				facing = state.getValue(HelmBlock.FACING);
			}
		}
		if (helm == null) {
			helm = blocks.getFirst();
		}
		Rotation turn = Rotation.NONE;
		for (Rotation r : Rotation.values()) {
			if (r.rotate(facing) == Direction.SOUTH) {
				turn = r;
			}
		}
		int y0 = Integer.MAX_VALUE, ax = Integer.MAX_VALUE, bx = Integer.MIN_VALUE, az = Integer.MAX_VALUE, bz = Integer.MIN_VALUE;
		for (BlockPos p : blocks) {
			y0 = Math.min(y0, p.getY());
			BlockPos local = p.subtract(helm).rotate(turn);
			ax = Math.min(ax, local.getX());
			bx = Math.max(bx, local.getX());
			az = Math.min(az, local.getZ());
			bz = Math.max(bz, local.getZ());
		}
		BlockPos middle = new BlockPos(Math.floorDiv(ax + bx, 2), 0, Math.floorDiv(az + bz, 2));
		Rotation back = turn.getRotated(turn).getRotated(turn);
		BlockPos origin = helm.offset(middle.rotate(back));
		Vec3 keel = new Vec3(origin.getX() + 0.5, origin.getY() + (y0 - helm.getY()), origin.getZ() + 0.5);
		return new Berth(helm, keel, facing.toYRot(), blocks);
	}

	/** {@code town}'s airship laid on her home berth, her bag in the town's colour. */
	static void lay(ServerLevel level, SteamTowns.Town town) {
		Faction f = Factions.get(level.getServer(), town.id());
		CampBuildings.Placer field = town.placer();
		int[] b = SteamBuildings.HOME_BERTH;
		BlockState wool = SteamTowns.wool(f != null ? f.color() : net.minecraft.world.item.DyeColor.WHITE);
		for (Blueprint.Placement p : SteamBuildings.airship(wool).blocks()) {
			level.setBlock(field.at(p.offset().offset(b[0], b[1], b[2])), field.turn(p.state()), Block.UPDATE_ALL);
		}
	}

	private static boolean moored(ServerLevel level, Berth berth) {
		return level.getBlockState(berth.helm()).getBlock() instanceof HelmBlock;
	}

	private static boolean clear(ServerLevel level, Berth berth) {
		for (BlockPos p : berth.blocks()) {
			BlockState there = level.getBlockState(p);
			if (!there.isAir() && !there.canBeReplaced()) {
				return false;
			}
		}
		return true;
	}

	/** A moored airship taken off her berth unseen (she has flown home): her blocks lifted out of the world and gone. */
	private static void putAway(ServerLevel level, BlockPos helm) {
		Ship ship = Fleet.castOff(level, helm);
		if (ship != null) {
			ship.discard();
		}
	}

	/** The town whose airship lies away at {@code town} (on its visitor berth), or null. */
	private static SteamTowns.@Nullable Town visiting(List<SteamTowns.Town> towns, int town) {
		for (SteamTowns.Town t : towns) {
			if (t.awayAt().isPresent() && t.awayAt().get() == town) {
				return t;
			}
		}
		return null;
	}

	/**
	 * Every loaded airfield: a visitor whose stay is over (or who belongs to nobody) is put away unseen and her own town
	 * told she is coming home; an empty home berth is laid again unseen once its ship is home, overdue, or (stolen,
	 * burnt) after {@link #REBUILD_TICKS}.
	 */
	private static void upkeep(ServerLevel level, long now) {
		List<SteamTowns.Town> towns = SteamTowns.all(level);
		for (SteamTowns.Town t : towns) {
			Berth home = berth(t, true), visitor = berth(t, false);
			if (!level.isPositionEntityTicking(home.helm()) || !level.isPositionEntityTicking(visitor.helm())) {
				continue;
			}
			boolean seen = Raids.playerNear(level, home.helm(), HIDE_RANGE);
			if (!seen && moored(level, visitor)) {
				SteamTowns.Town owner = visiting(towns, t.id());
				if (owner == null || now - owner.awaySince() > AWAY_STAY) {
					putAway(level, visitor.helm());
					if (owner != null) {
						SteamTowns.put(level, owner.away(Optional.empty(), -1L).leaving(-1L).missing(now - REBUILD_TICKS));
					}
				}
			}
			t = SteamTowns.get(level, t.id());
			if (t == null) {
				continue;
			}
			if (moored(level, home)) {
				if (t.gone() >= 0 || t.left() >= 0 || t.awayAt().isPresent()) {
					SteamTowns.put(level, t.away(Optional.empty(), -1L).leaving(-1L).missing(-1L));
				}
				continue;
			}
			boolean flying = t.left() >= 0 && now - t.left() < LOST_AFTER;
			boolean away = t.awayAt().isPresent() && now - t.awaySince() < AWAY_STAY * 2;
			if (flying || away) {
				continue;
			}
			// overdue from a flight or a stay: she comes home; gone with no word of her: she is built again in time
			boolean accounted = t.left() >= 0 || t.awayAt().isPresent();
			long gone = t.gone() >= 0 ? t.gone() : accounted ? now - REBUILD_TICKS : now;
			if (t.gone() != gone || accounted) {
				t = t.away(Optional.empty(), -1L).leaving(-1L).missing(gone);
				SteamTowns.put(level, t);
			}
			if (now - gone >= REBUILD_TICKS && !seen && clear(level, home)) {
				lay(level, t);
				SteamTowns.put(level, t.missing(-1L));
			}
		}
	}

	// ---- passage -------------------------------------------------------------------------------------------------------

	/** A moored airship {@code player} stands aboard with {@code captain} near her wheel: her town, whether it's her home berth, the berth. */
	private record Mooring(SteamTowns.Town town, boolean home, Berth berth) {
	}

	private static @Nullable Mooring mooring(ServerLevel level, ServerPlayer player, LivingEntity captain) {
		SteamTowns.Town town = SteamTowns.at(level, player.blockPosition());
		if (town == null) {
			return null;
		}
		for (boolean home : new boolean[] {true, false}) {
			Berth b = berth(town, home);
			Vec3 wheel = Vec3.atCenterOf(b.helm());
			if (player.position().distanceToSqr(wheel) <= ABOARD_REACH * ABOARD_REACH && player.getY() >= b.helm().getY() - 0.5
				&& captain.position().distanceToSqr(wheel) <= CAPTAIN_REACH * CAPTAIN_REACH && moored(level, b)) {
				return new Mooring(town, home, b);
			}
		}
		return null;
	}

	/** "Buy Passage" shows on {@code v}'s Interact page: a steam town's aeronaut, with {@code player} aboard a moored airship near him. */
	static boolean offers(ServerPlayer player, LivingEntity v) {
		return v instanceof Villager villager && SteamTrades.aeronaut(villager) && !villager.isBaby() && mooring(player.level(), player, v) != null;
	}

	/** The town that ship belongs to: the berth's own if she lies at home, else the town whose ship is away here (-1: nobody's). */
	private static int owner(List<SteamTowns.Town> towns, Mooring m) {
		if (m.home()) {
			return m.town().id();
		}
		SteamTowns.Town o = visiting(towns, m.town().id());
		return o == null ? -1 : o.id();
	}

	/** Where she can fly from {@code m}: home if she lies away, else every other steam town's visitor berth not taken, nearest first. */
	private static List<Dest> destinations(ServerLevel level, Mooring m, int owner) {
		List<SteamTowns.Town> towns = SteamTowns.all(level);
		List<Integer> bound = new ArrayList<>();
		for (Ship s : Ships.in(level)) {
			Trip trip = s.getAttached(TRIP);
			if (trip != null && trip.to() != trip.owner()) {
				bound.add(trip.to());
			}
		}
		List<Dest> out = new ArrayList<>();
		BlockPos from = m.berth().helm();
		for (SteamTowns.Town t : towns) {
			if (t.id() == m.town().id()) {
				continue;
			}
			boolean home = t.id() == owner;
			if (!home && (visiting(towns, t.id()) != null || bound.contains(t.id()))) {
				continue;
			}
			Berth b = berth(t, home);
			double far = Math.sqrt(Raids.horizontal(from, b.helm()));
			int fare = Math.min(FARE_MOST, FARE_BASE + (int)(far / FARE_PER));
			out.add(new Dest(t.id(), SteamTowns.name(level, t), b.keel(), b.yaw(), fare));
		}
		out.sort(Comparator.comparingDouble(d -> d.keel().distanceToSqr(Vec3.atCenterOf(from))));
		return out.size() > DESTINATIONS ? List.copyOf(out.subList(0, DESTINATIONS)) : out;
	}

	/** {@code player} pressed "Buy Passage": where her captain will fly, and his fares; or, with nowhere to go, a shake of the head. */
	static void talk(ServerPlayer player, LivingEntity v) {
		ServerLevel level = player.level();
		Mooring m = mooring(level, player, v);
		if (m == null) {
			return;
		}
		int owner = owner(SteamTowns.all(level), m);
		List<Dest> dests = destinations(level, m, owner);
		if (dests.isEmpty()) {
			Speech.say(level, v, Component.translatable("talk.jaymoskingdoms.steam_captain.nowhere"), Speech.WHITE, 16.0);
			level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
			return;
		}
		Faction f = Factions.get(level.getServer(), m.town().id());
		Component words = Component.translatable("talk.jaymoskingdoms.steam_captain.ask");
		if (!ServerPlayNetworking.canSend(player, TalkNet.Ask.TYPE)) {
			Speech.tell(level, v, player, words, Speech.WHITE);
			return;
		}
		List<Component> options = new ArrayList<>();
		List<Boolean> enabled = new ArrayList<>();
		for (Dest d : dests) {
			options.add(Component.translatable("talk.jaymoskingdoms.steam_captain.option", d.name(), Currencies.asked(level.getServer(), f, d.fare())));
			enabled.add(Money.canPay(player, f, Money.worth(d.fare())));
		}
		options.add(Component.translatable("talk.jaymoskingdoms.work.close"));
		enabled.add(true);
		ASKED.put(player.getUUID(), new Asked(v.getId(), m.berth().helm(), owner, dests));
		ServerPlayNetworking.send(player, new TalkNet.Ask(v.getId(), v.getDisplayName(), words, options, enabled));
	}

	/** An answer to a steam captain's fares; false when it wasn't one. */
	static boolean answer(ServerPlayer player, TalkNet.Answer answer) {
		Asked asked = ASKED.get(player.getUUID());
		if (asked == null || asked.captain() != answer.entityId()) {
			return false;
		}
		ASKED.remove(player.getUUID());
		ServerLevel level = player.level();
		if (answer.option() < 0 || answer.option() >= asked.dests().size() || !(level.getEntity(answer.entityId()) instanceof Villager captain)
			|| !offers(player, captain)) {
			return true;
		}
		Mooring m = mooring(level, player, captain);
		if (m == null || !m.berth().helm().equals(asked.helm())) {
			return true;
		}
		Dest dest = asked.dests().get(answer.option());
		// the berth she'd make for may have been taken while he was asked
		List<Dest> now = destinations(level, m, asked.owner());
		if (now.stream().noneMatch(d -> d.town() == dest.town())) {
			Speech.tell(level, captain, player, Component.translatable("talk.jaymoskingdoms.steam_captain.taken"), Speech.WHITE);
			return true;
		}
		Faction f = Factions.get(level.getServer(), m.town().id());
		if (!Money.charge(player, f, Money.worth(dest.fare()))) {
			return true;
		}
		if (!launch(level, m, asked.owner(), captain, dest)) {
			// she wouldn't go (something's built against her): his fare back
			Money.pay(player, f, Money.worth(dest.fare()));
			Speech.tell(level, captain, player, Component.translatable("talk.jaymoskingdoms.steam_captain.fouled"), Speech.WHITE);
		}
		return true;
	}

	/** She casts off from {@code m} with {@code captain} at her wheel and everyone on her deck as her passengers, bound for {@code dest}. */
	private static boolean launch(ServerLevel level, Mooring m, int owner, Villager captain, Dest dest) {
		Ship ship = Fleet.castOff(level, m.berth().helm());
		if (ship == null) {
			return false;
		}
		Fleet.Stations stations = Fleet.stations(ship);
		if (!ship.flying() || stations.helm() == null) {
			ship.moor();
			return false;
		}
		ship.takeAboard(captain, stations.helm());
		long now = level.getGameTime();
		double cruise = Math.max(ship.getY(), dest.keel().y) + CRUISE_UP;
		ship.setAttached(TRIP, new Trip(owner, dest.town(), dest.keel(), dest.yaw(), cruise, UP, now));
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, ship.getBoundingBox().inflate(2.0))) {
			if (Ships.aboard(p) == ship) {
				p.setAttached(PASSENGER, new Passage(ship.getUUID(), now + LONGEST_FLIGHT * 2));
				p.resetFallDistance();
			}
		}
		if (owner >= 0) {
			SteamTowns.Town o = SteamTowns.get(level, owner);
			if (o != null) {
				SteamTowns.put(level, o.away(Optional.empty(), -1L).leaving(now).missing(-1L));
			}
		}
		Speech.say(level, captain, Component.translatable("talk.jaymoskingdoms.steam_captain.off"), Speech.WHITE, 24.0);
		level.playSound(null, ship.getX(), ship.getY() + 2.0, ship.getZ(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.0F, 1.2F);
		level.playSound(null, ship.getX(), ship.getY() + 2.0, ship.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.8F, 0.6F);
		return true;
	}

	// ---- flying her ----------------------------------------------------------------------------------------------------

	@Override
	public @Nullable Input helm(ServerLevel level, Ship ship) {
		Trip trip = ship.getAttached(TRIP);
		if (trip == null) {
			return this.before != null ? this.before.helm(level, ship) : null;
		}
		long now = level.getGameTime();
		Flight flight = FLIGHTS.computeIfAbsent(ship.getUUID(), id -> new Flight());
		if (now - trip.began() > LONGEST_FLIGHT || flight.down > DOWN_MOST) {
			// she can't get there: moored where she hangs, and her town told where she is
			arrived(level, ship, trip, false);
			return Input.EMPTY;
		}
		double dx = trip.target().x - ship.getX(), dz = trip.target().z - ship.getZ(), far = Math.sqrt(dx * dx + dz * dz);
		if (trip.leg() < DOWN && now % LOOK_EVERY == 0) {
			flight.ahead = groundAhead(level, ship) + CLEAR;
		}
		double cruise = Math.max(trip.cruise(), flight.ahead) + flight.extra;
		double want = cruise, cloth = 0.0;
		float yaw = ship.getYRot();
		boolean steam = false;
		switch (trip.leg()) {
			case UP -> {
				if (ship.getY() >= cruise - 1.0) {
					ship.setAttached(TRIP, trip.on(OUT));
				}
			}
			case OUT -> {
				yaw = Fleet.bearing(ship, trip.target().x, trip.target().z);
				cloth = far < EASE_FROM ? EASE : 1.0;
				steam = true;
				if (far < APPROACH) {
					want = Math.max(trip.target().y + ARRIVE_UP, flight.ahead) + flight.extra;
				}
				if (far <= OVER) {
					ship.setAttached(TRIP, trip.on(DOWN));
				}
			}
			default -> {
				// down onto the berth, hauled in over it, her head brought round to how she will lie
				flight.down++;
				yaw = trip.yaw();
				boolean square = Math.abs(Mth.wrapDegrees(yaw - ship.getYRot())) < SQUARE;
				want = far < PLUMB && square ? trip.target().y : trip.target().y + ARRIVE_UP;
				if (far > 0.02) {
					double pull = Math.min(HAUL, far);
					ship.tow(new Vec3(dx / far * pull, 0.0, dz / far * pull));
				}
				if (Math.abs(ship.getY() - trip.target().y) < 0.2 && far < 0.3 && Math.abs(ship.way()) < 0.02 && square) {
					arrived(level, ship, trip, true);
					return Input.EMPTY;
				}
			}
		}
		// held up by something in her way: over it
		if (trip.leg() == OUT && ship.drift().horizontalDistanceSqr() < 0.0004) {
			if (++flight.stuck > STUCK_TICKS) {
				flight.stuck = 0;
				flight.extra += OVER_STUCK;
			}
		} else {
			flight.stuck = 0;
		}
		if (steam) {
			float off = Mth.wrapDegrees(yaw - ship.getYRot());
			double push = Mth.clamp(STEAM_CRUISE * cloth - Math.max(0.0, ship.way()), 0.0, STEAM_MOST) * Math.max(0.0, Mth.cos(off * Mth.DEG_TO_RAD));
			if (push > 0.0) {
				float heading = ship.getYRot() * Mth.DEG_TO_RAD;
				ship.tow(new Vec3(-Mth.sin(heading) * push, 0.0, Mth.cos(heading) * push));
			}
			smoke(level, ship);
		}
		Input rudder = Fleet.steer(ship, yaw, cloth, false, false);
		boolean up = ship.aloft() < want - 0.15, down = ship.aloft() > want + 0.15;
		return new Input(rudder.forward(), rudder.backward(), rudder.left(), rudder.right(), up, false, down);
	}

	/** The highest ground (its top block's height) under her and at {@link #LOOK_AHEAD} along her heading, where it is loaded. */
	private static double groundAhead(ServerLevel level, Ship ship) {
		float heading = ship.getYRot() * Mth.DEG_TO_RAD;
		double sx = -Mth.sin(heading), sz = Mth.cos(heading);
		int top = level.getMinY();
		for (int i = -1; i < LOOK_AHEAD.length; i++) {
			int d = i < 0 ? 0 : LOOK_AHEAD[i];
			int x = Mth.floor(ship.getX() + sx * d), z = Mth.floor(ship.getZ() + sz * d);
			if (Loaded.made(level, x, z)) {
				top = Math.max(top, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z));
			}
		}
		return top;
	}

	/** Her boiler's smoke from her stern, and now and then the chuff of its exhaust. */
	private static void smoke(ServerLevel level, Ship ship) {
		Hull h = ship.hull();
		if (h == null || ship.tickCount % 4 != 0) {
			return;
		}
		Vec3 stern = ship.toWorld(0.0, h.minY + 2.5, h.minZ + 0.5);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, stern.x, stern.y, stern.z, 1, 0.15, 0.1, 0.15, 0.01);
		RandomSource random = level.getRandom();
		if (random.nextInt(12) == 0) {
			level.playSound(null, stern.x, stern.y, stern.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.25F, 0.5F + random.nextFloat() * 0.2F);
		}
	}

	/** Her flight is over: moored ({@code atBerth}: on the berth she was bound for), her captain calls the town, her town told where she lies. */
	private static void arrived(ServerLevel level, Ship ship, Trip trip, boolean atBerth) {
		List<LivingEntity> crew = ship.crewAboard();
		ship.removeAttached(TRIP);
		FLIGHTS.remove(ship.getUUID());
		ship.moor();
		long now = level.getGameTime();
		if (trip.owner() >= 0) {
			SteamTowns.Town o = SteamTowns.get(level, trip.owner());
			if (o != null) {
				SteamTowns.put(level, trip.to() == trip.owner() && atBerth ? o.away(Optional.empty(), -1L).leaving(-1L).missing(-1L)
					: o.away(Optional.of(trip.to()), now).leaving(-1L));
			}
		}
		SteamTowns.Town to = SteamTowns.get(level, trip.to());
		if (atBerth && to != null && !crew.isEmpty()) {
			Speech.say(level, crew.getFirst(), Component.translatable("talk.jaymoskingdoms.steam_captain.arrived", SteamTowns.name(level, to)), Speech.WHITE, 24.0);
		}
		level.playSound(null, ship.getX(), ship.getY() + 2.0, ship.getZ(), SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.0F, 0.9F);
	}

	// ---- the passengers --------------------------------------------------------------------------------------------------

	/**
	 * Every passenger: kept from falling, set back on her deck if they went over the rail while she flies, and let go
	 * once she has landed (or is lost) and they stand on the ground.
	 */
	private static void passengers(MinecraftServer server, long now) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Passage passage = p.getAttached(PASSENGER);
			if (passage == null) {
				continue;
			}
			p.resetFallDistance();
			Entity e = p.level().getEntity(passage.ship());
			if (e instanceof Ship ship && !ship.isRemoved() && ship.hasAttached(TRIP)) {
				if (Ships.aboard(p) != ship && !p.isPassenger()) {
					Vec3 deck = ship.helmStand();
					p.teleportTo(p.level(), deck.x, deck.y + 0.1, deck.z, Set.of(), p.getYRot(), p.getXRot(), false);
				}
				continue;
			}
			if ((p.onGround() || p.isInWater()) && Ships.aboard(p) == null || now > passage.until()) {
				p.removeAttached(PASSENGER);
			}
		}
	}
}
