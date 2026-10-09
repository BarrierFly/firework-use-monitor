package com.fireworkmonitor.client;

import com.fireworkmonitor.FireworkUseMonitor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Gathers the classification for one client-side item use and forwards it to the reporter.
 * Also keeps a client frame counter (incremented from {@code Minecraft.tick}).
 */
final class ClientUseObserver
{
	private static long clientFrame;

	private ClientUseObserver()
	{
	}

	static void clientTick()
	{
		clientFrame++;
	}

	static long currentClientFrame()
	{
		return clientFrame;
	}

	static void observe(Player player, InteractionHand hand)
	{
		if (player == null)
		{
			return;
		}

		ItemStack stack = player.getItemInHand(hand);
		if (!(stack.getItem() instanceof net.minecraft.world.item.FireworkRocketItem))
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
		FireworkUseMonitor.handleClientUse(stack, new FireworkUseMonitor.ClientUseInfo(false, modFrame));
	}
}
