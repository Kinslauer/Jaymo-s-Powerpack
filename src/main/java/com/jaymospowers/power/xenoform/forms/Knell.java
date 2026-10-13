package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import java.util.Set;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Knell, the deep-dark hulk (scanned from a Warden): a third bigger than you, 50 health, fists of 8, slow, nearly
 * immovable.
 * <ul>
 * <li>R, Sonic Boom: a ringing blast straight ahead that goes through walls, hitting everything on its line.</li>
 * <li>G (hold), Tremor Sense: feel everything that moves within {@link #SENSE_RADIUS}; it glows for you.</li>
 * <li>V, Shriek: everything within {@link #SHRIEK_RADIUS} is plunged into darkness and slowed.</li>
 * <li>B, Burrow: sink into the ground and come up where you look, up to {@link #BURROW_RANGE} blocks away.</li>
 * <li>C (ascended), the Tolling: you plant yourself and toll {@link #TOLLS} times, every {@link #TOLL_EVERY} ticks, each
 * toll a ring of sonic force through everything within {@link #TOLL_RADIUS}; nothing moves you meanwhile.</li>
 * </ul>
 * Corrupted: every boom deafens you (darkness), and your shriek sets off every sculk shrieker within
 * {@link #CORRUPT_SHRIEKER_REACH}, wardens and all.
 */
public final class Knell extends XenoForm {
	// ---- tuning ----
	public static final int BOOM_COOLDOWN = 160;
	public static final double BOOM_RANGE = 15.0, BOOM_WIDTH = 1.5, BOOM_KNOCKBACK = 2.5;
	public static final float BOOM_DAMAGE = 10.0F;
	public static final double SENSE_RADIUS = 24.0;
	public static final int SENSE_EVERY = 10, SENSE_GLOW_TICKS = 15;
	public static final int SHRIEK_COOLDOWN = 300, SHRIEK_DARKNESS_TICKS = 120, SHRIEK_SLOW_TICKS = 100;
	public static final double SHRIEK_RADIUS = 12.0;
	public static final float SHRIEK_DAMAGE = 4.0F;
	public static final int BURROW_COOLDOWN = 240, BURROW_TICKS = 20;
	public static final double BURROW_RANGE = 16.0;
	/** The Tolling: how many tolls, how far apart, their reach, bite and push; C's cooldown. */
	public static final int TOLLS = 5, TOLL_EVERY = 40, TOLL_COOLDOWN = 600;
	public static final double TOLL_RADIUS = 8.0, TOLL_KNOCKBACK = 1.4;
	public static final float TOLL_DAMAGE = 6.0F;
	/** Corrupted: the boom's darkness on yourself; how far a shriek wakes shriekers. */
	public static final int CORRUPT_DEAF_TICKS = 60, CORRUPT_SHRIEKER_REACH = 12;

	public Knell() {
		super("knell", 1.3F, 50.0F, 8.0, -0.15, 0.9, 1.0, Items.SCULK_CATALYST, 0x1FB8C8);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof Warden;
	}

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		live.sense = false;
		live.burrow = 0;
		live.burrowTo = null;
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_2) {
			live.sense = down;
			if (down) {
				sound(player, SoundEvents.WARDEN_LISTENING, 1.0F, 1.0F);
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
					boom(player);
					cooldown(player, live, R, BOOM_COOLDOWN);
					if (live.corrupted) {
						player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, CORRUPT_DEAF_TICKS, 0, false, false));
					}
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					shriek(player);
					cooldown(player, live, V, SHRIEK_COOLDOWN);
					if (live.corrupted) {
						wakeShriekers(player);
					}
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B) && player.onGround() && burrow(player, live)) {
					cooldown(player, live, B, BURROW_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (live.sense && player.tickCount % SENSE_EVERY == 0) {
			for (LivingEntity living : around(player, SENSE_RADIUS)) {
				living.addEffect(new MobEffectInstance(MobEffects.GLOWING, SENSE_GLOW_TICKS, 0, false, false));
			}
			if (player.tickCount % (SENSE_EVERY * 2) == 0) {
				sound(player, SoundEvents.WARDEN_TENDRIL_CLICKS, 0.8F, 1.0F);
			}
		}
		if (live.burrow > 0) {
			live.burrow--;
			BlockPos under = player.blockPosition().below();
			BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(under).isAir()
				? net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState() : level.getBlockState(under));
			level.sendParticles(dust, player.getX(), player.getY() + 0.2, player.getZ(), 8, 0.6, 0.2, 0.6, 0.05);
			if (live.burrow == BURROW_TICKS && live.burrowTo != null) {
				// down: gone
				Vec3 to = live.burrowTo;
				live.burrowTo = null;
				player.teleportTo(level, to.x, to.y, to.z, Set.of(), player.getYRot(), player.getXRot(), false);
				player.resetFallDistance();
				sound(player, SoundEvents.WARDEN_EMERGE, 1.5F, 1.0F);
				level.sendParticles(dust, to.x, to.y + 0.5, to.z, 40, 0.8, 0.5, 0.8, 0.1);
			}
		}
	}

	/** Nothing reaches it while it's under the ground. */
	@Override
	public boolean allowDamage(ServerPlayer player, XenoformPower.Live live, DamageSource source, float amount) {
		return live.burrow <= 0;
	}

	// ---- the Tolling ----

	@Override
	public boolean onUltimate(ServerPlayer player, XenoformPower.Live live) {
		return toll(player, live);
	}

	@Override
	public void onUltimateAgain(ServerPlayer player, XenoformPower.Live live) {
		if (live.tolls <= 0 && ultimateReady(player, live)) {
			toll(player, live);
		}
	}

	@Override
	public void tickUltimate(ServerPlayer player, XenoformPower.Live live) {
		if (live.tolls <= 0) {
			return;
		}
		ServerLevel level = player.level();
		// planted
		Vec3 motion = player.getDeltaMovement();
		if (motion.horizontalDistanceSqr() > 1.0E-4) {
			player.setDeltaMovement(0.0, motion.y, 0.0);
			player.syncVelocity = true;
		}
		if (player.tickCount % TOLL_EVERY == 0) {
			live.tolls--;
			for (LivingEntity living : around(player, TOLL_RADIUS)) {
				if (living.hurtServer(level, level.damageSources().sonicBoom(player), TOLL_DAMAGE)) {
					double resist = 1.0 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
					shove(living, player.position(), TOLL_KNOCKBACK * resist, 0.4 * resist);
					if (living instanceof net.minecraft.world.entity.player.Player) {
						living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0));
					}
				}
			}
			Vec3 at = player.position().add(0.0, 1.2, 0.0);
			ring(level, ParticleTypes.SONIC_BOOM, at, TOLL_RADIUS * 0.35, 8);
			ring(level, ParticleTypes.SONIC_BOOM, at, TOLL_RADIUS * 0.7, 14);
			ring(level, ParticleTypes.SCULK_CHARGE_POP, at, TOLL_RADIUS, 24);
			sound(player, SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 0.6F);
			sound(player, SoundEvents.BELL_BLOCK, 3.0F, 0.5F);
			if (live.tolls == 0) {
				ultimateCooldown(player, live, TOLL_COOLDOWN);
			}
		} else if (player.tickCount % 4 == 0) {
			level.sendParticles(ParticleTypes.SCULK_SOUL, player.getX(), player.getY() + 1.5, player.getZ(), 1, 0.4, 0.4, 0.4, 0.0);
		}
	}

	@Override
	public void onUltimateEnd(ServerPlayer player, XenoformPower.Live live) {
		live.tolls = 0;
	}

	private static boolean toll(ServerPlayer player, XenoformPower.Live live) {
		if (!player.onGround()) {
			return false;
		}
		live.tolls = TOLLS;
		player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, TOLLS * TOLL_EVERY, 1, false, false));
		sound(player, SoundEvents.WARDEN_SONIC_CHARGE, 2.0F, 0.7F);
		sound(player, SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.8F);
		return true;
	}

	/** Corrupted: the shriek is heard by the sculk. Every shrieker near answers, and the deep dark keeps its count. */
	private static void wakeShriekers(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos feet = player.blockPosition();
		int r = CORRUPT_SHRIEKER_REACH;
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-r, -r, -r), feet.offset(r, r, r))) {
			if (level.getBlockEntity(pos) instanceof net.minecraft.world.level.block.entity.SculkShriekerBlockEntity shrieker) {
				shrieker.tryShriek(level, player);
			}
		}
	}

	private static void boom(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 from = player.getEyePosition().add(0.0, -0.3, 0.0);
		Vec3 dir = player.getLookAngle();
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		for (int i = 1; i <= (int)BOOM_RANGE; i++) {
			Vec3 p = from.add(dir.scale(i));
			level.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
		}
		for (LivingEntity living : around(player, BOOM_RANGE + 1.0)) {
			Vec3 rel = living.getBoundingBox().getCenter().subtract(from);
			double along = rel.dot(dir);
			if (along < 0.0 || along > BOOM_RANGE) {
				continue;
			}
			double off = rel.subtract(dir.scale(along)).length();
			if (off > BOOM_WIDTH + living.getBbWidth() * 0.5) {
				continue;
			}
			if (living.hurtServer(level, level.damageSources().sonicBoom(player), BOOM_DAMAGE)) {
				double resist = 1.0 - living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
				living.push(dir.x * BOOM_KNOCKBACK * resist, dir.y * 0.5 * resist + 0.2, dir.z * BOOM_KNOCKBACK * resist);
				living.syncVelocity = true;
			}
		}
		sound(player, SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 1.0F);
	}

	private static void shriek(ServerPlayer player) {
		ServerLevel level = player.level();
		for (LivingEntity living : around(player, SHRIEK_RADIUS)) {
			living.hurtServer(level, player.damageSources().playerAttack(player), SHRIEK_DAMAGE);
			living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, SHRIEK_DARKNESS_TICKS, 0));
			living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SHRIEK_SLOW_TICKS, 1));
		}
		Vec3 at = player.position().add(0.0, 1.5, 0.0);
		ring(level, ParticleTypes.SCULK_CHARGE_POP, at, SHRIEK_RADIUS * 0.4, 16);
		ring(level, ParticleTypes.SCULK_SOUL, at, SHRIEK_RADIUS * 0.8, 32);
		level.sendParticles(new net.minecraft.core.particles.ShriekParticleOption(0), at.x, at.y + 0.5, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		sound(player, SoundEvents.WARDEN_ROAR, 3.0F, 1.0F);
		sound(player, SoundEvents.SCULK_SHRIEKER_SHRIEK, 2.0F, 0.8F);
	}

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
		// up from there to standing room
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
		sound(player, SoundEvents.WARDEN_DIG, 1.5F, 1.0F);
		return true;
	}

}
