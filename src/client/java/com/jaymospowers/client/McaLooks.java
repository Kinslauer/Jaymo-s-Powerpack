package com.jaymospowers.client;

import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.resources.Identifier;

/**
 * (0.21.13) Where a look laid over a player's skin goes when MCA draws the player. Andy (2026-11-05): "is anything else
 * drawn on the player model instead of where it needs to be?" With MCA, a player is drawn as MCA's villager body (its
 * own head size, its own face and eyes, posed from the copy of the body its skin layer takes). A look drawn on the
 * player renderer's own model is posed a second time on its own and comes apart from that body as it moves, and eyes
 * found on the player's Mojang skin sit where that skin's eyes are, not on MCA's face. Kingdoms does the same
 * ({@code VampireLooks.skinBody/glowBody/faceEyes}). MCA's classes are touched only in {@link McaSkin}.
 */
public final class McaLooks {
	private static final boolean MCA = FabricLoader.getInstance().isModLoaded("mca");

	private McaLooks() {
	}

	/** The body to lay skin-deep looks on: MCA's skin body in MCA's pose when MCA draws this player, else {@code parent}. */
	public static PlayerModel skinBody(PlayerModel parent) {
		PlayerModel mca = MCA ? McaSkin.marks(parent) : null;
		return mca != null ? mca : parent;
	}

	/** The body to lay glowing looks on: MCA's, just over its face, when MCA draws this player, else {@code parent}. */
	public static PlayerModel glowBody(PlayerModel parent) {
		PlayerModel mca = MCA ? McaSkin.glow(parent) : null;
		return mca != null ? mca : parent;
	}

	/** Glowing eyes: on a player MCA draws, MCA's own eye pixels lit {@code color}; else {@code fromSkin}. */
	public static Identifier faceEyes(AvatarRenderState state, PlayerModel parent, int color, Supplier<Identifier> fromSkin) {
		if (MCA && McaSkin.draws(parent)) {
			Identifier eyes = McaSkin.eyes(state, color | 0xFF000000);
			if (eyes != null) {
				return eyes;
			}
		}
		return fromSkin.get();
	}
}
