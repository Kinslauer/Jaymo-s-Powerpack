package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.37) Sorcery: the spellcasters of the camps and the courts, our own take on Iron's Spells 'n Spellbooks (that mod is
 * NeoForge-only and all rights reserved, so nothing of it is used: the spells, art and names here are original).
 * <ul>
 * <li>A barbarian camp's <b>shaman</b> ({@link #SHAMAN}): fire bolts, a ring of fire when cornered, a hex on a player
 * (weak and slow), a war chant that drives the raiders near him on (strength and speed), and he mends the hurt.</li>
 * <li>A camp's <b>necromancer</b> ({@link #NECROMANCER}, once a camp is a few days old, at once in a Grim ruin): he drains
 * the life from his foe, raises the dead from the ground or from corpses lying near (Jaymo's Kitchen's), throws up bone
 * spikes at anyone close, and whatever dies near him in a fight climbs back up as his {@link Thralls thrall}: villagers
 * as zombie villagers, horses as zombie horses, men as zombies. His thralls crumble when he falls.</li>
 * <li>A kingdom's <b>court wizard</b> ({@link #WIZARD}; a wealthy kingdom keeps two): arcane bolts (twice as hard on the
 * undead), a ward on the guards fighting near him, mending, a blast that throws back anyone too close, and he banishes
 * enemy thralls to dust. A tyrant king keeps a <b>court necromancer</b> instead. (0.5.53) With Minecraft Comes Alive the
 * court wizard is one of the kingdom's own elders, taking the office in old age ({@link CourtWizards}).</li>
 * <li>(0.7.231) A camp's <b>blood witch</b> ({@link #BLOOD_WITCH}, once a camp is a few days old): bolts that cut, a grip
 * that drags you to her, hemorrhages that burst every cut near, and she drinks the bleeding to heal ({@link Hemomancy}).</li>
 * </ul>
 * Casters keep their distance and cast; a player who kills one takes a spell scroll from the body ({@link Arcana}).
 */
public final class Sorcery {
	/** The roles (folk ids; clear of the realms', the camps' 20-24 and the goblins' 30-32). */
	public static final int NECROMANCER = 40, SHAMAN = 41, WIZARD = 42;
	/** (0.7.231) A camp's blood witch ({@link Hemomancy}). */
	public static final int BLOOD_WITCH = 43;
	/** Health and pace of each. */
	static final double NECROMANCER_HEALTH = 44.0, SHAMAN_HEALTH = 34.0, WIZARD_HEALTH = 40.0, BLOOD_WITCH_HEALTH = 38.0, CASTER_SPEED = 0.3;
	/** How far they cast from, and how often they cast (ticks). */
	public static final double RANGE = 18.0;
	public static final int CAST_TICKS = 40;
	/** A spell's spread: a foe this near counts as close (spikes, the ring of fire, the blast). */
	static final double CLOSE = 4.5;
	/** How far a shaman's chant and mending and a wizard's ward reach. */
	static final double SUPPORT_RANGE = 14.0;

	// the spells' strength and cooldowns (ticks)
	/** Life drain: damage, and what the necromancer takes back of it. */
	static final float DRAIN_DAMAGE = 4.0F, DRAIN_HEAL = 4.0F;
	/** Arcane bolt: damage (doubled on the undead). */
	static final float BOLT_DAMAGE = 5.0F;
	/** Bone spikes: damage, length (blocks). */
	static final float SPIKES_DAMAGE = 6.0F;
	static final int SPIKES_LENGTH = 7, SPIKES_COOLDOWN = 160;
	/** Raise dead: thralls raised at once, the most he keeps, how long they last, and its cooldown. */
	static final int RAISE_COUNT = 2, MAX_THRALLS = 5, RAISE_LIFE = 20 * 90, RAISE_COOLDOWN = 20 * 20;
	/** The dead near a necromancer in a fight rise: how near, how soon, for how long. */
	static final double TOLL_RANGE = 16.0;
	static final int TOLL_DELAY = 50, TOLL_LIFE = 20 * 120;
	/** How far a necromancer looks for corpses to raise. */
	static final double CORPSE_RANGE = 12.0;
	/** Hex: how long, and its cooldown. */
	static final int HEX_TICKS = 20 * 6, HEX_COOLDOWN = 20 * 15;
	/** Ring of fire: how long it sets them burning (seconds), its cooldown. */
	static final float RING_SECONDS = 4.0F;
	static final int RING_COOLDOWN = 20 * 10;
	/** War chant: how long it lasts, its cooldown, and how many fighting allies it takes to start one. */
	static final int CHANT_TICKS = 20 * 10, CHANT_COOLDOWN = 20 * 25, CHANT_ALLIES = 1;
	/** Mending: how much, below what share of health, and its cooldown. */
	static final float MEND_HEAL = 8.0F, MEND_BELOW = 0.6F;
	static final int MEND_COOLDOWN = 20 * 15;
	/** Ward: how long, how strong (absorption amplifier), its cooldown. */
	static final int WARD_TICKS = 20 * 15, WARD_LEVEL = 1, WARD_COOLDOWN = 20 * 20;
	/** Repulse: how hard it throws, its cooldown. */
	static final double REPULSE_POWER = 1.6;
	static final int REPULSE_COOLDOWN = 20 * 10;
	/** Banish: how many thralls at once, how far, its cooldown. */
	static final int BANISH_COUNT = 3, BANISH_COOLDOWN = 20 * 10;
	static final double BANISH_RANGE = 16.0;

