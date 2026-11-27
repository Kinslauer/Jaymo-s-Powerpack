package com.jaymospowers.alchemy.oil;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.alchemy.Alchemy;
import com.jaymospowers.alchemy.Essence;
import com.jaymospowers.alchemy.EssenceList;
import com.jaymospowers.alchemy.Research;
import com.jaymospowers.alchemy.brewing.BrewGrade;
import com.jaymospowers.alchemy.brewing.BrewGrades;
import com.jaymospowers.alchemy.hunter.Draughts;
import com.jaymospowers.alchemy.toxic.Harms;
import com.jaymospowers.armory.Armory;
import com.jaymospowers.compat.KingdomsCompat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Oils (Hunter's Road, patch 7, "D4" blade oils and "T-C1" effect oils; the idea of an oil on the blade is Toxony's and the
 * witcher's, written clean-room). A blade carries one oil at a time, as the {@link #OILED} component: which oil, its grade (1 to 3,
 * from the brew grade of the flask or the pot it came from) and the <b>swings</b> it has left, {@link #SWINGS} by grade, used up
 * on every swing ({@link #swung}, {@link #punched}); a held blade with oil on it has a sheen ({@code OilClient}).
 * <ul>
 * <li><b>Blade oils</b>: the dead, beasts, brutes, people or the cursed (vampires and werewolves) ({@link OilKind#monsterKind}); a blow on one of that kind does
 * {@link #BONUS} more. When the last swing of a blade oil lands on one of its kind, the hunter learns what that kind fears
 * (Kingdoms' bestiary).</li>
 * <li><b>Effect oils</b>: poison, a slowing and weakening haze, a glowing mark, deadly toxin, a blinding smoke, armour-eating acid
 * and a purifying fire, each carried into the wound by every blow that lands ({@link #bite}).</li>
 * <li><b>Mending oil</b>: on a tool, it mends the tool a little with each swing or block broken.</li>
 * </ul>
 * An oil goes on by dipping the blade in an oil pot ({@link OilPotBlock}), or by using the flask from the off hand
 * ({@link OilFlaskItem}); the splashes (fire resistance, healing) are only thrown. Flasks are made in the crucible from the fat of
 * monsters (Kitchen renders it) once the oils are read in the Codex.
 */
public final class Oils {
	// ---- tuning (per grade 1 to 3) ----
	/** Swings an oil lasts: 20, 40 and 60. */
	public static final int[] SWINGS = {20, 40, 60};
	/** Extra harm a blade oil does to its kind (Andy: +2, +4, +6). */
	public static final float[] BONUS = {2.0F, 4.0F, 6.0F};
	/** Ticks an effect oil's harm lasts on whoever it bites. */
	public static final int[] POISON_TICKS = {100, 200, 320}, HAZE_TICKS = {100, 160, 240}, MARK_TICKS = {200, 400, 600},
		TOXIN_TICKS = {60, 100, 160}, SMOKE_TICKS = {60, 100, 160}, ACID_TICKS = {200, 400, 700}, BURN_TICKS = {60, 100, 160};
	/** Durability a mending oil mends a use. */
	public static final int[] MEND = {1, 2, 3};
	/** The research that opens the crucible's oils. */
	public static final String RESEARCH = "hunters_oils";

	/** What a blade carries: the oil's id ({@link OilKind#id}), its grade and the uses it has left. */
	public record Oiled(String kind, int tier, int swings) {
		public static final Codec<Oiled> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("kind").forGetter(Oiled::kind),
			Codec.INT.fieldOf("tier").forGetter(Oiled::tier),
			Codec.INT.fieldOf("swings").forGetter(Oiled::swings)).apply(i, Oiled::new));
		public static final StreamCodec<ByteBuf, Oiled> STREAM = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Oiled::kind, ByteBufCodecs.VAR_INT, Oiled::tier, ByteBufCodecs.VAR_INT, Oiled::swings, Oiled::new);

		public OilKind oil() {
			return OilKind.byId(this.kind);
		}
	}

	public static DataComponentType<Oiled> OILED;

	/** The tick each player's last counted swing was (one swing is one, whoever it struck and however many). */
	private static final Map<ServerPlayer, Integer> LAST_SWING = new WeakHashMap<>();

	private Oils() {
	}

	public static void init() {
		OILED = Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, JaymosPowers.id("oiled"),
			DataComponentType.<Oiled>builder().persistent(Oiled.CODEC).networkSynchronized(Oiled.STREAM).build());
		for (OilKind kind : OilKind.values()) {
			kind.flask = Alchemy.item(kind.itemId(), p -> new OilFlaskItem(p.stacksTo(16), kind));
		}
		Research.register(new Research.Entry(RESEARCH, Research.Category.CRUCIBLE, () -> OilKind.DEAD.flask,
			EssenceList.of(Essence.BEAST, 8, Essence.DEATH, 6).plus(Essence.METAL, 4), List.of("crucible"), 0, 5, 1));
		OilPotBlock.init();
		OilSashItem.init();
		OilSlickBlock.init();
		com.jaymospowers.alchemy.bomb.Bombs.init();
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, entity) -> {
			if (player instanceof ServerPlayer sp && sp.getMainHandItem().has(OILED)) {
				used(sp, true);
			}
		});
	}

	// ---- reading ------------------------------------------------------------------------------------------------------

	/** The grade (1 to 3) of an oil flask stack: the same ladder as the hunter's draughts. */
	public static int tier(ItemStack flask) {
		return Draughts.tier(flask);
	}

	/** The brew grade a flask of {@code tier} carries. */
	public static BrewGrade grade(int tier) {
		return tier >= 3 ? BrewGrade.EXPERT : tier == 2 ? BrewGrade.FINE : BrewGrade.PLAIN;
	}

	/** A flask of {@code kind} at {@code tier}. */
	public static ItemStack flask(OilKind kind, int tier) {
		ItemStack out = new ItemStack(kind.flask);
		BrewGrades.set(out, grade(tier));
		return out;
	}

	/** Whether {@code stack} is a blade an oil goes on: a weapon. */
	public static boolean isBlade(ItemStack stack) {
		return !stack.isEmpty() && (stack.has(DataComponents.WEAPON) || Armory.isWeapon(stack));
	}

	/** Whether {@code kind} can go on {@code stack}. */
	public static boolean fits(OilKind kind, ItemStack stack) {
		return switch (kind.group) {
			case BLADE, EFFECT -> isBlade(stack);
			case MENDING -> !stack.isEmpty() && stack.isDamageableItem();
			case SPLASH -> false;
		};
	}

	public static Oiled of(ItemStack stack) {
		return stack.get(OILED);
	}

	// ---- putting an oil on --------------------------------------------------------------------------------------------

	/**
	 * Puts {@code kind} at {@code tier} on {@code target}, in place of any oil already there. False (nothing changes) if it does
	 * not fit.
	 */
	public static boolean coat(ServerPlayer player, ItemStack target, OilKind kind, int tier) {
		if (!fits(kind, target)) {
			return false;
		}
		int t = Math.max(1, Math.min(3, tier));
		target.set(OILED, new Oiled(kind.id, t, SWINGS[t - 1]));
		if (player.level() instanceof ServerLevel level) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.8F, 1.3F);
			level.sendParticles(ParticleTypes.FALLING_HONEY, player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.2, 0.2, 0.2, 0.0);
		}
		return true;
	}

	// ---- using it up --------------------------------------------------------------------------------------------------

	/** A swing at something (from {@code Player.attack}): one swing used, however many things it struck. */
	public static void swung(ServerPlayer player) {
		if (!player.getMainHandItem().has(OILED)) {
			return;
		}
		Integer last = LAST_SWING.put(player, player.tickCount);
		if (last == null || last != player.tickCount) {
			used(player, false);
		}
	}

	/** An empty swing (the punch the client sends when it strikes at air), when the arm is ready for one. */
	public static void punched(ServerPlayer player) {
		if (player.getMainHandItem().has(OILED) && player.getAttackStrengthScale(0.5F) >= 0.9F) {
			LAST_SWING.put(player, player.tickCount);
			used(player, false);
		}
	}

	/** One use of the oil on the main-hand stack; {@code broke} is a block broken (only a mending oil counts those). */
	private static void used(ServerPlayer player, boolean broke) {
		ItemStack stack = player.getMainHandItem();
		Oiled o = stack.get(OILED);
		OilKind kind = o == null ? null : o.oil();
		if (kind == null) {
			stack.remove(OILED);
			return;
		}
		if (broke && kind.group != OilKind.Group.MENDING) {
			return;
		}
		if (kind.group == OilKind.Group.MENDING && stack.isDamageableItem() && stack.getDamageValue() > 0) {
			stack.setDamageValue(Math.max(0, stack.getDamageValue() - MEND[o.tier() - 1]));
		}
		if (o.swings() <= 1) {
			stack.remove(OILED);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_EMPTY, SoundSource.PLAYERS, 0.4F, 1.6F);
		} else {
			stack.set(OILED, new Oiled(o.kind(), o.tier(), o.swings() - 1));
		}
	}

	// ---- on the blow --------------------------------------------------------------------------------------------------

	/** Whether {@code victim} is of the kind a blade oil bites. */
	public static boolean bites(LivingEntity victim, OilKind kind) {
		if (kind.monsterKind == null) {
			return false;
		}
		TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("jaymoskingdoms", "kind/" + kind.monsterKind));
		// the cursed are mostly players and villagers, not entity types: Kingdoms' MonsterKinds reads a vampire or a werewolf in beast form
		return victim.is(tag) || kind == OilKind.DEAD && victim.is(EntityTypeTags.UNDEAD)
			|| kind == OilKind.CURSED && Boolean.TRUE.equals(KingdomsCompat.call("MonsterKinds", "is", new Class<?>[] {net.minecraft.world.entity.Entity.class, String.class},
				Boolean.FALSE, victim, kind.monsterKind));
	}

	/**
	 * What a blow of {@code amount} becomes with an oil on the blade (from the same mixin as the draughts' and the harms'): a
	 * blade oil adds its {@link #BONUS} to one of its kind, an effect oil goes into the wound. Only a player's own melee blow with
	 * the blade in the main hand counts.
	 */
	public static float incoming(LivingEntity victim, DamageSource source, float amount) {
		if (amount <= 0.0F || !(source.getEntity() instanceof ServerPlayer by) || source.getDirectEntity() != by || !source.is(DamageTypes.PLAYER_ATTACK)
			|| victim == by) {
			return amount;
		}
		Oiled o = by.getMainHandItem().get(OILED);
		OilKind kind = o == null ? null : o.oil();
		if (kind == null || o.swings() <= 0) {
			return amount;
		}
		switch (kind.group) {
			case BLADE -> {
				if (bites(victim, kind)) {
					amount += BONUS[o.tier() - 1];
					if (victim.level() instanceof ServerLevel level) {
						level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, victim.getX(), victim.getY(0.6), victim.getZ(), 3, 0.2, 0.2, 0.2, 0.1);
					}
					if (o.swings() == 1) {
						KingdomsCompat.call("Bestiary", "learnFear", new Class<?>[] {ServerPlayer.class, EntityType.class}, null, by, victim.getType());
					}
				}
			}
			case EFFECT -> bite(victim, kind, o.tier(), by);
			default -> {
			}
		}
		return amount;
	}

	/** An effect oil goes into a wound: what it does to {@code victim} at grade {@code tier}. {@code by} may be null (a thrown oil). */
	public static void bite(LivingEntity victim, OilKind kind, int tier, ServerPlayer by) {
		int t = Math.max(1, Math.min(3, tier)) - 1;
		switch (kind) {
			case POISON -> victim.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS[t], t >= 2 ? 1 : 0));
			case HAZE -> {
				victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, HAZE_TICKS[t], t >= 1 ? 1 : 0));
				victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, HAZE_TICKS[t], t >= 2 ? 1 : 0));
			}
			case MARK -> {
				victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, MARK_TICKS[t], 0));
				Harms.apply(victim, Harms.Harm.HUNT, MARK_TICKS[t], 0);
			}
			case TOXIN -> Harms.apply(victim, Harms.Harm.TOXIN, TOXIN_TICKS[t], t);
			case SMOKE -> {
				victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, SMOKE_TICKS[t], 0));
				if (victim instanceof Mob mob) {
					mob.setTarget(null);
				}
				if (victim.level() instanceof ServerLevel level) {
					level.sendParticles(ParticleTypes.LARGE_SMOKE, victim.getX(), victim.getY(0.8), victim.getZ(), 6, 0.25, 0.3, 0.25, 0.02);
				}
			}
			case ACID -> Harms.apply(victim, Harms.Harm.ACID, ACID_TICKS[t], t);
			case FIRE -> {
				victim.setRemainingFireTicks(BURN_TICKS[t]);
				for (MobEffectInstance e : List.copyOf(victim.getActiveEffects())) {
					if (e.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) {
						victim.removeEffect(e.getEffect());
					}
				}
			}
			default -> {
			}
		}
	}

	/** Whether {@code item} is a flask of any oil. */
	public static boolean isFlask(Item item) {
		return item instanceof OilFlaskItem;
	}

	/** A glass bottle, for the tests of who may hold what. */
	static boolean bottle(ItemStack stack) {
		return stack.is(Items.GLASS_BOTTLE);
	}
}
