package com.the_codeboy.mensabot.commands;

import com.the_codeboy.mensabot.data.UserDataManager;
import com.the_codeboy.mensabot.events.CommandEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

import java.util.List;

public class Karma extends Command {
    public Karma() {
        super("Karma", "gives the karma of a user");
        setGuildOnlyCommand(false);
    }

    @Override
    public SlashCommandData getCommandData() {
        return super.getCommandData().addOption(OptionType.USER, "user", "the user to get the karma of", false);
    }

    @Override
    public void run(CommandEvent event) {
        User user = event.getUser();
        if (event.isSlashCommandEvent()) {
            OptionMapping om = event.getSlashCommandEvent().getOption("user");
            if (om != null)
                user = om.getAsMember().getUser();
        } else if (event.isMessageEvent()) {
            List<Member> members = event.getMessageReceivedEvent().getMessage().getMentions().getMembers();
            if (!members.isEmpty() && members.get(0) != null)
                user = members.get(0).getUser();
        }
        int karma = UserDataManager.getInstance().getData(user).getKarma();
        EmbedBuilder builder = event.getBuilder();
        builder.setTitle("Karma of " + user.getAsTag())
                .setDescription(karma + "");
        event.reply(builder);
    }
}
