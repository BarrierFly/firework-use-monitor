package com.fireworkmonitor.client;

import com.fireworkmonitor.FireworkUseMonitor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;

/**
 * Gathers the classification for one client-side item use and forwards it to the reporter.
 * Also keeps a client frame counter (incremented from {@code Minecraft.tick}).
 *
 * <p>Lives in {@code com.fireworkmonitor.client} rather than next to the mixins in
 * {@code com.fireworkmonitor.mixins.client}: everything under the mixin config's {@code package}
 * prefix is reserved for mixin classes and cannot be loaded as ordinary classes.</p>
 */
public final class ClientUseObserver
{
	private static long clientFrame;

	private ClientUseObserver()
	{
	}

	public static void clientTick()
	{
		clientFrame++;
	}

	public static long currentClientFrame()
	{
		return clientFrame;
	}

	public static void observe(Player player, InteractionHand hand)
	{
		if (player == null)
		{
			return;
		}

		ItemStack stack = player.getItemInHand(hand);
		if (!(stack.getItem() instanceof FireworkRocketItem))
		{
			return;
		}

		boolean physical = ClientPhysicalInputClassifier.isPhysicalInputUse();
		if (physical)
		{
			// Real mouse click / use key: exactly what vanilla players do, never reported.
			return;
		}

		String modFrame = ClientPhysicalInputClassifier.findInitiatorFrame();
		String message = FireworkUseMonitor.handleClientUse(stack,
				new FireworkUseMonitor.ClientUseInfo(false, modFrame, currentClientFrame()));

		if (player instanceof LocalPlayer localPlayer)
		{
			localPlayer.displayClientMessage(Component.literal(message), true);
		}
	}
}
