package com.jaymoskingdoms;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.jspecify.annotations.Nullable;

/**
 * Powers' fishing (Track D of its docs/Plan-fishing.md): "Talk about fishing" ({@link #ANSWER}) in MCA's menu, only on a
 * grown fisherman, opens Powers' fisherman screen ({@code com.jaymospowers.fishing.Fishermen.open}): selling fish by
 * their weight, the village's fishing contest, and what they say keeps the water. Powers pays for the fish through
 * {@link #pay}, in the coin of the place.
 */
public final class FisherTalk {
	public static final String ANSWER = "jaymoskingdoms_fishing";

	private static boolean looked;
	private static java.lang.invoke.@Nullable MethodHandle open;

	private FisherTalk() {
	}

	/** {@code e} is a grown fisherman. */
	public static boolean canAsk(@Nullable Entity e) {
		return e instanceof Villager v && !v.isBaby() && v.getVillagerData().profession().is(VillagerProfession.FISHERMAN);
	}

	/** Open Powers' fisherman screen for {@code player} with {@code villager}. False without Powers' fishing. */
	public static boolean talk(ServerPlayer player, Entity villager) {
		if (!looked) {
			looked = true;
			try {
				open = java.lang.invoke.MethodHandles.publicLookup().findStatic(Class.forName("com.jaymospowers.fishing.Fishermen"), "open",
					java.lang.invoke.MethodType.methodType(void.class, ServerPlayer.class, Entity.class));
			} catch (ReflectiveOperationException | LinkageError e) {
				open = null;
			}
		}
		if (open == null) {
			return false;
		}
		try {
			open.invoke(player, villager);
			return true;
		} catch (Throwable t) {
			return false;
		}
	}

	/**
	 * Called by Powers (through reflection): pay {@code player} {@code milli} thousandths of an emerald for fish sold at
	 * {@code pos}, in that place's coin, and say how much it came to.
	 */
	public static Component pay(ServerPlayer player, BlockPos pos, long milli) {
		Currency coin = Currencies.at(player.level(), pos);
		Currencies.Pay pay = Currencies.pay(coin, milli);
		Currencies.give(player, pay);
		return Currencies.amount(pay);
	}
}
