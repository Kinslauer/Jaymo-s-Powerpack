package com.jaymoskitchen.farm;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Familiar farm animals (Andy's TFC Hardrock picks). Feeding a farm animal (one of {@code #jaymoskitchen:farm_animals})
 * its food once a day makes it a little more familiar ({@link #PER_FEED}, up to 1, saved on it). Only one at least
 * {@link #BREEDS} familiar can be bred: before that, the food goes to winning it over. Familiar hens lay faster
 * ({@link LayingHens}) and a familiar sheep gives an extra wool when shorn. A day it isn't fed, nothing is lost.
 */
public final class Familiarity {
	/** Familiarity a day's feeding adds, and what it takes to breed. */
	static final float PER_FEED = 0.2F, BREEDS = 0.6F;
	/** How much faster a fully familiar hen's egg timer runs. */
	public static final float HEN_BONUS = 0.5F;

	public static final TagKey<EntityType<?>> FARM_ANIMALS = TagKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("farm_animals"));
	/** How familiar it is (0 to 1). Saved on the animal. */
	public static final AttachmentType<Float> FAMILIAR = AttachmentRegistry.create(JaymosKitchen.id("familiar"), b -> b.persistent(Codec.FLOAT));
	/** The day it was last fed by hand. Saved on the animal. */
	public static final AttachmentType<Long> FED = AttachmentRegistry.create(JaymosKitchen.id("familiar_fed"), b -> b.persistent(Codec.LONG));

	private Familiarity() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(entity instanceof Animal animal) || !animal.getType().builtInRegistryHolder().is(FARM_ANIMALS) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getItemInHand(hand);
			if (held.is(Items.SHEARS) && animal instanceof Sheep sheep && sheep.readyForShearing() && level instanceof ServerLevel server
				&& of(sheep) >= BREEDS) {
				Item wool = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(sheep.getColor().getSerializedName() + "_wool"));
				sheep.spawnAtLocation(server, new ItemStack(wool));
				return InteractionResult.PASS;
			}
			if (!animal.isFood(held)) {
				return InteractionResult.PASS;
			}
			long today = level.getOverworldClockTime() / 24000L;
			boolean fedToday = animal.getAttachedOrElse(FED, -1L) == today;
			float familiar = of(animal);
			if (!level.isClientSide() && !fedToday) {
				familiar = Math.min(1.0F, familiar + PER_FEED);
				animal.setAttached(FAMILIAR, familiar);
				animal.setAttached(FED, today);
			}
			if (animal.isBaby() || familiar >= BREEDS || player.getAbilities().instabuild) {
				// a baby still grows from it, and a familiar one breeds as ever
				return InteractionResult.PASS;
			}
			// not familiar enough to breed: the food goes to winning it over
			if (level instanceof ServerLevel server) {
				if (fedToday) {
					player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.familiar.fed"));
					return InteractionResult.FAIL;
				}
				held.consume(1, player);
				server.sendParticles(ParticleTypes.HAPPY_VILLAGER, animal.getX(), animal.getY() + animal.getBbHeight(), animal.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
				player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.familiar.more", Math.round(familiar * 100)));
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** How familiar {@code animal} is, 0 to 1. */
	public static float of(Animal animal) {
		return animal.getAttachedOrElse(FAMILIAR, 0.0F);
	}
}
