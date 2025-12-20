package com.winlator.xenvironment;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.winlator.core.FileUtils;
import com.winlator.xenvironment.components.ALSAServerComponent;
import com.winlator.xenvironment.components.BionicProgramLauncherComponent;
import com.winlator.xenvironment.components.GlibcProgramLauncherComponent;
import com.winlator.xenvironment.components.GuestProgramLauncherComponent;
import com.winlator.xenvironment.components.PulseAudioComponent;

import java.io.File;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Iterator;

public class XEnvironment implements Iterable<EnvironmentComponent> {
    private final Context context;
    private final ImageFs imageFs;
    private final ArrayList<EnvironmentComponent> components = new ArrayList<>();

    private boolean winetricksRunning = false;

    private final AudioManager audioManager;
    private boolean audioCallbackRegistered = false;
    private final AudioDeviceCallback audioDeviceCallback = new AudioDeviceCallback() {
        @Override
        public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
            // Handle newly added audio devices (e.g., headphones connected)
            for (AudioDeviceInfo device : addedDevices) {
                if (device.isSink()) {
                    restartAudioComponent();
                }
            }
        }

        @Override
        public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
            // Handle removed audio devices (e.g., headphones disconnected)
            for (AudioDeviceInfo device : removedDevices) {
                if (device.isSink()) {
                    restartAudioComponent();
                }
            }
        }
    };

    public synchronized boolean isWinetricksRunning() {
        return winetricksRunning;
    }

    public synchronized void setWinetricksRunning(boolean running) {
        this.winetricksRunning = running;
    }

    public XEnvironment(Context context, ImageFs imageFs) {
        this.context = context;
        this.imageFs = imageFs;
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.audioManager.registerAudioDeviceCallback(audioDeviceCallback, null);
        this.audioCallbackRegistered = true;
    }

    public Context getContext() {
        return context;
    }

    public ImageFs getImageFs() {
        return imageFs;
    }

    public void addComponent(EnvironmentComponent environmentComponent) {
        environmentComponent.environment = this;
        components.add(environmentComponent);
    }

    public <T extends EnvironmentComponent> T getComponent(Class<T> componentClass) {
        for (EnvironmentComponent component : components) {
            if (component.getClass() == componentClass) return (T)component;
        }
        return null;
    }

    @Override
    public Iterator<EnvironmentComponent> iterator() {
        return components.iterator();
    }

    public static File getTmpDir(Context context) {
        File tmpDir = new File(context.getFilesDir(), "tmp");
        if (!tmpDir.isDirectory()) {
            tmpDir.mkdirs();
            FileUtils.chmod(tmpDir, 0771);
        }
        return tmpDir;
    }

    public void startEnvironmentComponents() {
        // FIX: Recover from potential previous root crashes BEFORE starting any components.
        // This ensures SysV and PulseAudio don't crash on permission denied errors for sockets.
        if (FileUtils.isRootAvailable()) {
            recoverStaleRootSession();
        }

        FileUtils.clear(getTmpDir(getContext()));
        for (EnvironmentComponent environmentComponent : this) environmentComponent.start();
    }

    private void recoverStaleRootSession() {
        try {
            Log.d("XEnvironment", "Attempting to recover stale root session...");
            java.lang.Process process = Runtime.getRuntime().exec("su");

            int appUid = context.getApplicationInfo().uid;
            File rootDir = imageFs.getRootDir();
            File mntDir = new File(rootDir, "mnt");
            File homeDir = new File(rootDir, "home");
            File tmpDir = new File(rootDir, "tmp");
            File usrTmpDir = new File(rootDir, "usr/tmp");

            StringBuilder script = new StringBuilder();

            // 1. Unmount any stale mounts in mnt/
            if (mntDir.exists()) {
                File[] mounts = mntDir.listFiles();
                if (mounts != null) {
                    for (File mount : mounts) {
                        script.append("/system/bin/umount -l '").append(mount.getAbsolutePath().replace("'", "'\\''")).append("' >/dev/null 2>&1\n");
                    }
                }
            }

            // 2. Fix permissions recursively on key directories
            if (homeDir.exists()) {
                script.append("/system/bin/chown -R ").append(appUid).append(":").append(appUid)
                        .append(" '").append(homeDir.getAbsolutePath().replace("'", "'\\''")).append("'\n");
            }
            if (tmpDir.exists()) {
                script.append("/system/bin/chown -R ").append(appUid).append(":").append(appUid)
                        .append(" '").append(tmpDir.getAbsolutePath().replace("'", "'\\''")).append("'\n");
            }
            if (usrTmpDir.exists()) {
                script.append("/system/bin/chown -R ").append(appUid).append(":").append(appUid)
                        .append(" '").append(usrTmpDir.getAbsolutePath().replace("'", "'\\''")).append("'\n");
            }

            // Clean up files dir PulseAudio if it exists
            File pulseDir = new File(context.getFilesDir(), "pulseaudio");
            if (pulseDir.exists()) {
                script.append("/system/bin/chown -R ").append(appUid).append(":").append(appUid)
                        .append(" '").append(pulseDir.getAbsolutePath().replace("'", "'\\''")).append("'\n");
            }

            script.append("exit\n");

            try (OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream())) {
                writer.write(script.toString());
                writer.flush();
            }

            int exitCode = process.waitFor();
            Log.d("XEnvironment", "Recovery script finished with exit code: " + exitCode);

        } catch (Exception e) {
            Log.w("XEnvironment", "Skipping root recovery (su not available or failed): " + e.getMessage());
        }
    }

    public void stopEnvironmentComponents() {
        for (EnvironmentComponent environmentComponent : this) environmentComponent.stop();
    }

    public void onPause() {
        if (audioCallbackRegistered) {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback);
            audioCallbackRegistered = false;
        }

        GuestProgramLauncherComponent guestProgramLauncherComponent = getComponent(GuestProgramLauncherComponent.class);
        if (guestProgramLauncherComponent != null) guestProgramLauncherComponent.suspendProcess();
        GlibcProgramLauncherComponent glibcProgramLauncherComponent = getComponent(GlibcProgramLauncherComponent.class);
        if (glibcProgramLauncherComponent != null) glibcProgramLauncherComponent.suspendProcess();
        BionicProgramLauncherComponent bionicProgramLauncherComponent = getComponent(BionicProgramLauncherComponent.class);
        if (bionicProgramLauncherComponent != null) bionicProgramLauncherComponent.suspendProcess();
    }

    public void onResume() {
        if (!audioCallbackRegistered) {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null);
            audioCallbackRegistered = true;
        }

        GuestProgramLauncherComponent guestProgramLauncherComponent = getComponent(GuestProgramLauncherComponent.class);
        if (guestProgramLauncherComponent != null) guestProgramLauncherComponent.resumeProcess();
        GlibcProgramLauncherComponent glibcProgramLauncherComponent = getComponent(GlibcProgramLauncherComponent.class);
        if (glibcProgramLauncherComponent != null) glibcProgramLauncherComponent.resumeProcess();
        BionicProgramLauncherComponent bionicProgramLauncherComponent = getComponent(BionicProgramLauncherComponent.class);
        if (bionicProgramLauncherComponent != null) bionicProgramLauncherComponent.resumeProcess();
    }

    private void restartAudioComponent() {
        final ALSAServerComponent alsaServerComponent = getComponent(ALSAServerComponent.class);
        if (alsaServerComponent != null) {
            alsaServerComponent.stop();
            alsaServerComponent.start();
        }

        final PulseAudioComponent pulseAudioComponent = getComponent(PulseAudioComponent.class);
        if (pulseAudioComponent != null) {
            //pulseAudioComponent.stop(); stop is already called inside start function
            pulseAudioComponent.start();
        }
    }
}
