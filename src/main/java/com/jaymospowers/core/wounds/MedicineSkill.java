package com.jaymospowers.core.wounds;

import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.15.49) The Medicine page of Jaymo's Kingdoms' Survival tree, worked into our treating ({@link Treatment}) and our
 * revives ({@link Downed}). Andy (2026-10-21): "a medicine tree in the same vein as shadow and free running but for
 * survival". Kingdoms holds the ranks and we read them by reflection on {@code com.jaymoskingdoms.Skills.rank(Player,
 * String)}, as {@code Meditate} and {@code Freerun} do; without Kingdoms every rank is 0 and nothing changes. All
 * medieval, like the rest of Wounds.
 * <ul>
 * <li><b>Steady Hands</b> (3 ranks): every treatment and every revive takes {@link #STEADY_HANDS_PER_RANK} less time per rank.</li>
 * <li><b>Field Dresser</b> (2 ranks): bandages and rags you tie heal {@link #FIELD_DRESSER_PER_RANK} more per rank.</li>
 * <li><b>Bonesetter</b>: a splint you put on mends {@link #BONESETTER_HEAL} into the limb at once; a stitch or a searing
 * you give hurts {@link #BONESETTER_HURT} as much.</li>
 * <li><b>Plague Doctor</b>: Kingdoms' own (the Nightfall plague's Atrophy goes easier on you); and here, leeches you
 * put on draw out the bad blood: the plague ({@link #ATROPHY}) and poison. Andy: "you can also use leeches for benefits,
 * when noone else can".</li>
 * <li><b>Second Wind</b>: eat while downed to rise, once a day ({@link SelfRevive}).</li>
 * <li><b>Lifesaver</b>: someone you bring round gets up with {@link #LIFESAVER_HEALTH} of their health and their bleeding
 * stopped; your smelling salts always rouse a player who has passed out.</li>
 * </ul>
 * Each treatment finished tags the healer {@link #XP_TAG}; Kingdoms turns it into a little Survival experience.
 */
public final class MedicineSkill {
	/** Steady Hands: per rank, this share off how long a treatment or a revive takes. */
	public static final float STEADY_HANDS_PER_RANK = 0.15F;
	/** Field Dresser: per rank, this share more healing from a bandage or a rag. */
	public static final float FIELD_DRESSER_PER_RANK = 0.35F;
	/** Bonesetter: what a splint mends into the limb at once (health points); the share of a stitch's or a searing's hurt left. */
	public static final float BONESETTER_HEAL = 3.0F, BONESETTER_HURT = 0.5F;
	/** Lifesaver: the share of their most health someone you bring round gets up with. */
	public static final float LIFESAVER_HEALTH = 0.5F;
	/** Plague Doctor's leeches draw out Kingdoms' Nightfall plague (by id: we never compile against Kingdoms). */
	static final Identifier ATROPHY = Identifier.fromNamespaceAndPath("jaymoskingdoms", "atrophy");
	/** The scoreboard tag on a player who just finished a treatment (Kingdoms' {@code Skills.TREATED_TAG}). */
	public static final String XP_TAG = "jaymospowers_treated";

	private static final @Nullable Method KINGDOMS_RANK;

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

	private MedicineSkill() {
	}

	/** {@code healer}'s rank in the Kingdoms perk {@code key} (0 for anything but a player, or without Kingdoms). */
	static int rank(@Nullable LivingEntity healer, String key) {
		if (KINGDOMS_RANK == null || !(healer instanceof Player player)) {
			return 0;
		}
		try {
			return (int) KINGDOMS_RANK.invoke(null, player, key);
		} catch (ReflectiveOperationException | RuntimeException e) {
			return 0;
		}
	}

	/** How long {@code healer} takes over what takes {@code ticks} (Steady Hands), at least a tick. */
	static int ticks(@Nullable LivingEntity healer, int ticks) {
		return Math.max(1, Math.round(ticks * (1.0F - STEADY_HANDS_PER_RANK * rank(healer, "steady_hands"))));
	}

	/** A bandage's or a rag's {@code heal}, tied by {@code healer} (Field Dresser). */
	static float dressing(@Nullable LivingEntity healer, float heal) {
		return heal * (1.0F + FIELD_DRESSER_PER_RANK * rank(healer, "field_dresser"));
	}

	static boolean bonesetter(@Nullable LivingEntity healer) {
		return rank(healer, "bonesetter") > 0;
	}

	/** A stitch's or a searing's {@code hurt}, given by {@code healer} (Bonesetter: half). */
	static float hurt(@Nullable LivingEntity healer, float hurt) {
		return bonesetter(healer) ? hurt * BONESETTER_HURT : hurt;
	}

	static boolean plagueDoctor(@Nullable LivingEntity healer) {
		return rank(healer, "plague_doctor") > 0;
	}

	/** Plague Doctor: {@code healer}'s leeches on {@code body} draw out the plague and poison. True if they drew anything. */
	static boolean bleedBadBlood(@Nullable LivingEntity healer, LivingEntity body) {
		if (!plagueDoctor(healer)) {
			return false;
		}
		boolean drew = body.removeEffect(MobEffects.POISON);
		var plague = BuiltInRegistries.MOB_EFFECT.get(ATROPHY);
		if (plague.isPresent()) {
			drew |= body.removeEffect(plague.get());
		}
		return drew;
	}

	static boolean lifesaver(@Nullable LivingEntity healer) {
		return rank(healer, "lifesaver") > 0;
	}

	/** {@code body}, just brought round by {@code healer}: with Lifesaver, up with half their health and their bleeding stopped. */
	static void broughtRound(@Nullable LivingEntity healer, LivingEntity body) {
		if (!lifesaver(healer) || !body.isAlive() || Downed.isDowned(body)) {
			return;
		}
		BodyPlan plan = BodyPlan.of(body);
		if (plan != null) {
			for (int i = 0; i < plan.size(); i++) {
				Bleeding.stop(body, plan.part(i), false);
			}
		}
		body.setHealth(Math.max(body.getHealth(), body.getMaxHealth() * LIFESAVER_HEALTH));
		Wounds.reconcile(body);
	}

	/** {@code healer} finished a treatment: Kingdoms reads the tag for the experience. */
	static void treated(Player healer) {
		healer.addTag(XP_TAG);
	}
}
