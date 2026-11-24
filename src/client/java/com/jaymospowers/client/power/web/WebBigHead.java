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

/**
 * The big body's head: the symbiote's head from Andy's own Symbiote mod (a heavy brow and a long muzzle over a hinged
 * jaw of fangs, a white-ringed eye on each side, {@code tools/gen_web_bighead.py}), put on our big body's neck in place of
 * the old blocky one ({@link WebHulk.Model} keeps its head masses hidden). The body stays ours. The head grows in with the
 * goo, last of all, and sinks back the same way ({@link WebHulk#flow}); its jaw is the big body's jaw ({@link WebHulk#jaw}:
 * hanging open a little and breathing, a snarl now and then, wider for a flick of the tongue); the tongue is the big body's
 * own ({@link WebHulk#submitTongue}), drawn from {@link #MOUTH_Y}/{@link #MOUTH_Z}. The eyes and the glint on the fangs
 * glow in the dark. The plain suit and Consume's maw ({@link WebMouthLayer}) keep their own heads.
 */
final class WebBigHead {
	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/web_symbiote_bighead.png");
	private static final Identifier GLOW = JaymosPowers.id("textures/entity/web_symbiote_bighead_glow.png");
	/**
	 * Placing the head: the original's units are drawn {@code SCALE} of their size, {@code OFFSET_Y} and {@code OFFSET_Z}
	 * pixels from the head's pivot, so the chin sits where the old jaw's did and the back of the skull on the neck.
	 */
	static final float SCALE = 0.6F, OFFSET_Y = -1.7F, OFFSET_Z = 1.5F;
	/** Where the mouth is in the head's frame (pixels), from the original's anchor: the tongue comes out of it. */
	static final float MOUTH_Y = OFFSET_Y + WebBigHeadMesh.MOUTH_Y * SCALE, MOUTH_Z = OFFSET_Z + WebBigHeadMesh.MOUTH_Z * SCALE;
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
		poseStack.popPose();
	}

	/** The head and its jaw: grown in with the goo, the jaw hung the way the big body hangs it. */
	static final class HeadModel extends Model<AvatarRenderState> {
		private final ModelPart jaw;

		HeadModel() {
			this(WebBigHeadMesh.create().bakeRoot());
		}

		private HeadModel(ModelPart root) {
			super(root, RenderTypes::entityCutout);
			this.jaw = root.getChild("head").getChild("jaw");
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
			this.jaw.xRot = WebHulk.jaw(state);
		}
	}
}
