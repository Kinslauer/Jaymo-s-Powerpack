package com.jaymoskingdoms;

import com.jaymoskingdoms.Soundscape.Layer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Kingdoms' held sounds, layered ({@link Soundscape}) instead of one sound on a fixed beat: oars and a hull under way,
 * a ship going down, caravan carts, trees falling and flying, dragons' fire and frost, wingbeats, a thrown shield's
 * whirl, the dead clawing up under a blood moon, a werewolf's change and the hunger curse feeding. Each is a body with
 * accents that come and go, spaced unevenly ({@link Soundscape#due}); loud ones echo in caves. Clocks stay clocks: a
 * trial's lift count, a stake's tugs and a rite's heartbeat keep their beat on purpose. Tuning is here.
 */
public final class WorldSounds {
	// oars: the dip, the oarlock, now and then water running off the blade
	static final Layer OAR = Layer.of(0.8F, 1.0F, 0.75F, 1.0F, SoundEvents.BOAT_PADDLE_WATER);
	static final Layer OARLOCK = Layer.of(0.15F, 0.3F, 0.5F, 0.7F, SoundEvents.WOOD_HIT).chance(0.5F);
	static final Layer OAR_DRIP = Layer.of(0.1F, 0.2F, 1.2F, 1.5F, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER).chance(0.3F);
	// a hull under way: timbers working, the sea against her sides, canvas and rigging when she's under sail
	static final Layer HULL = Layer.of(0.4F, 0.65F, 0.42F, 0.6F, SoundEvents.WOOD_STEP);
	static final Layer HULL_GROAN = Layer.of(0.2F, 0.35F, 0.35F, 0.45F, SoundEvents.LADDER_STEP).chance(0.3F);
	static final Layer SLAP = Layer.of(0.25F, 0.45F, 0.5F, 0.7F, SoundEvents.BOAT_PADDLE_WATER).chance(0.45F);
	static final Layer CANVAS = Layer.of(0.25F, 0.4F, 0.6F, 0.85F, SoundEvents.WOOL_STEP).chance(0.4F);
	static final Layer RIGGING = Layer.of(0.1F, 0.2F, 0.5F, 0.7F, SoundEvents.CHAIN_STEP).chance(0.2F);
	public static final int HULL_MIN = 50, HULL_MAX = 130;
	// going down: timbers giving, air rushing out, the sea pouring in
	static final Layer SINK = Layer.of(0.8F, 1.1F, 0.32F, 0.48F, SoundEvents.WOOD_STEP);
	static final Layer SINK_BREAK = Layer.of(0.4F, 0.7F, 0.4F, 0.55F, SoundEvents.WOOD_BREAK).chance(0.25F);
	static final Layer SINK_AIR = Layer.of(0.4F, 0.7F, 0.6F, 0.9F, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT).chance(0.5F);
	static final Layer SINK_POUR = Layer.of(0.3F, 0.5F, 0.6F, 0.8F, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_AMBIENT).chance(0.3F);
	public static final int SINK_MIN = 25, SINK_MAX = 55;
	// a caravan rolling: a cart's load shifting, a wheel, the harness
	static final Layer CART = Layer.of(0.55F, 0.85F, 0.8F, 1.0F, SoundEvents.BARREL_CLOSE);
	static final Layer WHEEL = Layer.of(0.25F, 0.4F, 0.5F, 0.7F, SoundEvents.WOOD_STEP).chance(0.5F);
	static final Layer HARNESS = Layer.of(0.15F, 0.25F, 0.9F, 1.2F, SoundEvents.CHAIN_STEP).chance(0.3F);
	public static final int CART_MIN = 25, CART_MAX = 60;
	// a tree going over: the creak (higher as it leans), leaves, a split
	static final Layer TREE_CREAK = Layer.of(0.7F, 0.9F, 0.9F, 1.1F, SoundEvents.WOOD_STEP);
	static final Layer TREE_LEAVES = Layer.of(0.4F, 0.7F, 0.6F, 0.9F, SoundEvents.AZALEA_LEAVES_STEP).chance(0.4F);
	static final Layer TREE_SPLIT = Layer.of(0.3F, 0.5F, 0.5F, 0.7F, SoundEvents.WOOD_HIT).chance(0.15F);
	// a tree in a tornado: leaves thrashing, a limb knocking
	static final Layer FLYING_LEAVES = Layer.of(1.0F, 1.3F, 0.5F, 0.72F, SoundEvents.AZALEA_LEAVES_STEP);
	static final Layer FLYING_LIMB = Layer.of(0.4F, 0.6F, 0.45F, 0.6F, SoundEvents.WOOD_STEP).chance(0.3F);
	// fire breath: the gout, a roar under it, the crackle of what it lands on
	static final Layer FIRE = Layer.of(1.0F, 1.3F, 0.55F, 0.8F, SoundEvents.FIRECHARGE_USE);
	static final Layer FIRE_ROAR = Layer.of(0.9F, 1.2F, 0.5F, 0.7F, SoundEvents.BLAZE_SHOOT).chance(0.5F);
	static final Layer FIRE_BED = Layer.of(0.6F, 0.9F, 0.6F, 0.8F, SoundEvents.FIRE_AMBIENT).chance(0.4F);
	// frost breath: the hiss, snow crunching, ice tinkling
	static final Layer FROST = Layer.of(0.85F, 1.1F, 1.4F, 1.8F, SoundEvents.FIRE_EXTINGUISH);
	static final Layer FROST_SNOW = Layer.of(0.7F, 1.0F, 0.8F, 1.1F, SoundEvents.SNOW_BREAK).chance(0.5F);
	static final Layer FROST_POWDER = Layer.of(0.4F, 0.6F, 0.8F, 1.0F, SoundEvents.POWDER_SNOW_STEP).chance(0.3F);
	static final Layer FROST_ICE = Layer.of(0.3F, 0.5F, 1.4F, 1.9F, SoundEvents.AMETHYST_BLOCK_CHIME).chance(0.15F);
	public static final int BREATH_MIN = 4, BREATH_MAX = 8;
	// the dead clawing up: earth giving way, a bone rattle
	static final Layer RISE = Layer.of(0.65F, 0.95F, 0.65F, 0.95F, SoundEvents.GRAVEL_BREAK, SoundEvents.ROOTED_DIRT_BREAK);
	static final Layer RISE_BONES = Layer.of(0.3F, 0.5F, 0.6F, 0.9F, SoundEvents.SKELETON_STEP).chance(0.25F);
	public static final int RISE_MIN = 8, RISE_MAX = 16;
	// a werewolf's change: bones cracking, a cry
	static final Layer CRACK = Layer.of(0.85F, 1.15F, 0.42F, 0.9F, SoundEvents.BONE_BLOCK_BREAK);
	static final Layer CRACK_SNAP = Layer.of(0.3F, 0.5F, 1.2F, 1.5F, SoundEvents.SKELETON_HURT).chance(0.2F);
	static final Layer CRY = Layer.of(0.85F, 1.1F, 0.5F, 0.8F, SoundEvents.VILLAGER_HURT);
	// the hunger curse feeding
	static final Layer DEVOUR = Layer.of(0.8F, 1.0F, 0.5F, 0.7F, SoundEvents.GENERIC_EAT.value());
	static final Layer DEVOUR_TEAR = Layer.of(0.3F, 0.5F, 0.5F, 0.7F, SoundEvents.HONEY_BLOCK_STEP).chance(0.4F);
	static final Layer DEVOUR_GULP = Layer.of(0.4F, 0.6F, 0.6F, 0.8F, SoundEvents.GENERIC_DRINK.value()).chance(0.2F);
	// a thrown shield's whirl
	static final Layer WHIRL = Layer.of(0.28F, 0.4F, 1.6F, 1.95F, SoundEvents.PLAYER_ATTACK_SWEEP);

	private WorldSounds() {
	}

	/** One stroke of the oars, quicker and higher the faster she goes. */
	public static void oars(ServerLevel level, Vec3 at, double speed) {
		float quick = 1.0F + (float)Math.min(0.2, speed * 0.3);
		Soundscape.play(level, at, SoundSource.NEUTRAL, OAR.pitched(quick), OARLOCK, OAR_DRIP);
	}

	/** A hull working under way (louder the faster), with canvas and rigging when her sails are set. */
	public static void hull(ServerLevel level, Entity ship, Vec3 at, double speed, boolean sails) {
		if (!Soundscape.due(ship, "hull", HULL_MIN, HULL_MAX)) {
			return;
		}
		float hard = 0.8F + (float)Math.min(0.6, speed);
		Layer slap = SLAP.louder(hard);
		if (sails) {
			Soundscape.play(level, at, SoundSource.BLOCKS, HULL.louder(hard), HULL_GROAN, slap, CANVAS, RIGGING);
		} else {
			Soundscape.play(level, at, SoundSource.BLOCKS, HULL.louder(hard), HULL_GROAN, slap);
		}
	}

	/** A ship going down. */
	public static void sinking(ServerLevel level, Entity ship) {
		if (Soundscape.due(ship, "sink", SINK_MIN, SINK_MAX)) {
			Soundscape.play(level, ship.position(), SoundSource.BLOCKS, SINK, SINK_BREAK, SINK_AIR, SINK_POUR);
		}
	}

	/** A caravan's cart on the road ({@code owner} stands for the caravan). */
	public static void cart(ServerLevel level, long owner, Entity cart) {
		if (Soundscape.due(level, owner, "cart", CART_MIN, CART_MAX)) {
			Soundscape.play(level, cart.position(), SoundSource.NEUTRAL, CART, WHEEL, HARNESS);
		}
	}

	/** A tree going over, its creak rising as it leans ({@code lean} in degrees). */
	public static void treeFalling(ServerLevel level, Entity tree, float lean) {
		if (Soundscape.due(tree, "fall", 4, 9)) {
			float rise = 0.5F + lean / 180.0F;
			Soundscape.play(level, tree.position(), SoundSource.BLOCKS, TREE_CREAK.pitched(rise), TREE_LEAVES, TREE_SPLIT);
		}
	}

	/** A tree flying in a tornado. */
	public static void treeFlying(ServerLevel level, Entity tree) {
		if (Soundscape.due(tree, "fly", 6, 11)) {
			Soundscape.play(level, tree.position(), SoundSource.BLOCKS, FLYING_LEAVES, FLYING_LIMB);
		}
	}

	/** A dragon breathing fire. */
	public static void fireBreath(ServerLevel level, Entity dragon, Vec3 mouth) {
		if (Soundscape.due(dragon, "fire", BREATH_MIN, BREATH_MAX)) {
			Soundscape.play(level, mouth, SoundSource.HOSTILE, FIRE, FIRE_ROAR, FIRE_BED);
		}
	}

	/** A dragon breathing frost. */
	public static void frostBreath(ServerLevel level, Entity dragon, Vec3 mouth) {
		if (Soundscape.due(dragon, "frost", BREATH_MIN, BREATH_MAX)) {
			Soundscape.play(level, mouth, SoundSource.HOSTILE, FROST, FROST_SNOW, FROST_POWDER, FROST_ICE);
		}
	}

	/** One of the dead clawing up out of the ground ({@code owner} stands for it). */
	public static void rising(ServerLevel level, long owner, Vec3 at) {
		if (Soundscape.due(level, owner, "rise", RISE_MIN, RISE_MAX)) {
			Soundscape.play(level, at, SoundSource.HOSTILE, RISE, RISE_BONES);
		}
	}

	/** A werewolf's bones cracking through the change, lower the further along ({@code low} 0..1). */
	public static void cracking(ServerLevel level, Entity body, int min, int max, float low) {
		if (Soundscape.due(body, "crack", min, max)) {
			Soundscape.play(level, body.position(), SoundSource.HOSTILE, CRACK.pitched(1.0F - 0.2F * low), CRACK_SNAP);
		}
	}

	/** A cry torn out through the change. */
	public static void crying(ServerLevel level, Entity body, int min, int max) {
		if (Soundscape.due(body, "cry", min, max)) {
			Soundscape.play(level, body.position(), SoundSource.HOSTILE, CRY);
		}
	}

	/** The hunger curse feeding. */
	public static void devour(ServerLevel level, Entity eater) {
		if (Soundscape.due(eater, "devour", 7, 13)) {
			Soundscape.play(level, eater.position(), SoundSource.PLAYERS, DEVOUR, DEVOUR_TEAR, DEVOUR_GULP);
		}
	}

	/** A thrown shield whirling through the air ({@code owner} stands for the throw). */
	public static void whirl(ServerLevel level, long owner, Vec3 at) {
		if (Soundscape.due(level, owner, "whirl", 4, 6)) {
			Soundscape.play(level, at, SoundSource.PLAYERS, WHIRL);
		}
	}

	/** A wingbeat: {@code body} at {@code volume} and {@code pitch}, every {@code min}..{@code max} ticks, a little varied. */
	public static void wingbeat(ServerLevel level, Entity flier, String key, int min, int max, SoundSource source,
		net.minecraft.sounds.SoundEvent body, float volume, float pitch) {
		if (Soundscape.due(flier, key, min, max)) {
			Soundscape.play(level, flier.position(), source, Layer.of(volume * 0.85F, volume * 1.1F, pitch * 0.92F, pitch * 1.1F, body));
		}
	}
}
