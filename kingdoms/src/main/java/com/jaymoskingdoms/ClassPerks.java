package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import org.jspecify.annotations.Nullable;

/**
 * (0.6.0) What each class ({@link Classes}) is good with, as attribute modifiers put on the player whenever their class,
 * hand or armour changes, and as the few things that need a tick (an Archer's drawn bow). Every number is here.
 *
 * <ul>
 * <li><b>Thief:</b> light blades hit harder and swing faster; leather, chain and hide give a point more armour a piece.</li>
 * <li><b>Warlord:</b> two-handed weapons hit harder; heavy armour weighs half as much.</li>
 * <li><b>Mage:</b> two hearts fewer (the staff and the spells are theirs, stage 2).</li>
 * <li><b>Archer:</b> arrows hit harder ({@link Classes}), quicker on their feet; with a bow drawn they jump two blocks
 * and land soft.</li>
 * <li><b>Survivalist:</b> hunger drains slower, air lasts longer, a heart more, falls hurt less, a little quicker.</li>
 * <li><b>Brute:</b> maces, flails and axes hit harder, two hearts more, a raised shield stops what gets through other
 * shields and comes back sooner when knocked aside; slower, and heavy armour weighs twice as much.</li>
 * <li><b>Marksman:</b> a heart fewer; light armour gives a point more a piece (the guns are stage 3).</li>
 * <li><b>Sorcerer:</b> nothing passive but their own element's harm halved (0.7.184): their element and blade are stage 2.</li>
 * <li><b>Rider:</b> (the ninth, ours) in the saddle their horse is quicker and tires slower, horses come to trust them
 * sooner, and they strike harder from horseback; on foot a little slower, bow-legged.</li>
 * </ul>
 */
