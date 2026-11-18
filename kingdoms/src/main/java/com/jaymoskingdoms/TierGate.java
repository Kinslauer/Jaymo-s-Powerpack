package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * (0.7.383, Andy's SevTech picks: "Hidden Until Reached") What is beyond a player's tier isn't offered to them. Smiths, the
 * wandering trader and every other merchant keep casts, blueprints and metal parts that belong to a higher tier out of the
 * offers shown to a player who hasn't reached it, and a smith's gear is never forged above the grade the player's tier
 * allows ({@link #maxGrade}). Nothing is removed from the world: the offers are set aside on the merchant ({@link #STASH})
 * and put back for the next player whose tier has caught up. JEI still lists every route.
 * <p>
 * Which tier an item belongs to: an item tag per tier ({@code jaymoskingdoms:tier_gate/<tier>}), or, for a part of Jaymo's
 * Tinkering, its material ({@link #MATERIAL_TIER}). Anything in neither is open from the start.
 */
public final class TierGate {
	// ---- tuning ----
	/** The tier (an ordinal) of each Tinkering material that isn't open from the start; any other material is Hearth's. */
	static final Map<String, Integer> MATERIAL_TIER = Map.ofEntries(
		Map.entry("pig_iron", 1),
		Map.entry("seared_stone", 1),
		Map.entry("silver", 2),
		Map.entry("rose_gold", 2),
		Map.entry("amethyst_bronze", 2),
		Map.entry("cobalt", 2),
		Map.entry("queen_slime", 2),
		Map.entry("slimesteel", 3),
		Map.entry("steel", 3),
		Map.entry("manyullyn", 3),
		Map.entry("hepatizon", 3),
		Map.entry("ember_dragon_bone", 4),
		Map.entry("rime_dragon_bone", 4),
		Map.entry("venom_dragon_bone", 4),
		Map.entry("storm_dragon_bone", 4));
	/** Per player tier (Hearth to Legend), the best forging grade a smith's gear can come with (0 poor, 1 well, 2 expert, 3 perfect, 4 master, 5 legendary). */
	static final int[] MAX_GRADE = {1, 2, 3, 4, 5};
	/** The grade index of Legendary, and the tier (Legend) that gear of it belongs to. */
	static final int LEGENDARY_GRADE = 5, LEGEND_TIER = 4;
	/** The namespace whose parts are read by material. */
	static final String TINKERING = "jaymospowers";

	/** Per tier above Hearth (index 1 to 4), the items held back until it's reached. */
	private static final List<TagKey<Item>> TAGS = List.of(tag("hearth"), tag("borderlands"), tag("wildlands"), tag("dread"), tag("legend"));

	/** Offers set aside on a merchant, for players below their tier. Saved with it. */
	static final AttachmentType<List<MerchantOffer>> STASH = AttachmentRegistry.create(JaymosKingdoms.id("tier_gate_stash"),
		builder -> builder.persistent(MerchantOffer.CODEC.listOf()));

	private TierGate() {
	}

	private static TagKey<Item> tag(String tier) {
		return TagKey.create(Registries.ITEM, JaymosKingdoms.id("tier_gate/" + tier));
	}

	static void register() {
		JaymosKingdoms.LOGGER.debug("tier gate attachment: {}", STASH);
		// before the trading screen opens (and before GradedTrades grades what is left)
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (player instanceof ServerPlayer sp && entity instanceof AbstractVillager merchant && merchant.isAlive() && merchant.getTradingPlayer() == null
				&& !world.isClientSide()) {
				filter(sp, merchant);
			}
			return InteractionResult.PASS;
		});
	}

	/** The tier (an ordinal) {@code stack} belongs to: 0 if it is open from the start. */
	public static int tierOf(ItemStack stack) {
		if (stack.isEmpty()) {
			return 0;
		}
		int tier = 0;
		for (int i = 1; i < TAGS.size(); i++) {
			if (stack.is(TAGS.get(i))) {
				tier = Math.max(tier, i);
			}
		}
		var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (id.getNamespace().equals(TINKERING)) {
			var data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
			if (data != null && !data.strings().isEmpty()) {
				tier = Math.max(tier, MATERIAL_TIER.getOrDefault(data.strings().get(0), 0));
			}
		}
		if (GradedTrades.gradeIndex(stack) >= LEGENDARY_GRADE) {
			tier = Math.max(tier, LEGEND_TIER);
		}
		return tier;
	}

	/** Whether {@code stack} may be offered to {@code player}: their tier has reached it (or they are in creative). */
	public static boolean allowed(Player player, ItemStack stack) {
		return player.isCreative() || tierOf(stack) <= gearTier(player);
	}

	/**
	 * The tier whose gear {@code player} may see, buy and have forged: their world tier, or a Tinkerer's craftsman's eye if
	 * that is higher ({@link TinkerPerks#eye}, class trees). Only the gear: the monsters round them stay at the real tier.
	 */
	public static int gearTier(Player player) {
		return Math.max(WorldTiers.progress(player).open(), TinkerPerks.eye(player));
	}

	/** The best forging grade (0 poor to 5 legendary) a smith's gear may come with for {@code player}. */
	public static int maxGrade(Player player) {
		return player.isCreative() ? MAX_GRADE[MAX_GRADE.length - 1] : MAX_GRADE[Math.clamp(gearTier(player), 0, MAX_GRADE.length - 1)];
	}

	/** Sets aside the offers beyond {@code player}'s tier, and puts back the ones set aside that their tier has reached. */
	static void filter(ServerPlayer player, AbstractVillager merchant) {
		List<MerchantOffer> stash = new ArrayList<>(merchant.getAttachedOrElse(STASH, List.of()));
		MerchantOffers offers = merchant.getOffers();
		boolean changed = false;
		for (Iterator<MerchantOffer> it = stash.iterator(); it.hasNext();) {
			MerchantOffer offer = it.next();
			if (allowed(player, offer.getResult())) {
				offers.add(offer);
				it.remove();
				changed = true;
			}
		}
		for (Iterator<MerchantOffer> it = offers.iterator(); it.hasNext();) {
			MerchantOffer offer = it.next();
			if (!allowed(player, offer.getResult())) {
				stash.add(offer);
				it.remove();
				changed = true;
			}
		}
		if (changed) {
			if (stash.isEmpty()) {
				merchant.removeAttached(STASH);
			} else {
				merchant.setAttached(STASH, stash);
			}
		}
	}
}
