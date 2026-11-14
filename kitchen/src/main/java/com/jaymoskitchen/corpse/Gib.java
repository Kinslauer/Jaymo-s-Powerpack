package com.jaymoskitchen.corpse;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * (0.5.21) A chunk of a body blown apart ({@link Gibs}): a piece of the creature's own model in its own skin, which the
 * client picks from the look by the chunk's id. It flies, tumbles, lands and sinks away like a crushed spider's loose
 * leg, which it is built on; nothing to pick up or grab.
 * <p>
 * Or a piece of what was inside it ({@link #piece}): an organ, a rope of gut, a slab of ribs, a length of spine, a
 * shard of bone, a scrap of what it wore. The soft pieces are drawn in the body's own blood colour ({@link #tint}).
 * A scrap of cloth keeps the body's look (and a player's id, {@link #owner}), so the client cuts it from that body's
 * own clothes or skin.
 */
public class Gib extends SpiderLeg {
	/** What it is: a chunk of the body's own model, or one of its insides. */
	public static final int CHUNK = 0, ORGAN = 1, GUTS = 2, RIBS = 3, SPINE = 4, BONE = 5, TATTERS = 6;

	private static final EntityDataAccessor<Byte> PIECE = SynchedEntityData.defineId(Gib.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> TINT = SynchedEntityData.defineId(Gib.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<String> OWNER = SynchedEntityData.defineId(Gib.class, EntityDataSerializers.STRING);

	public Gib(EntityType<? extends Gib> type, Level level) {
		super(type, level);
	}

	/** Makes it one of a body's insides, {@code tint} being that body's blood (RGB). */
	void setPiece(int piece, int tint) {
		this.entityData.set(PIECE, (byte) piece);
		this.entityData.set(TINT, tint);
	}

	/** The dead player it came off (their id as text), for a scrap of their clothes. */
	void setOwner(String owner) {
		this.entityData.set(OWNER, owner);
	}

	/** The dead player it came off, if it was one. */
	public java.util.Optional<java.util.UUID> owner() {
		try {
			return java.util.Optional.of(java.util.UUID.fromString(this.entityData.get(OWNER)));
		} catch (IllegalArgumentException e) {
			return java.util.Optional.empty();
		}
	}

	public int piece() {
		return this.entityData.get(PIECE);
	}

	/** Its body's blood colour (RGB), for the soft pieces. */
	public int tint() {
		return this.entityData.get(TINT);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(PIECE, (byte) CHUNK);
		builder.define(TINT, com.jaymoskitchen.KitchenBlood.RED);
		builder.define(OWNER, "");
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(PIECE, input.getByteOr("Piece", (byte) CHUNK));
		this.entityData.set(TINT, input.getIntOr("Tint", com.jaymoskitchen.KitchenBlood.RED));
		this.entityData.set(OWNER, input.getStringOr("Owner", ""));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putByte("Piece", this.entityData.get(PIECE));
		output.putInt("Tint", this.entityData.get(TINT));
		output.putString("Owner", this.entityData.get(OWNER));
	}
}
