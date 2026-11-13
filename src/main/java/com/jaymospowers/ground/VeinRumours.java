package com.jaymospowers.ground;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Vein rumours (Andy's TFC Hardrock picks: "Prospector career villagers sell vein rumours"). The stone-working villagers,
 * the masons who know the rock best, are the prospectors of a village: right-click one and, once, it puts two offers on
 * its trades. A <b>rumour</b>, a slip of paper that says which metal (iron, copper, gold or silver), which way and about how
 * far a great vein lies from where it was sold ({@link #REACH} blocks round the villager at most, but never closer than
 * {@link #CLOSEST}); and a <b>vein map</b>, a map of the same sort of country with the vein marked, for a master's price.
 * A vein is a run of {@link #A_VEIN} or more ore blocks together in one chunk section, read from the chunks that are
 * loaded round the villager, so a rumour always points at ore that is really there. A different metal from the map's goes
 * on the paper when two are in reach. Nothing is stocked if no vein is in reach.
 */
public final class VeinRumours {
	/** How far a rumour reaches and the least it ever is (blocks), and how many ore blocks together make a vein. */
	static final int REACH = 112, CLOSEST = 24, A_VEIN = 8;
	/** Prices in emeralds: the paper, and the map. */
	static final int PAPER_PRICE = 2, MAP_PRICE = 9, USES = 2, XP = 6;
	static final float PRICE_MULTIPLIER = 0.05F;

	/** The metals a rumour is about, with their name in lang ({@code ore_kind.jaymospowers.<name>}). */
	private static final List<Map.Entry<TagKey<Block>, String>> METALS = List.of(
		Map.entry(ConventionalBlockTags.IRON_ORES, "iron"),
		Map.entry(ConventionalBlockTags.COPPER_ORES, "copper"),
		Map.entry(ConventionalBlockTags.GOLD_ORES, "gold"),
		Map.entry(TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", "ores/silver")), "silver"));

	/** A vein found: its metal, and the middle of the ore. */
	record Vein(String metal, BlockPos at) {
	}

	private VeinRumours() {
	}

	public static void init() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && world instanceof ServerLevel level && entity instanceof Villager v && v.isAlive() && !v.isBaby()
				&& !player.isSpectator() && v.getTradingPlayer() == null && v.getVillagerData().profession().is(VillagerProfession.MASON)) {
				stock(level, v);
			}
			return InteractionResult.PASS;
		});
	}

	/** Puts the rumour and the map on {@code v}'s trades, once. */
	static void stock(ServerLevel level, Villager v) {
		MerchantOffers offers = v.getOffers();
		for (MerchantOffer offer : offers) {
			if (offer.getResult().has(DataComponents.LORE) && offer.getResult().is(Items.PAPER) || offer.getResult().is(Items.FILLED_MAP)
				&& offer.getResult().has(DataComponents.CUSTOM_NAME)) {
				return;
			}
		}
		List<Vein> veins = find(level, v.blockPosition());
		if (veins.isEmpty()) {
			return;
		}
		Vein first = veins.get(0);
		Vein second = veins.size() > 1 ? veins.get(1) : first;
		offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, PAPER_PRICE), java.util.Optional.of(new ItemCost(Items.PAPER, 1)),
			rumour(v.blockPosition(), first), USES, XP, PRICE_MULTIPLIER));
		ItemStack map = map(level, second);
		if (!map.isEmpty()) {
			offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, MAP_PRICE), java.util.Optional.of(new ItemCost(Items.COMPASS, 1)), map, 1, XP * 2,
				PRICE_MULTIPLIER));
		}
	}

	/** The slip of paper: the metal, the way and about how far from {@code from}. */
	static ItemStack rumour(BlockPos from, Vein vein) {
		ItemStack paper = new ItemStack(Items.PAPER);
		paper.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymospowers.vein_rumour",
			Component.translatable("ore_kind.jaymospowers." + vein.metal())));
		int dx = vein.at().getX() - from.getX(), dz = vein.at().getZ() - from.getZ();
		int distance = (int) (Math.round(Math.sqrt((double) dx * dx + (double) dz * dz) / 10.0) * 10);
		paper.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.jaymospowers.vein_rumour.line." + way(dx, dz), distance))));
		return paper;
	}

	/** The eight winds: "north" is toward -Z. */
	static String way(int dx, int dz) {
		double angle = Math.toDegrees(Math.atan2(dx, -dz));
		String[] names = {"north", "northeast", "east", "southeast", "south", "southwest", "west", "northwest"};
		return names[Math.floorMod((int) Math.round(angle / 45.0), 8)];
	}

	/** A map of the country round the vein with the vein marked on it, or empty if maps can't be made here. */
	static ItemStack map(ServerLevel level, Vein vein) {
		ItemStack map = MapItem.create(level, vein.at().getX(), vein.at().getZ(), (byte) 2, true, true);
		MapItem.renderBiomePreviewMap(level, map);
		MapItemSavedData.addTargetDecoration(map, vein.at(), "+", MapDecorationTypes.RED_X);
		map.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymospowers.vein_map",
			Component.translatable("ore_kind.jaymospowers." + vein.metal())));
		return map;
	}

	/**
	 * The veins in the loaded chunks round {@code from}, nearest first, one for each metal (at most two), none nearer than
	 * {@link #CLOSEST} or farther than {@link #REACH}.
	 */
	static List<Vein> find(ServerLevel level, BlockPos from) {
		List<Vein> best = new ArrayList<>();
		double[] bestDistance = new double[METALS.size()];
		Vein[] bestVein = new Vein[METALS.size()];
		java.util.Arrays.fill(bestDistance, Double.MAX_VALUE);
		int range = REACH / 16 + 1;
		ChunkPos centre = ChunkPos.containing(from);
		for (int cx = centre.x() - range; cx <= centre.x() + range; cx++) {
			for (int cz = centre.z() - range; cz <= centre.z() + range; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				LevelChunkSection[] sections = chunk.getSections();
				for (int s = 0; s < sections.length; s++) {
					for (int m = 0; m < METALS.size(); m++) {
						TagKey<Block> tag = METALS.get(m).getKey();
						LevelChunkSection section = sections[s];
						if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(tag))) {
							continue;
						}
						int n = 0;
						long sx = 0, sy = 0, sz = 0;
						for (int x = 0; x < 16; x++) {
							for (int y = 0; y < 16; y++) {
								for (int z = 0; z < 16; z++) {
									BlockState state = section.getBlockState(x, y, z);
									if (state.is(tag)) {
										n++;
										sx += x;
										sy += y;
										sz += z;
									}
								}
							}
						}
						if (n < A_VEIN) {
							continue;
						}
						BlockPos at = new BlockPos(cx * 16 + (int) (sx / n), chunk.getMinY() + s * 16 + (int) (sy / n), cz * 16 + (int) (sz / n));
						double d = Math.sqrt((double) (at.getX() - from.getX()) * (at.getX() - from.getX())
							+ (double) (at.getZ() - from.getZ()) * (at.getZ() - from.getZ()));
						if (d >= CLOSEST && d <= REACH && d < bestDistance[m]) {
							bestDistance[m] = d;
							bestVein[m] = new Vein(METALS.get(m).getValue(), at);
						}
					}
				}
			}
		}
		for (Vein vein : bestVein) {
			if (vein != null) {
				best.add(vein);
			}
		}
		best.sort((a, b) -> Double.compare(a.at().distSqr(from), b.at().distSqr(from)));
		return best.size() > 2 ? best.subList(0, 2) : best;
	}
}
