package com.jaymospowers.relics;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.baubles.Baubles;
import com.jaymospowers.compat.KingdomsCompat;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import org.jspecify.annotations.Nullable;

/**
 * (Awakening pick) The relic belt: a belt-slot bauble with {@link #POUCHES} pouches for small relics, each a little more
 * health, stamina, focus or the like, some working only while you are hale, at the brink, in water or sprinting. Two of
 * the same relic at the same level merge into one a level up (click one onto the other), to {@link #MOST_LEVEL}. Every
 * boss leaves its own relic when it falls (the same kind each time for the same boss).
 * <p>Lore: a relic is what a great creature's death leaves hardened behind, a knot of the strength that kept it up; worn
 * close, a little of that strength goes into the wearer.
 */
public final class Relics {
	// ---- tuning ----
	/** Pouches on a belt, and a relic's highest level. */
	public static final int POUCHES = 4, MOST_LEVEL = 3;
	/** "Hale" is at least this share of full health; "at the brink" at most this share. */
	static final float HALE_SHARE = 0.8F, BRINK_SHARE = 0.3F;
	/** How often the worn relics are looked at again (ticks). */
	static final int CHECK_TICKS = 10;
	/** The chance a fallen boss leaves its relic. */
	static final float BOSS_DROP = 1.0F;
	/** The focus relic: this share more harm from your power's moves per level (with Ability Power). */
	static final double FOCUS_PER_LEVEL = 0.05;
	/** The wind knot: golden feathers (half-feathers) per level. */
	static final int WIND_PER_LEVEL = 2;

	/** When a relic works. */
	public enum When {
		ALWAYS, HALE, BRINK, WATER, SPRINTING;

		boolean holds(Player player) {
			float share = player.getHealth() / Math.max(1.0F, player.getMaxHealth());
			return switch (this) {
				case ALWAYS -> true;
				case HALE -> share >= HALE_SHARE;
				case BRINK -> share <= BRINK_SHARE;
				case WATER -> player.isInWater();
				case SPRINTING -> player.isSprinting();
			};
		}
	}

	/** The relics: what each gives a level, and when. */
	public enum Kind {
		HEARTSEED(When.ALWAYS, Attributes.MAX_HEALTH, 2.0, AttributeModifier.Operation.ADD_VALUE, 0xC8323C),
		WIND_KNOT(When.ALWAYS, null, 0.0, null, 0xC8E6F0),
		FOCUS_STONE(When.ALWAYS, null, 0.0, null, 0x9A6AE0),
		HALE_COIN(When.HALE, Attributes.ATTACK_DAMAGE, 1.0, AttributeModifier.Operation.ADD_VALUE, 0xE8C040),
		LAST_EMBER(When.BRINK, Attributes.ARMOR, 3.0, AttributeModifier.Operation.ADD_VALUE, 0xF06A20),
		TIDE_SCALE(When.WATER, Attributes.WATER_MOVEMENT_EFFICIENCY, 0.2, AttributeModifier.Operation.ADD_VALUE, 0x3AA0B8),
		STRIDE_SPUR(When.SPRINTING, Attributes.MOVEMENT_SPEED, 0.05, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, 0xA0A8B0);

		public final When when;
		final @Nullable Holder<Attribute> attribute;
		final double perLevel;
		final AttributeModifier.@Nullable Operation operation;
		public final int color;
		Item item;

		Kind(When when, @Nullable Holder<Attribute> attribute, double perLevel, AttributeModifier.@Nullable Operation operation, int color) {
			this.when = when;
			this.attribute = attribute;
			this.perLevel = perLevel;
			this.operation = operation;
			this.color = color;
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		public Item item() {
			return this.item;
		}

		Identifier modifierId() {
			return JaymosPowers.id("relic_" + key());
		}
	}

	/** A relic's level (1 to {@link #MOST_LEVEL}). */
	public static DataComponentType<Integer> LEVEL;
	/** What a belt's pouches hold: each relic as {@code kind * 10 + level}. */
	public static DataComponentType<List<Integer>> POUCHED;
	public static Item RELIC_BELT;

	private Relics() {
	}

