package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * (Smithing) Who made it, and what the maker's craft is worth. A hammered result is marked "Forged by <name>" (the
 * smith's display name, which on this pack is their character's name; a costume or mask names them as it does
 * anywhere) and the mark rides from the head onto the tool made from it. A Blacksmith's rank from Jaymo's Kingdoms
 * (it tags the player {@code jaymoskingdoms.smith_rank_1} for a Journeyman, {@code _2} for a Master) widens the perfect
 * zone of the hammering bar. Kingdoms also listens for finished forgings ({@link #FORGED_LISTENERS}) to count them as
 * smith work.
 */
public final class Provenance {
	/** The maker's name, on a forged head and on what is made from it. */
	public static DataComponentType<String> FORGED_BY;

	/** Tags Jaymo's Kingdoms puts on a player for their Blacksmith rank. */
	public static final String JOURNEYMAN_TAG = "jaymoskingdoms.smith_rank_1", MASTER_TAG = "jaymoskingdoms.smith_rank_2";
	/** How much wider the perfect zone is for a Journeyman and a Master Blacksmith (bar units, half-width). */
	public static final float JOURNEYMAN_BONUS = 1.0F, MASTER_BONUS = 2.0F;

	/** Told of every finished forging: the smith and the result. Jaymo's Kingdoms adds one (by reflection) for smith work. */
	public static final List<BiConsumer<ServerPlayer, ItemStack>> FORGED_LISTENERS = new ArrayList<>();

	private Provenance() {
	}

	static void register() {
		FORGED_BY = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("forged_by"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());
		Forging.FINISHES.add((smith, anvil, option, result) -> {
			result.set(FORGED_BY, smith.getDisplayName().getString());
			for (BiConsumer<ServerPlayer, ItemStack> listener : FORGED_LISTENERS) {
				listener.accept(smith, result);
			}
		});
		Grades.CARRIERS.add((head, made) -> {
			String name = head.get(FORGED_BY);
			if (name != null) {
				made.set(FORGED_BY, name);
			}
		});
		Forging.ZONE_BONUS.add(Provenance::rankBonus);
	}

	/** The perfect zone's widening for this smith's Blacksmith rank. */
	public static double rankBonus(ServerPlayer smith) {
		var tags = smith.entityTags();
		return tags.contains(MASTER_TAG) ? MASTER_BONUS : tags.contains(JOURNEYMAN_TAG) ? JOURNEYMAN_BONUS : 0.0;
	}
}
