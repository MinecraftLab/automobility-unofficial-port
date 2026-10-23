package io.github.foundationgames.automobility.automobile.render.obj;

import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

/** Explicit main-body scope; never opt wheels, hinges or unknown resource transforms in. */
public final class RetainedBodyScope {
    private static final Identifier T3 = Identifier.fromNamespaceAndPath("automobility", "frame/vw_t3");
    private static final Identifier DEFENDER = Identifier.fromNamespaceAndPath("automobility", "frame/defender_90");
    private static final Identifier MX5 = Identifier.fromNamespaceAndPath("automobility", "frame/mx5_na");
    private RetainedBodyScope() { }

    public static boolean supports(Identifier model) {
        return T3.equals(model) || DEFENDER.equals(model) || MX5.equals(model);
    }

    public static boolean identityDefinition(Vector3f translation, Vector3f rotation, Vector3f scale) {
        return translation.equals(0, 0, 0) && rotation.equals(0, 0, 0) && scale.equals(1, 1, 1);
    }
}
