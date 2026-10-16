#include "SettingsWindow.h"
#include "HostBridge.h"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QFrame>
#include <QMessageBox>
#include <QScrollArea>
#include <QApplication>
#include <QShortcut>
#include <QFileDialog>
#include <QDir>
#include <QFileIconProvider>
#include <QFileInfo>
#include <QDesktopServices>
#include <QUrl>
#include <QTimer>

namespace {
constexpr int kNavW = 120;
constexpr int kWinW = 700;
constexpr int kWinH = 520;
constexpr int kContentPadH = 24;  // left/right content margin
constexpr int kContentPadV = 20;  // top/bottom content margin
constexpr int kCardGap = 16;
constexpr int kCardPad = 16;
constexpr int kRowH = 30;
constexpr int kRowGap = 10;
constexpr int kChipGap = 10;
constexpr int kBtnW = 104;
constexpr int kBtnH = 32;
constexpr int kNavItemH = 40;
}

SettingsWindow::SettingsWindow(QWidget* parent) : QMainWindow(parent) {
    setWindowTitle(QStringLiteral("设置"));
    setObjectName(QStringLiteral("CursorUsageSettingsQt"));
    // System title bar + close; no FramelessWindowHint, no WindowStaysOnTopHint.
    setWindowFlags(Qt::Window);
    setAttribute(Qt::WA_DeleteOnClose, false);
    resize(kWinW, kWinH);
    setMinimumSize(700, 480);
    buildUi();
    applyStyle();
    loadFromConfig();
    reloadShortcutsList();
    // Esc closes (Alt+F4 is system default for Qt::Window)
    auto* esc = new QShortcut(QKeySequence(Qt::Key_Escape), this);
    connect(esc, &QShortcut::activated, this, &QWidget::close);
}

void SettingsWindow::closeEvent(QCloseEvent* e) {
    e->accept();
    QApplication::quit();
}

void SettingsWindow::keyPressEvent(QKeyEvent* e) {
    if (e->key() == Qt::Key_Escape) {
        close();
        return;
    }
    if (e->key() == Qt::Key_Delete && stack_ && stack_->currentIndex() == 1) {
        onShortcutDelete();
        return;
    }
    QMainWindow::keyPressEvent(e);
}

void SettingsWindow::changeEvent(QEvent* e) {
    QMainWindow::changeEvent(e);
    if (e->type() == QEvent::ActivationChange && isActiveWindow()) {
        // Tray manage dialog may have rewritten shortcuts.txt
        if (stack_ && stack_->currentIndex() == 1)
            reloadShortcutsList();
    }
}

