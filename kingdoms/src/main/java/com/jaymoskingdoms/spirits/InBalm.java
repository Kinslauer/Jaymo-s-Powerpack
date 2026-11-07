package com.jaymoskingdoms.spirits;

import com.jaymoskingdoms.InBalming;
import com.jaymoskingdoms.Mana;
import net.minecraft.server.level.ServerPlayer;

/**
 * (0.7.433) Every spirit's In-balming ({@link InBalming}): a downed orc or goblin in reach bound to the keeper, each spirit
 * its own way. Nothing to in-balm, nothing spent.
 */
final class InBalm implements Move {
	private final int kind;

	InBalm(int kind) {
		this.kind = kind;
	}

	@Override
	public String id() {
		return "inbalm_" + Spirits.of(kind).id();
	}

	@Override
	public float cost() {
		return InBalming.COST;
	}

	@Override
	public int cooldown() {
		return InBalming.COOLDOWN;
	}

	@Override
	public int level() {
		return InBalming.LEVEL;
	}

	@Override
	public int pose() {
		return Poses.HOLD;
	}

	@Override
	public int intent() {
		return Mana.WILL;
	}

	@Override
	public boolean cast(ServerPlayer player, SpiritState state, int charge) {
		return InBalming.cast(player, kind);
	}
}
