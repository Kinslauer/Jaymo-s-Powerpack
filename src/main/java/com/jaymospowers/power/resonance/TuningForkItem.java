package com.jaymospowers.power.resonance;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * The Tuning Fork (copper ingot + 2 amethyst shards): strike it (right click) and for {@code LISTEN_TICKS} you hear every
 * creature within {@code RANGE} blocks, outlined through walls on your screen ({@code RANGE_RESONANCE} for a Resonance
 * user). Anyone can use it. The <b>Resonant Fork</b> (a Tuning Fork and an echo shard at a smithing table) strikes the
 * same, and is what a Resonance user holds when they kill a Warden to ascend.
 */
public final class TuningForkItem extends Item {
	public static final double RANGE = 24.0;
	public static final double RANGE_RESONANCE = 48.0;
	public static final int LISTEN_TICKS = 60;
	/** Between strikes (ticks). */
	public static final int COOLDOWN = 100;

	public static Item TUNING_FORK;
	public static Item RESONANT_FORK;

	private final boolean resonant;

	public TuningForkItem(Item.Properties properties, boolean resonant) {
		super(properties);
		this.resonant = resonant;
	}

	static void init() {
		TUNING_FORK = register("resonance_tuning_fork", false, Rarity.COMMON);
		RESONANT_FORK = register("resonance_resonant_fork", true, Rarity.RARE);
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			output.accept(TUNING_FORK);
			output.accept(RESONANT_FORK);
		});
	}

	private static Item register(String name, boolean resonant, Rarity rarity) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, new TuningForkItem(new Item.Properties().setId(key).stacksTo(1).rarity(rarity), resonant));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			boolean resonance = Powers.has(serverPlayer, ResonancePower.INSTANCE);
			ResonanceEcho.listen(serverPlayer, resonance ? RANGE_RESONANCE : RANGE, LISTEN_TICKS);
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F,
				resonant ? 0.8F : 1.4F);
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.8F);
			player.getCooldowns().addCooldown(stack, COOLDOWN);
			if (resonance) {
				Mastery.used(serverPlayer);
			}
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		String name = resonant ? "resonance_resonant_fork" : "resonance_tuning_fork";
		builder.accept(Component.translatable("item.jaymospowers." + name + ".lore1").withStyle(ChatFormatting.GRAY));
		if (resonant) {
			builder.accept(Component.translatable("item.jaymospowers." + name + ".lore2").withStyle(ChatFormatting.DARK_AQUA, ChatFormatting.ITALIC));
		}
	}
}
