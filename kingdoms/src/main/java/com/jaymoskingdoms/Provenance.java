package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractFurnaceMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Every item remembers where it came from (Andy's pick from Chest DNA, 2026-11-24). Chest DNA (by xsoras) is under the
 * Apache License 2.0; this is our own code after its design, nothing of it copied: an item is stamped once, when it first
 * comes into the world, with how (a chest, a kill, the pick, a hook, a brush, a bench or a trade), where (dimension,
 * place, biome, the structure it lay in), the day and who found or made it. A stamp is never written over, and it is
 * ignored when stacks are compared, so stamped and unstamped items stack as before; the stack kept is the first one's,
 * with its stamp ({@code mixin/provenance/ItemStackOriginMixin}). The tooltip shows one small line, and the rest on Shift
 * ({@code client/ProvenanceTooltips}).
 *
 * <p>Some places are famous: things from a dragon's hoard ({@link GreatLairs.Kind#DEN}), a sunken ship (vanilla wrecks,
 * buried treasure and our {@link TreasureWrecks}) and the old cities (our {@code old_ruins}, ancient cities, trail
 * ruins) fetch more from a merchant ({@link #famousBonus}). Coins are never stamped: one coin is as good as another.
 */
public final class Provenance {
	/** How things come into the world (the lang keys' last part). */
	public static final String CHEST = "chest", MOB = "mob", BOSS = "boss", MINED = "mined", FISHED = "fished", DUG = "dug",
		CRAFTED = "crafted", SMELTED = "smelted", SMITHED = "smithed", BOUGHT = "bought", GIFT = "gift", BARTERED = "bartered",
		SHEARED = "sheared", LOOT = "loot", SHIPWRECK = "shipwreck", DRAGON_HOARD = "dragon_hoard", OLD_CITY = "old_city";
	/** The finds collectors pay more for. */
	public static final Set<String> FAMOUS = Set.of(SHIPWRECK, DRAGON_HOARD, OLD_CITY);
	/** What a famous find fetches over its price, when sold to a merchant for coin (a share of the coin paid). */
	static final float FAMOUS_BONUS = 0.5F;
	/** Vanilla structures that are old cities, and sunken ships. */
	static final Set<String> OLD_CITY_STRUCTURES = Set.of("minecraft:ancient_city", "minecraft:trail_ruins");
	static final String WRECK_STRUCTURE = "minecraft:shipwreck", TREASURE_STRUCTURE = "minecraft:buried_treasure";

	/**
	 * Where an item came from: how ({@link #CHEST} and the rest), what (a loot table, a mob, a block, a merchant's name),
	 * the dimension, the place, the biome, the structure it lay in, the day and who found or made it (their MCA name).
	 * Text fields are empty where nothing is known.
	 */
	public record Origin(String source, String detail, String dimension, Optional<BlockPos> pos, String biome, String structure, long day, String finder) {
		public static final Codec<Origin> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("source").forGetter(Origin::source),
			Codec.STRING.optionalFieldOf("detail", "").forGetter(Origin::detail),
			Codec.STRING.optionalFieldOf("dimension", "").forGetter(Origin::dimension),
			BlockPos.CODEC.optionalFieldOf("pos").forGetter(Origin::pos),
			Codec.STRING.optionalFieldOf("biome", "").forGetter(Origin::biome),
			Codec.STRING.optionalFieldOf("structure", "").forGetter(Origin::structure),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(Origin::day),
			Codec.STRING.optionalFieldOf("finder", "").forGetter(Origin::finder)
		).apply(i, Origin::new));
		public static final StreamCodec<io.netty.buffer.ByteBuf, Origin> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

		public boolean famous() {
			return FAMOUS.contains(this.source);
		}
	}

	public static DataComponentType<Origin> ORIGIN;

	private Provenance() {
	}

	static void register() {
		ORIGIN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("provenance"),
			DataComponentType.<Origin>builder().persistent(Origin.CODEC).networkSynchronized(Origin.STREAM_CODEC).build());
		LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
			Identifier id = table.unwrapKey().map(k -> k.identifier()).orElse(null);
			Vec3 at = context.getOptional(LootContextParams.ORIGIN);
			if (id == null || drops.isEmpty()) {
				return;
			}
			String path = id.getPath();
			if (path.startsWith("equipment/") || path.startsWith("spawners/") && !path.contains("reward")) {
				// (what a mob is given to wear when it spawns isn't found yet)
				return;
			}
			Entity entity = context.getOptional(LootContextParams.THIS_ENTITY);
			Player finder = entity instanceof Player p ? p : context.getOptional(LootContextParams.LAST_DAMAGE_PLAYER);
			String source;
			String detail = id.toString();
			if (path.startsWith("entities/")) {
				if (entity == null || entity instanceof Player) {
					return;
				}
				source = BossBars.boss(entity) || Legendary.boss(entity) ? BOSS : MOB;
				detail = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
				if (at == null) {
					at = entity.position();
				}
			} else if (path.startsWith("blocks/")) {
				source = MINED;
				detail = context.getOptional(LootContextParams.BLOCK_STATE) instanceof net.minecraft.world.level.block.state.BlockState s
					? BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString() : "";
			} else if (path.startsWith("chests/")) {
				source = path.contains("shipwreck") || path.contains("buried_treasure") ? SHIPWRECK
					: path.startsWith("chests/old_age/") || path.contains("ancient_city") ? OLD_CITY : CHEST;
			} else if (path.startsWith("archaeology/")) {
				source = path.contains("trail_ruins") ? OLD_CITY : DUG;
			} else if (path.startsWith("gameplay/fishing")) {
				source = FISHED;
			} else if (path.startsWith("gameplay/piglin_bartering")) {
				source = BARTERED;
			} else if (path.startsWith("gameplay/hero_of_the_village") || path.contains("gift")) {
				source = GIFT;
			} else if (path.startsWith("shearing/")) {
				source = SHEARED;
			} else {
				source = LOOT;
			}
			ServerLevel level = context.getLevel();
			Origin o = at(level, at == null ? null : BlockPos.containing(at), source, detail, finder);
			for (ItemStack stack : drops) {
				stamp(stack, o);
			}
		});
	}

	// ---- stamping ---------------------------------------------------------------------------------------------------

	/** Stamps {@code stack} with {@code o}, unless it already has a stamp, or is money. */
	public static void stamp(ItemStack stack, Origin o) {
		if (ORIGIN != null && !stack.isEmpty() && !stack.has(ORIGIN) && !Currencies.isMoney(stack)) {
			stack.set(ORIGIN, o);
		}
	}

	/**
	 * An origin for something come by at {@code pos} in {@code level} (null: nowhere in particular): its biome and the
	 * structure it lay in are read there, and a find in a famous place is that place's ({@link #famous}).
	 */
	public static Origin at(ServerLevel level, @Nullable BlockPos pos, String source, String detail, @Nullable Player finder) {
		String biome = "", structure = "";
		boolean oldCity = false, wreck = false;
		if (pos != null && level.isLoaded(pos)) {
			biome = level.getBiome(pos).unwrapKey().map(k -> k.identifier().toString()).orElse("");
			StructureStart start = level.structureManager().getStructureWithPieceAt(pos, h -> true);
			if (start.isValid()) {
				Registry<Structure> structures = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
				Identifier key = structures.getKey(start.getStructure());
				Holder<Structure> holder = structures.wrapAsHolder(start.getStructure());
				structure = key == null ? "" : key.toString();
				oldCity = holder.is(com.jaymoskingdoms.oldage.OldRuins.RUINS) || OLD_CITY_STRUCTURES.contains(structure);
				wreck = structure.startsWith(WRECK_STRUCTURE) || structure.equals(TREASURE_STRUCTURE);
			}
		}
		String how = source;
		if (pos != null && (CHEST.equals(source) || DUG.equals(source) || LOOT.equals(source))) {
			GreatLairs.Lair lair = GreatLairs.at(level, pos);
			if (lair != null && lair.kind() == GreatLairs.Kind.DEN) {
				how = DRAGON_HOARD;
			} else if (wreck) {
				how = SHIPWRECK;
			} else if (oldCity) {
				how = OLD_CITY;
			}
		}
		String who = finder instanceof ServerPlayer sp ? Identities.trueName(sp).getString() : finder != null ? finder.getName().getString() : "";
		return new Origin(how, detail, level.dimension().identifier().toString(), Optional.ofNullable(pos).map(BlockPos::immutable), biome,
			structure, level.getGameTime() / 24000L, who);
	}

	/**
	 * ItemStack.onCraftedBy (crafting, a furnace's or a smithing table's output taken): made by {@code player} where they
	 * stand. (A trade's goods get here too, but were stamped as bought just before.)
	 */
	public static void made(ItemStack stack, Player player) {
		if (ORIGIN == null || stack.has(ORIGIN) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		String how = player.containerMenu instanceof AbstractFurnaceMenu ? SMELTED : player.containerMenu instanceof SmithingMenu ? SMITHED : CRAFTED;
		stamp(stack, at(level, player.blockPosition(), how, "", player));
	}

	/** MerchantResultSlot.onTake: {@code stack} bought from {@code merchant}. */
	public static void bought(ItemStack stack, Player player, @Nullable Entity merchant) {
		if (ORIGIN != null && !stack.has(ORIGIN) && player.level() instanceof ServerLevel level) {
			stamp(stack, at(level, player.blockPosition(), BOUGHT, merchant == null ? "" : merchant.getName().getString(), player));
		}
	}

	/** {@code stack} is a famous find (see the class). */
	public static boolean famous(ItemStack stack) {
		Origin o = ORIGIN == null ? null : stack.get(ORIGIN);
		return o != null && o.famous();
	}

	/**
	 * What a collector adds (milli) when {@code sold} is sold for {@code paid}: half the coin paid again, for a famous find.
	 * Zero for anything else, or when what's paid isn't coin.
	 */
	public static long famousBonus(ItemStack sold, ItemStack paid) {
		if (!famous(sold) || paid.isEmpty()) {
			return 0L;
		}
		Currency c = Currency.byItem(paid.getItem());
		return c == null ? 0L : (long)(paid.getCount() * (long)c.base * FAMOUS_BONUS);
	}

	/** For comparing stacks: the same stack without its origin (or itself, if it has none). */
	public static ItemStack bare(ItemStack stack) {
		if (ORIGIN == null || !stack.has(ORIGIN)) {
			return stack;
		}
		ItemStack copy = stack.copy();
		copy.remove(ORIGIN);
		return copy;
	}
}
