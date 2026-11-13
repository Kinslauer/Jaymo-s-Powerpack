package com.jaymoskingdoms.client.creatures;

import com.jaymoskingdoms.JaymosKingdoms;
import com.jaymoskingdoms.blossomland.Unburied;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import org.jspecify.annotations.Nullable;

/**
 * The Unburied, drawn as the dead it is made of ({@link Unburied#SLOTS}): each body rebuilt from its saved data, as a
 * Kitchen corpse is, and drawn in its place on the giant's frame, the frame moving as the bone giant's model does (legs
 * and arms swinging with its stride, the head turning to look). With Kitchen the bodies are drawn as its corpses are:
 * limbs splayed, eyes shut, lost parts missing. Coming apart at daybreak it shudders and sags as they drop out of it.
 * One raised from an egg, with no dead in it, is drawn as the old giant of bones.
 */
final class UnburiedRenderer extends MobRenderer<Unburied, UnburiedRenderer.State, CreatureModel> {
	/** Swing of the frame's limbs, as {@link CreatureModel}'s "legR"/"armR" animations. */
	private static final float LEG_SWING = 1.4F, ARM_SWING = 1.2F, STRIDE = 0.6662F;
	/** Coming apart: how hard it shakes and how far it sags (blocks) by the time it's gone. */
	private static final float SHAKE = 0.06F, SAG = 0.4F;
	/** A body's own size is fitted to its place: no smaller or bigger than this share of what it is. */
	private static final float MIN_FIT = 0.5F, MAX_FIT = 1.6F;
	/** Rebuilt bodies' ids: negative, well clear of Kitchen's corpse bodies and the nightmare phantoms. */
	private static final int ID_BASE = -50_000_000;

	static final class State extends CreatureState {
		boolean built;
		int apart;
		final @Nullable EntityRenderState[] bodies = new EntityRenderState[Unburied.DRAWN];
		@SuppressWarnings("rawtypes")
		final @Nullable EntityRenderer[] renderers = new EntityRenderer[Unburied.DRAWN];
		final float[] fit = new float[Unburied.DRAWN];
		final float[] height = new float[Unburied.DRAWN];
	}

	/** What's kept on the giant of its rebuilt bodies: the look each was rebuilt from, and the body. */
	private static final class Drawn {
		final String[] looks = new String[Unburied.DRAWN];
		final @Nullable Entity[] bodies = new Entity[Unburied.DRAWN];
	}

	private final Identifier texture = JaymosKingdoms.id("textures/entity/creatures/unburied.png");
	private final CreatureModel.Spec spec;

