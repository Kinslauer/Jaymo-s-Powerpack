package com.jaymospowers.tinkering;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import net.minecraft.world.item.component.BlocksAttacks;
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
	/** Crumbling: extra mining speed per level (a share) on blocks that need no tool. */
	static final float CRUMBLING_SPEED = 0.5F;
	/** Stringy and Solar Powered (in sunlight): chance per level that a point of wear doesn't land. */
	static final float STRINGY_CHANCE = 0.15F, SOLAR_CHANCE = 0.25F;
	/** Tempered (steel): chance that a point of wear doesn't land, however many steel parts (it doesn't stack). */
	static final float TEMPERED_CHANCE = 0.25F;
	/** The battlesign's guard: seconds to raise it, how wide it covers (degrees), and the share of a blow's damage it takes. */
	static final float BLOCK_RAISE_SECONDS = 0.15F, BLOCK_ANGLE = 120.0F, BLOCK_SHARE = 0.5F;
	/** Shears (kama, scythe): mining speed on cobwebs and on wool. */
	static final float SHEARS_COBWEB_SPEED = 15.0F, SHEARS_WOOL_SPEED = 5.0F;
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
		// (0.15.x) bow limbs share the durability average with the heads; grips add a share and melee damage
		float limbWeight = 0, limbDurability = 0, gripWeight = 0, gripDurability = 0, gripAttack = 0;
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
				case LIMB -> {
					TinkerMaterial.Limb limb = material.limb();
					if (limb != null) {
						limbWeight += slot.scale();
						limbDurability += limb.durability() * slot.scale();
					}
				}
				case GRIP -> {
					TinkerMaterial.Grip grip = material.grip();
					if (grip != null) {
						gripWeight += slot.scale();
						gripDurability += grip.durability() * slot.scale();
						gripAttack += grip.meleeDamage() * slot.scale();
					}
				}
				case HEAD, ARROW_HEAD -> {
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
				default -> {
				}
			}
		}
		if (gripWeight > 0) {
			gripDurability /= gripWeight;
			gripAttack /= gripWeight;
		}
		if (limbWeight > 0) {
			// heads and limbs average together by their shares
			durability = (durability + limbDurability) / (headWeight + limbWeight);
			if (headWeight > 0) {
				speed /= headWeight;
				attack /= headWeight;
			}
			headWeight = 0;
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
		if (tier.ordinal() > type.maxTier.ordinal()) {
			tier = type.maxTier;
		}
		int finalDurability = Math.max(1, Math.round((durability + type.ranged.baseDurability()) * (1 + handleDurability + gripDurability)
			* type.durabilityMultiplier));
		float finalSpeed = Math.max(0.1F, speed * (1 + handleSpeed) * type.speedMultiplier);
		float finalAttack = (type.attack + attack + gripAttack) * (1 + handleAttack) * type.attackMultiplier;
		float finalAttackSpeed = type.attackSpeed * (1 + handleAttackSpeed);
		return new Stats(finalDurability, finalSpeed, finalAttack, finalAttackSpeed, tier, traits);
	}

	/** (0.15.x) A ranged tool's numbers: draw speed, arrow velocity and accuracy as multipliers of a plain bow's, and thrown damage. */
	public record Ranged(float drawSpeed, float velocity, float accuracy, float damage, float throwSpeed) {
		/** Shot spread over a plain bow's: its accuracy of 0.75 is 1, 1.0 is none, and worse than 0.5 is double. */
		public float spread() {
			return Mth.clamp((1.0F - accuracy) / 0.25F, 0.0F, 2.0F);
		}
	}

	/** (0.15.x) Accuracy a level of Aerodynamic (a feather) adds. */
	static final float AERODYNAMIC_ACCURACY = 0.1F;
	/** (0.15.x) A thrown blade's damage: its base and a share of the head's attack. */
	static final float THROWN_HEAD_SHARE = 0.75F;
	/** (0.15.x) An arrow's base damage (a plain arrow's 2) and the share of the head's attack it adds. */
	static final float ARROW_BASE_DAMAGE = 2.0F, ARROW_HEAD_SHARE = 0.5F;

	public static Ranged ranged(ToolType type, List<TinkerMaterial> materials) {
		float limbWeight = 0, draw = 0, velocity = 0, accuracy = 0, gripAccuracy = 0, shaft = 0, aero = 0;
		for (int i = 0; i < type.parts.size() && i < materials.size(); i++) {
			ToolType.Slot slot = type.parts.get(i);
			TinkerMaterial material = materials.get(i);
			switch (slot.part().kind) {
				case LIMB -> {
					TinkerMaterial.Limb limb = material.limb();
					if (limb != null) {
						limbWeight += slot.scale();
						draw += limb.drawSpeed() * slot.scale();
						velocity += limb.velocity() * slot.scale();
						accuracy += limb.accuracy() * slot.scale();
					}
				}
				case GRIP -> {
					TinkerMaterial.Grip grip = material.grip();
					if (grip != null) {
						gripAccuracy += grip.accuracy() * slot.scale();
					}
				}
				case ARROW_SHAFT -> shaft += material.shaftVelocity();
				default -> {
				}
			}
			if (material.trait == Trait.AERODYNAMIC) {
				aero += AERODYNAMIC_ACCURACY;
			}
		}
		if (limbWeight > 0) {
			draw /= limbWeight;
			velocity /= limbWeight;
			accuracy /= limbWeight;
		}
		float finalAccuracy = type.ranged.baseAccuracy() + accuracy + gripAccuracy + aero;
		float damage = 0.0F;
		Stats stats = compute(type, materials);
		switch (type.style) {
			case JAVELIN -> damage = stats.attack() * type.ranged.thrownDamage();
			case THROWN -> damage = type.ranged.thrownDamage() + headAttack(type, materials) * THROWN_HEAD_SHARE;
			case ARROW -> damage = ARROW_BASE_DAMAGE + headAttack(type, materials) * ARROW_HEAD_SHARE;
			default -> {
			}
		}
		return new Ranged(Math.max(0.2F, 1.0F + draw), Math.max(0.2F, 1.0F + velocity + shaft), finalAccuracy, damage, type.ranged.throwSpeed());
	}

	/** The head parts' attack, averaged. */
	private static float headAttack(ToolType type, List<TinkerMaterial> materials) {
		float total = 0, weight = 0;
		for (int i = 0; i < type.parts.size() && i < materials.size(); i++) {
			if (type.parts.get(i).part().kind == PartType.Kind.ARROW_HEAD && materials.get(i).stats != null) {
				total += materials.get(i).stats.attack();
				weight++;
			}
		}
		return weight > 0 ? total / weight : 0.0F;
	}

	/** The materials on a tool, in part order (a raw /give gets its tool's defaults). */
	public static List<TinkerMaterial> materials(ItemStack stack) {
		List<String> ids = stack.get(Tinkering.TOOL_MATERIALS);
		ToolType type = stack.getItem() instanceof TinkerTool tool ? tool.type() : null;
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
		return defaultFor(type.parts.get(index).part().kind);
	}

	/** What a part of this kind is made of when nothing says (a raw /give, the creative tab). */
	public static TinkerMaterial defaultFor(PartType.Kind kind) {
		return switch (kind) {
			case HEAD -> TinkerMaterial.ROCK_STONE;
			case HANDLE, LIMB, GRIP, ARROW_SHAFT -> TinkerMaterial.WOOD;
			case BINDING, BOWSTRING -> TinkerMaterial.STRING;
			case ARROW_HEAD -> TinkerMaterial.FLINT;
			case FLETCHING -> TinkerMaterial.FEATHER;
		};
	}

	public static @Nullable Stats stats(ItemStack stack) {
		return stack.getItem() instanceof TinkerTool tool ? compute(tool.type(), materials(stack)) : null;
	}

	/** A tool stack with these materials, freshly built. */
	public static ItemStack build(ToolType type, List<TinkerMaterial> materials) {
		ItemStack stack = new ItemStack(Tinkering.TOOLS.get(type));
		stack.set(Tinkering.TOOL_MATERIALS, materials.stream().map(m -> m.id).toList());
		apply(stack);
		return stack;
	}

	public static boolean isBroken(ItemStack stack) {
		return stack.getItem() instanceof TinkerTool && stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage();
	}

	/** How worn the tool is, 0 (new) to 1 (broken). */
	static float worn(ItemStack stack) {
		int max = stack.getMaxDamage();
		return max > 0 ? Mth.clamp(stack.getDamageValue() / (float) max, 0.0F, 1.0F) : 0.0F;
	}

	/** Rewrites everything a tool carries from its materials and wear. */
	public static void apply(ItemStack stack) {
		if (!(stack.getItem() instanceof TinkerTool item)) {
			return;
		}
		ToolType type = item.type();
		List<TinkerMaterial> materials = materials(stack);
		Stats stats = compute(type, materials);
		if (!stack.has(Tinkering.TOOL_MATERIALS)) {
			stack.set(Tinkering.TOOL_MATERIALS, materials.stream().map(m -> m.id).toList());
		}
		if (!type.style.durable()) {
			// (0.15.x) arrows and thrown blades are plain stacks: no wear, no modifiers, just their materials' look and name
			stack.remove(DataComponents.MAX_DAMAGE);
			stack.remove(DataComponents.DAMAGE);
			applyLook(stack, type, materials, false);
			return;
		}
		int durability = Modifier.durability(stack, stats.durability());
		stack.set(DataComponents.MAX_DAMAGE, durability);
		stack.set(DataComponents.DAMAGE, Mth.clamp(stack.getDamageValue(), 0, durability));
		boolean broken = isBroken(stack);
		stack.set(DataComponents.TOOL, broken ? new Tool(List.of(), 1.0F, 1, type.style == ToolType.Style.TOOL)
			: tool(type, stats, Modifier.tier(stack, stats.tier())));
		float worn = worn(stack);
		float attack = Modifier.attack(stack, stats.attack()) + worn * (stats.level(Trait.JAGGED) * JAGGED_ATTACK - stats.level(Trait.STONEBOUND) * STONEBOUND_ATTACK);
		ItemAttributeModifiers.Builder attributes = ItemAttributeModifiers.builder();
		if (!broken) {
			attributes.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, Math.max(0.0F, attack),
				AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
		}
		attributes.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, stats.attackSpeed() - 4.0F,
			AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
		stack.set(DataComponents.ATTRIBUTE_MODIFIERS, attributes.build());
		stack.set(DataComponents.WEAPON, new Weapon(type.style == ToolType.Style.TOOL ? 2 : 1, broken ? 0.0F : type.disablesShields));
		// (0.15.x) the battlesign blocks like a shield when held up (right mouse): half of what comes at it from its front
		if (stats.level(Trait.BLOCKING) > 0 && !broken) {
			stack.set(DataComponents.BLOCKS_ATTACKS, new BlocksAttacks(BLOCK_RAISE_SECONDS, 1.0F, List.of(new BlocksAttacks.DamageReduction(
				BLOCK_ANGLE, Optional.empty(), 0.0F, BLOCK_SHARE)), BlocksAttacks.ItemDamageFunction.DEFAULT, Optional.empty(), Optional.empty(),
				Optional.empty()));
		} else {
			stack.remove(DataComponents.BLOCKS_ATTACKS);
		}
		applyLook(stack, type, materials, broken);
	}

	/** Its model (by material, and broken or not) and its name. */
	private static void applyLook(ItemStack stack, ToolType type, List<TinkerMaterial> materials, boolean broken) {
		stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(broken),
			materials.stream().map(m -> m.id).toList(), List.of()));
		stack.set(DataComponents.ITEM_NAME, Component.translatable("jaymospowers.tinkering.named", materials.getFirst().displayName(),
			Component.translatable("item.jaymospowers." + type.itemId())));
	}

	private static Tool tool(ToolType type, Stats stats, TinkerMaterial.Tier tier) {
		if (type.style == ToolType.Style.WEAPON || type.style.shoots() || type.style.thrown()) {
			return new Tool(List.of(), 1.0F, 1, false);
		}
		if (type.style == ToolType.Style.SWORD) {
			List<Tool.Rule> rules = new ArrayList<>(List.of(
				Tool.Rule.minesAndDrops(HolderSet.direct(new Holder[] {Blocks.COBWEB.builtInRegistryHolder()}), 15.0F),
				Tool.Rule.overrideSpeed(tag(BlockTags.SWORD_INSTANTLY_MINES), Float.MAX_VALUE),
				Tool.Rule.overrideSpeed(tag(BlockTags.SWORD_EFFICIENT), 1.5F)));
			// (0.15.x) a dagger also cuts what a hoe does (leaves, hay, moss)
			for (TagKey<Block> mines : type.mines) {
				rules.add(Tool.Rule.minesAndDrops(tag(mines), stats.miningSpeed()));
			}
			return new Tool(rules, 1.0F, 2, false);
		}
		List<Tool.Rule> rules = new ArrayList<>();
		rules.add(Tool.Rule.deniesDrops(tag(tier.incorrect)));
		for (TagKey<Block> mines : type.mines) {
			rules.add(Tool.Rule.minesAndDrops(tag(mines), stats.miningSpeed()));
		}
		// (0.15.x) shears cut cobwebs and wool fast
		if (stats.level(Trait.SHEARS) > 0) {
			rules.add(Tool.Rule.minesAndDrops(HolderSet.direct(new Holder[] {Blocks.COBWEB.builtInRegistryHolder()}), SHEARS_COBWEB_SPEED));
			rules.add(Tool.Rule.minesAndDrops(tag(BlockTags.WOOL), SHEARS_WOOL_SPEED));
		}
		return new Tool(rules, 1.0F, 1, true);
	}

	private static HolderSet<Block> tag(TagKey<Block> tag) {
		return BuiltInRegistries.BLOCK.getOrThrow(tag);
	}

	/** Mining speed with the traits that change as you go (Stonebound with wear, Dwarven with depth, Crumbling by block). */
	public static float liveSpeed(Player player, ItemStack stack, BlockState state, float speed) {
		if (speed <= 1.0F || !(stack.getItem() instanceof TinkerTool)) {
			return speed;
		}
		Stats stats = stats(stack);
		if (stats == null) {
			return speed;
		}
		float bonus = speed * stats.level(Trait.STONEBOUND) * STONEBOUND_SPEED * worn(stack);
		if (!state.requiresCorrectToolForDrops()) {
			bonus += speed * stats.level(Trait.CRUMBLING) * CRUMBLING_SPEED;
		}
		int dwarven = stats.level(Trait.DWARVEN);
		if (dwarven > 0) {
			float steps = Mth.clamp((DWARVEN_TOP - (float) player.getY()) / DWARVEN_STEP, 0.0F, DWARVEN_STEPS);
			bonus += dwarven * DWARVEN_SPEED * steps;
		}
		return speed + bonus;
	}

	/** The wear a hit or block really costs once Tanned, Stringy, Solar Powered and Tempered have had their say. */
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
		if (stats.level(Trait.TEMPERED) > 0) {
			skip += TEMPERED_CHANCE;
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
		if (stats == null || !(stack.getItem() instanceof TinkerTool tool)) {
			return 0;
		}
		float share = REPAIR_FACTOR / tool.type().parts.getFirst().part().cost * (1 + CULTIVATED_REPAIR * stats.level(Trait.CULTIVATED));
		return Math.max(1, Math.round(stack.getMaxDamage() * share));
	}
}
