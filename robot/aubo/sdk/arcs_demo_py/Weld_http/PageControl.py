from flask import Flask, render_template, request, redirect, url_for
import json
import robotService

app = Flask(__name__)

# 用于存储配置信息的字典
configuration = {}


@app.route('/')
def index():
    return render_template('index.html', config=configuration)


@app.route('/update', methods=['POST'])
def update_config():
    # 获取表单数据
    robot_ip = request.form.get('robot_ip')
    procedure_id = request.form.get('procedure_id')
    continuous_weld_len = request.form.get('continuous_weld_len')
    continuous_welding = request.form.get('continuous_welding') == 'on'

    # 更新配置字典
    configuration['robot_ip'] = str(robot_ip)
    configuration['procedure_id'] = int(procedure_id)
    configuration['continuous_weld_len'] = float(continuous_weld_len)
    configuration['continuous_welding'] = continuous_welding

    # 可选择将配置保存到文件
    with open('config.json', 'w') as f:
        json.dump(configuration, f)

    return redirect(url_for('index'))


if __name__ == '__main__':
    # app.run(debug=True)
    app.run(host='0.0.0.0', port=9090, debug=True)
    # robotService.weldServiceStart()

