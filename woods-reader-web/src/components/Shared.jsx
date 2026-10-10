import { C } from "../data.js";

export const Badge = ({ children, color = C.green }) => (
    <span style={{
        background: color + "22", color, border: `1px solid ${color}44`,
        borderRadius: 6, fontSize: 10, fontWeight: 700,
        letterSpacing: "0.08em", padding: "2px 8px", textTransform: "uppercase",
    }}>{children}</span>
);

export const ProgressBar = ({ value, color = C.green, height = 3 }) => (
    <div style={{ background: C.border, borderRadius: 99, height, overflow: "hidden" }}>
        <div style={{
            width: `${value}%`, background: color, height, borderRadius: 99,
            boxShadow: `0 0 8px ${color}88`, transition: "width 0.8s ease",
        }} />
    </div>
);

export const Toggle = ({ on, onChange }) => (
    <div onClick={() => onChange(!on)} style={{
        width: 40, height: 22, borderRadius: 11, cursor: "pointer", position: "relative",
        background: on ? C.green : C.bgPanel,
        border: `1px solid ${on ? C.green : C.border}`,
        transition: "all 0.2s", flexShrink: 0,
    }}>
        <div style={{
            position: "absolute", top: 2, left: on ? 18 : 2,
            width: 16, height: 16, borderRadius: "50%", background: C.bg,
            transition: "left 0.2s", boxShadow: on ? `0 0 6px ${C.green}` : "none",
        }} />
    </div>
);
