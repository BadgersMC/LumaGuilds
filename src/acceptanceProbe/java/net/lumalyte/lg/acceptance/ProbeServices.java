package net.lumalyte.lg.acceptance;

import org.koin.java.KoinJavaComponent;

/** Keep Kotlin KClass and Function signatures inside the provider's classloader. */
public final class ProbeServices {
    private ProbeServices() { }

    public static void requireOwner(ClassLoader owner) {
        if (KoinJavaComponent.class.getClassLoader() != owner) {
            throw new IllegalStateException("Koin must come from the pinned Guilds plugin");
        }
    }

    public static <T> T resolve(Class<T> type) {
        return type.cast(KoinJavaComponent.get(type));
    }
}
