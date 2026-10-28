/**
 * Woods Reader Mockup - Interactive Simulation Engine
 */

let APP_DATA = null;
let currentTheme = 'HIDDEN_FOREST';
let activeScreenId = 'screen-library';
let selectedBook = null;
let isTtsPlaying = false;
let currentBookProgress = 0.42;

// Material Icons SVG Definitions
const ICONS = {
  book: '<svg viewBox="0 0 24 24"><path d="M18 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2zM6 4h5v8l-2.5-1.5L6 12V4z"/></svg>',
  autoStories: '<svg viewBox="0 0 24 24"><path d="M19 1l-5 5v11l5-4.5V1zM1 6v14.65c0 .25.25.5.5.5.1 0 .15-.05.25-.05C3.1 20.45 5.05 20 6.5 20c1.95 0 4.05.4 5.5 1.5V6c-1.45-1.1-3.55-1.5-5.5-1.5S2.45 4.9 1 6zm22 13.5V6c-.6-.45-1.25-.75-2-1v13.5c.75.25 1.4.55 2 1z"/></svg>',
  barChart: '<svg viewBox="0 0 24 24"><path d="M5 9.2h3V19H5zM10.6 5h2.8v14.2h-2.8zm5.6 8H19v6.2h-2.8z"/></svg>',
  search: '<svg viewBox="0 0 24 24"><path d="M15.5 14h-.79l-.28-.27C15.41 12.59 16 11.11 16 9.5 16 5.91 13.09 3 9.5 3S3 5.91 3 9.5 5.91 16 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z"/></svg>',
  sync: '<svg viewBox="0 0 24 24"><path d="M12 4V1L8 5l4 4V6c3.31 0 6 2.69 6 6 0 1.01-.25 1.97-.7 2.8l1.46 1.46C19.54 15.03 20 13.57 20 12c0-4.42-3.58-8-8-8zm0 14c-3.31 0-6-2.69-6-6 0-1.01.25-1.97.7-2.8L5.24 7.74C4.46 8.97 4 10.43 4 12c0 4.42 3.58 8 8 8v3l4-4-4-4v3z"/></svg>',
  settings: '<svg viewBox="0 0 24 24"><path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z"/></svg>',
  arrowBack: '<svg viewBox="0 0 24 24"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg>',
  play: '<svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>',
  pause: '<svg viewBox="0 0 24 24"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>',
  checkCircle: '<svg viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 15l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/></svg>',
  star: '<svg viewBox="0 0 24 24"><path d="M12 17.27L18.18 21l-1.64-7.03L22 9.24l-7.19-.61L12 2 9.19 8.63 2 9.24l5.46 4.73L5.82 21z"/></svg>',
  moreVert: '<svg viewBox="0 0 24 24"><path d="M12 8c1.1 0 2-.9 2-2s-.9-2-2-2-2 .9-2 2 .9 2 2 2zm0 2c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm0 6c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z"/></svg>',
  formatList: '<svg viewBox="0 0 24 24"><path d="M3 13h2v-2H3v2zm0 4h2v-2H3v2zm0-8h2V7H3v2zm4 4h14v-2H7v2zm0 4h14v-2H7v2zM7 7v2h14V7H7z"/></svg>',
  tune: '<svg viewBox="0 0 24 24"><path d="M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z"/></svg>',
  headphones: '<svg viewBox="0 0 24 24"><path d="M12 3a9 9 0 0 0-9 9v7c0 1.1.9 2 2 2h4v-8H5v-1c0-3.87 3.13-7 7-7s7 3.13 7 7v1h-4v8h4c1.1 0 2-.9 2-2v-7a9 9 0 0 0-9-9z"/></svg>',
  hub: '<svg viewBox="0 0 24 24"><path d="M10 3a3 3 0 0 0-3 3c0 .35.06.68.17 1H4a2 2 0 0 0-2 2v2a2 2 0 0 0 2 2h3.17c-.11.32-.17.65-.17 1a3 3 0 0 0 3 3 3 3 0 0 0 3-3c0-.35-.06-.68-.17-1H16a2 2 0 0 0 2-2v-2a2 2 0 0 0-2-2h-3.17c.11-.32.17-.65.17-1a3 3 0 0 0-3-3zm0 2a1 1 0 1 1 0 2 1 1 0 0 1 0-2z"/></svg>',
  bookmark: '<svg viewBox="0 0 24 24"><path d="M17 3H7c-1.1 0-1.99.9-1.99 2L5 21l7-3 7 3V5c0-1.1-.9-2-2-2z"/></svg>',
  editNote: '<svg viewBox="0 0 24 24"><path d="M3 10h11v2H3v-2zm0-2h11V6H3v2zm0 8h7v-2H3v2zm15.01-3.13l.71-.71a.996.996 0 0 1 1.41 0l.71.71c.39.39.39 1.02 0 1.41l-.71.71-2.12-2.12zm-.71.71l-5.3 5.3V21h2.12l5.3-5.3-2.12-2.12z"/></svg>'
};

