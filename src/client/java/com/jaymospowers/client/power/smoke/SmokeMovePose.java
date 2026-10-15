package com.jaymospowers.client.power.smoke;

import com.jaymospowers.client.power.wind.WindPose;
import com.jaymospowers.power.smoke.SmokeJet;
import com.jaymospowers.power.smoke.SmokeMoves;
import com.jaymospowers.power.smoke.SmokeVapour;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * A pose for every Smoke move, on everyone's screen (from the synced {@link SmokeMoves#MOVE}, {@link SmokeJet#JETTING}
 * and {@link SmokeVapour#FORM}). Arms and head only: the legs are smoke in Vapour Form, and the rest is left to the walk.
 * <ul>
 * <li>Vapour Form hovering: arms loose and out, drifting; they give way to Wind's cruise pose as speed picks up.</li>
 * <li>R (switched on): fists to the chest, then flung down and out.</li>
 * <li>G Smokescreen: both hands out low in front, palms pushing, breathing with the pour.</li>
 * <li>V Smoke Bomb: an overhand throw (wind up behind the head, whip through, follow down).</li>
 * <li>B Smother: the hand reaches along the look and clenches, shaking harder the longer it holds; the other at the chest.</li>
 * <li>X Smoke Jet: both arms straight along the look, hands together, juddering.</li>
 * <li>Sneak + X Smoke Step: re-forms hunched with arms wrapped round, then unfolds wide.</li>
 * <li>Z Smoke Sight: two fingers to the temple.</li>
 * <li>C Pyroclasm: arms drawn down and back, trembling as it gathers; released, both sweep forward and down.</li>
 * <li>C Harness the Sky: both arms up, drawing the sky down; tapped, one arm thrown up then down at the spot.</li>
 * <li>Sneak + C feeding a storm: one arm up stirring, the other pointing where the tornado goes.</li>
 * <li>C Ash Storm: both arms flung up and wide, head back.</li>
 * </ul>
 */
public final class SmokeMovePose {
	public static final RenderStateDataKey<Pose> KEY = RenderStateDataKey.create(() -> "jaymospowers:smoke_move");

	/** {@code kind}: a {@link SmokeMoves} kind or 0; {@code t}: ticks into it; {@code hover}: 0-1 Vapour hover; {@code jet}: jetting. */
	public record Pose(int kind, float t, float hover, boolean jet, float time) {
	}

	/** Ticks a held move takes to come up. */
	static final float EASE_IN = 4.0F;
	/** Pyroclasm's gather at full (ticks), as the server's. */
	static final float GATHER_FULL = 60.0F;
	/** Smother's grip at its tightest (ticks). */
	static final float SMOTHER_FULL = 60.0F;

	private SmokeMovePose() {
	}

	/** From the avatar renderer's extractRenderState. */
	static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!(entity instanceof Player player) || minecraft.level == null || player.isSleeping() || player.isPassenger() || SmokeVapour.wholly(player)) {
			state.setData(KEY, null);
			return;
		}
		float now = minecraft.level.getGameTime() + partialTicks;
		SmokeMoves.Move move = SmokeMoves.get(player);
		int kind = 0;
		float t = 0.0F;
		if (move != null) {
			t = now - move.start();
			int length = SmokeMoves.length(move.kind());
			if (t >= 0.0F && (length == 0 || t < length)) {
				kind = move.kind();
			}
		}
		float hover = SmokeVapour.active(player) ? 1.0F - WindPose.amount(player, partialTicks) : 0.0F;
		boolean jet = SmokeJet.isActive(player);
		state.setData(KEY, kind == 0 && hover <= 0.001F && !jet ? null : new Pose(kind, t, hover, jet, now));
	}

	/** After a humanoid model animates. */
	public static void applyModel(Model<?> model, Object state) {
		if (!(state instanceof AvatarRenderState avatar) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		Pose pose = avatar.getData(KEY);
		if (pose == null) {
			return;
		}
		boolean leftMain = avatar.mainArm == HumanoidArm.LEFT;
		ModelPart main = leftMain ? h.leftArm : h.rightArm;
		ModelPart off = leftMain ? h.rightArm : h.leftArm;
		// +1 for the right arm: zRot times this is out from the body, yRot times minus this is in across it
		float mainOut = leftMain ? -1.0F : 1.0F;
		float offOut = -mainOut;
		float time = pose.time();
		if (pose.hover() > 0.001F) {
			hover(h, time, pose.hover());
		}
		float t = pose.t();
		switch (pose.kind()) {
			case SmokeMoves.ARM -> arm(h, t);
			case SmokeMoves.POUR -> pour(h, t);
			case SmokeMoves.BOMB -> bomb(h, main, off, mainOut, offOut, t);
			case SmokeMoves.SMOTHER -> smother(h, main, off, mainOut, offOut, t);
			case SmokeMoves.STEP -> step(h, t);
			case SmokeMoves.SIGHT -> sight(main, mainOut, t);
			case SmokeMoves.GATHER -> gather(h, t);
			case SmokeMoves.PYRO -> pyro(h, t);
			case SmokeMoves.SKY -> sky(h, t);
			case SmokeMoves.RAIN -> rain(h, main, mainOut, t);
			case SmokeMoves.FEED -> feed(h, main, off, mainOut, offOut, t);
			case SmokeMoves.ASH -> ash(h, t);
			default -> {
			}
		}
		if (pose.jet()) {
			jet(h, time);
		}
	}

	// ---------------------------------------------------------------- the poses

	/** Vapour Form hovering: arms loose, a little out and forward, drifting on the smoke. */
	private static void hover(HumanoidModel<?> h, float time, float amount) {
		float drift = Mth.sin(time * 0.08F);
		both(h, -0.35F + drift * 0.12F, 0.0F, 0.45F + Mth.sin(time * 0.1F + 1.0F) * 0.1F, amount);
	}

	/** R switched on: fists in to the chest, then flung down and out as the smoke takes. */
	private static void arm(HumanoidModel<?> h, float t) {
		float in = smooth(t / 4.0F);
		float out = smooth((t - 4.0F) / 4.0F);
		float fade = 1.0F - smooth((t - 8.0F) / 4.0F);
		float x = Mth.lerp(out, -1.3F, 0.15F);
		float y = Mth.lerp(out, -0.7F, 0.0F);
		float z = Mth.lerp(out, 0.0F, 0.75F);
		both(h, x, y, z, in * fade);
	}

	/** G Smokescreen: both hands out low in front, palms pushing, breathing with the pour. */
	private static void pour(HumanoidModel<?> h, float t) {
		float breath = Mth.sin(t * 0.18F);
		both(h, -0.95F + breath * 0.15F, -0.15F, 0.35F + breath * 0.08F, smooth(t / EASE_IN));
		h.head.xRot += 0.15F * smooth(t / EASE_IN);
	}

	/** V Smoke Bomb: wind up behind the head, whip through, follow down. */
	private static void bomb(HumanoidModel<?> h, ModelPart main, ModelPart off, float mainOut, float offOut, float t) {
		float x;
		if (t < 4.0F) {
			x = Mth.lerp(smooth(t / 4.0F), 0.0F, -2.9F);
		} else if (t < 7.0F) {
			x = Mth.lerp(smooth((t - 4.0F) / 3.0F), -2.9F, -0.5F);
		} else {
			x = Mth.lerp(smooth((t - 7.0F) / 5.0F), -0.5F, 0.0F);
		}
		float amount = 1.0F - smooth((t - 9.0F) / 3.0F);
		set(main, x, -mainOut * 0.15F, mainOut * 0.1F, amount);
		// the other arm out ahead for balance through the throw
		float balance = smooth(t / 4.0F) * amount;
		set(off, -0.9F, offOut * 0.2F, offOut * 0.25F, balance);
	}

	/** B Smother: the hand along the look, clenching harder and shaking; the other hand at the chest. */
	private static void smother(HumanoidModel<?> h, ModelPart main, ModelPart off, float mainOut, float offOut, float t) {
		float in = smooth(t / EASE_IN);
		float grip = Mth.clamp(t / SMOTHER_FULL, 0.0F, 1.0F);
		float shake = Mth.sin(t * 2.7F) * 0.03F * (0.3F + grip);
		// squeezing: the arm draws back a little and the wrist bends in
		set(main, h.head.xRot - Mth.HALF_PI + shake + grip * 0.2F, h.head.yRot - mainOut * 0.05F, mainOut * (0.05F + grip * 0.1F), in);
		set(off, -1.1F, -offOut * 0.7F, 0.0F, in);
	}

	/** X Smoke Jet: both arms along the look, hands together, juddering with the jet. */
	private static void jet(HumanoidModel<?> h, float time) {
		float judder = Mth.sin(time * 3.1F) * 0.035F;
		set(h.rightArm, h.head.xRot - Mth.HALF_PI + judder, h.head.yRot - 0.3F, 0.0F, 1.0F);
		set(h.leftArm, h.head.xRot - Mth.HALF_PI - judder, h.head.yRot + 0.3F, 0.0F, 1.0F);
	}

	/** Sneak + X Smoke Step: re-forms hunched with arms wrapped round, then unfolds wide. */
	private static void step(HumanoidModel<?> h, float t) {
		float open = smooth((t - 3.0F) / 7.0F);
		float fade = 1.0F - smooth((t - 11.0F) / 5.0F);
		float x = Mth.lerp(open, -1.1F, -0.25F);
		float y = Mth.lerp(open, -0.9F, 0.0F);
		float z = Mth.lerp(open, 0.0F, 1.1F);
		both(h, x, y, z, fade);
		h.head.xRot += 0.5F * (1.0F - open) * fade;
	}

	/** Z Smoke Sight: two fingers to the temple, held a moment. */
	private static void sight(ModelPart main, float mainOut, float t) {
		float amount = smooth(t / 4.0F) * (1.0F - smooth((t - 13.0F) / 5.0F));
		set(main, -2.5F, -mainOut * 0.55F, -mainOut * 0.15F, amount);
	}

	/** C Pyroclasm gathering: arms drawn down and back, fists trembling harder as it fills. */
	private static void gather(HumanoidModel<?> h, float t) {
		float charge = Mth.clamp(t / GATHER_FULL, 0.0F, 1.0F);
		float shake = Mth.sin(t * 3.3F) * 0.06F * charge;
		both(h, 0.45F + charge * 0.3F + shake, 0.0F, 0.3F + charge * 0.15F - shake, smooth(t / EASE_IN));
		h.head.xRot += 0.3F * charge;
	}

	/** C Pyroclasm released: both arms sweep from behind to forward and down, held, then let go. */
	private static void pyro(HumanoidModel<?> h, float t) {
		float sweep = smooth(t / 3.0F);
		float fade = 1.0F - smooth((t - 10.0F) / 6.0F);
		both(h, Mth.lerp(sweep, 0.75F, -1.15F), 0.0F, Mth.lerp(sweep, 0.45F, 0.2F), fade);
		h.head.xRot += 0.2F * fade;
	}

	/** C held, Harness the Sky: both arms up high, palms drawing the sky down in slow pulls. */
	private static void sky(HumanoidModel<?> h, float t) {
		float pull = Mth.sin(t * 0.14F);
		both(h, -2.8F + pull * 0.2F, Mth.sin(t * 0.07F) * 0.15F, 0.35F + pull * 0.08F, smooth(t / (EASE_IN * 2.0F)));
	}

	/** C tapped, a raincloud: one arm thrown up, then brought down to point at the spot. */
	private static void rain(HumanoidModel<?> h, ModelPart main, float mainOut, float t) {
		float up = smooth(t / 4.0F);
		float down = smooth((t - 6.0F) / 4.0F);
		float fade = 1.0F - smooth((t - 13.0F) / 5.0F);
		float x = Mth.lerp(down, -3.0F, h.head.xRot - Mth.HALF_PI);
		float y = Mth.lerp(down, 0.0F, h.head.yRot);
		set(main, x, y, mainOut * 0.1F * (1.0F - down), up * fade);
	}

	/** Sneak + C held, feeding a storm: one arm up stirring the sky, the other pointing where the tornado goes. */
	private static void feed(HumanoidModel<?> h, ModelPart main, ModelPart off, float mainOut, float offOut, float t) {
		float in = smooth(t / (EASE_IN * 2.0F));
		float a = t * 0.25F;
		set(off, -2.9F + Mth.sin(a) * 0.15F, Mth.cos(a) * 0.3F, offOut * (0.2F + Mth.sin(a) * 0.1F), in);
		set(main, h.head.xRot - Mth.HALF_PI, h.head.yRot, 0.0F, in);
	}

	/** C tapped, Ash Storm: both arms flung up and wide, head back, held, then let down. */
	private static void ash(HumanoidModel<?> h, float t) {
		float up = smooth(t / 5.0F);
		float fade = 1.0F - smooth((t - 22.0F) / 8.0F);
		both(h, -2.6F, 0.0F, 0.9F, up * fade);
		h.head.xRot -= 0.4F * up * fade;
	}

	// ---------------------------------------------------------------- helpers

	/** Both arms mirrored, as given for the right arm: {@code y} negative turns it in across the body, {@code z} positive out from it. */
	private static void both(HumanoidModel<?> h, float x, float y, float z, float amount) {
		set(h.rightArm, x, y, z, amount);
		set(h.leftArm, x, -y, -z, amount);
	}

	private static void set(ModelPart part, float xRot, float yRot, float zRot, float amount) {
		if (amount <= 0.0F) {
			return;
		}
		part.xRot = Mth.lerp(amount, part.xRot, xRot);
		part.yRot = Mth.lerp(amount, part.yRot, yRot);
		part.zRot = Mth.lerp(amount, part.zRot, zRot);
	}

	private static float smooth(float x) {
		x = Mth.clamp(x, 0.0F, 1.0F);
		return x * x * (3.0F - 2.0F * x);
	}
}
