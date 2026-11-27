package com.jaymospowers.umbrella;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** What an umbrella stand holds: the umbrella itself (its wear and colours kept). */
public class UmbrellaStandEntity extends BlockEntity {
	private ItemStack umbrella = ItemStack.EMPTY;

	public UmbrellaStandEntity(BlockPos pos, BlockState state) {
		super(Umbrellas.STAND_ENTITY, pos, state);
	}

	public ItemStack umbrella() {
		return this.umbrella;
	}

	public void set(ItemStack stack) {
		this.umbrella = stack;
		this.setChanged();
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		if (this.level != null) {
			Containers.dropItemStack(this.level, pos.getX(), pos.getY(), pos.getZ(), this.umbrella);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.umbrella = input.read("umbrella", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.store("umbrella", ItemStack.OPTIONAL_CODEC, this.umbrella);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}
}
