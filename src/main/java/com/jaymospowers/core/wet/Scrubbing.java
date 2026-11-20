package com.jaymospowers.core.wet;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.blood.BloodTrail;
import com.jaymospowers.core.blood.Bloodied;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "add a way to scrub it in water with a cheap material". Standing in water (or at a water cauldron), sneak + use
 * a <b>straw wisp</b> (a twist of two wheat's straw, good for {@link #WISP_USES} scrubs) or a handful of <b>sand</b> (any
 * sand; one is used up each scrub) to scrub yourself: your skin and clothes, the armour you wear, and the smell. Hold
 * something in the other hand to scrub that instead (a blade, a piece of armour).
 * <p>
 * Something rough takes blood off however old it is ({@link Bloodied#scour}): rotten blood, which water and rain never
 * get the last of and a bare-hand scrub only slowly, comes off in a few scrubs. Sand scours harder. Nothing takes
 * rust off (Andy: "nothing removes rust", {@link Rust}). At a cauldron each scrub uses a level of water.
 * <p>
 * Andy: "we need a way to clean blood". Blood on the floor or a wall comes off the same way, with water to hand: sneak +
 * use the wisp or sand on the stain with a water bucket or a water bottle in the other hand (the bottle is emptied), or
 * a wet sponge on its own ({@link #scrubStain}). Each scrub takes some of every stain under it off
 * ({@code BloodTrail.scrub}); blood that has dried takes twice the scrubbing, except with sand. Pouring water over it
 * washes it away too ({@code BloodTrail}).
 */
public final class Scrubbing {
	// ---- tuning ----
	/** What one scrub takes off: blood and grime with a wisp, and with sand. */
	static final int WISP_SCRUB = 40, SAND_SCRUB = 50;
	/** Scrubs in one wisp. */
	static final int WISP_USES = 24;
	/** A scrub at most every this many ticks. */
	static final int COOLDOWN = 12;
	/** Scrubbing a stain: how much of it one scrub takes off with a wisp, sand and a wet sponge; how far round the spot (blocks). */
	static final float STAIN_WISP = 0.35F, STAIN_SAND = 0.5F, STAIN_SPONGE = 0.45F;
	static final double STAIN_REACH = 0.9;
	/**
	 * Sand or a sponge on a wall scrubs it only with a lasting stain this near (blocks): the splats and runs on walls are
	 * each client's own, so the server goes by the stains both sides know (else the sand or sponge is placed as a block).
	 */
	static final double WALL_NEAR = 2.5;

	/**
	 * The blood only a client draws (splashes, splats and runs down a wall, which the server never knew of): the client sets
	 * it, so a scrub there is foreseen and wiped at once. Null on a server.
	 */
	public interface SeenBlood {
		/** Any blood drawn within {@code range} of {@code at}. */
		boolean near(Level level, Vec3 at, double range);

		/** One of the server's lasting stains (as sent to this client) within {@code range} of {@code at}. */
		boolean stain(Level level, Vec3 at, double range);

		void wipe(Level level, Vec3 at, double range);
	}

	public static @Nullable SeenBlood seen;

	public static Item STRAW_WISP;
	private static final Map<Player, Long> LAST = new WeakHashMap<>();

	private Scrubbing() {
	}

	public static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("straw_wisp"));
		STRAW_WISP = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key).durability(WISP_USES)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(STRAW_WISP));
		UseBlockCallback.EVENT.register(Scrubbing::onUseBlock);
		UseItemCallback.EVENT.register((player, level, hand) -> scrub(player, level, hand, null));
	}

	private static boolean wisp(ItemStack stack) {
		return STRAW_WISP != null && stack.is(STRAW_WISP);
	}

	private static boolean sand(ItemStack stack) {
		return stack.is(ItemTags.SAND);
	}

	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		InteractionResult stain = scrubStain(player, level, hand, hit);
		if (stain != InteractionResult.PASS) {
			return stain;
		}
		BlockPos pos = hit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		boolean cauldron = state.is(Blocks.WATER_CAULDRON) && state.hasProperty(LayeredCauldronBlock.LEVEL);
		return scrub(player, level, hand, cauldron ? pos : null);
	}

	/**
	 * A scrub, if {@code player} is sneaking with a wisp or sand in the main hand, in water or at the water cauldron at
	 * {@code cauldron}, and there's something to scrub off. Otherwise passes (sand is then placed as usual).
	 */
	private static InteractionResult scrub(Player player, Level level, InteractionHand hand, @Nullable BlockPos cauldron) {
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		ItemStack tool = player.getMainHandItem();
		boolean sand = sand(tool);
		if (!sand && !wisp(tool) || cauldron == null && !player.isInWater()) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getOffhandItem();
		// (the smell isn't known on the client, so sand, which would otherwise be placed, scrubs only what can be seen)
		if (!held.isEmpty() ? !dirty(held) : !ArmourGrime.anyDirt(player) && (sand || Smell.stink(player) <= 0.0F)) {
			return InteractionResult.PASS;
		}
		long now = level.getGameTime();
		Long last = LAST.get(player);
		if (last != null && now - last < COOLDOWN) {
			return InteractionResult.FAIL;
		}
		LAST.put(player, now);
		if (level instanceof ServerLevel server) {
			int scrub = sand ? SAND_SCRUB : WISP_SCRUB;
			if (!held.isEmpty()) {
				scrubItem(held, scrub);
			} else {
				Bloodied.scourCoat(player, scrub);
				ArmourGrime.washBody(player, scrub);
				for (EquipmentSlot slot : ArmourGrime.SLOTS) {
					scrubItem(player.getItemBySlot(slot), scrub);
				}
				if (Smell.stink(player) > 0.0F) {
					Smell.scrub(player);
				}
			}
			if (sand) {
				tool.consume(1, player);
			} else {
				tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
			}
			if (cauldron != null && !player.isInWater()) {
				LayeredCauldronBlock.lowerFillLevel(level.getBlockState(cauldron), level, cauldron);
			}
			Vec3 at = !held.isEmpty() ? player.getEyePosition().add(player.getLookAngle().scale(0.5)).add(0.0, -0.5, 0.0)
				: player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
			server.sendParticles(ParticleTypes.SPLASH, at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.1);
			server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, sand ? Blocks.SAND.defaultBlockState() : Blocks.HAY_BLOCK.defaultBlockState()),
				at.x, at.y, at.z, 6, 0.25, 0.25, 0.25, 0.05);
			level.playSound(null, at.x, at.y, at.z, sand ? SoundEvents.SAND_HIT : SoundEvents.GRASS_HIT, SoundSource.PLAYERS, 0.7F,
				0.9F + level.getRandom().nextFloat() * 0.2F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS, 0.3F, 1.3F);
		}
		return InteractionResult.SUCCESS;
	}

	/**
	 * Andy: "we need a way to clean blood". A scrub of the blood on the floor or wall {@code player} sneak-uses a wisp, sand
	 * or a wet sponge on, with water to hand (a water bucket or bottle in the other hand; a wet sponge is wet enough).
	 * Passes when there's no blood there to scrub (sand or a sponge is then placed as usual). A wall is always worth a
	 * wisp: the splats and runs on it are drawn by each client and never known here ({@link #WALL_NEAR}).
	 */
	private static InteractionResult scrubStain(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || !player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		ItemStack tool = player.getMainHandItem();
		boolean sand = sand(tool), sponge = tool.is(Items.WET_SPONGE);
		if (!sand && !sponge && !wisp(tool)) {
			return InteractionResult.PASS;
		}
		ItemStack water = player.getOffhandItem();
		boolean bottle = water.is(Items.POTION) && water.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).is(Potions.WATER);
		if (!sponge && !bottle && !water.is(Items.WATER_BUCKET)) {
			return InteractionResult.PASS;
		}
		Vec3 at = hit.getLocation();
		boolean wall = hit.getDirection().getAxis() != Direction.Axis.Y;
		boolean blood;
		if (!sand && !sponge) {
			// a wisp is never placed, so the two sides may judge it differently
			blood = wall || (level instanceof ServerLevel server ? BloodTrail.stained(server, at, STAIN_REACH)
				: seen != null && seen.near(level, at, STAIN_REACH));
		} else {
			// sand and a sponge are placed when there's nothing to scrub: both sides go by the same stains
			double reach = wall ? WALL_NEAR : STAIN_REACH;
			blood = level instanceof ServerLevel server ? BloodTrail.stained(server, at, reach) : seen != null && seen.stain(level, at, reach);
		}
		if (!blood) {
			return InteractionResult.PASS;
		}
		long now = level.getGameTime();
		Long last = LAST.get(player);
		if (last != null && now - last < COOLDOWN) {
			return InteractionResult.FAIL;
		}
		LAST.put(player, now);
		if (!(level instanceof ServerLevel server)) {
			if (seen != null) {
				seen.wipe(level, at, STAIN_REACH);
			}
			return InteractionResult.SUCCESS;
		}
		BloodTrail.scrub(server, at, STAIN_REACH, sand ? STAIN_SAND : sponge ? STAIN_SPONGE : STAIN_WISP, sand);
		if (sand) {
			tool.consume(1, player);
		} else if (!sponge) {
			tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
		}
		if (bottle) {
			player.setItemInHand(InteractionHand.OFF_HAND, ItemUtils.createFilledResult(water, player, new ItemStack(Items.GLASS_BOTTLE)));
		}
		Vec3 face = at.add(hit.getDirection().getStepX() * 0.05, hit.getDirection().getStepY() * 0.05, hit.getDirection().getStepZ() * 0.05);
		server.sendParticles(ParticleTypes.SPLASH, face.x, face.y, face.z, 10, 0.25, 0.1, 0.25, 0.1);
		if (!sponge) {
			server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, sand ? Blocks.SAND.defaultBlockState() : Blocks.HAY_BLOCK.defaultBlockState()),
				face.x, face.y, face.z, 5, 0.2, 0.1, 0.2, 0.05);
		}
		level.playSound(null, face.x, face.y, face.z, SoundEvents.BRUSH_GENERIC, SoundSource.PLAYERS, 0.7F, 0.9F + level.getRandom().nextFloat() * 0.2F);
		level.playSound(null, face.x, face.y, face.z, sponge ? SoundEvents.WET_SPONGE_HIT : SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS,
			sponge ? 0.6F : 0.3F, 1.2F);
		return InteractionResult.SUCCESS;
	}

	/** Scrubs {@code stack}: blood (any age) and grime. Rust stays. */
	private static void scrubItem(ItemStack stack, int scrub) {
		if (stack.isEmpty()) {
			return;
		}
		Bloodied.scour(stack, scrub);
		ArmourGrime.clean(stack, scrub);
	}

	private static boolean dirty(ItemStack stack) {
		return Bloodied.blood(stack) != null || ArmourGrime.grime(stack) != null;
	}
}
