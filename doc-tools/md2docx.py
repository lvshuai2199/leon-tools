# -*- coding: utf-8 -*-
"""Markdown 导出 Word。

改下面的 MD_PATH，保存后双击同目录 启动.bat。
DOCX_PATH 留空时，输出到 md 同目录、同名 .docx。
命令行可临时覆盖：python md2docx.py <md路径> [docx路径]
"""
import argparse
import io
import re
import sys
import tempfile
import urllib.request
from pathlib import Path

try:
    from docx import Document
    from docx.enum.table import WD_TABLE_ALIGNMENT
    from docx.enum.text import WD_ALIGN_PARAGRAPH
    from docx.oxml import OxmlElement
    from docx.oxml.ns import qn
    from docx.shared import Cm, Pt, RGBColor
    from PIL import Image
except ImportError:
    req = Path(__file__).resolve().parent / "requirements.txt"
    print(f"缺少依赖，先执行：pip install -r \"{req}\"")
    raise SystemExit(1)

# 改这里
MD_PATH = r"D:\GitFiles\personal\leon-notes\N-Welding\FFW-焊接中调整电流电压\电流电压与轨迹微调手册.md"
DOCX_PATH = r""

NAVY = RGBColor(0x1F, 0x4E, 0x79)
BODY = RGBColor(0x22, 0x22, 0x22)
MUTED = RGBColor(0x66, 0x66, 0x66)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
PAGE_WIDTH_CM = 16.0
IMG_RE = re.compile(r"!\[.*?\]\(([^)]+)\)")
NUM_RE = re.compile(r"^(\d+)\.\s+(.*)$")
INLINE_RE = re.compile(r"(\*\*[^*]+\*\*|`[^`]+`)")
HEADING_SIZE = {1: 22, 2: 16, 3: 14}


def set_run_font(run, cn="宋体", en="Calibri", size=12, bold=False, color=None):
    run.bold = bold
    run.font.size = Pt(size)
    run.font.name = en
    if color is not None:
        run.font.color.rgb = color
    rPr = run._element.get_or_add_rPr()
    rFonts = rPr.find(qn("w:rFonts"))
    if rFonts is None:
        rFonts = OxmlElement("w:rFonts")
        rPr.append(rFonts)
    rFonts.set(qn("w:ascii"), en)
    rFonts.set(qn("w:hAnsi"), en)
    rFonts.set(qn("w:eastAsia"), cn)
    rFonts.set(qn("w:cs"), en)


def add_inline(paragraph, text, size=12, color=BODY, cn="宋体"):
    pos = 0
    for match in INLINE_RE.finditer(text):
        if match.start() > pos:
            run = paragraph.add_run(text[pos:match.start()])
            set_run_font(run, cn=cn, size=size, color=color)
        token = match.group(0)
        if token.startswith("**"):
            run = paragraph.add_run(token[2:-2])
            set_run_font(run, cn=cn, size=size, bold=True, color=color)
        else:
            run = paragraph.add_run(token[1:-1])
            set_run_font(run, cn="Consolas", en="Consolas", size=max(size - 1, 9), color=color)
        pos = match.end()
    if pos < len(text) or not text:
        run = paragraph.add_run(text[pos:])
        set_run_font(run, cn=cn, size=size, color=color)


def set_style_font(style, cn, en, size, bold=False, color=None):
    style.font.name = en
    style.font.size = Pt(size)
    style.font.bold = bold
    if color is not None:
        style.font.color.rgb = color
    rPr = style.element.get_or_add_rPr()
    rFonts = rPr.find(qn("w:rFonts"))
    if rFonts is None:
        rFonts = OxmlElement("w:rFonts")
        rPr.append(rFonts)
    rFonts.set(qn("w:ascii"), en)
    rFonts.set(qn("w:hAnsi"), en)
    rFonts.set(qn("w:eastAsia"), cn)
    rFonts.set(qn("w:cs"), en)


def set_paragraph_spacing(paragraph, before=0, after=6, line=1.15, left=0, first=0):
    fmt = paragraph.paragraph_format
    fmt.space_before = Pt(before)
    fmt.space_after = Pt(after)
    fmt.line_spacing = line
    if left:
        fmt.left_indent = Cm(left)
    if first:
        fmt.first_line_indent = Cm(first)


def shade_cell(cell, fill):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = tcPr.find(qn("w:shd"))
    if shd is None:
        shd = OxmlElement("w:shd")
        tcPr.append(shd)
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), fill)


