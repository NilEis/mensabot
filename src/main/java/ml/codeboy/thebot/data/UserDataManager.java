package ml.codeboy.thebot.data;

import com.google.gson.Gson;
import net.dv8tion.jda.api.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

public class UserDataManager {
    private static final String userDataFolder = "users";
    private static final UserDataManager instance = new UserDataManager();
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final HashMap<String, UserData> userData = new HashMap<>();

    private final long karmaTopUpdate = 600000;
    private final Thread initThread;
    private long lastUpdatedKarmaTop = 0;
    private List<UserData> karmaSorted;

    private UserDataManager() {
        initThread = new Thread(this::loadUserData);
        initThread.start();
    }

    public static UserDataManager getInstance() {
        return instance;
    }

    public UserData getData(User user) {
        return getData(user.getId());
    }

    public UserData loadData(User user) throws FileNotFoundException {
        return loadData(user.getId());
    }

    private UserData loadData(String id) throws FileNotFoundException {
        UserData data = new Gson().fromJson(new FileReader(userDataFolder + File.separator + id), UserData.class);
        return data;
    }

    public void save(UserData data) {
        try {
            new File(userDataFolder).mkdirs();
            FileWriter writer = new FileWriter(userDataFolder + File.separator + data.getId());
            new Gson().toJson(data, writer);
            writer.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public UserData getData(String userId) {
        waitTilInit();
        UserData data = userData.get(userId);
        if (data != null) {
            return data;
        }
        try {
            return loadData(userId);
        } catch (FileNotFoundException ignored) {
        }
        data = new UserData(userId);
        userData.put(userId, data);
        return data;
    }

    public Collection<UserData> getAllUserData() {
        waitTilInit();
        return userData.values();
    }

    private void waitTilInit() {
        if (initThread != null) {
            try {
                initThread.join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void loadUserData() {
        File folder = new File(userDataFolder);
        if (folder.exists()) {
            for (File file : folder.listFiles()) {
                try {
                    userData.put(file.getName(), loadData(file.getName()));
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
        logger.info("finished loading user data for users");
    }


    public List<UserData> getKarmaSorted() {
        if (System.currentTimeMillis() - karmaTopUpdate > lastUpdatedKarmaTop) {
            updateKarmaTop();
        }
        karmaSorted.sort(Comparator.comparingInt(UserData::getKarma).reversed());
        return karmaSorted;
    }

    private void updateKarmaTop() {
        karmaSorted = new ArrayList<>(getAllUserData());
        karmaSorted.removeIf(d -> d.getKarma() == 0);
        karmaSorted.sort(Comparator.comparingInt(UserData::getKarma).reversed());
//            karmaSorted = karmaSorted.subList(0, 20);
        lastUpdatedKarmaTop = System.currentTimeMillis();
    }
}
