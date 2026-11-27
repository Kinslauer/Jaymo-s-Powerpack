package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.jaymoskingdoms.money.Money;
import org.jspecify.annotations.Nullable;

/**
 * Stolen goods are known (Andy's pick from Chest DNA, 2026-11-24). What is taken from a realm's storehouses or its
 * people's homes ({@link Theft}) or lifted from a pocket ({@link Pickpocket}) carries a {@link #STOLEN} mark naming the
 * realm. That realm's merchants know their own: put a marked thing in a trade with one and they close the deal, keep it
 * and cry thief ({@link #offered}); a guard's search finds it and it goes back to the stores ({@link #confiscate}). A fence
 * in a speakeasy cleans the marks off for a cut ({@link #clean}). Coins are never marked. Marked and unmarked things don't
 * stack together, so a thief can see which are which.
 */
public final class StolenGoods {
	/** A fence's cut: so much a piece, and a share of what the goods are worth (milli per {@link Lockpicking#worth} point). */
	static final long FENCE_PER_PIECE = 50L, FENCE_PER_WORTH = 200L, FENCE_LEAST = 100L;
	/** A home's chest: one this near a bed, inside a realm's law. */
	static final int HOME_BED_REACH = 6;
	/** How far off (blocks) the merchant a player trades with is looked for. */
	static final double TRADER_REACH = 8.0;

