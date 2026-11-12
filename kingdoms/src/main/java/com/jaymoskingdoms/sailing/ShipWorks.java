package com.jaymoskingdoms.sailing;

import com.jaymoskingdoms.JaymosKingdoms;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BlastFurnaceMenu;
import net.minecraft.world.inventory.BrewingStandMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.SmokerMenu;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The workshops aboard a ship keep working while she sails (the sailing plan: "furnaces that keep smelting"): furnaces,
 * smokers, blast furnaces and brewing stands in her hold tick as they would ashore, and open from the deck.
 *
 * <p>Her block entities aren't in the world while she sails, so each one is ticked with its world calls kept off the
 * world: the mixins in {@code mixin.sailing} ({@code LevelShipWorksMixin}, {@code ContainersShipWorksMixin}) see the
 * piece being worked ({@link #current}) and take its "set my block" (a furnace lighting up) as a change to her piece, drop
 * what it spits out (a bucket off a fuel stack) at the piece where it is now, and play its sounds there.
 */
public final class ShipWorks {
	/** The piece being worked this moment (server thread only; null the rest of the time). */
	private static @Nullable Working current;
	/** A workshop that threw while being worked is only logged the once. */
	private static boolean warned;
	/** The workshops given a level to work in aboard (so their "I changed" stays off the world). */
	private static final java.util.Set<BlockEntity> ABOARD = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

	private static final class Working {
		final Ship ship;
		final int piece;
		final BlockPos pos;
		@Nullable BlockState set;

		Working(Ship ship, int piece, BlockPos pos) {
			this.ship = ship;
			this.piece = piece;
			this.pos = pos;
		}
	}

	private ShipWorks() {
	}

	/** Whether this block entity keeps working aboard a ship under way. */
	static boolean works(BlockEntity be) {
		return be instanceof AbstractFurnaceBlockEntity || be instanceof BrewingStandBlockEntity;
	}

	/**
	 * One tick of piece {@code piece}'s workshop {@code be} (in {@code state}). The state it set itself to (a furnace lit or
	 * gone out, a stand's bottles), or null if it left its block as it was.
	 */
	@SuppressWarnings("unchecked")
	static @Nullable BlockState tick(ServerLevel level, Ship ship, int piece, BlockState state, BlockEntity be) {
		BlockEntityTicker<BlockEntity> ticker = state.getTicker(level, (net.minecraft.world.level.block.entity.BlockEntityType<BlockEntity>)be.getType());
		if (ticker == null) {
			return null;
		}
		if (be.getLevel() != level) {
			be.setLevel(level);
		}
		ABOARD.add(be);
		Working w = new Working(ship, piece, be.getBlockPos());
		current = w;
		try {
			ticker.tick(level, be.getBlockPos(), state, be);
		} catch (RuntimeException e) {
			if (!warned) {
				warned = true;
				JaymosKingdoms.LOGGER.warn("A ship's {} couldn't work under way", state.getBlock(), e);
			}
			return null;
		} finally {
			current = null;
		}
		if (w.set != null && w.set.getBlock() == state.getBlock()) {
			be.setBlockState(w.set);
			return w.set;
		}
		return null;
	}

	/** (mixins) Whether {@code pos} is the workshop being worked aboard a ship just now. */
	public static boolean ours(BlockPos pos) {
		Working w = current;
		return w != null && w.pos.equals(pos);
	}

	/** (mixins) The workshop being worked set its block to {@code state}: it becomes her piece's state. */
	public static void set(BlockState state) {
		Working w = current;
		if (w != null) {
			w.set = state;
		}
	}

	/** (mixins) Whether {@code be} is a workshop aboard a ship (not a block in the world). */
	public static boolean aboard(BlockEntity be) {
		return !ABOARD.isEmpty() && ABOARD.contains(be);
	}

	/** (mixins) Whether a workshop aboard is being worked just now (anywhere). */
	public static boolean working() {
		return current != null;
	}

	/** (mixins) Where the piece being worked is in the world now (the middle of its block), or null if none is. */
	public static @Nullable Vec3 where() {
		Working w = current;
		if (w == null) {
			return null;
		}
		Hull h = w.ship.hull();
		if (h == null || w.piece >= h.pieces.size()) {
			return w.ship.position();
		}
		Ship.Piece p = h.pieces.get(w.piece);
		return w.ship.toWorld(p.x(), p.y() + 0.5, p.z());
	}

	/** Runs {@code run} with no workshop being worked (so a redirected drop isn't redirected again). */
	public static void outside(Runnable run) {
		Working w = current;
		current = null;
		try {
			run.run();
		} finally {
			current = w;
		}
	}

	/**
	 * The menu a workshop aboard opens with ({@code held}: its hold's view of it, which closes when the ship sails off or
	 * anchors), or null if it isn't one of ours.
	 */
	static @Nullable AbstractContainerMenu menu(BlockEntity be, int id, Inventory inventory, net.minecraft.world.Container held) {
		if (be instanceof AbstractFurnaceBlockEntity furnace) {
			ContainerData data = ((com.jaymoskingdoms.mixin.sailing.FurnaceDataAccessor)furnace).jaymoskingdoms$data();
			// the furnace itself, so its result slot still pays out the experience; kept open only while in reach
			if (be instanceof SmokerBlockEntity) {
				return new SmokerMenu(id, inventory, furnace, data) {
					@Override
					public boolean stillValid(Player player) {
						return held.stillValid(player);
					}
				};
			}
			if (be instanceof BlastFurnaceBlockEntity) {
				return new BlastFurnaceMenu(id, inventory, furnace, data) {
					@Override
					public boolean stillValid(Player player) {
						return held.stillValid(player);
					}
				};
			}
			return new FurnaceMenu(id, inventory, furnace, data) {
				@Override
				public boolean stillValid(Player player) {
					return held.stillValid(player);
				}
			};
		}
		if (be instanceof BrewingStandBlockEntity stand) {
			ContainerData data = ((com.jaymoskingdoms.mixin.sailing.BrewingDataAccessor)stand).jaymoskingdoms$data();
			return new BrewingStandMenu(id, inventory, held, data);
		}
		return null;
	}
}
