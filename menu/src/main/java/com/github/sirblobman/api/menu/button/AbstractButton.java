package com.github.sirblobman.api.menu.button;

import org.jetbrains.annotations.NotNull;

import org.bukkit.event.inventory.InventoryClickEvent;

public abstract class AbstractButton {
    public abstract void onClick(@NotNull InventoryClickEvent e);
}
