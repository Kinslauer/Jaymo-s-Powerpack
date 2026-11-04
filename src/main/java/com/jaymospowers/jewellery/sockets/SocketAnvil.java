package com.jaymospowers.jewellery.sockets;

import com.jaymospowers.jewellery.CutGem;
import com.jaymospowers.jewellery.CutGemItem;
import com.jaymospowers.jewellery.Jewellery;
import com.jaymospowers.tinkering.smithing.SmithingAnvilBlock;
import com.jaymospowers.tinkering.smithing.TongsItem;
import com.jaymospowers.tinkering.smithing.Grades;
import com.jaymospowers.tinkering.smithing.Wear;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * (0.20.7) Socketing at a Smithing Anvil: sneak-use with the piece in the main hand. A cut gem in the off hand is set;
 * tongs pull the last gem out whole; an empty hand prises it out and it shatters (use twice).
 */
final class SocketAnvil {
	/** Ticks before the piece can be used at the anvil again. */
	private static final int SOCKET_COOLDOWN = 10;
	/** A second empty-hand use within this many ticks shatters the gem out. */
	private static final int PRISE_CONFIRM_TICKS = 40;
	/** A use this soon after the previous one counts as a held key and is ignored. */
	private static final int PRISE_MIN_GAP_TICKS = 8;
	/** Durability the tongs lose pulling a gem out whole. */
	private static final int TONGS_WEAR = 8;

	private static final Map<UUID, Long> PRISING = new HashMap<>();
	/** Which piece the warning was shown for, so another piece never inherits the confirmation. */
	private static final Map<UUID, Integer> PRISING_PIECE = new HashMap<>();
	private static final Map<UUID, Long> LAST_USE = new HashMap<>();

	private enum Action { SOCKET, PULL, PRISE }

	private SocketAnvil() {
	}

	static void register() {
		UseBlockCallback.EVENT.register(SocketAnvil::use);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PRISING.clear();
			PRISING_PIECE.clear();
			LAST_USE.clear();
		});
	}

	private static InteractionResult use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || !player.isSecondaryUseActive() || player.isSpectator()
			|| !(level.getBlockState(hit.getBlockPos()).getBlock() instanceof SmithingAnvilBlock)) {
			return InteractionResult.PASS;
		}
		ItemStack piece = player.getMainHandItem();
		if (!Sockets.socketable(piece)) {
			return InteractionResult.PASS;
		}
		ItemStack off = player.getOffhandItem();
		CutGem cut = off.is(Jewellery.CUT_GEM_ITEM) ? off.get(Jewellery.CUT_GEM) : null;
		boolean hasGems = !Sockets.gems(piece).isEmpty();
		Action action;
		if (cut != null && cut.gemType() != null) {
			action = Action.SOCKET;
		} else if (off.getItem() instanceof TongsItem && hasGems) {
			action = Action.PULL;
		} else if (off.isEmpty() && hasGems) {
			action = Action.PRISE;
		} else {
			return InteractionResult.PASS;
		}
		if (player.getCooldowns().isOnCooldown(piece) || !(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = hit.getBlockPos();
		boolean changed = switch (action) {
			case SOCKET -> socket(player, server, pos, piece, off, cut);
			case PULL -> pull(player, server, pos, piece, off);
			case PRISE -> prise(player, server, pos, piece);
		};
		if (changed) {
			player.getCooldowns().addCooldown(piece, SOCKET_COOLDOWN);
		}
		return InteractionResult.SUCCESS;
	}

	private static boolean socket(Player player, ServerLevel level, BlockPos pos, ItemStack piece, ItemStack off, CutGem cut) {
		if (Wear.broken(piece)) {
			tell(player, "broken", piece.getHoverName());
			return false;
		}
		Component hot = Grades.refusal(piece);
		if (hot != null) {
			player.sendOverlayMessage(hot);
			return false;
		}
		if (Sockets.count(piece) == 0) {
			tell(player, "no_sockets", piece.getHoverName());
			return false;
		}
		if (Sockets.free(piece) == 0) {
			tell(player, "full", piece.getHoverName());
			return false;
		}
		Component name = off.getHoverName();
		Sockets.add(piece, cut);
		off.shrink(1);
		level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 1.0F, 1.1F);
		level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.6F, 1.2F);
		level.sendParticles(ParticleTypes.WAX_ON, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 6, 0.25, 0.1, 0.25, 0.1);
		tell(player, "socketed", name, piece.getHoverName());
		return true;
	}

	private static boolean pull(Player player, ServerLevel level, BlockPos pos, ItemStack piece, ItemStack off) {
		CutGem gem = Sockets.removeLast(piece);
		if (gem == null) {
			return false;
		}
		ItemStack back = CutGemItem.of(gem);
		if (back.isEmpty()) {
			tell(player, "shattered", Component.translatable("item.jaymospowers.cut_gem"));
			return true;
		}
		player.getInventory().placeItemBackInInventory(back, Prediction.SERVER_ONLY);
		off.hurtAndBreak(TONGS_WEAR, player, InteractionHand.OFF_HAND);
		level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8F, 1.3F);
		tell(player, "pulled", back.getHoverName());
		return true;
	}

	private static boolean prise(Player player, ServerLevel level, BlockPos pos, ItemStack piece) {
		long now = level.getGameTime();
		Long first = PRISING.get(player.getUUID());
		int pieceKey = java.util.Objects.hash(player.getInventory().getSelectedSlot(), piece.getItem(), Sockets.gems(piece));
		Integer armedFor = PRISING_PIECE.get(player.getUUID());
		CutGem last = Sockets.gems(piece).get(Sockets.gems(piece).size() - 1);
		ItemStack shown = CutGemItem.of(last);
		Component name = shown.isEmpty() ? Component.translatable("item.jaymospowers.cut_gem") : shown.getHoverName();
		Long lastUse = LAST_USE.put(player.getUUID(), now);
		if (first != null && lastUse != null && now - lastUse < PRISE_MIN_GAP_TICKS) {
			// A held use key repeats every 4 ticks and keeps resetting this. Only a fresh click confirms.
			return false;
		}
		if (first == null || now - first > PRISE_CONFIRM_TICKS || armedFor == null || armedFor != pieceKey) {
			PRISING.put(player.getUUID(), now);
			PRISING_PIECE.put(player.getUUID(), pieceKey);
			tell(player, "prise_confirm", name);
			return false;
		}
		PRISING.remove(player.getUUID());
		PRISING_PIECE.remove(player.getUUID());
		LAST_USE.remove(player.getUUID());
		Sockets.removeLast(piece);
		level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 1.0F, 0.9F);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, Jewellery.CUT_GEM_ITEM), pos.getX() + 0.5, pos.getY() + 1.0,
			pos.getZ() + 0.5, 10, 0.2, 0.1, 0.2, 0.05);
		tell(player, "shattered", name);
		return true;
	}

	private static void tell(Player player, String key, Object... args) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.sockets." + key, args));
	}
}