// Initialize App
async function initApp() {
  try {
    const res = await fetch('data.json');
    APP_DATA = await res.json();
    selectedBook = APP_DATA.books[0];
    renderAll();
    setupEventListeners();
  } catch (e) {
    console.error('Error loading data.json:', e);
  }
}

function renderAll() {
  renderLibrary();
  renderBookDetail();
  renderReader();
  renderStats();
  renderGraph();
}

// 1. Render Library
function renderLibrary() {
  const container = document.getElementById('books-grid');
  if (!container || !APP_DATA) return;

  const heroBook = APP_DATA.books.find(b => b.progress > 0 && b.progress < 1.0) || APP_DATA.books[0];
  const heroContainer = document.getElementById('hero-slot');
  if (heroContainer) {
    heroContainer.innerHTML = `
      <div class="continue-reading-hero" onclick="openReader(${heroBook.id})">
        <div class="hero-cover">
          ${heroBook.coverPath 
            ? `<img src="${heroBook.coverPath}" alt="${heroBook.title}">` 
            : `<div class="book-initials">${heroBook.title.substring(0, 2).toUpperCase()}</div>`}
          <div class="woods-badge badge-${heroBook.format} card-badge">${heroBook.format}</div>
        </div>
        <div class="hero-info">
          <span class="hero-label">CONTINUAR LEYENDO</span>
          <h2 class="hero-title">${heroBook.title}</h2>
          <span class="hero-author">${heroBook.author}</span>
          <div class="hero-progress-group">
            <div class="woods-progress-track">
              <div class="woods-progress-fill" style="width: ${heroBook.progress * 100}%"></div>
            </div>
            <div class="hero-progress-labels">
              <span>${Math.round(heroBook.progress * 100)}%</span>
              <span>${heroBook.readingTimeRemainingMinutes} min restantes</span>
            </div>
          </div>
        </div>
      </div>
    `;
  }

  // Render 2-Column Grid
  container.innerHTML = APP_DATA.books.map(book => `
    <div class="book-card" onclick="openDetail(${book.id})">
      <div class="card-cover">
        ${book.coverPath 
          ? `<img src="${book.coverPath}" alt="${book.title}">` 
          : `<div class="book-initials">${book.title.substring(0, 2).toUpperCase()}</div>`}
        <div class="woods-badge badge-${book.format} card-badge">${book.format}</div>
        ${book.progress >= 1.0 ? `<div class="card-completed-icon">${ICONS.checkCircle}</div>` : ''}
        <div class="woods-progress-track">
          <div class="woods-progress-fill" style="width: ${book.progress * 100}%"></div>
        </div>
      </div>
      <div class="card-details">
        <h4 class="card-title">${book.title}</h4>
        <span class="card-author">${book.author}</span>
      </div>
    </div>
  `).join('');
}

