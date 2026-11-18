package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The Druid's tree (class trees, docs/ClassTrees-Plan.md). {@link Druids} keeps the starting move (one shape: the first beast
 * studied, worn longer as the Druid rises in the class) and asks here for the rest:
 * <ul>
 * <li><b>More Shapes</b> (3): one more of the beasts studied may be worn, each rank (in the order they were learned).</li>
 * <li><b>Long Shape</b> (3): shapes last twice as long.</li>
 * <li><b>Fox and Stag</b> (5): the fox (small, quiet, quick to dig) and the stag (fast on open ground, and a charge at a run)
 * may be studied and worn: the vanilla fox and the pack's deer.</li>
 * <li><b>Circle of the Moon</b> or <b>of the Land</b> (10): harder blows in a shape and health back as you take one; or more
 * rites known by heart and quicker casting out of shape.</li>
 * <li><b>Pack Leader</b> (12): in a shape, the wild beasts of that kind about follow you and fight beside you; you are drawn,
 * and sized, larger than they are.</li>
 * <li><b>Barkskin</b> (15): sneak and the key in a shape, or sneak and the key held in a shape or out of one, spends a shape's
 * use: for a few minutes your skin is bark, more armour and slower.</li>
 * <li><b>Elder Shape</b> (20): once a day, the treant or the great wind elemental joins the shapes you can pick.</li>
 * </ul>
 */
public final class WildShapes {
	/** Long Shape: how many times as long. */
	static final float LONG = 2.0F;
	/** The fox: its size, how much quieter it is; the stag: open-ground speed, its charge (harm, push, the wait on each foe). */
	static final double FOX_SCALE = -0.5, STAG_PUSH = 1.2;
	static final float FOX_QUIET = 0.4F, STAG_CHARGE = 5.0F;
	static final int STAG_EVERY = 20;
	/** Circle of the Moon: blows in a shape this share harder, and the health taking one gives back. */
	static final float MOON_HIT = 0.25F, MOON_HEAL = 6.0F;
	/** Circle of the Land: casts out of shape wait this share; the rites known by heart. */
	static final float LAND_COOLDOWN = 0.7F;
	static final Set<String> LAND_RITES = Set.of("spike_growth", "fog", "water_walk", "spider_climb", "harrying_gale");
	/** Pack Leader: how far the beasts are gathered from, and how big the leader is drawn. */
	static final double PACK_RANGE = 16.0, PACK_SCALE = 0.35;
	/** Pack Leader: a beast with no bite of its own (a deer) charges and butts for this much, when this near. */
	static final float PACK_BUTT = 3.0F;
	static final double PACK_BUTT_REACH = 2.0;
	/** Barkskin: how long (ticks), its armour, toughness and the slowing. */
	static final int BARK_TICKS = 20 * 60 * 3;
	static final double BARK_ARMOUR = 6.0, BARK_TOUGH = 2.0, BARK_SLOW = -0.15;
	/** Elder Shape: once a day (back with a night's sleep); its health and how long it lasts (a share of a shape's). */
	static final String ELDER_POOL = "elder";
	static final double TREANT_HEALTH = 20.0, ELEMENTAL_HEALTH = 10.0;
	static final float ELDER_TIME = 0.5F, TREANT_SIZE = 0.8F, ELEMENTAL_SIZE = 1.2F;

	public static final String SHAPES = "dru_shapes", LONG_KEY = "dru_long", FOXSTAG = "dru_foxstag", MOON = "dru_moon", LAND = "dru_land",
		PACK = "dru_pack", BARK = "dru_bark", ELDER = "dru_elder";
	public static final String FOX = "fox", STAG = "stag", TREANT = "treant", ELEMENTAL = "elemental";
	static final Identifier DEER = Identifier.fromNamespaceAndPath("naturalist", "deer");

	/** The forms in the order they were learned. Saved, kept through death. */
	static final AttachmentType<List<String>> LEARNED = AttachmentRegistry.create(JaymosKingdoms.id("druid_learned"),
		b -> b.persistent(Codec.STRING.listOf()).copyOnDeath());

