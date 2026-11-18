package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Lean;
import com.jaymoskingdoms.PowersCompat;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * The client's half of leaning ({@link Lean}): the two keys (Q and E, only while sneaking: without Shift Q still drops and E
 * still opens the inventory), what the server is told, and how far each player has leaned just now, eased so the body and the
 * view swing out and back. The camera ({@code CameraLeanMixin}) and every player's model ({@code AvatarLeanMixin}) read it.
 * <p>
 * The local player's lean follows the keys at once; everyone else's follows the attachment the server syncs.
 * <p>
 * Andy: "the character currently bends at the torso instead of using the leg to support that far of a bend". The lean is
 * carried by the legs, as in a shooter's corner peek: the hips slide out {@link #HIP_SHIFT} and sink {@link #HIP_DROP}, the
 * lean-side foot steps out {@link #STEP_OUT} and its knee bends under the weight, the far leg stays straight from where its
 * foot was, and only the body above the hips tilts ({@link Lean#TILT}), the head kept nearer level ({@link #HEAD_LEVEL}).
 * The knee is Jaymo's Powers' {@code KneeBend}; without Powers the leg is drawn straight.
 * <p>
 * Andy: "there needs to be animations from sneaking and crouching, crouching giving you the deepest lean out". From Powers'
 * low crouch (its knees already bent, or down on one knee when still) the hips go further out ({@link #CROUCH_HIP_SHIFT}),
 * the body tilts further ({@link Lean#CROUCH_TILT}) and the eye reaches {@link Lean#CROUCH_REACH}; the legs are swung out
 * sideways from the crouch's own bend. Going between the two eases like the lean itself.
 */
public final class LeanClient {
	/** How fast the lean comes and goes (a share of the full lean a tick): a quarter second to full. */
	static final float STEP = 0.2F;
	/** The camera stops this far short of a wall it leans into (blocks). */
	public static final double CAMERA_GAP = 0.25;
	/**
	 * At a full lean (pixels of the player model): the hips slide this far out and sink this far, and the lean-side foot steps
	 * this far out. With {@link Lean#TILT} above the hips, the eye ends up about {@link Lean#REACH} out.
	 */
	static final float HIP_SHIFT = 5.0F, HIP_DROP = 1.5F, STEP_OUT = 4.0F;
	/** The same from the low crouch: further out (pixels). */
	static final float CROUCH_HIP_SHIFT = 7.5F, CROUCH_HIP_DROP = 2.0F, CROUCH_STEP_OUT = 6.0F;
	/** The head takes back this share of the body's tilt, so it stays nearer level. */
	static final float HEAD_LEVEL = 0.5F;
	/** The player model: legs this far either side of the middle and this long, the hips this far down the model (pixels). */
	static final float HIP_HALF = 1.9F, LEG = 12.0F, HIP_Y = 12.0F;
	/** A pixel of the player model in blocks (players are drawn at 0.9375), and where its origin sits above the feet (blocks). */
	static final float PX = 0.9375F / 16.0F, MODEL_TOP = 1.501F;

	private static KeyMapping left;
	private static KeyMapping right;
	/** The lean each player has (entity id: before and now, -1 full left to 1 full right; then before and now, 0 sneaking to 1 crouched low). */
	private static final Map<Integer, float[]> AMOUNTS = new HashMap<>();
	/** The lean a player's render state was extracted with: {amount, how low}. */
	private static final Map<EntityRenderState, float[]> STATES = new WeakHashMap<>();
	/** What the server was last told. */
	private static int sent;
	/** Jaymo's Powers' {@code KneeBend.bend(ModelPart, float)}, if it's there. */
	private static MethodHandle kneeBend;
	private static boolean lookedForKnee;

	private LeanClient() {
	}

	public static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(JaymosKingdoms.id("lean"));
		left = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.lean_left", InputConstants.KEY_Q, category));
		right = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymoskingdoms.lean_right", InputConstants.KEY_E, category));
		ClientTickEvents.START_CLIENT_TICK.register(LeanClient::claimKeys);
		ClientTickEvents.END_CLIENT_TICK.register(LeanClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			AMOUNTS.clear();
			STATES.clear();
			sent = 0;
		});
	}

	/** Sneaking, a lean key press is the lean's: the drop and the inventory don't hear it (before the game reads its keys). */
	private static void claimKeys(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.gui.screen() != null || !player.isShiftKeyDown()) {
			return;
		}
		boolean used = left.isDown() || right.isDown();
		while (left.consumeClick()) {
			used = true;
		}
		while (right.consumeClick()) {
			used = true;
		}
		if (used) {
			while (minecraft.options.keyDrop.consumeClick()) {
				// (swallowed)
			}
			while (minecraft.options.keyInventory.consumeClick()) {
				// (swallowed)
			}
		}
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer me = minecraft.player;
		if (me == null || minecraft.level == null) {
			return;
		}
		int want = 0;
		if (minecraft.gui.screen() == null && Lean.can(me)) {
			want = (right.isDown() ? 1 : 0) - (left.isDown() ? 1 : 0);
		}
		if (want != sent && ClientPlayNetworking.canSend(Lean.Want.TYPE)) {
			ClientPlayNetworking.send(new Lean.Want(want));
			sent = want;
		}
		for (Player player : minecraft.level.players()) {
			float[] a = AMOUNTS.computeIfAbsent(player.getId(), id -> new float[4]);
			a[0] = a[1];
			a[1] += Mth.clamp((player == me ? want : Lean.dir(player)) - a[1], -STEP, STEP);
			a[2] = a[3];
			a[3] += Mth.clamp((PowersCompat.lowStance(player) ? 1.0F : 0.0F) - a[3], -STEP, STEP);
		}
		if (minecraft.level.getGameTime() % 200L == 0L) {
			AMOUNTS.keySet().removeIf(id -> minecraft.level.getEntity(id) == null);
		}
	}

	/** How far {@code entity} has leaned: -1 full left to 1 full right, 0 upright. */
	public static float amount(Entity entity, float partialTicks) {
		float[] a = AMOUNTS.get(entity.getId());
		return a == null ? 0.0F : Mth.lerp(partialTicks, a[0], a[1]);
	}

	/** How far into the low crouch {@code entity}'s lean is: 0 sneaking, 1 crouched low. */
	static float low(Entity entity, float partialTicks) {
		float[] a = AMOUNTS.get(entity.getId());
		return a == null ? 0.0F : Mth.lerp(partialTicks, a[2], a[3]);
	}

	/** How far out {@code entity}'s eye goes at a full lean just now (blocks), eased between sneaking and the low crouch. */
	public static double reach(Entity entity, float partialTicks) {
		return Mth.lerp(low(entity, partialTicks), Lean.REACH, Lean.CROUCH_REACH);
	}

	/** Remembers a player's lean for their render state. */
	public static void extract(Entity entity, EntityRenderState state, float partialTicks) {
		float amount = amount(entity, partialTicks);
		if (amount == 0.0F) {
			STATES.remove(state);
		} else {
			STATES.put(state, new float[] {amount, low(entity, partialTicks)});
		}
	}

	/**
	 * Slides a leaning player's hips out and down and tilts the body above them (called as the model is set up, in the body's
	 * own frame: +x to the right, blocks). The legs are set back on the ground by {@link #pose}.
	 */
	public static void rotate(EntityRenderState state, PoseStack poseStack) {
		float[] lean = STATES.get(state);
		if (lean == null) {
			return;
		}
		float amount = lean[0], low = lean[1];
		float hip = (MODEL_TOP - HIP_Y / 16.0F) * 0.9375F;
		poseStack.translate(amount * Mth.lerp(low, HIP_SHIFT, CROUCH_HIP_SHIFT) * PX,
			hip - Math.abs(amount) * Mth.lerp(low, HIP_DROP, CROUCH_HIP_DROP) * PX, 0.0F);
		poseStack.rotateDegrees(Axis.ZP, -amount * tilt(low));
		poseStack.translate(0.0F, -hip, 0.0F);
	}

	/**
	 * Right after a leaning player's model is posed: each leg turned so its foot stays on the ground under the tilted body, the
	 * lean-side one stepped out and bent at the knee ({@link #bendKnee}), the far one straight; the head tilted back toward level.
	 */
	public static void pose(Model<?> model, Object state) {
		if (!(state instanceof EntityRenderState s) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		float[] lean = STATES.get(s);
		if (lean == null) {
			return;
		}
		float amount = lean[0], low = lean[1];
		float[] legs = legs(amount, low);
		float side = Math.signum(amount);
		ModelPart near = amount > 0.0F ? h.rightLeg : h.leftLeg;
		ModelPart far = amount > 0.0F ? h.leftLeg : h.rightLeg;
		// (+zRot swings a leg toward the model's right)
		near.zRot += side * legs[0];
		near.xRot -= legs[1] * 0.5F;
		far.zRot += side * legs[2];
		h.head.zRot += amount * tilt(low) * Mth.DEG_TO_RAD * HEAD_LEVEL;
	}

	/** Right before a leaning player's model is drawn: the lean-side knee bent (Powers' KneeBend, which straightens it after). */
	public static void bendKnee(Model<?> model, Object state) {
		if (!(state instanceof EntityRenderState s) || !(model instanceof HumanoidModel<?> h)) {
			return;
		}
		float[] lean = STATES.get(s);
		MethodHandle bend = lean == null ? null : kneeBend();
		if (bend == null) {
			return;
		}
		float knee = legs(lean[0], lean[1])[1];
		if (knee <= 0.0F) {
			return;
		}
		try {
			bend.invoke(lean[0] > 0.0F ? h.rightLeg : h.leftLeg, knee);
		} catch (Throwable e) {
			kneeBend = null;
			JaymosKingdoms.LOGGER.warn("Couldn't bend a leaning player's knee", e);
		}
	}

	/**
	 * The legs under a lean of {@code amount}, in the body's frame (radians, + toward the lean): the lean-side leg's turn and
	 * knee bend, then the far leg's turn. Worked out across the body, the sideways move out and the up and down: the lean-side
	 * foot stepped out from under its hip, the far foot straight down from its hip as far as a straight leg reaches. From the
	 * low crouch ({@code low} 1) the crouch has bent the knees already, so only the sideways turns are added (no knee).
	 */
	static float[] legs(float amount, float low) {
		float m = Math.abs(amount);
		float tilt = m * tilt(low) * Mth.DEG_TO_RAD;
		float c = Mth.cos(tilt), s = Mth.sin(tilt);
		float shift = m * Mth.lerp(low, HIP_SHIFT, CROUCH_HIP_SHIFT), drop = m * Mth.lerp(low, HIP_DROP, CROUCH_HIP_DROP);
		// lean side: the hip (out and down with the tilt) to the foot stepped out on the ground
		float hipOut = shift + HIP_HALF * c, hipUp = -drop - HIP_HALF * s;
		float across = HIP_HALF + m * Mth.lerp(low, STEP_OUT, CROUCH_STEP_OUT) - hipOut, down = hipUp + LEG;
		float reach = (float)Math.sqrt(across * across + down * down);
		float knee = reach < LEG ? 2.0F * (float)Math.acos(reach / LEG) : 0.0F;
		float leanTurn = (float)Math.atan2(across, down) + tilt;
		// far side: the hip (in and up with the tilt), the leg straight to the ground
		float farUp = -drop + HIP_HALF * s;
		float farDown = Math.min(LEG, farUp + LEG);
		float farAcross = (float)Math.sqrt(Math.max(0.0F, LEG * LEG - farDown * farDown));
		float farTurn = (float)Math.atan2(-farAcross, farDown) + tilt;
		return new float[] {leanTurn, knee * (1.0F - low), farTurn};
	}

	/** The tilt above the hips at a full lean (degrees), between sneaking and the low crouch. */
	static float tilt(float low) {
		return Mth.lerp(low, Lean.TILT, Lean.CROUCH_TILT);
	}

	private static MethodHandle kneeBend() {
		if (!lookedForKnee) {
			lookedForKnee = true;
			if (FabricLoader.getInstance().isModLoaded("jaymospowers")) {
				try {
					kneeBend = MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.client.KneeBend"), "bend",
						MethodType.methodType(void.class, ModelPart.class, float.class));
				} catch (ReflectiveOperationException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Couldn't find Jaymo's Powers' KneeBend (older Powers?)", e);
				}
			}
		}
		return kneeBend;
	}
}
