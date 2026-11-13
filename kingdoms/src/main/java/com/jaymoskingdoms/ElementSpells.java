package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Nine spells a Sorcerer can keep. Four are of volcanic glass, which is no element: any Sorcerer learns them from a scroll,
 * whatever their blood. Three are fire's, one is lightning's, and one is a forbidden art.
 * <ul>
 * <li><b>Glass Shards</b>: a fan of splinters that cut and open wounds.</li>
 * <li><b>Glass Spires</b>: a row of spikes comes up out of the ground away from the caster, each lifting and slowing
 * whoever stands on it.</li>
 * <li><b>Glass Skin</b>: a held coat that turns blows and cuts whoever lands one; after enough blows it bursts.</li>
 * <li><b>Molten Glass</b>: a pool that burns while it glows, holds fast whoever is still in it as it cools, then
 * shatters.</li>
 * <li><b>Fire Ring</b>: a held ring of fire round the caster that burns and throws back whoever crosses it.</li>
 * <li><b>Fire Pillars</b>: three held columns of fire that turn about the caster.</li>
 * <li><b>Fire Lance</b>: a held stream of fire. With Jaymo's Powers it meets other streams ({@link PowersCompat#lanceStart});
 * the class key shoves the knot.</li>
 * <li><b>Black Flame</b>: a forbidden art ({@link ForbiddenArts}). It burns in water, leaps once to a body close by, and
 * only healing magic or a dispel puts it out ({@link #quench}).</li>
 * <li><b>Storm Cloak</b>: a held coat of lightning: quick feet, a shock for whoever strikes the caster, and an arc to a
 * foe close by.</li>
 * </ul>
 * A Mage or any staff caster learns and casts them like any scroll's spell. A Sorcerer cannot hold a staff's spells, so they
 * keep these with themselves ({@link #LEARNED}) and cast them from the Magic screen.
 */
public final class ElementSpells {
	/** The four of glass, fire's three, lightning's one and the forbidden one. */
	static final List<String> GLASS = List.of("glass_shards", "glass_spires", "glass_skin", "molten_glass");
	static final List<String> FIRE = List.of("fire_ring", "fire_pillars", "fire_lance");
	static final String BLACK_FLAME = "black_flame", STORM_CLOAK = "storm_cloak";
	static final List<String> IDS = List.of("glass_shards", "glass_spires", "glass_skin", "molten_glass", "fire_ring", "fire_pillars", "fire_lance",
		BLACK_FLAME, STORM_CLOAK);

	/** The colours of each. */
	static final int GLASS_COLOUR = 0x4A3A6E, MOLTEN_COLOUR = 0xFF9A3C, FIRE_COLOUR = 0xFF7A2A, BLACK_COLOUR = 0x2A1238, STORM_COLOUR = 0xBFD8FF;

	/** Shards: how far and how wide (the cosine of the half angle) the fan goes, its damage, and how many splinters are drawn. */
	static final double SHARDS_REACH = 9.0, SHARDS_COS = 0.82;
	static final float SHARDS_DAMAGE = 5.0F;
	static final int SHARDS_DRAWN = 7;

	/** Spires: how many come up, the gap between them (blocks), the ticks between one and the next, each one's reach, damage, lift and slow. */
	static final int SPIRES = 7, SPIRE_EVERY = 2, SPIRE_SLOW_TICKS = 60;
	static final double SPIRE_GAP = 1.6, SPIRE_RADIUS = 1.3, SPIRE_LIFT = 0.65;
	static final float SPIRE_DAMAGE = 6.0F;

	/** Skin: how long it holds (ticks), how many blows it takes, what a blow costs the one who lands it, and the burst at the end. */
	static final int SKIN_TICKS = 20 * 40, SKIN_BLOWS = 5;
	static final float SKIN_CUT = 2.0F, SKIN_BURST = 4.0F;
	static final double SKIN_BURST_RADIUS = 3.0;

	/** Molten glass: how far it is thrown, its size, how long it glows then cools (ticks), and what each stage does. */
	static final double MOLTEN_REACH = 16.0, MOLTEN_RADIUS = 2.6;
	static final int MOLTEN_HOT = 20 * 5, MOLTEN_COOL = 20 * 5;
	static final float MOLTEN_BURN = 2.0F, MOLTEN_SHATTER = 5.0F;

	/** Fire ring: how long it is held (ticks), where the fire stands (blocks from the caster), its bite and its shove. */
	static final int RING_TICKS = 20 * 20;
	static final double RING_RADIUS = 3.0, RING_WIDTH = 0.9, RING_SHOVE = 0.7;
	static final float RING_DAMAGE = 3.0F;

	/** Fire pillars: how long they are held (ticks), how far out they turn, how fast (radians a tick), their reach and bite. */
	static final int PILLARS_TICKS = 20 * 15, PILLARS = 3, PILLAR_AGAIN = 10;
	static final double PILLAR_ORBIT = 3.5, PILLAR_TURN = 0.11, PILLAR_RADIUS = 1.1, PILLAR_HEIGHT = 3.5;
	static final float PILLAR_DAMAGE = 4.0F;

	/** Fire lance: how long it is held (ticks), how far it reaches (the same as Powers' side of it), its width, and its bite every few ticks. */
	static final int LANCE_TICKS = 20 * 6, LANCE_EVERY = 4;
	static final double LANCE_REACH = 14.0, LANCE_WIDTH = 0.8;
	static final float LANCE_DAMAGE = 2.5F;

	/** Black flame: how far it is thrown, how long it burns (ticks), its bite each second, when it leaps and how far. */
	static final double BLACK_REACH = 18.0, BLACK_LEAP = 3.5;
	static final int BLACK_TICKS = 20 * 30, BLACK_LEAPS_AFTER = 20 * 5;
	static final float BLACK_DAMAGE = 1.5F;

	/** Storm cloak: how long it is held (ticks), the shock for a blow landed on the caster, and the arc to a foe near by. */
	static final int CLOAK_TICKS = 20 * 30, CLOAK_ARC_EVERY = 30;
	static final float CLOAK_SHOCK = 3.0F, CLOAK_ARC = 2.0F;
	static final double CLOAK_ARC_REACH = 5.0;

	/** The spells a Sorcerer has learned for good from these scrolls. */
	static final AttachmentType<List<String>> LEARNED = AttachmentRegistry.create(JaymosKingdoms.id("sorcerer_spells"),
		b -> b.persistent(Codec.STRING.listOf()).copyOnDeath());

	private record Skin(long until, int blows) {
	}

	private record Spire(ServerLevel level, UUID owner, Vec3 at, long when) {
	}

	private record Molten(ServerLevel level, UUID owner, Vec3 at, long made) {
	}

	private record Burn(long until, UUID by, boolean leaps, long lit) {
	}

	private static final Map<UUID, Skin> SKINS = new HashMap<>();
	private static final Map<UUID, Long> RINGS = new HashMap<>(), PILLARS_UP = new HashMap<>(), LANCES = new HashMap<>(), CLOAKS = new HashMap<>();
	private static final Map<UUID, Map<UUID, Long>> SCORCHED = new HashMap<>();
	private static final List<Spire> SPIRES_DUE = new ArrayList<>();
	private static final List<Molten> POOLS = new ArrayList<>();
	private static final Map<LivingEntity, Burn> BURNING = new WeakHashMap<>();
	private static final Map<String, Map<UUID, Long>> READY = new HashMap<>();
	/** True while a skin or a cloak answers a blow, so two coated casters do not answer each other for ever. */
	private static boolean answering;

	private ElementSpells() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(ElementSpells::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SKINS.clear();
			RINGS.clear();
			PILLARS_UP.clear();
			LANCES.clear();
			CLOAKS.clear();
			SCORCHED.clear();
			SPIRES_DUE.clear();
			POOLS.clear();
			BURNING.clear();
			READY.clear();
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> struck(victim, source));
	}

	static boolean ours(String spell) {
		return IDS.contains(spell);
	}

	// ---- learning and the Magic screen -------------------------------------------------------------------------------------

	/** Whether a Sorcerer of {@code element} can keep {@code spell}: glass and the black flame are anyone's, the rest go by blood. */
	static boolean fits(String element, String spell) {
		if (FIRE.contains(spell)) {
			return "fire".equals(element);
		}
		if (spell.equals(STORM_CLOAK)) {
			return "lightning".equals(element);
		}
		return ours(spell);
	}

	/** The spells {@code player} has learned as a Sorcerer, in the order shown. */
	public static List<String> known(Player player) {
		if (!Classes.is(player, Classes.PlayerClass.SORCERER)) {
			return List.of();
		}
		List<String> learned = player.getAttachedOrElse(LEARNED, List.of());
		List<String> out = new ArrayList<>();
		for (String id : IDS) {
			if (learned.contains(id)) {
				out.add(id);
			}
		}
		return out;
	}

	/** A Sorcerer reading the scroll of {@code spell}: true if they learned it for good (the scroll is spent). */
	static boolean learn(ServerPlayer player, String spell) {
		if (!ours(spell) || !Classes.is(player, Classes.PlayerClass.SORCERER) || !(player.level() instanceof ServerLevel level)) {
			return false;
		}
		List<String> learned = player.getAttachedOrElse(LEARNED, List.of());
		if (learned.contains(spell) || !fits(Classes.optionOf(player, Classes.PlayerClass.SORCERER), spell)) {
			return false;
		}
		if (!Knowledge.has(player, Knowledge.HIGHER_ARTS)) {
			Knowledge.refuse(player, Knowledge.HIGHER_ARTS);
			return false;
		}
		List<String> all = new ArrayList<>(learned);
		all.add(spell);
		player.setAttached(LEARNED, List.copyOf(all));
		level.sendParticles(Fx.GLYPH, player.getX(), player.getY() + 1.2, player.getZ(), 40, 0.4, 0.5, 0.4, 0.8);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.4F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.spell.learned", Spellcraft.spellName(spell)).withStyle(ChatFormatting.LIGHT_PURPLE));
		return true;
	}

	/** A Sorcerer's pick of {@code spell} on the Magic screen: its wait, its mana, then the cast. */
	static void castKnown(ServerPlayer player, ServerLevel level, String spell) {
		if (!known(player).contains(spell)) {
			return;
		}
		Map<UUID, Long> ready = READY.computeIfAbsent(spell, s -> new HashMap<>());
		long now = level.getGameTime();
		if (ready.getOrDefault(player.getUUID(), 0L) > now) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.sorcery_picks.wait", Spellcraft.spellName(spell),
				(ready.get(player.getUUID()) - now + 19) / 20));
			return;
		}
		if (!Mana.afford(player, Mana.cost(spell)) || !SpellParts.allow(player, spell, null)) {
			return;
		}
		if (!Arcana.cast(spell, level, player, 1.0F)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.scroll_no_target"));
			return;
		}
		SpellParts.spend(player, spell);
		Mana.cast(player, spell);
		ready.put(player.getUUID(), now + NewSpells.COOLDOWN.getOrDefault(spell, 20 * 20));
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		Spellcraft.afterCast(player, level, spell);
	}

	// ---- the casts -----------------------------------------------------------------------------------------------------------

	static boolean cast(String spell, ServerLevel level, Player caster, float power) {
		long now = level.getGameTime();
		UUID id = caster.getUUID();
		switch (spell) {
			case "glass_shards" -> {
				return shards(level, caster, power);
			}
			case "glass_spires" -> {
				spires(level, caster);
				return true;
			}
			case "glass_skin" -> {
				int lasts = Math.round(SKIN_TICKS * Math.max(0.5F, power));
				SKINS.put(id, new Skin(now + lasts, SKIN_BLOWS));
				caster.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, lasts, 0, true, false, true));
				Concentration.hold(caster, spell, lasts, () -> shatterSkin(level, caster, false));
				Fx.orb(level, caster.position().add(0.0, caster.getBbHeight() * 0.5, 0.0), 1.1F, GLASS_COLOUR, 20);
				level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.6F);
				return true;
			}
			case "molten_glass" -> {
				Vec3 ground = NewSpells.aim(level, caster, MOLTEN_REACH);
				POOLS.add(new Molten(level, id, ground, now));
				Fx.beam(level, caster.getEyePosition().add(0.0, -0.3, 0.0), ground, 0.2F, MOLTEN_COLOUR, 6);
				Fx.pool(level, ground.add(0.0, 0.05, 0.0), (float) MOLTEN_RADIUS, MOLTEN_COLOUR, MOLTEN_HOT);
				level.playSound(null, ground.x, ground.y, ground.z, SoundEvents.BUCKET_EMPTY_LAVA, SoundSource.PLAYERS, 1.0F, 0.8F);
				return true;
			}
			case "fire_ring" -> {
				int lasts = Math.round(RING_TICKS * Math.max(0.5F, power));
				RINGS.put(id, now + lasts);
				Concentration.hold(caster, spell, lasts, () -> RINGS.remove(id));
				level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 1.0F, 0.7F);
				return true;
			}
			case "fire_pillars" -> {
				int lasts = Math.round(PILLARS_TICKS * Math.max(0.5F, power));
				PILLARS_UP.put(id, now + lasts);
				Concentration.hold(caster, spell, lasts, () -> PILLARS_UP.remove(id));
				level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 0.6F);
				return true;
			}
			case "fire_lance" -> {
				if (caster instanceof ServerPlayer sp && !PowersCompat.lanceStart(sp)) {
					// a lost clash still rests their streams
					return false;
				}
				LANCES.put(id, now + LANCE_TICKS);
				Concentration.hold(caster, spell, LANCE_TICKS, () -> endLance(caster));
				level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.2F);
				return true;
			}
			case BLACK_FLAME -> {
				LivingEntity target = aimed(level, caster, BLACK_REACH);
				if (target == null) {
					return false;
				}
				Fx.beam(level, caster.getEyePosition().add(0.0, -0.3, 0.0), target.getBoundingBox().getCenter(), 0.15F, BLACK_COLOUR, 8);
				light(level, target, id, Math.round(BLACK_TICKS * Math.max(0.5F, power)), true);
				level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.6F, 0.5F);
				return true;
			}
			case STORM_CLOAK -> {
				int lasts = Math.round(CLOAK_TICKS * Math.max(0.5F, power));
				CLOAKS.put(id, now + lasts);
				caster.addEffect(new MobEffectInstance(MobEffects.SPEED, lasts, 1, true, false, true));
				Concentration.hold(caster, spell, lasts, () -> {
					CLOAKS.remove(id);
					caster.removeEffect(MobEffects.SPEED);
				});
				Fx.ring(level, caster.position().add(0.0, 0.1, 0.0), 1.6F, STORM_COLOUR);
				level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.5F, 1.6F);
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	/** Whether {@code caster}'s spells may hurt {@code e}. */
	private static boolean hits(Player caster, LivingEntity e) {
		if (e == caster || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand || caster.isAlliedTo(e)) {
			return false;
		}
		if (e instanceof ServerPlayer other) {
			return caster instanceof ServerPlayer c && c.level().isPvpAllowed() && !other.isCreative() && !Parties.allied(c, other);
		}
		return true;
	}

	private static DamageSource harm(ServerLevel level, Player caster) {
		return level.damageSources().indirectMagic(caster, caster);
	}

	/** The body {@code caster} looks at within {@code reach}, with nothing solid between. */
	private static LivingEntity aimed(ServerLevel level, Player caster, double reach) {
		Vec3 eye = caster.getEyePosition(), look = caster.getLookAngle();
		Vec3 end = eye.add(look.scale(reach));
		HitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
		double far = wall.getType() == HitResult.Type.MISS ? reach : wall.getLocation().distanceTo(eye);
		LivingEntity best = null;
		double bestAt = far;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.5), e -> hits(caster, e))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double along = to.dot(look);
			if (along < 0.0 || along > bestAt) {
				continue;
			}
			if (to.subtract(look.scale(along)).length() <= e.getBbWidth() * 0.5 + 0.6) {
				best = e;
				bestAt = along;
			}
		}
		return best;
	}

	private static boolean shards(ServerLevel level, Player caster, float power) {
		Vec3 eye = caster.getEyePosition().add(0.0, -0.25, 0.0), look = caster.getLookAngle();
		Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
		side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
		for (int i = 0; i < SHARDS_DRAWN; i++) {
			double spread = (i - (SHARDS_DRAWN - 1) / 2.0) * 0.2;
			Vec3 way = look.add(side.scale(spread)).add(0.0, (level.getRandom().nextDouble() - 0.5) * 0.15, 0.0).normalize();
			Fx.beam(level, eye.add(way.scale(0.8)), eye.add(way.scale(SHARDS_REACH)), 0.06F, GLASS_COLOUR, 5);
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(SHARDS_REACH), e -> hits(caster, e))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double far = to.length();
			if (far > SHARDS_REACH || far > 0.5 && to.scale(1.0 / far).dot(look) < SHARDS_COS || !caster.hasLineOfSight(e)) {
				continue;
			}
			if (e.hurtServer(level, harm(level, caster), SHARDS_DAMAGE * power)) {
				PowersCompat.cut(e);
				Fx.burst(level, e.getX(), e.getY(0.6), e.getZ(), GLASS_COLOUR, 0.7F, 6, 0.2, 0.3, 0.2);
			}
		}
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, 1.4F);
		return true;
	}

	private static void spires(ServerLevel level, Player caster) {
		Vec3 look = caster.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		flat = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0.0F, caster.getYRot()) : flat.normalize();
		long now = level.getGameTime();
		for (int i = 1; i <= SPIRES; i++) {
			Vec3 at = caster.position().add(flat.scale(1.0 + i * SPIRE_GAP));
			SPIRES_DUE.add(new Spire(level, caster.getUUID(), at, now + (long) i * SPIRE_EVERY));
		}
		level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1.0F, 0.5F);
	}

	private static void rise(Spire s) {
		ServerLevel level = s.level;
		if (!(level.getPlayerByUUID(s.owner) instanceof Player caster)) {
			return;
		}
		// settle onto the ground a little above or below the caster's feet
		net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.containing(s.at.x, s.at.y + 1.5, s.at.z);
		for (int i = 0; i < 5 && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty(); i++) {
			pos = pos.below();
		}
		if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
			return;
		}
		Vec3 foot = new Vec3(s.at.x, pos.getY(), s.at.z);
		Fx.beam(level, foot, foot.add(0.0, 2.6, 0.0), 0.28F, GLASS_COLOUR, 30);
		Fx.ring(level, foot.add(0.0, 0.05, 0.0), 0.9F, GLASS_COLOUR);
		level.playSound(null, foot.x, foot.y, foot.z, SoundEvents.GLASS_PLACE, SoundSource.PLAYERS, 0.9F, 0.6F);
		AABB box = new AABB(foot, foot.add(0.0, 2.6, 0.0)).inflate(SPIRE_RADIUS, 0.3, SPIRE_RADIUS);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> hits(caster, e))) {
			if (e.hurtServer(level, harm(level, caster), SPIRE_DAMAGE)) {
				Vec3 v = e.getDeltaMovement();
				e.setDeltaMovement(v.x * 0.3, SPIRE_LIFT, v.z * 0.3);
				e.syncVelocity = true;
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SPIRE_SLOW_TICKS, 2), caster);
				PowersCompat.cut(e);
			}
		}
	}

	private static void shatterSkin(ServerLevel level, Player caster, boolean burst) {
		if (SKINS.remove(caster.getUUID()) == null) {
			return;
		}
		caster.removeEffect(MobEffects.RESISTANCE);
		Vec3 at = caster.position().add(0.0, caster.getBbHeight() * 0.5, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.2F, 0.7F);
		Fx.blast(level, at, burst ? (float) SKIN_BURST_RADIUS : 1.2F, GLASS_COLOUR);
		if (!burst) {
			return;
		}
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(SKIN_BURST_RADIUS), e -> hits(caster, e))) {
			if (e.hurtServer(level, harm(level, caster), SKIN_BURST)) {
				PowersCompat.cut(e);
			}
		}
	}

	/** A blow landed on {@code victim}: a glass skin cuts the hand that landed it, a storm cloak shocks it. */
	private static void struck(LivingEntity victim, DamageSource source) {
		if (answering || !(victim instanceof Player caster) || !(victim.level() instanceof ServerLevel level)
			|| !(source.getDirectEntity() instanceof LivingEntity attacker) || attacker == victim || source.is(DamageTypes.INDIRECT_MAGIC)
			|| source.is(DamageTypes.MAGIC) || source.is(DamageTypes.THORNS)) {
			return;
		}
		UUID id = caster.getUUID();
		Skin skin = SKINS.get(id);
		boolean cloak = CLOAKS.containsKey(id);
		if (skin == null && !cloak) {
			return;
		}
		answering = true;
		try {
			if (skin != null) {
				if (attacker.hurtServer(level, harm(level, caster), SKIN_CUT)) {
					PowersCompat.cut(attacker);
				}
				Fx.burst(level, attacker.getX(), attacker.getY(0.7), attacker.getZ(), GLASS_COLOUR, 0.6F, 5, 0.2, 0.2, 0.2);
				if (skin.blows() <= 1) {
					shatterSkin(level, caster, true);
					if ("glass_skin".equals(Concentration.holding(caster))) {
						Concentration.hold(caster, "glass_skin_done", 1, () -> {
						});
					}
				} else {
					SKINS.put(id, new Skin(skin.until(), skin.blows() - 1));
					level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.GLASS_HIT, SoundSource.PLAYERS, 1.0F, 1.0F);
				}
			}
			if (cloak) {
				attacker.hurtServer(level, harm(level, caster), CLOAK_SHOCK);
				arc(level, caster, attacker);
			}
		} finally {
			answering = false;
		}
	}

	private static void arc(ServerLevel level, Player caster, LivingEntity to) {
		Vec3 from = caster.position().add(0.0, caster.getBbHeight() * 0.6, 0.0), at = to.getBoundingBox().getCenter();
		Fx.beam(level, from, at, 0.07F, STORM_COLOUR, 4);
		level.sendParticles(Fx.spark(STORM_COLOUR, 0.8F), at.x, at.y, at.z, 6, 0.2, 0.3, 0.2, 0.0);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.25F, 1.9F);
	}

	// ---- the black flame -----------------------------------------------------------------------------------------------------

	private static void light(ServerLevel level, LivingEntity target, UUID by, int ticks, boolean leaps) {
		long now = level.getGameTime();
		BURNING.put(target, new Burn(now + ticks, by, leaps, now));
	}

	/** Whether the black flame is on {@code entity}. */
	public static boolean burning(LivingEntity entity) {
		return BURNING.containsKey(entity);
	}

	/** Healing magic or a dispel on {@code entity}: the black flame goes out. True if it was burning. */
	static boolean quench(LivingEntity entity) {
		if (BURNING.remove(entity) == null) {
			return false;
		}
		if (entity.level() instanceof ServerLevel level) {
			level.sendParticles(Fx.wisp(0xE8E8F0, 0.9F), entity.getX(), entity.getY(0.6), entity.getZ(), 14, 0.3, 0.4, 0.3, 0.02);
			level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.NEUTRAL, 0.8F, 0.6F);
		}
		return true;
	}

	private static void tickFlames(long tick) {
		if (BURNING.isEmpty()) {
			return;
		}
		for (Map.Entry<LivingEntity, Burn> entry : List.copyOf(BURNING.entrySet())) {
			LivingEntity e = entry.getKey();
			Burn b = entry.getValue();
			if (!(e.level() instanceof ServerLevel level) || !e.isAlive() || e.isRemoved() || level.getGameTime() >= b.until()) {
				BURNING.remove(e);
				continue;
			}
			long gone = level.getGameTime() - b.lit();
			if (gone % 4 == 0) {
				level.sendParticles(Fx.miasma(BLACK_COLOUR, 1.0F), e.getX(), e.getY(0.5), e.getZ(), 3, e.getBbWidth() * 0.3, e.getBbHeight() * 0.3, e.getBbWidth() * 0.3, 0.01);
				level.sendParticles(Fx.mote(0x7A3AB8, 0.6F), e.getX(), e.getY(0.8), e.getZ(), 1, e.getBbWidth() * 0.3, e.getBbHeight() * 0.3, e.getBbWidth() * 0.3, 0.02);
			}
			if (gone % 20 != 19) {
				continue;
			}
			Player by = level.getPlayerByUUID(b.by());
			e.hurtServer(level, by != null ? harm(level, by) : level.damageSources().magic(), BLACK_DAMAGE);
			if (b.leaps() && gone >= BLACK_LEAPS_AFTER) {
				// it leaps once, to the body nearest: friend or foe, the flame does not choose
				LivingEntity next = null;
				double near = BLACK_LEAP * BLACK_LEAP;
				for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(BLACK_LEAP),
					o -> o != e && o.isAlive() && !o.isSpectator() && !(o instanceof ArmorStand) && !BURNING.containsKey(o) && !o.getUUID().equals(b.by())
						&& !(o instanceof Player p && p.isCreative()))) {
					double d = o.distanceToSqr(e);
					if (d < near) {
						near = d;
						next = o;
					}
				}
				if (next != null) {
					BURNING.put(e, new Burn(b.until(), b.by(), false, b.lit()));
					light(level, next, b.by(), (int) Math.max(40L, (b.until() - level.getGameTime()) / 2L), false);
					Fx.beam(level, e.getBoundingBox().getCenter(), next.getBoundingBox().getCenter(), 0.1F, BLACK_COLOUR, 6);
				}
			}
		}
	}

	// ---- the held ones, each tick --------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		long tick = server.getTickCount();
		tickFlames(tick);
		if (!SPIRES_DUE.isEmpty()) {
			for (Iterator<Spire> it = SPIRES_DUE.iterator(); it.hasNext();) {
				Spire s = it.next();
				if (s.level.getGameTime() >= s.when) {
					it.remove();
					rise(s);
				}
			}
		}
		if (!POOLS.isEmpty()) {
			for (Iterator<Molten> it = POOLS.iterator(); it.hasNext();) {
				if (pool(it.next())) {
					it.remove();
				}
			}
		}
		held(server, SKINS.keySet(), null);
		held(server, RINGS.keySet(), ElementSpells::ring);
		held(server, PILLARS_UP.keySet(), ElementSpells::pillars);
		held(server, LANCES.keySet(), ElementSpells::lance);
		held(server, CLOAKS.keySet(), ElementSpells::cloak);
	}

	private interface Work {
		void on(ServerLevel level, ServerPlayer caster, long now);
	}

	/** Runs {@code work} for each caster still holding; those gone, dead or out of time drop it. */
	private static void held(MinecraftServer server, Set<UUID> holders, Work work) {
		if (holders.isEmpty()) {
			return;
		}
		for (UUID id : List.copyOf(holders)) {
			ServerPlayer caster = server.getPlayerList().getPlayer(id);
			if (caster == null || !caster.isAlive() || !(caster.level() instanceof ServerLevel level)) {
				drop(id, caster);
				continue;
			}
			long now = level.getGameTime();
			Long until = holders == SKINS.keySet() ? Long.valueOf(SKINS.get(id).until())
				: holders == RINGS.keySet() ? RINGS.get(id) : holders == PILLARS_UP.keySet() ? PILLARS_UP.get(id)
				: holders == LANCES.keySet() ? LANCES.get(id) : CLOAKS.get(id);
			if (until == null) {
				continue;
			}
			if (now >= until) {
				if (holders == SKINS.keySet()) {
					shatterSkin(level, caster, false);
				} else if (holders == LANCES.keySet()) {
					endLance(caster);
				} else {
					holders.remove(id);
				}
				continue;
			}
			if (work != null) {
				work.on(level, caster, now);
			}
		}
	}

	private static void drop(UUID id, ServerPlayer caster) {
		SKINS.remove(id);
		RINGS.remove(id);
		PILLARS_UP.remove(id);
		CLOAKS.remove(id);
		SCORCHED.remove(id);
		if (LANCES.remove(id) != null && caster != null) {
			PowersCompat.lanceStop(caster);
		}
	}

	/** One bite of fire for {@code e} from {@code caster}, no sooner than {@code again} ticks after the last. */
	private static boolean scorch(ServerLevel level, ServerPlayer caster, LivingEntity e, long now, int again, float damage) {
		Map<UUID, Long> last = SCORCHED.computeIfAbsent(caster.getUUID(), u -> new HashMap<>());
		if (now - last.getOrDefault(e.getUUID(), -1000L) < again) {
			return false;
		}
		last.put(e.getUUID(), now);
		if (last.size() > 64) {
			last.values().removeIf(t -> now - t > 200L);
		}
		e.igniteForSeconds(3.0F);
		return e.hurtServer(level, harm(level, caster), damage);
	}

	private static void ring(ServerLevel level, ServerPlayer caster, long now) {
		if (now % 5 != 0L) {
			return;
		}
		Vec3 at = caster.position().add(0.0, 0.1, 0.0);
		Fx.ring(level, at, (float) RING_RADIUS, FIRE_COLOUR);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(RING_RADIUS + RING_WIDTH, 1.5, RING_RADIUS + RING_WIDTH),
			e -> hits(caster, e))) {
			double dx = e.getX() - caster.getX(), dz = e.getZ() - caster.getZ();
			double far = Math.sqrt(dx * dx + dz * dz);
			if (Math.abs(far - RING_RADIUS) > RING_WIDTH + e.getBbWidth() * 0.5) {
				continue;
			}
			if (scorch(level, caster, e, now, 10, RING_DAMAGE) && far > 0.01) {
				e.setDeltaMovement(dx / far * RING_SHOVE, 0.25, dz / far * RING_SHOVE);
				e.syncVelocity = true;
			}
		}
	}

	private static void pillars(ServerLevel level, ServerPlayer caster, long now) {
		for (int i = 0; i < PILLARS; i++) {
			double a = now * PILLAR_TURN + i * (Math.PI * 2.0 / PILLARS);
			Vec3 foot = caster.position().add(Math.cos(a) * PILLAR_ORBIT, 0.0, Math.sin(a) * PILLAR_ORBIT);
			if (now % 2 == 0L) {
				Fx.beam(level, foot, foot.add(0.0, PILLAR_HEIGHT, 0.0), 0.35F, FIRE_COLOUR, 3);
			}
			AABB box = new AABB(foot, foot.add(0.0, PILLAR_HEIGHT, 0.0)).inflate(PILLAR_RADIUS, 0.2, PILLAR_RADIUS);
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, e -> hits(caster, e))) {
				scorch(level, caster, e, now, PILLAR_AGAIN, PILLAR_DAMAGE);
			}
		}
		if (now % 30 == 0L) {
			level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.8F);
		}
	}

	private static void endLance(Player caster) {
		if (LANCES.remove(caster.getUUID()) != null && caster instanceof ServerPlayer sp) {
			PowersCompat.lanceStop(sp);
		}
	}

	private static void lance(ServerLevel level, ServerPlayer caster, long now) {
		if (!"fire_lance".equals(Concentration.holding(caster)) || !PowersCompat.lanceActive(caster)) {
			// broken by a blow, or a lost clash put it out
			endLance(caster);
			return;
		}
		if (PowersCompat.lanceClashing(caster)) {
			// the knot between the two streams is drawn there; nothing burns past it
			return;
		}
		Vec3 eye = caster.getEyePosition().add(0.0, -0.25, 0.0), look = caster.getLookAngle();
		Vec3 end = eye.add(look.scale(LANCE_REACH));
		HitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, caster));
		if (wall.getType() != HitResult.Type.MISS) {
			end = wall.getLocation();
		}
		if (now % 2 == 0L) {
			Fx.beam(level, eye.add(look.scale(0.7)), end, 0.3F, FIRE_COLOUR, 3);
		}
		if (now % LANCE_EVERY != 0L) {
			return;
		}
		double far = end.distanceTo(eye);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, end).inflate(1.5), e -> hits(caster, e))) {
			Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
			double along = to.dot(look);
			if (along < 0.0 || along > far + 0.5 || to.subtract(look.scale(along)).length() > LANCE_WIDTH + e.getBbWidth() * 0.5) {
				continue;
			}
			scorch(level, caster, e, now, LANCE_EVERY, LANCE_DAMAGE);
		}
		if (now % 20 == 0L) {
			level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 1.2F, 1.3F);
		}
	}

	/** The class key while {@code player} holds a Fire Lance locked with another stream: a shove of the knot. True if it was one. */
	static boolean lanceKey(ServerPlayer player) {
		return LANCES.containsKey(player.getUUID()) && PowersCompat.lanceTap(player);
	}

	private static void cloak(ServerLevel level, ServerPlayer caster, long now) {
		if (now % 6 == 0L) {
			level.sendParticles(Fx.spark(STORM_COLOUR, 0.7F), caster.getX(), caster.getY(0.6), caster.getZ(), 2, 0.3, 0.5, 0.3, 0.0);
		}
		if (now % CLOAK_ARC_EVERY != 0L) {
			return;
		}
		LivingEntity near = null;
		double best = CLOAK_ARC_REACH * CLOAK_ARC_REACH;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(CLOAK_ARC_REACH), e -> SorceryPicks.foe(caster, e))) {
			double d = e.distanceToSqr(caster);
			if (d < best && caster.hasLineOfSight(e)) {
				best = d;
				near = e;
			}
		}
		if (near != null && near.hurtServer(level, harm(level, caster), CLOAK_ARC)) {
			arc(level, caster, near);
		}
	}

	/** One look at a pool of molten glass: true when it is done. */
	private static boolean pool(Molten m) {
		ServerLevel level = m.level;
		long gone = level.getGameTime() - m.made;
		if (gone % 5 != 0L) {
			return false;
		}
		Player caster = level.getPlayerByUUID(m.owner);
		DamageSource source = caster != null ? harm(level, caster) : level.damageSources().magic();
		AABB box = new AABB(m.at, m.at).inflate(MOLTEN_RADIUS, 1.5, MOLTEN_RADIUS);
		List<LivingEntity> inside = level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && !e.isSpectator() && !e.getUUID().equals(m.owner)
			&& (caster == null || hits(caster, e)) && e.position().subtract(m.at).horizontalDistanceSqr() <= MOLTEN_RADIUS * MOLTEN_RADIUS);
		if (gone < MOLTEN_HOT) {
			if (gone % 10 == 0L) {
				for (LivingEntity e : inside) {
					e.igniteForSeconds(2.0F);
					e.hurtServer(level, source, MOLTEN_BURN);
				}
			}
			return false;
		}
		if (gone == MOLTEN_HOT) {
			Fx.pool(level, m.at.add(0.0, 0.05, 0.0), (float) MOLTEN_RADIUS, GLASS_COLOUR, MOLTEN_COOL);
			level.playSound(null, m.at.x, m.at.y, m.at.z, SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 0.7F);
		}
		if (gone < MOLTEN_HOT + MOLTEN_COOL) {
			// set hard round their feet
			for (LivingEntity e : inside) {
				if (e.onGround()) {
					e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 12, 6, true, false, false));
				}
			}
			return false;
		}
		Fx.blast(level, m.at.add(0.0, 0.3, 0.0), (float) MOLTEN_RADIUS, GLASS_COLOUR);
		level.playSound(null, m.at.x, m.at.y, m.at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.3F, 0.6F);
		for (LivingEntity e : inside) {
			if (e.hurtServer(level, source, MOLTEN_SHATTER)) {
				PowersCompat.cut(e);
			}
		}
		return true;
	}
}
