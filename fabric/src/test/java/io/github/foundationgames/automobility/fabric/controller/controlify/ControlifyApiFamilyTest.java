package io.github.foundationgames.automobility.fabric.controller.controlify;

import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Uses the two actual published SDK JARs, not replacement SDK classes. */
final class ControlifyApiFamilyTest {
    @Test
    void recognizesActualLegacySdk() throws Exception {
        try (var loader = sdkLoader("legacy", null)) {
            assertEquals(ControlifyApiFamily.LEGACY, ControlifyApiFamily.detect(loader));
        }
    }

    @Test
    void recognizesActualModernSdkWithoutLegacyGuideTypes() throws Exception {
        try (var loader = sdkLoader("modern", null)) {
            assertThrows(ClassNotFoundException.class,
                    () -> loader.loadClass("dev.isxander.controlify.api.guide.Fact"));
            assertEquals(ControlifyApiFamily.MODERN, ControlifyApiFamily.detect(loader));
        }
    }

    @Test
    void missingOptionalSdkIsUnsupported() {
        assertEquals(ControlifyApiFamily.UNSUPPORTED,
                ControlifyApiFamily.detect(ClassLoader.getPlatformClassLoader()));
    }

    @Test
    void incompleteModernApiIsNotMisclassifiedAsLegacy() throws Exception {
        try (var loader = sdkLoader("modern", "dev.isxander.controlify.api.contextual.ContextualStateSink")) {
            assertEquals(ControlifyApiFamily.UNSUPPORTED, ControlifyApiFamily.detect(loader));
        }
    }

    @Test
    void knownAdapterConstructionFailureIsNotSilenced() {
        var failure = assertThrows(IllegalStateException.class,
                () -> ControlifyApiFamily.createLegacyAdapter(FailingAdapter.class.getName(),
                        getClass().getClassLoader(), Runnable.class));
        assertNotNull(failure.getCause());
    }

    @Test
    void legacyFactoryOnlyLoadsRequestedOwnAdapter() {
        var adapter = ControlifyApiFamily.createLegacyAdapter(TestAdapter.class.getName(),
                getClass().getClassLoader(), Runnable.class);
        assertInstanceOf(TestAdapter.class, adapter);
    }

    public static final class TestAdapter implements Runnable {
        public TestAdapter() { }
        @Override public void run() { }
    }

    public static final class FailingAdapter implements Runnable {
        public FailingAdapter() { throw new IllegalStateException("expected construction failure"); }
        @Override public void run() { }
    }

    private URLClassLoader sdkLoader(String family, String blockedClass) throws Exception {
        String file = System.getProperty("controlify." + family + ".jar");
        assertNotNull(file, "Gradle must supply the exact real SDK artifact path");
        return new URLClassLoader(new URL[]{Path.of(file).toUri().toURL()}, getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals(blockedClass)) throw new ClassNotFoundException(name);
                // Never accidentally resolve the other API generation from a parent loader.
                if (name.startsWith("dev.isxander.controlify.")) {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> loaded = findLoadedClass(name);
                        if (loaded == null) loaded = findClass(name);
                        if (resolve) resolveClass(loaded);
                        return loaded;
                    }
                }
                return super.loadClass(name, resolve);
            }
        };
    }
}
