package org.openmrs.client.applications;

import android.app.Application;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.PrintWriter;

public class Logger extends Application {

    private static Logger mInstance;
    private static String mTAG = "OpenMRS";
    private static final boolean IS_DEBUGGING_ON = true;
    private static final String OPENMRS_DIR = Environment.getExternalStorageDirectory() + "/OpenMRS";
    private static final int MAX_SIZE = 64 * 1024; // 64kB;
    private static Process mLoggerProcess;
    private static File mLogFile;
    private static File mFolder;
    private static boolean mSaveToFileEnable = true;
    private static int mErrorCountSaveToFile = 2;
    private static boolean mIsRotating;

    @Override
    public void onCreate() {
        super.onCreate();
        mInstance = this;

        mFolder = new File(OPENMRS_DIR);
        try {
            if (isFolderExist()) {
                mLogFile = new File(OPENMRS_DIR + "/OpenMRS.log");
                if (!mLogFile.createNewFile()) {
                    rotateLogFile();
                }
            }
            Logger.d("Start logging to file");
        } catch (IOException e) {
            Logger.e("Error during create file", e);
        }
    }

    public static Logger getInstance() {
        return mInstance;
    }

    private static boolean isFolderExist() {
        boolean success = true;
        if (!mFolder.exists()) {
            success = mFolder.mkdir();
        }
        return success;
    }

    private static boolean isSaveToFileEnable() {
        return mSaveToFileEnable && mErrorCountSaveToFile > 0;
    }

    private static void setErrorCount() {
        mErrorCountSaveToFile--;
        if (mErrorCountSaveToFile <= 0) {
            mSaveToFileEnable = false;
            Logger.e("logging to file disabled because of to much error during save");
        }
    }

    private static void saveToFile() {
        if (isFolderExist() && isSaveToFileEnable()) {
            String command = "logcat -d -v time -s " + mTAG;
            try {
                mLoggerProcess = Runtime.getRuntime().exec(command);

                BufferedReader in = new BufferedReader(new InputStreamReader(mLoggerProcess.getInputStream()));
                String line = null;

                FileWriter writer = new FileWriter(mLogFile, true);
                while ((line = in.readLine()) != null) {
                    if (!line.startsWith("---------")) {
                        writer.write(line + "\n");
                    }
                }
                writer.flush();
                writer.close();

                mLoggerProcess = Runtime.getRuntime().exec("logcat -c");
                mLoggerProcess.waitFor();

            } catch (IOException e) {
                setErrorCount();
                if (isSaveToFileEnable()) {
                    Logger.e("Error during save log to file", e);
                }
            } catch (InterruptedException e) {
                setErrorCount();
                if (isSaveToFileEnable()) {
                    Logger.e("Error during waitng for \"logcat -c\" process", e);
                }
            }
            rotateLogFile();
        }
    }

    public static void v(final String msg) {
        Log.v(mTAG, getMessage(msg));
        saveToFile();
    }

    public static void v(final String msg, Throwable tr) {
        Log.v(mTAG, getMessage(msg), tr);
        saveToFile();
    }

    public static void d(final String msg) {
        if (IS_DEBUGGING_ON) {
            Log.d(mTAG, getMessage(msg));
            saveToFile();
        }
    }

    public static void d(final String msg, Throwable tr) {
        if (IS_DEBUGGING_ON) {
            Log.d(mTAG, getMessage(msg), tr);
            saveToFile();
        }
    }

    public static void i(final String msg) {
        Log.i(mTAG, getMessage(msg));
        saveToFile();
    }

    public static void i(final String msg, Throwable tr) {
        Log.i(mTAG, getMessage(msg), tr);
        saveToFile();
    }

    public static void w(final String msg) {
        Log.w(mTAG, getMessage(msg));
        saveToFile();
    }

    public static void w(final String msg, Throwable tr) {
        Log.w(mTAG, getMessage(msg), tr);
        saveToFile();
    }

    public static void e(final String msg) {
        Log.e(mTAG, getMessage(msg));
        saveToFile();
    }

    public static void e(final String msg, Throwable tr) {
        Log.e(mTAG, getMessage(msg), tr);
        saveToFile();
    }

    private static String getMessage(String msg) {
        final String fullClassName = Thread.currentThread().getStackTrace()[4].getClassName();
        final String className = fullClassName.substring(fullClassName.lastIndexOf('.') + 1);
        final String methodName = Thread.currentThread().getStackTrace()[4].getMethodName();
        final int lineNumber = Thread.currentThread().getStackTrace()[4].getLineNumber();

        return "#" + lineNumber + " " + className + "." + methodName + "() : " + msg;
    }

    private static void rotateLogFile() {
        if (mLogFile.length() > MAX_SIZE && !mIsRotating) {
            mIsRotating = true;
            Logger.i("Log file size is too big. Start rotating log file");
            new Thread() {
                @Override
                public void run() {
                    try {
                        LineNumberReader r = new LineNumberReader(new FileReader(mLogFile));

                        while (r.readLine() != null) {
                            continue;
                        }
                        r.close();

                        int remove = Math.round(r.getLineNumber() * 0.3f);
                        if (remove > 0) {
                            r = new LineNumberReader(new FileReader(mLogFile));

                            while (r.readLine() != null && r.getLineNumber() < remove) {
                                continue;
                            }

                            File newFile = new File(mLogFile.getAbsolutePath() + ".new");
                            PrintWriter pw = new PrintWriter(new FileWriter(newFile));
                            String line;
                            while ((line = r.readLine()) != null) {
                                pw.println(line);
                            }

                            pw.close();
                            r.close();

                            if (newFile.renameTo(mLogFile)) {
                                Logger.i("Log file rotated");
                            }
                            mIsRotating = false;
                        }
                    } catch (IOException e) {
                        Logger.e("Error rotating log file. Rotating disable. ", e);
                    }
                }
            } .start();
        }
    }
}