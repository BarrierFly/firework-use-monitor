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
 * <p>Launches that come from a player right-clicking the firework rocket item, from a crossbow, or
 * from a dispenser are considered normal and are ignored. Everything else (commands, other mods,
 * unknown code paths) is reported with detailed information and written to the log.</p>
 */
public class FireworkUseMonitor implements ModInitializer
{
	public static final String MOD_ID = "firework_use_monitor";
	public static final Logger LOGGER = LoggerFactory.getLogger("FireworkUseMonitor");

	/** Maximum number of stack frames that are included in an alert. */
	private static final int MAX_STACK_FRAMES = 20;

	private static final MappingResolver MAPPING_RESOLVER = FabricLoader.getInstance().getMappingResolver();

	@Override
	public void onInitialize()
	{
		LOGGER.info("Firework Use Monitor loaded");
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

		// Player right-click / crossbow / dispenser are the normal, expected ways to use a rocket.
		if (source == FireworkUseContext.Source.PLAYER
				|| source == FireworkUseContext.Source.CROSSBOW
				|| source == FireworkUseContext.Source.DISPENSER)
		{
			return;
		}

		// Re-loading an already existing rocket from a save file is not a "use".
		if (loadedFromNbt && source != FireworkUseContext.Source.COMMAND)
		{
			return;
		}

		String details = buildDetails(level, entity, pending, loadedFromNbt);

		LOGGER.warn("Abnormal firework rocket launch detected ({}).{}",
				sourceLabel(pending.source, pending.fromEntityType, loadedFromNbt),
				System.lineSeparator() + details);

		ServerPlayer target = resolveTarget(pending);
		if (target != null)
		{
			// Client-side-only chat message: only the involved player sees it, it is not broadcast.
			target.sendSystemMessage(Component.literal(
					"[FireworkUseMonitor] Abnormal firework rocket launch (" +
							sourceLabel(pending.source, pending.fromEntityType, loadedFromNbt) + "):\n" + details));
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
