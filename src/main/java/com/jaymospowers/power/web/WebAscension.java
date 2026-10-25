package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Fate;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * The web slinger's ascension. It comes from the swinging itself: the server tallies the distance you travel while
 * airborne on a swing web (from your position each tick: movement is client-authoritative), and once you've swung
 * {@link #ASCEND_DISTANCE} blocks with every ability mastered, the power ascends.
 *
 * <p>Spider-Sense grows with the power: hits miss you entirely, with a quick sidestep, as many as you have sense
 * charges ({@link #senseCharges}: one at the start, more as abilities unlock, {@link #DODGE_CHARGES} once ascended),
 * each charge back after {@link #DODGE_COOLDOWN} ticks. Right after a dodge, right click counters ({@link WebCounter}).
 * Ascended, creatures about to hurt you are outlined red on your screen too (the server tells your client which ones;
 * the client adds swelling creepers and drawn bows). The ascended ability is <b>Web Strike</b> (the ultimate key): zip
 * to whatever's under the crosshair and kick it.
 */
public final class WebAscension {
	/** Blocks swung (airborne, with a swing web out) to ascend. */
	public static final double ASCEND_DISTANCE = 2000.0;
	/** A single tick's movement longer than this is a teleport, not a swing. */
	private static final double MAX_STEP = 6.0;
	/** The tally is written back to the saved counter this often. */
	private static final int FLUSH_TICKS = 20;
	/** Whispers along the way (fractions of the distance), each heard once. */
	private static final double[] HINT_AT = {0.5, 0.85};

	/** Spider-Sense: how far it reaches, how often the server refreshes it, and how often a hit can be dodged. */
	public static final double SENSE_RANGE = 16.0;
	public static final int SENSE_TICKS = 10;
	public static final int DODGE_COOLDOWN = 300;
	/** Ascended, Spider-Sense holds this many dodges, each recharging on its own ({@link #DODGE_COOLDOWN}). */
	public static final int DODGE_CHARGES = 7;
	/** Before that, by abilities unlocked (0, a fresh awakening, to all 4). */
	public static final int[] CHARGES_BY_UNLOCKED = {2, 2, 3, 4, 5};
	/** The dodge's sidestep (blocks/tick sideways, and up). */
	private static final double DODGE_SIDESTEP = 0.85;
	private static final double DODGE_HOP = 0.22;

	/** Web Strike: reach, cooldown, damage (at least the minimum; more the faster you arrive), knockback. */
	public static final double STRIKE_RANGE = 32.0;
	/** (0.16.24) The strike's film runs alongside you this much past the pull (ticks). */
	static final int STRIKE_FILM_AFTER = 8;
	public static final int STRIKE_COOLDOWN = 160;
	public static final float STRIKE_MIN_DAMAGE = 10.0F;
	public static final float STRIKE_DAMAGE_PER_SPEED = 8.0F;
	public static final double STRIKE_KNOCKBACK = 2.2;
	public static final double STRIKE_LIFT = 0.55;
	/** The kick lands when you're this close to the target's edge. */
	private static final double STRIKE_REACH = 2.2;

	/** Blocks swung so far (saved, kept through death). */
	public static final AttachmentType<Double> SWUNG = AttachmentRegistry.create(JaymosPowers.id("web_swung"),
		builder -> builder.persistent(Codec.DOUBLE).copyOnDeath());
	/** When Web Strike is ready again (synced to the owner, for the HUD). */
	public static final AttachmentType<Long> STRIKE_READY = AttachmentRegistry.create(JaymosPowers.id("web_strike"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.targetOnly()));
	private static final AttachmentType<Live> LIVE = AttachmentRegistry.create(JaymosPowers.id("web_ascension_live"));

	private static final class Live {
		Vec3 lastPos;
		double pending;
		/** Speed over the last few ticks (blocks/tick), measured here. */
		final double[] speeds = new double[3];
		/** When each dodge charge is ready again. */
		final long[] dodgeReadyAt = new long[DODGE_CHARGES];
		int strikeTarget = -1;
		int strikeTicks;
		boolean sensing;
	}

	private WebAscension() {
	}

	static void register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(WebAscension::allowDamage);
		WebCounter.register();
	}

	/** Sense charges (dodges held at once): they grow with the power, to the most once it's ascended. */
	public static int senseCharges(Player player) {
		return Fate.isAscended(player) ? DODGE_CHARGES : CHARGES_BY_UNLOCKED[Mastery.unlocked(player)];
	}

	private static Live live(ServerPlayer player) {
		Live live = player.getAttached(LIVE);
		if (live == null) {
			live = new Live();
			player.setAttached(LIVE, live);
		}
		return live;
	}

	public static double swung(ServerPlayer player) {
		Double swung = player.getAttached(SWUNG);
		return swung == null ? 0.0 : swung;
	}

	static void onLost(ServerPlayer player) {
		player.removeAttached(SWUNG);
		player.removeAttached(STRIKE_READY);
		player.removeAttached(LIVE);
		WebCounter.clear(player);
	}

	/** Every tick, for a web slinger. {@code swinging}: a swing web is stuck right now. */
	static void tick(ServerPlayer player, boolean swinging) {
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}
		Live live = live(player);
		Vec3 pos = player.position();
		double step = live.lastPos == null ? 0.0 : pos.distanceTo(live.lastPos);
		live.lastPos = pos;
		System.arraycopy(live.speeds, 0, live.speeds, 1, live.speeds.length - 1);
		live.speeds[0] = step > MAX_STEP ? 0.0 : step;
		if (swinging && !player.onGround() && step <= MAX_STEP && !player.getAbilities().flying) {
			live.pending += step;
		}
		if (live.pending > 0.0 && player.tickCount % FLUSH_TICKS == 0) {
			addSwung(player, live.pending);
			live.pending = 0.0;
		}
		if (live.strikeTarget >= 0) {
			tickStrike(player, live);
		}
		WebCounter.tick(player);
		if (player.tickCount % SENSE_TICKS == 0) {
			sense(player, live);
		}
	}

	/** Adds to the tally, with a whisper or two along the way, and ascends at the end of it (if the power's ready). */
	public static void addSwung(ServerPlayer player, double blocks) {
		double before = swung(player);
		double after = before + blocks;
		player.setAttached(SWUNG, after);
		for (int i = 0; i < HINT_AT.length; i++) {
			double mark = HINT_AT[i] * ASCEND_DISTANCE;
			if (before < mark && after >= mark && !Fate.isAscended(player)) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.web_ascend." + i).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 1.8F);
			}
		}
		if (after >= ASCEND_DISTANCE && Fate.canAscend(player)) {
			Fate.ascend(player);
		}
	}

	// ---- Spider-Sense ------------------------------------------------------------------------------------------------

	/** Tells the client which creatures are after them (mob targets aren't synced, so the server has to). */
	private static void sense(ServerPlayer player, Live live) {
		if (!Fate.isAscended(player)) {
			if (live.sensing) {
				live.sensing = false;
				ServerPlayNetworking.send(player, new WebNet.Sense(List.of()));
			}
			return;
		}
		List<Integer> ids = new ArrayList<>();
		for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(SENSE_RANGE),
			m -> m.isAlive() && m.getTarget() == player)) {
			if (mob.distanceToSqr(player) <= SENSE_RANGE * SENSE_RANGE) {
				ids.add(mob.getId());
			}
		}
		if (!ids.isEmpty() || live.sensing) {
			ServerPlayNetworking.send(player, new WebNet.Sense(ids));
		}
		live.sensing = !ids.isEmpty();
	}

	/** The first hit every so often never lands: a tingle, and you're already a step to the side. */
	private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity instanceof ServerPlayer player) || !Powers.has(player, WebPower.INSTANCE)) {
			return true;
		}
		Entity attacker = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
		if (attacker == null || attacker == player || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || amount <= 0.0F) {
			return true;
		}
		Live live = live(player);
		ServerLevel level = player.level();
		long now = level.getGameTime();
		int charges = senseCharges(player);
		int charge = -1;
		for (int i = 0; i < charges && charge < 0; i++) {
			if (now >= live.dodgeReadyAt[i]) {
				charge = i;
			}
		}
		if (charge < 0) {
			return true;
		}
		live.dodgeReadyAt[charge] = now + DODGE_COOLDOWN;
		WebCounter.opened(player, attacker);
		// out of the way, square to where it came from, with one of four moves
		Entity from = source.getDirectEntity() != null ? source.getDirectEntity() : attacker;
		Vec3 away = player.position().subtract(from.position());
		Vec3 flat = new Vec3(away.x, 0.0, away.z);
		flat = flat.lengthSqr() < 1.0E-4 ? player.getViewVector(1.0F).horizontal() : flat.normalize();
		int sideSign = player.getRandom().nextBoolean() ? 1 : -1;
		Vec3 side = new Vec3(-flat.z, 0.0, flat.x).scale(sideSign);
		int kind = player.getRandom().nextInt(WebPose.DODGES);
		Vec3 velocity = switch (kind) {
			// back and up, over in a flip, landing in a crouch
			case WebPose.BACKFLIP -> flat.scale(DODGE_SIDESTEP * 0.9).add(0.0, DODGE_HOP * 2.2, 0.0);
			// low and sideways along the ground
			case WebPose.ROLL -> side.scale(DODGE_SIDESTEP * 1.1).add(flat.scale(DODGE_SIDESTEP * 0.2));
			// barely a step: the blow just misses
			case WebPose.WEAVE -> side.scale(DODGE_SIDESTEP * 0.45).add(0.0, 0.05, 0.0);
			default -> side.scale(DODGE_SIDESTEP).add(flat.scale(DODGE_SIDESTEP * 0.3)).add(0.0, DODGE_HOP, 0.0);
		};
		WebPower.pose(player, kind, sideSign);
		ServerPlayNetworking.send(player, new WebNet.Nudge(velocity));
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.9F);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 2.0F);
		level.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getEyeY(), player.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
		return false;
	}

	// ---- Web Strike --------------------------------------------------------------------------------------------------

	/** The ultimate key: zip to the creature under the crosshair, and kick it when you get there. */
	static void ultimate(ServerPlayer player, boolean down) {
		if (!down) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Long ready = player.getAttached(STRIKE_READY);
		if (ready != null && now < ready) {
			return;
		}
		Entity target = WebPower.entityInSight(player, STRIKE_RANGE);
		if (target == null || !(target instanceof LivingEntity)) {
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.4F, 2.0F);
			return;
		}
		Vec3 center = target.getBoundingBox().getCenter();
		int maxTicks = Mth.ceil(center.distanceTo(player.position()) / WebPower.ZIP_SPEED) + 12;
		WebPower.startPull(player, new WebNet.Pull(center, target.position(), target.getId(), maxTicks, (byte)-1),
			new WebLine(WebLine.YANK, center, target.getId()));
		Live live = live(player);
		live.strikeTarget = target.getId();
		com.jaymospowers.core.UltimateFilm.play(player, com.jaymospowers.core.UltimateFilm.CHASE, maxTicks + STRIKE_FILM_AFTER, center, target, 0.0);
		live.strikeTicks = maxTicks + 10;
		player.setAttached(STRIKE_READY, now + STRIKE_COOLDOWN);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 1.0F, 1.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_RIPTIDE_2.value(), SoundSource.PLAYERS, 0.8F, 1.4F);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, center.x, center.y, center.z, 8, 0.2, 0.2, 0.2, 0.02);
	}

	private static void tickStrike(ServerPlayer player, Live live) {
		Entity target = player.level().getEntity(live.strikeTarget);
		if (target == null || !target.isAlive() || --live.strikeTicks <= 0) {
			live.strikeTarget = -1;
			return;
		}
		double gap = player.distanceTo(target) - target.getBbWidth() * 0.5;
		if (gap > STRIKE_REACH) {
			return;
		}
		live.strikeTarget = -1;
		ServerLevel level = player.level();
		double speed = 0.0;
		for (double s : live.speeds) {
			speed = Math.max(speed, s);
		}
		float damage = Math.max(STRIKE_MIN_DAMAGE, (float)speed * STRIKE_DAMAGE_PER_SPEED);
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		target.hurtServer(level, level.damageSources().playerAttack(player), damage);
		Vec3 dir = target.position().subtract(player.position());
		Vec3 flat = new Vec3(dir.x, 0.0, dir.z);
		flat = flat.lengthSqr() < 1.0E-4 ? player.getViewVector(1.0F).horizontal().normalize() : flat.normalize();
		Vec3 push = flat.scale(STRIKE_KNOCKBACK).add(0.0, STRIKE_LIFT, 0.0);
		target.setDeltaMovement(push);
		target.needsSync = true;
		target.syncVelocity = true;
		if (target instanceof ServerPlayer other) {
			ServerPlayNetworking.send(other, new WebNet.Nudge(push));
		}
		// and you rebound off it
		ServerPlayNetworking.send(player, new WebNet.Nudge(flat.scale(-0.35).add(0.0, 0.35, 0.0)));
		Vec3 at = target.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 20, 0.3, 0.3, 0.3, 0.4);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.1);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.2F, 0.8F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.9F);
		com.jaymospowers.core.Shakes.send(player, 0.9F, at);
	}
}
