package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Ship;
import com.jaymoskingdoms.sailing.Ships;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * Titles earned by deeds (the JustLeveling Fork's titles, Apache 2.0: the idea only, our own code), worn under your name through
 * {@link TitleChoice} beside a pillar's, a job's and an aim's. Each deed is counted ({@link #COUNTS}) and gives its title at its
 * count: <b>Wolfbane</b> (wild wolves slain), <b>the Ferryman</b> (legs of a voyage at a ship's helm with someone else aboard) and
 * <b>Breaker of Keeps</b> (a realm's ruler slain: {@link KingdomEvents}). A villager who sees you wear one sometimes greets you by it.
 */
public final class DeedTitles {
	/** The deeds. {@code count}: how many make the title. */
	public enum Deed {
		WOLFBANE(25), FERRYMAN(20), KEEPS(1);

		public final int count;

		Deed(int count) {
			this.count = count;
		}

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	/** The id prefix of a deed title in {@link TitleChoice}. */
	public static final String PREFIX = "deed:";
	/** How often (ticks) the helm is looked at and a worn title is spoken to; the chance a villager in earshot greets you by it. */
	static final int HELM_EVERY = 200, GREET_EVERY = 600;
	static final float GREET_CHANCE = 0.35F;
	/** How far a villager hears you (blocks). */
	static final double EARSHOT = 8.0;
	static final int GREET_LINES = 4;
	static final int COLOUR = 0xD8C070;

	/** Each deed's count so far. Saved, kept through death. */
	static final AttachmentType<Map<String, Integer>> COUNTS = AttachmentRegistry.create(JaymosKingdoms.id("deed_counts"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());

	private DeedTitles() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((dead, source) -> {
			if (dead instanceof Wolf wolf && !wolf.isTame() && source.getEntity() instanceof ServerPlayer p) {
				add(p, Deed.WOLFBANE, 1);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(DeedTitles::tick);
	}

	/** {@code player} did {@code deed} {@code n} more times. */
	static void add(ServerPlayer player, Deed deed, int n) {
		Map<String, Integer> map = new HashMap<>(player.getAttachedOrElse(COUNTS, Map.of()));
		int before = map.getOrDefault(deed.key(), 0);
		map.put(deed.key(), before + n);
		player.setAttached(COUNTS, Map.copyOf(map));
		if (before < deed.count && before + n >= deed.count) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.title.earned", title(deed)).withStyle(ChatFormatting.GOLD));
		}
	}

	static boolean has(Player player, Deed deed) {
		return player.getAttachedOrElse(COUNTS, Map.<String, Integer>of()).getOrDefault(deed.key(), 0) >= deed.count;
	}

	static Component title(Deed deed) {
		return Component.translatable("title.jaymoskingdoms.deed." + deed.key());
	}

	/** The deed titles {@code player} has earned, for {@link TitleChoice#earned}. */
	static List<TitleChoice.Entry> earned(Player player) {
		List<TitleChoice.Entry> out = new ArrayList<>();
		for (Deed d : Deed.values()) {
			if (has(player, d)) {
				out.add(new TitleChoice.Entry(PREFIX + d.key(), title(d), COLOUR));
			}
		}
		return out;
	}

	/** The title of the deed id {@code id} ({@link #PREFIX} and a key), or null. */
	static @Nullable Component shown(String id) {
		for (Deed d : Deed.values()) {
			if (id.equals(PREFIX + d.key())) {
				return title(d);
			}
		}
		return null;
	}

	private static void tick(MinecraftServer server) {
		int t = server.getTickCount();
		boolean helm = t % HELM_EVERY == 0, greet = t % GREET_EVERY == 0;
		if (!helm && !greet) {
			return;
		}
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator()) {
				continue;
			}
			if (helm) {
				ferry(p);
			}
			if (greet) {
				greeted(p);
			}
		}
	}

	/** A leg of the voyage: at a ship's helm with someone else standing on her deck. */
	private static void ferry(ServerPlayer p) {
		Ship ship = Ships.aboard(p);
		if (ship == null || ship.pilot() != p) {
			return;
		}
		boolean passenger = !p.level().getEntitiesOfClass(LivingEntity.class, ship.getBoundingBox().inflate(1.0),
			e -> e != p && e.isAlive() && (e instanceof Player || e instanceof Villager) && ship.aboard(ship.toLocal(e.position()), 0.5)).isEmpty();
		if (passenger) {
			add(p, Deed.FERRYMAN, 1);
		}
	}

	/** A villager near hails you by the title you wear. */
	private static void greeted(ServerPlayer p) {
		String worn = TitleChoice.worn(p);
		Component title = worn.startsWith(PREFIX) ? shown(worn) : null;
		if (title == null || p.getRandom().nextFloat() >= GREET_CHANCE || !(p.level() instanceof ServerLevel level)) {
			return;
		}
		List<Villager> near = level.getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(EARSHOT), v -> v.isAlive() && !v.isSleeping() && v.getTarget() == null);
		if (!near.isEmpty()) {
			Villager who = near.get(p.getRandom().nextInt(near.size()));
			Speech.tell(level, who, p, Component.translatable("talk.jaymoskingdoms.title.greet." + p.getRandom().nextInt(GREET_LINES), title), Speech.WHITE);
		}
	}
}
