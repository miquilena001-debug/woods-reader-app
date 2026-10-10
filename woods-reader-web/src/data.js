export const C = {
    bg: "#080B08", bgCard: "#0D120D", bgPanel: "#111711", bgElevated: "#162016",
    border: "#1E2B1E", borderMid: "#2A3D2A",
    green: "#4CAF72", greenDim: "#2D7A4F", greenGlow: "#4CAF7240",
    text: "#D4E8D4", textMid: "#8BAE8B", textDim: "#4A6A4A",
    amber: "#E8B86D", blue: "#6DB8E8", pink: "#E87BB0",
};

export const BOOKS = [
    { id: 1, t: "Cien Años de Soledad", a: "García Márquez", p: 73, col: "#4CAF72", status: "reading" },
    { id: 2, t: "El nombre del viento", a: "Patrick Rothfuss", p: 42, col: "#6DB8E8", status: "reading" },
    { id: 3, t: "Sapiens", a: "Yuval Noah Harari", p: 100, col: "#E8B86D", status: "done" },
    { id: 4, t: "Dune", a: "Frank Herbert", p: 18, col: "#E87BB0", status: "reading" },
    { id: 5, t: "1984", a: "George Orwell", p: 0, col: "#8BB8E8", status: "new" },
    { id: 6, t: "El Principito", a: "A. de Saint-Exupéry", p: 100, col: "#E8D06D", status: "done" },
];

export const READER_THEMES = {
    forest: { bg: "#080B08", text: "#D4E8D4", textMid: "#8BAE8B", accent: "#4CAF72", label: "Bosque" },
    day: { bg: "#F5F0E8", text: "#2A2018", textMid: "#7A6A58", accent: "#4A8A62", label: "Día" },
    sepia: { bg: "#1A1410", text: "#C4A882", textMid: "#7A6848", accent: "#E8B86D", label: "Sepia" },
    amoled: { bg: "#000000", text: "#FFFFFF", textMid: "#888888", accent: "#4CAF72", label: "AMOLED" },
};

export const EXCERPT = [
    { type: "chapter", text: "Capítulo XII" },
    { type: "para", text: "Muchos años después, frente al pelotón de fusilamiento, el coronel Aureliano Buendía había de recordar aquella tarde remota en que su padre lo llevó a conocer el hielo." },
    { type: "highlight", text: "La ciencia ha eliminado las distancias. El año pasado, unos sabios muy distinguidos instalaron en nuestro pueblo una pista de baile gigante cubierta con una lona de circo." },
    { type: "para", text: "Macondo era entonces una aldea de veinte casas de barro y cañabrava construidas a la orilla de un río de aguas diáfanas que se precipitaban por un lecho de piedras pulidas, blancas y enormes como huevos prehistóricos." },
    { type: "dialogue", text: "—Mira —dijo el padre—, eso es hielo." },
    { type: "para", text: "Cuando tocó el bloque, José Arcadio Buendía no pudo reprimir el estremecimiento. Sin saber qué decir, pagó otros cinco reales sólo para tocar de nuevo." },
    { type: "para", text: "Fue como si Dios hubiera decidido poner a prueba toda capacidad de asombro y mantuviera a los habitantes de Macondo en un permanente vaivén de espanto y de desilusión." },
];

export const TTS_WORDS = ["Muchos", "años", "después,", "frente", "al", "pelotón", "de", "fusilamiento,", "el", "coronel", "Aureliano", "Buendía", "había", "de", "recordar"];
export const WAVEFORM = Array.from({ length: 44 }, (_, i) => 0.22 + Math.abs(Math.sin(i * 0.7 + 1.2)) * 0.78);

export const HL_COLORS = [
    { hex: "#FFF176", label: "Amarillo", badge: "#B8A000" },
    { hex: "#A5D6A7", label: "Verde", badge: "#4CAF72" },
    { hex: "#90CAF9", label: "Azul", badge: "#6DB8E8" },
    { hex: "#F48FB1", label: "Rosa", badge: "#E87BB0" },
];

