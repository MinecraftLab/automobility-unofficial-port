package io.github.foundationgames.automobility.automobile.render;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.rendertype.RenderTypes;

public class EmptyModel extends Model<BaseModel.RenderState> {
    public EmptyModel() {
        super(BaseModel.PART_EMPTY, RenderTypes::entitySolid);
    }
}
