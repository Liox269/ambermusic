# AmberMusic Android 2.5.0-beta3

Focalizado en el reproductor para Redmi Note 13 Pro 5G (HyperOS 3, navegación por gestos).

- Se ha simplificado la presentación de las carátulas: cuadro de dimensiones visibles, recorte de visualización (`Crop` predeterminado; `Fit` si se selecciona expresamente), transiciones suaves y recache de carátulas con bandas horizontales grandes detectadas de forma conservadora. Los archivos de música originales no se modifican.
- Los gestos horizontales sobre la carátula permiten pistas anterior y siguiente, y el deslizamiento vertical hacia abajo vuelve a la biblioteca. El sistema operativo gestiona el retroceso nativo desde ambos bordes sin un detector global de pantalla.
- El mini reproductor existente permite abrir el reproductor deslizando hacia arriba.
- La onda de progreso se limita al 83 % de ancho, con trazo de 1,8/2,1 dp, amplitud 2,8 dp, círculo de 3,9 dp y animación de seguimiento de 170 ms. Su zona táctil de 31 dp permite tocar/arrastrar; se añadieron acciones de accesibilidad y límites de seek.
- El diseño usa el espacio vertical disponible para situar acciones Letras, Cola, EQ y metadatos más abajo, manteniendo desplazamiento en pantallas compactas.
- Letras con desplazamiento a la línea activa y transición de color/tamaño de 220 ms; respeta Reducir movimiento.
- Pruebas unitarias de gestos y posiciones de búsqueda, además de pruebas anteriores del núcleo.

## Validación

La compilación de GitHub Actions verifica código y pruebas de unidad. La interacción táctil real, firma Android y funcionamiento específico de HyperOS aún requieren prueba en el Redmi. Una portada con bandas que no sean uniformes puede conservarlas: se evita recortar imágenes artísticas arbitrariamente.
