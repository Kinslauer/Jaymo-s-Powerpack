package com.jaymospowers.power.arsenal;

import com.jaymospowers.power.chrono.future.FutureGuns;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * (0.19.26) Andy: "give arsenal a small chance to pull handguns from the future with only one magazine."
 * <p>
 * Now and then ({@link #CHANCE}) a Reach (not Rummage) reaches too far and comes back with one of TaCZ's modern
 * handguns from the future (a Glock, an M1911, a Desert Eagle...), loaded with one full magazine and nothing else: no
 * spare rounds, so once it's empty it's done. It's a summon like any other pull and dissolves the same way. Never
 * instead of your pinned weapon or a stash relic, and nothing without TaCZ.
 */
final class ArsenalFutureGun {
	/** The chance a Reach brings a handgun from the future instead of its weapon (1 pull in 600). */
	static final float CHANCE = 1.0F / 600.0F;

	private ArsenalFutureGun() {
	}

	/** Rolled when a Reach opens: the loaded handgun it brings, or null (nearly always). */
	static @Nullable List<ItemStack> roll(ServerPlayer player, RandomSource random) {
		if (ArsenalArmory.pinned(player) != null || random.nextFloat() >= CHANCE) {
			return null;
		}
		ItemStack gun = FutureGuns.handgun(random);
		return gun.isEmpty() ? null : new ArrayList<>(List.of(gun));
	}
}
