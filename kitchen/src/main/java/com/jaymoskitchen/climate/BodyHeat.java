package com.jaymoskitchen.climate;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.ModItems;
import com.jaymoskitchen.Stacked;
import com.jaymoskitchen.cooking.Dish;
import com.jaymoskitchen.season.Seasons;
import com.jaymoskitchen.season.WinterCold;
import com.jaymoskitchen.stomach.Stomach;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

/**
 * (0.5.25) Body heat, our own take on Frostiful and Scorchful (LGPL, no 26.3 builds, so nothing is taken from them).
 * Everyone's body sits between {@code -100} (frozen) and {@code 100} (heatstroke), {@code 0} comfortable, and drifts
 * toward what the world around them says ({@link #ambient}): the land's own warmth (with the season's chill on temperate
 * lands), sun and night under the open sky, shelter and caves, height, rain, snow and water, fires close by, what you
 * wear, a hot meal, and whatever other mods add ({@link #WEATHER}: Kingdoms' blizzards and sandstorms).
 * <p>
 * Cold: shivering burns food ({@link #CHILLY}, what winter's cold used to be, {@link WinterCold}), freezing frosts the
 * screen over and slows you ({@link #FREEZING}), and deep cold hurts ({@link #FROSTBITE}). Heat: you sweat water away
 * ({@link #WARM}, {@link Thirst}), overheating tires you ({@link #OVERHEATING}), and heatstroke slows your steps and hurts
 * ({@link #HEATSTROKE}). Wool and leather keep cold off ({@link WinterCold#WARM_CLOTHING}) but are stuffy in the heat;
 * {@link #COOL_CLOTHING} keeps the sun off, and metal armour bakes in it. Clean water cools you.
 */
public final class BodyHeat {
	public static final float MIN = -100.0F, MAX = 100.0F;
	/** Where the bad starts, in body heat. */
	public static final float CHILLY = -35.0F, FREEZING = -60.0F, FROSTBITE = -85.0F;
	public static final float WARM = 35.0F, OVERHEATING = 60.0F, HEATSTROKE = 85.0F;
	/** How fast the body follows the air, a second (a share of the gap); water and a fire beside you are quicker. */
	static final float DRIFT = 0.035F, WATER_DRIFT = 0.08F;
	/** Each warm piece worn slows the cold getting in by this share. */
	static final float WARM_SLOWS = 0.12F;

