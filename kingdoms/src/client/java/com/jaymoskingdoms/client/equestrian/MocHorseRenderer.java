package com.jaymoskingdoms.client.equestrian;

import com.jaymoskingdoms.JaymosKingdoms;
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
 * (0.5.44) Draws horses, skeleton horses and zombie horses as {@link MocHorseModel} (Mo' Creatures' horse) in their coat
 * ({@link HorseLooks}), with horse armour as an overlay on a slightly larger copy of the model (Mo' Creatures' metal,
 * gold and diamond armour, and ours made from the metal: {@code textures/entity/moc_horse/armor/<namespace>/<asset>.png},
 * by the armour's equipment asset; iron for any we haven't drawn), and the tack ({@link TackLayer}). A foal is the
 * same horse, smaller.
 */
public final class MocHorseRenderer extends AbstractHorseRenderer<AbstractHorse, MocHorseRenderer.State, MocHorseModel> {
	/** A foal's size against a grown horse's. */
	private static final float FOAL_SCALE = 0.55F;
	private static final Identifier IRON = armorTexture(EquipmentAssets.IRON.identifier());
	private static final Map<Identifier, Identifier> ARMOR = new HashMap<>();

	public static final class State extends EquineRenderState {
		Identifier coat = HorseLooks.FALLBACK;
		@Nullable Identifier armor;
		int armorColor = -1;
		/** The original's saddle (for a vanilla saddle), and its headstall and reins (any saddle, unless it wears a bridle). */
		boolean mocSaddle, mocHeadstall;
	}

	public MocHorseRenderer(EntityRendererProvider.Context context) {
		super(context, new MocHorseModel(MocHorseModel.createBodyLayer().bakeRoot()), new MocHorseModel(MocHorseModel.createBodyLayer().bakeRoot()));
		this.addLayer(new ArmorLayer(this, new MocHorseModel(MocHorseModel.createArmorLayer().bakeRoot())));
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
		ItemStack armor = state.bodyArmorItem;
		Equippable equippable = armor.isEmpty() ? null : armor.get(DataComponents.EQUIPPABLE);
		Identifier asset = equippable == null ? null : equippable.assetId().map(k -> k.identifier()).orElse(null);
		state.armor = asset == null ? null : ARMOR.computeIfAbsent(asset, MocHorseRenderer::armorFor);
		state.armorColor = asset != null && asset.equals(EquipmentAssets.LEATHER.identifier())
			? ARGB.opaque(DyedItemColor.getOrDefault(armor, DyedItemColor.LEATHER_COLOR)) : -1;
		TackLayer.Look look = state.getData(TackLayer.LOOK);
		boolean saddled = !state.saddle.isEmpty();
		state.mocSaddle = saddled && (look == null || look.saddle() == 0);
		state.mocHeadstall = saddled && (look == null || !look.bridle());
	}

	private static Identifier armorTexture(Identifier asset) {
		return JaymosKingdoms.id("textures/entity/moc_horse/armor/" + asset.getNamespace() + "/" + asset.getPath() + ".png");
	}

	/** The overlay drawn for an equipment asset: ours if we've drawn one, else iron's. */
	private static Identifier armorFor(Identifier asset) {
		Identifier texture = armorTexture(asset);
		return Minecraft.getInstance().getResourceManager().getResource(texture).isPresent() ? texture : IRON;
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
	private static final class ArmorLayer extends RenderLayer<State, MocHorseModel> {
		private final MocHorseModel model;

		ArmorLayer(RenderLayerParent<State, MocHorseModel> parent, MocHorseModel model) {
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
