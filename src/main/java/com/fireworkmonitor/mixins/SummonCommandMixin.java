package com.fireworkmonitor.mixins;

import com.fireworkmonitor.FireworkUseContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.commands.SummonCommand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * {@code /summon firework_rocket} is reported as an abnormal source.
 */
@Mixin(SummonCommand.class)
public abstract class SummonCommandMixin
{
	@Inject(
			method = "createEntity(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/core/Holder$Reference;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/nbt/CompoundTag;Z)Lnet/minecraft/world/entity/Entity;",
			at = @At("HEAD")
	)
	private static void fum$onCreateEntity(CommandSourceStack source, Holder.Reference<EntityType<?>> type, Vec3 pos, CompoundTag tag, boolean randomizeProperties, CallbackInfoReturnable<Entity> cir)
	{
		if (type.value() == EntityType.FIREWORK_ROCKET)
		{
			FireworkUseContext.push(FireworkUseContext.Source.COMMAND, source.getPlayer(), null, BlockPos.containing(pos));
		}
	}

	@Inject(
			method = "createEntity(Lnet/minecraft/commands/CommandSourceStack;Lnet/minecraft/core/Holder$Reference;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/nbt/CompoundTag;Z)Lnet/minecraft/world/entity/Entity;",
			at = @At("RETURN")
	)
	private static void fum$afterCreateEntity(CommandSourceStack source, Holder.Reference<EntityType<?>> type, Vec3 pos, CompoundTag tag, boolean randomizeProperties, CallbackInfoReturnable<Entity> cir)
	{
		if (type.value() == EntityType.FIREWORK_ROCKET)
		{
			FireworkUseContext.clear();
		}
	}
}
