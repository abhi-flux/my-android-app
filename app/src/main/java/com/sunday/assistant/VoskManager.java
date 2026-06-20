package com.sunday.assistant;

import android.content.Context;

import org.vosk.Model;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class VoskManager {

    private static final String MODEL_URL =
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip";

    public interface ModelCallback {
        void onProgress(String message);
        void onReady(Model model);
        void onError(String error);
    }

    private final Context context;
    private final OkHttpClient client = new OkHttpClient();

    public VoskManager(Context context) {
        this.context = context;
    }

    public File getModelDir() {
        return new File(context.getFilesDir(), "vosk-model-small-en-us-0.15");
    }

    public boolean isModelReady() {
        File modelDir = getModelDir();
        return modelDir.exists() && modelDir.isDirectory()
                && modelDir.list() != null && modelDir.list().length > 0;
    }

    public void setupModel(ModelCallback callback) {
        if (isModelReady()) {
            loadModel(callback);
            return;
        }
        downloadModel(callback);
    }

    private void downloadModel(ModelCallback callback) {
        callback.onProgress("Downloading voice model (~40MB)...");

        Request request = new Request.Builder().url(MODEL_URL).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError("Download failed: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful() || response.body() == null) {
                    callback.onError("Download failed: server error");
                    return;
                }

                File zipFile = new File(context.getFilesDir(), "vosk-model.zip");
                try (InputStream in = response.body().byteStream();
                     FileOutputStream out = new FileOutputStream(zipFile)) {
                    byte[] buffer = new byte[8192];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        out.write(buffer, 0, len);
                    }
                }

                callback.onProgress("Unpacking voice model...");
                try {
                    unzip(zipFile, context.getFilesDir());
                    zipFile.delete();
                } catch (IOException e) {
                    callback.onError("Unzip failed: " + e.getMessage());
                    return;
                }

                loadModel(callback);
            }
        });
    }

    private void unzip(File zipFile, File targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    outFile.mkdirs();
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists()) parent.mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(outFile)) {
                        int len;
                        while ((len = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void loadModel(ModelCallback callback) {
        new Thread(() -> {
            try {
                callback.onProgress("Loading voice model...");
                Model model = new Model(getModelDir().getAbsolutePath());
                callback.onReady(model);
            } catch (Exception e) {
                callback.onError("Model load failed: " + e.getMessage());
            }
        }).start();
    }
}
