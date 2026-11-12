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
 * <li><b>The stances move</b>, and each gives its style away: the Arrow Slit sways a little from side to side behind
 * a high, tight guard; the Long Guard holds its lead hand out and paws the air with it; the Bastion stands southpaw
 * (right side leading), the lead arm low across the body, and shrugs its lead shoulder now and then.</li>
 * <li><b>Blocks</b> (right click): the Arrow Slit's hands close in tight by the head; the Long Guard crosses its
 * forearms ahead; the Bastion rounds its lead shoulder up and tucks the rear hand in.</li>
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
 * The model has no elbows, so each is the nearest a straight arm gets. Set by numbers, without having been seen on a
 * model: expect to tune the knobs below.
 */
public final class BoxingPoses {
	// ================================================ tuning knobs =================================================
	// An arm's pose is three numbers: its pitch (radians; 0 hanging, -1.57 straight ahead, further is higher), how far
	// it is turned in across the body (radians; negative is out to the side), and how far down the shoulder sits (pixels).
	/** The Arrow Slit's guard (both hands), its sway (radians, and radians a tick), and its block. */
	static final float SLIT_PITCH = -2.25F, SLIT_IN = 0.75F, SLIT_SWAY = 0.12F, SLIT_SWAY_RATE = 0.14F, SLIT_BLOCK_PITCH = -2.4F, SLIT_BLOCK_IN = 0.85F;
	/** The Arrow Slit's forward crouch and tucked chin (radians), standing and behind its block. */
	static final float SLIT_CROUCH = 0.2F, SLIT_CHIN = 0.12F, SLIT_BLOCK_CROUCH = 0.3F;
	/** The Long Guard's lead hand out ahead and its pawing (up and down, side to side; radians and radians a tick), its rear hand, and its crossed block. */
	static final float LONG_LEAD_PITCH = -1.57F, LONG_LEAD_IN = 0.05F, LONG_PAW = 0.06F, LONG_PAW_RATE = 0.3F, LONG_PAW_SIDE = 0.06F, LONG_PAW_SIDE_RATE = 0.17F;
	static final float LONG_REAR_PITCH = -2.1F, LONG_REAR_IN = 0.7F, LONG_BLOCK_PITCH = -1.5F, LONG_BLOCK_IN = 0.4F;
	/** The Long Guard's lead shoulder turned a little forward and its upright, slightly backward lean (radians). */
	static final float LONG_BLADE = 0.25F, LONG_LEAN = -0.06F;
	/** The Bastion's lead arm low across the body and its rear hand by the chin; the shrug (every this many ticks, this long, this high in pixels); its block. */
	static final float BASTION_LEAD_PITCH = -0.6F, BASTION_LEAD_IN = 1.2F, BASTION_REAR_PITCH = -2.1F, BASTION_REAR_IN = 0.8F;
	/** The Bastion side on (its lead shoulder turned forward, radians), that shoulder held up (pixels), the weight back, the head tipped to the rear shoulder and the chin tucked. */
	static final float BASTION_BLADE = 0.45F, BASTION_SHOULDER = 1.0F, BASTION_LEAN = -0.08F, BASTION_HEAD_TILT = 0.12F, BASTION_CHIN = 0.1F;
	/** Its slow rock of the weight, back and forth (radians, radians a tick), and how much further side on its block stands. */
	static final float BASTION_ROCK = 0.03F, BASTION_ROCK_RATE = 0.08F, BASTION_BLOCK_BLADE = 0.65F;
	static final float SHRUG_EVERY = 90.0F, SHRUG_TICKS = 12.0F, SHRUG_UP = 1.0F;
	static final float BASTION_BLOCK_LEAD_PITCH = -1.0F, BASTION_BLOCK_LEAD_IN = 1.25F, BASTION_BLOCK_UP = 1.6F, BASTION_BLOCK_REAR_PITCH = -2.15F, BASTION_BLOCK_REAR_IN = 0.7F;
	/** A straight punch at full reach; a hook's height and its sweep from out to in; an uppercut from low to high; the body's turn into a rear-hand punch. */
	static final float STRAIGHT_PITCH = -1.57F, HOOK_PITCH = -1.45F, HOOK_FROM = -0.6F, HOOK_TO = 1.0F, UPPERCUT_FROM = -0.4F, UPPERCUT_TO = -2.4F, UPPERCUT_IN = 0.3F;
	static final float PUNCH_TURN = 0.3F;
	/** An evade's pose lasts this long (ticks): a slip's lean to the side, a bob's duck, a pull's lean back, a shoulder roll's turn and lift. */
	static final float MOVE_TICKS = 8.0F, SLIP_LEAN = 0.3F, BOB_DUCK = 0.5F, PULL_LEAN = -0.3F, ROLL_TURN = 0.6F, ROLL_UP = 1.0F;
	/** A shoulder roll's sway back and its tip of the head away from the blow (radians). */
	static final float ROLL_LEAN = -0.25F, ROLL_HEAD = 0.15F;
	/** A model's shoulders are this far from its middle, and its hips this far below its neck (pixels). */
	static final float SHOULDER_OUT = 5.0F, TORSO = 12.0F;
	/** Gassed: the hands dropped, the shoulders' heave (pixels, radians a tick), the slump. */
	static final float GASSED_PITCH = -0.45F, GASSED_IN = 0.15F, HEAVE = 0.6F, HEAVE_RATE = 0.3F, SLUMP = 0.18F;
	// ================================================================================================================

	/** A boxer, as their render state carries them. {@code moveAge}: ticks since the evade began. */
	public record Look(Boxing.Style style, int spent, Boxing.@Nullable Punch punch, BoxingEvades.@Nullable Move move, float moveAge, boolean blocking) {
	}

