package com.jaymospowers.jewellery;

import com.jaymospowers.baubles.Baubles;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/** (0.19.87) The four kinds of jewellery: their bauble slot, frame and piece ids, ingots and bonus factor. */
public enum JewelKind {
	RING("ring", Baubles.Kind.RING, "ring_frame", "gem_ring", 1, 1.0F),
	AMULET("amulet", Baubles.Kind.AMULET, "amulet_frame", "gem_amulet", 2, 1.5F),
	BRACELET("bracelet", Baubles.Kind.WRIST, "bracelet_frame", "gem_bracelet", 2, 1.0F),
	CIRCLET("circlet", Baubles.Kind.HEAD, "circlet_frame", "gem_circlet", 3, 1.25F);

	public final String id;
	public final Baubles.Kind bauble;
	public final String frameId, pieceId;
	public final int ingots;
	public final float bonus;

	JewelKind(String id, Baubles.Kind bauble, String frameId, String pieceId, int ingots, float bonus) {
		this.id = id;
		this.bauble = bauble;
		this.frameId = frameId;
		this.pieceId = pieceId;
		this.ingots = ingots;
		this.bonus = bonus;
	}

	/** The kind a frame item is for, or null. */
	public static @Nullable JewelKind byFrame(Item item) {
		for (JewelKind kind : values()) {
			if (Jewellery.FRAMES.get(kind) == item) {
				return kind;
			}
		}
		return null;
	}

	/** The kind a finished piece is, or null. */
	public static @Nullable JewelKind byPiece(Item item) {
		for (JewelKind kind : values()) {
			if (Jewellery.PIECES.get(kind) == item) {
				return kind;
			}
		}
		return null;
	}
}
