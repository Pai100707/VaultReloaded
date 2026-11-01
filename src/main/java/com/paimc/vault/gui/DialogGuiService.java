package com.paimc.vault.gui;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Small Paper Dialog API adapter used by configured GUI layouts. */
public final class DialogGuiService {
    public static final class Action {
        private final String id;
        private final String label;
        private final String tooltip;
        private final Consumer<Player> callback;

        public Action(String id, String label, String tooltip, Consumer<Player> callback) {
            this.id = Objects.requireNonNull(id, "id");
            this.label = Objects.requireNonNull(label, "label");
            this.tooltip = tooltip;
            this.callback = Objects.requireNonNull(callback, "callback");
        }

        public String id() { return id; }
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    public void show(Player player, String title, String description, List<Action> actions, int columns) {
        List<ActionButton> buttons = new ArrayList<>();
        for (Action action : actions) {
            ActionButton.Builder button = ActionButton.builder(text(action.label));
            if (action.tooltip != null && !action.tooltip.isBlank()) button.tooltip(text(action.tooltip));
            button.action(DialogAction.customClick((view, audience) -> {
                if (audience instanceof Player target) action.callback.accept(target);
            }, net.kyori.adventure.text.event.ClickCallback.Options.builder().uses(1).build()));
            buttons.add(button.build());
        }
        if (buttons.isEmpty()) return;

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(text(title))
                        .body(List.of(DialogBody.plainMessage(text(description))))
                        .canCloseWithEscape(true)
                        .build())
                .type(DialogType.multiAction(buttons, null, Math.max(1, columns))));
        player.showDialog(dialog);
    }

    private static Component text(String legacyText) {
        String raw = legacyText == null ? "" : org.bukkit.ChatColor.translateAlternateColorCodes('&', legacyText);
        return LEGACY.deserialize(raw);
    }
}
