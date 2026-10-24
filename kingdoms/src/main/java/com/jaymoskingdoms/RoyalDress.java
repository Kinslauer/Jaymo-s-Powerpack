package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.item.equipment.trim.TrimMaterials;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.181) Andy (2026-10-23): "Give kings and queens the ability to wear armor or not as they please/ change activities
 * throughout the day (they change before bed, etc)".
 * <p>
 * A realm's ruler (a king or queen, a lord or lady; folk or MCA villager) chooses what to wear, and is seen to change:
 * <ul>
 * <li><b>Armour</b> ({@link Outfit#PLATE}): when there is danger (a foe or a hostile stranger near, a fresh wound, a
 * war, a war party coming), and, as their temper has it, to hold court or review the guards ({@link #ARMOURED_HOURS}).
 * A warlord keeps it on all day; a paranoid ruler puts it on at the slightest threat (a stranger, from far off); a
 * reveler only for real danger, never at his feast.</li>
 * <li><b>Finery</b> ({@link Outfit#ROBES}): the crown and royal attire ({@link Regalia}, or MCA's royal clothes), at leisure,
 * at table and in the evening.</li>
 * <li><b>Nightwear</b> ({@link Outfit#NIGHT}): crown and armour off before bed ({@link #BEDTIME}), on again at dawn.</li>
 * </ul>
 * Nothing is deleted or copied: the set not being worn is kept on the ruler ({@link #WEAR}), generated the first time it's
 * wanted, and dropped (if it isn't regalia) when they fall. A change takes a moment, with the sound of armour and a
 * word, the ruler hardly able to move ({@link #DRESS_TICKS}); in danger it's hurried. The day around it (morning court,
 * the round and the guard review, the meal, leisure and the evening) is {@link Rulers}, {@link Royals} and
 * {@link Routines}; {@link #activity} names the hour.
 */
final class RoyalDress {
	enum Outfit {
		ROBES, PLATE, NIGHT;

		String key() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** What a ruler is about by the hour (for the dressing). */
	enum Activity {
		COURT, ROUND, AUDIENCE, MEAL, LEISURE, EVENING, BED
	}

	/** Time of day: the ruler changes for bed from here to {@link Royals#DAWN}; the morning court ends; a meal is taken. */
	static final long BEDTIME = 11200L, MORNING_COURT_TO = 2500L, MEAL_FROM = Routines.LUNCH_FROM, MEAL_TO = Routines.LUNCH_TO;
	/**
	 * When each temper arms for the day (time of day, from and to; none: only for danger or war): a warlord all day, a
	 * paranoid ruler through his round and court, a tyrant and a purist to review the guards on his round.
	 */
	static final Map<Tempers.Temper, long[]> ARMOURED_HOURS = Map.of(
		Tempers.Temper.WARLORD, new long[] {Royals.DAWN, Rulers.GUARD_EVENING},
		Tempers.Temper.PARANOID, new long[] {Royals.DAWN, Rulers.COURT_TO},
		Tempers.Temper.TYRANT, new long[] {MORNING_COURT_TO, Rulers.ROUND_TO},
		Tempers.Temper.PURIST, new long[] {MORNING_COURT_TO, Rulers.ROUND_TO});
	/** How far a ruler sees danger (blocks); a paranoid one. */
	static final double DANGER_RANGE = 20.0, PARANOID_RANGE = 40.0;
	/** A stranger is anyone with less standing than this with the realm (a paranoid ruler arms for one). */
	static final int STRANGER_REP = Routines.STRANGER_REP;
	/** A war, a wound or a foe keeps the armour on this long after it's gone (ticks). */
	static final long DANGER_LINGER = 1200L;
	/** The least time between changes for no danger (ticks); and between hurried ones, in danger. */
	static final long SWAP_EVERY = 600L, SWAP_HURRIED = 40L;
	/** How long a calm change takes, and a hurried one (ticks, the ruler slowed to a stand). */
	static final int DRESS_TICKS = 50, HURRIED_TICKS = 20;
	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

	/** What a ruler wears now and the sets put by, each head, chest, legs, feet. Saved on the ruler. */
	record Wear(String worn, Map<String, List<ItemStack>> kept) {
		static final Codec<Wear> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.optionalFieldOf("worn", "robes").forGetter(Wear::worn),
				Codec.unboundedMap(Codec.STRING, ItemStack.OPTIONAL_CODEC.listOf()).optionalFieldOf("kept", Map.of()).forGetter(Wear::kept))
			.apply(i, Wear::new));
	}

	static final AttachmentType<Wear> WEAR = AttachmentRegistry.create(JaymosKingdoms.id("royal_wear"),
		builder -> builder.persistent(Wear.CODEC));

	/** Ruler → game time of the last danger seen, and of the last change. */
	private static final Map<UUID, Long> DANGER = new HashMap<>(), CHANGED = new HashMap<>();

	private RoyalDress() {
	}

	static void register() {
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
			DANGER.clear();
			CHANGED.clear();
		});
		// what a fallen ruler had put by is theirs to leave (regalia never drops)
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof PathfinderMob mob && entity.level() instanceof ServerLevel level && Rulers.is(mob)) {
				Wear wear = mob.getAttached(WEAR);
				if (wear != null) {
					for (List<ItemStack> set : wear.kept().values()) {
						for (ItemStack stack : set) {
							if (!stack.isEmpty() && !Regalia.marked(stack)) {
								mob.spawnAtLocation(level, stack.copy());
							}
						}
					}
				}
			}
		});
	}

	// ---- the day -------------------------------------------------------------------------------------------------------

	/** What a ruler is about at time of day {@code t}. */
	static Activity activity(long t) {
		if (t >= BEDTIME || t < Royals.DAWN) {
			return Activity.BED;
		}
		if (t < MORNING_COURT_TO) {
			return Activity.COURT;
		}
		if (t < Rulers.ROUND_TO) {
			return Activity.ROUND;
		}
		if (t >= MEAL_FROM && t < MEAL_TO) {
			return Activity.MEAL;
		}
		if (t < Rulers.COURT_TO) {
			return Activity.AUDIENCE;
		}
		return t < Rulers.GUARD_EVENING ? Activity.LEISURE : Activity.EVENING;
	}

	/** What {@code temper} would have a ruler wear at {@code t} with no danger about. */
	static Outfit scheduled(Tempers.@Nullable Temper temper, long t) {
		if (activity(t) == Activity.BED) {
			return Outfit.NIGHT;
		}
		long[] hours = temper == null ? null : ARMOURED_HOURS.get(temper);
		return hours != null && t >= hours[0] && t < hours[1] ? Outfit.PLATE : Outfit.ROBES;
	}

	/** Danger to {@code ruler} just now: a foe near, a hostile stranger, a fresh wound. A paranoid ruler sees it from further off. */
	private static boolean threat(ServerLevel level, PathfinderMob ruler, Faction f, Tempers.@Nullable Temper temper) {
		if (ruler.getLastHurtByMob() != null && ruler.tickCount - ruler.getLastHurtByMobTimestamp() < DANGER_LINGER) {
			return true;
		}
		boolean paranoid = temper == Tempers.Temper.PARANOID;
		double range = paranoid ? PARANOID_RANGE : DANGER_RANGE;
		return !level.getEntitiesOfClass(LivingEntity.class, ruler.getBoundingBox().inflate(range),
			e -> e.isAlive() && e != ruler && (e instanceof Enemy || Raids.isRaider(e)
				|| e instanceof ServerPlayer p && !p.isCreative() && !p.isSpectator()
				&& (Reputation.get(p, f.id()) <= Reputation.HOSTILE || paranoid && Reputation.get(p, f.id()) < STRANGER_REP))).isEmpty();
	}

	/** The realm is at war, or a war party is coming for it. */
	private static boolean war(MinecraftServer server, Faction f) {
		return WarAlert.atWar(server, f) || WarParties.incoming(server, f.id()) != null;
	}

	/** What {@code ruler} chooses to wear now. */
	static Outfit choose(ServerLevel level, PathfinderMob ruler, Faction f, Tempers.@Nullable Temper temper, long t, long now) {
		if (threat(level, ruler, f, temper)) {
			DANGER.put(ruler.getUUID(), now);
		}
		boolean alarmed = alarmed(ruler, now);
		// (a war arms all but the reveler at his feast and after)
		boolean warring = war(level.getServer(), f) && !(temper == Tempers.Temper.REVELER && t >= Tempers.FEAST_FROM && t < BEDTIME);
		if (alarmed || warring) {
			return Outfit.PLATE;
		}
		return scheduled(temper, t);
	}

	private static boolean alarmed(PathfinderMob ruler, long now) {
		Long at = DANGER.get(ruler.getUUID());
		return at != null && now - at < DANGER_LINGER;
	}

	// ---- changing ------------------------------------------------------------------------------------------------------

	/**
	 * From {@link Tempers}' tick: {@code ruler} of {@code f} thinks what to wear, and changes if it isn't what they have on.
	 */
	static void tick(ServerLevel level, Faction f, Tempers.@Nullable Temper temper, @Nullable PathfinderMob ruler, long now) {
		if (ruler == null || !ruler.isAlive() || Carriages.riding(ruler)) {
			return;
		}
		long t = Royals.timeOfDay(level);
		Outfit want = choose(level, ruler, f, temper, t, now);
		Outfit have = worn(ruler);
		if (want == have) {
			return;
		}
		boolean hurried = want == Outfit.PLATE && alarmed(ruler, now);
		long since = now - CHANGED.getOrDefault(ruler.getUUID(), -SWAP_EVERY);
		if (since < (hurried ? SWAP_HURRIED : SWAP_EVERY)) {
			return;
		}
		if (ruler.isSleeping() && !hurried) {
			// (they dress when they wake, not in their sleep)
			return;
		}
		if (Arrests.judging(ruler)) {
			// (in the middle of passing judgement)
			return;
		}
		change(level, f, ruler, have, want, hurried);
		CHANGED.put(ruler.getUUID(), now);
	}

	/** What {@code ruler} has on: their saved state, or by what kind of ruler they are (a folk king wears plate from the start). */
	static Outfit worn(PathfinderMob ruler) {
		Wear wear = ruler.getAttached(WEAR);
		if (wear == null) {
			return ruler instanceof Folk ? Outfit.PLATE : Outfit.ROBES;
		}
		for (Outfit o : Outfit.values()) {
			if (o.key().equals(wear.worn())) {
				return o;
			}
		}
		return Outfit.ROBES;
	}

	/** {@code ruler} is in nightwear: nothing re-dresses them in royal attire ({@link Rulers#step}). */
	static boolean nightwear(PathfinderMob ruler) {
		return worn(ruler) == Outfit.NIGHT;
	}

	/** Puts the set {@code have} by, and puts {@code want} on (generating it if it's never been worn). */
	private static void change(ServerLevel level, Faction f, PathfinderMob ruler, Outfit have, Outfit want, boolean hurried) {
		Map<String, List<ItemStack>> kept = new HashMap<>();
		Wear before = ruler.getAttached(WEAR);
		if (before != null) {
			before.kept().forEach((k, v) -> kept.put(k, new ArrayList<>(v)));
		}
		// what's on goes by
		List<ItemStack> off = new ArrayList<>(SLOTS.length);
		for (EquipmentSlot slot : SLOTS) {
			off.add(ruler.getItemBySlot(slot));
			ruler.setItemSlot(slot, ItemStack.EMPTY);
		}
		kept.put(have.key(), off);
		List<ItemStack> on = kept.remove(want.key());
		if (on == null || on.size() != SLOTS.length) {
			generate(ruler, f, want);
		} else {
			for (int i = 0; i < SLOTS.length; i++) {
				ruler.setItemSlot(SLOTS[i], on.get(i));
				if (Regalia.marked(on.get(i))) {
					ruler.setDropChance(SLOTS[i], 0.0F);
				}
			}
		}
		ruler.setAttached(WEAR, new Wear(want.key(), Map.copyOf(kept)));
		show(level, ruler, want, hurried);
	}

	/** A set never worn yet: plate (marked as regalia, so it never drops), finery, or nothing. */
	private static void generate(PathfinderMob ruler, Faction f, Outfit want) {
		int role = ruler instanceof Worker w ? w.role() : Folk.KING;
		switch (want) {
			case NIGHT -> {
			}
			case ROBES -> Regalia.dress(ruler, role, f, McaCompat.female(ruler), false);
			case PLATE -> {
				Tempers.Temper temper = Tempers.of(f);
				boolean grand = role == Folk.KING && (temper == Tempers.Temper.WARLORD || temper == Tempers.Temper.TYRANT);
				ItemStack[] set = grand
					? new ItemStack[] {new ItemStack(Items.NETHERITE_HELMET), new ItemStack(Items.NETHERITE_CHESTPLATE), new ItemStack(Items.NETHERITE_LEGGINGS), new ItemStack(Items.NETHERITE_BOOTS)}
					: role == Folk.LORD
						? new ItemStack[] {new ItemStack(Items.IRON_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.IRON_BOOTS)}
						: new ItemStack[] {new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE), new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS)};
				Outfits.Heraldry arms = Outfits.heraldry(f, ruler.level().getServer());
				for (int i = 0; i < SLOTS.length; i++) {
					Regalia.put(ruler, SLOTS[i], Regalia.trimmed(ruler, set[i], arms.pattern(), TrimMaterials.GOLD));
				}
			}
		}
	}

	/** The change, seen and heard: the sound of the metal, a word, the ruler held still a moment. */
	private static void show(ServerLevel level, PathfinderMob ruler, Outfit want, boolean hurried) {
		SoundEvent sound = want == Outfit.PLATE ? SoundEvents.ARMOR_EQUIP_IRON.value() : SoundEvents.ARMOR_EQUIP_GOLD.value();
		level.playSound(null, ruler.getX(), ruler.getY(), ruler.getZ(), sound, SoundSource.NEUTRAL, 1.0F, hurried ? 1.1F : 0.9F);
		level.sendParticles(ParticleTypes.CLOUD, ruler.getX(), ruler.getY() + 1.0, ruler.getZ(), 6, 0.3, 0.5, 0.3, 0.02);
		ruler.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
		int stand = hurried ? HURRIED_TICKS : DRESS_TICKS;
		ruler.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, stand, 9, false, false));
		ruler.getNavigation().stop();
		Speech.say(level, ruler, Component.translatable("royal.jaymoskingdoms.dress." + want.key() + (hurried ? ".hurried" : "")),
			want == Outfit.PLATE ? Speech.RED : Speech.GOLD, Tempers.SPEECH_RANGE);
	}

	/** The throne passes from {@code ruler}, or they're crowned afresh: what they had put by is forgotten (it was regalia). */
	static void reset(PathfinderMob ruler) {
		ruler.removeAttached(WEAR);
		DANGER.remove(ruler.getUUID());
		CHANGED.remove(ruler.getUUID());
	}
}