	public static final RenderStateDataKey<Look> KEY = RenderStateDataKey.create(() -> "jaymospowers:boxing_look");

	private BoxingPoses() {
	}

	/** From the avatar renderer's extractRenderState (through FangGauntletsClient): the boxer, if {@code entity} is one. */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
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
		if (look.spent() == Boxing.GASSED && !swinging) {
			// spent: the hands drop and the shoulders heave
			float heave = Mth.sin(time * HEAVE_RATE) * HEAVE;
			set(lead, leadRight, GASSED_PITCH, GASSED_IN);
			set(rear, !leadRight, GASSED_PITCH, GASSED_IN);
			lead.y += heave;
			rear.y += heave;
			model.body.xRot += SLUMP;
			model.head.xRot += SLUMP;
			return true;
		}
		if (look.blocking()) {
			switch (look.style()) {
				case ARROW_SLIT -> {
					set(lead, leadRight, SLIT_BLOCK_PITCH, SLIT_BLOCK_IN);
					set(rear, !leadRight, SLIT_BLOCK_PITCH, SLIT_BLOCK_IN);
					bend(model, SLIT_BLOCK_CROUCH);
					model.head.xRot += SLIT_CHIN;
				}
				case LONG_GUARD -> {
					set(lead, leadRight, LONG_BLOCK_PITCH, LONG_BLOCK_IN);
					set(rear, !leadRight, LONG_BLOCK_PITCH, LONG_BLOCK_IN);
					bend(model, LONG_LEAN);
				}
				case BASTION -> {
					set(lead, leadRight, BASTION_BLOCK_LEAD_PITCH, BASTION_BLOCK_LEAD_IN);
					lead.y -= BASTION_BLOCK_UP;
					set(rear, !leadRight, BASTION_BLOCK_REAR_PITCH, BASTION_BLOCK_REAR_IN);
					turn += BASTION_BLOCK_BLADE;
					bend(model, BASTION_LEAN);
					shellHead(model, leadRight);
				}
			}
		} else {
			turn += stance(model, look.style(), lead, rear, leadRight, time);
		}
		if (swinging && !look.blocking()) {
			turn += punch(look.punch() == null ? Boxing.Punch.JAB : look.punch(), lead, rear, leadRight, Mth.clamp(state.swingAnimation, 0.0F, 1.0F));
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
		return true;
	}

	/** The style's stance when nothing else is happening: where the hands sit, and the small movement that never stops. Returns how far the lead shoulder is turned forward. */
	private static float stance(HumanoidModel<?> model, Boxing.Style style, ModelPart lead, ModelPart rear, boolean leadRight, float time) {
		switch (style) {
			case ARROW_SLIT -> {
				float sway = Mth.sin(time * SLIT_SWAY_RATE) * SLIT_SWAY;
				set(lead, leadRight, SLIT_PITCH, SLIT_IN);
				set(rear, !leadRight, SLIT_PITCH, SLIT_IN);
				lean(model, sway);
				lead.zRot += sway;
				rear.zRot += sway;
				bend(model, SLIT_CROUCH);
				model.head.xRot += SLIT_CHIN;
				return 0.0F;
			}
			case LONG_GUARD -> {
				set(lead, leadRight, LONG_LEAD_PITCH + Mth.sin(time * LONG_PAW_RATE) * LONG_PAW, LONG_LEAD_IN + Mth.cos(time * LONG_PAW_SIDE_RATE) * LONG_PAW_SIDE);
				set(rear, !leadRight, LONG_REAR_PITCH, LONG_REAR_IN);
				bend(model, LONG_LEAN);
				return LONG_BLADE;
			}
			case BASTION -> {
				set(lead, leadRight, BASTION_LEAD_PITCH, BASTION_LEAD_IN);
				set(rear, !leadRight, BASTION_REAR_PITCH, BASTION_REAR_IN);
				lead.y -= BASTION_SHOULDER;
				float phase = time % SHRUG_EVERY / SHRUG_TICKS;
				if (phase < 1.0F) {
					lead.y -= Mth.sin(phase * Mth.PI) * SHRUG_UP;
				}
				bend(model, BASTION_LEAN + Mth.sin(time * BASTION_ROCK_RATE) * BASTION_ROCK);
				shellHead(model, leadRight);
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
	 * {@code swing}: 0 as the punch starts, 1 as it ends. The arm that throws it goes out and comes home; the other keeps
	 * its place. Returns how far the punch turns the lead shoulder forward (negative: the rear one comes through).
	 */
	private static float punch(Boxing.Punch punch, ModelPart lead, ModelPart rear, boolean leadRight, float swing) {
		float out = Mth.sin(swing * Mth.PI);
		switch (punch) {
			case JAB -> reach(lead, leadRight, STRAIGHT_PITCH, 0.0F, out);
			case CROSS, STRAIGHT, PULL_COUNTER -> {
				reach(rear, !leadRight, STRAIGHT_PITCH, 0.0F, out);
				// the rear shoulder comes through
				return -PUNCH_TURN * out;
			}
			case LEAD_HOOK -> set(lead, leadRight, HOOK_PITCH, Mth.lerp(swing, HOOK_FROM, HOOK_TO));
			case REAR_HOOK -> set(rear, !leadRight, HOOK_PITCH, Mth.lerp(swing, HOOK_FROM, HOOK_TO));
			case UPPERCUT -> set(rear, !leadRight, Mth.lerp(swing, UPPERCUT_FROM, UPPERCUT_TO), UPPERCUT_IN);
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

	/** Tips the body and head to the side ({@code amount} radians; positive is the boxer's left). */
	private static void lean(HumanoidModel<?> model, float amount) {
		model.body.zRot += amount;
		model.head.zRot += amount * 0.6F;
	}
}
