package com.jaymoskingdoms;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Breaking gear down at a smith (Apotheosis's salvaging, ARR: the idea only). Sneak and use an armourer, weaponsmith or toolsmith
 * with a worn-out piece of gear in the main hand (under {@link #BROKEN_BELOW} of its life left): the smith melts it down and hands
 * back {@link #RETURNED} of the metal it was made from ({@link #cost} ingots for the piece), as ingots, or for chain nuggets.
 * Gear of wood, stone, leather or an unknown metal is not worth the fire.
 */
public final class GearSalvage {
	/** Gear with less than this share of its durability left is broken enough. */
	static final float BROKEN_BELOW = 0.25F;
	/** The share of the metal that comes back. */
	static final float RETURNED = 0.5F;

	private GearSalvage() {
	}

	static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, target, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel) || !sp.isShiftKeyDown()
				|| !(target instanceof Villager smith) || !smithy(smith) || !broken(sp.getMainHandItem())) {
				return InteractionResult.PASS;
			}
			ItemStack back = metal(sp.getMainHandItem());
			if (back.isEmpty()) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.salvage.worthless"));
				return InteractionResult.SUCCESS;
			}
			sp.getMainHandItem().shrink(1);
			if (!sp.getInventory().add(back)) {
				sp.drop(back, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			level.playSound(null, smith.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.6F, 1.2F);
			sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.salvage.done", back.getCount(), back.getHoverName()));
			return InteractionResult.SUCCESS;
		});
	}

	static boolean smithy(Villager v) {
		var p = v.getVillagerData().profession();
		return p.is(VillagerProfession.ARMORER) || p.is(VillagerProfession.WEAPONSMITH) || p.is(VillagerProfession.TOOLSMITH);
	}

	static boolean broken(ItemStack stack) {
		return !stack.isEmpty() && stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() < stack.getMaxDamage() * BROKEN_BELOW;
	}

	/** Ingots in a piece of gear, by what it is. */
	static int cost(ItemStack stack) {
		if (stack.is(ItemTags.HEAD_ARMOR)) {
			return 5;
		}
		if (stack.is(ItemTags.CHEST_ARMOR)) {
			return 8;
		}
		if (stack.is(ItemTags.LEG_ARMOR)) {
			return 7;
		}
		if (stack.is(ItemTags.FOOT_ARMOR)) {
			return 4;
		}
		if (stack.is(ClassPerks.TWO_HANDED)) {
			return 6;
		}
		if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.AXES)) {
			return 3;
		}
		return stack.is(ItemTags.SHOVELS) ? 1 : 2;
	}

	/** What melting {@code stack} gives back (empty: nothing). */
	static ItemStack metal(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		boolean chain = path.startsWith("chainmail_");
		@Nullable Item ingot = path.startsWith("iron_") ? Items.IRON_INGOT : path.startsWith("golden_") || path.startsWith("gold_") ? Items.GOLD_INGOT
			: path.startsWith("copper_") ? Items.COPPER_INGOT : path.startsWith("diamond_") ? Items.DIAMOND : path.startsWith("netherite_") ? Items.NETHERITE_SCRAP
			: chain ? Items.IRON_NUGGET : null;
		if (ingot == null) {
			return ItemStack.EMPTY;
		}
		int n = Math.max(1, Math.round(cost(stack) * RETURNED * (chain ? 3 : 1)));
		return new ItemStack(ingot, n);
	}
}
