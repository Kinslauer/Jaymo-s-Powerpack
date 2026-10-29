package com.jaymospowers.power.web;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.Shakes;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.17.150) The symbiote's life with a host, and the end of it:
 * <ul>
 * <li><b>Neglect.</b> Starved until its bond is nothing, it stops waiting. Warnings as the bond sinks ({@link #WARN_AT}),
 * then, once it has been nothing for {@link #GRACE_TICKS}, it takes the wheel for good: it seizes, closes over you, and
 * kills you (a real death, past the downed state: {@code genericKill} ignores invulnerability, so
 * {@code Downed} lets it through). Only then does it feed, on what it killed. It leaves as a roaming blob that won't
 * take you again, and nothing of yours is kept for you.</li>
 * <li><b>Remains.</b> Any other death: the blob stays at the spot, holding your belongings ({@link Remains}, saved in the
 * blob), and walking back to it bonds you again with the bond, strain and stats you died with. The void leaves
 * nothing, nor does a death it caused itself.</li>
 * <li><b>Misfires.</b> Settling in (dormant) after the first bond, it now and then helps at the wrong moment
 * ({@link #misfire}).</li>
 * </ul>
 */
public final class WebSymbioteLife {
	// ---- tuning: the neglect death ----
	/** Starving, it speaks up as the bond falls to each of these (lang {@code life.warn.N}, one per step). */
	public static final int[] WARN_AT = {12, 8, 5, 2};
	public static final int WARN_LINES = 4, GNAW_LINES = 3, SEIZE_LINES = 3, CONSUME_LINES = 4;
	/** While it's close to nothing it gnaws at you with a line this often (ticks). */
	static final int GNAW_EVERY = 900;
	/** Bond at nothing, starving: it waits this long (ticks) after the last warning before it takes you. */
	static final int GRACE_TICKS = 1200;
	/** The scene: it seizes you for {@code SEIZE_TICKS}, closes over you for {@code CLOSE_TICKS}, then kills. */
	static final int SEIZE_TICKS = 90, CLOSE_TICKS = 70;
	/** If something saves you from the kill it tries this often, then gives up and tears away (rejects). */
	static final int KILL_TRIES = 3;
	/** The blob feeds on what it killed this long (ticks) before it roams. */
	static final int FEAST_TICKS = 240;

	// ---- tuning: remains ----
	/** The blob that holds your things stays within this of the spot. */
	static final int HOLD_RADIUS = 3;

	// ---- tuning: misfires ----
	/** While settling in, a misfire every so often (ticks; rare: a handful over the whole dormancy). */
	static final int MISFIRE_MIN = 2400, MISFIRE_MAX = 7200;
	/** The kinds: lash, flicker, jump, drop (lang {@code life.misfire.<kind>.N}, {@link #MISFIRE_LINES} each). */
	static final int MISFIRE_KINDS = 4, MISFIRE_LINES = 2;
	/** A lash reaches this far, hits for this much (it's a slap, not an attack). */
	static final double LASH_RANGE = 5.0;
	static final float LASH_DAMAGE = 2.0F;
	/** The suit flickers on this long (ticks). */
	static final int FLICKER_TICKS = 25;
	/** A boosted jump must come within this (ticks) of being armed; it adds this much lift and push. */
	static final int JUMP_WINDOW = 1200;
	static final double JUMP_LIFT = 0.85, JUMP_PUSH = 0.55;

	/** The phase my death hook runs in: after everything else, Downed's included, has let the death through. */
	private static final Identifier AFTER_DOWNED = JaymosPowers.id("web_symbiote_life");

	// ---- what's kept ----

	/** One stack from the inventory, and the slot it comes back to. */
	public record Held(int slot, ItemStack stack) {
		public static final Codec<Held> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.INT.fieldOf("slot").forGetter(Held::slot),
				ItemStack.CODEC.fieldOf("stack").forGetter(Held::stack))
			.apply(i, Held::new));
	}

	/** What the blob keeps of a host who died: who, the bond they had, and their things. */
	public record Remains(UUID owner, WebSymbioteState state, List<Held> items) {
		public static final Codec<Remains> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.CODEC.fieldOf("owner").forGetter(Remains::owner),
				WebSymbioteState.CODEC.fieldOf("state").forGetter(Remains::state),
				Held.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Remains::items))
			.apply(i, Remains::new));
	}

	/** Per player, not saved. */
	private static final class Track {
		int warned;
		long gnawAt;
		long zoneSince;

		Track(long now) {
			this.zoneSince = now;
			this.gnawAt = now + GNAW_EVERY;
		}
	}

	private static final class Scene {
		long start;
		int tries;
		boolean seized, closing;

		Scene(long start) {
			this.start = start;
		}
	}

	private static final Map<UUID, Track> TRACKS = new HashMap<>();
	private static final Map<UUID, Scene> SCENES = new HashMap<>();
	/** Hosts the symbiote is killing itself: their death leaves nothing and the blob feeds. */
	private static final java.util.Set<UUID> CONSUMED = new java.util.HashSet<>();
	/** The belongings taken at the moment of a death, until the blob exists to hold them. */
	private static final Map<UUID, List<Held>> PENDING = new HashMap<>();
	/** Misfires: when the next one is, and a jump armed to go too far (the jump count it must pass, until when). */
	private static final Map<UUID, Long> NEXT_MISFIRE = new HashMap<>();
	private static final Map<UUID, long[]> JUMPS = new HashMap<>();

	private WebSymbioteLife() {
	}

	static void register() {
		// the belongings are taken at the death itself (before the drops), after every other save has had its say
		ServerLivingEntityEvents.ALLOW_DEATH.addPhaseOrdering(JaymosPowers.id("downed_last"), AFTER_DOWNED);
		ServerLivingEntityEvents.ALLOW_DEATH.register(AFTER_DOWNED, (entity, source, amount) -> {
			if (entity instanceof ServerPlayer player && WebSymbiote.state(player).bonded()) {
				take(player, source);
			}
			return true;
		});
		// whoever died, if the blob is waiting: a pull toward the place
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			if (!alive && WAITING.remove(newPlayer.getUUID()) != null) {
				WebSymbiote.whisper(newPlayer, "life.waiting");
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			UUID id = handler.player.getUUID();
			TRACKS.remove(id);
			NEXT_MISFIRE.remove(id);
			JUMPS.remove(id);
			PENDING.remove(id);
			CONSUMED.remove(id);
			SCENES.remove(id);
		});
	}

	/** Who has died with a blob waiting on them (so they're told once they're back). */
	private static final Map<UUID, Boolean> WAITING = new HashMap<>();

	/** Whispers {@code key} and drives the screen as hard as {@code urgency} and {@code ticks} say. */
	private static void speak(ServerPlayer player, String key, float urgency, int ticks) {
		WebSymbiote.whisper(player, key);
		ServerPlayNetworking.send(player, new WebNet.Dread(ticks, true, true, urgency));
	}

	private static String line(ServerPlayer player, String stem, int lines) {
		return stem + "." + (1 + player.getRandom().nextInt(lines));
	}

	// ---- neglect: warnings, then the scene ----

	/**
	 * Every tick for a bonded host (from {@code WebSymbiote.tick}). True while the scene has the host: nothing else of the
	 * symbiote's runs.
	 */
	static boolean tick(ServerPlayer player, WebSymbioteState state, long now) {
		UUID id = player.getUUID();
		Scene scene = SCENES.get(id);
		if (scene != null) {
			return scene(player, scene, now);
		}
		if (player.isCreative() || player.isSpectator() || !state.bonded()) {
			TRACKS.remove(id);
			return false;
		}
		boolean starving = state.satiety() <= WebSymbiote.STARVING;
		if (!starving || state.bond() > WARN_AT[0]) {
			// fed, or warmed back up: it forgives
			TRACKS.remove(id);
			return false;
		}
		if (WebSymbioteBond.dormant(player) || WebSymbioteInfection.held(player)) {
			return false;
		}
		Track track = TRACKS.computeIfAbsent(id, k -> new Track(now));
		int step = 0;
		for (int i = 0; i < WARN_AT.length; i++) {
			if (state.bond() <= WARN_AT[i]) {
				step = i + 1;
			}
		}
		if (step > track.warned) {
			track.warned = step;
			track.gnawAt = now + GNAW_EVERY;
			warn(player, step);
		} else if (now >= track.gnawAt) {
			track.gnawAt = now + GNAW_EVERY;
			gnaw(player);
		}
		if (state.bond() <= 0 && track.warned >= WARN_AT.length && now - track.zoneSince >= GRACE_TICKS && !WebSymbiote.driving(player)
			&& player.isAlive()) {
			begin(player, now);
			return true;
		}
		return false;
	}

	/** The bond has sunk to {@code WARN_AT[step - 1]}: it says so, and the screen and the body feel it. */
	private static void warn(ServerPlayer player, int step) {
		ServerLevel level = player.level();
		float urgency = Mth.lerp(step / (float)WARN_AT.length, WebSymbiote.URGENCY_WARNING, WebSymbiote.URGENCY_MAX);
		speak(player, "life.warn." + Math.min(step, WARN_LINES), urgency, WebSymbiote.WHISPER_FX_TICKS * 2);
		Shakes.send(player, 2.0F + step * 1.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.4F, 0.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.PLAYERS, 0.6F, 0.7F);
		level.sendParticles(WebSymbioteStrains.dust(player, 1.2F), player.getX(), player.getY() + 1.0, player.getZ(), 14 + step * 6, 0.4, 0.8, 0.4, 0.0);
		player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60 + step * 20, 0, false, false));
		if (step >= 3) {
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0, false, false));
		}
	}

	/** Close to nothing, it keeps at you. */
	private static void gnaw(ServerPlayer player) {
		ServerLevel level = player.level();
		speak(player, line(player, "life.gnaw", GNAW_LINES), WebSymbiote.URGENCY_WARNING, WebSymbiote.WHISPER_FX_TICKS);
		Shakes.send(player, 3.0F);
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 10, 0.3, 0.6, 0.3, 0.02);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PHANTOM_BITE, SoundSource.PLAYERS, 0.5F, 0.5F);
	}

	/** It has stopped waiting. */
	static void begin(ServerPlayer player, long now) {
		UUID id = player.getUUID();
		if (SCENES.containsKey(id) || !WebSymbiote.state(player).bonded()) {
			return;
		}
		// (the scene's first tick is the next one)
		SCENES.put(id, new Scene(now + 1));
		TRACKS.remove(id);
	}

	private static boolean scene(ServerPlayer player, Scene scene, long now) {
		UUID id = player.getUUID();
		WebSymbioteState state = WebSymbiote.state(player);
		if (!player.isAlive() || !state.bonded()) {
			SCENES.remove(id);
			return false;
		}
		ServerLevel level = player.level();
		long t = now - scene.start;
		int total = SEIZE_TICKS + CLOSE_TICKS;
		float progress = Mth.clamp(t / (float)total, 0.0F, 1.0F);
		double x = player.getX(), z = player.getZ();
		if (t == 0L) {
			speak(player, line(player, "life.seize", SEIZE_LINES), WebSymbiote.URGENCY_MAX, WebSymbiote.WHISPER_FX_TICKS * 3);
			player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, total + 40, 4, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, total + 40, 2, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, total + 40, 0, false, false));
			player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, total + 40, 0, false, false));
			level.playSound(null, x, player.getY(), z, SoundEvents.WARDEN_ROAR, SoundSource.PLAYERS, 1.0F, 1.5F);
			level.playSound(null, x, player.getY(), z, SoundEvents.SLIME_BLOCK_BREAK, SoundSource.PLAYERS, 1.4F, 0.4F);
		}
		if (!scene.seized && t >= SEIZE_TICKS) {
			// it closes over you
			scene.seized = true;
			scene.closing = true;
			speak(player, line(player, "life.consume", CONSUME_LINES), WebSymbiote.URGENCY_MAX, WebSymbiote.WHISPER_FX_TICKS * 3);
			player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, CLOSE_TICKS + 20, 0, false, false));
			level.playSound(null, x, player.getY(), z, SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.9F, 1.3F);
			level.playSound(null, x, player.getY(), z, SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS, 1.5F, 0.4F);
		}
		if (t % 4 == 0) {
			Shakes.send(player, 3.0F + 9.0F * progress);
		}
		if (t % 20 == 0) {
			level.playSound(null, x, player.getY(), z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 1.6F, 0.5F + 0.5F * progress);
		}
		if (t % 12 == 0) {
			ServerPlayNetworking.send(player, new WebNet.Dread(WebSymbiote.WHISPER_FX_TICKS, true, true, WebSymbiote.URGENCY_MAX));
		}
		// tendrils climbing the body, thickening and drawing in
		int arms = 3 + (int)(8 * progress);
		double radius = Mth.lerp(progress, 0.95, 0.25);
		for (int k = 0; k < arms; k++) {
			double a = t * 0.3 + k * Math.PI * 2.0 / arms;
			double y = ((t * 0.045 + k * 0.37) % 1.0) * player.getBbHeight();
			level.sendParticles(WebSymbioteStrains.dust(player, 1.2F), x + Math.cos(a) * radius, player.getY() + y, z + Math.sin(a) * radius, 1, 0.0, 0.0, 0.0, 0.0);
		}
		if (t % 6 == 0) {
			level.sendParticles(ParticleTypes.SQUID_INK, x, player.getY() + 1.0, z, 3 + (int)(10 * progress), 0.35, 0.7, 0.35, 0.03);
		}
		if (t >= total) {
			kill(player, scene, now);
		}
		return true;
	}

	/** The kill: a death that ignores invulnerability, so the downed state has nothing to say about it. */
	private static void kill(ServerPlayer player, Scene scene, long now) {
		UUID id = player.getUUID();
		ServerLevel level = player.level();
		CONSUMED.add(id);
		player.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
		if (!player.isAlive()) {
			SCENES.remove(id);
			return;
		}
		// something held it off (a power's save): again shortly, a few times, then it tears away
		CONSUMED.remove(id);
		if (++scene.tries >= KILL_TRIES) {
			SCENES.remove(id);
			WebSymbiote.reject(player);
		} else {
			scene.start = now - (SEIZE_TICKS + CLOSE_TICKS) + 20;
		}
	}

	// ---- death: remains ----

	/**
	 * The host is about to die (the last word before the drops): their things go into {@code PENDING} for the blob, unless
	 * the symbiote is killing them itself, the void has them, or keepInventory or Jaymo's Kitchen's corpse has them.
	 */
	private static void take(ServerPlayer player, DamageSource source) {
		UUID id = player.getUUID();
		PENDING.remove(id);
		if (CONSUMED.contains(id) || source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			return;
		}
		if (player.level().getGameRules().get(GameRules.KEEP_INVENTORY) || KitchenCompat.playersLeaveCorpses()) {
			return;
		}
		Inventory inventory = player.getInventory();
		List<Held> held = new ArrayList<>();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			// (a vanishing curse still vanishes: it stays for the death to destroy)
			if (stack.isEmpty() || EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) {
				continue;
			}
			held.add(new Held(slot, stack.copy()));
			inventory.setItem(slot, ItemStack.EMPTY);
		}
		PENDING.put(id, held);
	}

	/** The bonded host has died (from {@code WebSymbiote}'s after-death hook): it pours out, and keeps what's theirs, or feeds. */
	static void died(ServerPlayer player, DamageSource source) {
		UUID id = player.getUUID();
		WebSymbioteState old = WebSymbiote.state(player);
		boolean consumed = CONSUMED.remove(id);
		List<Held> held = PENDING.remove(id);
		SCENES.remove(id);
		TRACKS.remove(id);
		NEXT_MISFIRE.remove(id);
		JUMPS.remove(id);
		WebSymbioteBlob blob = WebSymbiote.withdraw(player);
		if (consumed) {
			blob.feast(player);
			return;
		}
		if (source.is(DamageTypes.FELL_OUT_OF_WORLD)) {
			return;
		}
		blob.hold(new Remains(id, old, held == null ? List.of() : List.copyOf(held)));
		WAITING.put(id, Boolean.TRUE);
	}

	/**
	 * {@code player} has come to the blob that holds their things. If it's theirs: the bond comes back as it was, and so
	 * do the things (true; the caller does nothing more). Otherwise false.
	 */
	static boolean rebond(WebSymbioteBlob blob, ServerPlayer player, Vec3 from) {
		Remains remains = blob.remains;
		if (remains == null || !remains.owner().equals(player.getUUID())) {
			return false;
		}
		blob.remains = null;
		WebSymbioteState old = remains.state();
		WebSymbiote.bond(player, from, old.kind());
		// the same bond as before: what it was, not a stranger's; no settling in again, no fight for the suit
		WebSymbiote.set(player, new WebSymbioteState(true, Math.max(old.satiety(), WebSymbiote.SATIETY_START), old.stress(), old.rejections(),
			WebSymbioteState.SUIT | old.flags() & WebSymbioteState.COLLECT, old.hints(), old.bond(), old.strain()));
		WebSymbioteBond.clear(player);
		giveBack(player, remains.items());
		speak(player, line(player, "life.rebond", 2), WebSymbiote.URGENCY_MOOD, WebSymbiote.WHISPER_FX_TICKS * 2);
		return true;
	}

	/** Their things back where they were (or anywhere there's room, or at their feet). */
	static void giveBack(ServerPlayer player, List<Held> items) {
		Inventory inventory = player.getInventory();
		for (Held held : items) {
			ItemStack stack = held.stack().copy();
			if (held.slot() >= 0 && held.slot() < inventory.getContainerSize() && inventory.getItem(held.slot()).isEmpty()) {
				inventory.setItem(held.slot(), stack);
			} else if (!inventory.add(stack) && !stack.isEmpty()) {
				player.drop(stack, false, Prediction.SERVER_ONLY);
			}
		}
	}

	/** Someone else's blob has the owner's things and they stand beside it with another symbiote: just the things. */
	static void returnThings(WebSymbioteBlob blob, ServerPlayer owner) {
		Remains remains = blob.remains;
		if (remains == null) {
			return;
		}
		blob.remains = null;
		giveBack(owner, remains.items());
		WebSymbiote.whisper(owner, "life.returned");
	}

	/** The blob is gone (killed, burned, removed): what it kept spills where it was. */
	static void spill(WebSymbioteBlob blob, ServerLevel level) {
		Remains remains = blob.remains;
		if (remains == null) {
			return;
		}
		blob.remains = null;
		for (Held held : remains.items()) {
			blob.spawnAtLocation(level, held.stack().copy());
		}
	}

	/** The owner of the things this blob keeps, if they're about and free to take them back. */
	static @Nullable Player ownerQuarry(WebSymbioteBlob blob, ServerLevel level) {
		Remains remains = blob.remains;
		if (remains == null) {
			return null;
		}
		Player owner = level.getPlayerByUUID(remains.owner());
		if (owner == null || !owner.isAlive() || owner.isSpectator() || owner.distanceTo(blob) > WebSymbioteBlob.SEEK_RANGE) {
			return null;
		}
		return owner;
	}

	/** Per tick for a blob: its feast, and its things (true: it's busy feeding, nothing else of it runs). */
	static boolean blobTick(WebSymbioteBlob blob, ServerLevel level) {
		if (blob.remains != null && blob.tickCount % 10 == 0 && level.getPlayerByUUID(blob.remains.owner()) instanceof ServerPlayer owner
			&& owner.isAlive() && owner.distanceTo(blob) <= WebSymbioteBlob.BOND_REACH + 0.5 && WebSymbiote.state(owner).bonded()) {
			// they have another symbiote now and it can't take them: the things are theirs anyway
			returnThings(blob, owner);
		}
		if (blob.feast <= 0) {
			return false;
		}
		blob.feast--;
		blob.getNavigation().stop();
		if (blob.feast % 8 == 0) {
			level.playSound(null, blob.getX(), blob.getY(), blob.getZ(), blob.getRandom().nextBoolean() ? SoundEvents.PHANTOM_BITE : SoundEvents.SLIME_SQUISH,
				SoundSource.HOSTILE, 0.9F, 0.5F + blob.getRandom().nextFloat() * 0.3F);
			level.sendParticles(ParticleTypes.SQUID_INK, blob.getX(), blob.getY() + 0.3, blob.getZ(), 6, 0.5, 0.2, 0.5, 0.05);
			level.sendParticles(WebSymbioteStrains.dust(blob, 1.4F), blob.getX(), blob.getY() + 0.4, blob.getZ(), 6, 0.6, 0.2, 0.6, 0.0);
		}
		if (blob.feast == 0) {
			blob.setNoAi(false);
			level.playSound(null, blob.getX(), blob.getY(), blob.getZ(), SoundEvents.WARDEN_AGITATED, SoundSource.HOSTILE, 1.0F, 0.6F);
		}
		return true;
	}

	static void save(WebSymbioteBlob blob, net.minecraft.world.level.storage.ValueOutput output) {
		if (blob.remains != null) {
			output.store("life_remains", Remains.CODEC, blob.remains);
		}
		if (blob.rampageSite != null) {
			output.putIntArray("life_rampage", new int[] {blob.rampageSite.getX(), blob.rampageSite.getY(), blob.rampageSite.getZ()});
		}
	}

	static void load(WebSymbioteBlob blob, net.minecraft.world.level.storage.ValueInput input) {
		input.read("life_remains", Remains.CODEC).ifPresent(r -> blob.hold(r));
		input.getIntArray("life_rampage").filter(a -> a.length == 3)
			.ifPresent(a -> blob.rampage(new net.minecraft.core.BlockPos(a[0], a[1], a[2])));
		// (a feast cut short by a save: it's over)
		blob.feast = 0;
		blob.setNoAi(false);
	}

	// ---- misfires (settling in) ----

	/** Every tick while it's dormant (from {@code WebSymbioteBond}'s dormancy tick). */
	static void misfire(ServerPlayer player, long now) {
		UUID id = player.getUUID();
		if (player.isCreative() || player.isSpectator() || !player.isAlive() || WebSymbioteStrains.asleep(player) || WebSymbiote.driving(player)) {
			return;
		}
		// an armed jump goes too far
		long[] jump = JUMPS.get(id);
		if (jump != null) {
			int count = player.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP));
			if (now > jump[1]) {
				JUMPS.remove(id);
			} else if (count > jump[0]) {
				JUMPS.remove(id);
				overjump(player);
			}
		}
		Long next = NEXT_MISFIRE.get(id);
		if (next == null) {
			NEXT_MISFIRE.put(id, now + MISFIRE_MIN + player.getRandom().nextInt(MISFIRE_MAX - MISFIRE_MIN + 1));
			return;
		}
		if (now < next || jump != null) {
			return;
		}
		NEXT_MISFIRE.put(id, now + MISFIRE_MIN + player.getRandom().nextInt(MISFIRE_MAX - MISFIRE_MIN + 1));
		misfire(player, now, player.getRandom().nextInt(MISFIRE_KINDS));
	}

	/** One misfire of {@code kind} now (0 lash, 1 flicker, 2 jump, 3 drop); a kind with nothing to act on becomes a lash. */
	static void misfire(ServerPlayer player, long now, int kind) {
		ServerLevel level = player.level();
		switch (kind) {
			case 1 -> flicker(player, now);
			case 2 -> {
				// it will boost the next jump
				JUMPS.put(player.getUUID(), new long[] {player.getStats().getValue(Stats.CUSTOM.get(Stats.JUMP)), now + JUMP_WINDOW});
				level.sendParticles(WebSymbioteStrains.dust(player, 1.0F), player.getX(), player.getY() + 0.2, player.getZ(), 10, 0.3, 0.1, 0.3, 0.0);
				flinch(player, 2, 2.0F);
			}
			case 3 -> {
				if (!drop(player)) {
					lash(player);
				}
			}
			default -> lash(player);
		}
	}

	/** The common reaction: a line, the dark at the screen's edge, a jolt. */
	private static void flinch(ServerPlayer player, int kind, float shake) {
		speak(player, "life.misfire." + kind + "." + (1 + player.getRandom().nextInt(MISFIRE_LINES)), WebSymbiote.URGENCY_STRUGGLE, WebSymbiote.WHISPER_FX_TICKS);
		Shakes.send(player, shake);
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, 0.8F, 0.5F);
	}

	/** A tendril snaps out at the wrong moment: at the nearest creature (a slap), else at nothing. */
	private static void lash(ServerPlayer player) {
		ServerLevel level = player.level();
		Vec3 from = player.position().add(0.0, player.getBbHeight() * 0.65, 0.0);
		LivingEntity target = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(LASH_RANGE),
				e -> e != player && !(e instanceof Player) && !(e instanceof ArmorStand) && e.isAlive() && !WebSymbioteStrains.symbiotic(e)).stream()
			.min(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(player))).orElse(null);
		Vec3 to;
		if (target != null) {
			to = target.position().add(0.0, target.getBbHeight() * 0.6, 0.0);
			Vec3 away = target.position().subtract(player.position()).horizontal();
			target.hurtServer(level, level.damageSources().playerAttack(player), LASH_DAMAGE);
			if (away.lengthSqr() > 1.0E-4) {
				target.setDeltaMovement(target.getDeltaMovement().add(away.normalize().scale(0.6).add(0.0, 0.3, 0.0)));
				target.needsSync = true;
				target.syncVelocity = true;
			}
		} else {
			double a = player.getRandom().nextDouble() * Math.PI * 2.0;
			to = from.add(Math.cos(a) * 3.5, 0.3, Math.sin(a) * 3.5);
		}
		tendril(level, player, from, to);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.PLAYERS, 0.8F, 1.4F);
		flinch(player, 0, 4.0F);
	}

	/** The suit flares on for a moment, then tears away again. */
	private static void flicker(ServerPlayer player, long now) {
		WebSymbiote.showSuit(player, now + FLICKER_TICKS);
		ServerLevel level = player.level();
		level.sendParticles(WebSymbioteStrains.dust(player, 1.4F), player.getX(), player.getY() + 1.0, player.getZ(), 24, 0.35, 0.8, 0.35, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.HONEY_BLOCK_SLIDE, SoundSource.PLAYERS, 0.9F, 0.7F);
		flinch(player, 1, 3.0F);
	}

	/** A jump it boosts too far. */
	private static void overjump(ServerPlayer player) {
		Vec3 look = player.getLookAngle().horizontal();
		Vec3 push = look.lengthSqr() > 1.0E-4 ? look.normalize().scale(JUMP_PUSH) : Vec3.ZERO;
		player.setDeltaMovement(player.getDeltaMovement().add(push.x, JUMP_LIFT, push.z));
		player.needsSync = true;
		player.syncVelocity = true;
		ServerLevel level = player.level();
		level.sendParticles(WebSymbioteStrains.dust(player, 1.2F), player.getX(), player.getY() + 0.1, player.getZ(), 16, 0.3, 0.05, 0.3, 0.0);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SLIME_BLOCK_PLACE, SoundSource.PLAYERS, 1.0F, 0.6F);
		flinch(player, 2, 3.0F);
	}

	/** A tendril reaches for what you hold and lets go of it (false: your hands are empty). */
	private static boolean drop(ServerPlayer player) {
		ItemStack held = player.getMainHandItem();
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		if (held.isEmpty()) {
			held = player.getOffhandItem();
			hand = net.minecraft.world.InteractionHand.OFF_HAND;
		}
		if (held.isEmpty()) {
			return false;
		}
		ItemStack out = held.copy();
		player.setItemInHand(hand, ItemStack.EMPTY);
		ServerLevel level = player.level();
		Vec3 from = player.position().add(0.0, player.getBbHeight() * 0.65, 0.0);
		Vec3 hand3 = player.getEyePosition().add(player.getViewVector(1.0F).scale(0.7)).subtract(0.0, 0.4, 0.0);
		player.drop(out, false, Prediction.SERVER_ONLY);
		tendril(level, player, from, hand3);
		level.playSound(null, from.x, from.y, from.z, SoundEvents.SLIME_BLOCK_HIT, SoundSource.PLAYERS, 1.0F, 0.6F);
		flinch(player, 3, 3.0F);
		return true;
	}

	private static void tendril(ServerLevel level, ServerPlayer player, Vec3 from, Vec3 to) {
		Vec3 step = to.subtract(from);
		int n = Math.max(2, (int)(step.length() / 0.25));
		for (int i = 0; i <= n; i++) {
			Vec3 at = from.add(step.scale(i / (double)n));
			level.sendParticles(WebSymbioteStrains.dust(player, 1.1F), at.x, at.y, at.z, 2, 0.04, 0.04, 0.04, 0.0);
		}
		level.sendParticles(ParticleTypes.SQUID_INK, from.x, from.y, from.z, 8, 0.25, 0.15, 0.25, 0.06);
	}

	// ---- debug ----

	/** {@code /powerdebug web symbiote life ...} */
	static LiteralArgumentBuilder<CommandSourceStack> debug() {
		return Commands.literal("life")
			// the neglect death, now
			.then(Commands.literal("consume").executes(c -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				begin(player, player.level().getGameTime());
				return 1;
			}))
			// the nth warning (1-4)
			.then(Commands.literal("warn")
				.then(Commands.argument("step", IntegerArgumentType.integer(1, WARN_AT.length)).executes(c -> {
					warn(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "step"));
					return 1;
				})))
			// a misfire (0 lash, 1 flicker, 2 jump, 3 drop)
			.then(Commands.literal("misfire")
				.then(Commands.argument("kind", IntegerArgumentType.integer(0, MISFIRE_KINDS - 1)).executes(c -> {
					ServerPlayer player = c.getSource().getPlayerOrException();
					misfire(player, player.level().getGameTime(), IntegerArgumentType.getInteger(c, "kind"));
					return 1;
				})))
			// a blob rampaging round here (a meteor's)
			.then(Commands.literal("rampage").executes(c -> {
				ServerPlayer player = c.getSource().getPlayerOrException();
				WebSymbioteBlob blob = WebSymbioteBlob.release(player.level(), player.position().add(player.getViewVector(1.0F).scale(6.0)));
				blob.rampage(blob.blockPosition());
				return 1;
			}));
	}
}