public final class ClassPerks {
	// (0.7.184) Andy: "bring everything up to sorcerer's level, then add a boon across the board". Each class's numbers went
	// up and each got one new boon (Sorcerer's numbers are the bar and stay; it gets its boon too).
	// Thief (was +25% blades, +20% swing)
	static final double THIEF_DAMAGE = 0.35, THIEF_SPEED = 0.25, THIEF_ARMOR_PER_PIECE = 1.0;
	/** (0.7.184) Thief's boon, Backstab: a light-blade blow on a foe facing away hits this many times as hard. */
	static final float THIEF_BACKSTAB = 1.5F;
	/** How far round a foe must be facing away for a backstab: the cosine between its look and the way to the thief. */
	static final double BACKSTAB_COS = -0.3;
	/** (0.7.184) Thief's second boon, Prowl (Andy: "add another to thief"): they move this share faster while sneaking. */
	static final double THIEF_PROWL = 0.5;
	// Warlord (was +25% two-handed, armour weight x0.5)
	static final double WARLORD_DAMAGE = 0.35, WARLORD_WEIGHT = 0.25, WARLORD_HEAVY_ARMOR_PER_PIECE = 1.0;
	/** (0.7.184) Warlord's boon, Cleave: a two-handed blow carries this share of itself to up to {@code WARLORD_CLEAVE_COUNT}
	 * other foes within {@code WARLORD_CLEAVE_RANGE} of the one struck (players and bystanders spared). */
	static final float WARLORD_CLEAVE = 0.4F;
	static final int WARLORD_CLEAVE_COUNT = 3;
	static final double WARLORD_CLEAVE_RANGE = 2.5;
	// Mage (was -2 hearts; the staff's power and its echo are in Spellcraft)
	static final double MAGE_HEALTH = -2.0;
	// Archer (was +25% arrows)
	static final double ARCHER_DAMAGE = 1.35, ARCHER_MOVE = 0.1, ARCHER_JUMP = 0.12, ARCHER_FALL = -0.5;
	/** (0.7.184) Archer's boon, Headshot: an arrow or bolt that strikes at the head (within this far below the eyes) hits this many times as hard. */
	static final float ARCHER_HEADSHOT = 1.5F;
	static final double HEADSHOT_BELOW_EYES = 0.35;
	/**
	 * (0.7.184) Andy: "archer headshots can pin enemies to the wall, sometimes decapitating them first", then "add it to
	 * players, and a beheading pin just takes the head off and pins that... whole body pins should only come from extremely
	 * strong bow shots that kill, and pinning can now be done but to the legs, replacing the effect of crippling". An
	 * Archer's arrow, on anyone (players too):
	 * <ul>
	 * <li>a fully drawn shot of at least {@code ARCHER_IMPALE_DAMAGE} that kills, with a wall within {@code ARCHER_PIN_REACH}
	 * behind (along its flight), nails the body there: Jaymo's Kitchen hangs the corpse on the wall;</li>
	 * <li>one headshot in {@code ARCHER_BEHEAD_ONE_IN} with a wall that close behind the head takes the head off (Jaymo's
	 * Powers) and nails just the head there;</li>
	 * <li>a shot in the legs (the bottom {@code ARCHER_LEG_SHARE} of the body) pins them to the ground where they stand for
	 * {@code ARCHER_LEG_PIN_TICKS} ({@code ARCHER_LEG_PIN_CRIT_TICKS} fully drawn), instead of Kitchen's crippling.</li>
	 * </ul>
	 */
	static final double ARCHER_PIN_REACH = 1.5, ARCHER_LEG_SHARE = 0.375;
	static final float ARCHER_IMPALE_DAMAGE = 12.0F;
	static final int ARCHER_BEHEAD_ONE_IN = 4, ARCHER_LEG_PIN_TICKS = 40, ARCHER_LEG_PIN_CRIT_TICKS = 80;
	/** The tag Kitchen reads off a leg-pinning arrow (so it doesn't cripple too), and the prefix of the one that hangs a corpse on the wall. */
	static final String LEG_PIN_TAG = "jaymoskingdoms.leg_pin", CORPSE_PIN_TAG = "jaymoskitchen.pinned.";
	static final TagKey<net.minecraft.world.entity.EntityType<?>> NO_LEGS = TagKey.create(Registries.ENTITY_TYPE,
		net.minecraft.resources.Identifier.fromNamespaceAndPath("jaymoskitchen", "no_legs"));
	static final String LEG_PIN_SPEED_ID = "archer_leg_pin_speed", LEG_PIN_JUMP_ID = "archer_leg_pin_jump";
	// Survivalist (was hunger x0.7, fall -30%)
	static final float SURVIVALIST_EXHAUSTION = 0.6F;
	static final double SURVIVALIST_OXYGEN = 1.0, SURVIVALIST_HEALTH = 2.0, SURVIVALIST_FALL = -0.4, SURVIVALIST_MOVE = 0.05;
	/** (0.7.184) Survivalist's boon, Second Wind: unhurt for {@code SURVIVALIST_CALM} ticks, they mend {@code SURVIVALIST_MEND}
	 * health every {@code SURVIVALIST_MEND_EVERY} ticks, fed or not. */
	static final int SURVIVALIST_CALM = 20 * 8, SURVIVALIST_MEND_EVERY = 20 * 3;
	static final float SURVIVALIST_MEND = 1.0F;
	/** (0.7.96) A Survivalist's hearts grow with them: a heart more (2 health) every this many character levels, up to this much in all. */
	static final int SURVIVALIST_LEVELS_PER_HEART = 10;
	static final double SURVIVALIST_HEALTH_MAX = 10.0;
	// Brute (was +30% bludgeons, -8% speed; now hard to shove too)
	static final double BRUTE_DAMAGE = 0.4, BRUTE_HEALTH = 4.0, BRUTE_MOVE = -0.06, BRUTE_WEIGHT = 2.0, BRUTE_KNOCKBACK_RESIST = 0.4;
	/** (0.7.184) Brute's boon, Stagger: a bludgeon blow slows its foe (Slowness at this level, for these ticks). */
	static final int BRUTE_STAGGER_TICKS = 30, BRUTE_STAGGER_LEVEL = 1;
	/** Brute: the share of damage a raised shield still stops when it would otherwise get past; how long a knocked-aside shield stays down (ticks, vanilla 100). */
	static final float BRUTE_BLOCK_LEFTOVER = 0.5F;
	static final int BRUTE_SHIELD_DOWN = 50;
	// Marksman: a heart fewer, light armour; guns (WeaponMod's) this share harder, this share faster to reload and wind up, this
	// share further, and a shot bends up to this many degrees toward a target within this reach
	// (0.7.184: was a heart fewer and +30%; crossbow bolts now count too)
	public static final double MARKSMAN_HEALTH = 0.0, MARKSMAN_ARMOR_PER_PIECE = 1.0, MARKSMAN_DAMAGE = 0.4, MARKSMAN_RELOAD = 0.35, MARKSMAN_RANGE = 0.5,
		MARKSMAN_AIM_DEGREES = 3.0, MARKSMAN_AIM_REACH = 48.0, MARKSMAN_BOLTS = 1.3;
	/** (0.7.184) Marksman's boon, Steady Aim: a shot (gun or crossbow) fired while sneaking hits this share harder again. */
	public static final double MARKSMAN_STEADY = 0.25;
	// Rider (was horse +15%, mounted blows +20%, -5% on foot)
	static final double RIDER_MOUNT_SPEED = 0.25, RIDER_STAMINA = 0.75, RIDER_MOUNTED_DAMAGE = 0.3, RIDER_FOOT_MOVE = -0.03;
	/** (0.7.184) Rider's boon, Barding: the mount a Rider is steering takes this share of the damage it would. */
	static final float RIDER_BARDING = 0.7F;
	/** (0.7.184) Sorcerer's boon, Kindred Element: the harm of their own element (fire burns, ice's freezing, lightning,
	 * water's drowning, earth's blasts, air's falls) reaches them at this share. */
	static final float SORCERER_WARD = 0.5F;
	static final float RIDER_TRUST = 1.5F;
	/** Heavy armour's weight on everyone: this share off movement speed a piece. */
	static final double HEAVY_ARMOR_SLOW = 0.015;

