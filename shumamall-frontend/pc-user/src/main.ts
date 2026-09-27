import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import zhCn from 'element-plus/es/locale/lang/zh-cn';
import App from './App.vue';
import router from './router';

// Element Plus 默认英文语言包：ElMessageBox 的确认按钮会显示 OK / Cancel，
// 分页、日期选择等内置文案同样是英文。这里显式注入中文包。
createApp(App).use(ElementPlus, { locale: zhCn }).use(router).mount('#app');
