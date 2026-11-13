package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import org.jspecify.annotations.Nullable;

/**
 * What a player knows of each kind of foe, in stars (0 to {@link #MAX_STARS}). Stars come from fighting: a kind you have
 * killed {@link #KILLS_FOR_STAR} times, {@link #KILLS_FOR_STARS_2}, {@link #KILLS_FOR_STARS_3} earns one, two and three
 * ({@link #fought}). They can also be bought in a sage's lesson or a library's study ({@link Research}'s creature-fears
 * topic), which also teaches what the kind fears. A foe you know is easier to face: each star makes your blows land
 * {@link #DEALT_PER_STAR} harder and its blows on you {@link #TAKEN_PER_STAR} lighter ({@link #dealt}), and a fear you
 * have learned can be used against it: fire turns the dead and spiders, snow a blaze ({@link #fears}).
 */
public final class FoeStudy {
	public static final int MAX_STARS = 3;
	/** Kills of one kind for one, two and three stars. */
	static final int KILLS_FOR_STAR = 3, KILLS_FOR_STARS_2 = 12, KILLS_FOR_STARS_3 = 30;
	/** Per star: more damage dealt to the kind, less taken from it. */
	static final float DEALT_PER_STAR = 0.04F, TAKEN_PER_STAR = 0.03F;
	/** A known fear used on a foe scares it with this chance, plus this much per star, and this long (ticks). */
	static final float SCARE_CHANCE = 0.3F, SCARE_PER_STAR = 0.15F;
	static final int SCARE_TICKS = 120;

