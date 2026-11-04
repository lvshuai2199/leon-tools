#ifndef ROBOTTOOL_H
#define ROBOTTOOL_H

#include "aubo_sdk/rpc.h"

#include <QString>
#include <QStringList>
#include <QDebug>

#include <QString>
#include <vector>
#include <sstream> // 用于字符串流

class robotTool
{
public:
    robotTool();

public:
    /**
     * 角度转弧度
     * @brief deg2Rad
     * @param robotDeg
     * @return
     */
    std::vector<double> deg2Rad(std::vector<double> robotDeg);
    /**
     * 弧度转角度
     * @brief rad2Deg
     * @param robotRad
     * @return
     */
    std::vector<double> rad2Deg(std::vector<double> robotRad);
    /**
     * QString转换为double数组
     * @brief convertQStringToDoubleArray
     * @param input
     * @return
     */
    std::vector<double> convertQStringToDoubleArray(const QString &input);
    /**
     * vector转换为QString
     * @brief vectorToQString
     * @param vec
     * @return
     */
    QString vectorToQString(const std::vector<double> &vec);
};

#endif // ROBOTTOOL_H
