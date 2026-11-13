package com.jaymoskingdoms;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BowItem;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * All in, or careful (fifth edition's Great Weapon Master, Sharpshooter and Reckless Attack, Andy's pick C6). Holding
 * sneak while swinging a two-hander ({@link ClassPerks#TWO_HANDED}) or while drawing a bow makes the blow reckless: it
 * is likelier to glance off ({@link #GLANCE_CHANCE}, then it lands at {@link #GLANCE}), but lands much harder when it
 * doesn't ({@link #HARD}). Whoever strikes recklessly is left open: for {@link #OPEN_TICKS} ticks foes have the upper hand
 * on them ({@link UpperHand}).
 * <p>
 * Sneaking up on something that hasn't noticed you is still a sneak attack ({@link Senses}), not a reckless one. Nothing
 * here touches right-click: sneak + right-click still talks, and right-click with a weapon still guards.
 */
public final class RecklessBlows {
	/** A reckless blow glances this often, then lands this share; otherwise it lands this many times as hard. */
	static final float GLANCE_CHANCE = 0.35F, GLANCE = 0.4F, HARD = 1.75F;
	/** Foes have the upper hand on a reckless striker this long (ticks). */
	static final int OPEN_TICKS = 40;
	/** The tag on an arrow loosed recklessly. */
	static final String ARROW_TAG = "jaymoskingdoms_reckless";

	/** When each reckless striker is open until (game time). */
	private static final Map<LivingEntity, Long> OPEN = new WeakHashMap<>();

	private RecklessBlows() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> OPEN.clear());
		UpperHand.add((attacker, target) -> {
			Long until = OPEN.get(target);
			return until != null && target.level().getGameTime() < until ? 1 : 0;
		});
		// an arrow loosed from a bow drawn while sneaking is a reckless one
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (entity instanceof AbstractArrow arrow && arrow.getOwner() instanceof ServerPlayer shooter && shooter.isShiftKeyDown()
				&& arrow.getWeaponItem() != null && arrow.getWeaponItem().getItem() instanceof BowItem) {
				arrow.addTag(ARROW_TAG);
			}
		});
	}

	/** Whether {@code who} left themselves open by a reckless blow just now. */
	public static boolean open(LivingEntity who) {
		Long until = OPEN.get(who);
		return until != null && who.level().getGameTime() < until;
	}

	/** {@code target} taking {@code amount} from {@code source}: a reckless blow glances or lands hard. */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (amount <= 0.0F || !(source.getEntity() instanceof ServerPlayer striker) || target == striker || !(target.level() instanceof ServerLevel level)
			|| Senses.cooling(target, source)) {
			return amount;
		}
		boolean swing = source.getDirectEntity() == striker && striker.isShiftKeyDown() && HiddenBlade.weapon(striker).is(ClassPerks.TWO_HANDED);
		boolean shot = source.getDirectEntity() instanceof AbstractArrow arrow && arrow.entityTags().contains(ARROW_TAG);
		if (!swing && !shot || swing && Senses.unaware(target, striker)) {
			return amount;
		}
		OPEN.put(striker, level.getGameTime() + OPEN_TICKS);
		if (level.getRandom().nextFloat() < GLANCE_CHANCE) {
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_WEAK, SoundSource.PLAYERS, 0.9F, 0.7F);
			level.sendParticles(ParticleTypes.POOF, target.getX(), target.getY(0.6), target.getZ(), 3, 0.2, 0.2, 0.2, 0.02);
			return amount * GLANCE;
		}
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 10, 0.3, 0.3, 0.3, 0.3);
		return amount * HARD;
	}
}
