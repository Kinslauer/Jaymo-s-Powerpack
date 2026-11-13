package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.WeatherData;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Everyday magic (fifth edition's cantrips): small spells every caster knows, free, at will, from the Magic screen:
 * <ul>
 * <li><b>Light:</b> a steady glow at the staff's tip (or the fingertip) that goes where they go; cast again to put it out;</li>
 * <li><b>Mend:</b> a cracked tool in hand knit a little; never with a fight on;</li>
 * <li><b>Clean:</b> blood washed off a coat and what is worn ({@link PowersCompat#washBlood});</li>
 * <li><b>Warm</b> and <b>chill:</b> the dish in hand is warmed or cooled; eaten, it warms or cools the body a while. Kitchen has
 * no temperature of its own for a dish, so the spell marks the dish and the eater's body heat does the rest;</li>
 * <li><b>Whisper:</b> a few words to someone in sight, in a bubble over the caster that only they see;</li>
 * <li><b>Weather:</b> what tomorrow brings, and any disaster foretold, told in a bubble.</li>
 * </ul>
 * The light is a real light block that follows the caster one cell at a time and is taken back whenever it moves, goes out,
 * or the server stops.
 */
public final class Cantrips {
	/** The cantrips, in the order the screen lists them. */
	public static final List<String> IDS = List.of("light", "mend", "clean", "warm", "chill", "whisper", "weather");
	/** The light's brightness and how long it burns (ticks, ten minutes). */
	static final int LIGHT_LEVEL = 12, LIGHT_TICKS = 20 * 60 * 10;
	/** Mend: the share of the tool's life it knits back, and the least. */
	static final float MEND_SHARE = 0.2F;
	static final int MEND_LEAST = 8;
	/** A warmed or chilled dish: how long the body feels it (ticks), and by how much (body heat). */
	static final int DISH_TICKS = 3 * 60 * 20;
	static final double DISH_HEAT = 12.0;
	/** Whisper: how far it carries (blocks). */
	static final double WHISPER_REACH = 32.0;
	/** A day in ticks, for the forecast. */
	static final int DAY = 24000;
	/** Custom data key a warmed or chilled dish carries ({@code warm} or {@code chill}). */
	public static final String DISH_KEY = "jaymoskingdoms_dish_temp";
	static final int WHISPER_COLOUR = 0xC9B8FF, WEATHER_COLOUR = 0xBFE3FF;

	private record Glow(ServerLevel level, BlockPos at, long until) {
	}

	private record Dish(double heat, long until) {
	}

	private static final Map<UUID, Glow> LIGHTS = new HashMap<>();
	private static final Map<UUID, Dish> DISHES = new HashMap<>();

	private Cantrips() {
	}

	static void register() {
		MagicMenu.register();
		ServerTickEvents.END_SERVER_TICK.register(Cantrips::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (UUID id : List.copyOf(LIGHTS.keySet())) {
				put(id, null);
			}
			DISHES.clear();
		});
	}

	/** Whether {@code player} is a caster who knows the everyday spells: a Mage, a Sorcerer, anyone attuned. */
	public static boolean caster(Player player) {
		return player.isCreative() || Mana.attuned(player) || Classes.is(player, Classes.PlayerClass.MAGE) || Classes.is(player, Classes.PlayerClass.SORCERER);
	}

	/** Casts cantrip {@code id} for {@code player}; {@code text} is a whisper's words. */
	static void cast(String id, ServerLevel level, ServerPlayer player, String text) {
		if (Will.hushed(player)) {
			return;
		}
		boolean spoken = id.equals("whisper");
		if (spoken && !SpellParts.canSpeak(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.parts.gagged"));
			return;
		}
		boolean done = switch (id) {
			case "light" -> light(level, player);
			case "mend" -> mend(level, player);
			case "clean" -> clean(level, player);
			case "warm" -> dish(level, player, "warm");
			case "chill" -> dish(level, player, "chill");
			case "whisper" -> whisper(level, player, text);
			case "weather" -> weather(level, player);
			default -> false;
		};
		if (!done) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.cantrip.nothing." + id));
		}
	}

	// ---- light ------------------------------------------------------------------------------------------------------------------

	private static boolean light(ServerLevel level, ServerPlayer player) {
		if (LIGHTS.containsKey(player.getUUID())) {
			put(player.getUUID(), null);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.6F);
			return true;
		}
		LIGHTS.put(player.getUUID(), new Glow(level, player.blockPosition(), level.getGameTime() + LIGHT_TICKS));
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
		return true;
	}

	/** Takes the light block {@code id} placed away, and (if {@code to} is given) sets it at a new place. */
	private static void put(UUID id, @Nullable Glow to) {
		Glow old = LIGHTS.remove(id);
		if (old != null && old.level.getBlockState(old.at).is(Blocks.LIGHT)) {
			old.level.setBlock(old.at, Blocks.AIR.defaultBlockState(), 3);
		}
		if (to != null) {
			LIGHTS.put(id, to);
		}
	}

	private static BlockPos tip(ServerPlayer player) {
		Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(0.9));
		return BlockPos.containing(at);
	}

	private static void tick(MinecraftServer server) {
		if (!LIGHTS.isEmpty()) {
			for (UUID id : List.copyOf(LIGHTS.keySet())) {
				Glow glow = LIGHTS.get(id);
				ServerPlayer player = server.getPlayerList().getPlayer(id);
				if (player == null || !player.isAlive() || player.level() != glow.level || glow.level.getGameTime() >= glow.until) {
					put(id, null);
					continue;
				}
				BlockPos want = tip(player);
				if (!glow.level.getBlockState(want).isAir()) {
					want = player.blockPosition().above();
				}
				if (want.equals(glow.at)) {
					continue;
				}
				if (!glow.level.getBlockState(want).isAir()) {
					continue;
				}
				BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LIGHT_LEVEL);
				put(id, null);
				glow.level.setBlock(want, light, 3);
				LIGHTS.put(id, new Glow(glow.level, want, glow.until));
			}
		}
		if (!DISHES.isEmpty() && server.getTickCount() % 100 == 0) {
			long now = server.overworld().getGameTime();
			DISHES.values().removeIf(d -> d.until <= now);
		}
	}

	// ---- mend -------------------------------------------------------------------------------------------------------------------

	/** Whether a fight is on for {@code player}: they were struck lately, or something hostile has them in its sights. */
	static boolean fighting(ServerPlayer player) {
		if (Approaches.fighting(player)) {
			return true;
		}
		return !player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(16.0), m -> m.isAlive() && m.getTarget() == player).isEmpty();
	}

	private static boolean mend(ServerLevel level, ServerPlayer player) {
		if (fighting(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.cantrip.mend.fight"));
			return true;
		}
		ItemStack stack = player.getMainHandItem();
		if (!stack.isDamaged()) {
			stack = player.getOffhandItem();
		}
		if (!stack.isDamaged()) {
			return false;
		}
		int amount = Math.max(MEND_LEAST, Math.round(stack.getMaxDamage() * MEND_SHARE));
		stack.setDamageValue(Math.max(0, stack.getDamageValue() - amount));
		level.sendParticles(Fx.GLYPH, player.getX(), player.getY(1.0), player.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.3F, 1.8F);
		return true;
	}

	// ---- clean ------------------------------------------------------------------------------------------------------------------

	private static boolean clean(ServerLevel level, ServerPlayer player) {
		PowersCompat.washBlood(player);
		level.sendParticles(ParticleTypes.BUBBLE_POP, player.getX(), player.getY(1.0), player.getZ(), 16, 0.3, 0.5, 0.3, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.PLAYERS, 0.7F, 1.4F);
		return true;
	}

	// ---- warm and chill ---------------------------------------------------------------------------------------------------------

	private static boolean dish(ServerLevel level, ServerPlayer player, String temp) {
		ItemStack stack = player.getMainHandItem().has(DataComponents.FOOD) ? player.getMainHandItem() : player.getOffhandItem();
		if (!stack.has(DataComponents.FOOD)) {
			return false;
		}
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(DISH_KEY, temp));
		level.sendParticles(temp.equals("warm") ? ParticleTypes.CAMPFIRE_COSY_SMOKE : ParticleTypes.SNOWFLAKE, player.getX(), player.getY(1.1), player.getZ(), 8, 0.2, 0.2, 0.2, 0.01);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), temp.equals("warm") ? SoundEvents.FIRECHARGE_USE : SoundEvents.POWDER_SNOW_PLACE,
			SoundSource.PLAYERS, 0.5F, 1.5F);
		return true;
	}

	/** The temperature a dish was given ({@code warm}, {@code chill}), or null. */
	public static @Nullable String dishTemp(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? null : data.copyTag().getString(DISH_KEY).orElse(null);
	}

	/** A dish eaten by {@code who}: a warmed one warms the body for a while, a chilled one cools it. */
	public static void eaten(LivingEntity who, ItemStack dish) {
		if (!(who instanceof ServerPlayer player)) {
			return;
		}
		String temp = dishTemp(dish);
		if (temp != null) {
			DISHES.put(player.getUUID(), new Dish(temp.equals("warm") ? DISH_HEAT : -DISH_HEAT, player.level().getGameTime() + DISH_TICKS));
		}
	}

	/** Kitchen's body heat asks: how much a dish warmed or chilled adds to {@code player} just now. */
	public static double bodyHeat(ServerPlayer player) {
		Dish d = DISHES.get(player.getUUID());
		return d == null || d.until <= player.level().getGameTime() ? 0.0 : d.heat;
	}

	// ---- whisper ----------------------------------------------------------------------------------------------------------------

	private static boolean whisper(ServerLevel level, ServerPlayer player, String text) {
		String words = text == null ? "" : text.strip();
		if (words.length() > MagicMenu.WHISPER_MAX) {
			words = words.substring(0, MagicMenu.WHISPER_MAX);
		}
		LivingEntity looked = Arcana.sighted(player);
		if (words.isEmpty() || !(looked instanceof ServerPlayer to) || to.distanceTo(player) > WHISPER_REACH) {
			return false;
		}
		Component said = Component.literal(words);
		Speech.privately(player, to, said, WHISPER_COLOUR);
		Speech.privately(player, player, said, WHISPER_COLOUR);
		level.sendParticles(to, ParticleTypes.ENCHANT, false, false, to.getX(), to.getY(1.9), to.getZ(), 8, 0.2, 0.2, 0.2, 0.3);
		level.playSound(null, to.getX(), to.getY(), to.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.15F, 2.0F);
		return true;
	}

	// ---- weather ----------------------------------------------------------------------------------------------------------------

	private static boolean weather(ServerLevel level, ServerPlayer player) {
		WeatherData w = level.getWeatherData();
		String key;
		if (w.isRaining()) {
			key = w.isThundering() ? "storm" : w.getRainTime() < DAY ? "rain_ends" : "rain_stays";
		} else {
			int until = w.getClearWeatherTime() > 0 ? w.getClearWeatherTime() : w.getRainTime();
			key = until < DAY ? "rain_comes" : "fair";
		}
		Speech.privately(player, player, Component.translatable("speech.jaymoskingdoms.weather." + key), WEATHER_COLOUR);
		for (Component line : com.jaymoskingdoms.disasters.Calamities.foretold(level, player.blockPosition()).stream().limit(2).toList()) {
			Speech.privately(player, player, line, WEATHER_COLOUR);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundSource.PLAYERS, 0.3F, 1.7F);
		return true;
	}
}
