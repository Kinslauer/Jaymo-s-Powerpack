package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;

/**
 * (0.7.490) Hordes, Orc war journals: the book an orc or a goblin leaves behind ({@link LoreBooks}). One item, with a greasy
 * leather look for an orc's scrawl and a green ledger look for a goblin's; its pages are written once, when it is made,
 * and kept on the stack as any written book's are. Using it opens it; the {@link LoreCodex} files it.
 */
public final class WarJournal extends Item {
	/** The title a journal carries. */
	static final String TITLE = "War Journal";

	public static Item ITEM;

	private WarJournal(Properties properties) {
		super(properties);
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("war_journal"));
		WrittenBookContent blank = new WrittenBookContent(Filterable.passThrough(TITLE), "", 0, List.of(), true);
		ITEM = Registry.register(BuiltInRegistries.ITEM, key, new WarJournal(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)
			.component(DataComponents.WRITTEN_BOOK_CONTENT, blank)
			.component(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.WRITTEN_BOOK_CONTENT, true))));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.insertAfter(Items.MAP, ITEM));
	}

	/** A journal of {@code brood} ({@link Dialects#ORC}, {@link Dialects#GOBLIN}) with these pages; {@code suffix} makes its id one of a kind. */
	static ItemStack make(String brood, String suffix, List<Component> pages) {
		ItemStack book = new ItemStack(ITEM);
		List<Filterable<Component>> sheets = new ArrayList<>();
		for (Component page : pages) {
			sheets.add(Filterable.passThrough(page));
		}
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(TITLE), "", 0, sheets, true));
		book.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.war_journal." + brood).withStyle(Style.EMPTY.withItalic(false)));
		if (Dialects.GOBLIN.equals(brood)) {
			book.set(DataComponents.ITEM_MODEL, JaymosKingdoms.id("goblin_ledger"));
		}
		LoreBooks.tag(book, "journal:" + brood + ":" + suffix);
		return book;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (player instanceof ServerPlayer sp) {
			ItemStack held = sp.getItemInHand(hand);
			sp.openItemGui(held, hand);
			level.playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.9F);
		}
		return InteractionResult.SUCCESS;
	}
}
