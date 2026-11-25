package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumables;

/**
 * Molting (patch 4 of the 1.1.3 picks; the lore is "What it does when you aren't looking"). A bonded symbiote outgrows its
 * skin. Every {@link #EVERY_MIN} to {@link #EVERY_MAX} ticks (a week or more of days) from the Integrated stage it molts for
 * one day ({@link #LENGTH}): it itches, asks you to stay close ({@link #CLOSE} blocks of where it began) and not to run, and
 * at the end the old skin splits off at your feet as a {@link #SKIN}. Through the day it counts the time you spent running
 * (sprinting), fighting, and away. A molt you got through calmly ({@link #RAN_OK}, {@link #FOUGHT_OK}, {@link #AWAY_OK})
 * binds it tighter: {@link #CALM_BOND} bond, calm and trust. One you ran or fought through costs {@link #ROUGH_STRESS}
 * stress and trust, and each stretch of running or fighting costs a point of stress as it happens. With the Settings
 * screen's requests switched off ({@link WebSymbioteSettings.Setting#REQUESTS}) it asks nothing and keeps no score: the skin
 * still comes.
 * <p>
 * The skin is food ({@link #SKIN}), counted as meat for the symbiote ({@link #MEAT}: also whatever other mods call raw meat
 * or organs). Eaten while it is dormant (asleep from healing, or settling in), it cuts {@link #MEND_TICKS} off the sleep
 * ({@link #eaten}).
 */
public final class WebSymbioteMolt {
	// ---- tuning ----
	/** The stage a molt begins at (Integrated), how long a molt lasts, and the wait between molts (ticks). */
	public static final int MOLT_STAGE = 1;
	public static final long LENGTH = 24000L, EVERY_MIN = 7 * 24000L, EVERY_MAX = 12 * 24000L;
	/** How often it looks at how you are doing (ticks), and how far from where it began counts as close (blocks). */
	static final int STEP = 20, CLOSE = 48;
	/** Over this much time running, fighting or away (ticks) it was not a calm molt. */
	static final int RAN_OK = 1200, FOUGHT_OK = 400, AWAY_OK = 2400;
	/** It itches (a line) this often (ticks), and scolds a runner no more often than this. */
	static final int ITCH_EVERY = 3000, RUN_SCOLD = 600;
	/** A calm molt: bond and trust gained, stress taken off. A rough one: stress and trust. */
	static final int CALM_BOND = 2, CALM_TRUST = 3, CALM_RELIEF = 15, ROUGH_STRESS = 14, ROUGH_TRUST = 2;
	/** The skin eaten while it sleeps: ticks off the sleep (but never to nothing: it wakes in its own time). */
	static final int MEND_TICKS = 2400;
	static final int LINES = 3;

