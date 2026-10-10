package com.jaymoskingdoms.client;

import com.jaymoskingdoms.Folk;
import com.jaymoskingdoms.JaymosKingdoms;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

/** The client: how a kingdom's people look (a guard, or the king with his crown). */
public final class JaymosKingdomsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		EntityRenderers.register(JaymosKingdoms.FOLK, FolkRenderer::new);
	}

	/** Their render state: which of them it is. */
	public static final class FolkState extends HumanoidRenderState {
		int role;
	}

	/** Humanoid, in armour (theirs is real armour), with a skin per role. */
	static final class FolkRenderer extends HumanoidMobRenderer<Folk, FolkState, HumanoidModel<FolkState>> {
		private static final Identifier GUARD = JaymosKingdoms.id("textures/entity/guard.png");
		private static final Identifier KING = JaymosKingdoms.id("textures/entity/king.png");

		FolkRenderer(EntityRendererProvider.Context context) {
			super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
			this.addLayer(new HumanoidArmorLayer<>(this, ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, context.getModelSet(), HumanoidModel::new),
				context.getEquipmentRenderer()));
		}

		@Override
		public FolkState createRenderState() {
			return new FolkState();
		}

		@Override
		public void extractRenderState(Folk entity, FolkState state, float partialTicks) {
			super.extractRenderState(entity, state, partialTicks);
			state.role = entity.role();
		}

		@Override
		public Identifier getTextureLocation(FolkState state) {
			return state.role == Folk.KING ? KING : GUARD;
		}
	}
}
