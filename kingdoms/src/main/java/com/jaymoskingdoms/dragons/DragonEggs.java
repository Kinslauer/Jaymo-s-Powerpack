package com.jaymoskingdoms.dragons;

import com.jaymoskingdoms.JaymosKingdoms;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * (0.7.139) Dragon eggs: one per lesser wind dragon (notus, boreas, zephyrus, eurus), dropped by Aeolus when he falls.
 * Using one on a block hatches a hatchling of that kind right there, tamed to whoever used it (it grows on meat, see
 * {@link WindDragon}); the egg is spent unless the user is in creative.
 */
public final class DragonEggs {
	/** The lesser kinds, in the order Aeolus' winds turn. */
	public static final String[] SPECIES = {"notus", "boreas", "zephyrus", "eurus"};
	private static final Map<String, Item> EGGS = new LinkedHashMap<>();

	private DragonEggs() {
	}

	public static void register() {
		for (String species : SPECIES) {
			ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(species + "_dragon_egg"));
			EGGS.put(species, Registry.register(BuiltInRegistries.ITEM, key,
				new EggItem(species, new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.EPIC))));
		}
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.SPAWN_EGGS).register(output -> EGGS.values().forEach(output::accept));
	}

	/** The egg of a lesser kind ("notus", ...), one of it. */
	public static ItemStack egg(String species) {
		Item item = EGGS.get(species);
		return item == null ? ItemStack.EMPTY : new ItemStack(item);
	}

	private static EntityType<? extends WindDragon> type(String species) {
		return switch (species) {
			case "boreas" -> Dragons.BOREAS_DRAGON;
			case "zephyrus" -> Dragons.ZEPHYRUS_DRAGON;
			case "eurus" -> Dragons.EURUS_DRAGON;
			default -> Dragons.NOTUS_DRAGON;
		};
	}

	private static final class EggItem extends Item {
		private final String species;

		EggItem(String species, Item.Properties properties) {
			super(properties);
			this.species = species;
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			Level level = context.getLevel();
			if (!(level instanceof ServerLevel server) || !(context.getPlayer() instanceof ServerPlayer player)) {
				return InteractionResult.SUCCESS;
			}
			BlockPos at = context.getClickedPos().relative(context.getClickedFace());
			WindDragon dragon = type(this.species).create(server, EntitySpawnReason.SPAWN_ITEM_USE);
			if (dragon == null) {
				return InteractionResult.FAIL;
			}
			dragon.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
			dragon.finalizeSpawn(server, server.getCurrentDifficultyAt(at), EntitySpawnReason.SPAWN_ITEM_USE, null);
			// (finalizeSpawn makes it grown, so the hatchling's age comes after)
			dragon.setBaby(true);
			dragon.tameWithName(player);
			dragon.setPersistenceRequired();
			if (!server.noCollision(dragon)) {
				dragon.discard();
				return InteractionResult.FAIL;
			}
			server.addFreshEntity(dragon);
			server.playSound(null, at, SoundEvents.TURTLE_EGG_CRACK, SoundSource.NEUTRAL, 1.4F, 0.6F);
			server.playSound(null, at, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.NEUTRAL, 0.6F, 1.8F);
			server.sendParticles(ParticleTypes.POOF, at.getX() + 0.5, at.getY() + 0.4, at.getZ() + 0.5, 20, 0.3, 0.3, 0.3, 0.05);
			server.sendParticles(ParticleTypes.HEART, at.getX() + 0.5, at.getY() + 0.9, at.getZ() + 0.5, 5, 0.3, 0.3, 0.3, 0.0);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.dragon_egg.hatched", dragon.getDisplayName()));
			if (!player.isCreative()) {
				context.getItemInHand().shrink(1);
			}
			return InteractionResult.SUCCESS;
		}
	}
}
