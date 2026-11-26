# wee — reproductor local de música

Aplicación Android gratuita, sin anuncios, basada en Kotlin, Jetpack Compose y Media3.

Compilar: `./gradlew :app:assembleDebug` o `./gradlew :app:assembleRelease`.

La versión F-Droid se debe compilar desde el repositorio público con licencia y metadatos verificados.


## Android 2.5 beta3 — portada, barra y gestos
- Portada cuadrada real y escalado Crop (o Fit cuando el usuario lo elige), sin caja anidada; interacción de deslizar izquierda/derecha para pistas y abajo para volver a Biblioteca.
- Android controla el gesto nativo de volver desde los bordes; no se intercepta a nivel de pantalla.
- Reproductor adaptado a la altura disponible; controles secundarios empujados hacia la zona inferior sin esconder información en pantallas compactas.
- Barra ondulada de 83 % de ancho, amplitud 2.8 dp, animación de progreso de 170 ms y superficie táctil de 31 dp.
- Letras LRC: desplazamiento a la línea activa (una vez por cambio de línea) y resaltado animado de 220 ms; respeto a Reducir movimiento.
- Se añaden pruebas unitarias para interpretación de gestos y búsqueda en la pista.

La onda es decorativa, no una representación PCM. Las imágenes y los gestos específicos de HyperOS siguen requiriendo verificación en el dispositivo.
