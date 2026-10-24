# Всё, что нужно, держит манифест (Activity, Receiver, Application).
# Убираем лишние логи в релизе.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}
