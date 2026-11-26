package com.jaymospowers.power.web;

import com.jaymospowers.core.fx.MagicFx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Two-line dive slam: in the air, look steeply down ({@link #LOOK_DOWN} degrees or more) at the ground ahead and press Zip
 * with Sneak held, and both webs shoot into it ({@link WebPose#DIVE_SLAM}). They haul you down at {@link #SPEED}, the two lines
 * drawn the whole way, into a slam that hurts everything near the landing and webs it. From any height at all
 * ({@link #LEAST_DROP} blocks and up); the harder you land, the more it hurts and the wider it breaks the ground
 * ({@link WebImpacts#smash}). No fall damage on the way. Zip's own cooldown applies.
 */
public final class WebDiveSlam {
	/** How steeply down the look must be (degrees below flat), how far the webs reach (blocks), and the least drop that is worth it. */
	public static final float LOOK_DOWN = 55.0F;
	public static final double REACH = 64.0, LEAST_DROP = 2.5;
	/** How fast the lines haul you (blocks/tick), and the longest dive (ticks). */
	static final double SPEED = 2.4;
	static final int MAX_TICKS = 100;
	/** The slam: how far it reaches (blocks), its damage (a base and more per block fallen, capped), the throw out and up, and the web each foe gets. */
	static final double RADIUS = 4.5, OUT = 0.5, UP = 0.7;
	static final float BASE = 7.0F, PER_BLOCK = 0.35F, MOST = 22.0F;
	static final int WEB_TICKS = 70, STRAYS = 12;

	private record Live(Vec3 anchor, long start, double fromY) {
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();

	private WebDiveSlam() {
	}

	/**
	 * Zip with Sneak held, in the air, looking steeply down at ground in reach: starts the dive. False if it isn't one (the
	 * ordinary zip goes on).
	 */
	static boolean start(ServerPlayer player) {
		if (LIVE.containsKey(player.getUUID()) || !player.isShiftKeyDown() || player.onGround() || player.isInWater() || player.isInLava()
			|| player.isFallFlying() || player.getAbilities().flying || player.isPassenger() || player.getXRot() < LOOK_DOWN
			|| WebPower.swingingNow(player) || WebPower.pulling(player) || WebPower.ragdolled(player) || WebSymbiote.driving(player)) {
			return false;
		}
		Vec3 eye = player.getEyePosition();
		double reach = REACH * WebSymbiote.reachScale(player) * WebShooters.rangeScale(player, WebNet.MAIN);
		BlockHitResult hit = WebAnchors.clip(player, eye, eye.add(player.getViewVector(1.0F).scale(reach)));
		if (hit.getType() != HitResult.Type.BLOCK || hit.getDirection() != net.minecraft.core.Direction.UP || player.getY() - hit.getLocation().y < LEAST_DROP
			|| !WebStamina.pay(player, WebStamina.ZIP)) {
			return false;
		}
		Vec3 anchor = hit.getLocation();
		LIVE.put(player.getUUID(), new Live(anchor, player.level().getGameTime(), player.getY()));
		WebPower.pose(player, WebPose.DIVE_SLAM, 1);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0F, 1.2F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_2.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, anchor.x, anchor.y, anchor.z, 10, 0.3, 0.1, 0.3, 0.02);
		return true;
	}

	/** Whether {@code player} is being hauled down now (no movement check, no fall damage). */
	static boolean diving(ServerPlayer player) {
		return LIVE.containsKey(player.getUUID());
	}

	/** Every tick, per web slinger. */
	static void tick(ServerPlayer player) {
		Live live = LIVE.get(player.getUUID());
		if (live == null) {
			return;
		}
		long t = player.level().getGameTime() - live.start();
		if (!player.isAlive() || player.isSpectator() || player.isInWater() || player.isInLava() || player.isPassenger() || t > MAX_TICKS) {
			end(player);
			return;
		}
		Vec3 to = live.anchor().subtract(player.position());
		if (t >= 2 && (player.onGround() || to.length() < 1.6 || to.y > 0.0)) {
			land(player, live);
			return;
		}
		player.resetFallDistance();
		WebHaul.push(player, to.normalize().scale(SPEED));
		// both webs, a hand's width apart where they go in, drawn all the way down
		Vec3 side = new Vec3(-to.z, 0.0, to.x);
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(0.5, 0.0, 0.0) : side.normalize().scale(0.5);
		WebStrikeLines.show(player, live.anchor().add(side), -1, live.anchor().subtract(side), -1);
		if (t % 3 == 0) {
			WebPower.pose(player, WebPose.DIVE_SLAM, 1);
		}
	}

	private static void end(ServerPlayer player) {
		LIVE.remove(player.getUUID());
		WebStrikeLines.clear(player);
	}

	/** The landing: everything near hurt, thrown and webbed, the ground broken, a ring of dust. */
	private static void land(ServerPlayer player, Live live) {
		ServerLevel level = player.level();
		double dropped = Math.max(0.0, live.fromY() - player.getY());
		double speed = Math.min(2.2, 0.8 + dropped * 0.02);
		float damage = Math.min(MOST, BASE + PER_BLOCK * (float)dropped);
		Vec3 at = player.position();
		end(player);
		player.resetFallDistance();
		List<LivingEntity> hit = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(RADIUS, 1.5, RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof TamableAnimal pet && pet.isOwnedBy(player))
				&& (!(e instanceof Player other) || player.canHarmPlayer(other)))) {
			if (e.position().distanceTo(at) <= RADIUS + e.getBbWidth() * 0.5) {
				hit.add(e);
			}
		}
		for (LivingEntity e : hit) {
			double d = e.position().distanceTo(at);
			e.setInvulnerableTime(0);
			e.hurtServer(level, player.damageSources().mobAttack(player), (float)(damage * (1.0 - 0.5 * d / RADIUS)));
			if (e.isAlive()) {
				Vec3 flat = e.position().subtract(at).multiply(1.0, 0.0, 1.0);
				Vec3 out = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize().scale(OUT);
				WebHaul.push(e, out.add(0.0, UP, 0.0));
				Webbed.wrap(e, WEB_TICKS);
				level.sendParticles(ParticleTypes.ITEM_COBWEB, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 10, 0.3, 0.4, 0.3, 0.05);
			}
		}
		WebTraps.scatter(level, at, RADIUS, STRAYS, false);
		BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5, at.z));
		if (ground.isAir()) {
			ground = Blocks.DIRT.defaultBlockState();
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.1, at.z, (int)Math.min(160, 50 + 60 * speed),
			RADIUS * 0.4, 0.05, RADIUS * 0.4, 0.2);
		MagicFx.ring(level, at, (float)RADIUS, 0xF2F0E4);
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.2, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND, SoundSource.PLAYERS, (float)Math.min(1.4, 0.8 + speed * 0.3), 0.8F);
		com.jaymospowers.core.Shakes.heavy(player, at, 14.0, (float)Math.min(9.0, 3.0 + speed * 3.0));
		WebImpacts.smash(level, at.add(0.0, -0.5, 0.0), speed, player);
		WebPower.pose(player, WebPose.HERO_LAND, 1);
	}

	static void forget(ServerPlayer player) {
		LIVE.remove(player.getUUID());
	}

	static void clearAll() {
		LIVE.clear();
	}
}
