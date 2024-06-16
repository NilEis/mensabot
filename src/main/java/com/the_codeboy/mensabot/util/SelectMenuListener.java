package com.the_codeboy.mensabot.util;

import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent;

public interface SelectMenuListener {
    /**
     * @param event the event
     * @return true when the listener should be unregistered after this event
     */
    boolean onSelectMenuInteraction(SelectMenuInteractionEvent event);
}