def set_cell_borders(cell):
    tcPr = cell._tc.get_or_add_tcPr()
    borders = tcPr.find(qn("w:tcBorders"))
    if borders is None:
        borders = OxmlElement("w:tcBorders")
        tcPr.append(borders)
    for edge in ("top", "left", "bottom", "right"):
        el = borders.find(qn(f"w:{edge}"))
        if el is None:
            el = OxmlElement(f"w:{edge}")
            borders.append(el)
        el.set(qn("w:val"), "single")
        el.set(qn("w:sz"), "4")
        el.set(qn("w:space"), "0")
        el.set(qn("w:color"), "D0D7E2")


def set_cell_margins(cell):
    tcPr = cell._tc.get_or_add_tcPr()
    tcMar = tcPr.find(qn("w:tcMar"))
    if tcMar is None:
        tcMar = OxmlElement("w:tcMar")
        tcPr.append(tcMar)
    for edge in ("top", "left", "bottom", "right"):
        node = tcMar.find(qn(f"w:{edge}"))
        if node is None:
            node = OxmlElement(f"w:{edge}")
            tcMar.append(node)
        node.set(qn("w:w"), "90")
        node.set(qn("w:type"), "dxa")


def set_cell_valign(cell):
    tcPr = cell._tc.get_or_add_tcPr()
    vAlign = tcPr.find(qn("w:vAlign"))
    if vAlign is None:
        vAlign = OxmlElement("w:vAlign")
        tcPr.append(vAlign)
    vAlign.set(qn("w:val"), "center")


def prevent_row_split(row, header=False):
    trPr = row._tr.get_or_add_trPr()
    if trPr.find(qn("w:cantSplit")) is None:
        trPr.append(OxmlElement("w:cantSplit"))
    if header and trPr.find(qn("w:tblHeader")) is None:
        trPr.append(OxmlElement("w:tblHeader"))


def add_page_number(paragraph):
    run1 = paragraph.add_run()
    fld1 = OxmlElement("w:fldChar")
    fld1.set(qn("w:fldCharType"), "begin")
    run1._r.append(fld1)

    run2 = paragraph.add_run()
    instr = OxmlElement("w:instrText")
    instr.set(qn("xml:space"), "preserve")
    instr.text = " PAGE "
    run2._r.append(instr)
    set_run_font(run2, size=9, color=MUTED)

    run3 = paragraph.add_run()
    fld2 = OxmlElement("w:fldChar")
    fld2.set(qn("w:fldCharType"), "end")
    run3._r.append(fld2)


def configure_doc(doc, title):
    section = doc.sections[0]
    section.page_width = Cm(21.0)
    section.page_height = Cm(29.7)
    section.left_margin = Cm(2.0)
    section.right_margin = Cm(2.0)
    section.top_margin = Cm(2.0)
    section.bottom_margin = Cm(2.0)
    section.header_distance = Cm(0.8)
    section.footer_distance = Cm(0.8)

    normal = doc.styles["Normal"]
    set_style_font(normal, "宋体", "Calibri", 12, color=BODY)
    normal.paragraph_format.space_after = Pt(6)
    normal.paragraph_format.line_spacing = 1.15

    for level, size, before, after in ((1, 22, 0, 12), (2, 16, 16, 8), (3, 14, 12, 6)):
        style = doc.styles[f"Heading {level}"]
        set_style_font(style, "黑体", "Calibri", size, bold=True, color=NAVY)
        style.paragraph_format.space_before = Pt(before)
        style.paragraph_format.space_after = Pt(after)
        style.paragraph_format.line_spacing = 1.15

    footer = section.footer
    footer.is_linked_to_previous = False
    fp = footer.paragraphs[0]
    fp.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_paragraph_spacing(fp, before=0, after=0, line=1.0)
    add_page_number(fp)

    settings = doc.settings.element
    if settings.find(qn("w:updateFields")) is None:
        update = OxmlElement("w:updateFields")
        update.set(qn("w:val"), "true")
        settings.append(update)

    doc.core_properties.title = title
    doc.core_properties.category = "文档"


def add_body(doc, text, indent=0):
    paragraph = doc.add_paragraph()
    set_paragraph_spacing(paragraph, before=0, after=6, line=1.15, left=indent)
    add_inline(paragraph, text)
    return paragraph


def add_number_item(doc, number, text):
    paragraph = doc.add_paragraph()
    set_paragraph_spacing(paragraph, before=2, after=4, line=1.15, left=0.85, first=-0.85)
    add_inline(paragraph, f"{number}. {text}")
    return paragraph


