package com.jaymoskingdoms;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;

/** A thrown bottle of gas ({@link Poisons}): it breaks where it lands into a cloud of the poison it was filled with. */
public final class GasBottle extends ThrowableItemProjectile {
	public static EntityType<GasBottle> TYPE;

	public GasBottle(EntityType<? extends GasBottle> type, Level level) {
		super(type, level);
	}

	public GasBottle(ServerLevel level, LivingEntity owner, ItemStack stack) {
		super(TYPE, owner, level, stack);
	}

	static void register() {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("gas_bottle"));
		TYPE = Registry.register(BuiltInRegistries.ENTITY_TYPE, key, EntityType.Builder.<GasBottle>of(GasBottle::new, MobCategory.MISC)
			.noLootTable().sized(0.25F, 0.25F).clientTrackingRange(4).updateInterval(10).build(key));
	}

	@Override
	protected Item getDefaultItem() {
		return Poisons.GAS_BOTTLE;
	}

	@Override
	protected void onHit(HitResult hit) {
		super.onHit(hit);
		if (this.level() instanceof ServerLevel level && !this.isRemoved()) {
			Poisons.Kind kind = Poisons.Kind.of(this.getItem().get(Poisons.KIND));
			Poisons.cloud(level, hit.getLocation(), kind == null ? Poisons.Kind.PLAIN : kind);
			this.discard();
		}
	}
}
