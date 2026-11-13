package com.jaymospowers.client.blood;

import com.jaymospowers.core.blood.Bloodied;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Fresh blood drips off a weapon (a pick from Cruor's ideas): a blade or club bloodied in the last minute
 * ({@link #WET_TICKS}) and bloody enough ({@link #LEAST}) lets a drop fall now and then from about where it's held, more
 * often the bloodier it is, leaving a little splash. Drawn on every client for every body near, from the synced blood on
 * the item; a first-person player's own drips are hidden by {@link BloodDrop}, so they fall past the screen unseen. A
 * bloody weapon dropped on the ground drips where it lies while it's still wet.
 */
public final class WeaponDrips {
	/** How long blood on a weapon stays wet enough to drip (ticks), and the least of it that drips (0 to 1). */
	static final int WET_TICKS = 1200;
	static final float LEAST = 0.2F;
	/** The chance a tick at the bloodiest, falling off as the blood dries; and how far from the camera (blocks). */
	static final float CHANCE = 0.08F;
	static final double RANGE = 24.0;
	/** Where it falls from: out to the side of the body (blocks), forward, and down from the shoulders (share of height). */
	static final double SIDE = 0.4, FORWARD = 0.25, HAND_HEIGHT = 0.42;

	private WeaponDrips() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(WeaponDrips::tick);
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.isPaused() || mc.getCameraEntity() == null) {
			return;
		}
		long now = level.getGameTime();
		RandomSource random = level.getRandom();
		float share = BloodSettings.amount().drops;
		AABB around = mc.getCameraEntity().getBoundingBox().inflate(RANGE);
		for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, around, b -> b.isAlive() && !b.isInvisible())) {
			drip(level, body, body.getMainHandItem(), body.getMainArm(), now, random, share);
			drip(level, body, body.getOffhandItem(), body.getMainArm().getOpposite(), now, random, share);
		}
		// a bloody weapon lying on the ground (or tossed) drips where it lies, the same while it's wet
		for (ItemEntity dropped : level.getEntitiesOfClass(ItemEntity.class, around, d -> d.isAlive() && !d.isInWater())) {
			float chance = chance(Bloodied.blood(dropped.getItem()), now, share);
			if (chance > 0.0F && random.nextFloat() < chance) {
				Bloodied.Blood blood = Bloodied.blood(dropped.getItem());
				BloodClient.drip(level, dropped.getX() + (random.nextDouble() - 0.5) * 0.3, dropped.getY() + 0.1,
					dropped.getZ() + (random.nextDouble() - 0.5) * 0.3, blood.color(), 0.5F);
			}
		}
	}

	/** The chance a tick this blood drips: none once it's dry or too little, more the bloodier and fresher. */
	private static float chance(Bloodied.@org.jspecify.annotations.Nullable Blood blood, long now, float share) {
		if (blood == null || blood.level() < LEAST || now - blood.laid() > WET_TICKS || now < blood.laid()) {
			return 0.0F;
		}
		float wet = 1.0F - (now - blood.laid()) / (float) WET_TICKS;
		return CHANCE * blood.level() * wet * share;
	}

	private static void drip(ClientLevel level, LivingEntity body, ItemStack stack, HumanoidArm arm, long now, RandomSource random, float share) {
		Bloodied.Blood blood = Bloodied.blood(stack);
		float chance = chance(blood, now, share);
		if (chance <= 0.0F || random.nextFloat() >= chance) {
			return;
		}
		float yaw = body.yBodyRot * Mth.DEG_TO_RAD;
		double side = arm == HumanoidArm.RIGHT ? -SIDE : SIDE;
		// the body's right is (-cos, -sin) of its yaw, forward (-sin, cos)
		double x = body.getX() + Mth.cos(yaw) * side - Mth.sin(yaw) * FORWARD;
		double z = body.getZ() + Mth.sin(yaw) * side + Mth.cos(yaw) * FORWARD;
		double y = body.getY() + body.getBbHeight() * HAND_HEIGHT;
		BloodClient.drip(level, x, y, z, blood.color(), 0.5F);
	}
}
