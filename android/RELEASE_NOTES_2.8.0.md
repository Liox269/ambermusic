# AmberMusic 2.8.0-beta7 — letras rápidas y barra interactiva

- La posición de reproducción se consulta a 150 ms mientras Letras está abierta;
  fuera de ella conserva 750 ms para ahorrar batería.
- El resaltado adelanta visualmente 650 ms respecto a la marca del LRC, sin alterar
  el audio. Si el LRC está mal sincronizado, el usuario conserva el ajuste ±250 ms.
- Los desplazamientos entre líneas cercanas son animados; los saltos grandes se
  colocan de inmediato para que la animación no quede detrás del audio.
- Al cambiar de canción, la app solicita automáticamente letras a LRCLIB si no hay
  una copia local. Guarda archivos, respeta los LRC importados, limita consultas
  repetidas y nunca bloquea la reproducción. Requiere conexión a Internet.
- La barra de progreso de Letras acepta toque y arrastre con objetivo táctil de 36 dp,
  y busca el tiempo a través del controlador Media3.
- Tests nuevos de anticipación de línea y límites de seek.

Nota: al reproducir canciones sin letras locales, la app envía los metadatos
(título, artista, álbum, duración) a lrclib.net para encontrar letras.
Los archivos ya descargados se reutilizan sin red.
