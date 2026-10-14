package com.jaymospowers.power.arsenal;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Flames;
import com.jaymospowers.power.arsenal.SwordStone.Variant;
import com.jaymospowers.power.arsenal.entity.FrostLance;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The gifts of the five Swords in the Stone ({@link SwordStone}): each has a passive, a right-click active on a
 * cooldown, and one enchantment it's drawn with (so it glints). Same pattern as {@link RelicWeapons}.
 * <ul>
 * <li><b>Durandal</b>: unbreakable, and its holder can't be knocked back. <i>Plant</i>: raised, then driven into the
 * ground, a staggering shockwave around you.</li>
 * <li><b>Laevateinn</b>: sets what it cuts alight, and its holder doesn't burn. <i>Flame Wave</i>: an arc of fire rolls
 * out ahead.</li>
 * <li><b>Caladbolg</b>: each cut arcs on to two more foes nearby. <i>Rainbow Reach</i>: the blade stretches into a long
 * prismatic sweep.</li>
 * <li><b>Gram</b>: half of what armour would stop gets through, and it hits bosses and big things harder.
 * <i>Sunder</i>: a heavy overhead blow that wrecks armour and shields.</li>
 * <li><b>Hrunting</b>: frost creeps into what it cuts and slows it. <i>Frost Lance</i>: a spear of ice, thrown, that
 * comes back ({@link FrostLance}).</li>
 * </ul>
 */
public final class StoneSwords {
	/** All five: their attack damage and speed on a netherite base. */
	static final float SWORD_DAMAGE = 4.0F, SWORD_SPEED = -2.6F;

	/** Durandal: knockback resistance while in the main hand. */
	static final double DURANDAL_FOOTING = 1.0;
	/** Plant: the wind-up, reach, damage at the centre (and the share left at the edge), push, stagger, cooldown. */
	static final int PLANT_WINDUP_TICKS = 7;
	static final double PLANT_RADIUS = 4.0;
	static final float PLANT_DAMAGE = 6.0F, PLANT_EDGE_SHARE = 0.5F;
	static final double PLANT_PUSH = 0.8, PLANT_LIFT = 0.35;
	static final int PLANT_STAGGER_TICKS = 30;
	static final int PLANT_RINGS = 3, PLANT_RING_INTERVAL = 2;
	static final int PLANT_COOLDOWN = 120;
	/** Slowness on yourself while winding up a heavy move. */
	static final int WINDUP_SLOW_LEVEL = 2;

	/** Laevateinn: seconds alight on a cut. */
	static final int LAEV_BURN_SECONDS = 5;
	/** Laevateinn: the flames of a cut lick this far (blocks) around what it struck, catching whatever's there. */
	static final double LAEV_SPLASH = 1.0;
	/** Flame Wave: wind-up, reach, half-angle of the arc (degrees), speed (blocks a tick), damage, burn, push, cooldown. */
	static final int FLAME_WINDUP_TICKS = 4;
	static final double FLAME_RANGE = 8.0, FLAME_HALF_ARC = 50.0, FLAME_SPEED = 0.9, FLAME_HEIGHT = 2.5;
	static final float FLAME_DAMAGE = 5.0F;
	static final int FLAME_FIRE_SECONDS = 6;
	static final double FLAME_PUSH = 0.4;
	static final int FLAME_COOLDOWN = 140;

	/** Caladbolg: how many foes a cut arcs to, how far, and the share of the cut each takes. */
	static final int ARC_TARGETS = 2;
	static final double ARC_RANGE = 5.0;
	static final float ARC_SHARE = 0.4F;
	/** Rainbow Reach: wind-up, length, half-angle (degrees), ticks to sweep, height of the band, damage, push, cooldown. */
	static final int RAINBOW_WINDUP_TICKS = 5;
	static final double RAINBOW_RANGE = 10.0, RAINBOW_HALF_ARC = 75.0, RAINBOW_HEIGHT = 2.0;
	static final int RAINBOW_SWEEP_TICKS = 6;
	static final float RAINBOW_DAMAGE = 8.0F;
	static final double RAINBOW_PUSH = 0.6;
	static final int RAINBOW_COOLDOWN = 180;

