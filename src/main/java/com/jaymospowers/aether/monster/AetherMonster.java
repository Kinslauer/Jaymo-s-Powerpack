package com.jaymospowers.aether.monster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * What the Aether's monsters share: weak and strong spots. Every monster is hurt more or less by three kinds of blow:
 * slash (swords and axes), impact (picks, shovels, hoes, maces, fists, explosions) and pierce (arrows, spears). -1 means
 * weak (takes more), +1 strong (takes less). Blighted monsters (Tempest, Cockatrice) are weaker still while they stand
 * in daylight.
 */
public abstract class AetherMonster extends Monster {
	// ---- tuning ----
	/** Each step of strength takes this much off the damage taken (+1 is 25 percent less, +2 half); each step of weakness adds this much. */
	public static final float STRONG_STEP = 0.25F, WEAK_STEP = 0.3F;
	/** A blighted monster's weakness in daylight is this much worse. */
	public static final float SUN_WEAKNESS = 1.5F;

	private final int slash, impact, pierce;

	protected AetherMonster(EntityType<? extends AetherMonster> type, Level level, int slash, int impact, int pierce) {
		super(type, level);
		this.slash = slash;
		this.impact = impact;
		this.pierce = pierce;
	}

	/** True for monsters that fade in sunlight (they are hurt harder there). */
	protected boolean blighted() {
		return false;
	}

	/** Whether the sun is on this monster right now. */
	protected boolean inSun() {
		return this.level().isBrightOutside() && this.level().canSeeSky(this.blockPosition());
	}

	@Override
	public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
		return super.hurtServer(level, source, amount * this.factor(source));
	}

	private float factor(DamageSource source) {
		int spot;
		if (source.is(DamageTypeTags.IS_PROJECTILE)) {
			spot = this.pierce;
		} else if (source.getDirectEntity() instanceof LivingEntity attacker && attacker == source.getEntity()) {
			ItemStack held = attacker.getMainHandItem();
			if (held.is(ItemTags.SPEARS)) {
				spot = this.pierce;
			} else if (held.is(ItemTags.SWORDS) || held.is(ItemTags.AXES)) {
				spot = this.slash;
			} else {
				// picks, shovels, hoes, maces and bare fists
				spot = this.impact;
			}
		} else if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			spot = this.impact;
		} else {
			return 1.0F;
		}
		if (spot > 0) {
			return Math.max(0.4F, 1.0F - STRONG_STEP * spot);
		}
		if (spot < 0) {
			return 1.0F - WEAK_STEP * spot * (this.blighted() && this.inSun() ? SUN_WEAKNESS : 1.0F);
		}
		return 1.0F;
	}
}
