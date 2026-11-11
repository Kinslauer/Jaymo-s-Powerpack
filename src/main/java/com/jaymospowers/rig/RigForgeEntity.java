package com.jaymospowers.rig;

import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What the Rig Forge holds: the salvage fed into it (in points), the piece and frame picked, and the two paints (plates
 * and trim). Synced to everyone near, because the Forge draws the suit over itself as a turning hologram
 * (client {@code RigForgeRenderer}): the plates of the picked piece light as the salvage goes in.
 */
public class RigForgeEntity extends BlockEntity {
	public static BlockEntityType<RigForgeEntity> TYPE;

	/** The most salvage the Forge holds, in points (the dearest build costs less than this, doubled for the heaviest frame). */
	public static final int MAX_FED = 30;
	/** A paint that isn't set. */
	public static final int NO_PAINT = -1;

	private int fed;
	private int selection;
	private RigFrame frame = RigFrame.STANDARD;
	private int plates = NO_PAINT, trim = NO_PAINT;

	public RigForgeEntity(BlockPos pos, BlockState state) {
		super(TYPE, pos, state);
	}

	/** The Forge's memory at {@code pos}: made on the spot for a Forge set down before it had one. */
	public static RigForgeEntity of(Level level, BlockPos pos, BlockState state) {
		if (level.getBlockEntity(pos) instanceof RigForgeEntity forge) {
			return forge;
		}
		RigForgeEntity forge = new RigForgeEntity(pos, state);
		level.setBlockEntity(forge);
		return forge;
	}

	public int fed() {
		return this.fed;
	}

	/** What the Forge is set to build: the four pieces, then a Rig arm and a Rig leg ({@link #SELECTIONS}). */
	public static final int SELECTIONS = Rig.Piece.values().length + 2;
	public static final int ARM = 1, LEG = 2;

	public int selection() {
		return Math.floorMod(this.selection, SELECTIONS);
	}

	/** The picked piece, or null while it is set to a limb. */
	public Rig.@Nullable Piece piece() {
		int s = selection();
		return s < Rig.Piece.values().length ? Rig.Piece.values()[s] : null;
	}

	/** {@link #ARM} or {@link #LEG} while the Forge is set to a Rig limb, else 0. */
	public int limb() {
		int s = selection() - Rig.Piece.values().length;
		return s < 0 ? 0 : s + 1;
	}

	public void setSelection(int selection) {
		this.selection = Math.floorMod(selection, SELECTIONS);
		changed();
	}

	public RigFrame frame() {
		return this.frame;
	}

	/** The plates' paint (rgb) or {@link #NO_PAINT}. */
	public int plates() {
		return this.plates;
	}

	/** The trim's paint (rgb) or {@link #NO_PAINT}. */
	public int trim() {
		return this.trim;
	}

	public void setFed(int fed) {
		this.fed = Math.max(0, Math.min(MAX_FED, fed));
		changed();
	}

	public void setFrame(RigFrame frame) {
		this.frame = frame;
		changed();
	}

	public void setPaint(int plates, int trim) {
		this.plates = plates;
		this.trim = trim;
		changed();
	}

	private void changed() {
		setChanged();
		if (this.level != null) {
			this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.fed = input.getIntOr("fed", 0);
		this.selection = input.getIntOr("piece", 0);
		this.frame = RigFrame.byId(input.getStringOr("frame", ""));
		this.plates = input.getIntOr("plates", NO_PAINT);
		this.trim = input.getIntOr("trim", NO_PAINT);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("fed", this.fed);
		output.putInt("piece", this.selection);
		output.putString("frame", this.frame.id());
		output.putInt("plates", this.plates);
		output.putInt("trim", this.trim);
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
