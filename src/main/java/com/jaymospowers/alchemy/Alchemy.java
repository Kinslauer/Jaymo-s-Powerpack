package com.jaymospowers.alchemy;

import com.jaymospowers.JaymosPowers;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Jaymo's Alchemy: an apothecary's craft, our own remake of the ideas in Thaumcraft and Thaumic Tinkerer (both all
 * rights reserved, so nothing of theirs is used; see docs/Alchemy-Plan.md). Everything is made of {@link Essence}s.
 * <ol>
 * <li>{@code essence/} and {@code codex/}: what each thing is made of, the Scrying Lens and the Apothecary's Codex
 * (research).</li>
 * <li>{@code crucible/}: the crucible (things dissolve into essence; a catalyst turns essence into something new), the
 * alembic and essence jars.</li>
 * <li>{@code flux/}: what alchemy leaves behind ({@link Flux}): blight on the land, {@link Corruption} in people.</li>
 * <li>{@code infusion/}: the infusion altar, its pedestals and its instability.</li>
 * <li>{@code gear/}: essence enchanting and the artificer's gear (the rod and its foci, ichor gear...).</li>
 * </ol>
 * Shared here: the essence component and phials, the creative tab, and the init order.
 */
public final class Alchemy {
	/** What essence an item holds: phials, and anything else that carries essence. */
	public static DataComponentType<EssenceList> ESSENCES;
	public static Item PHIAL, ESSENCE_PHIAL;
	public static final ResourceKey<CreativeModeTab> TAB = ResourceKey.create(Registries.CREATIVE_MODE_TAB, JaymosPowers.id("alchemy"));

	private Alchemy() {
	}

	public static void init() {
		ESSENCES = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("essences"),
			DataComponentType.<EssenceList>builder().persistent(EssenceList.CODEC).networkSynchronized(EssenceList.STREAM_CODEC).build());
		PHIAL = item("phial", p -> new Item(p.stacksTo(16)));
		ESSENCE_PHIAL = item("essence_phial", p -> new Phials.EssencePhialItem(p.stacksTo(16)));
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, TAB, FabricCreativeModeTab.builder()
			.title(Component.translatable("itemGroup.jaymospowers.alchemy"))
			.icon(() -> new ItemStack(ESSENCE_PHIAL))
			.build());
		CreativeModeTabEvents.modifyOutputEvent(TAB).register(output -> {
			output.accept(PHIAL);
			for (Essence e : Essence.values()) {
				output.accept(Phials.of(e, Phials.SIZE));
			}
		});
		// each part registers its own blocks, items, research and events
		com.jaymospowers.alchemy.essence.Essences.init();
		com.jaymospowers.alchemy.codex.Codex.init();
		com.jaymospowers.alchemy.crucible.Crucibles.init();
		com.jaymospowers.alchemy.flux.FluxEffects.init();
		com.jaymospowers.alchemy.infusion.Infusion.init();
		com.jaymospowers.alchemy.gear.AlchemyGear.init();
	}

	/** Registers an item and adds it to the Alchemy tab. */
	public static Item item(String id, Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(id));
		Item item = Registry.register(BuiltInRegistries.ITEM, key, factory.apply(new Item.Properties().setId(key)));
		CreativeModeTabEvents.modifyOutputEvent(TAB).register(output -> output.accept(item));
		return item;
	}
}
