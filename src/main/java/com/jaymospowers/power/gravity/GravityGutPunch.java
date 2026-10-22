package com.jaymospowers.power.gravity;

import com.jaymospowers.core.Fate;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.BodyPlan;
import com.jaymospowers.core.wounds.Wounds;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Inversion (R+B: B while Lift holds a creature; Andy, 2026-10-21: "Pull target in and punch them in the gut, sending
 * them away while inverting their insides causing bleed and a heavy slow status"). Lift lets go and the creature is
 * yanked in to arm's length over at most {@link #PULL_TICKS}; your fist meets it in the gut: {@link #DAMAGE} (more
 * Collapsed), and it's sent flying the way you look. Its insides are turned over: a cut opens on its body (Wounds'
 * bleeding) and it bleeds inside too, {@link #INNER_BLEED} every {@link #INNER_BLEED_EVERY} for
 * {@link #INNER_BLEED_TICKS}; and it's slowed hard ({@link #SLOW_LEVEL}) and weak for {@link #SLOW_TICKS}. Nothing
 * bloodless bleeds (bones, golems), but it's still hit and slowed. Uses B's cooldown.
 */
final class GravityGutPunch {
	/** The yank: how fast (blocks/tick), and the longest it takes before the punch lands anyway. */
	static final double PULL_SPEED = 1.6;
	static final int PULL_TICKS = 8;
	/** It's met this far in front of your eyes (blocks, plus half its width). */
	static final double MEET = 1.3;
	/** The punch (half-hearts), and the throw: along your look (blocks/tick), and up. */
	static final float DAMAGE = 8.0F;
	static final double LAUNCH = 2.4, LAUNCH_UP = 0.45;
	/** The bleeding inside: this much (half-hearts, to the body) every so often, for so long. */
	static final float INNER_BLEED = 1.0F;
	static final int INNER_BLEED_EVERY = 20, INNER_BLEED_TICKS = 120;
	/** The heavy slow: this long, this hard (Slowness IV), with Weakness and Nausea for players. */
	static final int SLOW_TICKS = 140, SLOW_LEVEL = 3;
	/** B's cooldown after it (ticks). */
	static final int COOLDOWN = 220;

	private static final com.jaymospowers.core.blood.BloodParticles.Fleck BLOOD = com.jaymospowers.core.blood.BloodParticles.fleck(0x7A0A0A, 1.0F);

	private static final class Punch {
		final ResourceKey<Level> dimension;
		final UUID target, owner;
		final long start;

		Punch(ResourceKey<Level> dimension, UUID target, UUID owner, long start) {
			this.dimension = dimension;
			this.target = target;
			this.owner = owner;
			this.start = start;
		}
	}

	/** Bleeding inside: who, who did it, until when. */
	private record Inner(ResourceKey<Level> dimension, UUID target, UUID owner, long until) {
	}

	private static final List<Punch> PUNCHES = new ArrayList<>();
	private static final List<Inner> INNER = new ArrayList<>();

	private GravityGutPunch() {
	}

	/** Begins the yank on {@code target} (Lift has already let it go). */
	static void start(ServerPlayer player, LivingEntity target) {
		ServerLevel level = player.level();
		PUNCHES.removeIf(p -> p.target.equals(target.getUUID()));
		PUNCHES.add(new Punch(level.dimension(), target.getUUID(), player.getUUID(), level.getGameTime()));
		GravityPower.touch(target, player);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 1.0F, 0.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 0.6F, 2.0F);
	}

	static void tick(MinecraftServer server) {
		if (!PUNCHES.isEmpty()) {
			for (Iterator<Punch> it = PUNCHES.iterator(); it.hasNext(); ) {
				Punch punch = it.next();
				ServerLevel level = server.getLevel(punch.dimension);
				ServerPlayer owner = server.getPlayerList().getPlayer(punch.owner);
				Entity entity = level == null ? null : level.getEntity(punch.target);
				if (owner == null || !owner.isAlive() || owner.level() != level || !(entity instanceof LivingEntity target) || !target.isAlive()) {
					it.remove();
					continue;
				}
				Vec3 meet = owner.getEyePosition().add(owner.getViewVector(1.0F).scale(MEET + target.getBbWidth() * 0.5))
					.subtract(0.0, target.getBbHeight() * 0.5, 0.0);
				Vec3 to = meet.subtract(target.position());
				if (to.length() <= PULL_SPEED || level.getGameTime() - punch.start >= PULL_TICKS) {
					it.remove();
					land(level, owner, target);
					continue;
				}
				Vec3 v = to.normalize().scale(PULL_SPEED);
				target.setDeltaMovement(v);
				target.needsSync = true;
				target.syncVelocity = true;
				target.resetFallDistance();
				if (target instanceof ServerPlayer held) {
					net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(held, new com.jaymospowers.power.web.WebNet.Nudge(v));
				}
				level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 4,
					0.2, 0.2, 0.2, 0.02);
			}
		}
		if (!INNER.isEmpty()) {
			for (Iterator<Inner> it = INNER.iterator(); it.hasNext(); ) {
				Inner inner = it.next();
				ServerLevel level = server.getLevel(inner.dimension());
				Entity entity = level == null ? null : level.getEntity(inner.target());
				if (!(entity instanceof LivingEntity target) || !target.isAlive() || level.getGameTime() > inner.until()) {
					it.remove();
					continue;
				}
				if ((inner.until() - level.getGameTime()) % INNER_BLEED_EVERY == 0) {
					bleedInside(level, target, server.getPlayerList().getPlayer(inner.owner()));
				}
			}
		}
	}

	/** The fist lands. */
	private static void land(ServerLevel level, ServerPlayer owner, LivingEntity target) {
		owner.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		float damage = DAMAGE * (Fate.isCorrupted(owner) ? GravityPower.COLLAPSE_DAMAGE : 1.0F);
		GravityPower.hurtByWeight(target, owner, damage);
		if (!target.isAlive()) {
			return;
		}
		Vec3 v = owner.getViewVector(1.0F).scale(LAUNCH).add(0.0, LAUNCH_UP, 0.0);
		target.setDeltaMovement(v);
		target.needsSync = true;
		target.syncVelocity = true;
		if (target instanceof ServerPlayer hit) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(hit, new com.jaymospowers.power.web.WebNet.Nudge(v));
			hit.addEffect(new MobEffectInstance(MobEffects.NAUSEA, SLOW_TICKS, 0), owner);
		}
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SLOW_TICKS, SLOW_LEVEL), owner);
		target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SLOW_TICKS, 0), owner);
		// its insides turned over
		if (bleeds(target)) {
			Bleeding.open(target, BodyPart.BODY, false);
			INNER.removeIf(i -> i.target().equals(target.getUUID()));
			INNER.add(new Inner(level.dimension(), target.getUUID(), owner.getUUID(), level.getGameTime() + INNER_BLEED_TICKS));
			level.sendParticles(BLOOD, target.getX(), target.getY() + target.getBbHeight() * 0.55, target.getZ(), 14, 0.25, 0.25, 0.25, 0.0);
		}
		Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		level.sendParticles(ParticleTypes.SONIC_BOOM, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y, at.z, 30, 0.3, 0.3, 0.3, 0.4);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 1.0F, 0.7F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 1.0F, 0.5F);
		com.jaymospowers.core.Shakes.heavy(owner, at, 10.0, 0.9F);
	}

	/** Something with blood in it and a body Wounds parts (not a creative player). */
	private static boolean bleeds(LivingEntity target) {
		return BodyPlan.of(target) != null && !target.getType().builtInRegistryHolder().is(com.jaymospowers.core.BladeBlood.BLOODLESS)
			&& !(target instanceof net.minecraft.world.entity.player.Player player && (player.isCreative() || player.isSpectator()));
	}

	private static void bleedInside(ServerLevel level, LivingEntity target, ServerPlayer owner) {
		if (owner != null) {
			GravityPower.touch(target, owner);
		}
		Wounds.hurtPart(target, BodyPart.BODY, INNER_BLEED, owner, Bleeding.BLEEDING_DAMAGE);
		level.sendParticles(BLOOD, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 4, 0.2, 0.2, 0.2, 0.0);
	}
}
