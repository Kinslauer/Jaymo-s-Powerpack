package com.jaymospowers.power.chrono;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.fx.MagicFx;
import com.jaymospowers.power.xenoform.XenoformPower;
import com.mojang.serialization.Codec;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
 * (Awakening pick "Speedster": "its prism (lightning colour) comes with it") The Speed Prism: a speedster (Chrono, or a
 * Xenoform holder, whose Velox runs on the same code) uses it to turn the colour of their own speed lightning, one step
 * through {@link #COLOURS} a use and then back to their power's own. The choice is {@link #COLOUR}: saved, kept through
 * death, synced to everyone (the streak is drawn on each client, {@code ChronoStreak}). Never used up. Anyone else is told
 * it does nothing for them.
 */
public final class SpeedPrism extends Item {
	// ---- tuning ----
	/** The colours a prism turns the lightning, in the order a use steps through them (lang: {@code prism.jaymospowers.<name>}). */
	public static final int[] COLOURS = {0xFF3B30, 0xFF8A1E, 0x5CF07A, 0x35E0D0, 0x4F7BFF, 0xA855FF, 0xFF5FC8, 0xF2F2FF};
	static final String[] NAMES = {"red", "orange", "green", "teal", "blue", "violet", "pink", "white"};
	/** How far toward white the lightning's core and its afterimages are from the colour itself (0 to 1). */
	public static final float CORE_WHITEN = 0.75F, GHOST_WHITEN = 0.3F;

	/** The colour picked: 1 to {@link #COLOURS}' length (absent: the power's own). Saved, kept through death, synced to all. */
	public static final AttachmentType<Integer> COLOUR = AttachmentRegistry.create(JaymosPowers.id("speed_prism_colour"),
		builder -> builder.persistent(Codec.INT).copyOnDeath().syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));

	public static Item PRISM;

	public SpeedPrism(Item.Properties properties) {
		super(properties);
	}

	static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("speed_prism"));
		PRISM = Registry.register(BuiltInRegistries.ITEM, key, new SpeedPrism(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> output.accept(PRISM));
		EnergyBarItem.init();
	}

	/** Whether {@code player} is a speedster: a Chrono, or a Xenoform holder (Velox is one of its forms). Either side. */
	public static boolean speedster(Player player) {
		return Powers.has(player, ChronoPower.INSTANCE) || XenoformPower.has(player);
	}

	/** The prism colour {@code player} picked (1 to {@link #COLOURS}' length), or 0 for their power's own. Either side. */
	public static int picked(Player player) {
		Integer picked = player.getAttached(COLOUR);
		return picked == null ? 0 : Mth.clamp(picked, 0, COLOURS.length);
	}

	/** The colour of {@code player}'s speed lightning: the prism's if one is picked, else {@code own}. Either side. */
	public static int colour(Player player, int own) {
		int picked = picked(player);
		return picked == 0 ? own : COLOURS[picked - 1];
	}

	/** {@code rgb} mixed {@code share} of the way to white. */
	public static int whiten(int rgb, float share) {
		int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
		return Math.round(r + (255 - r) * share) << 16 | Math.round(g + (255 - g) * share) << 8 | Math.round(b + (255 - b) * share);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			if (!speedster(serverPlayer)) {
				serverPlayer.sendOverlayMessage(Component.translatable("message.jaymospowers.speed_prism.useless").withStyle(ChatFormatting.GRAY));
				serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 0.6F, 0.7F);
				return InteractionResult.SUCCESS;
			}
			int next = (picked(serverPlayer) + 1) % (COLOURS.length + 1);
			if (next == 0) {
				serverPlayer.removeAttached(COLOUR);
				serverPlayer.sendOverlayMessage(Component.translatable("message.jaymospowers.speed_prism.own"));
			} else {
				serverPlayer.setAttached(COLOUR, next);
				serverPlayer.sendOverlayMessage(Component.translatable("message.jaymospowers.speed_prism.set",
					Component.translatable("prism.jaymospowers." + NAMES[next - 1]).withStyle(style -> style.withColor(TextColor.fromRgb(COLOURS[next - 1])))));
				serverLevel.sendParticles(MagicFx.spark(COLOURS[next - 1], 0.8F), player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.3, 0.5, 0.3, 0.0);
			}
			serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.9F,
				0.8F + next * 0.1F);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.speed_prism").withStyle(ChatFormatting.GRAY));
	}
}
