package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * (0.7.650) Andy (2026-11-07): "tideborn will see fishing as an attack, and doing in their havens is seen as a crime."
 * The Tideborn are of the sea's blood, and a fish dragged out on a hook is one of theirs. When a fish takes a player's
 * hook (Powers' real fishing, through its {@code KingdomsCompat}), any Tideborn who can see the angler within
 * {@link #SEE_RANGE} take it as if they'd been struck: the nearest squares up and those round them join in
 * ({@link Retaliation#struck}, an assault where a realm's law holds). On a Tideborn haven's land it is poaching besides,
 * a medium crime ({@link Theft.Crime#POACHING}): seen, it costs reputation there, sets the guards on the angler and puts
 * a price on their head. A haven's own folk may fish their waters.
 */
public final class TideFishing {
	/** Tideborn this close who can see the angler take the fishing as an attack. */
	static final double SEE_RANGE = 24.0;
	/** Poaching is charged at most this often per angler (ticks), however many bites. */
	static final long POACHING_EVERY = 20L * 30;
	/** Lines a Tideborn may cry on seeing it. */
	static final int LINES = 3;
	static final double SPEECH_RANGE = 16.0;

	/** Angler → when they were last charged with poaching. */
	private static final Map<UUID, Long> CHARGED = new HashMap<>();

	private TideFishing() {
	}

	/** (From Powers' {@code KingdomsCompat}) a fish took {@code player}'s hook at {@code hook}. */
	public static void fished(ServerPlayer player, BlockPos hook) {
		if (!(player.level() instanceof ServerLevel level) || player.isCreative() || player.isSpectator()) {
			return;
		}
		List<LivingEntity> tideborn = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SEE_RANGE),
			e -> !(e instanceof Player) && e.isAlive() && !e.isSleeping() && VillagerHeritage.of(e) == Heritage.Kind.TIDEBORN
				&& e.hasLineOfSight(player));
		if (!tideborn.isEmpty()) {
			tideborn.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
			LivingEntity nearest = tideborn.getFirst();
			Speech.say(level, nearest, Component.translatable("message.jaymoskingdoms.tidefishing.cry." + level.getRandom().nextInt(LINES)),
				Speech.RED, SPEECH_RANGE);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.tidefishing.attack").withStyle(ChatFormatting.RED));
			Retaliation.struck(player, level, nearest);
		}
		Faction haven = Havens.at(level, hook);
		if (haven == null || !Heritage.Kind.TIDEBORN.key().equals(Havens.of(level.getServer(), haven)) || Theft.exempt(player, haven)) {
			return;
		}
		long now = level.getGameTime();
		Long last = CHARGED.get(player.getUUID());
		if (last != null && now - last < POACHING_EVERY) {
			return;
		}
		CHARGED.put(player.getUUID(), now);
		Theft.commit(player, level, haven, Theft.Crime.POACHING, hook, null, false);
	}
}
