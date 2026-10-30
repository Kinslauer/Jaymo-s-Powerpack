package com.jaymospowers.tinkering.smithing;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.tinkering.PartType;
import com.jaymospowers.tinkering.TinkerTool;
import com.jaymospowers.tinkering.ToolPartItem;
import com.jaymospowers.tinkering.WeaponPartItem;
import com.mojang.serialization.Codec;
import java.util.Set;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;

/**
 * (Smithing) What happens to a head after the anvil: it comes out ROUGH (blades and axes) and is polished on a grindstone;
 * a worn tool is honed on one. Also the entry point that registers every extra of the finishing patch ({@link #register}),
 * and the tooltip lines for casts and heads. Tune the numbers here.
 */
public final class Finishing {
	/** A blade or axe head straight off the anvil: it can only make a tool of up to Expert until it is polished. */
	public static DataComponentType<Unit> ROUGH;
	/** How many times a tool has been honed: each costs a little of its maximum durability. */
	public static DataComponentType<Integer> HONED;
	/** The grade a cast copied from a graded head remembers (heads poured from it come one grade lower). */
	public static DataComponentType<String> CAST_GRADE;

	/** Weapon heads ({@link com.jaymospowers.tinkering.WeaponParts} part ids) that come off the anvil rough: swords and axes. */
	public static final Set<String> ROUGH_WEAPONS = Set.of("dagger_blade", "hatchet_head", "longsword_blade", "glaive_blade", "war_axe_head",
		"war_scythe_blade", "broadsword_blade", "claymore_blade", "cleaver_blade", "ninjato_blade", "saber_blade", "machete_blade",
		"rapier_blade", "kunai_blade", "knife_blade", "halberd_head", "battleaxe_head", "katana_blade", "kitchen_knife_blade", "saw_blade");
	/** Tool heads that come off the anvil rough. */
	public static final Set<PartType> ROUGH_PARTS = Set.of(PartType.SMALL_AXE_HEAD, PartType.SMALL_BLADE, PartType.BROAD_BLADE,
		PartType.BROAD_AXE_HEAD);
	/** The best grade a rough head can make a tool at. */
	public static final Grade ROUGH_CAP = Grade.EXPERT;

	/** Honing: the share of maximum durability one use restores, the share of maximum lost for each honing, and the most honings. */
	public static final float HONE_RESTORE = 0.25F, HONE_LOSS = 0.02F;
	public static final int MAX_HONES = 25;
	/** Ticks before the same grindstone job can repeat with the same item (the click repeats while held). */
	public static final int GRIND_COOLDOWN = 10;

	private Finishing() {
	}

