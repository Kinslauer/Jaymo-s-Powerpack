package com.jaymoskingdoms;

import com.google.gson.JsonObject;
import com.jaymoskingdoms.forms.Lasso;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

/**
 * Captives (docs/StoryQuests-Plan.md, kidnapping): what you can do with someone lying hogtied ({@link Lasso}).
 * <ul>
 * <li><b>Freeing them.</b> Use held on them (not sneaking, which lifts them) with a blade, an axe or shears cuts the
 * knots ({@link #CUT_TICKS}); with an empty hand you untie them, slower ({@link #UNTIE_TICKS}). That's how a captive
 * held by outlaws is freed ({@link QuestJobs}, {@link Quest#HOSTAGE}), or a friend someone tied up.</li>
 * <li><b>The wanted.</b> Someone with a bounty on them, or a criminal a board's capture job wants alive
 * ({@link Quest#CAPTURE}), lying tied: use them with an empty hand and they talk, and what they offer is in their
 * temperament (one offer each, once): the kindhearted ask to make amends, the cheerful to be let off and owe you, the
 * greedy bribe, the brave would serve you, the proud ask for a fair fight, the devout confess, the dutiful come
 * quietly, the suspicious tell who sold them out, the timid where the rest of their lot hide, the hotheaded rage (and
 * are gagged), the spiteful curse you (unless you make them swear off it), the gloomy ask for an end and are given
 * hope. Letting one go is against the law (companions who hold to it leave you, {@link Convictions}), takes their
 * bounty off the board, and fails a capture job.</li>
 * <li><b>Handed over.</b> Brought in tied (or downed) to the court or the poster ({@link Bounties#claim},
 * {@link QuestJobs#handIn}): what they agreed to tells (a confession pays more, a quiet one or a fair fight won is a
 * good name), and they say a last word; the spiteful send someone after you later, and those let go come back
 * ({@link Tales.Return}).</li>
 * </ul>
 * Temper says how hard they work at the knots too ({@link #toughness}).
 */
final class Captures {
	// ---- tuning ----
	/** How long use is held to cut the knots, and to untie them by hand (ticks); a gap longer than this starts it over. */
	static final int CUT_TICKS = 30, UNTIE_TICKS = 70, GAP = 8;
	/** How much longer (over 1) or shorter a temper takes to work out of a hogtie. */
	static final double HOTHEADED_KNOTS = 0.6, BRAVE_KNOTS = 0.8, PROUD_KNOTS = 0.9, DUTIFUL_KNOTS = 1.4, GLOOMY_KNOTS = 1.5, TIMID_KNOTS = 1.6;
	/** A bribe is this share of what they're worth; a confession pays this much more. */
	static final double BRIBE_SHARE = 1.2, CONFESSION = 1.25;
	/** Good name with the court for one handed over quietly, or after a fair fight won; lost for taking one into your service. */
	static final int QUIET_NAME = 2, DUEL_NAME = 4, SERVICE_NAME = 6;
	/** The chance a spiteful one swears off revenge when made to. */
	static final float SWEAR_OFF = 0.5F;
	/** When the let-go come back (days, least and most), and the spiteful's friends. */
	static final int[] AMENDS_DAYS = {3, 7}, OWED_DAYS = {4, 9}, REVENGE_DAYS = {6, 14};
	/** A fair fight: how near it's kept, how hard and how often a townsperson strikes in one (ticks), how near they swing. */
	static final double DUEL_RANGE = 40.0, STRIKE_REACH = 2.6;
	static final float STRIKE_DAMAGE = 5.0F, STRIKE_SPEED = 0.7F;
	static final int STRIKE_TICKS = 20;

	/** On a captive: what they agreed to ("": nothing yet), so each offer is made once. Kept. */
	static final AttachmentType<String> MOOD = AttachmentRegistry.create(JaymosKingdoms.id("capture_mood"),
		builder -> builder.persistent(Codec.STRING));

	/** Use held on a tied body: by whom, on whom, from when, last seen when (game time). */
	private record Cutting(UUID body, long start, long last) {
	}

	private static final Map<UUID, Cutting> CUTTING = new HashMap<>();
	/** When each player last used a tied body (game time): a fresh click talks, a held one goes on cutting. */
	private static final Map<UUID, Long> LAST_USE = new HashMap<>();
	/** Fair fights under way: the captive, the one they fight; townspeople's next swing. */
	private record Duel(ServerLevel level, UUID player) {
	}

