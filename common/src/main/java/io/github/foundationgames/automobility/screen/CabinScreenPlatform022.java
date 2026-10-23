package io.github.foundationgames.automobility.screen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
/** Target-owned screen API; changes neither the requested screen nor its permission/focus guards. */
public final class CabinScreenPlatform022 {
    private CabinScreenPlatform022() { }
    public static void set(Minecraft client,Screen screen) { client.gui.setScreen(screen); }
}
