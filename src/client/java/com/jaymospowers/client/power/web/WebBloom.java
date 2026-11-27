package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.power.web.WebSymbioteBloom;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * The bloom on the big body's head: when the server says it is cornered, starving or panicking
 * ({@link WebSymbioteBloom#BLOOM}), the head splits into a crown of {@link #PETALS} petal tendrils. Each rises from a ring on
 * the skull's top, opens outward and curls at the tip, swaying, and folds away again when the bloom closes. Drawn from
 * {@link WebBigHead#submit} off the head's own pose. The art is our own: {@code tools/gen_symbiote_skin.py}.
 */
final class WebBloom {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_bloom.png");
	/** How the crown is: the petals round the skull, the ring's radius and height (pixels, in the head's own space), and each piece's length. */
	static final int PETALS = 8;
	static final float RING = 3.6F, RING_Y = -7.6F, RING_Z = 1.2F, STEM = 7.0F, TIP = 6.0F;
	/** How far a petal opens (radians): the stem leans out, and the tip curls further. */
	static final float LEAN = 0.85F, CURL = 0.7F, SWAY = 0.09F;
	/** The opening takes this many ticks (the closing is {@link WebSymbioteBloom#CLOSE}). */
	static final float OPEN_TICKS = 10.0F;
	/** How open the crown is (0-1), for the wearer's state; absent when it is shut. */
	static final RenderStateDataKey<Float> OPEN = RenderStateDataKey.create(() -> "jaymospowers:web_symbiote_bloom");

	private static Petals model;

	private WebBloom() {
	}

	/** From the extract: how open this entity's bloom is now, or null. */
	static Float open(Entity entity) {
		Long at = entity.getAttached(WebSymbioteBloom.BLOOM);
		if (at == null) {
			return null;
		}
		float now = entity.level().getGameTime() + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float open = at > 0L ? Mth.clamp((now - at) / OPEN_TICKS, 0.0F, 1.0F) : 1.0F - Mth.clamp((now + at) / WebSymbioteBloom.CLOSE, 0.0F, 1.0F);
		return open <= 0.01F ? null : open * open * (3.0F - 2.0F * open);
	}

	/** Draws the crown at the head's pose (the pose stack is already on the head). */
	static void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state) {
		Float open = state.getData(OPEN);
		if (open == null) {
			return;
		}
		if (model == null) {
			model = new Petals();
		}
		collector.order(1).submitModel(model, state, poseStack, RenderTypes.entityCutout(WebStrainLook.texture(TEXTURE, state)), lightCoords,
			LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor);
	}

	private static LayerDefinition mesh() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		for (int i = 0; i < PETALS; i++) {
			float angle = i * Mth.TWO_PI / PETALS;
			PartDefinition ring = root.addOrReplaceChild("ring" + i, CubeListBuilder.create(),
				PartPose.offsetAndRotation(0.0F, RING_Y, RING_Z, 0.0F, angle, 0.0F));
			PartDefinition stem = ring.addOrReplaceChild("stem" + i, CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -STEM, -1.0F, 2.0F, STEM, 2.0F),
				PartPose.offset(0.0F, 0.0F, -RING));
			stem.addOrReplaceChild("tip" + i, CubeListBuilder.create().texOffs(0, 8).addBox(-0.75F, -TIP, -0.75F, 1.5F, TIP, 1.5F),
				PartPose.offset(0.0F, -STEM, 0.0F));
		}
		return LayerDefinition.create(mesh, 16, 16);
	}

	/** The petals, opened by {@link #OPEN}. */
	private static final class Petals extends Model<AvatarRenderState> {
		private final ModelPart[] stems = new ModelPart[PETALS];
		private final ModelPart[] tips = new ModelPart[PETALS];

		Petals() {
			this(mesh().bakeRoot());
		}

		private Petals(ModelPart root) {
			super(root, RenderTypes::entityCutout);
			for (int i = 0; i < PETALS; i++) {
				this.stems[i] = root.getChild("ring" + i).getChild("stem" + i);
				this.tips[i] = this.stems[i].getChild("tip" + i);
			}
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			Float open = state.getData(OPEN);
			float o = open == null ? 0.0F : open;
			for (int i = 0; i < PETALS; i++) {
				float sway = Mth.sin(state.ageInTicks * 0.13F + i * 0.8F) * SWAY * o;
				// (a petal grows out of the head as it opens, longer than it is wide)
				this.stems[i].yScale = o;
				this.stems[i].xRot = LEAN * o + sway;
				this.stems[i].zRot = Mth.sin(state.ageInTicks * 0.09F + i * 1.3F) * SWAY * o;
				this.tips[i].xRot = CURL * o + sway * 1.5F;
			}
		}
	}
}
