package com.jaymospowers.client.power.arsenal;

import com.jaymospowers.power.arsenal.ArsenalChains;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/**
 * Draws Rift Chains for everyone in view: three small rifts around the bound creature with chains shooting out of them
 * onto it; once it's yanked, the rifts close and one chain runs from the caster's hand to it.
 */
final class ArsenalChainsRenderer {
	private static final ItemStackRenderState ITEM = new ItemStackRenderState();
	private static final ItemStack CHAIN = new ItemStack(Items.IRON_CHAIN);
	/** One link every this many blocks, drawn at this scale (an item sprite is half a block across, fixed). */
	private static final double LINK = 0.33;
	private static final float LINK_SCALE = 0.66F;
	private static final double RIFT_RADIUS = 0.42;
	private static final float RIFT_OPEN = 3.0F, RIFT_CLOSE = 5.0F;

	private ArsenalChainsRenderer() {
	}

	static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null) {
			return;
		}
		float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		for (Entity entity : level.entitiesForRendering()) {
			ArsenalChains.Chained chained = entity.getAttached(ArsenalChains.CHAINED);
			if (chained == null || entity.position().distanceToSqr(camera) > 64.0 * 64.0) {
				continue;
			}
			float t = level.getGameTime() - chained.start() + partialTick;
			Vec3 body = entity.getPosition(partialTick).add(0.0, entity.getBbHeight() * 0.55, 0.0);
			float open;
			if (chained.yank() == 0L) {
				open = Mth.clamp(t / RIFT_OPEN, 0.0F, 1.0F);
				float reach = Mth.clamp(t / ArsenalChains.SHOOT, 0.0F, 1.0F);
				for (int i = 0; i < 3; i++) {
					Vec3 rift = chained.rift(i, entity.getBbHeight());
					chain(context, minecraft, rift, rift.lerp(body, reach), camera, i * 7.0F);
				}
			} else {
				float since = level.getGameTime() - chained.yank() + partialTick;
				open = 1.0F - Mth.clamp(since / RIFT_CLOSE, 0.0F, 1.0F);
				if (level.getEntity(chained.owner()) instanceof Player owner) {
					chain(context, minecraft, ArsenalArmPose.handPos(owner, partialTick), body, camera, 0.0F);
				}
			}
			if (open > 0.01F) {
				for (int i = 0; i < 3; i++) {
					Vec3 rift = chained.rift(i, entity.getBbHeight());
					Vec3 normal = body.subtract(rift).normalize();
					Vec3 right = normal.cross(new Vec3(0.0, 1.0, 0.0));
					right = right.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
					Vec3 up = right.cross(normal).normalize();
					RiftPortals.disc(context.submitNodeCollector(), context.poseStack(), rift.subtract(camera), right, up, normal,
						RIFT_RADIUS * open, RIFT_RADIUS * open, t * 0.15 + i, false);
				}
			}
		}
	}

	/** A chain of links from {@code from} to {@code to}, each turned a quarter from the last, like real links. */
	private static void chain(LevelRenderContext context, Minecraft minecraft, Vec3 from, Vec3 to, Vec3 camera, float twist) {
		Vec3 span = to.subtract(from);
		double length = span.length();
		if (length < 0.05) {
			return;
		}
		Vec3 dir = span.scale(1.0 / length);
		Quaternionf along = new Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) dir.x, (float) dir.y, (float) dir.z);
		minecraft.getItemModelResolver().updateForTopItem(ITEM, CHAIN, ItemDisplayContext.FIXED, minecraft.level, null, 0);
		PoseStack poseStack = context.poseStack();
		int links = Math.max(1, (int) Math.ceil(length / LINK));
		for (int i = 0; i < links; i++) {
			Vec3 at = from.add(dir.scale(Math.min(length, (i + 0.5) * LINK))).subtract(camera);
			poseStack.pushPose();
			poseStack.translate(at.x, at.y, at.z);
			poseStack.rotate(along);
			poseStack.rotateDegrees(Axis.YP, twist + (i % 2 == 0 ? 0.0F : 90.0F));
			poseStack.scale(LINK_SCALE, LINK_SCALE, LINK_SCALE);
			ITEM.submit(poseStack, context.submitNodeCollector(), RiftPortals.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
			poseStack.popPose();
		}
	}
}
