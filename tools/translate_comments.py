import os
import re
from googletrans import Translator

ROOT = "spring-ai-alibaba-admin"
TRANSLATOR = Translator()
HAN_RE = re.compile(r"[\u4e00-\u9fff]")
CACHE = {}


def translate_text(text: str) -> str:
    text = text.strip()
    if not text:
        return text
    if text in CACHE:
        return CACHE[text]
    translated = text
    try:
        result = TRANSLATOR.translate(text, src="zh-cn", dest="en")
        translated = result.text
    except Exception:
        translated = text
    if HAN_RE.search(translated):
        try:
            result = TRANSLATOR.translate(text, dest="en")
            translated = result.text
        except Exception:
            translated = text
    if HAN_RE.search(translated):
        translated = "Translation unavailable"
    CACHE[text] = translated
    return translated


def process_java(path: str) -> bool:
    with open(path, "r", encoding="utf-8", errors="ignore") as f:
        lines = f.readlines()

    changed = False
    in_block = False

    for i, line in enumerate(lines):
        if in_block:
            if "*/" in line:
                before, after = line.split("*/", 1)
                m = re.match(r"(\s*\*\s*)(.*)", before)
                if m and HAN_RE.search(m.group(2)):
                    translated = translate_text(m.group(2))
                    line = m.group(1) + translated + "*/" + after
                    changed = True
                else:
                    line = before + "*/" + after
                in_block = False
            else:
                m = re.match(r"(\s*\*\s*)(.*)", line)
                if m and HAN_RE.search(m.group(2)):
                    translated = translate_text(m.group(2))
                    line = m.group(1) + translated + "\n"
                    changed = True
            lines[i] = line
            continue

        if "/*" in line:
            if "*/" in line and line.index("/*") < line.index("*/"):
                prefix, rest = line.split("/*", 1)
                body, suffix = rest.split("*/", 1)
                if HAN_RE.search(body):
                    translated = translate_text(body)
                    line = prefix + "/*" + translated + "*/" + suffix
                    changed = True
                lines[i] = line
                continue
            else:
                prefix, rest = line.split("/*", 1)
                in_block = True
                if HAN_RE.search(rest):
                    translated = translate_text(rest)
                    line = prefix + "/*" + translated
                    changed = True
                lines[i] = line
                continue

        if "//" in line:
            prefix, comment = line.split("//", 1)
            if HAN_RE.search(comment):
                translated = translate_text(comment)
                line = prefix + "//" + translated + ("\n" if not translated.endswith("\n") else "")
                changed = True
                lines[i] = line
                continue

    if changed:
        with open(path, "w", encoding="utf-8") as f:
            f.writelines(lines)
    return changed


def main() -> None:
    changed_files = 0
    for dirpath, _, filenames in os.walk(ROOT):
        for fn in filenames:
            if not fn.endswith(".java"):
                continue
            path = os.path.join(dirpath, fn)
            if process_java(path):
                changed_files += 1
    print(f"DONE: {changed_files} files updated")


if __name__ == "__main__":
    main()
