package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.NightGifts;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * A vampire's bat wings ({@link NightGifts#batWings}), spread while it glides with no elytra on.
 * <p>
 * (0.7.609, Andy: "whatever wings those are look awful") They used to be the elytra's two slabs in leather, blown up 1.35
 * times and stuck on the vanilla body: from behind, a pair of grey boards hiding the whole player. Now they're bat wings:
 * each an arm bone out from the shoulder blade and a long hand bone past the wrist, a thin membrane hung from both down
 * toward the legs, its trailing edge scalloped between the finger bones, a hooked thumb claw at the wrist
 * ({@code tools/gen_bat_wings.py}). The membrane lies across the back, so gliding face down it lies flat on the air.
 * They're a {@link WornModel}, riding the body MCA, Fresh Animations or our own poses give it, and they move: they
 * unfold out of the back when the glide starts, ripple as it goes, and sweep back the faster it gets.
 */
final class BatWings extends RenderLayer<AvatarRenderState, PlayerModel> {
	static final RenderStateDataKey<Boolean> SPREAD = RenderStateDataKey.create(() -> "jaymoskingdoms:bat_wings");
	private static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/vampire/bat_wings.png");

	/** Where each wing roots on the back (model pixels: out from the spine, down from the neck, behind the back). */
	static final float ROOT_X = 1.5F, ROOT_Y = 1.5F, ROOT_Z = 2.3F;
	/** The arm bone's and hand bone's length, and how deep their membranes hang (model pixels; the texture is laid out to these). */
	static final int ARM = 9, HAND = 12, ARM_DEPTH = 13, HAND_DEPTH = 14;
	/** Spread: the arm's lift, the hand's droop past the wrist, and how far both are swept back. */
	static final float LIFT = 0.45F, DROOP = 0.25F, SWEEP = 0.15F;
	/** Gliding: the ripple's speed and depth, and how far the wings sweep back at speed (from {@link #FAST_FROM} to {@link #FAST_FULL} blocks a tick). */
	static final float RIPPLE_RATE = 0.35F, RIPPLE = 0.07F, FAST_SWEEP = 0.55F, FAST_FROM = 0.5F, FAST_FULL = 1.5F;
	/** Unfolding: ticks to open, and the fold they open from (swept back along the back, the hand folded back along the arm). */
	static final float OPEN_TICKS = 6.0F, FOLDED_SWEEP = 1.35F, FOLDED_HAND = 2.6F;
	/** Ticks without being seen gliding before the wings fold away and must unfold again. */
	static final float FORGET_TICKS = 5.0F;

	/** Per entity id: the tick its wings started opening, and the tick they were last seen out. */
	private static final Map<Integer, float[]> OPENED = new HashMap<>();

	private final Model model;

	BatWings(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
		super(renderer);
		this.model = new Model(this::getParentModel);
	}

