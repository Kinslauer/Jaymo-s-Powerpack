package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Carry;
import com.jaymospowers.api.Power;
import com.jaymospowers.core.Crawl;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * Wounds, phase 2 (0.14.1): the downed state, for everyone. Andy (2026-10-19): "remove our downed system for a universal
 * one". Kingdoms' guards were the first to go down instead of dying (Kingdoms 0.7.73, every villager from 0.7.84, the
 * food revive from 0.7.93); that system now lives here, and Kingdoms only reads it (the scoreboard tag
 * {@link #DOWNED_TAG}) for what only it knows: villagers tending the downed, disarming a downed guard, and its folk
 * skipping the downed in their routines.
 * <p>
 * <b>Who goes down</b> ({@link #downable}): players (not in creative or spectator), villagers, anything carrying
 * {@link #DOWNABLE_TAG} (Kingdoms marks its folk), and tamed animals. Monsters, wild animals and bosses die; so does
 * anything that loses its head, and anything killed by what ignores invulnerability (the void, {@code /kill}).
 * <p>
 * <b>Down:</b> at {@link #DOWNED_HEALTH}, head and body given a little back ({@link Wounds#settle}). Mobs lose their AI
 * ({@code DownedMobMixin}); some ({@link #CRAWLER_CHANCE}) drag themselves away from whoever felled them. Players are held
 * in a slow crawl ({@link Crawl}) with their hands empty of use: no attacking, using, building or breaking. (0.15.21) Nor
 * powers: whatever was running ends as they fall ({@link Power#onDowned}), their keys are dead (a silencer, and the client
 * lets go of every key but moving and sneak: {@code DownedKeysMixin}), and nothing heals them ({@code DownedHealMixin}). Fire, falls
 * and mobs still hurt, out of what's left, and can kill (that death counts). A player's blow is a wound instead
 * ({@link #MAX_WOUNDS}th kills); once a player has hit one, a mob or tamed animal won't get up by itself
 * ({@link #RECOVER_TICKS}) and bleeds out {@link #BLEED_OUT_TICKS} after the last hit.
 * <p>
 * <b>Players don't bleed out into a respawn</b> (Andy: "Lie until roused or give up"): after {@link #BLEED_OUT_TICKS}
 * they pass out and lie still ({@link #OUT}) until someone brings them round, or they hold sneak for
 * {@link #GIVE_UP_TICKS} to give up (a death credited to whoever felled them). Logging out keeps it.
 * <p>
 * <b>Bringing one round</b> (Kingdoms' 0.7.93 revive, for everyone): sneak + hold use on a downed one. A tap picks them up
 * ({@link Carry}); held for {@link #REVIVE_TICKS}, it gets them up, for the least filling food you carry (a tamed animal
 * takes one of its own treats instead, {@link #TREATS}). Kingdoms' villagers tend the downed too, and ask for the
 * getting up with the tag {@link #REVIVE_TAG}.
 */
public final class Downed {
	/** A downed one's health: what mobs, fire and falls have left to take. */
	public static final float DOWNED_HEALTH = 6.0F;
	/** How long a downed mob or tamed animal no player has hit lies there before getting back up (ticks), and the health it, or anyone brought round, gets up with (share of its most). */
	public static final int RECOVER_TICKS = 1200;
	public static final float RECOVER_HEALTH = 0.3F;
	/** How long after a player's last hit a downed mob bleeds out (ticks), and how long a downed player crawls before passing out. */
	public static final int BLEED_OUT_TICKS = 2400;
	/** The player's hit that kills a downed one outright. */
	public static final int MAX_WOUNDS = 10;
	/** The share of downed mobs who drag themselves along; the rest lie still. */
	public static final float CRAWLER_CHANCE = 0.5F;
	/** A crawling mob's pace, as a share of its walking speed, and how far it gets from whoever felled it before it stops. */
	public static final double CRAWL_SPEED = 0.3, CRAWL_FROM = 14.0;
	/** A downed player's crawl, slower than a crawl (share of speed lost), and passed out: none at all. */
	public static final double PLAYER_CRAWL_SLOW = -0.5, PASSED_OUT_SLOW = -1.0;
	/** How long a downed player holds sneak to give up (ticks). */
	public static final int GIVE_UP_TICKS = 100;
	/** How often a downed player is reminded they can give up (ticks), and the give-up bar's length. */
	static final int HINT_TICKS = 20, GIVE_UP_BAR = 12;
	/** (0.7.93) A revive: how long sneak + use is held to get them up (ticks), and what makes it a hold, not a tap (a pick-up). */
	public static final int REVIVE_TICKS = 60, TAP_TICKS = 4;
	/** Holding use repeats every 4 ticks: no repeat for this long and you've let go. */
	static final int REVIVE_GRACE = 8;
	/** How far past your reach you may lean while reviving, and how often your hands work at it (a swing, a sound). */
	static final double REVIVE_SLACK = 0.5;
	static final int REVIVE_WORK_TICKS = 15;
	/** The revive bar's length. */
	static final int REVIVE_BAR = 12;

	/** The scoreboard tag on everyone downed (what Kingdoms reads: it never compiles against Powers). */
	public static final String DOWNED_TAG = "jaymospowers_downed";
	/** The scoreboard tag that lets anything else go down (Kingdoms puts it on its folk when they're hurt). */
	public static final String DOWNABLE_TAG = "jaymospowers_downable";
	/** Put on a downed one by someone else (Kingdoms' tending villagers): its next tick here gets it up. */
	public static final String REVIVE_TAG = "jaymospowers_revive";

	/**
	 * Someone downed: the game time they went down, shifted up one bit, the low bit set for a mob that crawls. Saved (they
	 * stay down through a reload or a relog) and synced to everyone (who draw them on the ground).
	 */
	public static final AttachmentType<Long> DOWNED = AttachmentRegistry.create(JaymosPowers.id("downed"),
		builder -> builder.persistent(Codec.LONG).syncWith(ByteBufCodecs.VAR_LONG.cast(), AttachmentSyncPredicate.all()));
	/** A downed one players have hit: when the last hit landed, shifted up 4 bits, with how many hits in the low bits. Saved. */
	public static final AttachmentType<Long> WOUNDS = AttachmentRegistry.create(JaymosPowers.id("downed_wounds"),
		builder -> builder.persistent(Codec.LONG));
	/** A downed player who has passed out (lies still, the screen darker). Saved, synced to everyone. */
	public static final AttachmentType<Boolean> OUT = AttachmentRegistry.create(JaymosPowers.id("downed_out"),
		builder -> builder.persistent(Codec.BOOL).syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/**
	 * (0.14.4) Knocked out for a while (too much poppy): when they come round (game time), shifted up one bit, the low bit set
	 * if they were down before it (they come round still down). Saved, server side.
	 */
	public static final AttachmentType<Long> KNOCKED = AttachmentRegistry.create(JaymosPowers.id("downed_knocked"),
		builder -> builder.persistent(Codec.LONG));

	/** A downed player's slow crawl. */
	static final Identifier SLOW_ID = JaymosPowers.id("downed_slow");
	/** Revive and the downed's empty hands run before every other mod's right-click on them (trades, quests, pockets, carrying). */
	static final Identifier FIRST = JaymosPowers.id("downed_first");
	/** Going down is the last say on a death (every other mod's save comes first), and on a right-click on the downed. */
	static final Identifier LAST = JaymosPowers.id("downed_last");

	/**
	 * What a tamed animal is brought round with, by its type's name: its own taming and breeding food. Anything tamed not
	 * here takes any food.
	 */
	static final Map<String, Set<String>> TREATS = Map.of(
		"wolf", Set.of("bone", "beef", "cooked_beef", "porkchop", "cooked_porkchop", "chicken", "cooked_chicken", "mutton", "cooked_mutton",
			"rabbit", "cooked_rabbit", "rotten_flesh"),
		"cat", Set.of("cod", "salmon"),
		"ocelot", Set.of("cod", "salmon"),
		"parrot", Set.of("wheat_seeds", "melon_seeds", "pumpkin_seeds", "beetroot_seeds", "torchflower_seeds", "pitcher_pod"),
		"horse", Set.of("golden_carrot", "golden_apple", "apple", "hay_block", "carrot", "wheat", "sugar"),
		"donkey", Set.of("golden_carrot", "golden_apple", "apple", "hay_block", "carrot", "wheat", "sugar"),
		"mule", Set.of("golden_carrot", "golden_apple", "apple", "hay_block", "carrot", "wheat", "sugar"),
		"llama", Set.of("hay_block", "wheat"),
		"trader_llama", Set.of("hay_block", "wheat"),
		"camel", Set.of("cactus"));

	/** The blow that put each down: whose kill it is when it dies (not saved; after a reload it dies unclaimed). */
	private static final Map<LivingEntity, DamageSource> FELLED_BY = new WeakHashMap<>();
	/** Those being finished off just now (their death let through as it comes). */
	private static final Set<LivingEntity> FINISHING = Collections.newSetFromMap(new WeakHashMap<>());
	/** A downed player → how long they've held sneak to give up (ticks). */
	private static final Map<UUID, Integer> GIVING_UP = new HashMap<>();

	/** (0.7.93) A player's sneak + use held on a downed one: since when, when use last came, how full the bar is, and whether it's over (use still swallowed till let go). */
	private static final class Revive {
		final LivingEntity body;
		final long start;
		long held;
		int ticks;
		boolean begun, over;

		Revive(LivingEntity body, long start) {
			this.body = body;
			this.start = start;
			this.held = start;
		}
	}

	/** Player → their revive. Not saved. */
	private static final Map<UUID, Revive> REVIVING = new HashMap<>();

	private Downed() {
	}

	static void register() {
		// last: every other save (Darktide's shade, Xenoform, a Z Fighter's spar, Kingdoms' Last Stand...) goes first
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(Event.DEFAULT_PHASE, LAST);
		ServerLivingEntityEvents.ALLOW_DEATH.register(LAST, (entity, source, amount) -> {
			if (!(entity.level() instanceof ServerLevel level) || FINISHING.contains(entity)) {
				return true;
			}
			if (isDowned(entity)) {
				// (a mob, fire, a fall, the void: they die as anyone would, body and all; no second downing)
				forget(entity);
				return true;
			}
			if (!downable(entity) || !spared(entity, source)) {
				return true;
			}
			goDown(level, entity, source, false);
			return false;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!isDowned(entity) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !(source.getEntity() instanceof Player)) {
				// (mobs, fire, falls: real harm, out of what's left)
				return true;
			}
			// a player's blow (or arrow): a wound, the tenth kills
			if (entity.level() instanceof ServerLevel level) {
				wound(level, entity, source);
			}
			return false;
		});
		UseEntityCallback.EVENT.addPhaseOrdering(FIRST, Event.DEFAULT_PHASE);
		UseEntityCallback.EVENT.register(FIRST, Downed::onUseEntity);
		// and whatever right-click on a downed mob nobody else took (Kingdoms takes a guard's weapons) does nothing: no
		// trading with a downed villager, no saddling a downed horse
		UseEntityCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, LAST);
		UseEntityCallback.EVENT.register(LAST, (player, world, hand, entity, hit) ->
			entity instanceof Mob && isDowned(entity) ? InteractionResult.SUCCESS : InteractionResult.PASS);
		// a downed player's hands are gone slack: no attacking, using, building or breaking
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> isDowned(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> isDowned(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> isDowned(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) -> isDowned(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !isDowned(player));
		// (0.15.21) and no powers: every check that asks whether a player's power keys are live says no
		Powers.addSilencer(Downed::isDowned);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			players(server);
			revives(server);
		});
		// the body that comes back is whole and on its feet (out of the End, still down if they went in down)
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive) {
				forget(newPlayer);
			} else if (!isDowned(newPlayer)) {
				newPlayer.removeTag(DOWNED_TAG);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			REVIVING.clear();
			GIVING_UP.clear();
		});
	}

	// ---- queries (both sides: the attachment is synced) ------------------------------------------------------------------

	public static boolean isDowned(Entity entity) {
		return entity.hasAttached(DOWNED);
	}

	/** Whether this downed mob drags itself along (rather than lying still). */
	public static boolean crawls(Entity entity) {
		Long downed = entity.getAttached(DOWNED);
		return downed != null && (downed & 1L) != 0L;
	}

	/** The game time it went down. */
	public static long since(Entity entity) {
		Long downed = entity.getAttached(DOWNED);
		return downed == null ? 0L : downed >> 1;
	}

	/** A downed player who has passed out. */
	public static boolean passedOut(Entity entity) {
		return entity.hasAttached(OUT);
	}

	/**
	 * Whether the blow that would kill {@code entity} downs it instead: a player (not creative or spectator), a villager,
	 * one of Kingdoms' folk ({@link #DOWNABLE_TAG}), or a tamed animal. Never a boss.
	 */
	public static boolean downable(LivingEntity entity) {
		if (entity instanceof Player player) {
			return !player.isCreative() && !player.isSpectator();
		}
		if (!(entity instanceof Mob) || BodyPlan.of(entity) == null) {
			return false;
		}
		if (entity instanceof Villager || entity.entityTags().contains(DOWNABLE_TAG)) {
			return true;
		}
		return entity instanceof TamableAnimal tame && tame.isTame() || entity instanceof AbstractHorse horse && horse.isTamed();
	}

	/**
	 * Whether this blow leaves anything to get up: not one that ignores invulnerability, nor a beheading (or a body already
	 * headless). And a totem in hand is left to do its work (this event comes before it).
	 */
	private static boolean spared(LivingEntity entity, DamageSource source) {
		return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(Dismember.DECAPITATION) && !Dismember.headless(entity)
			&& !entity.entityTags().contains(Dismember.tag(Dismember.HEAD))
			&& !entity.getMainHandItem().is(Items.TOTEM_OF_UNDYING) && !entity.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
	}

	// ---- going down, getting up ------------------------------------------------------------------------------------------

	/** Down they go; {@code source}: what felled them (null: nothing did, a knock-out); {@code keepHealth}: (0.14.4) a knock-out takes none. */
	private static void goDown(ServerLevel level, LivingEntity entity, @Nullable DamageSource source, boolean keepHealth) {
		boolean crawler = !(entity instanceof Player) && level.getRandom().nextFloat() < CRAWLER_CHANCE;
		if (!keepHealth) {
			entity.setHealth(Math.min(DOWNED_HEALTH, entity.getMaxHealth()));
		}
		entity.setAttached(DOWNED, level.getGameTime() << 1 | (crawler ? 1L : 0L));
		entity.removeAttached(WOUNDS);
		entity.removeAttached(OUT);
		entity.addTag(DOWNED_TAG);
		entity.removeTag(REVIVE_TAG);
		if (source != null) {
			FELLED_BY.put(entity, source);
		} else {
			FELLED_BY.remove(entity);
		}
		// head and body hold on to a little (the rest is spread as Wounds keeps it)
		Wounds.settle(entity);
		entity.stopUsingItem();
		if (entity.isSleeping()) {
			entity.stopSleeping();
		}
		entity.stopRiding();
		entity.ejectPassengers();
		entity.clearFire();
		if (entity instanceof Mob mob) {
			mob.setTarget(null);
			mob.setAggressive(false);
			mob.getNavigation().stop();
		}
		if (entity instanceof ServerPlayer player) {
			// whatever they carried falls from their arms
			Carry.slip(player);
			player.setSprinting(false);
			// (0.15.21) whatever power was running ends: a flight, a hold, a grab
			Power power = Powers.of(player);
			if (power != null) {
				power.onDowned(player);
			}
			GIVING_UP.remove(player.getUUID());
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.down").withStyle(ChatFormatting.RED));
		}
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_BIG_FALL, SoundSource.NEUTRAL, 0.9F, 0.8F);
	}

	/** No longer down (up again, dying, or respawned): its marks gone, a player's crawl and slowness with them. */
	private static void forget(LivingEntity entity) {
		FELLED_BY.remove(entity);
		boolean was = entity.hasAttached(DOWNED);
		entity.removeAttached(DOWNED);
		entity.removeAttached(WOUNDS);
		entity.removeAttached(OUT);
		entity.removeAttached(KNOCKED);
		entity.removeTag(DOWNED_TAG);
		entity.removeTag(REVIVE_TAG);
		if (entity instanceof Player player) {
			GIVING_UP.remove(player.getUUID());
			AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
			if (speed != null) {
				speed.removeModifier(SLOW_ID);
			}
			if (was) {
				// (the crawl was the downed state's; they get up off the ground)
				player.removeAttached(Crawl.CRAWLING);
			}
		}
	}

	/** Brought round (or left long enough): back on its feet, hurt, and a mob back to its post (its AI takes up again). */
	public static void getUp(ServerLevel level, LivingEntity entity) {
		if (!isDowned(entity)) {
			return;
		}
		if (Carry.carrierOf(entity) instanceof ServerPlayer carrier) {
			// (out of the arms that held them)
			Carry.slip(carrier);
		}
		forget(entity);
		entity.stopRiding();
		entity.setHealth(Math.max(entity.getHealth(), Math.max(1.0F, entity.getMaxHealth() * RECOVER_HEALTH)));
		Wounds.reconcile(entity);
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.NEUTRAL, 0.8F, 0.9F);
		if (entity instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.up").withStyle(ChatFormatting.GREEN));
		}
	}

	/**
	 * (0.14.4) Brings a downed one round, or knocks them out: {@code passedOut} false, they come round (a passed-out player
	 * crawls again, their bleeding-out clock started afresh; a mob drags itself along); true, they pass out where they lie.
	 * Nothing for one who isn't down.
	 */
	public static void wake(LivingEntity entity, boolean passedOut) {
		if (!isDowned(entity) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		if (passedOut) {
			entity.setAttached(OUT, true);
			return;
		}
		boolean wasOut = passedOut(entity);
		entity.removeAttached(OUT);
		entity.removeAttached(KNOCKED);
		if (!(entity instanceof Player)) {
			// (a mob drags itself along from here; its own clock runs on)
			entity.setAttached(DOWNED, since(entity) << 1 | 1L);
		} else if (wasOut) {
			// (a player who'd passed out crawls again, a fresh while before they pass out again)
			entity.setAttached(DOWNED, level.getGameTime() << 1);
		}
		if (wasOut && entity instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.come_round").withStyle(ChatFormatting.YELLOW));
		}
	}

	/**
	 * (0.14.4) Smelling salts: a downed one is up on the spot, with only {@code vital} health points (head and body share
	 * them; the rest is as it was). True if they were down.
	 */
	public static boolean rouse(ServerLevel level, LivingEntity entity, float vital) {
		if (!isDowned(entity)) {
			return false;
		}
		if (Carry.carrierOf(entity) instanceof ServerPlayer carrier) {
			Carry.slip(carrier);
		}
		forget(entity);
		entity.stopRiding();
		entity.setHealth(Math.min(entity.getMaxHealth(), Math.max(entity.getHealth(), vital)));
		// (head and body hold on to a little: the rest follows the health)
		Wounds.settle(entity);
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.WARDEN_SNIFF, SoundSource.NEUTRAL, 0.8F, 1.6F);
		if (entity instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.roused").withStyle(ChatFormatting.GREEN));
		}
		return true;
	}

	/**
	 * (0.14.4) Knocked out for {@code ticks} (too much poppy): one who's up goes down where they stand (no health lost) and
	 * passes out, and gets up again by themselves after; one already down passes out, and comes round still down. Only
	 * those who can go down ({@link #downable}) or already are. True if they were knocked out.
	 */
	public static boolean knockOut(ServerLevel level, LivingEntity entity, int ticks) {
		boolean was = isDowned(entity);
		if (!was && !downable(entity)) {
			return false;
		}
		if (!was) {
			goDown(level, entity, null, true);
		}
		// (out cold: no crawling away)
		entity.setAttached(DOWNED, since(entity) << 1);
		entity.setAttached(OUT, true);
		entity.setAttached(KNOCKED, (level.getGameTime() + ticks) << 1 | (was ? 1L : 0L));
		if (entity instanceof ServerPlayer player) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.knocked_out").withStyle(ChatFormatting.DARK_PURPLE));
		}
		return true;
	}

	/** (0.14.4) A knock-out's end: they come round (still down if they were before it, else up). True if it ended just now. */
	private static boolean comeRound(ServerLevel level, LivingEntity entity) {
		Long knocked = entity.getAttached(KNOCKED);
		if (knocked == null || level.getGameTime() < knocked >> 1) {
			return false;
		}
		entity.removeAttached(KNOCKED);
		if ((knocked & 1L) != 0L) {
			wake(entity, false);
		} else {
			// (a knock-out from standing is no rest cure: they get up as hurt as they went down)
			float health = entity.getHealth();
			getUp(level, entity);
			entity.setHealth(health);
			Wounds.reconcile(entity);
		}
		return true;
	}

	/**
	 * Its death, at last, by its wounds and credited to whoever felled it (or last struck it). It dies as anyone would:
	 * its body (Kitchen's corpse) and what it had left behind.
	 */
	private static void finish(ServerLevel level, LivingEntity entity) {
		if (entity.isRemoved() || entity.isDeadOrDying()) {
			return;
		}
		DamageSource felled = FELLED_BY.get(entity);
		Entity by = felled == null ? null : felled.getEntity();
		forget(entity);
		DamageSource source = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(Wounds.WOUNDS_DAMAGE), by);
		FINISHING.add(entity);
		// (and Wounds doesn't route the blow, should something else cancel this death)
		Wounds.FINISHING.add(entity);
		try {
			entity.setInvulnerableTime(0);
			entity.hurtServer(level, source, Float.MAX_VALUE);
		} finally {
			FINISHING.remove(entity);
			Wounds.FINISHING.remove(entity);
		}
	}

	/** A player hit a downed one: one more wound; the {@link #MAX_WOUNDS}th is its death. */
	private static void wound(ServerLevel level, LivingEntity entity, DamageSource source) {
		Long wounds = entity.getAttached(WOUNDS);
		int count = (wounds == null ? 0 : (int) (wounds & 0xF)) + 1;
		FELLED_BY.put(entity, source);
		if (count >= MAX_WOUNDS) {
			finish(level, entity);
			return;
		}
		entity.setAttached(WOUNDS, level.getGameTime() << 4 | count);
		level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, entity.getX(), entity.getY() + 0.3, entity.getZ(), 3, 0.3, 0.1, 0.3, 0.1);
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_HURT, SoundSource.NEUTRAL, 0.7F, 0.8F);
	}

	// ---- downed mobs ------------------------------------------------------------------------------------------------------

	/**
	 * A downed mob's tick, in place of its AI: it gets up or bleeds out, and a crawler hauls itself away from whoever
	 * felled it (while they're near). Called by {@code DownedMobMixin} on the server.
	 */
	public static void step(Mob mob) {
		if (!(mob.level() instanceof ServerLevel level)) {
			return;
		}
		// (0.14.4) Kingdoms' villagers bandaged it
		Bleeding.tended(mob);
		if (comeRound(level, mob)) {
			return;
		}
		if (mob.entityTags().contains(REVIVE_TAG)) {
			// (tended back on its feet by someone else's hands: Kingdoms' villagers)
			getUp(level, mob);
			return;
		}
		if (!mob.entityTags().contains(DOWNED_TAG)) {
			mob.addTag(DOWNED_TAG);
		}
		long now = level.getGameTime();
		Long wounds = mob.getAttached(WOUNDS);
		if (wounds == null && now - since(mob) >= RECOVER_TICKS && !mob.hasAttached(KNOCKED)) {
			getUp(level, mob);
			return;
		}
		if (wounds != null && now - (wounds >> 4) >= BLEED_OUT_TICKS) {
			finish(level, mob);
			return;
		}
		mob.setTarget(null);
		mob.getNavigation().stop();
		mob.setJumping(false);
		mob.setXxa(0.0F);
		mob.setSpeed(0.0F);
		if (Carry.isCarried(mob)) {
			return;
		}
		DamageSource source = FELLED_BY.get(mob);
		Entity from = source == null ? null : source.getEntity();
		if (crawls(mob) && from != null && from.isAlive() && from.level() == level && mob.distanceToSqr(from) < CRAWL_FROM * CRAWL_FROM) {
			float yaw = (float) (Mth.atan2(mob.getZ() - from.getZ(), mob.getX() - from.getX()) * Mth.RAD_TO_DEG) - 90.0F;
			mob.setYRot(yaw);
			mob.setYHeadRot(yaw);
			mob.yBodyRot = yaw;
			mob.setSpeed((float) (mob.getAttributeValue(Attributes.MOVEMENT_SPEED) * CRAWL_SPEED));
		}
	}

	// ---- downed players ---------------------------------------------------------------------------------------------------

	private static void players(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (isDowned(player) && player.isAlive()) {
				tickPlayer(player.level(), player);
			}
		}
	}

	/** A downed player's tick: the crawl held, passing out, giving up, and the getting up someone else asked for. */
	private static void tickPlayer(ServerLevel level, ServerPlayer player) {
		// (0.14.4) Kingdoms' villagers bandaged them
		Bleeding.tended(player);
		if (player.isCreative() || player.isSpectator() || player.entityTags().contains(REVIVE_TAG)) {
			getUp(level, player);
			return;
		}
		// (0.14.4) a knock-out over: up, or still down if they were before it
		if (comeRound(level, player) && !isDowned(player)) {
			return;
		}
		if (!player.entityTags().contains(DOWNED_TAG)) {
			// (the tag Kingdoms reads goes with the state, wherever the state went)
			player.addTag(DOWNED_TAG);
		}
		long now = level.getGameTime();
		boolean out = passedOut(player);
		if (!out && now - since(player) >= BLEED_OUT_TICKS) {
			// (Andy: "Lie until roused or give up": no respawn for bleeding out)
			player.setAttached(OUT, true);
			out = true;
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.6F, 0.5F);
		}
		// flat on the ground (but limp in the arms of whoever carries them)
		if (Carry.isCarried(player)) {
			if (player.hasAttached(Crawl.CRAWLING)) {
				player.removeAttached(Crawl.CRAWLING);
			}
		} else if (!player.hasAttached(Crawl.CRAWLING) && Crawl.canCrawl(player)) {
			player.setAttached(Crawl.CRAWLING, true);
		}
		player.setSprinting(false);
		AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
		double slow = out ? PASSED_OUT_SLOW : PLAYER_CRAWL_SLOW;
		if (speed != null) {
			AttributeModifier current = speed.getModifier(SLOW_ID);
			if (current == null || current.amount() != slow) {
				speed.addOrUpdateTransientModifier(new AttributeModifier(SLOW_ID, slow, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			}
		}
		if (player.hasAttached(KNOCKED)) {
			// (0.14.4) knocked out, not dying: nothing to give up, they'll come round
			GIVING_UP.remove(player.getUUID());
			if (now % HINT_TICKS == 0) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.knocked_out").withStyle(ChatFormatting.DARK_PURPLE));
			}
			return;
		}
		// giving up: sneak held (server side, from the player's input)
		int held = player.isShiftKeyDown() ? GIVING_UP.merge(player.getUUID(), 1, Integer::sum) : 0;
		if (held == 0) {
			GIVING_UP.remove(player.getUUID());
		}
		if (held >= GIVE_UP_TICKS) {
			GIVING_UP.remove(player.getUUID());
			finish(level, player);
			return;
		}
		if (held > 0 ? held % 2 == 0 : now % HINT_TICKS == 0) {
			int filled = Math.round(GIVE_UP_BAR * held / (float) GIVE_UP_TICKS);
			String bar = "|".repeat(filled) + ".".repeat(GIVE_UP_BAR - filled);
			player.sendOverlayMessage(Component.translatable(out ? "message.jaymospowers.downed.give_up_out" : "message.jaymospowers.downed.give_up", bar)
				.withStyle(held > 0 ? ChatFormatting.RED : ChatFormatting.GRAY));
		}
	}

	// ---- bringing one round -----------------------------------------------------------------------------------------------

	private static InteractionResult onUseEntity(Player player, net.minecraft.world.level.Level world, InteractionHand hand, Entity entity,
		net.minecraft.world.phys.@Nullable EntityHitResult hit) {
		if (isDowned(player)) {
			return InteractionResult.FAIL;
		}
		if (!world.isClientSide() && Treatment.claimsDowned(player, hand, entity)) {
			// (0.14.4) medicine in hand that can help them: Treatment's, not a revive
			return InteractionResult.PASS;
		}
		Revive held = world.isClientSide() || hand != InteractionHand.MAIN_HAND ? null : REVIVING.get(player.getUUID());
		if (held != null && held.body == entity) {
			// still holding use: the bar keeps filling, or, once it's over (they're up), nothing more till let go
			held.held = world.getGameTime();
			return InteractionResult.SUCCESS;
		}
		if (!(entity instanceof LivingEntity body) || !isDowned(body) || player.isSpectator() || !player.isShiftKeyDown()) {
			// (a plain right-click on a downed one is left to others: Kingdoms takes a guard's weapons)
			return InteractionResult.PASS;
		}
		if (!world.isClientSide() && hand == InteractionHand.MAIN_HAND) {
			// a tap picks them up once let go; held, it's a revive
			REVIVING.put(player.getUUID(), new Revive(body, world.getGameTime()));
		}
		return InteractionResult.SUCCESS;
	}

	/** (0.7.93) Each player's sneak + use on a downed one: a tap picks them up, a hold fills the bar and gets them up. */
	private static void revives(MinecraftServer server) {
		if (REVIVING.isEmpty()) {
			return;
		}
		for (Iterator<Map.Entry<UUID, Revive>> it = REVIVING.entrySet().iterator(); it.hasNext();) {
			var e = it.next();
			Revive r = e.getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			LivingEntity body = r.body;
			if (player == null || !(player.level() instanceof ServerLevel level) || !player.isAlive() || isDowned(player) || !body.isAlive()
				|| body.isRemoved() || body.level() != level) {
				it.remove();
				continue;
			}
			if (!isDowned(body)) {
				// (up already, by someone else's hand)
				r.over = true;
			}
			long now = level.getGameTime();
			if (now - r.held > REVIVE_GRACE) {
				// let go
				it.remove();
				if (r.held - r.start < TAP_TICKS && isDowned(body) && player.isWithinEntityInteractionRange(body, REVIVE_SLACK)) {
					Carry.pickUpDowned(player, body);
				} else if (r.begun && !r.over) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.revive_stopped", body.getDisplayName()).withStyle(ChatFormatting.GRAY));
				}
				continue;
			}
			if (r.over || r.held - r.start < TAP_TICKS) {
				continue;
			}
			if (!r.begun) {
				r.begun = true;
				if (cost(player, body) < 0) {
					r.over = true;
					player.sendOverlayMessage(Component.translatable(tamed(body) ? "message.jaymospowers.downed.no_treat" : "message.jaymospowers.downed.no_food",
						body.getDisplayName()).withStyle(ChatFormatting.GRAY));
					continue;
				}
			}
			if (!player.isShiftKeyDown() || Carry.isCarried(body) || !player.isWithinEntityInteractionRange(body, REVIVE_SLACK)) {
				r.over = true;
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.revive_stopped", body.getDisplayName()).withStyle(ChatFormatting.GRAY));
				continue;
			}
			if (++r.ticks % REVIVE_WORK_TICKS == 0) {
				// kneeling at their side, hands at work
				player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
				level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.5F,
					0.8F + level.getRandom().nextFloat() * 0.4F);
				level.sendParticles(ParticleTypes.HEART, body.getX(), body.getY() + 0.6, body.getZ(), 1, 0.3, 0.1, 0.3, 0.0);
			}
			if (r.ticks < REVIVE_TICKS) {
				if (r.ticks % 2 == 0) {
					int filled = Math.round(REVIVE_BAR * r.ticks / (float) REVIVE_TICKS);
					String bar = "|".repeat(filled) + ".".repeat(REVIVE_BAR - filled);
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.reviving", body.getDisplayName(), bar).withStyle(ChatFormatting.GRAY));
				}
				continue;
			}
			r.over = true;
			int slot = cost(player, body);
			if (slot < 0) {
				player.sendOverlayMessage(Component.translatable(tamed(body) ? "message.jaymospowers.downed.no_treat" : "message.jaymospowers.downed.no_food",
					body.getDisplayName()).withStyle(ChatFormatting.GRAY));
				continue;
			}
			ItemStack stack = player.getInventory().getItem(slot);
			Component eaten = stack.getHoverName();
			stack.consume(1, player);
			player.getInventory().setChanged();
			getUp(level, body);
			level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.8F, 1.0F);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, body.getX(), body.getY() + 0.8, body.getZ(), 10, 0.4, 0.4, 0.4, 0.0);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.revived", body.getDisplayName(), eaten).withStyle(ChatFormatting.GREEN));
		}
	}

	private static boolean tamed(LivingEntity body) {
		return body instanceof TamableAnimal || body instanceof AbstractHorse;
	}

	/** The inventory slot of what bringing {@code body} round costs {@code player}: a tamed animal's own treat, else food. -1 if none. */
	private static int cost(Player player, LivingEntity body) {
		if (tamed(body)) {
			Set<String> treats = TREATS.get(BuiltInRegistries.ENTITY_TYPE.getKey(body.getType()).getPath());
			if (treats != null) {
				for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
					ItemStack stack = player.getInventory().getItem(slot);
					if (!stack.isEmpty() && treats.contains(BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath())) {
						return slot;
					}
				}
				return -1;
			}
		}
		return food(player);
	}

	/** (0.7.93) The inventory slot of the least filling thing {@code player} has to eat (the first of equals), or -1. */
	private static int food(Player player) {
		int best = -1;
		FoodProperties least = null;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			FoodProperties food = player.getInventory().getItem(slot).get(DataComponents.FOOD);
			if (food != null && (least == null || food.nutrition() < least.nutrition()
				|| food.nutrition() == least.nutrition() && food.saturation() < least.saturation())) {
				best = slot;
				least = food;
			}
		}
		return best;
	}
}
