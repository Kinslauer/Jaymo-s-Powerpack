package com.jaymospowers.tinkering.dangers;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.Tinkering;
import com.jaymospowers.tinkering.forge.ForgeHearthBlock;
import com.jaymospowers.tinkering.kiln.Heat;
import com.jaymospowers.tinkering.kiln.Kiln;
import com.jaymospowers.tinkering.smithing.HotMetal;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Andy's GT New Horizons picks, patch 5: the forge's dangers (docs/GTNH-Plan.md).
 * <ul>
 * <li><b>Hot walls burn.</b> Leaning on or standing on a lit forge hearth, a kiln's brick over a fire, or the walls of a
 * working smeltery, coke oven or brick blast furnace burns a little ({@link #WALL_BURN} every {@link #WALL_CHECK_TICKS}).
 * Only the blocks a player's box touches are looked at, and the multiblocks report their walls hot themselves
 * ({@link #working}), so nothing scans for them. Anything between (planks, a slab, a trapdoor) keeps you off the wall.</li>
 * <li><b>Rain on the fire.</b> The fires call {@link #wet} and {@link #storm} on the spot over them that's open to the sky:
 * rain slows them, a thunderstorm puts them out ({@link #douse}); see each machine.</li>
 * <li><b>Upkeep.</b> Seared and kiln bricks crack ({@link Cracks}); a hammer and grout mend them ({@link #mend}).</li>
 * <li><b>Hot to hold.</b> Lava buckets and blaze rods in the hand ({@link #HOT_TO_HOLD}) and a pot off the fire
 * ({@link HotPots}) burn like hot metal; tongs or {@link SmithingGloves} take it ({@code HotMetal}).</li>
 * </ul>
 */
public final class ForgeDangers {
	/** How often each player is checked against hot walls (ticks), how near counts as touching (blocks), and the burn. */
	public static final int WALL_CHECK_TICKS = 20;
	public static final double TOUCH = 0.05;
	public static final float WALL_BURN = 1.0F;
	/** A working multiblock's walls stay hot this long after it last said so (ticks): a little over its own check. */
	static final int HOT_TTL = 60;
	/** How often a fire looks up at the weather (ticks). */
	public static final int RAIN_CHECK_TICKS = 40;

	/** Items too hot to hold bare-handed: lava buckets and blaze rods. */
	public static final TagKey<Item> HOT_TO_HOLD = TagKey.create(Registries.ITEM, JaymosPowers.id("hot_to_hold"));
	/** The walls of the smeltery, the foundry, the coke oven and the brick blast furnace (hot while it works). */
	public static final TagKey<Block> FORGE_WALLS = TagKey.create(Registries.BLOCK, JaymosPowers.id("forge_walls"));
	/** What mends a cracked brick (the tag the hammer recipes use). */
	public static final TagKey<Item> HAMMERS = TagKey.create(Registries.ITEM, JaymosPowers.id("hammers"));

	/** A working multiblock's walls, hot until the game time {@code until}. */
	private record Hot(AABB box, long until) {
	}

	private static final Map<ResourceKey<Level>, Map<BlockPos, Hot>> HOT = new HashMap<>();

	private ForgeDangers() {
	}

	public static void init() {
		Cracks.register();
		SmithingGloves.register();
		HotPots.init();
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if ((server.getTickCount() + player.getId()) % WALL_CHECK_TICKS == 0) {
					touchWalls(player);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> HOT.clear());
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> mend(player, level, hand, hit.getBlockPos()));
		CreativeModeTabEvents.modifyOutputEvent(Kiln.tab()).register(output -> {
			output.accept(SmithingGloves.ITEM);
			Cracks.ours().forEach(output::accept);
		});
	}

	// --- hot walls ---

	/** A multiblock reports its walls (the box {@code min}..{@code max}, inclusive) hot while it works. */
	public static void working(ServerLevel level, BlockPos controller, BlockPos min, BlockPos max) {
		HOT.computeIfAbsent(level.dimension(), k -> new HashMap<>())
			.put(controller.immutable(), new Hot(AABB.encapsulatingFullBlocks(min, max), level.getGameTime() + HOT_TTL));
	}

	/** It has stopped: its walls cool at once. */
	public static void cooled(ServerLevel level, BlockPos controller) {
		Map<BlockPos, Hot> hot = HOT.get(level.dimension());
		if (hot != null) {
			hot.remove(controller);
		}
	}

	private static void touchWalls(ServerPlayer player) {
		if (HotMetal.spared(player) || player.fireImmune()) {
			return;
		}
		ServerLevel level = player.level();
		AABB touch = player.getBoundingBox().inflate(TOUCH);
		Map<BlockPos, Hot> hot = HOT.get(level.dimension());
		long now = level.getGameTime();
		if (hot != null) {
			hot.values().removeIf(h -> h.until() < now);
		}
		for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(touch.minX), Mth.floor(touch.minY), Mth.floor(touch.minZ),
			Mth.floor(touch.maxX), Mth.floor(touch.maxY), Mth.floor(touch.maxZ))) {
			BlockState state = level.getBlockState(pos);
			if (hotWall(level, pos, state, hot)) {
				burn(level, player, pos, state);
				return;
			}
		}
	}

	private static boolean hotWall(ServerLevel level, BlockPos pos, BlockState state, Map<BlockPos, Hot> hot) {
		if (state.getBlock() instanceof ForgeHearthBlock) {
			return state.getValue(ForgeHearthBlock.LIT);
		}
		if (state.is(Kiln.KILN_WALLS)) {
			return kilnWallOverFire(level, pos);
		}
		if (hot != null && !hot.isEmpty() && state.is(FORGE_WALLS)) {
			for (Hot h : hot.values()) {
				if (h.box().contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)) {
					return true;
				}
			}
		}
		return false;
	}

	/** A kiln brick beside (or over) a kiln's space with a fire burning under it. */
	private static boolean kilnWallOverFire(Level level, BlockPos wall) {
		for (Direction side : Direction.values()) {
			if (side == Direction.UP) {
				continue;
			}
			BlockPos space = wall.relative(side);
			BlockState in = level.getBlockState(space);
			if ((in.isAir() || in.is(Kiln.KILN_LOAD)) && Heat.at(level, space.below()) > 0 && Kiln.walled(level, space)) {
				return true;
			}
		}
		return false;
	}

	private static void burn(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		if (player.hurtServer(level, level.damageSources().inFire(), WALL_BURN)) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.3F, 1.8F);
			level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 0.4, player.getZ(), 4, 0.25, 0.3, 0.25, 0.01);
		}
	}

	// --- rain ---

	/** Whether rain is falling on {@code open} (the spot over a fire that's open to the sky). */
	public static boolean wet(Level level, BlockPos open) {
		return level.isRainingAt(open);
	}

	/** Whether a thunderstorm's downpour is falling on {@code open}. */
	public static boolean storm(Level level, BlockPos open) {
		return level.isThundering() && level.isRainingAt(open);
	}

	/** The hiss of rain on a fire (a puff of steam), or ({@code doused}) of one going out. */
	public static void hiss(ServerLevel level, BlockPos at, boolean doused) {
		level.playSound(null, at, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, doused ? 1.0F : 0.3F, doused ? 0.8F : 1.4F + level.getRandom().nextFloat() * 0.3F);
		level.sendParticles(doused ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CLOUD, at.getX() + 0.5, at.getY() + 0.2, at.getZ() + 0.5,
			doused ? 12 : 2, 0.35, 0.1, 0.35, 0.02);
	}

	/** Puts out the fire at {@code fire} (a campfire, a fire, a lit hearth; lava and magma don't go out). True if it did. */
	public static boolean douse(ServerLevel level, BlockPos fire) {
		BlockState state = level.getBlockState(fire);
		if ((state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)) && state.getValue(CampfireBlock.LIT)) {
			CampfireBlock.douse(null, level, fire, state);
			level.setBlock(fire, state.setValue(CampfireBlock.LIT, false), Block.UPDATE_ALL);
		} else if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
			level.removeBlock(fire, false);
		} else if (state.getBlock() instanceof ForgeHearthBlock && state.getValue(ForgeHearthBlock.LIT)) {
			level.setBlock(fire, state.setValue(ForgeHearthBlock.LIT, false), Block.UPDATE_ALL);
		} else {
			return false;
		}
		hiss(level, fire, true);
		return true;
	}

	// --- mending ---

	/** A hammer on a cracked brick, with grout in the other hand or the pack: the grout is pressed in and the brick mended. */
	private static InteractionResult mend(Player player, Level level, InteractionHand hand, BlockPos pos) {
		ItemStack hammer = player.getItemInHand(hand);
		if (player.isSpectator() || !hammer.is(HAMMERS)) {
			return InteractionResult.PASS;
		}
		BlockState state = level.getBlockState(pos);
		BlockState mended = Cracks.mendedOf(state);
		if (mended == null) {
			return InteractionResult.PASS;
		}
		ItemStack grout = grout(player);
		if (grout.isEmpty() && !player.hasInfiniteMaterials()) {
			return InteractionResult.PASS;
		}
		if (level instanceof ServerLevel server) {
			if (!player.hasInfiniteMaterials()) {
				grout.shrink(1);
			}
			server.setBlock(pos, mended, Block.UPDATE_ALL);
			hammer.hurtAndBreak(1, player, hand);
			server.playSound(null, pos, SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.35F, 1.6F + server.getRandom().nextFloat() * 0.2F);
			server.playSound(null, pos, SoundEvents.GRAVEL_PLACE, SoundSource.BLOCKS, 0.8F, 0.9F);
			server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Tinkering.GROUT.defaultBlockState()),
				pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.35, 0.35, 0.35, 0.05);
		}
		return InteractionResult.SUCCESS;
	}

	/** Grout from the off hand first, then the pack; empty if there's none. */
	private static ItemStack grout(Player player) {
		Item grout = Tinkering.GROUT.asItem();
		if (player.getOffhandItem().is(grout)) {
			return player.getOffhandItem();
		}
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(grout)) {
				return inventory.getItem(i);
			}
		}
		return ItemStack.EMPTY;
	}
}
