package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

/**
 * (0.7.211) Caught at it: the {@link Underworld}'s law. A player seen dealing contraband (or flaunting it before a guard)
 * is charged ({@link #charge}) as well as hunted. Caught while the charge stands (downed with one of the realm's guards
 * by, or yielding: sneaking empty-handed right up to a guard for {@link #YIELD_TICKS}), they're sentenced on the spot,
 * aloud, by the guard: the contraband is taken, the hunt called off, and then by their record there ({@link #OFFENCES}),
 * the ruler's temper ({@code Arrests.TEMPER_LEAN}) and luck:
 * <ul>
 * <li><b>a flogging</b>: a guard lays on {@link #FLOG_BLOWS} strokes (never past {@link #FLOG_SPARES} health), and you go
 * slow and weak a while after;</li>
 * <li><b>the cells</b>: you're thrown in the realm's pit under its seat ({@link Speakeasy#cell}) for {@link #JAIL_TICKS}
 * (longer each time); dying doesn't get you out, digging out does, but that makes you an outlaw there;</li>
 * <li><b>the block</b>: beheaded where you stand.</li>
 * </ul>
 * Yielding is worth something; the Tidings carry word of the cells and of executions. A charge lapses when the hunt does:
 * get away, and it's dropped.
 */
public final class Busts {
	/** How close a guard must be to a downed player to take them, and to a yielding one; how long the yielding takes (ticks). */
	static final double TAKE_RANGE = 6.0, YIELD_RANGE = 3.0;
	static final int YIELD_TICKS = 40;
	/** Checked this often (ticks). */
	static final int CHECK = 10;
	/** Sentencing: each past offence counts this much, plus up to this at random; yielding takes this off. */
	static final int PER_OFFENCE = 15, CHANCE_SPREAD = 30, YIELD_MERCY = 15;
	/** The cells from this score, the block from this. */
	static final int JAIL_FROM = 40, EXECUTE_FROM = 80;
	/** A flogging: strokes, ticks between them, their sting; never below this health. Then slow and weak this long (ticks). */
	static final int FLOG_BLOWS = 5, FLOG_GAP = 16;
	static final float FLOG_DAMAGE = 2.0F, FLOG_SPARES = 4.0F;
	static final int FLOG_AFTER = 20 * 180;
	/** A downed prisoner is given this long to be got up before the strokes start (ticks). */
	static final int FLOG_WAIT = 20 * 10;
	/** The cells: this long the first time, this much more for each offence before (ticks). Out of this reach is out. */
	static final long JAIL_TICKS = 20L * 60 * 5, JAIL_PER_OFFENCE = 20L * 60 * 2;
	static final double JAIL_REACH = 3.5;
	/** How far the sentence is heard. */
	static final double SPEECH = 24.0;
	/** Lines each (lang {@code bust.jaymoskingdoms.<key>.<n>}). */
	static final int LINES = 3;

