package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.490) Hordes patch 3, Andy's money (2026-11-03): "bent ingots, a bent copper and a bent iron, simple items.
 * Greenskins take only bent ones and are dumb enough to refuse the same ingot once it is straightened."
 * <ul>
 * <li>A player bends a copper or iron ingot by using it on an anvil, sneaking, one at a time (sneaking so an iron ingot
 * can still open the anvil to repair with). Cold hammering won't straighten one again (Andy, 2026-11-03): it has to be
 * melted back down (furnace or blast furnace) or superheated first (Powers' Forge Hearth, where it comes off a Hot
 * Ingot that cools to a plain one).</li>
 * <li>Greenskins pay, are paid and trade in bent ingots only ({@link GreenMarket}, {@link Tribes#tribute}); offered a
 * plain ingot (sneaking, in hand) they refuse it in their broken English, and the same ingot bent they take.</li>
 * <li>A bent copper is worth {@link #COPPER_WORTH}, a bent iron {@link #IRON_WORTH}; prices and hoards ({@link HordeEconomy})
 * are counted in that worth.</li>
 * </ul>
 */
public final class BentIngots {
	// ================================================================================================================
	// Tuning
	/** What each bent ingot is worth, in the worth every greenskin price and hoard is counted in. */
	public static final int COPPER_WORTH = 1, IRON_WORTH = 3;
	/** A bent ingot offered by hand to a greenskin eases the grudge by this much for each point of worth. */
	static final int OFFER_RELIEF = 1;
	/** A plain ingot is refused no more than once in this long (ticks), so a held click doesn't flood the chat. */
	static final int REFUSE_GAP = 40;
	/** (0.7.490) The most of one kind of bent ingot a single trade cost can ask for (one stack). */
	static final int STACK = 64;
	// ================================================================================================================

	public static Item BENT_COPPER, BENT_IRON;

	/** Speaker → when they last turned a plain ingot down (game time). */
	private static final java.util.Map<java.util.UUID, Long> REFUSED = new java.util.HashMap<>();

	private BentIngots() {
	}

	static void register() {
		BENT_COPPER = item("bent_copper", new Item.Properties());
		BENT_IRON = item("bent_iron", new Item.Properties().rarity(Rarity.UNCOMMON));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			output.accept(BENT_COPPER);
			output.accept(BENT_IRON);
		});
		UseBlockCallback.EVENT.register(BentIngots::onAnvil);
		UseEntityCallback.EVENT.register(BentIngots::onOffer);
	}

	private static Item item(String name, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new HavenWares.Ware(properties.setId(key), name));
	}

	// ---- what they are -----------------------------------------------------------------------------------------------

	/** {@code stack} is a bent ingot. */
	public static boolean bent(ItemStack stack) {
		return stack.is(BENT_COPPER) || stack.is(BENT_IRON);
	}

	/** {@code stack} is a plain (straightened) copper or iron ingot: the kind greenskins refuse. */
	public static boolean plain(ItemStack stack) {
		return stack.is(Items.COPPER_INGOT) || stack.is(Items.IRON_INGOT);
	}

	/** One of {@code stack}'s worth (0 for anything that isn't a bent ingot). */
	public static int worth(ItemStack stack) {
		return stack.is(BENT_COPPER) ? COPPER_WORTH : stack.is(BENT_IRON) ? IRON_WORTH : 0;
	}

	/** What the whole {@code stack} is worth. */
	public static int total(ItemStack stack) {
		return worth(stack) * stack.getCount();
	}

	/**
	 * (0.7.490) An offer that sells {@code result} for {@code worth} in bent ingots. Priced in bent copper (the common
	 * drop), since a vanilla offer wants that exact item and gives no change: a price asked in iron could not be paid in
	 * copper. Above one stack of copper, the first cost is bent iron and the second the copper that's left over.
	 */
	public static MerchantOffer offer(int worth, ItemStack result, int uses, int xp, float multiplier) {
		int w = Math.max(1, worth);
		if (w <= STACK) {
			return new MerchantOffer(new ItemCost(BENT_COPPER, w), result, uses, xp, multiplier);
		}
		int irons = Math.min(STACK, (w - STACK + IRON_WORTH - 1) / IRON_WORTH);
		int coppers = Math.min(STACK, (w - irons * IRON_WORTH) / COPPER_WORTH);
		Optional<ItemCost> rest = coppers > 0 ? Optional.of(new ItemCost(BENT_COPPER, coppers)) : Optional.empty();
		return new MerchantOffer(new ItemCost(BENT_IRON, irons), rest, result, uses, xp, multiplier);
	}

	/** {@code worth} as one stack of bent ingots, paid out: iron where it divides evenly, else copper. */
	public static ItemStack coin(int worth) {
		int w = Math.max(1, worth);
		return w % IRON_WORTH == 0 ? new ItemStack(BENT_IRON, Math.min(STACK, w / IRON_WORTH)) : new ItemStack(BENT_COPPER, Math.min(STACK, w));
	}

	/** (0.7.490) What {@link #coin} really pays for {@code worth}: one stack holds only so much, and a buyer must never be paid less than was booked. */
	public static int payable(int worth) {
		int w = Math.max(1, worth);
		return w % IRON_WORTH == 0 ? Math.min(STACK, w / IRON_WORTH) * IRON_WORTH : Math.min(STACK, w);
	}

	/** {@code worth} as stacks of bent ingots: as many iron as fit, the rest copper. */
	public static List<ItemStack> stacks(long worth) {
		List<ItemStack> out = new ArrayList<>();
		long irons = worth / IRON_WORTH, coppers = worth % IRON_WORTH;
		while (irons > 0) {
			int n = (int)Math.min(64L, irons);
			out.add(new ItemStack(BENT_IRON, n));
			irons -= n;
		}
		if (coppers > 0) {
			out.add(new ItemStack(BENT_COPPER, (int)coppers));
		}
		return out;
	}

	// ---- in a player's pack --------------------------------------------------------------------------------------------

	/** The worth of the bent ingots {@code player} carries (bundles too). */
	public static int held(ServerPlayer player) {
		return Purse.count(player, BENT_COPPER) * COPPER_WORTH + Purse.count(player, BENT_IRON) * IRON_WORTH;
	}

	/** Plain ingots {@code player} carries (the kind a greenskin refuses). */
	public static int plainHeld(ServerPlayer player) {
		return Purse.count(player, Items.COPPER_INGOT) + Purse.count(player, Items.IRON_INGOT);
	}

	/**
	 * Takes up to {@code want} worth from {@code player}: copper first, then iron (change in copper if an iron overshoots).
	 * Returns the worth taken.
	 */
	public static int take(ServerPlayer player, int want) {
		int paid = 0;
		int coppers = Math.min(Purse.count(player, BENT_COPPER), want);
		if (coppers > 0) {
			Purse.take(player, BENT_COPPER, coppers);
			paid += coppers * COPPER_WORTH;
		}
		while (paid < want && Purse.count(player, BENT_IRON) > 0) {
			Purse.take(player, BENT_IRON, 1);
			int over = paid + IRON_WORTH - want;
			if (over > 0) {
				give(player, over);
				paid = want;
			} else {
				paid += IRON_WORTH;
			}
		}
		return paid;
	}

	/** Gives {@code player} {@code worth} in bent ingots (what doesn't fit in the pack drops at their feet). */
	public static void give(ServerPlayer player, long worth) {
		for (ItemStack stack : stacks(worth)) {
			player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
		}
	}

	// ---- the anvil -----------------------------------------------------------------------------------------------------

	/** A copper or iron ingot used (sneaking) on an anvil is bent, one at a time; a cold bent one only rings off it. */
	private static InteractionResult onAnvil(Player player, net.minecraft.world.level.Level level, InteractionHand hand,
		net.minecraft.world.phys.BlockHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		if (player.isSpectator() || !level.getBlockState(hit.getBlockPos()).is(BlockTags.ANVIL)) {
			return InteractionResult.PASS;
		}
		boolean bending = plain(held) && player.isShiftKeyDown();
		if (!bending && !bent(held)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		if (!bending) {
			// ((0.7.490) cold, it just rings: melt it down or superheat it first)
			level.playSound(null, hit.getBlockPos(), SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.3F, 1.6F);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bent.cold"));
			return InteractionResult.SUCCESS;
		}
		Item result = held.is(Items.COPPER_INGOT) ? BENT_COPPER : BENT_IRON;
		if (!player.getAbilities().instabuild) {
			held.shrink(1);
		}
		player.getInventory().placeItemBackInInventory(new ItemStack(result), net.minecraft.util.Prediction.SERVER_ONLY);
		float pitch = 0.9F + level.getRandom().nextFloat() * 0.2F;
		level.playSound(null, hit.getBlockPos(), SoundEvents.ANVIL_USE, SoundSource.BLOCKS, 0.6F, pitch);
		return InteractionResult.SUCCESS;
	}

	// ---- offering one to a greenskin ------------------------------------------------------------------------------------

	/**
	 * Sneaking, with an ingot in hand, on a goblin or an orc: a plain ingot is refused in their broken English, a bent one
	 * is taken into their camp's hoard and eases the tribe's grudge a little.
	 */
	private static InteractionResult onOffer(Player player, net.minecraft.world.level.Level level, InteractionHand hand, Entity entity,
		net.minecraft.world.phys.EntityHitResult hit) {
		ItemStack held = player.getItemInHand(hand);
		if (!player.isShiftKeyDown() || !(plain(held) || bent(held)) || brood(entity) == null || !(entity instanceof Worker w)) {
			return InteractionResult.PASS;
		}
		if (level.isClientSide() || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		if (plain(held)) {
			refuse(server, entity, sp);
			return InteractionResult.SUCCESS;
		}
		MinecraftServer minecraft = server.getServer();
		if (!Hordes.greenskin(minecraft, w.factionId())) {
			// ((0.7.490) a greenskin living in a human or haven settlement has no chief's hoard to take it)
			return InteractionResult.PASS;
		}
		int worth = worth(held);
		if (!sp.getAbilities().instabuild) {
			held.shrink(1);
		}
		Faction camp = Factions.get(minecraft, w.factionId());
		String tribe = camp == null ? null : Tribes.of(minecraft, camp.id());
		if (camp != null) {
			HordeEconomy.deposit(minecraft, camp.id(), worth);
		}
		if (tribe != null && Tribes.grudge(minecraft, sp.getUUID(), tribe) > 0) {
			Tribes.add(minecraft, sp.getUUID(), tribe, -worth * OFFER_RELIEF);
		}
		server.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 0.6F);
		Speech.tell(server, entity, sp, Component.translatable("talk.jaymoskingdoms.green.bent." + brood(entity) + "." + entity.getRandom().nextInt(3)), Speech.GOLD);
		return InteractionResult.SUCCESS;
	}

	/** {@code speaker} (a goblin or an orc) turns down a plain ingot from {@code player}, in their own broken English. */
	static void refuse(ServerLevel level, Entity speaker, ServerPlayer player) {
		String brood = brood(speaker);
		long now = level.getGameTime();
		if (brood == null || now - REFUSED.getOrDefault(speaker.getUUID(), -(long)REFUSE_GAP) < REFUSE_GAP) {
			return;
		}
		REFUSED.put(speaker.getUUID(), now);
		if (REFUSED.size() > 256) {
			REFUSED.values().removeIf(t -> now - t > REFUSE_GAP);
		}
		Speech.tell(level, speaker, player, Component.translatable("talk.jaymoskingdoms.green.plain." + brood + "." + speaker.getRandom().nextInt(3)), Speech.GOLD);
	}

	/** "goblin" or "orc" for a goblin's or an orc's body (folk or villager), else null. */
	static @Nullable String brood(Entity e) {
		if (e instanceof Worker w) {
			if (Goblins.isGoblin(w.role())) {
				return "goblin";
			}
			if (Orcs.isOrc(w.role())) {
				return "orc";
			}
		}
		Heritage.Kind kind = Greenskins.of(e);
		return kind == null ? null : kind == Heritage.Kind.ORC ? "orc" : "goblin";
	}
}
