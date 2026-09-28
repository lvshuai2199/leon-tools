package springboot.service.menu;

import java.util.List;

/** sys_menus.client 的取值 */
public final class MenuClients {

    public static final String ADMIN = "admin";
    public static final String APP = "app";
    public static final List<String> ALL = List.of(ADMIN, APP);

    private MenuClients() {
    }

    public static boolean isValid(String client) {
        return ADMIN.equals(client) || APP.equals(client);
    }
}
