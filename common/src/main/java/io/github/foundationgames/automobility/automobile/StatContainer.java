package io.github.foundationgames.automobility.automobile;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.function.Consumer;

public interface StatContainer<C extends StatContainer<C>> {
    Identifier containerId();

    default String getContainerTextKey() {
        var id = this.containerId();
        return id.getNamespace()+"."+id.getPath();
    }

    void forEachStat(Consumer<DisplayStat<C>> action);

    default void appendTexts(Consumer<Component> tooltipAdder, C container) {
        this.forEachStat(stat -> stat.appendTooltip(tooltipAdder, container));
    }
}
