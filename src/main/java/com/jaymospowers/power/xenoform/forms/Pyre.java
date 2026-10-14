package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Flames;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.xenoform.XenoformState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

/**
 * Pyre, the living magma (scanned from a blaze): your size, 24 health, burning fists. Fire and lava can't hurt it; water
 * and rain do.
 * <ul>
 * <li>R, Fire Bolt: a small fireball where you look.</li>
 * <li>G (hold), Flame Jet: jets of fire from your feet carry you where you look, for free; let go and you drift down.</li>
 * <li>V, Fire Nova: everything within {@link #NOVA_RADIUS} is burnt and set alight.</li>
 * <li>B, Magma Trail: for {@link #TRAIL_TICKS} the ground burns where you step.</li>
 * <li>C (ascended), Sunheart: the jet goes faster ({@link #FLIGHT_SPEED}), and R becomes a held beam of fire
 * {@link #BEAM_RANGE} blocks long that burns whatever it touches.</li>
 * </ul>
 * Corrupted: your fire spreads. The nova and the trail light the ground around you, and nothing of yours puts it out.
 * Water bites twice as hard.
 */
public final class Pyre extends XenoForm {
	// ---- tuning ----
	public static final int BOLT_COOLDOWN = 20;
	public static final double BOLT_SPEED = 1.6;
	/** The jet: this fast (blocks/tick), easing toward where you look; slow fall for this long after letting go. Free. */
	public static final double JET_SPEED = 0.55, JET_EASE = 0.2;
	public static final int JET_SLOW_FALL_TICKS = 50;
	public static final int NOVA_COOLDOWN = 200;
	public static final double NOVA_RADIUS = 5.0;
	/** Screen shake from the Fire Nova (degrees), and how far it's felt. */
	public static final float NOVA_SHAKE = 0.9F;
	public static final double NOVA_SHAKE_RANGE = 14.0;
	public static final float NOVA_DAMAGE = 6.0F;
	public static final int NOVA_BURN_SECONDS = 5;
	public static final int TRAIL_COOLDOWN = 300, TRAIL_TICKS = 120;
	/** In water or rain: this much damage every this many ticks. */
	public static final float WET_DAMAGE = 1.0F;
	public static final int WET_EVERY = 20;
	/** Sunheart: flight speed (blocks/tick) and easing; the beam's reach, bite (every {@link #BEAM_EVERY} ticks) and burn. */
	public static final double FLIGHT_SPEED = 0.8, FLIGHT_EASE = 0.25;
	public static final double BEAM_RANGE = 12.0;
	public static final float BEAM_DAMAGE = 2.0F;
	public static final int BEAM_EVERY = 4, BEAM_BURN_SECONDS = 3;
	/** Corrupted: the share of ground round a nova set alight; the water bite's multiplier. */
	public static final float CORRUPT_SPREAD = 0.3F, CORRUPT_WET = 2.0F;

