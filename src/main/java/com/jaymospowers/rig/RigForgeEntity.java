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
 * What the Rig Forge holds: the salvage fed into it (in points), the piece and frame picked, and the four paints (plates,
 * trim, the second colour and lights). Synced to everyone near, because the Forge draws the suit over itself as a turning hologram
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
	/** The paints, by {@link #PLATES}, {@link #TRIM}, {@link #UNDER}, {@link #LIGHTS}: rgb or {@link #NO_PAINT}. */
	private final int[] paints = {NO_PAINT, NO_PAINT, NO_PAINT, NO_PAINT};
	/** The paints the Forge puts on: the plates, the trim, the second colour (the under-suit and the second plates), the lights. */
	public static final int PLATES = 0, TRIM = 1, UNDER = 2, LIGHTS = 3, PAINTS = 4;
	private static final String[] PAINT_KEYS = {"plates", "trim", "under", "lights"};

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

	/** What the Forge is set to build: the four pieces, then a Rig arm and a Rig leg, then the Octo Core ({@link #SELECTIONS}). */
	public static final int SELECTIONS = Rig.Piece.values().length + 3;
	public static final int ARM = 1, LEG = 2;
	/** The selection that builds an Octo Core ({@link RigOcto}): a Tinkerer's or a web slinger's alone. */
	public static final int OCTO = Rig.Piece.values().length + 2;

	/** Whether the Forge is set to the Octo Core (it builds a Core, so {@link #piece} is the Core then). */
	public boolean octo() {
		return selection() == OCTO;
	}

	public int selection() {
		return Math.floorMod(this.selection, SELECTIONS);
	}

	/** The picked piece, or null while it is set to a limb. */
	public Rig.@Nullable Piece piece() {
		int s = selection();
		return s == OCTO ? Rig.Piece.CORE : s < Rig.Piece.values().length ? Rig.Piece.values()[s] : null;
	}

	/** {@link #ARM} or {@link #LEG} while the Forge is set to a Rig limb, else 0. */
	public int limb() {
		int s = selection() - Rig.Piece.values().length;
		return s < 0 || selection() == OCTO ? 0 : s + 1;
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
		return this.paints[PLATES];
	}

	/** The trim's paint (rgb) or {@link #NO_PAINT}. */
	public int trim() {
		return this.paints[TRIM];
	}

	/** One paint ({@link #PLATES}...{@link #LIGHTS}): rgb or {@link #NO_PAINT}. */
	public int paint(int which) {
		return this.paints[which];
	}

	public void setFed(int fed) {
		this.fed = Math.max(0, Math.min(MAX_FED, fed));
		changed();
	}

	public void setFrame(RigFrame frame) {
		this.frame = frame;
		changed();
	}

	public void setPaint(int which, int rgb) {
		this.paints[which] = rgb;
		changed();
	}

	/** Every paint washed off. */
	public void wash() {
		java.util.Arrays.fill(this.paints, NO_PAINT);
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
		for (int i = 0; i < PAINTS; i++) {
			this.paints[i] = input.getIntOr(PAINT_KEYS[i], NO_PAINT);
		}
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("fed", this.fed);
		output.putInt("piece", this.selection);
		output.putString("frame", this.frame.id());
		for (int i = 0; i < PAINTS; i++) {
			output.putInt(PAINT_KEYS[i], this.paints[i]);
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
