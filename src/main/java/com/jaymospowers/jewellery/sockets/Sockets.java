package com.jaymospowers.jewellery.sockets;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.baubles.BaubleItem;
import com.jaymospowers.jewellery.CutGem;
import com.jaymospowers.jewellery.Gems;
import com.jaymospowers.tinkering.TinkerTool;
import com.jaymospowers.tinkering.ToolPartItem;
import com.jaymospowers.tinkering.WeaponPartItem;
import com.jaymospowers.tinkering.smithing.Grade;
import com.jaymospowers.tinkering.smithing.Grades;
import com.jaymospowers.tinkering.smithing.Smithing;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import org.jspecify.annotations.Nullable;

/**
 * (0.20.7) Gem sockets in forged weapons and armour (docs/Sockets-Plan.md). The forging grade sets how many sockets a
 * piece has; each cut gem set in one adds its own bonus, baked into the item's modifiers by {@link Grades#bake}.
 */
public final class Sockets {
	/** Sockets per forging grade. */
	public static final Map<Grade, Integer> GRADE_SOCKETS = new EnumMap<>(Grade.class);
	/** Most sockets any grade gives; the saved list has room for twice as many (dormant gems). */
	public static final int MAX_SOCKETS = 2;
	/** Gem bonus multiplier on a melee piece (main hand) and on armour. */
	public static final float WEAPON_FACTOR = 1.0F, ARMOUR_FACTOR = 0.5F;
	/** Modifier id prefix: socket_[slot]_[index]. */
	public static final String ID_PREFIX = "socket_";

	static {
		GRADE_SOCKETS.put(Grade.POOR, 0);
		GRADE_SOCKETS.put(Grade.WELL, 0);
		GRADE_SOCKETS.put(Grade.EXPERT, 1);
		GRADE_SOCKETS.put(Grade.PERFECT, 1);
		GRADE_SOCKETS.put(Grade.MASTER, 2);
	}

	/** (0.20.7) The cut gems set in a forged weapon or armour piece, in socket order. Saved id: never rename. */
	public static DataComponentType<List<CutGem>> SOCKETS;

	private Sockets() {
	}

	public static void register() {
		SOCKETS = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("gem_sockets"),
			DataComponentType.<List<CutGem>>builder().persistent(CutGem.CODEC.listOf())
				.networkSynchronized(CutGem.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SOCKETS * 2))).build());
		Grades.ATTRIBUTE_PASSES.add(Sockets::withGems);
		SocketAnvil.register();
	}

	/** Forged melee weapons and armour that are not Tinkering tools, jewellery or heads. */
	public static boolean socketable(ItemStack stack) {
		return !stack.isEmpty() && stack.getMaxStackSize() == 1 && stack.has(Smithing.GRADE)
			&& !(stack.getItem() instanceof TinkerTool) && !(stack.getItem() instanceof BaubleItem)
			&& !(stack.getItem() instanceof WeaponPartItem) && !(stack.getItem() instanceof ToolPartItem)
			&& Grades.gearSlot(stack) != null;
	}

	public static int count(ItemStack stack) {
		Grade grade = Grades.of(stack);
		return socketable(stack) && grade != null ? GRADE_SOCKETS.get(grade) : 0;
	}

	public static List<CutGem> gems(ItemStack stack) {
		return stack.getOrDefault(SOCKETS, List.of());
	}

	public static int free(ItemStack stack) {
		return Math.max(0, count(stack) - gems(stack).size());
	}

	/** Sets a gem in the next free socket. */
	public static boolean add(ItemStack piece, CutGem gem) {
		if (free(piece) <= 0) {
			return false;
		}
		List<CutGem> list = new ArrayList<>(gems(piece));
		list.add(gem);
		piece.set(SOCKETS, List.copyOf(list));
		Grades.bake(piece);
		return true;
	}

	/** Takes the last gem out, or returns null. */
	public static @Nullable CutGem removeLast(ItemStack piece) {
		List<CutGem> list = new ArrayList<>(gems(piece));
		if (list.isEmpty()) {
			return null;
		}
		CutGem gem = list.remove(list.size() - 1);
		if (list.isEmpty()) {
			piece.remove(SOCKETS);
		} else {
			piece.set(SOCKETS, List.copyOf(list));
		}
		Grades.bake(piece);
		return gem;
	}

	public static double factor(ItemStack stack) {
		return Grades.gearSlot(stack) == EquipmentSlotGroup.MAINHAND ? WEAPON_FACTOR : ARMOUR_FACTOR;
	}

	/** What this gem gives in this piece, or null for an unknown gem. */
	public static BaubleItem.@Nullable Bonus bonus(ItemStack stack, CutGem gem) {
		Gems.Gem type = gem.gemType();
		return type == null ? null : type.bonus(gem.scale() * factor(stack));
	}

	/** The {@link Grades#ATTRIBUTE_PASSES} hook: one modifier per live gem. Gems past the grade's count stay dormant. */
	static ItemAttributeModifiers withGems(ItemStack stack, ItemAttributeModifiers attributes) {
		if (!socketable(stack)) {
			return attributes;
		}
		List<CutGem> gems = gems(stack);
		EquipmentSlotGroup slot = Grades.gearSlot(stack);
		int live = Math.min(count(stack), gems.size());
		for (int i = 0; i < live; i++) {
			BaubleItem.Bonus bonus = bonus(stack, gems.get(i));
			if (bonus != null) {
				attributes = attributes.withModifierAdded(bonus.attribute(), new AttributeModifier(
					JaymosPowers.id(ID_PREFIX + slot.getSerializedName() + "_" + i), bonus.amount(), bonus.operation()), slot);
			}
		}
		return attributes;
	}
}
