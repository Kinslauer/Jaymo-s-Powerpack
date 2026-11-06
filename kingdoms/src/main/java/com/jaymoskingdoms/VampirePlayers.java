package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;

/**
 * (0.7.0) Stage 3 of the 0.10.0 drop: a player as a vampire. Bitten by one (and living through it), they turn at the
 * next nightfall (dying first cures it). Then:
 *
 * <ul>
 * <li>At night: strong (Strength), quick (Speed), seeing in the dark (Night Vision).</li>
 * <li>By day under the open sky: weak; round midday ({@link #NOON_FROM}-{@link #NOON_TO}) with nothing on their head,
 * they burn.</li>
 * <li>Blood: sneak and right-click a grown villager or a realm's folk with an empty hand to drink (a bite every
 * second): it feeds them, heals them, and the bitten turn too, if they live. Two nights without it and they're
 * starving (Hunger, Weakness) until they drink. Anyone of that realm who sees it cries out (a heavy crime); at night
 * they show themselves too, and the guards hunt them till dawn.</li>
 * <li>The cure, for the bitten or the turned (or a werewolf, {@link WerewolfPlayers}): a rare quest from a priest
 * ({@link Cures}).</li>
 * <li>(0.7.66) A golden apple eaten between the bite and the nightfall only half saves them: they wake a
 * {@link Vampires.Kind#DHAMPIR dhampir}, with none of a vampire's weaknesses (no sun, no starving, fire burns them no
 * worse) and none of its night strength, but its gifts ({@link NightGifts}). Whoever turns wakes in a crypt dug beneath
 * where they stood ({@link Lairs#crypt}), their bed a coffin.</li>
 * </ul>
 */
public final class VampirePlayers {
	/** At night: Strength and Speed levels (amplifiers). */
	static final int NIGHT_STRENGTH = 0, NIGHT_SPEED = 0;
	/** Midday, when the sun burns a bare head (time of day); how long it burns (seconds). */
	static final long NOON_FROM = 3000L, NOON_TO = 9000L;
	static final float BURN_SECONDS = 3.0F;
	/** A bite: what it takes, what it gives (food, saturation, health), how often (ticks). */
	static final float DRINK_DAMAGE = 4.0F, DRINK_HEAL = 3.0F, DRINK_SATURATION = 1.5F;
	static final int DRINK_FOOD = 4, DRINK_EVERY = 20;
	/** (0.7.628) Lapping a pool on the ground: how near the spot, the most a gulp takes (stain size), what each unit gives. */
	static final double LAP_RANGE = 1.5;
	static final float LAP_MOST = 0.5F, LAP_HEAL = 3.0F, LAP_FOOD = 4.0F;
	/** Nights without blood before they starve. */
	static final int STARVE_NIGHTS = 2;

	private static final Map<UUID, Long> LAST_DRINK = new HashMap<>();

	/** (0.7.66) On the bitten who ate a golden apple before they turned: they'll wake a dhampir. */
	static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Boolean> HALF_TURN = net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.create(
		JaymosKingdoms.id("vampire_half_turn"), builder -> builder.persistent(com.mojang.serialization.Codec.BOOL));

	/** (0.7.625) Our use phase, and Powers' first one (its revive hold and downed handling), which ours runs ahead of. */
	private static final net.minecraft.resources.Identifier FIRST = JaymosKingdoms.id("vampire_first"),
		POWERS_DOWNED_FIRST = net.minecraft.resources.Identifier.fromNamespaceAndPath("jaymospowers", "downed_first");

