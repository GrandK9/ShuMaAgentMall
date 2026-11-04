<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { useRoute, useRouter } from 'vue-router';
import { clearCart, getCart, removeCartItem, updateCartQuantity, updateCartSelected } from '../api';
import { isLoggedIn, store } from '../store';
import type { CartItem } from '../types';

const router = useRouter();
const route = useRoute();
const loading = ref(false);
const loadError = ref('');
const items = ref<CartItem[]>([]);

const selectedItems = computed(() => items.value.filter((i) => i.selected === 1));
const selectedCount = computed(() => selectedItems.value.length);
const selectedTotal = computed(() => selectedItems.value.reduce((sum, i) => sum + i.subtotal, 0));
const allSelected = computed(
  () => items.value.length > 0 && items.value.every((i) => i.selected === 1),
);
const someSelected = computed(() => {
  const n = selectedCount.value;
  return n > 0 && n < items.value.length;
});

function normalizeCartItem(raw: CartItem): CartItem {
  const price = Number(raw.price ?? 0);
  const quantity = raw.quantity ?? 1;
  const subtotal = Number(raw.subtotal ?? price * quantity);
  return {
    ...raw,
    productName: raw.productName?.trim() || '商品',
    price,
    quantity,
    subtotal,
    selected: raw.selected ?? 1,
  };
}

async function load(): Promise<void> {
  if (!isLoggedIn.value) {
    items.value = [];
    loadError.value = '';
    return;
  }
  loading.value = true;
  loadError.value = '';
  try {
    const data = await getCart();
    items.value = (data ?? []).map(normalizeCartItem);
  } catch (e) {
    loadError.value = (e as Error).message;
    items.value = [];
    ElMessage.error(`购物车加载失败：${loadError.value}`);
  } finally {
    loading.value = false;
  }
}

async function onQuantityChange(item: CartItem, qty: number): Promise<void> {
  if (qty < 1) {
    item.quantity = 1;
    return;
  }
  try {
    await updateCartQuantity(item.skuId, qty);
    item.quantity = qty;
    item.subtotal = item.price * qty;
  } catch (e) {
    ElMessage.error(`修改数量失败：${(e as Error).message}`);
    void load();
  }
}

async function onSelectedChange(item: CartItem, selected: boolean): Promise<void> {
  try {
    await updateCartSelected(item.skuId, selected ? 1 : 0);
    item.selected = selected ? 1 : 0;
  } catch (e) {
    ElMessage.error(`更新选中失败：${(e as Error).message}`);
    void load();
  }
}

async function toggleSelectAll(selected: boolean): Promise<void> {
  for (const item of items.value) {
    if ((item.selected === 1) !== selected) {
      await onSelectedChange(item, selected);
    }
  }
}

async function onRemove(item: CartItem): Promise<void> {
  try {
    await removeCartItem(item.skuId);
    items.value = items.value.filter((i) => i.skuId !== item.skuId);
    ElMessage.success('已删除');
  } catch (e) {
    ElMessage.error(`删除失败：${(e as Error).message}`);
  }
}

async function onClear(): Promise<void> {
  try {
    await ElMessageBox.confirm('确定清空购物车吗？', '提示', { type: 'warning' });
    await clearCart();
    items.value = [];
    ElMessage.success('购物车已清空');
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error(`清空失败：${(e as Error).message}`);
    }
  }
}

function checkout(): void {
  const sel = selectedItems.value;
  if (sel.length === 0) {
    ElMessage.warning('请先勾选要结算的商品');
    return;
  }
  store.checkoutItems = sel.map((i) => ({ ...i }));
  void router.push('/checkout');
}

function goProduct(item: CartItem): void {
  void router.push(`/product/${item.productId}`);
}

watch(
  () => [route.path, store.token] as const,
  ([path, token]) => {
    if (path === '/cart' && token) {
      void load();
    }
  },
  { immediate: true },
);
</script>

