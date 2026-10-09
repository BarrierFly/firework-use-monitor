package com.fireworkmonitor;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.MappingResolver;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.core.component.DataComponents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Watches every {@link FireworkRocketEntity} that gets created on the server.
 *
 * <p>Reporting rules (see HANDOVER.md, v2):</p>
 * <ul>
 *   <li>{@code CROSSBOW} / {@code DISPENSER} launches are normal and silently ignored.</li>
 *   <li>{@code NETWORK} launches (a use packet arrived, then the rocket was constructed) are the
 *       standard "player right-clicked (or a client mod clicked for the player)" path. Depending on
 *       {@link Mode} they are logged at INFO, reported as a warning, or silenced.</li>
 *   <li>{@code PLAYER} means the item API ran on a server thread with <b>no</b> use packet in
 *       flight: some server-side code (typically another mod) called it directly. Reported.</li>
 *   <li>{@code COMMAND} ({@code /summon}) and unmarked constructions (direct entity creation) are
 *       reported.</li>
 *   <li>Reloading an existing rocket from save data is never a "use".</li>
 * </ul>
 */
public class FireworkUseMonitor implements ModInitializer
{
	public static final String MOD_ID = "firework_use_monitor";
	public static final Logger LOGGER = LoggerFactory.getLogger("FireworkUseMonitor");

	/** How {@code NETWORK} launches (use packets) are reported. Never affects abnormal sources. */
	public enum Mode
	{
		/** Use-packet launches are logged at INFO only. */
		QUIET,
		/** Use-packet launches are also reported as a warning with details. */
		NETWORK,
		/** Every launch is reported with details. */
		ALL
	}

	/** Maximum number of stack frames that are included in an alert. */
	private static final int MAX_STACK_FRAMES = 20;

	private static final MappingResolver MAPPING_RESOLVER = FabricLoader.getInstance().getMappingResolver();

	private static volatile Mode mode = Mode.QUIET;

	@Override
	public void onInitialize()
	{
		String value = System.getProperty("fireworkusemonitor.mode");
		if (value == null)
		{
			value = System.getenv("FIREWORKUSEMONITOR_MODE");
		}
		if (value != null)
		{
			try
			{
				mode = Mode.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
			}
			catch (IllegalArgumentException e)
			{
				LOGGER.warn("Invalid fireworkusemonitor.mode value '{}', using QUIET (valid: QUIET, NETWORK, ALL)", value);
			}
		}
		LOGGER.info("Firework Use Monitor loaded (network launches: {})", mode);
	}

	public static Mode getMode()
	{
		return mode;
	}

	/**
	 * Called from the entity mixin on the first tick of a newly created firework rocket.
	 */
	public static void handleSpawn(FireworkRocketEntity entity, PendingSpawn pending, boolean loadedFromNbt)
	{
		if (!(entity.level() instanceof ServerLevel level))
		{
			return;
		}

		FireworkUseContext.Source source = pending.source;

		// Re-loading an already existing rocket from a save file is not a "use".
		if (loadedFromNbt && source != FireworkUseContext.Source.COMMAND)
		{
			return;
		}

		if (source == FireworkUseContext.Source.CROSSBOW || source == FireworkUseContext.Source.DISPENSER)
		{
			return;
		}

		// The item API ran without any use packet in flight: a server-side mod called it directly.
		if (source == FireworkUseContext.Source.PLAYER)
		{
			report(level, entity, pending, loadedFromNbt, "direct item call (no use packet in flight)");
			return;
		}

		if (source == FireworkUseContext.Source.COMMAND)
		{
			report(level, entity, pending, loadedFromNbt, "command /summon");
			return;
		}

		if (source == FireworkUseContext.Source.NETWORK)
		{
			// A real click and a client-mod click are indistinguishable here (see HANDOVER.md).
			if (mode == Mode.QUIET)
			{
				LOGGER.info("Firework rocket launched via use packet{}",
						System.lineSeparator() + buildDetails(level, entity, pending, loadedFromNbt));
				return;
			}
			report(level, entity, pending, loadedFromNbt, "use packet (player click or client mod)");
			return;
		}

		// No marker at all: constructed outside any known path.
		String label = pending.fromEntityType
				? (loadedFromNbt ? "unknown (entity NBT load)" : "unknown (entity type create, e.g. plugin / mod summon)")
				: "unknown (firework rocket spawn constructor, e.g. plugin / mod)";
		report(level, entity, pending, loadedFromNbt, label);
	}

	private static void report(ServerLevel level, FireworkRocketEntity entity, PendingSpawn pending, boolean loadedFromNbt, String label)
	{
		String details = buildDetails(level, entity, pending, loadedFromNbt);

		LOGGER.warn("Abnormal firework rocket launch detected ({}).{}",
				label,
				System.lineSeparator() + details);

		ServerPlayer target = resolveTarget(pending);
		if (target != null)
		{
			// Client-side-only chat message: only the involved player sees it, it is not broadcast.
			target.sendSystemMessage(Component.literal(
					"[FireworkUseMonitor] Abnormal firework rocket launch (" + label + "):\n" + details));
		}
	}

