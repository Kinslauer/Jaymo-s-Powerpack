package com.jaymospowers.fighting;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.frost.Freezing;
import com.jaymospowers.power.arsenal.ArsenalEmber;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.Items;

/**
 * Snowballs sting (Clouds and Bits patch 6): a snowball does {@link #DAMAGE} and leaves a short chill (the Freezing effect,
 * {@link #CHILL_TICKS}); against the creatures of the Nether (the {@link #NETHER} tag; a blaze already took three from the
 * game) and Emberborn it does {@link #NETHER_DAMAGE}, and they are not chilled. A snowball that is really a thrown rock (a
 * cobblestone or one of ours) is left to the rocks' own rules.
 */
public final class SnowballSting {
	// ---- tuning ----
	/** Damage to anything (half hearts), and to Nether creatures and Emberborn. */
	static final float DAMAGE = 1.0F, NETHER_DAMAGE = 3.0F;
	/** How long the chill lasts (ticks): the Freezing effect, which freezes its bearer a little faster than powder snow thaws. */
	static final int CHILL_TICKS = 60;

	/** Creatures born of the Nether, who take the snowball hard. */
	static final TagKey<EntityType<?>> NETHER = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("nether_creatures"));

	private SnowballSting() {
	}

	/** What a snowball does to what it struck (server side): its damage, with the chill laid on a living target. {@code vanilla} is what the game would have dealt. */
	public static float hit(Snowball ball, Entity target, float vanilla) {
		if (ball.getItem().getItem() != Items.SNOWBALL) {
			return vanilla;
		}
		boolean hot = hot(target);
		if (!hot && target instanceof LivingEntity living && !Freezing.immune(living)) {
			living.addEffect(new MobEffectInstance(Freezing.FREEZING, CHILL_TICKS, 0, false, true), ball.getOwner());
		}
		return Math.max(vanilla, hot ? NETHER_DAMAGE : DAMAGE);
	}

	/** Whether the cold bites this one harder: a creature of the Nether, or an Emberborn player. */
	private static boolean hot(Entity target) {
		return target.getType().builtInRegistryHolder().is(NETHER) || target instanceof Player player && ArsenalEmber.emberborn(player);
	}
}
