package com.jaymospowers.power.wind;

import com.jaymospowers.armory.ThrowingWeaponItem;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * (0.10.25) Daggers in a wind sorcerer's hand, the way {@link WindStaff} does the quarterstaff (Andy: "the same way it
 * does quarterstaves"; the same gate, a Kingdoms Sorcerer of the air). The four Better Combat dagger swings (slash right,
 * flip and stab, backhand, double stab) each carry a gale, counted by our own chain of landed swings:
 * <ol>
 * <li><b>Gale Cut</b> (the slash): a short blade of air cuts everything in a narrow cone ahead.</li>
 * <li><b>Wind Stab</b> (the flip and stab): the foe is driven back and you ride the wind in after it.</li>
 * <li><b>Crosswind</b> (the backhand): everyone close is swept aside, across your front.</li>
 * <li><b>Cyclone Thrust</b> (the double stab): a burst round the foe throws it and those beside it up and away.</li>
 * </ol>
 * Right click (not with a throwing knife such as the kunai) is the <b>Wind Step</b>: a quick dash along the ground
 * where you look (sneak: a hop back), landing soft. Every hit leaves a puff of air. The Tempest mode (Z) and the
 * ascension make every gale hit harder. Anyone else just holds a dagger.
 */
public final class WindDagger {
	/** A chain breaks if no swing lands for this long (ticks); a swing is counted once however many it hits. */
	static final int CHAIN_TIMEOUT = 30;
	/** Every hit: how hard the puff pushes the foe away. */
	static final double PUFF_PUSH = 0.2;
	/** Gale Cut: reach (blocks), how wide (cosine of the half-angle), damage (x your attack damage), push. */
	static final double CUT_REACH = 4.5, CUT_CONE = 0.75, CUT_PUSH = 0.3;
	static final float CUT_DAMAGE = 0.5F;
	/** Wind Stab: push and lift on the foe, and how fast you follow it in (blocks/tick). */
	static final double STAB_PUSH = 1.1, STAB_LIFT = 0.2, STAB_FOLLOW = 0.9;
	/** Crosswind: radius and sideways push. */
	static final double CROSS_RADIUS = 3.5, CROSS_PUSH = 0.9;
	/** Cyclone Thrust: radius round the foe, push, lift, damage to those beside it (x your attack damage). */
	static final double CYCLONE_RADIUS = 3.0, CYCLONE_PUSH = 0.8, CYCLONE_LIFT = 0.7;
	static final float CYCLONE_DAMAGE = 0.35F;
	/** Wind Step: dash (blocks/tick), hop back, small lift, cooldown (ticks), soft landing window. */
	static final double STEP_FORWARD = 1.4, STEP_BACK = 1.0, STEP_UP = 0.25;
	static final int STEP_COOLDOWN = 30, STEP_SOFT_TICKS = 40;

	private record Chain(int step, long lastTick) {
	}

	private static final Map<UUID, Chain> CHAINS = new HashMap<>();
	/** Dashing players and the tick their soft landing runs out. */
	private static final Map<UUID, Long> STEPS = new HashMap<>();
	/** Our own gale hits fire the damage event too: don't count those as swings. */
	private static boolean inGale;

