package com.jaymospowers.mortal;

import com.jaymospowers.api.Power;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.darktide.DarktidePower;
import com.jaymospowers.power.gravity.GravityPower;
import com.jaymospowers.power.smoke.SmokePower;
import com.jaymospowers.power.web.WebPower;
import com.jaymospowers.power.wind.WindPower;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.jaymospowers.power.zfighter.ZFighterPower;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Launcher, juggle and slam, for those who can follow a foe into the air: the powers that fly (Wind, Z Fighter, Smoke,
 * Xenoform) and Web, Darktide and Gravity ({@link #can}). All three ride on a plain melee blow:
 * <ul>
 * <li><b>Launcher:</b> sneaking, on the ground, a blow at something on the ground throws it {@link #LAUNCH} up and you
 * after it ({@link #LAUNCH_WAIT} ticks between launches). Nothing too big ({@link #BIGGEST} blocks tall) or that
 * cannot be shoved.</li>
 * <li><b>Juggle:</b> each further blow on it before it lands bumps it back up, up to {@link #JUGGLES} times, and holds
 * you up with it.</li>
 * <li><b>Slam:</b> a sneaking blow on it in the air drives it down; where it lands it takes {@link #SLAM_DAMAGE} and
 * {@link #SLAM_PER_JUGGLE} more for each juggle, and whatever stands within {@link #SLAM_WIDE} takes
 * {@link #SLAM_SPLASH}.</li>
 * </ul>
 */
public final class AirCombo {
	static final double LAUNCH = 0.95, FOLLOW = 0.9, BUMP = 0.5, HOVER = 0.35, DRIVE = -1.8, BIGGEST = 3.5;
	static final int LAUNCH_WAIT = 120, AIRBORNE = 60, JUGGLES = 5, SLAM_TICKS = 60;
	static final float SLAM_DAMAGE = 6.0F, SLAM_PER_JUGGLE = 1.0F, SLAM_SPLASH = 3.0F;
	static final double SLAM_WIDE = 2.5;

	private record Up(UUID by, long until, int juggles) {
	}

	private record Down(UUID by, long until, float damage) {
	}

	private static final Map<LivingEntity, Up> UP = new HashMap<>();
	private static final Map<LivingEntity, Down> DOWN = new HashMap<>();
	private static final Map<UUID, Long> READY = new HashMap<>();

	private AirCombo() {
	}

	static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (!FallingAttacks.landing && taken > 0.0F && source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() instanceof ServerPlayer player && entity != player
				&& entity.isAlive() && entity.level() instanceof ServerLevel level) {
				blow(level, player, entity);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(AirCombo::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			UP.clear();
			DOWN.clear();
			READY.clear();
		});
	}

	/** Whether {@code player}'s power lets them fight in the air. */
	public static boolean can(Player player) {
		Power power = Powers.of(player);
		return power == WindPower.INSTANCE || power == ZFighterPower.INSTANCE || power == SmokePower.INSTANCE || power == XenoformPower.INSTANCE
			|| power == WebPower.INSTANCE || power == DarktidePower.INSTANCE || power == GravityPower.INSTANCE;
	}

	private static void blow(ServerLevel level, ServerPlayer player, LivingEntity target) {
		long now = level.getGameTime();
		Up up = UP.get(target);
		if (up != null && up.by().equals(player.getUUID()) && !target.onGround()) {
			if (player.isShiftKeyDown()) {
				// the slam
				UP.remove(target);
				Vec3 look = player.getLookAngle();
				fling(target, new Vec3(look.x * 0.4, DRIVE, look.z * 0.4));
				DOWN.put(target, new Down(player.getUUID(), now + SLAM_TICKS, SLAM_DAMAGE + SLAM_PER_JUGGLE * up.juggles()));
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 1.3F, 0.5F);
			} else if (up.juggles() < JUGGLES) {
				UP.put(target, new Up(up.by(), now + AIRBORNE, up.juggles() + 1));
				fling(target, new Vec3(0.0, BUMP, 0.0));
				fling(player, new Vec3(player.getDeltaMovement().x, HOVER, player.getDeltaMovement().z));
				player.resetFallDistance();
			}
			return;
		}
		if (player.isShiftKeyDown() && player.onGround() && target.onGround() && can(player) && now >= READY.getOrDefault(player.getUUID(), 0L)
			&& target.getBbHeight() <= BIGGEST && target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < 1.0) {
			READY.put(player.getUUID(), now + LAUNCH_WAIT);
			UP.put(target, new Up(player.getUUID(), now + AIRBORNE, 0));
			fling(target, new Vec3(0.0, LAUNCH, 0.0));
			fling(player, new Vec3(player.getDeltaMovement().x, FOLLOW, player.getDeltaMovement().z));
			level.sendParticles(ParticleTypes.CLOUD, target.getX(), target.getY() + 0.1, target.getZ(), 10, 0.3, 0.05, 0.3, 0.05);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.2F, 1.2F);
		}
	}

	private static void fling(Entity entity, Vec3 velocity) {
		entity.setDeltaMovement(velocity);
		entity.needsSync = true;
		entity.syncVelocity = true;
	}

	private static void tick(MinecraftServer server) {
		if (UP.isEmpty() && DOWN.isEmpty()) {
			return;
		}
		UP.entrySet().removeIf(e -> !e.getKey().isAlive() || e.getKey().level().getGameTime() > e.getValue().until()
			|| e.getKey().onGround() && e.getKey().level().getGameTime() > e.getValue().until() - AIRBORNE + 5);
		for (Iterator<Map.Entry<LivingEntity, Down>> it = DOWN.entrySet().iterator(); it.hasNext();) {
			Map.Entry<LivingEntity, Down> e = it.next();
			LivingEntity target = e.getKey();
			if (!target.isAlive() || !(target.level() instanceof ServerLevel level) || level.getGameTime() > e.getValue().until()) {
				it.remove();
				continue;
			}
			if (!target.onGround() && !target.isInWater()) {
				continue;
			}
			it.remove();
			ServerPlayer by = server.getPlayerList().getPlayer(e.getValue().by());
			var source = by != null ? by.damageSources().playerAttack(by) : level.damageSources().generic();
			target.setInvulnerableTime(0);
			target.hurtServer(level, source, e.getValue().damage());
			for (LivingEntity near : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(SLAM_WIDE, 1.0, SLAM_WIDE),
				n -> n != target && n != by && n.isAlive() && !n.isSpectator())) {
				near.hurtServer(level, source, SLAM_SPLASH);
			}
			level.sendParticles(ParticleTypes.EXPLOSION, target.getX(), target.getY() + 0.2, target.getZ(), 2, 0.4, 0.0, 0.4, 0.0);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 0.7F);
		}
	}
}
