package com.jaymoskitchen.farm;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
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
 * its food once a day, and brushing it once a day with a brush, each make it a little more familiar ({@link #PER_FEED},
 * {@link #PER_BRUSH}, up to 1, saved on it and shown to everyone: the client draws its hearts under the crosshair when you
 * look at it). Only one at least {@link #BREEDS} familiar can be bred: before that, the food goes to winning it over.
 * Familiar hens lay faster ({@link LayingHens}), a familiar sheep gives an extra wool when shorn, and a familiar cow or goat
 * gives an extra bucket of milk now and then ({@link #MILK_BONUS}, more the more familiar). A day it isn't looked after,
 * nothing is lost.
 */
public final class Familiarity {
	/** Familiarity a day's feeding adds, and what it takes to breed. */
	static final float PER_FEED = 0.2F, PER_BRUSH = 0.15F;
	public static final float BREEDS = 0.6F;
	/** The chance of a second bucket of milk from a fully familiar cow or goat (less the less familiar). */
	static final float MILK_BONUS = 0.6F;
	/** How much faster a fully familiar hen's egg timer runs. */
	public static final float HEN_BONUS = 0.5F;

	public static final TagKey<EntityType<?>> FARM_ANIMALS = TagKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("farm_animals"));
	/** How familiar it is (0 to 1). Saved on the animal. */
	public static final AttachmentType<Float> FAMILIAR = AttachmentRegistry.create(JaymosKitchen.id("familiar"),
		b -> b.persistent(Codec.FLOAT).syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.all()));
	/** The day it was last fed by hand. Saved on the animal. */
	public static final AttachmentType<Long> FED = AttachmentRegistry.create(JaymosKitchen.id("familiar_fed"), b -> b.persistent(Codec.LONG));
	/** The day it was last brushed. Saved on the animal. */
	public static final AttachmentType<Long> BRUSHED = AttachmentRegistry.create(JaymosKitchen.id("familiar_brushed"), b -> b.persistent(Codec.LONG));

	private Familiarity() {
	}

	public static void register() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(entity instanceof Animal animal) || !animal.getType().builtInRegistryHolder().is(FARM_ANIMALS) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getItemInHand(hand);
			if (held.is(Items.BRUSH)) {
				return brush(player, level, hand, animal, held);
			}
			if (held.is(Items.BUCKET) && !animal.isBaby() && (animal instanceof Cow || animal instanceof Goat) && level instanceof ServerLevel server
				&& server.getRandom().nextFloat() < MILK_BONUS * of(animal)) {
				// a familiar one lets down more: a second bucket's worth, at its feet, beside the one the game fills
				animal.spawnAtLocation(server, new ItemStack(Items.MILK_BUCKET));
				server.sendParticles(ParticleTypes.HEART, animal.getX(), animal.getY() + animal.getBbHeight(), animal.getZ(), 2, 0.3, 0.2, 0.3, 0.0);
			}
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

	/** A brush on a farm animal: once a day it grows a little more familiar. */
	private static InteractionResult brush(Player player, Level level, InteractionHand hand, Animal animal, ItemStack brush) {
		long today = level.getOverworldClockTime() / 24000L;
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		server.playSound(null, animal.blockPosition(), SoundEvents.BRUSH_GENERIC, SoundSource.NEUTRAL, 0.8F, 1.0F);
		if (animal.getAttachedOrElse(BRUSHED, -1L) == today) {
			server.sendParticles(ParticleTypes.HAPPY_VILLAGER, animal.getX(), animal.getY() + animal.getBbHeight(), animal.getZ(), 1, 0.3, 0.2, 0.3, 0.0);
			player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.familiar.brushed"));
			return InteractionResult.SUCCESS_SERVER;
		}
		float familiar = Math.min(1.0F, of(animal) + PER_BRUSH);
		animal.setAttached(FAMILIAR, familiar);
		animal.setAttached(BRUSHED, today);
		brush.hurtAndBreak(1, player, hand);
		server.sendParticles(ParticleTypes.HEART, animal.getX(), animal.getY() + animal.getBbHeight(), animal.getZ(), 4, 0.3, 0.2, 0.3, 0.0);
		player.sendOverlayMessage(Component.translatable("message.jaymoskitchen.familiar.more", Math.round(familiar * 100)));
		return InteractionResult.SUCCESS_SERVER;
	}

	/** How familiar {@code animal} is, 0 to 1. */
	public static float of(Animal animal) {
		return animal.getAttachedOrElse(FAMILIAR, 0.0F);
	}
}
