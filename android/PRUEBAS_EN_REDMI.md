# Protocolo de prueba Redmi Note 13 Pro 5G / HyperOS

1. **Antes:** exporta respaldo JSON desde v0.8.0 (Mi AmberMusic → Ajustes). No desinstales la app.
2. **Compila:** `env JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:assembleDebug`.
3. **Instala:** `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. **Migra:** abre la biblioteca. Verifica categorías, favoritas, playlist y portadas; pulsa actualizar una vez.
5. **Filtros:** comprueba que los audios de duración <= 60 s siguen ocultos; busca por carpeta y género; alterna FLAC/MP3.
6. **Inmersión:** abre canciones de Cyberpunk, Persona y Wuthering Waves. El fondo debe reflejar colores de cada portada sin saltos. Prueba el interruptor de fondo dinámico.
7. **Widget:** añade widget Aigis desde inicio. Prueba reproducción, pausa, anterior, siguiente, bloqueo de pantalla y actualización de carátula.
8. **Smart:** crea reglas Artista = Lyn, Formato = FLAC, Popularidad = 3. Verifica su actualización tras escanear.
9. **Duplicados:** pulsa Analizar; verifica que la operación no modifica archivos. Observa uso de CPU durante cálculo.
10. **Letras:** importa .lrc, desplaza ±0.5 s, edita una línea y verifica persistencia tras reiniciar.
11. **ReplayGain:** reproduce FLAC con tags ReplayGain, alterna off/track/album; revisa nivel y desactiva si los cambios no son deseables.
12. **Batería/Media3:** realme Buds Air 7 por Bluetooth, escucha 20 min con pantalla apagada, controla desde HyperOS, desconecta audífonos y verifica pausa.
13. **Cierre:** desliza app desde recientes; verifica que se detiene la música. Volver a abrir no debe reproducir sin orden.
14. **Rendimiento:** toma medidas ADB con `adb shell dumpsys gfxinfo dev.liox.ambermusic framestats`; compara antes/después (evita inferir 120 fps solo por ver una animación).

**Errores:** Si Gradle falla, envía el bloque `> Task :app:compileDebugKotlin` y las líneas `e:`. No intentes instalar el APK si no aparece `BUILD SUCCESSFUL`.
