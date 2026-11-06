# Implementación de la actualización grande de AmberMusic

## Instrucción para el agente de código

Implementa la actualización grande de AmberMusic manteniendo dos productos relacionados y técnicamente separados:

1. **Android**: Kotlin, Jetpack Compose, Media3, Room, MediaStore y MediaSession.
2. **Linux de escritorio**: Rust, GTK4/libadwaita, GStreamer, PipeWire, D-Bus/MPRIS y SQLite.

No reemplaces Android por Linux ni mezcles APIs entre ambos objetivos. Antes de modificar código, inspecciona el repositorio, incluyendo los ZIP de código fuente, y clasifica cada función como implementada, parcial, sin pruebas o ausente. Conserva los datos existentes, el `applicationId` `dev.liox.ambermusic`, las migraciones Room, el widget y la integración MediaSession.

Android Auto y nuevas letras sincronizadas quedan fuera del alcance. La música continúa siendo local. Chromecast solo se considera un módulo opcional posterior.

## Android

### Base existente que debe conservarse

- Kotlin, Compose Material 3, Coroutines, StateFlow y Koin.
- Media3 ExoPlayer mediante `MusicPlaybackService`.
- MediaSession, notificación, pantalla bloqueada, Bluetooth y widget.
- Room con canciones, artistas, categorías, playlists, favoritos, overrides e historial.
- Escaneo incremental por MediaStore, permisos de audio y exclusiones de carpetas.
- Caché local de portadas y fondos inmersivos.
- ReplayGain, audio offload, ecualizador del sistema, temporizador, duplicados, backups, playlists inteligentes y letras LRC.
- Temas actuales, gesto de volver, reproductor expandible y solicitud de 120 Hz.

### Etapa A: reproducción y restauración

- Restaurar canción, posición, cola, shuffle y repeat usando identificadores estables, no solo índices.
- Corregir restauraciones después de eliminar o esconder canciones.
- Persistir el orden real de shuffle y el estado pausado.
- Guardar al pausar, buscar, cambiar de pista, salir y pasar a segundo plano.
- No iniciar reproducción automáticamente al restaurar.
- Manejar URI ausentes o dañadas y continuar con la siguiente pista.
- Mantener explícita la diferencia entre cerrar la interfaz, detener el servicio y detener la reproducción.
- Añadir pruebas unitarias, instrumentadas y de migración Room.

### Etapa B: MediaSession y HyperOS

- Publicar título, artista, álbum, duración, portada, posición y estado correctos.
- Actualizar la portada sin reiniciar el audio.
- Mantener funcionales play, pause, previous, next, seek, shuffle y repeat.
- Resolver audio focus, llamadas, otras aplicaciones y `becoming noisy`.
- Comprobar auriculares Bluetooth, widget, pantalla bloqueada y notificación.
- No prometer una copia exacta de la tarjeta visual de HyperOS: el sistema decide su plantilla.
- Reproducir dentro de AmberMusic la tarjeta de portada de fondo, degradado, pausa grande, progreso y controles inferiores.

### Etapa C: interfaz y 120 Hz

- Hacer continua la transición minirreproductor/reproductor completo.
- Implementar cierre arrastrando hacia abajo con seguimiento del dedo.
- Permitir animaciones interrumpibles y reversibles.
- Mantener la portada anterior hasta preparar la siguiente.
- Sincronizar portada, fondo, colores, título y artista.
- Mantener estable la barra al arrastrar.
- Evitar recomponer toda la pantalla por cada actualización de progreso.
- Reservar espacio para que el minirreproductor no tape la última fila.
- Mantener scroll, navegación atrás y paneles coherentes.
- Respetar movimiento reducido, texto grande, lectores de pantalla, tablets, plegables y HiDPI.
- Usar claves estables, cargas cancelables y blur precalculado.
- Medir 60, 90 y 120 Hz; `preferredRefreshRate` no garantiza la frecuencia.

### Etapa D: biblioteca, cola y organización

- Mantener el escaneo incremental y no purgar la biblioteca tras errores de permisos o almacenamiento.
- Mostrar la biblioteca guardada antes de terminar el escaneo.
- Limitar y limpiar cachés de portadas, fondos y portadas personalizadas.
- Cancelar búsquedas, metadata, thumbnails y hashes obsoletos.
- Añadir colas independientes persistentes.
- Guardar la cola como playlist.
- Implementar reproducir ahora, después y al final.
- Añadir deshacer para retirar y reordenar.
- Persistir shuffle sin reiniciar su ciclo al agregar canciones.
- Añadir selección múltiple y agregar a varias playlists.
- Importar/exportar M3U/M3U8 con vista previa y rutas ausentes.
- Hacer búsqueda tolerante a tildes y mayúsculas.
- Permitir condiciones combinadas en playlists inteligentes.
- Ampliar historial por día y redescubrimiento local.
- Reutilizar hashes de duplicados cuando tamaño y modificación no cambien.
- Mantener overrides no destructivos; no modificar archivos sin confirmación explícita.

