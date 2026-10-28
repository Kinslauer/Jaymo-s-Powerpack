package com.jaymoskingdoms.client.spirits;

import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.client.ManaClient;
import com.jaymoskingdoms.client.ManaClient.Glow;
import com.jaymoskingdoms.spirits.Poses;
import com.jaymoskingdoms.spirits.Spirit;
import com.jaymoskingdoms.spirits.SpiritSickness;
import com.jaymoskingdoms.spirits.SpiritState;
import com.jaymoskingdoms.spirits.Spirits;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * (0.7.245) A mana spirit's arms. Andy: "these spirits should conjure as arms behind you with the same effects as your mana
 * aura making up their entire form", after Nero's Devil Breaker (DMC5) and Doppio's partial manifestation (JoJo), "all
 * different sizes". A render layer over players and over the casting folk (the same two-branch registration as
 * {@link ManaClient}): the wearer's own arm is copied, grown by the spirit's size about the shoulder and pushed behind,
 * then bent by the pose the spirit holds ({@link Poses}, a table of arm angles below) and drawn as the aura's own shells
 * ({@link #ARM_SHELLS}), tinted by the spirit's colour mixed with mana's blue, at the strength the wearer's glow has
 * ({@link ManaClient#KEY}; no glow, no arms, so only the attuned see them). Each arm is jointed, with each kind's own make
 * and the move's wind-up, snap and fingers ({@link SpiritRig}); a blow trails ghosts and bursts where it lands.
 * <p>
 * The arms ease in over {@link #MANIFEST_TICKS} when the spirit is called and out when it is sent away; a pose is lerped in
 * over {@link #POSE_IN}, held to {@link #POSE_TICKS} and eased back. Wisps rise from the hands. While spirit sickness is on
 * you, a one-arm flicker shows now and then ({@link #FLICKER_MIN}-{@link #FLICKER_MAX} ticks apart, sooner each day), and
 * {@link SpiritHud} tints the screen edge with it.
 */
public final class SpiritArms {
	/** On a being's render state: its spirit's arms this frame, or null for none. */
	static final RenderStateDataKey<Look> LOOK = RenderStateDataKey.create(() -> "jaymoskingdoms:spirit_arms");

	/** Ticks the arms take to appear and to vanish. */
	static final int MANIFEST_TICKS = 10;
	/** A pose is lerped in over {@code POSE_IN} ticks, held until {@code POSE_TICKS} after it was stamped, and lerped out over {@code POSE_OUT}. */
	static final int POSE_IN = 4, POSE_TICKS = 16, POSE_OUT = 6;
	/** The size the figure's place is counted to at most, so the biggest don't float off. */
	static final float FLOAT_SIZE_CAP = 1.6F;

	/**
	 * (0.7.280) Where a spirit's arms come from. Andy (2026-10-28): "imagine the arms as emitting from the large ki figure that
	 * emanates from the character behind the player. simulate all the arms like this." Every spirit's arms grow from the
	 * shoulders of an unseen figure standing over your back, not from your own arm: model pixels {@code up}, {@code back} and
	 * {@code out} (each arm to its own side) per unit of size (counted at least 1 and at most {@link #FLOAT_SIZE_CAP}), the
	 * whole figure {@code right} of you, the arm's resting angle ({@code idle}, as the {@link #TABLE}'s), and whether the
	 * resting hand is held open, ready to grab.
	 */
	record Anchor(float up, float back, float out, float right, float[] idle, boolean open) {
	}

	/** Each spirit's figure, by kind (the Quiet One takes one of the four each time it is called: {@link #style}). */
	private static final Anchor[] ANCHORS = new Anchor[Spirits.QUIET + 1];

	static {
		// Grimsby: his blade held up over your shoulder, edge forward, always in sight (Andy: "Grimsby's blade ... should always be visible")
		ANCHORS[Spirits.GRIMSBY] = new Anchor(3.0F, 3.5F, 1.0F, 0.0F, new float[] {-1.35F, -0.15F, 0.30F}, false);
		// Varro: "imposing and above and behind and out to the right at an angle, as if it were ready to grab"
		ANCHORS[Spirits.VARRO] = new Anchor(6.5F, 5.0F, 2.5F, 2.5F, new float[] {-1.15F, 0.35F, 0.55F}, true);
		// Ambrose: "float above and behind you", the great hands hovering over your shoulders
		ANCHORS[Spirits.AMBROSE] = new Anchor(6.0F, 4.5F, 2.0F, 0.0F, new float[] {-1.0F, 0.0F, 0.35F}, false);
		// Thessaly: the glove at your shoulder, a little out, where you can always see it
		ANCHORS[Spirits.THESSALY] = new Anchor(2.5F, 3.0F, 1.5F, 0.0F, new float[] {-1.2F, -0.2F, 0.30F}, false);
		ANCHORS[Spirits.QUIET] = ANCHORS[Spirits.AMBROSE];
	}

	/** The open hand at rest (Varro's, ready to grab): fist, spread, wrist bent back. */
	static final float OPEN_CURL = 0.12F, OPEN_SPREAD = 0.9F, OPEN_WRIST = -0.45F;
	/** The size the projection forms from, as a share of its own. */
	static final float FORM_FROM = 0.8F;
	/** How much the kind colour shows against mana's blue (0 all blue, 1 all kind). */
	static final float KIND_MIX = 0.5F;
	/** The draw order of the shells, after the aura's own. */
	static final int ORDER = 8;
	/** Idle sway: how far and how fast the floating arms drift. */
	static final float SWAY = 0.025F, SWAY_SPEED = 0.05F;
	/** Wisps: chance each tick, per arm, of one dust mote at the hand; the arms must be this far manifest. */
	static final float WISP_CHANCE = 0.35F, WISP_FROM = 0.5F;
	/**
	 * The projection is only up while it's used (Andy: "the arm should appear and disappear while not using it ... it probably
	 * costs concentration to keep a projection up"): it forms over {@code APPEAR_TICKS} when a move starts, stays up through the
	 * move and {@code LINGER_TICKS} after, then fades over {@code FADE_TICKS}. A held thing, steady hands and spirit armour keep it
	 * up. Called out, the spirit shows itself once (its summon is a move) and then waits unseen, but for the odd wisp.
	 */
	static final float APPEAR_TICKS = 3.0F, LINGER_TICKS = 12.0F, FADE_TICKS = 10.0F;
	/** While the spirit speaks the arm shows itself, as an arm by its keeper, for this long (Andy: "they pop in whenever they speak"). */
	static final float SPEAK_TICKS = 70.0F;
	/** Unseen and waiting: the chance each tick of one wisp at the shoulder. */
	static final float WAIT_WISP_CHANCE = 0.04F;
	/** The sickness flicker: ticks between flickers (longest, shortest), how long one shows, how strong the arm is. */
	static final int FLICKER_MIN = 100, FLICKER_MAX = 300, FLICKER_TICKS = 6;
	static final float FLICKER_MANIFEST = 0.4F;
	/** A day of sickness in ticks (the client counts it from when it first sees the sickness), and the sickness's length in days. */
	static final int DAY_TICKS = 24000, SICK_DAYS = 5;

	/**
	 * What one being's spirit looks like this frame: its {@code kind}, how far manifest it is (0-1), the {@code pose} it holds
	 * with that pose's strength ({@code poseWeight} 0-1) and age in ticks, whether it has {@code arms} one or two, and the {@code size} of them as conjured ({@link Spirit#size(SpiritState)}).
	 */
	record Look(int kind, float manifest, int pose, float poseWeight, float poseAge, int arms, float size, int reach, int style, long shown) {
	}

	/**
	 * Whose figure a spirit's arms take ({@link #ANCHORS}): its own, but the Quiet One's changes each time it is called (Andy:
	 * "Quiet should be similar to each of them, as it animates differently each time"), picked by the size it was called at.
	 */
	static int style(int kind, int scale) {
		return kind == Spirits.QUIET ? Math.floorMod(scale * 7 + scale / 13, Spirits.QUIET) : Math.clamp(kind, 0, Spirits.QUIET);
	}

	static Anchor anchor(int style) {
		return ANCHORS[Math.clamp(style, 0, Spirits.QUIET)];
	}

	/** Whether {@code style}'s arms are always up while the spirit is out (Andy: "Grimsby's blade, and Thessaly should always be visible"). */
	static boolean alwaysUp(int style) {
		return style == Spirits.GRIMSBY || style == Spirits.THESSALY;
	}

	/** Arm angle offsets (x, y, z in radians, for the right arm; the left mirrors y and z) added to the wearer's own arm for each pose. */
	private static final float[][] TABLE = new float[Poses.COUNT][];

	static {
		TABLE[Poses.IDLE] = new float[] {0.25F, 0.0F, 0.45F};
		TABLE[Poses.REACH] = new float[] {-1.5F, -0.2F, 0.10F};
		TABLE[Poses.GRAB] = new float[] {-1.2F, -0.5F, 0.20F};
		TABLE[Poses.THROW] = new float[] {-2.6F, 0.0F, 0.10F};
		TABLE[Poses.CATCH] = new float[] {-1.6F, -0.4F, 0.30F};
		TABLE[Poses.SHOVE] = new float[] {-1.5F, 0.0F, 0.00F};
		TABLE[Poses.PIN] = new float[] {-1.3F, -0.3F, 0.15F};
		TABLE[Poses.HOLD] = new float[] {-0.9F, -0.6F, 0.00F};
		TABLE[Poses.SWORD] = new float[] {-1.9F, -0.3F, 0.20F};
		TABLE[Poses.GREATSWORD] = new float[] {-2.4F, 0.0F, 0.35F};
		TABLE[Poses.SPEAR] = new float[] {-1.5F, -0.1F, 0.00F};
		TABLE[Poses.AXE] = new float[] {-2.3F, 0.0F, 0.25F};
		TABLE[Poses.HAMMER] = new float[] {-0.75F, 0.0F, 0.05F}; // brought down in front (wound up overhead: SpiritRig)
		TABLE[Poses.FLURRY] = new float[] {-1.4F, -0.2F, 0.20F};
		TABLE[Poses.PARRY] = new float[] {-1.2F, -0.8F, 0.10F};
		TABLE[Poses.SLAM] = new float[] {-0.60F, 0.0F, 0.05F};
		TABLE[Poses.JAVELIN] = new float[] {-2.0F, -0.1F, 0.60F};
		TABLE[Poses.VOLLEY] = new float[] {-1.9F, 0.0F, 0.70F};
		TABLE[Poses.BOMBARD] = new float[] {-2.2F, 0.0F, 0.15F};
		TABLE[Poses.WALL] = new float[] {-1.0F, 0.0F, 0.90F};
		TABLE[Poses.MARK] = new float[] {-1.6F, 0.3F, 0.20F};
		TABLE[Poses.WARD] = new float[] {-1.5F, -0.6F, 0.50F};
		TABLE[Poses.FINISH] = new float[] {-2.5F, 0.0F, 0.10F};
		// the second patch's, tuned to read as the move (x raises the arm forward when negative; y swings it across the body when
		// negative; z spreads it out to the side when positive; see also poseAdjust for the ones that move)
		TABLE[Poses.AEGIS] = new float[] {-1.55F, -0.55F, 0.10F};   // both arms up and forward, meeting: a wall
		TABLE[Poses.BLESS] = new float[] {-3.0F, -0.1F, 0.15F};     // palm held high over the head
		TABLE[Poses.CATCH_ME] = new float[] {0.10F, -0.10F, 1.00F}; // arms down and open wide
		TABLE[Poses.MEND] = new float[] {-0.85F, -1.10F, 0.00F};    // a hand laid across the chest
		TABLE[Poses.ARMOUR] = new float[] {-0.50F, -1.30F, -0.20F}; // wrapped round the body
		TABLE[Poses.RIDE] = new float[] {0.05F, 0.00F, 0.12F};      // straight down, as stilts
		TABLE[Poses.PULL] = new float[] {-1.45F, -0.20F, 0.05F};    // reaching out, then drawn back (poseAdjust)
		TABLE[Poses.STEAL] = new float[] {-1.30F, -0.90F, 0.00F};   // a snatch across the body
		TABLE[Poses.TRAP] = new float[] {-1.20F, -1.00F, 0.20F};    // both hands closing together
		TABLE[Poses.UNLOCK] = new float[] {-1.40F, -0.50F, 0.10F};  // one hand out, turning a key (poseAdjust)
		TABLE[Poses.JUGGLE] = new float[] {-1.30F, -0.20F, 0.30F};  // one up one down, trading (poseAdjust)
		TABLE[Poses.ECHO] = new float[] {-1.80F, -0.10F, 0.20F};    // a blow, and the blow again (poseAdjust)
		TABLE[Poses.MIRROR] = new float[] {-1.20F, 0.70F, 0.60F};   // arms swept out to either side
		TABLE[Poses.STILL] = new float[] {0.10F, 0.00F, 0.20F};     // hanging, calm
		TABLE[Poses.SILENT] = new float[] {-2.40F, -0.20F, 0.05F};  // drawn back, then thrust (poseAdjust)
		TABLE[Poses.SENSE] = new float[] {-0.10F, 0.30F, 1.50F};    // spread wide and still
		TABLE[Poses.CLASH] = new float[] {-1.57F, -0.65F, 0.05F};   // both thrust forward, hands locked at full reach
		TABLE[Poses.SPEAK] = TABLE[Poses.IDLE];                     // as it rests (the floating ones take their own rest)
	}

	/** How far manifest each being's arms are: eased from where they were when the spirit went out or away. */
	private static final Map<Integer, Track> TRACKS = new HashMap<>();
	/** The sickness flicker: ticks left showing, which spirit-to-be, ticks to the next, and ticks the sickness has been seen. */
	private static int flickerLeft, flickerKind, nextFlicker, sickTicks;

	private SpiritArms() {
	}

	private static final class Track {
		boolean out;
		float from;
		double since;
		/** The show whose blow last burst, so each blow bursts once. */
		long burst;
		/** The show last seen, and whether the projection was already up when it began (a held move stamped again doesn't re-form). */
		long seenShow;
		boolean carried;
		float lastPresence;

		float presence(SpiritState spirit, double now) {
			if (spirit.show() != this.seenShow) {
				this.seenShow = spirit.show();
				this.carried = this.lastPresence >= 0.5F;
			}
			float p = SpiritArms.presence(spirit, now);
			if (this.carried && now - spirit.shownAt() < APPEAR_TICKS) {
				p = Math.max(p, this.lastPresence);
			}
			this.lastPresence = p;
			return p;
		}

		float value(double now) {
			float k = Mth.clamp((float)(now - this.since) / MANIFEST_TICKS, 0.0F, 1.0F);
			k = k * k * (3.0F - 2.0F * k);
			return this.from + ((this.out ? 1.0F : 0.0F) - this.from) * k;
		}
	}

	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Player(avatar));
			} else if (renderer.getModel() instanceof HumanoidModel<?>) {
				helper.register(folk(renderer));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(SpiritArms::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			TRACKS.clear();
			flickerLeft = 0;
			sickTicks = 0;
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Folk<?> folk(RenderLayerParent<?, ?> renderer) {
		return new Folk((RenderLayerParent)renderer);
	}

	/** Whether the sickness flicker is showing now, and the colour of the spirit-to-be (for {@link SpiritHud}); else -1. */
	static int flickering() {
		return flickerLeft > 0 ? Spirits.of(flickerKind).color() : -1;
	}

	// ---- the state of the arms ---------------------------------------------------------------------------------------------

	/** From the living renderer's extractRenderState (called by {@link ManaClient#extract}): this being's arms, if it has a spirit. */
	public static void extract(LivingEntity entity, LivingEntityRenderState state) {
		state.setData(LOOK, look(entity));
	}

	private static @Nullable Look look(LivingEntity entity) {
		SpiritState spirit = entity.getAttached(Spirits.SPIRIT);
		Minecraft minecraft = Minecraft.getInstance();
		double now = entity.level().getGameTime() + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		if (spirit == null) {
			TRACKS.remove(entity.getId());
			if (flickerLeft > 0 && entity == minecraft.player) {
				return new Look(flickerKind, FLICKER_MANIFEST, Poses.IDLE, 0.0F, 0.0F, 1, Spirits.of(flickerKind).size(), 0, style(flickerKind, 100), 0L);
			}
			return null;
		}
		Track track = TRACKS.get(entity.getId());
		if (track == null) {
			track = new Track();
			track.since = now;
			TRACKS.put(entity.getId(), track);
		}
		if (track.out != spirit.out()) {
			track.from = track.value(now);
			track.out = spirit.out();
			track.since = now;
		}
		float called = track.value(now);
		if (called <= 0.001F && !track.out) {
			TRACKS.remove(entity.getId());
			return null;
		}
		float manifest = called * track.presence(spirit, now);
		if (manifest <= 0.001F) {
			return null;
		}
		int pose = spirit.pose();
		float age = (float)(now - spirit.shownAt());
		float weight = 0.0F;
		if (spirit.show() != 0L && pose > Poses.IDLE && pose < Poses.COUNT && age >= 0.0F) {
			weight = age < POSE_TICKS ? Math.min(1.0F, age / POSE_IN) : 1.0F - (age - POSE_TICKS) / POSE_OUT;
			weight = Mth.clamp(weight, 0.0F, 1.0F);
		}
		return new Look(spirit.kind(), manifest, pose, weight, age, spirit.arms(), Spirits.of(spirit.kind()).size(spirit), spirit.reach(),
			style(spirit.kind(), spirit.scale()), spirit.shownAt());
	}

	/** How far beyond its keeper's box a being's spirit hand reaches now (blocks), for the culling box; 0 for none. */
	public static float cullingReach(Entity entity) {
		SpiritState spirit = entity instanceof LivingEntity living ? living.getAttached(Spirits.SPIRIT) : null;
		if (spirit == null || !spirit.out() || spirit.reach() == 0 || presence(spirit, entity.level().getGameTime()) <= 0.0F) {
			return 0.0F;
		}
		return spirit.reach() + 1.0F;
	}

	/** How far the projection is up ({@link #APPEAR_TICKS}): 1 while a move is going or something is held, 0 once it has faded. */
	static float presence(SpiritState spirit, double now) {
		if (spirit.toggled(SpiritState.STEADY) || spirit.toggled(SpiritState.ARMOUR) || alwaysUp(style(spirit.kind(), spirit.scale()))) {
			return 1.0F;
		}
		if (spirit.show() == 0L || spirit.pose() <= Poses.IDLE || spirit.pose() >= Poses.COUNT) {
			return 0.0F;
		}
		float age = (float)(now - spirit.shownAt());
		if (age < 0.0F) {
			return 0.0F;
		}
		if (age < APPEAR_TICKS) {
			float k = age / APPEAR_TICKS;
			return k * k * (3.0F - 2.0F * k);
		}
		float gone = age - (spirit.pose() == Poses.SPEAK ? SPEAK_TICKS : POSE_TICKS + POSE_OUT + LINGER_TICKS);
		return gone <= 0.0F ? 1.0F : Mth.clamp(1.0F - gone / FADE_TICKS, 0.0F, 1.0F);
	}

	/** Each client tick: the sickness flicker's timing, and wisps rising from the hands of every spirit in view. */
	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null) {
			return;
		}
		SpiritSickness.State sick = player.getAttached(Spirits.SICK);
		if (sick == null) {
			flickerLeft = 0;
			sickTicks = 0;
			nextFlicker = 0;
		} else {
			sickTicks++;
			flickerKind = sick.kind();
			if (flickerLeft > 0) {
				flickerLeft--;
			} else if (--nextFlicker <= 0) {
				flickerLeft = FLICKER_TICKS;
				// every day of the sickness the flickers come sooner
				float sooner = 1.0F - 0.6F * Math.min(sickTicks / (float)DAY_TICKS, SICK_DAYS - 1.0F) / (SICK_DAYS - 1.0F);
				nextFlicker = (int)((FLICKER_MIN + player.getRandom().nextInt(FLICKER_MAX - FLICKER_MIN + 1)) * sooner);
			}
		}
		if (minecraft.isPaused() || TRACKS.isEmpty() && flickerLeft <= 0) {
			return;
		}
		if (level.getGameTime() % 100L == 0L) {
			// (whoever left the world, or was unloaded, with arms out)
			TRACKS.keySet().removeIf(id -> level.getEntity(id) == null);
		}
		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof LivingEntity living && living.distanceToSqr(player) < 32.0 * 32.0) {
				wisp(level, living);
				burst(level, living);
			}
		}
	}

	private static void wisp(ClientLevel level, LivingEntity entity) {
		SpiritState spirit = entity.getAttached(Spirits.SPIRIT);
		int kind;
		int arms;
		float manifest;
		float size;
		if (spirit != null) {
			Track track = TRACKS.get(entity.getId());
			kind = spirit.kind();
			arms = spirit.arms();
			size = Spirits.of(kind).size(spirit);
			long now = entity.level().getGameTime();
			float called = track == null ? 0.0F : track.value(now);
			manifest = called * (track == null ? 0.0F : SpiritArms.presence(spirit, now));
			if (manifest < WISP_FROM && called >= 0.99F && entity.getRandom().nextFloat() < WAIT_WISP_CHANCE && !entity.isInvisible()
				&& visibleToAttuned(entity)) {
				// waiting unseen: now and then a wisp at the shoulder, so the keeper knows it's there
				float yaw = entity.yBodyRot * Mth.DEG_TO_RAD;
				double side = entity.getRandom().nextBoolean() ? 0.4 : -0.4;
				level.addParticle(new DustParticleOptions(ghost(mix(Spirits.of(kind).color())) & 0xFFFFFF, 0.7F),
					entity.getX() - Mth.cos(yaw) * side, entity.getY() + 1.4, entity.getZ() - Mth.sin(yaw) * side, 0.0, 0.02, 0.0);
			}
		} else if (flickerLeft > 0 && entity == Minecraft.getInstance().player) {
			kind = flickerKind;
			arms = 1;
			size = Spirits.of(kind).size();
			manifest = FLICKER_MANIFEST * 2.0F;
		} else {
			return;
		}
		if (manifest < WISP_FROM || entity.isInvisible() || !visibleToAttuned(entity)) {
			return;
		}
		Spirit sp = Spirits.of(kind);
		DustParticleOptions mote = new DustParticleOptions(ghost(mix(sp.color())) & 0xFFFFFF, 1.3F);
		float yaw = entity.yBodyRot * Mth.DEG_TO_RAD;
		double behindX = Mth.sin(yaw), behindZ = -Mth.cos(yaw);
		for (int i = 0; i < arms; i++) {
			if (entity.getRandom().nextFloat() >= WISP_CHANCE) {
				continue;
			}
			// from the figure's shoulder over your back, a little toward where the hand rests
			Anchor a = anchor(style(kind, spirit == null ? 100 : spirit.scale()));
			double lift = Mth.clamp(size, 1.0F, FLOAT_SIZE_CAP) * PX;
			double side = (i == 0 ? 1.0 : -1.0) * (SHOULDER_OUT + a.out() * lift) + a.right() * lift;
			double back = a.back() * lift - 0.35 * Math.min(size, FLOAT_SIZE_CAP);
			double x = entity.getX() - Mth.cos(yaw) * side + behindX * back;
			double z = entity.getZ() - Mth.sin(yaw) * side + behindZ * back;
			double y = entity.getY() + SHOULDER_Y + a.up() * lift;
			// smoking off the projection: drifting back and up from it, the way a ghost's edge trails away
			level.addParticle(mote, x + (entity.getRandom().nextDouble() - 0.5) * 0.3, y + (entity.getRandom().nextDouble() - 0.5) * 0.3,
				z + (entity.getRandom().nextDouble() - 0.5) * 0.3, behindX * 0.02, 0.025, behindZ * 0.02);
		}
	}

	/**
	 * The blow landing: when a strike or a swing reaches its hit, a burst where the hand lands ahead of the caster: dust in the
	 * spirit's colour, sparks and crits flung out, and a crack (heard only by those who can see the arms).
	 */
	private static void burst(ClientLevel level, LivingEntity entity) {
		SpiritState spirit = entity.getAttached(Spirits.SPIRIT);
		Track track = TRACKS.get(entity.getId());
		if (spirit == null || track == null || !spirit.out() || spirit.show() == 0L || track.burst == spirit.show()
			|| !SpiritRig.lands(spirit.pose())) {
			return;
		}
		float age = (float)(level.getGameTime() - spirit.shownAt());
		if (age < SpiritRig.HIT_T || age > SpiritRig.HIT_T + BURST_LATE) {
			return;
		}
		track.burst = spirit.show();
		if (entity.isInvisible() || !visibleToAttuned(entity)) {
			return;
		}
		float size = Math.min(Spirits.of(spirit.kind()).size(spirit), FLOAT_SIZE_CAP);
		// where the hand landed: as far as it reached out, or just ahead
		double out = spirit.reach() > 0 ? spirit.reach() : BURST_AHEAD + BURST_AHEAD * size;
		Vec3 at = entity.getEyePosition().add(entity.getLookAngle().scale(out));
		DustParticleOptions dust = new DustParticleOptions(mix(Spirits.of(spirit.kind()).color()) & 0xFFFFFF, 1.6F);
		var random = entity.getRandom();
		if (spirit.pose() == Poses.HAMMER || spirit.pose() == Poses.SLAM) {
			// a slam lands on the ground in front: a ring of aura thrown out along it
			Vec3 flat = new Vec3(entity.getLookAngle().x, 0.0, entity.getLookAngle().z).normalize();
			at = spirit.reach() > 0 ? new Vec3(at.x, entity.getY() + 0.1, at.z) // (a far slam: on the ground under where it struck)
				: entity.position().add(flat.scale(SLAM_AHEAD + SLAM_AHEAD * size)).add(0.0, 0.1, 0.0);
			for (int n = 0; n < SLAM_RING; n++) {
				double a = n * Mth.TWO_PI / SLAM_RING;
				double vx = Math.cos(a) * SLAM_SPEED, vz = Math.sin(a) * SLAM_SPEED;
				level.addParticle(dust, at.x, at.y, at.z, vx, 0.02, vz);
				if (n % 3 == 0) {
					level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, vx * 1.4, 0.15, vz * 1.4);
				}
			}
			level.playLocalSound(at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.35F, 1.4F, false);
		}
		for (int n = 0; n < BURST_MOTES; n++) {
			double vx = (random.nextDouble() - 0.5) * BURST_SPEED, vy = (random.nextDouble() - 0.5) * BURST_SPEED, vz = (random.nextDouble() - 0.5) * BURST_SPEED;
			level.addParticle(dust, at.x, at.y, at.z, vx, vy, vz);
			if (n % 2 == 0) {
				level.addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, vx * 0.6, vy * 0.6, vz * 0.6);
			}
			if (n % 3 == 0) {
				level.addParticle(ParticleTypes.CRIT, at.x, at.y, at.z, vx * 1.5, vy * 1.5, vz * 1.5);
			}
		}
		level.playLocalSound(at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.7F, 1.3F - 0.15F * size, false);
		level.playLocalSound(at.x, at.y, at.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.5F, 1.6F, false);
	}

	/** The burst: blocks ahead of the eye per unit of size (plus as many), motes, their speed, and how many ticks late it may still fire. */
	static final float BURST_AHEAD = 0.9F, BURST_SPEED = 0.35F, BURST_LATE = 4.0F;
	static final int BURST_MOTES = 18;
	/** The slam: blocks ahead of the feet per unit of size (plus as many), the ring's motes and how fast they run out. */
	static final float SLAM_AHEAD = 0.9F, SLAM_SPEED = 0.45F;
	static final int SLAM_RING = 28;

	/** Whether this client may see the aura of {@code entity} (the arms are made of it): attuned, or reading magic. */
	private static boolean visibleToAttuned(LivingEntity entity) {
		LocalPlayer viewer = Minecraft.getInstance().player;
		return viewer != null && Mana.attuned(entity) && (Mana.attuned(viewer) || com.jaymoskingdoms.ManaAlchemy.readsMagic(viewer));
	}

	/** The spirit's colour mixed with mana's blue. */
	static int mix(int color) {
		int r = (int)Mth.lerp(KIND_MIX, ARGB.red(ManaClient.COLOR), ARGB.red(color));
		int g = (int)Mth.lerp(KIND_MIX, ARGB.green(ManaClient.COLOR), ARGB.green(color));
		int b = (int)Mth.lerp(KIND_MIX, ARGB.blue(ManaClient.COLOR), ARGB.blue(color));
		return ARGB.color(255, r, g, b);
	}

	/** Blocks a model pixel is, and the shoulder's place on a standing player (blocks out from the middle, up from the feet). */
	static final double PX = SpiritArms.PLAYER_SCALE / 16.0, SHOULDER_OUT = 0.31, SHOULDER_Y = 1.3;

	// ---- first person ------------------------------------------------------------------------------------------------------

	/** The player layer's arms, kept for the first-person draw (the player isn't drawn in first person, so its layers aren't). */
	private static @Nullable Arms firstPersonArms;

	/**
	 * First person (after the hands, from {@code SpiritFirstPersonMixin}): your own spirit's arms, placed where they are on your
	 * body in third person, so the floating ones hover in view and a strike comes out in front of you (Andy: "more of the spirit
	 * should be visible in first person, especially when it strikes in front of you"). The pose stack is the hands' view space:
	 * the camera's turn and your body's are put back on it, then the living renderer's own steps.
	 */
	public static void firstPerson(PoseStack poseStack, SubmitNodeCollector collector, @Nullable AvatarRenderState avatar) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		Arms arms = firstPersonArms;
		if (player == null || avatar == null || arms == null || avatar.isInvisible) {
			return;
		}
		// (the hands' state may not carry the arms and the glow: read them fresh)
		ManaClient.extract(player, avatar);
		Look look = avatar.getData(LOOK);
		if (look == null || look.manifest() <= 0.01F) {
			return;
		}
		Camera camera = minecraft.gameRenderer.mainCamera();
		Vec3 eye = camera.position();
		poseStack.pushPose();
		// the hands have their own, narrower lens: squeeze across so the arms sit where the world would show them
		float widen = (float)(Math.tan(Math.toRadians(camera.getFov() * 0.5)) / Math.tan(Math.toRadians(HAND_FOV * 0.5)));
		poseStack.scale(1.0F / widen, 1.0F / widen, 1.0F);
		poseStack.rotate(new Quaternionf(camera.rotation()).conjugate());
		poseStack.translate(avatar.x - eye.x, avatar.y - eye.y, avatar.z - eye.z);
		poseStack.scale(avatar.scale, avatar.scale, avatar.scale);
		poseStack.rotateDegrees(Axis.YP, 180.0F - avatar.bodyRot);
		poseStack.scale(-1.0F, -1.0F, 1.0F);
		poseStack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
		poseStack.translate(0.0F, MODEL_DROP, 0.0F);
		arms.submit(poseStack, collector, avatar.lightCoords, avatar);
		poseStack.popPose();
	}

	/** The player renderer's own scale, and the drop from the feet to the model's origin (LivingEntityRenderer.submit). */
	static final float PLAYER_SCALE = 0.9375F, MODEL_DROP = -1.501F;
	/** The field of view the hands are drawn with (GameRenderer's item-in-hand projection). */
	static final double HAND_FOV = 70.0;

	// ---- the layers --------------------------------------------------------------------------------------------------------

	/** The arms on a player. */
	private static final class Player extends RenderLayer<AvatarRenderState, PlayerModel> {
		private final Arms arms;

		Player(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
			this.arms = new Arms(this::getParentModel);
			firstPersonArms = this.arms;
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			this.arms.submit(poseStack, collector, lightCoords, state);
		}
	}

	/** The arms on the casting folk, and on anyone else humanoid. */
	private static final class Folk<S extends HumanoidRenderState> extends RenderLayer<S, HumanoidModel<S>> {
		private final Arms arms;

		Folk(RenderLayerParent<S, HumanoidModel<S>> renderer) {
			super(renderer);
			this.arms = new Arms(this::getParentModel);
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, S state, float yRot, float xRot) {
			this.arms.submit(poseStack, collector, lightCoords, state);
		}
	}

	/** One renderer's arm models (a model for each kind and shell, and the trail's lagging ghosts, baked when first needed) and how they are drawn. */
	private static final class Arms {
		private final Supplier<HumanoidModel<?>> wearer;
		private final ArmModel[][] models = new ArmModel[Spirits.QUIET + 1][ARM_SHELLS.length];
		private final ArmModel[][] trails = new ArmModel[Spirits.QUIET + 1][TRAIL_LAG.length];

		Arms(Supplier<HumanoidModel<?>> wearer) {
			this.wearer = wearer;
		}

		private ArmModel model(int kind, int shell) {
			if (this.models[kind][shell] == null) {
				this.models[kind][shell] = new ArmModel(bake(kind, shell), this.wearer, kind, 0.0F);
			}
			return this.models[kind][shell];
		}

		private ArmModel trail(int kind, int ghost) {
			if (this.trails[kind][ghost] == null) {
				// on the outermost shell: soft at the edge, a wisp rather than a second arm
				this.trails[kind][ghost] = new ArmModel(bake(kind, ARM_SHELLS.length - 1), this.wearer, kind, TRAIL_LAG[ghost]);
			}
			return this.trails[kind][ghost];
		}

		private static ModelPart bake(int kind, int shell) {
			return LayerDefinition.create(SpiritRig.mesh(kind, ARM_SHELLS[shell]), 64, 64).bakeRoot();
		}

		void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, HumanoidRenderState state) {
			Look look = state.getData(LOOK);
			Glow glow = state.getData(ManaClient.KEY);
			if (look == null || glow == null || state.isInvisible || look.manifest() <= 0.01F) {
				return;
			}
			int kind = Math.clamp(look.kind(), 0, Spirits.QUIET);
			int color = mix(Spirits.of(kind).color());
			float breathe = 1.0F + ManaClient.BREATHE * Mth.sin(state.ageInTicks * ManaClient.BREATHE_SPEED);
			// Grimsby's axe and spear change what the aura is made of (Andy): the axe runs hot and ragged, the spear pulls it in tight and pale
			float axe = kind == Spirits.GRIMSBY && look.pose() == Poses.AXE ? look.poseWeight() : 0.0F;
			float spear = kind == Spirits.GRIMSBY && look.pose() == Poses.SPEAR ? look.poseWeight() : 0.0F;
			color = ARGB.srgbLerp(axe * AXE_MIX, color, AXE_COLOR);
			color = ARGB.srgbLerp(spear * SPEAR_MIX, color, SPEAR_COLOR);
			float flow = 1.0F + axe * (AXE_FLOW - 1.0F) + spear * (SPEAR_FLOW - 1.0F);
			float rise = (state.ageInTicks * ManaClient.RISE * ARM_FLOW * flow) % 1.0F;
			// the arms fade with how manifest they are, as well as grow
			float fade = look.manifest() * look.manifest();
			float strike = look.pose() == Poses.IDLE ? 0.0F : look.poseWeight();
			// the projection is called up at its full: only a little dimmer for a dim glow
			float strength = Math.max(glow.strength(), MIN_STRENGTH);
			for (int i = 0; i < ARM_SHELLS.length; i++) {
				float light = Mth.clamp(ARM_SHELL_LIGHT[i] * strength * breathe * fade, 0.0F, 1.0F);
				if (i > 0) {
					// the axe's outer shells flare and gutter like a fire; the spear's thin away, all of it drawn into the core
					light *= 1.0F + axe * AXE_FLARE * Mth.sin(state.ageInTicks * 1.7F + i * 2.0F);
					light *= 1.0F - spear * SPEAR_THIN;
				}
				// a strike blazes: every shell up to full while the move's pose holds (Andy: "glow with their maximum intensity when striking")
				light = Mth.lerp(strike * fade, light, 1.0F);
				// the core paler and near still (the body of the ghost), the edges in the spirit's colour, drifting
				collector.order(ORDER + i).submitModel(this.model(kind, i), state, poseStack,
					RenderTypes.energySwirl(ManaClient.TEXTURE, i * 0.29F + 0.13F, -rise * (1 + i)), lightCoords, OverlayTexture.NO_OVERLAY,
					ManaClient.tint(i == 0 ? ghost(color) : color, light), null, 0);
			}
			// the trail: two soft ghosts of the arm behind it while it moves fast (a blow's snap, a grasp closing), not at rest;
			// the slower wisp is the smoke off it ({@link #wisp})
			boolean fast = look.pose() != Poses.IDLE && look.poseWeight() > 0.0F && look.poseAge() >= SpiritRig.WIND_T
				&& look.poseAge() <= SpiritRig.HIT_T + TRAIL_AFTER;
			if (!fast) {
				return;
			}
			float trail = SpiritRig.lands(look.pose()) ? 1.0F : 0.5F;
			for (int g = 0; g < TRAIL_LAG.length; g++) {
				float light = TRAIL_LIGHT[g] * trail * fade;
				collector.order(ORDER + ARM_SHELLS.length + g).submitModel(this.trail(kind, g), state, poseStack,
					RenderTypes.energySwirl(ManaClient.TEXTURE, 0.13F + g * 0.31F, -rise * (2 + g)), lightCoords, OverlayTexture.NO_OVERLAY,
					ManaClient.tint(color, light), null, 0);
			}
		}
	}

	/** The trail: how many ticks each ghost lags the arm, how bright each is after a blow and at any other time, and how long after the hit it lingers. */
	static final float[] TRAIL_LAG = {1.0F, 2.0F}, TRAIL_LIGHT = {0.35F, 0.18F};
	/**
	 * The look, after Kung Lao's ghost (Andy: "a more solid looking yet wispy projection"): the arm's own shells, a core that is
	 * the arm itself (not inflated, bright and pale, its texture near still) under two thin, dim, slowly drifting edges in the
	 * spirit's colour, and smoke coming off it ({@link #wisp}). {@code ARM_FLOW} slows the drift; {@code MIN_STRENGTH} is the least
	 * of the wearer's glow the arms show at; {@code GHOST_PALE} how far the core goes to white.
	 */
	static final float[] ARM_SHELLS = {0.0F, 0.45F, 1.0F}, ARM_SHELL_LIGHT = {0.8F, 0.22F, 0.08F};
	static final float ARM_FLOW = 0.35F, MIN_STRENGTH = 0.7F, GHOST_PALE = 0.4F;

	/** A colour taken toward white, for the ghost's pale core and its smoke. */
	static int ghost(int color) {
		return ARGB.srgbLerp(GHOST_PALE, color, 0xFFFFFFFF);
	}
	/** Grimsby's axe: how far the aura goes to embers, how much faster it churns, how hard its outer shells flicker. His spear: to a pale bright core, streaming, the outer shells thinned. */
	static final int AXE_COLOR = 0xFFFF5A1E, SPEAR_COLOR = 0xFFDDF6FF;
	static final float AXE_MIX = 0.65F, AXE_FLOW = 2.5F, AXE_FLARE = 0.45F, SPEAR_MIX = 0.6F, SPEAR_FLOW = 4.0F, SPEAR_THIN = 0.65F;
	static final float TRAIL_AFTER = 3.0F;

	// ---- the model ---------------------------------------------------------------------------------------------------------

	/**
	 * A spirit's two arms, one shell of them (or one ghost of the trail, {@code lag} ticks behind). A HumanoidModel (as
	 * {@code WornModel} is) so every pose laid over a humanoid model lands on it; its only visible parts are the arms, jointed
	 * ({@link SpiritRig}): the shoulder takes the wearer's own arm (the floating ones only their look) and then the spirit's pose,
	 * and the elbow, wrist and fingers follow the move's shape.
	 */
	private static final class ArmModel extends HumanoidModel<HumanoidRenderState> {
		private final Supplier<HumanoidModel<?>> wearer;
		private final SpiritRig.Limb right, left;
		private final float lag;
		private final SpiritRig.Frame frame = new SpiritRig.Frame();

		ArmModel(ModelPart root, Supplier<HumanoidModel<?>> wearer, int kind, float lag) {
			super(root);
			this.wearer = wearer;
			this.right = new SpiritRig.Limb(this.rightArm, kind);
			this.left = new SpiritRig.Limb(this.leftArm, kind);
			this.lag = lag;
		}

		@Override
		@SuppressWarnings({"unchecked", "rawtypes"})
		public void setupAnim(HumanoidRenderState state) {
			resetPose();
			this.head.visible = false;
			this.hat.visible = false;
			this.body.visible = false;
			this.rightLeg.visible = false;
			this.leftLeg.visible = false;
			Look look = state.getData(LOOK);
			HumanoidModel parent = this.wearer.get();
			parent.setupAnim(state);
			if (look == null) {
				this.rightArm.visible = false;
				this.leftArm.visible = false;
				return;
			}
			// forming: it fades in and swells the last little way, rather than growing from nothing
			float size = look.size() * (FORM_FROM + (1.0F - FORM_FROM) * look.manifest());
			Anchor anchor = anchor(look.style());
			float lift = Mth.clamp(size, 1.0F, FLOAT_SIZE_CAP);
			int poseId = Mth.clamp(look.pose(), 0, Poses.COUNT - 1);
			float[] idle = anchor.idle();
			float[] target = poseId == Poses.SPEAK ? idle : poseId == Poses.ECHO ? echoAngle(look.shown()) : TABLE[poseId];
			float age = Math.max(0.0F, look.poseAge() - this.lag);
			float w = look.poseWeight();
			int mode = mode(look);
			for (int i = 0; i < 2; i++) {
				ModelPart arm = i == 0 ? this.rightArm : this.leftArm;
				ModelPart from = i == 0 ? parent.rightArm : parent.leftArm;
				arm.loadPose(from.storePose());
				arm.visible = i == 0 || look.arms() >= 2;
				float side = i == 0 ? 1.0F : -1.0F;
				SpiritRig.Frame f = this.frame;
				// (a ghost of the trail lags the idle as well as the move, so the idle's movement leaves a wisp too)
				float time = state.ageInTicks - this.lag;
				// reaching out, only the first hand goes; the other waits at rest
				boolean waits = mode == REACHING && i == 1;
				SpiritRig.frame(f, waits ? Poses.IDLE : poseId, age, waits ? 0.0F : w, idle, target, true, i, time);
				float drift = SWAY * Mth.sin(time * SWAY_SPEED + i * 2.1F) * (1.0F - 0.6F * w);
				f.sx += w * poseAdjust(poseId, age, i) + drift;
				if (anchor.open()) {
					// at rest the hand hangs open, fingers spread and the wrist cocked back: ready to grab
					float rest = 1.0F - w;
					f.curl = Mth.lerp(rest, f.curl, OPEN_CURL);
					f.spread = Mth.lerp(rest, f.spread, OPEN_SPREAD);
					f.wrist = Mth.lerp(rest, f.wrist, OPEN_WRIST);
				}
				// coming from the figure, not your arm: not swung by your walk, but a blow goes where you look (down when you look down)
				arm.xRot = w * state.xRot * Mth.DEG_TO_RAD;
				arm.yRot = 0.0F;
				arm.zRot = 0.0F;
				arm.xRot += f.sx;
				arm.yRot += f.sy * side;
				arm.zRot += f.sz * side;
				boolean shaped = mode != NORMAL && !waits;
				// at the figure's shoulder, above and behind yours (model -y is up, +z behind; the right arm's outside is -x)
				arm.y -= anchor.up() * lift;
				arm.z += anchor.back() * lift;
				arm.x -= (side * anchor.out() + anchor.right()) * lift;
				if (shaped) {
					shape(arm, f, mode, look, state, age, w, size, side, look.arms() >= 2);
				}
				(i == 0 ? this.right : this.left).pose(f, side);
				arm.xScale = size;
				arm.yScale = size;
				arm.zScale = size;
			}
		}
	}

	// ---- what a use becomes ------------------------------------------------------------------------------------------------

	/**
	 * Andy (2026-10-27): the arm is only the spirit's rest and its talk; "every other use becomes what it entails". Beyond the
	 * poses of the table, three uses take the hand where the use is: a move that touched something away from its keeper
	 * ({@link Look#reach()}, synced from the server) shoots the hand out along your aim the whole way there, the arm stretching
	 * (Nero's Devil Breaker), and swings or slams its weapon at the end; the aegis and the ward swell the hand into a shield held
	 * in front of you, palm out; catch me cups the hands beneath your feet.
	 */
	static final int NORMAL = 0, REACHING = 1, SHIELD = 2, BENEATH = 3;
	/** The eye's height in model pixels (a standing player's), and the feet's. */
	static final float EYE_Y = -3.65F, FEET_Y = 24.0F;
	/** Model pixels a block is (the player model is drawn at 0.9375). */
	static final float PX_PER_BLOCK = 16.0F / PLAYER_SCALE;
	/** The hand's middle beyond the wrist (pixels at size 1), so the hand, not the wrist, lands on what it reaches. */
	static final float HAND_PX = 3.0F;
	/** Reaching: the least it reaches from the eye as it sets off (blocks); a swing's sweep into its mark (radians); a slam's raise. */
	static final float REACH_FROM = 0.7F, REACH_SWEEP = 0.7F, REACH_RAISE = 1.1F;
	/** The shield: how far ahead of the eye (blocks), how far below the aim, the two hands apart (blocks), how big the hand swells. */
	static final float SHIELD_OUT = 1.25F, SHIELD_DROP = 0.3F, SHIELD_APART = 0.32F, SHIELD_HAND = 2.8F;
	/** Beneath: the hands' gap (pixels), how far ahead of the feet (pixels), how far below them, how big they swell, their cup. */
	static final float BENEATH_APART = 3.0F, BENEATH_AHEAD = 1.5F, BENEATH_DROP = 2.0F, BENEATH_HAND = 2.6F, BENEATH_CUP = 0.45F;
	/** Ticks the shield and the cradle take to form. */
	static final float FORM_TICKS = 3.0F;

	static int mode(Look look) {
		return switch (look.pose()) {
			case Poses.AEGIS, Poses.WARD -> SHIELD;
			case Poses.CATCH_ME -> BENEATH;
			case Poses.IDLE, Poses.SPEAK -> NORMAL;
			default -> look.reach() > 0 ? REACHING : NORMAL;
		};
	}

	/** Takes {@code arm} (its pivot already placed) where the use is, and sets the joints below it in {@code f}. */
	private static void shape(ModelPart arm, SpiritRig.Frame f, int mode, Look look, HumanoidRenderState state, float age, float w,
		float size, float side, boolean pair) {
		float hold = age < POSE_TICKS ? 1.0F : w;
		float pitch = state.xRot * Mth.DEG_TO_RAD, yaw = state.yRot * Mth.DEG_TO_RAD;
		// the aim, in the model's own space (y down, -z ahead, the right side -x)
		float dx = -Mth.cos(pitch) * Mth.sin(yaw), dy = Mth.sin(pitch), dz = -Mth.cos(pitch) * Mth.cos(yaw);
		float tx, ty, tz;
		switch (mode) {
			case REACHING -> {
				// out along the aim, landing as the blow does, overshooting a touch, drawn back as the pose lets go
				float out = SpiritRig.backOut(Math.min(1.0F, age / SpiritRig.HIT_T)) * hold;
				float blocks = Mth.lerp(out, REACH_FROM, look.reach());
				tx = dx * blocks * PX_PER_BLOCK;
				ty = EYE_Y + dy * blocks * PX_PER_BLOCK;
				tz = dz * blocks * PX_PER_BLOCK;
			}
			case SHIELD -> {
				float flat = pitch * 0.5F;
				float fx = -Mth.cos(flat) * Mth.sin(yaw), fy = Mth.sin(flat), fz = -Mth.cos(flat) * Mth.cos(yaw);
				float apart = pair ? SHIELD_APART * side : 0.0F;
				tx = (fx * SHIELD_OUT - Mth.cos(yaw) * apart) * PX_PER_BLOCK;
				ty = EYE_Y + (fy * SHIELD_OUT + SHIELD_DROP) * PX_PER_BLOCK;
				tz = (fz * SHIELD_OUT + Mth.sin(yaw) * apart) * PX_PER_BLOCK;
			}
			default -> {
				tx = pair ? -BENEATH_APART * side : 0.0F;
				ty = FEET_Y + BENEATH_DROP;
				tz = -BENEATH_AHEAD;
			}
		}
		float vx = tx - arm.x, vy = ty - arm.y, vz = tz - arm.z;
		float len = Math.max(0.01F, Mth.sqrt(vx * vx + vy * vy + vz * vz));
		arm.xRot = -Mth.HALF_PI + (float)Math.asin(Mth.clamp(vy / len, -1.0F, 1.0F));
		arm.yRot = (float)Mth.atan2(-vx, -vz);
		arm.zRot = 0.0F;
		f.elbow = 0.0F;
		f.reach = Math.max(0.0F, (len / size - SpiritRig.ELBOW_Y - SpiritRig.FORE - HAND_PX) / SpiritRig.FORE);
		float formed = SpiritRig.backOut(Math.min(1.0F, age / FORM_TICKS)) * hold;
		switch (mode) {
			case REACHING -> {
				float k = Math.min(1.0F, age / SpiritRig.HIT_T);
				int how = SpiritRig.shape(look.pose()).how();
				if (look.pose() == Poses.HAMMER || look.pose() == Poses.SLAM) {
					// raised high as it goes out, brought down on the mark
					arm.xRot -= REACH_RAISE * (1.0F - k);
				} else if (how == SpiritRig.SWING) {
					// swept in from the side onto the mark
					arm.yRot += REACH_SWEEP * side * (1.0F - k);
				}
			}
			case SHIELD -> {
				// the hand bent up at the wrist, palm out, fingers spread wide and swelling into the shield
				f.wrist = -Mth.HALF_PI * formed;
				f.curl = 0.0F;
				f.spread = 1.0F;
				f.hand = Mth.lerp(formed, 1.0F, SHIELD_HAND);
			}
			default -> {
				// the hand turned palm up under you, cupped, swelling into a cradle
				f.wrist = -Mth.HALF_PI * formed;
				f.curl = BENEATH_CUP;
				f.spread = 0.7F;
				f.hand = Mth.lerp(formed, 1.0F, BENEATH_HAND);
			}
		}
	}

	/**
	 * (0.7.280) Echo strike's angles (Andy: "echo strike should attack from different angles"): each echo comes in from one of
	 * these, picked by the tick it was struck: overhead, swept in from outside, cut across the body, an uppercut, a straight
	 * thrust, and down from high and wide.
	 */
	private static final float[][] ECHO_ANGLES = {
		{-2.70F, 0.00F, 0.10F},
		{-1.50F, 0.95F, 0.70F},
		{-1.40F, -1.05F, 0.10F},
		{-0.75F, -0.25F, 0.15F},
		{-1.60F, 0.00F, 0.00F},
		{-2.20F, 0.55F, 0.80F}};

	static float[] echoAngle(long shown) {
		return ECHO_ANGLES[(int)Math.floorMod(shown * 31L + (shown >> 3), (long)ECHO_ANGLES.length)];
	}

	/** How far the SILENT arm is drawn back before it thrusts (ticks), and by how much (radians added to x at the extreme). */
	static final float SILENT_DRAW_TICKS = 6.0F, SILENT_DRAW = 3.2F;
	/** JUGGLE's swing and speed; ECHO's second blow; PULL's draw-back; UNLOCK's turn. */
	static final float JUGGLE_SWING = 1.0F, JUGGLE_SPEED = 0.9F, ECHO_PULSE = 0.5F, ECHO_SPEED = 1.5F, PULL_DRAW = 1.2F, TURN = 0.4F;

	/** Extra x-angle for the poses that move rather than hold: arm {@code i} at {@code age} ticks into pose {@code pose}. */
	private static float poseAdjust(int pose, float age, int i) {
		return switch (pose) {
			case Poses.JUGGLE -> JUGGLE_SWING * Mth.sin(age * JUGGLE_SPEED + i * Mth.PI);
			case Poses.ECHO -> ECHO_PULSE * Mth.sin(age * ECHO_SPEED);
			case Poses.PULL -> age < POSE_IN + 4 ? 0.0F : PULL_DRAW * Mth.clamp((age - POSE_IN - 4) / 4.0F, 0.0F, 1.0F);
			case Poses.UNLOCK -> TURN * Mth.sin(age * 0.8F);
			case Poses.SILENT -> age < SILENT_DRAW_TICKS ? SILENT_DRAW * (1.0F - age / SILENT_DRAW_TICKS) : 0.0F;
			default -> 0.0F;
		};
	}
}
