package com.jaymoskingdoms.inhabitants;

import java.util.EnumSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** How the draugr's ranks fight beyond a plain rush: the archer's, wight's and scourge's shots, and backing off hurt. */
final class DraugrFight {
	private DraugrFight() {
	}

	/**
	 * An archer, a wight at a distance, or a scourge: holds its ground in sight of its foe (a scourge backs away from one
	 * that closes), raises its bow or hand, and looses after the rank's wind-up. A bolt flies at where the foe stood
	 * {@link Draugr#AIM_LOCK} ticks before; a blow on a scourge stops its casting.
	 */
	static final class Shots extends Goal {
		private final Draugr draugr;
		private int windUp = -1, wait;
		private @Nullable Vec3 aim;

		Shots(Draugr draugr) {
			this.draugr = draugr;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			LivingEntity target = this.draugr.getTarget();
			return target != null && target.isAlive() && this.draugr.shoots(target);
		}

		@Override
		public boolean canContinueToUse() {
			LivingEntity target = this.draugr.getTarget();
			return target != null && target.isAlive() && (this.windUp >= 0 || this.draugr.shoots(target));
		}

		@Override
		public void stop() {
			this.windUp = -1;
			this.draugr.getNavigation().stop();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity target = this.draugr.getTarget();
			if (target == null || !(this.draugr.level() instanceof ServerLevel level)) {
				return;
			}
			this.draugr.getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (this.wait > 0) {
				this.wait--;
			}
			if (this.draugr.castBroken > 0) {
				this.windUp = -1;
				this.wait = Math.max(this.wait, this.draugr.castBroken);
			}
			double distance = this.draugr.distanceTo(target);
			boolean sees = this.draugr.getSensing().hasLineOfSight(target);
			if (this.windUp >= 0) {
				this.draugr.getNavigation().stop();
				this.windUp++;
				if (this.windUp == this.draugr.windUp() - Draugr.AIM_LOCK) {
					this.aim = target.getBoundingBox().getCenter();
				}
				if (this.windUp >= this.draugr.windUp()) {
					this.draugr.loose(level, target, this.aim != null ? this.aim : target.getBoundingBox().getCenter());
					this.windUp = -1;
					this.wait = this.draugr.shotGap();
				}
				return;
			}
			if (!sees || distance > Draugr.SHOT_RANGE) {
				this.draugr.getNavigation().moveTo(target, 1.0);
			} else if (this.draugr.rank() == Draugr.Rank.SCOURGE && distance < Draugr.SCOURGE_CLOSE) {
				if (this.draugr.getNavigation().isDone()) {
					Vec3 away = DefaultRandomPos.getPosAway(this.draugr, 8, 4, target.position());
					if (away != null) {
						this.draugr.getNavigation().moveTo(away.x, away.y, away.z, 1.1);
					}
				}
			} else {
				this.draugr.getNavigation().stop();
			}
			if (sees && distance <= Draugr.SHOT_RANGE && this.wait == 0) {
				this.windUp = 0;
				this.aim = null;
				this.draugr.windUpStart(level);
			}
		}
	}

	/** A warrior or wight under {@link Draugr#BACK_OFF_AT} of its health gives ground for a breath (a shield stays up the while). */
	static final class BackOff extends Goal {
		private final Draugr draugr;

		BackOff(Draugr draugr) {
			this.draugr = draugr;
			this.setFlags(EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			Draugr.Rank rank = this.draugr.rank();
			LivingEntity target = this.draugr.getTarget();
			return (rank == Draugr.Rank.WARRIOR || rank == Draugr.Rank.WIGHT) && this.draugr.backOffWait == 0 && target != null
				&& this.draugr.getHealth() < this.draugr.getMaxHealth() * Draugr.BACK_OFF_AT && this.draugr.distanceToSqr(target) < 36.0;
		}

		@Override
		public boolean canContinueToUse() {
			return this.draugr.backingOff > 0 && this.draugr.getTarget() != null;
		}

		@Override
		public void start() {
			this.draugr.backingOff = Draugr.BACK_OFF_TICKS;
			this.draugr.backOffWait = Draugr.BACK_OFF_GAP;
		}

		@Override
		public void stop() {
			this.draugr.backingOff = 0;
			this.draugr.getNavigation().stop();
		}

		@Override
		public void tick() {
			LivingEntity target = this.draugr.getTarget();
			this.draugr.backingOff--;
			if (target == null) {
				return;
			}
			this.draugr.getLookControl().setLookAt(target, 30.0F, 30.0F);
			if (this.draugr.getNavigation().isDone()) {
				Vec3 away = DefaultRandomPos.getPosAway(this.draugr, 8, 4, target.position());
				if (away != null) {
					this.draugr.getNavigation().moveTo(away.x, away.y, away.z, 1.0);
				}
			}
		}
	}
}
