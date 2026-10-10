import { useState, useEffect } from "react";
import { C, EXCERPT, READER_THEMES, TTS_WORDS, WAVEFORM, HL_COLORS, HIGHLIGHTS_DATA, BOOKMARKS_DATA } from "../data.js";
import { Badge, ProgressBar } from "../components/Shared.jsx";

/* ── Read View ──────────────────────────────────────────────────── */
function ReadView() {
    const [themeKey, setThemeKey] = useState("forest");
    const [fontSize, setFontSize] = useState(17);
    const [lineHeight, setLineHeight] = useState(1.85);
    const [showBar, setShowBar] = useState(false);
    const T = READER_THEMES[themeKey];

    const renderBlock = (block, i) => {
        if (block.type === "chapter") return (
            <div key={i} style={{ textAlign: "center", margin: "8px 0 24px" }}>
                <div style={{ fontSize: 9, color: T.textMid, letterSpacing: "0.25em", textTransform: "uppercase", marginBottom: 8 }}>✦ ✦ ✦</div>
                <div style={{ fontFamily: "Georgia,serif", fontSize: 20, fontWeight: 700, color: T.text }}>{block.text}</div>
                <div style={{ width: 32, height: 2, background: T.accent, margin: "10px auto 0", borderRadius: 2, opacity: 0.5 }} />
            </div>
        );
        if (block.type === "highlight") return (
            <p key={i} style={{
                fontFamily: "Georgia,serif", fontSize, lineHeight, color: T.text, margin: "0 0 16px",
                borderLeft: `3px solid ${T.accent}`, background: T.accent + "0A", padding: "8px 14px", borderRadius: "0 8px 8px 0"
            }}>{block.text}</p>
        );
        if (block.type === "dialogue") return (
            <p key={i} style={{ fontFamily: "Georgia,serif", fontSize, lineHeight, color: T.textMid, margin: "0 0 16px", paddingLeft: 18, fontStyle: "italic" }}>{block.text}</p>
        );
        return <p key={i} style={{ fontFamily: "Georgia,serif", fontSize, lineHeight, color: T.text, margin: "0 0 16px", textAlign: "justify" }}>{block.text}</p>;
    };

    return (
        <div style={{ background: T.bg, height: "100%", display: "flex", flexDirection: "column", transition: "background 0.4s" }}>
            <div style={{ padding: "7px 14px", display: "flex", justifyContent: "flex-end", background: T.bg, borderBottom: `1px solid ${T.accent}18`, flexShrink: 0 }}>
                <div onClick={() => setShowBar(!showBar)} style={{ padding: "4px 10px", borderRadius: 8, cursor: "pointer", background: `${T.accent}22`, border: `1px solid ${T.accent}44`, fontSize: 12, color: T.text, fontWeight: 600, userSelect: "none" }}>Aa</div>
            </div>
            {showBar && (
                <div style={{ padding: "8px 14px", background: T.bg, borderBottom: `1px solid ${T.accent}18`, display: "flex", gap: 7, alignItems: "center", flexWrap: "wrap", flexShrink: 0 }}>
                    <span style={{ fontSize: 9, color: T.textMid }}>Tamaño:</span>
                    {[13, 15, 17, 19, 22].map(s => (
                        <div key={s} onClick={() => setFontSize(s)} style={{ width: 24, height: 24, borderRadius: 7, cursor: "pointer", background: fontSize === s ? `${T.accent}33` : "transparent", border: `1px solid ${fontSize === s ? T.accent : T.accent + "33"}`, display: "flex", alignItems: "center", justifyContent: "center", fontSize: 9, color: T.text, fontWeight: fontSize === s ? 700 : 400 }}>{s}</div>
                    ))}
                    <div style={{ width: 1, height: 16, background: T.accent + "33" }} />
                    <span style={{ fontSize: 9, color: T.textMid }}>Tema:</span>
                    {Object.entries(READER_THEMES).map(([k, v]) => (
                        <div key={k} onClick={() => setThemeKey(k)} style={{ padding: "2px 8px", borderRadius: 6, cursor: "pointer", background: themeKey === k ? `${T.accent}33` : "transparent", border: `1px solid ${themeKey === k ? T.accent : T.accent + "22"}`, fontSize: 9, color: T.text }}>{v.label}</div>
                    ))}
                </div>
            )}
            <div style={{ flex: 1, overflowY: "auto", padding: "20px 22px 60px" }}>
                {EXCERPT.map(renderBlock)}
            </div>
            <div style={{ padding: "8px 16px 12px", background: T.bg, borderTop: `1px solid ${T.accent}15`, flexShrink: 0 }}>
                <div style={{ background: `${T.accent}22`, borderRadius: 99, height: 3, overflow: "hidden", marginBottom: 5 }}>
                    <div style={{ width: "73%", background: T.accent, height: 3, borderRadius: 99, boxShadow: `0 0 8px ${T.accent}88` }} />
                </div>
                <div style={{ display: "flex", justifyContent: "space-between" }}>
                    <span style={{ fontSize: 9, color: T.textMid }}>Página 344 / 471</span>
                    <span style={{ fontSize: 9, color: T.textMid }}>~2h 18min restantes</span>
                </div>
            </div>
        </div>
    );
}

