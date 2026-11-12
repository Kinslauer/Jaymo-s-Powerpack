package com.jaymospowers.client.boxing;

import com.jaymospowers.boxing.Boxing;
import com.jaymospowers.boxing.BoxingEvades;
import com.jaymospowers.boxing.BoxingGear;
import com.jaymospowers.client.PoseTakeover;
import com.jaymospowers.client.power.wind.CombatAnimCompat;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (Jung) How a boxer stands and moves ({@link Boxing}): third person, for everyone who sees them. A style's stance
 * replaces the Fang Gauntlets' plain fists-up guard for anyone with fist gear in hand and a style
 * ({@code armory/FangGauntletsClient} hands over to {@link #apply}).
 * <ul>
 * <li><b>The stances move</b>, and each gives its style away: the Arrow Slit crouches behind both gloves and sways
 * from side to side; the Long Guard holds its lead arm out and paws the air with it; the Bastion stands southpaw
 * (right side leading) and side on, the lead forearm across the belly, and shrugs its lead shoulder now and then.</li>
 * <li><b>Blocks</b> (right click): the Arrow Slit's gloves close in tighter; the Long Guard puts both arms out ahead,
 * hands together; the Bastion turns further side on behind a higher shoulder.</li>
 * <li><b>Punches</b>: the arm that throws it goes out by the punch (a jab, a cross or straight, a hook from the side,
 * an uppercut from below), the other stays home.</li>
 * <li><b>Evades</b> ({@link BoxingEvades#MOVED}): a lean to the side for a slip, a duck for a bob, a lean back for a
 * pull, the lead shoulder turned in for a shoulder roll.</li>
 * <li><b>Gassed</b> ({@link Boxing#GASSED}): the hands drop and the shoulders heave.</li>
 * </ul>
 * The three styles' shapes are copied from fight footage Jung sent ("Every Boxing Guard Explained"): the Arrow Slit is
 * the peekaboo (both gloves on the cheeks, elbows in, a forward crouch with the chin down, the head never still), the
 * Long Guard has its lead arm straight out at shoulder height with the rear glove by the chin and the body upright, and
 * the Bastion is the Philly shell (side on, the lead shoulder up by the chin, the lead arm low across the belly, the
 * rear glove by the cheek, the weight back; its roll turns the shoulder in and sways the head away).
 * <p>
 * <b>Elbows.</b> The arms bend ({@link com.jaymospowers.client.KneeBend}, as the crawl's and the climb's do): each pose
 * gives an arm its upper arm's pitch and turn, and a fold of the forearm at the elbow, either forward and up (a glove
 * raised to the cheek) or across the body (the Bastion's lead forearm over the belly, a hook). {@link #apply} leaves
 * the folds on the render state ({@link #BENDS}); {@link #bendLimbs} folds the arms as each model is drawn, and
 * {@code FangGauntletsClient} turns the worn fist gear with the forearm.
 * Set by numbers, without having been seen on a model: expect to tune the knobs below.
 */
public final class BoxingPoses {
	// ================================================ tuning knobs =================================================
	// An arm's pose: its upper arm's pitch (radians; 0 hanging, -1.57 straight ahead, further is higher), how far it is
	// turned in across the body (radians; negative is out to the side), and its elbow's fold (radians): forward and up
	// (0 straight, -1.57 a right angle, -2.2 the glove back by the shoulder) or, where it says "across", over the body.
	/** The elbow: this far down the arm from the shoulder (pixels; the arm reaches 10, so the forearm is as long as the upper arm). */
	public static final float ELBOW = 5.0F;
	/** The Arrow Slit's guard (both arms: elbows in, forearms up, gloves on the cheeks), its sway (radians, and radians a tick), and its block. */
	static final float SLIT_PITCH = -1.5F, SLIT_IN = 0.4F, SLIT_FOLD = -1.7F, SLIT_SWAY = 0.12F, SLIT_SWAY_RATE = 0.14F;
	static final float SLIT_BLOCK_PITCH = -1.6F, SLIT_BLOCK_IN = 0.55F, SLIT_BLOCK_FOLD = -1.65F;
	/** The Arrow Slit's forward crouch (radians), standing and behind its block; the head stays up. */
	static final float SLIT_CROUCH = 0.25F, SLIT_BLOCK_CROUCH = 0.3F;
	/** Its rounded back: the upper half of the spine curled this much further forward than the lower (radians). */
	static final float SLIT_HUNCH = 0.2F;
	// A base is three numbers: the feet apart (each leg out this far, radians), the lead foot ahead and the rear one behind
	// (radians at the hip), and the knees bent (each thigh this far forward, the shin folded back twice that).
	/** The Arrow Slit's base: wide and low, and lower still behind its block. */
	static final float SLIT_SPREAD = 0.2F, SLIT_STAGGER = 0.2F, SLIT_KNEES = 0.5F, SLIT_BLOCK_KNEES = 0.6F;
	/** The Long Guard's lead arm out ahead, all but straight, and its pawing (up and down, side to side; radians and radians a tick). */
	static final float LONG_LEAD_PITCH = -1.6F, LONG_LEAD_IN = 0.05F, LONG_LEAD_FOLD = -0.12F, LONG_PAW = 0.06F, LONG_PAW_RATE = 0.3F, LONG_PAW_SIDE = 0.06F, LONG_PAW_SIDE_RATE = 0.17F;
	/** Its rear glove by the chin, and its block: both arms out ahead, a little bent, the hands together. */
	static final float LONG_REAR_PITCH = -1.1F, LONG_REAR_IN = 0.5F, LONG_REAR_FOLD = -2.0F, LONG_BLOCK_PITCH = -1.35F, LONG_BLOCK_IN = 0.35F, LONG_BLOCK_FOLD = -0.35F;
	/** The Long Guard's lead shoulder turned forward, its slight lean in behind the lead arm and its tucked chin (radians). */
	static final float LONG_BLADE = 0.6F, LONG_LEAN = 0.08F, LONG_CHIN = 0.1F;
	/** Its base: long and balanced, the knees a little bent. A bladed stance's feet turn with the body by this share of its turn. */
	static final float LONG_SPREAD = 0.15F, LONG_STAGGER = 0.3F, LONG_KNEES = 0.3F, FEET_TURN = 0.8F;
	/** The Bastion's lead arm: the upper arm down and a little forward, the forearm folded across the belly. Its rear glove by the cheek. */
	static final float BASTION_LEAD_PITCH = -0.3F, BASTION_LEAD_IN = 0.0F, BASTION_LEAD_ACROSS = 1.35F, BASTION_REAR_PITCH = -1.1F, BASTION_REAR_IN = 0.55F, BASTION_REAR_FOLD = -2.0F;
	/** Its base: feet under the shoulders, the lead one ahead, the knees soft. */
	static final float BASTION_SPREAD = 0.22F, BASTION_STAGGER = 0.2F, BASTION_KNEES = 0.34F;
	/** The Bastion side on (its lead shoulder turned forward, radians), that shoulder held up (pixels), the weight back, the head tipped to the rear shoulder and the chin tucked. */
	static final float BASTION_BLADE = 0.45F, BASTION_SHOULDER = 1.0F, BASTION_LEAN = -0.04F, BASTION_HEAD_TILT = 0.12F, BASTION_CHIN = 0.1F;
	/** How much further side on its block stands. */
	static final float BASTION_BLOCK_BLADE = 0.65F;
	/** The shrug: every this many ticks, this long, this high (pixels). */
	static final float SHRUG_EVERY = 90.0F, SHRUG_TICKS = 12.0F, SHRUG_UP = 1.0F;
	/** The Bastion's block: the lead shoulder higher, the forearm a little higher over the body, the rear glove tight to the jaw. */
	static final float BASTION_BLOCK_LEAD_PITCH = -0.55F, BASTION_BLOCK_UP = 1.6F, BASTION_BLOCK_ACROSS = 1.45F, BASTION_BLOCK_REAR_PITCH = -1.2F, BASTION_BLOCK_REAR_IN = 0.7F, BASTION_BLOCK_REAR_FOLD = -2.2F;
	/** A straight punch at full reach (the elbow straightens as it goes out). */
	static final float STRAIGHT_PITCH = -1.57F;
	/** A hook: the upper arm level, swept from out to in, the forearm folded across at the elbow. */
	static final float HOOK_PITCH = -1.45F, HOOK_FROM = -0.7F, HOOK_TO = 0.5F, HOOK_ACROSS = 1.3F;
	/** An uppercut: the upper arm from by the hip up to ahead, the forearm up, the fist rising under the chin. */
	static final float UPPERCUT_FROM = 0.15F, UPPERCUT_TO = -1.2F, UPPERCUT_IN = 0.3F, UPPERCUT_FOLD = -1.7F;
	/** The body's turn into a rear-hand punch (radians). */
	static final float PUNCH_TURN = 0.3F;
	/** An evade's pose lasts this long (ticks): a slip's lean to the side, a bob's duck, a pull's lean back, a shoulder roll's turn and lift. */
	static final float MOVE_TICKS = 8.0F, SLIP_LEAN = 0.3F, BOB_DUCK = 0.5F, PULL_LEAN = -0.3F, ROLL_TURN = 0.6F, ROLL_UP = 1.0F;
	/** A shoulder roll's sway back and its tip of the head away from the blow (radians). */
	static final float ROLL_LEAN = -0.25F, ROLL_HEAD = 0.15F;
	/** A model's shoulders are this far from its middle, and its hips this far below its neck (pixels). */
	static final float SHOULDER_OUT = 5.0F, TORSO = 12.0F;
	/** A leg's thigh, and its shin (pixels). */
	static final float THIGH = 6.0F, SHIN = 6.0F;
	/** Gassed: the hands dropped, the shoulders' heave (pixels, radians a tick), the slump. */
	static final float GASSED_PITCH = -0.3F, GASSED_IN = 0.15F, GASSED_FOLD = -0.5F, HEAVE = 0.6F, HEAVE_RATE = 0.3F, SLUMP = 0.18F;
	// ================================================================================================================

	/** A boxer, as their render state carries them. {@code moveAge}: ticks since the evade began. */
	public record Look(Boxing.Style style, int spent, Boxing.@Nullable Punch punch, BoxingEvades.@Nullable Move move, float moveAge, boolean blocking) {
	}

	public static final RenderStateDataKey<Look> KEY = RenderStateDataKey.create(() -> "jaymospowers:boxing_look");

	/**
	 * The elbows as {@link #apply} last posed them: each arm's fold (radians, as {@code KneeBend} takes it), and whether
	 * it folds across the body rather than forward; both knees' fold; and the spine's, halfway down the back.
	 */
	public record Bends(float right, boolean rightAcross, float left, boolean leftAcross, float knees, float spine) {
	}

	public static final RenderStateDataKey<Bends> BENDS = RenderStateDataKey.create(() -> "jaymospowers:boxing_bends");

	/** One arm's elbow while a pose is worked out: its fold, and whether that is across the body (then positive is in toward it). */
	private static final class Elbow {
		float fold;
		boolean across;
		/** Both knees' fold (kept on the lead arm's elbow only, for want of a better place). */
		float knees;
		/** The spine's fold (kept here likewise). */
		float spine;

		void forward(float fold) {
			this.fold = fold;
			this.across = false;
		}

		void across(float fold) {
			this.fold = fold;
			this.across = true;
		}
	}

	private BoxingPoses() {
	}

	/** From the avatar renderer's extractRenderState (through FangGauntletsClient): the boxer, if {@code entity} is one. */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		// the elbows are set again by apply, if it poses this one
		state.setData(BENDS, null);
		if (!(entity instanceof Player player)) {
			state.setData(KEY, null);
			return;
		}
		// not Boxing.boxing: a player's levels are told only to that player, but the style everyone sees is only ever
		// set to a learned one (Boxing.use), so the style and fist gear in hand are enough here
		Boxing.Style style = Boxing.style(player);
		if (style == null || player.isSpectator() || !BoxingGear.isGear(player.getMainHandItem())) {
			state.setData(KEY, null);
			return;
		}
		BoxingEvades.Move move = null;
		float age = 0.0F;
		Long moved = player.getAttached(BoxingEvades.MOVED);
		if (moved != null) {
			int index = (int)(moved & 7);
			age = player.level().getGameTime() - (moved >> 3) + partialTicks;
			if (index >= 1 && index <= BoxingEvades.Move.values().length && age >= 0.0F && age < MOVE_TICKS) {
				move = BoxingEvades.Move.values()[index - 1];
			}
		}
		state.setData(KEY, new Look(style, Boxing.spent(player), Boxing.shownPunch(player), move, age, player.isBlocking()));
	}

	/**
	 * Right after a player's model is animated: a boxer's arms, body and head for where they are in the fight. False
	 * if this isn't a boxer (or something else has the body), and the caller goes on as before.
	 */
	public static boolean apply(HumanoidModel<?> model, AvatarRenderState state) {
		Look look = state.getData(KEY);
		if (look == null) {
			return false;
		}
		if (state.isFallFlying || state.swimAmount > 0.0F || state.isVisuallySwimming || state.isPassenger || CombatAnimCompat.firstPersonPass(state)
			|| PoseTakeover.posedOtherwise(state) || state.getData(com.jaymospowers.client.wounds.WoundNursePose.KEY) != null) {
			return true;
		}
		// the Bastion stands southpaw: its lead is the right arm; the others lead with the left
		boolean leadRight = look.style() == Boxing.Style.BASTION;
		ModelPart lead = leadRight ? model.rightArm : model.leftArm, rear = leadRight ? model.leftArm : model.rightArm;
		float time = state.ageInTicks;
		boolean swinging = state.currentSwing != null && state.swingAnimation > 0.0F;
		// how far the lead shoulder is turned forward (radians), added up from the stance, the punch and the evade
		float turn = 0.0F;
		Elbow leadElbow = new Elbow(), rearElbow = new Elbow();
		if (look.spent() == Boxing.GASSED && !swinging) {
			// spent: the hands drop and the shoulders heave
			float heave = Mth.sin(time * HEAVE_RATE) * HEAVE;
			set(lead, leadRight, GASSED_PITCH, GASSED_IN);
			set(rear, !leadRight, GASSED_PITCH, GASSED_IN);
			leadElbow.forward(GASSED_FOLD);
			rearElbow.forward(GASSED_FOLD);
			lead.y += heave;
			rear.y += heave;
			bend(model, SLUMP);
			model.head.xRot += SLUMP;
			keep(state, leadRight, leadElbow, rearElbow);
			return true;
		}
		if (look.blocking()) {
			switch (look.style()) {
				case ARROW_SLIT -> {
					set(lead, leadRight, SLIT_BLOCK_PITCH, SLIT_BLOCK_IN);
					set(rear, !leadRight, SLIT_BLOCK_PITCH, SLIT_BLOCK_IN);
					leadElbow.forward(SLIT_BLOCK_FOLD);
					rearElbow.forward(SLIT_BLOCK_FOLD);
					leadElbow.spine = hunch(model, SLIT_BLOCK_CROUCH, SLIT_HUNCH);
					leadElbow.knees = base(model, leadRight, SLIT_SPREAD, SLIT_STAGGER, SLIT_BLOCK_KNEES, 0.0F);
				}
				case LONG_GUARD -> {
					set(lead, leadRight, LONG_BLOCK_PITCH, LONG_BLOCK_IN);
					set(rear, !leadRight, LONG_BLOCK_PITCH, LONG_BLOCK_IN);
					leadElbow.forward(LONG_BLOCK_FOLD);
					rearElbow.forward(LONG_BLOCK_FOLD);
					bend(model, LONG_LEAN);
					model.head.xRot += LONG_CHIN;
					turn += LONG_BLADE;
					leadElbow.knees = base(model, leadRight, LONG_SPREAD, LONG_STAGGER, LONG_KNEES, LONG_BLADE);
				}
				case BASTION -> {
					set(lead, leadRight, BASTION_BLOCK_LEAD_PITCH, BASTION_LEAD_IN);
					leadElbow.across(BASTION_BLOCK_ACROSS);
					lead.y -= BASTION_BLOCK_UP;
					set(rear, !leadRight, BASTION_BLOCK_REAR_PITCH, BASTION_BLOCK_REAR_IN);
					rearElbow.forward(BASTION_BLOCK_REAR_FOLD);
					turn += BASTION_BLOCK_BLADE;
					bend(model, BASTION_LEAN);
					shellHead(model, leadRight);
					leadElbow.knees = base(model, leadRight, BASTION_SPREAD, BASTION_STAGGER, BASTION_KNEES, BASTION_BLADE);
				}
			}
		} else {
			turn += stance(model, look.style(), lead, rear, leadElbow, rearElbow, leadRight, time);
		}
		if (swinging && !look.blocking()) {
			turn += punch(look.punch() == null ? Boxing.Punch.JAB : look.punch(), lead, rear, leadElbow, rearElbow, leadRight,
				Mth.clamp(state.swingAnimation, 0.0F, 1.0F));
		}
		if (look.move() != null) {
			// out and back over the evade's few ticks
			float amount = Mth.sin(Mth.clamp(look.moveAge() / MOVE_TICKS, 0.0F, 1.0F) * Mth.PI);
			switch (look.move()) {
				case SLIP_LEFT -> lean(model, SLIP_LEAN * amount);
				case SLIP_RIGHT -> lean(model, -SLIP_LEAN * amount);
				case BOB -> {
					bend(model, BOB_DUCK * amount);
					model.head.xRot += BOB_DUCK * amount * 0.5F;
				}
				case PULL -> bend(model, PULL_LEAN * amount);
				case ROLL -> {
					// the lead shoulder turns in to take the blow, the weight goes back and the head tips away from it
					turn += ROLL_TURN * amount;
					lead.y -= ROLL_UP * amount;
					bend(model, ROLL_LEAN * amount);
					model.head.zRot += (leadRight ? ROLL_HEAD : -ROLL_HEAD) * amount;
				}
			}
		}
		turn(model, leadRight ? -turn : turn);
		keep(state, leadRight, leadElbow, rearElbow);
		return true;
	}

	/** Leaves the elbows on the state for {@link #bendLimbs} and the worn gear. An arm's own across is toward the body: +X for the right arm, which is a negative fold. */
	private static void keep(AvatarRenderState state, boolean leadRight, Elbow lead, Elbow rear) {
		Elbow right = leadRight ? lead : rear, left = leadRight ? rear : lead;
		state.setData(BENDS, new Bends(right.across ? -right.fold : right.fold, right.across, left.fold, left.across, lead.knees, lead.spine));
	}

	/** Last thing before a model is drawn: a boxer's elbows ({@link #BENDS}), put back straight once it is drawn ({@code KneeBend.restore}). */
	public static void bendLimbs(net.minecraft.client.model.Model<?> model, Object renderState) {
		if (!(renderState instanceof AvatarRenderState state) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Bends bends = state.getData(BENDS);
		if (bends == null) {
			return;
		}
		com.jaymospowers.client.KneeBend.bend(h.rightArm, bends.right(), ELBOW, bends.rightAcross());
		com.jaymospowers.client.KneeBend.bend(h.leftArm, bends.left(), ELBOW, bends.leftAcross());
		com.jaymospowers.client.KneeBend.bend(h.rightLeg, bends.knees());
		com.jaymospowers.client.KneeBend.bend(h.leftLeg, bends.knees());
		com.jaymospowers.client.KneeBend.bend(h.body, bends.spine(), TORSO * 0.5F, false);
	}

	/** The style's stance when nothing else is happening: where the hands sit, and the small movement that never stops. Returns how far the lead shoulder is turned forward. */
	private static float stance(HumanoidModel<?> model, Boxing.Style style, ModelPart lead, ModelPart rear, Elbow leadElbow, Elbow rearElbow, boolean leadRight,
		float time) {
		switch (style) {
			case ARROW_SLIT -> {
				float sway = Mth.sin(time * SLIT_SWAY_RATE) * SLIT_SWAY;
				set(lead, leadRight, SLIT_PITCH, SLIT_IN);
				set(rear, !leadRight, SLIT_PITCH, SLIT_IN);
				leadElbow.forward(SLIT_FOLD);
				rearElbow.forward(SLIT_FOLD);
				// the sway is in the shoulders and head; the hips and feet stay where they are
				swayShoulders(model, sway);
				lead.zRot += sway;
				rear.zRot += sway;
				leadElbow.spine = hunch(model, SLIT_CROUCH, SLIT_HUNCH);
				leadElbow.knees = base(model, leadRight, SLIT_SPREAD, SLIT_STAGGER, SLIT_KNEES, 0.0F);
				return 0.0F;
			}
			case LONG_GUARD -> {
				set(lead, leadRight, LONG_LEAD_PITCH + Mth.sin(time * LONG_PAW_RATE) * LONG_PAW, LONG_LEAD_IN + Mth.cos(time * LONG_PAW_SIDE_RATE) * LONG_PAW_SIDE);
				set(rear, !leadRight, LONG_REAR_PITCH, LONG_REAR_IN);
				leadElbow.forward(LONG_LEAD_FOLD);
				rearElbow.forward(LONG_REAR_FOLD);
				bend(model, LONG_LEAN);
				model.head.xRot += LONG_CHIN;
				leadElbow.knees = base(model, leadRight, LONG_SPREAD, LONG_STAGGER, LONG_KNEES, LONG_BLADE);
				return LONG_BLADE;
			}
			case BASTION -> {
				set(lead, leadRight, BASTION_LEAD_PITCH, BASTION_LEAD_IN);
				set(rear, !leadRight, BASTION_REAR_PITCH, BASTION_REAR_IN);
				leadElbow.across(BASTION_LEAD_ACROSS);
				rearElbow.forward(BASTION_REAR_FOLD);
				lead.y -= BASTION_SHOULDER;
				float phase = time % SHRUG_EVERY / SHRUG_TICKS;
				if (phase < 1.0F) {
					lead.y -= Mth.sin(phase * Mth.PI) * SHRUG_UP;
				}
				bend(model, BASTION_LEAN);
				shellHead(model, leadRight);
				leadElbow.knees = base(model, leadRight, BASTION_SPREAD, BASTION_STAGGER, BASTION_KNEES, BASTION_BLADE);
				return BASTION_BLADE;
			}
		}
		return 0.0F;
	}

	/** The Bastion's head: tipped toward the rear shoulder, off the line of a straight punch, the chin tucked behind the lead one. */
	private static void shellHead(HumanoidModel<?> model, boolean leadRight) {
		model.head.zRot += leadRight ? BASTION_HEAD_TILT : -BASTION_HEAD_TILT;
		model.head.xRot += BASTION_CHIN;
	}

	/**
	 * {@code swing}: 0 as the punch starts, 1 as it ends. The arm that throws it goes out and comes home, its elbow
	 * straightening for a straight punch and folding for a hook or an uppercut; the other keeps its place. Returns how
	 * far the punch turns the lead shoulder forward (negative: the rear one comes through).
	 */
	private static float punch(Boxing.Punch punch, ModelPart lead, ModelPart rear, Elbow leadElbow, Elbow rearElbow, boolean leadRight, float swing) {
		float out = Mth.sin(swing * Mth.PI);
		switch (punch) {
			case JAB -> {
				reach(lead, leadRight, STRAIGHT_PITCH, 0.0F, out);
				leadElbow.fold *= 1.0F - out;
			}
			case CROSS, STRAIGHT, PULL_COUNTER -> {
				reach(rear, !leadRight, STRAIGHT_PITCH, 0.0F, out);
				rearElbow.fold *= 1.0F - out;
				// the rear shoulder comes through
				return -PUNCH_TURN * out;
			}
			case LEAD_HOOK -> {
				set(lead, leadRight, HOOK_PITCH, Mth.lerp(swing, HOOK_FROM, HOOK_TO));
				leadElbow.across(HOOK_ACROSS);
			}
			case REAR_HOOK -> {
				set(rear, !leadRight, HOOK_PITCH, Mth.lerp(swing, HOOK_FROM, HOOK_TO));
				rearElbow.across(HOOK_ACROSS);
			}
			case UPPERCUT -> {
				set(rear, !leadRight, Mth.lerp(swing, UPPERCUT_FROM, UPPERCUT_TO), UPPERCUT_IN);
				rearElbow.forward(UPPERCUT_FOLD);
			}
		}
		return 0.0F;
	}

	/** Puts {@code arm} at {@code pitch}, turned {@code in} across the body. */
	private static void set(ModelPart arm, boolean right, float pitch, float in) {
		arm.xRot = pitch;
		arm.yRot = right ? -in : in;
		arm.zRot = 0.0F;
	}

	/** Moves {@code arm} {@code amount} (0 to 1) of the way from where it is to {@code pitch}, turned {@code in}. */
	private static void reach(ModelPart arm, boolean right, float pitch, float in, float amount) {
		arm.xRot = Mth.lerp(amount, arm.xRot, pitch);
		arm.yRot = Mth.lerp(amount, arm.yRot, right ? -in : in);
	}

	/**
	 * Turns the body {@code amount} radians about its upright (positive brings the left shoulder forward), and carries
	 * the shoulders round with it, the way the model's own swing does: the arms are not the body's children. Where the
	 * arms point is left alone, so a jab from a side-on stance still goes straight at the foe.
	 */
	private static void turn(HumanoidModel<?> model, float amount) {
		if (amount == 0.0F) {
			return;
		}
		model.body.yRot += amount;
		float all = model.body.yRot;
		model.rightArm.z = Mth.sin(all) * SHOULDER_OUT;
		model.rightArm.x = -Mth.cos(all) * SHOULDER_OUT;
		model.leftArm.z = -Mth.sin(all) * SHOULDER_OUT;
		model.leftArm.x = Mth.cos(all) * SHOULDER_OUT;
	}

	/** Leans the body {@code amount} radians forward from the neck (negative: back), and moves the legs to stay under the hips. */
	private static void bend(HumanoidModel<?> model, float amount) {
		model.body.xRot += amount;
		float back = Mth.sin(amount) * TORSO;
		model.rightLeg.z += back;
		model.leftLeg.z += back;
	}

	/**
	 * Sways the shoulders and head {@code amount} radians to the side (positive is the boxer's left) about the hips,
	 * which stay where they are: the body turns about its foot, and the head and arms ride its top.
	 */
	private static void swayShoulders(HumanoidModel<?> model, float amount) {
		float across = Mth.sin(amount) * TORSO, down = (1.0F - Mth.cos(amount)) * TORSO;
		model.body.zRot += amount;
		model.body.x += across;
		model.body.y += down;
		for (ModelPart part : new ModelPart[] {model.head, model.rightArm, model.leftArm}) {
			part.x += across;
			part.y += down;
		}
		model.head.zRot += amount * 0.6F;
	}

	/**
	 * A boxer's base: the feet {@code spread} apart, the lead foot {@code stagger} ahead and the rear one as far
	 * behind, both knees bent by {@code knees} (each thigh that far forward; the shin's fold back, twice that, is
	 * returned for {@link #bendLimbs}), and the feet turned with a body whose lead shoulder is {@code blade} forward.
	 * The step ahead and behind is along the way the boxer faces, whichever way the toes are turned, so a bladed
	 * stance's feet don't cross. Everything above the feet sinks by what the legs shorten, so the feet stay grounded.
	 */
	private static float base(HumanoidModel<?> model, boolean leadRight, float spread, float stagger, float knees, float blade) {
		float sink = (THIGH + SHIN) * (1.0F - Mth.cos(knees) * Mth.cos(stagger) * Mth.cos(spread));
		for (ModelPart part : new ModelPart[] {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg}) {
			part.y += sink;
		}
		float feet = (leadRight ? -blade : blade) * FEET_TURN;
		leg(model.rightLeg, spread, leadRight ? -stagger : stagger, feet, knees);
		leg(model.leftLeg, -spread, leadRight ? stagger : -stagger, feet, knees);
		return knees * 2.0F;
	}

	/**
	 * Points one leg: out to the side by {@code spread}, stepped by {@code step} (negative is ahead) the way the boxer
	 * faces, the toes turned by {@code toes}, and the thigh {@code knee} forward the way the toes point (on top of
	 * the walk's own swing). A part turns about Z, then Y, then X, so the three angles are worked out from the whole turn.
	 */
	private static void leg(ModelPart leg, float spread, float step, float toes, float knee) {
		float[] m = times(times(times(turnZ(spread), turnX(step)), turnY(toes)), turnX(leg.xRot - knee));
		leg.yRot = -(float) Math.asin(Mth.clamp(m[6], -1.0F, 1.0F));
		leg.xRot = (float) Math.atan2(m[7], m[8]);
		leg.zRot = (float) Math.atan2(m[3], m[0]);
	}

	// 3 by 3 turns, row by row
	private static float[] turnX(float a) {
		float c = Mth.cos(a), s = Mth.sin(a);
		return new float[] {1, 0, 0, 0, c, -s, 0, s, c};
	}

	private static float[] turnY(float a) {
		float c = Mth.cos(a), s = Mth.sin(a);
		return new float[] {c, 0, s, 0, 1, 0, -s, 0, c};
	}

	private static float[] turnZ(float a) {
		float c = Mth.cos(a), s = Mth.sin(a);
		return new float[] {c, -s, 0, s, c, 0, 0, 0, 1};
	}

	private static float[] times(float[] a, float[] b) {
		float[] out = new float[9];
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				out[row * 3 + col] = a[row * 3] * b[col] + a[row * 3 + 1] * b[3 + col] + a[row * 3 + 2] * b[6 + col];
			}
		}
		return out;
	}

	/**
	 * A crouch with a rounded back: the body leans {@code crouch} forward on the whole, its upper half {@code hunch}
	 * further forward than its lower (the fold of the spine, halfway down, is returned for {@link #bendLimbs}); the
	 * legs stay under the hips, and the head is left as it is, up.
	 */
	private static float hunch(HumanoidModel<?> model, float crouch, float hunch) {
		float upper = crouch + hunch * 0.5F, lower = crouch - hunch * 0.5F;
		model.body.xRot += upper;
		float back = (Mth.sin(upper) + Mth.sin(lower)) * TORSO * 0.5F;
		model.rightLeg.z += back;
		model.leftLeg.z += back;
		return -hunch;
	}

	/** Tips the body and head to the side ({@code amount} radians; positive is the boxer's left). */
	private static void lean(HumanoidModel<?> model, float amount) {
		model.body.zRot += amount;
		model.head.zRot += amount * 0.6F;
	}
}
