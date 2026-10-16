#include "SettingsWindow.h"
#include "HostBridge.h"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QFrame>
#include <QMessageBox>
#include <QScrollArea>
#include <QRadioButton>
#include <QApplication>

namespace {
constexpr int kNavW = 120;
constexpr int kWinW = 520;
constexpr int kWinH = 560;
constexpr int kRightPad = 16;
constexpr int kCardGap = 12;
constexpr int kCardPad = 12;
constexpr int kRowH = 28;
constexpr int kRowGap = 8;
constexpr int kChipGap = 8;
constexpr int kBtnW = 96;
constexpr int kBtnH = 28;
}

SettingsWindow::SettingsWindow(QWidget* parent) : QMainWindow(parent) {
    setWindowTitle(QStringLiteral("设置"));
    setObjectName(QStringLiteral("CursorUsageSettingsQt"));
    setWindowFlags(Qt::Tool | Qt::FramelessWindowHint | Qt::WindowStaysOnTopHint);
    setAttribute(Qt::WA_DeleteOnClose, false);
    resize(kWinW, kWinH);
    setMinimumSize(kWinW, 420);
    buildUi();
    applyStyle();
    loadFromConfig();
}

void SettingsWindow::closeEvent(QCloseEvent* e) {
    // Hide only — do not quit the tray (settings is a separate process; just exit this process).
    e->accept();
    QApplication::quit();
}

QLabel* SettingsWindow::sectionTitle(const QString& text) {
    auto* lab = new QLabel(text);
    lab->setObjectName("sectionTitle");
    lab->setAlignment(Qt::AlignLeft | Qt::AlignVCenter);
    return lab;
}

QWidget* SettingsWindow::makeCard(QWidget* inner) {
    auto* card = new QFrame;
    card->setObjectName("card");
    auto* lay = new QVBoxLayout(card);
    lay->setContentsMargins(kCardPad, kCardPad, kCardPad, kCardPad);
    lay->setSpacing(kRowGap);
    lay->addWidget(inner);
    return card;
}

void SettingsWindow::buildUi() {
    auto* root = new QWidget;
    setCentralWidget(root);
    auto* rootLay = new QHBoxLayout(root);
    rootLay->setContentsMargins(0, 0, 0, 0);
    rootLay->setSpacing(0);

    nav_ = new QListWidget;
    nav_->setObjectName("nav");
    nav_->setFixedWidth(kNavW);
    nav_->addItems({QStringLiteral("常规"), QStringLiteral("快捷方式"),
                    QStringLiteral("壁纸"), QStringLiteral("清除数据")});
    nav_->setCurrentRow(0);
    connect(nav_, &QListWidget::currentRowChanged, this, &SettingsWindow::onNavChanged);

    stack_ = new QStackedWidget;
    stack_->addWidget(buildGeneralPage());
    stack_->addWidget(buildShortcutsPage());
    stack_->addWidget(buildWallpaperPage());
    stack_->addWidget(buildClearPage());

    rootLay->addWidget(nav_);
    rootLay->addWidget(stack_, 1);
}

static QWidget* wrapScroll(QWidget* content) {
    auto* scroll = new QScrollArea;
    scroll->setWidgetResizable(true);
    scroll->setFrameShape(QFrame::NoFrame);
    scroll->setHorizontalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
    auto* holder = new QWidget;
    auto* lay = new QVBoxLayout(holder);
    lay->setContentsMargins(kRightPad, 16, kRightPad, 16);
    lay->setSpacing(kCardGap);
    lay->addWidget(content);
    // content may already be a column of cards — rebuild: caller passes column widget
    scroll->setWidget(holder);
    return scroll;
}

