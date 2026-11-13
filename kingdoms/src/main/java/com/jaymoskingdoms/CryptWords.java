package com.jaymoskingdoms;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * What is written and said about a great necromancer's vessel ({@link Necropolis}): two journals, and the talk of the nearby
 * villagers. Nothing is ever announced; a reader has to find the book or listen. The sexton's tally (in the barrel by the
 * crypt's hatch) says which way the vessel lies and about how far; the Hollow Man's own page (in the chest in the crypt)
 * says how deep and what marks the place. Villagers know the crypt is there and that he can't die while his heart is hid.
 */
final class CryptWords {
	static final String[] POINTS = {"east", "southeast", "south", "southwest", "west", "northwest", "north", "northeast"};

	private CryptWords() {
	}

	/** The compass point from {@code from} to {@code to}, as a translatable word. */
	static Component point(BlockPos from, BlockPos to) {
		double angle = Math.atan2(to.getZ() - from.getZ(), to.getX() - from.getX());
		int i = (int)Math.round(angle / (Math.PI / 4.0));
		return Component.translatable("direction.jaymoskingdoms." + POINTS[Math.floorMod(i, 8)]);
	}

	/** A distance said the way a sexton would: to the nearest {@code step} blocks. */
	static int paces(BlockPos from, BlockPos to, int step) {
		double d = Math.sqrt(from.distSqr(new BlockPos(to.getX(), from.getY(), to.getZ())));
		return Math.max(step, (int)(Math.round(d / step) * step));
	}

	private static ItemStack book(String id, String title, String author, Component name, List<Component> pages) {
		ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
		book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), author, 0,
			pages.stream().map(Filterable::passThrough).toList(), true));
		book.set(DataComponents.CUSTOM_NAME, name);
		LoreBooks.tag(book, "journal:crypt:" + id);
		return book;
	}

	/** The sexton's tally: which way and how far to the vessel from the hatch. */
	static ItemStack tally(String id, BlockPos hatch, BlockPos vessel) {
		return book(id + ":tally", "The Sexton's Tally", "Brother Wen", Component.translatable("journal.jaymoskingdoms.sexton.title"), List.of(
			Component.translatable("journal.jaymoskingdoms.sexton.1"),
			Component.translatable("journal.jaymoskingdoms.sexton.2", point(hatch, vessel), paces(hatch, vessel, 10)),
			Component.translatable("journal.jaymoskingdoms.sexton.3")));
	}

	/** The Hollow Man's own page: how deep, and what marks the place. */
	static ItemStack page(String id, BlockPos cairn, BlockPos vessel) {
		return book(id + ":page", "A Page in a Black Hand", "Halvard", Component.translatable("journal.jaymoskingdoms.hollow.title"), List.of(
			Component.translatable("journal.jaymoskingdoms.hollow.1"),
			Component.translatable("journal.jaymoskingdoms.hollow.2", Math.max(1, cairn.getY() - vessel.getY())),
			Component.translatable("journal.jaymoskingdoms.hollow.3")));
	}

	/** What a villager says to someone near, about a crypt that lies {@code toCrypt} way and about {@code far} blocks off, its vessel {@code toVessel} of it. */
	static Component gossip(int kind, BlockPos here, BlockPos crypt, BlockPos vessel) {
		return switch (kind) {
			case 0 -> Component.translatable("talk.jaymoskingdoms.crypt_gossip.0", point(here, crypt), paces(here, crypt, 50));
			case 1 -> Component.translatable("talk.jaymoskingdoms.crypt_gossip.1");
			default -> Component.translatable("talk.jaymoskingdoms.crypt_gossip.2", point(crypt, vessel));
		};
	}
}
