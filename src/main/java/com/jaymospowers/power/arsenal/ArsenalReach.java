package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Reach Through as an animation, shared by both sides. A portal tears open in front of the weapon hand, the arm reaches
 * in, then hauls the weapon out of it, and the portal snaps shut:
 * <pre>
 *   0 ─ OPEN_END ─ REACH_END ─ PULL_END ─ CLOSE_END   (ticks since the rift opened)
 *   opening   arm reaches in   weapon drawn out   closing
 * </pre>
 * The server hands the weapon over at {@link #PULL_END}. Everyone sees it through the synced {@link #REACH}.
 */
public final class ArsenalReach {
	public static final int OPEN_END = 6;
	public static final int REACH_END = 12;
	public static final int PULL_END = 22;
	public static final int CLOSE_END = 28;

	/** Portal radius when fully open (blocks), and where it opens relative to the body. */
	public static final double PORTAL_RADIUS = 0.55;
	private static final double PORTAL_UP = 1.35;
	private static final double PORTAL_FORWARD = 1.0;
	private static final double PORTAL_SIDE = 0.4;

	/** A rift that opened at game time {@code start}; {@code weapon} is what's coming out of it (for show). */
	public record Reach(long start, ItemStack weapon) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Reach> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Reach::start,
			ItemStack.OPTIONAL_STREAM_CODEC, Reach::weapon,
			Reach::new);
	}

	public static final AttachmentType<Reach> REACH = AttachmentRegistry.create(JaymosPowers.id("arsenal_reach"),
		builder -> builder.syncWith(Reach.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private ArsenalReach() {
	}

	public static void init() {
		// (the attachment registers itself when this class loads)
	}

	/** +1 when the main hand is the right one. */
	public static int side(Player player) {
		return player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
	}

	/** The body's forward and right (horizontal unit vectors) for a body yaw in degrees. */
	public static Vec3 forward(float bodyYaw) {
		float r = bodyYaw * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.sin(r), 0.0, Mth.cos(r));
	}

	public static Vec3 right(float bodyYaw) {
		float r = bodyYaw * Mth.DEG_TO_RAD;
		return new Vec3(-Mth.cos(r), 0.0, -Mth.sin(r));
	}

	/** Where the portal's centre is, in front of the weapon hand at shoulder height. */
	public static Vec3 portalCenter(Player player, float partialTick) {
		float bodyYaw = Mth.lerp(partialTick, player.yBodyRotO, player.yBodyRot);
		double scale = player.getScale();
		return player.getPosition(partialTick)
			.add(0.0, PORTAL_UP * scale, 0.0)
			.add(forward(bodyYaw).scale(PORTAL_FORWARD * scale))
			.add(right(bodyYaw).scale(side(player) * PORTAL_SIDE * scale));
	}

	/** The portal's size (0-1 of full) at {@code t} ticks: it bursts open, holds, then snaps shut. */
	public static float openness(float t) {
		if (t < OPEN_END) {
			float x = t / OPEN_END;
			// overshoots a touch as it tears open
			return (float)Math.min(1.12, 1.0 - Math.pow(1.0 - x, 3.0) + Math.sin(x * Math.PI) * 0.12);
		}
		if (t < PULL_END) {
			return 1.0F;
		}
		return Math.max(0.0F, 1.0F - (t - PULL_END) / (float)(CLOSE_END - PULL_END));
	}
}
