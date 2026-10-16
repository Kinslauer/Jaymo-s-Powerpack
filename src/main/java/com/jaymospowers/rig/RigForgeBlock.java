package com.jaymospowers.rig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The Rig Forge: where salvage becomes a suit. Right-click to build the selected piece, Sneak + right-click to pick the
 * next one (the cost shows each time). Salvage is counted in points: Rig Salvage {@link #SALVAGE_POINTS}, Fallen Rig
 * Scrap {@link #SCRAP_POINTS}; scrap is spent first. Right-click holding a worn-down Rig piece to refit it
 * ({@link #REFIT_POINTS}). Creative builds for free.
 */
public class RigForgeBlock extends Block {
	/** What each salvage is worth at the Forge. */
	static final int SALVAGE_POINTS = 3, SCRAP_POINTS = 1;
	/** Refitting a damaged piece to new. */
	static final int REFIT_POINTS = 2;

	/** A piece's price: salvage points, then two plain materials. */
	record Cost(int points, Item first, int firstCount, Item second, int secondCount) {
	}

	/** Each piece's price ({@link Rig.Piece} order). */
	static Cost cost(Rig.Piece piece) {
		return switch (piece) {
			case HELM -> new Cost(6, Items.IRON_INGOT, 3, Items.REDSTONE, 4);
			case CORE -> new Cost(9, Items.IRON_INGOT, 4, Items.REDSTONE_BLOCK, 1);
			case ARMS -> new Cost(9, Items.IRON_INGOT, 5, Items.GUNPOWDER, 4);
			case THRUSTERS -> new Cost(6, Items.IRON_INGOT, 4, Items.BLAZE_POWDER, 2);
		};
	}

	/** Each player's selected piece (forgotten on restart: it's only a dial). */
	private static final Map<UUID, Rig.Piece> SELECTED = new HashMap<>();

	public RigForgeBlock(Block.Properties properties) {
		super(properties);
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
		BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.PASS;
		}
		if (player instanceof ServerPlayer server) {
			work(server, stack, pos);
		}
		return InteractionResult.SUCCESS;
	}

	private static void work(ServerPlayer player, ItemStack held, BlockPos pos) {
		if (held.getItem() instanceof RigItem && held.isDamaged()) {
			if (!player.isCreative() && points(player) < REFIT_POINTS) {
				player.sendSystemMessage(Component.translatable("message.jaymospowers.rig_forge.refit_missing", REFIT_POINTS).withStyle(ChatFormatting.RED));
				return;
			}
			if (!player.isCreative()) {
				spendPoints(player, REFIT_POINTS);
			}
			held.setDamageValue(0);
			player.level().playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 0.8F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.rig_forge.refit", held.getHoverName()));
			return;
		}
		Rig.Piece piece = SELECTED.getOrDefault(player.getUUID(), Rig.Piece.HELM);
		if (player.isShiftKeyDown()) {
			piece = Rig.Piece.values()[(piece.ordinal() + 1) % Rig.Piece.values().length];
			SELECTED.put(player.getUUID(), piece);
			describe(player, piece);
			player.level().playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4F, 1.4F);
			return;
		}
		Cost cost = cost(piece);
		boolean free = player.isCreative();
		if (!free && (points(player) < cost.points() || count(player, cost.first()) < cost.firstCount()
			|| count(player, cost.second()) < cost.secondCount())) {
			describe(player, piece);
			player.sendSystemMessage(Component.translatable("message.jaymospowers.rig_forge.missing").withStyle(ChatFormatting.RED));
			return;
		}
		if (!free) {
			spendPoints(player, cost.points());
			take(player, cost.first(), cost.firstCount());
			take(player, cost.second(), cost.secondCount());
		}
		ItemStack made = new ItemStack(piece.item());
		player.getInventory().placeItemBackInInventory(made, Prediction.SERVER_ONLY);
		player.level().playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.8F, 0.9F);
		player.level().playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.5F, 1.6F);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.rig_forge.built",
			Component.translatable("item.jaymospowers.rig_" + piece.id())).withStyle(ChatFormatting.AQUA));
	}

	private static void describe(ServerPlayer player, Rig.Piece piece) {
		Cost cost = cost(piece);
		player.sendSystemMessage(Component.translatable("message.jaymospowers.rig_forge.selected",
			Component.translatable("item.jaymospowers.rig_" + piece.id()).withStyle(ChatFormatting.AQUA),
			cost.points(), points(player),
			cost.firstCount(), new ItemStack(cost.first()).getHoverName(), cost.secondCount(), new ItemStack(cost.second()).getHoverName()));
	}

	/** Salvage points carried (main inventory). */
	static int points(Player player) {
		return count(player, Rig.SALVAGE) * SALVAGE_POINTS + count(player, Rig.SCRAP) * SCRAP_POINTS;
	}

	private static int count(Player player, Item item) {
		int n = 0;
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.is(item)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	/** Spends points: scrap first, then whole salvage (no change given). */
	private static void spendPoints(Player player, int points) {
		int scrap = Math.min(count(player, Rig.SCRAP), points / SCRAP_POINTS);
		take(player, Rig.SCRAP, scrap);
		int left = points - scrap * SCRAP_POINTS;
		if (left > 0) {
			take(player, Rig.SALVAGE, (left + SALVAGE_POINTS - 1) / SALVAGE_POINTS);
		}
	}

	private static void take(Player player, Item item, int count) {
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (count <= 0) {
				return;
			}
			if (stack.is(item)) {
				int n = Math.min(count, stack.getCount());
				stack.shrink(n);
				count -= n;
			}
		}
	}
}
