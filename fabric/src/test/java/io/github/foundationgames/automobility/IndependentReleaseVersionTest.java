package io.github.foundationgames.automobility;

import com.google.gson.JsonParser;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.metadata.version.VersionPredicate;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/** Fabric's real version parser; no claim of game or third-party compatibility. */
class IndependentReleaseVersionTest {
    private Properties releases() throws Exception {
        var values = new Properties();
        try (var input = Files.newInputStream(Path.of("../release-versions.properties"))) {
            values.load(input);
        }
        return values;
    }

    @Test
    void independentAutomobilityOrdersAfterThePreviousUpstreamBasedLine() throws Exception {
        var current = Version.parse(releases().getProperty("mod_version"));
        assertTrue(current.compareTo(Version.parse("0.5.0-unofficial.36.garage.5")) > 0);
        assertTrue(VersionPredicate.parse(">=0.5.0-unofficial.35").test(current));
        assertTrue(VersionPredicate.parse(">=0.5.0-unofficial.36").test(current));
    }

    @Test
    void independentMomentumOrdersAfterBothPreviousLaneVersions() throws Exception {
        var current = Version.parse(releases().getProperty("momentum_version"));
        assertTrue(current.compareTo(Version.parse("0.1.11-unofficial.10.pw1")) > 0);
        assertTrue(current.compareTo(Version.parse("0.1.11-unofficial.11.pw1")) > 0);
    }

    @Test
    void developmentMinimumAcceptsTheCurrentBuildButNotAnOldAutomobility() throws Exception {
        var values = releases();
        var predicate = VersionPredicate.parse(">=" + values.getProperty("momentum_automobility_min_version"));
        var current = Version.parse(values.getProperty("mod_version") + "+26.2-fabric");
        assertTrue(predicate.test(current));
        assertFalse(predicate.test(Version.parse("0.5.0-unofficial.36.garage.5")));
        assertTrue(Version.parse("1.0.0-dev.1").compareTo(Version.parse("1.0.0")) < 0);
        assertFalse(VersionPredicate.parse(">=1.0.0").test(Version.parse("1.0.0-dev.1")));
    }

    @Test
    void processedMetadataUsesTheSameFullVersionAsTheArtifact() throws Exception {
        var metadata = JsonParser.parseString(Files.readString(Path.of("build/resources/main/fabric.mod.json"))).getAsJsonObject();
        assertEquals("automobility", metadata.get("id").getAsString());
        assertEquals(releases().getProperty("mod_version") + "+26.2-fabric", metadata.get("version").getAsString());
        assertEquals("26.2", metadata.getAsJsonObject("depends").get("minecraft").getAsString());
    }
}
