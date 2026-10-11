package com.my.televip;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.my.televip.logging.Logger;
import com.my.televip.utils.Utils;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

public class ClientChecker {
    public static boolean check(ClientType client, String pkgName)
    {
        return Arrays.asList(client.getPackageNames()).contains(pkgName);
    }

    public static boolean check(ClientType client)
    {
        return check(client, Utils.pkgName);
    }

    public static boolean isTgnetObfuscated()
    {
        ClientType client = ClientType.fromPackage(Utils.pkgName);
        return client != null && client.isTgnetObfuscated();
    }

    /**
     * The client build TeleVip was last checked against, logged next to the running one.
     *
     * <p>It no longer decides anything: every build is resolved from its own APK (see
     * RuntimeMappings). It used to pick a client's static table on exactly this build, which went
     * wrong once these were moved to newer builds than the tables were made from.</p>
     *
     * <p>The number in brackets is {@code PackageInfo.versionCode}. Telegram encodes its build
     * code and distribution channel in it as {@code code * 10 + channel}, where 1 and 2 are the
     * store bundles and 9 is the direct/web APK — so 70382 is build 7038 (12.10.1) from the
     * Play Store.</p>
     */
    private static final Map<ClientType, String> VERIFIED_BUILD = new EnumMap<>(ClientType.class);

    static {
        VERIFIED_BUILD.put(ClientType.Telegram, "12.10.1 (70382)");
        VERIFIED_BUILD.put(ClientType.TelegramBeta, "12.9.0 (69579)");
        VERIFIED_BUILD.put(ClientType.TelegramWeb, "12.10.6 (71129)");
        VERIFIED_BUILD.put(ClientType.TelegramPlus, "12.8.1.0 (22350)");
        VERIFIED_BUILD.put(ClientType.TGConnect, "11.13.1 (11130109)");
        VERIFIED_BUILD.put(ClientType.Nagram, "12.10.1 (1248)");
        VERIFIED_BUILD.put(ClientType.NagramX, "12.9.2-ee899ef (1258)");
        VERIFIED_BUILD.put(ClientType.NagramXF, "12.7.3 (1245)");
        VERIFIED_BUILD.put(ClientType.Nekogram, "12.10.6 (71120)");
        VERIFIED_BUILD.put(ClientType.Cherrygram, "12.10.6 (71120)");
        VERIFIED_BUILD.put(ClientType.Nicegram, "1.55.0 (2139)");
        VERIFIED_BUILD.put(ClientType.iMe, "12.8.1 (12080102)");
        VERIFIED_BUILD.put(ClientType.iMeWeb, "12.8.1 (12080109)");
        VERIFIED_BUILD.put(ClientType.XPlus, "12.0.1 (61669)");
        VERIFIED_BUILD.put(ClientType.forkgram, "12.10.4.0 (709008)");
        VERIFIED_BUILD.put(ClientType.forkgramBeta, "12.8.4.0 (691909)");
        VERIFIED_BUILD.put(ClientType.ForkgramClassic, "12.10.8.0 (709208)");
        VERIFIED_BUILD.put(ClientType.Mercurygram, "12.10.5.1 (7105018)");
        VERIFIED_BUILD.put(ClientType.Telegraph, "12.8.1.1 (69172)");
        VERIFIED_BUILD.put(ClientType.Telega, "2.4.3 (107)");
        VERIFIED_BUILD.put(ClientType.Momogram, "12.6.4");
        VERIFIED_BUILD.put(ClientType.Turrit, "1.8.9.9.5");
    }

    public static String verifiedBuild(ClientType client) {
        return client == null ? null : VERIFIED_BUILD.get(client);
    }

