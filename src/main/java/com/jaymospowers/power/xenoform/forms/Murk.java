package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * (0.18.111, Scan D of {@code docs/ScanGaps-Plan.md}) Murk, the ink squid: a soft, mottled purple-grey body, a long
 * mantle swept back from the head, big black eyes, and a fringe of tentacles hanging from its jaw. 22 health, fists of
 * 4. The second swimmer: in water it's quick (Dolphin's Grace II), breathes and sees in the dark. Scanned from a squid
 * or glow squid, or Naturalist's anglerfish or the Friends &amp; Foes barnacle ({@code XenoDna}).
 * <ul>
 * <li>R, Ink: a cloud of ink round you; everything in it is blinded and loses you.</li>
 * <li>G, Tentacles: up to {@link #GRIP_MAX} creatures near you are seized and held to you; G again lets go.</li>
 * <li>V, Jet: a dash where you look, far in water, short on land.</li>
 * <li>B, Lure: a glow that draws fish and mobs in the water to you.</li>
 * </ul>
 */
public final class Murk extends XenoForm {
	// ---- tuning ----
	public static final double INK_RADIUS = 5.0;
	public static final int INK_BLIND = 100, INK_COOLDOWN = 200;
	public static final int GRIP_MAX = 4, GRIP_TICKS = 80, GRIP_COOLDOWN = 240;
	public static final double GRIP_REACH = 6.0, GRIP_HOLD = 1.6;
	public static final float GRIP_DAMAGE = 1.0F;
	public static final double JET_WATER = 1.6, JET_LAND = 0.8;
	public static final int JET_COOLDOWN = 50;
	public static final double LURE_RADIUS = 24.0, LURE_SPEED = 1.2;
	public static final int LURE_TICKS = 200, LURE_COOLDOWN = 400;

	public Murk() {
		super("murk", 0.95F, 22.0F, 4.0, 0.0, 0.1, 0.0, Items.INK_SAC, 0x6A5878);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Squid;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.grips.clear();
		live.gripTicks = live.lure = 0;
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
					for (LivingEntity e : around(player, INK_RADIUS)) {
						e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, INK_BLIND, 0), player);
						if (e instanceof Mob mob && mob.getTarget() == player) {
							mob.setTarget(null);
						}
					}
					burst(level, ParticleTypes.SQUID_INK, player.position().add(0.0, 1.0, 0.0), 80, INK_RADIUS * 0.5, 0.05);
					sound(player, SoundEvents.SQUID_SQUIRT, 1.4F, 0.8F);
					cooldown(player, live, R, INK_COOLDOWN);
				}
			}
			case ABILITY_2 -> {
				if (!live.grips.isEmpty()) {
					live.grips.clear();
					live.gripTicks = 0;
					return;
				}
				if (!ready(player, live, G)) {
					return;
				}
				LivingEntity aimed = aimed(player, GRIP_REACH, e -> true);
				if (aimed != null) {
					live.grips.add(aimed.getId());
				}
				for (LivingEntity e : around(player, GRIP_REACH)) {
					if (live.grips.size() >= GRIP_MAX) {
						break;
					}
					if (!live.grips.contains(e.getId())) {
						live.grips.add(e.getId());
					}
				}
				if (live.grips.isEmpty()) {
					return;
				}
				live.gripTicks = GRIP_TICKS;
				sound(player, SoundEvents.SQUID_AMBIENT, 1.2F, 0.6F);
				cooldown(player, live, G, GRIP_COOLDOWN);
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					double speed = player.isInWater() ? JET_WATER : JET_LAND;
					player.setDeltaMovement(player.getLookAngle().scale(speed));
					player.syncVelocity = true;
					burst(level, player.isInWater() ? ParticleTypes.BUBBLE : ParticleTypes.SQUID_INK, player.position().add(0.0, 0.6, 0.0), 20, 0.3, 0.02);
					sound(player, SoundEvents.SQUID_SQUIRT, 1.0F, 1.3F);
					cooldown(player, live, V, JET_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					live.lure = LURE_TICKS;
					sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 0.6F);
					cooldown(player, live, B, LURE_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (player.isInWater()) {
			player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 10, 1, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 10, 0, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 220, 0, false, false));
		}
		if (!live.grips.isEmpty()) {
			if (--live.gripTicks <= 0) {
				live.grips.clear();
			} else {
				hold(player, live, level);
			}
		}
		if (live.lure > 0) {
			live.lure--;
			if (player.tickCount % 4 == 0) {
				level.sendParticles(ParticleTypes.GLOW, player.getX(), player.getY() + 1.4, player.getZ(), 3, 0.4, 0.4, 0.4, 0.01);
			}
			if (live.lure % 10 == 0) {
				for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(LURE_RADIUS),
					m -> m.isAlive() && m.isInWater() && !spared(player, m))) {
					mob.getNavigation().moveTo(player, LURE_SPEED);
				}
			}
		}
	}

	/** The tentacles: each held creature is pulled in and kept close, slowed and squeezed. */
	private static void hold(ServerPlayer player, XenoformPower.Live live, ServerLevel level) {
		var it = live.grips.iterator();
		int i = 0;
		while (it.hasNext()) {
			Entity e = level.getEntity(it.next());
			if (!(e instanceof LivingEntity held) || !held.isAlive() || held.distanceTo(player) > GRIP_REACH * 2.0) {
				it.remove();
				continue;
			}
			double a = player.getYRot() * net.minecraft.util.Mth.DEG_TO_RAD + i * Math.PI * 0.5;
			Vec3 spot = player.position().add(-Math.sin(a) * GRIP_HOLD, 0.2, Math.cos(a) * GRIP_HOLD);
			Vec3 pull = spot.subtract(held.position());
			held.setDeltaMovement(pull.scale(0.35));
			held.syncVelocity = true;
			held.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 10, 3, false, false), player);
			if (live.gripTicks % 20 == 0) {
				held.hurtServer(level, player.damageSources().playerAttack(player), GRIP_DAMAGE);
			}
			if (player.tickCount % 3 == 0) {
				beam(level, player.position().add(0.0, 1.0, 0.0), held.getBoundingBox().getCenter(), ParticleTypes.SQUID_INK, 4);
			}
			i++;
		}
	}

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		burst(player.level(), ParticleTypes.GLOW_SQUID_INK, player.position().add(0.0, 1.0, 0.0), 60, 1.0, 0.05);
		sound(player, SoundEvents.GLOW_SQUID_SQUIRT, 1.6F, 0.6F);
		return true;
	}
}
