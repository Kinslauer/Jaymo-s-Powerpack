package com.jaymospowers.jewellery;

import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/** (0.19.87) The Jeweller's Bench: a cooled frame on the left, a cut gem in the middle, the set piece on the right. */
public class JewellersBenchMenu extends ItemCombinerMenu {
	public static final int FRAME = 0, GEM = 1;

	/** Client side. */
	public JewellersBenchMenu(int id, Inventory inventory) {
		this(id, inventory, ContainerLevelAccess.NULL);
	}

	public JewellersBenchMenu(int id, Inventory inventory, ContainerLevelAccess access) {
		super(Jewellery.BENCH_MENU, id, inventory, access, ItemCombinerMenuSlotDefinition.create()
			.withSlot(0, 27, 47, s -> s.getItem() instanceof JewelFrameItem)
			.withSlot(1, 76, 47, s -> s.is(Jewellery.CUT_GEM_ITEM))
			.withResultSlot(2, 134, 47)
			.build());
	}

	@Override
	public void createResult() {
		ItemStack frame = this.inputSlots.getItem(FRAME), gem = this.inputSlots.getItem(GEM);
		this.resultSlots.setItem(0, Jewellery.refusal(frame) != null ? ItemStack.EMPTY : Jewellery.piece(frame, gem));
	}

	@Override
	protected void onTake(Player player, ItemStack carried) {
		for (int i = FRAME; i <= GEM; i++) {
			ItemStack stack = this.inputSlots.getItem(i);
			stack.shrink(1);
			this.inputSlots.setItem(i, stack);
		}
		this.access.execute((level, pos) -> level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F));
		this.createResult();
	}

	@Override
	protected boolean isValidBlock(BlockState state) {
		return state.is(Jewellery.JEWELLERS_BENCH);
	}

	/** Why there is no result (a hot frame), or null. */
	public @Nullable Component refusal() {
		ItemStack frame = this.inputSlots.getItem(FRAME);
		return frame.isEmpty() ? null : Jewellery.refusal(frame);
	}
}
