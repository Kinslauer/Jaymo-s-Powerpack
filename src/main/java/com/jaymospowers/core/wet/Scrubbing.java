package com.jaymospowers.core.wet;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.blood.Bloodied;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
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
 * get the last of and a bare-hand scrub only slowly, comes off in a few scrubs. Sand scours harder and takes rust off
 * metal too ({@link Rust}); straw doesn't touch rust. At a cauldron each scrub uses a level of water.
 */
public final class Scrubbing {
	// ---- tuning ----
	/** What one scrub takes off: blood and grime with a wisp, with sand; rust with sand. */
	static final int WISP_SCRUB = 40, SAND_SCRUB = 50, SAND_RUST = 25;
	/** Scrubs in one wisp. */
	static final int WISP_USES = 24;
	/** A scrub at most every this many ticks. */
	static final int COOLDOWN = 12;

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
		if (!held.isEmpty() ? !dirty(held, sand) : !dirty(player, sand) && (sand || Smell.stink(player) <= 0.0F)) {
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
			boolean rust;
			if (!held.isEmpty()) {
				rust = scrubItem(held, scrub, sand);
			} else {
				rust = false;
				Bloodied.scourCoat(player, scrub);
				ArmourGrime.washBody(player, scrub);
				for (EquipmentSlot slot : ArmourGrime.SLOTS) {
					rust |= scrubItem(player.getItemBySlot(slot), scrub, sand);
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
			if (rust) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.scrub.rust"));
			}
		}
		return InteractionResult.SUCCESS;
	}

	/** Scrubs {@code stack}: blood (any age) and grime, and with sand its rust. True if rust came off. */
	private static boolean scrubItem(ItemStack stack, int scrub, boolean sand) {
		if (stack.isEmpty()) {
			return false;
		}
		Bloodied.scour(stack, scrub);
		ArmourGrime.clean(stack, scrub);
		return sand && Rust.scour(stack, SAND_RUST);
	}

	private static boolean dirty(ItemStack stack, boolean sand) {
		return Bloodied.blood(stack) != null || ArmourGrime.grime(stack) != null || sand && Rust.rust(stack) > 0;
	}

	private static boolean dirty(Player player, boolean sand) {
		if (ArmourGrime.anyDirt(player)) {
			return true;
		}
		if (sand) {
			for (EquipmentSlot slot : ArmourGrime.SLOTS) {
				if (Rust.rust(player.getItemBySlot(slot)) > 0) {
					return true;
				}
			}
		}
		return false;
	}
}
