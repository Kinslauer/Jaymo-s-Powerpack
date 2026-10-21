package com.jaymospowers.power.smoke;

import com.jaymospowers.core.Soundscape;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * C (corrupted), Pyroclasm: hold to gather (up to {@link #GATHER_TICKS}), let go, and a rolling, burning cloud of smog
 * and ash pours out along the ground where you look: {@link #REACH_MIN} blocks for a tap, {@link #REACH_MAX} at full.
 * It hugs the ground (climbs a block or two, pours down slopes and into hollows), widening as it goes. Whatever it rolls
 * over burns and chokes; it lights grass and leaves at its front (fire then spreads as fire does, so
 * {@code doFireTick} off keeps bases safe) and leaves smog behind. Cooldown {@link #COOLDOWN}.
 */
final class SmokePyroclasm {
	/** A burning flow's sound: a fire's roar at uneven beats, with spits and crackles over it. */
	static final Soundscape.Layer FLOW_ROAR = Soundscape.Layer.of(1.0F, 1.3F, 0.45F, 0.6F, SoundEvents.FIRE_AMBIENT);
	static final Soundscape.Layer FLOW_SPIT = Soundscape.Layer.of(0.5F, 0.8F, 0.6F, 0.9F, SoundEvents.BLAZE_BURN, SoundEvents.CAMPFIRE_CRACKLE).chance(0.5F);

	static final int GATHER_TICKS = 60;
	static final double REACH_MIN = 12.0, REACH_MAX = 24.0;
	/** How fast it rolls (blocks a tick), and how wide it is (radius) setting out and at the end. */
	static final double SPEED = 0.4, WIDTH_START = 2.0, WIDTH_END = 5.0;
	/** Damage every {@link #HIT_EVERY} ticks to what's inside, and how long they burn. */
	static final float DAMAGE = 2.0F;
	static final int HIT_EVERY = 5, BURN_SECONDS = 5;
	/** Smog left behind a tick. */
	static final float POUR = 4.0F;
	/** Chance a tick it lights something at its front. */
	static final float IGNITE = 0.35F;
	static final int COOLDOWN = 800;

	private static final class Flow {
		final ServerLevel level;
		final UUID owner;
		final Vec3 dir;
		final double reach;
		Vec3 pos;
		double gone;
		int age;

		Flow(ServerLevel level, UUID owner, Vec3 pos, Vec3 dir, double reach) {
			this.level = level;
			this.owner = owner;
			this.pos = pos;
			this.dir = dir;
			this.reach = reach;
		}
	}

	private static final Map<UUID, Long> GATHERING = new HashMap<>();
	private static final List<Flow> FLOWS = new ArrayList<>();

	private SmokePyroclasm() {
	}

	static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SmokePyroclasm::tick);
	}

	/** Gathering a Pyroclasm now. */
	static boolean isGathering(java.util.UUID id) {
		return GATHERING.containsKey(id);
	}

	/** C pressed ({@code down}) or let go. */
	static void key(ServerPlayer player, boolean down) {
		long now = player.level().getGameTime();
		if (down) {
			long left = SmokePower.state(player).left(SmokeState.PYRO, now);
			if (left > 0) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.smoke_pyroclasm_wait", (left + 19) / 20).withStyle(ChatFormatting.GRAY));
				return;
			}
			if (SmokeVapour.wholly(player)) {
				return;
			}
			GATHERING.put(player.getUUID(), now);
			SmokeMoves.begin(player, SmokeMoves.GATHER);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_AMBIENT, SoundSource.PLAYERS, 0.8F, 0.5F);
			return;
		}
		Long from = GATHERING.remove(player.getUUID());
		if (from == null || !player.isAlive()) {
			return;
		}
		float charge = Mth.clamp((now - from) / (float)GATHER_TICKS, 0.0F, 1.0F);
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		if (flat.lengthSqr() < 1.0E-4) {
			flat = Vec3.directionFromRotation(0.0F, player.getYRot());
		}
		Vec3 dir = flat.normalize();
		Vec3 start = ground(player.level(), player.position().add(dir.scale(1.5)));
		FLOWS.add(new Flow(player.level(), player.getUUID(), start, dir, Mth.lerp(charge, REACH_MIN, REACH_MAX)));
		SmokePower.cooldown(player, SmokeState.PYRO, COOLDOWN);
		SmokeMoves.begin(player, SmokeMoves.PYRO);
		ServerLevel level = player.level();
		level.playSound(null, start.x, start.y, start.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F + charge, 0.5F);
		level.playSound(null, start.x, start.y, start.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.2F, 0.4F);
		com.jaymospowers.core.Shakes.around(level, start, 16.0, 0.4F + charge * 0.6F);
	}

	/** While gathering: embers and smoke gather round the user (from the power's tick). */
	static void gathering(ServerPlayer player) {
		Long from = GATHERING.get(player.getUUID());
		if (from == null) {
			return;
		}
		if (!player.isAlive()) {
			GATHERING.remove(player.getUUID());
			return;
		}
		ServerLevel level = player.level();
		float charge = Mth.clamp((level.getGameTime() - from) / (float)GATHER_TICKS, 0.0F, 1.0F);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.5, player.getZ(), 2 + (int)(charge * 6), 0.6, 0.3, 0.6, 0.01);
		level.sendParticles(ParticleTypes.LAVA, player.getX(), player.getY() + 0.3, player.getZ(), charge >= 1.0F ? 2 : 0, 0.5, 0.1, 0.5, 0.0);
	}

	private static void tick(MinecraftServer server) {
		if (FLOWS.isEmpty()) {
			return;
		}
		FLOWS.removeIf(SmokePyroclasm::roll);
	}

	/** One tick of a rolling cloud; true once it's spent. */
	private static boolean roll(Flow flow) {
		ServerLevel level = flow.level;
		ServerPlayer owner = level.getServer().getPlayerList().getPlayer(flow.owner);
		flow.age++;
		Vec3 next = ground(level, flow.pos.add(flow.dir.scale(SPEED)));
		if (next.y > flow.pos.y + 2.1) {
			// a wall: it piles up against it and stops
			flow.gone = flow.reach;
		} else {
			flow.pos = next;
			flow.gone += SPEED;
		}
		double share = Mth.clamp(flow.gone / flow.reach, 0.0, 1.0);
		double width = Mth.lerp(share, WIDTH_START, WIDTH_END);
		Vec3 at = flow.pos.add(0.0, 0.8, 0.0);
		SmokeField.puff(level, at, POUR, owner);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 10, width * 0.5, 0.6, width * 0.5, 0.03);
		level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.5, at.z, 3, width * 0.4, 0.4, width * 0.4, 0.01);
		level.sendParticles(ParticleTypes.FLAME, at.x, at.y - 0.3, at.z, 6, width * 0.4, 0.3, width * 0.4, 0.02);
		level.sendParticles(ParticleTypes.ASH, at.x, at.y, at.z, 12, width * 0.6, 0.8, width * 0.6, 0.02);
		if (Soundscape.due(level, System.identityHashCode(flow), "smoke.pyroclasm", 6, 12)) {
			Soundscape.play(level, at, SoundSource.PLAYERS, FLOW_ROAR, FLOW_SPIT);
		}
		if (flow.age % HIT_EVERY == 0) {
			for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(width, 1.8, width),
				e -> e.isAlive() && e != owner && !(e instanceof ArmorStand) && !e.isSpectator())) {
				living.igniteForSeconds(BURN_SECONDS);
				living.hurtServer(level, owner == null ? level.damageSources().inFire() : level.damageSources().source(
					net.minecraft.world.damagesource.DamageTypes.IN_FIRE, owner), DAMAGE);
				living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0), owner);
			}
		}
		if (level.getRandom().nextFloat() < IGNITE) {
			ignite(level, flow, width);
		}
		return flow.gone >= flow.reach;
	}

	/** Lights the ground at the front: on grass, leaves or anything that burns. */
	private static void ignite(ServerLevel level, Flow flow, double width) {
		Vec3 side = new Vec3(-flow.dir.z, 0.0, flow.dir.x);
		Vec3 spot = flow.pos.add(flow.dir.scale(1.0)).add(side.scale((level.getRandom().nextDouble() * 2.0 - 1.0) * width));
		BlockPos pos = BlockPos.containing(ground(level, spot));
		for (int dy = 1; dy >= -1; dy--) {
			BlockPos at = pos.above(dy);
			BlockState here = level.getBlockState(at);
			// in the open or in the grass, on solid ground or something that burns
			if ((here.isAir() || here.canBeReplaced()) && here.getFluidState().isEmpty() && BaseFireBlock.canBePlacedAt(level, at, Direction.UP)) {
				level.setBlockAndUpdate(at, BaseFireBlock.getState(level, at));
				return;
			}
		}
	}

	/** Where the ground is under (or just over) {@code at}: the top of the first solid block within a few blocks down. */
	private static Vec3 ground(ServerLevel level, Vec3 at) {
		BlockPos pos = BlockPos.containing(at.x, at.y + 2.0, at.z);
		for (int i = 0; i < 8; i++) {
			BlockPos below = pos.below();
			if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
				return new Vec3(at.x, pos.getY(), at.z);
			}
			pos = below;
		}
		return new Vec3(at.x, pos.getY(), at.z);
	}

	static void forget(ServerPlayer player) {
		GATHERING.remove(player.getUUID());
	}

	static void clear() {
		GATHERING.clear();
		FLOWS.clear();
	}
}
