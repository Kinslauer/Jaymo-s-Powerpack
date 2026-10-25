package com.jaymoskingdoms;

import java.util.EnumSet;
import java.util.Set;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.interaction.Constraint;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * (0.7.203) MCA villagers only take orders from folk they're involved with: their family (spouse, betrothed, kin), whoever
 * hired them, or a friend of {@link #TRUSTED_HEARTS} hearts or more. To anyone else MCA's interact page hides Pick Up,
 * Locations, Follow Me, Stay Here, Ride Mount and Armor ({@code McaStrangerButtonMixin}), and the server refuses those
 * commands ({@code McaCommandMixin}). Gifts, talk and trade are for anyone. Only loaded with MCA.
 */
public final class Strangers {
	/** Hearts a villager must have for you before they'll follow, stay, carry you about or let you see to them (MCA's gold heart is 100). */
	public static final int TRUSTED_HEARTS = 50;
	/** MCA's own ties that count: family, kin, spouse, betrothed, promised, and hired help. */
	public static final Set<Constraint> TIES = EnumSet.of(Constraint.FAMILY, Constraint.RELATIVE, Constraint.SPOUSE, Constraint.ENGAGED,
		Constraint.PROMISED, Constraint.MERCENARY);
	/**
	 * MCA's commands a stranger can't give. "move" (let them go about their day) is left to anyone: it only shows while
	 * they already follow or stay.
	 */
	public static final Set<String> COMMANDS = Set.of("pick_up", "follow", "stay", "ridehorse", "armor", "sethome", "setworkplace", "gohome");
	/** The interact page's buttons for them, and Locations (the page holding the home and workplace ones). */
	public static final Set<String> BUTTONS = Set.of("gui.button.pick_up", "gui.button.locations", "gui.button.follow", "gui.button.stay",
		"gui.button.ridehorse", "gui.button.armor", "gui.button.sethome", "gui.button.setworkplace", "gui.button.gohome");

	private Strangers() {
	}

	/** Whether MCA's {@code constraints} for a villager and their {@code hearts} for you make you someone they're involved with. */
	public static boolean involved(Set<Constraint> constraints, int hearts) {
		return hearts >= TRUSTED_HEARTS || constraints.stream().anyMatch(TIES::contains);
	}

	public static boolean involved(VillagerEntityMCA villager, ServerPlayer player) {
		return villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts() >= TRUSTED_HEARTS
			|| TIES.stream().anyMatch(c -> c.test(villager, player));
	}

	/** {@code command} asked of {@code villager} by a stranger: refused, with a word over the hotbar. */
	public static boolean refuses(VillagerEntityMCA villager, ServerPlayer player, String command) {
		if (!COMMANDS.contains(command) || involved(villager, player)) {
			return false;
		}
		player.sendOverlayMessage(Component.translatable("mca.jaymoskingdoms.stranger", TRUSTED_HEARTS).withStyle(ChatFormatting.GRAY));
		return true;
	}
}
