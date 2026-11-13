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
	static final float SLIT_PITCH = -1.2F, SLIT_IN = 0.25F, SLIT_FOLD = -0.8F, SLIT_SWAY = 0.12F, SLIT_SWAY_RATE = 0.14F;
	static final float SLIT_BLOCK_PITCH = -1.3F, SLIT_BLOCK_IN = 0.25F, SLIT_BLOCK_FOLD = -0.8F;
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
	static final float LONG_REAR_PITCH = -1.2F, LONG_REAR_IN = -0.35F, LONG_REAR_FOLD = -1.2F, LONG_BLOCK_PITCH = -1.3F, LONG_BLOCK_IN = 0.25F, LONG_BLOCK_FOLD = -0.3F;
	/** The Long Guard's lead shoulder turned forward, its slight lean in behind the lead arm and its tucked chin (radians). */
	static final float LONG_BLADE = 0.6F, LONG_LEAN = 0.08F, LONG_CHIN = 0.1F;
	/**
	 * Its base: the feet about under the hips, the lead one a little ahead, the knees soft, and the toes straight ahead
	 * (Jung: turned out with the body they looked awkward). The Bastion's feet turn with its body by {@code FEET_TURN} of its turn.
	 */
	static final float LONG_SPREAD = 0.1F, LONG_STAGGER = 0.2F, LONG_KNEES = 0.2F, FEET_TURN = 0.8F;
	/** The Bastion's lead arm: the upper arm down and a little forward, the forearm folded across the belly. Its rear glove by the cheek. */
	static final float BASTION_LEAD_PITCH = -0.19F, BASTION_LEAD_IN = 0.06F, BASTION_LEAD_ACROSS = 0.5F, BASTION_REAR_PITCH = -1.17F, BASTION_REAR_IN = 0.06F, BASTION_REAR_FOLD = -1.71F;
	/** Its base: feet under the shoulders, the lead one ahead, the knees soft. */
	static final float BASTION_SPREAD = 0.22F, BASTION_STAGGER = 0.2F, BASTION_KNEES = 0.34F;
	/** The Bastion side on (its lead shoulder turned forward, radians), that shoulder held up (pixels), the weight back, the head tipped to the rear shoulder and the chin tucked. */
	static final float BASTION_BLADE = 0.45F, BASTION_SHOULDER = 1.0F, BASTION_LEAN = -0.04F, BASTION_HEAD_TILT = 0.12F, BASTION_CHIN = 0.1F;
	/** How much further side on its block stands. */
	static final float BASTION_BLOCK_BLADE = 0.45F;
	/** (Jung: "make it look like the video with overlapping") How far forward each shoulder rolls round the chest (pixels). */
	static final float BASTION_LEAD_ROLL = 2.9F, BASTION_REAR_ROLL = 3.35F;
	/** How far the lead upper arm swings in across the body, in the stance and the block (radians). */
	static final float BASTION_LEAD_ADDUCT = 0.48F, BASTION_BLOCK_ADDUCT = 0.51F;
	/** The shrug: every this many ticks, this long, this high (pixels). */
	static final float SHRUG_EVERY = 90.0F, SHRUG_TICKS = 12.0F, SHRUG_UP = 1.0F;
	/** The Bastion's block: the lead shoulder higher, the forearm a little higher over the body, the rear glove tight to the jaw. */
	static final float BASTION_BLOCK_LEAD_PITCH = -0.2F, BASTION_BLOCK_UP = 0.85F, BASTION_BLOCK_ACROSS = 0.66F, BASTION_BLOCK_REAR_PITCH = -1.3F, BASTION_BLOCK_REAR_IN = 0.1F, BASTION_BLOCK_REAR_FOLD = -1.6F;
	/**
	 * A punch lasts this long (ticks), whatever the game's own arm swing does; a straight one is at full reach this
	 * far through (it snaps out and comes home slower).
	 */
	static final float PUNCH_TICKS = 6.0F, SNAP = 0.4F;
	/**
	 * Before it goes, a punch winds up for {@link Boxing#windup} ticks: a straight punch's arm draws back this share
	 * of its reach (and the body coils the other way by as much of its turn); a hook's arm rises out wide while the
	 * body coils back this share of its turn; an uppercut dips this far (radians) as the fist drops.
	 */
	/** How far a straight's windup draws the glove itself back (of the arm's whole reach; the body coils the rest). */
	static final float STRAIGHT_GLOVE_COIL = 0.05F;
	static final float STRAIGHT_COIL = 0.3F, HOOK_COIL = 0.4F, UPPERCUT_DIP = 0.15F;
	/**
	 * (Jung: "the torso set more over the hits") How far each punch leans the trunk in about the hips (the cross's
	 * and the hook's are with their own numbers), and how much deeper it sits into the knees as it lands (radians);
	 * a hook's tilt over the leg on its side.
	 */
	static final float JAB_LEAN = 0.12F, JAB_DUCK = 0.08F, CROSS_DUCK = 0.12F, HOOK_DUCK = 0.1F, HOOK_TILT = 0.07F, UPPERCUT_DUCK = 0.2F;
	/** The game's own arm swing lasts this long (ticks): how far into a click a swing is. A local windup gives way to the server's word of the punch if that comes within the second number of ticks. */
	static final float SWING_TICKS = 6.0F, LOCAL_GRACE = 8.0F;
	/** A jab: the lead arm straight out at chin height, its shoulder up to the chin (pixels) and turned in behind it (radians). The rear glove stays home. */
	static final float JAB_PITCH = -1.62F, JAB_SHOULDER = 0.8F, JAB_TURN = 0.3F;
	/**
	 * A cross (and the straights that are thrown like it): the rear arm straight out, the rear shoulder and hip turned
	 * right through, the body leaning into it, the rear knee turned in on the ball of the foot, and the lead glove
	 * brought home to the cheek (its upper arm's pitch and turn in, and its fold).
	 */
	static final float STRAIGHT_PITCH = -1.57F, CROSS_TURN = 0.75F, CROSS_LEAN = 0.2F, CROSS_KNEE = 0.5F, HOME_PITCH = -1.6F, HOME_IN = 0.0F, HOME_FOLD = -0.6F;
	/**
	 * A hook, after the lesson Jung sent ("Throw a Perfect Lead and Rear Hook"): the arm level at shoulder height, the
	 * elbow open between a right angle and 120 degrees (so the fist reaches, rather than hugging the chest), swung from
	 * out wide to land ahead on the centre line while the hips and shoulders turn with it and the foot on that side
	 * pivots. The numbers: the upper arm's pitch, its sweep (turned in, radians) from start to landing, the forearm's
	 * fold across, the body's turn, the pivoting leg's turn and the lean into it. Light in a string (the Arrow Slit's
	 * flurry); the full hook is the one a slip to the side sets up.
	 */
	static final float HOOK_PITCH = -1.5F, HOOK_FROM = -1.0F, HOOK_TO = 0.2F, HOOK_ACROSS = 0.9F, HOOK_TURN = 0.7F, HOOK_PIVOT = 0.6F, HOOK_LEAN = 0.14F;
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
	static final float MOVE_TICKS = 8.0F, ROLL_TURN = 0.6F, ROLL_UP = 1.0F;
	/**
	 * (Jung: "a step forward and duck at the same time keeping the body connected") Each evade's side lean and forward
	 * lean about the hips, and how much deeper it bends the knees (radians). A slip leans off the line and in; a bob
	 * folds forward and sinks; the pull shifts the weight back over the rear leg, still sitting down into the knees; a
	 * shoulder roll dips in and away from the blow. The roll also tips the head away ({@code ROLL_HEAD}).
	 */
	static final float SLIP_LEAN = 0.26F, SLIP_FORWARD = 0.16F, SLIP_DUCK = 0.25F;
	static final float BOB_DUCK = 0.38F, BOB_KNEES = 0.45F;
	static final float PULL_LEAN = -0.18F, PULL_DUCK = 0.15F;
	static final float ROLL_SIDE = 0.14F, ROLL_FORWARD = 0.12F, ROLL_DUCK = 0.3F, ROLL_HEAD = 0.15F;
	/** A model's shoulders are this far from its middle, and its hips this far below its neck (pixels). */
	static final float SHOULDER_OUT = 5.0F, TORSO = 12.0F;
	/**
	 * (Jung: "no air between body portions") The body sits this far down over the tops of the thighs (pixels), so a
	 * thigh tipped forward or out doesn't open a gap under it; the chest's half width and half depth (plus a little,
	 * pixels), and how much of the gap a turned arm opens at its shoulder is closed by moving the shoulder in.
	 */
	static final float HIP_TUCK = 1.0F, CHEST_SIDE = 4.0F, CHEST_DEPTH = 2.6F, SHOULDER_SEAT = 0.8F;
	/** A leg's thigh, and its shin (pixels). */
	static final float THIGH = 6.0F, SHIN = 6.0F;
	/** Gassed: the hands dropped, the shoulders' heave (pixels, radians a tick), the slump. */
	static final float GASSED_PITCH = -0.45F, GASSED_IN = 0.15F, GASSED_FOLD = -0.5F, HEAVE = 0.6F, HEAVE_RATE = 0.3F, SLUMP = 0.18F;
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
	/**
	 * (Jung: "the body looks disconnected") The trunk as the pose builds it up, put on the model at the end by
	 * {@link #settle}: it leans and turns about the <b>hips</b>, not about the neck the model's body part pivots on, so
	 * the waist stays on the legs, and the neck, head and shoulders ride its top. Render thread only, reset each pose.
	 */
	private static final class Trunk {
		/** Forward lean of the lower back (radians; negative is back), the upper back's further curl, the side lean (positive is the boxer's left) and the twist. */
		float pitch, curl, side, twist;
		/** The part of the lean and side lean an evade adds (the guard tips with its side lean; a punch's leaves the punching arm aimed). */
		float evadePitch, evadeSide;
		/** How far a punch or an evade turns the body past the stance (the guard turns with it), and the punching arm (which doesn't). */
		float guardTwist;
		@Nullable ModelPart punching;
		/** How much of that turn the punching arm goes with: all of it while it is home or winding up, none at full reach. */
		float ride;
		/** The head's own side tip, on top of the look. */
		float headSide;
		/** The stance's base ({@link #base}), and how much deeper an evade or punch bends the knees. */
		float spread, stagger, knees, blade, duck;
		boolean based;

		void reset() {
			pitch = curl = side = twist = evadePitch = evadeSide = guardTwist = headSide = spread = stagger = knees = blade = duck = 0.0F;
			punching = null;
			ride = 0.0F;
			based = false;
		}
	}

	private static final Trunk TRUNK = new Trunk();

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
		float windup = Boxing.windup(player) - SNAP * PUNCH_TICKS, whole = windup + PUNCH_TICKS;
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
		TRUNK.reset();
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
			settle(model, leadRight, leadElbow);
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
					hunch(model, SLIT_BLOCK_CROUCH, SLIT_HUNCH);
					base(model, leadRight, SLIT_SPREAD, SLIT_STAGGER, SLIT_BLOCK_KNEES, 0.0F);
				}
				case LONG_GUARD -> {
					set(lead, leadRight, LONG_BLOCK_PITCH, LONG_BLOCK_IN);
					set(rear, !leadRight, LONG_BLOCK_PITCH, LONG_BLOCK_IN);
					leadElbow.forward(LONG_BLOCK_FOLD);
					rearElbow.forward(LONG_BLOCK_FOLD);
					bend(model, LONG_LEAN);
					model.head.xRot += LONG_CHIN;
					turn += LONG_BLADE;
					base(model, leadRight, LONG_SPREAD, LONG_STAGGER, LONG_KNEES, 0.0F);
				}
				case BASTION -> {
					set(lead, leadRight, BASTION_BLOCK_LEAD_PITCH, BASTION_LEAD_IN);
					leadElbow.across(BASTION_BLOCK_ACROSS);
					lead.y -= BASTION_BLOCK_UP;
					rollShoulders(lead, rear, leadRight, BASTION_BLOCK_ADDUCT);
					set(rear, !leadRight, BASTION_BLOCK_REAR_PITCH, BASTION_BLOCK_REAR_IN);
					rearElbow.forward(BASTION_BLOCK_REAR_FOLD);
					turn += BASTION_BLOCK_BLADE;
					bend(model, BASTION_LEAN);
					shellHead(model, leadRight);
					base(model, leadRight, BASTION_SPREAD, BASTION_STAGGER, BASTION_KNEES, BASTION_BLADE);
				}
			}
		} else {
			turn += stance(model, look.style(), lead, rear, leadElbow, rearElbow, leadRight, time);
		}
		float stood = turn;
		if (swinging && !look.blocking()) {
			turn += punch(model, look, lead, rear, leadElbow, rearElbow, leadRight);
		}
		if (look.move() != null) {
			// out and back over the evade's few ticks
			float amount = Mth.sin(Mth.clamp(look.moveAge() / MOVE_TICKS, 0.0F, 1.0F) * Mth.PI);
			switch (look.move()) {
				// a slip, a bob or a roll steps in and ducks: the knees bend and the trunk goes forward over the lead leg,
				// the slip leaning off the line about the hips; the pull shifts the weight back the same way, about the hips
				case SLIP_LEFT -> evade(SLIP_LEAN * amount, SLIP_FORWARD * amount, SLIP_DUCK * amount);
				case SLIP_RIGHT -> evade(-SLIP_LEAN * amount, SLIP_FORWARD * amount, SLIP_DUCK * amount);
				case BOB -> {
					evade(0.0F, BOB_DUCK * amount, BOB_KNEES * amount);
					model.head.xRot += BOB_DUCK * amount * 0.3F;
				}
				case PULL -> evade(0.0F, PULL_LEAN * amount, PULL_DUCK * amount);
				case ROLL -> {
					// the lead shoulder turns in to take the blow, and the body dips under and away from it
					turn += ROLL_TURN * amount;
					lead.y -= ROLL_UP * amount;
					evade((leadRight ? ROLL_SIDE : -ROLL_SIDE) * amount, ROLL_FORWARD * amount, ROLL_DUCK * amount);
					model.head.zRot += (leadRight ? ROLL_HEAD : -ROLL_HEAD) * amount;
				}
			}
		}
		turn(model, leadRight ? -turn : turn);
		// the guard turns with the body as a punch or a shoulder roll turns it (the stance's own turn is in its numbers)
		TRUNK.guardTwist = leadRight ? stood - turn : turn - stood;
		settle(model, leadRight, leadElbow);
		keep(state, leadRight, leadElbow, rearElbow);
		return true;
	}

	/** Leaves the elbows on the state for {@link #bendLimbs} and the worn gear. An arm's own across is toward the body: +X for the right arm, which is a negative fold. */
	private static void keep(AvatarRenderState state, boolean leadRight, Elbow lead, Elbow rear) {
		Elbow right = leadRight ? lead : rear, left = leadRight ? rear : lead;
		state.setData(BENDS, new Bends(right.across ? -right.fold : right.fold, right.across, left.fold, left.across, lead.knees, -TRUNK.curl));
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
				hunch(model, SLIT_CROUCH, SLIT_HUNCH);
				base(model, leadRight, SLIT_SPREAD, SLIT_STAGGER, SLIT_KNEES, 0.0F);
				return 0.0F;
			}
			case LONG_GUARD -> {
				set(lead, leadRight, LONG_LEAD_PITCH + Mth.sin(time * LONG_PAW_RATE) * LONG_PAW, LONG_LEAD_IN + Mth.cos(time * LONG_PAW_SIDE_RATE) * LONG_PAW_SIDE);
				set(rear, !leadRight, LONG_REAR_PITCH, LONG_REAR_IN);
				leadElbow.forward(LONG_LEAD_FOLD);
				rearElbow.forward(LONG_REAR_FOLD);
				bend(model, LONG_LEAN);
				model.head.xRot += LONG_CHIN;
				base(model, leadRight, LONG_SPREAD, LONG_STAGGER, LONG_KNEES, 0.0F);
				return LONG_BLADE;
			}
			case BASTION -> {
				set(lead, leadRight, BASTION_LEAD_PITCH, BASTION_LEAD_IN);
				set(rear, !leadRight, BASTION_REAR_PITCH, BASTION_REAR_IN);
				leadElbow.across(BASTION_LEAD_ACROSS);
				rearElbow.forward(BASTION_REAR_FOLD);
				lead.y -= BASTION_SHOULDER;
				rollShoulders(lead, rear, leadRight, BASTION_LEAD_ADDUCT);
				float phase = time % SHRUG_EVERY / SHRUG_TICKS;
				if (phase < 1.0F) {
					lead.y -= Mth.sin(phase * Mth.PI) * SHRUG_UP;
				}
				bend(model, BASTION_LEAN);
				shellHead(model, leadRight);
				base(model, leadRight, BASTION_SPREAD, BASTION_STAGGER, BASTION_KNEES, BASTION_BLADE);
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
		TRUNK.ride = 1.0F - forward;
		switch (look.punch()) {
			case JAB -> {
				TRUNK.punching = lead;
				// (a forearm folded across, the Bastion's, isn't drawn back into the body by the windup)
				reach(lead, leadRight, JAB_PITCH, 0.0F, leadElbow.across ? forward : out);
				leadElbow.fold *= 1.0F - (leadElbow.across ? forward : out);
				lead.y -= JAB_SHOULDER * forward;
				// the trunk goes in behind it, over the lead knee
				bend(model, JAB_LEAN * forward);
				TRUNK.duck += JAB_DUCK * forward;
				return JAB_TURN * out;
			}
			case CROSS, STRAIGHT, PULL_COUNTER -> {
				TRUNK.punching = rear;
				// the windup coils the body; the glove barely leaves the chin, so it isn't drawn back into the face
				float straight = Math.max(out, -STRAIGHT_GLOVE_COIL);
				reach(rear, !leadRight, STRAIGHT_PITCH, 0.0F, straight);
				rearElbow.fold *= 1.0F - straight;
				if (!leadElbow.across) {
					// the lead glove comes home to the cheek (the Bastion's stays low, where it lives)
					reach(lead, leadRight, HOME_PITCH, HOME_IN, forward);
					leadElbow.fold = Mth.lerp(forward, leadElbow.fold, HOME_FOLD);
				}
				bend(model, CROSS_LEAN * forward);
				TRUNK.duck += CROSS_DUCK * forward;
				// the rear knee turns in as the hip comes through
				(leadRight ? model.leftLeg : model.rightLeg).yRot += (leadRight ? CROSS_KNEE : -CROSS_KNEE) * forward;
				return -CROSS_TURN * out;
			}
			case LEAD_HOOK, REAR_HOOK -> {
				boolean leading = look.punch() == Boxing.Punch.LEAD_HOOK, heavy = look.heavy();
				ModelPart arm = leading ? lead : rear;
				TRUNK.punching = arm;
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
				// the weight sits over the leg on the punching side
				TRUNK.side += (right ? -HOOK_TILT : HOOK_TILT) * planted;
				TRUNK.duck += HOOK_DUCK * planted;
				float turned = (heavy ? HOOK_TURN : LIGHT_HOOK_TURN) * through;
				return leading ? turned : -turned;
			}
			case UPPERCUT -> {
				// the windup drops the fist by the hip and dips the body; then it rises
				TRUNK.punching = rear;
				float dip = winding ? coil : 1.0F - Mth.clamp(at * 2.0F, 0.0F, 1.0F);
				reach(rear, !leadRight, Mth.lerp(at, UPPERCUT_FROM, UPPERCUT_TO), UPPERCUT_IN, winding ? coil : 1.0F);
				rearElbow.forward(Mth.lerp(winding ? coil : 1.0F, rearElbow.across ? 0.0F : rearElbow.fold, UPPERCUT_FOLD));
				bend(model, UPPERCUT_DIP * dip);
				TRUNK.duck += UPPERCUT_DUCK * dip;
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
		TRUNK.twist += amount;
	}

	/** Leans the trunk {@code amount} radians forward about the hips (negative: back); the head and shoulders ride its top. */
	private static void bend(HumanoidModel<?> model, float amount) {
		TRUNK.pitch += amount;
	}

	/** An evade's share of the trunk: a side lean, a forward lean (both about the hips; the guard goes with them) and a deeper knee bend. */
	private static void evade(float side, float forward, float duck) {
		TRUNK.side += side;
		TRUNK.evadeSide += side;
		TRUNK.pitch += forward;
		TRUNK.evadePitch += forward;
		TRUNK.duck += duck;
		TRUNK.headSide += side * 0.6F;
	}

	/**
	 * Sways the shoulders and head {@code amount} radians to the side (positive is the boxer's left) about the hips,
	 * which stay where they are: the body turns about its foot, and the head and arms ride its top.
	 */
	private static void swayShoulders(HumanoidModel<?> model, float amount) {
		TRUNK.side += amount;
		TRUNK.headSide += amount * 0.6F;
	}

	/**
	 * A boxer's base: the feet {@code spread} apart, the lead foot {@code stagger} ahead and the rear one as far
	 * behind, both knees bent by {@code knees} (each thigh that far forward; the shin's fold back, twice that, is
	 * returned for {@link #bendLimbs}), and the feet turned with a body whose lead shoulder is {@code blade} forward.
	 * The step ahead and behind is along the way the boxer faces, whichever way the toes are turned, so a bladed
	 * stance's feet don't cross. Everything above the feet sinks by what the legs shorten, so the feet stay grounded.
	 */
	private static float base(HumanoidModel<?> model, boolean leadRight, float spread, float stagger, float knees, float blade) {
		TRUNK.spread = spread;
		TRUNK.stagger = stagger;
		TRUNK.knees = knees;
		TRUNK.blade = blade;
		TRUNK.based = true;
		return knees * 2.0F;
	}

	/**
	 * Puts the trunk on the model, last of all. The legs take the base, the knees bent by the stance and whatever an
	 * evade adds; the hips sink by what the legs shorten, so the feet stay on the ground. The body part is turned
	 * about the hips: its waist is put back on them, so its neck (the part's own pivot) moves with the lean, and the
	 * head and shoulders go with the neck. The shoulders turn round with the twist and lean; an arm's own aim is left
	 * alone (a punch still goes where it is thrown, and the gloves stay at the face as the trunk dips) except that the
	 * guard tips with an evade's side lean.
	 */
	private static void settle(HumanoidModel<?> model, boolean leadRight, Elbow leadElbow) {
		Trunk t = TRUNK;
		float knees = t.knees + t.duck;
		float sink = t.based || t.duck != 0.0F ? (THIGH + SHIN) * (1.0F - Mth.cos(knees) * Mth.cos(t.stagger) * Mth.cos(t.spread)) : 0.0F;
		if (t.based || t.duck != 0.0F) {
			model.rightLeg.y += sink;
			model.leftLeg.y += sink;
			float feet = (leadRight ? -t.blade : t.blade) * FEET_TURN;
			// (a punch's pivot of a foot is in the leg's turn already)
			leg(model.rightLeg, t.spread, leadRight ? -t.stagger : t.stagger, feet + model.rightLeg.yRot, knees);
			leg(model.leftLeg, -t.spread, leadRight ? t.stagger : -t.stagger, feet + model.leftLeg.yRot, knees);
			leadElbow.knees = knees * 2.0F;
		}
		float upper = t.pitch + t.curl;
		float[] lower = times(times(turnZ(t.side), turnY(t.twist)), turnX(t.pitch));
		float[] top = times(times(turnZ(t.side), turnY(t.twist)), turnX(upper));
		// the hips, and from them up the lower back, then the upper back, to the neck
		float hipY = TORSO + sink + HIP_TUCK, half = TORSO * 0.5F;
		float neckX = -(lower[1] + top[1]) * half, neckY = hipY - (lower[4] + top[4]) * half, neckZ = -(lower[7] + top[7]) * half;
		model.body.x += neckX;
		model.body.y += neckY;
		model.body.z += neckZ;
		model.body.xRot += upper;
		model.body.yRot += t.twist;
		model.body.zRot += t.side;
		model.head.x += neckX;
		model.head.y += neckY;
		model.head.z += neckZ;
		model.head.zRot += t.headSide;
		for (ModelPart arm : new ModelPart[] {model.rightArm, model.leftArm}) {
			float[] guard = times(turnZ(t.evadeSide), turnY(arm == t.punching ? t.guardTwist * t.ride : t.guardTwist));
			// the shoulder, where the stance put it relative to the neck, carried by the upper back
			float x = arm.x, y = arm.y, z = arm.z;
			arm.x = neckX + top[0] * x + top[1] * y + top[2] * z;
			arm.y = neckY + top[3] * x + top[4] * y + top[5] * z;
			arm.z = neckZ + top[6] * x + top[7] * y + top[8] * z;
			float[] m = times(guard, times(times(turnZ(arm.zRot), turnY(arm.yRot)), turnX(arm.xRot)));
			if (t.evadeSide != 0.0F || t.guardTwist != 0.0F) {
				arm.yRot = -(float) Math.asin(Mth.clamp(m[6], -1.0F, 1.0F));
				arm.xRot = (float) Math.atan2(m[7], m[8]);
				arm.zRot = (float) Math.atan2(m[3], m[0]);
			}
			seat(arm, arm == model.rightArm, m, top, neckX, neckY, neckZ);
		}
	}

	/**
	 * (Jung: "no air between body portions") Keeps an upper arm against the side of the chest. A box arm turned in
	 * or out about its shoulder swings the back of its inside face off the chest; this finds how far the corners of
	 * that face that are beside the chest stand off it, and moves the shoulder in by {@link #SHOULDER_SEAT} of that.
	 * {@code m}: the arm's turn; {@code top}: the upper back's; the neck's place.
	 */
	private static void seat(ModelPart arm, boolean right, float[] m, float[] top, float neckX, float neckY, float neckZ) {
		// the inside face of the upper arm (the part's own box: x from -3 to 1 on the right arm, -1 to 3 on the left;
		// y from 2 above the shoulder down to the elbow; z from -2 to 2), and the chest's side, from the neck
		float inside = right ? 1.0F : -1.0F, chest = right ? -CHEST_SIDE : CHEST_SIDE, off = 0.0F;
		for (float cy : new float[] {-2.0F, ELBOW}) {
			for (float cz : new float[] {-2.0F, 2.0F}) {
				float wx = arm.x - neckX + m[0] * inside + m[1] * cy + m[2] * cz;
				float wy = arm.y - neckY + m[3] * inside + m[4] * cy + m[5] * cz;
				float wz = arm.z - neckZ + m[6] * inside + m[7] * cy + m[8] * cz;
				float bx = top[0] * wx + top[3] * wy + top[6] * wz;
				float by = top[1] * wx + top[4] * wy + top[7] * wz;
				float bz = top[2] * wx + top[5] * wy + top[8] * wz;
				if (Math.abs(bz) <= CHEST_DEPTH && by >= 0.0F && by <= TORSO) {
					off = Math.max(off, right ? chest - bx : bx - chest);
				}
			}
		}
		float in = SHOULDER_SEAT * off * (right ? 1.0F : -1.0F);
		arm.x += top[0] * in;
		arm.y += top[3] * in;
		arm.z += top[6] * in;
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
		TRUNK.pitch += crouch - hunch * 0.5F;
		TRUNK.curl += hunch;
		return -hunch;
	}

	/**
	 * The Bastion's shoulders rolled forward round the chest ({@link #BASTION_LEAD_ROLL}, {@link #BASTION_REAR_ROLL},
	 * pixels): the arms stay against the body's sides, a little further forward, so the lead forearm can lie across the
	 * belly and the rear glove reach the chin past a body turned side on. The lead upper arm also swings in across the
	 * body by {@code adduct} (radians).
	 */
	private static void rollShoulders(ModelPart lead, ModelPart rear, boolean leadRight, float adduct) {
		lead.z -= BASTION_LEAD_ROLL;
		rear.z -= BASTION_REAR_ROLL;
		// the lead upper arm swung in across the front of the body, so its forearm can reach across the belly
		lead.zRot += leadRight ? -adduct : adduct;
	}

	/** Tips the body and head to the side ({@code amount} radians; positive is the boxer's left). */
	private static void lean(HumanoidModel<?> model, float amount) {
		TRUNK.side += amount;
		TRUNK.headSide += amount * 0.6F;
	}

}
