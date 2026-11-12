package com.jaymoskingdoms.thirst;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Lycans;
import com.jaymoskingdoms.MoonCurse;
import com.jaymoskingdoms.TalkNet;
import com.jaymoskingdoms.Vampires;
import com.jaymoskingdoms.hunger.HungerCurse;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The thirst: an affliction, not a power (like the moon's curse, the curse of the hungry and the festering). It is the last
 * rite of the forbidden blood arts. A blood witch gives it, once, to someone already deep in blood magic who brings her what
 * she asks ({@link ThirstRite}); it can also be caught, rarely, from one who drinks a dying person nearly dry ({@link ThirstBite}).
 * <ul>
 * <li><b>The gauge</b> (0 to {@link #FULL}) falls with time and is filled by drinking ({@link ThirstBite}). Ordinary food
 * barely feeds them ({@link #POOR_FOOD}). Low, it weakens in three stages shown by the body and the screen, never by
 * numbers ({@link #STAGE_BELOW}); it never kills by itself.</li>
 * <li><b>The sun</b> weakens them and makes them smoke under the open daylight sky, and burns them slowly if they stay
 * ({@link #BURN_AFTER}); a helmet or shade lessens it, and holy ground ({@link #HOLY_RANGE} round a patron's temple) weakens them
 * by day or night. Nights make them quicker and stronger.</li>
 * <li><b>Shapes</b> ({@link ThirstForms}): a bat, a colony of bats, a mist and a red-eyed wolf, each at a cost of the gauge and
 * with a wait; the stare and the riposte are there too. They are picked with sneak and use on the open air.</li>
 * <li><b>Cures.</b> Only early, in the first {@link #CURE_TICKS} of play after it comes: a Potion of Cleansing (Jaymo's Powers'
 * effect), a healer's work or a spell of mending ({@code PowersCompat#cureCurses} and {@code restore}). Once settled it stays.</li>
 * <li><b>Death.</b> As the moon's curse, it is kept through death (the gauge comes back half full). It cannot be taken by
 * someone moon-cursed, wolf-cursed, hungry-cursed or already a blood-drinker of the old kind, nor can those take it
 * ({@link #excluded}).</li>
 * </ul>
 * Operators: {@code /kingdom thirst <give|cure|feed>} on themselves.
 */
public final class Thirst {
	// ---- tuning ----
	/** The gauge when full, what the thirst starts with, and how many seconds pass for each point it falls. */
	public static final int FULL = 100;
	static final int START = 55, FALL_SECONDS = 14;
	/** Below these gauge points the thirst is at its stages 1, 2 and 3. */
	static final int[] STAGE_BELOW = {40, 20, 6};
	/** How long after it comes (game ticks) it can still be washed out: about three days. */
	static final long CURE_TICKS = 72000L;
	/** How much of a meal ordinary food gives them. */
	static final float POOR_FOOD = 0.25F;
	/** How long each refresh of a sign lasts (ticks). */
	static final int SIGN_TICKS = 60;
	/** The night's gifts (amplifiers). */
	static final int NIGHT_SPEED = 0, NIGHT_STRENGTH = 0;
	/** The sun: seconds in it before it burns, seconds between burns bare-headed (three times that helmeted), damage, and the health it never takes them under. */
	static final int BURN_AFTER = 4, BURN_EVERY = 2;
	static final float BURN_DAMAGE = 1.0F, BURN_FLOOR = 6.0F;
	/** Holy ground: how near (blocks) a patron's temple the thirst is weakened. */
	static final double HOLY_RANGE = 28.0;
	/** The gauge points a respawn leaves them. */
	static final int RESPAWN_GAUGE = START / 2;
	/** The colour of the gauge and the blood. */
	public static final int BLOOD = 0x8A0303;

	private static final Identifier CLEANSING = Identifier.fromNamespaceAndPath("jaymospowers", "cleansing");

	/** On the afflicted. Saved, kept through death, told to its owner. */
	public static final AttachmentType<Boolean> THIRSTY = AttachmentRegistry.create(JaymosKingdoms.id("thirst"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.targetOnly()));
	/** The gauge (0 to {@link #FULL}). Saved, kept through death. */
	static final AttachmentType<Integer> GAUGE = AttachmentRegistry.create(JaymosKingdoms.id("thirst_gauge"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());
	/** The game time it came. Saved, kept through death. */
	static final AttachmentType<Long> SINCE = AttachmentRegistry.create(JaymosKingdoms.id("thirst_since"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());
	/** The shape they wear now ("bat", "mist", "wolf"; none when absent): told to everyone, not saved. */
	public static final AttachmentType<String> FORM = AttachmentRegistry.create(JaymosKingdoms.id("thirst_form"),
		builder -> builder.syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));
	/** On someone who was given flight for a shape and not yet had it taken back (so a log-out in the air can't keep it). Saved. */
	static final AttachmentType<Boolean> FLIGHT = AttachmentRegistry.create(JaymosKingdoms.id("thirst_flight"), builder -> builder.persistent(Codec.BOOL));

	/** Not saved: a player's seconds toward the next point, seconds in the sun, and the stage they were last told of. */
	private static final class State {
		int fall, sun, told;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();
	/** The food level and saturation a player had before they ate (for the poor feeding). */
	private static final Map<UUID, float[]> ATE = new HashMap<>();

	private Thirst() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Thirst::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer player) {
				ThirstForms.end(player, false);
				ThirstBite.release(player);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, player, alive) -> {
			if (thirsty(player)) {
				player.setAttached(GAUGE, Math.max(RESPAWN_GAUGE, gauge(player)));
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> ThirstForms.settleOnJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ThirstForms.end(handler.player, false);
			ThirstBite.release(handler.player);
			STATES.remove(handler.player.getUUID());
			ATE.remove(handler.player.getUUID());
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			STATES.clear();
			ATE.clear();
		});
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.serverboundPlay().register(ThirstForms.Menu.TYPE, ThirstForms.Menu.CODEC);
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(ThirstForms.Menu.TYPE,
			(payload, context) -> ThirstForms.menu(context.player()));
		ThirstBite.register();
		ThirstForms.register();
		ThirstRite.register();
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("thirst").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("give").executes(c -> give(c.getSource().getPlayerOrException(), true) ? 1 : 0))
				.then(Commands.literal("cure").executes(c -> lift(c.getSource().getPlayerOrException()) ? 1 : 0))
				.then(Commands.literal("feed").executes(c -> {
					feed(c.getSource().getPlayerOrException(), FULL);
					return 1;
				})))));
	}

	// ---- queries (both sides) ----

	public static boolean thirsty(Player player) {
		return Boolean.TRUE.equals(player.getAttached(THIRSTY));
	}

	/** The shape {@code player} wears, or an empty string. */
	public static String form(Player player) {
		return player.getAttachedOrElse(FORM, "");
	}

	/** The gauge of {@code player} (server side). */
	public static int gauge(Player player) {
		return player.getAttachedOrElse(GAUGE, 0);
	}

	/** The stage (0 to 3) of {@code player}'s thirst. */
	public static int stage(Player player) {
		int gauge = gauge(player);
		int stage = 0;
		for (int i = 0; i < STAGE_BELOW.length; i++) {
			if (gauge < STAGE_BELOW[i]) {
				stage = i + 1;
			}
		}
		return stage;
	}

	/** Whether {@code player} has another affliction of this kind, or is a blood-drinker of the old kind: the two don't live in one body. */
	public static boolean excluded(Player player) {
		return MoonCurse.cursed(player) || HungerCurse.cursed(player) || Lycans.cursed(player) || Vampires.isVampire(player);
	}

	/** Whether the thirst has set in {@code player}: it can no longer be washed out. */
	public static boolean settled(Player player) {
		return player.level().getGameTime() - player.getAttachedOrElse(SINCE, 0L) > CURE_TICKS;
	}

	// ---- entries and cures ----

	/** The thirst comes on {@code player} (the blood witch's rite, a bite on the dying, an operator). False if it could not. */
	public static boolean give(ServerPlayer player, boolean force) {
		if (thirsty(player) || excluded(player) || !force && (player.isCreative() || player.isSpectator())) {
			return false;
		}
		player.setAttached(THIRSTY, true);
		player.setAttached(GAUGE, START);
		player.setAttached(SINCE, player.level().getGameTime());
		STATES.remove(player.getUUID());
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.drinker.given").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		player.level().playSound(null, player.blockPosition(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 0.5F);
		return true;
	}

	/** A healer's hands, a spell of mending or a Potion of Cleansing: the thirst lets go, if it has not yet settled. True if it did. */
	public static boolean cure(ServerPlayer player) {
		return thirsty(player) && !settled(player) && lift(player);
	}

	/** The thirst is lifted whatever its age (the cure itself, and the operators'). */
	static boolean lift(ServerPlayer player) {
		if (!thirsty(player)) {
			return false;
		}
		ThirstForms.end(player, false);
		ThirstBite.release(player);
		player.removeAttached(THIRSTY);
		player.removeAttached(GAUGE);
		player.removeAttached(SINCE);
		STATES.remove(player.getUUID());
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.drinker.cured").withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** Puts {@code by} into the gauge. */
	public static void feed(ServerPlayer player, int by) {
		if (!thirsty(player)) {
			return;
		}
		int before = gauge(player);
		int after = Math.max(0, Math.min(FULL, before + by));
		player.setAttached(GAUGE, after);
		if (after >= FULL && before < FULL) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.drinker.sated").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
		}
	}

	/** Takes {@code by} out of the gauge. */
	static void spend(ServerPlayer player, int by) {
		player.setAttached(GAUGE, Math.max(0, gauge(player) - by));
	}

	// ---- eating (called from the food mixin) ----

	/** {@code eater} starts to eat: remembers what they had, for the poor feeding. */
	public static void ateStart(LivingEntity eater) {
		if (eater instanceof ServerPlayer player && thirsty(player)) {
			ATE.put(player.getUUID(), new float[] {player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()});
		}
	}

	/** Ordinary food feeds the thirsty by {@link #POOR_FOOD}. */
	public static void ateDone(LivingEntity eater) {
		if (!(eater instanceof ServerPlayer player)) {
			return;
		}
		float[] before = ATE.remove(player.getUUID());
		if (before == null || !thirsty(player)) {
			return;
		}
		var data = player.getFoodData();
		int food = data.getFoodLevel();
		float saturation = data.getSaturationLevel();
		data.setFoodLevel(Math.max((int)before[0], (int)(before[0] + Math.ceil((food - before[0]) * POOR_FOOD))));
		data.setSaturation(Math.max(before[1], before[1] + (saturation - before[1]) * POOR_FOOD));
	}

	// ---- answers (the talk screen) ----

	/** An answer on the talk screen that was ours (a shape menu or the blood witch's rite). */
	public static boolean answer(ServerPlayer player, TalkNet.Answer answer) {
		return ThirstForms.answer(player, answer) || ThirstRite.answer(player, answer);
	}

	// ---- the tick ----

	private static void tick(MinecraftServer server) {
		boolean second = server.getTickCount() % 20 == 0;
		boolean beat = server.getTickCount() % 5 == 0;
		if (!second && !beat) {
			return;
		}
		Holder<MobEffect> cleansing = second ? BuiltInRegistries.MOB_EFFECT.get(CLEANSING).map(h -> (Holder<MobEffect>)h).orElse(null) : null;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!thirsty(player)) {
				continue;
			}
			if (!player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			if (second) {
				if (cleansing != null && player.hasEffect(cleansing) && cure(player)) {
					continue;
				}
				perSecond(player, level);
			}
			if (beat) {
				bar(player);
			}
		}
	}

	private static void perSecond(ServerPlayer player, ServerLevel level) {
		State s = STATES.computeIfAbsent(player.getUUID(), id -> new State());
		if (++s.fall >= FALL_SECONDS) {
			s.fall = 0;
			spend(player, 1);
		}
		int stage = stage(player);
		if (stage > s.told) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.drinker.stage." + stage).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			s.told = stage;
		} else if (stage < s.told) {
			s.told = stage;
		}
		if (stage >= 1) {
			player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, SIGN_TICKS, 0, true, false, false));
		}
		if (stage >= 2) {
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SIGN_TICKS, 0, true, false, false));
			if (player.getRandom().nextInt(12) == 0) {
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0, true, false, false));
			}
		}
		if (stage >= 3) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SIGN_TICKS, 0, true, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, SIGN_TICKS + 40, 0, true, false, false));
		}
		boolean overworld = level.dimension() == Level.OVERWORLD;
		boolean sunlit = overworld && level.isBrightOutside() && level.canSeeSky(player.blockPosition());
		boolean holy = overworld && HolyGround.near(level, player.position(), HOLY_RANGE);
		if (sunlit) {
			sun(player, level, s);
		} else {
			s.sun = 0;
		}
		if (holy) {
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SIGN_TICKS, 0, true, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SIGN_TICKS, 0, true, false, false));
			smoke(level, player);
			if (s.told >= 0 && player.getRandom().nextInt(20) == 0) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.drinker.holy").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
			}
		}
		if (!sunlit && !holy && level.isDarkOutside() && stage == 0) {
			player.addEffect(new MobEffectInstance(MobEffects.SPEED, SIGN_TICKS, NIGHT_SPEED, true, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, SIGN_TICKS, NIGHT_STRENGTH, true, false, false));
		}
	}

	/** The sun on them: weakness, smoke, and after a few seconds a slow burn (never to death). */
	private static void sun(ServerPlayer player, ServerLevel level, State s) {
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SIGN_TICKS, 0, true, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SIGN_TICKS, 0, true, false, false));
		smoke(level, player);
		s.sun++;
		boolean helmet = !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
		int every = helmet ? BURN_EVERY * 3 : BURN_EVERY;
		if (s.sun == BURN_AFTER - 1) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.drinker.sun").withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC));
		}
		if (s.sun >= BURN_AFTER && s.sun % every == 0 && player.getHealth() > BURN_FLOOR) {
			player.hurtServer(level, level.damageSources().onFire(), BURN_DAMAGE);
		}
	}

	private static void smoke(ServerLevel level, ServerPlayer player) {
		level.sendParticles(Fx.miasma(0x3A3A3A, 0.7F), player.getX(), player.getY() + player.getBbHeight() * 0.5, player.getZ(), 5, 0.25, 0.4, 0.25, 0.01);
	}

	/** The action bar: the gauge, as {@code =} and {@code -} only, when they crouch; the bite's or the shape's own bar before it. */
	private static void bar(ServerPlayer player) {
		MutableComponent text = ThirstBite.bar(player);
		if (text == null) {
			text = ThirstForms.bar(player);
		}
		if (text == null && player.isShiftKeyDown()) {
			text = bar(gauge(player), FULL, ChatFormatting.DARK_RED);
		}
		if (text != null) {
			player.sendOverlayMessage(text);
		}
	}

	/** A bar of ten cells, {@code =} where it is full and {@code -} where it is not. */
	static MutableComponent bar(int value, int of, ChatFormatting colour) {
		final int cells = 10;
		int full = Math.max(0, Math.min(cells, Math.round(value * cells / (float)of)));
		return Component.literal("=".repeat(full)).withStyle(colour).append(Component.literal("-".repeat(cells - full)).withStyle(ChatFormatting.DARK_GRAY));
	}

	/** Where blood is spilled and blood-drinkers are seen: a light red mist at {@code at}. */
	static void bloodMist(ServerLevel level, Vec3 at, float size, int count) {
		level.sendParticles(Fx.mote(BLOOD, size), at.x, at.y, at.z, count, 0.2, 0.2, 0.2, 0.01);
	}
}
