package com.jaymospowers.client.rig;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.EmfCompat;
import com.jaymospowers.rig.Rig;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * (0.11.104) The worn Rig, drawn as a layered model of its own over the player (Andy: "it clips a bit currently with fresh
 * anims, and it should materialize on the user, peice by peice, one large component unfolding and mechanically
 * interlocking onto the user. also make a more layered rig, add depth and detail"):
 * <ul>
 * <li><b>Layers.</b> An under-suit with glowing seams, main plates over it, raised trim on their edges, vents, the spine
 * unit on the back, pistons at the elbows and knees, a lit chest core and visor ({@link RigSuitMesh} and its textures,
 * {@code tools/gen_rig.py}), with a glow pass over the lit pixels. The vanilla armour layers stay for armour stands and
 * mobs; on players the Rig's pieces are taken off the render state ({@link #extract}, after hidden armour and costumes
 * have had their say) and drawn here instead.</li>
 * <li><b>On the body, whatever moves it.</b> Like the costume: each frame the model re-runs the wearer's model and copies
 * its head, body and limbs, and when Entity Model Features (Fresh Animations) has animated the body, the pose it gave it
 * ({@link #keep}); our own poses are laid on it like on any player model.</li>
 * <li><b>Assembly.</b> Each piece builds itself on when it's put on (by hand or by a suit call), plate by plate, each
 * plate swinging or sliding in from where it was folded and locking with an overshoot and a clunk (and a sound): the
 * Core's spine unit lands on the back and unfolds its wings, the under-suit spreads from it, the chest's halves swing
 * round from the back and meet, the reactor slots in, then the abdomen, shoulders and upper arms; the Arms close the hip
 * harness and fit the thighs, gauntlets and joints; the Thrusters raise the shins and clamp the boots; the Helm comes
 * last, its faceplate swinging down. Taken off (or parked), it all comes apart the same way, backwards and quicker.
 * Players already wearing it when they come into view are shown whole.</li>
 * </ul>
 */
public final class RigSuit {
	/** Coming off plays the build backwards this much faster. */
	static final float REVERSE_SPEED = 1.6F;
	/** How far a plate overshoots its lock before settling back (the back-out easing's strength). */
	static final float OVERSHOOT = 1.9F;
	/** The clunk as a plate locks: how long (ticks), how much it swells, and how far it's driven home (pixels). */
	static final float CLUNK_TICKS = 3.0F, CLUNK_SCALE = 0.05F, CLUNK_DROP = 0.3F;
	/** Heavy plates (the spine unit, the reactor, the faceplate...) clunk this much harder. */
	static final float HEAVY_CLUNK = 1.8F;
	/** The arm plates are narrowed this much over a slim arm. */
	static final float SLIM_ARM = 0.8F;
	/** The suit's lights come up as the reactor locks in: from this tick of the Core's build, over this long. */
	static final float BOOT_AT = 20.0F, BOOT_TICKS = 10.0F;
	/** Lock sounds: volumes, and how many a player's suit may make in one tick; heard within this range (blocks). */
	static final float LOCK_VOLUME = 0.35F, HEAVY_VOLUME = 0.3F, UNLOCK_VOLUME = 0.2F;
	static final int SOUNDS_PER_TICK = 2;
	static final double SOUND_RANGE = 32.0;
	/** A backpack hangs this much further off the back over the spine unit (pixels; the armour's own gap included). */
	public static final float BACKPACK_CLEARANCE = 2.2F;

	/** Per piece (Rig.Piece's ordinal): how far into its build it is (ticks), or -1 when it isn't drawn. Null: no Rig. */
	static final RenderStateDataKey<float[]> AGES = RenderStateDataKey.create(() -> "jaymospowers:rig_suit");
	/** The body's pose as EMF animated it (root, head, body, arms, legs), for the suit drawn over it. */
	static final RenderStateDataKey<PartPose[]> BODY = RenderStateDataKey.create(() -> "jaymospowers:rig_suit_body");
	/** The model the player renderer drew this player with (MCA swaps it per player). */
	static final RenderStateDataKey<PlayerModel> PARENT = RenderStateDataKey.create(() -> "jaymospowers:rig_suit_parent");
	/** (0.11.107) How far the shoulder launcher's pods are raised (0-1; firing, {@link RigPose#pods}). */
	static final RenderStateDataKey<Float> POD = RenderStateDataKey.create(() -> "jaymospowers:rig_suit_pod");
	/** The pods: how far they rise (pixels) and tip back (radians) when raised. */
	static final float POD_RISE = 1.4F, POD_TIP = 0.5F;
	/** (0.11.110) How far the faceplate is raised (0-1; RigHelm#FACE_UP), and how far it swings up (radians), per tick. */
	static final RenderStateDataKey<Float> FACE = RenderStateDataKey.create(() -> "jaymospowers:rig_suit_face");
	static final float FACE_LIFT = 1.45F, FACE_EASE = 0.25F;
	/** (0.12.2) Each piece's dye from the Rig Forge (rgb), or -1 for bare metal; null when none is dyed. */
	static final RenderStateDataKey<int[]> COLORS = RenderStateDataKey.create(() -> "jaymospowers:rig_suit_colors");
	/** The plates are mid grey, so a dye is brightened this much before it tints them (or it comes out dark). */
	static final float DYE_LIFT = 1.7F;
	/** The glow pass's colour (its lights coming up). */
	static final RenderStateDataKey<Integer> GLOW = RenderStateDataKey.create(() -> "jaymospowers:rig_suit_glow");

	static final Identifier TEXTURE = JaymosPowers.id("textures/entity/rig/suit.png");
	static final Identifier GLOW_TEXTURE = JaymosPowers.id("textures/entity/rig/suit_glow.png");
	/** Narrower than this (pixels), an arm is a slim one. */
	private static final float SLIM_BELOW = 3.5F;
	private static final RandomSource ANY = RandomSource.create();
	private static final Rig.Piece[] PIECES = Rig.Piece.values();

	/** Every player's pieces, by entity id. */
	private static final Map<Integer, Track> TRACKS = new HashMap<>();

	private RigSuit() {
	}

	static void init() {
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new Layer(avatar));
			}
		});
		ClientTickEvents.END_CLIENT_TICK.register(RigSuit::tick);
	}

	/** One player's pieces: whether each is worn, and how far its build had got when that last changed. */
	private static final class Track {
		final boolean[] worn = new boolean[PIECES.length];
		final float[] base = new float[PIECES.length];
		final double[] mark = new double[PIECES.length];
		/** Whether each worn piece was drawn last frame (not hidden, nor under a costume or a form). */
		final boolean[] shown = new boolean[PIECES.length];
		/** Each piece's build at the last sound check. */
		final float[] heard = new float[PIECES.length];

		/** How far into its build a piece is at {@code time} (game ticks): growing while worn, shrinking once it's off. */
		float age(int piece, double time) {
			float dt = (float)(time - this.mark[piece]);
			return this.worn[piece] ? Math.min(this.base[piece] + dt, done(piece)) : this.base[piece] - dt * REVERSE_SPEED;
		}
	}

	/** A piece's build is over (its last plate locked and settled). */
	private static float done(int piece) {
		return RigSuitMesh.SPAN[piece] + CLUNK_TICKS;
	}

	/** The player's track, brought up to date with what they wear now. */
	private static Track track(Player player, long now) {
		Track track = TRACKS.get(player.getId());
		if (track == null) {
			// first seen: as it is, nothing building
			track = new Track();
			for (int i = 0; i < PIECES.length; i++) {
				track.worn[i] = Rig.wears(player, PIECES[i]);
				track.base[i] = track.worn[i] ? done(i) : -1.0F;
				track.mark[i] = now;
				track.heard[i] = track.base[i];
				track.shown[i] = true;
			}
			TRACKS.put(player.getId(), track);
			return track;
		}
		for (int i = 0; i < PIECES.length; i++) {
			boolean worn = Rig.wears(player, PIECES[i]);
			if (worn != track.worn[i]) {
				// turn round from wherever it had got to
				track.base[i] = Mth.clamp(track.age(i, now), 0.0F, done(i));
				track.mark[i] = now;
				track.worn[i] = worn;
			}
		}
		return track;
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			TRACKS.clear();
			FACES.clear();
			return;
		}
		long now = level.getGameTime();
		Set<Integer> seen = new HashSet<>();
		for (AbstractClientPlayer player : level.players()) {
			seen.add(player.getId());
			sounds(minecraft, level, player, track(player, now), now);
		}
		TRACKS.keySet().retainAll(seen);
		FACES.keySet().retainAll(seen);
	}

	/** A clunk as each plate locks on (a hiss as it lets go), for players near enough to hear it. */
	private static void sounds(Minecraft minecraft, ClientLevel level, AbstractClientPlayer player, Track track, long now) {
		LocalPlayer me = minecraft.player;
		boolean near = me != null && me.distanceToSqr(player) < SOUND_RANGE * SOUND_RANGE;
		int played = 0;
		for (int piece = 0; piece < PIECES.length; piece++) {
			float age = track.age(piece, now);
			float was = track.heard[piece];
			track.heard[piece] = age;
			if (!near || age == was || !track.shown[piece] || player.isInvisible()) {
				continue;
			}
			for (int i = 0; i < RigSuitMesh.PLATES.length && played < SOUNDS_PER_TICK; i++) {
				if (RigSuitMesh.PIECE[i] != piece) {
					continue;
				}
				float lock = RigSuitMesh.TIME[i][0] + RigSuitMesh.TIME[i][1];
				boolean locked = was < lock && age >= lock;
				boolean released = was >= lock && age < lock;
				if (!locked && !released) {
					continue;
				}
				double y = player.getY() + switch (RigSuitMesh.PLATES[i][0]) {
					case "head" -> 1.7;
					case "right_leg", "left_leg" -> 0.4;
					default -> 1.1;
				};
				float pitch = 1.2F + level.getRandom().nextFloat() * 0.3F;
				if (locked) {
					level.playLocalSound(player.getX(), y, player.getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.PLAYERS, LOCK_VOLUME, pitch, false);
					if (RigSuitMesh.HEAVY[i]) {
						level.playLocalSound(player.getX(), y, player.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, HEAVY_VOLUME, pitch + 0.3F, false);
					}
				} else {
					level.playLocalSound(player.getX(), y, player.getZ(), SoundEvents.PISTON_EXTEND, SoundSource.PLAYERS, UNLOCK_VOLUME, pitch + 0.6F, false);
				}
				played++;
			}
		}
	}

	/**
	 * From the avatar renderer's extractRenderState, after hidden armour, costumes and forms have taken what they hide off
	 * the state: the Rig's pieces still on it come off it (the vanilla armour layer would draw them) and onto the suit.
	 */
	public static void extract(Avatar entity, AvatarRenderState state, float partialTicks) {
		state.setData(AGES, null);
		state.setData(BODY, null);
		state.setData(PARENT, null);
		if (!(entity instanceof Player player)) {
			return;
		}
		long now = player.level().getGameTime();
		Track track = track(player, now);
		double time = now + partialTicks;
		float[] ages = null;
		int[] colors = null;
		for (int i = 0; i < PIECES.length; i++) {
			Rig.Piece piece = PIECES[i];
			boolean onState = piece.item() != null && equipment(state, piece).is(piece.item());
			if (onState) {
				DyedItemColor dye = equipment(state, piece).get(DataComponents.DYED_COLOR);
				if (dye != null) {
					if (colors == null) {
						colors = new int[] {-1, -1, -1, -1};
					}
					colors[i] = dye.rgb() & 0xFFFFFF;
				}
				switch (piece) {
					case HELM -> {
						state.headEquipment = ItemStack.EMPTY;
						state.headItem.clear();
					}
					case CORE -> state.chestEquipment = ItemStack.EMPTY;
					case ARMS -> state.legsEquipment = ItemStack.EMPTY;
					case THRUSTERS -> state.feetEquipment = ItemStack.EMPTY;
				}
			}
			float age = -1.0F;
			if (track.worn[i]) {
				track.shown[i] = onState;
				if (onState) {
					age = track.age(i, time);
				}
			} else if (track.shown[i] && !state.isInvisible) {
				// coming apart: only what was showing while it was worn
				float left = track.age(i, time);
				age = left > 0.0F ? left : -1.0F;
			}
			if (age >= 0.0F) {
				if (ages == null) {
					ages = new float[] {-1.0F, -1.0F, -1.0F, -1.0F};
				}
				ages[i] = age;
			}
		}
		state.setData(AGES, ages);
		state.setData(COLORS, ages == null ? null : colors);
		state.setData(POD, ages == null ? null : RigPose.pods(player, partialTicks));
		state.setData(FACE, ages == null ? null : face(player, (float)time));
		state.setData(GLOW, ages == null ? null : glow(ages[Rig.Piece.CORE.ordinal()], (float)time));
	}

	private static ItemStack equipment(AvatarRenderState state, Rig.Piece piece) {
		return switch (piece) {
			case HELM -> state.headEquipment;
			case CORE -> state.chestEquipment;
			case ARMS -> state.legsEquipment;
			case THRUSTERS -> state.feetEquipment;
		};
	}

	/** Per player: {how far the faceplate is up, the time it was last stepped}. */
	private static final Map<Integer, float[]> FACES = new HashMap<>();

	/** The faceplate's lift, easing toward up or down. */
	private static float face(Player player, float now) {
		boolean up = com.jaymospowers.rig.RigHelm.faceUp(player);
		float[] track = FACES.get(player.getId());
		if (track == null) {
			if (!up) {
				return 0.0F;
			}
			// first seen up: already up
			track = new float[] {1.0F, now};
			FACES.put(player.getId(), track);
		}
		float dt = Mth.clamp(now - track[1], 0.0F, 5.0F);
		track[1] = now;
		track[0] = Mth.clamp(track[0] + (up ? FACE_EASE : -FACE_EASE) * dt, 0.0F, 1.0F);
		if (!up && track[0] <= 0.0F) {
			FACES.remove(player.getId());
		}
		float x = track[0];
		return x * x * (3.0F - 2.0F * x);
	}

	/** The glow pass's colour: full, but flickering up as the Core's reactor locks in (and down as it comes off). */
	private static int glow(float coreAge, float time) {
		if (coreAge < 0.0F || coreAge >= BOOT_AT + BOOT_TICKS) {
			return 0xFFFFFFFF;
		}
		float b = Mth.clamp((coreAge - BOOT_AT) / BOOT_TICKS, 0.0F, 1.0F);
		if (b > 0.0F && b < 1.0F && Mth.sin(time * 2.3F) < -0.3F) {
			b *= 0.4F;
		}
		int v = Math.round(Mth.lerp(b, 0.15F, 1.0F) * 255.0F);
		return 0xFF000000 | v << 16 | v << 8 | v;
	}

	/**
	 * Just before the render layers (LivingEntityRenderer.submit): keeps the body's pose if Entity Model Features has
	 * just animated it (Fresh Animations), so the suit sits on the body as it really is posed.
	 */
	public static void keep(EntityRenderState state, Model<?> body) {
		if (state.getData(AGES) == null || !(body instanceof PlayerModel player) || !EmfCompat.bodyAnimated(state, body)) {
			return;
		}
		ModelPart[] parts = parts(player);
		PartPose[] pose = new PartPose[parts.length];
		for (int i = 0; i < parts.length; i++) {
			pose[i] = parts[i].storePose();
		}
		state.setData(BODY, pose);
	}

	/** How much further a backpack must hang off this player's back, clear of the spine unit (0: no spine unit there). */
	public static float backpackClearance(AvatarRenderState state) {
		float[] ages = state.getData(AGES);
		return ages != null && ages[Rig.Piece.CORE.ordinal()] > RigSuitMesh.TIME[0][0] ? BACKPACK_CLEARANCE : 0.0F;
	}

	static ModelPart[] parts(PlayerModel model) {
		return new ModelPart[] {model.root(), model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
	}

	private static boolean slimArm(ModelPart arm, boolean fallback) {
		if (arm.isEmpty()) {
			return fallback;
		}
		ModelPart.Cube cube = arm.getRandomCube(ANY);
		return cube.maxX - cube.minX < SLIM_BELOW;
	}

	/** 0 → 1, passing 1 and settling back to it. */
	private static float backOut(float p) {
		float q = p - 1.0F;
		return 1.0F + (OVERSHOOT + 1.0F) * q * q * q + OVERSHOOT * q * q;
	}

	/** A dye as the plates' tint: brightened (they're grey), opaque. -1 (bare metal) stays white. */
	static int tint(int rgb) {
		if (rgb < 0) {
			return 0xFFFFFFFF;
		}
		int r = Math.min(255, Math.round((rgb >> 16 & 0xFF) * DYE_LIFT));
		int g = Math.min(255, Math.round((rgb >> 8 & 0xFF) * DYE_LIFT));
		int b = Math.min(255, Math.round((rgb & 0xFF) * DYE_LIFT));
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	/** Draws the Rig over a player: the plates, then their lights. */
	static final class Layer extends RenderLayer<AvatarRenderState, PlayerModel> {
		private final Suit wide, slim;
		/** (0.12.2) One piece's plates only, for a suit dyed piece by piece. */
		private final Suit[] wideParts = new Suit[PIECES.length], slimParts = new Suit[PIECES.length];

		Layer(RenderLayerParent<AvatarRenderState, PlayerModel> renderer) {
			super(renderer);
			this.wide = new Suit(false, this::getParentModel, -1);
			this.slim = new Suit(true, this::getParentModel, -1);
			for (int i = 0; i < PIECES.length; i++) {
				this.wideParts[i] = new Suit(false, this::getParentModel, i);
				this.slimParts[i] = new Suit(true, this::getParentModel, i);
			}
		}

		@Override
		public void submit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, AvatarRenderState state, float yRot, float xRot) {
			if (state.getData(AGES) == null) {
				return;
			}
			PlayerModel parent = getParentModel();
			state.setData(PARENT, parent);
			boolean slimArms = slimArm(parent.rightArm, state.skin.model() == PlayerModelType.SLIM);
			Suit suit = slimArms ? this.slim : this.wide;
			int[] colors = state.getData(COLORS);
			if (colors == null || same(colors)) {
				renderColoredCutoutModel(suit, TEXTURE, poseStack, collector, lightCoords, state, tint(colors == null ? -1 : colors[0]), 1);
			} else {
				// dyed piece by piece: each piece's plates in its own colour
				Suit[] parts = slimArms ? this.slimParts : this.wideParts;
				for (int i = 0; i < parts.length; i++) {
					renderColoredCutoutModel(parts[i], TEXTURE, poseStack, collector, lightCoords, state, tint(colors[i]), 1);
				}
			}
			Integer glow = state.getData(GLOW);
			collector.order(1).submitModel(suit, state, poseStack, RenderTypes.eyes(GLOW_TEXTURE), lightCoords, OverlayTexture.NO_OVERLAY,
				glow == null ? 0xFFFFFFFF : glow, null, 0);
		}

		private static boolean same(int[] colors) {
			for (int c : colors) {
				if (c != colors[0]) {
					return false;
				}
			}
			return true;
		}
	}

	/** The suit's model: the player's bones, posed like the wearer's, with every plate a part on them. */
	static final class Suit extends PlayerModel {
		private final Supplier<PlayerModel> wearer;
		private final boolean slim;
		private final ModelPart[] plates;
		private final boolean[] arm;
		/** (0.11.107) Which plates are the launcher's pods. */
		private final boolean[] pod;
		/** (0.11.110) Which plate is the faceplate. */
		private final boolean[] face;
		/** (0.12.2) The one piece (Rig.Piece's ordinal) this draws, or -1 for the whole suit. */
		private final int only;

		Suit(boolean slim, Supplier<PlayerModel> wearer, int only) {
			super(RigSuitMesh.create(slim).bakeRoot(), slim);
			this.wearer = wearer;
			this.slim = slim;
			this.only = only;
			this.plates = new ModelPart[RigSuitMesh.PLATES.length];
			this.arm = new boolean[this.plates.length];
			this.pod = new boolean[this.plates.length];
			this.face = new boolean[this.plates.length];
			for (int i = 0; i < this.plates.length; i++) {
				this.pod[i] = RigSuitMesh.PLATES[i][1].startsWith("pod_");
				this.face[i] = RigSuitMesh.PLATES[i][1].equals("helm_face");
				this.plates[i] = root().getChild(RigSuitMesh.PLATES[i][0]).getChild(RigSuitMesh.PLATES[i][1]);
				this.arm[i] = RigSuitMesh.PLATES[i][0].endsWith("_arm");
			}
			hideSkinLayers();
		}

		@Override
		public void setupAnim(AvatarRenderState state) {
			resetPose();
			// the model this player was drawn with, animated for them; then the body as Fresh Animations moved it
			PlayerModel kept = state.getData(PARENT);
			PlayerModel parent = kept != null ? kept : this.wearer.get();
			parent.setupAnim(state);
			ModelPart[] from = parts(parent);
			ModelPart[] to = parts(this);
			for (int i = 0; i < to.length; i++) {
				to[i].loadPose(from[i].storePose());
				to[i].xScale = from[i].xScale;
				to[i].yScale = from[i].yScale;
				to[i].zScale = from[i].zScale;
				to[i].visible = from[i].visible;
			}
			PartPose[] body = state.getData(BODY);
			if (body != null) {
				for (int i = 0; i < to.length; i++) {
					to[i].loadPose(body[i]);
				}
			}
			hideSkinLayers();
			assemble(state.getData(AGES));
			Float face = state.getData(FACE);
			if (face != null && face > 0.0F) {
				// (0.11.110) the faceplate swung up off the face
				for (int i = 0; i < this.plates.length; i++) {
					if (this.face[i]) {
						this.plates[i].xRot -= FACE_LIFT * face;
					}
				}
			}
			Float pod = state.getData(POD);
			if (pod != null && pod > 0.0F) {
				for (int i = 0; i < this.plates.length; i++) {
					if (this.pod[i] && this.plates[i].visible) {
						this.plates[i].y -= POD_RISE * pod;
						this.plates[i].xRot += POD_TIP * pod;
					}
				}
			}
		}

		/** Each plate as far as its piece's build has brought it: hidden, swinging in, locking, or on. */
		private void assemble(float[] ages) {
			for (int i = 0; i < this.plates.length; i++) {
				ModelPart part = this.plates[i];
				float age = ages == null || this.only >= 0 && RigSuitMesh.PIECE[i] != this.only ? -1.0F : ages[RigSuitMesh.PIECE[i]];
				float[] time = RigSuitMesh.TIME[i];
				float p = age < 0.0F ? -1.0F : (age - time[0]) / time[1];
				if (p <= 0.0F) {
					part.visible = false;
					continue;
				}
				float[] came = RigSuitMesh.CAME[i];
				float scale = 1.0F;
				if (p < 1.0F) {
					// on its way in (or out): what's left of the way, overshooting the lock a little before it settles
					float e = backOut(p);
					float k = 1.0F - e;
					part.x += came[0] * k;
					part.y += came[1] * k;
					part.z += came[2] * k;
					part.xRot += came[3] * k;
					part.yRot += came[4] * k;
					part.zRot += came[5] * k;
					scale = came[6] + (1.0F - came[6]) * e;
				} else {
					float since = age - time[0] - time[1];
					if (since < CLUNK_TICKS) {
						// the clunk: it swells and is driven home, then sits
						float c = 1.0F - since / CLUNK_TICKS;
						float hard = RigSuitMesh.HEAVY[i] ? HEAVY_CLUNK : 1.0F;
						scale = 1.0F + CLUNK_SCALE * hard * c * c;
						part.y += CLUNK_DROP * hard * c * Mth.sin(since / CLUNK_TICKS * Mth.PI);
					}
				}
				part.visible = scale > 0.02F;
				part.xScale = Math.max(0.0F, scale) * (this.slim && this.arm[i] ? SLIM_ARM : 1.0F);
				part.yScale = Math.max(0.0F, scale);
				part.zScale = Math.max(0.0F, scale);
			}
		}

		/** The skin's outer layers have no part in the suit. */
		private void hideSkinLayers() {
			this.hat.visible = false;
			this.jacket.visible = false;
			this.leftSleeve.visible = false;
			this.rightSleeve.visible = false;
			this.leftPants.visible = false;
			this.rightPants.visible = false;
		}
	}
}
