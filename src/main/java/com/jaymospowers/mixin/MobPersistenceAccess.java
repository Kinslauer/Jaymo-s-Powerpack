package com.jaymospowers.mixin;

import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * (Blight mobs) Lets a blighted elite clear the "never despawn" flag that a custom name gives it, so named elites do
 * not pile up in a chunk nobody visits. Mob only exposes {@code setPersistenceRequired()} with no way back.
 */
@Mixin(Mob.class)
public interface MobPersistenceAccess {
	@Accessor("persistenceRequired")
	void jaymospowers$setPersistenceRequired(boolean required);
}
