package com.jaymoskingdoms.client.spirits;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.ManaAlchemy;
import com.jaymoskingdoms.client.ManaClient;
import com.jaymoskingdoms.spirits.SpiritHand;
import com.jaymoskingdoms.spirits.Spirits;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * (0.7.279) A spirit's floating hand ({@link SpiritHand}), for Far hand and Catch. Andy: "make the hand follow the wispy ki
 * glow texture". The spirit's own hand from its arms ({@link SpiritRig}), with only a stub of wrist, drawn as the ki glow is:
 * hollow shells of wisps scrolling along it, added like light, in the spirit's colour, with no solid core (the ki glow's own
 * texture, {@link #TEXTURE}). Open and pointing as it flies out or waits, closed as it grips. Only the attuned (and those who
 * read magic) see it.
 */
public final class SpiritHandRenderer extends EntityRenderer<SpiritHand, SpiritHandRenderer.State> {
	/** The ki glow's wisps (the same texture as Jaymo's Powers' ki glow). */
	static final Identifier TEXTURE = JaymosKingdoms.id("textures/entity/spirit_hand_glow.png");
	/** The shells, inside out: how far each sits off the hand (model pixels) and how bright it is (the ki glow's). */
	static final float[] SHELLS = {0.45F, 0.95F, 1.6F};
	static final float[] SHELL_LIGHT = {0.55F, 0.3F, 0.14F};
	/** How fast the wisps run, and the hand's slow breathing. */
	static final float RISE = 0.03F, BREATHE = 0.1F, BREATHE_SPEED = 0.2F;
	/** The hand's size by kind (Varro's gauntlet is big already). */
	static final float SIZE = 1.3F, VARRO_SIZE = 0.8F;
	/** Model pixels the wrist sits behind the hand's middle. */
	static final float WRIST_BACK = 3.0F;
	/** Each frame, how far the fingers ease toward open or closed. */
	static final float EASE = 0.35F;

	public static final class State extends EntityRenderState {
		int kind;
		boolean visible;
		float yRot, xRot, curl, fade;
	}

	/** How closed each hand is now, by entity id, eased toward open or closed. */
	private final Map<Integer, Float> curls = new HashMap<>();
	private final ModelPart[][] hands = new ModelPart[Spirits.QUIET + 1][];
	private final SpiritRig.Limb[][] limbs = new SpiritRig.Limb[Spirits.QUIET + 1][];

	SpiritHandRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	public static void register() {
		EntityRenderers.register(SpiritHand.TYPE, SpiritHandRenderer::new);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(SpiritHand hand, State state, float partialTicks) {
		super.extractRenderState(hand, state, partialTicks);
		LocalPlayer viewer = Minecraft.getInstance().player;
		state.visible = viewer != null && (Mana.attuned(viewer) || ManaAlchemy.readsMagic(viewer));
		state.kind = Math.clamp(hand.kind(), 0, Spirits.QUIET);
		state.yRot = hand.getYRot(partialTicks);
		state.xRot = hand.getXRot(partialTicks);
		state.fade = hand.fade();
		float want = hand.closed() ? 1.0F : 0.05F;
		float now = Mth.lerp(EASE, this.curls.getOrDefault(hand.getId(), want), want);
		if (hand.isRemoved()) {
			this.curls.remove(hand.getId());
		} else {
			this.curls.put(hand.getId(), now);
		}
		state.curl = now;
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.visible) {
			return;
		}
		int kind = state.kind;
		this.bake(kind);
		float size = kind == Spirits.VARRO ? VARRO_SIZE : SIZE;
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.2F, 0.0F);
		poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
		poseStack.scale(-size, -size, size);
		int color = SpiritArms.mix(Spirits.of(kind).color());
		float breathe = 1.0F + BREATHE * Mth.sin(state.ageInTicks * BREATHE_SPEED);
		float rise = (state.ageInTicks * RISE) % 1.0F;
		float alive = 1.0F - state.fade;
		SpiritRig.Frame f = new SpiritRig.Frame();
		f.set(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, state.curl, 1.0F - state.curl, 0.0F);
		for (int i = 0; i < SHELLS.length; i++) {
			ModelPart wrist = this.hands[kind][i];
			SpiritRig.Limb limb = this.limbs[kind][i];
			limb.pose(f, 1.0F);
			if (limb.weapons != null) {
				for (ModelPart weapon : limb.weapons) {
					weapon.visible = false;
				}
			}
			// the hand alone, fingers along its flight (model +y is the fingers' way; turned to -z, ahead), pitched as it flies
			wrist.x = 1.0F;
			wrist.y = 0.0F;
			wrist.z = WRIST_BACK;
			wrist.xRot = -Mth.HALF_PI + state.xRot * Mth.DEG_TO_RAD;
			wrist.yRot = 0.0F;
			wrist.zRot = 0.0F;
			float light = Mth.clamp(SHELL_LIGHT[i] * breathe * alive, 0.0F, 1.0F);
			collector.order(SpiritArms.ORDER + i).submitModelPart(wrist, poseStack,
				RenderTypes.energySwirl(TEXTURE, i * 0.31F, -rise * (1 + i)), state.lightCoords, OverlayTexture.NO_OVERLAY, null,
				ManaClient.tint(color, light));
		}
		poseStack.popPose();
	}

	/** Bakes {@code kind}'s hand, once for each shell, the first time it is drawn. */
	private void bake(int kind) {
		if (this.hands[kind] != null) {
			return;
		}
		this.hands[kind] = new ModelPart[SHELLS.length];
		this.limbs[kind] = new SpiritRig.Limb[SHELLS.length];
		for (int i = 0; i < SHELLS.length; i++) {
			ModelPart arm = LayerDefinition.create(SpiritRig.mesh(kind, SHELLS[i]), 64, 64).bakeRoot().getChild("right_arm");
			this.limbs[kind][i] = new SpiritRig.Limb(arm, kind);
			this.hands[kind][i] = arm.getChild("elbow").getChild("wrist");
		}
	}
}
