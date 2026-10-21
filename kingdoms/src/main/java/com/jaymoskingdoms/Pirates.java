package com.jaymoskingdoms;

import com.jaymoskingdoms.sailing.Ship;
import java.util.EnumSet;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.42) Sailing, stage 3: pirates. A pirate crew is a camp's people ({@link FactionType#BARBARIAN} factions: a
 * {@link PirateCoves cove}'s, or a crew roaming free), in our own original looks (sun-browned sailors in striped shirts
 * and bandanas, a black flag over them). They sail ships of our own design ({@link com.jaymoskingdoms.sailing.Fleet})
 * and fight from them ({@link Corsairs}): their {@link #CAPTAIN} at the helm (a boss: a cutlass, a brace of pistols,
 * a red boss bar), {@link #GUNNER}s at the guns (they lay and fire them, and shoot pistols at anyone close), their
 * {@link #DECKHAND}s (cutlasses, boarding axes) and a {@link #POWDER_MONKEY} or two (ship's boys running powder to the
 * guns: the guns load faster while one lives). They hunt players, villagers, golems and every settlement's folk.
 *
 * <p>Also the sailors of the kingdoms: a coastal realm's {@link #SHIPWRIGHT} (keeps its harbour, builds its warship
 * again when she's lost) and the {@link #SEA_CAPTAIN} who commands its warship or a merchantman ({@link Navies}).
 */
public final class Pirates {
	/** The roles (folk ids; clear of the realms', the camps' 20-24, goblins' 30-32, casters' 40-42, orcs' 50-53, assassins' 60). */
	public static final int CAPTAIN = 70, GUNNER = 71, DECKHAND = 72, POWDER_MONKEY = 73, SHIPWRIGHT = 74, SEA_CAPTAIN = 75;
	/** (0.7.104) A bombardier: lobs lit {@link Dynamite} over walls and heads, and backs off from a foe who closes. */
	public static final int BOMBARDIER = 76;
	/** A captain: health, blows, pace, how hard he is to shove. */
	static final double CAPTAIN_HEALTH = 160.0, CAPTAIN_DAMAGE = 8.0, CAPTAIN_SPEED = 0.3, CAPTAIN_KNOCKBACK_RESIST = 0.6;
	/** A gunner's, a deckhand's and a powder monkey's health and blows; a sea captain's. */
	static final double GUNNER_HEALTH = 26.0, GUNNER_DAMAGE = 3.5, DECKHAND_HEALTH = 28.0, DECKHAND_DAMAGE = 5.0, MONKEY_HEALTH = 14.0,
		SEA_CAPTAIN_HEALTH = 70.0, SEA_CAPTAIN_DAMAGE = 6.5;
	/** A powder monkey is a boy: this share of a man's size. */
	static final double MONKEY_SCALE = 0.7;
	/** How far they spot prey. */
	static final double SIGHT = 32.0;
	/** Pistols (gunners and captains): from this far to this far, this often (ticks, plus up to the jitter), this hard. */
	static final double PISTOL_NEAREST = 3.0, PISTOL_FURTHEST = 22.0;
	static final int PISTOL_RELOAD = 90, PISTOL_JITTER = 50;
	static final float PISTOL_DAMAGE = 6.0F, PISTOL_SPEED = 3.2F, PISTOL_SPREAD = 4.0F;
	/** A bombardier's health and blows (his cutlass, when a foe gets close). */
	static final double BOMBARDIER_HEALTH = 24.0, BOMBARDIER_DAMAGE = 3.5;
	/** He lobs from this far to this far (closer, he draws his cutlass), and backs off from a foe nearer than {@link #BOMB_BACK_OFF}. */
	static final double BOMB_NEAREST = 3.5, BOMB_FURTHEST = 20.0, BOMB_BACK_OFF = 7.0;
	/** Between throws (ticks, plus up to the jitter); the stick's flight (ticks) at the least and the most. */
	static final int BOMB_RELOAD = 70, BOMB_JITTER = 40, BOMB_FLIGHT_MIN = 14, BOMB_FLIGHT_MAX = 30;
	/** How wide of his mark a throw lands at its furthest (blocks). */
	static final double BOMB_STRAY = 1.5;
	/** While fighting, a pirate shouts about once in this many ticks. */
	static final int SHOUT_ONE_IN = 300;
	/** Their colours: a red bandana, a black one; a sailor's striped shirt (white), a blue coat; tar-black breeches. */
	static final int RED_CLOTH = 0x9B2420, BLACK_CLOTH = 0x221E1C, SHIRT = 0xD8D2C0, COAT = 0x22304E, TAR = 0x2B2724, CAPTAIN_COAT = 0x5A1414;

	private static final String[] FIRST = {"Silas", "Bartholomew", "Edric", "Morgan", "Jonah", "Ezekiel", "Calloway", "Ned", "Ambrose", "Thaddeus",
		"Rook", "Ishmael", "Grisel", "Corwin", "Mercy", "Anne", "Hester", "Josiah", "Barnaby", "Lucan"};
	private static final String[] BYNAMES = {"Blackwater", "the Red", "Saltbeard", "Gallows", "Ironhook", "One-Eye", "Bonebreaker", "the Drowned",
		"Grimtide", "Cutlass", "Stormcrow", "Ashgrave", "the Cruel", "Brine", "Sharkstooth", "Halfmoon"};
	private static final String[] SHIP_FIRST = {"Black", "Crimson", "Grey", "Drowned", "Salt", "Storm", "Iron", "Bloody", "Silent", "Wicked", "Rotten",
		"Howling", "Sable", "Widow's", "Devil's", "Hangman's"};
	private static final String[] SHIP_SECOND = {"Gull", "Serpent", "Revenge", "Maiden", "Kraken", "Fortune", "Wraith", "Dog", "Corsair", "Tide",
		"Raven", "Shark", "Jest", "Lantern", "Bell", "Harpy"};
	private static final String[] NAVY_SHIPS = {"Resolute", "Valiant", "Steadfast", "Dauntless", "Vigilant", "Sovereign", "Intrepid", "Defiance",
		"Constant", "Swiftsure", "Warden", "Lionheart"};
	private static final String[] MERCHANT_SHIPS = {"Good Hope", "Plenty", "Fair Wind", "Honest Penny", "Merry Lass", "Harvest", "Gilded Swan",
		"Patience", "Silver Herring", "Bountiful"};

	/** Pirate → the game time his pistol is loaded again (kept in memory: a restart just loads it). */
	private static final Map<Folk, Long> PISTOL = new WeakHashMap<>();

	private Pirates() {
	}

	/** Defines the roles (call once at init). */
	static void init() {
		Folk.defineRole(new Folk.Role(CAPTAIN, "pirate_captain", true, true, BossEvent.BossBarColor.RED, (folk, faction) -> {
			base(folk, CAPTAIN_HEALTH, CAPTAIN_DAMAGE, CAPTAIN_SPEED);
			folk.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(CAPTAIN_KNOCKBACK_RESIST);
			folk.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2.0);
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			folk.setItemSlot(EquipmentSlot.OFFHAND, pistol());
			folk.setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, BLACK_CLOTH));
			folk.setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, CAPTAIN_COAT));
			folk.setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, TAR));
			folk.setItemSlot(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, BLACK_CLOTH));
			folk.setCustomName(Component.literal(captainName(folk.getUUID().getLeastSignificantBits() ^ faction.id())));
		}));
		Folk.defineRole(new Folk.Role(GUNNER, "pirate_gunner", true, false, null, (folk, faction) -> {
			base(folk, GUNNER_HEALTH, GUNNER_DAMAGE, 0.28);
			folk.setItemSlot(EquipmentSlot.MAINHAND, pistol());
			folk.setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, folk.getRandom().nextBoolean() ? RED_CLOTH : BLACK_CLOTH));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.pirate_gunner_of", faction.name()));
		}));
		Folk.defineRole(new Folk.Role(DECKHAND, "pirate_deckhand", true, false, null, (folk, faction) -> {
			base(folk, DECKHAND_HEALTH, DECKHAND_DAMAGE, 0.31);
			RandomSource random = folk.getRandom();
			Item[] weapons = {Items.IRON_SWORD, Items.IRON_SWORD, Items.STONE_SWORD, Items.IRON_AXE, Items.STONE_AXE};
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(weapons[random.nextInt(weapons.length)]));
			if (random.nextInt(3) > 0) {
				folk.setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, random.nextBoolean() ? RED_CLOTH : BLACK_CLOTH));
			}
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.pirate_of", faction.name()));
		}));
		Folk.defineRole(new Folk.Role(POWDER_MONKEY, "powder_monkey", false, false, null, (folk, faction) -> {
			base(folk, MONKEY_HEALTH, 1.0, 0.34);
			folk.getAttribute(Attributes.SCALE).setBaseValue(MONKEY_SCALE);
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GUNPOWDER));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.powder_monkey_of", faction.name()));
		}));
		Folk.defineRole(new Folk.Role(BOMBARDIER, "pirate_bombardier", true, false, null, (folk, faction) -> {
			base(folk, BOMBARDIER_HEALTH, BOMBARDIER_DAMAGE, 0.3);
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			folk.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Dynamite.ITEM));
			folk.setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, folk.getRandom().nextBoolean() ? RED_CLOTH : BLACK_CLOTH));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.pirate_bombardier_of", faction.name()));
		}));
		Folk.defineRole(new Folk.Role(SHIPWRIGHT, "shipwright", false, false, null, (folk, faction) -> {
			base(folk, 24.0, 2.0, 0.27);
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.shipwright_of", faction.name()));
		}));
		Folk.defineRole(new Folk.Role(SEA_CAPTAIN, "sea_captain", true, false, null, (folk, faction) -> {
			base(folk, SEA_CAPTAIN_HEALTH, SEA_CAPTAIN_DAMAGE, 0.3);
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			folk.setItemSlot(EquipmentSlot.HEAD, dyed(Items.LEATHER_HELMET, COAT));
			folk.setItemSlot(EquipmentSlot.CHEST, dyed(Items.LEATHER_CHESTPLATE, COAT));
			folk.setItemSlot(EquipmentSlot.LEGS, dyed(Items.LEATHER_LEGGINGS, SHIRT));
			folk.setItemSlot(EquipmentSlot.FEET, dyed(Items.LEATHER_BOOTS, BLACK_CLOTH));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.sea_captain_of", faction.name()));
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

	/** A flintlock pistol (ours, with TaCZ), or a crossbow without it (for the look; they fire it themselves). */
	static ItemStack pistol() {
		ItemStack gun = TaczGuns.gun("flintlock_pistol");
		return gun.isEmpty() ? new ItemStack(Items.CROSSBOW) : gun;
	}

	/** A pirate captain's name, the same every time for the same seed. */
	static String captainName(long seed) {
		RandomSource random = RandomSource.create(seed ^ 0x5EA5L);
		return "Captain " + FIRST[random.nextInt(FIRST.length)] + " " + BYNAMES[random.nextInt(BYNAMES.length)];
	}

	/** A pirate ship's name ("the Crimson Gull"). */
	static String pirateShip(RandomSource random) {
		return "the " + SHIP_FIRST[random.nextInt(SHIP_FIRST.length)] + " " + SHIP_SECOND[random.nextInt(SHIP_SECOND.length)];
	}

	/** A realm's warship's name, and a merchantman's. */
	static String navyShip(RandomSource random) {
		return "the " + NAVY_SHIPS[random.nextInt(NAVY_SHIPS.length)];
	}

	static String merchantShip(RandomSource random) {
		return "the " + MERCHANT_SHIPS[random.nextInt(MERCHANT_SHIPS.length)];
	}

	public static boolean isPirate(int role) {
		return role >= CAPTAIN && role <= POWDER_MONKEY || role == BOMBARDIER;
	}

	public static boolean isPirate(LivingEntity entity) {
		return entity instanceof Folk folk && isPirate(folk.role());
	}

	/** Never on a roster: a captain (his cove's leader, or one ashore), a shipwright, a sea captain, a bombardier (deckhands ashore are). */
	static boolean offRoster(int role) {
		return role == CAPTAIN || role == SHIPWRIGHT || role == SEA_CAPTAIN || role == GUNNER || role == POWDER_MONKEY || role == BOMBARDIER;
	}

	/** It's one of the sailors' roles (shown with its own skin). */
	public static boolean isSailor(int role) {
		return role >= CAPTAIN && role <= SEA_CAPTAIN || role == BOMBARDIER;
	}

	/**
	 * The pirates' extra goals (called from {@link Folk#registerGoals}): they hunt players, villagers, golems and every
	 * settlement's folk (other camps' aside, unless they start it). A realm's fighting folk hunt them back already
	 * ({@link Barbarians#addGoals}: pirates are a camp's people).
	 */
	static void addGoals(Folk folk, GoalSelector goals, GoalSelector targets) {
		targets.addGoal(2, new NearestAttackableTargetGoal<>(folk, LivingEntity.class, true,
			(target, level) -> isPirate(folk.role()) && folk.combatant() && prey(target)));
		goals.addGoal(1, new Lob(folk));
	}

	/** What a pirate attacks on sight. */
	static boolean prey(LivingEntity target) {
		if (target instanceof ServerPlayer player) {
			return !player.isCreative() && !player.isSpectator();
		}
		if (target instanceof Folk other) {
			return other.style() != FactionType.BARBARIAN;
		}
		return target instanceof Villager || target instanceof IronGolem;
	}

	/** A pirate's own tick (from {@link Folk#customServerAiStep}): pistols at a foe close by, and shouts in a fight. */
	static void tick(ServerLevel level, Folk folk) {
		int role = folk.role();
		if (role != CAPTAIN && role != GUNNER && role != DECKHAND && role != BOMBARDIER) {
			return;
		}
		LivingEntity foe = folk.getTarget();
		if (foe == null || !foe.isAlive()) {
			return;
		}
		RandomSource random = level.getRandom();
		if ((folk.tickCount + folk.getId()) % 10 == 0 && random.nextInt(SHOUT_ONE_IN / 10) == 0) {
			Speech.say(level, folk, Component.translatable("speech.jaymoskingdoms.pirate.cry_" + random.nextInt(8)), Speech.RED, 24.0);
		}
		if (role == DECKHAND || role == BOMBARDIER) {
			return;
		}
		long now = level.getGameTime();
		Long ready = PISTOL.get(folk);
		if (ready == null) {
			PISTOL.put(folk, now + random.nextInt(PISTOL_JITTER + 1));
			return;
		}
		if (now < ready) {
			return;
		}
		double d = folk.distanceTo(foe);
		if (d < PISTOL_NEAREST || d > PISTOL_FURTHEST || !folk.hasLineOfSight(foe)) {
			return;
		}
		PISTOL.put(folk, now + PISTOL_RELOAD + random.nextInt(PISTOL_JITTER + 1));
		shoot(level, folk, foe);
	}

	/** A pistol ball at {@code foe} (sparing his own side, as javelins do). */
	private static void shoot(ServerLevel level, Folk folk, LivingEntity foe) {
		Projectile ball = Javelins.WEAPONMOD_LOADED ? WeaponModJavelins.bullet(level, folk, PISTOL_DAMAGE) : null;
		if (ball == null) {
			AbstractArrow bolt = ProjectileUtil.getMobArrow(folk, new ItemStack(Items.ARROW), 1.0F, Folk.firingWeapon(folk));
			bolt.setBaseDamage(PISTOL_DAMAGE / PISTOL_SPEED);
			bolt.pickup = AbstractArrow.Pickup.DISALLOWED;
			ball = bolt;
		}
		Javelins.mark(ball);
		double dx = foe.getX() - folk.getX(), dz = foe.getZ() - folk.getZ();
		double dy = foe.getY(0.5) - ball.getY();
		double flat = Math.sqrt(dx * dx + dz * dz);
		folk.getLookControl().setLookAt(foe, 30.0F, 30.0F);
		Projectile.spawnProjectileUsingShoot(ball, level, ItemStack.EMPTY, dx, dy + flat * 0.03, dz, PISTOL_SPEED, PISTOL_SPREAD);
		Vec3 muzzle = folk.getEyePosition().add(new Vec3(dx, dy, dz).normalize().scale(0.9));
		level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 6, 0.08, 0.08, 0.08, 0.02);
		level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 1, 0.03, 0.03, 0.03, 0.01);
		level.playSound(null, folk.getX(), folk.getY(), folk.getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE,
			0.6F, 1.9F + level.getRandom().nextFloat() * 0.2F);
	}

	/** {@code folk} is a pirate captain who leads his cove (his death clears it; another captain's doesn't). */
	static boolean isCoveCaptain(Folk folk) {
		if (folk.role() != CAPTAIN || folk.level().getServer() == null) {
			return false;
		}
		Faction camp = Factions.get(folk.level().getServer(), folk.factionId());
		return camp != null && camp.leader().isPresent() && camp.leader().get().equals(folk.getUUID());
	}

	/** Whether {@code folk} is one of a ship's crew, at their station aboard her. */
	public static boolean aboard(Folk folk) {
		return folk.getVehicle() instanceof Ship && folk.hasAttached(Ship.SEAT);
	}

	/**
	 * (0.7.104) A bombardier's fight: from {@link #BOMB_NEAREST} to {@link #BOMB_FURTHEST} of his foe he keeps his
	 * distance (backing off from one who comes within {@link #BOMB_BACK_OFF}) and lobs lit dynamite at where the foe is
	 * going, sight or no sight (over a wall, onto a roof); closer, it gives way to his cutlass.
	 */
	static final class Lob extends Goal {
		private final Folk bomber;
		private int reload;
		private int backOff;

		Lob(Folk bomber) {
			this.bomber = bomber;
			this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (this.bomber.role() != BOMBARDIER || this.bomber.isSleeping()) {
				return false;
			}
			LivingEntity foe = this.bomber.getTarget();
			if (foe == null || !foe.isAlive()) {
				return false;
			}
			double d = this.bomber.distanceTo(foe);
			return d >= BOMB_NEAREST && d <= BOMB_FURTHEST;
		}

		@Override
		public void start() {
			this.reload = Math.max(this.reload, 10 + this.bomber.getRandom().nextInt(BOMB_JITTER + 1));
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			LivingEntity foe = this.bomber.getTarget();
			if (foe == null || !(this.bomber.level() instanceof ServerLevel level)) {
				return;
			}
			this.bomber.getLookControl().setLookAt(foe, 30.0F, 30.0F);
			double d = this.bomber.distanceTo(foe);
			if (d < BOMB_BACK_OFF && !aboard(this.bomber)) {
				if (--this.backOff <= 0 || this.bomber.getNavigation().isDone()) {
					this.backOff = 20;
					Vec3 away = DefaultRandomPos.getPosAway(this.bomber, 8, 4, foe.position());
					if (away != null) {
						this.bomber.getNavigation().moveTo(away.x, away.y, away.z, 1.1);
					}
				}
			} else {
				this.bomber.getNavigation().stop();
			}
			if (--this.reload > 0) {
				return;
			}
			RandomSource random = level.getRandom();
			this.reload = BOMB_RELOAD + random.nextInt(BOMB_JITTER + 1);
			int flight = (int) Math.round(Math.min(BOMB_FLIGHT_MAX, Math.max(BOMB_FLIGHT_MIN, 10.0 + d)));
			// where he'll be when it comes down, give or take
			double stray = BOMB_STRAY * d / BOMB_FURTHEST;
			Vec3 mark = foe.position().add(foe.getDeltaMovement().multiply(flight * 0.6, 0.0, flight * 0.6))
				.add((random.nextDouble() * 2.0 - 1.0) * stray, 0.0, (random.nextDouble() * 2.0 - 1.0) * stray);
			Vec3 from = new Vec3(this.bomber.getX(), this.bomber.getEyeY() - 0.2, this.bomber.getZ());
			this.bomber.swing(InteractionHand.OFF_HAND, SwingAnimation.DEFAULT, true);
			Dynamite.light(level, this.bomber, Dynamite.lob(from, mark, flight));
			if (random.nextInt(3) == 0) {
				Speech.say(level, this.bomber, Component.translatable("speech.jaymoskingdoms.pirate.bomb_" + random.nextInt(4)), Speech.RED, 24.0);
			}
		}
	}

	static void clear() {
		PISTOL.clear();
	}
}
