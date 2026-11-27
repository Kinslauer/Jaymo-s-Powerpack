package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.wounds.Downed;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Grind across the ground (Legends patch 4, {@code sy_grind}): with a foe pinned in the tendrils' hand
 * ({@link WebHeldGrab}, the close hold), press the Grind key and you rush along the ground, steering by where you look,
 * dragging it through the dirt behind you ({@link #DRAG_TICKS} at most, or press again to end it early); then both tendrils
 * heave it overhead and slam it down. The drag hurts a little every few ticks, tears the grass and plants it passes and
 * leaves a scar on grass: it goes through the same rules as {@link WebSlam#breakGround} (build permission, mob griefing,
 * a block you may touch). Only what the tendrils can haul ({@link WebHaul#haulable}) is dragged; the hold itself, its limit
 * and its drop are {@link WebHeldGrab}'s. It costs a little satiety and ends on death, downed, disconnect, a change of
 * world, power loss, the hold breaking or the feet leaving the ground. {@link WebPose#GRIND_RUSH} and {@link WebPose#GRIND_LIFT}
 * draw the body.
 */
public final class WebGrind {
	/** The longest the drag goes (ticks), the shortest before it can be ended, the lift's length and the tick the slam lands in it. */
	public static final int DRAG_TICKS = 55, MIN_DRAG = 8, LIFT_TICKS = 24, SLAM_AT = 16;
	/** The rush: blocks a tick along the ground. */
	static final double SPEED = 0.62;
	/** Where the drag holds it: this far behind you (plus half its width). */
	static final double DRAG_BEHIND = 1.15;
	/** The drag: damage every this many ticks, the damage; the slam: its damage, and the stun (Slowness) it leaves. */
	static final int HURT_EVERY = 6, STUN_TICKS = 40, STUN_LEVEL = 2, COOLDOWN = 140, COST = 4;
	static final float DRAG_DAMAGE = 1.5F, SLAM_DAMAGE = 9.0F;
	/** The slam's breaking of the ground, the same speed the heavy landing uses. */
	static final double BREAK_SPEED = 0.9;
	/** A foe no further than this (blocks) from you is held in the drag or lift; blocked this many ticks the rush ends. */
	static final int BLOCKED_TICKS = 6;
	static final float SHAKE = 0.9F;

	/** The key: begins a grind, or ends the drag early. */
	public record Press() implements CustomPacketPayload {
		public static final Type<Press> TYPE = new Type<>(JaymosPowers.id("web_grind"));
		public static final StreamCodec<ByteBuf, Press> CODEC = StreamCodec.unit(new Press());

		@Override
		public Type<Press> type() {
			return TYPE;
		}
	}

	private static final class Live {
		final ServerLevel level;
		final long start;
		long liftAt = -1L;
		Vec3 last;
		int blocked;
		boolean slammed;

		Live(ServerLevel level, long start, Vec3 at) {
			this.level = level;
			this.start = start;
			this.last = at;
		}
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();

	private WebGrind() {
	}

	static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Press.TYPE, Press.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Press.TYPE, (payload, ctx) -> press(ctx.player()));
	}

	/** A grind is under way for this player (either phase). */
	public static boolean active(ServerPlayer player) {
		return LIVE.containsKey(player.getUUID());
	}

	/** The drag (not yet the lift) is under way. */
	static boolean dragging(ServerPlayer player) {
		Live live = LIVE.get(player.getUUID());
		return live != null && live.liftAt < 0L;
	}

	private static boolean able(ServerPlayer player) {
		return player.isAlive() && !player.isSpectator() && !Downed.isDowned(player) && Powers.has(player, WebPower.INSTANCE)
			&& !Powers.silenced(player) && WebSymbiote.isBonded(player) && WebSymbiote.tendrilKeys(player) && !WebSymbiote.driving(player)
			&& !player.isPassenger() && !player.isInWater() && !player.getAbilities().flying;
	}

	private static void press(ServerPlayer player) {
		long now = player.level().getGameTime();
		Live live = LIVE.get(player.getUUID());
		if (live != null) {
			if (live.liftAt < 0L && now - live.start >= MIN_DRAG) {
				lift(player, live, now);
			}
			return;
		}
		WebHeldGrab.Held held = player.getAttached(WebHeldGrab.HELD);
		LivingEntity foe = WebHeldGrab.heldEntity(player);
		if (!able(player) || held == null || held.carried() || foe == null || !foe.isAlive() || !WebHaul.haulable(foe)
			|| !player.onGround() || WebStilts.up(player) || now < READY.getOrDefault(player.getUUID(), 0L)
			|| held.attack() >= 0 && now - held.attackStart() < WebHeldGrab.animTicks(held.attack()) || now - held.since() < 2) {
			return;
		}
		if (!player.isCreative()) {
			WebSymbioteState state = WebSymbiote.state(player);
			if (state.satiety() < COST) {
				return;
			}
			WebSymbiote.set(player, state.withSatiety(state.satiety() - COST));
		}
		LIVE.put(player.getUUID(), new Live(player.level(), now, player.position()));
		WebPower.pose(player, WebPose.GRIND_RUSH, 1);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.0F, 0.6F);
		player.level().playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.SLIME_ATTACK, SoundSource.PLAYERS, 1.0F, 0.5F);
	}

	/** Where the hold has the foe now, for {@link WebHeldGrab#point}: dragged along the ground behind you, or heaved over your head and brought down. */
	static @Nullable Vec3 point(ServerPlayer player, LivingEntity target, long now) {
		Live live = LIVE.get(player.getUUID());
		if (live == null) {
			return null;
		}
		Vec3 flat = player.getLookAngle().horizontal();
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
		double scale = player.getScale();
		if (live.liftAt < 0L) {
			Vec3 spot = player.position().subtract(flat.scale(DRAG_BEHIND * scale + target.getBbWidth() * 0.5));
			return new Vec3(spot.x, player.getY() + target.getBbHeight() * 0.5, spot.z);
		}
		long t = now - live.liftAt;
		Vec3 over = player.position().add(0.0, (player.getBbHeight() + 0.3 * scale) + target.getBbHeight() * 0.5, 0.0);
		Vec3 ground = player.position().add(flat.scale(1.4 * scale + target.getBbWidth() * 0.5)).add(0.0, target.getBbHeight() * 0.5, 0.0);
		Vec3 start = player.position().subtract(flat.scale(DRAG_BEHIND * scale + target.getBbWidth() * 0.5)).add(0.0, target.getBbHeight() * 0.5, 0.0);
		if (t < SLAM_AT - 5) {
			double up = Mth.clamp(t / (double)(SLAM_AT - 5), 0.0, 1.0);
			up = up * up * (3.0 - 2.0 * up);
			return start.lerp(over, up);
		}
		double down = Mth.clamp((t - (SLAM_AT - 5)) / 5.0, 0.0, 1.0);
		return over.lerp(ground, down * down);
	}

	/** Every tick, per web slinger. */
	static void tick(ServerPlayer player) {
		Live live = LIVE.get(player.getUUID());
		if (live == null) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		LivingEntity foe = WebHeldGrab.heldEntity(player);
		WebHeldGrab.Held held = player.getAttached(WebHeldGrab.HELD);
		if (!able(player) || live.level != level || held == null || held.carried() || foe == null || !foe.isAlive() || foe.level() != level
			|| !WebHaul.haulable(foe)) {
			end(player, true);
			return;
		}
		if (live.liftAt < 0L) {
			drag(player, live, foe, now);
		} else {
			lifting(player, live, foe, now);
		}
	}

	private static void drag(ServerPlayer player, Live live, LivingEntity foe, long now) {
		ServerLevel level = live.level;
		long age = now - live.start;
		if (!player.onGround() && age > 2 && player.getDeltaMovement().y > 0.3) {
			// off the ground (a ledge, a jump): the rush is over, it goes on to the lift
			lift(player, live, now);
			return;
		}
		Vec3 look = player.getLookAngle().horizontal();
		look = look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize();
		Vec3 motion = player.getDeltaMovement();
		WebHaul.push(player, new Vec3(look.x * SPEED, Math.min(motion.y, 0.0) > -0.4 ? motion.y : -0.4, look.z * SPEED));
		player.resetFallDistance();
		// blocked by a wall or a block it cannot step: the rush ends there
		double moved = player.position().subtract(live.last).horizontalDistance();
		live.last = player.position();
		live.blocked = age > 4 && moved < 0.08 ? live.blocked + 1 : 0;
		if (age % 5 == 0) {
			WebPower.pose(player, WebPose.GRIND_RUSH, 1);
		}
		// through the dirt: a scar, torn plants, a spray
		Vec3 feet = new Vec3(foe.getX(), foe.getBoundingBox().minY, foe.getZ());
		scar(player, level, feet);
		if (age % HURT_EVERY == 0) {
			foe.hurtServer(level, level.damageSources().playerAttack(player), DRAG_DAMAGE);
			level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.8F, 0.6F);
			Shakes.send(player, 0.25F, feet);
		}
		if (age % 3 == 0) {
			level.playSound(null, feet.x, feet.y, feet.z, SoundEvents.GRAVEL_BREAK, SoundSource.PLAYERS, 0.8F, 0.6F);
		}
		if (age >= DRAG_TICKS || live.blocked >= BLOCKED_TICKS) {
			lift(player, live, now);
		}
	}

	private static void lift(ServerPlayer player, Live live, long now) {
		live.liftAt = now;
		WebHaul.push(player, new Vec3(0.0, Math.min(0.0, player.getDeltaMovement().y), 0.0));
		WebPower.pose(player, WebPose.GRIND_LIFT, 1);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 0.5F);
	}

	private static void lifting(ServerPlayer player, Live live, LivingEntity foe, long now) {
		ServerLevel level = live.level;
		long t = now - live.liftAt;
		// planted while it heaves
		Vec3 motion = player.getDeltaMovement();
		if (player.onGround() && motion.horizontalDistanceSqr() > 0.01) {
			WebHaul.push(player, new Vec3(0.0, motion.y, 0.0));
		}
		if (t == SLAM_AT && !live.slammed) {
			live.slammed = true;
			Vec3 at = new Vec3(foe.getX(), foe.getBoundingBox().minY, foe.getZ());
			foe.hurtServer(level, level.damageSources().playerAttack(player), SLAM_DAMAGE);
			foe.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, STUN_TICKS, STUN_LEVEL), player);
			WebHaul.burst(level, at, 1.0F);
			WebSlam.breakGround(player, at, BREAK_SPEED);
			Shakes.send(player, SHAKE, at);
			Shakes.around(level, at, 16.0, 0.4F);
		}
		if (t >= LIFT_TICKS - 2) {
			end(player, true);
		}
	}

	/** Tears what the foe is dragged over: plants go, grass turns to bare earth, dust flies; where the ground may not be touched, only the dust. */
	private static void scar(ServerPlayer player, ServerLevel level, Vec3 feet) {
		BlockPos under = BlockPos.containing(feet.x, feet.y - 0.2, feet.z);
		BlockState ground = level.getBlockState(under);
		if (ground.isAir()) {
			return;
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), feet.x, feet.y + 0.1, feet.z, 6, 0.3, 0.05, 0.3, 0.1);
		if (!level.hasChunkAt(under) || !player.onGround() || !player.mayBuild() || !level.getGameRules().get(GameRules.MOB_GRIEFING)
			|| !level.getWorldBorder().isWithinBounds(under) || !level.mayInteract(player, under)) {
			return;
		}
		BlockPos above = under.above();
		BlockState plant = level.getBlockState(above);
		if (!plant.isAir() && plant.getDestroySpeed(level, above) == 0.0F && plant.getFluidState().isEmpty() && plant.canBeReplaced()
			&& level.mayInteract(player, above)) {
			level.destroyBlock(above, false, player);
		}
		if (ground.is(Blocks.GRASS_BLOCK) || ground.is(Blocks.PODZOL) || ground.is(Blocks.MYCELIUM)) {
			level.setBlockAndUpdate(under, Blocks.DIRT.defaultBlockState());
		} else if (ground.is(Blocks.DIRT) && level.getRandom().nextInt(3) == 0) {
			level.setBlockAndUpdate(under, Blocks.COARSE_DIRT.defaultBlockState());
		}
	}

	/** The grind ends: whatever it holds is let go where it is (alive and no more than stunned), and it cools. */
	static void end(ServerPlayer player, boolean drop) {
		Live live = LIVE.remove(player.getUUID());
		if (live == null) {
			return;
		}
		READY.put(player.getUUID(), player.level().getGameTime() + COOLDOWN);
		WebPose pose = player.getAttached(WebPower.POSE);
		if (pose != null && (pose.kind() == WebPose.GRIND_RUSH || pose.kind() == WebPose.GRIND_LIFT)) {
			player.removeAttached(WebPower.POSE);
		}
		if (drop) {
			WebHeldGrab.drop(player);
		}
	}

	static void forget(ServerPlayer player) {
		end(player, true);
		READY.remove(player.getUUID());
	}

	static void clearAll() {
		LIVE.clear();
		READY.clear();
	}
}
