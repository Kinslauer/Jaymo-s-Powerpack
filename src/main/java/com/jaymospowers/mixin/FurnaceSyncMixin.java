package com.jaymospowers.mixin;

import com.jaymospowers.body.FurnaceHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * What cooks in a furnace, smoker or blast furnace is shown in its mouth (FurnaceRenderer), so the block entity's own
 * tag goes to the clients that can see it (on chunk load and on a change), where vanilla keeps it to the open screen.
 * A change is the item inside, the fuel catching or going out, or the cooking moving on by an eighth.
 */
@Mixin(AbstractFurnaceBlockEntity.class)
abstract class FurnaceSyncMixin extends BaseContainerBlockEntity implements FurnaceHeat {
	/** How many steps the cooking is shown in. */
	@Unique
	private static final int HEAT_STEPS = 8;

	@Shadow
	private int cookingTimer;
	@Shadow
	private int cookingTotalTime;
	@Shadow
	private int litTimeRemaining;

	@Unique
	private int jaymospowers$lastShown = Integer.MIN_VALUE;

	protected FurnaceSyncMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
	}

	@Override
	public Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return this.saveCustomOnly(registries);
	}

	@Inject(method = "serverTick", at = @At("TAIL"))
	private static void jaymospowers$sync(ServerLevel level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
		((FurnaceSyncMixin)(Object)furnace).jaymospowers$syncNow(level, pos, state);
	}

	@Unique
	private void jaymospowers$syncNow(ServerLevel level, BlockPos pos, BlockState state) {
		ItemStack input = this.getItem(0);
		int shown = input.isEmpty() ? 0 : 1 + input.getItem().hashCode() * 31 + (this.litTimeRemaining > 0 ? 7 : 0)
			+ (this.cookingTotalTime > 0 ? this.cookingTimer * HEAT_STEPS / this.cookingTotalTime * 131 : 0);
		if (shown != this.jaymospowers$lastShown) {
			this.jaymospowers$lastShown = shown;
			level.sendBlockUpdated(pos, state, state, 2);
		}
	}

	@Override
	public float jaymospowers$cooked() {
		return this.cookingTotalTime <= 0 ? 0.0F : Math.min(1.0F, (float)this.cookingTimer / this.cookingTotalTime);
	}

	@Override
	public boolean jaymospowers$burning() {
		return this.litTimeRemaining > 0;
	}
}
