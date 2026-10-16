#pragma once
#include <QString>

struct AppConfig {
    int dockEdge = 1;      // 0 left, 1 right, 2 top
    bool autoHide = true;
    bool showModels = true;
    bool showApi = true;
    bool showBot = false;
    int ringMode = 0;      // 0 quad, 1 dual
    bool wpEnabled = false;
    QString wpUrl;
    int wpInterval = 3;
};

struct ClearOptions {
    bool local = true;
    bool archived = true;
    bool cache = false;
    bool sidebar = false;
};

class ConfigStore {
public:
    static QString configPath();
    static AppConfig load();
    static bool save(const AppConfig& c);
};