	/** Gram: share of what armour stopped that gets through anyway; extra share against bosses and big things. */
	static final float GRAM_PIERCE = 0.5F, GRAM_BIG_BONUS = 0.5F;
	/** A boss by health, or big by bounding volume (width x width x height). */
	static final float GRAM_BOSS_HEALTH = 100.0F;
	static final double GRAM_BIG_VOLUME = 3.0;
	/** Sunder: reach, wind-up, damage, durability torn from each armour piece and a shield, shield lockout, cooldown. */
	static final double SUNDER_RANGE = 4.5;
	static final int SUNDER_WINDUP_TICKS = 9;
	static final float SUNDER_DAMAGE = 9.0F;
	static final int SUNDER_ARMOR_WEAR = 25, SUNDER_SHIELD_WEAR = 60, SUNDER_SHIELD_LOCK_TICKS = 100;
	static final int SUNDER_STAGGER_TICKS = 20;
	static final int SUNDER_COOLDOWN = 160;

	/** Hrunting: frost added per cut (and how far past freezing it can build), slowness on a cut. */
	static final int HRUNT_FROST_PER_HIT = 50, HRUNT_FROST_OVER = 60;
	static final int HRUNT_SLOW_TICKS = 40, HRUNT_SLOW_LEVEL = 1;
	/** Frost Lance: cooldown (the lance itself is tuned in {@link FrostLance}). */
	static final int LANCE_COOLDOWN = 100;

	/** The ice spear Frost Lance throws: only ever drawn, never held. */
	public static Item FROST_LANCE_ITEM;

	/** Wind-ups and waves in progress. Server memory only. */
	private static final List<Task> TASKS = new ArrayList<>();
	/** Set while Caladbolg's arcs or sweep are landing, so they don't arc again. */
	private static boolean arcing;

	private StoneSwords() {
	}

