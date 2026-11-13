package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Crews: the ranks and the boss of a barbarian camp's band (the plain kind of camp, {@link Camps#BARBARIAN}). The camps
 * already raise a chief and a growing company of raiders, folk or MCA villagers alike; this gives that company
 * <ul>
 * <li>four ranks, kept on the fighter ({@link #CREW}): <b>plain</b> raiders as before, a few <b>strong</b> ones (iron
 * gear, more health), one <b>heavy</b> (a great axe and iron plate, a big body, slow and hard to shove) and the
 * <b>boss</b>, the chief, who takes a style;</li>
 * <li>a boss style drawn for the camp ({@link Style}): the Knife darts in and hamstrings, the Hammer slams the ground,
 * the Archer looses volleys, the Poisoner throws clouds and poisons with every blow, each with its own weapon;</li>
 * <li>a name and a title for the boss, and a bounty on his head with the nearest realm's court
 * ({@link Bounties#postCrew}) under the title only, since his own name is not known until he gives it.</li>
 * </ul>
 * A boss falling clears the camp as it always has ({@link Camps#chiefSlain}): the band scatters. Nothing here spawns:
 * a rank goes to whoever of the band is in the world, replacing the fallen as the camp takes new raiders in. It looks at
 * a camp once a second and only while a player is within {@link #SWEEP_RANGE} of it. Tuning is in the constants.
 */
public final class Crews {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** How often (ticks) the camps near players are looked at, and how near a player must be (blocks). */
	static final int EVERY = 20;
	static final double SWEEP_RANGE = 96.0;
	/** A camp's band is what stands this far (blocks) round its seat, sideways and up. */
	static final double BAND_RANGE = 56.0, BAND_HEIGHT = 32.0;
	/** Raiders it takes for the first heavy, for the first strong one, and for a second strong one. */
	static final int HEAVY_AT = 4, STRONG_AT = 3, STRONG_TWO_AT = 7;

	/** Strong: more health, harder blows. */
	static final double STRONG_HEALTH = 16.0, STRONG_DAMAGE = 1.5;
	/** Heavy: health, blows, armour, knockback resistance (hard to stagger), pace (a share off) and body (a share on). */
	static final double HEAVY_HEALTH = 45.0, HEAVY_DAMAGE = 3.5, HEAVY_ARMOR = 6.0, HEAVY_KNOCKBACK = 0.9, HEAVY_PACE = -0.25, HEAVY_SCALE = 0.3;

	/** The Knife: pace on top of the chief's, the lunge's range (blocks), pause (ticks) and speed; the hamstring (ticks of slowness) and the speed it gives him. */
	static final double KNIFE_PACE = 0.15;
	static final double KNIFE_LUNGE_MIN = 4.0, KNIFE_LUNGE_MAX = 11.0;
	static final int KNIFE_PAUSE = 120, KNIFE_LUNGE_TICKS = 40, KNIFE_LUNGE_LEVEL = 1, HAMSTRING_TICKS = 60, KNIFE_HIT_TICKS = 30;
	/** The Hammer: body and weight, the slam's reach (blocks), pause (ticks), blow and throw. */
	static final double HAMMER_SCALE = 0.15, HAMMER_KNOCKBACK = 0.3, HAMMER_PACE = -0.1;
	static final double SLAM_REACH = 4.5;
	static final int SLAM_PAUSE = 160;
	static final float SLAM_DAMAGE = 7.0F;
	static final double SLAM_THROW = 0.9, SLAM_LIFT = 0.45;
	/** The Archer: his volley's range (blocks), pause (ticks), arrows and their spread (degrees of aim). */
	static final double VOLLEY_MIN = 6.0, VOLLEY_MAX = 28.0;
	static final int VOLLEY_PAUSE = 100, VOLLEY_ARROWS = 3;
	static final float VOLLEY_SPREAD = 6.0F, VOLLEY_SPEED = 1.6F;
	/** The Poisoner: his cloud's range (blocks), pause (ticks), reach (blocks) and life (ticks); the poison of cloud and blow (ticks). */
	static final double CLOUD_MIN = 3.0, CLOUD_MAX = 16.0, CLOUD_RADIUS = 3.2;
	static final int CLOUD_PAUSE = 200, CLOUD_LIFE = 100, CLOUD_POISON = 60, BLOW_POISON = 80;
	/** A boss sees a foe this far (blocks) when his own target is not a player. */
	static final double SIGHT = 24.0;
	/** The boss's bounty (emeralds' worth): above the board's miniboss line ({@link Bounties#BOSS_PRICE}). How far a court may be from the camp (blocks). */
	static final int BOUNTY = 55;
	static final double COURT_RANGE = 600.0;
	/** Colours: the Poisoner's cloud, the Hammer's ring, the Knife's streak. */
	static final int GREEN = 0x6FA83A, DUST = 0xB09060, STEEL = 0xC8D0D8;

	// ---- ranks and styles ----------------------------------------------------------------------------------------------

	public static final int PLAIN = 0, STRONG = 1, HEAVY = 2, BOSS = 3;

	/** A boss's fighting style: what he carries, and the titles he may go by. */
	enum Style {
		KNIFE(Items.DIAMOND_SWORD, "the Quick", "Thin Smile", "Cutpurse", "the Whisper"),
		HAMMER(Items.MACE, "Skullfall", "the Anvil", "Ironwrist", "the Mountain"),
		ARCHER(Items.BOW, "Farsight", "the Crow", "Hollow-Eye", "the Hawk"),
		POISONER(Items.IRON_SWORD, "the Pale", "Greenhand", "Sicklebrew", "the Gentle");

		final Item weapon;
		final String[] titles;

		Style(Item weapon, String... titles) {
			this.weapon = weapon;
			this.titles = titles;
		}

		/** A camp's own style, always the same one. */
		static Style of(int camp) {
			return values()[Math.floorMod(Integer.hashCode(camp * 31 + 7) ^ 0x5DEECE6, values().length)];
		}
	}

	/** What a fighter is in its band: a rank, and (a boss) a style by index. */
	public record Crew(int rank, int style) {
		public static final Codec<Crew> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("rank").forGetter(Crew::rank),
			Codec.INT.optionalFieldOf("style", 0).forGetter(Crew::style)).apply(i, Crew::new));
	}

	/** On a fighter: its rank in the band. Saved. */
	public static final AttachmentType<Crew> CREW = AttachmentRegistry.create(JaymosKingdoms.id("crew"), b -> b.persistent(Crew.CODEC));
	/** Camp (faction id) → the id the bounty on its boss was posted under. Kept on the Overworld. */
	static final AttachmentType<Map<String, String>> BOSSES = AttachmentRegistry.create(JaymosKingdoms.id("crew_bosses"),
		b -> b.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)));

	/** A cloud of the Poisoner's: where, how far across, until when (game time). */
	private record Cloud(Identifier dimension, Vec3 at, long until) {
	}

	private static final List<Cloud> CLOUDS = new ArrayList<>();
	/** A boss → when his move may be used again (game time). */
	private static final Map<UUID, Long> NEXT = new HashMap<>();

	private Crews() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Crews::tick);
		ServerLivingEntityEvents.AFTER_DAMAGE.register(Crews::struck);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CLOUDS.clear();
			NEXT.clear();
		});
	}

	// ---- looking at the camps ------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 7) {
			return;
		}
		ServerLevel overworld = server.overworld();
		long now = overworld.getGameTime();
		NEXT.values().removeIf(t -> t < now - 1200L);
		clouds(server, now);
		Set<Integer> seen = new HashSet<>();
		for (Faction camp : Factions.all(server, FactionType.BARBARIAN)) {
			if (camp.fallen() || !Camps.BARBARIAN.equals(Camps.kind(server, camp.id()))) {
				continue;
			}
			for (ServerPlayer player : overworld.players()) {
				if (!player.isSpectator() && Raids.horizontal(camp.seat(), player.blockPosition()) <= SWEEP_RANGE * SWEEP_RANGE && seen.add(camp.id())) {
					band(overworld, camp, now);
					break;
				}
			}
		}
	}

	/** One camp's band: ranks to those without, gear kept on, the boss's wits and bounty. */
	private static void band(ServerLevel level, Faction camp, long now) {
		BlockPos seat = camp.seat();
		AABB box = new AABB(seat).inflate(BAND_RANGE, BAND_HEIGHT, BAND_RANGE);
		List<Worker> band = Workers.near(level, box, w -> w.isAlive() && w.factionId() == camp.id() && (w.role() == Folk.BARBARIAN || w.role() == Folk.CHIEF));
		band.sort(Comparator.comparing(Worker::getUUID));
		int raiders = 0, strong = 0, heavy = 0;
		for (Worker w : band) {
			if (w.role() == Folk.BARBARIAN) {
				raiders++;
				Crew crew = w.mob().getAttached(CREW);
				strong += crew != null && crew.rank() == STRONG ? 1 : 0;
				heavy += crew != null && crew.rank() == HEAVY ? 1 : 0;
			}
		}
		int strongWanted = raiders >= STRONG_TWO_AT ? 2 : raiders >= STRONG_AT ? 1 : 0, heavyWanted = raiders >= HEAVY_AT ? 1 : 0;
		for (Worker w : band) {
			PathfinderMob mob = w.mob();
			if (Downed.isDowned(mob)) {
				continue;
			}
			if (w.role() == Folk.CHIEF) {
				boss(level, camp, mob, now);
				continue;
			}
			Crew crew = mob.getAttached(CREW);
			if (crew == null) {
				int rank = heavy < heavyWanted ? HEAVY : strong < strongWanted ? STRONG : PLAIN;
				heavy += rank == HEAVY ? 1 : 0;
				strong += rank == STRONG ? 1 : 0;
				crew = new Crew(rank, 0);
				mob.setAttached(CREW, crew);
				build(mob, crew);
			}
			outfit(mob, crew);
		}
	}

	// ---- ranks ---------------------------------------------------------------------------------------------------------

	/** The body a rank brings, added once (each change is a named modifier, so it is never added twice). */
	private static void build(PathfinderMob mob, Crew crew) {
		boolean tougher = false;
		switch (crew.rank()) {
			case STRONG -> {
				tougher |= mod(mob, Attributes.MAX_HEALTH, "strong_health", STRONG_HEALTH, AttributeModifier.Operation.ADD_VALUE);
				mod(mob, Attributes.ATTACK_DAMAGE, "strong_damage", STRONG_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
			}
			case HEAVY -> {
				tougher |= mod(mob, Attributes.MAX_HEALTH, "heavy_health", HEAVY_HEALTH, AttributeModifier.Operation.ADD_VALUE);
				mod(mob, Attributes.ATTACK_DAMAGE, "heavy_damage", HEAVY_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
				mod(mob, Attributes.ARMOR, "heavy_armor", HEAVY_ARMOR, AttributeModifier.Operation.ADD_VALUE);
				mod(mob, Attributes.KNOCKBACK_RESISTANCE, "heavy_knockback", HEAVY_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE);
				mod(mob, Attributes.MOVEMENT_SPEED, "heavy_pace", HEAVY_PACE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
				mod(mob, Attributes.SCALE, "heavy_scale", HEAVY_SCALE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			}
			case BOSS -> {
				switch (Style.values()[Math.floorMod(crew.style(), Style.values().length)]) {
					case KNIFE -> mod(mob, Attributes.MOVEMENT_SPEED, "knife_pace", KNIFE_PACE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
					case HAMMER -> {
						mod(mob, Attributes.SCALE, "hammer_scale", HAMMER_SCALE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
						mod(mob, Attributes.KNOCKBACK_RESISTANCE, "hammer_knockback", HAMMER_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE);
						mod(mob, Attributes.MOVEMENT_SPEED, "hammer_pace", HAMMER_PACE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
					}
					default -> {
					}
				}
			}
			default -> {
			}
		}
		if (tougher) {
			mob.setHealth(mob.getMaxHealth());
		}
	}

	/** True if {@code id} was new on {@code mob}. */
	private static boolean mod(LivingEntity mob, Holder<Attribute> attribute, String name, double amount, AttributeModifier.Operation op) {
		AttributeInstance a = mob.getAttribute(attribute);
		Identifier id = JaymosKingdoms.id("crew_" + name);
		if (a == null || a.getModifier(id) != null) {
			return false;
		}
		a.addPermanentModifier(new AttributeModifier(id, amount, op));
		return true;
	}

	/** Its gear for its rank, put back on if the camp's own dressing changed it. */
	private static void outfit(PathfinderMob mob, Crew crew) {
		switch (crew.rank()) {
			case STRONG -> {
				Item weapon = (mob.getUUID().getLeastSignificantBits() & 1L) == 0L ? Items.IRON_SWORD : Items.IRON_AXE;
				wear(mob, EquipmentSlot.MAINHAND, weapon);
				wear(mob, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE);
				wear(mob, EquipmentSlot.LEGS, Items.IRON_LEGGINGS);
			}
			case HEAVY -> {
				wear(mob, EquipmentSlot.MAINHAND, Items.IRON_AXE);
				wear(mob, EquipmentSlot.HEAD, Items.IRON_HELMET);
				wear(mob, EquipmentSlot.CHEST, Items.IRON_CHESTPLATE);
				wear(mob, EquipmentSlot.LEGS, Items.IRON_LEGGINGS);
				wear(mob, EquipmentSlot.FEET, Items.IRON_BOOTS);
			}
			case BOSS -> wear(mob, EquipmentSlot.MAINHAND, Style.values()[Math.floorMod(crew.style(), Style.values().length)].weapon);
			default -> {
			}
		}
	}

	private static void wear(PathfinderMob mob, EquipmentSlot slot, Item item) {
		if (!mob.getItemBySlot(slot).is(item)) {
			mob.setItemSlot(slot, new ItemStack(item));
		}
	}

	// ---- the boss ------------------------------------------------------------------------------------------------------

	/** A camp's chief: his style, his title, his bounty, and his move. */
	private static void boss(ServerLevel level, Faction camp, PathfinderMob mob, long now) {
		Style style = Style.of(camp.id());
		Crew crew = mob.getAttached(CREW);
		if (crew == null) {
			crew = new Crew(BOSS, style.ordinal());
			mob.setAttached(CREW, crew);
			build(mob, crew);
			String title = title(camp, style);
			if (mob instanceof Folk folk && folk.getCustomName() != null && !folk.getCustomName().getString().endsWith(title)) {
				folk.setCustomName(Component.literal(folk.getCustomName().getString() + " " + title));
			}
		}
		outfit(mob, crew);
		post(level, camp, mob, style);
		LivingEntity foe = foe(level, camp, mob);
		if (foe == null) {
			return;
		}
		long ready = NEXT.getOrDefault(mob.getUUID(), 0L);
		if (ready > now) {
			return;
		}
		double d = mob.distanceTo(foe);
		boolean used = switch (style) {
			case KNIFE -> lunge(mob, foe, d) || FanOfKnives.cast(level, mob, foe);
			case HAMMER -> slam(level, mob, d);
			case ARCHER -> volley(level, mob, foe, d);
			case POISONER -> cloud(level, mob, foe, d, now);
		};
		if (used) {
			NEXT.put(mob.getUUID(), now + switch (style) {
				case KNIFE -> KNIFE_PAUSE;
				case HAMMER -> SLAM_PAUSE;
				case ARCHER -> VOLLEY_PAUSE;
				case POISONER -> CLOUD_PAUSE;
			});
		}
	}

	/** The title a camp's boss goes by: one of the style's, the same each time. */
	static String title(Faction camp, Style style) {
		return style.titles[Math.floorMod(camp.id() * 17 + 3, style.titles.length)];
	}

	/** The style of the boss ({@code mob}) of a band, or null if it is not one. */
	static @Nullable Style styleOf(LivingEntity mob) {
		Crew crew = mob.getAttached(CREW);
		return crew == null || crew.rank() != BOSS ? null : Style.values()[Math.floorMod(crew.style(), Style.values().length)];
	}

	/** Whom the boss fights: his own target if it is a player, else the nearest player the camp would attack that he can see. */
	private static @Nullable LivingEntity foe(ServerLevel level, Faction camp, PathfinderMob mob) {
		if (mob.getTarget() instanceof ServerPlayer p && p.isAlive() && !p.isCreative() && !p.isSpectator()) {
			return p;
		}
		ServerPlayer best = null;
		double bestD = SIGHT * SIGHT;
		for (ServerPlayer p : level.players()) {
			double d = p.distanceToSqr(mob);
			if (d < bestD && p.isAlive() && !p.isCreative() && !p.isSpectator() && Barbarians.prey(camp.id(), p) && mob.hasLineOfSight(p)) {
				bestD = d;
				best = p;
			}
		}
		return best;
	}

	/** The bounty on the boss, posted with the nearest realm's court under his title; moved when his body is replaced. */
	private static void post(ServerLevel level, Faction camp, PathfinderMob mob, Style style) {
		MinecraftServer server = level.getServer();
		Map<String, String> posted = server.overworld().getAttachedOrElse(BOSSES, Map.of());
		String key = String.valueOf(camp.id()), id = mob.getStringUUID();
		String old = posted.get(key);
		if (old != null && !old.equals(id)) {
			Bounties.withdraw(server, old);
		}
		Faction court = null;
		double best = COURT_RANGE * COURT_RANGE;
		for (Faction f : Factions.all(server)) {
			if (f.fallen() || f.type() == FactionType.BARBARIAN) {
				continue;
			}
			double d = Raids.horizontal(camp.seat(), f.seat());
			if (d < best) {
				best = d;
				court = Bounties.court(server, f);
			}
		}
		if (court != null) {
			Bounties.postCrew(server, court.id(), id, "The " + style.name().charAt(0) + style.name().substring(1).toLowerCase(java.util.Locale.ROOT) + " of " + camp.name(),
				mob.blockPosition(), BOUNTY);
		}
		if (!id.equals(old)) {
			Map<String, String> next = new HashMap<>(posted);
			next.put(key, id);
			server.overworld().setAttached(BOSSES, Map.copyOf(next));
		}
	}

	// ---- the four moves (each true when used) -------------------------------------------------------------------------

	/** The Knife darts in. */
	private static boolean lunge(PathfinderMob mob, LivingEntity foe, double d) {
		if (d < KNIFE_LUNGE_MIN || d > KNIFE_LUNGE_MAX) {
			return false;
		}
		mob.addEffect(new MobEffectInstance(MobEffects.SPEED, KNIFE_LUNGE_TICKS, KNIFE_LUNGE_LEVEL, false, false, false));
		mob.getNavigation().moveTo(foe, 1.4);
		if (mob.level() instanceof ServerLevel level) {
			Fx.beam(level, mob.position().add(0.0, 1.0, 0.0), foe.position().add(0.0, 1.0, 0.0), 0.04F, STEEL, 6);
		}
		return true;
	}

	/** The Hammer slams the ground. */
	private static boolean slam(ServerLevel level, PathfinderMob mob, double d) {
		if (d > SLAM_REACH) {
			return false;
		}
		mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.MACE_SMASH_GROUND, SoundSource.HOSTILE, 1.4F, 0.8F);
		Fx.ring(level, mob.position().add(0.0, 0.1, 0.0), (float)SLAM_REACH, DUST);
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox().inflate(SLAM_REACH), x -> x.isAlive() && !x.isCreative() && !x.isSpectator())) {
			Vec3 away = p.position().subtract(mob.position()).multiply(1.0, 0.0, 1.0);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : away.normalize();
			p.hurtServer(level, level.damageSources().mobAttack(mob), SLAM_DAMAGE);
			p.setDeltaMovement(p.getDeltaMovement().add(away.x * SLAM_THROW, SLAM_LIFT, away.z * SLAM_THROW));
			p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(p));
		}
		return true;
	}

	/** The Archer looses a volley. */
	private static boolean volley(ServerLevel level, PathfinderMob mob, LivingEntity foe, double d) {
		if (d < VOLLEY_MIN || d > VOLLEY_MAX || !mob.hasLineOfSight(foe)) {
			return false;
		}
		ItemStack bow = mob.getMainHandItem();
		for (int i = 0; i < VOLLEY_ARROWS; i++) {
			ItemStack stack = new ItemStack(Items.ARROW);
			AbstractArrow arrow = ProjectileUtil.getMobArrow(mob, stack, 1.0F, bow);
			double xd = foe.getX() - mob.getX() + (i - 1) * VOLLEY_SPREAD * 0.25;
			double yd = foe.getY(0.3333333333333333) - arrow.getY();
			double zd = foe.getZ() - mob.getZ() + (i - 1) * VOLLEY_SPREAD * 0.25;
			double flat = Math.sqrt(xd * xd + zd * zd);
			Projectile.spawnProjectileUsingShoot(arrow, level, stack, xd, yd + flat * 0.2, zd, VOLLEY_SPEED, VOLLEY_SPREAD);
		}
		mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		mob.playSound(SoundEvents.SKELETON_SHOOT, 1.2F, 0.8F);
		return true;
	}

	/** The Poisoner throws a cloud at his foe's feet. */
	private static boolean cloud(ServerLevel level, PathfinderMob mob, LivingEntity foe, double d, long now) {
		if (d < CLOUD_MIN || d > CLOUD_MAX || !mob.hasLineOfSight(foe)) {
			return false;
		}
		CLOUDS.add(new Cloud(level.dimension().identifier(), foe.position(), now + CLOUD_LIFE));
		mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SPLASH_POTION_BREAK, SoundSource.HOSTILE, 1.0F, 0.9F);
		Fx.blast(level, foe.position().add(0.0, 0.4, 0.0), 1.5F, GREEN);
		return true;
	}

	/** The clouds act: players in one are poisoned, and it smokes. */
	private static void clouds(MinecraftServer server, long now) {
		if (CLOUDS.isEmpty()) {
			return;
		}
		CLOUDS.removeIf(c -> c.until() <= now);
		for (Cloud c : CLOUDS) {
			for (ServerLevel level : server.getAllLevels()) {
				if (!level.dimension().identifier().equals(c.dimension())) {
					continue;
				}
				level.sendParticles(Fx.miasma(GREEN, 1.4F), c.at().x, c.at().y + 0.4, c.at().z, 10, CLOUD_RADIUS * 0.5, 0.3, CLOUD_RADIUS * 0.5, 0.0);
				for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new AABB(c.at(), c.at()).inflate(CLOUD_RADIUS, 1.5, CLOUD_RADIUS),
					x -> x.isAlive() && !x.isCreative() && !x.isSpectator() && x.position().distanceTo(c.at()) <= CLOUD_RADIUS)) {
					p.addEffect(new MobEffectInstance(MobEffects.POISON, CLOUD_POISON, 0));
				}
			}
		}
	}

	/** A blow from a boss: the Knife hamstrings, the Poisoner poisons. */
	private static void struck(LivingEntity victim, DamageSource source, float baseDamage, float damage, boolean blocked) {
		if (!(victim instanceof ServerPlayer player) || blocked || !(source.getEntity() instanceof PathfinderMob by)) {
			return;
		}
		Style style = styleOf(by);
		if (style == Style.KNIFE) {
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, HAMSTRING_TICKS, 0));
			by.addEffect(new MobEffectInstance(MobEffects.SPEED, KNIFE_HIT_TICKS, KNIFE_LUNGE_LEVEL, false, false, false));
		} else if (style == Style.POISONER) {
			player.addEffect(new MobEffectInstance(MobEffects.POISON, BLOW_POISON, 0));
		}
	}
}
