package com.jaymospowers.client.baubles;

import com.jaymospowers.baubles.Baubles;
import com.jaymospowers.baubles.TradeHats;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * (Village mods patch 5) A trade hat ({@link TradeHats}) worn in the head bauble slot, drawn on the head with its own item
 * model, as Villager Hats (PinkGoosik, MIT) draws it: on the head's bone, raised a block and lowered by the hat's height,
 * scaled by its size and turned over to sit right way up. Over a helmet it sits a little higher and wider.
 */
public final class TradeHatLayer {
	// ===== the knobs ================================================================================================
	/** Over a helmet: how much bigger the hat is drawn, and how much higher it sits (blocks). */
	static final float OVER_HELMET_SIZE = 1.12F, OVER_HELMET_LIFT = 0.06F;
	// ================================================================================================================

	/** What a player wears on the head this frame: the hat's model, its size and height. */
	record Shown(ItemStackRenderState item, float size, float height) {
	}

	static final RenderStateDataKey<Shown> KEY = RenderStateDataKey.create(() -> "jaymospowers:trade_hat");

	private TradeHatLayer() {
	}

	public static void init() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Layer(avatar));
			}
		});
	}

	/** From the avatar renderer's extractRenderState: the trade hat this player wears, if any. */
	public static void extract(Avatar entity, AvatarRenderState state) {
		state.setData(KEY, null);
		if (!(entity instanceof Player player)) {
			return;
		}
		List<Identifier> looks = Baubles.looks(player);
		for (Identifier id : looks) {
			TradeHats.Hat hat = TradeHats.of(BuiltInRegistries.ITEM.getValue(id));
			if (hat == null || hat.item == null) {
				continue;
			}
			ItemStackRenderState item = new ItemStackRenderState();
			Minecraft.getInstance().getItemModelResolver().updateForLiving(item, new ItemStack(hat.item), ItemDisplayContext.NONE, player);
			if (!item.isEmpty()) {
				state.setData(KEY, new Shown(item, hat.size, hat.height));
			}
			return;
		}
	}

	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			Shown shown = state.getData(KEY);
			if (shown == null || state.isInvisible) {
				return;
			}
			boolean helmet = !state.headEquipment.isEmpty();
			float size = shown.size() * (helmet ? OVER_HELMET_SIZE : 1.0F);
			float height = shown.height() - (helmet ? OVER_HELMET_LIFT : 0.0F);
			PlayerModel body = this.getParentModel();
			poseStack.pushPose();
			body.root().translateAndRotate(poseStack);
			body.head.translateAndRotate(poseStack);
			poseStack.translate(0.0F, -1.0F - height, 0.0F);
			poseStack.scale(size, size, size);
			poseStack.rotateDegrees(Axis.XP, 180.0F);
			poseStack.rotateDegrees(Axis.YP, 180.0F);
			shown.item().submit(poseStack, collector, lightCoords, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}
}
