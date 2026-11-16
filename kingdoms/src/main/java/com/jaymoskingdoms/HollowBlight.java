package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.643) The Hollow's blight: turning slowly, before dying. Andy (2026-11-07): "players that get infected and are turning
 * into hollow, shouldnt reincarnate the way everyone else does, if they die before fully turning, then they dont get a prompt
 * on death, just a longer black screen, an appropriate vignette, and a wakeup as a full turned hollow whichever they may be."
 * <p>
 * A blow from one of {@link Hollows}' turners (a zombie, a witch, a skeleton's arrow, a phantom, a necromancer, any other
 * undead; a vampire's bite is its own, {@link VampirePlayers#bitten}) that a player lives through may leave the blight in
 * them, of the turner's Hollow. It spreads over {@link #TURN_TICKS} (a Minecraft day): hunger first, then weakness and
 * sickness, each stage told once. Lived through, they turn where they stand. Died before that, Jaymo's Powers' death by
 * cause asks here first ({@code Mortality.MID_TURNS}): no come-to, no final death, a long black with the Hollow's colour at
 * its edges, and they rise the Hollow they were becoming. A golden apple burns it out while it's still spreading.
 * One who is Hollow already, an android, a vampire (or one bitten) or a werewolf can't take it.
 */
public final class HollowBlight {
	/** A turner's blow lands the blight at its {@link Hollows.Turn#chance} times this (a zombie: one blow in sixteen). */
	static final float BLIGHT_SHARE = 0.25F;
	/**
	 * Armour wards it off (Andy's pick from Contagion): the chance falls by the armour worn's share of {@link #FULL_ARMOUR}
	 * points times this, so full plate cuts it most of the way but never to nothing. Broken armour gives no points, so it
	 * wards nothing.
	 */
	static final float ARMOUR_WARD_MAX = 0.7F, FULL_ARMOUR = 30.0F;
	/** It gets in through the wound: a blow that leaves a part bleeding is this much likelier, one that doesn't this much. */
	static final float WOUND_TIMES = 2.0F, NO_WOUND_TIMES = 1.0F;
	/** A blight just taken settles in over this long (ticks); a treatment on the wound in that time clears it this often. */
	static final int SETTLE_TICKS = 20 * 120;
	static final float TEND_CLEARS = 0.75F;
	/** Between bouts of symptoms the body rests at least this long (ticks). */
	static final int REST_TICKS = 20 * 30;
	/** In the blight's last this-many ticks a bout can wither you a little (once), for this long. */
	static final long LAST_HOUR = 1000L;
	static final int WITHER_TICKS = 20 * 4;
	/** How long the blight takes to turn one (ticks of game time: a Minecraft day). */
	static final long TURN_TICKS = 24000L;
	/** The stages (shares of {@link #TURN_TICKS}) at which it shows: hunger, then weakness and sickness. */
	static final float STAGE_HUNGRY = 0.33F, STAGE_WEAK = 0.66F;
	/** Out of every this many seconds, a stage's symptom comes on (for this many ticks). */
	static final int SYMPTOM_EVERY = 45, SYMPTOM_TICKS = 20 * 8;

	/** How long the black screen lasts while passing out (ticks; 14 s). */
	static final int TURN_DARK = 20 * 14;

	/** Zombies within this many blocks stand down and speak instead of attacking. */
	static final double SPEAK_RANGE = 16.0;
	/** Out of every this many ticks, one of the nearest zombies speaks (standDownAndSpeak runs a few times a second). */
	static final int SPEAK_EVERY = 100;
	/** At most this many of the nearest zombies get a roll to speak each check. */
	static final int SPEAK_MAX = 2;
	/** (0.7.654) Their words to you (lines {@code hollow_blight.kin.0..}), in this colour, at most once every this many ticks. */
	static final int KIN_LINES = 10, KIN_COLOR = 0xFF9FB58A, KIN_GAP = 20 * 15;

	/** Until when each blighted player's body rests from its last bout (game time), and who has withered already. Not kept. */
	private static final Map<java.util.UUID, Long> REST = new java.util.HashMap<>();
	private static final java.util.Set<java.util.UUID> WITHERED = new java.util.HashSet<>();

