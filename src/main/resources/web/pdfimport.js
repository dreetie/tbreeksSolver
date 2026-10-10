// Zet een "Cycles Personnel Services"-PDF om naar het tekstformaat week;R1;...;R7 (volledig in de browser).
(function (root) {
  const TIME = /^\d\d:\d\d$/;

  async function pdfToSeries(pdfjsLib, data) {
    const doc = await pdfjsLib.getDocument({ data: new Uint8Array(data) }).promise;
    const rows = {};
    for (let p = 1; p <= doc.numPages; p++) {
      const page = await doc.getPage(p);
      const H = page.getViewport({ scale: 1 }).height;
      const items = (await page.getTextContent()).items
        .filter(i => i.str && i.str.trim())
        .map(i => ({ t: i.str.trim(), x0: i.transform[4], x1: i.transform[4] + i.width, top: H - i.transform[5] }));
      const head = items.find(i => i.t === "Service" && i.x0 < 20);
      if (!head) continue;
      const near = name => items.find(i => i.t === name && Math.abs(i.top - head.top) < 3);
      const r1 = near("R1"), r7 = near("R7");
      if (!r1 || !r7) continue;
      const step = (r7.x0 - r1.x0) / 6;
      const base = r1.x0 - 40.4 * step / 65.2;
      const body = items.filter(i => i.top > head.top + 5 && i.top < H - 40);
      const weeks = body.filter(i => i.x0 < 30 && /^\d+$/.test(i.t)).sort((a, b) => a.top - b.top);
      weeks.forEach((wk, n) => {
        const y0 = wk.top - 4;
        const y1 = n + 1 < weeks.length ? weeks[n + 1].top - 4 : y0 + 60;
        const cells = [[], [], [], [], [], [], []];
        body.forEach(t => {
          if (t.top >= y0 && t.top < y1 && t.x0 > 40 && t.x0 < base + 7 * step) {
            const c = Math.floor(((t.x0 + t.x1) / 2 - base) / step);
            if (c >= 0 && c < 7) cells[c].push(t);
          }
        });
        const row = cells.map(c => {
          c.sort((a, b) => (Math.round(a.top) - Math.round(b.top)) || (a.x0 - b.x0));
          const times = c.filter(t => TIME.test(t.t)).map(t => t.t);
          const codes = c.filter(t => !TIME.test(t.t)).map(t => t.t);
          if (codes.length !== 1 || (times.length !== 0 && times.length !== 2))
            throw new Error("Kon week " + wk.t + " niet betrouwbaar lezen (" + c.map(t => t.t).join(" ") + ")");
          return codes[0] + (times.length ? " " + times[0] + "-" + times[1] : "");
        });
        rows[parseInt(wk.t, 10)] = row;
      });
    }
    const nums = Object.keys(rows).map(Number).sort((a, b) => a - b);
    if (!nums.length) throw new Error("Geen reeks gevonden in deze PDF");
    let services = 0;
    const lines = nums.map(w => { services += rows[w].filter(c => c.indexOf(" ") > 0).length; return w + ";" + rows[w].join(";"); });
    return { text: "# Omgezet uit PDF. Formaat: week;R1;...;R7\n" + lines.join("\n") + "\n", weeks: nums.length, services };
  }

  if (typeof module !== "undefined") module.exports = { pdfToSeries };
  else root.pdfToSeries = pdfToSeries;
})(typeof window !== "undefined" ? window : this);