	public Pyre() {
		super("pyre", 1.0F, 24.0F, 4.0, 0.0, 0.0, 0.0, Items.MAGMA_BLOCK, 0xFF8A2A);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Blaze;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.jet = false;
		player.removeEffect(MobEffects.LEVITATION);
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_1 && live.ultimate) {
			// Sunheart: the bolt is a beam, held
			if (down != live.beam) {
				live.beam = down;
				if (down) {
					sound(player, SoundEvents.BLAZE_SHOOT, 1.0F, 0.6F);
					cooldown(player, live, R, 1);
				}
			}
			return;
		}
		if (key == PowerKey.ABILITY_2) {
			if (down != live.jet) {
				live.jet = down;
				if (!down) {
					player.removeEffect(MobEffects.LEVITATION);
					if (!player.onGround()) {
						player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, JET_SLOW_FALL_TICKS, 0, false, false));
					}
				} else {
					cooldown(player, live, G, 1);
				}
			}
			return;
		}
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R)) {
					bolt(player);
					cooldown(player, live, R, BOLT_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					nova(player);
					if (live.corrupted) {
						spread(player);
					}
					cooldown(player, live, V, NOVA_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B)) {
					live.trailTicks = TRAIL_TICKS;
					sound(player, SoundEvents.BLAZE_BURN, 1.0F, 0.7F);
					cooldown(player, live, B, TRAIL_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		// the jet
		if (live.jet) {
			// flight where you look, free; Sunheart's is faster
			boolean sun = live.ultimate;
			Vec3 want = player.getLookAngle().scale(sun ? FLIGHT_SPEED : JET_SPEED);
			player.setDeltaMovement(player.getDeltaMovement().lerp(want, sun ? FLIGHT_EASE : JET_EASE));
			player.syncVelocity = true;
			player.resetFallDistance();
			Vec3 at = player.position();
			level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.2, at.z, sun ? 8 : 6, 0.3, 0.1, 0.3, sun ? 0.06 : 0.04);
			level.sendParticles(ParticleTypes.SMALL_FLAME, at.x, at.y + 0.1, at.z, 4, 0.2, 0.1, 0.2, 0.08);
			if (player.tickCount % 8 == 0) {
				sound(player, SoundEvents.FIRE_AMBIENT, 0.8F, sun ? 1.0F : 1.3F);
			}
		}
		// the trail
		if (live.trailTicks > 0) {
			live.trailTicks--;
			if (player.onGround() && player.tickCount % 2 == 0) {
				BlockPos at = player.blockPosition();
				if (level.getBlockState(at).isAir() && BaseFireBlock.canBePlacedAt(level, at, Direction.NORTH)) {
					level.setBlock(at, BaseFireBlock.getState(level, at), Block.UPDATE_ALL);
				}
				if (live.corrupted) {
					light(level, at.offset(player.getRandom().nextInt(3) - 1, 0, player.getRandom().nextInt(3) - 1));
				}
				level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 0.1, player.getZ(), 1, 0.2, 0.0, 0.2, 0.0);
			}
		}
		// always smouldering; wet, it hisses and hurts
		if (player.tickCount % 3 == 0) {
			level.sendParticles(ParticleTypes.SMALL_FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0.3, 0.5, 0.3, 0.01);
		}
		if (player.isInWaterOrRain() && !player.isCreative()) {
			if (player.tickCount % 5 == 0) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.3, 0.4, 0.3, 0.02);
			}
			if (player.tickCount % WET_EVERY == 0) {
				player.hurtServer(level, player.damageSources().drown(), live.corrupted ? WET_DAMAGE * CORRUPT_WET : WET_DAMAGE);
				sound(player, SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.2F);
			}
		}
	}

	/** Fire and lava roll off it. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		if (source.is(DamageTypeTags.IS_FIRE)) {
			player.clearFire();
			return false;
		}
		return true;
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return live.jet || player.hasEffect(MobEffects.SLOW_FALLING);
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return live.jet;
	}

	@Override
	public int flight(ServerPlayer player, XenoformPower.Live live) {
		return live.jet && !player.onGround() ? XenoformState.FLY_JET : XenoformState.FLY_NONE;
	}

	// ---- Sunheart ----

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, 1.0, 0.0);
		ring(level, ParticleTypes.FLAME, at, 2.0, 24);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 30, 0.6, 0.6, 0.6, 0.0);
		sound(player, SoundEvents.BLAZE_AMBIENT, 2.0F, 0.6F);
		sound(player, SoundEvents.FIRECHARGE_USE, 1.0F, 0.8F);
		com.jaymospowers.core.Shakes.heavy(player, at, 16.0, 1.0F);
		return true;
	}

	@Override
	public void tickUltimate(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		// the sun inside: it glows
		if (player.tickCount % 2 == 0) {
			level.sendParticles(ParticleTypes.SMALL_FLAME, player.getX(), player.getY() + 1.0, player.getZ(), 2, 0.4, 0.6, 0.4, 0.02);
		}
		if (live.beam) {
			beam(player, live);
		}
	}

	@Override
	public void onUltimateEnd(ServerPlayer player, XenoformPower.Live live) {
		live.beam = false;
	}

	/** The beam, every tick it's held: a line of fire from your hands to the first thing it meets. */
	private static void beam(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition().add(0.0, -0.3, 0.0);
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(BEAM_RANGE));
		var blockHit = level.clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
			net.minecraft.world.level.ClipContext.Fluid.NONE, player));
		if (blockHit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
			end = blockHit.getLocation();
		}
		var entityHit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level, player, eye, end,
			player.getBoundingBox().expandTowards(look.scale(BEAM_RANGE)).inflate(1.0),
			e -> e instanceof LivingEntity living && living.isAlive() && e != player && !e.isSpectator(), 0.5F);
		if (entityHit != null) {
			end = entityHit.getLocation();
		}
		double length = end.distanceTo(eye);
		for (double d = 0.5; d < length; d += 0.5) {
			Vec3 p = eye.add(look.scale(d));
			level.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
		}
		if (player.tickCount % BEAM_EVERY == 0) {
			if (entityHit != null && entityHit.getEntity() instanceof LivingEntity target) {
				target.hurtServer(level, player.damageSources().inFire(), BEAM_DAMAGE);
				target.hurtTime = 0;
				target.igniteForSeconds(BEAM_BURN_SECONDS);
			} else if (blockHit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
				light(level, blockHit.getBlockPos().relative(blockHit.getDirection()));
			}
			player.causeFoodExhaustion(0.02F);
		}
		if (player.tickCount % 6 == 0) {
			sound(player, SoundEvents.BLAZE_BURN, 0.6F, 1.5F);
		}
	}

	/** Sets {@code pos} alight if fire can stand there. */
	private static void light(ServerLevel level, BlockPos pos) {
		if (level.getBlockState(pos).isAir() && BaseFireBlock.canBePlacedAt(level, pos, Direction.NORTH)) {
			level.setBlock(pos, BaseFireBlock.getState(level, pos), Block.UPDATE_ALL);
		}
	}

	/** Corrupted: the nova leaves the ground round you burning. */
	private static void spread(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos feet = player.blockPosition();
		int r = (int)NOVA_RADIUS;
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-r, -1, -r), feet.offset(r, 1, r))) {
			if (player.getRandom().nextFloat() < CORRUPT_SPREAD && pos.distSqr(feet) <= r * r) {
				light(level, pos.immutable());
			}
		}
	}

	private static void bolt(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle();
		SmallFireball bolt = new SmallFireball(level, player, look.scale(BOLT_SPEED));
		Vec3 from = player.getEyePosition().add(look.scale(0.8));
		bolt.setPos(from.x, from.y - 0.2, from.z);
		level.addFreshEntity(bolt);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		sound(player, SoundEvents.BLAZE_SHOOT, 1.0F, 1.2F);
	}

	private static void nova(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, 0.5, 0.0);
		for (LivingEntity living : around(player, NOVA_RADIUS)) {
			living.hurtServer(level, player.damageSources().playerAttack(player), NOVA_DAMAGE);
			shove(living, player.position(), 0.6, 0.3);
		}
		Flames.area(level, at, NOVA_RADIUS, NOVA_BURN_SECONDS, player, e -> e != player);
		ring(level, ParticleTypes.FLAME, at, NOVA_RADIUS * 0.5, 24);
		ring(level, ParticleTypes.FLAME, at, NOVA_RADIUS, 40);
		level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.5, at.z, 20, 1.0, 0.5, 1.0, 0.0);
		sound(player, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 1.4F);
		sound(player, SoundEvents.BLAZE_SHOOT, 1.0F, 0.6F);
		com.jaymospowers.core.Shakes.heavy(player, at, NOVA_SHAKE_RANGE, NOVA_SHAKE);
	}
}
