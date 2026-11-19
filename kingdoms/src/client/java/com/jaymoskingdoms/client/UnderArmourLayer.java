package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.McaCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.478) Under-armour, our own take on what Armor Reimagined (KhalMika, All Rights Reserved: the idea only, none of its
 * art) does with the leggings' layer (Andy's Gemini handoff, pass 3). Leggings worn with nothing over the chest (or only an
 * elytra) bring the rest of their suit: leather leggings an arming doublet (in the leggings' own dye), mail leggings a
 * quilted gambeson with mail sleeves, iron leggings a padded gambeson with an iron-studded collar. Drawn on the body and
 * arms of anything humanoid (players, folk, zombies), just over the skin and under anything worn later; posed as the
 * wearer is ({@link WornModel}). Textures are drawn by {@code tools/gen_under_armour.py}.
 * <p>
 * (0.7.484) On a body MCA draws it goes on MCA's armour body, past MCA's clothes ({@link McaUnderArmour}), and under a
 * costume or the symbiote's suit it isn't drawn at all ({@link PowersCover}).
 * <p>
 * The under-armour is a real slot now ({@link com.jaymoskingdoms.Harness}): an arming doublet worn as padding is drawn in
 * its own dye, and a mail haubergeon over it, whatever is on the chest (their sleeves show past a cuirass). Leggings
 * alone still bring their suit's look as before, when neither is worn.
 */
public final class UnderArmourLayer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
	/** How far the suit sits out from the skin (model pixels): over it, under a chestplate's 1.0. */
	static final float SIT = 0.3F;
	/** How far a haubergeon sits out: over the doublet. */
	static final float MAIL_SIT = 0.5F;
	static final ModelLayerLocation LAYER = new ModelLayerLocation(JaymosKingdoms.id("under_armour"), "main");
	static final ModelLayerLocation MAIL_LAYER = new ModelLayerLocation(JaymosKingdoms.id("under_armour_mail"), "main");
	private static final Identifier HAUBERGEON = JaymosKingdoms.id("textures/entity/under_armour/haubergeon.png");
	private static final Identifier DOUBLET = JaymosKingdoms.id("textures/entity/under_armour/doublet.png");
	private static final Identifier MAIL = JaymosKingdoms.id("textures/entity/under_armour/gambeson_mail.png");
	private static final Identifier IRON = JaymosKingdoms.id("textures/entity/under_armour/gambeson_iron.png");
	/** The other shirts of the harness (Harness 6): the aketon (tinted, as the doublet), the fur jerkin, the jazerant. */
	private static final Identifier AKETON = JaymosKingdoms.id("textures/entity/under_armour/aketon.png");
	private static final Identifier FUR_JERKIN = JaymosKingdoms.id("textures/entity/under_armour/fur_jerkin.png");
	private static final Identifier JAZERANT = JaymosKingdoms.id("textures/entity/under_armour/jazerant.png");
	/** An undyed aketon's colour (pale linen; the generator's DYED). */
	static final int AKETON_UNDYED = 0xD8CCB0;
	/** An undyed doublet's colour (brown leather, as vanilla's). */
	static final int UNDYED = 0xA06540;

	private final Suit suit;
	private final Suit mail;

	private UnderArmourLayer(RenderLayerParent<S, HumanoidModel<S>> renderer, EntityRendererProvider.Context context) {
		super(renderer);
		this.suit = new Suit(context.bakeLayer(LAYER), this::getParentModel);
		this.mail = new Suit(context.bakeLayer(MAIL_LAYER), this::getParentModel);
	}

	public static void register() {
		ModelLayerRegistry.registerModelLayer(LAYER, () -> LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(SIT), 0.0F), 64, 32));
		ModelLayerRegistry.registerModelLayer(MAIL_LAYER, () -> LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(MAIL_SIT), 0.0F), 64, 32));
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(create(renderer, context));
			}
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static UnderArmourLayer<?> create(LivingEntityRenderer<?, ?, ?> renderer, EntityRendererProvider.Context context) {
		return new UnderArmourLayer((RenderLayerParent) renderer, context);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
		if (state.isBaby || state.isInvisible || PowersCover.body(state)) {
			return;
		}
		if (harness(poseStack, collector, lightCoords, state)) {
			return;
		}
		ItemStack chest = state.chestEquipment;
		if (!chest.isEmpty() && !chest.is(Items.ELYTRA)) {
			return;
		}
		ItemStack legs = state.legsEquipment;
		Identifier texture = texture(legs);
		if (texture == null) {
			return;
		}
		int color = -1;
		if (texture == DOUBLET) {
			DyedItemColor dyed = legs.get(DataComponents.DYED_COLOR);
			color = ARGB.opaque(dyed != null ? dyed.rgb() : UNDYED);
		}
		HumanoidModel<HumanoidRenderState> model = McaCompat.LOADED ? McaUnderArmour.model(state) : null;
		if (model == null) {
			model = this.suit;
		}
		collector.submitModel(model, state, poseStack, RenderTypes.armorCutoutNoCull(texture), lightCoords, OverlayTexture.NO_OVERLAY, color, null,
			state.outlineColor);
	}

	/** The padding and mail worn in the harness, if any: the doublet in its dye, the haubergeon over it. False when neither is worn. */
	private boolean harness(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state) {
		java.util.List<ItemStack> worn = state.getData(HeraldryLayer.HARNESS);
		if (worn == null) {
			return false;
		}
		ItemStack padding = worn.get(com.jaymoskingdoms.Harness.Slot.PADDING.ordinal());
		ItemStack mail = worn.get(com.jaymoskingdoms.Harness.Slot.MAIL.ordinal());
		if (padding.isEmpty() && mail.isEmpty()) {
			return false;
		}
		// (MCA's body has the one armour model: the outer shirt alone goes on it)
		HumanoidModel<HumanoidRenderState> mca = McaCompat.LOADED ? McaUnderArmour.model(state) : null;
		if (!padding.isEmpty() && (mca == null || mail.isEmpty())) {
			String kind = BuiltInRegistries.ITEM.getKey(padding.getItem()).getPath();
			Identifier texture = kind.equals("aketon") ? AKETON : kind.equals("fur_jerkin") ? FUR_JERKIN : DOUBLET;
			int color = texture == FUR_JERKIN ? -1 : ARGB.opaque(DyedItemColor.getOrDefault(padding, texture == AKETON ? AKETON_UNDYED : UNDYED));
			collector.submitModel(mca != null ? mca : this.suit, state, poseStack, RenderTypes.armorCutoutNoCull(texture), lightCoords, OverlayTexture.NO_OVERLAY,
				color, null, state.outlineColor);
		}
		if (!mail.isEmpty()) {
			collector.submitModel(mca != null ? mca : this.mail, state, poseStack, RenderTypes.armorCutoutNoCull(BuiltInRegistries.ITEM.getKey(mail.getItem()).getPath().equals("jazerant") ? JAZERANT : HAUBERGEON), lightCoords, OverlayTexture.NO_OVERLAY,
				-1, null, state.outlineColor);
		}
		return true;
	}

	/** The suit that comes with {@code legs}, or null for none (no leggings, or a kind without one). */
	private static @Nullable Identifier texture(ItemStack legs) {
		if (legs.is(Items.LEATHER_LEGGINGS)) {
			return DOUBLET;
		}
		if (legs.is(Items.CHAINMAIL_LEGGINGS)) {
			return MAIL;
		}
		if (legs.is(Items.IRON_LEGGINGS)) {
			return IRON;
		}
		return null;
	}

	/** The suit: body and arms only, posed as the wearer is. */
	private static final class Suit extends WornModel {
		Suit(ModelPart root, Supplier<HumanoidModel<?>> wearer) {
			super(root, wearer);
		}

		@Override
		public void setupAnim(HumanoidRenderState state) {
			super.setupAnim(state);
			this.head.visible = false;
			this.hat.visible = false;
			this.rightLeg.visible = false;
			this.leftLeg.visible = false;
		}
	}
}
