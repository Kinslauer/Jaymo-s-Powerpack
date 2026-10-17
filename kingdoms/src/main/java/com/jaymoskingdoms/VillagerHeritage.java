package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * Villagers born into a heritage ({@link Heritage}): every MCA villager, and every one of a settlement's own humanoid
 * folk ({@link Folk}: guards, archers, rulers, builders, farmers and the rest; not orcs, goblins, illagers, pirates or
 * casters), is of some blood. It's settled the first time they're in the world ({@link #loaded}; a folk's when it's set
 * up, {@link #folkSetup}), kept in the same attachment and entity tag ({@code jaymoskingdoms_heritage_<key>}) a player's
 * is, so Jaymo's Powers and every client read it the same way. A child takes one of its parents' (MCA's
 * {@code Genetics.combine}, {@code mixin/mca/McaGeneticsMixin}; a baby carried as an item keeps it in its saved data,
 * {@code mixin/mca/McaHeritageMixin}).
 * <ul>
 * <li><b>Weights</b> ({@code W_*}): plain Heartlanders most of all, the rarer bloods rare, the Verdant and the Warrior-born
 * rarest. The land leans them ({@link #LEAN}): Frostborn in the cold, Emberborn in the heat (and each shuns the other's,
 * {@link #SHUN}), Tideborn by the sea and the rivers, Sylvan in the forests, Stoneborn in the mountains and badlands,
 * Verdant in the jungle. Everyone born in the Verdant World is Verdant.</li>
 * <li><b>Looks:</b> a Verdant gets the green ({@link Verdants#SHADE}: MCA's skin, antennae); the rest wear their marks
 * and eyes through the client's HeritageLooks, as players do.</li>
 * <li><b>Warrior-born:</b> never shows a tail. Each is born either {@link #TAIL_WRAPPED} (kept round the waist, under
 * their clothes) or {@link #TAIL_CUT} (a stump; it never grows back on a villager). Jaymo's Powers may still turn a
 * wrapped one into the wild Great Ape under a full moon (its {@code VillagerApe}): it tags them {@link #APE_TURNED}, and
 * if their tail is cut off in the rampage, swaps the wrapped tag for the cut one and adds {@link #APE_CUT}.</li>
 * <li><b>Talk:</b> asked about their blood (MCA's menu, {@link #ANSWER}), chatted with now and then ({@link #CHAT_CHANCE};
 * a Warrior-born more often), and in gossip when they come over to you ({@link #GOSSIP_CHANCE}), they say what they
 * are, a Warrior-born why there's no tail to see.</li>
 * <li><b>Gifts and flaws</b> that work on a villager ({@link #fit}, {@link #tick}): an Emberborn never burns and is sapped
 * in the cold, a Frostborn never freezes, a Tideborn holds its breath long and swims well, a Stoneborn walks a little
 * slow, a Sylvan lands soft (and a fighting one hits lighter), a Verdant burns longer, a Nightborn is quicker in the dark,
 * a Warrior-born fights harder wounded. Attribute points, hunger, character level and skill experience are a player's
 * alone.</li>
 * </ul>
 */
public final class VillagerHeritage {
	/** How likely each heritage is, before the land leans it (out of their sum). */
	static final int W_HEARTLANDER = 560, W_STONEBORN = 60, W_TIDEBORN = 50, W_SYLVAN = 50, W_VERDANT = 15, W_EMBERBORN = 35,
		W_FROSTBORN = 35, W_NIGHTBORN = 30, W_WARRIORBORN = 25, W_EARTHLING = 70;
	/** A heritage whose land it is counts this many times over; one that shuns the land (ember in the cold, frost in the heat) this share. */
	static final int LEAN = 5;
	static final float SHUN = 0.2F;
	/** Stoneborn lean below this height too (deep in the hills and the caves). */
	static final int DEEP_Y = 40;
	/** The chance a Warrior-born is born with its tail kept wrapped (the rest had it cut). */
	static final float WRAPPED_CHANCE = 0.6F;
	/** Chatted with (MCA), the chance they talk of their blood instead of small talk; a Warrior-born's. */
	public static final float CHAT_CHANCE = 0.15F, WARRIOR_CHAT_CHANCE = 0.35F;
	/** Coming over to gossip, the chance they talk of their own blood. */
	static final float GOSSIP_CHANCE = 0.12F;
	/** How often the gifts that change with where they stand are looked at (ticks). */
	static final int TICK_EVERY = 20;

	/** A Warrior-born villager's tail: kept wrapped, or cut off (for good). */
	public static final String TAIL_WRAPPED = "jaymoskingdoms_tail_wrapped", TAIL_CUT = "jaymoskingdoms_tail_cut";
	/** Set by Jaymo's Powers: this one has turned into the Great Ape before; and lost its tail in the rampage. */
	public static final String APE_TURNED = "jaymospowers_ape_turned", APE_CUT = "jaymospowers_ape_tail_cut";
	/** An MCA villager's own saved data: its heritage's key and its tail tag (a baby item carries them). */
	public static final String SAVE_KEY = "jaymoskingdoms_heritage", SAVE_TAIL = "jaymoskingdoms_tail";
	/** MCA's main-menu answer: ask about their blood. */
	public static final String ANSWER = "jaymoskingdoms_blood";

	/** How many lines each kind of talk has ({@code dialogue.chat.jaymoskingdoms.blood_<kind>/<n>}, 1-based). */
	static final int WRAPPED_LINES = 6, WRAPPED_TURNED_LINES = 4, CUT_LINES = 6, CUT_RAMPAGE_LINES = 4, BLOOD_LINES = 3;

	/** The villagers in the world with a heritage (their gifts are kept up). */
	private static final Set<LivingEntity> LOADED = Collections.newSetFromMap(new IdentityHashMap<>());

	private VillagerHeritage() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register(VillagerHeritage::loaded);
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> LOADED.remove(entity));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LOADED.clear());
		ServerTickEvents.END_SERVER_TICK.register(VillagerHeritage::tick);
		// an Emberborn villager: fire, lava and burning never hurt
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> entity instanceof Player
			|| !source.is(DamageTypeTags.IS_FIRE) || of(entity) != Heritage.Kind.EMBERBORN);
	}

	// ---- who and what ---------------------------------------------------------------------------------------------

	/** An MCA villager, or one of a settlement's humanoid folk. */
	static boolean eligible(Entity entity) {
		if (entity instanceof Folk folk) {
			return humanFolk(folk.role());
		}
		return McaCompat.isMca(entity);
	}

	/** The folk roles that are townsfolk of flesh and blood (orcs, goblins, illagers, pirates and casters aren't). */
	static boolean humanFolk(int role) {
		return switch (role) {
			case Folk.GUARD, Folk.KING, Folk.ARCHER, Folk.LORD, Folk.FOREMAN, Folk.WORKER, Folk.FARMER, Folk.MINER, Folk.WOODCUTTER, Folk.CARTER,
				Folk.PULLER, Folk.STABLEHAND, Folk.FARRIER -> true;
			default -> false;
		};
	}

	/** {@code entity}'s heritage (a villager's or a player's), or null. */
	public static Heritage.@Nullable Kind of(Entity entity) {
		String key = entity.getAttached(Heritage.HERITAGE);
		return key == null ? null : Heritage.Kind.byKey(key);
	}

	/** A Warrior-born whose tail is cut off. */
	public static boolean tailCut(Entity entity) {
		return entity.entityTags().contains(TAIL_CUT);
	}

	/** A heritage for one born at {@code pos}: by the weights, leaned by the land. */
	static Heritage.Kind roll(Level level, BlockPos pos, RandomSource random) {
		if (level.dimension().identifier().equals(Verdants.WORLD)) {
			return Heritage.Kind.VERDANT;
		}
		Holder<Biome> biome = level.getBiome(pos);
		float heat = biome.value().getBaseTemperature();
		boolean hot = heat >= Heritage.HOT, cold = heat <= Heritage.COLD;
		boolean water = biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH);
		boolean forest = biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE);
		boolean stone = biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL) || biome.is(BiomeTags.IS_BADLANDS) || pos.getY() < DEEP_Y;
		Heritage.Kind[] kinds = Heritage.Kind.values();
		float[] weights = new float[kinds.length];
		float total = 0.0F;
		for (int i = 0; i < kinds.length; i++) {
			float w = switch (kinds[i]) {
				case HEARTLANDER -> W_HEARTLANDER;
				case STONEBORN -> W_STONEBORN * (stone ? LEAN : 1);
				case TIDEBORN -> W_TIDEBORN * (water ? LEAN : 1);
				case SYLVAN -> W_SYLVAN * (forest ? LEAN : 1);
				case VERDANT -> W_VERDANT * (biome.is(BiomeTags.IS_JUNGLE) ? LEAN : 1);
				case EMBERBORN -> W_EMBERBORN * (hot ? LEAN : cold ? SHUN : 1);
				case FROSTBORN -> W_FROSTBORN * (cold ? LEAN : hot ? SHUN : 1);
				case NIGHTBORN -> W_NIGHTBORN;
				case WARRIORBORN -> W_WARRIORBORN;
				case EARTHLING -> W_EARTHLING;
			};
			weights[i] = w;
			total += w;
		}
		float pick = random.nextFloat() * total;
		for (int i = 0; i < kinds.length; i++) {
			pick -= weights[i];
			if (pick < 0.0F) {
				return kinds[i];
			}
		}
		return Heritage.Kind.HEARTLANDER;
	}

	/**
	 * Makes {@code villager} of heritage {@code kind}: the attachment and the tag, a Verdant's green, a Warrior-born's tail
	 * (wrapped or cut, rolled here unless {@code tail} says which), and the gifts.
	 */
	public static void born(LivingEntity villager, Heritage.Kind kind, @Nullable String tail) {
		villager.setAttached(Heritage.HERITAGE, kind.key());
		for (Heritage.Kind k : Heritage.Kind.values()) {
			if (k == kind) {
				villager.addTag(Heritage.TAG + k.key());
			} else {
				villager.removeTag(Heritage.TAG + k.key());
			}
		}
		if (kind == Heritage.Kind.VERDANT && !Verdants.is(villager)) {
			villager.setAttached(Verdants.SHADE, (villager.getRandom().nextFloat() + villager.getRandom().nextFloat()) / 2.0F);
		}
		villager.removeTag(TAIL_WRAPPED);
		villager.removeTag(TAIL_CUT);
		if (kind == Heritage.Kind.WARRIORBORN) {
			if (tail == null) {
				tail = villager.getRandom().nextFloat() < WRAPPED_CHANCE ? TAIL_WRAPPED : TAIL_CUT;
			}
			villager.addTag(tail);
		} else {
			villager.removeTag(APE_TURNED);
			villager.removeTag(APE_CUT);
		}
		fit(villager);
	}

	/** Takes {@code villager}'s heritage away (a folk set up as something not of flesh and blood). */
	static void clear(LivingEntity villager) {
		villager.removeAttached(Heritage.HERITAGE);
		for (Heritage.Kind k : Heritage.Kind.values()) {
			villager.removeTag(Heritage.TAG + k.key());
		}
		villager.removeTag(TAIL_WRAPPED);
		villager.removeTag(TAIL_CUT);
		fit(villager);
		LOADED.remove(villager);
	}

	/** A child's heritage: one of its parents' (by a coin), or a new one if neither has any. */
	public static void inherit(LivingEntity child, @Nullable Entity mother, @Nullable Entity father) {
		Heritage.Kind a = mother == null ? null : of(mother), b = father == null ? null : of(father);
		Heritage.Kind kind = a == null ? b : b == null ? a : child.getRandom().nextBoolean() ? a : b;
		if (kind == null) {
			Entity at = mother != null ? mother : father != null ? father : child;
			kind = roll(at.level(), at.blockPosition(), child.getRandom());
		}
		born(child, kind, null);
	}

	/** A baby's heritage from its real parents (a player among them): one of theirs by a coin, or null if neither has one. */
	public static Heritage.@Nullable Kind babyBlood(Entity mother, Entity father) {
		Heritage.Kind a = of(mother), b = of(father);
		return a == null ? b : b == null ? a : mother.getRandom().nextBoolean() ? a : b;
	}

	/** The tail tag {@code villager} carries ({@link #TAIL_WRAPPED} or {@link #TAIL_CUT}), or "". For its saved data. */
	public static String tail(Entity villager) {
		Set<String> tags = villager.entityTags();
		return tags.contains(TAIL_CUT) ? TAIL_CUT : tags.contains(TAIL_WRAPPED) ? TAIL_WRAPPED : "";
	}

	/** A villager comes into the world: one with no heritage yet is born to one here; the gifts are put on. */
	private static void loaded(Entity entity, ServerLevel level) {
		if (!(entity instanceof LivingEntity living) || !eligible(entity)) {
			return;
		}
		Heritage.Kind kind = of(entity);
		if (kind == null) {
			born(living, roll(level, entity.blockPosition(), entity.getRandom()), null);
		} else {
			fit(living);
		}
		LOADED.add(living);
	}

	/** A folk is set up in its role: townsfolk are born to a heritage, anything else has none. */
	static void folkSetup(Folk folk) {
		if (!(folk.level() instanceof ServerLevel level)) {
			return;
		}
		if (!humanFolk(folk.role())) {
			if (of(folk) != null) {
				clear(folk);
			}
			return;
		}
		if (of(folk) == null) {
			born(folk, roll(level, folk.blockPosition(), folk.getRandom()), null);
		}
	}

	// ---- gifts and flaws ------------------------------------------------------------------------------------------

	/** The gifts and flaws that don't change with where they stand. */
	static void fit(LivingEntity villager) {
		Heritage.Kind kind = of(villager);
		modifier(villager, Attributes.MOVEMENT_SPEED, "villager_heritage_slow", kind == Heritage.Kind.STONEBORN ? -Heritage.STONE_SLOW : 0.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(villager, Attributes.OXYGEN_BONUS, "villager_heritage_breath", kind == Heritage.Kind.TIDEBORN ? Heritage.TIDE_OXYGEN : 0.0,
			AttributeModifier.Operation.ADD_VALUE);
		modifier(villager, Attributes.WATER_MOVEMENT_EFFICIENCY, "villager_heritage_swim", kind == Heritage.Kind.TIDEBORN ? Heritage.TIDE_SWIM : 0.0,
			AttributeModifier.Operation.ADD_VALUE);
		modifier(villager, Attributes.SAFE_FALL_DISTANCE, "villager_heritage_soft", kind == Heritage.Kind.SYLVAN ? Heritage.SYLVAN_SAFE_FALL : 0.0,
			AttributeModifier.Operation.ADD_VALUE);
		modifier(villager, Attributes.ATTACK_DAMAGE, "villager_heritage_weak", kind == Heritage.Kind.SYLVAN ? -Heritage.SYLVAN_WEAK : 0.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(villager, Attributes.BURNING_TIME, "villager_heritage_burn",
			kind == Heritage.Kind.VERDANT ? Heritage.VERDANT_BURN : kind == Heritage.Kind.EMBERBORN ? -1.0 : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		if (kind != Heritage.Kind.EMBERBORN && kind != Heritage.Kind.NIGHTBORN) {
			modifier(villager, Attributes.MOVEMENT_SPEED, "villager_heritage_place", 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			modifier(villager, Attributes.ATTACK_DAMAGE, "villager_heritage_place", 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
		if (kind != Heritage.Kind.WARRIORBORN) {
			modifier(villager, Attributes.ATTACK_DAMAGE, "villager_heritage_fury", 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
	}

	/** Every second: an Emberborn's fire put out and the cold's toll, a Frostborn's frost shed, the dark for a Nightborn, a Warrior-born's fury. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % TICK_EVERY != 0 || LOADED.isEmpty()) {
			return;
		}
		for (LivingEntity villager : new ArrayList<>(LOADED)) {
			if (villager.isRemoved() || !villager.isAlive()) {
				continue;
			}
			Heritage.Kind kind = of(villager);
			if (kind == null) {
				continue;
			}
			switch (kind) {
				case EMBERBORN -> {
					if (villager.isOnFire()) {
						villager.clearFire();
					}
					double weak = 0.0, slow = 0.0;
					if (villager.getTicksFrozen() > 0) {
						weak = Heritage.EMBER_FREEZING_WEAK;
						slow = Heritage.EMBER_FREEZING_SLOW;
					} else if (temperature(villager) <= Heritage.COLD) {
						weak = Heritage.EMBER_COLD_WEAK;
						slow = Heritage.EMBER_COLD_SLOW;
					}
					modifier(villager, Attributes.ATTACK_DAMAGE, "villager_heritage_place", -weak, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
					modifier(villager, Attributes.MOVEMENT_SPEED, "villager_heritage_place", -slow, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
				}
				case FROSTBORN -> {
					if (villager.getTicksFrozen() > 0) {
						villager.setTicksFrozen(0);
					}
				}
				case NIGHTBORN -> {
					double damage = 0.0, speed = 0.0;
					if (dark(villager)) {
						damage = Heritage.NIGHT_DAMAGE;
						speed = Heritage.NIGHT_SPEED;
					} else if (inSun(villager)) {
						damage = -Heritage.SUN_WEAK;
					}
					modifier(villager, Attributes.ATTACK_DAMAGE, "villager_heritage_place", damage, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
					modifier(villager, Attributes.MOVEMENT_SPEED, "villager_heritage_place", speed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
				}
				case WARRIORBORN -> {
					boolean wounded = villager.getHealth() < villager.getMaxHealth() * Heritage.WARRIOR_WOUNDED;
					modifier(villager, Attributes.ATTACK_DAMAGE, "villager_heritage_fury", wounded ? Heritage.WARRIOR_FURY : 0.0,
						AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
				}
				default -> {
				}
			}
		}
	}

	private static float temperature(Entity e) {
		return e.level().getBiome(e.blockPosition()).value().getBaseTemperature();
	}

	private static boolean dark(Entity e) {
		Level level = e.level();
		BlockPos pos = e.blockPosition();
		return level.getMaxLocalRawBrightness(pos) <= Heritage.DARK_LIGHT || level.isDarkOutside() && level.canSeeSky(pos);
	}

	private static boolean inSun(Entity e) {
		Level level = e.level();
		BlockPos pos = e.blockPosition().above();
		return level.isBrightOutside() && level.canSeeSky(pos) && !level.isRainingAt(pos);
	}

	/** Sets (or, at 0, takes off) a modifier on {@code villager}, if it has that attribute at all (a villager has no attack). */
	private static void modifier(LivingEntity villager, Holder<Attribute> attribute, String id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = villager.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		Identifier key = JaymosKingdoms.id(id);
		if (amount == 0.0) {
			instance.removeModifier(key);
		} else {
			instance.addOrUpdateTransientModifier(new AttributeModifier(key, amount, operation));
		}
	}

	// ---- talk -----------------------------------------------------------------------------------------------------

	/**
	 * What {@code villager} says of their blood, as a line key (without the {@code /n} MCA picks), or null for a plain
	 * Heartlander. A Warrior-born tells why there's no tail to see.
	 */
	public static @Nullable String talkKey(Entity villager) {
		Heritage.Kind kind = of(villager);
		if (kind == null || kind == Heritage.Kind.HEARTLANDER) {
			return null;
		}
		return "dialogue.chat.jaymoskingdoms.blood_" + talkKind(villager, kind, villager.getRandom());
	}

	/** Which lines: {@code wrapped}, {@code wrapped_turned}, {@code cut}, {@code cut_rampage}, or the heritage's key. */
	private static String talkKind(Entity villager, Heritage.Kind kind, RandomSource random) {
		if (kind != Heritage.Kind.WARRIORBORN) {
			return kind.key();
		}
		Set<String> tags = villager.entityTags();
		if (tags.contains(TAIL_CUT)) {
			return tags.contains(APE_CUT) ? "cut_rampage" : "cut";
		}
		// one who's turned before mostly talks of that
		return tags.contains(APE_TURNED) && random.nextBoolean() ? "wrapped_turned" : "wrapped";
	}

	/** The chance chatting with {@code villager} turns to their blood. */
	public static float chatChance(Entity villager) {
		Heritage.Kind kind = of(villager);
		return kind == null || kind == Heritage.Kind.HEARTLANDER ? 0.0F : kind == Heritage.Kind.WARRIORBORN ? WARRIOR_CHAT_CHANCE : CHAT_CHANCE;
	}

	/** Whether "Ask about their blood" shows in their menu: they have one worth telling of. */
	public static boolean hasTale(Entity villager) {
		return talkKey(villager) != null;
	}

	/** (Coming over to gossip) now and then they talk of their own blood instead; null if not this time. */
	static @Nullable Component gossip(ServerLevel level, Entity villager, ServerPlayer player) {
		Heritage.Kind kind = of(villager);
		if (kind == null || kind == Heritage.Kind.HEARTLANDER || level.getRandom().nextFloat() >= GOSSIP_CHANCE) {
			return null;
		}
		String which = talkKind(villager, kind, level.getRandom());
		int lines = switch (which) {
			case "wrapped" -> WRAPPED_LINES;
			case "wrapped_turned" -> WRAPPED_TURNED_LINES;
			case "cut" -> CUT_LINES;
			case "cut_rampage" -> CUT_RAMPAGE_LINES;
			default -> BLOOD_LINES;
		};
		int n = 1 + Mth.floor(level.getRandom().nextFloat() * lines);
		return Component.translatable("dialogue.chat.jaymoskingdoms.blood_" + which + "/" + Math.min(n, lines), player.getName());
	}
}
