package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Lurk takes on the look of what it touches: the colour of the wall against it, or the ground under it, drawn over its
 * skin on every client ({@link #TINT}, ARGB). The alpha is how much of it can be seen: all of it, a faint outline while
 * it moves unseen, none while it holds still unseen (the client draws nothing then). The rules for when it is unseen
 * are in {@code Lurk}.
 */
public final class LurkBlend {
	// ---- tuning ----
	/** How often the surroundings are looked at (ticks), and how much of the old colour stays when a new one is seen. */
	public static final int SAMPLE_EVERY = 5;
	public static final float KEEP_OLD = 0.5F;
	/** How far the block's colour is lightened toward white before it is laid over the dark skin (0-1): the skin multiplies it. */
	public static final float WHITEN = 0.45F;
	/** How much of Lurk shows while it moves unseen (alpha 0-255). */
	public static final int FAINT_ALPHA = 0x40;

	/** The look laid over Lurk, ARGB, synced to everyone. Absent: its own colours. Not saved. */
	public static final AttachmentType<Integer> TINT = AttachmentRegistry.create(JaymosPowers.id("lurk_tint"),
		builder -> builder.syncWith(ByteBufCodecs.INT, AttachmentSyncPredicate.all()));

	private LurkBlend() {
	}

	/** The map colour (RGB) of the wall {@code player} is against, else of the ground under it; -1 if neither gives one. */
	public static int sample(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos feet = player.blockPosition();
		AABB box = player.getBoundingBox().inflate(0.2, 0.0, 0.2);
		for (Direction side : Direction.Plane.HORIZONTAL) {
			for (int up = 0; up < 2; up++) {
				BlockPos at = feet.relative(side).above(up);
				BlockState state = level.getBlockState(at);
				if (!state.isAir() && !state.getCollisionShape(level, at).isEmpty() && box.intersects(new AABB(at))) {
					int rgb = state.getMapColor(level, at).col;
					if (rgb != 0) {
						return rgb;
					}
				}
			}
		}
		BlockPos below = feet.below();
		BlockState ground = level.getBlockState(below);
		if (!ground.isAir()) {
			int rgb = ground.getMapColor(level, below).col;
			return rgb != 0 ? rgb : -1;
		}
		return -1;
	}

	/** {@code old} (RGB) moved toward {@code seen} by {@code 1 - KEEP_OLD}. */
	public static int mix(int old, int seen) {
		if (seen < 0) {
			return old;
		}
		int r = Mth.lerpInt(1.0F - KEEP_OLD, old >> 16 & 0xFF, seen >> 16 & 0xFF);
		int g = Mth.lerpInt(1.0F - KEEP_OLD, old >> 8 & 0xFF, seen >> 8 & 0xFF);
		int b = Mth.lerpInt(1.0F - KEEP_OLD, old & 0xFF, seen & 0xFF);
		return r << 16 | g << 8 | b;
	}

	/** The tint for a surrounding colour {@code rgb} at {@code alpha}: lightened, so the dark skin keeps some of it. */
	public static int tint(int rgb, int alpha) {
		int r = Mth.lerpInt(WHITEN, rgb >> 16 & 0xFF, 255);
		int g = Mth.lerpInt(WHITEN, rgb >> 8 & 0xFF, 255);
		int b = Mth.lerpInt(WHITEN, rgb & 0xFF, 255);
		return alpha << 24 | r << 16 | g << 8 | b;
	}
}
