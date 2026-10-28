package com.jaymospowers.tinkering.fletching;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * An arrow that bursts into a small lingering cloud of its potion where it lands. A server-side {@link Arrow} (it keeps the
 * vanilla arrow's entity type, so clients draw it as a tipped arrow in the potion's colour); a hit still applies the
 * potion's effect to the target in the vanilla weakened way, and the arrow is spent on impact either way.
 */
public class LingeringArrow extends Arrow {
	/** The cloud: radius (blocks), lifetime (ticks, 10 s), ticks before it takes hold, and the share of a lingering potion's effect duration. */
	static final float RADIUS = 1.75F;
	static final int DURATION = 200, WAIT = 10;
	static final float EFFECT_SCALE = 0.25F;
	/** How much radius the cloud loses each time something is touched by it. */
	static final float RADIUS_ON_USE = -0.3F;

	private final PotionContents contents;
	private boolean burst;

	public LingeringArrow(Level level, LivingEntity shooter, ItemStack ammo, ItemStack weapon) {
		super(level, shooter, ammo, weapon);
		this.contents = ammo.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
	}

	public LingeringArrow(Level level, double x, double y, double z, ItemStack ammo, ItemStack weapon) {
		super(level, x, y, z, ammo, weapon);
		this.contents = ammo.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		burst(hit.getLocation());
	}

	@Override
	protected void onHitBlock(BlockHitResult hit) {
		super.onHitBlock(hit);
		burst(hit.getLocation());
	}

	private void burst(Vec3 at) {
		if (burst || !(this.level() instanceof ServerLevel level)) {
			return;
		}
		burst = true;
		AreaEffectCloud cloud = new AreaEffectCloud(level, at.x, at.y, at.z);
		if (this.getOwner() instanceof LivingEntity owner) {
			cloud.setOwner(owner);
		}
		cloud.setPotionContents(contents);
		cloud.setRadius(RADIUS);
		cloud.setRadiusOnUse(RADIUS_ON_USE);
		cloud.setWaitTime(WAIT);
		cloud.setDuration(DURATION);
		cloud.setRadiusPerTick(-RADIUS / (float) DURATION);
		cloud.setPotionDurationScale(EFFECT_SCALE);
		level.addFreshEntity(cloud);
		// the shaft shatters: nothing to pick up
		this.discard();
	}
}
