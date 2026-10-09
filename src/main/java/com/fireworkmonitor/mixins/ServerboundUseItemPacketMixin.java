package com.fireworkmonitor.mixins;

import com.fireworkmonitor.FireworkUseContext;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Marks the arrival of {@code ServerboundUseItemPacket} ("use item", right-click into the air,
 * which includes gliding-boost rocket usage).
 *
 * <p>Same contract as {@link ServerboundUseItemOnPacketMixin}: the marker is stamped at the packet
 * boundary and consumed inside the entity constructor capture; the RETURN-side cleanup clears the
 * thread local unconditionally, and the tick freshness check in {@link FireworkUseContext} makes a
 * leftover marker (e.g. from an exception between HEAD and RETURN) unusable for later ticks.</p>
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerboundUseItemPacketMixin
{
	@Inject(
			method = "handleUseItem(Lnet/minecraft/network/protocol/game/ServerboundUseItemPacket;)V",
			at = @At("HEAD")
	)
	private void fum$onUseItem(ServerboundUseItemPacket packet, CallbackInfo ci)
	{
		ServerGamePacketListenerImpl self = (ServerGamePacketListenerImpl) (Object) this;
		FireworkUseContext.push(FireworkUseContext.Source.NETWORK, self.getPlayer(), null, null, packet.getHand(),
				FireworkUseContext.currentTick(self.getPlayer().level()));
	}

	@Inject(
			method = "handleUseItem(Lnet/minecraft/network/protocol/game/ServerboundUseItemPacket;)V",
			at = @At("RETURN")
	)
	private void fum$afterUseItem(ServerboundUseItemPacket packet, CallbackInfo ci)
	{
		FireworkUseContext.clear();
	}
}
