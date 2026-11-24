package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Andy (2026-11-23): "a low profile floating menu with all the possible controls when in context". Jaymo's Powers draws
 * the list by the crosshair ({@code client.hud.ContextHints}); these are Kingdoms' lines in it, worked out on the client
 * from what it knows: someone of the village to greet (your hand out, {@link Handshakes}), a ruler to bow to, or one you
 * know to talk to; and, sneaking up behind them empty handed, a pocket to pick ({@link Pickpocket}, once you've learned
 * how). Each line is {how ("tap" or "hold"), lang key, key bindings...}. Handed to Powers through Fabric Loader's object
 * share (Kingdoms doesn't depend on it); without Powers nothing is drawn.
 */
public final class ContextLines {
	private static final String SHARE = "jaymos:context_hints";

	private ContextLines() {
	}

	/** From the client's initializer. */
	@SuppressWarnings("unchecked")
	public static void register() {
		BiFunction<Player, Entity, List<String[]>> lines = ContextLines::lines;
		Object[] source = {JaymosKingdoms.id("context").toString(), lines};
		FabricLoader.getInstance().getObjectShare().whenAvailable(SHARE,
			(key, adder) -> ((java.util.function.Consumer<Object[]>) adder).accept(source));
	}

	static List<String[]> lines(Player player, Entity target) {
		List<String[]> list = new ArrayList<>();
		if (!(target instanceof LivingEntity person) || !person.isAlive() || person.isBaby() || !(person instanceof Villager || person instanceof Folk)) {
			return list;
		}
		if (player.isShiftKeyDown()) {
			if (Pickpocket.markable(person) && player.getMainHandItem().isEmpty() && player.distanceTo(person) <= Pickpocket.ATTACH_RANGE
				&& (person.isSleeping() || behind(player, person, Skills.behindCos(player, Pickpocket.BEHIND_COS)))
				&& TreeLocks.has(player, TreeLocks.Lock.PICKPOCKET)) {
				list.add(new String[] {"hold", "jaymoskingdoms.context.pickpocket", "key.sneak", "key.use"});
			}
			return list;
		}
		if (McaCompat.isMca(person) && !person.isSleeping() && !Downed.isDowned(person)) {
			boolean known = Introductions.knownHere(person);
			String action = Rulers.is(person) ? known ? "talk" : "bow" : known || Outlaws.is(person) ? "talk" : "greet";
			list.add(new String[] {"tap", "jaymoskingdoms.context." + action, "key.use"});
		}
		return list;
	}

	/** {@code player} stands behind {@code mark} (as {@link Pickpocket}'s own check, here for either side). */
	private static boolean behind(Player player, LivingEntity mark, double behindCos) {
		Vec3 facing = Vec3.directionFromRotation(0.0F, mark.yBodyRot);
		Vec3 to = player.position().subtract(mark.position()).multiply(1.0, 0.0, 1.0);
		return to.lengthSqr() < 1.0E-6 || facing.dot(to.normalize()) <= behindCos;
	}
}
