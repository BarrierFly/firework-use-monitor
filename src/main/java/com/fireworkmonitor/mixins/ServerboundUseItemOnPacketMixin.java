package com.fireworkmonitor.mixins;

import com.fireworkmonitor.FireworkUseContext;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the arrival of {@code ServerboundUseItemOnPacket} ("use item on a block", which includes
 * right-clicking a block while holding the rocket).
 *
 * <p>Everything the server does afterwards (game mode -> item -> entity construction) runs on this
 * same thread inside this method, so a fresh marker stamped here is authoritative for the whole
 * use. A real mouse click and a client mod clicking for the player both produce this packet and
 * are intentionally indistinguishable here — see HANDOVER.md.</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerboundUseItemOnPacketMixin
{
	@Inject(
			method = "handleUseItemOn(Lnet/minecraft/network/protocol/game/ServerboundUseItemOnPacket;)V",
			at = @At("HEAD")
	)
	private void fum$onUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci)
	{
		ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl) (Object) this;
		FireworkUseContext.push(FireworkUseContext.Source.NETWORK, self.getPlayer(), null, null, packet.getHand(),
				FireworkUseContext.currentTick(self.getPlayer().level()));
	}

	@Inject(
			method = "handleUseItemOn(Lnet/minecraft/network/protocol/game/ServerboundUseItemOnPacket;)V",
			at = @At("RETURN")
	)
	private void fum$afterUseItemOn(ServerboundUseItemOnPacket packet, CallbackInfo ci)
	{
		FireworkUseContext.clear();
	}
}
