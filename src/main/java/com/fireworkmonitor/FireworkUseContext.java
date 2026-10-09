package com.fireworkmonitor;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Per-thread marker describing <b>why</b> a firework rocket is currently being created.
 *
 * <p>Vanilla (and other mods) create the {@code FireworkRocketEntity} object synchronously inside the
 * code path that triggered the launch. The packet / item / crossbow / command mixins push a context
 * onto this thread local right before the entity is constructed, and the entity mixin consumes it
 * again.</p>
 *
 * <p>Every marker carries the server tick it was pushed in. The entity mixin rejects markers that
 * were not pushed in the current tick, so a marker left behind by an aborted use (e.g. an exception
 * between the HEAD and RETURN injection points) can never mislabel a later spawn.</p>
 */
public final class FireworkUseContext
{
	public enum Source
	{
		/**
		 * {@code FireworkRocketItem.use} / {@code useOn} ran on a server thread with <b>no</b> use
		 * packet in flight. Vanilla never does this for firework rockets, so this means some
		 * server-side code (typically another mod) invoked the item API directly.
		 */
		PLAYER,
		/** A crossbow shot the rocket ({@code CrossbowItem.createProjectile}). */
		CROSSBOW,
		/** A dispenser (or a direct {@code asProjectile} call) launched the rocket. */
		DISPENSER,
		/** {@code /summon} created the rocket. */
		COMMAND,
		/**
		 * A use packet ({@code ServerboundUseItemPacket} / {@code ServerboundUseItemOnPacket})
		 * arrived for the player. A real mouse click and a client mod clicking for the player are
		 * indistinguishable at this level; only the client-side observer can tell them apart.
		 */
		NETWORK
	}

	private static final ThreadLocal<FireworkUseContext> CURRENT = new ThreadLocal<>();

	public final Source source;
	public final Player player;
	public final Entity shooter;
	public final BlockPos pos;
	public final InteractionHand hand;
	/** Server tick this marker was pushed in; negative means "unknown" and never matches. */
	public final int tick;

	private FireworkUseContext(Source source, Player player, Entity shooter, BlockPos pos, InteractionHand hand, int tick)
	{
		this.source = source;
		this.player = player;
		this.shooter = shooter;
		this.pos = pos;
		this.hand = hand;
		this.tick = tick;
	}

	public static void push(Source source, Player player, Entity shooter, BlockPos pos, InteractionHand hand, int tick)
	{
		CURRENT.set(new FireworkUseContext(source, player, shooter, pos, hand, tick));
	}

	/**
	 * Pushes only when no <b>fresh</b> marker is present on this thread.
	 *
	 * <p>Item-level mixins ({@code use}/{@code useOn}) use this instead of {@link #push} so that a
	 * marker pushed by the packet listener (NETWORK) is not overwritten by the item-level
	 * refinement (PLAYER): the packet-level answer is the meaningful one on the server, and the
	 * item-level marker alone additionally means "no packet was in flight at all".</p>
	 */
	public static void pushIfAbsent(Source source, Player player, Entity shooter, BlockPos pos, InteractionHand hand, int tick)
	{
		FireworkUseContext current = CURRENT.get();
		if (current != null && current.isCurrent(tick))
		{
			return;
		}
		push(source, player, shooter, pos, hand, tick);
	}

	/**
	 * Clears the marker only if it belongs to the given source and was pushed in the current tick.
	 * Used by the RETURN-side cleanup injections so they cannot wipe out an unrelated marker.
	 */
	public static void clearIf(Source expected, int nowTick)
	{
		FireworkUseContext current = CURRENT.get();
		if (current != null && current.source == expected && current.isCurrent(nowTick))
		{
			CURRENT.remove();
		}
	}

	/** True if this marker was pushed in the given tick (i.e. it is not a stale leftover). */
	public boolean isCurrent(int nowTick)
	{
		return tick >= 0 && tick == nowTick;
	}

	/**
	 * Current server tick for freshness stamping, or {@code -1} if it cannot be determined.
	 */
	public static int currentTick(Level level)
	{
		try
		{
			MinecraftServer server = level != null ? level.getServer() : null;
			return server != null ? server.getTickCount() : -1;
		}
		catch (Throwable ignored)
		{
			return -1;
		}
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
