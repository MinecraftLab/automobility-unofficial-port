package io.github.foundationgames.automobility.screen;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Instruction;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/** Checks compiled render calls without starting Minecraft or initializing native graphics. */
final class ClientTextRenderingTest {
    @Test
    void mechanicCategoryTitleSuppliesOpaqueArgb() throws IOException {
        var instructions = instructions(
                "io/github/foundationgames/automobility/screen/AutoMechanicTableScreen.class",
                "drawCategoryBar");
        int call = invocation(instructions, "centeredText");

        // GuiGraphicsExtractor.text discards zero-alpha colors before enqueueing text in 26.3.
        assertConstant(instructions.get(call - 1), 0xFFFFFFFF);
    }

    @Test
    void assemblerLabelSuppliesLightBeforeOpaqueTextColor() throws IOException {
        var instructions = instructions(
                "io/github/foundationgames/automobility/block/entity/render/AutomobileAssemblerBlockEntityRenderer.class",
                "submit");
        int call = invocation(instructions, "submitText");

        // All four arguments are ints, so javac cannot catch the old color/light mix-up.
        var light = assertInstanceOf(FieldInstruction.class, instructions.get(call - 4));
        assertEquals("lightCoords", light.name().stringValue());
        assertConstant(instructions.get(call - 3), 0xFFFFFFFF);
        assertConstant(instructions.get(call - 2), 0); // transparent background
        assertConstant(instructions.get(call - 1), 0); // no outline
    }

    private static void assertConstant(Instruction instruction, int expected) {
        var constant = assertInstanceOf(ConstantInstruction.class, instruction);
        assertEquals(expected, constant.constantValue());
    }

    private static int invocation(List<Instruction> instructions, String name) {
        for (int index = 0; index < instructions.size(); index++) {
            if (instructions.get(index) instanceof InvokeInstruction call && call.name().equalsString(name)) {
                return index;
            }
        }
        return fail("Missing compiled rendering call: " + name);
    }

    private static List<Instruction> instructions(String resource, String method) throws IOException {
        try (var stream = ClientTextRenderingTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream, "Compiled client renderer must be present: " + resource);
            return ClassFile.of().parse(stream.readAllBytes()).methods().stream()
                    .filter(candidate -> candidate.methodName().equalsString(method))
                    .flatMap(candidate -> candidate.code().stream())
                    .flatMap(code -> code.elementStream())
                    .filter(Instruction.class::isInstance)
                    .map(Instruction.class::cast)
                    .toList();
        }
    }
}
