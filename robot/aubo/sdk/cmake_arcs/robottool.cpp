#include "robottool.h"
#include <cmath> // 包含数学库，以使用 M_PI

robotTool::robotTool()
{
}

std::vector<double> robotTool::deg2Rad(std::vector<double> robotDeg)
{
    std::vector<double> robotRad; // 用于保存转换后的弧度值

    // 遍历输入的角度向量
    for (double degree : robotDeg) {
        // 将角度转换为弧度，并添加到结果向量中
        double radian = degree * (M_PI / 180.0);
        robotRad.push_back(radian);
    }

    return robotRad; // 返回转换后的向量
}

std::vector<double> robotTool::rad2Deg(std::vector<double> robotRad)
{
    std::vector<double> robotDeg; // 用于保存转换后的角度值

    // 遍历输入的弧度向量
    for (double radian : robotRad) {
        // 将弧度转换为角度，并添加到结果向量中
        double degree = radian * (180.0 / M_PI);
        robotDeg.push_back(degree);
    }

    return robotDeg; // 返回转换后的向量
}

std::vector<double> robotTool::convertQStringToDoubleArray(const QString &input)
{
    // 使用空格或其他分隔符分割字符串
    QStringList stringList = input.split(",", QString::SkipEmptyParts);
    std::vector<double> doubleArray;

    // 将每个子字符串转换为浮点数
    for (const QString &str : stringList) {
        bool ok;
        double value = str.toDouble(&ok); // 使用 toDouble 转换为双精度浮点数
        if (ok) {                         // 如果转换成功
            doubleArray.push_back(value); // 添加到 vector 中
        } else {
            qDebug() << "Conversion error for string:" << str; // 输出错误信息
        }
    }

    return doubleArray;
}

QString robotTool::vectorToQString(const std::vector<double> &vec)
{
    QString result;
    for (size_t i = 0; i < vec.size(); ++i) {
        // 将 double 转换为 QString
        result += QString::number(vec[i]);

        // 如果不是最后一个元素，可以添加分隔符（例如逗号或空格）
        if (i < vec.size() - 1) {
            result += ", "; // 添加一个逗号作为分隔符
        }
    }
    return result;
}
