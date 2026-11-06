package com.jaymospowers.power.web;

import com.jaymospowers.combat.HitStreak;
import com.jaymospowers.core.Powers;
import com.jaymospowers.core.finisher.Finishers;
import com.jaymospowers.core.wounds.Downed;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * (0.21.25) Andy: "can all of web's unarmed moves go to the bar? symbiote included?" Web's bare-handed moves on the RPG
 * skill bar ({@code combat/Takedowns#moves}, shown with an empty hand while locked on), in a fixed order so a slot keeps
 * its key. Nine keys at most:
 * <ul>
 * <li>Any web slinger: Takedown, Finish (a downed foe: a plain finish mid-streak, or one of the symbiote's executions
 * while suited), Counter (in Spider-Sense's window after a dodge).</li>
 * <li>Without the symbiote: Webbed Fists.</li>
 * <li>Bonded: Weapon Arm (it forms and strikes at the foe), Grab (seize a foe up to a cow's size in the hand; holding
 * one, maul it; the tendrils full, a combo), and the radial's fighting picks: Tendril, Spikes, Consume, Shroud. The Suit
 * and Wall Crawl stay on X.</li>
 * </ul>
 * Each goes at the lock-on foe, else the one in sights, by the same checks as its own key.
 */
public final class WebBarMoves {
	public static final String TAKEDOWN = "takedown", FINISH = "finish", COUNTER = "counter", FISTS = "fists", ARM = "arm", GRAB = "grab",
		TENDRIL = "tendril", SPIKES = "spikes", CONSUME = "consume", SHROUD = "shroud";
	/** A finish reaches this far (blocks, past half the body's width). */
	static final double FINISH_REACH = 3.5;

	private static final List<String> PLAIN = List.of(TAKEDOWN, FINISH, COUNTER, FISTS);
	private static final List<String> BONDED = List.of(TAKEDOWN, FINISH, COUNTER, ARM, GRAB, TENDRIL, SPIKES, CONSUME, SHROUD);

	private WebBarMoves() {
	}

	/** {@code player}'s web moves for the bar, either side (the caller checks the hand and the lock). */
	public static List<String> moves(Player player) {
		if (!Powers.has(player, WebPower.INSTANCE)) {
			return List.of();
		}
		return WebSymbiote.isBonded(player) ? BONDED : PLAIN;
	}

	/** {@code move} from the bar at {@code target} (null: none in sights). False if it didn't go. */
	public static boolean cast(ServerPlayer player, String move, @Nullable LivingEntity target) {
		if (!moves(player).contains(move) || !player.isAlive() || Powers.silenced(player) || WebSymbiote.driving(player)) {
			return false;
		}
		int id = target == null ? -1 : target.getId();
		switch (move) {
			case TAKEDOWN -> {
				return target != null && WebTakedowns.takedown(player, target);
			}
			case FINISH -> {
				return target != null && finish(player, target);
			}
			case COUNTER -> {
				if (!WebCounter.ready(player)) {
					return false;
				}
				WebCounter.strike(player);
				return true;
			}
			case FISTS -> {
				WebStrength.fists(player);
				return true;
			}
			case ARM -> {
				if (!WebMorph.usable(player)) {
					return false;
				}
				// (pressed and let go at once: it strikes as soon as the weapon has formed)
				WebMorph.gather(player);
				WebMorph.release(player, id);
				return true;
			}
			case GRAB -> {
				if (WebHeldGrab.holding(player)) {
					WebHeldGrab.attack(player);
				} else if (player.getAttached(WebTendrils.GRIPS) != null && WebTendrilCombos.kind(player, target) >= 0) {
					WebTendrilCombos.attack(player, id);
				} else if (target != null) {
					WebHeldGrab.seize(player, id);
				} else {
					return false;
				}
				return true;
			}
			case TENDRIL -> {
				WebSymbiote.use(player, WebSymbiote.TENDRIL);
				return true;
			}
			case SPIKES -> {
				WebSymbiote.use(player, WebSymbiote.SPIKES);
				return true;
			}
			case CONSUME -> {
				WebSymbiote.use(player, WebSymbiote.CONSUME);
				return true;
			}
			case SHROUD -> {
				WebSymbiote.use(player, WebSymbiote.SHROUD);
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	/** A downed foe: one of the symbiote's executions while suited, else a plain finish mid-streak. */
	private static boolean finish(ServerPlayer player, LivingEntity target) {
		if (target instanceof Player || !target.isAlive() || !Finishers.helpless(target) || Finishers.targeted(target)
			|| target.distanceTo(player) - target.getBbWidth() * 0.5 > FINISH_REACH) {
			return false;
		}
		if (player.getMainHandItem().isEmpty() && WebExecutions.applies(player) && !WebExecutions.busy(player)) {
			WebExecutions.begin(player.level(), player, target);
			return true;
		}
		if (HitStreak.count(player) < WebTakedowns.GROUND_FROM) {
			player.sendOverlayMessage(Component.translatable("message.jaymospowers.takedown.short", WebTakedowns.GROUND_FROM).withStyle(ChatFormatting.GRAY));
			return false;
		}
		Vec3 at = target.getBoundingBox().getCenter();
		Downed.execute(player.level(), target, player);
		HitStreak.add(player);
		player.level().sendParticles(ParticleTypes.ITEM_COBWEB, at.x, at.y, at.z, 10, 0.3, 0.2, 0.3, 0.05);
		player.level().playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.7F);
		return true;
	}
}
