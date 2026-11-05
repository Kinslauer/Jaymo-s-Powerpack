package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.component.SwingAnimation;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.581) Andy: "the devout villagers even if you have high reputation will cast you out for blasphemy against their
 * patron, and the Truth's followers will cast out and are sworn to cleanse evil (vampires, werewolves, blood/ death
 * magicians, etc. any villager who learns you partake will switch on you instantly)".
 * <ul>
 * <li><b>Blasphemy:</b> a devout villager or townsman (a priest, or of the devout temperament) who sees a player break one
 * of their own patron's commandments ({@link Commandments#act}, the acts done in sight, {@link #UNSEEN} left out) cries
 * out and casts them out, and so do the devout of that patron in earshot. They won't trade or talk with them again, however
 * well liked they were, and a realm holding that patron ({@link RealmFaiths}) makes them an outlaw on the spot
 * ({@link Theft#castOut}).</li>
 * <li><b>Cleansing:</b> a follower of The Truth who learns a player partakes (sees a forbidden spell cast, or comes to
 * suspect a vampire or werewolf, {@link Suspicion}, and it is so) turns on them at once: guards take them for a foe, the
 * rest go at them with fists or whatever is in their hands, and the word goes to every Truth follower in earshot. A Truth
 * realm casts them out as well.</li>
 * </ul>
 * Both are kept with the villager, by the name they know the player by ({@link Recollections#key}, so a mask fools them).
 */
public final class CastOut {
	// ---- tuning ----
	static final double SEE_RANGE = 16.0, WORD_RANGE = 24.0, HUNT_RANGE = 24.0, REACH = 2.3, CLOSE = 8.0;
	static final float FIST = 2.0F, ARMED = 5.0F, VILLAGER_SPEED = 0.75F, FOLK_SPEED = 1.15F;
	static final long BLOW_TICKS = 20L, CRY_GAP = 240L;
	static final int EVERY = 10, LINES = 3;
	public static final String EVIL = "evil", BLASPHEMY = "blasphemy:";

	/** Acts nobody watching takes for blasphemy: where someone is, what they ate, their word, their talk, their luck. */
	static final Set<String> UNSEEN = Set.of("no_sleep", "dry", "sky", "nether", "dark_below", "dark_walk", "ocean", "sea_swim", "drowned",
		"careless_death", "undead_death", "outlawed", "caught", "searched", "toll", "introduce", "accuse", "lock_failed", "tool_broke", "fire_hurt",
		"invisible", "eat_raw", "eat_rot", "wooden_weapon", "rude", "broken_word", "bribe", "deep_ore", "turned", "fell");

	/** On a villager or folk: the players (by the id they know them by) they have cast out, and why ({@link #EVIL} or blasphemy against a patron). */
	static final AttachmentType<Map<UUID, String>> CAST = AttachmentRegistry.create(JaymosKingdoms.id("cast_out"),
		b -> b.persistent(Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.STRING)));

	private static final Map<UUID, Long> NEXT_BLOW = new HashMap<>(), CRIED = new HashMap<>();

	private CastOut() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(CastOut::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			NEXT_BLOW.clear();
			CRIED.clear();
		});
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			// (sneaking hands (a vampire's bite, a pickpocket's) are their own business)
			if (world.isClientSide() || !(player instanceof ServerPlayer sp) || sp.isShiftKeyDown() || !(entity instanceof LivingEntity who) || why(who, sp) == null) {
				return InteractionResult.PASS;
			}
			if (hand == InteractionHand.MAIN_HAND) {
				Speech.tell(sp.level(), who, sp, Component.translatable("speech.jaymoskingdoms.cast_out.refuse." + who.getRandom().nextInt(LINES)), Speech.RED);
				sp.level().playSound(null, who.getX(), who.getY(), who.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 0.9F);
			}
			return InteractionResult.FAIL;
		});
	}

	// ---- reading ---------------------------------------------------------------------------------------------------------------

	/** Why {@code who} has cast {@code player} out, or null if they haven't. */
	public static @Nullable String why(LivingEntity who, ServerPlayer player) {
		Map<UUID, String> cast = who.getAttached(CAST);
		return cast == null ? null : cast.get(Recollections.key(player, who));
	}

	/** A priest, or one of the devout temperament. */
	static boolean devout(LivingEntity e) {
		return Accusations.priest(e) || Temperaments.is(e, Temperaments.Temperament.DEVOUT);
	}

	/** {@code player} partakes in what The Truth cleanses: a vampire (or half one), a werewolf, a practitioner of death or blood magic. */
	static boolean partakes(ServerPlayer player) {
		return Vampires.isVampire(player) || Lycans.cursed(player) || ForbiddenArts.known(player);
	}

	private static void cast(LivingEntity who, ServerPlayer player, String why) {
		Map<UUID, String> cast = new HashMap<>(who.getAttachedOrElse(CAST, Map.of()));
		UUID key = Recollections.key(player, who);
		String was = cast.get(key);
		if (EVIL.equals(was) || why.equals(was)) {
			return;
		}
		cast.put(key, why);
		who.setAttached(CAST, Map.copyOf(cast));
	}

	private static boolean offends(Patron p, String act) {
		for (String[] law : Commandments.LAWS[p.ordinal()]) {
			for (String a : law) {
				if (a.equals(act)) {
					return true;
				}
			}
		}
		return false;
	}

	// ---- blasphemy -------------------------------------------------------------------------------------------------------------

	/** (From {@link Commandments#act}) {@code sp} did {@code act}: the devout of each patron it offends who saw it cast them out. */
	static void blasphemy(ServerPlayer sp, String act) {
		if (UNSEEN.contains(act) || Blessings.unseen(sp)) {
			return;
		}
		ServerLevel level = sp.level();
		for (Patron p : Patron.values()) {
			if (!offends(p, act)) {
				continue;
			}
			List<LivingEntity> saw = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(SEE_RANGE),
				x -> x.isAlive() && !x.isBaby() && !x.isSleeping() && x.distanceTo(sp) <= SEE_RANGE && Faiths.holds(x) && devout(x) && Faiths.of(x) == p
					&& x.hasLineOfSight(sp)));
			if (saw.isEmpty()) {
				continue;
			}
			saw.sort((a, b) -> Double.compare(a.distanceToSqr(sp), b.distanceToSqr(sp)));
			String why = BLASPHEMY + p.key;
			LivingEntity first = saw.getFirst();
			if (!why.equals(why(first, sp)) && !EVIL.equals(why(first, sp))) {
				Speech.say(level, first, Component.translatable("speech.jaymoskingdoms.blasphemy.cry." + level.getRandom().nextInt(LINES), Patrons.coloured(p)),
					Speech.RED, WORD_RANGE);
				level.playSound(null, first.getX(), first.getY(), first.getZ(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.2F, 0.8F);
			}
			for (LivingEntity e : saw) {
				cast(e, sp, why);
			}
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(WORD_RANGE),
				x -> x.isAlive() && Faiths.holds(x) && devout(x) && Faiths.of(x) == p)) {
				cast(e, sp, why);
			}
			realm(level, sp, first, p);
		}
	}

	/** Where {@code witness} stands, a realm that holds {@code p} casts {@code player} out. */
	private static void realm(ServerLevel level, ServerPlayer player, LivingEntity witness, Patron p) {
		Faction f = Theft.lawAt(level, witness.blockPosition());
		if (f != null && RealmFaiths.patron(level.getServer(), f) == p) {
			Theft.castOut(player, level, f);
		}
	}

	// ---- the Truth's cleansing -------------------------------------------------------------------------------------------------

	/** (From {@link ForbiddenArts#cast}) {@code player} cast death or blood magic: every Truth follower who saw it knows. */
	static void forbiddenSeen(ServerPlayer player, ServerLevel level) {
		if (player.isCreative() || player.isSpectator() || Blessings.unseen(player)) {
			return;
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SEE_RANGE),
			x -> x.isAlive() && !x.isSleeping() && x.distanceTo(player) <= SEE_RANGE && Commandments.truth(x) && x.hasLineOfSight(player))) {
			learned(e, player, true);
		}
	}

	/**
	 * {@code who} has come to know (or, {@code sure} not, suspects: from {@link Suspicion}) what {@code player} is. A Truth
	 * follower turns on them at once if it is so, cries it aloud, and every Truth follower in earshot hears it.
	 */
	static void learned(LivingEntity who, ServerPlayer player, boolean sure) {
		if (!Commandments.truth(who) || EVIL.equals(why(who, player)) || !sure && !partakes(player) || player.isCreative() || player.isSpectator()) {
			return;
		}
		ServerLevel level = player.level();
		cast(who, player, EVIL);
		if (!who.isBaby()) {
			Speech.say(level, who, Component.translatable("speech.jaymoskingdoms.cleanse.cry." + level.getRandom().nextInt(LINES)), Speech.RED, WORD_RANGE);
			CRIED.put(who.getUUID(), level.getGameTime());
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, who.getBoundingBox().inflate(WORD_RANGE),
			x -> x.isAlive() && x != who && Commandments.truth(x))) {
			cast(e, player, EVIL);
		}
		realm(level, player, who, Patron.TRUTH);
	}

	/** Every half second: those sworn to cleanse a player near them go at them. */
	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isCreative() || player.isSpectator()) {
				continue;
			}
			ServerLevel level = player.level();
			long now = level.getGameTime();
			for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(HUNT_RANGE),
				x -> x.isAlive() && !x.isBaby() && !x.isSleeping() && x.hasAttached(CAST))) {
				if (!EVIL.equals(why(m, player))) {
					continue;
				}
				double d = m.distanceTo(player);
				if (d > CLOSE && !m.hasLineOfSight(player)) {
					continue;
				}
				if (now - CRIED.getOrDefault(m.getUUID(), Long.MIN_VALUE / 2) >= CRY_GAP && m.getRandom().nextInt(4) == 0) {
					CRIED.put(m.getUUID(), now);
					Speech.say(level, m, Component.translatable("speech.jaymoskingdoms.cleanse.hunt." + m.getRandom().nextInt(LINES)), Speech.RED, WORD_RANGE);
				}
				if (m instanceof Folk folk && folk.combatant()) {
					if (folk.getTarget() == null || !folk.getTarget().isAlive()) {
						folk.setTarget(player);
					}
					continue;
				}
				m.getLookControl().setLookAt(player);
				if (d <= REACH) {
					if (now >= NEXT_BLOW.getOrDefault(m.getUUID(), 0L)) {
						NEXT_BLOW.put(m.getUUID(), now + BLOW_TICKS);
						m.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
						player.hurtServer(level, level.damageSources().mobAttack(m), m.getMainHandItem().isEmpty() ? FIST : ARMED);
					}
				} else if (m instanceof Villager v) {
					BehaviorUtils.setWalkAndLookTargetMemories(v, new EntityTracker(player, true), VILLAGER_SPEED, 1);
				} else {
					m.getNavigation().moveTo(player, FOLK_SPEED);
				}
			}
		}
	}
}
