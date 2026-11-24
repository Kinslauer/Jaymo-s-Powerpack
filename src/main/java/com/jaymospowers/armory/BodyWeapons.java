package com.jaymospowers.armory;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.compat.ToolBeltCompat;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "weapons should always go to a specific place on the body unless the player toggles otherwise ... I want to look
 * like edward kenway with all his weapons visible". Each kind of weapon on the hotbar has its own homes on the body, taken
 * in hotbar order: swords at the hips, flintlocks on the chest (two, crossed on the strap) and then in the sash at the
 * front of the belt (two more), long guns, bows, crossbows, tridents and spears crossed on the back, a shield on the back
 * over them, a tool at the small of the back. Homes are given out over the whole hotbar, the item in hand included, and
 * the one in hand leaves its home empty, so drawing a pistol never moves the others. The server works the homes out and
 * syncs them to everyone ({@link #SHOWN}); {@code client/armory/BodyWeaponsLayer} draws them. A hidden blade (Jaymo's
 * Kingdoms) is worn on the wrist and draws itself.
 * <p>The toggle ({@link #FREE}, on the Character page): free placement hands the body back to YDM's Weapon Master as
 * before (up to 3 items by hotbar slot, all of them with a tool belt), and this shows nothing.
 */
public final class BodyWeapons {
	/** How many items a body shows in set places without a worn tool belt (a Kenway: two swords, four pistols). */
	public static final int MAX_SHOWN = 6;

	/** The places on the body. The client draws each one ({@code BodyWeaponsLayer}). */
	public enum Home {
		HIP_LEFT, HIP_RIGHT, CHEST_LEFT, CHEST_RIGHT, SASH_LEFT, SASH_RIGHT, BACK_LEFT, BACK_RIGHT, BACK_SHIELD, BACK_TOOL
	}

	/** The kinds of carried thing, each with the homes it takes, first free first. */
	enum Kind {
		PISTOL(Home.CHEST_LEFT, Home.CHEST_RIGHT, Home.SASH_LEFT, Home.SASH_RIGHT),
		LONG(Home.BACK_RIGHT, Home.BACK_LEFT),
		SHIELD(Home.BACK_SHIELD),
		AXE(Home.BACK_TOOL, Home.HIP_LEFT, Home.HIP_RIGHT),
		TOOL(Home.BACK_TOOL),
		BLADE(Home.HIP_LEFT, Home.HIP_RIGHT, Home.BACK_LEFT, Home.BACK_RIGHT);

		final Home[] homes;

		Kind(Home... homes) {
			this.homes = homes;
		}
	}

	/** On a player: what each {@link Home} shows (empty where nothing), synced to everyone. Absent when nothing shows. */
	public static final AttachmentType<List<ItemStack>> SHOWN = AttachmentRegistry.create(JaymosPowers.id("body_weapons"),
		builder -> builder.syncWith(ItemStack.OPTIONAL_LIST_STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** On a player: true when they chose free placement (Weapon Master's). Saved, synced to everyone (it decides who draws). */
	public static final AttachmentType<Boolean> FREE = AttachmentRegistry.create(JaymosPowers.id("body_weapons_free"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));

	/** Client → server: switch between set places and free placement. */
	public record Toggle() implements CustomPacketPayload {
		public static final Type<Toggle> TYPE = new Type<>(JaymosPowers.id("body_weapons_toggle"));
		public static final StreamCodec<ByteBuf, Toggle> CODEC = StreamCodec.unit(new Toggle());

		@Override
		public Type<Toggle> type() {
			return TYPE;
		}
	}

	private BodyWeapons() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Toggle.TYPE, Toggle.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Toggle.TYPE, (payload, context) -> {
			ServerPlayer player = context.player();
			if (free(player)) {
				player.removeAttached(FREE);
			} else {
				player.setAttached(FREE, true);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(BodyWeapons::tick);
	}

	/** Whether {@code player} chose free placement (Weapon Master's); client and server. */
	public static boolean free(Player player) {
		return Boolean.TRUE.equals(player.getAttached(FREE));
	}

	/** What {@code player}'s homes show, one per {@link Home} (empty list when nothing shows); client and server. */
	public static List<ItemStack> shown(Player player) {
		List<ItemStack> shown = player.getAttached(SHOWN);
		return shown == null ? List.of() : shown;
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			List<ItemStack> homes = free(player) || player.isSpectator() ? null : homes(player);
			if (homes == null) {
				if (player.getAttached(SHOWN) != null) {
					player.removeAttached(SHOWN);
				}
			} else if (!ItemStack.listMatches(homes, shown(player))) {
				player.setAttached(SHOWN, Collections.unmodifiableList(homes));
			}
		}
	}

	/** Each home's item for {@code player} now, or null when none shows. */
	private static @Nullable List<ItemStack> homes(ServerPlayer player) {
		Inventory inventory = player.getInventory();
		int selected = inventory.getSelectedSlot();
		int cap = ToolBeltCompat.wears(player) ? Home.values().length : MAX_SHOWN;
		Home[] taken = new Home[Inventory.getSelectionSize()];
		boolean[] used = new boolean[Home.values().length];
		// (Vanilla+) the back slot's weapon or tool first: its kind's first home on the back (a pistol, its first home), the
		// hotbar's taking the rest round it; it isn't on the hotbar, so it never shows twice
		ItemStack back = com.jaymospowers.baubles.Baubles.back(player);
		Home backHome = backHome(kind(back));
		if (backHome != null) {
			used[backHome.ordinal()] = true;
		}
		for (int slot = 0; slot < taken.length; slot++) {
			Kind kind = kind(inventory.getItem(slot));
			if (kind == null) {
				continue;
			}
			for (Home home : kind.homes) {
				if (!used[home.ordinal()]) {
					used[home.ordinal()] = true;
					taken[slot] = home;
					break;
				}
			}
		}
		List<ItemStack> homes = new ArrayList<>(Collections.nCopies(Home.values().length, ItemStack.EMPTY));
		int shown = 0;
		if (backHome != null) {
			homes.set(backHome.ordinal(), back.copyWithCount(1));
			shown++;
		}
		for (int slot = 0; slot < taken.length && shown < cap; slot++) {
			if (taken[slot] != null && slot != selected) {
				homes.set(taken[slot].ordinal(), inventory.getItem(slot).copyWithCount(1));
				shown++;
			}
		}
		return shown == 0 ? null : homes;
	}

	/** The home the back slot's item of {@code kind} takes: its first on the back, else its first; null for none. */
	private static @Nullable Home backHome(@Nullable Kind kind) {
		if (kind == null) {
			return null;
		}
		for (Home home : kind.homes) {
			if (home.name().startsWith("BACK")) {
				return home;
			}
		}
		return kind.homes[0];
	}

	/** Whether {@code stack} is a weapon or tool the body carries (what the Baubles back slot takes). */
	public static boolean carriedOnBody(ItemStack stack) {
		return kind(stack) != null;
	}

	/** Which kind of carried thing {@code stack} is, or null if it isn't shown on the body. */
	static @Nullable Kind kind(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		if (stack.getItem() instanceof ShieldItem) {
			return Kind.SHIELD;
		}
		if (OldGuns.is(stack)) {
			return GunBlast.Gun.of(stack) == GunBlast.Gun.FLINTLOCK ? Kind.PISTOL : Kind.LONG;
		}
		if (stack.getItem() instanceof ProjectileWeaponItem || stack.getItem() instanceof TridentItem || stack.is(ItemTags.SPEARS)) {
			return Kind.LONG;
		}
		if (stack.is(ItemTags.AXES)) {
			return Kind.AXE;
		}
		if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)) {
			return Kind.TOOL;
		}
		if (stack.is(ItemTags.SWORDS) || stack.getItem() instanceof MaceItem || stack.has(DataComponents.WEAPON)) {
			return Kind.BLADE;
		}
		return null;
	}
}
