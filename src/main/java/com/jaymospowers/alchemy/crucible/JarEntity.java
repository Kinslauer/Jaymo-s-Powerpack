package com.jaymospowers.alchemy.crucible;

import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * What an essence jar holds: up to {@link Crucibles#JAR_SIZE} of one essence. When the jar is broken the loot table
 * copies the {@link Alchemy#ESSENCES} component onto the dropped item (see {@link #collectImplicitComponents}), and a
 * jar placed from that item reads it back, so the contents travel with the jar.
 */
public class JarEntity extends BlockEntity {
	private @Nullable Essence essence;
	private int amount;

	public JarEntity(BlockPos pos, BlockState state) {
		super(Crucibles.JAR_ENTITY, pos, state);
	}

	/** What it holds (null when empty). */
	public @Nullable Essence essence() {
		return amount > 0 ? essence : null;
	}

	public int amount() {
		return essence == null ? 0 : amount;
	}

	/** How full, 0..1. */
	public float fill() {
		return Math.min(1.0F, amount() / (float) Crucibles.JAR_SIZE);
	}

	/** Whether {@code e} could be put in: the jar is empty or holds it, and has room. */
	public boolean accepts(Essence e) {
		return amount() < Crucibles.JAR_SIZE && (essence() == null || essence() == e);
	}

	/** Puts in up to {@code n} of {@code e}; returns how much went in. */
	public int add(Essence e, int n) {
		if (!accepts(e) || n <= 0) {
			return 0;
		}
		int in = Math.min(n, Crucibles.JAR_SIZE - amount());
		essence = e;
		amount = amount() + in;
		changed();
		return in;
	}

	/** Takes up to {@code n} out; returns how much came. */
	public int take(int n) {
		int out = Math.min(Math.max(0, n), amount());
		if (out > 0) {
			amount -= out;
			if (amount <= 0) {
				essence = null;
				amount = 0;
			}
			changed();
		}
		return out;
	}

	private void changed() {
		setChanged();
		if (level != null) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void collectImplicitComponents(DataComponentMap.Builder builder) {
		super.collectImplicitComponents(builder);
		if (essence() != null) {
			builder.set(Alchemy.ESSENCES, EssenceList.of(essence(), amount()));
		}
	}

	@Override
	protected void applyImplicitComponents(DataComponentGetter components) {
		super.applyImplicitComponents(components);
		EssenceList held = components.get(Alchemy.ESSENCES);
		Essence main = held == null ? null : held.main();
		if (main != null) {
			essence = main;
			amount = Math.min(Crucibles.JAR_SIZE, held.get(main));
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		essence = input.read("essence", Essence.CODEC).orElse(null);
		amount = essence == null ? 0 : input.getIntOr("amount", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (essence() != null) {
			output.store("essence", Essence.CODEC, essence());
			output.putInt("amount", amount());
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
