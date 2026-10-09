package com.fireworkmonitor.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Increments a client frame counter on every {@code Minecraft.tick}. The counter only replaces a
 * wall-clock heuristic for "same client frame" bookkeeping; the physical-input classification is
 * purely stack-based and does not need it, but keeping a frame stamp costs nothing and may help
 * correlating future server-side events with client-side uses.
 */
@Mixin(Minecraft.class)
public abstract class ClientMinecraftTickMixin
{
	@Inject(method = "tick", at = @At("HEAD"))
	private void fum$onTick(CallbackInfo ci)
	{
		ClientUseObserver.clientTick();
	}
}
