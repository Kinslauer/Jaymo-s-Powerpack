package com.jaymospowers.alchemy.hunter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.brewing.BrewGrade;
import com.jaymospowers.alchemy.brewing.BrewGrades;
import com.jaymospowers.alchemy.toxic.Harms;
import com.jaymospowers.alchemy.toxic.Toxicity;
import com.jaymospowers.core.wounds.Bleeding;
import com.jaymospowers.core.wounds.BodyPart;
import com.jaymospowers.core.wounds.BodyPlan;
import com.jaymospowers.core.wounds.Wounds;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Consumables;

/**
 * The hunter's brews at work (Hunter's Road, patch 6, "D2" draughts and "D3" monster brews). A drunk brew is an entry on its
 * drinker in {@link #ACTIVE} (when it ends, and its grade 1 to 3); the entry is the only state. Every draught adds load to the
 * body through {@link Toxicity}; a monster brew adds a heavy {@link Toxicity#dose held load} for the same ten minutes it
 * works, which earns hunter's eyes. A draught's grade comes from its brew grade ({@link #tier}): Crude and Plain are the first,
 * Fine the second, Expert and Masterwork the third.
 * <ul>
 * <li>Slow mending: a slow heal that costs hunger.</li>
 * <li>Grey sight: in the dark, sees in it and shows the living things near (the client's outline, {@code DarkSightClient}).</li>
 * <li>Savagery: critical blows hit harder. Keen edge: swords hit harder.</li>
 * <li>Deep breath: air that does not run out and strength, underwater.</li>
 * <li>Thorned blood: the dead who strike you are hurt, and at the top grade bleed. Thin venom: fewer poison ticks.</li>
 * <li>Plenty: feeds at once. The cleanser: takes the harms and the ordinary load off, and not the held load of a monster
 * brew (nothing draws that out until the brew is spent), then adds a small load of its own.</li>
 * <li>Monster brews, ten minutes each: marsh fury (more harm at full health), grave hunger (healing for each kill in a row),
 * pack marrow (fed while untouched), fog shroud (half harm in rain), imp gallop (a faster mount), troll blood (a troll's healing).</li>
 * </ul>
 */
public final class Draughts {
	// ---- tuning (per grade 1 to 3 where an array) ----
	/** Ticks a draught works (2, 3 and 5 minutes); a monster brew works ten minutes. */
	public static final int[] DURATION = {2400, 3600, 6000};
	public static final int MONSTER_TICKS = 20 * 60 * 10;
	/** The load a draught adds (as a brew graded Fine, Expert, Masterwork does); a monster brew's held load; the cleanser's own after it clears. */
	public static final int[] STRENGTH = {90, 150, 240};
	public static final int MONSTER_STRENGTH = 380;
	public static final int[] CLEANSER_AFTER = {60, 40, 20};
	/** Slow mending: health a second, and the exhaustion each health point costs. */
	public static final float[] SLOW_HEAL = {0.4F, 0.6F, 0.9F};
	public static final float SLOW_COST = 1.2F;
	/** Grey sight: how far the living show (blocks), and the light at or below which it is dark. */
	public static final int[] SIGHT_RANGE = {12, 20, 32};
	public static final int DARK_LIGHT = 7;
	/** Savagery and keen edge: the share a blow gains. */
	public static final float[] CRIT_MORE = {0.25F, 0.4F, 0.6F};
	public static final float[] EDGE_MORE = {0.15F, 0.25F, 0.4F};
	/** Thorned blood: the hurt a dead attacker takes; the top grade also opens a cut. */
	public static final float[] THORNS = {2.0F, 3.0F, 4.0F};
	/** Thin venom: ticks a second taken off a poison, besides the second that passes. */
	public static final int[] THIN_CUT = {20, 40, 60};
	/** Plenty: food and saturation given. */
	public static final int[] PLENTY_FOOD = {6, 10, 14};
	public static final float[] PLENTY_SAT = {6.0F, 8.0F, 10.0F};
	/** The cleanser: ordinary load taken (the top grade takes it all) and, at the top, every bad effect. */
	public static final int[] CLEANSE_LOAD = {300, 600, Toxicity.CAPACITY};
	/** Marsh fury: the share more harm dealt at full health. */
	public static final float MARSH_MORE = 0.4F;
	/** Grave hunger: ticks of quiet that end a run of kills, health a kill, and the most a kill heals. */
	public static final int GRAVE_GAP = 600;
	public static final float GRAVE_HEAL = 1.0F, GRAVE_MAX = 6.0F;
	/** Pack marrow: seconds between a bite of food, and ticks since the last hurt that count as untouched. */
	public static final int MARROW_EVERY = 4, MARROW_QUIET = 200;
	/** Fog shroud: the share of harm taken in rain. */
	public static final float FOG_TAKEN = 0.5F;
	/** Imp gallop: the mount's speed level (0 is the first). */
	public static final int GALLOP_LEVEL = 1;
	/** Troll blood: health a second, and the share of each part healed a second (1 over this). */
	public static final float TROLL_HEAL = 0.5F;
	public static final int TROLL_PART_SECONDS = 120;

