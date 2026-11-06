package com.jaymospowers.client.baubles.artifacts.worn;

import com.jaymospowers.baubles.Baubles;
import com.jaymospowers.baubles.artifacts.ArtifactBaubles;
import com.jaymospowers.client.baubles.artifacts.worn.model.BeltModel;
import com.jaymospowers.client.baubles.artifacts.worn.model.CharmPose;
import com.jaymospowers.client.baubles.artifacts.worn.model.CloudInABottleModel;
import com.jaymospowers.client.baubles.artifacts.worn.model.ScarfModel;
import com.jaymospowers.client.baubles.artifacts.worn.renderer.ArtifactRenderer;
import com.jaymospowers.client.baubles.artifacts.worn.renderer.BeltArtifactRenderer;
import com.jaymospowers.client.baubles.artifacts.worn.renderer.BootArtifactRenderer;
import com.jaymospowers.client.baubles.artifacts.worn.renderer.GloveArtifactRenderer;
import com.jaymospowers.client.baubles.artifacts.worn.renderer.SimpleArtifactRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.37) Andy: "Can we not port their looks too?" The Artifacts mod's own worn models (Wouter Pauwels, MIT; the mesh,
 * model and renderer classes in this package are its code, ported to our package) for the baubles we ported from it
 * that kept their Artifacts look: hats, scarves, necklaces and pendants, the belt charms, claws, gloves, the hook and
 * bracelet, and the shoes, with its textures ({@code textures/entity/wearable/artifacts/}, licence alongside).
 * <p>The sixteen we repainted medieval (the drinking hats, goggles, snorkel, cowboy hat, flamingo, warp drive, power glove,
 * pocket piston, pickaxe heater, onion ring, running shoes, aqua dashers, flippers...) keep our own looks
 * ({@code client/baubles/WornLooks}), since Artifacts' worn models are the modern things we renamed away from.
 * <p>Each slot's item comes from {@link Baubles#looks}, so everyone sees what everyone wears. Gloves and claws, a pair in
 * Artifacts' two hand slots, go on both hands from our one wrist slot.
 */
public final class WornArtifacts {
	/** Artifacts' belt positions run 0-3; this one hangs the charm at the front of the right hip, clear of the purse and lantern. */
	static final int BELT_POSITION = 1;

	private static final Map<Item, Supplier<ArtifactRenderer>> FACTORIES = new HashMap<>();
	private static final Map<Item, ArtifactRenderer> RENDERERS = new HashMap<>();
	/** Items whose pair of hands both get the glove. */
	private static final Map<Item, Boolean> BOTH_HANDS = new HashMap<>();

	private WornArtifacts() {
	}

