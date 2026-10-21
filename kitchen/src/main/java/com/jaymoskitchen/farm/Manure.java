package com.jaymoskitchen.farm;

import com.jaymoskitchen.JaymosKitchen;
import com.mojang.serialization.Codec;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders;

/**
 * (Better With Mods' dung and fertilizer, MIT; our own code and art.) Farm animals that have been fed or bred (BWM only
 * had fed wolves; we widen it to the {@code jaymoskitchen:manure_makers} tag) now and then leave a pile of manure, at
 * most one each per day, and only while someone is nearby to see it. Manure on the ground draws a few flies. It goes in
 * the composter, tans leather in place of bark ({@code jaymoskitchen:tanning_agents}), and with bone meal makes
 * fertilizer, which turns farmland into fertile farmland ({@link FertileFarmlandBlock}).
 */
public final class Manure {
	/** Animals that make manure (data/jaymoskitchen/tags/entity_type/manure_makers.json). */
	public static final TagKey<EntityType<?>> MANURE_MAKERS = TagKey.create(Registries.ENTITY_TYPE, JaymosKitchen.id("manure_makers"));
	/** Average ticks between piles for a fed adult (15 minutes); one a day at most, whatever the dice say. */
	static final int MANURE_TICKS = 18000;
	/** At most one pile per animal in this many ticks (an in-game day). */
	static final long DAY_TICKS = 24000L;
	/** How often animals are looked over, and how near a player they have to be (blocks). */
	static final int CHECK_EVERY = 100;
	static final double RANGE = 48.0;
	/** How often manure on the ground shows a fly or two, the chance each pile does, and how near a player. */
	static final int FLIES_EVERY = 10;
	static final float FLIES_CHANCE = 0.4F;
	static final double FLIES_RANGE = 16.0;
	/** The chance a random tick of fertile farmland gives the crop on it an extra growth tick. */
	static final float FERTILE_BONUS = 0.5F;
	/** Each growth step of a crop on fertile farmland has 1 in this many chances to use it up (back to farmland). */
	static final int SPENT_ODDS = 150;

	/** Fed but never yet made a pile. */
	private static final long FED = Long.MIN_VALUE / 2;
	/**
	 * On a fed or bred animal: the game time it last left manure ({@link #FED} if never). Absent: not fed yet, no manure.
	 */
	public static final AttachmentType<Long> MANURE_TIME = AttachmentRegistry.create(JaymosKitchen.id("manure_time"),
		builder -> builder.persistent(Codec.LONG));

	public static Item MANURE;
	public static Item FERTILIZER;
	public static FertileFarmlandBlock FERTILE_FARMLAND;

	private Manure() {
	}

	public static void register() {
		MANURE = item("manure", Item::new, new Item.Properties().compostable(ContextIntProviders.COMPOSTABLE_MEDIUM));
		FERTILIZER = item("fertilizer", FertilizerItem::new, new Item.Properties());
		ResourceKey<Block> farmland = ResourceKey.create(Registries.BLOCK, JaymosKitchen.id("fertile_farmland"));
		FERTILE_FARMLAND = Registry.register(BuiltInRegistries.BLOCK, farmland, new FertileFarmlandBlock(
			BlockBehaviour.Properties.ofFullCopy(Blocks.FARMLAND).mapColor(MapColor.TERRACOTTA_BROWN).setId(farmland)));

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> {
			for (Item item : List.of(MANURE, FERTILIZER)) {
				output.accept(new ItemStack(item));
			}
		});

		// fed by hand (food it eats, babies too): from now on it makes manure once grown
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!level.isClientSide() && !player.isSpectator() && entity instanceof Animal animal && animal.is(MANURE_MAKERS)
				&& animal.isFood(player.getItemInHand(hand))) {
				fed(animal);
			}
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(Manure::tick);
	}

	private static Item item(String id, Function<Item.Properties, Item> factory, Item.Properties properties) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKitchen.id(id));
		return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(properties.setId(key)));
	}

	private static void fed(Animal animal) {
		if (!animal.hasAttached(MANURE_TIME)) {
			animal.setAttached(MANURE_TIME, FED);
		}
	}

	private static void tick(MinecraftServer server) {
		int now = server.getTickCount();
		boolean animals = now % CHECK_EVERY == 0, flies = now % FLIES_EVERY == 0;
		if (!animals && !flies) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.players().isEmpty()) {
				continue;
			}
			if (animals) {
				animals(level);
			}
			if (flies) {
				flies(level);
			}
		}
	}

	/** Each fed adult near a player: now and then, a pile (once a day at most). Animals in love count as fed. */
	private static void animals(ServerLevel level) {
		Set<Animal> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		long time = level.getGameTime();
		float chance = (float) CHECK_EVERY / MANURE_TICKS;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			for (Animal animal : level.getEntitiesOfClass(Animal.class, player.getBoundingBox().inflate(RANGE),
				a -> a.isAlive() && a.is(MANURE_MAKERS))) {
				if (!seen.add(animal)) {
					continue;
				}
				if (animal.isInLove()) {
					fed(animal);
				}
				Long last = animal.getAttached(MANURE_TIME);
				if (last == null || animal.isBaby() || time - last < DAY_TICKS || level.getRandom().nextFloat() >= chance) {
					continue;
				}
				animal.setAttached(MANURE_TIME, time);
				ItemEntity pile = animal.spawnAtLocation(level, new ItemStack(MANURE));
				if (pile != null) {
					pile.setPickUpDelay(40);
				}
				level.playSound(null, animal.getX(), animal.getY(), animal.getZ(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.NEUTRAL,
					0.5F, 0.6F + level.getRandom().nextFloat() * 0.2F);
			}
		}
	}

	/** A faint fly or two over manure lying on the ground near a player. */
	private static void flies(ServerLevel level) {
		Set<ItemEntity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		for (ServerPlayer player : level.players()) {
			for (ItemEntity pile : level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(FLIES_RANGE),
				e -> e.onGround() && e.getItem().is(MANURE))) {
				if (seen.add(pile) && level.getRandom().nextFloat() < FLIES_CHANCE) {
					level.sendParticles(ParticleTypes.MYCELIUM, pile.getX(), pile.getY() + 0.35, pile.getZ(), 1, 0.2, 0.15, 0.2, 0.0);
				}
			}
		}
	}
}
