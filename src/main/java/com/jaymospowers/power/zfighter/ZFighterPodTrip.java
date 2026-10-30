package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.power.zfighter.pod.PodEntity;
import com.jaymospowers.power.zfighter.pod.PodParts;
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
 * inside while everyone near watches it go. Up past the clouds the client shows the flight ({@code ZFighterFlightScreen}): the pod
 * crossing space from here to there, longer the farther the world ({@link #flightTicks}); after a first trip it can be skipped.
 * Then the pod comes down out of the sky over open ground anywhere on the far world (no landing pad: {@link #ground}), slowing
 * on its thrusters, and sets down; it stands there holding the fuel left, and you climb out beside it. (0.18.49) It is the real
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
	/** While the flight screen shows, the pod keeps climbing this fast, up to this far over the world's top. */
	static final double CRUISE_SPEED = 0.6;
	static final int CRUISE_OVER_TOP = 40;
	/** The flight's length: a base and more for each unit of fuel the trip burns (ticks). */
	static final int FLIGHT_BASE_TICKS = 100, FLIGHT_PER_FUEL = 30;
	/** The descent: how high over the ground it starts, its top and bottom speed, how quickly it slows, and when it gives up. */
	static final int DESCENT_HEIGHT = 56, DESCENT_MAX_TICKS = 400;
	static final double DESCENT_FAST = 1.2, DESCENT_SLOW = 0.12, DESCENT_EASE = 0.05;
	/** (0.18.49) A pod that isn't whole lands hard: the pilot takes this much fall damage and this many parts (not the keel) come off. */
	static final float HARD_LANDING_DAMAGE = 6.0F;
	static final int SHAKEN_MIN = 1, SHAKEN_MAX = 2;
	/** How big a pod is to find room for it (blocks): its width and height. */
	static final double POD_WIDTH = 2.0, POD_HEIGHT = 3.5;
	/** Where on a far world the pod may come down: within this many blocks of the middle, tried this many times. */
	static final int LAND_RANGE = 360, LAND_TRIES = 40;

	/** The tag on the pod you ride. */
	public static final String TAG = "jaymospowers.space_pod";

	enum Phase {
		COUNTDOWN, LIFT, FLIGHT, DESCENT
	}

	/** One trip: where to (a destination index or Home), the fuel it will leave in the pod, the parts it carries, and where it is in the flight. */
	private static final class Trip {
		final int dest;
		final int fuelLeft;
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
		@Nullable ResourceKey<Level> to;
		@Nullable BlockPos ground;

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
	public record Flight(int to, int ticks, boolean skippable) implements CustomPacketPayload {
		public static final Type<Flight> TYPE = new Type<>(JaymosPowers.id("zfighter_space_flight"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Flight> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.to + 1);
				buf.writeVarInt(p.ticks);
				buf.writeBoolean(p.skippable);
			},
			buf -> new Flight(buf.readVarInt() - 1, buf.readVarInt(), buf.readBoolean()));

		@Override
		public Type<Flight> type() {
			return TYPE;
		}
	}

	private static final Map<UUID, Trip> TRIPS = new HashMap<>();
	/** Set while the hard landing hurts the pilot (a trip's pilot is otherwise unhurtable). */
	private static boolean hurting;

	private ZFighterPodTrip() {
	}

	static void init() {
		PayloadTypeRegistry.clientboundPlay().register(Flight.TYPE, Flight.CODEC);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> hurting || !(entity instanceof ServerPlayer player && flying(player)));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> leave(handler.getPlayer()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> wake(handler.getPlayer()));
		// (a pod left over from a trip that no longer runs loses its trip tag when it loads: see PodEntity.init)
	}

	/** Whether {@code player} is on a trip (seated, lifting, flying or coming down). */
	public static boolean flying(ServerPlayer player) {
		return TRIPS.containsKey(player.getUUID());
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

	/** The flight's length for a trip that burns {@code cost} fuel. */
	public static int flightTicks(int cost) {
		return FLIGHT_BASE_TICKS + FLIGHT_PER_FUEL * cost;
	}

	// ---------------------------------------------------------------- the start

	/** Seats {@code player} in {@code pod} (the pod entity the star map was opened at) and starts the countdown. */
	static void start(ServerPlayer player, int dest, PodEntity pod, int fuelLeft, int fuelBefore) {
		ServerLevel level = player.level();
		BlockPos at = pod.blockPosition();
		Trip trip = new Trip(dest, fuelLeft, fuelBefore, level.dimension(), at);
		trip.fuelGrade = pod.fuelGrade();
		trip.filled = pod.filled();
		trip.damaged = pod.damagedMask();
		trip.pod = pod;
		pod.ejectPassengers();
		pod.setHovering(true);
		pod.addTag(TAG);
		player.startRiding(pod, true, true);
		TRIPS.put(player.getUUID(), trip);
		level.playSound(null, at, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0F, 0.7F);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.space_countdown", COUNTDOWN_TICKS / 20).withStyle(ChatFormatting.GOLD));
	}

	/** The pod you ride, carrying the trip's parts, in the air at {@code at} (held up, tagged for the trip). */
	private static PodEntity pod(ServerLevel level, Vec3 at, float yaw, Trip trip) {
		PodEntity pod = PodEntity.spawn(level, at, false, trip.fuelBefore);
		pod.setFuelGrade(trip.fuelGrade);
		pod.setParts(trip.filled, trip.damaged);
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
				done.add(entry.getKey());
				continue;
			}
			if (step(player, entry.getValue())) {
				done.add(entry.getKey());
			}
		}
		for (UUID id : done) {
			TRIPS.remove(id);
		}
	}

	/** One tick of a trip; true when it is over. */
	private static boolean step(ServerPlayer player, Trip trip) {
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
					beginFlight(player, trip);
				}
			}
			case FLIGHT -> {
				if (at.y < level.getMaxY() + CRUISE_OVER_TOP) {
					move(pod, at.add(0.0, CRUISE_SPEED, 0.0));
					thrust(level, at, 3);
				}
				if (--trip.ticks <= 0) {
					beginDescent(player, trip);
				}
			}
			case DESCENT -> {
				BlockPos ground = trip.ground;
				if (ground == null) {
					return true;
				}
				double left = at.y - ground.getY();
				double speed = Mth.clamp(left * DESCENT_EASE, DESCENT_SLOW, DESCENT_FAST);
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

	/** Past the clouds: the landing is found (and its ground made, behind the screen) and the flight shows. */
	private static void beginFlight(ServerPlayer player, Trip trip) {
		target(player, trip);
		int cost = ZFighterSpace.cost(trip.dest, trip.from);
		int flown = player.getAttachedOrElse(FLIGHTS, 0);
		player.setAttached(FLIGHTS, flown + 1);
		trip.phase = Phase.FLIGHT;
		trip.ticks = flightTicks(cost);
		ServerPlayNetworking.send(player, new Flight(trip.dest, trip.ticks, flown > 0));
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
				trip.ground = groundAt(to, home.pos());
			} else {
				ServerLevel overworld = server.overworld();
				trip.to = overworld.dimension();
				trip.ground = groundAt(overworld, overworld.getRespawnData().pos());
			}
			return;
		}
		ZFighterSpace.Dest dest = ZFighterSpace.DESTS[trip.dest];
		ServerLevel to = server.getLevel(dest.level());
		if (to == null) {
			to = server.overworld();
		}
		trip.to = to.dimension();
		trip.ground = ground(to, dest);
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
	private static BlockPos ground(ServerLevel level, ZFighterSpace.Dest dest) {
		RandomSource random = level.getRandom();
		if (dest.kind() == ZFighterSpace.Kind.VOID) {
			ZFighterSpace.buildMaw(level);
			List<BlockPos> decks = ZFighterSpace.mawDecks();
			return decks.get(random.nextInt(decks.size()));
		}
		for (int i = 0; i < LAND_TRIES; i++) {
			int x = random.nextInt(LAND_RANGE * 2 + 1) - LAND_RANGE;
			int z = random.nextInt(LAND_RANGE * 2 + 1) - LAND_RANGE;
			level.getChunk(x >> 4, z >> 4);
			BlockPos spot = dest.kind() == ZFighterSpace.Kind.FORGE ? ZFighterSpace.forgeFloor(level, x, z) : ZFighterSpace.surface(level, x, z);
			if (spot != null) {
				return spot;
			}
		}
		// nowhere flat turned up: the old way down, the world's pad
		return ZFighterSpace.pad(level, dest);
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
		player.stopRiding();
		if (trip.pod != null) {
			trip.pod.discard();
		}
		player.teleportTo(to, start.x, start.y + 0.5, start.z, Set.of(), player.getYRot(), 0.0F, true);
		trip.pod = pod(to, start, player.getYRot(), trip);
		player.startRiding(trip.pod, true, true);
		trip.phase = Phase.DESCENT;
		trip.ticks = DESCENT_MAX_TICKS;
		to.playSound(null, start.x, start.y, start.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.BLOCKS, 2.0F, 0.6F);
	}

	/** On the ground: the pod stands (holding the fuel left), a pod that isn't whole lands hard, and you climb out beside it. */
	private static void touchDown(ServerPlayer player, Trip trip) {
		ServerLevel level = player.level();
		BlockPos ground = trip.ground == null ? player.blockPosition() : trip.ground;
		player.stopRiding();
		if (trip.pod != null) {
			trip.pod.discard();
			trip.pod = null;
		}
		PodEntity pod = stand(level, ground, trip, trip.fuelLeft);
		BlockPos at = pod.blockPosition();
		BlockPos feet = beside(level, at);
		player.teleportTo(level, feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
		player.resetFallDistance();
		level.sendParticles(ParticleTypes.CLOUD, at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, 40, 1.6, 0.1, 1.6, 0.1);
		level.playSound(null, at, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.6F, 0.6F);
		level.playSound(null, at, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.7F);
		Shakes.send(player, 0.6F, Vec3.atCenterOf(at));
		if (!pod.complete()) {
			hardLanding(level, player, pod);
		}
		ZFighterSpace.arrived(player, trip.dest == ZFighterSpace.HOME_INDEX ? null : ZFighterSpace.DESTS[trip.dest]);
	}

	/** (0.18.49) A pod that isn't whole comes down hard: a blast, the pilot hurt, and a part or two shaken off (never the keel). */
	private static void hardLanding(ServerLevel level, ServerPlayer player, PodEntity pod) {
		level.playSound(null, pod.getX(), pod.getY() + 1.0, pod.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
		level.sendParticles(ParticleTypes.EXPLOSION, pod.getX(), pod.getY() + 1.0, pod.getZ(), 2, 0.6, 0.4, 0.6, 0.0);
		Shakes.send(player, 1.0F, pod.position());
		hurting = true;
		try {
			player.hurtServer(level, level.damageSources().fall(), HARD_LANDING_DAMAGE);
		} finally {
			hurting = false;
		}
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
			PodPart part = pod.detach(loose.remove(random.nextInt(loose.size())), was);
			if (part != null) {
				ItemStack stack = PodParts.itemOf(part, was[0]);
				Block.popResource(level, pod.blockPosition().above(), stack);
			}
		}
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.pod.hard_landing").withStyle(ChatFormatting.RED));
	}

	/** Stands a pod with the trip's parts and {@code fuel} at {@code ground} (or the nearest room for it); the pod. */
	private static PodEntity stand(ServerLevel level, BlockPos ground, Trip trip, int fuel) {
		Vec3 at = room(level, ground);
		PodEntity pod = PodEntity.spawn(level, at, false, fuel);
		pod.setFuelGrade(trip.fuelGrade);
		pod.setParts(trip.filled, trip.damaged);
		pod.setFuel(fuel);
		return pod;
	}

	/** Where a pod's feet may rest at {@code ground} (or the nearest spot with room round it). */
	static Vec3 roomFor(ServerLevel level, BlockPos near) {
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

	/** The player asked to skip the flight: allowed after a first trip. */
	static void skip(ServerPlayer player) {
		Trip trip = TRIPS.get(player.getUUID());
		if (trip != null && trip.phase == Phase.FLIGHT && player.getAttachedOrElse(FLIGHTS, 0) > 1) {
			trip.ticks = 0;
		}
	}

	/** Left the game mid-trip: before the lift the pod stands again where it was; after it, it is set down where it was going. */
	private static void leave(ServerPlayer player) {
		Trip trip = TRIPS.remove(player.getUUID());
		if (trip == null) {
			return;
		}
		MinecraftServer server = player.level().getServer();
		player.stopRiding();
		if (trip.pod != null) {
			trip.pod.discard();
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
		player.setAttached(PENDING, new Pending(level.dimension().identifier().toString(), beside(level, stood.blockPosition())));
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
