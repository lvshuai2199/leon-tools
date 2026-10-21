import hashlib
import os
import re
import shutil
from datetime import datetime


IMAGE_EXTENSIONS = {'.png', '.jpg', '.jpeg', '.gif', '.bmp', '.svg'}

def get_all_images(image_folder):
    """
    获取图片文件夹及其子文件夹中所有图片的相对路径。

    :param image_folder: 图片文件夹路径
    :return: 图片相对路径列表
    """
    images = []
    for root, _, files in os.walk(image_folder):
        for file in files:
            if os.path.splitext(file.lower())[1] in IMAGE_EXTENSIONS:
                images.append(os.path.normpath(os.path.relpath(os.path.join(root, file), image_folder)))
    print(f"在图片文件夹及其子文件夹中找到 {len(images)} 个图片文件。")
    return images


def find_correct_image_path(image_folder, ref_path):
    """
    查找引用的图片文件在图片文件夹中的正确路径。

    :param image_folder: 图片文件夹路径
    :param ref_path: Markdown文件中引用的图片路径
    :return: 图片的正确相对路径（如果找到），否则返回 None
    """
    image_name = os.path.basename(ref_path)
    for root, _, files in os.walk(image_folder):
        if image_name in files:
            return os.path.normpath(os.path.relpath(os.path.join(root, image_name), image_folder))
    return None


def normalize_image_reference(ref_path):
    """
    清理 Markdown 图片引用中的查询参数、锚点和尖括号。
    """
    ref_path = ref_path.strip().strip('<>').split('?')[0].split('#')[0]
    return ref_path


def is_external_image_reference(ref_path):
    """
    判断是否是外部图片链接。
    """
    lowered = ref_path.lower()
    return lowered.startswith(('http://', 'https://', 'data:', 'ftp://'))


def build_used_image_name(source_rel_path):
    """
    生成统一图片文件夹中的唯一文件名。
    """
    normalized = os.path.normpath(source_rel_path)
    base_name = os.path.basename(normalized)
    stem, ext = os.path.splitext(base_name)
    digest = hashlib.sha1(normalized.encode('utf-8')).hexdigest()[:10]
    return f"{stem}__{digest}{ext}"


def is_subpath(path, parent):
    """
    判断 path 是否位于 parent 目录内部。
    """
    path = os.path.abspath(path)
    parent = os.path.abspath(parent)

    try:
        return os.path.commonpath([path, parent]) == parent
    except ValueError:
        return False


def copy_used_images(image_folder, used_image_folder, used_images):
    """
    将已使用的图片复制到单一文件夹中，并统一命名。
    """
    if not used_image_folder:
        return []

    image_folder = os.path.abspath(image_folder)
    used_image_folder = os.path.abspath(used_image_folder)

    if is_subpath(used_image_folder, image_folder):
        raise ValueError("输出文件夹不能位于源图片文件夹内部，请选择 image_folder 之外的路径。")

    if os.path.exists(used_image_folder):
        shutil.rmtree(used_image_folder)
    os.makedirs(used_image_folder, exist_ok=True)

    copied_images = []
    for rel_path in sorted(used_images):
        src_path = os.path.join(image_folder, rel_path)
        if not os.path.exists(src_path):
            print(f"跳过不存在的图片: {src_path}")
            continue

        dest_name = build_used_image_name(rel_path)
        dest_path = os.path.join(used_image_folder, dest_name)
        shutil.copy2(src_path, dest_path)
        copied_images.append(rel_path)

    print(f"已将 {len(copied_images)} 个已使用图片复制到: {used_image_folder}")
    return copied_images


def resolve_image_reference(md_file_path, image_folder, used_image_folder, raw_ref, image_set, used_image_map):
    """
    将 Markdown 中的一条图片引用解析成源图片路径。

    :return: (source_rel_path, dest_name, state)
             state 为 'source' 表示原始图片目录，'used' 表示已统一后的图片目录，None 表示未找到。
    """
    clean_ref = normalize_image_reference(raw_ref)
    if not clean_ref or is_external_image_reference(clean_ref):
        return None, None, None

    md_dir = os.path.dirname(md_file_path)
    abs_ref_path = os.path.abspath(os.path.join(md_dir, clean_ref))

    if used_image_folder and os.path.exists(abs_ref_path) and is_subpath(abs_ref_path, used_image_folder):
        used_rel = os.path.normpath(os.path.relpath(abs_ref_path, used_image_folder))
        source_rel = used_image_map.get(used_rel)
        if source_rel:
            return source_rel, used_rel, 'used'

    source_rel = os.path.normpath(os.path.relpath(abs_ref_path, image_folder))
    if source_rel in image_set:
        return source_rel, build_used_image_name(source_rel), 'source'

    correct_path = find_correct_image_path(image_folder, clean_ref)
    if correct_path:
        return correct_path, build_used_image_name(correct_path), 'source'

    return None, None, None


def fix_image_reference(file_path, old_ref, new_ref):
    """
    修复Markdown文件中的图片引用路径。

    :param file_path: Markdown文件路径
    :param old_ref: 旧的图片引用路径
    :param new_ref: 新的图片引用路径
    """
    with open(file_path, 'r', encoding='utf-8') as file:
        content = file.read()

    # 使用新路径替换旧路径
    updated_content = content.replace(old_ref, new_ref)

    with open(file_path, 'w', encoding='utf-8') as file:
        file.write(updated_content)
    print(f"已修复文件 '{file_path}' 中的引用: '{old_ref}' -> '{new_ref}'")


