"""按 GB2312 + ASCII + 常用全角符号对 MiSans ttf 做子集化。

GB2312:
- 一级汉字 3755 个 (0xB0A1 - 0xF7FE) - 高频常用字
- 二级汉字 3008 个 (0xA1A1 - 0xAFFE) - 次常用字
- 中文标点、数字、符号 (0xA1A1 - 0xA9FE 中的非汉字部分)

外加:
- ASCII (0x20-0x7E)
- 全角符号 (U+FF00-FFEF)
- 常用数学/几何符号、中文引号破折号
"""
import sys
from pathlib import Path

def build_gb2312_chars() -> set[str]:
    chars: set[str] = set()
    # GB2312 双字节范围: 高字节 0xA1-0xF7, 低字节 0xA1-0xFE
    for high in range(0xA1, 0xF8):
        for low in range(0xA1, 0xFF):
            try:
                chars.add(bytes([high, low]).decode("gb2312"))
            except (UnicodeDecodeError, ValueError):
                pass
    return chars


def build_extras() -> set[str]:
    chars: set[str] = set()
    # ASCII 可见字符
    chars.update(chr(i) for i in range(0x20, 0x7F))
    # 拉丁补充(带音调)
    chars.update(chr(i) for i in range(0xA0, 0x100))
    # 全角符号、全角 ASCII
    chars.update(chr(i) for i in range(0xFF00, 0xFFF0))
    # CJK 兼容半全角形式、CJK 标点
    chars.update("「」『』【】〈〉《》〔〕""''·—…、。·・")
    # 货币
    chars.update("¥$€£₹")
    # 数字圈号、罗马数字
    chars.update(chr(i) for i in range(0x2460, 0x2473))
    # 常用箭头/数学符号
    chars.update("→←↑↓↔⇒⇔•·°±×÷≤≥≠≈√∞∑∏∫")
    return chars


def write_text_file(path: Path, chars: set[str]) -> None:
    # pyftsubset 的 --text-file 接受 UTF-8 纯字符列表
    with path.open("w", encoding="utf-8") as f:
        f.write("".join(sorted(chars)))


if __name__ == "__main__":
    chars = build_gb2312_chars() | build_extras()
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "chars.txt")
    write_text_file(out, chars)
    print(f"wrote {len(chars)} unique chars to {out}")
