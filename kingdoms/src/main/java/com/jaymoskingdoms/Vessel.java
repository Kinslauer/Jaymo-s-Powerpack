package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

/**
 * A soul urn: the vessel a great necromancer ({@link GreatNecromancer}) hides his life in. Black clay, stoppered with wax and
 * cracked with violet light. It drops nothing and has no item: it is only ever found, buried, where {@link Necropolis} put
 * it. Breaking it by any means ends the necromancer's rising ({@link Necropolis#smashed}).
 */
public final class Vessel extends Block {
	public static Block BLOCK;

	private Vessel(Properties properties) {
		super(properties);
	}

	static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosKingdoms.id("soul_urn"));
		BLOCK = Registry.register(BuiltInRegistries.BLOCK, key, new Vessel(BlockBehaviour.Properties.of()
			.mapColor(MapColor.COLOR_BLACK)
			.strength(2.0F, 6.0F)
			.sound(SoundType.DECORATED_POT)
			.noOcclusion()
			.noLootTable()
			.setId(key)));
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (level instanceof ServerLevel server) {
			Necropolis.smashed(server, pos);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}