def add_bullet(doc, text, indent=0.75):
    paragraph = doc.add_paragraph()
    set_paragraph_spacing(paragraph, before=1, after=3, line=1.15, left=indent + 0.4, first=-0.4)
    add_inline(paragraph, f"• {text}")
    return paragraph


def column_widths(rows):
    cols = list(zip(*rows))
    weights = []
    for col in cols:
        longest = max((len(cell) for cell in col), default=1)
        weights.append(min(max(longest, 1), 28))
    total = sum(weights) or 1
    widths = [PAGE_WIDTH_CM * weight / total for weight in weights]
    return [max(width, 1.5) for width in widths]


def parse_row(line):
    return [cell.strip() for cell in line.strip().strip("|").split("|")]


def is_separator(line):
    cells = parse_row(line)
    return bool(cells) and all(re.fullmatch(r":?-{3,}:?", cell) for cell in cells)


def add_table(doc, rows):
    cols = max(len(row) for row in rows)
    rows = [row + [""] * (cols - len(row)) for row in rows]
    headers, body = rows[0], rows[1:]
    table = doc.add_table(rows=1 + len(body), cols=cols)
    table.alignment = WD_TABLE_ALIGNMENT.CENTER
    table.autofit = False

    tblPr = table._tbl.tblPr
    tblW = tblPr.find(qn("w:tblW"))
    if tblW is None:
        tblW = OxmlElement("w:tblW")
        tblPr.append(tblW)
    tblW.set(qn("w:w"), str(int(PAGE_WIDTH_CM * 567)))
    tblW.set(qn("w:type"), "dxa")

    widths = column_widths(rows)
    scale = PAGE_WIDTH_CM / sum(widths)
    widths = [width * scale for width in widths]
    short_cols = set()
    for idx, col_cells in enumerate(zip(*rows)):
        if all(len(cell) <= 6 for cell in col_cells):
            short_cols.add(idx)

    for r_idx, row_data in enumerate(rows):
        row = table.rows[r_idx]
        prevent_row_split(row, header=(r_idx == 0))
        for c_idx, text in enumerate(row_data):
            cell = row.cells[c_idx]
            cell.text = ""
            paragraph = cell.paragraphs[0]
            paragraph.alignment = (
                WD_ALIGN_PARAGRAPH.CENTER if c_idx in short_cols else WD_ALIGN_PARAGRAPH.LEFT
            )
            set_paragraph_spacing(paragraph, before=0, after=0, line=1.05)
            if r_idx == 0:
                add_inline(paragraph, text, size=10.5, color=WHITE, cn="黑体")
                for run in paragraph.runs:
                    set_run_font(run, cn="黑体", size=10.5, bold=True, color=WHITE)
                shade_cell(cell, "1F4E79")
            else:
                add_inline(paragraph, text, size=10.5)
                if r_idx % 2 == 0:
                    shade_cell(cell, "F4F7FB")
            set_cell_borders(cell)
            set_cell_margins(cell)
            set_cell_valign(cell)
            cell.width = Cm(widths[c_idx])
            tcPr = cell._tc.get_or_add_tcPr()
            tcW = tcPr.find(qn("w:tcW"))
            if tcW is None:
                tcW = OxmlElement("w:tcW")
                tcPr.append(tcW)
            tcW.set(qn("w:w"), str(int(widths[c_idx] * 567)))
            tcW.set(qn("w:type"), "dxa")

    spacer = doc.add_paragraph()
    set_paragraph_spacing(spacer, before=0, after=2, line=1.0)
    return table


def load_image(src, index, md_dir, img_dir):
    img_dir.mkdir(parents=True, exist_ok=True)
    dest = img_dir / f"img_{index:02d}.png"
    if src.startswith(("http://", "https://")):
        req = urllib.request.Request(
            src,
            headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"},
        )
        with urllib.request.urlopen(req, timeout=60) as resp:
            data = resp.read()
        image = Image.open(io.BytesIO(data))
    else:
        path = Path(src.strip("<>"))
        if not path.is_absolute():
            path = (md_dir / path).resolve()
        image = Image.open(path)
    if image.mode not in ("RGB", "L"):
        image = image.convert("RGB")
    image.save(dest, format="PNG")
    return dest, image.size


