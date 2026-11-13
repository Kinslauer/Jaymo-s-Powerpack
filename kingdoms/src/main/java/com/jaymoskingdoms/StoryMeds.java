package com.jaymoskingdoms;

import com.jaymoskingdoms.LifeStory.Start;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * (0.7.503) Andy: "everyone should start with applicable meds. obviously still none for rock bottom, and a whole medkit
 * for sworn". What medicine (Jaymo's Powers' medieval kit, {@code MedicineItems}) each life-story start wakes with,
 * fitting how it came:
 * <ul>
 * <li>Sworn: a realm's issue, a healer's satchel packed full ({@link #MEDKIT}).</li>
 * <li>Nothing for those stripped of everything (rock bottom, a captive, chained among captives), an android (oil and
 * ingots mend them, not bandages) or the afterlife's Divine-born.</li>
 * <li>Everyone else a little, by their road: rags for the poor, linen where there was a home or a ship's stores, the
 * remedy their trouble calls for (burn salve from the fire, a splint for the fall, yarrow for the cave's bite).</li>
 * <li>A Survivalist knows to weave grass into a dressing: two more, whatever the road.</li>
 * </ul>
 * Items are looked up by id, so without Powers there are none.
 */
final class StoryMeds {
	/** Powers' medicine ids. */
	static final String BANDAGE = "jaymospowers:linen_bandage", GRASS = "jaymospowers:grass_bandage", RAG = "jaymospowers:rag",
		CLEAN_RAG = "jaymospowers:clean_rag", SPLINT = "jaymospowers:splint", NEEDLE = "jaymospowers:needle_and_thread",
		SALTS = "jaymospowers:smelling_salts", YARROW = "jaymospowers:yarrow_powder", KNITBONE = "jaymospowers:knitbone_poultice",
		SALVE = "jaymospowers:burn_salve", GARLIC = "jaymospowers:garlic_poultice", WILLOW = "jaymospowers:willow_bark_tea",
		POPPY = "jaymospowers:poppy_syrup", SATCHEL = "jaymospowers:healers_satchel";

	/** What a sworn guard's satchel holds (id, count): the whole kit. */
	static final List<Need> MEDKIT = List.of(new Need(BANDAGE, 4), new Need(CLEAN_RAG, 2), new Need(SPLINT, 2), new Need(NEEDLE, 1),
		new Need(SALTS, 1), new Need(YARROW, 2), new Need(KNITBONE, 2), new Need(SALVE, 1), new Need(GARLIC, 1), new Need(WILLOW, 2),
		new Need(POPPY, 1));

	/** Grass dressings a Survivalist weaves before they set out. */
	static final int SURVIVALIST_GRASS = 2;

	record Need(String id, int count) {
	}

	private StoryMeds() {
	}

	/**
	 * The medicine {@code player} wakes with: {@code start} the start the story chose (Sworn's kit comes even if its
	 * realm couldn't be found), {@code landed} where they woke.
	 */
	static List<ItemStack> of(ServerPlayer player, Start start, Start landed, Classes.PlayerClass cls) {
		List<ItemStack> out = new ArrayList<>();
		if (start == Start.SWORN) {
			ItemStack satchel = stack(SATCHEL, 1);
			List<ItemStack> inside = new ArrayList<>();
			for (Need n : MEDKIT) {
				ItemStack s = stack(n.id, n.count);
				if (!s.isEmpty()) {
					inside.add(s);
				}
			}
			if (satchel.isEmpty()) {
				out.addAll(inside);
			} else {
				satchel.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(inside));
				out.add(satchel);
			}
			return out;
		}
		if (landed == Start.ROCK_BOTTOM || landed == Start.CAPTIVE || landed == Start.CHAINED || landed == Start.ANDROID
			|| landed == Start.AFTERLIFE || PowersCompat.android(player)) {
			return out;
		}
		for (Need n : road(landed)) {
			add(out, n.id, n.count);
		}
		if (cls == Classes.PlayerClass.RANGER) {
			add(out, GRASS, SURVIVALIST_GRASS);
		}
		return out;
	}

	/** A road's own few things. */
	private static List<Need> road(Start landed) {
		return switch (landed) {
			// a cell: one rag they let you keep
			case SENTENCED, EVICTED, CAST_OUT -> List.of(new Need(RAG, 1));
			// a geared road: a soldier's or a miner's dressings
			case BANISHED -> List.of(new Need(BANDAGE, 2), new Need(RAG, 1));
			case CAVE -> List.of(new Need(BANDAGE, 2), new Need(YARROW, 1));
			// a home, a ship's stores, a trader's goods
			case VILLAGE -> List.of(new Need(BANDAGE, 2), new Need(WILLOW, 1));
			case WHALERS -> List.of(new Need(BANDAGE, 2), new Need(NEEDLE, 1));
			case TRADER -> List.of(new Need(BANDAGE, 2), new Need(WILLOW, 1), new Need(SALVE, 1));
			// what the trouble calls for
			case BURNED -> List.of(new Need(SALVE, 2), new Need(RAG, 1));
			case SKYFALL -> List.of(new Need(SPLINT, 1), new Need(RAG, 2));
			case SNOWED -> List.of(new Need(RAG, 2), new Need(WILLOW, 1));
			case UNARMED -> List.of(new Need(CLEAN_RAG, 2));
			case HIDEOUT -> List.of(new Need(RAG, 2), new Need(POPPY, 1));
			case GREENSKIN -> List.of(new Need(RAG, 2), new Need(GARLIC, 1));
			// an alchemist's bench: a little of each remedy
			case LAB -> List.of(new Need(YARROW, 1), new Need(KNITBONE, 1), new Need(SALVE, 1), new Need(GARLIC, 1));
			// everyone else: a couple of rags
			default -> List.of(new Need(RAG, 2));
		};
	}

	private static void add(List<ItemStack> out, String id, int n) {
		ItemStack s = stack(id, n);
		if (!s.isEmpty()) {
			out.add(s);
		}
	}

	/** {@code n} of {@code id}, or empty if it isn't an item here. */
	private static ItemStack stack(String id, int n) {
		Optional<Holder.Reference<Item>> item = BuiltInRegistries.ITEM.get(Identifier.parse(id));
		return item.isPresent() ? new ItemStack(item.get().value(), n) : ItemStack.EMPTY;
	}
}
