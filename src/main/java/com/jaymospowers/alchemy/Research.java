package com.jaymospowers.alchemy;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * The apothecary's research: entries in the Codex a player unlocks by spending insight (essence knowledge gathered
 * with the Scrying Lens). Each part of alchemy registers its own entries in its init; recipes and gear check
 * {@link Knowledge#knows} before they work for a player.
 */
public final class Research {
	/** The Codex's tabs, in order. */
	public enum Category {
		BASICS, CRUCIBLE, INFUSION, BLIGHT, ARTIFICE, ENCHANTING
	}

	/**
	 * @param id         unique, e.g. {@code "crucible"}; title and text are {@code research.jaymospowers.<id>} and {@code .text}
	 * @param icon       shown in the Codex
	 * @param cost       insight spent to unlock it
	 * @param parents    entries that must be known first
	 * @param corruption total corruption needed before it even shows (forbidden lore; 0 for most)
	 * @param col        position on its tab's grid, column
	 * @param row        and row
	 */
	public record Entry(String id, Category category, Supplier<Item> icon, EssenceList cost, List<String> parents, int corruption,
		int col, int row) {
		public String key() {
			return "research.jaymospowers." + id;
		}
	}

	private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

	private Research() {
	}

	public static Entry register(Entry entry) {
		if (ENTRIES.putIfAbsent(entry.id(), entry) != null) {
			throw new IllegalStateException("Duplicate research " + entry.id());
		}
		return entry;
	}

	public static @Nullable Entry get(String id) {
		return ENTRIES.get(id);
	}

	public static Collection<Entry> all() {
		return Collections.unmodifiableCollection(ENTRIES.values());
	}
}
