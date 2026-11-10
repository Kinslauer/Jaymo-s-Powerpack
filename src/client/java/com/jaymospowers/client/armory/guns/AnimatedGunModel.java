package com.jaymospowers.client.armory.guns;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.armory.GunBlast;
import com.jaymospowers.power.arsenal.ArsenalFlintlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Item model type {@code jaymospowers:animated_gun}: a flintlock gun in the hand, drawn by GeckoLib and animated
 * ({@link GunRenderer}). The held_guns pack's musket, bayonet muskets, blunderbuss and flintlock, and the Void
 * Flintlock, use it for every hand-held view (their flat pictures stay in the inventory, on the ground and in frames).
 * <pre>
 * {"type": "jaymospowers:animated_gun",
 *  "gun": "musket",                            the GeckoLib model (gun_musket)
 *  "base": "jaymospowers:item/gun/musket",     whose hand-held placements and particle it keeps
 *  "transformation": [16 numbers],             where the model lies in the item (tools/gen_guns_geckolib.py)
 *  "texture": "...", "hide": ["bayonet"],      optional: another skin, bones left out
 *  "pose": "loaded" | "spent" | "reload"}      optional: WeaponMod's state, by its own conditions; without it
 *                                              the Void Flintlock's spent mark decides
 * </pre>
 * Each hand holding one has its own animation: aimed while it's held ready to fire, the fire when the holder's
 * {@link GunBlast#SHOT} is that hand's, the draw when it comes into the hand.
 */
public final class AnimatedGunModel implements ItemModel {
	public static final Identifier ID = JaymosPowers.id("animated_gun");
	/** A shot older than this (ticks) isn't started any more: it's long over. */
	static final int SHOT_WINDOW = 20;
	/** A gun not drawn for this long (ticks) is drawn again next time it's seen in that hand. */
	static final int DRAW_GAP = 5;

	private static final SpecialModelRenderer<GunState> RENDERER = new SpecialModelRenderer<>() {
		@Override
		public void submit(@Nullable GunState state, PoseStack poseStack, SubmitNodeCollector collector, int light, int overlay, boolean foil,
			int outline) {
			if (state != null) {
				state.renderer().submit(state, poseStack, collector, light);
			}
		}

		@Override
		public void getExtents(Consumer<Vector3fc> output) {
		}

		@Override
		public @Nullable GunState extractArgument(ItemStack stack) {
			return null;
		}
	};

	/** Per hand (instance id): when it was last drawn, from which hotbar slot, and when its draw began. */
	private record Seen(long tick, int slot, long drawStart) {
	}

	private static final Map<Long, Seen> SEEN = new HashMap<>();

	private final GunRenderer renderer;
	private final ModelRenderProperties properties;
	private final Matrix4fc transformation;
	private final @Nullable Identifier texture;
	private final List<String> hidden;
	private final GunState.@Nullable Pose pose;

	private AnimatedGunModel(GunRenderer renderer, ModelRenderProperties properties, Matrix4fc transformation, @Nullable Identifier texture,
		List<String> hidden, GunState.@Nullable Pose pose) {
		this.renderer = renderer;
		this.properties = properties;
		this.transformation = transformation;
		this.texture = texture;
		this.hidden = hidden;
		this.pose = pose;
	}

	public static void init() {
		ItemModels.ID_MAPPER.put(ID, Unbaked.MAP_CODEC);
	}

	@Override
	public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver, ItemDisplayContext displayContext,
		@Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
		output.appendModelIdentityElement(this);
		output.setAnimated();
		ItemStackRenderState.LayerRenderState layer = output.newLayer();
		if (item.hasFoil()) {
			layer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
			output.appendModelIdentityElement(ItemStackRenderState.FoilType.STANDARD);
		}
		layer.setLocalTransform(this.transformation);
		this.properties.applyToLayer(layer, displayContext);
		layer.setupSpecialModel(RENDERER, state(item, owner == null ? null : owner.asLivingEntity()));
	}

	private GunState state(ItemStack item, @Nullable LivingEntity holder) {
		GunState.Pose pose = this.pose != null ? this.pose
			: Boolean.TRUE.equals(item.get(ArsenalFlintlock.SPENT)) ? GunState.Pose.SPENT : GunState.Pose.LOADED;
		Boolean main = holder == null ? null : hand(holder, item);
		if (main == null) {
			return new GunState(this.renderer, 0L, pose, false, -1L, -1L, this.texture, this.hidden);
		}
		long instance = (holder.getId() * 2L + (main ? 0 : 1)) + 1;
		boolean aiming = pose != GunState.Pose.RELOAD && holder.isUsingItem() && holds(holder.getUseItem(), item);
		GunBlast.Shot shot = holder.getAttached(GunBlast.SHOT);
		long shotStart = shot != null && shot.mainHand() == main && ticksSince(shot.time()) < SHOT_WINDOW ? shot.time() : -1L;
		return new GunState(this.renderer, instance, pose, aiming, shotStart, drawStart(instance, holder, main), this.texture, this.hidden);
	}

	/**
	 * Whether {@code item} is in the holder's main hand (true) or off hand (false), or null if in neither: the very stack
	 * if it's there, else (first person may draw a copy) the hand holding the same gun, the main hand if both do.
	 */
	private static @Nullable Boolean hand(LivingEntity holder, ItemStack item) {
		ItemStack main = holder.getMainHandItem(), off = holder.getOffhandItem();
		if (main == item || off == item) {
			return main == item;
		}
		if (ItemStack.isSameItem(main, item)) {
			return true;
		}
		return ItemStack.isSameItem(off, item) ? false : null;
	}

	private static boolean holds(ItemStack held, ItemStack item) {
		return held == item || (!held.isEmpty() && ItemStack.isSameItem(held, item));
	}

	/** When the gun in this hand was drawn: now, if it wasn't in the hand a moment ago or the hotbar moved to it. */
	private static long drawStart(long instance, LivingEntity holder, boolean main) {
		long now = now();
		int slot = main && holder instanceof Player player ? player.getInventory().getSelectedSlot() : -1;
		Seen seen = SEEN.get(instance);
		long start = seen == null || now - seen.tick() > DRAW_GAP || seen.slot() != slot ? now : seen.drawStart();
		if (SEEN.size() > 256) {
			SEEN.entrySet().removeIf(e -> now - e.getValue().tick() > DRAW_GAP);
		}
		SEEN.put(instance, new Seen(now, slot, start));
		return start;
	}

	static long ticksSince(long time) {
		return now() - time;
	}

	private static long now() {
		ClientLevel level = Minecraft.getInstance().level;
		return level == null ? 0L : level.getGameTime();
	}

	public record Unbaked(String gun, Identifier base, Optional<Transformation> transformation, Optional<Identifier> texture, List<String> hide,
		Optional<GunState.Pose> pose) implements ItemModel.Unbaked {
		private static final Codec<GunState.Pose> POSE_CODEC = Codec.STRING.xmap(s -> GunState.Pose.valueOf(s.toUpperCase(java.util.Locale.ROOT)),
			p -> p.name().toLowerCase(java.util.Locale.ROOT));
		public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
				Codec.STRING.fieldOf("gun").forGetter(Unbaked::gun),
				Identifier.CODEC.fieldOf("base").forGetter(Unbaked::base),
				Transformation.EXTENDED_CODEC.optionalFieldOf("transformation").forGetter(Unbaked::transformation),
				Identifier.CODEC.optionalFieldOf("texture").forGetter(Unbaked::texture),
				Codec.STRING.listOf().optionalFieldOf("hide", List.of()).forGetter(Unbaked::hide),
				POSE_CODEC.optionalFieldOf("pose").forGetter(Unbaked::pose))
			.apply(i, Unbaked::new));

		@Override
		public void resolveDependencies(Resolver resolver) {
			resolver.markDependency(this.base);
		}

		@Override
		public ItemModel bake(BakingContext context, Matrix4fc transformation) {
			ModelBaker baker = context.blockModelBaker();
			ResolvedModel model = baker.getModel(this.base);
			ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(baker, model, model.getTopTextureSlots());
			return new AnimatedGunModel(GunRenderer.of(this.gun), properties, Transformation.compose(transformation, this.transformation),
				this.texture.orElse(null), this.hide, this.pose.orElse(null));
		}

		@Override
		public MapCodec<Unbaked> type() {
			return MAP_CODEC;
		}
	}
}
