package com.jaymospowers.alchemy.cauldron;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.kiln.Heat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.crafting.BrewingInput;
import net.minecraft.world.item.crafting.BrewingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * Cauldron brewing: the hedge-witch's way to potions, before there's a brewing stand. A full water cauldron over heat
 * ({@link Heat}, or anything in Kitchen's {@code jaymoskitchen:heat_sources} that is lit) takes potion ingredients
 * thrown in, in the brewing stand's order (its recipes, data ones included: nether wart, then the effect, then
 * redstone or glowstone...).
 * <ul>
 * <li>Each ingredient has to be stirred in ({@link #STIR_TURNS} turns with a stick or a shovel, holding use) before the
 * next goes in; one thrown in while the last is still unstirred, or one the brew can't take, is spat back out.</li>
 * <li>A stirred brew fills three bottles (one per cauldron level). Gunpowder thrown into it makes it splash, dragon's
 * breath lingering (the brewing stand's own recipes).</li>
 * <li>Buckets and more water would spoil it, so they're refused; a brew whose level changes some other way (rain, a
 * washed banner) is just water again.</li>
 * </ul>
 * Also here: sneak-use a water bottle to throw it; it splashes like a splash water bottle and puts out fires.
 */
public final class CauldronBrewing {
	/** Stirring turns (use clicks, about five a second while held) that work an ingredient in. */
	public static final int STIR_TURNS = 6;
	/** Ticks between the bubbling of a brew over heat. */
	public static final int BUBBLE_TICKS = 8;
	/** How hard a refused ingredient is spat out. */
	public static final double SPIT = 0.35;
	/** Kitchen's hot blocks (stove, furnaces...); empty when Kitchen isn't loaded. */
	public static final TagKey<Block> KITCHEN_HEAT = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("jaymoskitchen", "heat_sources"));

	/** A cauldron's brew: the potion one bottle takes, stir turns still owed, and the cauldron level it was left at. */
	public record Brew(ItemStack potion, int stirs, int fills) {
		public static final Codec<Brew> CODEC = RecordCodecBuilder.create(i -> i.group(
			ItemStack.CODEC.fieldOf("potion").forGetter(Brew::potion),
			Codec.INT.fieldOf("stirs").forGetter(Brew::stirs),
			Codec.INT.fieldOf("fills").forGetter(Brew::fills)
		).apply(i, Brew::new));
	}

	/** The brews in a chunk, by packed block position. */
	public static final AttachmentType<Map<String, Brew>> BREWS = AttachmentRegistry.create(JaymosPowers.id("cauldron_brews"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Brew.CODEC)));
	/** Brews seen since the server started, for their bubbling. */
	private static final Map<ServerLevel, Set<Long>> ACTIVE = new WeakHashMap<>();

	private CauldronBrewing() {
	}

	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player.isSpectator()) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			BlockState state = level.getBlockState(pos);
			if (!state.is(Blocks.WATER_CAULDRON)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (level.isClientSide()) {
				// the server decides; just don't let the client predict a water bottle out of a brew
				return stack.is(Items.STICK) || stack.is(ItemTags.SHOVELS) ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			Brew brew = get(level, pos);
			if (stack.is(Items.STICK) || stack.is(ItemTags.SHOVELS)) {
				return stir(player, level, pos, brew);
			}
			if (brew == null) {
				return InteractionResult.PASS;
			}
			if (stack.is(Items.GLASS_BOTTLE)) {
				return bottle(player, level, pos, state, brew, stack, hand);
			}
			if (stack.is(Items.BUCKET) || stack.is(Items.WATER_BUCKET) || stack.is(Items.POTION)) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.cauldron_brew.spoil"));
				return InteractionResult.FAIL;
			}
			if (stack.isEmpty()) {
				player.sendOverlayMessage(Component.translatable(brew.stirs() > 0 ? "message.jaymospowers.cauldron_brew.unstirred"
					: "message.jaymospowers.cauldron_brew.holds", brew.potion().getHoverName(), brew.fills()));
				// PASS: Kitchen's tanning lifts soaked hides out with an empty hand
				return InteractionResult.PASS;
			}
			return InteractionResult.PASS;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!player.isShiftKeyDown() || !stack.is(Items.POTION)
				|| !stack.getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER)) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				Projectile.spawnProjectileFromRotation(ThrownSplashPotion::new, server, PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WATER),
					player, -20.0F, 0.5F, 1.0F);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS, 0.5F, 0.4F);
			}
			stack.consume(1, player);
			return InteractionResult.SUCCESS;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
			if (state.getBlock() instanceof LayeredCauldronBlock) {
				set(level, pos, null);
			}
		});
		ServerTickEvents.END_LEVEL_TICK.register(CauldronBrewing::bubble);
	}

	/** An item in a water cauldron ({@code LayeredCauldronBlockMixin}): over heat, a brewing ingredient goes in or is spat out. */
	public static void itemInside(ServerLevel level, BlockPos pos, BlockState state, ItemEntity item) {
		if (!item.isAlive() || item.getItem().isEmpty() || !heated(level, pos)) {
			return;
		}
		Brew brew = get(level, pos);
		ItemStack potion = brew == null ? PotionContents.createItemStack(Items.POTION, Potions.WATER) : brew.potion();
		ItemStack reagent = item.getItem().copyWithCount(1);
		BrewingInput input = new BrewingInput(potion, reagent);
		Optional<RecipeHolder<BrewingRecipe>> recipe = level.recipeAccess().getRecipeFor(RecipeType.BREWING, input, level);
		if (brew == null) {
			// plain water: only a real first ingredient, in a full cauldron, starts a brew; anything else is left alone
			if (recipe.isEmpty()) {
				return;
			}
			if (state.getValue(LayeredCauldronBlock.LEVEL) < LayeredCauldronBlock.MAX_FILL_LEVEL) {
				spit(level, pos, item, "message.jaymospowers.cauldron_brew.not_full");
				return;
			}
		} else if (brew.stirs() > 0) {
			spit(level, pos, item, "message.jaymospowers.cauldron_brew.stir_first");
			return;
		} else if (recipe.isEmpty()) {
			spit(level, pos, item, "message.jaymospowers.cauldron_brew.refuses");
			return;
		}
		ItemStack result = recipe.get().value().assemble(input);
		item.getItem().shrink(1);
		if (item.getItem().isEmpty()) {
			item.discard();
		}
		set(level, pos, new Brew(result, STIR_TURNS, state.getValue(LayeredCauldronBlock.LEVEL)));
		level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.5F, 1.2F);
		level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.6F, 1.1F);
		puff(level, pos, result, 10);
	}

	private static InteractionResult stir(Player player, Level level, BlockPos pos, @Nullable Brew brew) {
		if (brew == null || brew.stirs() <= 0) {
			return InteractionResult.PASS;
		}
		if (!heated(level, pos)) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.cauldron_brew.cold"));
			return InteractionResult.FAIL;
		}
		int stirs = brew.stirs() - 1;
		set(level, pos, new Brew(brew.potion(), stirs, brew.fills()));
		ServerLevel server = (ServerLevel) level;
		server.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 0.95, pos.getZ() + 0.5, 4, 0.2, 0.0, 0.2, 0.0);
		level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, SoundSource.BLOCKS, 0.4F, 1.4F);
		if (stirs == 0) {
			level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.8F, 1.0F);
			puff(server, pos, brew.potion(), 16);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.cauldron_brew.stirred", brew.potion().getHoverName()));
		}
		return InteractionResult.SUCCESS;
	}

	private static InteractionResult bottle(Player player, Level level, BlockPos pos, BlockState state, Brew brew, ItemStack stack,
		net.minecraft.world.InteractionHand hand) {
		if (brew.stirs() > 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.cauldron_brew.unstirred", brew.potion().getHoverName(), brew.fills()));
			return InteractionResult.FAIL;
		}
		player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, brew.potion().copy()));
		level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
		int fills = brew.fills() - 1;
		set(level, pos, fills > 0 ? new Brew(brew.potion(), 0, fills) : null);
		LayeredCauldronBlock.lowerFillLevel(state, level, pos);
		return InteractionResult.SUCCESS;
	}

	/** Spits a refused item back out of the pot, and tells whoever threw it why. */
	private static void spit(ServerLevel level, BlockPos pos, ItemEntity item, String why) {
		item.setPos(pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5);
		item.setDeltaMovement((level.getRandom().nextDouble() - 0.5) * SPIT, SPIT, (level.getRandom().nextDouble() - 0.5) * SPIT);
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.4F, 1.6F);
		level.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 5, 0.2, 0.1, 0.2, 0.01);
		if (item.getOwner() instanceof Player player && player.distanceToSqr(item) < 256) {
			player.sendOverlayMessage(Component.translatable(why));
		}
	}

	private static void puff(ServerLevel level, BlockPos pos, ItemStack potion, int count) {
		int colour = potion.getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getColor();
		level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, colour | 0xFF000000), pos.getX() + 0.5, pos.getY() + 1.0,
			pos.getZ() + 0.5, count, 0.2, 0.15, 0.2, 0.0);
	}

	/** Brews over heat bubble in their colour, unstirred ones throw up froth. */
	private static void bubble(ServerLevel level) {
		if (level.getGameTime() % BUBBLE_TICKS != 0) {
			return;
		}
		Set<Long> active = ACTIVE.get(level);
		if (active == null || active.isEmpty()) {
			return;
		}
		for (Iterator<Long> it = active.iterator(); it.hasNext(); ) {
			BlockPos pos = BlockPos.of(it.next());
			if (!level.isLoaded(pos)) {
				it.remove();
				continue;
			}
			Brew brew = get(level, pos);
			if (brew == null) {
				it.remove();
				continue;
			}
			if (heated(level, pos)) {
				puff(level, pos, brew.potion(), 2);
				level.sendParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 0.95,
					pos.getZ() + 0.5, brew.stirs() > 0 ? 4 : 1, 0.2, 0.0, 0.2, 0.0);
			}
		}
	}

	/** Whether the block under a cauldron is burning. */
	public static boolean heated(Level level, BlockPos cauldron) {
		BlockPos below = cauldron.below();
		if (Heat.at(level, below) > 0) {
			return true;
		}
		BlockState state = level.getBlockState(below);
		return state.is(KITCHEN_HEAT) && (!state.hasProperty(BlockStateProperties.LIT) || state.getValue(BlockStateProperties.LIT));
	}

	/** The brew in a cauldron, or null; a brew whose cauldron changed under it (emptied, rained into) is dropped. */
	public static @Nullable Brew get(Level level, BlockPos pos) {
		if (!(level.getChunk(pos) instanceof LevelChunk chunk)) {
			return null;
		}
		Map<String, Brew> brews = chunk.getAttached(BREWS);
		Brew brew = brews == null ? null : brews.get(Long.toString(pos.asLong()));
		if (brew == null) {
			return null;
		}
		BlockState state = level.getBlockState(pos);
		if (!state.is(Blocks.WATER_CAULDRON) || state.getValue(LayeredCauldronBlock.LEVEL) != brew.fills()) {
			set(level, pos, null);
			return null;
		}
		if (level instanceof ServerLevel server) {
			ACTIVE.computeIfAbsent(server, l -> new HashSet<>()).add(pos.asLong());
		}
		return brew;
	}

	private static void set(Level level, BlockPos pos, @Nullable Brew brew) {
		if (!(level.getChunk(pos) instanceof LevelChunk chunk)) {
			return;
		}
		Map<String, Brew> old = chunk.getAttached(BREWS);
		if (brew == null && (old == null || !old.containsKey(Long.toString(pos.asLong())))) {
			return;
		}
		Map<String, Brew> brews = old == null ? new HashMap<>() : new HashMap<>(old);
		if (brew == null) {
			brews.remove(Long.toString(pos.asLong()));
		} else {
			brews.put(Long.toString(pos.asLong()), brew);
			if (level instanceof ServerLevel server) {
				ACTIVE.computeIfAbsent(server, l -> new HashSet<>()).add(pos.asLong());
			}
		}
		if (brews.isEmpty()) {
			chunk.removeAttached(BREWS);
		} else {
			chunk.setAttached(BREWS, brews);
		}
		chunk.markUnsaved();
	}
}
