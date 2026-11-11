package com.jaymospowers.baubles;

import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.wounds.Downed;
import java.util.List;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStackTemplate;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * (0.20.12, Andy 2026-11-04: Paxo's Pink Fairy "added as a bauble, super rare, and highly prized possessions among
 * players and villagers alike"). Paxo's mod is All Rights Reserved and Forge 1.20.1 only, so this is our own:
 * <ul>
 * <li>A {@link PinkFairy} comes, very rarely, to the flowers of a forest (or a meadow or a cherry grove) at night, near
 * a player out there: {@link #SPAWN_ODDS} to one, every {@link #SPAWN_EVERY} ticks, so a night spent in the woods has
 * about a one in ten chance of one. Never more than one about ({@link #ALONE}).</li>
 * <li>Crept up on (sneaking) and caught in an empty glass bottle, she is a {@link #BOTTLED_FAIRY}: an epic bauble for the
 * amulet or charm slot, worth more than anything to the townsfolk (Kingdoms makes her their dearest gift and has them
 * remark on her).</li>
 * <li>Worn, she brings luck and a little health, and once turns a death aside: the blow that would have killed you
 * leaves you on {@link #SAVED_HEALTH} with regeneration, the bottle cracks ({@link #CRACKED_BOTTLE}) and she flies
 * free, gone for good. She saves you before you would go down ({@link Downed}), never from the void or /kill.</li>
 * <li>(0.20.12) Andy: "the villagers can wear it too". A villager given one (MCA's gift puts it in their inventory)
 * puts her on: she hangs at their chest as at a player's ({@link #WEARS}, drawn by {@code WornFairies}), glows about
 * them, and once saves them the same way.</li>
 * </ul>
 */
public final class Fairies {
	// ---- tuning ----
	/** How often (ticks) each player out in fairy country may come upon one, and the odds against it each time. */
	static final int SPAWN_EVERY = 200, SPAWN_ODDS = 550;
	/** How far round a fairy no other may come (blocks). */
	static final double ALONE = 128.0;
	/** Where she turns up: this near and this far from the player, at a flower; tries to find one. */
	static final int NEAR = 12, FAR = 28, TRIES = 16;
	/** What a bottled fairy gives worn: luck, and a heart. */
	static final double LUCK = 2.0, HEALTH = 2.0;
	/** A death she turns aside: the health you are left with, and the regeneration after (ticks, amplifier). */
	static final float SAVED_HEALTH = 6.0F;
	static final int SAVED_REGEN_TICKS = 200, SAVED_REGEN_LEVEL = 1;

	/** (0.20.12) How often (ticks) wearers are looked at; how near a player villagers are (who may put one on). */
	static final int SHOW_EVERY = 20;
	static final double VILLAGERS_NEAR = 32.0;

	/**
	 * (0.20.12) On a body: wears a bottled fairy, shown hung on the chest for everyone (synced). On a player it mirrors
	 * their bauble slots; on a villager (given one) it is the fairy itself, kept.
	 */
	public static final AttachmentType<Boolean> WEARS = AttachmentRegistry.create(JaymosPowers.id("wears_fairy"),
		builder -> builder.persistent(Codec.BOOL).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	public static EntityType<PinkFairy> PINK_FAIRY;
	public static Item BOTTLED_FAIRY;
	/** (0.20.12) What's left once she has saved someone: the bottle, cracked. */
	public static Item CRACKED_BOTTLE;
	/** Her sprite only (the renderer draws the fairy as it): not in any creative tab. */
	public static Item FAIRY_SPRITE;

	private Fairies() {
	}

	static void init() {
		ResourceKey<Item> spriteKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("pink_fairy"));
		FAIRY_SPRITE = Registry.register(BuiltInRegistries.ITEM, spriteKey, new Item(new Item.Properties().setId(spriteKey)));
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("bottled_fairy"));
		BOTTLED_FAIRY = Baubles.add(Registry.register(BuiltInRegistries.ITEM, key, new BottledFairyItem(new Item.Properties().setId(key).stacksTo(1)
			.rarity(Rarity.EPIC).component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true), List.of(
				new BaubleItem.Bonus(Attributes.LUCK, LUCK, AttributeModifier.Operation.ADD_VALUE),
				new BaubleItem.Bonus(Attributes.MAX_HEALTH, HEALTH, AttributeModifier.Operation.ADD_VALUE)))));

		ResourceKey<EntityType<?>> fairyKey = ResourceKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("pink_fairy"));
		PINK_FAIRY = Registry.register(BuiltInRegistries.ENTITY_TYPE, fairyKey, EntityType.Builder.of(PinkFairy::new, MobCategory.AMBIENT)
			.sized(0.35F, 0.35F).eyeHeight(0.18F).noLootTable().clientTrackingRange(8).build(fairyKey));
		FabricDefaultAttributeRegistry.register(PINK_FAIRY, PinkFairy.createAttributes());

		ResourceKey<Item> crackedKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("cracked_fairy_bottle"));
		CRACKED_BOTTLE = Registry.register(BuiltInRegistries.ITEM, crackedKey, new Item(new Item.Properties().setId(crackedKey).stacksTo(16)));

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			tick(server);
			if (server.getTickCount() % SHOW_EVERY == 0) {
				wearers(server);
			}
		});
		// (default phase: before Downed's, which runs last)
		ServerLivingEntityEvents.ALLOW_DEATH.register(Fairies::saveFromDeath);
		ServerLivingEntityEvents.AFTER_DEATH.register(Fairies::diedWearing);
	}

	// ---- coming upon one ---------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % SPAWN_EVERY != 0) {
			return;
		}
		ServerLevel level = server.overworld();
		if (!level.isDarkOutside()) {
			return;
		}
		RandomSource random = level.getRandom();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator() || random.nextInt(SPAWN_ODDS) != 0 || !fairyCountry(level.getBiome(player.blockPosition()))
				|| !level.getEntities(PINK_FAIRY, player.getBoundingBox().inflate(ALONE), fairy -> true).isEmpty()) {
				continue;
			}
			comeUpon(level, player, random);
		}
	}

	/** Forests (birch, dark, flower, old growth...), meadows and cherry groves. */
	static boolean fairyCountry(Holder<Biome> biome) {
		return biome.is(BiomeTags.IS_FOREST) || biome.is(Biomes.MEADOW) || biome.is(Biomes.CHERRY_GROVE) || biome.is(Biomes.FLOWER_FOREST);
	}

	/** A fairy over a flower somewhere near the player, if a flower is found. */
	private static void comeUpon(ServerLevel level, ServerPlayer player, RandomSource random) {
		for (int i = 0; i < TRIES; i++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			int dist = Mth.nextInt(random, NEAR, FAR);
			int x = Mth.floor(player.getX() + Math.cos(angle) * dist), z = Mth.floor(player.getZ() + Math.sin(angle) * dist);
			if (!level.isLoaded(new BlockPos(x, 0, z))) {
				continue;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
			if (!level.getBlockState(top).is(BlockTags.FLOWERS) && !level.getBlockState(top.below()).is(BlockTags.FLOWERS)) {
				continue;
			}
			PinkFairy fairy = PINK_FAIRY.create(level, EntitySpawnReason.EVENT);
			if (fairy != null) {
				fairy.snapTo(x + 0.5, top.getY() + 0.8 + random.nextDouble(), z + 0.5, random.nextFloat() * 360.0F, 0.0F);
				level.addFreshEntity(fairy);
			}
			return;
		}
	}

	/** A fairy let go at a spot: she circles up and is gone. */
	static void loose(ServerLevel level, double x, double y, double z) {
		PinkFairy fairy = PINK_FAIRY.create(level, EntitySpawnReason.EVENT);
		if (fairy != null) {
			fairy.snapTo(x, y, z, 0.0F, 0.0F);
			fairy.free();
			level.addFreshEntity(fairy);
		}
		level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.5F);
		MagicFx.burst(level, x, y, z, PinkFairy.GLOW, 1.0F, 24, 0.4, 0.4, 0.4);
	}

	// ---- worn by anyone ----------------------------------------------------------------------------------------------

	/** Every {@link #SHOW_EVERY} ticks: who shows a fairy, villagers putting on one they were given, and the wearers' glow. */
	private static void wearers(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			// ((0.20.12) at the hip, from the belt slot, she is drawn as the coin purse is: WornLanterns, not on the chest)
			int slot = wornSlot(player);
			boolean wears = slot >= 0 && slot != Baubles.BELT_SLOT;
			if (wears != player.hasAttached(WEARS)) {
				set(player, wears);
			}
			if (!(player.level() instanceof ServerLevel level) || player.isSpectator()) {
				continue;
			}
			for (Villager v : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(VILLAGERS_NEAR), Villager::isAlive)) {
				if (v.hasAttached(WEARS)) {
					if (!v.isInvisible() && level.getRandom().nextInt(2) == 0) {
						level.sendParticles(MagicFx.wisp(PinkFairy.GLOW, 0.5F), v.getX(), v.getY() + v.getBbHeight() * 0.6, v.getZ(),
							1, 0.3, 0.3, 0.3, 0.0);
					}
					continue;
				}
				// (an MCA gift goes into the villager's own inventory: they take her out and put her on)
				SimpleContainer inventory = v.getInventory();
				for (int i = 0; i < inventory.getContainerSize(); i++) {
					if (inventory.getItem(i).is(BOTTLED_FAIRY)) {
						inventory.removeItem(i, 1);
						set(v, true);
						level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.0F, 1.5F);
						level.sendParticles(MagicFx.wisp(PinkFairy.GLOW, 0.9F), v.getX(), v.getY() + v.getBbHeight() * 0.6, v.getZ(),
							12, 0.3, 0.3, 0.3, 0.0);
						break;
					}
				}
			}
		}
	}

	private static void set(LivingEntity entity, boolean wears) {
		if (wears) {
			entity.setAttached(WEARS, true);
		} else {
			entity.removeAttached(WEARS);
		}
	}

	/** The bauble slot a player wears a fairy in (amulet, charm or (0.20.12) belt, hung at the hip), or -1. */
	static int wornSlot(Player player) {
		List<ItemStack> worn = Baubles.worn(player);
		for (int slot = 0; slot < Baubles.SIZE; slot++) {
			if (worn.get(slot).is(BOTTLED_FAIRY) && Baubles.fits(slot, worn.get(slot))) {
				return slot;
			}
		}
		return -1;
	}

	/** Whether this body shows a fairy worn (both sides: synced). */
	public static boolean wears(LivingEntity entity) {
		return entity.hasAttached(WEARS);
	}

	/** A villager (or anyone but a player) who wears one and dies all the same (the void): she drops where they fell. */
	private static void diedWearing(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof Player) && entity.hasAttached(WEARS) && entity.level() instanceof ServerLevel level) {
			entity.removeAttached(WEARS);
			entity.spawnAtLocation(level, new ItemStack(BOTTLED_FAIRY));
		}
	}

	// ---- turning death aside -----------------------------------------------------------------------------------------

	/**
	 * The wearer of a bottled fairy (a player's amulet, charm or belt slot, or a villager who was given one) does not die of
	 * this: the bottle cracks and she is spent instead.
	 */
	static boolean saveFromDeath(LivingEntity entity, DamageSource source, float amount) {
		if (!(entity.level() instanceof ServerLevel level) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || Downed.isDowned(entity)) {
			return true;
		}
		if (entity instanceof ServerPlayer player) {
			int slot = wornSlot(player);
			if (slot < 0) {
				return true;
			}
			List<ItemStack> worn = Baubles.worn(player);
			// (the worn stack itself, so an open bauble screen can't give her back)
			worn.get(slot).shrink(1);
			Baubles.setWorn(player, worn);
			set(player, false);
			ItemStack cracked = new ItemStack(CRACKED_BOTTLE);
			if (!player.getInventory().add(cracked)) {
				player.drop(cracked, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.fairy.saved"));
		} else if (entity.hasAttached(WEARS)) {
			set(entity, false);
			entity.spawnAtLocation(level, new ItemStack(CRACKED_BOTTLE));
		} else {
			return true;
		}
		entity.setHealth(Math.min(SAVED_HEALTH, entity.getMaxHealth()));
		entity.clearFire();
		entity.resetFallDistance();
		entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, SAVED_REGEN_TICKS, SAVED_REGEN_LEVEL));
		crack(level, entity);
		loose(level, entity.getX(), entity.getEyeY(), entity.getZ());
		return false;
	}

	/** The bottle cracks on the wearer's chest: breaking glass, shards of it and a burst of light. */
	static void crack(ServerLevel level, LivingEntity entity) {
		double y = entity.getY() + entity.getBbHeight() * 0.65;
		level.playSound(null, entity.getX(), y, entity.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 1.3F);
		level.playSound(null, entity.getX(), y, entity.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.5F, 1.2F);
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(new ItemStack(CRACKED_BOTTLE))),
			entity.getX(), y, entity.getZ(), 16, 0.2, 0.2, 0.2, 0.12);
		level.sendParticles(ParticleTypes.END_ROD, entity.getX(), y, entity.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
	}
}
