package com.jaymospowers.power.xenoform;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.api.Power;
import com.jaymospowers.api.PowerKey;
import com.jaymospowers.core.Mastery;
import com.jaymospowers.core.Powers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Xenoform: the Xeno-Band, a wrist band out of a fallen star ({@link FallingStars}) that turns you into one of the aliens
 * it holds ({@link XenoForms}), each with its own body and four moves. Everything is decided here, on the server.
 * <ul>
 * <li>Z tap: turn the dial to the next alien. Z held {@link #HOLD_TICKS}: slam the band and become the dialled alien, or
 * turn back early.</li>
 * <li>R G V B: the alien's moves ({@link XenoForm#onKey}). As a human they do nothing.</li>
 * <li>X held {@link #SCAN_TICKS} at a creature: scan it; if it carries an alien's DNA, the band unlocks that alien.</li>
 * <li>A form lasts a random {@link #FORM_MIN}-{@link #FORM_MAX} until the power is fully mastered, then always
 * {@link #FORM_MAX}. The band beeps through the last {@link #BEEP_TICKS}. Then, when the alien's health is gone, or when you
 * hold Z, you turn back at the health you had before, and the band recharges ({@link #RECHARGE}, {@link #RECHARGE_KNOCKOUT}
 * after a knockout).</li>
 * <li>Fists only: while transformed your hands read as empty ({@code XenoformHandsMixin}), so items can't be used and
 * punches hit for the alien's strength.</li>
 * </ul>
 * Ultimate forms (C, once ascended) and the band's corruption glitches are stage 3 / stage 2 of {@code docs/xenoform.md}.
 */
public final class XenoformPower implements Power {
	public static final XenoformPower INSTANCE = new XenoformPower();
	public static final Identifier ID = JaymosPowers.id("xenoform");
	public static final int COLOR = 0x5CF07A;

	// ---- tuning (ticks) ----
	/** Z held this long slams the band. */
	public static final int HOLD_TICKS = 10;
	/** A form lasts a random time in this range, until full mastery (then always the most). */
	public static final int FORM_MIN = 1200, FORM_MAX = 6000;
	/** The band's recharge after turning back, and after being knocked out of a form. */
	public static final int RECHARGE = 1200, RECHARGE_KNOCKOUT = 2400;
	/** The band beeps once a second through the last of a form. */
	public static final int BEEP_TICKS = 200;
	/** X held this long at a creature scans it, from this far (blocks). */
	public static final int SCAN_TICKS = 60;
	public static final double SCAN_REACH = 12.0;
	/** The HUD is sent this often while something ticks (sooner when something changes). */
	private static final int HUD_EVERY = 5;
	/** Hints about the band ("hold Z") repeat no sooner than this. */
	private static final int HINT_SPACING = 60;
	/** Each knockout corrupts the band this much (out of {@link com.jaymospowers.core.Fate#MAX_CORRUPTION}). */
	public static final int KNOCKOUT_CORRUPTION = 25;
	/** Corrupted: a slam gives the wrong alien this often, and the band changes you on its own about every this many ticks. */
	public static final float GLITCH_WRONG_CHANCE = 0.25F;
	public static final int GLITCH_SELF_TICKS = 7200;
	/** How often (ticks) a human checks whether the band is complete and the power ready to ascend. */
	public static final int ASCEND_CHECK_EVERY = 40;

	private static final Identifier SCALE_ID = JaymosPowers.id("xenoform_scale");
	private static final Identifier HEALTH_ID = JaymosPowers.id("xenoform_health");
	private static final Identifier DAMAGE_ID = JaymosPowers.id("xenoform_damage");
	private static final Identifier SPEED_ID = JaymosPowers.id("xenoform_speed");
	private static final Identifier KNOCKBACK_ID = JaymosPowers.id("xenoform_knockback");
	private static final Identifier STEP_ID = JaymosPowers.id("xenoform_step");

	/** Server-side bookkeeping for one player (not saved: a relog turns them back first). */
	public static final class Live {
		@Nullable XenoForm form;
		int ticksLeft, total;
		/** The health they had as a human, given back when they turn back. */
		float humanHealth;
		int recharge, rechargeTotal;
		long modeDownAt = -1L;
		boolean slammed;
		boolean scanning;
		int scanTicks;
		@Nullable String scanForm;
		final long[] cooldownUntil = new long[XenoformState.Hud.SLOTS];
		final int[] cooldownLength = new int[XenoformState.Hud.SLOTS];
		boolean hudDirty = true;
		int hudTick;
		long lastHint;
		// ---- scratch the forms use (see each form) ----
		/** Bulwark: hits left in the fist combo, and when the next lands; the creature held overhead (entity id, -1 none). */
		public int combo;
		public long comboNext;
		public int grabbed = -1;
		/** Bulwark: in a leap, and ticks since it started. Blur: ticks left of a dash's speed. */
		public boolean leaping;
		public int leapTicks;
		public int dashTicks;
		/** Pyre: ticks left of the magma trail; the jet is held. */
		public int trailTicks;
		public boolean jet;
		/** Blur: ticks spent on the wall this run; the chain strike's targets and its clock. */
		public int wallRun;
		public final List<Integer> chain = new ArrayList<>();
		public int chainTick;
		/** Prism: in the reflect stance; the wall's blocks and when it falls. */
		public boolean reflecting;
		public final List<net.minecraft.core.BlockPos> wall = new ArrayList<>();
		public long wallUntil;
		/** Wisp: shrouded; gliding; ticks left of the wail. */
		public boolean shroud, glide;
		public int wail;
		/** Gremlin: climbing; ticks since a spring (0: none). */
		public boolean climb;
		public int spring;
		/** Knell: sensing; ticks left of a burrow and where it comes up. Wyrm: the wings are held. */
		public boolean sense;
		public int burrow;
		public @Nullable Vec3 burrowTo;
		public boolean wings;
		// ---- stage 3: the Ultimate and the corruption ----
		/** In the Ultimate form (C, ascended) for the rest of this timer; the Ultimate's own move's cooldown. */
		public boolean ultimate;
		public long ultimateNext;
		/** The band is corrupted right now: each alien's moves carry their twist. Refreshed every tick. */
		public boolean corrupted;
		/** What the Ultimate summoned (entity ids): Swarm's gremlins, Afterimage's decoys. Dismissed when it ends. */
		public final List<Integer> minions = new ArrayList<>();
		/** Revenant: the creature possessed (entity id, -1 none). Sunheart: the beam is held. */
		public int possessed = -1;
		public boolean beam;
		/** Geode: the crystal placed and the creatures held in it, and when it shatters. The Tolling: tolls left. */
		public final List<net.minecraft.core.BlockPos> crystals = new ArrayList<>();
		public final List<Integer> frozen = new ArrayList<>();
		public long geodeUntil;
		public int tolls;
		/** Corrupted Wyrm: ticks the wings are out; corrupted Blur: ticks of a dash that won't stop. */
		public int wingStall, runaway;
		/** This change has hurt a living thing (the voices' trial counts the ones that don't). */
		public boolean hurt;
		/** Nadir: the Beacon's chosen player, R held for this many ticks (0: not held), and when the last tap was. */
		public @Nullable UUID beaconTarget;
		public int beaconHeld;
		public long beaconTapAt;

		void clearForm() {
			form = null;
			ticksLeft = total = 0;
			java.util.Arrays.fill(cooldownUntil, 0L);
			java.util.Arrays.fill(cooldownLength, 0);
			combo = 0;
			grabbed = -1;
			leaping = false;
			leapTicks = 0;
			dashTicks = 0;
			trailTicks = 0;
			jet = false;
			wallRun = 0;
			chain.clear();
			chainTick = 0;
			reflecting = false;
			wall.clear();
			shroud = glide = false;
			wail = 0;
			climb = false;
			spring = 0;
			sense = false;
			burrow = 0;
			burrowTo = null;
			wings = false;
			ultimate = false;
			ultimateNext = 0L;
			minions.clear();
			possessed = -1;
			beam = false;
			crystals.clear();
			frozen.clear();
			geodeUntil = 0L;
			tolls = 0;
			wingStall = runaway = 0;
			hurt = false;
			beaconTarget = null;
			beaconHeld = 0;
			hudDirty = true;
		}
	}

	private static final Map<UUID, Live> LIVES = new HashMap<>();

	private XenoformPower() {
	}

	/** Called once from {@link JaymosPowers#onInitialize()}. */
	public static void init() {
		Powers.register(INSTANCE);
		XenoNet.register();
		XenoBandItem.init();
		FallenStarBlock.init();
		FallingStars.init();
		// the alien's health is a second life bar: at zero you're knocked back into your own body instead of dying
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || !has(player) || live(player).form == null) {
				return true;
			}
			if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
				// the void, /kill: the form can't save you, but it lets go so you respawn whole
				revert(player, false, false);
				return true;
			}
			revert(player, true, true);
			return false;
		});
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (!(entity instanceof ServerPlayer player) || !has(player)) {
				return true;
			}
			Live live = live(player);
			return live.form == null || live.form.allowDamage(player, live, source, amount);
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (source.getEntity() instanceof ServerPlayer player && player != entity && has(player)) {
				Live live = live(player);
				if (live.form != null) {
					live.hurt = true;
					live.form.onDealtDamage(player, live, entity);
				}
			}
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayer player = handler.player;
			if (has(player) && live(player).form != null) {
				revert(player, false, false);
			}
			LIVES.remove(player.getUUID());
		});
		ServerTickEvents.END_SERVER_TICK.register(FallingStars::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			LIVES.clear();
			FallingStars.clear();
		});
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> XenoformDebugCommand.register(dispatcher));
		XenoVoices.register();
	}

	public static boolean has(Player player) {
		return Powers.has(player, INSTANCE);
	}

	static Live live(ServerPlayer player) {
		return LIVES.computeIfAbsent(player.getUUID(), id -> new Live());
	}

	/** No affinity and no random awakening: the band is the only way in. */
	@Override
	public boolean fated() {
		return false;
	}

	@Override
	public Identifier id() {
		return ID;
	}

	@Override
	public int color() {
		return COLOR;
	}

	@Override
	public ItemStack icon() {
		return new ItemStack(XenoBandItem.BAND);
	}

	/** The badge names the alien you are. */
	@Override
	public @Nullable String statusSuffix(Player player) {
		XenoForm form = XenoformState.form(player);
		return form == null ? null : "." + form.id;
	}

	/** The moves belong to the alien, not to mastery: every key works from the first slam. Mastery stretches the timer. */
	@Override
	public boolean usableEarly(PowerKey key) {
		return true;
	}

	@Override
	public void onGained(ServerPlayer player) {
		XenoformState.Band band = XenoformState.band(player);
		if (band.unlocked().isEmpty()) {
			// a fresh band holds one of the first aliens, at random
			List<XenoForm> starters = XenoForms.all().subList(0, Math.min(XenoForms.STARTERS, XenoForms.all().size()));
			XenoForm first = starters.get(player.getRandom().nextInt(starters.size()));
			player.setAttached(XenoformState.BAND, XenoformState.Band.NONE.plus(first.id));
		}
		live(player).hudDirty = true;
	}

	@Override
	public void onLost(ServerPlayer player) {
		Live live = live(player);
		if (live.form != null) {
			revert(player, false, false);
		}
		LIVES.remove(player.getUUID());
		player.removeAttached(XenoformState.HUD);
		player.removeAttached(XenoformState.FORM);
		player.removeAttached(XenoformState.ENDING);
	}

	@Override
	public boolean allowsFloating(ServerPlayer player) {
		Live live = live(player);
		return live.form != null && live.form.allowsFloating(player, live);
	}

	@Override
	public boolean skipsMovementCheck(ServerPlayer player) {
		Live live = live(player);
		return live.form != null && live.form.skipsMovementCheck(player, live);
	}

	@Override
	public void tick(ServerPlayer player) {
		Live live = live(player);
		if (!player.isAlive()) {
			return;
		}
		long now = player.level().getGameTime();
		// the slam: Z held long enough
		if (live.modeDownAt >= 0L && !live.slammed && now - live.modeDownAt >= HOLD_TICKS) {
			live.slammed = true;
			if (live.form != null) {
				revert(player, false, true);
			} else {
				XenoForm dialled = XenoForms.get(XenoformState.band(player).dialled());
				if (dialled != null) {
					XenoForm glitched = glitch(player, dialled);
					if (transform(player, glitched, false) && glitched != dialled) {
						glitchFx(player, "message.jaymospowers.xenoform.glitch_wrong");
					}
				}
			}
		}
		if (live.form != null) {
			// in a form: the clock runs
			live.ticksLeft--;
			if (live.ticksLeft == BEEP_TICKS) {
				player.setAttached(XenoformState.ENDING, true);
			}
			if (live.ticksLeft <= BEEP_TICKS && live.ticksLeft > 0 && live.ticksLeft % 20 == 0) {
				float pitch = live.ticksLeft <= 60 ? 1.8F : 1.4F;
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.6F, pitch);
				player.level().sendParticles(player, ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFFFF4040), true, false,
					player.getX(), player.getY() + 1.0, player.getZ(), 3, 0.3, 0.3, 0.3, 0.0);
			}
			if (live.ticksLeft <= 0) {
				revert(player, false, true);
			} else {
				live.corrupted = com.jaymospowers.core.Fate.isCorrupted(player);
				live.form.tick(player, live);
				if (live.ultimate && live.form != null) {
					live.form.tickUltimate(player, live);
				}
				// a grabbed creature that got away
				if (live.grabbed >= 0) {
					Entity held = player.level().getEntity(live.grabbed);
					if (held == null || !held.isAlive() || held.getVehicle() != player) {
						live.grabbed = -1;
					}
				}
			}
		} else {
			if (live.recharge > 0 && --live.recharge == 0) {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.recharged").withStyle(ChatFormatting.GREEN));
				player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.5F);
				live.hudDirty = true;
			}
			tickScan(player, live);
			tickAscension(player);
			XenoVoices.tick(player);
			// corrupted: now and then the band changes you without being asked
			if (live.recharge <= 0 && !player.isCreative() && com.jaymospowers.core.Fate.isCorrupted(player)
				&& player.getRandom().nextInt(GLITCH_SELF_TICKS) == 0) {
				XenoForm any = glitch(player, null);
				if (any != null && transform(player, any, false)) {
					glitchFx(player, "message.jaymospowers.xenoform.glitch_self");
				}
			}
		}
		// the HUD: on change, and every so often while a clock runs
		boolean ticking = live.form != null || live.recharge > 0 || live.scanTicks > 0;
		if (live.hudDirty || ticking && ++live.hudTick >= HUD_EVERY) {
			live.hudDirty = false;
			live.hudTick = 0;
			XenoformState.Hud hud = new XenoformState.Hud(live.ticksLeft, live.total, live.recharge, live.rechargeTotal, live.cooldownUntil.clone(),
				live.cooldownLength.clone(), live.scanTicks, SCAN_TICKS, live.scanForm == null ? "" : live.scanForm);
			if (!hud.same(XenoformState.hud(player))) {
				player.setAttached(XenoformState.HUD, hud);
			}
		}
	}

	/** X held as a human: the creature under the crosshair is scanned for an alien's DNA. */
	private static void tickScan(ServerPlayer player, Live live) {
		if (!live.scanning) {
			if (live.scanTicks > 0) {
				live.scanTicks = Math.max(0, live.scanTicks - 3);
				live.hudDirty = live.scanTicks == 0;
				if (live.scanTicks == 0) {
					live.scanForm = null;
				}
			}
			return;
		}
		Entity target = scanTarget(player);
		XenoForm form = target == null ? null : XenoForms.scanOf(target);
		if (form == null || target == null) {
			if (live.scanTicks > 0) {
				live.scanTicks = Math.max(0, live.scanTicks - 3);
			}
			return;
		}
		if (!form.id.equals(live.scanForm)) {
			live.scanForm = form.id;
			live.scanTicks = 0;
		}
		ServerLevel level = player.level();
		Vec3 at = target.getBoundingBox().getCenter();
		if (XenoformState.band(player).has(form.id)) {
			if (live.scanTicks == 0) {
				hint(player, live, Component.translatable("message.jaymospowers.xenoform.known", form.name()));
			}
			live.scanTicks = 1;
			return;
		}
		live.scanTicks++;
		// a thin green beam of motes between you and it
		Vec3 eye = player.getEyePosition();
		Vec3 step = at.subtract(eye).scale(1.0 / 6.0);
		for (int i = 1; i <= 6; i++) {
			Vec3 p = eye.add(step.scale(i));
			level.sendParticles(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xFF000000 | COLOR), p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.0);
		}
		if (live.scanTicks % 10 == 0) {
			level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.5F, 1.5F + live.scanTicks / 60.0F);
		}
		if (live.scanTicks >= SCAN_TICKS) {
			unlock(player, form);
			live.scanTicks = 0;
			live.scanForm = null;
			live.hudDirty = true;
		}
	}

	/** What the crosshair rests on within {@link #SCAN_REACH}: a creature, or a piece of the Ender Dragon (its body). */
	private static @Nullable Entity scanTarget(ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		Vec3 end = eye.add(player.getLookAngle().scale(SCAN_REACH));
		net.minecraft.world.phys.AABB box = player.getBoundingBox().expandTowards(player.getLookAngle().scale(SCAN_REACH)).inflate(1.0);
		net.minecraft.world.phys.EntityHitResult hit = net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(player.level(), player, eye, end, box,
			e -> e != player && e.isAlive() && !e.isSpectator() && (e instanceof LivingEntity || e instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragonPart), 0.3F);
		if (hit == null) {
			return null;
		}
		return hit.getEntity() instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragonPart part ? part.parentMob : hit.getEntity();
	}

	/** The band takes in a new alien (a scan, or a command). */
	public static void unlock(ServerPlayer player, XenoForm form) {
		XenoformState.Band band = XenoformState.band(player);
		if (band.has(form.id)) {
			return;
		}
		player.setAttached(XenoformState.BAND, band.plus(form.id));
		Component name = form.name().copy().withStyle(style -> style.withColor(TextColor.fromRgb(form.color)).withBold(true));
		player.sendSystemMessage(Component.translatable("message.jaymospowers.xenoform.unlocked", name).withStyle(ChatFormatting.GREEN));
		player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.unlocked_overlay", name));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.4F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.8F);
		level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.5, 0.4, 0.0);
		live(player).hudDirty = true;
	}

	/**
	 * The band's corruption: a slam gives a random other unlocked alien {@link #GLITCH_WRONG_CHANCE} of the time
	 * ({@code wanted} null: any unlocked alien, for the band acting on its own). Clean bands always give what was dialled.
	 */
	private static @Nullable XenoForm glitch(ServerPlayer player, @Nullable XenoForm wanted) {
		List<String> unlocked = XenoformState.band(player).unlocked();
		if (wanted != null && (!com.jaymospowers.core.Fate.isCorrupted(player) || unlocked.size() < 2
			|| player.getRandom().nextFloat() >= GLITCH_WRONG_CHANCE)) {
			return wanted;
		}
		List<XenoForm> others = new ArrayList<>();
		for (String id : unlocked) {
			XenoForm form = XenoForms.get(id);
			if (form != null && form != wanted) {
				others.add(form);
			}
		}
		return others.isEmpty() ? wanted : others.get(player.getRandom().nextInt(others.size()));
	}

	/** A glitch always shows: static on the screen, a sour note, and a line saying what the band did. */
	private static void glitchFx(ServerPlayer player, String message) {
		player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 80, 0, false, false));
		player.sendOverlayMessage(Component.translatable(message).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
		player.sendSystemMessage(Component.translatable(message).withStyle(ChatFormatting.DARK_PURPLE));
		ServerLevel level = player.level();
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.PLAYERS, 1.0F, 0.6F);
		level.sendParticles(ParticleTypes.SQUID_INK, player.getX(), player.getY() + 1.0, player.getZ(), 20, 0.4, 0.6, 0.4, 0.05);
	}

	// ---------------------------------------------------------------- keys

	/** R G V B: the alien's moves. As a human, only a reminder. */
	@Override
	public void onKey(ServerPlayer player, PowerKey key, boolean down) {
		Live live = live(player);
		if (live.form != null) {
			live.form.onKey(player, live, key, down);
			return;
		}
		if (down) {
			hint(player, live, Component.translatable("message.jaymospowers.xenoform.hold_z"));
		}
	}

	/** Z: a tap turns the dial, a hold slams the band (see {@link #tick}). */
	@Override
	public void onMode(ServerPlayer player, boolean down) {
		Live live = live(player);
		if (down) {
			live.modeDownAt = player.level().getGameTime();
			live.slammed = false;
			return;
		}
		boolean tap = live.modeDownAt >= 0L && !live.slammed;
		live.modeDownAt = -1L;
		if (tap && live.form == null) {
			dial(player, 1);
		}
	}

	/**
	 * C, once the power has ascended: as an alien, you become its Ultimate form for the rest of the timer (C again is the
	 * Ultimate's own move, where it has one). As yourself, nothing: slam first.
	 */
	@Override
	public void onUltimate(ServerPlayer player, boolean down) {
		if (!down) {
			return;
		}
		Live live = live(player);
		XenoForm form = live.form;
		if (form == null) {
			hint(player, live, Component.translatable("message.jaymospowers.xenoform.ultimate_human"));
			return;
		}
		if (live.ultimate) {
			form.onUltimateAgain(player, live);
			return;
		}
		if (!form.onUltimate(player, live)) {
			hint(player, live, Component.translatable("message.jaymospowers.xenoform.ultimate_not_now"));
			return;
		}
		live.ultimate = true;
		live.hudDirty = true;
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | form.color), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 60, 0.6, 1.0, 0.6, 0.15);
		level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, at.x, at.y, at.z, 40, 0.5, 0.8, 0.5, 0.3);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.2F, 0.9F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.5F, 1.6F);
		player.sendOverlayMessage(form.ultimateName().copy().withStyle(style -> style.withColor(TextColor.fromRgb(form.color)).withBold(true)));
		Mastery.used(player);
	}

	/**
	 * Ascension: the band is complete (every alien scanned) and the power fully mastered. Checked now and then as a
	 * human; {@code Fate.ascend} then tells the player and turns C on.
	 */
	private static void tickAscension(ServerPlayer player) {
		if (player.tickCount % ASCEND_CHECK_EVERY != 0 || com.jaymospowers.core.Fate.isAscended(player)) {
			return;
		}
		if (XenoformState.band(player).unlocked().stream().filter(id -> XenoForms.get(id) != null && !XenoForms.get(id).hidden()).count() >= XenoForms.scannable()
			&& com.jaymospowers.core.Fate.canAscend(player)) {
			com.jaymospowers.core.Fate.ascend(player);
		}
	}

	/** X went down or up (from the client): the scan. */
	static void skill(ServerPlayer player, boolean down) {
		if (!has(player)) {
			return;
		}
		Live live = live(player);
		live.scanning = down;
		if (down && live.form != null) {
			hint(player, live, Component.translatable("message.jaymospowers.xenoform.scan_human"));
		}
	}

	/** Turns the dial {@code by} aliens on. */
	public static void dial(ServerPlayer player, int by) {
		XenoformState.Band band = XenoformState.band(player);
		if (band.unlocked().size() <= 1) {
			XenoForm only = XenoForms.get(band.dialled());
			if (only != null) {
				hint(player, live(player), Component.translatable("message.jaymospowers.xenoform.only_one", only.name()));
			}
			return;
		}
		band = band.withDial(band.dial() + by);
		player.setAttached(XenoformState.BAND, band);
		XenoForm form = XenoForms.get(band.dialled());
		if (form != null) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.dialled", form.name().copy()
				.withStyle(style -> style.withColor(TextColor.fromRgb(form.color)))));
		}
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7F, 1.6F);
	}

	private static void hint(ServerPlayer player, Live live, Component text) {
		long now = player.level().getGameTime();
		if (now - live.lastHint < HINT_SPACING) {
			return;
		}
		live.lastHint = now;
		player.sendOverlayMessage(text.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
	}

	// ---------------------------------------------------------------- transforming

	/** Becomes {@code form}. {@code force} skips the recharge (commands). */
	public static boolean transform(ServerPlayer player, XenoForm form, boolean force) {
		if (form.hidden() && !force && !XenoVoices.content(player)) {
			// the void alien is lent, never owned: the voices must be content first
			XenoVoices.refuse(player);
			return false;
		}
		Live live = live(player);
		if (live.form != null || !player.isAlive()) {
			return false;
		}
		if (!force && live.recharge > 0) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.recharging", Mth.ceil(live.recharge / 20.0F))
				.withStyle(ChatFormatting.RED));
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8F, 0.6F);
			return false;
		}
		if (player.isPassenger()) {
			player.stopRiding();
		}
		if (player.isSleeping()) {
			player.stopSleeping();
		}
		live.clearForm();
		live.form = form;
		live.humanHealth = player.getHealth();
		boolean mastered = Mastery.unlocked(player) >= Mastery.ALL || player.isCreative();
		live.total = mastered ? FORM_MAX : FORM_MIN + player.getRandom().nextInt(FORM_MAX - FORM_MIN + 1);
		live.ticksLeft = live.total;
		player.setAttached(XenoformState.FORM, form.id);
		player.removeAttached(XenoformState.ENDING);
		player.addTag(XenoformState.TAG);
		player.addTag(XenoformState.TAG + ":" + form.id);
		live.corrupted = com.jaymospowers.core.Fate.isCorrupted(player);
		apply(player, form);
		player.setHealth(form.health);
		player.removeEffect(MobEffects.LEVITATION);
		ServerLevel level = player.level();
		Vec3 at = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
		level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | COLOR), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 40, 0.5, 0.8, 0.5, 0.12);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.6F);
		level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 0.4F, 1.8F);
		player.sendOverlayMessage(form.name().copy().withStyle(style -> style.withColor(TextColor.fromRgb(form.color)).withBold(true)));
		form.onEnter(player, live);
		if (live.corrupted) {
			player.sendSystemMessage(form.corruptLine().copy().withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
		}
		Mastery.used(player);
		live.hudDirty = true;
		return true;
	}

	/**
	 * Back to yourself, at the health you had before. {@code knockout}: the alien's health ran out (a longer recharge, and
	 * a moment's dizziness). {@code fx}: the flash and sound (not on a relog).
	 */
	public static void revert(ServerPlayer player, boolean knockout, boolean fx) {
		Live live = live(player);
		XenoForm form = live.form;
		if (form == null) {
			return;
		}
		if (live.ultimate) {
			form.onUltimateEnd(player, live);
		}
		if (fx) {
			XenoVoices.formEnded(player, form, knockout, live.ticksLeft <= 0, live.hurt);
		}
		form.onExit(player, live);
		player.removeTag(XenoformState.TAG);
		player.removeTag(XenoformState.TAG + ":" + form.id);
		for (Entity passenger : new ArrayList<>(player.getPassengers())) {
			passenger.stopRiding();
		}
		player.removeEffect(MobEffects.LEVITATION);
		unapply(player);
		player.removeAttached(XenoformState.FORM);
		player.removeAttached(XenoformState.ENDING);
		player.setHealth(Mth.clamp(live.humanHealth, 1.0F, player.getMaxHealth()));
		player.resetFallDistance();
		live.clearForm();
		live.recharge = live.rechargeTotal = knockout ? RECHARGE_KNOCKOUT : RECHARGE;
		if (player.isCreative()) {
			live.recharge = live.rechargeTotal = 0;
		}
		if (fx) {
			ServerLevel level = player.level();
			Vec3 at = player.position().add(0.0, 1.0, 0.0);
			level.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFF000000 | COLOR), at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 25, 0.4, 0.6, 0.4, 0.08);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE, SoundSource.PLAYERS, 1.0F, 1.4F);
			if (knockout) {
				com.jaymospowers.core.Fate.addCorruption(player, KNOCKOUT_CORRUPTION);
				player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 100, 0, false, false));
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.knocked_out").withStyle(ChatFormatting.RED));
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
			} else {
				player.sendOverlayMessage(Component.translatable("message.jaymospowers.xenoform.reverted").withStyle(ChatFormatting.GRAY));
			}
		}
		live.hudDirty = true;
	}

	/** The alien's body, as attribute modifiers on the player. */
	private static void apply(ServerPlayer player, XenoForm form) {
		unapply(player);
		modify(player, Attributes.SCALE, SCALE_ID, form.scale - 1.0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modify(player, Attributes.MAX_HEALTH, HEALTH_ID, form.health - player.getAttributeValue(Attributes.MAX_HEALTH), AttributeModifier.Operation.ADD_VALUE);
		modify(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID, form.damage - player.getAttributeValue(Attributes.ATTACK_DAMAGE), AttributeModifier.Operation.ADD_VALUE);
		modify(player, Attributes.MOVEMENT_SPEED, SPEED_ID, form.speed, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		modify(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID, form.knockbackResist, AttributeModifier.Operation.ADD_VALUE);
		modify(player, Attributes.STEP_HEIGHT, STEP_ID, form.stepUp, AttributeModifier.Operation.ADD_VALUE);
		player.refreshDimensions();
	}

	private static void unapply(ServerPlayer player) {
		remove(player, Attributes.SCALE, SCALE_ID);
		remove(player, Attributes.MAX_HEALTH, HEALTH_ID);
		remove(player, Attributes.ATTACK_DAMAGE, DAMAGE_ID);
		remove(player, Attributes.MOVEMENT_SPEED, SPEED_ID);
		remove(player, Attributes.KNOCKBACK_RESISTANCE, KNOCKBACK_ID);
		remove(player, Attributes.STEP_HEIGHT, STEP_ID);
		player.refreshDimensions();
	}

	private static void modify(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id,
		double amount, AttributeModifier.Operation operation) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null && Math.abs(amount) > 1.0E-6) {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
		}
	}

	private static void remove(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance != null) {
			instance.removeModifier(id);
		}
	}

	// ---------------------------------------------------------------- shared queries

	/** The alien {@code player} is right now, server side, or null. */
	public static @Nullable XenoForm formOf(ServerPlayer player) {
		return has(player) ? live(player).form : null;
	}

	/** Clears every cooldown and the recharge (the debug command). */
	static void ready(ServerPlayer player) {
		Live live = live(player);
		java.util.Arrays.fill(live.cooldownUntil, 0L);
		live.recharge = 0;
		live.hudDirty = true;
	}
}
