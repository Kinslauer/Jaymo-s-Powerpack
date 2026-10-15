package com.jaymospowers.core;

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
 * <p>A Z Fighter doesn't sit: he shadowboxes, throwing punches and kicks at nothing, and that's his meditation
 * (ZFighterTraining gives the TP, ki and form mastery). Under heavy gravity (a Gravity user's Immense Field, or
 * Gravity's weight) shadowboxing is how he trains: it counts many times over, and only it counts toward the field
 * owner's own mastery (GravityTrainingField).
 *
 * <p>Moving, leaving the ground, or being hurt breaks it; keep holding the key and you settle in again once still. The
 * state is synced to everyone so every client draws the pose (MeditatePose).
 */
public final class Meditate {
	/** Ticks of stillness before it starts counting (sitting down, finding the rhythm). */
	public static final int SETTLE = 40;
	/** One mastery point per this many ticks meditating, by Meditation rank (1-3; without Kingdoms, rank 1). */
	static final int[] TICKS_PER_POINT = {600, 600, 450, 300};
	/** How far you may drift in a tick and still be still (blocks, squared). */
	static final double STILL = 0.0025;
	/** After a hit, this long before you can settle again (ticks). */
	static final int HURT_BREAK = 40;

	public static final int MEDITATING = 0, SHADOWBOXING = 1, SHADOWBOXING_HEAVY = 2;

	/** On a meditating player: since when (game time) and how ({@link #MEDITATING}, {@link #SHADOWBOXING}...). Synced to everyone. */
	public record State(long since, int kind) {
		public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, State::since,
			ByteBufCodecs.VAR_INT, State::kind,
			State::new);
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
	private static final Map<UUID, Vec3> LAST = new HashMap<>();
	private static final Map<UUID, Long> BROKEN_UNTIL = new HashMap<>();

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
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F && HELD.contains(player.getUUID())) {
				BROKEN_UNTIL.put(player.getUUID(), player.level().getGameTime() + HURT_BREAK);
				stop(player);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			HELD.clear();
			LAST.clear();
			BROKEN_UNTIL.clear();
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
			forget(player.getUUID());
			stop(player);
			return;
		}
		if (rank(player) <= 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.meditate.locked").withStyle(ChatFormatting.GRAY));
			return;
		}
		HELD.add(player.getUUID());
	}

	private static void forget(UUID id) {
		HELD.remove(id);
		LAST.remove(id);
		BROKEN_UNTIL.remove(id);
	}

	private static void stop(ServerPlayer player) {
		if (player.hasAttached(STATE)) {
			player.removeAttached(STATE);
		}
	}

	/** The kind of meditation this player falls into right now. */
	private static int kind(ServerPlayer player) {
		if (!Powers.has(player, com.jaymospowers.power.zfighter.ZFighterPower.INSTANCE)) {
			return MEDITATING;
		}
		return com.jaymospowers.power.zfighter.ZFighterTraining.heavyGravity(player) ? SHADOWBOXING_HEAVY : SHADOWBOXING;
	}

	private static boolean canMeditate(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && player.onGround() && !player.isPassenger() && !player.isInWater()
			&& !player.isSleeping() && !player.getAbilities().flying && !player.isFallFlying() && !Crawl.crawling(player);
	}

	private static void tick(MinecraftServer server) {
		if (HELD.isEmpty()) {
			return;
		}
		for (UUID id : HELD.toArray(new UUID[0])) {
			ServerPlayer player = server.getPlayerList().getPlayer(id);
			if (player == null) {
				forget(id);
				continue;
			}
			ServerLevel level = player.level();
			long now = level.getGameTime();
			Vec3 at = player.position();
			Vec3 was = LAST.put(id, at);
			boolean still = was != null && at.distanceToSqr(was) < STILL;
			if (!still || !canMeditate(player) || BROKEN_UNTIL.getOrDefault(id, 0L) > now) {
				stop(player);
				continue;
			}
			State state = player.getAttached(STATE);
			int kind = kind(player);
			if (state == null) {
				state = new State(now, kind);
				player.setAttached(STATE, state);
			} else if (state.kind() != kind) {
				// stepping into (or out of) a field: the same session, a different rhythm
				state = new State(state.since(), kind);
				player.setAttached(STATE, state);
			}
			long time = now - state.since();
			if (time < SETTLE) {
				continue;
			}
			int perPoint = TICKS_PER_POINT[Math.min(TICKS_PER_POINT.length - 1, rank(player))];
			if ((time - SETTLE) % perPoint == perPoint - 1) {
				Mastery.practice(player, 1);
			}
			if (kind == MEDITATING) {
				if (time % 20 == 0) {
					level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.4, 0.3, 0.4, 0.15);
				}
			} else if (time % 9 == 0) {
				// a punch thrown at nothing: the swish, a puff of air off the fist
				Vec3 look = Vec3.directionFromRotation(0.0F, player.getYRot());
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE, SoundSource.PLAYERS,
					0.35F, kind == SHADOWBOXING_HEAVY ? 0.7F : 1.1F);
				level.sendParticles(kind == SHADOWBOXING_HEAVY ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.CLOUD, player.getX() + look.x * 0.8,
					player.getY() + 1.3, player.getZ() + look.z * 0.8, 1, 0.05, 0.05, 0.05, 0.01);
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

	/** Whether this Z Fighter is shadowboxing (settled in). */
	public static boolean shadowboxing(Player player) {
		State state = of(player);
		return state != null && state.kind() != MEDITATING && ticks(player) >= SETTLE;
	}
}
