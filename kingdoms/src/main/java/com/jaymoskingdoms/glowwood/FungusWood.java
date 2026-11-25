package com.jaymoskingdoms.glowwood;

import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.material.MapColor;

/**
 * The three giant fungi that give a full wood set (ported from Spore & Stem, Apache-2.0). Sounds follow the nether
 * stems: soft, fibrous, and not flammable. The set and wood types only carry sounds here (doors, gates); they are
 * not registered, since there are no signs.
 */
public enum FungusWood {
	GLOWCAP("glowcap", MapColor.COLOR_LIGHT_BLUE, MapColor.TERRACOTTA_WHITE, MapColor.COLOR_CYAN),
	BOUNCECAP("bouncecap", MapColor.TERRACOTTA_PINK, MapColor.TERRACOTTA_WHITE, MapColor.COLOR_PINK),
	INKCAP("inkcap", MapColor.TERRACOTTA_BLACK, MapColor.TERRACOTTA_GRAY, MapColor.COLOR_GRAY);

	private final String id;
	private final MapColor planks, stem, cap;
	private final BlockSetType setType;
	private final WoodType woodType;

	FungusWood(String id, MapColor planks, MapColor stem, MapColor cap) {
		this.id = id;
		this.planks = planks;
		this.stem = stem;
		this.cap = cap;
		String name = JaymosKingdoms.MOD_ID + ":" + id;
		this.setType = new BlockSetType(name, true, true, true,
			BlockSetType.PressurePlateSensitivity.EVERYTHING, SoundType.NETHER_WOOD,
			SoundEvents.NETHER_WOOD_DOOR_CLOSE, SoundEvents.NETHER_WOOD_DOOR_OPEN,
			SoundEvents.NETHER_WOOD_TRAPDOOR_CLOSE, SoundEvents.NETHER_WOOD_TRAPDOOR_OPEN,
			SoundEvents.NETHER_WOOD_PRESSURE_PLATE_CLICK_OFF, SoundEvents.NETHER_WOOD_PRESSURE_PLATE_CLICK_ON,
			SoundEvents.NETHER_WOOD_BUTTON_CLICK_OFF, SoundEvents.NETHER_WOOD_BUTTON_CLICK_ON);
		this.woodType = new WoodType(name, this.setType, SoundType.NETHER_WOOD,
			SoundType.NETHER_WOOD_HANGING_SIGN, SoundEvents.NETHER_WOOD_FENCE_GATE_CLOSE,
			SoundEvents.NETHER_WOOD_FENCE_GATE_OPEN);
	}

	/** The wood with this id ("glowcap", "bouncecap", "inkcap"), or null. */
	public static FungusWood byId(String id) {
		for (FungusWood wood : values()) {
			if (wood.id.equals(id)) {
				return wood;
			}
		}
		return null;
	}

	public String id() {
		return this.id;
	}

	public MapColor planksColor() {
		return this.planks;
	}

	public MapColor stemColor() {
		return this.stem;
	}

	public MapColor capColor() {
		return this.cap;
	}

	public BlockSetType setType() {
		return this.setType;
	}

	public WoodType woodType() {
		return this.woodType;
	}
}
