package com.jaymospowers.power.web;

import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Goo spit: from the maw (the big suit, or Living Armor with its mask on) the key spits a dark glob in a short arc. It
 * blinds and slows what it hits and bites a little; mobs lose their target and their nerve for a moment. Where it
 * lands it leaves a patch of goo on the ground that slows whatever walks through it (not the spitter). The glob is
 * simulated here, not an entity: it is drawn each tick as a {@link MagicFx} orb with a trail of miasma.
 */
public final class WebSpit {
	// ================================================================================================================
	// Tuning
	/** Blocks a tick at the lips, the pull of gravity (blocks a tick, each tick) and the ticks it flies: about 16 blocks level. */
	static final double SPEED = 0.9, GRAVITY = 0.03;
	static final int FLIGHT_TICKS = 18;
	/** A glob's size for the eye (blocks) and how far its box reaches for a target. */
	static final float GLOB_RADIUS = 0.2F;
	static final double GLOB_REACH = 0.3;
	/** A hit: damage, blindness (ticks), slowness (ticks, amplifier), and how long a mob forgets its target and flees. */
	static final float DAMAGE = 2.0F;
	static final int BLIND_TICKS = 60, SLOW_TICKS = 100, SLOW_LEVEL = 1, SCARE_TICKS = 40;
	/** The patch: radius (blocks), life (ticks), the slowness it refreshes (ticks, amplifier) and how often. */
	static final double PATCH_RADIUS = 1.6;
	static final int PATCH_TICKS = 160, PATCH_SLOW_TICKS = 30, PATCH_SLOW_LEVEL = 1, PATCH_EVERY = 10;
	/** The wait after a spit (ticks). */
	static final int COOLDOWN = 120;
	/** How far down from where it lands it looks for ground (blocks). */
	static final double GROUND_REACH = 3.0;
	// ================================================================================================================

	private static final class Glob {
		final UUID owner;
		final ResourceKey<Level> dimension;
		Vec3 pos;
		Vec3 vel;
		int age;

		Glob(UUID owner, ResourceKey<Level> dimension, Vec3 pos, Vec3 vel) {
			this.owner = owner;
			this.dimension = dimension;
			this.pos = pos;
			this.vel = vel;
		}
	}

	private record Patch(UUID owner, ResourceKey<Level> dimension, Vec3 center, long until, int color) {
	}

	private static final List<Glob> GLOBS = new ArrayList<>();
	private static final List<Patch> PATCHES = new ArrayList<>();

