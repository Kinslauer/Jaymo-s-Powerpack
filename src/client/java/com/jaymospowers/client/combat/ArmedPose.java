package com.jaymospowers.client.combat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.PoseBlend;
import com.jaymospowers.client.PoseTakeover;
import com.jaymospowers.client.power.wind.CombatAnimCompat;
import com.jaymospowers.combat.Armed;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The body of someone with a weapon out (Andy's picks from Star Wars: Galaxy in a Cube, ideas only: see {@link Armed}).
 * Until now a held sword hung from a walking arm like a stick. Drawn on every player, from what they hold:
 * <ul>
 * <li><b>The stance.</b> Standing still: the weapon shoulder turned forward, the blade held out ahead, the other hand
 * back, the feet set apart, the weight shifting slowly ({@link #BREATH_TICKS}). Walking, the blade is carried low at the
 * side; running, it trails behind and the body leans into it. A two-handed weapon is held in both hands before the
 * body; a polearm stands upright at the side and comes across the body on the move.</li>
 * <li><b>The draw.</b> Taking a weapon into the hand plays a short draw ({@link #DRAW_TICKS}): from the far hip for a
 * one-handed blade, from over the shoulder for the big ones, swept up and round into the stance, with a ring of steel.
 * A swing or a guard cuts it short: it never costs a blow.</li>
 * <li><b>The crouch.</b> Sneaking, the stance sinks into a low guard with the blade levelled ahead.</li>
 * </ul>
 * It gives way to everything else: a swing (vanilla's or Better Combat's, whose combos are untouched), a raised guard
 * ({@link ParryPose}), using an item, and every pose of ours ({@link PoseTakeover#posedOtherwise}). It is a pose of
 * ours itself, so Entity Model Features pauses for it as it does for the Fang Gauntlets' guard.
 */
public final class ArmedPose {
	// ===== the knobs ================================================================================================
	/** How long the draw takes (ticks), and the share of it spent reaching for the weapon before it sweeps out. */
	static final int DRAW_TICKS = 10;
	static final float DRAW_REACH = 0.3F;
	/** The slow shift of weight standing still: its period (ticks), and how far the arm and the hips move with it (radians). */
	static final float BREATH_TICKS = 80.0F, BREATH_ARM = 0.035F, BREATH_ROLL = 0.018F;
	/** Walking speed (the model's, 0-1) at which the standing stance has gone, and from which to which the run comes in. */
	static final float WALK_FULL = 0.5F, RUN_FROM = 0.75F, RUN_FULL = 1.0F;
	/** How much of the walk's own arm swing the weapon arm keeps. */
	static final float ARM_SWAY = 0.2F;
	/** Standing: the weapon shoulder turned forward (radians), the body lowered (pixels), the feet set fore and aft and apart. */
	static final float STANCE_YAW = 0.3F, STANCE_DROP = 0.6F, STANCE_STRIDE = 0.2F, STANCE_SPREAD = 0.08F;
	/** Running: the lean into it (radians). */
	static final float RUN_LEAN = 0.12F;
	/** Crouched: the extra turn of the weapon shoulder. */
	static final float CROUCH_YAW = 0.1F;
	/** How quick the stance comes up and goes (the weight's spring, times its usual). */
	static final float SPEED = 1.2F;

	/** Arm angles (x raised ahead when negative, y turned in toward the chest, z lifted out to the side), weapon arm then other arm, as a right-hander's. */
	static final float[] ONE_STILL = {-0.75F, 0.25F, 0.1F, 0.25F, 0.0F, 0.25F};
	static final float[] ONE_WALK = {-0.45F, 0.1F, 0.12F};
	static final float[] ONE_RUN = {0.4F, 0.0F, 0.28F};
	static final float[] ONE_CROUCH = {-1.25F, 0.35F, 0.0F, 0.3F, 0.0F, 0.35F};
	static final float[] TWO_STILL = {-0.95F, 0.35F, 0.0F, -1.0F, 0.75F, 0.0F};
	static final float[] TWO_RUN = {-0.4F, 0.2F, 0.0F, -0.6F, 0.9F, 0.0F};
	static final float[] TWO_CROUCH = {-1.3F, 0.4F, 0.0F, -1.35F, 0.8F, 0.0F};
	static final float[] POLE_STILL = {-0.35F, 0.15F, 0.1F};
	static final float[] POLE_MOVE = {-0.6F, 0.2F, 0.0F, -1.1F, 0.7F, 0.0F};
	static final float[] POLE_CROUCH = {-0.9F, 0.3F, 0.0F, -1.25F, 0.75F, 0.0F};
	/** The draw's reach, weapon arm only: across to the far hip (a one-handed blade), up over the shoulder (the big ones); then the top of the sweep out. */
	static final float[] DRAW_HIP = {-0.5F, 1.1F, 0.0F}, DRAW_SHOULDER = {-2.9F, 0.0F, -0.2F};
	static final float[] SWEEP_HIP = {-1.6F, -0.2F, 0.4F}, SWEEP_SHOULDER = {-1.9F, 0.1F, 0.0F};
	/**
	 * The special cuts ({@link Armed#CUT}; Kingdoms' blade forms). The Wheel Cut: the blade circles overhead through the
	 * wind-up ({@link #WHEEL_CIRCLE_TICKS} a turn, {@link #WHEEL_RADIUS} wide) with the body wound back, then comes
	 * across in one blow over the last {@link #WHEEL_SWEEP_TICKS}. The Figure Eight: the blade traces an eight before the
	 * body, once every {@link #EIGHT_TICKS}. The hurled blade: the arm goes back, then snaps forward over the last
	 * {@link #HURL_SNAP_TICKS}.
	 */
	static final float WHEEL_CIRCLE_TICKS = 12.5F, WHEEL_RADIUS = 0.35F, WHEEL_WIND = 0.55F;
	static final int WHEEL_SWEEP_TICKS = 8;
	static final float[] WHEEL_HIGH = {-2.75F, 0.0F, 0.1F}, WHEEL_FROM = {-1.9F, -0.9F, 0.5F}, WHEEL_TO = {-1.0F, 1.0F, 0.0F};
	static final float EIGHT_TICKS = 14.0F, EIGHT_RISE = 0.8F, EIGHT_ACROSS = 0.9F, EIGHT_TURN = 0.25F;
	static final float[] EIGHT_MID = {-1.25F, 0.1F, 0.15F};
	static final int HURL_SNAP_TICKS = 6;
	static final float[] HURL_BACK = {0.7F, -0.3F, 0.5F}, HURL_OUT = {-1.7F, 0.25F, 0.0F};
	static final float HURL_TURN = 0.5F;
	/**
	 * A blade in each hand: the other arm carries its blade as the weapon arm does, a little lower and wider
	 * ({@link #DUAL_LOWER}, {@link #DUAL_WIDER}), the feet wider apart ({@link #DUAL_SPREAD}) and the shoulders squarer
	 * ({@link #DUAL_YAW} of the stance's turn). Both blades together: the arms open wide, then cut across each other.
	 * The Mill Charge: both hands on the shaft before the body, turning it over and over ({@link #MILL_TICKS} a turn).
	 */
	static final float DUAL_LOWER = 0.15F, DUAL_WIDER = 0.1F, DUAL_SPREAD = 0.07F, DUAL_YAW = 0.35F;
	static final float[] BOTH_OPEN = {-1.3F, -0.7F, 0.5F}, BOTH_CROSS = {-1.1F, 0.9F, 0.0F};
	static final float MILL_TICKS = 6.0F, MILL_SWING = 0.9F;
	static final float[] MILL_MID = {-1.3F, 0.45F, 0.0F};
	// ================================================================================================================

	/**
	 * What's on a render state: how the weapon is carried, which arm has it, and how far through the draw (0-1; -1:
	 * drawn); and the special cut being made ({@code cut}: -1 none), how long it has run and how long it lasts (ticks).
	 */
	public record Held(Armed.Kind kind, boolean right, float draw, int cut, float cutAt, int cutTicks, boolean dual) {
	}

	public static final RenderStateDataKey<Held> KEY = RenderStateDataKey.create(() -> "jaymospowers:armed");
	private static final PoseBlend.Channel BLEND = PoseBlend.channel("armed");
	/** The draw's ring of steel (its own event, so a recording of ours can replace the stand-in in sounds.json). */
	private static final SoundEvent DRAW_SOUND = SoundEvent.createVariableRangeEvent(JaymosPowers.id("armed.draw"));

	/** What each player drawn was last seen holding, and when they last drew (game time). */
	private record Seen(Item item, long drawn) {
	}

	private static final Int2ObjectOpenHashMap<Seen> SEEN = new Int2ObjectOpenHashMap<>();

	private ArmedPose() {
	}

	/** From the avatar renderer's extractRenderState. */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		ItemStack main = entity.getMainHandItem();
		Armed.Kind kind = Armed.kind(main);
		if (kind == null && entity instanceof net.minecraft.world.entity.player.Player zf && com.jaymospowers.power.zfighter.ZFighterKiWeapon.wielded(zf)
			&& !com.jaymospowers.power.zfighter.ZFighterGuard.guarding(zf)) {
			// a Z Fighter's ki weapon is a weapon in the hand: the blade one-handed, the scythe and the lance as polearms
			kind = com.jaymospowers.power.zfighter.ZFighterKiWeapon.out(zf) == com.jaymospowers.power.zfighter.ZFighterKiWeapon.BLADE
				? Armed.Kind.ONE_HAND : Armed.Kind.POLE;
		}
		long now = entity.level().getGameTime();
		Seen seen = SEEN.get(entity.getId());
		long drawn = seen == null ? Long.MIN_VALUE : seen.drawn();
		if (seen == null || seen.item() != main.getItem()) {
			if (seen != null && kind != null && entity.isAlive()) {
				// a weapon just came into the hand
				drawn = now;
				entity.level().playLocalSound(entity.getX(), entity.getY(), entity.getZ(), DRAW_SOUND, SoundSource.PLAYERS, 0.35F,
					(kind == Armed.Kind.ONE_HAND ? 1.25F : 1.0F) + (entity.getRandom().nextFloat() - 0.5F) * 0.1F, false);
			}
			if (SEEN.size() > 512) {
				SEEN.clear();
			}
			SEEN.put(entity.getId(), new Seen(main.getItem(), drawn));
		}
		Armed.Cut special = kind == null || !entity.isAlive() ? null : entity.getAttached(Armed.CUT);
		if (special != null && (now - special.start() >= special.ticks() || now < special.start())) {
			special = null;
		}
		if (special != null) {
			// a special cut is the whole arm's business: it is drawn over a swing of the hand, and no draw plays under it
			SEEN.put(entity.getId(), new Seen(main.getItem(), Long.MIN_VALUE));
			state.setData(KEY, new Held(kind, entity.getMainArm() == HumanoidArm.RIGHT, -1.0F, special.kind(), now - special.start() + partialTicks,
				special.ticks(), Armed.dual(entity)));
			return;
		}
		boolean swinging = state.currentSwing != null && state.swingAnimation > 0.0F;
		if (kind == null || swinging || entity.isUsingItem() || entity.isSleeping() || !entity.isAlive() || CombatAnimCompat.attack(entity) != null) {
			if (swinging || entity.isUsingItem()) {
				// a blow or a guard cuts the draw short
				SEEN.put(entity.getId(), new Seen(main.getItem(), Long.MIN_VALUE));
			}
			state.setData(KEY, null);
			return;
		}
		float draw = drawn == Long.MIN_VALUE ? -1.0F : (now - drawn + partialTicks) / DRAW_TICKS;
		state.setData(KEY, new Held(kind, entity.getMainArm() == HumanoidArm.RIGHT, draw < 0.0F || draw >= 1.0F ? -1.0F : draw, -1, 0.0F, 0, Armed.dual(entity)));
	}

	/** Whether the stance is on this state (for {@link PoseTakeover}: Entity Model Features pauses so it holds). */
	public static boolean on(AvatarRenderState state) {
		return state.getData(KEY) != null && !blocked(state);
	}

	private static boolean blocked(AvatarRenderState state) {
		return state.deathTime > 0.0F || state.isFallFlying || state.swimAmount > 0.0F || state.isVisuallySwimming || state.isPassenger
			|| CombatAnimCompat.firstPersonPass(state) || PoseTakeover.posedOtherwise(state)
			|| state.getData(com.jaymospowers.client.wounds.WoundNursePose.KEY) != null || state.getData(ParryPose.KEY) != null;
	}

	/** Right after a player's model is animated (for the layers, and as each model is drawn). */
	public static void applyModel(Model<?> m, Object s) {
		if (!(m instanceof HumanoidModel<?> h) || !(s instanceof AvatarRenderState state)) {
			return;
		}
		Held held = state.getData(KEY);
		boolean on = held != null && !blocked(state);
		PoseBlend.Track track = BLEND.track(state.id, on, 0.0F, SPEED);
		if (track == null) {
			return;
		}
		if (!on) {
			track.apply(h, null);
			return;
		}
		PoseBlend.Frame f = new PoseBlend.Frame(h);
		boolean right = held.right();
		float walk = Mth.clamp(state.walkAnimationSpeed / WALK_FULL, 0.0F, 1.0F);
		float run = Mth.clamp((state.walkAnimationSpeed - RUN_FROM) / (RUN_FULL - RUN_FROM), 0.0F, 1.0F);
		float still = 1.0F - walk;
		float crouch = state.isCrouching ? 1.0F : 0.0F;
		float breath = Mth.sin(state.ageInTicks * Mth.TWO_PI / BREATH_TICKS) * still;
		// what's left of the walk's own swing on the weapon arm (it goes with the opposite leg)
		float sway = Mth.cos(state.walkAnimationPos * 0.6662F + (right ? Mth.PI : 0.0F)) * state.walkAnimationSpeed / Math.max(state.speedValue, 0.001F) * ARM_SWAY;
		float[] main = new float[3], other = new float[3];
		boolean bothHands;
		switch (held.kind()) {
			case TWO_HAND -> {
				bothHands = true;
				mix(main, other, TWO_STILL, TWO_RUN, run);
				mix(main, other, null, TWO_CROUCH, crouch);
			}
			case POLE -> {
				// upright at the side standing; across the body, in both hands, on the move or crouched
				float across = Math.max(walk, crouch);
				bothHands = across > 0.5F;
				main[0] = Mth.lerp(across, POLE_STILL[0], POLE_MOVE[0]);
				main[1] = Mth.lerp(across, POLE_STILL[1], POLE_MOVE[1]);
				main[2] = Mth.lerp(across, POLE_STILL[2], POLE_MOVE[2]);
				other[0] = POLE_MOVE[3];
				other[1] = POLE_MOVE[4];
				other[2] = POLE_MOVE[5];
				mix(main, other, null, POLE_CROUCH, crouch);
			}
			default -> {
				// the other arm keeps its own walk on the move: only standing or crouched is it held back
				bothHands = false;
				main[0] = Mth.lerp(run, Mth.lerp(walk, ONE_STILL[0], ONE_WALK[0]), ONE_RUN[0]) + sway;
				main[1] = Mth.lerp(run, Mth.lerp(walk, ONE_STILL[1], ONE_WALK[1]), ONE_RUN[1]);
				main[2] = Mth.lerp(run, Mth.lerp(walk, ONE_STILL[2], ONE_WALK[2]), ONE_RUN[2]);
				main[0] = Mth.lerp(crouch, main[0], ONE_CROUCH[0]);
				main[1] = Mth.lerp(crouch, main[1], ONE_CROUCH[1]);
				main[2] = Mth.lerp(crouch, main[2], ONE_CROUCH[2]);
				float back = Math.max(still, crouch);
				other[0] = Mth.lerp(crouch, ONE_STILL[3], ONE_CROUCH[3]) * back;
				other[1] = 0.0F;
				other[2] = Mth.lerp(crouch, ONE_STILL[5], ONE_CROUCH[5]) * back;
			}
		}
		main[0] += breath * BREATH_ARM;
		float yaw = STANCE_YAW * Math.max(still, crouch) + CROUCH_YAW * crouch;
		if (held.draw() >= 0.0F) {
			// the draw: reach for it, sweep it out and round, settle into the stance
			boolean hip = held.kind() == Armed.Kind.ONE_HAND;
			float[] reach = hip ? DRAW_HIP : DRAW_SHOULDER, sweep = hip ? SWEEP_HIP : SWEEP_SHOULDER;
			float t = held.draw();
			float[] from, to;
			float k;
			if (t < DRAW_REACH) {
				from = reach;
				to = reach;
				k = 0.0F;
			} else if (t < 0.65F) {
				from = reach;
				to = sweep;
				k = smooth((t - DRAW_REACH) / (0.65F - DRAW_REACH));
			} else {
				from = sweep;
				to = main.clone();
				k = smooth((t - 0.65F) / 0.35F);
			}
			for (int i = 0; i < 3; i++) {
				main[i] = Mth.lerp(k, from[i], to[i]);
			}
			// the shoulders open as the blade comes out
			yaw *= smooth(t);
			bothHands = bothHands && t > 0.65F;
		}
		boolean dual = held.dual() && held.kind() == Armed.Kind.ONE_HAND;
		if (dual) {
			// a blade in each hand: the other arm carries its own as the weapon arm does, and the shoulders square up
			bothHands = true;
			other[0] = main[0] + DUAL_LOWER;
			other[1] = main[1];
			other[2] = main[2] + DUAL_WIDER;
			yaw *= DUAL_YAW;
		}
		if (held.cut() >= 0) {
			// a special cut: the weapon arm alone, the body turning with it
			float t = held.cutAt();
			bothHands = false;
			other[0] = 0.2F;
			other[2] = 0.3F;
			switch (held.cut()) {
				case Armed.CUT_WHEEL -> {
					float windUp = held.cutTicks() - WHEEL_SWEEP_TICKS;
					if (t < windUp) {
						float a = t * Mth.TWO_PI / WHEEL_CIRCLE_TICKS;
						float rise = smooth(t / 5.0F);
						main[0] = Mth.lerp(rise, main[0], WHEEL_HIGH[0] + Mth.cos(a) * WHEEL_RADIUS);
						main[1] = Mth.lerp(rise, main[1], WHEEL_HIGH[1] + Mth.sin(a) * WHEEL_RADIUS);
						main[2] = Mth.lerp(rise, main[2], WHEEL_HIGH[2]);
						yaw = -WHEEL_WIND * smooth(t / windUp);
					} else {
						float k = smooth((t - windUp) / (WHEEL_SWEEP_TICKS * 0.6F));
						for (int i = 0; i < 3; i++) {
							main[i] = Mth.lerp(k, WHEEL_FROM[i], WHEEL_TO[i]);
						}
						yaw = Mth.lerp(k, -WHEEL_WIND, WHEEL_WIND);
					}
				}
				case Armed.CUT_EIGHT -> {
					float a = t * Mth.TWO_PI / EIGHT_TICKS;
					main[0] = EIGHT_MID[0] + Mth.sin(2.0F * a) * EIGHT_RISE;
					main[1] = EIGHT_MID[1] + Mth.sin(a) * EIGHT_ACROSS;
					main[2] = EIGHT_MID[2];
					yaw = Mth.sin(a) * EIGHT_TURN;
				}
				case Armed.CUT_BOTH -> {
					// both blades: open wide, then across each other
					float k = smooth(t / (held.cutTicks() * 0.6F));
					for (int i = 0; i < 3; i++) {
						main[i] = Mth.lerp(k, BOTH_OPEN[i], BOTH_CROSS[i]);
					}
					bothHands = true;
					other[0] = main[0];
					other[1] = main[1];
					other[2] = main[2];
					yaw = 0.0F;
				}
				case Armed.CUT_MILL -> {
					// both hands on the shaft, one over the other, turning it
					float a = t * Mth.TWO_PI / MILL_TICKS;
					main[0] = MILL_MID[0] + Mth.sin(a) * MILL_SWING;
					main[1] = MILL_MID[1];
					main[2] = MILL_MID[2];
					bothHands = true;
					other[0] = MILL_MID[0] - Mth.sin(a) * MILL_SWING;
					other[1] = MILL_MID[1];
					other[2] = MILL_MID[2];
					yaw = 0.0F;
				}
				default -> {
					float back = held.cutTicks() - HURL_SNAP_TICKS;
					float k = t < back ? 0.0F : smooth((t - back) / (HURL_SNAP_TICKS * 0.5F));
					float reach = smooth(t / Math.max(1.0F, back));
					for (int i = 0; i < 3; i++) {
						main[i] = Mth.lerp(k, Mth.lerp(reach, main[i], HURL_BACK[i]), HURL_OUT[i]);
					}
					yaw = Mth.lerp(k, -HURL_TURN * reach, HURL_TURN);
				}
			}
		}
		float side = right ? 1.0F : -1.0F;
		int mainArm = right ? PoseBlend.RIGHT_ARM : PoseBlend.LEFT_ARM, otherArm = right ? PoseBlend.LEFT_ARM : PoseBlend.RIGHT_ARM;
		f.set(mainArm, main[0], -side * main[1], side * main[2]);
		if (bothHands) {
			f.set(otherArm, other[0], side * other[1], -side * other[2]);
		} else if (held.kind() == Armed.Kind.ONE_HAND) {
			f.add(otherArm, other[0]);
			f.add(otherArm + 2, -side * other[2]);
		}
		// the weapon shoulder forward, the head still looking where it looks
		f.set(PoseBlend.HIP_YAW, -side * yaw);
		f.add(PoseBlend.HEAD + 1, side * yaw);
		f.set(PoseBlend.HIP_ROLL, breath * BREATH_ROLL);
		f.set(PoseBlend.HIP_PITCH, RUN_LEAN * run * (1.0F - crouch));
		f.add(PoseBlend.HEAD, -RUN_LEAN * run * (1.0F - crouch));
		float set = held.cut() >= 0 ? 1.0F - crouch : still * (1.0F - crouch);
		f.set(PoseBlend.DROP, STANCE_DROP * set);
		f.add(right ? PoseBlend.RIGHT_LEG : PoseBlend.LEFT_LEG, -STANCE_STRIDE * set);
		f.add(right ? PoseBlend.LEFT_LEG : PoseBlend.RIGHT_LEG, STANCE_STRIDE * set);
		float spread = STANCE_SPREAD + (dual ? DUAL_SPREAD : 0.0F);
		f.add(PoseBlend.RIGHT_LEG + 2, spread * set);
		f.add(PoseBlend.LEFT_LEG + 2, -spread * set);
		track.apply(h, f);
	}

	/** {@code main} and {@code other} moved from {@code a} (null: as they stand) toward {@code b} by {@code t}: six angles, weapon arm then other arm. */
	private static void mix(float[] main, float[] other, float @Nullable [] a, float[] b, float t) {
		for (int i = 0; i < 3; i++) {
			main[i] = Mth.lerp(t, a == null ? main[i] : a[i], b[i]);
			other[i] = Mth.lerp(t, a == null ? other[i] : a[i + 3], b[i + 3]);
		}
	}

	private static float smooth(float t) {
		t = Mth.clamp(t, 0.0F, 1.0F);
		return t * t * (3.0F - 2.0F * t);
	}
}