// 2. Render Detail Screen
function renderBookDetail() {
  if (!selectedBook) return;
  const screen = document.getElementById('screen-detail');
  if (!screen) return;

  screen.innerHTML = `
    <div class="detail-top-bar">
      <button class="icon-btn" onclick="navigateTo('screen-library')">${ICONS.arrowBack}</button>
      <div class="header-actions">
        <button class="icon-btn" onclick="openSideMenu()">${ICONS.moreVert}</button>
      </div>
    </div>
    <div class="detail-hero">
      <div class="detail-cover-box">
        ${selectedBook.coverPath 
          ? `<img src="${selectedBook.coverPath}" alt="${selectedBook.title}">` 
          : `<div class="book-initials" style="height: 100%;">${selectedBook.title.substring(0, 2).toUpperCase()}</div>`}
      </div>
      <h2 class="detail-title">${selectedBook.title}</h2>
      <span class="detail-author">${selectedBook.author}</span>
      <div class="progress-pill">
        ${ICONS.autoStories}
        <span>${Math.round(selectedBook.progress * 100)}% completado</span>
      </div>
      <div class="star-rating-row">
        ${Array(5).fill(0).map((_, i) => `<span style="opacity: ${i < selectedBook.userRating ? '1' : '0.3'}">${ICONS.star}</span>`).join('')}
      </div>
    </div>

    <div class="metadata-card">
      <div class="meta-col">
        <span class="meta-label">PÁGINAS</span>
        <span class="meta-val">${selectedBook.pageCount}</span>
      </div>
      <div class="meta-divider"></div>
      <div class="meta-col">
        <span class="meta-label">FORMATO</span>
        <span class="meta-val">${selectedBook.format.toUpperCase()}</span>
      </div>
      <div class="meta-divider"></div>
      <div class="meta-col">
        <span class="meta-label">TIEMPO REST.</span>
        <span class="meta-val">${selectedBook.readingTimeRemainingMinutes}m</span>
      </div>
      <div class="meta-divider"></div>
      <div class="meta-col">
        <span class="meta-label">IDIOMA</span>
        <span class="meta-val">${selectedBook.language.toUpperCase()}</span>
      </div>
    </div>

    <div class="synopsis-card">
      <h3>Sinopsis</h3>
      <p class="synopsis-text">${selectedBook.description}</p>
    </div>

    <button class="read-fab" onclick="openReader(${selectedBook.id})">
      ${ICONS.autoStories}
      <span>Leer ahora</span>
    </button>
  `;
}

