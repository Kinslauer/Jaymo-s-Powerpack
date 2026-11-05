package com.jaymospowers.jewellery;

import com.jaymospowers.baubles.BaubleItem;
import com.jaymospowers.JaymosPowers;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.87) The gems that can be cut on the lapidary wheel: each one's colour, hardness and the bonus it gives. All the
 * rules about a gem live here and in {@link CutGem}, so socketing into weapons and armour can reuse them. Items are kept
 * as ids and looked up when used, so the order other mods register in doesn't matter. The ids are saved on cut gems:
 * never rename them.
 */
public final class Gems {
	/** One cuttable gem. */
	public record Gem(String id, Identifier item, int colour, float hardness, Holder<Attribute> attribute, double amount,
		AttributeModifier.Operation operation) {
		public Component displayName() {
			return Component.translatable("gem.jaymospowers." + id);
		}

		/** Its bonus times {@code scale} (grade, cut, metal and frame factors multiplied). */
		public BaubleItem.Bonus bonus(double scale) {
			return new BaubleItem.Bonus(attribute, amount * scale, operation);
		}

		public Item asItem() {
			return BuiltInRegistries.ITEM.getValue(item);
		}
	}

	private static Identifier mc(String path) {
		return Identifier.withDefaultNamespace(path);
	}

	/** Every cuttable gem, in display order. */
	public static final List<Gem> ALL = List.of(
		new Gem("emerald", mc("emerald"), 0x17DD62, 1.1F, Attributes.LUCK, 0.5, AttributeModifier.Operation.ADD_VALUE),
		new Gem("diamond", mc("diamond"), 0x8FF4E6, 1.3F, Attributes.ARMOR_TOUGHNESS, 0.5, AttributeModifier.Operation.ADD_VALUE),
		new Gem("amethyst", mc("amethyst_shard"), 0xA77BE0, 0.9F, Attributes.MAX_HEALTH, 1.0, AttributeModifier.Operation.ADD_VALUE),
		new Gem("lapis", mc("lapis_lazuli"), 0x2E5CC9, 0.8F, Attributes.WATER_MOVEMENT_EFFICIENCY, 0.08, AttributeModifier.Operation.ADD_VALUE),
		new Gem("quartz", mc("quartz"), 0xEDE6DD, 0.9F, Attributes.BURNING_TIME, -0.08, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
		new Gem("zanite", JaymosPowers.id("zanite_gemstone"), 0x7A6CF0, 1.0F, Attributes.MINING_EFFICIENCY, 1.0, AttributeModifier.Operation.ADD_VALUE),
		new Gem("glint", JaymosPowers.id("glint_gemstone"), 0xF2D16B, 1.0F, Attributes.MOVEMENT_SPEED, 0.02, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
		new Gem("corrobonite", JaymosPowers.id("corrobonite_crystal"), 0x5FC9B8, 1.1F, Attributes.KNOCKBACK_RESISTANCE, 0.05, AttributeModifier.Operation.ADD_VALUE),
		new Gem("golden_amber", JaymosPowers.id("golden_amber"), 0xF0A030, 0.8F, Attributes.OXYGEN_BONUS, 0.5, AttributeModifier.Operation.ADD_VALUE));

	private Gems() {
	}

	/** The gem this raw stack is, or null. */
	public static @Nullable Gem of(ItemStack raw) {
		if (raw.isEmpty()) {
			return null;
		}
		Identifier key = BuiltInRegistries.ITEM.getKey(raw.getItem());
		for (Gem gem : ALL) {
			if (gem.item().equals(key)) {
				return gem;
			}
		}
		return null;
	}

	public static @Nullable Gem byId(String id) {
		for (Gem gem : ALL) {
			if (gem.id().equals(id)) {
				return gem;
			}
		}
		return null;
	}
}
