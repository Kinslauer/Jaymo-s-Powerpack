package com.jaymospowers.client.compat;

import com.jaymospowers.JaymosPowers;
import com.jaymospowers.client.CrouchPose;
import com.jaymospowers.core.Meditate;
import com.jaymospowers.core.wounds.Tending;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.bones.AdvancedPlayerAnimBone;
import com.zigythebird.playeranimcore.enums.PlayState;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.Objects;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The Player Animation Library calls behind {@link TakeASeatPoses}; only loaded when it and Take a Seat are. */
final class TakeASeatPal {
	/** Our two layers on every player: sitting (the whole body) and kneeling. */
	static final Identifier SIT = JaymosPowers.id("tas_sit"), KNEEL = JaymosPowers.id("tas_kneel");
	/** Above Take a Seat's own layer (1000), so ours shows if both play. */
	static final int SIT_PRIORITY = 1010, KNEEL_PRIORITY = 1005;
	/** How far a campfire or furnace may be looked at to sit by it (blocks), as Take a Seat has it. */
	static final double LOOK_REACH = 2.0;
	/** The bones a hands-free kneel (tending) leaves to our own pose. */
	static final String[] HANDS = {"right_arm", "left_arm", "right_item", "left_item"};

	// Take a Seat's animations, by its own names, grouped as it groups them (the first of each its first press)
	static final Identifier KNEESITTING = tas("kneesitting");
	static final Identifier[] GROUND = set("kneesitting", "buttsit", "buttsit2", "kneeleaning");
	static final Identifier[] CHAIR = set("chairsitting", "chairsitting2", "chairsitting3", "chairsitting4");
	static final Identifier[] FENCE = set("fencesitting", "fencesitting2");
	static final Identifier[] BED = set("bedlyingdown", "bedlyingdown2", "bedlyingdown3");
	static final Identifier[] SWORD = set("swordsit", "swordsit2");
	static final Identifier[] AXE = set("sittingaxe");
	static final Identifier[] SHOVEL = set("sittingshovel");
	static final Identifier[] FISHING = set("fishing");
	static final Identifier[] CAMPFIRE = set("campfiresit");
	static final Identifier[] FURNACE = set("furnacesit");

	/** One animation on a layer, and whether it leaves the hands to us. */
	private record Play(Identifier animation, boolean handsFree) {
	}

	private static final Int2ObjectOpenHashMap<Play> SAT = new Int2ObjectOpenHashMap<>(), KNELT = new Int2ObjectOpenHashMap<>();
	/** Bodies whose kneel leaves the hands to us (read when the kneel starts). */
	private static final IntOpenHashSet HANDS_FREE = new IntOpenHashSet();

	private TakeASeatPal() {
	}

	private static Identifier tas(String name) {
		return Identifier.fromNamespaceAndPath("takeaseat", name);
	}

	private static Identifier[] set(String... names) {
		Identifier[] out = new Identifier[names.length];
		for (int i = 0; i < names.length; i++) {
			out[i] = tas(names[i]);
		}
		return out;
	}

	static void init() {
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(SIT, SIT_PRIORITY,
			avatar -> new PlayerAnimationController(avatar, (controller, state, setter) -> PlayState.STOP));
		PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(KNEEL, KNEEL_PRIORITY, avatar -> {
			PlayerAnimationController controller = new PlayerAnimationController(avatar, (c, state, setter) -> PlayState.STOP);
			controller.setPostAnimationSetupConsumer(bones -> {
				if (HANDS_FREE.contains(avatar.getId())) {
					for (String name : HANDS) {
						AdvancedPlayerAnimBone bone = bones.apply(name);
						if (bone != null) {
							bone.setEnabled(false);
						}
					}
				}
			});
			return controller;
		});
		ClientTickEvents.END_CLIENT_TICK.register(TakeASeatPal::tick);
		noStairClick();
	}

