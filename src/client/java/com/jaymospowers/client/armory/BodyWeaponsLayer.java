package com.jaymospowers.client.armory;

import com.jaymospowers.armory.BodyWeapons;
import com.jaymospowers.armory.BodyWeapons.Home;
import com.jaymospowers.client.BodyCover;
import com.jaymospowers.client.Dismembered;
import com.jaymospowers.client.backpack.BackpackClient;
import com.jaymospowers.client.menu.MenuTabs;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Weapons in their set places on the body ({@link BodyWeapons}), drawn on every player from what the server syncs. Each
 * item is drawn as if held in a hand placed at its home: a virtual arm at the home is turned (pitch, then twist about the
 * blade, then roll across the body) and the item drawn as the game draws it in that hand, so every weapon's own held model
 * is used (WeaponMod's guns, our flintlocks, swords). Over a chestplate, leggings or a costume each item sits further out
 * from the body; back items sit out over a worn backpack. The Character page (H) has the toggle for free placement.
 * <p>NPCs the same way, on any humanoid body (an MCA villager's, a guard's): what Jaymo's Kingdoms says they carry
 * ({@link BodyWeapons#carry}), extracted from the living renderer's state.
 */
public final class BodyWeaponsLayer {
	// ===== the knobs ================================================================================================
	/**
	 * Each home, in {@link Home} order: where the grip sits (body pixels: x to the body's left, y down from the neck, z
	 * back), the virtual arm's pitch (degrees: 0 points the blade forward, 90 straight down, 115 down and back, -90 up), its
	 * twist about the blade (90 turns the flat of a gun or blade to face front or back), its roll across the body, the size,
	 * and which way "out" is (x, z) for armour and backpack clearance.
	 */
	static final float[][] HOMES = {
		// x,     y,     z,    pitch, twist, roll, size, out x, out z
		{4.6F, 11.0F, -0.5F, 115.0F, 0.0F, 8.0F, 0.85F, 1.0F, 0.0F},     // HIP_LEFT: sword hung from the belt, hilt forward
		{-4.6F, 11.0F, -0.5F, 115.0F, 0.0F, -8.0F, 0.85F, -1.0F, 0.0F},  // HIP_RIGHT
		{2.2F, 3.5F, -2.7F, 90.0F, 90.0F, 55.0F, 0.6F, 0.0F, -1.0F},     // CHEST_LEFT: pistol on the strap, barrel down across
		{-2.2F, 3.5F, -2.7F, 90.0F, 90.0F, -55.0F, 0.6F, 0.0F, -1.0F},   // CHEST_RIGHT
		{2.4F, 10.0F, -2.7F, 90.0F, 90.0F, 25.0F, 0.6F, 0.0F, -1.0F},    // SASH_LEFT: pistol tucked in the sash at the front
		{-2.4F, 10.0F, -2.7F, 90.0F, 90.0F, -25.0F, 0.6F, 0.0F, -1.0F},  // SASH_RIGHT
		{1.5F, 7.0F, 2.8F, -90.0F, 90.0F, -30.0F, 0.9F, 0.0F, 1.0F},     // BACK_LEFT: long gun or blade, muzzle up over the shoulder
		{-1.5F, 7.0F, 2.8F, -90.0F, 90.0F, 30.0F, 0.9F, 0.0F, 1.0F},     // BACK_RIGHT
		{0.0F, 6.0F, 3.6F, 90.0F, 90.0F, 0.0F, 0.9F, 0.0F, 1.0F},        // BACK_SHIELD: over the crossed guns
		{0.0F, 10.5F, 2.8F, 90.0F, 90.0F, 90.0F, 0.7F, 0.0F, 1.0F},      // BACK_TOOL: across the small of the back
		{-2.0F, -0.5F, 3.4F, 90.0F, 90.0F, -30.0F, 0.9F, 0.0F, 1.0F},    // TWIN_OUTER: the silver blade, hilt up over the right shoulder, point to the left hip
		{-5.0F, 1.2F, 2.8F, 90.0F, 90.0F, -24.0F, 0.9F, 0.0F, 1.0F},     // TWIN_INNER: the second blade, beside it toward the shoulder, under it
	};
	/** How much further out (pixels) over a chestplate or leggings, over a costume's suit, and (back) over a backpack. */
	static final float OVER_ARMOUR = 1.2F, OVER_COSTUME = 0.5F, OVER_BACKPACK = 5.0F;
	// ================================================================================================================

	/** One player's homes this frame: each home's item, ready to draw (null where empty). */
	record Shown(@Nullable ItemStackRenderState[] items) {
	}

	static final RenderStateDataKey<Shown> KEY = RenderStateDataKey.create(() -> "jaymospowers:body_weapons");

	private BodyWeaponsLayer() {
	}

	public static void init() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			// (players' bodies, and every humanoid NPC's)
			if (renderer instanceof AvatarRenderer<?> || renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(layer(renderer));
			}
		});
		MenuTabs.characterLink(() -> {
			Player player = Minecraft.getInstance().player;
			return player == null ? null
				: Component.translatable(BodyWeapons.free(player) ? "menu.jaymospowers.body_weapons.free" : "menu.jaymospowers.body_weapons.set");
		}, () -> ClientPlayNetworking.send(new BodyWeapons.Toggle()));
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Layer<?> layer(LivingEntityRenderer<?, ?, ?> renderer) {
		return new Layer((RenderLayerParent) renderer);
	}

	/** From the avatar renderer's extractRenderState: each home's item model. */
	public static void extract(Avatar entity, AvatarRenderState state) {
		extractBody(entity, state);
	}

	/** From the living renderer's extractRenderState, for an NPC's body (a player's is {@link #extract}'s). */
	public static void extractNpc(LivingEntity entity, LivingEntityRenderState state) {
		if (!(entity instanceof Avatar) && state instanceof HumanoidRenderState) {
			extractBody(entity, state);
		}
	}

	private static void extractBody(LivingEntity entity, LivingEntityRenderState state) {
		List<ItemStack> homes = BodyWeapons.shown(entity);
		if (homes.isEmpty()) {
			state.setData(KEY, null);
			return;
		}
		ItemStackRenderState[] items = new ItemStackRenderState[Math.min(homes.size(), HOMES.length)];
		boolean any = false;
		for (int i = 0; i < items.length; i++) {
			ItemStack stack = homes.get(i);
			if (stack.isEmpty()) {
				continue;
			}
			ItemStackRenderState item = new ItemStackRenderState();
			Minecraft.getInstance().getItemModelResolver().updateForLiving(item, stack, context(i), entity);
			if (!item.isEmpty()) {
				items[i] = item;
				any = true;
			}
		}
		state.setData(KEY, any ? new Shown(items) : null);
	}

	/** The hand a home's item is drawn in: the left hand's model for homes on the body's left (+x). */
	private static ItemDisplayContext context(int home) {
		return HOMES[home][0] > 0.0F ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
	}

	static final class Layer<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		Layer(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			Shown shown = state.getData(KEY);
			// (nor on a severed head or limb, drawn as the whole body with only that part showing)
			if (shown == null || state.isInvisible || state.getData(Dismembered.ONLY) != null) {
				return;
			}
			HumanoidModel<S> body = this.getParentModel();
			boolean costume = BodyCover.body(state);
			boolean backpack = state instanceof AvatarRenderState avatar && BackpackClient.worn(avatar);
			poseStack.pushPose();
			body.root().translateAndRotate(poseStack);
			body.body.translateAndRotate(poseStack);
			for (int i = 0; i < shown.items().length; i++) {
				ItemStackRenderState item = shown.items()[i];
				if (item == null) {
					continue;
				}
				float[] h = HOMES[i];
				boolean low = h[1] >= 9.0F;
				boolean armour = low ? !state.legsEquipment.isEmpty() || !state.chestEquipment.isEmpty() : !state.chestEquipment.isEmpty();
				float out = (armour ? OVER_ARMOUR : 0.0F) + (costume ? OVER_COSTUME : 0.0F) + (h[8] > 0.0F && backpack ? OVER_BACKPACK : 0.0F);
				poseStack.pushPose();
				poseStack.translate((h[0] + h[7] * out) / 16.0F, h[1] / 16.0F, (h[2] + h[8] * out) / 16.0F);
				// the virtual arm: roll across the body, twist about the blade, pitch from forward to its lie
				poseStack.rotateDegrees(Axis.ZP, h[5]);
				poseStack.rotateDegrees(Axis.YP, h[4]);
				poseStack.rotateDegrees(Axis.XP, h[3]);
				// the game's own turn from an arm to the item in its hand (ItemInHandLayer), less the arm's length
				poseStack.rotateDegrees(Axis.XP, -90.0F);
				poseStack.rotateDegrees(Axis.YP, 180.0F);
				poseStack.translate((h[0] > 0.0F ? -1.0F : 1.0F) / 16.0F, 0.125F, 0.0F);
				poseStack.scale(h[6], h[6], h[6]);
				item.submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, 0);
				poseStack.popPose();
			}
			poseStack.popPose();
		}
	}
}
