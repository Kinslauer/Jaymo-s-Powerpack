package com.jaymoskingdoms;

import com.jaymoskingdoms.monsters.Ogre;
import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.429) Havens patch 2, the king's guard and the trials (ideas from King's Monsters, ARR, and Shadow of War: our own
 * names and code). The biggest of their kind stand at every goblin king's and orc warchief's door ({@link #KINGS_GUARD}),
 * as two-headed ogres stand at an Ogre King's gate ({@link OgreHolds}). Ask one of them to see the king ("Ask to see the
 * king", or for an ogre, an empty hand held out) and you're set one trial before the throne:
 * <ul>
 * <li><b>Orcs, the pit fight:</b> their champion, in the fighting pit, until one of you is down.</li>
 * <li><b>Goblins, the trap hall:</b> from the door to the king's throne within {@link #HALL_SECONDS} seconds while the hall's
 * traps go off round you (darts, caltrops, nets).</li>
 * <li><b>Ogres, the stone-lifting brawl:</b> heave the stone (hold sneak, {@link #LIFT_TICKS}), then knock the guard down to
 * half with your bare fists (a weapon ends it).</li>
 * </ul>
 * The rest of the camp holds off while you're at it, and nobody dies of a trial: below {@link #SAFE_HEALTH} you're beaten
 * and let go. Pass and the tribe's grudge is wiped with a truce (the ogres of the region leave you be for as long), and the
 * king sends a gift. Once a trial is passed, that tribe sets no other for {@link #AGAIN_DAYS} days.
 */
public final class Trials {
	// ================================================================================================================
	// Tuning
	/** How often camps' guards are seen to (ticks); how many a king has at his door; how much bigger and tougher than their kind. */
	static final int GUARD_EVERY = 200, GUARDS = 2;
	static final double GUARD_SCALE = 1.35, GUARD_HEALTH = 2.0;
	/** A fallen guard is replaced after this many days, never with a player this near. */
	static final int GUARD_DAYS = 1;
	static final double GUARD_HIDE = 24.0;
	/** Nobody dies of a trial: below this much health you're beaten. */
	static final float SAFE_HEALTH = 5.0F;
	/** How far a challenger may stray before the trial's forfeit. */
	static final double STRAY = 48.0;
	/** The pit fight: the champion's size and health, how long it may run (seconds). */
	static final double CHAMPION_SCALE = 1.25, CHAMPION_HEALTH = 60.0;
	static final int PIT_SECONDS = 180;
	/** The trap hall: how long to reach the throne (seconds), how near it counts, a trap every so many ticks, a dart's damage. */
	static final int HALL_SECONDS = 60, TRAP_TICKS = 30;
	static final double THRONE_REACH = 3.0;
	static final float DART_DAMAGE = 2.0F, CALTROP_DAMAGE = 1.0F;
	/** The brawl: sneak this long by the stone (ticks) within so many seconds, then the guard down to this share with fists. */
	static final int LIFT_TICKS = 100, LIFT_SECONDS = 20, BRAWL_SECONDS = 120;
	static final double LIFT_REACH = 5.0;
	static final float BRAWL_DOWN = 0.5F;
	/** After a trial's passed, a tribe sets no other for this many days; the king's gift (emeralds). */
	static final int AGAIN_DAYS = 3, GIFT = 8;
	// ================================================================================================================

	/** MCA's talk answer on a king's guard. */
	public static final String ANSWER = "jaymoskingdoms_kings_trial";
	/** Tags: a king's guard; a pit champion. */
	static final String KINGS_GUARD = "jaymoskingdoms.kings_guard", CHAMPION = "jaymoskingdoms.trial_champion";

	/** On the Overworld: camp id → the day its guard was last mustered. Saved. */
	static final AttachmentType<Map<String, Long>> MUSTERED = AttachmentRegistry.create(JaymosKingdoms.id("kings_guard_mustered"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)));
	/** On a player: tribe (or ogre region "ogre@x,z") → game time a trial of theirs was passed. Saved. */
	static final AttachmentType<Map<String, Long>> PASSED = AttachmentRegistry.create(JaymosKingdoms.id("trials_passed"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.LONG)).copyOnDeath());

	enum Kind {
		PIT, HALL, BRAWL
	}

	/** One trial under way. */
	private static final class Trial {
		final Kind kind;
		final UUID player;
		/** The tribe (or ogre region) it's for, the camp (−1 for ogres). */
		final String tribe;
		final int camp;
		final BlockPos at;
		final long started;
		long ends;
		/** The champion (pit), the king (hall), the guard (brawl). */
		@Nullable UUID other;
		int lifted;
		boolean brawling;

		Trial(Kind kind, UUID player, String tribe, int camp, BlockPos at, long started, int seconds) {
			this.kind = kind;
			this.player = player;
			this.tribe = tribe;
			this.camp = camp;
			this.at = at;
			this.started = started;
			this.ends = started + seconds * 20L;
		}
	}

	private static final Map<UUID, Trial> TRIALS = new HashMap<>();
	private static boolean redealing;

	private Trials() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			ServerLevel level = Factions.level(server);
			if (server.getTickCount() % GUARD_EVERY == 0) {
				guards(level);
			}
			if (!TRIALS.isEmpty()) {
				tick(level);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> TRIALS.clear());
		ServerLivingEntityEvents.ALLOW_DAMAGE.register(Trials::allowDamage);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(entity instanceof Ogre ogre) || !OgreHolds.guard(ogre)
				|| !player.getMainHandItem().isEmpty()) {
				return InteractionResult.PASS;
			}
			brawl(sp, ogre);
			return InteractionResult.SUCCESS;
		});
	}

	// ---- the king's guard ----------------------------------------------------------------------------------------------

	/** {@code e} is one of a king's guard. */
	public static boolean kingsGuard(Entity e) {
		return e.entityTags().contains(KINGS_GUARD);
	}

	/** A camp's king's throne, or null for no goblin village or orc camp. */
	private static @Nullable BlockPos throne(MinecraftServer server, int camp) {
		Camps.CampData data = Camps.data(server, camp);
		if (data == null) {
			return null;
		}
		return Camps.orc(server, camp) ? OrcBuildings.throne(data.center(), data.gate())
			: Camps.goblin(server, camp) ? GoblinBuildings.throne(data.center(), data.gate()) : null;
	}

	/** Every loaded goblin village and orc camp keeps {@link #GUARDS} at its king's door (a fallen one replaced in a day). */
	private static void guards(ServerLevel level) {
		MinecraftServer server = level.getServer();
		long day = level.getGameTime() / 24000L;
		Map<String, Long> mustered = level.getAttachedOrElse(MUSTERED, Map.of());
		Map<String, Long> fresh = null;
		for (Faction camp : Factions.all(server, FactionType.BARBARIAN)) {
			Camps.CampData data = camp.fallen() ? null : Camps.data(server, camp.id());
			BlockPos throne = data == null || data.clearedAt() >= 0 ? null : throne(server, camp.id());
			if (throne == null || !level.isLoaded(throne) || !Storage.loaded(level, camp)
				|| day - mustered.getOrDefault(Integer.toString(camp.id()), -GUARD_DAYS - 1L) < GUARD_DAYS
				|| level.getNearestPlayer(throne.getX(), throne.getY(), throne.getZ(), GUARD_HIDE, false) != null) {
				continue;
			}
			int have = Workers.near(level, new AABB(throne).inflate(32.0), w -> w.factionId() == camp.id() && kingsGuard(w.mob()) && w.mob().isAlive()).size();
			if (have >= GUARDS) {
				continue;
			}
			Direction gate = data.gate();
			BlockPos door = throne.relative(gate, 3);
			boolean orcs = Camps.orc(server, camp.id());
			for (int i = have; i < GUARDS; i++) {
				BlockPos post = door.relative(gate.getClockWise(), i == 0 ? 1 : -1);
				PathfinderMob guard = Tribes.muster(level, camp, orcs ? Orcs.GRUNT : Goblins.GOBLIN, post, post);
				if (guard != null) {
					guard.addTag(KINGS_GUARD);
					grow(guard, GUARD_SCALE, GUARD_HEALTH);
					guard.setHomeTo(post, 2);
				}
			}
			if (fresh == null) {
				fresh = new HashMap<>(mustered);
			}
			fresh.put(Integer.toString(camp.id()), day);
		}
		if (fresh != null) {
			level.setAttached(MUSTERED, Map.copyOf(fresh));
		}
	}

	/** {@code mob} the biggest of its kind: {@code scale} times the size, {@code health} times the health. */
	private static void grow(PathfinderMob mob, double scale, double health) {
		AttributeInstance size = mob.getAttribute(Attributes.SCALE);
		if (size != null) {
			size.setBaseValue(size.getBaseValue() * scale);
		}
		AttributeInstance max = mob.getAttribute(Attributes.MAX_HEALTH);
		if (max != null) {
			max.setBaseValue(max.getBaseValue() * health);
			mob.setHealth(mob.getMaxHealth());
		}
	}

	/** (From {@code Workers.step}) a king's guard between fights: at their post by the door. True if this was their step. */
	static boolean guardStep(ServerLevel level, Worker w) {
		PathfinderMob mob = w.mob();
		if (Rulers.target(mob) != null || Rulers.foeNear(level, mob) || McaCompat.busy(mob) || Downed.isDowned(mob) || !mob.hasHome()) {
			return false;
		}
		BlockPos post = mob.getHomePosition();
		if (post.distToCenterSqr(mob.position()) > 2.5 * 2.5) {
			if (mob.tickCount % 20 == 0 || mob.getNavigation().isDone()) {
				mob.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 0.8);
			}
		} else {
			mob.getNavigation().stop();
		}
		return true;
	}

	// ---- setting a trial -----------------------------------------------------------------------------------------------

	/** {@code player} may ask {@code villager} (a goblin king's or a warchief's guard) to see the king. */
	public static boolean offers(ServerPlayer player, LivingEntity villager) {
		return kingsGuard(villager) && !TRIALS.containsKey(player.getUUID()) && Tribes.of(villager) != null;
	}

	/** (From MCA's menu) {@code player} asks {@code guard} to see the king: a trial is set, or not. What the guard says. */
	public static Component ask(ServerPlayer player, LivingEntity guard) {
		String tribe = Tribes.of(guard);
		ServerLevel level = (ServerLevel) guard.level();
		if (tribe == null || !(guard instanceof Worker w)) {
			return Component.empty();
		}
		if (passedLately(player, tribe)) {
			return line("again", guard, player);
		}
		Faction camp = Factions.get(level.getServer(), w.factionId());
		BlockPos throne = camp == null ? null : throne(level.getServer(), camp.id());
		if (camp == null || throne == null) {
			return line("none", guard, player);
		}
		long now = level.getGameTime();
		if (Camps.orc(level.getServer(), camp.id())) {
			Camps.CampData data = Camps.data(level.getServer(), camp.id());
			BlockPos pit = OrcBuildings.pit(data.center(), data.gate());
			PathfinderMob champion = Tribes.muster(level, camp, Orcs.GRUNT, pit, pit);
			if (champion == null) {
				return line("none", guard, player);
			}
			champion.addTag(CHAMPION);
			grow(champion, CHAMPION_SCALE, CHAMPION_HEALTH / Math.max(1.0, champion.getMaxHealth()));
			Trial t = new Trial(Kind.PIT, player.getUUID(), tribe, camp.id(), pit, now, PIT_SECONDS);
			t.other = champion.getUUID();
			TRIALS.put(player.getUUID(), t);
			level.playSound(null, pit, SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 2.0F, 0.8F);
			Speech.say(level, champion, Component.translatable("talk.jaymoskingdoms.trial.champion." + champion.getRandom().nextInt(3)), Speech.RED, 32.0);
			return line("pit", guard, player);
		}
		PathfinderMob king = king(level, camp, throne);
		if (king == null) {
			return line("none", guard, player);
		}
		Trial t = new Trial(Kind.HALL, player.getUUID(), tribe, camp.id(), throne, now, HALL_SECONDS);
		t.other = king.getUUID();
		TRIALS.put(player.getUUID(), t);
		return line("hall", guard, player);
	}

	/** The goblin king (or warchief) of {@code camp}, near his throne, or null. */
	private static @Nullable PathfinderMob king(ServerLevel level, Faction camp, BlockPos throne) {
		for (Worker w : Workers.near(level, new AABB(throne).inflate(32.0), w -> w.factionId() == camp.id() && Greenskins.chief(w.role()) && w.mob().isAlive())) {
			return w.mob();
		}
		return null;
	}

	/** An empty hand held out to an Ogre King's guard: the stone-lifting brawl. */
	private static void brawl(ServerPlayer player, Ogre guard) {
		ServerLevel level = (ServerLevel) guard.level();
		String region = "ogre@" + OgreHolds.region(guard.blockPosition());
		if (TRIALS.containsKey(player.getUUID()) || guard.getTarget() != null) {
			return;
		}
		if (passedLately(player, region)) {
			Speech.say(level, guard, Component.translatable("talk.jaymoskingdoms.trial.ogre.again"), Speech.WHITE, 16.0);
			return;
		}
		Trial t = new Trial(Kind.BRAWL, player.getUUID(), region, -1, guard.blockPosition(), level.getGameTime(), LIFT_SECONDS);
		t.other = guard.getUUID();
		TRIALS.put(player.getUUID(), t);
		level.playSound(null, guard.getX(), guard.getY(), guard.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5F, 0.8F);
		Speech.say(level, guard, Component.translatable("talk.jaymoskingdoms.trial.ogre.lift"), Speech.WHITE, 16.0);
	}

	/** (0.7.429) {@code player} is brawling with {@code ogre} just now. */
	static boolean brawling(net.minecraft.world.entity.player.Player player, Entity ogre) {
		Trial t = TRIALS.get(player.getUUID());
		return t != null && t.kind == Kind.BRAWL && ogre.getUUID().equals(t.other);
	}

	private static boolean passedLately(ServerPlayer player, String tribe) {
		Long at = player.getAttachedOrElse(PASSED, Map.of()).get(tribe);
		return at != null && player.level().getGameTime() - at < 24000L * AGAIN_DAYS;
	}

	// ---- under way -------------------------------------------------------------------------------------------------------

	/**
	 * (From {@code McaCompat.foe}) during a trial: a pit champion goes for his challenger alone; the rest of the camp leaves
	 * the challenger be. Null when no trial says either way.
	 */
	public static @Nullable Boolean foe(LivingEntity mob, LivingEntity target) {
		if (TRIALS.isEmpty()) {
			return null;
		}
		if (mob.entityTags().contains(CHAMPION)) {
			Trial t = TRIALS.get(target.getUUID());
			return t != null && mob.getUUID().equals(t.other);
		}
		Trial t = TRIALS.get(target.getUUID());
		if (t != null && t.camp >= 0 && mob instanceof Worker w && w.factionId() == t.camp) {
			return false;
		}
		return null;
	}

	private static void tick(ServerLevel level) {
		long now = level.getGameTime();
		for (var it = TRIALS.entrySet().iterator(); it.hasNext();) {
			Trial t = it.next().getValue();
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.player);
			Entity other = t.other == null ? null : level.getEntity(t.other);
			String result = null;
			if (player == null || player.level() != level || !player.isAlive() || player.position().distanceTo(Vec3.atCenterOf(t.at)) > STRAY) {
				result = "fled";
			} else if (now > t.ends) {
				result = "time";
			} else {
				result = switch (t.kind) {
					case PIT -> other instanceof LivingEntity c && c.isAlive() && !Downed.isDowned(c) ? null : "won";
					case HALL -> hall(level, t, player, other);
					case BRAWL -> brawlTick(level, t, player, other);
				};
			}
			if (result != null) {
				it.remove();
				end(level, t, player, other, result);
			}
		}
	}

	/** The trap hall: traps round the challenger; at the throne, they've won. */
	private static @Nullable String hall(ServerLevel level, Trial t, ServerPlayer player, @Nullable Entity king) {
		if (!(king instanceof LivingEntity k) || !k.isAlive()) {
			return "time";
		}
		if (player.distanceTo(k) <= THRONE_REACH + k.getBbWidth()) {
			return "won";
		}
		long left = (t.ends - level.getGameTime()) / 20;
		if (level.getGameTime() % 20 == 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.trial.hall", Component.literal(Long.toString(left))).withStyle(ChatFormatting.GOLD));
		}
		if ((level.getGameTime() - t.started) % TRAP_TICKS != 0) {
			return null;
		}
		RandomSource random = level.getRandom();
		switch (random.nextInt(3)) {
			case 0 -> {
				// a dart from the wall
				double angle = random.nextDouble() * Math.PI * 2.0;
				Vec3 from = player.position().add(Math.cos(angle) * 5.0, 1.2, Math.sin(angle) * 5.0);
				Arrow dart = new Arrow(level, from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
				Vec3 to = player.getEyePosition().subtract(from);
				dart.shoot(to.x, to.y, to.z, 1.6F, 4.0F);
				dart.setBaseDamage(DART_DAMAGE);
				dart.pickup = net.minecraft.world.entity.projectile.arrow.AbstractArrow.Pickup.DISALLOWED;
				level.addFreshEntity(dart);
				level.playSound(null, from.x, from.y, from.z, SoundEvents.DISPENSER_LAUNCH, SoundSource.HOSTILE, 1.0F, 1.4F);
			}
			case 1 -> {
				// caltrops underfoot
				player.hurtServer(level, level.damageSources().cactus(), CALTROP_DAMAGE);
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1));
				level.sendParticles(ParticleTypes.CRIT, player.getX(), player.getY() + 0.1, player.getZ(), 10, 0.4, 0.05, 0.4, 0.0);
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.trial.caltrops"));
			}
			default -> {
				// a net drops
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 30, 3));
				level.sendParticles(ParticleTypes.ITEM_COBWEB, player.getX(), player.getY() + 1.5, player.getZ(), 20, 0.5, 0.5, 0.5, 0.0);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOL_PLACE, SoundSource.HOSTILE, 1.0F, 0.8F);
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.trial.net"));
			}
		}
		return null;
	}

	/** The stone-lifting brawl: the lift first (sneak by the guard), then fists until the guard's down to half. */
	private static @Nullable String brawlTick(ServerLevel level, Trial t, ServerPlayer player, @Nullable Entity other) {
		if (!(other instanceof Ogre guard) || !guard.isAlive()) {
			return "time";
		}
		if (!t.brawling) {
			if (player.isShiftKeyDown() && player.distanceTo(guard) <= LIFT_REACH + guard.getBbWidth()) {
				t.lifted++;
				if (t.lifted % 10 == 0) {
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.8F, 0.6F);
				}
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.trial.lift", bar(t.lifted, LIFT_TICKS)));
			}
			if (t.lifted >= LIFT_TICKS) {
				// lifted: now the brawl
				t.brawling = true;
				t.ends = level.getGameTime() + BRAWL_SECONDS * 20L;
				guard.setTarget(player);
				Speech.say(level, guard, Component.translatable("talk.jaymoskingdoms.trial.ogre.brawl"), Speech.RED, 24.0);
				level.playSound(null, guard.getX(), guard.getY(), guard.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 2.0F, 0.9F);
			}
			return null;
		}
		if (guard.getTarget() != player) {
			guard.setTarget(player);
		}
		return guard.getHealth() <= guard.getMaxHealth() * BRAWL_DOWN ? "won" : null;
	}

	private static Component bar(int have, int of) {
		int filled = Math.round(10.0F * have / of);
		return Component.literal("|".repeat(filled)).withStyle(ChatFormatting.GOLD).append(Component.literal("|".repeat(10 - filled)).withStyle(ChatFormatting.DARK_GRAY));
	}

	/** Nobody dies of a trial; the brawl's fists only; a pit champion's blows don't land on anyone but his challenger. */
	private static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
		if (redealing || TRIALS.isEmpty()) {
			return true;
		}
		Entity by = source.getEntity();
		Trial mine = TRIALS.get(victim.getUUID());
		boolean trialBlow = mine != null && (by != null && by.getUUID().equals(mine.other)
			|| mine.kind == Kind.HALL && (by == null && source.getDirectEntity() instanceof Arrow || source.is(net.minecraft.world.damagesource.DamageTypes.CACTUS)));
		if (trialBlow && victim instanceof ServerPlayer player && player.getHealth() - amount < SAFE_HEALTH) {
			// beaten: let go
			TRIALS.remove(player.getUUID());
			end((ServerLevel) player.level(), mine, player, by, "beaten");
			return false;
		}
		if (by instanceof ServerPlayer player) {
			Trial t = TRIALS.get(player.getUUID());
			if (t != null && t.kind == Kind.BRAWL && t.brawling && victim.getUUID().equals(t.other) && !player.getMainHandItem().isEmpty()) {
				// a weapon in a brawl: it's over
				TRIALS.remove(player.getUUID());
				end((ServerLevel) player.level(), t, player, victim, "weapon");
				return false;
			}
			if (t != null && t.kind == Kind.BRAWL && !t.brawling && victim.getUUID().equals(t.other)) {
				// (no hitting before the stone's lifted)
				return false;
			}
		}
		return true;
	}

	/** A trial's end: {@code result} is "won", or how it was lost ("beaten", "fled", "time", "weapon"). */
	private static void end(ServerLevel level, Trial t, @Nullable ServerPlayer player, @Nullable Entity other, String result) {
		if (other instanceof PathfinderMob mob) {
			if (mob.getTarget() == player) {
				mob.setTarget(null);
			}
			if (t.kind == Kind.PIT && mob.isAlive()) {
				// the champion goes back to the fires
				mob.discard();
			}
		}
		if (player == null) {
			return;
		}
		boolean won = "won".equals(result);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.trial." + result).withStyle(won ? ChatFormatting.GOLD : ChatFormatting.GRAY));
		if (!won) {
			return;
		}
		MinecraftServer server = level.getServer();
		Map<String, Long> passed = new HashMap<>(player.getAttachedOrElse(PASSED, Map.of()));
		passed.put(t.tribe, level.getGameTime());
		player.setAttached(PASSED, Map.copyOf(passed));
		if (t.kind == Kind.BRAWL) {
			OgreHolds.truce(player, t.tribe.substring("ogre@".length()));
		} else {
			Tribes.makeTruce(server, player.getUUID(), t.tribe);
		}
		player.getInventory().placeItemBackInInventory(new ItemStack(Items.EMERALD, GIFT), net.minecraft.util.Prediction.SERVER_ONLY);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 0.7F);
		if (other instanceof LivingEntity speaker && speaker.isAlive()) {
			Speech.say(level, speaker, Component.translatable("talk.jaymoskingdoms.trial.passed." + t.kind.name().toLowerCase(java.util.Locale.ROOT)),
				Speech.GOLD, 32.0);
		}
	}

	private static Component line(String what, LivingEntity guard, ServerPlayer player) {
		return Component.translatable("dialogue.chat.jaymoskingdoms.trial." + what + "." + guard.getRandom().nextInt(2), Recollections.nameTo(player, guard));
	}
}
