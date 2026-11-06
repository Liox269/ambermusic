# AmberMusic v1.2.1 — Duplicados exactos

Esta actualización añade **Ocultar copias extra**, **Conservar** para elegir una copia de cada grupo y **Mostrar todas** para restaurar la biblioteca. Usa SHA-256 para comprobar contenido idéntico. Archivos con el mismo título pero distinta grabación, versión, tasa de bits o etiquetas se consideran diferentes y no se ocultan automáticamente.

- No elimina ni edita MP3, FLAC, listas, favoritos o categorías.
- Persistencia local mediante SharedPreferences con URIs de MediaStore.
- Evita modificar Media3, Room, el blur, el icono de Aigis y la isla dinámica.
- En **Mi AmberMusic → Duplicados**, pulsa **Analizar** y luego **Ocultar copias extra** o escoge **Conservar** en un grupo.
- **Mostrar todas** revierte inmediatamente la ocultación.
- La acción es bajo demanda; analiza de nuevo si copias otros archivos.

## Compilación

```bash
env JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Este paquete no fue compilado con el SDK Android en este entorno. Verifica `BUILD SUCCESSFUL` antes de instalarlo.