export const HIGHLIGHTS_DATA = [
    { text: "Muchos años después, frente al pelotón de fusilamiento, el coronel Aureliano Buendía había de recordar aquella tarde remota.", color: "#FFF176", note: "Apertura perfecta — in medias res", chapter: "Cap. 1", page: 1 },
    { text: "Macondo era entonces una aldea de veinte casas de barro y cañabrava construidas a la orilla de un río de aguas diáfanas.", color: "#A5D6A7", note: "El mundo antes de la corrupción", chapter: "Cap. 1", page: 3 },
    { text: "La ciencia ha eliminado las distancias.", color: "#90CAF9", note: "Ironía — el gitano modernizando Macondo", chapter: "Cap. 1", page: 8 },
    { text: "El tiempo no pasa... simplemente da vueltas.", color: "#F48FB1", note: "Tema central de la obra", chapter: "Cap. 3", page: 42 },
];

export const BOOKMARKS_DATA = [
    { label: "Donde lo dejé", chapter: "Cap. 12", page: 344, auto: true },
    { label: "La batalla de Macondo", chapter: "Cap. 9", page: 212, auto: false },
    { label: "Muerte del coronel", chapter: "Cap. 15", page: 401, auto: false },
];

export const SEARCH_CORPUS = [
    { pre: "…era la ", match: "soledad", post: " que sufría en vida como si viviera en el mundo de los muertos.", chapter: "Cap. 12", page: 344, book: "Cien Años de Soledad" },
    { pre: "…la condena a cien años de ", match: "soledad", post: " que no tendría una segunda oportunidad sobre la Tierra.", chapter: "Cap. 32", page: 471, book: "Cien Años de Soledad" },
    { pre: "…sabía que José Arcadio Buendía tenía su ", match: "soledad", post: " más profunda dentro del cuarto de cañabrava.", chapter: "Cap. 3", page: 41, book: "Cien Años de Soledad" },
    { pre: "…el coronel vivía en una ", match: "soledad", post: " tan completa que no necesitaba espejos para saber quién era.", chapter: "Cap. 18", page: 387, book: "Cien Años de Soledad" },
    { pre: "…compartían una misma ", match: "soledad", post: " sin que ninguno de los dos hubiera querido elegirla.", chapter: "Cap. 7", page: 143, book: "El nombre del viento" },
];

export const KPI_DATA = [
    { label: "Páginas hoy", value: "47", unit: "págs", color: "#4CAF72" },
    { label: "Tiempo hoy", value: "1h 23m", unit: "", color: "#6DB8E8" },
    { label: "Racha actual", value: "12", unit: "días", color: "#E8B86D" },
    { label: "Este mes", value: "342", unit: "págs", color: "#E87BB0" },
];
export const DAYS = ["L", "M", "X", "J", "V", "S", "D"];
export const HEATMAP_DATA = [[1, 3, 5, 0, 2, 4, 3], [0, 2, 4, 5, 3, 1, 0], [3, 5, 4, 2, 4, 5, 3], [1, 0, 3, 4, 2, 5, 4]];
export const WEEKLY_PAGES = [23, 45, 0, 67, 42, 89, 47];
export const BY_BOOK_DATA = [
    { title: "Cien Años de Soledad", pages: 156, color: "#4CAF72" },
    { title: "El nombre del viento", pages: 98, color: "#6DB8E8" },
    { title: "Dune", pages: 88, color: "#E87BB0" },
];
export const BY_BOOK_TOTAL = BY_BOOK_DATA.reduce((s, b) => s + b.pages, 0);

export const SETTINGS_THEMES = [
    { key: "forest", name: "Bosque", bg: "#080B08", accent: "#4CAF72" },
    { key: "day", name: "Día", bg: "#F5F0E8", accent: "#4A8A62" },
    { key: "sepia", name: "Sepia", bg: "#1A1410", accent: "#E8B86D" },
    { key: "amoled", name: "AMOLED", bg: "#000000", accent: "#4CAF72" },
    { key: "sunset", name: "Sunset", bg: "#0D080D", accent: "#E87BB0" },
];
export const FONTS = [
    { key: "literata", label: "Literata", style: "serif" },
    { key: "merriweather", label: "Merriweather", style: "serif" },
    { key: "eb-garamond", label: "EB Garamond", style: "serif" },
    { key: "source-sans", label: "Source Sans", style: "sans-serif" },
];
