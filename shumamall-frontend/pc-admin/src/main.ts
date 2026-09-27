import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import App from './App.vue';
import router from './router';
import { vPermission } from './permission';

// Element Plus 默认英文语言包：ElMessageBox 的确认按钮会显示 OK / Cancel，
// 分页、日期选择等内置文案同样是英文。这里显式注入中文包。
// 同时全局注册按钮级权限指令，各页面直接用 v-permission="'product:edit'"，无需逐个 import
createApp(App).use(ElementPlus, { locale: zhCn }).use(router).directive('permission', vPermission).mount('#app');
