package com.jaymospowers.client.power.ember;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.PoseBlend;
import com.jaymospowers.core.Powers;
import com.jaymospowers.power.ember.EmberKindle;
import com.jaymospowers.power.smoke.SmokePower;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;

/**
 * (0.14.0) Kindle's client half ({@link EmberKindle}): the held left mouse (asked through {@code RaceHoldClient}, which
 * decides when the hold is the race's), the heat bar over the crosshair (orange, white at full heat, like Smoke's pour
 * bar), and the right arm held out toward the flame, on everyone who kindles. The flame itself is the server's
 * particles, so everyone sees it.
 */
public final class EmberKindleClient {
	/** Ticks held before the flame starts (a quick click stays a punch), and how often a held button says so again. */
	static final int KINDLE_HOLD = 4, REFRESH = 5;
	/** Ticks after asking before the server's word counts: if the flame isn't running by then, it was put out (no food). */
	static final int GRACE = 10;
	/** The bar's width (pixels), how far over the crosshair, and its colours. */
	static final int BAR_WIDTH = 52, BAR_UP = 33, BAR_UP_SMOKE = 47;
	static final int HEAT_COLOR = 0xFF7A1A, WHITE_COLOR = 0xFFF4E0;
	public static final RenderStateDataKey<Boolean> KEY = RenderStateDataKey.create(() -> "jaymospowers:ember_kindle");
	/** (0.15.40) The heat (0-1) on everyone kindling, for the arm's tremble and the throw. */
	static final RenderStateDataKey<Float> HEAT = RenderStateDataKey.create(() -> "jaymospowers:ember_kindle_heat");

	// ---- (0.15.40) the flame arm's motion (seconds and radians; see applyModel)
	/** The shoulder drawn back before the push: how long, how far the arm (x) and the body turn away (hip yaw). */
	static final float COIL_TIME = 0.18F, COIL_ARM = 0.45F, COIL_TURN = 0.22F;
	/** Pushed out: the body turned in behind it (hip yaw, - is the right shoulder forward) and leaned in. */
	static final float PUSH_TURN = -0.32F, PUSH_LEAN = 0.06F;
	/** The free arm braced out to the side, and the stance (legs apart front to back, faded out while walking). */
	static final float BRACE_ARM = -0.45F, BRACE_OUT = 0.3F, STANCE = 0.28F, STANCE_WALK = 0.6F;
	/** The tremble at full heat (radians) and its beats (radians a second). */
	static final float TREMBLE = 0.035F, TREMBLE_BEAT = 47.0F, TREMBLE_BEAT_SIDE = 39.0F;
	/** A slow breath through the body while it's held. */
	static final float BREATH = 0.025F, BREATH_RATE = 2.3F;
	/** Heat at or over this when it's let go: the throw (the server throws a fireball then). */
	static final float THROW_HEAT = 0.99F;
	/** The throw's whip-over: up over the shoulder, then driven through and down; how long it plays (seconds). */
	static final float WHIP_UP_TIME = 0.08F, WHIP_TIME = 0.42F, WHIP_UP = -2.55F, WHIP_THROUGH = -0.9F, WHIP_TURN = -0.55F,
		WHIP_LEAN = 0.12F, WHIP_STEP = 0.35F;
	/** The springs a little snappier for the throw. */
	static final float WHIP_STIFFNESS = 1.4F;
	private static final PoseBlend.Channel BLEND = PoseBlend.channel("ember_kindle");

	private static int held;
	private static boolean on;
	/** The server put the flame out while it was still held: nothing more until the button is let go. */
	private static boolean out;

	private EmberKindleClient() {
	}