def check_and_fix_image_references(md_folder, image_folder, used_image_folder=None):
    """
    检查并修复Markdown文件中的图片引用。

    :param md_folder: Markdown文件夹路径
    :param image_folder: 图片文件夹路径
    :param used_image_folder: 已使用图片要复制到的新文件夹路径
    :return: 未被引用的图片、无法修复的错误引用和已修复的引用列表
    """
    images = get_all_images(image_folder)
    image_set = set(images)
    used_image_map = {build_used_image_name(rel_path): rel_path for rel_path in images}
    referenced_images = set()
    incorrect_references = []
    fixed_references = []
    md_file_count = 0

    print("开始检查和修复Markdown文件...")
    for root, _, files in os.walk(md_folder):
        for file in files:
            if file.endswith('.md'):
                md_file_count += 1
                file_path = os.path.join(root, file)
                print(f"正在检查文件: {file_path}")
                with open(file_path, 'r', encoding='utf-8') as f:
                    content = f.read()
                    # 查找Markdown文件中所有图片引用
                    img_references = re.findall(r'!\[.*?\]\((.*?)\)', content)
                    for raw_ref in img_references:
                        source_rel_path, dest_name, _state = resolve_image_reference(
                            file_path,
                            image_folder,
                            used_image_folder,
                            raw_ref,
                            image_set,
                            used_image_map,
                        )
                        if source_rel_path:
                            referenced_images.add(os.path.normpath(source_rel_path))
                            if used_image_folder:
                                new_ref = os.path.relpath(
                                    os.path.join(used_image_folder, dest_name),
                                    os.path.dirname(file_path),
                                ).replace('\\', '/')
                                if raw_ref != new_ref:
                                    fix_image_reference(file_path, raw_ref, new_ref)
                                    fixed_references.append((file_path, raw_ref, new_ref))
                        else:
                            if not is_external_image_reference(normalize_image_reference(raw_ref)):
                                incorrect_references.append((file_path, raw_ref))

    print(f"检查完成。共检查了 {md_file_count} 个Markdown文件。")

    unreferenced_images = set(images) - referenced_images
    copied_images = copy_used_images(image_folder, used_image_folder, referenced_images)
    return unreferenced_images, incorrect_references, fixed_references, copied_images


def generate_report(md_folder, image_folder, used_image_folder, unreferenced, incorrect, fixed, copied_images):
    """
    生成检查报告并保存为文本文件。

    :param md_folder: Markdown文件夹路径
    :param image_folder: 图片文件夹路径
    :param used_image_folder: 已使用图片输出文件夹
    :param unreferenced: 未被引用的图片列表
    :param incorrect: 无法修复的错误引用列表
    :param fixed: 已修复的引用列表
    :param copied_images: 已复制的图片列表
    """
    report = []
    report.append("图片引用检查报告")
    report.append("=" * 20)
    report.append(f"生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    report.append(f"Markdown文件夹: {md_folder}")
    report.append(f"图片文件夹: {image_folder}")
    report.append(f"已使用图片输出文件夹: {used_image_folder or '未启用'}")
    report.append("\n1. 未被引用的图片")
    report.append("-" * 20)
    if unreferenced:
        for image in unreferenced:
            report.append(f"- {image}")
        report.append("\n建议：检查这些图片是否需要在Markdown文件中引用，或者考虑删除未使用的图片。")
    else:
        report.append("所有图片都已被引用。")

    report.append("\n2. 无法修复的错误引用")
    report.append("-" * 20)
    if incorrect:
        for md_file, ref in incorrect:
            report.append(f"- 文件 '{md_file}' 中的引用 '{ref}' 无效且无法自动修复")
        report.append("\n建议：手动检查这些Markdown文件，修正图片的引用路径。")
    else:
        report.append("没有发现无法修复的错误引用。")

    report.append("\n3. 已统一到公共图片目录的引用")
    report.append("-" * 20)
    if fixed:
        for md_file, old_ref, new_ref in fixed:
            report.append(f"- 文件 '{md_file}' 中的引用从 '{old_ref}' 修改为 '{new_ref}'")
    else:
        report.append("没有需要自动修复的引用。")

    report.append("\n4. 已复制的图片")
    report.append("-" * 20)
    if copied_images:
        report.append(f"共复制 {len(copied_images)} 张图片到新的文件夹。")
    else:
        report.append("没有可复制的已使用图片。")

    report.append("\n注意：此脚本检查并修复了图片引用的有效性。如果图片以其他方式被使用（如CSS背景图），可能仍不会被检测到。")

    # 保存报告
    report_path = os.path.join(md_folder, "image_reference_report.txt")
    with open(report_path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(report))

    print(f"\n报告已生成：{report_path}")


# 使用示例
if __name__ == "__main__":
    # 用户修改为自己的文件路径
    md_folder = 'D:/GitFiles/aubo_notes/simple_files'  # Markdown文件夹路径
    image_folder = 'D:/GitFiles/aubo_notes/Pictures'  # 图片文件夹路径
    used_image_folder = os.path.join(md_folder, 'used_images')  # 已使用图片输出文件夹

    print(f"Markdown文件夹: {md_folder}")
    print(f"图片文件夹: {image_folder}")
    print(f"已使用图片输出文件夹: {used_image_folder}")

    unreferenced, incorrect, fixed, copied_images = check_and_fix_image_references(
        md_folder,
        image_folder,
        used_image_folder,
    )

    # 生成报告
    generate_report(md_folder, image_folder, used_image_folder, unreferenced, incorrect, fixed, copied_images)

    print("\n检查和修复过程已完成。详细信息请查看生成的报告文件。")