	public static void init() {
		LEVEL = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("relic_level"),
			DataComponentType.<Integer>builder().persistent(Codec.intRange(1, MOST_LEVEL)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
		POUCHED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("relic_pouches"),
			DataComponentType.<List<Integer>>builder().persistent(Codec.INT.listOf()).networkSynchronized(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(POUCHES)))
				.build());
		for (Kind kind : Kind.values()) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("relic_" + kind.key()));
			kind.item = Registry.register(BuiltInRegistries.ITEM, key, new RelicItem(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.RARE)
				.component(LEVEL, 1), kind));
		}
		ResourceKey<Item> beltKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("relic_belt"));
		RELIC_BELT = Registry.register(BuiltInRegistries.ITEM, beltKey, new RelicBeltItem(new Item.Properties().setId(beltKey).stacksTo(1)
			.rarity(Rarity.UNCOMMON)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(RELIC_BELT);
			for (Kind kind : Kind.values()) {
				output.accept(kind.item);
			}
		});
		com.jaymospowers.stamina.Feathers.addBonus(player -> levels(player).getOrDefault(Kind.WIND_KNOT, 0) * WIND_PER_LEVEL);
		ServerTickEvents.END_SERVER_TICK.register(Relics::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.level() instanceof ServerLevel level && boss(entity) && level.getRandom().nextFloat() < BOSS_DROP) {
				entity.spawnAtLocation(level, relic(bossRelic(entity.getType()), 1));
			}
		});
	}

	/** A relic of {@code kind} at {@code level}. */
	public static ItemStack relic(Kind kind, int level) {
		ItemStack stack = new ItemStack(kind.item);
		stack.set(LEVEL, Math.clamp(level, 1, MOST_LEVEL));
		return stack;
	}

	public static int level(ItemStack stack) {
		return stack.getOrDefault(LEVEL, 1);
	}

	static int pack(Kind kind, int level) {
		return kind.ordinal() * 10 + level;
	}

	static @Nullable Kind kindOf(int packed) {
		int k = packed / 10;
		return k >= 0 && k < Kind.values().length ? Kind.values()[k] : null;
	}

	static int levelOf(int packed) {
		return Math.clamp(packed % 10, 1, MOST_LEVEL);
	}

	/** The relics in a belt, packed. */
	public static List<Integer> pouched(ItemStack belt) {
		return belt.getOrDefault(POUCHED, List.of());
	}

	/** The levels of each relic in {@code player}'s worn belt that work right now. */
	static Map<Kind, Integer> levels(Player player) {
		Map<Kind, Integer> out = new EnumMap<>(Kind.class);
		ItemStack belt = Baubles.beltSlot(player);
		if (!belt.is(RELIC_BELT)) {
			return out;
		}
		for (int packed : pouched(belt)) {
			Kind kind = kindOf(packed);
			if (kind != null && kind.when.holds(player)) {
				out.merge(kind, levelOf(packed), Integer::sum);
			}
		}
		return out;
	}

	/** What a relic of {@code kind} at {@code level} gives, as the tooltip shows it (shares as percents, feathers whole). */
	static String amount(Kind kind, int level) {
		double v = switch (kind) {
			case WIND_KNOT -> level * WIND_PER_LEVEL / 2.0;
			case FOCUS_STONE -> level * FOCUS_PER_LEVEL * 100.0;
			case TIDE_SCALE, STRIDE_SPUR -> level * kind.perLevel * 100.0;
			default -> level * kind.perLevel;
		};
		return v == Math.rint(v) ? Long.toString(Math.round(v)) : Double.toString(Math.round(v * 10.0) / 10.0);
	}

	/** The focus relics' share more harm from the wearer's power ({@code core.AbilityPower}). */
	public static double focus(Player player) {
		return levels(player).getOrDefault(Kind.FOCUS_STONE, 0) * FOCUS_PER_LEVEL;
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % CHECK_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Map<Kind, Integer> levels = levels(player);
			for (Kind kind : Kind.values()) {
				if (kind.attribute == null) {
					continue;
				}
				AttributeInstance instance = player.getAttribute(kind.attribute);
				if (instance == null) {
					continue;
				}
				int level = levels.getOrDefault(kind, 0);
				AttributeModifier had = instance.getModifier(kind.modifierId());
				double amount = level * kind.perLevel;
				if (had != null && had.amount() == amount) {
					continue;
				}
				instance.removeModifier(kind.modifierId());
				if (level > 0) {
					instance.addTransientModifier(new AttributeModifier(kind.modifierId(), amount, kind.operation));
				}
				if (kind.attribute == Attributes.MAX_HEALTH && player.getHealth() > player.getMaxHealth()) {
					player.setHealth(player.getMaxHealth());
				}
			}
		}
	}

	/** A boss: Kingdoms' boss-bar mobs and the vanilla four. */
	static boolean boss(LivingEntity entity) {
		EntityType<?> type = entity.getType();
		return type == EntityTypes.WITHER || type == EntityTypes.ENDER_DRAGON || type == EntityTypes.WARDEN || type == EntityTypes.ELDER_GUARDIAN
			|| KingdomsCompat.boss(entity);
	}

	/** The relic a boss of {@code type} leaves: the vanilla four their own, every other boss the same one each time. */
	static Kind bossRelic(EntityType<?> type) {
		if (type == EntityTypes.WITHER) {
			return Kind.LAST_EMBER;
		}
		if (type == EntityTypes.ENDER_DRAGON) {
			return Kind.FOCUS_STONE;
		}
		if (type == EntityTypes.WARDEN) {
			return Kind.HEARTSEED;
		}
		if (type == EntityTypes.ELDER_GUARDIAN) {
			return Kind.TIDE_SCALE;
		}
		List<Kind> all = new ArrayList<>(List.of(Kind.values()));
		return all.get(Math.floorMod(BuiltInRegistries.ENTITY_TYPE.getKey(type).hashCode(), all.size()));
	}
}
