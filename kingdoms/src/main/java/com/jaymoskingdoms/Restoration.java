package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.jaymoskingdoms.Temperaments.Temperament;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.212) Restoration, the healing school. Andy (2026-10-25/26): "a restoration arcana that focuses on healing magic. a
 * sorcerer specializing in this would heal with j and cast healing magic with sneak". Its rules: it heals the body's parts
 * through Jaymo's Powers' wounds ({@link PowersCompat#mend}), worst first, and closes cuts; a plain heal never reaches the
 * downed; every heal that lands on the undead burns it instead; the big ones cost hunger as well as mana. Warm gold and
 * green, with the amethyst's chime.
 * <ul>
 * <li><b>The Restoration Sorcerer</b> (a seventh element; their blade never takes a charge): {@code J} is <b>Mend</b>, a
 * tap that heals who you look at (or you) over three seconds; sneak and {@code J} is <b>Renewal</b>, a ring of light
 * healing everyone friendly round you, or, looking at a downed friend, <b>Rekindle</b>, which has them up three seconds
 * later. <b>Lifebloom</b>: every blow they land on a foe heals the most hurt friend near them a little.</li>
 * <li><b>Spells</b> for scrolls and staffs ({@link Arcana#cast}): <b>Mending</b>, <b>Sanctuary</b> (a circle on the ground
 * the undead won't enter), and <b>Restoration</b> (whole again: bones set, festering cured, up if downed).</li>
 * <li><b>Healers:</b> a court wizard mends you for emeralds (free, and your bones set, if his realm thinks well of you)
 * and sells scrolls; in a fight he brings the downed of his side round before anything else. A village cleric sells
 * Mending scrolls, mends you, and looks after the hurt and the downed of the village.</li>
 * <li><b>Mercy:</b> villagers healed warm to you and remember it; bringing a downed foe round is mercy, and the devout and
 * the kindhearted who see it think the better of you.</li>
 * </ul>
 */
public final class Restoration {
	// ---- the Sorcerer's ----
	/** Mend: reach (blocks), heal (health points, over {@code MEND_TICKS}, in {@code MEND_PULSES}), cooldown (ticks), its burn on the undead. */
	static final double MEND_REACH = 8.0;
	static final float MEND_HEAL = 6.0F, MEND_SEAR = 6.0F;
	static final int MEND_TICKS = 60, MEND_PULSES = 3, MEND_COOLDOWN = 20 * 8;
	/** Renewal: reach, heal for each friend in it, cooldown, hunger, its burn and slow on the undead (ticks). */
	static final double RENEWAL_RANGE = 6.0;
	static final float RENEWAL_HEAL = 4.0F, RENEWAL_SEAR = 4.0F;
	static final int RENEWAL_COOLDOWN = 20 * 20, RENEWAL_HUNGER = 2, RENEWAL_SLOW = 100;
	/** Rekindle: reach, the share of health they get up with, cooldown, hunger, and how long before they rise (ticks). */
	static final double REKINDLE_REACH = 4.0;
	static final float REKINDLE_SHARE = 0.3F;
	static final int REKINDLE_COOLDOWN = 20 * 90, REKINDLE_HUNGER = 4, REKINDLE_DELAY = 60;
	/** Lifebloom: heal per blow on a foe, how far it finds a friend. */
	static final float LIFEBLOOM_HEAL = 1.0F;
	static final double LIFEBLOOM_RANGE = 12.0;

	// ---- the spells (times a caster's power) ----
	/** Mending: reach, heal, burn on the undead. */
	static final double MENDING_REACH = 8.0;
	static final float MENDING_HEAL = 6.0F;
	/** Sanctuary: radius (blocks), how long (ticks), heal per {@code SANCTUARY_HEAL_EVERY} for a friend inside, the push on the undead. */
	static final double SANCTUARY_RADIUS = 4.0, SANCTUARY_PUSH = 0.7;
	static final int SANCTUARY_TICKS = 20 * 15, SANCTUARY_HEAL_EVERY = 40;
	static final float SANCTUARY_HEAL = 1.0F, SANCTUARY_SEAR = 1.0F;
	/** Restoration: the hunger it costs whoever casts or reads it; the chance of its scroll in the rarest chests. */
	static final int RESTORATION_HUNGER = 6;
	static final float SCROLL_LOOT_CHANCE = 0.08F;

	// ---- the healers ----
	/** A healer's prices (emeralds): mending your wounds, a Scroll of Mending, a Scroll of Restoration. */
	static final int HEAL_EMERALDS = 3, MENDING_SCROLL_EMERALDS = 4, RESTORATION_SCROLL_EMERALDS = 24;
	/** At or over this a court wizard's realm mends you free and sets your bones; at or over the next he'll sell a Restoration. */
	static final int FRIEND_REP = 100, HONOURED_REP = 400;
	/** One Scroll of Restoration a week from a court wizard (ticks). */
	static final long RESTORATION_EVERY = 24000L * 7;
	/** A cleric's rounds: how often (ticks), how far, how much he mends, his own pause between them, between rekindlings. */
	static final int CLERIC_EVERY = 100, CLERIC_PAUSE = 20 * 10, CLERIC_REKINDLE_PAUSE = 20 * 60;
	static final double CLERIC_RANGE = 10.0, CLERIC_NEAR_PLAYER = 48.0;
	static final float CLERIC_HEAL = 4.0F, CLERIC_HEALS_BELOW = 0.8F;
	/** Hearts a villager warms by when you heal them (two when you bring them round), and how long before it counts again. */
	static final int MEND_HEARTS = 1, REKINDLE_HEARTS = 2;
	static final long CREDIT_EVERY = 20 * 60 * 5;
	/** How far the devout and the kindhearted see an act of mercy. */
	static final double MERCY_SEEN = 16.0;

	static final int GOLD = 0xFFD86B, GREEN = 0x9BE37A;
	static final DustParticleOptions GOLD_MOTE = new DustParticleOptions(GOLD, 1.0F), GREEN_MOTE = new DustParticleOptions(GREEN, 0.8F);

	/** When a player last bought a Scroll of Restoration from a court wizard (game time). Saved, kept through death. */
	static final AttachmentType<Long> RESTORATION_BOUGHT = AttachmentRegistry.create(JaymosKingdoms.id("restoration_bought"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());

	/** A heal landing a pulse at a time (Mend). */
	private record Pending(LivingEntity target, @Nullable ServerPlayer by, float perPulse, int pulsesLeft, long next) {
	}

	/** A downed one about to rise (Rekindle). */
	private record Rising(LivingEntity body, @Nullable ServerPlayer by, long at) {
	}

	/** A Sanctuary on the ground. */
	private record Circle(ServerLevel level, Vec3 centre, long until, float heal, @Nullable UUID owner) {
	}

	private static final List<Pending> PENDING = new ArrayList<>();
	private static final List<Rising> RISING = new ArrayList<>();
	private static final List<Circle> CIRCLES = new ArrayList<>();
	/** The Sorcerer's own cooldowns (game time each is ready again), by player. */
	private static final Map<UUID, Long> MEND_READY = new HashMap<>(), RENEWAL_READY = new HashMap<>(), REKINDLE_READY = new HashMap<>();
	/** When each was last credited for a heal, by healer and healed. */
	private static final Map<String, Long> CREDITED = new HashMap<>();
	/** A cleric's next rounds and next rekindling (game time). */
	private static final Map<Villager, long[]> CLERICS = new WeakHashMap<>();
	/** A healer's offer open with a player: the healer's entity id and what each option is. */
	private record Offer(int healer, List<String> kinds) {
	}

	private static final Map<UUID, Offer> OFFERS = new HashMap<>();

	private Restoration() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Restoration::serverTick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			PENDING.clear();
			RISING.clear();
			CIRCLES.clear();
			MEND_READY.clear();
			RENEWAL_READY.clear();
			REKINDLE_READY.clear();
			CREDITED.clear();
			CLERICS.clear();
			OFFERS.clear();
		});
		// Lifebloom: a Restoration Sorcerer's blow on a foe heals the most hurt friend near them
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (source.getEntity() instanceof ServerPlayer player && source.getDirectEntity() == player && entity != player && damageTaken > 0.0F
				&& !blocked && foe(player, entity) && restorer(player) && entity.level() instanceof ServerLevel level) {
				lifebloom(level, player);
			}
		});
		// a village cleric: sneak and use them (empty-handed, or with emeralds)
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !sp.isShiftKeyDown()
				|| !(entity instanceof Villager v) || !cleric(v) || CourtWizards.is(v)
				|| !(sp.getMainHandItem().isEmpty() || sp.getMainHandItem().is(Items.EMERALD))) {
				return InteractionResult.PASS;
			}
			if (!Languages.understands(sp, v)) {
				Languages.shrug(sp, v);
				return InteractionResult.SUCCESS;
			}
			offer(sp, v, false);
			return InteractionResult.SUCCESS;
		});
	}

	// ---- who ---------------------------------------------------------------------------------------------------------

	/** A Restoration Sorcerer. */
	static boolean restorer(Player player) {
		return Classes.is(player, PlayerClass.SORCERER) && "restoration".equals(Classes.of(player).option());
	}

	static boolean undead(LivingEntity e) {
		return e.getType().builtInRegistryHolder().is(EntityTypeTags.UNDEAD);
	}

	/** Someone a healer's magic is for: not a monster, not one going for {@code healer}, not undead. */
	static boolean friend(LivingEntity healer, LivingEntity e) {
		return e.isAlive() && !(e instanceof Enemy) && !undead(e) && !(e instanceof Mob m && m.getTarget() == healer);
	}

	/** Someone a blow on counts as fighting: a monster, or anything going for them. */
	private static boolean foe(Player player, LivingEntity e) {
		return e instanceof Enemy || e instanceof Mob m && m.getTarget() == player;
	}

	static boolean cleric(Villager v) {
		return !v.isBaby() && v.getVillagerData().profession().is(VillagerProfession.CLERIC);
	}

	// ---- the Sorcerer's keys -----------------------------------------------------------------------------------------

	/** A Restoration Sorcerer's {@code J} ({@code sneak}: with sneak held). */
	static void key(ServerPlayer player, ServerLevel level, boolean sneak) {
		LivingEntity target = Arcana.sighted(player);
		if (!sneak) {
			mend(player, level, target != null && player.distanceTo(target) <= MEND_REACH ? target : null);
		} else if (target != null && Downed.isDowned(target) && player.distanceTo(target) <= REKINDLE_REACH + 1.0) {
			rekindle(player, level, target);
		} else {
			renewal(player, level);
		}
	}

	private static boolean ready(ServerPlayer player, Map<UUID, Long> cooldowns, String what, long now) {
		Long at = cooldowns.get(player.getUUID());
		if (at != null && at > now && !player.isCreative()) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.restoration.cooldown",
				Component.translatable("restoration.jaymoskingdoms." + what), (at - now + 19) / 20));
			return false;
		}
		return true;
	}

	/** Whether {@code player} can spare {@code hunger} (and says so if not); creative players always can. */
	private static boolean fed(ServerPlayer player, int hunger) {
		if (player.isCreative() || player.getFoodData().getFoodLevel() >= hunger) {
			return true;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.restoration.hungry").withStyle(ChatFormatting.GOLD));
		return false;
	}

	private static void starve(ServerPlayer player, int hunger) {
		if (!player.isCreative()) {
			player.getFoodData().setFoodLevel(Math.max(0, player.getFoodData().getFoodLevel() - hunger));
		}
	}

	/** Mend: a tap; who you look at (or you), healed over three seconds and a cut closed; the undead seared. */
	private static void mend(ServerPlayer player, ServerLevel level, @Nullable LivingEntity sighted) {
		long now = level.getGameTime();
		if (!ready(player, MEND_READY, "mend", now)) {
			return;
		}
		LivingEntity target = sighted != null && (friend(player, sighted) || undead(sighted)) ? sighted : player;
		if (Downed.isDowned(target)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.restoration.downed"));
			return;
		}
		if (!Mana.spend(player, Mana.cost("mend"))) {
			return;
		}
		MEND_READY.put(player.getUUID(), now + MEND_COOLDOWN);
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		if (target != player) {
			beam(level, player, target, GOLD_MOTE);
		}
		if (undead(target)) {
			target.hurtServer(level, level.damageSources().indirectMagic(player, player), MEND_SEAR);
			target.igniteForSeconds(2.0F);
			level.sendParticles(ParticleTypes.SMOKE, target.getX(), target.getY(0.6), target.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8F, 1.4F);
			return;
		}
		PENDING.add(new Pending(target, player, MEND_HEAL / MEND_PULSES, MEND_PULSES, now + MEND_TICKS / MEND_PULSES));
		PowersCompat.closeCut(target);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.4F, 1.3F);
		level.sendParticles(GOLD_MOTE, target.getX(), target.getY(0.6), target.getZ(), 14, 0.35, 0.5, 0.35, 0.02);
		Theft.sorcery(player, level, player.blockPosition());
	}

	/** Renewal: a ring of gold light round them; every friend in it healed and their fever eased, the undead burnt and slowed. */
	private static void renewal(ServerPlayer player, ServerLevel level) {
		long now = level.getGameTime();
		if (!ready(player, RENEWAL_READY, "renewal", now) || !fed(player, RENEWAL_HUNGER) || !Mana.spend(player, Mana.cost("renewal"))) {
			return;
		}
		RENEWAL_READY.put(player.getUUID(), now + RENEWAL_COOLDOWN);
		starve(player, RENEWAL_HUNGER);
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		ring(level, player.position().add(0.0, 0.2, 0.0), RENEWAL_RANGE, GOLD_MOTE, 48);
		ring(level, player.position().add(0.0, 0.6, 0.0), RENEWAL_RANGE * 0.6, GREEN_MOTE, 28);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 1.2F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.7F, 1.6F);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RENEWAL_RANGE), LivingEntity::isAlive)) {
			if (e.distanceToSqr(player) > RENEWAL_RANGE * RENEWAL_RANGE) {
				continue;
			}
			if (undead(e)) {
				e.hurtServer(level, level.damageSources().indirectMagic(player, player), RENEWAL_SEAR);
				e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, RENEWAL_SLOW, 1), player);
				level.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY(0.6), e.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
			} else if (e == player || friend(player, e)) {
				if (PowersCompat.mend(e, RENEWAL_HEAL) > 0.0F | PowersCompat.easeFever(e)) {
					level.sendParticles(GOLD_MOTE, e.getX(), e.getY(0.6), e.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
					credit(level, player, e, false);
				}
			}
		}
		Theft.sorcery(player, level, player.blockPosition());
	}

	/** Rekindle: the light gathers on a downed friend, and three seconds later they're up. */
	private static void rekindle(ServerPlayer player, ServerLevel level, LivingEntity body) {
		long now = level.getGameTime();
		for (Rising r : RISING) {
			if (r.body() == body) {
				return;
			}
		}
		if (!ready(player, REKINDLE_READY, "rekindle", now) || !fed(player, REKINDLE_HUNGER) || !Mana.spend(player, Mana.cost("rekindle"))) {
			return;
		}
		REKINDLE_READY.put(player.getUUID(), now + REKINDLE_COOLDOWN);
		starve(player, REKINDLE_HUNGER);
		player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		beam(level, player, body, GOLD_MOTE);
		RISING.add(new Rising(body, player, now + REKINDLE_DELAY));
		level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.7F);
		Theft.sorcery(player, level, player.blockPosition());
	}

	/** Lifebloom: the most hurt friend near {@code player} (themselves too) healed a little. */
	private static void lifebloom(ServerLevel level, ServerPlayer player) {
		LivingEntity worst = null;
		float lowest = 0.999F;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(LIFEBLOOM_RANGE),
			e -> e == player || e instanceof Player || friend(player, e) && (e instanceof Villager || e instanceof Folk || e instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame()))) {
			float share = e.getHealth() / e.getMaxHealth();
			if (share < lowest && !Downed.isDowned(e)) {
				lowest = share;
				worst = e;
			}
		}
		if (worst == null || PowersCompat.mend(worst, LIFEBLOOM_HEAL) <= 0.0F) {
			return;
		}
		// (a thread of green only the attuned see)
		Vec3 from = player.getBoundingBox().getCenter(), to = worst.getBoundingBox().getCenter();
		int n = Math.max(2, (int)(from.distanceTo(to) * 2.0));
		for (int i = 0; i <= n; i++) {
			Vec3 p = from.lerp(to, i / (double)n);
			Mana.sendSeen(level, GREEN_MOTE, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	// ---- the spells ------------------------------------------------------------------------------------------------

	/** Mending: who you look at (a friend, within reach), else you; healed, a cut closed. The undead burnt instead. */
	static boolean mending(ServerLevel level, Player player, float power) {
		LivingEntity sighted = Arcana.sighted(player);
		LivingEntity target = sighted != null && player.distanceTo(sighted) <= MENDING_REACH && (friend(player, sighted) || undead(sighted)) ? sighted : player;
		if (target != player) {
			beam(level, player, target, GOLD_MOTE);
		}
		if (undead(target)) {
			target.hurtServer(level, level.damageSources().indirectMagic(player, player), MENDING_HEAL * power);
			target.igniteForSeconds(2.0F);
			return true;
		}
		if (Downed.isDowned(target)) {
			return false;
		}
		PowersCompat.mend(target, MENDING_HEAL * power);
		PowersCompat.closeCut(target);
		level.sendParticles(GOLD_MOTE, target.getX(), target.getY(0.6), target.getZ(), 16, 0.35, 0.5, 0.35, 0.02);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.4F, 1.2F);
		if (player instanceof ServerPlayer sp) {
			credit(level, sp, target, false);
		}
		return true;
	}

	/** Sanctuary: a circle of light where they stand; friends in it heal, the undead are thrown out of it. */
	static boolean sanctuary(ServerLevel level, Player player, float power) {
		CIRCLES.add(new Circle(level, player.position(), level.getGameTime() + (long)(SANCTUARY_TICKS * power), SANCTUARY_HEAL * Math.max(1.0F, power * 0.75F),
			player.getUUID()));
		ring(level, player.position().add(0.0, 0.1, 0.0), SANCTUARY_RADIUS, GOLD_MOTE, 40);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.4F);
		return true;
	}

	/** Restoration: who you look at (a friend, or one downed), else you, made whole. It costs the caster hunger. */
	static boolean restoration(ServerLevel level, Player player, float power) {
		if (player instanceof ServerPlayer sp && !fed(sp, RESTORATION_HUNGER)) {
			return false;
		}
		LivingEntity sighted = Arcana.sighted(player);
		LivingEntity target = sighted != null && (friend(player, sighted) || Downed.isDowned(sighted) && !undead(sighted)) ? sighted : player;
		boolean wasDown = Downed.isDowned(target);
		if (target != player) {
			beam(level, player, target, GOLD_MOTE);
		}
		PowersCompat.restore(target);
		if (wasDown && Downed.isDowned(target)) {
			// (no Powers to bring them round: asked of whoever holds the downed state)
			target.addTag(Downed.REVIVE_TAG);
		}
		ring(level, target.position().add(0.0, 0.1, 0.0), 1.5, GOLD_MOTE, 24);
		level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY(0.6), target.getZ(), 24, 0.4, 0.7, 0.4, 0.05);
		level.sendParticles(GOLD_MOTE, target.getX(), target.getY(0.6), target.getZ(), 30, 0.4, 0.7, 0.4, 0.03);
		level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.6F, 1.4F);
		if (player instanceof ServerPlayer sp) {
			starve(sp, RESTORATION_HUNGER);
			credit(level, sp, target, wasDown);
		}
		return true;
	}

	// ---- every tick ---------------------------------------------------------------------------------------------------

	private static void serverTick(MinecraftServer server) {
		if (!PENDING.isEmpty()) {
			List<Pending> next = new ArrayList<>();
			for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
				Pending p = it.next();
				it.remove();
				LivingEntity t = p.target();
				if (t.isRemoved() || !t.isAlive() || !(t.level() instanceof ServerLevel level)) {
					continue;
				}
				if (level.getGameTime() < p.next()) {
					next.add(p);
					continue;
				}
				if (PowersCompat.mend(t, p.perPulse()) > 0.0F && p.by() != null && t != p.by()) {
					credit(level, p.by(), t, false);
				}
				level.sendParticles(GOLD_MOTE, t.getX(), t.getY(0.6), t.getZ(), 6, 0.3, 0.4, 0.3, 0.02);
				if (p.pulsesLeft() > 1) {
					next.add(new Pending(t, p.by(), p.perPulse(), p.pulsesLeft() - 1, p.next() + MEND_TICKS / MEND_PULSES));
				}
			}
			PENDING.addAll(next);
		}
		for (Iterator<Rising> it = RISING.iterator(); it.hasNext(); ) {
			Rising r = it.next();
			LivingEntity body = r.body();
			if (body.isRemoved() || !body.isAlive() || !(body.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			long left = r.at() - level.getGameTime();
			if (left > 0L) {
				// the light gathers on them, rising
				double h = (REKINDLE_DELAY - left) / (double)REKINDLE_DELAY * body.getBbHeight() * 1.5;
				double a = left * 0.6;
				level.sendParticles(GOLD_MOTE, body.getX() + Math.cos(a) * 0.7, body.getY() + h, body.getZ() + Math.sin(a) * 0.7, 1, 0.0, 0.0, 0.0, 0.0);
				continue;
			}
			it.remove();
			boolean up = PowersCompat.rekindle(body, REKINDLE_SHARE);
			if (!up && Downed.isDowned(body)) {
				body.addTag(Downed.REVIVE_TAG);
				up = true;
			}
			if (up) {
				level.sendParticles(ParticleTypes.END_ROD, body.getX(), body.getY(0.6), body.getZ(), 20, 0.4, 0.6, 0.4, 0.06);
				level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 1.5F);
				if (r.by() != null) {
					credit(level, r.by(), body, true);
				}
			}
		}
		for (Iterator<Circle> it = CIRCLES.iterator(); it.hasNext(); ) {
			Circle c = it.next();
			long now = c.level().getGameTime();
			if (now >= c.until()) {
				it.remove();
				continue;
			}
			circle(c, now);
		}
		if (server.getTickCount() % CLERIC_EVERY == 0) {
			clerics(server);
		}
	}

	private static void circle(Circle c, long now) {
		ServerLevel level = c.level();
		if (now % 10L == 0L) {
			ring(level, c.centre().add(0.0, 0.1, 0.0), SANCTUARY_RADIUS, GOLD_MOTE, 32);
		}
		if (now % 5L != 0L) {
			return;
		}
		Player owner = c.owner() == null ? null : level.getPlayerByUUID(c.owner());
		boolean heal = now % SANCTUARY_HEAL_EVERY == 0L;
		double r2 = SANCTUARY_RADIUS * SANCTUARY_RADIUS;
		net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(c.centre(), c.centre()).inflate(SANCTUARY_RADIUS + 1.0, 2.0, SANCTUARY_RADIUS + 1.0);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, LivingEntity::isAlive)) {
			Vec3 off = e.position().subtract(c.centre());
			double d2 = off.x * off.x + off.z * off.z;
			if (undead(e)) {
				if (d2 <= (SANCTUARY_RADIUS + 1.0) * (SANCTUARY_RADIUS + 1.0)) {
					// thrown back out of the light
					Vec3 away = d2 < 0.01 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(off.x, 0.0, off.z).normalize();
					e.setDeltaMovement(away.x * SANCTUARY_PUSH, 0.15, away.z * SANCTUARY_PUSH);
					e.syncVelocity = true;
					if (heal) {
						e.hurtServer(level, owner == null ? level.damageSources().magic() : level.damageSources().indirectMagic(owner, owner), SANCTUARY_SEAR);
					}
				}
			} else if (heal && d2 <= r2 && (owner == null ? !(e instanceof Enemy) : e == owner || friend(owner, e))) {
				if (PowersCompat.mend(e, c.heal()) > 0.0F) {
					level.sendParticles(GOLD_MOTE, e.getX(), e.getY(0.6), e.getZ(), 4, 0.3, 0.4, 0.3, 0.02);
				}
			}
		}
	}

	/** Each cleric near a player, on their rounds: a downed villager brought round, else the most hurt mended. */
	private static void clerics(MinecraftServer server) {
		java.util.Set<Villager> seen = new java.util.HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			ServerLevel level = player.level();
			for (Villager cleric : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(CLERIC_NEAR_PLAYER),
				v -> v.isAlive() && cleric(v) && !v.isSleeping() && !Downed.isDowned(v))) {
				if (seen.add(cleric)) {
					rounds(level, cleric);
				}
			}
		}
	}

	private static void rounds(ServerLevel level, Villager cleric) {
		long now = level.getGameTime();
		long[] next = CLERICS.computeIfAbsent(cleric, c -> new long[2]);
		if (now < next[0]) {
			return;
		}
		List<Villager> near = level.getEntitiesOfClass(Villager.class, cleric.getBoundingBox().inflate(CLERIC_RANGE), v -> v.isAlive());
		if (now >= next[1]) {
			for (Villager v : near) {
				if (v != cleric && Downed.isDowned(v)) {
					next[1] = now + CLERIC_REKINDLE_PAUSE;
					next[0] = now + CLERIC_PAUSE;
					beam(level, cleric, v, GOLD_MOTE);
					RISING.add(new Rising(v, null, now + REKINDLE_DELAY));
					level.playSound(null, v.getX(), v.getY(), v.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 0.8F, 1.7F);
					return;
				}
			}
		}
		Villager worst = null;
		float lowest = CLERIC_HEALS_BELOW;
		for (Villager v : near) {
			float share = v.getHealth() / v.getMaxHealth();
			if (share < lowest && !Downed.isDowned(v)) {
				lowest = share;
				worst = v;
			}
		}
		if (worst == null) {
			return;
		}
		next[0] = now + CLERIC_PAUSE;
		if (worst != cleric) {
			beam(level, cleric, worst, GOLD_MOTE);
		}
		PowersCompat.mend(worst, CLERIC_HEAL);
		PowersCompat.closeCut(worst);
		ring(level, worst.position().add(0.0, 0.1, 0.0), 1.0, GOLD_MOTE, 14);
		level.playSound(null, worst.getX(), worst.getY(), worst.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 1.4F, 1.2F);
	}

	// ---- court wizards in a fight ------------------------------------------------------------------------------------

	/**
	 * A court wizard's mending turn: a downed one of {@code side} is brought round first (Rekindle); true if he did, and
	 * his mending waits its cooldown.
	 */
	static boolean wizardRekindle(ServerLevel level, LivingEntity wizard, int[] timers, List<? extends LivingEntity> side) {
		for (LivingEntity e : side) {
			if (e == wizard || !Downed.isDowned(e) || e.distanceTo(wizard) > Sorcery.SUPPORT_RANGE) {
				continue;
			}
			boolean already = false;
			for (Rising r : RISING) {
				already |= r.body() == e;
			}
			if (already) {
				continue;
			}
			timers[Sorcery.T_MEND] = Sorcery.MEND_COOLDOWN;
			beam(level, wizard, e, GOLD_MOTE);
			RISING.add(new Rising(e, null, level.getGameTime() + REKINDLE_DELAY));
			level.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.NEUTRAL, 0.8F, 1.7F);
			return true;
		}
		return false;
	}

	// ---- healers' services -------------------------------------------------------------------------------------------

	/** What a healer offers {@code player} (option kinds), a court wizard's ({@code wizard}) or a cleric's. */
	static List<String> services(ServerPlayer player, LivingEntity healer, boolean wizard) {
		List<String> out = new ArrayList<>();
		// (0.7.231) nothing for a known necromancer or blood mage
		if (ForbiddenArts.known(player)) {
			return out;
		}
		out.add(wizard && friendOfRealm(player, healer) ? "heal_free" : "heal");
		out.add("scroll_mending");
		if (wizard && reputation(player, healer) >= HONOURED_REP) {
			out.add("scroll_restoration");
		}
		return out;
	}

	/** An option's line, and whether it can be taken now. */
	static Component optionLine(String kind) {
		return switch (kind) {
			case "heal" -> Component.translatable("talk.jaymoskingdoms.healer.heal", HEAL_EMERALDS);
			case "heal_free" -> Component.translatable("talk.jaymoskingdoms.healer.heal_free");
			case "scroll_mending" -> Component.translatable("talk.jaymoskingdoms.healer.scroll_mending", MENDING_SCROLL_EMERALDS);
			case "scroll_restoration" -> Component.translatable("talk.jaymoskingdoms.healer.scroll_restoration", RESTORATION_SCROLL_EMERALDS);
			default -> Component.translatable("talk.jaymoskingdoms.wizard.respec.no");
		};
	}

	static boolean optionOpen(ServerPlayer player, String kind) {
		int emeralds = Approaches.emeralds(player);
		return switch (kind) {
			case "heal" -> emeralds >= HEAL_EMERALDS && hurt(player);
			case "heal_free" -> hurt(player);
			case "scroll_mending" -> emeralds >= MENDING_SCROLL_EMERALDS;
			case "scroll_restoration" -> emeralds >= RESTORATION_SCROLL_EMERALDS && restorationDue(player);
			default -> true;
		};
	}

	/** A cleric's offer: mending, and a Scroll of Mending. */
	static void offer(ServerPlayer player, LivingEntity healer, boolean wizard) {
		if (!ServerPlayNetworking.canSend(player, TalkNet.Ask.TYPE)) {
			return;
		}
		List<String> kinds = new ArrayList<>(services(player, healer, wizard));
		kinds.add("no");
		List<Component> options = new ArrayList<>();
		List<Boolean> enabled = new ArrayList<>();
		for (String kind : kinds) {
			options.add(optionLine(kind));
			enabled.add(optionOpen(player, kind));
		}
		OFFERS.put(player.getUUID(), new Offer(healer.getId(), kinds));
		ServerPlayNetworking.send(player, new TalkNet.Ask(healer.getId(), healer.getDisplayName(),
			Component.translatable(ForbiddenArts.known(player) ? "talk.jaymoskingdoms.healer.refuse" : "talk.jaymoskingdoms.healer.ask"), options, enabled));
	}

	/** An answer to a cleric's offer; false when it wasn't one. */
	static boolean answer(ServerPlayer player, TalkNet.Answer answer) {
		Offer offer = OFFERS.get(player.getUUID());
		if (offer == null || offer.healer() != answer.entityId()) {
			return false;
		}
		OFFERS.remove(player.getUUID());
		if (answer.option() < 0 || answer.option() >= offer.kinds().size()
			|| !(player.level().getEntity(answer.entityId()) instanceof LivingEntity healer) || healer.distanceTo(player) > 8.0) {
			return true;
		}
		serve(player, healer, offer.kinds().get(answer.option()));
		return true;
	}

	/** Does what {@code kind} asks of {@code healer} for {@code player}, if they can pay; true if it was a healer's kind. */
	static boolean serve(ServerPlayer player, LivingEntity healer, String kind) {
		ServerLevel level = player.level();
		if (!optionOpen(player, kind)) {
			return kind.startsWith("heal") || kind.startsWith("scroll_");
		}
		switch (kind) {
			case "heal", "heal_free" -> {
				if (kind.equals("heal")) {
					Approaches.take(player, Items.EMERALD, HEAL_EMERALDS);
					PowersCompat.mend(player, player.getMaxHealth());
					while (PowersCompat.closeCut(player)) {
						// (every cut closed)
					}
					PowersCompat.easeFever(player);
				} else {
					PowersCompat.restore(player);
				}
				beam(level, healer, player, GOLD_MOTE);
				ring(level, player.position().add(0.0, 0.1, 0.0), 1.2, GOLD_MOTE, 18);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.0F, 1.3F);
				Speech.tell(level, healer, player, Component.translatable(kind.equals("heal") ? "talk.jaymoskingdoms.healer.healed"
					: "talk.jaymoskingdoms.healer.healed_free"), Speech.GOLD);
			}
			case "scroll_mending" -> {
				Approaches.take(player, Items.EMERALD, MENDING_SCROLL_EMERALDS);
				player.getInventory().placeItemBackInInventory(new ItemStack(Arcana.SCROLL_MENDING), net.minecraft.util.Prediction.SERVER_ONLY);
				Speech.tell(level, healer, player, Component.translatable("talk.jaymoskingdoms.healer.sold"), Speech.GOLD);
			}
			case "scroll_restoration" -> {
				Approaches.take(player, Items.EMERALD, RESTORATION_SCROLL_EMERALDS);
				player.setAttached(RESTORATION_BOUGHT, level.getGameTime());
				player.getInventory().placeItemBackInInventory(new ItemStack(Arcana.SCROLL_RESTORATION), net.minecraft.util.Prediction.SERVER_ONLY);
				Speech.tell(level, healer, player, Component.translatable("talk.jaymoskingdoms.healer.sold_restoration"), Speech.GOLD);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	private static boolean hurt(ServerPlayer player) {
		return player.getHealth() < player.getMaxHealth() - 0.01F;
	}

	private static boolean restorationDue(ServerPlayer player) {
		Long last = player.getAttached(RESTORATION_BOUGHT);
		long now = player.level().getGameTime();
		return last == null || now - last >= RESTORATION_EVERY || now < last;
	}

	/** What a court wizard's realm thinks of {@code player} (0 for a hermit or a cleric). */
	private static int reputation(ServerPlayer player, LivingEntity healer) {
		return healer instanceof Worker w && w.factionId() >= 0 ? Reputation.get(player, w.factionId()) : 0;
	}

	private static boolean friendOfRealm(ServerPlayer player, LivingEntity healer) {
		return reputation(player, healer) >= FRIEND_REP;
	}

	// ---- mercy and thanks -------------------------------------------------------------------------------------------

	/**
	 * {@code player} healed {@code healed} ({@code revived}: brought them round from downed): a villager or one of the
	 * folk warms to them and remembers it (not more than once in {@link #CREDIT_EVERY}); bringing a downed foe round is
	 * mercy, and the devout and the kindhearted who see it think the better of them.
	 */
	static void credit(ServerLevel level, ServerPlayer player, LivingEntity healed, boolean revived) {
		if (healed == player || !Recollections.rememberer(healed) && !revived) {
			return;
		}
		long now = level.getGameTime();
		String key = player.getUUID() + "/" + healed.getUUID();
		Long last = CREDITED.get(key);
		if (last != null && now - last < CREDIT_EVERY && !revived) {
			return;
		}
		CREDITED.put(key, now);
		if (Recollections.rememberer(healed)) {
			McaCompat.hearts(healed, player, revived ? REKINDLE_HEARTS : MEND_HEARTS);
			Recollections.remember(healed, player, revived ? Recollections.Kind.SAVED : Recollections.Kind.MENDED, Component.empty());
		}
		if (revived && (healed instanceof Enemy || healed instanceof Folk f && (Barbarians.isBarbarian(f) || Barbarians.isRaider(f)))) {
			for (LivingEntity w : level.getEntitiesOfClass(LivingEntity.class, healed.getBoundingBox().inflate(MERCY_SEEN),
				e -> e != healed && e.isAlive() && Recollections.rememberer(e)
					&& (Temperaments.is(e, Temperament.DEVOUT) || Temperaments.is(e, Temperament.KINDHEARTED)))) {
				McaCompat.hearts(w, player, MEND_HEARTS);
				Recollections.remember(w, player, Recollections.Kind.SAW_DEED, Component.translatable("recall.jaymoskingdoms.deed.mercy"));
			}
		}
	}

	// ---- the look ---------------------------------------------------------------------------------------------------

	static void beam(ServerLevel level, Entity from, Entity to, ParticleOptions particle) {
		Vec3 a = from.getEyePosition().add(0.0, -0.3, 0.0), b = to.getBoundingBox().getCenter();
		int n = Math.max(2, (int)(a.distanceTo(b) * 3.0));
		for (int i = 0; i <= n; i++) {
			Vec3 p = a.lerp(b, i / (double)n);
			level.sendParticles(particle, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	static void ring(ServerLevel level, Vec3 centre, double radius, ParticleOptions particle, int points) {
		for (int i = 0; i < points; i++) {
			double a = i * Math.PI * 2.0 / points;
			level.sendParticles(particle, centre.x + Math.cos(a) * radius, centre.y, centre.z + Math.sin(a) * radius, 1, 0.0, 0.05, 0.0, 0.0);
		}
	}
}
