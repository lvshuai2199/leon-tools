#ifndef FORM_H
#define FORM_H

#include <QWidget>
#include <QMessageBox>

#include "robotcontrol.h"

namespace Ui {
class Form;
}

class Form : public QWidget
{
    Q_OBJECT

public:
    explicit Form(QWidget *parent = nullptr);
    ~Form();

private slots:
    void on_initBtn_clicked();

    void on_getPoseBtn_clicked();

    void on_inverseBtn_clicked();

    void on_jointMoveBtn_clicked();

    void on_moveStop_btn_clicked();

    void on_moveFastStop_btn_clicked();

    void on_jointMoveBtn_2_pressed();

    void on_jointMoveBtn_2_released();

private:
    Ui::Form *ui;

    robotControl *robot;
};

#endif // FORM_H
