package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * (0.17.54, Andy: "work this kind of sonic sway animation in somewhere") Sonic Sway, a quick technique (G): for
 * {@link #DURATION} ticks he stands nearly still and weaves his upper body from side to side too fast to hit. Every
 * melee blow and every shot aimed at him misses (the damage is cancelled, like {@link ZFighterSidestep}'s), each miss
 * costs {@link #DODGE_KI} ki, and it ends when the ki runs out, when he swings, or when he gathers a charge. The weave
 * and its afterimages are drawn on every client from {@link #SWAY} ({@code ZFighterSwayPose}).
 *
 * <p>{@link #flick} plays one quick lean and one afterimage for a blow slipped some other way (the meditation dodge,
 * silver instinct), through the same state.
 */
public final class ZFighterSonicSway {
	/** Ki to begin, and for each blow or shot that misses. */
	static final float KI = 20.0F;
	static final float DODGE_KI = 2.5F;
	/** Ticks the sway lasts (4 s), and ticks before G is ready again counted from the start (10 s). */
	static final int DURATION = 80;
	static final int COOLDOWN = 200;
	/** Ticks of the single lean a slipped blow plays ({@link #flick}); the client's lean takes this long. */
	public static final int FLICK_TICKS = 8;
	/** Ticks at the start in which a swing still going from before doesn't end the sway. */
	static final int SWING_GRACE = 6;

	/**
	 * What the clients draw, on everyone nearby; absent when he isn't swaying. Packed: bits 0-1 the {@link #MODE_SWAY} or
	 * {@link #MODE_FLICK}, bit 2 which way the last lean went (1 right), bits 3-10 a count of the leans so far (so each
	 * dodge changes the value and snaps the lean).
	 */
	public static final AttachmentType<Integer> SWAY = AttachmentRegistry.create(JaymosPowers.id("zfighter_sway"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	public static final int MODE_SWAY = 1, MODE_FLICK = 2;

	/** Until when each fighter's sway lasts (game time), and each one's count of leans. */
	private static final Map<UUID, Long> UNTIL = new HashMap<>();
	private static final Map<UUID, Integer> COUNT = new HashMap<>();

	private ZFighterSonicSway() {
	}

	public static int mode(int raw) {
		return raw & 3;
	}

	/** Which way the last lean went: 1 to his right, -1 to his left. */
	public static int lean(int raw) {
		return (raw & 4) != 0 ? 1 : -1;
	}

	public static int count(int raw) {
		return raw >> 3 & 0xFF;
	}

	static void init() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || amount <= 0.0F || !swaying(player)) {
				return true;
			}
			boolean melee = source.getEntity() instanceof LivingEntity by && source.getDirectEntity() == by && by != player
				&& !source.is(DamageTypeTags.IS_EXPLOSION) && !source.is(DamageTypeTags.IS_FIRE) && !source.is(DamageTypeTags.BYPASSES_ARMOR);
			boolean shot = source.is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.IS_EXPLOSION);
			if (!melee && !shot) {
				return true;
			}
			ZFighterPower.Live live = ZFighterPower.live(player);
			if (live.guarding || ZFighterPoise.stunned(player)) {
				return true;
			}
			if (!player.isCreative() && !live.raging()) {
				if (live.ki < DODGE_KI) {
					end(player);
					return true;
				}
				live.ki -= DODGE_KI;
			}
			var from = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
			lean(player, from == null ? null : from.position(), MODE_SWAY);
			ServerLevel level = player.level();
			Vec3 at = player.position();
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.5F, 1.9F + player.getRandom().nextFloat() * 0.2F);
			return false;
		});
	}

	static void clear() {
		UNTIL.clear();
		COUNT.clear();
		FLICK_UNTIL.clear();
	}

	/** Whether he is mid-sway (the long one). */
	public static boolean swaying(ServerPlayer player) {
		Long until = UNTIL.get(player.getUUID());
		return until != null && until > player.level().getGameTime();
	}

	/** G: begins the sway. Returns the cooldown, or 0 if it did not go off. */
	static int start(ServerPlayer player, ZFighterPower.Live live) {
		if (live.flying) {
			return 0;
		}
		if (!ZFighterPower.spend(player, live, KI)) {
			return 0;
		}
		ServerLevel level = player.level();
		UNTIL.put(player.getUUID(), level.getGameTime() + DURATION);
		publish(player, MODE_SWAY, player.getRandom().nextBoolean(), false);
		Vec3 at = player.position();
		level.sendParticles(ZFighterPower.dust(live), true, false, at.x, at.y + 1.0, at.z, 8, 0.25, 0.5, 0.25, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BREEZE_JUMP, SoundSource.PLAYERS, 0.6F, 1.8F);
		return COOLDOWN;
	}

	/** Ends the long sway now. */
	static void end(ServerPlayer player) {
		if (UNTIL.remove(player.getUUID()) != null) {
			player.removeAttached(SWAY);
		}
	}

	/** He lost the power, died or left. */
	static void drop(ServerPlayer player) {
		UNTIL.remove(player.getUUID());
		COUNT.remove(player.getUUID());
		FLICK_UNTIL.remove(player.getUUID());
		if (player.hasAttached(SWAY)) {
			player.removeAttached(SWAY);
		}
	}

	private static final Map<UUID, Long> FLICK_UNTIL = new HashMap<>();

	/**
	 * A blow slipped some other way (the meditation dodge, silver instinct): one quick lean away from {@code from}, and
	 * one afterimage. Does nothing to a sway already running but snap its lean.
	 */
	static void flick(ServerPlayer player, Vec3 from) {
		if (swaying(player)) {
			return;
		}
		lean(player, from, MODE_FLICK);
		FLICK_UNTIL.put(player.getUUID(), player.level().getGameTime() + FLICK_TICKS);
	}

	/** Snaps his lean to the side away from {@code from} (random if there is no telling) and syncs it. */
	private static void lean(ServerPlayer player, Vec3 from, int mode) {
		boolean right = player.getRandom().nextBoolean();
		if (from != null) {
			Vec3 side = Vec3.directionFromRotation(0.0F, player.getYRot() + 90.0F);
			double toward = from.subtract(player.position()).dot(side);
			if (Math.abs(toward) > 0.05) {
				// the blow comes from his right, he leans left
				right = toward < 0.0;
			}
		}
		publish(player, mode, right, true);
		ServerLevel level = player.level();
		Vec3 at = player.position();
		DustParticleOptions blur = ZFighterPower.dust(ZFighterPower.live(player));
		level.sendParticles(blur, true, false, at.x, at.y + 1.0, at.z, 4, 0.2, 0.5, 0.2, 0.0);
		level.sendParticles(ParticleTypes.POOF, true, false, at.x, at.y + 0.8, at.z, 2, 0.15, 0.3, 0.15, 0.02);
	}

	private static void publish(ServerPlayer player, int mode, boolean right, boolean count) {
		int n = COUNT.getOrDefault(player.getUUID(), 0);
		if (count) {
			n = n + 1 & 0xFF;
			COUNT.put(player.getUUID(), n);
		}
		int raw = mode | (right ? 4 : 0) | n << 3;
		Integer was = player.getAttached(SWAY);
		if (was == null || was != raw) {
			player.setAttached(SWAY, raw);
		}
	}

	/** Every tick, per Z Fighter: the sway ends on time, when he swings or charges, or the single lean when it is done. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		long now = player.level().getGameTime();
		Long until = UNTIL.get(player.getUUID());
		if (until != null) {
			if (until <= now || live.charging || live.flying || live.guarding
				|| player.isSwinging() && until - now < DURATION - SWING_GRACE) {
				end(player);
			}
			return;
		}
		Long flick = FLICK_UNTIL.get(player.getUUID());
		if (flick != null && flick <= now) {
			FLICK_UNTIL.remove(player.getUUID());
			player.removeAttached(SWAY);
		}
	}
}
