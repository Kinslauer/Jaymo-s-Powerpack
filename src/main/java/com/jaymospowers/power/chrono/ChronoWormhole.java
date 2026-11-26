package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.PickKit;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.UltimateFilm;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.power.chrono.future.Future;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Chrono's wormhole (Andy's picks from the Legends mods, ideas only; its Speed Force portal's colours, sizes and lifetimes
 * are read from Legends Mod Core, LGPL-3.0, and rebuilt in {@code ChronoWormholeArt}). In Overclock's top gear
 * ({@link ChronoGears#GEARS}, at speed), running flat out for {@link #OPEN_TICKS} tears the air {@link #AHEAD} blocks ahead
 * open into a swirling hole of blue and gold light ({@link Open}, drawn by every client near). Nothing is ever placed: no
 * portal block, no portal dimension; it is the leap's look. Run into it and a short film plays ({@link UltimateFilm#WORMHOLE}:
 * a tunnel of streaking light toward a glowing sphere, lightning trailing off the runner; skippable like the others), then:
 * <ul>
 * <li><b>The leap</b> ({@code ch_wormhole}): the runner comes out in the future or, from it, home, under {@link Future}'s
 * rules (only a Chrono whose echo is slain; its recharge). Everyone sprinting within {@link #FOLLOW_RADIUS} blocks behind
 * comes too, as Sneak + B brings companions.</li>
 * <li><b>A marked place</b> ({@code ch_marked}): with a place marked (the mark key, sneak: {@link Mark} tells this side), the
 * wormhole puts you and those behind you there in this same world, over any distance, instead of the future.</li>
 * <li><b>Dragging a foe</b> ({@code ch_drag}): the catch key ({@link Catch}) in top gear at a foe ahead plays
 * {@link UltimateFilm#DRAG}: the foe is held while the film takes it down the wormhole, over the sea and round the world; then,
 * back where you started, an uppercut and a lightning slam. Creatures only (never a player, the Ender Dragon or the Wither).</li>
 * </ul>
 * The film is only a view: the move waits for it, the client says when it ends (or is skipped: {@link FilmEnd}) and a timer
 * finishes it regardless. Everyone in one is protected from harm until it is done.
 */
public final class ChronoWormhole {
	// ---- tuning ----
	/** Flat out in top gear this long (ticks) tears the hole open; how far ahead (blocks); how long it stays; how near counts as in (blocks). */
	static final int OPEN_TICKS = 140, HOLE_LIFE = 120;
	static final double AHEAD = 14.0, MIN_AHEAD = 5.0, ENTER = 2.0, HEIGHT = 1.2;
	/** The wait after one opens or fails before the next can build (ticks), and after a failed leap. */
	static final int COOLDOWN = 400, FAILED_WAIT = 200;
	/** Runners this close (blocks) and sprinting come too. */
	static final double FOLLOW_RADIUS = 8.0;
	/** The wormhole film's length (ticks), and the arrival's own wait before the timer finishes it regardless. */
	static final int FILM_TICKS = 80, FINISH_GRACE = 30;
	/** The least a film plays before the client's "ended" counts (ticks), so a key held through the start doesn't skip it. */
	static final int MIN_FILM = 12;
	/** Clients this near (blocks) the hole are sent it. */
	static final double SYNC_RADIUS = 96.0;
	/** The drag: how far ahead a foe may be caught (blocks), the cone, the film's length (ticks), the share of it before the uppercut, the wait between drags. */
	static final double CATCH_REACH = 5.0, CATCH_CONE = 0.5;
	public static final int DRAG_TICKS = 200;
	static final float DRAG_FINISH_AT = 0.8F;
	static final int DRAG_COOLDOWN = 900;
	/** The uppercut and the slam: damage (plus per ability mastered), the lift and the drop (blocks a tick), the wait between them (ticks). */
	static final float UPPERCUT_DAMAGE = 5.0F, UPPERCUT_PER_LEVEL = 1.0F, SLAM_DAMAGE = 9.0F, SLAM_PER_LEVEL = 2.0F;
	static final double LIFT = 0.95, DROP = 2.4;
	static final int SLAM_WAIT = 10;
	/** The wormhole's colours (Legends Mod Core's Speed Force portal: its lightning's and energy's blooms, gold for the other half). */
	static final int BLUE = 0x63D1FF, GOLD = 0xFFC840;
	static final int FATE_FUTURE = 0, FATE_MARKED = 1, FATE_DRAG = 2;

	/** Server to client: a wormhole is open at {@code at} facing {@code dir} for {@code life} ticks (0 takes it away), {@code owner}'s. */
	public record Open(int owner, Vec3 at, Vec3 dir, int life) implements CustomPacketPayload {
		public static final Type<Open> TYPE = new Type<>(JaymosPowers.id("chrono_wormhole"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeVarInt(p.owner);
				buf.writeDouble(p.at.x);
				buf.writeDouble(p.at.y);
				buf.writeDouble(p.at.z);
				buf.writeDouble(p.dir.x);
				buf.writeDouble(p.dir.y);
				buf.writeDouble(p.dir.z);
				buf.writeVarInt(p.life);
			},
			buf -> new Open(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
				new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readVarInt()));

		@Override
		public Type<Open> type() {
			return TYPE;
		}
	}

	/** Client to server: a place was marked where you stand ({@code set}), or the mark was dropped. */
	public record Mark(boolean set, BlockPos pos) implements CustomPacketPayload {
		public static final Type<Mark> TYPE = new Type<>(JaymosPowers.id("chrono_wormhole_mark"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Mark> CODEC = CustomPacketPayload.codec(
			(p, buf) -> {
				buf.writeBoolean(p.set);
				buf.writeLong(p.pos.asLong());
			},
			buf -> new Mark(buf.readBoolean(), BlockPos.of(buf.readLong())));

		@Override
		public Type<Mark> type() {
			return TYPE;
		}
	}

	/** Client to server: the catch key was pressed (drag the foe ahead). */
	public record Catch() implements CustomPacketPayload {
		public static final Type<Catch> TYPE = new Type<>(JaymosPowers.id("chrono_wormhole_catch"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Catch> CODEC = StreamCodec.unit(new Catch());

		@Override
		public Type<Catch> type() {
			return TYPE;
		}
	}

	/** Client to server: your wormhole or drag film has ended (played out, or skipped). */
	public record FilmEnd() implements CustomPacketPayload {
		public static final Type<FilmEnd> TYPE = new Type<>(JaymosPowers.id("chrono_wormhole_end"));
		public static final StreamCodec<RegistryFriendlyByteBuf, FilmEnd> CODEC = StreamCodec.unit(new FilmEnd());

		@Override
		public Type<FilmEnd> type() {
			return TYPE;
		}
	}

	private record Hole(ResourceKey<Level> dim, Vec3 at, Vec3 dir, long until) {
	}

	private record MarkAt(ResourceKey<Level> dim, BlockPos pos) {
	}

	/** A runner's build-up to a wormhole, the hole once open, and when the next can open. */
	private static final class Run {
		int flat;
		@Nullable Hole hole;
		long readyAt;
	}

	/** A trip waiting for its film: who, to where, and what is held meanwhile. */
	private static final class Trip {
		UUID leader;
		final List<UUID> going = new ArrayList<>();
		int fate;
		long start, due;
		@Nullable MarkAt mark;
		@Nullable UUID foe;
		boolean foeNoAi;
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();
	private static final Map<UUID, MarkAt> MARKS = new HashMap<>();
	private static final Map<UUID, Trip> TRIPS = new HashMap<>();
	/** Everyone in a trip's film, to their leader. */
	private static final Map<UUID, UUID> MEMBERS = new HashMap<>();
	private static final Map<UUID, Long> DRAG_READY = new HashMap<>();
	/** Foes held for a drag's film: nothing hurts them until they are set down. */
	private static final Set<UUID> HELD = new java.util.HashSet<>();

	private ChronoWormhole() {
	}

	static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Open.TYPE, Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Mark.TYPE, Mark.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Catch.TYPE, Catch.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(FilmEnd.TYPE, FilmEnd.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Mark.TYPE, (payload, ctx) -> mark(ctx.player(), payload));
		ServerPlayNetworking.registerGlobalReceiver(Catch.TYPE, (payload, ctx) -> drag(ctx.player()));
		ServerPlayNetworking.registerGlobalReceiver(FilmEnd.TYPE, (payload, ctx) -> ended(ctx.player()));
		ServerTickEvents.END_SERVER_TICK.register(ChronoWormhole::tick);
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> !HELD.contains(entity.getUUID()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Trip trip : new ArrayList<>(TRIPS.values())) {
				release(server, trip);
			}
			RUNS.clear();
			MARKS.clear();
			TRIPS.clear();
			HELD.clear();
			MEMBERS.clear();
			DRAG_READY.clear();
		});
	}

	/** A runner leaves or loses the power: no hole, no mark, nothing waiting. */
	static void forget(ServerPlayer player) {
		Run run = RUNS.remove(player.getUUID());
		if (run != null && run.hole != null) {
			sendOpen(player, player.level(), run.hole, 0);
		}
		MARKS.remove(player.getUUID());
		DRAG_READY.remove(player.getUUID());
		Trip trip = TRIPS.get(player.getUUID());
		if (trip != null) {
			release(player.level().getServer(), trip);
			TRIPS.remove(player.getUUID());
		}
		MEMBERS.remove(player.getUUID());
	}

	// ---------------------------------------------------------------- the mark

	/** The client marked a place (where it stands, so no one can mark a place they have not been) or dropped the mark. */
	private static void mark(ServerPlayer player, Mark payload) {
		if (!payload.set()) {
			MARKS.remove(player.getUUID());
			return;
		}
		if (payload.pos().distSqr(player.blockPosition()) > 9.0) {
			return;
		}
		MARKS.put(player.getUUID(), new MarkAt(player.level().dimension(), payload.pos().immutable()));
	}

	private static @Nullable MarkAt markHere(ServerPlayer player) {
		MarkAt mark = MARKS.get(player.getUUID());
		return mark != null && mark.dim().equals(player.level().dimension()) ? mark : null;
	}

	// ---------------------------------------------------------------- the hole

	private static boolean ready(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && !player.isPassenger() && Powers.has(player, ChronoPower.INSTANCE)
			&& ChronoSpeed.overclocked(player) && !Downed.isDowned(player);
	}

	private static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			Run run = RUNS.get(id);
			boolean blur = player.getAttached(ChronoMomentum.BLUR) != null;
			if (run == null && !blur || MEMBERS.containsKey(id)) {
				continue;
			}
			if (!ready(player)) {
				if (run != null) {
					if (run.hole != null) {
						sendOpen(player, player.level(), run.hole, 0);
					}
					RUNS.remove(id);
				}
				continue;
			}
			if (run == null) {
				run = new Run();
				RUNS.put(id, run);
			}
			if (run.hole != null) {
				Hole hole = run.hole;
				ServerLevel level = player.level();
				if (now >= hole.until() || !hole.dim().equals(level.dimension())) {
					sendOpen(player, level, hole, 0);
					run.hole = null;
					run.flat = 0;
					run.readyAt = now + COOLDOWN;
				} else if (player.getBoundingBox().getCenter().distanceToSqr(hole.at()) <= ENTER * ENTER && ChronoRapid.speed(player) >= 0.2) {
					enter(player, run, hole, now);
				} else if (now % 4 == 0) {
					sparks(level, hole);
				}
				continue;
			}
			boolean flatOut = blur && player.isSprinting() && ChronoGears.gear(player) >= ChronoGears.GEARS;
			run.flat = flatOut ? run.flat + 1 : Math.max(0, run.flat - 2);
			if (run.flat >= OPEN_TICKS && now >= run.readyAt) {
				if (markHere(player) != null || Future.mayLeap(player)) {
					open(player, run, now);
				} else {
					run.flat = 0;
					run.readyAt = now + FAILED_WAIT;
				}
			} else if (run.flat == 0 && !blur && now >= run.readyAt) {
				RUNS.remove(id);
			}
		}
		if (!TRIPS.isEmpty()) {
			for (Trip trip : new ArrayList<>(TRIPS.values())) {
				if (now >= trip.due) {
					finish(server, trip);
				}
			}
		}
	}

	/** The air ahead of {@code player} tears open. */
	private static void open(ServerPlayer player, Run run, long now) {
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle().horizontal();
		Vec3 dir = look.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, player.getYRot()) : look.normalize();
		Vec3 eye = player.getEyePosition();
		double reach = AHEAD;
		HitResult wall = level.clip(new ClipContext(eye, eye.add(dir.scale(AHEAD + 1.0)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (wall.getType() != HitResult.Type.MISS) {
			reach = Math.max(MIN_AHEAD, wall.getLocation().distanceTo(eye) - 2.0);
		}
		Vec3 at = new Vec3(player.getX(), player.getY() + HEIGHT, player.getZ()).add(dir.scale(reach));
		run.hole = new Hole(level.dimension(), at, dir, now + HOLE_LIFE);
		sendOpen(player, level, run.hole, HOLE_LIFE);
		MagicFx.ring(level, at.subtract(0.0, HEIGHT - 0.1, 0.0), 3.5F, BLUE);
		MagicFx.blast(level, at, 2.4F, GOLD);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 0.5F, 1.6F);
	}

	private static void sparks(ServerLevel level, Hole hole) {
		level.sendParticles(MagicFx.spark(BLUE, 0.7F), hole.at().x, hole.at().y, hole.at().z, 2, 1.6, 1.6, 1.6, 0.0);
	}

	private static void sendOpen(ServerPlayer owner, ServerLevel level, Hole hole, int life) {
		Open packet = new Open(owner.getId(), hole.at(), hole.dir(), life);
		for (ServerPlayer other : level.players()) {
			if (other.distanceToSqr(hole.at()) <= SYNC_RADIUS * SYNC_RADIUS && ServerPlayNetworking.canSend(other, Open.TYPE)) {
				ServerPlayNetworking.send(other, packet);
			}
		}
	}

	/** The runner reached the hole: the film starts for them and those running close behind, and the trip waits for it. */
	private static void enter(ServerPlayer leader, Run run, Hole hole, long now) {
		ServerLevel level = leader.level();
		MarkAt mark = markHere(leader);
		int fate = mark != null ? FATE_MARKED : FATE_FUTURE;
		if (fate == FATE_FUTURE && !Future.allowed(leader)) {
			// told why (as Sneak + B tells it); the hole closes
			sendOpen(leader, level, hole, 0);
			run.hole = null;
			run.flat = 0;
			run.readyAt = now + FAILED_WAIT;
			return;
		}
		sendOpen(leader, level, hole, 0);
		run.hole = null;
		run.flat = 0;
		run.readyAt = now + COOLDOWN;
		Trip trip = new Trip();
		trip.leader = leader.getUUID();
		trip.fate = fate;
		trip.mark = mark;
		trip.start = now;
		trip.due = now + FILM_TICKS + FINISH_GRACE;
		trip.going.add(leader.getUUID());
		for (ServerPlayer other : level.getEntitiesOfClass(ServerPlayer.class, leader.getBoundingBox().inflate(FOLLOW_RADIUS))) {
			if (other != leader && other.isAlive() && !other.isSpectator() && other.isSprinting() && !MEMBERS.containsKey(other.getUUID())) {
				trip.going.add(other.getUUID());
			}
		}
		Vec3 ahead = leader.position().add(hole.dir().scale(16.0));
		for (UUID id : trip.going) {
			ServerPlayer p = leader.level().getServer().getPlayerList().getPlayer(id);
			if (p == null) {
				continue;
			}
			MEMBERS.put(id, trip.leader);
			p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, FILM_TICKS + FINISH_GRACE + 10, 4, false, false, false));
			UltimateFilm.play(p, UltimateFilm.WORMHOLE, FILM_TICKS, ahead, null, 0.0);
		}
		TRIPS.put(trip.leader, trip);
		com.jaymospowers.core.trials.PowerTrials.count(leader, "chrono.wormhole", 1);
		MagicFx.blast(level, hole.at(), 3.0F, 0xFFFFFF);
		level.playSound(null, hole.at().x, hole.at().y, hole.at().z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.5F);
		Shakes.around(level, hole.at(), 16.0, 0.8F);
	}

	/** A player's film is over: if they lead the trip, it finishes now. */
	private static void ended(ServerPlayer player) {
		Trip trip = TRIPS.get(player.getUUID());
		if (trip != null && player.level().getServer().getTickCount() - trip.start >= MIN_FILM) {
			finish(player.level().getServer(), trip);
		}
	}

	/** The film's over: the trip lands. */
	private static void finish(MinecraftServer server, Trip trip) {
		if (TRIPS.remove(trip.leader) == null) {
			return;
		}
		List<ServerPlayer> going = new ArrayList<>();
		for (UUID id : trip.going) {
			MEMBERS.remove(id);
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null && p.isAlive() && !p.isSpectator()) {
				going.add(p);
			}
		}
		ServerPlayer leader = server.getPlayerList().getPlayer(trip.leader);
		if (leader == null || !leader.isAlive()) {
			release(server, trip);
			return;
		}
		switch (trip.fate) {
			case FATE_FUTURE -> Future.go(leader, going);
			case FATE_MARKED -> arrive(server, trip, going);
			default -> uppercut(server, trip, leader);
		}
	}

	/** A marked place: everyone arrives there, in a ring round it, facing as they ran. */
	private static void arrive(MinecraftServer server, Trip trip, List<ServerPlayer> going) {
		MarkAt mark = trip.mark;
		ServerLevel to = mark == null ? null : server.getLevel(mark.dim());
		if (to == null) {
			return;
		}
		Vec3 centre = Vec3.atBottomCenterOf(mark.pos());
		for (int i = 0; i < going.size(); i++) {
			ServerPlayer p = going.get(i);
			double angle = i * 2.4;
			double radius = i == 0 ? 0.0 : 1.1;
			Vec3 at = centre.add(Math.cos(angle) * radius, 0.0, Math.sin(angle) * radius);
			MagicFx.blast(p.level(), p.position().add(0.0, 1.0, 0.0), 1.6F, BLUE);
			if (p.isPassenger()) {
				p.stopRiding();
			}
			p.teleportTo(to, at.x, at.y, at.z, Set.of(), p.getYRot(), p.getXRot(), true);
			MagicFx.blast(to, at.add(0.0, 1.0, 0.0), 1.6F, GOLD);
			to.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 0.8F, 1.3F);
		}
	}

	// ---------------------------------------------------------------- dragging a foe

	/** The catch key: in top gear, the foe ahead is dragged down the wormhole by a film. */
	private static void drag(ServerPlayer player) {
		long now = player.level().getServer().getTickCount();
		if (!ready(player) || player.getAttached(ChronoMomentum.BLUR) == null || MEMBERS.containsKey(player.getUUID())
			|| UltimateFilm.filming(player) || now < DRAG_READY.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		LivingEntity foe = foeAhead(player);
		if (foe == null) {
			return;
		}
		DRAG_READY.put(player.getUUID(), now + DRAG_COOLDOWN);
		Run run = RUNS.get(player.getUUID());
		if (run != null && run.hole != null) {
			sendOpen(player, player.level(), run.hole, 0);
			run.hole = null;
			run.flat = 0;
		}
		Trip trip = new Trip();
		trip.leader = player.getUUID();
		trip.fate = FATE_DRAG;
		trip.start = now;
		trip.due = now + Math.round(DRAG_TICKS * DRAG_FINISH_AT);
		trip.going.add(player.getUUID());
		trip.foe = foe.getUUID();
		HELD.add(foe.getUUID());
		if (foe instanceof Mob mob) {
			trip.foeNoAi = mob.isNoAi();
			mob.setNoAi(true);
		}
		MEMBERS.put(player.getUUID(), player.getUUID());
		TRIPS.put(player.getUUID(), trip);
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, DRAG_TICKS + 40, 4, false, false, false));
		com.jaymospowers.core.trials.PowerTrials.count(player, "chrono.drag", 1);
		UltimateFilm.play(player, UltimateFilm.DRAG, DRAG_TICKS, foe.position(), foe, 0.0);
		ServerLevel level = player.level();
		MagicFx.ring(level, foe.position().add(0.0, 0.1, 0.0), 2.5F, BLUE);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS, 1.0F, 0.5F);
	}

	/** The creature nearest the line of your sight within {@link #CATCH_REACH}, never a player or a great boss. */
	private static @Nullable LivingEntity foeAhead(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(CATCH_REACH + 1.0),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof Player) && !(e instanceof EnderDragon) && !(e instanceof WitherBoss)
				&& PickKit.enemy(player, e))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > CATCH_REACH + e.getBbWidth() * 0.5 || distance > 1.0E-3 && to.scale(1.0 / distance).dot(look) < CATCH_CONE
				|| distance >= bestDistance || !player.hasLineOfSight(e)) {
				continue;
			}
			best = e;
			bestDistance = distance;
		}
		return best;
	}

	/** Back where they started: the foe is set down in front of the runner and thrown up by an uppercut; the slam follows. */
	private static void uppercut(MinecraftServer server, Trip trip, ServerPlayer leader) {
		LivingEntity foe = foeOf(server, trip);
		release(server, trip);
		if (foe == null || !foe.isAlive()) {
			return;
		}
		ServerLevel level = leader.level();
		Vec3 look = Vec3.directionFromRotation(0.0F, leader.getYRot());
		Vec3 at = leader.position().add(look.scale(1.5));
		if (foe.level() == level) {
			foe.teleportTo(level, at.x, at.y, at.z, Set.of(), foe.getYRot(), foe.getXRot(), false);
		}
		leader.setAttached(ChronoFlow.MOVE, new ChronoFlow.Move(ChronoFlow.UPPERCUT, level.getGameTime(), foe.getId(), 1));
		leader.swing(InteractionHand.MAIN_HAND, new SwingAnimation(SwingAnimationType.WHACK, 8), true);
		foe.setInvulnerableTime(0);
		foe.hurtServer(level, level.damageSources().indirectMagic(leader, leader), UPPERCUT_DAMAGE + UPPERCUT_PER_LEVEL * Mastery.unlocked(leader));
		foe.setDeltaMovement(look.x * 0.2, LIFT, look.z * 0.2);
		foe.syncVelocity = true;
		ChronoFlow.reel(foe, ChronoFlow.FLUNG, 0);
		Vec3 c = foe.getBoundingBox().getCenter();
		MagicFx.ring(level, at.add(0.0, 0.1, 0.0), 2.0F, 0xFFFFFF);
		MagicFx.blast(level, c, 1.2F, GOLD);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.7F);
		Shakes.send(leader, 0.7F);
		UUID foeId = foe.getUUID();
		UltimateFilm.later(leader, SLAM_WAIT, p -> slam(p, foeId));
	}

	/** The lightning slam: a bolt out of the sky drives the foe back down. */
	private static void slam(ServerPlayer leader, UUID foeId) {
		ServerLevel level = leader.level();
		Entity found = level.getEntity(foeId);
		if (!(found instanceof LivingEntity foe) || !foe.isAlive()) {
			return;
		}
		Vec3 c = foe.getBoundingBox().getCenter();
		MagicFx.bolt(level, c.add(0.0, 14.0, 0.0), c, 0.5F, BLUE, 8);
		foe.setInvulnerableTime(0);
		foe.hurtServer(level, level.damageSources().indirectMagic(leader, leader), SLAM_DAMAGE + SLAM_PER_LEVEL * Mastery.unlocked(leader));
		foe.setDeltaMovement(0.0, -DROP, 0.0);
		foe.syncVelocity = true;
		MagicFx.ring(level, foe.position().add(0.0, 0.1, 0.0), 3.5F, GOLD);
		MagicFx.blast(level, c, 1.8F, 0xFFFFFF);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.2F, 1.4F);
		level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
		Shakes.around(level, c, 14.0, 1.0F);
	}

	private static @Nullable LivingEntity foeOf(MinecraftServer server, Trip trip) {
		if (trip.foe == null) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(trip.foe) instanceof LivingEntity foe) {
				return foe;
			}
		}
		return null;
	}

	/** Gives a held foe back to the world as it was, and takes the members out of the film. */
	private static void release(MinecraftServer server, Trip trip) {
		LivingEntity foe = foeOf(server, trip);
		if (trip.foe != null) {
			HELD.remove(trip.foe);
		}
		if (foe != null) {
			if (foe instanceof Mob mob) {
				mob.setNoAi(trip.foeNoAi);
			}
		}
		trip.foe = null;
		for (UUID id : trip.going) {
			MEMBERS.remove(id);
		}
		TRIPS.remove(trip.leader);
	}
}
