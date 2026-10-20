package com.jaymoskitchen.brewing;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Drunkenness: every fermented drink and the strong spirits add points ({@link Brew#drunk}, {@link #SPIRITS_DRUNK}),
 * up to {@link #MAX_DRUNK}; one wears off every {@link #DECAY_TICKS}. Tipsy (1-3) only sways the screen; drunk
 * ({@link #DRUNK_FROM}+) is slow; very drunk ({@link #VERY_DRUNK_FROM}+) is sick, weak and staggers. Sobering up from
 * a peak of {@link #HANGOVER_FROM} or more leaves a hangover. Jaymo's Powers (the Wounds system) reads {@link #level}.
 * Tuning: the constants below.
 */
public final class Drunk {
	public static final int MAX_DRUNK = 16;
	/** One point wears off this often. */
	public static final int DECAY_TICKS = 600;
	/** Points from strong spirits. */
	public static final int SPIRITS_DRUNK = 4;
	/** Strength I from strong spirits ("Dutch courage"). */
	public static final int SPIRIT_COURAGE_TICKS = 600;
	/** Slowness from here, and Nausea, Mining Fatigue and staggering from the next. */
	public static final int DRUNK_FROM = 4, VERY_DRUNK_FROM = 8;
	/** Sobering up from a peak of at least this many points leaves a hangover of this length. */
	public static final int HANGOVER_FROM = 6, HANGOVER_TICKS = 2400;
	/** How long each effect lasts when refreshed (every second), and nausea (so it fades in and out smoothly). */
	private static final int EFFECT_TICKS = 60, NAUSEA_TICKS = 140;
	/** A very drunk player staggers about once in this many seconds, shoved this hard. */
	private static final int STAGGER_SECONDS = 4;
	private static final double STAGGER_PUSH = 0.28;

	/** Drunkenness points (synced to you, saved, lost on death). Absent means sober. */
	public static final AttachmentType<Integer> POINTS = AttachmentRegistry.create(JaymosKitchen.id("drunk"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT.cast(), AttachmentSyncPredicate.targetOnly()));
	/** The most points reached since last sober (server side only), for the hangover. */
	private static final AttachmentType<Integer> PEAK = AttachmentRegistry.create(JaymosKitchen.id("drunk_peak"),
		builder -> builder.persistent(Codec.INT));

	/** Game tick at which each drunk player loses their next point (server side only, cleared on disconnect). */
	private static final Map<UUID, Long> NEXT_DECAY = new HashMap<>();

	private Drunk() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Drunk::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> NEXT_DECAY.remove(handler.player.getUUID()));
	}

	/** How drunk a player is, 0 (sober) to {@link #MAX_DRUNK}. Works on the client too, for the player themselves. */
	public static int level(Player player) {
		return player.getAttachedOrElse(POINTS, 0);
	}

	/** Adds drunkenness points. */
	public static void drink(ServerPlayer player, int points) {
		if (points <= 0) {
			return;
		}
		int before = level(player);
		if (before <= 0) {
			NEXT_DECAY.put(player.getUUID(), player.level().getGameTime() + DECAY_TICKS);
		}
		int next = Math.min(MAX_DRUNK, before + points);
		player.setAttached(POINTS, next);
		if (next > player.getAttachedOrElse(PEAK, 0)) {
			player.setAttached(PEAK, next);
		}
	}

	/** Something drunk (the stack is still whole): the drunkenness it gives, and the spirits' courage. */
	public static void onDrink(ServerPlayer player, ItemStack stack) {
		int points = Brewing.drunkOf(stack);
		if (points <= 0) {
			return;
		}
		drink(player, points);
		if (stack.is(Brewing.STRONG_SPIRITS)) {
			player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, SPIRIT_COURAGE_TICKS, 0));
		}
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			long now = player.level().getGameTime();
			if (!player.isAlive() || now % 20L != 0L) {
				continue;
			}
			int points = level(player);
			if (points <= 0) {
				continue;
			}
			long nextDecay = NEXT_DECAY.computeIfAbsent(player.getUUID(), id -> now + DECAY_TICKS);
			if (now >= nextDecay) {
				points--;
				NEXT_DECAY.put(player.getUUID(), nextDecay + DECAY_TICKS);
				if (points <= 0) {
					sober(player);
					continue;
				}
				player.setAttached(POINTS, points);
			}
			effects(player, points);
		}
	}

	/** Back to sober: a hangover if they got drunk enough. */
	private static void sober(ServerPlayer player) {
		int peak = player.getAttachedOrElse(PEAK, 0);
		player.removeAttached(POINTS);
		player.removeAttached(PEAK);
		NEXT_DECAY.remove(player.getUUID());
		if (peak >= HANGOVER_FROM) {
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, HANGOVER_TICKS, 0, false, false, true));
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, HANGOVER_TICKS, 0, false, false, true));
		}
	}

	private static void effects(ServerPlayer player, int points) {
		if (points >= DRUNK_FROM) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, EFFECT_TICKS, 0, false, false, false));
		}
		if (points >= VERY_DRUNK_FROM) {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NAUSEA_TICKS, 0, false, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, EFFECT_TICKS, 0, false, false, false));
			if (player.level().getRandom().nextInt(STAGGER_SECONDS) == 0) {
				double angle = player.level().getRandom().nextFloat() * 2.0 * Math.PI;
				Vec3 motion = player.getDeltaMovement();
				player.setDeltaMovement(motion.x + Math.cos(angle) * STAGGER_PUSH, motion.y, motion.z + Math.sin(angle) * STAGGER_PUSH);
				player.syncVelocity = true;
			}
		}
	}
}
