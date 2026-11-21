package com.jaymoskingdoms;

import com.jaymoskingdoms.Captains.Captain;
import com.jaymoskingdoms.Captains.Trait;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import org.jspecify.annotations.Nullable;

/**
 * <b>The gift-eater</b>: one captain in the world (the {@link Trait#GIFT_EATER} one) drinks the gifts of the gifted
 * folk he beats and fights with them as his own, a little weaker. What a player sees: a captain who uses a power no
 * captain should have (Jaymo's Powers' gifted-villager kits), who changes from one to another as a fight goes on, and
 * who, if he downs a gifted villager or a player with a power, drinks it: a pale beam from them to him.
 * <ul>
 * <li><b>He never comes to a fight empty</b>: whenever his body has none, he eats one first ({@link #first}), from a
 * gifted villager (one a player has seen at work or been told of before any other: the gift leaves them for good), or,
 * with none about, from a stranger's gift.</li>
 * <li><b>Weaker</b>: never a master of it ({@code Gifted.give} without {@code master}), and his power's blows land at
 * {@link #WEAKER}.</li>
 * <li><b>Draining</b> ({@link #struck}): a gifted villager he hurts to {@link #DRINK_BELOW} of their health or down loses theirs to
 * him; a player with a power he downs or beats to the same loses nothing (he copies a weaker one, {@link #LEARN_GAP} between each).
 * He holds up to {@link #HOLD_MAX} and turns to another ({@link #TURN_EVERY}) as a fight goes on.</li>
 * <li><b>Kill him and it goes nowhere</b>: the gifts live on his body (scoreboard tags), and are gone with it.</li>
 * </ul>
 * Nothing without Jaymo's Powers (no gifts to eat); the trait is given to a standing captain, once, when no captain has it.
 */
final class GiftEaters {
	/** A held gift is a tag on his body: this and its power ("wind"). */
	static final String EATEN = "jaymoskingdoms_eaten_";
	/** His powers' blows land at this share. */
	static final float WEAKER = 0.85F;
	/** How many gifts he holds at once. */
	static final int HOLD_MAX = 3;
	/** Between each turn to another gift he holds, in a fight (ticks). */
	static final int TURN_EVERY = 160;
	/** A gifted foe he's hurt to this share of their health is drunk from. */
	static final float DRINK_BELOW = 0.35F;
	/** Between his copies of one player's power (ticks). */
	static final long LEARN_GAP = 6000L;
	/** How far (blocks) from him he looks for a gifted villager to eat from. */
	static final double SEARCH = 384.0;
	/** The beam's colour. */
	static final int PALE = 0xCFC3E8;
	/** How often (game ticks) a standing captain is picked for it, while none is. */
	static final int PICK_EVERY = 1200;

	/** Player → when he last copied their power (game time). */
	private static final Map<UUID, Long> LEARNT = new HashMap<>();

	private GiftEaters() {
	}