	public static void init() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("ember_heat"), EmberKindleClient::renderHud);
	}

	/**
	 * From {@code RaceHoldClient}'s tick: {@code down} is the race's left hold, held now by an Emberborn. Starts the
	 * flame after {@link #KINDLE_HOLD} ticks, says it's still held every {@link #REFRESH}, and lets go.
	 */
	public static void held(boolean down) {
		if (!down) {
			held = 0;
			out = false;
			if (on) {
				on = false;
				if (Minecraft.getInstance().getConnection() != null) {
					ClientPlayNetworking.send(new EmberKindle.Kindle(false));
				}
			}
			return;
		}
		held++;
		LocalPlayer player = Minecraft.getInstance().player;
		if (on && held >= KINDLE_HOLD + GRACE && player != null && !EmberKindle.kindling(player)) {
			// the server stopped it (too hungry, the hand filled): stand aside so the click digs again
			on = false;
			out = true;
		}
		if (out) {
			return;
		}
		if (!on && held >= KINDLE_HOLD) {
			on = true;
			ClientPlayNetworking.send(new EmberKindle.Kindle(true));
		} else if (on && held % REFRESH == 0) {
			ClientPlayNetworking.send(new EmberKindle.Kindle(true));
		}
	}

	/** Whether the local player's flame is running (vanilla's held-click digging and the hold combo stand aside). */
	public static boolean kindling() {
		return on;
	}

	private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || player.isSpectator()) {
			return;
		}
		float heat = EmberKindle.heat(player);
		if (heat <= 0.0F && !EmberKindle.kindling(player)) {
			return;
		}
		Font font = minecraft.font;
		boolean white = heat >= 1.0F;
		int color = white ? WHITE_COLOR : HEAT_COLOR;
		int cx = graphics.guiWidth() / 2;
		// above Smoke's own bar when they have that power too
		int y = graphics.guiHeight() / 2 - (Powers.has(player, SmokePower.INSTANCE) ? BAR_UP_SMOKE : BAR_UP);
		int x0 = cx - BAR_WIDTH / 2;
		graphics.fill(x0 - 1, y - 1, x0 + BAR_WIDTH + 1, y + 4, 0x90000000);
		graphics.fill(x0, y, x0 + Mth.ceil(BAR_WIDTH * Mth.clamp(heat, 0.0F, 1.0F)), y + 3, 0xFF000000 | color);
		Component label = Component.translatable(white ? "hud.jaymospowers.ember.white" : "hud.jaymospowers.ember.heat");
		graphics.centeredText(font, label.copy().withColor(color), cx, y - 11, 0xFFFFFFFF);
	}

	/** From the avatar renderer's extractRenderState (everyone): whether they're kindling. */
	public static void extract(Avatar entity, AvatarRenderState state) {
		boolean kindling = entity instanceof Player player && EmberKindle.kindling(player);
		state.setData(KEY, kindling);
		// the heat is kept a moment past the flame too: letting go at full heat is the throw
		state.setData(HEAT, entity instanceof Player player ? EmberKindle.heat(player) : 0.0F);
	}

	/**
	 * (0.15.40, was a straight arm switched on and off) The flame arm: the shoulder draws back with the body turned away,
	 * then the arm pushes out along the look with the body turned in behind it, the free arm braced out and the feet set
	 * apart; it trembles more as the heat builds. Let go at full heat, the arm whips up over the shoulder and through
	 * with the throw. It all eases in and out ({@link com.jaymospowers.client.PoseBlend}).
	 */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		boolean on = Boolean.TRUE.equals(avatar.getData(KEY)) && avatar.deathTime <= 0.0F;
		Float heatData = avatar.getData(HEAT);
		float heat = heatData == null ? 0.0F : Mth.clamp(heatData, 0.0F, 1.0F);
		PoseBlend.Track before = BLEND.peek(avatar.id);
		if (before != null && before.on() && !on && before.memoTime >= THROW_HEAT) {
			// let go at full heat: the throw (memo marks it, memoTime the heat it was thrown at)
			before.memo = Boolean.TRUE;
		}
		boolean throwing = before != null && Boolean.TRUE.equals(before.memo) && !on;
		PoseBlend.Track track = BLEND.track(avatar.id, on, throwing ? WHIP_TIME : 0.0F, 1.0F);
		if (track == null) {
			return;
		}
		if (on) {
			track.memo = null;
			track.memoTime = heat;
		}
		if (avatar.deathTime > 0.0F) {
			return;
		}
		float t = track.age();
		float time = avatar.ageInTicks / 20.0F;
		if (!on && !throwing) {
			// let go: the last of it eases back out
			track.apply(h, null);
			return;
		}
		PoseBlend.Frame f = new PoseBlend.Frame(h);
		float stance = STANCE * (1.0F - Mth.clamp(avatar.walkAnimationSpeed / STANCE_WALK, 0.0F, 1.0F));
		float aimX = -Mth.HALF_PI + avatar.xRot * Mth.DEG_TO_RAD;
		float aimY = f.vanilla(PoseBlend.HEAD + 1);
		float turn;
		if (throwing) {
			// the whip: up over the shoulder, then driven through and down, the body turning hard through it
			float through = PoseBlend.seg(t, WHIP_UP_TIME, WHIP_TIME * 0.7F);
			turn = Mth.lerp(through, PUSH_TURN, WHIP_TURN);
			f.set(PoseBlend.RIGHT_ARM, Mth.lerp(through, WHIP_UP, WHIP_THROUGH), aimY - turn, 0.0F);
			f.set(PoseBlend.LEFT_ARM, BRACE_ARM * 0.5F, 0.0F, -BRACE_OUT * 1.3F);
			f.set(PoseBlend.HIP_PITCH, PUSH_LEAN + WHIP_LEAN * through);
			f.set(PoseBlend.RIGHT_LEG, -WHIP_STEP * Math.max(stance / STANCE, 0.4F), 0.0F, 0.04F);
			f.set(PoseBlend.LEFT_LEG, WHIP_STEP * Math.max(stance / STANCE, 0.4F), 0.0F, -0.04F);
		} else if (t < COIL_TIME) {
			// the coil: the shoulder drawn back, the body turned away, the weight back
			turn = COIL_TURN;
			f.set(PoseBlend.RIGHT_ARM, COIL_ARM, -turn * 0.5F, 0.08F);
			f.set(PoseBlend.LEFT_ARM, -0.25F, 0.0F, -0.12F);
			f.set(PoseBlend.HIP_PITCH, -0.04F);
		} else {
			// pushed out along the look, trembling with the heat, the body turned in behind it
			float build = PoseBlend.smooth((t - COIL_TIME) / 1.5F);
			float shake = heat * (0.4F + 0.6F * build);
			turn = PUSH_TURN;
			f.set(PoseBlend.RIGHT_ARM, aimX, aimY - turn, 0.0F);
			f.shake(PoseBlend.RIGHT_ARM, Mth.sin(time * TREMBLE_BEAT) * TREMBLE * shake);
			f.shake(PoseBlend.RIGHT_ARM + 2, Mth.sin(time * TREMBLE_BEAT_SIDE) * TREMBLE * 0.85F * shake);
			f.set(PoseBlend.LEFT_ARM, BRACE_ARM, 0.0F, -BRACE_OUT);
			f.set(PoseBlend.HIP_PITCH, PUSH_LEAN + Mth.sin(time * BREATH_RATE) * BREATH);
			f.set(PoseBlend.RIGHT_LEG, -stance, 0.0F, 0.03F);
			f.set(PoseBlend.LEFT_LEG, stance, 0.0F, -0.03F);
			f.add(PoseBlend.HEAD, 0.08F * build);
		}
		f.set(PoseBlend.HIP_YAW, turn);
		// the head stays on the look (the bend turns and tips it with the body otherwise), and so does the pushed arm
		float lean = f.get(PoseBlend.HIP_PITCH);
		f.add(PoseBlend.HEAD + 1, -turn);
		f.add(PoseBlend.HEAD, -lean);
		if (!throwing && t >= COIL_TIME) {
			f.add(PoseBlend.RIGHT_ARM, -lean);
		}
		track.apply(h, f, throwing ? WHIP_STIFFNESS : 1.0F);
	}
}
