# AmberMusic v1.2.0 — Ambient Motion

Actualización visual a partir de v1.1.0 Blue AMOLED. Sin red, sin Android Auto, sin cambio de datos Room.

## Correcciones
- Fondo ambiental nuevo: 448×960 píxeles, PNG sin pérdidas, desenfoque separable de tres pasos (bordes extendidos, sin bloques cuadrados). Caché `ambient-v2`: no reutiliza los JPEG de 96×96 de `ambient-v1`.
- Paleta local extraída de la portada; transiciones ambientales de 1100 ms. Precaché de la portada ambiental de la siguiente canción para suavizar el cambio.
- Carátula: 730 ms de fundido y escalado, salida de 450-600 ms. La imagen nítida de portada no se desenfoca.
- Apertura/cierre de Now Playing: transición vertical + opacidad de ~520 ms.
- Solicitud de la mayor frecuencia de refresco compatible hasta 120 Hz en la ventana y `Modifier.preferredFrameRate(120f)` en Compose. El sistema puede denegar 120 Hz.
- Import correcto `androidx.compose.material3.pulltorefresh.PullToRefreshBox`.
- Sin cambios en Media3, AudioSessionId, favoritos, servicio ni migraciones Room.

## Instalar
En CachyOS, después de aplicar el parche:

```sh
cd ~/Downloads/AmberMusic-Completo
env JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

No ejecutes `adb install` si la compilación falla. No desinstales AmberMusic.

## Probar 120 Hz de manera verificable
En HyperOS: Ajustes > Pantalla > Frecuencia de actualización > 120 Hz (si está disponible). En Opciones de desarrollador, activar «Mostrar frecuencia de actualización» si existe.

```sh
adb shell settings get system peak_refresh_rate
adb shell dumpsys display | grep -i -E 'refreshRate|mActiveMode' | head -20
adb shell dumpsys gfxinfo dev.liox.ambermusic reset
# Abre Now Playing y cambia varias canciones.
adb shell dumpsys gfxinfo dev.liox.ambermusic framestats > ~/Downloads/AmberMusic_framestats.txt
```

120 Hz no significa 120 fps reales si hay carga GPU, ahorro energético, límites del SO o pantalla configurada a 60 Hz. Comparar el rendimiento visual **en el dispositivo** con la pantalla de refresco activada.

## Notas de caché
Las carátulas siguen dentro del teléfono. El fondo se renderiza una vez por portada, se guarda en `cacheDir/ambient-v2` y queda limitado a 64 portadas (dos archivos por portada). No requiere INTERNET. Los fondos se recrean cuando cambia una carátula o cambia su tamaño/fecha.

## Revertir
El instalador deja el comando de restauración dentro de `.amber-backups/`. El APK instalado no cambia hasta que recompiles y lo instales por USB.

## Validación
Se compila y ejecuta la prueba pura del kernel de desenfoque sin Android SDK; la compilación completa del APK requiere tu SDK Android y Gradle en CachyOS.
