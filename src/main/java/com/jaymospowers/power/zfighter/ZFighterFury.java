package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.HudBar;
import com.jaymospowers.core.Shakes;
import com.jaymospowers.core.fx.MagicFx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * Battle fury (Andy's Awakening pick: "Z Fighter's rage and the Great Ape"). Fighting fills a Z Fighter's fury: every blow he
 * lands ({@link #FILL_DEALT}) and every one he takes ({@link #FILL_TAKEN}). As it fills he grows with it: up to
 * {@link #SCALE} bigger, {@link #HEALTH} more health and {@link #DAMAGE} harder blows at full. Out of the fight for
 * {@link #COOL_DELAY} ticks and it cools away, over {@link #COOL_TICKS}. The gauge shows on the action bar while it's up.
 * <p>
 * At full fury, his grab (Sneak + left click, {@link ZFighterGrab}) is the Great Ape's: he takes hold of the foe and pounds it
 * into the ground {@link #POUNDS} times, each slam {@link #POUND_SHARE} of his fists, and the fury is spent. Not as the Great
 * Ape himself, whose own rage is {@link ApeRage}.
 */
final class ZFighterFury {
	// ---- tuning ----
	/** Fury (0-1) a blow landed, a blow taken. */
	static final float FILL_DEALT = 0.04F, FILL_TAKEN = 0.03F;
	/** Out of the fight this long and it cools, from full to none over this long (ticks). */
	static final int COOL_DELAY = 100, COOL_TICKS = 600;
	/** At full: this much bigger (a share of his size), this much more health (points), this much harder (a share). */
	static final double SCALE = 0.35, HEALTH = 10.0, DAMAGE = 0.3;
	/** The pound: slams, ticks between them, each slam's harm (a share of his fists), how high he lifts it. */
	static final int POUNDS = 4, POUND_EVERY = 10;
	static final float POUND_SHARE = 0.6F;
	static final double LIFT = 1.6;
	static final int FURY_COLOR = 0xD8261A;

	private static final Identifier SCALE_ID = JaymosPowers.id("zfighter_fury_scale");
	private static final Identifier HEALTH_ID = JaymosPowers.id("zfighter_fury_health");
	private static final Identifier DAMAGE_ID = JaymosPowers.id("zfighter_fury_damage");

	private static final class Fury {
		float fury;
		/** The share last written to his attributes. */
		float applied;
		long lastFight;
		LivingEntity pounding;
		int poundTicks;
	}

	private static final Map<UUID, Fury> FURY = new HashMap<>();

	private ZFighterFury() {
	}

	private static Fury of(ServerPlayer player) {
		return FURY.computeIfAbsent(player.getUUID(), id -> new Fury());
	}

	static boolean full(ServerPlayer player) {
		Fury f = FURY.get(player.getUUID());
		return f != null && f.fury >= 1.0F && f.pounding == null;
	}

	/** He landed a blow. */
	static void dealt(ServerPlayer player) {
		fill(player, FILL_DEALT);
	}

	/** He took one. */
	static void took(ServerPlayer player) {
		fill(player, FILL_TAKEN);
	}

	private static void fill(ServerPlayer player, float amount) {
		if (ZFighterLineage.ape(player)) {
			return;
		}
		Fury f = of(player);
		boolean was = f.fury >= 1.0F;
		f.fury = Math.min(1.0F, f.fury + amount);
		f.lastFight = player.level().getGameTime();
		if (!was && f.fury >= 1.0F) {
			ServerLevel level = player.level();
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.9F, 1.1F);
			MagicFx.blast(level, player.getBoundingBox().getCenter(), 1.4F, FURY_COLOR);
		}
	}

	/** Every tick, per Z Fighter: the fury cools out of the fight, his size follows it, the pound goes on. */
	static void tick(ServerPlayer player) {
		Fury f = FURY.get(player.getUUID());
		if (f == null) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		if (f.pounding != null) {
			pound(player, f, level);
		} else if (now - f.lastFight > COOL_DELAY && f.fury > 0.0F) {
			f.fury = Math.max(0.0F, f.fury - 1.0F / COOL_TICKS);
		}
		if (ZFighterLineage.ape(player)) {
			f.fury = 0.0F;
		}
		if (Math.abs(f.fury - f.applied) >= 0.05F || f.fury == 0.0F && f.applied != 0.0F) {
			apply(player, f.fury);
			f.applied = f.fury;
		}
		if (f.fury > 0.0F && now % 10 == 0) {
			HudBar.gauge(player, "fury", f.fury, f.fury >= 1.0F ? HudBar.RED : HudBar.GOLD, Component.translatable("bar.jaymospowers.fury"), null);
		}
		if (f.fury >= 1.0F && now % 4 == 0) {
			level.sendParticles(MagicFx.wisp(FURY_COLOR, 1.2F), player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 2,
				player.getBbWidth() * 0.4, player.getBbHeight() * 0.3, player.getBbWidth() * 0.4, 0.02);
		}
		if (f.fury <= 0.0F && f.pounding == null) {
			FURY.remove(player.getUUID());
		}
	}

	private static void apply(ServerPlayer player, float fury) {
		ZFighterPower.set(player, Attributes.SCALE, SCALE_ID, SCALE * fury, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		ZFighterPower.set(player, Attributes.MAX_HEALTH, HEALTH_ID, Math.round(HEALTH * fury), AttributeModifier.Operation.ADD_VALUE);
		ZFighterPower.set(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, DAMAGE * fury, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	/** The power went: the fury and all it grew go too. */
	static void drop(ServerPlayer player) {
		FURY.remove(player.getUUID());
		apply(player, 0.0F);
	}

	/** At full fury, the grab: he takes hold of {@code foe} to pound it. */
	static void seize(ServerPlayer player, LivingEntity foe) {
		Fury f = of(player);
		f.pounding = foe;
		f.poundTicks = 0;
		ZFighterCombo.stop(player);
		player.level().playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.0F, 0.9F);
	}

	private static void pound(ServerPlayer player, Fury f, ServerLevel level) {
		LivingEntity foe = f.pounding;
		if (!foe.isAlive() || foe.level() != level || !player.isAlive() || player.distanceTo(foe) > 8.0) {
			end(f);
			return;
		}
		f.lastFight = level.getGameTime();
		int t = f.poundTicks++;
		int phase = t % POUND_EVERY;
		ZFighterGrab.lock(foe);
		foe.resetFallDistance();
		Vec3 flat = player.getLookAngle().multiply(1.0, 0.0, 1.0);
		flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
		Vec3 front = player.position().add(flat.scale(1.2 * player.getScale() + foe.getBbWidth() * 0.5));
		double ground = ground(level, front, player.getY());
		if (phase < POUND_EVERY - 3) {
			// up over his head
			double up = ground + LIFT * player.getScale() * Math.min(1.0, phase / 3.0);
			foe.teleportTo(front.x, up, front.z);
			foe.setDeltaMovement(Vec3.ZERO);
			foe.syncVelocity = true;
		} else if (phase == POUND_EVERY - 3) {
			// and down into the ground
			foe.teleportTo(front.x, ground, front.z);
			foe.setDeltaMovement(Vec3.ZERO);
			foe.syncVelocity = true;
			foe.setInvulnerableTime(0);
			float harm = (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE) * POUND_SHARE;
			foe.hurtServer(level, ZFighterKi.source(player), harm);
			ZFighterPoise.wear(foe, ZFighterGrab.THROW_POISE);
			player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			Vec3 at = new Vec3(front.x, ground, front.z);
			BlockPos under = BlockPos.containing(at.x, ground - 0.5, at.z);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, level.getBlockState(under)), at.x, at.y + 0.1, at.z, 30, 0.6, 0.1, 0.6, 0.2);
			level.sendParticles(ParticleTypes.EXPLOSION, true, false, at.x, at.y + 0.3, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.2F, 0.8F);
			com.jaymospowers.core.GroundCracks.crack(level, under, level.getRandom(), 3);
			Shakes.around(level, at, 16.0, 1.0F);
		}
		if (f.poundTicks >= POUNDS * POUND_EVERY) {
			end(f);
			f.fury = 0.0F;
		}
	}

	private static void end(Fury f) {
		f.pounding = null;
		f.poundTicks = 0;
	}

	/** The top of the ground under {@code at} (within a few blocks of {@code y}). */
	private static double ground(ServerLevel level, Vec3 at, double y) {
		BlockPos pos = BlockPos.containing(at.x, y + 1.0, at.z);
		for (int i = 0; i < 5; i++) {
			BlockPos p = pos.below(i);
			var shape = level.getBlockState(p).getCollisionShape(level, p);
			if (!shape.isEmpty()) {
				return p.getY() + shape.max(net.minecraft.core.Direction.Axis.Y);
			}
		}
		return y;
	}
}
