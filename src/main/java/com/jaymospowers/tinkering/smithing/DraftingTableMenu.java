package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.JaymosPowers;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * (Smithing) The Drafting Table's screen: a blueprint or a forged piece on the left, paper under it, and what comes of
 * them on the right. A blueprint is copied one level lower ({@link Blueprints#copyOf}) and stays on the table; a forged
 * piece is taken apart for its pattern ({@link Patterns#pattern}) and is gone. Either takes a sheet of paper (none in
 * creative). The pattern shows before it is taken, so taking it is the go-ahead the old second click was. Drawn by the
 * client's DraftingTableScreen.
 */
public class DraftingTableMenu extends AbstractContainerMenu {
	public static MenuType<DraftingTableMenu> TYPE;
	public static final int SUBJECT = 0, PAPER = 1, RESULT = 2, INPUTS = 2;
	/** Where the slots sit (x, y) in the screen. */
	public static final int[][] AT = {{44, 24}, {44, 52}, {124, 38}};
	public static final int INVENTORY_Y = 84;

	private final ContainerLevelAccess access;
	private final Player player;
	private final Container inputs = new SimpleContainer(INPUTS) {
		@Override
		public void setChanged() {
			super.setChanged();
			DraftingTableMenu.this.slotsChanged(this);
		}
	};
	private final ResultContainer result = new ResultContainer();

	static void init() {
		TYPE = Registry.register(BuiltInRegistries.MENU, JaymosPowers.id("drafting_table"), new MenuType<>(DraftingTableMenu::new, FeatureFlags.VANILLA_SET));
	}

	/** Client side. */
	public DraftingTableMenu(int id, Inventory inventory) {
		this(id, inventory, ContainerLevelAccess.NULL);
	}

	public DraftingTableMenu(int id, Inventory inventory, ContainerLevelAccess access) {
		super(TYPE, id);
		this.access = access;
		this.player = inventory.player;
		this.addSlot(new Slot(inputs, SUBJECT, AT[SUBJECT][0], AT[SUBJECT][1]) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return blueprint(stack) || Patterns.shapeOf(stack) != null;
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		this.addSlot(new Slot(inputs, PAPER, AT[PAPER][0], AT[PAPER][1]) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.is(Items.PAPER);
			}
		});
		this.addSlot(new Slot(result, 0, AT[RESULT][0], AT[RESULT][1]) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return false;
			}

			@Override
			public void onTake(Player taker, ItemStack carried) {
				spend(taker);
				super.onTake(taker, carried);
			}
		});
		this.addStandardInventorySlots(inventory, 8, INVENTORY_Y);
	}

	private static boolean blueprint(ItemStack stack) {
		return stack.is(Blueprints.BLUEPRINT_ITEM) && stack.has(Blueprints.BLUEPRINT);
	}

	@Override
	public void slotsChanged(Container container) {
		super.slotsChanged(container);
		if (container == inputs) {
			update();
		}
	}

	/** The copy or the pattern the table holds now, or nothing (no paper, or a rule says this smith can't read the piece). */
	private void update() {
		ItemStack subject = inputs.getItem(SUBJECT);
		ItemStack out = ItemStack.EMPTY;
		if (!subject.isEmpty() && (player.isCreative() || inputs.getItem(PAPER).is(Items.PAPER))) {
			if (blueprint(subject)) {
				out = Blueprints.copyOf(subject);
			} else {
				String shape = Patterns.shapeOf(subject);
				Component why = shape != null && player instanceof ServerPlayer smith ? Patterns.refused(smith) : null;
				if (why != null) {
					player.sendOverlayMessage(why);
				} else if (shape != null) {
					out = Patterns.pattern(subject, shape);
				}
			}
		}
		result.setItem(0, out);
		broadcastChanges();
	}

	/** The result is taken: a sheet of paper is used, and a piece taken apart is gone. */
	private void spend(Player taker) {
		ItemStack subject = inputs.getItem(SUBJECT);
		boolean apart = !blueprint(subject);
		if (!taker.isCreative()) {
			inputs.removeItem(PAPER, 1);
		}
		if (apart) {
			access.execute((level, pos) -> {
				if (level instanceof ServerLevel server) {
					server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, subject.getItem()), pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
						12, 0.2, 0.05, 0.2, 0.06);
				}
				level.playSound(null, pos, SoundEvents.ITEM_BREAK.value(), SoundSource.BLOCKS, 0.8F, 0.9F);
			});
			inputs.removeItem(SUBJECT, 1);
		}
		access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 0.9F));
	}

	@Override
	public boolean stillValid(Player who) {
		return stillValid(access, who, Blueprints.DRAFTING_TABLE);
	}

	@Override
	public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
		return target.container != result && super.canTakeItemForPickAll(carried, target);
	}

	@Override
	public void removed(Player who) {
		super.removed(who);
		this.clearContainer(who, inputs);
	}

	@Override
	public ItemStack quickMoveStack(Player who, int index) {
		Slot slot = this.slots.get(index);
		if (!slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack stack = slot.getItem();
		ItemStack moved = stack.copy();
		int end = this.slots.size();
		if (index == RESULT) {
			// one at a time: a copied blueprint stays on the table, so the result would refill and spend every sheet
			if (this.moveItemStackTo(stack, RESULT + 1, end, true)) {
				slot.onQuickCraft(stack, moved);
				slot.onTake(who, moved);
			}
			return ItemStack.EMPTY;
		} else if (index < INPUTS) {
			if (!this.moveItemStackTo(stack, RESULT + 1, end, false)) {
				return ItemStack.EMPTY;
			}
		} else {
			boolean placed = false;
			for (int i = 0; i < INPUTS && !placed; i++) {
				placed = this.slots.get(i).mayPlace(stack) && this.moveItemStackTo(stack, i, i + 1, false);
			}
			if (!placed) {
				return ItemStack.EMPTY;
			}
		}
		if (stack.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		if (stack.getCount() == moved.getCount()) {
			return ItemStack.EMPTY;
		}
		if (index == RESULT) {
			slot.onTake(who, stack);
		}
		return moved;
	}
}
