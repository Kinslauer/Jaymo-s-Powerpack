package com.jaymoskingdoms.nether;

import com.jaymoskingdoms.Arcana;
import com.jaymoskingdoms.Classes;
import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.FireCannon;
import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.Speech;
import com.mojang.serialization.Codec;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Andy: "they should all be different classes". Every Flame Avatar of a camp ({@link AvatarCamps}) follows one of our
 * classes ({@link PlayerClass}), no two of a camp the same while there are classes left ({@link #ORDER}), and each is
 * armed and built for it:
 * <ul>
 * <li><b>Tinkerer</b>: carries the {@link FireCannon} its kind made in the Nether, and shoots it. A camp's first avatar
 * is its Tinkerer, and when it falls the next the camp takes in takes its place.</li>
 * <li><b>Warlord</b>: a two-handed war axe, and a harder blow.</li>
 * <li><b>Sorcerer</b> (fire): a staff, and a bolt of fire at a foe out of arm's reach every {@link #BOLT_EVERY} ticks.</li>
 * <li><b>Brute</b>: bare fists, more health, a heavier blow, hard to shove.</li>
 * <li><b>Cleric</b>: a mace, and every {@link #MEND_EVERY} ticks mends the worst hurt of its camp within
 * {@link #MEND_RANGE}.</li>
 * <li><b>Thief</b>: a dagger, quick on its feet.</li>
 * <li><b>Paladin</b>: sword and shield, a thick hide.</li>
 * <li><b>Monk</b>: bare hands, quick, a harder blow and a tougher skin.</li>
 * </ul>
 * An avatar from before the classes takes one the first time it's looked at ({@link #settle}). An Emberborn Tinkerer who
 * speaks to a camp's Tinkerer is handed a fire cannon of their own, once ({@link #spoken}).
 */
public final class AvatarClasses {
	/** The classes a camp fills, in order. */
	static final List<PlayerClass> ORDER = List.of(PlayerClass.TINKERER, PlayerClass.WARLORD, PlayerClass.SORCERER, PlayerClass.BRUTE,
		PlayerClass.CLERIC, PlayerClass.THIEF, PlayerClass.PALADIN, PlayerClass.MONK);
	/** What each class adds: blows (attack damage), health, armour, speed (a share), steadiness (knockback resistance). */
	static final double WARLORD_DAMAGE = 3.0, BRUTE_DAMAGE = 3.0, BRUTE_HEALTH = 12.0, BRUTE_STEADY = 0.6, THIEF_SPEED = 0.2,
		PALADIN_ARMOUR = 8.0, MONK_DAMAGE = 2.0, MONK_ARMOUR = 4.0, MONK_SPEED = 0.1;
	/** A Sorcerer's bolt: this often (ticks), at a foe from this far to this far (blocks). */
	static final int BOLT_EVERY = 100;
	static final double BOLT_NEAR = 4.0, BOLT_FAR = 20.0;
	/** A Cleric's mending: this often (ticks), this far (blocks), this much health. */
	static final int MEND_EVERY = 200;
	static final double MEND_RANGE = 10.0;
	static final float MEND = 6.0F;
	/** The mending's colour (ARGB): hearth gold. */
	static final int MEND_COLOUR = 0xFFFFB040;

	/** On every avatar: its class's key. Saved. */
	public static final AttachmentType<String> CLASS = AttachmentRegistry.create(JaymosKingdoms.id("avatar_class"),
		builder -> builder.persistent(Codec.STRING));
	/** On a player: an avatar Tinkerer has handed them a fire cannon. Saved, kept through death. */
	public static final AttachmentType<Boolean> ARMED = AttachmentRegistry.create(JaymosKingdoms.id("avatar_cannon_given"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath());

	/** When each Sorcerer or Cleric may next cast (game time). */
	private static final Map<Mob, Long> NEXT = new WeakHashMap<>();

	private AvatarClasses() {
	}

	/** Loads the class, so its attachments are registered with the rest. */
	static void init() {
	}

	/** The class {@code avatar} follows, or null (not an avatar, or one not yet looked at). */
	public static @Nullable PlayerClass of(Entity avatar) {
		String key = avatar.getAttached(CLASS);
		return key == null ? null : PlayerClass.byKey(key);
	}

	/** Gives {@code avatar} a class none of its {@code campmates} follows, and that class's arms and build. */
	static void settle(ServerLevel nether, Mob avatar, List<Mob> campmates) {
		if (of(avatar) != null) {
			return;
		}
		Set<PlayerClass> taken = EnumSet.noneOf(PlayerClass.class);
		for (Mob mate : campmates) {
			PlayerClass theirs = mate == avatar ? null : of(mate);
			if (theirs != null) {
				taken.add(theirs);
			}
		}
		PlayerClass cls = ORDER.get(nether.getRandom().nextInt(ORDER.size()));
		for (PlayerClass free : ORDER) {
			if (!taken.contains(free)) {
				cls = free;
				break;
			}
		}
		avatar.setAttached(CLASS, cls.key());
		outfit(nether, avatar, cls);
	}

	private static void outfit(ServerLevel nether, Mob avatar, PlayerClass cls) {
		Item main = Items.AIR, off = Items.AIR;
		switch (cls) {
			case WARLORD -> {
				main = powers("iron_war_axe", Items.IRON_AXE);
				add(avatar, Attributes.ATTACK_DAMAGE, WARLORD_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
			}
			case SORCERER -> main = Arcana.WIZARD_STAFF;
			case BRUTE -> {
				add(avatar, Attributes.ATTACK_DAMAGE, BRUTE_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
				add(avatar, Attributes.MAX_HEALTH, BRUTE_HEALTH, AttributeModifier.Operation.ADD_VALUE);
				add(avatar, Attributes.KNOCKBACK_RESISTANCE, BRUTE_STEADY, AttributeModifier.Operation.ADD_VALUE);
				avatar.setHealth(avatar.getMaxHealth());
			}
			case CLERIC -> main = powers("iron_flanged_mace", Items.GOLDEN_AXE);
			case THIEF -> {
				main = powers("iron_dagger", Items.IRON_SWORD);
				add(avatar, Attributes.MOVEMENT_SPEED, THIEF_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			}
			case PALADIN -> {
				main = Items.IRON_SWORD;
				off = Items.SHIELD;
				add(avatar, Attributes.ARMOR, PALADIN_ARMOUR, AttributeModifier.Operation.ADD_VALUE);
			}
			case MONK -> {
				add(avatar, Attributes.ATTACK_DAMAGE, MONK_DAMAGE, AttributeModifier.Operation.ADD_VALUE);
				add(avatar, Attributes.ARMOR, MONK_ARMOUR, AttributeModifier.Operation.ADD_VALUE);
				add(avatar, Attributes.MOVEMENT_SPEED, MONK_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
			}
			default -> {
			}
		}
		if (cls == PlayerClass.TINKERER && avatar instanceof PathfinderMob gunner) {
			FireCannon.arm(nether, gunner);
			return;
		}
		avatar.setItemSlot(EquipmentSlot.MAINHAND, main == Items.AIR ? ItemStack.EMPTY : new ItemStack(main));
		avatar.setItemSlot(EquipmentSlot.OFFHAND, off == Items.AIR ? ItemStack.EMPTY : new ItemStack(off));
		avatar.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
		avatar.setDropChance(EquipmentSlot.OFFHAND, 0.0F);
	}

	/** Jaymo's Powers' weapon if it's there, else {@code plain}. */
	private static Item powers(String id, Item plain) {
		return BuiltInRegistries.ITEM.getOptional(Identifier.fromNamespaceAndPath("jaymospowers", id)).orElse(plain);
	}

	private static void add(Mob avatar, net.minecraft.core.Holder<Attribute> attribute, double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = avatar.getAttribute(attribute);
		if (instance != null) {
			instance.addOrReplacePermanentModifier(new AttributeModifier(JaymosKingdoms.id("avatar_class"), amount, operation));
		}
	}

	/** Every 2 s for each avatar: a Sorcerer's bolt at its foe, a Cleric's mending of its camp. */
	static void act(ServerLevel nether, Mob avatar, List<Mob> campmates) {
		PlayerClass cls = of(avatar);
		if (cls != PlayerClass.SORCERER && cls != PlayerClass.CLERIC) {
			return;
		}
		long now = nether.getGameTime();
		if (now < NEXT.getOrDefault(avatar, 0L)) {
			return;
		}
		if (cls == PlayerClass.SORCERER) {
			LivingEntity foe = avatar.getTarget() != null ? avatar.getTarget() : avatar.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
				? avatar.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) : null;
			if (foe == null || !foe.isAlive() || !avatar.hasLineOfSight(foe)) {
				return;
			}
			double d = avatar.distanceTo(foe);
			if (d < BOLT_NEAR || d > BOLT_FAR) {
				return;
			}
			NEXT.put(avatar, now + BOLT_EVERY);
			avatar.getLookControl().setLookAt(foe, 30.0F, 30.0F);
			Vec3 dir = new Vec3(foe.getX(), foe.getY(0.5), foe.getZ()).subtract(avatar.getEyePosition()).normalize();
			SmallFireball bolt = new SmallFireball(nether, avatar, dir);
			bolt.setPos(avatar.getX() + dir.x * 0.8, avatar.getEyeY() - 0.15, avatar.getZ() + dir.z * 0.8);
			nether.addFreshEntity(bolt);
			nether.playSound(null, avatar.getX(), avatar.getY(), avatar.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0F, 1.0F);
			return;
		}
		Mob worst = null;
		for (Mob mate : campmates) {
			if (mate.isAlive() && mate.getHealth() < mate.getMaxHealth() - 2.0F && mate.distanceToSqr(avatar) <= MEND_RANGE * MEND_RANGE
				&& (worst == null || mate.getHealth() / mate.getMaxHealth() < worst.getHealth() / worst.getMaxHealth())) {
				worst = mate;
			}
		}
		if (worst != null) {
			NEXT.put(avatar, now + MEND_EVERY);
			worst.heal(MEND);
			Fx.ring(nether, worst.position(), 1.2F, MEND_COLOUR);
			nether.playSound(null, worst.getX(), worst.getY(), worst.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 0.6F, 1.6F);
		}
	}

	/** An Emberborn spoke to {@code avatar}: a camp's Tinkerer hands a Tinkerer of its blood a fire cannon, once. */
	static void spoken(ServerLevel level, ServerPlayer player, Mob avatar) {
		if (of(avatar) != PlayerClass.TINKERER || player.getAttachedOrElse(ARMED, false)
			|| !Classes.both(player).contains(PlayerClass.TINKERER)) {
			return;
		}
		player.setAttached(ARMED, true);
		Speech.tell(level, avatar, player, Component.translatable("message.jaymoskingdoms.avatar.cannon"), Speech.GOLD);
		player.getInventory().placeItemBackInInventory(new ItemStack(FireCannon.ITEM), net.minecraft.util.Prediction.SERVER_ONLY);
	}
}