QWidget* SettingsWindow::buildGeneralPage() {
    auto* col = new QWidget;
    auto* lay = new QVBoxLayout(col);
    lay->setContentsMargins(0, 0, 0, 0);
    lay->setSpacing(kCardGap);

    // Dock card
    {
        auto* inner = new QWidget;
        auto* v = new QVBoxLayout(inner);
        v->setContentsMargins(0, 0, 0, 0);
        v->setSpacing(kRowGap);
        v->addWidget(sectionTitle(QStringLiteral("贴边")));
        auto* row = new QHBoxLayout;
        row->setSpacing(kChipGap);
        row->setAlignment(Qt::AlignLeft);
        dockGroup_ = new QButtonGroup(this);
        const char* labels[] = {"左", "右", "上"};
        for (int i = 0; i < 3; ++i) {
            auto* b = new QPushButton(QString::fromUtf8(labels[i]));
            b->setCheckable(true);
            b->setFixedHeight(kRowH);
            b->setMinimumWidth(56);
            b->setObjectName("chip");
            dockGroup_->addButton(b, i);
            row->addWidget(b);
            connect(b, &QPushButton::clicked, this, &SettingsWindow::onGeneralChanged);
        }
        row->addStretch();
        v->addLayout(row);
        lay->addWidget(makeCard(inner));
    }

    // Auto-hide
    {
        auto* inner = new QWidget;
        auto* v = new QVBoxLayout(inner);
        v->setContentsMargins(0, 0, 0, 0);
        autoHide_ = new QCheckBox(QStringLiteral("贴边自动隐藏"));
        autoHide_->setFixedHeight(kRowH);
        connect(autoHide_, &QCheckBox::toggled, this, &SettingsWindow::onGeneralChanged);
        v->addWidget(autoHide_);
        lay->addWidget(makeCard(inner));
    }

    // Usage rings visibility
    {
        auto* inner = new QWidget;
        auto* v = new QVBoxLayout(inner);
        v->setContentsMargins(0, 0, 0, 0);
        v->setSpacing(kRowGap);
        v->addWidget(sectionTitle(QStringLiteral("用量环显示")));
        showModels_ = new QCheckBox(QStringLiteral("Models"));
        showApi_ = new QCheckBox(QStringLiteral("API / 总量"));
        showBot_ = new QCheckBox(QStringLiteral("Bot"));
        for (auto* c : {showModels_, showApi_, showBot_}) {
            c->setFixedHeight(kRowH);
            v->addWidget(c);
            connect(c, &QCheckBox::toggled, this, &SettingsWindow::onGeneralChanged);
        }
        lay->addWidget(makeCard(inner));
    }

    // Ring mode
    {
        auto* inner = new QWidget;
        auto* v = new QVBoxLayout(inner);
        v->setContentsMargins(0, 0, 0, 0);
        v->setSpacing(kRowGap);
        v->addWidget(sectionTitle(QStringLiteral("环模式")));
        auto* row = new QHBoxLayout;
        row->setSpacing(kChipGap);
        row->setAlignment(Qt::AlignLeft);
        ringGroup_ = new QButtonGroup(this);
        auto* b0 = new QPushButton(QStringLiteral("四环"));
        auto* b1 = new QPushButton(QStringLiteral("双环"));
        for (auto* b : {b0, b1}) {
            b->setCheckable(true);
            b->setFixedHeight(kRowH);
            b->setMinimumWidth(56);
            b->setObjectName("chip");
            row->addWidget(b);
            connect(b, &QPushButton::clicked, this, &SettingsWindow::onGeneralChanged);
        }
        ringGroup_->addButton(b0, 0);
        ringGroup_->addButton(b1, 1);
        row->addStretch();
        v->addLayout(row);
        lay->addWidget(makeCard(inner));
    }

    lay->addStretch();

    auto* scroll = new QScrollArea;
    scroll->setWidgetResizable(true);
    scroll->setFrameShape(QFrame::NoFrame);
    scroll->setHorizontalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
    auto* holder = new QWidget;
    auto* hl = new QVBoxLayout(holder);
    hl->setContentsMargins(kRightPad, 16, kRightPad, 16);
    hl->setSpacing(0);
    hl->addWidget(col);
    scroll->setWidget(holder);
    return scroll;
}

QWidget* SettingsWindow::buildShortcutsPage() {
    auto* page = new QWidget;
    auto* lay = new QVBoxLayout(page);
    lay->setContentsMargins(kRightPad, 16, kRightPad, 16);
    auto* cardInner = new QLabel(QStringLiteral("快捷方式管理仍由托盘进程处理（本页占位）。\n请暂时使用旧版设置或后续里程碑迁移。"));
    cardInner->setWordWrap(true);
    cardInner->setAlignment(Qt::AlignLeft | Qt::AlignTop);
    lay->addWidget(makeCard(cardInner));
    lay->addStretch();
    return page;
}

