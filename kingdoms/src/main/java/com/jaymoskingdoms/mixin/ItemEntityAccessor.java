package com.jaymoskingdoms.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.item.ItemEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A dropped item's age and who threw it, so {@code Windfall} can let a forest's sticks and saplings go sooner. */
@Mixin(ItemEntity.class)
public interface ItemEntityAccessor {
	@Accessor("age")
	void jaymoskingdoms$age(int age);

	@Accessor("thrower")
	@Nullable EntityReference<Entity> jaymoskingdoms$thrower();
}
