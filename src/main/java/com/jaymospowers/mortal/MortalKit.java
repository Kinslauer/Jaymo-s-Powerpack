package com.jaymospowers.mortal;

import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.wounds.Downed;
import com.jaymospowers.core.wounds.Tripped;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What anyone can do: the four ability keys of a player with no power ({@code Powers.key}). Each has its own wait; a
 * press while it is still running out says so on the action bar and does nothing.
 * <ul>
 * <li><b>Power Strike</b> (key 1): everything behind one blow at whoever is in front, {@link #STRIKE_BASE} plus
 * {@link #STRIKE_SHARE} of the blow the hand already holds, and a hard shove.</li>
 * <li><b>Sweep Kick</b> (key 2): the legs taken out from under whoever is in front (the bolas trip, nothing broken:
 * {@code Tripped.sweep}); something not built like a person is kicked and slowed instead.</li>
 * <li><b>Second Wind</b> (key 3): at or under {@link #WIND_HEALTH} of your health, a breath: {@link #WIND_HEAL} back at
 * once and a little more after, slowness and weakness shaken off.</li>
 * <li><b>Battle Cry</b> (key 4): a shout: you and the players within {@link #CRY_RANGE} blocks hit harder for a while,
 * and lesser things that were coming for you lose heart.</li>
 * </ul>
 */
public final class MortalKit {
	static final double REACH = 3.5;
	static final float STRIKE_BASE = 4.0F, STRIKE_SHARE = 0.75F, STRIKE_SHOVE = 1.3F, STRIKE_TIRE = 2.0F;
	static final int STRIKE_WAIT = 20 * 8;
	static final float KICK_DAMAGE = 2.0F, KICK_TIRE = 1.5F;
	static final int KICK_DOWN = 50, KICK_SLOW = 40, KICK_WAIT = 20 * 12;
	static final float WIND_HEALTH = 0.6F, WIND_HEAL = 4.0F, WIND_TIRE = 6.0F;
	static final int WIND_REGEN = 20 * 5, WIND_WAIT = 20 * 90;
	static final double CRY_RANGE = 10.0, CRY_FEAR = 8.0;
	static final float CRY_FEAR_HEALTH = 30.0F;
	static final int CRY_TICKS = 20 * 10, CRY_FEAR_TICKS = 20 * 6, CRY_WAIT = 20 * 60;
	private static final String[] NAMES = {"power_strike", "sweep_kick", "second_wind", "battle_cry"};

	/** Each player's four waits: the game time each move is ready again. */
	private static final Map<UUID, long[]> READY = new HashMap<>();

	private MortalKit() {
	}

	static void init() {
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> READY.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> READY.clear());
	}

	/** A powerless player's ability key. */
	public static void key(ServerPlayer player, PowerKey key, boolean down) {
		if (!down || !player.isAlive() || player.isSpectator() || Downed.isDowned(player) || Tripped.tripped(player) || Powers.paused(player)
			|| !(player.level() instanceof ServerLevel level)) {
			return;
		}
		int move = key.ordinal();
		long now = level.getGameTime();
		long[] ready = READY.computeIfAbsent(player.getUUID(), id -> new long[4]);
		if (now < ready[move]) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.mortal.wait", Component.translatable("message.jaymospowers.mortal." + NAMES[move]),
				(ready[move] - now + 19) / 20).withStyle(ChatFormatting.GRAY));
			return;
		}
		int wait = switch (move) {
			case 0 -> strike(level, player);
			case 1 -> kick(level, player);
			case 2 -> wind(level, player);
			default -> cry(level, player);
		};
		if (wait > 0) {
			ready[move] = now + wait;
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.mortal." + NAMES[move]).withStyle(ChatFormatting.GOLD));
		}
	}

	/** Whoever {@code player} is looking at within reach. */
	static @Nullable LivingEntity aimed(Player player, double reach) {
		Vec3 eye = player.getEyePosition(), end = eye.add(player.getLookAngle().scale(reach));
		AABB box = player.getBoundingBox().expandTowards(player.getLookAngle().scale(reach)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
			e -> e instanceof LivingEntity living && living.isAlive() && !e.isSpectator() && e.isPickable(), reach * reach);
		return hit != null && hit.getEntity() instanceof LivingEntity living && player.hasLineOfSight(living) ? living : null;
	}

	private static int strike(ServerLevel level, ServerPlayer player) {
		LivingEntity target = aimed(player, REACH);
		if (target == null) {
			return 0;
		}
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		float damage = STRIKE_BASE + (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * STRIKE_SHARE;
		if (target.hurtServer(level, player.damageSources().playerAttack(player), damage)) {
			Vec3 look = player.getLookAngle();
			target.knockback(STRIKE_SHOVE, -look.x, -look.z, player.damageSources().playerAttack(player), damage);
			level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 18, 0.3, 0.3, 0.3, 0.3);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 0.7F);
		}
		player.causeFoodExhaustion(STRIKE_TIRE);
		return STRIKE_WAIT;
	}

	private static int kick(ServerLevel level, ServerPlayer player) {
		LivingEntity target = aimed(player, REACH);
		if (target == null) {
			return 0;
		}
		player.swing(InteractionHand.OFF_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		target.hurtServer(level, player.damageSources().playerAttack(player), KICK_DAMAGE);
		if (target.isAlive() && !Tripped.sweep(target, KICK_DOWN)) {
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, KICK_SLOW, 1), player);
		}
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY() + 0.3, target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.8F);
		player.causeFoodExhaustion(KICK_TIRE);
		return KICK_WAIT;
	}

	private static int wind(ServerLevel level, ServerPlayer player) {
		if (player.getHealth() > player.getMaxHealth() * WIND_HEALTH) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.mortal.second_wind.fresh").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		player.heal(WIND_HEAL);
		player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, WIND_REGEN, 1));
		player.removeEffect(MobEffects.SLOWNESS);
		player.removeEffect(MobEffects.WEAKNESS);
		player.causeFoodExhaustion(WIND_TIRE);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.5F, 0.8F);
		return WIND_WAIT;
	}

	private static int cry(ServerLevel level, ServerPlayer player) {
		for (Player ally : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(CRY_RANGE), p -> p.isAlive() && !p.isSpectator())) {
			ally.addEffect(new MobEffectInstance(MobEffects.STRENGTH, CRY_TICKS, 0), player);
		}
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(CRY_FEAR), m -> m.isAlive() && m.getTarget() == player
			&& m.getMaxHealth() <= CRY_FEAR_HEALTH)) {
			mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, CRY_FEAR_TICKS, 0), player);
			mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CRY_FEAR_TICKS, 0), player);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAID_HORN.value(), SoundSource.PLAYERS, 1.0F, 1.3F);
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getY() + 2.0, player.getZ(), 6, 0.4, 0.2, 0.4, 0.0);
		return CRY_WAIT;
	}
}
