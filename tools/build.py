#!/usr/bin/env python3
"""
Kanji Kartları – derleme betiği

data/ klasöründeki JSON dosyalarını okuyup şunları üretir:

  docs/index.html                         Web sürümü (GitHub Pages ana sayfası)
  docs/data.json                          Uygulamanın indirdiği güncel veri
  docs/version.json                       Veri sürümü + SHA-256 özeti
  docs/privacy.html                       Gizlilik politikası (Google Play için)
  android/app/src/main/assets/index.html  Android'in içindeki hazır veri
  android/app/src/main/assets/config.json Uzaktan güncelleme adresi

Veri sürümü otomatik artar: data/ içeriği değiştiyse bir önceki
docs/version.json'daki sayı +1 yapılır, değişmediyse aynı kalır.

Kullanım:
  python3 tools/build.py            # derle
  python3 tools/build.py --check    # sadece verileri doğrula
"""
import hashlib, json, pathlib, shutil, sys, datetime

ROOT = pathlib.Path(__file__).resolve().parent.parent
DATA = ROOT / "data"
DOCS = ROOT / "docs"
ASSETS = ROOT / "android" / "app" / "src" / "main" / "assets"
LEVELS = ["n5", "n4", "n3", "n2", "n1"]


def load(p):
    try:
        return json.loads(p.read_text(encoding="utf-8"))
    except FileNotFoundError:
        sys.exit(f"HATA: {p.relative_to(ROOT)} bulunamadı")
    except json.JSONDecodeError as e:
        sys.exit(f"HATA: {p.relative_to(ROOT)} satır {e.lineno}, sütun {e.colno}: {e.msg}")


def collect():
    errors = []
    kanji, words = [], {}
    radicals = load(DATA / "radicals" / "radicals.json")
    rad_keys = {r["key"] for r in radicals}
    seen = set()

    for lv in LEVELS:
        path = f"data/kanji/{lv}.json"
        for i, k in enumerate(load(DATA / "kanji" / f"{lv}.json"), start=2):
            where = f"{path} satır {i}"
            missing = [f for f in ("kanji", "level", "radical", "on", "kun", "easy", "meaning") if f not in k]
            if missing:
                errors.append(f"{where}: eksik alan {missing}")
                continue
            if k["kanji"] in seen:
                errors.append(f"{where}: {k['kanji']} birden fazla kez var")
            seen.add(k["kanji"])
            if k["level"].lower() != lv:
                errors.append(f"{where}: level '{k['level']}' dosya adıyla ({lv}) uyuşmuyor")
            if k["radical"] not in rad_keys:
                errors.append(f"{where}: radikal '{k['radical']}' radicals.json içinde yok")
            m = k["meaning"]
            if not m.get("tr") or not m.get("en"):
                errors.append(f"{where}: meaning.tr ve meaning.en dolu olmalı")
            kanji.append([k["kanji"], k["level"], k["radical"], k["on"], k["kun"], k["easy"],
                          m.get("tr", ""), m.get("en", "")])

        path = f"data/vocabulary/{lv}.json"
        for i, w in enumerate(load(DATA / "vocabulary" / f"{lv}.json"), start=2):
            where = f"{path} satır {i}"
            missing = [f for f in ("kanji", "word", "reading", "meaning") if f not in w]
            if missing:
                errors.append(f"{where}: eksik alan {missing}")
                continue
            if w["kanji"] not in seen:
                errors.append(f"{where}: kanji '{w['kanji']}' bu seviyenin kanji dosyasında yok")
            m = w["meaning"]
            words.setdefault(w["kanji"], []).append([w["word"], w["reading"], m.get("tr", ""), m.get("en", "")])

    i18n = {p.stem: load(p) for p in sorted((DATA / "i18n").glob("*.json"))}
    base_keys = set(i18n.get("tr", {}))
    for name, d in i18n.items():
        diff = base_keys - set(d)
        if diff:
            errors.append(f"data/i18n/{name}.json: eksik anahtarlar {sorted(diff)}")

    config = load(DATA / "config.json")
    content = {
        "kanji": kanji,
        "words": words,
        "radicals": [[r["key"], r["forms"], r["name_ja"], r["strokes"], r["tr"], r["en"]] for r in radicals],
        "i18n": i18n,
    }
    return content, config, errors


def main():
    content, config, errors = collect()
    if errors:
        print("\n".join(errors))
        sys.exit(f"\n{len(errors)} hata bulundu, derleme yapılmadı.")
    n_words = sum(map(len, content["words"].values()))
    print(f"Veri tamam: {len(content['kanji'])} kanji, {n_words} kelime, {len(content['radicals'])} radikal")
    if "--check" in sys.argv:
        return

    remote = config.get("remoteBaseUrl", "")
    if "KULLANICI" in remote or not remote.startswith("https://"):
        print("UYARI: data/config.json içindeki remoteBaseUrl ayarlanmamış; uygulama uzaktan güncelleme yapmaz.")

    # İçerik özeti ve sürüm
    canonical = json.dumps(content, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    content_sha = hashlib.sha256(canonical.encode("utf-8")).hexdigest()
    prev = {}
    if (DOCS / "version.json").exists():
        prev = json.loads((DOCS / "version.json").read_text(encoding="utf-8"))
    if prev.get("contentSha") == content_sha:
        version = prev["dataVersion"]
    else:
        version = int(prev.get("dataVersion", 0)) + 1

    payload = {"meta": {"version": version, "contentSha": content_sha,
                        "built": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")},
               **content}
    if prev.get("contentSha") == content_sha and (DOCS / "data.json").exists():
        data_bytes = (DOCS / "data.json").read_bytes()          # değişmediyse dosyaya dokunma
        payload = json.loads(data_bytes)
    else:
        data_bytes = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")

    js = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).replace("</", "<\\/")
    html = (ROOT / "src" / "app.template.html").read_text(encoding="utf-8").replace("/*__DATA__*/null", js)

    DOCS.mkdir(exist_ok=True)
    (DOCS / "index.html").write_text(html, encoding="utf-8")
    (DOCS / "data.json").write_bytes(data_bytes)
    (DOCS / "version.json").write_text(json.dumps({
        "dataVersion": version,
        "minAppVersion": int(config.get("minAppVersion", 1)),
        "file": "data.json",
        "sha256": hashlib.sha256(data_bytes).hexdigest(),
        "contentSha": content_sha,
    }, indent=2) + "\n", encoding="utf-8")
    shutil.copyfile(ROOT / "src" / "privacy.html", DOCS / "privacy.html")
    (DOCS / ".nojekyll").write_text("", encoding="utf-8")

    ASSETS.mkdir(parents=True, exist_ok=True)
    (ASSETS / "index.html").write_text(html, encoding="utf-8")
    (ASSETS / "config.json").write_text(json.dumps({"remoteBaseUrl": remote}, indent=2) + "\n", encoding="utf-8")

    print(f"Veri sürümü: {version}" + ("  (değişiklik yok)" if prev.get("contentSha") == content_sha else "  (yeni)"))
    print("Yazıldı: docs/ ve android/app/src/main/assets/")


if __name__ == "__main__":
    main()
