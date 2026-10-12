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

	public static Item BONE_STAFF, TOTEM_STAFF, WIZARD_STAFF;
	public static Item SCROLL_RAISING, SCROLL_DRAINING, SCROLL_FIRE, SCROLL_WARDING, SCROLL_ARCANE;

	private Arcana() {
	}

	static void register() {
		BONE_STAFF = item("bone_staff", p -> new Item(p.stacksTo(1)));
		TOTEM_STAFF = item("totem_staff", p -> new Item(p.stacksTo(1)));
		WIZARD_STAFF = item("wizard_staff", p -> new Item(p.stacksTo(1)));
		SCROLL_RAISING = item("scroll_of_raising", p -> new Scroll(p, Arcana::raising));
		SCROLL_DRAINING = item("scroll_of_draining", p -> new Scroll(p, Arcana::draining));
		SCROLL_FIRE = item("scroll_of_fire", p -> new Scroll(p, Arcana::fire));
		SCROLL_WARDING = item("scroll_of_warding", p -> new Scroll(p, Arcana::warding));
		SCROLL_ARCANE = item("scroll_of_arcane_bolt", p -> new Scroll(p, Arcana::arcane));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.COMBAT).register(output -> {
			Item after = Items.TOTEM_OF_UNDYING;
			for (Item item : List.of(SCROLL_RAISING, SCROLL_DRAINING, SCROLL_FIRE, SCROLL_WARDING, SCROLL_ARCANE, BONE_STAFF, TOTEM_STAFF, WIZARD_STAFF)) {
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
			case Sorcery.NECROMANCER -> random.nextBoolean() ? SCROLL_RAISING : SCROLL_DRAINING;
			case Sorcery.SHAMAN -> SCROLL_FIRE;
			default -> random.nextBoolean() ? SCROLL_WARDING : SCROLL_ARCANE;
		});
	}

	/** A spell read off a scroll: true if it took (the scroll is spent). */
	@FunctionalInterface
	interface Spell {
		boolean cast(ServerLevel level, Player player);
	}

	/** A spell scroll: read once, it crumbles. */
	static final class Scroll extends Item {
		private final Spell spell;

		Scroll(Item.Properties properties, Spell spell) {
			super(properties.stacksTo(16));
			this.spell = spell;
		}

		@Override
		public InteractionResult use(Level level, Player player, InteractionHand hand) {
			ItemStack stack = player.getItemInHand(hand);
			if (!(level instanceof ServerLevel server)) {
				return InteractionResult.SUCCESS;
			}
			if (!this.spell.cast(server, player)) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.scroll_no_target"));
				return InteractionResult.FAIL;
			}
			server.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 30, 0.4, 0.5, 0.4, 0.6);
			server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 0.8F);
			player.getCooldowns().addCooldown(stack, SCROLL_COOLDOWN);
			stack.consume(1, player);
			if (player instanceof ServerPlayer reader) {
				Theft.sorcery(reader, server, player.blockPosition());
			}
			return InteractionResult.SUCCESS;
		}
	}

	/** What {@code player} is looking at (a living thing within reach), or null. */
	private static @Nullable LivingEntity sighted(Player player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(SCROLL_REACH));
		AABB box = player.getBoundingBox().expandTowards(player.getLookAngle().scale(SCROLL_REACH)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
			e -> e instanceof LivingEntity l && l.isAlive() && !e.isSpectator() && e.isPickable(), SCROLL_REACH * SCROLL_REACH);
		Entity e = hit == null ? null : hit.getEntity();
		return e instanceof LivingEntity l && player.hasLineOfSight(l) ? l : null;
	}

	private static boolean raising(ServerLevel level, Player player) {
		Climate climate = Climate.at(level, player.blockPosition());
		Thralls.raise(level, Sorcery.zombieOf(climate), Sorcery.groundNear(level, player), -1, player.getUUID(), true, SCROLL_THRALL_LIFE, null);
		Thralls.raise(level, Sorcery.skeletonOf(climate), Sorcery.groundNear(level, player), -1, player.getUUID(), true, SCROLL_THRALL_LIFE, null);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_PREPARE_SUMMON, SoundSource.PLAYERS, 1.0F, 0.8F);
		return true;
	}

	private static boolean draining(ServerLevel level, Player player) {
		LivingEntity target = sighted(player);
		if (target == null) {
			return false;
		}
		Sorcery.drain(level, player, target, SCROLL_DRAIN, SCROLL_DRAIN);
		return true;
	}

	private static boolean fire(ServerLevel level, Player player) {
		Vec3 look = player.getLookAngle();
		for (int i = -1; i <= 1; i++) {
			Vec3 dir = look.yRot(i * 0.18F).normalize();
			SmallFireball bolt = new SmallFireball(level, player, dir);
			bolt.setPos(player.getX() + dir.x * 0.8, player.getEyeY() - 0.15, player.getZ() + dir.z * 0.8);
			level.addFreshEntity(bolt);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.0F);
		return true;
	}

	private static boolean warding(ServerLevel level, Player player) {
		for (Player p : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(SCROLL_WARD_RANGE), Entity::isAlive)) {
			p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, SCROLL_WARD_TICKS, SCROLL_WARD_LEVEL), player);
			level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.0, p.getZ(), 14, 0.4, 0.6, 0.4, 0.02);
		}
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.4F);
		return true;
	}

	private static boolean arcane(ServerLevel level, Player player) {
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
			target.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD) ? SCROLL_BOLT * 2.0F : SCROLL_BOLT);
		level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.6), target.getZ(), 12, 0.2, 0.3, 0.2, 0.2);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 1.3F);
		return true;
	}
}
