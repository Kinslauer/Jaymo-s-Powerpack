package com.jaymoskingdoms;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.util.Prediction;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.59) How a ruler's guards take a stranger (a player) about him, by his temper ({@link Tempers.Temper}). Each
 * stranger near him builds up <i>heat</i> with his guards, a little each second they're close (within {@link #SPACE})
 * or lingering (within {@link #WATCH_RANGE}), more each time they lay hands on him (right-click), and it cools off
 * ({@link #COOL} a second) once they give him room. At {@link Rules#wary} the guards act on it; at
 * {@link Rules#driveOut} (for the tempers that do) they walk the stranger out of the city ({@link #CITY_RADIUS} from
 * the seat) and keep them out for {@link #BAN_TICKS}. A friendly stranger gets his favour once a day instead.
 * <ul>
 * <li><b>Paranoid</b>: anyone near him is suspect. Too close: shoved back. Watched: town guards dog them, shoving them
 * away from him and threatening them. Then driven out.</li>
 * <li><b>Tyrant</b>: you kneel (sneak) before him, or it's insolence: guards demand it, then slap you and fine you an
 * emerald for his coffers, then throw you out. Kneeling cools them, and wins a nod (a little good name) once a day.</li>
 * <li><b>Warlord</b>: a drawn weapon near him is a challenge: sheathe it (hold something else) or be shoved, then dogged,
 * then driven out. Unarmed and well armoured, you get a soldier's salute (a little good name) once a day.</li>
 * <li><b>Noble</b>: greets you once a day, with bread if you're in his good books. Pester him and his guards ask you,
 * politely, to give him room; at worst a gentle push. Never thrown out.</li>
 * <li><b>Reveler</b>: a gift and a cheer once a day. Pester him and his guards toss you off, laughing, and that's the
 * end of it. Never thrown out.</li>
 * <li><b>Purist</b>: (0.7.130) his own blood ({@link Purists}) is welcome: a proud word and good name once a day. Anyone
 * else near him is beneath him: his guards sneer at them and shove them out of his path, then throw them out of his
 * city if they keep hanging about.</li>
 * </ul>
 * None of it is a fight: the guards never draw on you. Only an actual attack (hitting the king or a guard) makes them
 * hostile, as anywhere. His realm's own people and his sworn wardens ({@link Wardens}) are no strangers.
 * <p>
 * (0.7.184) Andy: "guards should make way for you regardless of their king's temperament if you are carrying a quest
 * item". Whoever carries a quest's letter or keepsake, or brings him the work he gave them ({@link Quests#questItem}), or
 * is being led to him by one of his guards ({@link Audiences}), has free passage ({@link #passage}): no heat, no
 * shoving, not seen out of the city (unless exiled as a vigilante), and guards in their way step aside.
 */
final class Retinue {
	/** Closer than this to him is close; within {@link #WATCH_RANGE} is lingering; his favours reach {@link #FAVOUR_RANGE}. */
	static final double SPACE = 5.0, WATCH_RANGE = 12.0, FAVOUR_RANGE = 8.0;
	/** Heat lost a second once you're away (or, before a tyrant, kneeling). */
	static final int COOL = 1, KNEEL_COOL = 2;
	/** Never reached. */
	static final int NEVER = Integer.MAX_VALUE;

	/**
	 * How his guards take a stranger: heat a second close and lingering, heat for each right-click on him, when
	 * they act on it, when they drive the stranger out ({@link #NEVER}: they don't), and how hard they shove.
	 */
	record Rules(int close, int linger, int handle, int wary, int driveOut, double shove) {
	}

	/** By temper: TYRANT, NOBLE, WARLORD, PARANOID, REVELER, PURIST (a purist's only for those not of his blood). */
	static final Rules[] RULES = {
		new Rules(2, 0, 15, 20, 50, 0.8),
		new Rules(1, 0, 6, 20, NEVER, 0.4),
		new Rules(3, 1, 8, 15, 50, 0.8),
		new Rules(3, 1, 10, 25, 60, 0.8),
		new Rules(0, 0, 8, 20, NEVER, 1.0),
		new Rules(2, 1, 12, 20, 60, 0.9)};

	/** The ban (ticks: 5 minutes), and how far from the seat the city reaches. */
	static final int BAN_TICKS = 6000;
	static final double CITY_RADIUS = 80.0;
	/** Guards that come for one stranger at most, from how far, how close they get to shove, and how hard when seeing them out. */
	static final int HARASSERS = 2;
	static final double CALL_RANGE = 40.0, REACH = 2.8, THROW = 1.1;
	/** A guard shoves at most this often (ticks); a warning, threat or punishment to the same player at most this often. */
	static final int SHOVE_TICKS = 30, WARN_TICKS = 200, THREAT_TICKS = 100;
	/** How fast a guard walks over (watched), and when seeing you out. */
	static final double WALK_SPEED = 0.9, DRIVE_SPEED = 1.1;
	/** How far guards' words carry. */
	static final double SPEECH_RANGE = 16.0;
	/** The tyrant: a slap's damage (only above {@link #SLAP_SPARES} health) and the fine for insolence (emeralds, to his treasury). */
	static final float SLAP_DAMAGE = 1.0F, SLAP_SPARES = 10.0F;
	static final int FINE = 1;
	/** The warlord salutes a stranger with at least this much armour. */
	static final int SALUTE_ARMOUR = 15;
	/** Good name a daily favour wins (kneeling to a tyrant, a warlord's salute). */
	static final int FAVOUR = 1;
	/** The noble gives this much bread to strangers with at least {@link #NOBLE_FRIEND} good name with his realm. */
	static final int NOBLE_BREAD = 2, NOBLE_FRIEND = 20;
	/** The reveler's toss: up, and out. */
	static final double TOSS_UP = 0.7, TOSS_OUT = 1.0;
	/** (0.7.130) The purist gives one of his own blood this much bread with his daily word. */
	static final int PURIST_BREAD = 2;
	/** (0.7.184) A guard this close to one with free passage, and ahead of them, steps aside; "make way" at most this often (ticks). */
	static final double MAKE_WAY_RANGE = 2.5;
	static final int MAKE_WAY_TICKS = 400;

	private static final class Watch {
		int heat;
		long bannedUntil = -1L;
		long touched;
		long warned = Long.MIN_VALUE / 2;
		long threatened = Long.MIN_VALUE / 2;
		/** (0.7.67) An exiled vigilante's ban ({@link #exile}): it lapses without a word of forgiveness. */
		boolean exiled;

		boolean banned(long now) {
			return this.bannedUntil > now;
		}
	}

	/** "faction/player" → what his guards think of them. */
	private static final Map<String, Watch> WATCHED = new HashMap<>();
	/** "faction/player" → the day of his last favour to them. */
	private static final Map<String, Long> FAVOURED = new HashMap<>();
	/** Guard → the player he's been sent after. */
	private static final Map<UUID, UUID> SENT = new HashMap<>();
	/** Guard → the faction he's from (to find the player's watch). */
	private static final Map<UUID, Integer> SENT_BY = new HashMap<>();
	/** (0.7.184) "faction/player" → when his guards last called to make way for them. */
	private static final Map<String, Long> MADE_WAY = new HashMap<>();

	private Retinue() {
	}

	static void register() {
		UseEntityCallback.EVENT.register(Retinue::use);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			WATCHED.clear();
			FAVOURED.clear();
			SENT.clear();
			SENT_BY.clear();
			SHOVED.clear();
			MADE_WAY.clear();
		});
	}

	private static String key(int faction, UUID player) {
		return faction + "/" + player;
	}

	private static Rules rules(Tempers.Temper t) {
		return RULES[t.ordinal()];
	}

	private static String speech(Tempers.Temper t, String what) {
		return "speech.jaymoskingdoms.retinue." + t.key() + "." + what;
	}

	/** {@code p} is no stranger to him: his own people, his wardens, or not really there. */
	private static boolean trusted(ServerLevel level, Faction f, ServerPlayer p) {
		return p.isCreative() || p.isSpectator() || f.members().containsKey(p.getUUID()) || Wardens.of(level.getServer(), f.id()).contains(p.getUUID());
	}

	/**
	 * (0.7.184) {@code p} has free passage past {@code f}'s guards, whatever its ruler's temper: they carry a quest's
	 * letter or keepsake, or work for him ({@link Quests#questItem}), or one of his guards is taking them to him.
	 */
	static boolean passage(Faction f, ServerPlayer p, long now) {
		return Audiences.passing(f.id(), p.getUUID(), now) || Quests.questItem(p, f.id());
	}

	/** (0.7.184) {@code p} has been thrown out of {@code f}'s city, and isn't to come back yet. */
	static boolean banned(Faction f, ServerPlayer p, long now) {
		Watch w = WATCHED.get(key(f.id(), p.getUUID()));
		return w != null && w.banned(now);
	}

	/** A weapon in hand (for the warlord). */
	static boolean armed(ServerPlayer p) {
		ItemStack s = p.getMainHandItem();
		return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES) || s.is(Items.BOW) || s.is(Items.CROSSBOW) || s.is(Items.TRIDENT) || s.is(Items.MACE);
	}

	/** Kneeling (sneaking), for the tyrant. */
	static boolean kneeling(ServerPlayer p) {
		return p.isShiftKeyDown();
	}

	/** Whether what {@code p} is doing near him counts against them at all. */
	private static boolean heats(Tempers.Temper t, Faction f, ServerPlayer p) {
		return switch (t) {
			case TYRANT -> !kneeling(p);
			case WARLORD -> armed(p);
			// (0.7.130) only those not of his blood
			case PURIST -> !Purists.kin(f, p);
			default -> true;
		};
	}

	/** His guards walk over to dog a stranger they're wary of (the others deal with it on the spot). */
	private static boolean dogs(Tempers.Temper t) {
		return t == Tempers.Temper.PARANOID || t == Tempers.Temper.WARLORD;
	}

	private static double flatDistance(Vec3 a, Vec3 b) {
		double dx = a.x - b.x, dz = a.z - b.z;
		return Math.sqrt(dx * dx + dz * dz);
	}

	private static boolean inCity(Faction f, Entity e) {
		return flatDistance(e.position(), Vec3.atBottomCenterOf(f.seat())) <= CITY_RADIUS;
	}

	private static boolean isRuler(Faction f, Folk folk) {
		return f.leader().map(folk.getUUID()::equals).orElse(false);
	}

	/** Laying hands on him (right-clicking him). */
	private static InteractionResult use(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level world, InteractionHand hand,
		Entity entity, net.minecraft.world.phys.@Nullable EntityHitResult hit) {
		if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer p) || !(entity instanceof Folk king)
			|| !(world instanceof ServerLevel level)) {
			return InteractionResult.PASS;
		}
		Faction f = Factions.get(level.getServer(), king.factionId());
		Tempers.Temper t = Tempers.of(f);
		long now = level.getGameTime();
		if (f == null || t == null || !isRuler(f, king) || trusted(level, f, p) || passage(f, p, now)) {
			// (0.7.184) one bringing him word or work, or brought before him, may approach
			return InteractionResult.PASS;
		}
		if (t == Tempers.Temper.PURIST && Purists.kin(f, p)) {
			// (0.7.130) one of his own blood may clasp his hand
			Speech.say(level, king, Component.translatable(speech(t, "kin_hands"), Purists.bloodName(f)), Speech.GOLD, SPEECH_RANGE);
			return InteractionResult.PASS;
		}
		Watch w = WATCHED.computeIfAbsent(key(f.id(), p.getUUID()), k -> new Watch());
		w.heat = Math.min(cap(t), w.heat + rules(t).handle());
		w.touched = now;
		PathfinderMob guard = nearestGuard(level, f, p, SPACE * 2);
		Speech.say(level, guard != null ? guard : king, Component.translatable(speech(t, "hands"), Purists.name(Heritage.of(p)), Purists.bloodName(f)),
			t == Tempers.Temper.NOBLE || t == Tempers.Temper.REVELER ? Speech.GOLD : Speech.RED, SPEECH_RANGE);
		if (t == Tempers.Temper.PARANOID || t == Tempers.Temper.TYRANT || t == Tempers.Temper.PURIST) {
			shove(level, guard, p, p.position().subtract(king.position()), rules(t).shove(), 0.25);
		}
		return InteractionResult.PASS;
	}

	/** Heat never needs to go past where the last step happens. */
	private static int cap(Tempers.Temper t) {
		Rules r = rules(t);
		return r.driveOut() != NEVER ? r.driveOut() : r.wary();
	}

	/**
	 * Every {@link Tempers#TICK} for a realm with a ruler whose seat is loaded; {@code king} is null while he's not about.
	 * (0.7.175) A ruler crowned from the townsfolk too (he'd had none since 0.7.138), and the realm's MCA guards do the
	 * shoving and dogging as its folk guards did.
	 */
	static void tick(ServerLevel level, Faction f, Tempers.Temper t, @Nullable PathfinderMob king, long now) {
		Rules r = rules(t);
		if (king != null) {
			for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, king.getBoundingBox().inflate(WATCH_RANGE))) {
				if (trusted(level, f, p)) {
					continue;
				}
				if (passage(f, p, now)) {
					// (0.7.184) free passage: his guards make way
					makeWay(level, f, king, p, now);
				} else {
					near(level, f, t, r, king, p, now);
				}
			}
		}
		String prefix = f.id() + "/";
		for (Iterator<Map.Entry<String, Watch>> it = WATCHED.entrySet().iterator(); it.hasNext();) {
			Map.Entry<String, Watch> e = it.next();
			if (!e.getKey().startsWith(prefix)) {
				continue;
			}
			Watch w = e.getValue();
			UUID id = UUID.fromString(e.getKey().substring(prefix.length()));
			ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
			if (w.bannedUntil >= 0 && !w.banned(now)) {
				// the ban's run out: a clean slate
				it.remove();
				if (p != null && !w.exiled) {
					p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.retinue.forgiven", f.name()).withStyle(ChatFormatting.GRAY));
				}
				continue;
			}
			if (!w.banned(now) && w.touched != now) {
				w.heat -= COOL;
				if (w.heat <= 0) {
					it.remove();
					continue;
				}
			}
			if (p == null || p.level() != level || trusted(level, f, p) || !w.exiled && passage(f, p, now)) {
				continue;
			}
			if (w.banned(now) ? inCity(f, p) : dogs(t) && w.heat >= r.wary() && king != null && p.distanceTo(king) <= WATCH_RANGE * 2) {
				send(level, f, p);
			}
		}
		// guards whose errand is over go back to their posts
		for (Iterator<Map.Entry<UUID, UUID>> it = SENT.entrySet().iterator(); it.hasNext();) {
			Map.Entry<UUID, UUID> e = it.next();
			Integer from = SENT_BY.get(e.getKey());
			if (from == null || from != f.id()) {
				continue;
			}
			if (!(level.getEntity(e.getKey()) instanceof LivingEntity g) || !g.isAlive() || !wanted(level, f, t, e.getValue(), now)) {
				SENT_BY.remove(e.getKey());
				SHOVED.remove(e.getKey());
				it.remove();
			}
		}
	}

	/** A stranger {@code p} within {@link #WATCH_RANGE} of him, this second. */
	private static void near(ServerLevel level, Faction f, Tempers.Temper t, Rules r, PathfinderMob king, ServerPlayer p, long now) {
		double d = p.distanceTo(king);
		if (d > WATCH_RANGE) {
			return;
		}
		String k = key(f.id(), p.getUUID());
		Watch w = WATCHED.get(k);
		int gain = heats(t, f, p) ? (d <= SPACE ? r.close() : r.linger()) : 0;
		if (gain > 0) {
			if (w == null) {
				w = new Watch();
				WATCHED.put(k, w);
			}
			int before = w.heat;
			w.heat = Math.min(cap(t), w.heat + gain);
			w.touched = now;
			if (before < r.wary() && w.heat >= r.wary() && !w.banned(now)) {
				p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.retinue." + t.key() + ".wary", king.getDisplayName(), Purists.bloodName(f))
					.withStyle(t == Tempers.Temper.NOBLE || t == Tempers.Temper.REVELER ? ChatFormatting.YELLOW : ChatFormatting.RED));
			}
		} else if (w != null && t == Tempers.Temper.TYRANT && kneeling(p) && d <= FAVOUR_RANGE) {
			w.heat = Math.max(0, w.heat - KNEEL_COOL);
			w.touched = now;
		}
		// on the spot: too close
		if (d <= SPACE && (t == Tempers.Temper.PARANOID || t == Tempers.Temper.WARLORD && armed(p) || t == Tempers.Temper.TYRANT && !kneeling(p)
			|| t == Tempers.Temper.PURIST && !Purists.kin(f, p))) {
			Watch ww = w != null ? w : WATCHED.computeIfAbsent(k, x -> new Watch());
			PathfinderMob guard = nearestGuard(level, f, p, SPACE);
			if (t != Tempers.Temper.TYRANT) {
				shove(level, guard, p, p.position().subtract(king.position()), r.shove(), 0.25);
			}
			if (now - ww.warned >= WARN_TICKS) {
				ww.warned = now;
				p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.retinue." + t.key() + ".close", king.getDisplayName(),
					Purists.name(Heritage.of(p))).withStyle(ChatFormatting.RED));
				if (guard != null && t != Tempers.Temper.PARANOID) {
					Speech.say(level, guard, Component.translatable(speech(t, "close"), Purists.name(Heritage.of(p)), Purists.bloodName(f)), Speech.RED,
						SPEECH_RANGE);
				}
			}
		}
		if (w != null && !w.banned(now) && w.heat >= r.driveOut()) {
			banish(level, f, t, king, p, w, now);
			return;
		}
		if (w != null && !w.banned(now) && w.heat >= r.wary() && !dogs(t) && now - w.threatened >= THREAT_TICKS) {
			w.threatened = now;
			punish(level, f, t, r, king, p, w);
			return;
		}
		if ((w == null || w.heat < r.wary()) && d <= FAVOUR_RANGE) {
			favour(level, f, t, king, p, now);
		}
	}

	/** The tempers that deal with a stranger they're wary of on the spot (not by dogging them). */
	private static void punish(ServerLevel level, Faction f, Tempers.Temper t, Rules r, PathfinderMob king, ServerPlayer p, Watch w) {
		PathfinderMob guard = nearestGuard(level, f, p, SPACE + 3.0);
		Entity by = guard != null ? guard : king;
		Vec3 away = p.position().subtract(king.position());
		switch (t) {
			case TYRANT -> {
				// a slap, and a fine for his coffers
				shove(level, guard, p, away, r.shove(), 0.25);
				if (p.getHealth() > SLAP_SPARES) {
					p.hurtServer(level, level.damageSources().mobAttack(guard != null ? guard : king), SLAP_DAMAGE);
				}
				if (takeEmeralds(p, FINE)) {
					Factions.update(level.getServer(), f.id(), x -> x.withTreasury(x.treasury() + FINE));
					p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.retinue.tyrant.fined", FINE, king.getDisplayName())
						.withStyle(ChatFormatting.RED));
				}
				Speech.say(level, by, Component.translatable(speech(t, "watch" + level.getRandom().nextInt(4))), Speech.RED, SPEECH_RANGE);
			}
			case NOBLE -> {
				// a polite word, and a gentle push
				shove(level, guard, p, away, r.shove(), 0.1);
				Speech.say(level, by, Component.translatable(speech(t, "watch" + level.getRandom().nextInt(4))), Speech.GOLD, SPEECH_RANGE);
			}
			case REVELER -> {
				// tossed off, laughing, and that's the end of it
				shove(level, guard, p, away, TOSS_OUT, TOSS_UP);
				level.sendParticles(ParticleTypes.NOTE, p.getX(), p.getY() + 2.0, p.getZ(), 4, 0.4, 0.3, 0.4, 1.0);
				level.playSound(null, by.blockPosition(), SoundEvents.VILLAGER_CELEBRATE, SoundSource.NEUTRAL, 1.0F, 1.0F);
				Speech.say(level, by, Component.translatable(speech(t, "watch" + level.getRandom().nextInt(4))), Speech.GOLD, SPEECH_RANGE);
				w.heat = 0;
			}
			case PURIST -> {
				// (0.7.130) a sneer, and shoved out of his path
				shove(level, guard, p, away, r.shove(), 0.2);
				Speech.say(level, by, Component.translatable(speech(t, "watch" + level.getRandom().nextInt(4)), Purists.name(Heritage.of(p)),
					Purists.bloodName(f)), Speech.RED, SPEECH_RANGE);
				level.sendParticles(ParticleTypes.ANGRY_VILLAGER, by.getX(), by.getY() + 2.1, by.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
			}
			default -> {
			}
		}
	}

	/** (0.7.184) Guards ahead of {@code p} (who has free passage) step aside for them, and once in a while call to make way. */
	private static void makeWay(ServerLevel level, Faction f, PathfinderMob king, ServerPlayer p, long now) {
		Vec3 to = king.position().subtract(p.position()).multiply(1.0, 0.0, 1.0);
		Vec3 look = p.getLookAngle().multiply(1.0, 0.0, 1.0);
		boolean called = false;
		for (Worker w : Workers.near(level, p.getBoundingBox().inflate(MAKE_WAY_RANGE),
			g -> g.isAlive() && g.factionId() == f.id() && g.role() == Folk.GUARD && Rulers.target(g.mob()) == null && !Audiences.leading(g.mob()))) {
			PathfinderMob g = w.mob();
			Vec3 off = g.position().subtract(p.position()).multiply(1.0, 0.0, 1.0);
			if (off.lengthSqr() < 1.0E-4 || off.dot(to) <= 0.0 && off.dot(look) <= 0.0) {
				// (not in their way)
				continue;
			}
			Audiences.stepAside(g, p, king.position());
			String k = key(f.id(), p.getUUID());
			if (!called && now - MADE_WAY.getOrDefault(k, Long.MIN_VALUE / 2) >= MAKE_WAY_TICKS) {
				MADE_WAY.put(k, now);
				called = true;
				Speech.say(level, g, Component.translatable("speech.jaymoskingdoms.retinue.make_way" + level.getRandom().nextInt(3)), Speech.WHITE, SPEECH_RANGE);
			}
		}
	}

	/** His daily favour to a stranger who's given no offence, if his temper has one. */
	private static void favour(ServerLevel level, Faction f, Tempers.Temper t, PathfinderMob king, ServerPlayer p, long now) {
		boolean due = switch (t) {
			case TYRANT -> kneeling(p);
			case WARLORD -> !armed(p) && p.getArmorValue() >= SALUTE_ARMOUR;
			case NOBLE, REVELER -> true;
			case PARANOID -> false;
			case PURIST -> Purists.kin(f, p);
		};
		String k = key(f.id(), p.getUUID());
		long day = Production.day(level.getServer());
		if (!due || FAVOURED.getOrDefault(k, -1L) == day) {
			return;
		}
		FAVOURED.put(k, day);
		king.getLookControl().setLookAt(p, 30.0F, 30.0F);
		Speech.say(level, king, Component.translatable(speech(t, "favour"), Purists.bloodName(f), Identities.shownName(p)),
			t == Tempers.Temper.TYRANT ? Speech.RED : Speech.GOLD, SPEECH_RANGE);
		switch (t) {
			case TYRANT, WARLORD -> Reputation.change(p, f, FAVOUR);
			case PURIST -> {
				Reputation.change(p, f, FAVOUR);
				give(p, new ItemStack(Items.BREAD, PURIST_BREAD));
			}
			case NOBLE -> {
				if (Reputation.get(p, f.id()) >= NOBLE_FRIEND) {
					give(p, new ItemStack(Items.BREAD, NOBLE_BREAD));
				}
			}
			case REVELER -> {
				give(p, level.getRandom().nextBoolean() ? new ItemStack(Items.COOKIE, 3) : new ItemStack(Items.HONEY_BOTTLE));
				level.playSound(null, king.blockPosition(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.NEUTRAL, 1.0F, 1.2F);
				level.sendParticles(ParticleTypes.NOTE, king.getX(), king.getY() + 2.3, king.getZ(), 3, 0.4, 0.2, 0.4, 1.0);
			}
			default -> {
			}
		}
		if (t == Tempers.Temper.WARLORD) {
			PathfinderMob guard = nearestGuard(level, f, p, FAVOUR_RANGE);
			if (guard != null) {
				guard.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			}
		}
	}

	private static void give(ServerPlayer p, ItemStack gift) {
		if (!p.getInventory().add(gift)) {
			p.drop(gift, false, Prediction.SERVER_ONLY);
		}
	}

	/** Takes {@code n} emeralds from {@code p} if they have that many. */
	private static boolean takeEmeralds(ServerPlayer p, int n) {
		if (Purse.count(p, Items.EMERALD) < n) {
			return false;
		}
		Purse.take(p, Items.EMERALD, n);
		return true;
	}

	/** {@code player} is still someone a guard should be after. */
	private static boolean wanted(ServerLevel level, Faction f, Tempers.Temper t, UUID player, long now) {
		Watch w = WATCHED.get(key(f.id(), player));
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(player);
		if (w == null || p == null || p.level() != level || !p.isAlive() || trusted(level, f, p) || !w.exiled && passage(f, p, now)) {
			return false;
		}
		return w.banned(now) ? inCity(f, p) : dogs(t) && w.heat >= rules(t).wary();
	}

	private static void banish(ServerLevel level, Faction f, Tempers.Temper t, PathfinderMob king, ServerPlayer p, Watch w, long now) {
		w.bannedUntil = now + BAN_TICKS;
		w.threatened = now;
		// (0.7.419) a road deed: shown the city's gate
		Deeds.done(level.getServer(), "banned.city");
		p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.retinue." + t.key() + ".banished", king.getDisplayName(), f.name(), BAN_TICKS / 1200,
			Purists.name(Heritage.of(p)))
			.withStyle(ChatFormatting.DARK_RED));
		Speech.say(level, king, Component.translatable(speech(t, "seize")), Speech.RED, SPEECH_RANGE);
		level.playSound(null, king.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 0.8F);
	}

	/**
	 * (0.7.67) {@code p} is banned from {@code f}'s city for at least {@code ticks}, and its guards see them out (an exiled
	 * vigilante, {@link Identities}). Only a realm with a ruler of its own runs its guards this way ({@link Tempers}).
	 */
	static void exile(ServerLevel level, Faction f, ServerPlayer p, long ticks) {
		long now = level.getGameTime();
		Watch w = WATCHED.computeIfAbsent(key(f.id(), p.getUUID()), k -> new Watch());
		w.bannedUntil = Math.max(w.bannedUntil, now + ticks);
		w.threatened = now;
		w.touched = now;
		w.exiled = true;
		if (!trusted(level, f, p) && inCity(f, p)) {
			send(level, f, p);
		}
	}

	/** Sends up to {@link #HARASSERS} of the realm's town guards after {@code p} (the nearest, not at his back). */
	private static void send(ServerLevel level, Faction f, ServerPlayer p) {
		int already = 0;
		for (UUID target : SENT.values()) {
			if (target.equals(p.getUUID())) {
				already++;
			}
		}
		if (already >= HARASSERS) {
			return;
		}
		// (0.7.175) its MCA guards as well as its folk ones
		List<Worker> guards = new java.util.ArrayList<>(Workers.near(level, p.getBoundingBox().inflate(CALL_RANGE),
			g -> g.isAlive() && g.factionId() == f.id() && g.role() == Folk.GUARD && Royals.escortOf(g) == null && Rulers.target(g.mob()) == null
				&& !Arrests.busy(g.mob()) && !SENT.containsKey(g.getUUID())));
		guards.sort((a, b) -> Double.compare(a.mob().distanceToSqr(p), b.mob().distanceToSqr(p)));
		for (Worker g : guards) {
			if (already >= HARASSERS) {
				break;
			}
			SENT.put(g.getUUID(), p.getUUID());
			SENT_BY.put(g.getUUID(), f.id());
			already++;
		}
	}

	/** The realm's nearest guard to {@code p} not in a fight (folk, or (0.7.175) MCA). */
	private static @Nullable PathfinderMob nearestGuard(ServerLevel level, Faction f, ServerPlayer p, double range) {
		PathfinderMob best = null;
		for (Worker w : Workers.near(level, p.getBoundingBox().inflate(range),
			g -> g.isAlive() && g.factionId() == f.id() && g.role() == Folk.GUARD && Rulers.target(g.mob()) == null)) {
			PathfinderMob g = w.mob();
			if (best == null || g.distanceToSqr(p) < best.distanceToSqr(p)) {
				best = g;
			}
		}
		return best;
	}

	/** {@code p} is shoved along {@code away} (flat), and lifted {@code up}, by {@code guard} if one's at hand. */
	private static void shove(ServerLevel level, @Nullable PathfinderMob guard, ServerPlayer p, Vec3 away, double strength, double up) {
		Vec3 flat = away.multiply(1.0, 0.0, 1.0);
		if (flat.lengthSqr() < 1.0E-4) {
			flat = new Vec3(1.0, 0.0, 0.0);
		}
		flat = flat.normalize();
		if (guard != null) {
			guard.getLookControl().setLookAt(p, 30.0F, 30.0F);
			guard.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			// (0.7.254) he'll remember it, and so will you, when you're introduced ({@link SharedHistory})
			Recollections.remember(guard, p, Recollections.Kind.SHOVED_YOU, Component.empty());
		}
		p.push(flat.x * strength, up, flat.z * strength);
		p.syncVelocity = true;
		level.playSound(null, p.blockPosition(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.NEUTRAL, 0.7F, 1.0F);
	}

	/** (0.7.175) {@code guard} has been sent after a stranger ({@link #send}) who's still about. */
	static boolean sent(PathfinderMob guard) {
		return suspect(guard) != null;
	}

	private static @Nullable ServerPlayer suspect(PathfinderMob guard) {
		UUID id = SENT.get(guard.getUUID());
		if (id == null || !(guard.level() instanceof ServerLevel level)) {
			return null;
		}
		ServerPlayer p = level.getServer().getPlayerList().getPlayer(id);
		return p != null && p.level() == level && p.isAlive() ? p : null;
	}

	/**
	 * A town guard sent after a stranger ({@link #send}): he walks up to them and shoves them along, away from the
	 * king (watched) or away from the seat (driven out), with a word now and then. He never draws on them. A folk
	 * guard's goal ({@link Harass}); (0.7.175) an MCA guard's step ({@link Rulers#guardStep}).
	 */
	static void harass(PathfinderMob guard, long[] shoved) {
		ServerPlayer p = suspect(guard);
		Integer from = SENT_BY.get(guard.getUUID());
		if (p == null || from == null || !(guard.level() instanceof ServerLevel level)) {
			return;
		}
		Faction f = Factions.get(level.getServer(), from);
		Tempers.Temper t = Tempers.of(f);
		Watch w = WATCHED.get(key(from, p.getUUID()));
		if (f == null || t == null || w == null) {
			return;
		}
		long now = level.getGameTime();
		boolean out = w.banned(now);
		guard.getLookControl().setLookAt(p, 30.0F, 30.0F);
		double d = guard.distanceTo(p);
		if (d > REACH) {
			if (guard.getNavigation().isDone() || guard.tickCount % 10 == 0) {
				guard.getNavigation().moveTo(p, out ? DRIVE_SPEED : WALK_SPEED);
			}
			return;
		}
		guard.getNavigation().stop();
		if (now - shoved[0] < SHOVE_TICKS) {
			return;
		}
		shoved[0] = now;
		Vec3 away;
		if (out) {
			away = p.position().subtract(Vec3.atBottomCenterOf(f.seat()));
		} else {
			Entity king = f.leader().map(level::getEntity).orElse(null);
			away = p.position().subtract(king != null ? king.position() : guard.position());
		}
		shove(level, guard, p, away, out ? THROW : rules(t).shove(), 0.25);
		level.sendParticles(ParticleTypes.ANGRY_VILLAGER, guard.getX(), guard.getY() + 2.1, guard.getZ(), 1, 0.2, 0.1, 0.2, 0.0);
		if (now - w.threatened >= THREAT_TICKS) {
			w.threatened = now;
			String line = out ? "speech.jaymoskingdoms.retinue.out" + level.getRandom().nextInt(4) : speech(t, "watch" + level.getRandom().nextInt(4));
			Speech.say(level, guard, Component.translatable(line), Speech.RED, SPEECH_RANGE);
		}
	}

	/** An MCA guard's last shove (their step keeps no goal of its own). */
	private static final Map<UUID, long[]> SHOVED = new HashMap<>();

	static void harass(PathfinderMob guard) {
		harass(guard, SHOVED.computeIfAbsent(guard.getUUID(), id -> new long[] {Long.MIN_VALUE / 2}));
	}

	/** A folk guard's {@link #harass}. */
	static final class Harass extends Goal {
		private final Folk guard;
		private final long[] shoved = {Long.MIN_VALUE / 2};

		Harass(Folk guard) {
			this.guard = guard;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			// (0.7.184) or walking someone to his ruler ({@link Audiences})
			return this.guard.getTarget() == null && (Audiences.leading(this.guard) || this.guard.escort() == null && suspect(this.guard) != null);
		}

		@Override
		public boolean canContinueToUse() {
			return this.canUse();
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void stop() {
			this.guard.getNavigation().stop();
		}

		@Override
		public void tick() {
			if (!Audiences.lead(this.guard)) {
				harass(this.guard, this.shoved);
			}
		}
	}
}
