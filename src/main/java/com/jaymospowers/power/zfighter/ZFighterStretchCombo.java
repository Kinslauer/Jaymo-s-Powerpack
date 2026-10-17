package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Shakes;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.13.30) A Verdant's hold combo stretches: any blow at a foe out of plain reach shoots the arm (a leg, for kicks) out
 * to it, up to {@link #STRETCH_RANGE} blocks, and some blows at a foe in reach stretch anyway for show. The foe stays
 * locked across the stretched blows; the Verdant lunges less and stretches more. The finisher is a double-arm stretch
 * slam (the rising dragon fist stays one arm, stretched up). Everyone sees the limb go out and snap back ({@link #REACH},
 * drawn by the client's {@code ZFighterStretchPose}). Other lineages' combos are untouched.
 */
public final class ZFighterStretchCombo {
	/** How far a stretched blow reaches (blocks, eyes to the foe's edge). */
	public static final double STRETCH_RANGE = 7.0;
	/** A stretched blow hits for this share of the same blow up close (knockback unchanged); the finisher's slam hits in full. */
	static final float STRETCH_DAMAGE = 0.85F;
	/** A stretched blow takes longer, somewhere between these (times the blow): the wind-up, the reach, the snap back. */
	static final float STRETCH_LENGTH_MIN = 1.2F;
	static final float STRETCH_LENGTH_MAX = 1.5F;
	/** The chance a blow at a foe already in reach (never the opener) stretches anyway, for show. */
	static final float FLAIR_CHANCE = 0.3F;
	/** A Verdant's lunge after a foe a little out of reach, and his step into a stretched blow, as shares of the usual. */
	static final double LUNGE_SHARE = 0.35;
	static final double STEP_SHARE = 0.4;
	/**
	 * The limb's travel, as shares of the blow: it starts out at {@code OUT_FROM}, is all the way out at {@code OUT_PEAK}
	 * (the blow lands at half), held to {@code SNAP_AT} (the snap back is heard), home by {@code HOME_AT}.
	 */
	public static final float OUT_FROM = 0.22F, OUT_PEAK = 0.5F, SNAP_AT = 0.6F, HOME_AT = 0.8F;
	/** Slime bits strung along a stretched limb when it hits (per block), and at the far hand. */
	static final int TRAIL_PER_BLOCK = 2;
	static final int IMPACT_BITS = 8;
	static final int SLAM_BITS = 22;
	/** The slam's shake, for everyone near (on top of the finisher's own). */
	static final float SLAM_SHAKE = 0.3F;

	/** The limb that stretches. */
	public static final int RIGHT_ARM = 0, LEFT_ARM = 1, RIGHT_LEG = 2, LEFT_LEG = 3, BOTH_ARMS = 4;

	/** A stretched blow: when it began (game time), how long it takes, the limb, how far (blocks), at whom (entity id, -1: nothing). */
	public record Reach(long start, int ticks, int limb, float reach, int target) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Reach> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Reach::start, ByteBufCodecs.VAR_INT, Reach::ticks, ByteBufCodecs.VAR_INT, Reach::limb,
			ByteBufCodecs.FLOAT, Reach::reach, ByteBufCodecs.VAR_INT, Reach::target, Reach::new);
	}

	/** The stretch under way, for everyone's client. Not saved. */
	public static final AttachmentType<Reach> REACH = AttachmentRegistry.create(JaymosPowers.id("zfighter_combo_stretch"),
		builder -> builder.syncWith(Reach.CODEC, AttachmentSyncPredicate.all()));

	private ZFighterStretchCombo() {
	}

	/** A Verdant, and not a Great Ape: his combo stretches. */
	static boolean verdant(ServerPlayer player) {
		return ZFighterLineage.of(player) == ZFighterLineage.VERDANT && !ZFighterLineage.ape(player);
	}

	/** How much further than plain reach a Verdant keeps a foe locked (blocks past his interaction range). */
	static double lockBonus(ServerPlayer player) {
		return STRETCH_RANGE - player.entityInteractionRange();
	}

	/**
	 * Whether this blow stretches, and with what: always at a foe out of plain reach, and the finisher; now and then
	 * otherwise. -1: a plain blow.
	 */
	static int limb(ServerPlayer player, @Nullable LivingEntity target, int move, int step, boolean finisher, double plainBonus) {
		if (!verdant(player)) {
			return -1;
		}
		int limb = finisher && move != ZFighterCombo.DRAGON_FIST || move == ZFighterCombo.DOUBLE_PALM ? BOTH_ARMS
			: ZFighterCombo.kick(move) ? RIGHT_LEG : move == ZFighterCombo.JAB ? LEFT_ARM : RIGHT_ARM;
		if (finisher || target != null && !player.isWithinEntityInteractionRange(target, plainBonus)) {
			return limb;
		}
		return step > 0 && player.getRandom().nextFloat() < FLAIR_CHANCE ? limb : -1;
	}

	/** A stretched blow takes a little longer, by a different amount each time. */
	static int length(ServerPlayer player, int length) {
		float k = STRETCH_LENGTH_MIN + player.getRandom().nextFloat() * (STRETCH_LENGTH_MAX - STRETCH_LENGTH_MIN);
		return Math.round(length * k);
	}

	/** The limb winds up to go: what it's for, and how far (to the foe, or along the look to the wall). */
	static void begin(ServerPlayer player, int limb, int ticks, @Nullable LivingEntity target) {
		ServerLevel level = player.level();
		float reach = (float)(target == null ? along(player) : player.getEyePosition().distanceTo(aim(target, limb)));
		player.setAttached(REACH, new Reach(level.getGameTime(), ticks, limb, reach, target == null ? -1 : target.getId()));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP_SMALL, SoundSource.PLAYERS, 0.7F,
			1.3F + level.getRandom().nextFloat() * 0.3F);
	}

	/** A plain blow (or the string's end): no limb out. */
	static void clear(ServerPlayer player) {
		if (player.hasAttached(REACH)) {
			player.removeAttached(REACH);
		}
	}

	/** Whether a stretched blow can reach the foe now: in range and in sight. */
	static boolean reaches(ServerPlayer player, LivingEntity target) {
		return player.isWithinEntityInteractionRange(target, lockBonus(player)) && player.hasLineOfSight(target);
	}

	/** The limb snaps back home: a wet whip of a sound. */
	static void snap(ServerPlayer player) {
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.8F,
			1.6F + level.getRandom().nextFloat() * 0.2F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.6F, 1.4F);
	}

	/** It lands at range: slime strung along the limb, a splat at the far hand (bigger and heavier for the slam). */
	static void impact(ServerPlayer player, int limb, Vec3 at, boolean finisher) {
		ServerLevel level = player.level();
		Vec3 from = player.position().add(0.0, limb == RIGHT_LEG || limb == LEFT_LEG ? 0.75 : 1.35, 0.0);
		trail(level, from, at);
		level.sendParticles(ParticleTypes.ITEM_SLIME, true, false, at.x, at.y, at.z, finisher ? SLAM_BITS : IMPACT_BITS, 0.25, 0.25, 0.25, 0.15);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_ATTACK, SoundSource.PLAYERS, 0.8F, finisher ? 0.7F : 1.2F);
		if (finisher && limb == BOTH_ARMS) {
			level.sendParticles(ParticleTypes.CLOUD, true, false, at.x, at.y - 0.4, at.z, 10, 0.5, 0.1, 0.5, 0.06);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, 1.2F, 0.6F);
			Shakes.felt(player, at, 10.0, SLAM_SHAKE, Shakes.ticksFor(SLAM_SHAKE));
		}
	}

	/** It reaches out at nothing (or at a foe it can't reach any more): a slap of air where the hand ends up. */
	static void whiff(ServerPlayer player, Reach reach) {
		Vec3 end = player.getEyePosition().add(player.getViewVector(1.0F).scale(reach.reach()));
		player.level().sendParticles(ParticleTypes.ITEM_SLIME, true, false, end.x, end.y, end.z, 4, 0.15, 0.15, 0.15, 0.05);
	}

	private static void trail(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 span = to.subtract(from);
		int bits = Math.max(2, (int)(span.length() * TRAIL_PER_BLOCK));
		for (int i = 1; i < bits; i++) {
			Vec3 p = from.add(span.scale(i / (double)bits));
			level.sendParticles(ParticleTypes.ITEM_SLIME, true, false, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
		}
	}

	/** Where on the foe the limb goes: the chest for a fist, lower for a foot. */
	private static Vec3 aim(LivingEntity target, int limb) {
		double up = limb == RIGHT_LEG || limb == LEFT_LEG ? 0.4 : 0.6;
		return target.position().add(0.0, target.getBbHeight() * up, 0.0);
	}

	/** How far it goes at nothing: the full stretch, or to the wall in the way. */
	private static double along(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getViewVector(1.0F).scale(STRETCH_RANGE));
		HitResult wall = player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		return wall.getType() == HitResult.Type.MISS ? STRETCH_RANGE : wall.getLocation().distanceTo(eye);
	}

	/** How far out the limb is now, 0-1: out from {@link #OUT_FROM} to {@link #OUT_PEAK}, held, snapped home by {@link #HOME_AT}. */
	public static float out(Reach reach, long now, float partial) {
		float t = (now - reach.start() + partial) / Math.max(1, reach.ticks());
		if (t <= OUT_FROM || t >= HOME_AT) {
			return 0.0F;
		}
		if (t < OUT_PEAK) {
			// accelerating out
			float k = (t - OUT_FROM) / (OUT_PEAK - OUT_FROM);
			return k * k * (2.0F - k);
		}
		if (t < SNAP_AT) {
			return 1.0F;
		}
		// snapped back, fast then settling
		float k = (t - SNAP_AT) / (HOME_AT - SNAP_AT);
		return (1.0F - k) * (1.0F - k);
	}

	/** Whether it's still on its way out (the slam comes down from overhead then). */
	public static boolean outward(Reach reach, long now, float partial) {
		return (now - reach.start() + partial) / Math.max(1, reach.ticks()) < OUT_PEAK;
	}
}
