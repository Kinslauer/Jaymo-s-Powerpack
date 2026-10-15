package com.jaymospowers.costume;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
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
 * Kingdoms keeps the identity's own reputation and crimes, and works out who is under the mask. Which power was just
 * used is already tagged by {@code core.Oddities} ({@code jaymospowers_odd:power/<power>}).
 */
public final class SecretIdentity {
	/** The tags' prefixes. */
	public static final String MASKED = "jaymospowers_masked:", NAME = "jaymospowers_mask_name:";
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

	/** The name shown for a masked wearer of {@code d}: the costume's own, or a stranger's. */
	public static Component shownName(CostumeDesign d) {
		return d.name().isEmpty() ? Component.translatable("costume.jaymospowers.identity.unnamed") : Component.literal(d.name());
	}

	/** The name to show over {@code player}'s head, or null if their face shows (either side). */
	public static @Nullable Component maskedName(Player player) {
		CostumeDesign worn = Costume.worn(player);
		return worn != null && worn.mask().hidesFace ? shownName(worn) : null;
	}

	/** Brings {@code player}'s tags in line with what they wear. */
	public static void update(ServerPlayer player) {
		CostumeDesign worn = Costume.worn(player);
		Set<String> want = new HashSet<>();
		if (worn != null && worn.mask().hidesFace) {
			want.add(MASKED + key(worn));
			if (!worn.name().isEmpty()) {
				want.add(NAME + worn.name());
			}
		}
		for (String tag : new ArrayList<>(player.entityTags())) {
			if ((tag.startsWith(MASKED) || tag.startsWith(NAME)) && !want.contains(tag)) {
				player.removeTag(tag);
			}
		}
		for (String tag : want) {
			if (!player.entityTags().contains(tag)) {
				player.addTag(tag);
			}
		}
	}
}
