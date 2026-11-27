package com.jaymospowers.alchemy.herbs;

import com.jaymospowers.alchemy.hunter.Leans;
import com.jaymospowers.alchemy.hunter.Leans.Lean;
import java.util.List;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Grafting (Hunter's Road, patch 7, "T-B2"; Toxony's idea, written clean-room with our own strains): use an ingredient that
 * leans a known way on a poison crop of the garden and, if you know both leans ({@code Leans#knows}) and the two go together
 * ({@link #STRAINS}), the crop becomes a new strain, at the same stage of growth. Graft without knowing the leans ("blind",
 * an ingredient that leans some way you have not learned, or a crop whose lean you have not) and the graft kills the crop. An
 * ingredient that leans no way, or two known leans that go to no strain, does nothing. One ingredient is used each time.
 * Only the base plants of the garden take a graft, and strains do not take another.
 */
public final class Grafting {
	/** A graft: this crop with an ingredient leaning this way grows this strain. */
	public record Strain(Garden.Crop from, Lean with, Garden.Crop into) {
	}

	/** The five strains: hemlock with the night's lean, nightshade with heat, bloodroot with decay, mint with cold, monkshood with the sea. */
	public static final List<Strain> STRAINS = List.of(
		new Strain(Garden.WATER_HEMLOCK, Lean.NETHER, Garden.DUSK_HEMLOCK),
		new Strain(Garden.NIGHTSHADE, Lean.HEAT, Garden.SUNBANE),
		new Strain(Garden.BLOODROOT, Lean.DECAY, Garden.WARWORT),
		new Strain(Garden.JUNGLE_MINT, Lean.COLD, Garden.RIME_MINT),
		new Strain(Garden.MONKSHOOD, Lean.SEA, Garden.EDDY_BELL));

	private Grafting() {
	}

	static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player.isSpectator() || !(level instanceof ServerLevel server)) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getItemInHand(hand);
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			Garden.Crop crop = baseCrop(state);
			Lean with = held.isEmpty() || crop == null ? null : Leans.of(held);
			if (with == null || state.getValue(HerbCropBlock.WILD)) {
				return InteractionResult.PASS;
			}
			boolean blind = !Leans.knows(player, with) || !Leans.knows(player, crop.lean());
			Strain strain = blind ? null : strainOf(crop, with);
			if (!blind && strain == null) {
				return InteractionResult.PASS;
			}
			if (!player.hasInfiniteMaterials()) {
				held.shrink(1);
			}
			double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
			if (blind) {
				// the crop withers where it stands
				server.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				server.sendParticles(ParticleTypes.SMOKE, x, y, z, 14, 0.25, 0.25, 0.25, 0.02);
				server.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 0.8F);
			} else {
				Block into = strain.into().block();
				server.setBlock(pos, into.defaultBlockState().setValue(HerbCropBlock.AGE, state.getValue(HerbCropBlock.AGE)), Block.UPDATE_ALL);
				server.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y, z, 10, 0.3, 0.3, 0.3, 0.0);
				server.playSound(null, pos, SoundEvents.COMPOSTER_FILL_SUCCESS, SoundSource.BLOCKS, 0.8F, 1.1F);
			}
			return InteractionResult.SUCCESS_SERVER;
		});
	}

	/** The garden's base poison plant {@code state} is, or null (a strain, a meadow flower or anything else). */
	private static Garden.@Nullable Crop baseCrop(BlockState state) {
		for (Strain s : STRAINS) {
			if (state.is(s.from().block())) {
				return s.from();
			}
		}
		return null;
	}

	private static @Nullable Strain strainOf(Garden.Crop crop, Lean with) {
		for (Strain s : STRAINS) {
			if (s.from() == crop && s.with() == with) {
				return s;
			}
		}
		return null;
	}
}
