package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * Survival picks (Andy, 2026-11-22, after Legendary Survival Overhaul's ideas; none of its code): what a part's own pool
 * ({@link Wounds}, {@link BodyPlan}) does to you as it runs low.
 * <ul>
 * <li><b>The 20% line (lso-thresholds):</b> a part with {@link #LOW} of its pool or less left: one arm, Mining Fatigue;
 * both arms, Weakness too; a foot, Slowness; the body, every hit lands {@link #BODY_LOW_DAMAGE} times as hard; a leg,
 * falls land {@link #LEGS_LOW_FALL} times as hard; the head, a headache (our own throbbing haze, drawn by
 * {@code PainHud}). A painkiller (willow bark, poppy, leeches, drink: {@link Pain#numb}) or adrenaline holds them all
 * off. Broken limbs stay as they were (Andy: "broken limbs should still be unusable before a splint", {@link Breaks},
 * {@link LimbUse}).</li>
 * <li><b>Broken hearts (lso-brokenhearts):</b> every part at nothing (not a severed one: that's a stump, not a wound)
 * takes {@link #BROKEN_HEART} off the most health until it heals, at most {@link #BROKEN_HEARTS_MAX}. The pools are
 * measured without it ({@link #poolMax}).</li>
 * <li><b>Sleep (lso-food-cost):</b> a night's sleep (slept long enough to pass the night) heals every part as far as
 * it may heal ({@link Breaks#caps}), paying its food ({@link Wounds#FOOD_PER_POINT}).</li>
 * </ul>
 */
public final class PartStrain {
	/** A part this full or less is low: its debuff is on. */
	public static final float LOW = 0.2F;
	/** The 20% debuffs are put on for this long (ticks), and kept up every {@link Wounds#PLAYER_RECONCILE_TICKS}. */
	static final int EFFECT_TICKS = 40;
	/** A low body: every hit is this many times as hard. A low leg: every fall. */
	public static final float BODY_LOW_DAMAGE = 1.25F, LEGS_LOW_FALL = 1.5F;
	/** Each part at nothing takes this share off the most health, up to this much in all. */
	public static final float BROKEN_HEART = 0.1F, BROKEN_HEARTS_MAX = 0.5F;
	private static final Identifier BROKEN_HEARTS_ID = JaymosPowers.id("broken_hearts");

	/** A sleep the world's clock jumped at least this far over (ticks, a night skipped) heals every part. */
	public static final long NIGHT_TICKS = 2000L;
	/** The overworld clock when each sleeper lay down. */
	private static final Map<ServerPlayer, Long> LAY_DOWN = new WeakHashMap<>();

	private PartStrain() {
	}

	/** From {@link Wounds#init}: a night's sleep, seen as the sleeper wakes (vanilla wakes them a tick after the night is skipped). */
	static void register() {
		EntitySleepEvents.START_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player) {
				LAY_DOWN.put(player, player.level().getOverworldClockTime());
			}
		});
		EntitySleepEvents.STOP_SLEEPING.register((entity, pos) -> {
			if (entity instanceof ServerPlayer player) {
				Long from = LAY_DOWN.remove(player);
				if (from != null && player.isAlive() && player.level().getOverworldClockTime() - from >= NIGHT_TICKS
					&& Wounds.mendEvery(player, 1.0F) > Wounds.EPS) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.wounds.slept_whole").withStyle(ChatFormatting.GREEN));
				}
			}
		});
	}

	/** The most health without what broken hearts take off it: what every pool is a share of. */
	static float poolMax(LivingEntity entity) {
		AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
		AttributeModifier ours = health == null ? null : health.getModifier(BROKEN_HEARTS_ID);
		float max = entity.getMaxHealth();
		return ours == null ? max : max / (float) Math.max(0.1, 1.0 + ours.amount());
	}

	/** Puts the broken hearts in line with the parts at nothing (from {@link Wounds#store}). */
	static void brokenHearts(LivingEntity entity, BodyPlan plan) {
		AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
		if (health == null) {
			return;
		}
		int out = 0;
		java.util.List<Float> fractions = entity.getAttached(Wounds.PARTS);
		if (fractions != null && fractions.size() == plan.size()) {
			for (int i = 0; i < plan.size(); i++) {
				if (fractions.get(i) <= Wounds.EPS && (plan.part(i).vital() || !Breaks.missing(entity, plan.part(i)))) {
					out++;
				}
			}
		}
		double amount = -Math.min(BROKEN_HEARTS_MAX, out * BROKEN_HEART);
		AttributeModifier had = health.getModifier(BROKEN_HEARTS_ID);
		if (amount == 0.0) {
			if (had != null) {
				health.removeModifier(BROKEN_HEARTS_ID);
			}
		} else if (had == null || had.amount() != amount) {
			health.addOrUpdateTransientModifier(new AttributeModifier(BROKEN_HEARTS_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			if (entity.getHealth() > entity.getMaxHealth()) {
				entity.setHealth(entity.getMaxHealth());
			}
		}
	}

	/** Whether the 20% debuffs are held off on {@code entity} (a painkiller, adrenaline). */
	static boolean eased(LivingEntity entity) {
		return Pain.numb(entity) || Adrenaline.active(entity);
	}

	/** How much harder a hit by {@code source} lands on {@code victim}: a low body, and a fall on a low leg. */
	static float hitScale(LivingEntity victim, DamageSource source) {
		BodyPlan plan = BodyPlan.of(victim);
		if (plan == null || plan == BodyPlan.SIMPLE || victim.getAttached(Wounds.PARTS) == null || eased(victim)) {
			return 1.0F;
		}
		float scale = 1.0F;
		if (low(victim, BodyPart.BODY)) {
			scale *= BODY_LOW_DAMAGE;
		}
		if (source.is(DamageTypeTags.IS_FALL)) {
			for (BodyPart leg : plan.fallParts()) {
				BodyPart at = leg == BodyPart.LEFT_FOOT ? BodyPart.LEFT_LEG : leg == BodyPart.RIGHT_FOOT ? BodyPart.RIGHT_LEG : leg;
				if (low(victim, at)) {
					scale *= LEGS_LOW_FALL;
					break;
				}
			}
		}
		return scale;
	}

	/** Whether {@code part} is there, and at {@link #LOW} or less. */
	private static boolean low(LivingEntity entity, BodyPart part) {
		BodyPlan plan = BodyPlan.of(entity);
		return plan != null && plan.index(part) >= 0 && !(!part.vital() && Breaks.missing(entity, part)) && Wounds.fraction(entity, part) <= LOW;
	}

	/** Every {@link Wounds#PLAYER_RECONCILE_TICKS} for a living survival player: broken hearts and the 20% debuffs. */
	static void tick(ServerPlayer player) {
		BodyPlan plan = BodyPlan.of(player);
		if (plan == null || plan == BodyPlan.SIMPLE) {
			return;
		}
		brokenHearts(player, plan);
		if (player.getAttached(Wounds.PARTS) == null || eased(player) || Downed.isDowned(player)) {
			return;
		}
		int arms = (low(player, BodyPart.LEFT_ARM) ? 1 : 0) + (low(player, BodyPart.RIGHT_ARM) ? 1 : 0);
		if (arms > 0) {
			player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, EFFECT_TICKS, 0, false, false, true));
		}
		if (arms > 1) {
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, EFFECT_TICKS, 0, false, false, true));
		}
		if (low(player, BodyPart.LEFT_FOOT) || low(player, BodyPart.RIGHT_FOOT)) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, EFFECT_TICKS, 0, false, false, true));
		}
	}
}
