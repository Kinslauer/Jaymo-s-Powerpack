package com.jaymospowers.power.zfighter;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Powers;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.50, Andy's NOEA picks) Branch forms: the steps past the ladder every Z Fighter climbs (Super, Super 2, Super 3, and
 * the Warrior-born's Primal Super). A branch form is <b>not</b> a new {@code live.form}: he stays in his top regular form (so
 * the hair, the aura's strength, the Spirit Bomb and every array indexed by form keep working) and this layer, kept in
 * {@link #BRANCH} (the form id, 10 and up; synced to everyone), stands on top of it: its own fists, speed, health, ki
 * power and drain, its own aura colour and name. The other round-2 forms (False Super, the god forms, the Legendary line) own
 * ids 5-9.
 * <ul>
 * <li>{@link #GLOOM_GIANT} and {@link #ASCENDANT}: a Verdant's, past Titan ({@link ZFighterVerdantForms});</li>
 * <li>{@link #SOVEREIGN}: a Frost-born's, past Golden ({@link ZFighterFrostForms});</li>
 * <li>{@link #OVERDRIVE} and {@link #REACTOR}: an android's, whatever his lineage ({@link ZFighterAndroid}).</li>
 * </ul>
 * C at the top of the ladder climbs one more step: the first time, if its price is met (mastery, hardship, TP, and for
 * the Gloom Giant a night), it opens for good and is taken; sneak+C steps back down. Branch forms have no mastery of
 * their own: opening the next one asks for time spent in the one below (kept in {@link #HELD}). Where the next step can't
 * be taken yet C does what it always did (the Spirit Bomb, if learned), so nothing is ever hidden behind it.
 * <p>Also here: what the branch forms, Ego, the android and the metal rebuild add to his attributes, ki power and ki
 * drain (the few hooks {@link ZFighterPower} calls), and {@code /powerdebug zfighter forms}.
 */
public final class ZFighterBranchForms {
	public static final int GLOOM_GIANT = 10;
	public static final int ASCENDANT = 11;
	public static final int SOVEREIGN = 12;
	public static final int OVERDRIVE = 13;
	public static final int REACTOR = 14;
	public static final int FIRST = GLOOM_GIANT;
	public static final int LAST = REACTOR;

	// ---- what each form adds on top of the form it stands on (index: form id - FIRST) ----
	/** Fists (added to the multiplier), speed (added), extra max health, and ki power / ki drain (times). */
	static final double[] FIST = {0.18, 0.35, 0.22, 0.15, 0.32};
	static final double[] SPEED = {-0.03, 0.06, 0.05, 0.04, 0.08};
	static final double[] HEALTH = {10.0, 16.0, 8.0, 6.0, 12.0};
	static final float[] KI = {1.10F, 1.22F, 1.15F, 1.10F, 1.25F};
	static final float[] DRAIN = {1.2F, 1.5F, 1.3F, 0.9F, 1.2F};
	/** Ki to go into the form (free in creative). */
	static final int[] ENTER_KI = {90, 100, 100, 80, 100};
	/** Aura colours: jade, pale lime, platinum ice, cyan, a reactor's orange. */
	static final int[] COLORS = {0x2A9D6E, 0xD8FF9A, 0xBFE8FF, 0x40E0F0, 0xFF6A3C};
	/** How many forms up the aura and glow read it (a Super 3 is 3, the Primal Super 4). */
	static final int[] TIER = {4, 5, 4, 4, 5};
	/** The Verdant forms grow him past Titan's scale (added; Titan is {@code GIANT_SCALE[3]}). */
	static final double[] SCALE = {0.5, 0.2, 0.0, 0.0, 0.0};

	/** What opening a form asks: mastery of the regular top form (hundredths of a percent), hardship, TP, seconds in the form below. */
	record Price(int mastery, int hardship, int tp, int heldBelow, boolean night) {
	}

	static final Price[] PRICES = {
		new Price(ZFighterTraining.FREE_ENTRY_MASTERY, 200, 25000, 0, true),
		new Price(ZFighterTraining.FREE_ENTRY_MASTERY, 260, 40000, 300, false),
		new Price(ZFighterTraining.FREE_ENTRY_MASTERY, 220, 30000, 0, false),
		new Price(0, 0, 0, 0, false),
		new Price(0, 0, 20000, 600, false)};

	/** The branch form he's in right now (10 and up); absent when he isn't in one. Everyone sees it; not saved. */
	public static final AttachmentType<Integer> BRANCH = AttachmentRegistry.create(JaymosPowers.id("zfighter_branch"),
		builder -> builder.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.all()));
	/** The branch forms he has opened (bit {@code form - FIRST}). Saved, kept through death. */
	public static final AttachmentType<Integer> OPEN = AttachmentRegistry.create(JaymosPowers.id("zfighter_branch_open"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());
	/** Seconds spent in each branch form (index: form id - FIRST). Saved, kept through death. */
	public static final AttachmentType<List<Integer>> HELD = AttachmentRegistry.create(JaymosPowers.id("zfighter_branch_held"),
		builder -> builder.persistent(Codec.INT.listOf()).copyOnDeath());

	private static final Identifier FIST_ID = JaymosPowers.id("zfighter_branch_fists");
	private static final Identifier SPEED_ID = JaymosPowers.id("zfighter_branch_speed");
	private static final Identifier HEALTH_ID = JaymosPowers.id("zfighter_branch_health");

	private ZFighterBranchForms() {
	}

	static void init() {
		ZFighterEgo.init();
		ZFighterAndroid.init();
		ZFighterFrostForms.init();
	}

	// ---------------------------------------------------------------- reading

	public static boolean branch(int form) {
		return form >= FIRST && form <= LAST;
	}

	/** The branch form he's in (10 and up), or 0. Both sides. */
	public static int of(Player player) {
		Integer form = player.getAttached(BRANCH);
		return form == null ? 0 : form;
	}

	/** The lang key of a branch form's name. */
	public static String key(int form) {
		return "form.jaymospowers.zfighter.branch." + form;
	}

	/**
	 * The aura colour (RGB) that overrides his form's: a branch form's, or Ego's dark purple; 0 when neither is on. Both
	 * sides (the glow, the aura, the ki weapon, the lock-on marker).
	 */
	public static int color(Player player) {
		int branch = of(player);
		if (branch != 0) {
			return COLORS[branch - FIRST];
		}
		return ZFighterEgo.on(player) ? ZFighterEgo.COLOR : 0;
	}

	/** How many forms up the aura and glow read him: Ego counts as a Super 2 from base form, a branch form as its {@link #TIER}. */
	public static int tier(Player player, int form) {
		int branch = of(player);
		if (branch != 0) {
			return TIER[branch - FIRST];
		}
		return form == 0 && ZFighterEgo.on(player) ? 2 : form;
	}

	/** The name the HUD shows for the form he's in. */
	public static Component label(Player player, int form) {
		int branch = of(player);
		return Component.translatable(branch != 0 ? key(branch) : ZFighterLineage.formKey(player, form));
	}

	static boolean open(Player player, int form) {
		return (player.getAttachedOrElse(OPEN, 0) >> (form - FIRST) & 1) != 0;
	}

	static int held(Player player, int form) {
		List<Integer> held = player.getAttachedOrElse(HELD, List.of());
		return form - FIRST < held.size() ? held.get(form - FIRST) : 0;
	}

	private static void setHeld(Player player, int form, int seconds) {
		List<Integer> next = new ArrayList<>(player.getAttachedOrElse(HELD, List.of()));
		while (next.size() <= form - FIRST) {
			next.add(0);
		}
		next.set(form - FIRST, seconds);
		player.setAttached(HELD, List.copyOf(next));
	}

	/** Grants a branch form outright (the debug command, a wish later). */
	static void grant(ServerPlayer player, int form) {
		player.setAttached(OPEN, player.getAttachedOrElse(OPEN, 0) | 1 << (form - FIRST));
	}

	// ---------------------------------------------------------------- the ladder

	/** The branch forms his lineage and body offer, lowest first. */
	static List<Integer> ladder(Player player) {
		List<Integer> ladder = new ArrayList<>();
		int lineage = ZFighterLineage.of(player);
		if (lineage == ZFighterLineage.VERDANT) {
			ladder.add(GLOOM_GIANT);
			ladder.add(ASCENDANT);
		}
		if (lineage == ZFighterLineage.FROST) {
			ladder.add(SOVEREIGN);
		}
		if (ZFighterAndroid.android(player)) {
			ladder.add(OVERDRIVE);
			ladder.add(REACTOR);
		}
		return ladder;
	}

	/** Whether the branch form he's in still stands: at the top of the regular ladder, on a lineage that has it, not the ape. */
	private static boolean valid(Player player, ZFighterPower.Live live, int branch) {
		int top = ApeRage.super4(player) ? ApeRage.SUPER_4 : ZFighterTraining.FORMS;
		return live.form == top && !ZFighterLineage.ape(player) && ladder(player).contains(branch);
	}

	/** The branch form C would take him to now (0: none: he isn't at the top of the ladder, or it ends there). */
	private static int next(ServerPlayer player, ZFighterPower.Live live) {
		int top = ApeRage.super4(player) ? ApeRage.SUPER_4 : ZFighterTraining.FORMS;
		if (live.form != top || ZFighterTraining.formsOpen(player) < ZFighterTraining.FORMS || ZFighterLineage.ape(player)) {
			return 0;
		}
		int current = of(player);
		for (int form : ladder(player)) {
			if (form > current) {
				return form;
			}
		}
		return 0;
	}

	/** Whether C would climb to a branch form now (open already, or its price is met): then the Spirit Bomb waits for the top. */
	static boolean canAscend(ServerPlayer player, ZFighterPower.Live live) {
		int next = next(player, live);
		return next != 0 && (open(player, next) || blocker(player, next) == null);
	}

	/** What stands between him and opening {@code form}, or null when it's his to take. */
	static @Nullable Component blocker(ServerPlayer player, int form) {
		Price price = PRICES[form - FIRST];
		if (player.isCreative()) {
			return null;
		}
		if (ZFighterTraining.of(player).mastery(ZFighterTraining.FORMS) < price.mastery()) {
			return Component.translatable("message.jaymospowers.zfighter_branch_need_mastery", price.mastery() / 100,
				Component.translatable(ZFighterLineage.formKey(player, ZFighterTraining.FORMS)));
		}
		if (ZFighterPower.hardship(player) < price.hardship()) {
			return Component.translatable("message.jaymospowers.zfighter_branch_need_hardship", price.hardship());
		}
		if (price.heldBelow() > 0 && held(player, form - 1) < price.heldBelow()) {
			return Component.translatable("message.jaymospowers.zfighter_branch_need_held", price.heldBelow() / 60, Component.translatable(key(form - 1)));
		}
		if (ZFighterTraining.of(player).tp() < price.tp()) {
			return Component.translatable("message.jaymospowers.zfighter_branch_need_tp", price.tp());
		}
		if (price.night() && !player.level().isDarkOutside()) {
			return Component.translatable("message.jaymospowers.zfighter_branch_need_night");
		}
		return null;
	}

	/**
	 * C past the top of the ladder: up one branch form (opening it first when its price is met). False if there is none to
	 * take; the caller then says "no higher" (and {@link #hint}s at what's missing).
	 */
	static boolean ascend(ServerPlayer player, ZFighterPower.Live live) {
		int next = next(player, live);
		if (next == 0) {
			return false;
		}
		if (!open(player, next)) {
			Component why = blocker(player, next);
			if (why != null) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_branch_wait", Component.translatable(key(next)), why)
					.withStyle(ChatFormatting.GRAY));
				return true;
			}
		}
		boolean free = player.isCreative();
		if (!free && live.ki < ENTER_KI[next - FIRST]) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.zfighter_no_ki").withStyle(ChatFormatting.GRAY));
			return true;
		}
		if (!open(player, next)) {
			if (!free) {
				ZFighterTraining.Data data = ZFighterTraining.of(player);
				player.setAttached(ZFighterTraining.DATA, data.withTp(data.tp() - PRICES[next - FIRST].tp()));
			}
			grant(player, next);
			player.sendSystemMessage(Component.translatable("message.jaymospowers.zfighter_branch_opened", Component.translatable(key(next)))
				.withStyle(ChatFormatting.GOLD));
		}
		if (!free) {
			live.ki -= ENTER_KI[next - FIRST];
		}
		enter(player, live, next);
		return true;
	}

	/** Into {@code form}: the transformation's blast of the form he stands on, then the branch's own flare. */
	static void enter(ServerPlayer player, ZFighterPower.Live live, int form) {
		player.setAttached(BRANCH, form);
		ZFighterPower.transform(player, live, live.form);
		ServerLevel level = player.level();
		level.sendParticles(new DustParticleOptions(COLORS[form - FIRST], 2.2F), true, false, player.getX(), player.getY() + player.getBbHeight() * 0.5,
			player.getZ(), 70, 1.0, player.getBbHeight() * 0.5, 1.0, 0.25);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 0.6F);
	}

	/** Sneak+C inside a branch form: one step down (to the one below it he's opened, else the regular top). False if he's in none. */
	static boolean descend(ServerPlayer player, ZFighterPower.Live live) {
		int current = of(player);
		if (current == 0) {
			return false;
		}
		int below = 0;
		for (int form : ladder(player)) {
			if (form < current && open(player, form)) {
				below = form;
			}
		}
		setBranch(player, below);
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 0.6F, 1.5F);
		ZFighterPower.refresh(player, live);
		return true;
	}

	private static void setBranch(ServerPlayer player, int form) {
		if (form == 0) {
			player.removeAttached(BRANCH);
		} else {
			player.setAttached(BRANCH, form);
		}
	}

	// ---------------------------------------------------------------- every tick

	/** Every tick of the power: the branch form holds or falls away, time in it counts, and the other forms' own ticks run. */
	static void tick(ServerPlayer player, ZFighterPower.Live live) {
		int branch = of(player);
		if (branch != 0 && !valid(player, live, branch)) {
			setBranch(player, 0);
			ZFighterPower.refresh(player, live);
			branch = 0;
		}
		if (branch != 0 && player.level().getGameTime() % 20 == 0) {
			setHeld(player, branch, held(player, branch) + 1);
		}
		ZFighterEgo.tick(player, live);
		ZFighterAndroid.tick(player, live);
		ZFighterVerdantForms.tick(player, live, branch);
		ZFighterFrostForms.tick(player, live, branch);
	}

	/** The power is gone (or reset): no branch form, no Ego, no android, no metal; nothing kept. */
	static void clear(ServerPlayer player) {
		player.removeAttached(BRANCH);
		player.removeAttached(OPEN);
		player.removeAttached(HELD);
		ZFighterEgo.clear(player);
		ZFighterAndroid.clear(player);
		ZFighterFrostForms.clear(player);
		ZFighterPower.set(player, Attributes.ATTACK_DAMAGE, FIST_ID, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ZFighterPower.set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ZFighterPower.set(player, Attributes.MAX_HEALTH, HEALTH_ID, 0.0, AttributeModifier.Operation.ADD_VALUE);
		ZFighterVerdantForms.attributes(player, 0);
	}

	// ---------------------------------------------------------------- what they add (called from ZFighterPower)

	/** Refreshed with the rest of his attributes: the branch form's, then Ego's, the android's and the metal body's. */
	static void attributes(ServerPlayer player, ZFighterPower.Live live) {
		int branch = of(player);
		boolean on = branch != 0 && valid(player, live, branch);
		int index = on ? branch - FIRST : 0;
		ZFighterPower.set(player, Attributes.ATTACK_DAMAGE, FIST_ID, on ? FIST[index] : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ZFighterPower.set(player, Attributes.MOVEMENT_SPEED, SPEED_ID, on ? SPEED[index] : 0.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		ZFighterPower.set(player, Attributes.MAX_HEALTH, HEALTH_ID, on ? HEALTH[index] : 0.0, AttributeModifier.Operation.ADD_VALUE);
		ZFighterVerdantForms.attributes(player, on ? branch : 0);
		ZFighterEgo.attributes(player, live);
		ZFighterAndroid.attributes(player, live);
		ZFighterFrostForms.attributes(player, live);
	}

	/** Times his ki techniques: the branch form's and Ego's. */
	static float kiFactor(ServerPlayer player, ZFighterPower.Live live) {
		int branch = of(player);
		float factor = branch != 0 && valid(player, live, branch) ? KI[branch - FIRST] : 1.0F;
		return factor * ZFighterEgo.kiFactor(player);
	}

	/** Times the form's ki drain: the branch form's cost, and none at all for an android. */
	static float drainFactor(ServerPlayer player, ZFighterPower.Live live) {
		int branch = of(player);
		float factor = branch != 0 && valid(player, live, branch) ? DRAIN[branch - FIRST] : 1.0F;
		return factor * ZFighterAndroid.costFactor(player);
	}

	// ---------------------------------------------------------------- /powerdebug zfighter forms

	/** {@code forms open <10-14>}: opens a branch form; {@code forms android <stage>}; {@code forms metal}; {@code forms ego}. */
	static LiteralArgumentBuilder<CommandSourceStack> debug() {
		return Commands.literal("forms")
			.then(Commands.literal("open")
				.then(Commands.argument("form", IntegerArgumentType.integer(FIRST, LAST)).executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					int form = IntegerArgumentType.getInteger(c, "form");
					grant(player, form);
					c.getSource().sendSuccess(() -> Component.literal("Opened branch form " + form + " (C at the top of your ladder takes it)."), false);
					return 1;
				})))
			.then(Commands.literal("android")
				.then(Commands.argument("stage", IntegerArgumentType.integer(0, ZFighterAndroid.DONE)).executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					int stage = IntegerArgumentType.getInteger(c, "stage");
					ZFighterAndroid.setStage(player, stage);
					c.getSource().sendSuccess(() -> Component.literal("Android quest stage " + stage), false);
					return 1;
				})))
			.then(Commands.literal("scientist").executes(c -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				c.getSource().sendSuccess(() -> Component.literal(ZFighterAndroid.summon(player) ? "A scientist set up shop nearby." : "No room for a scientist here."),
					false);
				return 1;
			}))
			.then(Commands.literal("metal").executes(c -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				ZFighterFrostForms.rebuild(player);
				c.getSource().sendSuccess(() -> Component.literal("Rebuilt in metal (any lineage, for testing)."), false);
				return 1;
			}))
			.then(Commands.literal("ego").executes(c -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				boolean on = ZFighterEgo.toggle(player, ZFighterPower.live(player));
				c.getSource().sendSuccess(() -> Component.literal(on ? "Ego toggled." : "Ego isn't open to you (corrupted in Corrupted mode, or a dark lesson's worth of corruption)."), false);
				return 1;
			}));
	}

	/** Whether the Z Fighter power is the player's (a guard for the events below). */
	static boolean fighter(Player player) {
		return Powers.has(player, ZFighterPower.INSTANCE);
	}
}
