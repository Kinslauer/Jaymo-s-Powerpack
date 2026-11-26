package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Artificer (Otherworld Origins' artificer, ARR: the idea only, our own code): a maker who works gear in the hand and brews
 * what no one else can. Beside the Tinkerer's benches and gadgets, nothing here wants a station.
 * <ul>
 * <li><b>Reforge</b> (the class key, with gear in the main hand): {@link #REFORGE_SHARDS} amethyst shards re-roll the gear's quality
 * ({@link FoundQuality}, airy or fiery, a grade of 1 to 3) and mend a tenth of what it has lost.</li>
 * <li><b>Infuse</b> (sneak and the class key, gear in the main hand and a reagent in the off hand): {@link #INFUSE_REAGENTS} blaze powder
 * make the gear fiery, feathers make it airy, at a grade the class tree raises.</li>
 * <li><b>Elixirs</b> (sneak and use a cauldron of water with a glass bottle in the main hand and a reagent in the off hand): an
 * elixir with two effects ({@link #ELIXIRS}) that only an Artificer knows to brew; anyone may drink one.</li>
 * </ul>
 * The tree ({@link #KEEN}, {@link #DEEP}, {@link #LONG}, {@link #TWICE}, {@link #MENDING}) is in {@code trees/ArtificerTree}.
 */
public final class Artificers {
	/** Amethyst shards a reforge takes; reagents an infusion takes. */
	static final int REFORGE_SHARDS = 2, INFUSE_REAGENTS = 2;
	/** A reforge mends this share of the gear's life (and, with Mending Hands, this). */
	static final float REFORGE_MEND = 0.1F, MENDING_MEND = 0.4F;
	/** Keen Eye: the chance a rank adds a grade to a reforge. */
	static final float KEEN_PER_RANK = 0.15F;
	/** Long Brew: the share of duration each rank adds. */
	static final float LONG_PER_RANK = 0.5F;

	public static final String KEEN = "art_keen", DEEP = "art_deep", LONG = "art_long", TWICE = "art_twice", MENDING = "art_mending";
	/** Marks an elixir. */
	static final String ELIXIR_TAG = "jaymoskingdoms_elixir";

	/** One elixir: its reagent, its name (lang {@code item.jaymoskingdoms.elixir.<key>}), its colour and its two effects. */
	record Elixir(Item reagent, String key, int colour, List<MobEffectInstance> effects) {
	}

	/** The elixirs, by reagent. Durations in ticks. */
	static final List<Elixir> ELIXIRS = List.of(
		new Elixir(Items.BLAZE_POWDER, "embers", 0xE8742A, List.of(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 9600), new MobEffectInstance(MobEffects.STRENGTH, 3600))),
		new Elixir(Items.GOLDEN_CARROT, "sight", 0xE8C84A, List.of(new MobEffectInstance(MobEffects.NIGHT_VISION, 12000), new MobEffectInstance(MobEffects.LUCK, 12000))),
		new Elixir(Items.PHANTOM_MEMBRANE, "wings", 0x9AB0D8, List.of(new MobEffectInstance(MobEffects.SLOW_FALLING, 9600), new MobEffectInstance(MobEffects.SPEED, 3600))),
		new Elixir(Items.GLISTERING_MELON_SLICE, "mending", 0xE86A8A, List.of(new MobEffectInstance(MobEffects.REGENERATION, 900, 1), new MobEffectInstance(MobEffects.ABSORPTION, 2400))));

	private Artificers() {
	}

	static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND || !sp.isShiftKeyDown() || !Classes.is(sp, PlayerClass.ARTIFICER)
				|| !sp.getMainHandItem().is(Items.GLASS_BOTTLE)) {
				return InteractionResult.PASS;
			}
			BlockState state = level.getBlockState(hit.getBlockPos());
			if (!state.is(Blocks.WATER_CAULDRON)) {
				return InteractionResult.PASS;
			}
			Elixir elixir = null;
			for (Elixir e : ELIXIRS) {
				if (sp.getOffhandItem().is(e.reagent())) {
					elixir = e;
				}
			}
			if (elixir == null) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.artificer.no_reagent"));
				return InteractionResult.SUCCESS;
			}
			brew(sp, (ServerLevel) level, hit.getBlockPos(), state, elixir);
			return InteractionResult.SUCCESS;
		});
	}

	static void clear() {
	}

	static void begin(ServerPlayer player) {
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.artificer.begin").withStyle(net.minecraft.ChatFormatting.GRAY));
	}

	private static void brew(ServerPlayer player, ServerLevel level, net.minecraft.core.BlockPos pos, BlockState state, Elixir elixir) {
		boolean twice = ClassTrees.has(player, TWICE) > 0 && player.getMainHandItem().getCount() >= 2;
		float longer = 1.0F + LONG_PER_RANK * ClassTrees.has(player, LONG);
		List<MobEffectInstance> effects = new java.util.ArrayList<>();
		for (MobEffectInstance e : elixir.effects()) {
			effects.add(new MobEffectInstance(e.getEffect(), Math.round(e.getDuration() * longer), e.getAmplifier()));
		}
		ItemStack potion = new ItemStack(Items.POTION, twice ? 2 : 1);
		potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(elixir.colour()), effects, Optional.empty()));
		potion.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.elixir." + elixir.key()).withStyle(net.minecraft.ChatFormatting.AQUA));
		CustomData.update(DataComponents.CUSTOM_DATA, potion, tag -> tag.putString(ELIXIR_TAG, elixir.key()));
		if (!player.isCreative()) {
			player.getMainHandItem().shrink(twice ? 2 : 1);
			player.getOffhandItem().shrink(1);
		}
		LayeredCauldronBlock.lowerFillLevel(state, level, pos);
		if (!player.getInventory().add(potion)) {
			player.drop(potion, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 0.8F, 1.3F);
		Fx.burst(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, elixir.colour(), 0.8F, 14, 0.25, 0.3, 0.25);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.artificer.brewed", Component.translatable("item.jaymoskingdoms.elixir." + elixir.key())));
		ClassTrees.give(player, PlayerClass.ARTIFICER, ClassTrees.XP_MADE);
	}

	/** The class key: reforge the gear in hand; with sneak, infuse it. */
	static void key(ServerPlayer player, boolean sneak) {
		ItemStack gear = player.getMainHandItem();
		if (!FoundQuality.gear(gear)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.artificer.no_gear"));
			return;
		}
		if (sneak) {
			infuse(player, gear);
		} else {
			reforge(player, gear);
		}
	}

	private static void reforge(ServerPlayer player, ItemStack gear) {
		if (!take(player, Items.AMETHYST_SHARD, REFORGE_SHARDS)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.artificer.shards", REFORGE_SHARDS));
			return;
		}
		var random = player.getRandom();
		int grade = FoundQuality.grade(random);
		float keen = KEEN_PER_RANK * ClassTrees.has(player, KEEN);
		if (grade < 3 && random.nextFloat() < keen) {
			grade++;
		}
		FoundQuality.put(gear, random.nextBoolean() ? FoundQuality.FIRE : FoundQuality.AIR, grade);
		mend(gear, ClassTrees.has(player, MENDING) > 0 ? MENDING_MEND : REFORGE_MEND);
		done(player, gear);
	}

	private static void infuse(ServerPlayer player, ItemStack gear) {
		ItemStack off = player.getOffhandItem();
		String kind = off.is(Items.BLAZE_POWDER) ? FoundQuality.FIRE : off.is(Items.FEATHER) ? FoundQuality.AIR : null;
		if (kind == null || off.getCount() < INFUSE_REAGENTS && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.artificer.reagents", INFUSE_REAGENTS));
			return;
		}
		if (!player.isCreative()) {
			off.shrink(INFUSE_REAGENTS);
		}
		FoundQuality.put(gear, kind, Math.min(3, 1 + ClassTrees.has(player, DEEP) + (player.getRandom().nextFloat() < 0.25F ? 1 : 0)));
		done(player, gear);
	}

	private static void done(ServerPlayer player, ItemStack gear) {
		if (player.level() instanceof ServerLevel level) {
			Fx.burst(level, player.getX(), player.getY() + 1.2, player.getZ(), 0xC8A0FF, 1.0F, 20, 0.4, 0.5, 0.4);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.artificer.done", gear.getHoverName()));
		ClassTrees.give(player, PlayerClass.ARTIFICER, ClassTrees.XP_INFUSE);
	}

	private static void mend(ItemStack gear, float share) {
		gear.setDamageValue(Math.max(0, gear.getDamageValue() - Math.round(gear.getMaxDamage() * share)));
	}

	private static boolean take(ServerPlayer player, Item item, int n) {
		if (player.isCreative()) {
			return true;
		}
		int have = 0;
		for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
			if (s.is(item)) {
				have += s.getCount();
			}
		}
		if (have < n) {
			return false;
		}
		for (ItemStack s : player.getInventory().getNonEquipmentItems()) {
			if (n > 0 && s.is(item)) {
				int d = Math.min(n, s.getCount());
				s.shrink(d);
				n -= d;
			}
		}
		return true;
	}
}
