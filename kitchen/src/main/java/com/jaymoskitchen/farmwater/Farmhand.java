package com.jaymoskitchen.farmwater;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The woken scarecrow's work (a {@link ScarecrowBlock} with a carved pumpkin and {@link ScarecrowBlock#WOKEN}), from the
 * Harvest Lich's spark. Once a second, while it has straw, it takes one ripe crop within {@link #REACH} blocks, plants
 * the same crop again in the same soil (it never steps on the field: it stands on its post and turns to face the work),
 * and carries what the crop gave to the chest its keeper chose ({@link #CARRY} blocks at most; no chest, or a full one,
 * and the harvest is set down at its foot). Each crop costs a unit of straw: mend it with hay or wheat now and then.
 * It looks after a field a keeper looks after, and a field left alone is no worse for having one standing in it, or not.
 * A scarecrow kept in straw sometimes brings in more than the crop gave ({@link #BOUNTY_CHANCE}).
 */
public final class Farmhand {
	// ---- tuning ----
	/** How far (blocks across, up and down) it reaches for a ripe crop, and how far from it the chosen chest may be. */
	public static final int REACH = 6, REACH_UP = 2, CARRY = 24;
	/** The straw it holds at most, what a hay block and a wheat give, and what one harvested crop costs. */
	public static final int MOST_STRAW = 256, HAY_STRAW = 64, WHEAT_STRAW = 6, PER_CROP = 1;
	/** The chance a harvest brings an extra helping of what the crop gave. */
	public static final float BOUNTY_CHANCE = 0.15F;
	/** How long (ticks) a keeper has to touch a chest after choosing to pick one. */
	static final long CHOOSE_TICKS = 600L;

	private record Choosing(ResourceKey<Level> level, BlockPos scarecrow, long until) {
	}

	private static final Map<UUID, Choosing> CHOOSING = new HashMap<>();

	private Farmhand() {
	}

	static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			Choosing choosing = CHOOSING.get(player.getUUID());
			if (choosing == null || !(level instanceof ServerLevel server) || !(player instanceof ServerPlayer)) {
				return InteractionResult.PASS;
			}
			if (server.getGameTime() > choosing.until || !server.dimension().equals(choosing.level)) {
				CHOOSING.remove(player.getUUID());
				return InteractionResult.PASS;
			}
			BlockPos at = hit.getBlockPos();
			if (at.equals(choosing.scarecrow) || !(server.getBlockEntity(at) instanceof Container)) {
				return InteractionResult.PASS;
			}
			CHOOSING.remove(player.getUUID());
			if (at.distSqr(choosing.scarecrow) > (double)CARRY * CARRY) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.farmhand.too_far"));
			} else if (server.getBlockEntity(choosing.scarecrow) instanceof FarmhandEntity hand2) {
				hand2.setChest(at.immutable());
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.farmhand.chosen"));
				server.playSound(null, at, SoundEvents.CHEST_CLOSE, SoundSource.BLOCKS, 0.6F, 1.2F);
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** {@code player} is to touch the chest this scarecrow should carry to. */
	static void choose(ServerLevel level, Player player, BlockPos scarecrow) {
		CHOOSING.put(player.getUUID(), new Choosing(level.dimension(), scarecrow.immutable(), level.getGameTime() + CHOOSE_TICKS));
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.farmhand.choose"));
	}

	/** Mends {@code keep} with hay or wheat in {@code held}: whether it took any. */
	static boolean mend(FarmhandEntity keep, ItemStack held, Player player) {
		int give = held.is(Items.HAY_BLOCK) ? HAY_STRAW : held.is(Items.WHEAT) ? WHEAT_STRAW : 0;
		if (give == 0 || keep.straw() > MOST_STRAW - give) {
			return false;
		}
		keep.setStraw(keep.straw() + give);
		if (!player.isCreative()) {
			held.shrink(1);
		}
		return true;
	}

	/** One look at the field: a ripe crop reaped, planted again and carried off. Called while it stands woken, once a second. */
	static void work(ServerLevel level, BlockPos pos, BlockState state) {
		if (!(level.getBlockEntity(pos) instanceof FarmhandEntity keep) || keep.straw() < PER_CROP) {
			return;
		}
		BlockPos crop = ripe(level, pos);
		if (crop == null) {
			return;
		}
		BlockState ripe = level.getBlockState(crop);
		CropBlock block = (CropBlock)ripe.getBlock();
		List<ItemStack> drops = Block.getDrops(ripe, level, crop, null);
		level.setBlock(crop, block.getStateForAge(0), Block.UPDATE_ALL);
		keep.setStraw(keep.straw() - PER_CROP);
		Vec3 from = Vec3.atCenterOf(pos);
		Vec3 to = Vec3.atCenterOf(crop);
		Direction face = Direction.getApproximateNearest(to.x - from.x, 0.0, to.z - from.z);
		if (face.getAxis().isHorizontal() && face != state.getValue(ScarecrowBlock.FACING)) {
			level.setBlock(pos, state.setValue(ScarecrowBlock.FACING, face), Block.UPDATE_CLIENTS);
		}
		level.playSound(null, crop, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.6F, 1.0F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, to.x, to.y + 0.2, to.z, 4, 0.25, 0.2, 0.25, 0.0);
		boolean bounty = level.getRandom().nextFloat() < BOUNTY_CHANCE;
		for (ItemStack drop : drops) {
			carry(level, pos, keep, drop.copy());
			if (bounty && drop.getCount() > 0) {
				carry(level, pos, keep, drop.copyWithCount(1));
			}
		}
	}

	/** A ripe crop in reach, the nearest, or null. */
	private static BlockPos ripe(ServerLevel level, BlockPos pos) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos at : BlockPos.betweenClosed(pos.offset(-REACH, -REACH_UP, -REACH), pos.offset(REACH, REACH_UP, REACH))) {
			BlockState there = level.getBlockState(at);
			if (there.getBlock() instanceof CropBlock crop && crop.isMaxAge(there) && at.distSqr(pos) < bestD) {
				best = at.immutable();
				bestD = at.distSqr(pos);
			}
		}
		return best;
	}

	/** Puts {@code stack} in the chosen chest; what won't fit is set down at the scarecrow's foot. */
	private static void carry(ServerLevel level, BlockPos pos, FarmhandEntity keep, ItemStack stack) {
		BlockPos chest = keep.chest();
		if (chest != null && chest.distSqr(pos) <= (double)CARRY * CARRY && level.isLoaded(chest)) {
			Container container = HopperBlockEntity.getContainerAt(level, chest);
			if (container != null) {
				stack = HopperBlockEntity.addItem(null, container, stack, null);
				if (stack.isEmpty()) {
					level.sendParticles(ParticleTypes.HAPPY_VILLAGER, chest.getX() + 0.5, chest.getY() + 1.1, chest.getZ() + 0.5, 1, 0.2, 0.1, 0.2, 0.0);
					return;
				}
			}
		}
		Block.popResource(level, pos.above(), stack);
	}
}
