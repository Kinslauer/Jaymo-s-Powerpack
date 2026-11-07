package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Optional;

/**
 * (0.7.517) Enchanted Storage (Andy: "Wizards should be able to enchant storage somewhere high on the tree, and gain a bag
 * of holding"). A Mage with the perk ({@link Skills.Perk#ENCHANTED_STORAGE}) sneak-uses an enchanting table with storage
 * in hand: a chest becomes a leather Bag of Holding (rank 1), a leather bag an iron one (rank 2), an iron bag a golden
 * one (rank 3), each for levels and lapis; a bag keeps what it holds. Bag of Holding (Fuzs, MPL-2.0) ships unchanged in
 * the pack and its grid recipes are dropped, so this is the only way to a bag. Without the mod nothing happens.
 */
public final class StorageEnchanting {
	/** What each step takes: experience levels and lapis lazuli. */
	static final int[] LEVELS = {3, 6, 10}, LAPIS = {2, 4, 6};
	static final String[] BAGS = {"bagofholding:leather_bag_of_holding", "bagofholding:iron_bag_of_holding",
		"bagofholding:golden_bag_of_holding"};

	private StorageEnchanting() {
	}

	static void register() {
		UseBlockCallback.EVENT.register(StorageEnchanting::use);
	}

	/** The step (0-2) {@code held} would take, or -1 if it isn't storage that can be enchanted. */
	static int step(ItemStack held) {
		if (held.is(Items.CHEST)) {
			return 0;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(held.getItem());
		for (int i = 0; i < BAGS.length - 1; i++) {
			if (id.toString().equals(BAGS[i])) {
				return i + 1;
			}
		}
		return -1;
	}

	private static InteractionResult use(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || !player.isSecondaryUseActive() || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		BlockPos pos = hit.getBlockPos();
		ItemStack held = player.getMainHandItem();
		int step = step(held);
		if (step < 0 || !level.getBlockState(pos).is(Blocks.ENCHANTING_TABLE)) {
			return InteractionResult.PASS;
		}
		Optional<Item> bag = BuiltInRegistries.ITEM.getOptional(Identifier.parse(BAGS[step]));
		if (bag.isEmpty()) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		if (!sp.isCreative()) {
			if (!Classes.is(sp, PlayerClass.MAGE)) {
				return refuse(sp, "only_mage");
			}
			if (Skills.of(sp).rank(Skills.Perk.ENCHANTED_STORAGE) <= step) {
				return refuse(sp, "rank_" + (step + 1));
			}
			if (sp.experienceLevel < LEVELS[step] || sp.getInventory().countItem(Items.LAPIS_LAZULI) < LAPIS[step]) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.enchanted_storage.cost", LEVELS[step], LAPIS[step]));
				return InteractionResult.FAIL;
			}
			sp.giveExperienceLevels(-LEVELS[step]);
			take(sp, LAPIS[step]);
		}
		ItemStack made = step == 0 ? new ItemStack(bag.get()) : held.transmuteCopy(bag.get(), 1);
		held.shrink(1);
		if (!sp.getInventory().add(made)) {
			sp.drop(made, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		ServerLevel server = (ServerLevel) level;
		server.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 40, 0.4, 0.4, 0.4, 0.6);
		server.sendParticles(ParticleTypes.REVERSE_PORTAL, sp.getX(), sp.getY(1.0), sp.getZ(), 20, 0.3, 0.3, 0.3, 0.05);
		level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 0.9F + 0.1F * step);
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult refuse(ServerPlayer player, String why) {
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.enchanted_storage." + why));
		return InteractionResult.FAIL;
	}

	private static void take(ServerPlayer player, int lapis) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize() && lapis > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(Items.LAPIS_LAZULI)) {
				int n = Math.min(lapis, stack.getCount());
				stack.shrink(n);
				lapis -= n;
			}
		}
	}
}
