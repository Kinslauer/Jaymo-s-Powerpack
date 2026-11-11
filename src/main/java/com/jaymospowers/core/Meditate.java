package com.jaymospowers.core;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.JaymosPowers;
import io.netty.buffer.ByteBuf;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Meditating, for every player: hold the Meditate key (I) standing still on the ground and you sit down cross-legged
 * and breathe. Once settled, it earns mastery toward your power's next move ({@link Mastery}). It's opened in Jaymo's
 * Kingdoms' Survival tree (the Meditation perk, K); more ranks make it quicker. Without Kingdoms anyone can meditate.
 *
 * <p>A Z Fighter or a Gravity user doesn't sit: he shadowboxes, throwing punches and kicks at nothing (combos from
 * {@link Shadowbox}, more as he unlocks abilities), and that's his meditation (for a Z Fighter, ZFighterTraining gives
 * the TP, ki and form mastery too). Under heavy gravity (someone else's Immense Field; for a Z Fighter also Gravity's
 * weight) he sinks into a heavier stance. A Gravity user's shadowboxing earns mastery only beside a partner (someone
 * else shadowboxing within {@link #PARTNER_RANGE}); in his own field that also speeds him up (GravityTrainingField).
 *
 * <p>Moving, leaving the ground, or being hurt breaks it; keep holding the key and you settle in again once still. The
 * state is synced to everyone so every client draws the pose (MeditatePose).
 *
 * <p>(Motion patch 0) <b>Sit anywhere</b>, after Actions &amp; Stuff's idea (Andy: "make the look down the meditation
 * trigger as well, leaving I to shadowboxing for those that have it"): anyone who stands still, sneaking and looking
 * down past {@link #SIT_PITCH}, for {@link #SIT_TICKS} (5 s) sits down cross-legged where they are, and stays sat after
 * letting go of Sneak, until they move, jump or are hurt. With the Meditation perk that sitting is meditating (the
 * mastery comes as it always did); without it, it's only a rest. The Meditate key (I) is the shadowboxers' now (a Z
 * Fighter or a Gravity user, still opened by the Meditation perk); anyone else pressing it is told how to sit.
 *
 * <p>Tiv: "wearing them should turn your meditate into shadowboxing". Anyone in Training Weights (either pair) shadowboxes
 * on the key too, whatever their power, and every second of it builds Might ({@link Training#weightsMight}).
 *
 * <p>(0.19.67) <b>Seats and the Sit key</b> ({@link Sitting}): right-click a stair, a bottom slab or a block in
 * {@code #jaymospowers:seats} with an empty hand, or press the Sit key, and you sit at once (on the seat you look at,
 * else on the ground). Pressing it again while sat changes how you sit ({@link #POSES} of each); pressing Sneak gets you
 * up. Sitting on a seat counts as meditating just like the ground.
 */
public final class Meditate {
	/** Ticks of stillness before it starts counting (sitting down, finding the rhythm). */
	public static final int SETTLE = 40;
	/**
	 * One practice point per this many ticks meditating, by Meditation rank (1-3; without Kingdoms, rank 1). Practice is
	 * capped by Mastery's allowance: rank 2 alone already brings an ability down to a day; rank 3 only pays off in a field.
	 */
	static final int[] TICKS_PER_POINT = {900, 900, 600, 400};
	/** How far you may drift in a tick and still be still (blocks, squared). */
	static final double STILL = 0.0025;
	/** After a hit, this long before you can settle again (ticks). */
	static final int HURT_BREAK = 40;
	/** (Motion patch 0) Sit anywhere: looking down at least this far (degrees), sneaking and still, this long (ticks), sits you down. */
	public static final float SIT_PITCH = 65.0F;
	public static final int SIT_TICKS = 100;

	public static final int MEDITATING = 0, SHADOWBOXING = 1, SHADOWBOXING_HEAVY = 2;
	/** (0.19.67) How many ways there are to sit, on the ground and on a seat each (the Sit key goes round them). */
	public static final int POSES = 3;

	/**
	 * On a meditating player: since when (game time), how ({@link #MEDITATING}, {@link #SHADOWBOXING}...) and how many
	 * shadowboxing combos he has ({@link Shadowbox#combos}). Synced to everyone. (0.19.67) Sitting also carries which
	 * way they sit ({@code pose}, 0 to {@link #POSES} - 1) and, on a seat, the way the seat faces ({@code seatYaw},
	 * degrees; NaN on the ground).
	 */
	public record State(long since, int kind, int combos, int pose, float seatYaw) {
		public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, State::since,
			ByteBufCodecs.VAR_INT, State::kind,
			ByteBufCodecs.VAR_INT, State::combos,
			ByteBufCodecs.VAR_INT, State::pose,
			ByteBufCodecs.FLOAT, State::seatYaw,
			State::new);

		/** Whether they sit on a seat (not on the ground). */
		public boolean seated() {
			return !Float.isNaN(seatYaw);
		}
	}

	/** (0.19.67) A seat someone sits on: the block, and where and which way they sit. */
	record Seat(BlockPos pos, Vec3 at, float yaw) {
	}

	public static final AttachmentType<State> STATE = AttachmentRegistry.create(JaymosPowers.id("meditating"),
		builder -> builder.syncWith(State.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/** Client → server: the Meditate key went down ({@code true}) or up. */
	public record Key(boolean down) implements CustomPacketPayload {
		public static final Type<Key> TYPE = new Type<>(JaymosPowers.id("meditate_key"));
		public static final StreamCodec<ByteBuf, Key> CODEC = ByteBufCodecs.BOOL.map(Key::new, Key::down);

		@Override
		public Type<Key> type() {
			return TYPE;
		}
	}

	/** Players holding the key; where each was last tick; when each may settle again after a hit (game time). */
	private static final Set<UUID> HELD = new HashSet<>();
	/** (Motion patch 0) Players sat down by looking down (no key held); how long each has been looking down, sneaking and still. */
	private static final Set<UUID> SITTING = new HashSet<>();
	private static final Map<UUID, Integer> LOOKING_DOWN = new HashMap<>();
	private static final Map<UUID, Vec3> LAST = new HashMap<>();
	private static final Map<UUID, Long> BROKEN_UNTIL = new HashMap<>();
	/** (0.19.67) Who sits on a seat, and on which; whether each sitter was sneaking last tick; the last way each sat. */
	private static final Map<UUID, Seat> SEATED = new HashMap<>();
	private static final Map<UUID, Boolean> SNEAKING = new HashMap<>();
	private static final Map<UUID, Integer> GROUND_POSE = new HashMap<>();
	private static final Map<UUID, Integer> SEAT_POSE = new HashMap<>();

	private static final Method KINGDOMS_RANK;

	static {
		Method rank = null;
		if (FabricLoader.getInstance().isModLoaded("jaymoskingdoms")) {
			try {
				rank = Class.forName("com.jaymoskingdoms.Skills").getMethod("rank", Player.class, String.class);
			} catch (ReflectiveOperationException | LinkageError e) {
				rank = null;
			}
		}
		KINGDOMS_RANK = rank;
	}

	private Meditate() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Key.TYPE, Key.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Key.TYPE, (payload, ctx) -> key(ctx.player(), payload.down()));
		ServerTickEvents.END_SERVER_TICK.register(Meditate::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F) {
				LOOKING_DOWN.remove(player.getUUID());
				if (HELD.contains(player.getUUID()) || SITTING.remove(player.getUUID())) {
					BROKEN_UNTIL.put(player.getUUID(), player.level().getGameTime() + HURT_BREAK);
					SEATED.remove(player.getUUID());
					stop(player);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HELD.clear();
			LAST.clear();
			BROKEN_UNTIL.clear();
			SITTING.clear();
			LOOKING_DOWN.clear();
			SEATED.clear();
			SNEAKING.clear();
			GROUND_POSE.clear();
			SEAT_POSE.clear();
		});
	}

	/** The Meditation rank: 0 locked, 1-3. Without Kingdoms everyone has rank 1. */
	public static int rank(Player player) {
		if (KINGDOMS_RANK == null) {
			return 1;
		}
		try {
			return (int)KINGDOMS_RANK.invoke(null, player, "meditation");
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 1;
		}
	}

	private static void key(ServerPlayer player, boolean down) {
		if (!down) {
			// (Motion patch 0) letting go only ends a held shadowbox, never a sit
			if (HELD.remove(player.getUUID())) {
				stop(player);
			}
			return;
		}
		if (kind(player) == MEDITATING) {
			// (Motion patch 0) the key is the shadowboxers'; everyone else sits by looking down
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.meditate.sit").withStyle(ChatFormatting.GRAY));
			return;
		}
		if (rank(player) <= 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.meditate.locked").withStyle(ChatFormatting.GRAY));
			return;
		}
		SITTING.remove(player.getUUID());
		HELD.add(player.getUUID());
	}

	private static void forget(UUID id) {
		HELD.remove(id);
		LAST.remove(id);
		BROKEN_UNTIL.remove(id);
		SITTING.remove(id);
		LOOKING_DOWN.remove(id);
		SEATED.remove(id);
		SNEAKING.remove(id);
		GROUND_POSE.remove(id);
		SEAT_POSE.remove(id);
	}

	/** (0.19.67) Whether this player sits (on the ground or a seat), not counting a held shadowbox. */
	static boolean sitting(ServerPlayer player) {
		return SITTING.contains(player.getUUID());
	}

	/** (0.19.67) Whether someone already sits on this block. */
	static boolean taken(ServerLevel level, BlockPos pos) {
		for (Map.Entry<UUID, Seat> e : SEATED.entrySet()) {
			if (e.getValue().pos().equals(pos)) {
				ServerPlayer other = level.getServer().getPlayerList().getPlayer(e.getKey());
				if (other != null && other.level() == level) {
					return true;
				}
			}
		}
		return false;
	}

	/** (0.19.67) Whether this player could sit down right now (on the ground; a seat checks its own ground). */
	static boolean canSit(ServerPlayer player) {
		return !HELD.contains(player.getUUID()) && !SITTING.contains(player.getUUID()) && canMeditate(player)
			&& BROKEN_UNTIL.getOrDefault(player.getUUID(), 0L) <= player.level().getGameTime();
	}

	/** (0.19.67) Sits this player down at once: on {@code seat} (moved onto it, facing its way), or on the ground. */
	static void sitDown(ServerPlayer player, @Nullable Seat seat) {
		UUID id = player.getUUID();
		LOOKING_DOWN.remove(id);
		if (seat != null) {
			player.connection.teleport(seat.at().x, seat.at().y, seat.at().z, seat.yaw(), player.getXRot());
			SEATED.put(id, seat);
		}
		LAST.put(id, player.position());
		SNEAKING.put(id, player.isShiftKeyDown());
		SITTING.add(id);
	}

	/** (0.19.67) The Sit key while sat: the next way of sitting (kept for next time). */
	static void nextPose(ServerPlayer player) {
		UUID id = player.getUUID();
		Map<UUID, Integer> poses = SEATED.containsKey(id) ? SEAT_POSE : GROUND_POSE;
		poses.merge(id, 1, (a, b) -> (a + b) % POSES);
		State state = player.getAttached(STATE);
		if (state != null) {
			player.setAttached(STATE, new State(state.since(), state.kind(), state.combos(), poses.get(id), state.seatYaw()));
		}
	}

	private static void stop(ServerPlayer player) {
		if (player.hasAttached(STATE)) {
			player.removeAttached(STATE);
		}
	}

	/** The kind of meditation this player falls into right now. */
	private static int kind(ServerPlayer player) {
		if (Powers.has(player, com.jaymospowers.power.zfighter.ZFighterPower.INSTANCE)) {
			return com.jaymospowers.power.zfighter.ZFighterTraining.heavyGravity(player) ? SHADOWBOXING_HEAVY : SHADOWBOXING;
		}
		if (Powers.has(player, com.jaymospowers.power.gravity.GravityPower.INSTANCE)) {
			// his own field doesn't weigh on him; someone else's does
			return com.jaymospowers.power.gravity.GravityTrainingField.inField(player) ? SHADOWBOXING_HEAVY : SHADOWBOXING;
		}
		if (com.jaymospowers.power.zfighter.ZFighterItems.wearsWeights(player)) {
			// training weights make anyone a shadowboxer
			return com.jaymospowers.power.zfighter.ZFighterTraining.heavyGravity(player) ? SHADOWBOXING_HEAVY : SHADOWBOXING;
		}
		return MEDITATING;
	}

	private static boolean canMeditate(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && player.onGround() && !player.isPassenger() && !player.isInWater()
			&& !player.isSleeping() && !player.getAbilities().flying && !player.isFallFlying() && !Crawl.crawling(player);
	}

	/**
	 * (Motion patch 0) Sit anywhere: counts how long each player has stood still, sneaking and looking down, and sits
	 * them down once it's {@link #SIT_TICKS}. Swinging or using an item (building a bridge, digging down) resets it.
	 */
	private static void watchForSitting(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			if (HELD.contains(id) || SITTING.contains(id)) {
				continue;
			}
			Vec3 at = player.position();
			Vec3 was = LAST.put(id, at);
			boolean still = was != null && at.distanceToSqr(was) < STILL;
			if (!still || !player.isShiftKeyDown() || player.getXRot() < SIT_PITCH || player.isSwinging() || player.isUsingItem()
				|| !canMeditate(player) || BROKEN_UNTIL.getOrDefault(id, 0L) > player.level().getGameTime()) {
				LOOKING_DOWN.remove(id);
				continue;
			}
			if (LOOKING_DOWN.merge(id, 1, Integer::sum) >= SIT_TICKS) {
				sitDown(player, null);
			}
		}
	}

	private static void tick(MinecraftServer server) {
		watchForSitting(server);
		if (HELD.isEmpty() && SITTING.isEmpty()) {
			return;
		}
		Set<UUID> sessions = new HashSet<>(HELD);
		sessions.addAll(SITTING);
		for (UUID id : sessions) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null) {
				forget(id);
				continue;
			}
			boolean sat = !HELD.contains(id);
			ServerLevel level = player.level();
			long now = level.getGameTime();
			Vec3 at = player.position();
			Vec3 was = LAST.put(id, at);
			boolean still = was != null && at.distanceToSqr(was) < STILL;
			// (0.19.67) a sitter gets up on pressing Sneak (not by still holding the Sneak they sat down with)
			boolean sneak = player.isShiftKeyDown();
			boolean stoodUp = sat && sneak && !SNEAKING.getOrDefault(id, sneak);
			if (sat) {
				SNEAKING.put(id, sneak);
			}
			Seat seat = sat ? SEATED.get(id) : null;
			if (!still || !canMeditate(player) || BROKEN_UNTIL.getOrDefault(id, 0L) > now || stoodUp
				|| seat != null && Sitting.seat(level, seat.pos(), player) == null) {
				// moved, jumped, hit, stood up or the seat went: a held key settles in again once still; a sit is over until you sit again
				SITTING.remove(id);
				SEATED.remove(id);
				SNEAKING.remove(id);
				stop(player);
				continue;
			}
			State state = player.getAttached(STATE);
			// sat down: always the sit (any of its poses), shadowboxers too
			int kind = sat ? MEDITATING : kind(player);
			int combos = Shadowbox.combos(Mastery.unlocked(player));
			int pose = sat ? (seat != null ? SEAT_POSE : GROUND_POSE).getOrDefault(id, 0) : 0;
			float seatYaw = seat != null ? seat.yaw() : Float.NaN;
			if (state == null) {
				state = new State(now, kind, combos, pose, seatYaw);
				player.setAttached(STATE, state);
			} else if (state.kind() != kind || state.combos() != combos) {
				// stepping into (or out of) a field, or a new combo learned: the same session, a different rhythm
				state = new State(state.since(), kind, combos, state.pose(), state.seatYaw());
				player.setAttached(STATE, state);
			}
			long satFor = now - state.since();
			if (sat && satFor >= SETTLE) {
				Sitting.rest(player, satFor - SETTLE);
			}
			long time = now - state.since();
			if (time < SETTLE) {
				continue;
			}
			if (!sat && (time - SETTLE) % 20 == 19 && com.jaymospowers.power.zfighter.ZFighterItems.wearsWeights(player)) {
				// a second of shadowboxing in training weights: Might, for everyone
				Training.weightsMight(player);
			}
			int rank = rank(player);
			int perPoint = TICKS_PER_POINT[Math.min(TICKS_PER_POINT.length - 1, rank)];
			// a Gravity user's shadowboxing only pays beside a partner (another shadowboxer); a plain sit only with the perk
			boolean pays = sat ? rank > 0 : !Powers.has(player, com.jaymospowers.power.gravity.GravityPower.INSTANCE) || partnered(player);
			// (0.19.29) a training spot (the cold for Wind, fire for Smoke, a Weighted Gi...) pays twice as often and lets more through
			String spot = pays ? Training.spot(player) : null;
			if (spot != null) {
				perPoint = Math.max(20, Math.round(perPoint * Training.SPOT_PACE));
				if ((time - SETTLE) % Training.SPOT_ALLOWANCE_TICKS == Training.SPOT_ALLOWANCE_TICKS - 1) {
					Mastery.spotAllowance(player);
				}
			}
			if (pays && (time - SETTLE) % perPoint == perPoint - 1) {
				Mastery.practice(player, 1);
				if (spot != null) {
					Training.trained(player, spot);
				}
			}
			if (kind == MEDITATING) {
				if (pays && time % 20 == 0) {
					level.sendParticles(MagicFx.GLYPH, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.4, 0.3, 0.4, 0.15);
				}
			} else {
				Shadowbox.Step step = Shadowbox.step(time, state.combos());
				if (step.move() != null && step.into() == (int)(step.length() * Shadowbox.STRIKE)) {
					// a blow thrown at nothing: the swish, a puff of air off the fist (or the foot, lower and heavier)
					boolean kick = step.move().kick;
					Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
					float pitch = (kind == SHADOWBOXING_HEAVY ? 0.7F : 1.1F) * (kick ? 0.8F : 1.0F);
					level.playSound(null, player.getX(), player.getY(), player.getZ(),
						kick ? SoundEvents.PLAYER_ATTACK_SWEEP : SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS, kick ? 0.3F : 0.35F, pitch);
					level.sendParticles(kind == SHADOWBOXING_HEAVY ? MagicFx.VOID : ParticleTypes.CLOUD,
						player.getX() + look.x * (kick ? 1.1 : 0.8), player.getY() + (kick ? 0.9 : 1.3), player.getZ() + look.z * (kick ? 1.1 : 0.8), 1,
						0.05, 0.05, 0.05, 0.01);
				}
			}
		}
	}

	/** This player's meditation (on either side), or null. */
	public static @Nullable State of(Player player) {
		return player.getAttached(STATE);
	}

	/** Ticks this player has been meditating (or shadowboxing), or -1 when they aren't. */
	public static long ticks(Player player) {
		State state = of(player);
		return state == null ? -1L : player.level().getGameTime() - state.since();
	}

	/** How near another shadowboxer must be to count as a partner (blocks). */
	static final double PARTNER_RANGE = 5.0;

	/** Whether this player is shadowboxing with someone else shadowboxing beside him. */
	public static boolean partnered(ServerPlayer player) {
		if (!shadowboxing(player)) {
			return false;
		}
		for (ServerPlayer other : player.level().players()) {
			if (other != player && other.distanceToSqr(player) <= PARTNER_RANGE * PARTNER_RANGE && shadowboxing(other)) {
				return true;
			}
		}
		return false;
	}

	/** Whether this player is shadowboxing (settled in): a Z Fighter, a Gravity user, or anyone in Training Weights. */
	public static boolean shadowboxing(Player player) {
		State state = of(player);
		return state != null && state.kind() != MEDITATING && ticks(player) >= SETTLE;
	}
}