/* ── TTS View ───────────────────────────────────────────────────── */
function TtsView({ book }) {
    const [playing, setPlaying] = useState(false);
    const [speed, setSpeed] = useState(1.0);
    const [wordIdx, setWordIdx] = useState(0);
    const [progress, setProgress] = useState(38);

    useEffect(() => {
        if (!playing) return;
        const id = setInterval(() => {
            setWordIdx(w => (w + 1) % TTS_WORDS.length);
            setProgress(p => Math.min(270, p + 1));
        }, 540 / speed);
        return () => clearInterval(id);
    }, [playing, speed]);

    const pct = Math.round((progress / 270) * 100);
    const fmt = s => `${String(Math.floor(s / 60)).padStart(2, "0")}:${String(s % 60).padStart(2, "0")}`;
    const Btn = ({ icon, size = "sm", onClick, active = false }) => {
        const dim = size === "lg" ? 52 : 38;
        return (
            <div onClick={onClick} style={{
                width: dim, height: dim, borderRadius: size === "lg" ? 16 : 11, cursor: "pointer",
                background: active ? C.green : C.bgPanel, border: `1px solid ${active ? C.green : C.borderMid}`,
                display: "flex", alignItems: "center", justifyContent: "center",
                fontSize: size === "lg" ? 18 : 12, color: active ? C.bg : C.text,
                boxShadow: active ? `0 0 18px ${C.green}44` : "none", transition: "all 0.2s", userSelect: "none"
            }}>{icon}</div>
        );
    };

    return (
        <div style={{ background: C.bg, height: "100%", overflowY: "auto", padding: "16px 17px 24px", fontFamily: "'Segoe UI',system-ui,sans-serif" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 14 }}>
                <div style={{ width: 38, height: 50, borderRadius: 7, flexShrink: 0, background: `linear-gradient(145deg,${C.green}44,${C.bgCard})`, border: `1px solid ${C.green}55`, display: "flex", alignItems: "center", justifyContent: "center", fontSize: 18 }}>📖</div>
                <div>
                    <div style={{ fontSize: 8, color: C.textDim, letterSpacing: "0.12em", textTransform: "uppercase", marginBottom: 2 }}>Leyendo en voz alta</div>
                    <div style={{ fontSize: 14, fontWeight: 700, color: C.text, fontFamily: "Georgia,serif" }}>{book.t}</div>
                    <div style={{ fontSize: 10, color: C.textMid }}>Capítulo XII · {book.a}</div>
                </div>
            </div>
            <div style={{ background: C.bgCard, border: `1px solid ${C.border}`, borderRadius: 15, padding: "13px 15px", marginBottom: 13 }}>
                <div style={{ fontSize: 8, color: C.textDim, letterSpacing: "0.12em", textTransform: "uppercase", marginBottom: 9 }}>Texto actual</div>
                <div style={{ fontFamily: "Georgia,serif", fontSize: 14, lineHeight: 1.9, color: C.text }}>
                    {TTS_WORDS.map((w, i) => (
                        <span key={i} style={{ color: i === wordIdx ? C.green : C.text, background: i === wordIdx ? C.greenGlow : "transparent", fontWeight: i === wordIdx ? 700 : 400, padding: "1px 3px", borderRadius: 4, transition: "all 0.12s" }}>{w} </span>
                    ))}
                    <span style={{ color: C.textMid }}> … aquella tarde remota en que su padre lo llevó.</span>
                </div>
            </div>
            <div style={{ background: C.bgCard, border: `1px solid ${C.border}`, borderRadius: 15, padding: "12px 15px", marginBottom: 13 }}>
                <div style={{ display: "flex", alignItems: "flex-end", gap: 2, height: 46, marginBottom: 9 }}>
                    {WAVEFORM.map((h, i) => {
                        const played = i / WAVEFORM.length < pct / 100;
                        return <div key={i} style={{ flex: 1, borderRadius: 2, height: `${h * 100}%`, background: played ? C.green : C.border, opacity: played ? 0.8 : 0.35, transition: "background 0.3s" }} />;
                    })}
                </div>
                <div style={{ display: "flex", justifyContent: "space-between", fontSize: 9, color: C.textDim }}>
                    <span>{fmt(progress)}</span><span>{fmt(270)}</span>
                </div>
            </div>
            <div style={{ display: "flex", justifyContent: "center", gap: 5, marginBottom: 14 }}>
                {[0.5, 0.75, 1.0, 1.25, 1.5, 2.0].map(s => (
                    <div key={s} onClick={() => setSpeed(s)} style={{
                        padding: "4px 9px", borderRadius: 8, cursor: "pointer", fontSize: 11, fontWeight: 700,
                        background: speed === s ? C.green : C.bgPanel, color: speed === s ? C.bg : C.textMid,
                        border: `1px solid ${speed === s ? C.green : C.border}`, transition: "all 0.2s"
                    }}>{s}×</div>
                ))}
            </div>
            <div style={{ background: `linear-gradient(135deg,${C.bgElevated},${C.bgCard})`, border: `1px solid ${C.borderMid}`, borderRadius: 17, padding: "17px 15px" }}>
                <div style={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 10 }}>
                    <Btn icon="⏮" onClick={() => setProgress(0)} />
                    <Btn icon="−10" onClick={() => setProgress(p => Math.max(0, p - 10))} />
                    <Btn icon={playing ? "⏸" : "▶"} size="lg" active onClick={() => setPlaying(p => !p)} />
                    <Btn icon="+10" onClick={() => setProgress(p => Math.min(270, p + 10))} />
                    <Btn icon="⏭" onClick={() => setProgress(270)} />
                </div>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 13, flexWrap: "wrap", gap: 6 }}>
                    <div style={{ display: "flex", alignItems: "center", gap: 5 }}>
                        <span style={{ fontSize: 10, color: C.textDim }}>🌙 Apagar en</span>
                        {["15m", "30m", "60m"].map(t => (
                            <div key={t} style={{ background: C.bgPanel, border: `1px solid ${C.border}`, borderRadius: 7, padding: "2px 8px", fontSize: 9, color: C.textMid, cursor: "pointer" }}>{t}</div>
                        ))}
                    </div>
                    <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                        <span style={{ fontSize: 10, color: C.textDim }}>🎤</span>
                        <div style={{ background: C.bgPanel, border: `1px solid ${C.border}`, borderRadius: 7, padding: "2px 10px", fontSize: 9, color: C.textMid, cursor: "pointer" }}>davefx ES ▾</div>
                    </div>
                </div>
            </div>
        </div>
    );
}

