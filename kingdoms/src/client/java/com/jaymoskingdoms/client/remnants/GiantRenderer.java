package com.jaymoskingdoms.client.remnants;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.remnants.AetherGiant;
import com.jaymoskingdoms.remnants.Element;
import com.jaymoskingdoms.remnants.Monoliths;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;

/**
 * (0.7.97) Draws the Aether's Giants ({@link AetherGiant}): one model ({@link GiantModel}), drawn {@link #SCALE} times
 * over, in its element's skin ({@code tools/gen_giants.py}).
 */
public class GiantRenderer extends MobRenderer<AetherGiant, GiantState, GiantModel> {
	/** How much bigger than its model it's drawn: to its full height (the model stands about 2.64 blocks). */
	static final float SCALE = AetherGiant.HEIGHT / 2.64F;
	static final float SHADOW = AetherGiant.WIDTH;

	private final Identifier texture;

	GiantRenderer(EntityRendererProvider.Context context, Element element) {
		super(context, new GiantModel(GiantModel.createBodyLayer().bakeRoot()), SHADOW);
		this.texture = JaymosKingdoms.id("textures/entity/giant/" + element.key() + ".png");
	}

	@Override
	public GiantState createRenderState() {
		return new GiantState();
	}

	@Override
	public void extractRenderState(AetherGiant giant, GiantState state, float partialTicks) {
		super.extractRenderState(giant, state, partialTicks);
		state.leftAttack = giant.leftCounter() > 0 ? giant.leftCounter() + partialTicks : 0.0F;
		state.rightAttack = giant.rightCounter() > 0 ? giant.rightCounter() + partialTicks : 0.0F;
		state.stamp = giant.swingingArm() == AetherGiant.BOTH;
		state.aggressive = giant.isAggressive();
		state.flinch = giant.hurtTime > 0 && giant.hurtDuration > 0 ? (giant.hurtTime - partialTicks) / giant.hurtDuration : 0.0F;
		state.seed = giant.getId();
		state.stride = giant.stride(partialTicks);
		int grab = giant.grabTick();
		state.grab = grab >= 0 && grab < AetherGiant.GRAB_TOTAL ? grab + partialTicks : -1.0F;
	}

	/** It doesn't topple sideways when it dies: the model falls on its face and breaks up instead (0.7.184, {@link GiantModel}). */
	@Override
	protected float getFlipDegrees() {
		return 0.0F;
	}

	@Override
	protected void scale(GiantState state, PoseStack poseStack) {
		poseStack.scale(SCALE, SCALE, SCALE);
	}

	@Override
	public Identifier getTextureLocation(GiantState state) {
		return this.texture;
	}

	public static void register() {
		for (Element element : Element.values()) {
			EntityRenderers.register(Monoliths.GIANTS.get(element), context -> new GiantRenderer(context, element));
		}
	}
}
