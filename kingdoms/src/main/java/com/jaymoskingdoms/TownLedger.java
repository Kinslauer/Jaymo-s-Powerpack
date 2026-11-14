package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import org.jspecify.annotations.Nullable;

/**
 * The town ledger (Andy's pick from MCA Kingdoms' village ledger, 2026-11-14; our own version). Every seat keeps a book
 * of its own affairs: in a realm the ruler keeps it, in a village anyone grown can fetch it down for you. "Read the town
 * ledger" on the Town business screen ({@link TalkErrands}) hands you a fresh copy, a written book of what
 * {@code /kingdom info} tells (who rules, temper and creed, wealth, stores, hands, peace, its members), then its troubles
 * ({@link TownTroubles}), its works ({@link TownProjects}) and its treaties ({@link Treaties}). A town that hates you
 * ({@link Reputation#HOSTILE}) won't open its books to you. An older copy of the same town's ledger in your pack is
 * swapped for the new one.
 */
public final class TownLedger {
	/** About how many characters fit on a book page before the next line goes over. */
	static final int PAGE_CHARS = 200;
	private static final String TAG = "jaymoskingdoms_ledger";

	private TownLedger() {
	}

	/** The town {@code speaker} belongs to, if it keeps a ledger: a realm's or a village's, standing. */
	private static @Nullable Faction town(LivingEntity speaker) {
		if (!(speaker.level() instanceof ServerLevel level)) {
			return null;
		}
		Faction f = Factions.around(level.getServer(), level, speaker.blockPosition(), Villages.VILLAGE_RADIUS);
		return f == null || f.fallen() || f.type() == FactionType.BARBARIAN ? null : f;
	}

	/** Whether {@code speaker} can hand over their town's ledger: a realm's ruler, or any grown villager of a village. */
	static boolean keeps(LivingEntity speaker) {
		Faction f = town(speaker);
		if (f == null) {
			return false;
		}
		return f.type().isRealm() ? Rulers.is(speaker) : speaker instanceof Villager v && !v.isBaby();
	}

	/** {@code speaker} hands {@code player} a fresh copy of their town's ledger. */
	static void give(ServerPlayer player, LivingEntity speaker) {
		Faction f = town(speaker);
		if (f == null || !keeps(speaker)) {
			return;
		}
		ServerLevel level = (ServerLevel)speaker.level();
		if (Reputation.get(player, f.id()) <= Reputation.HOSTILE) {
			Speech.tell(level, speaker, player, Component.translatable("talk.jaymoskingdoms.ledger.refused"), Speech.RED);
			return;
		}
		ItemStack book = book(level.getServer(), f, speaker.getName().getString());
		// (an older copy of this town's ledger is swapped for the new one)
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (ledgerOf(inventory.getItem(i)) == f.id()) {
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}
		if (!inventory.add(book)) {
			player.drop(book, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		Speech.tell(level, speaker, player, Component.translatable("talk.jaymoskingdoms.ledger.given", f.name()), Speech.GOLD);
	}

	/** The town {@code stack} is a ledger of, or -1. */
	private static int ledgerOf(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return stack.is(Items.WRITTEN_BOOK) && data != null ? data.copyTag().getIntOr(TAG, -1) : -1;
	}

	/** The ledger of {@code f} as a written book, signed by {@code keeper}. */
	static ItemStack book(MinecraftServer server, Faction f, String keeper) {
		List<Component> pages = new ArrayList<>();
		pack(pages, null, KingdomCommands.ledger(server, f));
		List<Component> troubles = new ArrayList<>();
		for (String kind : TownTroubles.of(server, f.id())) {
			troubles.add(Component.translatable("trouble.jaymoskingdoms." + kind + ".start", f.name()));
		}
		pack(pages, "troubles", troubles);
		pack(pages, "works", TownProjects.ledger(server, f));
		List<Component> treaties = new ArrayList<>();
		int head = Treaties.head(server, f).id();
		for (Treaties.Treaty t : Treaties.of(server, f)) {
			Treaties.Kind kind = t.kindOf();
			Faction other = Factions.get(server, t.other(head));
			if (kind != null && other != null) {
				treaties.add(Component.translatable("ledger.jaymoskingdoms.treaty", kind.named(), other.name(), Treaties.daysLeft(server, t)));
			}
		}
		pack(pages, "treaties", treaties);
		ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
		stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Ledger"), keeper, 0,
			pages.stream().map(Filterable::passThrough).toList(), true));
		stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.town_ledger", f.name())
			.withStyle(style -> style.withItalic(false)));
		CompoundTag tag = new CompoundTag();
		tag.putInt(TAG, f.id());
		stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return stack;
	}

	/**
	 * Lays {@code lines} on fresh pages after {@code pages}, under a heading ({@code ledger.jaymoskingdoms.<heading>}, none
	 * if null), as many to a page as fit; a heading with nothing under it says so.
	 */
	private static void pack(List<Component> pages, @Nullable String heading, List<Component> lines) {
		MutableComponent page = Component.empty();
		int used = 0;
		if (heading != null) {
			page.append(Component.translatable("ledger.jaymoskingdoms." + heading).withStyle(ChatFormatting.BOLD)).append("\n\n");
			used = 20;
			if (lines.isEmpty()) {
				pages.add(page.append(Component.translatable("ledger.jaymoskingdoms." + heading + ".none")));
				return;
			}
		}
		for (Component line : lines) {
			int length = line.getString().length() + 2;
			if (used > 0 && used + length > PAGE_CHARS) {
				pages.add(page);
				page = Component.empty();
				used = 0;
			}
			page.append(line).append("\n\n");
			used += length;
		}
		if (used > 0) {
			pages.add(page);
		}
	}
}