	UnburiedRenderer(EntityRendererProvider.Context context, CreatureModel.Spec spec) {
		super(context, new CreatureModel(CreatureModel.layer(spec).bakeRoot(), spec), 1.4F);
		this.spec = spec;
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(Unburied giant, State state, float partialTicks) {
		super.extractRenderState(giant, state, partialTicks);
		state.built = giant.took() > 0;
		state.apart = giant.apart();
		java.util.Arrays.fill(state.bodies, null);
		java.util.Arrays.fill(state.renderers, null);
		if (!state.built) {
			return;
		}
		Drawn drawn = giant.drawn instanceof Drawn d ? d : new Drawn();
		giant.drawn = drawn;
		for (int i = 0; i < Unburied.DRAWN; i++) {
			String look = giant.bodyLook(i);
			if (!look.equals(drawn.looks[i])) {
				drawn.looks[i] = look;
				drawn.bodies[i] = rebuild(giant.level(), look, ID_BASE - giant.getId() * Unburied.DRAWN - i);
			}
			Entity body = drawn.bodies[i];
			if (body == null) {
				continue;
			}
			stand(body, giant, partialTicks);
			EntityRenderer<? super Entity, ?> renderer = this.entityRenderDispatcher.getRenderer(body);
			EntityRenderState bodyState = renderer.createRenderState(body, partialTicks);
			bodyState.lightCoords = state.lightCoords;
			bodyState.outlineColor = state.outlineColor;
			bodyState.nameTag = null;
			bodyState.displayFireAnimation = false;
			if (bodyState instanceof LivingEntityRenderState living) {
				living.bodyRot = 0.0F;
				living.yRot = 0.0F;
				living.xRot = 0.0F;
				living.deathTime = 0.0F;
				living.walkAnimationPos = 0.0F;
				living.walkAnimationSpeed = 0.0F;
				// struck, the dead it's made of flash with it
				living.hasRedOverlay = state.hasRedOverlay;
			}
			KitchenLooks.dead(bodyState, missing(look), giant.getId() * 31L + i);
			state.bodies[i] = bodyState;
			state.renderers[i] = renderer;
			// fitted to its place by its longer side
			state.height[i] = body.getBbHeight();
			float size = Math.max(body.getBbHeight(), body.getBbWidth());
			state.fit[i] = size <= 0.0F ? 1.0F : Mth.clamp(Unburied.SLOTS[i].length() / size, MIN_FIT, MAX_FIT);
		}
	}

	/** Puts a rebuilt body where the giant is, standing, facing nowhere in particular (it is turned into place when drawn). */
	private static void stand(Entity body, Unburied giant, float partialTicks) {
		double x = Mth.lerp(partialTicks, giant.xo, giant.getX()), y = Mth.lerp(partialTicks, giant.yo, giant.getY()),
			z = Mth.lerp(partialTicks, giant.zo, giant.getZ());
		body.setPos(x, y, z);
		body.xo = x;
		body.yo = y;
		body.zo = z;
		body.xOld = x;
		body.yOld = y;
		body.zOld = z;
		body.setYRot(0.0F);
		body.yRotO = 0.0F;
		body.setXRot(0.0F);
		body.xRotO = 0.0F;
		if (body instanceof LivingEntity living) {
			living.setYBodyRot(0.0F);
			living.yBodyRotO = 0.0F;
			living.setYHeadRot(0.0F);
			living.yHeadRotO = 0.0F;
			living.deathTime = 0;
			living.hurtTime = 0;
		}
	}

	/** The parts the body had lost (Kitchen's bits), from its synced look. */
	private static int missing(String look) {
		int bar = look.indexOf('|');
		try {
			return bar > 0 ? Integer.parseInt(look.substring(0, bar)) : 0;
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** The dead mob, rebuilt from its saved data (as Kitchen rebuilds a corpse's), not in the world; null for none. */
	private static @Nullable Entity rebuild(Level level, String look, int id) {
		int bar = look.indexOf('|');
		if (bar < 0 || bar == look.length() - 1) {
			return null;
		}
		try {
			CompoundTag tag = TagParser.parseCompoundFully(look.substring(bar + 1));
			Identifier typeId = Identifier.tryParse(tag.getStringOr("id", ""));
			Optional<EntityType<?>> type = typeId == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(typeId);
			if (type.isEmpty()) {
				return null;
			}
			Entity entity = type.get().create(level, EntitySpawnReason.LOAD);
			if (entity == null) {
				return null;
			}
			ValueInput input = TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag);
			entity.load(input);
			// the client reads no attributes on its own: the body's size (scale) comes from them
			if (entity instanceof LivingEntity living) {
				input.read("attributes", AttributeInstance.Packed.LIST_CODEC).ifPresent(living.getAttributes()::apply);
			}
			entity.setId(id);
			return entity;
		} catch (Exception e) {
			JaymosKingdoms.LOGGER.debug("An Unburied's body could not be rebuilt to draw: {}", e.toString());
			return null;
		}
	}

	@Override
	protected void scale(State state, PoseStack poseStack) {
		poseStack.scale(this.spec.scale(), this.spec.scale(), this.spec.scale());
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return this.texture;
	}

	@Override
	protected @Nullable RenderType getRenderType(State state, boolean bodyVisible, boolean forceTransparent, boolean glowing) {
		// made of its dead, there are no bones to draw: only them
		return state.built ? null : super.getRenderType(state, bodyVisible, forceTransparent, glowing);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		super.submit(state, poseStack, collector, camera);
		if (!state.built) {
			return;
		}
		poseStack.pushPose();
		if (state.apart > 0) {
			// coming apart: shuddering, sagging as its dead leave it
			float t = state.ageInTicks;
			float sag = Math.min(1.0F, state.apart / 60.0F);
			poseStack.translate(Mth.sin(t * 2.3F) * SHAKE, -SAG * sag, Mth.cos(t * 2.9F) * SHAKE);
		}
		// its own frame: x to its right, y up, z backward (as the giant's model is turned)
		poseStack.rotate(Axis.YP.rotationDegrees(180.0F - state.bodyRot));
		for (int i = 0; i < Unburied.DRAWN; i++) {
			EntityRenderState body = state.bodies[i];
			EntityRenderer<?, ?> renderer = state.renderers[i];
			if (body == null || renderer == null) {
				continue;
			}
			Unburied.Slot slot = Unburied.SLOTS[i];
			poseStack.pushPose();
			float[] pivot = Unburied.PIVOTS[slot.part()];
			poseStack.translate(pivot[0], pivot[1], pivot[2]);
			move(state, slot.part(), poseStack);
			poseStack.translate(slot.x(), slot.y(), slot.z());
			lay(slot.lay(), poseStack);
			float fit = state.fit[i];
			poseStack.scale(fit, fit, fit);
			// the body is drawn from its feet: its middle on the place
			poseStack.translate(0.0F, -state.height[i] / 2.0F, 0.0F);
			// a body drawn facing nowhere faces the frame's back: turned to face its front
			poseStack.rotate(Axis.YP.rotationDegrees(180.0F));
			submitBody(renderer, body, poseStack, collector, camera);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	/** The frame part's own motion, as the bone giant's model moves it (model rotations, mirrored into this frame). */
	private static void move(State state, int part, PoseStack poseStack) {
		float walk = state.walkAnimationPos * STRIDE, speed = state.walkAnimationSpeed, age = state.ageInTicks;
		switch (part) {
			case Unburied.HEAD -> {
				poseStack.rotate(Axis.YP.rotationDegrees(-state.yRot));
				poseStack.rotate(Axis.XP.rotationDegrees(-state.xRot));
			}
			case Unburied.LEG_R -> poseStack.rotate(Axis.XP.rotation(-Mth.cos(walk) * LEG_SWING * speed));
			case Unburied.LEG_L -> poseStack.rotate(Axis.XP.rotation(-Mth.cos(walk + Mth.PI) * LEG_SWING * speed));
			case Unburied.ARM_R -> {
				poseStack.rotate(Axis.XP.rotation(-Mth.cos(walk + Mth.PI) * ARM_SWING * speed));
				poseStack.rotate(Axis.ZP.rotation(Mth.cos(age * 0.09F) * 0.05F + 0.05F));
			}
			case Unburied.ARM_L -> {
				poseStack.rotate(Axis.XP.rotation(-Mth.cos(walk) * ARM_SWING * speed));
				poseStack.rotate(Axis.ZP.rotation(-(Mth.cos(age * 0.09F) * 0.05F + 0.05F)));
			}
			default -> {
			}
		}
	}

	/** Turns a standing body (feet down, face forward) to lie as its place has it, about its middle. */
	private static void lay(Unburied.Lay lay, PoseStack poseStack) {
		switch (lay) {
			case UP -> {
			}
			case UP_BACK -> poseStack.rotate(Axis.YP.rotationDegrees(180.0F));
			case DOWN -> poseStack.rotate(Axis.ZP.rotationDegrees(180.0F));
			case DOWN_BACK -> {
				poseStack.rotate(Axis.ZP.rotationDegrees(180.0F));
				poseStack.rotate(Axis.YP.rotationDegrees(180.0F));
			}
			// the frame's right is +x: head that way
			case ACROSS_R -> poseStack.rotate(Axis.ZP.rotationDegrees(-90.0F));
			case ACROSS_L -> poseStack.rotate(Axis.ZP.rotationDegrees(90.0F));
			// head forward (-z), belly down
			case ALONG -> poseStack.rotate(Axis.XP.rotationDegrees(-90.0F));
		}
	}

	@SuppressWarnings("unchecked")
	private static <S extends EntityRenderState> void submitBody(EntityRenderer<?, S> renderer, EntityRenderState state, PoseStack poseStack,
		SubmitNodeCollector collector, CameraRenderState camera) {
		renderer.submit((S) state, poseStack, collector, camera);
	}

	/**
	 * Kitchen's way of drawing the dead, when Kitchen is loaded (its client classes, reached by reflection): limbs
	 * splayed as a body lying sprawled ({@code Ragdoll}), eyes shut ({@code ClosedEyes}), lost parts not drawn
	 * ({@code Severed}). Without it, bodies are drawn as they stood.
	 */
	private static final class KitchenLooks {
		private static final @Nullable RenderStateDataKey<Object> RAGDOLL, CLOSED_EYES, SEVERED;
		private static final @Nullable Method RAGDOLL_OF;

		static {
			RenderStateDataKey<Object> ragdoll = null, closed = null, severed = null;
			Method ragdollOf = null;
			if (FabricLoader.getInstance().isModLoaded("jaymoskitchen")) {
				try {
					Class<?> r = Class.forName("com.jaymoskitchen.client.corpse.Ragdoll");
					ragdoll = key(r.getField("KEY"));
					ragdollOf = r.getMethod("of", long.class);
					closed = key(Class.forName("com.jaymoskitchen.client.corpse.ClosedEyes").getField("KEY"));
					severed = key(Class.forName("com.jaymoskitchen.client.corpse.Severed").getField("KEY"));
				} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
					JaymosKingdoms.LOGGER.warn("Jaymo's Kitchen is too old to draw the Unburied's dead as corpses: {}", e.toString());
					ragdoll = null;
				}
			}
			RAGDOLL = ragdoll;
			RAGDOLL_OF = ragdoll == null ? null : ragdollOf;
			CLOSED_EYES = ragdoll == null ? null : closed;
			SEVERED = ragdoll == null ? null : severed;
		}

		@SuppressWarnings("unchecked")
		private static RenderStateDataKey<Object> key(Field field) throws IllegalAccessException {
			return (RenderStateDataKey<Object>) field.get(null);
		}

		static void dead(EntityRenderState state, int missing, long seed) {
			if (RAGDOLL == null || RAGDOLL_OF == null || CLOSED_EYES == null || SEVERED == null) {
				return;
			}
			try {
				state.setData(RAGDOLL, RAGDOLL_OF.invoke(null, seed));
				state.setData(CLOSED_EYES, Boolean.TRUE);
				state.setData(SEVERED, missing == 0 ? null : missing);
			} catch (ReflectiveOperationException | RuntimeException e) {
				// drawn as it stood
			}
		}
	}
}
