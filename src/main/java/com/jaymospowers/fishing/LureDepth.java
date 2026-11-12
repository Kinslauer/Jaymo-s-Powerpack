package com.jaymospowers.fishing;

import com.jaymospowers.JaymosPowers;
import java.util.Locale;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;

/**
 * Track B's leftover, lure depth (Advanced Fishing's idea, our own code): a lure doesn't float. Cast it, and it sinks
 * {@link #LURE_SINK} blocks a tick for as long as you leave it, so waiting before you reel takes it down to the fish that
 * keep deep (a zander or a halibut on the bottom, a pike in the middle; {@link Species#depth}); a ledger rod's bait sinks
 * straight to the bottom ({@link #LEDGER_SINK}). How far down it is shows on the action bar. Fish only come to bait near
 * their own depth ({@link FishBites#DEPTH_REACH}).
 *
 * <p>The depth (blocks under the top of the water; negative once it's on the bottom) lives on the hook, synced to everyone,
 * so every client draws the hook where it really is.
 */
public final class LureDepth {
	/** Blocks a tick a lure sinks, and a ledger rod's weighted bait. */
	static final float LURE_SINK = 0.05F, LEDGER_SINK = 0.15F;
	/** How far over the bed the hook stops. */
	static final double OFF_BOTTOM = 0.2;
	/** How often (ticks) the depth is sent, and shown to its angler. */
	static final int SYNC_EVERY = 5, TELL_EVERY = 20;
	/** Deeper than this the line runs out. */
	static final float MOST = 40.0F;

	public static final AttachmentType<Float> DEPTH = AttachmentRegistry.create(JaymosPowers.id("lure_depth"),
		builder -> builder.syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.all()));

	private LureDepth() {
	}

	/** How far down {@code hook}'s lure or bait has sunk (0 while it floats). */
	public static float depth(FishingHook hook) {
		Float d = hook.getAttached(DEPTH);
		return d == null ? 0.0F : Math.abs(d);
	}

	/** {@code hook}'s lure or bait lies on the bottom. */
	public static boolean onBottom(FishingHook hook) {
		Float d = hook.getAttached(DEPTH);
		return d != null && d < 0.0F;
	}

	/** The top of the water over {@code pos}, or NaN if {@code pos} isn't in water. */
	static double surface(Level level, BlockPos pos) {
		if (!level.getFluidState(pos).is(FluidTags.WATER)) {
			return Double.NaN;
		}
		BlockPos.MutableBlockPos at = pos.mutable();
		for (int i = 0; i < 64 && level.getFluidState(at.above()).is(FluidTags.WATER); i++) {
			at.move(0, 1, 0);
		}
		FluidState top = level.getFluidState(at);
		return at.getY() + top.getHeight(level, at);
	}

	/** The bed under the top of the water at {@code x, z} (from {@code from}, a water block). */
	static double bottom(Level level, BlockPos from) {
		BlockPos.MutableBlockPos at = from.mutable();
		for (int i = 0; i < 64 && level.getFluidState(at.below()).is(FluidTags.WATER); i++) {
			at.move(0, -1, 0);
		}
		return at.getY() + OFF_BOTTOM;
	}

	/**
	 * After the hook's own tick (both sides): on the server, sink a lure or a ledger rod's bait a little further; then on
	 * both, hold the hook at its depth instead of letting it bob back up.
	 */
	public static void tick(FishingHook hook) {
		Level level = hook.level();
		BlockPos pos = hook.blockPosition();
		double top = surface(level, pos);
		if (Double.isNaN(top)) {
			top = surface(level, pos.below());
		}
		if (Double.isNaN(top)) {
			return;
		}
		if (!level.isClientSide() && hook.getPlayerOwner() instanceof ServerPlayer player && !FishingReal.fight(player).fighting()) {
			Tackle.Rod rod = Tackle.rodOf(player);
			Item onHook = HookBait.bait(player);
			float sink = rod.bottom() ? LEDGER_SINK : Tackle.isLure(onHook) && rod.lures ? LURE_SINK : 0.0F;
			if (sink > 0.0F) {
				float was = depth(hook);
				double floor = top - bottom(level, BlockPos.containing(hook.getX(), top - 0.5, hook.getZ()));
				float now = (float) Math.min(Math.min(was + sink, floor), MOST);
				boolean bed = now >= floor - 1.0E-3;
				if (hook.tickCount % SYNC_EVERY == 0 || bed && !onBottom(hook)) {
					hook.setAttached(DEPTH, bed ? -now : now);
				}
				if (hook.tickCount % TELL_EVERY == 0) {
					player.sendOverlayMessage(Component.translatable(bed ? "message.jaymospowers.fishing.lure_bottom"
						: "message.jaymospowers.fishing.lure_depth", String.format(Locale.ROOT, "%.1f", now)).withStyle(ChatFormatting.AQUA));
				}
			}
		}
		float d = depth(hook);
		if (d > 0.0F) {
			double y = top - d;
			hook.setPos(hook.getX(), y, hook.getZ());
			hook.setDeltaMovement(hook.getDeltaMovement().multiply(0.5, 0.0, 0.5));
		}
	}

	/** Whether {@code player}'s line goes deep (a lure on a rod that works it, or a ledger rod). */
	static boolean sinks(Player player) {
		Tackle.Rod rod = Tackle.rodOf(player);
		return rod.bottom() || rod.lures && Tackle.isLure(HookBait.bait(player));
	}
}
