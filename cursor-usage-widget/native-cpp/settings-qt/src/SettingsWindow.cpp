#include "SettingsWindow.h"
#include "HostBridge.h"
#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QFrame>
#include <QMessageBox>
#include <QScrollArea>
#include <QApplication>
#include <QShortcut>
#include <QDir>
#include <QFileInfo>
#include <QDesktopServices>
#include <QUrl>
#include <QSet>
#include <QHash>
#include <QTimer>
#include <string>
#ifdef _WIN32
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <shellapi.h>
#endif

namespace {
constexpr int kNavW = 120;
constexpr int kWinW = 820;
constexpr int kWinH = 560;
constexpr int kContentPadH = 24;
constexpr int kContentPadV = 20;
constexpr int kCardGap = 16;
constexpr int kCardPad = 16;
constexpr int kRowH = 30;
constexpr int kRowGap = 10;
constexpr int kChipGap = 10;
constexpr int kBtnW = 104;
constexpr int kBtnH = 32;
constexpr int kNavItemH = 40;
constexpr int kRoleLaunch = Qt::UserRole;
constexpr int kRoleTarget = Qt::UserRole + 1;
constexpr int kRoleIsSystem = Qt::UserRole + 2;
}

SettingsWindow::SettingsWindow(QWidget* parent) : QMainWindow(parent) {
    setWindowTitle(QStringLiteral("设置"));
    setObjectName(QStringLiteral("CursorUsageSettingsQt"));
    setWindowFlags(Qt::Window);
    setAttribute(Qt::WA_DeleteOnClose, false);
    resize(kWinW, kWinH);
    setMinimumSize(760, 500);
    buildUi();
    applyStyle();
    loadFromConfig();
    reloadInstalledApps();
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
    QMainWindow::keyPressEvent(e);
}

bool SettingsWindow::eventFilter(QObject* obj, QEvent* e) {
    if (scRightList_ && obj == scRightList_->viewport() && e->type() == QEvent::Drop) {
        // Let QListWidget finish InternalMove, then sync order.
        QTimer::singleShot(0, this, [this]() { syncSelectedFromRightPreview(); });
    }
    return QMainWindow::eventFilter(obj, e);
}

