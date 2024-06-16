package com.the_codeboy.mensabot.listeners;

import com.github.codeboy.api.Mensa;
import com.github.codeboy.jokes4j.Jokes4J;
import com.github.codeboy.jokes4j.api.Flag;
import com.github.codeboy.jokes4j.api.JokeRequest;
import com.the_codeboy.mensabot.Bot;
import com.the_codeboy.mensabot.Config;
import com.the_codeboy.mensabot.MensaUtil;
import com.the_codeboy.mensabot.apis.AdviceApi;
import com.the_codeboy.mensabot.commands.*;
import com.the_codeboy.mensabot.commands.debug.GetQuotes;
import com.the_codeboy.mensabot.commands.debug.ListQuotes;
import com.the_codeboy.mensabot.commands.image.MorbCommand;
import com.the_codeboy.mensabot.commands.image.ShitCommand;
import com.the_codeboy.mensabot.commands.image.meme.*;
import com.the_codeboy.mensabot.commands.leaderboard.LeaderBoard;
import com.the_codeboy.mensabot.commands.mensa.*;
import com.the_codeboy.mensabot.commands.nils.ElMomentoCommand;
import com.the_codeboy.mensabot.commands.quotes.AddQuote;
import com.the_codeboy.mensabot.commands.quotes.AddQuoteList;
import com.the_codeboy.mensabot.commands.quotes.QuoteCommand;
import com.the_codeboy.mensabot.commands.secret.*;
import com.the_codeboy.mensabot.commands.sound.Queue;
import com.the_codeboy.mensabot.commands.sound.*;
import com.the_codeboy.mensabot.data.GuildData;
import com.the_codeboy.mensabot.data.GuildManager;
import com.the_codeboy.mensabot.events.MessageCommandEvent;
import com.the_codeboy.mensabot.events.SlashCommandCommandEvent;
import com.the_codeboy.mensabot.quotes.Quote;
import com.the_codeboy.mensabot.quotes.QuoteManager;
import com.the_codeboy.mensabot.util.Util;
import ml.codeboy.met.Weather4J;
import ml.codeboy.met.data.Forecast;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.MessageBuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceLeaveEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static com.the_codeboy.mensabot.WeatherUtil.generateForecastImage;

public class CommandHandler extends ListenerAdapter {
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Bot bot;
    private final HashMap<String, Command> commands = new HashMap<>();
    private final ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
    private final ExecutorService executor = Executors.newFixedThreadPool(10);
    private Guild server;

