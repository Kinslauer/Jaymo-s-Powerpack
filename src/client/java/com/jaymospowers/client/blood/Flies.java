package com.jaymospowers.client.blood;

import com.jaymospowers.core.blood.Bloodied;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * Andy, on Cruor's splash-back: rotten blood "stinks the worst". Flies find it: tiny black specks darting about whoever
 * wears rotten blood (their coat or their gear, {@link Bloodied#reek}), and about a corpse whose blood has rotted. Seen by
 * everyone near, from the synced blood alone.
 */
public final class Flies {
	/** How often bodies are looked over (ticks), and how far from the camera (blocks). */
	static final int EVERY = 4;
	static final double RANGE = 20.0;
	/** Rotten blood this strong ({@link Bloodied#reek}, 0 to 1) draws flies; at its worst, this many specks each look. */
	static final float DRAWS = 0.2F;
	static final int MOST = 3;
	/** A fly: its colour and size, how far from the body it buzzes (blocks) and how fast it darts. */
	static final int COLOUR = 0x0E0E0C;
	static final float SIZE = 0.32F;
	static final double ROAM = 0.55, DART = 0.06;

	private static final DustParticleOptions FLY = new DustParticleOptions(COLOUR, SIZE);

	private Flies() {
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(Flies::tick);
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || mc.isPaused() || level.getGameTime() % EVERY != 0 || mc.getCameraEntity() == null) {
			return;
		}
		long now = level.getGameTime();
		RandomSource random = level.getRandom();
		AABB around = mc.getCameraEntity().getBoundingBox().inflate(RANGE);
		for (Entity e : level.getEntities((Entity) null, around, e -> e instanceof LivingEntity || e.hasAttached(Bloodied.CORPSE_BLOOD))) {
			float reek;
			if (e instanceof LivingEntity body) {
				reek = body.isAlive() && !body.isInvisible() ? rotten(body) : 0.0F;
			} else {
				Bloodied.Blood blood = Bloodied.corpseBlood(e);
				reek = blood == null || Bloodied.stage(blood.laid(), now) != Bloodied.ROTTEN ? 0.0F : blood.level();
			}
			if (reek < DRAWS) {
				continue;
			}
			int specks = Math.max(1, Math.round(reek * MOST));
			for (int i = 0; i < specks; i++) {
				if (random.nextFloat() > 0.6F) {
					continue;
				}
				AABB box = e.getBoundingBox();
				double x = box.getCenter().x + (random.nextDouble() - 0.5) * (box.getXsize() + ROAM);
				double y = box.minY + random.nextDouble() * (box.getYsize() + 0.3);
				double z = box.getCenter().z + (random.nextDouble() - 0.5) * (box.getZsize() + ROAM);
				level.addParticle(FLY, x, y, z, (random.nextDouble() - 0.5) * DART, (random.nextDouble() - 0.4) * DART,
					(random.nextDouble() - 0.5) * DART);
			}
		}
	}

	/** How strongly {@code body}'s rotten blood draws flies (0: none of it is rotten). */
	private static float rotten(LivingEntity body) {
		long now = body.level().getGameTime();
		float worst = 0.0F;
		Bloodied.Coat coat = Bloodied.coat(body);
		if (coat != null && coat.laid() > 0 && Bloodied.stage(coat.laid(), now) == Bloodied.ROTTEN) {
			worst = coat.level();
		}
		for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
			Bloodied.Blood blood = Bloodied.blood(body.getItemBySlot(slot));
			if (blood != null && Bloodied.stage(blood.laid(), now) == Bloodied.ROTTEN) {
				worst = Math.max(worst, blood.level());
			}
		}
		return worst;
	}
}
