package com.jaymoskingdoms;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.Codec;
import java.util.Comparator;
import java.util.Locale;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.186) Andy (2026-10-24): "Every humanoid mob or villager should have a random temperament from a pool of at least
 * 9. A system like the kings but runs underlying each mob's memory. [...] This should dictate things like whether they
 * help you up despite your bad rep or being out in the wilderness (kindhearted, etc)".
 * <p>
 * Every humanoid (villagers, MCA's included, Kingdoms' folk, illagers, witches, piglins, wandering traders) draws one
 * temperament when it first loads, kept on it for life ({@link #TEMPERAMENT}, saved and synced: MCA's interact screen
 * shows it, and the chat AI is told it). It sits under what they remember of you ({@link Recollections}): a friend
 * helps a friend and a grudge refuses one, whatever their nature; past that, their nature decides. A ruler's way of
 * ruling ({@link Tempers}) is a separate thing from the person's own temperament.
 * <ul>
 * <li><b>Kindhearted</b> (compassion): helps anyone up, anywhere, whatever their name or crimes; tends even downed
 * strangers and enemies; won't strike someone who's down.</li>
 * <li><b>Cheerful</b> (joy): forgives a poor name (above {@link #CHEERFUL_FORGIVES}) and helps out in the wild too;
 * tends quickly.</li>
 * <li><b>Devout</b> (faith): mercy is a duty: helps anyone not an enemy of the land (and anyone in the wild); tends
 * downed strangers; won't strike someone who's down.</li>
 * <li><b>Dutiful</b> (duty): helps as their settlement would (good name with it, not wanted there).</li>
 * <li><b>Brave</b> (courage): helps as the settlement would, but even with monsters about; hardly frightened; stands
 * and fights in a brawl, even against someone feared.</li>
 * <li><b>Proud</b> (pride): helps only those of real standing ({@link #PROUD_REP}); won't take a bribe to end a
 * brawl.</li>
 * <li><b>Greedy</b> (avarice): helps anyone not an enemy, anywhere, if they've the coin, and takes
 * {@link #GREEDY_FEE} emeralds for it; takes a bribe even from an enemy.</li>
 * <li><b>Suspicious</b> (distrust): helps only those who've introduced themselves ({@link Introductions}), and then
 * as the settlement would.</li>
 * <li><b>Timid</b> (fear): helps as the settlement would, only with no monster near; frightened from farther and hides
 * longer; runs from a fight rather than joins it.</li>
 * <li><b>Hotheaded</b> (anger): helps as the settlement would, never one they bear any ill will; fights even someone
 * feared, joins every brawl, and quarrels turn to blows sooner.</li>
 * <li><b>Spiteful</b> (resentment): helps only someone they think well of; tends nobody else; jeers at the downed;
 * sours their neighbours.</li>
 * <li><b>Gloomy</b> (sorrow): helps as the settlement would, but from close by only and slowly; stays out of
 * brawls.</li>
 * </ul>
 */
public final class Temperaments {
	public enum Temperament {
		KINDHEARTED(1, ChatFormatting.GREEN, "kindhearted: you can't bear to see anyone hurt and help whoever needs it, friend or foe"),
		CHEERFUL(1, ChatFormatting.GREEN, "cheerful: you look on the bright side and forgive easily"),
		DEVOUT(0, ChatFormatting.AQUA, "devout: you hold mercy a sacred duty, and speak of faith and right"),
		DUTIFUL(0, ChatFormatting.AQUA, "dutiful: you do what's expected of you and keep to the rules of your people"),
		BRAVE(0, ChatFormatting.GOLD, "brave: you don't scare easily and stand your ground"),
		PROUD(-1, ChatFormatting.GOLD, "proud: you think highly of yourself and only respect those of real standing"),
		GREEDY(0, ChatFormatting.GOLD, "greedy: everything has its price, and you always ask what's in it for you"),
		SUSPICIOUS(-1, ChatFormatting.YELLOW, "suspicious: you don't trust strangers, and want to know who you're dealing with"),
		TIMID(0, ChatFormatting.YELLOW, "timid: you're easily frightened and avoid trouble"),
		HOTHEADED(-1, ChatFormatting.RED, "hotheaded: you're quick to anger and quicker to fight"),
		SPITEFUL(-2, ChatFormatting.RED, "spiteful: you hold grudges and enjoy others' misfortune"),
		GLOOMY(-1, ChatFormatting.GRAY, "gloomy: you're melancholy and slow to stir yourself for anyone");

		/** How it warms ({@code >0}) or sours their neighbours' regard ({@link Neighbours}), in steps. */
		final int warmth;
		final ChatFormatting colour;
		/** How the chat AI is told of it. */
		final String prompt;

		Temperament(int warmth, ChatFormatting colour, String prompt) {
			this.warmth = warmth;
			this.colour = colour;
			this.prompt = prompt;
		}

		public String key() {
			return this.name().toLowerCase(Locale.ROOT);
		}

		static @Nullable Temperament byKey(@Nullable String key) {
			if (key != null) {
				for (Temperament t : values()) {
					if (t.key().equals(key)) {
						return t;
					}
				}
			}
			return null;
		}
	}

	/** A cheerful one helps someone whose name with the place is above this. */
	static final int CHEERFUL_FORGIVES = -50;
	/** A proud one helps only someone whose name with the place is above this. */
	static final int PROUD_REP = 100;
	/** What a greedy one asks for getting you up (emeralds); they help for coin only if you have this much. */
	static final int GREEDY_FEE = 3;
	/** A timid one won't come over with a monster this near the downed. */
	static final double TIMID_DANGER = 16.0;
	/** Tending time, in percent of {@link Downed#TEND_TICKS}: cheerful, kindhearted, timid, gloomy. */
	static final int TEND_CHEERFUL = 75, TEND_KIND = 85, TEND_TIMID = 120, TEND_GLOOMY = 150;
	/** How far they'll come to tend someone, in percent of {@link Downed#HELP_RANGE}: kindhearted, gloomy. */
	static final int RANGE_KIND = 150, RANGE_GLOOMY = 60;
	/** How near a monster frightens them, in percent of {@link Frights#FRIGHT}: brave, hotheaded, timid; and timid's hiding. */
	static final int FRIGHT_BRAVE = 35, FRIGHT_HOTHEAD = 70, FRIGHT_TIMID = 150, HIDE_TIMID = 200;
	/** A brawler runs under this share of their health: brave and hotheaded, timid (others {@link Retaliation#FLEE_SHARE}). */
	static final float FLEE_BOLD = 0.15F, FLEE_TIMID = 0.5F;
	/** A quarrel turns to blows this many times as often with a hothead in it ({@link Neighbours#ESCALATE}). */
	static final float HOTHEAD_ESCALATE = 2.0F;
	/** The chance, each look ({@link Downed#HELP_LOOK_TICKS}), that a spiteful one near a downed player jeers; how near. */
	static final float JEER_CHANCE = 0.04F;
	static final double JEER_RANGE = 10.0;
	/** Lines each: coming to help ({@code .help.N}), a spiteful jeer, a greedy one's fee. */
	static final int LINES = 2;

	/** On every humanoid: their temperament's key. Saved, and synced (for MCA's interact screen). */
	public static final AttachmentType<String> TEMPERAMENT = AttachmentRegistry.create(JaymosKingdoms.id("temperament"),
		builder -> builder.persistent(Codec.STRING).syncWith(ByteBufCodecs.STRING_UTF8, AttachmentSyncPredicate.all()));

	private Temperaments() {
	}

	static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity living) {
				of(living);
			}
		});
		// the merciful don't strike someone who's down (nor take them for a target: ThrallTargetMixin)
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (source.getEntity() instanceof Mob attacker && attacker != entity && Downed.isDowned(entity) && spares(attacker, entity)) {
				attacker.setTarget(null);
				return false;
			}
			return true;
		});
	}

	/** Who has a temperament: the humanoids that think for themselves. */
	public static boolean humanoid(Entity e) {
		return e instanceof Villager || e instanceof Folk || e instanceof AbstractIllager || e instanceof Witch || e instanceof AbstractPiglin
			|| e instanceof WanderingTrader;
	}

	/** {@code e}'s temperament (drawn now, on the server, if it hasn't one yet); null for what isn't a humanoid. */
	public static @Nullable Temperament of(Entity e) {
		if (!humanoid(e)) {
			return null;
		}
		Temperament t = Temperament.byKey(e.getAttached(TEMPERAMENT));
		if (t == null && !e.level().isClientSide()) {
			t = Temperament.values()[e.level().getRandom().nextInt(Temperament.values().length)];
			e.setAttached(TEMPERAMENT, t.key());
		}
		return t;
	}

	static boolean is(Entity e, Temperament t) {
		return humanoid(e) && Temperament.byKey(e.getAttached(TEMPERAMENT)) == t;
	}

	/** "Temperament: Kindhearted", in its colour. */
	public static Component line(Temperament t) {
		return Component.translatable("temperament.jaymoskingdoms.line", Component.translatable("temperament.jaymoskingdoms." + t.key())).withStyle(t.colour);
	}

	public static Component hover(Temperament t) {
		return Component.translatable("temperament.jaymoskingdoms." + t.key() + ".hover");
	}

	// ---- helping the downed ---------------------------------------------------------------------------------------------

	/**
	 * Whether {@code helper} would come and get downed {@code player} up: what they remember of them first, then their
	 * nature, the place ({@code f}: the settlement the player lies in, null in the wild) and the player's name there.
	 */
	static boolean helpsPlayer(ServerLevel level, LivingEntity helper, ServerPlayer player, @Nullable Faction f) {
		Temperament t = of(helper);
		if (t == null) {
			return false;
		}
		int lean = Recollections.rememberer(helper) ? Recollections.leaning(helper, player) : 0;
		if (t == Temperament.KINDHEARTED) {
			return true;
		}
		if (lean <= Recollections.REFUSE) {
			// (a grudge: not them)
			return false;
		}
		if (lean >= Recollections.WARM) {
			// (a friend helps a friend, whatever the place thinks)
			return t != Temperament.TIMID || !danger(level, player);
		}
		boolean wild = f == null;
		boolean camp = f != null && f.type() == FactionType.BARBARIAN;
		// (a camp: an outlaw it spares is one of its own; anyone else is prey)
		int rep = wild ? 0 : camp ? (Barbarians.spares(player, f.id()) ? Reputation.HOSTILE + 101 : Reputation.MIN) : Reputation.get(player, f.id());
		boolean wanted = !wild && !camp && Theft.wanted(player, f.id());
		boolean welcome = !wild && rep > Downed.HELP_PLAYER_REP && !wanted;
		return switch (t) {
			case KINDHEARTED -> true;
			case DEVOUT -> wild || rep > Reputation.HOSTILE;
			case CHEERFUL -> wild || rep > CHEERFUL_FORGIVES && !wanted;
			case GREEDY -> welcome || (wild || rep > Reputation.HOSTILE) && emeralds(player) >= GREEDY_FEE;
			case PROUD -> !wild && rep > PROUD_REP && !wanted;
			case SUSPICIOUS -> welcome && Introductions.knows(player, helper);
			case TIMID -> welcome && !danger(level, player);
			case HOTHEADED -> welcome && lean >= 0;
			case SPITEFUL -> false;
			case DUTIFUL, BRAVE, GLOOMY -> welcome;
		};
	}

	/**
	 * Whether {@code helper} would tend downed {@code body} (not a player): {@code kin} if it's one of a settlement's own
	 * (whom villagers have always tended). The kindhearted and devout tend any humanoid; the spiteful nobody.
	 */
	static boolean tendsMob(ServerLevel level, LivingEntity helper, LivingEntity body, boolean kin) {
		Temperament t = of(helper);
		if (t == null) {
			return kin;
		}
		return switch (t) {
			case KINDHEARTED, DEVOUT -> kin || humanoid(body) && !(body instanceof Enemy && !(body instanceof Folk));
			case SPITEFUL -> false;
			case TIMID -> kin && !danger(level, body);
			default -> kin;
		};
	}

	/** A monster near {@code body} (the timid won't come). */
	private static boolean danger(ServerLevel level, LivingEntity body) {
		return !level.getEntitiesOfClass(Mob.class, body.getBoundingBox().inflate(TIMID_DANGER),
			m -> m.isAlive() && m instanceof Enemy && !Downed.isDowned(m) && !(m instanceof Folk folk && !Barbarians.isRaider(folk))).isEmpty();
	}

	/** They'd come to the downed though they're frightened (the brave). */
	static boolean fearless(LivingEntity helper) {
		return is(helper, Temperament.BRAVE);
	}

	/** How long they take getting someone up (ticks). */
	static int tendTicks(LivingEntity helper) {
		Temperament t = of(helper);
		int percent = t == Temperament.CHEERFUL ? TEND_CHEERFUL : t == Temperament.KINDHEARTED ? TEND_KIND : t == Temperament.TIMID ? TEND_TIMID
			: t == Temperament.GLOOMY ? TEND_GLOOMY : 100;
		return Downed.TEND_TICKS * percent / 100;
	}

	/** How far they'll come to tend someone. */
	static double helpRange(LivingEntity helper) {
		Temperament t = of(helper);
		return Downed.HELP_RANGE * (t == Temperament.KINDHEARTED ? RANGE_KIND : t == Temperament.GLOOMY ? RANGE_GLOOMY : 100) / 100.0;
	}

	/** {@code helper} comes over to downed {@code player}: a word in their own manner. */
	static void comes(ServerLevel level, LivingEntity helper, ServerPlayer player) {
		Temperament t = of(helper);
		if (t != null) {
			Speech.sayTo(level, helper, player, Component.translatable("temperament.jaymoskingdoms." + t.key() + ".help." + level.getRandom().nextInt(LINES)),
				Speech.WHITE);
		}
	}

	/** {@code helper} got {@code player} up: a greedy one takes their fee. */
	static void gotUp(ServerLevel level, LivingEntity helper, ServerPlayer player) {
		if (!is(helper, Temperament.GREEDY)) {
			return;
		}
		int took = 0;
		for (int i = 0; i < player.getInventory().getContainerSize() && took < GREEDY_FEE; i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(Items.EMERALD)) {
				int n = Math.min(stack.getCount(), GREEDY_FEE - took);
				stack.shrink(n);
				took += n;
			}
		}
		if (took > 0) {
			Speech.sayTo(level, helper, player, Component.translatable("temperament.jaymoskingdoms.greedy.fee." + level.getRandom().nextInt(LINES), took),
				Speech.GOLD);
		}
	}

	/** Now and then a spiteful one near a downed {@code player} jeers at them. */
	static void jeer(ServerLevel level, ServerPlayer player) {
		if (level.getRandom().nextFloat() >= JEER_CHANCE) {
			return;
		}
		level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(JEER_RANGE),
				e -> e.isAlive() && !e.isBaby() && !Downed.isDowned(e) && is(e, Temperament.SPITEFUL) && e.hasLineOfSight(player)).stream()
			.min(Comparator.comparingDouble(player::distanceToSqr))
			.ifPresent(e -> Speech.sayTo(level, e, player, Component.translatable("temperament.jaymoskingdoms.spiteful.jeer." + level.getRandom().nextInt(LINES)),
				Speech.RED));
	}

	/** {@code attacker} lets downed {@code body} be: the kindhearted and the devout don't strike someone who's down. */
	public static boolean spares(Mob attacker, LivingEntity body) {
		return is(attacker, Temperament.KINDHEARTED) || is(attacker, Temperament.DEVOUT);
	}

	// ---- fear and fights ------------------------------------------------------------------------------------------------

	/** How near a monster frightens {@code v} ({@link Frights}). */
	static double frightRange(LivingEntity v, double usual) {
		Temperament t = of(v);
		return usual * (t == Temperament.BRAVE ? FRIGHT_BRAVE : t == Temperament.HOTHEADED ? FRIGHT_HOTHEAD : t == Temperament.TIMID ? FRIGHT_TIMID : 100) / 100.0;
	}

	/** How long {@code v} hides once frightened (ticks). */
	static long hideTicks(LivingEntity v, long usual) {
		return is(v, Temperament.TIMID) ? usual * HIDE_TIMID / 100 : usual;
	}

	/** They square up even to someone their people fear ({@link Retaliation#FEARED}): the brave and the hotheaded. */
	static boolean bold(LivingEntity e) {
		return is(e, Temperament.BRAVE) || is(e, Temperament.HOTHEADED);
	}

	/** They'd rather run than fight (struck) or stay out of it (an onlooker): the timid; and the gloomy keep out of others' fights. */
	static boolean runs(LivingEntity e) {
		return is(e, Temperament.TIMID);
	}

	static boolean keepsOut(LivingEntity e) {
		return is(e, Temperament.TIMID) || is(e, Temperament.GLOOMY);
	}

	/** The share of their health under which a brawler runs. */
	static float fleeShare(LivingEntity e, float usual) {
		return bold(e) ? FLEE_BOLD : runs(e) ? FLEE_TIMID : usual;
	}

	/** Paid off: the greedy take coin from anyone; the proud from no one (null: as anyone would). */
	static @Nullable Boolean takesBribe(LivingEntity e) {
		return is(e, Temperament.GREEDY) ? Boolean.TRUE : is(e, Temperament.PROUD) ? Boolean.FALSE : null;
	}

	/** Their nature's warmth, for where two neighbours start ({@link Neighbours}). */
	static int warmth(LivingEntity e) {
		Temperament t = of(e);
		return t == null ? 0 : t.warmth;
	}

	/** How much likelier a quarrel with {@code e} in it turns to blows. */
	static float escalation(LivingEntity e) {
		return is(e, Temperament.HOTHEADED) ? HOTHEAD_ESCALATE : 1.0F;
	}

	/** For the chat AI's prompt. */
	static String prompt(LivingEntity e) {
		Temperament t = of(e);
		return t == null ? "" : "Your temperament: " + t.prompt + ".";
	}

	private static int emeralds(ServerPlayer player) {
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(Items.EMERALD)) {
				n += stack.getCount();
			}
		}
		return n;
	}

	// ---- the test command ------------------------------------------------------------------------------------------------

	/** {@code /kingdom temperament [<temperament>]} (ops): the nearest humanoid's temperament, or set it. */
	static LiteralArgumentBuilder<CommandSourceStack> command() {
		return Commands.literal("temperament").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.executes(c -> show(c, null))
			.then(Commands.argument("temperament", StringArgumentType.word())
				.suggests((c, b) -> SharedSuggestionProvider.suggest(java.util.Arrays.stream(Temperament.values()).map(Temperament::key), b))
				.executes(c -> show(c, StringArgumentType.getString(c, "temperament"))));
	}

	private static int show(CommandContext<CommandSourceStack> c, @Nullable String set) throws CommandSyntaxException {
		ServerPlayer player = c.getSource().getPlayerOrException();
		LivingEntity who = player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(8.0), Temperaments::humanoid).stream()
			.min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
		if (who == null) {
			c.getSource().sendFailure(Component.translatable("temperament.jaymoskingdoms.command.nobody"));
			return 0;
		}
		if (set != null) {
			Temperament t = Temperament.byKey(set.toLowerCase(Locale.ROOT));
			if (t == null) {
				c.getSource().sendFailure(Component.translatable("temperament.jaymoskingdoms.command.unknown", set));
				return 0;
			}
			who.setAttached(TEMPERAMENT, t.key());
		}
		Temperament t = of(who);
		if (t == null) {
			return 0;
		}
		c.getSource().sendSuccess(() -> Component.translatable("temperament.jaymoskingdoms.command.show", who.getDisplayName(), line(t)), false);
		return t.ordinal() + 1;
	}

	static @Nullable Faction placeOf(MinecraftServer server, ServerLevel level, ServerPlayer player) {
		Faction f = Factions.around(server, level, player.blockPosition(), Villages.VILLAGE_RADIUS);
		if (f == null || f.fallen()) {
			f = Factions.at(server, level, player.blockPosition());
		}
		return f == null || f.fallen() ? null : f;
	}
}
