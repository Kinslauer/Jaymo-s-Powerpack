package com.jaymoskitchen.mixin;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reaches a village's template pool, to slip a house of ours in among the vanilla ones ({@code DelightVillages}). */
@Mixin(StructureTemplatePool.class)
public interface StructureTemplatePoolAccessor {
	/** What a pool draws pieces from: a piece of weight 3 is in it three times. */
	@Accessor("templates")
	ObjectArrayList<StructurePoolElement> jaymoskitchen$templates();

	@Accessor("rawTemplates")
	List<Pair<StructurePoolElement, Integer>> jaymoskitchen$rawTemplates();

	@Mutable
	@Accessor("rawTemplates")
	void jaymoskitchen$setRawTemplates(List<Pair<StructurePoolElement, Integer>> templates);
}
