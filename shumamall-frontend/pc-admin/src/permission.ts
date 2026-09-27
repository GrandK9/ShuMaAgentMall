import { ref, readonly, type Directive } from 'vue';
import { ElMessage } from 'element-plus';
import { getMyPermissions } from './api';

/**
 * 当前登录管理员的权限编码集合。
 * 与后端的关系：后端 `@RequirePermission` 是最终的安全边界，本模块只负责**界面层**的
 * 显隐控制——没权限的按钮不渲染，避免用户点了才报 4031。前端隐藏不能替代后端校验。
 */
const permissionCodes = ref<Set<string>>(new Set());

/** 权限是否已加载完成（含"未登录"与"加载失败"两种终态，避免界面卡在加载中） */
const loaded = ref(false);

/**
 * 权限加载状态，供模板 `v-if` 使用。
 * 页面组件必须等权限就绪后再渲染，否则 `v-permission` 会在权限集合为空时误删按钮。
 */
export const permissionsLoaded = readonly(loaded);

/**
 * 判断当前用户是否拥有指定权限编码。
 *
 * @param code 权限编码，如 `product:edit`
 */
export function hasPermission(code: string): boolean {
  return permissionCodes.value.has(code);
}

/**
 * 登录后 / 刷新页面时拉取权限编码集（`GET /permission/my-permissions`）。
 * 未登录或接口失败时置为空集合（fail-closed），与后端权限切面行为保持一致：
 * 拿不到权限就不放行，不猜测、不默认有权限。
 */
export async function loadPermissions(): Promise<void> {
  const token = localStorage.getItem('admin_token') || '';
  if (!token) {
    permissionCodes.value = new Set();
    loaded.value = true;
    return;
  }
  try {
    permissionCodes.value = new Set(await getMyPermissions());
  } catch (e) {
    permissionCodes.value = new Set();
    ElMessage.error(`权限加载失败，按钮将按无权限显示：${(e as Error).message}`);
  } finally {
    loaded.value = true;
  }
}

/** 登出时清空权限；保持 `loaded=true`，避免登出瞬间页面卡在加载态 */
export function clearPermissions(): void {
  permissionCodes.value = new Set();
  loaded.value = true;
}

/**
 * `v-permission` 指令：当前用户缺少指定权限时移除该元素。
 * 用法：`v-permission="'product:edit'"` 或 `v-permission="['order:view', 'order:edit']"`（多编码为 OR）。
 */
export const vPermission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const codes = Array.isArray(binding.value) ? binding.value : [binding.value];
    if (!codes.some((code) => permissionCodes.value.has(code))) {
      el.parentNode?.removeChild(el);
    }
  },
};
