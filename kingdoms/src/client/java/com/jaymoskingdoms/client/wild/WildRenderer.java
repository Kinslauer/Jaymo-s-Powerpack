package com.jaymoskingdoms.client.wild;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.wild.FeralWolf;
import com.jaymoskingdoms.wild.Wild;
import com.jaymoskingdoms.wild.WildLook;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Function;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import org.jspecify.annotations.Nullable;

/**
 * Draws the wild beasts ({@link Wild}) with their Better Animals Plus models and skins, glowing eyes where they have
 * them (the coyote's only while it's hostile), the moose 1.5x.
 */
public final class WildRenderer<T extends Mob & WildLook> extends MobRenderer<T, WildRenderer.State, WildModel> {
	public static final class State extends LivingEntityRenderState {
		int variant;
		boolean hostile;
	}

	private final Identifier[] skins;
	private final @Nullable Identifier hostileSkin;
	private final float scale;

	private WildRenderer(EntityRendererProvider.Context context, WildModel model, float shadow, float scale, String[] skins,
		@Nullable String hostileSkin, @Nullable String eyes) {
		super(context, model, shadow * scale);
		this.scale = scale;
		this.skins = new Identifier[skins.length];
		for (int i = 0; i < skins.length; i++) {
			this.skins[i] = texture(skins[i]);
		}
		this.hostileSkin = hostileSkin == null ? null : texture(hostileSkin);
		if (eyes != null) {
			RenderType glow = RenderTypes.eyes(texture(eyes));
			this.addLayer(new EyesLayer<>(this) {
				@Override
				public RenderType renderType() {
					return glow;
				}

				@Override
				public void submit(PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector, int light, State state, float yRot, float xRot) {
					if (state.hostile) {
						super.submit(poseStack, collector, light, state, yRot, xRot);
					}
				}
			});
		}
	}

	private static Identifier texture(String name) {
		return JaymosKingdoms.id("textures/entity/wild/" + name + ".png");
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(T beast, State state, float partialTicks) {
		super.extractRenderState(beast, state, partialTicks);
		state.variant = beast.variant();
		state.hostile = beast.hostileLook();
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		if (this.scale != 1.0F) {
			poseStack.scale(this.scale, this.scale, this.scale);
		}
	}

	@Override
	public Identifier getTextureLocation(State state) {
		if (state.hostile && this.hostileSkin != null) {
			return this.hostileSkin;
		}
		return this.skins[Math.floorMod(state.variant, this.skins.length)];
	}

	private static <T extends Mob & WildLook> void register(EntityType<T> type, Function<ModelPart, WildModel> model, LayerDefinition layer,
		float shadow, float scale, String[] skins, @Nullable String hostileSkin, @Nullable String eyes) {
		EntityRenderers.register(type, context -> new WildRenderer<>(context, model.apply(layer.bakeRoot()), shadow, scale, skins, hostileSkin, eyes));
	}

	public static void register() {
		String[] wolves = new String[FeralWolf.COATS.length];
		for (int i = 0; i < wolves.length; i++) {
			wolves[i] = "feralwolf_" + FeralWolf.COATS[i];
		}
		register(Wild.FERAL_WOLF, FeralWolfModel::new, FeralWolfModel.createBodyLayer(), 0.5F, 1.0F, wolves, null, "feralwolf_eyes");
		register(Wild.COYOTE, CoyoteModel::new, CoyoteModel.createBodyLayer(), 0.5F, 1.0F, new String[] {"coyote"}, "coyote_hostile",
			"coyote_hostile_eyes");
		register(Wild.TARANTULA, TarantulaModel::new, TarantulaModel.createBodyLayer(), 1.0F, 1.0F,
			new String[] {"tarantula_desert_1", "tarantula_desert_2", "tarantula_desert_3", "tarantula_jungle_1", "tarantula_jungle_2",
				"tarantula_jungle_3"}, null, "tarantula_eyes");
		register(Wild.MOOSE, MooseModel::new, MooseModel.createBodyLayer(), 0.8F, 1.5F, new String[] {"moose_1", "moose_2", "moose_3", "moose_4"},
			null, null);
	}
}