	/** Take a Seat's own right-click on stairs would sit you a second way, beside our seats: it's turned off (not saved). */
	private static void noStairClick() {
		try {
			Object config = Class.forName("com.takeaseat.TakeASeatConfig").getMethod("getConfig").invoke(null);
			config.getClass().getField("enableClickToSit").setBoolean(config, false);
		} catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
			JaymosPowers.LOGGER.warn("Couldn't turn off Take a Seat's right-click on stairs", e);
		}
	}

	static boolean sitting(int id) {
		return SAT.containsKey(id);
	}

	static boolean kneeling(int id) {
		return KNELT.containsKey(id);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		if (level == null) {
			SAT.clear();
			KNELT.clear();
			HANDS_FREE.clear();
			return;
		}
		IntOpenHashSet seen = new IntOpenHashSet();
		for (AbstractClientPlayer player : level.players()) {
			seen.add(player.getId());
			try {
				Play sit = sitPose(player);
				play(player, SIT, sit, SAT);
				play(player, KNEEL, sit == null ? kneelPose(player) : null, KNELT);
			} catch (RuntimeException e) {
				SAT.remove(player.getId());
				KNELT.remove(player.getId());
			}
		}
		SAT.keySet().removeIf((int id) -> !seen.contains(id));
		KNELT.keySet().removeIf((int id) -> !seen.contains(id));
		HANDS_FREE.removeIf((int id) -> !seen.contains(id));
	}

	/** Starts, changes or stops {@code layer}'s animation on {@code player} to {@code want} (null: none). */
	private static void play(AbstractClientPlayer player, Identifier layer, @Nullable Play want, Int2ObjectOpenHashMap<Play> playing) {
		int id = player.getId();
		if (Objects.equals(playing.get(id), want)) {
			return;
		}
		if (!(PlayerAnimationAccess.getPlayerAnimationLayer(player, layer) instanceof PlayerAnimationController controller)) {
			return;
		}
		if (want == null || !PlayerAnimResources.hasAnimation(want.animation())) {
			// (an animation Take a Seat doesn't have: our own pose shows)
			controller.stopTriggeredAnimation();
			controller.stop();
			playing.remove(id);
			return;
		}
		if (want.handsFree()) {
			HANDS_FREE.add(id);
		} else {
			HANDS_FREE.remove(id);
		}
		if (controller.triggerAnimation(want.animation())) {
			playing.put(id, want);
		} else {
			playing.remove(id);
		}
	}

	/** Sitting ({@link Meditate}'s sitting, from the Sit key or a seat): Take a Seat's pose for it, the variant the key chose. */
	private static @Nullable Play sitPose(Player player) {
		Meditate.State m = Meditate.of(player);
		if (m == null || m.kind() != Meditate.MEDITATING || player.isDeadOrDying()) {
			return null;
		}
		Identifier[] set = sitSet(player, m.seated());
		return new Play(set[Math.floorMod(m.pose(), set.length)], false);
	}

	/** Take a Seat's choice, in its order: what you look at, what you hold, what you sit on. */
	private static Identifier[] sitSet(Player player, boolean seated) {
		Vec3 eye = player.getEyePosition();
		BlockHitResult look = player.level().clip(new ClipContext(eye, eye.add(player.getLookAngle().scale(LOOK_REACH)), ClipContext.Block.OUTLINE,
			ClipContext.Fluid.NONE, player));
		if (look.getType() == HitResult.Type.BLOCK) {
			Block looked = player.level().getBlockState(look.getBlockPos()).getBlock();
			if (looked instanceof CampfireBlock) {
				return CAMPFIRE;
			}
			if (looked instanceof AbstractFurnaceBlock) {
				return FURNACE;
			}
		}
		ItemStack held = player.getMainHandItem();
		if (held.is(ItemTags.SWORDS)) {
			return SWORD;
		}
		if (held.is(ItemTags.AXES)) {
			return AXE;
		}
		if (held.is(ItemTags.SHOVELS)) {
			return SHOVEL;
		}
		if (held.getItem() instanceof FishingRodItem) {
			return FISHING;
		}
		if (seated) {
			// (our seats: stairs, bottom slabs and other mods' chairs)
			return CHAIR;
		}
		BlockPos at = player.blockPosition();
		for (BlockPos pos : new BlockPos[] {at, at.below()}) {
			BlockState state = player.level().getBlockState(pos);
			if (state.getBlock() instanceof FenceBlock) {
				return FENCE;
			}
			if (state.getBlock() instanceof BedBlock) {
				return BED;
			}
		}
		return GROUND;
	}

	/** Kneeling: the low crouch held still (the whole body), or tending someone (the hands stay ours, at their work). */
	private static @Nullable Play kneelPose(Player player) {
		Tending.Tend tend = player.getAttached(Tending.TEND);
		if (tend != null && tend.target() != Tending.GROUND_DRINK) {
			return new Play(KNEESITTING, true);
		}
		return CrouchPose.kneeling(player.getId()) ? new Play(KNEESITTING, false) : null;
	}
}
