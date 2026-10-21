package com.jaymoskingdoms.client.nether;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.nether.Hornkin;
import com.jaymoskingdoms.nether.Nether;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

/** Draws the Hornkin ({@link Nether}): one model, a skin for each kind, the brute half again as big, the warlord bigger still. */
public final class HornkinRenderer extends HumanoidMobRenderer<Hornkin, HornkinRenderer.State, HornkinModel> {
	/** How much bigger than a man each kind is drawn (in {@link Hornkin.Kind} order). */
	static final float[] SCALE = {1.1F, 1.05F, 1.45F, 1.1F, 1.75F};

	public static final class State extends HumanoidRenderState {
		Hornkin.Kind kind = Hornkin.Kind.WARRIOR;
		boolean charging, admiring, throwing, zombified;
	}

	private final Identifier texture;
	private final float scale;

	private HornkinRenderer(EntityRendererProvider.Context context, Hornkin.Kind kind) {
		super(context, new HornkinModel(HornkinModel.createBodyLayer().bakeRoot()), 0.5F * SCALE[kind.ordinal()]);
		this.texture = JaymosKingdoms.id("textures/entity/nether/" + kind.name().toLowerCase(java.util.Locale.ROOT) + ".png");
		this.scale = SCALE[kind.ordinal()];
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Hornkin hornkin, State state, float partialTicks) {
		super.extractRenderState(hornkin, state, partialTicks);
		state.kind = hornkin.kind();
		state.zombified = hornkin.kind() == Hornkin.Kind.ZOMBIFIED;
		state.charging = hornkin.charging();
		state.admiring = hornkin.admiring();
		state.throwing = hornkin.kind() == Hornkin.Kind.HURLER && hornkin.isAggressive();
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		poseStack.scale(this.scale, this.scale, this.scale);
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return this.texture;
	}

	private static void register(EntityType<Hornkin> type, Hornkin.Kind kind) {
		EntityRenderers.register(type, context -> new HornkinRenderer(context, kind));
	}

	public static void register() {
		register(Nether.HORNKIN, Hornkin.Kind.WARRIOR);
		register(Nether.HORNKIN_HURLER, Hornkin.Kind.HURLER);
		register(Nether.HORNKIN_BRUTE, Hornkin.Kind.BRUTE);
		register(Nether.ZOMBIFIED_HORNKIN, Hornkin.Kind.ZOMBIFIED);
		register(Nether.HORNKIN_WARLORD, Hornkin.Kind.WARLORD);
		EntityRenderers.register(Nether.MAGMA_BOMB, context -> new ThrownItemRenderer<>(context, 1.0F, true));
	}
}
