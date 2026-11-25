package com.jaymoskingdoms.glowwood.creatures;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Spore clouds: lingering area-effect clouds of spore motes. Mycosis inside them is shrugged off by the immune. */
public final class SporeClouds {

	private SporeClouds() {
	}

	public static AreaEffectCloud spawn(ServerLevel level, Vec3 pos, float radius, int durationTicks,
										@Nullable LivingEntity owner, MobEffectInstance... effects) {
		AreaEffectCloud cloud = new AreaEffectCloud(level, pos.x, pos.y, pos.z);
		if (owner != null) {
			cloud.setOwner(owner);
		}
		cloud.setCustomParticle(GlowFx.spore());
		cloud.setRadius(radius);
		cloud.setRadiusOnUse(-0.05F);
		cloud.setWaitTime(4);
		cloud.setDuration(durationTicks);
		cloud.setRadiusPerTick(-radius * 0.4F / durationTicks);
		for (MobEffectInstance effect : effects) {
			if (effect != null) {
				cloud.addEffect(effect);
			}
		}
		level.addFreshEntity(cloud);
		// A visible burst on top of the lingering cloud.
		level.sendParticles(GlowFx.spore(), pos.x, pos.y + 0.4, pos.z, (int) (radius * 18), radius * 0.45,
				0.4, radius * 0.45, 0.04);
		return cloud;
	}
}
