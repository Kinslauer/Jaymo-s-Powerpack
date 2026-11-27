package com.jaymospowers.alchemy.hunter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Research;
import com.jaymospowers.ground.StoneAge;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The paste ladder (Hunter's Road, patch 6, "T-B4"; the idea of Toxony's, written clean-room), three steps, each a base for the
 * oils (patch 7), the vials of Jaymo's Kingdoms and the brews:
 * <ol>
 * <li><b>Poison paste</b>: a poison ingredient ground in the mortar ({@link #GROUND_FROM}), one for one.</li>
 * <li><b>Toxic paste</b>: poison paste dropped into a hot crucible holding venom, rot and fire
 * ({@code alchemy/crucible/toxic_paste.json}), once {@link #RESEARCH} is read.</li>
 * <li><b>Pure toxin</b>: toxic paste distilled in the Brewer's Alembic over heat, {@link #YIELD_LOSS} fewer than went in
 * ({@code BrewersAlembicEntity}).</li>
 * </ol>
 * Kingdoms' five poison vials are made from them ({@code recipe/*_vial.json} there).
 */
public final class Pastes {
	/** The research that opens the crucible step. */
	public static final String RESEARCH = "poison_pastes";
	/** Pastes lost in a distilling (as bottles are). */
	public static final int YIELD_LOSS = 1;
	/** Every paste, for oils and vials to ask (a data tag). */
	public static final TagKey<Item> PASTES = TagKey.create(Registries.ITEM, JaymosPowers.id("poison_pastes"));

	public static Item POISON_PASTE, TOXIC_PASTE, PURE_TOXIN;

	/** What the mortar grinds into poison paste. Kitchen's venom gland joins them when that mod has it. */
	private static final Item[] GROUND_FROM = {Items.POISONOUS_POTATO, Items.ROTTEN_FLESH, Items.WITHER_ROSE, Items.LILY_OF_THE_VALLEY,
		Items.CRIMSON_FUNGUS, Items.WARPED_FUNGUS};

	private Pastes() {
	}

	static void init() {
		POISON_PASTE = Alchemy.item("poison_paste", Item::new);
		TOXIC_PASTE = Alchemy.item("toxic_paste", Item::new);
		PURE_TOXIN = Alchemy.item("pure_toxin", Item::new);
		for (Item item : GROUND_FROM) {
			StoneAge.grinds().put(item, POISON_PASTE);
		}
		BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("jaymoskitchen", "venom_gland")).ifPresent(i -> StoneAge.grinds().put(i, POISON_PASTE));
		Research.register(new Research.Entry(RESEARCH, Research.Category.CRUCIBLE, () -> TOXIC_PASTE,
			EssenceList.of(Essence.VENOM, 8, Essence.ROT, 6).plus(Essence.FIRE, 4), List.of("crucible"), 0, 3, 2));
	}

	/** Whether the Brewer's Alembic distils {@code stack}: toxic paste into pure toxin. */
	public static boolean distillable(ItemStack stack) {
		return stack.is(TOXIC_PASTE);
	}

	/** Whether {@code stack} is already pure toxin (nothing more to distil). */
	public static boolean isPure(ItemStack stack) {
		return stack.is(PURE_TOXIN);
	}
}
