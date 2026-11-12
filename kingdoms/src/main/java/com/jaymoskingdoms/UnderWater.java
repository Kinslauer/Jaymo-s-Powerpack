package com.jaymoskingdoms;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.fish.WaterAnimal;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Fighting under water (fifth edition's underwater combat, Andy's pick C10). Most weapons swing badly with the water in
 * the way; some are made for it. With the head under water:
 * <ul>
 * <li><b>Swords and axes</b> swing slowly ({@link #SLOWER}, a share off the attack speed, put on while it is true) and
 * weakly ({@link #WEAKER}, a share off every blow).</li>
 * <li><b>Spears, tridents, daggers</b> work as on land, and so do fists, maces and the rest, which the row leaves alone.</li>
 * <li><b>Bows</b> barely reach: an arrow loosed from under water leaves at {@link #BOW_SPEED} of its speed and the water
 * does the rest.</li>
 * <li><b>Crossbows</b> work up close: the bolt leaves at full speed but is spent after {@link #BOLT_LIFE} ticks, a few
 * blocks.</li>
 * </ul>
 * Water-folk (the Tideborn, the drowned, fish) fight as they always do. It is the same for players and mobs.
 */
public final class UnderWater {
	/** Swords and axes: this much of the attack speed is lost (a share), and every blow lands at this share. */
	static final double SLOWER = -0.4;
	static final float WEAKER = 0.6F;
	/** A bow's arrow leaves this share of its speed; a crossbow's bolt lasts this many ticks. */
	static final double BOW_SPEED = 0.3;
	static final int BOLT_LIFE = 8;
	/** How often (ticks) players' swords are slowed or freed. */
	static final int EVERY = 5;
	static final Identifier SLOW_ID = JaymosKingdoms.id("underwater_swing");
	/** Spears, daggers and the like by name, for weapons other mods add. */
	private static final String[] WORKS = {"spear", "dagger", "knife", "trident", "rapier", "javelin", "harpoon", "lance"};
	/** Weapons that work under water by tag (a mod can add its own). */
	public static final TagKey<Item> WORKS_TAG = TagKey.create(Registries.ITEM, JaymosKingdoms.id("works_under_water"));

	private UnderWater() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractArrow arrow && arrow.getOwner() instanceof LivingEntity shooter && swimming(shooter)) {
				loosed(arrow, shooter);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(UnderWater::tick);
	}

	/** Whether {@code who} fights from under water as a land-dweller does: head under, and not water-folk. */
	static boolean swimming(LivingEntity who) {
		return who.isUnderWater() && !(who instanceof WaterAnimal) && !(who instanceof Drowned) && !(who instanceof ServerPlayer p && Heritage.of(p) == Heritage.Kind.TIDEBORN);
	}

	/** Whether {@code weapon} is a sword or an axe (what the water hampers). */
	static boolean heavy(ItemStack weapon) {
		return !weapon.isEmpty() && !works(weapon) && (weapon.is(ItemTags.SWORDS) || weapon.is(ItemTags.AXES) || DamageKinds.ofWeapon(weapon) == DamageKinds.Kind.SLASHING
			&& !weapon.is(ItemTags.HOES));
	}

	/** Whether {@code weapon} is one that works under water. */
	static boolean works(ItemStack weapon) {
		if (weapon.is(WORKS_TAG) || weapon.is(ItemTags.SPEARS) || weapon.is(Items.TRIDENT) || weapon.is(Items.CROSSBOW)) {
			return true;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(weapon.getItem());
		String name = id == null ? "" : id.getPath();
		for (String word : WORKS) {
			if (name.contains(word)) {
				return true;
			}
		}
		return false;
	}

	/** A blow from under water with a sword or axe lands weak. */
	public static float dealt(LivingEntity victim, DamageSource source, float amount) {
		if (amount > 0.0F && source.getDirectEntity() == source.getEntity() && source.getEntity() instanceof LivingEntity attacker && swimming(attacker)) {
			ItemStack weapon = source.getWeaponItem();
			if (weapon != null && heavy(weapon)) {
				return amount * WEAKER;
			}
		}
		return amount;
	}

	/** An arrow loosed from under water: a bow's goes slow, a crossbow's bolt lives only a moment. */
	private static void loosed(AbstractArrow arrow, LivingEntity shooter) {
		ItemStack weapon = arrow.getWeaponItem();
		if (weapon == null || weapon.isEmpty()) {
			return;
		}
		if (weapon.is(Items.BOW)) {
			Vec3 v = arrow.getDeltaMovement();
			arrow.setDeltaMovement(v.scale(BOW_SPEED));
			arrow.syncVelocity = true;
		} else if (weapon.is(Items.CROSSBOW)) {
			// a bolt spent after a moment: it is put out of the way when its time is up
			arrow.addTag(LIFE_TAG);
			SPENT.put(arrow, arrow.level().getGameTime() + BOLT_LIFE);
		}
	}

	static final String LIFE_TAG = "jaymoskingdoms.underwater_bolt";
	private static final java.util.Map<AbstractArrow, Long> SPENT = new java.util.WeakHashMap<>();

	private static void tick(MinecraftServer server) {
		if (!SPENT.isEmpty()) {
			var it = SPENT.entrySet().iterator();
			while (it.hasNext()) {
				var e = it.next();
				AbstractArrow bolt = e.getKey();
				if (bolt.isRemoved()) {
					it.remove();
				} else if (bolt.level().getGameTime() >= e.getValue()) {
					if (bolt.isInWater()) {
						// (its force spent, it sinks)
						bolt.setDeltaMovement(bolt.getDeltaMovement().scale(0.1));
					}
					it.remove();
				}
			}
		}
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			AttributeInstance speed = player.getAttribute(Attributes.ATTACK_SPEED);
			if (speed == null) {
				continue;
			}
			boolean slowed = swimming(player) && heavy(player.getMainHandItem());
			AttributeModifier now = speed.getModifier(SLOW_ID);
			if (slowed && now == null) {
				speed.addTransientModifier(new AttributeModifier(SLOW_ID, SLOWER, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			} else if (!slowed && now != null) {
				speed.removeModifier(SLOW_ID);
			}
		}
	}
}
