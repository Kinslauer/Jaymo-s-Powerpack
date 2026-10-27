package com.jaymoskingdoms;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.ClothingList;
import net.conczin.mca.resources.WeightedPool;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.177) Andy (2026-10-23): "anything that doesnt match the medieval theme like this should be removed from the pool"
 * (a villager baker in a sprinkled cake jumper and pink trousers, one of MCA's own clothes).
 * <p>
 * Clothes on the ban list are never picked for a villager, whether MCA's own ({@link #BUILT_IN}: hoodies, jeans, suits,
 * creeper shirts, swimwear...) or the clothing library's. A villager left with too few of their job's clothes (most
 * children were in modern ones) is dressed from the jobless clothes too ({@link #MIN_OPTIONS}). Anyone already wearing
 * banned clothes is dressed again when they load, unless their clothes are locked in MCA's editor.
 * <p>
 * Operators add to the list in game: {@code /kingdom clothes ban} bans what the villager you're looking at wears (and
 * re-dresses everyone in it), {@code unban <id>} takes it off (MCA's own too), {@code reroll} just dresses them again.
 * The list is kept in {@code config/jaymoskingdoms/clothing_bans.json} ({@code banned} added, {@code allowed} let back).
 */
public final class Wardrobe {
	/** Fewer of their own clothes than this left after the bans, and a villager may wear the jobless ones too. */
	static final int MIN_OPTIONS = 3;
	/** How far off {@code /kingdom clothes} finds the villager you look at (blocks). */
	static final double REACH = 8.0;

	/** MCA's own clothes that don't fit the medieval look (by gender folder: job/number). */
	static final Set<String> BUILT_IN = new LinkedHashSet<>();
	static {
		ban("female", "armorer/0 baker/3 baker/4 butcher/0 butcher/1 butcher/2 butcher/3 butcher/4 butcher/5 cartographer/0 cartographer/1"
			+ " cartographer/2 cartographer/3 cartographer/4 child/0 child/1 child/2 child/4 child/5 child/6 child/7 child/8 child/9 farmer/0"
			+ " farmer/1 farmer/2 farmer/3 farmer/4 fisherman/0 fisherman/1 fisherman/2 fisherman/3 fisherman/4 fletcher/0 fletcher/1 fletcher/2"
			+ " fletcher/3 fletcher/4 guard/0 leatherworker/0 leatherworker/1 leatherworker/3 leatherworker/4 librarian/0 librarian/1 librarian/3"
			+ " librarian/4 mason/0 mason/1 mason/2 mason/3 mason/4 miner/0 miner/2 miner/3 miner/4 none/0 none/1 none/2 none/3 none/4 none/5"
			+ " none/7 none/8 none/9 shepherd/1 shepherd/3 toolsmith/0 toolsmith/1 toolsmith/3 toolsmith/4");
		ban("male", "armorer/1 armorer/2 baker/0 baker/1 baker/2 baker/3 butcher/2 butcher/3 butcher/4 cartographer/0 cartographer/2 child/0"
			+ " child/1 child/10 child/11 child/2 child/3 child/4 child/5 child/6 child/7 child/8 child/9 farmer/0 farmer/2 farmer/3 farmer/4"
			+ " fisherman/1 fisherman/2 fisherman/3 fisherman/4 fletcher/1 fletcher/4 leatherworker/0 leatherworker/2 librarian/1 librarian/2"
			+ " librarian/3 librarian/4 mason/2 mason/3 mason/4 miner/1 miner/3 none/0 none/1 none/2 none/3 none/4 none/5 none/7 none/8 none/9"
			+ " shepherd/2 toolsmith/0 toolsmith/1 toolsmith/2 toolsmith/3 toolsmith/4 weaponsmith/0 weaponsmith/2 weaponsmith/3 weaponsmith/4");
	}
	/** Library clothes with any of these tags are left out. */
	static final Set<String> OFF_THEME_TAGS = Set.of("modern", "casual", "contemporary", "hoodie", "jeans", "denim", "suit", "tuxedo", "tie",
		"shirt", "t-shirt", "tshirt", "jacket", "sweater", "sweatshirt", "sneakers", "shorts", "swimsuit", "bikini", "swimwear", "school",
		"schoolgirl", "streetwear", "sport", "sports", "jersey", "gamer", "creeper", "scifi", "sci-fi", "futuristic", "cyberpunk", "space",
		"robot", "police", "army", "military", "camo", "anime", "kawaii", "emo", "punk", "goth", "e-girl", "egirl", "rapper", "pajamas",
		"onesie", "hospital", "nurse", "doctor", "office", "business", "chef", "pirate");

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	/** Banned in game (or by hand in the file), and MCA's own let back. */
	private static volatile Set<String> banned = Set.of(), allowed = Set.of();

	private Wardrobe() {
	}

	private static void ban(String gender, String clothes) {
		for (String c : clothes.split(" ")) {
			BUILT_IN.add("mca:skins/clothing/normal/" + gender + "/" + c + ".png");
		}
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> load());
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof VillagerEntityMCA v) {
				undress(v);
			}
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("clothes").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("ban").executes(Wardrobe::banCommand))
				.then(Commands.literal("reroll").executes(Wardrobe::rerollCommand))
				.then(Commands.literal("unban").then(Commands.argument("id", StringArgumentType.greedyString())
					.suggests((c, b) -> SharedSuggestionProvider.suggest(bans(), b)).executes(Wardrobe::unbanCommand))))));
	}

	// ---- the list -------------------------------------------------------------------------------------------------------

	/** {@code clothes} (an MCA clothing id, or a library one) may not be worn by villagers. */
	public static boolean banned(@Nullable String clothes) {
		return clothes != null && (banned.contains(clothes) || BUILT_IN.contains(clothes) && !allowed.contains(clothes)
			// (0.7.272) the clothing library is off: none of its clothes
			|| !ClothingLibrary.ENABLED && clothes.startsWith("immersive_library:"));
	}

	/** A library item tagged as off-theme (modern, hoodie...). */
	static boolean offTheme(Set<String> tags) {
		return tags.stream().anyMatch(OFF_THEME_TAGS::contains);
	}

	private static Set<String> bans() {
		Set<String> all = new LinkedHashSet<>(banned);
		BUILT_IN.stream().filter(c -> !allowed.contains(c)).forEach(all::add);
		return all;
	}

	/**
	 * MCA's pick of clothes for {@code villager} without the banned ones, topped up with the jobless clothes when too few
	 * are left; MCA's own pool if nothing at all is left.
	 */
	public static WeightedPool<String> filter(WeightedPool<String> pool, VillagerEntityMCA villager) {
		WeightedPool.Mutable<String> kept = new WeightedPool.Mutable<>("");
		int left = keep(pool, kept, Set.of());
		if (left < MIN_OPTIONS) {
			Gender gender = villager.getGenetics().getGender();
			Set<String> had = new LinkedHashSet<>();
			pool.getEntries().forEach(e -> had.add(e.getValue()));
			left += keep(ClothingList.getInstance().getPool(gender, "minecraft:none"), kept, had);
		}
		if (left == 0) {
			return pool;
		}
		WeightedPool.Mutable<String> out = new WeightedPool.Mutable<>(kept.getEntries().get(0).getValue());
		kept.getEntries().forEach(e -> out.add(e.getValue(), e.getWeight()));
		return out;
	}

	private static int keep(WeightedPool<String> from, WeightedPool.Mutable<String> into, Set<String> skip) {
		int n = 0;
		for (WeightedPool.Entry<String> e : from.getEntries()) {
			if (!banned(e.getValue()) && !skip.contains(e.getValue())) {
				into.add(e.getValue(), e.getWeight());
				n++;
			}
		}
		return n;
	}

	/** {@code villager}, if in banned or off-theme clothes (and not locked in them), is dressed again. True if they were. */
	static boolean undress(VillagerEntityMCA villager) {
		String clothes = villager.getClothes();
		if (villager.level().isClientSide() || villager.isClothingLocked() || !banned(clothes) && !ClothingLibrary.offTheme(clothes)) {
			return false;
		}
		villager.forceRandomizeClothes();
		return true;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("jaymoskingdoms").resolve("clothing_bans.json");
	}

	private static void load() {
		Set<String> b = new LinkedHashSet<>(), a = new LinkedHashSet<>();
		try {
			if (Files.exists(file())) {
				JsonObject saved = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
				read(saved, "banned", b);
				read(saved, "allowed", a);
			}
		} catch (IOException | RuntimeException e) {
			JaymosKingdoms.LOGGER.warn("Couldn't read the clothing ban list: {}", e.toString());
		}
		banned = Set.copyOf(b);
		allowed = Set.copyOf(a);
	}

	private static void read(JsonObject saved, String key, Set<String> into) {
		if (saved.has(key) && saved.get(key).isJsonArray()) {
			for (JsonElement e : saved.getAsJsonArray(key)) {
				into.add(e.getAsString());
			}
		}
	}

	private static void save() {
		try {
			JsonObject out = new JsonObject();
			JsonArray b = new JsonArray(), a = new JsonArray();
			banned.forEach(b::add);
			allowed.forEach(a::add);
			out.add("banned", b);
			out.add("allowed", a);
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(out));
		} catch (IOException e) {
			JaymosKingdoms.LOGGER.warn("Couldn't save the clothing ban list: {}", e.toString());
		}
	}

	// ---- commands -------------------------------------------------------------------------------------------------------

	/** The MCA villager {@code player} is looking at, within {@link #REACH}. */
	private static @Nullable VillagerEntityMCA lookedAt(ServerPlayer player) {
		Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
		VillagerEntityMCA best = null;
		double bestDot = 0.95;
		for (VillagerEntityMCA v : player.level().getEntitiesOfClass(VillagerEntityMCA.class, player.getBoundingBox().inflate(REACH))) {
			Vec3 to = v.getBoundingBox().getCenter().subtract(eye);
			if (to.length() > REACH) {
				continue;
			}
			double dot = to.normalize().dot(look);
			if (dot > bestDot && player.hasLineOfSight(v)) {
				best = v;
				bestDot = dot;
			}
		}
		return best;
	}

	private static int banCommand(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		VillagerEntityMCA v = lookedAt(c.getSource().getPlayerOrException());
		if (v == null) {
			c.getSource().sendFailure(Component.literal("Look at a villager first"));
			return 0;
		}
		String clothes = v.getClothes();
		if (clothes == null || clothes.isBlank()) {
			c.getSource().sendFailure(Component.literal("They aren't wearing anything MCA knows"));
			return 0;
		}
		Set<String> b = new LinkedHashSet<>(banned), a = new LinkedHashSet<>(allowed);
		b.add(clothes);
		a.remove(clothes);
		banned = Set.copyOf(b);
		allowed = Set.copyOf(a);
		save();
		boolean locked = v.isClothingLocked();
		if (locked) {
			v.forceRandomizeClothes();
		}
		int redressed = redressAll(c.getSource().getServer()) + (locked ? 1 : 0);
		c.getSource().sendSuccess(() -> Component.literal("Banned " + clothes + "; " + redressed + " villager(s) dressed again"), true);
		return redressed;
	}

	private static int unbanCommand(CommandContext<CommandSourceStack> c) {
		String clothes = StringArgumentType.getString(c, "id").strip();
		Set<String> b = new LinkedHashSet<>(banned), a = new LinkedHashSet<>(allowed);
		boolean was = b.remove(clothes);
		if (BUILT_IN.contains(clothes)) {
			was |= a.add(clothes);
		}
		if (!was) {
			c.getSource().sendFailure(Component.literal(clothes + " isn't banned"));
			return 0;
		}
		banned = Set.copyOf(b);
		allowed = Set.copyOf(a);
		save();
		c.getSource().sendSuccess(() -> Component.literal("Villagers may wear " + clothes + " again"), true);
		return 1;
	}

	private static int rerollCommand(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		VillagerEntityMCA v = lookedAt(c.getSource().getPlayerOrException());
		if (v == null) {
			c.getSource().sendFailure(Component.literal("Look at a villager first"));
			return 0;
		}
		v.forceRandomizeClothes();
		c.getSource().sendSuccess(() -> Component.literal("Dressed " + v.getName().getString() + " again (" + v.getClothes() + ")"), false);
		return 1;
	}

	/** Everyone about in banned clothes is dressed again; how many were. */
	private static int redressAll(MinecraftServer server) {
		int n = 0;
		for (ServerLevel level : server.getAllLevels()) {
			for (Entity e : level.getAllEntities()) {
				if (e instanceof VillagerEntityMCA v && undress(v)) {
					n++;
				}
			}
		}
		return n;
	}
}
