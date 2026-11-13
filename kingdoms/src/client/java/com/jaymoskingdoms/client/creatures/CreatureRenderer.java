package com.jaymoskingdoms.client.creatures;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.creatures.ClearCube;
import com.jaymoskingdoms.creatures.Looks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/** One renderer for the creatures of patch 9 (all but the bats): a model from a shape file, its own sheet, its pose from {@link Looks}. */
final class CreatureRenderer<T extends Mob> extends MobRenderer<T, CreatureState, CreatureModel> {
	private final CreatureModel.Spec spec;
	private final Identifier texture;
	private final int lieAt, flapsFrom;
	private final boolean glows;

	CreatureRenderer(EntityRendererProvider.Context context, String name, CreatureModel.Spec spec, float shadow, int lieAt, int flapsFrom, boolean glows) {
		super(context, new CreatureModel(CreatureModel.layer(spec).bakeRoot(), spec), shadow);
		this.spec = spec;
		this.texture = JaymosKingdoms.id("textures/entity/creatures/" + name + ".png");
		this.lieAt = lieAt;
		this.flapsFrom = flapsFrom;
		this.glows = glows;
		if (name.equals("clear_cube")) {
			this.addLayer(new Floating(this));
		}
	}

	@Override
	public CreatureState createRenderState() {
		return new CreatureState();
	}

	@Override
	public void extractRenderState(T entity, CreatureState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		int look = entity instanceof Looks l ? l.look() : 0;
		state.look = look;
		state.full = entity instanceof Looks l ? l.fullness() : 0.0F;
		state.lie = this.lieAt >= 0 && look == this.lieAt;
		state.flaps = this.flapsFrom >= 0 && look >= this.flapsFrom;
		state.insideCount = 0;
		if (entity instanceof ClearCube cube) {
			Minecraft minecraft = Minecraft.getInstance();
			int i = 0;
			for (ItemStack stack : cube.carried()) {
				if (i >= state.inside.length) {
					break;
				}
				minecraft.getItemModelResolver().updateForTopItem(state.inside[i], stack, ItemDisplayContext.GROUND, entity.level(), entity, entity.getId() + i);
				i++;
			}
			state.insideCount = i;
		}
	}

	@Override
	protected void scale(CreatureState state, PoseStack poseStack) {
		poseStack.scale(this.spec.scale(), this.spec.scale(), this.spec.scale());
	}

	@Override
	public Identifier getTextureLocation(CreatureState state) {
		return this.texture;
	}

	@Override
	protected @Nullable RenderType getRenderType(CreatureState state, boolean bodyVisible, boolean forceTransparent, boolean glowing) {
		if (this.spec.translucent() && bodyVisible && !forceTransparent) {
			return RenderTypes.entityTranslucent(this.texture);
		}
		return super.getRenderType(state, bodyVisible, forceTransparent, glowing);
	}

	@Override
	protected int getBlockLightLevel(T entity, net.minecraft.core.BlockPos pos) {
		return this.glows ? 15 : super.getBlockLightLevel(entity, pos);
	}

	/** The things a clear cube has swallowed, turning slowly in it. */
	private static final class Floating extends RenderLayer<CreatureState, CreatureModel> {
		Floating(CreatureRenderer<?> parent) {
			super(parent);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, CreatureState state, float yRot, float xRot) {
			for (int i = 0; i < state.insideCount; i++) {
				float t = state.ageInTicks * 0.03F + i * 1.9F;
				float height = 0.55F + 0.3F * i + Mth.sin(t * 1.3F) * 0.08F;
				poseStack.pushPose();
				poseStack.translate(Mth.sin(t) * 0.35F, 1.5F - height, Mth.cos(t * 0.8F) * 0.35F);
				poseStack.scale(-0.45F, -0.45F, 0.45F);
				poseStack.rotateDegrees(Axis.YP, state.ageInTicks * 1.5F + i * 70.0F);
				state.inside[i].submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
				poseStack.popPose();
			}
		}
	}
}
