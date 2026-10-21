package com.jaymoskitchen.mill.machines;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;

/**
 * What the potter's wheel throws, from {@code data/<namespace>/potters_wheel/*.json}: an input block, the output block
 * it becomes, and how many turns of the wheel that takes. An entry whose output isn't in the game (Jaymo's Powers'
 * unfired pot, without Powers) is left out. Where one input has several entries, the highest {@code priority} is the
 * wheel's first pick and sneak-using the wheel steps through the rest.
 */
public final class PottersWheelRecipes extends SimpleJsonResourceReloadListener<PottersWheelRecipes.Entry> {
	/** Turns to throw one, where an entry doesn't say. */
	public static final int POTTERS_STEPS = 8;

	public record Entry(Identifier input, Identifier output, int steps, int priority) {
		static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
			Identifier.CODEC.fieldOf("input").forGetter(Entry::input),
			Identifier.CODEC.fieldOf("output").forGetter(Entry::output),
			Codec.intRange(1, 1000).optionalFieldOf("steps", POTTERS_STEPS).forGetter(Entry::steps),
			Codec.INT.optionalFieldOf("priority", 0).forGetter(Entry::priority)).apply(i, Entry::new));

		public Optional<Block> outputBlock() {
			return BuiltInRegistries.BLOCK.getOptional(output);
		}
	}

	private static List<Entry> entries = List.of();

	PottersWheelRecipes() {
		super(Entry.CODEC, FileToIdConverter.json("potters_wheel"));
	}

	@Override
	protected void apply(Map<Identifier, Entry> loaded, ResourceManager manager, ProfilerFiller profiler) {
		List<Entry> list = new ArrayList<>();
		loaded.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
			Entry entry = e.getValue();
			if (BuiltInRegistries.BLOCK.getOptional(entry.input()).isPresent() && entry.outputBlock().isPresent()) {
				list.add(entry);
			}
		});
		list.sort(Comparator.comparingInt(Entry::priority).reversed());
		entries = List.copyOf(list);
	}

	/** Everything this block can be thrown into, the first pick first. */
	public static List<Entry> forInput(Block block) {
		Identifier id = BuiltInRegistries.BLOCK.getKey(block);
		return entries.stream().filter(e -> e.input().equals(id)).toList();
	}
}
