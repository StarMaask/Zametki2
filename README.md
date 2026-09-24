# Notes Android App

Android-приложение для заметок, созданное с использованием Jetpack Compose и Room.

## Как собрать APK через GitHub Actions

В проекте настроен автоматический CI workflow: `.github/workflows/build-apk.yml`.

### Запуск сборки:
1. Запушьте код в репозиторий GitHub в ветку `main` (или перейдите во вкладку **Actions** в GitHub и запустите workflow вручную через **Run workflow**).
2. Дождитесь завершения задачи **Build APK** (обычно 2–4 минуты).
3. Перейдите в завершенный запуск workflow — в секции **Artifacts** появится архив **Notes-Debug-APK** с готовым установочным файлом `app-debug.apk`.
4. Скачайте его на телефон и установите.

## Локальная сборка через Android Studio / терминал

```bash
./gradlew assembleDebug
```
Готовый APK будет находиться по пути:
`app/build/outputs/apk/debug/app-debug.apk`
