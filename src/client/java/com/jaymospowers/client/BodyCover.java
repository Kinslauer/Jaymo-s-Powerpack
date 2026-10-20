package com.jaymospowers.client;

import com.jaymospowers.client.costume.CostumeClient;
import com.jaymospowers.client.power.web.WebSuitGrowth;
import com.jaymospowers.costume.Costume;
import com.jaymospowers.costume.CostumeDesign;
import com.jaymospowers.power.web.WebSymbiote;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.jspecify.annotations.Nullable;

/**
 * (0.12.5) What a costume or the symbiote's suit covers on a player, so nothing under it pokes through or flickers
 * against it (Andy: costumes hide as much underneath as they can). Worked out once per frame on the render state, after
 * every power and the costume have dressed it ({@code BodyCoverAvatarRendererMixin}):
 * <ul>
 * <li>the skin's own outer layer (hat, jacket, sleeves, trousers) goes under what covers it: it sits at 0.25-0.5
 * pixels, where the costume's suit (0.3) and mask (0.55) flicker against it and the symbiote's suit (0.12) is under
 * it;</li>
 * <li>what hangs off the body is checked by its layer: a Warrior-born's tail, the Xeno-Band ({@link #body}); Super
 * hair, antennae, head fins ({@link #head}); glowing eyes and marks on the face ({@link #face}).</li>
 * </ul>
 * Jaymo's Kingdoms reads the same answers by reflection (its {@code PowersCover}) for the heritage looks, so these
 * three take the render state as an Object and never change their signatures.
 */
public final class BodyCover {
	/**
	 * What's covered: the skin's hat layer (any mask), the head and hair (a mask that covers the whole head, a hood), the
	 * face (a mask that hides who you are), the body and limbs (a costume's suit, the symbiote's).
	 */
	public record Cover(boolean hat, boolean head, boolean face, boolean body) {
		static final Cover NONE = new Cover(false, false, false, false);
		static final Cover ALL = new Cover(true, true, true, true);
	}

	/** (0.13.23) The symbiote's suit with its mask off: the body covered, the head and face bare. */
	private static final Cover FACE_BARE = new Cover(false, false, false, true);

	public static final RenderStateDataKey<Cover> KEY = RenderStateDataKey.create(() -> "jaymospowers:body_cover");

	private BodyCover() {
	}

	/** From the avatar renderer's extractRenderState, after the costume and the Rig have had it. */
	public static void extract(Avatar entity, AvatarRenderState state) {
		Cover cover = of(state);
		state.setData(KEY, cover);
		if (cover.hat()) {
			state.showHat = false;
		}
		if (cover.body()) {
			state.showJacket = false;
			state.showLeftSleeve = false;
			state.showRightSleeve = false;
			state.showLeftPants = false;
			state.showRightPants = false;
		}
	}

	private static Cover of(AvatarRenderState state) {
		if (state.isInvisible) {
			// (a Xeno-Band form, the Great Ape: the body isn't drawn, nor anything that checks for that)
			return Cover.NONE;
		}
		if (WebSuitGrowth.covered(state)) {
			// the symbiote's suit (or its big body), or most of the way to it; (0.13.23) its mask off, the head and face show
			return WebSuitGrowth.faceShown(state) ? FACE_BARE : Cover.ALL;
		}
		CostumeDesign design = state.getData(CostumeClient.KEY);
		if (design == null || CostumeClient.covered(state)) {
			return Cover.NONE;
		}
		CostumeDesign.Mask mask = design.mask();
		boolean head = mask.hidesFace || mask.isHood();
		return new Cover(mask != CostumeDesign.Mask.NONE, head, mask.hidesFace, true);
	}

	private static Cover get(@Nullable Object state) {
		Cover cover = state instanceof AvatarRenderState avatar ? avatar.getData(KEY) : null;
		return cover != null ? cover : Cover.NONE;
	}

	/** Whether the head and hair are covered: nothing that grows out of the head (hair, antennae, fins) is drawn. */
	public static boolean head(@Nullable Object state) {
		return get(state).head();
	}

	/** Whether the face is covered: no glowing eyes, marks or fangs. */
	public static boolean face(@Nullable Object state) {
		return get(state).face();
	}

	/** Whether the body and limbs are covered: nothing on them, nor hanging off them (a tail), is drawn. */
	public static boolean body(@Nullable Object state) {
		return get(state).body();
	}

	/**
	 * First person: whether your own arms are covered (the costume's sleeve or the suit's is drawn over them), so the
	 * skin's sleeve layer isn't drawn under it (it would show through the symbiote's, flicker against the costume's).
	 */
	public static boolean ownArms() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || player.isInvisible()) {
			return false;
		}
		return WebSymbiote.suitShown(player) || WebSymbiote.bulked(player) || Costume.worn(player) != null;
	}
}
