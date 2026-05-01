package de.cb.drones.drone;

import java.util.Map;

public record GuiSettings(
        String title,
        int size,
        Map<String, GuiItem> items,
        GuiItem fillItem
) {}
