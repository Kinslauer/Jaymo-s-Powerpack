package com.jaymoskitchen.sound;

import com.jaymoskitchen.delight.DelightHome;
import com.jaymoskitchen.sound.Soundscape.Layer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Kitchen's held sounds, layered ({@link Soundscape}) instead of one sound on a fixed beat: what's on the hob, the oven
 * finishing a dish, casks and stills, a body roasting, and rats and scavengers eating. Each is a body (the bubble, the
 * crackle) with accents that come and go (a rolling boil, spitting fat, a hiss as a pot boils over), spaced unevenly.
 * The cookware, stove, hearth and oven animation ticks play the same layers on the client. Tuning is here.
 */
public final class KitchenSounds {
	/** How often a hob, a finishing dish, a cask, a still and a roast sound (ticks, picked anew each time). */
	public static final int HOB_MIN = 35, HOB_MAX = 85;
	public static final int FINISH_MIN = 12, FINISH_MAX = 30;
	public static final int CASK_MIN = 60, CASK_MAX = 140;
	public static final int STILL_MIN = 25, STILL_MAX = 60;
	public static final int ROAST_MIN = 25, ROAST_MAX = 55;

	// a pot: the bubble, a rolling boil under it, now and then a boil-over hiss or the lid knocking
	static final Layer POT = Layer.of(0.35F, 0.55F, 0.8F, 1.1F, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP);
	static final Layer POT_ROLL = Layer.of(0.1F, 0.2F, 1.1F, 1.4F, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT).chance(0.35F);
	static final Layer POT_HISS = Layer.of(0.06F, 0.12F, 1.6F, 1.9F, SoundEvents.FIRE_EXTINGUISH).chance(0.12F);
	static final Layer POT_LID = Layer.of(0.12F, 0.2F, 1.6F, 1.9F, SoundEvents.CHAIN_STEP).chance(0.1F);
	// a pan: the crackle, fat spitting, a low hiss
	static final Layer PAN = Layer.of(0.4F, 0.6F, 1.05F, 1.35F, SoundEvents.CAMPFIRE_CRACKLE);
	static final Layer PAN_SPIT = Layer.of(0.12F, 0.22F, 1.5F, 2.0F, SoundEvents.LAVA_POP).chance(0.3F);
	static final Layer PAN_HISS = Layer.of(0.05F, 0.1F, 1.8F, 2.0F, SoundEvents.FIRE_EXTINGUISH).chance(0.15F);
	// an oven baking: the fire, a tick of the metal as it heats
	static final Layer OVEN = Layer.of(0.4F, 0.6F, 0.85F, 1.1F, SoundEvents.FURNACE_FIRE_CRACKLE);
	static final Layer OVEN_TICK = Layer.of(0.08F, 0.15F, 1.4F, 1.8F, SoundEvents.CHAIN_STEP).chance(0.15F);
	// a fire under a stove or a hearth: the crackle, a pop of sap, the draw of the flue
	static final Layer FIRE = Layer.of(0.5F, 0.9F, 0.85F, 1.15F, SoundEvents.CAMPFIRE_CRACKLE);
	static final Layer FIRE_POP = Layer.of(0.15F, 0.3F, 0.7F, 1.0F, SoundEvents.LAVA_POP).chance(0.15F);
	static final Layer FIRE_DRAW = Layer.of(0.05F, 0.1F, 0.6F, 0.8F, SoundEvents.FIRE_AMBIENT).chance(0.2F);
	// a sealed cask working (slow, low), a still running (quicker, higher, with a drip)
	static final Layer CASK = Layer.of(0.2F, 0.35F, 0.65F, 0.9F, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP);
	static final Layer CASK_CREAK = Layer.of(0.1F, 0.18F, 0.5F, 0.7F, SoundEvents.WOOD_STEP).chance(0.12F);
	static final Layer STILL = Layer.of(0.2F, 0.35F, 1.4F, 1.8F, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP);
	static final Layer STILL_DRIP = Layer.of(0.15F, 0.3F, 1.0F, 1.4F, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER).chance(0.4F);
	static final Layer STILL_HISS = Layer.of(0.04F, 0.08F, 1.7F, 2.0F, SoundEvents.FIRE_EXTINGUISH).chance(0.1F);
	// a body roasting on a fire: crackle, fat dripping into the flames
	static final Layer ROAST = Layer.of(0.6F, 0.85F, 1.1F, 1.45F, SoundEvents.CAMPFIRE_CRACKLE);
	static final Layer ROAST_DRIP = Layer.of(0.15F, 0.3F, 1.2F, 1.6F, SoundEvents.FIRE_EXTINGUISH).chance(0.3F);
	static final Layer ROAST_SPIT = Layer.of(0.15F, 0.25F, 1.3F, 1.8F, SoundEvents.LAVA_POP).chance(0.25F);

