package com.jaymospowers.power.zfighter;

import com.jaymospowers.core.LockOn;
import com.jaymospowers.core.wounds.Downed;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.46, Andy's NOEA picks) The eight techniques of round 2's second patch, and the one place the slot code calls them.
 * The quick ones go on G ({@link #quick}); the charged ones go on the sneak+V hold and fit {@link ZFighterKi}'s own
 * start, gather, release path through {@link #canGather}, {@link #gather} and {@link #release}. When a technique slot
 * changes (more slots to a bar), only who calls these has to change.
 * <ul>
 * <li>Drill Beam (V): {@link ZFighterDrillBeam}. Ring of Orbs (G): {@link ZFighterRingOfOrbs}.</li>
 * <li>Triangle Cannon (V): {@link ZFighterTriangleCannon}. Rising Dome (G): {@link ZFighterRisingDome}.</li>
 * <li>Ki Rain (G): {@link ZFighterKiRain}. Nova Sphere (V): {@link ZFighterNovaSphere}.</li>
 * <li>Rage Scream (G): {@link ZFighterRageScream}. Afterimage (G): {@link ZFighterAfterimage}.</li>
 * <li>Sonic Sway (G): {@link ZFighterSonicSway}.</li>
 * </ul>
 */
public final class ZFighterArts {
	/** Ticks before V fires again after a charged one of these (the Ki Wave's is {@link ZFighterKi#WAVE_COOLDOWN}). */
	static final int DRILL_COOLDOWN = 140, TRIANGLE_COOLDOWN = 240, NOVA_COOLDOWN = 400;

	private ZFighterArts() {
	}

	public static void init() {
		ZFighterRingOfOrbs.init();
		ZFighterRisingDome.init();
		ZFighterKiRain.init();
		ZFighterAfterimage.init();
		ZFighterSonicSway.init();
	}

	public static void clear() {
		ZFighterRingOfOrbs.clear();
		ZFighterRisingDome.clear();
		ZFighterKiRain.clear();
		ZFighterAfterimage.clear();
		ZFighterSonicSway.clear();
	}

	// ---------------------------------------------------------------- the dispatch

	/** Whether {@code index} is one of this patch's techniques. */
	public static boolean art(int index) {
		return index >= ZFighterTraining.DRILL_BEAM && index <= ZFighterTraining.SONIC_SWAY;
	}

	/** Whether {@code index} is one of this patch's techniques that is held and let go on V. */
	public static boolean charged(int index) {
		return index == ZFighterTraining.DRILL_BEAM || index == ZFighterTraining.TRIANGLE_CANNON || index == ZFighterTraining.NOVA_SPHERE;
	}

	/**
	 * G, when one of the quick techniques of this patch is on it: does it. False if it is some other technique's turn;
	 * true once it has had its turn (done, refused for want of a foe or ki, or swallowed by a stagger). The caller has checked
	 * G is ready.
	 */
	static boolean quick(ServerPlayer player, ZFighterPower.Live live, int index) {
		if (!art(index) || charged(index)) {
			return false;
		}
		if (ZFighterPoise.stunned(player) || live.guarding || live.charging || ZFighterLineage.ape(player)) {
			return true;
		}
		int cooldown = switch (index) {
			case ZFighterTraining.RING_OF_ORBS -> ZFighterRingOfOrbs.start(player, live);
			case ZFighterTraining.RISING_DOME -> ZFighterRisingDome.start(player, live);
			case ZFighterTraining.KI_RAIN -> ZFighterKiRain.start(player, live);
			case ZFighterTraining.RAGE_SCREAM -> ZFighterRageScream.start(player, live);
			case ZFighterTraining.AFTERIMAGE -> ZFighterAfterimage.start(player, live);
			default -> ZFighterSonicSway.start(player, live);
		};
		if (cooldown > 0) {
			ZFighterPower.cooldown(player, ZFighterState.BLAST, cooldown);
		}
		return true;
	}

	/** Ki to begin gathering charged technique {@code index}, and the most a full charge costs. */
	static float min(int index) {
		return switch (index) {
			case ZFighterTraining.DRILL_BEAM -> ZFighterDrillBeam.MIN_KI;
			case ZFighterTraining.TRIANGLE_CANNON -> ZFighterTriangleCannon.MIN_KI;
			default -> ZFighterNovaSphere.MIN_KI;
		};
	}

	static float max(int index) {
		return switch (index) {
			case ZFighterTraining.DRILL_BEAM -> ZFighterDrillBeam.MAX_KI;
			case ZFighterTraining.TRIANGLE_CANNON -> ZFighterTriangleCannon.MAX_KI;
			default -> ZFighterNovaSphere.MAX_KI;
		};
	}

	/** Ticks of gathering to a full charge (the HUD bar reads this too; {@code fallback} for the Ki Wave and Ki Bomb). */
	public static int full(int index, int fallback) {
		return switch (index) {
			case ZFighterTraining.DRILL_BEAM -> ZFighterDrillBeam.FULL_TICKS;
			case ZFighterTraining.TRIANGLE_CANNON -> ZFighterTriangleCannon.FULL_TICKS;
			case ZFighterTraining.NOVA_SPHERE -> ZFighterNovaSphere.FULL_TICKS;
			default -> fallback;
		};
	}

	/** V's cooldown after a charged technique went off. */
	static int waveCooldown(ServerPlayer player) {
		return switch (ZFighterTraining.of(player).charged()) {
			case ZFighterTraining.DRILL_BEAM -> DRILL_COOLDOWN;
			case ZFighterTraining.TRIANGLE_CANNON -> TRIANGLE_COOLDOWN;
			case ZFighterTraining.NOVA_SPHERE -> NOVA_COOLDOWN;
			default -> ZFighterKi.WAVE_COOLDOWN;
		};
	}

	/** A lang key saying why charged technique {@code index} can't be used now, or null when it can. */
	static @Nullable String blocker(ServerPlayer player, ZFighterPower.Live live, int index) {
		return index == ZFighterTraining.NOVA_SPHERE ? ZFighterNovaSphere.blocker(live) : null;
	}

	/** Sneak+V down: whether gathering may begin (otherwise it says why). */
	static boolean canGather(ServerPlayer player, ZFighterPower.Live live, int index) {
		String blocker = blocker(player, live, index);
		if (blocker != null) {
			player.sendOverlayMessage(Component.translatable(blocker).withStyle(ChatFormatting.GRAY));
			return false;
		}
		return true;
	}

	/** While V is held: the technique's gathering, drawn and heard ({@code charge} is 0-1). */
	static void gather(ServerPlayer player, ZFighterPower.Live live, int index, float charge) {
		switch (index) {
			case ZFighterTraining.DRILL_BEAM -> ZFighterDrillBeam.gather(player, live, charge);
			case ZFighterTraining.TRIANGLE_CANNON -> ZFighterTriangleCannon.gather(player, live, charge);
			default -> ZFighterNovaSphere.gather(player, live, charge);
		}
	}

	/** V let go with the ki already paid: it goes off. */
	static void release(ServerPlayer player, ZFighterPower.Live live, int index, float charge) {
		switch (index) {
			case ZFighterTraining.DRILL_BEAM -> ZFighterDrillBeam.fire(player, live, charge);
			case ZFighterTraining.TRIANGLE_CANNON -> ZFighterTriangleCannon.fire(player, live, charge);
			default -> ZFighterNovaSphere.fire(player, live, charge);
		}
	}

	// ---------------------------------------------------------------- shared by the techniques

	/** Whether {@code living} is a fair mark for {@code player}'s ki: alive, hittable, not downed, not a friend he can't harm. */
	static boolean valid(ServerPlayer player, LivingEntity living) {
		if (living == player || !living.isAlive() || !living.isAttackable() || living.isSpectator() || living.isPassengerOfSameVehicle(player)
			|| Downed.isDowned(living)) {
			return false;
		}
		return !(living instanceof Player other) || player.canHarmPlayer(other);
	}

	/** The mark: the locked one if it is within {@code range}, else the nearest in front that he can see. */
	static @Nullable LivingEntity find(ServerPlayer player, double range) {
		LivingEntity locked = LockOn.target(player);
		if (locked != null && valid(player, locked) && player.distanceTo(locked) <= range) {
			return locked;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (Entity entity : player.level().getEntities(player, new AABB(eye, eye).inflate(range))) {
			if (!(entity instanceof LivingEntity living) || !valid(player, living)) {
				continue;
			}
			Vec3 to = living.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > range || distance < 1.0E-3) {
				continue;
			}
			double facing = to.scale(1.0 / distance).dot(look);
			double score = distance * (2.0 - facing);
			if (facing >= 0.6 && score < bestScore && player.hasLineOfSight(living)) {
				best = living;
				bestScore = score;
			}
		}
		return best;
	}

	/** Whether a mob could tear blocks up here (the mob griefing rule): what decides whether ki may break the ground. */
	static boolean griefing(ServerLevel level) {
		return level.getGameRules().get(GameRules.MOB_GRIEFING);
	}

	/** Tells him there was nothing to aim at. */
	static void noFoe(ServerPlayer player) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_arts_nofoe").withStyle(ChatFormatting.GRAY));
	}

	/** His ki's colour: the form's, or his own in base form. */
	static int color(ZFighterPower.Live live) {
		return ZFighterKi.formColor(live);
	}
}
