# Правила R8. Библиотеки (kotlinx.serialization, Room, OkHttp) приносят свои правила сами.

# pdfbox-android: декодер JPEG 2000 — необязательная зависимость, картинки нам не нужны (только текст)
-dontwarn com.gemalto.jp2.**
