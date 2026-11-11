package com.jaymoskingdoms.client.spirits;

import com.jaymoskingdoms.Fx;
import com.jaymoskingdoms.client.ManaClient;
import com.jaymoskingdoms.spirits.Poses;
import com.jaymoskingdoms.spirits.SpiritState;
import com.jaymoskingdoms.spirits.Spirits;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The spirit as a faint warrior that comes fully into being when it speaks. Andy (2026-11-08), after Johnny Silverhand in
 * Cyberpunk 2077: "exactly what i want for the visuals on spirits. a faint but visible warrior. They should also appear fully
 * manifested as you level up when they speak ... noone else other than the mana attuned can see them", then "other mana
 * attuned should see it", and "the first person view of the arms should be more like this".
 * <p>
 * Drawn in the world (not as a layer on its keeper, so the keeper sees it in first person and it is hidden by walls like
 * anything else), from the synced {@link SpiritState} alone: nothing new on the server. For every spirit that is out within
 * {@link #VIEW_RANGE}, seen only by a viewer who can see mana (the same gate as the arms, {@link SpiritArms#visibleToAttuned}):
 * <ul>
 * <li><b>Faint</b>: its figure over its keeper's back at {@link #FAINT}, a translucent body under the aura's shells so it reads
 * in daylight, flaring as its arms strike ({@link SpiritFigure#strength}). Not drawn for its own keeper in first person (it
 * would stand round the camera).</li>
 * <li><b>Manifested</b>: when it speaks ({@code SpiritVoice} stamps {@link Poses#SPEAK}) and when it levels up, it steps out:
 * standing on the ground {@link #STAGE_AHEAD} in front of its keeper and a little to one side, facing them, at about a
 * person's size, gesturing as it talks. It forms and goes with a glitch (it jolts sideways, drops out, splits into its colours)
 * and data rain falls through it. The higher its level, the more solid it is ({@link #solid}) and the longer it holds
 * ({@link #hold}); a level up holds longest, fully solid, arms flung up. It keeps its spot (as Johnny does) unless its keeper
 * walks off, then it glitches back in front of them.</li>
 * </ul>
 * The Quiet One never shows a body (Andy: "only animating the large arms for the quiet one"). The chat line still prints: it
 * survives distance and looking away; the figure is the line's body, not its replacement.
 */
public final class SpiritManifest {
	/** The figure's translucent body: the mana glow's own texture, tinted per spirit (its streaks read as the hologram's lines). */
	static final Identifier TEXTURE = ManaClient.TEXTURE;
	/** How far away a spirit is drawn at all (blocks). */
	static final double VIEW_RANGE = 48.0;
	/** The faint figure's light, as a share of the manifested one's, and its body's alpha. */
	static final float FAINT = 0.3F, FAINT_CORE_ALPHA = 0.14F;
	/** The manifested figure: its shells' light over the faint figure's numbers, and its body's alpha at full solidity. */
	static final float STAGE_LIGHT = 1.7F, CORE_ALPHA = 0.6F;
	/** How solid it is at level 1 (share of full; full at the top level), and the top level. */
	static final float SOLID_MIN = 0.5F;
	static final int TOP_LEVEL = 20;
	/** How long a spoken line keeps it manifested (ticks), and how much longer per level above the first; a level up's hold. */
	static final float SPEAK_HOLD = 70.0F, HOLD_PER_LEVEL = 4.0F, LEVEL_UP_HOLD = 110.0F;
	/** Ticks it takes to form, and to go. */
	static final float IN_TICKS = 6.0F, OUT_TICKS = 12.0F;
	/** Where it stands: blocks ahead of its keeper and off to one side, and how far its keeper may walk before it follows. */
	static final double STAGE_AHEAD = 2.3, STAGE_SIDE = 0.9, STAGE_LEAVE = 6.0;
	/** The glitch: how long it lasts round forming and going (ticks), the jolt (blocks), the colour split (blocks), and the chance of a stray blip each step. */
	static final float GLITCH_TICKS = 9.0F, JOLT = 0.07F, SPLIT = 0.045F, BLIP_CHANCE = 0.04F, DROPOUT = 0.3F;
	/** Ticks per glitch step (it jumps, it doesn't slide). */
	static final int GLITCH_STEP = 2;
	/** The colour split's two edges. */
	static final int SPLIT_A = 0xFF35F0FF, SPLIT_B = 0xFFFF3FC8;
	/** Data rain: motes per tick through a manifested figure, their fall speed, and the figure's height they fall from (blocks). */
	static final int RAIN_PER_TICK = 2;
	static final double RAIN_FALL = 0.14, RAIN_HEIGHT = 2.3, RAIN_WIDTH = 0.45;
	/** A level up's ring of light: motes, and how fast they run out. */
	static final int LEVEL_RING = 24;
	static final double LEVEL_RING_SPEED = 0.18;
	/** Self-lit: the ghost's light doesn't come from the world. */
	static final int FULL_BRIGHT = 0xF000F0;
	/** The draw order after the aura's and the arms'. */
	static final int ORDER = SpiritArms.ORDER + 6;

	/** Where each being's spirit last manifested, by entity id. */
	private static final Map<Integer, Stage> STAGES = new HashMap<>();
	/** The figure models, by kind and shell, baked when first needed. */
	private static final SpiritFigure.Model[][] MODELS = new SpiritFigure.Model[SpiritFigure.KINDS][SpiritFigure.SHELLS.length];

	private SpiritManifest() {
	}

	/** One being's manifestation: which line it is for, where it stands, when it began, how long it holds and how solid it is. */
	private static final class Stage {
		long show;
		int level = -1;
		double x, y, z;
		double start = -1.0E9;
		float hold, solid;
		double levelAt = -1.0E9;
		boolean placed;
		/** The line it is saying (its bubble), and when it came. */
		java.util.@org.jspecify.annotations.Nullable List<net.minecraft.util.FormattedCharSequence> words;
		double wordsAt = -1.0E9;
		float wordsFor;
	}

	public static void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(SpiritManifest::render);
		ClientTickEvents.END_CLIENT_TICK.register(SpiritManifest::tick);
		ClientReceiveMessageEvents.GAME.register(SpiritManifest::heard);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			STAGES.clear();
			clashStart = -1.0E9;
		});
	}

	/** How long a line keeps {@code spirit} manifested (ticks): longer as it grows. */
	static float hold(SpiritState spirit) {
		return SPEAK_HOLD + HOLD_PER_LEVEL * (Math.max(1, spirit.level()) - 1);
	}

	/** How solid a spirit of {@code level} is when manifested (0-1). */
	static float solid(int level) {
		return Mth.lerp(Mth.clamp((level - 1) / (float)(TOP_LEVEL - 1), 0.0F, 1.0F), SOLID_MIN, 1.0F);
	}

	/** Whether {@code spirit}'s figure is standing out in front now (its arms over its keeper's back are hidden meanwhile). */
	static boolean speaking(SpiritState spirit, double now) {
		if (spirit.kind() == Spirits.QUIET || spirit.show() == 0L || spirit.pose() != Poses.SPEAK) {
			return false;
		}
		double age = now - spirit.shownAt();
		return age >= 0.0 && age < hold(spirit) + OUT_TICKS;
	}

	private static float smooth(float k) {
		k = Mth.clamp(k, 0.0F, 1.0F);
		return k * k * (3.0F - 2.0F * k);
	}

	/** How far manifested a stage is at {@code now} (0-1). */
	private static float manifest(Stage stage, double now) {
		float a = (float)(now - stage.start);
		if (a < 0.0F || a > stage.hold + OUT_TICKS) {
			return 0.0F;
		}
		return Math.min(smooth(a / IN_TICKS), 1.0F - smooth((a - stage.hold) / OUT_TICKS));
	}

	/** Keeps a being's stage in step with its spirit: a new line or a level up manifests it, a long walk moves it. */
	private static Stage stage(LivingEntity entity, SpiritState spirit, double now) {
		Stage stage = STAGES.computeIfAbsent(entity.getId(), id -> new Stage());
		boolean up = false;
		if (stage.level < 0) {
			stage.level = spirit.level();
		} else if (spirit.level() > stage.level) {
			stage.level = spirit.level();
			stage.levelAt = now;
			up = true;
		}
		boolean line = spirit.pose() == Poses.SPEAK && spirit.show() != stage.show && now - spirit.shownAt() < hold(spirit);
		if (line) {
			stage.show = spirit.show();
		}
		if (up || line) {
			float hold = up ? LEVEL_UP_HOLD : hold(spirit);
			float solid = up ? 1.0F : solid(spirit.level());
			boolean already = manifest(stage, now) > 0.5F;
			if (already) {
				// still standing there from the last line: it goes on talking, no new entrance
				float left = stage.hold - (float)(now - stage.start);
				stage.hold = Math.max(left, hold) + (float)(now - stage.start);
				stage.solid = Math.max(stage.solid, solid);
			} else {
				stage.start = up ? now : Math.min(now, spirit.shownAt());
				stage.hold = hold;
				stage.solid = solid;
				place(stage, entity, spirit.show());
			}
			if (up) {
				flourish(entity, stage);
			}
		}
		if (stage.placed && manifest(stage, now) > 0.0F && entity.distanceToSqr(stage.x, stage.y, stage.z) > STAGE_LEAVE * STAGE_LEAVE) {
			// its keeper walked off: it blinks back in front of them, entering again
			place(stage, entity, stage.show + 1L);
			stage.start = now - IN_TICKS * 0.5F;
		}
		return stage;
	}

	/** Puts the stage in front of {@code entity}, to one side (picked by the line, so every viewer picks the same side). */
	private static void place(Stage stage, LivingEntity entity, long seed) {
		float yaw = entity.getYHeadRot() * Mth.DEG_TO_RAD;
		double side = ((seed >>> 6) & 1L) == 0L ? 1.0 : -1.0;
		// ahead is (-sin, cos); its right hand (-cos, -sin)
		stage.x = entity.getX() - Mth.sin(yaw) * STAGE_AHEAD - Mth.cos(yaw) * STAGE_SIDE * side;
		stage.z = entity.getZ() + Mth.cos(yaw) * STAGE_AHEAD - Mth.sin(yaw) * STAGE_SIDE * side;
		stage.y = entity.getY();
		stage.placed = true;
	}

	/** A level up's entrance: a ring of light and a chime where it stands (only for those who can see it). */
	private static void flourish(LivingEntity entity, Stage stage) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || !SpiritArms.visibleToAttuned(entity) || entity.getAttached(Spirits.SPIRIT) == null) {
			return;
		}
		if (stage.placed) {
			for (int n = 0; n < LEVEL_RING; n++) {
				double a = n * Mth.TWO_PI / LEVEL_RING;
				level.addParticle(ParticleTypes.END_ROD, stage.x, stage.y + 0.1, stage.z, Math.cos(a) * LEVEL_RING_SPEED, 0.04, Math.sin(a) * LEVEL_RING_SPEED);
			}
			level.playLocalSound(stage.x, stage.y + 1.0, stage.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8F, 0.7F, false);
		}
	}

	// ---- drawing -----------------------------------------------------------------------------------------------------------

	private static SpiritFigure.Model model(int kind, int shell) {
		if (MODELS[kind][shell] == null) {
			MODELS[kind][shell] = new SpiritFigure.Model(LayerDefinition.create(SpiritFigure.mesh(kind, SpiritFigure.SHELLS[shell]), 64, 64).bakeRoot(), kind);
		}
		return MODELS[kind][shell];
	}

	private static void render(LevelRenderContext context) {
		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.player == null) {
			return;
		}
		if (clashStart > -1.0E8) {
			drawClash(context, minecraft, level);
		}
		if (STAGES.isEmpty() && !anySpirit(level)) {
			return;
		}
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double now = level.getGameTime() + partial;
		Vec3 camera = context.levelState().cameraRenderState.pos;
		Entity cameraEntity = minecraft.getCameraEntity();
		boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
		for (Entity e : level.entitiesForRendering()) {
			if (!(e instanceof LivingEntity entity) || entity.distanceToSqr(camera) > VIEW_RANGE * VIEW_RANGE) {
				continue;
			}
			float grue = grueBody(entity);
			if (grue > 0.0F) {
				drawGrueBody(context, entity, grue, partial, camera, now);
				continue;
			}
			SpiritState spirit = entity.getAttached(Spirits.SPIRIT);
			if (spirit == null || spirit.kind() < 0 || spirit.kind() > Spirits.QUIET) {
				continue;
			}
			if (spirit.kind() == Spirits.QUIET) {
				// (the Quiet One: no body, ever; in the trial its arms alone fight the Grue)
				LivingEntity boss = duelBoss(entity);
				if (boss != null) {
					drawDuel(context, entity, spirit, boss, partial, camera, now);
				}
				continue;
			}
			Stage stage = stage(entity, spirit, now);
			LivingEntity boss = duelBoss(entity);
			if (boss != null) {
				drawDuel(context, entity, spirit, boss, partial, camera, now);
				continue;
			}
			if (!spirit.out() || entity.isInvisible() || !SpiritArms.visibleToAttuned(entity)) {
				continue;
			}
			float m = stage.placed ? manifest(stage, now) : 0.0F;
			float age = (float)(now - spirit.shownAt());
			float flare = spirit.show() != 0L && spirit.pose() > Poses.IDLE && spirit.pose() != Poses.SPEAK ? SpiritFigure.strength(age) : 0.0F;
			// it shows with its arms, and goes with them (Andy: "the arms stay attached as long as they are visible")
			float shown = SpiritArms.presence(spirit, now, entity);
			float faint = (FAINT + (1.0F - FAINT) * flare) * (1.0F - m) * shown;
			if (faint > 0.01F && !(entity == cameraEntity && firstPerson)) {
				drawFaint(context, entity, spirit, partial, camera, faint, now);
			}
			if (m > 0.01F) {
				drawStage(context, entity, spirit, stage, partial, camera, m, now);
			}
		}
	}

	private static boolean anySpirit(ClientLevel level) {
		for (Entity e : level.entitiesForRendering()) {
			if (e instanceof LivingEntity living && (living.getAttached(Spirits.SPIRIT) != null || grueBody(living) > 0.0F)) {
				return true;
			}
		}
		return false;
	}

	/** The faint figure over its keeper's back, placed as the living renderer places a body (so its arms' layer meets it). */
	private static void drawFaint(LevelRenderContext context, LivingEntity entity, SpiritState spirit, float partial, Vec3 camera, float light, double now) {
		PoseStack pose = context.poseStack();
		Vec3 at = entity.getPosition(partial).subtract(camera);
		float bodyRot = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
		float headRot = Mth.rotLerp(partial, entity.yHeadRotO, entity.yHeadRot);
		pose.pushPose();
		pose.translate(at.x, at.y, at.z);
		pose.scale(entity.getScale(), entity.getScale(), entity.getScale());
		pose.rotateDegrees(Axis.YP, 180.0F - bodyRot);
		pose.scale(-1.0F, -1.0F, 1.0F);
		pose.scale(SpiritArms.PLAYER_SCALE, SpiritArms.PLAYER_SCALE, SpiritArms.PLAYER_SCALE);
		pose.translate(0.0F, SpiritArms.MODEL_DROP, 0.0F);
		int kind = spirit.kind();
		SpiritFigure.Frame frame = new SpiritFigure.Frame(kind, false, SpiritArms.style(kind, spirit.scale()), Spirits.of(kind).size(spirit), 1.0F, 0.0F, -1.0F,
			Mth.wrapDegrees(headRot - bodyRot) * Mth.DEG_TO_RAD, entity.getXRot(partial) * Mth.DEG_TO_RAD, false);
		int color = GrueClient.tint(entity, SpiritArms.mix(Spirits.of(kind).color()));
		submit(context.submitNodeCollector(), pose, entity, frame, kind, light, FAINT_CORE_ALPHA * light / FAINT, now, 0.0F, color, SpiritArms.ghost(color));
		pose.popPose();
	}

	/** The manifested figure standing on the ground in front of its keeper, facing them. */
	private static void drawStage(LevelRenderContext context, LivingEntity entity, SpiritState spirit, Stage stage, float partial, Vec3 camera, float m, double now) {
		int kind = spirit.kind();
		float a = (float)(now - stage.start);
		// the glitch: hard round forming and going, and the odd stray blip while it talks
		long step = (long)Math.floor(now / GLITCH_STEP);
		float glitch = Math.max(1.0F - a / GLITCH_TICKS, 0.0F);
		glitch = Math.max(glitch, 1.0F - (stage.hold + OUT_TICKS - a) / GLITCH_TICKS);
		if (noise(step, entity.getId(), 1) < BLIP_CHANCE) {
			glitch = Math.max(glitch, 0.6F);
		}
		glitch = Mth.clamp(glitch, 0.0F, 1.0F);
		float jolt = (noise(step, entity.getId(), 2) - 0.5F) * 2.0F * JOLT * glitch;
		float light = m * stage.solid;
		if (glitch > 0.5F && noise(step, entity.getId(), 3) < DROPOUT) {
			light *= 0.25F;
		}
		// it faces its keeper, its head turned to their eyes
		Vec3 eye = entity.getEyePosition(partial);
		double dx = eye.x - stage.x, dz = eye.z - stage.z;
		float yaw = (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
		double headY = stage.y + SpiritFigure.FOOT[kind] * SpiritFigure.STAGE[kind] * SpiritArms.PX;
		float pitch = (float)-Mth.atan2(eye.y - headY, Math.sqrt(dx * dx + dz * dz));
		float levelAge = (float)(now - stage.levelAt);
		boolean levelling = levelAge >= 0.0F && levelAge < LEVEL_UP_HOLD;
		float talk = spirit.pose() == Poses.SPEAK && now - spirit.shownAt() < hold(spirit) ? 1.0F : levelling ? 0.0F : 0.4F;
		if (levelling) {
			talk *= 1.0F - SpiritFigure.levelRaise(levelAge);
		}
		boolean fading = a > stage.hold;
		SpiritFigure.Frame frame = new SpiritFigure.Frame(kind, true, kind, Spirits.of(kind).size(spirit), m, talk * m, levelling ? levelAge : -1.0F, 0.0F,
			pitch, fading);
		PoseStack pose = context.poseStack();
		pose.pushPose();
		pose.translate(stage.x - camera.x, stage.y - camera.y, stage.z - camera.z);
		pose.rotateDegrees(Axis.YP, 180.0F - yaw);
		pose.translate(jolt, 0.0F, 0.0F);
		pose.scale(-1.0F, -1.0F, 1.0F);
		pose.scale(SpiritArms.PLAYER_SCALE, SpiritArms.PLAYER_SCALE, SpiritArms.PLAYER_SCALE);
		pose.translate(0.0F, SpiritArms.MODEL_DROP, 0.0F);
		int color = GrueClient.tint(entity, SpiritArms.mix(Spirits.of(kind).color()));
		submit(context.submitNodeCollector(), pose, entity, frame, kind, light * STAGE_LIGHT, CORE_ALPHA * light, now, glitch, color, SpiritArms.ghost(color));
		pose.popPose();
		bubble(context, stage, kind, camera, m, now, color);
	}

	/** One figure: its translucent body, then the aura's shells over it, and while it glitches, its colours split either side. */
	private static void submit(SubmitNodeCollector collector, PoseStack pose, LivingEntity entity, SpiritFigure.Frame frame, int kind, float light, float coreAlpha,
		double now, float glitch, int color, int pale) {
		HumanoidRenderState state = new HumanoidRenderState();
		state.ageInTicks = (float)(now % 100000.0) + (entity.getId() & 63);
		state.setData(SpiritFigure.FRAME, frame);
		if (coreAlpha > 0.01F) {
			int alpha = (int)(Mth.clamp(coreAlpha, 0.0F, 1.0F) * 255.0F);
			collector.order(ORDER).submitModel(model(kind, 0), state, pose, RenderTypes.entityTranslucent(TEXTURE), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
				ARGB.color(alpha, ARGB.red(pale), ARGB.green(pale), ARGB.blue(pale)), null, 0);
		}
		float rise = (state.ageInTicks * ManaClient.RISE * SpiritArms.ARM_FLOW) % 1.0F;
		float pulse = SpiritFigure.pulse(state.ageInTicks);
		for (int i = 0; i < SpiritFigure.SHELLS.length; i++) {
			float l = Mth.clamp(SpiritFigure.SHELL_LIGHT[i] * pulse * light, 0.0F, 1.0F);
			collector.order(ORDER + 1 + i).submitModel(model(kind, i), state, pose,
				RenderTypes.energySwirl(ManaClient.TEXTURE, i * 0.37F + 0.21F, -rise * (1 + i)), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
				ManaClient.tint(i == 0 ? pale : color, l), null, 0);
		}
		if (glitch > 0.05F) {
			int outer = SpiritFigure.SHELLS.length - 1;
			for (int s = -1; s <= 1; s += 2) {
				pose.pushPose();
				pose.translate(s * SPLIT * glitch / SpiritArms.PLAYER_SCALE, 0.0F, 0.0F);
				collector.order(ORDER + 1 + SpiritFigure.SHELLS.length).submitModel(model(kind, outer), state, pose,
					RenderTypes.energySwirl(ManaClient.TEXTURE, 0.5F, -rise * 3.0F), FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
					ManaClient.tint(s < 0 ? SPLIT_A : SPLIT_B, 0.5F * glitch * Math.max(light, 0.4F)), null, 0);
				pose.popPose();
			}
		}
	}

	/** A steady hash in 0-1 for one glitch step of one being (every viewer's figure jumps the same way). */
	private static float noise(long step, int id, int salt) {
		long h = step * 0x9E3779B97F4A7C15L + id * 0xC2B2AE3D27D4EB4FL + salt * 0x165667B19E3779F9L;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		return (h & 0xFFFFFF) / (float)0x1000000;
	}

	// ---- the rain, and forgetting ------------------------------------------------------------------------------------------

	/** Each tick: data rain through every manifested figure in view, and stages forgotten for beings that are gone. */
	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null || minecraft.isPaused()) {
			return;
		}
		double now = level.getGameTime();
		clashTick(minecraft, level, now);
		duelTick(minecraft, level, now);
		if (STAGES.isEmpty()) {
			return;
		}
		if (level.getGameTime() % 100L == 0L) {
			STAGES.keySet().removeIf(id -> !(level.getEntity(id) instanceof LivingEntity living) || living.getAttached(Spirits.SPIRIT) == null);
		}
		for (Map.Entry<Integer, Stage> entry : STAGES.entrySet()) {
			Stage stage = entry.getValue();
			if (!stage.placed || manifest(stage, now) < 0.3F || !(level.getEntity(entry.getKey()) instanceof LivingEntity entity)) {
				continue;
			}
			SpiritState spirit = entity.getAttached(Spirits.SPIRIT);
			if (spirit == null || !spirit.out() || !SpiritArms.visibleToAttuned(entity)) {
				continue;
			}
			int color = SpiritArms.ghost(SpiritArms.mix(Spirits.of(spirit.kind()).color()));
			ParticleOptions mote = Fx.wisp(GrueClient.tint(entity, color) & 0xFFFFFF, 0.5F);
			var random = entity.getRandom();
			for (int n = 0; n < RAIN_PER_TICK; n++) {
				level.addParticle(mote, stage.x + (random.nextDouble() - 0.5) * 2.0 * RAIN_WIDTH, stage.y + random.nextDouble() * RAIN_HEIGHT,
					stage.z + (random.nextDouble() - 0.5) * 2.0 * RAIN_WIDTH, 0.0, -RAIN_FALL, 0.0);
			}
		}
	}

	// ---- the bubble -------------------------------------------------------------------------------------------------------

	/** The bubble over a speaking spirit: text size (blocks per font pixel), wrap width (font pixels), its gap over the head (blocks), how long it stays (ticks: a base and per letter), and its background. */
	static final float BUBBLE_SCALE = 0.022F;
	static final int BUBBLE_WRAP = 160, BUBBLE_BACK_ALPHA = 0xA0;
	static final double BUBBLE_GAP = 0.35, HEAD_PX = 11.0;
	static final float BUBBLE_TICKS = 50.0F, BUBBLE_PER_CHAR = 1.2F, BUBBLE_MAX = 160.0F;

	/**
	 * A chat line from the keeper's own spirit ({@code SpiritVoice}: its name, ": ", the line) is caught here and shown in a bubble
	 * over its head while it stands out (Andy: "it should also show a chat bubble above the spirit when they speak"). The line
	 * still prints in chat. Only its keeper is sent the line, so only they see the bubble.
	 */
	private static void heard(net.minecraft.network.chat.Component message, boolean overlay) {
		Minecraft minecraft = Minecraft.getInstance();
		if (overlay || minecraft.player == null || minecraft.level == null || message.getSiblings().size() < 3) {
			return;
		}
		if (!(message.getSiblings().get(0).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents name)
			|| !name.getKey().startsWith("spirit.jaymoskingdoms.") || !name.getKey().endsWith(".name")) {
			return;
		}
		net.minecraft.network.chat.Component line = message.getSiblings().get(2);
		Stage stage = STAGES.computeIfAbsent(minecraft.player.getId(), id -> new Stage());
		stage.words = minecraft.font.split(line, BUBBLE_WRAP);
		stage.wordsAt = minecraft.level.getGameTime();
		stage.wordsFor = Math.min(BUBBLE_MAX, BUBBLE_TICKS + BUBBLE_PER_CHAR * line.getString().length());
	}

	private static void bubble(LevelRenderContext context, Stage stage, int kind, Vec3 camera, float m, double now, int color) {
		if (stage.words == null) {
			return;
		}
		float a = (float)(now - stage.wordsAt);
		if (a < 0.0F || a > stage.wordsFor) {
			if (a > stage.wordsFor) {
				stage.words = null;
			}
			return;
		}
		float alpha = m * Math.min(1.0F, Math.min(a / 4.0F, (stage.wordsFor - a) / 10.0F));
		if (alpha < 0.05F) {
			return;
		}
		var font = Minecraft.getInstance().font;
		PoseStack pose = context.poseStack();
		double top = (SpiritFigure.FOOT[kind] + HEAD_PX) * SpiritFigure.STAGE[kind] * SpiritArms.PX + SpiritFigure.HOVER * SpiritArms.PX + BUBBLE_GAP;
		int n = stage.words.size();
		pose.pushPose();
		pose.translate(stage.x - camera.x, stage.y + top - camera.y, stage.z - camera.z);
		pose.rotate(context.levelState().cameraRenderState.orientation);
		pose.scale(BUBBLE_SCALE, -BUBBLE_SCALE, BUBBLE_SCALE);
		int text = ARGB.color((int)(alpha * 255.0F), SpiritArms.ghost(color));
		int back = ARGB.color((int)(alpha * BUBBLE_BACK_ALPHA), 0x0A0A14);
		for (int i = 0; i < n; i++) {
			var row = stage.words.get(i);
			float x = -font.width(row) / 2.0F;
			float y = (i - n + 1) * (font.lineHeight + 1);
			context.submitNodeCollector().submitText(pose, x, y, row, false, net.minecraft.client.gui.Font.DisplayMode.NORMAL, FULL_BRIGHT, text, back, 0);
		}
		pose.popPose();
	}

	// ---- the duel ---------------------------------------------------------------------------------------------------------

	/**
	 * In the Grue's trial the spirit leaves its keeper and fights the Grue on its own (Andy: "watch their animated battle. each
	 * spirit using their unique attacks and dodging"), while its keeper fights the shades. It stands between the Grue and you,
	 * facing it, and fights in its own way: Grimsby fast cuts, left and right; Varro slow two-handed blows; Ambrose a
	 * two-palmed shove; Thessaly quick flicks from further off, nimble; the Quiet One its arms alone, echoing. Now and then it
	 * dodges aside (a glitch, a sidestep) and the Grue's claws go through where it stood. By kind: ticks per blow, the left
	 * arm's lag behind the right (0: both together), how far from the Grue it stands, ticks between dodges.
	 */
	static final float[] DUEL_BLOW = {9.0F, 22.0F, 16.0F, 11.0F, 12.0F};
	static final float[] DUEL_LAG = {0.5F, 0.0F, 0.0F, 0.5F, 0.5F};
	static final double[] DUEL_GAP = {1.5, 1.8, 1.6, 2.3, 1.6};
	static final float[] DODGE_EVERY = {50.0F, 95.0F, 60.0F, 34.0F, 45.0F};
	/** A dodge: how long (ticks) and how far aside (blocks); how far off its keeper a Grue body counts as theirs to fight (blocks). */
	static final float DODGE_TICKS = 10.0F;
	static final double DODGE_SIDE = 1.0, DUEL_RANGE = 40.0;

	private static int duelBlows;
	private static @org.jspecify.annotations.Nullable LivingEntity duelCache;
	private static long duelCacheAt = -1L;

	/** The Grue's body {@code keeper}'s spirit is fighting now: only for the local player, in its trial. */
	static @org.jspecify.annotations.Nullable LivingEntity duelBoss(LivingEntity keeper) {
		Minecraft minecraft = Minecraft.getInstance();
		if (keeper != minecraft.player || minecraft.level == null) {
			return null;
		}
		long now = minecraft.level.getGameTime();
		if (now != duelCacheAt) {
			duelCacheAt = now;
			duelCache = null;
			for (LivingEntity e : minecraft.level.getEntitiesOfClass(LivingEntity.class, keeper.getBoundingBox().inflate(DUEL_RANGE),
				e -> e.isAlive() && grueBody(e) >= 1.0F)) {
				duelCache = e;
				break;
			}
		}
		return keeper.getAttached(Spirits.SPIRIT) == null ? null : duelCache;
	}

	/** Whether {@code entity}'s spirit is away fighting the Grue (its arms don't show on it meanwhile). */
	static boolean duelling(LivingEntity entity) {
		return duelBoss(entity) != null;
	}

	private static void drawDuel(LevelRenderContext context, LivingEntity keeper, SpiritState spirit, LivingEntity boss, float partial, Vec3 camera, double now) {
		int kind = Math.clamp(spirit.kind(), 0, Spirits.QUIET);
		Vec3 g = boss.getPosition(partial);
		Vec3 k = keeper.getPosition(partial);
		Vec3 toward = new Vec3(k.x - g.x, 0.0, k.z - g.z);
		toward = toward.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : toward.normalize();
		Vec3 aside = new Vec3(-toward.z, 0.0, toward.x);
		// the dodge: aside and back, glitching, at its own rhythm
		float t = (float)now;
		float since = t % DODGE_EVERY[kind];
		float dodge = since < DODGE_TICKS ? Mth.sin(since / DODGE_TICKS * Mth.PI) : 0.0F;
		double side = ((long)(t / DODGE_EVERY[kind]) & 1L) == 0L ? 1.0 : -1.0;
		Vec3 at = g.add(toward.scale(DUEL_GAP[kind])).add(aside.scale(DODGE_SIDE * dodge * side));
		float glitch = Math.max(dodge, noise((long)Math.floor(now / GLITCH_STEP), keeper.getId(), 9) < BLIP_CHANCE ? 0.6F : 0.0F);
		// while it dodges it doesn't strike
		float strike = 1.0F - dodge;
		float levelSolid = solid(spirit.level());
		int color = GrueClient.tint(keeper, SpiritArms.mix(Spirits.of(kind).color()));
		SpiritFigure.Frame frame = new SpiritFigure.Frame(kind, true, kind, Spirits.of(kind).size(spirit), 1.0F, 0.0F, -1.0F, 0.0F, 0.0F, false,
			strike, t / DUEL_BLOW[kind], 0.0F, kind == Spirits.QUIET, DUEL_LAG[kind]);
		figureAt(context, camera, keeper, frame, kind, at.x, at.y, at.z, g.x, g.z, 0.0F, levelSolid * STAGE_LIGHT, CORE_ALPHA * levelSolid, now, glitch, color,
			SpiritArms.ghost(color));
	}

	/** Each tick of the duel: a crack at each of the spirit's blows (not while it dodges). */
	private static void duelTick(Minecraft minecraft, ClientLevel level, double now) {
		var player = minecraft.player;
		LivingEntity boss = player == null ? null : duelBoss(player);
		SpiritState spirit = player == null ? null : player.getAttached(Spirits.SPIRIT);
		if (boss == null || spirit == null) {
			return;
		}
		int kind = Math.clamp(spirit.kind(), 0, Spirits.QUIET);
		float since = (float)now % DODGE_EVERY[kind];
		int blows = (int)Math.floor(now / DUEL_BLOW[kind] - SpiritFigure.STRIKE_DRAW);
		if (blows != duelBlows) {
			duelBlows = blows;
			if (since >= DODGE_TICKS) {
				level.playLocalSound(boss.getX(), boss.getY() + 1.4, boss.getZ(), kind == Spirits.VARRO ? SoundEvents.WARDEN_ATTACK_IMPACT : SoundEvents.PLAYER_ATTACK_STRONG,
					SoundSource.PLAYERS, 0.5F, kind == Spirits.VARRO ? 0.8F : 1.3F, false);
				ParticleOptions mote = Fx.wisp(SpiritArms.mix(Spirits.of(kind).color()) & 0xFFFFFF, 1.1F);
				for (int n = 0; n < 6; n++) {
					level.addParticle(mote, boss.getX(), boss.getY() + 1.4, boss.getZ(), (player.getRandom().nextDouble() - 0.5) * 0.3, 0.05,
						(player.getRandom().nextDouble() - 0.5) * 0.3);
				}
			} else {
				level.playLocalSound(boss.getX(), boss.getY() + 1.4, boss.getZ(), SoundEvents.PHANTOM_FLAP, SoundSource.PLAYERS, 0.5F, 1.4F, false);
			}
		}
	}

	// ---- the Grue -----------------------------------------------------------------------------------------------------------

	/**
	 * The Grue fights the spirit (Andy: "they should all have an effect when the grue is attacking, maybe an animated fight"):
	 * each time it lashes at its keeper ({@code Grue.Lash}, which only the keeper is sent), the two appear in front of them and
	 * trade blows for {@link #CLASH_TICKS}: the spirit (the Quiet One only its arms) against the Grue, ghastly, winged and
	 * hovering on its wingbeats, both glitching, a crack at every blow. The stronger the lash, the redder the spirit and the
	 * harder the Grue presses (it drives the spirit back); at the end it breaks off and both fade.
	 */
	static final float CLASH_TICKS = 56.0F;
	/** Ticks per blow, how far ahead of the keeper the fight is, how far apart the two stand, and how high the Grue hovers (blocks). */
	static final float CLASH_BLOW = 14.0F;
	/** Ticks per pair of blows when the Grue's body is locked in a barrage (its fists a blur, as the spirit's are). */
	static final float BARRAGE_BLOW = 5.0F;
	static final double CLASH_AHEAD = 2.8, CLASH_APART = 0.95, CLASH_HOVER = 0.35, CLASH_PUSH = 0.6;
	/** The Grue's colours: its glowing edge, and its body, dark and see-through (ghastly). */
	static final int GRUE_EDGE = 0xFFE0142E, GRUE_CORE = 0xFF2A0006;
	static final float GRUE_CORE_ALPHA = 0.62F, GRUE_LIGHT = 1.5F;
	/** The boss's wingbeat, its shades' size against its own, and how close the keeper must be for it to strike at them (blocks). */
	static final float BOSS_FLAP = 1.0F, CLASH_FLAP = 1.7F, SHADE_SCALE = 0.42F;
	static final double BOSS_STRIKE_RANGE = 4.5;
	/** Lang keys the Grue's bodies are named by (GrueTrial names them; names are synced, tags are not). */
	static final String BOSS_NAME = "spirit.jaymoskingdoms.grue.name", SHADE_NAME = "entity.jaymoskingdoms.grue_shade";

	private static double clashStart = -1.0E9;
	private static float clashStrength;
	private static double clashX, clashY, clashZ, clashYaw;
	private static int clashBlows;

	/** The Grue lashed at the local player: its fight with their spirit plays out in front of them. */
	public static void lash(float strength) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player == null || minecraft.level == null || minecraft.player.getAttached(Spirits.SPIRIT) == null) {
			return;
		}
		var player = minecraft.player;
		if (duelling(player)) {
			// (in its trial the spirit is already at the Grue's throat)
			return;
		}
		double now = minecraft.level.getGameTime();
		if (now - clashStart < CLASH_TICKS * 0.6) {
			// already fighting: it only gets harder
			clashStrength = Math.max(clashStrength, strength);
			return;
		}
		float yaw = player.getYHeadRot() * Mth.DEG_TO_RAD;
		clashX = player.getX() - Mth.sin(yaw) * CLASH_AHEAD;
		clashZ = player.getZ() + Mth.cos(yaw) * CLASH_AHEAD;
		clashY = player.getY();
		clashYaw = yaw;
		clashStart = now;
		clashStrength = strength;
		clashBlows = 0;
	}

	/** Which of the Grue's bodies {@code entity} is: its scale (1 the boss, less for a shade), or 0 for none. */
	static float grueBody(LivingEntity entity) {
		var name = entity.getCustomName();
		if (name != null && name.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t) {
			if (BOSS_NAME.equals(t.getKey())) {
				return 1.0F;
			}
			if (SHADE_NAME.equals(t.getKey())) {
				return SHADE_SCALE;
			}
		}
		return 0.0F;
	}

	private static void drawClash(LevelRenderContext context, Minecraft minecraft, ClientLevel level) {
		var player = minecraft.player;
		float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double now = level.getGameTime() + partial;
		float a = (float)(now - clashStart);
		SpiritState spirit = player.getAttached(Spirits.SPIRIT);
		if (a > CLASH_TICKS || spirit == null) {
			if (a > CLASH_TICKS) {
				clashStart = -1.0E9;
			}
			return;
		}
		Vec3 camera = context.levelState().cameraRenderState.pos;
		float m = Math.min(smooth(a / IN_TICKS), 1.0F - smooth((a - (CLASH_TICKS - OUT_TICKS)) / OUT_TICKS));
		long step = (long)Math.floor(now / GLITCH_STEP);
		float glitch = Math.max(Math.max(1.0F - a / GLITCH_TICKS, 1.0F - (CLASH_TICKS - a) / GLITCH_TICKS), noise(step, 7, 4) < 0.25F ? 0.7F : 0.0F);
		float phase = a / CLASH_BLOW;
		// the Grue presses as hard as it lashed: the spirit is driven back toward its keeper
		float push = (float)CLASH_PUSH * clashStrength * smooth(a / CLASH_TICKS);
		double rx = -Mth.cos((float)clashYaw), rz = -Mth.sin((float)clashYaw);
		double fx = -Mth.sin((float)clashYaw), fz = Mth.cos((float)clashYaw);
		double sx = clashX - rx * CLASH_APART - fx * push, sz = clashZ - rz * CLASH_APART - fz * push;
		double gx = clashX + rx * CLASH_APART - fx * push * 0.5, gz = clashZ + rz * CLASH_APART - fz * push * 0.5;
		int kind = spirit.kind();
		float levelSolid = solid(spirit.level());
		// the spirit: its colours going to the Grue's red as the lash is stronger
		int color = ARGB.srgbLerp(clashStrength * 0.5F, GrueClient.tint(player, SpiritArms.mix(Spirits.of(kind).color())), GRUE_EDGE);
		SpiritFigure.Frame you = new SpiritFigure.Frame(kind, true, kind, Spirits.of(kind).size(spirit), m, 0.0F, -1.0F, 0.0F, 0.0F, false,
			1.0F, phase, 0.0F, kind == Spirits.QUIET);
		figureAt(context, camera, player, you, kind, sx, clashY, sz, gx, gz, 0.0F, m * levelSolid * STAGE_LIGHT, CORE_ALPHA * m * levelSolid, now, glitch, color,
			SpiritArms.ghost(color));
		// the Grue: half a blow out of step, so they trade
		SpiritFigure.Frame it = new SpiritFigure.Frame(SpiritFigure.GRUE, true, SpiritFigure.GRUE, 1.0F, m, 0.0F, -1.0F, 0.0F, 0.0F, false,
			1.0F, phase + 0.5F, CLASH_FLAP, false);
		figureAt(context, camera, player, it, SpiritFigure.GRUE, gx, clashY + CLASH_HOVER, gz, sx, sz, 0.0F, m * GRUE_LIGHT, GRUE_CORE_ALPHA * m, now,
			glitch, GRUE_EDGE, GRUE_CORE);
	}

	/** Each tick of the fight: a crack at every blow, and red motes thrown off. */
	private static void clashTick(Minecraft minecraft, ClientLevel level, double now) {
		if (clashStart < -1.0E8 || minecraft.player == null) {
			return;
		}
		float a = (float)(now - clashStart);
		if (a > CLASH_TICKS) {
			return;
		}
		// a blow lands at each half swing (one side's, then the other's), just after its draw
		int blows = (int)Math.floor(a / CLASH_BLOW * 2.0F - SpiritFigure.STRIKE_DRAW * 2.0F + 1.0F);
		if (blows > clashBlows && a > IN_TICKS && a < CLASH_TICKS - OUT_TICKS) {
			clashBlows = blows;
			boolean grueHits = (blows & 1) == 0;
			level.playLocalSound(clashX, clashY + 1.2, clashZ, grueHits ? SoundEvents.WARDEN_ATTACK_IMPACT : SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS,
				0.6F, grueHits ? 0.6F : 1.2F, false);
			level.playLocalSound(clashX, clashY + 1.2, clashZ, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.4F, grueHits ? 0.5F : 1.5F, false);
			ParticleOptions red = Fx.wisp(GRUE_EDGE & 0xFFFFFF, 1.2F);
			var random = minecraft.player.getRandom();
			for (int n = 0; n < 12; n++) {
				level.addParticle(red, clashX, clashY + 1.3, clashZ, (random.nextDouble() - 0.5) * 0.4, (random.nextDouble() - 0.3) * 0.3,
					(random.nextDouble() - 0.5) * 0.4);
				if (n % 3 == 0) {
					level.addParticle(ParticleTypes.CRIT, clashX, clashY + 1.3, clashZ, (random.nextDouble() - 0.5) * 0.6, random.nextDouble() * 0.3,
						(random.nextDouble() - 0.5) * 0.6);
				}
			}
		}
	}

	/** The boss (or a shade) drawn as the Grue: hovering on its wings, striking at the keeper when they're close, glitching now and then. */
	private static void drawGrueBody(LevelRenderContext context, LivingEntity entity, float scale, float partial, Vec3 camera, double now) {
		Minecraft minecraft = Minecraft.getInstance();
		var viewer = minecraft.player;
		if (viewer == null) {
			return;
		}
		Vec3 at = entity.getPosition(partial);
		float bodyRot = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot);
		float near = (float)Mth.clamp(1.0 - (entity.distanceTo(viewer) - BOSS_STRIKE_RANGE) / 2.0, 0.0, 1.0);
		long step = (long)Math.floor(now / GLITCH_STEP);
		float glitch = noise(step, entity.getId(), 5) < BLIP_CHANCE * 2.0F ? 0.7F : entity.hurtTime > 0 ? 0.5F : 0.0F;
		float headPitch = entity.getXRot(partial) * Mth.DEG_TO_RAD;
		// its finale: locked fist to fist with the keeper's spirit in the barrage, it pounds as fast as they do
		var barrage = entity.getAttached(com.jaymoskingdoms.spirits.SpiritBarrage.BARRAGE);
		boolean pounding = barrage != null && !barrage.over();
		if (viewer.getAttached(Spirits.SPIRIT) != null && duelBoss(viewer) == entity) {
			// locked with the spirit: always at it
			near = 1.0F;
		}
		if (pounding) {
			near = 1.0F;
			glitch = Math.max(glitch, noise(step, entity.getId(), 8) < 0.3F ? 0.5F : 0.0F);
		}
		SpiritFigure.Frame frame = new SpiritFigure.Frame(SpiritFigure.GRUE, true, SpiritFigure.GRUE, 1.0F, 1.0F, 0.0F, -1.0F,
			Mth.wrapDegrees(Mth.rotLerp(partial, entity.yHeadRotO, entity.yHeadRot) - bodyRot) * Mth.DEG_TO_RAD, headPitch, false,
			near, (float)(now / (pounding ? BARRAGE_BLOW : CLASH_BLOW)), BOSS_FLAP / Math.max(scale, 0.3F) * 0.6F + 0.4F, false);
		PoseStack pose = context.poseStack();
		pose.pushPose();
		pose.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
		pose.scale(scale, scale, scale);
		pose.rotateDegrees(Axis.YP, 180.0F - bodyRot);
		pose.translate((noise(step, entity.getId(), 6) - 0.5F) * 2.0F * JOLT * glitch, 0.0F, 0.0F);
		pose.scale(-1.0F, -1.0F, 1.0F);
		pose.scale(SpiritArms.PLAYER_SCALE, SpiritArms.PLAYER_SCALE, SpiritArms.PLAYER_SCALE);
		pose.translate(0.0F, SpiritArms.MODEL_DROP, 0.0F);
		submit(context.submitNodeCollector(), pose, entity, frame, SpiritFigure.GRUE, GRUE_LIGHT, GRUE_CORE_ALPHA, now, glitch, GRUE_EDGE, GRUE_CORE);
		pose.popPose();
	}

	/** One staged figure at ({@code x}, {@code y}, {@code z}), facing ({@code lookX}, {@code lookZ}). */
	private static void figureAt(LevelRenderContext context, Vec3 camera, LivingEntity owner, SpiritFigure.Frame frame, int kind, double x, double y, double z,
		double lookX, double lookZ, float extraYaw, float light, float coreAlpha, double now, float glitch, int color, int pale) {
		float yaw = (float)(Mth.atan2(lookZ - z, lookX - x) * Mth.RAD_TO_DEG) - 90.0F + extraYaw;
		long step = (long)Math.floor(now / GLITCH_STEP);
		PoseStack pose = context.poseStack();
		pose.pushPose();
		pose.translate(x - camera.x, y - camera.y, z - camera.z);
		pose.rotateDegrees(Axis.YP, 180.0F - yaw);
		pose.translate((noise(step, kind, 2) - 0.5F) * 2.0F * JOLT * glitch, 0.0F, 0.0F);
		pose.scale(-1.0F, -1.0F, 1.0F);
		pose.scale(SpiritArms.PLAYER_SCALE, SpiritArms.PLAYER_SCALE, SpiritArms.PLAYER_SCALE);
		pose.translate(0.0F, SpiritArms.MODEL_DROP, 0.0F);
		submit(context.submitNodeCollector(), pose, owner, frame, kind, light, coreAlpha, now, glitch, color, pale);
		pose.popPose();
	}
}
