package com.alfread.floatspace.shizuku;

import android.content.pm.PackageManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuRemoteProcess;

/**
 * Small compatibility bridge for shell commands through Shizuku 13.x.
 * Shizuku 13 marks newProcess as deprecated/private and recommends UserService;
 * this bridge keeps FloatSpace compact while documenting that future migration
 * may be needed when API 14 removes the method.
 */
public final class ShizukuBridge {
    private ShizukuBridge() {}

    public static boolean available() {
        try {
            return Shizuku.pingBinder()
                    && !Shizuku.isPreV11()
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void requestPermission() {
        if (!Shizuku.pingBinder()) return;
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            Shizuku.requestPermission(3401);
        }
    }

    public static CommandResult exec(String... command) {
        if (!available()) return new CommandResult(-2, "", "Shizuku unavailable");
        try {
            java.lang.reflect.Method m = Shizuku.class.getDeclaredMethod(
                    "newProcess", String[].class, String[].class, String.class);
            m.setAccessible(true);
            ShizukuRemoteProcess process = (ShizukuRemoteProcess) m.invoke(null, (Object) command, null, null);
            StringBuilder out = new StringBuilder();
            StringBuilder err = new StringBuilder();
            Thread outReader = new Thread(() -> read(process.getInputStream(), out), "fs-shizuku-out");
            Thread errReader = new Thread(() -> read(process.getErrorStream(), err), "fs-shizuku-err");
            outReader.start();
            errReader.start();
            int code = process.waitFor();
            outReader.join(1200);
            errReader.join(1200);
            process.destroy();
            return new CommandResult(code, out.toString().trim(), err.toString().trim());
        } catch (Throwable e) {
            return new CommandResult(-1, "", String.valueOf(e.getMessage()));
        }
    }

    private static void read(java.io.InputStream input, StringBuilder sink) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(input))) {
            String line;
            while ((line = br.readLine()) != null) sink.append(line).append('\n');
        } catch (Exception ignored) {
        }
    }

    public static final class CommandResult {
        public final int code;
        public final String out;
        public final String err;

        public CommandResult(int code, String out, String err) {
            this.code = code;
            this.out = out;
            this.err = err;
        }

        public boolean ok() {
            return code == 0;
        }
    }
}
