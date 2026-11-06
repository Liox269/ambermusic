# AmberMusic v1.4.0 — UI Reliability

Basado en v1.3.0 que compiló con GitHub Actions. Incluye la corrección del acceso directo a Ajustes (v1.3.1) más mensajes de estado vacío en Playlists, Inteligentes y Duplicados, y transiciones de navegación más rápidas, sin tocar Media3, Room, las carátulas ni el motor de blur.

## Lo que NO promete esta compilación
- Las mejoras de respuesta en pantallas deben probarse en el teléfono. No se han medido FPS ni rendimiento de este paquete en un dispositivo real.
- No introduce nueva integración con servicios en línea; la app sigue siendo local.
- No sustituye el sistema de firma del APK. Para actualizar conservando datos, las firmas deben coincidir.

## Compilación desde celular
Sube `AmberMusic_v1.4.0_Source.zip` a la raíz del repositorio `Liox269/ambermusic`. El flujo de GitHub Actions `build-apk.yml` actualizado lo compilará. En una ejecución exitosa, ve a Summary → Artifacts para descargar el ZIP con el APK.

Mantén una exportación de respaldo antes de modificar una instalación existente.
