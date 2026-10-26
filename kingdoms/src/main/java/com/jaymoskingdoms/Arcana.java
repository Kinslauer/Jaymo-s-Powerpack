package com.jaymoskingdoms;

import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.Registry;
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
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.5.37) The casters' things ({@link Sorcery}): their staffs (the necromancer's bone staff, the shaman's totem staff,
 * the court wizard's staff; carried, never dropped) and the spell scrolls a player takes from a slain caster. A scroll is
 * read once (right-click) and crumbles:
 * <ul>
 * <li><b>Raising</b> (a necromancer's): a zombie and a skeleton climb out of the ground to fight for you a minute (they
 * go for monsters and whoever fights you, never a player);</li>
 * <li><b>Draining</b> (a necromancer's): life drawn from what you're looking at, into you;</li>
 * <li><b>Fire</b> (a shaman's): three fire bolts in a fan;</li>
 * <li><b>Warding</b> (a court wizard's): absorption for you and the players beside you;</li>
 * <li><b>the Arcane Bolt</b> (a court wizard's): a lance of light at what you're looking at, twice as hard on the undead.</li>
 * </ul>
 * (0.7.103) A Scroll of Draining can also be written: paper, two bat wings ({@link #BAT_WING}, after Better With Mods'
 * bat wing: bats drop them) and a fermented spider eye.
 * <p>
 * (0.7.212) Restoration's three ({@link Restoration}): <b>Mending</b> (who you look at, or you, healed and a cut closed;
 * court wizards and clerics sell it), <b>Sanctuary</b> (a circle of light that heals and keeps the undead out; a court
 * wizard's) and <b>Restoration</b> (whole again, bones set, up if downed; rare). And the healer's staff, a wizard's staff
 * made over with a golden apple: it casts the same.
 * <p>
 * (0.7.229) Death's three ({@link Necromancy}): <b>Blight</b>, <b>Whispers</b>, <b>Death's Door</b> (a necromancer's,
 * now and then); blood's three ({@link Hemomancy}): <b>Hemorrhage</b>, <b>Blood Grip</b>, <b>Frenzy</b> (a blood
 * witch's). Forbidden: reading one is a crime where a realm's people see ({@link ForbiddenArts}).
 */
public final class Arcana {
	/** How far a scroll reaches (draining, arcane bolt). */
	static final double SCROLL_REACH = 24.0;
	/** Draining: damage and what you take back. Arcane bolt: damage (doubled on the undead). */
	static final float SCROLL_DRAIN = 8.0F, SCROLL_BOLT = 9.0F;
	/** Raising: how long your thralls stand. */
	static final int SCROLL_THRALL_LIFE = 20 * 60;
	/** Warding: how long, how strong, how far it spreads to other players. */
	static final int SCROLL_WARD_TICKS = 20 * 30, SCROLL_WARD_LEVEL = 2;
	static final double SCROLL_WARD_RANGE = 6.0;
	/** A pause after reading one (ticks), so a stack isn't read all at once. */
	static final int SCROLL_COOLDOWN = 20;

	public static Item BONE_STAFF, TOTEM_STAFF, WIZARD_STAFF, HEALER_STAFF;
	public static Item SCROLL_RAISING, SCROLL_DRAINING, SCROLL_FIRE, SCROLL_WARDING, SCROLL_ARCANE;
	/** (0.7.212) Restoration's scrolls. */
	public static Item SCROLL_MENDING, SCROLL_SANCTUARY, SCROLL_RESTORATION;
	/** (0.7.229) Death's scrolls ({@link Necromancy}) and blood's ({@link Hemomancy}). */
	public static Item SCROLL_BLIGHT, SCROLL_WHISPERS, SCROLL_DEATHS_DOOR, SCROLL_HEMORRHAGE, SCROLL_BLOOD_GRIP, SCROLL_FRENZY;
	/** (0.7.103) A bat's wing (bats drop 0-1, more with Looting): the reagent of a written Scroll of Draining. */
	public static Item BAT_WING;

	private Arcana() {
	}

	static void register() {
		BONE_STAFF = item("bone_staff", p -> new Item(p.stacksTo(1)));
		TOTEM_STAFF = item("totem_staff", p -> new Item(p.stacksTo(1)));
		WIZARD_STAFF = item("wizard_staff", p -> new Item(p.stacksTo(1)));
		HEALER_STAFF = item("healer_staff", p -> new Item(p.stacksTo(1)));
		SCROLL_RAISING = item("scroll_of_raising", p -> new Scroll(p, "raising"));
		SCROLL_DRAINING = item("scroll_of_draining", p -> new Scroll(p, "draining"));
		SCROLL_FIRE = item("scroll_of_fire", p -> new Scroll(p, "fire"));
		SCROLL_WARDING = item("scroll_of_warding", p -> new Scroll(p, "warding"));
		SCROLL_ARCANE = item("scroll_of_arcane_bolt", p -> new Scroll(p, "arcane"));
		SCROLL_MENDING = item("scroll_of_mending", p -> new Scroll(p, "mending"));
		SCROLL_SANCTUARY = item("scroll_of_sanctuary", p -> new Scroll(p, "sanctuary"));
		SCROLL_RESTORATION = item("scroll_of_restoration", p -> new Scroll(p, "restoration"));
		SCROLL_BLIGHT = item("scroll_of_blight", p -> new Scroll(p, "blight"));
		SCROLL_WHISPERS = item("scroll_of_whispers", p -> new Scroll(p, "whispers"));
		SCROLL_DEATHS_DOOR = item("scroll_of_deaths_door", p -> new Scroll(p, "deaths_door"));
		SCROLL_HEMORRHAGE = item("scroll_of_hemorrhage", p -> new Scroll(p, "hemorrhage"));
		SCROLL_BLOOD_GRIP = item("scroll_of_blood_grip", p -> new Scroll(p, "blood_grip"));
		SCROLL_FRENZY = item("scroll_of_frenzy", p -> new Scroll(p, "frenzy"));
		ResourceKey<Item> wing = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id("bat_wing"));
		BAT_WING = Registry.register(BuiltInRegistries.ITEM, wing, new Item(new Item.Properties().setId(wing)));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS).register(output -> output.insertAfter(Items.PHANTOM_MEMBRANE, BAT_WING));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			Item after = Items.TOTEM_OF_UNDYING;
			for (Item item : List.of(SCROLL_RAISING, SCROLL_DRAINING, SCROLL_FIRE, SCROLL_WARDING, SCROLL_ARCANE, SCROLL_MENDING, SCROLL_SANCTUARY,
				SCROLL_RESTORATION, SCROLL_BLIGHT, SCROLL_WHISPERS, SCROLL_DEATHS_DOOR, SCROLL_HEMORRHAGE, SCROLL_BLOOD_GRIP, SCROLL_FRENZY,
				BONE_STAFF, TOTEM_STAFF, WIZARD_STAFF, HEALER_STAFF)) {
				output.insertAfter(after, item);
				after = item;
			}
		});
	}

	private static Item item(String name, Function<Item.Properties, Item> make) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosKingdoms.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, make.apply(new Item.Properties().setId(key).rarity(Rarity.UNCOMMON)));
	}

	/** A scroll from a slain caster of {@code role}. */
	static ItemStack scrollFor(int role, RandomSource random) {
		return new ItemStack(switch (role) {
			// (0.7.229) and now and then one of death's new three
			case Sorcery.NECROMANCER -> switch (random.nextInt(6)) {
				case 0, 1 -> SCROLL_RAISING;
				case 2, 3 -> SCROLL_DRAINING;
				case 4 -> random.nextBoolean() ? SCROLL_BLIGHT : SCROLL_WHISPERS;
				default -> random.nextInt(3) == 0 ? SCROLL_DEATHS_DOOR : SCROLL_BLIGHT;
			};
			case Sorcery.BLOOD_WITCH -> switch (random.nextInt(5)) {
				case 0, 1 -> SCROLL_HEMORRHAGE;
				case 2, 3 -> SCROLL_BLOOD_GRIP;
				default -> SCROLL_FRENZY;
			};
			case Sorcery.SHAMAN, Orcs.SHAMAN -> SCROLL_FIRE;
			// (0.7.212) a wizard's: warding, the arcane bolt, or (now and then) mending or sanctuary
			default -> switch (random.nextInt(6)) {
				case 0, 1 -> SCROLL_WARDING;
				case 2, 3 -> SCROLL_ARCANE;
				case 4 -> SCROLL_MENDING;
				default -> SCROLL_SANCTUARY;
			};
		});
	}

	/** (0.7.212) A staff that casts: the wizard's, or the healer's. */
	public static boolean isStaff(ItemStack stack) {
		return stack.is(WIZARD_STAFF) || stack.is(HEALER_STAFF);
	}

	/** The spells by name ({@code raising}, {@code draining}, {@code fire}, {@code warding}, {@code arcane}). */
	public static final List<String> SPELLS = List.of("fire", "arcane", "warding", "draining", "raising", "mending", "sanctuary", "restoration",
		"blight", "whispers", "deaths_door", "hemorrhage", "blood_grip", "frenzy");

	/**
	 * Casts {@code spell} for {@code player} at {@code power} (1 off a scroll; a Mage's staff casts harder): true if it
	 * took (a target was there).
	 */
	public static boolean cast(String spell, ServerLevel level, Player player, float power) {
		return switch (spell) {
			case "raising" -> raising(level, player, power);
			case "draining" -> draining(level, player, power);
			case "fire" -> fire(level, player, power);
			case "warding" -> warding(level, player, power);
			case "arcane" -> arcane(level, player, power);
			case "mending" -> Restoration.mending(level, player, power);
			case "sanctuary" -> Restoration.sanctuary(level, player, power);
			case "restoration" -> Restoration.restoration(level, player, power);
			case "blight" -> Necromancy.blightSpell(level, player, power);
			case "whispers" -> Necromancy.whispers(level, player);
			case "deaths_door" -> Necromancy.deathsDoor(level, player, power);
			case "hemorrhage" -> Hemomancy.hemorrhage(level, player, power, false);
			case "blood_grip" -> Hemomancy.gripSpell(level, player, power);
			case "frenzy" -> Hemomancy.frenzy(level, player, power);
			default -> false;
		};
	}

	/** The scroll that teaches {@code spell}. */
	public static Item scrollOf(String spell) {
		return switch (spell) {
			case "raising" -> SCROLL_RAISING;
			case "draining" -> SCROLL_DRAINING;
			case "fire" -> SCROLL_FIRE;
			case "warding" -> SCROLL_WARDING;
			case "mending" -> SCROLL_MENDING;
			case "sanctuary" -> SCROLL_SANCTUARY;
			case "restoration" -> SCROLL_RESTORATION;
			case "blight" -> SCROLL_BLIGHT;
			case "whispers" -> SCROLL_WHISPERS;
			case "deaths_door" -> SCROLL_DEATHS_DOOR;
			case "hemorrhage" -> SCROLL_HEMORRHAGE;
			case "blood_grip" -> SCROLL_BLOOD_GRIP;
			case "frenzy" -> SCROLL_FRENZY;
			default -> SCROLL_ARCANE;
		};
	}

	/** A spell scroll: read once, it crumbles (a Mage learns it instead, {@link Spellcraft#learn}). */
	static final class Scroll extends Item {
		final String spell;

		Scroll(Item.Properties properties, String spell) {
			super(properties.stacksTo(16));
			this.spell = spell;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (!(level instanceof ServerLevel server)) {
				return InteractionResult.SUCCESS;
			}
			if (player instanceof ServerPlayer mage && Spellcraft.learn(mage, this.spell)) {
				stack.consume(1, player);
				return InteractionResult.SUCCESS;
			}
			// (a Sorcerer's magic is quick and small: the great scrolls are beyond them)
			if (Classes.is(player, Classes.PlayerClass.SORCERER) && List.of("raising", "warding", "sanctuary", "restoration").contains(this.spell)) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.scroll_beyond"));
				return InteractionResult.FAIL;
			}
			if (!cast(this.spell, server, player, 1.0F)) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.scroll_no_target"));
				return InteractionResult.FAIL;
			}
			server.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 30, 0.4, 0.5, 0.4, 0.6);
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
			player.getCooldowns().addCooldown(stack, SCROLL_COOLDOWN);
			stack.consume(1, player);
			if (player instanceof ServerPlayer reader) {
				// (0.7.229) death's and blood's are forbidden everywhere
				Spellcraft.afterCast(reader, server, this.spell);
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** What {@code player} is looking at (a living thing within reach), or null. */
	static @Nullable LivingEntity sighted(Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(SCROLL_REACH));
		AABB box = player.getBoundingBox().expandTowards(player.getLookAngle().scale(SCROLL_REACH)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
			e -> e instanceof LivingEntity l && l.isAlive() && !e.isSpectator() && e.isPickable(), SCROLL_REACH * SCROLL_REACH);
		Entity e = hit == null ? null : hit.getEntity();
		return e instanceof LivingEntity l && player.hasLineOfSight(l) ? l : null;
	}

	private static boolean raising(ServerLevel level, Player player, float power) {
		Climate climate = Climate.at(level, player.blockPosition());
		int life = (int) (SCROLL_THRALL_LIFE * power);
		Thralls.raise(level, Sorcery.zombieOf(climate), Sorcery.groundNear(level, player), -1, player.getUUID(), true, life, null);
		Thralls.raise(level, Sorcery.skeletonOf(climate), Sorcery.groundNear(level, player), -1, player.getUUID(), true, life, null);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.0F, 0.8F);
		return true;
	}

	private static boolean draining(ServerLevel level, Player player, float power) {
		LivingEntity target = sighted(player);
		if (target == null) {
			return false;
		}
		Sorcery.drain(level, player, target, SCROLL_DRAIN * power, SCROLL_DRAIN * power);
		return true;
	}

	private static boolean fire(ServerLevel level, Player player, float power) {
		Vec3 look = player.getLookAngle();
		int spread = power > 1.0F ? 2 : 1;
		for (int i = -spread; i <= spread; i++) {
			Vec3 dir = look.yRot(i * 0.18F).normalize();
			SmallFireball bolt = new SmallFireball(level, player, dir);
			bolt.setPos(player.getX() + dir.x * 0.8, player.getEyeY() - 0.15, player.getZ() + dir.z * 0.8);
			level.addFreshEntity(bolt);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
		return true;
	}

	private static boolean warding(ServerLevel level, Player player, float power) {
		for (Player p : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(SCROLL_WARD_RANGE), Entity::isAlive)) {
			p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, (int) (SCROLL_WARD_TICKS * power), SCROLL_WARD_LEVEL), player);
			level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 14, 0.4, 0.6, 0.4, 0.02);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.4F);
		return true;
	}

	private static boolean arcane(ServerLevel level, Player player, float power) {
		LivingEntity target = sighted(player);
		if (target == null) {
			return false;
		}
		Vec3 start = player.getEyePosition().add(0.0, -0.3, 0.0);
		Vec3 step = target.getBoundingBox().getCenter().subtract(start);
		int n = Math.max(2, (int)(step.length() * 3.0));
		for (int i = 0; i <= n; i++) {
			Vec3 p = start.add(step.scale(i / (double)n));
			level.sendParticles(new DustParticleOptions(Sorcery.BOLT_COLOUR, 1.2F), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
		target.hurtServer(level, level.damageSources().indirectMagic(player, player),
			(target.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD) ? SCROLL_BOLT * 2.0F : SCROLL_BOLT) * power);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.6), target.getZ(), 12, 0.2, 0.3, 0.2, 0.2);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.3F);
		return true;
	}
}
