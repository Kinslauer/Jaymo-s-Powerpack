package com.jaymospowers.core.wounds;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Carry;
import com.jaymospowers.api.Power;
import com.jaymospowers.core.Crawl;
import com.jaymospowers.core.Dismember;
import com.jaymospowers.core.Powers;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
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
 * <b>Who goes down</b> ({@link #downable}): players (not in creative or spectator), villagers, (0.15.41) every humanoid
 * (monsters too: zombies, skeletons, illagers), anything carrying {@link #DOWNABLE_TAG} (Kingdoms marks its folk), and
 * tamed animals. Other monsters, wild animals and bosses die; so does
 * anything that loses its head, and anything killed by what ignores invulnerability (the void, {@code /kill}).
 * <p>
 * <b>Down:</b> at {@link #DOWNED_HEALTH} (a player at {@link #DOWNED_PLAYER_HEALTH}), head and body given a little back ({@link Wounds#settle}). Mobs lose their AI
 * ({@code DownedMobMixin}); some ({@link #CRAWLER_CHANCE}, most people) drag themselves away from the fight or to a friend
 * who'll bind them, and all groan now and then ({@link DownedCrawl}). Players are held
 * in a slow crawl ({@link Crawl}) with their hands empty of use: no attacking, using, building or breaking. (0.15.31) Nor
 * powers: whatever was running ends as they fall ({@link Power#onDowned}), their keys are dead (a silencer, and the client
 * lets go of every key but moving and sneak: {@code DownedKeysMixin}), and nothing heals them ({@code DownedHealMixin}). Fire, falls
 * and mobs still hurt, out of what's left, and can kill (that death counts); so does a player's blow on a downed player.
 * A player's blow on a downed mob is a wound instead ({@link #MAX_WOUNDS}th kills); once a player has hit one, a mob or tamed animal won't get up by itself
 * ({@link #RECOVER_TICKS}) and bleeds out {@link #BLEED_OUT_TICKS} after the last hit.
 * <p>
 * <b>Players don't bleed out into a respawn</b> (Andy: "Lie until roused or give up"): after {@link #BLEED_OUT_TICKS}
 * they pass out and lie still ({@link #OUT}) until someone brings them round, or they hold sneak for
 * {@link #GIVE_UP_TICKS} to give up (a death credited to whoever felled them). Logging out keeps it.
 * <p>
 * <b>Bringing one round</b> (Kingdoms' 0.7.93 revive, for everyone): (0.17.7) hold use on a downed one, standing (Andy: "when
 * going for a revive you often pick up the player instead": sneak + use held is only the lift now, {@link Carry}, and
 * executions are left-click, {@code Finishers}). Held for {@link #REVIVE_TICKS}, it gets them up, for the least filling food you carry (a tamed animal
 * takes one of its own treats instead, {@link #TREATS}). Kingdoms' villagers tend the downed too, and ask for the
 * getting up with the tag {@link #REVIVE_TAG}.
 * <p>
 * (0.18.5) Andy (2026-10-29): "while downed, any attack that would deal 3 hearts or more of damage kills instantly ... if
 * anyone goes down without red damage or worse to limbs, they can stand themselves up with shift instead. if they are too
 * wounded to get up shift will give up. the animation takes longer but animates them rising to their feet, and is
 * interrupted after 2 hits." A blow of {@link #KILL_BLOW} or more on anyone downed is their death ({@link #attack}). A downed
 * player whose arms, legs and feet are all above red on the wounds screen ({@link #TOO_HURT_FRACTION}, unbroken, and
 * none of them that low as they fell: {@link #TOO_HURT}) holds sneak for {@link #RISE_TICKS} to get up by themselves
 * ({@link #RISING}, drawn by {@code CrawlPose}), at {@link #RISE_HEALTH}; the {@link #HOLD_HITS}th hit meanwhile knocks
 * them back down (Kingdoms' Unflinching: more, {@link WillSkill#holdHits}), as it breaks off a revive or Second Wind's
 * eating. Too wounded, or passed out, sneak gives up as before. The crippled (off their feet, not downed) are as they were.
 */
public final class Downed {
	/** A downed one's health: what mobs, fire and falls have left to take. */
	public static final float DOWNED_HEALTH = 6.0F;
	/**
	 * (0.15.33) A downed player's health instead (10 hearts; Andy: "dealing that much damage would kill them, not strictly
	 * 10 hits"): every blow takes it as damage, a player's too, and running it out is their death.
	 */
	public static final float DOWNED_PLAYER_HEALTH = 20.0F;
	/** How long a downed mob or tamed animal no player has hit lies there before getting back up (ticks), and the health it, or anyone brought round, gets up with (share of its most). */
	public static final int RECOVER_TICKS = 1200;
	public static final float RECOVER_HEALTH = 0.3F;
	/** How long after a player's last hit a downed mob bleeds out (ticks), and how long a downed player crawls before passing out. */
	public static final int BLEED_OUT_TICKS = 2400;
	/** The player's hit that kills a downed mob outright (a downed player has {@link #DOWNED_PLAYER_HEALTH} instead). */
	public static final int MAX_WOUNDS = 10;
	/**
	 * The share of downed mobs who drag themselves along (the rest lie still, flailing now and then), and (0.15.142) of
	 * downed people (Andy: "Humanoids should especially have a tendency to crawl away from the fight").
	 */
	public static final float CRAWLER_CHANCE = 0.5F, PERSON_CRAWLER_CHANCE = 0.85F;
	/** A crawling mob's pace, as a share of its walking speed (where it crawls to: {@link DownedCrawl}). */
	public static final double CRAWL_SPEED = 0.3;
	/** A downed player's crawl, slower than a crawl (share of speed lost), and passed out: none at all. */
	public static final double PLAYER_CRAWL_SLOW = -0.5, PASSED_OUT_SLOW = -1.0;
	/** How long a downed player holds sneak to give up (ticks). */
	public static final int GIVE_UP_TICKS = 100;
	/** (0.18.5) A blow this hard (health points: three hearts) on anyone downed kills them outright. */
	public static final float KILL_BLOW = 6.0F;
	/** (0.18.5) How long a downed player holds sneak to get up by themselves (ticks), and the share of their most health they rise with. */
	public static final int RISE_TICKS = 160;
	public static final float RISE_HEALTH = 0.3F;
	/** (0.18.5) An arm, leg or foot this whole or less (the wounds screen's red) leaves a downed player too wounded to rise. */
	public static final float TOO_HURT_FRACTION = 0.25F;
	/** (0.18.5) The hit that breaks off a held action (rising, a revive, Second Wind's eating), before Unflinching. */
	public static final int HOLD_HITS = 2;
	/** How often a downed player is reminded they can give up (ticks), and the give-up bar's length. */
	static final int HINT_TICKS = 20, GIVE_UP_BAR = 12;
	/** (0.7.93) A revive: how long use is held to get them up (ticks), and how long before the bar shows (a tap is Kingdoms' disarm). */
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
	/**
	 * (0.15.130) A player who has died and not yet respawned (set as they die; the body that respawns starts without it).
	 * Saved, so one who quits on the death screen isn't taken for one stuck at no health ({@link #limbo}) when they're back.
	 */
	static final AttachmentType<Boolean> DIED = AttachmentRegistry.create(JaymosPowers.id("downed_died"),
		builder -> builder.persistent(Codec.BOOL));

	/**
	 * (0.18.5) A downed player getting up by themselves: the game time they started (sneak held). Synced to everyone (who
	 * draw them rising, {@code CrawlPose}); not saved.
	 */
	public static final AttachmentType<Long> RISING = AttachmentRegistry.create(JaymosPowers.id("downed_rising"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG.cast(), AttachmentSyncPredicate.all()));
	/** (0.18.5) Downed with an arm, leg or foot at red or worse as they fell: too wounded to rise by themselves. Saved. */
	static final AttachmentType<Boolean> TOO_HURT = AttachmentRegistry.create(JaymosPowers.id("downed_too_hurt"),
		builder -> builder.persistent(Codec.BOOL));

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

	/** (0.7.93) A player's use held on a downed one: since when, when use last came, how full the bar is, and whether it's over (use still swallowed till let go). */
	private static final class Revive {
		final LivingEntity body;
		final long start;
		long held;
		int ticks, hits;
		boolean begun, over;

		Revive(LivingEntity body, long start) {
			this.body = body;
			this.start = start;
			this.held = start;
		}
	}

	/**
	 * (0.18.45) Told of every downed body a player gets back up (a revive, or smelling salts). Jaymo's Kingdoms adds one (by
	 * reflection) so the villager remembers who got them up, and says so.
	 */
	public static final List<BiConsumer<ServerPlayer, LivingEntity>> BROUGHT_ROUND_LISTENERS = new ArrayList<>();

	/** Player → their revive. Not saved. */
	private static final Map<UUID, Revive> REVIVING = new HashMap<>();

	/** (0.18.5) A downed player getting up by themselves: how far along, the hits taken, and knocked back (sneak let go first). */
	private static final class Rise {
		int ticks, hits;
		boolean knocked;
	}

	/** (0.18.5) Downed player → their rise. Not saved. */
	private static final Map<UUID, Rise> RISES = new HashMap<>();

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
			if (isDowned(entity) && !FINISHING.contains(entity) && amount >= KILL_BLOW && attack(source)
				&& !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && entity.level() instanceof ServerLevel level) {
				// (0.18.5) three hearts or more on the downed: that's the end of them
				FELLED_BY.put(entity, source);
				finish(level, entity);
				return false;
			}
			if (!isDowned(entity) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !(source.getEntity() instanceof Player)
				|| entity instanceof Player) {
				// (mobs, fire, falls: real harm, out of what's left; (0.15.33) on a downed player, a player's blow too)
				return true;
			}
			// a player's blow (or arrow) on a downed mob: a wound, the tenth kills
			if (entity.level() instanceof ServerLevel level) {
				wound(level, entity, source);
			}
			return false;
		});
		// (0.18.5) a hit breaks off a held action: rising, a revive, Second Wind's eating
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof ServerPlayer player && taken > 0.0F && attack(source) && player.isAlive()) {
				struck(player);
			}
		});
		UseEntityCallback.EVENT.addPhaseOrdering(FIRST, Event.DEFAULT_PHASE);
		UseEntityCallback.EVENT.register(FIRST, Downed::onUseEntity);
		// and whatever right-click on a downed mob nobody else took (Kingdoms takes a guard's weapons) does nothing: no
		// trading with a downed villager, no saddling a downed horse
		UseEntityCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, LAST);
		UseEntityCallback.EVENT.register(LAST, (player, world, hand, entity, hit) ->
			// ((0.15.98) and a plain click on a downed player is a revive's hold, not a bow drawn or a meal eaten)
			(entity instanceof Mob || entity instanceof Player && !player.isShiftKeyDown()) && isDowned(entity) ? InteractionResult.SUCCESS
				: InteractionResult.PASS);
		// a downed player's hands are gone slack: no attacking, using, building or breaking
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> isDowned(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> isDowned(player) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (!isDowned(player)) {
				return InteractionResult.PASS;
			}
			// (0.15.49) lying face down, use lands on the ground: Second Wind's eating still counts ({@link SelfRevive})
			InteractionResult eat = SelfRevive.use(player, hand);
			return eat != null ? eat : InteractionResult.FAIL;
		});
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!isDowned(player)) {
				return InteractionResult.PASS;
			}
			// (0.15.49) but Second Wind's eating to rise ({@link SelfRevive})
			InteractionResult eat = SelfRevive.use(player, hand);
			return eat != null ? eat : InteractionResult.FAIL;
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> !isDowned(player));
		// (0.15.31) and no powers: every check that asks whether a player's power keys are live says no
		Powers.addSilencer(Downed::isDowned);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			limbo(server);
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
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer) {
				entity.setAttached(DIED, true);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			REVIVING.clear();
			GIVING_UP.clear();
			RISES.clear();
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
	 * (0.15.89) In water, or floating just over it (a limp body bobbing at the surface): a downed player here can't swim
	 * and floats limp ({@code DownedKeysMixin}, {@code KitchenCompat.registerRagdollHolds}).
	 */
	public static boolean adrift(Entity entity) {
		return entity.isInWater() || entity.level().getFluidState(entity.blockPosition().below()).is(net.minecraft.tags.FluidTags.WATER)
			&& entity.level().getFluidState(entity.blockPosition()).isEmpty() && !entity.onGround();
	}

	/** (0.18.5) A blow someone or something dealt (a swing, an arrow, a bite, a blast), not fire, a fall, hunger or drowning. */
	static boolean attack(DamageSource source) {
		return source.getEntity() != null || source.getDirectEntity() != null || source.is(DamageTypeTags.IS_EXPLOSION);
	}

	/** (0.18.5) An arm, leg or foot at red or worse on the wounds screen ({@link #TOO_HURT_FRACTION}), broken, or gone. */
	static boolean limbsTooHurt(LivingEntity entity) {
		BodyPlan plan = BodyPlan.of(entity);
		if (plan == null || plan == BodyPlan.SIMPLE) {
			return false;
		}
		for (int i = 0; i < plan.size(); i++) {
			BodyPart part = plan.part(i);
			if (!part.vital() && (Wounds.isBroken(entity, part) || Wounds.fraction(entity, part) <= TOO_HURT_FRACTION)) {
				return true;
			}
		}
		return false;
	}

	/** (0.18.5) Whether this downed player is too wounded to get up by themselves: limbs at red or worse as they fell, or now. */
	public static boolean tooHurt(LivingEntity entity) {
		return entity.hasAttached(TOO_HURT) || limbsTooHurt(entity);
	}

	/**
	 * Whether the blow that would kill {@code entity} downs it instead: a player (not creative or spectator), (0.15.41) any
	 * humanoid ({@link BodyPlan#HUMANOID}: villagers, folk, illagers, witches, piglins, zombies, skeletons...), one of
	 * Kingdoms' folk ({@link #DOWNABLE_TAG}), or a tamed animal. Never a boss.
	 */
	public static boolean downable(LivingEntity entity) {
		if (entity instanceof Player player) {
			return !player.isCreative() && !player.isSpectator();
		}
		BodyPlan plan = BodyPlan.of(entity);
		if (!(entity instanceof Mob) || plan == null) {
			return false;
		}
		// (0.15.41) Andy: "ALL HUMANOIDS go down, if they werent decapitated or otherwise prevented from the downstate"
		if (plan == BodyPlan.HUMANOID || entity instanceof Villager || entity.entityTags().contains(DOWNABLE_TAG)) {
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
			&& !entity.getMainHandItem().is(Items.TOTEM_OF_UNDYING) && !entity.getOffhandItem().is(Items.TOTEM_OF_UNDYING)
			// (0.17.183) Kingdoms' Will: No Mercy for the weaker ({@link WillSkill#noMercy})
			&& !WillSkill.noMercy(entity, source);
	}

	// ---- going down, getting up ------------------------------------------------------------------------------------------

	/** Down they go; {@code source}: what felled them (null: nothing did, a knock-out); {@code keepHealth}: (0.14.4) a knock-out takes none. */
	private static void goDown(ServerLevel level, LivingEntity entity, @Nullable DamageSource source, boolean keepHealth) {
		boolean crawler = !(entity instanceof Player)
			&& level.getRandom().nextFloat() < (BodyPlan.of(entity) == BodyPlan.HUMANOID ? PERSON_CRAWLER_CHANCE : CRAWLER_CHANCE);
		// (0.18.5) the limbs as they fell, before the downed health fills them back to the bruise line
		if (entity instanceof Player && limbsTooHurt(entity)) {
			entity.setAttached(TOO_HURT, true);
		} else {
			entity.removeAttached(TOO_HURT);
		}
		entity.removeAttached(RISING);
		if (!keepHealth) {
			entity.setHealth(Math.min(entity instanceof Player ? DOWNED_PLAYER_HEALTH : DOWNED_HEALTH, entity.getMaxHealth()));
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
			// (0.15.31) whatever power was running ends: a flight, a hold, a grab
			Power power = Powers.of(player);
			if (power != null) {
				power.onDowned(player);
			}
			GIVING_UP.remove(player.getUUID());
			RISES.remove(player.getUUID());
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.down").withStyle(ChatFormatting.RED));
		}
		level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.PLAYER_BIG_FALL, SoundSource.NEUTRAL, 0.9F, 0.8F);
	}

	/** No longer down (up again, dying, or respawned): its marks gone, a player's crawl and slowness with them. */
	private static void forget(LivingEntity entity) {
		FELLED_BY.remove(entity);
		DownedCrawl.forget(entity);
		boolean was = entity.hasAttached(DOWNED);
		entity.removeAttached(DOWNED);
		entity.removeAttached(WOUNDS);
		entity.removeAttached(OUT);
		entity.removeAttached(KNOCKED);
		entity.removeAttached(RISING);
		entity.removeAttached(TOO_HURT);
		entity.removeTag(DOWNED_TAG);
		entity.removeTag(REVIVE_TAG);
		if (entity instanceof Player player) {
			GIVING_UP.remove(player.getUUID());
			RISES.remove(player.getUUID());
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

	/**
	 * Brought round (or left long enough): back on its feet, hurt, and a mob back to its post (its AI takes up again).
	 * (0.15.82) A player gets up battered ({@link Battered}).
	 */
	public static void getUp(ServerLevel level, LivingEntity entity) {
		getUp(level, entity, true);
	}

	/** (0.15.83) Undone rather than got over (Chrono's Rewind back to before the fall): up, and not battered by it. */
	public static void undo(ServerLevel level, LivingEntity entity) {
		getUp(level, entity, false);
	}

	/** {@code battered}: (0.15.82) false for a knock-out from standing, which was no downing. */
	private static void getUp(ServerLevel level, LivingEntity entity, boolean battered) {
		if (!isDowned(entity)) {
			return;
		}
		// (0.17.183) Kingdoms' Will: No Getting Up while a player with it stands near ({@link WillSkill#heldDown})
		DamageSource felled = FELLED_BY.get(entity);
		if (battered && WillSkill.heldDown(level, entity, felled == null ? null : felled.getEntity())) {
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
		if (battered) {
			Battered.gotUp(entity);
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
		// (0.15.82) up, but battered
		Battered.gotUp(entity);
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
			getUp(level, entity, false);
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

	/**
	 * (0.15.98) A finisher's last blow ({@code Finishers}): its death now, credited to {@code by}. Nothing if it's dead
	 * already (an axe's beheading). (0.15.114) Downed or only crippled, it dies the same way.
	 */
	public static void execute(ServerLevel level, LivingEntity entity, Player by) {
		if (!entity.isAlive()) {
			return;
		}
		FELLED_BY.put(entity, level.damageSources().playerAttack(by));
		finish(level, entity);
	}

	/** A player hit a downed one: one more wound; the {@link #MAX_WOUNDS}th is its death. */
	private static void wound(ServerLevel level, LivingEntity entity, DamageSource source) {
		Long wounds = entity.getAttached(WOUNDS);
		int count = (wounds == null ? 0 : (int) (wounds & 0xF)) + 1;
		FELLED_BY.put(entity, source);
		// ((0.15.142) and whoever was binding it starts over)
		DownedCrawl.hit(entity);
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
	 * A downed mob's tick, in place of its AI: it gets up or bleeds out, and (0.15.142) groans, and a crawler hauls itself
	 * away from the fight or to a friend ({@link DownedCrawl}). Called by {@code DownedMobMixin} on the server.
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
		// (0.15.92) lying downed, not also knocked flat by a leg shot (that turned it over twice, head into the ground)
		com.jaymospowers.power.web.KitchenCompat.endLegCrawl(mob);
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
		mob.setJumping(false);
		mob.setXxa(0.0F);
		mob.setSpeed(0.0F);
		if (Carry.isCarried(mob) || com.jaymospowers.core.finisher.Finishers.targeted(mob)) {
			// ((0.15.98) and lying still under a finisher)
			mob.getNavigation().stop();
			return;
		}
		// (0.15.142) groaning, and a crawler crawling: away from the fight, or to a friend who'll see to it
		DamageSource source = FELLED_BY.get(mob);
		DownedCrawl.tick(level, mob, source == null ? null : source.getEntity());
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
		// (0.18.5) not too wounded: sneak gets them up instead (carried or adrift, they can't)
		if (!out && !tooHurt(player) && !Carry.isCarried(player) && !adrift(player)) {
			GIVING_UP.remove(player.getUUID());
			rise(level, player, now);
			return;
		}
		stopRising(player);
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

	/** (0.18.5) A downed player able to rise, this tick: sneak held fills the bar and, full, gets them up by themselves. */
	private static void rise(ServerLevel level, ServerPlayer player, long now) {
		Rise r = RISES.get(player.getUUID());
		if (!player.isShiftKeyDown()) {
			if (r != null && r.ticks > 0 && !r.knocked) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.rise_stopped").withStyle(ChatFormatting.GRAY));
			}
			stopRising(player);
			if (now % HINT_TICKS == 0) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.rise_hint").withStyle(ChatFormatting.GRAY));
			}
			return;
		}
		if (r == null) {
			r = new Rise();
			RISES.put(player.getUUID(), r);
		}
		if (r.knocked) {
			// (knocked back down: let go of sneak and try again)
			return;
		}
		if (r.ticks++ == 0) {
			player.setAttached(RISING, now);
		}
		if (r.ticks % 20 == 10) {
			// straining with it
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.5F,
				0.7F + level.getRandom().nextFloat() * 0.3F);
		}
		if (r.ticks < RISE_TICKS) {
			if (r.ticks % 2 == 0) {
				int filled = Math.round(GIVE_UP_BAR * r.ticks / (float) RISE_TICKS);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.rising", "|".repeat(filled) + ".".repeat(GIVE_UP_BAR - filled))
					.withStyle(ChatFormatting.YELLOW));
			}
			return;
		}
		RISES.remove(player.getUUID());
		getUp(level, player);
		if (!isDowned(player)) {
			// (on their own legs, not a friend's arm: less in them than a revive leaves)
			player.setHealth(Math.min(player.getHealth(), Math.max(1.0F, player.getMaxHealth() * RISE_HEALTH)));
			Wounds.reconcile(player);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.risen").withStyle(ChatFormatting.GREEN));
		}
	}

	/** (0.18.5) No longer rising (let go, or can't now): the bar and the drawing gone. */
	private static void stopRising(ServerPlayer player) {
		RISES.remove(player.getUUID());
		if (player.hasAttached(RISING)) {
			player.removeAttached(RISING);
		}
	}

	/**
	 * (0.18.5) {@code player} took a hit: the {@link #HOLD_HITS}th (Unflinching: more) breaks off whatever they were holding
	 * at, their rise (back down they go), their revive, or Second Wind's eating.
	 */
	private static void struck(ServerPlayer player) {
		int most = WillSkill.holdHits(player, HOLD_HITS);
		Rise rise = RISES.get(player.getUUID());
		if (rise != null && rise.ticks > 0 && !rise.knocked && ++rise.hits >= most) {
			rise.knocked = true;
			rise.ticks = 0;
			rise.hits = 0;
			player.removeAttached(RISING);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_BIG_FALL, SoundSource.PLAYERS, 0.8F, 0.9F);
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.rise_knocked").withStyle(ChatFormatting.RED));
		}
		Revive revive = REVIVING.get(player.getUUID());
		if (revive != null && revive.begun && !revive.over && ++revive.hits >= most) {
			revive.over = true;
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.revive_broken", revive.body.getDisplayName())
				.withStyle(ChatFormatting.RED));
		}
		SelfRevive.struck(player, most);
	}

	/**
	 * (0.15.130) A player out of health who never died: something turned their death away and gave no health back (Fabric
	 * leaves it at none), and a body at no health can't be hurt, so giving up and {@code /kill} pass it by and they lie
	 * there for good, through a relog too. Andy (2026-10-22): Jung, after a meteor. Caught as the blow lands
	 * ({@code DownedLimboMixin}) or, for any other way in (a save loaded at none), here each tick.
	 */
	private static void limbo(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.getHealth() > 0.0F) {
				// ((0.15.140) alive after all: whatever death it marked is over)
				if (player.hasAttached(DIED)) {
					player.removeAttached(DIED);
				}
			} else if (!player.isSpectator() && !player.hasAttached(DIED)) {
				limbo(player.level(), player, player.getLastDamageSource());
			}
		}
	}

	/**
	 * (0.15.130) {@code entity} is out of health but never died ({@link #limbo(MinecraftServer)}): a player who could go down
	 * and wasn't goes down now (only straight after the blow, before their body starts to fall); anyone else gets the
	 * death they were owed, death screen and all.
	 */
	public static void limbo(ServerLevel level, LivingEntity entity, @Nullable DamageSource source) {
		// ((0.15.140) one who died properly is left to respawn: a player's die never sets vanilla's dead flag, so 0.15.130's
		// check took every death for this and stood them back up, alive but turned away from the respawn)
		if (!(entity instanceof ServerPlayer player) || player.isSpectator() || player.hasAttached(DIED)) {
			return;
		}
		DamageSource cause = source != null ? source : FELLED_BY.getOrDefault(player, level.damageSources().generic());
		boolean down = !isDowned(player) && downable(player) && spared(player, cause) && player.deathTime == 0 && !player.isRemoved();
		// (so the next one can be traced: whatever turned this death away left it at none)
		JaymosPowers.LOGGER.warn("{} was left at no health without dying (hit by {}, downed: {}); {}", player.getScoreboardName(),
			cause.getMsgId(), isDowned(player), down ? "putting them down" : "finishing the death");
		if (down) {
			goDown(level, player, cause, false);
			return;
		}
		forget(player);
		player.die(cause);
	}

	// ---- bringing one round -----------------------------------------------------------------------------------------------

	private static InteractionResult onUseEntity(Player player, net.minecraft.world.level.Level world, InteractionHand hand, Entity entity,
		net.minecraft.world.phys.@Nullable EntityHitResult hit) {
		if (isDowned(player)) {
			return InteractionResult.FAIL;
		}
		if (player.isShiftKeyDown() || world.isClientSide() || hand != InteractionHand.MAIN_HAND) {
			// (0.17.7) sneak + use on them is the lift (Carry), or medicine that wants sneak (Treatment)
			return InteractionResult.PASS;
		}
		if (Treatment.claimsDowned(player, hand, entity) || Treatment.treatsOnPlainUse(player.getMainHandItem(), world)) {
			// (0.14.4) medicine in hand: Treatment's, not a revive
			return InteractionResult.PASS;
		}
		Revive held = REVIVING.get(player.getUUID());
		if (held != null && held.body == entity) {
			// still holding use: the bar keeps filling, or, once it's over (they're up), nothing more till let go
			held.held = world.getGameTime();
		} else if (entity instanceof LivingEntity body && isDowned(body) && !player.isSpectator()) {
			REVIVING.put(player.getUUID(), new Revive(body, world.getGameTime()));
		}
		// (only watching: a tap on a downed guard still has Kingdoms take its weapons, and Downed's catch-all swallows the rest)
		return InteractionResult.PASS;
	}

	/** (0.7.93) Each player's use held on a downed one: a hold fills the bar and gets them up. */
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
				if (r.begun && !r.over) {
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
			if (player.isShiftKeyDown() || Carry.isCarried(body) || !player.isWithinEntityInteractionRange(body, REVIVE_SLACK)) {
				r.over = true;
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.revive_stopped", body.getDisplayName()).withStyle(ChatFormatting.GRAY));
				continue;
			}
			// (0.15.99) down on one knee at their side
			Tending.keep(player, body);
			if (++r.ticks % REVIVE_WORK_TICKS == 0) {
				// kneeling at their side, hands at work
				player.swing(InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
				level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.5F,
					0.8F + level.getRandom().nextFloat() * 0.4F);
				level.sendParticles(ParticleTypes.HEART, body.getX(), body.getY() + 0.6, body.getZ(), 1, 0.3, 0.1, 0.3, 0.0);
			}
			// (0.15.49) Steady Hands: quicker ({@link MedicineSkill})
			int need = MedicineSkill.ticks(player, REVIVE_TICKS);
			if (r.ticks < need) {
				if (r.ticks % 2 == 0) {
					int filled = Math.round(REVIVE_BAR * r.ticks / (float) need);
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
			// (0.17.183) held down by someone else's Will (No Getting Up): no getting them up while that one stands near
			DamageSource felled = FELLED_BY.get(body);
			WillSkill.reviver = player;
			try {
				if (WillSkill.heldDown(level, body, felled == null ? null : felled.getEntity())) {
					player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.held_down", body.getDisplayName()).withStyle(ChatFormatting.GRAY));
					continue;
				}
				ItemStack stack = player.getInventory().getItem(slot);
				Component eaten = stack.getHoverName();
				stack.consume(1, player);
				player.getInventory().setChanged();
				getUp(level, body);
				MedicineSkill.broughtRound(player, body);
				MedicineSkill.treated(player);
				level.playSound(null, body.getX(), body.getY(), body.getZ(), SoundEvents.GENERIC_EAT.value(), SoundSource.NEUTRAL, 0.8F, 1.0F);
				level.sendParticles(ParticleTypes.HAPPY_VILLAGER, body.getX(), body.getY() + 0.8, body.getZ(), 10, 0.4, 0.4, 0.4, 0.0);
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.downed.revived", body.getDisplayName(), eaten).withStyle(ChatFormatting.GREEN));
				for (BiConsumer<ServerPlayer, LivingEntity> listener : BROUGHT_ROUND_LISTENERS) {
					listener.accept(player, body);
				}
			} finally {
				WillSkill.reviver = null;
			}
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