	/** Kinds you have fought → kills of them (0: fought, not yet killed). Saved, kept through death, synced to its player. */
	public static final AttachmentType<Map<String, Integer>> FOUGHT = AttachmentRegistry.create(JaymosKingdoms.id("study_fought"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath()
			.syncWith(ByteBufCodecs.<io.netty.buffer.ByteBuf, String, Integer, Map<String, Integer>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), AttachmentSyncPredicate.targetOnly()));
	/** Kinds → stars taught by research. */
	public static final AttachmentType<Map<String, Integer>> TAUGHT = AttachmentRegistry.create(JaymosKingdoms.id("study_taught"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath()
			.syncWith(ByteBufCodecs.<io.netty.buffer.ByteBuf, String, Integer, Map<String, Integer>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT), AttachmentSyncPredicate.targetOnly()));
	/** Kinds whose fear you have learned. */
	public static final AttachmentType<List<String>> LEARNED = AttachmentRegistry.create(JaymosKingdoms.id("study_fears"),
		builder -> builder.persistent(Codec.STRING.listOf()).copyOnDeath()
			.syncWith(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), AttachmentSyncPredicate.targetOnly()));

	/** What a kind fears: {@code key} (lang {@code foe.fear.<key>}) and what sets it off ({@link Trigger}). */
	public record Fears(String key, Trigger trigger) {
	}

	/** How a known fear is used: by fire, by a snowball, or not at all (it is only known). */
	public enum Trigger {
		FIRE, SNOW, NONE
	}

	private static final Map<String, Fears> FEARS = new HashMap<>();

	static {
		for (String dead : List.of("zombie", "husk", "drowned", "skeleton", "stray", "bogged", "wither_skeleton", "zombie_villager", "zombified_piglin")) {
			FEARS.put(dead, new Fears("fire", Trigger.FIRE));
		}
		FEARS.put("spider", new Fears("fire", Trigger.FIRE));
		FEARS.put("cave_spider", new Fears("fire", Trigger.FIRE));
		FEARS.put("phantom", new Fears("daylight", Trigger.FIRE));
		FEARS.put("blaze", new Fears("snow", Trigger.SNOW));
		FEARS.put("creeper", new Fears("cats", Trigger.NONE));
		FEARS.put("enderman", new Fears("water", Trigger.NONE));
		FEARS.put("hoglin", new Fears("fungus", Trigger.NONE));
		FEARS.put("ghast", new Fears("fireball", Trigger.NONE));
	}

	private FoeStudy() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register((victim, source) -> {
			if (source.getEntity() instanceof ServerPlayer player && victim instanceof Mob) {
				fought(player, victim, 1);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (taken <= 0.0F) {
				return;
			}
			if (source.getEntity() instanceof ServerPlayer player && victim instanceof Mob) {
				fought(player, victim, 0);
			} else if (victim instanceof ServerPlayer player && source.getEntity() instanceof Mob foe) {
				fought(player, foe, 0);
			}
		});
	}

	/** The id of {@code e}'s kind without its namespace ("zombie"). */
	public static String kind(Entity e) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
	}

	/** {@code player} fought {@code foe}, and killed {@code kills} of them (0 or 1). */
	private static void fought(ServerPlayer player, Entity foe, int kills) {
		String kind = kind(foe);
		Map<String, Integer> now = player.getAttachedOrElse(FOUGHT, Map.of());
		if (kills == 0 && now.containsKey(kind)) {
			return;
		}
		Map<String, Integer> next = new HashMap<>(now);
		next.merge(kind, kills, Integer::sum);
		player.setAttached(FOUGHT, next);
	}

	/** Whether {@code player} has fought this kind. */
	public static boolean met(ServerPlayer player, String kind) {
		return player.getAttachedOrElse(FOUGHT, Map.of()).containsKey(kind);
	}

	/** The stars {@code kills} kills earn. */
	static int fought(int kills) {
		return kills >= KILLS_FOR_STARS_3 ? 3 : kills >= KILLS_FOR_STARS_2 ? 2 : kills >= KILLS_FOR_STAR ? 1 : 0;
	}

	/** {@code player}'s stars of {@code kind}: fought and taught, at most {@link #MAX_STARS}. */
	public static int stars(net.minecraft.world.entity.player.Player player, String kind) {
		int fighting = fought(player.getAttachedOrElse(FOUGHT, Map.<String, Integer>of()).getOrDefault(kind, 0));
		return Math.min(MAX_STARS, fighting + player.getAttachedOrElse(TAUGHT, Map.<String, Integer>of()).getOrDefault(kind, 0));
	}

	/** What {@code kind} fears, or null if it has no known dread. */
	public static @Nullable Fears fears(String kind) {
		return FEARS.get(kind);
	}

	public static boolean learned(net.minecraft.world.entity.player.Player player, String kind) {
		return player.getAttachedOrElse(LEARNED, List.<String>of()).contains(kind);
	}

	/** A lesson in {@code kind}: a star more (to the most) and what it fears. Returns false if there was nothing to learn. */
	public static boolean teach(ServerPlayer player, String kind) {
		boolean fear = FEARS.containsKey(kind) && !learned(player, kind);
		boolean star = stars(player, kind) < MAX_STARS;
		if (!fear && !star) {
			return false;
		}
		if (star) {
			Map<String, Integer> taught = new HashMap<>(player.getAttachedOrElse(TAUGHT, Map.of()));
			taught.merge(kind, 1, Integer::sum);
			player.setAttached(TAUGHT, taught);
		}
		if (fear) {
			List<String> next = new java.util.ArrayList<>(player.getAttachedOrElse(LEARNED, List.of()));
			next.add(kind);
			player.setAttached(LEARNED, List.copyOf(next));
		}
		return true;
	}

	/** (From the hurt mixin) a known foe: the player's blows land harder and its blows on them lighter; a known fear is used on it. */
	public static float dealt(LivingEntity target, DamageSource source, float amount) {
		if (source.getEntity() instanceof ServerPlayer player && target instanceof Mob foe && !(target instanceof net.minecraft.world.entity.player.Player)) {
			String kind = kind(foe);
			int stars = stars(player, kind);
			Fears fears = FEARS.get(kind);
			if (fears != null && learned(player, kind) && uses(fears.trigger(), source) && foe.getRandom().nextFloat() < SCARE_CHANCE + SCARE_PER_STAR * stars) {
				Fear.frighten(foe, player, SCARE_TICKS);
			}
			return amount * (1.0F + DEALT_PER_STAR * stars);
		}
		if (target instanceof ServerPlayer player && source.getEntity() instanceof Mob foe) {
			return amount * (1.0F - TAKEN_PER_STAR * stars(player, kind(foe)));
		}
		return amount;
	}

	private static boolean uses(Trigger trigger, DamageSource source) {
		return switch (trigger) {
			case FIRE -> source.is(DamageTypeTags.IS_FIRE) || source.getDirectEntity() != null && source.getDirectEntity().isOnFire();
			case SNOW -> source.getDirectEntity() instanceof Snowball;
			case NONE -> false;
		};
	}

	/** The kinds {@code player} has fought and that can be studied (they have a dread, or stars to gain), in name order. */
	public static List<String> studyable(ServerPlayer player) {
		return player.getAttachedOrElse(FOUGHT, Map.<String, Integer>of()).keySet().stream()
			.filter(k -> (FEARS.containsKey(k) && !learned(player, k)) || stars(player, k) < MAX_STARS).sorted().toList();
	}

	/** All the kinds with a known dread (for the sage's list of what could be asked). */
	static Set<String> dreaded() {
		return FEARS.keySet();
	}
}
