package io.github.foundationgames.automobility.screen;

import com.google.gson.JsonParser;
import net.minecraft.client.resources.language.FormattedBidiReorder;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/** Real Minecraft bidi and compiled call wiring, with explicitly simulated widths for algorithm tests.
 * Actual font metrics, glyph bounds and native rendering still require the separate in-game checks. */
class GarageTextLayoutTest {
    @Test
    void exactFitKeepsTheOriginalMeasuredSequenceAndDoesNotAddEllipsis() {
        var seen = new ArrayList<FormattedCharSequence>();
        var result = GarageTextLayout.fit("Frame", 5, GarageTextLayoutTest::plain, sequence -> {
            seen.add(sequence);
            return text(sequence).length();
        });
        assertEquals(1, seen.size());
        assertSame(seen.getFirst(), result.visualOrder());
        assertEquals("Frame", text(result.visualOrder()));
        assertEquals(5, result.fullWidth());
        assertFalse(GarageTextTooltip.isClipped(result.fullWidth(), 5));
    }

    @Test
    void overflowKeepsFullWidthForTooltipButReturnsOnlyAVerifiedFittingSequence() {
        var measured = new IdentityHashMap<FormattedCharSequence, Integer>();
        var result = GarageTextLayout.fit("Engine and wheels", 8, GarageTextLayoutTest::plain, sequence -> {
            int width = text(sequence).length();
            measured.put(sequence, width);
            return width;
        });
        assertEquals(17, result.fullWidth());
        assertTrue(GarageTextTooltip.isHoveredClipped(result.fullWidth(), 11, 138, 8, 9, 11, 138));
        assertTrue(measured.containsKey(result.visualOrder()), "Draw the exact sequence that was measured");
        assertTrue(measured.get(result.visualOrder()) <= 8);
        assertEquals("Engine …", text(result.visualOrder()));
    }

    @Test
    void aBoxSmallerThanTheEllipsisReturnsEmptyInsteadOfOverflowing() {
        for (int budget : new int[] {-1, 0, 1}) {
            var result = GarageTextLayout.fit("Long", budget, GarageTextLayoutTest::plain,
                    sequence -> text(sequence).equals("…") ? 2 : text(sequence).length() * 2);
            assertSame(FormattedCharSequence.EMPTY, result.visualOrder());
            assertEquals(8, result.fullWidth());
        }
    }

    @Test
    void supplementaryCodePointsAreNeverSplitAtACandidateBoundary() {
        String original = "A🚗B🚙C😀D";
        var candidates = new ArrayList<String>();
        var result = GarageTextLayout.fit(original, 4, candidate -> {
            assertWellFormedUtf16(candidate);
            candidates.add(candidate);
            return plain(candidate);
        }, sequence -> text(sequence).codePointCount(0, text(sequence).length()));
        assertTrue(candidates.size() > 2);
        String clipped = text(result.visualOrder());
        assertTrue(clipped.endsWith("…"));
        assertTrue(original.startsWith(clipped.substring(0, clipped.length() - 1)));
        assertTrue(clipped.codePointCount(0, clipped.length()) <= 4);
    }

    @Test
    void nonMonotonicWidthsCannotReplaceTheBestWithAnUnverifiedOrOverflowingCandidate() {
        var measured = new IdentityHashMap<FormattedCharSequence, Integer>();
        var result = GarageTextLayout.fit("abcdefghijklmnopqrstuvwxyz", 8, GarageTextLayoutTest::plain, sequence -> {
            String candidate = text(sequence);
            int prefix = candidate.length() - 1;
            int width = !candidate.endsWith("…") ? 100
                    : prefix == 0 ? 1 : prefix % 3 == 0 ? 100 : prefix + 1;
            measured.put(sequence, width);
            return width;
        });
        assertEquals(100, result.fullWidth());
        assertTrue(measured.containsKey(result.visualOrder()));
        assertTrue(measured.get(result.visualOrder()) <= 8);
        // Deliberately do not require the longest prefix: shaping can invalidate monotonicity.
    }

