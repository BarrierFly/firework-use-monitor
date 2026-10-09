package com.fireworkmonitor.client;

/**
 * Client-side stack anchors that only a <b>physical</b> input produces.
 *
 * <p>A real right-click always reaches {@code Minecraft.startUseItem} through
 * {@code MouseHandler.onButton} (mouse) or {@code Minecraft.handleKeybinds} (use key). Code that
 * initiates a use programmatically — a client mod auto-firing the rocket — calls
 * {@code startUseItem} / {@code gameMode.useItem} itself and therefore has neither anchor frame in
 * its stack. Method names are matched against their intermediary identities so this also works
 * outside a development environment.</p>
 */
public abstract class ClientPhysicalInputClassifier
{
	private static final String MOUSE_ON_BUTTON = "method_1601";
	private static final String HANDLE_KEYBINDS = "method_1508";

	private ClientPhysicalInputClassifier()
	{
	}

	/**
	 * True if the current client call stack contains one of the physical-input anchor frames.
	 */
	public static boolean isPhysicalInputUse()
	{
		for (StackTraceElement frame : new Throwable().getStackTrace())
		{
			String className = frame.getClassName();
			String methodName = frame.getMethodName();
			if (methodName.equals(MOUSE_ON_BUTTON)
					&& (className.equals("net.minecraft.class_312") || className.equals("net.minecraft.client.MouseHandler")))
			{
				return true;
			}
			if (methodName.equals(HANDLE_KEYBINDS)
					&& (className.equals("net.minecraft.class_310") || className.equals("net.minecraft.client.Minecraft")))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * First call stack frame that is neither vanilla nor this mod: the code that initiated the use.
	 */
	public static String findInitiatorFrame()
	{
		for (StackTraceElement frame : new Throwable().getStackTrace())
		{
			String className = frame.getClassName();
			if (isVanillaClientFrame(className))
			{
				continue;
			}
			return frame.getClassName() + "." + frame.getMethodName()
					+ (frame.getFileName() != null ? "(" + frame.getFileName() + ":" + frame.getLineNumber() + ")" : "");
		}
		return null;
	}

	private static boolean isVanillaClientFrame(String className)
	{
		return className.startsWith("net.minecraft.")
				|| className.startsWith("java.")
				|| className.startsWith("javax.")
				|| className.startsWith("jdk.")
				|| className.startsWith("sun.")
				|| className.startsWith("com.sun.")
				|| className.startsWith("com.mojang.")
				|| className.startsWith("com.fireworkmonitor.")
				|| className.startsWith("org.spongepowered.")
				|| className.startsWith("com.llamalad7.")
				|| className.startsWith("net.fabricmc.");
	}
}