	private static String buildDetails(ServerLevel level, FireworkRocketEntity entity, PendingSpawn pending, boolean loadedFromNbt)
	{
		StringBuilder sb = new StringBuilder();

		sb.append("source: ").append(sourceLabel(pending.source, pending.fromEntityType, loadedFromNbt)).append('\n');
		sb.append("dimension: ").append(level.dimension().identifier()).append('\n');
		sb.append("position: ")
				.append(format(entity.getX())).append(", ")
				.append(format(entity.getY())).append(", ")
				.append(format(entity.getZ())).append('\n');

		ItemStack stack = entity.getItem();
		sb.append("item: ").append(stack.getCount()).append("x ").append(itemId(stack)).append('\n');

		Fireworks fireworks = stack.get(DataComponents.FIREWORKS);
		if (fireworks != null)
		{
			sb.append("fireworks: flight=").append(fireworks.flightDuration())
					.append(", explosions=").append(fireworks.explosions().size()).append('\n');
		}

		if (pending.shooter != null)
		{
			sb.append("shooter: ").append(describeEntity(pending.shooter)).append('\n');
		}

		if (pending.player != null)
		{
			sb.append("responsible player: ").append(describeEntity(pending.player)).append('\n');
		}

		Entity owner = entity.getOwner();
		if (owner != null)
		{
			sb.append("owner: ").append(describeEntity(owner)).append('\n');
		}

		if (pending.stackTrace != null)
		{
			sb.append("call stack (newest first):").append('\n');
			appendStack(sb, pending.stackTrace);
		}

		return sb.toString();
	}

	private static String sourceLabel(FireworkUseContext.Source source, boolean fromEntityType, boolean loadedFromNbt)
	{
		if (source == FireworkUseContext.Source.COMMAND)
		{
			return "command /summon";
		}
		if (source == FireworkUseContext.Source.NETWORK)
		{
			return "use packet (player click or client mod)";
		}
		if (source == FireworkUseContext.Source.PLAYER)
		{
			return "direct item call (no use packet in flight)";
		}
		if (source != null)
		{
			return source.name().toLowerCase();
		}
		if (fromEntityType)
		{
			return loadedFromNbt
					? "unknown (entity NBT load)"
					: "unknown (entity type create, e.g. plugin / mod summon)";
		}
		return "unknown (firework rocket spawn constructor, e.g. plugin / mod)";
	}

	private static ServerPlayer resolveTarget(PendingSpawn pending)
	{
		if (pending.player instanceof ServerPlayer player)
		{
			return player;
		}
		if (pending.shooter instanceof ServerPlayer player)
		{
			return player;
		}
		return null;
	}

	/**
	 * Client-side observation result for one item use (see ClientUseObserver).
	 */
	public static final class ClientUseInfo
	{
		/** True if the use was initiated by physical input (mouse right button or the use key). */
		public final boolean physicalInput;
		/** First client call stack frame that belongs to a mod, or null. */
		public final String modFrame;

		public ClientUseInfo(boolean physicalInput, String modFrame)
		{
			this.physicalInput = physicalInput;
			this.modFrame = modFrame;
		}
	}

	/**
	 * Called by the client observer when a <b>programmatic</b> (non-physical-input) firework
	 * rocket use was detected on the client. Logs it and returns the user-facing message (the
	 * caller displays it; this class must stay free of client-only class references so it stays
	 * dedicated-server safe).
	 */
	public static String handleClientUse(ItemStack stack, ClientUseInfo info)
	{
		String detail = "Unknown client code initiated a firework rocket use (not a mouse click or use key press).";
		if (info.modFrame != null)
		{
			detail = "Client code that initiated the firework rocket use:\n  " + info.modFrame;
		}
		String message = "[FireworkUseMonitor] Programmatic firework rocket use detected (" + itemId(stack) + ")\n" + detail;

		LOGGER.warn("{}", message);
		return message;
	}

	private static String itemId(ItemStack stack)
	{
		try
		{
			return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		}
		catch (Throwable ignored)
		{
			return String.valueOf(stack.getItem());
		}
	}

	private static String describeEntity(Entity entity)
	{
		return entity.getName().getString() + " (" + entity.getUUID() + ")";
	}

	private static String format(double value)
	{
		return String.format(java.util.Locale.ROOT, "%.2f", value);
	}

	private static void appendStack(StringBuilder sb, StackTraceElement[] stack)
	{
		int written = 0;
		for (StackTraceElement frame : stack)
		{
			String className = frame.getClassName();
			if (isNoise(className))
			{
				continue;
			}

			sb.append("  at ").append(prettyFrame(frame)).append('\n');
			if (++written >= MAX_STACK_FRAMES)
			{
				sb.append("  ...").append('\n');
				break;
			}
		}
		if (written == 0)
		{
			sb.append("  <no frames>").append('\n');
		}
	}

	private static boolean isNoise(String className)
	{
		return className.startsWith("java.")
				|| className.startsWith("javax.")
				|| className.startsWith("jdk.")
				|| className.startsWith("sun.")
				|| className.startsWith("com.sun.")
				|| className.startsWith("com.fireworkmonitor.")
				|| className.startsWith("org.spongepowered.")
				|| className.startsWith("com.llamalad7.");
	}

	private static String prettyFrame(StackTraceElement frame)
	{
		String className = mapClass(frame.getClassName());
		return className + "." + frame.getMethodName()
				+ (frame.getFileName() != null ? "(" + frame.getFileName() + ":" + frame.getLineNumber() + ")" : "");
	}

	private static String mapClass(String className)
	{
		try
		{
			return MAPPING_RESOLVER.unmapClassName("intermediary", className);
		}
		catch (Throwable ignored)
		{
			return className;
		}
	}

	/**
	 * Information gathered while a firework rocket is being constructed.
	 */
	public static final class PendingSpawn
	{
		public final FireworkUseContext.Source source;
		public final boolean fromEntityType;
		public final Player player;
		public final Entity shooter;
		public StackTraceElement[] stackTrace;

		public PendingSpawn(FireworkUseContext.Source source, boolean fromEntityType, Player player, Entity shooter)
		{
			this.source = source;
			this.fromEntityType = fromEntityType;
			this.player = player;
			this.shooter = shooter;
		}
	}
}
