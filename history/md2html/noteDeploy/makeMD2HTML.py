import os
import shutil
import markdown
from bs4 import BeautifulSoup
import urllib.parse


def convert_md_to_html(md_dir, html_dir):
    html_template = """
    <!DOCTYPE html>
    <html lang="en">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>{title}</title>
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/github-markdown-css/5.1.0/github-markdown.min.css">
        <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.5.1/styles/default.min.css">
        <style>
            body {{
                font-family: Arial, sans-serif;
                line-height: 1.6;
                color: #333;
                max-width: 800px;
                margin: 0 auto;
                padding: 20px;
            }}
            nav {{
                margin-bottom: 20px;
                padding: 10px;
                background-color: #f0f0f0;
                border-radius: 5px;
            }}
            nav a {{
                color: #0066cc;
                text-decoration: none;
                margin-right: 5px;
            }}
            nav a:hover {{
                text-decoration: underline;
            }}
            .markdown-body {{
                box-sizing: border-box;
                min-width: 200px;
                max-width: 980px;
                margin: 0 auto;
                padding: 45px;
            }}
            @media (max-width: 767px) {{
                .markdown-body {{
                    padding: 15px;
                }}
            }}
        </style>
    </head>
    <body>
        <nav>{nav}</nav>
        <div class="markdown-body">
            {content}
        </div>
        <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.5.1/highlight.min.js"></script>
        <script>
            document.addEventListener('DOMContentLoaded', (event) => {{
                document.querySelectorAll('pre code').forEach((block) => {{
                    hljs.highlightBlock(block);
                }});
            }});
        </script>
    </body>
    </html>
    """

    for root, dirs, files in os.walk(md_dir):
        rel_path = os.path.relpath(root, md_dir)
        output_dir = os.path.join(html_dir, rel_path)
        if not os.path.exists(output_dir):
            os.makedirs(output_dir)

        for file in files:
            if file.endswith('.md'):
                md_file_path = os.path.join(root, file)
                html_file_name = file.replace('.md', '.html')
                html_file_path = os.path.join(output_dir, html_file_name)

                try:
                    with open(md_file_path, 'r', encoding='utf-8') as f:
                        md_content = f.read()

                    html_content = markdown.markdown(md_content, extensions=['fenced_code', 'codehilite'])

                    nav_path = create_nav_path(rel_path, file)
                    title = os.path.splitext(file)[0]

                    full_html_content = html_template.format(
                        title=title,
                        nav=nav_path,
                        content=html_content
                    )

                    with open(html_file_path, 'w', encoding='utf-8') as f:
                        f.write(full_html_content)

                    print(f"Converted: {md_file_path} to {html_file_path}")

                except Exception as e:
                    print(f"Failed to convert {md_file_path}: {e}")

    # Update HTML links within HTML files
    for root, dirs, files in os.walk(html_dir):
        for file in files:
            if file.endswith('.html'):
                file_path = os.path.join(root, file)
                try:
                    with open(file_path, 'r', encoding='utf-8') as f:
                        content = f.read()

                    soup = BeautifulSoup(content, 'html.parser')

                    for link in soup.find_all('a'):
                        href = link.get('href')
                        if href and href.endswith('.md'):
                            link['href'] = href.replace('.md', '.html')

                    with open(file_path, 'w', encoding='utf-8') as f:
                        f.write(str(soup))

                except Exception as e:
                    print(f"Failed to process links in {file_path}: {e}")

    print("转换完成！")

def create_nav_path(rel_path, filename):
    parts = rel_path.split(os.sep)
    nav_links = ['<a href="/">Root</a>']
    current_path = ''
    for part in parts:
        if part != '.':
            current_path = os.path.join(current_path, part).replace(os.sep, '/')
            encoded_path = urllib.parse.quote(current_path)
            nav_links.append(f'<a href="/{encoded_path}/index.html">{part}</a>')

    # 添加当前文件名（不包含 .html 扩展名）
    file_without_ext = os.path.splitext(filename)[0]
    encoded_filename = urllib.parse.quote(file_without_ext + '.html')
    nav_links.append(f'<a href="{encoded_filename}">{file_without_ext}</a>')

    return ' / '.join(nav_links)

