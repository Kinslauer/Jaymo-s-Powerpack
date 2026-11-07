package com.jaymospowers.power.chrono;

import com.jaymospowers.combat.HitStreak;
import com.jaymospowers.combat.Shields;
import com.jaymospowers.core.finisher.Finishers;
import com.jaymospowers.core.wounds.Downed;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.23, Kingdoms Rivals patch 9) More for Time Flow ({@link ChronoFlow}), ideas only from the Arkham and Shadow of War
 * games. Sneak + attack bare-handed ({@code combat/Takedowns}):
 * <ul>
 * <li><b>Takedown:</b> at {@link HitStreak#READY} combo, time stops round the foe in front of you for a beatdown and he's
 * left downed; at {@link HitStreak#DOUBLE}, the nearest other foe goes down with him. Spends that much combo.</li>
 * <li><b>Shadow-strike blink:</b> a foe you've hit with something thrown or shot is marked ({@link #MARK_TICKS}); at him
 * (out past arm's reach, up to 24 blocks) you blink to his back and strike. Spends {@link #BLINK_COST}.</li>
 * </ul>
 * And in the flow itself ({@code ChronoFlow.land}): a strike at one already down finishes him in passing and the combo goes
 * on ({@link #grounded}); one at a raised shield is a time-slowing flourish that leaves its bearer stunned, his guard down
 * ({@link #shieldStun}), so you can vault him or beat him down.
 */
public final class ChronoTakedowns {
	// ================================================================================================================
	// Tuning
	/** The blink: its cost in combo, how long a mark lasts (ticks), how far behind him you come out, its blow and stun. */
	public static final int BLINK_COST = 3, MARK_TICKS = 200;
	static final double BLINK_BEHIND = 1.3;
	static final float BLINK_DAMAGE = 4.0F;
	static final int BLINK_STUN = 20;
	/** Beyond this (blocks), a sneak + attack at a marked foe is the blink; within it, the takedown. */
	static final double BLINK_FROM = 3.5;
	/** The takedown: its reach, how long the downed stay out (ticks), and how near the second foe must be at 16. */
	static final double REACH = 4.0, SECOND_RANGE = 5.0;
	static final int OUT_TICKS = 400;
	static final float TAKEDOWN_DAMAGE = 3.0F;
	/** The shield flourish's stun (ticks). */
	static final int SHIELD_STUN = 40;
	// ================================================================================================================

	/** A mark: whose, and until when. */
	private record Mark(UUID by, long until) {
	}

	private static final Map<LivingEntity, Mark> MARKS = new WeakHashMap<>();

	private ChronoTakedowns() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (source.getEntity() instanceof ServerPlayer player && ChronoFlow.on(player) && source.getDirectEntity() != null
				&& source.getDirectEntity() != player && victim != player && victim.isAlive() && victim.level() instanceof ServerLevel level) {
				// (something thrown or shot: marked for the blink)
				MARKS.put(victim, new Mark(player.getUUID(), level.getGameTime() + MARK_TICKS));
				level.sendParticles(ParticleTypes.END_ROD, victim.getX(), victim.getEyeY() + 0.4, victim.getZ(), 6, 0.15, 0.15, 0.15, 0.01);
				level.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 1.6F);
			}
		});
	}

	private static boolean marked(ServerPlayer player, LivingEntity target) {
		Mark mark = MARKS.get(target);
		return mark != null && mark.by().equals(player.getUUID()) && player.level().getGameTime() < mark.until();
	}

	/** Sneak + attack at {@code target} (from {@code combat/Takedowns}). (0.21.24) True if it went. */
	public static boolean takedown(ServerPlayer player, LivingEntity target) {
		int combo = ChronoFlow.combo(player);
		double d = target.distanceTo(player) - target.getBbWidth() * 0.5;
		if (d > BLINK_FROM && marked(player, target)) {
			return blinkAt(player, target);
		}
		if (d > REACH) {
			return false;
		}
		if (combo < HitStreak.READY) {
			short_(player, HitStreak.READY);
			return false;
		}
		boolean two = combo >= HitStreak.DOUBLE;
		ChronoFlow.spendCombo(player, two ? HitStreak.DOUBLE : HitStreak.READY);
		beatDown(player, target);
		if (two) {
			LivingEntity second = second(player, target);
			if (second != null) {
				beatDown(player, second);
			}
		}
		ChronoFlow.keepCombo(player);
		player.setAttached(ChronoFlow.MOVE, new ChronoFlow.Move(ChronoFlow.UPPERCUT, player.level().getGameTime(), target.getId(), 1));
		return true;
	}

	/** (0.21.24, the skill bar's blink) To a marked foe's back, if there's combo for it. True if it went. */
	public static boolean blinkAt(ServerPlayer player, LivingEntity target) {
		if (!marked(player, target)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.takedown.unmarked").withStyle(ChatFormatting.GRAY));
			return false;
		}
		if (ChronoFlow.combo(player) < BLINK_COST) {
			short_(player, BLINK_COST);
			return false;
		}
		blink(player, target);
		return true;
	}

	private static void short_(ServerPlayer player, int needed) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.takedown.short", needed).withStyle(ChatFormatting.GRAY));
	}

	/** Time stops round him: a flurry he never sees, and he's down. */
	private static void beatDown(ServerPlayer player, LivingEntity target) {
		ServerLevel level = player.level();
		target.setInvulnerableTime(0);
		target.hurtServer(level, player.damageSources().playerAttack(player), TAKEDOWN_DAMAGE);
		if (target.isAlive() && !Downed.knockOut(level, target, OUT_TICKS, true, "message.jaymospowers.takedown.downed")) {
			// (too big to put down: he reels a long while instead)
			ChronoFlow.stun(target, OUT_TICKS / 4);
		}
		Vec3 at = target.getBoundingBox().getCenter();
		ChronoFx.particles(level, player, ParticleTypes.CRIT, at.x, at.y, at.z, 30, 0.4, 0.5, 0.4, 0.4);
		ChronoFx.particles(level, player, ParticleTypes.ENCHANT, at.x, at.y, at.z, 60, 0.8, 0.8, 0.8, 0.6);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.9F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 0.6F);
		com.jaymospowers.core.Shakes.send(player, 1.0F, at);
	}

	/** The nearest other foe near {@code first}, for a takedown at 16. */
	private static @Nullable LivingEntity second(ServerPlayer player, LivingEntity first) {
		LivingEntity best = null;
		double bestD = Double.MAX_VALUE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, first.getBoundingBox().inflate(SECOND_RANGE),
			e -> e != first && e != player && e.isAlive() && !Finishers.helpless(e) && (e instanceof Enemy || e instanceof Mob m && m.getTarget() == player))) {
			double d = e.distanceToSqr(first);
			if (d < bestD) {
				best = e;
				bestD = d;
			}
		}
		return best;
	}

	/** To his back in a blink, and the blow before he turns. */
	private static void blink(ServerPlayer player, LivingEntity target) {
		ServerLevel level = player.level();
		ChronoFlow.spendCombo(player, BLINK_COST);
		MARKS.remove(target);
		Vec3 from = player.position();
		Vec3 back = Vec3.directionFromRotation(0.0F, target.getYRot()).reverse().scale(BLINK_BEHIND + target.getBbWidth() * 0.5);
		Vec3 to = target.position().add(back);
		ChronoFx.particles(level, player, ParticleTypes.REVERSE_PORTAL, from.x, from.y + 1.0, from.z, 30, 0.3, 0.6, 0.3, 0.05);
		player.teleportTo(to.x, target.getY(), to.z);
		player.lookAt(EntityAnchorArgument.Anchor.EYES, target, EntityAnchorArgument.Anchor.EYES);
		player.resetFallDistance();
		ChronoFx.particles(level, player, ParticleTypes.REVERSE_PORTAL, to.x, target.getY() + 1.0, to.z, 30, 0.3, 0.6, 0.3, 0.05);
		level.playSound(null, to.x, target.getY(), to.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.5F);
		target.setInvulnerableTime(0);
		float bonus = Math.min(ChronoFlow.COMBO_MAX_BONUS, ChronoFlow.combo(player) * ChronoFlow.COMBO_BONUS);
		if (target.hurtServer(level, player.damageSources().playerAttack(player), BLINK_DAMAGE * (1.0F + bonus))) {
			ChronoFlow.stun(target, BLINK_STUN);
			ChronoFlow.reel(target, ChronoFlow.FLINCH, 1);
			ChronoFlow.addCombo(player);
		}
		player.setAttached(ChronoFlow.MOVE, new ChronoFlow.Move(ChronoFlow.HOOK, level.getGameTime(), target.getId(), 1));
	}

	/** (From {@code ChronoFlow.land}) A strike at one already down: finished in passing, and the combo counts on. */
	static boolean grounded(ServerPlayer player, LivingEntity target) {
		if (!Finishers.helpless(target) || Finishers.targeted(target) || target instanceof net.minecraft.world.entity.player.Player) {
			return false;
		}
		ServerLevel level = player.level();
		Vec3 at = target.getBoundingBox().getCenter();
		Downed.execute(level, target, player);
		ChronoFlow.addCombo(player);
		ChronoFx.particles(level, player, ParticleTypes.CRIT, at.x, at.y, at.z, 16, 0.3, 0.2, 0.3, 0.3);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.6F);
		return true;
	}

	/** (From {@code ChronoFlow.land}) A strike at a raised shield: time slows round it and its bearer reels, guard down. */
	static boolean shieldStun(ServerPlayer player, LivingEntity target) {
		if (!Shields.raised(target)) {
			return false;
		}
		ServerLevel level = player.level();
		target.stopUsingItem();
		ChronoFlow.stun(target, SHIELD_STUN);
		ChronoFlow.reel(target, ChronoFlow.FLINCH, player.getRandom().nextBoolean() ? 1 : -1);
		ChronoFlow.addCombo(player);
		Vec3 at = target.getBoundingBox().getCenter();
		ChronoFx.particles(level, player, ParticleTypes.ENCHANT, at.x, at.y, at.z, 40, 0.6, 0.6, 0.6, 0.8);
		ChronoFx.particles(level, player, ParticleTypes.WAX_OFF, at.x, at.y, at.z, 10, 0.5, 0.5, 0.5, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.8F, 1.4F);
		return true;
	}
}