	public static final TagKey<Item> LIGHT_BLADES = tag("light_blades"), TWO_HANDED = tag("two_handed"), BLUDGEONS = tag("bludgeons"),
		GUNS = tag("guns"), LIGHT_ARMOR = tag("light_armor"), HEAVY_ARMOR = tag("heavy_armor");

	static final String DAMAGE_ID = "class_damage", ATTACK_SPEED_ID = "class_attack_speed", ARMOR_ID = "class_armor", HEALTH_ID = "class_health",
		MOVE_ID = "class_move", WEIGHT_ID = "class_weight", OXYGEN_ID = "class_oxygen", FALL_ID = "class_fall", JUMP_ID = "class_jump",
		DRAW_FALL_ID = "class_draw_fall", MOUNTED_ID = "class_mounted", MOUNT_ID = "class_mount", KNOCKBACK_ID = "class_knockback", SNEAK_ID = "class_sneak";

	private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** Player → whether they had a bow drawn last tick; whether their shield was down. */
	private static final Map<UUID, Boolean> DRAWING = new HashMap<>(), SHIELD_DOWN = new HashMap<>();
	/** Rider → the horse they sat last tick (the modifier on it comes off when they get down). */
	private static final Map<UUID, LivingEntity> MOUNTS = new HashMap<>();
	/** Survivalist → the tick (their own tickCount) they were last hurt. */
	private static final Map<UUID, Integer> LAST_HURT = new HashMap<>();
	/** (0.7.184) A body an Archer's killing shot nailed to a wall: held there while it dies; its tag comes off if it lived. */
	private record Impale(Vec3 at, String tag) {
	}

	/** (0.7.184) A head to come off the tick after the headshot (out of the blow that struck it), and nailed at {@code wall}. */
	private record Beheading(LivingEntity target, @Nullable ServerPlayer by, Vec3 wall, float yaw) {
	}

	private static final Map<LivingEntity, Impale> IMPALED = new HashMap<>();
	private static final List<Beheading> BEHEADINGS = new ArrayList<>();
	/** (0.7.184) Legs an Archer's arrow pinned to the ground → the game time they come free. */
	private static final Map<LivingEntity, Long> LEG_PINNED = new HashMap<>();
	/** True while a Warlord's cleave is landing its share on the others (so those blows don't cleave again). */
	private static boolean cleaving;

	private ClassPerks() {
	}

	private static TagKey<Item> tag(String name) {
		return TagKey.create(Registries.ITEM, JaymosKingdoms.id(name));
	}

	/** A class's card on the screen: its hearts, its speed, what it hits harder with, its armour, each a line. */
	public static List<Component> card(PlayerClass cls) {
		List<Component> lines = new ArrayList<>();
		double health = switch (cls) {
			case MAGE -> MAGE_HEALTH;
			case SURVIVALIST -> SURVIVALIST_HEALTH;
			case BRUTE -> BRUTE_HEALTH;
			case MARKSMAN -> MARKSMAN_HEALTH;
			default -> 0.0;
		};
		double move = switch (cls) {
			case ARCHER -> ARCHER_MOVE;
			case SURVIVALIST -> SURVIVALIST_MOVE;
			case BRUTE -> BRUTE_MOVE;
			case RIDER -> RIDER_FOOT_MOVE;
			default -> 0.0;
		};
		String hearts = String.valueOf((int) ((20.0 + health) / 2.0));
		if (cls == PlayerClass.SURVIVALIST) {
			hearts += "-" + (int) ((20.0 + SURVIVALIST_HEALTH_MAX) / 2.0);
		}
		lines.add(line("hearts", hearts));
		lines.add(line("speed", pct(move)));
		switch (cls) {
			case THIEF -> {
				lines.add(line("blades", pct(THIEF_DAMAGE)));
				lines.add(line("light_armor", "+" + (int) THIEF_ARMOR_PER_PIECE));
				lines.add(line("backstab", "x" + THIEF_BACKSTAB));
				lines.add(line("prowl", pct(THIEF_PROWL)));
			}
			case WARLORD -> {
				lines.add(line("two_handed", pct(WARLORD_DAMAGE)));
				lines.add(line("weight", "x" + WARLORD_WEIGHT));
				lines.add(line("cleave", pct(WARLORD_CLEAVE)));
			}
			case MAGE -> {
				lines.add(line("spells", "+"));
				lines.add(line("staff", pct(Spellcraft.MAGE_POWER - 1.0)));
				lines.add(line("echo", "1/" + Spellcraft.MAGE_ECHO_ONE_IN));
			}
			case ARCHER -> {
				lines.add(line("bows", pct(ARCHER_DAMAGE - 1.0)));
				lines.add(line("draw_jump", "2"));
				lines.add(line("headshot", "x" + ARCHER_HEADSHOT));
			}
			case SURVIVALIST -> {
				lines.add(line("hunger", pct(SURVIVALIST_EXHAUSTION - 1.0)));
				lines.add(line("fall", pct(SURVIVALIST_FALL)));
				lines.add(line("second_wind", "+"));
			}
			case BRUTE -> {
				lines.add(line("bludgeons", pct(BRUTE_DAMAGE)));
				lines.add(line("weight", "x" + (int) BRUTE_WEIGHT));
				lines.add(line("stagger", "+"));
			}
			case MARKSMAN -> {
				lines.add(line("guns", pct(MARKSMAN_DAMAGE)));
				lines.add(line("light_armor", "+" + (int) MARKSMAN_ARMOR_PER_PIECE));
				lines.add(line("steady", pct(MARKSMAN_STEADY)));
			}
			case SORCERER -> {
				lines.add(line("imbue", "+"));
				lines.add(line("spells", "-"));
				lines.add(line("ward", pct(SORCERER_WARD - 1.0)));
			}
			case RIDER -> {
				lines.add(line("horses", pct(RIDER_MOUNT_SPEED)));
				lines.add(line("mounted", pct(RIDER_MOUNTED_DAMAGE)));
				lines.add(line("barding", pct(RIDER_BARDING - 1.0)));
			}
		}
		return lines;
	}

