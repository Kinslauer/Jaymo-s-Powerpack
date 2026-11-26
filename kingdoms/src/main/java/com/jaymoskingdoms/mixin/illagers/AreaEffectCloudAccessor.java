package com.jaymoskingdoms.mixin.illagers;

import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.item.alchemy.PotionContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** An alchemist (illagers/Alchemist) clears its own cloud's poison off its allies. */
@Mixin(AreaEffectCloud.class)
public interface AreaEffectCloudAccessor {
	@Accessor("potionContents")
	PotionContents jaymoskingdoms$potionContents();
}