	/** The molt in progress: when the next one is due, when this one began (0: none), where, and the ticks spent running, fighting, away. */
	public record Molt(long next, long since, long anchor, int ran, int fought, int away) {
		public static final Molt NONE = new Molt(0L, 0L, 0L, 0, 0, 0);
		public static final Codec<Molt> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("next", 0L).forGetter(Molt::next),
			Codec.LONG.optionalFieldOf("since", 0L).forGetter(Molt::since),
			Codec.LONG.optionalFieldOf("anchor", 0L).forGetter(Molt::anchor),
			Codec.INT.optionalFieldOf("ran", 0).forGetter(Molt::ran),
			Codec.INT.optionalFieldOf("fought", 0).forGetter(Molt::fought),
			Codec.INT.optionalFieldOf("away", 0).forGetter(Molt::away)).apply(i, Molt::new));

		boolean molting() {
			return this.since > 0L;
		}
	}

	/** Its molting. Saved, kept through death. */
	public static final AttachmentType<Molt> MOLT = AttachmentRegistry.create(JaymosPowers.id("web_symbiote_molt"),
		builder -> builder.persistent(Molt.CODEC).copyOnDeath());

	/** The cast skin: a thin black husk. Food. */
	public static Item SKIN;
	/** What the symbiote eats as raw meat besides the plain list: the skin, and other mods' raw meat and organs. */
	public static final TagKey<Item> MEAT = TagKey.create(Registries.ITEM, JaymosPowers.id("symbiote_meat"));

	private WebSymbioteMolt() {
	}

	static void register() {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, JaymosPowers.id("symbiote_skin"));
		SKIN = Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)
			.food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.3F).alwaysEdible().build(), Consumables.defaultFood().build())));
		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.FOOD_AND_DRINKS).register(output -> output.accept(SKIN));
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.tickCount % STEP == 0) {
					tick(player);
				}
			}
		});
	}

	public static Molt molt(ServerPlayer player) {
		return player.getAttachedOrElse(MOLT, Molt.NONE);
	}

	/** It is in the middle of a molt right now. */
	public static boolean molting(ServerPlayer player) {
		return molt(player).molting();
	}

	private static boolean asks(ServerPlayer player) {
		return !WebSymbioteSettings.off(player, WebSymbioteSettings.Setting.REQUESTS);
	}

	private static void tick(ServerPlayer player) {
		WebSymbioteState state = WebSymbiote.state(player);
		if (!state.bonded() || WebSymbioteMind.stage(state.bond()) < MOLT_STAGE) {
			return;
		}
		ServerLevel level = player.level();
		long now = level.getGameTime();
		Molt molt = molt(player);
		if (!molt.molting()) {
			if (molt.next() == 0L) {
				player.setAttached(MOLT, new Molt(now + EVERY_MIN + (long)(player.getRandom().nextFloat() * (EVERY_MAX - EVERY_MIN)), 0L, 0L, 0, 0, 0));
			} else if (now >= molt.next() && player.isAlive() && !WebSymbioteBond.dormant(player)) {
				begin(player, molt, now);
			}
			return;
		}
		if (now - molt.since() >= LENGTH) {
			finish(player, molt, state, now);
			return;
		}
		int ran = molt.ran();
		int fought = molt.fought();
		int away = molt.away();
		boolean scored = asks(player);
		if (player.isSprinting()) {
			ran += STEP;
			if (scored) {
				WebSymbioteMind.addStress(player, 1);
				if (ran % RUN_SCOLD < STEP) {
					WebSymbiote.whisper(player, "molt.run." + (1 + player.getRandom().nextInt(LINES)));
				}
			}
		}
		if (player.tickCount - player.getLastHurtMobTimestamp() < STEP * 2 || player.tickCount - player.getLastHurtByMobTimestamp() < STEP * 2) {
			fought += STEP;
			if (scored) {
				WebSymbioteMind.addStress(player, 1);
			}
		}
		if (BlockPos.of(molt.anchor()).distSqr(player.blockPosition()) > (double)CLOSE * CLOSE) {
			away += STEP;
		}
		if (scored && (now - molt.since()) % ITCH_EVERY < STEP) {
			WebSymbiote.whisper(player, "molt.itch." + (1 + player.getRandom().nextInt(LINES)));
		}
		if ((now - molt.since()) % 100 < STEP && !player.isInvisible()) {
			level.sendParticles(WebSymbiote.dust(0.6F), player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.3, 0.6, 0.3, 0.0);
		}
		player.setAttached(MOLT, new Molt(molt.next(), molt.since(), molt.anchor(), ran, fought, away));
	}

	private static void begin(ServerPlayer player, Molt molt, long now) {
		player.setAttached(MOLT, new Molt(molt.next(), now, player.blockPosition().asLong(), 0, 0, 0));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.6F, 0.5F);
		if (asks(player)) {
			WebSymbiote.whisper(player, "molt.begin." + (1 + player.getRandom().nextInt(LINES)));
		}
	}

	private static void finish(ServerPlayer player, Molt molt, WebSymbioteState state, long now) {
		ServerLevel level = player.level();
		boolean calm = molt.ran() <= RAN_OK && molt.fought() <= FOUGHT_OK && molt.away() <= AWAY_OK;
		player.setAttached(MOLT, new Molt(now + EVERY_MIN + (long)(player.getRandom().nextFloat() * (EVERY_MAX - EVERY_MIN)), 0L, 0L, 0, 0, 0));
		if (asks(player)) {
			if (calm) {
				WebSymbiote.set(player, state.withBond(Mth.clamp(state.bond() + CALM_BOND, 0, WebSymbiote.BOND_MAX)));
				WebSymbioteMind.addTrust(player, CALM_TRUST);
				WebSymbioteMind.addStress(player, -CALM_RELIEF);
				WebSymbiote.whisper(player, "molt.calm." + (1 + player.getRandom().nextInt(LINES)));
			} else {
				WebSymbioteMind.addStress(player, ROUGH_STRESS);
				WebSymbioteMind.addTrust(player, -ROUGH_TRUST);
				WebSymbiote.whisper(player, "molt.rough." + (1 + player.getRandom().nextInt(LINES)));
			}
		}
		ItemEntity skin = new ItemEntity(level, player.getX(), player.getY() + 0.4, player.getZ(), new ItemStack(SKIN));
		level.addFreshEntity(skin);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.9F, 0.6F);
		level.sendParticles(WebSymbiote.dust(1.1F), player.getX(), player.getY() + 1.0, player.getZ(), 16, 0.35, 0.7, 0.35, 0.0);
	}

	/**
	 * A bonded host has just eaten {@code stack} for the symbiote. The cast skin, eaten while it is dormant (asleep from
	 * healing, or settling in), cuts {@link #MEND_TICKS} off the sleep, leaving it a moment to wake in its own way.
	 */
	static void eaten(ServerPlayer player, ItemStack stack) {
		if (!stack.is(SKIN) || !WebSymbioteBond.dormant(player)) {
			return;
		}
		int left = WebSymbioteBond.dormantTicks(player);
		WebSymbioteBond.setDormant(player, Math.max(WebSymbioteBond.DORMANT_STEP, left - MEND_TICKS));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.6F, 0.7F);
	}
}