// 3. Render Reader Screen
function renderReader() {
  if (!selectedBook) return;
  const screen = document.getElementById('screen-reader');
  if (!screen) return;

  screen.innerHTML = `
    <!-- Top Bar -->
    <div class="reader-top-bar" id="reader-top">
      <button class="icon-btn" onclick="navigateTo('screen-library')">${ICONS.arrowBack}</button>
      <div class="reader-title-info">
        <div class="rt-title">${selectedBook.title}</div>
        <div class="rt-chap">Capítulo 7: De palabras y fuego</div>
      </div>
      <div style="display: flex; gap: 4px;">
        <button class="icon-btn" onclick="navigateTo('screen-graph')">${ICONS.hub}</button>
        <button class="icon-btn" onclick="openSideMenu()">${ICONS.moreVert}</button>
      </div>
    </div>

    <!-- Book Page Text -->
    <div class="reader-book-content" onclick="toggleReaderControls()">
      <p>He robado princesas a reyes agónicos. Incendié la ciudad de Trebon. He pasado la noche con Felurian y he despertado con la cordura intacta y el orgullo maltrecho.</p>
      
      <p>Me llamo Kvothe. Quizá hayas oído hablar de mí. He pisado caminos de piedra de noche, en lugares donde la gente teme caminar de día. <span class="book-highlight-yellow">Viajé, amé, perdí, confié y fui traicionado. En un rincón de mi memoria aún arde la madera de la posada Roca de Guía.</span></p>

      <p><span class="woods-tts-active" id="tts-sentence">El silencio era de tres partes. La parte más obvia era una calma hueca y resonante hecha por las cosas que faltaban.</span> Si hubiera habido viento, habría suspirado entre los árboles, habría hecho balancearse el letrero de la posada y habría empujado el silencio calle abajo como una pila de hojas secas.</p>

      <p>Si hubiera habido gente en la posada, incluso un puñado de comensales, habrían llenado el silencio con conversaciones y risas, el chasquido y el tintineo que acompaña a una taberna en las horas sombrías de la noche.</p>
    </div>

    <!-- TTS Floating HUD -->
    <div class="tts-player-bar" id="tts-hud">
      <div class="tts-ticker" id="tts-ticker-text">
        🔊 "El silencio era de tres partes. La parte más obvia era una calma hueca..."
      </div>
      <div class="tts-controls-row">
        <span style="font-size: 11px; color: var(--text-secondary);">1.2x • Álvaro Neural</span>
        <div style="display: flex; gap: 8px; align-items: center;">
          <button class="icon-btn" style="width: 32px; height: 32px;" onclick="toggleTts()">${ICONS.tune}</button>
          <button class="tts-play-btn" onclick="toggleTtsPlay()">${isTtsPlaying ? ICONS.pause : ICONS.play}</button>
        </div>
      </div>
    </div>

    <!-- Progress Slider Bar -->
    <div class="reader-slider-bar" id="reader-slider">
      <span>Pág 280</span>
      <input type="range" class="reader-slider-input" min="0" max="100" value="${currentBookProgress * 100}" oninput="updateReaderProgress(this.value)">
      <span>42%</span>
    </div>

    <!-- Bottom Bar -->
    <div class="reader-bottom-bar" id="reader-bottom">
      <button class="icon-btn" onclick="openTocSheet()">${ICONS.formatList}</button>
      <button class="icon-btn" onclick="openTypographySheet()">${ICONS.tune}</button>
      <button class="icon-btn" style="color: var(--primary-color);" onclick="toggleTts()">${ICONS.headphones}</button>
      <button class="icon-btn" onclick="openNotesSheet()">${ICONS.editNote}</button>
      <button class="icon-btn">${ICONS.search}</button>
    </div>
  `;
}

// 4. Render Stats
function renderStats() {
  const screen = document.getElementById('screen-stats');
  if (!screen || !APP_DATA) return;
  const s = APP_DATA.statistics;

  screen.innerHTML = `
    <div class="library-header" style="padding: 16px 0;">
      <div class="library-title-group">
        <h1>Estadísticas de Lectura</h1>
        <p>Tu hábito en el bosque</p>
      </div>
    </div>

    <div class="stats-goal-card">
      <div class="goal-circle">
        <div class="goal-circle-inner">78%</div>
      </div>
      <div>
        <span class="meta-label">META DIARIA</span>
        <h2 style="font-size: 24px; font-weight: 700; color: var(--text-primary);">${s.todayMinutesRead} / ${s.dailyGoalMinutes} min</h2>
        <p style="font-size: 12px; color: var(--primary-color); margin-top: 4px;">¡A 10 minutos de cumplir tu objetivo hoy!</p>
      </div>
    </div>

    <div class="metrics-3col">
      <div class="metric-card">
        <div class="metric-icon" style="background: rgba(255, 152, 0, 0.2); color: #FF9800;">🔥</div>
        <h4>${s.currentStreakDays} días</h4>
        <p>Racha actual</p>
      </div>
      <div class="metric-card">
        <div class="metric-icon" style="background: rgba(76, 175, 114, 0.2); color: var(--primary-color);">⚡</div>
        <h4>${s.averageWpm} ppm</h4>
        <p>Velocidad media</p>
      </div>
      <div class="metric-card">
        <div class="metric-icon" style="background: rgba(66, 165, 245, 0.2); color: #42A5F5;">📚</div>
        <h4>${s.totalBooksRead}</h4>
        <p>Libros leídos</p>
      </div>
    </div>

    <!-- 12-Week Heatmap Matrix (7 x 12 = 84 cells) -->
    <div class="heatmap-card">
      <h3>Actividad de Lectura (12 Semanas)</h3>
      <div class="heatmap-matrix">
        ${s.weeklyHeatmapMatrix12Weeks.flat().map(val => {
          let level = '';
          if (val > 50) level = 'l4';
          else if (val > 30) level = 'l3';
          else if (val > 15) level = 'l2';
          else if (val > 0) level = 'l1';
          return `<div class="heat-cell ${level}" title="${val} min"></div>`;
        }).join('')}
      </div>
    </div>

    <div class="synopsis-card" style="margin: 0 0 20px 0;">
      <h3>Distribución por Formato</h3>
      <div style="display: flex; gap: 16px; margin-top: 10px;">
        <span style="font-size: 13px; color: var(--primary-color); font-weight: 600;">EPUB: ${Math.round(s.formatDistribution.epub * 100)}%</span>
        <span style="font-size: 13px; color: var(--secondary-color); font-weight: 600;">PDF: ${Math.round(s.formatDistribution.pdf * 100)}%</span>
      </div>
    </div>
  `;
}

