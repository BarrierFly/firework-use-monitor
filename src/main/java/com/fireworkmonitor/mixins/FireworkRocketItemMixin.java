package com.fireworkmonitor.mixins;

import com.fireworkmonitor.FireworkUseContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.FireworkRocketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Refines the source marker when the firework rocket item API runs.
 *
 * <p>On the server, {@code use}/{@code useOn} push a {@code PLAYER} marker only when no fresh
 * marker is present. Two cases:</p>
 * <ul>
 *   <li>A use packet is in flight (pushed by {@code ServerboundUsePacketListenerMixin}):
 *       the fresh NETWORK marker is kept — a real click and a client-mod click are
 *       indistinguishable on the server side.</li>
 *   <li>No packet in flight: the resulting PLAYER marker means some server-side code called the
 *       item API directly (typically another mod) — vanilla never does this for firework rockets.</li>
 * </ul>
 *
 * <p>On the client the marker is pushed unconditionally and consumed by the client-side
 * {@code FireworkRocketEntity} constructor capture, so client prediction is classified too.</p>
 */
@Mixin(FireworkRocketItem.class)
public abstract class FireworkRocketItemMixin
{
	@Inject(
			method = "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
			at = @At("HEAD")
	)
	private void fum$onUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir)
	{
		if (level instanceof ServerLevel serverLevel)
		{
			FireworkUseContext.pushIfAbsent(FireworkUseContext.Source.PLAYER, player, player, null, hand,
					FireworkUseContext.currentTick(serverLevel));
		}
		else
		{
			FireworkUseContext.push(FireworkUseContext.Source.PLAYER, player, player, null, hand, -1);
		}
	}

	@Inject(
			method = "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
			at = @At("RETURN")
	)
	private void fum$afterUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir)
	{
		if (level instanceof ServerLevel serverLevel)
		{
			FireworkUseContext.clearIf(FireworkUseContext.Source.PLAYER, FireworkUseContext.currentTick(serverLevel));
		}
	}

	@Inject(
			method = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
			at = @At("HEAD")
	)
	private void fum$onUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir)
	{
		Level level = context.getLevel();
		if (level instanceof ServerLevel serverLevel)
		{
			FireworkUseContext.pushIfAbsent(FireworkUseContext.Source.PLAYER, context.getPlayer(), null,
					context.getClickedPos(), context.getHand(), FireworkUseContext.currentTick(serverLevel));
		}
		else
		{
			FireworkUseContext.push(FireworkUseContext.Source.PLAYER, context.getPlayer(), null,
					context.getClickedPos(), context.getHand(), -1);
		}
	}

	@Inject(
			method = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
			at = @At("RETURN")
	)
	private void fum$afterUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir)
	{
		Level level = context.getLevel();
		if (level instanceof ServerLevel serverLevel)
		{
			FireworkUseContext.clearIf(FireworkUseContext.Source.PLAYER, FireworkUseContext.currentTick(serverLevel));
		}
	}

	@Inject(
			method = "asProjectile(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/Position;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/entity/projectile/Projectile;",
			at = @At("HEAD")
	)
	private void fum$onAsProjectile(Level level, Position pos, ItemStack stack, Direction direction, CallbackInfoReturnable<Projectile> cir)
	{
		FireworkUseContext.push(FireworkUseContext.Source.DISPENSER, null, null, BlockPos.containing(pos), null, -1);
	}

	@Inject(
			method = "asProjectile(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/Position;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/entity/projectile/Projectile;",
			at = @At("RETURN")
	)
	private void fum$afterAsProjectile(Level level, Position pos, ItemStack stack, Direction direction, CallbackInfoReturnable<Projectile> cir)
	{
		FireworkUseContext.clear();
	}
}
