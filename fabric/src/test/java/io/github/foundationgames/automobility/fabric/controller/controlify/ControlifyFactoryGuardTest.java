package io.github.foundationgames.automobility.fabric.controller.controlify;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import static org.junit.jupiter.api.Assertions.*;

class ControlifyFactoryGuardTest {
    @Test
    void compiledFactoryRejectsUnsupportedApiBeforeLoadingControllerAndCatchesLinkageErrors() throws Exception {
        var path = "/io/github/foundationgames/automobility/fabric/FabricPlatform.class";
        try (var input = getClass().getResourceAsStream(path)) {
            assertNotNull(input);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            var method = node.methods.stream().filter(m -> m.name.equals("createControlifyController")).findFirst().orElseThrow();
            int detection = -1, classLoad = -1, index = 0, fallbacks = 0;
            boolean unsupportedCompared = false;
            for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    if (call.owner.endsWith("/ControlifyApiFamily") && call.name.equals("detect")) detection = index;
                    if (call.owner.equals("java/lang/Class") && call.name.equals("forName")) classLoad = index;
                }
                if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.GETSTATIC) {
                    if (field.owner.endsWith("/ControlifyApiFamily") && field.name.equals("UNSUPPORTED")) unsupportedCompared = true;
                    if (field.owner.endsWith("/AutomobileController") && field.name.equals("INCOMPATIBLE")) fallbacks++;
                }
                index++;
            }
            assertTrue(detection >= 0 && classLoad > detection, "Detect SDK family before loading optional controller implementation");
            assertTrue(unsupportedCompared);
            assertEquals(2, fallbacks, "Unsupported family and initialization failure both retain keyboard controls");
            assertTrue(method.tryCatchBlocks.stream().anyMatch(c -> "java/lang/LinkageError".equals(c.type)));
            assertTrue(method.tryCatchBlocks.stream().anyMatch(c -> "java/lang/ReflectiveOperationException".equals(c.type)));
        }
    }
}
