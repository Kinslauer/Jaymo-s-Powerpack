package com.jaymoskingdoms.equestrian;

import java.util.Locale;

/**
 * (0.5.34) The breeds. Each sets the range its hidden stats map onto (a stat of 0 is the bottom of its range, 1 the
 * top), its size, its natural courage, how fast it tires, its top gait, and its coats (the last one rare, see
 * {@link Equestrian#RARE_COAT_CHANCE}). Saved by id: rename nothing.
 */
public enum Breed {
	/** Kingdom stables, war parties: carries armoured riders, holds a charge; slow. */
	DESTRIER(0.22, 0.27, 0.55, 0.75, 30, 38, 1.0, 1.0, 0.85, 1.12F, 1.08F, 1.0F, 1.0F, Gait.GALLOP,
		"black", "dark_bay", "iron_grey", "blaze_black", "liver_chestnut", "blood_roan"),
	/** Plains, wealthy kingdoms: fastest gallop, best jumper; tires fast, light armour only. */
	COURSER(0.30, 0.36, 0.80, 1.0, 20, 26, 0.9, 1.4, 0.45, 1.0F, 0.92F, 1.04F, 1.04F, Gait.GALLOP,
		"bay", "chestnut_blaze", "fleabitten_grey", "golden_chestnut", "black_socks", "palomino"),
	/** Villages, caravans: a smooth amble (trot and canter cost nothing), calm; middling speed. */
	PALFREY(0.24, 0.29, 0.55, 0.75, 22, 28, 1.1, 1.0, 0.6, 0.95F, 1.0F, 1.0F, 1.0F, Gait.GALLOP,
		"dun", "bay_star", "chestnut_socks", "dapple_grey", "cream", "leopard"),
	/** Farms, carriage and wagon yards: pulls anything; can't gallop. */
	DRAFT(0.22, 0.26, 0.45, 0.60, 30, 36, 1.2, 0.8, 0.7, 1.18F, 1.15F, 0.97F, 1.0F, Gait.CANTER,
		"black_feathered", "bay_blaze", "strawberry_roan", "flaxen_chestnut", "grey", "dapple_silver"),
	/** Savanna and barbarian camps: never spooks, stamina for days; small, weak jump. */
	STEPPE_PONY(0.24, 0.29, 0.40, 0.55, 20, 26, 1.3, 0.5, 1.0, 0.82F, 1.05F, 0.95F, 0.95F, Gait.GALLOP,
		"dun_stripe", "grulla", "bay_dun", "piebald", "buckskin", "silver_dun"),
	/** Desert kingdoms: doesn't tire in heat, fast on sand; hates cold and snow. */
	DESERT_HORSE(0.27, 0.33, 0.60, 0.85, 20, 26, 1.0, 1.0, 0.5, 0.96F, 0.92F, 1.02F, 1.0F, Gait.GALLOP,
		"white_grey", "golden_bay", "fleabitten", "black_star", "chestnut", "gold_shimmer"),
	/**
	 * Desert kingdoms' war elephant ({@link WarElephant}): huge, brave, tramples what it charges; slow, barely jumps.
	 * Its hides are Mo' Creatures' Indian, African (the big ears) and royal (painted) elephants.
	 */
	WAR_ELEPHANT(0.18, 0.22, 0.42, 0.46, 90, 110, 1.4, 0.9, 0.9, 1.1F, 1.0F, 1.0F, 1.0F, Gait.GALLOP,
		"indian", "african", "royal"),
	/** Snowy kingdoms' woolly mammoth (a {@link WarElephant} too): bigger and tougher still, tireless in the cold, wilts in the heat. */
	WOOLLY_MAMMOTH(0.17, 0.21, 0.42, 0.46, 110, 130, 1.5, 0.9, 0.95, 1.25F, 1.0F, 1.0F, 1.0F, Gait.GALLOP,
		"woolly", "russet", "frost"),
	/**
	 * (0.5.56) The nightmare (Mo' Creatures' horse and coat): a black horse that burns and is never burned; fearless and
	 * fast. Found wild in the Nether, or over the badlands at night ({@link MythicMounts}).
	 */
	NIGHTMARE(0.30, 0.36, 0.70, 0.95, 28, 34, 1.3, 0.8, 1.0, 1.05F, 1.0F, 1.0F, 1.02F, Gait.GALLOP, "nightmare"),
	/** (0.5.56) The bat horse (Mo' Creatures'): a leathery-winged horse of the dark woods that flies under its rider ({@link MythicMounts}). */
	BAT_HORSE(0.26, 0.31, 0.60, 0.80, 22, 28, 1.0, 1.0, 0.7, 1.0F, 0.95F, 1.0F, 1.0F, Gait.GALLOP, "bat");

	/** The horse breeds (every breed but the elephant and the mammoth). */
	public static final Breed[] HORSES = {DESTRIER, COURSER, PALFREY, DRAFT, STEPPE_PONY, DESERT_HORSE};

	public final double speedMin, speedMax, jumpMin, jumpMax;
	public final int healthMin, healthMax;
	/** Stamina it holds (x {@link Equestrian#STAMINA}), and how fast it spends it. */
	public final double capacity, drain;
	/** Its natural courage (blended with the courage stat, {@link Equine#bravery}). */
	public final double courage;
	/** Its size (the scale attribute), and how its body is drawn stretched on top of that (width, height, length). */
	public final float scale, width, height, length;
	public final Gait topGait;
	public final String[] coats;

	Breed(double speedMin, double speedMax, double jumpMin, double jumpMax, int healthMin, int healthMax, double capacity, double drain,
		double courage, float scale, float width, float height, float length, Gait topGait, String... coats) {
		this.speedMin = speedMin;
		this.speedMax = speedMax;
		this.jumpMin = jumpMin;
		this.jumpMax = jumpMax;
		this.healthMin = healthMin;
		this.healthMax = healthMax;
		this.capacity = capacity;
		this.drain = drain;
		this.courage = courage;
		this.scale = scale;
		this.width = width;
		this.height = height;
		this.length = length;
		this.topGait = topGait;
		this.coats = coats;
	}

	/** A war elephant or a mammoth (a {@link WarElephant}, not a horse). */
	public boolean elephant() {
		return this == WAR_ELEPHANT || this == WOOLLY_MAMMOTH;
	}

	public String id() {
		return this.name().toLowerCase(Locale.ROOT);
	}

	public String key() {
		return "breed.jaymoskingdoms." + this.id();
	}

	public static Breed byId(String id) {
		for (Breed b : values()) {
			if (b.id().equals(id)) {
				return b;
			}
		}
		return PALFREY;
	}

	public String coat(int coat) {
		return this.coats[Math.floorMod(coat, this.coats.length)];
	}

	/** Its top speed for a speed stat of {@code stat} (0-1). */
	public double topSpeed(double stat) {
		return this.speedMin + (this.speedMax - this.speedMin) * stat;
	}

	public double jump(double stat) {
		return this.jumpMin + (this.jumpMax - this.jumpMin) * stat;
	}
}
