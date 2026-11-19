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
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The steam towns' airships (GT New Horizons picks, patch 9; Andy: "maybe they have airships?"). Each steam town
 * ({@link SteamTowns}) keeps one on its berth at the airfield: a railed deck under a bag of wool, a balloon {@link Ship}
 * of the sailing code's kind, and her captain (the town's aeronaut, {@link SteamTrades}, marked {@link #CAPTAIN}) waits
 * on the landing stage beside her. Standing on her deck, a player buys passage from him on MCA's Interact page ("Buy
 * Passage"): to another steam town, or home if she lies away. She casts off with her captain at the wheel (hers while
 * she flies: no player may take it), climbs, and flies there on the wind with her boiler pushing her along
 * ({@link #STEAM_CRUISE}), going up over high ground ahead of her, then comes down onto that town's visitor berth (or her
 * own) and is blocks again. A flight that can't be finished, or a berth found blocked, brings her down onto the ground
 * wherever she is ({@link #SETTLE}). With no other steam town in the world, her captain shakes his head.
 * <p>
 * <b>The passenger</b> ({@link #PASSENGER}): everyone aboard her in flight is kept from harm while they are aboard and
 * for {@link #GRACE} after: no damage, no fall, and one who goes over the rail is caught and set back on her deck. It
 * ends at death and on leaving the Overworld.
 * <p>
 * <b>The way home.</b> Lying at another town, she waits there a day ({@link #AWAY_STAY}) for anyone wanting to fly back.
 * Then, the next time that berth is loaded and nobody is near, she is gone from it with her captain aboard, and her own
 * berth at home has them both again the next time it is loaded with nobody near ({@link #upkeep}): an empty flight never
 * loads a chunk. One brought down short of a berth is taken home the same way from where she lies.
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
	/** A flight that has taken this long (ticks) is given up and she comes down where she is; coming down onto her berth that long, the same. */
	static final long LONGEST_FLIGHT = 24000L;
	static final int DOWN_MOST = 1200;
	/** Coming down onto open ground: the longest it may take (ticks), and how long she may hang without sinking before she lies where she is. */
	static final int SETTLE_MOST = 1200, SETTLE_STALL = 40;
	/** Passage: her captain this near her wheel; the fare (emeralds' worth) and how it grows with distance; how many towns he names. */
	static final double CAPTAIN_REACH = 32.0;
	static final int FARE_BASE = 3, FARE_PER = 250, FARE_MOST = 20, DESTINATIONS = 3;
	/** A captain farther than this (blocks) from his place on the stage walks back to it, at this pace. */
	static final double CAPTAIN_STAY = 8.0;
	static final float CAPTAIN_WALK = 0.5F;
	/** How often the berths are seen to (ticks), and how near no player may be for a ship to come or go unseen. */
	static final int UPKEEP_EVERY = 200;
	static final double HIDE_RANGE = 64.0;
	/** She lies at another town this long before she goes home; a flight or a stay unheard of this long is given up; a berth found empty is laid again after this. */
	static final long AWAY_STAY = 24000L, LOST_AFTER = 120000L, REBUILD_TICKS = 48000L;
	/** How often (ticks) the passengers are looked after; how long their care outlasts their last tick aboard her in flight; how far off one is caught back. */
	static final int SAFE_EVERY = 5, GRACE = 200;
	static final double CATCH_REACH = 64.0;
	/** How far round where her wheel should be (blocks) it is looked for once she is moored. */
	static final int FIND_HELM = 4;

	/** The legs of a flight: up from her berth, across, down onto the berth she is bound for, down onto open ground. */
	static final int UP = 0, OUT = 1, DOWN = 2, SETTLE = 3;

	/**
	 * A flight: the town she belongs to (-1: none's), the town she is bound for, where her keel will lie there and how
	 * she will lie, the height she cruises at, the leg she is on, when she cast off (coming down on open ground: when she
	 * began to).
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

	/** A passenger: the airship they fly on, and when their care runs out (it is put off while they are aboard her in flight). */
	record Passage(UUID ship, long until) {
		static final Codec<Passage> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("ship").forGetter(Passage::ship),
				Codec.LONG.fieldOf("until").forGetter(Passage::until))
			.apply(i, Passage::new));
	}

	public static final AttachmentType<Trip> TRIP = AttachmentRegistry.create(JaymosKingdoms.id("steam_airship_trip"), builder -> builder.persistent(Trip.CODEC));
	public static final AttachmentType<Passage> PASSENGER = AttachmentRegistry.create(JaymosKingdoms.id("steam_airship_passenger"),
		builder -> builder.persistent(Passage.CODEC));
	/** An aeronaut's town, whose airship he captains wherever she lies. */
	public static final AttachmentType<Integer> CAPTAIN = AttachmentRegistry.create(JaymosKingdoms.id("steam_airship_captain"),
		builder -> builder.persistent(Codec.INT));

	/** One of a town's two berths: where her wheel stands, where her keel lies and how she lies, every block of her there, its corners, her captain's place. */
	record Berth(BlockPos helm, Vec3 keel, float yaw, List<BlockPos> blocks, BlockPos min, BlockPos max, BlockPos stage) {
		/** {@code player} stands on her deck (under her bag, inside her rails' square). */
		boolean holds(ServerPlayer player) {
			BlockPos at = player.blockPosition();
			return at.getX() >= this.min.getX() && at.getX() <= this.max.getX() && at.getZ() >= this.min.getZ() && at.getZ() <= this.max.getZ()
				&& player.getY() >= this.min.getY() + 0.9 && player.getY() < this.min.getY() + 4.0;
		}
	}

	/** Where a captain will fly: the town, her keel's place and heading there, the fare. */
	record Dest(int town, String name, Vec3 keel, float yaw, int fare) {
	}

	/** A player asked a captain: him, the wheel of the ship he'd fly, the town she belongs to, and where he named. */
	private record Asked(int captain, BlockPos helm, int owner, List<Dest> dests) {
	}

	/**
	 * Each flight under way: how long she has made no way (ticks), how much higher she has gone for it, how high the
	 * ground ahead wants her, how long she has been coming down; coming down on open ground, where she will lie and how
	 * long she has hung without sinking.
	 */
	private static final class Flight {
		int stuck, extra, down, stall;
		double ahead = Double.NEGATIVE_INFINITY, land = Double.NaN, lastY = Double.NaN;
	}

	private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
	private static final Map<UUID, Asked> ASKED = new HashMap<>();
	/** Who flies what isn't ours (the sky folk's ferries). */
	private Ship.@Nullable Skyfaring before;

	private SteamAirships() {
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("airship attachments: {} {} {}", TRIP, PASSENGER, CAPTAIN);
		SteamAirships pilot = new SteamAirships();
		pilot.before = Ship.skyfaring;
		Ship.skyfaring = pilot;
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof ServerPlayer p && guarded(p) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)));
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
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Ship ship) {
				FLIGHTS.remove(ship.getUUID());
				Trip trip = ship.getAttached(TRIP);
				if (trip != null && ship.getRemovalReason() != null && ship.getRemovalReason().shouldDestroy()) {
					// wrecked in flight: her town builds another in time
					wrecked(Factions.level(level.getServer()), trip);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FLIGHTS.clear();
			ASKED.clear();
		});
	}

	@Override
	public boolean keepsWheel(Ship ship) {
		return ship.hasAttached(TRIP) || this.before != null && this.before.keepsWheel(ship);
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
		int[] stage = home ? SteamBuildings.HOME_STAGE : SteamBuildings.VISITOR_STAGE;
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
		int wx0 = Integer.MAX_VALUE, wx1 = Integer.MIN_VALUE, wz0 = Integer.MAX_VALUE, wz1 = Integer.MIN_VALUE, wy1 = Integer.MIN_VALUE;
		for (BlockPos p : blocks) {
			y0 = Math.min(y0, p.getY());
			wy1 = Math.max(wy1, p.getY());
			wx0 = Math.min(wx0, p.getX());
			wx1 = Math.max(wx1, p.getX());
			wz0 = Math.min(wz0, p.getZ());
			wz1 = Math.max(wz1, p.getZ());
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
		return new Berth(helm, keel, facing.toYRot(), blocks, new BlockPos(wx0, y0, wz0), new BlockPos(wx1, wy1, wz1), field.at(stage[0], stage[1], stage[2]));
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

	/** {@code town}'s airship lies on her home berth (loaded) with her captain about, not stowed aboard: no aeronaut is wanted in his place. */
	static boolean home(ServerLevel level, SteamTowns.Town town) {
		Berth b = berth(town, true);
		return town.captain().isEmpty() && Loaded.made(level, b.helm()) && moored(level, b);
	}

	private static boolean moored(ServerLevel level, Berth berth) {
		return level.getBlockState(berth.helm()).getBlock() instanceof HelmBlock;
	}

	/** Every chunk under the berth is made and loaded, so reading it waits on nothing. */
	private static boolean loaded(ServerLevel level, Berth berth) {
		BlockPos a = berth.min(), b = berth.max();
		return Loaded.made(level, a.getX(), a.getZ()) && Loaded.made(level, b.getX(), b.getZ()) && Loaded.made(level, a.getX(), b.getZ())
			&& Loaded.made(level, b.getX(), a.getZ());
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

	/** A moored airship taken off where she lies unseen (she has gone home): her blocks lifted out of the world and gone; false if they wouldn't come away. */
	private static boolean putAway(ServerLevel level, BlockPos helm) {
		Ship ship = Fleet.castOff(level, helm);
		if (ship == null) {
			return false;
		}
		ship.discard();
		return true;
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

	// ---- her captain ---------------------------------------------------------------------------------------------------

	/** {@code town}'s airship captain within {@code reach} of {@code near}, nearest first, or null. */
	private static @Nullable Villager captain(ServerLevel level, int town, BlockPos near, double reach) {
		Integer id = town;
		List<Villager> found = level.getEntitiesOfClass(Villager.class, new AABB(near).inflate(reach),
			v -> v.isAlive() && id.equals(v.getAttached(CAPTAIN)) && SteamTrades.aeronaut(v));
		return found.stream().min(Comparator.comparingDouble(v -> v.distanceToSqr(Vec3.atCenterOf(near)))).orElse(null);
	}

	/** {@code town}'s captain near {@code near} taken aboard her as she goes home unseen: his body, kept on the town until her berth has her again. */
	private static Optional<CompoundTag> stow(ServerLevel level, int town, BlockPos near, double reach) {
		Villager v = captain(level, town, near, reach);
		if (v == null) {
			return Optional.empty();
		}
		v.stopRiding();
		v.ejectPassengers();
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		if (!v.saveAsPassenger(out)) {
			return Optional.empty();
		}
		CompoundTag body = out.buildResult();
		v.discard();
		return Optional.of(body);
	}

	/** {@code town}'s stowed captain back on his stage beside her, home again. */
	private static void restore(ServerLevel level, SteamTowns.Town town, Berth berth) {
		if (town.captain().isEmpty()) {
			return;
		}
		Vec3 at = Vec3.atBottomCenterOf(berth.stage());
		Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), town.captain().get()), level,
			EntitySpawnReason.LOAD, e -> {
				e.snapTo(at.x, at.y, at.z, e.getYRot(), e.getXRot());
				return e;
			});
		if (loaded instanceof Villager v && level.getEntity(v.getUUID()) == null) {
			level.addFreshEntity(v);
		}
	}

	/** {@code town}'s captain, wandered off from his place by {@code berth}, walks back to it. */
	private static void standBy(ServerLevel level, int town, Berth berth, BlockPos seat) {
		Villager v = captain(level, town, seat, SteamTowns.TOWN_REACH);
		if (v != null && !v.isSleeping() && !v.isPassenger() && v.getTradingPlayer() == null
			&& v.distanceToSqr(Vec3.atBottomCenterOf(berth.stage())) > CAPTAIN_STAY * CAPTAIN_STAY) {
			v.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(berth.stage(), CAPTAIN_WALK, 1));
		}
	}

	// ---- upkeep ----------------------------------------------------------------------------------------------------------

	/**
	 * Every loaded airfield and every airship brought down short of a berth: a visitor whose stay is over, or one lying
	 * out in the country, is put away unseen with her captain aboard and her own town told she is coming home; an empty
	 * home berth is laid again unseen (her captain back on his stage) once its ship is home, or (wrecked, stolen, burnt,
	 * or unheard of for {@link #LOST_AFTER}) after {@link #REBUILD_TICKS}. Records are cleared only for what has really
	 * happened.
	 */
	static void upkeep(ServerLevel level, long now) {
		for (SteamTowns.Town t : SteamTowns.all(level)) {
			if (t.lost().isPresent()) {
				stranded(level, t, now);
			}
		}
		for (SteamTowns.Town listed : SteamTowns.all(level)) {
			SteamTowns.Town t = SteamTowns.get(level, listed.id());
			Faction f = Factions.get(level.getServer(), listed.id());
			if (t == null || f == null) {
				continue;
			}
			Berth home = berth(t, true), visitor = berth(t, false);
			if (!level.isPositionEntityTicking(home.helm()) || !level.isPositionEntityTicking(visitor.helm()) || !loaded(level, home) || !loaded(level, visitor)) {
				continue;
			}
			boolean seen = Raids.playerNear(level, home.helm(), HIDE_RANGE);
			SteamTowns.Town owner = visiting(SteamTowns.all(level), t.id());
			if (owner != null) {
				if (!moored(level, visitor)) {
					// she isn't where she was left (taken, or broken up): her town builds another in time
					SteamTowns.put(level, owner.home().missing(now));
				} else if (!seen && now - owner.awaySince() > AWAY_STAY) {
					if (putAway(level, visitor.helm())) {
						Optional<CompoundTag> body = stow(level, owner.id(), f.seat(), SteamTowns.TOWN_REACH);
						SteamTowns.put(level, owner.home().missing(now - REBUILD_TICKS).stowed(body.isPresent() ? body : owner.captain()));
					}
				} else {
					standBy(level, owner.id(), visitor, f.seat());
				}
			}
			t = SteamTowns.get(level, t.id());
			if (t == null) {
				continue;
			}
			if (moored(level, home)) {
				SteamTowns.Town next = t.home();
				if (t.captain().isPresent() && !seen) {
					restore(level, t, home);
					next = next.stowed(Optional.empty());
				}
				if (!next.equals(t)) {
					SteamTowns.put(level, next);
				}
				standBy(level, t.id(), home, f.seat());
				continue;
			}
			boolean flying = t.left() >= 0 && now - t.left() < LOST_AFTER;
			boolean away = t.awayAt().isPresent() && now - t.awaySince() < LOST_AFTER;
			if (flying || away || t.lost().isPresent()) {
				continue;
			}
			// unheard of from a flight or a stay this long: given up, and built again; gone with no word of her: built again in time
			boolean accounted = t.left() >= 0 || t.awayAt().isPresent();
			long gone = accounted ? now - REBUILD_TICKS : t.gone() >= 0 ? t.gone() : now;
			if (t.gone() != gone || accounted) {
				t = t.home().missing(gone);
				SteamTowns.put(level, t);
			}
			if (now - gone >= REBUILD_TICKS && !seen && clear(level, home)) {
				lay(level, t);
				restore(level, t, home);
				SteamTowns.put(level, t.home().stowed(Optional.empty()));
			}
		}
	}

	/** {@code t}'s airship lying out in the country: put away unseen and her captain with her, or, gone from there, built again in time. */
	private static void stranded(ServerLevel level, SteamTowns.Town t, long now) {
		BlockPos wheel = t.lost().orElseThrow();
		if (!level.isPositionEntityTicking(wheel)) {
			if (now - t.lostSince() > LOST_AFTER) {
				SteamTowns.put(level, t.stranded(Optional.empty(), -1L).missing(now));
			}
			return;
		}
		if (Raids.playerNear(level, wheel, HIDE_RANGE)) {
			return;
		}
		if (level.getBlockState(wheel).getBlock() instanceof HelmBlock) {
			if (!putAway(level, wheel)) {
				// built onto, or grown into: left where she lies until she comes away
				return;
			}
			Optional<CompoundTag> body = stow(level, t.id(), wheel, CAPTAIN_REACH);
			SteamTowns.put(level, t.stranded(Optional.empty(), -1L).missing(now - REBUILD_TICKS).stowed(body.isPresent() ? body : t.captain()));
		} else {
			Optional<CompoundTag> body = stow(level, t.id(), wheel, CAPTAIN_REACH);
			SteamTowns.put(level, t.stranded(Optional.empty(), -1L).missing(now).stowed(body.isPresent() ? body : t.captain()));
		}
	}

	/** Her flight ended with her wrecked: her town builds another in time. */
	private static void wrecked(ServerLevel level, Trip trip) {
		SteamTowns.Town o = trip.owner() >= 0 ? SteamTowns.get(level, trip.owner()) : null;
		if (o != null) {
			SteamTowns.put(level, o.home().missing(level.getGameTime()));
		}
	}

	// ---- passage -------------------------------------------------------------------------------------------------------

	/** A moored airship {@code player} stands on the deck of with her own captain near her wheel: her town's berth, whether it's her home, the berth, the town she belongs to. */
	private record Mooring(SteamTowns.Town town, boolean home, Berth berth, int owner) {
	}

	private static @Nullable Mooring mooring(ServerLevel level, ServerPlayer player, LivingEntity captain) {
		SteamTowns.Town town = SteamTowns.at(level, player.blockPosition());
		if (town == null) {
			return null;
		}
		for (boolean home : new boolean[] {true, false}) {
			Berth b = berth(town, home);
			if (!b.holds(player) || !moored(level, b)) {
				continue;
			}
			SteamTowns.Town visitor = home ? null : visiting(SteamTowns.all(level), town.id());
			int owner = home ? town.id() : visitor == null ? -1 : visitor.id();
			Integer his = captain.getAttached(CAPTAIN);
			if (owner >= 0 && his != null && his == owner && captain.position().distanceToSqr(Vec3.atCenterOf(b.helm())) <= CAPTAIN_REACH * CAPTAIN_REACH) {
				return new Mooring(town, home, b, owner);
			}
		}
		return null;
	}

	/** "Buy Passage" shows on {@code v}'s Interact page: a steam town's aeronaut, with {@code player} on the deck of his moored airship. */
	static boolean offers(ServerPlayer player, LivingEntity v) {
		return v instanceof Villager villager && SteamTrades.aeronaut(villager) && !villager.isBaby() && mooring(player.level(), player, v) != null;
	}

	/** Where she can fly from {@code m}: home if she lies away, else every other steam town's visitor berth not taken or blocked, nearest first. */
	private static List<Dest> destinations(ServerLevel level, Mooring m) {
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
			boolean home = t.id() == m.owner();
			if (!home && (visiting(towns, t.id()) != null || bound.contains(t.id()))) {
				continue;
			}
			Berth b = berth(t, home);
			// a berth seen to be built over is no place to come down (one out of sight is found out on the way)
			if (loaded(level, b) && !clear(level, b)) {
				continue;
			}
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
		List<Dest> dests = destinations(level, m);
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
		ASKED.put(player.getUUID(), new Asked(v.getId(), m.berth().helm(), m.owner(), dests));
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
			|| !SteamTrades.aeronaut(captain)) {
			return true;
		}
		// still on her deck, she still on that berth, and still his
		Mooring m = mooring(level, player, captain);
		if (m == null || !m.berth().helm().equals(asked.helm()) || m.owner() != asked.owner()) {
			return true;
		}
		Dest dest = asked.dests().get(answer.option());
		// the berth she'd make for may have been taken while he was asked
		if (destinations(level, m).stream().noneMatch(d -> d.town() == dest.town())) {
			Speech.tell(level, captain, player, Component.translatable("talk.jaymoskingdoms.steam_captain.taken"), Speech.WHITE);
			return true;
		}
		Faction f = Factions.get(level.getServer(), m.town().id());
		if (!Money.charge(player, f, Money.worth(dest.fare()))) {
			return true;
		}
		if (!launch(level, m, captain, dest)) {
			// she wouldn't go (something's built against her): his fare back
			Money.pay(player, f, Money.worth(dest.fare()));
			Speech.tell(level, captain, player, Component.translatable("talk.jaymoskingdoms.steam_captain.fouled"), Speech.WHITE);
		}
		return true;
	}

	/** She casts off from {@code m} with {@code captain} at her wheel and everyone on her deck as her passengers, bound for {@code dest}. */
	private static boolean launch(ServerLevel level, Mooring m, Villager captain, Dest dest) {
		Ship ship = Fleet.castOff(level, m.berth().helm());
		if (ship == null) {
			return false;
		}
		Fleet.Stations stations = Fleet.stations(ship);
		if (!ship.flying() || stations.helm() == null || !ship.takeAboard(captain, stations.helm())) {
			ship.moor();
			return false;
		}
		long now = level.getGameTime();
		double cruise = Math.max(ship.getY(), dest.keel().y) + CRUISE_UP;
		ship.setAttached(TRIP, new Trip(m.owner(), dest.town(), dest.keel(), dest.yaw(), cruise, UP, now));
		care(level, ship, now);
		SteamTowns.Town o = SteamTowns.get(level, m.owner());
		if (o != null) {
			SteamTowns.put(level, o.home().leaving(now));
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
		if (!Fleet.wheelManned(ship)) {
			ship.letDrift();
			if (Fleet.helmsmanDown(ship)) {
				// her captain downed at her wheel: she drifts on the wind while he's down, and flies on when he's up at it again
				return null;
			}
			// her captain dead: she is let go, drifts on the wind and comes down; her town builds another in time
			ship.removeAttached(TRIP);
			FLIGHTS.remove(ship.getUUID());
			wrecked(level, trip);
			return null;
		}
		Flight flight = FLIGHTS.computeIfAbsent(ship.getUUID(), id -> new Flight());
		if (now % SAFE_EVERY == 0) {
			care(level, ship, now);
		}
		if (trip.leg() == SETTLE) {
			return settle(level, ship, trip, flight, now);
		}
		if (now - trip.began() > LONGEST_FLIGHT || flight.down > DOWN_MOST) {
			// she can't get there: down onto the ground where she is
			giveUp(ship, trip, flight, now);
			return Input.EMPTY;
		}
		double dx = trip.target().x - ship.getX(), dz = trip.target().z - ship.getZ(), far = Math.sqrt(dx * dx + dz * dz);
		if (trip.leg() < DOWN && now % LOOK_EVERY == 0) {
			flight.ahead = groundAhead(level, ship) + CLEAR;
			if (far < APPROACH && blocked(level, trip)) {
				// her berth built over: she comes down short of it
				giveUp(ship, trip, flight, now);
				return Input.EMPTY;
			}
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
					if (!lie(level, ship, trip, berthOf(level, trip))) {
						// no room for her there after all
						giveUp(ship, trip, flight, now);
					}
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

	/** The berth {@code trip} is bound for, or null if its town is no more. */
	private static @Nullable Berth berthOf(ServerLevel level, Trip trip) {
		SteamTowns.Town t = SteamTowns.get(level, trip.to());
		return t == null ? null : berth(t, trip.to() == trip.owner());
	}

	/** The berth she is bound for is in sight and built over. */
	private static boolean blocked(ServerLevel level, Trip trip) {
		Berth b = berthOf(level, trip);
		return b == null || loaded(level, b) && !clear(level, b);
	}

	/** She gives up on her berth and comes down onto the ground where she is. */
	private static void giveUp(Ship ship, Trip trip, Flight flight, long now) {
		ship.setAttached(TRIP, new Trip(trip.owner(), trip.to(), ship.position(), ship.getYRot(), trip.cruise(), SETTLE, now));
		flight.down = 0;
		flight.extra = 0;
		flight.stall = 0;
		flight.land = Double.NaN;
		flight.lastY = Double.NaN;
	}

	/** Coming down on open ground: sinking onto it (a block of air under her keel, so she comes away again), then moored there. */
	private static Input settle(ServerLevel level, Ship ship, Trip trip, Flight flight, long now) {
		Hull h = ship.hull();
		if (h == null) {
			return Input.EMPTY;
		}
		if (now - trip.began() > SETTLE_MOST) {
			// nowhere she will lie: let go to the wind (she moors herself where she drifts), and her town builds another in time
			ship.removeAttached(TRIP);
			FLIGHTS.remove(ship.getUUID());
			wrecked(level, trip);
			return Input.EMPTY;
		}
		if (Double.isNaN(flight.land) || now % LOOK_EVERY == 0) {
			flight.land = landing(level, ship, h) + flight.extra;
		}
		flight.stall = Math.abs(ship.getY() - flight.lastY) < 0.01 ? flight.stall + 1 : 0;
		flight.lastY = ship.getY();
		if (ship.getY() - flight.land < 0.3 && Math.abs(ship.way()) < 0.05 || flight.stall > SETTLE_STALL) {
			if (!lie(level, ship, trip, berthOf(level, trip))) {
				// no room for her here: a block higher
				flight.extra++;
				flight.stall = 0;
			}
			return Input.EMPTY;
		}
		Input rudder = Fleet.steer(ship, ship.getYRot(), 0.0, false, false);
		boolean up = ship.aloft() < flight.land - 0.15, down = ship.aloft() > flight.land + 0.15;
		return new Input(rudder.forward(), rudder.backward(), rudder.left(), rudder.right(), up, false, down);
	}

	/** Where her keel lies coming down here: a block over the highest ground under any corner of her or her middle. */
	private static double landing(ServerLevel level, Ship ship, Hull h) {
		int top = level.getMinY();
		for (double[] c : new double[][] {{h.minX, h.minZ}, {h.maxX, h.minZ}, {h.minX, h.maxZ}, {h.maxX, h.maxZ}, {0.0, 0.0}, {0.0, h.minZ}, {0.0, h.maxZ}}) {
			Vec3 at = ship.toWorld(c[0], 0.0, c[1]);
			int x = Mth.floor(at.x), z = Mth.floor(at.z);
			if (Loaded.made(level, x, z)) {
				top = Math.max(top, Stacked.top(level, Heightmap.Types.MOTION_BLOCKING, x, z, ship.getBlockY()));
			}
		}
		return top + 1 - h.minY;
	}

	/** The highest ground (its top block's height) under her and at {@link #LOOK_AHEAD} along her heading, where it is loaded. */
	static double groundAhead(ServerLevel level, Ship ship) {
		float heading = ship.getYRot() * Mth.DEG_TO_RAD;
		double sx = -Mth.sin(heading), sz = Mth.cos(heading);
		int top = level.getMinY();
		for (int i = -1; i < LOOK_AHEAD.length; i++) {
			int d = i < 0 ? 0 : LOOK_AHEAD[i];
			int x = Mth.floor(ship.getX() + sx * d), z = Mth.floor(ship.getZ() + sz * d);
			if (Loaded.made(level, x, z)) {
				top = Math.max(top, Stacked.top(level, Heightmap.Types.MOTION_BLOCKING, x, z, ship.getBlockY()));
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

	/** Where her wheel is in the world now, or null if she has none. */
	private static @Nullable BlockPos wheel(Ship ship) {
		Hull h = ship.hull();
		if (h == null || h.helm < 0) {
			return null;
		}
		Ship.Piece p = h.pieces.get(h.helm);
		return BlockPos.containing(ship.toWorld(p.x(), p.y(), p.z()));
	}

	/** The helm nearest {@code guess} once she is moored (she may lie a block or two off where she hung), or null. */
	private static @Nullable BlockPos findHelm(ServerLevel level, @Nullable BlockPos guess) {
		if (guess == null) {
			return null;
		}
		BlockPos best = null;
		for (BlockPos p : BlockPos.betweenClosed(guess.offset(-FIND_HELM, -1, -FIND_HELM), guess.offset(FIND_HELM, 1, FIND_HELM))) {
			if (level.getBlockState(p).getBlock() instanceof HelmBlock && (best == null || p.distSqr(guess) < best.distSqr(guess))) {
				best = p.immutable();
			}
		}
		return best;
	}

	/**
	 * Her flight is over: moored where she is, and her town told where she really lies: on the berth she was bound for
	 * ({@code berth}, if she lies exactly on it), else out in the country at her wheel. False (and her course kept) if
	 * there was no room for her.
	 */
	private static boolean lie(ServerLevel level, Ship ship, Trip trip, @Nullable Berth berth) {
		List<LivingEntity> crew = ship.crewAboard();
		BlockPos guess = wheel(ship);
		Vec3 at = ship.position();
		ship.removeAttached(TRIP);
		if (!ship.moor()) {
			ship.setAttached(TRIP, trip);
			return false;
		}
		FLIGHTS.remove(ship.getUUID());
		BlockPos helm = findHelm(level, guess);
		boolean atBerth = berth != null && berth.helm().equals(helm);
		long now = level.getGameTime();
		SteamTowns.Town o = trip.owner() >= 0 ? SteamTowns.get(level, trip.owner()) : null;
		if (o != null) {
			SteamTowns.Town next = o.home();
			if (atBerth && trip.to() != trip.owner()) {
				next = next.away(Optional.of(trip.to()), now);
			} else if (!atBerth) {
				next = helm != null ? next.stranded(Optional.of(helm), now) : next.missing(now);
			}
			SteamTowns.put(level, next);
		}
		SteamTowns.Town to = SteamTowns.get(level, trip.to());
		if (atBerth && to != null && !crew.isEmpty()) {
			Speech.say(level, crew.getFirst(), Component.translatable("talk.jaymoskingdoms.steam_captain.arrived", SteamTowns.name(level, to)), Speech.WHITE, 24.0);
		}
		level.playSound(null, at.x, at.y + 2.0, at.z, SoundEvents.BELL_BLOCK, SoundSource.NEUTRAL, 1.0F, 0.9F);
		return true;
	}

	// ---- the passengers --------------------------------------------------------------------------------------------------

	/** {@code player}'s care as a passenger holds now. */
	private static boolean guarded(ServerPlayer player) {
		Passage passage = player.getAttached(PASSENGER);
		return passage != null && player.level().getGameTime() <= passage.until();
	}

	/** Everyone aboard her in flight is a passenger, cared for until {@link #GRACE} from now. */
	private static void care(ServerLevel level, Ship ship, long now) {
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, ship.getBoundingBox().inflate(2.0))) {
			if (p.isAlive() && !p.isSpectator() && Ships.aboard(p) == ship) {
				p.setAttached(PASSENGER, new Passage(ship.getUUID(), now + GRACE));
				p.resetFallDistance();
			}
		}
	}

	/**
	 * Every passenger: kept from falling, and set back on her deck if they went over the rail while she flies; their
	 * care ends {@link #GRACE} after they were last aboard her in flight, at death, or on leaving the Overworld.
	 */
	private static void passengers(MinecraftServer server, long now) {
		ServerLevel level = Factions.level(server);
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			Passage passage = p.getAttached(PASSENGER);
			if (passage == null) {
				continue;
			}
			if (now > passage.until() || !p.isAlive() || p.level() != level) {
				p.removeAttached(PASSENGER);
				continue;
			}
			p.resetFallDistance();
			if (level.getEntity(passage.ship()) instanceof Ship ship && !ship.isRemoved() && ship.hasAttached(TRIP) && Ships.aboard(p) != ship
				&& !p.isPassenger() && p.distanceToSqr(ship) < CATCH_REACH * CATCH_REACH) {
				Vec3 deck = ship.helmStand();
				p.teleportTo(level, deck.x, deck.y + 0.1, deck.z, Set.of(), p.getYRot(), p.getXRot(), false);
			}
		}
	}
}
