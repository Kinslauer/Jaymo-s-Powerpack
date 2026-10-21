package com.jaymoskingdoms;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
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

/**
 * The goblins of a goblin village ({@link GoblinVillages}): small, quick and green ({@link #SCALE} of a man), in rags
 * and scraps of hide. A village has its {@link #GOBLIN}s (knives, clubs and whatever they've stolen), its
 * {@link #SLINGER}s (bows, on the lookout and the gate) and its {@link #KING} (a mini-boss: fat, crowned in gold,
 * a green boss bar, a rally when hurt). They fall on players, villagers, golems and every settlement's folk, unless a
 * player is wearing a goblin mask and passing for one of them ({@link Disguise}).
 */
public final class Goblins {
	/** The roles (folk ids; clear of the realms', the camps' and the illagers' 20-24). */
	public static final int GOBLIN = 30, SLINGER = 31, KING = 32;
	/** How big a goblin is beside a man, and a goblin king. */
	static final double SCALE = 0.7, KING_SCALE = 0.95;
	/** A goblin: health, blows, pace; a slinger's health. */
	static final double GOBLIN_HEALTH = 16.0, GOBLIN_DAMAGE = 3.0, GOBLIN_SPEED = 0.36, SLINGER_HEALTH = 14.0;
	/** The king: health, blows, pace, how hard he is to shove. */
	static final double KING_HEALTH = 140.0, KING_DAMAGE = 7.0, KING_SPEED = 0.27, KING_KNOCKBACK_RESIST = 0.5;
	/** How far they spot prey. */
	static final double SIGHT = 20.0;
	/** One goblin in this many wears a scrap of a cap; one in this many drops its mask when it dies. */
	static final int CAP_ONE_IN = 3, MASK_DROP_ONE_IN = 6;
	/** Rag and hide colours, the king's purple robe. */
	static final int RAG = 0x6B5A3A, HIDE = 0x4E3B26, MOSS = 0x55602E, ROBE = 0x5B2C6F;

	private static final String[] KING_FIRST = {"Grub", "Snag", "Gob", "Mukk", "Skrag", "Nob", "Rotz", "Gnarl", "Bogg", "Zug", "Wort", "Grizz"};
	private static final String[] KING_SECOND = {"nak", "gut", "lob", "rik", "snot", "tooth", "bag", "nose", "gob", "wart"};
	private static final String[] KING_BYNAMES = {"the Fat", "the Greedy", "the Sly", "the Magnificent", "Gold-Tooth", "the Unwashed",
		"Big-Belly", "the Thrice-Crowned"};

	private Goblins() {
	}

	/** Defines the goblin roles (call once at init). */
	static void init() {
		Folk.defineRole(new Folk.Role(GOBLIN, "goblin", true, false, null, (folk, faction) -> {
			base(folk, GOBLIN_HEALTH, GOBLIN_DAMAGE, GOBLIN_SPEED, SCALE);
			Item[] weapons = {Items.WOODEN_SWORD, Items.STONE_SWORD, Items.STONE_SWORD, Items.STONE_AXE, Items.IRON_SWORD, Items.WOODEN_AXE};
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapons[folk.getRandom().nextInt(weapons.length)]));
			folk.setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, folk.getRandom().nextBoolean() ? RAG : HIDE));
			if (folk.getRandom().nextInt(CAP_ONE_IN) == 0) {
				folk.setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, MOSS));
			}
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.goblin_of", faction.name()));
		}));
		Folk.defineRole(new Folk.Role(SLINGER, "goblin_slinger", true, false, null, (folk, faction) -> {
			base(folk, SLINGER_HEALTH, GOBLIN_DAMAGE, GOBLIN_SPEED, SCALE);
			folk.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(Folk.ARCHER_RANGE + 4.0);
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			folk.setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, HIDE));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.goblin_slinger_of", faction.name()));
		}));
		Folk.defineRole(new Folk.Role(KING, "goblin_king", true, true, BossEvent.BossBarColor.GREEN, (folk, faction) -> {
			base(folk, KING_HEALTH, KING_DAMAGE, KING_SPEED, KING_SCALE);
			folk.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(KING_KNOCKBACK_RESIST);
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLDEN_AXE));
			folk.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.GOLDEN_HELMET));
			folk.setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, ROBE));
			folk.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.CHAINMAIL_LEGGINGS));
			folk.setCustomName(Component.literal(kingName(folk.getUUID().getLeastSignificantBits() ^ faction.id())));
		}));
	}

	private static void base(Folk folk, double health, double damage, double speed, double scale) {
		folk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		folk.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(damage);
		folk.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(speed);
		folk.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(SIGHT + 8.0);
		folk.getAttribute(Attributes.SCALE).setBaseValue(scale);
	}

	private static ItemStack dyed(Item item, int rgb) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
		return stack;
	}

	/** A goblin king's name, the same every time for the same seed. */
	static String kingName(long seed) {
		RandomSource random = RandomSource.create(seed ^ 0x60B11L);
		String name = KING_FIRST[random.nextInt(KING_FIRST.length)] + KING_SECOND[random.nextInt(KING_SECOND.length)];
		return name + " " + KING_BYNAMES[random.nextInt(KING_BYNAMES.length)];
	}

	/** One of a village's goblins (a goblin, a slinger or the king). */
	public static boolean isGoblin(Folk folk) {
		int role = folk.role();
		return role == GOBLIN || role == SLINGER || role == KING;
	}

	public static boolean isGoblin(LivingEntity entity) {
		return entity instanceof Folk folk && isGoblin(folk);
	}

	/** It shoots rather than closing in. */
	public static boolean ranged(int role) {
		return role == SLINGER;
	}

	/**
	 * The goblins' extra goals (called from {@link Folk#registerGoals}): they hunt players who aren't passing for one of
	 * them ({@link Disguise#fooled}), villagers, golems and every other faction's folk (camps' aside).
	 */
	static void addGoals(Folk folk, GoalSelector goals, GoalSelector targets) {
		targets.addGoal(2, new NearestAttackableTargetGoal<>(folk, LivingEntity.class, true, (target, level) -> isGoblin(folk) && prey(folk, target)));
	}

	/** What a goblin of {@code folk}'s village attacks on sight. */
	static boolean prey(Folk folk, LivingEntity target) {
		if (target instanceof ServerPlayer player) {
			return !player.isCreative() && !player.isSpectator() && !Disguise.fooled(player, folk.factionId());
		}
		if (target instanceof Folk other) {
			return other.style() != FactionType.BARBARIAN;
		}
		return target instanceof Villager && !Barbarians.isCampVillager(target) || target instanceof IronGolem && !((IronGolem)target).isPlayerCreated();
	}

	/** A goblin died (not the king: his death clears the village): now and then it leaves its mask behind. */
	static void died(ServerLevel level, Folk goblin, DamageSource source) {
		if (goblin.role() != KING && source.getEntity() instanceof ServerPlayer && level.getRandom().nextInt(MASK_DROP_ONE_IN) == 0) {
			goblin.spawnAtLocation(level, new ItemStack(Disguise.MASK));
		}
	}
}