	/** The realm it was taken from, and the day. */
	public record Mark(int realm, long day) {
		public static final Codec<Mark> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("realm").forGetter(Mark::realm),
			Codec.LONG.optionalFieldOf("day", 0L).forGetter(Mark::day)
		).apply(i, Mark::new));
		public static final StreamCodec<io.netty.buffer.ByteBuf, Mark> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Mark::realm, ByteBufCodecs.VAR_LONG, Mark::day, Mark::new);
	}

	public static DataComponentType<Mark> STOLEN;
	/** Players whose trade screen changed this tick, to look over for stolen goods next tick (not inside the screen's own update). */
	private static final Map<UUID, Integer> OFFERED = new HashMap<>();

	private StolenGoods() {
	}

	static void register() {
		STOLEN = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKingdoms.id("stolen"),
			DataComponentType.<Mark>builder().persistent(Mark.CODEC).networkSynchronized(Mark.STREAM_CODEC).build());
		ServerTickEvents.END_SERVER_TICK.register(StolenGoods::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> OFFERED.clear());
	}

	// ---- marking ----------------------------------------------------------------------------------------------------

	/** Marks {@code stack} as taken from {@code f} (not coins). */
	static void mark(ServerLevel level, ItemStack stack, Faction f) {
		if (STOLEN != null && !stack.isEmpty() && !Currencies.isMoney(stack)) {
			stack.set(STOLEN, new Mark(f.id(), level.getGameTime() / 24000L));
		}
	}

	/**
	 * {@code n} of {@code item} that {@code player} just took from {@code f}'s chest: that many unmarked ones in their pack
	 * are split off and marked (they took them, whichever they were).
	 */
	static void markTaken(ServerPlayer player, ServerLevel level, Item item, int n, Faction f) {
		if (n <= 0 || STOLEN == null) {
			return;
		}
		var inventory = player.getInventory();
		List<ItemStack> marked = new ArrayList<>();
		for (int i = 0; i < inventory.getContainerSize() && n > 0; i++) {
			ItemStack s = inventory.getItem(i);
			if (!s.is(item) || s.has(STOLEN) || Currencies.isMoney(s)) {
				continue;
			}
			ItemStack part = s.split(Math.min(n, s.getCount()));
			n -= part.getCount();
			mark(level, part, f);
			marked.add(part);
		}
		for (ItemStack part : marked) {
			if (!inventory.add(part) && !part.isEmpty()) {
				player.drop(part, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		inventory.setChanged();
	}

	/** {@code stack} was taken from realm {@code realm} (or any realm, for -1). */
	static boolean stolenFrom(ItemStack stack, int realm) {
		Mark m = STOLEN == null ? null : stack.get(STOLEN);
		return m != null && (realm < 0 || m.realm() == realm);
	}

	/** {@code player} carries something taken from {@code realm} (-1: from anyone). */
	static boolean carries(ServerPlayer player, int realm) {
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (stolenFrom(inventory.getItem(i), realm)) {
				return true;
			}
		}
		return false;
	}

	/** A chest that is someone's at home: near a bed, where {@code f}'s law holds (Theft's opened). */
	static boolean homeChest(ServerLevel level, net.minecraft.core.BlockPos pos) {
		for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(pos.offset(-HOME_BED_REACH, -2, -HOME_BED_REACH),
			pos.offset(HOME_BED_REACH, 2, HOME_BED_REACH))) {
			if (level.getBlockState(p).is(net.minecraft.tags.BlockTags.BEDS)) {
				return true;
			}
		}
		return false;
	}

	// ---- found out --------------------------------------------------------------------------------------------------

	/**
	 * A guard found what {@code player} took from {@code f}: it's taken off them, cleaned, and goes back to the realm's
	 * stores (what doesn't fit is kept by the guard). How many pieces.
	 */
	static int confiscate(ServerLevel level, ServerPlayer player, Faction f) {
		var inventory = player.getInventory();
		ServerLevel home = Factions.level(level.getServer());
		int n = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack s = inventory.getItem(i);
			if (stolenFrom(s, f.id())) {
				ItemStack back = inventory.removeItemNoUpdate(i);
				n += back.getCount();
				back.remove(STOLEN);
				Storage.put(home, f, back);
			}
		}
		if (n > 0) {
			inventory.setChanged();
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.stolen.confiscated", n, f.name()).withStyle(ChatFormatting.RED));
		}
		return n;
	}

	/** MerchantContainer.updateSellItem: {@code player}'s trade changed; looked at next tick. */
	public static void offered(ServerPlayer player) {
		OFFERED.put(player.getUUID(), player.containerMenu.containerId);
	}

	private static void tick(MinecraftServer server) {
		if (OFFERED.isEmpty()) {
			return;
		}
		Map<UUID, Integer> now = new HashMap<>(OFFERED);
		OFFERED.clear();
		for (var e : now.entrySet()) {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player != null && player.containerMenu instanceof MerchantMenu menu && menu.containerId == e.getValue()) {
				recognise(player, menu);
			}
		}
	}

	/**
	 * Something marked lies in {@code player}'s trade with a merchant of the realm it was taken from: the merchant knows
	 * it. The deal is off, they keep it for the stores, and they cry thief.
	 */
	private static void recognise(ServerPlayer player, MerchantMenu menu) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		Villager merchant = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(TRADER_REACH), v -> v.getTradingPlayer() == player)
			.stream().findFirst().orElse(null);
		if (merchant == null) {
			return;
		}
		Faction f = Theft.lawAt(level, merchant.blockPosition());
		if (f == null) {
			return;
		}
		boolean found = false;
		for (int slot = 0; slot < 2; slot++) {
			ItemStack s = menu.getSlot(slot).getItem();
			if (stolenFrom(s, f.id())) {
				ItemStack back = menu.getSlot(slot).remove(s.getCount());
				back.remove(STOLEN);
				Storage.put(Factions.level(level.getServer()), f, back);
				found = true;
			}
		}
		if (!found) {
			return;
		}
		player.closeContainer();
		Speech.say(level, merchant, Component.translatable("message.jaymoskingdoms.stolen.known." + level.getRandom().nextInt(3)), Speech.RED, 16.0);
		level.playSound(null, merchant.getX(), merchant.getY(), merchant.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
		List<LivingEntity> seen = new ArrayList<>(Theft.crimeWitnesses(level, f, player));
		if (!seen.contains(merchant)) {
			seen.add(merchant);
		}
		Theft.commit(player, level, f, Theft.Crime.TAKE, null, true, seen);
	}

	// ---- the fence --------------------------------------------------------------------------------------------------

	/** What the fence asks to clean everything marked {@code player} carries (milli), or 0 if they carry nothing marked. */
	static long cut(ServerPlayer player) {
		List<ItemStack> goods = new ArrayList<>();
		long pieces = 0;
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack s = inventory.getItem(i);
			if (stolenFrom(s, -1)) {
				goods.add(s);
				pieces += s.getCount();
			}
		}
		if (pieces == 0) {
			return 0L;
		}
		return Math.max(FENCE_LEAST, pieces * FENCE_PER_PIECE + (long)(Lockpicking.worth(goods) * FENCE_PER_WORTH));
	}

	/** The fence's Clean the Goods button: for their cut, the marks come off. What they say. */
	static @Nullable Component clean(ServerPlayer player, LivingEntity fence) {
		long cut = cut(player);
		if (cut <= 0L) {
			return null;
		}
		if (!Money.charge(player, cut)) {
			return Component.translatable("message.jaymoskingdoms.stolen.fence.short", Money.text(player, cut));
		}
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack s = inventory.getItem(i);
			if (stolenFrom(s, -1)) {
				s.remove(STOLEN);
			}
		}
		inventory.setChanged();
		fence.level().playSound(null, fence.getX(), fence.getY(), fence.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 0.8F, 0.9F);
		return Component.translatable("message.jaymoskingdoms.stolen.fence.done", Money.textPaid(player, cut));
	}
}
