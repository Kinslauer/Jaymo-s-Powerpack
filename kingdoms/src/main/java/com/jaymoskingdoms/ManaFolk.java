package com.jaymoskingdoms;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * (0.7.234) The folk who work magic are attuned too. Andy: "court wizards and clerics should have to be attuned as well.
 * meaning other magic users can see their glow, and know their power level from the intensity of the glow. mentioning it if
 * asked." A court wizard, a village cleric, a hermit wizard, and every caster of the camps and the courts (shaman,
 * necromancer, blood witch, orc shaman) is attuned with a {@link Mana#POWER} of their own ({@link #power}), give or take one,
 * and glows at it for the attuned. Asked about their magic ({@link #ANSWER}, MCA's menu), they say how strong it is, and
 * to one attuned, how it measures against theirs.
 */
public final class ManaFolk {
	/** How often the folk near players are looked over (ticks), and how far from a player. */
	static final int EVERY = 100;
	static final double RANGE = 48.0;
	/** Their power: a hermit, a court wizard, a necromancer, a blood witch, a shaman, a cleric. */
	static final int HERMIT = 8, WIZARD = 6, NECROMANCER = 6, BLOOD_WITCH = 5, SHAMAN = 4, CLERIC = 3;
	/** The tiers of power they speak of (1-2, 3-4 ...), and how near another's power counts as the same. */
	static final int TIERS = 5;

	/** "Ask about their magic" in MCA's menu. */
	public static final String ANSWER = "jaymoskingdoms_magic";

	private ManaFolk() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ManaFolk::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = (ServerLevel)player.level();
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE),
				e -> e.isAlive() && !(e instanceof net.minecraft.world.entity.player.Player) && !Mana.attuned(e))) {
				attuneIfMagic(e);
			}
		}
	}

	/** How strong {@code e}'s magic is by what they are (0: they work none). */
	static int power(LivingEntity e) {
		if (Hermits.is(e)) {
			return HERMIT;
		}
		if (e instanceof Worker w && Sorcery.isCaster(w.role())) {
			return switch (w.role()) {
				case Sorcery.WIZARD -> WIZARD;
				case Sorcery.NECROMANCER -> NECROMANCER;
				case Sorcery.BLOOD_WITCH -> BLOOD_WITCH;
				default -> SHAMAN;
			};
		}
		if (CourtWizards.is(e)) {
			return WIZARD;
		}
		return e instanceof Villager v && Restoration.cleric(v) ? CLERIC : 0;
	}

	/** Attunes {@code e} if they work magic: their power, give or take one (their glow always sits full). */
	static void attuneIfMagic(LivingEntity e) {
		if (Mana.attuned(e) || e instanceof net.minecraft.world.entity.player.Player) {
			return;
		}
		int base = power(e);
		if (base <= 0) {
			return;
		}
		int power = Math.clamp(base + Math.floorMod(e.getUUID().hashCode(), 3) - 1, 1, Mana.MAX_POWER);
		e.setAttached(Mana.ATTUNED, true);
		e.setAttached(Mana.POWER, power);
	}

	/** Whether {@code villager} can be asked about their magic. */
	public static boolean canAsk(LivingEntity villager) {
		attuneIfMagic(villager);
		return Mana.attuned(villager);
	}

	/** {@code player} asked {@code villager} about their magic: how strong it is, and how it measures against the player's. */
	public static void answer(ServerPlayer player, LivingEntity villager, java.util.function.Consumer<Component> say) {
		int theirs = Mana.power(villager);
		say.accept(Component.translatable("message.jaymoskingdoms.magic.tier." + tier(theirs)));
		if (Mana.attuned(player)) {
			int yours = Mana.power(player);
			String how = Math.abs(yours - theirs) <= 1 ? "even" : yours > theirs ? "stronger" : "weaker";
			say.accept(Component.translatable("message.jaymoskingdoms.magic.compare." + how));
		} else if (ManaAlchemy.readsMagic(player)) {
			say.accept(Component.translatable("message.jaymoskingdoms.magic.corrupted"));
		} else {
			say.accept(Component.translatable("message.jaymoskingdoms.magic.blind"));
		}
	}

	/** The tier of {@code power} they speak of, 1-{@link #TIERS}. */
	static int tier(int power) {
		return Math.clamp((power + 1) / 2, 1, TIERS);
	}
}
