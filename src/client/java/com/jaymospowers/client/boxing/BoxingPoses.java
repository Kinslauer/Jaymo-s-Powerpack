package com.jaymospowers.client.boxing;

import com.jaymospowers.boxing.Boxing;
import com.jaymospowers.boxing.BoxingEvades;
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
 * All of it is set by numbers, without having been seen on a model: expect to tune the knobs below.
 */
public final class BoxingPoses {
	// ================================================ tuning knobs =================================================
	// An arm's pose is three numbers: its pitch (radians; 0 hanging, -1.57 straight ahead, further is higher), how far
	// it is turned in across the body (radians; negative is out to the side), and how far down the shoulder sits (pixels).
	/** The Arrow Slit's guard (both hands), its sway (radians, and radians a tick), and its block. */
	static final float SLIT_PITCH = -1.95F, SLIT_IN = 0.5F, SLIT_SWAY = 0.07F, SLIT_SWAY_RATE = 0.12F, SLIT_BLOCK_PITCH = -2.25F, SLIT_BLOCK_IN = 0.65F;
	/** The Long Guard's lead hand out ahead and its pawing (up and down, side to side; radians and radians a tick), its rear hand, and its crossed block. */
	static final float LONG_LEAD_PITCH = -1.5F, LONG_LEAD_IN = 0.15F, LONG_PAW = 0.1F, LONG_PAW_RATE = 0.3F, LONG_PAW_SIDE = 0.08F, LONG_PAW_SIDE_RATE = 0.17F;
	static final float LONG_REAR_PITCH = -1.85F, LONG_REAR_IN = 0.55F, LONG_BLOCK_PITCH = -1.55F, LONG_BLOCK_IN = 0.95F;
	/** The Bastion's lead arm low across the body and its rear hand by the chin; the shrug (every this many ticks, this long, this high in pixels); its block. */
	static final float BASTION_LEAD_PITCH = -0.75F, BASTION_LEAD_IN = 1.0F, BASTION_REAR_PITCH = -2.0F, BASTION_REAR_IN = 0.55F;
	static final float SHRUG_EVERY = 90.0F, SHRUG_TICKS = 12.0F, SHRUG_UP = 1.0F;
	static final float BASTION_BLOCK_LEAD_PITCH = -1.0F, BASTION_BLOCK_LEAD_IN = 1.25F, BASTION_BLOCK_UP = 0.8F, BASTION_BLOCK_REAR_PITCH = -2.15F, BASTION_BLOCK_REAR_IN = 0.7F;
	/** A straight punch at full reach; a hook's height and its sweep from out to in; an uppercut from low to high; the body's turn into a rear-hand punch. */
	static final float STRAIGHT_PITCH = -1.57F, HOOK_PITCH = -1.45F, HOOK_FROM = -0.6F, HOOK_TO = 1.0F, UPPERCUT_FROM = -0.4F, UPPERCUT_TO = -2.4F, UPPERCUT_IN = 0.3F;
	static final float PUNCH_TURN = 0.3F;
	/** An evade's pose lasts this long (ticks): a slip's lean to the side, a bob's duck, a pull's lean back, a shoulder roll's turn and lift. */
	static final float MOVE_TICKS = 8.0F, SLIP_LEAN = 0.3F, BOB_DUCK = 0.5F, PULL_LEAN = -0.3F, ROLL_TURN = 0.5F, ROLL_UP = 1.0F;
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
		if (!(entity instanceof Player player) || !Boxing.boxing(player)) {
			state.setData(KEY, null);
			return;
		}
		Boxing.Style style = Boxing.style(player);
		if (style == null) {
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
				}
				case LONG_GUARD -> {
					set(lead, leadRight, LONG_BLOCK_PITCH, LONG_BLOCK_IN);
					set(rear, !leadRight, LONG_BLOCK_PITCH, LONG_BLOCK_IN);
				}
				case BASTION -> {
					set(lead, leadRight, BASTION_BLOCK_LEAD_PITCH, BASTION_BLOCK_LEAD_IN);
					lead.y -= BASTION_BLOCK_UP;
					set(rear, !leadRight, BASTION_BLOCK_REAR_PITCH, BASTION_BLOCK_REAR_IN);
				}
			}
		} else {
			stance(model, look.style(), lead, rear, leadRight, time);
		}
		if (swinging && !look.blocking()) {
			punch(model, look.punch() == null ? Boxing.Punch.JAB : look.punch(), lead, rear, leadRight, Mth.clamp(state.swingAnimation, 0.0F, 1.0F));
		}
		if (look.move() != null) {
			// out and back over the evade's few ticks
			float amount = Mth.sin(Mth.clamp(look.moveAge() / MOVE_TICKS, 0.0F, 1.0F) * Mth.PI);
			switch (look.move()) {
				case SLIP_LEFT -> lean(model, SLIP_LEAN * amount);
				case SLIP_RIGHT -> lean(model, -SLIP_LEAN * amount);
				case BOB -> {
					model.body.xRot += BOB_DUCK * amount;
					model.head.xRot += BOB_DUCK * amount * 0.5F;
				}
				case PULL -> model.body.xRot += PULL_LEAN * amount;
				case ROLL -> {
					model.body.yRot += (leadRight ? -ROLL_TURN : ROLL_TURN) * amount;
					lead.y -= ROLL_UP * amount;
				}
			}
		}
		return true;
	}

	/** The style's stance when nothing else is happening: where the hands sit, and the small movement that never stops. */
	private static void stance(HumanoidModel<?> model, Boxing.Style style, ModelPart lead, ModelPart rear, boolean leadRight, float time) {
		switch (style) {
			case ARROW_SLIT -> {
				float sway = Mth.sin(time * SLIT_SWAY_RATE) * SLIT_SWAY;
				set(lead, leadRight, SLIT_PITCH, SLIT_IN);
				set(rear, !leadRight, SLIT_PITCH, SLIT_IN);
				lean(model, sway);
				lead.zRot += sway;
				rear.zRot += sway;
			}
			case LONG_GUARD -> {
				set(lead, leadRight, LONG_LEAD_PITCH + Mth.sin(time * LONG_PAW_RATE) * LONG_PAW, LONG_LEAD_IN + Mth.cos(time * LONG_PAW_SIDE_RATE) * LONG_PAW_SIDE);
				set(rear, !leadRight, LONG_REAR_PITCH, LONG_REAR_IN);
			}
			case BASTION -> {
				set(lead, leadRight, BASTION_LEAD_PITCH, BASTION_LEAD_IN);
				set(rear, !leadRight, BASTION_REAR_PITCH, BASTION_REAR_IN);
				float phase = time % SHRUG_EVERY / SHRUG_TICKS;
				if (phase < 1.0F) {
					lead.y -= Mth.sin(phase * Mth.PI) * SHRUG_UP;
				}
			}
		}
	}

	/** {@code swing}: 0 as the punch starts, 1 as it ends. The arm that throws it goes out and comes home; the other keeps its place. */
	private static void punch(HumanoidModel<?> model, Boxing.Punch punch, ModelPart lead, ModelPart rear, boolean leadRight, float swing) {
		float out = Mth.sin(swing * Mth.PI);
		switch (punch) {
			case JAB -> reach(lead, leadRight, STRAIGHT_PITCH, 0.0F, out);
			case CROSS, STRAIGHT, PULL_COUNTER -> {
				reach(rear, !leadRight, STRAIGHT_PITCH, 0.0F, out);
				// the rear shoulder comes through
				model.body.yRot += (leadRight ? -PUNCH_TURN : PUNCH_TURN) * out;
			}
			case LEAD_HOOK -> set(lead, leadRight, HOOK_PITCH, Mth.lerp(swing, HOOK_FROM, HOOK_TO));
			case REAR_HOOK -> set(rear, !leadRight, HOOK_PITCH, Mth.lerp(swing, HOOK_FROM, HOOK_TO));
			case UPPERCUT -> set(rear, !leadRight, Mth.lerp(swing, UPPERCUT_FROM, UPPERCUT_TO), UPPERCUT_IN);
		}
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

	/** Tips the body and head to the side ({@code amount} radians; positive is the boxer's left). */
	private static void lean(HumanoidModel<?> model, float amount) {
		model.body.zRot += amount;
		model.head.zRot += amount * 0.6F;
	}
}