### Etapa E: apariencia y audio

- Separar tema, color, fondo, blur, distribución, densidad, portada, movimiento, tipografía y tamaño.
- Incluir AMOLED, Inmersivo, Tarjetas y Claro con diferencias reales.
- Mostrar una vista previa de biblioteca, fila, minirreproductor, reproductor y paneles.
- Mantener ReplayGain y audio offload; separar gapless de crossfade.
- Añadir perfiles por salida, limitador y ecualizador paramétrico solo cuando el dispositivo y la arquitectura lo permitan.
- No presentar datos de salida que Android no pueda medir.
- Mejorar el widget con favorito, playlists y estado real.

## Linux de escritorio

Implementa la versión en un directorio independiente `desktop/`. No uses Media3, Room, MediaStore, Compose ni servicios Android.

### Plataforma

- Rust como lenguaje principal.
- GTK4/libadwaita para la interfaz.
- GStreamer para reproducción.
- PipeWire para las salidas disponibles.
- zbus o equivalente para D-Bus/MPRIS.
- SQLite para biblioteca y sesión.
- Wayland como objetivo principal; no depender de X11.
- No depender de APIs privadas de Hyprland ni Caelestia.

### Funciones

- Biblioteca local, escaneo incremental, exclusiones y rutas configurables.
- Metadata y portada embebida con caché limitada.
- Fondo inmersivo precalculado.
- Favoritos, playlists, playlists inteligentes, historial y duplicados no destructivos.
- Cola persistente, reordenable, shuffle, repeat, temporizador, gapless y crossfade opcional.
- Restauración de canción, posición, cola y estado pausado.
- Importación/exportación M3U/M3U8.
- Temas AMOLED, Inmersivo, Tarjetas y Claro.
- Densidad, tipografía, movimiento reducido, accesibilidad AT-SPI, teclado, ratón, táctil y HiDPI.
- Ventana usable en mosaico, flotante, pequeña, maximizada y pantalla completa.

### MPRIS y Caelestia

Implementa `org.mpris.MediaPlayer2`, `org.mpris.MediaPlayer2.Player` y TrackList cuando sea viable, incluyendo:

- Metadata de título, artista, álbum, duración y URI de portada.
- `PlaybackStatus`, `Position`, `CanPlay`, `CanPause`, `CanSeek`, `CanGoNext` y `CanGoPrevious`.
- `Play`, `Pause`, `PlayPause`, `Stop`, `Next`, `Previous`, `Seek` y `SetPosition`.

Verifica `playerctl`, Caelestia Shell, Waybar, teclas multimedia, notificaciones, bloqueo de pantalla y auriculares Bluetooth. Caelestia controla el diseño de su tarjeta; AmberMusic solo publica estado, metadata, progreso, portada y acciones.

### XDG, Hyprland y CachyOS

Usa:

- `$XDG_CONFIG_HOME/ambermusic` o `~/.config/ambermusic`.
- `$XDG_DATA_HOME/ambermusic` o `~/.local/share/ambermusic`.
- `$XDG_CACHE_HOME/ambermusic` o `~/.cache/ambermusic`.

Añade `.desktop`, iconos, logs configurables, asociación opcional con audio, ejemplo de autostart para Hyprland sin modificar archivos del usuario y empaquetado `PKGBUILD`. Documenta las dependencias de CachyOS: Rust, GTK4, libadwaita, GStreamer, plugins, PipeWire, SQLite, D-Bus, `desktop-file-utils` y `shared-mime-info`.

## Validación

Cada etapa debe indicar archivos modificados, migraciones, pruebas automáticas, pruebas manuales, limitaciones de Android/HyperOS y estado final:

- Implementada.
- Probada automáticamente.
- Probada manualmente.
- Pendiente de validar en HyperOS, CachyOS, Hyprland o Caelestia.
- Limitada por el sistema.
- Fuera del alcance.

Ejecuta las pruebas y compilaciones configuradas en cada proyecto. En Android valida MediaSession, widget, pantalla bloqueada, Bluetooth, llamadas, escaneo, restauración y frames tardíos en el Redmi. En Linux valida reproducción, `playerctl`, MPRIS, Caelestia, PipeWire, Wayland, mosaico de Hyprland, HiDPI, memoria y caché. No declares una función terminada solo porque compile y no ocultes errores con valores silenciosos o capturas generales.
