package com.jaymoskingdoms.hunger;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.KitchenCompat;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The curse of the hungry: an affliction, not a power (like the festering and {@code MoonCurse}). It comes from the Gaunt's
 * bite (sometimes), from eating its heart ({@link Hunger#HEART}) and, rarely, from eating rotten flesh in the snow at night.
 * <ul>
 * <li><b>At night</b> a cursed player sprints faster, on all fours (the client draws it: {@code HungerPose}); crouching on the
 * ground coils them, and a jump from the coil is a pounce that pins whatever they land on (heavy slowness and a bite of
 * damage for a few seconds).</li>
 * <li><b>Feeding.</b> Crouching by a corpse of something they killed devours it ({@link #DEVOUR_TICKS}); with Jaymo's Kitchen
 * that is its corpse ({@link KitchenCompat#freeCorpseNear}), without it a kill from the last {@link #FRESH_TICKS}. Never the
 * living. Each meal fills a gauge, shown in the action bar as {@code =} and {@code -}; at full they grow
 * ({@link #BIG_SCALE}), hit harder and take less for {@link #BIG_TICKS}, then the gauge empties and they are spent.</li>
 * <li><b>Costs.</b> Ordinary food feeds them by half; villagers keep away from them at night; daylight weakens them. And the
 * craving grows between meals, in three stages shown by the body and the screen, never by numbers ({@link #STAGE_AT}); it never
 * kills.</li>
 * <li><b>Cures.</b> A Potion of Cleansing (Jaymo's Powers' effect), a healer's work or a spell of mending
 * ({@code PowersCompat#cureCurses} and {@code restore}). Dying ends it (it is not kept through death, unlike the moon's
 * curse: it is caught, not born to). Operators: {@code /kingdom hungercurse <curse|cure|feed>} on themselves.</li>
 * </ul>
 */
public final class HungerCurse {
	// ---- tuning ----
	/** The gauge when full, and what a meal gives (by the dead one's health, between the two). */
	static final int FULL = 100, FEED_MIN = 20, FEED_MAX = 60;
	/** What eating the heart gives one already cursed. */
	static final int HEART_FEED = 50;
	/** How long the grown form lasts (ticks), and its gifts: size, bite, health, armour, steadiness, reach. */
	static final int BIG_TICKS = 600;
	static final double BIG_SCALE = 0.6, BIG_DAMAGE = 5.0, BIG_HEALTH = 12.0, BIG_ARMOUR = 5.0, BIG_KNOCKBACK = 0.5, BIG_REACH = 1.5;
	/** How long they are spent after it (ticks). */
	static final int SPENT_TICKS = 160;
	/** How long (ticks) devouring takes, how near the kill they must be, and how long a kill can be fed on (ticks) with Kitchen, and without it. */
	static final int DEVOUR_TICKS = 50, MEMORY_TICKS = 6000, FRESH_TICKS = 100;
	static final double DEVOUR_REACH = 3.5, CORPSE_SLACK = 3.0;
	/** The pounce: coil ticks it takes at least and at most, ticks between pounces, the leap's strength (low, high) and lift, how long it counts as flying. */
	static final int COIL_MIN = 8, COIL_MAX = 25, POUNCE_COOLDOWN = 80, POUNCE_FLIGHT = 40;
	static final double LEAP_LOW = 0.9, LEAP_HIGH = 1.7, LIFT_LOW = 0.42, LIFT_HIGH = 0.67;
	/** What a pounce lands on: how near (blocks), the bite, how long the pin holds (ticks) and its slowness. */
	static final double LAND_REACH = 1.7;
	static final float PIN_DAMAGE = 5.0F;
	static final int PIN_TICKS = 60, PIN_SLOW = 6;
	/** Their speed at night (amplifier), and how long each refresh of the signs lasts (ticks). */
	static final int NIGHT_SPEED = 1, SIGN_TICKS = 60;
	/** How near (blocks) villagers keep from them at night, and how far they run. */
	static final double FEAR_RANGE = 12.0;
	static final int FEAR_RUN = 10;
	/** The craving (seconds since a meal) at each stage (1, 2, 3). */
	static final int[] STAGE_AT = {300, 900, 1800};
	/** Per second, the chance of a swimming head by stage (index 0 is stage 1), and how long it lasts (ticks). */
	static final float[] SWIM_CHANCE = {0.0F, 1.0F / 40.0F, 1.0F / 15.0F};
	static final int SWIM_TICKS = 100;
	/** Eating rotten flesh in the snow at night: the chance of the curse. */
	static final float ROT_CHANCE = 0.12F;
	/** How much of a meal ordinary food gives them. */
	static final float POOR_FOOD = 0.5F;
	/** The colour of the gauge's {@code =}. */
	static final int MEAL_COLOUR = 0x8E2A3F;

	private static final Identifier CLEANSING = Identifier.fromNamespaceAndPath("jaymospowers", "cleansing");
	private static final Identifier SIZE_ID = JaymosKingdoms.id("hunger_size"), DAMAGE_ID = JaymosKingdoms.id("hunger_bite"),
		HEALTH_ID = JaymosKingdoms.id("hunger_health"), ARMOUR_ID = JaymosKingdoms.id("hunger_armour"),
		KNOCK_ID = JaymosKingdoms.id("hunger_steady"), REACH_ID = JaymosKingdoms.id("hunger_reach");

	/** On the cursed: they carry it. Saved, and synced to everyone (the client draws them on all fours); not kept through death. */
	public static final AttachmentType<Boolean> CURSED = AttachmentRegistry.create(JaymosKingdoms.id("hunger_cursed"),
		builder -> builder.persistent(Codec.BOOL).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** The meal gauge (0 to {@link #FULL}). Saved. */
	static final AttachmentType<Integer> GAUGE = AttachmentRegistry.create(JaymosKingdoms.id("hunger_gauge"), builder -> builder.persistent(Codec.INT));
	/** Seconds since they last fed. Saved. */
	static final AttachmentType<Integer> CRAVING = AttachmentRegistry.create(JaymosKingdoms.id("hunger_craving"), builder -> builder.persistent(Codec.INT));
	/** When the grown form ends (game time); 0 when not grown. Saved. */
	static final AttachmentType<Long> BIG_UNTIL = AttachmentRegistry.create(JaymosKingdoms.id("hunger_big_until"), builder -> builder.persistent(Codec.LONG));

	/** A kill that may be fed on: where, when, and the dead one's health. */
	private record Kill(Vec3 at, long when, float health) {
	}

	/** Not saved: a player's coil, pounce and meal in progress. */
	private static final class Moves {
		int coil, devour;
		boolean wasGround = true, pouncing;
		long pounceAt, readyAt;
	}

	private static final Map<UUID, Moves> MOVES = new HashMap<>();
	private static final Map<UUID, List<Kill>> KILLS = new HashMap<>();
	private static final Map<UUID, Integer> TOLD = new HashMap<>();
	/** The food level and saturation a player had before they ate (for the poor feeding). */
	private static final Map<UUID, float[]> ATE = new HashMap<>();

	private HungerCurse() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(HungerCurse::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(HungerCurse::died);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MOVES.clear();
			KILLS.clear();
			TOLD.clear();
			ATE.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("hungercurse").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("curse").executes(c -> {
					curse(c.getSource().getPlayerOrException());
					return 1;
				}))
				.then(Commands.literal("cure").executes(c -> cure(c.getSource().getPlayerOrException()) ? 1 : 0))
				.then(Commands.literal("feed").executes(c -> {
					feed(c.getSource().getPlayerOrException(), FULL);
					return 1;
				})))));
	}

	// ---- queries ----

	public static boolean cursed(Player player) {
		return Boolean.TRUE.equals(player.getAttached(CURSED));
	}

	/** Whether {@code player} is grown now (the form after a full gauge). */
	public static boolean big(Player player) {
		return player.getAttachedOrElse(BIG_UNTIL, 0L) > player.level().getGameTime();
	}

	/** The stage (0 to 3) of {@code player}'s craving. */
	public static int stage(Player player) {
		int craving = player.getAttachedOrElse(CRAVING, 0);
		int stage = 0;
		for (int i = 0; i < STAGE_AT.length; i++) {
			if (craving >= STAGE_AT[i]) {
				stage = i + 1;
			}
		}
		return stage;
	}

	/** Night in the Overworld (or anywhere the sky is dark). */
	private static boolean night(Level level) {
		return level.isDarkOutside();
	}

	/** In the daylight, under the open sky. */
	private static boolean sunlit(Level level, BlockPos pos) {
		return level.isBrightOutside() && level.canSeeSky(pos);
	}

	// ---- entries and cures ----

	/** The curse comes on {@code player} (the Gaunt's bite, its heart, rotten flesh in the snow). */
	public static void curse(ServerPlayer player) {
		if (cursed(player) || player.isCreative() || player.isSpectator() || com.jaymoskingdoms.Vampires.isVampire(player) || com.jaymoskingdoms.Lycans.cursed(player)) {
			// (a vampire or a werewolf has a hunger of its own)
			return;
		}
		player.setAttached(CURSED, true);
		player.setAttached(GAUGE, 0);
		player.setAttached(CRAVING, 0);
		player.removeAttached(BIG_UNTIL);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.hunger.cursed").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		player.level().playSound(null, player.blockPosition(), SoundEvents.WARDEN_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.5F);
	}

	/** A healer's hands, a spell of mending or a Potion of Cleansing: the curse lets go. True if there was one. */
	public static boolean cure(ServerPlayer player) {
		if (!cursed(player)) {
			return false;
		}
		player.removeAttached(CURSED);
		player.removeAttached(GAUGE);
		player.removeAttached(CRAVING);
		player.removeAttached(BIG_UNTIL);
		MOVES.remove(player.getUUID());
		KILLS.remove(player.getUUID());
		TOLD.remove(player.getUUID());
		strip(player);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.hunger.cured").withStyle(ChatFormatting.GOLD));
		return true;
	}

	/** Puts {@code by} into the gauge (and the craving eases); at full they grow. */
	static void feed(ServerPlayer player, int by) {
		if (!cursed(player)) {
			return;
		}
		player.setAttached(CRAVING, 0);
		TOLD.remove(player.getUUID());
		if (big(player)) {
			return;
		}
		int gauge = Math.min(FULL, player.getAttachedOrElse(GAUGE, 0) + by);
		player.setAttached(GAUGE, gauge);
		if (gauge >= FULL) {
			grow(player);
		}
	}

	private static void grow(ServerPlayer player) {
		player.setAttached(BIG_UNTIL, player.level().getGameTime() + BIG_TICKS);
		bigGifts(player);
		player.heal((float)BIG_HEALTH);
		if (player.level() instanceof ServerLevel level) {
			level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.6F, 0.5F);
			Fx.ring(level, player.position(), 4.0F, MEAL_COLOUR);
			Fx.blast(level, player.position().add(0.0, 1.0, 0.0), 2.5F, MEAL_COLOUR);
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hunger.grown").withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC));
	}

	private static void spent(ServerPlayer player) {
		player.setAttached(GAUGE, 0);
		player.removeAttached(BIG_UNTIL);
		strip(player);
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SPENT_TICKS, 1, true, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SPENT_TICKS, 0, true, false, false));
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hunger.spent").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}

	// ---- eating (called from the food mixin) ----

	/** {@code eater} starts to eat {@code stack}: the heart and rotten flesh in the snow bring the curse; remembers what they had. */
	public static void ateStart(LivingEntity eater, ItemStack stack) {
		if (!(eater instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		if (stack.is(Hunger.HEART)) {
			if (cursed(player)) {
				feed(player, HEART_FEED);
			} else {
				curse(player);
			}
			return;
		}
		boolean rot = stack.is(Items.ROTTEN_FLESH);
		if (!cursed(player)) {
			if (rot && night(level) && inSnow(level, player.blockPosition()) && player.getRandom().nextFloat() < ROT_CHANCE) {
				curse(player);
			}
			return;
		}
		if (!rot) {
			ATE.put(player.getUUID(), new float[] {player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel()});
		}
	}

	/** Ordinary food feeds the cursed by {@link #POOR_FOOD}. */
	public static void ateDone(LivingEntity eater) {
		if (!(eater instanceof ServerPlayer player)) {
			return;
		}
		float[] before = ATE.remove(player.getUUID());
		if (before == null || !cursed(player)) {
			return;
		}
		var data = player.getFoodData();
		int food = data.getFoodLevel();
		float saturation = data.getSaturationLevel();
		data.setFoodLevel(Math.max((int)before[0], (int)(before[0] + Math.ceil((food - before[0]) * POOR_FOOD))));
		data.setSaturation(Math.max(before[1], before[1] + (saturation - before[1]) * POOR_FOOD));
	}

	/** Standing in snow: on or in it, or in a biome cold enough for it. */
	private static boolean inSnow(Level level, BlockPos pos) {
		return level.getBlockState(pos).is(BlockTags.SNOW) || level.getBlockState(pos.below()).is(BlockTags.SNOW)
			|| level.getBiome(pos).value().coldEnoughToSnow(pos, level.getSeaLevel());
	}

	// ---- kills ----

	private static void died(LivingEntity entity, DamageSource source) {
		if (!(source.getEntity() instanceof ServerPlayer killer) || !cursed(killer) || entity instanceof Player || entity instanceof ArmorStand) {
			return;
		}
		List<Kill> kills = KILLS.computeIfAbsent(killer.getUUID(), id -> new ArrayList<>());
		kills.add(new Kill(entity.position(), killer.level().getGameTime(), entity.getMaxHealth()));
		while (kills.size() > 6) {
			kills.removeFirst();
		}
	}

	// ---- the tick ----

	private static void tick(MinecraftServer server) {
		boolean second = server.getTickCount() % 20 == 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!cursed(player)) {
				if (second && player.getAttribute(Attributes.SCALE) != null && player.getAttribute(Attributes.SCALE).getModifier(SIZE_ID) != null) {
					strip(player);
				}
				continue;
			}
			if (!player.isAlive() || player.isSpectator()) {
				continue;
			}
			Holder<MobEffect> cleansing = BuiltInRegistries.MOB_EFFECT.get(CLEANSING).map(h -> (Holder<MobEffect>)h).orElse(null);
			if (cleansing != null && player.hasEffect(cleansing) && cure(player)) {
				continue;
			}
			if (!(player.level() instanceof ServerLevel level)) {
				continue;
			}
			long now = level.getGameTime();
			Moves moves = MOVES.computeIfAbsent(player.getUUID(), id -> new Moves());
			boolean big = big(player);
			if (big && now >= player.getAttachedOrElse(BIG_UNTIL, 0L)) {
				spent(player);
				big = false;
			}
			boolean dark = night(level);
			pounce(player, level, moves, now, dark || big);
			devour(player, level, moves, now);
			if (second) {
				perSecond(player, level, big, dark);
			}
			if (server.getTickCount() % 5 == 0) {
				gauge(player, moves, big, now);
			}
		}
	}

	/** One second of a cursed player: the craving, the signs, the night's gifts, the day's weakness, the villagers' fear. */
	private static void perSecond(ServerPlayer player, ServerLevel level, boolean big, boolean dark) {
		if (big) {
			bigGifts(player);
		} else {
			player.setAttached(CRAVING, player.getAttachedOrElse(CRAVING, 0) + 1);
		}
		int stage = stage(player);
		Integer told = TOLD.get(player.getUUID());
		if (stage > 0 && (told == null || told < stage)) {
			TOLD.put(player.getUUID(), stage);
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hunger.stage." + stage).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
		} else if (told != null && told > stage) {
			TOLD.put(player.getUUID(), stage);
		}
		if (stage >= 1) {
			player.addEffect(new MobEffectInstance(MobEffects.HUNGER, SIGN_TICKS, 0, true, false, false));
		}
		if (stage >= 2) {
			player.addEffect(new MobEffectInstance(MobEffects.MINING_FATIGUE, SIGN_TICKS, 0, true, false, false));
		}
		if (stage >= 3) {
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SIGN_TICKS, 0, true, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, SIGN_TICKS + 40, 0, true, false, false));
		}
		if (stage >= 2 && player.getRandom().nextFloat() < SWIM_CHANCE[stage - 1]) {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, SWIM_TICKS, 0, true, false, false));
			level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 0.6F, 0.6F);
		}
		if (dark || big) {
			if (player.isSprinting()) {
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, SIGN_TICKS, NIGHT_SPEED, true, false, false));
			}
			scare(player, level);
		} else if (sunlit(level, player.blockPosition())) {
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, SIGN_TICKS, 0, true, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SIGN_TICKS, 0, true, false, false));
		}
	}

	/** Villagers near them at night keep away. */
	private static void scare(ServerPlayer player, ServerLevel level) {
		for (AbstractVillager villager : level.getEntitiesOfClass(AbstractVillager.class, player.getBoundingBox().inflate(FEAR_RANGE), AbstractVillager::isAlive)) {
			Vec3 away = DefaultRandomPos.getPosAway(villager, FEAR_RUN, 4, player.position());
			if (away != null) {
				villager.getNavigation().moveTo(away.x, away.y, away.z, 1.2);
			}
		}
	}

	/** Crouching on the ground coils them; a jump from the coil is a pounce; the pounce pins what it lands on. */
	private static void pounce(ServerPlayer player, ServerLevel level, Moves moves, long now, boolean ready) {
		boolean ground = player.onGround();
		if (!ready) {
			moves.coil = 0;
		} else if (player.isShiftKeyDown() && ground) {
			moves.coil = Math.min(COIL_MAX, moves.coil + 1);
		} else if (moves.coil > 0) {
			moves.coil--;
		}
		if (ready && moves.wasGround && !ground && !moves.pouncing && player.getDeltaMovement().y > 0.2 && moves.coil >= COIL_MIN && now >= moves.readyAt
			&& !player.isPassenger() && !player.isInWater()) {
			double ratio = (double)moves.coil / COIL_MAX;
			Vec3 look = player.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0.0, look.z);
			flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
			double power = LEAP_LOW + (LEAP_HIGH - LEAP_LOW) * ratio;
			player.setDeltaMovement(flat.x * power, LIFT_LOW + (LIFT_HIGH - LIFT_LOW) * ratio, flat.z * power);
			player.syncVelocity = true;
			player.causeFoodExhaustion(1.5F);
			moves.pouncing = true;
			moves.pounceAt = now;
			moves.readyAt = now + POUNCE_COOLDOWN;
			moves.coil = 0;
			level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 0.9F, 0.7F);
			Fx.ring(level, player.position(), 1.5F, MEAL_COLOUR);
		}
		if (moves.pouncing) {
			LivingEntity prey = null;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(LAND_REACH * 0.5, 0.5, LAND_REACH * 0.5),
				e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && player.canAttack(e) && !player.isPassengerOfSameVehicle(e))) {
				if (prey == null || e.distanceToSqr(player) < prey.distanceToSqr(player)) {
					prey = e;
				}
			}
			if (prey != null && now - moves.pounceAt > 2) {
				pin(player, level, prey);
				moves.pouncing = false;
			} else if (ground && now - moves.pounceAt > 4 || now - moves.pounceAt > POUNCE_FLIGHT) {
				moves.pouncing = false;
			}
		}
		moves.wasGround = ground;
	}

	/** They land on {@code prey}: a bite of damage, and it is held down. */
	private static void pin(ServerPlayer player, ServerLevel level, LivingEntity prey) {
		float bonus = big(player) ? (float)BIG_DAMAGE : 0.0F;
		prey.hurtServer(level, level.damageSources().playerAttack(player), PIN_DAMAGE + bonus);
		prey.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PIN_TICKS, PIN_SLOW, false, true, true));
		prey.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, PIN_TICKS, 1, false, true, true));
		if (prey instanceof net.minecraft.world.entity.Mob mob) {
			mob.getNavigation().stop();
			mob.setTarget(null);
		}
		player.setDeltaMovement(Vec3.ZERO);
		player.syncVelocity = true;
		level.playSound(null, prey.blockPosition(), SoundEvents.RAVAGER_ATTACK, SoundSource.PLAYERS, 1.0F, 0.7F);
		Fx.blast(level, prey.position().add(0.0, prey.getBbHeight() * 0.5, 0.0), 1.2F, MEAL_COLOUR);
	}

	/** Crouching by the corpse of something they killed: each second of it is a bite; at the end the meal is theirs. */
	private static void devour(ServerPlayer player, ServerLevel level, Moves moves, long now) {
		List<Kill> kills = KILLS.get(player.getUUID());
		if (kills == null || kills.isEmpty() || big(player)) {
			moves.devour = 0;
			return;
		}
		kills.removeIf(k -> now - k.when() > MEMORY_TICKS);
		Kill meal = null;
		if (player.isShiftKeyDown() && player.onGround()) {
			for (Kill k : kills) {
				if (player.position().distanceToSqr(k.at()) > DEVOUR_REACH * DEVOUR_REACH) {
					continue;
				}
				boolean dead = KitchenCompat.corpsesExist() ? KitchenCompat.freeCorpseNear(level, k.at(), CORPSE_SLACK) != null : now - k.when() <= FRESH_TICKS;
				if (dead) {
					meal = k;
					break;
				}
			}
		}
		if (meal == null) {
			moves.devour = 0;
			return;
		}
		moves.devour++;
		if (moves.devour % 10 == 0) {
			level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 0.9F, 0.6F);
			level.sendParticles(Fx.mote(MEAL_COLOUR, 0.5F), meal.at().x, meal.at().y + 0.3, meal.at().z, 4, 0.3, 0.2, 0.3, 0.0);
		}
		if (moves.devour >= DEVOUR_TICKS) {
			moves.devour = 0;
			kills.remove(meal);
			level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.8F, 0.5F);
			player.heal(2.0F);
			feed(player, Math.max(FEED_MIN, Math.min(FEED_MAX, Math.round(meal.health() * 2.0F))));
		}
	}

	/** The action bar: the meal gauge (red, or gold and shrinking while grown), or the coil (grey), as {@code =} and {@code -} only. */
	private static void gauge(ServerPlayer player, Moves moves, boolean big, long now) {
		final int cells = 10;
		MutableComponent text = null;
		if (big) {
			int left = (int)Math.ceil((player.getAttachedOrElse(BIG_UNTIL, 0L) - now) * cells / (double)BIG_TICKS);
			text = bar(left, cells, ChatFormatting.GOLD);
		} else if (moves.devour > 0) {
			text = bar(moves.devour * cells / DEVOUR_TICKS, cells, ChatFormatting.DARK_RED);
		} else if (moves.coil >= 1 && player.isShiftKeyDown()) {
			text = bar(moves.coil * cells / COIL_MAX, cells, ChatFormatting.GRAY);
		} else {
			int gauge = player.getAttachedOrElse(GAUGE, 0);
			if (gauge > 0 && (player.isSprinting() || gauge >= FULL / 2)) {
				text = bar(gauge * cells / FULL, cells, ChatFormatting.RED);
			}
		}
		if (text != null) {
			player.sendOverlayMessage(text);
		}
	}

	private static MutableComponent bar(int filled, int cells, ChatFormatting colour) {
		int full = Math.max(0, Math.min(cells, filled));
		return Component.literal("=".repeat(full)).withStyle(colour).append(Component.literal("-".repeat(cells - full)).withStyle(ChatFormatting.DARK_GRAY));
	}

	// ---- the grown form's gifts ----

	private static void bigGifts(ServerPlayer player) {
		set(player, Attributes.SCALE, SIZE_ID, BIG_SCALE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		set(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, BIG_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.MAX_HEALTH, HEALTH_ID, BIG_HEALTH, AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.ARMOR, ARMOUR_ID, BIG_ARMOUR, AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.KNOCKBACK_RESISTANCE, KNOCK_ID, BIG_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE);
		set(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, BIG_REACH, AttributeModifier.Operation.ADD_VALUE);
	}

	/** Takes the grown form's gifts off. */
	private static void strip(ServerPlayer player) {
		for (Map.Entry<Holder<Attribute>, Identifier> e : List.of(Map.entry(Attributes.SCALE, SIZE_ID), Map.entry(Attributes.ATTACK_DAMAGE, DAMAGE_ID),
			Map.entry(Attributes.MAX_HEALTH, HEALTH_ID), Map.entry(Attributes.ARMOR, ARMOUR_ID), Map.entry(Attributes.KNOCKBACK_RESISTANCE, KNOCK_ID),
			Map.entry(Attributes.ENTITY_INTERACTION_RANGE, REACH_ID))) {
			AttributeInstance instance = player.getAttribute(e.getKey());
			if (instance != null) {
				instance.removeModifier(e.getValue());
			}
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static void set(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null && instance.getModifier(id) == null) {
			instance.addTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}
}
