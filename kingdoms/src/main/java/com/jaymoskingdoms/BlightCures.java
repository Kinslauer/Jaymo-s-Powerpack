package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * The cures for the Hollow's blight and what holds it back, Andy's picks (2026-11-15), besides the golden apple eaten while
 * weakened ({@link HollowBlight#ateGold}):
 * <ul>
 * <li><b>Tainted flesh.</b> Andy: "it shouldn't look any different, it's just something that should happen from rotten
 * flesh". Raw rotten flesh eaten can give you the blight ({@link #RAW_FLESH_CHANCE}); cooked into <b>seared flesh</b>
 * (furnace, smoker, campfire) it is only bad meat.</li>
 * <li><b>Gilded flesh</b> (Andy: "Old druid remedy, most don't trust it"): seared flesh worked through with gold nuggets.
 * Eaten, it sets the blight's spread back by {@link #GILDED_SET_BACK} of a day, never cures it. Anyone near who sees you
 * eat it has something to say about druid remedies.</li>
 * <li><b>The Hollow's Cure</b> (Andy: "Only druid apothecaries. Witches hide the cure as they command zombie thralls
 * themselves"): a draught that burns the blight out. Only a druid can brew it: a Druid (first or second calling) works
 * gilded flesh into a water cauldron over a fire, with an empty bottle to take it up; an old druid of the groves brews
 * one for anyone who brings gilded flesh and an empty bottle. The witches have it too and hide it: each swamp hut's
 * cauldron gives one draught to an empty bottle, once, and a witch now and then carries one. Their zombie thralls
 * ({@code monsters/WitchThralls}) keep the huts.</li>
 * <li><b>For good</b> (Andy: "You gain immunity forever after a cure"): whoever is cured, by any cure, can never take the
 * blight again ({@link #PROOF}, kept through death; a new life wipes it with everything else).</li>
 * <li><b>A totem</b> held when the blight would turn you is spent instead, and that is a cure ({@link #totem}).</li>
 * <li><b>Healers</b> burn it out for coin ({@code Restoration}'s "blight" service); one who minds the Hollow turns the
 * Hollow away.</li>
 * </ul>
 */
public final class BlightCures {
	/** Raw rotten flesh eaten gives the blight this often. */
	static final float RAW_FLESH_CHANCE = 0.12F;
	/** Gilded flesh sets the blight back this share of its whole course. */
	static final float GILDED_SET_BACK = 0.33F;
	/** A witch carries a cure this often (dropped when she dies). */
	static final float WITCH_DROP = 0.2F;
	/** One who sees gilded flesh eaten remarks on it from this far (blocks); lines {@code blight_cures.jaymoskingdoms.gilded.<n>}. */
	static final double REMARK_RANGE = 10.0;
	static final int GILDED_LINES = 5, DRUID_LINES = 3;
	/** How long the Hollow's Cure takes to drink (ticks). */
	static final int DRINK_TICKS = 40;
	static final int DRUID_COLOUR = 0xFFA8C878;

	/** Cured once, for good: the blight can't take this one again. Saved, kept through death. */
	public static final AttachmentType<Boolean> PROOF = AttachmentRegistry.create(JaymosKingdoms.id("blight_proof"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());
	/** The swamp huts (their chunk) whose cauldron has given up its hidden draught. Saved with the world. */
	private static final AttachmentType<List<Long>> HUTS_DRAWN = AttachmentRegistry.create(JaymosKingdoms.id("witch_cures_drawn"),
		builder -> builder.persistent(Codec.LONG.listOf()));

	public static Item SEARED_FLESH, GILDED_FLESH, CURE;

	private BlightCures() {
	}

	static void register() {
		SEARED_FLESH = item("seared_flesh", p -> new Item(p.food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.2F).build())));
		GILDED_FLESH = item("gilded_flesh", p -> new Gilded(p.food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.3F)
			.alwaysEdible().build())));
		CURE = item("hollows_cure", p -> new Cure(p.stacksTo(1)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> {
			output.insertAfter(Items.ROTTEN_FLESH, SEARED_FLESH);
			output.insertAfter(SEARED_FLESH, GILDED_FLESH);
			output.insertAfter(GILDED_FLESH, CURE);
		});
		UseBlockCallback.EVENT.register(BlightCures::useBlock);
		UseEntityCallback.EVENT.register(BlightCures::useEntity);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Witch witch && witch.level() instanceof ServerLevel level && witch.getRandom().nextFloat() < WITCH_DROP) {
				witch.spawnAtLocation(level, new ItemStack(CURE));
			}
		});
	}

	private static Item item(String name, java.util.function.Function<Item.Properties, Item> make) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(new Item.Properties().setId(key)));
	}

	/** Whether {@code body} was cured once and can't take the blight again. */
	static boolean proof(LivingEntity body) {
		return body.getAttachedOrElse(PROOF, false);
	}

	/** {@code player} finished eating or drinking {@code stack} (from {@code GoldenAppleMixin}): raw rotten flesh may blight them. */
	public static void ate(ServerPlayer player, ItemStack stack) {
		if (stack.is(Items.ROTTEN_FLESH) && player.getRandom().nextFloat() < RAW_FLESH_CHANCE) {
			HollowBlight.fromFlesh(player);
		}
	}

	// ---- gilded flesh -----------------------------------------------------------------------------------------------

	/** Gilded flesh: food that sets the blight back, and gets remarked on. */
	static final class Gilded extends Item {
		Gilded(Properties properties) {
			super(properties);
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
			if (user instanceof ServerPlayer player && level instanceof ServerLevel server) {
				HollowBlight.setBack(player, GILDED_SET_BACK);
				remark(server, player);
			}
			return super.finishUsingItem(stack, level, user);
		}
	}

	/** Someone near who saw {@code player} eat gilded flesh says what most think of druid remedies. */
	private static void remark(ServerLevel level, ServerPlayer player) {
		List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(REMARK_RANGE),
			x -> x != player && x.isAlive() && !x.isBaby() && !x.isSleeping() && Recollections.rememberer(x) && x.hasLineOfSight(player));
		if (near.isEmpty()) {
			return;
		}
		LivingEntity by = near.get(player.getRandom().nextInt(near.size()));
		if (by instanceof Mob m) {
			m.getLookControl().setLookAt(player, 30.0F, 30.0F);
		}
		Speech.say(level, by, Component.translatable("blight_cures.jaymoskingdoms.gilded." + player.getRandom().nextInt(GILDED_LINES)), Speech.WHITE,
			REMARK_RANGE + 4.0);
	}

	// ---- the Hollow's Cure ------------------------------------------------------------------------------------------

	/** The Hollow's Cure: drunk, it burns the blight out (and the bottle comes back). */
	static final class Cure extends Item {
		Cure(Properties properties) {
			super(properties);
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			player.startUsingItem(hand);
			return InteractionResult.CONSUME;
		}

		@Override
		public int getUseDuration(ItemStack stack, LivingEntity user) {
			return DRINK_TICKS;
		}

		@Override
		public ItemUseAnimation getUseAnimation(ItemStack stack) {
			return ItemUseAnimation.DRINK;
		}

		@Override
		public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
			if (!level.isClientSide()) {
				level.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.8F, 0.9F);
				HollowBlight.cure(user);
			}
			if (user instanceof Player player && !player.hasInfiniteMaterials()) {
				stack.shrink(1);
				if (stack.isEmpty()) {
					return new ItemStack(Items.GLASS_BOTTLE);
				}
				player.getInventory().placeItemBackInInventory(new ItemStack(Items.GLASS_BOTTLE), net.minecraft.util.Prediction.SERVER_ONLY);
			}
			return stack;
		}
	}

	/** A Druid brewing the cure at a cauldron over a fire, or a bottle dipped in a witch's hut cauldron. */
	private static InteractionResult useBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getItemInHand(hand);
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		if (held.is(GILDED_FLESH) && state.is(Blocks.WATER_CAULDRON) && heated(level, pos.below())
			&& Classes.both(sp).contains(Classes.PlayerClass.DRUID) && takeBottle(sp)) {
			held.consume(1, sp);
			LayeredCauldronBlock.lowerFillLevel(state, level, pos);
			give(sp, new ItemStack(CURE));
			brewed(server, net.minecraft.world.phys.Vec3.atCenterOf(pos));
			return InteractionResult.SUCCESS_SERVER;
		}
		if (held.is(Items.GLASS_BOTTLE) && state.is(BlockTags.CAULDRONS)) {
			StructureStart hut = server.structureManager().getStructureWithPieceAt(pos, h -> h.is(BuiltinStructures.SWAMP_HUT));
			if (hut.isValid()) {
				List<Long> drawn = new ArrayList<>(server.getAttachedOrElse(HUTS_DRAWN, List.<Long>of()));
				long key = hut.getChunkPos().pack();
				if (!drawn.contains(key)) {
					drawn.add(key);
					server.setAttached(HUTS_DRAWN, drawn);
					held.consume(1, sp);
					give(sp, new ItemStack(CURE));
					level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 0.8F);
					Fx.burst(server, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 0xA8C878, 0.5F, 6, 0.2, 0.1, 0.2);
					JaymosKingdoms.LOGGER.info("{} found the cure a witch hid in her hut", sp.getScoreboardName());
					return InteractionResult.SUCCESS_SERVER;
				}
			}
		}
		return InteractionResult.PASS;
	}

	/** Gilded flesh and an empty bottle handed to an old druid of the groves: they brew the cure. */
	private static InteractionResult useEntity(Player player, Level level, InteractionHand hand, Entity target, @Nullable EntityHitResult hit) {
		if (!(target instanceof Villager druid) || !druid.entityTags().contains(Groves.DRUID_TAG) || !player.getItemInHand(hand).is(GILDED_FLESH)) {
			return InteractionResult.PASS;
		}
		if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		if (hand != InteractionHand.MAIN_HAND || !takeBottle(sp)) {
			Speech.sayPlainly(server, druid, sp, Component.translatable("blight_cures.jaymoskingdoms.druid.bottle"), DRUID_COLOUR);
			return InteractionResult.CONSUME;
		}
		player.getItemInHand(hand).consume(1, sp);
		give(sp, new ItemStack(CURE));
		brewed(server, druid.position().add(0.0, 1.0, 0.0).add(druid.getLookAngle().scale(0.5)));
		Speech.sayPlainly(server, druid, sp, Component.translatable("blight_cures.jaymoskingdoms.druid.brew." + sp.getRandom().nextInt(DRUID_LINES)),
			DRUID_COLOUR);
		return InteractionResult.CONSUME;
	}

	/** Takes an empty bottle from {@code player}'s inventory; false if they have none. */
	private static boolean takeBottle(ServerPlayer player) {
		if (player.hasInfiniteMaterials()) {
			return true;
		}
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(Items.GLASS_BOTTLE)) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}

	private static void give(ServerPlayer player, ItemStack stack) {
		player.getInventory().placeItemBackInInventory(stack, net.minecraft.util.Prediction.SERVER_ONLY);
	}

	private static void brewed(ServerLevel level, net.minecraft.world.phys.Vec3 at) {
		level.playSound(null, at.x, at.y, at.z, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0F, 0.8F);
		Fx.burst(level, at.x, at.y, at.z, 0xA8C878, 0.6F, 10, 0.25, 0.2, 0.25);
	}

	/** Whether the block at {@code below} keeps a cauldron above it boiling: a fire, a lit campfire, magma or lava. */
	static boolean heated(Level level, BlockPos below) {
		BlockState state = level.getBlockState(below);
		return state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.LAVA)
			|| state.is(BlockTags.CAMPFIRES) && state.getValue(CampfireBlock.LIT);
	}

	/** Every cure that leaves {@code body} proof against the blight for good. */
	static void proofAgainst(LivingEntity body) {
		body.setAttached(PROOF, true);
	}
}