QWidget* SettingsWindow::buildWallpaperPage() {
    auto* col = new QWidget;
    auto* lay = new QVBoxLayout(col);
    lay->setContentsMargins(0, 0, 0, 0);
    lay->setSpacing(kCardGap);

    auto* inner = new QWidget;
    auto* v = new QVBoxLayout(inner);
    v->setContentsMargins(0, 0, 0, 0);
    v->setSpacing(kRowGap);
    v->addWidget(sectionTitle(QStringLiteral("定时更换桌面壁纸")));
    wpEnabled_ = new QCheckBox(QStringLiteral("启用"));
    wpEnabled_->setFixedHeight(kRowH);
    connect(wpEnabled_, &QCheckBox::toggled, this, &SettingsWindow::onWallpaperChanged);
    v->addWidget(wpEnabled_);

    wpExtra_ = new QWidget;
    auto* ev = new QVBoxLayout(wpExtra_);
    ev->setContentsMargins(0, 0, 0, 0);
    ev->setSpacing(kRowGap);
    wpUrl_ = new QLineEdit;
    wpUrl_->setPlaceholderText(QStringLiteral("粘贴管理端复制的接口地址"));
    wpUrl_->setFixedHeight(kRowH);
    connect(wpUrl_, &QLineEdit::editingFinished, this, &SettingsWindow::onWallpaperChanged);
    ev->addWidget(wpUrl_);
    v->addWidget(wpExtra_);
    lay->addWidget(makeCard(inner));
    lay->addStretch();

    auto* scroll = new QScrollArea;
    scroll->setWidgetResizable(true);
    scroll->setFrameShape(QFrame::NoFrame);
    auto* holder = new QWidget;
    auto* hl = new QVBoxLayout(holder);
    hl->setContentsMargins(kRightPad, 16, kRightPad, 16);
    hl->addWidget(col);
    scroll->setWidget(holder);
    return scroll;
}

QWidget* SettingsWindow::buildClearPage() {
    auto* col = new QWidget;
    auto* lay = new QVBoxLayout(col);
    lay->setContentsMargins(0, 0, 0, 0);
    lay->setSpacing(kCardGap);

    auto* inner = new QWidget;
    auto* v = new QVBoxLayout(inner);
    v->setContentsMargins(0, 0, 0, 0);
    v->setSpacing(kRowGap);
    v->addWidget(sectionTitle(QStringLiteral("清除数据")));

    auto* notice = new QLabel(
        QStringLiteral("当前清除仅针对本地 Composer 对话 / 已归档 / 可选本地缓存。\n"
                       "不会删除账号下的云端 Agents 与云端项目记录（左侧带云图标那栏）。\n"
                       "云端真正删除：暂不支持。"));
    notice->setObjectName("notice");
    notice->setWordWrap(true);
    notice->setAlignment(Qt::AlignLeft | Qt::AlignTop);
    v->addWidget(notice);

    clrLocal_ = new QCheckBox(QStringLiteral("清除本地对话（Composer/Agent）"));
    clrArchived_ = new QCheckBox(QStringLiteral("清除已归档对话"));
    clrCache_ = new QCheckBox(QStringLiteral("清除本地缓存（检索/索引/Agent 产物）"));
    clrSidebar_ = new QCheckBox(QStringLiteral("清除本地侧栏缓存（Projects 钉选 + Agents 本地缓存）"));
    for (auto* c : {clrLocal_, clrArchived_, clrCache_, clrSidebar_}) {
        c->setFixedHeight(kRowH);
        v->addWidget(c);
    }
    auto* risk = new QLabel(QStringLiteral("云端 Agents 重启或刷新后可能再次出现；不会删云端记录。Projects 本地钉选会被移除。"));
    risk->setObjectName("risk");
    risk->setWordWrap(true);
    v->addWidget(risk);

    clrBtn_ = new QPushButton(QStringLiteral("立即清除"));
    clrBtn_->setObjectName("dangerBtn");
    clrBtn_->setFixedSize(kBtnW, kBtnH);
    clrBtn_->setCursor(Qt::PointingHandCursor);
    connect(clrBtn_, &QPushButton::clicked, this, &SettingsWindow::onClearClicked);
    auto* btnRow = new QHBoxLayout;
    btnRow->setAlignment(Qt::AlignLeft);
    btnRow->addWidget(clrBtn_);
    btnRow->addStretch();
    v->addLayout(btnRow);

    lay->addWidget(makeCard(inner));
    lay->addStretch();

    auto* scroll = new QScrollArea;
    scroll->setWidgetResizable(true);
    scroll->setFrameShape(QFrame::NoFrame);
    auto* holder = new QWidget;
    auto* hl = new QVBoxLayout(holder);
    hl->setContentsMargins(kRightPad, 16, kRightPad, 16);
    hl->addWidget(col);
    scroll->setWidget(holder);
    return scroll;
}

