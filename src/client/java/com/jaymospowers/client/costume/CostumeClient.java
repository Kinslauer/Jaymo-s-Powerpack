package com.jaymospowers.client.costume;

import com.jaymospowers.client.power.web.WebSuitGrowth;
import com.jaymospowers.client.power.web.WebSymbioteClient;
import com.jaymospowers.client.power.xenoform.XenoformClient;
import com.jaymospowers.costume.Costume;
import com.jaymospowers.costume.CostumeDesign;
import com.jaymospowers.costume.CostumeNet;
import com.jaymospowers.power.web.WebSymbiote;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Costumes on the client: the designer opens when the server says ({@link CostumeScreen}); every player's worn costume
 * goes on their render state ({@link #extract}, which also takes their armour off it: it still protects, it just isn't
 * drawn), and {@link Layer} draws it over the body. In first person your own sleeves are drawn over your hands.
 * <p>
 * The costume gives way to a Xeno-Band form and to the symbiote's suit (both cover the whole body themselves).
 */
public final class CostumeClient {
	/** The costume to draw on this player, or null. */
	public static final RenderStateDataKey<CostumeDesign> KEY = RenderStateDataKey.create(() -> "jaymospowers:costume");

	/** While the designer draws its preview: the design shown on the local player in place of what they wear. */
	private static @Nullable CostumeDesign preview;
	private static CostumeModels.@Nullable Set handModels;

	private CostumeClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(CostumeNet.Open.TYPE, (payload, context) ->
			context.client().gui.setScreen(new CostumeScreen(payload.pos())));
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Layer(avatar));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(CostumeEmblems::tick);
	}

	/** The designer's preview: while {@code run} extracts the local player's render state, they wear {@code design}. */
	static void withPreview(CostumeDesign design, Runnable run) {
		preview = design;
		try {
			run.run();
		} finally {
			preview = null;
		}
	}

	/** From the avatar renderer's extractRenderState, last: the costume on the state, the armour off it. */
	public static void extract(Avatar entity, AvatarRenderState state) {
		CostumeDesign design = null;
		if (entity instanceof Player player) {
			design = preview != null && player == Minecraft.getInstance().player ? preview : Costume.worn(player);
		}
		state.setData(KEY, design);
		if (design == null) {
			return;
		}
		state.headEquipment = ItemStack.EMPTY;
		state.headItem.clear();
		state.wornHeadType = null;
		state.wornHeadProfile = null;
		if (!state.chestEquipment.has(DataComponents.GLIDER)) {
			state.chestEquipment = ItemStack.EMPTY;
		}
		state.legsEquipment = ItemStack.EMPTY;
		state.feetEquipment = ItemStack.EMPTY;
		if (design.cape() != CostumeDesign.Cape.NONE) {
			state.showCape = false;
		}
	}

	/** Whether something else covers the whole body just now (a Xeno-Band form, the symbiote's suit). */
	private static boolean covered(AvatarRenderState state) {
		if (state.getData(XenoformClient.FORM) != null || state.getData(WebSymbioteClient.SUIT) != null || state.getData(WebSuitGrowth.KEY) != null) {
			return true;
		}
		WebSymbioteClient.Tendrils tendrils = state.getData(WebSymbioteClient.TENDRILS);
		return tendrils != null && tendrils.armor();
	}

	/** Draws the worn costume over a player, piece by piece, each piece once per colour. */
	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		private final CostumeModels.Set models;

		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
			this.models = new CostumeModels.Set(this::getParentModel);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			CostumeDesign design = state.getData(KEY);
			if (design == null || state.isInvisible || covered(state)) {
				return;
			}
			int primary = 0xFF000000 | design.primary().getTextureDiffuseColor();
			int secondary = 0xFF000000 | design.secondary().getTextureDiffuseColor();
			boolean slim = state.skin.model() == PlayerModelType.SLIM;
			String cut = design.cut().getSerializedName();
			CostumeModels.Worn suit = this.models.suit(design.cut(), slim);
			renderColoredCutoutModel(suit, CostumeModels.texture("suit", cut, slim, false), poseStack, collector, lightCoords, state, primary, 1);
			renderColoredCutoutModel(suit, CostumeModels.texture("suit", cut, slim, true), poseStack, collector, lightCoords, state, secondary, 1);
			CostumeModels.Worn mask = this.models.mask(design.mask());
			if (mask != null) {
				String name = design.mask().getSerializedName();
				renderColoredCutoutModel(mask, CostumeModels.texture("mask", name, false, false), poseStack, collector, lightCoords, state, primary, 1);
				renderColoredCutoutModel(mask, CostumeModels.texture("mask", name, false, true), poseStack, collector, lightCoords, state, secondary, 1);
			}
			CostumeModels.Worn cape = this.models.cape(design.cape());
			if (cape != null && !state.chestEquipment.has(DataComponents.GLIDER)) {
				String name = design.cape().getSerializedName();
				renderColoredCutoutModel(cape, CostumeModels.texture("cape", name, false, false), poseStack, collector, lightCoords, state, primary, 1);
				renderColoredCutoutModel(cape, CostumeModels.texture("cape", name, false, true), poseStack, collector, lightCoords, state, secondary, 1);
			}
			if (design.hasEmblem()) {
				renderColoredCutoutModel(this.models.emblem, CostumeEmblems.texture(design), poseStack, collector, lightCoords, state, -1, 1);
			}
		}
	}

	/** First person, after the game draws your arm: the costume's sleeve over it (from the avatar renderer's renderHand). */
	public static void renderHand(PoseStack poseStack, SubmitNodeCollector collector, int light, ModelPart arm, PlayerModel source) {
		LocalPlayer player = Minecraft.getInstance().player;
		CostumeDesign design = player != null ? Costume.worn(player) : null;
		if (design == null || WebSymbiote.suitShown(player) || WebSymbiote.bulked(player)) {
			return;
		}
		if (handModels == null) {
			// these never re-run a wearer's animation (only the arm's own pose is copied below)
			handModels = new CostumeModels.Set(() -> source);
		}
		boolean slim = player.getSkin().model() == PlayerModelType.SLIM;
		CostumeModels.Worn suit = handModels.suit(design.cut(), slim);
		ModelPart sleeve = arm == source.rightArm ? suit.rightArm : suit.leftArm;
		sleeve.loadPose(arm.storePose());
		sleeve.visible = true;
		String cut = design.cut().getSerializedName();
		collector.submitModelPart(sleeve, poseStack, RenderTypes.entityCutout(CostumeModels.texture("suit", cut, slim, false)), light,
			OverlayTexture.NO_OVERLAY, null, 0xFF000000 | design.primary().getTextureDiffuseColor());
		collector.submitModelPart(sleeve, poseStack, RenderTypes.entityCutout(CostumeModels.texture("suit", cut, slim, true)), light,
			OverlayTexture.NO_OVERLAY, null, 0xFF000000 | design.secondary().getTextureDiffuseColor());
	}
}