// 5. Render Semantic Graph Canvas (GraphCanvas.kt)
function renderGraph() {
  const canvas = document.getElementById('graph-canvas');
  if (!canvas || !APP_DATA) return;
  const ctx = canvas.getContext('2d');
  const d = APP_DATA.knowledgeGraph;

  canvas.width = canvas.parentElement.clientWidth;
  canvas.height = canvas.parentElement.clientHeight;

  const w = canvas.width;
  const h = canvas.height;
  ctx.clearRect(0, 0, w, h);

  // Position nodes radially
  const nodeMap = {};
  const total = d.nodes.length;
  d.nodes.forEach((n, idx) => {
    const angle = (idx / total) * Math.PI * 2;
    const dist = idx === 0 ? 0 : 130 + (idx % 2) * 40;
    nodeMap[n.id] = {
      ...n,
      x: w / 2 + Math.cos(angle) * dist,
      y: h / 2 + Math.sin(angle) * dist,
      r: Math.min(10 + Math.sqrt(n.mentionCount), 26)
    };
  });

  // Draw Edges
  d.edges.forEach(e => {
    const s = nodeMap[e.source];
    const t = nodeMap[e.target];
    if (!s || !t) return;
    ctx.beginPath();
    ctx.moveTo(s.x, s.y);
    ctx.lineTo(t.x, t.y);
    ctx.strokeStyle = 'rgba(76, 175, 114, 0.25)';
    ctx.lineWidth = e.weight;
    ctx.stroke();
  });

  // Draw Nodes
  const colors = {
    CHARACTER: '#4CAF50',
    LOCATION: '#2196F3',
    ITEM: '#FF9800',
    FACTION: '#9C27B0',
    CONCEPT: '#00BCD4'
  };

  Object.values(nodeMap).forEach(n => {
    ctx.beginPath();
    ctx.arc(n.x, n.y, n.r, 0, Math.PI * 2);
    ctx.fillStyle = colors[n.type] || '#4CAF50';
    ctx.fill();
    ctx.strokeStyle = '#fff';
    ctx.lineWidth = 1.5;
    ctx.stroke();

    // Node label
    ctx.font = '11px sans-serif';
    ctx.fillStyle = '#D4E8D4';
    ctx.textAlign = 'center';
    ctx.fillText(n.name, n.x, n.y + n.r + 14);
  });
}

// Navigation & Screen Management
function navigateTo(screenId) {
  document.querySelectorAll('.app-screen').forEach(s => s.classList.remove('active'));
  const target = document.getElementById(screenId);
  if (target) {
    target.classList.add('active');
    activeScreenId = screenId;
  }

  // Update Bottom Nav active state
  document.querySelectorAll('.nav-item').forEach(item => {
    const nav = item.getAttribute('data-nav');
    if (
      (screenId === 'screen-library' && nav === 'library') ||
      (screenId === 'screen-stats' && nav === 'stats')
    ) {
      item.classList.add('active');
    } else {
      item.classList.remove('active');
    }
  });

  // Hide bottom nav in reader and graph
  const bottomNav = document.getElementById('main-nav');
  if (bottomNav) {
    if (screenId === 'screen-reader' || screenId === 'screen-detail' || screenId === 'screen-graph') {
      bottomNav.style.transform = 'translateY(120px)';
    } else {
      bottomNav.style.transform = 'translateY(0)';
    }
  }

  if (screenId === 'screen-graph') {
    setTimeout(renderGraph, 100);
  }
}