void SettingsWindow::applyStyle() {
    setStyleSheet(QStringLiteral(R"(
        QMainWindow, QWidget { background: #F5F6F8; color: #1F2329; font-family: "Microsoft YaHei UI", "Segoe UI", sans-serif; font-size: 13px; }
        #nav { background: #EBEDF0; border: none; outline: none; padding: 10px 0; }
        #nav::item { height: 36px; padding-left: 12px; margin: 2px 10px; border-radius: 8px; }
        #nav::item:selected { background: #FFFFFF; }
        #card { background: #FFFFFF; border: 1px solid #E5E6EB; border-radius: 10px; }
        #sectionTitle { color: #8A8F98; font-size: 13px; font-weight: 500; }
        #notice { color: #4E5969; background: #F2F3F5; border-radius: 8px; padding: 10px; }
        #risk { color: #C47800; font-size: 12px; }
        QPushButton#chip { background: #F2F3F5; border: 1px solid #E5E6EB; border-radius: 6px; padding: 0 12px; text-align: left; }
        QPushButton#chip:checked { background: #E8F3FF; border-color: #3491FA; color: #165DFF; }
        QPushButton#dangerBtn { background: #F53F3F; color: white; border: none; border-radius: 6px; font-weight: 600; }
        QPushButton#dangerBtn:hover { background: #E33939; }
        QPushButton#dangerBtn:disabled { background: #E5E6EB; color: #A9AEB8; }
        QLineEdit { border: 1px solid #E5E6EB; border-radius: 6px; padding: 0 8px; background: #FFF; }
        QCheckBox { spacing: 8px; }
    )"));
}

void SettingsWindow::loadFromConfig() {
    loading_ = true;
    AppConfig c = ConfigStore::load();
    if (auto* b = dockGroup_->button(c.dockEdge)) b->setChecked(true);
    autoHide_->setChecked(c.autoHide);
    showModels_->setChecked(c.showModels);
    showApi_->setChecked(c.showApi);
    showBot_->setChecked(c.showBot);
    if (auto* b = ringGroup_->button(c.ringMode)) b->setChecked(true);
    wpEnabled_->setChecked(c.wpEnabled);
    wpUrl_->setText(c.wpUrl);
    wpExtra_->setVisible(c.wpEnabled);
    clrLocal_->setChecked(true);
    clrArchived_->setChecked(true);
    clrCache_->setChecked(false);
    clrSidebar_->setChecked(false);
    loading_ = false;
}

void SettingsWindow::persistAndNotify() {
    AppConfig c = ConfigStore::load();
    c.dockEdge = dockGroup_->checkedId() >= 0 ? dockGroup_->checkedId() : c.dockEdge;
    c.autoHide = autoHide_->isChecked();
    c.showModels = showModels_->isChecked();
    c.showApi = showApi_->isChecked();
    c.showBot = showBot_->isChecked();
    c.ringMode = ringGroup_->checkedId() >= 0 ? ringGroup_->checkedId() : c.ringMode;
    c.wpEnabled = wpEnabled_->isChecked();
    c.wpUrl = wpUrl_->text().trimmed();
    ConfigStore::save(c);
    HostBridge::notifyConfigChanged();
}

void SettingsWindow::onNavChanged(int row) {
    if (row >= 0) stack_->setCurrentIndex(row);
}

void SettingsWindow::onGeneralChanged() {
    if (loading_) return;
    persistAndNotify();
}

void SettingsWindow::onWallpaperChanged() {
    if (loading_) return;
    wpExtra_->setVisible(wpEnabled_->isChecked());
    persistAndNotify();
}

void SettingsWindow::onClearClicked() {
    ClearOptions opt;
    opt.local = clrLocal_->isChecked();
    opt.archived = clrArchived_->isChecked();
    opt.cache = clrCache_->isChecked();
    opt.sidebar = clrSidebar_->isChecked();
    if (!opt.local && !opt.archived && !opt.cache && !opt.sidebar) {
        QMessageBox::information(this, QStringLiteral("清除 Cursor 历史"),
                                 QStringLiteral("请先勾选要清除的项。"));
        return;
    }
    const auto ans = QMessageBox::question(
        this, QStringLiteral("确认清除 Cursor 历史"),
        QStringLiteral("确定清除吗？\n不会删除：登录态、settings.json、扩展与 MCP 配置、云端 Agents/项目记录。"),
        QMessageBox::Yes | QMessageBox::No, QMessageBox::No);
    if (ans != QMessageBox::Yes) return;
    if (!HostBridge::requestClearHistory(opt)) {
        QMessageBox::warning(this, QStringLiteral("清除失败"),
                             QStringLiteral("未找到运行中的托盘进程（CursorUsageWidget）。请先启动用量小窗。"));
    }
}
