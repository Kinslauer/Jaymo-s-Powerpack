package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;

/**
 * (0.7.10) Jaymo's Powers' Warrior-born Z Fighters have a tail. Townsfolk who see it out take them for a werewolf or
 * some other monster and run ({@link Frights}, crying out as they go), and keep running every time they see it, until
 * the player has made a friend of them: at {@link #EXPLAIN_HEARTS} hearts with an MCA villager the chat menu gains
 * "Explain your tail", and once told, that villager knows them and is never frightened by the tail again. Each
 * villager has to be told on their own. A wrapped tail (sneak+Z) shows nothing, which is how a bond is built at all.
 * (0.7.20) Explaining takes a tongue you share ({@link Languages}).
 *
 * <p>(0.7.443, Havens patch 7b) Andy: "they can still tie their tails down, but those who know, will always smell it". A
 * tied tail fools townsfolk, but not those who know ({@link #smells}): the Warrior-born themselves, a haven's people,
 * hunters (fletchers and leatherworkers) and the tribes' goblins and orcs. A mask hides the blood from them no longer
 * ({@code Prejudice.minds}), and now and then one says so as you pass ({@link #tick}).
 *
 * <p>Read by entity tag ({@link #TAILED}, {@link #TAIL}, set by Powers), so Kingdoms needs no Powers classes.
 */
public final class Tails {
	/** On a Warrior-born; on one whose tail is out right now. */
	static final String TAILED = "jaymospowers_tailed", TAIL = "jaymospowers_tail";
	/** The MCA hearts a villager needs with you before you can explain. */
	public static final int EXPLAIN_HEARTS = 25;
	/** The hearts telling them earns. */
	static final int EXPLAINED_HEARTS = 5;
	/** The chat menu answer (MCA dialogue {@code main}). */
	public static final String ANSWER = "jaymoskingdoms_tail";

	/** (0.7.443) How often tied tails are sniffed at (ticks), how near, the chance each time, at most once in this long a player (ticks). */
	static final int SMELL_EVERY = 40, SMELL_COOLDOWN = 20 * 150;
	static final double SMELL_RANGE = 6.0;
	static final float SMELL_CHANCE = 0.15F;
	/** (0.7.443) Lines each ({@code tail.jaymoskingdoms.smelled.<kin|haven|hunter|tribe>.N}). */
	static final int SMELL_LINES = 3;

	/** (0.7.443) Player → when one who knows last said they smelled the tail. */
	private static final java.util.Map<UUID, Long> SNIFFED = new java.util.HashMap<>();

	/** Villagers (by uuid) this player has explained the tail to; kept across deaths. */
	static final AttachmentType<List<String>> EXPLAINED = AttachmentRegistry.create(JaymosKingdoms.id("tail_explained"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath());

	private Tails() {
	}

	static void register() {
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % SMELL_EVERY == 0) {
				for (ServerPlayer p : server.getPlayerList().getPlayers()) {
					tick(p);
				}
			}
		});
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(server -> SNIFFED.clear());
	}

	/** (0.7.443) {@code p} has a tail and it's tied down. */
	static boolean tied(Player p) {
		return p.entityTags().contains(TAILED) && !tailOut(p);
	}

	/**
	 * (0.7.443) What {@code who} smells on {@code p}'s tied tail: "kin" (Warrior-born), "haven" (a haven's people), "tribe"
	 * (a goblin or an orc), "hunter" (a fletcher or leatherworker); null for anyone else, or no tied tail.
	 */
	static @org.jspecify.annotations.Nullable String smells(LivingEntity who, Player p) {
		if (who == p || !tied(p)) {
			return null;
		}
		if (VillagerHeritage.of(who) == Heritage.Kind.WARRIORBORN) {
			return "kin";
		}
		if (Greenskins.of(who) != null || Tribes.of(who) != null) {
			return "tribe";
		}
		if (Havens.home(who) != null) {
			return "haven";
		}
		if (who instanceof Villager v) {
			var trade = v.getVillagerData().profession();
			if (trade.is(VillagerProfession.FLETCHER) || trade.is(VillagerProfession.LEATHERWORKER)) {
				return "hunter";
			}
		}
		return null;
	}

	/** (0.7.443) Now and then one who knows, near and in sight, says they smell the tail {@code p} has tied down. */
	private static void tick(ServerPlayer p) {
		if (!tied(p) || p.isSpectator() || !(p.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return;
		}
		long now = level.getGameTime();
		if (now - SNIFFED.getOrDefault(p.getUUID(), -(long)SMELL_COOLDOWN) < SMELL_COOLDOWN || level.getRandom().nextFloat() >= SMELL_CHANCE) {
			return;
		}
		for (LivingEntity who : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(SMELL_RANGE),
			e -> e instanceof Villager && e.isAlive() && !e.isSleeping())) {
			String kind = smells(who, p);
			if (kind != null && who.hasLineOfSight(p)) {
				SNIFFED.put(p.getUUID(), now);
				Speech.sayTo(level, who, p, net.minecraft.network.chat.Component.translatable("tail.jaymoskingdoms.smelled." + kind + "."
					+ level.getRandom().nextInt(SMELL_LINES), Identities.shownName(p)), Speech.WHITE);
				return;
			}
		}
	}

	static boolean tailOut(Entity e) {
		return e.entityTags().contains(TAIL);
	}

	static boolean explained(ServerPlayer player, UUID villager) {
		List<String> told = player.getAttached(EXPLAINED);
		return told != null && told.contains(villager.toString());
	}

	/** Whether {@code v} sees a monster in {@code player}: the tail's out and nobody's explained it to them. */
	static boolean feared(ServerPlayer player, Villager v) {
		return !player.isCreative() && !player.isSpectator() && tailOut(player) && !explained(player, v.getUUID());
	}

	/** Whether "Explain your tail" shows in {@code villager}'s chat menu for {@code player}. */
	public static boolean canExplain(ServerPlayer player, Villager villager) {
		return player.entityTags().contains(TAILED) && !explained(player, villager.getUUID())
			&& McaCompat.heartsOf(villager, player) >= EXPLAIN_HEARTS && Languages.understands(player, villager);
	}

	/** {@code player} explains: {@code villager} understands, says so, and fears the tail no more. */
	public static void explain(ServerPlayer player, Villager villager) {
		if (!canExplain(player, villager)) {
			return;
		}
		List<String> told = new ArrayList<>(player.getAttachedOrElse(EXPLAINED, List.of()));
		told.add(villager.getUUID().toString());
		player.setAttached(EXPLAINED, told);
		McaCompat.hearts(villager, player, EXPLAINED_HEARTS);
		villager.level().playSound(null, villager.blockPosition(), SoundEvents.VILLAGER_YES, SoundSource.NEUTRAL, 1.0F, 1.0F);
	}
}
