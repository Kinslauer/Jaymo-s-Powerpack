package com.jaymoskingdoms;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.116) Work areas a lord lays out with the {@link Surveyor}'s rod, kept among the faction's buildings
 * ({@link Works.Built}) so its builders build round them:
 * <ul>
 * <li>A <b>field</b> ({@link #FIELD}): a farmer is taken on to keep it, harvesting and sowing its crops like a farm's
 * ({@link Farmers}). Till it and sow a row or two; he keeps it going.</li>
 * <li>A <b>quarry</b> ({@link #QUARRY}): a pit its miners dig out, the top layer first, a block at a time, down to its
 * floor. They carry what they dig to storage, light it with torches as it gets dark, and stop water and lava with
 * cobblestone before digging next to them. A stair is left along its north edge so they can walk in and out.</li>
 * <li><b>Storage</b>: a chest or barrel added to the settlement's storage (or taken out of it).</li>
 * </ul>
 * {@code /kingdom area list} shows them; {@code /kingdom area clear} unmarks the field or quarry you stand in.
 */
public final class Areas {
	/** The entries a field and a quarry are kept under. */
	public static final String FIELD = "field", QUARRY = "quarry";
	/** A field's sides, at least and at most (a farmer works about {@link Farmers#REACH_SLACK} past its half-width). */
	static final int FIELD_MIN = 3, FIELD_MAX = 17;
	/** A quarry's sides, at least and at most; how deep when the two corners are level, and the deepest. */
	static final int QUARRY_MIN = 3, QUARRY_MAX = 16, QUARRY_DEPTH = 8, QUARRY_DEEPEST = 64;
	/** A quarry never reaches nearer the world's floor than this. */
	static final int FLOOR_MARGIN = 6;
	/** Miners taken on for each quarry. */
	static final int MINERS_PER_QUARRY = 1;
	/** A quarry found dug out isn't looked over again for this long (ticks). */
	static final long DUG_OUT_RECHECK_TICKS = 1200L;
	/** What a miner may stop water or lava with, from what he carries (else a cobblestone from storage). */
	static final List<Item> PLUGS = List.of(Items.COBBLESTONE, Items.COBBLED_DEEPSLATE, Items.STONE, Items.DIRT, Items.ANDESITE, Items.DIORITE,
		Items.GRANITE, Items.TUFF, Items.NETHERRACK);

	/** Quarries (by their lowest corner) found dug out: when to look them over again. */
	private static final Map<BlockPos, Long> RESTING = new HashMap<>();

	private Areas() {
	}

	/** Forgets what it knew of the world (the server stopping). */
	static void clear() {
		RESTING.clear();
	}

	// ---- marking --------------------------------------------------------------------------------------------------

	/**
	 * The faction whose land the box from {@code min} to {@code max} lies in, if {@code player} may lay out its work
	 * there (and, if {@code clear}, it's clear of every building and plot); else tells them why and null.
	 */
	private static @Nullable Faction landFor(ServerPlayer player, BlockPos min, BlockPos max, boolean clear) {
		MinecraftServer server = player.level().getServer();
		Faction f = Factions.at(server, player.level(), min);
		if (f == null) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.plot.not_land").withStyle(ChatFormatting.RED));
			return null;
		}
		if (!Plots.mayMark(player, f)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.plot.lords_only", f.name()).withStyle(ChatFormatting.RED));
			return null;
		}
		for (BlockPos corner : new BlockPos[] {min, max, new BlockPos(min.getX(), 0, max.getZ()), new BlockPos(max.getX(), 0, min.getZ())}) {
			if (!f.claims(ChunkPos.containing(corner))) {
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.plot.outside", f.name()).withStyle(ChatFormatting.RED));
				return null;
			}
		}
		if (clear) {
			for (var e : Works.all(server).entrySet()) {
				for (Works.Built b : e.getValue().built()) {
					if (overlaps(min, max, b.min(), b.max())) {
						player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.area.overlap",
							Component.translatableWithFallback("building.jaymoskingdoms." + b.entry(), b.entry())).withStyle(ChatFormatting.RED));
						return null;
					}
				}
				for (Works.Plot p : e.getValue().plots()) {
					if (overlaps(min, max, p.min(), p.max())) {
						player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.plot.overlap", p.id()).withStyle(ChatFormatting.RED));
						return null;
					}
				}
			}
		}
		return f;
	}

	private static boolean overlaps(BlockPos min, BlockPos max, BlockPos otherMin, BlockPos otherMax) {
		return min.getX() <= otherMax.getX() && max.getX() >= otherMin.getX() && min.getZ() <= otherMax.getZ() && max.getZ() >= otherMin.getZ();
	}

	private static void add(MinecraftServer server, Faction f, Works.Built area) {
		Works.update(server, f.id(), s -> {
			List<Works.Built> built = new ArrayList<>(s.built());
			built.add(area);
			return s.withBuilt(built);
		});
	}

	/** Marks a field with corners {@code a} and {@code b} (its height the lower one's); true if marked. */
	static boolean field(ServerPlayer player, BlockPos a, BlockPos b) {
		BlockPos lo = Surveyor.min(a, b), hi = Surveyor.max(a, b);
		BlockPos min = lo, max = new BlockPos(hi.getX(), lo.getY(), hi.getZ());
		int w = max.getX() - min.getX() + 1, d = max.getZ() - min.getZ() + 1;
		if (w < FIELD_MIN || d < FIELD_MIN || w > FIELD_MAX || d > FIELD_MAX) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.area.field_size", w, d, FIELD_MIN, FIELD_MAX).withStyle(ChatFormatting.RED));
			return false;
		}
		Faction f = landFor(player, min, max, true);
		if (f == null) {
			return false;
		}
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		add(server, f, new Works.Built(FIELD, min, max));
		Surveyor.outline(level, min, max);
		level.playSound(null, b, SoundEvents.HOE_TILL.value(), SoundSource.PLAYERS, 0.8F, 1.0F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.area.field_marked", w, d, f.name()).withStyle(ChatFormatting.GREEN));
		Faction now = Factions.get(server, f.id());
		if (now != null && Farmers.wanted(server, now) && Farmers.spawn(level, now, Economy.recruit(level, now)) != null) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.area.farmer_hired", f.name()).withStyle(ChatFormatting.GRAY));
		}
		return true;
	}

	/**
	 * Marks a quarry with corners {@code a} and {@code b}: its top the higher one, its floor the lower one if it's at
	 * least two below, else {@link #QUARRY_DEPTH} deep. True if marked.
	 */
	static boolean quarry(ServerPlayer player, BlockPos a, BlockPos b) {
		ServerLevel level = player.level();
		BlockPos lo = Surveyor.min(a, b), hi = Surveyor.max(a, b);
		int top = hi.getY();
		int floor = lo.getY() <= top - 2 ? lo.getY() : top - QUARRY_DEPTH + 1;
		floor = Math.max(Math.max(floor, top - QUARRY_DEEPEST + 1), level.getMinY() + FLOOR_MARGIN);
		BlockPos min = new BlockPos(lo.getX(), floor, lo.getZ()), max = new BlockPos(hi.getX(), top, hi.getZ());
		int w = max.getX() - min.getX() + 1, d = max.getZ() - min.getZ() + 1;
		if (w < QUARRY_MIN || d < QUARRY_MIN || w > QUARRY_MAX || d > QUARRY_MAX || floor > top) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.area.quarry_size", w, d, QUARRY_MIN, QUARRY_MAX).withStyle(ChatFormatting.RED));
			return false;
		}
		Faction f = landFor(player, min, max, true);
		if (f == null) {
			return false;
		}
		MinecraftServer server = level.getServer();
		add(server, f, new Works.Built(QUARRY, min, max));
		Surveyor.outline(level, min, max);
		level.playSound(null, b, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.8F, 0.8F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.area.quarry_marked", w, d, top - floor + 1, f.name())
			.withStyle(ChatFormatting.GREEN));
		Faction now = Factions.get(server, f.id());
		if (now != null && Gatherers.wanted(server, now, Folk.MINER)) {
			Gatherers.hire(level, now, Folk.MINER, 1);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.area.miner_hired", f.name()).withStyle(ChatFormatting.GRAY));
		}
		return true;
	}

	/** Adds the chest or barrel at {@code pos} to its settlement's storage, or takes it out if it's in. True if either. */
	static boolean toggleStorage(ServerPlayer player, BlockPos pos) {
		ServerLevel level = player.level();
		MinecraftServer server = level.getServer();
		if (!(level.getBlockEntity(pos) instanceof Container)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.area.not_container").withStyle(ChatFormatting.RED));
			return false;
		}
		Faction f = landFor(player, pos, pos, false);
		if (f == null) {
			return false;
		}
		List<BlockPos> both = new ArrayList<>(List.of(pos.immutable()));
		BlockState state = level.getBlockState(pos);
		if (state.getBlock() instanceof ChestBlock && state.hasProperty(BlockStateProperties.CHEST_TYPE)
			&& state.getValue(BlockStateProperties.CHEST_TYPE) != ChestType.SINGLE) {
			// (a double chest: both halves)
			both.add(pos.relative(ChestBlock.getConnectedDirection(state)).immutable());
		}
		Faction owner = Factions.storageOwner(server, pos);
		if (owner != null && owner.id() != f.id()) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.area.storage_other", owner.name()).withStyle(ChatFormatting.RED));
			return false;
		}
		if (owner != null) {
			if (f.holdings().storage().size() <= both.size() || f.holdings().depot().filter(both::contains).isPresent()) {
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.area.storage_keep", f.name()).withStyle(ChatFormatting.RED));
				return false;
			}
			Factions.update(server, f.id(), x -> {
				List<BlockPos> left = new ArrayList<>(x.holdings().storage());
				left.removeAll(both);
				return x.withHoldings(x.holdings().withStorage(left, x.holdings().depot()));
			});
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.area.storage_removed", f.name()).withStyle(ChatFormatting.YELLOW));
			return true;
		}
		if (f.holdings().storage().isEmpty()) {
			Storage.register(server, f.id(), both, pos.immutable());
		} else {
			Storage.add(server, f.id(), both);
		}
		level.playSound(null, pos, SoundEvents.CHEST_CLOSE, SoundSource.PLAYERS, 0.6F, 1.2F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.area.storage_added", f.name(), f.holdings().storage().size() + both.size())
			.withStyle(ChatFormatting.GREEN));
		return true;
	}

	// ---- quarries -------------------------------------------------------------------------------------------------

	static List<Works.Built> quarries(MinecraftServer server, Faction f) {
		return Works.of(server, f.id()).built().stream().filter(b -> b.entry().equals(QUARRY)).toList();
	}

	/** Where a quarry's miner lives: on the ground at the top of its stair. */
	static BlockPos home(Works.Built quarry) {
		return new BlockPos(quarry.min().getX(), quarry.max().getY() + 1, quarry.min().getZ());
	}

	/**
	 * The block at {@code pos} is part of the quarry's stair and stays: along its north edge, the step {@code j} blocks
	 * from its west corner is dug only {@code j} layers down.
	 */
	static boolean stair(Works.Built quarry, BlockPos pos) {
		if (pos.getZ() != quarry.min().getZ()) {
			return false;
		}
		int step = pos.getX() - quarry.min().getX(), layer = quarry.max().getY() - pos.getY();
		return layer >= step;
	}

	/** A miner digs this out of a quarry: something dry he can break that's in the way (not grass, a container, nor his own torches). */
	static boolean diggable(ServerLevel level, BlockPos pos, BlockState state) {
		return !state.isAir() && !state.canBeReplaced() && state.getFluidState().isEmpty() && !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0
			&& !state.is(Blocks.TORCH) && !state.is(Blocks.WALL_TORCH);
	}

	/**
	 * The next block for a miner near {@code near} in one of {@code f}'s quarries (the nearest quarry with work left
	 * first): in its highest layer with anything left to dig, the nearest to him not in {@code skip}. Null if they're
	 * all dug out (or what's left can't be reached).
	 */
	static @Nullable BlockPos next(ServerLevel level, Faction f, BlockPos near, Set<BlockPos> skip) {
		List<Works.Built> all = new ArrayList<>(quarries(level.getServer(), f));
		all.sort(Comparator.comparingDouble(q -> q.min().atY(near.getY()).distSqr(near)));
		long now = level.getGameTime();
		for (Works.Built q : all) {
			if (!level.isLoaded(q.min()) || !level.isLoaded(q.max()) || now < RESTING.getOrDefault(q.min(), 0L)) {
				continue;
			}
			for (int y = q.max().getY(); y >= q.min().getY(); y--) {
				BlockPos best = null;
				double bestD = Double.MAX_VALUE;
				for (int x = q.min().getX(); x <= q.max().getX(); x++) {
					for (int z = q.min().getZ(); z <= q.max().getZ(); z++) {
						BlockPos pos = new BlockPos(x, y, z);
						if (stair(q, pos) || skip.contains(pos) || !diggable(level, pos, level.getBlockState(pos))) {
							continue;
						}
						double d = pos.distSqr(near);
						if (d < bestD) {
							bestD = d;
							best = pos;
						}
					}
				}
				if (best != null) {
					return best;
				}
			}
			// (nothing left: not looked over again for a while)
			RESTING.put(q.min(), now + DUG_OUT_RECHECK_TICKS);
		}
		return null;
	}

	/**
	 * Stops any water or lava beside {@code at} with a block from what the miner carries ({@code bag}), else a
	 * cobblestone from storage. True if none is left beside it.
	 */
	static boolean plug(ServerLevel level, Faction f, Worker folk, Gatherers.Bag bag, BlockPos at) {
		for (Direction d : Direction.values()) {
			BlockPos n = at.relative(d);
			if (level.getFluidState(n).isEmpty()) {
				continue;
			}
			Item with = null;
			for (ItemStack held : bag.items) {
				if (PLUGS.contains(held.getItem()) && !held.isEmpty()) {
					with = held.getItem();
					held.shrink(1);
					break;
				}
			}
			bag.items.removeIf(ItemStack::isEmpty);
			if (with == null && !Storage.take(level, f, Items.COBBLESTONE, 1).isEmpty()) {
				with = Items.COBBLESTONE;
			}
			if (with == null) {
				return false;
			}
			Block block = Block.byItem(with);
			level.setBlock(n, block.defaultBlockState(), Block.UPDATE_ALL);
			level.playSound(null, n, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.7F, 1.0F);
			folk.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		}
		return true;
	}

	// ---- commands -------------------------------------------------------------------------------------------------

	/** {@code /kingdom area list | clear}. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("area")
			.then(Commands.literal("list").executes(Areas::list))
			.then(Commands.literal("clear").executes(Areas::clear));
	}

	private static @Nullable Faction here(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Faction f = Factions.at(server, player.level(), player.blockPosition());
		return f != null ? f : Factions.of(server, player.getUUID());
	}

	private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		Faction f = here(player);
		if (f == null) {
			c.getSource().sendFailure(Component.translatable("message.jaymoskingdoms.no_such"));
			return 0;
		}
		MinecraftServer server = player.level().getServer();
		List<Works.Built> areas = Works.of(server, f.id()).built().stream().filter(b -> b.entry().equals(FIELD) || b.entry().equals(QUARRY)).toList();
		c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.area.header", f.name(), f.holdings().storage().size())
			.withStyle(ChatFormatting.GOLD), false);
		for (Works.Built b : areas) {
			int w = b.max().getX() - b.min().getX() + 1, d = b.max().getZ() - b.min().getZ() + 1, h = b.max().getY() - b.min().getY() + 1;
			c.getSource().sendSuccess(() -> Component.translatable("message.jaymoskingdoms.area.line." + b.entry(), b.min().toShortString(), w, d, h)
				.withStyle(ChatFormatting.GRAY), false);
			if (player.level() == Factions.level(server)) {
				Surveyor.outline(player.level(), b.min(), b.entry().equals(QUARRY) ? b.max() : b.max().atY(b.min().getY()));
			}
		}
		return areas.size();
	}

	private static int clear(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		MinecraftServer server = player.level().getServer();
		BlockPos at = player.blockPosition();
		for (var e : Works.all(server).entrySet()) {
			for (Works.Built b : e.getValue().built()) {
				if (!(b.entry().equals(FIELD) || b.entry().equals(QUARRY)) || at.getX() < b.min().getX() || at.getX() > b.max().getX()
					|| at.getZ() < b.min().getZ() || at.getZ() > b.max().getZ()) {
					continue;
				}
				Faction f = Factions.get(server, e.getKey());
				if (f == null || !Plots.mayMark(player, f)) {
					player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.plot.lords_only", f == null ? "?" : f.name()).withStyle(ChatFormatting.RED));
					return 0;
				}
				Works.update(server, f.id(), s -> {
					List<Works.Built> built = new ArrayList<>(s.built());
					built.remove(b);
					return s.withBuilt(built);
				});
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.area.cleared." + b.entry()).withStyle(ChatFormatting.YELLOW));
				return 1;
			}
		}
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.area.none_here").withStyle(ChatFormatting.RED));
		return 0;
	}
}
