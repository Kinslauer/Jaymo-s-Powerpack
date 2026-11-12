package com.jaymoskingdoms;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Damage kinds and who shrugs them off (fifth edition's damage types, resistance, vulnerability and immunity, Andy's pick
 * C8). Every hit has a {@link Kind}, worked out from the damage and, for a blow of arms, from the weapon: a sword or an
 * axe cuts (slashing), a spear or a dagger or an arrow pierces, a mace or a club or a fist bludgeons; fire, cold,
 * lightning, thunder (blasts), poison, necrotic (wither), radiant (holy), force (spells) and the rest come from the damage
 * itself. Then each creature takes more or less of each kind by the <i>families</i> it belongs to: a table
 * ({@link #FAMILIES}), a family being a test for who is in it and a multiplier per kind (0 immune, under 1 resists, over 1
 * fears). A creature in several families takes the product. Add a family with {@link #family}, a way of telling a kind with
 * {@link #addKinder}.
 * <ul>
 * <li><b>Skeletons</b> barely feel arrows and other piercing, and break under maces and clubs; poison is nothing to
 * them.</li>
 * <li><b>The undead</b> ignore poison (a zombie's poisoning is cleared) and fear holy light.</li>
 * <li><b>Ghosts</b> ({@code #jaymoskingdoms:ghostly}: vexes, wraiths) take no plain blow: only an enchanted weapon, a silver
 * or holy one, a spell, fire or holy light touches them.</li>
 * <li><b>Slimes</b> split when cut: a slashing blow that doesn't kill one with some body to it cuts a smaller slime off.
 * Piercing barely hurts them; fire does.</li>
 * <li><b>Metal golems</b> shrug off plain iron (an iron weapon with no enchantment on it), pierce badly, and take
 * lightning hard.</li>
 * <li><b>Fire creatures</b> ({@code #jaymoskingdoms:fiery}) take no fire: it feeds them. They heal standing in fire or lava
 * (and a fire's blow heals them), and cold bites them hard. Snow golems melt in fire.</li>
 * </ul>
 * A blow that is shrugged off clinks, a ghost's passes through with a sigh, and a blow that finds a weakness rings; no
 * numbers are shown.
 */
public final class DamageKinds {
	/** The thirteen kinds of harm. */
	public enum Kind {
		SLASHING, PIERCING, BLUDGEONING, FIRE, COLD, LIGHTNING, THUNDER, ACID, POISON, NECROTIC, RADIANT, FORCE, PSYCHIC;

		public String key() {
			return name().toLowerCase(Locale.ROOT);
		}

		/** The three kinds a plain weapon does. */
		public boolean physical() {
			return this == SLASHING || this == PIERCING || this == BLUDGEONING;
		}
	}

	/**
	 * A family of creatures: who is in it, how much of each kind they take (a kind not listed is taken whole), and the few
	 * things that make it a family: {@code plainPhysicalPasses} (physical blows pass through unless enchanted or holy: ghosts),
	 * {@code shrugs} (a weapon the family takes {@link #SHRUGGED} of: plain iron against golems), {@code feedsOnFire} (fire
	 * heals it instead of hurting it), {@code poisonProof} (the poison effect is cleared from it), {@code splitsWhenCut}.
	 */
	public record Family(String key, Predicate<LivingEntity> members, Map<Kind, Float> takes, boolean plainPhysicalPasses,
		@Nullable Predicate<ItemStack> shrugs, boolean feedsOnFire, boolean poisonProof, boolean splitsWhenCut) {
		public float takes(Kind kind) {
			return this.takes.getOrDefault(kind, 1.0F);
		}
	}

	// ---- tuning ----
	/** The share of a blow a family takes of a weapon it shrugs off. */
	static final float SHRUGGED = 0.3F;
	/** A multiplier at or under this is "resists" (it clinks), at or over {@link #FEARS} "fears" (it rings). */
	static final float RESISTS = 0.7F, FEARS = 1.3F;
	/** A slime splits only above this size, off a slime this size at least; the cut-off bit is this share of its size. */
	static final int SPLIT_MIN_SIZE = 2;
	static final float SPLIT_SHARE = 0.5F;
	/** A slime can be cut this often in all, and no more than this many slimes crowd one place. */
	static final int SPLIT_MOST = 3, SLIMES_NEAR = 16;
	static final double SLIME_CROWD = 12.0;
	/** The scoreboard tag counting the cuts a slime has had: this, then the count. */
	static final String CUT_TAG = "jaymoskingdoms.cut.";
	/** How often (ticks) the families are looked at (fire creatures heal, poison is cleared), and how far from a player (blocks). */
	static final int EVERY = 40;
	static final double NEAR = 40.0;
	/** What a fire creature heals standing in fire each time, and a fire's blow heals it this share of the blow. */
	static final float FIRE_HEAL = 2.0F, FIRE_FEED = 0.5F;

	public static final TagKey<EntityType<?>> GHOSTLY = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("ghostly")),
		FIERY = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("fiery")),
		METAL_GOLEMS = TagKey.create(Registries.ENTITY_TYPE, JaymosKingdoms.id("metal_golems"));
	/** Weapons that are holy to the touch of the dead (silver and the like), beside Smite and every enchanted blade. */
	public static final TagKey<net.minecraft.world.item.Item> HOLY = TagKey.create(Registries.ITEM, JaymosKingdoms.id("holy_weapons"));

	/** Words in a weapon's name that tell how it strikes, for weapons other mods add. */
	private static final List<String> PIERCERS = List.of("spear", "lance", "pike", "dagger", "knife", "rapier", "dirk", "stiletto", "shiv", "javelin",
		"trident", "fang", "needle", "halberd", "crossbow", "bow");
	private static final List<String> CUTTERS = List.of("sword", "axe", "saber", "sabre", "scimitar", "cutlass", "katana", "claymore", "glaive",
		"blade", "scythe", "sickle", "machete", "cleaver", "falchion");

	private static final List<Function<DamageSource, @Nullable Kind>> KINDERS = new ArrayList<>();
	private static final List<Family> FAMILIES = new ArrayList<>();

	private DamageKinds() {
	}

	static void register() {
		family("skeletal", e -> e.is(EntityTypeTags.SKELETONS), kinds(Kind.PIERCING, 0.25F, Kind.SLASHING, 0.8F, Kind.BLUDGEONING, 1.6F,
			Kind.POISON, 0.0F, Kind.NECROTIC, 0.0F, Kind.RADIANT, 1.3F), false, null, false, true, false);
		family("undead", e -> e.is(EntityTypeTags.UNDEAD), kinds(Kind.POISON, 0.0F, Kind.NECROTIC, 0.25F, Kind.RADIANT, 1.5F),
			false, null, false, true, false);
		family("ghostly", e -> e.is(GHOSTLY), kinds(Kind.POISON, 0.0F, Kind.NECROTIC, 0.0F, Kind.COLD, 0.5F, Kind.RADIANT, 1.5F,
			Kind.FORCE, 1.25F), true, null, false, true, false);
		family("slime", e -> e instanceof Slime, kinds(Kind.PIERCING, 0.5F, Kind.BLUDGEONING, 0.75F, Kind.FIRE, 1.5F, Kind.ACID, 0.0F),
			false, null, false, false, true);
		family("metal_golem", e -> e.is(METAL_GOLEMS), kinds(Kind.PIERCING, 0.6F, Kind.SLASHING, 0.75F, Kind.POISON, 0.0F,
			Kind.LIGHTNING, 1.5F, Kind.NECROTIC, 0.5F, Kind.PSYCHIC, 0.0F), false, DamageKinds::plainIron, false, true, false);
		family("fiery", e -> e.is(FIERY), kinds(Kind.FIRE, 0.0F, Kind.COLD, 1.5F), false, null, true, false, false);
		family("snowy", e -> e.getType() == EntityTypes.SNOW_GOLEM, kinds(Kind.FIRE, 2.0F, Kind.COLD, 0.0F), false, null, false, false, false);
		ServerTickEvents.END_SERVER_TICK.register(DamageKinds::tick);
	}

	private static Map<Kind, Float> kinds(Object... pairs) {
		Map<Kind, Float> map = new EnumMap<>(Kind.class);
		for (int i = 0; i + 1 < pairs.length; i += 2) {
			map.put((Kind) pairs[i], (Float) pairs[i + 1]);
		}
		return map;
	}

	/** Adds a family of creatures to the table. */
	public static void family(String key, Predicate<LivingEntity> members, Map<Kind, Float> takes, boolean plainPhysicalPasses,
		@Nullable Predicate<ItemStack> shrugs, boolean feedsOnFire, boolean poisonProof, boolean splitsWhenCut) {
		FAMILIES.add(new Family(key, members, takes, plainPhysicalPasses, shrugs, feedsOnFire, poisonProof, splitsWhenCut));
	}

	/** Adds a way of telling the kind of a hit (tried before the built-in ones; return null to leave it): acid, psychic, a mod's own. */
	public static void addKinder(Function<DamageSource, @Nullable Kind> kinder) {
		KINDERS.add(kinder);
	}

	// ---- what kind a hit is ------------------------------------------------------------------------------------------------

	/** The kind of {@code source}'s harm on {@code victim} (a poison's sting looks like any magic, so the victim is asked). */
	public static Kind of(LivingEntity victim, DamageSource source) {
		for (Function<DamageSource, @Nullable Kind> k : KINDERS) {
			Kind kind = k.apply(source);
			if (kind != null) {
				return kind;
			}
		}
		if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypes.DRAGON_BREATH)) {
			return Kind.FIRE;
		}
		if (source.is(DamageTypeTags.IS_FREEZING)) {
			return Kind.COLD;
		}
		if (source.is(DamageTypeTags.IS_LIGHTNING)) {
			return Kind.LIGHTNING;
		}
		if (source.is(DamageTypes.SONIC_BOOM) || source.is(DamageTypeTags.IS_EXPLOSION)) {
			return Kind.THUNDER;
		}
		if (source.is(DamageTypes.WITHER) || source.is(DamageTypes.WITHER_SKULL)) {
			return Kind.NECROTIC;
		}
		if (source.is(DamageTypes.MAGIC) && source.getEntity() == null && victim.hasEffect(MobEffects.POISON)) {
			return Kind.POISON;
		}
		if (source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
			return Kind.FORCE;
		}
		if (source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH) || source.is(DamageTypes.STALAGMITE) || source.is(DamageTypes.THORNS)
			|| source.is(DamageTypes.STING)) {
			return Kind.PIERCING;
		}
		if (source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.FALLING_BLOCK) || source.is(DamageTypes.FALLING_ANVIL)
			|| source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.MACE_SMASH)) {
			return Kind.BLUDGEONING;
		}
		Entity direct = source.getDirectEntity();
		if (direct instanceof AbstractArrow || source.is(DamageTypes.ARROW) || source.is(DamageTypes.TRIDENT) || source.is(DamageTypes.SPEAR)) {
			return Kind.PIERCING;
		}
		if (direct instanceof LivingEntity attacker) {
			ItemStack weapon = source.getWeaponItem();
			if (weapon != null && !weapon.isEmpty()) {
				return ofWeapon(weapon);
			}
			return attacker.is(EntityTypeTags.ARTHROPOD) ? Kind.PIERCING : Kind.BLUDGEONING;
		}
		if (direct instanceof Projectile) {
			return Kind.BLUDGEONING;
		}
		return Kind.BLUDGEONING;
	}

	/** How a weapon strikes: by its tags, else its name. */
	public static Kind ofWeapon(ItemStack weapon) {
		if (weapon.is(Items.MACE) || PowersCompat.blunt(weapon)) {
			return Kind.BLUDGEONING;
		}
		if (weapon.is(ItemTags.SPEARS) || weapon.is(Items.TRIDENT) || weapon.is(ItemTags.PICKAXES) || weapon.is(Items.CROSSBOW) || weapon.is(Items.BOW)) {
			return Kind.PIERCING;
		}
		if (weapon.is(ItemTags.SWORDS) || weapon.is(ItemTags.AXES) || weapon.is(ItemTags.HOES)) {
			return Kind.SLASHING;
		}
		if (weapon.is(ItemTags.SHOVELS)) {
			return Kind.BLUDGEONING;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(weapon.getItem());
		String name = id == null ? "" : id.getPath();
		for (String word : PIERCERS) {
			if (name.contains(word)) {
				return Kind.PIERCING;
			}
		}
		for (String word : CUTTERS) {
			if (name.contains(word)) {
				return Kind.SLASHING;
			}
		}
		return Kind.BLUDGEONING;
	}

	/** An iron weapon with no enchantment on it. */
	static boolean plainIron(ItemStack weapon) {
		if (weapon.isEmpty() || weapon.isEnchanted()) {
			return false;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(weapon.getItem());
		return id != null && id.getPath().startsWith("iron_");
	}

	/** Whether a blow is enchanted or holy: an enchanted weapon, a silver or holy one, or the hand of a Divine-born or a Dawnborn in the sun. */
	static boolean touchesGhosts(DamageSource source) {
		ItemStack weapon = source.getWeaponItem();
		if (weapon != null && !weapon.isEmpty() && (weapon.isEnchanted() || weapon.is(HOLY) || Vampires.silver(weapon))) {
			return true;
		}
		Entity by = source.getEntity();
		if (by instanceof Player p) {
			Heritage.Kind blood = Heritage.of(p);
			return blood == Heritage.Kind.DIVINE || blood == Heritage.Kind.DAWNBORN && p.level().isBrightOutside() && p.level().canSeeSky(p.blockPosition());
		}
		return false;
	}

	// ---- the hit -----------------------------------------------------------------------------------------------------------

	/** {@code victim} taking {@code amount}: by its families' take on the kind. */
	public static float dealt(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0.0F || !(victim.level() instanceof ServerLevel level) || FAMILIES.isEmpty()) {
			return amount;
		}
		List<Family> mine = null;
		for (Family f : FAMILIES) {
			if (f.members.test(victim)) {
				if (mine == null) {
					mine = new ArrayList<>(2);
				}
				mine.add(f);
			}
		}
		if (mine == null) {
			return amount;
		}
		Kind kind = of(victim, source);
		float factor = 1.0F;
		boolean passes = false, sunk = false;
		for (Family f : mine) {
			factor *= f.takes(kind);
			if (f.plainPhysicalPasses && kind.physical() && !touchesGhosts(source)) {
				factor = 0.0F;
				passes = true;
			}
			if (f.shrugs != null && kind.physical()) {
				ItemStack weapon = source.getWeaponItem();
				if (weapon != null && f.shrugs.test(weapon)) {
					factor *= SHRUGGED;
					sunk = true;
				}
			}
			if (f.feedsOnFire && kind == Kind.FIRE) {
				victim.heal(amount * FIRE_FEED);
				level.sendParticles(ParticleTypes.FLAME, victim.getX(), victim.getY(0.5), victim.getZ(), 3, 0.25, 0.3, 0.25, 0.01);
			}
		}
		for (Family f : mine) {
			if (f.splitsWhenCut && kind == Kind.SLASHING && victim instanceof Slime slime && victim.getHealth() - amount * factor > 0.0F) {
				cut(level, slime);
			}
		}
		if (factor == 1.0F) {
			return amount;
		}
		if (source.getEntity() != null || source.getDirectEntity() != null) {
			sound(level, victim, factor, passes, sunk);
		}
		return amount * factor;
	}

	private static void sound(ServerLevel level, LivingEntity victim, float factor, boolean passes, boolean sunk) {
		if (passes) {
			level.playSound(null, victim.getX(), victim.getY(0.5), victim.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 0.6F, 1.3F);
			level.sendParticles(ParticleTypes.SOUL, victim.getX(), victim.getY(0.5), victim.getZ(), 3, 0.2, 0.3, 0.2, 0.01);
		} else if (factor <= RESISTS || sunk) {
			level.playSound(null, victim.getX(), victim.getY(0.5), victim.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.HOSTILE, 0.5F, 1.2F);
		} else if (factor >= FEARS) {
			level.playSound(null, victim.getX(), victim.getY(0.5), victim.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 0.6F, 0.9F);
			level.sendParticles(ParticleTypes.CRIT, victim.getX(), victim.getY(0.6), victim.getZ(), 6, 0.25, 0.3, 0.25, 0.2);
		}
	}

	/** A slime cut and left standing cuts a smaller one off (a few times, and not into a crowd). */
	private static void cut(ServerLevel level, Slime slime) {
		int size = slime.getSize();
		if (size < SPLIT_MIN_SIZE) {
			return;
		}
		int cuts = 0;
		for (String tag : slime.entityTags()) {
			if (tag.startsWith(CUT_TAG)) {
				cuts = Math.max(cuts, Integer.parseInt(tag.substring(CUT_TAG.length())));
			}
		}
		if (cuts >= SPLIT_MOST || level.getEntitiesOfClass(Slime.class, slime.getBoundingBox().inflate(SLIME_CROWD)).size() > SLIMES_NEAR) {
			return;
		}
		Entity made = slime.getType().create(level, EntitySpawnReason.TRIGGERED);
		if (!(made instanceof Slime bit)) {
			return;
		}
		bit.setSize(Math.max(1, Math.round(size * SPLIT_SHARE)), true);
		bit.snapTo(slime.getX() + (slime.getRandom().nextDouble() - 0.5), slime.getY(), slime.getZ() + (slime.getRandom().nextDouble() - 0.5), slime.getRandom().nextFloat() * 360.0F, 0.0F);
		bit.addTag(CUT_TAG + SPLIT_MOST);
		level.addFreshEntity(bit);
		slime.removeTag(CUT_TAG + cuts);
		slime.addTag(CUT_TAG + (cuts + 1));
		level.playSound(null, slime.getX(), slime.getY(), slime.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.HOSTILE, 0.8F, 1.3F);
		level.sendParticles(ParticleTypes.ITEM_SLIME, slime.getX(), slime.getY(0.5), slime.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
	}

	// ---- the families, looked at ---------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.players().isEmpty()) {
				continue;
			}
			Set<Mob> seen = new HashSet<>();
			for (var player : level.players()) {
				if (!player.isSpectator()) {
					seen.addAll(level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(NEAR), Mob::isAlive));
				}
			}
			for (Mob mob : seen) {
				for (Family f : FAMILIES) {
					if ((f.feedsOnFire || f.poisonProof) && f.members.test(mob)) {
						if (f.poisonProof && mob.hasEffect(MobEffects.POISON)) {
							mob.removeEffect(MobEffects.POISON);
						}
						if (f.feedsOnFire && mob.getHealth() < mob.getMaxHealth() && inFire(level, mob)) {
							mob.heal(FIRE_HEAL);
							level.sendParticles(ParticleTypes.FLAME, mob.getX(), mob.getY(0.5), mob.getZ(), 4, 0.3, 0.4, 0.3, 0.02);
						}
					}
				}
			}
		}
	}

	/** Standing in fire, lava or a lit campfire, or burning. */
	private static boolean inFire(ServerLevel level, LivingEntity mob) {
		var pos = mob.blockPosition();
		var state = level.getBlockState(pos);
		return mob.isOnFire() || mob.isInLava() || state.is(BlockTags.FIRE) || level.getBlockState(pos.below()).is(BlockTags.FIRE)
			|| net.minecraft.world.level.block.CampfireBlock.isLitCampfire(state);
	}
}
