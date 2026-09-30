package com.fireworkmonitor;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Per-thread marker describing <b>why</b> a firework rocket is currently being created.
 *
 * <p>Vanilla (and other mods) create the {@code FireworkRocketEntity} object synchronously inside the
 * code path that triggered the launch. The item / crossbow / command mixins push a context onto this
 * thread local right before the entity is constructed, and the entity mixin consumes it again.</p>
 */
public final class FireworkUseContext
{
	public enum Source
	{
		/** Player right-clicked with the firework rocket item (gliding boost or block placement). */
		PLAYER,
		/** A crossbow shot the rocket. */
		CROSSBOW,
		/** A dispenser launched the rocket. */
		DISPENSER,
		/** {@code /summon} created the rocket. */
		COMMAND
	}

	private static final ThreadLocal<FireworkUseContext> CURRENT = new ThreadLocal<>();

	public final Source source;
	public final Player player;
	public final Entity shooter;
	public final BlockPos pos;

	private FireworkUseContext(Source source, Player player, Entity shooter, BlockPos pos)
	{
		this.source = source;
		this.player = player;
		this.shooter = shooter;
		this.pos = pos;
	}

	public static void push(Source source, Player player, Entity shooter, BlockPos pos)
	{
		CURRENT.set(new FireworkUseContext(source, player, shooter, pos));
	}

	public static FireworkUseContext current()
	{
		return CURRENT.get();
	}

	public static void clear()
	{
		CURRENT.remove();
	}
}