	private static final Map<UUID, Duel> DUELS = new HashMap<>();
	private static final Map<UUID, Long> SWINGS = new HashMap<>();

	private Captures() {
	}

	static void register() {
		Lasso.toughness = Captures::toughness;
		UseEntityCallback.EVENT.register(Lasso.EARLY, Captures::use);
		ServerTickEvents.END_SERVER_TICK.register(Captures::duels);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CUTTING.clear();
			LAST_USE.clear();
			DUELS.clear();
			SWINGS.clear();
		});
	}

	/** How much harder (over 1) a mob finds working out of a hogtie: the hotheaded tear at it, the timid hardly dare. */
	private static double toughness(LivingEntity body) {
		Temperaments.Temperament t = Temperaments.of(body);
		return t == null ? 1.0 : switch (t) {
			case HOTHEADED -> HOTHEADED_KNOTS;
			case BRAVE -> BRAVE_KNOTS;
			case PROUD -> PROUD_KNOTS;
			case DUTIFUL -> DUTIFUL_KNOTS;
			case GLOOMY -> GLOOMY_KNOTS;
			case TIMID -> TIMID_KNOTS;
			default -> 1.0;
		};
	}

	static String mood(LivingEntity body) {
		return body.getAttachedOrElse(MOOD, "");
	}

	// ---- using a tied body ---------------------------------------------------------------------------------------------

	private static InteractionResult use(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || player.isShiftKeyDown() || !(entity instanceof LivingEntity body) || !Lasso.hogtied(body)
			|| Lasso.hogtied(player)) {
			return InteractionResult.PASS;
		}
		ItemStack held = player.getMainHandItem();
		boolean blade = Nets.blade(held);
		if (!blade && !held.isEmpty()) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer p)) {
			return InteractionResult.SUCCESS;
		}
		long now = server.getGameTime();
		Long last = LAST_USE.put(p.getUUID(), now);
		boolean fresh = last == null || now - last > GAP;
		if (!(body instanceof Player) && wanted(server, body)) {
			// (the wanted aren't cut loose by a slip of the blade: they're talked to, and let go only by choice)
			if (blade) {
				return InteractionResult.PASS;
			}
			if (fresh) {
				talk(server, p, body);
			}
			return InteractionResult.SUCCESS;
		}
		Cutting c = CUTTING.get(p.getUUID());
		if (c == null || fresh || !c.body().equals(body.getUUID())) {
			c = new Cutting(body.getUUID(), now, now);
		}
		long done = now - c.start();
		int need = blade ? CUT_TICKS : UNTIE_TICKS;
		if (done < need) {
			CUTTING.put(p.getUUID(), new Cutting(c.body(), c.start(), now));
			HudBars.hold(p, "captive", done / (float) need, HudBars.GREY,
				Component.translatable(blade ? "bar.jaymoskingdoms.cutting" : "bar.jaymoskingdoms.untying", body.getDisplayName()), true);
			if (done % 8 == 0) {
				server.playSound(null, body.getX(), body.getY(), body.getZ(), blade ? SoundEvents.SHEEP_SHEAR : SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 0.6F, 1.2F);
			}
			return InteractionResult.SUCCESS;
		}
		CUTTING.remove(p.getUUID());
		Lasso.free(body);
		body.removeAttached(MOOD);
		server.playSound(null, body.getX(), body.getY(), body.getZ(), blade ? SoundEvents.SHEEP_SHEAR : SoundEvents.LEAD_BREAK, SoundSource.PLAYERS, 1.0F, 0.9F);
		p.sendOverlayMessage(Component.translatable(blade ? "captive.jaymoskingdoms.cut" : "captive.jaymoskingdoms.untied", body.getDisplayName()).withStyle(ChatFormatting.GOLD));
		QuestJobs.freed(server, p, body);
		return InteractionResult.SUCCESS;
	}

	/** Whether {@code body} is wanted alive: a live bounty on them, or a capture job's. */
	static boolean wanted(ServerLevel level, LivingEntity body) {
		return Bounties.wanted(level.getServer(), body) || QuestJobs.wantedFor(level, body) != null;
	}

	/** What {@code body} is worth (emeralds) and to whom: their bounty's court and price, or the capture job's poster's town and pay. */
	private record Worth(@Nullable Faction court, int price) {
	}

	private static Worth worth(ServerLevel level, LivingEntity body) {
		Bounties.Bounty b = Bounties.anyOn(level.getServer(), body);
		if (b != null) {
			return new Worth(Factions.get(level.getServer(), b.court()), b.price());
		}
		Quest q = QuestJobs.wantedFor(level, body);
		return q == null ? new Worth(null, 0) : new Worth(Factions.get(level.getServer(), q.giver().faction()), q.reward().emeralds());
	}

	private static Temperaments.Temperament temper(LivingEntity body) {
		Temperaments.Temperament t = Temperaments.of(body);
		return t == null ? Temperaments.Temperament.GLOOMY : t;
	}

	private static String key(LivingEntity body) {
		return temper(body).name().toLowerCase(java.util.Locale.ROOT);
	}

	private static Component line(ServerPlayer player, LivingEntity body, String what) {
		return Languages.heard(player, body, Component.translatable("captive.jaymoskingdoms." + key(body) + "." + what));
	}

	/** {@code player} talks to {@code body}, tied and wanted: they plead, and make the offer their temper makes, once. */
	private static void talk(ServerLevel level, ServerPlayer player, LivingEntity body) {
		String mood = mood(body);
		List<Menus.Choice> choices = new ArrayList<>();
		Component words;
		if (mood.isEmpty()) {
			words = Languages.heard(player, body, Component.translatable("captive.jaymoskingdoms." + key(body) + ".plea." + level.getRandom().nextInt(2)));
			choices.add(Menus.Choice.of(Component.translatable("captive.jaymoskingdoms." + key(body) + ".offer"), p -> offer(level, p, body)));
		} else {
			words = Languages.heard(player, body, Component.translatable("captive.jaymoskingdoms.mood." + mood));
		}
		choices.add(Menus.Choice.of(Component.translatable("captive.jaymoskingdoms.let_go"), p -> {
			if (Lasso.hogtied(body)) {
				Speech.tell(level, body, p, line(p, body, "freed"), Speech.GOLD);
				letGo(level, p, body, "let_go");
			}
		}));
		choices.add(Menus.Choice.of(Component.translatable("captive.jaymoskingdoms.leave"), p -> {
		}));
		Menus.ask(player, body, words, choices);
	}

	/** The offer {@code body}'s temper makes, taken. */
	private static void offer(ServerLevel level, ServerPlayer player, LivingEntity body) {
		if (!body.isAlive() || !Lasso.hogtied(body) || !mood(body).isEmpty()) {
			return;
		}
		Worth w = worth(level, body);
		switch (temper(body)) {
			case KINDHEARTED -> {
				// (let go to make it right: they come back with something for you)
				Speech.tell(level, body, player, line(player, body, "taken"), Speech.GOLD);
				comeBack(level, player, body, "thanks", AMENDS_DAYS, List.of("minecraft:emerald*" + Math.max(2, w.price() / 3)), w.court());
				letGo(level, player, body, "let_go");
			}
			case CHEERFUL -> {
				// (let off: they owe you, and come back one day to ride with you)
				Speech.tell(level, body, player, line(player, body, "taken"), Speech.GOLD);
				comeBack(level, player, body, "recruit", OWED_DAYS, List.of(), w.court());
				letGo(level, player, body, "let_go");
			}
			case GREEDY -> {
				// (a bribe, more than the price on them: coins sewn into a coat)
				int bribe = (int)Math.ceil(Math.max(1, w.price()) * BRIBE_SHARE);
				Currencies.give(player, BaneCut.cut(player, Currencies.payEmeralds(Currencies.of(level.getServer(), w.court()), bribe)));
				Speech.tell(level, body, player, line(player, body, "taken"), Speech.GOLD);
				Convictions.affronted(player, List.of(Convictions.Belief.HONOUR.key), null);
				letGo(level, player, body, "let_go");
			}
			case BRAVE -> {
				// (into your service, if they can be: off the board, though the court thinks less of you for it)
				if (!McaCompat.swear(body, player)) {
					// (they'd serve, but there's no taking them on: they come quietly instead)
					body.setAttached(MOOD, "quiet");
					Lasso.fasten(body);
					Speech.tell(level, body, player, line(player, body, "refused"), Speech.WHITE);
					return;
				}
				Lasso.drop(body);
				body.removeAttached(MOOD);
				Speech.tell(level, body, player, line(player, body, "taken"), Speech.GOLD);
				if (w.court() != null) {
					Reputation.change(player, w.court(), -SERVICE_NAME);
				}
				Bounties.withdraw(level.getServer(), body.getStringUUID());
				QuestJobs.lost(level, body, "joined");
				Convictions.affronted(player, List.of(Convictions.Belief.LAW.key), null);
			}
			case PROUD -> {
				// (a fair fight: cut loose, and they come at you; beaten, they go in with their head up)
				Lasso.drop(body);
				body.setAttached(MOOD, "duel");
				DUELS.put(body.getUUID(), new Duel(level, player.getUUID()));
				Speech.tell(level, body, player, line(player, body, "taken"), Speech.RED);
			}
			case DEVOUT -> {
				body.setAttached(MOOD, "confessed");
				Lasso.fasten(body);
				Speech.tell(level, body, player, Languages.heard(player, body, Component.translatable("captive.jaymoskingdoms.devout.taken",
					crime(level, body))), Speech.WHITE);
			}
			case DUTIFUL -> {
				body.setAttached(MOOD, "quiet");
				Lasso.fasten(body);
				Speech.tell(level, body, player, line(player, body, "taken"), Speech.WHITE);
			}
			case SUSPICIOUS -> {
				// (who sold them out: a rumour, the start of a tale now and then, or a place worth a look)
				body.setAttached(MOOD, "told");
				Lasso.fasten(body);
				Component told = Tales.rumourHeard(level, body, player);
				if (told == null || told.getString().isEmpty()) {
					net.minecraft.core.BlockPos at = Rumours.place(level, body.blockPosition());
					told = at == null ? Component.translatable("captive.jaymoskingdoms.suspicious.nothing") : Rumours.words(body.position(), at);
				}
				Speech.tell(level, body, player, Languages.heard(player, body, Component.translatable("captive.jaymoskingdoms.suspicious.taken", told)), Speech.WHITE);
			}
			case TIMID -> {
				// (where the rest of their lot hide: another wanted one's last spot)
				body.setAttached(MOOD, "told");
				Lasso.fasten(body);
				Component where = Bounties.hideout(level.getServer(), body);
				Speech.tell(level, body, player, Languages.heard(player, body, where == null ? Component.translatable("captive.jaymoskingdoms.timid.nothing")
					: Component.translatable("captive.jaymoskingdoms.timid.taken", where)), Speech.WHITE);
			}
			case HOTHEADED -> {
				// (gagged, and the knots pulled tight again)
				body.setAttached(MOOD, "gagged");
				Lasso.tighten(body);
				level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 0.8F, 0.7F);
				player.sendOverlayMessage(Component.translatable("captive.jaymoskingdoms.hotheaded.taken", body.getDisplayName()).withStyle(ChatFormatting.GRAY));
			}
			case SPITEFUL -> {
				boolean sworn = level.getRandom().nextFloat() < SWEAR_OFF;
				body.setAttached(MOOD, sworn ? "sworn" : "cursed");
				Speech.tell(level, body, player, line(player, body, sworn ? "taken" : "cursed"), sworn ? Speech.WHITE : Speech.RED);
			}
			case GLOOMY -> {
				body.setAttached(MOOD, "hope");
				Lasso.fasten(body);
				Speech.tell(level, body, player, line(player, body, "taken"), Speech.WHITE);
			}
		}
	}

	/** What {@code body} is wanted for, in words. */
	private static Component crime(ServerLevel level, LivingEntity body) {
		Bounties.Bounty b = Bounties.anyOn(level.getServer(), body);
		if (b != null) {
			return Component.translatable("bounty.jaymoskingdoms.crime." + b.crime());
		}
		Quest q = QuestJobs.wantedFor(level, body);
		String name = q == null ? "" : q.objective().otherName();
		return Component.translatable("work.jaymoskingdoms.crime." + (name.contains("|") ? name.substring(name.indexOf('|') + 1) : "theft"));
	}

	/** {@code body}, wanted, is let go by {@code player}: untied, off the board, a capture job failed; it's against the law. */
	private static void letGo(ServerLevel level, ServerPlayer player, LivingEntity body, String why) {
		Lasso.drop(body);
		body.removeAttached(MOOD);
		DUELS.remove(body.getUUID());
		Bounties.withdraw(level.getServer(), body.getStringUUID());
		Recollections.remember(body, player, Recollections.Kind.SPARED, Component.empty());
		Convictions.affronted(player, List.of(Convictions.Belief.LAW.key), null);
		QuestJobs.lost(level, body, why);
	}

	/** {@code body} comes to find {@code player} again, {@code as} a tale's let-go do ({@link TaleCast#comeBack}), in {@code days}. */
	private static void comeBack(ServerLevel level, ServerPlayer player, LivingEntity body, String as, int[] days, List<String> gifts, @Nullable Faction court) {
		JsonObject shape = new JsonObject();
		boolean fighter = !as.equals("thanks");
		shape.addProperty("body", fighter ? "fighter" : "villager");
		if (fighter) {
			shape.addProperty("hand", "minecraft:iron_sword");
		}
		long day = Tales.day(level) + days[0] + level.getRandom().nextInt(days[1] - days[0] + 1);
		Tales.state(player).returns.add(new Tales.Return(body.getName().getString(), shape.toString(), day, as, "", gifts, court == null ? -1 : court.id()));
	}

	// ---- fair fights -----------------------------------------------------------------------------------------------------

	/** {@code mob} goes for {@code foe}: a townsperson walks up and strikes (their own AI wouldn't), anything else is set on them. */
	static void press(ServerLevel level, Mob mob, LivingEntity foe, float damage) {
		if (!(mob instanceof Villager v)) {
			if (mob.getTarget() != foe) {
				mob.setTarget(foe);
			}
			return;
		}
		v.getLookControl().setLookAt(foe);
		if (v.distanceTo(foe) <= STRIKE_REACH) {
			long now = level.getGameTime();
			if (now >= SWINGS.getOrDefault(v.getUUID(), 0L)) {
				SWINGS.put(v.getUUID(), now + STRIKE_TICKS);
				v.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				foe.hurtServer(level, level.damageSources().mobAttack(v), damage);
			}
		} else {
			BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(foe, true), STRIKE_SPEED, 1);
		}
	}

	private static void duels(MinecraftServer server) {
		if (DUELS.isEmpty() || server.getTickCount() % 5 != 0) {
			return;
		}
		for (Map.Entry<UUID, Duel> e : new ArrayList<>(DUELS.entrySet())) {
			ServerLevel level = e.getValue().level();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getValue().player());
			if (!(level.getEntity(e.getKey()) instanceof Mob crook) || !crook.isAlive()) {
				DUELS.remove(e.getKey());
				continue;
			}
			if (Downed.isDowned(crook) || Lasso.hogtied(crook)) {
				// (beaten fair: they'll go in now, and the court will hear how)
				DUELS.remove(e.getKey());
				crook.setAttached(MOOD, "duel_won");
				if (player != null) {
					Speech.tell(level, crook, player, line(player, crook, "beaten"), Speech.WHITE);
				}
				continue;
			}
			if (player == null || player.level() != level || !player.isAlive() || Downed.isDowned(player) || player.distanceTo(crook) > DUEL_RANGE) {
				// (they won, or you ran: they're off, and owe you nothing)
				DUELS.remove(e.getKey());
				crook.removeAttached(MOOD);
				crook.setTarget(null);
				if (player != null && player.level() == level) {
					Speech.tell(level, crook, player, line(player, crook, "won"), Speech.RED);
				}
				continue;
			}
			press(level, crook, player, STRIKE_DAMAGE);
		}
	}

	// ---- handed over -----------------------------------------------------------------------------------------------------

	/** What the court pays for {@code body} over the price: a confession is worth more. */
	static double bonus(LivingEntity body) {
		return mood(body).equals("confessed") ? CONFESSION : 1.0;
	}

	/**
	 * {@code body}, wanted, is handed over by {@code player} to {@code court}: a quiet one or a fair
	 * fight won does them credit, a last word, and the spiteful (unless sworn off it) send someone after them.
	 */
	static void handedOver(ServerLevel level, ServerPlayer player, LivingEntity body, @Nullable Faction court) {
		String mood = mood(body);
		if (court != null && (mood.equals("quiet") || mood.equals("hope"))) {
			Reputation.change(player, court, QUIET_NAME);
		} else if (court != null && mood.equals("duel_won")) {
			Reputation.change(player, court, DUEL_NAME);
		}
		if (Temperaments.humanoid(body)) {
			Speech.tell(level, body, player, line(player, body, "cells"), Speech.WHITE);
		}
		if (temper(body) == Temperaments.Temperament.SPITEFUL && !mood.equals("sworn") || mood.equals("cursed")) {
			comeBack(level, player, body, "revenge", REVENGE_DAYS, List.of(), court);
		}
		DUELS.remove(body.getUUID());
		Lasso.drop(body);
		// a Galactic Patrol officer's arrest (Jaymo's Powers)
		PowersCompat.patrolHandedOver(player, body);
	}
}
