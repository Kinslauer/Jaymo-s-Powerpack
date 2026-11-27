package com.jaymoskingdoms.client.deep;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.deep.DeepBeast;
import com.jaymoskingdoms.deep.DeepSpecies;
import com.jaymoskingdoms.deep.Deeps;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;

/**
 * Draws a deep beast (Rim's rig, ported and repainted: see {@link DeepBeastModel}) in its violet hide with its amber glow
 * (veins and gills) over it, full-bright.
 */
public final class DeepBeastRenderer extends MobRenderer<DeepBeast, DeepBeastState, DeepBeastModel> {
	private final DeepSpecies species;
	private final Identifier texture;

	DeepBeastRenderer(EntityRendererProvider.Context context, DeepSpecies species, DeepBeastModel.Spec spec) {
		super(context, new DeepBeastModel(DeepBeastModel.layer(spec).bakeRoot(), spec), species.width * 0.5F);
		this.species = species;
		this.texture = JaymosKingdoms.id("textures/entity/deep/" + species.id + ".png");
		Identifier glow = JaymosKingdoms.id("textures/entity/deep/" + species.id + "_glow.png");
		this.addLayer(new EyesLayer<DeepBeastState, DeepBeastModel>(this) {
			@Override
			public RenderType renderType() {
				return RenderTypes.eyes(glow);
			}
		});
	}

	public static void register() {
		for (DeepSpecies species : DeepSpecies.values()) {
			DeepBeastModel.Spec spec = DeepBeastModel.load(species.id);
			EntityRenderers.register(Deeps.TYPES.get(species), context -> new DeepBeastRenderer(context, species, spec));
		}
	}

	@Override
	public DeepBeastState createRenderState() {
		return new DeepBeastState();
	}

	@Override
	public void extractRenderState(DeepBeast entity, DeepBeastState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.bite = entity.bite();
	}

	@Override
	protected void scale(DeepBeastState state, PoseStack poseStack) {
		poseStack.scale(this.species.scale, this.species.scale, this.species.scale);
	}

	@Override
	public Identifier getTextureLocation(DeepBeastState state) {
		return this.texture;
	}

	@Override
	protected AABB getBoundingBoxForCulling(DeepBeast entity, float partialTicks) {
		return super.getBoundingBoxForCulling(entity, partialTicks).inflate(this.species.width, 1.0, this.species.width);
	}
}
