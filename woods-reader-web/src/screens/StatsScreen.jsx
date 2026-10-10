import { useState } from "react";
import { C, KPI_DATA, DAYS, HEATMAP_DATA, WEEKLY_PAGES, BY_BOOK_DATA, BY_BOOK_TOTAL } from "../data.js";

export default function StatsScreen() {
    const [hoverIdx, setHoverIdx] = useState(null);
    const maxPages = Math.max(...WEEKLY_PAGES);
    const iToHex = v => v === 0 ? C.border : `${C.green}${Math.round((v / 5) * 220 + 35).toString(16).padStart(2, "0")}`;

    return (
        <div style={{ background: C.bg, height: "100%", overflowY: "auto", padding: "20px 18px 24px", fontFamily: "'Segoe UI',system-ui,sans-serif" }}>
            <div style={{ marginBottom: 16 }}>
                <div style={{ fontSize: 9, color: C.textDim, letterSpacing: "0.15em", textTransform: "uppercase", marginBottom: 2 }}>Marzo 2026</div>
                <div style={{ fontSize: 23, fontWeight: 800, color: C.text, fontFamily: "Georgia,serif" }}>Estadísticas</div>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(2,1fr)", gap: 9, marginBottom: 13 }}>
                {KPI_DATA.map((k, i) => (
                    <div key={i} style={{ background: C.bgCard, border: `1px solid ${k.color}33`, borderRadius: 14, padding: "12px 14px" }}>
                        <div style={{ fontSize: 8, color: C.textDim, letterSpacing: "0.1em", textTransform: "uppercase", marginBottom: 5 }}>{k.label}</div>
                        <div style={{ fontSize: 26, fontWeight: 800, color: k.color, lineHeight: 1 }}>{k.value}</div>
                        {k.unit && <div style={{ fontSize: 10, color: C.textMid, marginTop: 2 }}>{k.unit}</div>}
                    </div>
                ))}
            </div>
            <div style={{ background: C.bgCard, border: `1px solid ${C.border}`, borderRadius: 14, padding: "12px 14px", marginBottom: 12 }}>
                <div style={{ fontSize: 9, color: C.textDim, letterSpacing: "0.1em", textTransform: "uppercase", marginBottom: 10 }}>Hábito de lectura — últimas 4 semanas</div>
                <div style={{ display: "flex", gap: 4, marginBottom: 4 }}>
                    {DAYS.map(d => <div key={d} style={{ flex: 1, textAlign: "center", fontSize: 8, color: C.textDim }}>{d}</div>)}
                </div>
                {HEATMAP_DATA.map((week, wi) => (
                    <div key={wi} style={{ display: "flex", gap: 4, marginBottom: 4 }}>
                        {week.map((val, di) => (
                            <div key={di} title={`${val * 20} páginas`} style={{ flex: 1, aspectRatio: "1", borderRadius: 3, background: iToHex(val), border: `1px solid ${C.border}` }} />
                        ))}
                    </div>
                ))}
                <div style={{ display: "flex", justifyContent: "flex-end", gap: 4, marginTop: 7, alignItems: "center" }}>
                    <span style={{ fontSize: 7, color: C.textDim }}>Menos</span>
                    {[0, 1, 2, 3, 4, 5].map(v => <div key={v} style={{ width: 9, height: 9, borderRadius: 2, background: iToHex(v) }} />)}
                    <span style={{ fontSize: 7, color: C.textDim }}>Más</span>
                </div>
            </div>
            <div style={{ background: C.bgCard, border: `1px solid ${C.border}`, borderRadius: 14, padding: "12px 14px", marginBottom: 12 }}>
                <div style={{ fontSize: 9, color: C.textDim, letterSpacing: "0.1em", textTransform: "uppercase", marginBottom: 12 }}>Páginas · semana actual</div>
                <div style={{ display: "flex", alignItems: "flex-end", gap: 6, height: 64 }}>
                    {WEEKLY_PAGES.map((v, i) => {
                        const isToday = i === 6, isHov = hoverIdx === i;
                        const bc = isToday ? C.green : isHov ? C.greenDim + "CC" : C.greenDim + "66";
                        return (
                            <div key={i} onMouseEnter={() => setHoverIdx(i)} onMouseLeave={() => setHoverIdx(null)}
                                style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", gap: 2 }}>
                                <div style={{ fontSize: 7, color: isToday ? C.green : C.textDim, minHeight: 9 }}>{v || ""}</div>
                                <div style={{ width: "100%", height: `${v ? (v / maxPages) * 100 : 4}%`, minHeight: 4, background: bc, borderRadius: "3px 3px 0 0", boxShadow: isToday ? `0 0 8px ${C.green}66` : "none", transition: "background 0.2s" }} />
                                <div style={{ fontSize: 8, color: isToday ? C.green : C.textDim }}>{DAYS[i]}</div>
                            </div>
                        );
                    })}
                </div>
            </div>
            <div style={{ background: C.bgCard, border: `1px solid ${C.border}`, borderRadius: 14, padding: "12px 14px" }}>
                <div style={{ fontSize: 9, color: C.textDim, letterSpacing: "0.1em", textTransform: "uppercase", marginBottom: 10 }}>Distribución por libro — este mes</div>
                <div style={{ display: "flex", borderRadius: 7, overflow: "hidden", height: 12, marginBottom: 12 }}>
                    {BY_BOOK_DATA.map((b, i) => <div key={i} title={`${b.title}: ${b.pages} págs`} style={{ width: `${(b.pages / BY_BOOK_TOTAL) * 100}%`, background: b.color, transition: "width 0.6s" }} />)}
                </div>
                {BY_BOOK_DATA.map((b, i) => (
                    <div key={i} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 7 }}>
                        <div style={{ display: "flex", alignItems: "center", gap: 7 }}>
                            <div style={{ width: 8, height: 8, borderRadius: 2, background: b.color }} />
                            <span style={{ fontSize: 11, color: C.text }}>{b.title}</span>
                        </div>
                        <span style={{ fontSize: 11, color: C.textMid, fontWeight: 600 }}>
                            {b.pages} <span style={{ color: C.textDim, fontWeight: 400, fontSize: 9 }}>({Math.round(b.pages / BY_BOOK_TOTAL * 100)}%)</span>
                        </span>
                    </div>
                ))}
            </div>
        </div>
    );
}