	public static void init() {
		ArtifactLayers.register((location, layer) -> ModelLayerRegistry.registerModelLayer(location, layer::get));

		// head
		add(ArtifactBaubles.VILLAGER_HAT, () -> SimpleArtifactRenderer.create("villager_hat", humanoid(ArtifactLayers.BRIMMED_HAT, false)));
		add(ArtifactBaubles.FISHERS_HAT, () -> SimpleArtifactRenderer.create("anglers_hat", humanoid(ArtifactLayers.ANGLERS_HAT, false)));
		// neck
		add(ArtifactBaubles.LUCKY_SCARF, () -> SimpleArtifactRenderer.create("lucky_scarf", new ScarfModel(bake(ArtifactLayers.SCARF))));
		add(ArtifactBaubles.SCARF_OF_INVISIBILITY, () -> SimpleArtifactRenderer.create("scarf_of_invisibility",
			new ScarfModel(bake(ArtifactLayers.SCARF), RenderTypes::entityTranslucent)));
		add(ArtifactBaubles.CROSS_NECKLACE, () -> SimpleArtifactRenderer.create("cross_necklace", humanoid(ArtifactLayers.CROSS_NECKLACE, true)));
		add(ArtifactBaubles.PANIC_NECKLACE, () -> SimpleArtifactRenderer.create("panic_necklace", humanoid(ArtifactLayers.PANIC_NECKLACE, true)));
		add(ArtifactBaubles.SHOCK_PENDANT, () -> SimpleArtifactRenderer.create("shock_pendant", humanoid(ArtifactLayers.PENDANT, true)));
		add(ArtifactBaubles.FLAME_PENDANT, () -> SimpleArtifactRenderer.create("flame_pendant", humanoid(ArtifactLayers.PENDANT, true)));
		add(ArtifactBaubles.THORN_PENDANT, () -> SimpleArtifactRenderer.create("thorn_pendant", humanoid(ArtifactLayers.PENDANT, true)));
		add(ArtifactBaubles.CHARM_OF_SINKING, () -> SimpleArtifactRenderer.create("charm_of_sinking", humanoid(ArtifactLayers.CHARM_OF_SINKING, true)));
		add(ArtifactBaubles.CHARM_OF_SHRINKING, () -> SimpleArtifactRenderer.create("charm_of_shrinking", humanoid(ArtifactLayers.CHARM_OF_SHRINKING, true)));
		// belt
		add(ArtifactBaubles.CLOUD_IN_A_BOTTLE, () -> BeltArtifactRenderer.create("cloud_in_a_bottle",
			CloudInABottleModel.create(bake(ArtifactLayers.CLOUD_IN_A_BOTTLE), RenderTypes::entityTranslucent, CharmPose.CLOUD_IN_A_BOTTLE)));
		add(ArtifactBaubles.OBSIDIAN_SKULL, () -> BeltArtifactRenderer.create("obsidian_skull", BeltModel.create(bake(ArtifactLayers.OBSIDIAN_SKULL), CharmPose.OBSIDIAN_SKULL)));
		add(ArtifactBaubles.ANTIDOTE_VESSEL, () -> BeltArtifactRenderer.create("antidote_vessel", BeltModel.create(bake(ArtifactLayers.ANTIDOTE_VESSEL), CharmPose.ANTIDOTE_VESSEL)));
		add(ArtifactBaubles.CRYSTAL_HEART, () -> BeltArtifactRenderer.create("crystal_heart",
			BeltModel.create(bake(ArtifactLayers.CRYSTAL_HEART), RenderTypes::entityTranslucent, CharmPose.CRYSTAL_HEART)));
		add(ArtifactBaubles.CHORUS_TOTEM, () -> BeltArtifactRenderer.create("chorus_totem", BeltModel.create(bake(ArtifactLayers.CHORUS_TOTEM), CharmPose.CHORUS_TOTEM)));
		// hands
		addHands(ArtifactBaubles.DIGGING_CLAWS, true, () -> GloveArtifactRenderer.create("digging_claws", "digging_claws", ArmsModelSet.bake(ArtifactLayers.CLAWS)));
		addHands(ArtifactBaubles.FERAL_CLAWS, true, () -> GloveArtifactRenderer.create("feral_claws", "feral_claws", ArmsModelSet.bake(ArtifactLayers.CLAWS)));
		addHands(ArtifactBaubles.FIRE_GAUNTLET, false, () -> GloveArtifactRenderer.createGlowing("fire_gauntlet", ArmsModelSet.bake(ArtifactLayers.GLOVE)));
		addHands(ArtifactBaubles.VAMPIRIC_GLOVE, false, () -> GloveArtifactRenderer.create("vampiric_glove", ArmsModelSet.bake(ArtifactLayers.GLOVE)));
		addHands(ArtifactBaubles.GOLDEN_HOOK, false, () -> GloveArtifactRenderer.create("golden_hook", ArmsModelSet.bake(ArtifactLayers.GOLDEN_HOOK)));
		addHands(ArtifactBaubles.WITHERED_BRACELET, false, () -> GloveArtifactRenderer.create("withered_bracelet", ArmsModelSet.bake(ArtifactLayers.WITHERED_BRACELET)));
		// feet
		add(ArtifactBaubles.HARE_BOOTS, () -> SimpleArtifactRenderer.create("bunny_hoppers", humanoid(ArtifactLayers.BUNNY_HOPPERS, false)));
		add(ArtifactBaubles.KITTY_SLIPPERS, () -> SimpleArtifactRenderer.create("kitty_slippers", humanoid(ArtifactLayers.KITTY_SLIPPERS, false)));
		add(ArtifactBaubles.SNOWSHOES, () -> SimpleArtifactRenderer.create("snowshoes", humanoid(ArtifactLayers.SNOWSHOES, false)));
		add(ArtifactBaubles.STEADFAST_SPIKES, () -> SimpleArtifactRenderer.create("steadfast_spikes", humanoid(ArtifactLayers.STEADFAST_SPIKES, false)));
		add(ArtifactBaubles.ROOTED_BOOTS, () -> boots("rooted_boots"));
		add(ArtifactBaubles.STRIDER_SHOES, () -> boots("strider_shoes"));

		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Layer(avatar));
			}
		});
	}

	/** Whether {@code item} is drawn with its Artifacts look here (so our own look is skipped). */
	public static boolean has(Identifier item) {
		return BuiltInRegistries.ITEM.getOptional(item).map(FACTORIES::containsKey).orElse(false);
	}

	private static void add(Item item, Supplier<ArtifactRenderer> factory) {
		FACTORIES.put(item, factory);
	}

	private static void addHands(Item item, boolean both, Supplier<ArtifactRenderer> factory) {
		FACTORIES.put(item, factory);
		BOTH_HANDS.put(item, both);
	}

	private static ArtifactRenderer boots(String name) {
		return new BootArtifactRenderer(name, hasArmor -> humanoid(hasArmor ? ArtifactLayers.BOOTS_LARGE : ArtifactLayers.BOOTS_SMALL, false));
	}

	private static ModelPart bake(ModelLayerLocation location) {
		return Minecraft.getInstance().getEntityModels().bakeLayer(location);
	}

	private static <S extends net.minecraft.client.renderer.entity.state.HumanoidRenderState> HumanoidModel<S> humanoid(ModelLayerLocation location,
		boolean translucent) {
		return translucent ? new HumanoidModel<>(bake(location), RenderTypes::entityTranslucent)
			: new HumanoidModel<>(bake(location), RenderTypes::armorCutoutNoCull);
	}

	/** Its renderer, made the first time it's drawn (models can only be baked once the game has loaded them). */
	private static @Nullable ArtifactRenderer renderer(Item item) {
		ArtifactRenderer renderer = RENDERERS.get(item);
		if (renderer == null) {
			Supplier<ArtifactRenderer> factory = FACTORIES.get(item);
			if (factory == null) {
				return null;
			}
			renderer = factory.get();
			RENDERERS.put(item, renderer);
		}
		return renderer;
	}

	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			List<Identifier> looks = state.getData(com.jaymospowers.client.baubles.WornLooks.WORN);
			if (looks == null || state.isInvisible) {
				return;
			}
			for (Identifier id : looks) {
				Item item = BuiltInRegistries.ITEM.getOptional(id).orElse(null);
				ArtifactRenderer renderer = item == null ? null : renderer(item);
				if (renderer == null) {
					continue;
				}
				ItemStack stack = new ItemStack(item);
				if (renderer instanceof GloveArtifactRenderer) {
					renderer.render(stack, state, getParentModel(), 0, poseStack, collector, lightCoords);
					if (BOTH_HANDS.getOrDefault(item, false)) {
						renderer.render(stack, state, getParentModel(), 1, poseStack, collector, lightCoords);
					}
				} else {
					renderer.render(stack, state, getParentModel(), renderer instanceof BeltArtifactRenderer ? BELT_POSITION : 0, poseStack,
						collector, lightCoords);
				}
			}
		}
	}
}
