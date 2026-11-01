package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * (0.18.92) Andy: "only these two bands will exist in the world, either player killing the other, will remove the band
 * and give it to them. they can then do whatever they want with it." The world keeps count of its one Xeno-Band (the
 * rival band, patch 8, will keep its own): who wears it, and how many band items are loose (in an inventory, a chest,
 * or lying on the ground). A fallen star holds the band only while nobody wears it and none is loose.
 * <ul>
 * <li>Put on (the item used): worn; one loose item fewer.</li>
 * <li>A downed wearer: another player taps them (use, let go at once; holding is a revive) and tears the band off
 * themselves (Andy: "you have to tear it off yourself, with a tap"). The power ends and the band is the taker's, an item,
 * theirs to wear, keep, trade or throw away. Killed outright, the wearer keeps it.</li>
 * <li>A loose band destroyed (lava, fire, the void, despawned, killed): back among the stars.</li>
 * <li>The power taken away by hand (the powers screen, {@code /power}): back among the stars.</li>
 * </ul>
 * Kept on the overworld as a saved attachment. Players who already wore a band before this are counted as wearers
 * when they next join, so older worlds with more than one keep theirs, and no new one falls while any is out.
 */
public final class XenoBands {
	/** The world's band: everyone wearing one, and how many band items are loose. */
	public record Count(List<String> wearers, int loose) {
		public static final Count NONE = new Count(List.of(), 0);
		public static final Codec<Count> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.listOf().optionalFieldOf("wearers", List.of()).forGetter(Count::wearers),
				Codec.INT.optionalFieldOf("loose", 0).forGetter(Count::loose))
			.apply(i, Count::new));
	}

	public static final AttachmentType<Count> COUNT = AttachmentRegistry.create(JaymosPowers.id("xeno_bands"),
		builder -> builder.persistent(Count.CODEC));
	/** (0.18.125, patch 8) The rival band's own count: its wearers, and its loose items (the hunter's wrist counts as loose). */
	public static final AttachmentType<Count> RIVAL_COUNT = AttachmentRegistry.create(JaymosPowers.id("rival_bands"),
		builder -> builder.persistent(Count.CODEC));
	private static final String UPGRADES_KEY = "xeno_upgrades", STYLE_KEY = "xeno_style";

	private static final String ALIENS_KEY = "xeno_aliens";

	private XenoBands() {
	}

	static void init() {
		// a wearer from before the count: counted now
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (XenoformPower.has(handler.player)) {
				worn(handler.player, false);
			}
		});
		// a tap on a downed wearer tears the band off
		com.jaymospowers.core.wounds.Downed.TAPPED_LISTENERS.add((taker, body) -> {
			if (body instanceof ServerPlayer player && player != taker && XenoformPower.has(player)) {
				takenBy(player, taker);
			}
		});
		// a loose band destroyed goes back to the stars (one picked up leaves an emptied stack behind: not destroyed)
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof ItemEntity item && entity.getRemovalReason() != null && entity.getRemovalReason().shouldDestroy()
				&& XenoBandItem.isBand(item.getItem()) && !item.getItem().isEmpty()) {
				destroyed(level.getServer(), item.getItem().is(XenoBandItem.RIVAL));
			}
		});
	}

	public static Count count(MinecraftServer server) {
		return count(server, false);
	}

	public static Count count(MinecraftServer server, boolean rival) {
		Count count = server.overworld().getAttached(rival ? RIVAL_COUNT : COUNT);
		return count == null ? Count.NONE : count;
	}

	private static void set(MinecraftServer server, boolean rival, Count count) {
		server.overworld().setAttached(rival ? RIVAL_COUNT : COUNT, count);
	}

	/** (0.18.125) Whether the rival band is out of the world entirely (no wearer, none loose): a new hunter may come with it. */
	public static boolean rivalFree(MinecraftServer server) {
		Count count = count(server, true);
		return count.wearers().isEmpty() && count.loose() <= 0;
	}

	/** Whether a fallen star may hold the band: nobody wears it and none is loose. */
	public static boolean available(MinecraftServer server) {
		Count count = count(server);
		return count.wearers().isEmpty() && count.loose() <= 0;
	}

	/** A band came out of a star (or was handed out): one more loose. */
	static void loosed(MinecraftServer server) {
		loosed(server, false);
	}

	static void loosed(MinecraftServer server, boolean rival) {
		Count count = count(server, rival);
		set(server, rival, new Count(count.wearers(), count.loose() + 1));
	}

	/** {@code player} wears it now ({@code fromItem}: a loose band was used to put it on). */
	static void worn(ServerPlayer player, boolean fromItem) {
		MinecraftServer server = player.level().getServer();
		boolean rival = XenoformState.rival(player);
		Count count = count(server, rival);
		String id = player.getUUID().toString();
		List<String> wearers = new ArrayList<>(count.wearers());
		if (!wearers.contains(id)) {
			wearers.add(id);
		}
		set(server, rival, new Count(List.copyOf(wearers), fromItem ? Math.max(0, count.loose() - 1) : count.loose()));
	}

	/** {@code player} no longer wears it (the power ended). */
	static void unworn(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		String id = player.getUUID().toString();
		for (boolean rival : new boolean[] {false, true}) {
			Count count = count(server, rival);
			if (count.wearers().contains(id)) {
				List<String> wearers = new ArrayList<>(count.wearers());
				wearers.remove(id);
				set(server, rival, new Count(List.copyOf(wearers), count.loose()));
			}
		}
	}

	static void destroyed(MinecraftServer server, boolean rival) {
		Count count = count(server, rival);
		if (count.loose() > 0) {
			set(server, rival, new Count(count.wearers(), count.loose() - 1));
		}
	}

	/** The band is torn off {@code player}'s wrist by {@code killer}, with every alien it held. */
	private static void takenBy(ServerPlayer player, ServerPlayer killer) {
		List<String> aliens = XenoformState.band(player).unlocked();
		boolean rival = XenoformState.rival(player);
		BandUpgrades.Upgrades upgrades = player.getAttached(BandUpgrades.UPGRADES);
		BandStyle.Style style = player.getAttached(BandStyle.STYLE);
		Powers.set(player, null);
		player.removeAttached(XenoformState.BAND);
		player.removeAttached(BandUpgrades.UPGRADES);
		loosed(killer.level().getServer(), rival);
		killer.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		killer.getInventory().placeItemBackInInventory(styled(item(aliens, rival, upgrades), style), net.minecraft.util.Prediction.SERVER_ONLY);
		killer.sendSystemMessage(Component.translatable("message.jaymospowers.xenoform.band_won", player.getDisplayName()).withStyle(ChatFormatting.GREEN));
		player.sendSystemMessage(Component.translatable("message.jaymospowers.xenoform.band_lost", killer.getDisplayName()).withStyle(ChatFormatting.RED));
		ServerLevel level = killer.level();
		level.playSound(null, killer.getX(), killer.getY(), killer.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0F, 0.8F);
		level.playSound(null, killer.getX(), killer.getY(), killer.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8F, 1.4F);
	}

	/** A band item that still holds {@code aliens} (a band taken off a wearer keeps what it learned). */
	static ItemStack item(List<String> aliens) {
		return item(aliens, false, null);
	}

	/** (0.18.125) Either band, holding its aliens and (patch 7) its reforge, modules and skills. */
	static ItemStack item(List<String> aliens, boolean rival, BandUpgrades.@org.jspecify.annotations.Nullable Upgrades upgrades) {
		ItemStack stack = new ItemStack(rival ? XenoBandItem.RIVAL : XenoBandItem.BAND);
		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		if (!aliens.isEmpty()) {
			tag.putString(ALIENS_KEY, String.join(",", aliens));
		}
		if (upgrades != null) {
			BandUpgrades.Upgrades.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, upgrades).result().ifPresent(t -> tag.put(UPGRADES_KEY, t));
		}
		if (!tag.isEmpty()) {
			stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
		}
		return stack;
	}

	/** (0.18.126) {@code stack} carrying how its band was dressed ({@link BandStyle}), when it was. */
	static ItemStack styled(ItemStack stack, BandStyle.@org.jspecify.annotations.Nullable Style style) {
		if (style == null || style.equals(BandStyle.Style.NONE)) {
			return stack;
		}
		net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		net.minecraft.nbt.CompoundTag tag = data == null ? new net.minecraft.nbt.CompoundTag() : data.copyTag();
		BandStyle.Style.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, style).result().ifPresent(t -> tag.put(STYLE_KEY, t));
		stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
		return stack;
	}

	/** (0.18.126) How a band item's band was dressed, or null for plain. */
	static BandStyle.@org.jspecify.annotations.Nullable Style styleOf(ItemStack stack) {
		net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		net.minecraft.nbt.Tag tag = data.copyTag().get(STYLE_KEY);
		return tag == null ? null : BandStyle.Style.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).result().orElse(null);
	}

	/** (0.18.125) The reforge, modules and skills a band item carries, or null for none. */
	static BandUpgrades.@org.jspecify.annotations.Nullable Upgrades upgradesOf(ItemStack stack) {
		net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		if (data == null) {
			return null;
		}
		net.minecraft.nbt.Tag tag = data.copyTag().get(UPGRADES_KEY);
		return tag == null ? null : BandUpgrades.Upgrades.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag).result().orElse(null);
	}

	/** The aliens a band item holds (none for one fresh from a star). */
	static List<String> aliensOf(ItemStack stack) {
		net.minecraft.world.item.component.CustomData data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
		String held = data == null ? "" : data.copyTag().getStringOr(ALIENS_KEY, "");
		return held.isEmpty() ? List.of() : List.of(held.split(","));
	}

	/** For the debug command: forget every wearer and loose band, so the next star may carry it. */
	static void reset(MinecraftServer server) {
		set(server, false, Count.NONE);
		set(server, true, Count.NONE);
	}
}
