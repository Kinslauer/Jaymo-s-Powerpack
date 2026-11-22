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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
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
 * ({@link #WARM}, {@link Thirst}), overheating tires you ({@link #OVERHEATING}), and heatstroke makes you sick and hurts
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
	/**
	 * One of Legendary Survival Overhaul's degrees in body heat, for the numbers taken from it (height, time of day, wet).
	 * Set so its height cold (6 degrees every 64 blocks) matches what ours was (16 a 64 blocks), and its wet (10 degrees)
	 * what standing in water was (25).
	 */
	static final float LSO_DEGREE = 2.5F;
	/** Thin air: 6 degrees colder every 64 blocks over the sea. */
	static final float HEIGHT_COLD = 6.0F * LSO_DEGREE / 64.0F;
	/** The day's swing in the overworld: this much warmer at noon, as much colder at midnight, smoothly between. */
	static final float TIME_SWING = 2.0F * LSO_DEGREE;
	/** Jaymo's Powers' Aether (its own dimension, or its band of a One Dimension world): no thin-air cold there. */
	static final ResourceKey<Level> AETHER = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath("jaymospowers", "aether"));
	static final float RAIN = -15.0F, SNOW = -20.0F, POWDER_SNOW = -40.0F;
	/** Wet through ({@link Wetness}): 10 degrees colder, less as you dry; soaked on cold land, this many times that. */
	static final float WET = -10.0F * LSO_DEGREE, COLD_SOAK = 1.5F;
	/** Soaked clothes keep this share of their warmth; wool keeps all of it. */
	static final float SOAKED_WARMTH = 0.5F;
	/**
	 * Warmth from what's burning near you (at the source; nothing {@link #FIRE_RANGE} blocks off), and the share of it
	 * that reaches you under the open sky rather than indoors.
	 */
	static final float FIRE = 30.0F, SOUL_FIRE = 25.0F, LAVA = 45.0F, MAGMA = 12.0F, FURNACE = 18.0F, OUTDOORS = 0.5F;
	static final int FIRE_RANGE = 20;
	/**
	 * Looked for every second this close (and {@link #NEAR_HEIGHT} up and down); the rest of {@link #FIRE_RANGE} every
	 * {@link #FAR_EVERY} seconds ({@link #FIRE_HEIGHT} up and down).
	 */
	static final int NEAR_RANGE = 5, NEAR_HEIGHT = 2, FAR_EVERY = 5, FIRE_HEIGHT = 4;
	/** After spawning or dying, this many ticks in which the world can't push your body further from comfortable. */
	static final int GRACE_TICKS = 90 * 20;
	/** Clothes: a warm piece against the cold, the same piece in the heat, a cool piece in the heat. */
	static final float WARM_PIECE = 9.0F, STUFFY_PIECE = 4.0F, COOL_PIECE = 8.0F;
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
	/** Heatstroke's sick spells: this long, every this many seconds. */
	static final int STROKE_NAUSEA = 120, STROKE_EVERY = 20;
	/** How often (seconds) breath and sweat show; breath shows when the air is this cold, or you are chilly. */
	static final int PUFF_EVERY = 2;
	static final float BREATH_AIR = -10.0F * LSO_DEGREE;

	/** Tag for worn clothing that keeps the sun off (robes, turbans; empty until a mod fills it). */
	public static final TagKey<Item> COOL_CLOTHING = TagKey.create(Registries.ITEM, JaymosKitchen.id("cool_clothing"));
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

	/** Synced to you for the HUD; back to comfortable when you die. */
	public static final AttachmentType<Heat> HEAT = AttachmentRegistry.create(JaymosKitchen.id("body_heat"),
		builder -> builder.persistent(Heat.CODEC).syncWith(Heat.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Other mods' weather on a player, in body heat (summed into {@link #ambient}); Kingdoms adds by reflection. */
	public static final List<ToDoubleFunction<ServerPlayer>> WEATHER = new CopyOnWriteArrayList<>();
	private static Predicate<Player> heatProof = player -> false;
	/** (0.5.60) Places other mods say the weather never reaches (Powers' Other World): body heat held neutral there. */
	private static final List<Predicate<Player>> TEMPERATE = new CopyOnWriteArrayList<>();

	/** Held wet through until this game time ({@link #soak}). */
	private static final Map<UUID, Long> WET_UNTIL = new HashMap<>();
	/** Spawn and death grace ({@link #GRACE_TICKS}) runs to this game time. */
	private static final Map<UUID, Long> GRACE_UNTIL = new HashMap<>();
	/** The far fires' warmth, looked for every {@link #FAR_EVERY} seconds. */
	private static final Map<UUID, Float> FAR_FIRE = new HashMap<>();
	private static final Map<UUID, Integer> STATE = new HashMap<>();
	/** (Hardrock picks) The place's own push on each player, from the last {@link #ambient}: see {@link Heat#air}. */
	private static final Map<UUID, Float> BARE = new HashMap<>();

	private BodyHeat() {
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
			return;
		}
		float ambient = ambient(player);
		Heat heat = heat(player);
		float body = heat.body();
		float drift = player.isInWater() ? WATER_DRIFT : DRIFT;
		if (ambient < body && body <= 0.0F) {
			drift *= Math.max(0.2F, 1.0F - WARM_SLOWS * warmPieces(player));
		}
		float next = Mth.clamp(body + (ambient - body) * drift, MIN, MAX);
		if (Math.abs(next) > Math.abs(body) && GRACE_UNTIL.getOrDefault(player.getUUID(), 0L) > level.getGameTime()) {
			// just spawned or back from dying: the world can't make it worse yet, only better
			next = body;
		}
		body = next;
		set(player, new Heat(body, ambient, BARE.getOrDefault(player.getUUID(), ambient)));
		announce(player, body);

		boolean downed = player.entityTags().contains(DOWNED_TAG);
		if ((body <= CHILLY || BARE.getOrDefault(player.getUUID(), 0.0F) <= BREATH_AIR) && seconds % PUFF_EVERY == 0L
			&& !player.isUnderWater()) {
			// breath steaming in the cold: a small puff from the mouth, drifting the way you face (seen by everyone)
			Vec3 look = player.getLookAngle();
			level.sendParticles(ParticleTypes.WHITE_SMOKE, player.getX() + look.x * 0.3, player.getEyeY() - 0.15 + look.y * 0.3,
				player.getZ() + look.z * 0.3, 0, look.x, look.y * 0.5 + 0.1, look.z, 0.04);
		}
		if (body <= CHILLY) {
			player.causeFoodExhaustion(body <= FREEZING ? CHILL_EXHAUSTION * 2.0F : CHILL_EXHAUSTION);
			if (body <= FROSTBITE && !downed && seconds % HURT_EVERY == 0L) {
				player.hurtServer(level, player.damageSources().freeze(), HURT);
			}
		} else if (body >= OVERHEATING) {
			player.causeFoodExhaustion(HEAT_EXHAUSTION);
			if (seconds % PUFF_EVERY == 0L) {
				// sweat dripping off
				level.sendParticles(ParticleTypes.FALLING_WATER, player.getX(), player.getEyeY(), player.getZ(), 2, 0.25, 0.1, 0.25, 0.0);
			}
			if (body >= HEATSTROKE) {
				if (seconds % STROKE_EVERY == 0L) {
					player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, STROKE_NAUSEA, 0));
				}
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 50, 0, true, false, true));
				if (body >= MAX - 5.0F && !downed && seconds % HURT_EVERY == 0L) {
					DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(HEATSTROKE_DAMAGE));
					player.hurtServer(level, source, HURT);
				}
			}
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
		float land;
		float base = 0.8F;
		// (0.5.59) by the realm at the player's height, so a One Dimension world's stacked Nether counts (Stacked)
		ResourceKey<Level> realm = Stacked.realm(level, player.getY());
		if (realm == Level.NETHER) {
			land = NETHER;
		} else if (realm == Level.END) {
			land = END;
		} else {
			base = level.getBiome(at).value().getBaseTemperature();
			float t = base + Seasons.chill(base);
			if (t < COMFORT_LOW) {
				land = t >= 0.0F ? COLD_AT_ZERO * (COMFORT_LOW - t) / COMFORT_LOW : COLD_AT_ZERO + t * FROZEN_SLOPE;
			} else if (t > COMFORT_HIGH) {
				land = (t - COMFORT_HIGH) * HOT_SLOPE;
			} else {
				land = 0.0F;
			}
			if (sky) {
				if (level.isBrightOutside()) {
					if (!level.isRaining()) {
						land += land >= 0.0F ? SUN : COLD_SUN;
					}
				} else {
					land += base >= DRY_LAND ? DRY_NIGHT : NIGHT;
				}
			} else if (at.getY() < level.getSeaLevel() - CAVE_DEPTH) {
				land = Mth.lerp(CAVE_PULL, land * SHELTER, CAVE);
			} else {
				land *= SHELTER;
			}
			if (realm == Level.OVERWORLD && (sky || at.getY() >= level.getSeaLevel() - CAVE_DEPTH)) {
				// warmest at noon, coldest at midnight (the clock's noon is 6000)
				long time = level.getOverworldClockTime() % 24000L;
				land += TIME_SWING * (float) Math.cos((time - 6000L) / 24000.0 * Math.PI * 2.0);
			}
			int above = at.getY() - level.getSeaLevel();
			// the Aether's islands keep their own weather: its height is the sky's, not thin air over the Overworld's sea
			// (stacked over the Overworld it stands hundreds of blocks up, which read as deep frostbite)
			if (above > 0 && realm != AETHER) {
				land -= above * HEIGHT_COLD;
			}
			if (realm == Level.OVERWORLD) {
				// (Hardrock picks) colder the further north you travel, warmer to the south
				land += Warmth.north(level, player.getZ());
			}
		}
		float air = land;
		// (Hardrock picks) damp near the sea
		if (realm == Level.OVERWORLD) {
			air += Warmth.damp(level, at, sky, land);
		}

		// wet
		Biome.Precipitation falling = level.precipitationAt(head);
		if (falling == Biome.Precipitation.RAIN) {
			air += RAIN;
		} else if (falling == Biome.Precipitation.SNOW) {
			air += SNOW;
		}
		// the one wet term, in the water or out of it: the meter (Jaymo's Powers' Wet and Soaked feed it), worse soaked on cold land
		air += WET * Wetness.share(player) * (Wetness.soaked(player) && land < 0.0F ? COLD_SOAK : 1.0F);
		if (player.isInPowderSnow) {
			air += POWDER_SNOW;
		}

		// the Nether's open caverns count as out of doors: it's no building, and lava is everywhere
		air += fire(player, sky || realm == Level.NETHER);
		// (Hardrock picks) a lit hearth's or icebox's room
		air = Warmth.room(player, air);

		BARE.put(player.getUUID(), Mth.clamp(air, MIN - 10.0F, MAX + 10.0F));

		// what you wear
		float warm = warmPieces(player);
		float cool = pieces(player, COOL_CLOTHING) + Warmth.lined(player, false) + com.jaymoskitchen.tanning.HideTraits.coolPieces(player);
		if (air < 0.0F) {
			air = Math.min(0.0F, air + warm * WARM_PIECE);
		} else {
			air += warm * STUFFY_PIECE - cool * COOL_PIECE;
			if (sky && level.isBrightOutside() && land > 0.0F) {
				air += player.getArmorValue() * ARMOUR_IN_SUN;
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
	 * Warmth from the fires, lava and lit furnaces within {@link #FIRE_RANGE}: the strongest, less with distance, and
	 * less again out under the sky ({@link #OUTDOORS}). The near ones are looked for every second, the far ones every
	 * {@link #FAR_EVERY} seconds, skipping whole chunk sections with nothing hot in them.
	 */
	static float fire(ServerPlayer player, boolean outdoors) {
		ServerLevel level = player.level();
		BlockPos at = player.blockPosition();
		UUID id = player.getUUID();
		if (level.getGameTime() / 20L % FAR_EVERY == 0L || !FAR_FIRE.containsKey(id)) {
			FAR_FIRE.put(id, fire(level, at, FIRE_RANGE, FIRE_HEIGHT));
		}
		float best = Math.max(fire(level, at, NEAR_RANGE, NEAR_HEIGHT), FAR_FIRE.get(id));
		return outdoors ? best * OUTDOORS : best;
	}

	/** The strongest warmth reaching {@code at} from within {@code range} across and {@code height} up and down. */
	private static float fire(ServerLevel level, BlockPos at, int range, int height) {
		float best = 0.0F;
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
					if (section.hasOnlyAir() || !section.maybeHas(state -> heatOf(state) > 0.0F)) {
						continue;
					}
					int x0 = Math.max(cx << 4, at.getX() - range), x1 = Math.min((cx << 4) + 15, at.getX() + range);
					int z0 = Math.max(cz << 4, at.getZ() - range), z1 = Math.min((cz << 4) + 15, at.getZ() + range);
					int y0 = Math.max(sy << 4, minY), y1 = Math.min((sy << 4) + 15, maxY);
					for (int y = y0; y <= y1; y++) {
						for (int x = x0; x <= x1; x++) {
							for (int z = z0; z <= z1; z++) {
								float strength = heatOf(section.getBlockState(x & 15, y & 15, z & 15));
								if (strength <= best) {
									continue;
								}
								double dx = x - at.getX(), dy = y - at.getY(), dz = z - at.getZ();
								float near = 1.0F - (float) Math.sqrt(dx * dx + dy * dy + dz * dz) / FIRE_RANGE;
								best = Math.max(best, strength * near);
							}
						}
					}
				}
			}
		}
		return best;
	}

	/** How much warmth a block gives off at its source (0 for none). */
	private static float heatOf(BlockState state) {
		if (state.is(Blocks.LAVA)) {
			return LAVA;
		} else if (state.is(Blocks.SOUL_FIRE) || state.is(Blocks.SOUL_CAMPFIRE) && CampfireBlock.isLitCampfire(state)) {
			return SOUL_FIRE;
		} else if (state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) && CampfireBlock.isLitCampfire(state)) {
			return FIRE;
		} else if (state.is(Blocks.MAGMA_BLOCK)) {
			return MAGMA;
		} else if (state.getBlock() instanceof AbstractFurnaceBlock && state.getValue(AbstractFurnaceBlock.LIT)) {
			return FURNACE;
		}
		return 0.0F;
	}

	/** Jaymo's Powers' studded leather (optional: an unknown tag matches nothing): all four pieces worn count as one warm piece. */
	static final TagKey<Item> STUDDED = TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("jaymospowers", "studded_armor"));

	/** Warm clothes worn, warm linings sewn into what's worn ({@link Lining}), and llama leather (half a piece each). */
	static float warmPieces(Player player) {
		float warm = pieces(player, WinterCold.WARM_CLOTHING) + Warmth.lined(player, true) + com.jaymoskitchen.tanning.HideTraits.warmPieces(player)
			+ (pieces(player, STUDDED) == 4 ? 1.0F : 0.0F);
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