    @Test
    void arbitrarilyLongNamesUseLogarithmicallyBoundedMeasurementAttempts() {
        String original = "🚗".repeat(50_000);
        var preparations = new AtomicInteger();
        var measurements = new AtomicInteger();
        var result = GarageTextLayout.fit(original, 12, candidate -> {
            preparations.incrementAndGet();
            assertWellFormedUtf16(candidate);
            return plain(candidate);
        }, sequence -> {
            measurements.incrementAndGet();
            String candidate = text(sequence);
            return candidate.codePointCount(0, candidate.length());
        });
        int maximumAttempts = 2 + (32 - Integer.numberOfLeadingZeros(original.length()));
        assertTrue(preparations.get() <= maximumAttempts);
        assertEquals(preparations.get(), measurements.get());
        assertEquals(50_000, result.fullWidth());
        assertTrue(text(result.visualOrder()).codePointCount(0, text(result.visualOrder()).length()) <= 12);
    }

    @Test
    void actualUrduShapingExposesTheRawMeasurementBugUnderExplicitlySimulatedGlyphAdvances() throws Exception {
        String urdu = urduStatus();
        Function<String, FormattedCharSequence> actualBidi = value ->
                FormattedBidiReorder.reorder(FormattedText.of(value), true);
        var fullVisual = actualBidi.apply(urdu);
        assertNotEquals(urdu, text(fullVisual), "Actual Minecraft Arabic shaping/bidi must run");
        assertTrue(text(fullVisual).codePoints().anyMatch(GarageTextLayoutTest::isArabicPresentationForm));
        // These are adversarial model widths, NOT the native font's pixel advances.
        int oldRawWidth = simulatedArabicWidth(plain(urdu));
        int actualSequenceModelWidth = simulatedArabicWidth(fullVisual);
        assertTrue(oldRawWidth <= 226, "The old raw-string algorithm would incorrectly accept this model");
        assertTrue(actualSequenceModelWidth > 226, "The shaped sequence must exceed the same model budget");
        var result = GarageTextLayout.fit(urdu, 226, actualBidi, GarageTextLayoutTest::simulatedArabicWidth);
        assertEquals(actualSequenceModelWidth, result.fullWidth());
        assertTrue(GarageTextTooltip.isClipped(result.fullWidth(), 226));
        assertTrue(simulatedArabicWidth(result.visualOrder()) <= 226);
        assertTrue(text(result.visualOrder()).contains("…"));
    }

    @Test
    void mixedDirectionCandidatesAreReshapedAsCompleteLogicalPrefixPlusEllipsis() {
        String original = "Urdu 12: انجن یا پہیوں 🚗 Garage";
        var logicalBySequence = new IdentityHashMap<FormattedCharSequence, String>();
        var result = GarageTextLayout.fit(original, 14, candidate -> {
            var sequence = FormattedBidiReorder.reorder(FormattedText.of(candidate), true);
            logicalBySequence.put(sequence, candidate);
            return sequence;
        }, sequence -> text(sequence).codePointCount(0, text(sequence).length()));
        String candidate = logicalBySequence.get(result.visualOrder());
        assertNotNull(candidate);
        assertTrue(candidate.endsWith("…"));
        assertTrue(original.startsWith(candidate.substring(0, candidate.length() - 1)));
        assertEquals(text(FormattedBidiReorder.reorder(FormattedText.of(candidate), true)),
                text(result.visualOrder()));
        assertWellFormedUtf16(candidate);
    }