<template>
  <div class="cart-page">
    <header class="cart-head">
      <div class="cart-title-row">
        <svg class="cart-icon" viewBox="0 0 24 24" width="28" height="28" aria-hidden="true">
          <path
            fill="currentColor"
            d="M7 18c-1.1 0-1.99.9-1.99 2S5.9 22 7 22s2-.9 2-2-.9-2-2-2zm10 0c-1.1 0-1.99.9-1.99 2S15.9 22 17 22s2-.9 2-2-.9-2-2-2zM7.16 14h9.45c.75 0 1.41-.41 1.75-1.03l3.58-6.49a1 1 0 0 0-.87-1.48H5.21L4.27 2H1v2h2l3.6 7.59-1.35 2.44C4.52 15.37 5.48 17 7 17h12v-2H7.16l1.1-2z"
          />
        </svg>
        <h1 class="cart-title">购物车</h1>
        <span v-if="isLoggedIn && items.length" class="cart-badge">{{ items.length }}</span>
      </div>
      <router-link to="/" class="continue-link">继续购物</router-link>
    </header>

    <el-empty v-if="!isLoggedIn" description="请先在右上角登录，再查看购物车">
      <el-button type="primary" @click="router.push('/')">去首页逛逛</el-button>
    </el-empty>

    <template v-else>
      <div v-loading="loading" class="cart-body">
        <el-empty
          v-if="!loading && loadError"
          :description="loadError"
        >
          <el-button type="primary" @click="load">重试</el-button>
        </el-empty>

        <el-empty v-else-if="!loading && items.length === 0" description="购物车还是空的">
          <el-button type="primary" @click="router.push('/')">去挑选商品</el-button>
        </el-empty>

        <template v-else-if="items.length">
          <div class="cart-toolbar">
            <el-checkbox
              :model-value="allSelected"
              :indeterminate="someSelected"
              @change="(v: string | number | boolean) => toggleSelectAll(Boolean(v))"
            >
              全选
            </el-checkbox>
            <el-button v-if="items.length" link type="danger" @click="onClear">清空</el-button>
          </div>

          <ul class="cart-list" role="list">
            <li v-for="item in items" :key="item.skuId" class="cart-row">
              <el-checkbox
                class="row-check"
                :model-value="item.selected === 1"
                @change="(v: string | number | boolean) => onSelectedChange(item, Boolean(v))"
              />
              <button type="button" class="row-media" @click="goProduct(item)">
                <img v-if="item.productImage" :src="item.productImage" :alt="item.productName" />
                <span v-else class="media-placeholder">暂无图</span>
              </button>
              <div class="row-main">
                <button type="button" class="row-name" @click="goProduct(item)">{{ item.productName }}</button>
                <p v-if="item.skuSpecs" class="row-specs">{{ item.skuSpecs }}</p>
                <p class="row-price amount">¥{{ item.price.toFixed(2) }}</p>
              </div>
              <div class="row-qty">
                <el-input-number
                  :model-value="item.quantity"
                  :min="1"
                  :max="99"
                  size="small"
                  @change="(v: number | undefined) => onQuantityChange(item, v ?? 1)"
                />
              </div>
              <div class="row-subtotal amount">¥{{ item.subtotal.toFixed(2) }}</div>
              <el-button class="row-remove" link type="danger" @click="onRemove(item)">删除</el-button>
            </li>
          </ul>
        </template>
      </div>

      <footer v-if="items.length" class="cart-checkout">
        <div class="checkout-summary">
          <span class="summary-text">
            已选 <strong>{{ selectedCount }}</strong> 件
          </span>
          <span class="summary-total">
            合计
            <span class="total-amount amount">¥{{ selectedTotal.toFixed(2) }}</span>
          </span>
        </div>
        <el-button type="primary" size="large" :disabled="selectedCount === 0" @click="checkout">
          去结算
        </el-button>
      </footer>
    </template>
  </div>
