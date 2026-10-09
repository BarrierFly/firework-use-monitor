package com.fireworkmonitor.mixins;

import com.fireworkmonitor.FireworkUseContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A crossbow firing a firework rocket is considered a normal use.
 */
@Mixin(CrossbowItem.class)
public abstract class CrossbowItemMixin
{
	@Inject(
			method = "createProjectile(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/projectile/Projectile;",
			at = @At("HEAD")
	)
	private void fum$onCreateProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean isCrit, CallbackInfoReturnable<Projectile> cir)
	{
		Player player = shooter instanceof Player p ? p : null;
		FireworkUseContext.push(FireworkUseContext.Source.CROSSBOW, player, shooter, null, null,
				level instanceof ServerLevel serverLevel ? FireworkUseContext.currentTick(serverLevel) : -1);
	}

	@Inject(
			method = "createProjectile(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/entity/projectile/Projectile;",
			at = @At("RETURN")
	)
	private void fum$afterCreateProjectile(Level level, LivingEntity shooter, ItemStack weapon, ItemStack ammo, boolean isCrit, CallbackInfoReturnable<Projectile> cir)
	{
		FireworkUseContext.clear();
	}
}
