package chat.rox.android.demo.util;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.FileNameMap;
import java.net.URLConnection;

public class FileHelper {
    private final HandlerThread handlerThread;
    private final Handler workerHandler;
    private final Handler mainHandler;
    private FileLoaderListener listener;
    private Context context;

    public FileHelper(FileLoaderListener listener, Context context) {
        this.listener = listener;
        this.context = context;

        handlerThread = new HandlerThread(this.getClass().getSimpleName());
        handlerThread.start();
        workerHandler = new Handler(handlerThread.getLooper());
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public void createTempFile(Uri uri) {
        workerHandler.post(() -> {
            createTempFileInternal(uri);
        });
    }

    public void loadFileDescriptor(Uri uri) {
        loadFileDescriptor(uri, "r");
    }

    public void loadFileDescriptor(Uri uri, String mode) {
        ContentResolver contentResolver = context.getContentResolver();
        try {
            ParcelFileDescriptor parcelFileDescriptor = contentResolver.openFileDescriptor(uri, mode);
            String mime = resolveMimeType(uri);
            String name = resolveFilename(uri);
            listener.onFileLoaded(parcelFileDescriptor, name, mime);
        } catch (Exception e) {
            listener.onError();
        }
    }

    public void release() {
        handlerThread.quit();
        listener = null;
        context = null;
    }

    public String resolveMimeType(Uri uri) {
        String mime = null;
        String contentScheme = "content";
        String fileScheme = "file";

        if (uri.getScheme().equals(contentScheme)) {
            mime = context.getContentResolver().getType(uri);
        } else if (uri.getScheme().equals(fileScheme)) {
            FileNameMap fileNameMap = URLConnection.getFileNameMap();
            mime = fileNameMap.getContentTypeFor(uri.getLastPathSegment());
        }
        return mime;
    }

    @SuppressLint("Range")
    public String resolveFilename(Uri uri) {
        Cursor cursor = context.getContentResolver().query(
            uri,
            null,
            null,
            null,
            null
        );
        if (cursor != null && cursor.moveToFirst()) {
            String name = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
            cursor.close();
            return name;
        }
        return null;
    }

    private void createTempFileInternal(Uri uri) {
        String mime = resolveMimeType(uri);
        String name = resolveFilename(uri);
        String extension = mime == null ? null : MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        File file = null;
        try {
            InputStream inp = context.getContentResolver().openInputStream(uri);
            if (inp != null) {
                file = File.createTempFile("rox", extension, context.getCacheDir());
                writeFully(file, inp);
            }
        } catch (IOException e) {
            Log.e("ROX", "failed to copy selected file", e);
            if (file != null) {
                boolean fileDeleted = file.delete();
                if (!fileDeleted) {
                    Log.w("ROX", "failed to deleted file " + file.getName());
                }
                file = null;
            }
        }

        FileLoaderListener listener = this.listener;
        if (listener != null) {
            if (file != null && name != null && mime != null) {
                File finalFile = file;
                mainHandler.post(() -> listener.onFileLoaded(finalFile, name, mime));
            } else {
                mainHandler.post(listener::onError);
            }
        }
    }

    private static void writeFully(@NonNull File to, @NonNull InputStream from) throws IOException {
        byte[] buffer = new byte[4096];
        OutputStream out = null;
        try {
            out = new FileOutputStream(to);
            for (int read; (read = from.read(buffer)) != -1; ) {
                out.write(buffer, 0, read);
            }
        } finally {
            from.close();
            if (out != null) {
                out.close();
            }
        }
    }

    public interface FileLoaderListener {
        void onFileLoaded(@NonNull File tempFile, @NonNull String filename, @NonNull String mimeType);

        void onFileLoaded(@NonNull ParcelFileDescriptor descriptor, @NonNull String filename, @NonNull String mimeType);

        void onError();
    }
}