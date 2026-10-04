package io.github.foundationgames.automobility.fabric.block.render;

import net.fabricmc.fabric.api.client.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.client.renderer.v1.mesh.ShadeMode;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class FabricGeometryBuilder {
    private final QuadEmitter quads;
    private final Matrix4fc transform;

    private int index = 0;
    private @Nullable Material.Baked material = null;

    public FabricGeometryBuilder(QuadEmitter quads, Matrix4fc transform) {
        this.quads = quads;
        this.transform = transform;
    }

    public FabricGeometryBuilder vertex(float x, float y, float z, @Nullable Direction face, float nx, float ny, float nz, Material.Baked material, float u, float v) {
        return this.vertex(x, y, z, face, nx, ny, nz, material, u, v, 0xFFFFFFFF);
    }

    public FabricGeometryBuilder vertex(float x, float y, float z, @Nullable Direction face, float nx, float ny, float nz, Material.Baked material, float u, float v, int color) {
        if (index == 0) {
            this.material = material;
            quads.clear();
            quads.ambientOcclusion(TriState.DEFAULT);
            quads.shadeMode(ShadeMode.VANILLA);
        }

        var pos = new Vector4f(x - 0.5f, y, z - 0.5f, 1);
        var tNormal = new Vector4f(nx, ny, nz, 1);
        pos.mul(this.transform);
        tNormal.mul(this.transform); // This is under the assumption that transform will always be a rotation

        var normal = new Vector3f(tNormal.x(), tNormal.y(), tNormal.z());
        normal.normalize();

        quads.pos(index, pos.x() + 0.5f, pos.y(), pos.z() + 0.5f);
        if (face != null) {
            face = Direction.rotate(this.transform, face);
            quads.cullFace(face);
        }
        quads.nominalFace(face);
        quads.normal(index, normal.x(), normal.y(), normal.z());
        quads.color(index, color);
        quads.uv(index, u, v);

        if (++index >= 4) {
            quads.materialBake(this.material, MutableQuadView.BAKE_NORMALIZED);
            quads.emit();
            index = 0;
            this.material = null;
        }

        return this;
    }
}
