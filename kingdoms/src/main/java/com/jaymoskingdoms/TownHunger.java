package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * A town that runs out of food starves. Andy (2026-11-19): "when anyone goes hungry they cant just make the food come out
 * of nowhere, thats how kingdoms should starve too when you get that notifier" (the famine, {@link TownTroubles}).
 * Townsfolk eat twice a day from the storehouse ({@link Meals}); each meal they go to and find nothing to be had is
 * counted on them ({@link #MISSED}), and a meal eaten clears it.
 * <ul>
 * <li>A day without ({@link #WEAK_AFTER} meals): they're weak.</li>
 * <li>Two days ({@link #STARVING_AFTER}): slow as well, and every meal missed after that the hunger takes
 * {@link #STARVE_HURT} of their health, as it does a player's: on Easy never below half their health, on Normal never
 * below half a heart, and on Hard it kills them.</li>
 * </ul>
 * Fill the storehouse and they mend; leave it bare and the town dwindles, fewer hands to bring the harvest in.
 */
final class TownHunger {
	/** On a villager: meals in a row they went without. Saved. */
	static final AttachmentType<Integer> MISSED = AttachmentRegistry.create(JaymosKingdoms.id("missed_meals"),
		builder -> builder.persistent(Codec.INT));
	/** Weak after this many meals missed in a row (two a day); starving after this many. */
	static final int WEAK_AFTER = 2, STARVING_AFTER = 4;
	/** The hunger's bite on a starving villager each meal missed; its weakness lasts till the next mealtime and more (ticks). */
	static final float STARVE_HURT = 4.0F;
	static final int LASTS = 14000;
	static final double SPEECH_RANGE = 12.0;

	private TownHunger() {
	}

	/** (Loads the class at start-up, so its saved count is known before a world loads.) */
	static void register() {
	}

	/** {@code v} came for a meal and there was none. */
	static void wentWithout(ServerLevel level, Villager v) {
		int missed = v.getAttachedOrElse(MISSED, 0) + 1;
		v.setAttached(MISSED, missed);
		if (missed >= WEAK_AFTER) {
			v.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, LASTS, 0));
		}
		if (missed < STARVING_AFTER) {
			return;
		}
		v.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, LASTS, 0));
		Difficulty difficulty = level.getDifficulty();
		float floor = difficulty == Difficulty.HARD ? 0.0F : difficulty == Difficulty.NORMAL ? 1.0F : v.getMaxHealth() / 2.0F;
		float hurt = Math.min(STARVE_HURT, v.getHealth() - floor);
		if (difficulty != Difficulty.PEACEFUL && hurt > 0.0F) {
			v.hurtServer(level, level.damageSources().starve(), hurt);
		}
		if (v.isAlive()) {
			Speech.say(level, v, Component.translatable("spirits.jaymoskingdoms.starving." + level.getRandom().nextInt(Spirits.LINES)),
				Speech.WHITE, SPEECH_RANGE);
		}
	}

	/** {@code v} has eaten: the count is cleared, and the weakness of hunger goes. */
	static void ate(Villager v) {
		Integer missed = v.getAttached(MISSED);
		if (missed == null) {
			return;
		}
		v.removeAttached(MISSED);
		if (missed >= WEAK_AFTER) {
			v.removeEffect(MobEffects.WEAKNESS);
			v.removeEffect(MobEffects.SLOWNESS);
		}
	}
}
