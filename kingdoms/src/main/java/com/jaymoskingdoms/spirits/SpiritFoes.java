package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.Arcana;
import com.jaymoskingdoms.Factions;
import com.jaymoskingdoms.Folk;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.MobShields;
import com.jaymoskingdoms.Worker;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.241) Who a spirit's blows may land on, and the little things Grimsby's blades and Varro's javelins share. A spirit
 * never harms its keeper's friends: no tamed animals, no workers or folk of the keeper's own faction, no villagers or golems
 * it was not pointed at, no players. Of the rest, a blow lands on the hostile ({@link Enemy}), on anything already hunting the
 * keeper, and (when the keeper looked straight at it) on the thing they chose.
 */
final class SpiritFoes {
	private SpiritFoes() {
	}

	/** Whether {@code e} is on {@code caster}'s side and must never be struck. */
	static boolean ally(LivingEntity caster, LivingEntity e) {
		if (e instanceof OwnableEntity pet && pet.getOwnerReference() != null) {
			return true;
		}
		if (e instanceof Folk other && caster instanceof Folk folk && folk.sameSide(other)) {
			return true;
		}
		if (e instanceof Worker worker) {
			if (caster instanceof Worker mine) {
				return mine.factionId() == worker.factionId() && worker.factionId() >= 0;
			}
			if (caster instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
				var faction = Factions.of(level.getServer(), player.getUUID());
				return faction != null && faction.id() == worker.factionId();
			}
		}
		return false;
	}

	/** Whether a blow of {@code caster}'s may land on {@code e}; {@code explicit} when the caster picked it out (looked at it). */
	static boolean foe(LivingEntity caster, LivingEntity e, boolean explicit) {
		if (e == caster || !e.isAlive() || e.isSpectator() || ally(caster, e)) {
			return false;
		}
		if (e instanceof Player p) {
			return caster instanceof Mob m && m.getTarget() == p && !p.isCreative();
		}
		if (e instanceof Enemy || e instanceof Mob m && m.getTarget() == caster || caster instanceof Mob c && c.getTarget() == e) {
			return true;
		}
		return explicit && !(e instanceof Villager || e instanceof IronGolem || e instanceof Folk);
	}

	/** The foes of {@code caster} within {@code reach} (from the body's edge) and {@code halfDeg} of where they look, nearest first. */
	static List<LivingEntity> cone(LivingEntity caster, double reach, double halfDeg, boolean sight) {
		Vec3 eye = caster.getEyePosition();
		Vec3 look = caster.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		boolean aimed = flat.lengthSqr() > 1.0E-3;
		flat = aimed ? flat.normalize() : flat;
		double cos = Math.cos(Math.toRadians(halfDeg));
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : caster.level().getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(reach + 1.5), e -> foe(caster, e, false))) {
			if (reaches(caster, e, eye, flat, aimed, reach, cos)) {
				out.add(e);
			}
		}
		if (sight && caster instanceof Player player) {
			LivingEntity seen = Arcana.sighted(player);
			if (seen != null && !out.contains(seen) && foe(caster, seen, true) && reaches(caster, seen, eye, flat, aimed, reach, 0.0)) {
				out.add(seen);
			}
		}
		out.sort(Comparator.comparingDouble(caster::distanceToSqr));
		return out;
	}

	private static boolean reaches(LivingEntity caster, LivingEntity e, Vec3 eye, Vec3 flat, boolean aimed, double reach, double cos) {
		Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
		if (Math.abs(to.y) > 2.5 + e.getBbHeight() * 0.5 || to.length() - e.getBbWidth() * 0.5 > reach) {
			return false;
		}
		Vec3 across = new Vec3(to.x, 0.0, to.z);
		if (!aimed || across.lengthSqr() < 0.25) {
			return true;
		}
		return across.normalize().dot(flat) >= cos;
	}

	/** One blow of {@code damage} from {@code caster}'s spirit (magic, so the keeper has the kill). */
	static boolean strike(ServerLevel level, LivingEntity caster, LivingEntity target, float damage) {
		return target.hurtServer(level, level.damageSources().indirectMagic(caster, caster), damage);
	}

	/** Knocks {@code target} away from {@code from}, harder the lighter it is. */
	static void shove(ServerLevel level, LivingEntity caster, LivingEntity target, double power) {
		target.knockback(power, caster.getX() - target.getX(), caster.getZ() - target.getZ(), level.damageSources().indirectMagic(caster, caster), 0.0F);
	}

	/** Knocks {@code target}'s raised shield aside for {@code seconds}; whether it had one up. */
	static boolean breakShield(ServerLevel level, LivingEntity target, float seconds) {
		if (target instanceof Mob mob) {
			return MobShields.disable(level, mob, seconds);
		}
		ItemStack shield = target.getUseItem();
		BlocksAttacks blocks = shield.get(DataComponents.BLOCKS_ATTACKS);
		if (blocks == null || !target.isBlocking()) {
			return false;
		}
		blocks.disable(level, target, seconds, shield);
		target.stopUsingItem();
		return true;
	}

	/** A short line of motes from {@code from} to {@code to}, for the attuned, and a faint sparkle for everyone. */
	static void line(ServerLevel level, net.minecraft.core.particles.ParticleOptions sparkle, Vec3 from, Vec3 to, int steps) {
		for (int i = 0; i <= steps; i++) {
			Vec3 p = from.lerp(to, i / (double)steps);
			Mana.sendSeen(level, Mana.MOTE, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
			level.sendParticles(sparkle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	/** The damage source a spirit's cut carries (for callers that need to compare). */
	static DamageSource source(ServerLevel level, LivingEntity caster) {
		return level.damageSources().indirectMagic(caster, caster);
	}
}
