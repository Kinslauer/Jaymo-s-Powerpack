package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;

/**
 * The Ranger: the old Archer's and Survivalist's perks together ({@link ClassPerks}: bows, Headshot with its pins and pincushion, the
 * drawn jump, a lean stomach, the soft landing, Second Wind) and, besides:
 *
 * <ul>
 * <li><b>Favoured foe:</b> picked with the class (the dead, raiders, beasts, spiders, greenskins or monsters): {@link #FOE_DAMAGE}
 * more harm to them, from bow or blade ({@link #dealt}), and {@link #FOE_LAND_DAMAGE} more again on favoured land.</li>
 * <li><b>Favoured land:</b> a kind of country (forest, taiga, jungle, mountain, desert, swamp, plains, snow, underground, water).
 * A Ranger begins with the land they start in; with sneak held, the class key claims the land they stand in instead, once a day.
 * There they move quietly ({@link #quiet}).</li>
 * <li><b>Tracking:</b> the class key reads the ground ({@link TrailReading}) at once, farther on favoured land; the favoured
 * foe's prints are read at the farthest and oldest and stand out larger.</li>
 * <li><b>Quiet:</b> a Ranger's noise carries less ({@link Awareness#noise}) and mobs notice them from less far ({@link Senses#factor}).</li>
 * </ul>
 */
public final class Rangers {
	/** The extra harm to a favoured foe, and again on favoured land (shares). */
	static final double FOE_DAMAGE = 0.25, FOE_LAND_DAMAGE = 0.15;
	/** How much of a Ranger's noise and sight-range is left, anywhere and on favoured land (shares). */
	static final double QUIET = 0.8, QUIET_LAND = 0.6;
	/** The wait between trackings and between claims of land (ticks). */
	static final int TRACK_COOLDOWN = 100, CLAIM_COOLDOWN = 24000;
	static final String[] LANDS = {"underground", "water", "snow", "desert", "swamp", "jungle", "taiga", "mountain", "forest", "plains"};

