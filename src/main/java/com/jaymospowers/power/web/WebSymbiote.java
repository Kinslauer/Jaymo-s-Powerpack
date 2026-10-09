package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The symbiote: a living black suit a web slinger can bond with by finding one of the rare meteor craters (see
 * {@link WebCraterFeature}) and laying hands on the goo at its heart. Bonded, your webs reach further and pull harder,
 * and six abilities open up on a radial menu (X). It has to be fed, it hates fire and loud noise, and push it too far
 * and it tears itself off you for good: you'll have to find another crater.
 *
 * <p>Nothing here is explained in game: hints are one-line whispers, each heard once.
 */
public final class WebSymbiote {
	// ===== abilities (radial menu order, clockwise from the top) ====================================================
	public static final int TENDRIL = 0;
	public static final int ARMOR = 1;
	public static final int CRAWL = 2;
	public static final int CONSUME = 3;
	public static final int SPIKES = 4;
	public static final int SHROUD = 5;
	public static final int ABILITIES = 6;
	/** Not an ability: the little mask in the middle of the menu shows or hides the suit (the powers keep working). */
	public static final int SUIT_TOGGLE = 6;
	/** Cooldown of each ability after use, in ticks (toggles: after switching). */
	public static final int[] COOLDOWN = {100, 40, 20, 160, 240, 600};
	/** Satiety each ability costs. */
	public static final int[] COST = {2, 0, 0, 0, 8, 5};

	/** Tendril Grab: reach, how hard creatures are flung to you, and above this bounding-box volume you go to them. */
	public static final double TENDRIL_RANGE = 20.0;
	public static final double TENDRIL_HEAVY_VOLUME = 8.0;
	/** Living Armor: absorption (half-hearts: 16 = 8 hearts), regrown one point every so often; knockback resistance. */
	public static final double ARMOR_ABSORPTION = 16.0;
	public static final float ARMOR_REGEN = 1.0F;
	public static final int ARMOR_REGEN_TICKS = 40;
	public static final double ARMOR_KNOCKBACK_RESIST = 0.6;
	/** Living Armor makes it hungry this many times faster. */
	public static final int ARMOR_DRAIN_MULT = 3;
	/** Consume: reach, the biggest thing it'll eat (max health), what it heals you and feeds it. */
	public static final double CONSUME_RANGE = 3.0;
	public static final float CONSUME_MAX_HEALTH = 10.0F;
	public static final float CONSUME_HEAL = 6.0F;
	public static final int CONSUME_FEED = 30;
	public static final int CONSUME_CALM = 10;
	/** Spike Burst: radius, damage, how hard it throws things away. */
	public static final double SPIKE_RADIUS = 5.0;
	public static final float SPIKE_DAMAGE = 8.0F;
	public static final double SPIKE_KNOCKBACK = 1.3;
	/** Shroud: invisible until you attack, or for this long at most. */
	public static final int SHROUD_TICKS = 400;

	// ===== the bond ================================================================================================
	public static final int SATIETY_MAX = 100;
	public static final int SATIETY_START = 70;
	/** One point of satiety is lost every this many ticks (100 points ≈ 20 minutes, a Minecraft day). */
	public static final int SATIETY_DRAIN_TICKS = 240;
	/** At or below this it's starving: it eats from your hunger bar instead, and gets agitated. */
	public static final int STARVING = 15;
	public static final int STARVE_TICKS = 40;
	public static final float STARVE_EXHAUSTION = 1.2F;
	/** Raw meat feeds it this much per point of the meat's nutrition (and feeds you nothing). */
	public static final int MEAT_FEED_PER_NUTRITION = 5;
	public static final List<Item> RAW_MEAT = List.of(Items.BEEF, Items.PORKCHOP, Items.CHICKEN, Items.MUTTON, Items.RABBIT,
		Items.COD, Items.SALMON, Items.ROTTEN_FLESH);
	/** Stress: 0-100. Calms by a point every so often while fed, climbs while starving. */
	public static final int STRESS_MAX = 100;
	public static final int STRESS_CALM_TICKS = 100;
	public static final int STRESS_STARVE_TICKS = 80;

	/** Takeover: starving and this stressed, every so often there's a chance it grabs the wheel. */
	public static final int TAKEOVER_STRESS = 50;
	public static final int TAKEOVER_CHECK_TICKS = 200;
	public static final float TAKEOVER_CHANCE = 0.35F;
	public static final int TAKEOVER_MIN_TICKS = 60;
	public static final int TAKEOVER_MAX_TICKS = 100;
	public static final double TAKEOVER_RANGE = 12.0;
	public static final double TAKEOVER_REACH = 3.0;
	public static final int TAKEOVER_ATTACK_TICKS = 10;
	public static final int TAKEOVER_COOLDOWN = 1200;
	public static final int TAKEOVER_RELIEF = 20;

	/** Weaknesses: what each exposure does to you and to the bond, and how many it takes to break it. */
	public static final float EXPOSURE_DAMAGE = 3.0F;
	public static final int EXPOSURE_STRESS = 20;
	public static final int REJECT_AT = 6;
	/** How close each noise has to be (blocks). */
	public static final double BELL_RANGE = 16.0;
	public static final double NOTE_RANGE = 8.0;
	public static final double HORN_RANGE = 24.0;
	public static final double EXPLOSION_RANGE = 16.0;
	/** After a noise counts, more noise doesn't count again for this long (one TNT chain is one fright). */
	public static final int NOISE_GRACE_TICKS = 100;
	/** A burning episode ends after this long without fire (so it's one exposure per episode, not per tick). */
	public static final int FIRE_EPISODE_GAP = 60;

	/** Bonded, your webs reach this much further, pull this much harder, and Zip cools down this much faster. */
	public static final double REACH_SCALE = 1.5;
	public static final double PULL_SCALE = 1.25;
	public static final double ZIP_COOLDOWN_SCALE = 0.5;

	/** The goo crawling up onto you takes this long. */
	public static final int BOND_TICKS = 50;
	/** It crawls away from a rejected host for this long. */
	public static final int CRAWL_AWAY_TICKS = 50;

	/** The symbiote's black, and its purple sheen (for dust particles). */
	public static final int BLACK = 0x0E0C12;
	public static final int SHEEN = 0x34313A;

	// one-time whispers
	private static final int HINT_NOISE = 1;
	private static final int HINT_FIRE = 2;
	private static final int HINT_HUNGER = 4;
	private static final int HINT_EDGE = 8;
	private static final int HINT_MEAT = 16;
	private static final int HINT_INSTINCT = 32;

	private static final Identifier ARMOR_ABSORB_ID = JaymosPowers.id("web_symbiote_armor");
	private static final Identifier ARMOR_KNOCKBACK_ID = JaymosPowers.id("web_symbiote_knockback");