    /** Logs the running client build against the one TeleVip was last verified on. */
    public static void checkClientVersion(android.content.Context context) {
        try {
            if (context == null) return;
            ClientType client = ClientType.fromPackage(Utils.pkgName);
            if (client == null) return;

            PackageManager pm = context.getPackageManager();
            PackageInfo info = pm.getPackageInfo(context.getPackageName(), 0);
            String running = info.versionName + " (" + Utils.versionCode(info) + ")";
            String verified = VERIFIED_BUILD.get(client);

            if (verified == null) {
                Logger.l("client " + client.name() + " " + running + " (no verified build on record)");
                return;
            }
            if (verified.equals(running)) {
                Logger.l("client " + client.name() + " " + running + " matches the verified build");
                return;
            }
            // Not a failure: RuntimeMappings resolves this build from its own APK. See its line
            // in the log for how much of it was found.
            Logger.l("client " + client.name() + " is " + running + ", verified build is " + verified
                    + " - names are being resolved from the running APK");
        } catch (Throwable ignored) {
        }
    }

    public enum ClientType {
        Telegram("org.telegram.messenger"),
        TelegramWeb("org.telegram.messenger.web"),
        TelegramPlus("org.telegram.plus"),
        TGConnect("com.tgconnect.android"),
        Nagram("xyz.nextalone.nagram", com.my.televip.Clients.Nagram.class),
        Nicegram("app.nicegram", com.my.televip.Clients.Nicegram.class),
        TelegramBeta("org.telegram.messenger.beta"),
        NagramX(new String[]{"nu.gpu.nagram", "nu.gpu.nagramx"}, null),   // full and "base" builds
        XPlus("com.xplus.messenger"),
        iMe("com.iMe.android"),
        iMeWeb("com.iMe.android.web"),
        forkgram("org.forkgram.messenger"),
        forkgramBeta("org.forkclient.messenger.beta"),
        Telegraph("ir.ilmili.telegraph", com.my.televip.Clients.Telegraph.class),
        Telega("ru.dahl.messenger"),
        Momogram(new String[]{"nekox.messenger.broken", "momo.gram"}, com.my.televip.Clients.Momogram.class),
        Nekogram("tw.nekomimi.nekogram", com.my.televip.Clients.Nekogram.class, true),
        NekogramX("nekox.messenger", com.my.televip.Clients.NekogramX.class),
        Cherrygram("uz.unnarsx.cherrygram", com.my.televip.Clients.Cherrygram.class, true),
        ForkgramClassic("org.forkgram.classic"),
        Turrit("org.telegram.group", com.my.televip.Clients.Turrit.class),
        NagramXF("fork.risin42.nagramx"),
        Mercurygram("it.belloworld.mercurygram");

        private final String[] packageNames;
        private final Class<?> resolverClass;
        private final boolean tgnetObfuscated;

        /** A client without a table of its own: everything is resolved from its APK. */
        ClientType(String packageName) {
            this(packageName, null);
        }

        ClientType(String packageName, Class<?> resolverClass) {
            this.packageNames = new String[]{packageName};
            this.resolverClass = resolverClass;
            tgnetObfuscated = false;
        }

        ClientType(String packageName, Class<?> resolverClass, boolean tgnetObfuscated) {
            this.packageNames = new String[]{packageName};
            this.resolverClass = resolverClass;
            this.tgnetObfuscated = tgnetObfuscated;
        }

        ClientType(String[] packageNames, Class<?> resolverClass) {
            this.packageNames = packageNames;
            this.resolverClass = resolverClass;
            tgnetObfuscated = false;
        }

        public String[] getPackageNames() { return packageNames; }
        public Class<?> getResolverClass() { return resolverClass; }
        public boolean isTgnetObfuscated() { return tgnetObfuscated; }

        /** Whether a hand-made name table exists for this client's verified build. */
        public boolean hasStaticTable() {
            return this == Nekogram || this == Cherrygram || this == Telegraph;
        }

        public static ClientType fromPackage(String pkg){
            for (ClientType type: ClientType.values()){
                for (String name: type.getPackageNames()){
                    if (name.equals(pkg)) return type;
                }
            }
            return null;
        }

        public static boolean containsPackage(String pkg){
            return fromPackage(pkg) != null;
        }
    }
}
