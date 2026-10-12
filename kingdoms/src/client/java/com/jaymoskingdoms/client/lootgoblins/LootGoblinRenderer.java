package com.jaymoskingdoms.client.lootgoblins;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.lootgoblins.LootGoblin;
import com.jaymoskingdoms.lootgoblins.LootGoblins;
import com.jaymoskingdoms.lootgoblins.OreMobs;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.EndermanRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.HoglinRenderer;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.PillagerRenderer;
import net.minecraft.client.renderer.entity.SkeletonRenderer;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.client.renderer.entity.state.EndermanRenderState;
import net.minecraft.client.renderer.entity.state.HoglinRenderState;
import net.minecraft.client.renderer.entity.state.IllagerRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.SkeletonRenderState;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;

/** Draws the loot goblins (their model and eight skins) and the minor goblins (vanilla's models in Loot Goblins' ore skins). */
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

	private static Identifier ore(String path) {
		return JaymosKingdoms.id("textures/entity/loot_goblin/" + path + ".png");
	}

	public static void register() {
		EntityRenderers.register(LootGoblins.LOOT_GOBLIN, LootGoblinRenderer::new);
		Identifier gold = ore("golden_skeleton"), lapis = ore("lapis_zombie"), iron = ore("iron_spider"), redstone = ore("redstone_creeper"),
			quartz = ore("quartz_hoglin"), emerald = ore("emerald_illager"), prismarine = ore("prismarine_enderman");
		EntityRenderers.register(LootGoblins.GOLDEN_SKELETON, context -> new SkeletonRenderer(context) {
			@Override
			public Identifier getTextureLocation(SkeletonRenderState state) {
				return gold;
			}
		});
		EntityRenderers.register(LootGoblins.LAPIS_ZOMBIE, context -> new ZombieRenderer(context) {
			@Override
			public Identifier getTextureLocation(ZombieRenderState state) {
				return lapis;
			}
		});
		EntityRenderers.register(LootGoblins.IRON_SPIDER, context -> new SpiderRenderer<OreMobs.IronSpider>(context) {
			@Override
			public Identifier getTextureLocation(LivingEntityRenderState state) {
				return iron;
			}
		});
		EntityRenderers.register(LootGoblins.REDSTONE_CREEPER, context -> new CreeperRenderer(context) {
			@Override
			public Identifier getTextureLocation(CreeperRenderState state) {
				return redstone;
			}
		});
		EntityRenderers.register(LootGoblins.QUARTZ_HOGLIN, context -> new HoglinRenderer(context) {
			@Override
			public Identifier getTextureLocation(HoglinRenderState state) {
				return quartz;
			}
		});
		EntityRenderers.register(LootGoblins.EMERALD_ILLAGER, context -> new PillagerRenderer(context) {
			@Override
			public Identifier getTextureLocation(IllagerRenderState state) {
				return emerald;
			}
		});
		EntityRenderers.register(LootGoblins.PRISMARINE_ENDERMAN, context -> new EndermanRenderer(context) {
			@Override
			public Identifier getTextureLocation(EndermanRenderState state) {
				return prismarine;
			}
		});
	}
}
