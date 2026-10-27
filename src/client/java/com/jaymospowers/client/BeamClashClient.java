package com.jaymospowers.client;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.core.BeamClash;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A beam clash ({@link BeamClash}) as everyone near sees and hears it, from the synced {@link BeamClash#CLASH}: both
 * streams drawn as straight beams into the knot, the knot churning (wind spiralling into smoke, sparks of pressure, rings
 * pulsing out), and a roaring, grinding loop at the knot. For the two users: rooted (sliding back while losing), a
 * balance bar bottom-right in the chip style, and the spin of being overwhelmed by wind.
 */
public final class BeamClashClient {
	/** How far ahead of the eyes a beam starts (blocks); your own in first person; how far under the eyes. */
	private static final double OUT = 0.75, OWN_OUT = 1.6, DROP = 0.24;
	/** A beam: a puff every this many blocks, this thick (blocks) at the mouth and this much thicker at the knot at full push. */
	private static final double BEAM_STEP = 0.45, BEAM_WIDTH = 0.08, BEAM_SWELL = 0.35;
	/** The knot: how wide it churns, how many puffs a tick on each side, how fast it turns. */
	private static final double KNOT_SIZE = 1.3;
	private static final int KNOT_PUFFS = 7;
	private static final float KNOT_TURN = 0.45F;
	/** A ring pulses out every this many ticks (faster the harder they push), of this many puffs. */
	private static final int RING_EVERY = 10, RING_EVERY_MIN = 4, RING_PUFFS = 22;
	private static final double RING_SPEED = 0.32;
	/** Losing: the body slides back this fast (blocks/tick) at the worst. */
	private static final double SLIDE = 0.035;
	/** The HUD panel: size; the push that fills a push meter. */
	static final int PANEL_W = 96, PANEL_H = 25;
	private static final int CHIP = 20;
	private static final float PUSH_FULL = BeamClash.BASE_PUSH * 2.0F + BeamClash.SURGE_MAX;
	/** Colours by kind: wind, smoke, soot, ki. */
	private static final int[] COLORS = {0xBFF4EA, 0x9A9A9A, 0x3A3030, 0xFFE070};
	/** The power skill key (registered in {@code WebSymbioteClient}); a ki beam is pushed with V (ability 3). */
	private static final String KEY_NAME = "key.jaymospowers.web_symbiote", KI_KEY_NAME = "key.jaymospowers.ability_3";

	/** Lead player's entity id → the knot's roar and grind. */
	private static final Map<Integer, KnotSound[]> SOUNDS = new HashMap<>();
	private static float spin;
	private static int spinTicks, spinTotal;
	private static final Map<String, KeyMapping> KEYS = new HashMap<>();

	private BeamClashClient() {
	}

	static void init() {
		ClientPlayNetworking.registerGlobalReceiver(BeamClash.Spin.TYPE, (payload, ctx) -> {
			spin = payload.degrees();
			spinTicks = spinTotal = Math.max(1, payload.ticks());
		});
		ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
			if (!minecraft.isPaused()) {
				tick(minecraft);
			}
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, JaymosPowers.id("beam_clash"), BeamClashClient::render);
		// (0.17.45) the timing game: right click as the marker crosses the window
		BeamClashTimingClient.init();
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			SOUNDS.clear();
			spinTicks = 0;
		});
	}

	public static boolean isClashing(@Nullable Player player) {
		return player != null && BeamClash.isClashing(player);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		LocalPlayer self = minecraft.player;
		if (level == null || self == null) {
			SOUNDS.clear();
			return;
		}
		// overwhelmed by wind: the world whirls round, slowing
		if (spinTicks > 0) {
			float share = spinTicks / (float)spinTotal;
			self.setYRot(self.getYRot() + spin * share);
			spinTicks--;
		}
		BeamClash.State own = self.getAttached(BeamClash.CLASH);
		if (own != null) {
			// rooted; losing, the feet are dragged back
			Vec3 away = self.position().subtract(own.knot());
			away = new Vec3(away.x, 0.0, away.z);
			Vec3 slide = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize().scale(SLIDE * Math.max(0.0F, -own.balance()));
			Vec3 v = self.getDeltaMovement();
			self.setDeltaMovement(slide.x, Math.min(v.y, 0.0), slide.z);
		}
		RandomSource random = level.getRandom();
		SOUNDS.values().removeIf(sounds -> sounds[0].isStopped() && sounds[1].isStopped());
		for (Player player : level.players()) {
			BeamClash.State state = player.getAttached(BeamClash.CLASH);
			if (state == null) {
				continue;
			}
			beam(minecraft, level, random, player, state);
			Entity other = level.getEntity(state.opponent());
			// the knot is drawn once, by the lead (or by whichever of the two is in view)
			if (state.lead() || !(other instanceof Player)) {
				knot(level, random, player, other, state);
				if (!SOUNDS.containsKey(player.getId())) {
					KnotSound[] sounds = {new KnotSound(player, SoundEvents.ELYTRA_FLYING, 1.6F, 0.45F), new KnotSound(player, SoundEvents.MINECART_RIDING, 1.0F, 0.5F)};
					SOUNDS.put(player.getId(), sounds);
					minecraft.getSoundManager().play(sounds[0]);
					minecraft.getSoundManager().play(sounds[1]);
				}
			}
		}
	}

	private static Vec3 mouth(Minecraft minecraft, Player player) {
		Vec3 look = player.getLookAngle();
		double out = player == minecraft.player && minecraft.options.getCameraType().isFirstPerson() ? OWN_OUT : OUT;
		return player.getEyePosition().add(look.scale(out)).add(0.0, -DROP, 0.0);
	}

	/** A straight beam from the mouth (or hands) into the knot, swelling with the push behind it. */
	private static void beam(Minecraft minecraft, ClientLevel level, RandomSource random, Player player, BeamClash.State state) {
		if (state.kind() == BeamClash.KIND_KI) {
			// a ki beam draws itself to the knot (ZFighterBeams): only sparks along it here
			if (random.nextInt(2) == 0) {
				Vec3 from = mouth(minecraft, player);
				Vec3 p = from.lerp(state.knot(), random.nextDouble());
				level.addParticle(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, random.nextGaussian() * 0.1, random.nextGaussian() * 0.1, random.nextGaussian() * 0.1);
			}
			return;
		}
		Vec3 from = mouth(minecraft, player);
		Vec3 line = state.knot().subtract(from);
		double length = line.length();
		if (length < 0.2) {
			return;
		}
		Vec3 dir = line.scale(1.0 / length);
		double force = Mth.clamp(state.push() / PUSH_FULL, 0.0F, 1.0F);
		int kind = state.kind();
		for (double t = random.nextDouble() * BEAM_STEP; t < length; t += BEAM_STEP) {
			double share = t / length;
			double r = BEAM_WIDTH + BEAM_SWELL * share * (0.4 + force);
			Vec3 p = from.add(dir.scale(t));
			Vec3 v = dir.scale(0.12 + 0.2 * force);
			ParticleOptions particle = kind == BeamClash.KIND_WIND ? (random.nextInt(4) == 0 ? ParticleTypes.CLOUD : ParticleTypes.WHITE_SMOKE)
				: kind == BeamClash.KIND_SOOT ? (random.nextInt(5) == 0 ? ParticleTypes.SQUID_INK : ParticleTypes.LARGE_SMOKE)
				: (share > 0.4 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE);
			level.addParticle(particle, p.x + random.nextGaussian() * r, p.y + random.nextGaussian() * r, p.z + random.nextGaussian() * r, v.x, v.y, v.z);
		}
		if (kind == BeamClash.KIND_WIND && player.tickCount % 3 == 0) {
			Vec3 p = from.add(dir.scale(random.nextDouble() * length));
			level.addParticle(ParticleTypes.SMALL_GUST, p.x, p.y, p.z, 0.0, 0.0, 0.0);
		}
	}

	/** The knot: each side's stream spiralling into it from its own side, sparks of pressure, and rings pulsing out. */
	private static void knot(ClientLevel level, RandomSource random, Player lead, @Nullable Entity other, BeamClash.State state) {
		Vec3 k = state.knot();
		Vec3 u = k.subtract(lead.getEyePosition());
		u = u.lengthSqr() < 1.0E-4 ? lead.getLookAngle() : u.normalize();
		Vec3 v = Math.abs(u.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
		v = v.subtract(u.scale(v.dot(u))).normalize();
		Vec3 w = u.cross(v);
		float age = state.age();
		float strain = 1.0F - Math.abs(state.balance());
		for (int side = 0; side < 2; side++) {
			int kind = side == 0 ? state.kind() : state.theirKind();
			double toward = side == 0 ? -1.0 : 1.0;
			for (int i = 0; i < KNOT_PUFFS; i++) {
				double a = age * KNOT_TURN * (side == 0 ? 1.0 : -1.0) + i * Mth.TWO_PI / KNOT_PUFFS + random.nextDouble() * 0.4;
				double r = KNOT_SIZE * (0.35 + random.nextDouble() * 0.65);
				Vec3 radial = v.scale(Math.cos(a)).add(w.scale(Math.sin(a)));
				Vec3 tangent = v.scale(-Math.sin(a)).add(w.scale(Math.cos(a))).scale(side == 0 ? 1.0 : -1.0);
				Vec3 p = k.add(radial.scale(r)).add(u.scale(toward * random.nextDouble() * 0.7));
				// round and in, toward the heart of it
				Vec3 vel = tangent.scale(0.18).add(radial.scale(-0.08)).add(u.scale(-toward * 0.05));
				ParticleOptions particle = kind == BeamClash.KIND_WIND ? (i % 3 == 0 ? ParticleTypes.CLOUD : ParticleTypes.WHITE_SMOKE)
					: kind == BeamClash.KIND_KI ? (i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK)
					: kind == BeamClash.KIND_SOOT ? (i % 3 == 0 ? ParticleTypes.SQUID_INK : ParticleTypes.LARGE_SMOKE)
					: (i % 2 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CAMPFIRE_COSY_SMOKE);
				if (particle == ParticleTypes.CAMPFIRE_COSY_SMOKE && random.nextInt(3) != 0) {
					particle = ParticleTypes.SMOKE;
				}
				level.addParticle(particle, p.x, p.y, p.z, vel.x, vel.y, vel.z);
			}
		}
		// sparks of pressure where they grind, more the more even it is
		int sparks = 1 + Math.round(3 * strain);
		for (int i = 0; i < sparks; i++) {
			Vec3 s = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian()).normalize().scale(0.35 + random.nextDouble() * 0.3);
			level.addParticle(random.nextBoolean() ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.CRIT, k.x, k.y, k.z, s.x, s.y, s.z);
		}
		if (random.nextInt(4) == 0) {
			level.addParticle(ParticleTypes.SMALL_GUST, k.x + random.nextGaussian() * 0.3, k.y + random.nextGaussian() * 0.3, k.z + random.nextGaussian() * 0.3, 0.0, 0.0, 0.0);
		}
		// a ring pulsing out, faster the harder they push together
		float pushing = Mth.clamp((state.push() + state.theirPush()) / (PUSH_FULL * 2.0F), 0.0F, 1.0F);
		int every = Math.max(RING_EVERY_MIN, Math.round(Mth.lerp(pushing, RING_EVERY, RING_EVERY_MIN)));
		if (state.age() % every == 0) {
			level.addParticle(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), k.x, k.y, k.z, 0.0, 0.0, 0.0);
			boolean smoky = state.kind() != BeamClash.KIND_WIND && state.theirKind() != BeamClash.KIND_WIND
				&& (state.kind() != BeamClash.KIND_KI || state.theirKind() != BeamClash.KIND_KI);
			for (int i = 0; i < RING_PUFFS; i++) {
				double a = i * Mth.TWO_PI / RING_PUFFS;
				Vec3 out = v.scale(Math.cos(a)).add(w.scale(Math.sin(a)));
				Vec3 p = k.add(out.scale(0.4));
				Vec3 vel = out.scale(RING_SPEED);
				level.addParticle(smoky && i % 2 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.CLOUD, p.x, p.y, p.z, vel.x, vel.y, vel.z);
			}
		}
		if (other == null && random.nextInt(2) == 0) {
			level.addParticle(ParticleTypes.POOF, k.x, k.y, k.z, 0.0, 0.0, 0.0);
		}
	}

	private static @Nullable KeyMapping key(Minecraft minecraft, String name) {
		KeyMapping found = KEYS.get(name);
		if (found == null) {
			for (KeyMapping mapping : minecraft.options.keyMappings) {
				if (name.equals(mapping.getName())) {
					found = mapping;
					KEYS.put(name, mapping);
					break;
				}
			}
		}
		return found;
	}

	/** Bottom right, above the chip row: who's winning, the time left, and both pushes. */
	private static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		BeamClash.State state = player == null ? null : player.getAttached(BeamClash.CLASH);
		if (state == null || BeamCinematic.running()) {
			// (0.15.143) in the film, the panel sits in the bottom bar (BeamCinematic)
			return;
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int x = width / 2 + 91 + 30;
		int chipY = height - CHIP - 1;
		if (x + PANEL_W > width - 2) {
			// no room right of the hotbar: over the chips moved above it
			x = width - 2 - PANEL_W;
			chipY = height - 22 - 30 - CHIP;
		}
		panel(graphics, delta, x, chipY - 17 - PANEL_H);
	}

	/** The panel itself, its top left at {@code x, y}. */
	static void panel(GuiGraphicsExtractor graphics, DeltaTracker delta, int x, int y) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		BeamClash.State state = player == null ? null : player.getAttached(BeamClash.CLASH);
		if (state == null) {
			return;
		}
		Font font = minecraft.font;
		int mine = COLORS[Math.clamp(state.kind(), 0, COLORS.length - 1)];
		int theirs = COLORS[Math.clamp(state.theirKind(), 0, COLORS.length - 1)];
		float time = state.age() + delta.getGameTimeDeltaPartialTick(false);
		boolean losing = state.balance() < -0.5F;
		int rim = losing ? pulse(0xFF5050, 0xFFFFFF, time * 0.5F) : 0xFFFFFF;
		graphics.fill(x, y, x + PANEL_W, y + PANEL_H, 0x90000000);
		graphics.outline(x, y, PANEL_W, PANEL_H, 0xC0000000 | rim);
		// the title, and the key to mash
		boolean ki = state.kind() == BeamClash.KIND_KI;
		KeyMapping mapping = key(minecraft, ki ? KI_KEY_NAME : KEY_NAME);
		String keyLabel = mapping == null ? (ki ? "V" : "X") : mapping.getTranslatedKeyMessage().getString();
		graphics.text(font, Component.translatable("hud.jaymospowers.beam_clash.title"), x + 3, y + 3, 0xFFFFFFFF);
		Component mash = Component.translatable("hud.jaymospowers.beam_clash.mash", keyLabel);
		int mashColor = (int)(time * 0.5F) % 2 == 0 ? 0xFFFFE070 : 0xFFFFFFFF;
		graphics.text(font, mash, x + PANEL_W - 3 - font.width(mash), y + 3, mashColor);
		// the struggle: yours from the left, theirs from the right, meeting at the knot
		int barX = x + 3, barY = y + 13, barW = PANEL_W - 6;
		int split = barX + Math.round(barW * (1.0F + state.balance()) * 0.5F);
		graphics.fill(barX, barY, split, barY + 4, 0xFF000000 | mine);
		graphics.fill(split, barY, barX + barW, barY + 4, 0xFF000000 | theirs);
		graphics.fill(split - 1, barY - 1, split + 1, barY + 5, 0xFFFFFFFF);
		// the pushes: yours under the left half, theirs under the right
		int half = barW / 2 - 2;
		float my = Mth.clamp(state.push() / PUSH_FULL, 0.0F, 1.0F), their = Mth.clamp(state.theirPush() / PUSH_FULL, 0.0F, 1.0F);
		graphics.fill(barX, barY + 6, barX + half, barY + 8, 0xFF303030);
		graphics.fill(barX, barY + 6, barX + Math.round(half * my), barY + 8, 0xFF000000 | mine);
		graphics.fill(barX + barW - half, barY + 6, barX + barW, barY + 8, 0xFF303030);
		graphics.fill(barX + barW - Math.round(half * their), barY + 6, barX + barW, barY + 8, 0xFF000000 | theirs);
		// time left before it bursts: a line along the top edge, shrinking
		float left = 1.0F - Mth.clamp(time / BeamClash.MAX_TICKS, 0.0F, 1.0F);
		graphics.fill(x, y, x + Math.round(PANEL_W * left), y + 1, left < 0.25F ? 0xFFFF8040 : 0xFFFFFFFF);
	}

	private static int pulse(int a, int b, float t) {
		float s = 0.5F + 0.5F * Mth.sin(t);
		int r = Math.round(Mth.lerp(s, a >> 16 & 0xFF, b >> 16 & 0xFF));
		int g = Math.round(Mth.lerp(s, a >> 8 & 0xFF, b >> 8 & 0xFF));
		int bl = Math.round(Mth.lerp(s, a & 0xFF, b & 0xFF));
		return r << 16 | g << 8 | bl;
	}

	/** The knot's roar and grind: following the knot, louder and higher the harder they push, fading when it ends. */
	private static final class KnotSound extends AbstractTickableSoundInstance {
		private static final float SWELL = 0.25F, FADE = 0.15F;
		private final Player lead;
		private final float loud, low;
		private float level;
		private int age;

		KnotSound(Player lead, SoundEvent sound, float loud, float low) {
			super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
			this.lead = lead;
			this.loud = loud;
			this.low = low;
			this.looping = true;
			this.delay = 0;
			this.volume = 0.01F;
			this.pitch = low;
			this.relative = false;
			this.x = lead.getX();
			this.y = lead.getY() + 1.0;
			this.z = lead.getZ();
		}

		@Override
		public void tick() {
			this.age++;
			BeamClash.State state = this.lead.isRemoved() ? null : this.lead.getAttached(BeamClash.CLASH);
			this.level = state != null ? Math.min(1.0F, this.level + SWELL) : this.level - FADE;
			if (this.level <= 0.0F) {
				this.stop();
				return;
			}
			if (state != null) {
				this.x = state.knot().x;
				this.y = state.knot().y;
				this.z = state.knot().z;
				float pushing = Mth.clamp((state.push() + state.theirPush()) / (PUSH_FULL * 2.0F), 0.0F, 1.0F);
				this.pitch = this.low + 0.25F * pushing + 0.05F * Mth.sin(this.age * 0.37F) + 0.15F * state.age() / BeamClash.MAX_TICKS;
			}
			this.volume = Math.max(0.01F, this.loud * this.level);
		}
	}
}