function openDetail(bookId) {
  selectedBook = APP_DATA.books.find(b => b.id === bookId) || APP_DATA.books[0];
  renderBookDetail();
  navigateTo('screen-detail');
}

function openReader(bookId) {
  selectedBook = APP_DATA.books.find(b => b.id === bookId) || APP_DATA.books[0];
  renderReader();
  navigateTo('screen-reader');
}

// Reader Controls & Bottom Sheets
function toggleReaderControls() {
  const top = document.getElementById('reader-top');
  const bottom = document.getElementById('reader-bottom');
  const slider = document.getElementById('reader-slider');
  if (!top || !bottom) return;

  const isHidden = top.style.transform === 'translateY(-100%)';
  top.style.transform = isHidden ? 'translateY(0)' : 'translateY(-100%)';
  bottom.style.transform = isHidden ? 'translateY(0)' : 'translateY(100%)';
  if (slider) slider.style.transform = isHidden ? 'translateY(0)' : 'translateY(100%)';
}

function toggleTtsPlay() {
  isTtsPlaying = !isTtsPlaying;
  renderReader();
}

function toggleTts() {
  const hud = document.getElementById('tts-hud');
  if (hud) {
    hud.style.display = hud.style.display === 'none' ? 'flex' : 'none';
  }
}

function updateReaderProgress(val) {
  currentBookProgress = val / 100;
}

// Bottom Sheets
function openBottomSheet(htmlContent) {
  const scrim = document.getElementById('sheet-scrim');
  const content = document.getElementById('sheet-body-content');
  if (scrim && content) {
    content.innerHTML = htmlContent;
    scrim.classList.add('visible');
  }
}

function closeBottomSheet() {
  const scrim = document.getElementById('sheet-scrim');
  if (scrim) scrim.classList.remove('visible');
}

function openTypographySheet() {
  openBottomSheet(`
    <h3 style="font-family: var(--font-serif); margin-bottom: 16px;">Ajustes de Lectura</h3>
    <div style="display: flex; flex-direction: column; gap: 16px;">
      <div>
        <span class="meta-label">TEMA VISUAL</span>
        <div class="theme-selector-row" style="padding: 10px 0;">
          <div class="theme-pill-card ${currentTheme === 'HIDDEN_FOREST' ? 'active' : ''}" onclick="setTheme('HIDDEN_FOREST')">
            <div class="theme-pill-swatch" style="background: #080B08; color: #4CAF72; border: 1px solid #1E2C1E;">HF</div>
            <span>Bosque</span>
          </div>
          <div class="theme-pill-card ${currentTheme === 'DAY' ? 'active' : ''}" onclick="setTheme('DAY')">
            <div class="theme-pill-swatch" style="background: #F7F9F6; color: #2E7D47; border: 1px solid #D8E2D7;">Day</div>
            <span>Día</span>
          </div>
          <div class="theme-pill-card ${currentTheme === 'SEPIA' ? 'active' : ''}" onclick="setTheme('SEPIA')">
            <div class="theme-pill-swatch" style="background: #F4ECD8; color: #7A5230; border: 1px solid #CEBEA0;">Sep</div>
            <span>Sepia</span>
          </div>
          <div class="theme-pill-card ${currentTheme === 'AMOLED' ? 'active' : ''}" onclick="setTheme('AMOLED')">
            <div class="theme-pill-swatch" style="background: #000; color: #3DD975; border: 1px solid #1A1A1A;">OLED</div>
            <span>AMOLED</span>
          </div>
          <div class="theme-pill-card ${currentTheme === 'SUNSET' ? 'active' : ''}" onclick="setTheme('SUNSET')">
            <div class="theme-pill-swatch" style="background: #120B10; color: #E57342; border: 1px solid #2E1E2B;">Sun</div>
            <span>Sunset</span>
          </div>
        </div>
      </div>
      <div>
        <span class="meta-label">TAMAÑO DE FUENTE: 18 SP</span>
        <input type="range" class="reader-slider-input" min="12" max="36" value="18" style="width: 100%; margin-top: 8px;">
      </div>
      <div>
        <span class="meta-label">INTERLINEADO: 1.4</span>
        <input type="range" class="reader-slider-input" min="8" max="20" value="14" style="width: 100%; margin-top: 8px;">
      </div>
    </div>
  `);
}

