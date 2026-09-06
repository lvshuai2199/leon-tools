
// #ifndef VUE3
import Vue from 'vue'
import App from './App'
import api from '@/apiUtils/index.js'

Vue.config.productionTip = false
Vue.prototype.$api = api

App.mpType = 'app'

const app = new Vue({
    ...App
})
app.$mount()
// #endif

// #ifdef VUE3
import { createSSRApp } from 'vue'
import App from './App.vue'

import api from '@/apiUtils/index.js'
export function createApp() {
  const app = createSSRApp(App)
  app.config.globalProperties.$api = api
  return {
    app
  }
}
// #endif