	// who keeps them
	/** A camp takes a necromancer at this age (days), a second shaman at this one. */
	static final int NECROMANCER_DAYS = 2, SECOND_SHAMAN_DAYS = 5;
	/** (0.7.231) A camp takes a blood witch at this age (days). */
	static final int BLOOD_WITCH_DAYS = 4;
	/** A kingdom with this much in its treasury keeps a second wizard. */
	static final long SECOND_WIZARD_TREASURY = 300;
	/** The share of kingdoms that hold with magic at all (the rest keep no court mage). */
	static final float MAGIC_BELIEF = 0.6F;
	/** How often the roster is checked for them (ticks). */
	static final int STAFF_TICKS = 1200;

	/** Scroll drops: one in this many shamans leaves one (necromancers and wizards always do). */
	static final float SHAMAN_SCROLL_CHANCE = 0.6F;

	/** The spell timers' slots (on each caster, {@link Folk#spellTimers}). */
	static final int T_RAISE = 0, T_SPIKES = 1, T_HEX = 2, T_RING = 3, T_CHANT = 4, T_MEND = 5, T_WARD = 6, T_REPULSE = 7, T_BANISH = 8;
	public static final int TIMERS = 9;

	/** Dust colours of the spells. */
	static final int DRAIN_COLOUR = 0x7A1FA2, BOLT_COLOUR = 0x66CCFF, HEX_COLOUR = 0x3F7F2A;

	/** The dead about to rise (a necromancer near when they fell). */
	private record Rising(ResourceKey<Level> dimension, Vec3 at, EntityType<? extends Mob> type, UUID necromancer, long due) {
	}

	private static final List<Rising> RISING = new ArrayList<>();

	private Sorcery() {
	}

