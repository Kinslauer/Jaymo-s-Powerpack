package com.jaymoskitchen.corpse;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
	/** A crushed head's ({@link Gibs#skullCrushed}): a lump of brain matter, a piece of the skull or jaw. */
	public static final int BRAIN = 7, SKULL = 8;
	/** A shard of ice off a frozen body ({@link CorpseEntity#shatter}), and a sweet from a piñata burst ({@link Gibs#pinata}); each carries its own colour in {@link #tint}. */
	public static final int ICE = 9, CANDY = 10;
	/** How hard a bone, ice or sweet bounces off the ground and how far it slides; flesh and gut slap down and stay put. */
	static final double HARD_BOUNCE = 0.55, HARD_KEEP = 0.8, SOFT_BOUNCE = 0.08, SOFT_KEEP = 0.4;
	/** A landing this fast (blocks a tick) makes a sound; the louder it is the harder it came down. */
	static final double CLACK_SPEED = 0.25;

	private static final EntityDataAccessor<Byte> PIECE = SynchedEntityData.defineId(Gib.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Integer> TINT = SynchedEntityData.defineId(Gib.class, EntityDataSerializers.INT);
	/** Squashed flat by something falling on the body ({@link Gibs#crushed}): drawn wide and thin. */
	private static final EntityDataAccessor<Boolean> FLAT = SynchedEntityData.defineId(Gib.class, EntityDataSerializers.BOOLEAN);
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

	void setFlat() {
		this.entityData.set(FLAT, true);
	}

	public boolean flat() {
		return this.entityData.get(FLAT);
	}

	/** Not gore: ice and sweets, drawn whatever the blood setting says. */
	public boolean clean() {
		int piece = piece();
		return piece == ICE || piece == CANDY;
	}

	/** Bone, ice and sweets are hard: they clack and bounce. */
	private boolean hard() {
		int piece = piece();
		return piece == BONE || piece == SPINE || piece == RIBS || piece == SKULL || clean();
	}

	@Override
	protected double bounce() {
		return hard() ? HARD_BOUNCE : SOFT_BOUNCE;
	}

	@Override
	protected double groundKeep() {
		return hard() ? HARD_KEEP : SOFT_KEEP;
	}

	@Override
	protected void landed(double speed) {
		if (speed < CLACK_SPEED || !(level() instanceof ServerLevel level)) {
			return;
		}
		int piece = piece();
		net.minecraft.sounds.SoundEvent sound = piece == ICE ? SoundEvents.GLASS_STEP : piece == CANDY ? SoundEvents.AMETHYST_BLOCK_STEP
			: hard() ? SoundEvents.BONE_BLOCK_STEP : SoundEvents.HONEY_BLOCK_STEP;
		level.playSound(null, getX(), getY(), getZ(), sound, SoundSource.NEUTRAL, (float) Math.min(0.6, speed), hard() ? 1.3F : 0.8F);
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
		builder.define(FLAT, false);
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		super.readAdditionalSaveData(input);
		this.entityData.set(PIECE, input.getByteOr("Piece", (byte) CHUNK));
		this.entityData.set(TINT, input.getIntOr("Tint", com.jaymoskitchen.KitchenBlood.RED));
		this.entityData.set(OWNER, input.getStringOr("Owner", ""));
		this.entityData.set(FLAT, input.getBooleanOr("Flat", false));
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		super.addAdditionalSaveData(output);
		output.putByte("Piece", this.entityData.get(PIECE));
		output.putInt("Tint", this.entityData.get(TINT));
		output.putString("Owner", this.entityData.get(OWNER));
		output.putBoolean("Flat", this.entityData.get(FLAT));
	}
}
