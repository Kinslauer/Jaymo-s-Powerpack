package com.jaymospowers.client.backpack;

import com.jaymospowers.backpack.Backpack;
import com.jaymospowers.backpack.BackpackNet;
import com.jaymospowers.client.JaymosPowersClient;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * The backpack on the client: its key (` by default: open; sneak: take it off), the worn backpack drawn on every
 * player's back ({@link Layer}), and the set-down one ({@link BackpackRenderer}).
 * <p>
 * Worn, it is laid on the body part as the body model holds it when the layers are drawn, so it follows whatever moved
 * the body: the vanilla walk, Fresh Animations (Entity Model Features animates the body before the layers), our own
 * poses, crouching, swimming. On top of that it moves on its own ({@link Sway}): it hangs from the shoulders on a
 * spring, swinging out as you start, stop, jump and fall, rocking side to side with each step, and its flap bounces.
 */
public final class BackpackClient {
	/** The worn backpack's sway on this player ({swing, roll, flap, bob}), or null if they wear none. */
	static final RenderStateDataKey<float[]> KEY = RenderStateDataKey.create(() -> "jaymospowers:backpack");
	/** How far the bag sits off the back (pixels), and how much further over a chestplate. */
	static final float OFF_BACK = 2.2F;
	static final float OVER_ARMOUR = 1.0F;

	private static KeyMapping key;
	private static final Map<Avatar, Sway> SWAYS = new WeakHashMap<>();

	private BackpackClient() {
	}

	public static void init() {
		key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.backpack", InputConstants.KEY_GRAVE, JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			while (key.consumeClick()) {
				if (minecraft.player != null && minecraft.getConnection() != null && minecraft.gui.screen() == null) {
					ClientPlayNetworking.send(new BackpackNet.Key(minecraft.player.isShiftKeyDown()));
				}
			}
		});
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Layer(avatar));
			}
		});
		BlockEntityRenderers.register(Backpack.ENTITY, BackpackRenderer::new);
	}

	/** From the avatar renderer's extractRenderState: the worn backpack's sway, and no cape under it. */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		if (!(entity instanceof Player player) || !Backpack.wearing(player)) {
			state.setData(KEY, null);
			return;
		}
		Sway sway;
		synchronized (SWAYS) {
			sway = SWAYS.computeIfAbsent(entity, e -> new Sway());
		}
		sway.update(entity.getPosition(partialTicks), Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot), state.ageInTicks);
		float step = state.walkAnimationPos * 0.6662F;
		float stride = Mth.clamp(state.walkAnimationSpeed, 0.0F, 1.0F);
		float bob = Math.abs(Mth.sin(step)) * stride;
		state.setData(KEY, new float[] {
			sway.swing + 0.05F * stride,
			sway.roll + Sway.STEP_ROLL * Mth.sin(step) * stride,
			-Mth.clamp(Sway.FLAP_BOUNCE * bob + Sway.FLAP_LIFT * Math.abs(sway.swingSpeed), 0.0F, Sway.FLAP_MAX),
			Sway.BOB * bob});
		state.showCape = false;
	}

	/**
	 * A worn backpack hanging from the shoulders: a damped spring each way. It swings out (bottom away from the back)
	 * when you speed up or fall, presses in when you land or stop, and rolls when you turn or sidestep.
	 */
	static final class Sway {
		/** Spring stiffness and damping (per tick²/tick), and how hard each push moves it. */
		static final float STIFFNESS = 0.22F;
		static final float DAMPING = 0.55F;
		static final float PUSH_FORWARD = 2.4F;
		static final float PUSH_FALL = 1.6F;
		static final float PUSH_SIDE = 2.0F;
		static final float DRAG = 0.6F;
		/** Its limits (radians): it can't swing into the back, nor fly up over the head. */
		static final float SWING_IN = -0.12F, SWING_OUT = 0.9F, ROLL_MAX = 0.35F;
		/** Each step's rock, the flap's bounce and how much a swing lifts it, and the bag's bob (pixels). */
		static final float STEP_ROLL = 0.07F;
		static final float FLAP_BOUNCE = 0.35F, FLAP_LIFT = 2.5F, FLAP_MAX = 0.7F;
		static final float BOB = 0.45F;

		float swing, swingSpeed, roll, rollSpeed;
		private float age = Float.NaN;
		private Vec3 pos = Vec3.ZERO;
		private double forward, side, up;

		void update(Vec3 now, float bodyYaw, float ageInTicks) {
			if (Float.isNaN(this.age) || ageInTicks < this.age || ageInTicks - this.age > 10.0F) {
				this.age = ageInTicks;
				this.pos = now;
				return;
			}
			float dt = ageInTicks - this.age;
			if (dt <= 0.0F) {
				return;
			}
			this.age = ageInTicks;
			Vec3 v = now.subtract(this.pos).scale(1.0 / dt);
			this.pos = now;
			float yaw = bodyYaw * Mth.DEG_TO_RAD;
			double forward = -Mth.sin(yaw) * v.x + Mth.cos(yaw) * v.z;
			double side = Mth.cos(yaw) * v.x + Mth.sin(yaw) * v.z;
			double accelForward = (forward - this.forward) / dt, accelSide = (side - this.side) / dt, accelUp = (v.y - this.up) / dt;
			this.forward = forward;
			this.side = side;
			this.up = v.y;
			// bounded, so a teleport or a fling doesn't throw it over the head
			float pushSwing = (float)Mth.clamp(PUSH_FORWARD * accelForward - PUSH_FALL * accelUp + DRAG * Math.max(0.0, forward) - 0.5 * Math.min(0.0, v.y), -1.0, 1.0);
			float pushRoll = (float)Mth.clamp(PUSH_SIDE * accelSide, -1.0, 1.0);
			for (float left = dt; left > 0.0F; left -= 1.0F) {
				float h = Math.min(1.0F, left);
				this.swingSpeed += (STIFFNESS * (pushSwing - this.swing) - DAMPING * this.swingSpeed) * h;
				this.swing = Mth.clamp(this.swing + this.swingSpeed * h, SWING_IN, SWING_OUT);
				this.rollSpeed += (STIFFNESS * (pushRoll - this.roll) - DAMPING * this.rollSpeed) * h;
				this.roll = Mth.clamp(this.roll + this.rollSpeed * h, -ROLL_MAX, ROLL_MAX);
			}
		}
	}

	/**
	 * Submits the bag with its top at ({@code x}, {@code y}, {@code z}) pixels, swung by {@code swing} (bottom away from
	 * the back) and {@code roll}, and its flap lifted by {@code flap} (negative). The worn and set-down backpack alike.
	 */
	static void submitBag(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, float x, float y, float z, float swing, float roll, float flap) {
		poseStack.pushPose();
		poseStack.translate(x / 16.0F, y / 16.0F, z / 16.0F);
		poseStack.rotate(new Quaternionf().rotationZYX(roll, 0.0F, swing));
		collector.submitModelPart(BackpackModel.bag(), poseStack, RenderTypes.entityCutout(BackpackModel.TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY, null);
		poseStack.rotate(new Quaternionf().rotationX(flap));
		collector.submitModelPart(BackpackModel.flap(), poseStack, RenderTypes.entityCutout(BackpackModel.TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY, null);
		poseStack.popPose();
	}

	/** Draws a worn backpack on the body as it is posed now. */
	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			float[] sway = state.getData(KEY);
			if (sway == null || state.isInvisible) {
				return;
			}
			PlayerModel body = this.getParentModel();
			boolean armour = !state.chestEquipment.isEmpty() && !state.chestEquipment.has(DataComponents.GLIDER);
			poseStack.pushPose();
			body.root().translateAndRotate(poseStack);
			body.body.translateAndRotate(poseStack);
			collector.submitModelPart(BackpackModel.straps(), poseStack, RenderTypes.entityCutout(BackpackModel.TEXTURE), lightCoords,
				OverlayTexture.NO_OVERLAY, null);
			submitBag(poseStack, collector, lightCoords, 0.0F, 1.0F + sway[3], OFF_BACK + (armour ? OVER_ARMOUR : 0.0F), sway[0], sway[1], sway[2]);
			poseStack.popPose();
		}
	}
}
