package com.jaymospowers.power.smoke;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.elemental.ElementalCorruption;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * (Jung) Smoke's part in Elemental Corruption: the smog's answer. The reckoning lays a fog over the land round the
 * killer: a thin smoke, nobody's, that dims the view without blinding it ({@link #FOG}, under {@link SmokeField#THICK}).
 * Through the wrath every creature near them is smothered for {@link #SMOTHER} (half a heart) every
 * {@link #SMOTHER_EVERY} ticks, and the fog is kept topped up; it thins away when the wrath ends.
 */
final class SmokeWrath implements ElementalCorruption.Wrath {
	// ---- tuning ----
	/** The fog: puffs of this much smoke, this many of them, out to this far (blocks) round the killer. */
	static final float FOG = 0.3F;
	static final int FOG_PUFFS = 48;
	static final double FOG_RADIUS = 16.0;
	/** Through the wrath, this many fresh puffs every this many ticks keep it up. */
	static final int KEEP_PUFFS = 6, KEEP_EVERY = 40;
	/** Smothering: creatures this near (blocks), this much (half a heart), every this many ticks. */
	static final double SMOTHER_RADIUS = 8.0;
	static final float SMOTHER = 1.0F;
	static final int SMOTHER_EVERY = 60;
	/** The smog pouring off the holder. */
	static final int COLOR = 0x5E4E30;

	@Override
	public void erupt(ServerPlayer player, ServerLevel level) {
		fog(player, level, FOG_PUFFS);
	}

	@Override
	public void tick(ServerPlayer player, ServerLevel level, long age) {
		if (age % KEEP_EVERY == 0) {
			fog(player, level, KEEP_PUFFS);
		}
		if (age == 0 || age % SMOTHER_EVERY != 0) {
			return;
		}
		for (LivingEntity target : ElementalCorruption.near(player, SMOTHER_RADIUS)) {
			target.hurtServer(level, player.damageSources().source(SmokeSmother.CHOKE_TYPE, player), SMOTHER);
		}
	}

	@Override
	public void aura(ServerPlayer player, ServerLevel level) {
		level.sendParticles(MagicFx.miasma(COLOR, 1.6F), player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.4, 0.7, 0.4, 0.01);
	}

	@Override
	public void disperse(ServerPlayer player, ServerLevel level) {
		SmokeField.thin(level, player.position(), FOG_RADIUS + 8.0, 1.0F);
	}

	/** Thin smoke, nobody's, at {@code puffs} spots round the holder, near the ground. */
	private static void fog(ServerPlayer player, ServerLevel level, int puffs) {
		for (int i = 0; i < puffs; i++) {
			double angle = level.getRandom().nextDouble() * Mth.TWO_PI, far = Math.sqrt(level.getRandom().nextDouble()) * FOG_RADIUS;
			Vec3 at = new Vec3(player.getX() + Math.cos(angle) * far, player.getY() + 0.5 + level.getRandom().nextDouble() * 2.0,
				player.getZ() + Math.sin(angle) * far);
			SmokeField.puff(level, at, FOG, null);
		}
	}
}