	private static final Identifier BARK_ARMOUR_ID = JaymosKingdoms.id("druid_bark_armour"), BARK_TOUGH_ID = JaymosKingdoms.id("druid_bark_tough"),
		BARK_SLOW_ID = JaymosKingdoms.id("druid_bark_slow"), SMALL_ID = JaymosKingdoms.id("druid_small"), LEADER_ID = JaymosKingdoms.id("druid_leader");
	private static final Map<UUID, Long> BARKED = new HashMap<>();
	/** The stag's charge: foe → when it may be struck again. */
	private static final Map<Integer, Long> CHARGED = new HashMap<>();

	private WildShapes() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(WildShapes::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			BARKED.clear();
			CHARGED.clear();
		});
	}

	static boolean has(Player player, String key) {
		return Classes.is(player, PlayerClass.DRUID) && ClassTrees.has(player, key) > 0;
	}

	// ---- which shapes ---------------------------------------------------------------------------------------------------------

	/** A form was learned: kept in its order. */
	static void learned(Player player, String form) {
		List<String> order = new ArrayList<>(player.getAttachedOrElse(LEARNED, List.of()));
		if (!order.contains(form)) {
			order.add(form);
			player.setAttached(LEARNED, List.copyOf(order));
		}
	}

	/**
	 * The shapes {@code player} may take of those {@code studied}: in the order learned, the fox and the stag only with Fox
	 * and Stag (the stag only where the pack's deer are), as many as their slots (one, and one a rank of More Shapes); then the
	 * elder shapes.
	 */
	static List<String> usable(Player player, List<String> studied) {
		List<String> order = new ArrayList<>();
		for (String f : player.getAttachedOrElse(LEARNED, List.<String>of())) {
			if (studied.contains(f)) {
				order.add(f);
			}
		}
		for (String f : studied) {
			if (!order.contains(f)) {
				order.add(f);
			}
		}
		order.removeIf(f -> (f.equals(FOX) || f.equals(STAG)) && !has(player, FOXSTAG) || f.equals(STAG) && deer() == null);
		int slots = 1 + Math.round(Classes.is(player, PlayerClass.DRUID) ? ClassTrees.power(player, SHAPES) : 0.0F);
		List<String> out = new ArrayList<>(order.subList(0, Math.min(slots, order.size())));
		if (has(player, ELDER)) {
			out.add(TREANT);
			out.add(ELEMENTAL);
		}
		return out;
	}

	static boolean elder(String form) {
		return form.equals(TREANT) || form.equals(ELEMENTAL);
	}

	/** Spends the use a shape costs: an elder shape's once a day, any other a shape from the short rest's. */
	static boolean spend(ServerPlayer player, String form, int shapes) {
		if (elder(form)) {
			if (!Rests.spend(player, ELDER_POOL, 1, 1)) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.druid.elder_spent"));
				return false;
			}
			return true;
		}
		if (!Rests.spend(player, Rests.SHAPE, 1, shapes)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.druid.tired"));
			return false;
		}
		return true;
	}

	/** How long {@code form} lasts on {@code player}, from a shape's own time: practice, Long Shape; an elder shape, half. */
	static long ticks(Player player, String form, int base) {
		float t = base * ClassTrees.practice(player, PlayerClass.DRUID) * (has(player, LONG_KEY) ? LONG : 1.0F);
		return Math.round(elder(form) ? t * ELDER_TIME : t);
	}

	private static @Nullable EntityType<?> deer() {
		return BuiltInRegistries.ENTITY_TYPE.getOptional(DEER).orElse(null);
	}

	/** The stand-in for the tree's own shapes (null for the four of old). */
	static @Nullable Mob stand(ServerLevel level, String form) {
		EntityType<?> type = switch (form) {
			case FOX -> net.minecraft.world.entity.EntityTypes.FOX;
			case STAG -> deer();
			case TREANT -> net.minecraft.world.entity.EntityTypes.CREAKING;
			case ELEMENTAL -> net.minecraft.world.entity.EntityTypes.BREEZE;
			default -> null;
		};
		return type != null && type.create(level, EntitySpawnReason.EVENT) instanceof Mob mob ? mob : null;
	}

	/** The extra health of the tree's own shapes. */
	static double health(String form) {
		return switch (form) {
			case TREANT -> TREANT_HEALTH;
			case ELEMENTAL -> ELEMENTAL_HEALTH;
			default -> 0.0;
		};
	}

	/** A shape is taken: the fox's size, Moon's health back, the leader drawn larger, an elder's bulk. */
	static void taken(ServerPlayer player, Mob beast, String form) {
		if (form.equals(FOX)) {
			modifier(player, Attributes.SCALE, SMALL_ID, FOX_SCALE, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
		double size = (form.equals(TREANT) ? TREANT_SIZE : form.equals(ELEMENTAL) ? ELEMENTAL_SIZE : 0.0) + (has(player, PACK) ? PACK_SCALE : 0.0);
		if (size > 0.0) {
			modifier(beast, Attributes.SCALE, LEADER_ID, size, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		}
		if (has(player, MOON)) {
			player.heal(MOON_HEAL);
		}
	}

	/** The shape is shed: the fox's size given back. */
	static void shed(ServerPlayer player) {
		AttributeInstance scale = player.getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.removeModifier(SMALL_ID);
		}
	}

	private static void modifier(LivingEntity e, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id,
		double amount, AttributeModifier.Operation op) {
		AttributeInstance a = e.getAttribute(attribute);
		if (a != null) {
			a.removeModifier(id);
			a.addTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	/** The tree's own shapes' strengths, put on for a moment more (as {@link Druids}' are). */
	static void buffs(ServerPlayer player, String form) {
		switch (form) {
			case FOX -> {
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 0, true, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.HASTE, 60, 1, true, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, false));
			}
			case STAG -> {
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, open(player) ? 2 : 0, true, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 60, 1, true, false, false));
			}
			case TREANT -> {
				player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 2, true, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 1, true, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 1, true, false, false));
			}
			case ELEMENTAL -> {
				player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1, true, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 60, 3, true, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, false, false));
				player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, 1, true, false, false));
			}
			default -> {
			}
		}
	}

	/** Open ground: the sky over them. */
	private static boolean open(ServerPlayer player) {
		return player.level().canSeeSky(player.blockPosition().above());
	}

	/** The fox is quiet ({@link Awareness#noise}). */
	static float quiet(Player player) {
		return FOX.equals(player.getAttachedOrElse(Druids.FORM, "")) ? FOX_QUIET : 1.0F;
	}

	/** Circle of the Land: casts out of shape wait less. */
	static float cooldown(Player player) {
		return has(player, LAND) && !Druids.shaped(player) ? LAND_COOLDOWN : 1.0F;
	}

	/** Circle of the Land: the rites a Druid knows by heart. */
	static boolean born(Player player, String spell) {
		return has(player, LAND) && LAND_RITES.contains(spell);
	}

	// ---- sneak and the key in a shape -------------------------------------------------------------------------------------------

	/** Barkskin. False when it isn't theirs. */
	static boolean bark(ServerPlayer player, int shapes) {
		if (!has(player, BARK)) {
			return false;
		}
		if (!Rests.spend(player, Rests.SHAPE, 1, shapes)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.druid.tired"));
			return true;
		}
		BARKED.put(player.getUUID(), player.level().getGameTime() + BARK_TICKS);
		modifier(player, Attributes.ARMOR, BARK_ARMOUR_ID, BARK_ARMOUR, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.ARMOR_TOUGHNESS, BARK_TOUGH_ID, BARK_TOUGH, AttributeModifier.Operation.ADD_VALUE);
		modifier(player, Attributes.MOVEMENT_SPEED, BARK_SLOW_ID, BARK_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.COMPOSTER, player.getX(), player.getY(0.5), player.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WOOD_PLACE, SoundSource.PLAYERS, 1.0F, 0.7F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.druid.bark"));
		return true;
	}

	private static void unbark(ServerPlayer player) {
		for (var e : List.of(Map.entry(Attributes.ARMOR, BARK_ARMOUR_ID), Map.entry(Attributes.ARMOR_TOUGHNESS, BARK_TOUGH_ID),
			Map.entry(Attributes.MOVEMENT_SPEED, BARK_SLOW_ID))) {
			AttributeInstance a = player.getAttribute(e.getKey());
			if (a != null) {
				a.removeModifier(e.getValue());
			}
		}
	}

	// ---- the blows ------------------------------------------------------------------------------------------------------------

	/** Circle of the Moon: a Druid's blow in a shape lands harder. */
	static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (source.getEntity() instanceof ServerPlayer druid && source.getDirectEntity() == druid && druid != target && Druids.shaped(druid)
			&& has(druid, MOON)) {
			return amount * (1.0F + MOON_HIT);
		}
		return amount;
	}

	// ---- the tick -------------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long now = server.overworld().getGameTime();
		if (!BARKED.isEmpty()) {
			BARKED.entrySet().removeIf(e -> {
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				if (p != null && e.getValue() > now && p.isAlive()) {
					return false;
				}
				if (p != null) {
					unbark(p);
				}
				return true;
			});
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			String form = player.getAttachedOrElse(Druids.FORM, "");
			if (form.isEmpty()) {
				continue;
			}
			if (form.equals(STAG) && player.isSprinting()) {
				charge(player, now);
			}
			if ((now + player.getId()) % 20 == 0 && has(player, PACK)) {
				pack(player, form);
			}
		}
		if (now % 1200 == 0) {
			CHARGED.values().removeIf(t -> t < now);
		}
	}

	/** The stag at a run: what stands before it is struck and thrown aside. */
	private static void charge(ServerPlayer player, long now) {
		ServerLevel level = player.level();
		Vec3 ahead = Vec3.directionFromRotation(0.0F, player.getYRot());
		for (LivingEntity foe : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().move(ahead.scale(0.8)).inflate(0.3, 0.0, 0.3),
			e -> e != player && e.isAlive() && !(e instanceof Player) && !Druids.standIn(e) && !(e instanceof OwnableEntity o && o.getOwnerReference() != null))) {
			if (CHARGED.getOrDefault(foe.getId(), 0L) > now) {
				continue;
			}
			CHARGED.put(foe.getId(), now + STAG_EVERY);
			DamageSource source = player.damageSources().playerAttack(player);
			foe.hurtServer(level, source, STAG_CHARGE);
			foe.knockback(STAG_PUSH, -ahead.x, -ahead.z, source, STAG_CHARGE);
			level.playSound(null, foe.blockPosition(), SoundEvents.GOAT_RAM_IMPACT, SoundSource.PLAYERS, 1.0F, 1.0F);
		}
	}

	/** Pack Leader: the wild beasts of the shape's kind about follow, and go for what the Druid fights. */
	private static void pack(ServerPlayer player, String form) {
		List<String> kin = Druids.KIN.get(form);
		if (kin == null) {
			return;
		}
		ServerLevel level = player.level();
		LivingEntity foe = player.getLastHurtMob() != null && player.getLastHurtMob().isAlive() && player.tickCount - player.getLastHurtMobTimestamp() < 200
			? player.getLastHurtMob() : player.getLastHurtByMob();
		for (PathfinderMob beast : level.getEntitiesOfClass(PathfinderMob.class, player.getBoundingBox().inflate(PACK_RANGE),
			b -> b.isAlive() && !Druids.standIn(b) && kin.contains(FoeStudy.kind(b)) && !(b instanceof OwnableEntity o && o.getOwnerReference() != null))) {
			if (beast.getTarget() == player) {
				beast.setTarget(null);
			}
			if (foe != null && foe != beast && foe.isAlive() && foe.distanceTo(player) < PACK_RANGE) {
				beast.setTarget(foe);
				if (beast.getAttribute(Attributes.ATTACK_DAMAGE) == null) {
					// a beast that never fights (the stag's deer) has no attack of its own: it charges and butts
					beast.getNavigation().moveTo(foe, 1.4);
					if (beast.distanceTo(foe) < PACK_BUTT_REACH) {
						foe.hurtServer(level, level.damageSources().mobAttack(beast), PACK_BUTT);
					}
				}
			} else if (beast.distanceTo(player) > 4.0) {
				beast.getNavigation().moveTo(player, 1.2);
			}
		}
	}
}
