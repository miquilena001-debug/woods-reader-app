import { useState } from "react";
import { C, SETTINGS_THEMES, FONTS } from "../data.js";
import { Badge, Toggle } from "../components/Shared.jsx";

const Sec = ({ title, children }) => (
    <div style={{ background: C.bgCard, border: `1px solid ${C.border}`, borderRadius: 14, padding: "12px 14px", marginBottom: 11 }}>
        <div style={{ fontSize: 11, fontWeight: 700, color: C.text, marginBottom: 12 }}>{title}</div>
        {children}
    </div>
);

export default function SettingsScreen() {
    const [activeTheme, setActiveTheme] = useState("forest");
    const [activeFont, setActiveFont] = useState("literata");
    const [fontSize, setFontSize] = useState(17);
    const [aiEnabled, setAiEnabled] = useState(true);
    const [notifs, setNotifs] = useState(true);
    const [ttsAuto, setTtsAuto] = useState(false);
    const [analytics, setAnalytics] = useState(true);
    const currentFont = FONTS.find(f => f.key === activeFont);

    return (
        <div style={{ background: C.bg, height: "100%", overflowY: "auto", padding: "20px 18px 24px", fontFamily: "'Segoe UI',system-ui,sans-serif" }}>
            <div style={{ marginBottom: 18 }}>
                <div style={{ fontSize: 9, color: C.textDim, letterSpacing: "0.15em", textTransform: "uppercase", marginBottom: 2 }}>Personalización</div>
                <div style={{ fontSize: 23, fontWeight: 800, color: C.text, fontFamily: "Georgia,serif" }}>Ajustes</div>
            </div>
            <Sec title="🎨 Tema visual">
                <div style={{ display: "flex", gap: 7 }}>
                    {SETTINGS_THEMES.map(t => (
                        <div key={t.key} onClick={() => setActiveTheme(t.key)} title={t.name} style={{ flex: 1, cursor: "pointer", border: `2px solid ${activeTheme === t.key ? C.green : C.border}`, borderRadius: 9, overflow: "hidden", boxShadow: activeTheme === t.key ? `0 0 8px ${C.green}33` : "none", transition: "all 0.2s" }}>
                            <div style={{ height: 26, background: t.bg, display: "flex", alignItems: "center", justifyContent: "center" }}>
                                <div style={{ width: 9, height: 9, borderRadius: 3, background: t.accent, boxShadow: `0 0 5px ${t.accent}` }} />
                            </div>
                            <div style={{ background: t.bg, padding: "2px 0", textAlign: "center", fontSize: 7, color: "#888", borderTop: `1px solid ${t.accent}33` }}>{t.name}</div>
                        </div>
                    ))}
                </div>
            </Sec>
            <Sec title="🔤 Tipografía de lectura">
                <div style={{ display: "flex", gap: 5, marginBottom: 10, flexWrap: "wrap" }}>
                    {FONTS.map(f => (
                        <div key={f.key} onClick={() => setActiveFont(f.key)} style={{ padding: "4px 10px", borderRadius: 8, cursor: "pointer", background: activeFont === f.key ? C.green + "33" : C.bgPanel, border: `1px solid ${activeFont === f.key ? C.green : C.border}`, color: activeFont === f.key ? C.green : C.textMid, fontSize: 10, transition: "all 0.2s" }}>{f.label}</div>
                    ))}
                </div>
                <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 11 }}>
                    <span style={{ fontSize: 10, color: C.textDim }}>Tamaño:</span>
                    <div style={{ display: "flex", gap: 5 }}>
                        {[13, 15, 17, 19, 22].map(s => (
                            <div key={s} onClick={() => setFontSize(s)} style={{ width: 27, height: 27, borderRadius: 8, cursor: "pointer", display: "flex", alignItems: "center", justifyContent: "center", fontSize: 9, fontWeight: fontSize === s ? 700 : 400, background: fontSize === s ? C.green + "33" : C.bgPanel, border: `1px solid ${fontSize === s ? C.green : C.border}`, color: fontSize === s ? C.green : C.textMid, transition: "all 0.2s" }}>{s}</div>
                        ))}
                    </div>
                </div>
                <div style={{ background: C.bgPanel, borderRadius: 9, padding: "9px 12px", border: `1px solid ${C.border}` }}>
                    <div style={{ fontSize: 7, color: C.textDim, letterSpacing: "0.1em", textTransform: "uppercase", marginBottom: 6 }}>Vista previa</div>
                    <p style={{ fontFamily: `${currentFont?.label},${currentFont?.style}`, fontSize, lineHeight: 1.8, color: C.textMid, margin: 0 }}>
                        Muchos años después, frente al pelotón de fusilamiento, el coronel Aureliano Buendía había de recordar aquella tarde remota.
                    </p>
                </div>
            </Sec>
            <Sec title="✦ Asistente de IA (On-Device)">
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: aiEnabled ? 12 : 0 }}>
                    <div>
                        <div style={{ fontSize: 11, color: C.text }}>Activar asistente</div>
                        <div style={{ fontSize: 9, color: C.textDim }}>Procesamiento 100% en el dispositivo</div>
                    </div>
                    <Toggle on={aiEnabled} onChange={setAiEnabled} />
                </div>
                {aiEnabled && (
                    <div style={{ background: C.bgPanel, borderRadius: 10, padding: "10px 12px", border: `1px solid ${C.green}33` }}>
                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 7 }}>
                            <span style={{ fontSize: 11, color: C.text, fontWeight: 600 }}>Gemma 3n E2B Q4_K_M</span>
                            <Badge color={C.green}>Instalado</Badge>
                        </div>
                        <div style={{ background: C.border, borderRadius: 99, height: 3, overflow: "hidden", marginBottom: 7 }}>
                            <div style={{ width: "100%", background: C.green, height: 3, boxShadow: `0 0 6px ${C.green}88` }} />
                        </div>
                        <div style={{ fontSize: 9, color: C.textDim }}>1.2 GB en disco · RAM: 3.1 GB / 6.0 GB</div>
                    </div>
                )}
            </Sec>
            <Sec title="⚙️ General">
                {[
                    { label: "Notificaciones de lectura", sub: "Controles TTS en pantalla de bloqueo", on: notifs, set: setNotifs },
                    { label: "TTS automático al abrir", sub: "Empieza a leer en voz alta sin tocar", on: ttsAuto, set: setTtsAuto },
                    { label: "Estadísticas de lectura", sub: "Registrar páginas y tiempo de sesión", on: analytics, set: setAnalytics },
                ].map((s, i, arr) => (
                    <div key={i}>
                        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "5px 0" }}>
                            <div>
                                <div style={{ fontSize: 11, color: C.text }}>{s.label}</div>
                                <div style={{ fontSize: 9, color: C.textDim }}>{s.sub}</div>
                            </div>
                            <Toggle on={s.on} onChange={s.set} />
                        </div>
                        {i < arr.length - 1 && <div style={{ height: 1, background: C.border, margin: "3px 0" }} />}
                    </div>
                ))}
            </Sec>
            <div style={{ background: C.bgCard, border: `1px solid ${C.border}`, borderRadius: 14, padding: "12px 14px" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 9 }}>
                    <span style={{ fontSize: 12, color: C.text, fontWeight: 700 }}>🌿 Woods Reader</span>
                    <Badge color={C.amber}>v1.0.0-alpha</Badge>
                </div>
                <div style={{ fontSize: 9, color: C.textDim, lineHeight: 1.7 }}>
                    Android 14+ · Kotlin 2.0 · Jetpack Compose · Readium KT 3.0.3<br />
                    sherpa-onnx 1.10.30 · Gemma 3n E2B · 100% código abierto
                </div>
            </div>
        </div>
    );
}