	static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((victim, source, base, taken, blocked) -> {
			if (taken > 0.0F) {
				struck(victim, source);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LEARNT.clear());
	}

	// ---- who he is ---------------------------------------------------------------------------------------------------

	/** Every so often (from {@link Captains}): if no captain is the gift-eater yet, a standing one becomes him. */
	static void pick(ServerLevel level) {
		MinecraftServer server = level.getServer();
		if (level.getGameTime() % PICK_EVERY >= Captains.EVERY || PowersCompat.giftable().isEmpty()) {
			return;
		}
		List<Captain> standing = new ArrayList<>();
		for (Captain c : Captains.all(server).values()) {
			if (c.has(Trait.GIFT_EATER)) {
				return;
			}
			if (!c.fallen() && !c.chief() && c.body().map(level::getEntity).orElse(null) instanceof PathfinderMob mob && mob.isAlive()) {
				standing.add(c);
			}
		}
		if (standing.isEmpty()) {
			return;
		}
		Captain c = standing.get(level.getRandom().nextInt(standing.size()));
		List<String> traits = new ArrayList<>(c.traits());
		traits.add(Trait.GIFT_EATER.key());
		Captain eater = c.climbed(c.rank(), List.copyOf(traits), c.trophy(), c.slain());
		Captains.put(server, eater);
		if (level.getEntity(c.body().orElseThrow()) instanceof LivingEntity body) {
			Captains.bind(level, eater, body);
		}
	}

	/** His step (from {@link Captains}): a gift on him first, and a turn to another while he fights. */
	static void step(ServerLevel level, PathfinderMob mob, @Nullable LivingEntity target) {
		if (!Captains.has(mob, Trait.GIFT_EATER) || mob.tickCount % 20 != 0) {
			return;
		}
		List<String> held = held(mob);
		if (held.isEmpty()) {
			first(level, mob);
		} else if (target != null && held.size() > 1 && mob.tickCount % TURN_EVERY == 0) {
			String now = PowersCompat.gift(mob);
			String next = held.get((Math.max(0, held.indexOf(now)) + 1) % held.size());
			use(mob, next);
		}
	}

	/** The gifts on {@code mob}'s body. */
	static List<String> held(LivingEntity mob) {
		List<String> out = new ArrayList<>();
		for (String tag : mob.entityTags()) {
			if (tag.startsWith(EATEN)) {
				out.add(tag.substring(EATEN.length()));
			}
		}
		out.sort(null);
		return out;
	}

	/** (From {@link CaptainWays#gives}) a blow by the gift-eater: his powers' land at {@link #WEAKER}. */
	static float gives(LivingEntity by, DamageSource source, float amount) {
		return Captains.has(by, Trait.GIFT_EATER) && CaptainWays.magic(source) ? amount * WEAKER : amount;
	}

	// ---- eating ------------------------------------------------------------------------------------------------------

	/** His first meal: a gifted villager's, one a player knows before any other; a stranger's if there's none. */
	private static void first(ServerLevel level, PathfinderMob mob) {
		RandomSource random = level.getRandom();
		List<AbstractVillager> known = new ArrayList<>(), others = new ArrayList<>();
		for (Entity e : level.getAllEntities()) {
			if (e instanceof AbstractVillager v && v.isAlive() && !v.isBaby() && v.distanceTo(mob) <= SEARCH && PowersCompat.gift(v) != null && !Captains.is(v)) {
				(metBySomeone(level, v) ? known : others).add(v);
			}
		}
		List<AbstractVillager> from = known.isEmpty() ? others : known;
		if (!from.isEmpty()) {
			AbstractVillager v = from.get(random.nextInt(from.size()));
			String power = PowersCompat.gift(v);
			PowersCompat.giftTake(v);
			swallow(level, mob, power, v);
			return;
		}
		List<String> any = PowersCompat.giftable();
		if (!any.isEmpty()) {
			swallow(level, mob, any.get(random.nextInt(any.size())), null);
		}
	}

	/** Some player has seen {@code v}'s power at work, or been told of it. */
	private static boolean metBySomeone(ServerLevel level, Entity v) {
		for (ServerPlayer p : level.players()) {
			Set<UUID> seen = p.getAttached(GiftTales.SEEN), asked = p.getAttached(GiftTales.ASKED);
			if (seen != null && seen.contains(v.getUUID()) || asked != null && asked.contains(v.getUUID())) {
				return true;
			}
		}
		return false;
	}

	/** {@code mob} holds {@code power} now (one of his goes if he holds too many), and fights with it. */
	private static void swallow(ServerLevel level, PathfinderMob mob, @Nullable String power, @Nullable Entity from) {
		if (power == null) {
			return;
		}
		List<String> held = held(mob);
		if (!held.contains(power)) {
			if (held.size() >= HOLD_MAX) {
				mob.removeTag(EATEN + held.get(level.getRandom().nextInt(held.size())));
			}
			mob.addTag(EATEN + power);
		}
		use(mob, power);
		if (from != null) {
			Fx.beam(level, from.position().add(0.0, from.getBbHeight() * 0.6, 0.0), mob.position().add(0.0, mob.getBbHeight() * 0.6, 0.0), 0.3F, PALE, 14);
			level.playSound(null, mob.getX(), mob.getY(), mob.getZ(), SoundEvents.SOUL_ESCAPE.value(), SoundSource.HOSTILE, 1.2F, 0.6F);
			Speech.say(level, mob, Component.translatable("speech.jaymoskingdoms.gift_eater.drink." + level.getRandom().nextInt(2)), Speech.RED, Approaches.SPEECH_RANGE);
		}
	}

	/** He fights with {@code power} (never a master of it, not hiding it). */
	private static void use(PathfinderMob mob, String power) {
		PowersCompat.give(mob, power, PowersCompat.BOLD, false, true);
	}

	/** {@code victim} was struck: if by the gift-eater, and hurt enough, he drinks. */
	private static void struck(LivingEntity victim, DamageSource source) {
		if (!(source.getEntity() instanceof PathfinderMob eater) || !eater.isAlive() || victim == eater || !(victim.level() instanceof ServerLevel level)
			|| !Captains.has(eater, Trait.GIFT_EATER) || !(victim.getHealth() <= victim.getMaxHealth() * DRINK_BELOW || Downed.isDowned(victim))) {
			return;
		}
		if (victim instanceof AbstractVillager v && PowersCompat.gift(v) != null) {
			String power = PowersCompat.gift(v);
			PowersCompat.giftTake(v);
			swallow(level, eater, power, v);
		} else if (victim instanceof ServerPlayer player) {
			String power = PowersCompat.power(player);
			long now = level.getGameTime();
			Long last = LEARNT.get(player.getUUID());
			if (power == null || !PowersCompat.giftable().contains(power) || held(eater).contains(power) || last != null && now - last < LEARN_GAP) {
				return;
			}
			LEARNT.put(player.getUUID(), now);
			// (a copy: the player keeps theirs)
			swallow(level, eater, power, player);
		}
	}
}
