import re
from pathlib import Path

src_dir = Path('/Users/kumarsaikat/code/AI/work/spring-ai-alibaba-saikat/spring-ai-alibaba-admin/docker/middleware/init/mysql')
out_dir = Path('/Users/kumarsaikat/code/AI/work/spring-ai-alibaba-saikat/spring-ai-alibaba-admin/docker/middleware/init/postgres')

replacements = [
    (r"\)\s*ENGINE\s*=.*?;", ");", re.IGNORECASE | re.DOTALL),
    (r"\)\s*ENGINE\s*=.*?\n", ")\n", re.IGNORECASE),
    (r"AUTO_INCREMENT\s*=\s*\d+", "", re.IGNORECASE),
    (r"DEFAULT CHARSET\s*=\s*[^\s]+", "", re.IGNORECASE),
    (r"DEFAULT\s+CHARACTER\s+SET\s*=\s*[^\s]+", "", re.IGNORECASE),
    (r"COLLATE\s*=\s*[^\s]+", "", re.IGNORECASE),
    (r"COMMENT\s*=\s*'[^']*'", "", re.IGNORECASE),
    (r"COMMENT\s+'[^']*'", "", re.IGNORECASE),
    (r"\s+ON UPDATE CURRENT_TIMESTAMP\(?\d*\)?", "", re.IGNORECASE),
    (r"BIGINT\(\d+\)\s+UNSIGNED\s+AUTO_INCREMENT", "BIGSERIAL", re.IGNORECASE),
    (r"BIGINT\s+UNSIGNED\s+AUTO_INCREMENT", "BIGSERIAL", re.IGNORECASE),
    (r"BIGINT(?:\(\d+\))?\s+UNSIGNED\s+NOT NULL\s+AUTO_INCREMENT", "BIGSERIAL NOT NULL", re.IGNORECASE),
    (r"BIGINT(?:\(\d+\))?\s+NOT NULL\s+AUTO_INCREMENT", "BIGSERIAL NOT NULL", re.IGNORECASE),
    (r"BIGINT(?:\(\d+\))?\s+AUTO_INCREMENT", "BIGSERIAL", re.IGNORECASE),
    (r"BIGINT\(\d+\)\s+UNSIGNED", "BIGINT", re.IGNORECASE),
    (r"BIGINT\s+UNSIGNED", "BIGINT", re.IGNORECASE),
    (r"BIGINT\(\d+\)", "BIGINT", re.IGNORECASE),
    (r"TINYINT\(\d+\)", "SMALLINT", re.IGNORECASE),
    (r"\bTINYINT\b", "SMALLINT", re.IGNORECASE),
    (r"\bINT\(\d+\)", "INTEGER", re.IGNORECASE),
    (r"DATETIME\((\d+)\)", r"TIMESTAMP(\1)", re.IGNORECASE),
    (r"DATETIME", "TIMESTAMP", re.IGNORECASE),
    (r"LONGTEXT", "TEXT", re.IGNORECASE),
    (r"DEFAULT\s+CURRENT_TIMESTAMP\(\d+\)", "DEFAULT CURRENT_TIMESTAMP", re.IGNORECASE),
    (r"UNIQUE KEY\s+\w+\s*\(([^\)]+)\)", r"UNIQUE (\1)", re.IGNORECASE),
    (r"\n\s*KEY\s+\w+\s*\([^\)]*\)\s*,?", "", re.IGNORECASE),
    (r"\bTINYINTEGER\b", "SMALLINT", re.IGNORECASE),
]

out_dir.mkdir(parents=True, exist_ok=True)

for name in ['admin-schema.sql', 'agentscope-schema.sql']:
    src = src_dir / name
    out = out_dir / name
    sql = src.read_text(encoding='utf-8')
    sql = sql.replace('`', '')
    for pattern, repl, flags in replacements:
        sql = re.sub(pattern, repl, sql, flags=flags)
    sql = re.sub(r",\s*\n\)", "\n)", sql)
    sql = re.sub(r",\s*\)", ")", sql)
    sql = re.sub(r"^#", "--", sql, flags=re.MULTILINE)
    sql = re.sub(r"\n\s*\)\s*\n", "\n)\n", sql)
    sql = re.sub(r"\n{3,}", "\n\n", sql)
    out.write_text(sql, encoding='utf-8')
    print(f"Converted {name} -> {out}")
