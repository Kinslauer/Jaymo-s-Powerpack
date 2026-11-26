package com.jaymoskitchen.corpse;

import com.jaymoskitchen.JaymosKitchen;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * Bodies that don't lie where they fell: an enderman leaves no corpse to carry or butcher, but fades out where it died, a
 * part at a time (legs, arms, head, then the rest), the parts that have gone drawn missing by every client
 * ({@link Wear#hidden}) and a puff of portal dust where each one was.
 */
final class Passing {
	/** Creatures that fade away where they die (data/jaymoskitchen/tags/entity_type/fades_out.json). */
	static final TagKey<EntityType<?>> FADES_OUT = TagKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("fades_out"));
	/** Ticks between one part going and the next. */
	static final int STEP_TICKS = 8;
	/** Where on the body (as a share of its height) each step's dust rises, by step. */
	private static final float[] DUST_HEIGHT = {0.1F, 0.1F, 0.55F, 0.55F, 0.9F, 0.5F};

	private Passing() {
	}

	/** One tick of a fading body: another part goes every {@link #STEP_TICKS}; after the last, the body is gone. */
	static void fade(CorpseEntity corpse, ServerLevel level) {
		if (corpse.tickCount % STEP_TICKS != 0) {
			return;
		}
		int wear = corpse.wear();
		int step = Wear.get(wear, Wear.FADE);
		float height = Math.max(0.5F, corpse.bodyHeight());
		level.sendParticles(ParticleTypes.PORTAL, corpse.getX(), corpse.getY() + height * DUST_HEIGHT[Math.min(step, DUST_HEIGHT.length - 1)],
			corpse.getZ(), 14, 0.3, 0.2, 0.3, 0.3);
		level.playSound(null, corpse.getX(), corpse.getY(), corpse.getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 0.3F, 1.3F + step * 0.1F);
		if (step >= Wear.FADE_STEPS - 1) {
			corpse.discard();
			return;
		}
		corpse.setWear(Wear.with(wear, Wear.FADE, step + 1));
	}
}
