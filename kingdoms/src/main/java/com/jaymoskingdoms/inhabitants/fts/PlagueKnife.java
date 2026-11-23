package com.jaymoskingdoms.inhabitants.fts;

import com.jaymoskingdoms.nightfall.Nightfall;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * A knife a Plague Bearer throws (from the ThrowingDaggerEntity of From the Shadows Reborn by Sonmok14 and L_Ender, AGPL v3;
 * the logic is ported, the code our own, see licenses/from-the-shadows/). It hurts a little and gives whoever it hits the
 * plague (Nightfall's Atrophy: no second plague of its own). It is the bearer's own, never picked up; the original's
 * throwing dagger as a weapon is the gear's, not this.
 */
public class PlagueKnife extends ThrowableItemProjectile {
	/** What a knife does (half hearts; the original's config default is 5, halved per dagger), and how long the plague it gives lasts. */
	static final float DAMAGE = 2.5F;
	static final int PLAGUE_TICKS = 300;

	public PlagueKnife(EntityType<? extends PlagueKnife> type, Level level) {
		super(type, level);
	}

	PlagueKnife(Level level, LivingEntity thrower) {
		super(Fts.PLAGUE_KNIFE, thrower, level, new ItemStack(Items.IRON_SWORD));
	}

	@Override
	protected Item getDefaultItem() {
		return Items.IRON_SWORD;
	}

	@Override
	protected void onHitEntity(EntityHitResult hit) {
		super.onHitEntity(hit);
		Entity target = hit.getEntity();
		if (this.level() instanceof ServerLevel level && !(target instanceof PlagueBearer)) {
			Entity owner = this.getOwner();
			boolean hurt = target.hurtServer(level, owner instanceof LivingEntity living ? this.damageSources().mobProjectile(this, living)
				: this.damageSources().thrown(this, owner), DAMAGE);
			if (hurt && target instanceof LivingEntity struck) {
				Nightfall.infect(struck, PLAGUE_TICKS, 0);
			}
		}
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (!this.level().isClientSide()) {
			this.discard();
		}
	}
}
