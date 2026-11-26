package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.power.zfighter.pod.PodEntity;
import com.jaymospowers.power.zfighter.pod.PodClass;
import com.jaymospowers.power.zfighter.pod.PodGrades;
import com.jaymospowers.power.zfighter.pod.PodLegs;
import com.jaymospowers.power.zfighter.pod.PodBeacons;
import com.jaymospowers.power.zfighter.pod.PodParts;
import com.jaymospowers.power.zfighter.pod.PodClimb;
import com.jaymospowers.power.zfighter.pod.PodFlight;
import com.jaymospowers.power.zfighter.pod.PodPost;
import com.jaymospowers.power.zfighter.pod.PodReturn;
import com.jaymospowers.power.zfighter.pod.PodSpace;
import com.jaymospowers.power.zfighter.pod.PodTow;
import com.jaymospowers.power.zfighter.pod.PodPart;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.4, Andy) A trip in the space pod, which you now ride: the pod entity ({@link PodEntity}, tagged {@link #TAG} for the trip)
 * takes you in, and it counts down shaking and smoking, then lifts off on its fire with you
 * inside while everyone near watches it go. Then the pilot flies it: up through the sky bands of the world they leave ({@link PodClimb}),
 * and across the dark between the worlds ({@link PodSpace}, shown by {@code PodFlightScreen}), or lets the autopilot do it.
 * Then the pod comes down out of the sky over open ground anywhere on the far world (no landing pad: {@link #ground}), slowing
 * on its thrusters, and sets down; it stands there holding the fuel left, and you climb out beside it. (0.18.50) It is the real
 * pod with the parts it was launched with: one that isn't whole lands hard ({@link #HARD_LANDING_DAMAGE}) and shakes
 * {@link #SHAKEN_MIN} to {@link #SHAKEN_MAX} parts loose.
 * <p>
 * You can't climb out mid-trip ({@code ZFighterPodRideMixin}) and nothing hurts you. Leaving the game mid-trip ends it at once:
 * before the lift the pod stands where it was with its fuel back; after it, the pod is set down where it was going and you wake
 * beside it when you come back ({@link #PENDING}).
 */
public final class ZFighterPodTrip {
	// ---- tuning ----
	/** The countdown (ticks), the lift (ticks), how fast the lift speeds up and its top speed (blocks a tick). */
	static final int COUNTDOWN_TICKS = 60, LIFT_TICKS = 70;
	static final double LIFT_ACCEL = 0.03, LIFT_TOP = 1.6;
	/** How far over the world's top a descent starts from (blocks). */
	static final int CRUISE_OVER_TOP = PodFlight.CLIMB_OVER_TOP;
	/** The descent: how high over the ground it starts, its top and bottom speed, how quickly it slows, and when it gives up. */
	static final int DESCENT_HEIGHT = 56, DESCENT_MAX_TICKS = 400;
	static final double DESCENT_FAST = 1.2, DESCENT_SLOW = 0.12, DESCENT_EASE = 0.05;
	/** (0.18.50) A pod that isn't whole lands hard: the pilot takes this much fall damage and this many parts (not the keel) come off. */
	static final float HARD_LANDING_DAMAGE = 6.0F;
	static final int SHAKEN_MIN = 1, SHAKEN_MAX = 2;
	/** How big a pod is to find room for it (blocks): its width and height. */
	static final double POD_WIDTH = 2.0, POD_HEIGHT = 3.5;
	/** Where on a far world the pod may come down: within this many blocks of the middle, tried this many times. */
	static final int LAND_RANGE = 360, LAND_TRIES = 40;

	/** (0.18.52) For the pod package: tells Kingdoms {@code player} did a deed that opens a world tier (see {@code ZFighterKingdoms.deed}). */
	public static void deed(ServerPlayer player, String deed) {
		ZFighterKingdoms.deed(player, deed);
	}

	/** The tag on the pod you ride. */
	public static final String TAG = "jaymospowers.space_pod";

	enum Phase {
		COUNTDOWN, LIFT,
		/** Patch 9: the pilot flies the pod up through the sky bands, in the real world ({@link PodClimb}). */
		CLIMB,
		/** Patch 9: the crossing between the worlds, a flight space the server simulates ({@link PodSpace}). */
		SPACE,
		DESCENT,
		/** (0.20.36) Caught in the high sky by a raider band ({@link ZFighterHighSky}): the pod hangs struck, the pilot is out fighting. */
		AMBUSH
	}

	/** One trip: where to (a destination index or Home), the fuel it will leave in the pod, the parts it carries, and where it is in the flight. */
	private static final class Trip {
		int dest;
		int fuelLeft;
		final int fuelBefore;
		/** (0.18.50) The grade of the fuel in the tank, carried to the pod that lands. */
		int fuelGrade;
		final ResourceKey<Level> from;
		final BlockPos origin;
		Phase phase = Phase.COUNTDOWN;
		int ticks = COUNTDOWN_TICKS;
		double speed;
		@Nullable PodEntity pod;
		int filled, damaged;
		/** (0.18.51) Whether the pod was finished, its mend grade, and the passenger a second seat carries (they ride and land with the pilot). */
		boolean finished;
		int grade;
		/** (0.18.56) A Starlit Lens on the mast, carried to the pod that lands. */
		boolean lens;
		/** What else the pod carries (sockets, modules, forging, the hold), the share of the flight its class takes, and whether it has landing legs. */
		PodEntity.@Nullable Extras extras;
		float flightScale = 1.0F;
		boolean legs = true;
		@Nullable UUID guest;
		@Nullable ResourceKey<Level> to;
		@Nullable BlockPos ground;
		/** (0.20.36) An ambush in the high sky: its raiders, whether they are beaten (or gone), and whether the pod ran with them on it. */
		final List<UUID> raiders = new ArrayList<>();
		boolean cleared;
		boolean battered;
		/** Patch 9: the climb, the crossing, whether the autopilot flies, whether the high-sky ambush has been rolled, whether the pod crashed, what it tows. */
		PodClimb.@Nullable Flight climb;
		@Nullable PodSpace sim;
		boolean auto = true;
		boolean ambushRolled;
		boolean crashed;
		PodTow.@Nullable Towed towed;

		Trip(int dest, int fuelLeft, int fuelBefore, ResourceKey<Level> from, BlockPos origin) {
			this.dest = dest;
			this.fuelLeft = fuelLeft;
			this.fuelBefore = fuelBefore;
			this.from = from;
			this.origin = origin;
		}
	}

	/** Where a player who left the game mid-trip wakes: the dimension and the spot beside their pod. */
	public record Pending(String dimension, BlockPos pos) {
		public static final Codec<Pending> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("dimension").forGetter(Pending::dimension),
			BlockPos.CODEC.fieldOf("pos").forGetter(Pending::pos)).apply(i, Pending::new));
	}

	public static final AttachmentType<Pending> PENDING = AttachmentRegistry.create(JaymosPowers.id("zfighter_space_pending"),
		builder -> builder.persistent(Pending.CODEC));
	/** How many trips a player has flown: after the first, the flight can be skipped. */
	public static final AttachmentType<Integer> FLIGHTS = AttachmentRegistry.create(JaymosPowers.id("zfighter_space_flights"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	/** Server → client: the flight is under way; show it. {@code to} is a destination index or Home. */
	/** (Lanterns) {@code ki}: flown on the fighter's own ki ({@link KiVoyage}), no pod. */
	public record Flight(int to, int ticks, boolean skippable, boolean lens, boolean ki) implements CustomPacketPayload {
		public static final Type<Flight> TYPE = new Type<>(JaymosPowers.id("zfighter_space_flight"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Flight> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.to + 1);
				buf.writeVarInt(p.ticks);
				buf.writeBoolean(p.skippable);
				buf.writeBoolean(p.lens);
				buf.writeBoolean(p.ki);
			},
			buf -> new Flight(buf.readVarInt() - 1, buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));

		@Override
		public Type<Flight> type() {
			return TYPE;
		}
	}

	private static final Map<UUID, Trip> TRIPS = new HashMap<>();
	/** (0.18.51) Passengers of a second seat, by who is aboard, to their pilot. */
	private static final Map<UUID, UUID> GUESTS = new HashMap<>();
	/** Set while the hard landing hurts the pilot (a trip's pilot is otherwise unhurtable). */
	private static boolean hurting;

	private ZFighterPodTrip() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Flight.TYPE, Flight.CODEC);
		// (0.20.36) nothing hurts you on a trip, but in an ambush you are out of the pod and fair game
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> hurting
			|| !(entity instanceof ServerPlayer player && (shielded(player) || KiVoyage.voyaging(player))));
		KiVoyage.init();
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				Trip trip = TRIPS.get(player.getUUID());
				if (trip != null && trip.phase == Phase.AMBUSH) {
					TRIPS.remove(player.getUUID());
					lost(player.level().getServer(), trip);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> leave(handler.getPlayer()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> wake(handler.getPlayer()));
		// (a pod left over from a trip that no longer runs loses its trip tag when it loads: see PodEntity.init)
	}

	/** Whether {@code player} is on a trip (seated, lifting, flying or coming down). */
	public static boolean flying(ServerPlayer player) {
		return TRIPS.containsKey(player.getUUID()) || GUESTS.containsKey(player.getUUID());
	}

	/** (0.20.36) Whether {@code player} is on a trip and inside it (not thrown out into an ambush). */
	private static boolean shielded(ServerPlayer player) {
		Trip trip = TRIPS.get(player.getUUID());
		if (trip == null) {
			UUID pilot = GUESTS.get(player.getUUID());
			if (pilot == null) {
				return false;
			}
			trip = TRIPS.get(pilot);
		}
		return trip == null || trip.phase != Phase.AMBUSH;
	}

	/** (0.18.51) The passenger of {@code trip}, if they are still aboard and about; otherwise the trip forgets them. */
	private static @Nullable ServerPlayer guest(MinecraftServer server, Trip trip) {
		if (trip.guest == null) {
			return null;
		}
		ServerPlayer guest = server.getPlayerList().getPlayer(trip.guest);
		if (guest == null || !guest.isAlive()) {
			GUESTS.remove(trip.guest);
			trip.guest = null;
		}
		return guest;
	}

	/** (0.18.51) A trip is over: its passenger is no longer a passenger. */
	private static void forget(Trip trip) {
		if (trip.guest != null) {
			GUESTS.remove(trip.guest);
			trip.guest = null;
		}
	}

	/** Whether {@code entity} is the pod of a trip under way. */
	public static boolean tracked(Entity entity) {
		for (Trip trip : TRIPS.values()) {
			if (trip.pod == entity) {
				return true;
			}
		}
		return false;
	}

	// ---- (0.20.36) the high-sky ambush ----
	/** How long the raiders stay at it before they break off (ticks), and how near the pod (blocks) you climb back in. */
	static final int AMBUSH_TICKS = 20 * 180;
	static final double BOARD_RANGE = 2.5;

	// ---------------------------------------------------------------- the start

	/** Seats {@code player} in {@code pod} (the pod entity the star map was opened at) and starts the countdown. */
	static void start(ServerPlayer player, int dest, PodEntity pod, int fuelLeft, int fuelBefore, boolean auto, @Nullable PodEntity towed) {
		com.jaymospowers.core.PackAdvancements.deed(player, "pod");
		ServerLevel level = player.level();
		BlockPos at = pod.blockPosition();
		Trip trip = new Trip(dest, fuelLeft, fuelBefore, level.dimension(), at);
		trip.fuelGrade = pod.fuelGrade();
		trip.filled = pod.filled();
		trip.damaged = pod.damagedMask();
		trip.pod = pod;
		trip.finished = pod.finished();
		trip.grade = pod.grade();
		trip.lens = pod.lens() && pod.has(PodPart.MAST, 0);
		trip.extras = pod.extras();
		trip.flightScale = PodClass.of(pod).flight;
		trip.legs = PodLegs.lands(pod);
		trip.auto = auto;
		// a pod that crashed and was mended and fuelled flies as any other
		PodTow.clear(pod);
		if (towed != null) {
			trip.towed = PodTow.take(player, towed);
		}
		// (0.18.50) a second seat: whoever else is aboard comes along
		ServerPlayer guest = null;
		for (Entity rider : pod.getPassengers()) {
			if (guest == null && pod.seats() > 1 && rider instanceof ServerPlayer other && other != player) {
				guest = other;
			}
		}
		pod.ejectPassengers();
		pod.setHovering(true);
		pod.addTag(TAG);
		player.startRiding(pod, true, true);
		if (guest != null) {
			trip.guest = guest.getUUID();
			GUESTS.put(guest.getUUID(), player.getUUID());
			guest.startRiding(pod, true, true);
			guest.sendOverlayMessage(Component.translatable("message.jaymospowers.space_countdown", COUNTDOWN_TICKS / 20).withStyle(ChatFormatting.GOLD));
		}
		TRIPS.put(player.getUUID(), trip);
		level.playSound(null, at, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.7F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_countdown", COUNTDOWN_TICKS / 20).withStyle(ChatFormatting.GOLD));
	}

	/** The pod you ride, carrying the trip's parts, in the air at {@code at} (held up, tagged for the trip). */
	private static PodEntity pod(ServerLevel level, Vec3 at, float yaw, Trip trip) {
		PodEntity pod = PodEntity.spawn(level, at, false, trip.fuelBefore);
		pod.setFuelGrade(trip.fuelGrade);
		pod.setParts(trip.filled, trip.damaged);
		pod.setFinished(trip.finished);
		pod.setGrade(trip.grade);
		pod.setLens(trip.lens);
		if (trip.extras != null) {
			pod.applyExtras(trip.extras);
		}
		pod.setYRot(yaw);
		pod.setHovering(true);
		// tagged once it is in the world, so the load check doesn't take it for a leftover
		pod.addTag(TAG);
		return pod;
	}

	// ---------------------------------------------------------------- every tick

	static void tick(MinecraftServer server) {
		if (TRIPS.isEmpty()) {
			return;
		}
		List<UUID> done = new ArrayList<>();
		for (Map.Entry<UUID, Trip> entry : new ArrayList<>(TRIPS.entrySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				forget(entry.getValue());
				done.add(entry.getKey());
				continue;
			}
			if (step(player, entry.getValue())) {
				forget(entry.getValue());
				done.add(entry.getKey());
			}
		}
		for (UUID id : done) {
			TRIPS.remove(id);
			ServerPlayer pilot = server.getPlayerList().getPlayer(id);
			if (pilot != null) {
				// the flight view of the pilot (and of anyone in the second seat) closes
				ServerPlayNetworking.send(pilot, PodSpace.end());
				PodFlight.forget(pilot);
			}
		}
	}

	/** One tick of a trip; true when it is over. */
	private static boolean step(ServerPlayer player, Trip trip) {
		if (trip.phase == Phase.AMBUSH) {
			return ambush(player, trip);
		}
		ServerLevel level = player.level();
		if (trip.pod == null || trip.pod.isRemoved() || trip.pod.level() != level) {
			if (trip.pod != null && !trip.pod.isRemoved()) {
				// the pilot was taken to another level mid-trip: the pod follows, the old one goes
				trip.pod.discard();
			}
			trip.pod = pod(level, player.position(), player.getYRot(), trip);
		}
		PodEntity pod = trip.pod;
		if (player.getVehicle() != pod) {
			player.startRiding(pod, true, true);
		}
		player.resetFallDistance();
		ServerPlayer guest = guest(level.getServer(), trip);
		if (guest != null) {
			if (guest.level() != level) {
				// taken to another level mid-trip: they are no longer aboard
				forget(trip);
			} else {
				if (guest.getVehicle() != pod) {
					guest.startRiding(pod, true, true);
				}
				guest.resetFallDistance();
			}
		}
		Vec3 at = pod.position();
		switch (trip.phase) {
			case COUNTDOWN -> {
				if (trip.ticks % 20 == 0 && trip.ticks > 0) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_count", trip.ticks / 20).withStyle(ChatFormatting.GOLD));
					level.playSound(null, at.x, at.y, at.z, SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.BLOCKS, 1.0F, 0.8F + 0.2F * (3 - trip.ticks / 20));
				}
				level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.2, at.z, 6, 0.7, 0.1, 0.7, 0.02);
				if (trip.ticks < 25) {
					level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.1, at.z, 4, 0.4, 0.05, 0.4, 0.02);
				}
				if (trip.ticks % 6 == 0) {
					Shakes.send(player, 0.25F, at);
				}
				if (--trip.ticks <= 0) {
					trip.phase = Phase.LIFT;
					trip.ticks = LIFT_TICKS;
					level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 3.0F, 0.5F);
					level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.0F, 1.6F);
					level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.2, at.z, 40, 1.5, 0.2, 1.5, 0.12);
				}
			}
			case LIFT -> {
				trip.speed = Math.min(LIFT_TOP, trip.speed + LIFT_ACCEL);
				move(pod, at.add(0.0, trip.speed, 0.0));
				thrust(level, at, 10);
				if (trip.ticks % 4 == 0) {
					Shakes.send(player, 0.45F, at.add(0.0, -3.0, 0.0));
				}
				if (--trip.ticks <= 0) {
					beginClimb(player, trip);
				}
			}
			case CLIMB -> {
				PodClimb.Flight climb = trip.climb;
				if (climb == null) {
					climb = trip.climb = new PodClimb.Flight(at.y, trip.auto);
				}
				double top = level.getMaxY() + PodFlight.CLIMB_OVER_TOP;
				if (!trip.ambushRolled && at.y >= ZFighterHighSky.ambushY(level.getServer())) {
					// up through the high sky, and a raider band may be waiting there
					trip.ambushRolled = true;
					if (ZFighterHighSky.ambushes(player, trip.from, player.getAttachedOrElse(FLIGHTS, 1) - 1)) {
						beginAmbush(player, trip);
						return false;
					}
				}
				thrust(level, at, climb.auto ? 3 : 2);
				if (PodClimb.step(player, level, pod, climb, top, trip.flightScale, guest)) {
					beginSpace(player, trip);
				}
			}
			case SPACE -> {
				PodSpace sim = trip.sim;
				if (sim == null) {
					beginDescent(player, trip);
					return false;
				}
				pod.setDeltaMovement(Vec3.ZERO);
				PodSpace.Result result = sim.tick(player, pod, guest);
				if (sim.takeSaving()) {
					trip.fuelLeft = Math.min(trip.fuelBefore, trip.fuelLeft + PodSpace.SLING_SAVES);
				}
				if (result != PodSpace.Result.FLYING) {
					endSpace(player, trip, result);
				}
			}
			case DESCENT -> {
				BlockPos ground = trip.ground;
				if (ground == null) {
					return true;
				}
				double left = at.y - ground.getY();
				double speed = Mth.clamp(left * DESCENT_EASE, DESCENT_SLOW, DESCENT_FAST);
				if (!trip.legs) {
					// no landing legs: only the cabin comes down, under a chute
					speed = Math.min(speed, PodLegs.CHUTE_SPEED);
					PodLegs.chute(level, at);
				}
				if (left <= speed || --trip.ticks <= 0) {
					touchDown(player, trip);
					return true;
				}
				move(pod, at.add(0.0, -speed, 0.0));
				thrust(level, at, left < 10 ? 10 : 4);
				if (left < 10) {
					level.sendParticles(ParticleTypes.CLOUD, at.x, ground.getY() + 0.2, at.z, 6, 1.4, 0.1, 1.4, 0.08);
					if (trip.ticks % 4 == 0) {
						Shakes.send(player, 0.35F, at);
					}
				}
				if (trip.ticks % 10 == 0) {
					level.playSound(null, at.x, at.y, at.z, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.6F, 0.5F);
				}
			}
		}
		return false;
	}

	private static void move(PodEntity pod, Vec3 to) {
		pod.setPos(to.x, to.y, to.z);
	}

	/** The pod's fire under it. */
	private static void thrust(ServerLevel level, Vec3 at, int flames) {
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y - 0.1, at.z, flames, 0.3, 0.1, 0.3, 0.05);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y - 0.4, at.z, Math.max(1, flames / 2), 0.4, 0.15, 0.4, 0.02);
	}

	/** The lift is over: the landing is found (and its ground made, behind the climb) and the pilot has the pod. */
	private static void beginClimb(ServerPlayer player, Trip trip) {
		target(player, trip);
		int flown = player.getAttachedOrElse(FLIGHTS, 0);
		player.setAttached(FLIGHTS, flown + 1);
		trip.phase = Phase.CLIMB;
		trip.climb = trip.pod == null ? null : new PodClimb.Flight(trip.pod.getY(), trip.auto);
		player.sendOverlayMessage(Component.translatable(trip.auto ? "message.jaymospowers.pod.climb_auto" : "message.jaymospowers.pod.climb").withStyle(ChatFormatting.GOLD));
	}

	/** The pod is above the world: the crossing begins, flown by hand or by the autopilot. */
	private static void beginSpace(ServerPlayer player, Trip trip) {
		PodEntity pod = trip.pod;
		if (pod == null) {
			beginDescent(player, trip);
			return;
		}
		int from = ZFighterSpace.indexOf(trip.from);
		int leagues = PodFlight.leagues(trip.dest, trip.from);
		boolean hollow = ZFighterSpace.skirtsHollow(from, trip.dest);
		pod.setDeltaMovement(Vec3.ZERO);
		trip.phase = Phase.SPACE;
		trip.sim = new PodSpace(player, pod, from, trip.dest, leagues, trip.auto, ZFighterSpace.readCharts(player), trip.towed != null, hollow, trip.flightScale);
	}

	/** The crossing is over: landed on, docked at the post, or the pod crashed (or the Maw took it). */
	private static void endSpace(ServerPlayer player, Trip trip, PodSpace.Result result) {
		PodSpace sim = trip.sim;
		trip.sim = null;
		MinecraftServer server = player.level().getServer();
		ServerPlayer guest = guest(server, trip);
		ServerPlayNetworking.send(player, PodSpace.end());
		if (guest != null) {
			ServerPlayNetworking.send(guest, PodSpace.end());
		}
		PodFlight.forget(player);
		switch (result) {
			case DOCKED -> {
				// down on the post's deck, in the high sky of the world ahead
				ServerLevel to = trip.dest == ZFighterSpace.HOME_INDEX ? null : server.getLevel(ZFighterSpace.DESTS[trip.dest].level());
				if (to != null && PodPost.isPostWorld(to)) {
					trip.to = to.dimension();
					trip.ground = PodPost.dock(to);
				}
				tell(player, guest, Component.translatable("message.jaymospowers.pod.docked").withStyle(ChatFormatting.AQUA));
			}
			case CRASHED -> {
				if (sim != null && sim.caught()) {
					// the Maw takes the pod, and throws a return stage out of its wrecks after it
					trip.dest = ZFighterSpace.MAW;
					trip.to = null;
					trip.ground = null;
					player.getInventory().placeItemBackInInventory(new ItemStack(PodReturn.STAGE), net.minecraft.util.Prediction.SERVER_ONLY);
					tell(player, guest, Component.translatable("message.jaymospowers.pod.maw_took").withStyle(ChatFormatting.RED));
				} else {
					// the hull is gone: the pod limps down on the nearer world, engines dead, and waits for a tow
					boolean back = sim == null || sim.progress() < 0.5;
					trip.dest = back ? ZFighterSpace.indexOf(trip.from) : trip.dest;
					trip.to = null;
					trip.ground = null;
					trip.fuelLeft = 0;
					trip.crashed = true;
					tell(player, guest, Component.translatable(back ? "message.jaymospowers.pod.crash_back" : "message.jaymospowers.pod.crash_on").withStyle(ChatFormatting.RED));
				}
			}
			default -> {
			}
		}
		beginDescent(player, trip);
	}

	// ---------------------------------------------------------------- (0.20.36) the ambush in the high sky

	/**
	 * Caught in the high sky: the pod is struck and hangs there smoking, the pilot (and passenger) thrown out into the thin air,
	 * flying on ki, and a tyrant's band comes in at them. Beat them and climb back in to fly on; climb in sooner (sneak by the
	 * hatch) and the pod runs for it with them battering it, so it lands hard. Fall in the fight and the pod falls too.
	 */
	private static void beginAmbush(ServerPlayer player, Trip trip) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		PodEntity pod = trip.pod;
		if (pod == null) {
			beginDescent(player, trip);
			return;
		}
		Vec3 at = new Vec3(pod.getX(), ZFighterHighSky.ambushY(server), pod.getZ());
		ServerPlayer guest = guest(server, trip);
		player.stopRiding();
		if (guest != null) {
			guest.stopRiding();
		}
		move(pod, at);
		pod.setDeltaMovement(Vec3.ZERO);
		trip.phase = Phase.AMBUSH;
		trip.ticks = AMBUSH_TICKS;
		trip.cleared = false;
		float yaw = player.getYRot();
		player.teleportTo(level, at.x + 2.5, at.y + 1.0, at.z, Set.of(), yaw, 0.0F, true);
		ZFighterHighSky.thrown(player);
		if (guest != null) {
			guest.teleportTo(level, at.x - 2.5, at.y + 1.0, at.z, Set.of(), yaw, 0.0F, true);
			ZFighterHighSky.thrown(guest);
		}
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 1.5, at.z, 3, 0.8, 0.8, 0.8, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.0F, 0.8F);
		Shakes.send(player, 1.0F, at);
		trip.raiders.clear();
		trip.raiders.addAll(ZFighterHighSky.raid(level, at, player, guest));
		tell(player, guest, Component.translatable("message.jaymospowers.high_sky.ambush").withStyle(ChatFormatting.RED));
	}

	/** One tick of an ambush; true when the trip is over (the pod lost). */
	private static boolean ambush(ServerPlayer player, Trip trip) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		PodEntity pod = trip.pod;
		if (pod == null || pod.isRemoved() || !player.isAlive() || pod.level() != level) {
			lost(server, trip);
			return true;
		}
		pod.setDeltaMovement(Vec3.ZERO);
		Vec3 at = pod.position();
		if (trip.ticks % 5 == 0) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 1.5, at.z, 2, 0.4, 0.4, 0.4, 0.02);
		}
		ServerPlayer guest = guest(server, trip);
		if (!trip.cleared) {
			ZFighterHighSky.forgetDead(level, trip.raiders);
			if (trip.raiders.isEmpty()) {
				trip.cleared = true;
				tell(player, guest, Component.translatable("message.jaymospowers.high_sky.clear").withStyle(ChatFormatting.GOLD));
			} else if (--trip.ticks <= 0) {
				ZFighterHighSky.flee(level, trip.raiders);
				trip.raiders.clear();
				trip.cleared = true;
				tell(player, guest, Component.translatable("message.jaymospowers.high_sky.broke_off").withStyle(ChatFormatting.GOLD));
			}
		}
		boolean near = player.distanceToSqr(at.add(0.0, 1.5, 0.0)) < BOARD_RANGE * BOARD_RANGE;
		if (!near || !trip.cleared && !player.isShiftKeyDown()) {
			return false;
		}
		// back in the pod, and on
		if (!trip.cleared) {
			// running with the band still at it: they batter the pod as it goes, and it comes down hard
			trip.battered = true;
			ZFighterHighSky.flee(level, trip.raiders);
			trip.raiders.clear();
			trip.cleared = true;
			level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 1.5, at.z, 2, 0.6, 0.6, 0.6, 0.0);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.5F, 1.1F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.high_sky.ran").withStyle(ChatFormatting.RED));
		}
		ZFighterPower.endFlight(player, ZFighterPower.live(player));
		player.startRiding(pod, true, true);
		if (guest != null) {
			if (guest.level() == level && guest.distanceToSqr(pod) < 64.0 * 64.0) {
				ZFighterPower.endFlight(guest, ZFighterPower.live(guest));
				guest.startRiding(pod, true, true);
			} else {
				forget(trip);
			}
		}
		level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 2.0F, 0.6F);
		trip.phase = Phase.CLIMB;
		trip.climb = new PodClimb.Flight(pod.getY(), trip.auto);
		return false;
	}

	/** The pilot fell (or left) in an ambush: the band breaks off, and the pod drops out of the sky onto the ground below it, shaken. */
	private static @Nullable PodEntity lost(MinecraftServer server, Trip trip) {
		forget(trip);
		PodEntity pod = trip.pod;
		ServerLevel level = pod != null && pod.level() instanceof ServerLevel l ? l : server.overworld();
		ZFighterHighSky.flee(level, trip.raiders);
		trip.raiders.clear();
		double x = pod != null ? pod.getX() : trip.origin.getX() + 0.5, z = pod != null ? pod.getZ() : trip.origin.getZ() + 0.5;
		if (pod != null && !pod.isRemoved()) {
			level.sendParticles(ParticleTypes.LARGE_SMOKE, pod.getX(), pod.getY() + 1.0, pod.getZ(), 30, 0.6, 0.8, 0.6, 0.04);
			pod.discard();
		}
		trip.pod = null;
		int bx = Mth.floor(x), bz = Mth.floor(z);
		level.getChunk(bx >> 4, bz >> 4);
		BlockPos ground = new BlockPos(bx, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz), bz);
		PodEntity stood = stand(level, ground, trip, trip.fuelLeft);
		level.sendParticles(ParticleTypes.EXPLOSION, stood.getX(), stood.getY() + 1.0, stood.getZ(), 3, 0.8, 0.5, 0.8, 0.0);
		level.playSound(null, stood.getX(), stood.getY(), stood.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 2.0F, 0.7F);
		if (!PodGrades.softLanding(stood.grade())) {
			shake(level, stood);
		}
		return stood;
	}

	/** Says {@code message} to the pilot and the passenger. */
	private static void tell(ServerPlayer player, @Nullable ServerPlayer guest, Component message) {
		player.sendSystemMessage(message);
		if (guest != null) {
			guest.sendSystemMessage(message);
		}
	}

	/** Where the trip comes down: the world, and open ground on it. */
	private static void target(ServerPlayer player, Trip trip) {
		if (trip.to != null && trip.ground != null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		if (trip.dest == ZFighterSpace.HOME_INDEX) {
			ZFighterSpace.Home home = player.getAttached(ZFighterSpace.HOME);
			ServerLevel to = null;
			if (home != null) {
				Identifier id = Identifier.tryParse(home.dimension());
				to = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
			}
			if (to != null) {
				trip.to = to.dimension();
				// a landing beacon: the pod comes down beside it
				to.getChunk(home.pos().getX() >> 4, home.pos().getZ() >> 4);
				BlockPos spot = to.getBlockState(home.pos()).is(PodBeacons.BLOCK) ? home.pos().east(PodBeacons.BESIDE) : home.pos();
				trip.ground = groundAt(to, spot);
			} else {
				ServerLevel overworld = server.overworld();
				trip.to = overworld.dimension();
				trip.ground = groundAt(overworld, overworld.getRespawnData().pos());
			}
			// (0.18.55) a landing yard within reach of the home ground: down on a free pad
			ServerLevel homeLevel = trip.to == null ? null : server.getLevel(trip.to);
			BlockPos pad = homeLevel == null || trip.ground == null ? null : ZFighterYard.freePad(homeLevel, trip.ground);
			if (pad != null) {
				trip.ground = pad;
			}
			return;
		}
		ZFighterSpace.Dest dest = ZFighterSpace.DESTS[trip.dest];
		ServerLevel to = server.getLevel(dest.level());
		if (to == null) {
			to = server.overworld();
		}
		trip.to = to.dimension();
		trip.ground = ground(to, dest, player);
	}

	/** {@code pos} if you can stand there, otherwise the open ground at its column. */
	private static BlockPos groundAt(ServerLevel level, BlockPos pos) {
		level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
		if (standable(level, pos)) {
			return pos;
		}
		return new BlockPos(pos.getX(), level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()), pos.getZ());
	}

	private static boolean standable(ServerLevel level, BlockPos feet) {
		return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
			&& !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty() && level.getBlockState(feet).getFluidState().isEmpty();
	}

	/** Open ground anywhere on a far world: a random flat dry spot (a cave floor on Cinder, a wreck's deck in the Maw). */
	private static BlockPos ground(ServerLevel level, ZFighterSpace.Dest dest, ServerPlayer player) {
		RandomSource random = level.getRandom();
		// where the pilot last lifted off from this world, unless they asked for somewhere new
		BlockPos marked = PodMarks.landing(level, dest, player);
		if (marked != null) {
			return marked;
		}
		if (dest.kind() == ZFighterSpace.Kind.WRECKS) {
			MawWorld.build(level);
			List<BlockPos> decks = MawWorld.decks();
			return decks.get(random.nextInt(decks.size()));
		}
		int[] made = {ZFighterSpace.NEW_CHUNKS};
		for (int i = 0; i < LAND_TRIES; i++) {
			int x = random.nextInt(LAND_RANGE * 2 + 1) - LAND_RANGE;
			int z = random.nextInt(LAND_RANGE * 2 + 1) - LAND_RANGE;
			if (!ZFighterSpace.reach(level, x, z, made)) {
				continue;
			}
			BlockPos spot = dest.kind() == ZFighterSpace.Kind.FORGE ? ZFighterSpace.forgeFloor(level, x, z) : ZFighterSpace.surface(level, x, z);
			if (spot != null) {
				return spot;
			}
		}
		// nowhere flat turned up: the old way down, the world's pad (not given a pod of its own: yours is landing on it)
		return ZFighterSpace.pad(level, dest, false);
	}

	/** The flight is over: the pod drops out of the sky over the landing, with you in it. */
	private static void beginDescent(ServerPlayer player, Trip trip) {
		MinecraftServer server = player.level().getServer();
		target(player, trip);
		ServerLevel to = trip.to == null ? null : server.getLevel(trip.to);
		BlockPos ground = trip.ground;
		if (to == null || ground == null) {
			to = server.overworld();
			ground = groundAt(to, to.getRespawnData().pos());
			trip.to = to.dimension();
			trip.ground = ground;
		}
		double top = Math.min(ground.getY() + DESCENT_HEIGHT, to.getMaxY() + CRUISE_OVER_TOP);
		Vec3 start = new Vec3(ground.getX() + 0.5, top, ground.getZ() + 0.5);
		ServerPlayer guest = guest(server, trip);
		player.stopRiding();
		if (guest != null) {
			guest.stopRiding();
		}
		if (trip.pod != null) {
			trip.pod.discard();
		}
		player.teleportTo(to, start.x, start.y + 0.5, start.z, Set.of(), player.getYRot(), 0.0F, true);
		if (guest != null) {
			guest.teleportTo(to, start.x, start.y + 0.5, start.z, Set.of(), player.getYRot(), 0.0F, true);
		}
		trip.pod = pod(to, start, player.getYRot(), trip);
		player.startRiding(trip.pod, true, true);
		if (guest != null) {
			guest.startRiding(trip.pod, true, true);
		}
		trip.phase = Phase.DESCENT;
		trip.ticks = DESCENT_MAX_TICKS;
		to.playSound(null, start.x, start.y, start.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 2.0F, 0.6F);
	}

	/** On the ground: the pod stands (holding the fuel left), a pod that isn't whole lands hard, and you climb out beside it. */
	private static void touchDown(ServerPlayer player, Trip trip) {
		ServerLevel level = player.level();
		BlockPos ground = trip.ground == null ? player.blockPosition() : trip.ground;
		ServerPlayer guest = guest(level.getServer(), trip);
		player.stopRiding();
		if (guest != null) {
			guest.stopRiding();
		}
		if (trip.pod != null) {
			trip.pod.discard();
			trip.pod = null;
		}
		PodEntity pod = stand(level, ground, trip, trip.fuelLeft);
		BlockPos at = pod.blockPosition();
		BlockPos feet = beside(level, at);
		player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
		player.resetFallDistance();
		if (guest != null) {
			BlockPos next = standable(level, feet.east()) ? feet.east() : feet;
			guest.teleportTo(level, next.getX() + 0.5, next.getY(), next.getZ() + 0.5, Set.of(), guest.getYRot(), guest.getXRot(), true);
			guest.resetFallDistance();
		}
		level.sendParticles(ParticleTypes.CLOUD, at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, 40, 1.6, 0.1, 1.6, 0.1);
		level.playSound(null, at, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.6F, 0.6F);
		level.playSound(null, at, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.7F);
		Shakes.send(player, 0.6F, Vec3.atCenterOf(at));
		// no landing legs: the fins, bell, tank and mast are torn off and the tank is empty, instead of a hard landing
		boolean torn = PodLegs.tearOff(level, pod, player);
		if (!torn && (!pod.complete() || trip.battered || trip.crashed)) {
			hardLanding(level, player, guest, pod);
		}
		if (trip.crashed) {
			// engines dead, the tank empty: it stays here till it is mended, or a friend with a tow line takes it home
			PodTow.mark(pod, player.getUUID());
		}
		if (trip.towed != null) {
			PodTow.land(level, roomFor(level, ground.east(6)), trip.towed);
			trip.towed = null;
		}
		PodScanner.wind(player, pod);
		ZFighterSpace.Dest dest = trip.dest == ZFighterSpace.HOME_INDEX ? null : ZFighterSpace.DESTS[trip.dest];
		if (trip.dest == ZFighterSpace.HOME_INDEX) {
			// (0.18.52) a pod flown out and landed home again: the deed that opens Dread
			ZFighterKingdoms.deed(player, "pod_flight");
		}
		// (0.18.55) a Pilot is paid for a trip with a passenger or a parcel aboard (Kingdoms' Careers)
		ZFighterKingdoms.flown(player, guest, level, at);
		ZFighterSpace.arrived(player, dest);
		if (guest != null) {
			ZFighterSpace.arrived(guest, dest);
		}
	}

	/** (0.18.47) A pod that isn't whole comes down hard: a blast, the pilot hurt, and a part or two shaken off (never the keel). */
	private static void hardLanding(ServerLevel level, ServerPlayer player, @Nullable ServerPlayer guest, PodEntity pod) {
		level.playSound(null, pod.getX(), pod.getY() + 1.0, pod.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
		level.sendParticles(ParticleTypes.EXPLOSION, pod.getX(), pod.getY() + 1.0, pod.getZ(), 2, 0.6, 0.4, 0.6, 0.0);
		Shakes.send(player, 1.0F, pod.position());
		hurting = true;
		try {
			player.hurtServer(level, level.damageSources().fall(), HARD_LANDING_DAMAGE);
			if (guest != null) {
				guest.hurtServer(level, level.damageSources().fall(), HARD_LANDING_DAMAGE);
			}
		} finally {
			hurting = false;
		}
		if (PodGrades.softLanding(pod.grade())) {
			// (0.18.51) a pod mended to the top grade takes the blow in its hull: nothing shakes loose
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.pod.hard_landing_soft").withStyle(ChatFormatting.GOLD));
			return;
		}
		shake(level, pod);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.pod.hard_landing").withStyle(ChatFormatting.RED));
	}

	/** {@link #SHAKEN_MIN} to {@link #SHAKEN_MAX} of the pod's parts shaken off (never the keel), dropped beside it. */
	private static void shake(ServerLevel level, PodEntity pod) {
		List<Integer> loose = new ArrayList<>();
		for (int bit = 1; bit != 0 && bit <= PodPart.ALL_SLOTS; bit <<= 1) {
			if ((pod.filled() & bit) != 0 && (PodPart.KEEL.bits() & bit) == 0) {
				loose.add(bit);
			}
		}
		RandomSource random = level.getRandom();
		int shaken = SHAKEN_MIN + random.nextInt(SHAKEN_MAX - SHAKEN_MIN + 1);
		for (int i = 0; i < shaken && !loose.isEmpty(); i++) {
			boolean[] was = new boolean[1];
			int bit = loose.remove(random.nextInt(loose.size()));
			int forged = pod.forgedAt(bit);
			PodPart part = pod.detach(bit, was);
			if (part != null) {
				ItemStack stack = PodParts.stackOf(part, was[0], 0, forged, com.jaymospowers.power.zfighter.pod.PodModules.FEWEST_SOCKETS);
				Block.popResource(level, pod.blockPosition().above(), stack);
			}
		}
	}

	/** Stands a pod with the trip's parts and {@code fuel} at {@code ground} (or the nearest room for it); the pod. */
	private static PodEntity stand(ServerLevel level, BlockPos ground, Trip trip, int fuel) {
		Vec3 at = room(level, ground);
		PodEntity pod = PodEntity.spawn(level, at, false, fuel);
		pod.setFuelGrade(trip.fuelGrade);
		pod.setParts(trip.filled, trip.damaged);
		pod.setFinished(trip.finished);
		pod.setGrade(trip.grade);
		pod.setLens(trip.lens);
		if (trip.extras != null) {
			pod.applyExtras(trip.extras);
		}
		pod.setFuel(fuel);
		return pod;
	}

	/** Where a pod's feet may rest at {@code ground} (or the nearest spot with room round it). */
	public static Vec3 roomFor(ServerLevel level, BlockPos near) {
		return room(level, near.offset(3, 0, 0));
	}

	private static Vec3 room(ServerLevel level, BlockPos ground) {
		for (int r = 0; r <= 3; r++) {
			for (int dx = -r; dx <= r; dx++) {
				for (int dz = -r; dz <= r; dz++) {
					for (int dy = 2; dy >= -2; dy--) {
						BlockPos spot = ground.offset(dx, dy, dz);
						Vec3 feet = Vec3.atBottomCenterOf(spot);
						if (level.getBlockState(spot).getFluidState().isEmpty()
							&& level.getBlockState(spot.below()).isFaceSturdy(level, spot.below(), net.minecraft.core.Direction.UP)
							&& level.noCollision(new AABB(feet.x - POD_WIDTH / 2.0, feet.y, feet.z - POD_WIDTH / 2.0, feet.x + POD_WIDTH / 2.0, feet.y + POD_HEIGHT,
								feet.z + POD_WIDTH / 2.0))) {
							return feet;
						}
					}
				}
			}
		}
		return Vec3.atBottomCenterOf(ground);
	}

	/** A spot to stand beside the pod at {@code pod}. */
	private static BlockPos beside(ServerLevel level, BlockPos pod) {
		int[][] sides = {{0, 3}, {3, 0}, {-3, 0}, {0, -3}, {2, 2}, {-2, -2}, {2, -2}, {-2, 2}};
		for (int[] side : sides) {
			for (int dy = 1; dy >= -1; dy--) {
				BlockPos feet = pod.offset(side[0], dy, side[1]);
				if (standable(level, feet)) {
					return feet;
				}
			}
		}
		return pod.above(4);
	}

	// ---------------------------------------------------------------- the skip, and leaving mid-trip

	/** Left the game mid-trip: before the lift the pod stands again where it was; after it, it is set down where it was going. */
	private static void leave(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		UUID pilot = GUESTS.remove(player.getUUID());
		if (pilot != null) {
			// (0.18.51) a passenger leaves: they wake where the trip began
			Trip aboard = TRIPS.get(pilot);
			player.stopRiding();
			if (aboard != null) {
				aboard.guest = null;
				ServerLevel home = server.getLevel(aboard.from);
				if (home != null) {
					player.setAttached(PENDING, new Pending(home.dimension().identifier().toString(), beside(home, aboard.origin)));
				}
			}
			return;
		}
		Trip trip = TRIPS.remove(player.getUUID());
		if (trip == null) {
			return;
		}
		ServerPlayer guest = guest(server, trip);
		forget(trip);
		player.stopRiding();
		if (guest != null) {
			guest.stopRiding();
		}
		if (trip.pod != null) {
			trip.pod.discard();
		}
		if (trip.phase == Phase.AMBUSH) {
			// (0.20.36) gone in the middle of the fight: the pod is lost below, and you wake beside it
			PodEntity fell = lost(server, trip);
			if (fell != null && fell.level() instanceof ServerLevel down) {
				player.setAttached(PENDING, new Pending(down.dimension().identifier().toString(), beside(down, fell.blockPosition())));
			}
			return;
		}
		ServerLevel level;
		BlockPos at;
		int fuel;
		if (trip.phase == Phase.COUNTDOWN || trip.phase == Phase.LIFT) {
			level = server.getLevel(trip.from);
			at = trip.origin;
			fuel = trip.fuelBefore;
		} else {
			target(player, trip);
			level = trip.to == null ? null : server.getLevel(trip.to);
			at = trip.ground;
			fuel = trip.fuelLeft;
		}
		if (level == null || at == null) {
			return;
		}
		PodEntity stood = stand(level, at, trip, fuel);
		if (trip.towed != null) {
			PodTow.land(level, roomFor(level, at.east(6)), trip.towed);
		}
		BlockPos wakes = beside(level, stood.blockPosition());
		player.setAttached(PENDING, new Pending(level.dimension().identifier().toString(), wakes));
		if (guest != null) {
			guest.teleportTo(level, wakes.getX() + 1.5, wakes.getY(), wakes.getZ() + 0.5, Set.of(), guest.getYRot(), guest.getXRot(), true);
			guest.resetFallDistance();
		}
	}

	/** Back in the game after leaving mid-trip: beside the pod. */
	private static void wake(ServerPlayer player) {
		Pending pending = player.getAttached(PENDING);
		if (pending == null) {
			return;
		}
		player.removeAttached(PENDING);
		Identifier id = Identifier.tryParse(pending.dimension());
		ServerLevel level = id == null ? null : player.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION, id));
		if (level == null) {
			return;
		}
		BlockPos at = pending.pos();
		player.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
		player.resetFallDistance();
	}
}