	/** The one call from {@link Smithing#register()}: every part of the finishing patch hooks in from here. */
	public static void register() {
		ROUGH = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("rough"),
			DataComponentType.<Unit>builder().persistent(Unit.CODEC).networkSynchronized(Unit.STREAM_CODEC).build());
		HONED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("honed"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).networkSynchronized(ByteBufCodecs.VAR_INT).build());
		CAST_GRADE = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("cast_grade"),
			DataComponentType.<String>builder().persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8).build());

		Forging.FINISHES.add((smith, anvil, option, result) -> {
			if (roughKind(result)) {
				result.set(ROUGH, Unit.INSTANCE);
			}
		});
		Grades.ASSEMBLY_CAPS.add(head -> head.has(ROUGH) ? ROUGH_CAP : null);
		UseBlockCallback.EVENT.register(Finishing::grindstone);

		CastRules.register();
		Quenching.register();
		Edges.register();
		SmithTiers.register();
		StoneAnvil.register();
		Blueprints.register();
	}

	/** Whether a head of this kind comes off the anvil rough. */
	public static boolean roughKind(ItemStack head) {
		if (head.getItem() instanceof WeaponPartItem weapon) {
			return ROUGH_WEAPONS.contains(weapon.kind.part());
		}
		return head.getItem() instanceof ToolPartItem part && ROUGH_PARTS.contains(part.part);
	}

	/** Maximum durability left after the honings this stack has had (1 for none). */
	public static float honeFactor(ItemStack stack) {
		int honed = stack.getOrDefault(HONED, 0);
		return honed <= 0 ? 1.0F : Math.max(1.0F - HONE_LOSS * MAX_HONES, 1.0F - HONE_LOSS * honed);
	}

	/** Whether this is a worn tool or weapon a grindstone can hone. */
	static boolean honable(ItemStack stack) {
		return stack.isDamageableItem() && stack.isDamaged() && !stack.has(ROUGH) && !stack.has(DataComponents.EQUIPPABLE)
			&& (stack.getItem() instanceof TinkerTool || stack.has(DataComponents.WEAPON) || stack.has(DataComponents.TOOL));
	}

	/** Sneak-use on a vanilla grindstone: polish a rough head, or hone a worn tool. Plain use still opens the grindstone. */
	private static InteractionResult grindstone(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (!player.isSecondaryUseActive() || player.isSpectator() || !level.getBlockState(hit.getBlockPos()).is(Blocks.GRINDSTONE)) {
			return InteractionResult.PASS;
		}
		ItemStack stack = player.getItemInHand(hand);
		boolean polish = stack.has(ROUGH);
		if (!polish && !honable(stack)) {
			return InteractionResult.PASS;
		}
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.SUCCESS;
		}
		if (!(level instanceof ServerLevel server)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = hit.getBlockPos();
		if (polish) {
			polish(player, hand, stack);
		} else if (!hone(player, stack)) {
			return InteractionResult.SUCCESS;
		}
		player.getCooldowns().addCooldown(player.getItemInHand(hand), GRIND_COOLDOWN);
		server.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.8F, polish ? 1.2F : 1.0F);
		server.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 6, 0.25, 0.1, 0.25, 0.1);
		return InteractionResult.SUCCESS;
	}

	/** Takes the roughness off one head; one that is still hot loses a grade to the haste. */
	private static void polish(Player player, InteractionHand hand, ItemStack stack) {
		ItemStack one = stack.copyWithCount(1);
		stack.shrink(1);
		one.remove(ROUGH);
		Grade grade = Grades.of(one);
		if (one.has(Smithing.HEATED_AT) && grade != null && grade != Grade.POOR) {
			Grades.set(one, grade.lower());
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smithing.polished_hot", one.getHoverName(),
				grade.lower().displayName()));
		} else {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smithing.polished", one.getHoverName()));
		}
		if (stack.isEmpty()) {
			player.setItemInHand(hand, one);
		} else {
			player.getInventory().placeItemBackInInventory(one, Prediction.SERVER_ONLY);
		}
	}

	/** Restores a share of the tool's durability at the cost of a little of its maximum; false if it can take no more. */
	private static boolean hone(Player player, ItemStack stack) {
		int honed = stack.getOrDefault(HONED, 0);
		if (honed >= MAX_HONES) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.smithing.too_worn", stack.getHoverName()));
			return false;
		}
		stack.set(HONED, honed + 1);
		Grades.bake(stack);
		int heal = Math.max(1, Math.round(stack.getMaxDamage() * HONE_RESTORE));
		stack.setDamageValue(Math.max(0, stack.getDamageValue() - heal));
		Grades.bake(stack);
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.smithing.honed", stack.getHoverName(),
			Math.round((1.0F - honeFactor(stack)) * 100.0F)));
		return true;
	}

	/** Extra tooltip lines for heads and casts: too long to cast, rough, and a cast's remembered grade. */
	public static void tooltip(ItemStack stack, Consumer<Component> builder) {
		if (CastRules.hammerOnly(stack) || stack.getItem() instanceof com.jaymospowers.tinkering.smeltery.CastItem && CastRules.hammerOnlyShape(stack)) {
			builder.accept(Component.translatable("tooltip.jaymospowers.smithing.too_long_to_cast").withStyle(ChatFormatting.RED));
		}
		if (stack.has(ROUGH)) {
			builder.accept(Component.translatable("tooltip.jaymospowers.smithing.rough").withStyle(ChatFormatting.YELLOW));
		}
		Grade remembered = Grade.byId(stack.get(CAST_GRADE));
		if (remembered != null) {
			builder.accept(Component.translatable("tooltip.jaymospowers.smithing.cast_grade", remembered.displayName(),
				remembered.lower().displayName()).withStyle(ChatFormatting.GRAY));
		}
	}
}
