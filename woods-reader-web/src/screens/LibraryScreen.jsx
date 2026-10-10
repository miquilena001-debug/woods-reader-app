import { useState } from "react";
import { C, BOOKS } from "../data.js";
import { Badge, ProgressBar } from "./Shared.jsx";

function BookCover({ book, selected, onSelect, onOpen }) {
    const [hov, setHov] = useState(false);
    return (
        <div onClick={onSelect} onDoubleClick={onOpen}
            onMouseEnter={() => setHov(true)} onMouseLeave={() => setHov(false)}
            style={{
                cursor: "pointer", borderRadius: 12, overflow: "hidden",
                border: `1px solid ${selected ? book.col : hov ? C.borderMid : C.border}`,
                background: C.bgCard, transition: "all 0.25s ease",
                transform: hov ? "translateY(-3px)" : "none",
                boxShadow: selected ? `0 0 18px ${book.col}44` : hov ? "0 6px 20px #00000066" : "none",
            }}>
            <div style={{
                height: 108,
                background: `linear-gradient(145deg,${book.col}22,${book.col}08,${C.bg})`,
                display: "flex", alignItems: "center", justifyContent: "center",
                position: "relative", overflow: "hidden",
            }}>
                {[...Array(5)].map((_, i) => (
                    <div key={i} style={{ position: "absolute", left: `${15 + i * 17}%`, top: 0, bottom: 0, width: 1, background: book.col + "18" }} />
                ))}
                <div style={{
                    width: 44, height: 58, borderRadius: 6, border: `2px solid ${book.col}66`,
                    background: `linear-gradient(160deg,${book.col}33,${C.bgCard})`,
                    display: "flex", alignItems: "center", justifyContent: "center", fontSize: 18,
                }}>📖</div>
                {book.status === "done" && <div style={{ position: "absolute", top: 5, right: 5, background: C.green + "33", border: `1px solid ${C.green}66`, borderRadius: 5, padding: "1px 5px", fontSize: 8, color: C.green, fontWeight: 700 }}>✓</div>}
                {book.status === "new" && <div style={{ position: "absolute", top: 5, left: 5, background: C.blue + "33", border: `1px solid ${C.blue}66`, borderRadius: 5, padding: "1px 5px", fontSize: 7, color: C.blue, fontWeight: 700 }}>NEW</div>}
            </div>
            <div style={{ padding: "8px 10px 10px" }}>
                <div style={{
                    fontSize: 11, fontWeight: 700, color: C.text, marginBottom: 2, lineHeight: 1.3, fontFamily: "Georgia,serif",
                    overflow: "hidden", display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical"
                }}>{book.t}</div>
                <div style={{ fontSize: 9, color: C.textDim, marginBottom: 6 }}>{book.a}</div>
                <ProgressBar value={book.p} color={book.col} />
                <div style={{ fontSize: 8, color: C.textDim, marginTop: 2, textAlign: "right" }}>{book.p}%</div>
            </div>
        </div>
    );
}

export default function LibraryScreen({ onOpenBook }) {
    const [selected, setSelected] = useState(1);
    const [query, setQuery] = useState("");
    const filtered = BOOKS.filter(b =>
        b.t.toLowerCase().includes(query.toLowerCase()) ||
        b.a.toLowerCase().includes(query.toLowerCase())
    );
    return (
        <div style={{ background: C.bg, height: "100%", overflowY: "auto", padding: "20px 18px 16px", fontFamily: "'Segoe UI',system-ui,sans-serif" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", marginBottom: 14 }}>
                <div>
                    <div style={{ fontSize: 9, color: C.textDim, letterSpacing: "0.15em", textTransform: "uppercase", marginBottom: 2 }}>Mi Colección</div>
                    <div style={{ fontSize: 23, fontWeight: 800, color: C.text, fontFamily: "Georgia,serif" }}>Biblioteca</div>
                </div>
                <div style={{ display: "flex", gap: 5 }}>
                    <Badge color={C.green}>{BOOKS.filter(b => b.status === "reading").length} leyendo</Badge>
                    <Badge color={C.amber}>{BOOKS.filter(b => b.status === "done").length} leídos</Badge>
                </div>
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: 8, background: C.bgCard, border: `1px solid ${C.border}`, borderRadius: 13, padding: "9px 14px", marginBottom: 14 }}>
                <span style={{ fontSize: 13, opacity: 0.4 }}>🔍</span>
                <input value={query} onChange={e => setQuery(e.target.value)} placeholder="Buscar título, autor…"
                    style={{ background: "none", border: "none", outline: "none", color: C.text, fontSize: 13, flex: 1, fontFamily: "inherit" }} />
                {query && <span onClick={() => setQuery("")} style={{ cursor: "pointer", color: C.textDim, fontSize: 11 }}>✕</span>}
            </div>
            <div style={{
                background: `linear-gradient(135deg,${C.bgElevated},${C.bgCard})`,
                border: `1px solid ${C.borderMid}`, borderRadius: 15, padding: "12px 14px",
                display: "flex", gap: 12, alignItems: "center", marginBottom: 16,
            }}>
                <div style={{
                    width: 40, height: 52, borderRadius: 7, flexShrink: 0,
                    background: `linear-gradient(145deg,${BOOKS[0].col}44,${C.bgCard})`, border: `1px solid ${BOOKS[0].col}55`,
                    display: "flex", alignItems: "center", justifyContent: "center"
                }}>📖</div>
                <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 9, color: C.textDim, letterSpacing: "0.12em", textTransform: "uppercase", marginBottom: 3 }}>Continuar leyendo</div>
                    <div style={{ fontSize: 14, fontWeight: 700, color: C.text, fontFamily: "Georgia,serif", marginBottom: 2 }}>{BOOKS[0].t}</div>
                    <div style={{ fontSize: 10, color: C.textMid, marginBottom: 6 }}>Capítulo 12 · {BOOKS[0].p}% completado</div>
                    <ProgressBar value={BOOKS[0].p} color={BOOKS[0].col} height={4} />
                </div>
                <div onClick={() => onOpenBook(BOOKS[0])} style={{
                    background: C.green + "22", border: `1px solid ${C.green}55`, borderRadius: 11,
                    padding: "7px 13px", color: C.green, fontSize: 12, fontWeight: 700, cursor: "pointer",
                    whiteSpace: "nowrap", boxShadow: `0 0 12px ${C.green}22`,
                }}>▶ Leer</div>
            </div>
            <div style={{ fontSize: 9, color: C.textDim, letterSpacing: "0.12em", textTransform: "uppercase", marginBottom: 10 }}>
                Todos los libros — {filtered.length}
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(3,1fr)", gap: 9 }}>
                {filtered.map(b => (
                    <BookCover key={b.id} book={b} selected={selected === b.id}
                        onSelect={() => setSelected(b.id)} onOpen={() => onOpenBook(b)} />
                ))}
            </div>
            {filtered.length === 0 && (
                <div style={{ textAlign: "center", padding: "40px 0", color: C.textDim }}>
                    <div style={{ fontSize: 28, marginBottom: 10 }}>📭</div>
                    <div style={{ fontSize: 13 }}>No se encontraron libros</div>
                </div>
            )}
        </div>
    );
}
