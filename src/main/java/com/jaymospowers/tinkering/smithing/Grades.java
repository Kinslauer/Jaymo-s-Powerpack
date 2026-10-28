package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.TinkerTool;
import com.jaymospowers.tinkering.ToolStats;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import org.jspecify.annotations.Nullable;

/**
 * (Smithing) Reading and writing a {@link Grade}, and putting it into a tool's numbers. A Tinkering tool works its
 * grade in with the rest of its stats ({@link ToolStats#apply}); anything else forged from a head (Armory and WeaponMod
 * weapons, vanilla spears, armour) has the grade baked over its item's own numbers ({@link #bake}).
 */
public final class Grades {
	/** The modifiers a grade adds to a weapon or armour piece that isn't a Tinkering tool. */
	public static final Identifier DAMAGE_ID = JaymosPowers.id("grade_damage"), SPEED_ID = JaymosPowers.id("grade_speed"),
		ARMOR_ID = JaymosPowers.id("grade_armor");

	/** Why the Tool Station won't take this head yet (a hot head), or null; the first answer wins. */
	public static final List<Function<ItemStack, @Nullable Component>> STATION_REFUSALS = new ArrayList<>();
	/** The best grade a tool assembled from this head can have (a rough head), or null for no cap. */
	public static final List<Function<ItemStack, @Nullable Grade>> ASSEMBLY_CAPS = new ArrayList<>();
	/** Something else that moves from a head onto the tool made from it (the maker's mark): (head, made). */
	public static final List<java.util.function.BiConsumer<ItemStack, ItemStack>> CARRIERS = new ArrayList<>();

	private Grades() {
	}

	static void register() {
	}

	public static @Nullable Grade of(ItemStack stack) {
		return Grade.byId(stack.get(Smithing.GRADE));
	}

	/** Sets the grade (a Masterwork shimmers) and rebakes the numbers. */
	public static void set(ItemStack stack, @Nullable Grade grade) {
		if (grade == null) {
			stack.remove(Smithing.GRADE);
		} else {
			stack.set(Smithing.GRADE, grade.id);
		}
		if (grade == Grade.MASTER) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		} else if (stack.getPrototype().get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) == null) {
			stack.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
		}
		bake(stack);
	}

	/** Why the Tool Station won't take this head, or null. */
	public static @Nullable Component refusal(ItemStack head) {
		for (Function<ItemStack, @Nullable Component> refusal : STATION_REFUSALS) {
			Component why = refusal.apply(head);
			if (why != null) {
				return why;
			}
		}
		return null;
	}

	/** The head's grade passed on to what the Tool Station made from it (capped by the head's state). */
	public static void carry(ItemStack head, ItemStack made) {
		if (!made.isEmpty()) {
			for (java.util.function.BiConsumer<ItemStack, ItemStack> carrier : CARRIERS) {
				carrier.accept(head, made);
			}
		}
		Grade grade = of(head);
		if (grade == null || made.isEmpty()) {
			return;
		}
		for (Function<ItemStack, @Nullable Grade> cap : ASSEMBLY_CAPS) {
			Grade most = cap.apply(head);
			if (most != null) {
				grade = Grade.min(grade, most);
			}
		}
		set(made, grade);
	}

	/** A Tinkering tool's stats with its grade worked in. */
	public static ToolStats.Stats scale(ToolStats.Stats stats, @Nullable Grade grade) {
		if (grade == null) {
			return stats;
		}
		return new ToolStats.Stats(Math.max(1, Math.round(stats.durability() * grade.durability)), stats.miningSpeed() * grade.mining,
			stats.attack() + grade.attack, stats.attackSpeed() + grade.attackSpeed, stats.tier(), stats.traits());
	}

	/** Puts the grade into a forged item's durability, damage, swing speed, mining speed and armour. */
	public static void bake(ItemStack stack) {
		if (stack.getItem() instanceof TinkerTool) {
			ToolStats.apply(stack);
			return;
		}
		Grade grade = of(stack);
		DataComponentMap proto = stack.getPrototype();
		Integer maxDamage = proto.get(DataComponents.MAX_DAMAGE);
		if (maxDamage != null) {
			int max = grade == null ? maxDamage : Math.max(1, Math.round(maxDamage * grade.durability));
			stack.set(DataComponents.MAX_DAMAGE, max);
			stack.set(DataComponents.DAMAGE, Mth.clamp(stack.getDamageValue(), 0, max - 1));
		}
		ItemAttributeModifiers base = proto.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
		ItemAttributeModifiers attributes = base;
		if (grade != null) {
			boolean melee = proto.has(DataComponents.WEAPON) || base.modifiers().stream().anyMatch(e -> e.attribute().equals(Attributes.ATTACK_DAMAGE));
			EquipmentSlotGroup armorSlot = null;
			for (ItemAttributeModifiers.Entry entry : base.modifiers()) {
				if (entry.attribute().equals(Attributes.ARMOR)) {
					armorSlot = entry.slot();
				}
			}
			if (melee && grade.attack != 0) {
				attributes = attributes.withModifierAdded(Attributes.ATTACK_DAMAGE, new AttributeModifier(DAMAGE_ID, grade.attack,
					AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
			}
			if (melee && grade.attackSpeed != 0) {
				attributes = attributes.withModifierAdded(Attributes.ATTACK_SPEED, new AttributeModifier(SPEED_ID, grade.attackSpeed,
					AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
			}
			if (armorSlot != null && grade.armor != 0) {
				attributes = attributes.withModifierAdded(Attributes.ARMOR, new AttributeModifier(ARMOR_ID, grade.armor,
					AttributeModifier.Operation.ADD_VALUE), armorSlot);
			}
		}
		// (setting a component to its item's own value drops it from the stack's changes)
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes);
		Tool tool = proto.get(DataComponents.TOOL);
		if (tool != null) {
			if (grade == null || grade.mining == 1.0F) {
				stack.set(DataComponents.TOOL, tool);
			} else {
				List<Tool.Rule> rules = tool.rules().stream().map(rule -> new Tool.Rule(rule.blocks(),
					rule.speed().map(speed -> speed * grade.mining), rule.correctForDrops())).toList();
				stack.set(DataComponents.TOOL, new Tool(rules, tool.defaultMiningSpeed(), tool.damagePerBlock(), tool.canDestroyBlocksInCreative()));
			}
		}
		Wear.restrict(stack);
	}
}
