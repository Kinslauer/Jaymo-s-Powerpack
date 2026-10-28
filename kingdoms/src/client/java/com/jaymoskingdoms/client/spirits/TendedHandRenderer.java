package com.jaymoskingdoms.client.spirits;

import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.ManaAlchemy;
import com.jaymoskingdoms.client.ManaClient;
import com.jaymoskingdoms.spirits.Spirits;
import com.jaymoskingdoms.spirits.TendedHand;
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
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * (0.7.278) A hand left behind ({@link TendedHand}): the spirit's own forearm and hand from its arms ({@link SpiritRig}),
 * drawn alone in the aura's shells, hovering over its container. It hangs loose at rest, opens and points as it reaches for
 * something, closes round what it carries, and punches as a fist. Only the attuned (and those who read magic) see the hand;
 * what it carries everyone sees, floating along.
 */
public final class TendedHandRenderer extends EntityRenderer<TendedHand, TendedHandRenderer.State> {
	/** The hand's size by kind (Varro's gauntlet is big already). */
	static final float SIZE = 1.35F, VARRO_SIZE = 0.85F;
	/** Model pixels the elbow sits above the hand's middle. */
	static final float ELBOW_UP = 7.0F;
	/** How bright its shells are awake and resting. */
	static final float AWAKE_LIGHT = 0.85F, REST_LIGHT = 0.35F;
	/** Each frame, how far the hand eases toward its new shape. */
	static final float EASE = 0.2F;
	/** A punch: ticks it takes, and how far forward it lunges (model pixels). */
	static final float PUNCH_TICKS = 6.0F, PUNCH_REACH = 6.0F;
	/** The carried item: how far below the hand it hangs (blocks), and its size. */
	static final float ITEM_DROP = 0.25F, ITEM_SCALE = 0.6F;

	public static final class State extends EntityRenderState {
		int kind;
		int act;
		boolean awake, visible;
		float yRot, punch, tilt, curl, spread;
		final ItemStackRenderState item = new ItemStackRenderState();
	}

	/** Shape it has now, by entity id (tilt, curl, spread), eased toward each act's. */
	private final Map<Integer, float[]> shapes = new HashMap<>();
	private final ModelPart[][] hands = new ModelPart[Spirits.QUIET + 1][];
	private final SpiritRig.Limb[][] limbs = new SpiritRig.Limb[Spirits.QUIET + 1][];
	private final ItemModelResolver items;

	TendedHandRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.items = context.getItemModelResolver();
	}

	public static void register() {
		EntityRenderers.register(TendedHand.TYPE, TendedHandRenderer::new);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(TendedHand hand, State state, float partialTicks) {
		super.extractRenderState(hand, state, partialTicks);
		LocalPlayer viewer = Minecraft.getInstance().player;
		state.visible = viewer != null && (Mana.attuned(viewer) || ManaAlchemy.readsMagic(viewer));
		state.kind = Math.clamp(hand.kind(), 0, Spirits.QUIET);
		state.act = hand.act();
		state.awake = hand.awake();
		state.yRot = hand.getYRot(partialTicks);
		float since = hand.sinceBlow() + partialTicks;
		state.punch = since < PUNCH_TICKS ? Mth.sin(since / PUNCH_TICKS * Mth.PI) : 0.0F;
		// the shape each act asks for: how far the hand tips forward, how closed, how spread
		float tilt, curl, spread;
		switch (state.act) {
			case TendedHand.REACH -> {
				tilt = -1.4F;
				curl = 0.05F;
				spread = 1.0F;
			}
			case TendedHand.CARRY -> {
				tilt = -0.25F;
				curl = 0.75F;
				spread = 0.2F;
			}
			case TendedHand.STRIKE -> {
				tilt = -1.5F;
				curl = 1.0F;
				spread = 0.0F;
			}
			default -> {
				tilt = state.awake ? -0.35F : 0.0F;
				curl = 0.35F + 0.08F * Mth.sin(state.ageInTicks * 0.07F);
				spread = 0.4F;
			}
		}
		float[] now = this.shapes.computeIfAbsent(hand.getId(), k -> new float[] {tilt, curl, spread});
		now[0] = Mth.lerp(EASE, now[0], tilt);
		now[1] = Mth.lerp(EASE, now[1], curl);
		now[2] = Mth.lerp(EASE, now[2], spread);
		state.tilt = now[0];
		state.curl = now[1];
		state.spread = now[2];
		if (hand.isRemoved()) {
			this.shapes.remove(hand.getId());
		}
		this.items.updateForNonLiving(state.item, hand.held(), ItemDisplayContext.GROUND, hand);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (!state.item.isEmpty()) {
			poseStack.pushPose();
			poseStack.translate(0.0F, ITEM_DROP, 0.0F);
			poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
			state.item.submit(poseStack, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
		if (!state.visible) {
			super.submit(state, poseStack, collector, camera);
			return;
		}
		int kind = state.kind;
		this.bake(kind);
		float size = kind == Spirits.VARRO ? VARRO_SIZE : SIZE;
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.25F, 0.0F);
		poseStack.rotateDegrees(Axis.YP, 180.0F - state.yRot);
		poseStack.scale(-size, -size, size);
		int color = SpiritArms.mix(Spirits.of(kind).color());
		float breathe = 1.0F + ManaClient.BREATHE * Mth.sin(state.ageInTicks * ManaClient.BREATHE_SPEED);
		float rise = (state.ageInTicks * ManaClient.RISE * SpiritArms.ARM_FLOW) % 1.0F;
		float strength = (state.awake ? AWAKE_LIGHT : REST_LIGHT) * breathe;
		SpiritRig.Frame f = new SpiritRig.Frame();
		f.set(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, state.curl, state.spread, 0.0F);
		for (int i = 0; i < SpiritArms.ARM_SHELLS.length; i++) {
			ModelPart elbow = this.hands[kind][i];
			SpiritRig.Limb limb = this.limbs[kind][i];
			limb.pose(f, 1.0F);
			if (limb.weapons != null) {
				for (ModelPart weapon : limb.weapons) {
					weapon.visible = false;
				}
			}
			// the forearm and hand alone, its middle on the entity, tipped forward by the act, and a fist lunges
			elbow.x = 1.0F;
			elbow.y = -ELBOW_UP;
			elbow.z = -state.punch * PUNCH_REACH;
			elbow.xRot = state.tilt;
			elbow.yRot = 0.0F;
			elbow.zRot = 0.0F;
			float light = Mth.clamp(SpiritArms.ARM_SHELL_LIGHT[i] * strength, 0.0F, 1.0F);
			light = Mth.lerp(state.punch, light, 1.0F);
			collector.order(SpiritArms.ORDER + i).submitModelPart(elbow, poseStack,
				RenderTypes.energySwirl(ManaClient.TEXTURE, i * 0.29F + 0.13F, -rise * (1 + i)), state.lightCoords, OverlayTexture.NO_OVERLAY, null,
				ManaClient.tint(i == 0 ? SpiritArms.ghost(color) : color, light));
		}
		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/** Bakes {@code kind}'s forearm and hand, one for each of the aura's shells, the first time it is drawn. */
	private void bake(int kind) {
		if (this.hands[kind] != null) {
			return;
		}
		int shells = SpiritArms.ARM_SHELLS.length;
		this.hands[kind] = new ModelPart[shells];
		this.limbs[kind] = new SpiritRig.Limb[shells];
		for (int i = 0; i < shells; i++) {
			ModelPart arm = LayerDefinition.create(SpiritRig.mesh(kind, SpiritArms.ARM_SHELLS[i]), 64, 64).bakeRoot().getChild("right_arm");
			this.limbs[kind][i] = new SpiritRig.Limb(arm, kind);
			this.hands[kind][i] = arm.getChild("elbow");
		}
	}
}
