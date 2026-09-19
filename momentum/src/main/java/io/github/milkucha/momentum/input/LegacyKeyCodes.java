package io.github.milkucha.momentum.input;

import static org.lwjgl.sdl.SDLScancode.*;

/**
 * Converts the existing momentum.json keyboard contract to Minecraft 26.3's SDL scancodes.
 * The persisted numbers stay unchanged so moving a config between game versions is lossless.
 * All SDL references here are compile-time integer constants, including on a dedicated server.
 */
public final class LegacyKeyCodes {
    public static final int UNKNOWN = SDL_SCANCODE_UNKNOWN;

    private LegacyKeyCodes() {
    }

    public static int toScancode(int key) {
        if (key >= 65 && key <= 90) return SDL_SCANCODE_A + key - 65;
        if (key >= 49 && key <= 57) return SDL_SCANCODE_1 + key - 49;
        if (key >= 290 && key <= 301) return SDL_SCANCODE_F1 + key - 290;
        if (key >= 302 && key <= 313) return SDL_SCANCODE_F13 + key - 302;
        if (key >= 321 && key <= 329) return SDL_SCANCODE_KP_1 + key - 321;

        return switch (key) {
            case 32 -> SDL_SCANCODE_SPACE;
            case 39 -> SDL_SCANCODE_APOSTROPHE;
            case 44 -> SDL_SCANCODE_COMMA;
            case 45 -> SDL_SCANCODE_MINUS;
            case 46 -> SDL_SCANCODE_PERIOD;
            case 47 -> SDL_SCANCODE_SLASH;
            case 48 -> SDL_SCANCODE_0;
            case 59 -> SDL_SCANCODE_SEMICOLON;
            case 61 -> SDL_SCANCODE_EQUALS;
            case 91 -> SDL_SCANCODE_LEFTBRACKET;
            case 92 -> SDL_SCANCODE_BACKSLASH;
            case 93 -> SDL_SCANCODE_RIGHTBRACKET;
            case 96 -> SDL_SCANCODE_GRAVE;
            case 256 -> SDL_SCANCODE_ESCAPE;
            case 257 -> SDL_SCANCODE_RETURN;
            case 258 -> SDL_SCANCODE_TAB;
            case 259 -> SDL_SCANCODE_BACKSPACE;
            case 260 -> SDL_SCANCODE_INSERT;
            case 261 -> SDL_SCANCODE_DELETE;
            case 262 -> SDL_SCANCODE_RIGHT;
            case 263 -> SDL_SCANCODE_LEFT;
            case 264 -> SDL_SCANCODE_DOWN;
            case 265 -> SDL_SCANCODE_UP;
            case 266 -> SDL_SCANCODE_PAGEUP;
            case 267 -> SDL_SCANCODE_PAGEDOWN;
            case 268 -> SDL_SCANCODE_HOME;
            case 269 -> SDL_SCANCODE_END;
            case 280 -> SDL_SCANCODE_CAPSLOCK;
            case 281 -> SDL_SCANCODE_SCROLLLOCK;
            case 282 -> SDL_SCANCODE_NUMLOCKCLEAR;
            case 283 -> SDL_SCANCODE_PRINTSCREEN;
            case 284 -> SDL_SCANCODE_PAUSE;
            case 320 -> SDL_SCANCODE_KP_0;
            case 330 -> SDL_SCANCODE_KP_PERIOD;
            case 331 -> SDL_SCANCODE_KP_DIVIDE;
            case 332 -> SDL_SCANCODE_KP_MULTIPLY;
            case 333 -> SDL_SCANCODE_KP_MINUS;
            case 334 -> SDL_SCANCODE_KP_PLUS;
            case 335 -> SDL_SCANCODE_KP_ENTER;
            case 336 -> SDL_SCANCODE_KP_EQUALS;
            case 340 -> SDL_SCANCODE_LSHIFT;
            case 341 -> SDL_SCANCODE_LCTRL;
            case 342 -> SDL_SCANCODE_LALT;
            case 343 -> SDL_SCANCODE_LGUI;
            case 344 -> SDL_SCANCODE_RSHIFT;
            case 345 -> SDL_SCANCODE_RCTRL;
            case 346 -> SDL_SCANCODE_RALT;
            case 347 -> SDL_SCANCODE_RGUI;
            case 348 -> SDL_SCANCODE_APPLICATION;
            // GLFW WORLD_1/WORLD_2 and F25 have no unambiguous portable SDL equivalent.
            default -> UNKNOWN;
        };
    }
}