	/** The favoured foe and favoured land. Saved, kept through death. */
	record Favour(String foe, String land) {
		static final Codec<Favour> CODEC = com.mojang.serialization.codecs.RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.optionalFieldOf("foe", "monsters").forGetter(Favour::foe),
			Codec.STRING.optionalFieldOf("land", "forest").forGetter(Favour::land)
		).apply(i, Favour::new));
	}

	static final AttachmentType<Favour> FAVOUR = AttachmentRegistry.create(JaymosKingdoms.id("ranger_favour"), b -> b.persistent(Favour.CODEC).copyOnDeath());
	/** When the land was last claimed (game time). */
	static final AttachmentType<Long> CLAIMED = AttachmentRegistry.create(JaymosKingdoms.id("ranger_claimed"), b -> b.persistent(Codec.LONG).copyOnDeath());

	private static final Map<UUID, Long> TRACKED = new HashMap<>();

	private Rangers() {
	}

	/** On favoured land the favoured foe's trail shows by itself this often (ticks): the trail is never lost there. */
	static final int TRAIL_EVERY = 60;
	/** A menu shows at most this many lands at once. */
	private static final int LANDS_A_PAGE = 5;
	/** Those still to be asked what they hunt and where, and how long after joining they are (ticks). */
	private static final java.util.Set<UUID> TO_ASK = new java.util.HashSet<>();
	private static final int ASK_AFTER = 20 * 10;

	static void register() {
		// an old Archer or Survivalist, a Ranger now, hasn't said what they hunt or where they are at home: they are asked
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer player = handler.player;
			if (Classes.is(player, PlayerClass.RANGER) && player.getAttached(FAVOUR) == null) {
				String picked = Classes.optionOf(player, PlayerClass.RANGER);
				player.setAttached(FAVOUR, new Favour(picked.isEmpty() ? "monsters" : picked, land(player.level(), player.blockPosition())));
				// (asked once they are in and looking about, not into the loading screen)
				TO_ASK.add(player.getUUID());
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.tickCount > ASK_AFTER && player.isAlive() && TO_ASK.remove(player.getUUID())) {
					if (Classes.optionOf(player, PlayerClass.RANGER).isEmpty()) {
						askFoe(player);
					} else {
						askLand(player, 0);
					}
				}
				if ((player.tickCount + player.getId()) % TRAIL_EVERY != 0 || !player.isAlive() || player.isSpectator()
					|| !Classes.is(player, PlayerClass.RANGER) || !(player.level() instanceof ServerLevel level)
					|| !land(level, player.blockPosition()).equals(land(player))) {
					continue;
				}
				String kind = foe(player);
				TrailReading.read(level, player, 1, e -> e instanceof LivingEntity living && isFoe(kind, living), true);
			}
		});
	}

	/** Asks {@code player} what they hunt (an old Archer or Survivalist who never said), then where they are at home. */
	private static void askFoe(ServerPlayer player) {
		java.util.List<Menus.Choice> choices = new java.util.ArrayList<>();
		for (String foe : PlayerClass.RANGER.options) {
			choices.add(Menus.Choice.of(Component.translatable("class.jaymoskingdoms.option." + foe), p -> {
				p.setAttached(FAVOUR, new Favour(foe, land(p)));
				askLand(p, 0);
			}));
		}
		Menus.ask(player, player, Component.translatable("message.jaymoskingdoms.ranger.ask_foe"), choices);
	}

	/** Asks {@code player} which country they know: a page of lands, and a way to the rest. Left unanswered, it is where they stand. */
	private static void askLand(ServerPlayer player, int page) {
		java.util.List<Menus.Choice> choices = new java.util.ArrayList<>();
		int from = page * LANDS_A_PAGE;
		for (int i = from; i < Math.min(LANDS.length, from + LANDS_A_PAGE); i++) {
			String land = LANDS[i];
			choices.add(Menus.Choice.of(Component.translatable("option.jaymoskingdoms.land." + land), p -> {
				p.setAttached(FAVOUR, new Favour(foe(p), land));
				p.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.ranger.land", Component.translatable("option.jaymoskingdoms.land." + land)));
			}));
		}
		int next = from + LANDS_A_PAGE < LANDS.length ? page + 1 : 0;
		choices.add(Menus.Choice.of(Component.translatable("message.jaymoskingdoms.ranger.other_lands"), p -> askLand(p, next)));
		Menus.ask(player, player, Component.translatable("message.jaymoskingdoms.ranger.ask_land"), choices);
	}

	static void clear() {
		TO_ASK.clear();
		TRACKED.clear();
	}

	/** A new Ranger's favoured foe is the one they picked; their favoured land they are asked for. */
	static void begin(ServerPlayer player) {
		String foe = Classes.optionOf(player, PlayerClass.RANGER);
		String land = land(player.level(), player.blockPosition());
		player.setAttached(FAVOUR, new Favour(foe.isEmpty() ? "monsters" : foe, land));
		// the land is theirs to name (unanswered, it is where they stand)
		if (foe.isEmpty()) {
			askFoe(player);
		} else {
			askLand(player, 0);
		}
	}

	/** The Ranger's favoured foe ("monsters" if unsaid). */
	public static String foe(Player player) {
		Favour f = player.getAttached(FAVOUR);
		return f == null ? "monsters" : f.foe();
	}

	/** The Ranger's favoured land. */
	public static String land(Player player) {
		Favour f = player.getAttached(FAVOUR);
		return f == null ? "forest" : f.land();
	}

	/** What kind of country {@code pos} is. */
	static String land(ServerLevel level, BlockPos pos) {
		if (!level.canSeeSky(pos) && pos.getY() < level.getSeaLevel() - 4) {
			return "underground";
		}
		Holder<Biome> b = level.getBiome(pos);
		if (b.is(BiomeTags.IS_OCEAN) || b.is(BiomeTags.IS_RIVER) || b.is(BiomeTags.IS_BEACH)) {
			return "water";
		}
		if (b.getRegisteredName().contains("snow") || b.getRegisteredName().contains("frozen") || b.getRegisteredName().contains("ice") || b.getRegisteredName().contains("grove")) {
			return "snow";
		}
		if (b.getRegisteredName().contains("desert") || b.is(BiomeTags.IS_BADLANDS)) {
			return "desert";
		}
		if (b.getRegisteredName().contains("swamp") || b.getRegisteredName().contains("mangrove")) {
			return "swamp";
		}
		if (b.is(BiomeTags.IS_JUNGLE)) {
			return "jungle";
		}
		if (b.is(BiomeTags.IS_TAIGA)) {
			return "taiga";
		}
		if (b.is(BiomeTags.IS_MOUNTAIN) || b.is(BiomeTags.IS_HILL)) {
			return "mountain";
		}
		if (b.is(BiomeTags.IS_FOREST)) {
			return "forest";
		}
		return "plains";
	}

	/** Whether {@code foe} is of the kind {@code kind}. */
	static boolean isFoe(String kind, LivingEntity foe) {
		String id = FoeStudy.kind(foe);
		return switch (kind) {
			case "undead" -> Restoration.undead(foe);
			case "raiders" -> foe instanceof AbstractIllager || id.equals("ravager") || id.equals("witch") || id.equals("vex") || id.equals("evoker");
			case "beasts" -> foe instanceof Animal && (foe instanceof NeutralMob || id.equals("hoglin") || id.equals("polar_bear") || id.contains("bear")) || id.contains("wolf");
			case "spiders" -> foe instanceof Spider || id.contains("spider") || id.equals("silverfish") || id.equals("endermite");
			case "greenskins" -> id.contains("goblin") || id.contains("orc") || id.contains("ogre") || id.contains("troll") || id.contains("piglin_brute") || id.equals("zombified_piglin");
			default -> foe instanceof Enemy;
		};
	}

	/** A Ranger's blow on {@code target}: the favoured foe takes more. Called from the class perks. */
	static float dealt(ServerPlayer player, LivingEntity target, float amount) {
		if (!isFoe(foe(player), target)) {
			return amount;
		}
		double more = FOE_DAMAGE + (land(player.level(), player.blockPosition()).equals(land(player)) ? FOE_LAND_DAMAGE : 0.0);
		return amount * (float) (1.0 + more);
	}

	/** How much of {@code player}'s noise and notice-range a Ranger keeps (1 for anyone else). */
	public static double quiet(LivingEntity player) {
		if (!(player instanceof ServerPlayer sp) || !Classes.is(sp, PlayerClass.RANGER)) {
			return 1.0;
		}
		return land(sp.level(), sp.blockPosition()).equals(land(sp)) ? QUIET_LAND : QUIET;
	}

	/** The class key: read the ground; with sneak held, claim the land you stand in. */
	static void key(ServerPlayer player, boolean sneak) {
		if (!(player.level() instanceof ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		if (sneak) {
			Long at = player.getAttached(CLAIMED);
			if (at != null && now - at < CLAIM_COOLDOWN && now >= at) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.ranger.claimed_lately"));
				return;
			}
			String here = land(level, player.blockPosition());
			player.setAttached(FAVOUR, new Favour(foe(player), here));
			player.setAttached(CLAIMED, now);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.ranger.land", Component.translatable("option.jaymoskingdoms.land." + here)));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 0.8F);
			return;
		}
		Long last = TRACKED.get(player.getUUID());
		if (last != null && now - last < TRACK_COOLDOWN && now >= last) {
			return;
		}
		TRACKED.put(player.getUUID(), now);
		boolean home = land(level, player.blockPosition()).equals(land(player));
		String kind = foe(player);
		// their favoured foe's trail is read as far and as old as any tracker reads, and stands out
		TrailReading.read(level, player, home ? 1 : 0, e -> e instanceof LivingEntity living && isFoe(kind, living));
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.ranger.tracking"));
	}
}