/* ── Notes View ─────────────────────────────────────────────────── */
function NotesView() {
    const [tab, setTab] = useState("highlights");
    const [highlights, setHighlights] = useState(HIGHLIGHTS_DATA);
    const updateColor = (idx, color) => setHighlights(prev => prev.map((h, i) => i === idx ? { ...h, color } : h));

    return (
        <div style={{ background: C.bg, height: "100%", overflowY: "auto", padding: "16px 17px 24px", fontFamily: "'Segoe UI',system-ui,sans-serif" }}>
            <div style={{ display: "flex", gap: 7, marginBottom: 14, alignItems: "center" }}>
                {[{ k: "highlights", label: `🖊 Marcados (${highlights.length})` }, { k: "bookmarks", label: `🔖 Marcadores (${BOOKMARKS_DATA.length})` }].map(t => (
                    <div key={t.k} onClick={() => setTab(t.k)} style={{ padding: "5px 12px", borderRadius: 9, cursor: "pointer", fontSize: 11, fontWeight: 600, background: tab === t.k ? C.green + "33" : C.bgCard, border: `1px solid ${tab === t.k ? C.green : C.border}`, color: tab === t.k ? C.green : C.textMid, transition: "all 0.2s" }}>{t.label}</div>
                ))}
                <div style={{ marginLeft: "auto", padding: "5px 11px", borderRadius: 9, cursor: "pointer", fontSize: 11, background: C.bgCard, border: `1px solid ${C.border}`, color: C.textMid }}>↗ Exportar</div>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: 9 }}>
                {tab === "highlights" && highlights.map((h, i) => (
                    <div key={i} style={{ background: C.bgCard, border: `1px solid ${C.border}`, borderRadius: 13, padding: "11px 13px", borderLeft: `4px solid ${h.color}` }}>
                        <p style={{ fontFamily: "Georgia,serif", fontSize: 12, lineHeight: 1.7, color: C.text, fontStyle: "italic", margin: "0 0 9px" }}>"{h.text}"</p>
                        {h.note && <div style={{ fontSize: 10, color: C.textMid, background: C.bgPanel, borderRadius: 7, padding: "3px 8px", marginBottom: 8, display: "inline-flex", alignItems: "center", gap: 5 }}>📝 {h.note}</div>}
                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                            <div style={{ display: "flex", gap: 6, alignItems: "center" }}>
                                <Badge color={HL_COLORS.find(c => c.hex === h.color)?.badge || C.green}>{h.chapter}</Badge>
                                <span style={{ fontSize: 9, color: C.textDim }}>p. {h.page}</span>
                            </div>
                            <div style={{ display: "flex", gap: 5 }}>
                                {HL_COLORS.map(c => (
                                    <div key={c.hex} onClick={() => updateColor(i, c.hex)} style={{ width: 12, height: 12, borderRadius: "50%", background: c.hex, cursor: "pointer", border: h.color === c.hex ? "2px solid #fff" : "2px solid transparent", transition: "border-color 0.15s" }} />
                                ))}
                            </div>
                        </div>
                    </div>
                ))}
                {tab === "bookmarks" && BOOKMARKS_DATA.map((bm, i) => (
                    <div key={i} style={{ background: C.bgCard, border: `1px solid ${C.border}`, borderRadius: 13, padding: "11px 13px", display: "flex", alignItems: "center", gap: 10, cursor: "pointer" }}>
                        <div style={{ fontSize: 20 }}>{bm.auto ? "📌" : "🔖"}</div>
                        <div style={{ flex: 1 }}>
                            <div style={{ fontSize: 12, fontWeight: 700, color: C.text }}>{bm.label}</div>
                            <div style={{ fontSize: 10, color: C.textMid }}>{bm.chapter} · página {bm.page}</div>
                        </div>
                        {bm.auto && <Badge color={C.amber}>Auto</Badge>}
                        <div style={{ fontSize: 12, color: C.textDim }}>▶</div>
                    </div>
                ))}
            </div>
        </div>
    );
}

