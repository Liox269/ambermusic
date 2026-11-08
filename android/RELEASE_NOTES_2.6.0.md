# AmberMusic 2.6.0-beta4 — carátulas y composición

Se corrigió el bug de tamaño en CoverCarousel: AnimatedContent ahora llena el cuadrado;
AsyncImage llena ese mismo cuadrado y utiliza Crop (Fit es una opción configurable).
Así, una portada rectangular se recorta al formato de la vista, en vez de
mostrarse como miniatura dentro de un gran panel.

El ambiente desenfocado se habilita también con el tema AMOLED, si la opción
«Inmersión» está activada. Durante la generación del fondo precalculado se
muestra una versión del arte desenfocada por GPU, que se sustituye suavemente
cuando está disponible el archivo WebP de caché. Se habilita la preferencia
una sola vez en esta revisión, conservando el control para desactivarla.

Los controles de reproducción se agrupan a 18 dp por debajo del bloque de
tiempos y progreso, eliminando el hueco dinámico anterior de hasta 90 dp.

Validaciones pendientes fuera del CI: apariencia final con las carátulas reales,
correcta transición a GPU blurred background en HyperOS y ergonomía de gestos
sobre la pantalla física.
