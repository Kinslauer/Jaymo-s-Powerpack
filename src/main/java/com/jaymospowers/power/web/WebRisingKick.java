package com.jaymospowers.power.web;

import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Rising drop kick: tap Yank (B) on a foe that is already webbed and both webs pull it in, you flip upside down
 * ({@link WebPose#DROP_KICK}) and drop-kick it up into the air, then burst webbing out around you. (Hold B on a webbed foe and it
 * is led on the line as ever.) The pull steers the foe to just ahead of you for {@link #KICK_AT} ticks; if it is still within
 * {@link #REACH} then, the kick lands.
 */
public final class WebRisingKick {
	/** The tick the kick lands at, how far the foe may be then (blocks), and how close ahead it is hauled (blocks). */
	static final int KICK_AT = 9;
	static final double REACH = 4.5, AHEAD = 1.7;
	/** The kick: its damage, how hard it throws the foe up and ahead (blocks/tick), and how high you rise. */
	static final float DAMAGE = 9.0F;
	static final double THROW_UP = 1.15, THROW_AHEAD = 0.25, RISE = 0.5;
	/** The burst: how far it webs (blocks), for how long (ticks), and the strays of cobweb left on the ground. */
	static final double BURST = 4.0;
	static final int BURST_TICKS = 60, STRAYS = 8;

	private record Live(int target, long start) {
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();

	private WebRisingKick() {
	}

	/** Whether a Yank tap on {@code foe} is the rising kick: webbed, and light enough to haul. */
	static boolean applies(ServerPlayer player, LivingEntity foe) {
		return !LIVE.containsKey(player.getUUID()) && foe.isAlive() && Webbed.layers(foe) > 0 && WebPin.pinnable(player, foe);
	}

	static void start(ServerPlayer player, LivingEntity foe) {
		LIVE.put(player.getUUID(), new Live(foe.getId(), player.level().getGameTime()));
		WebPower.pose(player, WebPose.DROP_KICK, player.getRandom().nextBoolean() ? 1 : -1);
		ServerPlayNetworking.send(player, new WebNet.Nudge(new Vec3(player.getDeltaMovement().x * 0.3, 0.3, player.getDeltaMovement().z * 0.3)));
		player.level().playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	/** Every tick, per web slinger. */
	static void tick(ServerPlayer player) {
		Live live = LIVE.get(player.getUUID());
		if (live == null) {
			return;
		}
		ServerLevel level = player.level();
		long t = level.getGameTime() - live.start();
		if (!player.isAlive() || player.isSpectator() || !(level.getEntity(live.target()) instanceof LivingEntity foe) || !foe.isAlive()) {
			LIVE.remove(player.getUUID());
			return;
		}
		if (t < KICK_AT) {
			Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
			look = look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize();
			WebHaul.steer(foe, player.position().add(look.scale(AHEAD)).add(0.0, 0.2, 0.0));
			WebStrikeLines.toEntity(player, foe);
			return;
		}
		LIVE.remove(player.getUUID());
		if (foe.distanceTo(player) > REACH) {
			return;
		}
		Vec3 look = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		look = look.lengthSqr() < 1.0E-4 ? Vec3.ZERO : look.normalize();
		foe.setInvulnerableTime(0);
		foe.hurtServer(level, player.damageSources().mobAttack(player), DAMAGE);
		if (foe.isAlive()) {
			WebHaul.push(foe, look.scale(THROW_AHEAD).add(0.0, THROW_UP, 0.0));
		}
		ServerPlayNetworking.send(player, new WebNet.Nudge(new Vec3(0.0, RISE, 0.0)));
		burst(level, player, foe);
	}

	/** Webbing bursts out of you: every foe near is wrapped ({@link WebStrength#cocoon}'s layer of web). */
	private static void burst(ServerLevel level, ServerPlayer player, LivingEntity kicked) {
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(BURST),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof TamableAnimal pet && pet.isOwnedBy(player))
				&& (!(e instanceof Player other) || player.canHarmPlayer(other)))) {
			if (e.position().distanceTo(at) <= BURST + e.getBbWidth() * 0.5 || e == kicked) {
				Webbed.wrap(e, BURST_TICKS);
				level.sendParticles(ParticleTypes.ITEM_COBWEB, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 10, 0.3, 0.4, 0.3, 0.05);
			}
		}
		WebTraps.scatter(level, player.position(), BURST, STRAYS, false);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 50, BURST * 0.3, 0.5, BURST * 0.3, 0.2);
		MagicFx.blast(level, at, 1.5F, 0xF2F0E4);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.3F, 0.8F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.COBWEB_PLACE, SoundSource.PLAYERS, 1.2F, 0.6F);
		com.jaymospowers.core.Shakes.send(player, 0.6F, at);
	}

	static void forget(ServerPlayer player) {
		LIVE.remove(player.getUUID());
	}

	static void clearAll() {
		LIVE.clear();
	}
}
