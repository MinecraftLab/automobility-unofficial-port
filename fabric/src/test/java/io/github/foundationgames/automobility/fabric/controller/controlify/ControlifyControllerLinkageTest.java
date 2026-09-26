package io.github.foundationgames.automobility.fabric.controller.controlify;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

/** Checks every SDK call, including rumble and lambda method handles, without a controller. */
final class ControlifyControllerLinkageTest {
    private record Call(String owner, String name, String descriptor, boolean ownerInterface) { }

    @Test
    void everyCompiledControllerSdkCallExistsInBothRealSdkJars() throws Exception {
        Set<Call> calls = new HashSet<>();
        try (var input = getClass().getResourceAsStream("ControlifyController.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        private void collect(String owner, String method, String desc, boolean itf) {
                            if (owner.startsWith("dev/isxander/controlify/")) calls.add(new Call(owner, method, desc, itf));
                        }
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
                            collect(owner, method, desc, itf);
                        }
                        @Override
                        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrap, Object... args) {
                            for (Object arg : args) {
                                if (arg instanceof Handle method) {
                                    collect(method.getOwner(), method.getName(), method.getDesc(), method.isInterface());
                                }
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertTrue(calls.stream().anyMatch(call -> call.owner().contains("rumble/")));
        assertTrue(calls.stream().anyMatch(call -> call.name().equals("digitalNow")), "Lambda SDK calls must be included");
        for (String family : new String[]{"legacy", "modern"}) {
            try (var sdk = new ZipFile(Path.of(System.getProperty("controlify." + family + ".jar")).toFile())) {
                for (Call call : calls) {
                    var entry = sdk.getEntry(call.owner() + ".class");
                    assertNotNull(entry, family + " lacks " + call.owner());
                    boolean[] found = {false};
                    try (var input = sdk.getInputStream(entry)) {
                        new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                                assertEquals(call.ownerInterface(), (access & Opcodes.ACC_INTERFACE) != 0,
                                        family + " changed class/interface kind for " + call.owner());
                            }
                            @Override
                            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                                if (name.equals(call.name()) && descriptor.equals(call.descriptor())
                                        && (access & Opcodes.ACC_PUBLIC) != 0) found[0] = true;
                                return null;
                            }
                        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    }
                    assertTrue(found[0], family + " lacks public SDK symbol " + call);
                }
            }
        }
    }
}
