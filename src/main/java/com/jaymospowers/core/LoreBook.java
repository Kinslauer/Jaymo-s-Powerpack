package com.jaymospowers.core;

import com.jaymospowers.JaymosPowers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * A short written book Powers leaves in the world for the Retaking (a raider band's tally, a demon muster-roll). Its pages
 * are lang keys {@code book.jaymospowers.<name>.page1..n}, and it carries Jaymo's Kingdoms' lore id
 * ({@code jaymoskingdoms_lore} = {@code jaymospowers:<name>}) so the lore codex files it like any lore book.
 */
public final class LoreBook {
	private LoreBook() {
	}

	/** The book {@code name}, titled {@code title}, with {@code pages} pages. */
	public static ItemStack written(String name, String title, int pages) {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		List<Filterable<Component>> text = new ArrayList<>();
		for (int i = 1; i <= pages; i++) {
			text.add(Filterable.passThrough(Component.translatable("book.jaymospowers." + name + ".page" + i)));
		}
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), "", 1, text, true));
		CompoundTag tag = new CompoundTag();
		tag.putString("jaymoskingdoms_lore", JaymosPowers.id(name).toString());
		book.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return book;
	}
}
