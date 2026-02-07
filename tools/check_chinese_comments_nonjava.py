import os
import re

ROOT = "spring-ai-alibaba-admin"
HAN = re.compile(r"[\u4e00-\u9fff]")

def scan_xml(path: str) -> bool:
    with open(path, "r", encoding="utf-8", errors="ignore") as f:
        text = f.read()
    # find comments
    for m in re.finditer(r"<!--([\s\S]*?)-->", text):
        if HAN.search(m.group(1)):
            return True
    return False


def scan_yaml(path: str) -> bool:
    with open(path, "r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            if "#" in line:
                comment = line.split("#", 1)[1]
                if HAN.search(comment):
                    return True
    return False


def main() -> None:
    offenders = []
    for dirpath, _, filenames in os.walk(ROOT):
        for fn in filenames:
            path = os.path.join(dirpath, fn)
            if fn.endswith(".xml") and scan_xml(path):
                offenders.append(path)
            elif fn.endswith((".yml", ".yaml")) and scan_yaml(path):
                offenders.append(path)
    print("\n".join(sorted(offenders)))


if __name__ == "__main__":
    main()
