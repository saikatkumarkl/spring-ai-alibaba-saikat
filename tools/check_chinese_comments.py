import os
import re

ROOT = "spring-ai-alibaba-admin"
HAN = re.compile(r"[\u4e00-\u9fff]")


def scan_java(path: str) -> bool:
    with open(path, "r", encoding="utf-8", errors="ignore") as f:
        lines = f.readlines()
    in_block = False
    for line in lines:
        if in_block:
            if "*/" in line:
                before = line.split("*/", 1)[0]
                m = re.match(r"(\s*\*\s*)(.*)", before)
                text = m.group(2) if m else before
                if HAN.search(text):
                    return True
                in_block = False
            else:
                m = re.match(r"(\s*\*\s*)(.*)", line)
                text = m.group(2) if m else line
                if HAN.search(text):
                    return True
            continue
        if "/*" in line:
            if "*/" in line and line.index("/*") < line.index("*/"):
                body = line.split("/*", 1)[1].split("*/", 1)[0]
                if HAN.search(body):
                    return True
            else:
                in_block = True
                rest = line.split("/*", 1)[1]
                if HAN.search(rest):
                    return True
            continue
        if "//" in line:
            comment = line.split("//", 1)[1]
            if HAN.search(comment):
                return True
    return False


def main() -> None:
    offenders = []
    for dirpath, _, filenames in os.walk(ROOT):
        for fn in filenames:
            if not fn.endswith(".java"):
                continue
            path = os.path.join(dirpath, fn)
            if scan_java(path):
                offenders.append(path)
    print("\n".join(sorted(offenders)))


if __name__ == "__main__":
    main()
