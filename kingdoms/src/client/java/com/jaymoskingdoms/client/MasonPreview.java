package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.MasonTable;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The plot a written building plan takes, outlined while it is held: before it is laid, the box it would be laid in at
 * the block the crosshair is on (its anchor is the spot against the face looked at, as laying puts it); once laid in
 * this dimension, the box it was laid in. The twelve edges are thin bars with a faint fill on the floor, and the
 * design's north edge (turned with the plan) is drawn heavier, so turning the plan with sneak-use shows.
 */
public final class MasonPreview {
	/** The colours (ARGB, see-through): the unlaid preview (PlanGhosts' pale blue), the laid plot (warmer), the floor fills. */
	static final int PREVIEW = 0xC0000000 | PlanGhosts.MISSING & 0xFFFFFF, LAID = 0xE0FFC060, FILL_ALPHA = 0x30;
	/** How thick the edge bars are, and the heavier north edge (blocks). */
	static final float BAR = 0.06F, FRONT_BAR = 0.16F;
	/** How far the outline sits outside the box and the floor fill above it (blocks), so neither flickers against blocks. */
	static final float OUT = 0.01F, FLOOR = 0.02F;

	private static final Identifier WHITE = JaymosKingdoms.id("textures/astronomy/white.png");

	private MasonPreview() {
	}

	static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(MasonPreview::render);
	}

	private static MasonTable.@Nullable Plot held(LocalPlayer player) {
		for (ItemStack stack : new ItemStack[] {player.getMainHandItem(), player.getOffhandItem()}) {
			MasonTable.Plot plot = MasonTable.plot(stack);
			if (plot != null) {
				return plot;
			}
		}
		return null;
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.level == null || minecraft.player == null) {
			return;
		}
		MasonTable.Plot plot = held(minecraft.player);
		if (plot == null) {
			return;
		}
		BlockPos anchor;
		boolean laid = plot.anchor() != null;
		if (laid) {
			if (!plot.dimension().equals(minecraft.level.dimension().identifier().toString())) {
				return;
			}
			anchor = plot.anchor();
		} else if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			anchor = hit.getBlockPos().relative(hit.getDirection());
		} else {
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float x0 = (float) (anchor.getX() - camera.x) - OUT, y0 = (float) (anchor.getY() - camera.y) - OUT, z0 = (float) (anchor.getZ() - camera.z) - OUT;
		float x1 = x0 + plot.width() + 2 * OUT, y1 = y0 + plot.height() + 2 * OUT, z1 = z0 + plot.depth() + 2 * OUT;
		int colour = laid ? LAID : PREVIEW;
		PoseStack poseStack = context.poseStack();
		poseStack.pushPose();
		context.submitNodeCollector().submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(WHITE), (pose, buffer) -> {
			// the floor fill
			int fill = FILL_ALPHA << 24 | colour & 0xFFFFFF;
			quad(buffer, pose, x0, y0 + FLOOR, z0, x1, y0 + FLOOR, z0, x1, y0 + FLOOR, z1, x0, y0 + FLOOR, z1, fill);
			for (float y : new float[] {y0, y1}) {
				bar(buffer, pose, x0, y, z0, x1, y, z0, colour, BAR);
				bar(buffer, pose, x0, y, z1, x1, y, z1, colour, BAR);
				bar(buffer, pose, x0, y, z0, x0, y, z1, colour, BAR);
				bar(buffer, pose, x1, y, z0, x1, y, z1, colour, BAR);
			}
			for (float x : new float[] {x0, x1}) {
				for (float z : new float[] {z0, z1}) {
					bar(buffer, pose, x, y0, z, x, y1, z, colour, BAR);
				}
			}
			// the design's north edge, turned with the plan: north, east, south, west
			switch (plot.turns()) {
				case 0 -> bar(buffer, pose, x0, y0, z0, x1, y0, z0, colour, FRONT_BAR);
				case 1 -> bar(buffer, pose, x1, y0, z0, x1, y0, z1, colour, FRONT_BAR);
				case 2 -> bar(buffer, pose, x0, y0, z1, x1, y0, z1, colour, FRONT_BAR);
				default -> bar(buffer, pose, x0, y0, z0, x0, y0, z1, colour, FRONT_BAR);
			}
		});
		poseStack.popPose();
	}

	/** A bar of thickness {@code t} centred on the line from (ax, ay, az) to (bx, by, bz): a box, its six faces. */
	private static void bar(VertexConsumer buffer, PoseStack.Pose pose, float ax, float ay, float az, float bx, float by, float bz, int colour, float t) {
		float h = t * 0.5F;
		float lx = Math.min(ax, bx) - h, ux = Math.max(ax, bx) + h, ly = Math.min(ay, by) - h, uy = Math.max(ay, by) + h;
		float lz = Math.min(az, bz) - h, uz = Math.max(az, bz) + h;
		quad(buffer, pose, lx, ly, lz, ux, ly, lz, ux, ly, uz, lx, ly, uz, colour);
		quad(buffer, pose, lx, uy, lz, ux, uy, lz, ux, uy, uz, lx, uy, uz, colour);
		quad(buffer, pose, lx, ly, lz, ux, ly, lz, ux, uy, lz, lx, uy, lz, colour);
		quad(buffer, pose, lx, ly, uz, ux, ly, uz, ux, uy, uz, lx, uy, uz, colour);
		quad(buffer, pose, lx, ly, lz, lx, ly, uz, lx, uy, uz, lx, uy, lz, colour);
		quad(buffer, pose, ux, ly, lz, ux, ly, uz, ux, uy, uz, ux, uy, lz, colour);
	}

	/** One quad, drawn both ways round so it shows from either side. */
	private static void quad(VertexConsumer buffer, PoseStack.Pose pose, float x0, float y0, float z0, float x1, float y1, float z1,
		float x2, float y2, float z2, float x3, float y3, float z3, int colour) {
		vertex(buffer, pose, x0, y0, z0, colour);
		vertex(buffer, pose, x1, y1, z1, colour);
		vertex(buffer, pose, x2, y2, z2, colour);
		vertex(buffer, pose, x3, y3, z3, colour);
		vertex(buffer, pose, x3, y3, z3, colour);
		vertex(buffer, pose, x2, y2, z2, colour);
		vertex(buffer, pose, x1, y1, z1, colour);
		vertex(buffer, pose, x0, y0, z0, colour);
	}

	private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, int colour) {
		buffer.addVertex(pose, x, y, z).setColor(colour).setUv(0.5F, 0.5F)
			.setOverlay(OverlayTexture.NO_OVERLAY).setLight(15728880).setNormal(pose, 0.0F, 1.0F, 0.0F);
	}
}