def create_index_html(html_dir):
    import os
    import urllib.parse

    def create_directory_structure(path):
        structure = {}
        for root, dirs, files in os.walk(path):
            dirs[:] = [d for d in dirs if not d.startswith('.')]
            rel_path = os.path.relpath(root, path)
            current = structure
            if rel_path != '.':
                for part in rel_path.split(os.sep):
                    current = current.setdefault(part, {})
            for file in files:
                if file.endswith('.html') and file != 'index.html':
                    current[file] = None
            for dir in dirs:
                if dir not in current:
                    current[dir] = {}
        return structure

    def generate_html(structure, current_path=''):
        html = '<ul class="file-tree">\n'
        for key, value in sorted(structure.items()):
            if value is None:
                file_path = os.path.join(current_path, key).replace(os.sep, '/')
                html += f'<li class="file"><span class="file-icon">📄</span> <a href="{urllib.parse.quote(file_path)}">{key}</a></li>\n'
            else:
                dir_path = os.path.join(current_path, key).replace(os.sep, '/')
                html += ('<li class="directory">'
                         f'<div class="folder-header" onclick="toggleFolder(this)">'
                         f'<span class="folder-icon">📁</span> {key}/'
                         f'<span class="toggle-icon">▼</span>'
                         '</div>\n'
                         f'<ul class="nested">{generate_html(value, os.path.join(current_path, key).replace(os.sep, "/"))}</ul>'
                         '</li>\n')
        html += '</ul>\n'
        return html

    def create_index_file(current_dir, current_structure, rel_path=''):
        index_content = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Index of {}</title>
            <style>
                body {{
                    font-family: Arial, sans-serif;
                    line-height: 1.6;
                    color: #333;
                    max-width: 800px;
                    margin: 0 auto;
                    padding: 20px;
                    background-color: #f4f4f4;
                }}
                h1 {{
                    color: #2c3e50;
                    border-bottom: 2px solid #3498db;
                    padding-bottom: 10px;
                }}
                .file-tree {{
                    list-style-type: none;
                    padding-left: 20px;
                }}
                .file-tree li {{
                    margin: 5px 0;
                }}
                .file-tree a {{
                    text-decoration: none;
                    color: #2980b9;
                }}
                .file-tree a:hover {{
                    text-decoration: underline;
                }}
                .folder-header {{
                    cursor: pointer;
                    display: flex;
                    align-items: center;
                }}
                .folder-icon, .file-icon {{
                    margin-right: 5px;
                }}
                .toggle-icon {{
                    margin-left: 5px;
                    transition: transform 0.3s;
                    color: #666; /* 调整箭头的颜色 */
                    font-size: 0.8em; /* 调整箭头的大小 */
                }}
                .nested {{
                    display: none;
                    padding-left: 20px;
                }}
                .open > .folder-header > .toggle-icon {{
                    transform: rotate(180deg);
                }}
                .open > .nested {{
                    display: block;
                }}
                @media (max-width: 600px) {{
                    body {{
                        padding: 10px;
                    }}
                    h1 {{
                        font-size: 1.5em;
                    }}
                    .file-tree {{
                        padding-left: 10px;
                    }}
                }}
            </style>
            <script>
                function toggleFolder(element) {{
                    var parentLi = element.parentElement;
                    parentLi.classList.toggle('open');
                }}
            </script>
        </head>
        <body>
            <h1>Index of {}</h1>
        """.format(rel_path or 'Root', rel_path or 'Root')

        index_content += generate_html(current_structure)
        index_content += """
        </body>
        </html>
        """

        index_file_path = os.path.join(current_dir, 'index.html')
        with open(index_file_path, 'w', encoding='utf-8') as f:
            f.write(index_content)

        for key, value in current_structure.items():
            if isinstance(value, dict):
                next_dir = os.path.join(current_dir, key)
                next_rel_path = os.path.join(rel_path, key).replace(os.sep, '/')
                create_index_file(next_dir, value, next_rel_path)

    structure = create_directory_structure(html_dir)
    create_index_file(html_dir, structure)
    print(f"Index files created in all directories, excluding hidden folders.")

def moveImg2Place(imgDir,targetDir):
    print("复制文件到指定位置中...")

    try:
        # 检查源目录是否存在
        if not os.path.exists(imgDir):
            print(f"源目录 {imgDir} 不存在！")
            return

        # 如果目标目录不存在，则创建
        if not os.path.exists(targetDir):
            os.makedirs(targetDir)
            print(f"目标目录 {targetDir} 已创建！")

        # 获取源目录的名称
        folder_name = os.path.basename(os.path.normpath(imgDir))

        # 构造目标路径
        target_path = os.path.join(targetDir, folder_name)

        # 如果目标路径已存在，直接删除
        if os.path.exists(target_path):
            print(f"目标位置 {target_path} 已经存在，正在覆盖...")
            shutil.rmtree(target_path)  # 删除已存在的目录

        # 使用 shutil.copytree 复制整个文件夹
        shutil.copytree(imgDir, target_path)
        print(f"成功将文件夹从 {imgDir} 复制到 {target_path}！")

    except Exception as e:
        print(f"发生错误：{e}")

if __name__ == '__main__':
    # 使用示例
    md_directory = 'D:/GitFiles/typoraFiles'
    html_directory = 'C:/Users/13326/Desktop/测试文件/notes'
    convert_md_to_html(md_directory, html_directory)
    create_index_html(html_directory)