	private KitchenSounds() {
	}

	/** A pan's sizzle (Farmer's Delight's skillet, once it's registered) on top of its crackle. */
	private static Layer[] pan() {
		SoundEvent sizzle = DelightHome.SKILLET_SIZZLE;
		return sizzle == null ? new Layer[] {PAN, PAN_SPIT, PAN_HISS}
			: new Layer[] {PAN, PAN_SPIT, PAN_HISS, Layer.of(0.15F, 0.3F, 0.85F, 1.1F, sizzle).chance(0.45F)};
	}

	private static Layer[] pot() {
		return new Layer[] {POT, POT_ROLL, POT_HISS, POT_LID};
	}

	private static Vec3 top(BlockPos pos) {
		return new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
	}

	/** Something cooking on a hob (an oven top): a pot, a pan, or the oven itself baking. */
	public static void hob(ServerLevel level, BlockPos pos, boolean pot, boolean pan) {
		if (Soundscape.due(level, pos.asLong(), "hob", HOB_MIN, HOB_MAX)) {
			Soundscape.play(level, top(pos), SoundSource.BLOCKS, pot ? pot() : pan ? pan() : new Layer[] {OVEN, OVEN_TICK});
		}
	}

	/** A dish finishing on an oven: a busier boil or sizzle than a hob left to itself. */
	public static void finishing(ServerLevel level, BlockPos pos, boolean pan) {
		if (Soundscape.due(level, pos.asLong(), "finish", FINISH_MIN, FINISH_MAX)) {
			Layer[] layers = pan ? pan() : pot();
			layers[0] = layers[0].louder(1.5F);
			Soundscape.play(level, top(pos), SoundSource.BLOCKS, layers);
		}
	}

	/** A sealed cask fermenting. */
	public static void cask(ServerLevel level, BlockPos pos) {
		if (Soundscape.due(level, pos.asLong(), "cask", CASK_MIN, CASK_MAX)) {
			Soundscape.play(level, top(pos), SoundSource.BLOCKS, CASK, CASK_CREAK);
		}
	}

	/** A still running. */
	public static void still(ServerLevel level, BlockPos pos) {
		if (Soundscape.due(level, pos.asLong(), "still", STILL_MIN, STILL_MAX)) {
			Soundscape.play(level, top(pos), SoundSource.BLOCKS, STILL, STILL_DRIP, STILL_HISS);
		}
	}

	/** A body roasting on the fire under it. */
	public static void roast(ServerLevel level, Entity body) {
		if (Soundscape.due(body, "roast", ROAST_MIN, ROAST_MAX)) {
			Soundscape.play(level, body.position(), SoundSource.NEUTRAL, ROAST, ROAST_DRIP, ROAST_SPIT);
		}
	}

	/** A mouthful: a nibble for something small, a tear and a chew for something bigger. */
	public static void chew(Entity eater, float volume, float pitch) {
		Soundscape.play(eater.level(), eater.position(), eater.getSoundSource(),
			Layer.of(volume * 0.8F, volume * 1.1F, pitch * 0.9F, pitch * 1.12F, SoundEvents.GENERIC_EAT.value()),
			Layer.of(volume * 0.3F, volume * 0.5F, pitch * 0.8F, pitch * 1.0F, SoundEvents.HONEY_BLOCK_STEP).chance(0.3F));
	}

	// ---- on the client, from block animation ticks ----

	/** Cookware on a heat: a pot or a pan. */
	public static void cookwareLocal(Level level, BlockPos pos, boolean pot) {
		Vec3 at = Vec3.atCenterOf(pos);
		Soundscape.playLocal(level, at.x, at.y, at.z, SoundSource.BLOCKS, pot ? pot() : pan());
	}

	/** A stove's or hearth's fire. */
	public static void fireLocal(Level level, double x, double y, double z) {
		Soundscape.playLocal(level, x, y, z, SoundSource.BLOCKS, FIRE, FIRE_POP, FIRE_DRAW);
	}

	/** A lit oven. */
	public static void ovenLocal(Level level, double x, double y, double z) {
		Soundscape.playLocal(level, x, y, z, SoundSource.BLOCKS, OVEN, OVEN_TICK);
	}
}
