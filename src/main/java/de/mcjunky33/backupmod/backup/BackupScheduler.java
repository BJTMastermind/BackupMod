package de.mcjunky33.backupmod.backup;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import de.mcjunky33.backupmod.config.BackupConfig;
import net.minecraft.server.MinecraftServer;

public class BackupScheduler {
    private static final String HISTORY_FILE = "backups/.temp/schedule-history.json";
    private static final String SCHEDULE_FILE = "backups/.temp/schedulesystem.yml";
    private static List<LocalDateTime> todayBackups = new ArrayList<>();
    private static LocalDate lastCheckedDay = null;

    private static boolean isDebugTimerActive = false;
    private static int debugSeconds = 0;
    private static LocalDateTime lastBackupTime = null;
    private static double lastIntervalSeconds = 36.0 * 60;

    // Timer Status: being saved regularly!
    private static long secondsRemaining = -1; // -1 = not set
    private static int tickCounter = 0; // Ticks since the last timer update

    private static void saveScheduleState() {
        File file = new File(SCHEDULE_FILE);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("lastBackupTime: " + (lastBackupTime == null ? "" : lastBackupTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)) + "\n");
            writer.write("isDebugTimerActive: " + isDebugTimerActive + "\n");
            writer.write("debugSeconds: " + debugSeconds + "\n");
            writer.write("lastIntervalSeconds: " + lastIntervalSeconds + "\n");
            writer.write("secondsRemaining: " + secondsRemaining + "\n");
        } catch (Exception e) {
            // No further notification
        }
    }

    private static void loadScheduleState() {
        File file = new File(SCHEDULE_FILE);
        if (!file.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            LocalDateTime lbTime = null;
            boolean debugActive = false;
            int dbgSeconds = 0;
            double liSeconds = 36.0 * 60;
            long secRem = -1;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("lastBackupTime: ")) {
                    String tLine = line.substring("lastBackupTime: ".length()).trim();
                    lbTime = (tLine.isEmpty()) ? null : LocalDateTime.parse(tLine, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
                if (line.startsWith("isDebugTimerActive: ")) {
                    debugActive = "true".equals(line.substring("isDebugTimerActive: ".length()).trim());
                }
                if (line.startsWith("debugSeconds: ")) {
                    try { dbgSeconds = Integer.parseInt(line.substring("debugSeconds: ".length()).trim()); } catch (Exception ignored) {}
                }
                if (line.startsWith("lastIntervalSeconds: ")) {
                    try { liSeconds = Double.parseDouble(line.substring("lastIntervalSeconds: ".length()).trim()); } catch (Exception ignored) {}
                }
                if (line.startsWith("secondsRemaining: ")) {
                    try { secRem = Long.parseLong(line.substring("secondsRemaining: ".length()).trim()); } catch (Exception ignored) {}
                }
            }
            lastBackupTime = lbTime;
            isDebugTimerActive = debugActive;
            debugSeconds = dbgSeconds;
            lastIntervalSeconds = liSeconds;
            secondsRemaining = secRem;
        } catch (Exception e) {
            // No further notification
        }
    }

    public static void setAutoBackupTimes(int backupsPerDay) {
        if (backupsPerDay < 1) {
            BackupConfig.autoBackupTimes = 0;
            BackupConfig.autoBackupEnabled = 0;
            BackupConfig.saveConfig();
            resetSchedule();
            return;
        }
        BackupConfig.autoBackupTimes = backupsPerDay;
        BackupConfig.autoBackupEnabled = 1;
        BackupConfig.saveConfig();
        todayBackups.clear();
        lastIntervalSeconds = 1440.0 * 60 / backupsPerDay;
        lastBackupTime = LocalDateTime.now();
        isDebugTimerActive = false;
        debugSeconds = 0;
        secondsRemaining = (long) lastIntervalSeconds;
        tickCounter = 0;
        saveScheduleState();
    }

    public static void setDebugTimer(int hours, int minutes, int seconds, MinecraftServer server) {
        int totalSeconds = hours * 3600 + minutes * 60 + seconds;
        isDebugTimerActive = true;
        debugSeconds = totalSeconds;
        lastBackupTime = LocalDateTime.now();
        secondsRemaining = -1;
        tickCounter = 0;
        saveScheduleState();
        todayBackups.clear();
    }

    public static String getNextScheduledBackupTimerString() {
        loadScheduleState();
        if (isDebugTimerActive) {
            long secondsRem = debugSeconds;
            if (secondsRem <= 0) {
                return "Debug timer finished! Backup will be executed automatically.";
            } else {
                long hours = secondsRem / 3600;
                long minutes = (secondsRem % 3600) / 60;
                long seconds = secondsRem % 60;
                return String.format("%02dh %02dmin %02dsec", hours, minutes, seconds);
            }
        } else if (secondsRemaining >= 0) {
            long secondsRem = secondsRemaining;
            long hours = secondsRem / 3600;
            long minutes = (secondsRem % 3600) / 60;
            long seconds = secondsRem % 60;
            return String.format("%02dh %02dmin %02dsec", hours, minutes, seconds);
        } else {
            // If -1, then reset the interval (only on the very first start)
            int timesPerDay = Math.max(BackupConfig.autoBackupTimes, 1);
            double intervalSeconds = 1440.0 * 60 / timesPerDay;
            long secondsRem = (long) intervalSeconds;
            long hours = secondsRem / 3600;
            long minutes = (secondsRem % 3600) / 60;
            long seconds = secondsRem % 60;
            return String.format("%02dh %02dmin %02dsec", hours, minutes, seconds);
        }
    }

    // The timer is counted down and saved every real second (=20 ticks)!
    public static void checkAndRunBackup(MinecraftServer server) {
        loadScheduleState();
        int timesPerDay = Math.max(BackupConfig.autoBackupTimes, 1);
        lastIntervalSeconds = 1440.0 * 60 / timesPerDay;

        // Only on the very first start does secondsRemaining set the interval!
        if (secondsRemaining < 0) {
            secondsRemaining = (long) lastIntervalSeconds;
            saveScheduleState();
        }

        boolean doBackup = false;
        tickCounter++;

        // The timer only counts down every real second (20 ticks = 1 second).
        if (tickCounter >= 20) {
            tickCounter = 0;
            if (secondsRemaining > 0) {
                secondsRemaining -= 1;
                saveScheduleState();
            } else if (secondsRemaining == 0) {
                doBackup = true;
            }
        }

        if (doBackup) {
            boolean success = runScheduledBackup(server);
            if (success) {
                lastBackupTime = LocalDateTime.now();
                todayBackups.add(lastBackupTime);
                secondsRemaining = (long) lastIntervalSeconds;
                tickCounter = 0;
                saveScheduleState();
            }
        }
    }

    public static boolean runScheduledBackup(MinecraftServer server) {
        String cmd = "backup create autobackup";
        try {
            server.getCommands().getDispatcher().execute(
                    cmd,
                    server.createCommandSourceStack().withPermission(4)
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static void resetSchedule() {
        todayBackups.clear();
        lastCheckedDay = LocalDate.now();
        File file = new File(HISTORY_FILE);
        File file2 = new File(SCHEDULE_FILE);
        if (file.exists()) file.delete();
        if (file2.exists()) file2.delete();
        lastBackupTime = null;
        isDebugTimerActive = false;
        debugSeconds = 0;
        lastIntervalSeconds = 36.0 * 60;
        secondsRemaining = -1;
        tickCounter = 0;
    }

    public static boolean isDebugTimerActive() {
        return isDebugTimerActive;
    }
}