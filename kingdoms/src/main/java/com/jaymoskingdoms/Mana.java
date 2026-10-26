package com.jaymoskingdoms;

import com.jaymoskingdoms.Classes.PlayerClass;
import com.mojang.serialization.Codec;
import java.util.Map;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * (0.7.212) Mana and attunement. Andy (2026-10-26): "some kind of attunement system, adding mana for users that either
 * spawn as wizards or sorcerers, or find an amethyst deposit, this imbues the user with mana overloading them and their
 * field of view with a blue hue, then dissipating. mana is displayed as the glowing ki that z fighters had previously. now
 * it is only visible by those who are ALSO attuned. same goes for some of their abilities. the glow is more faint than
 * before, and intensifies with the user's mana. upon unlocking it becomes another bar in the attributes tree. This allows
 * non wizards and sorcerers to use spells, but only after some acquisition, and they will never be as strong as a natural
 * sorcerer or learned wizard."
 * <ul>
 * <li><b>Attuned:</b> a Mage or a Sorcerer the moment they take the class (a <i>natural</i>); anyone else who comes within
 * {@link #AMETHYST_RANGE} of a budding amethyst (a geode's heart). Attunement overloads them: their mana fills to
 * {@link #OVERLOAD} times its most, their sight goes blue (the client's {@code ManaClient}), and the extra bleeds away.</li>
 * <li><b>Mana:</b> casting costs it ({@link #COST}, {@link #spend}); it comes back by itself. A natural holds
 * {@link #NATURAL_MAX} and gets it back faster; one who found it holds {@link #FOUND_MAX}. The Arcana attribute
 * ({@link Stats.Stat#ARCANA}, shown only to the attuned) raises both.</li>
 * <li><b>Seen by the attuned only:</b> a faint glow round the attuned, brighter the fuller their mana ({@link #GLOW}), and
 * the quiet signs of their magic ({@link #sendSeen}): an imbued blade's trail, Lifebloom's threads.</li>
 * <li><b>Spells for the rest:</b> an attuned player who isn't a Mage or Sorcerer learns a scroll's spell by reading it
 * and casts it from a staff, as hard as a scroll ({@link Spellcraft#ATTUNED_POWER}), never as hard as a Mage.</li>
 * </ul>
 * (0.7.235) Andy: "court wizards and clerics should have to be attuned as well. meaning other magic users can see their
 * glow, and know their power level from the intensity of the glow ... amethyst should be now crushable for mana. add a type
 * of mana sickness for punishing over zealous casters ... Arcana increases that minimum, so you now glow for others the most
 * dim at your weakest. A mana infused wand ... mana can be regenerated using meditation from the survival tree. when
 * casting a spell the aura should ripple, indicating the caster's intent."
 * <ul>
 * <li><b>Power:</b> every attuned being has a {@link #POWER} (1-{@link #MAX_POWER}), synced to all: a player's from their
 * most mana (so Arcana raises it), the casting folk's from what they are ({@link ManaFolk}). The glow never sinks below
 * what their power shows; the attuned read a stranger's strength from it.</li>
 * <li><b>Amethyst:</b> an attuned player crushes a shard in their fist (use it) for {@link #CRUSH_MANA}, even past full; what
 * goes past full strains them ({@link ManaSickness}).</li>
 * <li><b>Meditation</b> (Jaymo's Powers' Meditate key, opened in Survival): mana comes back {@link #MEDITATE_REGEN} times as
 * fast, by rank, and strain eases faster.</li>
 * <li><b>Intent:</b> every cast ripples the caster's aura ({@link #RIPPLE}), coloured by what it's for: harm, healing,
 * warding, or will (summoning, knowing, moving mana).</li>
 * <li>Mana sickness ({@link ManaSickness}), alchemy's vis and corruption ({@link ManaAlchemy}), the mana wand
 * ({@link ManaWand}).</li>
 * </ul>
 */
public final class Mana {
	/** The most mana a natural (Mage, Sorcerer) and one who found it hold, and what each Arcana point adds. */
	static final float NATURAL_MAX = 100.0F, FOUND_MAX = 50.0F, NATURAL_PER_POINT = 10.0F, FOUND_PER_POINT = 5.0F;
	/** Mana back per second, a natural's and a finder's, and what each Arcana point adds. */
	static final float NATURAL_REGEN = 2.0F, FOUND_REGEN = 1.0F, REGEN_PER_POINT = 0.15F;
	/** Attunement fills mana to this share of its most; over the most it bleeds away this much a second. */
	static final float OVERLOAD = 1.5F, SPILL = 3.0F;
	/** How long the blue overload lasts on screen and in the glow (ticks). */
	public static final int OVERLOAD_TICKS = 200;
	/** How near a budding amethyst attunes you (blocks), and how often it's looked for (ticks). */
	static final int AMETHYST_RANGE = 3, AMETHYST_EVERY = 40;
	/** How far off the attuned see another's magic (blocks). */
	static final double SEEN_RANGE = 48.0;
	/** The glow's steps (synced): 0 none ... {@code GLOW_STEPS} full. */
	public static final int GLOW_STEPS = 10;
	/** (0.7.235) A player's power: one step for every this much of their most mana (a finder 2, a fresh caster 4). */
	static final float MANA_PER_POWER = 25.0F;
	public static final int MAX_POWER = 10;
	/** (0.7.235) Mana from one amethyst shard crushed in the fist, and the wait between crushes (ticks). */
	static final float CRUSH_MANA = 10.0F;
	static final int CRUSH_COOLDOWN = 10;
	/** (0.7.235) Mana comes back this many times as fast while meditating, by Meditation rank (0-3). */
	static final float[] MEDITATE_REGEN = {1.0F, 3.0F, 4.0F, 5.0F};
	/** (0.7.235) A cast's intent, shown in the ripple of the caster's aura. */
	public static final int HARM = 0, HEAL = 1, WARD = 2, WILL = 3;

	/**
	 * What each spell costs: a staff cast (Mage or attuned) and the Sorcerer's keys. A scroll read once costs nothing: the
	 * magic is in the scroll.
	 */
	static final Map<String, Float> COST = Map.ofEntries(
		Map.entry("fire", 10.0F), Map.entry("arcane", 8.0F), Map.entry("warding", 30.0F), Map.entry("draining", 15.0F),
		Map.entry("raising", 40.0F), Map.entry("mending", 15.0F), Map.entry("sanctuary", 35.0F), Map.entry("restoration", 60.0F),
		// the Sorcerer's
		Map.entry("imbue", 20.0F), Map.entry("bolt", 8.0F), Map.entry("mend", 15.0F), Map.entry("renewal", 30.0F),
		Map.entry("rekindle", 40.0F),
		// (0.7.231) death's (blood's are paid in blood: Hemomancy.COST)
		Map.entry("blight", 15.0F), Map.entry("whispers", 5.0F), Map.entry("deaths_door", 50.0F), Map.entry("reap", 10.0F), Map.entry("raise", 30.0F));

	/** (0.7.235) What each spell or key is for (the rest: harm). */
	static final Map<String, Integer> INTENT = Map.ofEntries(
		Map.entry("mending", HEAL), Map.entry("restoration", HEAL), Map.entry("mend", HEAL), Map.entry("renewal", HEAL),
		Map.entry("rekindle", HEAL), Map.entry("warding", WARD), Map.entry("sanctuary", WARD), Map.entry("deaths_door", WARD),
		Map.entry("raising", WILL), Map.entry("raise", WILL), Map.entry("whispers", WILL));

	/** Whether a player is attuned. Saved, kept through death, synced to everyone (who sees whose glow). */
	public static final AttachmentType<Boolean> ATTUNED = AttachmentRegistry.create(JaymosKingdoms.id("attuned"),
		builder -> builder.persistent(Codec.BOOL).copyOnDeath().syncWith(ByteBufCodecs.BOOL, AttachmentSyncPredicate.all()));
	/** Their mana now. Saved, kept through death, synced to them (the K screen). */
	public static final AttachmentType<Float> MANA = AttachmentRegistry.create(JaymosKingdoms.id("mana"),
		builder -> builder.persistent(Codec.FLOAT).copyOnDeath().syncWith(ByteBufCodecs.FLOAT, AttachmentSyncPredicate.targetOnly()));
	/** How full their mana is, in {@link #GLOW_STEPS} (more than that: overloaded). Synced to everyone, for the glow. */
	public static final AttachmentType<Integer> GLOW = AttachmentRegistry.create(JaymosKingdoms.id("mana_glow"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** When they were last overloaded (game time). Synced to everyone: the blue sight, the flare of the glow. */
	public static final AttachmentType<Long> OVERLOADED = AttachmentRegistry.create(JaymosKingdoms.id("mana_overloaded"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	/**
	 * (0.7.235) How strong an attuned being is (1-{@link #MAX_POWER}), shown in how bright their glow sits at its least.
	 * Saved (on folk), synced to everyone.
	 */
	public static final AttachmentType<Integer> POWER = AttachmentRegistry.create(JaymosKingdoms.id("mana_power"),
		builder -> builder.persistent(Codec.INT).syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** (0.7.235) Their last cast: game time times 4, plus its intent ({@link #HARM}...). Synced to everyone, for the ripple. */
	public static final AttachmentType<Long> RIPPLE = AttachmentRegistry.create(JaymosKingdoms.id("mana_ripple"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_LONG, AttachmentSyncPredicate.all()));

	/** The overload's blue motes. */
	public static final DustParticleOptions MOTE = new DustParticleOptions(0x4C8DFF, 1.0F);

	private Mana() {
	}

	static void register() {
		ManaSickness.register();
		ManaFolk.register();
		ManaWand.register();
		// (their attachments, registered as the classes load)
		JaymosKingdoms.LOGGER.debug("mana attachments: {} {}", ManaSickness.SICK, ManaAlchemy.ATTUNING);
		// (0.7.235) an amethyst shard crushed in the fist
		UseItemCallback.EVENT.register((player, world, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(Items.AMETHYST_SHARD) || !attuned(player) || player.isSpectator()) {
				return InteractionResult.PASS;
			}
			if (player instanceof ServerPlayer sp) {
				crush(sp, stack);
			}
			return InteractionResult.SUCCESS;
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(Commands.literal("kingdom")
			.then(Commands.literal("mana").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("attune").executes(c -> attuneCommand(c.getSource(), c.getSource().getPlayerOrException()))
					.then(Commands.argument("player", EntityArgument.player()).executes(c -> attuneCommand(c.getSource(), EntityArgument.getPlayer(c, "player")))))
				.then(Commands.literal("fill").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					set(p, max(p));
					return 1;
				}))
				.then(Commands.literal("clear").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					p.removeAttached(ATTUNED);
					p.removeAttached(MANA);
					p.removeAttached(GLOW);
					p.removeAttached(POWER);
					return 1;
				})))));
	}

	private static int attuneCommand(CommandSourceStack source, ServerPlayer player) {
		attune(player);
		source.sendSuccess(() -> Component.literal(player.getGameProfile().name() + " is attuned"), true);
		return 1;
	}

	// ---- who and how much -------------------------------------------------------------------------------------------

	public static boolean attuned(Entity entity) {
		return entity.getAttachedOrElse(ATTUNED, false);
	}

	/** A natural: an attuned Mage or Sorcerer. */
	public static boolean natural(Player player) {
		return attuned(player) && (Classes.is(player, PlayerClass.MAGE) || Classes.is(player, PlayerClass.SORCERER));
	}

	public static float current(Player player) {
		return player.getAttachedOrElse(MANA, 0.0F);
	}

	/** The most mana {@code player} holds (0 unattuned). */
	public static float max(Player player) {
		if (!attuned(player)) {
			return 0.0F;
		}
		int points = Stats.value(player, Stats.Stat.ARCANA);
		// (0.7.235) corruption eats into it
		return (natural(player) ? NATURAL_MAX + NATURAL_PER_POINT * points : FOUND_MAX + FOUND_PER_POINT * points) * ManaAlchemy.left(player);
	}

	/** (0.7.235) How strong {@code entity}'s magic is (0 unattuned): a player's from their most mana, folk's as they are. */
	public static int power(LivingEntity entity) {
		if (!attuned(entity)) {
			return 0;
		}
		if (entity instanceof Player player) {
			return Math.clamp(Math.round(max(player) / MANA_PER_POWER), 1, MAX_POWER);
		}
		return entity.getAttachedOrElse(POWER, 1);
	}

	/** Mana back per second. */
	public static float regen(Player player) {
		if (!attuned(player)) {
			return 0.0F;
		}
		return (natural(player) ? NATURAL_REGEN : FOUND_REGEN) + REGEN_PER_POINT * Stats.value(player, Stats.Stat.ARCANA);
	}

	/**
	 * Takes {@code cost} from {@code player}'s mana; false (and a word on the screen) if they haven't enough, or aren't
	 * attuned. Creative players cast for free.
	 */
	static boolean spend(ServerPlayer player, float cost) {
		if (!afford(player, cost)) {
			return false;
		}
		take(player, cost);
		return true;
	}

	/** (0.7.235) Pays for {@code what} (a spell or a Sorcerer's key) and ripples the aura with its intent; false if they can't. */
	static boolean cast(ServerPlayer player, String what) {
		if (!spend(player, cost(what))) {
			return false;
		}
		ripple(player, intent(what));
		return true;
	}

	/**
	 * (0.7.236) Pays {@code cost} for something outside the spell lists (a spirit's move) and ripples the aura with
	 * {@code intent}; false (and a word on the screen) if they can't.
	 */
	public static boolean pay(ServerPlayer player, float cost, int intent) {
		if (!spend(player, cost)) {
			return false;
		}
		ripple(player, intent);
		return true;
	}

	/** (0.7.236) Takes {@code amount} from {@code player}'s mana with no ripple and no strain (a spirit's upkeep, a release). */
	public static void drain(ServerPlayer player, float amount) {
		if (!player.isCreative() && attuned(player)) {
			set(player, current(player) - amount);
		}
	}

	/** (0.7.236) Whether {@code player} could pay {@code cost} now, quietly (no word on the screen). */
	public static boolean canPay(ServerPlayer player, float cost) {
		return player.isCreative() || attuned(player) && !ManaSickness.burntOut(player) && current(player) >= price(player, cost);
	}

	/** (0.7.235) What a cast at this {@code cost} really takes from {@code player}: dearer sick, or near the corrupt. */
	static float price(ServerPlayer player, float cost) {
		return cost * ManaSickness.costTimes(player) * ManaAlchemy.smother(player);
	}

	/** Whether {@code player} has {@code cost} to spend (a word on the screen if not). Creative players always have. */
	static boolean afford(ServerPlayer player, float cost) {
		if (player.isCreative()) {
			return true;
		}
		if (!attuned(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.mana.unattuned").withStyle(ChatFormatting.BLUE));
			return false;
		}
		if (ManaSickness.burntOut(player)) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.mana.burnt_out").withStyle(ChatFormatting.DARK_AQUA));
			return false;
		}
		float now = current(player), price = price(player, cost);
		if (now < price) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.mana.low", Math.round(now), Math.round(price)).withStyle(ChatFormatting.BLUE));
			return false;
		}
		return true;
	}

	/** Takes {@code cost} (what {@link #afford} said they had). */
	static void take(ServerPlayer player, float cost) {
		if (!player.isCreative()) {
			float price = price(player, cost);
			if (ManaAlchemy.smother(player) > 1.0F) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.mana.smothered").withStyle(ChatFormatting.DARK_PURPLE));
			}
			set(player, current(player) - price);
			// (0.7.236) casting while the spirit stirs in its sleep wakes it sooner
			com.jaymoskingdoms.spirits.SpiritSickness.pushed(player, price);
			// (0.7.235) every cast strains: cast too much too fast and it makes you sick
			ManaSickness.strain(player, price);
		}
	}

	/**
	 * (0.7.235) Mana poured into {@code player} from outside (amethyst, a wand), up to {@link #OVERLOAD} times their most;
	 * what goes past full strains them. How much went in.
	 */
	static float give(ServerPlayer player, float amount) {
		float max = max(player), now = current(player);
		float in = Math.max(0.0F, Math.min(amount, max * OVERLOAD - now));
		if (in <= 0.0F) {
			return 0.0F;
		}
		set(player, now + in);
		float over = Math.min(in, now + in - max);
		if (over > 0.0F) {
			ManaSickness.strain(player, over);
		}
		return in;
	}

	/** (0.7.235) What {@code what} (a spell, a key, an element) is for. */
	static int intent(String what) {
		return INTENT.getOrDefault(what, HARM);
	}

	/** (0.7.235) {@code caster}'s aura ripples with a cast's {@code intent} (once a tick; only the attuned have an aura). */
	public static void ripple(LivingEntity caster, int intent) {
		if (!(caster.level() instanceof ServerLevel level)) {
			return;
		}
		if (!(caster instanceof Player)) {
			ManaFolk.attuneIfMagic(caster);
		}
		if (!attuned(caster)) {
			return;
		}
		long stamp = level.getGameTime() * 4L + intent;
		Long last = caster.getAttached(RIPPLE);
		if (last == null || last / 4L != level.getGameTime()) {
			caster.setAttached(RIPPLE, stamp);
		}
	}

	/** (0.7.235) An amethyst shard crushed in the fist: mana, a chime, violet dust. */
	private static void crush(ServerPlayer player, ItemStack stack) {
		if (player.getCooldowns().isOnCooldown(stack)) {
			return;
		}
		float in = player.isCreative() ? CRUSH_MANA : give(player, CRUSH_MANA);
		if (in <= 0.0F) {
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.mana.brimming").withStyle(ChatFormatting.BLUE));
			return;
		}
		if (!player.isCreative()) {
			stack.shrink(1);
		}
		player.getCooldowns().addCooldown(stack, CRUSH_COOLDOWN);
		player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
		ServerLevel level = (ServerLevel)player.level();
		net.minecraft.world.phys.Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.5)).subtract(0.0, 0.3, 0.0);
		level.sendParticles(new net.minecraft.core.particles.ItemParticleOption(ParticleTypes.ITEM, Items.AMETHYST_SHARD), hand.x, hand.y, hand.z, 10,
			0.1, 0.1, 0.1, 0.05);
		sendSeen(level, MOTE, hand.x, hand.y, hand.z, 6, 0.15, 0.15, 0.15, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 0.8F, 1.2F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0F, 0.8F);
	}

	/** The cost of {@code what} (a spell, or a Sorcerer's key: imbue, bolt, mend, renewal, rekindle). */
	static float cost(String what) {
		return COST.getOrDefault(what, 10.0F);
	}

	static void set(ServerPlayer player, float mana) {
		player.setAttached(MANA, Math.max(0.0F, mana));
		glow(player);
	}

	/** Keeps the synced glow step in line with the mana. */
	private static void glow(ServerPlayer player) {
		float max = max(player);
		int step = max <= 0.0F ? 0 : Math.round(Math.min(OVERLOAD, current(player) / max) * GLOW_STEPS);
		if (player.getAttachedOrElse(GLOW, -1) != step) {
			player.setAttached(GLOW, step);
		}
		// (0.7.235) and their power, for how bright it sits at its least
		int power = power(player);
		if (player.getAttachedOrElse(POWER, -1) != power) {
			player.setAttached(POWER, power);
		}
	}

	// ---- attuning ---------------------------------------------------------------------------------------------------

	/** {@code player} is attuned now (again, if they were): overloaded with mana, their sight blue till it settles. */
	public static void attune(ServerPlayer player) {
		attune(player, null);
	}

	/** {@link #attune(ServerPlayer)}, telling a first-timer {@code how} (a message key) instead of the amethyst's song. */
	static void attune(ServerPlayer player, @org.jspecify.annotations.Nullable String how) {
		boolean first = !attuned(player);
		player.setAttached(ATTUNED, true);
		set(player, max(player) * OVERLOAD);
		ServerLevel level = (ServerLevel)player.level();
		player.setAttached(OVERLOADED, level.getGameTime());
		level.sendParticles(MOTE, player.getX(), player.getY() + 1.0, player.getZ(), 60, 0.5, 0.8, 0.5, 0.05);
		level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.6, 0.4, 0.08);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2F, 0.7F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.5F);
		if (first) {
			player.sendSystemMessage(Component.translatable(natural(player) ? "message.jaymoskingdoms.mana.attuned.natural"
				: how != null ? how : "message.jaymoskingdoms.mana.attuned").withStyle(ChatFormatting.AQUA));
		}
	}

	/** Every tick, for each player: a caster attuned, mana coming back (or bleeding off an overload), the amethyst felt. */
	static void tick(ServerPlayer player) {
		long now = player.level().getGameTime();
		if (!attuned(player)) {
			if (Classes.is(player, PlayerClass.MAGE) || Classes.is(player, PlayerClass.SORCERER)) {
				attune(player);
			} else if ((now + player.getId()) % AMETHYST_EVERY == 0L && nearAmethyst(player)) {
				attune(player);
			} else {
				// (0.7.235) or slowly, carrying alchemy's vis
				ManaAlchemy.unattunedTick(player, now);
			}
			return;
		}
		if (now % 20L != 0L) {
			return;
		}
		ManaSickness.tick(player);
		float max = max(player), mana = current(player);
		// (0.7.235) meditating brings it back faster, sickness slower or not at all; vis feeds it, flux drains it
		float back = regen(player) * MEDITATE_REGEN[meditating(player)] * ManaSickness.regenTimes(player) * com.jaymoskingdoms.spirits.SpiritSickness.regenTimes(player);
		float alchemy = ManaAlchemy.perSecond(player);
		if (mana > max) {
			set(player, Math.max(max, mana - SPILL + Math.min(0.0F, alchemy)));
			if (player.level() instanceof ServerLevel level && player.getRandom().nextInt(3) == 0) {
				sendSeen(level, MOTE, player.getX(), player.getY() + 1.0, player.getZ(), 4, 0.4, 0.6, 0.4, 0.02);
			}
		} else if (mana < max || alchemy < 0.0F) {
			set(player, Mth.clamp(mana + back + alchemy, 0.0F, max));
		} else {
			glow(player);
		}
	}

	/** (0.7.235) {@code player}'s Meditation rank while they're settled into it (Jaymo's Powers' Meditate), else 0. */
	static int meditating(ServerPlayer player) {
		return PowersCompat.meditating(player) ? Math.clamp(Skills.rank(player, "meditation"), 1, MEDITATE_REGEN.length - 1) : 0;
	}

	private static boolean nearAmethyst(ServerPlayer player) {
		BlockPos at = player.blockPosition();
		for (BlockPos pos : BlockPos.betweenClosed(at.offset(-AMETHYST_RANGE, -AMETHYST_RANGE, -AMETHYST_RANGE),
			at.offset(AMETHYST_RANGE, AMETHYST_RANGE, AMETHYST_RANGE))) {
			if (player.level().getBlockState(pos).is(Blocks.BUDDING_AMETHYST)) {
				return true;
			}
		}
		return false;
	}

	// ---- seen by the attuned ----------------------------------------------------------------------------------------

	/** Particles only the attuned near by can see (the quiet signs of magic). */
	public static void sendSeen(ServerLevel level, ParticleOptions particle, double x, double y, double z, int count, double dx, double dy, double dz,
		double speed) {
		for (ServerPlayer viewer : level.players()) {
			if (attuned(viewer) && viewer.distanceToSqr(x, y, z) < SEEN_RANGE * SEEN_RANGE) {
				level.sendParticles(viewer, particle, false, false, x, y, z, count, dx, dy, dz, speed);
			}
		}
	}
}
