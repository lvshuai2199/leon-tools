import os
import re
from datetime import datetime

def get_all_images(image_folder):
    """
    获取图片文件夹及其子文件夹中所有图片的相对路径。

    :param image_folder: 图片文件夹路径
    :return: 图片相对路径列表
    """
    image_extensions = {'.png', '.jpg', '.jpeg', '.gif', '.bmp', '.svg'}
    images = []
    for root, _, files in os.walk(image_folder):
        for file in files:
            if os.path.splitext(file.lower())[1] in image_extensions:
                images.append(os.path.relpath(os.path.join(root, file), image_folder))
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
            return os.path.relpath(os.path.join(root, image_name), image_folder)
    return None


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


def check_and_fix_image_references(md_folder, image_folder):
    """
    检查并修复Markdown文件中的图片引用。

    :param md_folder: Markdown文件夹路径
    :param image_folder: 图片文件夹路径
    :return: 未被引用的图片、无法修复的错误引用和已修复的引用列表
    """
    images = get_all_images(image_folder)
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
                    for ref in img_references:
                        # 去除 URL 参数
                        ref = ref.split('?')[0]
                        # 计算引用图片的绝对路径
                        abs_ref_path = os.path.abspath(os.path.join(os.path.dirname(file_path), ref))
                        # 检查引用是否在图片列表中
                        rel_path = os.path.relpath(abs_ref_path, image_folder)
                        if rel_path in images:
                            referenced_images.add(rel_path)
                        else:
                            # 查找正确路径并尝试修复
                            correct_path = find_correct_image_path(image_folder, ref)
                            if correct_path:
                                rel_correct_path = os.path.relpath(os.path.join(image_folder, correct_path), os.path.dirname(file_path))
                                rel_correct_path = rel_correct_path.replace('\\', '/')  # 用正斜杠替换
                                fix_image_reference(file_path, ref, rel_correct_path)
                                fixed_references.append((file_path, ref, rel_correct_path))
                            else:
                                incorrect_references.append((file_path, ref))

    print(f"检查完成。共检查了 {md_file_count} 个Markdown文件。")

    unreferenced_images = set(images) - referenced_images
    return unreferenced_images, incorrect_references, fixed_references


def generate_report(md_folder, image_folder, unreferenced, incorrect, fixed, out_folder):
    """
    生成检查报告并保存为文本文件。

    :param md_folder: Markdown文件夹路径
    :param image_folder: 图片文件夹路径
    :param unreferenced: 未被引用的图片列表
    :param incorrect: 无法修复的错误引用列表
    :param fixed: 已修复的引用列表
    """
    report = []
    report.append("图片引用检查报告")
    report.append("=" * 20)
    report.append(f"生成时间: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    report.append(f"Markdown文件夹: {md_folder}")
    report.append(f"图片文件夹: {image_folder}")
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

    report.append("\n3. 已自动修复的引用")
    report.append("-" * 20)
    if fixed:
        for md_file, old_ref, new_ref in fixed:
            report.append(f"- 文件 '{md_file}' 中的引用从 '{old_ref}' 修改为 '{new_ref}'")
    else:
        report.append("没有需要自动修复的引用。")

    report.append("\n注意：此脚本检查并修复了图片引用的有效性。如果图片以其他方式被使用（如CSS背景图），可能仍不会被检测到。")

    # 保存报告
    report_path = os.path.join(out_folder, "image_reference_report.txt")
    with open(report_path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(report))

    print(f"\n报告已生成：{report_path}")


# 使用示例
if __name__ == "__main__":
    # 用户修改为自己的文件路径
    md_folder = 'D:/GitFiles/typoraFiles'  # Markdown文件夹路径
    image_folder = 'D:/GitFiles/typoraFiles/Pictures/imgs'  # 图片文件夹路径

    print(f"Markdown文件夹: {md_folder}")
    print(f"图片文件夹: {image_folder}")

    unreferenced, incorrect, fixed = check_and_fix_image_references(md_folder, image_folder)

    # 生成报告
    generate_report(md_folder, image_folder, unreferenced, incorrect, fixed)

    print("\n检查和修复过程已完成。详细信息请查看生成的报告文件。")
