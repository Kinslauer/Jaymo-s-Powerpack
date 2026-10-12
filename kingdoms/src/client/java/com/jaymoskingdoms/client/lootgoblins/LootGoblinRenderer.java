package com.jaymoskingdoms.client.lootgoblins;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.lootgoblins.LootGoblin;
import com.jaymoskingdoms.lootgoblins.LootGoblins;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;

/** Draws the loot goblins: their model and eight skins. */
public final class LootGoblinRenderer extends MobRenderer<LootGoblin, LootGoblinRenderer.State, LootGoblinModel> {
	/** The skins by look ({@link LootGoblin#VARIANTS}). */
	private static final String[] SKINS = {"lootgoblin", "nethergoblin", "endgoblin", "armorer", "cake", "plantgoblin", "eastergoblin", "witch"};
	private static final Identifier[] TEXTURES = new Identifier[SKINS.length];

	static {
		for (int i = 0; i < SKINS.length; i++) {
			TEXTURES[i] = JaymosKingdoms.id("textures/entity/loot_goblin/" + SKINS[i] + ".png");
		}
	}

	static final class State extends LivingEntityRenderState {
		int variant;
	}

	private LootGoblinRenderer(EntityRendererProvider.Context context) {
		super(context, new LootGoblinModel(LootGoblinModel.createBodyLayer().bakeRoot()), 0.3F);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(LootGoblin goblin, State state, float partialTicks) {
		super.extractRenderState(goblin, state, partialTicks);
		state.variant = goblin.variant();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return TEXTURES[Math.floorMod(state.variant, TEXTURES.length)];
	}

	public static void register() {
		EntityRenderers.register(LootGoblins.LOOT_GOBLIN, LootGoblinRenderer::new);
	}
}
