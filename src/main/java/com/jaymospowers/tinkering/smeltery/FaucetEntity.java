package com.jaymospowers.tinkering.smeltery;

import com.jaymospowers.tinkering.Tinkering;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * A faucet on a smeltery drain: opened (right-click or a redstone pulse), it pours the smeltery's bottom liquid into
 * the casting table or basin under it until that's full or the liquid runs out.
 */
public class FaucetEntity extends BlockEntity {
	/** mB a tick it pours. */
	static final int POUR_RATE = 6;

	/** What it's pouring now (null when shut), for the stream drawn under it. */
	private @Nullable Molten pouring;

	public FaucetEntity(BlockPos pos, BlockState state) {
		super(Tinkering.FAUCET_ENTITY, pos, state);
	}

	public @Nullable Molten pouring() {
		return pouring;
	}

	/** Opens it, if there's something to pour and somewhere to pour it. */
	public void open() {
		if (pouring == null) {
			Molten next = next();
			if (next != null) {
				set(next);
			}
		}
	}

	private @Nullable SmelteryEntity source() {
		if (level == null) {
			return null;
		}
		BlockPos behind = worldPosition.relative(getBlockState().getValue(HorizontalDirectionalBlock.FACING).getOpposite());
		return level.getBlockEntity(behind) instanceof DrainEntity drain ? drain.smeltery() : null;
	}

	private @Nullable CastingEntity target() {
		return level != null && level.getBlockEntity(worldPosition.below()) instanceof CastingEntity casting ? casting : null;
	}

	/** The liquid it would pour now: the smeltery's bottom one, if the cast below takes it. */
	private @Nullable Molten next() {
		SmelteryEntity smeltery = source();
		CastingEntity casting = target();
		Tank.Layer bottom = smeltery == null ? null : smeltery.tank().bottom();
		return bottom != null && casting != null && casting.wants(bottom.molten) > 0 ? bottom.molten : null;
	}

	public static void serverTick(Level level, BlockPos pos, BlockState state, FaucetEntity faucet) {
		if (faucet.pouring == null) {
			return;
		}
		SmelteryEntity smeltery = faucet.source();
		CastingEntity casting = faucet.target();
		Tank.Layer bottom = smeltery == null ? null : smeltery.tank().bottom();
		if (casting == null || bottom == null || bottom.molten != faucet.pouring) {
			faucet.set(null);
			return;
		}
		int want = Math.min(POUR_RATE, casting.wants(faucet.pouring));
		int got = want > 0 ? smeltery.drain(faucet.pouring, want) : 0;
		if (got <= 0) {
			faucet.set(null);
			return;
		}
		casting.fill(faucet.pouring, got);
	}

	private void set(@Nullable Molten molten) {
		pouring = molten;
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		pouring = Molten.byId(input.getStringOr("pouring", ""));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (pouring != null) {
			output.putString("pouring", pouring.id);
		}
	}

	@Override
	public ClientboundBlockEntityDataPacket getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
		saveAdditional(out);
		return out.buildResult();
	}
}
