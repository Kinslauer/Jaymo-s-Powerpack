package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.frost.Freezing;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The cold that settles on whatever a Frost-born's beam or breath stays on: after {@link #AFTER} ticks (about 6 s) on the
 * same target it freezes, the way the mod's own Freezing effect does it (frost creeping up the body, {@link Freezing}) with a
 * hard slowing on top, for {@link #FREEZE_TICKS}. Then it needs another {@link #AFTER} ticks on target to freeze it again.
 * One of these per fighter per move; it holds no world state.
 */
final class ZFighterChill {
	/** Ticks on one target before it freezes (6 s), how long it stays frozen, and the slowing on top (a Slowness level). */
	static final int AFTER = 120, FREEZE_TICKS = 100, SLOW_LEVEL = 5;
	static final int COLOR = 0xBFEFFF;

	private @Nullable UUID target;
	private int on;

	/** Another tick of the cold on {@code foe} ({@code null}: it found nothing). Returns true the tick it freezes. */
	boolean tick(ServerLevel level, @Nullable LivingEntity foe) {
		if (foe == null) {
			on = Math.max(0, on - 2);
			if (on == 0) {
				target = null;
			}
			return false;
		}
		if (!foe.getUUID().equals(target)) {
			target = foe.getUUID();
			on = 0;
		}
		if (++on < AFTER) {
			return false;
		}
		on = 0;
		freeze(level, foe);
		return true;
	}

	/** How far to freezing, 0-1. */
	float progress() {
		return Math.min(1.0F, on / (float)AFTER);
	}

	void reset() {
		target = null;
		on = 0;
	}

	static void freeze(ServerLevel level, LivingEntity foe) {
		if (Freezing.immune(foe)) {
			return;
		}
		foe.addEffect(new MobEffectInstance(Freezing.FREEZING, FREEZE_TICKS, 0));
		foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FREEZE_TICKS, SLOW_LEVEL, false, true));
		foe.setTicksFrozen(foe.getTicksRequiredToFreeze() + FREEZE_TICKS);
		Vec3 at = foe.getBoundingBox().getCenter();
		MagicFx.blast(level, at, 1.2F + foe.getBbWidth(), COLOR);
		MagicFx.ring(level, foe.position().add(0.0, 0.1, 0.0), 1.0F + foe.getBbWidth() * 0.6F, COLOR);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.9F, 1.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.POWDER_SNOW_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
	}
}
