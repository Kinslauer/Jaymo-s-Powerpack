package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Andy (2026-11-13): "giants like the ape need the legs and tail targetable, as they should all go crippled if both legs
 * go black in a fight". A giant ({@link #GIANTS}: ogres, the Bogre, trolls, the minotaur, the Piglin King, the Made Brute,
 * the Aether's Giants, the warden) whose legs are all at zero (black on the body silhouette) can't walk or jump at all
 * until they mend ({@link Breaks}, where any other mob only drags itself at a crawl), and goes down with a thud. The
 * Great Ape is a giant too: it registers its own crippling on {@link #LEGS_GONE}.
 */
public final class Giants {
	public static final TagKey<EntityType<?>> GIANTS = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("giants"));
	/** A giant's legs all at zero: its walk and jump are cut by this share (all of it). */
	public static final double CRIPPLED_SPEED = -1.0, CRIPPLED_JUMP = -1.0;
	/**
	 * Called when a hit takes the last of a creature's legs to zero (each leg part of its {@link BodyPlan}): anyone's
	 * crippling (the Great Ape's lives in the Z Fighter's lineage).
	 */
	public static final List<Consumer<LivingEntity>> LEGS_GONE = new ArrayList<>();

	private Giants() {
	}

	/** Whether {@code entity} is a tagged giant. */
	public static boolean giant(LivingEntity entity) {
		return entity.getType().builtInRegistryHolder().is(GIANTS);
	}

	/** After a hit: if it took the last leg to zero, it's crippled. */
	static void afterHit(LivingEntity victim, BodyPlan plan, float[] was, float[] now) {
		boolean any = false, wasStanding = false;
		for (int i = 0; i < plan.size(); i++) {
			BodyPart part = plan.part(i);
			if (part != BodyPart.LEFT_LEG && part != BodyPart.RIGHT_LEG && part != BodyPart.FRONT_LEGS && part != BodyPart.HIND_LEGS
				&& part != BodyPart.LEGS) {
				continue;
			}
			any = true;
			if (now[i] > Wounds.EPS) {
				return;
			}
			wasStanding |= was[i] > Wounds.EPS;
		}
		if (!any || !wasStanding || !victim.isAlive()) {
			return;
		}
		if (giant(victim) && victim.level() instanceof ServerLevel level) {
			level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.RAVAGER_STUNNED, SoundSource.HOSTILE, 3.0F, 0.5F);
		}
		for (Consumer<LivingEntity> listener : LEGS_GONE) {
			listener.accept(victim);
		}
	}
}
