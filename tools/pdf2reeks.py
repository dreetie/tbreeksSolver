#!/usr/bin/env python3
"""Zet een 'Cycles Personnel Services'-PDF om naar het tekstformaat van de reeksoptimalisatie.

Gebruik (zonder iets op je systeem te installeren):
  podman run --rm -v "$PWD":/w -w /w docker.io/library/python:3.12-slim \
      sh -c "pip install -q pdfplumber && python tools/pdf2reeks.py MijnReeks.pdf > mijnreeks.txt"
"""
import re
import sys

import pdfplumber

TIME = re.compile(r"^\d\d:\d\d$")


def main(path):
    rows = {}
    with pdfplumber.open(path) as pdf:
        for page in pdf.pages:
            words = page.extract_words()
            head = [w for w in words if w["text"] == "Service" and w["x0"] < 20]
            if not head:
                continue
            top = head[0]["top"]
            r1 = [w for w in words if w["text"] == "R1" and abs(w["top"] - top) < 3]
            r2 = [w for w in words if w["text"] == "R2" and abs(w["top"] - top) < 3]
            r7 = [w for w in words if w["text"] == "R7" and abs(w["top"] - top) < 3]
            if not (r1 and r2 and r7):
                continue
            step = (r7[0]["x0"] - r1[0]["x0"]) / 6.0
            base = r1[0]["x0"] - 40.4 * step / 65.2
            body = [w for w in words if w["top"] > top + 5 and w["top"] < page.height - 40]
            weeks = sorted((w for w in body if w["x0"] < 30 and w["text"].isdigit()), key=lambda w: w["top"])
            for i, wk in enumerate(weeks):
                y0 = wk["top"] - 4
                y1 = weeks[i + 1]["top"] - 4 if i + 1 < len(weeks) else y0 + 60
                cells = [[] for _ in range(7)]
                for t in body:
                    if y0 <= t["top"] < y1 and 40 < t["x0"] < base + 7 * step:
                        c = int(((t["x0"] + t["x1"]) / 2 - base) // step)
                        if 0 <= c < 7:
                            cells[c].append(t)
                row = []
                for c in cells:
                    c.sort(key=lambda t: (round(t["top"]), t["x0"]))
                    toks = [t["text"] for t in c]
                    times = [x for x in toks if TIME.match(x)]
                    codes = [x for x in toks if not TIME.match(x)]
                    if len(codes) != 1 or len(times) not in (0, 2):
                        sys.exit("Kon week %s niet betrouwbaar lezen: %s" % (wk["text"], toks))
                    row.append(codes[0] + (" %s-%s" % tuple(times) if times else ""))
                rows[int(wk["text"])] = row
    if not rows:
        sys.exit("Geen reeks gevonden in de PDF")
    print("# Omgezet uit %s. Formaat: week;R1;...;R7" % path)
    services = 0
    for w in sorted(rows):
        print("%d;%s" % (w, ";".join(rows[w])))
        services += sum(1 for c in rows[w] if " " in c)
    sys.stderr.write("%d weken, %d diensten ingelezen. Vergelijk met 'Number of Prestations' in de PDF.\n" % (len(rows), services))


if __name__ == "__main__":
    main(sys.argv[1])