	/** Per player: realm id → their offences against its law on contraband (convictions). Saved. */
	static final AttachmentType<Map<String, Integer>> OFFENCES = AttachmentRegistry.create(JaymosKingdoms.id("underworld_offences"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.INT)).copyOnDeath());
	/** Per player: realm id → the charge standing against them there (dealing, contraband, escape). Saved. */
	static final AttachmentType<Map<String, String>> CHARGES = AttachmentRegistry.create(JaymosKingdoms.id("underworld_charges"),
		builder -> builder.persistent(Codec.unboundedMap(Codec.STRING, Codec.STRING)).copyOnDeath());

	/** A prisoner: whose cells, where, and until when. */
	record Jail(int realm, BlockPos cell, long until) {
		static final Codec<Jail> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("realm").forGetter(Jail::realm),
			BlockPos.CODEC.fieldOf("cell").forGetter(Jail::cell),
			Codec.LONG.fieldOf("until").forGetter(Jail::until)
		).apply(i, Jail::new));
	}

	/** Per player: their sentence in the cells, while it runs. Saved. */
	static final AttachmentType<Jail> JAILED = AttachmentRegistry.create(JaymosKingdoms.id("jailed"),
		builder -> builder.persistent(Jail.CODEC).copyOnDeath());

	/** A flogging under way. */
	private static final class Flogging {
		final UUID player;
		final PathfinderMob guard;
		int blows;
		long next;
		final long giveUp;

		Flogging(UUID player, PathfinderMob guard, long next) {
			this.player = player;
			this.guard = guard;
			this.next = next;
			this.giveUp = next + FLOG_WAIT;
		}
	}

	private enum Sentence {
		FLOG, JAIL, EXECUTE;

		String key() {
			return this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	private static final Map<UUID, Flogging> FLOGGINGS = new HashMap<>();
	/** Player → ticks they've been yielding. */
	private static final Map<UUID, Integer> YIELDING = new HashMap<>();
	/** Players beheaded just now (their tag comes off once they're dead). */
	private static final Set<UUID> BEHEADED = new java.util.HashSet<>();

	private Busts() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(Busts::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer p && BEHEADED.remove(p.getUUID())) {
				p.removeTag(Arrests.HEADLESS_TAG);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			newPlayer.removeTag(Arrests.HEADLESS_TAG);
			Jail jail = newPlayer.getAttached(JAILED);
			if (jail != null) {
				// dying is no way out
				Vec3 c = Vec3.atBottomCenterOf(jail.cell());
				newPlayer.teleportTo(Factions.level(newPlayer.level().getServer()), c.x, c.y, c.z, Set.of(), newPlayer.getYRot(), 0.0F, false);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			FLOGGINGS.clear();
			YIELDING.clear();
			BEHEADED.clear();
		});
	}

	/** {@code player} stands charged in {@code f} with {@code crime} (if the realm's people saw it: they're hunted now). */
	static void charge(ServerPlayer player, Faction f, String crime) {
		if (!Theft.wanted(player, f.id())) {
			// (no one saw, or the realm let it go)
			return;
		}
		Map<String, String> charges = new HashMap<>(player.getAttachedOrElse(CHARGES, Map.of()));
		charges.put(Integer.toString(f.id()), crime);
		player.setAttached(CHARGES, Map.copyOf(charges));
	}

	public static boolean jailed(ServerPlayer player) {
		return player.hasAttached(JAILED);
	}

	// ---- the tick ------------------------------------------------------------------------------------------------------

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		for (Iterator<Flogging> it = FLOGGINGS.values().iterator(); it.hasNext();) {
			if (!flog(level, it.next(), now)) {
				it.remove();
			}
		}
		if (now % CHECK != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Jail jail = player.getAttached(JAILED);
			if (jail != null) {
				serve(level, player, jail, now);
				continue;
			}
			Map<String, String> charges = player.getAttachedOrElse(CHARGES, Map.of());
			if (charges.isEmpty() || player.level() != level || !player.isAlive()) {
				YIELDING.remove(player.getUUID());
				continue;
			}
			for (Map.Entry<String, String> e : charges.entrySet()) {
				int realm = Integer.parseInt(e.getKey());
				Faction f = Factions.get(server, realm);
				if (f == null || !Theft.wanted(player, realm)) {
					// got away: the charge lapses
					drop(player, e.getKey());
					break;
				}
				if (caught(level, player, f, e.getValue())) {
					break;
				}
			}
		}
	}

	/** Downed by a guard, or yielding to one: true if they were sentenced. */
	private static boolean caught(ServerLevel level, ServerPlayer player, Faction f, String crime) {
		boolean downed = Downed.isDowned(player);
		if (downed && Unmasking.awaits(player)) {
			// (0.7.473) masked: the guards see who it is first
			return false;
		}
		List<Worker> near = Underworld.guards(level, f, player.getBoundingBox().inflate(downed ? TAKE_RANGE : YIELD_RANGE));
		if (near.isEmpty()) {
			YIELDING.remove(player.getUUID());
			return false;
		}
		PathfinderMob guard = near.getFirst().mob();
		if (downed) {
			sentence(level, player, f, crime, guard, false);
			return true;
		}
		if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()) {
			YIELDING.remove(player.getUUID());
			return false;
		}
		int held = YIELDING.merge(player.getUUID(), CHECK, Integer::sum);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bust.yielding").withStyle(ChatFormatting.GRAY));
		if (held < YIELD_TICKS) {
			return false;
		}
		YIELDING.remove(player.getUUID());
		sentence(level, player, f, crime, guard, true);
		return true;
	}

	private static void drop(ServerPlayer player, String realm) {
		Map<String, String> charges = new HashMap<>(player.getAttachedOrElse(CHARGES, Map.of()));
		charges.remove(realm);
		player.setAttached(CHARGES, Map.copyOf(charges));
	}

	// ---- sentencing ----------------------------------------------------------------------------------------------------

	private static void sentence(ServerLevel level, ServerPlayer player, Faction f, String crime, PathfinderMob guard, boolean yielded) {
		RandomSource random = level.getRandom();
		String key = Integer.toString(f.id());
		int offences = player.getAttachedOrElse(OFFENCES, Map.of()).getOrDefault(key, 0);
		Tempers.Temper temper = Tempers.of(f);
		int lean = Arrests.TEMPER_LEAN[temper == null ? Arrests.TEMPER_LEAN.length - 1 : temper.ordinal()];
		int score = offences * PER_OFFENCE + lean + random.nextInt(CHANCE_SPREAD + 1) - (yielded ? YIELD_MERCY : 0)
			+ (crime.equals("escape") ? PER_OFFENCE : 0);
		Sentence s = score >= EXECUTE_FROM ? Sentence.EXECUTE : score >= JAIL_FROM ? Sentence.JAIL : Sentence.FLOG;
		Map<String, Integer> record = new HashMap<>(player.getAttachedOrElse(OFFENCES, Map.of()));
		record.put(key, offences + 1);
		player.setAttached(OFFENCES, Map.copyOf(record));
		drop(player, key);
		Theft.clearWanted(player, f.id());
		// the hunt is called off
		for (Mob m : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(Theft.PURSUIT_RANGE), m -> m.getTarget() == player)) {
			m.setTarget(null);
		}
		int taken = confiscate(player);
		if (Downed.isDowned(player)) {
			// got up to take their punishment
			player.addTag(Downed.REVIVE_TAG);
			player.addTag(Downed.BANDAGED_TAG);
		}
		Underworld.warm(level.getServer(), f, Underworld.HEAT_PER_BUST);
		guard.getLookControl().setLookAt(player, 30.0F, 30.0F);
		Speech.say(level, guard, Component.translatable("bust.jaymoskingdoms.verdict." + s.key() + "." + random.nextInt(LINES)), Speech.RED, SPEECH);
		Component name = Post.nameOf(player);
		Component told = Component.translatable("message.jaymoskingdoms.bust.sentence", f.name(), Component.translatable("bust.jaymoskingdoms.crime." + crime),
			Component.translatable("bust.jaymoskingdoms.sentence." + s.key()));
		player.sendSystemMessage(told.copy().withStyle(ChatFormatting.DARK_RED));
		if (taken > 0) {
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bust.confiscated", taken).withStyle(ChatFormatting.GRAY));
		}
		for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, player.getBoundingBox().inflate(Arrests.ANNOUNCE_RANGE), p -> p != player)) {
			p.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bust.seen", name, f.name(),
				Component.translatable("bust.jaymoskingdoms.sentence." + s.key())).withStyle(ChatFormatting.GOLD));
		}
		long now = level.getGameTime();
		switch (s) {
			case FLOG -> FLOGGINGS.put(player.getUUID(), new Flogging(player.getUUID(), guard, now + FLOG_GAP * 2L));
			case JAIL -> {
				Underworld.Den d = Underworld.den(level.getServer(), f.id());
				BlockPos cell = Speakeasy.cell(level, f, d == null ? null : d.cell().orElse(null));
				if (d != null) {
					Underworld.putCell(level.getServer(), d, cell);
				}
				long until = now + JAIL_TICKS + offences * JAIL_PER_OFFENCE;
				player.setAttached(JAILED, new Jail(f.id(), cell, until));
				// (0.7.419) a road deed: someone locked in a realm's cells
				Deeds.done(player, "cells.held");
				Vec3 c = Vec3.atBottomCenterOf(cell);
				player.teleportTo(level, c.x, c.y, c.z, Set.of(), player.getYRot(), 0.0F, false);
				level.playSound(null, c.x, c.y, c.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 1.0F, 0.7F);
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bust.cells", (until - now) / 1200L).withStyle(ChatFormatting.RED));
				Post.news(level.getServer(), Component.translatable("news.jaymoskingdoms.underworld.jailed", name, f.name()));
			}
			case EXECUTE -> {
				guard.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.NEUTRAL, 1.0F, 0.8F);
				Post.news(level.getServer(), Component.translatable("news.jaymoskingdoms.underworld.executed", name, f.name()));
				// beheaded: nothing to get up from
				player.addTag(Arrests.HEADLESS_TAG);
				BEHEADED.add(player.getUUID());
				player.kill(level);
			}
		}
	}

	/** Every bit of contraband on {@code player} taken; how much. */
	private static int confiscate(ServerPlayer player) {
		Inventory inv = player.getInventory();
		int taken = 0;
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (Underworld.contraband(stack)) {
				taken += stack.getCount();
				inv.setItem(i, ItemStack.EMPTY);
			}
		}
		return taken;
	}

	/** One tick of a flogging; false when it's done. */
	private static boolean flog(ServerLevel level, Flogging fl, long now) {
		ServerPlayer player = level.getServer().getPlayerList().getPlayer(fl.player);
		if (player == null || !player.isAlive() || player.level() != level || !fl.guard.isAlive()) {
			return false;
		}
		if (now < fl.next) {
			return true;
		}
		if (Downed.isDowned(player)) {
			if (now < fl.giveUp) {
				fl.next = now + FLOG_GAP;
				return true;
			}
			// (still down: they'll feel it when they wake)
		} else {
			fl.guard.getLookControl().setLookAt(player, 30.0F, 30.0F);
			fl.guard.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, true);
			if (player.getHealth() > FLOG_SPARES) {
				player.hurtServer(level, level.damageSources().mobAttack(fl.guard), FLOG_DAMAGE);
			}
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0F, 1.0F);
			if (++fl.blows < FLOG_BLOWS) {
				fl.next = now + FLOG_GAP;
				return true;
			}
		}
		player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, FLOG_AFTER, 0));
		player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, FLOG_AFTER, 0));
		Speech.say(level, fl.guard, Component.translatable("bust.jaymoskingdoms.flogged." + level.getRandom().nextInt(LINES)), Speech.RED, SPEECH);
		return false;
	}

	/** A prisoner's time: let out when it's up; out of the cell before then is a break-out. */
	private static void serve(ServerLevel level, ServerPlayer player, Jail jail, long now) {
		Faction f = Factions.get(level.getServer(), jail.realm());
		if (f == null || now >= jail.until()) {
			player.removeAttached(JAILED);
			if (f != null) {
				// (0.7.419) a road deed: a sentence served out
				Deeds.done(player, "cells.served");
			}
			if (f != null && player.isAlive()) {
				Vec3 out = Speakeasy.release(level, f);
				player.teleportTo(level, out.x, out.y, out.z, Set.of(), player.getYRot(), 0.0F, false);
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bust.released", f.name()).withStyle(ChatFormatting.YELLOW));
			}
			return;
		}
		if (!player.isAlive()) {
			return;
		}
		if (player.level() != level || player.position().distanceTo(Vec3.atBottomCenterOf(jail.cell())) > JAIL_REACH) {
			player.removeAttached(JAILED);
			// (0.7.419) a road deed: out of the cells before the sentence was done
			Deeds.done(player, "cells.broke");
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.bust.escaped", f.name()).withStyle(ChatFormatting.DARK_RED));
			Post.news(level.getServer(), Component.translatable("news.jaymoskingdoms.underworld.escaped", Post.nameOf(player), f.name()));
			Theft.arrest(player, level, f, Theft.OUTLAW_TICKS);
			charge(player, f, "escape");
			return;
		}
		if (now % 100 == 0) {
			long left = (jail.until() - now) / 20L;
			player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.bust.time", left / 60, String.format("%02d", left % 60))
				.withStyle(ChatFormatting.GRAY));
		}
	}
}
