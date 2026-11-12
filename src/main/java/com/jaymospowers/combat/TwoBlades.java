package com.jaymospowers.combat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.SwingAnimation;

/**
 * A blade in each hand (Andy's pick from Star Wars: Galaxy in a Cube, ideas only). Better Combat already swings the off
 * hand turn about; this adds what it doesn't: every {@link #EVERY third} swing that lands, both blades cut together,
 * the second for {@link #SHARE} of your attack on the same foe. The wider stance and the crossed guard are the client's
 * ({@code ArmedPose}, {@code ParryPose}, from {@link Armed#dual}).
 */
public final class TwoBlades {
	/** Every which landed swing both blades cut, the share of your attack the second blade adds, and how long a string of swings holds between blows (ticks). */
	public static final int EVERY = 3;
	public static final float SHARE = 0.6F;
	static final int STRING_TICKS = 50;
	/** The least ticks between two swings that count (a sweep through three foes is one), and how long the cut is drawn (ticks). */
	static final int SWING_GAP = 4, CUT_TICKS = 8;

	private static final class Run {
		int swings;
		long last;
	}

	private static final Map<UUID, Run> RUNS = new HashMap<>();
	/** Players whose second blade is landing right now (its own damage is not a swing). */
	private static final Set<UUID> CUTTING = new HashSet<>();

	private TwoBlades() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() instanceof ServerPlayer player) {
				landed(player, entity);
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> RUNS.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> RUNS.clear());
	}

	private static void landed(ServerPlayer player, LivingEntity target) {
		if (CUTTING.contains(player.getUUID()) || !Armed.dual(player)) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Run run = RUNS.computeIfAbsent(player.getUUID(), id -> new Run());
		if (now - run.last < SWING_GAP) {
			return;
		}
		if (now - run.last > STRING_TICKS) {
			run.swings = 0;
		}
		run.last = now;
		if (++run.swings < EVERY) {
			return;
		}
		run.swings = 0;
		if (!target.isAlive()) {
			return;
		}
		// both blades together: the second lands on the same foe
		Armed.cut(player, Armed.CUT_BOTH, CUT_TICKS, 0);
		player.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
		CUTTING.add(player.getUUID());
		try {
			target.setInvulnerableTime(0);
			target.hurtServer(level, level.damageSources().playerAttack(player), (float)player.getAttributeValue(Attributes.ATTACK_DAMAGE) * SHARE);
		} finally {
			CUTTING.remove(player.getUUID());
		}
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.6), target.getZ(), 2, 0.3, 0.1, 0.3, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.9F, 1.3F);
	}
}
