package fr.Alphart.BungeePlayerCounter;

import com.google.gson.Gson;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

public final class GsonLoader {
    public static boolean loadGson() {
        try {
            Class.forName("com.google.gson.Gson");
            return true;
        } catch (final Throwable t) {
            BPC.info("Gson wasn't found... Please update to spigot 1.8.3 or earlier."
                    + "BPC will try to dynamically load it.");
        }
        final File bpcFolder = BPC.getInstance().getDataFolder();
        final File gsonPath = new File(bpcFolder + File.separator + "lib" + File.separator
                + "gson.jar");
        new File(bpcFolder + File.separator + "lib").mkdir();

        // Download the driver if it doesn't exist
        if (!gsonPath.exists()) {
            BPC.info("Gson was not found. It is being downloaded, please wait ...");

            final String gsonDL = "https://repo1.maven.org/maven2/com/google/code/gson/gson/2.3.1/gson-2.3.1.jar";
            FileOutputStream fos = null;
            try {
                final ReadableByteChannel rbc = Channels.newChannel(new URL(gsonDL).openStream());
                fos = new FileOutputStream(gsonPath);
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            } catch (final IOException e) {
                BPC.severe("An error occured during the download of Gson.", e);
                return false;
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            BPC.info("Gson has been successfully downloaded.");
        }

        try {
            URLClassLoader systemClassLoader;
            URL gsonUrl;
            Class<URLClassLoader> sysclass;
            gsonUrl = gsonPath.toURI().toURL();
            systemClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
            sysclass = URLClassLoader.class;
            final Method method = sysclass.getDeclaredMethod("addURL", new Class[]{URL.class});
            method.setAccessible(true);
            method.invoke(systemClassLoader, new Object[]{gsonUrl});

            return true;
        } catch (final Throwable t) {
            BPC.severe("Gson cannot be loaded.", t);
        }
        return false;
    }
}
