package com.jaymospowers.dino;

import com.jaymospowers.dino.block.DinoSkullBlock;
import com.jaymospowers.dino.block.DinoSkullBlockEntity;
import com.jaymospowers.dino.entity.Species;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

public final class DinoBlocks {
	private static final Map<Species, DinoSkullBlock> SKULLS = new EnumMap<>(Species.class);
	public static BlockEntityType<DinoSkullBlockEntity> SKULL_ENTITY;

	private DinoBlocks() {
	}

	public static DinoSkullBlock skull(Species s) {
		return SKULLS.get(s);
	}

	public static void init() {
		for (Species s : Species.values()) {
			ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Dino.id(s.id + "_skull"));
			SKULLS.put(s, Registry.register(BuiltInRegistries.BLOCK, key, new DinoSkullBlock(s,
				Properties.of().setId(key).mapColor(MapColor.SAND).strength(1.0F).sound(SoundType.BONE_BLOCK).noOcclusion().pushReaction(PushReaction.POPPED))));
		}
		Set<Block> blocks = new HashSet<>(SKULLS.values());
		SKULL_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, Dino.id("dino_skull"), new BlockEntityType<>(DinoSkullBlockEntity::new, blocks));
	}
}
