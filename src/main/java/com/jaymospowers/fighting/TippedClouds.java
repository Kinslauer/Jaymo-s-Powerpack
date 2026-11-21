package com.jaymospowers.fighting;

import com.jaymospowers.tinkering.fletching.LingeringArrow;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.Vec3;

/**
 * Tipped arrows leave a cloud (Clouds and Bits patch 6): where a tipped arrow hits (a body or a block) it leaves a small
 * lingering cloud of its potion, {@link #RADIUS} blocks wide for {@link #DURATION} ticks (3 s). The arrow still gives the
 * target its own effect as ever. A lingering arrow (Fletching) already bursts into a bigger cloud and is left to that. One
 * cloud per arrow, even a piercing one.
 */
public final class TippedClouds {
	// ---- tuning ----
	/** The cloud: radius (blocks), lifetime (ticks, 3 s), ticks before it takes hold, and the share of a lingering potion's effect duration it gives. */
	static final float RADIUS = 1.5F;
	static final int DURATION = 60, WAIT = 5;
	static final float EFFECT_SCALE = 0.25F;

	/** Marks an arrow that has made its cloud. */
	private static final String SPENT_TAG = "jaymospowers_tipped_cloud";

	private TippedClouds() {
	}

	/** An arrow has struck something at {@code at} (server side). */
	public static void hit(AbstractArrow arrow, Vec3 at) {
		if (!(arrow instanceof Arrow) || arrow instanceof LingeringArrow || !(arrow.level() instanceof ServerLevel level) || arrow.entityTags().contains(SPENT_TAG)) {
			return;
		}
		ItemStack stack = arrow.getPickupItemStackOrigin();
		if (!stack.is(Items.TIPPED_ARROW)) {
			return;
		}
		PotionContents contents = stack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
		if (!contents.hasEffects()) {
			return;
		}
		arrow.addTag(SPENT_TAG);
		AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
		if (arrow.getOwner() instanceof LivingEntity owner) {
			cloud.setOwner(owner);
		}
		cloud.setPotionContents(contents);
		cloud.setRadius(RADIUS);
		cloud.setWaitTime(WAIT);
		cloud.setDuration(DURATION);
		cloud.setRadiusPerTick(-RADIUS / (float) DURATION);
		cloud.setPotionDurationScale(EFFECT_SCALE);
		level.addFreshEntity(cloud);
	}
}
