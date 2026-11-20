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
 * <li>Eye Beams (V): {@link ZFighterEyeBeams}. Great Breath (V): {@link ZFighterGreatBreath}. Aerial Charge (G):
 * {@link ZFighterAerialCharge}. Thunderclap (G): {@link ZFighterThunderclap}.</li>
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
			ZFighterCreator.init();
			ZFighterEyeBeams.init();
			ZFighterGreatBreath.init();
			ZFighterAerialCharge.init();
			ZFighterThunderclap.init();
			ZFighterBracers.init();
			// Patch 3 techniques
			ZFighterKiShell.init();
			ZFighterTwinDiscs.init();
			ZFighterRingBeam.init();
			ZFighterSwordTechnique.init();
			ZFighterPurgingBeam.init();
			ZFighterStickyBind.init();
			ZFighterRiftMoves.init();
			ZFighterKiDrinkingBarrier.init();
			ZFighterKiCell.init();
		}

	public static void clear() {
		ZFighterRingOfOrbs.clear();
		ZFighterRisingDome.clear();
		ZFighterKiRain.clear();
		ZFighterAfterimage.clear();
		ZFighterSonicSway.clear();
		ZFighterSelfDestruct.clear();
		ZFighterEyeBeams.clear();
		ZFighterGreatBreath.clear();
		ZFighterAerialCharge.clear();
		ZFighterThunderclap.clear();
		ZFighterBracers.clear();
		// Patch 3 techniques
		ZFighterKiShell.clear();
		ZFighterTwinDiscs.clear();
		ZFighterRingBeam.clear();
		ZFighterSwordTechnique.clear();
		ZFighterPurgingBeam.clear();
		ZFighterStickyBind.clear();
		ZFighterRiftMoves.clear();
		ZFighterKiDrinkingBarrier.clear();
		ZFighterKiCell.clear();
	}

	// ---------------------------------------------------------------- the dispatch

	/** Whether {@code index} is one of this patch's techniques. */
	public static boolean art(int index) {
		return index >= ZFighterTraining.DRILL_BEAM && index <= ZFighterTraining.KI_DRINKING_BARRIER || ZFighterCreator.custom(index);
	}

	/** Whether {@code index} is one of this patch's techniques that is held and let go on V. */
	public static boolean charged(int index) {
		return index == ZFighterTraining.DRILL_BEAM || index == ZFighterTraining.TRIANGLE_CANNON || index == ZFighterTraining.NOVA_SPHERE
			|| index == ZFighterTraining.SELF_DESTRUCT || index == ZFighterTraining.EYE_BEAMS || index == ZFighterTraining.GREAT_BREATH
			|| index == ZFighterTraining.TWIN_DISCS || index == ZFighterTraining.RING_BEAM || index == ZFighterTraining.PURGING_BEAM
			|| ZFighterCreator.chargedPlace(index);
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
		int cooldown = ZFighterCreator.custom(index) ? ZFighterCreator.quick(player, live, index) : switch (index) {
			case ZFighterTraining.RING_OF_ORBS -> ZFighterRingOfOrbs.start(player, live);
			case ZFighterTraining.RISING_DOME -> ZFighterRisingDome.start(player, live);
			case ZFighterTraining.KI_RAIN -> ZFighterKiRain.start(player, live);
			case ZFighterTraining.RAGE_SCREAM -> ZFighterRageScream.start(player, live);
			case ZFighterTraining.AFTERIMAGE -> ZFighterAfterimage.start(player, live);
			case ZFighterTraining.AERIAL_CHARGE -> ZFighterAerialCharge.start(player, live);
			case ZFighterTraining.THUNDERCLAP -> ZFighterThunderclap.start(player, live);
			case ZFighterTraining.KI_SHELL -> 0; // passive, no cooldown
			case ZFighterTraining.SWORD_TECHNIQUE -> ZFighterSwordTechnique.start(player, live);
			case ZFighterTraining.STICKY_BIND -> ZFighterStickyBind.start(player, live);
			case ZFighterTraining.RIFT_FIST -> ZFighterRiftMoves.riftFist(player, live);
			case ZFighterTraining.RIFT_STEP -> ZFighterRiftMoves.riftStep(player, live);
			case ZFighterTraining.KI_DRINKING_BARRIER -> ZFighterKiDrinkingBarrier.start(player, live);
			default -> ZFighterSonicSway.start(player, live);
		};
		if (cooldown > 0) {
			ZFighterPower.cooldown(player, ZFighterState.BLAST, cooldown);
		}
		return true;
	}

	/** Ki to begin gathering charged technique {@code index}, and the most a full charge costs. */
	static float min(ServerPlayer player, int index) {
		if (ZFighterCreator.custom(index)) {
			return ZFighterCreator.min(player, index);
		}
		return switch (index) {
			case ZFighterTraining.DRILL_BEAM -> ZFighterDrillBeam.MIN_KI;
			case ZFighterTraining.TRIANGLE_CANNON -> ZFighterTriangleCannon.MIN_KI;
			case ZFighterTraining.SELF_DESTRUCT -> ZFighterSelfDestruct.MIN_KI;
			case ZFighterTraining.EYE_BEAMS -> ZFighterEyeBeams.MIN_KI;
			case ZFighterTraining.GREAT_BREATH -> ZFighterGreatBreath.MIN_KI;
			case ZFighterTraining.TWIN_DISCS -> ZFighterTwinDiscs.min(player);
			case ZFighterTraining.RING_BEAM -> ZFighterRingBeam.min(player);
			case ZFighterTraining.PURGING_BEAM -> ZFighterPurgingBeam.min(player);
			default -> ZFighterNovaSphere.MIN_KI;
		};
	}

	static float max(ServerPlayer player, int index) {
		if (ZFighterCreator.custom(index)) {
			return ZFighterCreator.max(player, index);
		}
		return switch (index) {
			case ZFighterTraining.DRILL_BEAM -> ZFighterDrillBeam.MAX_KI;
			case ZFighterTraining.TRIANGLE_CANNON -> ZFighterTriangleCannon.MAX_KI;
			case ZFighterTraining.SELF_DESTRUCT -> ZFighterSelfDestruct.MAX_KI;
			case ZFighterTraining.EYE_BEAMS -> ZFighterEyeBeams.MAX_KI;
			case ZFighterTraining.GREAT_BREATH -> ZFighterGreatBreath.MAX_KI;
			case ZFighterTraining.TWIN_DISCS -> ZFighterTwinDiscs.max(player);
			case ZFighterTraining.RING_BEAM -> ZFighterRingBeam.max(player);
			case ZFighterTraining.PURGING_BEAM -> ZFighterPurgingBeam.max(player);
			default -> ZFighterNovaSphere.MAX_KI;
		};
	}

	/** Ticks of gathering to a full charge (the HUD bar reads this too; {@code fallback} for the Ki Wave and Ki Bomb). */
	public static int full(int index, int fallback) {
		return switch (index) {
			case ZFighterTraining.DRILL_BEAM -> ZFighterDrillBeam.FULL_TICKS;
			case ZFighterTraining.TRIANGLE_CANNON -> ZFighterTriangleCannon.FULL_TICKS;
			case ZFighterTraining.NOVA_SPHERE -> ZFighterNovaSphere.FULL_TICKS;
			case ZFighterTraining.SELF_DESTRUCT -> ZFighterSelfDestruct.FULL_TICKS;
			case ZFighterTraining.EYE_BEAMS -> ZFighterEyeBeams.FULL_TICKS;
			case ZFighterTraining.GREAT_BREATH -> ZFighterGreatBreath.FULL_TICKS;
			case ZFighterTraining.TWIN_DISCS -> ZFighterTwinDiscs.FULL_TICKS;
			case ZFighterTraining.RING_BEAM -> ZFighterRingBeam.FULL_TICKS;
			case ZFighterTraining.PURGING_BEAM -> ZFighterPurgingBeam.FULL_TICKS;
			default -> ZFighterCreator.custom(index) ? ZFighterCreator.FULL_TICKS : fallback;
		};
	}

	/** V's cooldown after a charged technique went off. */
	static int waveCooldown(ServerPlayer player) {
		return switch (ZFighterTraining.of(player).charged()) {
			case ZFighterTraining.DRILL_BEAM -> DRILL_COOLDOWN;
			case ZFighterTraining.TRIANGLE_CANNON -> TRIANGLE_COOLDOWN;
			case ZFighterTraining.NOVA_SPHERE -> NOVA_COOLDOWN;
			case ZFighterTraining.SELF_DESTRUCT -> NOVA_COOLDOWN;
			case ZFighterTraining.EYE_BEAMS -> ZFighterEyeBeams.COOLDOWN;
			case ZFighterTraining.GREAT_BREATH -> ZFighterGreatBreath.COOLDOWN;
			case ZFighterTraining.TWIN_DISCS -> ZFighterTwinDiscs.cooldown(player);
			case ZFighterTraining.RING_BEAM -> ZFighterRingBeam.cooldown(player);
			case ZFighterTraining.PURGING_BEAM -> ZFighterPurgingBeam.cooldown(player);
			default -> ZFighterCreator.custom(ZFighterTraining.of(player).charged())
				? ZFighterCreator.cooldown(player, ZFighterTraining.of(player).charged()) : ZFighterKi.WAVE_COOLDOWN;
		};
	}

	/** A lang key saying why charged technique {@code index} can't be used now, or null when it can. */
	static @Nullable String blocker(ServerPlayer player, ZFighterPower.Live live, int index) {
		return switch (index) {
			case ZFighterTraining.NOVA_SPHERE -> ZFighterNovaSphere.blocker(live);
			case ZFighterTraining.SELF_DESTRUCT -> ZFighterSelfDestruct.blocker(player);
			case ZFighterTraining.EYE_BEAMS -> ZFighterEyeBeams.blocker(player);
			case ZFighterTraining.GREAT_BREATH -> ZFighterGreatBreath.blocker(player);
			case ZFighterTraining.TWIN_DISCS -> ZFighterTwinDiscs.blocker(live);
			case ZFighterTraining.RING_BEAM -> ZFighterRingBeam.blocker(live);
			case ZFighterTraining.PURGING_BEAM -> ZFighterPurgingBeam.blocker(live);
			default -> ZFighterCreator.custom(index) ? ZFighterCreator.blocker(player, index) : null;
		};
	}

	/** Sneak+V down: whether gathering may begin (otherwise it says why). */
	static boolean canGather(ServerPlayer player, ZFighterPower.Live live, int index) {
		String blocker = blocker(player, live, index);
		if (blocker != null) {
			player.sendOverlayMessage(Component.translatable(blocker).withStyle(ChatFormatting.GRAY));
			return false;
		}
		// (0.18.94) the self-destruct needs someone in reach to grab and hold while it builds
		return index != ZFighterTraining.SELF_DESTRUCT || ZFighterSelfDestruct.start(player);
	}

	/** While V is held: the technique's gathering, drawn and heard ({@code charge} is 0-1). */
	static void gather(ServerPlayer player, ZFighterPower.Live live, int index, float charge) {
		switch (index) {
			case ZFighterTraining.DRILL_BEAM -> ZFighterDrillBeam.gather(player, live, charge);
			case ZFighterTraining.TRIANGLE_CANNON -> ZFighterTriangleCannon.gather(player, live, charge);
			case ZFighterTraining.SELF_DESTRUCT -> ZFighterSelfDestruct.gather(player, live, charge);
			case ZFighterTraining.EYE_BEAMS -> ZFighterEyeBeams.gather(player, live, charge);
			case ZFighterTraining.GREAT_BREATH -> ZFighterGreatBreath.gather(player, live, charge);
			case ZFighterTraining.TWIN_DISCS -> ZFighterTwinDiscs.gather(player, live, charge);
			case ZFighterTraining.RING_BEAM -> ZFighterRingBeam.gather(player, live, charge);
			case ZFighterTraining.PURGING_BEAM -> ZFighterPurgingBeam.gather(player, live, charge);
			default -> {
				if (ZFighterCreator.custom(index)) {
					ZFighterCreator.gather(player, live, index, charge);
				} else {
					ZFighterNovaSphere.gather(player, live, charge);
				}
			}
		}
	}

	/** V let go with the ki already paid: it goes off. */
	static void release(ServerPlayer player, ZFighterPower.Live live, int index, float charge) {
		switch (index) {
			case ZFighterTraining.DRILL_BEAM -> ZFighterDrillBeam.fire(player, live, charge);
			case ZFighterTraining.TRIANGLE_CANNON -> ZFighterTriangleCannon.fire(player, live, charge);
			case ZFighterTraining.SELF_DESTRUCT -> ZFighterSelfDestruct.fire(player, live, charge);
			case ZFighterTraining.EYE_BEAMS -> ZFighterEyeBeams.release(player, live, charge);
			case ZFighterTraining.GREAT_BREATH -> ZFighterGreatBreath.release(player, live, charge);
			case ZFighterTraining.TWIN_DISCS -> ZFighterTwinDiscs.fire(player, live, charge);
			case ZFighterTraining.RING_BEAM -> ZFighterRingBeam.fire(player, live, charge);
			case ZFighterTraining.PURGING_BEAM -> ZFighterPurgingBeam.fire(player, live, charge);
			default -> {
				if (ZFighterCreator.custom(index)) {
					ZFighterCreator.fire(player, live, index, charge);
				} else {
					ZFighterNovaSphere.fire(player, live, charge);
				}
			}
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
