package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * (0.7.88) Where the Nightborn heritage ({@link Heritage}) meets the vampires ({@link Vampires}, {@link VampirePlayers},
 * {@link NightGifts}). The sun's rules stay as they are; everything else leans together:
 * <ul>
 * <li><b>Old Blood:</b> a Nightborn bitten at night turns after a short fever ({@link #OLD_BLOOD_TICKS}) instead of at the
 * next nightfall (bitten by day: at that dusk), and no vampire's bite can take them below {@link #OLD_BLOOD_FLOOR}
 * before they turn. They wake whole.</li>
 * <li><b>Long thirst:</b> a Nightborn vampire starves after {@link #STARVE_NIGHTS} nights unfed.</li>
 * <li><b>Kin-sense both ways:</b> a vampire's kin-sense picks up unturned Nightborn faintly. The Elder never hunts one:
 * at night he comes close and whispers an offer ({@link #court}); yes is a bite freely given, no (or walking away)
 * and he leaves them be for {@link #LEAVE_BE_TICKS}.</li>
 * <li><b>Starlit blood:</b> the looks merge (client {@code HeritageLooks}, {@code VampireLooks}).</li>
 * <li><b>Night-deep gifts:</b> Blood Dash and Groundpound reach further while a Nightborn's dark bonus is on.</li>
 * <li><b>Born dhampir:</b> a Nightborn dhampir keeps the vampire's night Strength.</li>
 * <li><b>What the cure leaves:</b> a cured Nightborn keeps one trace for good, picked at the rite ({@link #TRACE}).</li>
 * <li><b>Blood Moon kin:</b> under a Blood Moon a Nightborn vampire's dark bonus grows, and the risen dead leave them
 * be.</li>
 * </ul>
 */
public final class NightbornBlood {
	/** Old Blood: a Nightborn bitten at night turns this long after the bite (ticks); the last stretch of it they reel. */
	static final int OLD_BLOOD_TICKS = 600, OLD_BLOOD_REEL_TICKS = 100;
	/** Old Blood: a vampire's bite never takes an unturned Nightborn below this much health. */
	static final float OLD_BLOOD_FLOOR = 2.0F;
	/** Long thirst: nights a Nightborn vampire goes unfed before starving (others: {@link VampirePlayers#STARVE_NIGHTS}). */
	static final int STARVE_NIGHTS = 3;
	/** Kin-sense: how far a vampire faintly feels an unturned Nightborn, how many it shows, distances rounded to this. */
	static final double FAINT_RANGE = 96.0;
	static final int FAINT_SHOWN = 2, FAINT_ROUND = 10;
	/** The Elder's offer: how far he looks for one of the Nightborn, how near he comes to speak, how long he waits on an
	 *  answer (ticks), how far they may go before it counts as walking away, and how long he then leaves them be. */
	static final double COURT_RANGE = 48.0, OFFER_REACH = 3.5, WALK_AWAY = 14.0;
	static final int OFFER_TICKS = 20 * 30, LEAVE_BE_TICKS = 24000 * 3;
	/** The bite freely given: what it takes. */
	static final float GIVEN_BITE = 2.0F;
	/** Night-deep gifts: Blood Dash's reach (times); Groundpound's radius, throw and reveal range (times). */
	static final double DEEP_DASH = 1.3, DEEP_POUND_RADIUS = 1.4, DEEP_POUND_PUSH = 1.3, DEEP_REVEAL = 1.5;
	/** Blood Moon kin: a Nightborn vampire's dark bonus (times) under a Blood Moon and a Super Blood Moon. */
	static final double BLOOD_MOON_BONUS = 2.0, SUPER_BLOOD_MOON_BONUS = 3.0;
	/** What the cure leaves: how far a faint kin-sense still reaches. */
	static final double TRACE_SENSE_RANGE = 64.0;
	static final int TRACE_SENSE_SHOWN = 1;
	/** Monsters that climbed out of the ground under a Blood Moon ({@link Moons}). */
	static final String RISEN_TAG = "jaymoskingdoms_risen";

	/** The trace a cured Nightborn keeps: eyes for the dark, or a faint kin-sense. */
	static final int TRACE_EYES = 1, TRACE_SENSE = 2;
	/** On a cured Nightborn: which trace they kept. Saved, kept through death. */
	static final AttachmentType<Integer> TRACE = AttachmentRegistry.create(JaymosKingdoms.id("nightborn_trace"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());

	/** An offer waiting on an answer: the Elder's UUID and when it lapses (game time). */
	private record Offer(UUID elder, long until) {
	}

	/** Offers by the player they're made to; whom the Elder leaves be, till when (game time). */
	private static final Map<UUID, Offer> OFFERS = new HashMap<>();
	private static final Map<UUID, Long> LEAVE_BE = new HashMap<>();

	private NightbornBlood() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(NightbornBlood::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			OFFERS.clear();
			LEAVE_BE.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("nightkin").then(Commands.argument("answer", StringArgumentType.word())
				.executes(c -> answer(c.getSource().getPlayerOrException(), StringArgumentType.getString(c, "answer")))))));
	}

	// ---- who ---------------------------------------------------------------------------------------------------------

	static boolean nightborn(Entity e) {
		return e instanceof Player p && Heritage.is(p, Heritage.Kind.NIGHTBORN);
	}

	/** A Nightborn vampire (or dhampir). */
	public static boolean starlit(Entity e) {
		return nightborn(e) && Vampires.isVampire(e);
	}

	/** One of the Nightborn the Elder would sooner have as kin than as prey: unturned, unbitten, uncursed, in survival. */
	static boolean kin(LivingEntity e) {
		return e instanceof ServerPlayer p && nightborn(p) && p.isAlive() && !p.isCreative() && !p.isSpectator() && !Vampires.isVampire(p)
			&& !p.hasAttached(Vampires.BITTEN) && !Lycans.cursed(p);
	}

	// ---- Old Blood -----------------------------------------------------------------------------------------------------

	/** Whether the bitten {@code player} (bitten at {@code bitten}, the Overworld clock) turns now, at night, by Old Blood. */
	static boolean oldBlood(ServerPlayer player, long bitten, long clock) {
		return nightborn(player) && clock - bitten >= OLD_BLOOD_TICKS;
	}

	/** Every second, a Nightborn bitten and waiting out the night's fever: the blood races, and at the last they reel. */
	static void fever(ServerPlayer player, long bitten, long clock) {
		if (!nightborn(player)) {
			return;
		}
		long left = OLD_BLOOD_TICKS - (clock - bitten);
		if (left <= 0 || left > OLD_BLOOD_TICKS) {
			return;
		}
		player.level().playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 0.6F,
			0.8F + 0.4F * (1.0F - left / (float)OLD_BLOOD_TICKS));
		if (left <= OLD_BLOOD_REEL_TICKS) {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0, false, false, false));
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.nightborn.fever").withStyle(ChatFormatting.DARK_RED));
		}
	}

	/** A vampire's bite of {@code amount} on {@code prey}, as Old Blood lets it land (0: it can take nothing more). */
	static float bite(LivingEntity prey, float amount) {
		if (!nightborn(prey) || Vampires.isVampire(prey)) {
			return amount;
		}
		return Math.max(0.0F, Math.min(amount, prey.getHealth() - OLD_BLOOD_FLOOR));
	}

	/** A Nightborn wakes from the change whole. */
	static void woke(ServerPlayer player, boolean half) {
		if (!nightborn(player)) {
			return;
		}
		player.setHealth(player.getMaxHealth());
		player.removeEffect(MobEffects.WEAKNESS);
		player.removeEffect(MobEffects.HUNGER);
		player.sendSystemMessage(Component.translatable(half ? "message.jaymoskingdoms.nightborn.born_dhampir" : "message.jaymoskingdoms.nightborn.old_blood")
			.withStyle(ChatFormatting.DARK_RED));
	}

	// ---- long thirst, born dhampirs, Blood Moon kin ------------------------------------------------------------------

	/** Nights without blood before {@code player} starves (read on both sides). */
	public static int starveNights(Player player) {
		return nightborn(player) ? STARVE_NIGHTS : VampirePlayers.STARVE_NIGHTS;
	}

	/** How much a Nightborn's dark bonus is worth now (times): more for a vampire under a Blood Moon. */
	static double darkBonus(ServerPlayer player) {
		if (!Vampires.isVampire(player) || player.level().dimension() != Level.OVERWORLD) {
			return 1.0;
		}
		Moons.Moon moon = Moons.now(player.level().getServer());
		return moon == Moons.Moon.SUPER_BLOOD ? SUPER_BLOOD_MOON_BONUS : moon == Moons.Moon.BLOOD ? BLOOD_MOON_BONUS : 1.0;
	}

	/** (ThrallTargetMixin) Under a Blood Moon the risen dead leave Nightborn vampires be. */
	public static boolean spares(LivingEntity attacker, LivingEntity target) {
		return target instanceof ServerPlayer p && attacker.entityTags().contains(RISEN_TAG) && starlit(p) && Moons.blood(p.level().getServer());
	}

	/** Night-deep gifts: a blood gift's moves reach further while the Nightborn dark bonus is on. */
	static boolean deep(ServerPlayer player) {
		return nightborn(player) && Heritage.dark(player);
	}

	// ---- kin-sense -----------------------------------------------------------------------------------------------------

	/** Unturned Nightborn a vampire faintly feels: bearings and rough distances, nearest first, into the two lists. */
	static void faint(ServerPlayer player, List<Integer> bearings, List<Integer> distances) {
		List<ServerPlayer> found = new ArrayList<>();
		for (ServerPlayer other : player.level().players()) {
			if (other != player && !other.isSpectator() && nightborn(other) && !Vampires.isVampire(other) && other.distanceToSqr(player) <= FAINT_RANGE * FAINT_RANGE) {
				found.add(other);
			}
		}
		add(player, found, FAINT_SHOWN, bearings, distances);
	}

	private static void add(ServerPlayer player, List<? extends Entity> found, int shown, List<Integer> bearings, List<Integer> distances) {
		found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(player)));
		for (int i = 0; i < found.size() && i < shown; i++) {
			Entity e = found.get(i);
			double dx = e.getX() - player.getX(), dz = e.getZ() - player.getZ();
			bearings.add(Math.round((float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F));
			distances.add(Math.max(FAINT_ROUND, (int)Math.round(Math.sqrt(dx * dx + dz * dz) / FAINT_ROUND) * FAINT_ROUND));
		}
	}

	// ---- what the cure leaves ------------------------------------------------------------------------------------------

	/** The rite lifted a Nightborn's vampirism: one trace stays, for good (picked now, at random). */
	static void cured(ServerPlayer player) {
		if (!nightborn(player) || player.hasAttached(TRACE)) {
			return;
		}
		int trace = player.getRandom().nextBoolean() ? TRACE_EYES : TRACE_SENSE;
		player.setAttached(TRACE, trace);
		player.sendSystemMessage(Component.translatable(trace == TRACE_EYES ? "message.jaymoskingdoms.nightborn.trace_eyes"
			: "message.jaymoskingdoms.nightborn.trace_sense").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
	}

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % 200 == 0) {
			LEAVE_BE.values().removeIf(until -> until < now);
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((now + player.getId()) % 20 != 7 || !player.isAlive() || player.isSpectator()) {
				continue;
			}
			Integer trace = player.getAttached(TRACE);
			if (trace == null || Vampires.isVampire(player)) {
				continue;
			}
			if (trace == TRACE_EYES && Heritage.dark(player)) {
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, false));
			} else if (trace == TRACE_SENSE && NightGifts.gift(player) == null) {
				// (a faint kin-sense: the nearest vampire, roughly)
				List<Entity> found = new ArrayList<>();
				for (ServerPlayer other : player.level().players()) {
					if (other != player && !other.isSpectator() && Vampires.isVampire(other) && other.distanceToSqr(player) <= TRACE_SENSE_RANGE * TRACE_SENSE_RANGE) {
						found.add(other);
					}
				}
				found.addAll(player.level().getEntitiesOfClass(PathfinderMob.class, player.getBoundingBox().inflate(TRACE_SENSE_RANGE),
					m -> m.isAlive() && Vampires.isVampire(m) && m.distanceToSqr(player) <= TRACE_SENSE_RANGE * TRACE_SENSE_RANGE));
				List<Integer> bearings = new ArrayList<>(), distances = new ArrayList<>();
				add(player, found, TRACE_SENSE_SHOWN, bearings, distances);
				ServerPlayNetworking.send(player, new NightGifts.Sense(List.of(), List.of(), 0, bearings, distances));
			}
		}
	}

	// ---- the Elder's offer ---------------------------------------------------------------------------------------------

	/**
	 * (Vampires, the Elder at night with no prey) Courting one of the Nightborn: an offer already made he waits on, close
	 * by and watching; else he goes to the nearest he hasn't asked lately and, near enough, whispers it. True while he's
	 * busy at it.
	 */
	static boolean court(ServerLevel level, PathfinderMob elder, long now) {
		for (Map.Entry<UUID, Offer> e : new ArrayList<>(OFFERS.entrySet())) {
			if (!e.getValue().elder().equals(elder.getUUID())) {
				continue;
			}
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(e.getKey());
			if (player == null || !kin(player) || player.level() != level) {
				OFFERS.remove(e.getKey());
				continue;
			}
			if (now > e.getValue().until() || player.distanceTo(elder) > WALK_AWAY) {
				OFFERS.remove(e.getKey());
				leaveBe(player, now, "message.jaymoskingdoms.nightkin.walked");
				continue;
			}
			Vampires.release(elder);
			elder.getLookControl().setLookAt(player, 30.0F, 30.0F);
			return true;
		}
		ServerPlayer best = null;
		double bestD = COURT_RANGE * COURT_RANGE;
		for (ServerPlayer p : level.players()) {
			double d = p.distanceToSqr(elder);
			if (d < bestD && kin(p) && !OFFERS.containsKey(p.getUUID()) && !LEAVE_BE.containsKey(p.getUUID())) {
				bestD = d;
				best = p;
			}
		}
		if (best == null) {
			return false;
		}
		if (elder.isSleeping()) {
			elder.stopSleeping();
		}
		if (Math.sqrt(bestD) > OFFER_REACH || !elder.hasLineOfSight(best)) {
			Vampires.chase(elder, best);
			return true;
		}
		Vampires.release(elder);
		elder.getLookControl().setLookAt(best, 30.0F, 30.0F);
		offer(best, elder, now);
		return true;
	}

	private static void offer(ServerPlayer player, PathfinderMob elder, long now) {
		OFFERS.put(player.getUUID(), new Offer(elder.getUUID(), now + OFFER_TICKS));
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.nightkin.whisper", elder.getName())
			.withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		player.sendSystemMessage(Component.literal("  ").append(option("message.jaymoskingdoms.nightkin.yes", "/kingdom nightkin yes", ChatFormatting.RED))
			.append(Component.literal("   ")).append(option("message.jaymoskingdoms.nightkin.no", "/kingdom nightkin no", ChatFormatting.GRAY)));
		player.level().playSound(null, player.blockPosition(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.0F, 0.6F);
		// (it's on them: the screen darkens a moment)
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, false));
	}

	private static Component option(String key, String command, ChatFormatting colour) {
		return Component.translatable(key).withStyle(style -> style.withColor(colour).withUnderlined(true)
			.withClickEvent(new ClickEvent.RunCommand(command)).withHoverEvent(new HoverEvent.ShowText(Component.literal(command))));
	}

	private static void leaveBe(ServerPlayer player, long now, String key) {
		LEAVE_BE.put(player.getUUID(), now + LEAVE_BE_TICKS);
		player.sendSystemMessage(Component.translatable(key).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	/** {@code /kingdom nightkin yes|no}: the answer to the Elder. */
	private static int answer(ServerPlayer player, String answer) {
		ServerLevel level = player.level();
		long now = level.getServer().overworld().getGameTime();
		Offer offer = OFFERS.remove(player.getUUID());
		Entity found = offer == null ? null : level.getEntity(offer.elder());
		if (offer == null || now > offer.until() || !(found instanceof PathfinderMob elder) || !elder.isAlive() || elder.distanceTo(player) > WALK_AWAY
			|| !kin(player)) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.nightkin.no_offer").withStyle(ChatFormatting.GRAY));
			return 0;
		}
		if (!answer.equalsIgnoreCase("yes")) {
			leaveBe(player, now, "message.jaymoskingdoms.nightkin.refused");
			return 1;
		}
		// a bite freely given: it turns them (Old Blood), and it feeds him
		elder.getLookControl().setLookAt(player, 30.0F, 30.0F);
		elder.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		float bite = bite(player, GIVEN_BITE);
		if (bite > 0.0F) {
			player.hurtServer(level, level.damageSources().mobAttack(elder), bite);
		}
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 3, false, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 100, 0, false, false, false));
		level.sendParticles(PowersCompat.blood(0x8A0303, 1.0F), player.getX(), player.getEyeY() - 0.25, player.getZ(), 10, 0.15, 0.15, 0.15, 0.0);
		level.playSound(null, elder.blockPosition(), SoundEvents.GENERIC_DRINK.value(), SoundSource.HOSTILE, 0.9F, 0.5F);
		VampirePlayers.bitten(player);
		Vampires.fedElder(elder, level.getOverworldClockTime(), now);
		LEAVE_BE.put(player.getUUID(), now + LEAVE_BE_TICKS);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.nightkin.accepted").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		return 1;
	}
}