bool SettingsWindow::eventFilter(QObject* obj, QEvent* e) {
    if (obj == scList_ && e->type() == QEvent::KeyPress) {
        auto* ke = static_cast<QKeyEvent*>(e);
        if (ke->key() == Qt::Key_Delete || ke->key() == Qt::Key_Backspace) {
            onShortcutDelete();
            return true;
        }
    }
    return QMainWindow::eventFilter(obj, e);
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

QWidget* SettingsWindow::wrapScroll(QWidget* col) {
    auto* scroll = new QScrollArea;
    scroll->setWidgetResizable(true);
    scroll->setFrameShape(QFrame::NoFrame);
    scroll->setHorizontalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
    auto* holder = new QWidget;
    auto* hl = new QVBoxLayout(holder);
    hl->setContentsMargins(kContentPadH, kContentPadV, kContentPadH, kContentPadV);
    hl->setSpacing(0);
    hl->addWidget(col);
    scroll->setWidget(holder);
    return scroll;
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

QWidget* SettingsWindow::buildGeneralPage() {
    auto* col = new QWidget;
    auto* lay = new QVBoxLayout(col);
    lay->setContentsMargins(0, 0, 0, 0);
    lay->setSpacing(kCardGap);

    // Dock card — chip order: 上｜左｜右 (ids: 0 left, 1 right, 2 top)
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
        struct DockChip { const char* label; int id; };
        const DockChip chips[] = {{"上", 2}, {"左", 0}, {"右", 1}};
        for (const auto& ch : chips) {
            auto* b = new QPushButton(QString::fromUtf8(ch.label));
            b->setCheckable(true);
            b->setFixedHeight(kRowH);
            b->setMinimumWidth(56);
            b->setObjectName("chip");
            dockGroup_->addButton(b, ch.id);
            row->addWidget(b);
            connect(b, &QPushButton::clicked, this, &SettingsWindow::onGeneralChanged);
        }
        row->addStretch();
        v->addLayout(row);
        lay->addWidget(makeCard(inner));
    }

    // Behavior / auto-hide
    {
        auto* inner = new QWidget;
        auto* v = new QVBoxLayout(inner);
        v->setContentsMargins(0, 0, 0, 0);
        v->setSpacing(kRowGap);
        v->addWidget(sectionTitle(QStringLiteral("行为")));
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
    return wrapScroll(col);
}

QWidget* SettingsWindow::buildShortcutsPage() {
    auto* col = new QWidget;
    auto* lay = new QVBoxLayout(col);
    lay->setContentsMargins(0, 0, 0, 0);
    lay->setSpacing(kCardGap);

    auto* inner = new QWidget;
    auto* v = new QVBoxLayout(inner);
    v->setContentsMargins(0, 0, 0, 0);
    v->setSpacing(kRowGap);
    v->addWidget(sectionTitle(QStringLiteral("快捷方式")));

    scEmpty_ = new QLabel(QStringLiteral("还没有快捷方式，点下方添加"));
    scEmpty_->setObjectName("scEmpty");
    scEmpty_->setAlignment(Qt::AlignLeft | Qt::AlignVCenter);
    scEmpty_->setWordWrap(true);
    v->addWidget(scEmpty_);

    scList_ = new QListWidget;
    scList_->setObjectName("scList");
    scList_->setIconSize(QSize(28, 28));
    scList_->setSpacing(2);
    scList_->setUniformItemSizes(true);
    scList_->setHorizontalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
    scList_->setSelectionMode(QAbstractItemView::SingleSelection);
    scList_->installEventFilter(this);
    connect(scList_, &QListWidget::itemDoubleClicked, this, &SettingsWindow::onShortcutOpen);
    connect(scList_, &QListWidget::itemSelectionChanged, this, &SettingsWindow::onShortcutSelectionChanged);
    v->addWidget(scList_, 1);

    auto* btnRow = new QHBoxLayout;
    btnRow->setSpacing(kChipGap);
    btnRow->setAlignment(Qt::AlignLeft);
    scAddBtn_ = new QPushButton(QStringLiteral("添加"));
    scAddBtn_->setObjectName("secondaryBtn");
    scAddBtn_->setFixedSize(kBtnW, kBtnH);
    scAddBtn_->setCursor(Qt::PointingHandCursor);
    connect(scAddBtn_, &QPushButton::clicked, this, &SettingsWindow::onShortcutAdd);
    scDelBtn_ = new QPushButton(QStringLiteral("删除"));
    scDelBtn_->setObjectName("ghostBtn");
    scDelBtn_->setFixedSize(kBtnW, kBtnH);
    scDelBtn_->setCursor(Qt::PointingHandCursor);
    scDelBtn_->setEnabled(false);
    connect(scDelBtn_, &QPushButton::clicked, this, &SettingsWindow::onShortcutDelete);
    btnRow->addWidget(scAddBtn_);
    btnRow->addWidget(scDelBtn_);
    btnRow->addStretch();
    v->addLayout(btnRow);

    lay->addWidget(makeCard(inner), 1);
    return wrapScroll(col);
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

    auto* ivRow = new QHBoxLayout;
    ivRow->setSpacing(kChipGap);
    ivRow->setAlignment(Qt::AlignLeft | Qt::AlignVCenter);
    auto* ivLab = new QLabel(QStringLiteral("更换间隔"));
    ivLab->setFixedHeight(kRowH);
    wpInterval_ = new QComboBox;
    wpInterval_->setObjectName("wpInterval");
    wpInterval_->setFixedHeight(kRowH);
    wpInterval_->setMinimumWidth(160);
    wpInterval_->addItems({
        QStringLiteral("30 分钟"),
        QStringLiteral("1 小时"),
        QStringLiteral("3 小时"),
        QStringLiteral("每天"),
        QStringLiteral("只在开机时换"),
    });
    connect(wpInterval_, QOverload<int>::of(&QComboBox::currentIndexChanged),
            this, &SettingsWindow::onWallpaperChanged);
    ivRow->addWidget(ivLab);
    ivRow->addWidget(wpInterval_);
    ivRow->addStretch();
    ev->addLayout(ivRow);

    wpNowBtn_ = new QPushButton(QStringLiteral("立即换"));
    wpNowBtn_->setObjectName("secondaryBtn");
    wpNowBtn_->setFixedSize(kBtnW, kBtnH);
    wpNowBtn_->setCursor(Qt::PointingHandCursor);
    connect(wpNowBtn_, &QPushButton::clicked, this, &SettingsWindow::onWallpaperNow);
    auto* btnRow = new QHBoxLayout;
    btnRow->setAlignment(Qt::AlignLeft);
    btnRow->addWidget(wpNowBtn_);
    btnRow->addStretch();
    ev->addLayout(btnRow);

    v->addWidget(wpExtra_);
    lay->addWidget(makeCard(inner));
    lay->addStretch();
    return wrapScroll(col);
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
        QStringLiteral("仅本地；云端 Agents 暂不支持。\n"
                       "当前清除仅针对本地 Composer 对话 / 已归档 / 可选本地缓存。\n"
                       "不会删除账号下的云端 Agents 与云端项目记录。"));
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
    return wrapScroll(col);
}

void SettingsWindow::applyStyle() {
    setStyleSheet(QStringLiteral(R"(
        QMainWindow, QWidget { background: #F4F6FA; color: #111827; font-family: "Microsoft YaHei UI", "Segoe UI", sans-serif; font-size: 13px; }
        #nav { background: #EEF1F6; border: none; outline: none; padding: 10px 0; color: #111827; }
        #nav::item { height: %1px; padding-left: 12px; padding-right: 12px; margin: 2px 10px; border-radius: 8px; color: #111827; }
        #nav::item:selected { background: #EFF6FF; color: #111827; }
        #nav::item:selected:!active { background: #EFF6FF; color: #111827; }
        #card { background: #FFFFFF; border: 1px solid #E6E8EF; border-radius: 10px; }
        #sectionTitle { color: #6B7280; font-size: 13px; font-weight: 500; }
        #notice { color: #6B7280; background: #F0F4FF; border-radius: 8px; padding: 10px; }
        #risk { color: #D97706; font-size: 12px; }
        #scEmpty { color: #6B7280; }
        #scList { background: transparent; border: none; outline: none; }
        #scList::item { height: 36px; padding: 4px 8px; border-radius: 6px; color: #111827; }
        #scList::item:selected { background: #EFF6FF; color: #111827; }
        #scList::item:hover { background: #F3F4F6; }
        QPushButton#chip { background: #FFFFFF; border: 1px solid #E6E8EF; border-radius: 6px; padding: 0 12px; text-align: left; color: #111827; }
        QPushButton#chip:checked { background: #EFF6FF; border-color: #3B82F6; color: #1D4ED8; }
        QPushButton#dangerBtn { background: #EF4444; color: white; border: none; border-radius: 6px; font-weight: 600; }
        QPushButton#dangerBtn:hover { background: #DC2626; }
        QPushButton#dangerBtn:disabled { background: #E6E8EF; color: #9CA3AF; }
        QPushButton#secondaryBtn { background: #3B82F6; color: white; border: none; border-radius: 6px; }
        QPushButton#secondaryBtn:hover { background: #2563EB; }
        QPushButton#secondaryBtn:disabled { background: #E6E8EF; color: #9CA3AF; }
        QPushButton#ghostBtn { background: #FFFFFF; color: #111827; border: 1px solid #E6E8EF; border-radius: 6px; }
        QPushButton#ghostBtn:hover { background: #F3F4F6; }
        QPushButton#ghostBtn:disabled { background: #F9FAFB; color: #9CA3AF; border-color: #E6E8EF; }
        QLineEdit { border: 1px solid #E6E8EF; border-radius: 6px; padding: 0 8px; background: #FFF; color: #111827; }
        QLineEdit:focus { border: 1px solid #3B82F6; }
        QComboBox { border: 1px solid #E6E8EF; border-radius: 6px; padding: 0 8px; background: #FFF; color: #111827; }
        QComboBox:focus { border: 1px solid #3B82F6; }
        QComboBox::drop-down { border: none; width: 24px; }
        QCheckBox { spacing: 8px; color: #111827; }
        QCheckBox::indicator { width: 16px; height: 16px; border-radius: 4px; border: 1px solid #E6E8EF; background: #FFFFFF; }
        QCheckBox::indicator:checked { background: #3B82F6; border-color: #3B82F6; }
    )").arg(kNavItemH));
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
    wpInterval_->setCurrentIndex(qBound(0, c.wpInterval, 4));
    wpExtra_->setVisible(c.wpEnabled);
    clrLocal_->setChecked(true);
    clrArchived_->setChecked(true);
    clrCache_->setChecked(false);
    clrSidebar_->setChecked(false);
    loading_ = false;
}

void SettingsWindow::reloadShortcutsList() {
    if (!scList_) return;
    const QStringList paths = ConfigStore::loadShortcuts();
    scList_->clear();
    QFileIconProvider icons;
    for (const QString& p : paths) {
        QFileInfo fi(p);
        auto* item = new QListWidgetItem(icons.icon(fi), fi.completeBaseName().isEmpty() ? fi.fileName() : fi.completeBaseName());
        item->setData(Qt::UserRole, p);
        item->setToolTip(p);
        item->setTextAlignment(Qt::AlignLeft | Qt::AlignVCenter);
        scList_->addItem(item);
    }
    const bool empty = paths.isEmpty();
    scEmpty_->setVisible(empty);
    scList_->setVisible(!empty);
    onShortcutSelectionChanged();
}

void SettingsWindow::persistShortcutsAndNotify() {
    QStringList paths;
    for (int i = 0; i < scList_->count(); ++i) {
        paths.append(scList_->item(i)->data(Qt::UserRole).toString());
    }
    ConfigStore::saveShortcuts(paths);
    HostBridge::notifyConfigChanged();
    reloadShortcutsList();
}

void SettingsWindow::addShortcutPath(const QString& path) {
    if (path.isEmpty()) return;
    if (scList_->count() >= ConfigStore::kMaxShortcuts) {
        QMessageBox::information(this, QStringLiteral("快捷方式"),
                                 QStringLiteral("最多 %1 个快捷方式。").arg(ConfigStore::kMaxShortcuts));
        return;
    }
    for (int i = 0; i < scList_->count(); ++i) {
        if (scList_->item(i)->data(Qt::UserRole).toString().compare(path, Qt::CaseInsensitive) == 0)
            return;
    }
    QFileInfo fi(path);
    QFileIconProvider icons;
    auto* item = new QListWidgetItem(icons.icon(fi), fi.completeBaseName().isEmpty() ? fi.fileName() : fi.completeBaseName());
    item->setData(Qt::UserRole, path);
    item->setToolTip(path);
    scList_->addItem(item);
    persistShortcutsAndNotify();
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
    c.wpInterval = wpInterval_->currentIndex();
    if (c.wpInterval < 0 || c.wpInterval > 4) c.wpInterval = 3;
    ConfigStore::save(c);
    HostBridge::notifyConfigChanged();
}

void SettingsWindow::onNavChanged(int row) {
    if (row >= 0) stack_->setCurrentIndex(row);
    if (row == 1) reloadShortcutsList();
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

void SettingsWindow::onWallpaperNow() {
    persistAndNotify();
    if (!HostBridge::requestWallpaperNow()) {
        QMessageBox::warning(this, QStringLiteral("立即换壁纸"),
                             QStringLiteral("未找到运行中的托盘进程（CursorUsageWidget）。请先启动用量小窗。"));
    }
}

void SettingsWindow::onShortcutAdd() {
    // Prefer tray's installed-apps manage dialog (same as GDI settings).
    if (HostBridge::requestOpenManageShortcuts()) {
        // Reload after manage dialog likely closes; poll briefly.
        QTimer::singleShot(800, this, [this]() { reloadShortcutsList(); });
        QTimer::singleShot(2500, this, [this]() { reloadShortcutsList(); });
        return;
    }
    // Fallback: local file picker when tray is not running.
    if (scList_->count() >= ConfigStore::kMaxShortcuts) {
        QMessageBox::information(this, QStringLiteral("快捷方式"),
                                 QStringLiteral("最多 %1 个快捷方式。").arg(ConfigStore::kMaxShortcuts));
        return;
    }
    const QString path = QFileDialog::getOpenFileName(
        this, QStringLiteral("添加快捷方式"),
        QString(),
        QStringLiteral("程序或快捷方式 (*.exe *.lnk);;所有文件 (*.*)"));
    if (path.isEmpty()) return;
    addShortcutPath(QDir::toNativeSeparators(path));
}

void SettingsWindow::onShortcutDelete() {
    auto* item = scList_ ? scList_->currentItem() : nullptr;
    if (!item) return;
    delete scList_->takeItem(scList_->row(item));
    persistShortcutsAndNotify();
}

void SettingsWindow::onShortcutOpen(QListWidgetItem* item) {
    if (!item) return;
    const QString path = item->data(Qt::UserRole).toString();
    if (path.isEmpty()) return;
    QDesktopServices::openUrl(QUrl::fromLocalFile(path));
}

void SettingsWindow::onShortcutSelectionChanged() {
    if (scDelBtn_)
        scDelBtn_->setEnabled(scList_ && scList_->currentItem() != nullptr);
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
