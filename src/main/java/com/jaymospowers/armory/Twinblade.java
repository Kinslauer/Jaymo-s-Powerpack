package com.jaymospowers.armory;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.combat.Armed;
import com.mojang.serialization.Codec;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/**
 * The Twinblade (Andy's pick from Star Wars: Galaxy in a Cube, ideas only: its double-ended blade made of two that
 * join at the hilt). Andy: "an ancient warrior born design, not often wielded by many, and the schematic is found in
 * the stormborn dungeon."
 * <ul>
 * <li><b>The schematic</b> lies in the Stormborn Tomb (its boss's hoard, now and then its last chest: {@link #loot}).
 * Reading it (use it) teaches the joining for good ({@link #KNOWN}) and uses the sheet up.</li>
 * <li><b>Joining</b>: sneak and use with two swords of the same make, one in each hand. They become one Twinblade that
 * carries both inside it ({@code minecraft:container}): it strikes for the sword's damage and {@link #BONUS_DAMAGE}
 * more, slower ({@link #ATTACK_SPEED}), lasts as long as the two together, and is held, swung and guarded with like a
 * quarterstaff. No recipe: the blades are forged as any sword is.</li>
 * <li><b>Parting</b>: sneak and use it with the other hand empty, and the two swords come apart again, the wear it took
 * shared between them. Their enchantments sleep while they are joined.</li>
 * </ul>
 */
public final class Twinblade {
	/** What the joined blades strike for over one of them, and the Twinblade's attack speed modifier (a sword's is -2.4). */
	public static final float BONUS_DAMAGE = 1.0F, ATTACK_SPEED = -2.6F;
	/** The chance the Stormborn Tomb's boss hoard, and its last chest, holds the schematic. */
	public static final float BOSS_CHANCE = 0.35F, END_CHEST_CHANCE = 0.1F;
	static final String TOMB_NAMESPACE = "jaymoskingdoms", BOSS_LOOT = "chests/stormborn_tomb_boss_loot", END_LOOT = "chests/stormborn_tomb_end_chest_loot";

	/** On a player who has read the schematic. Saved, kept through death, synced to them (the join is judged on both sides). */
	public static final AttachmentType<Boolean> KNOWN = AttachmentRegistry.create(JaymosPowers.id("twinblade_known"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));

	static Item item, schematic;

	private Twinblade() {
	}

	/** Before the weapon guard's own use (combat/Guard), so a sneaking join or parting isn't taken for a raised guard. */
	public static void init() {
		UseItemCallback.EVENT.register(Twinblade::use);
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			var key = table.unwrapKey();
			if (key.isEmpty() || schematic == null || !key.get().identifier().getNamespace().equals(TOMB_NAMESPACE)) {
				return;
			}
			String path = key.get().identifier().getPath();
			float chance = path.equals(BOSS_LOOT) ? BOSS_CHANCE : path.equals(END_LOOT) ? END_CHEST_CHANCE : 0.0F;
			if (chance > 0.0F && context.getRandom().nextFloat() < chance) {
				drops.add(new ItemStack(schematic));
			}
		});
	}

	public static boolean is(ItemStack stack) {
		return item != null && stack.is(item);
	}

	public static boolean knows(Player player) {
		return Boolean.TRUE.equals(player.getAttached(KNOWN));
	}

	private static InteractionResult use(Player player, Level level, InteractionHand hand) {
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		ItemStack main = player.getMainHandItem(), off = player.getOffhandItem();
		if (schematic != null && main.is(schematic)) {
			return read(player, level, main);
		}
		if (!player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		if (is(main) && off.isEmpty()) {
			return part(player, level, main);
		}
		if (joinable(main, off) && knows(player)) {
			return join(player, level, main, off);
		}
		return InteractionResult.PASS;
	}

	private static InteractionResult read(Player player, Level level, ItemStack sheet) {
		if (knows(player)) {
			if (!level.isClientSide()) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.twinblade.known").withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.SUCCESS;
		}
		if (!level.isClientSide()) {
			player.setAttached(KNOWN, true);
			sheet.consume(1, player);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.twinblade.learned").withStyle(ChatFormatting.GOLD));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
		}
		return InteractionResult.SUCCESS;
	}

	/** Two swords of the same make, each a one-handed blade, neither already a Twinblade. */
	private static boolean joinable(ItemStack main, ItemStack off) {
		return !main.isEmpty() && !off.isEmpty() && main.getItem() == off.getItem() && main.is(ItemTags.SWORDS) && !is(main)
			&& Armed.kind(main) == Armed.Kind.ONE_HAND;
	}

	private static InteractionResult join(Player player, Level level, ItemStack main, ItemStack off) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		ItemStack joined = new ItemStack(item);
		joined.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(main.copyWithCount(1), off.copyWithCount(1))));
		double damage = 0.0;
		for (ItemAttributeModifiers.Entry entry : main.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers()) {
			if (entry.attribute().equals(Attributes.ATTACK_DAMAGE) && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
				damage += entry.modifier().amount();
			}
		}
		joined.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
			.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, damage + BONUS_DAMAGE, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND)
			.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, ATTACK_SPEED, AttributeModifier.Operation.ADD_VALUE),
				EquipmentSlotGroup.MAINHAND)
			.build());
		if (main.isDamageableItem() && off.isDamageableItem()) {
			joined.set(DataComponents.MAX_DAMAGE, main.getMaxDamage() + off.getMaxDamage());
			joined.setDamageValue(main.getDamageValue() + off.getDamageValue());
		}
		main.shrink(1);
		off.shrink(1);
		player.setItemInHand(InteractionHand.MAIN_HAND, joined);
		if (off.isEmpty()) {
			player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.9F, 0.8F);
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult part(Player player, Level level, ItemStack joined) {
		List<ItemStack> blades = joined.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemCopyStream().toList();
		if (blades.size() < 2) {
			// (one that never was two swords: out of the creative tab)
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		ItemStack first = blades.get(0), second = blades.get(1);
		// the wear it took while joined, shared between the two
		int wear = Math.max(0, joined.getDamageValue() - first.getDamageValue() - second.getDamageValue());
		wear(first, wear - wear / 2);
		wear(second, wear / 2);
		player.setItemInHand(InteractionHand.MAIN_HAND, first);
		player.setItemInHand(InteractionHand.OFF_HAND, second);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, 0.9F, 1.2F);
		return InteractionResult.SUCCESS;
	}

	private static void wear(ItemStack blade, int amount) {
		if (blade.isDamageableItem() && amount > 0) {
			blade.setDamageValue(Math.min(blade.getMaxDamage() - 1, blade.getDamageValue() + amount));
		}
	}
}
