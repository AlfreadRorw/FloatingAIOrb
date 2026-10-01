package com.alfread.statusvault;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.os.Environment;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class StatusRepository {
    public static final String SOURCE = "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/.Statuses/";
    public static final String DESTINATION = "/storage/emulated/0/Download/";

    private static final String[] VIDEO_EXTENSIONS = {".mp4", ".3gp", ".mkv", ".webm", ".mov", ".avi", ".m4v"};

    public static List<StatusItem> scan() {
        File folder = new File(SOURCE);
        List<StatusItem> result = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null) return result;

        Arrays.sort(files, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });

        for (File file : files) {
            if (!file.isFile() || !file.canRead()) continue;
            String name = file.getName().toLowerCase(Locale.US);
            for (String ext : VIDEO_EXTENSIONS) {
                if (name.endsWith(ext)) {
                    result.add(new StatusItem(file));
                    break;
                }
            }
        }
        return result;
    }

    public static File destinationFile(String requestedName) {
        File dir = new File(DESTINATION);
        if (!dir.exists()) dir.mkdirs();
        String clean = requestedName.replaceAll("[\\\\/:*?\"<>|]", "_");
        File target = new File(dir, clean);
        if (!target.exists()) return target;
        String base = clean;
        String ext = "";
        int dot = clean.lastIndexOf('.');
        if (dot > 0) {
            base = clean.substring(0, dot);
            ext = clean.substring(dot);
        }
        int counter = 1;
        while (target.exists()) {
            target = new File(dir, base + " (" + counter + ")" + ext);
            counter++;
        }
        return target;
    }

    public static void transfer(Context context, File source, boolean move, Progress progress) throws IOException {
        if (source == null || !source.exists()) throw new IOException("Source file not found");
        File target = destinationFile(source.getName());
        long total = source.length();
        long copied = 0;
        byte[] buffer = new byte[1024 * 1024];
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(source));
             BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(target))) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                copied += read;
                if (progress != null && total > 0) progress.onProgress((int) Math.min(100, (copied * 100) / total));
            }
            out.flush();
        } catch (IOException e) {
            // Never delete the source when a partial target may exist.
            if (target.exists()) target.delete();
            throw e;
        }

        if (move) {
            if (!source.delete()) {
                // The downloaded copy is valid; report successful copy semantics rather than losing the file.
                throw new IOException("Copied successfully, but source could not be removed");
            }
        }

        MediaScannerConnection.scanFile(context, new String[]{target.getAbsolutePath()}, new String[]{"video/*"}, null);
    }

    public interface Progress {
        void onProgress(int percent);
    }

    public static String prettySize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double value = bytes / 1024.0;
        if (value < 1024) return String.format(Locale.US, "%.1f KB", value);
        value /= 1024.0;
        if (value < 1024) return String.format(Locale.US, "%.1f MB", value);
        return String.format(Locale.US, "%.1f GB", value / 1024.0);
    }
}
