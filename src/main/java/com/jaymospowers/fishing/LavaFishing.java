package com.jaymospowers.fishing;

import com.jaymospowers.tinkering.TinkerMaterial;
import com.jaymospowers.tinkering.ToolStats;
import com.jaymospowers.tinkering.ranged.TinkerRodItem;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;

/**
 * Fishing the fire (Andy picked it from the Tide list). A hook that lands in lava burns up unless the rod is heat-proof: a
 * Tinkering rod whose limb and hook are both forged of something that takes the heat ({@link #HEAT_PROOF}; the iron rule, so
 * no plain iron or wood will do). A heat-proof rod's hook floats on the lava as a bobber floats on water, and the Ash's
 * lava fish ({@link LavaFish}) come to it as they would to any baited hook ({@link FishBites}); water fish never come to a
 * hook in lava, nor lava fish to one in water.
 */
public final class LavaFishing {
	/** The materials that take a lava lake's heat: the seared and black stones, the hot-forged alloys, a blaze rod, a fire dragon's bone. */
	static final Set<TinkerMaterial> HEAT_PROOF = EnumSet.of(TinkerMaterial.SEARED_STONE, TinkerMaterial.ROCK_BLACKSTONE,
		TinkerMaterial.CINDER_BRONZE, TinkerMaterial.BLAZE_ROD, TinkerMaterial.COBALT, TinkerMaterial.MANYULLYN,
		TinkerMaterial.HEPATIZON, TinkerMaterial.STEEL, TinkerMaterial.QUEEN_SLIME, TinkerMaterial.EMBER_DRAGON_BONE);
	/** Which of a Tinkering rod's parts are held to the heat: the limb and the hook (the line stays above it). */
	private static final int LIMB = 0, HOOK = 2;

	private LavaFishing() {
	}

	/** {@code rod} is a Tinkering rod that can fish lava. */
	public static boolean heatProof(ItemStack rod) {
		if (!(rod.getItem() instanceof TinkerRodItem) || ToolStats.isBroken(rod)) {
			return false;
		}
		List<TinkerMaterial> materials = ToolStats.materials(rod);
		return materials.size() > HOOK && HEAT_PROOF.contains(materials.get(LIMB)) && HEAT_PROOF.contains(materials.get(HOOK));
	}

	/** The top of the lava at {@code pos}, or NaN if {@code pos} isn't in lava. */
	static double top(Level level, BlockPos pos) {
		if (!level.getFluidState(pos).is(FluidTags.LAVA)) {
			return Double.NaN;
		}
		BlockPos.MutableBlockPos at = pos.mutable();
		for (int i = 0; i < 64 && level.getFluidState(at.above()).is(FluidTags.LAVA); i++) {
			at.move(0, 1, 0);
		}
		FluidState top = level.getFluidState(at);
		return at.getY() + top.getHeight(level, at);
	}

	/** Whether the water or lava at {@code pos} is lava: what a line hangs in. */
	static boolean lava(Level level, BlockPos pos) {
		return level.getFluidState(pos).is(FluidTags.LAVA) || level.getFluidState(pos.below()).is(FluidTags.LAVA);
	}

	/** {@code hook} floats in lava on a heat-proof rod, and so can be fished. */
	public static boolean holds(FishingHook hook) {
		return hook.getPlayerOwner() != null && heatProof(HookBait.rod(hook.getPlayerOwner())) && lava(hook.level(), hook.blockPosition());
	}

	/**
	 * After the hook's own tick (both sides): a hook in lava on a heat-proof rod floats at the top of it; on any other rod it
	 * burns up (the rod is not hurt).
	 */
	public static void tick(FishingHook hook) {
		Level level = hook.level();
		BlockPos pos = hook.blockPosition();
		double top = top(level, pos);
		if (Double.isNaN(top)) {
			top = top(level, pos.below());
		}
		if (Double.isNaN(top)) {
			return;
		}
		Player owner = hook.getPlayerOwner();
		if (owner == null) {
			return;
		}
		if (!heatProof(HookBait.rod(owner))) {
			if (!level.isClientSide() && owner instanceof ServerPlayer player) {
				burn((ServerLevel) level, hook, player);
			}
			return;
		}
		hook.clearFire();
		hook.setPos(hook.getX(), top, hook.getZ());
		hook.setDeltaMovement(hook.getDeltaMovement().multiply(0.5, 0.0, 0.5));
	}

	/** The hook went into lava on a rod that can't take it: it's gone, with a hiss. */
	private static void burn(ServerLevel level, FishingHook hook, ServerPlayer player) {
		level.playSound(null, hook.getX(), hook.getY(), hook.getZ(), SoundEvents.GENERIC_BURN, SoundSource.NEUTRAL, 0.6F, 1.2F);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, hook.getX(), hook.getY() + 0.2, hook.getZ(), 6, 0.1, 0.1, 0.1, 0.02);
		hook.discard();
		player.fishing = null;
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.fishing.lava_burns").withStyle(ChatFormatting.GRAY));
	}
}
