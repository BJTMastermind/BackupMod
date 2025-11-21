package me.bjtmastermind.backupmod;

import java.io.File;
import java.io.FileWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import me.bjtmastermind.backupmod.backup.BackupScheduler;
import me.bjtmastermind.backupmod.command.BackupCommand;
import me.bjtmastermind.backupmod.config.BackupConfig;
import me.bjtmastermind.backupmod.lang.LangManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

public class BackupMod implements ModInitializer {
    public static final String MOD_ID = "backupmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final String BACKUP_DIR = "backups";
    public static final String TEMP_DIR = BACKUP_DIR + File.separator + ".temp";
    public static final String LOG_DIR = BACKUP_DIR + File.separator + "logs";

    @Override
    public void onInitialize() {
        LOGGER.info("{} initializing...", MOD_ID);

        createBackupFolders();
        createDoNotDeleteNotice();

        try {
            BackupConfig.load();
            LangManager.load();
            BackupCommand.register();
        } catch (Exception e) {
            LOGGER.error("Error during BackupMod initialization!", e);
        }

        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
    }

    private void onServerTick(MinecraftServer server) {
        BackupScheduler.checkAndRunBackup(server);
    }

    private void createBackupFolders() {
        File backupDir = new File(BACKUP_DIR);
        File tempDir = new File(TEMP_DIR);
        File logDir = new File(LOG_DIR);
        if (!backupDir.exists()) backupDir.mkdirs();
        if (!tempDir.exists()) tempDir.mkdirs();
        if (!logDir.exists()) logDir.mkdirs();
    }

    private void createDoNotDeleteNotice() {
        File tempDir = new File(TEMP_DIR);
        File noticeFile = new File(tempDir, "donotdeletethisfolder.txt");
        if (!noticeFile.exists()) {
            try (FileWriter writer = new FileWriter(noticeFile)) {
                writer.write("""
                WARNING: Do NOT delete this folder!
                --------------------------------------------------
                This folder is used by BackupMod to store temporary and system data for automatic backups.
                If you delete this folder, it can cause data loss, corrupted backups, or errors in the backup system.
                Possible issues after deletion:
                  - AutoBackup timer will not work correctly.
                  - Restoration of older backups might fail.
                  - Backup logs or scheduler status can be lost.
                  - Incomplete or broken backups.
                Please always keep this folder!
                --------------------------------------------------
                """);
            } catch (Exception e) {
                LOGGER.warn("Could not create donotdeletethisfolder.txt in .temp!", e);
            }
        }
    }
}