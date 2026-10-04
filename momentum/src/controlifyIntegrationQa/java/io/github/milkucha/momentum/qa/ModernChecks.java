package io.github.milkucha.momentum.qa;

import dev.isxander.controlify.api.bind.ControlifyBindApi;
import dev.isxander.controlify.api.bind.RadialIcon;
import dev.isxander.controlify.api.contextual.GuideLocation;
import dev.isxander.controlify.bindings.BindContext;
import dev.isxander.controlify.bindings.RadialIconManager;
import dev.isxander.controlify.contextual.ContextualState;
import dev.isxander.controlify.contextual.GuideRule;
import dev.isxander.controlify.contextual.RuleEngine;
import dev.isxander.controlify.contextual.RuleSetManager;
import io.github.milkucha.momentum.qa.mixin.RadialIconManagerAccessor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.github.milkucha.momentum.qa.ControlifyIntegrationQa.require;

/** Loaded only after detecting the real modern SDK. Reads applied state, never registers substitutes. */
final class ModernChecks {
    private static final Logger LOG = LoggerFactory.getLogger("MomentumControlifyIntegrationQa");
    private static final Identifier DOMAIN = id("controlify:in_game");
    private static final Map<String, String> EXPECTED_MODELS = Map.of(
            "momentum:cruise_control", "minecraft:compass",
            "momentum:cruise_increase", "minecraft:lime_dye",
            "momentum:cruise_decrease", "minecraft:red_dye",
            "momentum:cruise_resume", "minecraft:clock");

    private ModernChecks() { }

    static boolean resourcesReady() {
        return RuleSetManager.GUIDE_RULES.getRuleEngine(null, DOMAIN).isPresent();
    }

    static int verify(String phase) {
        int checks = 0;
        RuleEngine<GuideRule.Key, GuideRule> engine = RuleSetManager.GUIDE_RULES.getRuleEngine(null, DOMAIN)
                .orElseThrow(() -> new IllegalStateException("No applied in-game guide rule engine"));
        List<GuideRule> addonRules = engine.rules().stream().filter(ModernChecks::isAddon).toList();
        Set<String> ruleIds = addonRules.stream().map(rule -> rule.binding().bindId().toString()).collect(Collectors.toSet());
        require(addonRules.size() == 7 && ruleIds.equals(ControlifyIntegrationQa.EXPECTED_IDS),
                "Expected seven unique applied addon guide rules, found " + ruleIds + " count=" + addonRules.size());
        checks++;
        for (GuideRule rule : addonRules) {
            String bindingId = rule.binding().bindId().toString();
            boolean core = ControlifyIntegrationQa.CORE_IDS.contains(bindingId);
            require(rule.location() == (core ? GuideLocation.LEFT : GuideLocation.RIGHT), "Wrong guide location: " + bindingId);
            String key = core ? "controlify.binding.automobility." + bindingId.substring(bindingId.indexOf(':') + 1)
                    : "key.momentum." + bindingId.substring(bindingId.indexOf(':') + 1);
            require(rule.text().getContents() instanceof TranslatableContents text && text.getKey().equals(key),
                    "Wrong applied guide translation: " + bindingId + " text=" + rule.text());
            Set<Identifier> dependencies = new HashSet<>();
            dependencies.add(id(core ? "automobility:driving_automobile" : "momentum:driving_automobile"));
            if (bindingId.equals("momentum:cruise_increase") || bindingId.equals("momentum:cruise_decrease")) {
                dependencies.add(id("momentum:cruise_active"));
            }
            if (bindingId.equals("momentum:cruise_resume")) dependencies.add(id("momentum:cruise_resumable"));
            require(rule.predicate().factDependencies().equals(dependencies), "Wrong applied fact dependencies: " + bindingId);
            checks += 3;
        }
        for (String sentinel : List.of("controlify:jump", "controlify:inventory")) {
            require(engine.rules().stream().anyMatch(rule -> rule.binding().bindId().toString().equals(sentinel)),
                    "Vanilla guide was replaced: " + sentinel);
            require(ControlifyBindApi.get().getAllBindIds().anyMatch(binding -> binding.toString().equals(sentinel)),
                    "Vanilla binding is missing: " + sentinel);
            checks += 2;
        }
        require(BindContext.CONTEXTS.containsKey(id("automobility:driving")), "Core driving context missing");
        require(BindContext.CONTEXTS.containsKey(id("momentum:driving")), "Momentum driving context missing");
        checks += 2;
        Map<Identifier, RadialIcon> appliedIcons = ((RadialIconManagerAccessor) (Object) RadialIconManager.INSTANCE).momentumQa$appliedIcons();
        for (Map.Entry<String, String> expected : EXPECTED_MODELS.entrySet()) {
            RadialIcon icon = appliedIcons.get(id(expected.getKey()));
            require(icon != null && icon.content() instanceof RadialIcon.Model model && model.id().toString().equals(expected.getValue()),
                    "Wrong or missing APPLIED radial icon for " + expected.getKey() + ": " + icon);
            checks++;
        }
        require(appliedIcons.containsKey(id("controlify:jump")), "Vanilla radial icons were replaced");
        checks++;
        assertRules(engine, false, false, false, false, Set.of());
        assertRules(engine, true, true, false, false, withCore("momentum:cruise_control"));
        assertRules(engine, true, true, true, false, withCore("momentum:cruise_control", "momentum:cruise_increase", "momentum:cruise_decrease"));
        assertRules(engine, true, true, false, true, withCore("momentum:cruise_control", "momentum:cruise_resume"));
        assertRules(engine, true, false, true, true, ControlifyIntegrationQa.CORE_IDS);
        assertRules(engine, false, false, true, true, Set.of());
        checks += 6;
        LOG.info("CONTROLIFY_INTEGRATION_QA_RESOURCES phase={} checks={} bindings=7 guides=7 radial=4 syntheticFactCases=6 vanillaPreserved=true physicalController=NOT_PROVEN",
                phase, checks);
        return checks;
    }

    private static void assertRules(RuleEngine<GuideRule.Key, GuideRule> engine, boolean coreDriving,
                                    boolean momentumDriving, boolean active, boolean resumable, Set<String> expected) {
        ContextualState state = new ContextualState(Map.of(
                id("automobility:driving_automobile"), coreDriving,
                id("momentum:driving_automobile"), momentumDriving,
                id("momentum:cruise_active"), active,
                id("momentum:cruise_resumable"), resumable), Map.of(), Map.of(), Map.of());
        Set<String> actual = engine.evaluate(state, ModernChecks::isAddon).stream()
                .map(rule -> rule.binding().bindId().toString()).collect(Collectors.toSet());
        require(actual.equals(expected), "Applied guide truth-table mismatch for " + state.facts() + ": " + actual + " expected=" + expected);
    }

    private static boolean isAddon(GuideRule rule) {
        return ControlifyIntegrationQa.EXPECTED_IDS.contains(rule.binding().bindId().toString());
    }

    private static Set<String> withCore(String... momentumIds) {
        Set<String> result = new HashSet<>(ControlifyIntegrationQa.CORE_IDS);
        result.addAll(List.of(momentumIds));
        return result;
    }

    private static Identifier id(String value) {
        int colon = value.indexOf(':');
        return Identifier.fromNamespaceAndPath(value.substring(0, colon), value.substring(colon + 1));
    }
}
