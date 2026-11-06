# AmberMusic v1.4.2 — Biblioteca y ajustes compactos

- En la barra superior de Biblioteca únicamente aparece el menú de tres puntos (salvo retorno de detalle).
- El menú abre Ajustes; selección de carpetas y actualización de biblioteca están dentro de Ajustes.
- El reproductor conserva cola, temporizador y ecualizador; se ha eliminado «Detener música y salir» del menú.
- Al abandonar la actividad se fuerza una instantánea de canción, cola y posición; al restaurar se mantiene pausada.
- Al quitar la app de recientes no se vacía la cola guardada. El proceso de audio sigue bajo control de Android.
- Conservará el icono Aigis, los temas y el fondo dinámico actual.

## Compilar

`gradle :app:assembleDebug`

Proyecto derivado de v1.4.1. No se ha realizado una compilación Android completa en este entorno. Requiere validación en GitHub Actions o Android Studio.
