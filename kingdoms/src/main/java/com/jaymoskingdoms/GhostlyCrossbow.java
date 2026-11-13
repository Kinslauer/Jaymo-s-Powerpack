package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Monsters;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;

/**
 * The ghostly crossbow (Andy's pick, Picks 5): a pale crossbow a wraith carries over from the hunt it died in. Its bolts
 * are lit as they fly (they glow, so a hunter can follow one into the dark) and are gone after the shot: they are never
 * left to pick up. It loads and draws like any crossbow. A wraith (not a flame wraith) lets it fall now and then
 * ({@link #DROP_CHANCE}); it is not crafted.
 */
public final class GhostlyCrossbow extends CrossbowItem {
	// ---- tuning ----
	/** The chance a slain wraith lets its crossbow fall. */
	static final float DROP_CHANCE = 0.06F;
	static final int DURABILITY = 380;

	public static Item ITEM;

	private GhostlyCrossbow(Item.Properties properties) {
		super(properties);
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("ghostly_crossbow"));
		ITEM = Registry.register(BuiltInRegistries.ITEM, key,
			new GhostlyCrossbow(new Item.Properties().setId(key).stacksTo(1).durability(DURABILITY).rarity(Rarity.RARE)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.insertAfter(Items.CROSSBOW, ITEM));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity.getType() == Monsters.WRAITH && entity.level() instanceof ServerLevel level
				&& level.getRandom().nextFloat() < DROP_CHANCE) {
				entity.spawnAtLocation(level, new ItemStack(ITEM));
			}
		});
	}

	@Override
	protected Projectile createProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack projectile, boolean isCrit) {
		Projectile bolt = super.createProjectile(level, shooter, weapon, projectile, isCrit);
		bolt.setGlowingTag(true);
		if (bolt instanceof AbstractArrow arrow) {
			arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		}
		if (!level.isClientSide()) {
			level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.PLAYERS, 0.5F, 1.4F);
		}
		return bolt;
	}
}
