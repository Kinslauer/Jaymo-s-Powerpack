package com.jaymospowers.combat;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Blades keep an edge (Andy's pick from Hackers 'n Slashers, ideas only; "yeah blades should be kept sharp"). A sword, an
 * axe or a spear collects dullness as it is used: a little off flesh, a good deal off stone and metal ({@link MetalHits}
 * reads what it struck). A dull blade does less damage (down to {@link #MOST_LOST} of it gone), and a grindstone puts the
 * edge back: use it with the blade in hand (sneaking is the Kingdoms marks screen's, so that is left alone). The number
 * is kept in the stack's custom data under {@link #KEY}, which Jaymo's Kingdoms' smiths read and clear on a guard's blade
 * ({@code Whetting}); anyone's blade, a mob's as well as a player's, wears the same way.
 */
public final class Edge {
	/** The custom data key holding a blade's dullness (Kingdoms' Whetting writes the same one). */
	public static final String KEY = "jaymospowers_edge";
	/** A blade's dullness runs 0 (keen) to this. */
	public static final int MAX = 300;
	/** Dullness off a cut on flesh, a hit on metal armour or a metal body, a stroke on metal or stone blocks. */
	public static final int FLESH = 1, METAL = 8, METAL_BLOCK = 6, STONE = 5;
	/** From these a blade is worn, dull, blunt (tooltip words); and below worn it loses no damage. */
	static final int WORN = 75, DULL = 150, BLUNT = 225;
	/** Share of its damage a fully dull blade has lost. */
	static final float MOST_LOST = 0.35F;
	/** Dullness a use of the grindstone takes off, and the sparks and sound of it. */
	static final int GRIND = 75, GRIND_SPARKS = 5;
	static final float GRIND_SCALE = 0.5F, GRIND_VOLUME = 0.6F;

	private Edge() {
	}

	public static void init() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack blade = player.getItemInHand(hand);
			if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || player.isSpectator() || dullness(blade) <= 0
				|| !level.getBlockState(hit.getBlockPos()).is(Blocks.GRINDSTONE)) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server) {
				sharpen(blade, GRIND);
				BlockPos at = hit.getBlockPos();
				Vec3 top = new Vec3(hit.getLocation().x, at.getY() + 1.0, hit.getLocation().z);
				ClashFx.sparks(server, top, player.getEyePosition().subtract(top), GRIND_SPARKS, GRIND_SCALE);
				server.playSound(null, at, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, GRIND_VOLUME, 1.1F);
				if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
					sp.sendOverlayMessage(Component.translatable(dullness(blade) > 0 ? "message.jaymospowers.edge.sharpened" : "message.jaymospowers.edge.keen"));
				}
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** A weapon that has an edge to lose: a sword, an axe or a spear that isn't wood. */
	public static boolean edged(ItemStack stack) {
		return !stack.isEmpty() && (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SPEARS)) && !ClashFx.woodenWeapon(stack);
	}

	/** How dull {@code stack} is (0: keen, or not a blade). */
	public static int dullness(ItemStack stack) {
		if (!edged(stack)) {
			return 0;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0 : Mth.clamp(data.copyTag().getIntOr(KEY, 0), 0, MAX);
	}

	/** {@code amount} duller. */
	public static void dull(ItemStack stack, int amount) {
		if (edged(stack) && amount > 0) {
			set(stack, dullness(stack) + amount);
		}
	}

	/** {@code amount} keener. */
	public static void sharpen(ItemStack stack, int amount) {
		if (edged(stack)) {
			set(stack, dullness(stack) - amount);
		}
	}

	private static void set(ItemStack stack, int value) {
		int to = Mth.clamp(value, 0, MAX);
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
			if (to <= 0) {
				tag.remove(KEY);
			} else {
				tag.putInt(KEY, to);
			}
		});
		// (a keen blade leaves no empty custom data behind, so it stacks and compares as it did)
		CustomData left = stack.get(DataComponents.CUSTOM_DATA);
		if (left != null && left.isEmpty()) {
			stack.remove(DataComponents.CUSTOM_DATA);
		}
	}

	/** The share of its damage {@code stack}'s blow still has: 1 while keen or only worn, less as it dulls. */
	public static float damageFactor(ItemStack stack) {
		int dull = dullness(stack);
		return dull <= WORN ? 1.0F : 1.0F - MOST_LOST * (dull - WORN) / (MAX - WORN);
	}

	/** {@code attacker}'s blow with the blade in hand, {@code damage} before the edge. */
	public static float cut(LivingEntity attacker, float damage) {
		return damage * damageFactor(attacker.getMainHandItem());
	}

	/** The tooltip word for {@code stack}'s edge, or null for what isn't a blade. */
	public static String key(ItemStack stack) {
		if (!edged(stack)) {
			return null;
		}
		int dull = dullness(stack);
		return dull >= BLUNT ? "tooltip.jaymospowers.edge.blunt" : dull >= DULL ? "tooltip.jaymospowers.edge.dull"
			: dull >= WORN ? "tooltip.jaymospowers.edge.worn" : "tooltip.jaymospowers.edge.keen";
	}
}
