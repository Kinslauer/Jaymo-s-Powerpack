package com.jaymospowers.tinkering;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * A built tool's numbers, worked out from its parts the way Tinkers' Construct does: heads give durability, speed,
 * attack and tier (averaged by each part's share), handles scale them, the tool type multiplies the lot. Everything a
 * tool carries (durability, mining rules, attack, its look and name) is rewritten from its materials by {@link #apply}.
 * <p>
 * Tools never vanish: at 0 durability they're broken (mine like a fist, hit for nothing) until repaired.
 */
public final class ToolStats {
	/** Stonebound: the most mining speed it adds, and attack it loses, per level, when nearly worn out. */
	static final float STONEBOUND_SPEED = 0.5F, STONEBOUND_ATTACK = 1.0F;
	/** Jagged: the most attack it adds per level when nearly worn out. */
	static final float JAGGED_ATTACK = 2.0F;
	/** Dwarven: mining speed added per level for every 32 blocks below y=64, up to 4 steps. */
	static final float DWARVEN_SPEED = 1.0F;
	static final int DWARVEN_TOP = 64, DWARVEN_STEP = 32, DWARVEN_STEPS = 4;
	/** Stringy and Solar Powered (in sunlight): chance per level that a point of wear doesn't land. */
	static final float STRINGY_CHANCE = 0.15F, SOLAR_CHANCE = 0.25F;
	/** One material unit repairs this share of the tool, over the cost of its main part (a pick head: 25% a unit). */
	static final float REPAIR_FACTOR = 0.5F;
	/** Cultivated: repairs go this much further per level. */
	static final float CULTIVATED_REPAIR = 0.5F;

	/** A tool's worked-out stats (attack before wear-based traits). */
	public record Stats(int durability, float miningSpeed, float attack, float attackSpeed, TinkerMaterial.Tier tier,
		Map<Trait, Integer> traits) {
		public int level(Trait trait) {
			return traits.getOrDefault(trait, 0);
		}
	}

	private ToolStats() {
	}

	public static Stats compute(ToolType type, List<TinkerMaterial> materials) {
		float headWeight = 0, durability = 0, speed = 0, attack = 0;
		float handleWeight = 0, handleDurability = 0, handleSpeed = 0, handleAttack = 0, handleAttackSpeed = 0;
		TinkerMaterial.Tier tier = TinkerMaterial.Tier.WOOD;
		Map<Trait, Integer> traits = new EnumMap<>(Trait.class);
		for (int i = 0; i < type.parts.size(); i++) {
			ToolType.Slot slot = type.parts.get(i);
			TinkerMaterial material = i < materials.size() ? materials.get(i) : TinkerMaterial.WOOD;
			traits.merge(material.trait, 1, Integer::sum);
			TinkerMaterial.Stats s = material.stats;
			if (s == null) {
				continue;
			}
			switch (slot.part().kind) {
				case HEAD -> {
					headWeight += slot.scale();
					durability += s.durability() * slot.scale();
					speed += s.miningSpeed() * slot.scale();
					attack += s.attack() * slot.scale();
					if (i == 0) {
						tier = s.tier();
					}
				}
				case HANDLE -> {
					handleWeight += slot.scale();
					handleDurability += s.handleDurability() * slot.scale();
					handleSpeed += s.handleSpeed() * slot.scale();
					handleAttack += s.handleAttack() * slot.scale();
					handleAttackSpeed += s.handleAttackSpeed() * slot.scale();
				}
				case BINDING -> {
				}
			}
		}
		if (headWeight > 0) {
			durability /= headWeight;
			speed /= headWeight;
			attack /= headWeight;
		}
		if (handleWeight > 0) {
			handleDurability /= handleWeight;
			handleSpeed /= handleWeight;
			handleAttack /= handleWeight;
			handleAttackSpeed /= handleWeight;
		}
		type.traits.forEach((trait, level) -> traits.merge(trait, level, Integer::sum));
		int finalDurability = Math.max(1, Math.round(durability * (1 + handleDurability) * type.durabilityMultiplier));
		float finalSpeed = Math.max(0.1F, speed * (1 + handleSpeed) * type.speedMultiplier);
		float finalAttack = (type.attack + attack) * (1 + handleAttack) * type.attackMultiplier;
		float finalAttackSpeed = type.attackSpeed * (1 + handleAttackSpeed);
		return new Stats(finalDurability, finalSpeed, finalAttack, finalAttackSpeed, tier, traits);
	}

	/** The materials on a tool, in part order (a raw /give gets its tool's defaults). */
	public static List<TinkerMaterial> materials(ItemStack stack) {
		List<String> ids = stack.get(Tinkering.TOOL_MATERIALS);
		ToolType type = stack.getItem() instanceof TinkerToolItem tool ? tool.type : null;
		List<TinkerMaterial> out = new ArrayList<>();
		int size = type != null ? type.parts.size() : ids != null ? ids.size() : 0;
		for (int i = 0; i < size; i++) {
			TinkerMaterial material = ids != null && i < ids.size() ? TinkerMaterial.byId(ids.get(i)) : null;
			out.add(material != null ? material : defaultMaterial(type, i));
		}
		return out;
	}

	static TinkerMaterial defaultMaterial(@Nullable ToolType type, int index) {
		if (type == null) {
			return TinkerMaterial.WOOD;
		}
		return switch (type.parts.get(index).part().kind) {
			case HEAD -> TinkerMaterial.ROCK_STONE;
			case HANDLE -> TinkerMaterial.WOOD;
			case BINDING -> TinkerMaterial.STRING;
		};
	}

	public static @Nullable Stats stats(ItemStack stack) {
		return stack.getItem() instanceof TinkerToolItem tool ? compute(tool.type, materials(stack)) : null;
	}

	/** A tool stack with these materials, freshly built. */
	public static ItemStack build(ToolType type, List<TinkerMaterial> materials) {
		ItemStack stack = new ItemStack(Tinkering.TOOLS.get(type));
		stack.set(Tinkering.TOOL_MATERIALS, materials.stream().map(m -> m.id).toList());
		apply(stack);
		return stack;
	}

	public static boolean isBroken(ItemStack stack) {
		return stack.getItem() instanceof TinkerToolItem && stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage();
	}

	/** How worn the tool is, 0 (new) to 1 (broken). */
	static float worn(ItemStack stack) {
		int max = stack.getMaxDamage();
		return max > 0 ? Mth.clamp(stack.getDamageValue() / (float) max, 0.0F, 1.0F) : 0.0F;
	}

	/** Rewrites everything a tool carries from its materials and wear. */
	public static void apply(ItemStack stack) {
		if (!(stack.getItem() instanceof TinkerToolItem item)) {
			return;
		}
		ToolType type = item.type;
		List<TinkerMaterial> materials = materials(stack);
		Stats stats = compute(type, materials);
		if (!stack.has(Tinkering.TOOL_MATERIALS)) {
			stack.set(Tinkering.TOOL_MATERIALS, materials.stream().map(m -> m.id).toList());
		}
		stack.set(DataComponents.MAX_DAMAGE, stats.durability());
		stack.set(DataComponents.DAMAGE, Mth.clamp(stack.getDamageValue(), 0, stats.durability()));
		boolean broken = isBroken(stack);
		stack.set(DataComponents.TOOL, broken ? new Tool(List.of(), 1.0F, 1, type.style == ToolType.Style.TOOL) : tool(type, stats));
		float worn = worn(stack);
		float attack = stats.attack() + worn * (stats.level(Trait.JAGGED) * JAGGED_ATTACK - stats.level(Trait.STONEBOUND) * STONEBOUND_ATTACK);
		ItemAttributeModifiers.Builder attributes = ItemAttributeModifiers.builder();
		if (!broken) {
			attributes.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, Math.max(0.0F, attack),
				AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
		}
		attributes.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, stats.attackSpeed() - 4.0F,
			AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes.build());
		stack.set(DataComponents.WEAPON, new Weapon(type.style == ToolType.Style.SWORD ? 1 : 2, broken ? 0.0F : type.disablesShields));
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(broken),
			materials.stream().map(m -> m.id).toList(), List.of()));
		stack.set(DataComponents.ITEM_NAME, Component.translatable("jaymospowers.tinkering.named", materials.getFirst().displayName(),
			Component.translatable("item.jaymospowers." + type.itemId())));
	}

	private static Tool tool(ToolType type, Stats stats) {
		if (type.style == ToolType.Style.SWORD) {
			return new Tool(List.of(
				Tool.Rule.minesAndDrops(HolderSet.direct(new Holder[] {Blocks.COBWEB.builtInRegistryHolder()}), 15.0F),
				Tool.Rule.overrideSpeed(tag(BlockTags.SWORD_INSTANTLY_MINES), Float.MAX_VALUE),
				Tool.Rule.overrideSpeed(tag(BlockTags.SWORD_EFFICIENT), 1.5F)), 1.0F, 2, false);
		}
		List<Tool.Rule> rules = new ArrayList<>();
		rules.add(Tool.Rule.deniesDrops(tag(stats.tier().incorrect)));
		for (TagKey<Block> mines : type.mines) {
			rules.add(Tool.Rule.minesAndDrops(tag(mines), stats.miningSpeed()));
		}
		return new Tool(rules, 1.0F, 1, true);
	}

	private static HolderSet<Block> tag(TagKey<Block> tag) {
		return BuiltInRegistries.BLOCK.getOrThrow(tag);
	}

	/** Mining speed with the traits that change as you go (Stonebound with wear, Dwarven with depth). */
	public static float liveSpeed(Player player, ItemStack stack, BlockState state, float speed) {
		if (speed <= 1.0F || !(stack.getItem() instanceof TinkerToolItem)) {
			return speed;
		}
		Stats stats = stats(stack);
		if (stats == null) {
			return speed;
		}
		float bonus = speed * stats.level(Trait.STONEBOUND) * STONEBOUND_SPEED * worn(stack);
		int dwarven = stats.level(Trait.DWARVEN);
		if (dwarven > 0) {
			float steps = Mth.clamp((DWARVEN_TOP - (float) player.getY()) / DWARVEN_STEP, 0.0F, DWARVEN_STEPS);
			bonus += dwarven * DWARVEN_SPEED * steps;
		}
		return speed + bonus;
	}

	/** The wear a hit or block really costs once Tanned, Stringy and Solar Powered have had their say. */
	public static int wear(ItemStack stack, int amount, ServerLevel level, @Nullable ServerPlayer player) {
		Stats stats = stats(stack);
		if (stats == null || amount <= 0) {
			return amount;
		}
		if (stats.level(Trait.TANNED) > 0) {
			amount = Math.min(amount, 1);
		}
		float skip = stats.level(Trait.STRINGY) * STRINGY_CHANCE;
		if (player != null && stats.level(Trait.SOLAR_POWERED) > 0 && level.isBrightOutside() && level.canSeeSky(player.blockPosition().above())) {
			skip += stats.level(Trait.SOLAR_POWERED) * SOLAR_CHANCE;
		}
		if (skip <= 0) {
			return amount;
		}
		int out = 0;
		for (int i = 0; i < amount; i++) {
			if (level.getRandom().nextFloat() >= skip) {
				out++;
			}
		}
		return out;
	}

	/** Durability one unit of the head's material puts back. */
	public static int repairPerUnit(ItemStack stack) {
		Stats stats = stats(stack);
		if (stats == null || !(stack.getItem() instanceof TinkerToolItem tool)) {
			return 0;
		}
		float share = REPAIR_FACTOR / tool.type.parts.getFirst().part().cost * (1 + CULTIVATED_REPAIR * stats.level(Trait.CULTIVATED));
		return Math.max(1, Math.round(stack.getMaxDamage() * share));
	}
}
