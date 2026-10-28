package com.jaymoskingdoms.client.equestrian;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.equestrian.Breed;
import com.jaymoskingdoms.equestrian.Equestrian;
import com.jaymoskingdoms.equestrian.Equine;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.AbstractHorseRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EquineRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.SkeletonHorse;
import net.minecraft.world.entity.animal.equine.ZombieHorse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.302) Draws horses, skeleton horses and zombie horses as {@link BreedHorseModel}, in their breed's build and coat
 * ({@link HorseLooks}), with horse armour as an overlay on a slightly larger copy of the model (iron, gold, diamond,
 * copper, netherite, leather and our chain and plate barding, drawn for each build:
 * {@code textures/entity/breed_horse/armor/<namespace>/<asset>/<build>.png}, by the armour's equipment asset; iron for
 * any we haven't drawn), and the tack ({@link TackLayer}). A foal is the same horse, smaller. (Until 0.7.302 this drew
 * Mo' Creatures' horse, 0.5.44.)
 */
public final class BreedHorseRenderer extends AbstractHorseRenderer<AbstractHorse, BreedHorseRenderer.State, BreedHorseModel> {
	/** A foal's size against a grown horse's. */
	private static final float FOAL_SCALE = 0.55F;
	private static final Map<String, Identifier> ARMOR = new HashMap<>();

	public static final class State extends EquineRenderState {
		Identifier coat = HorseLooks.FALLBACK;
		/** Which build of the model it's drawn in. */
		String build = HorseLooks.FALLBACK_BUILD;
		@Nullable Identifier armor;
		int armorColor = -1;
		/** The model's own saddle (for a vanilla saddle), and its headstall and reins (any saddle, unless it wears a bridle). */
		boolean vanillaSaddle, headstall;
		/** (0.5.56) A bat horse: its wings, off the ground, climbing. */
		boolean wings, airborne, flapping;
	}

	/** (0.5.56) The nightmare's coat flickers between its two skins, this many ticks each. */
	private static final int NIGHTMARE_FLICKER_TICKS = 3;
	private static final Identifier NIGHTMARE_FLICKER = HorseLooks.texture("textures/entity/breed_horse/nightmare/nightmare_1.png");

	public BreedHorseRenderer(EntityRendererProvider.Context context) {
		super(context, new BreedHorseModel(BreedHorseModel.createBodyLayer().bakeRoot()), new BreedHorseModel(BreedHorseModel.createBodyLayer().bakeRoot()));
		this.addLayer(new ArmorLayer(this, new BreedHorseModel(BreedHorseModel.createArmorLayer().bakeRoot())));
		this.addLayer(new TackLayer(this));
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(AbstractHorse horse, State state, float partialTicks) {
		super.extractRenderState(horse, state, partialTicks);
		Identifier coat = HorseLooks.texture(state);
		state.coat = coat != null ? coat : horse instanceof SkeletonHorse ? HorseLooks.FALLBACK_SKELETON
			: horse instanceof ZombieHorse ? HorseLooks.FALLBACK_ZOMBIE : HorseLooks.FALLBACK;
		state.build = HorseLooks.build(state);
		ItemStack armor = state.bodyArmorItem;
		Equippable equippable = armor.isEmpty() ? null : armor.get(DataComponents.EQUIPPABLE);
		Identifier asset = equippable == null ? null : equippable.assetId().map(k -> k.identifier()).orElse(null);
		state.armor = asset == null ? null : armorFor(asset, state.build);
		state.armorColor = asset != null && asset.equals(EquipmentAssets.LEATHER.identifier())
			? ARGB.opaque(DyedItemColor.getOrDefault(armor, DyedItemColor.LEATHER_COLOR)) : -1;
		TackLayer.Look look = state.getData(TackLayer.LOOK);
		boolean saddled = !state.saddle.isEmpty();
		state.vanillaSaddle = saddled && (look == null || look.saddle() == 0);
		state.headstall = saddled && (look == null || !look.bridle());
		Equine e = Equestrian.peek(horse);
		state.wings = e != null && e.breed() == Breed.BAT_HORSE;
		state.airborne = !horse.onGround() && !horse.isInWater();
		state.flapping = state.airborne && horse.getDeltaMovement().y > 0.0;
		if (e != null && e.breed() == Breed.NIGHTMARE && (int) (state.ageInTicks / NIGHTMARE_FLICKER_TICKS) % 2 == 1) {
			state.coat = NIGHTMARE_FLICKER;
		}
	}

	private static Identifier armorTexture(Identifier asset, String build) {
		return JaymosKingdoms.id("textures/entity/breed_horse/armor/" + asset.getNamespace() + "/" + asset.getPath() + "/" + build + ".png");
	}

	/** The overlay drawn for an equipment asset on a build: ours if we've drawn one, else iron's. */
	private static Identifier armorFor(Identifier asset, String build) {
		return ARMOR.computeIfAbsent(asset + "|" + build, k -> {
			Identifier texture = armorTexture(asset, build);
			return Minecraft.getInstance().getResourceManager().getResource(texture).isPresent() ? texture
				: armorTexture(EquipmentAssets.IRON.identifier(), build);
		});
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return state.coat;
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		if (state.isBaby) {
			poseStack.scale(FOAL_SCALE, FOAL_SCALE, FOAL_SCALE);
		}
		super.scale(state, poseStack);
	}

	/** Horse armour, over the coat. */
	private static final class ArmorLayer extends RenderLayer<State, BreedHorseModel> {
		private final BreedHorseModel model;

		ArmorLayer(RenderLayerParent<State, BreedHorseModel> parent, BreedHorseModel model) {
			super(parent);
			this.model = model;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, State state, float yRot, float xRot) {
			if (state.armor != null && !state.isInvisible) {
				renderColoredCutoutModel(this.model, state.armor, poseStack, collector, lightCoords, state, state.armorColor, 1);
			}
		}
	}
}