function openTocSheet() {
  openBottomSheet(`
    <h3 style="font-family: var(--font-serif); margin-bottom: 16px;">Índice de Contenidos</h3>
    <div style="display: flex; flex-direction: column; gap: 12px;">
      <div style="padding: 8px 0; border-bottom: 1px solid var(--border-color); display: flex; justify-content: space-between;">
        <span>Prólogo: Un silencio de tres partes</span>
        <span style="color: var(--text-secondary);">Pág. 1</span>
      </div>
      <div style="padding: 8px 0; border-bottom: 1px solid var(--border-color); display: flex; justify-content: space-between;">
        <span>Capítulo 1: Un lugar para los forasteros</span>
        <span style="color: var(--text-secondary);">Pág. 12</span>
      </div>
      <div style="padding: 8px 0; border-bottom: 1px solid var(--border-color); display: flex; justify-content: space-between; font-weight: 600; color: var(--primary-color);">
        <span>Capítulo 7: De palabras y fuego</span>
        <span>Pág. 84</span>
      </div>
      <div style="padding: 8px 0; border-bottom: 1px solid var(--border-color); display: flex; justify-content: space-between;">
        <span>Capítulo 16: Hojas al viento</span>
        <span style="color: var(--text-secondary);">Pág. 142</span>
      </div>
    </div>
  `);
}

function openNotesSheet() {
  if (!APP_DATA) return;
  openBottomSheet(`
    <h3 style="font-family: var(--font-serif); margin-bottom: 16px;">Subrayados y Citas (${APP_DATA.highlights.length})</h3>
    <div style="display: flex; flex-direction: column; gap: 12px;">
      ${APP_DATA.highlights.map(h => `
        <div style="background: var(--surface-variant); padding: 12px; border-radius: var(--radius-md); border-left: 4px solid var(--hl-${h.color});">
          <p style="font-family: var(--font-serif); font-style: italic; font-size: 13px; color: var(--text-primary); margin-bottom: 6px;">"${h.text}"</p>
          <span style="font-size: 11px; color: var(--text-secondary);">${h.chapterTitle}</span>
          ${h.note ? `<div style="margin-top: 6px; font-size: 11px; color: var(--primary-color);">Nota: ${h.note}</div>` : ''}
        </div>
      `).join('')}
    </div>
  `);
}

function openSideMenu() {
  const menu = document.getElementById('side-menu');
  if (menu) menu.classList.add('open');
}

function closeSideMenu() {
  const menu = document.getElementById('side-menu');
  if (menu) menu.classList.remove('open');
}

function setTheme(themeId) {
  currentTheme = themeId;
  const root = document.querySelector('.phone-viewport');
  if (root) {
    if (themeId === 'HIDDEN_FOREST') {
      root.removeAttribute('data-theme');
    } else {
      root.setAttribute('data-theme', themeId);
    }
  }
  closeBottomSheet();
}

function setupEventListeners() {
  // Scrim click to close bottom sheets
  const scrim = document.getElementById('sheet-scrim');
  if (scrim) {
    scrim.addEventListener('click', (e) => {
      if (e.target === scrim) closeBottomSheet();
    });
  }
}

// Start on load
window.addEventListener('DOMContentLoaded', initApp);