	private WindDagger() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!inGale && damageTaken > 0.0F && source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() instanceof ServerPlayer attacker
				&& entity != attacker && isDagger(attacker.getMainHandItem()) && WindStaff.wind(attacker)) {
				hit(attacker, entity);
			}
		});
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (hand != InteractionHand.MAIN_HAND || !isDagger(stack) || stack.getItem() instanceof ThrowingWeaponItem
				|| !WindStaff.wind(player)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer serverPlayer) {
				step(serverPlayer, stack);
			}
			return InteractionResult.SUCCESS;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(source.is(DamageTypes.FALL) && entity instanceof ServerPlayer player && STEPS.containsKey(player.getUUID())));
		ServerTickEvents.END_SERVER_TICK.register(WindDagger::tick);
	}

	/** Any dagger or knife (the Armory's, Balkon's knives, or one another mod names so), the kunai too. */
	static boolean isDagger(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.contains("dagger") || path.contains("knife") || path.contains("kunai");
	}

	// ---------------------------------------------------------------- the combo

	private static void hit(ServerPlayer player, LivingEntity target) {
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Vec3 away = target.position().subtract(player.position()).horizontal();
		Vec3 dir = away.lengthSqr() > 1.0E-4 ? away.normalize() : player.getLookAngle().horizontal().normalize();
		double bonus = WindStaff.bonus(player);
		WindStaff.push(target, dir.scale(PUFF_PUSH * bonus), 0.0);
		Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
		level.sendParticles(ParticleTypes.SMALL_GUST, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);

		Chain chain = CHAINS.get(player.getUUID());
		if (chain != null && chain.lastTick() == now) {
			return; // the same swing hitting a second foe
		}
		int step = chain == null || now - chain.lastTick() > CHAIN_TIMEOUT ? 0 : (chain.step() + 1) % 4;
		CHAINS.put(player.getUUID(), new Chain(step, now));
		inGale = true;
		try {
			switch (step) {
				case 0 -> cut(player, bonus);
				case 1 -> stab(player, target, dir, bonus);
				case 2 -> crosswind(player, bonus);
				default -> cyclone(player, target, bonus);
			}
		} finally {
			inGale = false;
		}
	}

	private static void cut(ServerPlayer player, double bonus) {
		ServerLevel level = player.level();
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();
		for (double d = 1.2; d <= CUT_REACH; d += 1.1) {
			Vec3 p = eye.add(look.scale(d)).add(0.0, -0.4, 0.0);
			level.sendParticles(ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 2, 0.3, 0.1, 0.3, 0.02);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BREEZE_SHOOT, SoundSource.PLAYERS, 0.6F, 1.8F);
		float damage = (float)(player.getAttributeValue(Attributes.ATTACK_DAMAGE) * CUT_DAMAGE * bonus);
		Vec3 flat = look.horizontal().normalize();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(CUT_REACH), e -> WindStaff.foe(player, e))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > CUT_REACH + e.getBbWidth() * 0.5 || distance > 1.0E-3 && to.scale(1.0 / distance).dot(look) < CUT_CONE) {
				continue;
			}
			e.setInvulnerableTime(0);
			if (e.hurtServer(level, player.damageSources().playerAttack(player), damage)) {
				WindStaff.push(e, flat.scale(CUT_PUSH * bonus), 0.05);
			}
		}
	}

	private static void stab(ServerPlayer player, LivingEntity target, Vec3 dir, double bonus) {
		WindStaff.push(target, dir.scale(STAB_PUSH * bonus), STAB_LIFT);
		// ride the wind in after it
		Vec3 v = player.getDeltaMovement();
		player.setDeltaMovement(v.x + dir.x * STAB_FOLLOW, Math.max(v.y, 0.1), v.z + dir.z * STAB_FOLLOW);
		player.syncVelocity = true;
		ServerLevel level = player.level();
		Vec3 at = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
		level.sendParticles(ParticleTypes.GUST, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(), 8, 0.3, 0.05, 0.3, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.6F, 1.5F);
	}

	private static void crosswind(ServerPlayer player, double bonus) {
		ServerLevel level = player.level();
		Vec3 look = player.getLookAngle().horizontal();
		look = look.lengthSqr() > 1.0E-4 ? look.normalize() : new Vec3(0.0, 0.0, 1.0);
		// the backhand swings right to left: the wind follows it across your front
		Vec3 across = new Vec3(look.z, 0.0, -look.x);
		Vec3 center = player.position().add(look.scale(1.5)).add(0.0, 1.0, 0.0);
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.BREEZE_WHIRL, SoundSource.PLAYERS, 0.8F, 1.3F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(CROSS_RADIUS), e -> WindStaff.foe(player, e))) {
			if (e.position().subtract(player.position()).horizontal().length() > CROSS_RADIUS) {
				continue;
			}
			WindStaff.push(e, across.scale(CROSS_PUSH * bonus).add(look.scale(0.3 * bonus)), 0.2);
		}
	}

	private static void cyclone(ServerPlayer player, LivingEntity target, double bonus) {
		ServerLevel level = player.level();
		Vec3 center = target.position();
		level.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, center.x, center.y + 0.5, center.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.CLOUD, center.x, center.y + 0.1, center.z, 20, CYCLONE_RADIUS * 0.3, 0.1, CYCLONE_RADIUS * 0.3, 0.12);
		level.playSound(null, center.x, center.y, center.z, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
		float damage = (float)(player.getAttributeValue(Attributes.ATTACK_DAMAGE) * CYCLONE_DAMAGE * bonus);
		Vec3 fromPlayer = center.subtract(player.position()).horizontal();
		fromPlayer = fromPlayer.lengthSqr() > 1.0E-4 ? fromPlayer.normalize() : Vec3.ZERO;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(CYCLONE_RADIUS), e -> WindStaff.foe(player, e))) {
			Vec3 away = e.position().subtract(center).horizontal();
			double distance = away.length();
			if (distance > CYCLONE_RADIUS) {
				continue;
			}
			if (e != target) {
				e.setInvulnerableTime(0);
				e.hurtServer(level, player.damageSources().playerAttack(player), damage);
			}
			Vec3 out = distance > 1.0E-3 ? away.scale(1.0 / distance) : fromPlayer;
			double share = 1.0 - distance / (CYCLONE_RADIUS + 1.0);
			WindStaff.push(e, out.add(fromPlayer).normalize().scale(CYCLONE_PUSH * share * bonus), CYCLONE_LIFT * share * bonus);
		}
	}

	// ---------------------------------------------------------------- the step

	private static void step(ServerPlayer player, ItemStack stack) {
		if (player.getCooldowns().isOnCooldown(stack) || player.isPassenger()) {
			return;
		}
		ServerLevel level = player.level();
		Vec3 flat = player.getLookAngle().horizontal();
		flat = flat.lengthSqr() > 1.0E-4 ? flat.normalize() : Vec3.ZERO;
		Vec3 v = player.isShiftKeyDown() ? flat.scale(-STEP_BACK).add(0.0, STEP_UP + 0.15, 0.0) : flat.scale(STEP_FORWARD).add(0.0, STEP_UP, 0.0);
		player.setDeltaMovement(v);
		player.syncVelocity = true;
		player.fallDistance = 0.0;
		player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		STEPS.put(player.getUUID(), level.getGameTime() + STEP_SOFT_TICKS);
		player.getCooldowns().addCooldown(stack, WindCold.cooldown(player, STEP_COOLDOWN));
		Vec3 at = player.position();
		level.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.1, at.z, 10, 0.3, 0.05, 0.3, 0.06);
		level.sendParticles(ParticleTypes.SMALL_GUST, at.x, at.y + 0.5, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.7F, 1.5F);
	}

	private static void tick(MinecraftServer server) {
		for (Iterator<Map.Entry<UUID, Long>> it = STEPS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<UUID, Long> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null || !player.isAlive()) {
				it.remove();
				continue;
			}
			long now = player.level().getGameTime();
			if (now > entry.getValue() || player.onGround() && entry.getValue() - now < STEP_SOFT_TICKS - 5) {
				player.fallDistance = 0.0;
				it.remove();
			}
		}
		CHAINS.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
	}
}
