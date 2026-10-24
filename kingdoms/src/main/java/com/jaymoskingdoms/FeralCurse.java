package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.wolf.WolfSoundVariants;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.185) A werewolf player slowly losing themselves to the curse (Andy: "becoming more and more feral as a
 * werewolf"). The wildness ({@link #FERAL}, 0-100) climbs with every change (the full moon's most), every minute as
 * the beast, every kill; it ebbs only a little in the days they stay themselves, and a little each time they fight the
 * wolf off. By stage (every {@link #STAGE} points):
 *
 * <ol start="0">
 * <li><b>Cursed:</b> as before.</li>
 * <li><b>Restless:</b> as the beast, now and then the wolf takes the wheel ({@link Seizures}) and goes for the nearest
 * beast or monster it can see. Fight it (each key a heave) or let it hunt.</li>
 * <li><b>Wild:</b> more often, longer, harder to fight, and villagers are prey. Badly hurt at night as themselves, the
 * wolf may tear out to save itself: they double over and can fight it down. Changing back at will sometimes fails.</li>
 * <li><b>Savage:</b> players are prey too. Some nights the change comes on its own, whatever the moon. Changing back
 * fails more often than not.</li>
 * <li><b>Feral:</b> the change comes every night; the hunts run from one kill to the next; at night there's no
 * changing back.</li>
 * <li><b>Lost:</b> the change at night can't be fought.</li>
 * </ol>
 * The priest's cure lifts it all.
 */
public final class FeralCurse {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** Points to a stage (stages 0-5). */
	static final int STAGE = 20;
	/** Gained: a full moon's change, a change that came on its own, one at will. */
	static final float MOON_CHANGE = 10.0F, WILD_CHANGE = 6.0F, WILLED_CHANGE = 2.0F;
	/** Gained each second as the beast (twice under a full moon). */
	static final float BEAST_SECOND = 0.01F;
	/** Gained per kill as the beast: a beast or monster, a person or player. */
	static final float KILL_BEAST = 0.5F, KILL_PERSON = 3.0F;
	/** Gained when a hunt ends: fed (its prey fell), lost; lost when they tear free, or fight the change down. */
	static final float HUNT_FED = 2.0F, HUNT_LOST = 1.0F, BROKE_FREE = 1.5F, HELD_BACK = 1.0F;
	/** Lost each second as themselves (about 2 a day). */
	static final float EBB = 0.0015F;
	/** As the beast, how often it may take the wheel (ticks), and the chance each time, by stage. */
	static final int WHIM_EVERY = 400;
	static final float[] WHIM_CHANCE = {0.0F, 0.15F, 0.25F, 0.35F, 0.5F, 0.7F};
	/** A hunt: how long (ticks) and how many presses break it, by stage; its bar drains this much a tick plus this per stage. */
	static final int HUNT_TICKS = 200, HUNT_TICKS_PER = 60, NEEDED = 6, NEEDED_PER = 6;
	static final float FADE = 0.06F, FADE_PER = 0.03F;
	/** It looks for prey within this (blocks). */
	static final double HUNT_RANGE = 16.0;
	/** Hurt below this share of their health at night (stage 2+): the chance the wolf tears out; then not again for a while (ticks). */
	static final float HURT_SHARE = 0.5F, HURT_CHANCE = 0.35F;
	static final int HURT_COOLDOWN = 1200;
	/** Nights the change comes on its own: the chance a night, by stage; it comes within this long of the roll (ticks). */
	static final float[] NIGHT_CHANCE = {0.0F, 0.0F, 0.0F, 0.3F, 1.0F, 1.0F};
	static final int NIGHT_SPREAD = 4000;
	/** Fighting the change down: how long (ticks) and presses, at stage 2, and more per stage above it. */
	static final int CHANGE_TICKS = 100, CHANGE_NEEDED = 14, CHANGE_NEEDED_PER = 5;
	static final float CHANGE_FADE = 0.1F;
	/** Changing back at will fails this often, by stage (by day half as often). */
	static final float[] REFUSE_CHANCE = {0.0F, 0.0F, 0.3F, 0.6F, 1.0F, 1.0F};

	// ---- what's kept ---------------------------------------------------------------------------------------------------

	/** The wildness (0-100), on the cursed; synced to them (their senses sharpen with it). */
	public static final AttachmentType<Float> FERAL = AttachmentRegistry.create(JaymosKingdoms.id("feral_curse"),
		builder -> builder.persistent(Codec.FLOAT).copyOnDeath().syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));

	/** Not kept: the last night rolled for, when tonight's change is due (0: none), when the hurt may set it off again. */
	private static final Map<UUID, Long> ROLLED = new HashMap<>();
	private static final Map<UUID, Long> DUE = new HashMap<>();
	private static final Map<UUID, Long> HURT_READY = new HashMap<>();

	private FeralCurse() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(FeralCurse::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity instanceof ServerPlayer player && damageTaken > 0.0F) {
				hurt(player);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			ROLLED.clear();
			DUE.clear();
			HURT_READY.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("werewolf").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("feral")
					.executes(c -> show(c.getSource()))
					.then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
						.executes(c -> set(c.getSource(), IntegerArgumentType.getInteger(c, "value")))))
				.then(Commands.literal("whim").executes(c -> whimNow(c.getSource()))))));
	}

	// ---- queries (both sides) ------------------------------------------------------------------------------------------

	public static float feral(Player player) {
		Float f = player.getAttached(FERAL);
		return f == null ? 0.0F : f;
	}

	/** 0 (cursed) to 5 (lost). */
	public static int stage(Player player) {
		return Mth.clamp((int)(feral(player) / STAGE), 0, 5);
	}

	/** Cursed in full (a wolfblood never changes). */
	static boolean stillCursed(ServerPlayer player) {
		Lycans.Curse curse = player.getAttached(Lycans.CURSE);
		return curse != null && !curse.half();
	}

	private static void add(ServerPlayer player, float by) {
		int was = stage(player);
		player.setAttached(FERAL, Mth.clamp(feral(player) + by, 0.0F, 100.0F));
		int now = stage(player);
		if (now > was) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feral.stage." + now).withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
			howl(player, 0.5F);
		} else if (now < was) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feral.back." + now).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
	}

	private static void howl(ServerPlayer player, float pitch) {
		player.level().playSound(null, player.blockPosition(),
			SoundEvents.WOLF_SOUNDS.get(WolfSoundVariants.SoundSet.BIG).adultSounds().growlSound().value(), SoundSource.PLAYERS, 1.5F, pitch);
	}

	private static boolean night(ServerPlayer player) {
		return Vampires.night(player.level().getServer().overworld().getOverworldClockTime());
	}

	private static boolean fullMoonNight(ServerPlayer player) {
		ServerLevel overworld = player.level().getServer().overworld();
		long clock = overworld.getOverworldClockTime();
		return Vampires.night(clock) && Lycans.fullMoon(overworld, clock);
	}

	// ---- hooks from WerewolfPlayers ------------------------------------------------------------------------------------

	/** The beast tore out of them: under the moon ({@code forced}), on its own ({@code wild}), or at will. */
	static void toreOut(ServerPlayer player, boolean forced, boolean wild) {
		add(player, wild ? WILD_CHANGE : forced ? MOON_CHANGE : WILLED_CHANGE);
	}

	/** A second as the beast. */
	static void beastSecond(ServerPlayer player, boolean moon) {
		add(player, moon ? BEAST_SECOND * 2.0F : BEAST_SECOND);
	}

	/** The beast killed {@code prey}. */
	static void kill(ServerPlayer player, LivingEntity prey) {
		add(player, prey instanceof Player || Vampires.person(prey) || prey instanceof Folk ? KILL_PERSON : KILL_BEAST);
	}

	/** Asked to change back at will: whether the wolf won't let go. */
	static boolean refuses(ServerPlayer player) {
		float chance = REFUSE_CHANCE[stage(player)] * (night(player) ? 1.0F : 0.5F);
		if (chance <= 0.0F || player.getRandom().nextFloat() >= chance) {
			return false;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feral.refuse." + (1 + player.getRandom().nextInt(3)))
			.withStyle(ChatFormatting.GOLD));
		howl(player, 0.6F);
		return true;
	}

	// ---- every second --------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		long clock = overworld.getOverworldClockTime();
		boolean night = Vampires.night(clock);
		boolean moon = night && Lycans.fullMoon(overworld, clock);
		long nightNo = Vampires.nightIndex(clock);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if ((now + player.getId()) % 20 != 7) {
				continue;
			}
			if (!stillCursed(player)) {
				if (player.hasAttached(FERAL)) {
					// (cured: the wolf's gone out of them)
					player.removeAttached(FERAL);
				}
				continue;
			}
			if (!player.hasAttached(FERAL)) {
				player.setAttached(FERAL, 0.0F);
			}
			if (!player.isAlive() || player.isSpectator() || player.isCreative()) {
				continue;
			}
			int stage = stage(player);
			if (WerewolfPlayers.beastUp(player)) {
				DUE.remove(player.getUUID());
				if (!Seizures.held(player) && (now / 20) % (WHIM_EVERY / 20) == player.getId() % (WHIM_EVERY / 20)
					&& player.getRandom().nextFloat() < WHIM_CHANCE[stage]) {
					hunt(player);
				}
				continue;
			}
			if (WerewolfPlayers.beast(player)) {
				// (doubling over, or shrinking back)
				continue;
			}
			add(player, -EBB);
			if (!night || moon) {
				// (the full moon's change is the moon's: WerewolfPlayers)
				DUE.remove(player.getUUID());
				continue;
			}
			if (!nightNo(ROLLED, player, nightNo)) {
				ROLLED.put(player.getUUID(), nightNo);
				if (player.getRandom().nextFloat() < NIGHT_CHANCE[stage]) {
					DUE.put(player.getUUID(), now + player.getRandom().nextInt(NIGHT_SPREAD));
				}
			}
			Long due = DUE.get(player.getUUID());
			if (due != null && now >= due && !Seizures.held(player) && !player.isSleeping()) {
				DUE.remove(player.getUUID());
				tearing(player);
			}
		}
	}

	private static boolean nightNo(Map<UUID, Long> map, ServerPlayer player, long nightNo) {
		Long rolled = map.get(player.getUUID());
		return rolled != null && rolled == nightNo;
	}

	/** Badly hurt at night, themselves: the wolf may tear out to save itself. */
	private static void hurt(ServerPlayer player) {
		if (!stillCursed(player) || stage(player) < 2 || WerewolfPlayers.beast(player) || Seizures.held(player) || !night(player)
			|| fullMoonNight(player) || player.isCreative() || player.getHealth() > player.getMaxHealth() * HURT_SHARE || !player.isAlive()) {
			return;
		}
		long now = player.level().getGameTime();
		if (now < HURT_READY.getOrDefault(player.getUUID(), 0L)) {
			return;
		}
		HURT_READY.put(player.getUUID(), now + HURT_COOLDOWN);
		if (player.getRandom().nextFloat() < HURT_CHANCE) {
			tearing(player);
		}
	}

	/** The wolf starts tearing its way out: they can fight it down (until it's lost them). */
	private static void tearing(ServerPlayer player) {
		int stage = stage(player);
		howl(player, 0.5F);
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, CHANGE_TICKS + 60, 0, false, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CHANGE_TICKS, 4, false, false, false));
		if (stage >= 5) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feral.tearing.lost").withStyle(ChatFormatting.GOLD));
			WerewolfPlayers.wildChange(player);
			return;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feral.tearing." + (1 + player.getRandom().nextInt(3)))
			.withStyle(ChatFormatting.GOLD));
		int needed = CHANGE_NEEDED + CHANGE_NEEDED_PER * Math.max(0, stage - 2);
		Seizures.start(player, Seizures.Kind.CHANGE, null, CHANGE_TICKS, needed, CHANGE_FADE, false);
	}

	/** The fight against the change is over. */
	static void heldBack(ServerPlayer player, Seizures.Hold hold, Seizures.End how) {
		if (how == Seizures.End.BROKE_FREE) {
			add(player, -HELD_BACK);
			player.removeEffect(MobEffects.SLOWNESS);
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 400, 0));
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feral.held." + (1 + player.getRandom().nextInt(3)))
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		} else if (how == Seizures.End.LOST) {
			// (they couldn't hold it)
			WerewolfPlayers.wildChange(player);
		}
	}

	// ---- the wolf's hunts ----------------------------------------------------------------------------------------------

	/** Who the wolf goes for, by stage: beasts and monsters, then people, then players; never pets, never a beast like it. */
	private static boolean prey(ServerPlayer player, LivingEntity e, int stage) {
		if (e == player || !e.isAlive() || e.isInvisible() || e instanceof ArmorStand || Seizures.ownBeast(e) || Lycans.changed(e)) {
			return false;
		}
		if (e instanceof Player) {
			return stage >= 3 && Vampires.mortal(e);
		}
		if (Vampires.person(e) || e instanceof Folk) {
			return stage >= 2;
		}
		return e instanceof Mob m && !(m instanceof TamableAnimal tame && tame.isTame()) && (stage >= 3 || !m.hasCustomName());
	}

	private static @Nullable LivingEntity nearest(ServerPlayer player, boolean seen) {
		int stage = stage(player);
		LivingEntity best = null;
		double bestD = HUNT_RANGE * HUNT_RANGE;
		for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(HUNT_RANGE),
			e -> prey(player, e, stage))) {
			double d = e.distanceToSqr(player);
			if (d < bestD && (!seen || player.hasLineOfSight(e))) {
				bestD = d;
				best = e;
			}
		}
		return best;
	}

	/** The wolf takes the wheel. False if there's nothing to go for. */
	static boolean hunt(ServerPlayer player) {
		LivingEntity prey = nearest(player, true);
		if (prey == null) {
			return false;
		}
		int stage = Math.max(1, stage(player));
		Seizures.start(player, Seizures.Kind.FERAL, prey, HUNT_TICKS + HUNT_TICKS_PER * stage, NEEDED + NEEDED_PER * stage, FADE + FADE_PER * stage, false);
		howl(player, 0.7F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feral.hunt." + (1 + player.getRandom().nextInt(4)))
			.withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** Its prey fell or got away: from Feral on, straight on to the next; else nothing. */
	static @Nullable LivingEntity nextPrey(ServerPlayer player, Seizures.Hold hold) {
		return stage(player) >= 4 ? nearest(player, false) : null;
	}

	/** A hunt's over. */
	static void released(ServerPlayer player, Seizures.Hold hold, Seizures.End how) {
		switch (how) {
			case BROKE_FREE -> {
				add(player, -BROKE_FREE);
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.feral.free." + (1 + player.getRandom().nextInt(3)))
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
			}
			case SATED -> add(player, HUNT_FED);
			case LOST -> add(player, HUNT_LOST);
			case CUT -> {
			}
		}
	}

	// ---- commands ------------------------------------------------------------------------------------------------------

	private static int show(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		source.sendSuccess(() -> Component.literal("Feral " + Math.round(feral(player)) + " (stage " + stage(player) + ")"), false);
		return 1;
	}

	private static int set(CommandSourceStack source, int value) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!stillCursed(player)) {
			source.sendFailure(Component.literal("You don't carry the full curse."));
			return 0;
		}
		add(player, value - feral(player));
		return show(source);
	}

	private static int whimNow(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		if (!WerewolfPlayers.beastUp(player) || !hunt(player)) {
			source.sendFailure(Component.literal("You're not the beast right now, or there's nothing in sight to hunt."));
			return 0;
		}
		return 1;
	}
}
