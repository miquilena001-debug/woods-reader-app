import { useState, useMemo } from "react";
import { C, SEARCH_CORPUS } from "../data.js";
import { Badge } from "../components/Shared.jsx";

export default function SearchScreen() {
    const [query, setQuery] = useState("soledad");
    const [scope, setScope] = useState("book");

    const results = useMemo(() => {
        if (!query.trim()) return [];
        const q = query.toLowerCase();
        return SEARCH_CORPUS.filter(r => {
            const inText = (r.pre + r.match + r.post).toLowerCase().includes(q);
            return scope === "book" ? inText && r.book === "Cien Años de Soledad" : inText;
        });
    }, [query, scope]);

    const hl = (text) => {
        if (!query.trim()) return text;
        const re = new RegExp(`(${query.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")})`, "gi");
        return text.split(re).map((p, i) =>
            re.test(p)
                ? <span key={i} style={{ color: C.green, background: C.green + "22", fontWeight: 700, padding: "0 2px", borderRadius: 3 }}>{p}</span>
                : p
        );
    };

    return (
        <div style={{ background: C.bg, height: "100%", overflowY: "auto", padding: "20px 18px 24px", fontFamily: "'Segoe UI',system-ui,sans-serif" }}>
            <div style={{ marginBottom: 16 }}>
                <div style={{ fontSize: 9, color: C.textDim, letterSpacing: "0.15em", textTransform: "uppercase", marginBottom: 2 }}>Texto completo</div>
                <div style={{ fontSize: 23, fontWeight: 800, color: C.text, fontFamily: "Georgia,serif" }}>Búsqueda</div>
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: 8, background: C.bgCard, border: `1px solid ${C.borderMid}`, borderRadius: 14, padding: "11px 15px", marginBottom: 12, boxShadow: `0 0 0 1px ${C.green}11` }}>
                <span style={{ fontSize: 15, opacity: 0.45 }}>🔍</span>
                <input value={query} onChange={e => setQuery(e.target.value)} placeholder="Buscar en el libro…"
                    style={{ flex: 1, background: "none", border: "none", outline: "none", color: C.text, fontSize: 14, fontFamily: "Georgia,serif" }} />
                {query && <span onClick={() => setQuery("")} style={{ cursor: "pointer", color: C.textDim, fontSize: 12 }}>✕</span>}
            </div>
            <div style={{ display: "flex", gap: 7, marginBottom: 14 }}>
                {[{ k: "book", label: "📖 Este libro" }, { k: "library", label: "📚 Biblioteca" }].map(s => (
                    <div key={s.k} onClick={() => setScope(s.k)} style={{
                        padding: "5px 12px", borderRadius: 9, cursor: "pointer", fontSize: 11, fontWeight: 600,
                        background: scope === s.k ? C.green + "33" : C.bgCard, border: `1px solid ${scope === s.k ? C.green : C.border}`,
                        color: scope === s.k ? C.green : C.textMid, transition: "all 0.2s"
                    }}>{s.label}</div>
                ))}
            </div>
            {query.trim() && (
                <div style={{ fontSize: 9, color: C.textDim, letterSpacing: "0.1em", textTransform: "uppercase", marginBottom: 12 }}>
                    {results.length} resultado{results.length !== 1 ? "s" : ""} · "{query}"
                </div>
            )}
            <div style={{ display: "flex", flexDirection: "column", gap: 9 }}>
                {results.map((r, i) => (
                    <div key={i} style={{ background: C.bgCard, border: `1px solid ${C.border}`, borderRadius: 13, padding: "11px 14px", cursor: "pointer" }}>
                        <p style={{ fontFamily: "Georgia,serif", fontSize: 12, lineHeight: 1.75, color: C.textMid, margin: "0 0 9px" }}>
                            {r.pre}{hl(r.match)}{r.post}
                        </p>
                        <div style={{ display: "flex", gap: 7, alignItems: "center", flexWrap: "wrap" }}>
                            <Badge color={C.blue}>{r.book.split(" ")[0]}</Badge>
                            <Badge color={C.textDim}>{r.chapter}</Badge>
                            <span style={{ fontSize: 9, color: C.textDim }}>página {r.page}</span>
                        </div>
                    </div>
                ))}
            </div>
            {!query.trim() && <div style={{ textAlign: "center", padding: "40px 0", color: C.textDim }}><div style={{ fontSize: 28, marginBottom: 10 }}>🔍</div><div style={{ fontSize: 13 }}>Escribe para buscar en el texto completo</div></div>}
            {query.trim() && results.length === 0 && <div style={{ textAlign: "center", padding: "40px 0", color: C.textDim }}><div style={{ fontSize: 28, marginBottom: 10 }}>🔎</div><div style={{ fontSize: 13 }}>Sin resultados para "{query}"</div></div>}
        </div>
    );
}
