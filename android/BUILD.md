# Probar AmberMusic en Android

Esta carpeta contiene la versión Android actual (`1.4.5`, `versionCode 143`).
El APK no se pudo generar en el entorno de desarrollo porque no están
instalados Android SDK, Gradle ni `adb`.

## Compilación local

Requiere Java 17, Android SDK con API 37 y Gradle 9.6:

```bash
cd android
gradle :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

El workflow manual `.github/workflows/build-android-current.yml` compila el
APK en GitHub Actions y lo publica como artefacto `AmberMusic-v1.4.5-debug`.

## Estado real

La aplicación Android existente conserva Media3, Room, MediaSession, widget,
escáner, caché de portadas, blur, playlists, favoritos, backups, duplicados,
ReplayGain, letras LRC y temas. En esta sesión se reforzó la restauración de
la sesión para preferir el ID estable de la canción y reportar errores.

La actualización completa descrita en la especificación todavía no está
implementada: faltan pruebas en Redmi/HyperOS y varias funciones avanzadas.
