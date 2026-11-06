> **Actualización v1.2.0:** Consulta [README_v1.2.0.md](README_v1.2.0.md) para el nuevo blur y soporte de 120 Hz.

# AmberMusic v1.1.0 — Blue AMOLED UI

Actualización visual compatible con AmberMusic v1.0.0 (Aigis Edition).

## Cambios

- Corazón en lugar del check. Un toque activa/desactiva Favoritos (persistencia existente con Room); pulsación larga abre la colección Favoritos.
- Favoritos aparece como primera colección **virtual** en Categorías. No puede borrarse ni duplicarse; se actualiza con el estado de cada pista y no necesita migración de base de datos.
- Eliminada la franja de filtros de formato MP3, FLAC, etc. Los formatos siguen encontrándose con el buscador general.
- Eliminada la acción de compartir audio desde Now Playing; no se ha añadido tráfico de red.
- Tema azul AMOLED con superficies azul muy oscuro y detalle azul suave; Aigis rosa queda como tema alternativo.
- El blur por portada y la caché `ImmersiveBackdrop` permanecen intactos. Solo se añadió un gradiente azul tenue superpuesto para integrar con el negro.
- Gestos: bajar desde el reproductor para volver a la biblioteca; izquierda/derecha sobre la portada cambia de canción; derecha/izquierda en biblioteca cambia de pestaña; derecha en detalle vuelve al nivel anterior; bajar en la biblioteca actualiza.
- También hay controles visibles: flecha hacia abajo en reproductor, botón de biblioteca/cola abajo, tocar título Biblioteca vuelve a Canciones y botón Inicio en otras pestañas.
- Minirreproductor se expande al tocarlo o deslizar hacia arriba.
- Botones secundarios reorganizados en un menú superior; carpetas, actualizar y detener/salir siguen disponibles.
- **No hay cambios a Media3, base de datos, favoritos guardados, portadas, escáner, widgets ni icono Aigis.**

## Instalación CachyOS

```fish
cd ~/Downloads
unzip -o AmberMusic_Parche_v1.1.0_Blue_AMOLED.zip
bash AmberMusic-Parche-v1.1.0-Blue/instalar.sh
cd ~/Downloads/AmberMusic-Completo
env JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Si Gradle falla, **no instales el APK**. El instalador crea un respaldo en `.amber-backups/v1.1.0-FECHA/restaurar.sh`, que puedes ejecutar para recuperar el código anterior.

Al actualizar mediante `adb install -r` se preservan los datos siempre que mantengas el mismo `applicationId` y firma. No desinstales AmberMusic.

## Pruebas sugeridas

1. Reproduce una canción con portada y verifica que el blur sigue cambiando según la canción.
2. Toca el corazón; abre Categorías > Favoritos y verifica que aparece esa pista. Repite para quitarla.
3. Mantén pulsado el corazón para navegar directamente a Favoritos.
4. Desliza hacia abajo en Now Playing; desliza la portada a los lados; desliza las pestañas de la biblioteca.
5. Desliza hacia abajo una lista para reescanear la música; prueba pulsar Inicio y la flecha hacia abajo.
6. Comprueba que la reproducción continúa con pantalla apagada y la notificación de HyperOS funciona igual.
7. En Ajustes, prueba los temas Azul AMOLED / Aigis rosa.

**Validación:** integridad ZIP, respaldo y restauración de instalador, referencias estáticas. La compilación Android y la prueba real en el Redmi quedan pendientes.
