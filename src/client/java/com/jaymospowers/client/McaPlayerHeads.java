package com.jaymospowers.client;

import com.jaymospowers.client.mixin.LivingRendererScaleInvoker;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.ResolvableProfile;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

/**
 * A player's head (the item, set down as a block, or worn: a captain's trophy, a bounty head) drawn as the villager MCA
 * draws that player as, instead of the skin on their account. Andy (2026-11-22): "looks like mob heads take the default
 * player skin instead of our MCA rewrites". The game draws every player head as a plain skull in the account's skin, so
 * the head a captain took off you wasn't your face. Only for a player MCA knows a look for ({@code McaLooks}); anyone
 * else's head is drawn as before.
 * <p>The skull is drawn in a player head's own model space (the neck at the origin, the head 8 units up from it), which is
 * where the villager's renderer puts its head too, once its turn, flip, scale and lift are undone: so the villager is drawn with
 * only its head showing ({@link Dismembered#ONLY}), on the skull's place.
 */
public final class McaPlayerHeads {
	private static final CameraRenderState NO_CAMERA = new CameraRenderState();

	private McaPlayerHeads() {
	}

	/** The id of the player {@code profile} is for, or null for none. */
	public static @Nullable UUID id(@Nullable ResolvableProfile profile) {
		if (profile == null) {
			return null;
		}
		UUID id = profile.partialProfile().id();
		return id == null || id.equals(net.minecraft.util.Util.NIL_UUID) ? null : id;
	}

	/**
	 * Draws the head of the villager MCA draws player {@code id} as, where a player head's skull would be drawn with this
	 * {@code poseStack}. False (nothing drawn) when there's no such villager: the caller draws the skull as usual.
	 */
	public static boolean submit(@Nullable UUID id, PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int outlineColor) {
		if (id == null) {
			return false;
		}
		Entity look = com.jaymoskitchen.client.corpse.McaLooks.of(Optional.of(id));
		if (!(look instanceof LivingEntity villager)) {
			return false;
		}
		Minecraft minecraft = Minecraft.getInstance();
		EntityRenderer<? super Entity, ?> renderer = minecraft.getEntityRenderDispatcher().getRenderer(villager);
		EntityRenderState state = renderer.createRenderState(villager, minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
		state.nameTag = null;
		state.displayFireAnimation = false;
		state.lightCoords = lightCoords;
		state.outlineColor = outlineColor;
		state.setData(Dismembered.ONLY, com.jaymospowers.core.Dismember.HEAD);
		if (state instanceof LivingEntityRenderState living) {
			living.hasRedOverlay = false;
			living.deathTime = 0.0F;
			living.walkAnimationPos = 0.0F;
			living.walkAnimationSpeed = 0.0F;
			living.bodyRot = 0.0F;
			living.yRot = 0.0F;
			living.xRot = 0.0F;
			living.scale = 1.0F;
		}
		// the renderer's way from its feet to its model (LivingEntityRenderer#submit, facing 0): a half turn, the flip, its own
		// scale (MCA's genes) and the lift; undone, its head's model space lies on the skull's
		Matrix4f toModel = new Matrix4f().rotateY((float)Math.PI).scale(-1.0F, -1.0F, 1.0F);
		if (renderer instanceof LivingRendererScaleInvoker scaling && state instanceof LivingEntityRenderState living) {
			PoseStack own = new PoseStack();
			scaling.jaymospowers$scale(living, own);
			toModel.mul(own.last().pose());
		}
		toModel.translate(0.0F, -1.501F, 0.0F);
		poseStack.pushPose();
		poseStack.mulPose(toModel.invert());
		submitBody(renderer, state, poseStack, collector);
		poseStack.popPose();
		return true;
	}

	@SuppressWarnings("unchecked")
	private static <S extends EntityRenderState> void submitBody(EntityRenderer<?, S> renderer, EntityRenderState state, PoseStack poseStack,
		SubmitNodeCollector collector) {
		renderer.submit((S) state, poseStack, collector, NO_CAMERA);
	}
}
