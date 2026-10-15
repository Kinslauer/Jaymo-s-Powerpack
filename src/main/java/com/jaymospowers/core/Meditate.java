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
 * <p>A Z Fighter or a Gravity user doesn't sit: he shadowboxes, throwing punches and kicks at nothing (combos from
 * {@link Shadowbox}, more as he unlocks abilities), and that's his meditation (for a Z Fighter, ZFighterTraining gives
 * the TP, ki and form mastery too). Under heavy gravity (someone else's Immense Field; for a Z Fighter also Gravity's
 * weight) he sinks into a heavier stance. A Gravity user's shadowboxing earns mastery only beside a partner (someone
 * else shadowboxing within {@link #PARTNER_RANGE}); in his own field that also speeds him up (GravityTrainingField).
 *
 * <p>Moving, leaving the ground, or being hurt breaks it; keep holding the key and you settle in again once still. The
 * state is synced to everyone so every client draws the pose (MeditatePose).
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

	public static final int MEDITATING = 0, SHADOWBOXING = 1, SHADOWBOXING_HEAVY = 2;

	/**
	 * On a meditating player: since when (game time), how ({@link #MEDITATING}, {@link #SHADOWBOXING}...) and how many
	 * shadowboxing combos he has ({@link Shadowbox#combos}). Synced to everyone.
	 */
	public record State(long since, int kind, int combos) {
		public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, State::since,
			ByteBufCodecs.VAR_INT, State::kind,
			ByteBufCodecs.VAR_INT, State::combos,
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
		if (Powers.has(player, com.jaymospowers.power.zfighter.ZFighterPower.INSTANCE)) {
			return com.jaymospowers.power.zfighter.ZFighterTraining.heavyGravity(player) ? SHADOWBOXING_HEAVY : SHADOWBOXING;
		}
		if (Powers.has(player, com.jaymospowers.power.gravity.GravityPower.INSTANCE)) {
			// his own field doesn't weigh on him; someone else's does
			return com.jaymospowers.power.gravity.GravityTrainingField.inField(player) ? SHADOWBOXING_HEAVY : SHADOWBOXING;
		}
		return MEDITATING;
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
			int combos = Shadowbox.combos(Mastery.unlocked(player));
			if (state == null) {
				state = new State(now, kind, combos);
				player.setAttached(STATE, state);
			} else if (state.kind() != kind || state.combos() != combos) {
				// stepping into (or out of) a field, or a new combo learned: the same session, a different rhythm
				state = new State(state.since(), kind, combos);
				player.setAttached(STATE, state);
			}
			long time = now - state.since();
			if (time < SETTLE) {
				continue;
			}
			int perPoint = TICKS_PER_POINT[Math.min(TICKS_PER_POINT.length - 1, rank(player))];
			// a Gravity user's shadowboxing only pays beside a partner (another shadowboxer)
			boolean pays = !Powers.has(player, com.jaymospowers.power.gravity.GravityPower.INSTANCE) || partnered(player);
			if (pays && (time - SETTLE) % perPoint == perPoint - 1) {
				Mastery.practice(player, 1);
			}
			if (kind == MEDITATING) {
				if (time % 20 == 0) {
					level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.4, 0.3, 0.4, 0.15);
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
					level.sendParticles(kind == SHADOWBOXING_HEAVY ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.CLOUD,
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

	/** Whether this player is shadowboxing (settled in): a Z Fighter or a Gravity user. */
	public static boolean shadowboxing(Player player) {
		State state = of(player);
		return state != null && state.kind() != MEDITATING && ticks(player) >= SETTLE;
	}
}
