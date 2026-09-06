import { createApp } from "vue";
import App from "./App.vue";
import router from "./router.js";
import api from "./apiUtils/index.js";

const app = createApp(App);
app.use(router);
app.config.globalProperties.$api = api;
app.mount("#app");