	/** The bond (saved, kept through death, synced to all: everyone sees the suit). */
	public static final AttachmentType<WebSymbioteState> STATE = AttachmentRegistry.create(JaymosPowers.id("web_symbiote"),
		builder -> builder.persistent(WebSymbioteState.CODEC).copyOnDeath().syncWith(WebSymbioteState.STREAM_CODEC, AttachmentSyncPredicate.all()));
	/** When each ability is ready (synced to the owner only, for the menu). */
	public static final AttachmentType<WebSymbioteState.Cooldowns> COOLDOWNS = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_cd"),
		builder -> builder.syncWith(WebSymbioteState.Cooldowns.STREAM_CODEC, AttachmentSyncPredicate.targetOnly()));
	/** Server-only bookkeeping (not saved, not synced). */
	private static final AttachmentType<Live> LIVE = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_live"));

	// ---- Living Armor, bulked up (0.5.0) ----
	/** The armour swells you: this much bigger (scale), too big for a 1x2 gap. */
	public static final double ARMOR_SCALE = 0.25;
	/** The tendrils strike on their own: how often they look for something, how far they reach, the wind-up to the hit,
	 * the whole strike (slow and deliberate), and how hard it hits. */
	public static final int STRIKE_EVERY = 50;
	public static final double STRIKE_RANGE = 5.5;
	public static final int STRIKE_HIT = 18;
	public static final int STRIKE_TICKS = 40;
	public static final float STRIKE_DAMAGE = 6.0F;
	/**
	 * Worn too long, it wants out: after {@code ARMOR_RESTLESS_TICKS} of Living Armor, each takeover check (every
	 * {@link #TAKEOVER_CHECK_TICKS}) has a chance that climbs by {@code ARMOR_TAKEOVER_PER_MINUTE} a minute, up to
	 * {@code ARMOR_TAKEOVER_MAX}, hungry or not. Such a rampage lasts longer, and goes for players too.
	 */
	public static final int ARMOR_RESTLESS_TICKS = 1200;
	public static final float ARMOR_TAKEOVER_PER_MINUTE = 0.12F;
	public static final float ARMOR_TAKEOVER_MAX = 0.6F;
	public static final int RAMPAGE_MIN_TICKS = 120, RAMPAGE_MAX_TICKS = 200;
	/** In a rampage, every this many strikes on a player is a bite: this much more damage, and it feeds. */
	public static final int RAMPAGE_BITE_EVERY = 3;
	public static final float RAMPAGE_BITE_DAMAGE = 8.0F;
	/** Consume, on a corpse you're carrying (any size): heal and feed by its size. */
	public static final float CORPSE_HEAL_MIN = 6.0F, CORPSE_HEAL_MAX = 16.0F;
	public static final int CORPSE_FEED_MAX = 80;

	private static final Identifier ARMOR_SCALE_ID = JaymosPowers.id("web_symbiote_bulk");

	/** A tendril strike in progress: at what, and when it began (game time). Synced to everyone, who see it. */
	public record Strike(int target, long start) {
		public static final net.minecraft.network.codec.StreamCodec<io.netty.buffer.ByteBuf, Strike> STREAM_CODEC =
			net.minecraft.network.codec.StreamCodec.composite(
				net.minecraft.network.codec.ByteBufCodecs.VAR_INT, Strike::target,
				net.minecraft.network.codec.ByteBufCodecs.VAR_LONG, Strike::start,
				Strike::new);
	}

	public static final AttachmentType<Strike> STRIKE = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_strike"),
		builder -> builder.syncWith(Strike.STREAM_CODEC, AttachmentSyncPredicate.all()));

	/**
	 * Instinct: the symbiote keeps itself (and so you) alive. Every {@code INSTINCT_CHECK_TICKS} it looks for danger:
	 * fire close by, a long fall about to end, something hostile on you, or your health running low. Then, now and
	 * then ({@code INSTINCT_ARMOR_CHANCE}), it throws Living Armor up by itself; and while the armour is up it acts on
	 * its own: shoving you clear of fire (and smothering flames on you), catching a fall just before the ground,
	 * shoving you away from an attacker or snatching the attacker and flinging it, and sometimes, as you run,
	 * catapulting you along.
	 */
	public static final int INSTINCT_CHECK_TICKS = 10;
	public static final float INSTINCT_ARMOR_CHANCE = 0.45F;
	public static final float INSTINCT_LOW_HEALTH = 8.0F;
	public static final double INSTINCT_THREAT_RANGE = 3.0;
	public static final int INSTINCT_COOLDOWN = 50;
	public static final double INSTINCT_FIRE_SHOVE = 0.7;
	public static final double INSTINCT_THREAT_SHOVE = 0.95;
	public static final float INSTINCT_GRAB_CHANCE = 0.5F;
	public static final double INSTINCT_FALL_CATCH_HEIGHT = 4.0;
	public static final float INSTINCT_CATAPULT_CHANCE = 0.05F;
	public static final double INSTINCT_CATAPULT = 1.4;

	/** Consume takes its time: the jaws chomp {@code EAT_CHOMPS} times over {@code EAT_TICKS}, then it's gone. */
	public static final int EAT_TICKS = 40;
	public static final int EAT_CHOMPS = 4;
	/**
	 * How wide each bite gapes (0-1 of the maw's full, engulfing size): random from {@code BITE_MIN} up. The last bite
	 * has a {@code LAST_BITE_FULL} chance of going all the way, and then it swallows the meal whole right there.
	 */
	public static final float BITE_MIN = 0.15F, LAST_BITE_FULL = 0.25F;

	/** Bite {@code bite}'s size for the meal begun at {@code start} by entity {@code id}: the same on the server and every client. */
	public static float biteSize(long start, int id, int bite) {
		net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create((start * 31L + id) * 7919L + bite);
		float roll = random.nextFloat();
		if (bite == EAT_CHOMPS - 1 && roll < LAST_BITE_FULL) {
			return 1.0F;
		}
		return Mth.lerp(random.nextFloat(), BITE_MIN, 1.0F) * 0.999F;
	}
	/** What a meal does for your own hunger: food points per point healed, and the saturation modifier. */
	public static final float CONSUME_HUNGER_PER_HEAL = 0.75F;
	public static final float CONSUME_SATURATION = 0.6F;
	/** A meal in progress (game time it began): synced to everyone, who see the jaws work. */
	public static final AttachmentType<Long> EATING = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_eating"),
		builder -> builder.syncWith(net.minecraft.network.codec.ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	private static final class Live {
		long noiseReadyAt;
		boolean burning;
		int coolTicks;
		int bondTicks;
		Vec3 bondFrom = Vec3.ZERO;
		int crawlAwayTicks;
		Vec3 crawlAwayPos = Vec3.ZERO;
		Vec3 crawlAwayDir = Vec3.ZERO;
		int takeoverTicks;
		int takeoverTarget = -1;
		long takeoverReadyAt;
		long shroudUntil;
		boolean clinging;
		/** Ticks of Living Armor worn without a break. */
		int armorTicks;
		/** This takeover is a rampage (from wearing the armour too long): longer, and players aren't spared. */
		boolean rampage;
		int rampageStrikes;
		long nextStrikeAt;
		/** Instinct: when it can act on its own again, and whether it has caught this fall already. */
		long instinctReadyAt;
		boolean caughtFall;
		/** The meal: what, until when, and what it gives. */
		int eatTarget = -1;
		long eatUntil;
		float eatHeal;
		int eatFeed;
	}

	private WebSymbiote() {
	}

	static void register() {
		UseBlockCallback.EVENT.register(WebSymbiote::onUseBlock);
		ServerPlayNetworking.registerGlobalReceiver(WebNet.SymbioteUse.TYPE, (payload, ctx) -> use(ctx.player(), payload.ability()));
		// attacking anything breaks the Shroud
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (source.getEntity() instanceof ServerPlayer attacker && attacker != entity) {
				endShroud(attacker);
			}
		});
	}

	// ---- shared queries (both sides: the bond is synced) -------------------------------------------------------------

	public static WebSymbioteState state(Player player) {
		WebSymbioteState state = player.getAttached(STATE);
		return state == null ? WebSymbioteState.NONE : state;
	}

	/** Bonded, and still a web slinger (the symbiote only answers one). */
	public static boolean isBonded(Player player) {
		return state(player).bonded() && Powers.has(player, WebPower.INSTANCE);
	}

	/** Others should see the black suit (and black webs). */
	public static boolean suitShown(Player player) {
		WebSymbioteState state = state(player);
		return state.bonded() && state.has(WebSymbioteState.SUIT) && Powers.has(player, WebPower.INSTANCE);
	}

	/** Living Armor is up (the tendrils grab and hold; the plain suit only swings). */
	public static boolean armored(Player player) {
		return isToggled(player, WebSymbioteState.ARMOR);
	}

	public static boolean isToggled(Player player, int flag) {
		return isBonded(player) && state(player).has(flag);
	}

	public static double reachScale(Player player) {
		return isBonded(player) ? REACH_SCALE : 1.0;
	}

	/**
	 * How far a swing reaches: wearing the suit, the swing is the tendrils' (most of one side's, grabbing close by),
	 * a short-range swing; bonded but bare, the symbiote's webs reach further than a plain web's.
	 */
	public static double swingScale(Player player) {
		return suitShown(player) ? TENDRIL_SWING_SCALE : reachScale(player);
	}

	/** The tendrils' swing reach, as a share of a web's. */
	public static final double TENDRIL_SWING_SCALE = 0.3;

	public static double pullScale(Player player) {
		return isBonded(player) ? PULL_SCALE : 1.0;
	}

	public static int zipCooldown(Player player) {
		return isBonded(player) ? (int)Math.round(WebPower.ZIP_COOLDOWN * ZIP_COOLDOWN_SCALE) : WebPower.ZIP_COOLDOWN;
	}

	/** Wall Crawl is on: the server lets them hang on walls and ceilings without the flying kick. */
	static boolean allowsFloating(ServerPlayer player) {
		return isToggled(player, WebSymbioteState.CRAWL);
	}

	// ---- server ------------------------------------------------------------------------------------------------------

	private static Live live(ServerPlayer player) {
		Live live = player.getAttached(LIVE);
		if (live == null) {
			live = new Live();
			player.setAttached(LIVE, live);
		}
		return live;
	}

	private static void set(ServerPlayer player, WebSymbioteState state) {
		if (!state.equals(player.getAttached(STATE))) {
			player.setAttached(STATE, state);
		}
	}

	private static void whisper(ServerPlayer player, String key) {
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.web_symbiote." + key)
			.withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
	}

	/** Whispers {@code key} the first time {@code hint} comes up. Returns the state with the hint recorded. */
	private static WebSymbioteState hintOnce(ServerPlayer player, WebSymbioteState state, int hint, String key) {
		if ((state.hints() & hint) != 0) {
			return state;
		}
		whisper(player, key);
		return state.withHint(hint);
	}

	static DustParticleOptions dust(float scale) {
		return new DustParticleOptions(BLACK, scale);
	}

	private static DustParticleOptions sheen(float scale) {
		return new DustParticleOptions(SHEEN, scale);
	}

	/** Every tick, for a web slinger (from WebPower.tick). */
	static void tick(ServerPlayer player) {
		Live live = player.getAttached(LIVE);
		if (live != null) {
			tickEffects(player, live);
		}
		WebSymbioteState state = player.getAttached(STATE);
		if (state == null || !state.bonded()) {
			if (player.tickCount % 20 == 0) {
				removeArmor(player);
			}
			return;
		}
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}
		if (live == null) {
			live = live(player);
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		WebSymbioteState next = state;
		boolean armor = state.has(WebSymbioteState.ARMOR);

		// hunger
		int drainTicks = armor ? Math.max(1, SATIETY_DRAIN_TICKS / ARMOR_DRAIN_MULT) : SATIETY_DRAIN_TICKS;
		if (!player.isCreative() && player.tickCount % drainTicks == 0 && next.satiety() > 0) {
			next = next.withSatiety(next.satiety() - 1);
		}
		boolean starving = next.satiety() <= STARVING;
		if (starving) {
			if (player.tickCount % STARVE_TICKS == 0 && !player.isCreative()) {
				// it feeds on you instead
				player.causeFoodExhaustion(next.satiety() <= 0 ? STARVE_EXHAUSTION * 2.0F : STARVE_EXHAUSTION);
			}
			if (player.tickCount % STRESS_STARVE_TICKS == 0) {
				next = next.withStress(Math.min(STRESS_MAX, next.stress() + 1));
			}
			next = hintOnce(player, next, HINT_HUNGER, "hunger");
		} else if (player.tickCount % STRESS_CALM_TICKS == 0 && next.stress() > 0) {
			next = next.withStress(next.stress() - 1);
		}

		// Living Armor: absorption that grows back, and planted feet
		if (armor) {
			applyArmor(player);
			if (player.tickCount % ARMOR_REGEN_TICKS == 0 && player.getAbsorptionAmount() < ARMOR_ABSORPTION) {
				player.setAbsorptionAmount((float)Math.min(ARMOR_ABSORPTION, player.getAbsorptionAmount() + ARMOR_REGEN));
			}
			live.armorTicks++;
			tickStrikes(player, live, now);
			if (player.tickCount % INSTINCT_CHECK_TICKS == 0 && live.takeoverTicks <= 0) {
				catapult(player, live, now);
			}
		} else {
			live.armorTicks = 0;
			if (player.getAttached(STRIKE) != null) {
				player.removeAttached(STRIKE);
			}
			if (player.tickCount % 20 == 0) {
				removeArmor(player);
			}
		}

		if (live.eatTarget >= 0) {
			tickEating(player, live, now);
		}
		if (player.onGround() || player.isInWater()) {
			live.caughtFall = false;
		}
		if (live.takeoverTicks <= 0 && live.bondTicks <= 0 && !player.isCreative() && player.tickCount % INSTINCT_CHECK_TICKS == 0) {
			next = instinct(player, live, next, now);
		}

		// Shroud runs out
		if (live.shroudUntil > 0 && now >= live.shroudUntil) {
			endShroud(player);
		}

		// fire: one exposure per burning episode
		boolean burning = player.isOnFire() || player.isInLava();
		if (burning) {
			live.coolTicks = 0;
			if (!live.burning) {
				live.burning = true;
				set(player, next);
				expose(player, Exposure.FIRE);
				return;
			}
		} else if (live.burning && ++live.coolTicks > FIRE_EPISODE_GAP) {
			live.burning = false;
		}

		// wall crawl: hanging on a wall or ceiling is like being on a ladder
		live.clinging = state.has(WebSymbioteState.CRAWL) && !player.onGround() && touchingWall(player);
		if (live.clinging) {
			player.resetFallDistance();
		}

		// takeover
		if (live.takeoverTicks > 0) {
			tickTakeover(player, live, now);
			if (live.takeoverTicks == 0) {
				next = next.withStress(Math.max(0, next.stress() - TAKEOVER_RELIEF));
			}
		} else if (now >= live.takeoverReadyAt && player.tickCount % TAKEOVER_CHECK_TICKS == 0 && !player.isCreative()) {
			// hungry and stressed, it may grab the wheel; worn as armour too long, it grows restless whatever its mood
			float restless = live.armorTicks <= ARMOR_RESTLESS_TICKS ? 0.0F
				: Math.min(ARMOR_TAKEOVER_MAX, (live.armorTicks - ARMOR_RESTLESS_TICKS) / 1200.0F * ARMOR_TAKEOVER_PER_MINUTE);
			if (restless > 0.0F && player.getRandom().nextFloat() < restless) {
				live.rampage = true;
				startTakeover(player, live, now);
			} else if (starving && next.stress() >= TAKEOVER_STRESS && player.getRandom().nextFloat() < TAKEOVER_CHANCE) {
				live.rampage = false;
				startTakeover(player, live, now);
			}
		}

		// a faint drip of black from a bonded suit, now and then (everyone sees it)
		if (state.has(WebSymbioteState.SUIT) && player.tickCount % 30 == 0 && !player.isInvisible()) {
			level.sendParticles(dust(0.8F), player.getX(), player.getY() + 0.9, player.getZ(), 1, 0.25, 0.45, 0.25, 0.0);
		}
		set(player, next);
	}

	/** Wall or ceiling right next to the player (cheap: one box check). */
	static boolean touchingWall(Player player) {
		AABB box = player.getBoundingBox();
		return !player.level().noCollision(player, box.inflate(0.12, -0.1, 0.12))
			|| !player.level().noCollision(player, box.move(0.0, 0.15, 0.0).deflate(0.05, 0.0, 0.05));
	}

	/** Bonding animation, crawling-away animation. */
	private static void tickEffects(ServerPlayer player, Live live) {
		ServerLevel level = player.level();
		if (live.bondTicks > 0) {
			// the goo crawls up from the ground, spiralling to the head
			float t = 1.0F - live.bondTicks / (float)BOND_TICKS;
			double angle = live.bondTicks * 0.7;
			double y = player.getY() + t * player.getBbHeight();
			for (int k = 0; k < 3; k++) {
				double a = angle + k * Math.PI * 2.0 / 3.0;
				double r = 0.45 - t * 0.1;
				level.sendParticles(dust(1.4F), player.getX() + Math.cos(a) * r, y, player.getZ() + Math.sin(a) * r, 2, 0.04, 0.06, 0.04, 0.0);
			}
			if (live.bondTicks % 4 == 0) {
				level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), y, player.getZ(), 2, 0.3, 0.1, 0.3, 0.01);
				level.playSound(null, player.getX(), y, player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.6F, 0.5F + t * 0.4F);
			}
			if (--live.bondTicks == 0) {
				level.sendParticles(sheen(1.6F), player.getX(), player.getY() + 1.0, player.getZ(), 30, 0.35, 0.8, 0.35, 0.0);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.6F, 0.7F);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.2F, 0.6F);
			}
		}
		if (live.crawlAwayTicks > 0) {
			// a black blot slithering off across the ground, thinning as it goes
			live.crawlAwayPos = live.crawlAwayPos.add(live.crawlAwayDir.scale(0.18));
			Vec3 at = live.crawlAwayPos;
			float left = live.crawlAwayTicks / (float)CRAWL_AWAY_TICKS;
			level.sendParticles(dust(0.6F + 1.2F * left), at.x, at.y + 0.1, at.z, 3, 0.12, 0.03, 0.12, 0.0);
			if (live.crawlAwayTicks % 8 == 0) {
				level.playSound(null, at.x, at.y, at.z, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.5F * left + 0.1F, 0.6F);
			}
			if (--live.crawlAwayTicks == 0) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.2, at.z, 6, 0.2, 0.1, 0.2, 0.01);
			}
		}
	}

	// ---- bonding -----------------------------------------------------------------------------------------------------

	private static InteractionResult onUseBlock(Player player, net.minecraft.world.level.Level level, InteractionHand hand, BlockHitResult hit) {
		if (WebPower.GOO == null || !level.getBlockState(hit.getBlockPos()).is(WebPower.GOO) || player.isSpectator()) {
			return InteractionResult.PASS;
		}
		if (hand != InteractionHand.MAIN_HAND) {
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = hit.getBlockPos();
		if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		Vec3 center = Vec3.atBottomCenterOf(pos).add(0.0, 0.3, 0.0);
		if (!Powers.has(sp, WebPower.INSTANCE) || !sp.isShiftKeyDown() || state(sp).bonded()) {
			// it recoils from anyone else (and from a careless touch)
			serverLevel.playSound(null, pos, SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 1.0F, 0.5F);
			serverLevel.playSound(null, pos, SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.BLOCKS, 0.8F, 1.6F);
			serverLevel.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 8, 0.25, 0.15, 0.25, 0.04);
			if (!Powers.has(sp, WebPower.INSTANCE)) {
				whisper(sp, "recoil");
			}
			return InteractionResult.SUCCESS;
		}
		serverLevel.removeBlock(pos, false);
		bond(sp, center);
		return InteractionResult.SUCCESS;
	}

	/** {@code player} takes the symbiote in (from the goo at {@code from}, or from a command). */
	public static void bond(ServerPlayer player, Vec3 from) {
		WebSymbioteState old = state(player);
		set(player, new WebSymbioteState(true, SATIETY_START, 0, 0, WebSymbioteState.SUIT, old.hints()));
		player.setAttached(COOLDOWNS, WebSymbioteState.Cooldowns.NONE);
		Live live = live(player);
		live.bondTicks = BOND_TICKS;
		live.bondFrom = from;
		live.burning = false;
		ServerLevel level = player.level();
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0, false, false));
		level.playSound(null, from.x, from.y, from.z, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1.2F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS, 1.5F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.2F, 0.5F);
		level.sendParticles(ParticleTypes.SQUID_INK, from.x, from.y, from.z, 30, 0.4, 0.2, 0.4, 0.08);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, from.x, from.y, from.z, 10, 0.4, 0.2, 0.4, 0.02);
		whisper(player, "bond");
	}

	/** The symbiote tears itself off and leaves for good (this one, at least). */
	public static void reject(ServerPlayer player) {
		WebSymbioteState old = state(player);
		if (!old.bonded()) {
			return;
		}
		endShroud(player);
		Live live = live(player);
		if (live.takeoverTicks > 0) {
			live.takeoverTicks = 0;
			ServerPlayNetworking.send(player, new WebNet.Takeover(-1, 0));
		}
		set(player, new WebSymbioteState(false, 0, 0, 0, 0, old.hints()));
		removeArmor(player);
		player.removeAttached(COOLDOWNS);
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 80, 0.45, 0.9, 0.45, 0.12);
		level.sendParticles(dust(2.0F), player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.5, 0.9, 0.5, 0.0);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.5, player.getZ(), 20, 0.5, 0.4, 0.5, 0.03);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_DEATH, SoundSource.PLAYERS, 1.5F, 0.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.7F, 1.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 1.0F, 1.2F);
		player.hurtServer(level, level.damageSources().magic(), EXPOSURE_DAMAGE);
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 160, 0, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 0, false, false));
		// and it slithers away
		double angle = player.getRandom().nextDouble() * Math.PI * 2.0;
		live.crawlAwayDir = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
		live.crawlAwayPos = player.position();
		live.crawlAwayTicks = CRAWL_AWAY_TICKS;
		whisper(player, "reject");
	}

	/** Fills it up and calms it down (a command, for testing). */
	public static void feed(ServerPlayer player) {
		WebSymbioteState state = state(player);
		if (state.bonded()) {
			set(player, state.withSatiety(SATIETY_MAX).withStress(0));
		}
	}

	/** Eating raw meat while bonded feeds it instead of you. Returns true if it took the meal. */
	public static boolean feedOnMeat(ServerPlayer player, ItemStack stack, int nutrition) {
		if (!isBonded(player) || !RAW_MEAT.contains(stack.getItem())) {
			return false;
		}
		WebSymbioteState state = state(player);
		WebSymbioteState next = state.withSatiety(Math.min(SATIETY_MAX, state.satiety() + Math.max(1, nutrition) * MEAT_FEED_PER_NUTRITION))
			.withStress(Math.max(0, state.stress() - 3));
		next = hintOnce(player, next, HINT_MEAT, "meat");
		set(player, next);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.8F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_BITE, SoundSource.PLAYERS, 0.5F, 0.7F);
		Vec3 mouth = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.4));
		level.sendParticles(dust(1.0F), mouth.x, mouth.y - 0.1, mouth.z, 8, 0.1, 0.1, 0.1, 0.0);
		return true;
	}

	/** The power is going away: so is the bond's grip on the body (the bond itself is dropped). */
	static void onLost(ServerPlayer player) {
		endShroud(player);
		removeArmor(player);
		player.removeAttached(STATE);
		player.removeAttached(COOLDOWNS);
		player.removeAttached(LIVE);
	}

	// ---- weaknesses --------------------------------------------------------------------------------------------------

	enum Exposure { FIRE, NOISE }

	/** A game event somewhere in {@code level} (from the server level mixin): loud ones hurt nearby symbiotes. */
	public static void onGameEvent(ServerLevel level, Holder<GameEvent> event, Vec3 pos) {
		double range;
		if (event.is(GameEvent.EXPLODE)) {
			range = EXPLOSION_RANGE;
		} else if (event.is(GameEvent.INSTRUMENT_PLAY)) {
			range = HORN_RANGE;
		} else if (event.is(GameEvent.NOTE_BLOCK_PLAY)) {
			range = NOTE_RANGE;
		} else if (event.is(GameEvent.BLOCK_CHANGE) && level.getBlockState(BlockPos.containing(pos)).is(Blocks.BELL)) {
			range = BELL_RANGE;
		} else {
			return;
		}
		for (ServerPlayer player : level.players()) {
			if (isBonded(player) && player.isAlive() && !player.isSpectator() && player.position().distanceToSqr(pos) <= range * range) {
				Live live = live(player);
				long now = level.getGameTime();
				if (now >= live.noiseReadyAt) {
					live.noiseReadyAt = now + NOISE_GRACE_TICKS;
					expose(player, Exposure.NOISE);
				}
			}
		}
	}

	/** Fire or noise: a painful shudder, stress, and one step closer to it leaving. */
	private static void expose(ServerPlayer player, Exposure kind) {
		WebSymbioteState state = state(player);
		if (!state.bonded()) {
			return;
		}
		ServerLevel level = player.level();
		int rejections = state.rejections() + 1;
		WebSymbioteState next = state.withRejections(rejections).withStress(Math.min(STRESS_MAX, state.stress() + EXPOSURE_STRESS));
		player.hurtServer(level, level.damageSources().magic(), EXPOSURE_DAMAGE);
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0, false, false));
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 1, false, false));
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 25, 0.35, 0.7, 0.35, 0.06);
		level.sendParticles(dust(1.5F), player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.5, 0.8, 0.5, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 1.2F, 0.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 0.5F, 1.8F);
		// the suit flinches off you for a moment: a little jolt upward
		ServerPlayNetworking.send(player, new WebNet.Nudge(new Vec3(0.0, 0.25, 0.0)));
		if (rejections >= REJECT_AT) {
			set(player, next);
			reject(player);
			return;
		}
		boolean firstTime = (next.hints() & (kind == Exposure.FIRE ? HINT_FIRE : HINT_NOISE)) == 0;
		next = kind == Exposure.FIRE ? hintOnce(player, next, HINT_FIRE, "fire") : hintOnce(player, next, HINT_NOISE, "noise");
		if (!firstTime && rejections == REJECT_AT - 1) {
			// one whisper at a time: the warning only comes on an exposure that isn't a first
			next = hintOnce(player, next, HINT_EDGE, "edge");
		}
		set(player, next);
	}

	// ---- takeover ----------------------------------------------------------------------------------------------------

	/** The nearest thing it wants: creatures (not pets or named ones); in a rampage, other players too. */
	private static @org.jspecify.annotations.Nullable LivingEntity prey(ServerPlayer player, boolean rampage) {
		LivingEntity target = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity living : player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(TAKEOVER_RANGE),
			e -> e != player && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand)
				&& (e instanceof Mob m ? !(m instanceof TamableAnimal tame && tame.isTame()) && !m.hasCustomName()
					: rampage && e instanceof Player p && !p.isCreative()))) {
			double d = living.distanceToSqr(player);
			if (d < best) {
				best = d;
				target = living;
			}
		}
		return target;
	}

	private static void startTakeover(ServerPlayer player, Live live, long now) {
		LivingEntity target = prey(player, live.rampage);
		live.takeoverReadyAt = now + TAKEOVER_COOLDOWN;
		if (target == null) {
			return;
		}
		live.rampageStrikes = 0;
		live.takeoverTicks = live.rampage ? Mth.nextInt(player.getRandom(), RAMPAGE_MIN_TICKS, RAMPAGE_MAX_TICKS)
			: Mth.nextInt(player.getRandom(), TAKEOVER_MIN_TICKS, TAKEOVER_MAX_TICKS);
		live.takeoverTarget = target.getId();
		ServerPlayNetworking.send(player, new WebNet.Takeover(target.getId(), live.takeoverTicks));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 1.3F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 0.8F, 0.6F);
		level.sendParticles(dust(1.6F), player.getX(), player.getY() + 1.0, player.getZ(), 25, 0.4, 0.8, 0.4, 0.0);
		whisper(player, "takeover");
	}

	private static void tickTakeover(ServerPlayer player, Live live, long now) {
		Entity target = player.level().getEntity(live.takeoverTarget);
		if (live.rampage && (target == null || !target.isAlive()) && live.takeoverTicks > 1) {
			// a rampage doesn't stop at one: on to the next
			LivingEntity next = prey(player, true);
			if (next != null) {
				live.takeoverTarget = next.getId();
				ServerPlayNetworking.send(player, new WebNet.Takeover(next.getId(), live.takeoverTicks));
				target = next;
			}
		}
		if (target == null || !target.isAlive() || --live.takeoverTicks <= 0) {
			live.rampage = false;
			live.takeoverTicks = 0;
			live.takeoverTarget = -1;
			ServerPlayNetworking.send(player, new WebNet.Takeover(-1, 0));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.8F, 1.4F);
			return;
		}
		if (live.takeoverTicks % TAKEOVER_ATTACK_TICKS == 0 && player.distanceTo(target) - target.getBbWidth() * 0.5 <= TAKEOVER_REACH) {
			player.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			player.attack(target);
			if (live.rampage && target instanceof LivingEntity living && living.isAlive()) {
				if (living instanceof Player && ++live.rampageStrikes % RAMPAGE_BITE_EVERY == 0) {
					// it bites: tearing into them, and feeding on it
					living.hurtServer(player.level(), player.level().damageSources().playerAttack(player), RAMPAGE_BITE_DAMAGE);
					player.heal(RAMPAGE_BITE_DAMAGE * 0.5F);
					feed(player, 10, 0);
					ServerLevel level = player.level();
					level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.PHANTOM_BITE, SoundSource.PLAYERS, 1.2F, 0.4F);
					level.sendParticles(ParticleTypes.SQUID_INK, living.getX(), living.getY() + 1.0, living.getZ(), 15, 0.3, 0.4, 0.3, 0.05);
				} else if (!(living instanceof Player) && living.getHealth() <= CONSUME_MAX_HEALTH) {
					// anything weak enough, it simply devours
					devour(player, living);
				}
			}
		}
		if (live.takeoverTicks % 5 == 0) {
			player.level().sendParticles(dust(1.0F), player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.3, 0.6, 0.3, 0.0);
		}
	}

	// ---- abilities ---------------------------------------------------------------------------------------------------

	/** A radial-menu pick. Server-authoritative: bond, cooldown and satiety are all checked here. */
	static void use(ServerPlayer player, int ability) {
		if (ability == SUIT_TOGGLE && isBonded(player)) {
			WebSymbioteState state = state(player);
			boolean on = !state.has(WebSymbioteState.SUIT);
			WebSymbioteState next = state.withFlag(WebSymbioteState.SUIT, on);
			if (!on && next.has(WebSymbioteState.ARMOR)) {
				// the suit comes off, and Living Armor with it
				next = next.withFlag(WebSymbioteState.ARMOR, false);
				removeArmor(player);
			}
			set(player, next);
			ServerLevel level = player.level();
			level.playSound(null, player.getX(), player.getY(), player.getZ(), on ? SoundEvents.SLIME_BLOCK_PLACE : SoundEvents.HONEY_BLOCK_SLIDE,
				SoundSource.PLAYERS, 0.8F, on ? 0.6F : 1.2F);
			level.sendParticles(dust(1.2F), player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.3, 0.7, 0.3, 0.0);
			return;
		}
		if (ability < 0 || ability >= ABILITIES || !isBonded(player) || !player.isAlive() || player.isSpectator()) {
			return;
		}
		Live live = live(player);
		if (live.takeoverTicks > 0 || live.bondTicks > 0) {
			// it isn't listening right now
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		WebSymbioteState.Cooldowns cooldowns = player.getAttached(COOLDOWNS);
		if (cooldowns == null) {
			cooldowns = WebSymbioteState.Cooldowns.NONE;
		}
		if (now < cooldowns.end(ability)) {
			return;
		}
		WebSymbioteState state = state(player);
		if (state.satiety() < COST[ability] && !player.isCreative()) {
			// too weak
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.5F, 0.4F);
			whisper(player, "weak");
			return;
		}
		boolean done = switch (ability) {
			case TENDRIL -> WebTendrils.radialGrab(player);
			case ARMOR -> toggle(player, WebSymbioteState.ARMOR);
			case CRAWL -> toggle(player, WebSymbioteState.CRAWL);
			case CONSUME -> consume(player);
			case SPIKES -> spikes(player);
			case SHROUD -> shroud(player);
			default -> false;
		};
		if (!done) {
			return;
		}
		player.setAttached(COOLDOWNS, cooldowns.with(ability, now + COOLDOWN[ability], ABILITIES));
		if (COST[ability] > 0 && !player.isCreative()) {
			WebSymbioteState after = state(player);
			set(player, after.withSatiety(Math.max(0, after.satiety() - COST[ability])));
		}
	}

	/** Tendril Grab: a creature under the crosshair is yanked to you; a block yanks you to it. */
	private static boolean tendril(ServerPlayer player) {
		ServerLevel level = player.level();
		Entity target = WebPower.entityInSight(player, TENDRIL_RANGE);
		Vec3 eye = player.getEyePosition();
		if (target != null) {
			Vec3 center = target.getBoundingBox().getCenter();
			double volume = target.getBbWidth() * target.getBbWidth() * target.getBbHeight();
			if (volume > TENDRIL_HEAVY_VOLUME) {
				int maxTicks = Mth.ceil(center.distanceTo(player.position()) / WebPower.ZIP_SPEED) + 12;
				WebPower.startPull(player, new WebNet.Pull(center, target.position(), target.getId(), maxTicks, (byte)-1),
					new WebLine(WebLine.YANK, center, target.getId()));
			} else {
				Vec3 to = player.position().subtract(target.position());
				double horizontal = to.horizontalDistance();
				double speed = Math.min(2.2, Math.max(0.0, horizontal - 1.5) * 0.16 + 0.2);
				double up = Mth.clamp(0.45 + to.y * 0.08 + horizontal * 0.01, 0.25, 1.2);
				Vec3 flat = new Vec3(to.x, 0.0, to.z).normalize().scale(speed);
				if (target instanceof Mob mob) {
					mob.getNavigation().stop();
				}
				target.setDeltaMovement(flat.x, up, flat.z);
				target.needsSync = true;
				target.syncVelocity = true;
				if (target instanceof ServerPlayer other) {
					ServerPlayNetworking.send(other, new WebNet.Nudge(new Vec3(flat.x, up, flat.z)));
				}
				WebPower.showLine(player, new WebLine(WebLine.YANK, center, target.getId()), 10);
			}
			trail(level, eye, center);
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 0.6F);
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.9F, 0.9F);
			return true;
		}
		BlockHitResult hit = WebAnchors.clip(player, eye, eye.add(player.getViewVector(1.0F).scale(TENDRIL_RANGE)));
		if (hit.getType() == HitResult.Type.MISS) {
			level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FISHING_BOBBER_THROW, SoundSource.PLAYERS, 0.4F, 0.8F);
			return false;
		}
		Vec3 anchor = hit.getLocation();
		Direction face = hit.getDirection();
		Vec3 feet = face == Direction.UP ? anchor.add(0.0, 0.05, 0.0)
			: face == Direction.DOWN ? anchor.add(0.0, -player.getBbHeight() - 0.1, 0.0)
			: anchor.add(face.getUnitVec3().scale(player.getBbWidth() * 0.5 + 0.2)).add(0.0, -1.3, 0.0);
		int maxTicks = Mth.ceil(feet.distanceTo(player.position()) / WebPower.ZIP_SPEED) + 12;
		WebPower.startPull(player, new WebNet.Pull(anchor, feet, -1, maxTicks, (byte)face.ordinal()), new WebLine(WebLine.ZIP, anchor, -1));
		trail(level, eye, anchor);
		level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 0.5F);
		return true;
	}

	private static void trail(ServerLevel level, Vec3 from, Vec3 to) {
		Vec3 d = to.subtract(from);
		int steps = Math.max(2, (int)(d.length() * 1.5));
		for (int i = 1; i <= steps; i++) {
			Vec3 at = from.add(d.scale(i / (double)steps));
			level.sendParticles(dust(0.9F), at.x, at.y, at.z, 1, 0.02, 0.02, 0.02, 0.0);
		}
	}

	private static boolean toggle(ServerPlayer player, int flag) {
		WebSymbioteState state = state(player);
		boolean on = !state.has(flag);
		WebSymbioteState next = state.withFlag(flag, on);
		if (flag == WebSymbioteState.ARMOR && on) {
			// Living Armor is the suit, swollen: it puts the suit on
			next = next.withFlag(WebSymbioteState.SUIT, true);
		}
		set(player, next);
		ServerLevel level = player.level();
		if (flag == WebSymbioteState.ARMOR) {
			if (on) {
				applyArmor(player);
				player.setAbsorptionAmount((float)Math.max(player.getAbsorptionAmount(), ARMOR_ABSORPTION * 0.5));
			} else {
				removeArmor(player);
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), on ? SoundEvents.SLIME_BLOCK_PLACE : SoundEvents.SLIME_BLOCK_BREAK,
				SoundSource.PLAYERS, 1.0F, on ? 0.5F : 0.8F);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
		} else {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), on ? SoundEvents.SPIDER_AMBIENT : SoundEvents.SPIDER_STEP,
				SoundSource.PLAYERS, 0.7F, on ? 0.6F : 1.0F);
		}
		level.sendParticles(dust(1.2F), player.getX(), player.getY() + 1.0, player.getZ(), on ? 18 : 8, 0.35, 0.7, 0.35, 0.0);
		return true;
	}

	private static void applyArmor(ServerPlayer player) {
		AttributeInstance absorb = player.getAttribute(Attributes.MAX_ABSORPTION);
		if (absorb != null && !absorb.hasModifier(ARMOR_ABSORB_ID)) {
			absorb.addOrUpdateTransientModifier(new AttributeModifier(ARMOR_ABSORB_ID, ARMOR_ABSORPTION, AttributeModifier.Operation.ADD_VALUE));
		}
		AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (knockback != null && !knockback.hasModifier(ARMOR_KNOCKBACK_ID)) {
			knockback.addOrUpdateTransientModifier(new AttributeModifier(ARMOR_KNOCKBACK_ID, ARMOR_KNOCKBACK_RESIST, AttributeModifier.Operation.ADD_VALUE));
		}
		// swollen with it: bigger than a body should be
		AttributeInstance scale = player.getAttribute(Attributes.SCALE);
		if (scale != null && !scale.hasModifier(ARMOR_SCALE_ID)) {
			scale.addOrUpdateTransientModifier(new AttributeModifier(ARMOR_SCALE_ID, ARMOR_SCALE, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
		}
	}

	private static void removeArmor(ServerPlayer player) {
		AttributeInstance absorb = player.getAttribute(Attributes.MAX_ABSORPTION);
		if (absorb != null && absorb.hasModifier(ARMOR_ABSORB_ID)) {
			absorb.removeModifier(ARMOR_ABSORB_ID);
		}
		AttributeInstance knockback = player.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
		if (knockback != null && knockback.hasModifier(ARMOR_KNOCKBACK_ID)) {
			knockback.removeModifier(ARMOR_KNOCKBACK_ID);
		}
		AttributeInstance scale = player.getAttribute(Attributes.SCALE);
		if (scale != null && scale.hasModifier(ARMOR_SCALE_ID)) {
			scale.removeModifier(ARMOR_SCALE_ID);
		}
	}

	/** The symbiote looks after itself (see {@link #INSTINCT_CHECK_TICKS}). Returns the state (the armour may come up). */
	private static WebSymbioteState instinct(ServerPlayer player, Live live, WebSymbioteState state, long now) {
		ServerLevel level = player.level();
		var random = player.getRandom();
		BlockPos fire = nearFire(player);
		boolean burning = player.isOnFire() || fire != null;
		boolean falling = !player.onGround() && player.getY() - player.yo < -0.55 && player.fallDistance > 5.0F;
		LivingEntity threat = null;
		double best = INSTINCT_THREAT_RANGE * INSTINCT_THREAT_RANGE;
		for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(INSTINCT_THREAT_RANGE),
			m -> m.isAlive() && m.getTarget() == player)) {
			double d = mob.distanceToSqr(player);
			if (d < best) {
				best = d;
				threat = mob;
			}
		}
		boolean danger = burning || falling || threat != null || player.getHealth() <= INSTINCT_LOW_HEALTH;
		if (!danger) {
			return state;
		}
		boolean armor = state.has(WebSymbioteState.ARMOR);
		if (!armor) {
			if (random.nextFloat() < INSTINCT_ARMOR_CHANCE) {
				// it hardens around you, unasked
				state = state.withFlag(WebSymbioteState.ARMOR, true).withFlag(WebSymbioteState.SUIT, true);
				applyArmor(player);
				player.setAbsorptionAmount((float)Math.max(player.getAbsorptionAmount(), ARMOR_ABSORPTION * 0.5));
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.0F, 0.5F);
				level.sendParticles(dust(1.4F), player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.8, 0.4, 0.0);
				state = hintOnce(player, state, HINT_INSTINCT, "instinct");
			}
			return state;
		}
		// a fall about to end: it reaches for the ground and takes the blow
		if (falling && !live.caughtFall && groundWithin(player, INSTINCT_FALL_CATCH_HEIGHT)) {
			live.caughtFall = true;
			player.resetFallDistance();
			ServerPlayNetworking.send(player, new WebNet.Nudge(new Vec3(0.0, -0.12, 0.0)));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, 1.0F, 0.5F);
			level.sendParticles(dust(1.6F), player.getX(), player.getY(), player.getZ(), 16, 0.6, 0.1, 0.6, 0.0);
			return state;
		}
		if (now < live.instinctReadyAt) {
			return state;
		}
		if (burning) {
			// it fears fire: it smothers the flames and shoves you clear
			live.instinctReadyAt = now + INSTINCT_COOLDOWN;
			player.clearFire();
			Vec3 away = fire == null ? player.getLookAngle().reverse() : player.position().subtract(Vec3.atCenterOf(fire));
			away = new Vec3(away.x, 0.0, away.z);
			away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
			ServerPlayNetworking.send(player, new WebNet.Nudge(away.scale(INSTINCT_FIRE_SHOVE).add(0.0, 0.4, 0.0)));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 0.7F, 1.4F);
			return state;
		}
		if (threat != null) {
			live.instinctReadyAt = now + INSTINCT_COOLDOWN;
			if (random.nextFloat() < INSTINCT_GRAB_CHANCE && WebTendrils.grabbable(player, threat)) {
				// it snatches the attacker and flings it off
				WebTendrils.snatch(player, threat);
			} else {
				// it shoves you out of reach
				Vec3 away = player.position().subtract(threat.position());
				away = new Vec3(away.x, 0.0, away.z);
				away = away.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : away.normalize();
				ServerPlayNetworking.send(player, new WebNet.Nudge(away.scale(INSTINCT_THREAT_SHOVE).add(0.0, 0.3, 0.0)));
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 0.5F);
			}
			return state;
		}
		return state;
	}

	/** Running with the armour up, now and then it catapults you along (checked with the instinct). */
	private static void catapult(ServerPlayer player, Live live, long now) {
		if (!player.isSprinting() || !player.onGround() || now < live.instinctReadyAt
			|| player.getRandom().nextFloat() >= INSTINCT_CATAPULT_CHANCE) {
			return;
		}
		live.instinctReadyAt = now + INSTINCT_COOLDOWN * 2;
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0.0, look.z);
		if (flat.lengthSqr() < 1.0E-4) {
			return;
		}
		ServerPlayNetworking.send(player, new WebNet.Nudge(flat.normalize().scale(INSTINCT_CATAPULT).add(0.0, 0.42, 0.0)));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_JUMP, SoundSource.PLAYERS, 1.0F, 0.4F);
		level.sendParticles(dust(1.4F), player.getX(), player.getY() + 0.2, player.getZ(), 14, 0.4, 0.1, 0.4, 0.0);
	}

	/** Fire or lava touching or right beside you (a small box: cheap). */
	private static @org.jspecify.annotations.Nullable BlockPos nearFire(ServerPlayer player) {
		AABB box = player.getBoundingBox().inflate(1.2, 0.3, 1.2);
		for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX, box.minY, box.minZ), BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
			net.minecraft.world.level.block.state.BlockState block = player.level().getBlockState(pos);
			if (block.is(net.minecraft.tags.BlockTags.FIRE) || block.getFluidState().is(net.minecraft.tags.FluidTags.LAVA)) {
				return pos.immutable();
			}
		}
		return null;
	}

	private static boolean groundWithin(ServerPlayer player, double height) {
		Vec3 feet = player.position();
		return player.level().clip(new net.minecraft.world.level.ClipContext(feet, feet.subtract(0.0, height, 0.0),
			net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.ANY, player)).getType()
			!= HitResult.Type.MISS;
	}

	/** The tendrils, on their own: now and then they find something close and strike it, slowly and hard. */
	private static void tickStrikes(ServerPlayer player, Live live, long now) {
		Strike strike = player.getAttached(STRIKE);
		ServerLevel level = player.level();
		if (strike != null) {
			long t = now - strike.start();
			Entity target = level.getEntity(strike.target());
			if (t == STRIKE_HIT && target instanceof LivingEntity living && living.isAlive()
				&& living.distanceTo(player) <= STRIKE_RANGE + 1.5 + living.getBbWidth() * 0.5) {
				living.hurtServer(level, level.damageSources().playerAttack(player), STRIKE_DAMAGE);
				Vec3 away = living.position().subtract(player.position()).horizontal();
				if (away.lengthSqr() > 1.0E-4) {
					Vec3 push = away.normalize().scale(0.6);
					living.push(push.x, 0.25, push.z);
					living.syncVelocity = true;
				}
				level.sendParticles(dust(1.4F), living.getX(), living.getY() + living.getBbHeight() * 0.6, living.getZ(), 14, 0.3, 0.3, 0.3, 0.0);
				level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1.0F, 0.5F);
				level.playSound(null, living.getX(), living.getY(), living.getZ(), SoundEvents.SLIME_ATTACK, SoundSource.PLAYERS, 1.0F, 0.5F);
			}
			if (t >= STRIKE_TICKS || target == null) {
				player.removeAttached(STRIKE);
				live.nextStrikeAt = now + STRIKE_EVERY;
			}
			return;
		}
		if (now < live.nextStrikeAt || live.takeoverTicks > 0 && !live.rampage) {
			return;
		}
		live.nextStrikeAt = now + 10;
		LivingEntity target = null;
		double best = STRIKE_RANGE * STRIKE_RANGE;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(STRIKE_RANGE),
			e -> e != player && e.isAlive() && !e.isSpectator() && (e instanceof net.minecraft.world.entity.monster.Enemy
				|| live.rampage && (e instanceof Mob || e instanceof Player p && !p.isCreative())))) {
			double d = living.distanceToSqr(player);
			if (d < best) {
				best = d;
				target = living;
			}
		}
		if (target != null) {
			player.setAttached(STRIKE, new Strike(target.getId(), now));
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_TENDRIL_CLICKS, SoundSource.PLAYERS, 1.0F, 0.6F);
		}
	}

	/** Consume: something small within reach (the one you look at, else the nearest) is swallowed whole. */
	private static boolean consume(ServerPlayer player) {
		ServerLevel level = player.level();
		// a corpse you're carrying, whatever its size, goes down whole
		Entity corpse = KitchenCompat.carriedCorpse(player);
		if (live(player).eatTarget >= 0) {
			return false;
		}
		if (corpse != null) {
			float size = Mth.clamp(corpse.getBbWidth() * corpse.getBbWidth() * corpse.getBbHeight() / 2.0F, 0.0F, 1.0F);
			startEating(player, corpse, Mth.lerp(size, CORPSE_HEAL_MIN, CORPSE_HEAL_MAX), Math.round(Mth.lerp(size, CONSUME_FEED, CORPSE_FEED_MAX)));
			return true;
		}
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0F);
		LivingEntity best = null;
		double bestScore = Double.MAX_VALUE;
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(CONSUME_RANGE),
			e -> e != player && e.isAlive() && !(e instanceof Player) && !(e instanceof ArmorStand) && e.getMaxHealth() <= CONSUME_MAX_HEALTH
				&& !(e instanceof TamableAnimal tame && tame.isTame()) && !e.hasCustomName())) {
			Vec3 to = living.getBoundingBox().getCenter().subtract(eye);
			double distance = to.length();
			if (distance > CONSUME_RANGE + living.getBbWidth() * 0.5) {
				continue;
			}
			// favour what you're looking at
			double score = distance - 2.0 * Math.max(0.0, to.normalize().dot(look));
			if (score < bestScore) {
				bestScore = score;
				best = living;
			}
		}
		if (best == null) {
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.5F, 1.4F);
			return false;
		}
		trail(level, eye.add(0.0, -0.3, 0.0), best.getBoundingBox().getCenter());
		startEating(player, best, CONSUME_HEAL, CONSUME_FEED);
		return true;
	}

	/** The jaws open on it: it's held where it is while they chomp, thrashing, and then it's gone. */
	private static void startEating(ServerPlayer player, Entity food, float heal, int feed) {
		Live live = live(player);
		long now = player.level().getGameTime();
		live.eatTarget = food.getId();
		live.eatUntil = now + EAT_TICKS;
		live.eatHeal = heal;
		live.eatFeed = feed;
		player.setAttached(EATING, now);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.5F, 1.6F);
	}

	private static void tickEating(ServerPlayer player, Live live, long now) {
		ServerLevel level = player.level();
		Entity food = level.getEntity(live.eatTarget);
		if (food == null || !food.isAlive() || food.distanceTo(player) > CONSUME_RANGE + 3.0) {
			live.eatTarget = -1;
			player.removeAttached(EATING);
			return;
		}
		long left = live.eatUntil - now;
		long t = EAT_TICKS - left;
		Vec3 at = food.getBoundingBox().getCenter();
		// held still in the jaws (a carried corpse is already held), shaking with every bite
		if (food instanceof LivingEntity living) {
			living.setDeltaMovement(Vec3.ZERO);
			living.syncVelocity = true;
			if (living instanceof Mob mob) {
				mob.getNavigation().stop();
			}
		}
		if (t % 3 == 0) {
			KitchenCompat.jolt(food);
			if (food instanceof LivingEntity living) {
				living.hurtTime = 6;
				level.broadcastEntityEvent(living, (byte)2);
			}
		}
		int every = EAT_TICKS / EAT_CHOMPS;
		if (t % every == every / 2) {
			// a bite
			level.playSound(null, at.x, at.y, at.z, SoundEvents.PHANTOM_BITE, SoundSource.PLAYERS, 1.1F, 0.4F + player.getRandom().nextFloat() * 0.2F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
			level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 10, food.getBbWidth() * 0.4, food.getBbHeight() * 0.3, food.getBbWidth() * 0.4, 0.06);
			level.sendParticles(dust(1.6F), at.x, at.y, at.z, 14, food.getBbWidth() * 0.5, food.getBbHeight() * 0.4, food.getBbWidth() * 0.5, 0.0);
			level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(0x7A0A0A, 1.2F), at.x, at.y, at.z, 10, 0.3, 0.3, 0.3, 0.0);
		}
		Long started = player.getAttached(EATING);
		boolean gulp = started != null && t % every == every / 2 && t / every == EAT_CHOMPS - 1
			&& biteSize(started, player.getId(), EAT_CHOMPS - 1) >= 1.0F;
		if (gulp) {
			// the maw opened all the way: it takes the whole thing in one
			level.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 0.6F, 1.6F);
			level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EAT.value(), SoundSource.PLAYERS, 1.3F, 0.3F);
		} else if (left > 0) {
			return;
		}
		// swallowed whole: nothing left behind
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 25, food.getBbWidth() * 0.4, food.getBbHeight() * 0.4, food.getBbWidth() * 0.4, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_BURP, SoundSource.PLAYERS, 0.8F, 0.6F);
		food.discard();
		player.heal(live.eatHeal);
		feed(player, live.eatFeed, CONSUME_CALM);
		// and you're fed too: about a hunger point per half-heart it healed
		player.getFoodData().eat(Math.max(2, Math.round(live.eatHeal * CONSUME_HUNGER_PER_HEAL)), CONSUME_SATURATION);
		live.eatTarget = -1;
		player.removeAttached(EATING);
	}

	/** Feeds the symbiote (and calms it). */
	private static void feed(ServerPlayer player, int satiety, int calm) {
		WebSymbioteState state = state(player);
		set(player, state.withSatiety(Math.min(SATIETY_MAX, state.satiety() + satiety)).withStress(Math.max(0, state.stress() - calm)));
	}

	/** Swallowed whole in a rampage. */
	private static void devour(ServerPlayer player, LivingEntity living) {
		ServerLevel level = player.level();
		Vec3 at = living.getBoundingBox().getCenter();
		level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 20, living.getBbWidth() * 0.4, living.getBbHeight() * 0.4, living.getBbWidth() * 0.4, 0.05);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.PHANTOM_BITE, SoundSource.PLAYERS, 1.0F, 0.5F);
		living.discard();
		player.heal(CONSUME_HEAL);
		feed(player, CONSUME_FEED, CONSUME_CALM);
	}

	/** Spike Burst: black spikes burst out all around, hurting and hurling everything near. */
	private static boolean spikes(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 center = player.position().add(0.0, 1.0, 0.0);
		for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(SPIKE_RADIUS),
			e -> e != player && e.isAlive() && !e.isSpectator())) {
			Vec3 to = living.getBoundingBox().getCenter().subtract(center);
			if (to.length() > SPIKE_RADIUS + living.getBbWidth() * 0.5) {
				continue;
			}
			living.hurtServer(level, level.damageSources().playerAttack(player), SPIKE_DAMAGE);
			Vec3 flat = new Vec3(to.x, 0.0, to.z);
			flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : flat.normalize();
			Vec3 push = flat.scale(SPIKE_KNOCKBACK).add(0.0, 0.45, 0.0);
			living.setDeltaMovement(living.getDeltaMovement().add(push));
			living.needsSync = true;
			living.syncVelocity = true;
			if (living instanceof ServerPlayer other) {
				ServerPlayNetworking.send(other, new WebNet.Nudge(push));
			}
		}
		// the spikes: rays of black out from the body, in a ring
		for (int ray = 0; ray < 24; ray++) {
			double angle = ray * Math.PI * 2.0 / 24.0;
			double tilt = (ray % 3 - 1) * 0.35;
			Vec3 dir = new Vec3(Math.cos(angle), tilt, Math.sin(angle));
			for (int k = 1; k <= 5; k++) {
				Vec3 at = center.add(dir.scale(k * SPIKE_RADIUS / 5.0));
				level.sendParticles(k == 5 ? sheen(1.2F) : dust(1.6F - k * 0.2F), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
		level.sendParticles(ParticleTypes.SQUID_INK, center.x, center.y, center.z, 30, 0.5, 0.6, 0.5, 0.25);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.EVOKER_FANGS_ATTACK, SoundSource.PLAYERS, 1.2F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.POINTED_DRIPSTONE_LAND, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_DEATH, SoundSource.PLAYERS, 0.8F, 0.5F);
		return true;
	}

	/** Shroud: the suit swallows the light; you vanish until you strike, or until it tires. */
	private static boolean shroud(ServerPlayer player) {
		ServerLevel level = player.level();
		player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, SHROUD_TICKS, 0, false, false));
		live(player).shroudUntil = level.getGameTime() + SHROUD_TICKS;
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 25, 0.35, 0.8, 0.35, 0.03);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 0.8, player.getZ(), 10, 0.35, 0.6, 0.35, 0.01);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 1.0F, 0.6F);
		return true;
	}

	private static void endShroud(ServerPlayer player) {
		Live live = player.getAttached(LIVE);
		if (live == null || live.shroudUntil <= 0) {
			return;
		}
		live.shroudUntil = 0;
		player.removeEffect(MobEffects.INVISIBILITY);
		ServerLevel level = player.level();
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 15, 0.3, 0.7, 0.3, 0.03);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ILLUSIONER_CAST_SPELL, SoundSource.PLAYERS, 0.6F, 0.6F);
	}

	/** Who's holding a symbiote's attention, for debug output. */
	public static String describe(Player player) {
		WebSymbioteState s = state(player);
		return "bonded=" + s.bonded() + " satiety=" + s.satiety() + " stress=" + s.stress() + " rejections=" + s.rejections() + " flags=" + s.flags();
	}
}
