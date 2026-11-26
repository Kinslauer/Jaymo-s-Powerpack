package com.jaymoskingdoms;

import com.jaymoskingdoms.money.Money;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Prediction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.sounds.SoundEvents;

/**
 * The ranger's leaf cloak (docs/Climate-Plan.md, cl-leaf): leaves woven on a frame of withies, worn on the chest. It is cool
 * clothing in the heat (the tag {@code jaymoskitchen:cool_clothing}) and it hides its wearer in the woods
 * ({@link #sight}, from {@link Senses}): a mob notices them from {@link #IN_LEAVES} of its usual range with leaves against
 * them, {@link #IN_FOREST} of it elsewhere in a forest, and no less anywhere else. The Green Huntsman's rangers
 * ({@link ForestRangers}) sell it, in the silver of the land.
 */
public final class LeafCloak {
	/** What a mob's noticing range becomes (a share of it) with leaves against the wearer, and in a forest's trees. */
	static final double IN_LEAVES = 0.5, IN_FOREST = 0.7;
	/** What the ranger asks, in emeralds' worth. */
	static final int PRICE = 6;
	private static final int DURABILITY = 4, ENCHANT = 10;
	public static final TagKey<Item> LEAF_CLOTH = TagKey.create(Registries.ITEM, JaymosKingdoms.id("leaf_cloth"));
	static final ArmorMaterial LEAF = new ArmorMaterial(DURABILITY, ArmorMaterials.makeDefense(0, 0, 1, 0, 1), ENCHANT,
		SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F, LEAF_CLOTH, ResourceKey.create(EquipmentAssets.ROOT_ID, JaymosKingdoms.id("leaf_cloak")));

	public static Item CLOAK;

	private LeafCloak() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("leaf_cloak"));
		CLOAK = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).humanoidArmor(LEAF, ArmorType.CHESTPLATE)));
	}

	/** What a mob's range for noticing {@code player} is multiplied by for the cloak: 1 without it, or off in the open. */
	static double sight(ServerPlayer player) {
		if (CLOAK == null || !player.getItemBySlot(EquipmentSlot.CHEST).is(CLOAK)) {
			return 1.0;
		}
		ServerLevel level = player.level();
		BlockPos feet = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1, -1, -1), feet.offset(1, 2, 1))) {
			if (level.getBlockState(pos).is(BlockTags.LEAVES)) {
				return IN_LEAVES;
			}
		}
		return level.getBiome(feet).is(BiomeTags.IS_FOREST) ? IN_FOREST : 1.0;
	}

	/** A ranger's offer of a cloak, among their other choices ({@link ForestRangers}). */
	static void offer(ServerPlayer player, List<Menus.Choice> choices) {
		if (CLOAK == null) {
			return;
		}
		choices.add(Menus.Choice.of(Component.translatable("speech.jaymoskingdoms.ranger.leaf_cloak", PRICE), Money.canPay(player, Money.worth(PRICE)), p -> {
			if (Money.charge(p, Money.worth(PRICE))) {
				p.getInventory().placeItemBackInInventory(new ItemStack(CLOAK), Prediction.SERVER_ONLY);
				p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.ranger.leaf_cloaked").withStyle(net.minecraft.ChatFormatting.DARK_GREEN));
			}
		}));
	}
}
