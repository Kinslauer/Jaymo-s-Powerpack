package com.jaymospowers.costume;

import com.jaymospowers.tinkering.Tinkering;
import com.jaymospowers.tinkering.smeltery.Molten;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * Metal plates sewn into a costume (Andy: "can we work metal plates into the designs too ... could add armor values to the
 * system as a whole"): one plate each on the head, chest, legs and feet, of any {@link Metal}. They ride on the design's
 * {@link CostumeKit}, so a worn costume, a costume item, an armour stand's costume and a corpse's all keep them. A plate is
 * drawn over that part of the suit in the metal's colour (client {@code CostumeModels.plate}) and, while the costume is
 * worn, adds armour points ({@link CostumePlating}).
 * <p>
 * What a plate costs and gives: each takes {@link #INGOTS_PER_PLATE} ingots of its metal, taken from the pack when the
 * costume is sewn ({@link CostumeNet#cost}). Its armour points are the slot's base ({@link Slot#armour}, 7 for a whole
 * suit in iron) times the metal's multiplier ({@link Metal#armour}), and the whole costume never gives more than
 * {@link CostumePlating#MAX_POINTS} (chainmail's 12).
 */
public record CostumePlates(Metal head, Metal chest, Metal legs, Metal feet) {
	/** No plates. */
	public static final CostumePlates NONE = new CostumePlates(Metal.NONE, Metal.NONE, Metal.NONE, Metal.NONE);
	/** How many ingots one plate takes. */
	public static final int INGOTS_PER_PLATE = 2;

	/** Where a plate goes: the equipment slot it stands in for, and the armour points it gives in iron (before the metal's multiplier). */
	public enum Slot implements StringRepresentable {
		HEAD(EquipmentSlot.HEAD, 1.0), CHEST(EquipmentSlot.CHEST, 3.0), LEGS(EquipmentSlot.LEGS, 2.0), FEET(EquipmentSlot.FEET, 1.0);

		public final EquipmentSlot equipment;
		public final double armour;

		Slot(EquipmentSlot equipment, double armour) {
			this.equipment = equipment;
			this.armour = armour;
		}

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/**
	 * The metals a plate can be made of: the ingot it takes, its colour in the tinted plate texture, and its armour
	 * multiplier. Copper is soft, bronze is better, steel (Tinkering's, with netherite as its plating) the best, netherite
	 * heaviest. (Diamond gear is out of this pack, so there is no diamond plate.) Only ever grows at the end.
	 */
	public enum Metal implements StringRepresentable {
		NONE(0, 0.0, () -> null), COPPER(0xFFC77B58, 0.6, () -> Items.COPPER_INGOT), IRON(0xFFD8D8D8, 1.0, () -> Items.IRON_INGOT),
		BRONZE(0xFFCD8F4A, 1.2, () -> Tinkering.ALLOY_INGOTS.get(Molten.BRONZE)), STEEL(0xFFAEB6C2, 1.5, () -> Tinkering.STEEL_INGOT),
		NETHERITE(0xFF5A4E55, 1.7, () -> Items.NETHERITE_INGOT);

		public static final Codec<Metal> CODEC = StringRepresentable.fromEnum(Metal::values);
		public static final StreamCodec<ByteBuf, Metal> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(i -> values()[Math.floorMod(i, values().length)], Enum::ordinal);

		/** The plate's tint (ARGB). */
		public final int colour;
		public final double armour;
		private final Supplier<Item> ingot;

		Metal(int colour, double armour, Supplier<Item> ingot) {
			this.colour = colour;
			this.armour = armour;
			this.ingot = ingot;
		}

		/** The ingot a plate of this metal takes (null for none). */
		public Item ingot() {
			return this.ingot.get();
		}

		@Override
		public String getSerializedName() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	public static final Codec<CostumePlates> CODEC = RecordCodecBuilder.create(i -> i.group(
		Metal.CODEC.optionalFieldOf("head", Metal.NONE).forGetter(CostumePlates::head),
		Metal.CODEC.optionalFieldOf("chest", Metal.NONE).forGetter(CostumePlates::chest),
		Metal.CODEC.optionalFieldOf("legs", Metal.NONE).forGetter(CostumePlates::legs),
		Metal.CODEC.optionalFieldOf("feet", Metal.NONE).forGetter(CostumePlates::feet)).apply(i, CostumePlates::new));

	public static final StreamCodec<ByteBuf, CostumePlates> STREAM_CODEC = StreamCodec.composite(
		Metal.STREAM_CODEC, CostumePlates::head, Metal.STREAM_CODEC, CostumePlates::chest,
		Metal.STREAM_CODEC, CostumePlates::legs, Metal.STREAM_CODEC, CostumePlates::feet, CostumePlates::new);

	/** The metal of the plate at {@code slot}. */
	public Metal at(Slot slot) {
		return switch (slot) {
			case HEAD -> this.head;
			case CHEST -> this.chest;
			case LEGS -> this.legs;
			case FEET -> this.feet;
		};
	}

	public CostumePlates with(Slot slot, Metal metal) {
		return switch (slot) {
			case HEAD -> new CostumePlates(metal, chest, legs, feet);
			case CHEST -> new CostumePlates(head, metal, legs, feet);
			case LEGS -> new CostumePlates(head, chest, metal, feet);
			case FEET -> new CostumePlates(head, chest, legs, metal);
		};
	}

	/** How many plates there are. */
	public int count() {
		int n = 0;
		for (Slot slot : Slot.values()) {
			n += at(slot) != Metal.NONE ? 1 : 0;
		}
		return n;
	}

	public boolean any() {
		return count() > 0;
	}
}
