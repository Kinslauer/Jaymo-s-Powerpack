package com.jaymospowers.baubles;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.compat.KingdomsCompat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * The ticking charm, a charm-slot bauble that tells the wearer how near the sick light is by sound alone: it clicks slowly
 * when the blight is far, quicker and higher as they come near, and fast and shrill at its heart. The blight is Kingdoms'
 * Irradiated monolith and the wolves it has changed ({@link KingdomsCompat#blightDistance}); without Kingdoms it stays
 * silent. The click is a vanilla sound at a varying pitch and rate, heard only by the wearer. There is no readout of any kind.
 */
public final class TickingCharm extends BaubleItem {
	/** Beyond this many blocks the charm is still. */
	static final double HEARING = 80.0;
	/** Ticks between clicks at the edge of hearing, and at the heart. */
	static final int SLOWEST = 40, FASTEST = 2;
	/** How sharply the rate climbs with nearness (above 1: slow until close). */
	static final double CURVE = 1.6;
	/** The click's pitch at the edge and at the heart, and its volume. */
	static final float LOW_PITCH = 0.8F, HIGH_PITCH = 1.9F, VOLUME = 0.4F;

	public static Item CHARM;
	/** The game time each wearer's next click is due (not saved). */
	private static final Map<UUID, Long> NEXT = new HashMap<>();

	private TickingCharm(Properties properties) {
		super(properties, Baubles.Kind.CHARM, List.of());
	}

	static void init() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("ticking_charm"));
		CHARM = Baubles.add(Registry.register(BuiltInRegistries.ITEM, key, new TickingCharm(new Item.Properties().setId(key).stacksTo(1).rarity(Rarity.UNCOMMON))));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				tick(player, server.getTickCount());
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> NEXT.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> NEXT.clear());
	}

	private static void tick(ServerPlayer player, long now) {
		if (!player.isAlive() || !Baubles.wears(player, CHARM)) {
			return;
		}
		if (now < NEXT.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		double distance = KingdomsCompat.blightDistance(player);
		if (distance >= HEARING) {
			// still: look again in a second
			NEXT.put(player.getUUID(), now + 20);
			return;
		}
		double near = 1.0 - Mth.clamp(distance / HEARING, 0.0, 1.0);
		int gap = (int)Math.round(Mth.lerp(Math.pow(near, 1.0 / CURVE), SLOWEST, FASTEST));
		NEXT.put(player.getUUID(), now + Math.max(FASTEST, gap));
		float pitch = Mth.lerp((float)near, LOW_PITCH, HIGH_PITCH);
		player.connection.send(new ClientboundSoundPacket(BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.TRIPWIRE_CLICK_ON),
			SoundSource.PLAYERS, player.getX(), player.getY(), player.getZ(), VOLUME, pitch, player.getRandom().nextLong()));
	}

	@Override
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
		builder.accept(Component.translatable("tooltip.jaymospowers.ticking_charm").withStyle(ChatFormatting.GOLD));
		super.appendHoverText(stack, context, display, builder, flag);
	}
}
