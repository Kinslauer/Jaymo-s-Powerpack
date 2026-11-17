package com.jaymoskingdoms;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The seven sizes (Andy's Awakening pick "Size changer", 2026-11-17: "Wrap druid into this with his beast forms"). A Druid's
 * wild shape is the body the sizes are worn on: in a shape, the Wild key ({@link ArtKeys}) steps the beast through
 * {@link #SCALES}, a speck to a colossus (the Druid's own body and the stand-in beast are both sized).
 * <ul>
 * <li><b>Seven Sizes</b> ({@link #SIZES}, 3 ranks): tap grows a size, sneak + tap shrinks one; each rank opens one more size
 * each way from the beast's own. Health and blows go with the size.</li>
 * <li><b>Small</b> (the two smallest): use on a bigger creature and you ride it; whatever was hunting you loses you, and the
 * creature goes its own way with you on it.</li>
 * <li><b>Big</b> (the three largest): sprint + tap <b>stomps</b> (everything near is hurt and thrown); hold <b>grabs</b> the
 * smaller creature you look at into your hand, and hold again <b>throws</b> it.</li>
 * <li><b>Wax and Wane</b> ({@link #RAY}), out of a shape: hold the key at a creature and a ray <b>shrinks</b> it a size; sneak
 * + hold <b>grows</b> it (five sizes for others, back to its own in a while). A creature at the smallest size goes into a
 * glass bottle used on it, a <b>jar</b>: used on a block, the jar lets it out again.</li>
 * </ul>
 */
public final class SizeArts {
	// ---- tuning ----
	public static final String SIZES = "dru_sizes", RAY = "dru_ray";
	/** The seven sizes: how many times a body's own size each is, and what each does to health and to blows (a share added). */
	static final double[] SCALES = {0.1, 0.25, 0.5, 1.0, 1.75, 2.75, 4.0};
	static final double[] HEALTH = {-0.6, -0.4, -0.2, 0.0, 0.25, 0.5, 1.0};
	static final double[] DAMAGE = {-0.7, -0.5, -0.25, 0.0, 0.25, 0.6, 1.0};
	static final int NATURAL = 3, SMALL_FROM = 1, BIG_FROM = 4;
	/** Each size past the beast's own: step height and reach (blocks), safe fall (blocks). */
	static final double STEP_PER = 0.5, REACH_PER = 1.5, FALL_PER = 3.0;
	static final int STEP_COOLDOWN = 20;
	/** Riding: the creature must be this many times as tall as you; how far hunters lose you. */
	static final double RIDE_TALLER = 2.0, UNNOTICED_RANGE = 24.0;
	/** Stomp: radius and harm at the first big size, and what each size past it adds; the throw up; cooldown. */
	static final double STOMP_RADIUS = 3.5, STOMP_RADIUS_PER = 1.5, STOMP_LIFT = 0.55;
	static final float STOMP_DAMAGE = 6.0F, STOMP_DAMAGE_PER = 3.0F;
	static final int STOMP_COOLDOWN = 100;
	/** Grab: reach at the first big size (and more each size), how small the grabbed must be next to you, the throw's speed and harm, cooldown. */
	static final double GRAB_REACH = 4.0, GRAB_SMALLER = 0.6, THROW_SPEED = 1.3, THROW_SPEED_PER = 0.3;
	static final float THROW_DAMAGE = 4.0F;
	/** A grabbed creature is held helpless this long (ticks) at a time. A jar holds at most this much of a creature (bytes of its saved self). */
	static final int GRAB_HOLD = 30, JAR_BYTES = 65536;
	static final int GRAB_COOLDOWN = 40;
	/** The ray: reach, the sizes it gives others (and what each does to their blows), how long a size lasts (ticks), cooldown. */
	static final double RAY_REACH = 16.0;
	static final double[] RAY_SCALES = {0.25, 0.5, 1.0, 1.75, 2.75};
	static final double[] RAY_DAMAGE = {-0.5, -0.25, 0.0, 0.25, 0.5};
	static final int RAY_NATURAL = 2, RAY_TICKS = 1200, RAY_COOLDOWN = 100;
	/** The jar: the custom data key that holds the creature, and how long it stays small once let out (ticks). */
	static final String JAR_TAG = "jaymoskingdoms_jar";
	static final int JAR_SMALL_TICKS = 200;
	static final int WAX = 0xE6C65A, WANE = 0x6FC7B5;

	private static final Identifier SCALE_ID = JaymosKingdoms.id("druid_size"), HEALTH_ID = JaymosKingdoms.id("druid_size_health"),
		DAMAGE_ID = JaymosKingdoms.id("druid_size_damage"), STEP_ID = JaymosKingdoms.id("druid_size_step"), REACH_ID = JaymosKingdoms.id("druid_size_reach"),
		BLOCK_REACH_ID = JaymosKingdoms.id("druid_size_block_reach"), FALL_ID = JaymosKingdoms.id("druid_size_fall"),
		RAY_SCALE_ID = JaymosKingdoms.id("size_ray"), RAY_DAMAGE_ID = JaymosKingdoms.id("size_ray_damage");

	private static final class Sized {
		int step;
		long until;
	}

	/** A shaped Druid's size (an index into {@link #SCALES}); none is the beast's own. */
	private static final Map<UUID, Integer> SIZE = new HashMap<>();
	private static final Map<UUID, LivingEntity> GRABBED = new HashMap<>();
	private static final Map<LivingEntity, Sized> OTHERS = new WeakHashMap<>();

	private SizeArts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SizeArts::tick);
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (!(player instanceof ServerPlayer sp) || !(world instanceof ServerLevel level) || hand != InteractionHand.MAIN_HAND || !(entity instanceof LivingEntity target)) {
				return InteractionResult.PASS;
			}
			if (sp.getMainHandItem().is(Items.GLASS_BOTTLE) && !jar(sp.getMainHandItem())) {
				return bottle(sp, level, target) ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			// small, in a shape: up onto a bigger creature
			if (Druids.shaped(sp) && size(sp) <= SMALL_FROM && !sp.isPassenger() && target.getBbHeight() >= sp.getBbHeight() * RIDE_TALLER
				&& sp.startRiding(target, true, true)) {
				unnoticed(sp, level);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
			ItemStack held = player.getItemInHand(hand);
			if (world instanceof ServerLevel level && player instanceof ServerPlayer sp && jar(held)) {
				release(sp, level, hand, held, hit.getBlockPos().relative(hit.getDirection()));
				return InteractionResult.SUCCESS;
			}
			return jar(held) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		});
		// a jar is never filled with water over what is in it
		UseItemCallback.EVENT.register((player, world, hand) -> jar(player.getItemInHand(hand)) ? InteractionResult.FAIL : InteractionResult.PASS);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			SIZE.clear();
			GRABBED.clear();
			OTHERS.clear();
		});
	}

	/** The size {@code player} wears now (an index into {@link #SCALES}; {@link #NATURAL} for none). */
	public static int size(Player player) {
		return SIZE.getOrDefault(player.getUUID(), NATURAL);
	}

	// ---- the key (in a shape) -------------------------------------------------------------------------------------------------

	static void key(ServerPlayer player, ArtKeys.Gesture gesture) {
		if (!(player.level() instanceof ServerLevel level) || !ClassTrees.check(player, SIZES)) {
			return;
		}
		int size = size(player);
		switch (gesture) {
			case TAP, SNEAK -> {
				int reach = player.isCreative() ? NATURAL : Math.min(NATURAL, ClassTrees.has(player, SIZES));
				int to = size + (gesture == ArtKeys.Gesture.TAP ? 1 : -1);
				if (to < NATURAL - reach || to > NATURAL + reach) {
					ArtKeys.refuse(player, "size_limit");
				} else if (to > size && !room(player, level, SCALES[to] / SCALES[size])) {
					ArtKeys.refuse(player, "size_no_room");
				} else if (!ArtKeys.cooling(player, "size_step", STEP_COOLDOWN)) {
					set(player, level, to);
				}
			}
			case SPRINT -> {
				if (size < BIG_FROM) {
					ArtKeys.refuse(player, "size_too_small");
				} else if (!ArtKeys.cooling(player, "size_stomp", STOMP_COOLDOWN)) {
					stomp(player, level, size - BIG_FROM);
				}
			}
			default -> {
				LivingEntity held = GRABBED.remove(player.getUUID());
				if (held != null && held.getVehicle() == player) {
					hurl(player, level, held, size - BIG_FROM);
				} else if (size < BIG_FROM) {
					ArtKeys.refuse(player, "size_too_small");
				} else {
					LivingEntity seen = ArtKeys.sight(player, GRAB_REACH + (size - BIG_FROM) * REACH_PER, e -> !ArtKeys.mine(player, e));
					if (seen == null || seen.getBbHeight() > player.getBbHeight() * GRAB_SMALLER || ArtKeys.great(seen)) {
						ArtKeys.refuse(player, "size_no_grab");
					} else if (!ArtKeys.cooling(player, "size_grab", GRAB_COOLDOWN) && seen.startRiding(player, true, false)) {
						GRABBED.put(player.getUUID(), seen);
						level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.RAVAGER_STEP, SoundSource.PLAYERS, 1.0F, 0.6F);
					}
				}
			}
		}
	}

	/** Whether the body has room to be {@code times} as big where it stands. */
	private static boolean room(ServerPlayer player, ServerLevel level, double times) {
		AABB box = player.getBoundingBox();
		double w = box.getXsize() * times / 2.0, h = box.getYsize() * times;
		return level.noCollision(player, new AABB(player.getX() - w, box.minY + 0.01, player.getZ() - w, player.getX() + w, box.minY + h, player.getZ() + w));
	}

	private static void set(ServerPlayer player, ServerLevel level, int size) {
		if (size == NATURAL) {
			SIZE.remove(player.getUUID());
		} else {
			SIZE.put(player.getUUID(), size);
		}
		int past = Math.max(0, size - NATURAL);
		float share = player.getHealth() / player.getMaxHealth();
		modifier(player, Attributes.SCALE, SCALE_ID, SCALES[size] - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, HEALTH[size], AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, DAMAGE[size], AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(player, Attributes.STEP_HEIGHT, STEP_ID, past * STEP_PER, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.ENTITY_INTERACTION_RANGE, REACH_ID, past * REACH_PER, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.BLOCK_INTERACTION_RANGE, BLOCK_REACH_ID, past * REACH_PER, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.SAFE_FALL_DISTANCE, FALL_ID, past * FALL_PER, AttributeModifier.Operation.ADD_VALUE);
		// the health keeps its share of the new whole
		player.setHealth(Math.max(1.0F, share * player.getMaxHealth()));
		Mob beast = Druids.beast(player);
		if (beast != null) {
			modifier(beast, Attributes.SCALE, SCALE_ID, SCALES[size] - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
		if (size > SMALL_FROM && player.isPassenger()) {
			player.stopRiding();
		}
		if (size < BIG_FROM) {
			drop(player);
		}
		Fx.ring(level, player.position().add(0.0, 0.1, 0.0), (float) Math.max(1.0, SCALES[size] * 1.2), size >= NATURAL ? WAX : WANE);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PUFFER_FISH_BLOW_UP, SoundSource.PLAYERS, 1.0F, (float) (1.6 - size * 0.18));
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.size.now", Component.translatable("message.jaymoskingdoms.size." + size)));
	}

	private static void modifier(LivingEntity e, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance a = e.getAttribute(attribute);
		if (a != null) {
			a.removeModifier(id);
			if (amount != 0.0) {
				a.addTransientModifier(new AttributeModifier(id, amount, op));
			}
		}
	}

	/** The shape is shed ({@link WildShapes#shed}): the body is its own size again, and what it held is let go. */
	static void shed(ServerPlayer player) {
		drop(player);
		if (SIZE.remove(player.getUUID()) == null) {
			return;
		}
		for (Holder<Attribute> a : List.of(Attributes.SCALE, Attributes.MAX_HEALTH, Attributes.ATTACK_DAMAGE, Attributes.STEP_HEIGHT, Attributes.ENTITY_INTERACTION_RANGE,
			Attributes.BLOCK_INTERACTION_RANGE, Attributes.SAFE_FALL_DISTANCE)) {
			AttributeInstance inst = player.getAttribute(a);
			if (inst != null) {
				for (Identifier id : List.of(SCALE_ID, HEALTH_ID, DAMAGE_ID, STEP_ID, REACH_ID, BLOCK_REACH_ID, FALL_ID)) {
					inst.removeModifier(id);
				}
			}
		}
		player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
		if (player.isPassenger() && !(player.getVehicle() instanceof net.minecraft.world.entity.vehicle.VehicleEntity)) {
			player.stopRiding();
		}
	}

	private static void drop(ServerPlayer player) {
		LivingEntity held = GRABBED.remove(player.getUUID());
		if (held != null && held.getVehicle() == player) {
			held.stopRiding();
		}
	}

	// ---- small: riding unnoticed ----------------------------------------------------------------------------------------------

	/** Whether {@code target} is too great, or too much somebody, for the ray or the jar. */
	private static boolean kept(LivingEntity target) {
		return ArtKeys.great(target) || target instanceof Folk || Druids.standIn(target);
	}

	/**
	 * Whether {@code attacker} can't take {@code target} for prey: a Druid small enough, riding a bigger creature, whom it has
	 * no blow from to remember (asked by every mob's targeting).
	 */
	public static boolean unnoticed(LivingEntity attacker, LivingEntity target) {
		return target instanceof ServerPlayer rider && rider.getVehicle() instanceof LivingEntity && attacker.getLastHurtByMob() != rider
			&& Druids.shaped(rider) && size(rider) <= SMALL_FROM;
	}

	/** Whatever hunts {@code player} loses them. */
	private static void unnoticed(ServerPlayer player, ServerLevel level) {
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(UNNOTICED_RANGE), m -> m.getTarget() == player)) {
			mob.setTarget(null);
		}
	}

	// ---- big: stomp, grab, throw ----------------------------------------------------------------------------------------------

	private static void stomp(ServerPlayer player, ServerLevel level, int past) {
		double radius = STOMP_RADIUS + past * STOMP_RADIUS_PER;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius, 2.0, radius), e -> ArtKeys.foe(player, e))) {
			e.hurtServer(level, player.damageSources().playerAttack(player), STOMP_DAMAGE + past * STOMP_DAMAGE_PER);
			e.setDeltaMovement(e.getDeltaMovement().add(0.0, STOMP_LIFT, 0.0));
			e.syncVelocity = true;
			e.needsSync = true;
		}
		Fx.ring(level, player.position().add(0.0, 0.1, 0.0), (float) radius, 0x9A8B6B);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
	}

	private static void hurl(ServerPlayer player, ServerLevel level, LivingEntity held, int past) {
		Vec3 look = player.getLookAngle();
		Vec3 from = player.getEyePosition().add(look.scale(1.0 + player.getBbWidth()));
		held.stopRiding();
		held.snapTo(from.x, from.y, from.z, held.getYRot(), held.getXRot());
		held.setDeltaMovement(look.scale(THROW_SPEED + past * THROW_SPEED_PER));
		held.syncVelocity = true;
		held.needsSync = true;
		held.hurtServer(level, player.damageSources().playerAttack(player), THROW_DAMAGE);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.6F);
	}

	// ---- the ray --------------------------------------------------------------------------------------------------------------

	/** The Wild key held out of a shape: a ray that shrinks (or, {@code grow}, grows) the creature looked at, one size. */
	static void ray(ServerPlayer player, ServerLevel level, boolean grow) {
		if (!ClassTrees.check(player, RAY)) {
			return;
		}
		LivingEntity target = ArtKeys.sight(player, RAY_REACH, e -> true);
		if (target == null) {
			ArtKeys.refuse(player, "no_creature");
			return;
		}
		// (never a boss or a lord, the townsfolk, another Druid's shape; another player only of their own party, or where players may fight)
		if (kept(target) || target instanceof Player other && !ArtKeys.mine(player, other) && !ArtKeys.foe(player, other)) {
			ArtKeys.refuse(player, "size_too_great");
			return;
		}
		Sized sized = OTHERS.get(target);
		int step = (sized == null ? RAY_NATURAL : sized.step) + (grow ? 1 : -1);
		if (step < 0 || step >= RAY_SCALES.length) {
			ArtKeys.refuse(player, "size_limit");
			return;
		}
		if (ArtKeys.cooling(player, "size_ray", RAY_COOLDOWN)) {
			return;
		}
		size(target, step, level.getGameTime() + RAY_TICKS);
		Fx.beam(level, player.getEyePosition().add(0.0, -0.3, 0.0), target.getBoundingBox().getCenter(), 0.1F, grow ? WAX : WANE, 12);
		Fx.blast(level, target.getBoundingBox().getCenter(), 0.8F, grow ? WAX : WANE);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), grow ? SoundEvents.PUFFER_FISH_BLOW_UP : SoundEvents.PUFFER_FISH_BLOW_OUT,
			SoundSource.PLAYERS, 1.0F, grow ? 0.7F : 1.4F);
	}

	private static void size(LivingEntity target, int step, long until) {
		if (step == RAY_NATURAL) {
			OTHERS.remove(target);
		} else {
			Sized sized = OTHERS.computeIfAbsent(target, t -> new Sized());
			sized.step = step;
			sized.until = until;
		}
		modifier(target, Attributes.SCALE, RAY_SCALE_ID, RAY_SCALES[step] - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		modifier(target, Attributes.ATTACK_DAMAGE, RAY_DAMAGE_ID, RAY_DAMAGE[step], AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	// ---- the jar --------------------------------------------------------------------------------------------------------------

	/** Whether {@code stack} is a jar with a creature in it. */
	public static boolean jar(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		return data != null && data.copyTag().contains(JAR_TAG);
	}

	/** A glass bottle used on {@code target}: at the smallest size it goes in. True if it did. */
	private static boolean bottle(ServerPlayer player, ServerLevel level, LivingEntity target) {
		Sized sized = OTHERS.get(target);
		// (people are not put in jars)
		if (sized == null || sized.step != 0 || target instanceof Player || target instanceof net.minecraft.world.entity.npc.villager.AbstractVillager
			|| kept(target) || target.isPassenger() || target.isVehicle()
			|| target instanceof OwnableEntity pet && pet.getOwnerReference() != null && !player.getUUID().equals(pet.getOwnerReference().getUUID())) {
			return false;
		}
		// (its own size goes into the jar with it, not the ray's)
		size(target, RAY_NATURAL, 0L);
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
		if (!target.saveAsPassenger(out)) {
			return false;
		}
		CompoundTag body = out.buildResult();
		// (a pack beast with a load of its own, jars in it even, is too much for one)
		if (body.sizeInBytes() > JAR_BYTES) {
			ArtKeys.refuse(player, "jar_too_much");
			return false;
		}
		ItemStack jar = new ItemStack(Items.GLASS_BOTTLE);
		CustomData.update(DataComponents.CUSTOM_DATA, jar, tag -> tag.put(JAR_TAG, body));
		jar.set(DataComponents.CUSTOM_NAME, Component.translatable("item.jaymoskingdoms.creature_jar", target.getDisplayName()).withStyle(s -> s.withItalic(false).withColor(ChatFormatting.AQUA)));
		jar.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		jar.set(DataComponents.MAX_STACK_SIZE, 1);
		Fx.blast(level, target.getBoundingBox().getCenter(), 0.5F, WANE);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 1.0F, 1.4F);
		target.discard();
		if (!player.isCreative()) {
			player.getMainHandItem().shrink(1);
		}
		if (!player.getInventory().add(jar)) {
			player.drop(jar, false, net.minecraft.util.Prediction.SERVER_ONLY);
		}
		return true;
	}

	private static void release(ServerPlayer player, ServerLevel level, InteractionHand hand, ItemStack jar, BlockPos at) {
		CompoundTag body = jar.get(DataComponents.CUSTOM_DATA).copyTag().getCompoundOrEmpty(JAR_TAG);
		Entity out = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), body), level, EntitySpawnReason.LOAD, e -> {
			e.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, e.getYRot(), e.getXRot());
			return e;
		});
		if (out == null || !level.tryAddFreshEntityWithPassengers(out)) {
			ArtKeys.refuse(player, "jar_stuck");
			return;
		}
		if (out instanceof LivingEntity living) {
			// still small a while, then its own size
			size(living, 0, level.getGameTime() + JAR_SMALL_TICKS);
		}
		player.setItemInHand(hand, new ItemStack(Items.GLASS_BOTTLE));
		level.playSound(null, at, SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 1.0F, 1.2F);
	}

	// ---- every tick -----------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (now % 20L != 0L) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!(player.level() instanceof ServerLevel level)) {
				continue;
			}
			if (Druids.shaped(player) && size(player) <= SMALL_FROM && player.getVehicle() instanceof LivingEntity) {
				unnoticed(player, level);
			}
			LivingEntity held = GRABBED.get(player.getUUID());
			if (held != null) {
				if (held.getVehicle() != player || !held.isAlive()) {
					GRABBED.remove(player.getUUID());
				} else {
					// in the hand it can do nothing
					HeldFast.hold(held, GRAB_HOLD);
				}
			}
		}
		for (Iterator<Map.Entry<LivingEntity, Sized>> it = OTHERS.entrySet().iterator(); it.hasNext();) {
			Map.Entry<LivingEntity, Sized> e = it.next();
			if (!e.getKey().isAlive() || e.getKey().isRemoved()) {
				it.remove();
			} else if (now >= e.getValue().until) {
				it.remove();
				modifier(e.getKey(), Attributes.SCALE, RAY_SCALE_ID, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
				modifier(e.getKey(), Attributes.ATTACK_DAMAGE, RAY_DAMAGE_ID, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			}
		}
	}
}
