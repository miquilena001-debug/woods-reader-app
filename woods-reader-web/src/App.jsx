import { useState } from "react";
import { C, BOOKS } from "./data.js";
import LibraryScreen from "./screens/LibraryScreen.jsx";
import ReaderScreen from "./screens/ReaderScreen.jsx";
import SearchScreen from "./screens/SearchScreen.jsx";
import StatsScreen from "./screens/StatsScreen.jsx";
import SettingsScreen from "./screens/SettingsScreen.jsx";

const NAV = [
  { id: "library", icon: "📚", label: "Biblioteca" },
  { id: "reader", icon: "📖", label: "Leer" },
  { id: "search", icon: "🔍", label: "Buscar" },
  { id: "stats", icon: "📊", label: "Stats" },
  { id: "settings", icon: "⚙️", label: "Ajustes" },
];

export default function App() {
  const [screen, setScreen] = useState("library");
  const [selectedBook, setSelectedBook] = useState(BOOKS[0]);
  const [readerTab, setReaderTab] = useState("read");

  const openBook = (book) => {
    setSelectedBook(book);
    setReaderTab("read");
    setScreen("reader");
  };

  const renderScreen = () => {
    switch (screen) {
      case "library": return <LibraryScreen onOpenBook={openBook} />;
      case "reader": return <ReaderScreen book={selectedBook} onBack={() => setScreen("library")} tab={readerTab} setTab={setReaderTab} />;
      case "search": return <SearchScreen />;
      case "stats": return <StatsScreen />;
      case "settings": return <SettingsScreen />;
      default: return null;
    }
  };

  return (
    <div style={{
      minHeight: "100vh", background: "#040604",
      display: "flex", alignItems: "center", justifyContent: "center",
      padding: "16px", fontFamily: "'Segoe UI',system-ui,sans-serif",
      backgroundImage: "radial-gradient(ellipse 60% 50% at 50% 0%, #0D1A0D 0%, #040604 70%)",
    }}>
      {/* Phone frame */}
      <div style={{
        width: 390, height: 812,
        background: C.bg, borderRadius: 48,
        border: "2px solid #253525",
        boxShadow: ["0 0 0 1px #1A2B1A", "0 0 80px #00000099", "0 32px 80px #00000066", `inset 0 0 0 1px ${C.bgCard}`].join(", "),
        overflow: "hidden", display: "flex", flexDirection: "column", position: "relative",
      }}>
        {/* Status bar */}
        <div style={{ height: 44, background: C.bgCard, flexShrink: 0, display: "flex", justifyContent: "space-between", alignItems: "center", padding: "0 24px", borderBottom: `1px solid ${C.border}` }}>
          <span style={{ fontSize: 13, fontWeight: 700, color: C.text }}>9:41</span>
          <div style={{ width: 118, height: 34, borderRadius: 20, background: C.bg, display: "flex", alignItems: "center", justifyContent: "center", border: `1px solid ${C.border}` }}>
            <div style={{ width: 8, height: 8, borderRadius: "50%", background: C.green, boxShadow: `0 0 6px ${C.green}` }} />
          </div>
          <div style={{ display: "flex", gap: 5, alignItems: "center" }}>
            <span style={{ fontSize: 11, color: C.textMid }}>●●●</span>
            <span style={{ fontSize: 10, color: C.textMid }}>🔋</span>
          </div>
        </div>

        {/* Main content */}
        <div style={{ flex: 1, overflow: "hidden" }}>
          {renderScreen()}
        </div>

        {/* Bottom nav */}
        <div style={{ flexShrink: 0, background: C.bgCard, borderTop: `1px solid ${C.border}`, display: "flex" }}>
          {NAV.map(n => {
            const active = screen === n.id;
            return (
              <div key={n.id} onClick={() => setScreen(n.id)} style={{ flex: 1, paddingTop: 10, paddingBottom: 8, display: "flex", flexDirection: "column", alignItems: "center", gap: 3, cursor: "pointer", position: "relative", userSelect: "none" }}>
                {active && <div style={{ position: "absolute", top: 0, left: "50%", transform: "translateX(-50%)", width: 28, height: 2, background: C.green, borderRadius: "0 0 2px 2px", boxShadow: `0 0 8px ${C.green}88` }} />}
                <span style={{ fontSize: 18, filter: active ? "none" : "grayscale(1) opacity(0.45)" }}>{n.icon}</span>
                <span style={{ fontSize: 8, fontWeight: active ? 700 : 400, color: active ? C.green : C.textDim, letterSpacing: "0.04em" }}>{n.label}</span>
              </div>
            );
          })}
        </div>

        {/* Home indicator */}
        <div style={{ height: 20, background: C.bgCard, flexShrink: 0, display: "flex", alignItems: "center", justifyContent: "center" }}>
          <div style={{ width: 120, height: 4, borderRadius: 2, background: C.border }} />
        </div>
      </div>
    </div>
  );
}
