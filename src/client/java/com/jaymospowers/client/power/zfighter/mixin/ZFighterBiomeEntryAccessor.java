package com.jaymospowers.client.power.zfighter.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A Single Biome list entry's biome ({@link ZFighterBuffetListMixin}). */
@Mixin(targets = "net.minecraft.client.gui.screens.CreateBuffetWorldScreen$BiomeList$Entry")
public interface ZFighterBiomeEntryAccessor {
	@Accessor("biome")
	Holder.Reference<Biome> jaymospowers$biome();
}
