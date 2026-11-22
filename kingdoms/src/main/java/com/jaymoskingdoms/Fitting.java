package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A fitted harness (Harness 5; Lore.md, "The armourers' wider makes": "a smith who fits a harness to its wearer makes it
 * sit lighter and guard better"). An armourer, shown emeralds by someone crouching before him, fits the plate of the
 * {@link HeraldryArmour} harness they wear to them, {@link #PRICE} emeralds a piece. A fitted piece is marked with its
 * wearer ({@link #FITTED}): on them it gives {@link #ARMOUR} more armour, and Jaymo's Powers counts it lighter
 * ({@code ArmourKind.FITTED}). On anyone else it is only plate. Fitting again for someone new refits it to them.
 */
public final class Fitting {
	/** Emeralds a piece, and the armour each fitted piece adds on its own wearer. */
	static final int PRICE = 2;
	static final double ARMOUR = 0.5;

	/** On a fitted piece: whom it was fitted to. */
	public static final DataComponentType<UUID> FITTED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("fitted"),
		DataComponentType.<UUID>builder().persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC).build());

	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final net.minecraft.resources.Identifier BONUS = JaymosKingdoms.id("fitted_harness");

	private Fitting() {
	}

	static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !player.isShiftKeyDown() || !player.getMainHandItem().is(Items.EMERALD)
				|| !(entity instanceof Villager v) || v.isBaby() || !armourer(v)) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp) {
				fit(sp, v);
			}
			return InteractionResult.SUCCESS;
		});
	}

	private static boolean armourer(Villager v) {
		return BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getVillagerData().profession().value()).getPath().equals("armorer");
	}

	/** The plate {@code wearer} has on that can be fitted: helm, cuirass, sabatons and the harness's plate. */
	private static List<ItemStack> plate(LivingEntity wearer) {
		List<ItemStack> out = new ArrayList<>();
		for (EquipmentSlot slot : SLOTS) {
			add(out, wearer.getItemBySlot(slot));
		}
		for (ItemStack piece : Harness.worn(wearer)) {
			add(out, piece);
		}
		return out;
	}

	private static void add(List<ItemStack> out, ItemStack stack) {
		HeraldryArmour.Piece piece = HeraldryArmour.of(stack);
		if (piece != null && piece.plate()) {
			out.add(stack);
		}
	}

	private static void fit(ServerPlayer player, Villager armourer) {
		ItemStack emeralds = player.getMainHandItem();
		int fitted = 0;
		boolean any = false;
		for (ItemStack piece : plate(player)) {
			if (player.getUUID().equals(piece.get(FITTED))) {
				continue;
			}
			any = true;
			if (emeralds.getCount() < PRICE) {
				break;
			}
			emeralds.shrink(PRICE);
			piece.set(FITTED, player.getUUID());
			fitted++;
		}
		if (fitted > 0) {
			Harness.resync(player);
			armourer.playSound(SoundEvents.SMITHING_TABLE_USE, 1.0F, 1.0F);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.fitting.fitted", fitted, fitted * PRICE));
		} else if (any) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.fitting.short", PRICE));
		} else {
			armourer.playSound(SoundEvents.VILLAGER_NO, 1.0F, 1.0F);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.fitting.nothing"));
		}
		Harness.refresh(player);
	}

	/** (from {@link Harness#refresh}) {@code wearer}'s armour from the pieces fitted to them. */
	static void bonus(LivingEntity wearer) {
		AttributeInstance armour = wearer.getAttribute(Attributes.ARMOR);
		if (armour == null) {
			return;
		}
		int count = 0;
		for (ItemStack piece : plate(wearer)) {
			if (wearer.getUUID().equals(piece.get(FITTED))) {
				count++;
			}
		}
		double amount = count * ARMOUR;
		AttributeModifier has = armour.getModifier(BONUS);
		if (amount <= 0.0) {
			if (has != null) {
				armour.removeModifier(BONUS);
			}
		} else if (has == null || has.amount() != amount) {
			armour.addOrReplacePermanentModifier(new AttributeModifier(BONUS, amount, AttributeModifier.Operation.ADD_VALUE));
		}
	}
}
