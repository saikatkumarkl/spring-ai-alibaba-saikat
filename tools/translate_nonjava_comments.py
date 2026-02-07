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


def process_xml(path: str) -> bool:
    with open(path, "r", encoding="utf-8", errors="ignore") as f:
        lines = f.readlines()

    changed = False
    in_comment = False

    for i, line in enumerate(lines):
        if in_comment:
            if "-->" in line:
                before, after = line.split("-->", 1)
                content = before
                if HAN_RE.search(content):
                    translated = translate_text(content)
                    line = translated + "-->" + after
                    changed = True
                in_comment = False
            else:
                if HAN_RE.search(line):
                    translated = translate_text(line)
                    line = translated + "\n"
                    changed = True
            lines[i] = line
            continue

        if "<!--" in line:
            prefix, rest = line.split("<!--", 1)
            if "-->" in rest:
                body, suffix = rest.split("-->", 1)
                if HAN_RE.search(body):
                    translated = translate_text(body)
                    line = prefix + "<!--" + translated + "-->" + suffix
                    changed = True
                lines[i] = line
                continue
            else:
                in_comment = True
                if HAN_RE.search(rest):
                    translated = translate_text(rest)
                    line = prefix + "<!--" + translated
                    changed = True
                lines[i] = line
                continue

    if changed:
        with open(path, "w", encoding="utf-8") as f:
            f.writelines(lines)
    return changed


def process_yaml(path: str) -> bool:
    with open(path, "r", encoding="utf-8", errors="ignore") as f:
        lines = f.readlines()

    changed = False
    for i, line in enumerate(lines):
        if "#" not in line:
            continue
        prefix, comment = line.split("#", 1)
        if HAN_RE.search(comment):
            translated = translate_text(comment)
            line = prefix + "#" + translated + ("\n" if not translated.endswith("\n") else "")
            lines[i] = line
            changed = True
    if changed:
        with open(path, "w", encoding="utf-8") as f:
            f.writelines(lines)
    return changed


def main() -> None:
    changed_files = 0
    for dirpath, _, filenames in os.walk(ROOT):
        for fn in filenames:
            path = os.path.join(dirpath, fn)
            if fn.endswith(".xml"):
                if process_xml(path):
                    changed_files += 1
            elif fn.endswith(('.yml', '.yaml')):
                if process_yaml(path):
                    changed_files += 1
    print(f"DONE: {changed_files} files updated")


if __name__ == "__main__":
    main()
