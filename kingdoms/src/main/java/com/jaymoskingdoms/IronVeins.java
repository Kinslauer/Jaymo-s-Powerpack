package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Iron is scarce, as the Hardrock plan said (Andy's TFC Hardrock picks: "most villages will not have iron armor or tools
 * and will be more encouraged to trade for it"; Andy 2026-11-12: "iron as planned"). A realm has iron only if a great
 * iron vein lies near its seat: {@link #A_VEIN} or more iron ore blocks together in one chunk section within
 * {@link #REACH_CHUNKS} chunks of it. Without one its folk go in leather, bronze (copper) or mail ({@link Outfits}), and its
 * smiths ask {@link #DEAR} more for anything of iron. The answer is kept once a vein is found, or once enough of the
 * country round the seat ({@link #ENOUGH_LOADED}) has been looked over without one.
 */
public final class IronVeins {
	/** How far round a seat a vein counts (chunks), how many iron ores together make a vein. */
	static final int REACH_CHUNKS = 6, A_VEIN = 8;
	/** The share of the chunks in reach that must be loaded before "no vein" is believed. */
	static final float ENOUGH_LOADED = 0.6F;
	/** How much dearer a smith's iron goods are in a realm with no iron (a share of the price). */
	static final float DEAR = 0.5F;

	/** Realm id: whether iron lies near its seat, once that is known. */
	private static final Map<Integer, Boolean> KNOWN = new HashMap<>();

	private IronVeins() {
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> KNOWN.clear());
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand == InteractionHand.MAIN_HAND && world instanceof ServerLevel level && entity instanceof Villager v && v.isAlive()
				&& !v.isBaby() && !player.isSpectator() && v.getTradingPlayer() == null && smith(v)) {
				dearIron(level, v);
			}
			return InteractionResult.PASS;
		});
	}

	/** Whether {@code faction}'s seat has a great iron vein near it (false while that isn't known yet). */
	public static boolean near(ServerLevel level, Faction faction) {
		Boolean known = KNOWN.get(faction.id());
		if (known != null) {
			return known;
		}
		ChunkPos centre = ChunkPos.containing(faction.seat());
		int loaded = 0, all = 0;
		for (int cx = centre.x() - REACH_CHUNKS; cx <= centre.x() + REACH_CHUNKS; cx++) {
			for (int cz = centre.z() - REACH_CHUNKS; cz <= centre.z() + REACH_CHUNKS; cz++) {
				all++;
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) {
					continue;
				}
				loaded++;
				if (vein(chunk)) {
					KNOWN.put(faction.id(), true);
					return true;
				}
			}
		}
		if (loaded >= all * ENOUGH_LOADED) {
			KNOWN.put(faction.id(), false);
		}
		return false;
	}

	private static boolean vein(LevelChunk chunk) {
		for (LevelChunkSection section : chunk.getSections()) {
			if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(BlockTags.IRON_ORES))) {
				continue;
			}
			int count = 0;
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						if (section.getBlockState(x, y, z).is(BlockTags.IRON_ORES) && ++count >= A_VEIN) {
							return true;
						}
					}
				}
			}
		}
		return false;
	}

	private static boolean smith(Villager v) {
		var trade = v.getVillagerData().profession();
		return trade.is(VillagerProfession.WEAPONSMITH) || trade.is(VillagerProfession.ARMORER) || trade.is(VillagerProfession.TOOLSMITH);
	}

	/** In a realm with no iron, {@code v}'s iron goods cost {@link #DEAR} more while this trade lasts. */
	private static void dearIron(ServerLevel level, Villager v) {
		Faction faction = Factions.at(level.getServer(), level, v.blockPosition());
		if (faction == null || !faction.type().isRealm() || near(level, faction)) {
			return;
		}
		for (MerchantOffer offer : v.getOffers()) {
			if (iron(offer.getResult())) {
				// (set, not added: a click that opens no trade must not stack it; the trade's end clears it)
				offer.setSpecialPriceDiff(Math.max(1, Mth.ceil(offer.getBaseCostA().getCount() * DEAR)));
			}
		}
	}

	/** An iron ingot, block, tool, weapon or piece of armour (anything named iron but a nugget). */
	static boolean iron(ItemStack stack) {
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.contains("iron") && !path.contains("nugget");
	}

	/** Whether {@code pos} in {@code level} lies in a realm that has no iron near. */
	static boolean poorAt(ServerLevel level, BlockPos pos) {
		Faction faction = Factions.at(level.getServer(), level, pos);
		return faction != null && faction.type().isRealm() && !near(level, faction);
	}
}