	/** Called from the render-state mixin. */
	static Boolean spread(LivingEntity entity) {
		return entity instanceof Player p && NightGifts.onBatWings(p) ? Boolean.TRUE : null;
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
		if (!Boolean.TRUE.equals(state.getData(SPREAD)) || state.isInvisible) {
			return;
		}
		float now = state.ageInTicks;
		float[] seen = OPENED.get(state.id);
		if (seen == null || now - seen[1] > FORGET_TICKS || now < seen[1]) {
			if (OPENED.size() > 64) {
				OPENED.clear();
			}
			seen = new float[] {now, now};
			OPENED.put(state.id, seen);
		}
		seen[1] = now;
		this.model.open = Mth.clamp((now - seen[0]) / OPEN_TICKS, 0.0F, 1.0F);
		net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
		net.minecraft.world.entity.Entity who = level == null ? null : level.getEntity(state.id);
		this.model.speed = who == null ? 0.0F : (float)who.getDeltaMovement().length();
		collector.order(1).submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY, -1,
			null, state.outlineColor);
	}

	/** A humanoid with nothing on it but the wings, so they ride the body through every pose. */
	static final class Model extends WornModel {
		private final ModelPart rightWing, leftWing, rightHand, leftHand;
		float open = 1.0F, speed;

		Model(java.util.function.Supplier<net.minecraft.client.model.HumanoidModel<?>> wearer) {
			super(build(), wearer);
			this.rightWing = this.body.getChild("right_wing");
			this.leftWing = this.body.getChild("left_wing");
			this.rightHand = this.rightWing.getChild("right_hand");
			this.leftHand = this.leftWing.getChild("left_hand");
		}

		/**
		 * The texture (64x64, {@code tools/gen_bat_wings.py}): the arm's membrane at 0,0 (front ARM x ARM_DEPTH, back beside
		 * it), the hand's at 0,16, the arm bone at 0,40, the hand bone at 0,44, the thumb claw at 0,48.
		 */
		private static ModelPart build() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO).addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
			root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
			root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
			// right wing out along -x, left (mirrored) along +x; each membrane a flat sheet hanging down (+y) from its bone
			PartDefinition right = body.addOrReplaceChild("right_wing", CubeListBuilder.create()
				.addBox("bone", -ARM, -0.5F, -0.5F, ARM, 1, 1, 0, 40)
				.addBox("skin", -ARM, 0.0F, 0.0F, ARM, ARM_DEPTH, 0, 0, 0), PartPose.offset(-ROOT_X, ROOT_Y, ROOT_Z));
			right.addOrReplaceChild("right_hand", CubeListBuilder.create()
				.addBox("bone", -HAND, -0.5F, -0.5F, HAND, 1, 1, 0, 44)
				.addBox("skin", -HAND, 0.0F, 0.0F, HAND, HAND_DEPTH, 0, 0, 16)
				.addBox("thumb", -0.5F, -2.5F, -0.5F, 1, 2, 1, 0, 48), PartPose.offset(-ARM, 0.0F, 0.0F));
			PartDefinition left = body.addOrReplaceChild("left_wing", CubeListBuilder.create().mirror()
				.addBox("bone", 0.0F, -0.5F, -0.5F, ARM, 1, 1, 0, 40)
				.addBox("skin", 0.0F, 0.0F, 0.0F, ARM, ARM_DEPTH, 0, 0, 0), PartPose.offset(ROOT_X, ROOT_Y, ROOT_Z));
			left.addOrReplaceChild("left_hand", CubeListBuilder.create().mirror()
				.addBox("bone", 0.0F, -0.5F, -0.5F, HAND, 1, 1, 0, 44)
				.addBox("skin", 0.0F, 0.0F, 0.0F, HAND, HAND_DEPTH, 0, 0, 16)
				.addBox("thumb", -0.5F, -2.5F, -0.5F, 1, 2, 1, 0, 48), PartPose.offset(ARM, 0.0F, 0.0F));
			return LayerDefinition.create(mesh, 64, 64).bakeRoot();
		}

		@Override
		public void setupAnim(HumanoidRenderState state) {
			super.setupAnim(state);
			float t = state.ageInTicks;
			float open = smooth(this.open);
			float fast = smooth(Mth.clamp((this.speed - FAST_FROM) / (FAST_FULL - FAST_FROM), 0.0F, 1.0F));
			// a ripple running out along the wing, the hand a beat behind the arm
			float ripple = Mth.sin(t * RIPPLE_RATE) * RIPPLE * (1.0F - 0.5F * fast);
			float handRipple = Mth.sin(t * RIPPLE_RATE - 1.2F) * RIPPLE * 1.5F * (1.0F - 0.5F * fast);
			float sweep = Mth.lerp(open, FOLDED_SWEEP, SWEEP + FAST_SWEEP * fast);
			float lift = (LIFT + ripple) * open;
			float droop = -(DROOP + handRipple) * open;
			float fold = FOLDED_HAND * (1.0F - open);
			this.rightWing.yRot = sweep;
			this.leftWing.yRot = -sweep;
			this.rightWing.zRot = lift;
			this.leftWing.zRot = -lift;
			this.rightHand.zRot = droop;
			this.leftHand.zRot = -droop;
			// folded, the hand lies back along the arm (toward +z, behind)
			this.rightHand.yRot = fold;
			this.leftHand.yRot = -fold;
			float grow = 0.55F + 0.45F * open;
			for (ModelPart wing : new ModelPart[] {this.rightWing, this.leftWing}) {
				wing.xScale *= grow;
				wing.yScale *= grow;
				wing.zScale *= grow;
			}
		}
	}

	private static float smooth(float x) {
		return x * x * (3.0F - 2.0F * x);
	}
}
