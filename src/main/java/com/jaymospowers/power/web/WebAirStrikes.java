package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Carry;
import com.jaymospowers.core.Powers;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Strikes out of a swing: let go of a web and, still in the air, a click throws one of three blows (everyone sees the
 * body in it: {@link WebPose#RISING_BLOW}, {@link WebPose#DIVE_KICK}, {@link WebPose#SPIN_STRIKE}).
 * <ul>
 * <li>A click: a rising blow at what's ahead of you, which throws it up into the air (you rise a little with it).</li>
 * <li>A click while a trick is still turning you (a flip, a corkscrew, a cartwheel): a spinning strike that hits everything
 * within {@link #SPIN_RADIUS} all round you and knocks it away.</li>
 * <li>Sneak + a click: a two-footed dive kick. You plunge, and where you land (or on whoever you land on) the ground is
 * slammed: everything near is hurt and thrown up, and a hard enough landing breaks the ground ({@link WebImpacts#smash}).
 * Falling still hurts as it always does.</li>
 * </ul>
 * The slinger's own client picks the blow from how the swing went and asks for it; the server checks the web was let go
 * of a moment ago and that nothing else has the body. Each strike also counts as a stunt ({@link WebStunts}).
 */
public final class WebAirStrikes {
	// ================================================================================================================
	// Tuning
	/** A strike must come this soon (ticks) after the last web let go, and no sooner than {@link #COOLDOWN} after the last strike. */
	public static final int WINDOW = 60, COOLDOWN = 24;
	/** How far ahead the rising blow reaches (blocks), its damage, and how hard it throws the foe up (blocks/tick). */
	static final double RISE_REACH = 3.4, RISE_THROW = 1.0;
	static final float RISE_DAMAGE = 7.0F;
	/** What the slinger gains from it: up this fast (blocks/tick), keeping this share of its speed. */
	static final double RISE_LIFT = 0.45, RISE_KEEP = 0.6;
	/** The spinning strike: the circle (blocks), damage, how far it knocks things away (blocks/tick) and up. */
	static final double SPIN_RADIUS = 3.2, SPIN_KNOCK = 0.9, SPIN_UP = 0.35;
	static final float SPIN_DAMAGE = 5.0F;
	/** The dive: how fast the plunge goes (blocks/tick down), how much of the facing it keeps along the ground, the longest it lasts (ticks). */
	static final double DIVE_SPEED = 1.7, DIVE_FORWARD = 0.4;
	static final int DIVE_MAX_TICKS = 100;
	/** The slam where it lands: the circle (blocks), the throw up and out (blocks/tick). */
	static final double SLAM_RADIUS = 3.6, SLAM_UP = 0.65, SLAM_OUT = 0.5;
	/** Its damage: a base, and more per block/tick of landing speed, capped. */
	static final float SLAM_BASE = 6.0F, SLAM_PER_SPEED = 3.0F, SLAM_MAX = 18.0F;
	/** A foe this close under the feet, in the dive and falling at least this fast (blocks/tick), is landed on. */
	static final double LAND_ON_REACH = 0.6, LAND_ON_SPEED = 0.6;
	// ================================================================================================================

	/** Client → server: throw strike {@code kind} ({@link WebPose#RISING_BLOW}..{@link WebPose#SPIN_STRIKE}). */
	public record Strike(int kind) implements CustomPacketPayload {
		public static final Type<Strike> TYPE = new Type<>(JaymosPowers.id("web_air_strike"));
		public static final StreamCodec<ByteBuf, Strike> CODEC = ByteBufCodecs.VAR_INT.map(Strike::new, Strike::kind);

		@Override
		public Type<Strike> type() {
			return TYPE;
		}
	}

	private static final class Live {
		/** Game time a web was last out, and whether one is out now. */
		long lastSwung = Long.MIN_VALUE;
		boolean swinging;
		long nextStrike;
		/** The dive: when it began (-1: not diving), and the height last tick (for the landing speed). */
		long diveStart = -1;
		double lastY;
	}

	private static final Map<ServerPlayer, Live> LIVE = new WeakHashMap<>();

	private WebAirStrikes() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Strike.TYPE, Strike.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Strike.TYPE, (payload, ctx) -> strike(ctx.player(), payload.kind()));
	}

	private static Live live(ServerPlayer player) {
		return LIVE.computeIfAbsent(player, p -> new Live());
	}

	/** Every tick, for a web slinger. {@code swinging}: a swing web is stuck right now. */
	static void tick(ServerPlayer player, boolean swinging) {
		Live live = live(player);
		long now = player.level().getGameTime();
		live.swinging = swinging;
		if (swinging) {
			live.lastSwung = now;
		}
		if (live.diveStart >= 0) {
			tickDive(player, live, now);
		}
	}

	/** A web was let go of within the last {@code ticks}, and none is out now (the swing is over, the body still in the air). */
	static boolean released(ServerPlayer player, int ticks) {
		Live live = LIVE.get(player);
		return live != null && !live.swinging && player.level().getGameTime() - live.lastSwung <= ticks;
	}

	/** Whether the player is plunging in a dive kick right now. */
	static boolean diving(ServerPlayer player) {
		Live live = LIVE.get(player);
		return live != null && live.diveStart >= 0;
	}

	static void forget(ServerPlayer player) {
		LIVE.remove(player);
	}

	private static void strike(ServerPlayer player, int kind) {
		if (kind < WebPose.RISING_BLOW || kind > WebPose.SPIN_STRIKE || !player.isAlive() || player.isSpectator() || player.isPassenger()
			|| player.onGround() || player.isInWater() || player.isInLava() || player.isFallFlying() || player.getAbilities().flying
			|| !Powers.has(player, WebPower.INSTANCE) || Carry.isCarried(player) || WebPower.ragdolled(player) || WebSymbiote.driving(player)) {
			return;
		}
		Live live = live(player);
		long now = player.level().getGameTime();
		if (now < live.nextStrike || live.diveStart >= 0 || !released(player, WINDOW + 10)) {
			return;
		}
		WebPose current = player.getAttached(WebPower.POSE);
		if (current != null && now - current.start() < current.ticks() && !WebPose.trick(current.kind())) {
			// a dodge, a ragdoll or another strike has the body
			return;
		}
		live.nextStrike = now + COOLDOWN;
		WebPower.pose(player, kind, player.getRandom().nextBoolean() ? 1 : -1);
		com.jaymospowers.core.trials.PowerTrials.count(player, "web.air_strike", 1);
		WebStunts.trick(player, kind);
		switch (kind) {
			case WebPose.RISING_BLOW -> rising(player);
			case WebPose.SPIN_STRIKE -> spin(player);
			default -> startDive(player, live, now);
		}
	}

	/** Everything alive within {@code box} that this player may hurt (no one carried, no pet of theirs, no friend they can't harm). */
	private static List<LivingEntity> foes(ServerLevel level, ServerPlayer player, AABB box) {
		List<LivingEntity> out = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !e.isSpectator()
			&& !e.isPassengerOfSameVehicle(player) && !Carry.isCarried(e) && !(e instanceof TamableAnimal pet && pet.isOwnedBy(player))
			&& (!(e instanceof Player other) || player.canHarmPlayer(other)))) {
			out.add(e);
		}
		return out;
	}

	private static void hurt(ServerLevel level, ServerPlayer player, LivingEntity target, float damage) {
		target.setInvulnerableTime(0);
		target.hurtServer(level, player.damageSources().playerAttack(player), damage);
	}

	// ---- the rising blow ---------------------------------------------------------------------------------------------

	private static void rising(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle();
		AABB reach = player.getBoundingBox().expandTowards(look.scale(RISE_REACH)).inflate(0.7);
		LivingEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		for (LivingEntity e : foes(level, player, reach)) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(player.getEyePosition());
			double d = to.lengthSqr();
			if (d <= RISE_REACH * RISE_REACH + 1.0 && to.normalize().dot(look) > 0.35 && d < bestDistance) {
				bestDistance = d;
				best = e;
			}
		}
		Vec3 v = player.getDeltaMovement();
		if (best == null) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 1.4F);
			return;
		}
		hurt(level, player, best, RISE_DAMAGE);
		if (best.isAlive()) {
			Vec3 push = best.getBoundingBox().getCenter().subtract(player.position()).multiply(1.0, 0.0, 1.0);
			Vec3 flat = push.lengthSqr() < 1.0E-4 ? Vec3.ZERO : push.normalize().scale(0.25);
			WebHaul.push(best, flat.add(0.0, RISE_THROW, 0.0));
		}
		// the slinger rises with the blow
		WebHaul.push(player, new Vec3(v.x * RISE_KEEP, Math.max(v.y, 0.0) + RISE_LIFT, v.z * RISE_KEEP));
		Vec3 at = best.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 10, 0.3, 0.4, 0.3, 0.06);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 8, 0.3, 0.4, 0.3, 0.2);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.1F, 0.9F);
		com.jaymospowers.core.Shakes.send(player, 0.4F, at);
	}

	// ---- the spinning strike -----------------------------------------------------------------------------------------

	private static void spin(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 centre = player.getBoundingBox().getCenter();
		int hit = 0;
		for (LivingEntity e : foes(level, player, player.getBoundingBox().inflate(SPIN_RADIUS))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(centre);
			if (to.length() > SPIN_RADIUS + e.getBbWidth() * 0.5) {
				continue;
			}
			hurt(level, player, e, SPIN_DAMAGE);
			if (e.isAlive()) {
				Vec3 flat = to.multiply(1.0, 0.0, 1.0);
				Vec3 away = flat.lengthSqr() < 1.0E-4 ? player.getLookAngle().multiply(1.0, 0.0, 1.0) : flat.normalize();
				WebHaul.push(e, away.scale(SPIN_KNOCK).add(0.0, SPIN_UP, 0.0));
			}
			hit++;
		}
		for (int i = 0; i < 8; i++) {
			double a = i * Math.PI / 4.0;
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, centre.x + Math.cos(a) * 1.4, centre.y, centre.z + Math.sin(a) * 1.4, 1, 0.0, 0.0, 0.0, 0.0);
		}
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.2F);
		if (hit > 0) {
			level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9F, 1.0F);
			com.jaymospowers.core.Shakes.send(player, 0.35F, centre);
		}
	}

	// ---- the dive kick -----------------------------------------------------------------------------------------------

	private static void startDive(ServerPlayer player, Live live, long now) {
		live.diveStart = now;
		live.lastY = player.getY();
		Vec3 flat = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(DIVE_FORWARD);
		WebHaul.push(player, new Vec3(flat.x, -DIVE_SPEED, flat.z));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 0.7F);
	}

	private static void tickDive(ServerPlayer player, Live live, long now) {
		double fall = live.lastY - player.getY();
		live.lastY = player.getY();
		if (!player.isAlive() || player.isSpectator() || player.isInWater() || player.isInLava() || player.isPassenger()
			|| now - live.diveStart > DIVE_MAX_TICKS || player.getAbilities().flying) {
			live.diveStart = -1;
			return;
		}
		if (now - live.diveStart < 2) {
			return;
		}
		LivingEntity under = null;
		if (fall >= LAND_ON_SPEED) {
			AABB feet = player.getBoundingBox().expandTowards(0.0, -LAND_ON_REACH, 0.0);
			for (LivingEntity e : foes(player.level(), player, feet)) {
				under = e;
				break;
			}
		}
		if (player.onGround() || under != null) {
			live.diveStart = -1;
			slam(player, Math.max(fall, 0.0), under);
		}
	}

	/** The landing: the ground slammed where the feet came down (or where the foe they landed on stood). */
	private static void slam(ServerPlayer player, double speed, LivingEntity under) {
		ServerLevel level = player.level();
		Vec3 at = under != null ? under.position() : player.position();
		float damage = Math.min(SLAM_MAX, SLAM_BASE + SLAM_PER_SPEED * (float)speed);
		for (LivingEntity e : foes(level, player, new AABB(at, at).inflate(SLAM_RADIUS, 1.5, SLAM_RADIUS))) {
			double d = e.position().distanceTo(at);
			if (d > SLAM_RADIUS + e.getBbWidth() * 0.5) {
				continue;
			}
			// the one landed on takes all of it, the rest less the further they stand
			hurt(level, player, e, e == under ? damage * 1.25F : (float)(damage * (1.0 - 0.5 * d / SLAM_RADIUS)));
			if (e.isAlive()) {
				Vec3 flat = e.position().subtract(at).multiply(1.0, 0.0, 1.0);
				Vec3 out = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(SLAM_OUT);
				WebHaul.push(e, out.add(0.0, SLAM_UP, 0.0));
			}
		}
		BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
		if (ground.isAir()) {
			ground = Blocks.DIRT.defaultBlockState();
		}
		int count = (int)Math.min(160, 40 + 60 * speed);
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z, count, SLAM_RADIUS * 0.4, 0.05, SLAM_RADIUS * 0.4, 0.2);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.2, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y + 0.3, at.z, 14, 0.8, 0.2, 0.8, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, (float)Math.min(1.4, 0.7 + speed * 0.4), 0.8F);
		com.jaymospowers.core.Shakes.heavy(player, at, 12.0, (float)Math.min(9.0, 3.0 + speed * 3.0));
		WebImpacts.smash(level, at.add(0.0, -0.5, 0.0), speed, player);
		WebPower.pose(player, WebPose.HERO_LAND, 1);
	}
}
