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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.117, Scan F) Fuse, the blast alien: a mottled green body with a pale chest, a blocky head with the creeper's
 * dark hollow eyes and frown, and stubby legs. 20 health, fists of 4, and every punch pops (a little burst that knocks
 * back). Its own blasts never hurt it. Scanned from a creeper.
 * <ul>
 * <li>R, Blast Pod: a pod stuck on the creature or block you look at; it goes off {@link #POD_FUSE} ticks later.</li>
 * <li>G, Fuse Dash: a hissing dash where you look; the first creature met is popped.</li>
 * <li>V, Self-Destruct (hold): swell, and let go when full to blow up and re-form a moment later. It costs band charge,
 * not health; let go early and nothing happens. The blast is a big one, then a ring of small ones over the next ticks
 * (as the meteor's are spread out), so it doesn't lag.</li>
 * <li>B, Defuse: primed TNT and swelling creepers near you are put out, and for {@link #DEFUSE_TICKS} ticks blasts
 * don't hurt you.</li>
 * </ul>
 */
public final class Fuse extends XenoForm {
	// ---- tuning ----
	public static final double POP_SHOVE = 0.6, POP_UP = 0.25;
	public static final double POD_REACH = 8.0;
	public static final int POD_FUSE = 40, POD_COOLDOWN = 100;
	public static final float POD_POWER = 2.0F;
	public static final double DASH_SPEED = 1.3, DASH_HIT = 1.0;
	public static final int DASH_TICKS = 12, DASH_COOLDOWN = 60;
	public static final float DASH_DAMAGE = 5.0F;
	/** Self-Destruct: ticks to swell full, the main blast, the ring after it (count, distance, power). */
	public static final int CHARGE_FULL = 30, CHARGE_COOLDOWN = 400, CHARGE_COST = 300, REFORM_TICKS = 20;
	public static final float CHARGE_POWER = 3.5F, RING_POWER = 1.5F;
	public static final int RING_BLASTS = 4;
	public static final double RING_OUT = 3.0;
	public static final double DEFUSE_RADIUS = 10.0;
	public static final int DEFUSE_TICKS = 100, DEFUSE_COOLDOWN = 300;
	public static final float DEFUSE_HEAL = 2.0F;

	public Fuse() {
		super("fuse", 1.0F, 20.0F, 4.0, 0.02, 0.0, 0.0, Items.GUNPOWDER, 0x58A048);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Creeper;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.anchor = null;
		live.latched = -1;
		live.latchTicks = live.rush = live.swell = live.blasts = 0;
		live.hideUntil = 0L;
	}

	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		if (!source.is(DamageTypeTags.IS_EXPLOSION)) {
			return true;
		}
		// its own blasts never hurt it, and while Defuse lasts no blast does
		return source.getEntity() != player && (live.hideUntil == 0L || player.level().getGameTime() > live.hideUntil);
	}

	/** Every punch pops. */
	@Override
	public void onDealtDamage(ServerPlayer player, XenoformPower.Live live, LivingEntity target) {
		pop(player, target);
	}

	private static void pop(ServerPlayer player, LivingEntity target) {
		ServerLevel level = player.level();
		shove(target, player.position(), POP_SHOVE, POP_UP);
		Vec3 at = target.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.03);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, net.minecraft.sounds.SoundSource.PLAYERS, 0.3F, 1.8F);
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		ServerLevel level = player.level();
		if (key == PowerKey.ABILITY_3) {
			if (down) {
				if (ready(player, live, V) && live.blasts == 0) {
					live.swell = 1;
					sound(player, SoundEvents.CREEPER_PRIMED, 1.0F, 0.9F);
				}
			} else if (live.swell > 0) {
				boolean full = live.swell >= CHARGE_FULL;
				live.swell = 0;
				if (full) {
					detonate(player, live, level);
				}
			}
			return;
		}
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (!ready(player, live, R) || live.latchTicks > 0) {
					return;
				}
				LivingEntity target = aimed(player, POD_REACH, e -> true);
				if (target != null) {
					live.latched = target.getId();
					live.anchor = null;
				} else {
					HitResult hit = player.pick(POD_REACH, 1.0F, false);
					if (hit.getType() != HitResult.Type.BLOCK) {
						return;
					}
					live.latched = -1;
					live.anchor = hit.getLocation();
				}
				live.latchTicks = POD_FUSE;
				sound(player, SoundEvents.SLIME_ATTACK, 1.0F, 1.6F);
				cooldown(player, live, R, POD_COOLDOWN);
			}
			case ABILITY_2 -> {
				if (ready(player, live, G)) {
					player.setDeltaMovement(player.getLookAngle().scale(DASH_SPEED));
					player.syncVelocity = true;
					live.rush = DASH_TICKS;
					sound(player, SoundEvents.CREEPER_PRIMED, 1.2F, 1.5F);
					cooldown(player, live, G, DASH_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					defuse(player, live, level);
					cooldown(player, live, B, DEFUSE_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** Self-Destruct: the big blast at its feet, the ring to follow, gone a moment and back. */
	private static void detonate(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		level.explode(player, player.getX(), player.getY() + 0.5, player.getZ(), CHARGE_POWER, Level.ExplosionInteraction.MOB);
		live.blasts = RING_BLASTS;
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, REFORM_TICKS, 0, false, false));
		XenoformPower.drain(player, live, CHARGE_COST);
		cooldown(player, live, V, CHARGE_COOLDOWN);
	}

	/** Defuse: what's about to blow near it is put out; blasts can't hurt it for a while. */
	private static void defuse(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		int soaked = 0;
		for (PrimedTnt tnt : level.getEntitiesOfClass(PrimedTnt.class, player.getBoundingBox().inflate(DEFUSE_RADIUS))) {
			level.addFreshEntity(new ItemEntity(level, tnt.getX(), tnt.getY(), tnt.getZ(), new ItemStack(Items.TNT)));
			level.sendParticles(ParticleTypes.SMOKE, tnt.getX(), tnt.getY() + 0.5, tnt.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
			tnt.discard();
			soaked++;
		}
		for (Creeper creeper : level.getEntitiesOfClass(Creeper.class, player.getBoundingBox().inflate(DEFUSE_RADIUS), c -> c.getSwellDir() > 0)) {
			creeper.setSwellDir(-1);
			level.sendParticles(ParticleTypes.SMOKE, creeper.getX(), creeper.getY() + 1.0, creeper.getZ(), 8, 0.2, 0.3, 0.2, 0.02);
			soaked++;
		}
		if (live.latchTicks > 0) {
			live.latchTicks = 0;
			live.latched = -1;
			live.anchor = null;
			soaked++;
		}
		live.hideUntil = level.getGameTime() + DEFUSE_TICKS;
		if (soaked > 0) {
			player.heal(DEFUSE_HEAL * soaked);
		}
		burst(level, ParticleTypes.SMOKE, player.position().add(0.0, 1.0, 0.0), 20, 0.5, 0.03);
		sound(player, SoundEvents.FIRE_EXTINGUISH, 1.0F, 1.0F);
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (live.swell > 0) {
			live.swell = Math.min(CHARGE_FULL + 1, live.swell + 1);
			if (player.tickCount % 3 == 0) {
				level.sendParticles(live.swell >= CHARGE_FULL ? ParticleTypes.FLAME : ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.3, 0.5,
					0.3, 0.02);
			}
			if (live.swell == CHARGE_FULL) {
				sound(player, SoundEvents.CREEPER_PRIMED, 1.4F, 0.6F);
			}
		}
		if (live.blasts > 0) {
			// one small blast a tick round where it went off
			double a = live.blasts / (double)RING_BLASTS * Math.PI * 2.0;
			level.explode(player, player.getX() + Math.cos(a) * RING_OUT, player.getY() + 0.5, player.getZ() + Math.sin(a) * RING_OUT, RING_POWER,
				Level.ExplosionInteraction.MOB);
			if (--live.blasts == 0) {
				burst(level, ParticleTypes.HAPPY_VILLAGER, player.position().add(0.0, 1.0, 0.0), 12, 0.4, 0.02);
			}
		}
		if (live.latchTicks > 0) {
			Vec3 at = live.anchor;
			if (live.latched >= 0) {
				Entity e = level.getEntity(live.latched);
				at = e != null && e.isAlive() ? e.getBoundingBox().getCenter() : null;
				live.anchor = at;
			}
			if (at == null) {
				live.latchTicks = 0;
				live.latched = -1;
			} else if (--live.latchTicks == 0) {
				level.explode(player, at.x, at.y, at.z, POD_POWER, Level.ExplosionInteraction.MOB);
				live.latched = -1;
				live.anchor = null;
			} else if (live.latchTicks % 4 == 0) {
				level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 2, 0.1, 0.1, 0.1, 0.01);
				if (live.latchTicks % 10 == 0) {
					level.playSound(null, at.x, at.y, at.z, SoundEvents.CREEPER_PRIMED, net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 1.6F);
				}
			}
		}
		if (live.rush > 0) {
			live.rush--;
			level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.6, player.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
			for (LivingEntity e : around(player, DASH_HIT + 1.0)) {
				if (e.getBoundingBox().inflate(DASH_HIT * 0.5).intersects(player.getBoundingBox())) {
					e.hurtServer(level, player.damageSources().playerAttack(player), DASH_DAMAGE);
					pop(player, e);
					live.rush = 0;
					break;
				}
			}
		}
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.SMOKE, player.position().add(0.0, 1.0, 0.0), 40, 0.8, 0.05);
		sound(player, SoundEvents.CREEPER_PRIMED, 1.6F, 0.5F);
		return true;
	}
}
