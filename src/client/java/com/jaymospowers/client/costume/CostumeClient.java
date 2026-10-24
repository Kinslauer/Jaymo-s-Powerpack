package com.jaymospowers.client.costume;

import com.jaymospowers.client.JaymosPowersClient;
import com.jaymospowers.client.power.web.WebSuitGrowth;
import com.jaymospowers.client.power.web.WebSymbioteClient;
import com.jaymospowers.client.power.xenoform.XenoformClient;
import com.jaymospowers.costume.Costume;
import com.jaymospowers.costume.CostumeDesign;
import com.jaymospowers.costume.CostumeNet;
import com.jaymospowers.costume.CostumeTailoring;
import com.jaymospowers.costume.SecretIdentity;
import com.jaymospowers.power.web.WebSymbiote;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.KeyMapping;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
 * <p>
 * (0.12.3) The mask key ({@link #maskKey}, apostrophe by default: every letter is taken by Powers, Kingdoms or
 * vanilla) takes the costume's head piece off and puts it back ({@code Costume.toggleMask}); with it off
 * ({@code Costume.UNMASKED}) the head piece isn't drawn and the face shows.
 * <p>
 * (0.14.3) A hood up ({@code Costume.hooded}) leaves the face and the real name showing, but someone else's name tag
 * only within {@link #HOOD_NAME_RANGE}.
 */
public final class CostumeClient {
	/** (0.14.3) A hooded player's name tag is shown only to viewers within this many blocks. */
	public static final double HOOD_NAME_RANGE = 8.0;
	/** The costume to draw on this player, or null. */
	public static final RenderStateDataKey<CostumeDesign> KEY = RenderStateDataKey.create(() -> "jaymospowers:costume");

	/** While the designer draws its preview: the design shown on the local player in place of what they wear. */
	private static @Nullable CostumeDesign preview;
	private static CostumeModels.@Nullable Set handModels;
	/** (0.12.3) The mask key. */
	private static KeyMapping maskKey;

	private CostumeClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(CostumeNet.Open.TYPE, (payload, context) ->
			context.client().gui.setScreen(new CostumeScreen(payload.pos())));
		// (0.11.78) a tailor's order: the designer, paid in emeralds
		ClientPlayNetworking.registerGlobalReceiver(CostumeTailoring.Open.TYPE, (payload, context) ->
			context.client().gui.setScreen(CostumeScreen.tailored(payload.price())));
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Layer(avatar));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(CostumeEmblems::tick);
		maskKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.jaymospowers.costume_mask", InputConstants.KEY_APOSTROPHE,
			JaymosPowersClient.CATEGORY));
		ClientTickEvents.END_CLIENT_TICK.register(CostumeClient::maskTick);
	}

	/** (0.12.3) The mask key, for the key guide. */
	public static KeyMapping maskKey() {
		return maskKey;
	}

	/** (0.12.3) Asks the server to take the mask off or put it back on (the key and the designer's button). */
	static void toggleMask() {
		ClientPlayNetworking.send(new CostumeNet.ToggleMask());
	}

	/** Each press of the mask key (outside screens) flips the mask; the server checks there's one to flip. */
	private static void maskTick(Minecraft minecraft) {
		boolean pressed = false;
		while (maskKey.consumeClick()) {
			pressed = true;
		}
		if (pressed && minecraft.player != null && minecraft.getConnection() != null && minecraft.gui.screen() == null) {
			toggleMask();
		}
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
			if (design != null && design != preview && design.mask() != CostumeDesign.Mask.NONE && Costume.unmasked(player)) {
				// (0.12.3) the mask is off: everything but the head piece is drawn
				design = design.withMask(CostumeDesign.Mask.NONE);
			}
		}
		state.setData(KEY, design);
		// (0.11.78) filled in again as it is drawn (CostumeBody), never left over from another frame
		state.setData(CostumeBody.BODY, null);
		state.setData(CostumeBody.PARENT, null);
		state.setData(CostumeCover.KEY, null);
		if (entity instanceof Player player && state.nameTag != null) {
			// (0.11.70) a hidden face: the costume's name over their head, not theirs (nor their score)
			Component masked = SecretIdentity.maskedName(player);
			if (masked != null) {
				state.nameTag = masked;
				state.scoreText = null;
			} else if (player != Minecraft.getInstance().player && Costume.hooded(player)) {
				// (0.14.3) a hood up: their real name, but only when you are close (never your own)
				LocalPlayer me = Minecraft.getInstance().player;
				if (me == null || me.distanceToSqr(player) > HOOD_NAME_RANGE * HOOD_NAME_RANGE) {
					state.nameTag = null;
					state.scoreText = null;
				}
			}
		}
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
	public static boolean covered(AvatarRenderState state) {
		if (state.getData(XenoformClient.FORM) != null || state.getData(WebSymbioteClient.SUIT) != null || state.getData(WebSuitGrowth.KEY) != null) {
			return true;
		}
		return WebSuitGrowth.bigBody(state);
	}

	/** Draws the worn costume over a player, piece by piece, each piece once per colour. */
	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		private final CostumeModels.Set models;
		private final RenderLayerParent<AvatarRenderState, PlayerModel> renderer;

		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
			this.renderer = renderer;
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
			// (0.11.78) the costume follows the model this player is drawn with now (CostumeBody): MCA swaps it per player
			PlayerModel parent = getParentModel();
			state.setData(CostumeBody.PARENT, parent);
			boolean slim = CostumeBody.slim(parent, state.skin.model() == PlayerModelType.SLIM);
			String cut = design.cut().getSerializedName();
			CostumeModels.Worn suit = this.models.suit(design.cut(), slim);
			Identifier suitPrimary = CostumeModels.texture("suit", cut, slim, false), suitSecondary = CostumeModels.texture("suit", cut, slim, true);
			renderColoredCutoutModel(suit, suitPrimary, poseStack, collector, lightCoords, state, primary, 1);
			renderColoredCutoutModel(suit, suitSecondary, poseStack, collector, lightCoords, state, secondary, 1);
			// (0.15.34) the skin under what the suit covers isn't drawn (CostumeCover)
			CostumeCover.mark(state, this.renderer, suitPrimary, suitSecondary);
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
		boolean slim = CostumeBody.slimArm(arm, player.getSkin().model() == PlayerModelType.SLIM);
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
