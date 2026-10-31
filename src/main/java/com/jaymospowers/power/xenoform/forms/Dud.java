package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.102, Xeno-Band patch 5) Dud, the one that can't be hurt: small, soft and round, stubby limbs, a worried face.
 * Never scanned; a corrupted band's glitch teaches it (patch 6), and {@code /powerdebug xenoform unlock dud} until then.
 * Hidden, like Nadir: not needed for a complete band. Nothing hurts it (but the void), and it can't hurt anything either
 * (its blows are stopped in {@code XenoformPower}). Every hit sends it flying, and every monster near goes for it. Bait, while friends fight.
 * <ul>
 * <li>R, Taunt: every monster within {@link #TAUNT_RADIUS} comes for you.</li>
 * <li>G, Boing: a bouncy hop.</li>
 * <li>V, Play Dead: flop over; monsters lose interest for a moment.</li>
 * <li>B, Roll: roll away fast.</li>
 * </ul>
 * No Ultimate: C does nothing.
 */
public final class Dud extends XenoForm {
	// ---- tuning ----
	/** How hard a blow throws it (a multiple of an ordinary knockback), and how often monsters near are drawn to it. */
	public static final double THROWN = 2.5, LURE_RADIUS = 12.0;
	public static final int LURE_EVERY = 20;
	public static final double TAUNT_RADIUS = 24.0;
	public static final int TAUNT_COOLDOWN = 100;
	public static final double BOING_UP = 1.0;
	public static final int BOING_COOLDOWN = 20;
	public static final int PLAY_DEAD_TICKS = 60, PLAY_DEAD_COOLDOWN = 200;
	public static final double ROLL_SPEED = 1.1;
	public static final int ROLL_COOLDOWN = 40;

	public Dud() {
		super("dud", 0.7F, 20.0F, 0.0, 0.0, 0.0, 0.0, Items.SLIME_BALL, 0xE8C0D8);
	}

	@Override
	public boolean scans(Entity creature) {
		return false;
	}

	@Override
	public boolean hidden() {
		return true;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.horn = 0;
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			return true;
		}
		// nothing hurts it, but every blow sends it flying
		Vec3 from = source.getSourcePosition();
		if (from != null && !source.is(DamageTypeTags.IS_FALL)) {
			Vec3 away = player.position().subtract(from).horizontal();
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
			double force = 0.4 * THROWN * Math.min(3.0, 1.0 + amount / 6.0);
			player.setDeltaMovement(away.x * force, 0.5 + force * 0.3, away.z * force);
			player.syncVelocity = true;
			sound(player, SoundEvents.SLIME_SQUISH, 1.0F, 1.6F);
		}
		return false;
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (!down) {
			return;
		}
		ServerLevel level = player.level();
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R)) {
					lure(player, TAUNT_RADIUS);
					burst(level, ParticleTypes.ANGRY_VILLAGER, player.getEyePosition().add(0.0, 0.5, 0.0), 4, 0.3, 0.0);
					sound(player, SoundEvents.VILLAGER_NO, 1.2F, 1.8F);
					cooldown(player, live, R, TAUNT_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (ready(player, live, G) && player.onGround()) {
					player.setDeltaMovement(player.getDeltaMovement().add(0.0, BOING_UP, 0.0));
					player.syncVelocity = true;
					sound(player, SoundEvents.SLIME_JUMP, 1.0F, 1.6F);
					cooldown(player, live, G, BOING_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					live.horn = PLAY_DEAD_TICKS;
					for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32.0), m -> m.getTarget() == player)) {
						mob.setTarget(null);
					}
					sound(player, SoundEvents.PLAYER_HURT_SWEET_BERRY_BUSH, 1.0F, 0.6F);
					cooldown(player, live, V, PLAY_DEAD_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					Vec3 look = player.getLookAngle().horizontal().normalize();
					player.setDeltaMovement(look.x * ROLL_SPEED, 0.2, look.z * ROLL_SPEED);
					player.syncVelocity = true;
					sound(player, SoundEvents.SLIME_SQUISH_SMALL, 1.0F, 1.2F);
					cooldown(player, live, B, ROLL_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** Every monster within {@code radius} that can see it goes for it. */
	private static void lure(ServerPlayer player, double radius) {
		if (player.isCreative() || player.isSpectator()) {
			return;
		}
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius),
			m -> m instanceof Enemy && m.isAlive() && m.hasLineOfSight(player))) {
			if (mob.getTarget() != player) {
				mob.setTarget(player);
			}
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		if (live.horn > 0) {
			// playing dead: flat on its back, nothing comes
			live.horn--;
			for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16.0), m -> m.getTarget() == player)) {
				mob.setTarget(null);
			}
			return;
		}
		if (player.tickCount % LURE_EVERY == 0) {
			lure(player, LURE_RADIUS);
		}
	}
}
