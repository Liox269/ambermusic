# AmberMusic Android 2.6.1-beta5 — biblioteca más limpia

- Eliminada por completo la sección "Seguir escuchando" y su carrusel.
- Retirada la pestaña "Todo" porque presentaba la misma lista que "Canciones".
- Pestañas: Canciones, Álbumes, Artistas y Categorías; conserva las rutas internas previas y migra la selección guardada antigua.
- Eliminado el título de sección "Canciones" repetido; contador visible una sola vez en el encabezado de ordenación.
- Subtítulo del título principal: perfil activo, sin volver a repetir el contador de canciones.
- Sin cambios en la base Room, la música, los favoritos ni los ajustes de audio.
- Sin cambios en la app Linux.

Verificación: GitHub Actions testDebugUnitTest y assembleDebug; pendiente de revisión visual en Redmi.
