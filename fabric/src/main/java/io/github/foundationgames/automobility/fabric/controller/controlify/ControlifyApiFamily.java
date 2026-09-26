package io.github.foundationgames.automobility.fabric.controller.controlify;

/** Public API capability checks only: no SDK initialization and no private Controlify hooks. */
public enum ControlifyApiFamily {
    LEGACY, MODERN, UNSUPPORTED;

    private static final String ROOT = "dev.isxander.controlify.";

    public static ControlifyApiFamily detect(ClassLoader loader) {
        try {
            Class<?> preInit = sdk(loader, "api.entrypoint.PreInitContext");
            Class<?> bindings = sdk(loader, "api.bind.ControlifyBindApi");
            Class<?> supplier = sdk(loader, "api.bind.InputBindingSupplier");
            Class<?> controller = sdk(loader, "controller.ControllerEntity");
            Class<?> binding = sdk(loader, "api.bind.InputBinding");
            Class<?> builder = sdk(loader, "api.bind.InputBindingBuilder");
            Class<?> id = Class.forName("net.minecraft.resources.Identifier", false, loader);
            requireReturn(preInit, "bindings", bindings);
            requireReturn(bindings, "registerBinding", supplier, sdk(loader, "api.bind.ControlifyBindApi$RegistryCallback"));
            requireReturn(bindings, "registerBindContext", void.class, sdk(loader, "bindings.BindContext"));
            requireReturn(supplier, "on", binding, controller);
            requireReturn(binding, "justPressed", boolean.class);
            requireReturn(binding, "digitalNow", boolean.class);
            requireReturn(builder, "radialCandidate", builder, id);

            if (hasMethod(preInit, "contextualDomains")) {
                Class<?> domains = sdk(loader, "api.contextual.ContextualDomainRegistry");
                Class<?> domain = sdk(loader, "api.contextual.ContextualDomain");
                requireReturn(preInit, "contextualDomains", domains);
                requireReturn(domains, "inGame", domain);
                requireReturn(domain, "registerContributor", void.class, sdk(loader, "api.contextual.ContextualStateContributor"));
                requireReturn(sdk(loader, "api.contextual.ContextualStateSink"), "contributeFact", void.class, id, boolean.class);
                requireReturn(builder, "radialCandidate", builder, boolean.class);
                sdk(loader, "api.contextual.InGameContext");
                return MODERN;
            }
            if (hasMethod(preInit, "guideRegistries")) {
                requireReturn(preInit, "guideRegistries", sdk(loader, "api.guide.GuideDomainRegistries"));
                Class<?> icon = sdk(loader, "api.bind.RadialIcon");
                if (!icon.isInterface()) return UNSUPPORTED;
                requireReturn(bindings, "registerRadialIcon", void.class, id, icon);
                for (String name : new String[]{"Fact", "FactProvider", "InGameCtx", "Rule", "RuleBuilder", "ActionLocation"}) {
                    sdk(loader, "api.guide." + name);
                }
                return LEGACY;
            }
        } catch (ReflectiveOperationException | LinkageError unavailableApi) {
            // An unknown optional SDK must not take keyboard controls down with it.
        }
        return UNSUPPORTED;
    }

    private static Class<?> sdk(ClassLoader loader, String name) throws ClassNotFoundException {
        return Class.forName(ROOT + name, false, loader);
    }

    private static boolean hasMethod(Class<?> owner, String name) {
        try {
            owner.getMethod(name);
            return true;
        } catch (NoSuchMethodException absent) {
            return false;
        }
    }

    private static void requireReturn(Class<?> owner, String name, Class<?> result, Class<?>... parameters)
            throws NoSuchMethodException {
        if (owner.getMethod(name, parameters).getReturnType() != result) {
            throw new NoSuchMethodException(owner.getName() + "." + name + " return type changed");
        }
    }

    /** Reflection is confined to our own legacy implementation, compiled against the old SDK. */
    public static <T> T createLegacyAdapter(String className, ClassLoader loader, Class<T> contract) {
        try {
            return contract.cast(Class.forName(className, true, loader).getConstructor().newInstance());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Failed to create recognized legacy Controlify adapter " + className, failure);
        }
    }
}