</template>

<style scoped>
.cart-page {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  padding-bottom: var(--space-6);
}

.cart-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-4);
  flex-wrap: wrap;
}

.cart-title-row {
  display: flex;
  align-items: center;
  gap: var(--space-3);
}

.cart-icon {
  color: var(--accent);
  flex-shrink: 0;
}

.cart-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--text-lg);
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: var(--ink);
}

.cart-badge {
  min-width: 1.5rem;
  padding: 2px 8px;
  font-size: var(--text-xs);
  font-weight: 600;
  text-align: center;
  color: var(--accent-ink);
  background: var(--accent);
  border-radius: 999px;
}

.continue-link {
  font-size: var(--text-sm);
  font-weight: 500;
  color: var(--muted);
  text-decoration: none;
  transition: color var(--duration-fast) var(--ease-out);
}

.continue-link:hover {
  color: var(--ink);
}

.cart-body {
  background: var(--surface-raised);
  border-radius: var(--radius-lg);
  padding: var(--space-4) var(--space-5);
  min-height: 120px;
}

.cart-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-4);
  padding-bottom: var(--space-3);
  border-bottom: 1px solid var(--separator);
}

.cart-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.cart-row {
  display: grid;
  grid-template-columns: auto 88px 1fr auto auto auto;
  gap: var(--space-4);
  align-items: center;
  padding: var(--space-4);
  background: var(--surface-card);
  border-radius: var(--radius-md);
  transition: background var(--duration-fast) var(--ease-out);
}

.cart-row:hover {
  background: var(--surface-hover);
}

.row-check {
  margin: 0;
}

.row-media {
  width: 88px;
  height: 88px;
  padding: 0;
  border: none;
  border-radius: var(--radius-md);
  overflow: hidden;
  cursor: pointer;
  background: var(--surface-inset);
  display: flex;
  align-items: center;
  justify-content: center;
}

.row-media img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.media-placeholder {
  font-size: var(--text-xs);
  color: var(--muted);
}

.row-main {
  min-width: 0;
  text-align: left;
}

.row-name {
  display: block;
  width: 100%;
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--ink);
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-name:hover {
  color: var(--accent);
}

.row-specs {
  margin: var(--space-1) 0 0;
  font-size: var(--text-xs);
  color: var(--muted);
}

.row-price {
  margin: var(--space-2) 0 0;
  font-size: var(--text-sm);
  font-weight: 600;
  color: var(--ink);
}

.row-subtotal {
  font-weight: 700;
  font-size: var(--text-base);
  color: var(--ink);
  min-width: 5rem;
  text-align: right;
}

.row-remove {
  flex-shrink: 0;
}

.cart-checkout {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: var(--space-5);
  padding: var(--space-4) var(--space-5);
  background: var(--surface-raised);
  border-radius: var(--radius-lg);
}

.checkout-summary {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: var(--space-4);
}

.summary-text {
  font-size: var(--text-sm);
  color: var(--muted);
}

.summary-text strong {
  color: var(--ink);
  font-weight: 700;
}

.summary-total {
  font-size: var(--text-sm);
  color: var(--muted);
}

.total-amount {
  margin-left: var(--space-2);
  font-size: var(--text-lg);
  font-weight: 700;
  color: var(--ink);
}

@media (max-width: 768px) {
  .cart-row {
    grid-template-columns: auto 72px 1fr;
    grid-template-rows: auto auto auto;
  }

  .row-qty {
    grid-column: 2 / 4;
  }

  .row-subtotal {
    grid-column: 3;
    text-align: left;
  }

  .row-remove {
    grid-column: 3;
    justify-self: end;
  }

  .row-media {
    width: 72px;
    height: 72px;
  }

  .cart-checkout {
    flex-direction: column;
    align-items: stretch;
  }

  .cart-checkout .el-button {
    width: 100%;
  }
}
</style>
