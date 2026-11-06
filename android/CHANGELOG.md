## 0.8.0 — Paquete único de estabilidad, interfaz y audio

- Migración de Room 2 a 3: metadatos corregidos localmente.
- Playlist CRUD y secciones favoritas / más escuchadas / recientes.
- Cola restituida sin reproducir automáticamente y control de orden.
- Temporizador de apagado y ecualizador del sistema.
- Letras LRC locales sincronizadas, selector de portada propia y tema Aigis rosa.
- Respaldos JSON de categorías, playlists y favoritos.
- Preferencia de Audio Offload condicionada a capacidades gapless.

### Lo que no implementa
Normalización real de volumen LUFS, escritura in-place de tags MP3/FLAC y crossfade. La compatibilidad con 120 Hz depende del dispositivo/versión y no equivale a 120 fps garantizados.