	private WebSpit() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WebSpit::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			GLOBS.clear();
			PATCHES.clear();
		});
	}

	/** The key: spit. */
	static void fire(ServerPlayer player) {
		if (!WebGoo.maw(player)) {
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.web_goo.no_maw"));
			return;
		}
		if (!WebGoo.ready(player, WebGoo.SPIT) || WebPuddle.puddled(player) || !WebGoo.pay(player, WebGoo.SPIT_COST)) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle();
		Vec3 lips = player.getEyePosition().add(look.scale(0.4)).subtract(0.0, 0.15, 0.0);
		GLOBS.add(new Glob(player.getUUID(), level.dimension(), lips, look.scale(SPEED).add(player.getDeltaMovement().multiply(1.0, 0.0, 1.0).scale(0.5))));
		WebGoo.cool(player, WebGoo.SPIT, COOLDOWN);
		level.playSound(null, lips.x, lips.y, lips.z, SoundEvents.LLAMA_SPIT, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, lips.x, lips.y, lips.z, SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.8F, 0.5F);
		Shakes.send(player, 0.2F);
	}

	private static int color(MinecraftServer server, UUID owner) {
		ServerPlayer player = server.getPlayerList().getPlayer(owner);
		return WebSymbioteStrains.color(player == null ? WebSymbioteStrain.BLACK : WebSymbiote.state(player).kind());
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Glob> it = GLOBS.iterator(); it.hasNext(); ) {
			Glob glob = it.next();
			ServerLevel level = server.getLevel(glob.dimension);
			if (level == null || !level.isLoaded(BlockPos.containing(glob.pos))) {
				it.remove();
				continue;
			}
			if (step(server, level, glob)) {
				it.remove();
			}
		}
		for (Iterator<Patch> it = PATCHES.iterator(); it.hasNext(); ) {
			Patch patch = it.next();
			ServerLevel level = server.getLevel(patch.dimension());
			if (level == null || level.getGameTime() >= patch.until()) {
				it.remove();
				continue;
			}
			if (level.getGameTime() % PATCH_EVERY == 0) {
				soak(level, patch);
			}
		}
	}

	/** Moves the glob a tick; true when it is spent (hit something, landed, or out of flight). */
	private static boolean step(MinecraftServer server, ServerLevel level, Glob glob) {
		ServerPlayer owner = server.getPlayerList().getPlayer(glob.owner);
		int color = color(server, glob.owner);
		Vec3 from = glob.pos;
		Vec3 to = from.add(glob.vel);
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner == null ? net.minecraft.world.phys.shapes.CollisionContext.empty() : net.minecraft.world.phys.shapes.CollisionContext.of(owner)));
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
		LivingEntity target = null;
		double best = from.distanceToSqr(end);
		AABB sweep = new AABB(from, end).inflate(GLOB_REACH + 1.0);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, sweep,
			e -> e.isAlive() && !e.isSpectator() && e != owner && !(e instanceof ArmorStand) && !WebPuddle.puddled(e))) {
			var hit = living.getBoundingBox().inflate(GLOB_REACH).clip(from, end);
			if (hit.isPresent() && from.distanceToSqr(hit.get()) < best) {
				best = from.distanceToSqr(hit.get());
				target = living;
				end = hit.get();
			}
		}
		glob.pos = end;
		draw(level, end, color);
		if (target != null) {
			splat(level, owner, target, end, color);
			return true;
		}
		if (block.getType() != HitResult.Type.MISS) {
			land(level, owner, end, color);
			return true;
		}
		glob.vel = glob.vel.subtract(0.0, GRAVITY, 0.0);
		if (++glob.age >= FLIGHT_TICKS) {
			land(level, owner, end, color);
			return true;
		}
		return false;
	}

	private static void draw(ServerLevel level, Vec3 at, int color) {
		MagicFx.orb(level, at, GLOB_RADIUS, color, 2);
		level.sendParticles(MagicFx.miasma(color, 0.5F), at.x, at.y, at.z, 1, 0.05, 0.05, 0.05, 0.0);
	}

	private static void splat(ServerLevel level, ServerPlayer owner, LivingEntity target, Vec3 at, int color) {
		target.hurtServer(level, owner == null ? level.damageSources().generic() : level.damageSources().playerAttack(owner), DAMAGE);
		target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLIND_TICKS, 0, false, true));
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, SLOW_LEVEL, false, true));
		if (target instanceof Mob mob) {
			mob.setTarget(null);
			if (mob instanceof PathfinderMob && !(mob instanceof WitherBoss) && !(mob instanceof EnderDragon) && owner != null) {
				WebFear.scare(mob, owner, level.getGameTime() + SCARE_TICKS);
			}
		}
		land(level, owner, at, color);
	}

	/** The glob bursts at {@code at}; if there is ground near, a patch is left on it. */
	private static void land(ServerLevel level, ServerPlayer owner, Vec3 at, int color) {
		MagicFx.blast(level, at, 0.9F, color);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.6F);
		BlockHitResult down = level.clip(new ClipContext(at.add(0.0, 0.3, 0.0), at.subtract(0.0, GROUND_REACH, 0.0), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
		if (down.getType() == HitResult.Type.MISS) {
			return;
		}
		Vec3 ground = down.getLocation().add(0.0, 0.02, 0.0);
		PATCHES.add(new Patch(owner == null ? new UUID(0L, 0L) : owner.getUUID(), level.dimension(), ground, level.getGameTime() + PATCH_TICKS, color));
		MagicFx.pool(level, ground, (float)PATCH_RADIUS, color, PATCH_TICKS);
	}

	/** Slows whatever stands on the patch (not the spitter). */
	private static void soak(ServerLevel level, Patch patch) {
		AABB flat = new AABB(patch.center().x - PATCH_RADIUS, patch.center().y - 0.3, patch.center().z - PATCH_RADIUS,
			patch.center().x + PATCH_RADIUS, patch.center().y + 1.0, patch.center().z + PATCH_RADIUS);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, flat, e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(patch.owner()))) {
			if (living.getY() - patch.center().y < 0.7 && Math.hypot(living.getX() - patch.center().x, living.getZ() - patch.center().z) <= PATCH_RADIUS) {
				living.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PATCH_SLOW_TICKS, PATCH_SLOW_LEVEL, false, false));
			}
		}
		level.sendParticles(MagicFx.miasma(patch.color(), 0.5F), patch.center().x + (level.getRandom().nextDouble() - 0.5) * PATCH_RADIUS,
			patch.center().y + 0.1, patch.center().z + (level.getRandom().nextDouble() - 0.5) * PATCH_RADIUS, 1, 0.0, 0.05, 0.0, 0.0);
	}
}
