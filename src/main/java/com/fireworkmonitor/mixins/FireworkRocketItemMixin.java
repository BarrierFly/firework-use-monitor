package com.fireworkmonitor.mixins;

import com.fireworkmonitor.FireworkUseContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
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
 * Marks the code paths that count as a "normal" firework rocket use:
 * player right-click (gliding boost) and player right-click on a block.
 * The {@code asProjectile} path (dispenser) is normal as well.
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
		FireworkUseContext.push(FireworkUseContext.Source.PLAYER, player, player, null);
	}

	@Inject(
			method = "use(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;",
			at = @At("RETURN")
	)
	private void fum$afterUse(Level level, Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir)
	{
		FireworkUseContext.clear();
	}

	@Inject(
			method = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
			at = @At("HEAD")
	)
	private void fum$onUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir)
	{
		FireworkUseContext.push(FireworkUseContext.Source.PLAYER, context.getPlayer(), null, context.getClickedPos());
	}

	@Inject(
			method = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
			at = @At("RETURN")
	)
	private void fum$afterUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir)
	{
		FireworkUseContext.clear();
	}

	@Inject(
			method = "asProjectile(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/Position;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/Direction;)Lnet/minecraft/world/entity/projectile/Projectile;",
			at = @At("HEAD")
	)
	private void fum$onAsProjectile(Level level, Position pos, ItemStack stack, Direction direction, CallbackInfoReturnable<Projectile> cir)
	{
		FireworkUseContext.push(FireworkUseContext.Source.DISPENSER, null, null, BlockPos.containing(pos));
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
