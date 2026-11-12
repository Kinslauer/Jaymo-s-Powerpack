package com.jaymoskingdoms;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "i broke a chest and the items just disappeared, they should either drop on the ground as a random roll, or
 * actual break items inside into components at a highly inefficient rate".
 *
 * <p>A loot chest or barrel run by Lootr keeps each player's share apart from the chest, and Lootr drops none of it when
 * the chest is broken: the breaker's share, and whatever anyone else left inside, just went. Now, when a player breaks
 * one, every share in it spills onto the floor as a random roll: each stack comes out with {@link #SURVIVES}, and a
 * stack that comes out keeps between {@link #KEPT_MIN} and all of its count. The rest is lost in the smash. A share
 * nobody had opened yet is the breaker's, rolled from the chest's loot table as if they had opened it, so forcing a
 * locked chest still gets you something, just less than picking the lock. Plain chests drop all they hold, as ever.
 *
 * <p>By reflection: Lootr is in the pack, but we don't compile against it (as {@code Lockpicking.Lootr}).
 */
public final class SpiltLoot {
	/** The chance each stack comes out of the broken chest at all. */
	static final float SURVIVES = 0.6F;
	/** The least share of a stack's count that comes out with it (the rest is lost). */
	static final float KEPT_MIN = 0.4F;

	private SpiltLoot() {
	}

	static void register() {
		if (!FabricLoader.getInstance().isModLoaded("lootr")) {
			return;
		}
		// AFTER: Lootr's own BEFORE has already let the break go through (a sneak-break), and the chest is gone
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer breaker && be != null && !breaker.isCreative()) {
				spill(server, breaker, pos, be);
			}
		});
	}

	/** Spills every share of the broken Lootr container {@code be} at {@code pos}, through the random roll. */
	private static void spill(ServerLevel level, ServerPlayer breaker, BlockPos pos, BlockEntity be) {
		Object instance = Lootr.instance(be);
		if (instance == null) {
			return;
		}
		List<Container> shares = new ArrayList<>();
		for (UUID opener : Lootr.openers(instance)) {
			if (!opener.equals(breaker.getUUID())) {
				Container share = Lootr.share(instance, opener);
				if (share != null) {
					shares.add(share);
				}
			}
		}
		// the breaker's own share, rolled now if they never opened it
		Container own = Lootr.share(instance, breaker);
		if (own != null) {
			shares.add(own);
		}
		RandomSource random = level.getRandom();
		for (Container share : shares) {
			for (int slot = 0; slot < share.getContainerSize(); slot++) {
				ItemStack stack = share.removeItemNoUpdate(slot);
				if (!stack.isEmpty() && random.nextFloat() < SURVIVES) {
					int kept = Mth.clamp(Mth.ceil(stack.getCount() * Mth.nextFloat(random, KEPT_MIN, 1.0F)), 1, stack.getCount());
					Block.popResource(level, pos, stack.copyWithCount(kept));
				}
			}
			share.setChanged();
		}
	}

	/** Lootr's API, by reflection. Every call gives up quietly (nothing spilt) if Lootr changes under us. */
	private static final class Lootr {
		private static boolean looked;
		private static @Nullable Class<?> lootrBlockEntity;
		private static @Nullable Method data, inventory, openers, stored;

		private static boolean find() {
			if (!looked) {
				looked = true;
				try {
					Class<?> api = Class.forName("noobanidus.mods.lootr.common.api.LootrAPI");
					Class<?> container = Class.forName("noobanidus.mods.lootr.common.api.data.ILootrContainerInstance");
					Class<?> store = Class.forName("noobanidus.mods.lootr.common.api.data.ILootrInventoryStore");
					lootrBlockEntity = Class.forName("noobanidus.mods.lootr.common.api.data.blockentity.ILootrBlockEntity");
					data = api.getMethod("getData", container);
					inventory = api.getMethod("getInventory", container, ServerPlayer.class);
					openers = Class.forName("noobanidus.mods.lootr.common.api.interfaces.IHasOpeners").getMethod("getActualOpeners");
					stored = store.getMethod("getInventory", UUID.class);
				} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Spilt loot: Lootr's API isn't where it was, so broken Lootr chests won't spill", e);
					lootrBlockEntity = null;
				}
			}
			return lootrBlockEntity != null;
		}

		/** {@code be} when it's one of Lootr's own chests, barrels or the like, else null (a plain chest drops all it holds). */
		static @Nullable Object instance(BlockEntity be) {
			return find() && lootrBlockEntity.isInstance(be) ? be : null;
		}

		/** Who has opened it (each has a share in it). */
		@SuppressWarnings("unchecked")
		static Set<UUID> openers(Object instance) {
			try {
				Object store = data.invoke(null, instance);
				return store == null ? Set.of() : Set.copyOf((Set<UUID>)openers.invoke(store));
			} catch (ReflectiveOperationException | RuntimeException e) {
				return Set.of();
			}
		}

		/** The share {@code opener} left in it, or null. */
		static @Nullable Container share(Object instance, UUID opener) {
			try {
				Object store = data.invoke(null, instance);
				return store == null ? null : stored.invoke(store, opener) instanceof Container share ? share : null;
			} catch (ReflectiveOperationException | RuntimeException e) {
				return null;
			}
		}

		/** {@code player}'s share, filled from the loot table if they never opened it. */
		static @Nullable Container share(Object instance, Player player) {
			try {
				return inventory.invoke(null, instance, player) instanceof Container share ? share : null;
			} catch (ReflectiveOperationException | RuntimeException e) {
				return null;
			}
		}
	}
}