void SettingsWindow::changeEvent(QEvent* e) {
    QMainWindow::changeEvent(e);
    if (e->type() == QEvent::ActivationChange && isActiveWindow()) {
        if (stack_ && stack_->currentIndex() == 1) {
            // Re-sync selection from disk if tray wrote shortcuts while inactive.
            const QStringList disk = ConfigStore::loadShortcuts();
            if (disk != scSelectedPaths_) {
                scSelectedPaths_ = disk;
                syncChecksFromSelected();
                rebuildRightPreview();
            }
        }
    }
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
                    QStringLiteral("壁纸"), QStringLiteral("清除历史")});
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

    {
        auto* inner = new QWidget;
        auto* v = new QVBoxLayout(inner);
        v->setContentsMargins(0, 0, 0, 0);
        v->setSpacing(kRowGap);
        v->addWidget(sectionTitle(QStringLiteral("停靠")));
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

    {
        auto* inner = new QWidget;
        auto* v = new QVBoxLayout(inner);
        v->setContentsMargins(0, 0, 0, 0);
        v->setSpacing(kRowGap);
        v->addWidget(sectionTitle(QStringLiteral("行为")));
        autoHide_ = new QCheckBox(QStringLiteral("鼠标离开自动隐藏"));
        autoHide_->setFixedHeight(kRowH);
        connect(autoHide_, &QCheckBox::toggled, this, &SettingsWindow::onGeneralChanged);
        v->addWidget(autoHide_);
        lay->addWidget(makeCard(inner));
    }

    {
        auto* inner = new QWidget;
        auto* v = new QVBoxLayout(inner);
        v->setContentsMargins(0, 0, 0, 0);
        v->setSpacing(kRowGap);
        v->addWidget(sectionTitle(QStringLiteral("用量环显示")));
        showModels_ = new QCheckBox(QStringLiteral("Models"));
        showApi_ = new QCheckBox(QStringLiteral("API / 用量"));
        showBot_ = new QCheckBox(QStringLiteral("Bot"));
        for (auto* c : {showModels_, showApi_, showBot_}) {
            c->setFixedHeight(kRowH);
            v->addWidget(c);
            connect(c, &QCheckBox::toggled, this, &SettingsWindow::onGeneralChanged);
        }
        lay->addWidget(makeCard(inner));
    }

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
    auto* page = new QWidget;
    auto* pageLay = new QVBoxLayout(page);
    pageLay->setContentsMargins(kContentPadH, kContentPadV, kContentPadH, kContentPadV);
    pageLay->setSpacing(kCardGap);

    auto* inner = new QWidget;
    auto* v = new QVBoxLayout(inner);
    v->setContentsMargins(0, 0, 0, 0);
    v->setSpacing(kRowGap);
    v->addWidget(sectionTitle(QStringLiteral("快捷方式")));

    scHint_ = new QLabel(QStringLiteral("勾选本机已装软件，右侧预览可拖拽排序；最多 %1 个。")
                             .arg(ConfigStore::kMaxShortcuts));
    scHint_->setObjectName("scEmpty");
    scHint_->setWordWrap(true);
    v->addWidget(scHint_);

    auto* split = new QHBoxLayout;
    split->setSpacing(12);

    // ---- Left: installed apps ----
    auto* left = new QWidget;
    left->setObjectName("scPane");
    auto* leftLay = new QVBoxLayout(left);
    leftLay->setContentsMargins(0, 0, 0, 0);
    leftLay->setSpacing(8);

    scSearch_ = new QLineEdit;
    scSearch_->setPlaceholderText(QStringLiteral("搜索软件名称…"));
    scSearch_->setFixedHeight(kRowH);
    scSearch_->setClearButtonEnabled(true);
    connect(scSearch_, &QLineEdit::textChanged, this, &SettingsWindow::onShortcutSearchChanged);
    leftLay->addWidget(scSearch_);

    scShowSystem_ = new QCheckBox(QStringLiteral("显示系统应用"));
    scShowSystem_->setFixedHeight(kRowH);
    connect(scShowSystem_, &QCheckBox::toggled, this, &SettingsWindow::onShortcutShowSystemToggled);
    leftLay->addWidget(scShowSystem_);

    scLeftList_ = new QListWidget;
    scLeftList_->setObjectName("scList");
    scLeftList_->setIconSize(QSize(28, 28));
    scLeftList_->setSpacing(1);
    scLeftList_->setUniformItemSizes(true);
    scLeftList_->setHorizontalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
    scLeftList_->setSelectionMode(QAbstractItemView::NoSelection);
    connect(scLeftList_, &QListWidget::itemChanged, this, &SettingsWindow::onShortcutItemChanged);
    connect(scLeftList_, &QListWidget::itemDoubleClicked, this, &SettingsWindow::onShortcutOpen);
    leftLay->addWidget(scLeftList_, 1);

    // ---- Right: selected preview ----
    auto* right = new QWidget;
    right->setObjectName("scPane");
    auto* rightLay = new QVBoxLayout(right);
    rightLay->setContentsMargins(0, 0, 0, 0);
    rightLay->setSpacing(8);

    auto* rightTitle = new QLabel(QStringLiteral("已选预览"));
    rightTitle->setObjectName("sectionTitle");
    rightLay->addWidget(rightTitle);

    scRightEmpty_ = new QLabel(QStringLiteral("勾选左侧软件"));
    scRightEmpty_->setObjectName("scEmpty");
    scRightEmpty_->setAlignment(Qt::AlignCenter);
    rightLay->addWidget(scRightEmpty_, 1);

    scRightList_ = new QListWidget;
    scRightList_->setObjectName("scPreview");
    scRightList_->setViewMode(QListView::IconMode);
    scRightList_->setFlow(QListView::LeftToRight);
    scRightList_->setWrapping(true);
    scRightList_->setIconSize(QSize(48, 48));
    scRightList_->setGridSize(QSize(84, 86));
    scRightList_->setResizeMode(QListView::Adjust);
    scRightList_->setMovement(QListView::Snap);
    scRightList_->setUniformItemSizes(true);
    scRightList_->setDragEnabled(true);
    scRightList_->setAcceptDrops(true);
    scRightList_->setDropIndicatorShown(false);
    scRightList_->setDefaultDropAction(Qt::MoveAction);
    scRightList_->setDragDropMode(QAbstractItemView::InternalMove);
    scRightList_->setSpacing(6);
    scRightList_->setWordWrap(true);
    scRightList_->setHorizontalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
    scRightList_->setVerticalScrollBarPolicy(Qt::ScrollBarAsNeeded);
    scRightList_->setSelectionMode(QAbstractItemView::SingleSelection);
    scRightList_->setVisible(false);
    scRightList_->viewport()->installEventFilter(this);
    connect(scRightList_, &QListWidget::itemDoubleClicked, this, &SettingsWindow::onShortcutOpen);
    rightLay->addWidget(scRightList_, 1);

    split->addWidget(left, 3);
    split->addWidget(right, 2);
    v->addLayout(split, 1);

    auto* btnRow = new QHBoxLayout;
    btnRow->setSpacing(kChipGap);
    btnRow->setAlignment(Qt::AlignLeft);
    scSaveBtn_ = new QPushButton(QStringLiteral("保存"));
    scSaveBtn_->setObjectName("secondaryBtn");
    scSaveBtn_->setFixedSize(kBtnW, kBtnH);
    scSaveBtn_->setCursor(Qt::PointingHandCursor);
    connect(scSaveBtn_, &QPushButton::clicked, this, &SettingsWindow::onShortcutSave);
    btnRow->addWidget(scSaveBtn_);
    btnRow->addStretch();
    v->addLayout(btnRow);

    pageLay->addWidget(makeCard(inner), 1);
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
    v->addWidget(sectionTitle(QStringLiteral("壁纸")));

    wpEnabled_ = new QCheckBox(QStringLiteral("启用动态壁纸"));
    wpEnabled_->setFixedHeight(kRowH);
    connect(wpEnabled_, &QCheckBox::toggled, this, &SettingsWindow::onWallpaperChanged);
    v->addWidget(wpEnabled_);

    wpExtra_ = new QWidget;
    auto* ev = new QVBoxLayout(wpExtra_);
    ev->setContentsMargins(0, 0, 0, 0);
    ev->setSpacing(kRowGap);

    auto* urlRow = new QHBoxLayout;
    urlRow->addWidget(new QLabel(QStringLiteral("图片 URL")));
    wpUrl_ = new QLineEdit;
    wpUrl_->setFixedHeight(kRowH);
    wpUrl_->setPlaceholderText(QStringLiteral("https://…"));
    connect(wpUrl_, &QLineEdit::editingFinished, this, &SettingsWindow::onWallpaperChanged);
    urlRow->addWidget(wpUrl_, 1);
    ev->addLayout(urlRow);

    auto* intRow = new QHBoxLayout;
    intRow->addWidget(new QLabel(QStringLiteral("更换间隔")));
    wpInterval_ = new QComboBox;
    wpInterval_->setFixedHeight(kRowH);
    wpInterval_->addItems({
        QStringLiteral("每小时"), QStringLiteral("每 3 小时"), QStringLiteral("每 6 小时"),
        QStringLiteral("每天"), QStringLiteral("每周")
    });
    connect(wpInterval_, QOverload<int>::of(&QComboBox::currentIndexChanged),
            this, &SettingsWindow::onWallpaperChanged);
    intRow->addWidget(wpInterval_);
    intRow->addStretch();
    ev->addLayout(intRow);

    wpNowBtn_ = new QPushButton(QStringLiteral("立即更换"));
    wpNowBtn_->setObjectName("ghostBtn");
    wpNowBtn_->setFixedSize(kBtnW, kBtnH);
    wpNowBtn_->setCursor(Qt::PointingHandCursor);
    connect(wpNowBtn_, &QPushButton::clicked, this, &SettingsWindow::onWallpaperNow);
    auto* nowRow = new QHBoxLayout;
    nowRow->setAlignment(Qt::AlignLeft);
    nowRow->addWidget(wpNowBtn_);
    nowRow->addStretch();
    ev->addLayout(nowRow);

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
    v->addWidget(sectionTitle(QStringLiteral("清除 Cursor 历史")));

    auto* notice = new QLabel(QStringLiteral("由托盘进程执行清理；请先勾选要清除的项。"));
    notice->setObjectName("notice");
    notice->setWordWrap(true);
    v->addWidget(notice);

    clrLocal_ = new QCheckBox(QStringLiteral("本地 Agent / 项目记录"));
    clrArchived_ = new QCheckBox(QStringLiteral("已归档对话"));
    clrCache_ = new QCheckBox(QStringLiteral("缓存"));
    clrSidebar_ = new QCheckBox(QStringLiteral("侧边栏状态"));
    for (auto* c : {clrLocal_, clrArchived_, clrCache_, clrSidebar_}) {
        c->setFixedHeight(kRowH);
        v->addWidget(c);
    }

    auto* risk = new QLabel(QStringLiteral("注意：可能删除本地会话态、settings.json、扩展与 MCP 配置、移动 Agents/项目目录。"));
    risk->setObjectName("risk");
    risk->setWordWrap(true);
    v->addWidget(risk);

    clrBtn_ = new QPushButton(QStringLiteral("清除"));
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
        QMainWindow { background: #F4F6FA; color: #111827; font-family: "Microsoft YaHei UI", "Segoe UI", sans-serif; font-size: 13px; }
        QWidget { color: #111827; font-family: "Microsoft YaHei UI", "Segoe UI", sans-serif; font-size: 13px; }
        QStackedWidget, QScrollArea { background: #F4F6FA; border: none; }
        QScrollArea > QWidget > QWidget { background: #F4F6FA; }
        #nav { background: #EEF1F6; border: none; outline: none; padding: 10px 0; color: #111827; }
        #nav::item { height: %1px; padding-left: 12px; padding-right: 12px; margin: 2px 10px; border-radius: 8px; color: #111827; }
        #nav::item:selected { background: #EFF6FF; color: #111827; }
        #nav::item:selected:!active { background: #EFF6FF; color: #111827; }
        #card { background: #FFFFFF; border: 1px solid #E6E8EF; border-radius: 10px; }
        #card QWidget { background: transparent; }
        #sectionTitle { color: #6B7280; font-size: 13px; font-weight: 500; background: transparent; }
        #card #notice, #notice { color: #6B7280; background: #F0F4FF; border-radius: 8px; padding: 10px; }
        #risk { color: #D97706; font-size: 12px; }
        #scEmpty { color: #6B7280; }
        #scList { background: #FAFBFC; border: 1px solid #E6E8EF; border-radius: 8px; outline: none; padding: 4px; }
        #scList::item { height: 34px; padding: 2px 6px; border-radius: 6px; color: #111827; }
        #scList::item:hover { background: #F3F4F6; }
        #scPreview {
            background: transparent; border: none; outline: none; padding: 4px 2px;
        }
        #scPreview::item {
            color: #374151; font-size: 11px;
            background: transparent; border: 1px solid transparent;
            border-radius: 10px; padding: 6px 4px 4px 4px;
            margin: 2px;
        }
        #scPreview::item:hover { background: #F3F4F6; border-color: #E6E8EF; }
        #scPreview::item:selected { background: #EFF6FF; border-color: #BFDBFE; color: #1D4ED8; }
        #scPane { background: transparent; }
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

void SettingsWindow::reloadInstalledApps() {
    scSelectedPaths_ = ConfigStore::loadShortcuts();
    scApps_ = InstalledApps::enumerate();

    // Inject saved shortcuts missing from enum so they stay visible/checked.
    QSet<QString> known;
    for (const auto& a : scApps_)
        known.insert(a.launchPath.toLower());
    for (const QString& p : scSelectedPaths_) {
        if (p.isEmpty() || known.contains(p.toLower())) continue;
        InstalledApp a;
        a.launchPath = p;
        if (InstalledApps::isShellAppsPath(p)) {
            a.target.clear();
            const int slash = p.lastIndexOf(QLatin1Char('\\'));
            a.name = slash >= 0 ? p.mid(slash + 1) : p;
            // Prefer friendly AUMID leaf before '!'.
            const int bang = a.name.indexOf(QLatin1Char('!'));
            if (bang > 0) a.name = a.name.left(bang);
        } else {
            a.target = p.endsWith(QStringLiteral(".lnk"), Qt::CaseInsensitive)
                           ? InstalledApps::resolveLnk(p) : p;
            a.name = QFileInfo(p).completeBaseName();
            if (a.name.isEmpty()) a.name = QFileInfo(p).fileName();
        }
        a.isSystem = InstalledApps::isSystemApp(a.name, a.launchPath, a.target);
        scApps_.prepend(a);
        known.insert(p.toLower());
    }

    rebuildLeftList();
    rebuildRightPreview();
}

void SettingsWindow::rebuildLeftList() {
    if (!scLeftList_) return;
    scRebuilding_ = true;
    scLeftList_->clear();
    const QString filter = scSearch_ ? scSearch_->text().trimmed() : QString();
    const bool showSys = scShowSystem_ && scShowSystem_->isChecked();

    for (const auto& a : scApps_) {
        if (!showSys && a.isSystem) continue;
        if (!filter.isEmpty() && !a.name.contains(filter, Qt::CaseInsensitive)) continue;
        auto* item = new QListWidgetItem(InstalledApps::iconFor(a.launchPath, a.target), a.name);
        item->setData(kRoleLaunch, a.launchPath);
        item->setData(kRoleTarget, a.target);
        item->setData(kRoleIsSystem, a.isSystem);
        item->setToolTip(a.launchPath);
        item->setFlags(item->flags() | Qt::ItemIsUserCheckable);
        bool checked = false;
        for (const QString& s : scSelectedPaths_) {
            if (s.compare(a.launchPath, Qt::CaseInsensitive) == 0) { checked = true; break; }
        }
        item->setCheckState(checked ? Qt::Checked : Qt::Unchecked);
        scLeftList_->addItem(item);
    }
    scRebuilding_ = false;
}

void SettingsWindow::rebuildRightPreview() {
    if (!scRightList_ || !scRightEmpty_) return;
    scRightList_->clear();
    QHash<QString, InstalledApp> byLaunch;
    for (const auto& a : scApps_)
        byLaunch.insert(a.launchPath.toLower(), a);

    for (const QString& p : scSelectedPaths_) {
        InstalledApp a = byLaunch.value(p.toLower());
        if (a.launchPath.isEmpty()) {
            a.launchPath = p;
            if (InstalledApps::isShellAppsPath(p)) {
                a.target.clear();
                const int slash = p.lastIndexOf(QLatin1Char('\\'));
                a.name = slash >= 0 ? p.mid(slash + 1) : p;
            } else {
                a.name = QFileInfo(p).completeBaseName();
                if (a.name.isEmpty()) a.name = QFileInfo(p).fileName();
                a.target = p.endsWith(QStringLiteral(".lnk"), Qt::CaseInsensitive)
                               ? InstalledApps::resolveLnk(p) : p;
            }
        }
        QString shortName = a.name;
        if (shortName.size() > 10)
            shortName = shortName.left(9) + QStringLiteral("…");
        auto* item = new QListWidgetItem(InstalledApps::iconFor(a.launchPath, a.target), shortName);
        item->setData(kRoleLaunch, a.launchPath);
        item->setToolTip(a.name + QChar(QChar::LineFeed) + a.launchPath);
        item->setTextAlignment(Qt::AlignHCenter | Qt::AlignTop);
        item->setFlags((item->flags() | Qt::ItemIsDragEnabled | Qt::ItemIsDropEnabled | Qt::ItemIsEnabled | Qt::ItemIsSelectable)
                       & ~Qt::ItemIsEditable);
        scRightList_->addItem(item);
    }
    const bool empty = scSelectedPaths_.isEmpty();
    scRightEmpty_->setVisible(empty);
    scRightList_->setVisible(!empty);
}

void SettingsWindow::syncChecksFromSelected() {
    if (!scLeftList_) return;
    scRebuilding_ = true;
    for (int i = 0; i < scLeftList_->count(); ++i) {
        auto* item = scLeftList_->item(i);
        const QString launch = item->data(kRoleLaunch).toString();
        bool on = false;
        for (const QString& s : scSelectedPaths_) {
            if (s.compare(launch, Qt::CaseInsensitive) == 0) { on = true; break; }
        }
        item->setCheckState(on ? Qt::Checked : Qt::Unchecked);
    }
    scRebuilding_ = false;
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
    if (row == 1) reloadInstalledApps();
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
        QMessageBox::warning(this, QStringLiteral("立即更换壁纸"),
                             QStringLiteral("未找到运行中的托盘进程（CursorUsageWidget）。请先启动用量环托盘。"));
    }
}

void SettingsWindow::onShortcutSearchChanged(const QString&) {
    rebuildLeftList();
}

void SettingsWindow::onShortcutShowSystemToggled(bool) {
    rebuildLeftList();
}

void SettingsWindow::onShortcutItemChanged(QListWidgetItem* item) {
    if (scRebuilding_ || !item) return;
    const QString launch = item->data(kRoleLaunch).toString();
    if (launch.isEmpty()) return;

    if (item->checkState() == Qt::Checked) {
        for (const QString& s : scSelectedPaths_) {
            if (s.compare(launch, Qt::CaseInsensitive) == 0) {
                rebuildRightPreview();
                return;
            }
        }
        if (scSelectedPaths_.size() >= ConfigStore::kMaxShortcuts) {
            scRebuilding_ = true;
            item->setCheckState(Qt::Unchecked);
            scRebuilding_ = false;
            QMessageBox::information(this, QStringLiteral("快捷方式"),
                                     QStringLiteral("最多 %1 个快捷方式。").arg(ConfigStore::kMaxShortcuts));
            return;
        }
        scSelectedPaths_.append(launch);
    } else {
        for (int i = 0; i < scSelectedPaths_.size(); ++i) {
            if (scSelectedPaths_[i].compare(launch, Qt::CaseInsensitive) == 0) {
                scSelectedPaths_.removeAt(i);
                break;
            }
        }
    }
    rebuildRightPreview();
}

void SettingsWindow::syncSelectedFromRightPreview() {
    if (!scRightList_) return;
    QStringList next;
    for (int i = 0; i < scRightList_->count(); ++i) {
        const QString launch = scRightList_->item(i)->data(kRoleLaunch).toString();
        if (!launch.isEmpty())
            next.append(launch);
    }
    if (next == scSelectedPaths_) return;
    scSelectedPaths_ = next;
    syncChecksFromSelected();
}

void SettingsWindow::onShortcutSave() {
    ConfigStore::saveShortcuts(scSelectedPaths_);
    HostBridge::notifyConfigChanged();  // WM_APP+0x41 only
    scSelectedPaths_ = ConfigStore::loadShortcuts();
    syncChecksFromSelected();
    rebuildRightPreview();
}

void SettingsWindow::onShortcutOpen(QListWidgetItem* item) {
    if (!item) return;
    const QString path = item->data(kRoleLaunch).toString();
    if (path.isEmpty()) return;
#ifdef _WIN32
    if (InstalledApps::isShellAppsPath(path)) {
        const std::wstring w = path.toStdWString();
        ShellExecuteW(nullptr, L"open", w.c_str(), nullptr, nullptr, SW_SHOWNORMAL);
        return;
    }
#endif
    QDesktopServices::openUrl(QUrl::fromLocalFile(path));
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
        QStringLiteral("确定继续吗？\n可能删除本地会话态、settings.json、扩展与 MCP 配置、移动 Agents/项目目录。"),
        QMessageBox::Yes | QMessageBox::No, QMessageBox::No);
    if (ans != QMessageBox::Yes) return;
    if (!HostBridge::requestClearHistory(opt)) {
        QMessageBox::warning(this, QStringLiteral("清除失败"),
                             QStringLiteral("未找到运行中的托盘进程（CursorUsageWidget）。请先启动用量环托盘。"));
    }
}