	private VampirePlayers() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(VampirePlayers::tick);
		// (0.7.625) ahead of Powers' downed handling (its revive hold and its sneak lift): holding someone by the throat, the
		// use held on them is the grab's alone, never a revive filling up; and a vampire's sneak+use on a downed person or
		// piglin is the bite, not the lift
		UseEntityCallback.EVENT.addPhaseOrdering(FIRST, POWERS_DOWNED_FIRST);
		UseEntityCallback.EVENT.addPhaseOrdering(FIRST, net.fabricmc.fabric.api.event.Event.DEFAULT_PHASE);
		UseEntityCallback.EVENT.register(FIRST, (player, level, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer sp) || !(entity instanceof LivingEntity target)) {
				return InteractionResult.PASS;
			}
			if (BloodGrab.holding(sp)) {
				return InteractionResult.SUCCESS;
			}
			if (hand == InteractionHand.MAIN_HAND && level instanceof ServerLevel server && Downed.isDowned(target) && Vampires.isVampire(sp)
				&& sp.isShiftKeyDown() && sp.getMainHandItem().isEmpty() && !(target instanceof Player)
				&& (Vampires.person(target) || Vampires.netherPrey(target))) {
				drink(server, sp, target, false, Vampires.person(target));
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND
				|| !(entity instanceof LivingEntity target)) {
				return InteractionResult.PASS;
			}
			// (0.7.615) holding someone up by the throat (BloodGrab), use does nothing else
			if (BloodGrab.holding(sp)) {
				return InteractionResult.SUCCESS;
			}
			if (Vampires.isVampire(sp) && sp.isShiftKeyDown() && sp.getMainHandItem().isEmpty() && Vampires.person(target)) {
				drink(server, sp, target);
				return InteractionResult.SUCCESS;
			}
			// (0.7.615) in the Nether: piglins and Hornkin, never zombified ones; they never turn
			if (Vampires.isVampire(sp) && sp.isShiftKeyDown() && sp.getMainHandItem().isEmpty() && Vampires.netherPrey(target)) {
				drink(server, sp, target, false, false);
				return InteractionResult.SUCCESS;
			}
			// (0.7.332) sleeping prey: a mortal player asleep in a bed ({@link SleepingPrey})
			if (Vampires.isVampire(sp) && sp.isShiftKeyDown() && sp.getMainHandItem().isEmpty() && target != sp && SleepingPrey.prey(target)) {
				SleepingPrey.bite(server, sp, (ServerPlayer) target);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		// (0.7.628) sneak and right-click a pool of blood on the ground with an empty hand: lap it up
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level instanceof ServerLevel server && player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND && Vampires.isVampire(sp)
				&& sp.isShiftKeyDown() && sp.getMainHandItem().isEmpty() && lap(server, sp, hit.getLocation())) {
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			LAST_DRINK.clear();
			LAPPED.clear();
		});
	}

	/**
	 * (0.7.628) Andy: "blood should pool under the downed and corpses until they bleed out, that way blood sorcerers and
	 * vampires have finite resources". A vampire laps the spilt blood off the ground at {@code at} (Jaymo's Powers' stains,
	 * the pools the downed and the dead bleed out into): up to {@link #LAP_MOST} a gulp, once a second, a thin meal next to
	 * a bite ({@link #LAP_FOOD}, {@link #LAP_HEAL} for each unit of stain, a man's whole pool about three), but it counts as
	 * fed and eases the fever. What's lapped up is gone: a body's blood runs out. True if there was any to take.
	 */
	/** (0.7.628) When each vampire last lapped from the ground (game time), so the pose holds between gulps but not after a bite. */
	private static final Map<UUID, Long> LAPPED = new HashMap<>();

	private static boolean lapping(ServerPlayer player, long now) {
		Long at = LAPPED.get(player.getUUID());
		return at != null && now - at < DRINK_EVERY;
	}

	static boolean lap(ServerLevel level, ServerPlayer player, net.minecraft.world.phys.Vec3 at) {
		long now = level.getGameTime();
		Long last = LAST_DRINK.get(player.getUUID());
		if (last != null && now - last < DRINK_EVERY) {
			// (between gulps (or a bite): still drinking, so a held button opens nothing under them)
			if (lapping(player, now)) {
				PowersCompat.drinkAtGround(player);
			}
			return true;
		}
		float drawn = PowersCompat.drawStains(level, at, LAP_RANGE, LAP_MOST, LAP_MOST, from -> Hemomancy.rise(level, from, player.getEyePosition()));
		if (drawn <= 0.0F) {
			return false;
		}
		LAST_DRINK.put(player.getUUID(), now);
		LAPPED.put(player.getUUID(), now);
		// (0.7.628) Andy: "is this animated?": down on hands and knees, mouth to the ground, bobbing with each gulp
		PowersCompat.drinkAtGround(player);
		player.heal(LAP_HEAL * drawn);
		player.getFoodData().eat(Math.max(1, Math.round(LAP_FOOD * drawn)), 0.4F);
		fedFromBottle(player);
		BloodFever.bit(player);
		level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.7F, 0.45F);
		return true;
	}

	static boolean vampire(Player player) {
		return Vampires.isVampire(player);
	}

	/** Gone {@link #STARVE_NIGHTS} nights without blood (a Nightborn: {@link NightbornBlood#STARVE_NIGHTS}; also read on the client, for blood vision). */
	public static boolean starving(Player player) {
		Vampires.Vampire v = player.getAttached(Vampires.VAMPIRE);
		return v != null && v.type() != Vampires.Kind.DHAMPIR
			&& Vampires.nightIndex(player.level().getOverworldClockTime()) - v.fedNight() >= NightbornBlood.starveNights(player);
	}

	/** A vampire's bite on {@code player}: they'll turn at the next nightfall (unless they die first, or carry the wolf's curse). */
	static void bitten(ServerPlayer player) {
		if (!Vampires.isVampire(player) && !Lycans.cursed(player) && !player.hasAttached(Vampires.BITTEN)) {
			player.setAttached(Vampires.BITTEN, player.level().getOverworldClockTime());
		}
	}

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long clock = overworld.getOverworldClockTime();
		boolean night = Vampires.night(clock);
		long nightNo = Vampires.nightIndex(clock);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((overworld.getGameTime() + player.getId()) % 20 != 0 || !player.isAlive() || player.isSpectator()) {
				continue;
			}
			Long bitten = player.getAttached(Vampires.BITTEN);
			if (bitten != null && night && (nightIndex(bitten) < nightNo || NightbornBlood.oldBlood(player, bitten, clock))) {
				turn(player, nightNo);
			} else if (bitten != null && night) {
				NightbornBlood.fever(player, bitten, clock);
			}
			Vampires.Vampire v = player.getAttached(Vampires.VAMPIRE);
			if (v == null || player.isCreative()) {
				continue;
			}
			if (v.fedAt() == 0L) {
				// (0.7.332) never fed yet: the pallor ({@link Vampires#pallor}) counts from now
				v = new Vampires.Vampire(v.kind(), v.fedNight(), overworld.getGameTime());
				player.setAttached(Vampires.VAMPIRE, v);
			}
			if (v.type() == Vampires.Kind.DHAMPIR) {
				if (night) {
					player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, true));
					if (NightbornBlood.nightborn(player)) {
						// (0.7.88) born dhampir: the night's strength too
						player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, NIGHT_STRENGTH, true, false, true));
					}
				}
				continue;
			}
			if (night) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, true));
				// (0.7.185) bloodbound ({@link BloodFever}): the night's strength a level higher
				player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 40, NIGHT_STRENGTH + (BloodFever.bloodbound(player) ? 1 : 0), true, false, true));
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, 40, NIGHT_SPEED, true, false, true));
			} else if (sunlit(player)) {
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, true, false, true));
				long t = Math.floorMod(clock, 24000L);
				if (t >= NOON_FROM && t < NOON_TO && player.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
					player.igniteForSeconds(BURN_SECONDS);
				}
			}
			if (nightNo - v.fedNight() >= NightbornBlood.starveNights(player)) {
				player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 40, 0, true, false, true));
				player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0, true, false, true));
			}
		}
	}

	private static long nightIndex(long clock) {
		return Vampires.nightIndex(clock);
	}

	/** (0.7.332) A dhampir (half a vampire: none of the weaknesses, none of the tells); on either side. */
	public static boolean dhampir(net.minecraft.world.entity.Entity e) {
		Vampires.Vampire v = e.getAttached(Vampires.VAMPIRE);
		return v != null && v.type() == Vampires.Kind.DHAMPIR;
	}

	static boolean sunlit(ServerPlayer player) {
		ServerLevel level = player.level();
		return !level.isRainingAt(player.blockPosition()) && level.canSeeSky(BlockPos.containing(player.getEyePosition()));
	}

	/** They wake changed (vaguely told: they find out the rest), in a crypt where there's room for one. */
	private static void turn(ServerPlayer player, long nightNo) {
		Commandments.act(player, "turned");
		player.removeAttached(Vampires.BITTEN);
		boolean half = Boolean.TRUE.equals(player.removeAttached(HALF_TURN));
		Vampires.Kind kind = half ? Vampires.Kind.DHAMPIR : Vampires.Kind.PLAYER;
		player.setAttached(Vampires.VAMPIRE, new Vampires.Vampire(kind.name().toLowerCase(java.util.Locale.ROOT), nightNo - 1, player.level().getGameTime()));
		BloodThralls.release(player); // (0.7.332) a thrall who turns is no one's thrall
		player.sendSystemMessage(Component.translatable(half ? "message.jaymoskingdoms.vampire.turned_half" : "message.jaymoskingdoms.vampire.turned")
			.withStyle(ChatFormatting.DARK_RED));
		player.level().playSound(null, player.blockPosition(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 0.6F, 0.5F);
		NightbornBlood.woke(player, half);
		if (!player.isPassenger() && Lairs.crypt(player.level(), player)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.vampire.crypt").withStyle(ChatFormatting.DARK_RED));
		}
	}

	/** (0.7.66) A golden apple, eaten by the bitten before they turn: they'll only half turn. */
	public static void ateGold(ServerPlayer player) {
		if (player.hasAttached(Vampires.BITTEN) && !player.hasAttached(HALF_TURN)) {
			player.setAttached(HALF_TURN, true);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.vampire.gold").withStyle(ChatFormatting.GOLD));
		}
	}

	/**
	 * (0.7.615) A bottle of Kitchen's blood ({@link Hemomancy#drankBottle}): a thin meal, but it counts as fed, so a vampire
	 * stranded where no one can be bitten (the Nether) doesn't starve on it. Blood requests and the like still want a bite.
	 */
	static void fedFromBottle(ServerPlayer player) {
		Vampires.Vampire v = player.getAttached(Vampires.VAMPIRE);
		if (v != null) {
			long now = player.level().getGameTime();
			player.setAttached(Vampires.VAMPIRE, new Vampires.Vampire(v.kind(), Vampires.nightIndex(((ServerLevel) player.level()).getOverworldClockTime()), now));
		}
	}

	/** (0.7.615) A gulp in the grab-drink ({@link BloodGrab}): the bite, on the grab's own quicker pace instead of the bite's. */
	static void drinkNow(ServerLevel level, ServerPlayer player, LivingEntity target, boolean infect) {
		LAST_DRINK.remove(player.getUUID());
		drink(level, player, target, false, infect);
	}

	/** A bite from a player who's one of them. */
	static void drink(ServerLevel level, ServerPlayer player, LivingEntity target) {
		drink(level, player, target, false, true);
	}

	/**
	 * A bite. {@code quiet}: (0.7.332) on a sleeper, the first bite takes its blood without a blow and without waking them
	 * ({@link SleepingPrey}); {@code infect}: whether a survivor turns at the next nightfall.
	 */
	static void drink(ServerLevel level, ServerPlayer player, LivingEntity target, boolean quiet, boolean infect) {
		long now = level.getGameTime();
		Long last = LAST_DRINK.get(player.getUUID());
		if (last != null && now - last < DRINK_EVERY) {
			return;
		}
		LAST_DRINK.put(player.getUUID(), now);
		long clock = level.getOverworldClockTime();
		long nightNo = Vampires.nightIndex(clock);
		if (quiet) {
			// (0.7.332) no blow, so no waking: the blood is taken, never to the last heart
			target.setHealth(Math.max(SleepingPrey.QUIET_FLOOR, target.getHealth() - DRINK_DAMAGE));
		} else {
			if (target.isSleeping()) {
				target.stopSleeping();
			}
			target.hurtServer(level, level.damageSources().playerAttack(player), DRINK_DAMAGE);
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 3, false, false, false));
		}
		player.heal(DRINK_HEAL);
		player.getFoodData().eat(DRINK_FOOD, DRINK_SATURATION);
		Vampires.Vampire v = player.getAttached(Vampires.VAMPIRE);
		if (v != null) {
			player.setAttached(Vampires.VAMPIRE, new Vampires.Vampire(v.kind(), nightNo, now));
		}
		level.sendParticles(PowersCompat.blood(0x8A0303, 1.0F), target.getX(), target.getEyeY() - 0.25, target.getZ(), 8, 0.15, 0.15, 0.15, 0.0);
		level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.PLAYERS, 0.9F, 0.55F);
		// (0.7.615) a bite that doesn't infect (a sleeper, the Nether's piglins and Hornkin) never turns its prey
		if (infect && target.isAlive() && target instanceof PathfinderMob p && !Lycans.cursed(p) && !p.hasAttached(Vampires.BITTEN)) {
			Vampires.bite(p, clock);
		} else if (infect && target.isAlive() && target instanceof ServerPlayer bitten) {
			// (0.7.185) a ravenous fever's player prey ({@link BloodFever})
			bitten(bitten);
		}
		// (0.7.332) a bite on their thrall heals twice over and eases the fever more; a mortal player bitten counts toward binding them
		boolean thrall = BloodThralls.isThrall(player, target);
		if (target instanceof ServerPlayer victim && target.isAlive()) {
			BloodThralls.drank(player, victim, nightNo);
		}
		BloodRequests.fed(player, target);
		Commandments.act(player, "blood");
		VampireMask.fed(player);
		// (0.7.332) villagers who saw it suspect them by name ({@link Suspicion}); a player bitten remembers the biter ({@link VictimMemory})
		Suspicion.witnessed(level, player, Suspicion.VAMPIRE, Suspicion.ADD_FEEDING, player.isShiftKeyDown() ? Suspicion.FEED_RANGE * Suspicion.SNEAK_SHARE : Suspicion.FEED_RANGE, false);
		if (target instanceof ServerPlayer victim && Vampires.isVampire(player)) {
			VictimMemory.bitten(victim, player);
		}
		// (0.7.185) every bite eases the blood fever
		BloodFever.bit(player, thrall);
		if (Vampires.night(clock)) {
			Vampires.expose(player, nightNo);
		}
		Faction f = Theft.lawAt(level, target.blockPosition());
		if (f != null) {
			Theft.commit(player, level, f, Theft.Crime.BLOODLETTING, target.blockPosition(), null, false);
		}
	}
}
