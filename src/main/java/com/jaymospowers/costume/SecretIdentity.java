package com.jaymospowers.costume;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.11.70) Costumes, stage 2: the secret identity. While a player's costume hides their face ({@link Costume#masked}),
 * other players see the costume's name over their head in place of theirs (client: {@code CostumeClient}), and the
 * player carries two entity tags for Jaymo's Kingdoms, which reads them by name and never loads these classes:
 * <ul>
 * <li>{@link #MASKED}{@code <key>}: masked, as the identity {@link #key} (the same costume always gives the same key);</li>
 * <li>{@link #NAME}{@code <name>}: the costume's name, when it has one.</li>
 * </ul>
 * Kingdoms keeps the identity's own reputation and crimes, and works out who is under the mask.
 * <p>
 * (0.11.75) While masked the costume's name stands in for the player's display name everywhere (chat, the tab list,
 * death messages, the name tag: {@code mixin/CostumeNameMixin}, {@code CostumeTabListMixin}). Which power was just
 * used is already tagged by {@code core.Oddities} ({@code jaymospowers_odd:power/<power>}).
 * <p>
 * (0.14.3) A hood ({@link CostumeDesign.Mask#isHood}: the hood, the peaked hood) that is up leaves the face showing, so no
 * identity is hidden, but the player carries a third tag, {@link #HOODED}{@code <key>} (the same {@link #key} the
 * masked tag would have), for Kingdoms' stealth to read; it is removed when the hood comes off or a face-hiding piece
 * replaces it. The client shows a hooded player's name tag only within {@code CostumeClient#HOOD_NAME_RANGE}.
 * <p>
 * (0.12.3) With the costume's mask taken off ({@link Costume#UNMASKED}) the face shows: no identity, the real name.
 */
public final class SecretIdentity {
	/** The tags' prefixes. */
	public static final String MASKED = "jaymospowers_masked:", NAME = "jaymospowers_mask_name:";
	/** (0.14.3) The tag of a player with a hood up (the face showing). */
	public static final String HOODED = "jaymospowers_hooded:";
	/** How often every player's tags are brought up to date (ticks); putting a costume on or off does it at once too. */
	static final int EVERY = 10;

	private SecretIdentity() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(SecretIdentity::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			update(player);
		}
	}

	/**
	 * The identity a design stands for: a stable hash of its name (case aside) and its whole look. Sew the same costume
	 * again and it's the same identity; rename it or change its look and it's someone new.
	 */
	public static String key(CostumeDesign d) {
		String canon = d.name().toLowerCase(Locale.ROOT) + "|" + d.cut().getSerializedName() + "|" + d.primary().getName() + "|"
			+ d.secondary().getName() + "|" + d.mask().getSerializedName() + "|" + d.cape().getSerializedName() + "|" + d.emblem();
		// (FNV-1a, 64 bits: the same on every machine and every run)
		long h = 0xcbf29ce484222325L;
		for (int i = 0; i < canon.length(); i++) {
			h ^= canon.charAt(i);
			h *= 0x100000001b3L;
		}
		return String.format(Locale.ROOT, "%016x", h);
	}

	/** (0.11.82) The identity behind a Rig Helm: a stable hash of "rig|" and the Helm's name (case aside). */
	public static String rigKey(String helmName) {
		String canon = "rig|" + helmName.toLowerCase(Locale.ROOT);
		long h = 0xcbf29ce484222325L;
		for (int i = 0; i < canon.length(); i++) {
			h ^= canon.charAt(i);
			h *= 0x100000001b3L;
		}
		return String.format(Locale.ROOT, "%016x", h);
	}

	/** The name shown for a masked wearer of {@code d}: the costume's own, or a stranger's. */
	public static Component shownName(CostumeDesign d) {
		return d.name().isEmpty() ? Component.translatable("costume.jaymospowers.identity.unnamed") : Component.literal(d.name());
	}

	/** The name to show over {@code player}'s head, or null if their face shows (either side). */
	public static @Nullable Component maskedName(Player player) {
		CostumeDesign worn = Costume.worn(player);
		// (0.11.82) a Rig Helm hides the face when the costume doesn't: its name (or "a Rig pilot") is shown
		// (0.12.3) a mask taken off hides nobody (Costume#faceHidden)
		return worn != null && Costume.faceHidden(player) ? shownName(worn) : com.jaymospowers.rig.Rig.helmIdentity(player);
	}

	/** Brings {@code player}'s tags in line with what they wear. */
	public static void update(ServerPlayer player) {
		CostumeDesign worn = Costume.worn(player);
		Set<String> want = new HashSet<>();
		if (worn != null && Costume.faceHidden(player)) {
			want.add(MASKED + key(worn));
			if (!worn.name().isEmpty()) {
				want.add(NAME + worn.name());
			}
		} else {
			Component helm = com.jaymospowers.rig.Rig.helmIdentity(player);
			if (helm != null) {
				// (0.11.82) behind a Rig Helm: an identity of its own, keyed by the Helm's name
				String name = helm.getString();
				want.add(MASKED + rigKey(name));
				if (com.jaymospowers.rig.Rig.wears(player, com.jaymospowers.rig.Rig.Piece.HELM)
					&& player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).getCustomName() != null) {
					want.add(NAME + name);
				}
			}
		}
		if (worn != null && Costume.hooded(player)) {
			want.add(HOODED + key(worn));
			// (the hood's name, for Kingdoms' "hooded figure", unless a Rig Helm's identity already gives one)
			if (!worn.name().isEmpty() && want.stream().noneMatch(t -> t.startsWith(NAME))) {
				want.add(NAME + worn.name());
			}
		}
		boolean changed = false;
		for (String tag : new ArrayList<>(player.entityTags())) {
			if ((tag.startsWith(MASKED) || tag.startsWith(NAME) || tag.startsWith(HOODED)) && !want.contains(tag)) {
				player.removeTag(tag);
				changed = true;
			}
		}
		for (String tag : want) {
			if (!player.entityTags().contains(tag)) {
				player.addTag(tag);
				changed = true;
			}
		}
		if (changed) {
			// (0.11.75) everyone's tab list shows the costume's name now (or the real one again)
			player.level().getServer().getPlayerList().broadcastAll(
				new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, player));
		}
	}
}
