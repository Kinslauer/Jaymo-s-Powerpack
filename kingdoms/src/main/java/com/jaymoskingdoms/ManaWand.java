package com.jaymoskingdoms;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.237) The mana-infused wand. Andy: "A mana infused wand would be good for transferring extra stored mana." A short
 * wand with an amethyst at its tip that holds up to {@link #HOLDS} mana (in the stack, so it keeps it wherever it goes).
 * Only the attuned can work it:
 * <ul>
 * <li>Sneak and use: pour {@link #POUR} of your mana into it.</li>
 * <li>Use, looking at an attuned player: their share of what it holds goes into them.</li>
 * <li>Use at nothing: draw it back into yourself.</li>
 * </ul>
 * Mana poured into anyone past full strains them, as crushed amethyst does ({@link Mana#give}).
 */
public final class ManaWand extends Item {
	/** The most a wand holds, and how much one pour puts in. */
	static final float HOLDS = 100.0F, POUR = 25.0F;
	/** The wait between uses (ticks). */
	static final int COOLDOWN = 10;
	/** The custom data key the stored mana lives under. */
	static final String KEY = "jaymoskingdoms_mana";
	/** The bar's blue (mana's). */
	static final int BAR = 0x5A9CFF;

	public static Item WAND;

	ManaWand(Properties properties) {
		super(properties);
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("mana_wand"));
		WAND = Registry.register(BuiltInRegistries.ITEM, key, new ManaWand(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> output.insertAfter(Arcana.HEALER_STAFF, WAND));
	}

	public static float stored(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data == null ? 0.0F : data.copyTag().getFloatOr(KEY, 0.0F);
	}

	static void store(ItemStack stack, float mana) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putFloat(KEY, Math.max(0.0F, Math.min(HOLDS, mana))));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!Mana.attuned(player)) {
			if (player instanceof ServerPlayer sp) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.wand.unattuned").withStyle(ChatFormatting.BLUE));
			}
			return InteractionResult.FAIL;
		}
		ItemStack wand = player.getItemInHand(hand);
		if (player instanceof ServerPlayer sp && level instanceof ServerLevel server) {
			if (sp.isShiftKeyDown()) {
				pour(sp, server, wand);
			} else {
				LivingEntity sighted = Arcana.sighted(sp);
				release(sp, server, wand, sighted instanceof ServerPlayer other && Mana.attuned(other) ? other : sp);
			}
			sp.getCooldowns().addCooldown(wand, COOLDOWN);
		}
		return InteractionResult.SUCCESS;
	}

	/** Some of the player's mana into the wand. */
	private static void pour(ServerPlayer player, ServerLevel level, ItemStack wand) {
		float held = stored(wand);
		float amount = Math.min(POUR, Math.min(HOLDS - held, Mana.current(player)));
		if (amount <= 0.0F) {
			player.sendOverlayMessage(Component.translatable(held >= HOLDS ? "message.jaymoskingdoms.wand.full" : "message.jaymoskingdoms.wand.nothing")
				.withStyle(ChatFormatting.BLUE));
			return;
		}
		Mana.set(player, Mana.current(player) - amount);
		store(wand, held + amount);
		Mana.ripple(player, Mana.WILL);
		Mana.sendSeen(level, Mana.MOTE, player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.6F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.wand.poured", Math.round(amount), Math.round(held + amount), Math.round(HOLDS))
			.withStyle(ChatFormatting.BLUE));
	}

	/** What the wand holds into {@code to} (the player, or the attuned player they look at), as much as they can take. */
	private static void release(ServerPlayer player, ServerLevel level, ItemStack wand, ServerPlayer to) {
		float held = stored(wand);
		if (held <= 0.0F) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.wand.empty").withStyle(ChatFormatting.BLUE));
			return;
		}
		float in = Mana.give(to, held);
		if (in <= 0.0F) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.wand.brimming", to.getDisplayName()).withStyle(ChatFormatting.BLUE));
			return;
		}
		store(wand, held - in);
		Mana.ripple(player, Mana.WILL);
		if (to != player) {
			// a line of motes from the wand's tip to them, seen by the attuned
			Vec3 from = player.getEyePosition().add(player.getLookAngle().scale(0.6));
			Vec3 at = to.getBoundingBox().getCenter();
			int steps = (int)Math.ceil(from.distanceTo(at) * 3.0);
			for (int i = 0; i <= steps; i++) {
				Vec3 p = from.lerp(at, i / (double)steps);
				Mana.sendSeen(level, Mana.MOTE, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
			}
			to.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.wand.received", Math.round(in), player.getDisplayName()).withStyle(ChatFormatting.BLUE));
		}
		Mana.sendSeen(level, Mana.MOTE, to.getX(), to.getY() + 1.0, to.getZ(), 12, 0.3, 0.5, 0.3, 0.03);
		level.playSound(null, to.getX(), to.getY(), to.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 1.4F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.wand.released", Math.round(in), Math.round(held - in), Math.round(HOLDS))
			.withStyle(ChatFormatting.BLUE));
	}

	@Override
	public boolean isBarVisible(ItemStack stack) {
		return stored(stack) > 0.0F;
	}

	@Override
	public int getBarWidth(ItemStack stack) {
		return Math.round(13.0F * stored(stack) / HOLDS);
	}

	@Override
	public int getBarColor(ItemStack stack) {
		return BAR;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag) {
		lines.accept(Component.translatable("item.jaymoskingdoms.mana_wand.holds", Math.round(stored(stack)), Math.round(HOLDS)).withStyle(ChatFormatting.BLUE));
		lines.accept(Component.translatable("item.jaymoskingdoms.mana_wand.use").withStyle(ChatFormatting.GRAY));
	}
}
