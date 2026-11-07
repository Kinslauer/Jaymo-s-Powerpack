package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.web.KitchenCompat;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Wounds, phase 4 (0.14.4): pain and what dulls it. While {@link #numb}, the slowness and weakness of broken limbs
 * ({@link Breaks}) are suspended (the bones are no less broken).
 * <ul>
 * <li><b>Willow-bark tea:</b> after {@link #TEA_DELAY}, numb for {@link #TEA_TICKS}.</li>
 * <li><b>Leeches:</b> numb at once, for {@link #LEECH_TICKS} (they cost a little health, {@link Treatment}).</li>
 * <li><b>Strong spirits</b> (Kitchen): numb while at least {@link #DRUNK_NUMB} drunk.</li>
 * <li><b>Poppy syrup (dwale):</b> numb for {@link #POPPY_TICKS}, a warm haze on the screen; a downed one comes round to
 * crawling ({@link Downed#wake}). A second dose within {@link #OVERDOSE_TICKS} knocks the taker out for
 * {@link #KNOCKOUT_TICKS} ({@link Downed#knockOut}). Each dose adds {@link #POPPY_ADDICTION} to a hidden
 * {@link #ADDICTION} that wears off {@link #ADDICTION_DECAY} a second; above {@link #CRAVING_FROM}, going
 * {@link #WITHDRAWAL_TICKS} without a dose brings withdrawal (shakes, Nausea now and then, Weakness, the craving) until a
 * dose, or until it has worn off below {@link #CRAVING_FROM}.</li>
 * </ul>
 */
public final class Pain {
	/** (0.14.4) Willow-bark tea: how long before it works, and how long it lasts (ticks). */
	public static final int TEA_DELAY = 200, TEA_TICKS = 3600;
	/** (0.14.4) Leeches: how long the pain stays dull (ticks). */
	public static final int LEECH_TICKS = 4800;
	/** (0.14.4) Poppy syrup: how long it lasts, how soon a second dose is too much, and how long that knocks you out (ticks). */
	public static final int POPPY_TICKS = 7200, OVERDOSE_TICKS = 1200, KNOCKOUT_TICKS = 1200;
	/** (0.14.4) Drunk at least this much (Kitchen's points), the pain is dulled. */
	public static final int DRUNK_NUMB = 4;
	/** (0.14.4) Addiction: added per dose, the most, worn off per second, and where craving begins. */
	public static final float POPPY_ADDICTION = 20.0F, MAX_ADDICTION = 100.0F, ADDICTION_DECAY = 0.02F, CRAVING_FROM = 40.0F;
	/** (0.14.4) An addict this long without a dose is in withdrawal (ticks). */
	public static final int WITHDRAWAL_TICKS = 6000;
	/** (0.14.4) In withdrawal: a wave of Nausea this often (seconds) for this long (ticks), and the craving's reminder (seconds). */
	public static final int NAUSEA_EVERY = 30, NAUSEA_TICKS = 140, CRAVING_EVERY = 15;

	/** (0.14.4) When the pain starts being dulled (game time). Saved, server side. */
	public static final AttachmentType<Long> NUMB_FROM = AttachmentRegistry.create(JaymosPowers.id("wounds_numb_from"),
		builder -> builder.persistent(Codec.LONG));
	/** (0.14.4) When it stops. Saved, server side. */
	public static final AttachmentType<Long> NUMB_UNTIL = AttachmentRegistry.create(JaymosPowers.id("wounds_numb_until"),
		builder -> builder.persistent(Codec.LONG));
	/** (0.14.4) Under poppy until (game time): the haze. Saved, synced to its owner. */
	public static final AttachmentType<Long> POPPY_UNTIL = AttachmentRegistry.create(JaymosPowers.id("wounds_poppy_until"),
		builder -> builder.persistent(Codec.LONG).syncWith(ByteBufCodecs.VAR_LONG.cast(), AttachmentSyncPredicate.targetOnly()));
	/** (0.14.4) The last dose of poppy (game time). Saved, kept through death. */
	public static final AttachmentType<Long> LAST_POPPY = AttachmentRegistry.create(JaymosPowers.id("wounds_last_poppy"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());
	/** (0.14.4) The hidden addiction (0 to {@link #MAX_ADDICTION}). Saved, kept through death, never synced. */
	public static final AttachmentType<Float> ADDICTION = AttachmentRegistry.create(JaymosPowers.id("wounds_addiction"),
		builder -> builder.persistent(Codec.FLOAT).copyOnDeath());
	/** (0.14.4) In withdrawal (the shakes on the client). Synced to its owner, not saved (worked out again each second). */
	public static final AttachmentType<Boolean> WITHDRAWAL = AttachmentRegistry.create(JaymosPowers.id("wounds_withdrawal"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));
	/** (0.14.4) Smelling salts just went under the nose (game time): the white flash. Synced to its owner, not saved. */
	public static final AttachmentType<Long> JOLT = AttachmentRegistry.create(JaymosPowers.id("wounds_jolt"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG.cast(), AttachmentSyncPredicate.targetOnly()));
	/** (0.14.5) The pain is dulled ({@link #numb}; no pain blur on the client). Synced to its owner, not saved (worked out again each second). */
	public static final AttachmentType<Boolean> DULLED = AttachmentRegistry.create(JaymosPowers.id("wounds_dulled"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	private Pain() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Pain::tick);
	}

	// ---- queries ----------------------------------------------------------------------------------------------------------

	/** (0.14.4) Whether {@code entity}'s pain is dulled now (server side): tea, poppy, leeches or drink. */
	public static boolean numb(LivingEntity entity) {
		long now = entity.level().getGameTime();
		Long from = entity.getAttached(NUMB_FROM);
		Long until = entity.getAttached(NUMB_UNTIL);
		if (from != null && until != null && now >= from && now < until) {
			return true;
		}
		return entity instanceof Player player && KitchenCompat.drunk(player) >= DRUNK_NUMB;
	}

	/** (0.14.5) Whether the pain is dulled, as last synced to its owner (client side; server side use {@link #numb}). */
	public static boolean dulled(Entity entity) {
		return entity.hasAttached(DULLED);
	}

	/** (0.14.4) Under poppy (both sides, for its owner: the haze). */
	public static boolean poppied(Entity entity) {
		Long until = entity.getAttached(POPPY_UNTIL);
		return until != null && entity.level().getGameTime() < until;
	}

	// ---- what dulls it ----------------------------------------------------------------------------------------------------

	/** Numb from {@code from} to {@code until} (game times), joined to any numbness still to come or going. */
	private static void dull(LivingEntity entity, long from, long until) {
		long now = entity.level().getGameTime();
		Long oldFrom = entity.getAttached(NUMB_FROM);
		Long oldUntil = entity.getAttached(NUMB_UNTIL);
		if (oldFrom != null && oldUntil != null && oldUntil > now) {
			from = Math.min(from, oldFrom);
			until = Math.max(until, oldUntil);
		}
		entity.setAttached(NUMB_FROM, from);
		entity.setAttached(NUMB_UNTIL, until);
	}

	/** (0.14.4) A cup of willow-bark tea: the pain fades after a while. */
	static void tea(LivingEntity entity) {
		tea(entity, 1.0F);
	}

	/** (0.19.24) A cup of tea that lasts {@code share} of willow-bark's (chamomile's is weaker). */
	static void tea(LivingEntity entity, float share) {
		long now = entity.level().getGameTime();
		dull(entity, now + TEA_DELAY, now + TEA_DELAY + Math.round(TEA_TICKS * share));
		if (entity instanceof Player player) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.tea").withStyle(ChatFormatting.GRAY));
		}
	}

	/** (0.19.24) A little ease from rest or a warm soak: the pain dulled at once, for {@code ticks} more (joined to any numbness already running). */
	public static void ease(LivingEntity entity, int ticks) {
		long now = entity.level().getGameTime();
		dull(entity, now, now + ticks);
	}

	/** (0.14.4) Leeches: the pain dulled at once, and for longer. */
	static void leeches(LivingEntity entity) {
		long now = entity.level().getGameTime();
		dull(entity, now, now + LEECH_TICKS);
	}

	/** (0.14.4) A dose of poppy syrup: see the class comment. */
	static void poppy(ServerLevel level, LivingEntity entity) {
		long now = level.getGameTime();
		Long last = entity.getAttached(LAST_POPPY);
		boolean overdose = last != null && now - last < OVERDOSE_TICKS;
		entity.setAttached(LAST_POPPY, now);
		dull(entity, now, now + POPPY_TICKS);
		entity.setAttached(POPPY_UNTIL, now + POPPY_TICKS);
		if (entity instanceof ServerPlayer player) {
			float addiction = Math.min(MAX_ADDICTION, entity.getAttachedOrElse(ADDICTION, 0.0F) + POPPY_ADDICTION);
			entity.setAttached(ADDICTION, addiction);
			if (entity.hasAttached(WITHDRAWAL)) {
				entity.removeAttached(WITHDRAWAL);
			}
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.poppy").withStyle(ChatFormatting.GOLD));
		}
		if (overdose && Downed.knockOut(level, entity, KNOCKOUT_TICKS)) {
			return;
		}
		if (Downed.isDowned(entity) && !entity.hasAttached(Downed.KNOCKED)) {
			// (dwale: the downed come round, enough to crawl)
			Downed.wake(entity, false);
		}
	}

	/** (0.14.4) Smelling salts under the nose: the owner's screen flashes white. */
	static void jolt(LivingEntity entity) {
		entity.setAttached(JOLT, entity.level().getGameTime());
	}

	// ---- addiction --------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			// (0.14.5) whether the pain is dulled, for the owner's screen
			boolean dulled = player.isAlive() && numb(player);
			if (dulled && !player.hasAttached(DULLED)) {
				player.setAttached(DULLED, true);
			} else if (!dulled && player.hasAttached(DULLED)) {
				player.removeAttached(DULLED);
			}
			Float addiction = player.getAttached(ADDICTION);
			if (addiction == null) {
				if (player.hasAttached(WITHDRAWAL)) {
					player.removeAttached(WITHDRAWAL);
				}
				continue;
			}
			// it always wears off, a little each second (alive or not, in withdrawal or not)
			float left = addiction - ADDICTION_DECAY;
			if (left <= 0.0F) {
				player.removeAttached(ADDICTION);
				if (player.hasAttached(WITHDRAWAL)) {
					player.removeAttached(WITHDRAWAL);
				}
				continue;
			}
			player.setAttached(ADDICTION, left);
			if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
				// (no shakes out of survival)
				if (player.hasAttached(WITHDRAWAL)) {
					player.removeAttached(WITHDRAWAL);
				}
				continue;
			}
			long now = player.level().getGameTime();
			Long last = player.getAttached(LAST_POPPY);
			boolean withdrawing = left >= CRAVING_FROM && (last == null || now - last >= WITHDRAWAL_TICKS);
			if (!withdrawing) {
				if (player.hasAttached(WITHDRAWAL)) {
					player.removeAttached(WITHDRAWAL);
				}
				continue;
			}
			if (!player.hasAttached(WITHDRAWAL)) {
				player.setAttached(WITHDRAWAL, true);
			}
			long seconds = now / 20L;
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0, false, false, true));
			if (seconds % NAUSEA_EVERY == 0) {
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NAUSEA_TICKS, 0, false, false, true));
			}
			if (seconds % CRAVING_EVERY == 0) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.craving").withStyle(ChatFormatting.DARK_PURPLE));
			}
		}
	}
}
