package com.jaymospowers.client.power.xenoform;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.xenoform.XenoformState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A Wisp feels the dead before it sees them: while anything undead, or another Wisp, is within {@link #SENSE} blocks (walls
 * or not), a thin cold breath leaves its mouth every {@link #EVERY} ticks. No sound, no text: you learn what it means.
 * Everyone sees the breath on every Wisp; nothing else is told.
 */
public final class WispBreath {
	// ---- tuning ----
	/** How near the dead have to be (blocks), and how often it breathes while they are (ticks). */
	static final double SENSE = 14.0;
	static final int EVERY = 50;
	/** A breath: this many motes over this many ticks, drifting out along the look. */
	static final int PUFF_TICKS = 6, PUFF_MOTES = 2;
	static final double PUFF_SPEED = 0.035;
	static final int COLOR = 0xA8DCFF;
	static final float SIZE = 0.6F;

	private WispBreath() {
	}

	/** Every client tick. */
	static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}
		for (AbstractClientPlayer player : level.players()) {
			if (!"wisp".equals(player.getAttached(XenoformState.FORM)) || player.isInvisible()) {
				continue;
			}
			// out of step from Wisp to Wisp
			int beat = (player.tickCount + player.getId() * 7) % EVERY;
			if (beat >= PUFF_TICKS || !haunted(level, player)) {
				continue;
			}
			if (player == minecraft.getCameraEntity() && minecraft.options.getCameraType().isFirstPerson() && beat > 2) {
				// in your own eyes a short puff is enough
				continue;
			}
			puff(level, player);
		}
	}

	private static boolean haunted(ClientLevel level, AbstractClientPlayer player) {
		return !level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SENSE), other -> other != player && other.isAlive()
			&& (other.is(EntityTypeTags.UNDEAD) || other instanceof Player ghost && "wisp".equals(ghost.getAttached(XenoformState.FORM)))).isEmpty();
	}

	private static void puff(ClientLevel level, AbstractClientPlayer player) {
		RandomSource random = player.getRandom();
		ParticleOptions mote = MagicFx.mote(COLOR, SIZE);
		Vec3 look = player.getLookAngle();
		Vec3 mouth = player.getEyePosition().add(0.0, -0.18, 0.0).add(look.scale(0.3));
		for (int i = 0; i < PUFF_MOTES; i++) {
			level.addParticle(mote, mouth.x, mouth.y, mouth.z,
				look.x * PUFF_SPEED + random.nextGaussian() * 0.006, look.y * PUFF_SPEED + 0.008 + random.nextGaussian() * 0.004,
				look.z * PUFF_SPEED + random.nextGaussian() * 0.006);
		}
	}
}
