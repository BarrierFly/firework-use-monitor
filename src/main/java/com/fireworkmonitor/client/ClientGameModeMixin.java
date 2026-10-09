package com.fireworkmonitor.client;

import com.fireworkmonitor.FireworkUseMonitor;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.FireworkRocketItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side observer on {@code MultiPlayerGameMode.useItem/useItemOn}.
 *
 * <p>When the local player uses a firework rocket item, the client call stack is classified:
 * physical input (mouse right button / use key) is silent, anything else is a programmatic use by
 * some client code and is reported locally with the initiating frame. This is the only layer that
 * can tell a real click apart from a client mod clicking for the player — on the server both look
 * identical (see HANDOVER.md).</p>
 *
 * <p>The mixin is registered in the {@code client} section of the mixin config, which dedicated
 * servers skip entirely — {@code MultiPlayerGameMode} never loads there.</p>
 */
@Mixin(value = MultiPlayerGameMode.class, remap = true)
public abstract class ClientGameModeMixin
{
	@Inject(
			method = "useItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
			at = @At("HEAD")
	)
	private void fum$onUseItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir)
	{
		ClientUseObserver.observe(player, hand);
	}

	@Inject(
			method = "useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;",
			at = @At("HEAD")
	)
	private void fum$onUseItemOn(LocalPlayer player, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir)
	{
		ClientUseObserver.observe(player, hand);
	}
}
