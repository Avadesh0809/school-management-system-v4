/* Minimal canvas charts: bar + doughnut (no libs) */
(function () {
  function barChart(canvas, data, opts = {}) {
    const ctx = canvas.getContext('2d');
    const dpr = window.devicePixelRatio || 1;
    const w = canvas.clientWidth, h = canvas.clientHeight || 240;
    canvas.width = w * dpr; canvas.height = h * dpr;
    ctx.scale(dpr, dpr); ctx.clearRect(0, 0, w, h);
    const pad = { l: 40, r: 10, t: 20, b: 30 };
    const cw = w - pad.l - pad.r, ch = h - pad.t - pad.b;
    const max = Math.max(...data.map(d => d.value), 1);
    const bw = cw / data.length * 0.7;
    const gap = cw / data.length * 0.3;
    ctx.font = '12px Inter, sans-serif';
    ctx.strokeStyle = '#e2e8f0'; ctx.fillStyle = '#64748b';
    // Y axis lines
    for (let i = 0; i <= 4; i++) {
      const y = pad.t + ch - (ch * i / 4);
      ctx.beginPath(); ctx.moveTo(pad.l, y); ctx.lineTo(pad.l + cw, y); ctx.stroke();
      ctx.fillText(Math.round(max * i / 4), 4, y + 4);
    }
    data.forEach((d, i) => {
      const x = pad.l + i * (bw + gap) + gap / 2;
      const bh = (d.value / max) * ch;
      const grad = ctx.createLinearGradient(0, pad.t, 0, pad.t + ch);
      grad.addColorStop(0, opts.color || '#4f46e5'); grad.addColorStop(1, opts.color2 || '#0ea5e9');
      ctx.fillStyle = grad;
      ctx.fillRect(x, pad.t + ch - bh, bw, bh);
      ctx.fillStyle = '#475569';
      ctx.textAlign = 'center';
      ctx.fillText(d.label, x + bw / 2, h - 10);
      ctx.textAlign = 'left';
    });
  }

  function doughnut(canvas, data) {
    const ctx = canvas.getContext('2d');
    const dpr = window.devicePixelRatio || 1;
    const w = canvas.clientWidth, h = canvas.clientHeight || 240;
    canvas.width = w * dpr; canvas.height = h * dpr;
    ctx.scale(dpr, dpr); ctx.clearRect(0, 0, w, h);
    const cx = w / 2, cy = h / 2, r = Math.min(w, h) / 2 - 20;
    const total = data.reduce((s, d) => s + d.value, 0) || 1;
    const colors = ['#4f46e5','#0ea5e9','#16a34a','#f59e0b','#dc2626','#a855f7'];
    let start = -Math.PI / 2;
    data.forEach((d, i) => {
      const ang = (d.value / total) * Math.PI * 2;
      ctx.beginPath(); ctx.moveTo(cx, cy); ctx.arc(cx, cy, r, start, start + ang); ctx.closePath();
      ctx.fillStyle = d.color || colors[i % colors.length]; ctx.fill();
      start += ang;
    });
    ctx.beginPath(); ctx.arc(cx, cy, r * 0.55, 0, Math.PI * 2);
    ctx.fillStyle = '#fff'; ctx.fill();
    ctx.fillStyle = '#0f172a'; ctx.font = 'bold 16px Inter, sans-serif'; ctx.textAlign = 'center';
    ctx.fillText(total, cx, cy + 5);
  }

  window.Charts = { bar: barChart, doughnut };
})();
