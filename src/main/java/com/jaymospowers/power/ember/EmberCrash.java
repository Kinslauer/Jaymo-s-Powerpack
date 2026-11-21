package com.jaymospowers.power.ember;

import com.jaymospowers.core.Flames;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The Emberborn's rise and crash (Andy: "fiery rise and crash"), built like the Rig's meteor dive ({@code RigDive}): a
 * burst of fire shoots you straight up for {@link #RISE_TICKS}, then you crash down toward where you looked at the top,
 * and land in a ring of fire that burns and throws back what stands near. No block is broken. Sprint + the heritage key
 * (Kingdoms asks by name: {@link #crash}); a fire user only ({@link RunningHot#user}); {@link #COOLDOWN} ticks between.
 * <p>
 * Fall damage still applies on every other fall. The crash's own landing is absorbed (RigDive does not do this, so a Rig
 * dive's landing is hurt by the fall as well): fall damage is refused while the crash lasts and for {@link #GRACE} ticks after.
 */
public final class EmberCrash {
	/** Up speed and how long it climbs (ticks); down speed; the longest a crash may take (ticks). */
	static final double UP_SPEED = 1.3, DOWN_SPEED = 2.2;
	static final int RISE_TICKS = 11, CRASH_MAX = 80;
	/** How far the look is followed to pick the landing spot (blocks). */
	static final double AIM_REACH = 36.0;
	/** The landing: reach, the push. */
	static final double RADIUS = 4.5, PUSH = 1.2;
	/** Landing damage, burn seconds, and ticks between crashes. */
	static final float DAMAGE = 11.0F;
	static final int BURN = 6, COOLDOWN = 20 * 18;
	/** Heat the crash feeds the gauge, hunger it costs, ticks after landing in which fall damage is still absorbed. */
	static final float HEAT = 20.0F, EXHAUSTION = 2.0F;
	static final int GRACE = 5;
	static final int COLOR = 0xFF8A1E, WHITE = 0xFFE9B0;

	private static final class Run {
		/** 0 rising, 1 crashing. */
		int phase;
		long since;
		Vec3 target = Vec3.ZERO;
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();
	/** Who landed from a crash, and until when fall damage is still absorbed. */
	private static final Map<UUID, Long> ABSORB = new HashMap<>();

	private EmberCrash() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(EmberCrash::tick);
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			READY.remove(handler.player.getUUID());
			ABSORB.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			RUNS.clear();
			READY.clear();
			ABSORB.clear();
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && source.is(DamageTypeTags.IS_FALL)) {
				if (RUNS.containsKey(player.getUUID())) {
					return false;
				}
				Long until = ABSORB.get(player.getUUID());
				if (until != null && player.level().getGameTime() <= until) {
					return false;
				}
			}
			return true;
		});
	}

	/** Sprint + the heritage key: rise and crash. False if {@code player} is not a fire user (Kingdoms then does its own move). */
	public static boolean crash(ServerPlayer player) {
		if (!RunningHot.user(player) || !player.isAlive() || player.isSpectator()) {
			return false;
		}
		if (RUNS.containsKey(player.getUUID()) || player.isPassenger() || player.isInWater() || player.getAbilities().flying) {
			return true;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		long left = READY.getOrDefault(player.getUUID(), 0L) - now;
		if (left > 0 && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.ember_crash_wait", (left + 19) / 20).withStyle(ChatFormatting.GRAY));
			return true;
		}
		READY.put(player.getUUID(), now + COOLDOWN);
		Run run = new Run();
		run.since = now;
		RUNS.put(player.getUUID(), run);
		player.causeFoodExhaustion(EXHAUSTION);
		EmberHeat.cast(player);
		player.setDeltaMovement(0.0, UP_SPEED, 0.0);
		player.syncVelocity = true;
		MagicFx.ring(level, player.position().add(0.0, 0.1, 0.0), 3.0F, COLOR);
		MagicFx.blast(level, player.position().add(0.0, 0.3, 0.0), 1.5F, WHITE);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.6F);
		return true;
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Run>> it = RUNS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, Run> entry = it.next();
			Run run = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive()) {
				it.remove();
				continue;
			}
			ServerLevel level = player.level();
			long age = level.getGameTime() - run.since;
			if (run.phase == 0) {
				player.setDeltaMovement(0.0, UP_SPEED, 0.0);
				player.syncVelocity = true;
				level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY(), player.getZ(), 6, 0.2, 0.1, 0.2, 0.05);
				if (age >= RISE_TICKS || player.verticalCollision && age > 2) {
					run.phase = 1;
					run.since = level.getGameTime();
					run.target = aim(player);
					Vec3 toward = run.target.subtract(player.position());
					Vec3 dir = new Vec3(toward.x, -Math.max(1.0, Math.abs(toward.y)), toward.z);
					player.setDeltaMovement(dir.normalize().scale(DOWN_SPEED));
					player.syncVelocity = true;
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.5F);
				}
			} else {
				level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 0.5, player.getZ(), 5, 0.25, 0.4, 0.25, 0.03);
				if (player.onGround() || player.isInWater() || age >= CRASH_MAX) {
					it.remove();
					land(level, player);
				}
			}
		}
	}

	/** The spot looked at, out to {@link #AIM_REACH}: the block hit, or the end of the look. */
	private static Vec3 aim(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 to = eye.add(player.getLookAngle().scale(AIM_REACH));
		HitResult hit = player.level().clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();
	}

	private static void land(ServerLevel level, ServerPlayer player) {
		ABSORB.put(player.getUUID(), level.getGameTime() + GRACE);
		player.resetFallDistance();
		Vec3 at = player.position();
		MagicFx.ring(level, at.add(0.0, 0.1, 0.0), (float)RADIUS, COLOR);
		MagicFx.blast(level, at.add(0.0, 0.5, 0.0), 2.2F, WHITE);
		MagicFx.pool(level, at.add(0.0, 0.05, 0.0), (float)RADIUS * 0.8F, COLOR, 30);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.9F, 1.1F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.5F);
		Shakes.around(level, at, 24.0, 1.5F);
		RunningHot.add(player, HEAT);
		for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RADIUS), e -> FireKit.foe(player, e))) {
			FireKit.hurt(player, near, DAMAGE);
			Flames.touch(level, near, BURN, player);
			Vec3 away = near.position().subtract(at);
			Vec3 flat = new Vec3(away.x, 0.0, away.z);
			flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
			near.setDeltaMovement(near.getDeltaMovement().add(flat.scale(PUSH)).add(0.0, 0.4, 0.0));
			near.syncVelocity = true;
		}
	}
}
