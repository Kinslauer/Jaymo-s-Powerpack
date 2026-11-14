package com.jaymoskingdoms.client.reaped;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.reaped.Reaped;
import com.jaymoskingdoms.reaped.ReapedEvoker;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.EvokerRenderer;
import net.minecraft.client.renderer.entity.PillagerRenderer;
import net.minecraft.client.renderer.entity.VindicatorRenderer;
import net.minecraft.client.renderer.entity.state.EvokerRenderState;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.resources.Identifier;

/** The reaped ({@link Reaped}) as they're seen: vanilla's own illagers, each in its dead skin. */
public final class ReapedClient {
	private static final Identifier PILLAGER = JaymosKingdoms.id("textures/entity/reaped/reaped_pillager.png"),
		VINDICATOR = JaymosKingdoms.id("textures/entity/reaped/reaped_vindicator.png"),
		EVOKER = JaymosKingdoms.id("textures/entity/reaped/reaped_evoker.png");

	private ReapedClient() {
	}

	public static void register() {
		EntityRenderers.register(Reaped.pillager(), context -> new PillagerRenderer(context) {
			@Override
			public Identifier getTextureLocation(IllagerRenderState state) {
				return PILLAGER;
			}
		});
		EntityRenderers.register(Reaped.vindicator(), context -> new VindicatorRenderer(context) {
			@Override
			public Identifier getTextureLocation(IllagerRenderState state) {
				return VINDICATOR;
			}
		});
		EntityRenderers.register(Reaped.evoker(), context -> new EvokerRenderer<ReapedEvoker>(context) {
			@Override
			public Identifier getTextureLocation(EvokerRenderState state) {
				return EVOKER;
			}
		});
	}
}
