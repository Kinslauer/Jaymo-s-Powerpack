package com.jaymoskingdoms.mixin.mca;

import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whose genes these are (a parent's, when a child's are combined from them). */
@Mixin(Genetics.class)
public interface McaGeneticsAccessor {
	@Accessor("entity")
	VillagerLike<?> jaymoskingdoms$entity();
}
