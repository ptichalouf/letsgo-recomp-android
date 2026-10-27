#!/usr/bin/env python3
"""Copie les libellés du lanceur (apk/res/values{,-en,-fr}/strings.xml) dans maquettes/commun/strings.js.

Les maquettes n'ont AUCUN libellé écrit à la main : elles lisent ce fichier. Relancer après chaque
modification des strings.xml :

    python3 design/outils/gen-strings.py /chemin/du/depot

Décodage = celui d'Android : guillemets d'encadrement retirés, \\' \\" \\n \\\\ décodés.
"""
import json, os, subprocess, sys
import xml.etree.ElementTree as ET

repo = sys.argv[1] if len(sys.argv) > 1 else "/opt/data/cache/scratch/lg-ui"
out = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "maquettes", "commun", "strings.js")


def android(s):
    s = s or ""
    if len(s) >= 2 and s[0] == '"' and s[-1] == '"':
        s = s[1:-1]
    r, i = [], 0
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s):
            n = s[i + 1]
            r.append({"n": "\n", "t": "\t", "'": "'", '"': '"', "\\": "\\", "@": "@", "?": "?"}.get(n, n))
            i += 2
            continue
        r.append(c)
        i += 1
    return "".join(r)


S = {}
for lang, d in (("pt", "values"), ("en", "values-en"), ("fr", "values-fr")):
    root = ET.parse(os.path.join(repo, "apk", "res", d, "strings.xml")).getroot()
    S[lang] = {e.get("name"): android(e.text) for e in root.findall("string")}

keys = set(S["pt"])
for lang in ("en", "fr"):
    if set(S[lang]) != keys:
        sys.exit("clés différentes entre values et values-%s : %s" % (lang, sorted(set(S[lang]) ^ keys)))

try:
    rev = subprocess.check_output(["git", "-C", repo, "log", "-1", "--format=%h %cs"], text=True).strip()
except Exception:
    rev = "?"

with open(out, "w", encoding="utf-8") as f:
    f.write("// GÉNÉRÉ par design/outils/gen-strings.py depuis apk/res/values{,-en,-fr}/strings.xml (%s).\n" % rev)
    f.write("// Ne pas éditer : relancer le script.\n")
    f.write("window.STRINGS_SOURCE = %s;\n" % json.dumps("apk/res/values{,-en,-fr}/strings.xml @ " + rev))
    f.write("window.STRINGS = %s;\n" % json.dumps(S, ensure_ascii=False, indent=0, sort_keys=True))
print("%d clés x 3 langues -> %s (%s)" % (len(keys), os.path.normpath(out), rev))
