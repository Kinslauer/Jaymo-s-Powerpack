package com.jaymoskingdoms.money;

import com.google.common.collect.ImmutableSet;
import com.jaymoskingdoms.JaymosKingdoms;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.Optional;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * (0.7.486) The money changer: a villager who works at a Counting Table, makes change for free, and trades the gems the
 * coinage left behind. Coins are the money now; an emerald is a gem he'll buy for {@link #EMERALD_CP} cp (a little under a
 * silver) and sell back for a silver and some copper. All his trades are plain vanilla offers, so no screen of our own
 * is needed. His rates are the constants below.
 *
 * <p>For {@link #PAR_DAYS} days after a world first loads with the coinage he buys emeralds at par, one silver
 * ({@link #PAR_EMERALD_CP}), so nobody is robbed of the emeralds they were carrying by the patch.
 *
 * <p>His offers are marked (a price multiplier of {@link #MARK}, no experience) so {@link #own} can tell them from a
 * villager's trades, which the coin conversion of trades leaves be.
 */
public final class Changer {
	/** What he gives for one emerald, in copper pieces; and the same while the par grace lasts. */
	public static final int EMERALD_CP = 9, PAR_EMERALD_CP = 10;
	/** What he gives for an emerald block, in silver. */
	public static final int EMERALD_BLOCK_SILVER = 8;
	/** What he asks for an emerald, in silver and copper coins. */
	public static final int SELL_EMERALD_SILVER = 1, SELL_EMERALD_COPPER = 2;
	/** What he gives for a diamond, in silver. */
	public static final int DIAMOND_SILVER = 4;
	/** Change is free and exact: ten of one coin for one of the next. */
	public static final int CHANGE_RATIO = 10;
	/** He never runs out. */
	static final int USES = 9999;
	/** Days of par for old emeralds after the coinage first loads. */
	public static final int PAR_DAYS = 14;
	/** An offer made by a changer has this price multiplier and no experience. */
	public static final float MARK = 0.0F;

	/** When the par grace ends (game time), set the first time a world loads. Saved on the Overworld. */
	public static final AttachmentType<Long> PAR_UNTIL = AttachmentRegistry.create(JaymosKingdoms.id("par_until"),
		builder -> builder.persistent(Codec.LONG));

	public static final Identifier ID = JaymosKingdoms.id("money_changer");
	public static final ResourceKey<PoiType> POI = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, ID);
	public static VillagerProfession PROFESSION;

	private Changer() {
	}

	static void register() {
		PoiHelper.register(ID, 1, 1, MoneyItems.COUNTING_TABLE);
		PROFESSION = Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, ID, new VillagerProfession(
			Component.translatable("entity.minecraft.villager." + ID.getNamespace() + "." + ID.getPath()), holder -> holder.is(POI), holder -> holder.is(POI),
			ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_CARTOGRAPHER, Int2ObjectMap.ofEntries()));
		JaymosKingdoms.LOGGER.debug("par attachment: {}", PAR_UNTIL);
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			ServerLevel overworld = server.overworld();
			if (!overworld.hasAttached(PAR_UNTIL)) {
				overworld.setAttached(PAR_UNTIL, overworld.getGameTime() + PAR_DAYS * 24000L);
			}
		});
		// he sets out his rates whenever someone comes to trade
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level instanceof ServerLevel sl && entity instanceof Villager v && is(v) && v.getTradingPlayer() == null && !v.isBaby()) {
				v.setOffers(offers(sl.getServer()));
			}
			return InteractionResult.PASS;
		});
	}

	/** Whether {@code v} is a money changer. */
	public static boolean is(Villager v) {
		return v.getVillagerData().profession().value() == PROFESSION;
	}

	public static boolean is(Holder<VillagerProfession> profession) {
		return profession.value() == PROFESSION;
	}

	/** Whether {@code offer} is one of a changer's (the coin conversion of other trades skips these). */
	public static boolean own(MerchantOffer offer) {
		return offer.getPriceMultiplier() == MARK && offer.getXp() == 0;
	}

	/** Whether the par grace for old emeralds is still on. */
	public static boolean par(MinecraftServer server) {
		Long until = server.overworld().getAttached(PAR_UNTIL);
		return until != null && server.overworld().getGameTime() < until;
	}

	/** What he gives for an emerald now, in cp. */
	public static int emeraldCp(MinecraftServer server) {
		return par(server) ? PAR_EMERALD_CP : EMERALD_CP;
	}

	/** His offers at today's rates. */
	public static MerchantOffers offers(MinecraftServer server) {
		MerchantOffers out = new MerchantOffers();
		out.add(offer(new ItemCost(Items.EMERALD, 1), Optional.empty(), Coin.COPPER.stack(emeraldCp(server))));
		out.add(offer(new ItemCost(Items.EMERALD_BLOCK, 1), Optional.empty(), Coin.SILVER.stack(EMERALD_BLOCK_SILVER)));
		out.add(offer(new ItemCost(Coin.SILVER.item(), SELL_EMERALD_SILVER), Optional.of(new ItemCost(Coin.COPPER.item(), SELL_EMERALD_COPPER)),
			new ItemStack(Items.EMERALD)));
		out.add(offer(new ItemCost(Items.DIAMOND, 1), Optional.empty(), Coin.SILVER.stack(DIAMOND_SILVER)));
		Coin[] coins = Coin.values();
		for (int i = 0; i + 1 < coins.length; i++) {
			Coin small = coins[i], big = coins[i + 1];
			out.add(offer(new ItemCost(small.item(), CHANGE_RATIO), Optional.empty(), big.stack(1)));
			out.add(offer(new ItemCost(big.item(), 1), Optional.empty(), small.stack(CHANGE_RATIO)));
		}
		return out;
	}

	private static MerchantOffer offer(ItemCost cost, Optional<ItemCost> second, ItemStack result) {
		return new MerchantOffer(cost, second, result, USES, 0, MARK);
	}

	/** The pedlars' rates are worse: they keep this share (percent) of the value when they change a coin. */
	public static final int PEDLAR_CUT_PERCENT = 10;
}
