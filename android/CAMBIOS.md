# Cambios de AmberMusic 1.0.0 respecto de 0.8.0

## Persistencia

- Room 3→4: añade `genre` a `songs` y la tabla `play_events` con sus índices y foreign key `ON DELETE CASCADE`.
- Migración sin `fallbackToDestructiveMigration`; historial de usuario antiguo se conserva.
- Primer escaneo después de actualizar fuerza una lectura completa de géneros; siguientes escaneos vuelven a ser incrementales.
- Smart playlists almacenadas como reglas en SharedPreferences con exportación de respaldo.

## Audio y HyperOS

- Leer ReplayGain de etiquetas compatibles al cambiar pista, procesando fuera del hilo principal.
- Protección frente a clipping en MP3 y FLAC; no se amplifica el volumen por encima de 100%.
- Deshabilitar offload al usar ReplayGain; mantener gapless compatible si offload está activo.
- Entrada suave opcional en pistas: volumen progresivo en lugar de mezclar dos pistas.
- MediaSession, `onTaskRemoved` y cierre explícito permanecen sin cambios funcionales.
- Actualización de widget solo ante eventos; sin timer de pantalla ni escaneo continuo.

## Interfaz

- `ImmersiveBackdrop`: downsample, blur de bajo coste precalculado y caché.
- Paleta dinámica y gradientes suaves, con interruptor en Ajustes.
- Swipe horizontal en portada.
- Búsqueda avanzada y filtros de formato.
- Pestañas Inteligentes y Duplicados, estadísticas semanales/mensuales.
- Ajuste temporal y edición de letras LRC.

## No incluido

- Android Auto (por petición).
- Crossfade real entre dos señales con solapamiento.
- Escritura directa de tags ID3/Vorbis en los archivos fuente.
- Perfiles Baseline medidos; requieren hardware y compilación instrumentada.
