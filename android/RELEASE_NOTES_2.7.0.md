# AmberMusic Android 2.7.0-beta6 — Letras inmersivas

- Eliminado el bottom sheet de letras. Ahora Letras es una pantalla dedicada con retorno por gesto nativo y botón de retroceso.
- Fondo desenfocado de la carátula actual con transición de 360 ms; se reutiliza el ambient WebP fuera del hilo UI y se muestra una imagen de respaldo desenfocada durante su generación.
- Encabezado compacto con carátula, título y artista; importación, LRCLIB, offset ±250 ms y borrar en menú de opciones.
- Seguimiento centrado de líneas LRC, color y tamaño animados (280 ms), desplazamiento por línea activa y soporte para tocar una línea con timestamp para adelantar.
- Estado sincronizado al reloj de Media3; pausa/seek no inventan tiempos. Opción Reducir movimiento anula las animaciones.
- Barra inferior minimalista con progreso, reloj y reproducción/pausa.
- Añadidas pruebas unitarias de cambio temporal, seek atrás, inicio y letras sin marcas de tiempo.
- Sin modificaciones en Room, listas, favoritos, biblioteca o la app Linux.

La validación real en Redmi es necesaria para ajustar experiencia visual, rendimiento y colores según el álbum.
