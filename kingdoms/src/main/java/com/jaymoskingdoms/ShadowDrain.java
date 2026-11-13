package com.jaymoskingdoms;

import com.jaymoskingdoms.creatures.Shadow;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * What a shadow's touch takes: Might (fifth edition's strength drain). Each touch of a {@link Shadow} drains
 * {@link #PER_TOUCH} points from a player's Might ({@link Stats#value} reads it off), and the loss mends by itself, slowly,
 * a point every {@link #MENDS_EVERY} ticks. At nothing left to give (the drain has taken as much as Might has, at least
 * {@link #LEAST_POOL}) they go down, never to die of it: they are laid out where they stand for {@link #DOWN_TICKS}
 * ({@link PowersCompat#knockOut}), and their own shadow peels away from them and joins the others. Slaying that shadow
 * gives all of it back.
 */
public final class ShadowDrain {
	/** The Might a touch takes, how long a point takes to mend (ticks), and the least Might a body is reckoned to have to lose. */
	public static final int PER_TOUCH = 5, LEAST_POOL = 20;
	static final int MENDS_EVERY = 20 * 30;
	/** Laid out this long (ticks) when the drain is complete. */
	static final int DOWN_TICKS = 20 * 40;

	/** Points of Might drained from a player. Saved, kept through death. */
	public static final AttachmentType<Integer> DRAIN = AttachmentRegistry.create(JaymosKingdoms.id("might_drain"),
		b -> b.persistent(Codec.INT).copyOnDeath());

	private ShadowDrain() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ShadowDrain::tick);
	}

	/** Points of Might {@code who} has lost to shadows. */
	public static int drained(Player who) {
		return who.getAttachedOrElse(DRAIN, 0);
	}

	/** {@code raw} Might less the drain, at least nothing. */
	static int apply(Player who, int raw) {
		return Math.max(0, raw - drained(who));
	}

	/** The Might a body has before the drain, or the least pool. */
	static int pool(ServerPlayer who) {
		return Math.max(LEAST_POOL, Math.min(Stats.cap(who), Stats.total(who, Skills.of(who), Stats.Stat.MIGHT)));
	}

	/** A shadow touches {@code who}: drains Might; at the end of it, they go down and their own shadow peels away. */
	public static void touch(ServerLevel level, LivingEntity who, Shadow by) {
		if (!(who instanceof ServerPlayer player)) {
			who.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1), by);
			return;
		}
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		int pool = pool(player);
		int now = Math.min(pool, drained(player) + PER_TOUCH);
		player.setAttached(DRAIN, now);
		Stats.attributes(player);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 0.8F, 0.6F);
		level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY(0.6), player.getZ(), 10, 0.3, 0.4, 0.3, 0.02);
		if (now >= pool && !player.hasEffect(MobEffects.WEAKNESS)) {
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, DOWN_TICKS, 2));
			if (!PowersCompat.knockOut(level, player, DOWN_TICKS)) {
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, DOWN_TICKS, 4));
			}
			Shadow own = Shadow.peeledFrom(level, player, now);
			if (own != null) {
				level.addFreshEntity(own);
			}
		}
	}

	/** The shadow that was {@code owner}'s is slain: the Might comes back. */
	public static void returned(ServerLevel level, java.util.UUID owner, int points) {
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);
		if (player == null) {
			return;
		}
		player.setAttached(DRAIN, Math.max(0, drained(player) - points));
		Stats.attributes(player);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(0.6), player.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % MENDS_EVERY != 0) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			int d = drained(p);
			if (d > 0) {
				p.setAttached(DRAIN, d - 1);
				Stats.attributes(p);
			}
		}
	}
}
