package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Every blood tastes of its people (Night's Blood, patch 3). A full vampire who drinks a person (a bite, a throat held, a
 * bottle, a willing cup) takes the after-taste of that person's heritage ({@link Heritage}) for {@link #TASTE_TICKS}:
 * <ul>
 * <li>Frostborn: cold can't touch you (Kitchen's body heat, freezing damage and frost on the skin).</li>
 * <li>Emberborn: fire can't (fire resistance).</li>
 * <li>Tideborn: you swim fast (dolphin's grace).</li>
 * <li>Stoneborn: you're heavy and hard to knock back (knockback resistance, a slower step).</li>
 * <li>Verdant: half the mB ({@link #VERDANT_SHARE}).</li>
 * <li>Divine-born: it burns you, as holy dread does ({@link HolyDread#suffer}, one searing at the first mouthful).</li>
 * <li>Nightborn: a long, deep fill: the drink gives {@link #DEEP_EXTRA} more over the next minute, a little a second
 * ({@link #DEEP_EACH}).</li>
 * </ul>
 * A king's or lord's blood ({@link Rulers#is}) gives {@link #GREAT_FACTOR} times the mB. A bottle carries the blood's taste
 * and greatness (Kitchen's blood bottle remembers them), so it tastes the same later. A dhampir keeps the food bar and has no
 * tastes. Blood of any other people has none.
 */
public final class BloodTaste {
	/** How long an after-taste stays (ticks). */
	public static final int TASTE_TICKS = 20 * 90;
	/** A king's or lord's blood gives this many times the mB; a Verdant's this share of it. */
	static final int GREAT_FACTOR = 2;
	static final float VERDANT_SHARE = 0.5F;
	/** A Nightborn's long fill: this share of the drink more, paid out this many mB each second. */
	static final float DEEP_EXTRA = 0.5F;
	static final int DEEP_EACH = 10;
	/** Divine-born blood: the dread felt each second while it lasts (under the burning line), the dread of the first mouthful, and the least gap between searings (ticks). */
	static final float DIVINE_DREAD = 0.4F, DIVINE_BURN = 0.6F;
	static final int DIVINE_BURN_GAP = 100;
	/** Stoneborn blood: the knockback resisted (0-1 added), and how much slower the step (share of speed). */
	static final double STONE_KNOCKBACK = 0.8, STONE_SLOW = -0.1;
	private static final Identifier STONE_KB_ID = JaymosKingdoms.id("taste_stone_knockback"), STONE_SLOW_ID = JaymosKingdoms.id("taste_stone_slow");

	/** The taste in them: which heritage's, until when (game time), and when it last burned (Divine-born). */
	record Tasted(String key, long until, long burned) {
		static final Codec<Tasted> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("key").forGetter(Tasted::key),
			Codec.LONG.fieldOf("until").forGetter(Tasted::until),
			Codec.LONG.fieldOf("burned").forGetter(Tasted::burned)).apply(i, Tasted::new));
	}

	static final AttachmentType<Tasted> TASTED = AttachmentRegistry.create(JaymosKingdoms.id("blood_taste"), builder -> builder.persistent(Tasted.CODEC));
	/** What a Nightborn's blood still owes the drinker (mB). */
	static final AttachmentType<Integer> DEEP = AttachmentRegistry.create(JaymosKingdoms.id("blood_deep"), builder -> builder.persistent(Codec.INT));

	private BloodTaste() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(BloodTaste::tick);
		// frost on the skin does nothing to one who has tasted the Frostborn
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
			!(entity instanceof ServerPlayer player && source.is(DamageTypeTags.IS_FREEZING) && has(player, Heritage.Kind.FROSTBORN)));
		KitchenCompat.addColdProof(player -> has(player, Heritage.Kind.FROSTBORN));
	}

	// ---- what a blood is worth ----------------------------------------------------------------------------------------------

	/** The taste key of {@code kind}'s blood ({@link Heritage.Kind#key}), or "" when that people's blood has none of its own. */
	public static String key(Heritage.@Nullable Kind kind) {
		if (kind == null) {
			return "";
		}
		return switch (kind) {
			case FROSTBORN, EMBERBORN, TIDEBORN, STONEBORN, VERDANT, DIVINE, NIGHTBORN -> kind.key();
			default -> "";
		};
	}

	/** The heritage a taste key stands for, or null. */
	static Heritage.@Nullable Kind kindOf(String key) {
		return key.isEmpty() ? null : Heritage.Kind.byKey(key);
	}

	/** Whether {@code e}'s blood is a king's or lord's. */
	static boolean great(LivingEntity e) {
		return Rulers.is(e);
	}

	/** {@code base} mB of {@code kind}'s blood, {@code great} or not: doubled for a king's or lord's, halved for a Verdant's. */
	public static int scaled(int base, Heritage.@Nullable Kind kind, boolean great) {
		float mb = base;
		if (great) {
			mb *= GREAT_FACTOR;
		}
		if (kind == Heritage.Kind.VERDANT) {
			mb *= VERDANT_SHARE;
		}
		return Math.round(mb);
	}

	/** The heritage {@code e}'s blood is, or null. */
	static Heritage.@Nullable Kind blood(LivingEntity e) {
		return VillagerHeritage.of(e);
	}

	// ---- drinking ---------------------------------------------------------------------------------------------------------

	/**
	 * A gulp of {@code target}'s blood worth {@code food} old food points: a full vampire's reserve gains it scaled by whose
	 * blood it is, unless it goes into a bottle held in the off hand ({@link BloodBottling#catches}); a dhampir eats it as
	 * food, as before.
	 */
	static void gulp(ServerPlayer player, LivingEntity target, int food, float saturation) {
		if (!VampireBlood.full(player)) {
			VampireBlood.feed(player, food, saturation);
			return;
		}
		Heritage.Kind kind = blood(target);
		int mb = scaled(food * VampireBlood.PER_FOOD, kind, great(target));
		if (BloodBottling.catches(player, target, mb)) {
			return;
		}
		drank(player, mb, kind, false);
	}

	/** {@code mb} of {@code kind}'s blood (already scaled) drunk by a full vampire: into the reserve, then the after-taste. */
	static void drank(ServerPlayer player, int mb, Heritage.@Nullable Kind kind, boolean bottle) {
		VampireBlood.add(player, bottle ? BloodPage.bottle(player, mb) : mb);
		taste(player, kind);
		if (kind == Heritage.Kind.NIGHTBORN) {
			int owed = player.getAttachedOrElse(DEEP, 0) + Math.round(mb * DEEP_EXTRA);
			player.setAttached(DEEP, owed);
		}
	}

	/** {@code player} (a full vampire) tastes {@code kind}'s blood: the after-taste begins, or is renewed. */
	static void taste(ServerPlayer player, Heritage.@Nullable Kind kind) {
		String key = key(kind);
		if (key.isEmpty() || !VampireBlood.full(player)) {
			return;
		}
		long now = player.level().getGameTime();
		Tasted was = player.getAttached(TASTED);
		long burned = was != null && was.key().equals(key) ? was.burned() : 0L;
		if (kind == Heritage.Kind.DIVINE && now - burned >= DIVINE_BURN_GAP) {
			HolyDread.suffer(player, DIVINE_BURN);
			burned = now;
		}
		player.setAttached(TASTED, new Tasted(key, now + TASTE_TICKS, burned));
	}

	// ---- what it does -----------------------------------------------------------------------------------------------------

	/** Whether {@code player} has the after-taste of {@code kind}'s blood now. */
	public static boolean has(net.minecraft.world.entity.player.Player player, Heritage.Kind kind) {
		Tasted t = player.getAttached(TASTED);
		return t != null && t.key().equals(kind.key()) && player.level().getGameTime() < t.until();
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 20 != 7) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Tasted t = player.getAttached(TASTED);
			boolean active = t != null && player.level().getGameTime() < t.until() && VampireBlood.full(player) && player.isAlive();
			stone(player, active && Heritage.Kind.STONEBORN.key().equals(t.key()));
			if (t != null && !active) {
				player.removeAttached(TASTED);
			}
			if (active) {
				ServerLevel level = player.level();
				switch (Heritage.Kind.byKey(t.key())) {
					case FROSTBORN -> {
						if (player.getTicksFrozen() > 0) {
							player.setTicksFrozen(0);
						}
					}
					case EMBERBORN -> player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, true, false, true));
					case TIDEBORN -> player.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 60, 0, true, false, true));
					case DIVINE -> {
						if (!player.isCreative()) {
							HolyDread.suffer(player, DIVINE_DREAD);
						}
					}
					case null, default -> {
					}
				}
			}
			int owed = player.getAttachedOrElse(DEEP, 0);
			if (owed > 0) {
				int pay = Math.min(owed, DEEP_EACH);
				VampireBlood.add(player, pay);
				player.setAttached(DEEP, owed - pay);
			}
		}
	}

	private static void stone(ServerPlayer player, boolean on) {
		AttributeInstance kb = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		if (kb != null) {
			if (on) {
				kb.addOrUpdateTransientModifier(new AttributeModifier(STONE_KB_ID, STONE_KNOCKBACK, AttributeModifier.Operation.ADD_VALUE));
			} else {
				kb.removeModifier(STONE_KB_ID);
			}
		}
		if (speed != null) {
			if (on) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(STONE_SLOW_ID, STONE_SLOW, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			} else {
				speed.removeModifier(STONE_SLOW_ID);
			}
		}
	}
}
