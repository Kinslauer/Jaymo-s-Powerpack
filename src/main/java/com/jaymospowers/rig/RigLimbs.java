package com.jaymospowers.rig;

import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Prosthetics;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What a Rig prosthetic does ({@link Prosthetics}, Rig limbs built at the Forge in a frame). They need no suit and no
 * charge: the limb carries its own.
 * <ul>
 * <li><b>Rig arm</b> (no power: G, when no suit is worn): a repulsor shot out of the palm,
 * {@link #SHOT_DAMAGE} times the frame's shot damage. A Scrap arm
 * knocks harder and hits weaker; a Shade arm is silent; a Siege arm bursts where it hits (no blocks broken).</li>
 * <li><b>Rig leg</b> (no power: V, when no suit is worn): a thruster hop off the ground, and in the air a kick forward that hurts and throws
 * what it hits. A Scrap leg goes lower, a Shade leg is silent, a Siege leg kicks hardest.</li>
 * </ul>
 */
public final class RigLimbs {
	/** The arm: damage, range (blocks), cooldown (ticks), hunger it costs, and how hard a Scrap arm throws. */
	static final float SHOT_DAMAGE = 4.0F;
	static final double SHOT_RANGE = 18.0, SHOT_KNOCKBACK = 0.6, SCRAP_KNOCKBACK = 1.2;
	static final int SHOT_COOLDOWN = 30;
	static final float SHOT_EXHAUSTION = 0.4F, SIEGE_BURST = 0.8F;
	/** The leg: the hop (up and forward, b/t), the kick (forward, b/t), its damage, reach (blocks), throw, cooldown (ticks), hunger. */
	static final double HOP_UP = 0.85, HOP_FORWARD = 0.45, KICK_FORWARD = 1.0, KICK_REACH = 2.6, KICK_KNOCKBACK = 1.1;
	static final float KICK_DAMAGE = 5.0F, SIEGE_KICK = 1.4F, SCRAP_HOP = 0.8F;
	static final int KICK_COOLDOWN = 40;
	static final float KICK_EXHAUSTION = 0.5F;
	/** The frames’ colours for the shot and the kick’s blast. */
	static final int STANDARD_COLOR = 0x5FE6FF, SCRAP_COLOR = 0xFFB060, SHADE_COLOR = 0x9A78FF, SIEGE_COLOR = 0xFF6A4A;

	/** The game time each player’s arm and leg are next ready. */
	private static final Map<UUID, long[]> READY = new HashMap<>();

	private RigLimbs() {
	}

	static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> READY.remove(handler.player.getUUID()));
	}

	/** The first arm or leg bit (of {@code parts}) with a Rig limb on it, or 0. */
	private static int limb(ServerPlayer player, int... parts) {
		for (int part : parts) {
			if (Prosthetics.hasRig(player, part)) {
				return part;
			}
		}
		return 0;
	}

	private static RigFrame frameOf(ServerPlayer player, int part) {
		return RigFrame.byId(Prosthetics.rigFrame(player, part));
	}

	static int color(RigFrame frame) {
		return switch (frame) {
			case SCRAP -> SCRAP_COLOR;
			case SHADE -> SHADE_COLOR;
			case SIEGE -> SIEGE_COLOR;
			default -> STANDARD_COLOR;
		};
	}

	/** A limb key from the client. */
	static void use(ServerPlayer player, int action) {
		if (player.isSpectator()) {
			return;
		}
		boolean arm = action == RigNet.LIMB_SHOT;
		int part = arm ? limb(player, Dismember.RIGHT_ARM, Dismember.LEFT_ARM) : limb(player, Dismember.RIGHT_LEG, Dismember.LEFT_LEG);
		if (part == 0) {
			return;
		}
		long now = player.level().getGameTime();
		long[] ready = READY.computeIfAbsent(player.getUUID(), u -> new long[2]);
		int slot = arm ? 0 : 1;
		if (ready[slot] > now) {
			return;
		}
		if (player.getFoodData().getFoodLevel() <= 0 && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig.limb_hungry"));
			return;
		}
		RigFrame frame = frameOf(player, part);
		if (arm) {
			ready[slot] = now + SHOT_COOLDOWN;
			shot(player, frame);
		} else {
			ready[slot] = now + KICK_COOLDOWN;
			kick(player, frame);
		}
		if (!player.isCreative()) {
			player.causeFoodExhaustion(arm ? SHOT_EXHAUSTION : KICK_EXHAUSTION);
		}
	}

	private static void shot(ServerPlayer player, RigFrame frame) {
		ServerLevel level = player.level();
		RigMoves.start(player, RigMoves.PALM);
		// the state ticks the move off; make sure there is one
		RigActives.stateOf(player);
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		Vec3 end = eye.add(look.scale(SHOT_RANGE));
		BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		if (block.getType() != HitResult.Type.MISS) {
			end = block.getLocation();
		}
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0),
			e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != player, eye.distanceToSqr(end));
		if (hit != null) {
			end = hit.getLocation();
		}
		Vec3 from = eye.add(look.scale(0.6)).add(0.0, -0.35, 0.0);
		int color = color(frame);
		MagicFx.beam(level, from, end, frame == RigFrame.SIEGE ? 0.14F : 0.07F, color, 5);
		MagicFx.blast(level, end, 0.45F, color);
		if (frame != RigFrame.SHADE) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F,
				frame == RigFrame.SCRAP ? 1.4F : 1.9F);
		}
		if (hit != null && hit.getEntity() instanceof LivingEntity target) {
			DamageSource source = level.damageSources().playerAttack(player);
			float damage = SHOT_DAMAGE * frame.shotDamage;
			target.hurtServer(level, source, damage);
			target.knockback(frame == RigFrame.SCRAP ? SCRAP_KNOCKBACK : SHOT_KNOCKBACK, -look.x, -look.z, source, damage);
			target.syncVelocity = true;
		}
		if (frame == RigFrame.SIEGE) {
			level.explode(player, end.x, end.y, end.z, SIEGE_BURST, Level.ExplosionInteraction.NONE);
		}
	}

	private static void kick(ServerPlayer player, RigFrame frame) {
		ServerLevel level = player.level();
		RigMoves.start(player, RigMoves.KICK);
		RigActives.stateOf(player);
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
		int color = color(frame);
		Vec3 feet = player.position();
		if (player.onGround()) {
			// the hop: up off the ground on the foot’s thruster
			double up = HOP_UP * (frame == RigFrame.SCRAP ? SCRAP_HOP : 1.0);
			Vec3 push = flat.scale(HOP_FORWARD);
			player.setDeltaMovement(push.x, up, push.z);
			player.syncVelocity = true;
			MagicFx.ring(level, feet.add(0.0, 0.1, 0.0), 1.6F, color);
			MagicFx.blast(level, feet.add(0.0, 0.2, 0.0), 0.7F, color);
		} else {
			// the kick: a lunge forward that hurts and throws what it meets
			player.setDeltaMovement(flat.x * KICK_FORWARD, Math.max(player.getDeltaMovement().y, 0.1), flat.z * KICK_FORWARD);
			player.syncVelocity = true;
			Vec3 front = feet.add(flat.scale(KICK_REACH * 0.5)).add(0.0, 0.8, 0.0);
			MagicFx.blast(level, front, 0.9F, color);
			float damage = KICK_DAMAGE * (frame == RigFrame.SIEGE ? SIEGE_KICK : 1.0F) * frame.shotDamage;
			DamageSource source = level.damageSources().playerAttack(player);
			for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(KICK_REACH * 0.5).move(flat.scale(KICK_REACH * 0.5)),
				e -> e != player && e.isAlive() && !e.isSpectator())) {
				near.hurtServer(level, source, damage);
				near.knockback(KICK_KNOCKBACK, -flat.x, -flat.z, source, damage);
				near.syncVelocity = true;
			}
		}
		if (frame != RigFrame.SHADE) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.6F, 0.9F);
		}
	}
}
