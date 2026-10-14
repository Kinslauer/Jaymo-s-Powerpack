package com.jaymospowers.power.smoke;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.wind.WindFlight;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/**
 * R, Vapour Form: everything below your waist turns to smoke and you fly on it, Wind-style (the client flies you:
 * {@code SmokeFlightClient}; hover, Sprint to cruise, S to brake). R again comes back down, as does Sneak on the ground.
 * It costs hunger, more the faster you go, and ends when you're too hungry. Your smoke half pours a thick trail into
 * the {@link SmokeField}; arrows and other shots pass through it (blows, fire and blasts still land). Everyone sees the
 * smoke legs ({@link #FORM}).
 */
public final class SmokeVapour {
	/** Smoke a tick your legs pour out (a block's worth is 1); a fresh awakening's (only this ability) is thinner. */
	static final float TRAIL = 0.6F;
	static final float STARTER_TRAIL = 0.35F;
	/** Hunger a tick, plus a tick per block moved. At or below this food you can't keep it up. */
	static final float EXHAUSTION = 0.01F;
	static final float EXHAUSTION_PER_BLOCK = 0.012F;
	static final int MIN_FOOD = 6;
	/** Standing on the ground this long (after the take-off's grace) ends it. */
	static final int GROUNDED_TICKS = 30;
	static final int TAKEOFF_GRACE = 10;

	/** On a player in Vapour Form: synced to everyone, who draw the smoke legs. */
	public static final AttachmentType<Boolean> FORM = AttachmentRegistry.create(JaymosPowers.id("smoke_vapour"),
		builder -> builder.syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** Each flier's last position and ticks on the ground. */
	private static final Map<UUID, Vec3> LAST = new HashMap<>();
	private static final Map<UUID, Integer> GROUNDED = new HashMap<>();

	private SmokeVapour() {
	}

	static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || !active(player) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				return true;
			}
			if (source.is(DamageTypeTags.IS_FALL)) {
				return false;
			}
			if (!(source.getDirectEntity() instanceof Projectile) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)) {
				return true;
			}
			// the shot goes through the smoke: a swirl where it went in
			ServerLevel level = player.level();
			Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.3, 0.0);
			level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 10, 0.3, 0.4, 0.3, 0.02);
			SmokeField.puff(level, at, 0.8F, player);
			return false;
		});
	}

	/** In Vapour Form (both sides). */
	public static boolean active(Player player) {
		return player.getAttached(FORM) != null && Powers.has(player, SmokePower.INSTANCE);
	}

	/** R: into Vapour Form, or back out of it. */
	static void toggle(ServerPlayer player) {
		if (player.getAttached(FORM) != null) {
			end(player);
		} else if (SmokePower.ready(player, SmokeState.VAPOUR)) {
			start(player);
		}
	}

	static void start(ServerPlayer player) {
		if (!WindFlight.canFly(player) || player.isFallFlying() || spent(player)) {
			return;
		}
		ServerLevel level = player.level();
		SmokePower.set(player, SmokePower.state(player).withVapour(level.getGameTime()));
		player.setAttached(FORM, true);
		player.resetFallDistance();
		LAST.put(player.getUUID(), player.position());
		GROUNDED.remove(player.getUUID());
		// the legs go up in a gout of smoke
		Vec3 at = player.position().add(0.0, 0.4, 0.0);
		SmokeField.puff(level, at, 6.0F, player);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 30, 0.4, 0.3, 0.4, 0.06);
		level.sendParticles(ParticleTypes.WHITE_SMOKE, at.x, at.y, at.z, 20, 0.4, 0.2, 0.4, 0.04);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.6F, 0.6F);
	}

	static void tick(ServerPlayer player) {
		if (player.getAttached(FORM) == null) {
			return;
		}
		ServerLevel level = player.level();
		UUID id = player.getUUID();
		long since = level.getGameTime() - SmokePower.state(player).vapourFrom();
		int grounded = player.onGround() && since > TAKEOFF_GRACE ? GROUNDED.getOrDefault(id, 0) + 1 : 0;
		GROUNDED.put(id, grounded);
		// landed (Sneak on the ground, or stood there a while), out of food, or something else has the body
		if (!player.isAlive() || !WindFlight.canFly(player) || player.isFallFlying() || spent(player)
			|| grounded > 0 && player.isShiftKeyDown() || grounded >= GROUNDED_TICKS) {
			end(player);
			return;
		}
		player.resetFallDistance();
		Vec3 now = player.position();
		Vec3 last = LAST.put(id, now);
		double moved = last == null ? 0.0 : Math.min(4.0, now.distanceTo(last));
		player.causeFoodExhaustion(EXHAUSTION + (float)moved * EXHAUSTION_PER_BLOCK);
		// the legs pour out a trail, from the hips down
		SmokeField.puff(level, now.add(0.0, player.getBbHeight() * 0.25, 0.0), Mastery.starterOnly(player) ? STARTER_TRAIL : TRAIL, player);
		if (player.tickCount % 16 == 0) {
			level.playSound(null, now.x, now.y, now.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.25F,
				0.4F + level.getRandom().nextFloat() * 0.2F);
		}
	}

	private static boolean spent(ServerPlayer player) {
		return !player.getAbilities().instabuild && player.getFoodData().getFoodLevel() <= MIN_FOOD;
	}

	/** Back to flesh: the legs come back and the cooldown starts. */
	static void end(ServerPlayer player) {
		LAST.remove(player.getUUID());
		GROUNDED.remove(player.getUUID());
		if (player.getAttached(FORM) == null) {
			return;
		}
		player.removeAttached(FORM);
		SmokePower.set(player, SmokePower.state(player).withVapour(-1L));
		SmokePower.cooldown(player, SmokeState.VAPOUR, SmokePower.VAPOUR_COOLDOWN);
		Vec3 at = player.position().add(0.0, 0.4, 0.0);
		SmokeField.puff(player.level(), at, 2.5F, player);
		player.level().playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.7F, 1.2F);
	}

	static void clear() {
		LAST.clear();
		GROUNDED.clear();
	}
}