	// the world's warmth, in body heat
	/** A land's base temperature (plains 0.8) is comfortable between these; colder or hotter lands go by the slopes below. */
	static final float COMFORT_LOW = 0.5F, COMFORT_HIGH = 0.95F;
	/** Cold lands: down to {@code COLD_AT_ZERO} at 0 (snowy plains), then {@code FROZEN_SLOPE} a degree below that. */
	static final float COLD_AT_ZERO = -40.0F, FROZEN_SLOPE = 60.0F;
	/** Hot lands: a desert (2.0) is about 52 in the shade. */
	static final float HOT_SLOPE = 50.0F;
	static final float NETHER = 55.0F, END = -20.0F;
	/** Under the open sky: the sun on a hot or mild land, on a cold one, and night (dry lands lose more). */
	static final float SUN = 15.0F, COLD_SUN = 5.0F, NIGHT = -15.0F, DRY_NIGHT = -35.0F;
	static final float DRY_LAND = 1.5F;
	/** Under a roof the land counts this much; a cave far underground sits near {@code CAVE}. */
	static final float SHELTER = 0.5F, CAVE = 5.0F, CAVE_PULL = 0.75F;
	static final int CAVE_DEPTH = 10;
	/** Deep rock: below {@code DEEP_FROM} (a y level) the cave grows this much warmer for every block further down. */
	static final int DEEP_FROM = -32;
	static final float DEEP_WARM = 0.6F;
	/**
	 * One of Legendary Survival Overhaul's degrees in body heat, for the numbers taken from it (height, time of day, wet).
	 * Set so its height cold (6 degrees every 64 blocks) matches what ours was (16 a 64 blocks), and its wet (10 degrees)
	 * what standing in water was (25).
	 */
	static final float LSO_DEGREE = 2.5F;
	/** Thin air: 6 degrees colder every 64 blocks, from the treeline (this many blocks over the sea: y 120 in the Overworld). */
	static final float HEIGHT_COLD = 6.0F * LSO_DEGREE / 64.0F;
	static final int TREELINE_ABOVE_SEA = 57;
	/** The day's swing in the overworld: this much warmer at noon, as much colder at midnight, smoothly between. */
	static final float TIME_SWING = 2.0F * LSO_DEGREE;
	/** Jaymo's Powers' Aether (its own dimension, or its band of a One Dimension world): no thin-air cold there. */
	static final ResourceKey<Level> AETHER = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("jaymospowers", "aether"));
	/**
	 * Falling snow chills the air. Rain has no term of its own: it takes the sun away and soaks you ({@link Wetness}), and
	 * counting it a third time made a summer shower as cold as snow.
	 */
	static final float SNOW = -20.0F, POWDER_SNOW = -40.0F;
	/**
	 * A hot spring (water over a magma block {@link #SPRING_DEPTH} or fewer blocks under your feet, as Jaymo's Powers
	 * digs them): the water holds you at least this warm, and its wet doesn't chill you while you're in it.
	 */
	static final float HOT_SPRING = 20.0F;
	static final int SPRING_DEPTH = 3;
	/** Wet through ({@link Wetness}): 10 degrees colder, less as you dry; soaked on cold land, this many times that. */
	static final float WET = -10.0F * LSO_DEGREE, COLD_SOAK = 1.5F;
	/** Soaked clothes keep this share of their warmth; wool keeps all of it. */
	static final float SOAKED_WARMTH = 0.5F;
	/**
	 * Warmth from what's burning near you (at the source; nothing {@link #FIRE_RANGE} blocks off), and the share of it
	 * that reaches you under the open sky rather than indoors.
	 */
	static final float OUTDOORS = 0.5F;
	/** What the block tags give off at the source, strongest first: molten, flame, soul, stove, warm ({@code heat_sources/*}). */
	static final float MOLTEN = 45.0F, FLAME = 30.0F, SOUL = 25.0F, STOVE = 18.0F, WARM_BLOCK = 12.0F;
	/** What ice and snow near you take off the heat (at the source), and how far they reach ({@code chill_sources/*}). */
	static final float CHILL_HARD = 16.0F, CHILL_SOFT = 8.0F;
	static final int CHILL_RANGE = 5, CHILL_HEIGHT = 3, CHILL_CELLS = 700;
	static final int FIRE_RANGE = 20;
	/** The most air a walk from you looks through for a fire, near and far (a fire's warmth fills its room, not the stone round it). */
	static final int NEAR_CELLS = 700, FAR_CELLS = 8000;
	/**
	 * Looked for every second this close (and {@link #NEAR_HEIGHT} up and down); the rest of {@link #FIRE_RANGE} every
	 * {@link #FAR_EVERY} seconds ({@link #FIRE_HEIGHT} up and down).
	 */
	static final int NEAR_RANGE = 5, NEAR_HEIGHT = 2, FAR_EVERY = 5, FIRE_HEIGHT = 4;
	/** After spawning or dying, this many ticks in which the world can't push your body further from comfortable. */
	static final int GRACE_TICKS = 90 * 20;
	/**
	 * A brand-new character's first wake: this many ticks of the same grace, saved on the player once ({@link #CLEMENCY});
	 * a player who has already played this many ticks in the world is no newcomer.
	 */
	static final int CLEMENCY_TICKS = 10 * 60 * 20, NEWCOMER_TICKS = 5 * 60 * 20;
	/** Clothes: a warm piece against the cold, the same piece in the heat, a cool piece in the heat. */
	static final float WARM_PIECE = 9.0F, STUFFY_PIECE = 4.0F, COOL_PIECE = 8.0F;
	/**
	 * (Climate track D) An armour trim of copper holds warmth, one of quartz stays cool: each trimmed piece counts as this
	 * much of a warm (or cool) piece, a quarter of a wool shirt's worth.
	 */
	static final float TRIM_PIECE = 0.25F;
	/**
	 * (Climate track D) A piece lined with a dragon's hide ({@link Lining#TEMPERED}) keeps the body between these: no colder than
	 * chilly, no hotter than warm. Already past them (the piece put on late), it comes back this much a second.
	 */
	static final float TEMPERED_LOW = CHILLY, TEMPERED_HIGH = WARM, TEMPERED_PULL = 10.0F;
	private static final EquipmentSlot[] ARMOUR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** Metal in the sun: this much per point of armour. */
	static final float ARMOUR_IN_SUN = 0.6F;
	/** A stew or soup still fresh in your stomach (eaten in the last {@code HOT_MEAL_TICKS}). */
	static final float HOT_MEAL = 12.0F;
	static final int HOT_MEAL_TICKS = 3 * 60 * 20;
	/** Someone proof against the cold or the heat can't be pushed past these. */
	static final float PROOF = 30.0F;

	// what it does
	/** Extra exhaustion a second shivering (4 is a hunger point), doubled freezing; and overheating. */
	static final float CHILL_EXHAUSTION = 0.025F, HEAT_EXHAUSTION = 0.015F;
	/** Thirst's speed warm, overheating and with heatstroke; cold slows it. */
	static final float WARM_THIRST = 1.6F, HOT_THIRST = 2.5F, STROKE_THIRST = 3.5F, COLD_THIRST = 0.75F;
	/** Seconds between hurts from frostbite or heatstroke, and how much. */
	static final int HURT_EVERY = 4;
	static final float HURT = 1.0F;
	/** Frostbite and heatstroke start a count of this many seconds before the first hurt (the on-screen line has told you). */
	static final int HURT_DELAY = 25;
	/** Heatstroke slows your steps by this share at the most, reached after this many seconds of it. */
	static final float STROKE_SLOW = 0.15F;
	static final int STROKE_SLOW_RAMP = 90;
	static final Identifier STROKE_SLOW_ID = JaymosKitchen.id("heatstroke_slow");
	/** Once out of the cold or heat that was hurting, the body comes back toward comfortable this many times as fast. */
	static final float REBOUND = 2.0F;
	/** Burning: the push on the air, and what it adds to the body itself each second. */
	static final float ON_FIRE = 60.0F, FIRE_BODY = 8.0F;
	/** Riding a warm-blooded beast takes this much of the cold off (like a warm piece, in body heat). */
	static final float MOUNT_WARMTH = 10.0F;
	/** A lit torch or lantern in a hand takes this much of the cold off; ice or a snowball in the heat, this much of the heat. */
	static final float HELD_WARM = 8.0F, HELD_COOL = 8.0F;
	/** How often (seconds) breath and sweat show; breath shows when the air is this cold, or you are chilly. */
	static final int PUFF_EVERY = 2;
	static final float BREATH_AIR = -10.0F * LSO_DEGREE;

	/** Tag for worn clothing that keeps the sun off (robes, turbans; empty until a mod fills it). */
	public static final TagKey<Item> COOL_CLOTHING = TagKey.create(Registries.ITEM, JaymosKitchen.id("cool_clothing"));
	/** Blocks that give off heat while lit, by how much ({@link #MOLTEN} and the rest), and what chills the air near it. */
	static final TagKey<Block> HEAT_MOLTEN = blockTag("heat_sources/molten"), HEAT_FLAME = blockTag("heat_sources/flame"),
		HEAT_SOUL = blockTag("heat_sources/soul"), HEAT_STOVE = blockTag("heat_sources/stove"), HEAT_WARM = blockTag("heat_sources/warm"),
		CHILL_HARD_TAG = blockTag("chill_sources/hard"), CHILL_SOFT_TAG = blockTag("chill_sources/soft");
	/** Held in a hand: a lit torch or lantern warms, ice or a snowball cools. */
	static final TagKey<Item> WARMS_IN_HAND = TagKey.create(Registries.ITEM, JaymosKitchen.id("warms_in_hand")),
		COOLS_IN_HAND = TagKey.create(Registries.ITEM, JaymosKitchen.id("cools_in_hand"));
	/** Mounts that are cold-blooded or lifeless: riding them warms nothing. */
	static final TagKey<EntityType<?>> COLD_MOUNTS = TagKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("cold_mounts"));
	public static final String DOWNED_TAG = "jaymospowers_downed";
	static final ResourceKey<DamageType> HEATSTROKE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosKitchen.id("heatstroke"));

	/**
	 * Where your body is and where the world is pushing it. (Hardrock picks) {@code air} is the push of the place alone,
	 * before what you wear, eat and drink: what a held thermometer shows.
	 */
	public record Heat(float body, float ambient, float air) {
		static final Codec<Heat> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.FLOAT.fieldOf("body").forGetter(Heat::body),
			Codec.FLOAT.fieldOf("ambient").forGetter(Heat::ambient),
			Codec.FLOAT.optionalFieldOf("air", 0.0F).forGetter(Heat::air)
		).apply(i, Heat::new));
		static final StreamCodec<ByteBuf, Heat> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, Heat::body,
			ByteBufCodecs.FLOAT, Heat::ambient,
			ByteBufCodecs.FLOAT, Heat::air,
			Heat::new);
		public static final Heat NEUTRAL = new Heat(0.0F, 0.0F, 0.0F);
	}

	/**
	 * What's left of your first-wake grace, in ticks: absent until you first wake, then counting down while you're in the
	 * world, and 0 once it's used up. Kept through death, so a later death or wake-up never renews it.
	 */
	public static final AttachmentType<Integer> CLEMENCY = AttachmentRegistry.create(JaymosKitchen.id("clemency"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	/** Synced to you for the HUD; back to comfortable when you die. */
	public static final AttachmentType<Heat> HEAT = AttachmentRegistry.create(JaymosKitchen.id("body_heat"),
		builder -> builder.persistent(Heat.CODEC).syncWith(Heat.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Other mods' weather on a player, in body heat (summed into {@link #ambient}); Kingdoms adds by reflection. */
	public static final List<ToDoubleFunction<ServerPlayer>> WEATHER = new CopyOnWriteArrayList<>();
	private static Predicate<Player> heatProof = player -> false;
	/** Where other mods say the sun can't reach you (under a rickshaw's hood or a carriage's roof), and mounts that aren't warm. */
	private static final List<Predicate<Player>> SHADE = new CopyOnWriteArrayList<>();
	private static final List<Predicate<Entity>> COLD_MOUNT = new CopyOnWriteArrayList<>();
	/** Warm and cool pieces other mods dress players in outside the armour slots (Kingdoms' harness: fur hoods, turbans). */
	private static final List<ToDoubleFunction<Player>> WARM_WORN = new CopyOnWriteArrayList<>(), COOL_WORN = new CopyOnWriteArrayList<>();
	/** (0.5.60) Places other mods say the weather never reaches (Powers' Other World): body heat held neutral there. */
	private static final List<Predicate<Player>> TEMPERATE = new CopyOnWriteArrayList<>();

	/** Held wet through until this game time ({@link #soak}). */
	private static final Map<UUID, Long> WET_UNTIL = new HashMap<>();
	/** Spawn and death grace ({@link #GRACE_TICKS}) runs to this game time. */
	private static final Map<UUID, Long> GRACE_UNTIL = new HashMap<>();
	/** Seconds in a row in frostbite or heatstroke ({@link #HURT_DELAY}). */
	private static final Map<UUID, Integer> HARM = new HashMap<>();
	/** The far fires' warmth, looked for every {@link #FAR_EVERY} seconds. */
	private static final Map<UUID, Float> FAR_FIRE = new HashMap<>();
	private static final Map<UUID, Integer> STATE = new HashMap<>();
	/** (Hardrock picks) The place's own push on each player, from the last {@link #ambient}: see {@link Heat#air}. */
	private static final Map<UUID, Float> BARE = new HashMap<>();

	private BodyHeat() {
	}

	private static TagKey<Block> blockTag(String path) {
		return TagKey.create(Registries.BLOCK, JaymosKitchen.id(path));
	}

	/** For other mods (by reflection): who the heat doesn't touch (Kingdoms' Emberborn). Fire resistance always does. */
	public static void setHeatProof(Predicate<Player> proof) {
		heatProof = proof;
	}

	/**
	 * For other mods (by reflection): {@code player} is soaked through ({@link Wetness}), and stays so for {@code ticks}
	 * from now before drying starts (Kingdoms' downpours).
	 */
	public static void soak(ServerPlayer player, int ticks) {
		Wetness.set(player, Wetness.MAX);
		WET_UNTIL.merge(player.getUUID(), player.level().getGameTime() + ticks, Math::max);
	}

	/** Spawned or come back from dying: {@link #GRACE_TICKS} of the world not making your body heat any worse. */
	static void grace(ServerPlayer player) {
		GRACE_UNTIL.put(player.getUUID(), player.level().getGameTime() + GRACE_TICKS);
	}

	/** For other mods (by reflection): more weather on players, in body heat. */
	public static void addWeather(ToDoubleFunction<ServerPlayer> weather) {
		WEATHER.add(weather);
	}

	/**
	 * For other mods (by reflection): how many warm pieces ({@code warm}) or cool pieces a player wears that the armour
	 * slots don't show, counted like {@link WinterCold#WARM_CLOTHING} and {@link #COOL_CLOTHING} pieces.
	 */
	public static void addWorn(boolean warm, ToDoubleFunction<Player> pieces) {
		(warm ? WARM_WORN : COOL_WORN).add(pieces);
	}

	/**
	 * For other mods (by reflection): where {@code shaded} is true for a player, the sun doesn't reach them (a rickshaw's
	 * hood, a carriage's roof): no sun on them and no metal baking, whatever the sky.
	 */
	public static void addShade(Predicate<Player> shaded) {
		SHADE.add(shaded);
	}

	/**
	 * For other mods (by reflection): a mount that gives no warmth though it is a living animal (a dragon or the like).
	 * Warm-blooded living animals warm a rider in the cold already; the undead never do ({@code jaymoskitchen:cold_mounts}).
	 */
	public static void addColdMount(Predicate<Entity> cold) {
		COLD_MOUNT.add(cold);
	}

	static boolean shaded(Player player) {
		for (Predicate<Player> shade : SHADE) {
			if (shade.test(player)) {
				return true;
			}
		}
		return false;
	}

	/** Riding a warm-blooded animal: a living animal that isn't undead, in the cold tag, or something a mod says is cold. */
	static boolean warmMount(Player player) {
		Entity mount = player.getVehicle();
		if (!(mount instanceof Animal) || mount.is(EntityTypeTags.UNDEAD) || mount.is(COLD_MOUNTS)) {
			return false;
		}
		for (Predicate<Entity> cold : COLD_MOUNT) {
			if (cold.test(mount)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * For other mods (by reflection): {@code player} starts a new life as a brand-new character (Kingdoms' final Reincarnate
	 * wipes everything): the first-wake grace is theirs again, from the next second they're in the world.
	 */
	public static void newCharacter(ServerPlayer player) {
		player.removeAttached(CLEMENCY);
		if (Warmth.CLEMENCY_EFFECT != null) {
			player.removeEffect(Warmth.CLEMENCY_EFFECT);
		}
	}

	/** Joined the world: a newcomer's first wake starts the grace; someone who has long played here has had theirs. */
	static void joined(ServerPlayer player) {
		grace(player);
		if (!player.hasAttached(CLEMENCY)) {
			long played = player.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME));
			player.setAttached(CLEMENCY, played < NEWCOMER_TICKS ? CLEMENCY_TICKS : 0);
		}
	}

	/**
	 * Counts the first-wake grace down a second and keeps its status icon up; true while it holds this second. With no
	 * record (after {@link #newCharacter}) it starts here.
	 */
	private static boolean clement(ServerPlayer player) {
		int left = player.getAttachedOrElse(CLEMENCY, CLEMENCY_TICKS);
		if (left <= 0) {
			if (player.hasEffect(Warmth.CLEMENCY_EFFECT)) {
				player.removeEffect(Warmth.CLEMENCY_EFFECT);
			}
			if (!player.hasAttached(CLEMENCY)) {
				player.setAttached(CLEMENCY, 0);
			}
			return false;
		}
		int now = Math.max(0, left - 20);
		player.setAttached(CLEMENCY, now);
		if (now > 0 && !player.hasEffect(Warmth.CLEMENCY_EFFECT)) {
			player.addEffect(new MobEffectInstance(Warmth.CLEMENCY_EFFECT, now, 0, true, true, true));
		}
		return true;
	}

	private static float worn(List<ToDoubleFunction<Player>> sources, Player player) {
		float n = 0.0F;
		for (ToDoubleFunction<Player> source : sources) {
			n += (float) source.applyAsDouble(player);
		}
		return n;
	}

	/**
	 * (0.5.60) For other mods (by reflection): where {@code player} is, no cold or heat reaches the body (Andy: "you should
	 * not freeze or overheat in heaven"). Body heat goes straight back to neutral there and stays.
	 */
	public static void addTemperate(Predicate<Player> temperate) {
		TEMPERATE.add(temperate);
	}

	static boolean temperate(Player player) {
		for (Predicate<Player> t : TEMPERATE) {
			if (t.test(player)) {
				return true;
			}
		}
		return false;
	}

	public static Heat heat(Player player) {
		return player.getAttachedOrElse(HEAT, Heat.NEUTRAL);
	}

	public static float body(Player player) {
		return heat(player).body();
	}

	static boolean heatProof(Player player) {
		return player.hasEffect(MobEffects.FIRE_RESISTANCE) || heatProof.test(player);
	}

	static void forget(UUID id) {
		WET_UNTIL.remove(id);
		GRACE_UNTIL.remove(id);
		FAR_FIRE.remove(id);
		HARM.remove(id);
		STATE.remove(id);
		BARE.remove(id);
		Warmth.forget(id);
	}

	/** A cooling drink or the like. */
	static void cool(ServerPlayer player, float by) {
		Heat heat = heat(player);
		if (heat.body() > 0.0F) {
			set(player, new Heat(Math.max(0.0F, heat.body() - by), heat.ambient(), heat.air()));
		}
	}

	private static void set(ServerPlayer player, Heat heat) {
		Heat old = heat(player);
		if (Math.abs(old.body() - heat.body()) >= 0.25F || Math.abs(old.ambient() - heat.ambient()) >= 1.0F
			|| Math.abs(old.air() - heat.air()) >= 1.0F) {
			player.setAttached(HEAT, heat);
		}
	}

	/** Thirst's speed for how hot you are. */
	static float thirstFactor(Player player) {
		float body = body(player);
		if (body >= HEATSTROKE) {
			return STROKE_THIRST;
		}
		if (body >= OVERHEATING) {
			return HOT_THIRST;
		}
		if (body >= WARM) {
			return WARM_THIRST;
		}
		return body <= CHILLY ? COLD_THIRST : 1.0F;
	}

	// ---- every tick: wet, and frost on the screen ----

	static void tick(ServerPlayer player) {
		long now = player.level().getGameTime();
		if (now % Wetness.STEP == 0L) {
			Wetness.step(player);
			if (WET_UNTIL.getOrDefault(player.getUUID(), 0L) > now) {
				Wetness.set(player, Wetness.MAX);
			}
		}
		float body = body(player);
		if (body <= FREEZING && !temperate(player)) {
			// the game's own freezing (frosted screen, slowed steps), held short of fully frozen: the hurt is ours
			int cap = player.getTicksRequiredToFreeze() - 1;
			int frost = Math.round(Mth.clamp((FREEZING - body) / (FREEZING - MIN), 0.0F, 1.0F) * cap);
			if (player.getTicksFrozen() < frost) {
				player.setTicksFrozen(frost);
			}
		}
	}

	// ---- every second ----

	static void second(ServerPlayer player, long seconds) {
		ServerLevel level = player.level();
		if (temperate(player)) {
			// (0.5.60) heaven and the like: neither cold nor heat, whatever the height or the sky says
			set(player, Heat.NEUTRAL);
			announce(player, 0.0F);
			HARM.remove(player.getUUID());
			slow(player, 0);
			return;
		}
		float ambient = ambient(player);
		Heat heat = heat(player);
		float body = heat.body();
		float drift = player.isInWater() ? WATER_DRIFT : DRIFT;
		if (ambient < body && body <= 0.0F) {
			drift *= Math.max(0.2F, 1.0F - WARM_SLOWS * warmPieces(player));
		}
		if (body <= CHILLY && ambient > CHILLY || body >= OVERHEATING && ambient < OVERHEATING) {
			// out of the cold or heat that was hurting: the body comes back to comfortable faster than it fell
			drift *= REBOUND;
		}
		float next = Mth.clamp(body + (ambient - body) * drift, MIN, MAX);
		if (player.isOnFire() && !heatProof(player)) {
			// burning heats the body itself, on top of the air's push
			next = Math.min(MAX, next + FIRE_BODY);
		}
		boolean clement = clement(player);
		if (Math.abs(next) > Math.abs(body) && (clement || GRACE_UNTIL.getOrDefault(player.getUUID(), 0L) > level.getGameTime())) {
			// just spawned, back from dying or on a first wake: the world can't make it worse yet, only better
			next = body;
		}
		if (tempered(player)) {
			// a dragon-hide lining: never worse than chilly or warm; past that already, back by TEMPERED_PULL a second
			if (next > TEMPERED_HIGH) {
				next = Math.max(TEMPERED_HIGH, Math.min(next, body) - TEMPERED_PULL);
			} else if (next < TEMPERED_LOW) {
				next = Math.min(TEMPERED_LOW, Math.max(next, body) + TEMPERED_PULL);
			}
		}
		body = next;
		set(player, new Heat(body, ambient, BARE.getOrDefault(player.getUUID(), ambient)));
		announce(player, body);

		boolean downed = player.entityTags().contains(DOWNED_TAG);
		// seconds in a row in frostbite or heatstroke: the first hurt waits for HURT_DELAY of them
		int harm = 0;
		if (body <= FROSTBITE || body >= HEATSTROKE) {
			harm = HARM.merge(player.getUUID(), 1, Integer::sum);
		} else {
			HARM.remove(player.getUUID());
		}
		slow(player, body >= HEATSTROKE ? harm : 0);
		if ((body <= CHILLY || BARE.getOrDefault(player.getUUID(), 0.0F) <= BREATH_AIR) && seconds % PUFF_EVERY == 0L
			&& !player.isUnderWater()) {
			// breath steaming in the cold: a small puff from the mouth, drifting the way you face (seen by everyone)
			Vec3 look = player.getLookAngle();
			level.sendParticles(ParticleTypes.WHITE_SMOKE, player.getX() + look.x * 0.3, player.getEyeY() - 0.15 + look.y * 0.3,
				player.getZ() + look.z * 0.3, 0, look.x, look.y * 0.5 + 0.1, look.z, 0.04);
		}
		if (body <= CHILLY) {
			player.causeFoodExhaustion(body <= FREEZING ? CHILL_EXHAUSTION * 2.0F : CHILL_EXHAUSTION);
			if (body <= FROSTBITE && !downed && harm >= HURT_DELAY && seconds % HURT_EVERY == 0L) {
				player.hurtServer(level, player.damageSources().freeze(), HURT);
			}
		} else if (body >= OVERHEATING) {
			player.causeFoodExhaustion(HEAT_EXHAUSTION);
			if (seconds % PUFF_EVERY == 0L) {
				// sweat dripping off
				level.sendParticles(ParticleTypes.FALLING_WATER, player.getX(), player.getEyeY(), player.getZ(), 2, 0.25, 0.1, 0.25, 0.0);
			}
			if (body >= HEATSTROKE) {
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, 0, true, false, true));
				if (body >= MAX - 5.0F && !downed && harm >= HURT_DELAY && seconds % HURT_EVERY == 0L) {
					DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(HEATSTROKE_DAMAGE));
					player.hurtServer(level, source, HURT);
				}
			}
		}
	}

	/** Heatstroke slows your steps: up to {@link #STROKE_SLOW} after {@link #STROKE_SLOW_RAMP} seconds of it; none at 0. */
	private static void slow(ServerPlayer player, int seconds) {
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (speed == null) {
			return;
		}
		if (seconds > 0) {
			float share = Math.min(1.0F, seconds / (float) STROKE_SLOW_RAMP);
			speed.addOrUpdateTransientModifier(new AttributeModifier(STROKE_SLOW_ID, -STROKE_SLOW * share, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else if (speed.hasModifier(STROKE_SLOW_ID)) {
			speed.removeModifier(STROKE_SLOW_ID);
		}
	}

	/** A line when you cross into something worse (once, until you're out of it). */
	private static void announce(ServerPlayer player, float body) {
		int state = body <= FROSTBITE ? -3 : body <= FREEZING ? -2 : body <= CHILLY ? -1
			: body >= HEATSTROKE ? 3 : body >= OVERHEATING ? 2 : body >= WARM ? 1 : 0;
		int was = STATE.getOrDefault(player.getUUID(), 0);
		STATE.put(player.getUUID(), state);
		if (state != 0 && (Integer.signum(state) != Integer.signum(was) || Math.abs(state) > Math.abs(was))) {
			String key = switch (state) {
				case -1 -> "chilly";
				case -2 -> "freezing";
				case -3 -> "frostbite";
				case 1 -> "warm";
				case 2 -> "overheating";
				default -> "heatstroke";
			};
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.body_heat." + key)
				.withStyle(state < 0 ? ChatFormatting.AQUA : ChatFormatting.GOLD));
		}
	}

	// ---- what the world says ----

	/** Where the world around {@code player} pushes their body, in body heat. */
	public static float ambient(ServerPlayer player) {
		ServerLevel level = player.level();
		BlockPos at = player.blockPosition();
		BlockPos head = BlockPos.containing(player.getEyePosition());
		boolean sky = level.canSeeSky(head);
		// under a rickshaw's hood or a carriage's roof (a mod says so): the sun can't reach you however open the sky
		boolean shaded = shaded(player);
		// rain or snow falling here, not just somewhere in the world (a desert under a clear sky keeps its sun)
		Biome.Precipitation falling = level.precipitationAt(head);
		float land;
		float base = 0.8F;
		// (0.5.59) by the realm at the player's height, so a One Dimension world's stacked Nether counts (Stacked)
		ResourceKey<Level> realm = Stacked.realm(level, player.getY());
		if (realm == Level.NETHER) {
			// our own Nether lands may feel different from the Nether's blaze (the biome feel tags)
			Float feel = Warmth.feel(level.getBiome(at));
			land = feel != null ? feel : NETHER;
		} else if (realm == Level.END) {
			land = END;
		} else {
			var biome = level.getBiome(at);
			base = biome.value().getBaseTemperature();
			float t = base + Seasons.chill(base);
			Float feel = Warmth.feel(biome);
			if (feel != null) {
				// a land whose own temperature feels wrong says how it feels (the biome feel tags)
				land = feel;
			} else if (t < COMFORT_LOW) {
				land = t >= 0.0F ? COLD_AT_ZERO * (COMFORT_LOW - t) / COMFORT_LOW : COLD_AT_ZERO + t * FROZEN_SLOPE;
			} else if (t > COMFORT_HIGH) {
				land = (t - COMFORT_HIGH) * HOT_SLOPE;
			} else {
				land = 0.0F;
			}
			if (sky) {
				if (level.isBrightOutside()) {
					if (falling == Biome.Precipitation.NONE && !shaded) {
						land += land >= 0.0F ? SUN : COLD_SUN;
					}
				} else {
					land += base >= DRY_LAND ? DRY_NIGHT : NIGHT;
				}
			} else if (at.getY() < level.getSeaLevel() - CAVE_DEPTH) {
				// deep rock grows warmer the further down you go (Overworld only)
				float cave = CAVE;
				if (realm == Level.OVERWORLD && at.getY() < DEEP_FROM) {
					cave += (DEEP_FROM - at.getY()) * DEEP_WARM;
				}
				land = Mth.lerp(CAVE_PULL, land * SHELTER, cave);
			} else {
				land *= SHELTER;
			}
			if (realm == Level.OVERWORLD && (sky || at.getY() >= level.getSeaLevel() - CAVE_DEPTH)) {
				// warmest at noon, coldest at midnight (the clock's noon is 6000)
				long time = level.getOverworldClockTime() % 24000L;
				land += TIME_SWING * (float) Math.cos((time - 6000L) / 24000.0 * Math.PI * 2.0);
			}
			int above = at.getY() - (level.getSeaLevel() + TREELINE_ABOVE_SEA);
			// the Aether's islands keep their own weather: its height is the sky's, not thin air over the Overworld's sea
			// (stacked over the Overworld it stands hundreds of blocks up, which read as deep frostbite)
			if (above > 0 && realm != AETHER) {
				land -= above * HEIGHT_COLD;
			}
			if (realm == Level.OVERWORLD && !Warmth.CLIMATE_MAP) {
				// (Hardrock picks) colder the further north you travel, warmer to the south; with Jaymo's Kingdoms its
				// climate map already does this through the lands themselves, so it isn't counted twice
				land += Warmth.north(level, player.getZ());
			}
		}
		float air = land;
		// (Hardrock picks) damp near the sea
		if (realm != Level.NETHER && realm != Level.END) {
			air += Warmth.damp(level, at, sky, falling != Biome.Precipitation.NONE, land);
		}

		if (falling == Biome.Precipitation.SNOW) {
			air += SNOW;
		}
		boolean spring = hotSpring(player);
		if (!spring) {
			// the one wet term, in the water or out of it: the meter (Jaymo's Powers' Wet and Soaked feed it), worse soaked on cold land
			air += WET * Wetness.share(player) * (Wetness.soaked(player) && land < 0.0F ? COLD_SOAK : 1.0F);
		}
		if (player.isInPowderSnow) {
			air += POWDER_SNOW;
		}
		if (player.isOnFire()) {
			// burning: the air round you is a furnace (fire resistance and heat-proof folk clamp it below)
			air += ON_FIRE;
		}
		if (spring) {
			// the spring's water is warm, however cold the land round it
			air = Math.max(air, HOT_SPRING);
		}

		// the Nether's open caverns count as out of doors: it's no building, and lava is everywhere
		air += fire(player, sky || realm == Level.NETHER);
		// (Hardrock picks) a lit hearth's or icebox's room
		air = Warmth.room(player, air);

		BARE.put(player.getUUID(), Mth.clamp(air, MIN - 10.0F, MAX + 10.0F));

		// what you wear
		float warm = warmPieces(player);
		float cool = pieces(player, COOL_CLOTHING) + Warmth.lined(player, false) + com.jaymoskitchen.tanning.HideTraits.coolPieces(player)
			+ trims(player, TrimMaterials.QUARTZ) + worn(COOL_WORN, player);
		// held in a hand: a lit torch or lantern against the cold, ice or a snowball against the heat; riding a warm beast
		boolean heldWarm = player.getMainHandItem().is(WARMS_IN_HAND) || player.getOffhandItem().is(WARMS_IN_HAND);
		boolean heldCool = player.getMainHandItem().is(COOLS_IN_HAND) || player.getOffhandItem().is(COOLS_IN_HAND);
		if (air < 0.0F) {
			air = Math.min(0.0F, air + warm * WARM_PIECE + (heldWarm ? HELD_WARM : 0.0F) + (warmMount(player) ? MOUNT_WARMTH : 0.0F));
		} else {
			air += warm * STUFFY_PIECE - cool * COOL_PIECE - (heldCool ? HELD_COOL : 0.0F);
			if (!spring) air -= chill(player);
			if (sky && !shaded && level.isBrightOutside() && land > 0.0F) {
				air += metalArmour(player) * ARMOUR_IN_SUN;
			}
		}
		if (hotMeal(player)) {
			air += HOT_MEAL;
		}
		air += Warmth.drinkPush(player);
		for (ToDoubleFunction<ServerPlayer> weather : WEATHER) {
			air += (float) weather.applyAsDouble(player);
		}
		// (0.5.28) Comfort, from a hot bowl of something, keeps the cold off like Frostborn blood
		if (WinterCold.coldProof(player) || player.hasEffect(com.jaymoskitchen.delight.Delight.COMFORT)) {
			air = Math.max(air, -PROOF);
		}
		if (heatProof(player)) {
			air = Math.min(air, PROOF);
		}
		return Mth.clamp(air, MIN - 10.0F, MAX + 10.0F);
	}

	/**
	 * Warmth from the fires, lava and lit stoves within {@link #FIRE_RANGE}: the strongest, less with distance, and less
	 * again out under the sky ({@link #OUTDOORS}). A fire's warmth fills the room it's in and spreads through open air and
	 * doorways, never through stone ({@link Warmth#fill}, the hearth's own room fill), so a furnace behind a wall warms
	 * nobody on the far side. The near ones are looked for every second, the far ones every {@link #FAR_EVERY} seconds.
	 */
	static float fire(ServerPlayer player, boolean outdoors) {
		ServerLevel level = player.level();
		UUID id = player.getUUID();
		if (level.getGameTime() / 20L % FAR_EVERY == 0L || !FAR_FIRE.containsKey(id)) {
			FAR_FIRE.put(id, reach(player, FIRE_RANGE, FIRE_HEIGHT, FAR_CELLS, true));
		}
		float best = Math.max(reach(player, NEAR_RANGE, NEAR_HEIGHT, NEAR_CELLS, true), FAR_FIRE.get(id));
		return outdoors ? best * OUTDOORS : best;
	}

	/** How much the ice and snow in reach take off the heat (0 for none): the strongest, less with distance. */
	static float chill(ServerPlayer player) {
		return reach(player, CHILL_RANGE, CHILL_HEIGHT, CHILL_CELLS, false);
	}

	/**
	 * The strongest {@code heat} (or chill) reaching {@code player} through the air, from within {@code range} across and
	 * {@code height} up and down: walks the air from where they stand and sees what it touches. Sections with nothing of
	 * the kind in them are skipped first, so the walk only happens when something might be there.
	 */
	private static float reach(ServerPlayer player, int range, int height, int cells, boolean heat) {
		ServerLevel level = player.level();
		BlockPos at = player.blockPosition();
		if (!anywhere(level, at, range, height, heat)) {
			return 0.0F;
		}
		float[] best = {0.0F};
		float span = heat ? FIRE_RANGE : CHILL_RANGE;
		Warmth.fill(level, at, List.of(at, BlockPos.containing(player.getEyePosition())), true,
			new Warmth.Walk(range, height, range + height, cells, false), (pos, state) -> {
				float strength = heat ? heatOf(state) : chillOf(state);
				if (strength <= best[0] || heat && !lit(level, pos, state)) {
					return;
				}
				double dx = pos.getX() - at.getX(), dy = pos.getY() - at.getY(), dz = pos.getZ() - at.getZ();
				float near = 1.0F - (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / span;
				best[0] = Math.max(best[0], strength * near);
			});
		return best[0];
	}

	/** Whether any chunk section within the box holds a block that gives off heat (or chill) at all. */
	private static boolean anywhere(ServerLevel level, BlockPos at, int range, int height, boolean heat) {
		int minY = Math.max(level.getMinY(), at.getY() - height), maxY = Math.min(level.getMaxY(), at.getY() + height);
		for (int cx = (at.getX() - range) >> 4; cx <= (at.getX() + range) >> 4; cx++) {
			for (int cz = (at.getZ() - range) >> 4; cz <= (at.getZ() + range) >> 4; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				for (int sy = minY >> 4; sy <= maxY >> 4; sy++) {
					int index = chunk.getSectionIndexFromSectionY(sy);
					if (index < 0 || index >= chunk.getSectionsCount()) {
						continue;
					}
					LevelChunkSection section = chunk.getSection(index);
					if (!section.hasOnlyAir() && section.maybeHas(heat ? state -> heatOf(state) > 0.0F : state -> chillOf(state) > 0.0F)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** How much warmth a block gives off at its source when lit (0 for none): the {@code heat_sources/*} block tags. */
	static float heatOf(BlockState state) {
		if (state.is(HEAT_MOLTEN)) {
			return MOLTEN;
		} else if (state.is(HEAT_SOUL)) {
			return SOUL;
		} else if (state.is(HEAT_FLAME)) {
			return FLAME;
		} else if (state.is(HEAT_STOVE) || state.getBlock() instanceof AbstractFurnaceBlock) {
			return STOVE;
		} else if (state.is(HEAT_WARM)) {
			return WARM_BLOCK;
		}
		return 0.0F;
	}

	/** How much heat ice and snow take out of the air near them at their source (0 for none): the {@code chill_sources/*} tags. */
	static float chillOf(BlockState state) {
		return state.is(CHILL_HARD_TAG) ? CHILL_HARD : state.is(CHILL_SOFT_TAG) ? CHILL_SOFT : 0.0F;
	}

	/**
	 * Whether a block from the heat tags is burning now: a lit stove (or anything with {@code lit}), a boiler with heat in
	 * it, an oven whose fire is lit (a block entity that says so); blocks with no way of being unlit (fire, lava, magma) are.
	 */
	private static boolean lit(ServerLevel level, BlockPos pos, BlockState state) {
		if (state.hasProperty(BlockStateProperties.LIT)) {
			return state.getValue(BlockStateProperties.LIT);
		}
		if (state.getBlock() instanceof com.jaymoskitchen.mill.steam.BoilerBlock) {
			return state.getValue(com.jaymoskitchen.mill.steam.BoilerBlock.HEAT) > 0;
		}
		if (state.hasBlockEntity()) {
			var entity = level.getBlockEntity(pos);
			return entity != null && com.jaymoskitchen.kitchen.OvenHob.isLit(entity);
		}
		return true;
	}

	/** In a hot spring: in water, with a magma block no more than {@link #SPRING_DEPTH} blocks under the feet. */
	static boolean hotSpring(Player player) {
		if (!player.isInWater()) {
			return false;
		}
		BlockPos feet = player.blockPosition();
		for (int i = 1; i <= SPRING_DEPTH; i++) {
			if (player.level().getBlockState(feet.below(i)).is(Blocks.MAGMA_BLOCK)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The armour points worn that bake in the sun: all but what the warm and cool clothes give (leather, wool and robes
	 * don't heat up like metal does; leather's stuffiness is counted already).
	 */
	static float metalArmour(Player player) {
		float cloth = 0.0F;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack piece = player.getItemBySlot(slot);
			if (piece.is(WinterCold.WARM_CLOTHING) || piece.is(COOL_CLOTHING)) {
				float[] armour = {0.0F};
				piece.forEachModifier(slot, (attribute, modifier) -> {
					if (attribute.value() == Attributes.ARMOR.value() && modifier.operation() == AttributeModifier.Operation.ADD_VALUE) {
						armour[0] += (float) modifier.amount();
					}
				});
				cloth += armour[0];
			}
		}
		return Math.max(0.0F, player.getArmorValue() - cloth);
	}

	/** Jaymo's Powers' studded leather (optional: an unknown tag matches nothing): all four pieces worn count as one warm piece. */
	static final TagKey<Item> STUDDED = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("jaymospowers", "studded_armor"));

	/** Warm clothes worn, warm linings sewn into what's worn ({@link Lining}), and llama leather (half a piece each). */
	static float warmPieces(Player player) {
		float warm = pieces(player, WinterCold.WARM_CLOTHING) + Warmth.lined(player, true) + com.jaymoskitchen.tanning.HideTraits.warmPieces(player)
			+ (pieces(player, STUDDED) == 4 ? 1.0F : 0.0F) + trims(player, TrimMaterials.COPPER) + worn(WARM_WORN, player);
		if (Wetness.soaked(player)) {
			// wet wool still keeps you warm; soaked leather, linings and the rest keep half
			int wool = woolPieces(player);
			warm = wool + Math.max(0.0F, warm - wool) * SOAKED_WARMTH;
		}
		return warm;
	}

	/** Warm pieces worn that are wool. */
	static int woolPieces(Player player) {
		int n = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			var piece = player.getItemBySlot(slot);
			if (piece.is(WinterCold.WARM_CLOTHING) && net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(piece.getItem()).getPath().contains("wool")) {
				n++;
			}
		}
		return n;
	}

	/** What the armour trims of {@code material} on worn pieces count as, in warm (copper) or cool (quartz) pieces. */
	static float trims(Player player, ResourceKey<TrimMaterial> material) {
		float n = 0.0F;
		for (EquipmentSlot slot : ARMOUR) {
			ArmorTrim trim = player.getItemBySlot(slot).get(DataComponents.TRIM);
			if (trim != null && trim.material().is(material)) {
				n += TRIM_PIECE;
			}
		}
		return n;
	}

	/** Wearing a piece lined with a dragon's hide ({@link Lining#TEMPERED}). */
	static boolean tempered(Player player) {
		for (EquipmentSlot slot : ARMOUR) {
			if (player.getItemBySlot(slot).get(Warmth.LINING) == Lining.TEMPERED) {
				return true;
			}
		}
		return false;
	}

	static int pieces(Player player, TagKey<Item> tag) {
		int n = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			if (player.getItemBySlot(slot).is(tag)) {
				n++;
			}
		}
		return n;
	}

	/** A stew or soup eaten in the last few minutes, still warm inside you. */
	static boolean hotMeal(Player player) {
		for (Stomach.Meal meal : Stomach.meals(player)) {
			Item item = meal.item();
			Dish dish = ModItems.dishOf(item);
			boolean stew = dish != null ? dish.bowlFood()
				: item == Items.MUSHROOM_STEW || item == Items.BEETROOT_SOUP || item == Items.RABBIT_STEW || item == Items.SUSPICIOUS_STEW;
			if (stew && meal.total() - meal.left() < HOT_MEAL_TICKS) {
				return true;
			}
		}
		return false;
	}
}
