package com.jaymospowers.power.zfighter.farworld;

import com.jaymospowers.power.zfighter.ZFighterSpace;
import com.jaymospowers.tinkering.TinkerMaterial;
import com.jaymospowers.tinkering.Tinkering;
import com.jaymospowers.tinkering.smeltery.Molten;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * (0.18.53, SevTech: far worlds restart the stone age) The six far worlds, in the star map's order, each with the first metal
 * that bites its ore, the rock lying about on it, and the stone its veiled ore passes for.
 */
public enum FarWorld {
	MOON("moon", ZFighterSpace.MOON, Molten.PALE_ALLOY, VeiledOreBlock.Host.STONE),
	RUSTFALL("rustfall", ZFighterSpace.RUSTFALL, Molten.RUST_IRON, VeiledOreBlock.Host.STONE),
	GLACIA("glacia", ZFighterSpace.GLACIA, Molten.ICE_IRON, VeiledOreBlock.Host.STONE),
	CINDER("cinder", ZFighterSpace.CINDER, Molten.CINDER_BRONZE, VeiledOreBlock.Host.NETHERRACK),
	MAW("maw", ZFighterSpace.MAW, Molten.VOID_SHARD, VeiledOreBlock.Host.BLACKSTONE),
	VERDANT("verdant", ZFighterSpace.VERDANT, Molten.AMBER_BRONZE, VeiledOreBlock.Host.STONE);

	public final String key;
	/** Its place on the pod's map ({@link ZFighterSpace#MOON} and the rest), also its chart's number. */
	public final int dest;
	public final Molten molten;
	/** What the veiled ore looks like here when it is not deep in the ground. */
	public final VeiledOreBlock.Host host;

	FarWorld(String key, int dest, Molten molten, VeiledOreBlock.Host host) {
		this.key = key;
		this.dest = dest;
		this.molten = molten;
		this.host = host;
	}

	/** The first metal as a tool material. */
	public TinkerMaterial metal() {
		return molten.material;
	}

	/** Its ingot (the first metal's item). */
	public Item ingot() {
		return Tinkering.ALLOY_INGOTS.get(molten);
	}

	/** The loose rock that lies on this world. */
	public Block rock() {
		return FarWorlds.ROCKS.get(this);
	}

	/** Which far world {@code level} is, or null. */
	public static @Nullable FarWorld of(Level level) {
		for (FarWorld world : values()) {
			if (ZFighterSpace.DESTS[world.dest].level() == level.dimension()) {
				return world;
			}
		}
		return null;
	}

	/** The world whose first metal is {@code molten}, or null. */
	public static @Nullable FarWorld ofMolten(Molten molten) {
		for (FarWorld world : values()) {
			if (world.molten == molten) {
				return world;
			}
		}
		return null;
	}

	public static @Nullable FarWorld byKey(String key) {
		for (FarWorld world : values()) {
			if (world.key.equals(key)) {
				return world;
			}
		}
		return null;
	}
}
