package com.jaymoskingdoms;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * (0.5.54) People grow old and die: every MCA villager, and the folk of every realm (its king or lord, guards, archers).
 * Their age is counted from the day they came of age ({@link CourtWizards#grownDays}). Grown {@link CourtWizards#ELDER_DAYS}
 * days they're elders: their hair goes grey (MCA's), and they walk slower; near their end their hair is white and they're
 * slower still. Grown {@link #lifespan} days (each their own, {@link #OLD_AGE_DAYS} to {@link #OLD_AGE_DAYS} +
 * {@link #LIFESPAN_SPREAD}), they die in their sleep, in bed; one who doesn't lie down dies at home that night, once
 * {@link #GRACE_DAYS} past their time. A peaceful death: no scream, a corpse (Jaymo's Kitchen), and with MCA their
 * family mourns at their grave as for any death. A ruler who dies so is succeeded ({@link KingdomEvents#folkDied}); a
 * court wizard's office goes to the next eldest. MCA's couples have children to fill the beds the dead leave.
 */
public final class OldAge {
	/** Days grown before they die of old age: this many, plus up to {@link #LIFESPAN_SPREAD} more (their own, by their UUID). */
	static final int OLD_AGE_DAYS = 24, LIFESPAN_SPREAD = 12;
	/** Days past their time before they die at night wherever they are, if they never lie down in a bed. */
	static final int GRACE_DAYS = 2;
	/** Their last days (before their lifespan): white hair, slower still. */
	static final int FRAIL_DAYS = 6;
	/** How much slower an elder walks (and a frail one). */
	static final double ELDER_SLOW = -0.10, FRAIL_SLOW = -0.20;
	/** Not while they're fighting, or this soon after they were hurt (ticks). */
	static final int HURT_TICKS = 200;
	static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosKingdoms.id("old_age"));
	private static final Identifier SLOW = JaymosKingdoms.id("old_age");

	private OldAge() {
	}

	/** A realm's folk ages (from {@link Folk}'s step, once a second); true if they died of it just now. */
	static boolean folk(ServerLevel level, Folk folk) {
		Faction f = Factions.get(level.getServer(), folk.factionId());
		if (f == null || !f.type().isRealm()) {
			// (camps' barbarians, orcs and goblins live hard and die young)
			return false;
		}
		CourtWizards.age(level, folk);
		return step(level, folk);
	}

	/** {@code a}'s age shows, and if their time has come (and they're in bed), they die; true if they did. */
	static boolean step(ServerLevel level, Aging a) {
		long days = CourtWizards.grownDays(level, a);
		if (days < 0) {
			return false;
		}
		int lifespan = lifespan(a);
		look(a.mob(), days, lifespan);
		if (days < lifespan || !ready(level, a.mob(), days >= lifespan + GRACE_DAYS)) {
			return false;
		}
		die(level, a);
		return true;
	}

	/** Days {@code a} lives grown: their own, the same every time it's asked. */
	static int lifespan(Aging a) {
		long bits = a.mob().getUUID().getLeastSignificantBits() ^ a.mob().getUUID().getMostSignificantBits();
		return OLD_AGE_DAYS + (int)Math.floorMod(bits, (long)(LIFESPAN_SPREAD + 1));
	}

	/** Whether {@code s} is a death of old age. */
	static boolean is(DamageSource s) {
		return s.is(DAMAGE);
	}

	/** Grey hair and a slower step for an elder; white hair and slower still near the end. */
	private static void look(PathfinderMob mob, long days, int lifespan) {
		boolean elder = days >= CourtWizards.ELDER_DAYS;
		boolean frail = days >= lifespan - FRAIL_DAYS;
		AttributeInstance speed = mob.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed != null) {
			double slow = frail ? FRAIL_SLOW : elder ? ELDER_SLOW : 0.0;
			AttributeModifier now = speed.getModifier(SLOW);
			if (slow == 0.0) {
				if (now != null) {
					speed.removeModifier(SLOW);
				}
			} else if (now == null || now.amount() != slow) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(SLOW, slow, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
		if (elder) {
			McaCompat.greyHair(mob, frail);
		}
	}

	/** Asleep in bed; or {@code overdue} and it's night, not fighting, riding, hurt or talking to someone. */
	private static boolean ready(ServerLevel level, PathfinderMob mob, boolean overdue) {
		if (!mob.isAlive() || mob.isPassenger() || mob.isVehicle() || mob.getTarget() != null
			|| mob.getLastHurtByMob() != null && mob.tickCount - mob.getLastHurtByMobTimestamp() < HURT_TICKS || McaCompat.busy(mob)) {
			return false;
		}
		return mob.isSleeping() || overdue && Homes.bedtime(level);
	}

	/** They die peacefully (vanilla gets them up out of bed to do it): a little light rising from them, a soft chime, and their corpse. */
	private static void die(ServerLevel level, Aging a) {
		PathfinderMob mob = a.mob();
		// (raised again from their grave with MCA's staff, they come back an elder with years in them)
		a.setGrownSince(level.getGameTime() - (long)CourtWizards.ELDER_DAYS * CourtWizards.DAY_TICKS);
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE));
		mob.getCombatTracker().recordDamage(source, mob.getHealth());
		mob.setHealth(0.0F);
		mob.die(source);
		level.sendParticles(ParticleTypes.END_ROD, mob.getX(), mob.getY() + 0.6, mob.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
		level.playSound(null, mob.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.8F, 0.6F);
		JaymosKingdoms.LOGGER.debug("{} died of old age", mob.getName().getString());
	}
}
