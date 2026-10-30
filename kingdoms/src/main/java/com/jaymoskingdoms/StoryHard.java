package com.jaymoskingdoms;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.world.level.block.Blocks;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.412) The hard roads of the life story ({@link LifeStory.Start#CHAINED}, {@link LifeStory.Start#SENTENCED},
 * {@link LifeStory.Start#EVICTED}, {@link LifeStory.Start#CAST_OUT}): what each asks of the player once they've woken.
 *
 * <ul>
 * <li><b>Still in chains</b>: shackled where they woke, among a barbarian camp's captives. The chain lets them shuffle
 * {@link #CHAIN_REACH} blocks and no further, and nothing in the camp harms them while they're bound (the raiders'
 * {@link Barbarians#prey} lets them be). A lockpick used in hand runs the Pin game on the shackle
 * ({@link Lockpicking#pickVirtual}); or, at night, sneaking perfectly still for {@link #SLIP_TICKS} slips it. Free, the camp
 * hunts them for a day, as an escaped captive's does ({@link LifeStory#startHunt}).</li>
 * <li><b>Sentenced</b>: woken in a realm's cells ({@link Busts#JAILED}, {@link Speakeasy#cell}) with
 * {@link #SENTENCE_DAYS} days to serve. The cell has an iron door in its east wall: the sentence runs out and it opens
 * ({@link Busts} lets them out); a pick used on it breaks out (the realm then hunts them as an escaped prisoner); or
 * emeralds in hand pay the jailer's fine, {@link #FINE_PER_DAY} for every day left, and they walk out clean.</li>
 * <li><b>Evicted</b>: between dusk and dawn, inside the realm's land and under a guard's eye, a player who sleeps or stands
 * about is warned ({@link #WARN_AFTER}), then taken in for the night ({@link #EVICT_JAIL_TICKS}, the same cells). It stops
 * the moment they hold a {@link Careers} job or have a bed of their own (a spawn bed they slept in) within the realm's land.
 * There is no inn system, so a bed is what counts.</li>
 * <li><b>Cast out by your own</b>: for {@link #CAST_OUT_TICKS} the non-human villagers and the havens' people mind the
 * player at {@link #CAST_OUT_MINDS} ({@link Prejudice#minds}), and a haven's good name is won slowly and lost fast
 * ({@link Prejudice#reputation}), whatever their blood.</li>
 * </ul>
 */
public final class StoryHard {
	// ---- tuning --------------------------------------------------------------------------------------------------------

	/** Still in chains: how far the chain lets them go (blocks), how hard the shackle's lock is, and the picks they wake with. */
	static final double CHAIN_REACH = 2.5;
	static final int SHACKLE_LEVEL = 2, CHAIN_PICKS = 2;
	/** Slipping the shackle: how long perfectly still and sneaking at night it takes (ticks), checked this often. */
	static final int SLIP_TICKS = 100, SLIP_CHECK = 5;
	/** Sentenced: the days to serve, the fine for each day left (emeralds), the cell door's lock, and the picks they have. */
	static final int SENTENCE_DAYS = 2, FINE_PER_DAY = 3, CELL_LEVEL = 3, CELL_PICKS = 1;
	static final long DAY_TICKS = 24000L;
	/** Evicted: how often it is looked at (ticks), how far a realm's streets reach from its seat (blocks), how far a guard sees (blocks). */
	static final int EVICT_CHECK = 20;
	static final double EVICT_RANGE = 96.0, WATCH_SIGHT = 32.0;
	/**
	 * Evicted: how long stood about at night before the warning and before the cells (ticks), and a night in the cells
	 * (ticks). Asleep in the open counts at once; standing still (working a furnace, fishing) only after these minutes.
	 */
	static final int WARN_AFTER = 1200, JAIL_AFTER = 2400, SLEEPER_HASTE = 8;
	static final long EVICT_JAIL_TICKS = 10000L;
	/** Cast out: how long their own kind turn them away (ticks: three days), and how much they mind (as a Tideborn is minded). */
	static final long CAST_OUT_TICKS = 3L * DAY_TICKS;
	static final float CAST_OUT_MINDS = 4.0F;

	// ---- state ---------------------------------------------------------------------------------------------------------

	/** Where a shackled player is held, and the camp that holds them. */
	record Shackle(BlockPos at, int camp) {
		static final Codec<Shackle> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("at").forGetter(Shackle::at),
			Codec.INT.fieldOf("camp").forGetter(Shackle::camp)
		).apply(i, Shackle::new));
	}

	/** On a player who woke in chains: held there till the shackle is picked or slipped. Saved, kept through death. */
	static final AttachmentType<Shackle> SHACKLE = AttachmentRegistry.create(JaymosKingdoms.id("story_shackle"),
		builder -> builder.persistent(Shackle.CODEC).copyOnDeath());
	/** On an evicted player: the realm (id) that put them out, till they hold work or a bed there. Saved, kept through death. */
	static final AttachmentType<Integer> EVICTED = AttachmentRegistry.create(JaymosKingdoms.id("story_evicted"),
		builder -> builder.persistent(Codec.INT).copyOnDeath());
	/** On a player cast out by their own: the game time it ends. Saved, kept through death. */
	static final AttachmentType<Long> CAST_OUT = AttachmentRegistry.create(JaymosKingdoms.id("story_cast_out"),
		builder -> builder.persistent(Codec.LONG).copyOnDeath());

	/** Player → ticks spent perfectly still and sneaking at night; where they stood at the last look. */
	private static final Map<UUID, Integer> SLIPPING = new HashMap<>();
	private static final Map<UUID, Vec3> STOOD = new HashMap<>();
	/** Player → ticks about at night (evicted); the last look's position; whether the watch has warned them. */
	private static final Map<UUID, Integer> LOITERING = new HashMap<>();
	private static final Map<UUID, Vec3> LOITER_AT = new HashMap<>();
	private static final Set<UUID> WARNED = new java.util.HashSet<>();

	private StoryHard() {
	}

	static void register() {
		ServerTickEvents.END_SERVER_TICK.register(StoryHard::tick);
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (!(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel) || hand != InteractionHand.MAIN_HAND
				|| !sp.getMainHandItem().is(Lockpicking.LOCKPICK)) {
				return InteractionResult.PASS;
			}
			Shackle shackle = sp.getAttached(SHACKLE);
			if (shackle == null) {
				return InteractionResult.PASS;
			}
			if (!Lockpicking.pickVirtual(sp, shackle.at(), SHACKLE_LEVEL, shackle.at().asLong() ^ 0x5DEECE66DL, () -> {
				ServerPlayer again = sp.level().getServer().getPlayerList().getPlayer(sp.getUUID());
				if (again != null) {
					free(again, "picked");
				}
			})) {
				sp.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hard.chains.no_pick").withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.SUCCESS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || hand != InteractionHand.MAIN_HAND) {
				return InteractionResult.PASS;
			}
			Busts.Jail jail = sp.getAttached(Busts.JAILED);
			if (jail == null) {
				return InteractionResult.PASS;
			}
			BlockPos door = Speakeasy.doorAt(jail.cell());
			// only a cell with the story's iron door: the plain Busts cells (no door) have no fine or pick
			if (!hit.getBlockPos().equals(door) && !hit.getBlockPos().equals(door.above()) || !level.getBlockState(door).is(Blocks.IRON_DOOR)) {
				return InteractionResult.PASS;
			}
			cellDoor(sp, jail);
			return InteractionResult.SUCCESS;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			SLIPPING.clear();
			STOOD.clear();
			LOITERING.clear();
			LOITER_AT.clear();
			WARNED.clear();
		});
	}

	private static void forget(UUID id) {
		SLIPPING.remove(id);
		STOOD.remove(id);
		LOITERING.remove(id);
		LOITER_AT.remove(id);
		WARNED.remove(id);
	}

	/** The /kingdom story reset: none of it any more. */
	static void clear(ServerPlayer player) {
		player.removeAttached(SHACKLE);
		player.removeAttached(EVICTED);
		player.removeAttached(CAST_OUT);
		forget(player.getUUID());
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = Factions.level(server);
		long now = level.getGameTime();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Shackle shackle = player.getAttached(SHACKLE);
			if (shackle != null) {
				chained(server, level, player, shackle, now);
			}
			Integer realm = player.getAttached(EVICTED);
			if (realm != null && now % EVICT_CHECK == 0) {
				evicted(server, level, player, realm, now);
			}
			Long until = player.getAttached(CAST_OUT);
			if (until != null && now >= until && now % 100L == 0L) {
				player.removeAttached(CAST_OUT);
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.hard.cast.ended").withStyle(ChatFormatting.GRAY));
			}
		}
	}

	// ---- still in chains -----------------------------------------------------------------------------------------------

	/** {@code player} woke in chains at {@code at}, held by camp {@code camp}. */
	static void shackle(ServerPlayer player, BlockPos at, int camp) {
		player.setAttached(SHACKLE, new Shackle(at, camp));
	}

	/** {@code e} is a player still in a shackle: a camp's raiders let them be. */
	static boolean shackled(Entity e) {
		return e instanceof Player && e.hasAttached(SHACKLE);
	}

	/** The camp holding {@code player} (or one they slipped from), or -1. */
	static int shackleCamp(ServerPlayer player) {
		Shackle s = player.getAttached(SHACKLE);
		return s == null ? -1 : s.camp();
	}

	private static void chained(MinecraftServer server, ServerLevel level, ServerPlayer player, Shackle shackle, long now) {
		if (!player.isAlive() || player.isSpectator()) {
			return;
		}
		if (now % 20L == 0L) {
			// (nothing in the camp harms a captive while they're bound)
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 4, false, false));
		}
		Vec3 anchor = Vec3.atBottomCenterOf(shackle.at());
		if (player.level() != level || player.position().distanceToSqr(anchor) > CHAIN_REACH * CHAIN_REACH) {
			player.teleportTo(level, anchor.x, anchor.y, anchor.z, Set.<net.minecraft.world.entity.Relative>of(), player.getYRot(), player.getXRot(), false);
			player.setDeltaMovement(Vec3.ZERO);
			level.playSound(null, anchor.x, anchor.y, anchor.z, SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 0.8F, 0.8F);
			if (now % 20L < 10L) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hard.chains.hold").withStyle(ChatFormatting.GRAY));
			}
			SLIPPING.remove(player.getUUID());
			return;
		}
		if (now % SLIP_CHECK != 0L) {
			return;
		}
		Vec3 before = STOOD.put(player.getUUID(), player.position());
		boolean still = before != null && before.distanceToSqr(player.position()) < 0.0004;
		if (!(level.isDarkOutside() && player.isShiftKeyDown() && still)) {
			SLIPPING.remove(player.getUUID());
			return;
		}
		int held = SLIPPING.merge(player.getUUID(), SLIP_CHECK, Integer::sum);
		if (held >= SLIP_TICKS) {
			free(player, "slipped");
			return;
		}
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hard.chains.slipping", held * 100 / SLIP_TICKS).withStyle(ChatFormatting.GRAY));
	}

	/** The shackle comes off ({@code how}: "picked" or "slipped"): the camp hunts them from now, for the day. */
	private static void free(ServerPlayer player, String how) {
		Shackle shackle = player.getAttached(SHACKLE);
		if (shackle == null) {
			return;
		}
		player.removeAttached(SHACKLE);
		SLIPPING.remove(player.getUUID());
		STOOD.remove(player.getUUID());
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0F, 0.9F);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.hard.chains." + how).withStyle(ChatFormatting.GREEN));
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.hard.chains.hunted").withStyle(ChatFormatting.GRAY));
		LifeStory.startHunt(level.getServer(), player, shackle.camp());
	}

	// ---- sentenced -----------------------------------------------------------------------------------------------------

	/**
	 * Puts {@code player} in {@code f}'s cells for {@code ticks} (the cell is dug if it never was; its door set): the
	 * same sentence {@link Busts} serves out and lets them out of at the end. Returns where they were put.
	 */
	static Vec3 imprison(ServerPlayer player, Faction f, long ticks) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = Factions.level(server);
		Underworld.Den d = Underworld.den(server, f.id());
		BlockPos cell = Speakeasy.cell(level, f, d == null ? null : d.cell().orElse(null));
		if (d != null) {
			Underworld.putCell(server, d, cell);
		}
		Speakeasy.cellDoor(level, cell);
		player.setAttached(Busts.JAILED, new Busts.Jail(f.id(), cell, level.getGameTime() + ticks));
		Vec3 at = Vec3.atBottomCenterOf(cell);
		player.teleportTo(level, at.x, at.y, at.z, Set.<net.minecraft.world.entity.Relative>of(), player.getYRot(), 0.0F, false);
		level.playSound(null, at.x, at.y, at.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.NEUTRAL, 1.0F, 0.7F);
		return at;
	}

	/** The fine (emeralds) for a sentence with {@code left} ticks to run. */
	static int fine(long left) {
		return (int)Math.max(1L, (left + DAY_TICKS - 1L) / DAY_TICKS) * FINE_PER_DAY;
	}

	/** A prisoner tries the cell door: emeralds in hand pay the fine, a lockpick in hand picks it, bare hands find it locked. */
	private static void cellDoor(ServerPlayer player, Busts.Jail jail) {
		ServerLevel level = player.level();
		Faction f = Factions.get(level.getServer(), jail.realm());
		if (f == null) {
			return;
		}
		int fine = fine(jail.until() - level.getGameTime());
		ItemStack held = player.getMainHandItem();
		if (held.is(Items.EMERALD)) {
			Inventory inventory = player.getInventory();
			if (inventory.countItem(Items.EMERALD) < fine) {
				player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hard.cell.short", fine).withStyle(ChatFormatting.RED));
				return;
			}
			take(inventory, fine);
			player.removeAttached(Busts.JAILED);
			let(level, player, f);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.hard.cell.paid", fine).withStyle(ChatFormatting.YELLOW));
			return;
		}
		if (held.is(Lockpicking.LOCKPICK)) {
			UUID id = player.getUUID();
			Lockpicking.pickVirtual(player, Speakeasy.doorAt(jail.cell()), CELL_LEVEL, jail.cell().asLong() ^ 0x2545F491L, () -> {
				ServerPlayer again = level.getServer().getPlayerList().getPlayer(id);
				if (again != null && again.hasAttached(Busts.JAILED)) {
					breakOut(again, f);
				}
			});
			return;
		}
		level.playSound(null, player.blockPosition(), SoundEvents.CHEST_LOCKED, SoundSource.BLOCKS, 1.0F, 1.0F);
		player.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.hard.cell.locked", fine).withStyle(ChatFormatting.GRAY));
	}

	/** The cell door is picked: out, but a fugitive of {@code f} now, as any other prisoner who escapes ({@link Busts}). */
	private static void breakOut(ServerPlayer player, Faction f) {
		ServerLevel level = Factions.level(player.level().getServer());
		player.removeAttached(Busts.JAILED);
		let(level, player, f);
		player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.hard.cell.picked", f.name()).withStyle(ChatFormatting.DARK_RED));
		Post.news(level.getServer(), Component.translatable("news.jaymoskingdoms.underworld.escaped", Post.nameOf(player), f.name()));
		Theft.arrest(player, level, f, Theft.OUTLAW_TICKS);
		Busts.charge(player, f, "escape");
	}

	/** Puts a prisoner out by the seat. */
	private static void let(ServerLevel level, ServerPlayer player, Faction f) {
		Vec3 out = Speakeasy.release(level, f);
		player.teleportTo(level, out.x, out.y, out.z, Set.<net.minecraft.world.entity.Relative>of(), player.getYRot(), 0.0F, false);
		level.playSound(null, out.x, out.y, out.z, SoundEvents.IRON_DOOR_OPEN, SoundSource.NEUTRAL, 1.0F, 0.9F);
	}

	private static void take(Inventory inventory, int n) {
		int left = n;
		for (int i = 0; i < inventory.getContainerSize() && left > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.is(Items.EMERALD)) {
				int used = Math.min(left, stack.getCount());
				stack.shrink(used);
				left -= used;
			}
		}
	}

	// ---- evicted -------------------------------------------------------------------------------------------------------

	/** {@code player} was put out of {@code f}: its watch moves them on at night till they hold work or a bed there. */
	static void evict(ServerPlayer player, Faction f) {
		player.setAttached(EVICTED, f.id());
	}

	/**
	 * Whether {@code player} has a place in {@code f}: a {@link Careers} job, or a bed of their own (a spawn bed they
	 * slept in, not the story's own spawn) in its land. There is no inn system, so an inn's bed is just a bed.
	 */
	private static boolean settled(MinecraftServer server, ServerPlayer player, Faction f) {
		if (!Careers.of(player).posts().isEmpty()) {
			return true;
		}
		ServerPlayer.RespawnConfig respawn = player.getRespawnConfig();
		if (respawn == null || respawn.forced() || respawn.respawnData().dimension() != Factions.level(server).dimension()) {
			return false;
		}
		Faction there = Factions.around(server, Factions.level(server), respawn.respawnData().pos(), EVICT_RANGE);
		return there != null && there.id() == f.id();
	}

	private static void evicted(MinecraftServer server, ServerLevel level, ServerPlayer player, int realm, long now) {
		UUID id = player.getUUID();
		Faction f = Factions.get(server, realm);
		if (f == null || f.fallen() || settled(server, player, f)) {
			player.removeAttached(EVICTED);
			forgetLoiter(id);
			if (f != null && !f.fallen()) {
				player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.hard.evict.settled", f.name()).withStyle(ChatFormatting.GREEN));
			}
			return;
		}
		Vec3 before = LOITER_AT.put(id, player.position());
		boolean still = before != null && before.distanceToSqr(player.position()) < 1.0;
		boolean rough = player.isSleeping() || still;
		Faction here = Factions.around(server, level, player.blockPosition(), EVICT_RANGE);
		if (!player.isAlive() || player.level() != level || !Approaches.night(level) || Busts.jailed(player) || !rough || here == null || here.id() != f.id()) {
			forgetLoiter(id);
			return;
		}
		List<Worker> watch = Underworld.guards(level, f, player.getBoundingBox().inflate(WATCH_SIGHT));
		if (watch.isEmpty()) {
			// (unseen is unpunished)
			return;
		}
		int about = LOITERING.merge(id, player.isSleeping() ? EVICT_CHECK * SLEEPER_HASTE : EVICT_CHECK, Integer::sum);
		if (about >= JAIL_AFTER) {
			forgetLoiter(id);
			Speech.say(level, watch.getFirst().mob(), Component.translatable("message.jaymoskingdoms.hard.evict.take"), Speech.RED, Busts.SPEECH);
			imprison(player, f, EVICT_JAIL_TICKS);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.hard.evict.jailed", f.name()).withStyle(ChatFormatting.RED));
		} else if (about >= WARN_AFTER && WARNED.add(id)) {
			Speech.say(level, watch.getFirst().mob(), Component.translatable("message.jaymoskingdoms.hard.evict.warn"), Speech.RED, Busts.SPEECH);
			player.sendSystemMessage(Component.translatable("message.jaymoskingdoms.hard.evict.warned", f.name()).withStyle(ChatFormatting.GOLD));
		}
	}

	private static void forgetLoiter(UUID id) {
		LOITERING.remove(id);
		WARNED.remove(id);
	}

	// ---- cast out by your own ------------------------------------------------------------------------------------------

	/** {@code player} is turned away by their own kind from now, for {@link #CAST_OUT_TICKS}. */
	static void castOut(ServerPlayer player) {
		player.setAttached(CAST_OUT, Factions.level(player.level().getServer()).getGameTime() + CAST_OUT_TICKS);
	}

	/** {@code e} is a player their own kind are still turning away. */
	static boolean castOutActive(Entity e) {
		Long until = e instanceof Player ? e.getAttached(CAST_OUT) : null;
		return until != null && e.level().getGameTime() < until;
	}

	/**
	 * How much {@code who} holds {@code whom}'s blood against them for a cast-out start (from {@link Prejudice#minds}):
	 * {@link #CAST_OUT_MINDS} from a non-human villager or a haven's people, 0 from anyone else or once it's over.
	 */
	static float castOutMinds(LivingEntity who, Entity whom) {
		if (!castOutActive(whom)) {
			return 0.0F;
		}
		return Prejudice.degree(VillagerHeritage.of(who)) > 0 || Havens.home(who) != null ? CAST_OUT_MINDS : 0.0F;
	}

	// ---- the hideout -----------------------------------------------------------------------------------------------------

	/** The realm nearest {@code from} that keeps a speakeasy dug, or null. */
	static @Nullable Faction denRealm(MinecraftServer server, BlockPos from) {
		Faction best = null;
		double bestD = Double.MAX_VALUE;
		for (Faction f : Factions.all(server)) {
			if (f.type().isRealm() && !f.fallen() && Underworld.builtDen(server, f) != null) {
				double d = f.seat().distSqr(from);
				if (d < bestD) {
					bestD = d;
					best = f;
				}
			}
		}
		return best;
	}

	/** The standing settlement (anything but a camp) nearest {@code from}: whose good name decides whether the camps spare an outlaw ({@link Barbarians#outlaw}). */
	static @Nullable Faction nearestSettlement(MinecraftServer server, BlockPos from) {
		Faction best = null;
		double bestD = Double.MAX_VALUE;
		for (Faction f : Factions.all(server)) {
			if (!f.fallen() && f.type() != FactionType.BARBARIAN) {
				double d = f.seat().distSqr(from);
				if (d < bestD) {
					bestD = d;
					best = f;
				}
			}
		}
		return best;
	}
}
