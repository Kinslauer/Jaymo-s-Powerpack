package com.jaymoskitchen.butchery;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/**
 * The organ a hunter carves out of a grown monster's body (Hunter's Road, patch 6, "D3"): the table of which monster gives
 * which organ, read from {@code data/<namespace>/monster_organs/*.json} ({@code {"entity": "ns:monster", "organ": "ns:item"}}).
 * A monster that is in the table and lies as a body is gutted with a skinning knife like a spider is
 * ({@code corpse/Gutting}): the cut, the opening, and then its organ, laid beside it. Nothing here touches loot tables, so
 * the organ never drops from a kill, only from the knife. A baby has none. A patch that adds a monster adds a file here for
 * it; an entry whose entity or item isn't in the game (a monster of a mod that isn't loaded) is left out.
 */
public final class MonsterOrgans extends SimpleJsonResourceReloadListener<MonsterOrgans.Entry> {
	public record Entry(Identifier entity, Identifier organ) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("entity").forGetter(Entry::entity),
			Identifier.CODEC.fieldOf("organ").forGetter(Entry::organ)).apply(i, Entry::new));
	}

	private static volatile Map<Identifier, Identifier> table = Map.of();

	private MonsterOrgans() {
		super(Entry.CODEC, FileToIdConverter.json("monster_organs"));
	}

	static void register() {
		ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(JaymosKitchen.id("monster_organs"), new MonsterOrgans());
	}

	@Override
	protected void apply(Map<Identifier, Entry> loaded, ResourceManager manager, ProfilerFiller profiler) {
		Map<Identifier, Identifier> next = new HashMap<>();
		for (Entry entry : loaded.values()) {
			if (BuiltInRegistries.ENTITY_TYPE.containsKey(entry.entity()) && BuiltInRegistries.ITEM.containsKey(entry.organ())) {
				next.put(entry.entity(), entry.organ());
			}
		}
		table = Map.copyOf(next);
	}

	/** The organ carved from a monster of {@code type}, or null if the hunters carve nothing from it. */
	public static @Nullable Item organOf(@Nullable EntityType<?> type) {
		if (type == null || table.isEmpty()) {
			return null;
		}
		Identifier organ = table.get(BuiltInRegistries.ENTITY_TYPE.getKey(type));
		return organ == null ? null : BuiltInRegistries.ITEM.getOptional(organ).orElse(null);
	}
}