    @Test
    void compiledScreenDrawsTheSameLayoutSequenceWhoseFullWidthControlsTooltipGeometry() throws Exception {
        var screen = bytecode(GarageScreen.class);
        var method = screen.methods.stream().filter(m -> m.name.equals("clippedText")).findFirst().orElseThrow();
        var calls = calls(method);
        String layoutOwner = "io/github/foundationgames/automobility/screen/GarageTextLayout";
        String resultOwner = layoutOwner + "$Layout";
        var fit = calls.stream().filter(c -> c.owner.equals(layoutOwner) && c.name.equals("fit")).findFirst().orElseThrow();
        assertTrue(fit.getNext() instanceof VarInsnNode);
        int resultLocal = ((VarInsnNode) fit.getNext()).var;
        for (String accessor : List.of("fullWidth", "visualOrder")) {
            var call = calls.stream().filter(c -> c.owner.equals(resultOwner) && c.name.equals(accessor)).findFirst().orElseThrow();
            assertTrue(call.getPrevious() instanceof VarInsnNode);
            assertEquals(Opcodes.ALOAD, call.getPrevious().getOpcode());
            assertEquals(resultLocal, ((VarInsnNode) call.getPrevious()).var);
        }
        var draws = calls.stream().filter(c -> c.owner.equals("net/minecraft/client/gui/GuiGraphicsExtractor")
                && c.name.equals("text")).toList();
        assertEquals(1, draws.size());
        assertEquals("(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V", draws.getFirst().desc);
        assertTrue(calls.stream().noneMatch(c -> c.owner.equals("net/minecraft/client/gui/Font")
                && (c.name.equals("plainSubstrByWidth") || c.name.equals("width"))));
        var helper = bytecode(GarageTextLayout.class);
        var productionFit = helper.methods.stream().filter(m -> m.name.equals("fit")
                && m.desc.startsWith("(Lnet/minecraft/client/gui/Font;")).findFirst().orElseThrow();
        boolean measuredSequence = false;
        for (var instruction : productionFit.instructions) {
            if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                for (var argument : dynamic.bsmArgs) {
                    if (argument instanceof Handle handle && handle.getOwner().equals("net/minecraft/client/gui/Font")) {
                        assertEquals("width", handle.getName());
                        assertEquals("(Lnet/minecraft/util/FormattedCharSequence;)I", handle.getDesc());
                        measuredSequence = true;
                    }
                }
            }
        }
        assertTrue(measuredSequence, "Production width callback must measure the visual sequence overload");
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        var calls = new ArrayList<MethodInsnNode>();
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call) calls.add(call);
        }
        return calls;
    }

    private static ClassNode bytecode(Class<?> type) throws Exception {
        var node = new ClassNode();
        try (var input = type.getResourceAsStream("/" + type.getName().replace('.', '/') + ".class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        return node;
    }

    private static FormattedCharSequence plain(String text) {
        return FormattedCharSequence.forward(text, Style.EMPTY);
    }

    private static String text(FormattedCharSequence sequence) {
        var text = new StringBuilder();
        sequence.accept((index, style, codePoint) -> { text.appendCodePoint(codePoint); return true; });
        return text.toString();
    }

    private static int simulatedArabicWidth(FormattedCharSequence sequence) {
        return text(sequence).codePoints().map(cp -> isArabicPresentationForm(cp) ? 9 : 1).sum();
    }

    private static boolean isArabicPresentationForm(int codePoint) {
        return (codePoint >= 0xFB50 && codePoint <= 0xFDFF) || (codePoint >= 0xFE70 && codePoint <= 0xFEFF);
    }

    private static void assertWellFormedUtf16(String value) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                assertTrue(index + 1 < value.length() && Character.isLowSurrogate(value.charAt(++index)));
            } else {
                assertFalse(Character.isLowSurrogate(current));
            }
        }
    }

    private static String urduStatus() throws Exception {
        var directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            var resource = directory.resolve("common/src/main/resources/assets/automobility/lang/ur_pk.json");
            if (Files.isRegularFile(resource)) {
                try (var reader = Files.newBufferedReader(resource, StandardCharsets.UTF_8)) {
                    return JsonParser.parseReader(reader).getAsJsonObject()
                            .get("garage.automobility.status.insert_part").getAsString();
                }
            }
            directory = directory.getParent();
        }
        return fail("Cannot locate this worktree's actual Urdu status resource");
    }
}

