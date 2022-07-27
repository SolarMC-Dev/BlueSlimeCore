package com.github.sirblobman.api.language;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.github.sirblobman.api.configuration.ConfigurationManager;
import com.github.sirblobman.api.configuration.IResourceHolder;
import com.github.sirblobman.api.utility.MessageUtility;
import com.github.sirblobman.api.utility.Validate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LanguageManager {
    private static final String[] KNOWN_LANGUAGE_ARRAY;
    
    static {
        // Last Updated: June 28, 2022 18:03
        KNOWN_LANGUAGE_ARRAY = new String[] {
                "af_za", "ar_sa", "ast_es", "az_az", "ba_ru", "bar", "be_by", "bg_bg", "br_fr", "brb", "bs_ba",
                "ca_es", "cs_cz", "cy_gb", "da_dk", "de_at", "de_ch", "de_de", "el_gr", "en_au", "en_ca", "en_gb",
                "en_nz", "en_pt", "en_ud", "en_us", "enp", "enws", "eo_uy", "es_ar", "es_cl", "es_ec", "es_es",
                "es_mx", "es_uy", "es_ve", "esan", "et_ee", "eu_es", "fa_ir", "fi_fi", "fil_ph", "fo_fo", "fr_ca",
                "fr_fr", "fra_de", "fy_nl", "ga_ie", "gd_gb", "gl_es", "got_de", "gv_im", "haw_us", "he_il",
                "hi_in", "hr_hr", "hu_hu", "hy_am", "id_id", "ig_ng", "io_en", "is_is", "isv", "it_it", "ja_jp",
                "jbo_en", "ka_ge", "kab_kab", "kk_kz", "kn_in", "ko_kr", "ksh", "kw_gb", "la_la", "lb_lu", "li_li",
                "lol_us", "lt_lt", "lv_lv", "mi_nz", "mk_mk", "mn_mn", "moh_ca", "ms_my", "mt_mt", "nds_de", "nl_be",
                "nl_nl", "nn_no", "no_no", "nb_no", "nuk", "oc_fr", "oj_ca", "ovd", "pl_pl", "pt_br", "pt_pt",
                "qya_aa", "ro_ro", "rpr", "ru_ru", "scn", "se_no", "sk_sk", "sl_si", "so_so", "sq_al", "sr_sp",
                "sv_se", "swg", "sxu", "szl", "ta_in", "th_th", "tl_ph", "tlh_aa", "tr_tr", "tt_ru", "tzl_tzl",
                "uk_ua", "val_es", "vec_it", "vi_vn", "yi_de", "yo_ng", "zh_cn", "zh_hk", "zh_tw"
        };
    }

    private final ConfigurationManager configurationManager;
    private final Map<String, Language> languageMap;

    private String defaultLanguageName;
    private String consoleLanguageName;
    private Language defaultLanguage;
    private Language consoleLanguage;
    private boolean forceDefaultLanguage;

    public LanguageManager(ConfigurationManager configurationManager) {
        Validate.notNull(configurationManager, "configurationManager must not be null!");
        this.configurationManager = configurationManager;
        this.languageMap = new ConcurrentHashMap<>();

        this.defaultLanguageName = null;
        this.consoleLanguageName = null;
        this.defaultLanguage = null;
        this.consoleLanguage = null;
        this.forceDefaultLanguage = false;
    }

    @NotNull
    public ConfigurationManager getConfigurationManager() {
        return this.configurationManager;
    }

    @NotNull
    public IResourceHolder getResourceHolder() {
        ConfigurationManager configurationManager = getConfigurationManager();
        return configurationManager.getResourceHolder();
    }

    @NotNull
    public Logger getLogger() {
        IResourceHolder resourceHolder = getResourceHolder();
        return resourceHolder.getLogger();
    }

    @Nullable
    public Language getDefaultLanguage() {
        if(this.defaultLanguage == null) {
            if(this.defaultLanguageName != null) {
                this.defaultLanguage = this.languageMap.get(this.defaultLanguageName);
                if(this.defaultLanguage == null) {
                    Logger logger = getLogger();
                    logger.warning("Missing default language with name '" + this.defaultLanguageName + "'.");
                }
            }
        }
        
        return this.defaultLanguage;
    }

    @Nullable
    public Language getConsoleLanguage() {
        if(this.consoleLanguage == null) {
            if(this.consoleLanguageName != null) {
                this.consoleLanguage = this.languageMap.get(this.consoleLanguageName);
                if(this.consoleLanguage == null) {
                    Logger logger = getLogger();
                    logger.warning("Missing console language with name '" + this.consoleLanguageName + "'.");
                }
            }
        }

        return this.consoleLanguage;
    }

    public boolean isForceDefaultLanguage() {
        return this.forceDefaultLanguage;
    }

    public void saveDefaultLanguageFiles() {
        ConfigurationManager configurationManager = getConfigurationManager();
        configurationManager.saveDefault("language.yml");

        File dataFolder = configurationManager.getBaseFolder();
        File languageFolder = new File(dataFolder, "language");
        if(!languageFolder.exists()) {
            boolean makeFolder = languageFolder.mkdirs();
            if(!makeFolder) {
                throw new IllegalStateException("Failed to create language folder '" + languageFolder + "'.");
            }

            for(String languageName : LanguageManager.KNOWN_LANGUAGE_ARRAY) {
                String languageFileName = String.format(Locale.US, "language/%s.lang.yml", languageName);
                YamlConfiguration jarLanguageConfiguration = configurationManager.getInternal(languageFileName);
                if(jarLanguageConfiguration != null) {
                    configurationManager.saveDefault(languageFileName);
                }
            }
        }
    }

    public void reloadLanguageFiles() {
        this.languageMap.clear();
        IResourceHolder resourceHolder = getResourceHolder();
        Logger logger = getLogger();

        File dataFolder = resourceHolder.getDataFolder();
        File languageFolder = new File(dataFolder, "language");
        if(!languageFolder.exists() || !languageFolder.isDirectory()) {
            logger.warning("'language' folder does not exist or is not a directory.");
            return;
        }
        
        FilenameFilter filenameFilter = (folder, fileName) -> fileName.endsWith(".lang.yml");
        File[] fileArray = languageFolder.listFiles(filenameFilter);
        if(fileArray == null || fileArray.length == 0) {
            logger.warning("Failed to find any '.lang.yml' files in the language folder.");
            return;
        }

        List<YamlConfiguration> configurationList = new ArrayList<>();
        for(File languageFile : fileArray) {
            try {
                YamlConfiguration configuration = new YamlConfiguration();
                configuration.load(languageFile);

                String languageFileName = languageFile.getName();
                String languageName = languageFileName.replace(".lang.yml", "");
                configuration.set("language-name", languageName);

                configurationList.add(configuration);
            } catch(IOException | InvalidConfigurationException ex) {
                logger.log(Level.WARNING, "An error occurred while loading a language file:", ex);
            }
        }

        LanguageConfigurationComparator languageConfigurationComparator = new LanguageConfigurationComparator();
        configurationList.sort(languageConfigurationComparator);

        for(YamlConfiguration configuration : configurationList) {
            try {
                Language language = loadLanguage(configuration);
                if(language != null) {
                    String languageCode = language.getLanguageCode();
                    this.languageMap.put(languageCode, language);
                }
            } catch(InvalidConfigurationException ex) {
                logger.log(Level.WARNING, "An error occurred while loading a language configuration:", ex);
            }
        }

        ConfigurationManager configurationManager = getConfigurationManager();
        configurationManager.reload("language.yml");

        YamlConfiguration configuration = configurationManager.get("language.yml");
        this.forceDefaultLanguage = configuration.getBoolean("enforce-default-locale");
        this.defaultLanguageName = configuration.getString("default-locale");
        this.consoleLanguageName = configuration.getString("console-locale");

        int languageCount = this.languageMap.size();
        logger.info("Successfully loaded " + languageCount + " language(s).");
    }

    @Nullable
    private Language loadLanguage(YamlConfiguration configuration) throws InvalidConfigurationException {
        String languageName = configuration.getString("language-name");
        if(languageName == null) {
            return null;
        }

        Language parentLanguage = null;
        String parentLanguageName = configuration.getString("parent");
        if(parentLanguageName != null) {
            parentLanguage = this.languageMap.get(parentLanguageName);
            if(parentLanguage == null) {
                throw new InvalidConfigurationException("parent language not loaded correctly.");
            }
        }

        Language language = new Language(parentLanguage, languageName, configuration);
        Set<String> keySet = configuration.getKeys(true);
        for(String key : keySet) {
            if(configuration.isList(key)) {
                List<String> messageList = configuration.getStringList(key);
                if(!messageList.isEmpty()) {
                    String message = String.join("\n", messageList);
                    language.addTranslation(key, message);
                }
            }

            if(configuration.isString(key)) {
                String message = configuration.getString(key, key);
                if(message != null) {
                    language.addTranslation(key, message);
                }
            }
        }

        return language;
    }

    @Nullable
    public Language getLanguage(String localeName) {
        if(localeName == null || localeName.isEmpty() || localeName.equals("default")) {
            return getDefaultLanguage();
        }

        return this.languageMap.getOrDefault(localeName, getDefaultLanguage());
    }

    @Nullable
    public Language getLanguage(CommandSender audience) {
        if(audience == null || isForceDefaultLanguage()) {
            return getDefaultLanguage();
        }

        if(audience instanceof ConsoleCommandSender) {
            return getConsoleLanguage();
        }

        if(audience instanceof Player) {
            String cachedLocale = LanguageCache.getCachedLocale((Player) audience);
            return getLanguage(cachedLocale);
        }

        return getDefaultLanguage();
    }

    @NotNull
    public String getMessage(@Nullable CommandSender audience, @NotNull String key, @Nullable Replacer replacer,
                             boolean color) {
        Validate.notEmpty(key, "key must not be empty!");
        Language language = getLanguage(audience);
        if(language == null) {
            Logger logger = getLogger();
            logger.warning("There are no languages available.");
            return "";
        }

        String message = language.getTranslation(key);
        if(message.isEmpty()) {
            return "";
        }

        if(replacer != null) {
            message = replacer.replace(message);
        }

        if(color) {
            message = MessageUtility.color(message);
        }

        return message;
    }

    public void sendMessage(@NotNull CommandSender audience, @NotNull String key, @Nullable Replacer replacer,
                            boolean color) {
        String message = getMessage(audience, key, replacer, color);
        if(message.isEmpty()) {
            return;
        }

        audience.sendMessage(message);
    }

    public void broadcastMessage(@NotNull String key, @Nullable Replacer replacer, boolean color,
                                 @Nullable String permission) {
        Collection<? extends Player> onlinePlayerCollection = Bukkit.getOnlinePlayers();
        for (Player player : onlinePlayerCollection) {
            if(permission != null && !permission.isEmpty() && !player.hasPermission(permission)) {
                continue;
            }

            sendMessage(player, key, replacer, color);
        }
    }
}
