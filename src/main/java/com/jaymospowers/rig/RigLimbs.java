package com.jaymospowers.rig;

import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Prosthetics;
import com.jaymospowers.core.HitStop;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
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
 * <li><b>Rig leg</b> (no power: V, when no suit is worn): Andy: "the rig kick on V should b a powerful kick animated like a
 * taekwondo side kick with a chamber turn and flick, with powerful breaking capability and a low cooldown. using it in a
 * combo does more damage animates a glow and steam and spins the camera around as you do a roundhouse kick". V is the
 * side kick ({@link #sideKick}); a melee blow landed just before makes it the spinning roundhouse ({@link #roundhouse});
 * Sneak + V on the ground is the old thruster hop. A Scrap leg hops lower, a Shade leg is silent, a Siege leg kicks hardest.</li>
 * </ul>
 */
public final class RigLimbs {
	/** The arm: damage, range (blocks), cooldown (ticks), hunger it costs, and how hard a Scrap arm throws. */
	static final float SHOT_DAMAGE = 4.0F;
	static final double SHOT_RANGE = 18.0, SHOT_KNOCKBACK = 0.6, SCRAP_KNOCKBACK = 1.2;
	static final int SHOT_COOLDOWN = 30;
	static final float SHOT_EXHAUSTION = 0.4F, SIEGE_BURST = 0.8F;
	/** Where the shot leaves the palm: out to the fitted arm's side, ahead and below the eye (blocks). */
	static final double PALM_OUT = 0.35, PALM_AHEAD = 0.6, PALM_DOWN = 0.35;
	/** The leg's hop (Sneak + V: up and forward, b/t). */
	static final double HOP_UP = 0.85, HOP_FORWARD = 0.45;
	static final int HOP_COOLDOWN = 40;
	/**
	 * The side kick: the flick's step on the ground and lunge in the air (b/t), its reach (blocks), how wide in front it
	 * catches (the cosine off the look), the hip's height, the throw and the lift; its damage, hardest block it breaks and
	 * hit-stop (ticks); cooldown (ticks), hunger.
	 */
	static final double KICK_STEP = 0.35, KICK_FORWARD = 1.0, KICK_REACH = 3.0, KICK_CONE = 0.55, KICK_HEIGHT = 0.9, KICK_KNOCKBACK = 1.8,
		KICK_LIFT = 0.25;
	static final float KICK_DAMAGE = 9.0F, SIEGE_KICK = 1.4F, SCRAP_HOP = 0.8F, KICK_BREAK = 3.0F;
	static final int KICK_HOLD = 2, KICK_COOLDOWN = 12;
	static final float KICK_EXHAUSTION = 0.4F;
	/**
	 * The combo roundhouse: a melee blow landed this recently (ticks) makes V the roundhouse; its damage (times the side
	 * kick's), reach all round (blocks), throw, hardest block it breaks, and hit-stop (ticks).
	 */
	static final int COMBO_WINDOW = 30, ROUND_HOLD = 4, ROUND_COOLDOWN = 18;
	static final float ROUND_DAMAGE = 1.8F, ROUND_BREAK = 5.0F;
	static final double ROUND_REACH = 3.2, ROUND_KNOCKBACK = 2.4;
	/** The frames’ colours for the shot and the kick’s blast. */
	static final int STANDARD_COLOR = 0x5FE6FF, SCRAP_COLOR = 0xFFB060, SHADE_COLOR = 0x9A78FF, SIEGE_COLOR = 0xFF6A4A;

	/** The game time each player’s arm and leg are next ready. */
	private static final Map<UUID, long[]> READY = new HashMap<>();
	/** The game time each player last landed a melee blow (the roundhouse's combo). */
	private static final Map<UUID, Long> COMBO = new HashMap<>();

	private RigLimbs() {
	}

	static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			READY.remove(handler.player.getUUID());
			COMBO.remove(handler.player.getUUID());
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(RigLimbs::afterDamage);
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
			shot(player, frame, part);
		} else {
			ready[slot] = now + kick(player, frame);
		}
		if (!player.isCreative()) {
			player.causeFoodExhaustion(arm ? SHOT_EXHAUSTION : KICK_EXHAUSTION);
		}
	}

	private static void shot(ServerPlayer player, RigFrame frame, int part) {
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
		// out of the palm of the arm the Rig limb is fitted to
		double yaw = Math.toRadians(player.getYRot());
		Vec3 right = new Vec3(-Math.cos(yaw), 0.0, -Math.sin(yaw));
		double side = part == Dismember.LEFT_ARM ? -1.0 : 1.0;
		Vec3 from = eye.add(look.scale(PALM_AHEAD)).add(right.scale(side * PALM_OUT)).add(0.0, -PALM_DOWN, 0.0);
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

	/**
	 * The V key with a Rig leg: Sneak on the ground hops, a melee blow landed just before spins the roundhouse, else the
	 * side kick. Returns the leg's cooldown (ticks).
	 */
	private static int kick(ServerPlayer player, RigFrame frame) {
		if (player.onGround() && player.isShiftKeyDown()) {
			hop(player, frame);
			return HOP_COOLDOWN;
		}
		Long last = COMBO.remove(player.getUUID());
		if (last != null && player.level().getGameTime() - last <= COMBO_WINDOW) {
			roundhouse(player, frame);
			return ROUND_COOLDOWN;
		}
		sideKick(player, frame);
		return KICK_COOLDOWN;
	}

	/** The hop: up off the ground on the foot's thruster (Sneak + V). */
	private static void hop(ServerPlayer player, RigFrame frame) {
		ServerLevel level = player.level();
		Vec3 flat = flatLook(player);
		Vec3 feet = player.position();
		int color = color(frame);
		double up = HOP_UP * (frame == RigFrame.SCRAP ? SCRAP_HOP : 1.0);
		Vec3 push = flat.scale(HOP_FORWARD);
		player.setDeltaMovement(push.x, up, push.z);
		player.syncVelocity = true;
		MagicFx.ring(level, feet.add(0.0, 0.1, 0.0), 1.6F, color);
		MagicFx.blast(level, feet.add(0.0, 0.2, 0.0), 0.7F, color);
		if (frame != RigFrame.SHADE) {
			level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.6F, 0.9F);
		}
	}

	/**
	 * The side kick: the knee chambered up, the hips turned over, and the heel flicked out along the look
	 * ({@link RigMoves#KICK_FIRE} ticks in), the foot's thruster firing behind it. It hurts and throws what it meets and
	 * breaks the soft blocks it lands on.
	 */
	private static void sideKick(ServerPlayer player, RigFrame frame) {
		RigMoves.start(player, RigMoves.KICK);
		RigActives.stateOf(player);
		RigActives.later(player, RigMoves.KICK_FIRE, () -> {
			ServerLevel level = player.level();
			Vec3 flat = flatLook(player);
			Vec3 look = player.getLookAngle();
			int color = color(frame);
			// the flick carries the body after the heel: a step on the ground, a lunge in the air
			double forward = player.onGround() ? KICK_STEP : KICK_FORWARD;
			player.setDeltaMovement(flat.x * forward, Math.max(player.getDeltaMovement().y, player.onGround() ? 0.0 : 0.1), flat.z * forward);
			player.syncVelocity = true;
			Vec3 hip = player.position().add(0.0, KICK_HEIGHT, 0.0);
			float damage = KICK_DAMAGE * (frame == RigFrame.SIEGE ? SIEGE_KICK : 1.0F) * frame.shotDamage;
			boolean hit = false;
			DamageSource source = level.damageSources().playerAttack(player);
			for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(KICK_REACH),
				e -> e != player && e.isAlive() && !e.isSpectator() && inFront(hip, look, e, KICK_REACH))) {
				near.hurtServer(level, source, damage);
				near.knockback(KICK_KNOCKBACK, -flat.x, -flat.z, source, damage);
				near.push(0.0, KICK_LIFT, 0.0);
				near.syncVelocity = true;
				HitStop.hit(player, near, KICK_HOLD);
				hit = true;
			}
			Vec3 foot = hip.add(look.scale(KICK_REACH * 0.8));
			boolean broke = breakAhead(player, KICK_REACH + 0.6, KICK_BREAK, false);
			MagicFx.blast(level, foot, 0.7F, color);
			MagicFx.ring(level, hip.subtract(flat.scale(0.3)), flat, 0.5F, color, 6);
			if (hit || broke) {
				Shakes.send(player, hit ? 0.5F : 0.35F, foot);
			}
			if (frame != RigFrame.SHADE) {
				level.playSound(null, hip.x, hip.y, hip.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 0.5F, 1.3F);
				if (hit) {
					level.playSound(null, foot.x, foot.y, foot.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.0F, 0.7F);
				}
			}
		});
	}

	/**
	 * In a combo (a melee blow landed within {@link #COMBO_WINDOW} ticks), V spins the roundhouse instead: the leg's vents
	 * glow and steam as the knee comes up, the body turns all the way round and the shin whips out at the end of it,
	 * {@link #ROUND_DAMAGE} times the side kick's, at everything round the wearer. It breaks harder blocks too.
	 */
	private static void roundhouse(ServerPlayer player, RigFrame frame) {
		RigMoves.start(player, RigMoves.ROUNDHOUSE);
		RigActives.stateOf(player);
		int color = color(frame);
		int fire = RigMoves.ROUNDHOUSE_FIRE;
		// the turn, a tick at a time: the glowing heel's trail round the wearer and steam off the leg
		for (int i = 1; i <= fire + 2; i++) {
			float share = Math.min(1.0F, (float)i / fire);
			RigActives.later(player, i, () -> roundhouseTrail(player, color, share));
		}
		if (frame != RigFrame.SHADE) {
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.4F);
		}
		RigActives.later(player, fire, () -> {
			ServerLevel level = player.level();
			Vec3 flat = flatLook(player);
			Vec3 hip = player.position().add(0.0, KICK_HEIGHT, 0.0);
			float damage = KICK_DAMAGE * ROUND_DAMAGE * (frame == RigFrame.SIEGE ? SIEGE_KICK : 1.0F) * frame.shotDamage;
			DamageSource source = level.damageSources().playerAttack(player);
			boolean hit = false;
			for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(ROUND_REACH),
				e -> e != player && e.isAlive() && !e.isSpectator() && e.distanceTo(player) <= ROUND_REACH + e.getBbWidth() * 0.5)) {
				Vec3 away = near.position().subtract(player.position()).horizontal();
				away = away.lengthSqr() < 1.0E-4 ? flat : away.normalize();
				near.hurtServer(level, source, damage);
				near.knockback(ROUND_KNOCKBACK, -away.x, -away.z, source, damage);
				near.push(0.0, KICK_LIFT * 1.5, 0.0);
				near.syncVelocity = true;
				HitStop.hit(player, near, ROUND_HOLD);
				hit = true;
			}
			boolean broke = breakAhead(player, ROUND_REACH + 0.6, ROUND_BREAK, true);
			Vec3 side = new Vec3(-flat.z, 0.0, flat.x);
			MagicFx.swing(level, hip, flat, side, ROUND_REACH * 0.8, 360.0, 0.35F, color, 8);
			MagicFx.blast(level, hip.add(flat.scale(ROUND_REACH * 0.8)), 1.0F, color);
			level.sendParticles(ParticleTypes.CLOUD, hip.x, hip.y - 0.4, hip.z, 14, 0.6, 0.2, 0.6, 0.05);
			Shakes.send(player, hit || broke ? 0.8F : 0.4F, hip.add(flat));
			if (frame != RigFrame.SHADE) {
				level.playSound(null, hip.x, hip.y, hip.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 0.8F, 0.8F);
				if (hit) {
					level.playSound(null, hip.x, hip.y, hip.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.6F);
				}
			}
		});
	}

	/** One tick of the roundhouse's turn ({@code share} of the way round): the glowing heel's trail, and steam off the leg. */
	private static void roundhouseTrail(ServerPlayer player, int color, float share) {
		ServerLevel level = player.level();
		Vec3 flat = flatLook(player);
		// the heel comes round from behind the kicking side to the front: a whole turn
		double angle = Math.atan2(flat.z, flat.x) + Math.PI * 2.0 * (share - 1.0);
		double out = ROUND_REACH * 0.7 * Math.min(1.0, share * 1.6);
		Vec3 hip = player.position().add(0.0, KICK_HEIGHT, 0.0);
		Vec3 heel = hip.add(Math.cos(angle) * out, 0.0, Math.sin(angle) * out);
		level.sendParticles(MagicFx.mote(color, 1.4F), heel.x, heel.y, heel.z, 3, 0.08, 0.08, 0.08, 0.0);
		level.sendParticles(MagicFx.spark(0xFFFFFF, 0.7F), heel.x, heel.y, heel.z, 1, 0.05, 0.05, 0.05, 0.0);
		Vec3 knee = hip.add(Math.cos(angle) * out * 0.45, -0.2, Math.sin(angle) * out * 0.45);
		level.sendParticles(ParticleTypes.WHITE_SMOKE, knee.x, knee.y, knee.z, 2, 0.1, 0.1, 0.1, 0.02);
	}

	/** The look along the ground (unit), or zero looking straight up or down. */
	private static Vec3 flatLook(ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		return flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
	}

	/** Whether {@code e} is within {@code reach} of {@code from} and in front along {@code look}. */
	private static boolean inFront(Vec3 from, Vec3 look, LivingEntity e, double reach) {
		Vec3 to = e.getBoundingBox().getCenter().subtract(from);
		double length = to.length();
		return length <= reach + e.getBbWidth() * 0.5 && (length < 0.8 || to.scale(1.0 / length).dot(look) >= KICK_CONE);
	}

	/**
	 * Breaks the block the wearer looks at within {@code reach} and the ones round it on that face (a plus, or a full 3x3
	 * when {@code wide}), each no harder than {@code hardness} and only where the wearer may break blocks.
	 */
	private static boolean breakAhead(ServerPlayer player, double reach, float hardness, boolean wide) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		BlockHitResult block = level.clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(reach)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, player));
		if (block.getType() == HitResult.Type.MISS) {
			return false;
		}
		BlockPos center = block.getBlockPos();
		Direction face = block.getDirection();
		boolean broke = false;
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				if (!wide && a != 0 && b != 0) {
					continue;
				}
				BlockPos pos = switch (face.getAxis()) {
					case X -> center.offset(0, a, b);
					case Y -> center.offset(a, 0, b);
					case Z -> center.offset(a, b, 0);
				};
				BlockState state = level.getBlockState(pos);
				float h = state.getDestroySpeed(level, pos);
				if (!state.isAir() && h >= 0.0F && h <= hardness && player.mayInteract(level, pos)
					&& !player.blockActionRestricted(level, pos, player.gameMode())) {
					broke |= level.destroyBlock(pos, true, player);
				}
			}
		}
		return broke;
	}

	/** A melee blow the player landed (not the kicks themselves): the start of a combo. */
	private static void afterDamage(LivingEntity victim, DamageSource source, float base, float taken, boolean blocked) {
		if (source.getDirectEntity() instanceof ServerPlayer player && source.getEntity() == player && taken > 0.0F
			&& source.is(DamageTypes.PLAYER_ATTACK) && !kicking(player)) {
			COMBO.put(player.getUUID(), player.level().getGameTime());
		}
	}

	/** Whether the player's own kick is under way (its blows don't start a combo). */
	private static boolean kicking(ServerPlayer player) {
		RigMoves.Move move = RigMoves.get(player);
		return move != null && (move.kind() == RigMoves.KICK || move.kind() == RigMoves.ROUNDHOUSE);
	}
}
