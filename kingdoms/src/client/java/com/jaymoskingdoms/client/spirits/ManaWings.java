package com.jaymoskingdoms.client.spirits;

import com.jaymoskingdoms.Mana;
import com.jaymoskingdoms.ManaAlchemy;
import com.jaymoskingdoms.client.ManaClient;
import com.jaymoskingdoms.client.ManaClient.Glow;
import com.jaymoskingdoms.spirits.SpiritState;
import com.jaymoskingdoms.spirits.Spirits;
import com.jaymoskingdoms.spirits.ThessalyWings;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Input;
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
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (Andy, 2026-11-01) Thessaly's mana wings, on the client ({@link ThessalyWings} has the rules). Andy: "the mana wings animate
 * like angelic wispy 2-3 player wide hands with wings that extend beneath it, and flap gently as they hover."
 * <ul>
 * <li><b>The hover</b>: while your wings are spread and you are off the ground, this moves you the way the wind's hover does
 * (WASD drifts, Jump rises, Sneak sinks, a gentle bob when still), as high above the ground and as fast as the spirit's level
 * allows. Spread them on the ground and they lift you; Jump takes off again after you land. {@link #hovering} tells Jaymo's
 * Powers' hover sway (by reflection) to hang the body in the air the same way.</li>
 * <li><b>The look</b>: at your shoulder blades, two great open hands of Thessaly's light, palms back and fingers fanned out
 * like a wing's tips, each with long feathers of light hanging beneath it. Drawn as the spirit's arms are (the ki glow's wisps,
 * hollow shells added like light, Thessaly's rose in mana's blue taken toward white), they beat slowly while you hover, a
 * little harder as you climb, with the feathers trailing each beat; on the ground they rest half folded; folded away they
 * sweep back, droop and fade. Light motes drift down off them. Like all of the spirit, only the attuned (and those who read
 * magic) see them: no glow on you for this viewer, no wings.</li>
 * </ul>
 */
public final class ManaWings {
	/** On a player's render state: their wings this frame, or null for none. */
	static final RenderStateDataKey<Look> LOOK = RenderStateDataKey.create(() -> "jaymoskingdoms:mana_wings");

	/** The hover: how fast velocity follows input, the bob when still, and how fast it sinks back down when above its height. */
	static final double RESPONSE = 0.16, BOB = 0.012, OVER_SINK = 0.08;
	/** The lift when the wings spread on the ground (blocks a tick up). */
	static final double LIFT = 0.42;
	/** How fast the wings open or fold, and lift or settle, per tick (0-1). */
	static final float OPEN_EASE = 0.12F, ALOFT_EASE = 0.1F, RISING_EASE = 0.15F;
	/** The beat: radians a tick at rest and added at a full climb; how far it swings resting on the ground, aloft and climbing. */
	static final float FLAP_SPEED = 0.13F, FLAP_SPEED_RISE = 0.12F;
	static final float FLAP_REST = 0.05F, FLAP_ALOFT = 0.22F, FLAP_RISE = 0.15F;
	/** The wing's raise (radians up from level) on the ground and aloft, and folded away (drooping). */
	static final float RAISE_GROUND = 0.12F, RAISE_ALOFT = 0.38F, RAISE_FOLDED = -0.9F;
	/** The wing's sweep back (radians) spread and folded, and how far a downstroke brings it forward. */
	static final float SWEEP = 0.35F, SWEEP_FOLDED = 1.25F, SWEEP_BEAT = 0.1F;
	/** The size folded, as a share of spread. */
	static final float FOLDED_SIZE = 0.55F;
	/** The feathers: how far they trail behind the wing's beat (radians of the beat, then more for each further out), and how far they swing. */
	static final float FEATHER_LAG = 0.6F, FEATHER_LAG_EACH = 0.25F, FEATHER_SWING = 0.15F, FEATHER_TILT = 0.2F;
	/** The shells, inside out: how far each sits off the cubes (model pixels) and how bright each is. */
	static final float[] SHELLS = {0.0F, 0.45F, 1.0F};
	static final float[] SHELL_LIGHT = {0.75F, 0.24F, 0.09F};
	/** How far the colour goes toward white (angelic), and how fast the wisps run along it. */
	static final float WHITE = 0.35F, FLOW = 0.3F;
	/** Motes shed from the feathers aloft: chance a tick, and how far out (blocks, per unit of size) they start. */
	static final float MOTE_CHANCE = 0.3F, MOTE_OUT_MIN = 0.8F, MOTE_OUT_MAX = 2.0F;
	/** The draw order of the shells (behind the spirit's arms). */
	static final int ORDER = SpiritArms.ORDER - 3;

	/**
	 * One player's wings this frame: how spread they are ({@code open}, 0 folded away to 1), how far aloft ({@code aloft}, 0 on
	 * the ground to 1 hovering), how much they're climbing ({@code rising} 0-1), and their {@code size} by the spirit's level.
	 */
	record Look(float open, float aloft, float rising, float size) {
	}

	/** Each player's wings, eased from tick to tick. */
	private static final class Track {
		float open, prevOpen, aloft, prevAloft, rising, prevRising;
		float size = ThessalyWings.SIZE_FIRST;
	}

	private static final Map<Integer, Track> TRACKS = new HashMap<>();

	/** The local player's hover: whether it's holding them up, its velocity, and whether the wings were spread last tick. */
	private static boolean localHovering, wasSpread;
	private static Vec3 velocity = Vec3.ZERO;
	private static int ticks;

	private ManaWings() {
	}

	public static void register() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Layer(avatar));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(ManaWings::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			TRACKS.clear();
			localHovering = false;
			wasSpread = false;
			velocity = Vec3.ZERO;
		});
	}

	/** Whether {@code player}'s mana wings are spread now (Thessaly's, out, toggled on). */
	static boolean spread(Player player) {
		SpiritState state = Spirits.state(player);
		return state != null && ThessalyWings.spread(state);
	}

	/**
	 * Whether {@code player} is held up by the wings now: Jaymo's Powers' hover sway reads this (by reflection) to hang them in
	 * the air like the wind's hover.
	 */
	public static boolean hovering(Player player) {
		Minecraft minecraft = Minecraft.getInstance();
		if (player == minecraft.player) {
			return localHovering;
		}
		return spread(player) && !player.onGround() && !player.isInWater() && !player.isFallFlying() && !player.isPassenger()
			&& !player.getAbilities().flying;
	}

	// ---- each tick ---------------------------------------------------------------------------------------------------------

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null) {
			TRACKS.clear();
			localHovering = false;
			return;
		}
		if (minecraft.isPaused()) {
			return;
		}
		ticks++;
		fly(player);
		for (AbstractClientPlayer other : level.players()) {
			boolean spread = spread(other);
			Track track = TRACKS.get(other.getId());
			if (track == null) {
				if (!spread) {
					continue;
				}
				track = new Track();
				TRACKS.put(other.getId(), track);
			}
			SpiritState state = Spirits.state(other);
			if (state != null) {
				track.size = ThessalyWings.size(state.level());
			}
			boolean aloft = spread && hovering(other);
			double climb = other.getY() - other.yo;
			track.prevOpen = track.open;
			track.prevAloft = track.aloft;
			track.prevRising = track.rising;
			track.open = ease(track.open, spread ? 1.0F : 0.0F, OPEN_EASE);
			track.aloft = ease(track.aloft, aloft ? 1.0F : 0.0F, ALOFT_EASE);
			track.rising = ease(track.rising, aloft ? Mth.clamp((float)(climb / 0.25), 0.0F, 1.0F) : 0.0F, RISING_EASE);
			if (aloft && track.open > 0.5F && other.distanceToSqr(player) < 32.0 * 32.0) {
				mote(level, other, track);
			}
		}
		TRACKS.entrySet().removeIf(e -> e.getValue().open <= 0.0F && e.getValue().prevOpen <= 0.0F || level.getEntity(e.getKey()) == null);
	}

	private static float ease(float value, float to, float by) {
		return value < to ? Math.min(to, value + by) : Math.max(to, value - by);
	}

	/** The local player's hover: the wind's own controls, held to the height and speed the spirit's level allows. */
	private static void fly(LocalPlayer player) {
		SpiritState state = Spirits.state(player);
		boolean spread = state != null && ThessalyWings.spread(state) && !player.isSpectator() && !player.getAbilities().flying
			&& !player.isFallFlying() && !player.isPassenger() && !player.isInWater() && !player.isInLava();
		if (spread && !wasSpread && player.onGround()) {
			// spread on the ground, they lift you off it
			velocity = new Vec3(player.getDeltaMovement().x, LIFT, player.getDeltaMovement().z);
			player.setDeltaMovement(velocity);
			wasSpread = true;
			localHovering = true;
			return;
		}
		wasSpread = spread;
		if (!spread || player.onGround()) {
			localHovering = false;
			velocity = player.getDeltaMovement();
			return;
		}
		if (!localHovering) {
			// (walked off a ledge or jumped: catch the way you were already going)
			velocity = player.getDeltaMovement();
		}
		localHovering = true;
		int lvl = state.level();
		double speed = ThessalyWings.speed(lvl), rise = ThessalyWings.rise(lvl), height = ThessalyWings.height(lvl);
		Input input = player.input.keyPresses;
		Vec2 move = player.input.getMoveVector();
		float sin = Mth.sin(player.getYRot() * Mth.DEG_TO_RAD);
		float cos = Mth.cos(player.getYRot() * Mth.DEG_TO_RAD);
		double wishX = (move.x * cos - move.y * sin) * speed;
		double wishZ = (move.y * cos + move.x * sin) * speed;
		double wishY = 0.0;
		if (input.jump()) {
			wishY += rise;
		}
		if (input.shift()) {
			wishY -= rise;
		}
		if (wishY == 0.0 && wishX * wishX + wishZ * wishZ < 1.0E-4) {
			wishY = Math.cos(ticks * 0.1) * BOB;
		}
		// no higher than the wings can hold above the ground (over a drop, they settle down to it)
		double above = above(player, height + 2.0);
		if (above >= height) {
			wishY = Math.min(wishY, above > height + 0.5 ? -OVER_SINK : 0.0);
		}
		Vec3 wish = new Vec3(wishX, wishY, wishZ);
		velocity = velocity.add(wish.subtract(velocity).scale(RESPONSE));
		if (player.horizontalCollision) {
			velocity = new Vec3(velocity.x * 0.5, velocity.y, velocity.z * 0.5);
		}
		if (player.verticalCollision && velocity.y > 0.0) {
			velocity = new Vec3(velocity.x, 0.0, velocity.z);
		}
		player.setDeltaMovement(velocity);
		player.resetFallDistance();
	}

	/** Blocks from {@code player}'s feet down to the ground or water below, or {@code most} when there's none that near. */
	private static double above(Player player, double most) {
		Vec3 feet = player.position();
		HitResult hit = player.level().clip(new ClipContext(feet, feet.subtract(0.0, most, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, player));
		return hit.getType() == HitResult.Type.MISS ? most : feet.y - hit.getLocation().y;
	}

	/** A mote of light shed from under a wing, drifting down (only for those who may see the wings). */
	private static void mote(ClientLevel level, Player player, Track track) {
		if (player.isInvisible() || player.getRandom().nextFloat() >= MOTE_CHANCE || !seen(player)) {
			return;
		}
		float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
		double side = (player.getRandom().nextBoolean() ? 1.0 : -1.0)
			* Mth.lerp(player.getRandom().nextFloat(), MOTE_OUT_MIN, MOTE_OUT_MAX) * track.size;
		double back = 0.35;
		double x = player.getX() - Mth.cos(yaw) * side - Mth.sin(yaw) * back;
		double z = player.getZ() - Mth.sin(yaw) * side + Mth.cos(yaw) * back;
		double y = player.getY() + 0.6 + player.getRandom().nextDouble() * 0.8;
		level.addParticle(new DustParticleOptions(color() & 0xFFFFFF, 0.9F), x, y, z, 0.0, -0.02, 0.0);
	}

	/** Whether this client may see {@code entity}'s mana (and so its wings): it is attuned, and the viewer is or reads magic. */
	private static boolean seen(LivingEntity entity) {
		LocalPlayer viewer = Minecraft.getInstance().player;
		return viewer != null && Mana.attuned(entity) && (Mana.attuned(viewer) || ManaAlchemy.readsMagic(viewer));
	}

	/** Thessaly's rose in mana's blue, taken toward white. */
	static int color() {
		return ARGB.srgbLerp(WHITE, SpiritArms.mix(Spirits.of(Spirits.THESSALY).color()), 0xFFFFFFFF);
	}

	// ---- the look ----------------------------------------------------------------------------------------------------------

	/** From the living renderer's extractRenderState (called by {@link SpiritArms#extract}): this player's wings, if spread or folding. */
	static void extract(LivingEntity entity, LivingEntityRenderState state) {
		Track track = entity instanceof Player ? TRACKS.get(entity.getId()) : null;
		if (track == null) {
			state.setData(LOOK, null);
			return;
		}
		float pt = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
		float open = Mth.lerp(pt, track.prevOpen, track.open);
		if (open <= 0.001F) {
			state.setData(LOOK, null);
			return;
		}
		state.setData(LOOK, new Look(open, Mth.lerp(pt, track.prevAloft, track.aloft), Mth.lerp(pt, track.prevRising, track.rising), track.size));
	}

	/** The wings on a player. */
	private static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		private final WingModel[] shells = new WingModel[SHELLS.length];

		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
		}

		private WingModel shell(int i) {
			if (this.shells[i] == null) {
				this.shells[i] = new WingModel(LayerDefinition.create(mesh(SHELLS[i]), 64, 64).bakeRoot(), this);
			}
			return this.shells[i];
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			Look look = state.getData(LOOK);
			Glow glow = state.getData(ManaClient.KEY);
			if (look == null || glow == null || state.isInvisible) {
				return;
			}
			int color = color();
			float strength = Math.max(glow.strength(), SpiritArms.MIN_STRENGTH);
			float breathe = 1.0F + ManaClient.BREATHE * Mth.sin(state.ageInTicks * ManaClient.BREATHE_SPEED);
			float rise = (state.ageInTicks * ManaClient.RISE * FLOW) % 1.0F;
			float fade = look.open() * look.open();
			for (int i = 0; i < SHELLS.length; i++) {
				float light = Mth.clamp(SHELL_LIGHT[i] * strength * breathe * fade, 0.0F, 1.0F);
				collector.order(ORDER + i).submitModel(this.shell(i), state, poseStack,
					RenderTypes.energySwirl(SpiritHandRenderer.TEXTURE, i * 0.27F + 0.11F, -rise * (1 + i)), lightCoords, OverlayTexture.NO_OVERLAY,
					ManaClient.tint(i == 0 ? SpiritArms.ghost(color) : color, light), null, 0);
			}
		}
	}

	// ---- the model ---------------------------------------------------------------------------------------------------------

	/** Each finger's fan (radians up from straight out: top to bottom) and height on the palm's outer edge (model pixels). */
	static final float[] FINGER_FAN = {0.5F, 0.18F, -0.12F, -0.4F};
	static final float[] FINGER_Y = {-3.3F, -1.3F, 0.7F, 2.4F};
	/** The feathers: how many under each wing, the first one's place out along the wing and the gap after, and their lengths. */
	static final int FEATHERS = 7;
	static final float FEATHER_FROM = 1.5F, FEATHER_GAP = 2.6F, FEATHER_LENGTH = 9.0F, FEATHER_LONGER = 1.6F;
	/** How far each feather further out fans its tip outward (radians), from the first's. */
	static final float FEATHER_FAN = 0.05F, FEATHER_FAN_EACH = 0.09F;
	/** The strut from the shoulder blade to the hand, and the palm (model pixels). */
	static final float STRUT = 10.0F, PALM = 7.0F;

	/**
	 * Both wings, one shell of them. A HumanoidModel (as the spirit's own are) whose only visible part is the body, carrying the
	 * wings: the body takes the wearer's own pose, the wings spread, beat and fold by the {@link Look}.
	 */
	private static final class WingModel extends HumanoidModel<HumanoidRenderState> {
		private final Layer layer;
		private final ModelPart[] wings = new ModelPart[2], hands = new ModelPart[2];
		private final ModelPart[][] fingers = new ModelPart[2][FINGER_FAN.length], tips = new ModelPart[2][FINGER_FAN.length];
		private final ModelPart[][] feathers = new ModelPart[2][FEATHERS];

		WingModel(ModelPart root, Layer layer) {
			super(root);
			this.layer = layer;
			for (int w = 0; w < 2; w++) {
				this.wings[w] = this.body.getChild(w == 0 ? "wing_left" : "wing_right");
				this.hands[w] = this.wings[w].getChild("hand");
				for (int f = 0; f < FINGER_FAN.length; f++) {
					this.fingers[w][f] = this.hands[w].getChild("finger" + f);
					this.tips[w][f] = this.fingers[w][f].getChild("tip");
				}
				for (int f = 0; f < FEATHERS; f++) {
					this.feathers[w][f] = this.wings[w].getChild("feather" + f);
				}
			}
		}

		@Override
		public void setupAnim(HumanoidRenderState state) {
			resetPose();
			this.head.visible = false;
			this.hat.visible = false;
			this.rightArm.visible = false;
			this.leftArm.visible = false;
			this.rightLeg.visible = false;
			this.leftLeg.visible = false;
			Look look = state.getData(LOOK);
			this.body.visible = look != null;
			if (look == null) {
				return;
			}
			// the wearer's own body (a crouch, a lean), so the wings stay on the back
			this.body.loadPose(this.layer.getParentModel().body.storePose());
			float open = look.open(), aloft = look.aloft(), rising = look.rising();
			float t = state.ageInTicks;
			float speed = FLAP_SPEED + FLAP_SPEED_RISE * rising;
			float beat = Mth.sin(t * speed);
			float swing = (FLAP_REST + FLAP_ALOFT * aloft + FLAP_RISE * rising) * open;
			float raise = Mth.lerp(open, RAISE_FOLDED, Mth.lerp(aloft, RAISE_GROUND, RAISE_ALOFT) + swing * beat);
			float sweep = Mth.lerp(open, SWEEP_FOLDED, SWEEP - SWEEP_BEAT * beat * aloft);
			float size = look.size() * Mth.lerp(open, FOLDED_SIZE, 1.0F);
			for (int w = 0; w < 2; w++) {
				// +1: the wing out to the wearer's left (model +x); -1: to the right
				float s = w == 0 ? 1.0F : -1.0F;
				ModelPart wing = this.wings[w];
				wing.zRot = -s * raise;
				wing.yRot = -s * sweep;
				wing.xRot = 0.15F;
				wing.xScale = size;
				wing.yScale = size;
				wing.zScale = size;
				// the hand follows the beat a moment late, the fingers flex with it and fan out as the wings open
				this.hands[w].zRot = -s * 0.12F * Mth.sin(t * speed - 0.5F) * open;
				for (int f = 0; f < FINGER_FAN.length; f++) {
					this.fingers[w][f].zRot = -s * FINGER_FAN[f] * (0.4F + 0.6F * open);
					this.tips[w][f].zRot = -s * (0.12F + 0.08F * Mth.sin(t * speed - 0.9F)) * open;
				}
				// the feathers trail each beat, the outer ones latest, and fan out as the wings open
				for (int f = 0; f < FEATHERS; f++) {
					ModelPart feather = this.feathers[w][f];
					feather.xRot = FEATHER_TILT + FEATHER_SWING * Mth.sin(t * speed - FEATHER_LAG - f * FEATHER_LAG_EACH) * open * (0.3F + 0.7F * aloft);
					feather.zRot = -s * (FEATHER_FAN + FEATHER_FAN_EACH * f) * (0.3F + 0.7F * open);
				}
			}
		}
	}

	/** The wings' mesh, its cubes grown {@code out} model pixels (a shell). The standard parts are empty but the body's wings. */
	static MeshDefinition mesh(float out) {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
		head.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
		root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5.0F, 2.0F, 0.0F));
		root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12.0F, 0.0F));
		root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12.0F, 0.0F));
		PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
		CubeDeformation g = new CubeDeformation(out);
		for (int w = 0; w < 2; w++) {
			float s = w == 0 ? 1.0F : -1.0F;
			// from the shoulder blade: a wisp of a strut out to the hand
			CubeListBuilder strut = CubeListBuilder.create().texOffs(0, 0);
			box(strut, s, 0.0F, -0.75F, -0.75F, STRUT, 1.5F, 1.5F, g);
			PartDefinition wing = body.addOrReplaceChild(w == 0 ? "wing_left" : "wing_right", strut, PartPose.offset(s * 2.0F, 2.5F, 2.2F));
			// the open hand at its end, palm back
			CubeListBuilder palm = CubeListBuilder.create().texOffs(0, 8);
			box(palm, s, 0.0F, -4.0F, -0.75F, PALM, 7.0F, 1.5F, g);
			PartDefinition hand = wing.addOrReplaceChild("hand", palm, PartPose.offset(s * STRUT, 0.0F, 0.0F));
			for (int f = 0; f < FINGER_FAN.length; f++) {
				CubeListBuilder finger = CubeListBuilder.create().texOffs(16, 0);
				box(finger, s, 0.0F, -0.8F, -0.7F, 6.0F, 1.6F, 1.4F, g);
				PartDefinition part = hand.addOrReplaceChild("finger" + f, finger, PartPose.offset(s * PALM, FINGER_Y[f], 0.0F));
				CubeListBuilder tip = CubeListBuilder.create().texOffs(16, 4);
				box(tip, s, 0.0F, -0.65F, -0.6F, 5.0F, 1.3F, 1.2F, g);
				part.addOrReplaceChild("tip", tip, PartPose.offset(s * 6.0F, 0.0F, 0.0F));
			}
			// the thumb up off the palm's top edge, leaning in
			CubeListBuilder thumb = CubeListBuilder.create().texOffs(32, 0);
			box(thumb, s, -0.7F, -5.0F, -0.7F, 1.4F, 5.0F, 1.4F, g);
			hand.addOrReplaceChild("thumb", thumb, PartPose.offsetAndRotation(s * 1.0F, -3.5F, 0.0F, 0.0F, 0.0F, s * 0.35F));
			// the feathers beneath: hung from the strut and the palm's lower edge, longer further out, each tapering
			for (int f = 0; f < FEATHERS; f++) {
				float x = FEATHER_FROM + FEATHER_GAP * f;
				float length = FEATHER_LENGTH + FEATHER_LONGER * f;
				CubeListBuilder feather = CubeListBuilder.create().texOffs(0, 24);
				feather.addBox(-1.5F, 0.0F, -0.3F, 3.0F, length * 0.65F, 0.6F, g);
				feather.addBox(-1.0F, length * 0.65F, -0.3F, 2.0F, length * 0.35F, 0.6F, g);
				wing.addOrReplaceChild("feather" + f, feather, PartPose.offset(s * x, x < STRUT ? 0.75F : 3.0F, 0.3F));
			}
		}
		return mesh;
	}

	/** A box running {@code w} out from {@code x} along the wing's side {@code s} (+1 the model's +x, -1 its -x). */
	private static void box(CubeListBuilder c, float s, float x, float y, float z, float w, float h, float d, CubeDeformation g) {
		c.addBox(s > 0.0F ? x : -x - w, y, z, w, h, d, g);
	}
}
