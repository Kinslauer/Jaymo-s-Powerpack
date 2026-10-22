package com.jaymospowers.tinkering.table;

import com.jaymospowers.tinkering.StationBlock;
import com.jaymospowers.tinkering.StationEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * (Tinkers' signature UX) What the Part Builder, Tool Station, Tool Forge and Crafting Station screens share: the
 * <b>side inventory</b>, the container next to the table (a Pattern or Part Chest first, else any container block
 * beside it), whose slots show in a panel left of the screen and take clicks and shift-clicks; and the <b>tabs</b>, one
 * for each Tinkers table touching this one, to hop between them without closing.
 * <p>
 * The slots are always there, {@link #SIDE_MAX} of them, only as many as the container has are active, so client and
 * server agree without sending the container's size when the menu opens (a data slot carries it).
 */
public final class TableExtras {
	/** Most slots shown (a double chest). */
	public static final int SIDE_MAX = 54;
	public static final int SIDE_COLUMNS = 6;
	/** Where the first side slot sits, relative to the screen's left and top. */
	public static final int SIDE_X = -114, SIDE_Y = 19;
	/** Menu button ids from here on are tabs: this plus the {@link StationBlock.Kind}'s place. */
	public static final int TAB_BUTTONS = 1000;
	/** What the side container holds, for the side panel's title and the slots' filter. */
	public static final int FILTER_NONE = 0, FILTER_PATTERNS = 1, FILTER_PARTS = 2;

	private final ContainerLevelAccess access;
	private final StationBlock.Kind self;
	private final @Nullable Container real;
	private final Container backing;
	private final DataSlot sideSize = DataSlot.standalone(), filter = DataSlot.standalone(), tabs = DataSlot.standalone();
	private final List<Slot> slots = new ArrayList<>();

	public TableExtras(ContainerLevelAccess access, Player player, StationBlock.Kind self) {
		this.access = access;
		this.self = self;
		if (player.level().isClientSide()) {
			this.real = null;
			this.backing = new SimpleContainer(SIDE_MAX);
		} else {
			this.real = access.evaluate(TableExtras::find, null);
			this.backing = new View(real);
			if (real != null) {
				sideSize.set(Math.min(SIDE_MAX, real.getContainerSize()));
				filter.set(real instanceof TinkerChestEntity chest ? chest.filterId() : FILTER_NONE);
				real.startOpen(player);
			}
			tabs.set(access.evaluate(this::tabMask, 1 << self.ordinal()));
		}
		for (int i = 0; i < SIDE_MAX; i++) {
			int index = i;
			slots.add(new Slot(backing, i, SIDE_X + i % SIDE_COLUMNS * 18, SIDE_Y + i / SIDE_COLUMNS * 18) {
				@Override
				public boolean isActive() {
					return index < sideSize.get();
				}

				@Override
				public boolean mayPlace(ItemStack stack) {
					return isActive() && accepts(filter.get(), stack) && backing.canPlaceItem(index, stack);
				}
			});
		}
	}

	/** The slots, for the menu to add after its own. */
	public List<Slot> slots() {
		return slots;
	}

	/** The data slots, for the menu to add. */
	public List<DataSlot> data() {
		return List.of(sideSize, filter, tabs);
	}

	/** The menu index of the first side slot (once the menu has added them). */
	public int first() {
		return slots.get(0).index;
	}

	/** How many side slots are active (0: no container beside the table). */
	public int size() {
		return sideSize.get();
	}

	/** One past the last active side slot's menu index. */
	public int end() {
		return first() + size();
	}

	public int filter() {
		return filter.get();
	}

	/** The side panel's title. */
	public Component title() {
		return Component.translatable(switch (filter.get()) {
			case FILTER_PATTERNS -> "container.jaymospowers.pattern_chest";
			case FILTER_PARTS -> "container.jaymospowers.part_chest";
			default -> "gui.jaymospowers.tables.side";
		});
	}

	/** The tables on offer as tabs (with this one), in tab order; empty when this is the only one. */
	public List<StationBlock.Kind> tabs() {
		List<StationBlock.Kind> kinds = new ArrayList<>();
		for (StationBlock.Kind kind : StationBlock.Kind.values()) {
			if ((tabs.get() & 1 << kind.ordinal()) != 0) {
				kinds.add(kind);
			}
		}
		return kinds.size() < 2 ? List.of() : kinds;
	}

	public StationBlock.Kind self() {
		return self;
	}

	/** Whether the side container is still there to be used. */
	public boolean stillValid(Player player) {
		return real == null || real.stillValid(player);
	}

	public void removed(Player player) {
		if (real != null) {
			real.stopOpen(player);
		}
	}

	/** A tab was clicked (menu button {@link #TAB_BUTTONS} on): open that table, if it is still beside this one. */
	public boolean clickTab(Player player, int button) {
		int index = button - TAB_BUTTONS;
		if (index < 0 || index >= StationBlock.Kind.values().length) {
			return false;
		}
		StationBlock.Kind kind = StationBlock.Kind.values()[index];
		if (kind == self || !(player instanceof ServerPlayer server)) {
			return false;
		}
		access.execute((level, pos) -> {
			for (Direction direction : Direction.Plane.HORIZONTAL) {
				BlockPos at = pos.relative(direction);
				BlockState state = level.getBlockState(at);
				if (state.getBlock() instanceof StationBlock block && block.kind() == kind) {
					if (level.getBlockEntity(at) instanceof StationEntity table && table.busyFor(player)) {
						player.sendOverlayMessage(Component.translatable("message.jaymospowers.tinkering.busy"));
					} else {
						server.openMenu(state.getMenuProvider(level, at));
					}
					return;
				}
			}
		});
		return true;
	}

	/** Whether a chest of this filter takes the stack. */
	public static boolean accepts(int filter, ItemStack stack) {
		return switch (filter) {
			case FILTER_PATTERNS -> TinkerChestEntity.isPattern(stack);
			case FILTER_PARTS -> TinkerChestEntity.isPart(stack);
			default -> true;
		};
	}

	/** The container beside the table: a Pattern or Part Chest first, else the first container block around it. */
	private static @Nullable Container find(Level level, BlockPos pos) {
		Container other = null;
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			BlockPos at = pos.relative(direction);
			BlockEntity entity = level.getBlockEntity(at);
			if (entity instanceof TinkerChestEntity chest) {
				return chest;
			}
			if (other == null && entity instanceof Container) {
				other = HopperBlockEntity.getContainerAt(level, at);
			}
		}
		return other;
	}

	/** Bit k is set for the table of kind k at or beside this one. */
	private int tabMask(Level level, BlockPos pos) {
		int mask = 1 << self.ordinal();
		for (Direction direction : Direction.Plane.HORIZONTAL) {
			if (level.getBlockState(pos.relative(direction)).getBlock() instanceof StationBlock block) {
				mask |= 1 << block.kind().ordinal();
			}
		}
		return mask;
	}

	/** The side container on the server, padded to {@link #SIDE_MAX} slots of which the extra ones are empty and refuse everything. */
	private static final class View implements Container {
		private final @Nullable Container real;
		private final int size;

		View(@Nullable Container real) {
			this.real = real;
			this.size = real == null ? 0 : Math.min(SIDE_MAX, real.getContainerSize());
		}

		@Override
		public int getContainerSize() {
			return SIDE_MAX;
		}

		@Override
		public boolean isEmpty() {
			return real == null || real.isEmpty();
		}

		@Override
		public ItemStack getItem(int slot) {
			return slot < size ? real.getItem(slot) : ItemStack.EMPTY;
		}

		@Override
		public ItemStack removeItem(int slot, int count) {
			return slot < size ? real.removeItem(slot, count) : ItemStack.EMPTY;
		}

		@Override
		public ItemStack removeItemNoUpdate(int slot) {
			return slot < size ? real.removeItemNoUpdate(slot) : ItemStack.EMPTY;
		}

		@Override
		public void setItem(int slot, ItemStack stack) {
			if (slot < size) {
				real.setItem(slot, stack);
			}
		}

		@Override
		public int getMaxStackSize() {
			return real == null ? Container.super.getMaxStackSize() : real.getMaxStackSize();
		}

		@Override
		public void setChanged() {
			if (real != null) {
				real.setChanged();
			}
		}

		@Override
		public boolean stillValid(Player player) {
			return real == null || real.stillValid(player);
		}

		@Override
		public boolean canPlaceItem(int slot, ItemStack stack) {
			return slot < size && real.canPlaceItem(slot, stack);
		}

		@Override
		public void clearContent() {
		}
	}
}
