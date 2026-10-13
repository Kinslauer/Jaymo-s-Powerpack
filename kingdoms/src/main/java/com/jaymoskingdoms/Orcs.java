package com.jaymoskingdoms;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * (0.5.45) The orcs of an orc war camp ({@link OrcCamps}): our own take on The Orcs! (that mod is all rights reserved and
 * stopped at 1.20.4, so nothing of it is used: the looks, names and camps here are original). Grey-green, tusked and broad
 * (drawn wider than a man, the same height so they still fit through doors), in blackened hides and scraps of mail. A camp
 * has its {@link #GRUNT}s (axes and cleavers; they fight harder when badly hurt), its {@link #RANGER}s (bows, on the
 * watchtower and the wall), a {@link #SHAMAN} (a caster: {@link Sorcery}'s war chant, mending, fire and hexes) and its
 * {@link #WARCHIEF} (a boss: huge, a red boss bar, a rally when hurt, and a ground slam that throws back everyone close).
 * They hunt players, villagers, golems and every settlement's folk, and their war bands raid the kingdoms near them
 * ({@link Raids}).
 */
public final class Orcs {
	/** The roles (folk ids; clear of the realms', the camps' 20-24, the goblins' 30-32 and the casters' 40-42). */
	public static final int GRUNT = 50, RANGER = 51, SHAMAN = 52, WARCHIEF = 53;
	/** The warchief's size beside a man (the rest are a man's height, drawn broader: see the client's orc skins). */
	static final double WARCHIEF_SCALE = 1.35;
	/** A grunt: health, blows, pace, how hard he is to shove; a ranger's health; a shaman's. */
	static final double GRUNT_HEALTH = 34.0, GRUNT_DAMAGE = 5.5, GRUNT_SPEED = 0.31, GRUNT_KNOCKBACK_RESIST = 0.25, RANGER_HEALTH = 26.0,
		SHAMAN_HEALTH = 36.0;
	/** The warchief: health, blows, pace, how hard he is to shove, his armour's toughness. */
	static final double WARCHIEF_HEALTH = 220.0, WARCHIEF_DAMAGE = 10.0, WARCHIEF_SPEED = 0.28, WARCHIEF_KNOCKBACK_RESIST = 0.8,
		WARCHIEF_TOUGHNESS = 3.0;
	/** How far they spot prey. */
	static final double SIGHT = 24.0;
	/** Blood rage: below this share of his health a grunt (or ranger) fights harder and faster, for this long at a time (ticks). */
	static final float RAGE_BELOW = 0.35F;
	static final int RAGE_TICKS = 20 * 8;
	/** The warchief's ground slam: how near a foe must be, its reach, damage, throw, and how often (ticks). */
	static final double SLAM_TRIGGER = 3.5, SLAM_RADIUS = 5.0, SLAM_THROW = 1.3;
	static final float SLAM_DAMAGE = 8.0F;
	static final int SLAM_COOLDOWN = 20 * 8;
	/** One grunt in this many wears a helm; one in this many a mail shirt. */
	static final int HELM_ONE_IN = 3, MAIL_ONE_IN = 4;
	/** While fighting, a grunt bellows a war cry about once in this many ticks. */
	static final int CRY_ONE_IN = 240;
	/** Blackened hide, bloodied hide, bone-white paint. */
	static final int BLACK_HIDE = 0x2B2622, BLOOD_HIDE = 0x5E1F1A, ASH = 0x6E6A62;

	private static final String[] CHIEF_FIRST = {"Grak", "Morg", "Uzg", "Thrak", "Gor", "Durz", "Krug", "Nazg", "Burz", "Rukh", "Skarg", "Ogth",
		"Vrak", "Hurg", "Lugd", "Zog"};
	private static final String[] CHIEF_SECOND = {"ash", "mok", "ul", "gash", "rok", "thul", "nar", "bag", "dush", "grim"};
	private static final String[] CHIEF_BYNAMES = {"the Butcher", "Skull-Taker", "Iron-Jaw", "the Unbroken", "Bone-Crusher", "the Red",
		"Half-Ear", "the Gutter", "Tusk-Breaker", "the Vile"};

	/** Warchief UUID → the game time his slam is ready again (kept in memory only; a restart just readies it). */
	private static final java.util.Map<java.util.UUID, Long> SLAM_READY = new java.util.HashMap<>();

	private Orcs() {
	}

	/** Defines the orc roles (call once at init). */
	static void init() {
		Folk.defineRole(new Folk.Role(GRUNT, "orc_grunt", true, false, null, (folk, faction) -> {
			base(folk, GRUNT_HEALTH, GRUNT_DAMAGE, GRUNT_SPEED);
			folk.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(GRUNT_KNOCKBACK_RESIST);
			RandomSource random = folk.getRandom();
			Item[] weapons = {Items.IRON_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.IRON_SWORD, Items.STONE_SWORD, Items.IRON_AXE};
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapons[random.nextInt(weapons.length)]));
			folk.setItemSlot(EquipmentSlot.CHEST, random.nextInt(MAIL_ONE_IN) == 0 ? new ItemStack(Items.CHAINMAIL_CHESTPLATE)
				: dyed(Items.LEATHER_CHESTPLATE, random.nextBoolean() ? BLACK_HIDE : BLOOD_HIDE));
			folk.setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, BLACK_HIDE));
			if (random.nextInt(HELM_ONE_IN) == 0) {
				folk.setItemSlot(EquipmentSlot.HEAD, random.nextBoolean() ? new ItemStack(Items.IRON_HELMET) : dyed(Items.LEATHER_HELMET, BLACK_HIDE));
			}
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.orc_grunt_of", faction.name()));
		}));
		Folk.defineRole(new Folk.Role(RANGER, "orc_ranger", true, false, null, (folk, faction) -> {
			base(folk, RANGER_HEALTH, GRUNT_DAMAGE, GRUNT_SPEED);
			folk.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(Folk.ARCHER_RANGE + 6.0);
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			folk.setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, ASH));
			folk.setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, BLACK_HIDE));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.orc_ranger_of", faction.name()));
		}));
		Folk.defineRole(new Folk.Role(SHAMAN, "orc_shaman", true, false, null, (folk, faction) -> {
			base(folk, SHAMAN_HEALTH, 3.0, Sorcery.CASTER_SPEED);
			folk.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(Sorcery.RANGE + 10.0);
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Arcana.TOTEM_STAFF));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.orc_shaman_of", faction.name()));
		}));
		Folk.defineRole(new Folk.Role(WARCHIEF, "orc_warchief", true, true, BossEvent.BossBarColor.RED, (folk, faction) -> {
			base(folk, WARCHIEF_HEALTH, WARCHIEF_DAMAGE, WARCHIEF_SPEED);
			folk.getAttribute(Attributes.SCALE).setBaseValue(WARCHIEF_SCALE);
			folk.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(WARCHIEF_KNOCKBACK_RESIST);
			folk.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(WARCHIEF_TOUGHNESS);
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_AXE));
			folk.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
			folk.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.CHAINMAIL_CHESTPLATE));
			folk.setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, BLOOD_HIDE));
			folk.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
			folk.setCustomName(Component.literal(warchiefName(folk.getUUID().getLeastSignificantBits() ^ faction.id())));
		}));
	}

	private static void base(Folk folk, double health, double damage, double speed) {
		folk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		folk.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(damage);
		folk.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
		folk.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(SIGHT + 8.0);
	}

	private static ItemStack dyed(Item item, int rgb) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
		return stack;
	}

	/** A warchief's name, the same every time for the same seed. */
	static String warchiefName(long seed) {
		RandomSource random = RandomSource.create(seed ^ 0x0BC5L);
		String name = CHIEF_FIRST[random.nextInt(CHIEF_FIRST.length)] + CHIEF_SECOND[random.nextInt(CHIEF_SECOND.length)];
		return name + " " + CHIEF_BYNAMES[random.nextInt(CHIEF_BYNAMES.length)];
	}

	public static boolean isOrc(int role) {
		return role == GRUNT || role == RANGER || role == SHAMAN || role == WARCHIEF;
	}

	public static boolean isOrc(Folk folk) {
		return isOrc(folk.role());
	}

	public static boolean isOrc(LivingEntity entity) {
		return entity instanceof Folk folk && isOrc(folk);
	}

	/** It shoots rather than closing in. */
	public static boolean ranged(int role) {
		return role == RANGER;
	}

	/** It fights hand to hand (and goes raiding): a grunt or the warchief. */
	static boolean brawler(int role) {
		return role == GRUNT || role == WARCHIEF;
	}

	/**
	 * The orcs' extra goals (called from {@link Folk#registerGoals}): they hunt players, villagers, golems and every
	 * settlement's folk (other camps' folk aside, unless they start it). A realm's fighting folk already hunt them back
	 * ({@link Barbarians#addGoals}: an orc camp is a camp).
	 */
	static void addGoals(Folk folk, GoalSelector goals, GoalSelector targets) {
		targets.addGoal(2, new NearestAttackableTargetGoal<>(folk, LivingEntity.class, true, (target, level) -> isOrc(folk) && prey(target)));
	}

	/** What an orc attacks on sight. */
	static boolean prey(LivingEntity target) {
		if (target instanceof ServerPlayer player) {
			return !player.isCreative() && !player.isSpectator();
		}
		if (target instanceof Folk other) {
			return other.style() != FactionType.BARBARIAN;
		}
		return target instanceof Villager || target instanceof IronGolem;
	}

	/** An orc's own tick (from {@link Folk#customServerAiStep}): blood rage, war cries, the warchief's slam. */
	static void tick(ServerLevel level, Folk folk) {
		if (!isOrc(folk) || folk.tickCount % 10 != 0) {
			return;
		}
		LivingEntity target = folk.getTarget();
		boolean fighting = target != null && target.isAlive();
		int role = folk.role();
		if ((role == GRUNT || role == RANGER) && fighting && folk.getHealth() < folk.getMaxHealth() * RAGE_BELOW && !folk.hasEffect(MobEffects.STRENGTH)) {
			folk.addEffect(new MobEffectInstance(MobEffects.STRENGTH, RAGE_TICKS, 0));
			folk.addEffect(new MobEffectInstance(MobEffects.SPEED, RAGE_TICKS, 0));
			level.sendParticles(ParticleTypes.ANGRY_VILLAGER, folk.getX(), folk.getY() + 2.0, folk.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
			level.playSound(null, folk.getX(), folk.getY(), folk.getZ(), SoundEvents.PIGLIN_BRUTE_ANGRY, SoundSource.HOSTILE, 1.2F, 0.6F);
			Speech.say(level, folk, Component.translatable("speech.jaymoskingdoms.orc.rage_" + level.getRandom().nextInt(3)), Speech.RED, 20.0);
		}
		if (role == GRUNT && fighting && level.getRandom().nextInt(CRY_ONE_IN / 10) == 0) {
			level.playSound(null, folk.getX(), folk.getY(), folk.getZ(), SoundEvents.PIGLIN_BRUTE_ANGRY, SoundSource.HOSTILE, 1.0F, 0.7F);
			Speech.say(level, folk, Component.translatable("speech.jaymoskingdoms.orc.cry_" + level.getRandom().nextInt(6)), Speech.RED, 20.0);
		}
		if (role == WARCHIEF && fighting && folk.distanceTo(target) <= SLAM_TRIGGER
			&& level.getGameTime() >= SLAM_READY.getOrDefault(folk.getUUID(), 0L)) {
			slam(level, folk);
		}
	}

	/** The warchief brings his axe down on the ground: everyone near (not his own) is hurt and thrown back. */
	private static void slam(ServerLevel level, Folk chief) {
		SLAM_READY.put(chief.getUUID(), level.getGameTime() + SLAM_COOLDOWN);
		chief.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		DamageSource source = level.damageSources().mobAttack(chief);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, chief.getBoundingBox().inflate(SLAM_RADIUS, 1.5, SLAM_RADIUS),
			e -> e != chief && e.isAlive() && !(e instanceof Folk f && chief.sameSide(f))
				&& !(e instanceof ServerPlayer p && (p.isCreative() || p.isSpectator())))) {
			if (!(e instanceof ServerPlayer) && !(e instanceof Folk) && !(e instanceof Villager) && !(e instanceof IronGolem) && e != chief.getTarget()) {
				continue;
			}
			e.hurtServer(level, source, SLAM_DAMAGE);
			Vec3 away = e.position().subtract(chief.position()).multiply(1.0, 0.0, 1.0);
			Vec3 push = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 0.0) : away.normalize().scale(SLAM_THROW);
			e.setDeltaMovement(push.x, 0.55, push.z);
			e.syncVelocity = true;
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), chief.getX(), chief.getY() + 0.1,
			chief.getZ(), 60, 2.0, 0.1, 2.0, 0.15);
		level.sendParticles(ParticleTypes.EXPLOSION, chief.getX(), chief.getY() + 0.3, chief.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, chief.getX(), chief.getY(), chief.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 1.0F, 0.6F);
		level.playSound(null, chief.getX(), chief.getY(), chief.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.5F, 0.8F);
	}

	static void clear() {
		SLAM_READY.clear();
	}
}
