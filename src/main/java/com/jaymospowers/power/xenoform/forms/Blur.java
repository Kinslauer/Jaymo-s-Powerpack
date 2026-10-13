package com.jaymospowers.power.xenoform.forms;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.power.xenoform.XenoForm;
import com.jaymospowers.power.xenoform.XenoformPower;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * Blur, the raptor sprinter (scanned from a horse): a little smaller than you, 20 health, 60% faster, lands softly.
 * <ul>
 * <li>R, Dash: a burst forward.</li>
 * <li>G (hold), Wall Run: against a wall, keep running straight up it for up to {@link #WALL_RUN_TICKS}.</li>
 * <li>V, Whirlwind: spin, pulling everything within {@link #SPIN_RADIUS} in and raking it.</li>
 * <li>B, Chain Strike: flicker between up to {@link #CHAIN_TARGETS} foes within {@link #CHAIN_RANGE}, striking each.</li>
 * </ul>
 */
public final class Blur extends XenoForm {
	// ---- tuning ----
	public static final int DASH_COOLDOWN = 40, DASH_TICKS = 8;
	public static final double DASH_SPEED = 1.5;
	public static final int WALL_RUN_TICKS = 40;
	public static final double WALL_RUN_CLIMB = 0.32;
	public static final int SPIN_COOLDOWN = 160;
	public static final double SPIN_RADIUS = 5.0, SPIN_PULL = 0.8;
	public static final float SPIN_DAMAGE = 4.0F;
	public static final int CHAIN_COOLDOWN = 240, CHAIN_TARGETS = 5, CHAIN_SPACING = 3;
	public static final double CHAIN_RANGE = 8.0;
	public static final float CHAIN_DAMAGE = 5.0F;

	public Blur() {
		super("blur", 0.9F, 20.0F, 4.0, 0.6, 0.0, 0.0, Items.FEATHER, 0x7FD0FF);
	}

	@Override
	public boolean scans(Entity creature) {
		return creature instanceof AbstractHorse;
	}

	@Override
	public void onEnter(ServerPlayer player, XenoformPower.Live live) {
		player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SAFE_FALL_DISTANCE)
			.addOrUpdateTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(SAFE_FALL_ID, 4.0,
				net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
	}

	private static final net.minecraft.resources.Identifier SAFE_FALL_ID = com.jaymospowers.JaymosPowers.id("xenoform_blur_fall");

	@Override
	public void onExit(ServerPlayer player, XenoformPower.Live live) {
		var fall = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.SAFE_FALL_DISTANCE);
		if (fall != null) {
			fall.removeModifier(SAFE_FALL_ID);
		}
	}

	@Override
	public void onKey(ServerPlayer player, XenoformPower.Live live, PowerKey key, boolean down) {
		if (key == PowerKey.ABILITY_2) {
			// the wall run: held
			if (down) {
				live.wallRun = 0;
			} else {
				live.wallRun = WALL_RUN_TICKS + 1;
			}
			return;
		}
		if (!down) {
			return;
		}
		switch (key) {
			case ABILITY_1 -> {
				if (ready(player, live, R)) {
					Vec3 look = player.getLookAngle().horizontal();
					look = look.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
					player.setDeltaMovement(look.scale(DASH_SPEED).add(0.0, 0.15, 0.0));
					player.syncVelocity = true;
					live.dashTicks = DASH_TICKS;
					burst(player.level(), ParticleTypes.CLOUD, player.position().add(0.0, 0.3, 0.0), 12, 0.4, 0.05);
					sound(player, SoundEvents.HORSE_GALLOP, 1.0F, 1.5F);
					cooldown(player, live, R, DASH_COOLDOWN);
				}
			}
			case ABILITY_3 -> {
				if (ready(player, live, V)) {
					spin(player);
					cooldown(player, live, V, SPIN_COOLDOWN);
				}
			}
			case ABILITY_4 -> {
				if (ready(player, live, B) && chain(player, live)) {
					cooldown(player, live, B, CHAIN_COOLDOWN);
				}
			}
			default -> {
			}
		}
	}

	@Override
	public void tick(ServerPlayer player, XenoformPower.Live live) {
		ServerLevel level = player.level();
		if (live.dashTicks > 0) {
			live.dashTicks--;
			burst(level, ParticleTypes.CLOUD, player.position().add(0.0, 0.3, 0.0), 2, 0.2, 0.01);
		}
		// the wall run: G held, against a wall, going somewhere
		boolean running = com.jaymospowers.core.PowerInput.isDown(player, PowerKey.ABILITY_2) && live.wallRun < WALL_RUN_TICKS
			&& player.horizontalCollision && !player.onGround() && !player.isInWater();
		if (running) {
			live.wallRun++;
			Vec3 motion = player.getDeltaMovement();
			player.setDeltaMovement(motion.x, WALL_RUN_CLIMB, motion.z);
			player.syncVelocity = true;
			player.resetFallDistance();
			if (live.wallRun % 4 == 0) {
				burst(level, ParticleTypes.CLOUD, player.position(), 2, 0.2, 0.01);
				sound(player, SoundEvents.HORSE_GALLOP, 0.5F, 1.8F);
			}
		} else if (player.onGround()) {
			live.wallRun = 0;
		}
		// the chain strike, one flicker at a time
		if (!live.chain.isEmpty() && ++live.chainTick >= CHAIN_SPACING) {
			live.chainTick = 0;
			Entity next = level.getEntity(live.chain.remove(0));
			if (next instanceof LivingEntity living && living.isAlive() && living.distanceToSqr(player) <= CHAIN_RANGE * CHAIN_RANGE * 2.0) {
				strike(player, living);
			}
		}
	}

	@Override
	public boolean allowsFloating(ServerPlayer player, XenoformPower.Live live) {
		return live.wallRun > 0 && live.wallRun <= WALL_RUN_TICKS || !live.chain.isEmpty();
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player, XenoformPower.Live live) {
		return live.dashTicks > 0 || !live.chain.isEmpty() || live.chainTick > 0;
	}

	private static void spin(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 centre = player.position();
		for (LivingEntity living : around(player, SPIN_RADIUS)) {
			Vec3 pull = centre.subtract(living.position());
			double d = pull.length();
			if (d > 1.5) {
				living.setDeltaMovement(living.getDeltaMovement().add(pull.scale(SPIN_PULL / d)).add(0.0, 0.2, 0.0));
				living.syncVelocity = true;
			}
			living.hurtServer(level, player.damageSources().playerAttack(player), SPIN_DAMAGE);
		}
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		ring(level, ParticleTypes.SWEEP_ATTACK, centre.add(0.0, 1.0, 0.0), 1.5, 8);
		ring(level, ParticleTypes.CLOUD, centre, SPIN_RADIUS, 24);
		sound(player, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.2F);
		sound(player, SoundEvents.WIND_CHARGE_BURST.value(), 0.8F, 1.5F);
	}

	private static boolean chain(ServerPlayer player, XenoformPower.Live live) {
		List<LivingEntity> targets = around(player, CHAIN_RANGE);
		targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		live.chain.clear();
		for (LivingEntity target : targets) {
			if (live.chain.size() >= CHAIN_TARGETS) {
				break;
			}
			live.chain.add(target.getId());
		}
		if (live.chain.isEmpty()) {
			return false;
		}
		live.chainTick = CHAIN_SPACING;
		sound(player, SoundEvents.HORSE_GALLOP, 1.0F, 2.0F);
		return true;
	}

	/** One flicker: beside (behind) the target, and a blow. */
	private static void strike(ServerPlayer player, LivingEntity target) {
		ServerLevel level = player.level();
		Vec3 behind = target.position().subtract(target.getLookAngle().horizontal().normalize().scale(1.2));
		Vec3 from = player.position();
		burst(level, ParticleTypes.CLOUD, from.add(0.0, 0.5, 0.0), 6, 0.3, 0.02);
		float yaw = (float)(Math.toDegrees(Math.atan2(-(target.getX() - behind.x), target.getZ() - behind.z)));
		player.teleportTo(level, behind.x, behind.y, behind.z, Set.of(), yaw, player.getXRot(), false);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		target.hurtServer(level, player.damageSources().playerAttack(player), CHAIN_DAMAGE);
		target.hurtTime = 0;
		burst(level, ParticleTypes.CRIT, target.getBoundingBox().getCenter(), 8, 0.3, 0.2);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.4F);
	}
}