	static void register() {
		Folk.defineRole(new Folk.Role(NECROMANCER, "necromancer", true, false, null, (folk, faction) -> {
			base(folk, NECROMANCER_HEALTH);
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Arcana.BONE_STAFF));
			String title = faction.type() == FactionType.BARBARIAN ? "necromancer_of" : "court_necromancer_of";
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms." + title, faction.name()));
		}));
		Folk.defineRole(new Folk.Role(SHAMAN, "shaman", true, false, null, (folk, faction) -> {
			base(folk, SHAMAN_HEALTH);
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Arcana.TOTEM_STAFF));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.shaman_of", faction.name()));
		}));
		Folk.defineRole(new Folk.Role(BLOOD_WITCH, "blood_witch", true, false, null, (folk, faction) -> {
			base(folk, BLOOD_WITCH_HEALTH);
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Arcana.BONE_STAFF));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.blood_witch_of", faction.name()));
		}));
		Folk.defineRole(new Folk.Role(WIZARD, "wizard", true, false, null, (folk, faction) -> {
			base(folk, WIZARD_HEALTH);
			folk.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Arcana.WIZARD_STAFF));
			folk.setCustomName(Component.translatable("entity.jaymoskingdoms.court_wizard_of", faction.name()));
		}));
		ServerLivingEntityEvents.AFTER_DEATH.register(Sorcery::died);
		ServerTickEvents.END_SERVER_TICK.register(Sorcery::serverTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			RISING.clear();
			Thralls.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("caster").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("necromancer").executes(c -> summon(c.getSource(), NECROMANCER)))
				.then(Commands.literal("shaman").executes(c -> summon(c.getSource(), SHAMAN)))
				.then(Commands.literal("blood_witch").executes(c -> summon(c.getSource(), BLOOD_WITCH)))
				.then(Commands.literal("wizard").executes(c -> summon(c.getSource(), WIZARD))))));
	}

	private static void base(Folk folk, double health) {
		folk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
		folk.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(CASTER_SPEED);
		folk.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(RANGE + 10.0);
	}

	public static boolean isCaster(int role) {
		return role == NECROMANCER || role == SHAMAN || role == WIZARD || role == BLOOD_WITCH || role == Orcs.SHAMAN;
	}

	/** A camp's caster (they hunt as its raiders do). */
	static boolean campCaster(Folk folk) {
		return isCaster(folk.role()) && folk.style() == FactionType.BARBARIAN;
	}

	// ---- who keeps them ----------------------------------------------------------------------------------------------

	private static void serverTick(MinecraftServer server) {
		ServerLevel overworld = Factions.level(server);
		long now = overworld.getGameTime();
		if (!RISING.isEmpty()) {
			rise(server, now);
		}
		if (now % STAFF_TICKS == 0) {
			for (Faction f : Factions.all(server)) {
				if (!f.fallen()) {
					staff(server, f, now);
				}
			}
		}
	}

	/** What {@code f} should keep of {@code role}. */
	static int wanted(MinecraftServer server, Faction f, int role, long now) {
		if (f.type() == FactionType.BARBARIAN) {
			Camps.CampData data = Camps.data(server, f.id());
			if (data == null) {
				return 0;
			}
			long days = (now - data.founded()) / Camps.DAY_TICKS;
			if (data.orc()) {
				// (0.5.45) an orc camp keeps a shaman of its own, a second once it's grown
				return role == Orcs.SHAMAN ? days >= SECOND_SHAMAN_DAYS ? 2 : 1 : 0;
			}
			if (!Camps.BARBARIAN.equals(data.kind())) {
				return 0;
			}
			Holds.Hold hold = Holds.get(server, f.id());
			return switch (role) {
				case SHAMAN -> days >= SECOND_SHAMAN_DAYS ? 2 : 1;
				case NECROMANCER -> days >= NECROMANCER_DAYS || hold != null && hold.isGrim() ? 1 : 0;
				case BLOOD_WITCH -> days >= BLOOD_WITCH_DAYS ? 1 : 0;
				default -> 0;
			};
		}
		if (f.type() == FactionType.KINGDOM) {
			if (!believesInMagic(f)) {
				return 0;
			}
			boolean tyrant = Tempers.of(f) == Tempers.Temper.TYRANT;
			return switch (role) {
				case NECROMANCER -> tyrant ? 1 : 0;
				case WIZARD -> (tyrant ? 0 : 1) + (f.treasury() >= SECOND_WIZARD_TREASURY ? 1 : 0);
				default -> 0;
			};
		}
		return 0;
	}

	/** Not every kingdom believes in magic: whether this one does is fixed for good by its id ({@link #MAGIC_BELIEF} of them do). */
	public static boolean believesInMagic(Faction f) {
		return net.minecraft.util.RandomSource.create(f.id() * 0x9E3779B97F4A7C15L ^ 0x5EC12E7L).nextFloat() < MAGIC_BELIEF;
	}

	/** Places for the casters it lacks (hired as any other hand is, {@link Economy#hire}). */
	private static void staff(MinecraftServer server, Faction f, long now) {
		for (int role : new int[] {SHAMAN, NECROMANCER, BLOOD_WITCH, WIZARD, Orcs.SHAMAN}) {
			int want = wanted(server, f, role, now);
			int have = f.holdings().count(role) + f.holdings().vacancies().getOrDefault(Folk.roleName(role), 0);
			if (have < want) {
				int add = want - have;
				Factions.update(server, f.id(), x -> x.withHoldings(x.holdings().adjustVacancy(role, add)));
			}
		}
	}

	/**
	 * A caster taken on: a court's in its hall, a camp's by its fire. (0.5.53) With MCA a kingdom's wizard is one of its
	 * elders instead, or nobody yet if it has none to spare.
	 */
	static @Nullable Worker spawn(ServerLevel level, Faction faction, int role) {
		if (CourtWizards.inPlace(faction, role)) {
			return CourtWizards.enlist(level, faction, null);
		}
		Folk folk = KingdomEvents.spawn(level, faction, role);
		Castle.Keep keep = Factions.keep(level.getServer(), faction.id());
		if (folk != null && keep != null && faction.type().isRealm()) {
			BlockPos hall = keep.hall();
			folk.snapTo(hall.getX() + 0.5, hall.getY(), hall.getZ() + 0.5, folk.getYRot(), 0.0F);
			folk.setHomeTo(hall, 8);
		}
		return folk;
	}

	/** {@code /kingdom caster <kind>}: one for the faction nearest you, at your feet (for testing). */
	private static int summon(CommandSourceStack source, int role) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = player.level();
		Faction f = Factions.around(level.getServer(), level, player.blockPosition(), 400);
		if (f == null) {
			source.sendFailure(Component.translatable("message.jaymoskingdoms.caster_none"));
			return 0;
		}
		if (CourtWizards.inPlace(f, role)) {
			// (0.5.53) with MCA: the grown villager nearest you is made its court wizard, old or not
			Worker w = CourtWizards.summon(level, f, player);
			if (w == null) {
				source.sendFailure(Component.translatable("message.jaymoskingdoms.wizard_none"));
				return 0;
			}
			source.sendSuccess(() -> Component.translatable("message.jaymoskingdoms.caster_summoned", w.mob().getDisplayName(), f.name()), true);
			return 1;
		}
		Folk folk = KingdomEvents.spawn(level, f, role);
		if (folk == null) {
			return 0;
		}
		folk.snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), 0.0F);
		folk.setHomeTo(player.blockPosition(), Folk.HOME_RADIUS);
		source.sendSuccess(() -> Component.translatable("message.jaymoskingdoms.caster_summoned", folk.getDisplayName(), f.name()), true);
		return 1;
	}

	// ---- casting -----------------------------------------------------------------------------------------------------

	/**
	 * A caster's own tick (from {@link Folk#customServerAiStep}): the timers run down; out of reach or sight of its foe it
	 * closes in; a shaman chants and mends, a wizard wards and mends, whether or not it has a foe of its own.
	 */
	static void tick(ServerLevel level, Folk folk) {
		int[] timers = folk.spellTimers();
		for (int i = 0; i < timers.length; i++) {
			if (timers[i] > 0) {
				timers[i]--;
			}
		}
		if (folk.tickCount % 10 != 0) {
			return;
		}
		LivingEntity target = folk.getTarget();
		if (target != null && target.isAlive() && (folk.distanceTo(target) > RANGE || !folk.getSensing().hasLineOfSight(target))) {
			folk.getNavigation().moveTo(target, 1.0);
		}
		switch (folk.role()) {
			case BLOOD_WITCH -> Hemomancy.witchTick(level, folk);
			case SHAMAN, Orcs.SHAMAN -> {
				if (timers[T_CHANT] <= 0 && fightingAllies(level, folk).size() >= CHANT_ALLIES) {
					chant(level, folk);
				} else if (timers[T_MEND] <= 0) {
					mend(level, folk);
				}
			}
			case WIZARD -> {
				com.jaymoskingdoms.spirits.FolkSpirits.tick(level, folk, target);
				List<Folk> fighting = fightingAllies(level, folk);
				if (timers[T_WARD] <= 0 && (target != null || !fighting.isEmpty())) {
					fighting.add(folk);
					ward(level, folk, timers, fighting);
				} else if (timers[T_MEND] <= 0) {
					mend(level, folk);
				} else if (timers[T_BANISH] <= 0) {
					banish(level, folk, timers);
				}
			}
			default -> {
			}
		}
	}

	/** A spell at {@code target} (from {@link Folk#performRangedAttack}, as an archer looses). */
	static void cast(Folk folk, LivingEntity target) {
		if (!(folk.level() instanceof ServerLevel level)) {
			return;
		}
		int[] timers = folk.spellTimers();
		double d = folk.distanceTo(target);
		folk.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		switch (folk.role()) {
			case NECROMANCER -> {
				if (d <= CLOSE && timers[T_SPIKES] <= 0) {
					spikes(level, folk, target);
				} else if (timers[T_RAISE] <= 0 && Thralls.count(folk.getUUID()) < MAX_THRALLS) {
					raiseDead(level, folk, target);
				} else {
					drain(level, folk, target, DRAIN_DAMAGE, DRAIN_HEAL);
				}
			}
			case BLOOD_WITCH -> Hemomancy.witchCast(level, folk, target, timers);
			case SHAMAN, Orcs.SHAMAN -> {
				if (d <= CLOSE && timers[T_RING] <= 0) {
					ring(level, folk);
				} else if (target instanceof ServerPlayer && timers[T_HEX] <= 0) {
					hex(level, folk, target);
				} else {
					fireBolt(level, folk, target);
				}
			}
			case WIZARD -> {
				if (d <= CLOSE && timers[T_REPULSE] <= 0) {
					repulse(level, folk, timers, e -> foe(folk, e));
				} else if (Thralls.isThrall(target) && timers[T_BANISH] <= 0) {
					banish(level, folk, timers);
				} else {
					arcaneBolt(level, folk, target);
				}
			}
			default -> {
			}
		}
	}

	/** Folk of its side near it with a fight on (itself left out). */
	private static List<Folk> fightingAllies(ServerLevel level, Folk folk) {
		return level.getEntitiesOfClass(Folk.class, folk.getBoundingBox().inflate(SUPPORT_RANGE),
			f -> f != folk && f.isAlive() && f.combatant() && folk.sameSide(f) && f.getTarget() != null && f.getTarget().isAlive());
	}

	/** {@code e} is fair game for one of {@code caster}'s blasts: its foe, or something fighting its side; never its own side. */
	public static boolean foe(Folk caster, LivingEntity e) {
		if (e == caster || !e.isAlive() || e instanceof Player p && (p.isCreative() || p.isSpectator())) {
			return false;
		}
		if (e instanceof Folk f && caster.sameSide(f) || Thralls.refuses(caster, e)) {
			return false;
		}
		if (e == caster.getTarget()) {
			return true;
		}
		if (e instanceof Mob m && m.getTarget() != null && (m.getTarget() == caster || m.getTarget() instanceof Folk f && caster.sameSide(f))) {
			return true;
		}
		return e instanceof Enemy && caster.canAttack(e);
	}

	/** A line of {@code particle} from the caster's hand to {@code to}. */
	static void beam(ServerLevel level, LivingEntity from, Vec3 to, ParticleOptions particle) {
		Vec3 start = from.getEyePosition().add(0.0, -0.3, 0.0);
		Vec3 step = to.subtract(start);
		int n = Math.max(2, (int)(step.length() * 3.0));
		for (int i = 0; i <= n; i++) {
			Vec3 p = start.add(step.scale(i / (double)n));
			level.sendParticles(particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	private static DamageSource magic(ServerLevel level, Entity caster) {
		return level.damageSources().indirectMagic(caster, caster);
	}

	// the necromancer's

	/** Life drain: a violet thread from his hand to the foe; what it takes, he keeps. */
	static void drain(ServerLevel level, LivingEntity caster, LivingEntity target, float damage, float heal) {
		Mana.ripple(caster, Mana.HARM);
		beam(level, caster, target.getBoundingBox().getCenter(), new DustParticleOptions(DRAIN_COLOUR, 1.2F));
		if (target.hurtServer(level, magic(level, caster), damage)) {
			caster.heal(heal);
			level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, target.getX(), target.getY(0.6), target.getZ(), 4, 0.2, 0.2, 0.2, 0.1);
		}
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 0.8F, 0.6F);
	}

	/** Bone spikes: a line of them bursting up from the ground at the foe, cutting whatever of the other side stands there. */
	private static void spikes(ServerLevel level, Folk folk, LivingEntity target) {
		Mana.ripple(folk, Mana.HARM);
		folk.spellTimers()[T_SPIKES] = SPIKES_COOLDOWN;
		Vec3 dir = new Vec3(target.getX() - folk.getX(), 0.0, target.getZ() - folk.getZ());
		dir = dir.lengthSqr() < 1.0E-4 ? folk.getLookAngle().multiply(1.0, 0.0, 1.0).normalize() : dir.normalize();
		List<LivingEntity> hit = new ArrayList<>();
		for (int i = 1; i <= SPIKES_LENGTH; i++) {
			Vec3 p = folk.position().add(dir.scale(i));
			level.sendParticles(ParticleTypes.CRIT, p.x, p.y + 0.3, p.z, 6, 0.2, 0.4, 0.2, 0.15);
			level.sendParticles(new DustParticleOptions(0xE8E2CF, 1.6F), p.x, p.y + 0.5, p.z, 8, 0.15, 0.5, 0.15, 0.0);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(p.x - 0.7, p.y - 0.5, p.z - 0.7, p.x + 0.7, p.y + 2.0, p.z + 0.7),
				e -> !hit.contains(e) && foe(folk, e))) {
				hit.add(e);
				if (e.hurtServer(level, magic(level, folk), SPIKES_DAMAGE)) {
					e.setDeltaMovement(e.getDeltaMovement().add(0.0, 0.45, 0.0));
				}
			}
		}
		level.playSound(null, folk.getX(), folk.getY(), folk.getZ(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.HOSTILE, 1.0F, 0.8F);
		level.playSound(null, folk.getX(), folk.getY(), folk.getZ(), SoundEvents.SKELETON_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.6F);
	}

	/**
	 * Raise dead: the corpses lying near him (Jaymo's Kitchen's, not players') get up first; else the dead claw their way
	 * out of the ground beside him, a zombie and a skeleton (of his land's kind).
	 */
	private static void raiseDead(ServerLevel level, Folk folk, LivingEntity target) {
		Mana.ripple(folk, Mana.WILL);
		folk.spellTimers()[T_RAISE] = RAISE_COOLDOWN;
		int room = Math.min(RAISE_COUNT, MAX_THRALLS - Thralls.count(folk.getUUID()));
		int raised = 0;
		if (KitchenCompat.loaded()) {
			List<Entity> corpses = level.getEntities(folk, folk.getBoundingBox().inflate(CORPSE_RANGE), KitchenCompat::isRaisableCorpse);
			corpses.sort(Comparator.comparingDouble(folk::distanceToSqr));
			for (Entity corpse : corpses) {
				if (raised >= room) {
					break;
				}
				EntityType<? extends Mob> type = risenAs(KitchenCompat.sourceType(corpse), folk.climate());
				if (type != null && Thralls.raise(level, type, corpse.position(), folk.factionId(), folk.getUUID(), false, RAISE_LIFE, target) != null) {
					beam(level, folk, corpse.position().add(0.0, 0.3, 0.0), new DustParticleOptions(DRAIN_COLOUR, 1.0F));
					corpse.discard();
					raised++;
				}
			}
		}
		Climate climate = folk.climate();
		for (int i = raised; i < room; i++) {
			EntityType<? extends Mob> type = i % 2 == 0 ? zombieOf(climate) : skeletonOf(climate);
			Thralls.raise(level, type, groundNear(level, folk), folk.factionId(), folk.getUUID(), false, RAISE_LIFE, target);
		}
		level.playSound(null, folk.getX(), folk.getY(), folk.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.HOSTILE, 1.2F, 0.7F);
		level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, folk.getX(), folk.getY() + 1.0, folk.getZ(), 20, 0.4, 0.8, 0.4, 0.02);
	}

	static EntityType<? extends Mob> zombieOf(Climate climate) {
		return climate == Climate.DESERT || climate == Climate.BADLANDS ? EntityTypes.HUSK : EntityTypes.ZOMBIE;
	}

	static EntityType<? extends Mob> skeletonOf(Climate climate) {
		return climate == Climate.SNOWY ? EntityTypes.STRAY : climate == Climate.SWAMP ? EntityTypes.BOGGED : EntityTypes.SKELETON;
	}

	/** Free ground a step or three from {@code by}, or its own feet. */
	static Vec3 groundNear(ServerLevel level, LivingEntity by) {
		var random = level.getRandom();
		for (int tries = 0; tries < 10; tries++) {
			BlockPos base = by.blockPosition().offset(random.nextInt(7) - 3, 0, random.nextInt(7) - 3);
			for (int dy = 1; dy >= -2; dy--) {
				BlockPos p = base.above(dy);
				if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && level.getBlockState(p.below()).isSolid()) {
					return Vec3.atBottomCenterOf(p);
				}
			}
		}
		return by.position();
	}

	/** What the dead of {@code type} rise as (null: they don't). */
	static @Nullable EntityType<? extends Mob> risenAs(@Nullable EntityType<?> type, Climate climate) {
		if (type == null) {
			return null;
		}
		if (type == EntityTypes.VILLAGER || type == EntityTypes.WANDERING_TRADER || type == EntityTypes.ZOMBIE_VILLAGER) {
			return EntityTypes.ZOMBIE_VILLAGER;
		}
		if (type == EntityTypes.HORSE || type == EntityTypes.DONKEY || type == EntityTypes.MULE || type == EntityTypes.ZOMBIE_HORSE) {
			return EntityTypes.ZOMBIE_HORSE;
		}
		if (type == EntityTypes.SKELETON || type == EntityTypes.STRAY || type == EntityTypes.BOGGED || type == EntityTypes.WITHER_SKELETON) {
			return skeletonOf(climate);
		}
		if (type == EntityTypes.PLAYER || type == JaymosKingdoms.FOLK || type == EntityTypes.ZOMBIE || type == EntityTypes.HUSK
			|| type == EntityTypes.DROWNED || type == EntityTypes.PILLAGER || type == EntityTypes.VINDICATOR || type == EntityTypes.EVOKER
			|| type == EntityTypes.WITCH || type == EntityTypes.ILLUSIONER) {
			return zombieOf(climate);
		}
		return null;
	}

	// the shaman's

	/** Fire bolt: a small fireball, as a blaze throws. */
	private static void fireBolt(ServerLevel level, Folk folk, LivingEntity target) {
		Mana.ripple(folk, Mana.HARM);
		Vec3 dir = new Vec3(target.getX() - folk.getX(), target.getY(0.5) - folk.getEyeY() + 0.2, target.getZ() - folk.getZ()).normalize();
		SmallFireball bolt = new SmallFireball(level, folk, dir);
		bolt.setPos(folk.getX() + dir.x * 0.6, folk.getEyeY() - 0.2, folk.getZ() + dir.z * 0.6);
		level.addFreshEntity(bolt);
		level.playSound(null, folk.getX(), folk.getY(), folk.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.9F, 0.9F);
	}

	/** Ring of fire: everything of the other side close to him catches fire and is thrown back. */
	private static void ring(ServerLevel level, Folk folk) {
		Mana.ripple(folk, Mana.HARM);
		folk.spellTimers()[T_RING] = RING_COOLDOWN;
		for (int i = 0; i < 24; i++) {
			double a = i * Math.PI * 2.0 / 24.0;
			level.sendParticles(ParticleTypes.FLAME, folk.getX() + Math.cos(a) * 2.5, folk.getY() + 0.2, folk.getZ() + Math.sin(a) * 2.5, 3, 0.1, 0.2, 0.1, 0.02);
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, folk.getBoundingBox().inflate(CLOSE), e -> foe(folk, e))) {
			e.igniteForSeconds(RING_SECONDS);
			e.knockback(0.8, folk.getX() - e.getX(), folk.getZ() - e.getZ(), magic(level, folk), 0.0F);
		}
		level.playSound(null, folk.getX(), folk.getY(), folk.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.2F, 0.6F);
	}

	/** Hex: green smoke round the foe; weak and slow for a while. */
	private static void hex(ServerLevel level, Folk folk, LivingEntity target) {
		Mana.ripple(folk, Mana.HARM);
		folk.spellTimers()[T_HEX] = HEX_COOLDOWN;
		beam(level, folk, target.getBoundingBox().getCenter(), new DustParticleOptions(HEX_COLOUR, 1.0F));
		target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, HEX_TICKS, 0), folk);
		target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, HEX_TICKS, 1), folk);
		level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY(0.6), target.getZ(), 20, 0.3, 0.5, 0.3, 0.05);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ILLUSIONER_PREPARE_BLINDNESS, SoundSource.HOSTILE, 1.0F, 0.8F);
	}

	/** War chant: a drumbeat and a shout; the raiders fighting near him hit harder and run faster. */
	private static void chant(ServerLevel level, Folk folk) {
		Mana.ripple(folk, Mana.WARD);
		folk.spellTimers()[T_CHANT] = CHANT_COOLDOWN;
		List<Folk> allies = fightingAllies(level, folk);
		allies.add(folk);
		for (Folk ally : allies) {
			ally.addEffect(new MobEffectInstance(MobEffects.STRENGTH, CHANT_TICKS, 0), folk);
			ally.addEffect(new MobEffectInstance(MobEffects.SPEED, CHANT_TICKS, 0), folk);
			level.sendParticles(ParticleTypes.ANGRY_VILLAGER, ally.getX(), ally.getY() + 2.1, ally.getZ(), 1, 0.1, 0.1, 0.1, 0.0);
		}
		level.playSound(null, folk.getX(), folk.getY(), folk.getZ(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.HOSTILE, 3.0F, 0.6F);
		level.playSound(null, folk.getX(), folk.getY(), folk.getZ(), SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.6F, 1.4F);
		Speech.say(level, folk, Component.translatable("speech.jaymoskingdoms.chant_" + level.getRandom().nextInt(4)), Speech.RED, 24.0);
	}

	/** Mending: the worst hurt of his side near him (himself too) is healed. */
	private static void mend(ServerLevel level, Folk folk) {
		mend(level, folk, folk.spellTimers(), level.getEntitiesOfClass(Folk.class, folk.getBoundingBox().inflate(SUPPORT_RANGE),
			f -> f == folk || folk.sameSide(f)));
	}

	/** Mending: the worst hurt of {@code side} (the caster's own, near him) is healed. */
	static void mend(ServerLevel level, LivingEntity caster, int[] timers, List<? extends LivingEntity> side) {
		LivingEntity worst = side.stream().filter(e -> e.isAlive() && e.getHealth() < e.getMaxHealth() * MEND_BELOW)
			.min(Comparator.comparingDouble(e -> e.getHealth() / e.getMaxHealth())).orElse(null);
		if (worst == null) {
			return;
		}
		timers[T_MEND] = MEND_COOLDOWN;
		Mana.ripple(caster, Mana.HEAL);
		worst.heal(MEND_HEAL);
		if (worst != caster) {
			beam(level, caster, worst.getBoundingBox().getCenter(), ParticleTypes.HAPPY_VILLAGER);
		}
		level.sendParticles(ParticleTypes.HEART, worst.getX(), worst.getY() + 2.0, worst.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
		// (0.7.212) Restoration's gold ring where it lands, so a field healer is plain to see in a fight
		Restoration.ring(level, worst.position().add(0.0, 0.1, 0.0), 1.2, Restoration.GOLD_MOTE, 18);
		level.playSound(null, worst.getX(), worst.getY(), worst.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.5F, 1.2F);
	}

	// the wizard's

	/** Arcane bolt: a blue lance of light; twice as hard on the undead. */
	static void arcaneBolt(ServerLevel level, LivingEntity folk, LivingEntity target) {
		// (0.7.235) every cast ripples the caster's aura with its intent
		Mana.ripple(folk, Mana.HARM);
		beam(level, folk, target.getBoundingBox().getCenter(), new DustParticleOptions(BOLT_COLOUR, 1.2F));
		float damage = target.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD) ? BOLT_DAMAGE * 2.0F : BOLT_DAMAGE;
		target.hurtServer(level, magic(level, folk), damage);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.6), target.getZ(), 10, 0.2, 0.3, 0.2, 0.2);
		level.playSound(null, folk.getX(), folk.getY(), folk.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.NEUTRAL, 0.9F, 1.3F);
	}

	/** Ward: a shimmer over {@code allies}, the guards fighting near him (and himself): absorption hearts. */
	static void ward(ServerLevel level, LivingEntity folk, int[] timers, List<? extends LivingEntity> allies) {
		Mana.ripple(folk, Mana.WARD);
		timers[T_WARD] = WARD_COOLDOWN;
		for (LivingEntity ally : allies) {
			ally.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, WARD_TICKS, WARD_LEVEL), folk);
			level.sendParticles(ParticleTypes.END_ROD, ally.getX(), ally.getY() + 1.0, ally.getZ(), 10, 0.4, 0.6, 0.4, 0.02);
		}
		level.playSound(null, folk.getX(), folk.getY(), folk.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.NEUTRAL, 1.0F, 1.4F);
	}

	/** Repulse: a ring of force throws everything of the other side ({@code foe}) away from him, slowed. */
	static void repulse(ServerLevel level, LivingEntity folk, int[] timers, java.util.function.Predicate<LivingEntity> foe) {
		Mana.ripple(folk, Mana.HARM);
		timers[T_REPULSE] = REPULSE_COOLDOWN;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, folk.getBoundingBox().inflate(CLOSE), foe)) {
			e.knockback(REPULSE_POWER, folk.getX() - e.getX(), folk.getZ() - e.getZ(), magic(level, folk), 0.0F);
			e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1), folk);
		}
		level.sendParticles(ParticleTypes.CLOUD, folk.getX(), folk.getY() + 0.5, folk.getZ(), 30, 1.5, 0.3, 1.5, 0.1);
		level.sendParticles(ParticleTypes.ENCHANT, folk.getX(), folk.getY() + 1.0, folk.getZ(), 40, 1.5, 0.8, 1.5, 0.5);
		level.playSound(null, folk.getX(), folk.getY(), folk.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.NEUTRAL, 1.2F, 0.7F);
	}

	/** Banish: the enemy thralls near him fall back to dust. */
	static void banish(ServerLevel level, Mob folk, int[] timers) {
		Mana.ripple(folk, Mana.HARM);
		List<Mob> thralls = level.getEntitiesOfClass(Mob.class, folk.getBoundingBox().inflate(BANISH_RANGE),
			m -> m.isAlive() && Thralls.isThrall(m) && !Thralls.refuses(folk, m) && folk.getSensing().hasLineOfSight(m));
		if (thralls.isEmpty()) {
			return;
		}
		timers[T_BANISH] = BANISH_COOLDOWN;
		thralls.sort(Comparator.comparingDouble(folk::distanceToSqr));
		for (Mob thrall : thralls.subList(0, Math.min(BANISH_COUNT, thralls.size()))) {
			beam(level, folk, thrall.getBoundingBox().getCenter(), ParticleTypes.END_ROD);
			Thralls.crumble(level, thrall);
		}
		level.playSound(null, folk.getX(), folk.getY(), folk.getZ(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.NEUTRAL, 0.8F, 1.5F);
	}

	// ---- the dead ----------------------------------------------------------------------------------------------------

	/** A caster's scroll for its killer, his thralls let go; and whatever falls near a necromancer in a fight rises. */
	private static void died(LivingEntity entity, DamageSource source) {
		if (!(entity.level() instanceof ServerLevel level)) {
			return;
		}
		if (entity instanceof Folk folk && isCaster(folk.role())) {
			if (folk.role() == NECROMANCER) {
				Thralls.release(level, folk.getUUID());
			}
			if (source.getEntity() instanceof ServerPlayer && (folk.role() != SHAMAN && folk.role() != Orcs.SHAMAN || level.getRandom().nextFloat() < SHAMAN_SCROLL_CHANCE)) {
				folk.spawnAtLocation(level, Arcana.scrollFor(folk.role(), level.getRandom()));
			}
			return;
		}
		if (Thralls.isThrall(entity)) {
			return;
		}
		Folk necromancer = level.getEntitiesOfClass(Folk.class, entity.getBoundingBox().inflate(TOLL_RANGE),
				f -> f.role() == NECROMANCER && f.isAlive() && f.getTarget() != null && Thralls.count(f.getUUID()) < MAX_THRALLS).stream()
			.min(Comparator.comparingDouble(entity::distanceToSqr)).orElse(null);
		if (necromancer == null) {
			return;
		}
		EntityType<? extends Mob> type = risenAs(entity.getType(), necromancer.climate());
		if (type == null) {
			return;
		}
		level.sendParticles(ParticleTypes.SOUL, entity.getX(), entity.getY() + 0.5, entity.getZ(), 6, 0.3, 0.3, 0.3, 0.02);
		RISING.add(new Rising(level.dimension(), entity.position(), type, necromancer.getUUID(), level.getGameTime() + TOLL_DELAY));
	}

	/** The dead whose time has come get up (if their necromancer still stands); the body they left (Kitchen's) goes with them. */
	private static void rise(MinecraftServer server, long now) {
		for (Rising r : List.copyOf(RISING)) {
			if (now < r.due()) {
				continue;
			}
			RISING.remove(r);
			ServerLevel level = server.getLevel(r.dimension());
			if (level == null || !(level.getEntity(r.necromancer()) instanceof Folk necromancer) || !necromancer.isAlive()
				|| Thralls.count(necromancer.getUUID()) >= MAX_THRALLS) {
				continue;
			}
			if (KitchenCompat.loaded()) {
				level.getEntities((Entity)null, new AABB(r.at(), r.at()).inflate(2.0), KitchenCompat::isRaisableCorpse).stream()
					.min(Comparator.comparingDouble(e -> e.position().distanceToSqr(r.at()))).ifPresent(Entity::discard);
			}
			Thralls.raise(level, r.type(), r.at(), necromancer.factionId(), necromancer.getUUID(), false, TOLL_LIFE, necromancer.getTarget());
		}
	}
}
