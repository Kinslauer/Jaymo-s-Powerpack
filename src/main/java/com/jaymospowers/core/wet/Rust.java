package com.jaymospowers.core.wet;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.ArmourKind;
import com.mojang.serialization.Codec;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Andy: "like leather armor is affected metal armors should rust if left wet or to soak too long." Iron and steel armour
 * (mail and plate, {@link ArmourKind}) carries its own rust ({@code 0..}{@link #MAX}), which grows while it's wet: worn
 * by someone {@link Wet} or soaked, or lying in water or the rain. Sea water rusts twice as fast (the Deep Mother rusts
 * what the Anvil-Father makes). Gold, copper, bronze, netherite and gems don't rust ({@link #rusts}).
 * <p>
 * Rust shows on the piece and its icon as orange-brown pitting, creases and lower edges first (the client's
 * {@code ArmourGrimeTextures}), and is named on the tooltip. Past {@link #EATS} it eats the metal: each check it grows
 * it may take a point of durability (likelier the worse it is), never the last one, as soaked leather wears.
 * <p>
 * Andy: "nothing removes rust". It stays on the piece for good, on players' and folk's armour alike: no wash, scrub or
 * repair takes it off.
 */
public final class Rust {
	// ---- tuning ----
	public static final int MAX = 100;
	/** How often rust grows (ticks), and how far from a player (blocks). */
	static final int CHECK = 200;
	static final double RANGE = 48.0;
	/** Rust a check: in water (or an item lying in it, or in the rain), worn soaked, worn wet; sea water times this. */
	static final float IN_WATER = 1.0F, SOAKED = 0.6F, WET = 0.25F, SEA = 2.0F;
	/** From this much rust it eats the metal: a point of durability a check it grows, at a chance of rust / MAX. */
	public static final int EATS = 30;
	/** Rust worth a word on the tooltip, and the worse grades. */
	public static final int SPOTTED = 10, RUSTY = 35, CORRODED = 70;
	/** Armour that never rusts, by tag, and by words in its id. */
	public static final TagKey<Item> RUSTPROOF = TagKey.create(Registries.ITEM, JaymosPowers.id("rustproof"));
	static final String[] RUSTPROOF_WORDS = {"gold", "gilded", "copper", "bronze", "brass", "netherite", "diamond", "emerald", "amethyst",
		"crystal", "obsidian", "silver", "mithril", "dragon", "bone", "turtle", "scute", "chitin", "leather", "wood"};

	public static DataComponentType<Integer> RUST;

	private Rust() {
	}

	public static void init() {
		RUST = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("rust"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
		ServerTickEvents.END_SERVER_TICK.register(Rust::tick);
	}

	/** How rusty {@code stack} is (0: none). */
	public static int rust(ItemStack stack) {
		if (RUST == null || stack.isEmpty()) {
			return 0;
		}
		Integer r = stack.get(RUST);
		return r == null ? 0 : r;
	}

	/** Whether {@code stack} is armour that rusts: mail or plate, not of a metal (or anything) that doesn't. */
	public static boolean rusts(ItemStack stack) {
		ArmourKind kind = ArmourKind.of(stack);
		if (kind != ArmourKind.MAIL && kind != ArmourKind.PLATE || stack.is(RUSTPROOF)) {
			return false;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().toLowerCase(Locale.ROOT);
		for (String word : RUSTPROOF_WORDS) {
			if (path.contains(word)) {
				return false;
			}
		}
		return true;
	}

	private static void set(ItemStack stack, int rust) {
		stack.set(RUST, Math.min(MAX, rust));
	}

	// ---- every check ----

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Set<Entity> seen = new HashSet<>();
			for (ServerPlayer player : level.players()) {
				AABB around = new AABB(player.blockPosition()).inflate(RANGE);
				for (LivingEntity body : level.getEntitiesOfClass(LivingEntity.class, around, LivingEntity::isAlive)) {
					if (seen.add(body)) {
						weather(level, body);
					}
				}
				for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, around, e -> e.isInWaterOrRain() && rusts(e.getItem()))) {
					if (seen.add(item)) {
						grow(item.getItem(), IN_WATER * sea(level, item), level.getRandom());
					}
				}
			}
		}
	}

	private static float sea(ServerLevel level, Entity at) {
		return at.isInWater() && (level.getBiome(at.blockPosition()).is(BiomeTags.IS_OCEAN) || level.getBiome(at.blockPosition()).is(BiomeTags.IS_BEACH))
			? SEA : 1.0F;
	}

	private static void weather(ServerLevel level, LivingEntity body) {
		float rate = body.isInWater() ? IN_WATER * sea(level, body) : Wet.soaked(body) ? SOAKED : Wet.wet(body) ? WET : 0.0F;
		if (rate <= 0.0F || body instanceof Player p && p.isCreative()) {
			return;
		}
		for (EquipmentSlot slot : ArmourGrime.SLOTS) {
			ItemStack piece = body.getItemBySlot(slot);
			if (rusts(piece)) {
				grow(piece, rate, level.getRandom());
			}
		}
	}

	/** {@code stack} rusts by {@code rate} (fractions by chance), and past {@link #EATS} the rust may eat a point of it. */
	static void grow(ItemStack stack, float rate, RandomSource random) {
		int add = Mth.floor(rate) + (random.nextFloat() < rate - Mth.floor(rate) ? 1 : 0);
		if (add <= 0) {
			return;
		}
		int rust = Math.min(MAX, rust(stack) + add);
		set(stack, rust);
		if (rust >= EATS && stack.isDamageableItem() && stack.getDamageValue() < stack.getMaxDamage() - 1 && random.nextInt(MAX) < rust) {
			stack.setDamageValue(stack.getDamageValue() + 1);
		}
	}
}
