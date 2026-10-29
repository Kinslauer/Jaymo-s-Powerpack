package com.jaymoskingdoms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.332) The scent trail. The server notes where every mortal player has walked in the last {@link #KEEP_TICKS} (a
 * point every {@link #STEP_EVERY} ticks while they're on the ground, in a ring buffer of {@link #MAX_POINTS}). A werewolf
 * player (themselves at night, or the beast at any hour) who sneaks for {@link #SNEAK_TICKS} takes a scent: the nearest
 * mortal player within {@link #SNIFF_RANGE} blocks, or failing that, whoever left the freshest footstep that near. For
 * {@link #FOLLOW_TICKS} they see that player's trail as faint particles only they see ({@link Trail}, drawn by
 * {@code ScentClient}); the older a point, the fainter it is.
 */
public final class ScentTrails {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** A footstep is noted this often (ticks) if they've moved at least {@link #MIN_STEP} blocks since the last. */
	static final int STEP_EVERY = 30;
	static final double MIN_STEP = 0.8;
	/** How long a footstep is kept (ticks: three minutes), and the most kept for one player. */
	public static final int KEEP_TICKS = 3600;
	static final int MAX_POINTS = 128;
	/** How long they must sneak to take a scent (ticks), how near the one they scent must be (blocks), and the wait before the next (ticks). */
	static final int SNEAK_TICKS = 40, SNIFF_COOLDOWN = 100;
	static final double SNIFF_RANGE = 16.0;
	/** How long they see the trail (ticks), how often it is sent again, and how far from them its points are sent (blocks). */
	static final int FOLLOW_TICKS = 1200, RESEND_EVERY = 40;
	static final double SEND_RANGE = 96.0;

	/** Server → player: a trail to draw ({@code lifeTicks} is how much of the scent is left; 0 clears it), each point with its age (ticks). */
	public record Trail(int lifeTicks, List<Vec3> points, List<Integer> ages) implements CustomPacketPayload {
		public static final Type<Trail> TYPE = new Type<>(JaymosKingdoms.id("scent_trail"));
		public static final StreamCodec<FriendlyByteBuf, Trail> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Trail::lifeTicks,
			Vec3.STREAM_CODEC.apply(ByteBufCodecs.list()), Trail::points, ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Trail::ages, Trail::new);

		@Override
		public Type<Trail> type() {
			return TYPE;
		}
	}

	/** A footstep: where, in which world, when. */
	private record Step(double x, double y, double z, Identifier world, long time) {
	}

	/** Who a werewolf is following by scent, till when, and when to send the trail again. */
	private static final class Follow {
		final UUID target;
		final long until;
		long nextSend;

		Follow(UUID target, long until) {
			this.target = target;
			this.until = until;
		}
	}

	private static final Map<UUID, Deque<Step>> TRAILS = new HashMap<>();
	private static final Map<UUID, Integer> SNEAKING = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();
	private static final Map<UUID, Follow> FOLLOWING = new HashMap<>();

	private ScentTrails() {
	}

	static void register() {
		// (the payload type goes in before anything receives or sends it)
		PayloadTypeRegistry.clientboundPlay().register(Trail.TYPE, Trail.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(ScentTrails::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			SNEAKING.remove(id);
			READY.remove(id);
			FOLLOWING.remove(id);
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TRAILS.clear();
			SNEAKING.clear();
			READY.clear();
			FOLLOWING.clear();
		});
	}

	// ---- every tick ----------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		boolean night = Vampires.night(overworld.getOverworldClockTime());
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((now + player.getId()) % STEP_EVERY == 0) {
				note(player, now);
			}
			sniff(player, now, night);
		}
		if (now % 1200 == 0) {
			TRAILS.values().removeIf(steps -> steps.isEmpty() || now - steps.peekLast().time() > KEEP_TICKS);
		}
	}

	/** A footstep of {@code player}'s, if they're on the ground and have moved. */
	private static void note(ServerPlayer player, long now) {
		if (!Vampires.mortal(player) || !player.onGround() || player.isPassenger()) {
			return;
		}
		Deque<Step> steps = TRAILS.computeIfAbsent(player.getUUID(), k -> new ArrayDeque<>());
		Identifier world = player.level().dimension().identifier();
		Step last = steps.peekLast();
		if (last != null && last.world().equals(world) && player.distanceToSqr(last.x(), last.y(), last.z()) < MIN_STEP * MIN_STEP) {
			return;
		}
		steps.addLast(new Step(player.getX(), player.getY(), player.getZ(), world, now));
		while (steps.size() > MAX_POINTS || !steps.isEmpty() && now - steps.peekFirst().time() > KEEP_TICKS) {
			steps.removeFirst();
		}
	}

	/** A werewolf sneaking long enough takes a scent; one following a trail is sent it as it grows. */
	private static void sniff(ServerPlayer wolf, long now, boolean night) {
		UUID id = wolf.getUUID();
		Follow follow = FOLLOWING.get(id);
		boolean able = FeralCurse.stillCursed(wolf) && wolf.isAlive() && !wolf.isSpectator() && !wolf.isCreative();
		if (follow != null && (!able || now >= follow.until)) {
			FOLLOWING.remove(id);
			ServerPlayNetworking.send(wolf, new Trail(0, List.of(), List.of()));
			follow = null;
		}
		if (follow != null && now >= follow.nextSend) {
			follow.nextSend = now + RESEND_EVERY;
			send(wolf, follow, now);
		}
		if (!able || !wolf.isShiftKeyDown() || Seizures.held(wolf) || !(WerewolfPlayers.beast(wolf) || night)) {
			SNEAKING.remove(id);
			return;
		}
		int held = SNEAKING.merge(id, 1, Integer::sum);
		if (held < SNEAK_TICKS) {
			return;
		}
		SNEAKING.remove(id);
		if (now < READY.getOrDefault(id, 0L)) {
			return;
		}
		READY.put(id, now + SNIFF_COOLDOWN);
		UUID target = pick(wolf);
		wolf.level().playSound(null, wolf.blockPosition(), SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.BIG).adultSounds().pantSound().value(),
			SoundSource.PLAYERS, 0.7F, 0.6F);
		if (target == null) {
			wolf.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feral.scent.none").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			return;
		}
		Follow taken = new Follow(target, now + FOLLOW_TICKS);
		taken.nextSend = now + RESEND_EVERY;
		FOLLOWING.put(id, taken);
		send(wolf, taken, now);
		wolf.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feral.scent.taken").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
	}

	/** Whose scent: the nearest mortal player in range, else whoever left the freshest footstep in range. */
	private static @Nullable UUID pick(ServerPlayer wolf) {
		ServerPlayer nearest = null;
		double best = SNIFF_RANGE * SNIFF_RANGE;
		for (ServerPlayer other : wolf.level().players()) {
			double d = other.distanceToSqr(wolf);
			if (other != wolf && Vampires.mortal(other) && d < best) {
				best = d;
				nearest = other;
			}
		}
		if (nearest != null) {
			return nearest.getUUID();
		}
		Identifier world = wolf.level().dimension().identifier();
		UUID freshest = null;
		long when = Long.MIN_VALUE;
		for (Map.Entry<UUID, Deque<Step>> e : TRAILS.entrySet()) {
			if (e.getKey().equals(wolf.getUUID())) {
				continue;
			}
			for (Step s : e.getValue()) {
				if (s.world().equals(world) && s.time() > when && wolf.distanceToSqr(s.x(), s.y(), s.z()) <= SNIFF_RANGE * SNIFF_RANGE) {
					when = s.time();
					freshest = e.getKey();
				}
			}
		}
		return freshest;
	}

	/** {@code follow}'s trail, as the points near {@code wolf} in their world. */
	private static void send(ServerPlayer wolf, Follow follow, long now) {
		Deque<Step> steps = TRAILS.get(follow.target);
		List<Vec3> points = new ArrayList<>();
		List<Integer> ages = new ArrayList<>();
		if (steps != null) {
			Identifier world = wolf.level().dimension().identifier();
			for (Step s : steps) {
				long age = now - s.time();
				if (s.world().equals(world) && age <= KEEP_TICKS && wolf.distanceToSqr(s.x(), s.y(), s.z()) <= SEND_RANGE * SEND_RANGE) {
					points.add(new Vec3(s.x(), s.y(), s.z()));
					ages.add((int)age);
				}
			}
		}
		ServerPlayNetworking.send(wolf, new Trail((int)Math.max(0L, follow.until - now), points, ages));
	}
}
