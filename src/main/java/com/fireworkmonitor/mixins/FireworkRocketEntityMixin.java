package com.fireworkmonitor.mixins;

import com.fireworkmonitor.FireworkUseContext;
import com.fireworkmonitor.FireworkUseMonitor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures how a firework rocket was created and reports abnormal launches on the first tick.
 *
 * <p>Freshness is checked once, at construction: the marker in {@link FireworkUseContext} is
 * always pushed on the same thread, synchronously in the same server tick as the construction
 * (packet handler -> game mode -> item -> {@code new FireworkRocketEntity}), so a legitimate
 * marker always matches the tick that is current at the constructor's RETURN. A marker left over
 * from an earlier tick (aborted use) fails that check and is treated as "unmarked". The snapshot
 * consumed here is then reported on the first tick whenever that happens — no tick-time freshness
 * check, otherwise a first tick on the tick after construction would suppress every report.</p>
 */
@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin
{
	@Unique
	private FireworkUseMonitor.PendingSpawn fum$pending;

	@Unique
	private boolean fum$loadedFromNbt;

	// Injected at RETURN, not HEAD: Mixin requires handlers to be static for injection points at or
	// before the super()/this() delegate call of a constructor (this is not initialized yet), and
	// these handlers write instance fields. RETURN is after the delegate call, and still runs
	// synchronously inside the same call stack that pushed the FireworkUseContext, so the
	// thread-local source marker is still live here.
	@Inject(
			method = "<init>(Lnet/minecraft/world/level/Level;DDDLnet/minecraft/world/item/ItemStack;)V",
			at = @At("RETURN")
	)
	private void fum$captureSpawn(Level level, double x, double y, double z, ItemStack stack, CallbackInfo ci)
	{
		this.fum$capture(level, false);
	}

	@Inject(
			method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V",
			at = @At("RETURN")
	)
	private void fum$captureCreate(EntityType<?> type, Level level, CallbackInfo ci)
	{
		this.fum$capture(level, true);
	}

	@Inject(method = "readAdditionalSaveData", at = @At("HEAD"))
	private void fum$markLoadedFromNbt(ValueInput input, CallbackInfo ci)
	{
		this.fum$loadedFromNbt = true;
	}

	@Inject(method = "tick", at = @At("HEAD"))
	private void fum$onTick(CallbackInfo ci)
	{
		FireworkUseMonitor.PendingSpawn pending = this.fum$pending;
		this.fum$pending = null;
		if (pending != null)
		{
			FireworkUseMonitor.handleSpawn((FireworkRocketEntity) (Object) this, pending, this.fum$loadedFromNbt);
		}
	}

	@Unique
	private void fum$capture(Level level, boolean fromEntityType)
	{
		if (!(level instanceof ServerLevel serverLevel))
		{
			// Client-side prediction, or construction outside of a live server level: nothing to do.
			FireworkUseContext.clear();
			return;
		}

		FireworkUseContext ctx = FireworkUseContext.current();
		if (ctx != null && !ctx.isCurrent(FireworkUseContext.currentTick(serverLevel)))
		{
			// Leftover from an earlier tick (e.g. an aborted use): not a description of this spawn.
			ctx = null;
		}
		FireworkUseContext.Source source = ctx != null ? ctx.source : null;

		FireworkUseMonitor.PendingSpawn pending = new FireworkUseMonitor.PendingSpawn(
				source,
				fromEntityType,
				ctx != null ? ctx.player : null,
				ctx != null ? ctx.shooter : null
		);

		// Call stacks are only useful for the sources that can be reported. A use packet's server
		// stack is always the packet-handler chain and identifies nothing, so NETWORK skips it.
		if (source != FireworkUseContext.Source.CROSSBOW
				&& source != FireworkUseContext.Source.DISPENSER
				&& source != FireworkUseContext.Source.NETWORK)
		{
			pending.stackTrace = new Throwable().getStackTrace();
		}

		this.fum$pending = pending;

		if (ctx != null)
		{
			FireworkUseContext.clear();
		}
	}
}
