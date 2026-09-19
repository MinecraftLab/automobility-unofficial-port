package io.github.milkucha.momentum.input;

import com.mojang.blaze3d.platform.InputConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.sdl.SDLScancode.*;

class LegacyKeyCodesTest {
    @Test
    void existingDefaultBrakeAndDriftUseTheSamePhysicalKeysOnSdl() {
        assertEquals(InputConstants.KEY_S, LegacyKeyCodes.toScancode(83));
        assertEquals(InputConstants.KEY_SPACE, LegacyKeyCodes.toScancode(32));
    }

    @Test
    void allLettersAndDigitsConvertFromLegacyCodes() {
        for (int key = 65; key <= 90; key++) {
            assertEquals(SDL_SCANCODE_A + key - 65, LegacyKeyCodes.toScancode(key));
        }
        for (int key = 49; key <= 57; key++) {
            assertEquals(SDL_SCANCODE_1 + key - 49, LegacyKeyCodes.toScancode(key));
        }
        assertEquals(SDL_SCANCODE_0, LegacyKeyCodes.toScancode(48));
    }

    @Test
    void functionKeysHandleTheGapBetweenF12AndF13() {
        for (int key = 290; key <= 301; key++) {
            assertEquals(SDL_SCANCODE_F1 + key - 290, LegacyKeyCodes.toScancode(key));
        }
        for (int key = 302; key <= 313; key++) {
            assertEquals(SDL_SCANCODE_F13 + key - 302, LegacyKeyCodes.toScancode(key));
        }
    }

    @Test
    void navigationAndModifiersKeepTheirMeaning() {
        int[][] pairs = {
                {256, SDL_SCANCODE_ESCAPE}, {257, SDL_SCANCODE_RETURN},
                {258, SDL_SCANCODE_TAB}, {259, SDL_SCANCODE_BACKSPACE},
                {260, SDL_SCANCODE_INSERT}, {261, SDL_SCANCODE_DELETE},
                {262, SDL_SCANCODE_RIGHT}, {263, SDL_SCANCODE_LEFT},
                {264, SDL_SCANCODE_DOWN}, {265, SDL_SCANCODE_UP},
                {266, SDL_SCANCODE_PAGEUP}, {267, SDL_SCANCODE_PAGEDOWN},
                {268, SDL_SCANCODE_HOME}, {269, SDL_SCANCODE_END},
                {340, SDL_SCANCODE_LSHIFT}, {341, SDL_SCANCODE_LCTRL},
                {342, SDL_SCANCODE_LALT}, {343, SDL_SCANCODE_LGUI},
                {344, SDL_SCANCODE_RSHIFT}, {345, SDL_SCANCODE_RCTRL},
                {346, SDL_SCANCODE_RALT}, {347, SDL_SCANCODE_RGUI},
                {348, SDL_SCANCODE_APPLICATION}
        };
        for (int[] pair : pairs) {
            assertEquals(pair[1], LegacyKeyCodes.toScancode(pair[0]), "Legacy key " + pair[0]);
        }
    }

    @Test
    void punctuationAndLockKeysKeepTheirMeaning() {
        int[][] pairs = {
                {39, SDL_SCANCODE_APOSTROPHE}, {44, SDL_SCANCODE_COMMA},
                {45, SDL_SCANCODE_MINUS}, {46, SDL_SCANCODE_PERIOD},
                {47, SDL_SCANCODE_SLASH}, {59, SDL_SCANCODE_SEMICOLON},
                {61, SDL_SCANCODE_EQUALS}, {91, SDL_SCANCODE_LEFTBRACKET},
                {92, SDL_SCANCODE_BACKSLASH}, {93, SDL_SCANCODE_RIGHTBRACKET},
                {96, SDL_SCANCODE_GRAVE}, {280, SDL_SCANCODE_CAPSLOCK},
                {281, SDL_SCANCODE_SCROLLLOCK}, {282, SDL_SCANCODE_NUMLOCKCLEAR},
                {283, SDL_SCANCODE_PRINTSCREEN}, {284, SDL_SCANCODE_PAUSE}
        };
        for (int[] pair : pairs) {
            assertEquals(pair[1], LegacyKeyCodes.toScancode(pair[0]), "Legacy key " + pair[0]);
        }
    }

    @Test
    void keypadNumbersAndOperatorsStaySeparateFromMainKeyboard() {
        for (int key = 321; key <= 329; key++) {
            assertEquals(SDL_SCANCODE_KP_1 + key - 321, LegacyKeyCodes.toScancode(key));
        }
        int[][] pairs = {
                {320, SDL_SCANCODE_KP_0}, {330, SDL_SCANCODE_KP_PERIOD},
                {331, SDL_SCANCODE_KP_DIVIDE}, {332, SDL_SCANCODE_KP_MULTIPLY},
                {333, SDL_SCANCODE_KP_MINUS}, {334, SDL_SCANCODE_KP_PLUS},
                {335, SDL_SCANCODE_KP_ENTER}, {336, SDL_SCANCODE_KP_EQUALS}
        };
        for (int[] pair : pairs) {
            assertEquals(pair[1], LegacyKeyCodes.toScancode(pair[0]), "Legacy key " + pair[0]);
        }
    }

    @Test
    void unboundInvalidAndPlatformSpecificKeysNeverActivateAnotherKey() {
        for (int key : new int[] {-1, 0, 100, 161, 162, 314, 349, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertEquals(LegacyKeyCodes.UNKNOWN, LegacyKeyCodes.toScancode(key));
        }
    }
}
