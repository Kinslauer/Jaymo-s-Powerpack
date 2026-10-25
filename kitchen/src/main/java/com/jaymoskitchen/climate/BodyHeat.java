package com.jaymoskitchen.climate;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.ModItems;
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
	/** Thin air: this much colder a block above {@code HEIGHT_FROM} over the sea. */
	static final int HEIGHT_FROM = 40;
	static final float HEIGHT_COLD = 0.25F;
	static final float RAIN = -15.0F, SNOW = -20.0F, IN_WATER = -25.0F, WET = -10.0F, POWDER_SNOW = -40.0F;
	/** Ticks you stay wet after getting out of the water or the rain. */
	static final int WET_TICKS = 1200;
	/** Warmth from what's burning close by (at the source; nothing {@link #FIRE_RANGE} blocks off). */
	static final float FIRE = 30.0F, SOUL_FIRE = 25.0F, LAVA = 45.0F, MAGMA = 12.0F, FURNACE = 18.0F;
	static final int FIRE_RANGE = 5;
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
	/** How often (seconds) breath and sweat show. */
	static final int PUFF_EVERY = 2;

	/** Tag for worn clothing that keeps the sun off (robes, turbans; empty until a mod fills it). */
	public static final TagKey<Item> COOL_CLOTHING = TagKey.create(Registries.ITEM, JaymosKitchen.id("cool_clothing"));
	public static final String DOWNED_TAG = "jaymospowers_downed";
	static final ResourceKey<DamageType> HEATSTROKE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, JaymosKitchen.id("heatstroke"));

	/** Where your body is and where the world is pushing it. */
	public record Heat(float body, float ambient) {
		static final Codec<Heat> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.FLOAT.fieldOf("body").forGetter(Heat::body),
			Codec.FLOAT.fieldOf("ambient").forGetter(Heat::ambient)
		).apply(i, Heat::new));
		static final StreamCodec<ByteBuf, Heat> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, Heat::body,
			ByteBufCodecs.FLOAT, Heat::ambient,
			Heat::new);
		public static final Heat NEUTRAL = new Heat(0.0F, 0.0F);
	}

	/** Synced to you for the HUD; back to comfortable when you die. */
	public static final AttachmentType<Heat> HEAT = AttachmentRegistry.create(JaymosKitchen.id("body_heat"),
		builder -> builder.persistent(Heat.CODEC).syncWith(Heat.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));

	/** Other mods' weather on a player, in body heat (summed into {@link #ambient}); Kingdoms adds by reflection. */
	public static final List<ToDoubleFunction<ServerPlayer>> WEATHER = new CopyOnWriteArrayList<>();
	private static Predicate<Player> heatProof = player -> false;

	private static final Map<UUID, Long> WET_UNTIL = new HashMap<>();
	private static final Map<UUID, Integer> STATE = new HashMap<>();

	private BodyHeat() {
	}

	/** For other mods (by reflection): who the heat doesn't touch (Kingdoms' Emberborn). Fire resistance always does. */
	public static void setHeatProof(Predicate<Player> proof) {
		heatProof = proof;
	}

	/** For other mods (by reflection): more weather on players, in body heat. */
	public static void addWeather(ToDoubleFunction<ServerPlayer> weather) {
		WEATHER.add(weather);
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
		STATE.remove(id);
	}

	/** A cooling drink or the like. */
	static void cool(ServerPlayer player, float by) {
		Heat heat = heat(player);
		if (heat.body() > 0.0F) {
			set(player, new Heat(Math.max(0.0F, heat.body() - by), heat.ambient()));
		}
	}

	private static void set(ServerPlayer player, Heat heat) {
		Heat old = heat(player);
		if (Math.abs(old.body() - heat.body()) >= 0.25F || Math.abs(old.ambient() - heat.ambient()) >= 1.0F) {
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
		if (player.isInWaterOrRain()) {
			WET_UNTIL.put(player.getUUID(), now + WET_TICKS);
		}
		float body = body(player);
		if (body <= FREEZING) {
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
		float ambient = ambient(player);
		Heat heat = heat(player);
		float body = heat.body();
		float drift = player.isInWater() ? WATER_DRIFT : DRIFT;
		if (ambient < body && body <= 0.0F) {
			drift *= Math.max(0.2F, 1.0F - WARM_SLOWS * pieces(player, WinterCold.WARM_CLOTHING));
		}
		body = Mth.clamp(body + (ambient - body) * drift, MIN, MAX);
		set(player, new Heat(body, ambient));
		announce(player, body);

		boolean downed = player.entityTags().contains(DOWNED_TAG);
		if (body <= CHILLY) {
			player.causeFoodExhaustion(body <= FREEZING ? CHILL_EXHAUSTION * 2.0F : CHILL_EXHAUSTION);
			if (seconds % PUFF_EVERY == 0L) {
				// breath steaming in the cold
				level.sendParticles(ParticleTypes.CLOUD, player.getX() + player.getLookAngle().x * 0.4, player.getEyeY() - 0.1,
					player.getZ() + player.getLookAngle().z * 0.4, 1, 0.05, 0.02, 0.05, 0.005);
			}
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
		if (level.dimension() == Level.NETHER) {
			land = NETHER;
		} else if (level.dimension() == Level.END) {
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
			int above = at.getY() - level.getSeaLevel() - HEIGHT_FROM;
			if (above > 0) {
				land -= above * HEIGHT_COLD;
			}
		}
		float air = land;

		// wet
		Biome.Precipitation falling = level.precipitationAt(head);
		if (falling == Biome.Precipitation.RAIN) {
			air += RAIN;
		} else if (falling == Biome.Precipitation.SNOW) {
			air += SNOW;
		}
		if (player.isInWater()) {
			air += IN_WATER;
		} else if (WET_UNTIL.getOrDefault(player.getUUID(), 0L) > level.getGameTime()) {
			air += WET;
		}
		if (player.isInPowderSnow) {
			air += POWDER_SNOW;
		}

		air += fire(level, at);

		// what you wear
		int warm = pieces(player, WinterCold.WARM_CLOTHING);
		int cool = pieces(player, COOL_CLOTHING);
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

	/** Warmth from the fires, lava and lit furnaces close by: the strongest, less with distance. */
	static float fire(ServerLevel level, BlockPos at) {
		float best = 0.0F;
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-FIRE_RANGE + 1, -1, -FIRE_RANGE + 1), at.offset(FIRE_RANGE - 1, 2, FIRE_RANGE - 1))) {
			BlockState state = level.getBlockState(pos);
			float strength;
			if (state.is(Blocks.LAVA)) {
				strength = LAVA;
			} else if (state.is(Blocks.SOUL_FIRE) || state.is(Blocks.SOUL_CAMPFIRE) && CampfireBlock.isLitCampfire(state)) {
				strength = SOUL_FIRE;
			} else if (state.is(BlockTags.FIRE) || state.is(BlockTags.CAMPFIRES) && CampfireBlock.isLitCampfire(state)) {
				strength = FIRE;
			} else if (state.is(Blocks.MAGMA_BLOCK)) {
				strength = MAGMA;
			} else if (state.getBlock() instanceof AbstractFurnaceBlock && state.getValue(AbstractFurnaceBlock.LIT)) {
				strength = FURNACE;
			} else {
				continue;
			}
			float near = 1.0F - (float) Math.sqrt(pos.distSqr(at)) / FIRE_RANGE;
			best = Math.max(best, strength * near);
		}
		return best;
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