	static void register() {
		ResourceKey<Item> lanceKey = ResourceKey.create(Registries.ITEM, JaymosPowers.id("arsenal_frost_lance"));
		FROST_LANCE_ITEM = Registry.register(BuiltInRegistries.ITEM, lanceKey, new Item(new Item.Properties().setId(lanceKey).stacksTo(1)));
		ServerLivingEntityEvents.AFTER_DAMAGE.register(StoneSwords::afterDamage);
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(source.is(DamageTypeTags.IS_FIRE) && holding(entity, Variant.LAEVATEINN)));
		ServerTickEvents.END_SERVER_TICK.register(server -> TASKS.removeIf(Task::tick));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> clear());
	}

	static void clear() {
		TASKS.clear();
		arcing = false;
	}

	/** The sword's own properties on top of the shared ones: its lore, and Durandal's unbreakable footing. */
	static Item.Properties properties(Variant variant, Item.Properties properties) {
		properties.sword(ToolMaterial.NETHERITE, SWORD_DAMAGE, SWORD_SPEED).component(DataComponents.LORE, new ItemLore(List.of(
			Component.translatable("item.jaymospowers." + variant.sword + ".lore1").withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
			Component.translatable("item.jaymospowers." + variant.sword + ".passive").withStyle(ChatFormatting.BLUE),
			Component.translatable("item.jaymospowers." + variant.sword + ".active").withStyle(ChatFormatting.DARK_PURPLE),
			Component.translatable("item.jaymospowers.durandal.lore2").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC))));
		if (variant == Variant.DURANDAL) {
			properties.component(DataComponents.UNBREAKABLE, Unit.INSTANCE).attributes(ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
					SWORD_DAMAGE + ToolMaterial.NETHERITE.attackDamageBonus(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, SWORD_SPEED, AttributeModifier.Operation.ADD_VALUE),
					EquipmentSlotGroup.MAINHAND)
				.add(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(JaymosPowers.id("arsenal_durandal_footing"), DURANDAL_FOOTING,
					AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.build());
		}
		return properties;
	}

	/** The enchantment each is drawn with, and its level. */
	private static ResourceKey<Enchantment> enchantment(Variant variant) {
		return switch (variant) {
			case DURANDAL -> Enchantments.SHARPNESS;
			case LAEVATEINN -> Enchantments.FIRE_ASPECT;
			case CALADBOLG -> Enchantments.SWEEPING_EDGE;
			case GRAM -> Enchantments.LOOTING;
			case HRUNTING -> Enchantments.UNBREAKING;
		};
	}

	private static int enchantmentLevel(Variant variant) {
		return switch (variant) {
			case DURANDAL, LAEVATEINN, GRAM -> 2;
			case CALADBOLG, HRUNTING -> 3;
		};
	}

	/** A sword fresh from its stone: its enchantment baked in. */
	static void enchant(ServerLevel level, Variant variant, ItemStack sword) {
		level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(enchantment(variant))
			.ifPresent(holder -> sword.enchant(holder, enchantmentLevel(variant)));
	}

	static @Nullable Variant variant(ItemStack stack) {
		return stack.getItem() instanceof Blade blade ? blade.variant : null;
	}

	private static boolean holding(LivingEntity entity, Variant variant) {
		return variant(entity.getMainHandItem()) == variant || variant(entity.getOffhandItem()) == variant;
	}

	// ---------------------------------------------------------------- passives

	private static void afterDamage(LivingEntity entity, DamageSource source, float baseDamage, float damageTaken, boolean blocked) {
		if (damageTaken <= 0.0F || blocked || !(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker
			|| !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		Variant variant = variant(attacker.getMainHandItem());
		if (variant == null) {
			return;
		}
		switch (variant) {
			case LAEVATEINN -> {
				Flames.touch(level, entity, LAEV_BURN_SECONDS, attacker);
				Flames.area(level, entity.getBoundingBox().inflate(LAEV_SPLASH), LAEV_BURN_SECONDS, attacker,
					e -> e != entity && !(e instanceof OwnableEntity pet && pet.getOwner() == attacker));
				level.sendParticles(ParticleTypes.FLAME, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(), 6, 0.2, 0.3, 0.2, 0.02);
			}
			case CALADBOLG -> {
				if (!arcing) {
					arc(level, attacker, entity, damageTaken * ARC_SHARE);
				}
			}
			case GRAM -> gramBite(level, entity, baseDamage, damageTaken);
			case HRUNTING -> frostbite(level, attacker, entity);
			default -> {
			}
		}
	}

	/** Caladbolg: the cut leaps on to the nearest foes around the one it struck. */
	private static void arc(ServerLevel level, LivingEntity attacker, LivingEntity struck, float damage) {
		List<LivingEntity> foes = level.getEntitiesOfClass(LivingEntity.class, struck.getBoundingBox().inflate(ARC_RANGE),
			e -> e != struck && e != attacker && e.isAlive() && !e.isSpectator() && foe(attacker, e));
		foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(struck)));
		Vec3 from = struck.getBoundingBox().getCenter();
		arcing = true;
		try {
			for (int i = 0; i < Math.min(ARC_TARGETS, foes.size()); i++) {
				LivingEntity foe = foes.get(i);
				Vec3 to = foe.getBoundingBox().getCenter();
				for (double d = 0.0; d <= 1.0; d += 0.12) {
					Vec3 at = from.lerp(to, d);
					level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 1, 0.03, 0.03, 0.03, 0.0);
				}
				foe.setInvulnerableTime(0);
				foe.hurtServer(level, attack(level, attacker), Math.max(1.0F, damage));
				from = to;
			}
		} finally {
			arcing = false;
		}
		if (!foes.isEmpty()) {
			level.playSound(null, struck.getX(), struck.getY(), struck.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2F, 1.6F);
		}
	}

	/** Something worth arcing to: a monster, or a mob that's after you (never your own pets). */
	private static boolean foe(LivingEntity attacker, LivingEntity e) {
		if (e instanceof OwnableEntity pet && pet.getOwner() == attacker) {
			return false;
		}
		return e instanceof Enemy || e instanceof Mob mob && mob.getTarget() == attacker;
	}

	/** Gram: half of what armour stopped gets through, and more again against bosses and big things. */
	private static void gramBite(ServerLevel level, LivingEntity entity, float baseDamage, float damageTaken) {
		if (!entity.isAlive()) {
			return;
		}
		float bonus = Math.max(0.0F, baseDamage - damageTaken) * GRAM_PIERCE;
		boolean big = entity.getMaxHealth() >= GRAM_BOSS_HEALTH
			|| entity.getBbWidth() * entity.getBbWidth() * entity.getBbHeight() >= GRAM_BIG_VOLUME;
		if (big) {
			bonus += baseDamage * GRAM_BIG_BONUS;
		}
		if (bonus < 0.1F) {
			return;
		}
		entity.setInvulnerableTime(0);
		entity.hurtServer(level, level.damageSources().magic(), bonus);
		level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
			big ? 10 : 4, 0.25, 0.3, 0.25, 0.02);
	}

	/** Hrunting: frost creeps in with every cut, and it slows. */
	private static void frostbite(ServerLevel level, LivingEntity attacker, LivingEntity entity) {
		if (entity.canFreeze()) {
			entity.setTicksFrozen(Math.min(entity.getTicksFrozen() + HRUNT_FROST_PER_HIT, entity.getTicksRequiredToFreeze() + HRUNT_FROST_OVER));
		}
		entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, HRUNT_SLOW_TICKS, HRUNT_SLOW_LEVEL), attacker);
		level.sendParticles(ParticleTypes.SNOWFLAKE, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(), 8, 0.25, 0.35, 0.25, 0.02);
	}

	private static DamageSource attack(ServerLevel level, LivingEntity attacker) {
		return attacker instanceof Player player ? level.damageSources().playerAttack(player) : level.damageSources().mobAttack(attacker);
	}

	/** Everything a sweep or shockwave can catch: alive, not you, not your own pets. */
	private static boolean catchable(LivingEntity self, LivingEntity e) {
		return e != self && e.isAlive() && !e.isSpectator() && !(e instanceof OwnableEntity pet && pet.getOwner() == self);
	}

	// ---------------------------------------------------------------- actives

	/** A stone sword: its gift on right click. */
	public static final class Blade extends Item {
		final Variant variant;

		Blade(Properties properties, Variant variant) {
			super(properties);
			this.variant = variant;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (player.getCooldowns().isOnCooldown(stack)) {
				return InteractionResult.PASS;
			}
			if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
				return InteractionResult.SUCCESS;
			}
			int cooldown = switch (this.variant) {
				case DURANDAL -> plant(server, serverPlayer);
				case LAEVATEINN -> flameWave(server, serverPlayer);
				case CALADBOLG -> rainbowReach(server, serverPlayer);
				case GRAM -> sunder(server, serverPlayer);
				case HRUNTING -> frostLance(server, serverPlayer);
			};
			if (cooldown <= 0) {
				return InteractionResult.PASS;
			}
			player.getCooldowns().addCooldown(stack, cooldown);
			player.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
			return InteractionResult.SUCCESS;
		}
	}

	/** Something that plays out over a few ticks: {@link #step} returns true when it's done. */
	private abstract static class Task {
		final ServerPlayer player;
		final ServerLevel level;
		final long start;

		Task(ServerPlayer player) {
			this.player = player;
			this.level = player.level();
			this.start = this.level.getGameTime();
		}

		final boolean tick() {
			if (this.player.isRemoved() || !this.player.isAlive() || this.player.level() != this.level) {
				return true;
			}
			return this.step(this.level.getGameTime() - this.start);
		}

		abstract boolean step(long t);
	}

	private static void windUp(ServerPlayer player, int ticks) {
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, WINDUP_SLOW_LEVEL, false, false));
	}

	// Durandal: Plant

	private static int plant(ServerLevel level, ServerPlayer player) {
		windUp(player, PLANT_WINDUP_TICKS + 4);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
		TASKS.add(new Task(player) {
			@Override
			boolean step(long t) {
				if (t < PLANT_WINDUP_TICKS) {
					if (t == 0) {
						Vec3 tip = this.player.getEyePosition().add(0.0, 0.4, 0.0);
						this.level.sendParticles(ParticleTypes.ENCHANT, tip.x, tip.y, tip.z, 12, 0.2, 0.3, 0.2, 0.4);
					}
					return false;
				}
				long ring = t - PLANT_WINDUP_TICKS;
				if (ring == 0) {
					plantImpact(this.level, this.player);
				}
				if (ring % PLANT_RING_INTERVAL == 0) {
					plantRing(this.level, this.player, PLANT_RADIUS * (ring / PLANT_RING_INTERVAL + 1) / PLANT_RINGS);
				}
				return ring >= (long)(PLANT_RINGS - 1) * PLANT_RING_INTERVAL;
			}
		});
		return PLANT_COOLDOWN;
	}

	private static void plantImpact(ServerLevel level, ServerPlayer player) {
		Vec3 centre = player.position();
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		player.setDeltaMovement(player.getDeltaMovement().multiply(0.0, 1.0, 0.0));
		player.syncVelocity = true;
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(PLANT_RADIUS, 1.5, PLANT_RADIUS),
			e -> catchable(player, e))) {
			Vec3 away = target.position().subtract(centre).horizontal();
			double dist = away.length();
			if (dist > PLANT_RADIUS) {
				continue;
			}
			float share = 1.0F - (1.0F - PLANT_EDGE_SHARE) * (float)(dist / PLANT_RADIUS);
			target.hurtServer(level, level.damageSources().playerAttack(player), PLANT_DAMAGE * share);
			Vec3 push = (dist < 1.0E-3 ? player.getLookAngle().horizontal() : away).normalize().scale(PLANT_PUSH * share);
			target.push(push.x, PLANT_LIFT * share, push.z);
			target.syncVelocity = true;
			target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, PLANT_STAGGER_TICKS, 3), player);
			if (target instanceof Mob mob) {
				mob.getNavigation().stop();
			}
		}
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.MACE_SMASH_GROUND_HEAVY, SoundSource.PLAYERS, 1.4F, 0.7F);
		level.playSound(null, centre.x, centre.y, centre.z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6F, 0.5F);
		level.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y + 0.2, centre.z, 1, 0.0, 0.0, 0.0, 0.0);
		com.jaymospowers.core.Shakes.heavy(player, centre, PLANT_RADIUS * 2.5, 1.4F);
	}

	/** One ring of the shockwave: dust of whatever ground it runs over. */
	private static void plantRing(ServerLevel level, ServerPlayer player, double radius) {
		Vec3 centre = player.position();
		int points = (int)(radius * 10.0);
		for (int i = 0; i < points; i++) {
			double a = Math.PI * 2.0 * i / points;
			double x = centre.x + Math.cos(a) * radius, z = centre.z + Math.sin(a) * radius;
			BlockState ground = level.getBlockState(BlockPos.containing(x, centre.y - 0.5, z));
			if (ground.isAir()) {
				continue;
			}
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, centre.y + 0.1, z, 3, 0.1, 0.05, 0.1, 0.15);
		}
	}

	// Laevateinn: Flame Wave

	private static int flameWave(ServerLevel level, ServerPlayer player) {
		Vec3 look = player.getLookAngle().horizontal();
		if (look.lengthSqr() < 1.0E-4) {
			look = Vec3.directionFromRotation(0.0F, player.getYRot());
		}
		Vec3 dir = look.normalize();
		Vec3 origin = player.position();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
		TASKS.add(new Task(player) {
			final Set<Integer> hit = new HashSet<>();
			final Set<Integer> lit = new HashSet<>();

			@Override
			boolean step(long t) {
				if (t < FLAME_WINDUP_TICKS) {
					Vec3 hand = this.player.getEyePosition().add(dir.scale(0.6)).add(0.0, -0.4, 0.0);
					this.level.sendParticles(ParticleTypes.FLAME, hand.x, hand.y, hand.z, 4, 0.15, 0.15, 0.15, 0.02);
					return false;
				}
				if (t == FLAME_WINDUP_TICKS) {
					this.level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2F, 0.6F);
				}
				double inner = FLAME_SPEED * (t - FLAME_WINDUP_TICKS);
				double outer = Math.min(FLAME_RANGE, inner + FLAME_SPEED);
				// the front
				int points = Math.max(4, (int)(outer * 2.5));
				for (int i = 0; i <= points; i++) {
					double a = Math.toRadians(-FLAME_HALF_ARC + 2.0 * FLAME_HALF_ARC * i / points);
					Vec3 at = origin.add(rotate(dir, a).scale(outer));
					this.level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3, at.z, 2, 0.15, 0.25, 0.15, 0.02);
					if (i % 3 == 0) {
						this.level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.2, at.z, 1, 0.1, 0.1, 0.1, 0.0);
					}
				}
				AABB box = new AABB(origin, origin).inflate(outer + 1.0, FLAME_HEIGHT, outer + 1.0);
				java.util.function.Predicate<Entity> inFront = e -> {
					Vec3 to = e.position().subtract(origin);
					double dist = to.horizontalDistance();
					return !(dist < inner - 0.5 || dist > outer + 0.5 || Math.abs(to.y) > FLAME_HEIGHT || dist > 1.0E-3
						&& Math.toDegrees(Math.acos(Mth.clamp(to.horizontal().normalize().dot(dir), -1.0, 1.0))) > FLAME_HALF_ARC);
				};
				for (LivingEntity target : this.level.getEntitiesOfClass(LivingEntity.class, box,
					e -> catchable(this.player, e) && !this.hit.contains(e.getId()) && inFront.test(e))) {
					this.hit.add(target.getId());
					target.hurtServer(this.level, this.level.damageSources().playerAttack(this.player), FLAME_DAMAGE);
					target.push(dir.x * FLAME_PUSH, 0.2, dir.z * FLAME_PUSH);
					target.syncVelocity = true;
				}
				// the whole arc catches: creatures, corpses, items, creepers, TNT, arrows, armor stands
				Flames.area(this.level, box, FLAME_FIRE_SECONDS, this.player, e -> !this.lit.contains(e.getId()) && inFront.test(e)
					&& !(e instanceof OwnableEntity pet && pet.getOwner() == this.player) && this.lit.add(e.getId()));
				return outer >= FLAME_RANGE;
			}
		});
		return FLAME_COOLDOWN;
	}

	/** {@code dir} (horizontal) turned by {@code radians} about the vertical. */
	private static Vec3 rotate(Vec3 dir, double radians) {
		double cos = Math.cos(radians), sin = Math.sin(radians);
		return new Vec3(dir.x * cos - dir.z * sin, 0.0, dir.x * sin + dir.z * cos);
	}

	// Caladbolg: Rainbow Reach

	private static int rainbowReach(ServerLevel level, ServerPlayer player) {
		Vec3 look = player.getLookAngle().horizontal();
		if (look.lengthSqr() < 1.0E-4) {
			look = Vec3.directionFromRotation(0.0F, player.getYRot());
		}
		Vec3 dir = look.normalize();
		// sweeps from one side or the other, rolled each time
		double side = level.getRandom().nextBoolean() ? 1.0 : -1.0;
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5F, 1.4F);
		TASKS.add(new Task(player) {
			final Set<Integer> hit = new HashSet<>();

			@Override
			boolean step(long t) {
				Vec3 eye = this.player.getEyePosition().add(0.0, -0.3, 0.0);
				if (t < RAINBOW_WINDUP_TICKS) {
					// the blade lengthening, held out to the side
					double reach = RAINBOW_RANGE * (t + 1) / RAINBOW_WINDUP_TICKS;
					blade(this.level, eye, rotate(dir, Math.toRadians(-side * RAINBOW_HALF_ARC)), reach, t);
					return false;
				}
				long s = t - RAINBOW_WINDUP_TICKS;
				if (s == 0) {
					this.level.playSound(null, eye.x, eye.y, eye.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.4F, 0.5F);
					this.player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
				}
				double from = -RAINBOW_HALF_ARC + 2.0 * RAINBOW_HALF_ARC * s / RAINBOW_SWEEP_TICKS;
				double to = -RAINBOW_HALF_ARC + 2.0 * RAINBOW_HALF_ARC * (s + 1) / RAINBOW_SWEEP_TICKS;
				for (double a = from; a <= to; a += 8.0) {
					blade(this.level, eye, rotate(dir, Math.toRadians(side * a)), RAINBOW_RANGE, t);
				}
				arcing = true;
				try {
					for (LivingEntity target : this.level.getEntitiesOfClass(LivingEntity.class, this.player.getBoundingBox().inflate(RAINBOW_RANGE, RAINBOW_HEIGHT, RAINBOW_RANGE),
						e -> catchable(this.player, e) && !this.hit.contains(e.getId()))) {
						Vec3 rel = target.getBoundingBox().getCenter().subtract(eye);
						double dist = rel.horizontalDistance();
						if (dist > RAINBOW_RANGE + target.getBbWidth() * 0.5 || Math.abs(rel.y) > RAINBOW_HEIGHT || dist < 1.0E-3) {
							continue;
						}
						Vec3 flat = rel.horizontal().normalize();
						double angle = Math.toDegrees(Math.atan2(dir.x * flat.z - dir.z * flat.x, dir.x * flat.x + dir.z * flat.z)) * side;
						if (angle < from - 4.0 || angle > to + 4.0) {
							continue;
						}
						this.hit.add(target.getId());
						target.hurtServer(this.level, this.level.damageSources().playerAttack(this.player), RAINBOW_DAMAGE);
						Vec3 push = rotate(flat, Math.toRadians(side * 90.0)).scale(RAINBOW_PUSH);
						target.push(push.x, 0.25, push.z);
						target.syncVelocity = true;
					}
				} finally {
					arcing = false;
				}
				return s + 1 >= RAINBOW_SWEEP_TICKS;
			}
		});
		return RAINBOW_COOLDOWN;
	}

	/** The stretched blade, banded through the colours along its length. */
	private static void blade(ServerLevel level, Vec3 eye, Vec3 dir, double reach, long t) {
		for (double d = 1.0; d <= reach; d += 0.5) {
			Vec3 at = eye.add(dir.scale(d));
			int rgb = Mth.hsvToRgb((float)((d / RAINBOW_RANGE + t * 0.05) % 1.0), 0.75F, 1.0F);
			level.sendParticles(new DustParticleOptions(rgb, 1.2F), at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	// Gram: Sunder

	private static int sunder(ServerLevel level, ServerPlayer player) {
		Entity sighted = com.jaymospowers.power.web.WebPower.entityInSight(player, SUNDER_RANGE);
		if (!(sighted instanceof LivingEntity target)) {
			return 0;
		}
		windUp(player, SUNDER_WINDUP_TICKS + 2);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 0.7F);
		TASKS.add(new Task(player) {
			@Override
			boolean step(long t) {
				if (t < SUNDER_WINDUP_TICKS) {
					Vec3 over = this.player.getEyePosition().add(0.0, 0.6, 0.0);
					this.level.sendParticles(ParticleTypes.SCULK_SOUL, over.x, over.y, over.z, 1, 0.15, 0.1, 0.15, 0.01);
					return false;
				}
				if (target.isAlive() && target.level() == this.level
					&& target.distanceTo(this.player) <= SUNDER_RANGE + target.getBbWidth() * 0.5 + 1.0) {
					sunderHit(this.level, this.player, target);
				} else {
					this.level.playSound(null, this.player.getX(), this.player.getY(), this.player.getZ(), SoundEvents.PLAYER_ATTACK_NODAMAGE,
						SoundSource.PLAYERS, 1.0F, 0.6F);
				}
				return true;
			}
		});
		return SUNDER_COOLDOWN;
	}

	private static void sunderHit(ServerLevel level, ServerPlayer player, LivingEntity target) {
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		// the shield first: knocked aside and torn
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack held = target.getItemInHand(hand);
			if (held.has(DataComponents.BLOCKS_ATTACKS)) {
				if (target.isBlocking() && target.getUsedItemHand() == hand) {
					target.stopUsingItem();
				}
				if (target instanceof Player victim) {
					victim.getCooldowns().addCooldown(held, SUNDER_SHIELD_LOCK_TICKS);
				}
				held.hurtAndBreak(SUNDER_SHIELD_WEAR, target, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SHIELD_BREAK.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
			}
		}
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack armour = target.getItemBySlot(slot);
			if (armour.isDamageableItem()) {
				armour.hurtAndBreak(SUNDER_ARMOR_WEAR, target, slot);
			}
		}
		target.setInvulnerableTime(0);
		target.hurtServer(level, level.damageSources().playerAttack(player), SUNDER_DAMAGE);
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SUNDER_STAGGER_TICKS, 2), player);
		Vec3 at = target.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 20, 0.3, 0.4, 0.3, 0.3);
		level.sendParticles(ParticleTypes.SCULK_CHARGE_POP, at.x, at.y, at.z, 12, 0.3, 0.4, 0.3, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.MACE_SMASH_AIR, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.ANVIL_BREAK, SoundSource.PLAYERS, 0.5F, 0.7F);
	}

	// Hrunting: Frost Lance

	private static int frostLance(ServerLevel level, ServerPlayer player) {
		FrostLance lance = FrostLance.create(level, player);
		level.addFreshEntity(lance);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 1.0F, 1.3F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.5F, 1.8F);
		return LANCE_COOLDOWN;
	}
}
