package com.jaymoskingdoms.client.reaped;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.reaped.Reaped;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.IllagerRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.monster.illager.AbstractIllager;

/** The reaped ({@link Reaped}) as they're seen: each on {@link ReapedModel} in its own skin, what it carries in its hanging hand. */
public final class ReapedClient {
	private ReapedClient() {
	}

	public static void register() {
		EntityRenderers.register(Reaped.pillager(), context -> new Renderer<>(context, "reaped_pillager", false));
		EntityRenderers.register(Reaped.vindicator(), context -> new Renderer<>(context, "reaped_vindicator", false));
		EntityRenderers.register(Reaped.evoker(), context -> new Renderer<>(context, "reaped_evoker", true));
	}

	private static final class Renderer<T extends AbstractIllager> extends IllagerRenderer<T, IllagerRenderState> {
		private final Identifier texture;

		Renderer(EntityRendererProvider.Context context, String name, boolean robed) {
			super(context, new ReapedModel(ReapedModel.createBodyLayer(robed).bakeRoot()), 0.5F);
			this.texture = JaymosKingdoms.id("textures/entity/reaped/" + name + ".png");
			this.addLayer(new ItemInHandLayer<>(this));
		}

		@Override
		public Identifier getTextureLocation(IllagerRenderState state) {
			return this.texture;
		}

		@Override
		public IllagerRenderState createRenderState() {
			return new IllagerRenderState();
		}
	}
}
