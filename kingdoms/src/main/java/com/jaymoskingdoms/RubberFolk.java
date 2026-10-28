package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * (0.7.262, Andy's NOEA picks) Rubber-folk: pink and stretchy. Their reach is longer ({@link #REACH}: the stretch, as a
 * longer arm and not a drawn one), a lost limb grows back on its own ({@link #REGROW_EVERY}, as a Verdant's does, through
 * Jaymo's Powers' dismemberment), and they can take in the weak: look at a small, low-health foe, or any not-too-big foe
 * that lies downed, and press the heritage key close by ((0.7.288) a toggle, {@link HeritageHold}: the hand on it, pulses
 * through both, and it shrinks away) to swallow it for a heal ({@link #absorb}). (0.7.297, Andy: "rubber absorb should take
 * the absorbed's powers/ or abilities. they basically adopt their stats into themselves. but only one at a time") They
 * take on the last thing they swallowed ({@link #ADOPTED}): a share of its health, blow, armour and pace, and what it
 * could do (fire, water, flight, night eyes, a spring), until the next swallow replaces it or they die.
 * They have a sweet tooth: cookies, pie, honey, berries, melon, cake, and Jaymo's Kitchen's tarts, pies, cobbler and
 * brownies mend them ({@link #sweet}). Flaw: rubber burns, and pops: fire burns them longer, and arrows hurt more
 * ({@link Heritage#dealt}).
 */
public final class RubberFolk {
	/** The stretch: extra reach to hit and to use things (blocks). */
	static final double REACH = 1.0;
	/** Fire burns this much longer (a share), and arrows and spines hurt this many times as much (the flaw). */
	static final double BURN = 0.6;
	static final float POPS = 1.25F;
	/** A lost limb grows back this often (ticks), one at a time. */
	static final int REGROW_EVERY = 400;
	/**
	 * Absorbing: how far the foe may be (blocks), how long it takes (ticks), the share of its health they take back, at
	 * least and at most, and food. A weak foe is small (at most this wide and tall) with this little health at most; a downed
	 * one may be bigger.
	 */
	static final double ABSORB_REACH = 2.5;
	public static final int ABSORB_TICKS = 60;
	static final int ABSORB_FOOD = 2;
	static final float ABSORB_HEAL_SHARE = 0.4F, ABSORB_HEAL_MIN = 2.0F, ABSORB_HEAL_MAX = 8.0F;
	/** (0.7.297) Jaymo's Kitchen lays no body down for one tagged this (a swallowed player or villager). */
	static final String NO_CORPSE_TAG = "jaymoskitchen.no_corpse";
	static final float WEAK_HEALTH = 12.0F, WEAK_WIDTH = 1.0F, WEAK_HEIGHT = 1.4F;
	static final float DOWNED_HEALTH = 40.0F, DOWNED_WIDTH = 1.6F, DOWNED_HEIGHT = 2.4F;
	/**
	 * (0.7.297) Adopting: the share of the swallowed's health, blow and armour they take on, the most of each (health
	 * points, damage, armour points), and its pace against a usual mob's ({@link #PACE_USUAL}) as a share, at most this far
	 * either way. A gift lasts this long and is put back this often (ticks), so it never runs out while adopted.
	 */
	static final float ADOPT_SHARE = 0.5F, ADOPT_HEALTH_MAX = 10.0F, ADOPT_DAMAGE_MAX = 4.0F, ADOPT_ARMOR_MAX = 4.0F;
	static final double PACE_USUAL = 0.25, ADOPT_PACE_MAX = 0.2;
	static final int GIFT_TICKS = 220, GIFT_EVERY = 100;
	/** (0.7.297) What a swallowed thing could do, as bits of {@link Adopted#gifts}. */
	static final int FIRE = 1, WATER = 2, FLIGHT = 4, NIGHT = 8, SPRING = 16;
	/** A sweet thing mends this much; a baked one from Kitchen, and a slice of cake. */
	static final float SWEET_HEAL = 4.0F, KITCHEN_HEAL = 6.0F, CAKE_HEAL = 4.0F;
	/** Pink motes. */
	private static final DustParticleOptions PINK = new DustParticleOptions(0xF08CB8, 0.8F);

	/**
	 * (0.7.297) The last thing swallowed and what was taken from it: its type (for the name), the health, blow, armour and
	 * pace added, and its gifts ({@link #FIRE}...). Saved; lost on death.
	 */
	record Adopted(String type, float health, float damage, float armor, float pace, int gifts) {
		static final Codec<Adopted> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("type").forGetter(Adopted::type),
			Codec.FLOAT.fieldOf("health").forGetter(Adopted::health),
			Codec.FLOAT.fieldOf("damage").forGetter(Adopted::damage),
			Codec.FLOAT.fieldOf("armor").forGetter(Adopted::armor),
			Codec.FLOAT.fieldOf("pace").forGetter(Adopted::pace),
			Codec.INT.fieldOf("gifts").forGetter(Adopted::gifts)).apply(i, Adopted::new));
	}

	static final AttachmentType<Adopted> ADOPTED = AttachmentRegistry.create(JaymosKingdoms.id("rubber_adopted"),
		builder -> builder.persistent(Adopted.CODEC));

	/** The sweet each is eating now, and the ticks it had left when last seen. */
	private static final Map<UUID, ItemStack> EATING = new HashMap<>();
	private static final Map<UUID, Integer> LEFT = new HashMap<>();
	/**
	 * (0.7.306) A swallow under way → their MCA Size and Width as it began, as it'll end, and how far along it they've
	 * grown: {@code {fromSize, fromWidth, toSize, toWidth, share}}.
	 */
	private static final Map<UUID, float[]> GROWTH = new HashMap<>();

	private RubberFolk() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(RubberFolk::tick);
		// (0.7.297) a swallowed player wakes whole, and their next death leaves a body as usual
		net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> newPlayer.removeTag(NO_CORPSE_TAG));
		// a slice of cake, eaten (it can only be eaten hungry, as in vanilla)
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND && player instanceof ServerPlayer sp && Heritage.is(sp, Heritage.Kind.RUBBERFOLK)
				&& sp.canEat(false) && !sp.isShiftKeyDown()) {
				var state = level.getBlockState(hit.getBlockPos());
				if (state.is(Blocks.CAKE) || state.is(BlockTags.CANDLE_CAKES)) {
					relish(sp, CAKE_HEAL);
				}
			}
			return InteractionResult.PASS;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			EATING.remove(handler.player.getUUID());
			LEFT.remove(handler.player.getUUID());
			GROWTH.remove(handler.player.getUUID());
		});
	}

	/**
	 * The stretch and the burn, and (0.7.297) what they've adopted, on {@code player} as they stand (taken off anyone who
	 * isn't Rubber-folk).
	 */
	static void attributes(ServerPlayer player, Heritage.Kind kind) {
		boolean rubber = kind == Heritage.Kind.RUBBERFOLK;
		Skills.modifier(player, Attributes.ENTITY_INTERACTION_RANGE, "heritage_rubber_reach", rubber ? REACH : 0.0, AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.BURNING_TIME, "heritage_rubber_burn", rubber ? BURN : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Adopted adopted = rubber ? player.getAttached(ADOPTED) : null;
		Skills.modifier(player, Attributes.MAX_HEALTH, "heritage_rubber_adopted_health", adopted == null ? 0.0 : adopted.health(), AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.ATTACK_DAMAGE, "heritage_rubber_adopted_damage", adopted == null ? 0.0 : adopted.damage(), AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.ARMOR, "heritage_rubber_adopted_armor", adopted == null ? 0.0 : adopted.armor(), AttributeModifier.Operation.ADD_VALUE);
		Skills.modifier(player, Attributes.MOVEMENT_SPEED, "heritage_rubber_adopted_pace", adopted == null ? 0.0 : adopted.pace(),
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	/** (0.7.297) What swallowing {@code foe} gives: a share of its stats, and what it could do. */
	static Adopted adopt(LivingEntity foe) {
		float health = Math.min(ADOPT_HEALTH_MAX, (float)base(foe, Attributes.MAX_HEALTH) * ADOPT_SHARE);
		float damage = Math.min(ADOPT_DAMAGE_MAX, (float)base(foe, Attributes.ATTACK_DAMAGE) * ADOPT_SHARE);
		float armor = Math.min(ADOPT_ARMOR_MAX, (float)base(foe, Attributes.ARMOR) * ADOPT_SHARE);
		double speed = base(foe, Attributes.MOVEMENT_SPEED);
		float pace = speed <= 0.0 || foe.canBreatheUnderwater() && foe.isInWater() ? 0.0F
			: (float)Mth.clamp((speed / PACE_USUAL - 1.0) * ADOPT_SHARE, -ADOPT_PACE_MAX, ADOPT_PACE_MAX);
		EntityType<?> type = foe.getType();
		int gifts = 0;
		if (foe.fireImmune()) {
			gifts |= FIRE;
		}
		if (foe.canBreatheUnderwater()) {
			gifts |= WATER;
		}
		if (type == EntityTypes.BAT || type == EntityTypes.CHICKEN || type == EntityTypes.PHANTOM || type == EntityTypes.BEE || type == EntityTypes.PARROT
			|| type == EntityTypes.ALLAY || type == EntityTypes.VEX) {
			gifts |= FLIGHT;
		}
		if (foe instanceof Spider || type == EntityTypes.BAT) {
			gifts |= NIGHT;
		}
		if (foe instanceof Slime || foe instanceof Rabbit || foe instanceof Frog) {
			gifts |= SPRING;
		}
		return new Adopted(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(), health, damage, armor, pace, gifts);
	}

	/** {@code foe}'s own value of {@code attribute}, before anything was added (0 if it has none). */
	private static double base(LivingEntity foe, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute) {
		var instance = foe.getAttribute(attribute);
		return instance == null ? 0.0 : instance.getBaseValue();
	}

	/** (0.7.297) Keeps what they adopted could do going: fire, water, flight (a slow fall), night eyes, a spring. */
	private static void gifts(ServerPlayer player, Adopted adopted) {
		int gifts = adopted.gifts();
		if ((gifts & FIRE) != 0) {
			gift(player, MobEffects.FIRE_RESISTANCE, 0);
		}
		if ((gifts & WATER) != 0) {
			gift(player, MobEffects.WATER_BREATHING, 0);
		}
		if ((gifts & FLIGHT) != 0) {
			gift(player, MobEffects.SLOW_FALLING, 0);
		}
		if ((gifts & NIGHT) != 0) {
			gift(player, MobEffects.NIGHT_VISION, 0);
		}
		if ((gifts & SPRING) != 0) {
			gift(player, MobEffects.JUMP_BOOST, 1);
		}
	}

	private static void gift(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect, int amplifier) {
		player.addEffect(new MobEffectInstance(effect, GIFT_TICKS, amplifier, true, false, true));
	}

	/** A sweet thing: a cookie, a pie, honey, berries, a slice of melon, a cake, or one of Kitchen's. */
	static boolean sweet(ItemStack stack) {
		return stack.is(Items.COOKIE) || stack.is(Items.PUMPKIN_PIE) || stack.is(Items.HONEY_BOTTLE) || stack.is(Items.SWEET_BERRIES)
			|| stack.is(Items.MELON_SLICE) || stack.is(Items.CAKE) || KitchenCompat.sweet(stack);
	}

	private static void tick(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID id = player.getUUID();
			if (!Heritage.is(player, Heritage.Kind.RUBBERFOLK)) {
				if (!EATING.isEmpty()) {
					EATING.remove(id);
					LEFT.remove(id);
				}
				continue;
			}
			// (0.7.297) what they adopted keeps working
			Adopted adopted = player.getAttached(ADOPTED);
			if (adopted != null && server.getTickCount() % GIFT_EVERY == 0 && player.isAlive()) {
				gifts(player, adopted);
			}
			// a lost limb grows back, one at a time
			if (server.getTickCount() % REGROW_EVERY == 0 && player.isAlive() && PowersCompat.regrow(player, false) && player.level() instanceof ServerLevel level) {
				level.sendParticles(PINK, player.getX(), player.getY(0.6), player.getZ(), 16, 0.4, 0.6, 0.4, 0.02);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.8F, 1.4F);
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.rubber.regrown"));
			}
			// a sweet finished (seen eating it last tick, with next to nothing left)
			if (player.isUsingItem() && sweet(player.getUseItem())) {
				EATING.put(id, player.getUseItem().copy());
				LEFT.put(id, player.getUseItemRemainingTicks());
			} else if (EATING.containsKey(id)) {
				ItemStack was = EATING.remove(id);
				Integer left = LEFT.remove(id);
				if (left != null && left <= 2) {
					relish(player, was.is(Items.CAKE) ? CAKE_HEAL : KitchenCompat.sweet(was) ? KITCHEN_HEAL : SWEET_HEAL);
				}
			}
			if (player.level() instanceof ServerLevel level) {
				HeritageHold.Channel channel = HeritageHold.channel(player);
				if (channel != null && McaCompat.LOADED && !GROWTH.containsKey(id)) {
					float[] genes = McaBridge.bodyGenes(player);
					if (genes != null) {
						float[] to = BodySizes.withMass(genes[0], genes[1], channel.shape.height(), channel.shape.width());
						GROWTH.put(id, new float[] {genes[0], genes[1], to[0], to[1], 0.0F});
					}
				}
				LivingEntity foe = HeritageHold.step(level, player, RubberFolk::weak, ABSORB_REACH, ABSORB_TICKS, PINK, SoundEvents.SLIME_SQUISH_SMALL,
					"message.jaymoskingdoms.rubber.absorbing");
				if (foe != null) {
					absorb(level, player, foe);
				} else {
					grow(player, HeritageHold.channel(player));
				}
			}
		}
	}

	/**
	 * (0.7.306) Andy: "the mass should increase per pulse as the absorbed gets smaller". While {@code channel} runs, their
	 * build is as far from where it was to where it'll end ({@link BodySizes#withMass}) as the swallow was at its last
	 * pulse; let go (no channel), it goes back to how it was.
	 */
	private static void grow(ServerPlayer player, HeritageHold.@org.jspecify.annotations.Nullable Channel channel) {
		float[] growth = GROWTH.get(player.getUUID());
		if (growth == null || !McaCompat.LOADED) {
			return;
		}
		if (channel == null) {
			GROWTH.remove(player.getUUID());
			if (growth[4] > 0.0F) {
				McaBridge.setBody(player, growth[0], growth[1]);
			}
			return;
		}
		float at = (float)channel.pulsed;
		if (at > growth[4]) {
			growth[4] = at;
			McaBridge.setBody(player, Mth.lerp(at, growth[0], growth[2]), Mth.lerp(at, growth[1], growth[3]));
		}
	}

	/** (0.7.288) The heritage key pressed: starts (or stops) swallowing a weak foe close by. */
	static void press(ServerPlayer player) {
		// (0.7.306) sneaking, they bring their last swallow back up (or spit out one still alive in them)
		if (player.isShiftKeyDown() && HeritageHold.channel(player) == null && RubberStomach.bringUp(player)) {
			return;
		}
		HeritageHold.press(player, e -> (HeritageHold.foe(player, e) || person(player, e)) && weak(e), ABSORB_REACH, "message.jaymoskingdoms.rubber.absorbing");
	}

	/**
	 * (0.7.297, Andy: "you get a player's power or villager if you absorb them") Another player or a villager (one of a
	 * kingdom's folk too), downed: theirs to swallow, power and all.
	 */
	static boolean person(ServerPlayer player, LivingEntity e) {
		return e != player && e.isAlive() && Downed.isDowned(e)
			&& (e instanceof ServerPlayer other && !other.isCreative() && !other.isSpectator() || e instanceof Villager || e instanceof Folk);
	}

	/** Small and low in health, or downed and not too big: weak enough to swallow. */
	static boolean weak(LivingEntity e) {
		if (Downed.isDowned(e)) {
			return e.getMaxHealth() <= DOWNED_HEALTH && e.getBbWidth() <= DOWNED_WIDTH && e.getBbHeight() <= DOWNED_HEIGHT;
		}
		return e.getMaxHealth() <= WEAK_HEALTH && e.getBbWidth() <= WEAK_WIDTH && e.getBbHeight() <= WEAK_HEIGHT;
	}

	/**
	 * {@code foe} is swallowed whole: gone, and a share of its health theirs. (0.7.306) Its body stays in them, to be
	 * brought up ({@link RubberStomach#bringUp}); a downed player isn't dead yet, but held inside ({@link RubberStomach#swallowLive}).
	 */
	private static void absorb(ServerLevel level, ServerPlayer player, LivingEntity foe) {
		float heal = Mth.clamp(foe.getMaxHealth() * ABSORB_HEAL_SHARE, ABSORB_HEAL_MIN, ABSORB_HEAL_MAX);
		level.sendParticles(PINK, foe.getX(), foe.getY(0.5), foe.getZ(), 20, 0.3, 0.4, 0.3, 0.03);
		level.playSound(null, foe.getX(), foe.getY(), foe.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.0F, 0.8F);
		player.heal(heal);
		// (0.7.306) Andy: "it should add their mass to the user": the last of its bulk goes in (the rest went with each pulse, grow)
		float[] growth = GROWTH.remove(player.getUUID());
		float[] gained = {0.0F, 0.0F};
		if (growth != null && McaCompat.LOADED) {
			float[] was = McaBridge.bodyGenes(player);
			McaBridge.setBody(player, growth[2], growth[3]);
			float[] now = McaBridge.bodyGenes(player);
			if (was != null && now != null) {
				gained = new float[] {now[0] - growth[0], now[1] - growth[1]};
			}
		}
		player.getFoodData().setFoodLevel(Math.min(20, player.getFoodData().getFoodLevel() + ABSORB_FOOD));
		if (foe instanceof ServerPlayer victim) {
			// (0.7.306) Andy: "If a downed player is absorbed they stay alive, but wounded and appear inside the rubber folk"
			RubberStomach.swallowLive(level, player, victim, gained);
			return;
		}
		// (0.7.297) a villager: their power runs in them now, its R theirs (Powers' SwallowedPower), and they die of it (credited to the swallower, so the
		// law and their kin know); anything else is simply gone
		boolean person = foe instanceof Villager || foe instanceof Folk;
		if (person) {
			PowersCompat.takePower(player, foe);
		}
		takeOn(player, foe, person);
		// (0.7.306) the body it leaves goes into them (at its own size, not shrunk to nothing), to be brought up later
		HeritageHold.shrink(foe, 0.0);
		RubberStomach.fill(player, KitchenCompat.corpseOf(foe), java.util.List.of(), gained);
		if (!person) {
			foe.discard();
			return;
		}
		HeritageHold.shrink(foe, HeritageHold.SHRINK);
		foe.addTag(NO_CORPSE_TAG);
		if (!Necromancy.execute(level, foe, player)) {
			foe.setInvulnerableTime(0);
			foe.hurtServer(level, level.damageSources().playerAttack(player), Float.MAX_VALUE);
		}
		if (foe.isAlive()) {
			// (something saved them after all (a totem): back to their size, and no body-less death waiting for them later)
			HeritageHold.shrink(foe, 0.0);
			foe.removeTag(NO_CORPSE_TAG);
		}
	}

	/** (0.7.297) {@code player} takes on what {@code foe} had, in place of the last thing they swallowed, and is told so. */
	static void takeOn(ServerPlayer player, LivingEntity foe, boolean person) {
		Adopted adopted = adopt(foe);
		player.setAttached(ADOPTED, adopted);
		attributes(player, Heritage.Kind.RUBBERFOLK);
		gifts(player, adopted);
		player.sendSystemMessage(Component.translatable(person ? "message.jaymoskingdoms.rubber.adopted_person" : "message.jaymoskingdoms.rubber.adopted", person ? foe.getDisplayName() : foe.getType().getDescription(),
			describe(adopted)).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE));
	}

	/** (0.7.306) What they took on leaves them (they brought it back up). */
	static void giveUp(ServerPlayer player) {
		if (player.hasAttached(ADOPTED)) {
			player.removeAttached(ADOPTED);
			attributes(player, Heritage.of(player));
		}
	}

	/** (0.7.297) What they took on, for the chat line: "+5 health, +1.5 blow, fire" and so on. */
	static Component describe(Adopted adopted) {
		java.util.List<Component> parts = new java.util.ArrayList<>();
		if (adopted.health() > 0.0F) {
			parts.add(Component.translatable("message.jaymoskingdoms.rubber.adopted.health", fmt(adopted.health())));
		}
		if (adopted.damage() > 0.0F) {
			parts.add(Component.translatable("message.jaymoskingdoms.rubber.adopted.damage", fmt(adopted.damage())));
		}
		if (adopted.armor() > 0.0F) {
			parts.add(Component.translatable("message.jaymoskingdoms.rubber.adopted.armor", fmt(adopted.armor())));
		}
		if (adopted.pace() != 0.0F) {
			parts.add(Component.translatable(adopted.pace() > 0.0F ? "message.jaymoskingdoms.rubber.adopted.faster" : "message.jaymoskingdoms.rubber.adopted.slower",
				Math.round(Math.abs(adopted.pace()) * 100.0F)));
		}
		String[] gifts = {"fire", "water", "flight", "night", "spring"};
		for (int i = 0; i < gifts.length; i++) {
			if ((adopted.gifts() & 1 << i) != 0) {
				parts.add(Component.translatable("message.jaymoskingdoms.rubber.adopted." + gifts[i]));
			}
		}
		if (parts.isEmpty()) {
			return Component.translatable("message.jaymoskingdoms.rubber.adopted.nothing");
		}
		return net.minecraft.network.chat.ComponentUtils.formatList(parts, Component.literal(", "));
	}

	private static String fmt(float value) {
		return value == Math.round(value) ? Integer.toString(Math.round(value)) : String.format(java.util.Locale.ROOT, "%.1f", value);
	}

	/** A sweet mends {@code player} by {@code amount}, with a little pink pop. */
	private static void relish(ServerPlayer player, float amount) {
		if (player.getHealth() < player.getMaxHealth() && !Downed.isDowned(player)) {
			player.heal(amount);
		}
		if (player.level() instanceof ServerLevel level) {
			level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY(1.1), player.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
		}
	}

	/** (the flaw) arrows and spines pop them: the factor on harm from {@code source}. */
	static float taken(net.minecraft.world.damagesource.DamageSource source) {
		return source.is(DamageTypeTags.IS_PROJECTILE) || source.is(net.minecraft.world.damagesource.DamageTypes.CACTUS)
			|| source.is(net.minecraft.world.damagesource.DamageTypes.STALAGMITE) ? POPS : 1.0F;
	}
}
