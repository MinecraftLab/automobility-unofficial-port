package io.github.foundationgames.automobility.entity;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source wiring contract only; actual mixin ordering is covered by the server fixture. */
final class ReverseBrakeInputWiringTest {
    @Test
    void playerRawEdgesComeFromAcceptedInputEnvelopesAndNeverFromMaskedTickFields() throws IOException {
        String source = Files.readString(findEntitySource());
        String preparation = body(source, "public void prepareReverseBrakeIntent()");
        String nonPlayerCapture = body(preparation,
                "if (valid && !(this.getFirstPassenger() instanceof Player))");
        String rawCapture = "reverseBrakeGuard.acceptBaseInput(input.accelerating, input.braking,";
        assertEquals(1, occurrences(preparation, rawCapture));
        assertEquals(1, occurrences(nonPlayerCapture, rawCapture));

        String input = body(source, "public static class Input");
        for (String setter : new String[]{"public boolean setInputs(", "public boolean setDigitalInputs("}) {
            assertTrue(body(input, setter).contains("owner.acceptRawBaseInput(fwd, back)"));
        }
        assertFalse(body(input, "public void clearInputs()").contains("acceptRawBaseInput"),
                "Synthetic unpowered/stale clearing cannot become a physical pedal release");

        String acceptedClient = body(source, "public boolean applyClientInputState(");
        assertTrue(acceptedClient.indexOf("lastAcceptedClientInputTick =")
                < acceptedClient.indexOf("this.input.setInputs("));
        assertTrue(body(source, "public void readSyncStateData(").contains("input.setInputs("),
                "Remote client envelopes must retain the same owner callback wiring");
    }

    private static int occurrences(String text, String fragment) {
        return text.split(java.util.regex.Pattern.quote(fragment), -1).length - 1;
    }

    private static String body(String text, String signature) {
        int signatureStart = text.indexOf(signature);
        assertTrue(signatureStart >= 0, "Missing source declaration: " + signature);
        int start = text.indexOf('{', signatureStart);
        int depth = 1;
        for (int cursor = start + 1; cursor < text.length(); cursor++) {
            if (text.charAt(cursor) == '{') depth++;
            if (text.charAt(cursor) == '}') {
                depth--;
                if (depth == 0) return text.substring(start + 1, cursor);
            }
        }
        throw new AssertionError("Unclosed source declaration: " + signature);
    }

    private static Path findEntitySource() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve(
                    "common/src/main/java/io/github/foundationgames/automobility/entity/AutomobileEntity.java");
            if (Files.isRegularFile(candidate)) return candidate;
            directory = directory.getParent();
        }
        throw new AssertionError("AutomobileEntity source not found");
    }
}
