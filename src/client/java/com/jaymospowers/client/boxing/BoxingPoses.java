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
	/**
	 * A punch lasts this long (ticks), whatever the game's own arm swing does; a straight one is at full reach this
	 * far through (it snaps out and comes home slower).
	 */
	static final float PUNCH_TICKS = 6.0F, SNAP = 0.4F;
	/**
	 * Before it goes, a punch winds up for {@link Boxing#WINDUP} ticks: a straight punch's arm draws back this share
	 * of its reach (and the body coils the other way by as much of its turn); a hook's arm rises out wide while the
	 * body coils back this share of its turn; an uppercut dips this far (radians) as the fist drops.
	 */
	static final float STRAIGHT_COIL = 0.3F, HOOK_COIL = 0.4F, UPPERCUT_DIP = 0.15F;
	/** The game's own arm swing lasts this long (ticks): how far into a click a swing is. A local windup gives way to the server's word of the punch if that comes within the second number of ticks. */
	static final float SWING_TICKS = 6.0F, LOCAL_GRACE = 8.0F;
	/** A jab: the lead arm straight out at chin height, its shoulder up to the chin (pixels) and turned in behind it (radians). The rear glove stays home. */
	static final float JAB_PITCH = -1.62F, JAB_SHOULDER = 0.8F, JAB_TURN = 0.3F;
	/**
	 * A cross (and the straights that are thrown like it): the rear arm straight out, the rear shoulder and hip turned
	 * right through, the body leaning into it, the rear knee turned in on the ball of the foot, and the lead glove
	 * brought home to the cheek (its upper arm's pitch and turn in, and its fold).
	 */
	static final float STRAIGHT_PITCH = -1.57F, CROSS_TURN = 0.75F, CROSS_LEAN = 0.1F, CROSS_KNEE = 0.5F, HOME_PITCH = -1.3F, HOME_IN = 0.45F, HOME_FOLD = -1.85F;
	/**
	 * A hook, after the lesson Jung sent ("Throw a Perfect Lead and Rear Hook"): the arm level at shoulder height, the
	 * elbow open between a right angle and 120 degrees (so the fist reaches, rather than hugging the chest), swung from
	 * out wide to land ahead on the centre line while the hips and shoulders turn with it and the foot on that side
	 * pivots. The numbers: the upper arm's pitch, its sweep (turned in, radians) from start to landing, the forearm's
	 * fold across, the body's turn, the pivoting leg's turn and the lean into it. Light in a string (the Arrow Slit's
	 * flurry); the full hook is the one a slip to the side sets up.
	 */
	static final float HOOK_PITCH = -1.5F, HOOK_FROM = -1.0F, HOOK_TO = 0.2F, HOOK_ACROSS = 0.9F, HOOK_TURN = 0.7F, HOOK_PIVOT = 0.6F, HOOK_LEAN = 0.08F;
	static final float LIGHT_HOOK_FROM = -0.55F, LIGHT_HOOK_TO = 0.15F, LIGHT_HOOK_ACROSS = 1.1F, LIGHT_HOOK_TURN = 0.4F, LIGHT_HOOK_PIVOT = 0.35F;
	/** A hook's timing, as shares of the punch: the arm is up into the swing by the first, lands at the second, and is on its way home over the last. */
	static final float HOOK_UP = 0.15F, HOOK_LANDS = 0.55F, HOOK_HOME = 0.3F;
	/** A hook is the full one for this long after a slip (ticks). */
	static final float HEAVY_HOOK_TICKS = 30.0F;
	/** An uppercut: the upper arm from by the hip up to ahead, the forearm up, the fist rising under the chin; the body's turn into it. */
	static final float UPPERCUT_FROM = 0.15F, UPPERCUT_TO = -1.2F, UPPERCUT_IN = 0.3F, UPPERCUT_FOLD = -1.7F, PUNCH_TURN = 0.3F;
	/** First person: how far a punching arm goes forward, in toward the middle and up (blocks), for a straight punch, a hook and an uppercut. */
	static final float[] FIRST_STRAIGHT = {0.5F, 0.12F, 0.04F}, FIRST_HOOK = {0.38F, 0.32F, 0.05F}, FIRST_UPPERCUT = {0.2F, 0.1F, 0.3F};
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

	/**
	 * A boxer, as their render state carries them. {@code punch}: the punch being thrown right now, or null;
	 * {@code wind}: how far through its windup it is (0 to 1); {@code punchAt}: how far through the punch itself (0
	 * until the windup is done, then to 1); {@code heavy}: a hook thrown out of a slip, the full one.
	 * {@code moveAge}: ticks since the evade began.
	 */
	public record Look(Boxing.Style style, int spent, Boxing.@Nullable Punch punch, float wind, float punchAt, boolean heavy, BoxingEvades.@Nullable Move move,
		float moveAge, boolean blocking) {
	}

	/**
	 * The last punch each player was told to have thrown ({@link Boxing#LOOK}, less its tiredness bits), and when its
	 * pose began (game ticks). {@code local}: nobody told of it; it is the click's own swing, shown as a jab.
	 */
	private record Thrown(int look, float at, boolean local) {
	}

	private static final java.util.Map<Integer, Thrown> THROWN = new java.util.HashMap<>();

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
		boolean slipped = false;
		Long moved = player.getAttached(BoxingEvades.MOVED);
		if (moved != null) {
			int index = (int)(moved & 7);
			age = player.level().getGameTime() - (moved >> 3) + partialTicks;
			if (index >= 1 && index <= BoxingEvades.Move.values().length && age >= 0.0F) {
				BoxingEvades.Move made = BoxingEvades.Move.values()[index - 1];
				slipped = age < HEAVY_HOOK_TICKS && (made == BoxingEvades.Move.SLIP_LEFT || made == BoxingEvades.Move.SLIP_RIGHT);
				if (age < MOVE_TICKS) {
					move = made;
				}
			}
		}
		// The punch's pose (a windup, then the punch) runs on its own clock, from when the server says a punch was thrown
		// (so a queued punch, thrown a moment after the click, shows too). The click's own swing starts it at once, as a
		// jab; if word of the real punch comes soon after, the pose carries on as that punch from where it is.
		Integer told = player.getAttached(Boxing.LOOK);
		int seen = told == null ? 0 : told >> 2;
		float now = player.level().getGameTime() + partialTicks;
		boolean swinging = state.currentSwing != null && state.swingAnimation > 0.0F;
		// (the pose's windup ends a little before the server's, so the punch is at full reach as the blow lands)
		float windup = Boxing.WINDUP - SNAP * PUNCH_TICKS, whole = windup + PUNCH_TICKS;
		if (THROWN.size() > 512) {
			THROWN.clear();
		}
		Thrown thrown = THROWN.get(player.getId());
		if (thrown == null) {
			thrown = new Thrown(seen, now - whole, false);
			THROWN.put(player.getId(), thrown);
		} else if (thrown.look() != seen) {
			boolean carried = thrown.local() && now - thrown.at() < LOCAL_GRACE;
			thrown = new Thrown(seen, carried ? thrown.at() : now, false);
			THROWN.put(player.getId(), thrown);
		} else if (swinging && state.swingAnimation < 0.34F && now - thrown.at() >= whole) {
			thrown = new Thrown(seen, now - state.swingAnimation * SWING_TICKS, true);
			THROWN.put(player.getId(), thrown);
		}
		Boxing.Punch punch = thrown.local() ? Boxing.Punch.JAB : Boxing.shownPunch(player);
		float since = now - thrown.at(), wind = 0.0F, punchAt = 0.0F;
		if (punch != null && since >= 0.0F && since < whole) {
			wind = Math.min(1.0F, since / windup);
			punchAt = Math.max(0.0F, (since - windup) / PUNCH_TICKS);
		} else {
			punch = null;
		}
		state.setData(KEY, new Look(style, Boxing.spent(player), punch, wind, punchAt, slipped, move, age, player.isBlocking()));
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
		boolean swinging = look.punch() != null;
		if (state.currentSwing != null && state.swingAnimation > 0.0F) {
			// the game's own arm swing has turned the body and carried the shoulders round: a boxer's punch is posed whole here
			model.body.yRot = 0.0F;
			model.rightArm.x = -SHOULDER_OUT;
			model.leftArm.x = SHOULDER_OUT;
			model.rightArm.z = 0.0F;
			model.leftArm.z = 0.0F;
		}
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
			turn += punch(model, look, lead, rear, leadElbow, rearElbow, leadRight);
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
	 * The punch {@code look} is throwing. The arm that throws it goes out and comes home; the body, the other glove
	 * and the rear leg do their part. Returns how far the punch turns the lead shoulder forward (negative: the rear
	 * one comes through).
	 * <ul>
	 * <li>A <b>jab</b>: the lead arm snaps straight out at chin height, its shoulder up and turned in behind it; the
	 * rear glove doesn't leave the cheek.</li>
	 * <li>A <b>cross</b> (and a straight or pull counter): the rear arm straight out as the rear shoulder and hip
	 * turn right through, the rear knee turning in, the body leaning into it; the lead glove comes home to the cheek.</li>
	 * <li>A <b>hook</b>: the upper arm level, the forearm folded across, swept in as the body turns with it; lighter
	 * in a string than out of a slip.</li>
	 * <li>An <b>uppercut</b>: the fist rising under the chin with the forearm up.</li>
	 * </ul>
	 */
	private static float punch(HumanoidModel<?> model, Look look, ModelPart lead, ModelPart rear, Elbow leadElbow, Elbow rearElbow, boolean leadRight) {
		float at = look.punchAt();
		boolean winding = look.wind() < 1.0F;
		// the windup's coil (0 to 1), then the punch: out fast, home slower
		float coil = Mth.sin(look.wind() * Mth.HALF_PI);
		float out = drive(look);
		float forward = Math.max(0.0F, out);
		switch (look.punch()) {
			case JAB -> {
				reach(lead, leadRight, JAB_PITCH, 0.0F, out);
				leadElbow.fold *= 1.0F - out;
				lead.y -= JAB_SHOULDER * forward;
				return JAB_TURN * out;
			}
			case CROSS, STRAIGHT, PULL_COUNTER -> {
				reach(rear, !leadRight, STRAIGHT_PITCH, 0.0F, out);
				rearElbow.fold *= 1.0F - out;
				if (!leadElbow.across) {
					// the lead glove comes home to the cheek (the Bastion's stays low, where it lives)
					reach(lead, leadRight, HOME_PITCH, HOME_IN, forward);
					leadElbow.fold = Mth.lerp(forward, leadElbow.fold, HOME_FOLD);
				}
				bend(model, CROSS_LEAN * forward);
				// the rear knee turns in as the hip comes through
				(leadRight ? model.leftLeg : model.rightLeg).yRot += (leadRight ? CROSS_KNEE : -CROSS_KNEE) * forward;
				return -CROSS_TURN * out;
			}
			case LEAD_HOOK, REAR_HOOK -> {
				boolean leading = look.punch() == Boxing.Punch.LEAD_HOOK, heavy = look.heavy();
				ModelPart arm = leading ? lead : rear;
				boolean right = leading == leadRight;
				// the windup lifts the arm out wide as the body coils back; then it sweeps in to land ahead, and comes home over the end
				float home = Math.min(1.0F, (1.0F - at) / HOOK_HOME);
				float in = winding ? coil : home;
				float sweep = winding ? 0.0F : Mth.sin(Mth.clamp(at / HOOK_LANDS, 0.0F, 1.0F) * Mth.HALF_PI);
				reach(arm, right, HOOK_PITCH, Mth.lerp(sweep, heavy ? HOOK_FROM : LIGHT_HOOK_FROM, heavy ? HOOK_TO : LIGHT_HOOK_TO), in);
				arm.zRot = 0.0F;
				Elbow elbow = leading ? leadElbow : rearElbow;
				if (!winding || look.wind() > 0.5F) {
					elbow.across(heavy ? HOOK_ACROSS : LIGHT_HOOK_ACROSS);
				}
				// the hips turn with it: the foot on the punching side pivots, and the body leans a little in
				float through = winding ? -HOOK_COIL * coil : Mth.lerp(sweep, -HOOK_COIL, 1.0F) * home;
				float planted = Math.max(0.0F, through);
				ModelPart leg = right ? model.rightLeg : model.leftLeg;
				leg.yRot += (right ? -1.0F : 1.0F) * (heavy ? HOOK_PIVOT : LIGHT_HOOK_PIVOT) * planted;
				bend(model, HOOK_LEAN * planted);
				float turned = (heavy ? HOOK_TURN : LIGHT_HOOK_TURN) * through;
				return leading ? turned : -turned;
			}
			case UPPERCUT -> {
				// the windup drops the fist by the hip and dips the body; then it rises
				float dip = winding ? coil : 1.0F - Mth.clamp(at * 2.0F, 0.0F, 1.0F);
				reach(rear, !leadRight, Mth.lerp(at, UPPERCUT_FROM, UPPERCUT_TO), UPPERCUT_IN, winding ? coil : 1.0F);
				rearElbow.forward(Mth.lerp(winding ? coil : 1.0F, rearElbow.across ? 0.0F : rearElbow.fold, UPPERCUT_FOLD));
				bend(model, UPPERCUT_DIP * dip);
				return -PUNCH_TURN * (winding ? -HOOK_COIL * coil : Mth.sin(at * Mth.PI));
			}
		}
		return 0.0F;
	}

	/**
	 * How far out a punch is: during the windup it draws back (down to minus {@link #STRAIGHT_COIL}), then it snaps
	 * out to 1 by {@link #SNAP} of the punch and comes home to 0 slower.
	 */
	private static float drive(Look look) {
		if (look.wind() < 1.0F) {
			return -STRAIGHT_COIL * Mth.sin(look.wind() * Mth.HALF_PI);
		}
		float at = look.punchAt();
		return at < SNAP ? Mth.lerp(Mth.sin(at / SNAP * Mth.HALF_PI), -STRAIGHT_COIL, 1.0F) : Mth.cos((at - SNAP) / (1.0F - SNAP) * Mth.HALF_PI);
	}

	/**
	 * First person: where a boxer's own {@code right} or left arm is in its punch, as how far it has gone forward, in
	 * toward the middle of the view and up (blocks); all zero for an arm that isn't punching. Null if this isn't a boxer.
	 */
	public static float @Nullable [] firstPerson(AvatarRenderState state, boolean right) {
		Look look = state.getData(KEY);
		if (look == null) {
			return null;
		}
		Boxing.Punch punch = look.punch();
		boolean leadRight = look.style() == Boxing.Style.BASTION;
		boolean leading = punch == Boxing.Punch.JAB || punch == Boxing.Punch.LEAD_HOOK;
		if (punch == null || look.blocking() || leading != (right == leadRight)) {
			return new float[3];
		}
		// (drawn back a little through the windup, then out)
		float out = drive(look);
		float[] reach = switch (punch) {
			case LEAD_HOOK, REAR_HOOK -> FIRST_HOOK;
			case UPPERCUT -> FIRST_UPPERCUT;
			default -> FIRST_STRAIGHT;
		};
		float light = (punch == Boxing.Punch.LEAD_HOOK || punch == Boxing.Punch.REAR_HOOK) && !look.heavy() ? 0.7F : 1.0F;
		return new float[] {reach[0] * out * light, reach[1] * out * light, reach[2] * out * light};
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
