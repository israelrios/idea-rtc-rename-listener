package com.rios;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.vfs.LocalFileOperationsHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ThrowableConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class RtcLocalFileOperationHandler implements LocalFileOperationsHandler {

    private static final String JAZZ_SCMTOOLS_PATH =
        System.getProperty("user.home") + "/dev/jazz/scmtools/eclipse/";

    private Map<String, String> rootsCache = new LinkedHashMap<String, String>() {
        @Override protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > 100;
        }
    };

    private Set<String> roots = new HashSet<>();

    void stopDaemons() {
        for (String root : roots) {
            String[] params = new String[]{JAZZ_SCMTOOLS_PATH + "scm", "daemon", "stop", root};

            try {
                Runtime.getRuntime().exec(params, null, new File(root));
            } catch (IOException e) {
                getLogger().error("", e);
            }
        }

    }

    @Override public boolean delete(VirtualFile file) {
        return false;
    }

    @Override public boolean move(VirtualFile file, VirtualFile toDir) throws IOException {
        return renameOrMove(file, toDir, null);
    }

    @Nullable @Override public File copy(VirtualFile file, VirtualFile toDir, String copyName) {
        return null;
    }

    @Override public boolean rename(VirtualFile file, String newName) throws IOException {
        return renameOrMove(file, null, newName);
    }

    private boolean renameOrMove(VirtualFile file, VirtualFile toDir, String newName) throws IOException {
        String root = getRoot(file);
        if (root.equals("")) {
            return false;
        }
        final String path = file.getCanonicalPath();
        if (toDir == null) {
            toDir = file.getParent();
        }
        if (newName == null) {
            newName = file.getName();
        }
        final String newpath = toDir.getCanonicalPath() + File.separator + newName;
        try {
            return scmMove(root, path, newpath);
        } catch (InterruptedException e) {
            getLogger().error("", e);
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private boolean scmMove(String root, String path, String newpath)
        throws IOException, InterruptedException {

        String[] params = new String[]{
            JAZZ_SCMTOOLS_PATH + "lscm",
            "--non-interactive", "move", path, newpath};

        // Se o arquivo não existir um erro será lançado.
        Process process = Runtime.getRuntime().exec(params, null, new File(root));
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            StringBuilder out = getOutput(process);
            Notification notification = new Notification("RTC Rename", "Move/Rename Error",
                "lscm move retornou " + exitCode, NotificationType.ERROR);
            Notifications.Bus.notify(notification);
            getLogger().error("lscm move retornou {}.\n{}", exitCode, out.toString());
            return false;
        }

        return true;
    }

    private Logger getLogger() {
        return LoggerFactory.getLogger(getClass());
    }

    @NotNull private StringBuilder getOutput(Process process) throws IOException {
        StringBuilder out = new StringBuilder(100);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line);
                out.append("\n");
            }
        }
        return out;
    }

    private String getRoot(VirtualFile file) {
        VirtualFile dir = file;
        if (!dir.isDirectory()) {
            dir = dir.getParent();
        }
        final String dirPath = dir.getCanonicalPath();
        final String cached = rootsCache.get(dirPath);
        if (cached != null) {
            return cached;
        }

        final String result = checkShared(dirPath).intern();
        rootsCache.put(dirPath, result);
        roots.add(result);
        return result;
    }

    private String checkShared(String path) {
        File lastdir = new File(path);
        File dir = lastdir.getParentFile();
        while (dir != null) {
            final String cached = rootsCache.get(dir.getPath());
            if (cached != null) {
                return cached;
            }
            File jazz5 = new File(dir, ".jazz5");
            if (jazz5.exists()) {
                if (new File(jazz5, lastdir.getName()).exists()) {
                    return lastdir.getPath();
                } else {
                    break;
                }
            }
            lastdir = dir;
            dir = dir.getParentFile();
        }
        return "";
    }

    @Override public boolean createFile(VirtualFile file, String name) {
        return false;
    }

    @Override public boolean createDirectory(VirtualFile file, String name) {
        return false;
    }

    @Override public void afterDone(
        ThrowableConsumer<LocalFileOperationsHandler, IOException> throwableConsumer) {
        // nada a fazer
    }
}