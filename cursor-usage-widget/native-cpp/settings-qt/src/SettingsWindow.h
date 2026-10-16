#pragma once
#include <QMainWindow>
#include <QStackedWidget>
#include <QListWidget>
#include <QCheckBox>
#include <QLineEdit>
#include <QPushButton>
#include <QLabel>
#include <QCloseEvent>
#include <QKeyEvent>
#include <QButtonGroup>
#include <QComboBox>
#include "ConfigStore.h"

class SettingsWindow : public QMainWindow {
    Q_OBJECT
public:
    explicit SettingsWindow(QWidget* parent = nullptr);

protected:
    void closeEvent(QCloseEvent* e) override;
    void keyPressEvent(QKeyEvent* e) override;

private slots:
    void onNavChanged(int row);
    void onGeneralChanged();
    void onWallpaperChanged();
    void onClearClicked();
    void onWallpaperNow();

private:
    void buildUi();
    QWidget* buildGeneralPage();
    QWidget* buildShortcutsPage();
    QWidget* buildWallpaperPage();
    QWidget* buildClearPage();
    QWidget* makeCard(QWidget* inner);
    QLabel* sectionTitle(const QString& text);
    void loadFromConfig();
    void applyStyle();
    void persistAndNotify();

    QListWidget* nav_ = nullptr;
    QStackedWidget* stack_ = nullptr;

    QButtonGroup* dockGroup_ = nullptr;
    QCheckBox* autoHide_ = nullptr;
    QCheckBox* showModels_ = nullptr;
    QCheckBox* showApi_ = nullptr;
    QCheckBox* showBot_ = nullptr;
    QButtonGroup* ringGroup_ = nullptr;

    QCheckBox* wpEnabled_ = nullptr;
    QLineEdit* wpUrl_ = nullptr;
    QComboBox* wpInterval_ = nullptr;
    QPushButton* wpNowBtn_ = nullptr;
    QWidget* wpExtra_ = nullptr;

    QCheckBox* clrLocal_ = nullptr;
    QCheckBox* clrArchived_ = nullptr;
    QCheckBox* clrCache_ = nullptr;
    QCheckBox* clrSidebar_ = nullptr;
    QPushButton* clrBtn_ = nullptr;

    bool loading_ = false;
};
