package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.Soundscape;
import com.jaymospowers.core.brawl.Brawl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/**
 * Sword Technique: a blade flurry ending in a ki burst, for fighter weapons.
 * Quick technique on G ({@link ZFighterTraining#SWORD_TECHNIQUE}).
 * Requires a sword or spear in hand. Performs a rapid series of strikes ending in a ki explosion.
 */
public final class ZFighterSwordTechnique {
	/** Ki cost. */
	static final float KI_COST = 20.0F;
	/** Cooldown (ticks). */
	static final int COOLDOWN = 160;
	/** Number of strikes in the flurry. */
	static final int STRIKES = 5;
	/** Time between strikes (ticks). */
	static final int STRIKE_GAP = 4;
	/** Damage per strike (share of fist damage). */
	static final float STRIKE_SHARE = 0.6F;
	/** Final burst damage (share of fist damage). */
	static final float BURST_SHARE = 2.0F;
	/** Burst radius (blocks). */
	static final double BURST_RADIUS = 3.5;
	/** Total duration (ticks). */
	static final int DURATION = STRIKES * STRIKE_GAP + 10;

	private static final class Flurry {
		final ServerLevel level;
		final ServerPlayer player;
		final Vec3 look;
		final float damage;
		final int color;
		int strike;
		long start;

		Flurry(ServerPlayer player, Vec3 look, float damage, int color) {
			this.level = player.level();
			this.player = player;
			this.look = look;
			this.damage = damage;
			this.color = color;
			this.strike = 0;
			this.start = level.getGameTime();
		}
	}

	private static final java.util.Map<UUID, Flurry> FLURRIES = new java.util.HashMap<>();

	private ZFighterSwordTechnique() {
	}

	static void init() {
	}

	static void clear() {
		FLURRIES.clear();
	}

	/** Whether the player is holding a blade (sword or spear). */
	static boolean hasBlade(ServerPlayer player) {
		ItemStack main = player.getMainHandItem();
		return !main.isEmpty() && (main.is(net.minecraft.tags.ItemTags.SWORDS) || main.is(net.minecraft.tags.ItemTags.SPEARS));
	}

	/** G: start the sword flurry. Returns cooldown, or 0 if it couldn't start. */
	static int start(ServerPlayer player, ZFighterPower.Live live) {
		if (!hasBlade(player)) {
			player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("message.jaymospowers.zfighter_sword.no_blade").withStyle(net.minecraft.ChatFormatting.GRAY));
			return 0;
		}
		if (!ZFighterPower.spend(player, live, KI_COST)) {
			return 0;
		}
		ServerLevel level = player.level();
		Vec3 look = LockOn.aim(player);
		float kiPower = ZFighterPower.kiPower(player, live);
		float damage = (float)(ZFighterPower.fistBonus(player) * STRIKE_SHARE * kiPower);
		int color = ZFighterArts.color(live);
		FLURRIES.put(player.getUUID(), new Flurry(player, look, damage, color));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 1.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.6F, 1.8F);
		return COOLDOWN;
	}

	/** Called every server tick to advance flurries. */
	static void tick(MinecraftServer server) {
		if (FLURRIES.isEmpty()) {
			return;
		}
		for (java.util.Iterator<java.util.Map.Entry<java.util.UUID, Flurry>> it = FLURRIES.entrySet().iterator(); it.hasNext(); ) {
			java.util.Map.Entry<java.util.UUID, Flurry> entry = it.next();
			Flurry f = entry.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive() || player.level() != f.level || !hasBlade(player)) {
				it.remove();
				continue;
			}
			long elapsed = f.level.getGameTime() - f.start;
			if (elapsed >= DURATION) {
				it.remove();
				continue;
			}
			// Do a strike every STRIKE_GAP ticks
			if (elapsed % STRIKE_GAP == 0 && f.strike < STRIKES) {
				strike(f, player);
				f.strike++;
			}
			// Final burst on the last tick
			if (elapsed == DURATION - 1) {
				burst(f, player);
			}
		}
	}

	private static void strike(Flurry f, ServerPlayer player) {
		ServerLevel level = f.level;
		Vec3 pos = player.position().add(f.look.scale(1.5 + f.strike * 0.3));
		// Slash particles
		level.sendParticles(MagicFx.wisp(f.color, 1.2F), true, false, pos.x, pos.y + player.getBbHeight() * 0.5, pos.z,
				8, 0.4, 0.4, 0.4, 0.05);
		level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.6F + f.strike * 0.1F);
		// Hit entities in a cone ahead
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
				new AABB(pos, pos).inflate(2.0), e -> ZFighterArts.valid(player, e) && e != player)) {
			Vec3 toTarget = target.position().subtract(player.position());
			if (toTarget.normalize().dot(f.look) > 0.5) {
				target.setInvulnerableTime(0);
				target.hurtServer(level, ZFighterKi.source(player), f.damage);
				ZFighterKi.push(target, f.look, 0.4, 0.1);
			}
		}
		// Animate the arm
		ZFighterHands.thrust(player, ZFighterHands.hand(player, true), ZFighterHands.JAB);
	}

	private static void burst(Flurry f, ServerPlayer player) {
		ServerLevel level = f.level;
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0).add(f.look.scale(2.0));
		MagicFx.burst(level, at.x, at.y, at.z, f.color, 2.0F, 30, (float)BURST_RADIUS, 0.5F, (float)BURST_RADIUS);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.0F, 1.2F);
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
				new AABB(at, at).inflate(BURST_RADIUS), e -> ZFighterArts.valid(player, e) && e != player)) {
			Vec3 away = target.position().subtract(at);
			double dist = away.length();
			if (dist > BURST_RADIUS) continue;
			float scale = (float)(1.0 - dist / (BURST_RADIUS * 1.5));
			target.setInvulnerableTime(0);
			target.hurtServer(level, ZFighterKi.source(player), f.damage * BURST_SHARE * scale);
			ZFighterKi.push(target, away.normalize(), 1.0 * scale, 0.3);
		}
		com.jaymospowers.core.Shakes.send(player, 0.6F, at);
		if (!player.isCreative()) {
			player.causeFoodExhaustion(0.3F);
		}
	}
}