def add_image(doc, path, size):
    w_px, h_px = size
    width = w_px / 120 * 2.54
    height = h_px / 120 * 2.54
    scale = min(
        1.0,
        PAGE_WIDTH_CM / width if width else 1.0,
        PAGE_WIDTH_CM / height if height else 1.0,
    )
    width *= scale
    paragraph = doc.add_paragraph()
    paragraph.alignment = WD_ALIGN_PARAGRAPH.CENTER
    set_paragraph_spacing(paragraph, before=6, after=10, line=1.0)
    paragraph.add_run().add_picture(str(path), width=Cm(width))
    return paragraph


def parse_blocks(text):
    lines = text.splitlines()
    blocks = []
    i = 0
    while i < len(lines):
        raw = lines[i]
        stripped = raw.strip()
        if not stripped:
            i += 1
            continue
        if stripped.startswith("|"):
            table_lines = []
            while i < len(lines) and lines[i].strip().startswith("|"):
                table_lines.append(lines[i].strip())
                i += 1
            rows = [parse_row(line) for line in table_lines if not is_separator(line)]
            if rows:
                blocks.append(("table", rows))
            continue
        image = IMG_RE.fullmatch(stripped)
        if image:
            blocks.append(("image", image.group(1).strip()))
            i += 1
            continue
        heading = re.match(r"^(#{1,3})\s+(.*)$", stripped)
        if heading:
            blocks.append((f"h{len(heading.group(1))}", heading.group(2).strip()))
            i += 1
            continue
        number = NUM_RE.match(stripped)
        if number:
            blocks.append(("num", number.group(1), number.group(2)))
            i += 1
            continue
        if stripped.startswith(("- ", "* ")):
            indent = 1.15 if raw.startswith((" ", "\t")) else 0.75
            blocks.append(("bullet", stripped[2:].strip(), indent))
            i += 1
            continue
        indent = 0.75 if raw.startswith((" ", "\t")) else 0
        blocks.append(("p", stripped, indent))
        i += 1
    return blocks


def export(md_path, docx_path):
    src = Path(md_path)
    if not src.is_file():
        print(f"找不到 Markdown：{src}")
        return 1
    dst = Path(docx_path) if docx_path else src.with_suffix(".docx")
    text = src.read_text(encoding="utf-8-sig")
    blocks = parse_blocks(text)
    images = [block[1] for block in blocks if block[0] == "image"]
    loaded = {}
    failed = []
    img_dir = Path(tempfile.gettempdir()) / "md2docx-imgs"
    for idx, src_img in enumerate(images, start=1):
        try:
            loaded[src_img] = load_image(src_img, idx, src.parent, img_dir)
            size = loaded[src_img][1]
            print(f"图片 {idx}/{len(images)} {size[0]}x{size[1]}")
        except Exception as exc:
            failed.append((src_img, str(exc)))
            print(f"图片失败 {idx}/{len(images)} {exc}")

    title = next((block[1] for block in blocks if block[0] == "h1"), src.stem)
    doc = Document()
    configure_doc(doc, title)
    for block in blocks:
        kind = block[0]
        if kind in ("h1", "h2", "h3"):
            level = int(kind[1])
            paragraph = doc.add_heading(block[1], level=level)
            for run in paragraph.runs:
                set_run_font(run, cn="黑体", size=HEADING_SIZE[level], bold=True, color=NAVY)
        elif kind == "num":
            add_number_item(doc, block[1], block[2])
        elif kind == "bullet":
            add_bullet(doc, block[1], indent=block[2])
        elif kind == "p":
            add_body(doc, block[1], indent=block[2])
        elif kind == "table":
            add_table(doc, block[1])
        elif kind == "image":
            if block[1] in loaded:
                path, size = loaded[block[1]]
                add_image(doc, path, size)
            else:
                add_body(doc, f"[图片失败] {block[1]}")

    try:
        dst.parent.mkdir(parents=True, exist_ok=True)
        doc.save(dst)
    except PermissionError:
        print(f"无法写入，文件可能正被 Word 打开：{dst}")
        return 1
    print(f"已导出 {dst}")
    print(f"图片 {len(loaded)}/{len(images)}，失败 {len(failed)}")
    return 1 if failed else 0


def main():
    parser = argparse.ArgumentParser(description="把 Markdown 导出为 Word")
    parser.add_argument("md", nargs="?", default=MD_PATH, help="Markdown 路径，默认用脚本顶部 MD_PATH")
    parser.add_argument("docx", nargs="?", default=DOCX_PATH, help="docx 路径，默认同名")
    args = parser.parse_args()
    if not str(args.md).strip():
        print("请先在 md2docx.py 顶部填写 MD_PATH")
        return 1
    return export(args.md, args.docx)


if __name__ == "__main__":
    sys.exit(main())