	/** One brew at work: the game time it ends and its grade. */
	public record Active(long until, int tier) {
		public static final Codec<Active> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("until").forGetter(Active::until),
			Codec.INT.fieldOf("tier").forGetter(Active::tier)).apply(i, Active::new));
		public static final StreamCodec<ByteBuf, Active> STREAM = StreamCodec.composite(
			ByteBufCodecs.VAR_LONG, Active::until, ByteBufCodecs.VAR_INT, Active::tier, Active::new);
	}

	/** The brews working on a body, by {@link Draught#id}. Saved, synced to its owner (grey sight is drawn by their client), not kept through death. */
	public static final AttachmentType<Map<String, Active>> ACTIVE = AttachmentRegistry.create(JaymosPowers.id("hunter_brews"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Active.CODEC)).initializer(Map::of)
			.syncWith(ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, Active.STREAM).map(m -> (Map<String, Active>) m, m -> new HashMap<>(m)),
				AttachmentSyncPredicate.targetOnly()));

	/** Each killer's run of kills: {streak, game time of the last}. */
	private static final Map<ServerPlayer, long[]> STREAKS = new WeakHashMap<>();

	private Draughts() {
	}

	public static void init() {
		for (Draught d : Draught.values()) {
			d.item = Alchemy.item(d.itemId(), p -> new DraughtItem(p.stacksTo(16).component(DataComponents.CONSUMABLE, Consumables.DEFAULT_DRINK), d));
		}
		ServerTickEvents.END_SERVER_TICK.register(Draughts::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((dead, source) -> {
			if (source.getEntity() instanceof ServerPlayer killer && has(killer, Draught.GRAVE_HUNGER) && dead != killer) {
				graveKill(killer);
			}
		});
	}

	// ---- reading ------------------------------------------------------------------------------------------------------

	/** The grade (1 to 3) of a brew stack: Crude and Plain 1, Fine 2, Expert and Masterwork 3. */
	public static int tier(ItemStack stack) {
		BrewGrade grade = BrewGrades.orPlain(stack);
		return grade.atLeast(BrewGrade.EXPERT) ? 3 : grade == BrewGrade.FINE ? 2 : 1;
	}

	public static Map<String, Active> active(Player player) {
		Map<String, Active> map = player.getAttached(ACTIVE);
		return map == null ? Map.of() : map;
	}

	/** The grade of {@code draught} working on {@code player} (1 to 3), 0 if it is not (both sides). */
	public static int level(Player player, Draught draught) {
		Active a = active(player).get(draught.id);
		return a != null && player.level().getGameTime() < a.until() ? a.tier() : 0;
	}

	public static boolean has(Player player, Draught draught) {
		return level(player, draught) > 0;
	}

	// ---- drinking -----------------------------------------------------------------------------------------------------

	/**
	 * {@code player} drinks {@code draught} of grade {@code tier}; {@code mult} stretches how long it works (the hunter's flask
	 * doubles it). {@code drunk} is the stack as it was, for the lean ({@link Leans#taken}).
	 */
	public static void drink(ServerPlayer player, Draught draught, int tier, double mult, ItemStack drunk) {
		int t = Math.max(1, Math.min(3, tier));
		long now = player.level().getGameTime();
		int taken;
		if (draught == Draught.CLEANSER) {
			cleanse(player, t);
			taken = Toxicity.dose(player, CLEANSER_AFTER[t - 1], 0);
		} else if (draught.monster()) {
			taken = Toxicity.dose(player, MONSTER_STRENGTH, MONSTER_TICKS);
		} else {
			taken = Toxicity.dose(player, STRENGTH[t - 1], 0);
		}
		if (draught == Draught.PLENTY) {
			player.getFoodData().eat(PLENTY_FOOD[t - 1], PLENTY_SAT[t - 1] / Math.max(1, PLENTY_FOOD[t - 1]) / 2.0F);
			player.getFoodData().setSaturation(Math.min(player.getFoodData().getFoodLevel(),
				player.getFoodData().getSaturationLevel() + PLENTY_SAT[t - 1]));
		} else if (draught != Draught.CLEANSER) {
			long length = draught.monster() ? MONSTER_TICKS : Math.round(DURATION[t - 1] * mult);
			Map<String, Active> next = new HashMap<>(active(player));
			next.put(draught.id, new Active(now + length, t));
			player.setAttached(ACTIVE, Map.copyOf(next));
		}
		Leans.taken(player, drunk, taken);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WITCH_DRINK, SoundSource.PLAYERS, 0.7F, 0.8F);
	}

	/** The cleanser: every harm off, the ordinary load down (not the held load of a monster brew), the top grade every bad effect. */
	private static void cleanse(ServerPlayer player, int tier) {
		Harms.clear(player);
		if (tier >= 3) {
			Toxicity.clear(player, false);
			for (MobEffectInstance effect : java.util.List.copyOf(player.getActiveEffects())) {
				if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
					player.removeEffect(effect.getEffect());
				}
			}
		} else {
			Toxicity.detox(player, CLEANSE_LOAD[tier - 1]);
		}
	}

	// ---- in a fight ---------------------------------------------------------------------------------------------------

	/** What a blow of {@code amount} on {@code victim} becomes (from the same mixin as the skins and harms). */
	public static float incoming(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0.0F) {
			return amount;
		}
		if (victim instanceof ServerPlayer hit && !active(hit).isEmpty()) {
			if (has(hit, Draught.FOG_SHROUD) && hit.level().isRainingAt(hit.blockPosition())) {
				amount *= FOG_TAKEN;
			}
			int thorns = level(hit, Draught.THORNED_BLOOD);
			if (thorns > 0) {
				thorned(hit, source, thorns);
			}
		}
		if (source.getEntity() instanceof ServerPlayer by && !active(by).isEmpty()) {
			boolean melee = source.is(DamageTypes.PLAYER_ATTACK) && source.getDirectEntity() == by;
			if (has(by, Draught.MARSH_FURY) && by.getHealth() >= by.getMaxHealth() - 0.01F) {
				amount *= 1.0F + MARSH_MORE;
			}
			if (melee && by.getMainHandItem().is(ItemTags.SWORDS)) {
				int edge = level(by, Draught.KEEN_EDGE);
				if (edge > 0) {
					amount *= 1.0F + EDGE_MORE[edge - 1];
				}
			}
			int crit = level(by, Draught.SAVAGERY);
			if (melee && crit > 0 && crits(by)) {
				amount *= 1.0F + CRIT_MORE[crit - 1];
			}
		}
		return amount;
	}

	/** Whether {@code by}'s swing is a critical blow by vanilla's own rule (falling, off the ground, not in water, not sprinting). */
	private static boolean crits(Player by) {
		return by.fallDistance > 0.0F && !by.onGround() && !by.onClimbable() && !by.isInWater() && !by.hasEffect(MobEffects.BLINDNESS)
			&& !by.isPassenger() && !by.isSprinting();
	}

	/** A dead thing that strikes a body with thorned blood is hurt; at the top grade it bleeds. */
	private static void thorned(ServerPlayer hit, DamageSource source, int tier) {
		Entity direct = source.getDirectEntity();
		if (!(direct instanceof LivingEntity attacker) || !isDead(attacker) || !(hit.level() instanceof ServerLevel level) || attacker == hit) {
			return;
		}
		attacker.hurtServer(level, level.damageSources().thorns(hit), THORNS[tier - 1]);
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, attacker.getX(), attacker.getY() + attacker.getBbHeight() * 0.5, attacker.getZ(), 4, 0.2, 0.2, 0.2, 0.1);
		if (tier >= 3) {
			Bleeding.open(attacker, BodyPart.BODY, false);
		}
	}

	/** Whether {@code who} is one of the dead: vanilla's undead, or what Kingdoms' dead kind lists. */
	private static boolean isDead(LivingEntity who) {
		return who.is(EntityTypeTags.UNDEAD) || who.is(KIND_DEAD);
	}

	private static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> KIND_DEAD = net.minecraft.tags.TagKey.create(
		net.minecraft.core.registries.Registries.ENTITY_TYPE, net.minecraft.resources.Identifier.fromNamespaceAndPath("jaymoskingdoms", "kind/dead"));

	/** A kill by a body with grave hunger: a run of kills heals more each time. */
	private static void graveKill(ServerPlayer killer) {
		long now = killer.level().getGameTime();
		long[] run = STREAKS.computeIfAbsent(killer, k -> new long[2]);
		run[0] = now - run[1] > GRAVE_GAP ? 1 : run[0] + 1;
		run[1] = now;
		killer.heal(Math.min(GRAVE_MAX, GRAVE_HEAL * run[0]));
		if (killer.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.HEART, killer.getX(), killer.getY() + 1.6, killer.getZ(), (int) Math.min(5, run[0]), 0.3, 0.2, 0.3, 0.0);
		}
	}

	// ---- the passing of time ------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Map<String, Active> map = active(player);
			if (map.isEmpty() || !player.isAlive()) {
				continue;
			}
			long now = player.level().getGameTime();
			Map<String, Active> next = new HashMap<>(map);
			boolean over = next.values().removeIf(a -> now >= a.until());
			if (over) {
				if (next.isEmpty()) {
					player.removeAttached(ACTIVE);
				} else {
					player.setAttached(ACTIVE, Map.copyOf(next));
				}
			}
			for (Map.Entry<String, Active> en : next.entrySet()) {
				Draught d = Draught.byId(en.getKey());
				if (d != null) {
					second(player, d, en.getValue().tier(), (int) (server.getTickCount() / 20));
				}
			}
		}
	}

	/** One second of one brew's work. */
	private static void second(ServerPlayer player, Draught draught, int tier, int second) {
		switch (draught) {
			case SLOW_MENDING -> {
				if (player.getHealth() < player.getMaxHealth() && player.getFoodData().getFoodLevel() > 0) {
					float heal = SLOW_HEAL[tier - 1];
					player.heal(heal);
					player.getFoodData().addExhaustion(heal * SLOW_COST);
				}
			}
			case GREY_SIGHT -> {
				if (player.level().getMaxLocalRawBrightness(player.blockPosition()) <= DARK_LIGHT) {
					player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, false));
				}
			}
			case DEEP_BREATH -> {
				if (player.isUnderWater()) {
					player.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, true, false, false));
					player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 60, tier - 1, true, false, false));
				}
			}
			case THIN_VENOM -> {
				MobEffectInstance poison = player.getEffect(MobEffects.POISON);
				if (poison != null) {
					int left = poison.getDuration() - THIN_CUT[tier - 1];
					player.removeEffect(MobEffects.POISON);
					if (left > 0) {
						player.addEffect(new MobEffectInstance(MobEffects.POISON, left, poison.getAmplifier(), poison.isAmbient(), poison.isVisible(),
							poison.showIcon()));
					}
				}
			}
			case PACK_MARROW -> {
				if (second % MARROW_EVERY == 0 && player.tickCount - player.getLastHurtByMobTimestamp() > MARROW_QUIET
					&& player.getFoodData().needsFood()) {
					player.getFoodData().eat(1, 0.5F);
				}
			}
			case IMP_GALLOP -> {
				if (player.getVehicle() instanceof LivingEntity mount) {
					mount.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, GALLOP_LEVEL, true, false, false));
				}
			}
			case TROLL_BLOOD -> {
				player.heal(TROLL_HEAL);
				BodyPlan plan = Wounds.plan(player);
				if (plan != null) {
					for (int i = 0; i < plan.size(); i++) {
						Wounds.healPart(player, plan.part(i), Wounds.cap(player, plan, i) / TROLL_PART_SECONDS);
					}
				}
			}
			default -> {
			}
		}
	}
}
