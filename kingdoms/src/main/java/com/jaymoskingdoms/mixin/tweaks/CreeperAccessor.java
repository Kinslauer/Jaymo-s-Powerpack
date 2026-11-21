package com.jaymoskingdoms.mixin.tweaks;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.entity.monster.Creeper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets a golem's hit put a creeper's fuse out ({@code mobs.GolemHunt#defuse}). */
@Mixin(Creeper.class)
public interface CreeperAccessor {
	@Accessor("swell")
	void jaymoskingdoms$setSwell(int swell);

	@Accessor("oldSwell")
	void jaymoskingdoms$setOldSwell(int swell);

	@Accessor("DATA_IS_IGNITED")
	static EntityDataAccessor<Boolean> jaymoskingdoms$ignited() {
		throw new AssertionError();
	}
}
