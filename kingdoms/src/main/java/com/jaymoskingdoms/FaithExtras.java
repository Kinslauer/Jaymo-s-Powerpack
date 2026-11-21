package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Class trees patch 8, the extras of the faithful and the wild (docs/ClassTrees-Plan.md).
 *
 * <p><b>Druid.</b> Beast Tongue (beasts of a kind they have studied neither flee nor hunt them), Green Thumb (crops and
 * saplings by where they slept grow a stage overnight), the second crossroads Thick Hide (in a shape, blows under three
 * are shrugged off) or Swift Paws (in a shape, a fifth faster and a longer leap), and Wild Heart (once a rest, a blow that
 * would bring them down in a shape throws them back into their own body at the health they took it with).
 *
 * <p><b>Cleric.</b> Steady Hands (a hit doesn't break a held prayer), Candle Bearer (the raised symbol lights the way and
 * slows the dead near it), the second crossroads Shepherd (the blessing reaches every friend within 12 blocks) or Exorcist
 * (Turn the Undead also drives out possession and the living nightmare and unmasks a shapeshifter), and Intercession (once
 * a rest, a friend going down within eight blocks stands back up on two hearts).
 *
 * <p><b>Monk.</b> Light Step (no harm from a fall under eight blocks, no crop trampled), Iron Palm (bare hands break wood,
 * then stone, as an iron tool would, a little slower) and The Fast (hunger half as fast).
 *
 * <p><b>Paladin.</b> Steadfast (no knockback while blocking), the second crossroads Avenger (a friend going down within 12
 * blocks makes the next smite in ten seconds free) or Redeemer (a foe brought down and spared never turns on them again),
 * and Beacon (friends in the aura can't be frightened or Overcome).
 */
public final class FaithExtras {
	// ---- Druid ----
	/** Beast Tongue: how far a studied beast that hunts them is called off (blocks), and how often. */
	static final double TONGUE_RANGE = 20.0;
	static final int TONGUE_EVERY = 10;
	/** Green Thumb: how far from where they slept (blocks). */
	static final int THUMB_RANGE = 6;
	/** Thick Hide: blows under this are shrugged off. Swift Paws: the share faster, and the leap's gain. */
	static final float HIDE_UNDER = 3.0F;
	static final double PAWS_SPEED = 0.2, PAWS_LEAP = 0.12;
	// ---- Cleric ----
	/** Candle Bearer: how far the dead are slowed (blocks), how long each pulse of it lasts (ticks), and how often it comes. */
	static final double CANDLE_RANGE = 6.0;
	static final int CANDLE_SLOW = 40, CANDLE_EVERY = 10;
	/** Shepherd: how far the blessing reaches. */
	static final double SHEPHERD_RANGE = 12.0;
	/** Intercession: how near the friend must fall, how far friends are watched (so one who fell far off isn't raised by walking up), and the health they stand on. */
	static final double INTER_RANGE = 8.0, WATCH_RANGE = 24.0;
	static final float INTER_HEALTH = 4.0F;
	static final int WATCH_EVERY = 5;
	// ---- Monk ----
	/** Light Step: falls under this many blocks do no harm (vanilla's are three). */
	static final double STEP_FALL = 8.0, PLAIN_FALL = 3.0;
	/** Iron Palm: an iron tool's speed, and how much of it the bare hand has. */
	static final float PALM_IRON = 6.0F, PALM_SHARE = 0.8F;
	/** The Fast: what hunger's drain is multiplied by. */
	static final float FAST = 0.5F;
	// ---- Paladin ----
	/** Avenger: how near the friend must fall (blocks), and how long the free smite waits (ticks). */
	static final double AVENGE_RANGE = 12.0;
	static final int AVENGE_TICKS = 200;
	/** Redeemer: how far a spared foe is kept from turning on them (blocks), and how often it is looked to (ticks). */
	static final double REDEEM_RANGE = 24.0;
	static final int REDEEM_EVERY = 2;

	public static final String TONGUE = "dru_tongue", THUMB = "dru_thumb", THICK_HIDE = "dru_hide", SWIFT_PAWS = "dru_paws", WILD_HEART = "dru_heart";
	public static final String STEADY = "cle_steady", CANDLE = "cle_candle", SHEPHERD = "cle_shepherd", EXORCIST = "cle_exorcist", INTERCESSION = "cle_inter";
	public static final String LIGHT_STEP = "monk_step", IRON_PALM = "monk_palm", FAST_KEY = "monk_fast";
	public static final String STEADFAST = "pal_stead", AVENGER = "pal_avenger", REDEEMER = "pal_redeemer", BEACON = "pal_beacon";
	/** The once-a-rest pools (any rest brings them back, {@link Rests}). */
	static final String HEART_POOL = "wild_heart", INTER_POOL = "intercession";
	static final String REDEEMED_TAG = "jaymoskingdoms.redeemed.";

	/** Wild Heart: the health each Druid took their shape with. */
	private static final Map<UUID, Float> TOOK = new HashMap<>();
	/** Who was seen lying downed, and when last (game time): a friend not in here who is down has just gone down. */
	private static final Map<UUID, Long> DOWN = new HashMap<>();
	/** Avenger: until when the next smite is free. */
	private static final Map<UUID, Long> AVENGE = new HashMap<>();

	private FaithExtras() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(FaithExtras::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			TOOK.clear();
			DOWN.clear();
			AVENGE.clear();
		});
		// Wild Heart: before Jaymo's Powers' own downing (which waits to the last)
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> !(entity instanceof ServerPlayer druid && wildHeart(druid)));
		// Redeemer: a foe a Redeemer's blow leaves downed and living is marked theirs
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof Mob mob && source.getEntity() instanceof ServerPlayer paladin && Downed.isDowned(mob) && OathGifts.has(paladin, REDEEMER)) {
				mob.addTag(REDEEMED_TAG + paladin.getUUID());
			}
		});
	}

	/** A friend of {@code player}'s: another player, a villager or settlement folk, a pet, a golem, or one who takes their orders; never a monster. */
	static boolean friend(ServerPlayer player, LivingEntity e) {
		if (e == player || e instanceof Enemy) {
			return false;
		}
		return e instanceof Player || e instanceof Villager || e instanceof Folk || e instanceof TamableAnimal pet && pet.getOwnerReference() != null
			|| e instanceof AbstractGolem || Followers.takesOrders(e, player);
	}

	/**
	 * Whether {@code attacker} leaves {@code target} be (asked by every mob's targeting): one a Redeemer brought down and spared
	 * never goes for that Redeemer again, and a beast of a studied kind doesn't hunt a Druid who speaks its tongue unless the Druid
	 * struck it first.
	 */
	public static boolean spares(LivingEntity attacker, LivingEntity target) {
		if (!(target instanceof Player player) || !(attacker instanceof Mob mob)) {
			return false;
		}
		if (mob.entityTags().contains(REDEEMED_TAG + player.getUUID()) && player instanceof ServerPlayer paladin && OathGifts.has(paladin, REDEEMER)) {
			return true;
		}
		return mob.getLastHurtByMob() != player && beastFriend(player, mob);
	}

	// ---- Druid ----------------------------------------------------------------------------------------------------------------

	/** Beast Tongue: {@code beast} is of a kind {@code player} has studied (a shape's kin), and they speak its tongue. */
	public static boolean beastFriend(Player player, Entity beast) {
		return ClassTrees.has(player, TONGUE) > 0 && studied(player, FoeStudy.kind(beast));
	}

	static boolean studied(Player player, String kind) {
		for (String form : Druids.FORMS) {
			List<String> kin = Druids.KIN.get(form);
			if (kin != null && kin.contains(kind) && Druids.knows(player, form)) {
				return true;
			}
		}
		return false;
	}

	/** Green Thumb: {@code player} wakes from a night's sleep; the crops and saplings round their bed have grown a stage. */
	static void woke(ServerPlayer player) {
		if (ClassTrees.has(player, THUMB) <= 0 || !(player.level() instanceof ServerLevel level)) {
			return;
		}
		int reach = Math.max(1, Math.round(THUMB_RANGE * Math.min(1.0F, ClassTrees.power(player, THUMB))));
		BlockPos bed = player.blockPosition();
		int grown = 0;
		for (BlockPos pos : BlockPos.betweenClosed(bed.offset(-reach, -2, -reach), bed.offset(reach, 2, reach))) {
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof CropBlock crop && !crop.isMaxAge(state)) {
				level.setBlock(pos, crop.getStateForAge(crop.getAge(state) + 1), 2);
			} else if (state.getBlock() instanceof StemBlock && state.getValue(StemBlock.AGE) < StemBlock.MAX_AGE) {
				level.setBlock(pos, state.setValue(StemBlock.AGE, state.getValue(StemBlock.AGE) + 1), 2);
			} else if (state.getBlock() instanceof SaplingBlock sapling) {
				sapling.advanceTree(level, pos.immutable(), state, level.getRandom());
			} else {
				continue;
			}
			grown++;
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.25, 0.25, 0.25, 0.0);
		}
		if (grown > 0) {
			level.playSound(null, bed, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 0.6F, 1.0F);
		}
	}

	/** Wild Heart: a shape is taken; the health they had is remembered ({@link Druids}). */
	static void shapeTaken(ServerPlayer player) {
		TOOK.put(player.getUUID(), player.getHealth());
	}

	/** Wild Heart: the blow that would bring {@code druid} down in a shape throws them back into their own body instead. True if it did. */
	private static boolean wildHeart(ServerPlayer druid) {
		if (!Druids.shaped(druid) || ClassTrees.has(druid, WILD_HEART) <= 0 || !Rests.spend(druid, HEART_POOL, 1, 1)) {
			return false;
		}
		Float had = TOOK.remove(druid.getUUID());
		Druids.end(druid, false);
		druid.setHealth(Math.max(1.0F, Math.min(druid.getMaxHealth(), (had == null ? druid.getMaxHealth() / 2.0F : had) * Math.min(1.0F, ClassTrees.power(druid, WILD_HEART)))));
		druid.setInvulnerableTime(20);
		ServerLevel level = druid.level();
		Fx.ring(level, druid.position().add(0.0, 0.1, 0.0), 2.0F, 0x6FBF4A);
		level.playSound(null, druid.getX(), druid.getY(), druid.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.5F, 1.4F);
		druid.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.extras.wild_heart"));
		return true;
	}

	// ---- Cleric ---------------------------------------------------------------------------------------------------------------

	/** Steady Hands: a hit doesn't break {@code who}'s held prayer ({@link Concentration}). */
	static boolean steady(LivingEntity who) {
		return who instanceof Player player && ClassTrees.has(player, STEADY) > 0 && who.getRandom().nextFloat() < Math.min(1.0F, ClassTrees.power(player, STEADY));
	}

	/** Candle Bearer: the symbol is raised ({@link Clerics#raised}); it lights the way, and the dead near it are slowed. */
	static void candle(ServerPlayer player, ServerLevel level, int held) {
		if (held % CANDLE_EVERY != 0 || ClassTrees.has(player, CANDLE) <= 0) {
			return;
		}
		Cantrips.carried(player, true);
		double range = CANDLE_RANGE * Math.min(1.0F, ClassTrees.power(player, CANDLE));
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range), m -> m.isAlive() && Restoration.undead(m))) {
			if (mob.distanceToSqr(player) <= range * range) {
				mob.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, CANDLE_SLOW, 1, true, true, true), player);
			}
		}
	}

	/** Shepherd: how far {@code cleric}'s blessing reaches. */
	static double blessRange(ServerPlayer cleric) {
		return Math.max(Clerics.BLESS_RANGE, SHEPHERD_RANGE * Math.min(1.0F, ClassTrees.power(cleric, SHEPHERD)));
	}

	/** Shepherd: whether {@code cleric}'s blessing falls on {@code e} beyond their band. */
	static boolean shepherded(ServerPlayer cleric, LivingEntity e) {
		return ClassTrees.has(cleric, SHEPHERD) > 0 && friend(cleric, e);
	}

	/**
	 * Exorcist: a pulse of the raised symbol ({@link Clerics}) over those before {@code cleric}, along {@code look}: a held
	 * mind is let go, the living nightmare is lifted (their own too), and a shapeshifter's stolen face comes off.
	 */
	static void exorcise(ServerPlayer cleric, ServerLevel level, Vec3 look) {
		if (ClassTrees.has(cleric, EXORCIST) <= 0) {
			return;
		}
		boolean any = LivingNightmare.cure(cleric);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, cleric.getBoundingBox().inflate(Clerics.TURN_RANGE), e -> e != cleric && e.isAlive())) {
			Vec3 to = e.position().subtract(cleric.position()).multiply(1.0, 0.0, 1.0);
			if (to.lengthSqr() > Clerics.TURN_RANGE * Clerics.TURN_RANGE || to.lengthSqr() < 1.0E-4 || look.dot(to.normalize()) < Clerics.CONE_COS || !cleric.hasLineOfSight(e)) {
				continue;
			}
			boolean driven = false;
			if (e instanceof Mob mob && Possession.held(mob)) {
				Possession.release(mob, false);
				driven = true;
			}
			if (e instanceof ServerPlayer other && LivingNightmare.cure(other)) {
				driven = true;
			}
			if (e instanceof Villager copy && Doubles.isCopy(copy)) {
				Doubles.expose(level, copy, cleric);
				driven = true;
			}
			if (driven) {
				any = true;
				Fx.beam(level, cleric.getEyePosition().add(0.0, -0.3, 0.0), e.getBoundingBox().getCenter(), 0.05F, 0xFFF4D0, 8);
				level.sendParticles(ParticleTypes.SOUL, e.getX(), e.getY(1.0), e.getZ(), 8, 0.3, 0.4, 0.3, 0.03);
			}
		}
		if (any) {
			level.playSound(null, cleric.getX(), cleric.getY(), cleric.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 0.7F, 1.5F);
		}
	}

	// ---- Monk -----------------------------------------------------------------------------------------------------------------

	/** Light Step: {@code player} never tramples a crop. */
	public static boolean lightStep(Player player) {
		return ClassTrees.has(player, LIGHT_STEP) > 0;
	}

	/** Iron Palm: whether {@code player}'s bare hand breaks {@code state} as an iron tool would (wood; stone at the second rank). Both sides. */
	public static boolean palm(Player player, BlockState state) {
		if (!player.getMainHandItem().isEmpty()) {
			return false;
		}
		int rank = ClassTrees.has(player, IRON_PALM);
		return rank >= 1 && state.is(BlockTags.MINEABLE_WITH_AXE)
			|| rank >= 2 && state.is(BlockTags.MINEABLE_WITH_PICKAXE) && !state.is(BlockTags.INCORRECT_FOR_IRON_TOOL);
	}

	/** Iron Palm: the speed {@code player}'s hand breaks {@code state} at, from the bare hand's {@code speed}. */
	public static float palmSpeed(Player player, BlockState state, float speed) {
		return palm(player, state) ? Math.max(speed, PALM_IRON * PALM_SHARE) : speed;
	}

	/** The Fast: what {@code player}'s hunger drain is multiplied by. */
	public static float fast(Player player) {
		return 1.0F - (1.0F - FAST) * Math.min(1.0F, ClassTrees.power(player, FAST_KEY));
	}

	// ---- Paladin --------------------------------------------------------------------------------------------------------------

	/** Avenger: whether {@code paladin} has a friend to avenge right now (their smite may be readied with none left). */
	static boolean avenging(ServerPlayer paladin) {
		return AVENGE.getOrDefault(paladin.getUUID(), 0L) >= paladin.level().getGameTime();
	}

	/** Avenger: the smite is struck; true if it was the free one (and that is used up). */
	static boolean avenged(ServerPlayer paladin) {
		return avenging(paladin) && AVENGE.remove(paladin.getUUID()) != null;
	}

	// ---- blows ----------------------------------------------------------------------------------------------------------------

	/** Thick Hide: in a shape, a blow under three is shrugged off. */
	static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (target instanceof ServerPlayer druid && source.getEntity() != null && source.getEntity() != druid && Druids.shaped(druid)
			&& amount < HIDE_UNDER * Math.min(1.0F, ClassTrees.power(druid, THICK_HIDE))) {
			return 0.0F;
		}
		return amount;
	}

	// ---- every tick -----------------------------------------------------------------------------------------------------------

	/** {@link Skills#modifier}, only when the amount has changed (so a held modifier isn't sent to the client every tick). */
	private static void set(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, String id, double amount,
		AttributeModifier.Operation operation) {
		net.minecraft.world.entity.ai.attributes.AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		AttributeModifier now = instance.getModifier(JaymosKingdoms.id(id));
		if (now == null ? amount != 0.0 : now.amount() != amount) {
			Skills.modifier(player, attribute, id, amount, operation);
		}
	}

	private static void tick(MinecraftServer server) {
		long tick = server.getTickCount();
		List<LivingEntity> fell = tick % WATCH_EVERY == 0 ? new ArrayList<>() : null;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!player.isAlive() || player.isSpectator() || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			long now = level.getGameTime();
			// Druid: Swift Paws while a shape is worn
			double paws = Druids.shaped(player) ? Math.min(1.0F, ClassTrees.power(player, SWIFT_PAWS)) : 0.0;
			set(player, Attributes.MOVEMENT_SPEED, "dru_paws_speed", PAWS_SPEED * paws, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			set(player, Attributes.JUMP_STRENGTH, "dru_paws_leap", PAWS_LEAP * paws, AttributeModifier.Operation.ADD_VALUE);
			if ((now + player.getId()) % TONGUE_EVERY == 0 && ClassTrees.has(player, TONGUE) > 0) {
				// Beast Tongue: a studied beast that hunts them (unprovoked) is called off
				for (Mob beast : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(TONGUE_RANGE),
					b -> b.isAlive() && b.getTarget() == player && b.getLastHurtByMob() != player && studied(player, FoeStudy.kind(b)))) {
					beast.setTarget(null);
				}
			}
			// Monk: Light Step
			set(player, Attributes.SAFE_FALL_DISTANCE, "monk_step_fall", (STEP_FALL - PLAIN_FALL) * Math.min(1.0F, ClassTrees.power(player, LIGHT_STEP)),
				AttributeModifier.Operation.ADD_VALUE);
			// Paladin: Steadfast while the guard is up
			set(player, Attributes.KNOCKBACK_RESISTANCE, "pal_steadfast", player.isBlocking() ? Math.min(1.0F, ClassTrees.power(player, STEADFAST)) : 0.0,
				AttributeModifier.Operation.ADD_VALUE);
			if ((now + player.getId()) % REDEEM_EVERY == 0 && OathGifts.has(player, REDEEMER)) {
				String tag = REDEEMED_TAG + player.getUUID();
				for (Mob spared : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(REDEEM_RANGE), m -> m.isAlive() && m.entityTags().contains(tag))) {
					if (spared.getTarget() == player) {
						spared.setTarget(null);
						spared.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
					}
					if (spared.getLastHurtByMob() == player) {
						spared.setLastHurtByMob(null);
					}
				}
			}
			if (now % 20 == 0 && OathGifts.has(player, BEACON) && OathGifts.has(player, OathGifts.AURA)) {
				// Beacon: fear and a faint of it are lifted from friends in the aura (and can't take hold: {@link OathGifts#courage})
				double r = Paladins.AURA_RANGE * OathGifts.auraScale(player);
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(r), e -> e.isAlive() && !(e instanceof Enemy))) {
					if (e.distanceToSqr(player) > r * r) {
						continue;
					}
					Fear.calm(e);
					if (e instanceof ServerPlayer friend && PowersCompat.faintedOfOvercome(friend)) {
						PowersCompat.bringRound(friend);
					}
				}
			}
			if (fell != null && (ClassTrees.has(player, INTERCESSION) > 0 || ClassTrees.has(player, AVENGER) > 0)) {
				// friends watched for the moment they go down
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(WATCH_RANGE), e -> e.isAlive() && friend(player, e))) {
					if (!Downed.isDowned(e)) {
						DOWN.remove(e.getUUID());
					} else if (DOWN.put(e.getUUID(), now) == null && !fell.contains(e)) {
						fell.add(e);
					}
				}
			}
		}
		if (fell != null && DOWN.size() > 512) {
			long old = server.overworld().getGameTime() - 12000L;
			DOWN.values().removeIf(seen -> seen < old);
		}
		if (fell == null || fell.isEmpty()) {
			return;
		}
		for (LivingEntity friend : fell) {
			if (!(friend.level() instanceof ServerLevel level)) {
				continue;
			}
			boolean up = false;
			for (ServerPlayer player : level.players()) {
				if (player == friend || !player.isAlive() || Downed.isDowned(player) || !friend(player, friend)) {
					continue;
				}
				double d = player.distanceToSqr(friend);
				if (d <= AVENGE_RANGE * AVENGE_RANGE && OathGifts.has(player, AVENGER)) {
					AVENGE.put(player.getUUID(), level.getGameTime() + Math.round(AVENGE_TICKS * Math.min(1.0F, ClassTrees.power(player, AVENGER))));
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.extras.avenge", friend.getDisplayName()));
					level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 0.6F, 0.7F);
				}
				if (!up && d <= INTER_RANGE * INTER_RANGE && ClassTrees.has(player, INTERCESSION) > 0 && Rests.left(player, INTER_POOL, 1) > 0) {
					PowersCompat.getUp(level, friend);
					if (Downed.isDowned(friend)) {
						// held down, pulled apart or halved: no prayer stands them up, and none is spent
						continue;
					}
					Rests.spend(player, INTER_POOL, 1, 1);
					up = true;
					friend.setHealth(Math.max(friend.getHealth(), Math.min(friend.getMaxHealth(), INTER_HEALTH * Math.min(1.0F, ClassTrees.power(player, INTERCESSION)))));
					DOWN.remove(friend.getUUID());
					Fx.beam(level, player.getEyePosition().add(0.0, -0.3, 0.0), friend.getBoundingBox().getCenter(), 0.06F, 0xFFF4D0, 10);
					Fx.ring(level, friend.position().add(0.0, 0.1, 0.0), 1.4F, 0xFFF4D0);
					level.playSound(null, friend.getX(), friend.getY(), friend.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.9F, 1.4F);
					player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.extras.intercession", friend.getDisplayName()));
				}
			}
		}
	}
}