	private static Component line(String key, String value) {
		return Component.translatable("gui.jaymoskingdoms.class.stat." + key, value);
	}

	private static String pct(double share) {
		int p = (int) Math.round(share * 100.0);
		return p == 0 ? "0" : (p > 0 ? "+" : "") + p + "%";
	}

	static void clear() {
		DRAWING.clear();
		SHIELD_DOWN.clear();
		MOUNTS.clear();
		LAST_HURT.clear();
		IMPALED.clear();
		BEHEADINGS.clear();
		LEG_PINNED.clear();
	}

	/** The factor on {@code player}'s food exhaustion (a Survivalist's hunger drains slower). */
	public static float exhaustion(ServerPlayer player) {
		return Classes.is(player, PlayerClass.SURVIVALIST) ? SURVIVALIST_EXHAUSTION : 1.0F;
	}

	/**
	 * (0.7.10) Whether {@code player}'s class is trained with {@code weapon}: a Thief's light blades, a Warlord's two-handers,
	 * a Brute's bludgeons, an Archer's bows, a Marksman's guns and crossbows, a Sorcerer's swords, a Mage's staffs, and
	 * anything a Rider swings from the saddle. Jaymo's Powers asks, by reflection: a Z Fighter hits at full only with these.
	 */
	public static boolean trainedWith(ServerPlayer player, ItemStack weapon) {
		PlayerClass cls = Classes.classOf(player);
		if (cls == null || weapon.isEmpty()) {
			return false;
		}
		return switch (cls) {
			case THIEF -> weapon.is(LIGHT_BLADES);
			case WARLORD -> weapon.is(TWO_HANDED);
			case BRUTE -> weapon.is(BLUDGEONS);
			case ARCHER -> weapon.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem;
			case MARKSMAN -> weapon.is(GUNS) || weapon.getItem() instanceof net.minecraft.world.item.CrossbowItem;
			case SORCERER -> weapon.is(net.minecraft.tags.ItemTags.SWORDS) || weapon.is(LIGHT_BLADES) || Tomes.isTome(weapon);
			case MAGE -> weapon.is(Arcana.BONE_STAFF) || weapon.is(Arcana.TOTEM_STAFF) || weapon.is(Arcana.WIZARD_STAFF) || Tomes.isTome(weapon);
			case RIDER -> player.getVehicle() != null;
			case SURVIVALIST -> false;
		};
	}

	/** A Survivalist's extra health: {@link #SURVIVALIST_HEALTH}, and a heart more every {@link #SURVIVALIST_LEVELS_PER_HEART} levels. */
	static double survivalistHealth(ServerPlayer player) {
		int grown = Stats.characterLevel(Skills.of(player)) / SURVIVALIST_LEVELS_PER_HEART;
		return Math.min(SURVIVALIST_HEALTH_MAX, SURVIVALIST_HEALTH + 2.0 * grown);
	}

