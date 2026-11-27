package com.jaymospowers.power.web;

import com.jaymospowers.core.Carry;
import com.jaymospowers.mortal.AirCombo;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Zip strike: Zip (Z) with a webbed foe in sights flies you at it feet first, and the kick lands as you arrive
 * ({@link WebPose#ZIP_KICK}). A foe wrapped in {@link Webbed#FULL} layers can be zipped from as far as a block can
 * ({@link WebPower#ZIP_RANGE}); one with fewer, only from {@link #RANGE}. Each layer on it adds to the kick. If you launched the foe
 * into the air a moment ago ({@link AirCombo#launchedBy}), Zip reaches it whether it is webbed or not and, on arrival, you
 * stand on its back instead ({@link WebSurf}). Also the answer to an arrow: {@link #answer}.
 */
public final class WebZipStrike {
	/** How far a zip reaches a foe with fewer than {@link Webbed#FULL} layers (blocks, times the symbiote's reach and the shooters'). */
	public static final double RANGE = 24.0;
	/** The kick: its damage, and more for each layer of web on the foe; how hard (blocks/tick) it throws the foe away and up. */
	static final float DAMAGE = 7.0F, PER_LAYER = 1.5F;
	static final double KNOCK = 1.5, LIFT = 0.35, REBOUND = 0.4;
	/** The kick lands when you are this close to the foe's edge (blocks), and the zip is given this long past its own length (ticks). */
	static final double HIT_REACH = 2.4;
	static final int SLACK_TICKS = 14;
	/** The answer to an arrow: how far the shooter may be (blocks) and how much harder it is hit. */
	public static final double ANSWER_RANGE = 40.0;
	static final float ANSWER_MORE = 1.25F;

	private record Live(int target, long until, boolean answer) {
	}

	private static final Map<UUID, Live> LIVE = new HashMap<>();

	private WebZipStrike() {
	}

	/** A foe {@code player} may zip at: alive, theirs to fight, and webbed or launched by them. */
	private static boolean strikable(ServerPlayer player, @Nullable Entity entity) {
		if (!(entity instanceof LivingEntity foe) || !foe.isAlive() || foe == player || foe.isSpectator() || Carry.isCarried(foe)
			|| foe instanceof TamableAnimal pet && pet.isOwnedBy(player) || foe instanceof Player other && !player.canHarmPlayer(other)) {
			return false;
		}
		return Webbed.layers(foe) > 0 || AirCombo.launchedBy(foe, player);
	}

	/** How far {@code foe} can be zipped at from here (blocks). */
	private static double reach(ServerPlayer player, LivingEntity foe) {
		double scale = WebSymbiote.reachScale(player) * WebShooters.rangeScale(player, WebNet.MAIN);
		return (Webbed.layers(foe) >= Webbed.FULL ? WebPower.ZIP_RANGE : RANGE) * scale;
	}

	/** Zip pressed: if a webbed (or just launched) foe is in sights and in reach, the zip goes at it. False if none is. */
	static boolean start(ServerPlayer player) {
		if (LIVE.containsKey(player.getUUID())) {
			return false;
		}
		double most = WebPower.ZIP_RANGE * WebSymbiote.reachScale(player) * WebShooters.rangeScale(player, WebNet.MAIN);
		Entity seen = WebLead.aim(player, most);
		if (!strikable(player, seen)) {
			return false;
		}
		LivingEntity foe = (LivingEntity)seen;
		if (foe.distanceTo(player) > reach(player, foe) || !WebStamina.pay(player, WebStamina.ZIP)) {
			return false;
		}
		go(player, foe, false);
		return true;
	}

	/**
	 * Spider-Sense dodged an arrow from {@code shooter} a moment ago and the slinger clicked: turn, zip at whoever shot it and
	 * hit them ({@link WebPose#SENSE_TURN} into the kick). False if the shooter is gone, out of {@link #ANSWER_RANGE} or out of sight.
	 */
	static boolean answer(ServerPlayer player, LivingEntity shooter) {
		if (LIVE.containsKey(player.getUUID()) || !shooter.isAlive() || shooter == player || shooter.distanceTo(player) > ANSWER_RANGE
			|| !player.hasLineOfSight(shooter) || shooter instanceof Player other && !player.canHarmPlayer(other)
			|| shooter instanceof TamableAnimal pet && pet.isOwnedBy(player)) {
			return false;
		}
		WebPower.pose(player, WebPose.SENSE_TURN, player.getRandom().nextBoolean() ? 1 : -1);
		go(player, shooter, true);
		return true;
	}

	private static void go(ServerPlayer player, LivingEntity foe, boolean answer) {
		ServerLevel level = player.level();
		Vec3 centre = foe.getBoundingBox().getCenter();
		int maxTicks = Mth.ceil(centre.distanceTo(player.position()) / WebPower.ZIP_SPEED) + 12;
		WebPower.startPull(player, new WebNet.Pull(centre, foe.position(), foe.getId(), maxTicks, (byte)-1), new WebLine(WebLine.YANK, centre, foe.getId()));
		LIVE.put(player.getUUID(), new Live(foe.getId(), level.getGameTime() + maxTicks + SLACK_TICKS, answer));
		if (!answer) {
			WebPower.pose(player, WebPose.ZIP_KICK, 1);
		}
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0F, 1.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_1, SoundSource.PLAYERS, 0.7F, 1.4F);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, centre.x, centre.y, centre.z, 8, 0.2, 0.2, 0.2, 0.02);
	}

	/** Every tick, per web slinger: the zip's arrival. */
	static void tick(ServerPlayer player) {
		Live live = LIVE.get(player.getUUID());
		if (live == null) {
			return;
		}
		ServerLevel level = player.level();
		if (!player.isAlive() || player.isSpectator() || level.getGameTime() > live.until() || !(level.getEntity(live.target()) instanceof LivingEntity foe)
			|| !foe.isAlive()) {
			LIVE.remove(player.getUUID());
			return;
		}
		if (player.distanceTo(foe) - foe.getBbWidth() * 0.5 > HIT_REACH) {
			return;
		}
		LIVE.remove(player.getUUID());
		if (!live.answer() && !(foe instanceof Player) && AirCombo.launchedBy(foe, player)) {
			WebSurf.begin(player, foe);
			return;
		}
		kick(level, player, foe, live.answer());
	}

	private static void kick(ServerLevel level, ServerPlayer player, LivingEntity foe, boolean answer) {
		int layers = Webbed.layers(foe);
		float damage = (DAMAGE + PER_LAYER * layers) * (answer ? ANSWER_MORE : 1.0F);
		WebPower.pose(player, WebPose.ZIP_KICK, 2);
		foe.setInvulnerableTime(0);
		foe.hurtServer(level, player.damageSources().mobAttack(player), damage);
		Vec3 away = foe.position().subtract(player.position()).multiply(1.0, 0.0, 1.0);
		away = away.lengthSqr() < 1.0E-4 ? player.getLookAngle().multiply(1.0, 0.0, 1.0) : away.normalize();
		if (foe.isAlive()) {
			WebHaul.push(foe, away.scale(KNOCK).add(0.0, LIFT, 0.0));
		}
		// off the foe's chest, back and up
		ServerPlayNetworking.send(player, new WebNet.Nudge(away.scale(-REBOUND).add(0.0, 0.3, 0.0)));
		Vec3 at = foe.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.15);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.3F, 0.8F);
		com.jaymospowers.core.Shakes.send(player, 0.5F, at);
	}

	/** Whether the slinger is zipping at a foe for a strike right now (so the zip does not count as an ordinary one). */
	static boolean flying(ServerPlayer player) {
		return LIVE.containsKey(player.getUUID());
	}

	static void forget(ServerPlayer player) {
		LIVE.remove(player.getUUID());
	}

	static void clearAll() {
		LIVE.clear();
	}
}
