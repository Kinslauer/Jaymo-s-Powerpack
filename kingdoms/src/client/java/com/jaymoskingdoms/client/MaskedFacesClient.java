package com.jaymoskingdoms.client;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.MaskedFaces;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * Who is masked, as the server last told us ({@link MaskedFaces}), and the face they show where a player's own face would
 * be: the tab list ({@code MaskedTabFaceMixin}) and the social interactions screen ({@code MaskedSocialEntryMixin}).
 * The face is a dark cloth mask with eye slits ({@code tools/gen_masked_face.py}).
 */
public final class MaskedFacesClient {
	static final PlayerSkin SKIN = PlayerSkin.insecure(new ClientAsset.ResourceTexture(JaymosKingdoms.id("entity/identity/masked_face")), null, null,
		PlayerModelType.WIDE);
	private static volatile Set<UUID> masked = Set.of();

	private MaskedFacesClient() {
	}

	static void register() {
		ClientPlayNetworking.registerGlobalReceiver(MaskedFaces.Masked.TYPE, (payload, context) -> masked = Set.copyOf(payload.ids()));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> masked = Set.of());
	}

	/** The player with {@code id} is behind a full mask now. */
	public static boolean masked(UUID id) {
		return masked.contains(id);
	}

	/** The skin to draw {@code id}'s face from: the mask's while they're masked, else {@code own}. */
	public static PlayerSkin face(UUID id, PlayerSkin own) {
		return masked(id) ? SKIN : own;
	}
}
