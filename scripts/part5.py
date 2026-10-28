content = """
---

## 10. Datos de Ejemplo (JSON)

Fixture completo realista para poblar todas las pantallas del maquetado HTML sin requerir conexión a base de datos: 6 libros, 3 subrayados, 2 marcadores, sesiones de lectura, estadísticas completas (racha, palabras diarias, mapa de calor de 12 semanas) y el grafo de conocimiento narrativo con entidades y relaciones.

```json
{
  \"books\": [
    {
      \"id\": 1,
      \"title\": \"El Nombre del Viento\",
      \"author\": \"Patrick Rothfuss\",
      \"format\": \"epub\",
      \"coverPath\": \"covers/name_of_the_wind.jpg\",
      \"progress\": 0.42,
      \"currentPage\": 280,
      \"pageCount\": 662,
      \"readingStatus\": \"READING\",
      \"collection\": \"Favoritos\",
      \"userRating\": 5,
      \"readingTimeRemainingMinutes\": 320,
      \"language\": \"es\",
      \"description\": \"He robado princesas a reyes agónicos. Incendié la ciudad de Trebon. He pasado la noche con Felurian y he despertado con la cordura intacta y el orgullo maltrecho.\",
      \"lastReadTimestamp\": 1729987200000
    },
    {
      \"id\": 2,
      \"title\": \"Dune\",
      \"author\": \"Frank Herbert\",
      \"format\": \"epub\",
      \"coverPath\": \"covers/dune.jpg\",
      \"progress\": 0.88,
      \"currentPage\": 612,
      \"pageCount\": 695,
      \"readingStatus\": \"READING\",
      \"collection\": \"Ciencia Ficción\",
      \"userRating\": 5,
      \"readingTimeRemainingMinutes\": 65,
      \"language\": \"es\",
      \"description\": \"En el desértico planeta Arrakis, el joven Paul Atreides se enfrenta a una traición política y asume su destino mesiánico en medio de las dunas.\",
      \"lastReadTimestamp\": 1729893600000
    },
    {
      \"id\": 3,
      \"title\": \"Neuromante\",
      \"author\": \"William Gibson\",
      \"format\": \"epub\",
      \"coverPath\": \"covers/neuromancer.jpg\",
      \"progress\": 1.0,
      \"currentPage\": 320,
      \"pageCount\": 320,
      \"readingStatus\": \"COMPLETED\",
      \"collection\": \"Cyberpunk\",
      \"userRating\": 4,
      \"readingTimeRemainingMinutes\": 0,
      \"language\": \"es\",
      \"description\": \"El cielo sobre el puerto tenía el color de una pantalla de televisor sintonizada en un canal muerto.\",
      \"lastReadTimestamp\": 1729202400000
    },
    {
      \"id\": 4,
      \"title\": \"Hyperion\",
      \"author\": \"Dan Simmons\",
      \"format\": \"epub\",
      \"coverPath\": null,
      \"progress\": 0.15,
      \"currentPage\": 75,
      \"pageCount\": 500,
      \"readingStatus\": \"READING\",
      \"collection\": \"Ciencia Ficción\",
      \"userRating\": 4,
      \"readingTimeRemainingMinutes\": 410,
      \"language\": \"es\",
      \"description\": \"En vísperas del Armagedón galáctico, siete peregrinos viajan a las Tumbas del Tiempo para enfrentarse al Alcaudón.\",
      \"lastReadTimestamp\": 1728511200000
    },
    {
      \"id\": 5,
      \"title\": \"Manual de Inteligencia Artificial Moderna\",
      \"author\": \"Stuart Russell & Peter Norvig\",
      \"format\": \"pdf\",
      \"coverPath\": \"covers/aima.jpg\",
      \"progress\": 0.28,
      \"currentPage\": 312,
      \"pageCount\": 1150,
      \"readingStatus\": \"READING\",
      \"collection\": \"Estudio\",
      \"userRating\": 5,
      \"readingTimeRemainingMinutes\": 950,
      \"language\": \"es\",
      \"description\": \"El texto fundamental de referencia sobre teoría y aplicación práctica de agentes inteligentes y aprendizaje automático.\",
      \"lastReadTimestamp\": 1727733600000
    },
    {
      \"id\": 6,
      \"title\": \"Cien Años de Soledad\",
      \"author\": \"Gabriel García Márquez\",
      \"format\": \"epub\",
      \"coverPath\": \"covers/soledad.jpg\",
      \"progress\": 0.0,
      \"currentPage\": 0,
      \"pageCount\": 448,
      \"readingStatus\": \"UNREAD\",
      \"collection\": \"Clásicos\",
      \"userRating\": 0,
      \"readingTimeRemainingMinutes\": 520,
      \"language\": \"es\",
      \"description\": \"Muchos años después, frente al pelotón de fusilamiento, el coronel Aureliano Buendía había de recordar aquella tarde remota en que su padre lo llevó a conocer el hielo.\",
      \"lastReadTimestamp\": 1726000000000
    }
  ],
  \"highlights\": [
    {
      \"id\": 1,
      \"bookId\": 1,
      \"chapterTitle\": \"Capítulo 7: De palabras y fuego\",
      \"text\": \"Viajé, amé, perdí, confié y fui traicionado. En un rincón de mi memoria aún arde la madera de la posada Roca de Guía.\",
      \"color\": \"yellow\",
      \"note\": \"Definición fundamental del carácter de Kvothe.\",
      \"createdAt\": 1729980000000
    },
    {
      \"id\": 2,
      \"bookId\": 1,
      \"chapterTitle\": \"Capítulo 16: Hojas al viento\",
      \"text\": \"El silencio era de tres partes. La parte más obvia era una calma hueca y resonante...\",
      \"color\": \"green\",
      \"note\": \"El célebre silencio de tres partes del prólogo y epílogo.\",
      \"createdAt\": 1729951200000
    },
    {
      \"id\": 3,
      \"bookId\": 2,
      \"chapterTitle\": \"Capítulo 1: La prueba del Gom Jabbar\",
      \"text\": \"El misterio de la vida no es un problema que deba ser resuelto, sino una realidad que debe ser experimentada.\",
      \"color\": \"blue\",
      \"note\": \"Cita de la Reverenda Madre.\",
      \"createdAt\": 1729864800000
    }
  ],
  \"bookmarks\": [
    {
      \"id\": 1,
      \"bookId\": 1,
      \"title\": \"Encuentro con Abenthy\",
      \"page\": 84,
      \"progress\": 0.126,
      \"createdAt\": 1729800000000
    },
    {
      \"id\": 2,
      \"bookId\": 1,
      \"title\": \"La Universidad y el Archivo\",
      \"page\": 240,
      \"progress\": 0.362,
      \"createdAt\": 1729920000000
    }
  ],
  \"readingSessions\": [
    { \"date\": \"2026-10-20\", \"minutesRead\": 42, \"wordsRead\": 10500 },
    { \"date\": \"2026-10-21\", \"minutesRead\": 35, \"wordsRead\": 8750 },
    { \"date\": \"2026-10-22\", \"minutesRead\": 60, \"wordsRead\": 15000 },
    { \"date\": \"2026-10-23\", \"minutesRead\": 25, \"wordsRead\": 6250 },
    { \"date\": \"2026-10-24\", \"minutesRead\": 50, \"wordsRead\": 12500 },
    { \"date\": \"2026-10-25\", \"minutesRead\": 45, \"wordsRead\": 11250 },
    { \"date\": \"2026-10-26\", \"minutesRead\": 35, \"wordsRead\": 8750 }
  ],
  \"statistics\": {
    \"currentStreakDays\": 14,
    \"longestStreakDays\": 28,
    \"averageWpm\": 250,
    \"totalBooksRead\": 12,
    \"totalMinutesRead\": 8420,
    \"dailyGoalMinutes\": 45,
    \"todayMinutesRead\": 35,
    \"formatDistribution\": {
      \"epub\": 0.82,
      \"pdf\": 0.18
    },
    \"weeklyHeatmapMatrix12Weeks\": [
      [0, 15, 30, 45, 20, 0, 10, 35, 45, 60, 25, 35],
      [20, 25, 40, 50, 30, 15, 20, 40, 50, 45, 30, 40],
      [30, 35, 45, 40, 25, 20, 30, 45, 60, 50, 40, 30],
      [15, 20, 30, 35, 40, 25, 35, 50, 40, 35, 25, 45],
      [45, 50, 60, 70, 50, 40, 45, 60, 55, 60, 50, 50],
      [60, 65, 80, 75, 60, 55, 60, 70, 65, 75, 65, 60],
      [40, 45, 50, 60, 45, 30, 40, 55, 45, 50, 45, 35]
    ]
  },
  \"knowledgeGraph\": {
    \"bookId\": 1,
    \"nodes\": [
      { \"id\": \"n1\", \"name\": \"Kvothe\", \"type\": \"CHARACTER\", \"mentionCount\": 340, \"sampleContext\": \"Kvothe miró hacia el horizonte mientras afinaba las cuerdas de su laúd.\" },
      { \"id\": \"n2\", \"name\": \"Denna\", \"type\": \"CHARACTER\", \"mentionCount\": 165, \"sampleContext\": \"Denna sonrió con esa mirada esquiva que parecía desvanecerse con la brisa.\" },
      { \"id\": \"n3\", \"name\": \"Bast\", \"type\": \"CHARACTER\", \"mentionCount\": 98, \"sampleContext\": \"Bast colocó un plato con manzanas en la barra de madera pulida.\" },
      { \"id\": \"n4\", \"name\": \"La Universidad\", \"type\": \"LOCATION\", \"mentionCount\": 142, \"sampleContext\": \"Cruzó el puente de piedra sobre el río Omeca en dirección a los patios de la Universidad.\" },
      { \"id\": \"n5\", \"name\": \"Posada Roca de Guía\", \"type\": \"LOCATION\", \"mentionCount\": 85, \"sampleContext\": \"El fuego de la chimenea proyectaba sombras en las paredes de la posada.\" },
      { \"id\": \"n6\", \"name\": \"Laúd de siete cuerdas\", \"type\": \"ITEM\", \"mentionCount\": 62, \"sampleContext\": \"Sacó con sumo cuidado su laúd de siete cuerdas del estuche de terciopelo verde.\" },
      { \"id\": \"n7\", \"name\": \"Los Chandrian\", \"type\": \"FACTION\", \"mentionCount\": 78, \"sampleContext\": \"¿Dónde estaban los fuegos azules y el hierro oxidado de los Chandrian?\" },
      { \"id\": \"n8\", \"name\": \"Nominación\", \"type\": \"CONCEPT\", \"mentionCount\": 54, \"sampleContext\": \"El nombre del viento no era algo que pudiera atraparse en un frasco de alquimia.\" }
    ],
    \"edges\": [
      { \"source\": \"n1\", \"target\": \"n2\", \"weight\": 4.5, \"coOccurrences\": 92 },
      { \"source\": \"n1\", \"target\": \"n3\", \"weight\": 3.8, \"coOccurrences\": 74 },
      { \"source\": \"n1\", \"target\": \"n4\", \"weight\": 5.0, \"coOccurrences\": 118 },
      { \"source\": \"n1\", \"target\": \"n5\", \"weight\": 3.5, \"coOccurrences\": 65 },
      { \"source\": \"n1\", \"target\": \"n6\", \"weight\": 3.2, \"coOccurrences\": 48 },
      { \"source\": \"n1\", \"target\": \"n7\", \"weight\": 4.0, \"coOccurrences\": 56 },
      { \"source\": \"n1\", \"target\": \"n8\", \"weight\": 3.0, \"coOccurrences\": 38 },
      { \"source\": \"n3\", \"target\": \"n5\", \"weight\": 3.4, \"coOccurrences\": 52 },
      { \"source\": \"n4\", \"target\": \"n8\", \"weight\": 2.8, \"coOccurrences\": 31 }
    ]
  }
}
```

---

## 11. Pendiente / No Pude Determinar

A continuación se registran los elementos que no están explícitamente parametrizados con constantes numéricas en el código y adoptan los comportamientos por defecto del sistema o bibliotecas subyacentes:

1. **Grosor del Borde en Diálogos de Material 3 (`AlertDialog`):**
   - El código fuente no especifica un `border = BorderStroke(...)` personalizado en `WebToEpubImporterDialog` ni en los diálogos de confirmación estándar, por lo que heredan el estilo nativo de Material 3 (`0.dp` de borde y elevación tonal de superficie de `6.dp`).
2. **Duración de Animación de Parpadeo del Cursor del Ticker TTS:**
   - La velocidad del deslizamiento tipo marquesina (`Marquee`) de la frase en locución utiliza el modificador experimental `basicMarquee()` de Android Compose 1.7+, cuyos valores internos son: velocidad de `30.dp` por segundo, retraso inicial de `1200ms` y separación de repetición de `64.dp`.
3. **Curva de Bezier para Deslizamiento de Página en Readium:**
   - Cuando el paso de página 3D (`BookFlip3D`) está desactivado y se utiliza `Slide`, la curva de aceleración física de arrastre táctil es gestionada internamente por el motor de vistas de `ViewPager2` de Readium Navigator con un interpolador cúbico de desaceleración estándar de Android (`PathInterpolator(0.25f, 0.1f, 0.25f, 1f)`).
4. **Umbral de Luminosidad para Inversión de Texto en Portadas sin Carátula (`BookInitials`):**
   - El color de las iniciales se computa mediante luminancia perceptiva W3C: si la luminancia del color generado por el hash del título es mayor a `0.5`, se dibuja texto `#111811`; si es menor o igual, se dibuja `#FFFFFF`.
"""
