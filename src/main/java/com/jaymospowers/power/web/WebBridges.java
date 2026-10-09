package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;

/**
 * Weblines: a taut web strung between two points you aim at (press B on the first, let go on the second), thick enough
 * to walk across. It's a row of thin web planks along the line, filling only empty air, and it frays away after
 * {@link #BRIDGE_TICKS}. The same two-point idea as hitching a creature to a block with B. It's Web's starter, and it
 * stays on B for good.
 */
public final class WebBridges {
	/** Longest line, how long it lasts, and the cooldown after stringing one. */
	public static final double MAX_LENGTH = 24.0;
	public static final int BRIDGE_TICKS = 1200;
	public static final int COOLDOWN = 160;
	/** How far you can reach to either end. */
	public static final double REACH = 24.0;

	public static Block BRIDGE;

	private record Plank(ResourceKey<Level> dimension, BlockPos pos, long until) {
	}

	private static final List<Plank> PLANKS = new ArrayList<>();
	private static long clock;

	private WebBridges() {
	}

	static void register() {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, JaymosPowers.id("web_bridge"));
		BRIDGE = Registry.register(BuiltInRegistries.BLOCK, key, new WebBridgeBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.WOOL)
			.strength(0.2F)
			.noLootTable()
			.noOcclusion()
			.sound(SoundType.WOOL)
			.pushReaction(PushReaction.POPPED)
			.setId(key)));
		ServerTickEvents.END_SERVER_TICK.register(WebBridges::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			// a stopping server takes its weblines with it (nothing left hanging in the save)
			for (Plank plank : PLANKS) {
				ServerLevel level = server.getLevel(plank.dimension());
				if (level != null && level.isLoaded(plank.pos()) && level.getBlockState(plank.pos()).is(BRIDGE)) {
					level.setBlock(plank.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				}
			}
			PLANKS.clear();
		});
	}

	/** Strings a webline from {@code from} to {@code to}. Returns false if it's too long. */
	static boolean string(ServerLevel level, Vec3 from, Vec3 to) {
		double length = from.distanceTo(to);
		if (length > MAX_LENGTH || length < 1.0) {
			return false;
		}
		// planks along the line, just under it, so the line is the walking surface
		Set<BlockPos> spots = new LinkedHashSet<>();
		int steps = (int)Math.ceil(length * 3.0);
		for (int i = 0; i <= steps; i++) {
			Vec3 at = from.lerp(to, i / (double)steps);
			spots.add(BlockPos.containing(at.x, at.y - 0.1, at.z));
		}
		for (BlockPos pos : spots) {
			if (level.getBlockState(pos).isAir() && level.getFluidState(pos).isEmpty()) {
				level.setBlock(pos, BRIDGE.defaultBlockState(), Block.UPDATE_ALL);
				PLANKS.add(new Plank(level.dimension(), pos.immutable(), clock + BRIDGE_TICKS));
			}
		}
		level.playSound(null, to.x, to.y, to.z, SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 1.3F);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, from.x, from.y, from.z, 6, 0.1, 0.1, 0.1, 0.02);
		level.sendParticles(ParticleTypes.ITEM_COBWEB, to.x, to.y, to.z, 6, 0.1, 0.1, 0.1, 0.02);
		return true;
	}

	private static void tick(MinecraftServer server) {
		clock++;
		for (Iterator<Plank> it = PLANKS.iterator(); it.hasNext(); ) {
			Plank plank = it.next();
			if (clock < plank.until()) {
				continue;
			}
			ServerLevel level = server.getLevel(plank.dimension());
			if (level == null) {
				it.remove();
				continue;
			}
			if (!level.isLoaded(plank.pos())) {
				continue; // wait for its chunk to come back
			}
			it.remove();
			if (level.getBlockState(plank.pos()).is(BRIDGE)) {
				level.setBlock(plank.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				level.sendParticles(ParticleTypes.ITEM_COBWEB, plank.pos().getX() + 0.5, plank.pos().getY() + 0.1, plank.pos().getZ() + 0.5,
					2, 0.3, 0.05, 0.3, 0.0);
			}
		}
	}
}