	/** (0.7.654) When a zombie last spoke to each blighted player (game time). Not kept. */
	private static final Map<java.util.UUID, Long> SPOKE = new java.util.HashMap<>();

	/** The vignette each Hollow's turning casts over the black (RGB): the pallid one is {@link #PALLID_TINT}. */
	static final int ZOMBIE_TINT = 0x5C8A24, SKELETON_TINT = 0xB8B094, WRAITH_TINT = 0x5A6EA8, REAPER_TINT = 0x3C8C82, PALLID_TINT = 0x8A8A8A;

	/** A blight in someone: the Hollow it's making (a variant key, or "pallid"), its wake line, when it took,
	 * whether they have passed out waiting to rise turned, and the game time they should wake at. Persistent: logging out while
	 * passed out keeps the pending turn. */
	record Blight(String look, String line, long since, int told, boolean passedOut, long wakeAt, long settles) {
		static final Codec<Blight> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("look").forGetter(Blight::look),
			Codec.STRING.fieldOf("line").forGetter(Blight::line),
			Codec.LONG.fieldOf("since").forGetter(Blight::since),
			Codec.INT.optionalFieldOf("told", 0).forGetter(Blight::told),
			Codec.BOOL.optionalFieldOf("passedOut", false).forGetter(Blight::passedOut),
			Codec.LONG.optionalFieldOf("wakeAt", 0L).forGetter(Blight::wakeAt),
			Codec.LONG.optionalFieldOf("settles", 0L).forGetter(Blight::settles)).apply(i, Blight::new));

		HeritageVariants.@Nullable Variant variant() {
			return HeritageVariants.Variant.byKey(this.look);
		}

		Hollows.Turn turn() {
			return new Hollows.Turn(variant(), 1.0F, this.line);
		}

		/** Whether it's still settling in at {@code now} (a treatment on the wound may yet clear it). */
		boolean settling(long now) {
			return now < this.settles;
		}

		Blight stage(int stage) {
			return new Blight(this.look, this.line, this.since, stage, false, 0L, this.settles);
		}
	}

	static final AttachmentType<Blight> BLIGHT = AttachmentRegistry.create(JaymosKingdoms.id("hollow_blight"),
		builder -> builder.persistent(Blight.CODEC));

	private HollowBlight() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && taken > 0.0F && !blocked) {
				struck(player, source);
			}
		});
		BlightSigns.init();
		BlightCures.register();
		BlightSpread.register();
		BlightQuarantine.register();
		KeepingCells.register();
		ServerTickEvents.END_SERVER_TICK.register(HollowBlight::tick);
		PowersCompat.tellMidTurns(HollowBlight::midTurn);
		PowersCompat.<net.minecraft.world.entity.LivingEntity>listen("com.jaymospowers.core.wounds.Treatment", "ANY_TENDED_LISTENERS",
			(by, body) -> tended(body));
	}

	/** Whether {@code player} carries the blight. */
	public static boolean blighted(net.minecraft.world.entity.LivingEntity body) {
		return body.hasAttached(BLIGHT);
	}

	/** Hearts a villager loses with one they hear cough the blight's cough. */
	static final int COUGH_HEARTS = 2;

	/**
	 * {@code by}, who remarked on {@code cougher}'s cough (Andy: "This happening is why people avoid those that cough"), backs
	 * off and thinks a little less of a player they heard.
	 */
	static void heardCough(net.minecraft.world.entity.LivingEntity by, net.minecraft.world.entity.LivingEntity cougher) {
		HollowSighting.flee(by, cougher);
		if (cougher instanceof ServerPlayer player && by instanceof net.minecraft.world.entity.npc.villager.Villager) {
			McaCompat.hearts(by, player, -COUGH_HEARTS);
		}
	}

	static boolean immune(ServerPlayer player) {
		return player.isCreative() || player.isSpectator() || Heritage.of(player) == Heritage.Kind.HOLLOW || PowersCompat.android(player)
			|| Vampires.isVampire(player) || player.hasAttached(Vampires.BITTEN) || Lycans.cursed(player) || SkeletonCurse.cursed(player)
			|| BlightCures.proof(player);
	}

	/** A skeleton's blight, taken before only the Skeleton Lord's curse made skeletons ({@link SkeletonCurse}): it fades. */
	private static boolean stale(Blight blight) {
		return blight.variant() == HeritageVariants.Variant.SKELETON;
	}

	/** A blow on {@code player} they lived through: one of the turners' may leave the blight. */
	private static void struck(ServerPlayer player, DamageSource source) {
		Entity by = source.getEntity();
		if (by == null || by == player || !player.isAlive() || player.isDeadOrDying() || blighted(player) || Vampires.isVampire(by)
			|| immune(player)) {
			return;
		}
		Hollows.Turn turn = Hollows.turnOf(by);
		if (turn == null) {
			return;
		}
		float chance = turn.chance() * BLIGHT_SHARE * (1.0F - ward(player)) * (PowersCompat.cuts(player) > 0 ? WOUND_TIMES : NO_WOUND_TIMES);
		if (player.getRandom().nextFloat() >= chance) {
			return;
		}
		take(player, turn, by);
	}

	/** How much of a turner's blow {@code body}'s armour wards off (0 to {@link #ARMOUR_WARD_MAX}). */
	static float ward(net.minecraft.world.entity.LivingEntity body) {
		return Math.min(1.0F, body.getArmorValue() / FULL_ARMOUR) * ARMOUR_WARD_MAX;
	}

	/**
	 * The blight gets into {@code player}, the {@code turn}'s Hollow. It settles in first ({@link #SETTLE_TICKS}). Andy's pick:
	 * only one who knows wounds (a Medicine perk) feels it go in; anyone else finds out when the hunger starts.
	 */
	static void take(ServerPlayer player, Hollows.Turn turn, @Nullable Entity by) {
		String look = turn.look() == null ? "pallid" : turn.look().key();
		long now = player.level().getGameTime();
		player.setAttached(BLIGHT, new Blight(look, turn.line(), now, 0, false, 0L, now + SETTLE_TICKS));
		if (BlightSigns.knowsWounds(player)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.hollow_blight.took").withStyle(ChatFormatting.DARK_GRAY,
				ChatFormatting.ITALIC));
		}
		JaymosKingdoms.LOGGER.info("{} took the Hollow's blight ({}) from {}", player.getScoreboardName(), look,
			by == null ? "nothing living" : by.getType().getDescriptionId());
	}

	/** Raw rotten flesh eaten by {@code player} gave them the blight ({@link BlightCures#ate}): the zombie Hollow's. */
	static void fromFlesh(ServerPlayer player) {
		if (blighted(player) || immune(player)) {
			return;
		}
		take(player, new Hollows.Turn(HeritageVariants.Variant.ZOMBIE, 1.0F, "zombie"), null);
	}

	/** Gilded flesh eaten: the blight in {@code player} goes back {@code share} of its course (never past its start). */
	static void setBack(ServerPlayer player, float share) {
		Blight blight = player.getAttached(BLIGHT);
		if (blight == null || blight.passedOut()) {
			return;
		}
		long now = player.level().getGameTime();
		long since = Math.min(now, blight.since() + (long) (TURN_TICKS * share));
		float done = (now - since) / (float) TURN_TICKS;
		int stage = done >= STAGE_WEAK ? 2 : done >= STAGE_HUNGRY ? 1 : 0;
		player.setAttached(BLIGHT, new Blight(blight.look(), blight.line(), since, Math.min(stage, blight.told()), false, 0L, blight.settles()));
		WITHERED.remove(player.getUUID());
	}

	/**
	 * A cure (the Hollow's Cure, a golden apple while weakened, a healer, a totem): the blight in {@code body} is burned out,
	 * and (Andy: "You gain immunity forever after a cure") it can never take them again. True if there was one to cure.
	 */
	static boolean cure(net.minecraft.world.entity.LivingEntity body) {
		if (!blighted(body) || body.getAttached(BLIGHT).passedOut()) {
			return false;
		}
		BlightCures.proofAgainst(body);
		if (body instanceof ServerPlayer player) {
			clear(player);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.hollow_blight.cured").withStyle(ChatFormatting.GOLD));
		} else {
			body.removeAttached(BLIGHT);
		}
		body.level().playSound(null, body.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.6F, 1.2F);
		return true;
	}

	/** A treatment finished on {@code body}: a blight still settling in is cleared, most times. */
	private static void tended(net.minecraft.world.entity.LivingEntity body) {
		if (!(body instanceof ServerPlayer player)) {
			return;
		}
		Blight blight = player.getAttached(BLIGHT);
		if (blight != null && !blight.passedOut() && blight.settling(player.level().getGameTime()) && player.getRandom().nextFloat() < TEND_CLEARS) {
			clear(player);
			JaymosKingdoms.LOGGER.info("{} had a wound tended in time: the Hollow's blight never settled", player.getScoreboardName());
		}
	}

	/** Takes the blight out of {@code player} (cured, turned or faded), and its signs with it. */
	static void clear(ServerPlayer player) {
		player.removeAttached(BLIGHT);
		BlightSigns.clear(player);
		REST.remove(player.getUUID());
		WITHERED.remove(player.getUUID());
		BlightQuarantine.cleared(player);
	}

	private static void tick(MinecraftServer server) {
		long tick = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((tick + player.getId()) % 20 != 0 || !player.isAlive()) {
				continue;
			}
			Blight blight = player.getAttached(BLIGHT);
			if (blight == null) {
				continue;
			}

			// Passed out waiting to rise turned: wake at wakeAt and rise.
			if (blight.passedOut()) {
				if (player.level().getGameTime() >= blight.wakeAt()) {
					if (!stale(blight)) {
						Hollows.turn(player, blight.turn());
					}
					clear(player);
				}
				continue;
			}

			if (immune(player) && !player.isCreative() && !player.isSpectator() || stale(blight)) {
				// turned some other way meanwhile (or already Hollow): nothing left for it to make
				clear(player);
				continue;
			}
			if (PowersCompat.waking(player)) {
				continue; // (left for dead: Powers finishes it as they rise)
			}
			float done = (player.level().getGameTime() - blight.since()) / (float) TURN_TICKS;
			if (done >= 1.0F) {
				turnNow(player, blight);
				continue;
			}
			int stage = done >= STAGE_WEAK ? 2 : done >= STAGE_HUNGRY ? 1 : 0;
			if (stage > blight.told()) {
				blight = blight.stage(stage);
				player.setAttached(BLIGHT, blight);
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.hollow_blight.stage." + stage)
					.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			}
			symptoms(player, stage, done);
			BlightSigns.show(player, blight, stage);
			// While the blight spreads, zombies don't attack you: they stand down and speak instead,
			// for the whole blight from stage 0; their target choice is hooked so they don't re-target you.
			standDownAndSpeak(player);
		}
	}

	/** While the blight spreads, nearby zombies stand down and speak to you instead of attacking.
	 * Runs every check for the whole blight, from stage 0. Targets on you are cleared so they stop
	 * attacking, and ZombieBlightMixin hooks their target choice so they don't re-target you next tick.
	 * One or two of the nearest speak with ambient zombie sounds now and then. */
	private static void standDownAndSpeak(ServerPlayer player) {
		Level level = player.level();
		List<Zombie> zombies = level.getEntitiesOfClass(Zombie.class,
				player.getBoundingBox().inflate(SPEAK_RANGE), z -> z.isAlive());
		zombies.sort(Comparator.comparingDouble(z -> z.distanceToSqr(player)));
		int speaking = 0;
		for (Zombie z : zombies) {
			if (z.getTarget() == player) {
				z.setTarget(null);
			}
			if (speaking < SPEAK_MAX && player.getRandom().nextInt(SPEAK_EVERY) == 0) {
				level.playSound(null, z.blockPosition(), SoundEvents.ZOMBIE_AMBIENT, SoundSource.HOSTILE, 0.6F, 1.0F);
				speaking++;
				// (0.7.654) Andy: "you should be spoken to by the zombies". The groan comes with words over its head, to you,
				// now and then (a speech bubble, no chat), while it stops and looks at you
				long now = level.getGameTime();
				Long last = SPOKE.get(player.getUUID());
				if ((last == null || now - last >= KIN_GAP || now < last) && z.hasLineOfSight(player)) {
					SPOKE.put(player.getUUID(), now);
					z.getNavigation().stop();
					z.getLookControl().setLookAt(player, 30.0F, 30.0F);
					Speech.sayTo(player.level(), z, player, Component.translatable("message.jaymoskingdoms.hollow_blight.kin."
						+ player.getRandom().nextInt(KIN_LINES), player.getDisplayName()), KIN_COLOR);
				}
			}
		}
	}

	/**
	 * A stage's symptoms, now and then, with the body resting between bouts (Andy's pick from Contagion): hunger or slowness
	 * once it's taken hold; weakness and nausea too near the end; and in its last hour one bout withers them a little.
	 */
	private static void symptoms(ServerPlayer player, int stage, float done) {
		long now = player.level().getGameTime();
		Long rest = REST.get(player.getUUID());
		if (stage == 0 || rest != null && now < rest || player.getRandom().nextInt(SYMPTOM_EVERY) != 0) {
			return;
		}
		REST.put(player.getUUID(), now + SYMPTOM_TICKS + REST_TICKS);
		player.addEffect(new MobEffectInstance(player.getRandom().nextBoolean() ? MobEffects.HUNGER : MobEffects.SLOWNESS, SYMPTOM_TICKS, 0, true, false,
			true));
		if (stage > 1) {
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SYMPTOM_TICKS, 0, true, false, true));
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, SYMPTOM_TICKS / 2, 0, true, false, true));
			if ((1.0F - done) * TURN_TICKS <= LAST_HOUR && WITHERED.add(player.getUUID())) {
				player.addEffect(new MobEffectInstance(MobEffects.WITHER, WITHER_TICKS, 0, true, false, true));
			}
		}
	}

	/** The blight has run its course while they lived: they pass out first, then rise turned. */
	private static void turnNow(ServerPlayer player, Blight blight) {
		// a totem held: it's spent instead, and that is a cure (Andy's pick)
		for (net.minecraft.world.InteractionHand hand : net.minecraft.world.InteractionHand.values()) {
			net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
			if (held.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)) {
				held.shrink(1);
				player.level().broadcastEntityEvent(player, (byte) 35);
				cure(player);
				return;
			}
		}
		player.setAttached(BLIGHT, new Blight(blight.look(), blight.line(), blight.since(), blight.told(), true,
				player.level().getGameTime() + TURN_DARK, 0L));
		BlightSigns.clear(player);
		BlightQuarantine.cleared(player);
		player.level().playSound(null, player.blockPosition(), SoundEvents.ZOMBIE_VILLAGER_CONVERTED, SoundSource.PLAYERS, 0.8F, 0.6F);
		// Pass out via Powers' downing (eyes closed, black screen) instead of a scoreboard tag: Powers owns
		// the downed state and wakes them itself. Kingdoms rises the turn at wakeAt, the same moment.
		// ((0.7.654) with the Hollow's colour at the edges of the black, as dying mid-turn has)
		PowersCompat.turnOut(player, tint(blight.variant()), TURN_DARK);
	}

	/** For Powers' death by cause: one dying with the blight in them rises the Hollow it was making ({@code Mortality.MID_TURNS}). */
	static Map.@Nullable Entry<Integer, Runnable> midTurn(ServerPlayer player) {
		Blight blight = player.getAttached(BLIGHT);
		if (blight == null || immune(player) || stale(blight)) {
			return null;
		}
		return Map.entry(tint(blight.variant()), () -> {
			clear(player);
			Hollows.turn(player, blight.turn());
		});
	}

	/** The colour of {@code look}'s turning, on the black. */
	static int tint(HeritageVariants.@Nullable Variant look) {
		if (look == null) {
			return PALLID_TINT;
		}
		return switch (look) {
			case ZOMBIE -> ZOMBIE_TINT;
			case SKELETON -> SKELETON_TINT;
			case WRAITH -> WRAITH_TINT;
			case REAPER -> REAPER_TINT;
			default -> PALLID_TINT;
		};
	}

	/** A golden apple eaten while the blight spreads burns it out -- but only while the player is Weakened
	 * (a drunk, splash or lingering weakness potion all apply it), like curing a zombie villager. */
	public static void ateGold(ServerPlayer player) {
		if (player.hasEffect(MobEffects.WEAKNESS)) {
			cure(player);
		}
	}
}
