package com.jaymoskitchen.farm;

import com.jaymoskitchen.JaymosKitchen;
import com.jaymoskitchen.climate.Climate;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Raw milk (Raw Milk's idea, CC0; our own code). Milk is raw as it comes from the animal: every bucket and bottle of milk
 * carries {@link #RAW} until it has been heated, so whatever milked it (a bucket, Jaymo's Powers' clay bucket, a
 * familiar cow's extra bucket, a milk jar) it's raw. Drunk raw it may turn your stomach ({@link #SICK_CHANCE}: Hunger, and
 * now and then Nausea), after it has cleared what ails you as milk does. Heated it's pasteurised and safe: use the bucket
 * or bottle on anything that boils water ({@link Climate#purifiesAt}: a lit campfire, firepit, hearth, or a pot or pan on
 * heat) or on a water cauldron over a fire, or cook the bucket in a furnace or smoker. Every use milk had stays: cakes,
 * cheese, bottles poured from a bucket (raw from a raw bucket) and every dish take either kind. Some villagers would
 * rather have it raw and pay well for a bucket fresh from the cow (Jaymo's Kingdoms' own wants).
 */
public final class RawMilk {
	/** The chance raw milk makes you sick, and of Nausea on top. */
	static final float SICK_CHANCE = 0.35F, NAUSEA_CHANCE = 0.3F;
	/** How long the sickness lasts (ticks), and how bad. */
	static final int SICK_TICKS = 30 * 20, NAUSEA_TICKS = 8 * 20;
	static final int SICK_AMPLIFIER = 0;
	/** Jaymo's Powers' clay bucket of milk, raw too. */
	static final Identifier CLAY_MILK = Identifier.fromNamespaceAndPath("jaymospowers", "clay_milk_bucket");

	/** On milk that hasn't been heated (every milk item starts with it; heating takes it off). */
	public static DataComponentType<Unit> RAW;

	private RawMilk() {
	}

	public static void register() {
		RAW = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosKitchen.id("raw_milk"),
			DataComponentType.<Unit>builder().persistent(Unit.CODEC).networkSynchronized(StreamCodec.unit(Unit.INSTANCE)).build());
		DefaultItemComponentEvents.MODIFY.register(context -> {
			context.modify(Items.MILK_BUCKET, builder -> builder.set(RAW, Unit.INSTANCE));
			context.modify(com.jaymoskitchen.delight.DelightDishes.MILK_BOTTLE, builder -> builder.set(RAW, Unit.INSTANCE));
			BuiltInRegistries.ITEM.getOptional(CLAY_MILK).ifPresent(clay -> context.modify(clay, builder -> builder.set(RAW, Unit.INSTANCE)));
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			ItemStack held = player.getItemInHand(hand);
			// (sneaking puts things on a station's worktop instead)
			if (hand != InteractionHand.MAIN_HAND || player.isSpectator() || player.isShiftKeyDown() || !raw(held)) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			if (!Climate.purifiesAt(level, pos) && !Climate.boiledCauldron(level, pos)) {
				return InteractionResult.PASS;
			}
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
				heat(server, sp, pos, held);
			}
			return InteractionResult.SUCCESS;
		});
	}

	/** Whether {@code stack} is milk at all (raw or heated). */
	public static boolean milk(ItemStack stack) {
		return RAW != null && !stack.isEmpty() && stack.getItem().components().has(RAW);
	}

	/** Whether {@code stack} is raw milk. */
	public static boolean raw(ItemStack stack) {
		return RAW != null && !stack.isEmpty() && stack.has(RAW);
	}

	/** One of {@code held} heated through over the fire at {@code pos}: pasteurised. */
	private static void heat(ServerLevel level, ServerPlayer player, BlockPos pos, ItemStack held) {
		if (held.getCount() == 1) {
			held.remove(RAW);
		} else {
			ItemStack one = held.split(1);
			one.remove(RAW);
			if (!player.getInventory().add(one)) {
				player.drop(one, false, net.minecraft.util.Prediction.SERVER_ONLY);
			}
		}
		level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.5F, 1.3F);
		level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.2F, 1.8F);
		level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 5, 0.15, 0.1, 0.15, 0.02);
	}

	/** Server: {@code player} has just drunk {@code stack} (its effects applied, the stack still whole). Raw, it may sicken them. */
	public static void drunk(ServerPlayer player, ItemStack stack) {
		if (!raw(stack) || player.getRandom().nextFloat() >= SICK_CHANCE) {
			return;
		}
		player.addEffect(new MobEffectInstance(MobEffects.HUNGER, SICK_TICKS, SICK_AMPLIFIER));
		if (player.getRandom().nextFloat() < NAUSEA_CHANCE) {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, NAUSEA_TICKS));
		}
	}
}