	/** Everything a class does through attributes, from the class, the held weapon and the armour worn now. */
	static void attributes(ServerPlayer player) {
		PlayerClass cls = Classes.classOf(player);
		// (0.7.113: a hidden blade out over an empty hand is a light blade too)
		ItemStack held = HiddenBlade.weapon(player);
		int light = 0, heavy = 0;
		for (EquipmentSlot slot : ARMOR) {
			ItemStack piece = player.getItemBySlot(slot);
			if (piece.is(LIGHT_ARMOR)) {
				light++;
			} else if (piece.is(HEAVY_ARMOR)) {
				heavy++;
			}
		}
		double damage = 0.0, attackSpeed = 0.0, armor = 0.0, health = 0.0, move = 0.0, weight = HEAVY_ARMOR_SLOW * heavy, oxygen = 0.0, fall = 0.0, knockback = 0.0, sneak = 0.0;
		if (cls != null) {
			switch (cls) {
				case THIEF -> {
					if (held.is(LIGHT_BLADES)) {
						damage = THIEF_DAMAGE;
						attackSpeed = THIEF_SPEED;
					}
					armor = THIEF_ARMOR_PER_PIECE * light;
					sneak = THIEF_PROWL;
				}
				case WARLORD -> {
					if (held.is(TWO_HANDED)) {
						damage = WARLORD_DAMAGE;
					}
					weight *= WARLORD_WEIGHT;
					armor = WARLORD_HEAVY_ARMOR_PER_PIECE * heavy;
				}
				case MAGE -> health = MAGE_HEALTH;
				case ARCHER -> move = ARCHER_MOVE;
				case SURVIVALIST -> {
					oxygen = SURVIVALIST_OXYGEN;
					health = survivalistHealth(player);
					fall = SURVIVALIST_FALL;
					move = SURVIVALIST_MOVE;
				}
				case BRUTE -> {
					if (held.is(BLUDGEONS)) {
						damage = BRUTE_DAMAGE;
					}
					health = BRUTE_HEALTH;
					move = BRUTE_MOVE;
					weight *= BRUTE_WEIGHT;
					knockback = BRUTE_KNOCKBACK_RESIST;
				}
				case MARKSMAN -> {
					health = MARKSMAN_HEALTH;
					armor = MARKSMAN_ARMOR_PER_PIECE * light;
				}
				case SORCERER -> {
				}
				case RIDER -> move = RIDER_FOOT_MOVE;
			}
		}
		float before = player.getMaxHealth();
		Skills.modifier(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, damage, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		Skills.modifier(player, Attributes.ATTACK_SPEED, ATTACK_SPEED_ID, attackSpeed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		Skills.modifier(player, Attributes.ARMOR, ARMOR_ID, armor, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.MAX_HEALTH, HEALTH_ID, health, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.MOVEMENT_SPEED, MOVE_ID, move, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Skills.modifier(player, Attributes.MOVEMENT_SPEED, WEIGHT_ID, -weight, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Skills.modifier(player, Attributes.OXYGEN_BONUS, OXYGEN_ID, oxygen, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, knockback, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.SNEAKING_SPEED, SNEAK_ID, sneak, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		// (a share off what's left: a power that takes fall damage to nothing keeps it at nothing, Andy's rule)
		Skills.modifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_ID, fall, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		// (fewer hearts: the health bar shrinks to fit; more: the new hearts come empty, to be eaten back)
		if (player.getHealth() > player.getMaxHealth() || before > player.getMaxHealth()) {
			player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
		}
		if (!Classes.is(player, PlayerClass.ARCHER)) {
			drawing(player, false);
		}
		if (!Classes.is(player, PlayerClass.RIDER)) {
			mounted(player, null);
		}
	}

	/** A Rider in the saddle: their horse quicker, their blows from it heavier; both come off when they get down. */
	private static void mounted(ServerPlayer player, @Nullable LivingEntity mount) {
		LivingEntity was = MOUNTS.get(player.getUUID());
		if (was == mount) {
			return;
		}
		if (was != null) {
			AttributeInstance speed = was.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.removeModifier(JaymosKingdoms.id(MOUNT_ID));
			}
		}
		if (mount == null) {
			MOUNTS.remove(player.getUUID());
		} else {
			MOUNTS.put(player.getUUID(), mount);
			AttributeInstance speed = mount.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(JaymosKingdoms.id(MOUNT_ID), RIDER_MOUNT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
		Skills.modifier(player, Attributes.ATTACK_DAMAGE, MOUNTED_ID, mount == null ? 0.0 : RIDER_MOUNTED_DAMAGE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	/** How fast a horse under {@code rider} spends its stamina (a Rider's tires slower). */
	public static double stamina(net.minecraft.world.entity.@Nullable Entity rider) {
		return rider instanceof ServerPlayer sp && Classes.is(sp, PlayerClass.RIDER) ? RIDER_STAMINA : 1.0;
	}

	/** The trust a horse gains in {@code player} from {@code delta} (a Rider wins it sooner). */
	public static float trust(net.minecraft.world.entity.player.Player player, float delta) {
		return delta > 0.0F && Classes.is(player, PlayerClass.RIDER) ? delta * RIDER_TRUST : delta;
	}

	/** An Archer's drawn bow: the two-block jump and the soft landing while it's drawn. */
	private static void drawing(ServerPlayer player, boolean drawing) {
		Boolean was = DRAWING.put(player.getUUID(), drawing);
		if (was != null && was == drawing) {
			return;
		}
		Skills.modifier(player, Attributes.JUMP_STRENGTH, JUMP_ID, drawing ? ARCHER_JUMP : 0.0, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.FALL_DAMAGE_MULTIPLIER, DRAW_FALL_ID, drawing ? ARCHER_FALL : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	/** A bow or crossbow being drawn or loaded. */
	static boolean drawingBow(ServerPlayer player) {
		if (!player.isUsingItem()) {
			return false;
		}
		ItemStack use = player.getUseItem();
		return use.is(Items.BOW) || use.is(Items.CROSSBOW) || use.is(ItemTags.BOW_ENCHANTABLE) || use.is(ItemTags.CROSSBOW_ENCHANTABLE);
	}

	static void tick(ServerPlayer player) {
		PlayerClass cls = Classes.classOf(player);
		if (cls == PlayerClass.ARCHER) {
			drawing(player, drawingBow(player));
		} else if (cls == PlayerClass.RIDER) {
			mounted(player, player.getVehicle() instanceof LivingEntity mount && mount.getControllingPassenger() == player ? mount : null);
		} else if (cls == PlayerClass.SURVIVALIST) {
			// Second Wind: a while unhurt, they mend on their own
			int since = player.tickCount - LAST_HURT.getOrDefault(player.getUUID(), Integer.MIN_VALUE / 2);
			if (since >= SURVIVALIST_CALM && player.tickCount % SURVIVALIST_MEND_EVERY == 0 && player.getHealth() < player.getMaxHealth() && !Downed.isDowned(player)) {
				player.heal(SURVIVALIST_MEND);
			}
		} else if (cls == PlayerClass.BRUTE) {
			// a shield knocked aside (an axe, a mace) comes back in half the time: the cooldown is put back shorter when it starts
			ItemStack shield = player.getOffhandItem().has(DataComponents.BLOCKS_ATTACKS) ? player.getOffhandItem()
				: player.getMainHandItem().has(DataComponents.BLOCKS_ATTACKS) ? player.getMainHandItem() : ItemStack.EMPTY;
			boolean down = !shield.isEmpty() && player.getCooldowns().isOnCooldown(shield);
			Boolean was = SHIELD_DOWN.put(player.getUUID(), down);
			if (down && (was == null || !was)) {
				player.getCooldowns().addCooldown(shield, BRUTE_SHIELD_DOWN);
			}
		}
	}

	/**
	 * {@code target} taking {@code amount} from {@code source}: what the classes add or take away. An Archer's arrows harder
	 * (and at the head, harder again); (0.7.184) a Marksman's crossbow bolts harder, a Thief's backstab, a Warlord's cleave,
	 * a Brute's stagger, a Rider's horse barded, a Sorcerer warded from their own element, and a Survivalist's calm broken.
	 */
	public static float dealt(LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount) {
		if (target instanceof ServerPlayer hurt) {
			PlayerClass cls = Classes.classOf(hurt);
			if (cls == PlayerClass.SURVIVALIST) {
				LAST_HURT.put(hurt.getUUID(), hurt.tickCount);
			} else if (cls == PlayerClass.SORCERER && ownElement(Spellcraft.element(hurt), source)) {
				amount *= SORCERER_WARD;
			}
		}
		if (target.getControllingPassenger() instanceof ServerPlayer rider && Classes.is(rider, PlayerClass.RIDER)) {
			amount *= RIDER_BARDING;
		}
		if (!(source.getEntity() instanceof ServerPlayer sp) || target == sp) {
			return amount;
		}
		PlayerClass cls = Classes.classOf(sp);
		if (cls == null) {
			return amount;
		}
		if (source.getDirectEntity() instanceof AbstractArrow arrow) {
			if (cls == PlayerClass.ARCHER) {
				amount *= (float) ARCHER_DAMAGE;
				double hitY = arrow.getY() + arrow.getBbHeight() * 0.5;
				boolean head = hitY >= target.getEyeY() - HEADSHOT_BELOW_EYES;
				if (head) {
					amount *= ARCHER_HEADSHOT;
				}
				pin(sp, target, arrow, amount, head, hitY);
			} else if (cls == PlayerClass.MARKSMAN && arrow.getWeaponItem() != null && arrow.getWeaponItem().getItem() instanceof net.minecraft.world.item.CrossbowItem) {
				amount *= (float) (MARKSMAN_BOLTS + (sp.isShiftKeyDown() ? MARKSMAN_STEADY : 0.0));
			}
			return amount;
		}
		if (source.getDirectEntity() != sp || cleaving) {
			return amount;
		}
		ItemStack held = HiddenBlade.weapon(sp);
		switch (cls) {
			case THIEF -> {
				if (held.is(LIGHT_BLADES) && behind(target, sp)) {
					amount *= THIEF_BACKSTAB;
					if (sp.level() instanceof net.minecraft.server.level.ServerLevel level) {
						level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, target.getX(), target.getY(0.6), target.getZ(), 8, 0.2, 0.2, 0.2, 0.2);
					}
				}
			}
			case WARLORD -> {
				if (held.is(TWO_HANDED)) {
					cleave(sp, target, amount * WARLORD_CLEAVE);
				}
			}
			case BRUTE -> {
				if (held.is(BLUDGEONS)) {
					target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, BRUTE_STAGGER_TICKS, BRUTE_STAGGER_LEVEL), sp);
				}
			}
			default -> {
			}
		}
		return amount;
	}

	/** An Archer's arrow landing: a killing shot nails the body to a wall, a headshot may nail the head, a leg shot pins the legs. */
	private static void pin(ServerPlayer archer, LivingEntity target, AbstractArrow arrow, float amount, boolean head, double hitY) {
		if (!(target.level() instanceof ServerLevel level) || IMPALED.containsKey(target)) {
			return;
		}
		Vec3 dir = arrow.getDeltaMovement().multiply(1.0, 0.0, 1.0);
		if (dir.lengthSqr() < 1.0E-4) {
			return;
		}
		dir = dir.normalize();
		// facing out of the wall, back toward the archer
		float yaw = (float) Math.toDegrees(Math.atan2(dir.x, -dir.z));
		if (arrow.isCritArrow() && amount >= ARCHER_IMPALE_DAMAGE && amount >= target.getHealth() + target.getAbsorptionAmount()) {
			Vec3 wall = wall(level, target, dir, target.getY() + target.getBbHeight() * 0.6);
			if (wall != null) {
				// flush against the wall, its feet where they were; Kitchen hangs its corpse there by the tag
				double half = target.getBbWidth() * 0.5 + 0.05;
				Vec3 at = new Vec3(wall.x - dir.x * half, target.getY(), wall.z - dir.z * half);
				String tag = CORPSE_PIN_TAG + Math.round(yaw);
				target.addTag(tag);
				target.setPos(at.x, at.y, at.z);
				target.setDeltaMovement(Vec3.ZERO);
				IMPALED.put(target, new Impale(at, tag));
				level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.TRIDENT_HIT, net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 0.6F);
				return;
			}
		}
		if (head) {
			if (archer.getRandom().nextInt(ARCHER_BEHEAD_ONE_IN) == 0) {
				Vec3 wall = wall(level, target, dir, hitY);
				if (wall != null) {
					BEHEADINGS.add(new Beheading(target, archer, wall.subtract(dir.scale(0.18)), yaw));
				}
			}
			return;
		}
		if (target.getBbHeight() > 0.0F && (hitY - target.getY()) / target.getBbHeight() <= ARCHER_LEG_SHARE && !target.is(NO_LEGS)) {
			arrow.addTag(LEG_PIN_TAG);
			legPin(target, level.getGameTime() + (arrow.isCritArrow() ? ARCHER_LEG_PIN_CRIT_TICKS : ARCHER_LEG_PIN_TICKS));
			level.playSound(null, target.getX(), target.getY(), target.getZ(), net.minecraft.sounds.SoundEvents.ARROW_HIT, net.minecraft.sounds.SoundSource.PLAYERS, 0.8F, 0.6F);
		}
	}

	/** Where a wall stands within {@link #ARCHER_PIN_REACH} behind {@code target} at height {@code y}, along {@code dir}; null if none. */
	private static @Nullable Vec3 wall(ServerLevel level, LivingEntity target, Vec3 dir, double y) {
		Vec3 from = new Vec3(target.getX(), y, target.getZ());
		BlockHitResult hit = level.clip(new ClipContext(from, from.add(dir.scale(target.getBbWidth() * 0.5 + ARCHER_PIN_REACH)), ClipContext.Block.COLLIDER,
			ClipContext.Fluid.NONE, target));
		return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : null;
	}

	/** Legs pinned to the ground until {@code until}: no walking and no jumping (works on players, who move themselves). */
	private static void legPin(LivingEntity target, long until) {
		LEG_PINNED.merge(target, until, Math::max);
		legModifiers(target, true);
	}

	private static void legModifiers(LivingEntity target, boolean on) {
		AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
		AttributeInstance jump = target.getAttribute(Attributes.JUMP_STRENGTH);
		if (on) {
			if (speed != null) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(JaymosKingdoms.id(LEG_PIN_SPEED_ID), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
			if (jump != null) {
				jump.addOrUpdateTransientModifier(new AttributeModifier(JaymosKingdoms.id(LEG_PIN_JUMP_ID), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		} else {
			if (speed != null) {
				speed.removeModifier(JaymosKingdoms.id(LEG_PIN_SPEED_ID));
			}
			if (jump != null) {
				jump.removeModifier(JaymosKingdoms.id(LEG_PIN_JUMP_ID));
			}
		}
	}

	/** Pinned bodies held to their walls while they die, heads taken off and nailed up, pinned legs let go when it's time. */
	static void serverTick(net.minecraft.server.MinecraftServer server) {
		if (!IMPALED.isEmpty()) {
			var it = IMPALED.entrySet().iterator();
			while (it.hasNext()) {
				var e = it.next();
				LivingEntity target = e.getKey();
				Impale impale = e.getValue();
				if (target.isRemoved()) {
					it.remove();
				} else if (target.isAlive()) {
					// it lived through it: no hanging corpse after all
					target.removeTag(impale.tag());
					it.remove();
				} else {
					target.setPos(impale.at().x, impale.at().y, impale.at().z);
					target.setDeltaMovement(Vec3.ZERO);
				}
			}
		}
		if (!BEHEADINGS.isEmpty()) {
			List<Beheading> due = new ArrayList<>(BEHEADINGS);
			BEHEADINGS.clear();
			for (Beheading b : due) {
				if (!b.target().isRemoved()) {
					PowersCompat.beheadPinned(b.target(), b.by(), b.wall(), b.yaw());
				}
			}
		}
		if (!LEG_PINNED.isEmpty()) {
			var it = LEG_PINNED.entrySet().iterator();
			while (it.hasNext()) {
				var e = it.next();
				LivingEntity target = e.getKey();
				if (target.isRemoved() || !target.isAlive() || target.level().getGameTime() >= e.getValue()) {
					legModifiers(target, false);
					it.remove();
				} else if (target instanceof net.minecraft.world.entity.Mob mob) {
					mob.getNavigation().stop();
				}
			}
		}
	}

	/** Whether {@code target} has its back (near enough) to {@code attacker}. */
	private static boolean behind(LivingEntity target, LivingEntity attacker) {
		net.minecraft.world.phys.Vec3 look = target.getLookAngle().multiply(1.0, 0.0, 1.0);
		net.minecraft.world.phys.Vec3 to = attacker.position().subtract(target.position()).multiply(1.0, 0.0, 1.0);
		if (look.lengthSqr() < 1.0E-4 || to.lengthSqr() < 1.0E-4) {
			return false;
		}
		return look.normalize().dot(to.normalize()) < BACKSTAB_COS;
	}

	/** A Warlord's two-handed blow carried on to the foes standing by {@code struck}. */
	private static void cleave(ServerPlayer warlord, LivingEntity struck, float share) {
		if (share <= 0.0F || !(warlord.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return;
		}
		// (enemies, and whatever has its eye on the warlord: no townsfolk or friends caught in the swing)
		List<LivingEntity> near = CombatArts.foes(level, warlord, struck.getBoundingBox().inflate(WARLORD_CLEAVE_RANGE));
		near.remove(struck);
		near.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(struck)));
		cleaving = true;
		try {
			for (int i = 0; i < Math.min(WARLORD_CLEAVE_COUNT, near.size()); i++) {
				CombatArts.strike(level, warlord, near.get(i), share);
			}
		} finally {
			cleaving = false;
		}
		level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK, struck.getX(), struck.getY(0.5), struck.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
	}

	/** (0.7.231) Jaymo's Powers' bleeding damage. */
	private static final net.minecraft.resources.ResourceKey<net.minecraft.world.damagesource.DamageType> BLEEDING = net.minecraft.resources.ResourceKey.create(
		net.minecraft.core.registries.Registries.DAMAGE_TYPE, net.minecraft.resources.Identifier.fromNamespaceAndPath("jaymospowers", "bleeding"));

	/** Whether {@code source} is the harm of a Sorcerer's own {@code element}. */
	private static boolean ownElement(String element, net.minecraft.world.damagesource.DamageSource source) {
		return switch (element) {
			case "fire" -> source.is(net.minecraft.tags.DamageTypeTags.IS_FIRE);
			case "ice" -> source.is(net.minecraft.tags.DamageTypeTags.IS_FREEZING);
			case "lightning" -> source.is(net.minecraft.tags.DamageTypeTags.IS_LIGHTNING);
			case "water" -> source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING);
			case "earth" -> source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);
			case "air" -> source.is(net.minecraft.tags.DamageTypeTags.IS_FALL);
			// (0.7.212) a healer shrugs off poison and withering
			case "restoration" -> source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC) || source.is(net.minecraft.world.damagesource.DamageTypes.WITHER);
			// (0.7.231) a necromancer shrugs off withering, a blood mage bleeding (Jaymo's Powers')
			case "death" -> source.is(net.minecraft.world.damagesource.DamageTypes.WITHER);
			case "blood" -> source.is(BLEEDING);
			default -> false;
		};
	}

	/**
	 * A Brute with a shield raised: the share of {@code amount} that still gets through. Called for damage a raised shield
	 * didn't stop (what bypasses shields: explosions, magic, fire, and the leftover of a partial block).
	 */
	public static float blocked(ServerPlayer player, float amount) {
		return Classes.is(player, PlayerClass.BRUTE) && player.isBlocking() ? amount * (1.0F - BRUTE_BLOCK_LEFTOVER) : amount;
	}
}
