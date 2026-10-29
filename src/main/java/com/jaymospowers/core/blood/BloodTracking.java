package com.jaymospowers.core.blood;

import com.jaymospowers.core.wounds.Downed;
import java.util.EnumSet;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.182) A hunting mob ({@link BloodTrail#TRACKERS}) with nothing to fight smells a living bleeder's stains near it
 * and follows them, each to the next fresher one, until it sees whoever left them and goes for them. Monsters follow the
 * blood of players and villagers; wild beasts follow the blood of smaller animals, and of a player only once they're
 * badly hurt or down. Tamed animals never track on their own.
 */
final class BloodTracking extends Goal {
	static final int PRIORITY = 4;
	/** How far it smells a trail from, and how far along it looks for the next stain (blocks). */
	static final double SCENT = 16.0, STEP = 12.0;
	/** It goes for the bleeder when this close and in sight (blocks); with the trail run out, it heads straight to one this close. */
	static final double SIGHT = 12.0, END_OF_TRAIL = 24.0;
	/** It sniffs this often while idle (ticks, at least this, up to this many more), and gives up after this long. */
	static final int SNIFF = 40, SNIFF_SPREAD = 40, GIVE_UP = 1200;
	/** A wild beast goes after a player under this share of their health (or down). */
	static final float WEAK = 0.4F;
	static final double SPEED = 1.0;

	private final PathfinderMob mob;
	private @Nullable UUID quarry;
	private BloodTrail.@Nullable Mark next;
	private int sniff;
	private int left;

	BloodTracking(PathfinderMob mob) {
		this.mob = mob;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		if (this.mob.getTarget() != null || this.mob instanceof TamableAnimal tame && tame.isTame() || this.mob.isPassenger()
			|| !(this.mob.level() instanceof ServerLevel level)) {
			return false;
		}
		if (--this.sniff > 0) {
			return false;
		}
		this.sniff = SNIFF + this.mob.getRandom().nextInt(SNIFF_SPREAD);
		BloodTrail.Mark nearest = null;
		double best = Double.MAX_VALUE;
		for (BloodTrail.Mark mark : BloodTrail.near(level, this.mob.position(), SCENT, mark -> this.hunts(BloodTrail.bleeder(level, mark.by())))) {
			double d = this.mob.distanceToSqr(mark.x, mark.y, mark.z);
			if (d < best) {
				best = d;
				nearest = mark;
			}
		}
		if (nearest == null) {
			return false;
		}
		this.quarry = nearest.by();
		this.next = nearest;
		this.left = GIVE_UP;
		return true;
	}

	/** Whether it would hunt {@code prey} down by its blood. */
	private boolean hunts(@Nullable LivingEntity prey) {
		if (prey == null || prey == this.mob || prey.getType() == this.mob.getType() || !this.mob.canAttack(prey)
			|| prey instanceof Player player && (player.isCreative() || player.isSpectator())) {
			return false;
		}
		if (this.mob instanceof Enemy) {
			return prey instanceof Player || prey instanceof Npc;
		}
		if (prey instanceof Player) {
			return Downed.isDowned(prey) || prey.getHealth() < prey.getMaxHealth() * WEAK;
		}
		return prey instanceof Animal animal && !(animal instanceof TamableAnimal tame && tame.isTame())
			&& animal.getBbWidth() * animal.getBbHeight() < this.mob.getBbWidth() * this.mob.getBbHeight();
	}

	@Override
	public boolean canContinueToUse() {
		return this.next != null && this.left > 0 && this.mob.getTarget() == null;
	}

	@Override
	public void start() {
		this.walkTo(this.next);
	}

	@Override
	public void stop() {
		this.next = null;
		this.quarry = null;
		this.mob.getNavigation().stop();
	}

	@Override
	public void tick() {
		this.left--;
		if (!(this.mob.level() instanceof ServerLevel level) || this.next == null) {
			return;
		}
		LivingEntity prey = BloodTrail.bleeder(level, this.quarry);
		if (!this.hunts(prey)) {
			this.next = null;
			return;
		}
		if (this.mob.distanceToSqr(prey) < SIGHT * SIGHT && this.mob.hasLineOfSight(prey)) {
			this.mob.setTarget(prey);
			this.next = null;
			return;
		}
		boolean there = this.mob.distanceToSqr(this.next.x, this.next.y, this.next.z) < 2.25;
		if (!there && !this.mob.getNavigation().isDone()) {
			return;
		}
		BloodTrail.Mark fresher = BloodTrail.fresher(level, this.next.position(), STEP, this.quarry, this.next.laid);
		if (fresher != null) {
			this.next = fresher;
			this.walkTo(fresher);
		} else if (this.mob.distanceToSqr(prey) < END_OF_TRAIL * END_OF_TRAIL) {
			// the trail ends here and they can't be far: head where the blood leads
			this.mob.getNavigation().moveTo(prey, SPEED);
		} else {
			this.next = null;
		}
	}

	private void walkTo(BloodTrail.@Nullable Mark mark) {
		if (mark != null) {
			this.mob.getNavigation().moveTo(mark.x, mark.y, mark.z, SPEED);
		}
	}
}
