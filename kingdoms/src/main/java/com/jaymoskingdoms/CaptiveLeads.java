package com.jaymoskingdoms;

import com.jaymoskingdoms.forms.Lasso;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * A rope to lead people. The lasso ({@link Lasso}) already drags a hogtied captive after you; this is the rest of the lead.
 * <ul>
 * <li><b>Tied off.</b> Using the lasso on a fence or a wall while you have a hogtied or collared (shackled,
 * {@link Shackles}) captive on the rope ties the rope's end to that post: you let go and they stay, the rope drawn from them
 * to the post ({@link Shackles#TETHER}, for villagers too). They can't be dragged further than {@link #REACH} from it (a
 * stretchy lead: the nearer the end, the harder it pulls back, {@link #PULL}). The post goes and the rope drops.</li>
 * <li><b>Taken up.</b> Using the lasso on someone tied off takes the lead up again.</li>
 * <li><b>Collared.</b> A shackled captive led on the rope isn't pulled back to their camp's stake: their chain follows where they
 * are led ({@link StoryHard#moveAnchor}); let go in the open, a new post is driven in beside them ({@link Shackles#tie}), as
 * the camps do. Cutting the rope from a post is as it always was ({@link Shackles}).</li>
 * </ul>
 * The knots a hogtie is in are the lasso's own; nothing here frees anyone. Nothing here is saved but the rope drawn.
 */
public final class CaptiveLeads {
	/** How far from the post (blocks) the one tied off may go before the rope pulls, how hard it pulls per block over, and where it stops pulling and carries them. */
	static final double REACH = 3.0, PULL = 0.2, PULL_MOST = 0.5, SNAP_BACK = 6.0;
	/** Ticks between looks at those tied off and those led. */
	static final int EVERY = 5;

	/** Who is tied off, and to which post. */
	private static final Map<UUID, BlockPos> POSTED = new HashMap<>();
	/** Shackled players currently led on a rope. */
	private static final Set<UUID> LED = new HashSet<>();

	private CaptiveLeads() {
	}

	static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server) || !sp.getMainHandItem().is(Lasso.ITEM)) {
				return InteractionResult.PASS;
			}
			var state = level.getBlockState(hit.getBlockPos());
			if (!state.is(BlockTags.FENCES) && !state.is(BlockTags.WALLS)) {
				return InteractionResult.PASS;
			}
			LivingEntity held = Lasso.heldBy(sp);
			if (held == null || !(Lasso.hogtied(held) || held.hasAttached(Shackles.TETHER))) {
				return InteractionResult.PASS;
			}
			tieOff(server, sp, held, hit.getBlockPos());
			return InteractionResult.SUCCESS;
		});
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp) || !(level instanceof ServerLevel server) || player.isShiftKeyDown()
				|| !sp.getMainHandItem().is(Lasso.ITEM) || !(entity instanceof LivingEntity body) || !POSTED.containsKey(body.getUUID())) {
				return InteractionResult.PASS;
			}
			untie(body);
			Lasso.takeUp(server, sp, body);
			return InteractionResult.SUCCESS;
		});
		// (a rope left over from before a restart, on someone no longer held by anything)
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof LivingEntity && entity.hasAttached(Shackles.TETHER) && !StoryHard.shackled(entity)) {
				entity.removeAttached(Shackles.TETHER);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(CaptiveLeads::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			POSTED.clear();
			LED.clear();
		});
	}

	/** Whether {@code id} is tied off to a post. */
	static boolean posted(UUID id) {
		return POSTED.containsKey(id);
	}

	private static void tieOff(ServerLevel level, ServerPlayer holder, LivingEntity held, BlockPos post) {
		Lasso.letGo(held);
		held.setAttached(Shackles.TETHER, new Shackles.Tether(post, true));
		POSTED.put(held.getUUID(), post);
		if (held instanceof ServerPlayer captive && StoryHard.shackled(captive)) {
			StoryHard.moveAnchor(captive, post);
			LED.remove(captive.getUUID());
		}
		level.playSound(null, post, SoundEvents.LEAD_TIED, SoundSource.PLAYERS, 1.0F, 0.9F);
		holder.sendOverlayMessage(Component.translatable("message.jaymoskingdoms.lead.tied_off", held.getDisplayName()).withStyle(ChatFormatting.GRAY));
	}

	/** Takes the rope off the post (a collared player keeps their own chain's tether). */
	private static void untie(LivingEntity body) {
		POSTED.remove(body.getUUID());
		body.removeAttached(Shackles.TETHER);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % EVERY != 0) {
			return;
		}
		for (Map.Entry<UUID, BlockPos> e : new ArrayList<>(POSTED.entrySet())) {
			LivingEntity body = null;
			for (ServerLevel level : server.getAllLevels()) {
				if (level.getEntity(e.getKey()) instanceof LivingEntity found) {
					body = found;
					break;
				}
			}
			BlockPos post = e.getValue();
			if (body == null || !body.isAlive()) {
				POSTED.remove(e.getKey());
				continue;
			}
			boolean bound = Lasso.hogtied(body) || StoryHard.shackled(body);
			boolean standing = body.level().getBlockState(post).is(BlockTags.FENCES) || body.level().getBlockState(post).is(BlockTags.WALLS);
			if (!bound || !standing) {
				// (they worked free, or the post is gone: the rope drops)
				untie(body);
				continue;
			}
			Vec3 to = Vec3.atBottomCenterOf(post).subtract(body.position());
			double over = Math.sqrt(to.x * to.x + to.z * to.z) - REACH;
			if (over > 0.0) {
				if (over > SNAP_BACK) {
					body.teleportTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5);
				} else {
					Vec3 flat = new Vec3(to.x, 0.0, to.z).normalize().scale(Math.min(PULL_MOST, over * PULL));
					body.setDeltaMovement(body.getDeltaMovement().add(flat));
					body.syncVelocity = true;
				}
			}
		}
		// (a collared captive led on a rope: their chain goes with them; let go, a new post is driven in beside them)
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (!StoryHard.shackled(p) || POSTED.containsKey(p.getUUID())) {
				LED.remove(p.getUUID());
				continue;
			}
			if (Lasso.holderOf(p) != null) {
				LED.add(p.getUUID());
				p.removeAttached(Shackles.TETHER);
				StoryHard.moveAnchor(p, p.blockPosition());
			} else if (LED.remove(p.getUUID())) {
				Shackles.tie(p, p.blockPosition(), p.getRandom());
			}
		}
	}
}
