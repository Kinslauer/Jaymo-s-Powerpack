package com.jaymospowers.power.xenoform.forms.ultimate;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoUltimate;
import com.jaymospowers.power.xenoform.XenoformPower;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * (0.20.21, Ultimates patch 1) Knell's Ultimate, The Tolling: the hunched deep-dark beast grown heavier in sculk-black plate, with a
 * health bar of its own on top of Knell's. Its model is {@code UltimateTolling}.
 * <ul>
 * <li>R, Sonic Lance: a longer, wider, harder Sonic Boom that still goes through walls.</li>
 * <li>G (hold), Deep Sense: Tremor Sense reaching {@link #SENSE_RADIUS} blocks; invisible creatures glow too.</li>
 * <li>V, Deep Shriek: a wider Shriek that also slows everything it reaches.</li>
 * <li>B, Undertow: Burrow that surfaces farther away and throws everything near the exit into the air.</li>
 * <li>C, The Tolling: you plant yourself and toll {@link #TOLLS} times, every {@link #TOLL_EVERY} ticks; each toll is a ring of sonic
 * force through everything within {@link #TOLL_RADIUS}. Again {@link #TOLL_COOLDOWN} ticks after the last toll.</li>
 * </ul>
 */
public final class Tolling extends XenoUltimate {
	// ---- tuning ----
	/** Sonic Lance: cooldown, reach, half-width, shove, damage. */
	/** The Tolling: the Slowness amplifier that plants you for the tolls (0.20.21). */
	public static final int PLANT_SLOWNESS = 9;
	public static final int LANCE_COOLDOWN = 160;
	public static final double LANCE_RANGE = 24.0, LANCE_WIDTH = 2.5, LANCE_KNOCKBACK = 2.8;
	public static final float LANCE_DAMAGE = 14.0F;
	/** Deep Sense: reach, ticks between sweeps, how long a sweep's glow lasts. */
	public static final double SENSE_RADIUS = 40.0;
	public static final int SENSE_EVERY = 10, SENSE_GLOW_TICKS = 15;
	/** Deep Shriek: cooldown, reach, damage, ticks of darkness and of Slowness II. */
	public static final int SHRIEK_COOLDOWN = 300, SHRIEK_DARKNESS_TICKS = 120, SHRIEK_SLOW_TICKS = 120;
	public static final double SHRIEK_RADIUS = 16.0;
	public static final float SHRIEK_DAMAGE = 6.0F;
	/** Undertow: cooldown, ticks to dig down (the same again to come up), how far away it surfaces, the exit's reach, damage and throw. */
	public static final int BURROW_COOLDOWN = 240, BURROW_TICKS = 20;
	public static final double BURROW_RANGE = 24.0, UNDERTOW_RADIUS = 4.0, UNDERTOW_THROW = 0.9;
	public static final float UNDERTOW_DAMAGE = 8.0F;
	/** The Tolling: how many tolls, how far apart, their reach, shove and bite; C's cooldown after the last (30 s); ticks of darkness for players. */
	public static final int TOLLS = 7, TOLL_EVERY = 40, TOLL_COOLDOWN = 600, TOLL_DARKNESS_TICKS = 60;
	public static final double TOLL_RADIUS = 10.0, TOLL_KNOCKBACK = 1.4;
	public static final float TOLL_DAMAGE = 7.0F;
	/** Screen shake (degrees) for a toll and the surfacing, and how far the surfacing is felt. */
	public static final float TOLL_SHAKE = 1.8F, UNDERTOW_SHAKE = 1.2F;
	public static final double UNDERTOW_SHAKE_RANGE = 14.0;

	/** (0.20.21) The Resistance the player had before the guard (and when it was noted), put back if the guard is dropped (server thread only). */
	private record Prior(MobEffectInstance effect, long at) {
	}

	private static final Map<UUID, Prior> PRIOR = new HashMap<>();

	public Tolling(XenoForm base) {
		super(base, 1.6F, 36.0F, 11.0, -0.2, 0.95, 1.2);
	}

	@Override
	public Item[] icons() {
		return new Item[] {Items.ECHO_SHARD, Items.CALIBRATED_SCULK_SENSOR, Items.SCULK_SHRIEKER, Items.REINFORCED_DEEPSLATE, Items.BELL};
	}

	/** The change-in film zooms on these in turn (model part names): the plated forearms, the hump and its spine, the rib plates, the horns. */
	@Override
	public String[] morphParts() {
		return new String[] {"right_forearm+left_forearm", "hump+spine", "right_rib+left_rib", "right_horn+left_horn"};
	}

	@Override
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
		live.sense = false;
		live.burrow = 0;
		live.burrowTo = null;
		live.tolls = 0;
		live.lastToll = -1L;
		sound(player, SoundEvents.WARDEN_EMERGE, 1.5F, 0.6F);
		sound(player, SoundEvents.BELL_BLOCK, 2.0F, 0.5F);
		Shakes.heavy(player, player.position(), 20.0, 1.4F);
	}

	/** (0.20.21) Where each planted beast stands, so jumping and sprinting cannot carry it off. */
	private static final java.util.Map<java.util.UUID, Vec3> ANCHOR = new java.util.HashMap<>();

	/** (0.20.21) A Tolling cut short still owes its wait on C: starts now, without a move's drain or a mastery use. Also run before a quick change snapshots the cooldowns. */
	public static void pendingCooldown(ServerPlayer player, XenoformPower.Live live) {
		if (live.tolls > 0) {
			live.cooldownUntil[C] = player.level().getGameTime() + TOLL_COOLDOWN;
			live.cooldownLength[C] = TOLL_COOLDOWN;
			live.hudDirty = true;
		}
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		if (live.tolls > 0) {
			// dropped mid-sequence: the planted guard ends with it (the framework's wait on C covers the next change-in)
			// (0.20.21) only the guard this planted: a potion's or beacon's Resistance stays, and the 30 s wait starts now
			MobEffectInstance guard = player.getEffect(MobEffects.RESISTANCE);
			if (guard != null && guard.getAmplifier() == 1 && !guard.isAmbient() && !guard.isVisible() && guard.getDuration() <= TOLLS * TOLL_EVERY + 20) {
				player.removeEffect(MobEffects.RESISTANCE);
				Prior prior = PRIOR.get(player.getUUID());
				if (prior != null) { // (0.20.21) the weaker Resistance the guard hid comes back, with the time it ran down meanwhile
					long elapsed = player.level().getGameTime() - prior.at();
					MobEffectInstance had = prior.effect();
					if (had.isInfiniteDuration() || had.getDuration() > elapsed) {
						player.addEffect(had.isInfiniteDuration() ? new MobEffectInstance(had) : new MobEffectInstance(had.getEffect(), (int)(had.getDuration() - elapsed),
							had.getAmplifier(), had.isAmbient(), had.isVisible(), had.showIcon()));
					}
				}
			}
			pendingCooldown(player, live);
		}
		PRIOR.remove(player.getUUID());
		ANCHOR.remove(player.getUUID());
		live.tolls = 0;
		live.sense = false;
		live.burrow = 0;
		live.burrowTo = null;
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_2) {
			live.sense = down;
			if (down) {
				sound(player, SoundEvents.WARDEN_LISTENING, 1.0F, 0.7F);
				cooldown(player, live, G, 1);
			}
			return;
		}
		if (!down || live.burrow > 0) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R)) {
					lance(player);
					cooldown(player, live, R, LANCE_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					shriek(player);
					cooldown(player, live, V, SHRIEK_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B) && player.onGround() && live.tolls <= 0 && burrow(player, live)) {
					cooldown(player, live, B, BURROW_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	/** C: The Tolling. Planted on the ground, seven tolls roll out; tapping it again while they run does nothing. */
	@Override
	public void onBigMove(ServerPlayer player, XenoformPower.Live live) {
		if (live.tolls > 0 || live.burrow > 0 || !ready(player, live, C) || !player.onGround()) {
			return;
		}
		ANCHOR.remove(player.getUUID());
		live.tolls = TOLLS;
		live.lastToll = -1L;
		MobEffectInstance before = player.getEffect(MobEffects.RESISTANCE);
		if (before != null) {
			PRIOR.put(player.getUUID(), new Prior(new MobEffectInstance(before), player.level().getGameTime()));
		} else {
			PRIOR.remove(player.getUUID());
		}
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, TOLLS * TOLL_EVERY + 20, 1, false, false));
		sound(player, SoundEvents.WARDEN_SONIC_CHARGE, 2.0F, 0.6F);
		sound(player, SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.7F);
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (live.sense && player.tickCount % SENSE_EVERY == 0) {
			for (LivingEntity living : around(player, SENSE_RADIUS)) {
				living.addEffect(new MobEffectInstance(MobEffects.GLOWING, SENSE_GLOW_TICKS, 0, false, false));
			}
			if (player.tickCount % (SENSE_EVERY * 2) == 0) {
				sound(player, SoundEvents.WARDEN_TENDRIL_CLICKS, 0.8F, 0.8F);
			}
		}
		if (live.burrow > 0) {
			live.burrow--;
			BlockPos under = player.blockPosition().below();
			BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(under).isAir()
				? net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState() : level.getBlockState(under));
			level.sendParticles(dust, player.getX(), player.getY() + 0.2, player.getZ(), 10, 0.8, 0.2, 0.8, 0.05);
			if (live.burrow == BURROW_TICKS && live.burrowTo != null) {
				// down: gone; up: it comes out of the ground and throws everything near the exit
				Vec3 to = live.burrowTo;
				live.burrowTo = null;
				player.teleportTo(level, to.x, to.y, to.z, Set.of(), player.getYRot(), player.getXRot(), false);
				player.resetFallDistance();
				for (LivingEntity living : around(player, UNDERTOW_RADIUS)) {
					if (living.hurtServer(level, level.damageSources().playerAttack(player), UNDERTOW_DAMAGE)) {
						double resist = 1.0 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
						shove(living, to, 0.4 * resist, UNDERTOW_THROW * resist);
					}
				}
				sound(player, SoundEvents.WARDEN_EMERGE, 1.5F, 0.8F);
				level.sendParticles(dust, to.x, to.y + 0.5, to.z, 60, 1.2, 0.6, 1.2, 0.12);
				ring(level, ParticleTypes.SCULK_CHARGE_POP, to.add(0.0, 0.2, 0.0), UNDERTOW_RADIUS, 20);
				Shakes.heavy(player, to, UNDERTOW_SHAKE_RANGE, UNDERTOW_SHAKE);
			}
		}
		if (live.tolls > 0) {
			tollTick(player, live);
		}
	}

	/** Nothing reaches it while it's under the ground. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return live.burrow <= 0;
	}

	// ---- C: The Tolling ----

	/** One tick of the Tolling: planted, and a toll on every {@link #TOLL_EVERY}th tick of the level's clock. */
	private static void tollTick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		// planted
		// (0.20.21) Slowness holds a walking player (the server never sees their motion); it runs out a few ticks after the tolls
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 5, PLANT_SLOWNESS, false, false));
		// (0.20.21) and a hop cannot carry it away: back to where it planted (height is left alone)
		Vec3 anchor = ANCHOR.computeIfAbsent(player.getUUID(), id -> player.position());
		if (Math.abs(player.getX() - anchor.x) > 0.05 || Math.abs(player.getZ() - anchor.z) > 0.05) {
			player.teleportTo(level, anchor.x, player.getY(), anchor.z, java.util.Set.of(), player.getYRot(), player.getXRot(), false);
		}
		// on the level's clock, which the client's model follows for the fists' slam (an entity's age isn't synced);
		// once per value of it, so a frozen clock (/tick freeze) holds the toll rather than ringing every tick
		long now = level.getGameTime();
		if (now % TOLL_EVERY == 0 && now != live.lastToll) {
			live.lastToll = now;
			live.tolls--;
			for (LivingEntity living : around(player, TOLL_RADIUS)) {
				if (living.hurtServer(level, level.damageSources().sonicBoom(player), TOLL_DAMAGE)) {
					double resist = 1.0 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
					shove(living, player.position(), TOLL_KNOCKBACK * resist, 0.4 * resist);
					if (living instanceof net.minecraft.world.entity.player.Player) {
						living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, TOLL_DARKNESS_TICKS, 0));
					}
				}
			}
			Vec3 at = player.position().add(0.0, 1.2, 0.0);
			ring(level, ParticleTypes.SONIC_BOOM, at, TOLL_RADIUS * 0.35, 8);
			ring(level, ParticleTypes.SONIC_BOOM, at, TOLL_RADIUS * 0.7, 14);
			ring(level, ParticleTypes.SCULK_CHARGE_POP, at, TOLL_RADIUS, 28);
			sound(player, SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 0.5F);
			sound(player, SoundEvents.BELL_BLOCK, 3.0F, 0.4F);
			Shakes.heavy(player, at, TOLL_RADIUS * 2.0, TOLL_SHAKE);
			if (live.tolls <= 0) {
				live.tolls = 0;
				ANCHOR.remove(player.getUUID());
				cooldown(player, live, C, TOLL_COOLDOWN);
			}
		} else if (player.tickCount % 4 == 0) {
			level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 2.0, player.getZ(), 1, 0.5, 0.5, 0.5, 0.0);
		}
	}

	// ---- R: Sonic Lance ----

	private static void lance(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 from = player.getEyePosition().add(0.0, -0.3, 0.0);
		Vec3 dir = player.getLookAngle();
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		for (int i = 1; i <= (int)LANCE_RANGE; i++) {
			Vec3 p = from.add(dir.scale(i));
			level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		for (LivingEntity living : around(player, LANCE_RANGE + 1.0)) {
			Vec3 rel = living.getBoundingBox().getCenter().subtract(from);
			double along = rel.dot(dir);
			if (along < 0.0 || along > LANCE_RANGE) {
				continue;
			}
			double off = rel.subtract(dir.scale(along)).length();
			if (off > LANCE_WIDTH + living.getBbWidth() * 0.5) {
				continue;
			}
			if (living.hurtServer(level, level.damageSources().sonicBoom(player), LANCE_DAMAGE)) {
				double resist = 1.0 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
				living.push(dir.x * LANCE_KNOCKBACK * resist, dir.y * 0.5 * resist + 0.2, dir.z * LANCE_KNOCKBACK * resist);
				living.syncVelocity = true;
			}
		}
		sound(player, SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 0.8F);
		Shakes.heavy(player, from.add(dir.scale(2.0)), LANCE_RANGE, 1.5F);
	}

	// ---- V: Deep Shriek ----

	private static void shriek(ServerPlayer player) {
		ServerLevel level = player.level();
		for (LivingEntity living : around(player, SHRIEK_RADIUS)) {
			if (!living.hurtServer(level, player.damageSources().playerAttack(player), SHRIEK_DAMAGE) && living instanceof net.minecraft.world.entity.player.Player) {
				continue; // (0.20.21) a player the shriek could not hurt (pvp off, blocked) is not blinded or slowed either
			}
			living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, SHRIEK_DARKNESS_TICKS, 0));
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SHRIEK_SLOW_TICKS, 1));
		}
		Vec3 at = player.position().add(0.0, 1.8, 0.0);
		ring(level, ParticleTypes.SCULK_CHARGE_POP, at, SHRIEK_RADIUS * 0.4, 20);
		ring(level, ParticleTypes.SCULK_SOUL, at, SHRIEK_RADIUS * 0.8, 40);
		level.sendParticles(new net.minecraft.core.particles.ShriekParticleOption(0), at.x, at.y + 0.5, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		sound(player, SoundEvents.WARDEN_ROAR, 3.0F, 0.8F);
		sound(player, SoundEvents.SCULK_SHRIEKER_SHRIEK, 2.0F, 0.6F);
		Shakes.heavy(player, at, SHRIEK_RADIUS * 1.5, 1.8F);
	}

	// ---- B: Undertow ----

	/** Picks where you come up: the surface of the block you look at, or the ground {@link #BURROW_RANGE} ahead. */
	private static boolean burrow(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(BURROW_RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		BlockPos column;
		if (hit.getType() == HitResult.Type.BLOCK) {
			column = hit.getBlockPos();
		} else {
			Vec3 far = eye.add(look.scale(BURROW_RANGE));
			column = BlockPos.containing(far.x, far.y, far.z);
		}
		// up from there to standing room (the beast is two blocks tall: room for its head too)
		BlockPos.MutableBlockPos pos = column.mutable();
		boolean found = false;
		for (int i = 0; i < 8 && pos.getY() < level.getMaxY(); i++, pos.move(0, 1, 0)) {
			if (level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty()
				&& !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) {
				found = true;
				break;
			}
		}
		if (!found) {
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ());
			pos.set(column.getX(), y, column.getZ());
		}
		if (pos.distSqr(player.blockPosition()) < 2.0) {
			return false;
		}
		live.burrowTo = Vec3.atBottomCenterOf(pos);
		live.burrow = BURROW_TICKS * 2;
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, BURROW_TICKS * 2 + 2, 9, false, false));
		player.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		sound(player, SoundEvents.WARDEN_DIG, 1.5F, 0.8F);
		return true;
	}
}
