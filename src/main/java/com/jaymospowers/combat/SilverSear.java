package com.jaymospowers.combat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.compat.KingdomsCompat;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.power.darktide.DarktideStatus;
import com.jaymospowers.tinkering.Tinkering;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Andy (2026-11-25): "silver blades should sear the undead along the slash, dealing extra damage, stunning and animating a
 * seared hot cut. same with werewolves and other monsters". The Witcher's two-sword idea only (steel for men, silver for
 * monsters); the names, numbers and look are ours.
 * <p>A silver blade (an item whose id says silver, or a tinker tool with a silver head) struck by hand into what the
 * Lantern Warden puts down (the tag {@code jaymospowers:silver_bane}: the dead, the moon-cursed, ghosts and the monsters of
 * legend; and every vampire but a dhampir, by Jaymo's Kingdoms) sears it: {@link #SEAR_DAMAGE} more on the blow, a short
 * stun ({@link #STUN_TICKS}, no oftener than {@link #STUN_EVERY} on one body), a hiss and smoke, and the cut it leaves
 * ({@code WoundMarks}) is seared: charred instead of bloody, glowing hot along the swing's line and cooling over
 * {@link #GLOW_TICKS}, and it doesn't drip (the heat closed it). Kingdoms' own silver weaknesses (a vampire's, a
 * werewolf's thick hide, the silver sword against the undead) stay as they were, on top.
 * <p>Hunter's Road: what silver burns also includes every monster of the kinds {@code corpse_eaters}, {@code dead}, {@code spirits}
 * and {@code cursed} (Jaymo's Kingdoms' {@code MonsterKinds}: the tags {@code jaymoskingdoms:kind/*}, and a vampire or a werewolf in
 * beast form), and a steel blade (iron, or a tinker tool with an iron or steel head) adds {@link #STEEL_DAMAGE} to a blow on
 * {@code people} and {@code beasts} ({@link #steelEdge}): the Witcher's two swords, steel for men and silver for monsters.
 */
public final class SilverSear {
	/** Added to a searing blow (health points), before armour. */
	public static final float SEAR_DAMAGE = 2.0F;
	/** Added to a steel blade's blow on people and beasts (health points), before armour: small, the silver's is the big one. */
	public static final float STEEL_DAMAGE = 1.0F;
	/** The monster kinds silver burns, and the kinds a steel blade bites better (Kingdoms' {@code MonsterKinds}). */
	private static final String[] SILVER_KINDS = {"corpse_eaters", "dead", "spirits", "cursed"}, STEEL_KINDS = {"people", "beasts"};
	/** The stun a sear gives (ticks): a mob's, and a player's (shorter, so a vampire player can still fight back). */
	public static final int STUN_TICKS = 16, PLAYER_STUN_TICKS = 8;
	/** One body is stunned by a sear no oftener than this (ticks), so a flurry doesn't hold it for good. */
	public static final int STUN_EVERY = 60;
	/** How long a seared cut glows (ticks): white-hot at first, then orange, then a dull red ember gone to char. */
	public static final int GLOW_TICKS = 160;

	/** What silver burns, by type. */
	public static final TagKey<EntityType<?>> BANE = TagKey.create(Registries.ENTITY_TYPE, JaymosPowers.id("silver_bane"));

	/** When each body may next be stunned by a sear (game time). */
	private static final Map<LivingEntity, Long> NEXT_STUN = new WeakHashMap<>();

	private SilverSear() {
	}

	public static void init() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (damageTaken <= 0.0F || !(entity.level() instanceof ServerLevel level) || !sears(entity, source)) {
				return;
			}
			double y = entity.getY(0.6);
			level.playSound(null, entity.getX(), y, entity.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.9F, 1.5F);
			level.playSound(null, entity.getX(), y, entity.getZ(), SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.5F, 1.8F);
			level.sendParticles(ParticleTypes.SMOKE, entity.getX(), y, entity.getZ(), 14, 0.2, 0.3, 0.2, 0.02);
			level.sendParticles(ParticleTypes.SMALL_FLAME, entity.getX(), y, entity.getZ(), 4, 0.15, 0.25, 0.15, 0.01);
			long now = level.getGameTime();
			synchronized (NEXT_STUN) {
				if (!entity.isAlive() || now < NEXT_STUN.getOrDefault(entity, 0L)) {
					return;
				}
				NEXT_STUN.put(entity, now + STUN_EVERY);
			}
			DarktideStatus.stun(entity, entity instanceof Player ? PLAYER_STUN_TICKS : STUN_TICKS);
		});
	}

	/** Whether a blow from {@code source} on {@code victim} is silver in a hand, into something silver burns. */
	public static boolean sears(LivingEntity victim, DamageSource source) {
		Entity by = source.getEntity();
		if (!(by instanceof LivingEntity attacker) || source.getDirectEntity() != attacker || attacker == victim) {
			return false;
		}
		ItemStack weapon = source.getWeaponItem();
		return silver(weapon != null ? weapon : attacker.getMainHandItem()) && bane(victim);
	}

	/** {@code damage} on {@code victim} from {@code source}, with the sear's added when it sears, or the steel edge's when it bites. */
	public static float dealt(LivingEntity victim, DamageSource source, float damage) {
		if (damage <= 0.0F) {
			return damage;
		}
		if (sears(victim, source)) {
			return damage + SEAR_DAMAGE;
		}
		return steelEdge(victim, source) ? damage + STEEL_DAMAGE : damage;
	}

	/** Whether a blow from {@code source} on {@code victim} is a steel blade in a hand, into a person or a beast silver doesn't burn. */
	public static boolean steelEdge(LivingEntity victim, DamageSource source) {
		Entity by = source.getEntity();
		if (!(by instanceof LivingEntity attacker) || source.getDirectEntity() != attacker || attacker == victim) {
			return false;
		}
		ItemStack weapon = source.getWeaponItem();
		return steel(weapon != null ? weapon : attacker.getMainHandItem()) && kind(victim, STEEL_KINDS) && !bane(victim);
	}

	/** Whether {@code stack} is a steel blade: a blade whose id says iron or steel, or a tinker tool whose head is iron or steel. */
	public static boolean steel(@Nullable ItemStack stack) {
		if (stack == null || stack.isEmpty() || !Dismember.blade(stack)) {
			return false;
		}
		if (Tinkering.TOOL_MATERIALS != null) {
			List<String> parts = stack.get(Tinkering.TOOL_MATERIALS);
			if (parts != null && !parts.isEmpty()) {
				return parts.getFirst().contains("iron") || parts.getFirst().contains("steel");
			}
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.contains("iron") || path.contains("steel");
	}

	/** Whether {@code body} is of any of {@code kinds} (Kingdoms' {@code MonsterKinds#is}; false without Kingdoms). */
	private static boolean kind(LivingEntity body, String[] kinds) {
		for (String kind : kinds) {
			if (Boolean.TRUE.equals(KingdomsCompat.call("MonsterKinds", "is", new Class<?>[] {Entity.class, String.class}, Boolean.FALSE, body, kind))) {
				return true;
			}
		}
		return false;
	}

	/** Whether {@code stack} is a silver blade: a blade whose id says silver, or a tinker tool whose head is silver. */
	public static boolean silver(@Nullable ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return false;
		}
		if (Tinkering.TOOL_MATERIALS != null) {
			List<String> parts = stack.get(Tinkering.TOOL_MATERIALS);
			if (parts != null && !parts.isEmpty()) {
				return parts.getFirst().contains("silver") && Dismember.blade(stack);
			}
		}
		// (a coin isn't a blade, so realm silver and hacksilver never count; nor do ingots and nuggets)
		return Dismember.blade(stack) && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains("silver");
	}

	/** Whether silver burns {@code body}: the {@link #BANE} tag, any of the silver kinds, or a vampire of Jaymo's Kingdoms that isn't a dhampir. */
	public static boolean bane(LivingEntity body) {
		if (body.is(BANE) || kind(body, SILVER_KINDS)) {
			return true;
		}
		return Boolean.TRUE.equals(KingdomsCompat.call("Vampires", "isVampire", new Class<?>[] {Entity.class}, Boolean.FALSE, body))
			&& !Boolean.TRUE.equals(KingdomsCompat.call("VampirePlayers", "dhampir", new Class<?>[] {Entity.class}, Boolean.FALSE, body));
	}
}
