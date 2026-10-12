package com.jaymoskingdoms;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

/**
 * (0.5.7) For testing in a dev game only (the {@code jaymoskingdoms.devCommands} system property, set by the Gradle
 * run configs; never in the pack): once a second, the lines of {@code commands.txt} in the game folder are run as
 * server commands (as the server, so with every permission), then the file is deleted. Lets a test drive a
 * singleplayer world from outside (set up a scene, move the camera) without a second game or RCON.
 */
final class DevCommands {
	static final String FILE = "commands.txt";
	static final int EVERY = 20;

	private DevCommands() {
	}

	static void register() {
		if (!Boolean.getBoolean("jaymoskingdoms.devCommands")) {
			return;
		}
		ServerTickEvents.END_SERVER_TICK.register(DevCommands::tick);
		net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
			net.minecraft.commands.Commands.literal("kingdomsdev")
				.then(net.minecraft.commands.Commands.literal("fell").executes(c -> fell(c.getSource().getPlayerOrException(), STAND_OFF)))
				.then(net.minecraft.commands.Commands.literal("stock").executes(c -> stock(c.getSource().getPlayerOrException())))));
	}

	/** (0.5.21) {@code /kingdomsdev stock}: the faction nearest the tester gets a stack or four of common building stuff in storage. */
	private static int stock(net.minecraft.server.level.ServerPlayer player) {
		net.minecraft.server.level.ServerLevel level = player.level();
		Faction f = Factions.around(level.getServer(), level, player.blockPosition(), 200);
		if (f == null) {
			return 0;
		}
		for (net.minecraft.world.item.Item item : java.util.List.of(net.minecraft.world.item.Items.OAK_LOG, net.minecraft.world.item.Items.OAK_PLANKS,
			net.minecraft.world.item.Items.COBBLESTONE, net.minecraft.world.item.Items.STONE, net.minecraft.world.item.Items.GLASS_PANE,
			net.minecraft.world.item.Items.TORCH, net.minecraft.world.item.Items.BREAD)) {
			Storage.put(level, f, item, 256);
		}
		JaymosKingdoms.LOGGER.info("[dev] stocked {}", f.name());
		return 1;
	}

	/** How far from a tree the tester stands to fell it, and how far round it a tree is looked for. */
	static final int STAND_OFF = 6, TREE_SEARCH = 40;

	/**
	 * (0.5.9) {@code /kingdomsdev fell}: the natural tree nearest {@code player} is felled as if they'd cut its foot
	 * (they're moved {@code off} blocks from it first, looking at it, so it falls away from them).
	 */
	private static int fell(net.minecraft.server.level.ServerPlayer player, int off) {
		net.minecraft.server.level.ServerLevel level = player.level();
		net.minecraft.core.BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		net.minecraft.core.BlockPos me = player.blockPosition();
		for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(me.offset(-TREE_SEARCH, -12, -TREE_SEARCH),
			me.offset(TREE_SEARCH, 20, TREE_SEARCH))) {
			net.minecraft.world.level.block.state.BlockState s = level.getBlockState(p);
			if (!s.is(net.minecraft.tags.BlockTags.LOGS) || level.getBlockState(p.below()).is(net.minecraft.tags.BlockTags.LOGS)
				|| !level.getBlockState(p.below()).isSolid() || !level.getBlockState(p.above()).is(net.minecraft.tags.BlockTags.LOGS)) {
				continue;
			}
			double d = p.distSqr(me);
			if (d < bestD && Trees.natural(level, p.above(3))) {
				bestD = d;
				best = p.immutable();
			}
		}
		if (best == null) {
			JaymosKingdoms.LOGGER.info("[dev] no tree near {}", me);
			return 0;
		}
		double x = best.getX() + 0.5 - off, z = best.getZ() + 0.5;
		player.teleportTo(level, x, best.getY(), z, java.util.Set.of(), -90.0F, 10.0F, false);
		player.setPos(x, best.getY(), z);
		int logs = Trees.fell(level, best, player, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_AXE), null, false);
		// then off to the side (south of it), to watch it come down eastward
		player.teleportTo(level, best.getX() + 3.5, best.getY() + 4.0, best.getZ() + 18.5, java.util.Set.of(), 180.0F, 8.0F, false);
		JaymosKingdoms.LOGGER.info("[dev] felled the tree at {}: {} logs", best, logs);
		return logs;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		Path file = Path.of(FILE);
		if (!Files.exists(file)) {
			return;
		}
		List<String> lines;
		try {
			lines = Files.readAllLines(file, StandardCharsets.UTF_8);
			Files.delete(file);
		} catch (IOException e) {
			return;
		}
		for (String line : lines) {
			String command = line.strip();
			if (command.isEmpty() || command.startsWith("#")) {
				continue;
			}
			JaymosKingdoms.LOGGER.info("[dev] {}", command);
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
		}
	}
}
