package com.jaymospowers.client.power.web;

import com.jaymospowers.JaymosPowers;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;

/**
 * The big body's head: the symbiote's head from Andy's own Symbiote mod (a heavy brow and a long muzzle over a hinged
 * jaw of fangs, a white-ringed eye on each side, {@code tools/gen_web_bighead.py}), put on our big body's neck in place of
 * the old blocky one ({@link WebHulk.Model} keeps its head masses hidden). The body stays ours. The head grows in with the
 * goo, last of all, and sinks back the same way ({@link WebHulk#flow}); its jaw is the big body's jaw ({@link WebHulk#jaw}:
 * hanging open a little and breathing, a snarl now and then, wider for a flick of the tongue). The long red tongue is the
 * original's own four segments, lying in the jaw and shot out of the mouth on the big body's flick timing
 * ({@link WebHulk#flick}: every {@code TONGUE_EVERY} ticks on each wearer's own beat, then a long pause, and a few licks just
 * after a meal), stretched out to {@code out} of its length with a ripple down it and its tip whipping, then drawn back in. The
 * eyes and the glint on the fangs glow in the dark. The plain suit and Consume's maw ({@link WebMouthLayer}) keep their own
 * heads (and the maw its own tongue; the head's tongue stays in while it is out).
 */
final class WebBigHead {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_bighead.png");
	private static final Identifier GLOW = JaymosPowers.id("textures/entity/web_symbiote_bighead_glow.png");
	/**
	 * Placing the head: the original's units are drawn {@code SCALE} of their size, {@code OFFSET_Y} and {@code OFFSET_Z}
	 * pixels from the head's pivot, so the chin sits where the old jaw's did and the back of the skull on the neck.
	 */
	static final float SCALE = 0.6F, OFFSET_Y = -1.7F, OFFSET_Z = 1.5F;
	/**
	 * The tongue: the jaw's lift is taken back out of it so it leaves the dropped jaw level ({@code TONGUE_LEAD} of the jaw's
	 * angle, and {@code TONGUE_REST} drooping), then each segment after the first ripples and sways down it, more toward the tip, and
	 * its tip whips in a flick: the big body's old tongue's ripple, sway, flutter and droop ({@link WebHulk#TONGUE_RIPPLE}...),
	 * {@code SEGMENT} times as much for a segment as for one of its little pieces.
	 */
	static final float TONGUE_LEAD = 0.7F, TONGUE_REST = 0.05F, SEGMENT = 1.0F;
	/** The first of the old head's masses to flow on: the new head grows in step with it. */
	private static final int HEAD_ORDER = headOrder();
	private static final int HEAD_PHASE = headPhase();

	private WebBigHead() {
	}

	private static int headPhase() {
		for (int i = 0; i < WebHulkMesh.PARTS.length; i++) {
			if ("head".equals(WebHulkMesh.PARTS[i][0])) {
				return i;
			}
		}
		return 0;
	}

	private static int headOrder() {
		return WebHulkMesh.ORDER[headPhase()];
	}

	/** Draws the head off the parent model's head (posed the way the maw's is), over the big body. */
	static void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, PlayerModel parent,
		Model<AvatarRenderState> head) {
		if (state.isInvisible || WebSymbioteClient.ownFirstPerson(state)) {
			return;
		}
		poseStack.pushPose();
		WebFeral.applyModel(parent, state);
		WebClimbPose.applyModel(parent, state);
		WebHeldGrabPose.applyModel(parent, state);
		WebExecutionPose.applyModel(parent, state);
		WebSuitGrowth.shift(parent, state);
		parent.head.translateAndRotate(poseStack);
		collector.order(1).submitModel(head, state, poseStack, RenderTypes.entityCutout(WebStrainLook.texture(TEXTURE, state)), lightCoords,
			LivingEntityRenderer.getOverlayCoords(state, 0.0F), state.outlineColor);
		collector.order(2).submitModel(head, state, poseStack, RenderTypes.eyes(GLOW), LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
			state.outlineColor);
		// (cornered, starving or panicking: the head splits into a crown of petals)
		WebBloom.submit(poseStack, collector, lightCoords, state);
		poseStack.popPose();
	}

	/** The head and its jaw: grown in with the goo, the jaw hung the way the big body hangs it. */
	static final class HeadModel extends Model<AvatarRenderState> {
		private final ModelPart jaw;
		/** The four tongue segments, each the child of the one before (the first in the jaw). */
		private final ModelPart[] tongue = new ModelPart[4];

		HeadModel() {
			this(WebBigHeadMesh.create().bakeRoot());
		}

		private HeadModel(ModelPart root) {
			super(root, RenderTypes::entityCutout);
			this.jaw = root.getChild("head").getChild("jaw");
			ModelPart segment = this.jaw;
			for (int i = 0; i < this.tongue.length; i++) {
				segment = segment.getChild("tongue" + i);
				this.tongue[i] = segment;
			}
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			super.setupAnim(state);
			float g = WebSuitGrowth.hulkBuilt(state);
			float flow = WebHulk.flow(HEAD_ORDER, WebHulk.flowed(g, WebSuitGrowth.peeledHulk(state)));
			float thick = WebHulk.thick(HEAD_ORDER, true, HEAD_PHASE, g, state.ageInTicks, flow);
			ModelPart root = this.root();
			root.visible = flow > 0.02F;
			root.xScale = SCALE * thick;
			root.yScale = SCALE * flow;
			root.zScale = SCALE * thick;
			root.y = OFFSET_Y;
			root.z = OFFSET_Z;
			float jawAngle = WebHulk.jaw(state);
			this.jaw.xRot = jawAngle;
			// (the maw has the head while it eats, roars or counters: it has its own tongue)
			boolean maw = state.getData(WebSymbioteClient.EATING) != null || state.getData(WebSymbioteClient.COUNTER_MAW) != null
				|| state.getData(WebSymbioteClient.ROAR) != null;
			float flick = WebHulk.flick(state);
			float out = maw ? 0.0F : WebHulk.flickOut(flick) * Mth.clamp((g - 0.85F) / 0.15F, 0.0F, 1.0F);
			this.tongue[0].visible = out > 0.02F;
			if (out > 0.02F) {
				this.tongueOut(state.ageInTicks, out, Mth.sin(flick * Mth.PI), jawAngle);
			}
		}

		/** Lays the tongue out {@code out} of its length (the whole chain stretches along the jaw), {@code whip} (0-1) through a flick. */
		private void tongueOut(float age, float out, float whip, float jawAngle) {
			this.tongue[0].zScale = out;
			this.tongue[0].xRot = TONGUE_REST - jawAngle * TONGUE_LEAD;
			for (int i = 1; i < this.tongue.length; i++) {
				float f = i / (float)(this.tongue.length - 1);
				this.tongue[i].xRot = SEGMENT * (WebHulk.TONGUE_DROOP * (1.0F - whip * 0.5F) + Mth.sin(age * 0.23F - i * 0.6F) * WebHulk.TONGUE_RIPPLE * f);
				this.tongue[i].yRot = SEGMENT * (Mth.sin(age * 0.13F - i * 0.45F) * WebHulk.TONGUE_SWAY * (0.3F + f) * 0.5F
					+ Mth.sin(age * 1.9F - i * 0.7F) * WebHulk.TONGUE_FLUTTER * f * whip);
			}
		}
	}
}