    public CommandHandler(Bot bot) {
        this.bot = bot;
        String serverID = Config.getInstance().serverId;
        try {
            bot.getJda().awaitReady();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (serverID != null)
            server = bot.getJda().getGuildById(serverID);

        this.registerKnownCommands();

        registerAnnouncements();
    }

    private void registerAnnouncements() {
        Date date = new Date();
        announceIn(60 * 60 * 20 - (date.getSeconds() + date.getMinutes() * 60 + date.getHours() * 3600), false);
        announceIn(60 * 60 * 7 - (date.getSeconds() + date.getMinutes() * 60 + date.getHours() * 3600), true);
    }

    private void announceIn(int seconds, boolean includeWeather) {
        if (seconds < 0)
            seconds += 24 * 60 * 60;
        executorService.scheduleAtFixedRate(() -> {
            sendMealsToAllGuilds();
            if (includeWeather)
                sendWeatherToAllGuilds();
        }, seconds, 24 * 60 * 60, TimeUnit.SECONDS);
    }

    private void sendMealsToGuild(GuildData data, Message message) {
        try {
            MessageChannel channel = (MessageChannel) getBot().getJda().getGuildChannelById(data.getUpdateChannelId());
            if (channel != null) {
                message = channel.sendMessage(message).complete();
                data.setLatestAnnouncementId(message.getId());
                data.save();
            }
        } catch (Exception ignored) {
        }
    }

    public void sendMealsToAllGuilds() {
        logger.info("Sending meals to guilds");
        List<GuildData> data = GuildManager.getInstance().getAllGuildData();
        while (!data.isEmpty()) {
            GuildData d = data.remove(0);
            Mensa mensa = d.getDefaultMensa();
            Date date = new Date(System.currentTimeMillis() + 1000 * 3600 * 5);
            ActionRow mealButtons = MensaUtil.createMealButtons(mensa, date);
            Message message = new MessageBuilder()
                    .setEmbeds(MensaUtil.MealsToEmbed(mensa, date).build())
                    .setActionRows(mealButtons).build();
            sendMealsToGuild(d, message);
            data.removeIf(g -> {
                if (g.getDefaultMensaId() == d.getDefaultMensaId()) {
                    sendMealsToGuild(g, message);
                    return true;
                }
                return false;
            });
        }
    }

    private void sendWeatherToGuild(Guild guild, File file) {
        GuildData data = GuildManager.getInstance().getData(guild);
        try {
            Mensa mensa = data.getDefaultMensa();
            MessageChannel channel = (MessageChannel) getBot().getJda().getGuildChannelById(data.getUpdateChannelId());
            if (channel != null) {

                channel.sendMessage(
                                "Forecast for " + mensa.getCity() + "\nData from The Norwegian Meteorological Institute")
                        .addFile(file, "weather_forecast.png").complete();
            }
        } catch (Exception ignored) {
        }
    }

    private void sendWeatherToAllGuilds() {
        logger.info("Sending weather to guilds");
        List<GuildData> data = GuildManager.getInstance().getAllGuildData();

        while (!data.isEmpty()) {
            GuildData d = data.remove(0);
            Mensa mensa = d.getDefaultMensa();

            List<Double> coordinates = mensa.getCoordinates();
            String lat = String.valueOf(coordinates.get(0)), lon = String.valueOf(coordinates.get(1));
            List<Forecast> forecasts = Weather4J.getForecasts(lat, lon);
            Instant now = Instant.now();
            while (forecasts.get(1).getTime().isBefore(now)) {
                forecasts.remove(0);
            }

            BufferedImage image = generateForecastImage(forecasts, 16);
            File file = new File("images/" + new Random().nextInt() + ".png");
            try {
                ImageIO.write(image, "png", file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            sendWeatherToGuild(d.getGuild(), file);
            data.removeIf(g -> {
                if (g.getDefaultMensaId() == d.getDefaultMensaId()) {
                    sendWeatherToGuild(g.getGuild(), file);
                    return true;
                }
                return false;
            });
            file.delete();
        }

    }

    private void createCommand(Class<? extends Command> command) {
        executor.execute(() -> {
            try {
                registerCommand(command.newInstance());
            } catch (InstantiationException | IllegalAccessException e) {
                logger.error("failed to create Command " + command.getName(), e);
            }
        });
    }

    public void registerKnownCommands() {
        this.registerCommand(new Help(bot));

        createCommand(ChuckNorrisJokeCommand.class);
        createCommand(JermaCommand.class);
        createCommand(TrumpQuoteCommand.class);
        createCommand(AdviceCommand.class);
        createCommand(NewsCommand.class);
        createCommand(InsultCommand.class);
        createCommand(RhymeCommand.class);
        createCommand(MemeCommand.class);
        createCommand(JokeCommand.class);
        createCommand(ShortsCommand.class);
        createCommand(WeatherCommand.class);
        createCommand(PingCommand.class);
        createCommand(ShittyTranslateCommand.class);
        createCommand(ASCIICommand.class);
        createCommand(GifCommand.class);
        // createCommand(StudydriveCommand.class);
        // had to remove this, see https://study.the-codeboy.com/

        createCommand(MensaCommand.class);
        createCommand(RateCommand.class);
        createCommand(DefaultMensaCommand.class);
        createCommand(MensaAnnounceChannelCommand.class);
        createCommand(DetailCommand.class);
        createCommand(AddImageCommand.class);

        createCommand(DönerrateCommand.class);
        createCommand(Dönertop.class);

        createCommand(ExecuteCommand.class);
        createCommand(LanguagesCommand.class);

        registerAudioCommands();

        createCommand(AddQuote.class);
        createCommand(AddQuoteList.class);
        createCommand(QuoteCommand.class);

        registerLeaderBoardCommands();

        registerImageCommands();

        registerSecretCommands();

        registerNilsCommands();

        registerDebugCommands();

        if (Config.getInstance().quoteStatus) {
            changeStatus();
        }
        try {
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            logger.error("bot did not start in time", e);
        }

        registerAllSlashCommands();
    }

    private void registerImageCommands() {
        createCommand(MorbCommand.class);
        createCommand(ShitCommand.class);
        createCommand(ChangeMyMindCommand.class);
        createCommand(HotlineBlingCommand.class);
        createCommand(TwoButtonsCommand.class);
        createCommand(Draw25Command.class);
        createCommand(DisasterGirlCommand.class);
        createCommand(SupermanCommand.class);
        createCommand(LatexCommand.class);
    }

    private void registerSecretCommands() {
        createCommand(RickRoll.class);
        createCommand(React.class);
        createCommand(Msg.class);
        createCommand(LoadKarma.class);
        createCommand(LoadSusCount.class);
        createCommand(Bee.class);
        createCommand(AcceptImage.class);
        createCommand(RejectImage.class);
        createCommand(SendImageInfo.class);
        createCommand(AnnounceCommand.class);
    }

    private void registerNilsCommands() {
        createCommand(ElMomentoCommand.class);
    }

    private void registerDebugCommands() {
        createCommand(ListQuotes.class);
        createCommand(GetQuotes.class);
    }

    private void changeStatus() {
        new Timer().schedule(new TimerTask() {
            public void run() {
                String status;
                do {
                    status = getRandomStatus();
                } while (status.length() > 128 || status.length() == 0);
                getBot().getJda().getPresence().setActivity(Activity.of(Activity.ActivityType.STREAMING, status,
                        "https://www.youtube.com/watch?v=dQw4w9WgXcQ&v=watch&feature=youtu.be"));
            }
        }, 0, 60_000);
    }

    private String getRandomStatus() {
        return Jokes4J.getInstance()
                .getJoke(
                        new JokeRequest.Builder().blackList(Flag.explicit, Flag.nsfw, Flag.racist, Flag.sexist).build())
                .getJoke();
    }

    private String getRandomAdviceStatus() {
        return AdviceApi.getInstance().getObject();
    }

    private String getRandomQuoteStatus() {
        Quote quote;
        String status;
        quote = QuoteManager.getInstance().getRandomQuote("Sun Tzu");
        if (quote == null)
            return "";
        status = "\"" + quote.getContent() +
                "\"\n - " + quote.getPerson();
        return status;
    }

    private void registerAudioCommands() {
        createCommand(fPlay.class);
        createCommand(Pause.class);
        createCommand(Resume.class);
        // createCommand(Echo.class);
        createCommand(Loop.class);
        createCommand(PlayNext.class);
        createCommand(Queue.class);
        createCommand(RemoveTrack.class);
        createCommand(Shuffle.class);
        createCommand(Skip.class);
        createCommand(Stop.class);
        createCommand(Volume.class);
        createCommand(CurrentTrack.class);
        createCommand(Leave.class);
        createCommand(Join.class);
    }

    private void registerLeaderBoardCommands() {
        LeaderBoard.registerAll(this);
    }

    public void registerCommand(Command command) {
        command.register(this);
        commands.put(command.getName().toLowerCase(), command);
        for (String alias : command.getAliases()) {
            commands.put(alias.toLowerCase(), command);
        }
        CommandData data = command.getCommandData();
        if (data != null && !command.isHidden())
            registerSlashCommand(data);
        logger.info("registered command " + command.getName());
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        String content = event.getMessage().getContentRaw();
        if (!event.isFromGuild() && !event.getAuthor().isBot()) {
            TextChannel channel = (TextChannel) getBot().getJda()
                    .getGuildChannelById(Config.getInstance().dmDebugChannel);
            if (channel != null) {
                if (event.getMessage().getContentRaw().length() > 0)
                    channel.sendMessageEmbeds(new EmbedBuilder()
                            .setAuthor(event.getAuthor().getAsTag() + " " + event.getAuthor().getAsMention())
                            .setDescription(content).setThumbnail(event.getAuthor().getAvatarUrl())
                            .setTimestamp(event.getMessage().getTimeCreated()).build()).queue();
                else {
                    for (Message.Attachment attachment : event.getMessage().getAttachments()) {
                        try {
                            channel.sendMessageEmbeds(new EmbedBuilder()
                                    .setAuthor(event.getAuthor().getAsTag() + " " + event.getAuthor().getAsMention())
                                    .setThumbnail(event.getAuthor().getAvatarUrl())
                                    .setTimestamp(event.getMessage().getTimeCreated())
                                    .setDescription(attachment.getDescription() + "").build()).queue();
                            channel.sendFile(attachment.getProxy().download().get(), attachment.getFileName())
                                    .complete();
                        } catch (InterruptedException | ExecutionException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
        if (!content.startsWith(Config.getInstance().prefix))
            return;
        content = content.replaceFirst(Config.getInstance().prefix, "");
        String cmd = content.split(" ", 2)[0];
        Command command = getCommand(cmd);
        if (command != null) {
            if (event.isFromGuild()) {
                logger.info(event.getGuild().getName() + ": " + event.getChannel().getName() + ": "
                        + event.getAuthor().getAsTag()
                        + ": " + event.getMessage().getContentRaw());
            } else {
                logger.info(event.getAuthor().getAsTag() + ": " + event.getMessage().getContentRaw());
            }

            command.execute(new MessageCommandEvent(event, command));
        }
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        Command command = getCommand(event.getName());
        if (command != null) {
            logger.info((event.getGuild() != null ? event.getGuild().getName() + ": " + event.getChannel().getName()
                    : event.getChannel().getName())
                    + ": " + event.getUser().getAsTag()
                    + ": " + event.getCommandString());
            command.execute(new SlashCommandCommandEvent(event, command));
        }
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteractionEvent event) {
        Command command = getCommand(event.getName());
        if (command != null) {
            command.autoComplete(event);
        }
    }

    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        String emote = event.getReaction().getEmoji().getAsReactionCode();

        boolean upvote = Config.getInstance().isUpvote(emote);
        boolean downVote = Config.getInstance().isDownvote(emote);
        boolean sus = Config.getInstance().isSus(emote);

        if (upvote || downVote) {
            Message message = event.getChannel().retrieveMessageById(event.getMessageId()).complete();
            Util.addKarma(message.getAuthor(), upvote ? 1 : -1);
        }

        if (sus) {
            Message message = event.getChannel().retrieveMessageById(event.getMessageId()).complete();
            Util.addSusCount(message.getAuthor(), 1);
        }

    }

    @Override
    public void onMessageReactionRemove(@NotNull MessageReactionRemoveEvent event) {
        String emote = event.getReaction().getEmoji().getAsReactionCode();

        boolean upvote = Config.getInstance().isUpvote(emote);
        boolean downVote = Config.getInstance().isDownvote(emote);
        boolean sus = Config.getInstance().isSus(emote);

        if (upvote || downVote) {
            Message message = event.getChannel().retrieveMessageById(event.getMessageId()).complete();
            Util.addKarma(message.getAuthor(), upvote ? -1 : 1);// removing upvotes => remove karma
        }

        if (sus) {
            Message message = event.getChannel().retrieveMessageById(event.getMessageId()).complete();
            Util.addSusCount(message.getAuthor(), -1);
        }
    }

    private void registerAllSlashCommands() {
        CommandListUpdateAction action = getBot().getJda().updateCommands();
        for (Command command : getCommands()) {
            CommandData commandData = command.getCommandData();
            if (!command.isHidden() && commandData != null)
                action = action.addCommands(commandData);
        }
        action.queue();
    }

    /**
     * this is used to automatically register slash commands to a test server
     * (faster than global registration)
     */
    private void registerSlashCommand(CommandData data) {
        if (getServer() != null)
            getServer().upsertCommand(data).queue();
    }

    @Override
    public void onGuildVoiceLeave(@NotNull GuildVoiceLeaveEvent event) {
        if (event.getChannelLeft().getMembers().size() == 1) {
            AudioChannel connectedChannel = event.getGuild().getSelfMember().getVoiceState().getChannel();
            if (connectedChannel != event.getChannelLeft())
                return;

            PlayerManager.getInstance().destroy(event.getGuild());
        }
    }

    private Bot getBot() {
        return bot;
    }

    public Command getCommand(String name) {
        return commands.get(name.toLowerCase());
    }

    public Collection<Command> getCommands() {
        return new HashSet<>(commands.values());
    }

    public Guild getServer() {
        return server;
    }

    public void sendAnnouncementToAllGuilds(Message message) {
        List<GuildData> data = GuildManager.getInstance().getAllGuildData();
        for (GuildData d : data) {
            try {
                MessageChannel channel = (MessageChannel) getBot().getJda().getGuildChannelById(d.getUpdateChannelId());
                if (channel != null) {
                    channel.sendMessage(message).queue();
                }
            } catch (Exception ignored) {
            }
        }
    }
}