/* ── Reader Screen ──────────────────────────────────────────────── */
export default function ReaderScreen({ book, onBack, tab, setTab }) {
    const TABS = [
        { id: "read", icon: "📖", label: "Texto" },
        { id: "tts", icon: "🔊", label: "Audio" },
        { id: "notes", icon: "🖊", label: "Notas" },
    ];
    return (
        <div style={{ height: "100%", display: "flex", flexDirection: "column", background: C.bg, fontFamily: "'Segoe UI',system-ui,sans-serif" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "10px 16px", borderBottom: `1px solid ${C.border}`, background: C.bgCard, flexShrink: 0 }}>
                <div onClick={onBack} style={{ fontSize: 12, color: C.textMid, cursor: "pointer", userSelect: "none" }}>← Biblioteca</div>
                <div style={{ fontSize: 11, color: C.text, fontWeight: 600, fontFamily: "Georgia,serif", maxWidth: 160, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{book.t}</div>
                <div style={{ fontSize: 10, color: C.textMid }}>Cap. 12</div>
            </div>
            <div style={{ display: "flex", background: C.bgCard, borderBottom: `1px solid ${C.border}`, flexShrink: 0 }}>
                {TABS.map(t => (
                    <div key={t.id} onClick={() => setTab(t.id)} style={{ flex: 1, padding: "7px 4px", textAlign: "center", cursor: "pointer", borderBottom: `2px solid ${tab === t.id ? C.green : "transparent"}`, transition: "border-color 0.2s" }}>
                        <div style={{ fontSize: 15 }}>{t.icon}</div>
                        <div style={{ fontSize: 9, color: tab === t.id ? C.green : C.textDim, fontWeight: tab === t.id ? 700 : 400, marginTop: 1 }}>{t.label}</div>
                    </div>
                ))}
            </div>
            <div style={{ flex: 1, overflow: "hidden" }}>
                {tab === "read" && <ReadView book={book} />}
                {tab === "tts" && <TtsView book={book} />}
                {tab === "notes" && <NotesView book={book} />}
            </div>
        </div>
    );
}
